package com.jellycine.app.ui.screens.player

import android.view.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import com.jellycine.app.player.mpv.MpvPlayerController

@UnstableApi
@Composable
fun MpvVideoSurface(
    player: MpvPlayerController,
    lifecycle: Lifecycle.Event,
    resizeMode: Int,
    @Suppress("UNUSED_PARAMETER") isHdr: Boolean,
    modifier: Modifier
) {
    AndroidView(
        factory = { context ->
            SurfaceView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
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
