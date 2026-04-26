package com.jellycine.app.ui.screens.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import dagger.hilt.android.lifecycle.HiltViewModel
import com.jellycine.app.ui.screens.player.mpv.MPVPlayer
import com.jellycine.app.ui.screens.player.mpv.MpvPlayerController
import com.jellycine.data.model.AudioTranscodeMode
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaSource
import com.jellycine.data.model.MediaStream
import com.jellycine.data.model.PlaybackRequest
import com.jellycine.data.repository.MediaRepository
import com.jellycine.detail.CodecCapabilityManager
import com.jellycine.player.audio.SpatializerHelper
import com.jellycine.player.core.PlaybackMarkerUtils
import com.jellycine.player.core.PlayerState
import com.jellycine.player.core.PlayerTrack
import com.jellycine.player.core.PlayerUtils
import com.jellycine.player.preferences.PlayerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.jellycine.app.download.DownloadRepository
import com.jellycine.app.download.DownloadRepositoryProvider
import java.io.File
import javax.inject.Inject

/**
 * Player ViewModel
 */
@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {
    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    private val _preferredStreamIndexes = MutableStateFlow(PreferredStreamIndexes())
    val preferredStreamIndexes: StateFlow<PreferredStreamIndexes> = _preferredStreamIndexes.asStateFlow()
    private val _playbackCompletedEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val playbackCompletedEvents: SharedFlow<String> = _playbackCompletedEvents.asSharedFlow()

    var exoPlayer: ExoPlayer? by mutableStateOf(null)
        private set
    var mpvPlayer: MpvPlayerController? by mutableStateOf(null)
        private set
    private var activePlayerEngine: String = PlayerPreferences.DEFAULT_PLAYER_ENGINE

    private val trackSelectionCoordinator = PlayerTrackSelection()
    private var playbackSession = PlaybackSessionContext()
    private val playbackReporter = PlayerPlaybackReporter(
        mediaRepository = mediaRepository,
        scope = viewModelScope,
        positionProvider = { getCurrentPosition() },
        isPausedProvider = { !isPlayingNow() }
    )
    private var spatializerHelper: SpatializerHelper? = null
    private var playerContext: Context? = null
    private var apiMediaStreams: List<MediaStream>? = null
    private var defaultAudioStreamIndex: Int? = null
    private var defaultSubtitleStreamIndex: Int? = null
    private var hasHandledPlaybackCompletion = false
    private var videoTranscodingAllowed: Boolean? = null
    private var audioTranscodingAllowed: Boolean? = null
    private var audioDiagnosticsSignature: String? = null
    private var downloadRepository: DownloadRepository? = null
    private var communityPlaybackSegmentsJob: Job? = null
    private var spatialAudioAnalysisJob: Job? = null
    private var currentItemDetails: BaseItemDto? = null
    private var nextEpisodePrefetchJob: Job? = null
    private var nextEpisodePrefetchSignature: String? = null
    private var hasRenderedFirstFrame = false
    private var mpvExternalSubtitleUrls: Map<Int, String> = emptyMap()

    private fun isMpvPlayback(): Boolean {
        return activePlayerEngine == PlayerPreferences.PLAYER_ENGINE_MPV
    }

    fun initializePlayer(
        context: Context,
        mediaId: String,
        initialItemDetails: BaseItemDto? = null,
        preferredAudioStreamIndex: Int? = null,
        preferredSubtitleStreamIndex: Int? = null,
        initialSeekPositionMs: Long? = null,
        startPlayback: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                _playerState.value = _playerState.value.copy(
                    isLoading = true,
                    isPlaying = false,
                    playWhenReady = startPlayback,
                    hasStartedPlayback = false,
                    error = null
                )
                _playerState.value = _playerState.value.copy(
                    recapStartMs = null,
                    recapEndMs = null,
                    introStartMs = null,
                    introEndMs = null,
                    creditsStartMs = null,
                    creditsEndMs = null,
                    previewStartMs = null,
                    previewEndMs = null,
                    chapterMarkers = emptyList()
                )

                playerContext = context
                hasHandledPlaybackCompletion = false
                val playerPreferences = PlayerPreferences(context)
                activePlayerEngine = playerPreferences.getPlayerEngine()
                val resolvedPreferredAudioStreamIndex = preferredAudioStreamIndex
                    ?: playerPreferences.getPreferredAudioStreamIndex(mediaId)
                val activePreferredSubtitleStreamIndex = preferredSubtitleStreamIndex
                    ?: playerPreferences.getPreferredSubtitleStreamIndex(mediaId)
                val isVideoTranscodingAllowed = isVideoTranscodingAllowedForUser()
                val isAudioTranscodingAllowed = isAudioTranscodingAllowedForUser()
                val audioTranscodeMode = if (isAudioTranscodingAllowed) {
                    playerPreferences.getAudioTranscodeMode()
                } else {
                    AudioTranscodeMode.AUTO
                }
                val maxStreamingBitrate = if (isVideoTranscodingAllowed) {
                    playerPreferences.getMaxStreamingBitrate()
                } else {
                    null
                }
                val maxStreamingHeight = if (isVideoTranscodingAllowed) {
                    playerPreferences.getStreamingQualityMaxHeight()
                } else {
                    null
                }
                trackSelectionCoordinator.resetPendingSelections(
                    preferredAudioStreamIndex = resolvedPreferredAudioStreamIndex,
                    preferredSubtitleStreamIndex = activePreferredSubtitleStreamIndex
                )
                _preferredStreamIndexes.value = PreferredStreamIndexes(
                    audioStreamIndex = resolvedPreferredAudioStreamIndex,
                    subtitleStreamIndex = activePreferredSubtitleStreamIndex
                )

                audioDiagnosticsSignature = null
                currentItemDetails = null
                cancelNextEpisodePrefetch()
                playbackReporter.reset()
                communityPlaybackSegmentsJob?.cancel()
                communityPlaybackSegmentsJob = null
                spatialAudioAnalysisJob?.cancel()
                spatialAudioAnalysisJob = null
                hasRenderedFirstFrame = false
                spatializerHelper = SpatializerHelper(context)
                downloadRepository = DownloadRepositoryProvider.getInstance(context)
                val offlinePath = downloadRepository?.getOfflineFilePath(mediaId)
                val hasOfflineFile = !offlinePath.isNullOrBlank() && File(offlinePath).exists()
                val offlineItemDetails = if (hasOfflineFile) {
                    downloadRepository?.offlineItemMetadata(mediaId)
                } else {
                    null
                }

                // Get item details to check for resume position
                val itemDetails = if (initialItemDetails?.id == mediaId) {
                    initialItemDetails
                } else if (hasOfflineFile) {
                    offlineItemDetails ?: mediaRepository.getItemById(mediaId).getOrNull()
                } else {
                    mediaRepository.getItemById(mediaId).getOrNull()
                }
                currentItemDetails = itemDetails
                val resumePositionTicks = itemDetails?.userData?.playbackPositionTicks
                val storedResumePositionMs = if (resumePositionTicks != null && resumePositionTicks > 0) {
                    resumePositionTicks / 10000L
                } else {
                    null
                }
                val mediaTitle = itemDetails?.name ?: "Unknown Title"
                val logoSourceId = when {
                    itemDetails?.imageTags?.containsKey("Logo") == true && !itemDetails.id.isNullOrBlank() -> itemDetails.id
                    !itemDetails?.parentLogoItemId.isNullOrBlank() && !itemDetails?.parentLogoImageTag.isNullOrBlank() -> itemDetails?.parentLogoItemId
                    else -> null
                }
                val mediaLogoUrl = logoSourceId?.let { sourceId ->
                    mediaRepository.getImageUrlString(
                        itemId = sourceId,
                        imageType = "Logo",
                        width = 320,
                        quality = 90,
                        enableImageEnhancers = false
                    )
                }
                val seasonEpisodeLabel = itemDetails?.let { item ->
                    val isEpisodeItem = item.type.equals("Episode", ignoreCase = true)
                    val season = item.parentIndexNumber
                    val episode = item.indexNumber
                    if (isEpisodeItem && season != null && episode != null) {
                        val episodeName = item.episodeTitle
                            ?.takeIf { it.isNotBlank() }
                            ?: item.name?.takeIf { it.isNotBlank() }
                        buildString {
                            append("S")
                            append(season)
                            append(":E")
                            append(episode)
                            episodeName?.let {
                                append(" - ")
                                append(it)
                            }
                        }
                    } else {
                        null
                    }
                }
                val chapterMarkers = PlaybackMarkerUtils.buildChapterMarkers(itemDetails?.chapters)
                val playerStartPositionMs = initialSeekPositionMs ?: storedResumePositionMs
                val introSegment = PlaybackMarkerUtils.extractIntroWindow(itemDetails?.chapters)

                var primaryMediaSource: MediaSource? = null
                var sessionPlaySessionId: String? = null
                var sessionMediaSourceId: String? = null
                var sessionMediaSourceContainer: String? = null
                var sessionMediaSourceBitrateKbps: Int? = null
                var sessionPlayMethod = PlayMethod.DIRECT_PLAY
                var sessionIsOfflinePlayback = false
                var streamingMediaSource: androidx.media3.exoplayer.source.MediaSource? = null
                var playbackRequest: PlaybackRequest? = null
                defaultAudioStreamIndex = null
                defaultSubtitleStreamIndex = null

                val mediaItem = if (hasOfflineFile) {
                    val localFilePath = requireNotNull(offlinePath)
                    sessionIsOfflinePlayback = true
                    sessionPlayMethod = PlayMethod.OFFLINE
                    MediaItem.fromUri(Uri.fromFile(File(localFilePath)))
                } else {
                    sessionIsOfflinePlayback = false

                    // Get playback info first to obtain session details
                    val playbackInfoResult = mediaRepository.getPlaybackInfo(
                        itemId = mediaId,
                        maxStreamingBitrate = maxStreamingBitrate,
                        audioStreamIndex = resolvedPreferredAudioStreamIndex,
                        subtitleStreamIndex = activePreferredSubtitleStreamIndex,
                        audioTranscodeMode = audioTranscodeMode
                    )
                    if (playbackInfoResult.isFailure) {
                        val error = playbackInfoResult.exceptionOrNull()?.message ?: "Failed to get playback info"
                        _playerState.value = _playerState.value.copy(isLoading = false, error = error)
                        return@launch
                    }

                    val playbackInfo = playbackInfoResult.getOrNull()
                    if (playbackInfo == null) {
                        _playerState.value = _playerState.value.copy(isLoading = false, error = "Playback info is null")
                        return@launch
                    }

                    primaryMediaSource = playbackInfo.mediaSources?.firstOrNull()
                    defaultAudioStreamIndex = primaryMediaSource?.defaultAudioStreamIndex
                    defaultSubtitleStreamIndex = primaryMediaSource?.defaultSubtitleStreamIndex
                    sessionPlaySessionId = playbackInfo.playSessionId
                    sessionMediaSourceId = primaryMediaSource?.id
                    sessionMediaSourceContainer = primaryMediaSource?.container
                    sessionMediaSourceBitrateKbps = primaryMediaSource?.bitrate?.div(1000)
                    sessionPlayMethod = when {
                        primaryMediaSource?.supportsDirectPlay == true -> PlayMethod.DIRECT_PLAY
                        primaryMediaSource?.supportsDirectStream == true -> PlayMethod.DIRECT_STREAM
                        else -> PlayMethod.TRANSCODE
                    }
                    val playbackRequestResult = mediaRepository.getPlaybackRequest(
                        itemId = mediaId,
                        maxStreamingBitrate = maxStreamingBitrate,
                        maxStreamingHeight = maxStreamingHeight,
                        audioStreamIndex = resolvedPreferredAudioStreamIndex,
                        subtitleStreamIndex = activePreferredSubtitleStreamIndex,
                        audioTranscodeMode = audioTranscodeMode,
                        playbackInfo = playbackInfo,
                        includeAccessToken = isMpvPlayback()
                    )
                    if (playbackRequestResult.isFailure) {
                        val error = playbackRequestResult.exceptionOrNull()?.message ?: "Failed to get playback request"
                        _playerState.value = _playerState.value.copy(isLoading = false, error = error)
                        return@launch
                    }

                    playbackRequest = playbackRequestResult.getOrNull()
                    val streamingUrl = playbackRequest?.url
                    if (streamingUrl.isNullOrEmpty()) {
                        _playerState.value = _playerState.value.copy(isLoading = false, error = "Failed to get playback URL")
                        return@launch
                    }
                    val streamUri = Uri.parse(streamingUrl)
                    val streamPlaySessionId = streamUri.getQueryParameter("PlaySessionId")
                        ?: streamUri.getQueryParameter("playSessionId")
                    if (!streamPlaySessionId.isNullOrBlank()) {
                        sessionPlaySessionId = streamPlaySessionId
                    }
                    sessionPlayMethod = getPlayMethod(
                        streamingUrl = streamingUrl,
                        fallback = sessionPlayMethod
                    )

                    val activeSubtitleStreamIndex = (
                        activePreferredSubtitleStreamIndex
                            ?: primaryMediaSource?.defaultSubtitleStreamIndex
                        )?.takeIf { it >= 0 }
                    val activeSubtitleStream = primaryMediaSource
                        ?.mediaStreams
                        ?.firstOrNull { stream ->
                            stream.type == "Subtitle" &&
                                stream.index == activeSubtitleStreamIndex
                        }

                    val streamingMediaItem = streamingMediaItem(
                        streamingUrl = streamingUrl,
                        selectedSubtitleStream = activeSubtitleStream
                    )
                    if (!isMpvPlayback()) {
                        streamingMediaSource = PlayerUtils.createStreamingMediaSource(
                            context = context,
                            mediaItem = streamingMediaItem,
                            requestHeaders = playbackRequest?.requestHeaders.orEmpty()
                        )
                    }
                    streamingMediaItem
                }

                playbackSession = PlaybackSessionContext(
                    mediaId = mediaId,
                    playSessionId = sessionPlaySessionId,
                    mediaSourceId = sessionMediaSourceId,
                    mediaSourceContainer = sessionMediaSourceContainer,
                    mediaSourceBitrateKbps = sessionMediaSourceBitrateKbps,
                    playMethod = sessionPlayMethod,
                    isOfflinePlayback = sessionIsOfflinePlayback
                )
                playbackReporter.updateSession(playbackSession)
                
                // Get media info for spatial audio analysis
                apiMediaStreams = PlayerTrack.resolveApiMediaStreams(
                    itemDetails = itemDetails,
                    playbackMediaSource = primaryMediaSource
                )
                mpvExternalSubtitleUrls = MPVPlayer.externalSubtitleUrls(
                    playbackRequest = playbackRequest,
                    mediaStreams = apiMediaStreams.orEmpty()
                )

                if (isMpvPlayback()) {
                    val selectedAudioStreamIndex = _preferredStreamIndexes.value.audioStreamIndex
                        ?: defaultAudioStreamIndex
                    val selectedSubtitleStreamIndex = _preferredStreamIndexes.value.subtitleStreamIndex
                        ?: defaultSubtitleStreamIndex
                    mpvPlayer = createMpvPlayer(context).also { player ->
                        player.load(
                            url = mediaItem.localConfiguration?.uri?.toString().orEmpty(),
                            subtitleUrls = mpvExternalSubtitleUrls.values.toList(),
                            audioTrackId = MPVPlayer.audioTrackId(
                                apiMediaStreams,
                                selectedAudioStreamIndex
                            ),
                            subtitleTrackId = MPVPlayer.subtitleTrackId(
                                apiMediaStreams,
                                selectedSubtitleStreamIndex
                            ),
                            selectedSubtitleUrl = selectedSubtitleStreamIndex?.let(
                                mpvExternalSubtitleUrls::get
                            ),
                            startPositionMs = playerStartPositionMs,
                            startPlayback = startPlayback
                        )
                    }
                } else {
                    exoPlayer = PlayerUtils.createPlayer(
                        context = context
                    )
                    exoPlayer?.apply {
                        addListener(playerListener)
                        if (streamingMediaSource != null) {
                            setMediaSource(streamingMediaSource!!)
                        } else {
                            setMediaItem(mediaItem)
                        }
                        prepare()

                        if (playerStartPositionMs != null && playerStartPositionMs > 0) {
                            seekTo(playerStartPositionMs)
                        }

                        playWhenReady = startPlayback
                    }
                }

                // Spatial audio analysis and device capabilities
                val usesMpv = isMpvPlayback()
                if (!usesMpv) {
                    updateTrackInformation()
                }
                val isHdrPlayback = if (usesMpv) {
                    MPVPlayer.isHdr(apiMediaStreams)
                } else {
                    PlayerMetadata.isCurrentPlaybackHdr(exoPlayer)
                }
                
                // Apply start maximized setting if enabled
                applyStartMaximizedSetting(context)

                _playerState.value = _playerState.value.copy(
                    isLoading = true,
                    isPlaying = false,
                    playWhenReady = startPlayback,
                    hasStartedPlayback = false,
                    mediaTitle = mediaTitle,
                    mediaLogoUrl = mediaLogoUrl,
                    seasonEpisodeLabel = seasonEpisodeLabel,
                    chapterMarkers = chapterMarkers,
                    introStartMs = introSegment?.startMs,
                    introEndMs = introSegment?.endMs,
                    isVideoTranscodingAllowed = isVideoTranscodingAllowed,
                    isAudioTranscodingAllowed = isAudioTranscodingAllowed,
                    currentAudioTranscodeMode = audioTranscodeMode,
                    spatializationResult = null,
                    isSpatialAudioEnabled = false,
                    spatialAudioFormat = "",
                    isHdrEnabled = isHdrPlayback
                )
                if (usesMpv) {
                    updateApiTrackInformation()
                }
                if (itemDetails != null) {
                    applyCommunityPlaybackSegments(mediaId = mediaId, itemDetails = itemDetails)
                }
                analyzeSpatialAudioAsync(
                    context = context,
                    mediaId = mediaId,
                    mediaStreams = apiMediaStreams,
                    helper = spatializerHelper
                )

            } catch (e: Exception) {
                Log.e(TAG, "Player initialization failed", e)
                _playerState.value = _playerState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    private fun analyzeSpatialAudioAsync(
        context: Context,
        mediaId: String,
        mediaStreams: List<MediaStream>?,
        helper: SpatializerHelper?
    ) {
        spatialAudioAnalysisJob?.cancel()
        val primaryAudioStream = mediaStreams
            ?.firstOrNull { it.type == "Audio" }
            ?: return

        spatialAudioAnalysisJob = viewModelScope.launch {
            val spatializationResult = withContext(Dispatchers.Default) {
                CodecCapabilityManager.canSpatializeAudioStream(
                    context = context,
                    audioStream = primaryAudioStream,
                    spatializerHelper = helper
                )
            }
            if (playbackSession.mediaId != mediaId) return@launch

            _playerState.value = _playerState.value.copy(
                spatializationResult = spatializationResult,
                isSpatialAudioEnabled = spatializationResult.canSpatialize,
                spatialAudioFormat = spatializationResult.spatialFormat
            )
        }
    }

    private fun applyCommunityPlaybackSegments(mediaId: String, itemDetails: BaseItemDto) {
        if (!itemDetails.type.equals("Episode", ignoreCase = true)) return

        communityPlaybackSegmentsJob?.cancel()
        communityPlaybackSegmentsJob = viewModelScope.launch {
            val playbackSegments = mediaRepository.getCommunityPlaybackSegments(itemDetails).getOrNull()
                ?: return@launch
            if (playbackSession.mediaId != mediaId) return@launch

            val currentState = _playerState.value
            _playerState.value = currentState.copy(
                recapStartMs = currentState.recapStartMs ?: playbackSegments.recap?.startMs,
                recapEndMs = currentState.recapEndMs ?: playbackSegments.recap?.endMs,
                introStartMs = currentState.introStartMs ?: playbackSegments.intro?.startMs,
                introEndMs = currentState.introEndMs ?: playbackSegments.intro?.endMs,
                creditsStartMs = currentState.creditsStartMs ?: playbackSegments.credits?.startMs,
                creditsEndMs = currentState.creditsEndMs ?: playbackSegments.credits?.endMs,
                previewStartMs = currentState.previewStartMs ?: playbackSegments.preview?.startMs,
                previewEndMs = currentState.previewEndMs ?: playbackSegments.preview?.endMs
            )
        }
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
        mpvPlayer?.seekTo(position)
        _playerState.value = _playerState.value.copy(currentPosition = position)
    }

    fun play() {
        exoPlayer?.play()
        mpvPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
        mpvPlayer?.pause()
        persistPosition()
    }

    fun seekToProgress(progress: Float) {
        val duration = getDuration()
        if (duration > 0L) {
            seekTo((duration * progress).toLong())
        }
    }

    fun seekBy(deltaMs: Long) {
        val currentPosition = getCurrentPosition()
        val duration = getDuration()
        val targetPosition = if (duration > 0L) {
            (currentPosition + deltaMs).coerceIn(0L, duration)
        } else {
            (currentPosition + deltaMs).coerceAtLeast(0L)
        }
        seekTo(targetPosition)
    }

    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: mpvPlayer?.currentPosition ?: 0L

    fun isPlayingNow(): Boolean = exoPlayer?.isPlaying == true || mpvPlayer?.isPlaying == true

    fun getDuration(): Long = exoPlayer?.duration?.coerceAtLeast(0L) ?: mpvPlayer?.duration ?: 0L

    fun updateNextEpisodeCache(
        context: Context,
        nextEpisodeId: String?,
        preferredAudioStreamIndex: Int?,
        preferredSubtitleStreamIndex: Int?
    ) {
        val playerPreferences = PlayerPreferences(context)
        val targetEpisodeId = nextEpisodeId?.takeIf { it.isNotBlank() }
        if (
            targetEpisodeId == null ||
            targetEpisodeId == playbackSession.mediaId ||
            !playerPreferences.isCacheNextEpisodeEnabled()
        ) {
            cancelNextEpisodePrefetch()
            return
        }
        val prefetchSignature = buildString {
            append(targetEpisodeId)
            append('|')
            append(preferredAudioStreamIndex ?: "auto")
            append('|')
            append(preferredSubtitleStreamIndex ?: "auto")
            append('|')
            append(playerPreferences.getStreamingQuality())
            append('|')
            append(playerPreferences.getAudioTranscodeMode().name)
        }

        if (
            nextEpisodePrefetchSignature == prefetchSignature &&
            nextEpisodePrefetchJob?.isActive == true
        ) {
            return
        }

        cancelNextEpisodePrefetch()
        nextEpisodePrefetchSignature = prefetchSignature
        nextEpisodePrefetchJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                prefetchNextEpisode(
                    context = context.applicationContext,
                    nextEpisodeId = targetEpisodeId,
                    preferredAudioStreamIndex = preferredAudioStreamIndex,
                    preferredSubtitleStreamIndex = preferredSubtitleStreamIndex,
                    playerPreferences = playerPreferences
                )
            }.onFailure { error ->
                Log.d(TAG, "Skipping next-episode cache prefetch for $targetEpisodeId", error)
            }
        }
    }

    private suspend fun prefetchNextEpisode(
        context: Context,
        nextEpisodeId: String,
        preferredAudioStreamIndex: Int?,
        preferredSubtitleStreamIndex: Int?,
        playerPreferences: PlayerPreferences
    ) {
        val nextDownloadRepository = downloadRepository ?: DownloadRepositoryProvider.getInstance(context)
        val offlinePath = nextDownloadRepository.getOfflineFilePath(nextEpisodeId)
        if (!offlinePath.isNullOrBlank() && File(offlinePath).exists()) {
            return
        }

        val isVideoTranscodingAllowed = isVideoTranscodingAllowedForUser()
        val isAudioTranscodingAllowed = isAudioTranscodingAllowedForUser()
        val audioTranscodeMode = if (isAudioTranscodingAllowed) {
            playerPreferences.getAudioTranscodeMode()
        } else {
            AudioTranscodeMode.AUTO
        }
        val maxStreamingBitrate = if (isVideoTranscodingAllowed) {
            playerPreferences.getMaxStreamingBitrate()
        } else {
            null
        }
        val maxStreamingHeight = if (isVideoTranscodingAllowed) {
            playerPreferences.getStreamingQualityMaxHeight()
        } else {
            null
        }

        val playbackInfo = mediaRepository.getPlaybackInfo(
            itemId = nextEpisodeId,
            maxStreamingBitrate = maxStreamingBitrate,
            audioStreamIndex = preferredAudioStreamIndex,
            subtitleStreamIndex = preferredSubtitleStreamIndex,
            audioTranscodeMode = audioTranscodeMode
        ).getOrNull() ?: return

        val playbackRequest = mediaRepository.getPlaybackRequest(
            itemId = nextEpisodeId,
            maxStreamingBitrate = maxStreamingBitrate,
            maxStreamingHeight = maxStreamingHeight,
            audioStreamIndex = preferredAudioStreamIndex,
            subtitleStreamIndex = preferredSubtitleStreamIndex,
            audioTranscodeMode = audioTranscodeMode,
            playbackInfo = playbackInfo
        ).getOrNull() ?: return

        val streamingUrl = playbackRequest.url?.takeIf { it.isNotBlank() } ?: return
        val nextMediaItem = streamingMediaItem(streamingUrl = streamingUrl)
        val localConfiguration = nextMediaItem.localConfiguration ?: return
        val prefetchBytes = nextEpisodePrefetchBytes(
            playerPreferences = playerPreferences,
            sourceBitrate = playbackInfo.mediaSources?.firstOrNull()?.bitrate,
            maxStreamingBitrate = maxStreamingBitrate
        )

        PlayerUtils.prefetchStreamingMedia(
            context = context,
            streamUri = localConfiguration.uri,
            cacheKey = localConfiguration.customCacheKey,
            maxBytes = prefetchBytes,
            requestHeaders = playbackRequest.requestHeaders
        )
    }

    private fun nextEpisodePrefetchBytes(
        playerPreferences: PlayerPreferences,
        sourceBitrate: Int?,
        maxStreamingBitrate: Int?
    ): Long {
        val bitrateBitsPerSecond = when {
            maxStreamingBitrate != null && maxStreamingBitrate > 0 -> maxStreamingBitrate.toLong()
            sourceBitrate != null && sourceBitrate > 0 -> sourceBitrate.toLong()
            else -> 8_000_000L
        }.coerceAtLeast(2_000_000L)
        val prefetchWindowSeconds = minOf(playerPreferences.getPlayerCacheTimeSeconds(), 45)
        val desiredBytes = bitrateBitsPerSecond
            .times(prefetchWindowSeconds.toLong())
            .div(8L)
        val cacheBudgetBytes = playerPreferences.getPlayerCacheSizeMb()
            .toLong()
            .times(1024L * 1024L)
            .div(3L)

        return minOf(desiredBytes, cacheBudgetBytes).coerceAtLeast(8L * 1024L * 1024L)
    }

    private fun cancelNextEpisodePrefetch() {
        nextEpisodePrefetchJob?.cancel()
        nextEpisodePrefetchJob = null
        nextEpisodePrefetchSignature = null
    }

    private fun getPlayMethod(
        streamingUrl: String,
        fallback: PlayMethod
    ): PlayMethod {
        val streamUri = Uri.parse(streamingUrl)
        val path = streamUri.encodedPath.orEmpty().lowercase()
        val isTranscodingUrl =
            path.contains("master.m3u8") ||
                path.contains("transcode") ||
                path.contains("transcoding")

        if (isTranscodingUrl) {
            return PlayMethod.TRANSCODE
        }

        return when (streamUri.getQueryParameter("static")?.lowercase()) {
            "true" -> PlayMethod.DIRECT_PLAY
            "false" -> PlayMethod.DIRECT_STREAM
            else -> fallback
        }
    }

    private suspend fun isVideoTranscodingAllowedForUser(): Boolean {
        videoTranscodingAllowed?.let { return it }
        mediaRepository.loadPersistedHomeSnapshot()?.isVideoTranscodingAllowed?.let {
            videoTranscodingAllowed = it
            return it
        }

        val user = mediaRepository.getCurrentUser().getOrNull()
        val allowed = user?.policy?.enableVideoPlaybackTranscoding
            ?: user?.let { true }
            ?: false

        videoTranscodingAllowed = allowed
        mediaRepository.persistHomeSnapshot(isVideoTranscodingAllowed = allowed)
        return allowed
    }

    private suspend fun isAudioTranscodingAllowedForUser(): Boolean {
        audioTranscodingAllowed?.let { return it }
        mediaRepository.loadPersistedHomeSnapshot()?.isAudioTranscodingAllowed?.let {
            audioTranscodingAllowed = it
            return it
        }

        val user = mediaRepository.getCurrentUser().getOrNull()
        val allowed = user?.policy?.enableAudioPlaybackTranscoding
            ?: user?.let { true }
            ?: false

        audioTranscodingAllowed = allowed
        mediaRepository.persistHomeSnapshot(isAudioTranscodingAllowed = allowed)
        return allowed
    }

    fun togglePlayPause() {
        if (exoPlayer != null || mpvPlayer != null) {
            if (isPlayingNow()) pause() else play()
            playbackReporter.onPlaybackPauseStateChanged()
        }
    }

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume
        mpvPlayer?.setVolume(volume)
        _playerState.value = _playerState.value.copy(volume = volume)
    }

    fun setBrightness(brightness: Float) {
        _playerState.value = _playerState.value.copy(brightness = brightness)
    }

    fun toggleControls() {
        _playerState.value = _playerState.value.copy(showControls = !_playerState.value.showControls)
    }

    fun releasePlayer() {
        persistPosition()
        playbackReporter.reportPlaybackStopped()
        cancelNextEpisodePrefetch()
        communityPlaybackSegmentsJob?.cancel()
        communityPlaybackSegmentsJob = null
        spatialAudioAnalysisJob?.cancel()
        spatialAudioAnalysisJob = null
        exoPlayer?.apply {
            removeListener(playerListener)
            release()
        }
        exoPlayer = null
        mpvPlayer?.release()
        mpvPlayer = null
        spatializerHelper?.cleanup()
        spatializerHelper = null
        playbackSession = PlaybackSessionContext()
        playbackReporter.reset()
        trackSelectionCoordinator.clear()
        apiMediaStreams = null
        defaultAudioStreamIndex = null
        defaultSubtitleStreamIndex = null
        mpvExternalSubtitleUrls = emptyMap()
        playerContext = null
        downloadRepository = null
        hasHandledPlaybackCompletion = false
        hasRenderedFirstFrame = false
        audioDiagnosticsSignature = null
        _preferredStreamIndexes.value = PreferredStreamIndexes()
        _playerState.value = PlayerState()
    }

    private fun handlePlaybackCompleted() {
        if (hasHandledPlaybackCompletion) return
        hasHandledPlaybackCompletion = true
        persistPosition(markCompleted = true)
        playbackReporter.reportPlaybackStopped()
        playbackSession.mediaId?.let { completedMediaId ->
            _playbackCompletedEvents.tryEmit(completedMediaId)
        }
    }

    private fun persistPosition(markCompleted: Boolean = false) {
        val session = playbackSession
        if (!session.isOfflinePlayback) return
        val mediaId = session.mediaId ?: return
        downloadRepository?.updatePlaybackPosition(
            itemId = mediaId,
            positionMs = getCurrentPosition(),
            markCompleted = markCompleted
        )
    }

    fun clearError() {
        _playerState.value = _playerState.value.copy(error = null)
    }

    /**
     * Toggle lock state - when locked, disable all gestures and hide controls
     */
    fun toggleLock() {
        val currentState = _playerState.value
        _playerState.value = currentState.copy(
            isLocked = !currentState.isLocked,
            showControls = if (!currentState.isLocked) false else currentState.showControls
        )
    }

    /**
     * Update track information from ExoPlayer
     */
    private fun updateTrackInformation() {
        exoPlayer?.let { player ->
            try {
                val isHdrPlayback = PlayerMetadata.isCurrentPlaybackHdr(exoPlayer)
                val selectedAudioSignature = buildSelectedAudioSignature(player)
                if (selectedAudioSignature != null && selectedAudioSignature != audioDiagnosticsSignature) {
                    PlayerUtils.logAudioPlaybackDiagnostics(player, reason = "track_changed")
                    audioDiagnosticsSignature = selectedAudioSignature
                }
                val resolvedTracks = PlayerTrack.currentTrackState(
                    exoPlayer = player,
                    mediaStreams = apiMediaStreams,
                    isTranscoding = playbackSession.playMethod == PlayMethod.TRANSCODE,
                    selectedAudioStreamIndex = _preferredStreamIndexes.value.audioStreamIndex,
                    selectedSubtitleStreamIndex = _preferredStreamIndexes.value.subtitleStreamIndex,
                    defaultAudioStreamIndex = defaultAudioStreamIndex,
                    defaultSubtitleStreamIndex = defaultSubtitleStreamIndex
                )
                val syncedPreferredIndexes = trackSelectionCoordinator.syncPreferredIndexesFromCurrentTracks(
                    context = playerContext,
                    mediaId = playbackSession.mediaId,
                    currentAudioTrack = resolvedTracks.currentAudioTrack
                        ?.takeUnless { it.requiresPlaybackRestart },
                    currentSubtitleTrack = resolvedTracks.currentSubtitleTrack
                        ?.takeUnless { it.requiresPlaybackRestart },
                    currentPublished = _preferredStreamIndexes.value
                )
                if (syncedPreferredIndexes != _preferredStreamIndexes.value) {
                    _preferredStreamIndexes.value = syncedPreferredIndexes
                }

                _playerState.value = _playerState.value.copy(
                    availableAudioTracks = resolvedTracks.availableAudioTracks,
                    currentAudioTrack = resolvedTracks.currentAudioTrack,
                    availableSubtitleTracks = resolvedTracks.availableSubtitleTracks,
                    currentSubtitleTrack = resolvedTracks.currentSubtitleTrack,
                    availableVideoTracks = resolvedTracks.availableVideoTracks,
                    isHdrEnabled = isHdrPlayback
                )
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Failed to update track information", e)
            }
        }
    }

    @UnstableApi
    private fun buildSelectedAudioSignature(player: ExoPlayer): String? {
        player.currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
            val trackIndex = (0 until group.mediaTrackGroup.length)
                .firstOrNull(group::isTrackSelected)
                ?: return@forEachIndexed
            val format = group.mediaTrackGroup.getFormat(trackIndex)
            return "$groupIndex:$trackIndex|${format.channelCount}|${format.bitrate}|${format.sampleRate}|${format.codecs ?: format.sampleMimeType.orEmpty()}"
        }
        return null
    }

    private fun applyPendingTrackSelectionsIfNeeded() {
        val player = exoPlayer ?: return
        val appliedAnySelection = trackSelectionCoordinator.applyInitialSelections(
            player = player,
            mediaStreams = apiMediaStreams,
            isTranscoding = playbackSession.playMethod == PlayMethod.TRANSCODE
        )
        if (appliedAnySelection) {
            viewModelScope.launch {
                delay(250)
                updateTrackInformation()
            }
        }
    }

    private fun updateApiTrackInformation() {
        val trackState = MPVPlayer.trackState(
            mediaStreams = apiMediaStreams,
            selectedAudioStreamIndex = _preferredStreamIndexes.value.audioStreamIndex,
            selectedSubtitleStreamIndex = _preferredStreamIndexes.value.subtitleStreamIndex,
            defaultAudioStreamIndex = defaultAudioStreamIndex,
            defaultSubtitleStreamIndex = defaultSubtitleStreamIndex
        )

        _playerState.value = _playerState.value.copy(
            availableAudioTracks = trackState.availableAudioTracks,
            currentAudioTrack = trackState.currentAudioTrack,
            availableSubtitleTracks = trackState.availableSubtitleTracks,
            currentSubtitleTrack = trackState.currentSubtitleTrack,
            availableVideoTracks = trackState.availableVideoTracks,
            isHdrEnabled = MPVPlayer.isHdr(apiMediaStreams)
        )
    }

    private fun createMpvPlayer(context: Context): MpvPlayerController {
        val preferences = PlayerPreferences(context)
        return MpvPlayerController(
            context = context,
            hardwareDecoding = preferences.getMpvHardwareDecoding(),
            videoOutput = preferences.getMpvVideoOutput(),
            audioOutput = preferences.getMpvAudioOutput(),
            listener = object : MpvPlayerController.Listener {
                override fun onBuffering() {
                    _playerState.value = _playerState.value.copy(isLoading = true)
                }

                override fun onReady() {
                    val wasPlaying = _playerState.value.isPlaying
                    _playerState.value = _playerState.value.copy(
                        isLoading = false,
                        isPlaying = isPlayingNow(),
                        playWhenReady = isPlayingNow(),
                        hasStartedPlayback = true,
                        duration = getDuration()
                    )
                    if (!playbackReporter.hasReportedStart() && isPlayingNow()) {
                        playbackReporter.reportPlaybackStatus()
                    }
                    if (wasPlaying != isPlayingNow()) {
                        playbackReporter.onPlaybackPauseStateChanged()
                    }
                }

                override fun onEnded() {
                    _playerState.value = _playerState.value.copy(
                        isPlaying = false,
                        playWhenReady = false,
                        isLoading = false
                    )
                    handlePlaybackCompleted()
                }

            }
        )
    }

    /**
     * Select audio track by ID
     */
    fun selectAudioTrack(trackId: String) {
        if (trackId == _playerState.value.currentAudioTrack?.id) return
        val selectedTrack = _playerState.value.availableAudioTracks.firstOrNull { it.id == trackId } ?: return
        if (isMpvPlayback()) {
            val streamIndex = MPVPlayer.selectAudioTrack(mpvPlayer, selectedTrack) ?: return
            val (preferences, mediaId) = currentMediaPreferences() ?: return
            preferences.setPreferredAudioStreamIndex(mediaId, streamIndex)
            _preferredStreamIndexes.value = _preferredStreamIndexes.value.copy(audioStreamIndex = streamIndex)
            _playerState.value = _playerState.value.copy(currentAudioTrack = selectedTrack)
            return
        }
        if (selectedTrack.requiresPlaybackRestart) {
            playbackTrackSelection(
                audioStreamIndex = selectedTrack.streamIndex,
                subtitleStreamIndex = _preferredStreamIndexes.value.subtitleStreamIndex
            )
            return
        }
        exoPlayer?.let { player ->
            val playerTrackId = selectedTrack.playerTrackId ?: return
            trackSelectionCoordinator.markManualTrackSelection()
            PlayerUtils.selectAudioTrack(player, playerTrackId)
            viewModelScope.launch {
                delay(500)
                updateTrackInformation()
            }
        }
    }

    /**
     * Select subtitle track by ID
     */
    fun selectSubtitleTrack(trackId: String) {
        if (trackId == _playerState.value.currentSubtitleTrack?.id) return
        val selectedTrack = _playerState.value.availableSubtitleTracks.firstOrNull { it.id == trackId } ?: return
        if (isMpvPlayback()) {
            val streamIndex = MPVPlayer.selectSubtitleTrack(
                controller = mpvPlayer,
                track = selectedTrack,
                externalSubtitleUrls = mpvExternalSubtitleUrls
            ) ?: return
            val (preferences, mediaId) = currentMediaPreferences() ?: return
            preferences.setPreferredSubtitleStreamIndex(
                mediaId,
                streamIndex.takeUnless { it < 0 }
            )
            _preferredStreamIndexes.value = _preferredStreamIndexes.value.copy(
                subtitleStreamIndex = streamIndex.takeUnless { it < 0 }
            )
            _playerState.value = _playerState.value.copy(currentSubtitleTrack = selectedTrack)
            return
        }
        if (selectedTrack.requiresPlaybackRestart) {
            playbackTrackSelection(
                audioStreamIndex = _preferredStreamIndexes.value.audioStreamIndex,
                subtitleStreamIndex = selectedTrack.streamIndex
            )
            return
        }
        exoPlayer?.let { player ->
            val playerTrackId = selectedTrack.playerTrackId ?: return
            trackSelectionCoordinator.markManualTrackSelection()
            PlayerUtils.selectSubtitleTrack(player, playerTrackId)
            viewModelScope.launch {
                delay(500)
                updateTrackInformation()
            }
        }
    }

    private fun currentMediaPreferences(): Pair<PlayerPreferences, String>? {
        val context = playerContext ?: return null
        val mediaId = playbackSession.mediaId ?: return null
        return PlayerPreferences(context) to mediaId
    }

    private fun playbackTrackSelection(
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?
    ) {
        val context = playerContext ?: return
        val mediaId = playbackSession.mediaId ?: return
        val resumePositionMs = getCurrentPosition()
        val shouldResumePlaying = isPlayingNow()

        PlayerPreferences(context).apply {
            setPreferredAudioStreamIndex(mediaId, audioStreamIndex)
            setPreferredSubtitleStreamIndex(mediaId, subtitleStreamIndex)
        }

        releasePlayer()
        initializePlayer(
            context = context,
            mediaId = mediaId,
            initialItemDetails = currentItemDetails,
            preferredAudioStreamIndex = audioStreamIndex,
            preferredSubtitleStreamIndex = subtitleStreamIndex,
            initialSeekPositionMs = resumePositionMs,
            startPlayback = shouldResumePlaying
        )
    }

    private var currentAspectRatio by mutableIntStateOf(0)
    private val aspectRatioModes = listOf("Fit", "Zoom")

    private var currentResizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT

    /**
     * Toggle between fit and zoom modes
     * Uses ExoPlayer's native AspectRatioFrameLayout resize modes for proper aspect ratio handling
     */
    fun cycleAspectRatio() {
        setAspectRatioMode((currentAspectRatio + 1) % aspectRatioModes.size)
    }
    
    /**
     * Get current resize mode for VideoSurface
     */
    fun getCurrentResizeMode(): Int = currentResizeMode
    
    /**
     * Handle pinch-to-zoom gesture to set appropriate resize mode
     */
    fun handlePinchZoom(isZooming: Boolean) {
        if (isZooming && currentAspectRatio == 0) {
            setAspectRatioMode(1)
        } else if (!isZooming && currentAspectRatio == 1) {
            setAspectRatioMode(0)
        }
    }

    /**
     * Apply start maximized setting based on user preference
     * Uses ExoPlayer's native resize modes for proper aspect ratio handling
     */
    private fun applyStartMaximizedSetting(context: Context) {
        val playerPreferences = PlayerPreferences(context)
        val startMaximized = playerPreferences.isStartMaximizedEnabled()
        
        setAspectRatioMode(if (startMaximized) 1 else 0)
    }

    private fun setAspectRatioMode(modeIndex: Int) {
        currentAspectRatio = modeIndex.coerceIn(0, aspectRatioModes.lastIndex)
        currentResizeMode = when (currentAspectRatio) {
            1 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        _playerState.value = _playerState.value.copy(
            aspectRatioMode = aspectRatioModes[currentAspectRatio],
            videoScale = 1f,
            videoOffsetX = 0f,
            videoOffsetY = 0f
        )
    }

    /**
     * Seek backward by the configured interval
     */
    fun seekBackward() {
        val seconds = PlayerPreferences(playerContext ?: return)
            .getSeekBackwardIntervalSeconds()
        seekBy(deltaMs = -(seconds * 1000L))
    }

    /**
     * Seek forward by the configured interval
     */
    fun seekForward() {
        val seconds = PlayerPreferences(playerContext ?: return)
            .getSeekForwardIntervalSeconds()
        seekBy(deltaMs = seconds * 1000L)
    }

    private val playerListener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            applyPendingTrackSelectionsIfNeeded()
            updateTrackInformation()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val currentState = _playerState.value
            val wasPlaying = currentState.isPlaying
            val playWhenReady = exoPlayer?.playWhenReady == true
            val isNowPlaying = playbackState == Player.STATE_READY && playWhenReady
            val hasReportedStart = playbackReporter.hasReportedStart()
            val shouldShowLoading = when (playbackState) {
                Player.STATE_IDLE -> !hasReportedStart
                Player.STATE_BUFFERING -> playWhenReady || !hasReportedStart
                Player.STATE_READY -> playWhenReady && !hasRenderedFirstFrame
                else -> false
            }
            
            _playerState.value = currentState.copy(
                isLoading = shouldShowLoading,
                isPlaying = isNowPlaying,
                playWhenReady = playWhenReady,
                hasStartedPlayback = currentState.hasStartedPlayback || hasRenderedFirstFrame
            )

            if (playbackState == Player.STATE_READY && isNowPlaying && !hasReportedStart) {
                playbackReporter.reportPlaybackStatus()
            }

            if (wasPlaying != isNowPlaying) {
                playbackReporter.onPlaybackPauseStateChanged()
            }

            if (playbackState == Player.STATE_READY) {
                hasHandledPlaybackCompletion = false
                applyPendingTrackSelectionsIfNeeded()
                updateTrackInformation()
            }

            if (playbackState == Player.STATE_ENDED) {
                handlePlaybackCompleted()
            }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            val currentState = _playerState.value
            val playbackState = exoPlayer?.playbackState ?: Player.STATE_IDLE
            val hasReportedStart = playbackReporter.hasReportedStart()
            val shouldShowLoading = when (playbackState) {
                Player.STATE_IDLE -> !hasReportedStart
                Player.STATE_BUFFERING -> playWhenReady || !hasReportedStart
                Player.STATE_READY -> playWhenReady && !hasRenderedFirstFrame
                else -> false
            }
            _playerState.value = currentState.copy(
                playWhenReady = playWhenReady,
                isPlaying = playWhenReady && playbackState == Player.STATE_READY,
                isLoading = shouldShowLoading
            )
        }

        override fun onRenderedFirstFrame() {
            hasRenderedFirstFrame = true
            _playerState.value = _playerState.value.copy(
                isLoading = false,
                hasStartedPlayback = true
            )
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            hasRenderedFirstFrame = false
            _playerState.value = _playerState.value.copy(
                error = error.message ?: "Playback error occurred",
                isLoading = false,
                playWhenReady = false,
                isPlaying = false
            )

            if (playbackReporter.hasReportedStart()) {
                playbackReporter.reportPlaybackStopped(failed = true)
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            _playerState.value = _playerState.value.copy(
                currentPosition = newPosition.positionMs,
                duration = getDuration()
            )

            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                playbackReporter.onPlaybackPositionDiscontinuity()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }

    fun getHdrFormatInfo(): String {
        return PlayerMetadata.buildHdrFormatInfo(
            context = playerContext,
            exoPlayer = exoPlayer
        )
    }

    /**
     * Get unified media metadata information for the modern bubble dialog
     */
    fun getMediaMetadataInfo(): MediaMetadataInfo {
        return PlayerMetadata.buildMediaMetadataInfo(
            context = playerContext,
            exoPlayer = exoPlayer,
            mediaStreams = apiMediaStreams,
            mediaSourceContainer = playbackSession.mediaSourceContainer,
            mediaSourceBitrateKbps = playbackSession.mediaSourceBitrateKbps,
            playMethodDisplayName = playbackSession.playMethod.displayName
        )
    }

    fun getSourceVideoHeight(): Int? {
        return PlayerMetadata.getSourceVideoHeight(apiMediaStreams)
    }
}
