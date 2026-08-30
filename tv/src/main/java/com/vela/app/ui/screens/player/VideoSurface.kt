package com.vela.app.ui.screens.player

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.vela.player.preferences.PlayerPreferences
import androidx.lifecycle.Lifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import androidx.media3.common.util.UnstableApi
import kotlin.math.roundToInt

@UnstableApi
@SuppressLint("ClickableViewAccessibility")
@Composable
fun VideoSurface(
    player: ExoPlayer?,
    lifecycle: Lifecycle.Event,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    onToggleControls: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playerPreferences = remember { PlayerPreferences(context) }

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

                    setDefaultArtwork(null)

                    isFocusable = false
                    isFocusableInTouchMode = false
                    setOnClickListener { onToggleControls() }
                }
            },
            update = { playerView ->
                playerView.player = player
                playerView.resizeMode = resizeMode

                playerView.subtitleView?.apply {
                    val assCompatible = playerPreferences.isSubtitleAssCompatible()
                    setApplyEmbeddedStyles(true)
                    setApplyEmbeddedFontSizes(assCompatible)

                    setFractionalTextSize(
                        PlayerPreferences.exoTextSizeFraction(playerPreferences.getSubtitleScale())
                    )
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
                    if (assCompatible) {
                        setBottomPaddingFraction(SubtitleView.DEFAULT_BOTTOM_PADDING_FRACTION)
                        if (paddingTop != 0) {
                            setPadding(paddingLeft, 0, paddingRight, paddingBottom)
                        }
                    } else {
                        setBottomPaddingFraction(
                            playerPreferences
                                .getSubtitleBottomEdgePositionPercent()
                                .coerceIn(0, PlayerPreferences.MAX_SUBTITLE_EDGE_PERCENT) / 100f
                        )

                        if (playerView.height > 0) {
                            val topPaddingPx = (
                                playerView.height *
                                    (
                                        playerPreferences.getSubtitleTopEdgePositionPercent()
                                            .coerceIn(0, PlayerPreferences.MAX_SUBTITLE_EDGE_PERCENT) / 100f
                                        )
                                ).roundToInt()
                            if (paddingTop != topPaddingPx) {
                                setPadding(paddingLeft, topPaddingPx, paddingRight, paddingBottom)
                            }
                        }
                    }
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
        )

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
