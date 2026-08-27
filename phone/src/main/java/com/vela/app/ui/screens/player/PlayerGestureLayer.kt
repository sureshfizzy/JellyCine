package com.vela.app.ui.screens.player

import android.annotation.SuppressLint
import android.media.AudioManager
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi

private class GestureLayerCallbacks {
    var enabled: Boolean = true
    var onToggleControls: () -> Unit = {}
    var onSeek: (Long) -> Unit = {}
    var onVolumeChange: (Float) -> Unit = {}
    var onBrightnessChange: (Float) -> Unit = {}
    var getCurrentVolumeLevel: () -> Float = { 0f }
    var getCurrentBrightnessLevel: () -> Float = { 0f }
    var onZoomChange: (Boolean) -> Unit = {}
    var onTogglePlayPause: () -> Unit = {}
    var getPlaybackPosition: () -> Long = { 0L }
    var getPlaybackDuration: () -> Long = { 0L }
    var onSeekPreview: (Long?) -> Unit = {}
    var onHoldSpeed: (Boolean) -> Unit = {}
}

@SuppressLint("ClickableViewAccessibility")
@UnstableApi
@Composable
fun PlayerGestureLayer(
    audioManager: AudioManager,
    enabled: Boolean,
    onToggleControls: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    getCurrentVolumeLevel: () -> Float,
    getCurrentBrightnessLevel: () -> Float,
    onZoomChange: (Boolean) -> Unit,
    onTogglePlayPause: () -> Unit,
    getPlaybackPosition: () -> Long = { 0L },
    getPlaybackDuration: () -> Long = { 0L },
    onSeekPreview: (Long?) -> Unit = {},
    onHoldSpeed: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            val callbacks = GestureLayerCallbacks()
            View(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isClickable = true
                isFocusable = false
                setTag(callbacks)
                val helper = GestureHelper(
                    context = context,
                    touchView = this,
                    audioManager = audioManager,
                    onShowControls = { callbacks.onToggleControls() },
                    onSeek = { callbacks.onSeek(it) },
                    onVolumeChange = { callbacks.onVolumeChange(it) },
                    onBrightnessChange = { callbacks.onBrightnessChange(it) },
                    getCurrentVolumeLevel = { callbacks.getCurrentVolumeLevel() },
                    getCurrentBrightnessLevel = { callbacks.getCurrentBrightnessLevel() },
                    onZoomChange = { callbacks.onZoomChange(it) },
                    onTogglePlayPause = { callbacks.onTogglePlayPause() },
                    getPlaybackPosition = { callbacks.getPlaybackPosition() },
                    getPlaybackDuration = { callbacks.getPlaybackDuration() },
                    onSeekPreview = { callbacks.onSeekPreview(it) },
                    onHoldSpeed = { callbacks.onHoldSpeed(it) }
                )
                setOnTouchListener { _, event ->
                    if (!callbacks.enabled) return@setOnTouchListener false
                    helper.handleTouchEvent(event)
                }
            }
        },
        update = { view ->
            val callbacks = view.tag as GestureLayerCallbacks
            callbacks.enabled = enabled
            callbacks.onToggleControls = onToggleControls
            callbacks.onSeek = onSeek
            callbacks.onVolumeChange = onVolumeChange
            callbacks.onBrightnessChange = onBrightnessChange
            callbacks.getCurrentVolumeLevel = getCurrentVolumeLevel
            callbacks.getCurrentBrightnessLevel = getCurrentBrightnessLevel
            callbacks.onZoomChange = onZoomChange
            callbacks.onTogglePlayPause = onTogglePlayPause
            callbacks.getPlaybackPosition = getPlaybackPosition
            callbacks.getPlaybackDuration = getPlaybackDuration
            callbacks.onSeekPreview = onSeekPreview
            callbacks.onHoldSpeed = onHoldSpeed
        },
        modifier = modifier.fillMaxSize()
    )
}
