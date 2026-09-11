package com.jellycine.app.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import coil3.SingletonImageLoader
import com.jellycine.shared.R
import com.jellycine.data.model.AudioTranscodeMode
import com.jellycine.data.model.BaseItemDto
import com.jellycine.player.core.SkippableSegmentType
import com.jellycine.player.core.findActiveSkippableSegment
import com.jellycine.player.preferences.PlayerPreferences
import kotlinx.coroutines.delay

private const val NEXT_EPISODE_AUTOPLAY_DELAY_MS = 10_000L
private const val NEXT_EPISODE_PROGRESS_UPDATE_MS = 16L
private const val PLAYER_POSITION_UPDATE_MS = 250L

private fun flushImgCache(context: Context) {
    runCatching {
        SingletonImageLoader.get(context).memoryCache?.clear()
    }
}

data class PlayerUiState(
    val controlsVisible: Boolean = true,
    val currentPosition: Long = 0L,
    val bufferedPosition: Long = 0L,
    val isPlaying: Boolean = false
)

@UnstableApi
@Composable
fun PlayerScreen(
    mediaId: String,
    initialItemDetails: BaseItemDto? = null,
    preferredAudioStreamIndex: Int? = null,
    preferredSubtitleStreamIndex: Int? = null,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
    onPreferredStreamIndexesChanged: (Int?, Int?) -> Unit = { _, _ -> },
    onBackPressed: (() -> Unit)? = null,
    onPlaybackCompleted: ((String) -> Unit)? = null,
    previousEpisodeId: String? = null,
    onWatchPreviousEpisode: ((String) -> Unit)? = null,
    nextEpisodeId: String? = null,
    onWatchNextEpisode: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val currentView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var uiState by remember { mutableStateOf(PlayerUiState()) }
    var lifecycle by remember { mutableStateOf(Lifecycle.Event.ON_CREATE) }
    var autoHideKey by remember { mutableStateOf(0) }
    var isScrubbing by remember { mutableStateOf(false) }
    var dismissedCreditsPrompt by remember(mediaId) { mutableStateOf(false) }

    val hideSystemBars = {
        (context as? Activity)?.let { act ->
            val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
            windowInsetsController.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    val resetAutoHideTimer = {
        autoHideKey++
        hideSystemBars()
    }

    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showSubtitleTrackDialog by remember { mutableStateOf(false) }
    var showStreamingQualityDialog by remember { mutableStateOf(false) }
    var showAudioTranscodingDialog by remember { mutableStateOf(false) }
    var pendingStreamingQualitySelection by remember { mutableStateOf<String?>(null) }
    var showMediaInfo by remember { mutableStateOf(false) }
    val mediaInfoSnapshot = remember(showMediaInfo, viewModel) {
        if (showMediaInfo) viewModel.getMediaMetadataInfo() else null
    }

    val playerState by viewModel.playerState.collectAsState()
    val preferredStreamIndexes by viewModel.preferredStreamIndexes.collectAsState()
    val sourceVideoHeight = viewModel.getSourceVideoHeight()
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

    val playerPreferences = remember { PlayerPreferences(context) }
    var currentStreamingQuality by remember { mutableStateOf(playerPreferences.getStreamingQuality()) }
    val skipIntroEnabled = remember { playerPreferences.isSkipIntroEnabled() }
    var currentAudioTranscodeMode by remember {
        mutableStateOf(playerPreferences.getAudioTranscodeMode())
    }
    val seekBackwardSeconds = playerPreferences.getSeekBackwardIntervalSeconds()
    val seekForwardSeconds = playerPreferences.getSeekForwardIntervalSeconds()
    val chapterMarkersEnabled = playerPreferences.areChapterMarkersEnabled()

    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        val activity = context as? Activity
        val originalRequestedOrientation = activity?.requestedOrientation
        activity?.let { act ->
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            hideSystemBars()
        }

        onDispose {
            currentView.keepScreenOn = false
            activity?.let { act ->
                act.requestedOrientation = originalRequestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycle = event
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var initializedMediaId by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(mediaId) {
        if (initializedMediaId == mediaId) return@LaunchedEffect

        try {
            flushImgCache(context)
            if (initializedMediaId != null) {
                viewModel.releasePlayer()
            }
            uiState = uiState.copy(currentPosition = 0L, bufferedPosition = 0L, isPlaying = false)
            viewModel.initializePlayer(
                context = context,
                mediaId = mediaId,
                initialItemDetails = initialItemDetails,
                preferredAudioStreamIndex = preferredAudioStreamIndex,
                preferredSubtitleStreamIndex = preferredSubtitleStreamIndex
            )
            initializedMediaId = mediaId
        } catch (e: Exception) {
        }
    }

    LaunchedEffect(viewModel, onPlaybackCompleted) {
        viewModel.playbackCompletedEvents.collect { completedMediaId ->
            onPlaybackCompleted?.invoke(completedMediaId)
        }
    }

    LaunchedEffect(viewModel.exoPlayer) {
        while (true) {
            val currentPosition = viewModel.getCurrentPosition()
            val bufferedPosition = viewModel.getBufferedPosition()
            val isPlayingNow = viewModel.isPlayingNow()
            if (uiState.currentPosition != currentPosition ||
                uiState.bufferedPosition != bufferedPosition ||
                uiState.isPlaying != isPlayingNow
            ) {
                uiState = uiState.copy(
                    currentPosition = currentPosition,
                    bufferedPosition = bufferedPosition,
                    isPlaying = isPlayingNow
                )
            }
            delay(PLAYER_POSITION_UPDATE_MS)
        }
    }

    LaunchedEffect(
        initializedMediaId,
        preferredStreamIndexes.audioStreamIndex,
        preferredStreamIndexes.subtitleStreamIndex
    ) {
        if (initializedMediaId == mediaId) {
            onPreferredStreamIndexesChanged(
                preferredStreamIndexes.audioStreamIndex,
                preferredStreamIndexes.subtitleStreamIndex
            )
        }
    }

    LaunchedEffect(playerState.currentAudioTranscodeMode) {
        currentAudioTranscodeMode = playerState.currentAudioTranscodeMode
    }

    val hasPlaybackSettings = playerState.isVideoTranscodingAllowed ||
        playerState.isAudioTranscodingAllowed
    val playbackDuration = viewModel.getDuration()
    val activeSkippableSegment = remember(
        skipIntroEnabled,
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
        if (!skipIntroEnabled) {
            null
        } else {
            playerState.findActiveSkippableSegment(
                positionMs = uiState.currentPosition,
                durationMs = playbackDuration
            )
        }
    }
    val activeCreditsSegment = activeSkippableSegment?.takeIf {
        it.type == SkippableSegmentType.CREDITS
    }
    val canWatchPreviousEpisode = !previousEpisodeId.isNullOrBlank() && onWatchPreviousEpisode != null
    val canWatchNextEpisode = !nextEpisodeId.isNullOrBlank() && onWatchNextEpisode != null
    var nextEpisodeButtonProgress by remember(
        activeCreditsSegment?.startMs,
        activeCreditsSegment?.endMs,
        canWatchNextEpisode,
        dismissedCreditsPrompt
    ) {
        mutableFloatStateOf(0f)
    }

    LaunchedEffect(activeCreditsSegment?.startMs, activeCreditsSegment?.endMs) {
        if (activeCreditsSegment == null) {
            dismissedCreditsPrompt = false
        }
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
        while (elapsedMs < NEXT_EPISODE_AUTOPLAY_DELAY_MS) {
            nextEpisodeButtonProgress =
                elapsedMs.toFloat() / NEXT_EPISODE_AUTOPLAY_DELAY_MS.toFloat()
            delay(NEXT_EPISODE_PROGRESS_UPDATE_MS)
            elapsedMs += NEXT_EPISODE_PROGRESS_UPDATE_MS
        }

        nextEpisodeButtonProgress = 1f
        nextEpisodeId
            ?.takeIf { it.isNotBlank() }
            ?.let { onWatchNextEpisode?.invoke(it) }
    }

    LaunchedEffect(
        initializedMediaId,
        nextEpisodeId,
        activeCreditsSegment != null,
        canWatchNextEpisode,
        dismissedCreditsPrompt,
        preferredStreamIndexes.audioStreamIndex,
        preferredStreamIndexes.subtitleStreamIndex
    ) {
        viewModel.updateNextEpisodeCache(
            context = context,
            nextEpisodeId = nextEpisodeId.takeIf {
                initializedMediaId == mediaId &&
                    activeCreditsSegment != null &&
                    canWatchNextEpisode &&
                    !dismissedCreditsPrompt
            },
            preferredAudioStreamIndex = preferredStreamIndexes.audioStreamIndex,
            preferredSubtitleStreamIndex = preferredStreamIndexes.subtitleStreamIndex
        )
    }

    val applyPlaybackSettingsSelection: (String, AudioTranscodeMode) -> Unit = applyPlaybackSettingsSelection@{ quality, audioMode ->
        val selectedQuality = quality.trim()
        val qualityChanged = selectedQuality.isNotEmpty() && selectedQuality != currentStreamingQuality
        val audioModeChanged = audioMode != currentAudioTranscodeMode

        pendingStreamingQualitySelection = null
        showStreamingQualityDialog = false
        showAudioTranscodingDialog = false

        if (selectedQuality.isEmpty()) return@applyPlaybackSettingsSelection

        playerPreferences.setStreamingQuality(selectedQuality)
        currentStreamingQuality = playerPreferences.getStreamingQuality()
        playerPreferences.setAudioTranscodeMode(audioMode)
        currentAudioTranscodeMode = playerPreferences.getAudioTranscodeMode()

        if (!qualityChanged && !audioModeChanged) {
            return@applyPlaybackSettingsSelection
        }

        val resumePositionMs = viewModel.getCurrentPosition()
        val shouldResumePlaying = viewModel.isPlayingNow()
        val preferredAudio = preferredStreamIndexes.audioStreamIndex
        val preferredSubtitle = preferredStreamIndexes.subtitleStreamIndex

        uiState = uiState.copy(controlsVisible = true)
        viewModel.releasePlayer()
        initializedMediaId = null
        viewModel.initializePlayer(
            context = context,
            mediaId = mediaId,
            initialItemDetails = initialItemDetails,
            preferredAudioStreamIndex = preferredAudio,
            preferredSubtitleStreamIndex = preferredSubtitle,
            initialSeekPositionMs = resumePositionMs,
            startPlayback = shouldResumePlaying
        )
        initializedMediaId = mediaId
    }

    val applyStreamingQualitySelection: (String) -> Unit = { selectedQuality ->
        if (!playerState.isVideoTranscodingAllowed) {
            pendingStreamingQualitySelection = null
            showAudioTranscodingDialog = false
            showStreamingQualityDialog = false
        } else {
            val selection = selectedQuality.trim()
            if (selection.isEmpty()) {
                pendingStreamingQualitySelection = null
                showAudioTranscodingDialog = false
                showStreamingQualityDialog = false
            } else {
                val needsAudioPrompt = playerState.isAudioTranscodingAllowed

                if (needsAudioPrompt) {
                    pendingStreamingQualitySelection = selection
                    showStreamingQualityDialog = false
                    showAudioTranscodingDialog = true
                } else {
                    applyPlaybackSettingsSelection(selection, currentAudioTranscodeMode)
                }
            }
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
            hideSystemBars()
        }
    }

    LaunchedEffect(
        playerState.isVideoTranscodingAllowed,
        playerState.isAudioTranscodingAllowed
    ) {
        if (!playerState.isVideoTranscodingAllowed) {
            pendingStreamingQualitySelection = null
            showAudioTranscodingDialog = false
            showStreamingQualityDialog = false
        }
        if (!playerState.isAudioTranscodingAllowed) {
            pendingStreamingQualitySelection = null
            showAudioTranscodingDialog = false
        }
    }

    LaunchedEffect(
        uiState.controlsVisible,
        playerState.hasStartedPlayback,
        autoHideKey,
        isScrubbing
    ) {
        if (uiState.controlsVisible && playerState.hasStartedPlayback && !isScrubbing) {
            delay(3000L)
            uiState = uiState.copy(controlsVisible = false)
        }
    }

    val dialogboxOpen = showAudioTrackDialog || showSubtitleTrackDialog ||
        showStreamingQualityDialog || showAudioTranscodingDialog || showMediaInfo

    BackHandler {
        when {
            dialogboxOpen -> {
                showAudioTrackDialog = false
                showSubtitleTrackDialog = false
                showStreamingQualityDialog = false
                showAudioTranscodingDialog = false
                showMediaInfo = false
            }
            uiState.controlsVisible -> {
                uiState = uiState.copy(controlsVisible = false)
            }
            else -> {
                viewModel.releasePlayer()
                onBackPressed?.invoke()
            }
        }
    }

    val rootFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        rootFocusRequester.requestFocus()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                val dialogOpen = showAudioTrackDialog || showSubtitleTrackDialog ||
                    showStreamingQualityDialog || showAudioTranscodingDialog || showMediaInfo
                if (dialogOpen) return@onPreviewKeyEvent false

                if (!uiState.controlsVisible) {
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            resetAutoHideTimer()
                            uiState = uiState.copy(controlsVisible = true)
                            true
                        }
                        Key.DirectionLeft -> {
                            resetAutoHideTimer()
                            viewModel.seekBackward()
                            uiState = uiState.copy(controlsVisible = true)
                            true
                        }
                        Key.DirectionRight -> {
                            resetAutoHideTimer()
                            viewModel.seekForward()
                            uiState = uiState.copy(controlsVisible = true)
                            true
                        }
                        Key.DirectionUp, Key.DirectionDown -> {
                            resetAutoHideTimer()
                            uiState = uiState.copy(controlsVisible = true)
                            true
                        }
                        Key.Spacebar, Key.MediaPlayPause -> {
                            resetAutoHideTimer()
                            if (playerState.playWhenReady) viewModel.pause() else viewModel.play()
                            true
                        }
                        else -> false
                    }
                } else {
                    when (event.key) {
                        Key.Spacebar, Key.MediaPlayPause -> {
                            resetAutoHideTimer()
                            if (playerState.playWhenReady) viewModel.pause() else viewModel.play()
                            true
                        }
                        Key.DirectionLeft, Key.DirectionRight,
                        Key.DirectionUp, Key.DirectionDown,
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            resetAutoHideTimer()
                            false
                        }
                        else -> false
                    }
                }
            }
            .focusable()
    ) {
        VideoSurface(
            player = viewModel.exoPlayer,
            lifecycle = lifecycle,
            resizeMode = viewModel.getCurrentResizeMode(),
            onToggleControls = {
                resetAutoHideTimer()
                uiState = uiState.copy(controlsVisible = !uiState.controlsVisible)
            },
            modifier = Modifier.fillMaxSize()
        )

        if (uiState.controlsVisible && !showAudioTrackDialog && !showSubtitleTrackDialog &&
            !showStreamingQualityDialog && !showAudioTranscodingDialog && !showMediaInfo
        ) {
            ControlsOverlay(
                title = playerState.mediaTitle,
                mediaLogoUrl = playerState.mediaLogoUrl,
                seasonEpisodeLabel = playerState.seasonEpisodeLabel,
                chapterMarkers = if (chapterMarkersEnabled) playerState.chapterMarkers else emptyList(),
                isPlaying = playerState.playWhenReady,
                currentPosition = uiState.currentPosition,
                bufferedPosition = uiState.bufferedPosition,
                duration = viewModel.getDuration(),
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
                    viewModel.seekToProgress(progress)
                },
                onScrubStateChange = { scrubbing ->
                    isScrubbing = scrubbing
                    resetAutoHideTimer()
                },
                spatializationResult = playerState.spatializationResult,
                isSpatialAudioEnabled = playerState.isSpatialAudioEnabled,
                isHdrEnabled = playerState.isHdrEnabled,
                onShowMediaInfo = {
                    resetAutoHideTimer()
                    showMediaInfo = true
                },
                currentStreamingQuality = currentStreamingQuality,
                showPlaybackSettingsButton = hasPlaybackSettings,
                onShowPlaybackSettings = {
                    resetAutoHideTimer()
                    if (playerState.isVideoTranscodingAllowed) {
                        showStreamingQualityDialog = true
                    } else if (playerState.isAudioTranscodingAllowed) {
                        pendingStreamingQualitySelection = null
                        showAudioTranscodingDialog = true
                    }
                },
                onShowAudioTrackSelection = {
                    resetAutoHideTimer()
                    showAudioTrackDialog = true
                },
                onShowSubtitleTrackSelection = {
                    resetAutoHideTimer()
                    showSubtitleTrackDialog = true
                },
                onCycleAspectRatio = {
                    resetAutoHideTimer()
                    viewModel.cycleAspectRatio()
                },
                onSeekBackward = {
                    resetAutoHideTimer()
                    viewModel.seekBackward()
                },
                onSeekForward = {
                    resetAutoHideTimer()
                    viewModel.seekForward()
                },
                canPlayPreviousEpisode = canWatchPreviousEpisode,
                canPlayNextEpisode = canWatchNextEpisode,
                onPlayPreviousEpisode = {
                    resetAutoHideTimer()
                    previousEpisodeId
                        ?.takeIf { it.isNotBlank() }
                        ?.let { onWatchPreviousEpisode?.invoke(it) }
                },
                onPlayNextEpisode = {
                    resetAutoHideTimer()
                    nextEpisodeId
                        ?.takeIf { it.isNotBlank() }
                        ?.let { onWatchNextEpisode?.invoke(it) }
                },
                seekBackwardSeconds = seekBackwardSeconds,
                seekForwardSeconds = seekForwardSeconds,
                modifier = Modifier.fillMaxSize()
            )
        }

        AnimatedVisibility(
            visible = activeSkippableSegment != null &&
                activeSkippableSegment?.type != SkippableSegmentType.CREDITS &&
                uiState.controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 24.dp, bottom = 28.dp)
        ) {
            FilledTonalButton(
                onClick = {
                    resetAutoHideTimer()
                    activeSkippableSegment?.seekToMs?.let(viewModel::seekTo)
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
                            SkippableSegmentType.RECAP -> R.string.player_skip_recap
                            SkippableSegmentType.PREVIEW -> R.string.player_skip_preview
                            else -> R.string.player_skip_intro
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
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(end = 24.dp, bottom = 28.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        resetAutoHideTimer()
                        dismissedCreditsPrompt = true
                        uiState = uiState.copy(controlsVisible = false)
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
                    val buttonLabel = stringResource(R.string.player_watch_next_episode)
                    val progressFraction = nextEpisodeButtonProgress.coerceIn(0f, 1f)
                    val buttonShape = RoundedCornerShape(999.dp)
                    Box(
                        modifier = Modifier
                            .clip(buttonShape)
                            .drawBehind {
                                drawRect(color = Color.Black.copy(alpha = 0.44f))
                                if (progressFraction > 0f) {
                                    drawRect(
                                        color = Color.White,
                                        size = Size(size.width * progressFraction, size.height)
                                    )
                                }
                            }
                            .clickable {
                                resetAutoHideTimer()
                                nextEpisodeId
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { onWatchNextEpisode?.invoke(it) }
                            }
                            .border(
                                width = 1.dp,
                                color = Color.White.copy(alpha = 0.12f),
                                shape = buttonShape
                            )
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = buttonLabel,
                            fontSize = 14.sp,
                            modifier = Modifier.drawWithContent {
                                val progressEdge = (size.width * progressFraction)
                                    .coerceIn(0f, size.width)
                                clipRect(left = progressEdge) {
                                    this@drawWithContent.drawContent()
                                }
                            },
                            color = Color.White
                        )

                        Text(
                            text = buttonLabel,
                            fontSize = 14.sp,
                            modifier = Modifier.drawWithContent {
                                val progressEdge = (size.width * progressFraction)
                                    .coerceIn(0f, size.width)
                                val overlapPx = 1.dp.toPx()
                                clipRect(right = (progressEdge + overlapPx).coerceAtMost(size.width)) {
                                    this@drawWithContent.drawContent()
                                }
                            },
                            color = Color.Black
                        )
                    }
                }
            }
        }

        AudioTrackSelectionDialog(
            isVisible = showAudioTrackDialog,
            audioTracks = playerState.availableAudioTracks,
            currentAudioTrack = playerState.currentAudioTrack,
            onTrackSelected = { trackId ->
                viewModel.selectAudioTrack(trackId)
                showAudioTrackDialog = false
            },
            onDismiss = { showAudioTrackDialog = false }
        )

        SubtitleTrackSelectionDialog(
            isVisible = showSubtitleTrackDialog,
            subtitleTracks = playerState.availableSubtitleTracks,
            currentSubtitleTrack = playerState.currentSubtitleTrack,
            onTrackSelected = { trackId ->
                viewModel.selectSubtitleTrack(trackId)
                showSubtitleTrackDialog = false
            },
            onDismiss = { showSubtitleTrackDialog = false }
        )

        StreamingQualitySelectionDialog(
            isVisible = showStreamingQualityDialog && playerState.isVideoTranscodingAllowed,
            qualityOptions = availableStreamingQualityOptions,
            currentQuality = currentStreamingQuality,
            onQualitySelected = applyStreamingQualitySelection,
            onDismiss = { showStreamingQualityDialog = false }
        )

        AudioTranscodingModeDialog(
            isVisible = showAudioTranscodingDialog && playerState.isAudioTranscodingAllowed,
            currentMode = currentAudioTranscodeMode,
            onModeSelected = { selectedMode ->
                val targetQuality = pendingStreamingQualitySelection ?: currentStreamingQuality
                applyPlaybackSettingsSelection(targetQuality, selectedMode)
            },
            onDismiss = {
                pendingStreamingQualitySelection = null
                showAudioTranscodingDialog = false
            }
        )

        if (playerState.isLoading && !dialogboxOpen) {
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

        if (showMediaInfo) {
            mediaInfoSnapshot?.let { mediaInfo ->
                MediaInfoDialog(
                    mediaInfo = mediaInfo,
                    onDismiss = { showMediaInfo = false }
                )
            }
        }
    }
}