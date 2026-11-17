package com.jellycine.ui.screens.player

import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.*
import com.jellycine.player.core.PlayerConstants.FULL_SWIPE_RANGE_SCREEN_RATIO
import com.jellycine.player.core.PlayerConstants.GESTURE_EXCLUSION_AREA_HORIZONTAL
import com.jellycine.player.core.PlayerConstants.GESTURE_EXCLUSION_AREA_VERTICAL
import com.jellycine.player.core.PlayerConstants.ZOOM_SCALE_BASE
import com.jellycine.player.core.PlayerConstants.ZOOM_SCALE_THRESHOLD
import com.jellycine.player.preferences.PlayerPreferences
import kotlin.math.abs
import kotlinx.coroutines.*

private const val HORIZONTAL_SEEK_START_PX = 50f
private const val HORIZONTAL_SEEK_MS_PER_PX = 90f

private enum class SwipeMode { None, Progress, Volume, Brightness }

/**
 * Transparent touch layer for the player: tap toggles controls, double tap seeks or toggles
 * playback (left, middle, right fifths), horizontal swipe seeks, vertical swipe changes volume
 * (right half) or brightness (left half), and pinch toggles zoom.
 */
@Composable
fun PlayerGestureSurface(
    preferences: PlayerPreferences,
    getVolume: () -> Float,
    onTap: () -> Unit,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onZoomChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentGetVolume by rememberUpdatedState(getVolume)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnTogglePlayPause by rememberUpdatedState(onTogglePlayPause)
    val currentOnVolumeChange by rememberUpdatedState(onVolumeChange)
    val currentOnBrightnessChange by rememberUpdatedState(onBrightnessChange)
    val currentOnZoomChange by rememberUpdatedState(onZoomChange)

    Box(
        modifier = modifier.pointerInput(preferences) {
            val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
            val touchSlop = viewConfiguration.touchSlop
            var pendingTap: Job? = null
            var lastTapUptime = Long.MIN_VALUE

            coroutineScope {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val first = down.position
                    val gesturesEnabled = preferences.arePlayerGesturesEnabled()
                    val inExclusionArea = isInExclusionArea(first.x, first.y)

                    var mode = SwipeMode.None
                    var moved = false
                    var multiTouch = false
                    var progressDeltaMs = 0L
                    var volumeLevel = 0f
                    var lastUptime = down.uptimeMillis

                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        lastUptime = event.changes.maxOf { it.uptimeMillis }

                        if (pressed.size >= 2) {
                            multiTouch = true
                            if (gesturesEnabled && preferences.isZoomGestureEnabled()) {
                                val zoom = event.calculateZoom()
                                if (abs(zoom - ZOOM_SCALE_BASE) > ZOOM_SCALE_THRESHOLD) {
                                    currentOnZoomChange(zoom > 1f)
                                }
                            }
                            event.changes.forEach { it.consume() }
                            continue
                        }
                        if (multiTouch) continue

                        val change = event.changes.firstOrNull { it.id == down.id } ?: pressed.first()
                        val dx = change.position.x - first.x
                        val dy = change.position.y - first.y
                        if (abs(dx) > touchSlop || abs(dy) > touchSlop) moved = true
                        if (!gesturesEnabled || inExclusionArea) continue

                        val verticalStep = -change.positionChange().y /
                            (size.height * FULL_SWIPE_RANGE_SCREEN_RATIO)

                        when (mode) {
                            SwipeMode.None -> {
                                if (abs(dx) > abs(dy) && abs(dx) > HORIZONTAL_SEEK_START_PX) {
                                    mode = SwipeMode.Progress
                                } else if (abs(dy) > touchSlop && abs(dy) >= abs(dx) &&
                                    preferences.isVolumeBrightnessGesturesEnabled()
                                ) {
                                    mode = if (first.x > size.width / 2f) {
                                        volumeLevel = currentGetVolume()
                                        SwipeMode.Volume
                                    } else {
                                        SwipeMode.Brightness
                                    }
                                }
                            }
                            SwipeMode.Progress -> {
                                progressDeltaMs = (dx * HORIZONTAL_SEEK_MS_PER_PX).toLong()
                                change.consume()
                            }
                            SwipeMode.Volume -> {
                                volumeLevel = (volumeLevel + verticalStep).coerceIn(0f, 1f)
                                currentOnVolumeChange(volumeLevel)
                                change.consume()
                            }
                            SwipeMode.Brightness -> {
                                currentOnBrightnessChange(verticalStep)
                                change.consume()
                            }
                        }
                    }

                    when {
                        mode == SwipeMode.Progress -> {
                            if (progressDeltaMs != 0L) currentOnSeek(progressDeltaMs)
                        }
                        mode == SwipeMode.None && !moved && !multiTouch -> {
                            val isDoubleTap = pendingTap?.isActive == true &&
                                lastUptime - lastTapUptime <= doubleTapTimeout
                            if (isDoubleTap) {
                                pendingTap?.cancel()
                                pendingTap = null
                                if (gesturesEnabled) {
                                    handleDoubleTap(
                                        x = first.x,
                                        width = size.width,
                                        preferences = preferences,
                                        onSeek = currentOnSeek,
                                        onTogglePlayPause = currentOnTogglePlayPause
                                    )
                                }
                            } else {
                                lastTapUptime = lastUptime
                                pendingTap = launch {
                                    delay(doubleTapTimeout)
                                    currentOnTap()
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

private fun PointerInputScope.isInExclusionArea(x: Float, y: Float): Boolean {
    val vertical = GESTURE_EXCLUSION_AREA_VERTICAL * density
    val horizontal = GESTURE_EXCLUSION_AREA_HORIZONTAL * density
    return y < vertical || y > size.height - vertical ||
        x < horizontal || x > size.width - horizontal
}

private fun handleDoubleTap(
    x: Float,
    width: Int,
    preferences: PlayerPreferences,
    onSeek: (Long) -> Unit,
    onTogglePlayPause: () -> Unit
) {
    val fifth = width / 5f
    when {
        x < fifth * 2 -> if (preferences.isProgressSeekGestureEnabled()) {
            onSeek(-preferences.getSeekBackwardIntervalSeconds() * 1000L)
        }
        x < fifth * 3 -> onTogglePlayPause()
        else -> if (preferences.isProgressSeekGestureEnabled()) {
            onSeek(preferences.getSeekForwardIntervalSeconds() * 1000L)
        }
    }
}
