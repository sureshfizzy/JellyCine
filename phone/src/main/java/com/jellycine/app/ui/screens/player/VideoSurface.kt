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
import com.jellycine.app.ui.screens.player.mpv.MpvVideoSurface
import com.jellycine.app.ui.screens.player.mpv.MpvPlayerController
import kotlin.math.roundToInt

@UnstableApi
@Composable
fun VideoSurface(
    player: ExoPlayer?,
    mpvPlayer: MpvPlayerController? = null,
    lifecycle: Lifecycle.Event,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    getCurrentVolumeLevel: () -> Float,
    getCurrentBrightnessLevel: () -> Float,
    onSeek: (Long) -> Unit,
    onToggleControls: () -> Unit,
    onTogglePlayPause: () -> Unit = {},
    onZoomChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val surfaceModifier = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(0.dp))
        .graphicsLayer(
            scaleX = animatedScale,
            scaleY = animatedScale,
            translationX = animatedOffsetX,
            translationY = animatedOffsetY,
            clip = false
        )

    Box(
        modifier = modifier.background(Color.Black)
    ) {
        if (mpvPlayer != null) {
            MpvVideoSurface(
                player = mpvPlayer,
                lifecycle = lifecycle,
                resizeMode = resizeMode,
                audioManager = audioManager,
                onToggleControls = onToggleControls,
                onSeek = onSeek,
                onVolumeChange = onVolumeChange,
                onBrightnessChange = onBrightnessChange,
                getCurrentVolumeLevel = getCurrentVolumeLevel,
                getCurrentBrightnessLevel = getCurrentBrightnessLevel,
                onZoomChange = onZoomChange,
                onTogglePlayPause = onTogglePlayPause,
                modifier = surfaceModifier
            )
        } else {
            ExoPlayerView(
                player = player,
                lifecycle = lifecycle,
                resizeMode = resizeMode,
                audioManager = audioManager,
                onToggleControls = onToggleControls,
                onSeek = onSeek,
                onVolumeChange = onVolumeChange,
                onBrightnessChange = onBrightnessChange,
                getCurrentVolumeLevel = getCurrentVolumeLevel,
                getCurrentBrightnessLevel = getCurrentBrightnessLevel,
                onZoomChange = onZoomChange,
                onTogglePlayPause = onTogglePlayPause,
                modifier = surfaceModifier
            )
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
@UnstableApi
@Composable
private fun ExoPlayerView(
    player: ExoPlayer?,
    lifecycle: Lifecycle.Event,
    resizeMode: Int,
    audioManager: AudioManager,
    onToggleControls: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    getCurrentVolumeLevel: () -> Float,
    getCurrentBrightnessLevel: () -> Float,
    onZoomChange: (Boolean) -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val playerPreferences = remember { PlayerPreferences(context) }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
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
                setDefaultArtwork(null)

                val helper = GestureHelper(
                    context = viewContext,
                    touchView = this,
                    audioManager = audioManager,
                    onShowControls = onToggleControls,
                    onSeek = onSeek,
                    onVolumeChange = onVolumeChange,
                    onBrightnessChange = onBrightnessChange,
                    getCurrentVolumeLevel = getCurrentVolumeLevel,
                    getCurrentBrightnessLevel = getCurrentBrightnessLevel,
                    onZoomChange = onZoomChange,
                    onTogglePlayPause = onTogglePlayPause,
                    getPlayer = { this.player }
                )
                setOnTouchListener { _, event -> helper.handleTouchEvent(event) }
            }
        },
        update = { playerView ->
            playerView.player = player
            playerView.resizeMode = resizeMode
            playerView.applySubtitlePreferences(playerPreferences)

            when (lifecycle) {
                Lifecycle.Event.ON_PAUSE -> {
                    playerView.onPause()
                    playerView.player?.pause()
                }
                Lifecycle.Event.ON_RESUME -> playerView.onResume()
                else -> Unit
            }
        },
        modifier = modifier
    )
}

@UnstableApi
private fun PlayerView.applySubtitlePreferences(playerPreferences: PlayerPreferences) {
    subtitleView?.apply {
        setApplyEmbeddedStyles(true)
        setApplyEmbeddedFontSizes(false)
        setFractionalTextSize(subtitleTextSizeFraction(playerPreferences.getSubtitleTextSize()))
        setStyle(
            CaptionStyleCompat(
                subtitleTextColorArgb(
                    playerPreferences.getSubtitleTextColor(),
                    playerPreferences.getSubtitleTextOpacityPercent()
                ),
                subtitleBackgroundColorArgb(playerPreferences.getSubtitleBackgroundColor()),
                android.graphics.Color.TRANSPARENT,
                subtitleEdgeType(playerPreferences.getSubtitleEdgeType()),
                subtitleEdgeColor(playerPreferences.getSubtitleEdgeType()),
                null
            )
        )
        setBottomPaddingFraction(
            playerPreferences.getSubtitlePosition().coerceIn(0, 50) / 100f
        )

        if (height > 0) {
            val topPaddingPx = (
                height * (playerPreferences.getSubtitleTopEdgePositionPercent().coerceIn(0, 50) / 100f)
                ).roundToInt()
            if (paddingTop != topPaddingPx) {
                setPadding(paddingLeft, topPaddingPx, paddingRight, paddingBottom)
            }
        }
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

private fun subtitleTextColorArgb(color: String, opacityPercent: Int): Int {
    val baseColor = when (color) {
        PlayerPreferences.SUBTITLE_TEXT_COLOR_YELLOW -> android.graphics.Color.YELLOW
        PlayerPreferences.SUBTITLE_TEXT_COLOR_GREEN -> android.graphics.Color.GREEN
        PlayerPreferences.SUBTITLE_TEXT_COLOR_CYAN -> android.graphics.Color.CYAN
        PlayerPreferences.SUBTITLE_TEXT_COLOR_BLACK -> android.graphics.Color.BLACK
        else -> android.graphics.Color.WHITE
    }
    return applyAlphaToColor(baseColor, opacityPercent)
}

private fun subtitleBackgroundColorArgb(color: String): Int {
    return when (color) {
        PlayerPreferences.SUBTITLE_BACKGROUND_BLACK -> android.graphics.Color.BLACK
        PlayerPreferences.SUBTITLE_BACKGROUND_WHITE -> android.graphics.Color.WHITE
        else -> android.graphics.Color.TRANSPARENT
    }
}

private fun subtitleEdgeType(edgeType: String): Int {
    return when (edgeType) {
        PlayerPreferences.SUBTITLE_EDGE_TYPE_OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
        PlayerPreferences.SUBTITLE_EDGE_TYPE_DROP_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
        PlayerPreferences.SUBTITLE_EDGE_TYPE_RAISED -> CaptionStyleCompat.EDGE_TYPE_RAISED
        PlayerPreferences.SUBTITLE_EDGE_TYPE_DEPRESSED -> CaptionStyleCompat.EDGE_TYPE_DEPRESSED
        else -> CaptionStyleCompat.EDGE_TYPE_NONE
    }
}

private fun subtitleEdgeColor(edgeType: String): Int {
    return if (edgeType == PlayerPreferences.SUBTITLE_EDGE_TYPE_NONE) {
        android.graphics.Color.TRANSPARENT
    } else {
        android.graphics.Color.BLACK
    }
}

private fun applyAlphaToColor(color: Int, opacityPercent: Int): Int {
    val alpha = ((opacityPercent.coerceIn(0, 100) / 100f) * 255f).roundToInt().coerceIn(0, 255)
    return android.graphics.Color.argb(
        alpha,
        android.graphics.Color.red(color),
        android.graphics.Color.green(color),
        android.graphics.Color.blue(color)
    )
}