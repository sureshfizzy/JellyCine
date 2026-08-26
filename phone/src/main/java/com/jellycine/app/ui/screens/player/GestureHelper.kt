package com.jellycine.app.ui.screens.player

import android.content.Context
import android.content.res.Resources
import android.media.AudioManager
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.media3.common.Player
import kotlin.math.abs

import com.jellycine.player.core.PlayerConstants.GESTURE_EXCLUSION_AREA_HORIZONTAL
import com.jellycine.player.core.PlayerConstants.GESTURE_EXCLUSION_AREA_VERTICAL
import com.jellycine.player.core.PlayerConstants.FULL_SWIPE_RANGE_SCREEN_RATIO
import com.jellycine.player.core.PlayerConstants.ZOOM_SCALE_BASE
import com.jellycine.player.core.PlayerConstants.ZOOM_SCALE_THRESHOLD
import com.jellycine.player.preferences.PlayerPreferences

class GestureHelper(
    private val context: Context,
    private val touchView: View,
    private val audioManager: AudioManager,
    private val onShowControls: () -> Unit,
    private val onSeek: (Long) -> Unit,
    private val onVolumeChange: (Float) -> Unit,
    private val onBrightnessChange: (Float) -> Unit,
    private val getCurrentVolumeLevel: () -> Float,
    private val getCurrentBrightnessLevel: () -> Float,
    private val onZoomChange: (Boolean) -> Unit,
    private val onTogglePlayPause: () -> Unit = {},
    private val getPlayer: () -> Player? = { null },
    private val getPlaybackPosition: () -> Long = { 0L },
    private val getPlaybackDuration: () -> Long = { 0L },
    private val onSeekPreview: (Long?) -> Unit = {},
    private val onHoldSpeed: (Boolean) -> Unit = {}
) {
    private val playerPreferences = PlayerPreferences(context)
    // Gesture state tracking
    private var swipeGestureValueTrackerVolume = -1f
    private var swipeGestureValueTrackerBrightness = -1f
    private var swipeGestureValueTrackerProgress = 0L
    private var swipeGestureVolumeOpen = false
    private var swipeGestureBrightnessOpen = false
    private var swipeGestureProgressOpen = false
    private var lastScaleEvent: Long = 0
    private var currentNumberOfPointers: Int = 0
    private var isZoomEnabled = false
    private var screenWidth = 0
    private var screenHeight = 0
    private var swipeSeekStartPosition = 0L
    private var speedHoldActive = false

    // Constants

    private fun seekBackwardDeltaMs(): Long {
        return playerPreferences.getSeekBackwardIntervalSeconds() * 1000L
    }

    private fun seekForwardDeltaMs(): Long {
        return playerPreferences.getSeekForwardIntervalSeconds() * 1000L
    }

    // Single tap and double tap detector
    private val tapGestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                onShowControls()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!playerPreferences.arePlayerGesturesEnabled() ||
                    !playerPreferences.isProgressSeekGestureEnabled()
                ) {
                    onShowControls()
                    return true
                }
                when (tapZone(e.x)) {
                    -1 -> onSeek(-seekBackwardDeltaMs())
                    1 -> onSeek(seekForwardDeltaMs())
                    else -> onShowControls()
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (!playerPreferences.arePlayerGesturesEnabled()) return
                if (swipeGestureProgressOpen || swipeGestureVolumeOpen || swipeGestureBrightnessOpen) return
                speedHoldActive = true
                onHoldSpeed(true)
            }
        }
    )

    private fun tapZone(x: Float): Int {
        val viewWidth = touchView.measuredWidth
        if (viewWidth <= 0) return 0
        val landscape = viewWidth > touchView.measuredHeight
        val sideFraction = if (landscape) 0.22f else 0.4f
        val leftEnd = viewWidth * sideFraction
        val rightStart = viewWidth * (1f - sideFraction)
        return when {
            x < leftEnd -> -1
            x >= rightStart -> 1
            else -> 0
        }
    }

    private val seekGestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                firstEvent: MotionEvent?,
                currentEvent: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (firstEvent == null) return false
                if (!playerPreferences.arePlayerGesturesEnabled()) {
                    return false
                }
                if (inExclusionArea(firstEvent)) return false

                // Check if swipe is horizontal
                if (abs(distanceX) > abs(distanceY)) {
                    return if ((abs(currentEvent.x - firstEvent.x) > 50 || swipeGestureProgressOpen) &&
                        !swipeGestureBrightnessOpen && !swipeGestureVolumeOpen &&
                        !speedHoldActive &&
                        (SystemClock.elapsedRealtime() - lastScaleEvent) > 200
                    ) {
                        if (!swipeGestureProgressOpen) {
                            swipeSeekStartPosition = getPlaybackPosition()
                        }
                        val duration = getPlaybackDuration()
                        val width = touchView.measuredWidth.coerceAtLeast(1)
                        val rangeMs = if (duration > 0L) duration / 5L else 10L * 60L * 1000L
                        val difference = ((currentEvent.x - firstEvent.x) / width.toFloat() * rangeMs).toLong()
                        swipeGestureValueTrackerProgress = difference
                        swipeGestureProgressOpen = true
                        val preview = if (duration > 0L) {
                            (swipeSeekStartPosition + difference).coerceIn(0L, duration)
                        } else {
                            (swipeSeekStartPosition + difference).coerceAtLeast(0L)
                        }
                        onSeekPreview(preview)
                        true
                    } else {
                        false
                    }
                }
                return true
            }
        }
    )

    // Volume and brightness gesture detector
    private val vbGestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                firstEvent: MotionEvent?,
                currentEvent: MotionEvent,
                distanceX: Float,
                distanceY: Float,
            ): Boolean {
                if (firstEvent == null) return false
                if (inExclusionArea(firstEvent)) {
                    return false
                }
                if (!playerPreferences.arePlayerGesturesEnabled() ||
                    !playerPreferences.isVolumeBrightnessGesturesEnabled()
                ) {
                    return false
                }

                if (abs(distanceY) < abs(distanceX)) {
                    return false
                }
                if (swipeGestureProgressOpen) {
                    return false
                }

                val viewCenterX = touchView.measuredWidth / 2
                val distanceFull = touchView.measuredHeight * FULL_SWIPE_RANGE_SCREEN_RATIO
                val ratioChange = distanceY / distanceFull

                if (firstEvent.x.toInt() > viewCenterX) {
                    if (swipeGestureValueTrackerVolume == -1f) {
                        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        swipeGestureValueTrackerVolume = getCurrentVolumeLevel() * maxVolume
                    }
                    val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    val change = ratioChange * maxVolume
                    swipeGestureValueTrackerVolume = (swipeGestureValueTrackerVolume + change)
                        .coerceIn(0f, maxVolume.toFloat())

                    val volumePercent = (swipeGestureValueTrackerVolume / maxVolume.toFloat())
                    onVolumeChange(volumePercent)
                    swipeGestureVolumeOpen = true
                } else {
                    if (swipeGestureValueTrackerBrightness == -1f) {
                        swipeGestureValueTrackerBrightness = getCurrentBrightnessLevel()
                    }
                    
                    val newBrightness = (swipeGestureValueTrackerBrightness + ratioChange)
                        .coerceIn(0.01f, 1f)
                    swipeGestureValueTrackerBrightness = newBrightness

                    onBrightnessChange(ratioChange)
                    swipeGestureBrightnessOpen = true
                }
                return true
            }
        }
    )

    // Zoom gesture detector
    private val zoomGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.OnScaleGestureListener {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                return playerPreferences.arePlayerGesturesEnabled() &&
                    playerPreferences.isZoomGestureEnabled()
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!playerPreferences.arePlayerGesturesEnabled() ||
                    !playerPreferences.isZoomGestureEnabled()
                ) {
                    return false
                }
                lastScaleEvent = SystemClock.elapsedRealtime()
                val scaleFactor = detector.scaleFactor
                
                if (abs(scaleFactor - ZOOM_SCALE_BASE) > ZOOM_SCALE_THRESHOLD) {
                    val enableZoom = scaleFactor > 1
                    updateZoomMode(enableZoom)
                }
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) = Unit
        }
    ).apply {
        isQuickScaleEnabled = false
    }

    private fun updateZoomMode(enabled: Boolean) {
        isZoomEnabled = enabled
        onZoomChange(enabled)
    }

    private fun releaseAction(event: MotionEvent) {
        if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
            if (swipeGestureVolumeOpen) {
                swipeGestureVolumeOpen = false
                swipeGestureValueTrackerVolume = -1f
            }

            if (swipeGestureBrightnessOpen) {
                swipeGestureBrightnessOpen = false
                swipeGestureValueTrackerBrightness = -1f
            }

            if (swipeGestureProgressOpen) {
                if (swipeGestureValueTrackerProgress != 0L) {
                    onSeek(swipeGestureValueTrackerProgress)
                }
                swipeGestureProgressOpen = false
                swipeGestureValueTrackerProgress = 0L
                onSeekPreview(null)
            }

            if (speedHoldActive) {
                speedHoldActive = false
                onHoldSpeed(false)
            }

            currentNumberOfPointers = 0
        }
    }

    private fun inExclusionArea(firstEvent: MotionEvent): Boolean {
        val exclusionVertical = GESTURE_EXCLUSION_AREA_VERTICAL * Resources.getSystem().displayMetrics.density
        val exclusionHorizontal = GESTURE_EXCLUSION_AREA_HORIZONTAL * Resources.getSystem().displayMetrics.density

        val inExclusion = firstEvent.y < exclusionVertical ||
            firstEvent.y > screenHeight - exclusionVertical ||
            firstEvent.x < exclusionHorizontal ||
            firstEvent.x > screenWidth - exclusionHorizontal

        return inExclusion
    }

    fun handleTouchEvent(event: MotionEvent): Boolean {
        currentNumberOfPointers = event.pointerCount

        screenWidth = touchView.width
        screenHeight = touchView.height

        when (event.pointerCount) {
            1 -> {
                tapGestureDetector.onTouchEvent(event)
                if (playerPreferences.arePlayerGesturesEnabled() && !speedHoldActive) {
                    vbGestureDetector.onTouchEvent(event)
                    seekGestureDetector.onTouchEvent(event)
                }
            }
            2 -> {
                if (playerPreferences.arePlayerGesturesEnabled() &&
                    playerPreferences.isZoomGestureEnabled()
                ) {
                    zoomGestureDetector.onTouchEvent(event)
                }
            }
        }

        releaseAction(event)
        return true
    }
}
