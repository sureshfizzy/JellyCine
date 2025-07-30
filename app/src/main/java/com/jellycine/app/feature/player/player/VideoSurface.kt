package com.jellycine.app.feature.player.player

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

@SuppressLint("ClickableViewAccessibility")
@Composable
fun VideoSurface(
    player: ExoPlayer?,
    lifecycle: Lifecycle.Event,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onScaleChange: (Float, Float, Float) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onSeek: (Long) -> Unit,
    onToggleControls: () -> Unit,
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
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setPadding(0, 0, 0, 0)
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // Smooth video rendering
                    setUseArtwork(false)
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
                        onZoomChange = { /* Handle zoom change */ }
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

        // Smooth loading indicator
        if (player?.isLoading == true) {
            LoadingIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
private fun LoadingIndicator(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(80.dp)
            .background(
                Color.Black.copy(alpha = 0.8f),
                RoundedCornerShape(40.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = Color.White,
            strokeWidth = 3.dp,
            modifier = Modifier.size(40.dp)
        )
    }
}
