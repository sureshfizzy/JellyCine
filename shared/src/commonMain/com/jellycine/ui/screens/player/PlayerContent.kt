package com.jellycine.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.AudioTranscodeMode
import com.jellycine.player.core.PlayerConstants.CONTROLS_AUTO_HIDE_DELAY
import com.jellycine.player.core.PlayerConstants.GESTURE_INDICATOR_HIDE_DELAY
import com.jellycine.player.core.PlayerConstants.NEXT_EPISODE_AUTOPLAY_DELAY
import com.jellycine.player.core.PlayerConstants.NEXT_EPISODE_PROGRESS_UPDATE_DELAY
import com.jellycine.player.core.PlayerController
import com.jellycine.player.core.PlayerState
import com.jellycine.player.core.SkippableSegmentAction
import com.jellycine.player.core.SkippableSegmentType
import com.jellycine.player.core.findActiveSkippableSegment
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.resources.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

/** Short-lived UI state for the player screen. */
data class PlayerUiState(
    val controlsVisible: Boolean = true,
    val currentPosition: Long = 0L,
    val bufferedPosition: Long = 0L,
    val isPlaying: Boolean = false,
    val volumeLevel: Float? = null,
    val brightnessLevel: Float? = null,
    val seekPosition: String? = null,
    val seekSide: SeekSide = SeekSide.CENTER
)

private const val PLAYER_POSITION_UPDATE_MS = 250L
private const val CONTROLS_HIDE_AFTER_MS = 3000L

/**
 * The whole player screen: video surface slot, gestures, controls overlay and dialogs.
 * The host supplies the video surface and the controller that drives playback.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlayerContent(
    controller: PlayerController,
    preferences: PlayerPreferences,
    platform: PlayerPlatform,
    mediaId: String,
    sourceVideoHeight: Int?,
    mediaInfoProvider: () -> MediaMetadataInfo?,
    onApplyPlaybackSettings: (quality: String, audioMode: AudioTranscodeMode) -> Unit,
    onBackPressed: (() -> Unit)?,
    modifier: Modifier = Modifier,
    previousEpisodeId: String? = null,
    onWatchPreviousEpisode: ((String) -> Unit)? = null,
    nextEpisodeId: String? = null,
    onWatchNextEpisode: ((String) -> Unit)? = null,
    onNextEpisodePrefetchChange: (Boolean) -> Unit = {},
    videoSurface: @Composable (Modifier) -> Unit
) {
    var uiState by remember { mutableStateOf(PlayerUiState()) }
    var autoHideKey by remember { mutableStateOf(0) }
    var isScrubbing by remember { mutableStateOf(false) }
    var dismissedCreditsPrompt by remember(mediaId) { mutableStateOf(false) }

    val resetAutoHideTimer = {
        autoHideKey++
        platform.hideSystemBars()
    }

    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showSubtitleTrackDialog by remember { mutableStateOf(false) }
    var showStreamingQualityDialog by remember { mutableStateOf(false) }
    var showAudioTranscodingDialog by remember { mutableStateOf(false) }
    var pendingStreamingQualitySelection by remember { mutableStateOf<String?>(null) }
    var showMediaInfo by remember { mutableStateOf(false) }
    val mediaInfoSnapshot = remember(showMediaInfo, controller) {
        if (showMediaInfo) mediaInfoProvider() else null
    }

    val playerState by controller.playerState.collectAsState()
    val availableStreamingQualityOptions = remember(
        sourceVideoHeight,
        playerState.isVideoTranscodingAllowed
    ) {
        if (playerState.isVideoTranscodingAllowed) {
            PlayerPreferences.getStreamingQualityOptions(sourceVideoHeight)
        } else {
            listOf(PlayerPreferences.STREAMING_QUALITY_ORIGINAL)
        }
    }

    val useDeviceVolumeInPlayer = remember { preferences.isUseDeviceVolumeInPlayerEnabled() }
    val useDeviceBrightnessInPlayer = remember { preferences.isUseDeviceBrightnessInPlayerEnabled() }
    var playerBrightness by remember(useDeviceBrightnessInPlayer) {
        mutableStateOf(
            if (useDeviceBrightnessInPlayer) {
                platform.deviceBrightness() ?: preferences.getPlayerBrightness()
            } else {
                preferences.getPlayerBrightness()
            }
        )
    }
    var playerVolume by remember(useDeviceVolumeInPlayer) {
        mutableStateOf(
            if (useDeviceVolumeInPlayer) platform.deviceVolume() else preferences.getPlayerVolume()
        )
    }
    var currentStreamingQuality by remember { mutableStateOf(preferences.getStreamingQuality()) }
    val skipIntroEnabled = remember { preferences.isSkipIntroEnabled() }
    var currentAudioTranscodeMode by remember { mutableStateOf(preferences.getAudioTranscodeMode()) }
    val seekBackwardSeconds = preferences.getSeekBackwardIntervalSeconds()
    val seekForwardSeconds = preferences.getSeekForwardIntervalSeconds()
    val chapterMarkersEnabled = preferences.areChapterMarkersEnabled()

    LaunchedEffect(playerState.currentAudioTranscodeMode) {
        currentAudioTranscodeMode = playerState.currentAudioTranscodeMode
    }

    val hasPlaybackSettings = playerState.isVideoTranscodingAllowed || playerState.isAudioTranscodingAllowed
    val playbackDuration = controller.getDuration()
    val activeSkippableSegment = remember(
        skipIntroEnabled,
        playerState.isLocked,
        playerState.recapStartMs,
        playerState.recapEndMs,
        playerState.introStartMs,
        playerState.introEndMs,
        playerState.creditsStartMs,
        playerState.creditsEndMs,
        playerState.previewStartMs,
        playerState.previewEndMs,
        playbackDuration,
        uiState.currentPosition
    ) {
        if (!skipIntroEnabled || playerState.isLocked) {
            null
        } else {
            playerState.findActiveSkippableSegment(
                positionMs = uiState.currentPosition,
                durationMs = playbackDuration
            )
        }
    }
    val activeCreditsSegment = activeSkippableSegment?.takeIf { it.type == SkippableSegmentType.CREDITS }
    val canWatchPreviousEpisode = !previousEpisodeId.isNullOrBlank() && onWatchPreviousEpisode != null
    val canWatchNextEpisode = !nextEpisodeId.isNullOrBlank() && onWatchNextEpisode != null

    LaunchedEffect(activeCreditsSegment?.startMs, activeCreditsSegment?.endMs) {
        if (activeCreditsSegment == null) dismissedCreditsPrompt = false
    }

    LaunchedEffect(activeCreditsSegment != null, canWatchNextEpisode, dismissedCreditsPrompt) {
        onNextEpisodePrefetchChange(
            activeCreditsSegment != null && canWatchNextEpisode && !dismissedCreditsPrompt
        )
    }

    LaunchedEffect(Unit) {
        platform.setBrightness(playerBrightness)
        platform.setDeviceVolume(playerVolume)
    }

    LaunchedEffect(controller) {
        while (true) {
            val currentPosition = controller.getCurrentPosition()
            val bufferedPosition = controller.getBufferedPosition()
            val isPlayingNow = controller.isPlayingNow()
            val current = uiState
            if (current.currentPosition != currentPosition ||
                current.bufferedPosition != bufferedPosition ||
                current.isPlaying != isPlayingNow
            ) {
                uiState = current.copy(
                    currentPosition = currentPosition,
                    bufferedPosition = bufferedPosition,
                    isPlaying = isPlayingNow
                )
            }
            delay(PLAYER_POSITION_UPDATE_MS)
        }
    }

    LaunchedEffect(uiState.volumeLevel) {
        if (uiState.volumeLevel != null) {
            delay(CONTROLS_AUTO_HIDE_DELAY)
            uiState = uiState.copy(volumeLevel = null)
        }
    }

    LaunchedEffect(uiState.brightnessLevel) {
        if (uiState.brightnessLevel != null) {
            delay(CONTROLS_AUTO_HIDE_DELAY)
            uiState = uiState.copy(brightnessLevel = null)
        }
    }

    LaunchedEffect(uiState.seekPosition) {
        if (uiState.seekPosition != null) {
            delay(GESTURE_INDICATOR_HIDE_DELAY)
            uiState = uiState.copy(seekPosition = null)
        }
    }

    LaunchedEffect(
        uiState.controlsVisible,
        showAudioTrackDialog,
        showSubtitleTrackDialog,
        showStreamingQualityDialog,
        showAudioTranscodingDialog,
        showMediaInfo
    ) {
        if (
            uiState.controlsVisible ||
            showAudioTrackDialog ||
            showSubtitleTrackDialog ||
            showStreamingQualityDialog ||
            showAudioTranscodingDialog ||
            showMediaInfo
        ) {
            platform.hideSystemBars()
        }
    }

    LaunchedEffect(uiState.controlsVisible, playerState.hasStartedPlayback, autoHideKey, isScrubbing) {
        if (uiState.controlsVisible && playerState.hasStartedPlayback && !isScrubbing) {
            delay(CONTROLS_HIDE_AFTER_MS)
            uiState = uiState.copy(controlsVisible = false)
        }
    }

    val closePlaybackSettingsDialogs = {
        pendingStreamingQualitySelection = null
        showStreamingQualityDialog = false
        showAudioTranscodingDialog = false
    }

    val applyPlaybackSettingsSelection: (String, AudioTranscodeMode) -> Unit =
        applyPlaybackSettingsSelection@{ quality, audioMode ->
            val selectedQuality = quality.trim()
            val qualityChanged = selectedQuality.isNotEmpty() && selectedQuality != currentStreamingQuality
            val audioModeChanged = audioMode != currentAudioTranscodeMode

            closePlaybackSettingsDialogs()

            if (selectedQuality.isEmpty()) return@applyPlaybackSettingsSelection

            preferences.setStreamingQuality(selectedQuality)
            currentStreamingQuality = preferences.getStreamingQuality()
            preferences.setAudioTranscodeMode(audioMode)
            currentAudioTranscodeMode = preferences.getAudioTranscodeMode()

            if (!qualityChanged && !audioModeChanged) return@applyPlaybackSettingsSelection

            uiState = uiState.copy(controlsVisible = true)
            onApplyPlaybackSettings(selectedQuality, audioMode)
        }

    val applyStreamingQualitySelection: (String) -> Unit = { selectedQuality ->
        val selection = selectedQuality.trim()
        when {
            !playerState.isVideoTranscodingAllowed || selection.isEmpty() -> closePlaybackSettingsDialogs()
            playerState.isAudioTranscodingAllowed -> {
                pendingStreamingQualitySelection = selection
                showStreamingQualityDialog = false
                showAudioTranscodingDialog = true
            }
            else -> applyPlaybackSettingsSelection(selection, currentAudioTranscodeMode)
        }
    }

    LaunchedEffect(playerState.isVideoTranscodingAllowed, playerState.isAudioTranscodingAllowed) {
        if (!playerState.isVideoTranscodingAllowed) closePlaybackSettingsDialogs()
        if (!playerState.isAudioTranscodingAllowed) {
            pendingStreamingQualitySelection = null
            showAudioTranscodingDialog = false
        }
    }

    BackHandler {
        controller.releasePlayer()
        onBackPressed?.invoke()
    }

    CompositionLocalProvider(LocalPlayerPlatform provides platform) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusable()
        ) {
            videoSurface(Modifier.fillMaxSize())

            PlayerGestureSurface(
                preferences = preferences,
                getVolume = { playerVolume },
                onTap = {
                    resetAutoHideTimer()
                    uiState = uiState.copy(controlsVisible = !uiState.controlsVisible)
                },
                onSeek = { delta ->
                    if (!playerState.isLocked) {
                        controller.seekBy(delta)

                        val isForward = delta > 0
                        val seconds = kotlin.math.abs(delta) / 1000
                        val seekText = if (isForward) "+${seconds}s" else "-${seconds}s"
                        val side = if (isForward) SeekSide.RIGHT else SeekSide.LEFT

                        uiState = uiState.copy(seekPosition = seekText, seekSide = side)
                    }
                },
                onTogglePlayPause = controller::togglePlayPause,
                onVolumeChange = { level ->
                    if (!playerState.isLocked) {
                        playerVolume = level.coerceIn(0f, 1f)
                        if (!useDeviceVolumeInPlayer) {
                            preferences.setPlayerVolume(playerVolume)
                        }
                        platform.setDeviceVolume(playerVolume)
                        uiState = uiState.copy(volumeLevel = playerVolume)
                    }
                },
                onBrightnessChange = { delta ->
                    if (!playerState.isLocked) {
                        val newPlayerBrightness = (playerBrightness + delta).coerceIn(0.01f, 1f)
                        playerBrightness = newPlayerBrightness
                        if (!useDeviceBrightnessInPlayer) {
                            preferences.setPlayerBrightness(newPlayerBrightness)
                        }
                        platform.setBrightness(newPlayerBrightness)
                        uiState = uiState.copy(brightnessLevel = newPlayerBrightness)
                    }
                },
                onZoomChange = controller::handlePinchZoom,
                modifier = Modifier.fillMaxSize()
            )

            PlayerOverlayHost(
                uiState = uiState,
                playerState = playerState,
                currentStreamingQuality = currentStreamingQuality,
                hasPlaybackSettings = hasPlaybackSettings,
                chapterMarkersEnabled = chapterMarkersEnabled,
                seekBackwardSeconds = seekBackwardSeconds,
                seekForwardSeconds = seekForwardSeconds,
                activeSkippableSegment = activeSkippableSegment,
                activeCreditsSegment = activeCreditsSegment,
                dismissedCreditsPrompt = dismissedCreditsPrompt,
                canWatchPreviousEpisode = canWatchPreviousEpisode,
                canWatchNextEpisode = canWatchNextEpisode,
                controller = controller,
                onBackPressed = onBackPressed,
                resetAutoHideTimer = resetAutoHideTimer,
                onScrubbingChange = { isScrubbing = it },
                onWatchCredits = {
                    dismissedCreditsPrompt = true
                    uiState = uiState.copy(controlsVisible = false)
                },
                onWatchPreviousEpisode = {
                    previousEpisodeId
                        ?.takeIf { it.isNotBlank() }
                        ?.let { onWatchPreviousEpisode?.invoke(it) }
                },
                onWatchNextEpisode = {
                    nextEpisodeId
                        ?.takeIf { it.isNotBlank() }
                        ?.let { onWatchNextEpisode?.invoke(it) }
                },
                onShowMediaInfo = { showMediaInfo = true },
                onShowStreamingQualityDialog = { showStreamingQualityDialog = true },
                onShowAudioTranscodingDialog = {
                    pendingStreamingQualitySelection = null
                    showAudioTranscodingDialog = true
                },
                onShowAudioTrackDialog = { showAudioTrackDialog = true },
                onShowSubtitleTrackDialog = { showSubtitleTrackDialog = true }
            )

            playerState.error?.let { message ->
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = message,
                        color = Color.White,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            PlayerDialogsHost(
                playerState = playerState,
                showAudioTrackDialog = showAudioTrackDialog,
                showSubtitleTrackDialog = showSubtitleTrackDialog,
                showStreamingQualityDialog = showStreamingQualityDialog,
                showAudioTranscodingDialog = showAudioTranscodingDialog,
                showMediaInfo = showMediaInfo,
                availableStreamingQualityOptions = availableStreamingQualityOptions,
                currentStreamingQuality = currentStreamingQuality,
                currentAudioTranscodeMode = currentAudioTranscodeMode,
                mediaInfoSnapshot = mediaInfoSnapshot,
                onAudioTrackSelected = { trackId ->
                    controller.selectAudioTrack(trackId)
                    showAudioTrackDialog = false
                },
                onSubtitleTrackSelected = { trackId ->
                    controller.selectSubtitleTrack(trackId)
                    showSubtitleTrackDialog = false
                },
                onStreamingQualitySelected = applyStreamingQualitySelection,
                onAudioTranscodingSelected = { selectedMode ->
                    val targetQuality = pendingStreamingQualitySelection ?: currentStreamingQuality
                    applyPlaybackSettingsSelection(targetQuality, selectedMode)
                },
                onDismissAudioTrackDialog = { showAudioTrackDialog = false },
                onDismissSubtitleTrackDialog = { showSubtitleTrackDialog = false },
                onDismissStreamingQualityDialog = { showStreamingQualityDialog = false },
                onDismissAudioTranscodingDialog = {
                    pendingStreamingQualitySelection = null
                    showAudioTranscodingDialog = false
                },
                onDismissMediaInfo = { showMediaInfo = false }
            )
        }
    }
}

@Composable
internal fun BoxScope.PlayerOverlayHost(
    uiState: PlayerUiState,
    playerState: PlayerState,
    currentStreamingQuality: String,
    hasPlaybackSettings: Boolean,
    chapterMarkersEnabled: Boolean,
    seekBackwardSeconds: Int,
    seekForwardSeconds: Int,
    activeSkippableSegment: SkippableSegmentAction?,
    activeCreditsSegment: SkippableSegmentAction?,
    dismissedCreditsPrompt: Boolean,
    canWatchPreviousEpisode: Boolean,
    canWatchNextEpisode: Boolean,
    controller: PlayerController,
    onBackPressed: (() -> Unit)?,
    resetAutoHideTimer: () -> Unit,
    onScrubbingChange: (Boolean) -> Unit,
    onWatchCredits: () -> Unit,
    onWatchPreviousEpisode: () -> Unit,
    onWatchNextEpisode: () -> Unit,
    onShowMediaInfo: () -> Unit,
    onShowStreamingQualityDialog: () -> Unit,
    onShowAudioTranscodingDialog: () -> Unit,
    onShowAudioTrackDialog: () -> Unit,
    onShowSubtitleTrackDialog: () -> Unit
) {
    var nextEpisodeButtonProgress by remember(
        activeCreditsSegment?.startMs,
        activeCreditsSegment?.endMs,
        canWatchNextEpisode,
        dismissedCreditsPrompt
    ) {
        mutableFloatStateOf(0f)
    }

    LaunchedEffect(
        activeCreditsSegment?.startMs,
        activeCreditsSegment?.endMs,
        canWatchNextEpisode,
        dismissedCreditsPrompt
    ) {
        nextEpisodeButtonProgress = 0f

        if (activeCreditsSegment == null || !canWatchNextEpisode || dismissedCreditsPrompt) {
            return@LaunchedEffect
        }

        var elapsedMs = 0L
        while (elapsedMs < NEXT_EPISODE_AUTOPLAY_DELAY) {
            nextEpisodeButtonProgress =
                elapsedMs.toFloat() / NEXT_EPISODE_AUTOPLAY_DELAY.toFloat()
            delay(NEXT_EPISODE_PROGRESS_UPDATE_DELAY)
            elapsedMs += NEXT_EPISODE_PROGRESS_UPDATE_DELAY
        }

        nextEpisodeButtonProgress = 1f
        onWatchNextEpisode()
    }

    if (uiState.controlsVisible) {
        ControlsOverlay(
            title = playerState.mediaTitle,
            mediaLogoUrl = playerState.mediaLogoUrl,
            seasonEpisodeLabel = playerState.seasonEpisodeLabel,
            chapterMarkers = if (chapterMarkersEnabled) playerState.chapterMarkers else emptyList(),
            isPlaying = playerState.playWhenReady,
            currentPosition = uiState.currentPosition,
            bufferedPosition = uiState.bufferedPosition,
            duration = controller.getDuration(),
            onBackClick = {
                controller.releasePlayer()
                onBackPressed?.invoke()
            },
            onPlayPause = {
                resetAutoHideTimer()
                if (playerState.playWhenReady) {
                    controller.pause()
                } else {
                    controller.play()
                }
            },
            onSeek = { progress ->
                resetAutoHideTimer()
                controller.seekToProgress(progress)
            },
            onScrubStateChange = { scrubbing ->
                onScrubbingChange(scrubbing)
                resetAutoHideTimer()
            },
            spatializationResult = playerState.spatializationResult,
            isSpatialAudioEnabled = playerState.isSpatialAudioEnabled,
            isHdrEnabled = playerState.isHdrEnabled,
            hdrFormat = playerState.hdrFormat,
            onShowMediaInfo = {
                resetAutoHideTimer()
                onShowMediaInfo()
            },
            isLocked = playerState.isLocked,
            onToggleLock = {
                resetAutoHideTimer()
                controller.toggleLock()
            },
            currentStreamingQuality = currentStreamingQuality,
            showPlaybackSettingsButton = hasPlaybackSettings,
            onShowPlaybackSettings = {
                resetAutoHideTimer()
                if (playerState.isVideoTranscodingAllowed) {
                    onShowStreamingQualityDialog()
                } else if (playerState.isAudioTranscodingAllowed) {
                    onShowAudioTranscodingDialog()
                }
            },
            onShowAudioTrackSelection = {
                resetAutoHideTimer()
                onShowAudioTrackDialog()
            },
            onShowSubtitleTrackSelection = {
                resetAutoHideTimer()
                onShowSubtitleTrackDialog()
            },
            onCycleAspectRatio = {
                resetAutoHideTimer()
                controller.cycleAspectRatio()
            },
            onSeekBackward = {
                resetAutoHideTimer()
                controller.seekBackward()
            },
            onSeekForward = {
                resetAutoHideTimer()
                controller.seekForward()
            },
            canPlayPreviousEpisode = canWatchPreviousEpisode,
            canPlayNextEpisode = canWatchNextEpisode,
            onPlayPreviousEpisode = {
                resetAutoHideTimer()
                onWatchPreviousEpisode()
            },
            onPlayNextEpisode = {
                resetAutoHideTimer()
                onWatchNextEpisode()
            },
            seekBackwardSeconds = seekBackwardSeconds,
            seekForwardSeconds = seekForwardSeconds,
            modifier = Modifier.fillMaxSize()
        )
    }

    AnimatedVisibility(
        visible = activeSkippableSegment != null &&
            activeSkippableSegment.type != SkippableSegmentType.CREDITS &&
            uiState.controlsVisible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 24.dp, bottom = 28.dp)
    ) {
        FilledTonalButton(
            onClick = {
                resetAutoHideTimer()
                activeSkippableSegment?.seekToMs?.let(controller::seekTo)
            },
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = Color.Black.copy(alpha = 0.52f),
                contentColor = Color.White
            ),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)),
            elevation = ButtonDefaults.filledTonalButtonElevation(
                defaultElevation = 0.dp,
                pressedElevation = 1.dp
            ),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = stringResource(
                    when (activeSkippableSegment?.type) {
                        SkippableSegmentType.RECAP -> Res.string.player_skip_recap
                        SkippableSegmentType.PREVIEW -> Res.string.player_skip_preview
                        else -> Res.string.player_skip_intro
                    }
                ),
                fontSize = 14.sp
            )
        }
    }

    AnimatedVisibility(
        visible = activeCreditsSegment != null &&
            !dismissedCreditsPrompt,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 24.dp, bottom = 28.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    resetAutoHideTimer()
                    onWatchCredits()
                },
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.44f),
                    contentColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = stringResource(Res.string.player_watch_credits),
                    fontSize = 14.sp
                )
            }

            if (canWatchNextEpisode) {
                NextEpisodeProgressPill(
                    label = stringResource(Res.string.player_watch_next_episode),
                    progressFraction = nextEpisodeButtonProgress,
                    onClick = {
                        resetAutoHideTimer()
                        onWatchNextEpisode()
                    }
                )
            }
        }
    }

    GestureIndicators(
        volumeLevel = uiState.volumeLevel,
        brightnessLevel = uiState.brightnessLevel,
        seekPosition = uiState.seekPosition,
        seekSide = uiState.seekSide
    )

    if (playerState.isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Color.White,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
private fun NextEpisodeProgressPill(
    label: String,
    progressFraction: Float,
    onClick: () -> Unit
) {
    val clampedProgress = progressFraction.coerceIn(0f, 1f)
    val pillShape = RoundedCornerShape(999.dp)

    Box(
        modifier = Modifier
            .clip(pillShape)
            .drawBehind {
                drawRect(color = Color.Black.copy(alpha = 0.44f))
                if (clampedProgress > 0f) {
                    drawRect(
                        color = Color.White,
                        size = Size(size.width * clampedProgress, size.height)
                    )
                }
            }
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.12f),
                shape = pillShape
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            modifier = Modifier.drawWithContent {
                val progressEdge = (size.width * clampedProgress).coerceIn(0f, size.width)
                clipRect(left = progressEdge) {
                    this@drawWithContent.drawContent()
                }
            },
            color = Color.White
        )

        Text(
            text = label,
            fontSize = 14.sp,
            modifier = Modifier.drawWithContent {
                val progressEdge = (size.width * clampedProgress).coerceIn(0f, size.width)
                val overlapPx = 1.dp.toPx()
                clipRect(right = (progressEdge + overlapPx).coerceAtMost(size.width)) {
                    this@drawWithContent.drawContent()
                }
            },
            color = Color.Black
        )
    }
}

@Composable
internal fun PlayerDialogsHost(
    playerState: PlayerState,
    showAudioTrackDialog: Boolean,
    showSubtitleTrackDialog: Boolean,
    showStreamingQualityDialog: Boolean,
    showAudioTranscodingDialog: Boolean,
    showMediaInfo: Boolean,
    availableStreamingQualityOptions: List<String>,
    currentStreamingQuality: String,
    currentAudioTranscodeMode: AudioTranscodeMode,
    mediaInfoSnapshot: MediaMetadataInfo?,
    onAudioTrackSelected: (String) -> Unit,
    onSubtitleTrackSelected: (String) -> Unit,
    onStreamingQualitySelected: (String) -> Unit,
    onAudioTranscodingSelected: (AudioTranscodeMode) -> Unit,
    onDismissAudioTrackDialog: () -> Unit,
    onDismissSubtitleTrackDialog: () -> Unit,
    onDismissStreamingQualityDialog: () -> Unit,
    onDismissAudioTranscodingDialog: () -> Unit,
    onDismissMediaInfo: () -> Unit
) {
    AudioTrackSelectionDialog(
        isVisible = showAudioTrackDialog,
        audioTracks = playerState.availableAudioTracks,
        currentAudioTrack = playerState.currentAudioTrack,
        onTrackSelected = onAudioTrackSelected,
        onDismiss = onDismissAudioTrackDialog
    )

    SubtitleTrackSelectionDialog(
        isVisible = showSubtitleTrackDialog,
        subtitleTracks = playerState.availableSubtitleTracks,
        currentSubtitleTrack = playerState.currentSubtitleTrack,
        onTrackSelected = onSubtitleTrackSelected,
        onDismiss = onDismissSubtitleTrackDialog
    )

    StreamingQualitySelectionDialog(
        isVisible = showStreamingQualityDialog && playerState.isVideoTranscodingAllowed,
        qualityOptions = availableStreamingQualityOptions,
        currentQuality = currentStreamingQuality,
        onQualitySelected = onStreamingQualitySelected,
        onDismiss = onDismissStreamingQualityDialog
    )

    AudioTranscodingModeDialog(
        isVisible = showAudioTranscodingDialog && playerState.isAudioTranscodingAllowed,
        currentMode = currentAudioTranscodeMode,
        onModeSelected = onAudioTranscodingSelected,
        onDismiss = onDismissAudioTranscodingDialog
    )

    if (showMediaInfo) {
        mediaInfoSnapshot?.let { mediaInfo ->
            MediaInfoDialog(
                mediaInfo = mediaInfo,
                onDismiss = onDismissMediaInfo
            )
        }
    }
}
