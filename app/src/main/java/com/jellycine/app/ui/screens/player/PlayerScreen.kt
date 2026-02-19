package com.jellycine.app.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
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
import androidx.compose.ui.tooling.preview.Preview
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
import com.jellycine.app.ui.screens.player.PlayerViewModel
import com.jellycine.player.core.PlayerConstants.CONTROLS_AUTO_HIDE_DELAY
import com.jellycine.player.core.PlayerConstants.GESTURE_INDICATOR_HIDE_DELAY
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.app.ui.screens.player.MediaInfoDialog
import kotlinx.coroutines.delay

/**
 * Player state data class to group related states
 */
data class PlayerUiState(
    val controlsVisible: Boolean = true,
    val currentPosition: Long = 0L,
    val isPlaying: Boolean = false,
    val mediaTitle: String = "Loading...",
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
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
    onBackPressed: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val currentView = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Consolidated UI state
    var uiState by remember { mutableStateOf(PlayerUiState()) }
    var lifecycle by remember { mutableStateOf(Lifecycle.Event.ON_CREATE) }
    var autoHideKey by remember { mutableStateOf(0) }

    // Helper function to reset auto-hide timer
    val resetAutoHideTimer = {
        autoHideKey++
    }

    // Dialog states
    var showAudioTrackDialog by remember { mutableStateOf(false) }
    var showSubtitleTrackDialog by remember { mutableStateOf(false) }
    var showMediaInfo by remember { mutableStateOf(false) }

    // Player state from ViewModel
    val playerState by viewModel.playerState.collectAsState()

    // System managers
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val playerPreferences = remember { PlayerPreferences(context) }

    // Store original values to restore on exit
    val originalVolume = remember { audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) }

    // Player-level brightness and volume (persistent)
    var playerBrightness by remember { mutableStateOf(playerPreferences.getPlayerBrightness()) }
    var playerVolume by remember { mutableStateOf(playerPreferences.getPlayerVolume()) }

    // Setup player-specific settings
    DisposableEffect(Unit) {
        currentView.keepScreenOn = true
        val activity = context as? Activity
        val originalRequestedOrientation = activity?.requestedOrientation
        activity?.let { act ->
            act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
            windowInsetsController.apply {
                hide(WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }

        onDispose {
            currentView.keepScreenOn = false
            activity?.let { act ->
                act.requestedOrientation = originalRequestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, originalVolume, 0)
                val layoutParams = act.window.attributes
                layoutParams.screenBrightness = -1f
                act.window.attributes = layoutParams
                val windowInsetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Handle lifecycle events
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            lifecycle = event
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Store initialization state to prevent multiple calls
    var hasInitialized by remember { mutableStateOf(false) }
    
    // Initialize player
    LaunchedEffect(mediaId) {
        if (!hasInitialized) {
            try {
                viewModel.initializePlayer(context, mediaId)
                hasInitialized = true
            } catch (e: Exception) {
                // Initialization failed, will be handled by PlayerViewModel
            }
        }
    }

    // Update position and playing state
    LaunchedEffect(viewModel.exoPlayer) {
        while (true) {
            viewModel.exoPlayer?.let { player ->
                uiState = uiState.copy(
                    currentPosition = player.currentPosition,
                    isPlaying = player.isPlaying
                )
            }
            delay(100)
        }
    }
    // Fetch media title
    LaunchedEffect(mediaId) {
        try {
            val mediaRepository = MediaRepositoryProvider.getInstance(context)
            val result = mediaRepository.getItemById(mediaId)
            val title = if (result.isSuccess) {
                result.getOrNull()?.name ?: "Unknown Title"
            } else {
                "Unknown Title"
            }
            uiState = uiState.copy(mediaTitle = title)
        } catch (e: Exception) {
            uiState = uiState.copy(mediaTitle = "Unknown Title")
        }
    }

    // Initialize player brightness
    LaunchedEffect(Unit) {
        val activity = context as? Activity
        activity?.let { act ->
            val layoutParams = act.window.attributes
            layoutParams.screenBrightness = playerBrightness
            act.window.attributes = layoutParams
        }

        // Set initial volume
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val initialVolume = (playerVolume * maxVolume).toInt().coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, initialVolume, 0)
    }

    // Auto-hide gesture indicators
    LaunchedEffect(uiState.volumeLevel) {
        uiState.volumeLevel?.let {
            delay(CONTROLS_AUTO_HIDE_DELAY)
            uiState = uiState.copy(volumeLevel = null)
        }
    }

    LaunchedEffect(uiState.brightnessLevel) {
        uiState.brightnessLevel?.let {
            delay(CONTROLS_AUTO_HIDE_DELAY)
            uiState = uiState.copy(brightnessLevel = null)
        }
    }

    LaunchedEffect(uiState.seekPosition) {
        uiState.seekPosition?.let {
            delay(GESTURE_INDICATOR_HIDE_DELAY)
            uiState = uiState.copy(seekPosition = null)
        }
    }

    // Auto-hide controls after 3 seconds
    LaunchedEffect(uiState.controlsVisible, autoHideKey) {
        if (uiState.controlsVisible) {
            delay(3000L)
            uiState = uiState.copy(controlsVisible = false)
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
                    playerPreferences.setPlayerVolume(playerVolume)

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
                        playerPreferences.setPlayerBrightness(newPlayerBrightness)

                        val layoutParams = act.window.attributes
                        layoutParams.screenBrightness = newPlayerBrightness
                        act.window.attributes = layoutParams

                        uiState = uiState.copy(brightnessLevel = newPlayerBrightness)
                    }
                }
            },
            onSeek = { delta ->
                if (!playerState.isLocked) {
                    val newPosition = (uiState.currentPosition + delta).coerceIn(0L, viewModel.exoPlayer?.duration ?: 0L)
                    viewModel.exoPlayer?.seekTo(newPosition)

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

        if (uiState.controlsVisible) {
            ControlsOverlay(
                title = uiState.mediaTitle,
                isPlaying = uiState.isPlaying,
                currentPosition = uiState.currentPosition,
                duration = viewModel.exoPlayer?.duration ?: 0L,
                onBackClick = {
                    viewModel.releasePlayer()
                    onBackPressed?.invoke()
                },
                onPlayPause = {
                    resetAutoHideTimer()
                    if (uiState.isPlaying) {
                        viewModel.exoPlayer?.pause()
                    } else {
                        viewModel.exoPlayer?.play()
                    }
                },
                onSeek = { progress ->
                    resetAutoHideTimer()
                    val duration = viewModel.exoPlayer?.duration ?: 0L
                    viewModel.exoPlayer?.seekTo((duration * progress).toLong())
                },
                // Media info parameters
                spatializationResult = playerState.spatializationResult,
                isSpatialAudioEnabled = playerState.isSpatialAudioEnabled,
                isHdrEnabled = playerState.isHdrEnabled,
                onShowMediaInfo = {
                    resetAutoHideTimer()
                    showMediaInfo = true
                },
                // Lock and track selection parameters
                isLocked = playerState.isLocked,
                onToggleLock = {
                    resetAutoHideTimer()
                    viewModel.toggleLock()
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
                // Seek functions
                onSeekBackward = {
                    resetAutoHideTimer()
                    viewModel.seekBackward()
                },
                onSeekForward = {
                    resetAutoHideTimer()
                    viewModel.seekForward()
                },
                onPrevious = {
                    resetAutoHideTimer()
                    viewModel.goToPrevious()
                },
                onNext = {
                    resetAutoHideTimer()
                    viewModel.goToNext()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Gesture indicators
        GestureIndicators(
            volumeLevel = uiState.volumeLevel,
            brightnessLevel = uiState.brightnessLevel,
            seekPosition = uiState.seekPosition,
            seekSide = uiState.seekSide
        )

        // Track selection dialogs
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

        // Loading indicator
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

        // Media Information Dialog
        if (showMediaInfo) {
            MediaInfoDialog(
                mediaInfo = viewModel.getMediaMetadataInfo(),
                onDismiss = { showMediaInfo = false }
            )
        }
    }
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
            onPrevious = { },
            onNext = { },
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
