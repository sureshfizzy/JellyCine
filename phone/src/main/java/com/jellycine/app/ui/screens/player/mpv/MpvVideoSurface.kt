package com.jellycine.app.ui.screens.player.mpv

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.media.AudioManager
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.jellycine.app.ui.screens.player.GestureHelper

@SuppressLint("ClickableViewAccessibility")
@UnstableApi
@Composable
fun MpvVideoSurface(
    player: MpvPlayerController,
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
    AndroidView(
        factory = { context ->
            TextureView(context).apply {
                var outputSurface: Surface? = null
                val gestureHelper = GestureHelper(
                    context = context,
                    touchView = this,
                    audioManager = audioManager,
                    onShowControls = onToggleControls,
                    onSeek = onSeek,
                    onVolumeChange = onVolumeChange,
                    onBrightnessChange = onBrightnessChange,
                    getCurrentVolumeLevel = getCurrentVolumeLevel,
                    getCurrentBrightnessLevel = getCurrentBrightnessLevel,
                    onZoomChange = onZoomChange,
                    onTogglePlayPause = onTogglePlayPause
                )
                setOnTouchListener { _, event -> gestureHelper.handleTouchEvent(event) }
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        outputSurface?.release()
                        outputSurface = Surface(surfaceTexture)
                        player.attachSurface(outputSurface ?: return, width, height)
                    }

                    override fun onSurfaceTextureSizeChanged(
                        surfaceTexture: SurfaceTexture,
                        width: Int,
                        height: Int
                    ) {
                        player.resizeSurface(width, height)
                    }

                    override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
                        player.detachSurface()
                        outputSurface?.release()
                        outputSurface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
                }
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