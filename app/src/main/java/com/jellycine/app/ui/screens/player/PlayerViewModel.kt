package com.jellycine.app.ui.screens.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import com.jellycine.data.model.AudioTranscodeMode
import com.jellycine.data.model.ChapterInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import javax.inject.Inject
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.model.MediaStream
import com.jellycine.data.model.MediaSource
import com.jellycine.player.core.ChapterMarker
import com.jellycine.player.core.PlayerState
import com.jellycine.player.core.PlayerTrack
import com.jellycine.player.core.PlayerUtils
import com.jellycine.player.audio.SpatializerHelper
import com.jellycine.detail.CodecCapabilityManager
import com.jellycine.app.download.DownloadRepository
import com.jellycine.app.download.DownloadRepositoryProvider
import java.io.File

/**
 * Player ViewModel
 */
@UnstableApi
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val mediaRepository: MediaRepository
) : ViewModel() {
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    private val _preferredStreamIndexes = MutableStateFlow(PreferredStreamIndexes())
    val preferredStreamIndexes: StateFlow<PreferredStreamIndexes> = _preferredStreamIndexes.asStateFlow()
    private val _playbackCompletedEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val playbackCompletedEvents: SharedFlow<String> = _playbackCompletedEvents.asSharedFlow()

    var exoPlayer: ExoPlayer? = null
        private set

    private val trackSelectionCoordinator = PlayerTrackSelection()
    private var playbackSession = PlaybackSessionContext()
    private val playbackReporter = PlayerPlaybackReporter(
        mediaRepository = mediaRepository,
        scope = viewModelScope,
        positionProvider = { exoPlayer?.currentPosition ?: 0L },
        isPausedProvider = { exoPlayer?.playWhenReady != true }
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

    fun initializePlayer(
        context: Context,
        mediaId: String,
        preferredAudioStreamIndex: Int? = null,
        preferredSubtitleStreamIndex: Int? = null,
        initialSeekPositionMs: Long? = null,
        startPlayback: Boolean = true
    ) {
        viewModelScope.launch {
            try {
                _playerState.value = _playerState.value.copy(isLoading = true, error = null)
                _playerState.value = _playerState.value.copy(
                    introStartMs = null,
                    introEndMs = null,
                    chapterMarkers = emptyList()
                )

                playerContext = context
                hasHandledPlaybackCompletion = false
                val playerPreferences = com.jellycine.player.preferences.PlayerPreferences(context)
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
                playbackReporter.reset()
                spatializerHelper = SpatializerHelper(context)
                exoPlayer = PlayerUtils.createPlayer(context)
                downloadRepository = DownloadRepositoryProvider.getInstance(context)
                val offlinePath = downloadRepository?.getOfflineFilePath(mediaId)
                val hasOfflineFile = !offlinePath.isNullOrBlank() && File(offlinePath).exists()
                val offlineItemDetails = if (hasOfflineFile) {
                    downloadRepository?.offlineItemMetadata(mediaId)
                } else {
                    null
                }

                // Get item details to check for resume position
                val itemDetails = if (hasOfflineFile) {
                    offlineItemDetails ?: mediaRepository.getItemById(mediaId).getOrNull()
                } else {
                    mediaRepository.getItemById(mediaId).getOrNull()
                }
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
                val chapterMarkers = buildChapterMarkers(itemDetails?.chapters)
                val resolvedStartPositionMs = initialSeekPositionMs ?: storedResumePositionMs
                val introWindow = skipIntroWindow(itemDetails?.chapters)

                var primaryMediaSource: MediaSource? = null
                var sessionPlaySessionId: String? = null
                var sessionMediaSourceId: String? = null
                var sessionMediaSourceContainer: String? = null
                var sessionMediaSourceBitrateKbps: Int? = null
                var sessionPlayMethod = PlayMethod.DIRECT_PLAY
                var sessionIsOfflinePlayback = false
                var streamingMediaSource: androidx.media3.exoplayer.source.MediaSource? = null
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
                    val bitrateCapApplied = (maxStreamingBitrate ?: 0) > 0
                    sessionPlayMethod = when {
                        bitrateCapApplied -> PlayMethod.TRANSCODE
                        primaryMediaSource?.supportsDirectPlay == true -> PlayMethod.DIRECT_PLAY
                        primaryMediaSource?.supportsDirectStream == true -> PlayMethod.DIRECT_STREAM
                        else -> PlayMethod.TRANSCODE
                    }

                    val streamingResult = mediaRepository.getStreamingUrl(
                        itemId = mediaId,
                        maxStreamingBitrate = maxStreamingBitrate,
                        maxStreamingHeight = maxStreamingHeight,
                        audioStreamIndex = resolvedPreferredAudioStreamIndex,
                        subtitleStreamIndex = activePreferredSubtitleStreamIndex,
                        audioTranscodeMode = audioTranscodeMode,
                        playbackInfo = playbackInfo
                    )
                    if (streamingResult.isFailure) {
                        val error = streamingResult.exceptionOrNull()?.message ?: "Failed to get streaming URL"
                        _playerState.value = _playerState.value.copy(isLoading = false, error = error)
                        return@launch
                    }

                    val streamingUrl = streamingResult.getOrNull()
                    if (streamingUrl.isNullOrEmpty()) {
                        _playerState.value = _playerState.value.copy(isLoading = false, error = "Failed to get streaming URL")
                        return@launch
                    }
                    val streamUri = Uri.parse(streamingUrl)
                    val streamPlaySessionId = streamUri.getQueryParameter("PlaySessionId")
                        ?: streamUri.getQueryParameter("playSessionId")
                    if (!streamPlaySessionId.isNullOrBlank()) {
                        sessionPlaySessionId = streamPlaySessionId
                    }

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
                    streamingMediaSource = PlayerUtils.createStreamingMediaSource(
                        context = context,
                        mediaItem = streamingMediaItem
                    )
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

                val spatializationResult = apiMediaStreams?.let { streams ->
                    val audioStreams = streams.filter { it.type == "Audio" }
                    val primaryAudioStream = audioStreams.firstOrNull()
                    if (primaryAudioStream != null) {
                        val result = CodecCapabilityManager.canSpatializeAudioStream(context, primaryAudioStream)
                        result
                    } else {
                        null
                    }
                }
                
                exoPlayer?.apply {
                    addListener(playerListener)
                    if (streamingMediaSource != null) {
                        setMediaSource(streamingMediaSource!!)
                    } else {
                        setMediaItem(mediaItem)
                    }
                    prepare()

                    if (resolvedStartPositionMs != null && resolvedStartPositionMs > 0) {
                        seekTo(resolvedStartPositionMs)
                    }
                    
                    playWhenReady = startPlayback
                }

                // Spatial audio analysis and device capabilities
                val spatialInfo = spatializerHelper?.getSpatialAudioInfo()
                val contentSupportsSpatialization = spatializationResult?.canSpatialize == true
                val deviceSpatializerEnabled = spatialInfo?.let { it.isAvailable && it.isEnabled } == true
                val shouldEnableSpatialAudio = contentSupportsSpatialization && deviceSpatializerEnabled

                // Update track information after player is ready
                updateTrackInformation()
                val isHdrPlayback = PlayerMetadata.isCurrentPlaybackHdr(exoPlayer)
                
                // Apply start maximized setting if enabled
                applyStartMaximizedSetting(context)

                _playerState.value = _playerState.value.copy(
                    isLoading = false,
                    isPlaying = startPlayback,
                    mediaTitle = mediaTitle,
                    mediaLogoUrl = mediaLogoUrl,
                    seasonEpisodeLabel = seasonEpisodeLabel,
                    chapterMarkers = chapterMarkers,
                    introStartMs = introWindow?.first,
                    introEndMs = introWindow?.second,
                    isVideoTranscodingAllowed = isVideoTranscodingAllowed,
                    isAudioTranscodingAllowed = isAudioTranscodingAllowed,
                    currentAudioTranscodeMode = audioTranscodeMode,
                    spatializationResult = spatializationResult,
                    isSpatialAudioEnabled = shouldEnableSpatialAudio,
                    spatialAudioFormat = spatializationResult?.spatialFormat ?: "Stereo",
                    isHdrEnabled = isHdrPlayback
                )

            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Player initialization failed", e)
                _playerState.value = _playerState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Unknown error occurred"
                )
            }
        }
    }

    fun seekTo(position: Long) {
        exoPlayer?.seekTo(position)
        _playerState.value = _playerState.value.copy(currentPosition = position)
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
        persistPosition()
    }

    fun seekToProgress(progress: Float) {
        val duration = getDuration()
        if (duration > 0L) {
            seekTo((duration * progress).toLong())
        }
    }

    fun seekBy(deltaMs: Long) {
        val player = exoPlayer ?: return
        val duration = player.duration
        val targetPosition = if (duration > 0L) {
            (player.currentPosition + deltaMs).coerceIn(0L, duration)
        } else {
            (player.currentPosition + deltaMs).coerceAtLeast(0L)
        }
        seekTo(targetPosition)
    }

    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L

    fun isPlayingNow(): Boolean = exoPlayer?.isPlaying == true

    fun getDuration(): Long = exoPlayer?.duration ?: 0L

    private fun skipIntroWindow(chapters: List<ChapterInfo>?): Pair<Long, Long>? {
        val chapterList = chapters
            ?.mapNotNull { chapter ->
                val positionMs = chapter.startPositionTicks
                    ?.takeIf { it >= 0L }
                    ?.div(10_000L)
                    ?: return@mapNotNull null
                chapter to positionMs
            }
            ?.sortedBy { it.second }
            .orEmpty()

        if (chapterList.isEmpty()) return null

        val introStartIndex = chapterList.indexOfFirst { (chapter, _) ->
            chapter.name.isIntroStartMarker()
        }
        if (introStartIndex == -1) return null

        val introStartMs = chapterList[introStartIndex].second
        val explicitIntroEndMs = chapterList
            .drop(introStartIndex + 1)
            .firstOrNull { (chapter, _) -> chapter.name.isIntroEndMarker() }
            ?.second
        val fallbackIntroEndMs = chapterList
            .getOrNull(introStartIndex + 1)
            ?.second
        val introEndMs = explicitIntroEndMs ?: fallbackIntroEndMs

        return introEndMs
            ?.takeIf { it > introStartMs }
            ?.let { introStartMs to it }
    }

    private fun buildChapterMarkers(chapters: List<ChapterInfo>?): List<ChapterMarker> {
        return chapters
            ?.mapNotNull { chapter ->
                val positionMs = chapter.startPositionTicks
                    ?.takeIf { it >= 0L }
                    ?.div(10_000L)
                    ?: return@mapNotNull null
                ChapterMarker(
                    positionMs = positionMs,
                    label = chapter.name?.trim()?.takeIf { it.isNotEmpty() }
                )
            }
            ?.distinctBy { it.positionMs }
            ?.sortedBy { it.positionMs }
            .orEmpty()
    }

    private fun String?.isIntroStartMarker(): Boolean {
        val key = markerKey()
        return key == "intro" || key == "introstart"
    }

    private fun String?.isIntroEndMarker(): Boolean {
        return markerKey() == "introend"
    }

    private fun String?.markerKey(): String {
        return this
            ?.lowercase()
            ?.filterNot { it.isWhitespace() || it == '_' || it == '-' }
            .orEmpty()
    }

    private suspend fun isVideoTranscodingAllowedForUser(): Boolean {
        videoTranscodingAllowed?.let { return it }

        val user = mediaRepository.getCurrentUser().getOrNull()
        val allowed = user?.policy?.enableVideoPlaybackTranscoding
            ?: user?.let { true }
            ?: false

        videoTranscodingAllowed = allowed
        return allowed
    }

    private suspend fun isAudioTranscodingAllowedForUser(): Boolean {
        audioTranscodingAllowed?.let { return it }

        val user = mediaRepository.getCurrentUser().getOrNull()
        val allowed = user?.policy?.enableAudioPlaybackTranscoding
            ?: user?.let { true }
            ?: false

        audioTranscodingAllowed = allowed
        return allowed
    }

    fun togglePlayPause() {
        exoPlayer?.let { player ->
            if (player.isPlaying) {
                pause()
            } else {
                play()
            }
            playbackReporter.onPlaybackPauseStateChanged()
        }
    }

    fun setVolume(volume: Float) {
        exoPlayer?.volume = volume
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
        exoPlayer?.apply {
            removeListener(playerListener)
            release()
        }
        exoPlayer = null
        spatializerHelper?.cleanup()
        spatializerHelper = null
        playbackSession = PlaybackSessionContext()
        playbackReporter.reset()
        trackSelectionCoordinator.clear()
        apiMediaStreams = null
        defaultAudioStreamIndex = null
        defaultSubtitleStreamIndex = null
        playerContext = null
        downloadRepository = null
        hasHandledPlaybackCompletion = false
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

    /**
     * Select audio track by ID
     */
    fun selectAudioTrack(trackId: String) {
        if (trackId == _playerState.value.currentAudioTrack?.id) return
        val selectedTrack = _playerState.value.availableAudioTracks.firstOrNull { it.id == trackId } ?: return
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

    private fun playbackTrackSelection(
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?
    ) {
        val context = playerContext ?: return
        val mediaId = playbackSession.mediaId ?: return
        val resumePositionMs = getCurrentPosition()
        val shouldResumePlaying = isPlayingNow()

        com.jellycine.player.preferences.PlayerPreferences(context).apply {
            setPreferredAudioStreamIndex(mediaId, audioStreamIndex)
            setPreferredSubtitleStreamIndex(mediaId, subtitleStreamIndex)
        }

        releasePlayer()
        initializePlayer(
            context = context,
            mediaId = mediaId,
            preferredAudioStreamIndex = audioStreamIndex,
            preferredSubtitleStreamIndex = subtitleStreamIndex,
            initialSeekPositionMs = resumePositionMs,
            startPlayback = shouldResumePlaying
        )
    }

    // Aspect ratio states
    private var currentAspectRatio by mutableIntStateOf(0)
    private val aspectRatioModes = listOf("Fit", "Zoom")
    
    // Keep track of ExoPlayer resize mode
    private var currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT

    /**
     * Toggle between fit and zoom modes
     * Uses ExoPlayer's native AspectRatioFrameLayout resize modes for proper aspect ratio handling
     */
    fun cycleAspectRatio() {
        currentAspectRatio = (currentAspectRatio + 1) % aspectRatioModes.size
        
        currentResizeMode = when (currentAspectRatio) {
            0 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT // Respects video aspect ratio
            1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM // Fills screen, crops if needed
            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        
        val mode = aspectRatioModes[currentAspectRatio]
        _playerState.value = _playerState.value.copy(
            aspectRatioMode = mode,
            videoScale = 1f,
            videoOffsetX = 0f,
            videoOffsetY = 0f
        )
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
            currentAspectRatio = 1
            currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            _playerState.value = _playerState.value.copy(
                aspectRatioMode = "Zoom",
                videoScale = 1f,
                videoOffsetX = 0f,
                videoOffsetY = 0f
            )
        } else if (!isZooming && currentAspectRatio == 1) {
            // Switch to fit mode when user pinches to fit
            currentAspectRatio = 0
            currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            _playerState.value = _playerState.value.copy(
                aspectRatioMode = "Fit",
                videoScale = 1f,
                videoOffsetX = 0f,
                videoOffsetY = 0f
            )
        }
    }

    /**
     * Apply start maximized setting based on user preference
     * Uses ExoPlayer's native resize modes for proper aspect ratio handling
     */
    private fun applyStartMaximizedSetting(context: Context) {
        val playerPreferences = com.jellycine.player.preferences.PlayerPreferences(context)
        val startMaximized = playerPreferences.isStartMaximizedEnabled()
        
        if (startMaximized) {
            currentAspectRatio = 1 // Zoom mode
            currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            _playerState.value = _playerState.value.copy(
                aspectRatioMode = "Zoom",
                videoScale = 1f,
                videoOffsetX = 0f,
                videoOffsetY = 0f
            )
        } else {
            currentAspectRatio = 0 // Fit mode
            currentResizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            _playerState.value = _playerState.value.copy(
                aspectRatioMode = "Fit",
                videoScale = 1f,
                videoOffsetX = 0f,
                videoOffsetY = 0f
            )
        }
    }

    /**
     * Update video transform values
     */
    fun updateVideoTransform(scale: Float, offsetX: Float, offsetY: Float) {
        val mode = aspectRatioModes[currentAspectRatio]
        _playerState.value = _playerState.value.copy(
            videoScale = scale,
            videoOffsetX = offsetX,
            videoOffsetY = offsetY,
            aspectRatioMode = mode
        )
    }

    /**
     * Seek backward by the configured interval
     */
    fun seekBackward() {
        val seconds = com.jellycine.player.preferences.PlayerPreferences(playerContext ?: return)
            .getSeekBackwardIntervalSeconds()
        seekBy(deltaMs = -(seconds * 1000L))
    }

    /**
     * Seek forward by the configured interval
     */
    fun seekForward() {
        val seconds = com.jellycine.player.preferences.PlayerPreferences(playerContext ?: return)
            .getSeekForwardIntervalSeconds()
        seekBy(deltaMs = seconds * 1000L)
    }

    private val playerListener = object : Player.Listener {
        override fun onTracksChanged(tracks: Tracks) {
            applyPendingTrackSelectionsIfNeeded()
            updateTrackInformation()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val wasPlaying = _playerState.value.isPlaying
            val playWhenReady = exoPlayer?.playWhenReady == true
            val isNowPlaying = playbackState == Player.STATE_READY && playWhenReady
            val hasReportedStart = playbackReporter.hasReportedStart()
            val shouldShowLoading = when (playbackState) {
                Player.STATE_IDLE -> !hasReportedStart
                Player.STATE_BUFFERING -> playWhenReady || !hasReportedStart
                else -> false
            }
            
            _playerState.value = _playerState.value.copy(
                isLoading = shouldShowLoading,
                isPlaying = isNowPlaying
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
            val playbackState = exoPlayer?.playbackState ?: Player.STATE_IDLE
            val hasReportedStart = playbackReporter.hasReportedStart()
            val shouldShowLoading = when (playbackState) {
                Player.STATE_IDLE -> !hasReportedStart
                Player.STATE_BUFFERING -> playWhenReady || !hasReportedStart
                else -> false
            }
            _playerState.value = _playerState.value.copy(
                isPlaying = playWhenReady && playbackState == Player.STATE_READY,
                isLoading = shouldShowLoading
            )
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _playerState.value = _playerState.value.copy(
                error = error.message ?: "Playback error occurred",
                isLoading = false,
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
                duration = exoPlayer?.duration ?: 0L
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
