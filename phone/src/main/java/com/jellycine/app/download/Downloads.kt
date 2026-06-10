package com.jellycine.app.download

import com.jellycine.app.ui.components.common.isPausedDownloadState
import com.jellycine.data.download.DownloadDestination
import com.jellycine.data.download.DownloadMessages
import com.jellycine.data.download.DownloadMetadataStore
import com.jellycine.data.download.DownloadStorage
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.DownloadStatus
import com.jellycine.data.model.ItemDownloadState
import com.jellycine.data.model.PersistedDownloadMetadata
import com.jellycine.data.model.TrackedDownload
import com.jellycine.data.repository.MediaRepository.ItemDownloadRequest
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedOutputStream
import java.io.EOFException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLException

internal data class RecoveryDecision(
    val status: DownloadStatus,
    val message: String?
)

internal data class QueuedDownloadRequest(
    val item: BaseItemDto,
    val requestData: ItemDownloadRequest,
    val destination: DownloadDestination,
    val downloadId: Long
)

internal class InsufficientStorageException(
    val fileSizeBytes: Long? = null,
    val availableBytes: Long,
    val neededBytes: Long
) : IllegalStateException("Insufficient storage space")

internal class DownloadQueue {
    private val lock = Any()
    private val pendingQueue = ArrayDeque<QueuedDownloadRequest>()
    private val pendingItemIds = mutableSetOf<String>()

    fun contains(itemId: String): Boolean {
        return synchronized(lock) { pendingItemIds.contains(itemId) }
    }

    fun add(request: QueuedDownloadRequest): Boolean {
        val itemId = request.item.id ?: return false
        return synchronized(lock) {
            if (pendingItemIds.contains(itemId)) {
                false
            } else {
                pendingQueue.addLast(request)
                pendingItemIds.add(itemId)
                true
            }
        }
    }

    fun remove(itemId: String) {
        synchronized(lock) {
            if (pendingItemIds.remove(itemId)) {
                val iterator = pendingQueue.iterator()
                while (iterator.hasNext()) {
                    if (iterator.next().item.id == itemId) {
                        iterator.remove()
                        break
                    }
                }
            }
        }
    }

    fun next(
        activeCount: Int,
        maxConcurrent: Int,
        shouldSkip: (String) -> Boolean
    ): QueuedDownloadRequest? {
        return synchronized(lock) {
            if (activeCount >= maxConcurrent || pendingQueue.isEmpty()) {
                return@synchronized null
            }

            while (pendingQueue.isNotEmpty()) {
                val candidate = pendingQueue.removeFirst()
                val candidateId = candidate.item.id
                if (candidateId != null) {
                    pendingItemIds.remove(candidateId)
                    if (!shouldSkip(candidateId)) {
                        return@synchronized candidate
                    }
                }
            }
            null
        }
    }
}

internal class DownloadTransfer(
    private val httpClient: OkHttpClient,
    private val storage: DownloadStorage,
    private val messages: DownloadMessages,
    private val ensureStorageCapacity: (fileSizeBytes: Long?, neededBytes: Long) -> Unit,
    private val onCallCreated: (itemId: String, call: Call) -> Unit,
    private val onState: (itemId: String, state: ItemDownloadState, forcePersist: Boolean, forceRefresh: Boolean) -> Unit
) {
    fun execute(
        itemId: String,
        requestData: ItemDownloadRequest,
        destination: DownloadDestination,
        downloadId: Long,
        initialBytes: Long
    ) {
        val requestBuilder = Request.Builder()
            .url(requestData.downloadUrl)

        requestData.authToken?.takeIf { it.isNotBlank() }?.let { token ->
            requestBuilder.header("X-Emby-Token", token)
        }

        val fileExists = storage.exists(destination.location)
        val resumeFrom = if (fileExists) storage.length(destination.location) else initialBytes
        if (resumeFrom > 0L) {
            requestBuilder.header("Range", "bytes=$resumeFrom-")
        }

        val call = httpClient.newCall(requestBuilder.build())
        onCallCreated(itemId, call)

        call.execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException(messages.serverError(response.code))
            }

            val body = response.body
            val append = response.code == 206 && resumeFrom > 0L
            val downloadedStart = if (append) resumeFrom else 0L
            if (!append && storage.exists(destination.location) && storage.length(destination.location) > 0L) {
                storage.outputStream(destination.location, append = false).use { /* truncate partial file */ }
            }
            val totalBytes = if (body.contentLength() > 0L) downloadedStart + body.contentLength() else 0L
            if (totalBytes > 0L) {
                ensureStorageCapacity(totalBytes, (totalBytes - downloadedStart).coerceAtLeast(0L))
            }

            body.byteStream().use { input ->
                BufferedOutputStream(
                    storage.outputStream(destination.location, append),
                    OUTPUT_BUFFER_SIZE_BYTES
                ).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE_BYTES)
                    var downloadedBytes = downloadedStart
                    var bytesRead: Int
                    var lastEmitAt = 0L
                    var lastCapacityCheckedAt = downloadedStart

                    while (true) {
                        bytesRead = input.read(buffer)
                        if (bytesRead == -1) break

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        if (downloadedBytes - lastCapacityCheckedAt >= CAPACITY_CHECK_INTERVAL_BYTES) {
                            lastCapacityCheckedAt = downloadedBytes
                            val neededBytes = if (totalBytes > 0L) {
                                (totalBytes - downloadedBytes).coerceAtLeast(0L)
                            } else {
                                CAPACITY_CHECK_INTERVAL_BYTES
                            }
                            if (neededBytes > 0L) {
                                ensureStorageCapacity(totalBytes.takeIf { it > 0L }, neededBytes)
                            }
                        }

                        val now = System.currentTimeMillis()
                        if (now - lastEmitAt >= PROGRESS_EMIT_INTERVAL_MS) {
                            lastEmitAt = now
                            onState(
                                itemId,
                                ItemDownloadState(
                                    status = DownloadStatus.DOWNLOADING,
                                    progress = downloadProgressFromBytes(downloadedBytes, totalBytes),
                                    downloadId = downloadId,
                                    downloadedBytes = downloadedBytes,
                                    totalBytes = totalBytes,
                                    filePath = destination.location
                                ),
                                false,
                                false
                            )
                        }
                    }
                    output.flush()
                    storage.publish(destination.location)
                    onState(
                        itemId,
                        ItemDownloadState(
                            status = DownloadStatus.COMPLETED,
                            progress = 1f,
                            downloadId = downloadId,
                            downloadedBytes = downloadedBytes,
                            totalBytes = if (totalBytes > 0L) totalBytes else downloadedBytes,
                            filePath = destination.location
                        ),
                        true,
                        true
                    )
                }
            }
        }
    }

    companion object {
        fun isRecoverableInterruption(error: Throwable): Boolean {
            var current: Throwable? = error
            while (current != null) {
                when (current) {
                    is UnknownHostException,
                    is ConnectException,
                    is NoRouteToHostException,
                    is SocketTimeoutException,
                    is SocketException,
                    is InterruptedIOException,
                    is EOFException,
                    is ProtocolException,
                    is SSLException -> return true
                }
                current = current.cause
            }
            return false
        }

        private const val BUFFER_SIZE_BYTES = 256 * 1024
        private const val OUTPUT_BUFFER_SIZE_BYTES = 1024 * 1024
        private const val CAPACITY_CHECK_INTERVAL_BYTES = 64L * 1024 * 1024
        private const val PROGRESS_EMIT_INTERVAL_MS = 300L
    }
}

internal fun downloadProgressFromBytes(downloadedBytes: Long, totalBytes: Long): Float {
    if (downloadedBytes > 0L && totalBytes > 0L) {
        return (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    }
    return 0f
}

internal class DownloadTracker(
    private val storage: DownloadStorage,
    private val metadataStore: DownloadMetadataStore,
    private val stateFlows: ConcurrentHashMap<String, MutableStateFlow<ItemDownloadState>>,
    private val pausedStatusMessage: () -> String
) {
    fun recoveryDecision(
        metadata: PersistedDownloadMetadata,
        fallbackStatus: DownloadStatus,
        messages: DownloadMessages
    ): RecoveryDecision {
        val location = metadata.localPath
        val available = !location.isNullOrBlank() && storage.exists(location)
        val locationBytes = if (available && location != null) storage.length(location) else 0L
        val hasPartialData = locationBytes > 0L
        val wasPaused = isPausedDownloadState(fallbackStatus, metadata.message, pausedStatusMessage())
        val wasInFlight = fallbackStatus == DownloadStatus.DOWNLOADING ||
            (fallbackStatus == DownloadStatus.QUEUED && !wasPaused)

        val recoveredState = if (fallbackStatus != DownloadStatus.COMPLETED || !available) {
            false
        } else if (metadata.totalBytes > 0L) {
            locationBytes >= metadata.totalBytes
        } else {
            metadata.completedAt != null || metadata.progress >= 1f
        }

        return when {
            recoveredState -> RecoveryDecision(DownloadStatus.COMPLETED, metadata.message)
            wasPaused && available -> RecoveryDecision(DownloadStatus.QUEUED, pausedStatusMessage())
            wasInFlight -> RecoveryDecision(DownloadStatus.QUEUED, messages.resuming)
            fallbackStatus == DownloadStatus.FAILED && hasPartialData ->
                RecoveryDecision(DownloadStatus.QUEUED, messages.resuming)
            fallbackStatus == DownloadStatus.COMPLETED && !available ->
                RecoveryDecision(DownloadStatus.FAILED, messages.downloadIncomplete)
            else -> RecoveryDecision(fallbackStatus, metadata.message)
        }
    }

    fun build(): List<TrackedDownload> {
        return metadataStore.allTrackedItemIds().mapNotNull { itemId ->
            val metadata = metadataStore.read(itemId) ?: return@mapNotNull null
            val fullItem = metadataStore.parseItem(metadata.fullItemJson, metadata.itemId)
            val flowState = stateFlows[itemId]?.value
            val status = flowState?.status ?: metadataStore.parseStatus(metadata.status)
            val path = flowState?.filePath ?: metadata.localPath
            val pathExists = path?.let(storage::exists) == true
            val pathBytes = if (pathExists && path != null) storage.length(path) else 0L
            val isOffline = status == DownloadStatus.COMPLETED && pathExists

            val totalBytes = when {
                flowState?.totalBytes != null && flowState.totalBytes > 0L -> flowState.totalBytes
                metadata.totalBytes > 0L -> metadata.totalBytes
                pathExists -> pathBytes
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
                filePath = path,
                storageShortageInfo = flowState?.storageShortageInfo
            )

            TrackedDownload(
                itemId = itemId,
                item = fullItem,
                title = fullItem?.name?.takeIf { it.isNotBlank() } ?: metadata.title,
                mediaType = fullItem?.type ?: metadata.mediaType,
                year = fullItem?.productionYear ?: metadata.year,
                state = state,
                isOfflineAvailable = isOffline,
                fileSizeBytes = if (isOffline) pathBytes else state.totalBytes.takeIf { it > 0L },
                requestedAt = metadata.requestedAt,
                completedAt = metadata.completedAt
            )
        }.sortedWith(
            compareByDescending<TrackedDownload> {
                it.state.status == DownloadStatus.DOWNLOADING || it.state.status == DownloadStatus.QUEUED
            }.thenByDescending { it.completedAt ?: 0L }
                .thenByDescending { it.requestedAt }
        )
    }
}