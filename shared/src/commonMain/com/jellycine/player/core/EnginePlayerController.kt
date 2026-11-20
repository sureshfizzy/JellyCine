package com.jellycine.player.core

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaStream
import com.jellycine.data.repository.MediaRepository
import com.jellycine.detail.CodecCapabilityManager
import com.jellycine.player.mpv.MPVPlayer
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.resources.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString

/**
 * Runs playback on a single [PlayerEngine] (mpv) and exposes it to the shared player screen.
 * Tracks come from the server's stream list, so it needs nothing engine specific. iOS uses this
 * directly.
 */
class EnginePlayerController(
    private val mediaRepository: MediaRepository,
    private val preferences: PlayerPreferences,
    private val scope: CoroutineScope,
    engineFactory: () -> PlayerEngine,
    private val onPlaybackCompleted: (String) -> Unit = {}
) : PlayerController {
    private val _playerState = MutableStateFlow(PlayerState())
    override val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _preferredStreamIndexes = MutableStateFlow(PreferredStreamIndexes())

    /** The engine playing the current item; the video surface attaches to it. */
    val engine: PlayerEngine = engineFactory()

    private val playerPlayback = PlayerPlayback(mediaRepository)
    private val reporter = PlayerPlaybackReporter(
        mediaRepository = mediaRepository,
        scope = scope,
        positionProvider = { getCurrentPosition() },
        isPausedProvider = { !isPlayingNow() }
    )

    private var playbackSession = PlaybackSessionContext()
    private var apiMediaStreams: List<MediaStream>? = null
    private var defaultAudioStreamIndex: Int? = null
    private var defaultSubtitleStreamIndex: Int? = null
    private var externalSubtitleUrls: Map<Int, String> = emptyMap()
    private var currentItemDetails: BaseItemDto? = null
    private var segmentsJob: Job? = null
    private var bufferedHighWaterMarkMs = 0L
    private var hasHandledCompletion = false
    private var aspectRatioIndex = 0
    private var lastStart: StartRequest? = null

    private class StartRequest(
        val mediaId: String,
        val initialItemDetails: BaseItemDto?,
        val preferredAudioStreamIndex: Int?,
        val preferredSubtitleStreamIndex: Int?
    )

    init {
        engine.setListener(
            object : PlayerEngineListener {
                override fun onBuffering() {
                    _playerState.value = _playerState.value.copy(isLoading = true)
                }

                override fun onReady() {
                    val wasPlaying = _playerState.value.isPlaying
                    val playing = isPlayingNow()
                    _playerState.value = _playerState.value.copy(
                        isLoading = false,
                        isPlaying = playing,
                        playWhenReady = playing,
                        hasStartedPlayback = true,
                        duration = getDuration()
                    )
                    if (!reporter.hasReportedStart() && playing) reporter.reportPlaybackStatus()
                    if (wasPlaying != playing) reporter.onPlaybackPauseStateChanged()
                }

                override fun onEnded() {
                    _playerState.value = _playerState.value.copy(
                        isPlaying = false,
                        playWhenReady = false,
                        isLoading = false
                    )
                    handlePlaybackCompleted()
                }

                override fun onError(message: String) {
                    _playerState.value = _playerState.value.copy(
                        error = message,
                        isLoading = false,
                        isPlaying = false,
                        playWhenReady = false
                    )
                    if (reporter.hasReportedStart()) reporter.reportPlaybackStopped(failed = true)
                }
            }
        )
    }

    /** Resolves and starts playback of a library item. */
    fun start(
        mediaId: String,
        initialItemDetails: BaseItemDto? = null,
        preferredAudioStreamIndex: Int? = null,
        preferredSubtitleStreamIndex: Int? = null,
        initialSeekPositionMs: Long? = null,
        startPlayback: Boolean = true
    ) {
        lastStart = StartRequest(mediaId, initialItemDetails, preferredAudioStreamIndex, preferredSubtitleStreamIndex)
        scope.launch {
            try {
                resetSession()
                _playerState.value = PlayerState(isLoading = true, playWhenReady = startPlayback)

                val audioIndex = preferredAudioStreamIndex ?: preferences.getPreferredAudioStreamIndex(mediaId)
                val subtitleIndex = preferredSubtitleStreamIndex ?: preferences.getPreferredSubtitleStreamIndex(mediaId)
                _preferredStreamIndexes.value = PreferredStreamIndexes(audioIndex, subtitleIndex)

                val playback = when (
                    val resolution = playerPlayback.resolve(
                        mediaId = mediaId,
                        initialItemDetails = initialItemDetails,
                        preferences = preferences,
                        preferredAudioStreamIndex = audioIndex,
                        preferredSubtitleStreamIndex = subtitleIndex,
                        offline = null,
                        includeAccessToken = true
                    )
                ) {
                    is PlaybackResolution.Failed -> {
                        _playerState.value = _playerState.value.copy(isLoading = false, error = resolution.message)
                        return@launch
                    }
                    is PlaybackResolution.Ready -> resolution.playback
                }

                currentItemDetails = playback.itemDetails
                apiMediaStreams = playback.mediaStreams
                defaultAudioStreamIndex = playback.defaultAudioStreamIndex
                defaultSubtitleStreamIndex = playback.defaultSubtitleStreamIndex
                playbackSession = playback.session
                reporter.updateSession(playbackSession)
                externalSubtitleUrls = MPVPlayer.externalSubtitleUrls(
                    playbackRequest = playback.playbackRequest,
                    mediaStreams = apiMediaStreams.orEmpty(),
                    itemId = mediaId,
                    mediaSourceId = playback.session.mediaSourceId
                )

                val selectedAudio = audioIndex ?: defaultAudioStreamIndex
                val selectedSubtitle = subtitleIndex ?: defaultSubtitleStreamIndex
                val selectedSubtitleUrl = selectedSubtitle?.let(externalSubtitleUrls::get)
                engine.load(
                    url = playback.streamUrl.orEmpty(),
                    subtitleUrls = externalSubtitleUrls.values.toList(),
                    audioTrackId = MPVPlayer.audioTrackId(apiMediaStreams, selectedAudio),
                    subtitleTrackId = MPVPlayer.subtitleTrackId(apiMediaStreams, selectedSubtitle),
                    selectedSubtitleUrl = selectedSubtitleUrl,
                    startPositionMs = initialSeekPositionMs ?: playback.storedResumePositionMs,
                    startPlayback = startPlayback,
                    requestHeaders = playback.playbackRequest?.requestHeaders.orEmpty(),
                    useAppleDisplay = CodecCapabilityManager.usesAppleDolbyVisionDisplay(
                        mediaStreams = apiMediaStreams,
                        container = playback.session.mediaSourceContainer,
                        streamUrl = playback.streamUrl,
                        transcoded = playback.session.playMethod == PlayMethod.TRANSCODE,
                        externalSubtitleSelected = selectedSubtitleUrl != null
                    )
                )

                val hdrFormat = hdrFormatLabel()
                _playerState.value = _playerState.value.copy(
                    isLoading = true,
                    playWhenReady = startPlayback,
                    mediaTitle = playback.mediaTitle,
                    mediaLogoUrl = playback.mediaLogoUrl,
                    seasonEpisodeLabel = playback.seasonEpisodeLabel,
                    chapterMarkers = playback.chapterMarkers,
                    introStartMs = playback.introSegment?.startMs,
                    introEndMs = playback.introSegment?.endMs,
                    isVideoTranscodingAllowed = playback.isVideoTranscodingAllowed,
                    isAudioTranscodingAllowed = playback.isAudioTranscodingAllowed,
                    currentAudioTranscodeMode = playback.audioTranscodeMode,
                    isHdrEnabled = hdrFormat.isNotBlank(),
                    hdrFormat = hdrFormat
                )
                applyTrackState()
                setAspectRatioMode(if (preferences.isStartMaximizedEnabled()) 1 else 0)
                playback.itemDetails?.let { applyCommunitySegments(mediaId, it) }
            } catch (e: Exception) {
                _playerState.value = _playerState.value.copy(
                    isLoading = false,
                    error = e.message ?: getString(Res.string.player_error_unknown)
                )
            }
        }
    }

    /** Plays a bare URL such as a trailer. */
    fun startRemote(url: String, title: String?, startPlayback: Boolean = true) {
        lastStart = null
        scope.launch {
            resetSession()
            _playerState.value = PlayerState(
                isLoading = true,
                playWhenReady = startPlayback,
                mediaTitle = title?.takeIf { it.isNotBlank() } ?: getString(Res.string.player_title_trailer)
            )
            engine.load(
                url = url,
                subtitleUrls = emptyList(),
                audioTrackId = null,
                subtitleTrackId = null,
                selectedSubtitleUrl = null,
                startPositionMs = null,
                startPlayback = startPlayback
            )
            setAspectRatioMode(if (preferences.isStartMaximizedEnabled()) 1 else 0)
        }
    }

    /** Re-resolves the current item, e.g. after streaming quality changed, keeping the position. */
    fun restart() {
        val request = lastStart ?: return
        start(
            mediaId = request.mediaId,
            initialItemDetails = request.initialItemDetails ?: currentItemDetails,
            preferredAudioStreamIndex = _preferredStreamIndexes.value.audioStreamIndex,
            preferredSubtitleStreamIndex = _preferredStreamIndexes.value.subtitleStreamIndex,
            initialSeekPositionMs = getCurrentPosition(),
            startPlayback = isPlayingNow()
        )
    }

    fun getSourceVideoHeight(): Int? = sourceVideoHeight(apiMediaStreams)

    override fun getDuration(): Long {
        val engineDuration = engine.duration
        if (engineDuration > 0L) return engineDuration
        return currentItemDetails?.runTimeTicks?.takeIf { it > 0L }?.div(10_000L) ?: 0L
    }

    override fun getCurrentPosition(): Long = engine.currentPosition

    override fun getBufferedPosition(): Long {
        bufferedHighWaterMarkMs = maxOf(bufferedHighWaterMarkMs, engine.bufferedPosition)
        val duration = getDuration()
        return if (duration > 0L) bufferedHighWaterMarkMs.coerceAtMost(duration) else bufferedHighWaterMarkMs
    }

    override fun isPlayingNow(): Boolean = engine.isPlaying

    override fun play() {
        engine.play()
        _playerState.value = _playerState.value.copy(isPlaying = true, playWhenReady = true)
    }

    override fun pause() {
        engine.pause()
        _playerState.value = _playerState.value.copy(isPlaying = false, playWhenReady = false)
    }

    override fun togglePlayPause() {
        if (isPlayingNow()) pause() else play()
        reporter.onPlaybackPauseStateChanged()
    }

    override fun seekTo(position: Long) {
        engine.seekTo(position)
        _playerState.value = _playerState.value.copy(currentPosition = position)
        reporter.onPlaybackPositionDiscontinuity()
    }

    override fun seekToProgress(progress: Float) {
        val duration = getDuration()
        if (duration > 0L) seekTo((duration * progress).toLong())
    }

    override fun seekBy(deltaMs: Long) {
        val duration = getDuration()
        val target = getCurrentPosition() + deltaMs
        seekTo(if (duration > 0L) target.coerceIn(0L, duration) else target.coerceAtLeast(0L))
    }

    override fun seekBackward() = seekBy(-(preferences.getSeekBackwardIntervalSeconds() * 1000L))

    override fun seekForward() = seekBy(preferences.getSeekForwardIntervalSeconds() * 1000L)

    override fun toggleLock() {
        val state = _playerState.value
        _playerState.value = state.copy(
            isLocked = !state.isLocked,
            showControls = if (!state.isLocked) false else state.showControls
        )
    }

    override fun cycleAspectRatio() = setAspectRatioMode((aspectRatioIndex + 1) % ASPECT_RATIO_MODES.size)

    override fun handlePinchZoom(isZooming: Boolean) {
        if (isZooming && aspectRatioIndex == 0) {
            setAspectRatioMode(1)
        } else if (!isZooming && aspectRatioIndex == 1) {
            setAspectRatioMode(0)
        }
    }

    override fun selectAudioTrack(trackId: String) {
        if (trackId == _playerState.value.currentAudioTrack?.id) return
        val track = _playerState.value.availableAudioTracks.firstOrNull { it.id == trackId } ?: return
        val streamIndex = MPVPlayer.selectAudioTrack(engine, track) ?: return
        val mediaId = playbackSession.mediaId ?: return
        preferences.setPreferredAudioStreamIndex(mediaId, streamIndex)
        _preferredStreamIndexes.value = _preferredStreamIndexes.value.copy(audioStreamIndex = streamIndex)
        _playerState.value = _playerState.value.copy(currentAudioTrack = track)
    }

    override fun selectSubtitleTrack(trackId: String) {
        if (trackId == _playerState.value.currentSubtitleTrack?.id) return
        val track = _playerState.value.availableSubtitleTracks.firstOrNull { it.id == trackId } ?: return
        val streamIndex = MPVPlayer.selectSubtitleTrack(engine, track, externalSubtitleUrls) ?: return
        val mediaId = playbackSession.mediaId ?: return
        val stored = streamIndex.takeUnless { it < 0 }
        preferences.setPreferredSubtitleStreamIndex(mediaId, stored)
        _preferredStreamIndexes.value = _preferredStreamIndexes.value.copy(subtitleStreamIndex = stored)
        _playerState.value = _playerState.value.copy(currentSubtitleTrack = track)
    }

    override fun releasePlayer() {
        reporter.reportPlaybackStopped()
        resetSession()
        engine.release()
        _playerState.value = PlayerState()
    }

    /** Stops reporting and clears per-item state without touching the engine. */
    private fun resetSession() {
        segmentsJob?.cancel()
        segmentsJob = null
        reporter.reportPlaybackStopped()
        reporter.reset()
        playbackSession = PlaybackSessionContext()
        apiMediaStreams = null
        defaultAudioStreamIndex = null
        defaultSubtitleStreamIndex = null
        externalSubtitleUrls = emptyMap()
        currentItemDetails = null
        bufferedHighWaterMarkMs = 0L
        hasHandledCompletion = false
        _preferredStreamIndexes.value = PreferredStreamIndexes()
    }

    private fun handlePlaybackCompleted() {
        if (hasHandledCompletion) return
        hasHandledCompletion = true
        reporter.reportPlaybackStopped()
        playbackSession.mediaId?.let(onPlaybackCompleted)
    }

    private fun hdrFormatLabel(): String {
        return CodecCapabilityManager.detectBestSourceHDRFormat(apiMediaStreams)
            .ifBlank { if (MPVPlayer.isHdr(apiMediaStreams)) "HDR" else "" }
    }

    private fun applyTrackState() {
        val tracks = MPVPlayer.trackState(
            mediaStreams = apiMediaStreams,
            selectedAudioStreamIndex = _preferredStreamIndexes.value.audioStreamIndex,
            selectedSubtitleStreamIndex = _preferredStreamIndexes.value.subtitleStreamIndex,
            defaultAudioStreamIndex = defaultAudioStreamIndex,
            defaultSubtitleStreamIndex = defaultSubtitleStreamIndex
        )
        _playerState.value = _playerState.value.copy(
            availableAudioTracks = tracks.availableAudioTracks,
            currentAudioTrack = tracks.currentAudioTrack,
            availableSubtitleTracks = tracks.availableSubtitleTracks,
            currentSubtitleTrack = tracks.currentSubtitleTrack,
            availableVideoTracks = tracks.availableVideoTracks
        )
    }

    private fun setAspectRatioMode(index: Int) {
        aspectRatioIndex = index.coerceIn(0, ASPECT_RATIO_MODES.lastIndex)
        engine.setZoomMode(aspectRatioIndex == 1)
        _playerState.value = _playerState.value.copy(
            aspectRatioMode = ASPECT_RATIO_MODES[aspectRatioIndex],
            videoScale = 1f,
            videoOffsetX = 0f,
            videoOffsetY = 0f
        )
    }

    private fun applyCommunitySegments(mediaId: String, item: BaseItemDto) {
        if (!item.type.equals("Episode", ignoreCase = true)) return
        segmentsJob?.cancel()
        segmentsJob = scope.launch {
            val segments = mediaRepository.getCommunityPlaybackSegments(item).getOrNull() ?: return@launch
            if (playbackSession.mediaId != mediaId) return@launch
            val state = _playerState.value
            _playerState.value = state.copy(
                recapStartMs = state.recapStartMs ?: segments.recap?.startMs,
                recapEndMs = state.recapEndMs ?: segments.recap?.endMs,
                introStartMs = state.introStartMs ?: segments.intro?.startMs,
                introEndMs = state.introEndMs ?: segments.intro?.endMs,
                creditsStartMs = state.creditsStartMs ?: segments.credits?.startMs,
                creditsEndMs = state.creditsEndMs ?: segments.credits?.endMs,
                previewStartMs = state.previewStartMs ?: segments.preview?.startMs,
                previewEndMs = state.previewEndMs ?: segments.preview?.endMs
            )
        }
    }

    private companion object {
        val ASPECT_RATIO_MODES = listOf("Fit", "Zoom")
    }
}
