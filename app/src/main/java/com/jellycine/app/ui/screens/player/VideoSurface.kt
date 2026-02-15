package com.jellycine.app.ui.screens.player

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jellycine.app.ui.screens.player.GestureHelper
import androidx.lifecycle.Lifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi

@UnstableApi
@SuppressLint("ClickableViewAccessibility")
@Composable
fun VideoSurface(
    player: ExoPlayer?,
    lifecycle: Lifecycle.Event,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onScaleChange: (Float, Float, Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onSeek: (Long) -> Unit,
    onToggleControls: () -> Unit,
    onZoomChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Animation states for smooth transitions
    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = tween(durationMillis = 200),
        label = "video_scale"
    )
    val animatedOffsetX by animateFloatAsState(
        targetValue = offsetX,
        animationSpec = tween(durationMillis = 200),
        label = "video_offset_x"
    )
    val animatedOffsetY by animateFloatAsState(
        targetValue = offsetY,
        animationSpec = tween(durationMillis = 200),
        label = "video_offset_y"
    )

    // Initialize  gesture helper
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var gestureHelper: GestureHelper? by remember { mutableStateOf(null) }

    Box(
        modifier = modifier.background(Color.Black)
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setKeepContentOnPlayerReset(true)
                    this.resizeMode = resizeMode
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setPadding(0, 0, 0, 0)
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // Smooth video rendering
                    setDefaultArtwork(null)

                    // Set up  gesture handling
                    setOnTouchListener { _, event ->
                        val result = gestureHelper?.handleTouchEvent(event) ?: false
                        result
                    }
                }
            },
            update = { playerView ->
                playerView.player = player
                playerView.resizeMode = resizeMode

                // Initialize gesture helper
                if (gestureHelper == null) {
                    gestureHelper = GestureHelper(
                        context = context,
                        playerView = playerView,
                        audioManager = audioManager,
                        onShowControls = onToggleControls,
                        onHideControls = onToggleControls,
                        onSeek = onSeek,
                        onVolumeChange = onVolumeChange,
                        onBrightnessChange = onBrightnessChange,
                        onZoomChange = onZoomChange
                    )
                }

                when (lifecycle) {
                    Lifecycle.Event.ON_PAUSE -> {
                        playerView.onPause()
                        playerView.player?.pause()
                    }
                    Lifecycle.Event.ON_RESUME -> {
                        playerView.onResume()
                    }
                    else -> Unit
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(0.dp))
                .graphicsLayer(
                    scaleX = animatedScale,
                    scaleY = animatedScale,
                    translationX = animatedOffsetX,
                    translationY = animatedOffsetY,
                    clip = false
                )
        )

    }
}
