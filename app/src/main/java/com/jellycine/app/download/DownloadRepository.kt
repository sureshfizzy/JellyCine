package com.jellycine.app.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.google.gson.Gson
import com.jellycine.app.preferences.DownloadPreferences
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository.ItemDownloadRequest
import com.jellycine.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong

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

private data class RecoveryDecision(
    val status: DownloadStatus,
    val message: String?
)

class DownloadRepository(context: Context) {
    private val appContext = context.applicationContext
    private val mediaRepository = MediaRepositoryProvider.getInstance(appContext)
    private val downloadPreferences = DownloadPreferences(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder().retryOnConnectionFailure(true).build()

    private val stateFlows = ConcurrentHashMap<String, MutableStateFlow<ItemDownloadState>>()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val pausedItems = CopyOnWriteArraySet<String>()
    private val canceledItems = CopyOnWriteArraySet<String>()
    private val lastMetadataPersistAt = ConcurrentHashMap<String, Long>()
    private val lastTrackedRefreshAt = ConcurrentHashMap<String, Long>()
    private val trackedDownloadsFlow = MutableStateFlow<List<TrackedDownload>>(emptyList())
    @Volatile
    private var lastForegroundSyncActive = false

    init {
        restoreDownloads()
    }

    fun observeItemDownload(itemId: String): StateFlow<ItemDownloadState> {
        return stateFlows.getOrPut(itemId) { MutableStateFlow(ItemDownloadState()) }.asStateFlow()
    }

    fun observeTrackedDownloads(): StateFlow<List<TrackedDownload>> = trackedDownloadsFlow.asStateFlow()
    fun trackedDownloadsSnapshot(): List<TrackedDownload> = trackedDownloadsFlow.value

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
        pausedItems.remove(itemId)
        canceledItems.add(itemId)
        activeCalls.remove(itemId)?.cancel()
        activeJobs.remove(itemId)?.cancel()

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
            .commit()
        stateFlows.remove(itemId)
        lastMetadataPersistAt.remove(itemId)
        lastTrackedRefreshAt.remove(itemId)
        refreshTrackedDownloads()

        deleteFileResult.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun enqueueItemDownload(item: BaseItemDto): Result<Long> = withContext(Dispatchers.IO) {
        val itemId = item.id ?: return@withContext Result.failure(Exception("Item ID unavailable"))
        val current = stateFlows.getOrPut(itemId) { MutableStateFlow(ItemDownloadState()) }.value
        if (current.status == DownloadStatus.COMPLETED && current.downloadId != null) {
            return@withContext Result.success(current.downloadId)
        }

        if (activeJobs[itemId]?.isActive == true) {
            return@withContext Result.success(current.downloadId ?: readMetadata(itemId)?.downloadId ?: nextDownloadId())
        }

        val requestData = mediaRepository.getItemDownloadRequest(itemId).getOrElse {
            val failureState = ItemDownloadState(
                status = DownloadStatus.FAILED,
                progress = 0f,
                message = it.message ?: "Failed to prepare download"
            )
            setFlowState(itemId, failureState)
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

        val existingMeta = readMetadata(itemId)
        val destination = existingMeta?.localPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: createDestinationFile(requestData.fileExtension)
        val downloadId = existingMeta?.downloadId ?: nextDownloadId()
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
                requestedAt = existingMeta?.requestedAt ?: System.currentTimeMillis(),
                localPath = destination.absolutePath,
                status = DownloadStatus.QUEUED.name,
                progress = 0.05f,
                downloadId = downloadId,
                fullItemJson = existingMeta?.fullItemJson ?: serializeItem(item)
            )
        )

        pausedItems.remove(itemId)
        canceledItems.remove(itemId)
        if (downloadPreferences.isWifiOnlyDownloadsEnabled() && !isWifiConnected()) {
            Failed(
                itemId = itemId,
                message = "Wi-Fi required for downloads",
                downloadId = downloadId,
                filePath = destination.absolutePath
            )
            return@withContext Result.failure(Exception("Wi-Fi required for downloads"))
        }
        val queuedState = ItemDownloadState(
            status = DownloadStatus.QUEUED,
            progress = 0.05f,
            downloadId = downloadId,
            filePath = destination.absolutePath
        )
        setFlowState(itemId, queuedState)
        startTransfer(item, requestData, destination, downloadId)
        Result.success(downloadId)
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

    fun pauseDownload(itemId: String) {
        canceledItems.remove(itemId)
        pausedItems.add(itemId)
        activeCalls.remove(itemId)?.cancel()
        activeJobs.remove(itemId)?.cancel()
        Paused(itemId)
    }

    fun resumeDownload(itemId: String) {
        canceledItems.remove(itemId)
        pausedItems.remove(itemId)
        val metadata = readMetadata(itemId) ?: return
        val fullItem = parseItem(metadata.fullItemJson) ?: return
        scope.launch {
            enqueueItemDownload(fullItem)
        }
    }

    fun cancelDownload(itemId: String) {
        pausedItems.remove(itemId)
        canceledItems.add(itemId)
        scope.launch {
            deleteDownloadedItem(itemId)
        }
    }

    private fun startTransfer(
        item: BaseItemDto,
        requestData: ItemDownloadRequest,
        destination: File,
        downloadId: Long
    ) {
        val itemId = item.id ?: return
        activeJobs[itemId]?.cancel()
        activeJobs[itemId] = scope.launch {
            try {
                val initialBytes = destination.takeIf { it.exists() }?.length() ?: 0L
                val parent = destination.parentFile
                if (parent != null && !parent.exists()) {
                    parent.mkdirs()
                }
                val startState = ItemDownloadState(
                    status = DownloadStatus.DOWNLOADING,
                    progress = if (initialBytes > 0L) 0.05f else 0.01f,
                    downloadId = downloadId,
                    downloadedBytes = initialBytes,
                    totalBytes = stateFlows[itemId]?.value?.totalBytes ?: 0L,
                    filePath = destination.absolutePath
                )
                applyState(itemId, startState, forcePersist = true, forceRefreshTracked = true)
                transferBytes(itemId, requestData, destination, downloadId, initialBytes)
            } catch (_: CancellationException) {
                if (canceledItems.contains(itemId)) {
                    return@launch
                }
                if (pausedItems.contains(itemId)) {
                    Paused(itemId, downloadId = downloadId, filePath = destination.absolutePath)
                }
            } catch (e: Exception) {
                if (canceledItems.contains(itemId)) {
                    return@launch
                }
                if (pausedItems.contains(itemId) || e.message?.contains("Canceled", ignoreCase = true) == true) {
                    Paused(itemId, downloadId = downloadId, filePath = destination.absolutePath)
                } else {
                    Failed(
                        itemId = itemId,
                        message = e.message ?: "Download failed",
                        downloadId = downloadId,
                        filePath = destination.absolutePath
                    )
                }
            } finally {
                activeCalls.remove(itemId)
                activeJobs.remove(itemId)
            }
        }
    }

    private fun Paused(itemId: String, downloadId: Long? = null, filePath: String? = null) {
        val current = stateFlows[itemId]?.value
        val pausedState = (current ?: ItemDownloadState(
            downloadId = downloadId,
            filePath = filePath
        )).copy(
            status = DownloadStatus.QUEUED,
            message = "Paused",
            downloadId = current?.downloadId ?: downloadId,
            filePath = current?.filePath ?: filePath
        )
        applyState(itemId, pausedState, forcePersist = true, forceRefreshTracked = true)
    }

    private fun Failed(
        itemId: String,
        message: String,
        downloadId: Long? = null,
        filePath: String? = null
    ) {
        val current = stateFlows[itemId]?.value
        val failedState = ItemDownloadState(
            status = DownloadStatus.FAILED,
            progress = 0f,
            downloadId = current?.downloadId ?: downloadId,
            message = message,
            downloadedBytes = current?.downloadedBytes ?: 0L,
            totalBytes = current?.totalBytes ?: 0L,
            filePath = current?.filePath ?: filePath
        )
        applyState(itemId, failedState, forcePersist = true, forceRefreshTracked = true)
    }

    private fun transferBytes(
        itemId: String,
        requestData: ItemDownloadRequest,
        destination: File,
        downloadId: Long,
        initialBytes: Long
    ) {
        val requestBuilder = Request.Builder()
            .url(requestData.downloadUrl)

        requestData.authToken?.takeIf { it.isNotBlank() }?.let { token ->
            requestBuilder.header("X-Emby-Token", token)
        }

        val fileExists = destination.exists()
        val resumeFrom = if (fileExists) destination.length() else initialBytes
        if (resumeFrom > 0L) {
            requestBuilder.header("Range", "bytes=$resumeFrom-")
        }

        val call = httpClient.newCall(requestBuilder.build())
        activeCalls[itemId] = call

        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Server error ${response.code}")
            }

            val body = response.body ?: throw IllegalStateException("Empty response body")
            val append = response.code == 206 && resumeFrom > 0L
            val downloadedStart = if (append) resumeFrom else 0L
            if (!append && destination.exists() && destination.length() > 0L) {
                FileOutputStream(destination, false).use { /* truncate existing partial */ }
            }
            val totalBytes = if (body.contentLength() > 0L) downloadedStart + body.contentLength() else 0L

            body.byteStream().use { input ->
                FileOutputStream(destination, append).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloadedBytes = downloadedStart
                    var bytesRead: Int
                    var lastEmitAt = 0L

                    while (true) {
                        bytesRead = input.read(buffer)
                        if (bytesRead == -1) break
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        val now = System.currentTimeMillis()
                        if (now - lastEmitAt >= PROGRESS_EMIT_INTERVAL_MS) {
                            lastEmitAt = now
                            val progress = if (totalBytes > 0L) {
                                (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }
                            applyState(
                                itemId,
                                ItemDownloadState(
                                    status = DownloadStatus.DOWNLOADING,
                                    progress = if (progress > 0f) progress else 0.01f,
                                    downloadId = downloadId,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    filePath = destination.absolutePath
                                ),
                                forcePersist = false,
                                forceRefreshTracked = false
                            )
                        }
                    }
                    output.flush()
                    applyState(
                        itemId,
                        ItemDownloadState(
                            status = DownloadStatus.COMPLETED,
                            progress = 1f,
                            downloadId = downloadId,
                            downloadedBytes = downloadedBytes,
                            totalBytes = if (totalBytes > 0L) totalBytes else downloadedBytes,
                            filePath = destination.absolutePath
                        ),
                        forcePersist = true,
                        forceRefreshTracked = true
                    )
                }
            }
        }
    }

    private fun applyState(
        itemId: String,
        state: ItemDownloadState,
        forcePersist: Boolean = false,
        forceRefreshTracked: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val isTerminal = state.status == DownloadStatus.COMPLETED || state.status == DownloadStatus.FAILED
        val isPaused = state.message == "Paused"
        val shouldPersist = forcePersist || isTerminal || isPaused ||
            now - (lastMetadataPersistAt[itemId] ?: 0L) >= METADATA_PERSIST_INTERVAL_MS
        val shouldRefreshTracked = forceRefreshTracked || isTerminal || isPaused ||
            now - (lastTrackedRefreshAt[itemId] ?: 0L) >= TRACKED_REFRESH_INTERVAL_MS

        setFlowState(itemId, state, refreshTracked = shouldRefreshTracked)
        if (shouldRefreshTracked) {
            lastTrackedRefreshAt[itemId] = now
        }
        if (!shouldPersist) return

        val existing = readMetadata(itemId)
        persistMetadata(
            (existing ?: PersistedDownloadMetadata(itemId = itemId, title = "Item $itemId")).copy(
                status = state.status.name,
                progress = state.progress,
                downloadedBytes = state.downloadedBytes,
                totalBytes = state.totalBytes,
                message = state.message,
                localPath = state.filePath ?: existing?.localPath,
                downloadId = state.downloadId ?: existing?.downloadId,
                completedAt = if (state.status == DownloadStatus.COMPLETED) {
                    existing?.completedAt ?: now
                } else {
                    existing?.completedAt
                }
            )
        )
        lastMetadataPersistAt[itemId] = now
    }

    private fun setFlowState(itemId: String, state: ItemDownloadState, refreshTracked: Boolean = true) {
        stateFlows.getOrPut(itemId) { MutableStateFlow(ItemDownloadState()) }.value = state
        if (refreshTracked) {
            refreshTrackedDownloads()
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
            val decision = RecoveredState(metadata, fallbackStatus)
            val recoveredState = fallbackState.copy(status = decision.status, message = decision.message)
            setFlowState(itemId, recoveredState)
            persistMetadata(
                metadata.copy(
                    status = decision.status.name,
                    message = decision.message,
                    completedAt = if (decision.status == DownloadStatus.COMPLETED) {
                        metadata.completedAt ?: System.currentTimeMillis()
                    } else {
                        metadata.completedAt
                    }
                )
            )
        }
        refreshTrackedDownloads()
    }

    private fun RecoveredState(
        metadata: PersistedDownloadMetadata,
        fallbackStatus: DownloadStatus
    ): RecoveryDecision {
        val file = metadata.localPath?.let(::File)
        val available = file?.exists() == true
        val wasPaused = fallbackStatus == DownloadStatus.QUEUED && metadata.message == "Paused"
        val wasInFlight = fallbackStatus == DownloadStatus.DOWNLOADING ||
            (fallbackStatus == DownloadStatus.QUEUED && !wasPaused)

        val RecoverAsCompleted = if (fallbackStatus != DownloadStatus.COMPLETED || !available) {
            false
        } else if (metadata.totalBytes > 0L) {
            (file?.length() ?: 0L) >= metadata.totalBytes
        } else {
            metadata.completedAt != null || metadata.progress >= 1f
        }

        return when {
            RecoverAsCompleted -> RecoveryDecision(DownloadStatus.COMPLETED, metadata.message)
            wasPaused && available -> RecoveryDecision(DownloadStatus.QUEUED, "Paused")
            wasInFlight -> RecoveryDecision(DownloadStatus.QUEUED, "Resuming")
            fallbackStatus == DownloadStatus.COMPLETED && !available ->
                RecoveryDecision(DownloadStatus.FAILED, "Download incomplete")
            else -> RecoveryDecision(fallbackStatus, metadata.message)
        }
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
        val hasActiveDownloads = tracked.any {
            it.state.status == DownloadStatus.DOWNLOADING || it.state.status == DownloadStatus.QUEUED
        }
        if (hasActiveDownloads != lastForegroundSyncActive) {
            lastForegroundSyncActive = hasActiveDownloads
            DownloadForegroundService.sync(
                context = appContext,
                hasActiveDownloads = hasActiveDownloads
            )
        }
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

    private fun isWifiConnected(): Boolean {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
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
    private fun nextDownloadId(): Long = ID_GENERATOR.incrementAndGet()

    companion object {
        private const val PREFS_NAME = "jellycine_download_state"
        private const val DOWNLOAD_PREFIX = "download_item_"
        private const val METADATA_PREFIX = "download_meta_"
        private const val PROGRESS_EMIT_INTERVAL_MS = 300L
        private const val METADATA_PERSIST_INTERVAL_MS = 1500L
        private const val TRACKED_REFRESH_INTERVAL_MS = 1000L
        private val ID_GENERATOR = AtomicLong(System.currentTimeMillis())
    }
}
