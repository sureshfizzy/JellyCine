package com.jellycine.app.ui.screens.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.util.UnstableApi
import com.jellycine.app.R
import com.jellycine.app.ui.screens.player.PlayerViewModel
import com.jellycine.data.model.AudioTranscodeMode
import com.jellycine.data.model.BaseItemDto
import com.jellycine.player.core.SkippableSegmentType
import com.jellycine.player.core.findActiveSkippableSegment
import com.jellycine.player.preferences.PlayerPreferences

/**
 * Player state data class to group related states
 */
data class PlayerUiState(
    val controlsVisible: Boolean = true,
    val currentPosition: Long = 0L,
    val isPlaying: Boolean = false,
    val volumeLevel: Float? = null,
    val brightnessLevel: Float? = null,
    val seekPosition: String? = null,
    val seekSide: SeekSide = SeekSide.CENTER,
    val videoScale: Float = 1f,
    val videoOffsetX: Float = 0f,
    val videoOffsetY: Float = 0f
)

/**
 * Player Screen with proper immersive mode and gestures
 */
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
    nextEpisodeId: String? = null,
    onWatchNextEpisode: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val currentView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Consolidated UI state
    var uiState by remember { mutableStateOf(PlayerUiState()) }
    var lifecycle by remember { mutableStateOf(Lifecycle.Event.ON_CREATE) }
    var autoHideKey by remember { mutableStateOf(0) }
    var isScrubbing by remember { mutableStateOf(false) }
    var dismissedCreditsPrompt by remember(mediaId) { mutableStateOf(false) }

    val hideSystemBars: () -> Unit = {
        (context as? Activity)?.let { act ->
            val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
            windowInsetsController?.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        Unit
    }

    // Helper function to reset auto-hide timer
    val resetAutoHideTimer = {
        autoHideKey++
        hideSystemBars()
    }

    // Dialog states
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showSubtitleTrackDialog by remember { mutableStateOf(false) }
    var showStreamingQualityDialog by remember { mutableStateOf(false) }
    var showAudioTranscodingDialog by remember { mutableStateOf(false) }
    var pendingStreamingQualitySelection by remember { mutableStateOf<String?>(null) }
    var showMediaInfo by remember { mutableStateOf(false) }
    val mediaInfoSnapshot = remember(showMediaInfo, viewModel) {
        if (showMediaInfo) viewModel.getMediaMetadataInfo() else null
    }

    // Player state from ViewModel
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

    // System managers
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val playerPreferences = remember { PlayerPreferences(context) }
    val useDeviceVolumeInPlayer = remember { playerPreferences.isUseDeviceVolumeInPlayerEnabled() }
    val useDeviceBrightnessInPlayer = remember { playerPreferences.isUseDeviceBrightnessInPlayerEnabled() }

    // Store original values to restore on exit
    val originalVolume = remember { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) }

    // Player-level brightness and volume (persistent)
    var playerBrightness by remember(useDeviceBrightnessInPlayer) {
        mutableStateOf(
            if (useDeviceBrightnessInPlayer) {
                readCurrentDeviceBrightness(context)
            } else {
                playerPreferences.getPlayerBrightness()
            }
        )
    }
    var playerVolume by remember(useDeviceVolumeInPlayer) {
        mutableStateOf(
            if (useDeviceVolumeInPlayer) {
                readCurrentDeviceVolume(audioManager)
            } else {
                playerPreferences.getPlayerVolume()
            }
        )
    }
    var currentStreamingQuality by remember { mutableStateOf(playerPreferences.getStreamingQuality()) }
    val skipIntroEnabled = remember { playerPreferences.isSkipIntroEnabled() }
    var currentAudioTranscodeMode by remember {
        mutableStateOf(playerPreferences.getAudioTranscodeMode())
    }
    val seekBackwardSeconds = playerPreferences.getSeekBackwardIntervalSeconds()
    val seekForwardSeconds = playerPreferences.getSeekForwardIntervalSeconds()
    val chapterMarkersEnabled = playerPreferences.areChapterMarkersEnabled()

    // Track initialized media so this screen can switch to a new episode in-place.
    var initializedMediaId by remember { mutableStateOf<String?>(null) }

    PlayerScreenEffects(
        context = context,
        currentView = currentView,
        lifecycleOwner = lifecycleOwner,
        mediaId = mediaId,
        initialItemDetails = initialItemDetails,
        preferredAudioStreamIndex = preferredAudioStreamIndex,
        preferredSubtitleStreamIndex = preferredSubtitleStreamIndex,
        viewModel = viewModel,
        onPlaybackCompleted = onPlaybackCompleted,
        preferredStreamIndexes = preferredStreamIndexes,
        playerState = playerState,
        useDeviceVolumeInPlayer = useDeviceVolumeInPlayer,
        audioManager = audioManager,
        originalVolume = originalVolume,
        playerBrightness = playerBrightness,
        playerVolume = playerVolume,
        showAudioTrackDialog = showAudioTrackDialog,
        showSubtitleTrackDialog = showSubtitleTrackDialog,
        showStreamingQualityDialog = showStreamingQualityDialog,
        showAudioTranscodingDialog = showAudioTranscodingDialog,
        showMediaInfo = showMediaInfo,
        autoHideKey = autoHideKey,
        isScrubbing = isScrubbing,
        hideSystemBars = hideSystemBars,
        uiStateProvider = { uiState },
        onUiStateChange = { uiState = it },
        initializedMediaIdProvider = { initializedMediaId },
        onInitializedMediaIdChange = { initializedMediaId = it },
        onLifecycleChange = { lifecycle = it },
        onCurrentAudioTranscodeModeChange = { currentAudioTranscodeMode = it },
        onPreferredStreamIndexesChanged = onPreferredStreamIndexesChanged
    )

    val hasPlaybackSettings = playerState.isVideoTranscodingAllowed ||
        playerState.isAudioTranscodingAllowed
    val playbackDuration = viewModel.getDuration()
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
    val activeCreditsSegment = activeSkippableSegment?.takeIf {
        it.type == SkippableSegmentType.CREDITS
    }
    val canWatchNextEpisode = !nextEpisodeId.isNullOrBlank() && onWatchNextEpisode != null

    LaunchedEffect(activeCreditsSegment?.startMs, activeCreditsSegment?.endMs) {
        if (activeCreditsSegment == null) {
            dismissedCreditsPrompt = false
        }
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

    // Back handler
    BackHandler {
        viewModel.releasePlayer()
        onBackPressed?.invoke()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusable()
    ) {
        VideoSurface(
            player = viewModel.exoPlayer,
            lifecycle = lifecycle,
            scale = playerState.videoScale,
            offsetX = playerState.videoOffsetX,
            offsetY = playerState.videoOffsetY,
            resizeMode = viewModel.getCurrentResizeMode(),
            onScaleChange = { scale, offsetX, offsetY ->
                if (!playerState.isLocked) {
                    // Update ViewModel state directly to avoid conflicts with start maximized setting
                    viewModel.updateVideoTransform(scale, offsetX, offsetY)
                }
            },
            onVolumeChange = { level ->
                if (!playerState.isLocked) {
                    playerVolume = level.coerceIn(0f, 1f)
                    if (!useDeviceVolumeInPlayer) {
                        playerPreferences.setPlayerVolume(playerVolume)
                    }

                    // Apply volume to system
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val newVolume = (playerVolume * maxVolume).toInt().coerceIn(0, maxVolume)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)

                    uiState = uiState.copy(volumeLevel = playerVolume)
                }
            },
            onBrightnessChange = { delta ->
                if (!playerState.isLocked) {
                    val activity = context as? Activity
                    activity?.let { act ->
                        val newPlayerBrightness = (playerBrightness + delta).coerceIn(0.01f, 1f)
                        playerBrightness = newPlayerBrightness
                        if (!useDeviceBrightnessInPlayer) {
                            playerPreferences.setPlayerBrightness(newPlayerBrightness)
                        }

                        val layoutParams = act.window.attributes
                        layoutParams.screenBrightness = newPlayerBrightness
                        act.window.attributes = layoutParams

                        uiState = uiState.copy(brightnessLevel = newPlayerBrightness)
                    }
                }
            },
            getCurrentVolumeLevel = { playerVolume },
            getCurrentBrightnessLevel = { playerBrightness },
            onSeek = { delta ->
                if (!playerState.isLocked) {
                    viewModel.seekBy(delta)

                    // Show seek indicator
                    val isForward = delta > 0
                    val seconds = kotlin.math.abs(delta) / 1000
                    val seekText = if (isForward) "+${seconds}s" else "-${seconds}s"
                    val side = if (isForward) SeekSide.RIGHT else SeekSide.LEFT

                    uiState = uiState.copy(seekPosition = seekText, seekSide = side)
                }
            },
            onToggleControls = {
                resetAutoHideTimer()
                uiState = uiState.copy(controlsVisible = !uiState.controlsVisible)
            },
            onZoomChange = { isZooming ->
                viewModel.handlePinchZoom(isZooming)
            },
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
            canWatchNextEpisode = canWatchNextEpisode,
            viewModel = viewModel,
            onBackPressed = onBackPressed,
            resetAutoHideTimer = resetAutoHideTimer,
            onScrubbingChange = { isScrubbing = it },
            onWatchCredits = {
                dismissedCreditsPrompt = true
                uiState = uiState.copy(controlsVisible = false)
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
                viewModel.selectAudioTrack(trackId)
                showAudioTrackDialog = false
            },
            onSubtitleTrackSelected = { trackId ->
                viewModel.selectSubtitleTrack(trackId)
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

private fun readCurrentDeviceVolume(audioManager: AudioManager): Float {
    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    if (maxVolume <= 0) return 0f
    return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume.toFloat()
}

private fun readCurrentDeviceBrightness(context: Context): Float {
    return runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            .toFloat()
            .div(255f)
            .coerceIn(0.01f, 1f)
    }.getOrDefault(PlayerPreferences(context).getPlayerBrightness())
}

@Composable
fun SpatialAudioInfoDialog(
    spatialInfo: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Spatial Audio Status",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        text = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = spatialInfo,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("OK")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun HdrFormatInfoDialog(
    hdrInfo: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HDR Format & Fallback Status",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        text = {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = hdrInfo,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss
            ) {
                Text("OK")
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(16.dp)
    )
}

@Preview(
    name = "Player Screen - Controls Visible",
    showBackground = true,
    widthDp = 800,
    heightDp = 450
)
@Composable
fun PlayerScreenPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Mock video surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text(
                text = "Video Content",
                color = Color.White.copy(alpha = 0.3f),
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
            )
        }

        // Show controls overlay
        ControlsOverlay(
            title = "Sample Movie Title",
            chapterMarkers = emptyList(),
            isPlaying = true,
            currentPosition = 45000L, // 45 seconds
            duration = 7200000L, // 2 hours
            onBackClick = { },
            onPlayPause = { },
            onSeek = { },
            isLocked = false,
            onToggleLock = { },
            onShowAudioTrackSelection = { },
            onShowSubtitleTrackSelection = { },
            onCycleAspectRatio = { },
            onSeekBackward = { },
            onSeekForward = { },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(
    name = "Player Screen - Gesture Indicators",
    showBackground = true,
    widthDp = 800,
    heightDp = 450
)
@Composable
fun PlayerScreenGesturePreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Mock video surface
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Video Surface",
                color = Color.White,
                fontSize = 24.sp
            )
        }

        // Gesture indicators preview
        GestureIndicators(
            volumeLevel = 0.7f, // 70% volume
            brightnessLevel = 0.5f, // 50% brightness
            seekPosition = "+10s"
        )

        // Loading indicator preview
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

@Preview(
    name = "Player Screen - Controls Hidden",
    showBackground = true,
    widthDp = 800,
    heightDp = 450
)
@Composable
fun PlayerScreenPreviewHidden() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text(
                text = "Video Content",
                color = Color.White.copy(alpha = 0.3f),
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium
            )
        }
    }
}
