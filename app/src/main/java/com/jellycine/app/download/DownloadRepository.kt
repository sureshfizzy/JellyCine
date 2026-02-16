package com.jellycine.app.download

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.jellycine.app.preferences.DownloadPreferences
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

enum class DownloadStatus {
    IDLE,
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

data class ItemDownloadState(
    val status: DownloadStatus = DownloadStatus.IDLE,
    val progress: Float = 0f,
    val downloadId: Long? = null,
    val message: String? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val filePath: String? = null
)

data class TrackedDownload(
    val itemId: String,
    val item: BaseItemDto?,
    val title: String,
    val mediaType: String?,
    val year: Int?,
    val state: ItemDownloadState,
    val isOfflineAvailable: Boolean,
    val fileSizeBytes: Long?,
    val requestedAt: Long,
    val completedAt: Long?
)

private data class PersistedDownloadMetadata(
    val itemId: String,
    val title: String,
    val subtitle: String? = null,
    val mediaType: String? = null,
    val year: Int? = null,
    val requestedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val localPath: String? = null,
    val status: String = DownloadStatus.IDLE.name,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String? = null,
    val downloadId: Long? = null,
    val fullItemJson: String? = null
)

class DownloadRepository(context: Context) {
    private val appContext = context.applicationContext
    private val mediaRepository = MediaRepositoryProvider.getInstance(appContext)
    private val downloadPreferences = DownloadPreferences(appContext)
    private val downloadManager = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    private val stateFlows = ConcurrentHashMap<String, MutableStateFlow<ItemDownloadState>>()
    private val pollingJobs = ConcurrentHashMap<Long, Job>()
    private val trackedDownloadsFlow = MutableStateFlow<List<TrackedDownload>>(emptyList())

    init {
        restoreDownloads()
    }

    fun observeItemDownload(itemId: String): StateFlow<ItemDownloadState> {
        return stateFlows.getOrPut(itemId) { MutableStateFlow(ItemDownloadState()) }.asStateFlow()
    }

    fun observeTrackedDownloads(): StateFlow<List<TrackedDownload>> = trackedDownloadsFlow.asStateFlow()

    fun getOfflineFilePath(itemId: String): String? {
        val statePath = stateFlows[itemId]?.value?.filePath
        val metadataPath = readMetadata(itemId)?.localPath
        return sequenceOf(statePath, metadataPath)
            .filterNotNull()
            .firstOrNull { path -> File(path).exists() }
    }

    suspend fun deleteDownloadedItem(itemId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val metadata = readMetadata(itemId)
        val state = stateFlows[itemId]?.value
        val downloadId = prefs.getLong(downloadKey(itemId), -1L).takeIf { it > 0L }
            ?: metadata?.downloadId
            ?: state?.downloadId

        runCatching {
            downloadId?.let {
                pollingJobs.remove(it)?.cancel()
                downloadManager.remove(it)
            }
        }

        val filePath = metadata?.localPath ?: state?.filePath
        val deleteFileResult = runCatching {
            if (!filePath.isNullOrBlank()) {
                val file = File(filePath)
                if (file.exists() && !file.delete()) {
                    throw IllegalStateException("Failed to delete file")
                }
            }
        }

        prefs.edit()
            .remove(downloadKey(itemId))
            .remove(metadataKey(itemId))
            .apply()
        stateFlows.remove(itemId)
        refreshTrackedDownloads()

        deleteFileResult.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun enqueueItemDownload(item: BaseItemDto): Result<Long> = withContext(Dispatchers.IO) {
        val itemId = item.id ?: return@withContext Result.failure(Exception("Item ID unavailable"))
        val stateFlow = stateFlows.getOrPut(itemId) { MutableStateFlow(ItemDownloadState()) }

        val current = stateFlow.value
        if (current.status == DownloadStatus.COMPLETED && current.downloadId != null) {
            return@withContext Result.success(current.downloadId)
        }

        val existingId = prefs.getLong(downloadKey(itemId), -1L).takeIf { it > 0L }
        if (existingId != null) {
            val snapshot = queryDownloadSnapshot(existingId)
            if (snapshot != null && snapshot.status in RUNNING_STATUSES) {
                updateStateFromSnapshot(itemId, existingId, snapshot)
                startPolling(itemId, existingId)
                return@withContext Result.success(existingId)
            }
        }

        val requestData = mediaRepository.getItemDownloadRequest(itemId).getOrElse {
            val failureState = ItemDownloadState(
                status = DownloadStatus.FAILED,
                progress = 0f,
                message = it.message ?: "Failed to prepare download"
            )
            setState(itemId, failureState)
            persistMetadata(
                PersistedDownloadMetadata(
                    itemId = itemId,
                    title = item.name?.takeIf { name -> name.isNotBlank() } ?: "Item $itemId",
                    subtitle = buildSubtitle(item),
                    mediaType = item.type,
                    year = item.productionYear,
                    status = DownloadStatus.FAILED.name,
                    message = failureState.message,
                    fullItemJson = serializeItem(item)
                )
            )
            return@withContext Result.failure(it)
        }

        val destination = createDestinationFile(requestData.fileExtension)
        val request = DownloadManager.Request(Uri.parse(requestData.downloadUrl))
            .setTitle(requestData.displayName)
            .setDescription("Downloading...")
            .setDestinationUri(Uri.fromFile(destination))
            .setAllowedOverRoaming(false)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        if (downloadPreferences.isWifiOnlyDownloadsEnabled()) {
            request.setAllowedOverMetered(false)
            @Suppress("DEPRECATION")
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI)
        } else {
            request.setAllowedOverMetered(true)
        }

        requestData.authToken?.takeIf { it.isNotBlank() }?.let { token ->
            request.addRequestHeader("X-Emby-Token", token)
        }

        return@withContext try {
            val downloadId = downloadManager.enqueue(request)
            prefs.edit().putLong(downloadKey(itemId), downloadId).apply()

            val title = item.name?.takeIf { it.isNotBlank() }
                ?: requestData.displayName
                ?: "Item $itemId"

            persistMetadata(
                PersistedDownloadMetadata(
                    itemId = itemId,
                    title = title,
                    subtitle = buildSubtitle(item),
                    mediaType = item.type,
                    year = item.productionYear,
                    requestedAt = System.currentTimeMillis(),
                    localPath = destination.absolutePath,
                    status = DownloadStatus.QUEUED.name,
                    progress = 0.05f,
                    downloadId = downloadId,
                    fullItemJson = serializeItem(item)
                )
            )

            val queuedState = ItemDownloadState(
                status = DownloadStatus.QUEUED,
                progress = 0.05f,
                downloadId = downloadId,
                filePath = destination.absolutePath
            )
            setState(itemId, queuedState)
            startPolling(itemId, downloadId)
            Result.success(downloadId)
        } catch (e: Exception) {
            val failedState = ItemDownloadState(
                status = DownloadStatus.FAILED,
                progress = 0f,
                message = e.message ?: "Failed to enqueue download"
            )
            setState(itemId, failedState)
            val existingMeta = readMetadata(itemId)
            persistMetadata(
                (existingMeta ?: PersistedDownloadMetadata(
                    itemId = itemId,
                    title = item.name?.takeIf { name -> name.isNotBlank() } ?: "Item $itemId",
                    subtitle = buildSubtitle(item),
                    mediaType = item.type,
                    year = item.productionYear,
                    fullItemJson = serializeItem(item)
                )).copy(
                    status = DownloadStatus.FAILED.name,
                    progress = 0f,
                    message = failedState.message,
                    fullItemJson = existingMeta?.fullItemJson ?: serializeItem(item)
                )
            )
            Result.failure(e)
        }
    }

    suspend fun enqueueSeriesDownload(seriesId: String): Result<Int> = withContext(Dispatchers.IO) {
        val episodes = mediaRepository.getEpisodes(seriesId).getOrElse {
            return@withContext Result.failure(it)
        }
        if (episodes.isEmpty()) {
            return@withContext Result.failure(Exception("No episodes found for this series"))
        }

        var queued = 0
        episodes.forEach { episode ->
            val result = enqueueItemDownload(episode)
            if (result.isSuccess) {
                queued += 1
            }
        }

        if (queued == 0) {
            Result.failure(Exception("No episodes could be queued"))
        } else {
            Result.success(queued)
        }
    }

    private fun startPolling(itemId: String, downloadId: Long) {
        if (pollingJobs[downloadId]?.isActive == true) return
        pollingJobs[downloadId] = scope.launch {
            while (true) {
                val snapshot = queryDownloadSnapshot(downloadId)
                if (snapshot == null) {
                    val meta = readMetadata(itemId)
                    val path = meta?.localPath
                    val fileExists = path?.let { File(it).exists() } == true
                    if (!fileExists) {
                        setState(itemId, ItemDownloadState(status = DownloadStatus.FAILED, message = "Download missing"))
                        persistMetadata(
                            meta?.copy(
                                status = DownloadStatus.FAILED.name,
                                message = "Download missing"
                            ) ?: PersistedDownloadMetadata(
                                itemId = itemId,
                                title = "Item $itemId",
                                status = DownloadStatus.FAILED.name,
                                message = "Download missing"
                            )
                        )
                    }
                    break
                }

                updateStateFromSnapshot(itemId, downloadId, snapshot)
                if (snapshot.status in TERMINAL_STATUSES) {
                    break
                }
                delay(POLL_INTERVAL_MS)
            }
            pollingJobs.remove(downloadId)
        }
    }

    private fun updateStateFromSnapshot(itemId: String, downloadId: Long, snapshot: DownloadSnapshot) {
        val progress = if (snapshot.totalBytes > 0L) {
            (snapshot.downloadedBytes.toFloat() / snapshot.totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        val localPath = snapshot.localUri?.let { uriString ->
            runCatching { Uri.parse(uriString).path }.getOrNull()
        }

        val state = when (snapshot.status) {
            DownloadManager.STATUS_PENDING, DownloadManager.STATUS_PAUSED -> ItemDownloadState(
                status = DownloadStatus.QUEUED,
                progress = if (progress > 0f) progress else 0.05f,
                downloadId = downloadId,
                downloadedBytes = snapshot.downloadedBytes,
                totalBytes = snapshot.totalBytes,
                filePath = localPath
            )
            DownloadManager.STATUS_RUNNING -> ItemDownloadState(
                status = DownloadStatus.DOWNLOADING,
                progress = if (progress > 0f) progress else 0.05f,
                downloadId = downloadId,
                downloadedBytes = snapshot.downloadedBytes,
                totalBytes = snapshot.totalBytes,
                filePath = localPath
            )
            DownloadManager.STATUS_SUCCESSFUL -> ItemDownloadState(
                status = DownloadStatus.COMPLETED,
                progress = 1f,
                downloadId = downloadId,
                downloadedBytes = snapshot.downloadedBytes,
                totalBytes = snapshot.totalBytes,
                filePath = localPath
            )
            DownloadManager.STATUS_FAILED -> ItemDownloadState(
                status = DownloadStatus.FAILED,
                progress = 0f,
                downloadId = downloadId,
                message = mapFailureReason(snapshot.reason),
                downloadedBytes = snapshot.downloadedBytes,
                totalBytes = snapshot.totalBytes,
                filePath = localPath
            )
            else -> ItemDownloadState(
                status = DownloadStatus.QUEUED,
                progress = 0.05f,
                downloadId = downloadId,
                downloadedBytes = snapshot.downloadedBytes,
                totalBytes = snapshot.totalBytes,
                filePath = localPath
            )
        }

        setState(itemId, state)
        val existing = readMetadata(itemId)
        val now = System.currentTimeMillis()
        persistMetadata(
            (existing ?: PersistedDownloadMetadata(itemId = itemId, title = "Item $itemId")).copy(
                status = state.status.name,
                progress = state.progress,
                downloadedBytes = state.downloadedBytes,
                totalBytes = state.totalBytes,
                message = state.message,
                localPath = state.filePath ?: existing?.localPath,
                downloadId = state.downloadId ?: existing?.downloadId,
                completedAt = if (state.status == DownloadStatus.COMPLETED) (existing?.completedAt ?: now) else existing?.completedAt
            )
        )
    }

    private fun setState(itemId: String, state: ItemDownloadState) {
        stateFlows.getOrPut(itemId) { MutableStateFlow(ItemDownloadState()) }.value = state
        refreshTrackedDownloads()
    }

    private fun queryDownloadSnapshot(downloadId: Long): DownloadSnapshot? {
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            val status = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloadedBytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val totalBytes = it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            val reason = it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
            val localUriColumn = it.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val localUri = if (localUriColumn >= 0) it.getString(localUriColumn) else null
            return DownloadSnapshot(status, downloadedBytes, totalBytes, reason, localUri)
        }
    }

    private fun restoreDownloads() {
        val itemIds = allTrackedItemIds()
        itemIds.forEach { itemId ->
            val metadata = readMetadata(itemId) ?: return@forEach
            val downloadId = prefs.getLong(downloadKey(itemId), -1L).takeIf { it > 0L } ?: metadata.downloadId
            val fallbackStatus = parseStatus(metadata.status)
            val fallbackState = ItemDownloadState(
                status = fallbackStatus,
                progress = metadata.progress,
                downloadId = downloadId,
                message = metadata.message,
                downloadedBytes = metadata.downloadedBytes,
                totalBytes = metadata.totalBytes,
                filePath = metadata.localPath
            )

            stateFlows[itemId] = MutableStateFlow(fallbackState)

            if (downloadId != null) {
                val snapshot = queryDownloadSnapshot(downloadId)
                if (snapshot != null) {
                    updateStateFromSnapshot(itemId, downloadId, snapshot)
                    if (snapshot.status in RUNNING_STATUSES) {
                        startPolling(itemId, downloadId)
                    }
                } else {
                    val path = metadata.localPath
                    val available = path?.let { File(it).exists() } == true
                    val recoveredStatus = if (available) DownloadStatus.COMPLETED else fallbackStatus
                    val recoveredState = fallbackState.copy(status = recoveredStatus)
                    setState(itemId, recoveredState)
                    persistMetadata(
                        metadata.copy(
                            status = recoveredStatus.name,
                            completedAt = if (recoveredStatus == DownloadStatus.COMPLETED) {
                                metadata.completedAt ?: System.currentTimeMillis()
                            } else {
                                metadata.completedAt
                            }
                        )
                    )
                }
            }
        }
        refreshTrackedDownloads()
    }

    private fun refreshTrackedDownloads() {
        val tracked = allTrackedItemIds().mapNotNull { itemId ->
            val metadata = readMetadata(itemId) ?: return@mapNotNull null
            val fullItem = parseItem(metadata.fullItemJson)
            val flowState = stateFlows[itemId]?.value
            val status = flowState?.status ?: parseStatus(metadata.status)
            val path = flowState?.filePath ?: metadata.localPath
            val file = path?.let { File(it) }
            val isOffline = status == DownloadStatus.COMPLETED && file?.exists() == true

            val totalBytes = when {
                flowState?.totalBytes != null && flowState.totalBytes > 0L -> flowState.totalBytes
                metadata.totalBytes > 0L -> metadata.totalBytes
                file?.exists() == true -> file.length()
                else -> 0L
            }

            val downloadedBytes = when {
                flowState?.downloadedBytes != null && flowState.downloadedBytes > 0L -> flowState.downloadedBytes
                metadata.downloadedBytes > 0L -> metadata.downloadedBytes
                isOffline -> totalBytes
                else -> 0L
            }

            val state = ItemDownloadState(
                status = status,
                progress = when {
                    status == DownloadStatus.COMPLETED -> 1f
                    flowState != null -> flowState.progress
                    metadata.progress > 0f -> metadata.progress
                    else -> 0f
                },
                downloadId = flowState?.downloadId ?: metadata.downloadId,
                message = flowState?.message ?: metadata.message,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                filePath = path
            )

            val resolvedTitle = fullItem?.name?.takeIf { it.isNotBlank() } ?: metadata.title
            val resolvedType = fullItem?.type ?: metadata.mediaType
            val resolvedYear = fullItem?.productionYear ?: metadata.year
            TrackedDownload(
                itemId = itemId,
                item = fullItem,
                title = resolvedTitle,
                mediaType = resolvedType,
                year = resolvedYear,
                state = state,
                isOfflineAvailable = isOffline,
                fileSizeBytes = if (isOffline) file?.length() else state.totalBytes.takeIf { it > 0L },
                requestedAt = metadata.requestedAt,
                completedAt = metadata.completedAt
            )
        }.sortedWith(
            compareByDescending<TrackedDownload> {
                it.state.status == DownloadStatus.DOWNLOADING || it.state.status == DownloadStatus.QUEUED
            }.thenByDescending { it.completedAt ?: 0L }
                .thenByDescending { it.requestedAt }
        )

        trackedDownloadsFlow.value = tracked
    }

    private fun buildSubtitle(item: BaseItemDto?): String? {
        if (item == null) return null
        return when (item.type) {
            "Episode" -> {
                val season = item.parentIndexNumber?.let { "S${it.toString().padStart(2, '0')}" }
                val episode = item.indexNumber?.let { "E${it.toString().padStart(2, '0')}" }
                val code = listOfNotNull(season, episode).joinToString("")
                val seriesName = item.seriesName?.takeIf { it.isNotBlank() }
                when {
                    code.isNotBlank() && !seriesName.isNullOrBlank() -> "$seriesName • $code"
                    code.isNotBlank() -> code
                    !seriesName.isNullOrBlank() -> seriesName
                    else -> item.seasonName
                }
            }
            else -> null
        }
    }

    private fun serializeItem(item: BaseItemDto?): String? {
        if (item == null) return null
        return runCatching { gson.toJson(item) }.getOrNull()
    }

    private fun parseItem(raw: String?): BaseItemDto? {
        if (raw.isNullOrBlank()) return null
        return runCatching { gson.fromJson(raw, BaseItemDto::class.java) }.getOrNull()
    }

    private fun createDestinationFile(extension: String?): File {
        val baseDir = appContext.filesDir.resolve("downloads")
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }

        val safeExtension = extension
            ?.trim()
            ?.trimStart('.')
            ?.lowercase()
            ?.takeIf { it.matches(Regex("^[a-z0-9]{1,8}$")) }

        val encryptedName = UUID.randomUUID().toString()
        val fileName = if (safeExtension != null) "$encryptedName.$safeExtension" else encryptedName
        return File(baseDir, fileName)
    }

    private fun mapFailureReason(reason: Int): String {
        return when (reason) {
            DownloadManager.ERROR_CANNOT_RESUME -> "Cannot resume download"
            DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage device not found"
            DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "File already exists"
            DownloadManager.ERROR_FILE_ERROR -> "File write error"
            DownloadManager.ERROR_HTTP_DATA_ERROR -> "Network data error"
            DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Insufficient storage space"
            DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
            DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "Unhandled server response"
            DownloadManager.ERROR_UNKNOWN -> "Unknown download error"
            else -> "Download failed"
        }
    }

    private fun parseStatus(raw: String?): DownloadStatus {
        return runCatching { DownloadStatus.valueOf(raw ?: DownloadStatus.IDLE.name) }
            .getOrElse { DownloadStatus.IDLE }
    }

    private fun persistMetadata(metadata: PersistedDownloadMetadata) {
        prefs.edit().putString(metadataKey(metadata.itemId), gson.toJson(metadata)).apply()
    }

    private fun readMetadata(itemId: String): PersistedDownloadMetadata? {
        val raw = prefs.getString(metadataKey(itemId), null) ?: return null
        return runCatching { gson.fromJson(raw, PersistedDownloadMetadata::class.java) }.getOrNull()
    }

    private fun allTrackedItemIds(): Set<String> {
        return prefs.all.keys
            .filter { it.startsWith(METADATA_PREFIX) || it.startsWith(DOWNLOAD_PREFIX) }
            .map {
                when {
                    it.startsWith(METADATA_PREFIX) -> it.removePrefix(METADATA_PREFIX)
                    it.startsWith(DOWNLOAD_PREFIX) -> it.removePrefix(DOWNLOAD_PREFIX)
                    else -> ""
                }
            }
            .filter { it.isNotBlank() }
            .toSet()
    }

    private fun downloadKey(itemId: String): String = "$DOWNLOAD_PREFIX$itemId"
    private fun metadataKey(itemId: String): String = "$METADATA_PREFIX$itemId"

    private data class DownloadSnapshot(
        val status: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val reason: Int,
        val localUri: String?
    )

    companion object {
        private const val PREFS_NAME = "jellycine_download_state"
        private const val DOWNLOAD_PREFIX = "download_item_"
        private const val METADATA_PREFIX = "download_meta_"
        private const val POLL_INTERVAL_MS = 750L

        private val RUNNING_STATUSES = setOf(
            DownloadManager.STATUS_PENDING,
            DownloadManager.STATUS_PAUSED,
            DownloadManager.STATUS_RUNNING
        )

        private val TERMINAL_STATUSES = setOf(
            DownloadManager.STATUS_SUCCESSFUL,
            DownloadManager.STATUS_FAILED
        )
    }
}

