package com.vela.app.ui.screens.player

import android.annotation.SuppressLint
import android.graphics.PixelFormat
import android.media.AudioManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.vela.app.player.mpv.MpvPlayerController

@SuppressLint("ClickableViewAccessibility")
@UnstableApi
@Composable
fun MpvVideoSurface(
    player: MpvPlayerController,
    resizeMode: Int,
    audioManager: AudioManager,
    @Suppress("UNUSED_PARAMETER") isHdr: Boolean,
    onToggleControls: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    getCurrentVolumeLevel: () -> Float,
    getCurrentBrightnessLevel: () -> Float,
    onZoomChange: (Boolean) -> Unit,
    onTogglePlayPause: () -> Unit,
    onSurfaceReady: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") subtitleAppearanceEpoch: Int = 0,
    modifier: Modifier
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                holder.setFormat(PixelFormat.RGBA_8888)
                // Gestures are handled by PlayerGestureLayer so portrait letterbox swipes work.
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        val frame = holder.surfaceFrame
                        player.attachSurface(
                            surface = holder.surface,
                            width = frame.width(),
                            height = frame.height()
                        )
                        if (frame.width() > 0 && frame.height() > 0) {
                            post { onSurfaceReady() }
                        }
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        player.resizeSurface(width, height)
                        if (width > 0 && height > 0) {
                            post { onSurfaceReady() }
                        }
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        player.detachSurface()
                    }
                })
            }
        },
        update = { view ->
            player.applySubtitlePreferences()
            player.setZoomMode(resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
            if (view.width > 0 && view.height > 0) {
                player.resizeSurface(view.width, view.height)
            }
        },
        modifier = modifier
    )
}
