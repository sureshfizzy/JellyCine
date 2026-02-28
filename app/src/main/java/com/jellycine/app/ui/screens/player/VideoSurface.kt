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
import com.jellycine.player.preferences.PlayerPreferences
import androidx.lifecycle.Lifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi
import kotlin.math.roundToInt

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

    // Initialize gesture helper
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val playerPreferences = remember { PlayerPreferences(context) }
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

                playerView.subtitleView?.apply {
                    setApplyEmbeddedStyles(false)
                    setApplyEmbeddedFontSizes(false)

                    setFractionalTextSize(
                        subtitleTextSizeFraction(playerPreferences.getSubtitleTextSize())
                    )
                    setStyle(
                        CaptionStyleCompat(
                            subtitleTextColorArgb(playerPreferences.getSubtitleTextColor()),
                            subtitleBackgroundColorArgb(playerPreferences.getSubtitleBackgroundColor()),
                            android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                    setBottomPaddingFraction(
                        playerPreferences
                            .getSubtitleBottomEdgePositionPercent()
                            .coerceIn(0, 50) / 100f
                    )

                    if (playerView.height > 0) {
                        val topPaddingPx = (
                            playerView.height *
                                (playerPreferences.getSubtitleTopEdgePositionPercent().coerceIn(0, 50) / 100f)
                            ).roundToInt()
                        if (paddingTop != topPaddingPx) {
                            setPadding(paddingLeft, topPaddingPx, paddingRight, paddingBottom)
                        }
                    }
                }

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

private fun subtitleTextSizeFraction(size: String): Float {
    return when (size) {
        PlayerPreferences.SUBTITLE_TEXT_SIZE_SMALL -> 0.04f
        PlayerPreferences.SUBTITLE_TEXT_SIZE_LARGE -> 0.065f
        PlayerPreferences.SUBTITLE_TEXT_SIZE_EXTRA_LARGE -> 0.08f
        else -> 0.0533f // Normal
    }
}

private fun subtitleTextColorArgb(color: String): Int {
    return when (color) {
        PlayerPreferences.SUBTITLE_TEXT_COLOR_YELLOW -> android.graphics.Color.YELLOW
        PlayerPreferences.SUBTITLE_TEXT_COLOR_GREEN -> android.graphics.Color.GREEN
        PlayerPreferences.SUBTITLE_TEXT_COLOR_CYAN -> android.graphics.Color.CYAN
        PlayerPreferences.SUBTITLE_TEXT_COLOR_BLACK -> android.graphics.Color.BLACK
        else -> android.graphics.Color.WHITE
    }
}

private fun subtitleBackgroundColorArgb(color: String): Int {
    return when (color) {
        PlayerPreferences.SUBTITLE_BACKGROUND_BLACK -> android.graphics.Color.BLACK
        PlayerPreferences.SUBTITLE_BACKGROUND_WHITE -> android.graphics.Color.WHITE
        else -> android.graphics.Color.TRANSPARENT
    }
}
