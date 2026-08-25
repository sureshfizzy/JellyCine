package com.jellycine.app.ui.screens.player

import android.annotation.SuppressLint
import android.media.AudioManager
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi

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
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { context ->
            View(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                isClickable = true
                isFocusable = false
                val helper = GestureHelper(
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
                setOnTouchListener { _, event ->
                    if (!enabled) return@setOnTouchListener false
                    helper.handleTouchEvent(event)
                }
            }
        },
        modifier = modifier.fillMaxSize()
    )
}
