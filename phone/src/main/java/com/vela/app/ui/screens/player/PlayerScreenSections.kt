@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.vela.app.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import coil3.SingletonImageLoader
import com.vela.shared.R
import com.vela.data.model.AudioTranscodeMode
import com.vela.data.model.BaseItemDto
import com.vela.player.core.PlayerConstants.CONTROLS_AUTO_HIDE_DELAY
import com.vela.player.core.PlayerConstants.GESTURE_INDICATOR_HIDE_DELAY
import com.vela.player.core.PlayerConstants.NEXT_EPISODE_AUTOPLAY_DELAY
import com.vela.player.core.PlayerConstants.NEXT_EPISODE_PROGRESS_UPDATE_DELAY
import com.vela.player.core.PlayerState
import com.vela.player.core.SkippableSegmentAction
import com.vela.player.core.SkippableSegmentType
import com.vela.player.preferences.PlayerPreferences
import kotlinx.coroutines.delay

private const val PLAYER_POSITION_UPDATE_ACTIVE_MS = 250L
private const val PLAYER_POSITION_UPDATE_IDLE_MS = 750L

private fun trimImageMemoryCacheForPlayback(context: Context) {
    runCatching {
        SingletonImageLoader.get(context).memoryCache?.clear()
    }
}

@Composable
internal fun PlayerScreenEffects(
    context: Context,
    currentView: View,
    lifecycleOwner: LifecycleOwner,
    mediaId: String,
    initialItemDetails: BaseItemDto?,
    remoteMediaUrl: String?,
    remoteMediaTitle: String?,
    preferredAudioStreamIndex: Int?,
    preferredSubtitleStreamIndex: Int?,
    startFromBeginning: Boolean = false,
    initialSeekPositionMs: Long? = null,
    viewModel: PlayerViewModel,
    onPlaybackCompleted: ((String) -> Unit)?,
    preferredStreamIndexes: PreferredStreamIndexes,
    playerState: PlayerState,
    useDeviceVolumeInPlayer: Boolean,
    audioManager: AudioManager,
    originalVolume: Int,
    playerBrightness: Float,
    playerVolume: Float,
    showAudioTrackDialog: Boolean,
    showSubtitleTrackDialog: Boolean,
    showStreamingQualityDialog: Boolean,
    showAudioTranscodingDialog: Boolean,
    showMediaInfo: Boolean,
    autoHideKey: Int,
    isScrubbing: Boolean,
    hideSystemBars: () -> Unit,
    uiStateProvider: () -> PlayerUiState,
    onUiStateChange: (PlayerUiState) -> Unit,
    initializedMediaIdProvider: () -> String?,
    onInitializedMediaIdChange: (String?) -> Unit,
    onLifecycleChange: (Lifecycle.Event) -> Unit,
    onCurrentAudioTranscodeModeChange: (AudioTranscodeMode) -> Unit,
    onPreferredStreamIndexesChanged: (Int?, Int?) -> Unit,
    playerOrientation: String = PlayerPreferences.DEFAULT_PLAYER_ORIENTATION
) {
    DisposableEffect(playerOrientation) {
        currentView.keepScreenOn = true
        val activity = context as? Activity
        val originalRequestedOrientation = activity?.requestedOrientation
        activity?.let { act ->
            act.requestedOrientation = requestedOrientationFor(playerOrientation)
            act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (playerOrientation != PlayerPreferences.PLAYER_ORIENTATION_PORTRAIT) {
                hideSystemBars()
            }
        }

        onDispose {
            currentView.keepScreenOn = false
            activity?.let { act ->
                act.requestedOrientation =
                    originalRequestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (!useDeviceVolumeInPlayer) {
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
                }
                val layoutParams = act.window.attributes
                layoutParams.screenBrightness = -1f
                act.window.attributes = layoutParams
                WindowCompat.getInsetsController(act.window, act.window.decorView)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            onLifecycleChange(event)
            val activity = context as? Activity
            if (
                event == Lifecycle.Event.ON_STOP &&
                activity?.isInPictureInPictureMode != true &&
                activity?.isChangingConfigurations != true
            ) {
                // ON_PAUSE 也会在进入 PiP 时触发；仅真正退到后台后暂停，并保留用户原有的暂停状态。
                viewModel.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val hdrPassthrough = remember { !PlayerPreferences(context).getMpvHdrToSdrTonemapping() }
    DisposableEffect(viewModel.mpvPlayer, playerState.isHdrEnabled, hdrPassthrough) {
        val activity = context as? Activity
        val shouldUseHdrColorMode = viewModel.mpvPlayer != null && playerState.isHdrEnabled && hdrPassthrough
        val originalColorMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity?.window?.colorMode
        } else {
            null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            if (shouldUseHdrColorMode) {
                activity.window.colorMode = ActivityInfo.COLOR_MODE_HDR
                if (Build.VERSION.SDK_INT >= 34) {
                    activity.window.setDesiredHdrHeadroom(4.0f)
                }
            } else {
                activity.window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
                if (Build.VERSION.SDK_INT >= 34) {
                    activity.window.setDesiredHdrHeadroom(1.0f)
                }
            }
        }

        onDispose {
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                activity != null &&
                originalColorMode != null
            ) {
                activity.window.colorMode = originalColorMode
                if (Build.VERSION.SDK_INT >= 34) {
                    activity.window.setDesiredHdrHeadroom(1.0f)
                }
            }
        }
    }

    val initializationKey = remoteMediaUrl
        ?.takeIf { it.isNotBlank() }
        ?.let { "remote:$it" }
        ?: mediaId

    LaunchedEffect(initializationKey) {
        if (initializedMediaIdProvider() == initializationKey) return@LaunchedEffect

        try {
            trimImageMemoryCacheForPlayback(context)
            if (initializedMediaIdProvider() != null) {
                viewModel.releasePlayer()
            }
            onUiStateChange(uiStateProvider().copy(currentPosition = 0L, isPlaying = false))
            if (!remoteMediaUrl.isNullOrBlank()) {
                viewModel.initializeRemotePlayer(
                    context = context,
                    mediaId = initializationKey,
                    remoteUrl = remoteMediaUrl,
                    title = remoteMediaTitle
                )
            } else {
                viewModel.initializePlayer(
                    context = context,
                    mediaId = mediaId,
                    initialItemDetails = initialItemDetails,
                    preferredAudioStreamIndex = preferredAudioStreamIndex,
                    preferredSubtitleStreamIndex = preferredSubtitleStreamIndex,
                    initialSeekPositionMs = initialSeekPositionMs,
                    startFromBeginning = startFromBeginning
                )
            }
            onInitializedMediaIdChange(initializationKey)
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(viewModel, onPlaybackCompleted) {
        viewModel.playbackCompletedEvents.collect { completedMediaId ->
            onPlaybackCompleted?.invoke(completedMediaId)
        }
    }

    LaunchedEffect(viewModel.exoPlayer, viewModel.mpvPlayer) {
        while (true) {
            val currentPosition = viewModel.getCurrentPosition()
            val bufferedPosition = viewModel.getBufferedPosition()
            val isPlayingNow = viewModel.isPlayingNow()
            val uiState = uiStateProvider()
            if (
                uiState.currentPosition != currentPosition ||
                uiState.bufferedPosition != bufferedPosition ||
                uiState.isPlaying != isPlayingNow
            ) {
                onUiStateChange(
                    uiState.copy(
                        currentPosition = currentPosition,
                        bufferedPosition = bufferedPosition,
                        isPlaying = isPlayingNow
                    )
                )
            }
            // 控件显示时保持进度灵敏；隐藏后降低整屏重组频率，跳过片段判断仍保持亚秒级响应。
            delay(
                if (uiState.controlsVisible || playerState.isLocked) {
                    PLAYER_POSITION_UPDATE_ACTIVE_MS
                } else {
                    PLAYER_POSITION_UPDATE_IDLE_MS
                }
            )
        }
    }

    LaunchedEffect(
        initializedMediaIdProvider(),
        preferredStreamIndexes.audioStreamIndex,
        preferredStreamIndexes.subtitleStreamIndex
    ) {
        if (initializedMediaIdProvider() == initializationKey && remoteMediaUrl.isNullOrBlank()) {
            onPreferredStreamIndexesChanged(
                preferredStreamIndexes.audioStreamIndex,
                preferredStreamIndexes.subtitleStreamIndex
            )
        }
    }

    LaunchedEffect(playerState.currentAudioTranscodeMode) {
        onCurrentAudioTranscodeModeChange(playerState.currentAudioTranscodeMode)
    }

    LaunchedEffect(Unit) {
        val activity = context as? Activity
        activity?.let { act ->
            val layoutParams = act.window.attributes
            layoutParams.screenBrightness = playerBrightness
            act.window.attributes = layoutParams
        }

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val initialVolume = (playerVolume * maxVolume).toInt().coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, initialVolume, 0)
    }

    LaunchedEffect(uiStateProvider().volumeLevel) {
        if (uiStateProvider().volumeLevel != null) {
            delay(CONTROLS_AUTO_HIDE_DELAY)
            onUiStateChange(uiStateProvider().copy(volumeLevel = null))
        }
    }

    LaunchedEffect(uiStateProvider().brightnessLevel) {
        if (uiStateProvider().brightnessLevel != null) {
            delay(CONTROLS_AUTO_HIDE_DELAY)
            onUiStateChange(uiStateProvider().copy(brightnessLevel = null))
        }
    }

    LaunchedEffect(
        uiStateProvider().seekPosition,
        uiStateProvider().seekFeedbackId
    ) {
        if (uiStateProvider().seekPosition != null) {
            delay(GESTURE_INDICATOR_HIDE_DELAY)
            onUiStateChange(uiStateProvider().copy(seekPosition = null))
        }
    }

    LaunchedEffect(
        uiStateProvider().controlsVisible,
        showAudioTrackDialog,
        showSubtitleTrackDialog,
        showStreamingQualityDialog,
        showAudioTranscodingDialog,
        showMediaInfo
    ) {
        if (
            uiStateProvider().controlsVisible ||
            showAudioTrackDialog ||
            showSubtitleTrackDialog ||
            showStreamingQualityDialog ||
            showAudioTranscodingDialog ||
            showMediaInfo
        ) {
            hideSystemBars()
        }
    }

    LaunchedEffect(
        uiStateProvider().controlsVisible,
        playerState.hasStartedPlayback,
        autoHideKey,
        isScrubbing
    ) {
        if (uiStateProvider().controlsVisible && playerState.hasStartedPlayback && !isScrubbing) {
            delay(CONTROLS_AUTO_HIDE_DELAY)
            onUiStateChange(uiStateProvider().copy(controlsVisible = false))
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
    viewModel: PlayerViewModel,
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
    onShowSubtitleTrackDialog: () -> Unit,
    onToggleOrientation: () -> Unit = {},
    onTitleClick: () -> Unit = {},
    onEnterPip: () -> Unit = {},
    onShowChapters: () -> Unit = {},
    onBackgroundClick: () -> Unit = {},
    onSeekFeedback: (String, SeekSide) -> Unit = { _, _ -> },
    onPositionChanged: (Long) -> Unit = {}
): Unit {
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
            duration = viewModel.getDuration(),
            bufferedPosition = uiState.bufferedPosition,
            onBackClick = {
                viewModel.releasePlayer()
                onBackPressed?.invoke()
            },
            onPlayPause = {
                resetAutoHideTimer()
                if (playerState.playWhenReady) {
                    viewModel.pause()
                } else {
                    viewModel.play()
                }
            },
            onSeek = { progress ->
                resetAutoHideTimer()
                viewModel.seekToProgress(progress, exact = true)
                onPositionChanged(viewModel.getCurrentPosition())
            },
            onLiveSeek = { progress ->
                resetAutoHideTimer()
                viewModel.seekToProgress(progress, exact = false)
                onPositionChanged(viewModel.getCurrentPosition())
            },
            onScrubStateChange = { scrubbing ->
                onScrubbingChange(scrubbing)
                resetAutoHideTimer()
            },
            scrubPreviewFrame = viewModel.scrubPreviewFrame,
            onScrubPreviewPositionChange = { positionMs ->
                if (positionMs == null) {
                    viewModel.clearScrubPreview()
                } else {
                    viewModel.requestScrubPreview(positionMs)
                }
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
                viewModel.toggleLock()
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
                viewModel.cycleAspectRatio()
            },
            onToggleOrientation = {
                resetAutoHideTimer()
                onToggleOrientation()
            },
            onTitleClick = {
                resetAutoHideTimer()
                onTitleClick()
            },
            onSeekBackward = {
                resetAutoHideTimer()
                viewModel.seekBackward()
                onPositionChanged(viewModel.getCurrentPosition())
                onSeekFeedback("-${seekBackwardSeconds}s", SeekSide.LEFT)
            },
            onSeekForward = {
                resetAutoHideTimer()
                viewModel.seekForward()
                onPositionChanged(viewModel.getCurrentPosition())
                onSeekFeedback("+${seekForwardSeconds}s", SeekSide.RIGHT)
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
            onEnterPip = {
                resetAutoHideTimer()
                onEnterPip()
            },
            onToggleHardwareDecoding = {
                resetAutoHideTimer()
                viewModel.toggleHardwareDecoding()
            },
            onShowChapters = {
                resetAutoHideTimer()
                onShowChapters()
            },
            onNudgeSpeed = { delta ->
                resetAutoHideTimer()
                viewModel.nudgePlaybackSpeed(delta)
            },
            playbackSpeed = playerState.playbackSpeed,
            hardwareDecodingEnabled = playerState.hardwareDecoding !=
                PlayerPreferences.MPV_HARDWARE_DECODING_NONE,
            onUserInteraction = resetAutoHideTimer,
            onBackgroundClick = onBackgroundClick,
            skipActionLabel = when (activeSkippableSegment?.type) {
                SkippableSegmentType.RECAP -> stringResource(R.string.player_skip_recap)
                SkippableSegmentType.PREVIEW -> stringResource(R.string.player_skip_preview)
                SkippableSegmentType.INTRO -> stringResource(R.string.player_skip_intro)
                else -> null
            },
            onSkipAction = {
                resetAutoHideTimer()
                activeSkippableSegment?.seekToMs?.let(viewModel::seekTo)
            },
            modifier = Modifier.fillMaxSize()
        )
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
                    text = stringResource(R.string.player_watch_credits),
                    fontSize = 14.sp
                )
            }

            if (canWatchNextEpisode) {
                NextEpisodeProgressPill(
                    label = stringResource(R.string.player_watch_next_episode),
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
        seekSide = uiState.seekSide,
        swipeSeekPositionMs = uiState.swipeSeekPositionMs,
        swipeSeekDurationMs = playerState.duration.takeIf { it > 0L } ?: viewModel.getDuration(),
        holdSpeedLabel = uiState.holdSpeedLabel
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

private fun requestedOrientationFor(orientation: String): Int {
    return when (orientation) {
        PlayerPreferences.PLAYER_ORIENTATION_LANDSCAPE ->
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        PlayerPreferences.PLAYER_ORIENTATION_AUTO ->
            ActivityInfo.SCREEN_ORIENTATION_SENSOR
        else -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}
