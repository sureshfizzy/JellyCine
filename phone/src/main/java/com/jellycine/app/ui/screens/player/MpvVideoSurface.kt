package com.jellycine.app.ui.screens.player

import android.annotation.SuppressLint
import android.media.AudioManager
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.jellycine.app.player.mpv.MpvPlayerController

@SuppressLint("ClickableViewAccessibility")
@UnstableApi
@Composable
fun MpvVideoSurface(
    player: MpvPlayerController,
    lifecycle: Lifecycle.Event,
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
    modifier: Modifier
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // Gestures are handled by PlayerGestureLayer so portrait letterbox swipes work.
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        val frame = holder.surfaceFrame
                        player.attachSurface(
                            surface = holder.surface,
                            width = frame.width(),
                            height = frame.height()
                        )
                    }

                    override fun surfaceChanged(
                        holder: SurfaceHolder,
                        format: Int,
                        width: Int,
                        height: Int
                    ) {
                        player.resizeSurface(width, height)
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        player.detachSurface()
                    }
                })
            }
        },
        update = {
            player.applySubtitlePreferences()
            player.setZoomMode(resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
            if (lifecycle == Lifecycle.Event.ON_PAUSE) {
                player.pause()
            }
        },
        modifier = modifier
    )
}
