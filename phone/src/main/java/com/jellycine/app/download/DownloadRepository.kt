package com.jellycine.app.download

import android.content.Context
import com.jellycine.shared.preferences.Preferences
import com.jellycine.app.ui.components.common.isPausedDownloadState
import com.jellycine.app.ui.components.common.pausedDownloadMessage
import com.jellycine.data.model.BatchDownloadCandidate
import com.jellycine.data.model.BatchDownloadEstimate
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.DownloadStatus
import com.jellycine.data.model.ItemDownloadState
import com.jellycine.data.model.DownloadItemMetadata
import com.jellycine.data.model.StorageShortageInfo
import com.jellycine.data.model.TrackedDownload
import com.jellycine.data.model.UserItemDataDto
import com.jellycine.data.model.PersistedDownloadMetadata
import com.jellycine.data.download.DownloadMetadataStore
import com.jellycine.data.download.DownloadDestination
import com.jellycine.data.download.DownloadMessages
import com.jellycine.data.download.DownloadStorage
import com.jellycine.data.network.NetworkModule
import com.jellycine.data.preferences.DownloadPreferences
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

class DownloadRepository(context: Context) {
    private val appContext = context.applicationContext
    private val mediaRepository = MediaRepositoryProvider.getInstance(appContext)
    private val preferences = Preferences(appContext)
    private val downloadPreferences = DownloadPreferences(appContext)
    private val storage = DownloadStorage(appContext, downloadPreferences)
    private val metadataStore = DownloadMetadataStore(appContext)
    private val messages = DownloadMessages(appContext)
    private val queue = DownloadQueue()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val httpClient = OkHttpClient.Builder().retryOnConnectionFailure(true).build()

    private val stateFlows = ConcurrentHashMap<String, MutableStateFlow<ItemDownloadState>>()
    private val downloadTracker = DownloadTracker(
        storage = storage,
        metadataStore = metadataStore,
        stateFlows = stateFlows,
        pausedStatusMessage = { pausedStatusMessage }
    )
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, Call>()
    private val transfer = DownloadTransfer(
        httpClient = httpClient,
        storage = storage,
        messages = messages,
        ensureStorageCapacity = ::ensureStorageCapacity,
        onCallCreated = { itemId, call -> activeCalls[itemId] = call },
        onState = { itemId, state, forcePersist, forceRefresh ->
            applyState(itemId, state, forcePersist = forcePersist, forceRefreshTracked = forceRefresh)
        }
    )
    private val pausedItems = CopyOnWriteArraySet<String>()
    private val canceledItems = CopyOnWriteArraySet<String>()
    private val pausedStatusMessage: String
        get() = pausedDownloadMessage(appContext)
    private val lastMetadataPersistAt = ConcurrentHashMap<String, Long>()
    private val lastTrackedRefreshAt = ConcurrentHashMap<String, Long>()
    private val trackedDownloadsFlow = MutableStateFlow<List<TrackedDownload>>(emptyList())
    private var networkObserverJob: Job? = null
    @Volatile
    private var lastForegroundSyncActive = false

    init {
        restoreDownloads()
        observeNetworkRestoration()
    }

    fun observeItemDownload(itemId: String): StateFlow<ItemDownloadState> {
        return stateFlows.getOrPut(itemId) { MutableStateFlow(ItemDownloadState()) }.asStateFlow()
    }

    fun observeTrackedDownloads(): StateFlow<List<TrackedDownload>> = trackedDownloadsFlow.asStateFlow()
    fun trackedDownloadsSnapshot(): List<TrackedDownload> = trackedDownloadsFlow.value

    fun getOfflineFilePath(itemId: String): String? {
        val statePath = stateFlows[itemId]?.value?.filePath
        val metadataPath = metadataStore.read(itemId)?.localPath
        return sequenceOf(statePath, metadataPath)
            .filterNotNull()
            .firstOrNull(storage::exists)
    }

    fun offlineItemMetadata(itemId: String): BaseItemDto? {
        return metadataStore.read(itemId)?.let { metadata ->
            metadataStore.parseItem(metadata.fullItemJson, metadata.itemId)
        }
    }

    fun updatePlaybackPosition(
        itemId: String,
        positionMs: Long,
        markCompleted: Boolean = false
    ) {
        scope.launch {
            val metadata = metadataStore.read(itemId) ?: return@launch
            val item = metadataStore.parseItem(metadata.fullItemJson, metadata.itemId) ?: return@launch
            val runtimeTicks = item.runTimeTicks?.takeIf { it > 0L }
            val rawPositionTicks = positionMs.coerceAtLeast(0L) * 10_000L
            val isNearStart = runtimeTicks?.let { rawPositionTicks <= it / 10L } == true
            val isNearEnd = runtimeTicks?.let { rawPositionTicks >= (it * 9L) / 10L } == true
            val played = markCompleted || isNearEnd
            val playbackPositionTicks = if (played || isNearStart) 0L else rawPositionTicks
            val playedPercentage = when {
                runtimeTicks == null -> item.userData?.playedPercentage
                played -> 100.0
                playbackPositionTicks == 0L -> 0.0
                else -> ((rawPositionTicks.toDouble() / runtimeTicks.toDouble()) * 100.0)
                    .coerceIn(0.0, 100.0)
            }
            val updatedUserData = (item.userData ?: UserItemDataDto()).copy(
                playbackPositionTicks = playbackPositionTicks,
                playedPercentage = playedPercentage,
                played = played
            )
            val updatedItem = item.copy(userData = updatedUserData)
            metadataStore.persist(
                metadata.copy(fullItemJson = metadataStore.serializeItem(updatedItem))
            )
            refreshTrackedDownloads()
        }
    }

    suspend fun deleteDownloadedItem(itemId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val metadata = metadataStore.read(itemId)
        val state = stateFlows[itemId]?.value
        pausedItems.remove(itemId)
        canceledItems.add(itemId)
        removePendingQueue(itemId)
        activeCalls.remove(itemId)?.cancel()
        val activeJob = activeJobs.remove(itemId)
        activeJob?.cancel()
        if (activeJob != null) {
            withTimeoutOrNull(downloadPreferences.getCancelDeleteWaitMs()) {
                activeJob.join()
            }
        }

        val filePath = metadata?.localPath ?: state?.filePath
        val deleteFileResult = runCatching {
            if (!filePath.isNullOrBlank()) {
                if (!storage.deleteWithRetry(filePath)) {
                    throw IllegalStateException(messages.deleteFileFailed)
                }
            }
        }

        metadataStore.remove(itemId)
        stateFlows[itemId]?.value = ItemDownloadState()
        stateFlows.remove(itemId)
        lastMetadataPersistAt.remove(itemId)
        lastTrackedRefreshAt.remove(itemId)
        refreshTrackedDownloads()
        drainPendingQueue()

        deleteFileResult.fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(it) }
        )
    }

    suspend fun enqueueItemDownload(item: BaseItemDto): Result<Long> = withContext(Dispatchers.IO) {
        val itemId = item.id ?: return@withContext Result.failure(Exception(messages.itemIdUnavailable))
        val current = stateFlows.getOrPut(itemId) { MutableStateFlow(ItemDownloadState()) }.value
        val metadata = metadataStore.read(itemId)
        val currentDownloadId = current.downloadId
        if (current.status == DownloadStatus.COMPLETED && currentDownloadId != null) {
            return@withContext Result.success(currentDownloadId)
        }

        if (isQueued(itemId)) {
            val queuedDownloadId = currentDownloadId ?: metadata?.downloadId ?: metadataStore.nextDownloadId()
            return@withContext Result.success(queuedDownloadId)
        }

        val requestData = mediaRepository.getItemDownloadRequest(itemId).getOrElse {
            val failureState = ItemDownloadState(
                status = DownloadStatus.FAILED,
                progress = 0f,
                message = it.message ?: messages.prepareDownloadFailed
            )
            setFlowState(itemId, failureState)
            metadataStore.persist(
                PersistedDownloadMetadata(
                    itemId = itemId,
                    title = item.name?.takeIf { name -> name.isNotBlank() } ?: messages.itemFallbackTitle(itemId),
                    subtitle = DownloadItemMetadata.subtitle(item),
                    mediaType = item.type,
                    year = item.productionYear,
                    status = DownloadStatus.FAILED.name,
                    message = failureState.message,
                    fullItemJson = metadataStore.serializeItem(item)
                )
            )
            return@withContext Result.failure(it)
        }

        val metadataItem = downloadMetadataItem(item).let { resolvedItem ->
            if (resolvedItem.id.isNullOrBlank()) {
                resolvedItem.copy(id = itemId)
            } else {
                resolvedItem
            }
        }
        val downloadId = metadata?.downloadId ?: metadataStore.nextDownloadId()
        metadataStore.saveDownloadId(itemId, downloadId)
        val title = metadataItem.name?.takeIf { it.isNotBlank() }
            ?: requestData.displayName.takeIf { it.isNotBlank() }
            ?: messages.itemFallbackTitle(itemId)

        pausedItems.remove(itemId)
        canceledItems.remove(itemId)
        if (preferences.isWifiOnlyDownloadsEnabled() && !NetworkModule.isWifiConnected(appContext)) {
            val downloadedBytes = knownDownloadedBytes(current = current, metadata = metadata)
            val totalBytes = knownTotalBytes(current = current, metadata = metadata)
            val failureMessage = messages.wifiRequired
            setFlowState(
                itemId,
                ItemDownloadState(
                    status = DownloadStatus.FAILED,
                    progress = 0f,
                    downloadId = downloadId,
                    message = failureMessage,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    filePath = metadata?.localPath
                )
            )
            metadataStore.persist(
                PersistedDownloadMetadata(
                    itemId = itemId,
                    title = title,
                    subtitle = DownloadItemMetadata.subtitle(metadataItem),
                    mediaType = metadataItem.type,
                    year = metadataItem.productionYear,
                    requestedAt = metadata?.requestedAt ?: System.currentTimeMillis(),
                    localPath = metadata?.localPath,
                    status = DownloadStatus.FAILED.name,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    message = failureMessage,
                    downloadId = downloadId,
                    fullItemJson = metadataStore.serializeItem(metadataItem)
                )
            )
            refreshTrackedDownloads()
            return@withContext Result.failure(Exception(failureMessage))
        }

        val destination = metadata?.localPath
            ?.takeIf { it.isNotBlank() }
            ?.let(::DownloadDestination)
            ?: storage.createDestination(
                extension = requestData.fileExtension,
                displayName = requestData.displayName,
                item = metadataItem
            )
        val downloadedBytes = knownDownloadedBytes(
            current = current,
            metadata = metadata,
            destination = destination
        )
        val totalBytes = knownTotalBytes(current = current, metadata = metadata)
        val queuedProgress = downloadProgressFromBytes(
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes
        )

        metadataStore.persist(
            PersistedDownloadMetadata(
                itemId = itemId,
                title = title,
                subtitle = DownloadItemMetadata.subtitle(metadataItem),
                mediaType = metadataItem.type,
                year = metadataItem.productionYear,
                requestedAt = metadata?.requestedAt ?: System.currentTimeMillis(),
                localPath = destination.location,
                status = DownloadStatus.QUEUED.name,
                progress = queuedProgress,
                downloadedBytes = downloadedBytes,
                totalBytes = totalBytes,
                downloadId = downloadId,
                fullItemJson = metadataStore.serializeItem(metadataItem)
            )
        )

        val queuedState = ItemDownloadState(
            status = DownloadStatus.QUEUED,
            progress = queuedProgress,
            downloadId = downloadId,
            downloadedBytes = downloadedBytes,
            totalBytes = totalBytes,
            filePath = destination.location
        )
        setFlowState(itemId, queuedState)
        enqueuePendingTransfer(
            QueuedDownloadRequest(
                item = metadataItem,
                requestData = requestData,
                destination = destination,
                downloadId = downloadId
            )
        )
        Result.success(downloadId)
    }

    suspend fun buildSeriesDownloadEstimate(seriesId: String): Result<BatchDownloadEstimate> = withContext(Dispatchers.IO) {
        val episodes = mediaRepository.getEpisodes(seriesId).getOrElse {
            return@withContext Result.failure(it)
        }
        buildEpisodeBatchEstimate(
            episodes = episodes,
            emptyError = messages.noEpisodesFoundForSeries
        )
    }

    suspend fun buildEpisodeBatchEstimate(
        episodes: List<BaseItemDto>,
        emptyError: String = messages.noEpisodesFoundToDownload
    ): Result<BatchDownloadEstimate> = withContext(Dispatchers.IO) {
        val normalizedEpisodes = episodes
            .filter { !it.id.isNullOrBlank() }
            .distinctBy { it.id }
        if (normalizedEpisodes.isEmpty()) {
            return@withContext Result.failure(Exception(emptyError))
        }

        val candidates = normalizedEpisodes.map { episode ->
            val episodeId = episode.id.orEmpty()
            val estimatedBytes = estimateDownloadSizeBytes(episode)
            val alreadyDownloadedBytes = downloadedBytesForItem(episodeId)
            val remainingBytes = estimatedBytes?.let { size ->
                (size - alreadyDownloadedBytes).coerceAtLeast(0L)
            }

            BatchDownloadCandidate(
                item = episode,
                remainingBytes = remainingBytes
            )
        }

        val requiredBytes = candidates.sumOf { it.remainingBytes ?: 0L }
        val availableBytes = storage.availableBytes()

        Result.success(
            BatchDownloadEstimate(
                candidates = candidates,
                availableBytes = availableBytes,
                requiredBytes = requiredBytes
            )
        )
    }

    suspend fun enqueueEpisodeDownloads(episodes: List<BaseItemDto>): Result<Int> = withContext(Dispatchers.IO) {
        enqueueEpisodeBatch(
            episodes = episodes,
            emptyError = messages.noEpisodesSelected
        )
    }

    fun pauseDownload(itemId: String) {
        canceledItems.remove(itemId)
        pausedItems.add(itemId)
        removePendingQueue(itemId)
        activeCalls.remove(itemId)?.cancel()
        activeJobs.remove(itemId)?.cancel()
        markQueuedState(itemId, message = pausedStatusMessage)
        drainPendingQueue()
    }

    fun resumeDownload(itemId: String) {
        canceledItems.remove(itemId)
        pausedItems.remove(itemId)
        val metadata = metadataStore.read(itemId) ?: return
        val fullItem = metadataStore.parseItem(metadata.fullItemJson, metadata.itemId) ?: return
        scope.launch {
            enqueueItemDownload(fullItem)
        }
    }

    fun cancelDownload(itemId: String) {
        pausedItems.remove(itemId)
        canceledItems.add(itemId)
        removePendingQueue(itemId)
        scope.launch {
            deleteDownloadedItem(itemId)
        }
    }

    fun onDownloadPreferencesChanged() {
        drainPendingQueue()
    }

    private fun isQueued(itemId: String): Boolean {
        if (activeJobs[itemId]?.isActive == true) {
            return true
        }
        return queue.contains(itemId)
    }

    private suspend fun enqueueEpisodeBatch(
        episodes: List<BaseItemDto>,
        emptyError: String
    ): Result<Int> {
        val estimate = buildEpisodeBatchEstimate(
            episodes = episodes,
            emptyError = emptyError
        ).getOrElse {
            return Result.failure(it)
        }

        if (estimate.exceedsStorage) {
            return Result.failure(
                Exception(messages.notEnoughStorage)
            )
        }

        var queued = 0
        estimate.candidates.forEach { candidate ->
            val episode = candidate.item
            val result = enqueueItemDownload(episode)
            if (result.isSuccess) {
                queued += 1
            }
        }

        return if (queued == 0) {
            Result.failure(Exception(messages.noEpisodesQueued))
        } else {
            Result.success(queued)
        }
    }

    private suspend fun estimateDownloadSizeBytes(item: BaseItemDto): Long? {
        val fromItem = item.mediaSources
            ?.asSequence()
            ?.mapNotNull { mediaSource -> mediaSource.size?.takeIf { it > 0L } }
            ?.maxOrNull()
        if (fromItem != null) return fromItem

        val itemId = item.id ?: return null
        val activeKnownBytes = stateFlows[itemId]?.value?.totalBytes?.takeIf { it > 0L }
        if (activeKnownBytes != null) return activeKnownBytes

        val persistedKnownBytes = metadataStore.read(itemId)?.totalBytes?.takeIf { it > 0L }
        if (persistedKnownBytes != null) return persistedKnownBytes

        return mediaRepository.getPlaybackInfo(itemId).getOrNull()
            ?.mediaSources
            ?.asSequence()
            ?.mapNotNull { mediaSource -> mediaSource.size?.takeIf { it > 0L } }
            ?.maxOrNull()
    }

    private suspend fun downloadMetadataItem(item: BaseItemDto): BaseItemDto {
        val itemId = item.id ?: return item
        return mediaRepository.getItemById(itemId).getOrNull() ?: item
    }

    private fun downloadedBytesForItem(itemId: String): Long {
        if (itemId.isBlank()) return 0L

        val metadata = metadataStore.read(itemId)
        val fileBytes = metadata?.localPath
            ?.takeIf { it.isNotBlank() }
            ?.takeIf(storage::exists)
            ?.let(storage::length)
            ?.takeIf { it > 0L }

        return listOfNotNull(
            fileBytes,
            stateFlows[itemId]?.value?.downloadedBytes?.takeIf { it > 0L },
            metadata?.downloadedBytes?.takeIf { it > 0L }
        ).maxOrNull() ?: 0L
    }

    private fun knownDownloadedBytes(
        current: ItemDownloadState?,
        metadata: PersistedDownloadMetadata?,
        destination: DownloadDestination? = null
    ): Long {
        return listOfNotNull(
            destination?.location
                ?.takeIf(storage::exists)
                ?.let(storage::length),
            current?.downloadedBytes,
            metadata?.downloadedBytes
        ).filter { it > 0L }
            .maxOrNull() ?: 0L
    }

    private fun knownTotalBytes(
        current: ItemDownloadState?,
        metadata: PersistedDownloadMetadata?
    ): Long {
        return current?.totalBytes?.takeIf { it > 0L }
            ?: metadata?.totalBytes?.takeIf { it > 0L }
            ?: 0L
    }

    private fun enqueuePendingTransfer(request: QueuedDownloadRequest) {
        if (queue.add(request)) {
            drainPendingQueue()
        }
    }

    private fun removePendingQueue(itemId: String) {
        queue.remove(itemId)
    }

    private fun drainPendingQueue() {
        val request = queue.next(
            activeCount = activeJobs.values.count { it.isActive },
            maxConcurrent = downloadPreferences.getMaxConcurrentDownloads(),
            shouldSkip = { itemId -> canceledItems.contains(itemId) || pausedItems.contains(itemId) }
        ) ?: return
        startTransfer(
            item = request.item,
            requestData = request.requestData,
            destination = request.destination,
            downloadId = request.downloadId
        )
    }

    private fun startTransfer(
        item: BaseItemDto,
        requestData: ItemDownloadRequest,
        destination: DownloadDestination,
        downloadId: Long
    ) {
        val itemId = item.id ?: return
        activeJobs[itemId]?.cancel()
        activeJobs[itemId] = scope.launch {
            try {
                val initialBytes = destination.location
                    .takeIf(storage::exists)
                    ?.let(storage::length)
                    ?: 0L
                val knownTotalBytes = knownTotalBytes(
                    current = stateFlows[itemId]?.value,
                    metadata = metadataStore.read(itemId)
                )
                storage.prepare(destination)
                val startState = ItemDownloadState(
                    status = DownloadStatus.DOWNLOADING,
                    progress = downloadProgressFromBytes(
                        downloadedBytes = initialBytes,
                        totalBytes = knownTotalBytes
                    ),
                    downloadId = downloadId,
                    downloadedBytes = initialBytes,
                    totalBytes = knownTotalBytes,
                    filePath = destination.location
                )
                applyState(itemId, startState, forcePersist = true, forceRefreshTracked = true)
                transfer.execute(itemId, requestData, destination, downloadId, initialBytes)
            } catch (_: CancellationException) {
                if (canceledItems.contains(itemId)) {
                    return@launch
                }
                if (pausedItems.contains(itemId)) {
                    markQueuedState(
                        itemId = itemId,
                        message = pausedStatusMessage,
                        downloadId = downloadId,
                        filePath = destination.location
                    )
                }
            } catch (e: Exception) {
                if (canceledItems.contains(itemId)) {
                    return@launch
                }
                if (e is InsufficientStorageException) {
                    Failed(
                        itemId = itemId,
                        message = messages.storageShortageDevice,
                        downloadId = downloadId,
                        filePath = destination.location,
                        storageShortageInfo = StorageShortageInfo(
                            fileSizeBytes = e.fileSizeBytes,
                            availableBytes = e.availableBytes,
                            neededBytes = e.neededBytes
                        )
                    )
                    return@launch
                }
                if (pausedItems.contains(itemId)) {
                    markQueuedState(
                        itemId = itemId,
                        message = pausedStatusMessage,
                        downloadId = downloadId,
                        filePath = destination.location
                    )
                } else if (
                    e.message?.contains("Canceled", ignoreCase = true) == true ||
                    DownloadTransfer.isRecoverableInterruption(e)
                ) {
                    markQueuedState(
                        itemId = itemId,
                        message = messages.resuming,
                        downloadId = downloadId,
                        filePath = destination.location
                    )
                } else {
                    Failed(
                        itemId = itemId,
                        message = e.message ?: messages.downloadFailed,
                        downloadId = downloadId,
                        filePath = destination.location
                    )
                }
            } finally {
                activeCalls.remove(itemId)
                activeJobs.remove(itemId)
                drainPendingQueue()
            }
        }
    }

    private fun markQueuedState(
        itemId: String,
        message: String,
        downloadId: Long? = null,
        filePath: String? = null
    ) {
        val current = stateFlows[itemId]?.value
        val queuedState = (current ?: ItemDownloadState(
            downloadId = downloadId,
            filePath = filePath
        )).copy(
            status = DownloadStatus.QUEUED,
            message = message,
            downloadId = current?.downloadId ?: downloadId,
            filePath = current?.filePath ?: filePath
        )
        applyState(itemId, queuedState, forcePersist = true, forceRefreshTracked = true)
    }

    private fun Failed(
        itemId: String,
        message: String,
        downloadId: Long? = null,
        filePath: String? = null,
        storageShortageInfo: StorageShortageInfo? = null
    ) {
        val current = stateFlows[itemId]?.value
        val failedState = ItemDownloadState(
            status = DownloadStatus.FAILED,
            progress = 0f,
            downloadId = current?.downloadId ?: downloadId,
            message = message,
            downloadedBytes = current?.downloadedBytes ?: 0L,
            totalBytes = current?.totalBytes ?: 0L,
            filePath = current?.filePath ?: filePath,
            storageShortageInfo = storageShortageInfo
        )
        applyState(itemId, failedState, forcePersist = true, forceRefreshTracked = true)
    }

    private fun applyState(
        itemId: String,
        state: ItemDownloadState,
        forcePersist: Boolean = false,
        forceRefreshTracked: Boolean = false
    ) {
        val now = System.currentTimeMillis()
        val isTerminal = state.status == DownloadStatus.COMPLETED || state.status == DownloadStatus.FAILED
        val isPaused = isPausedDownloadState(state, pausedStatusMessage)
        val shouldPersist = forcePersist || isTerminal || isPaused ||
            now - (lastMetadataPersistAt[itemId] ?: 0L) >= downloadPreferences.getMetadataPersistIntervalMs()
        val shouldRefreshTracked = forceRefreshTracked || isTerminal || isPaused ||
            now - (lastTrackedRefreshAt[itemId] ?: 0L) >= downloadPreferences.getTrackedRefreshIntervalMs()

        setFlowState(itemId, state, refreshTracked = shouldRefreshTracked)
        if (shouldRefreshTracked) {
            lastTrackedRefreshAt[itemId] = now
        }
        if (!shouldPersist) return

        val metadata = metadataStore.read(itemId)
        metadataStore.persist(
            (metadata ?: PersistedDownloadMetadata(itemId = itemId, title = messages.itemFallbackTitle(itemId))).copy(
                status = state.status.name,
                progress = state.progress,
                downloadedBytes = state.downloadedBytes,
                totalBytes = state.totalBytes,
                message = state.message,
                localPath = state.filePath ?: metadata?.localPath,
                downloadId = state.downloadId ?: metadata?.downloadId,
                completedAt = if (state.status == DownloadStatus.COMPLETED) {
                    metadata?.completedAt ?: now
                } else {
                    metadata?.completedAt
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
        val trackedMetadata = metadataStore.allTrackedItemIds()
            .mapNotNull { itemId ->
                metadataStore.read(itemId)?.let { metadata -> itemId to metadata }
            }
            .sortedBy { (_, metadata) -> metadata.requestedAt }
        val itemsToResume = mutableListOf<BaseItemDto>()
        trackedMetadata.forEach { (itemId, metadata) ->
            val downloadId = metadataStore.downloadId(itemId) ?: metadata.downloadId
            val fallbackStatus = metadataStore.parseStatus(metadata.status)
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
            val decision = downloadTracker.recoveryDecision(
                metadata = metadata,
                fallbackStatus = fallbackStatus,
                messages = messages
            )
            val recoveredState = fallbackState.copy(status = decision.status, message = decision.message)
            setFlowState(itemId, recoveredState)
            metadataStore.persist(
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
            if (
                decision.status == DownloadStatus.QUEUED &&
                !isPausedDownloadState(decision.status, decision.message, pausedStatusMessage)
            ) {
                metadataStore.parseItem(metadata.fullItemJson, metadata.itemId)?.let { parsed ->
                    itemsToResume.add(parsed)
                }
            }
        }
        refreshTrackedDownloads()
        if (itemsToResume.isNotEmpty()) {
            scope.launch {
                itemsToResume.forEach { item ->
                    enqueueItemDownload(item)
                }
            }
        }
    }

    private fun observeNetworkRestoration() {
        if (networkObserverJob?.isActive == true) return
        networkObserverJob = scope.launch {
            NetworkModule.observeNetworkAvailability(appContext).collect { isAvailable ->
                if (isAvailable) {
                    autoResumeQueuedDownloads()
                }
            }
        }
    }

    private suspend fun autoResumeQueuedDownloads() {
        if (preferences.isWifiOnlyDownloadsEnabled() && !NetworkModule.isWifiConnected(appContext)) {
            return
        }

        val candidates = metadataStore.allTrackedItemIds().mapNotNull { itemId ->
            if (canceledItems.contains(itemId)) return@mapNotNull null

            val state = stateFlows[itemId]?.value
            if (state?.status != DownloadStatus.QUEUED) return@mapNotNull null
            if (state != null && isPausedDownloadState(state, pausedStatusMessage)) return@mapNotNull null
            if (isQueued(itemId)) return@mapNotNull null

            metadataStore.read(itemId)?.let { metadata ->
                metadataStore.parseItem(metadata.fullItemJson, metadata.itemId)
            }
        }

        candidates.forEach { item ->
            enqueueItemDownload(item)
        }
    }

    private fun refreshTrackedDownloads() {
        val tracked = downloadTracker.build()
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

    private fun ensureStorageCapacity(
        fileSizeBytes: Long?,
        neededBytes: Long
    ) {
        if (neededBytes <= 0L) return
        val availableBytes = storage.availableBytes()
        if (availableBytes < neededBytes) {
            throw InsufficientStorageException(
                fileSizeBytes = fileSizeBytes,
                availableBytes = availableBytes,
                neededBytes = neededBytes
            )
        }
    }
}

object DownloadRepositoryProvider {
    @Volatile
    private var instance: DownloadRepository? = null

    fun getInstance(context: Context): DownloadRepository {
        return instance ?: synchronized(this) {
            instance ?: DownloadRepository(context.applicationContext).also { instance = it }
        }
    }
}