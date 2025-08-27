package com.jellycine.app.ui.screens.player

import android.content.Context
import android.content.res.Resources
import android.media.AudioManager
import android.os.SystemClock
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.common.util.UnstableApi
import kotlin.math.abs

import com.jellycine.player.core.PlayerConstants.GESTURE_EXCLUSION_AREA_HORIZONTAL
import com.jellycine.player.core.PlayerConstants.GESTURE_EXCLUSION_AREA_VERTICAL
import com.jellycine.player.core.PlayerConstants.FULL_SWIPE_RANGE_SCREEN_RATIO
import com.jellycine.player.core.PlayerConstants.SEEK_BACKWARD_MS
import com.jellycine.player.core.PlayerConstants.SEEK_FORWARD_MS
import com.jellycine.player.core.PlayerConstants.ZOOM_SCALE_BASE
import com.jellycine.player.core.PlayerConstants.ZOOM_SCALE_THRESHOLD
import com.jellycine.player.preferences.PlayerPreferences

@UnstableApi
class GestureHelper(
    private val context: Context,
    private val playerView: PlayerView,
    private val audioManager: AudioManager,
    private val onShowControls: () -> Unit,
    private val onHideControls: () -> Unit,
    private val onSeek: (Long) -> Unit,
    private val onVolumeChange: (Float) -> Unit,
    private val onBrightnessChange: (Float) -> Unit,
    private val onZoomChange: (Boolean) -> Unit
) {
    private val playerPreferences = PlayerPreferences(context)
    // Gesture state tracking
    private var swipeGestureValueTrackerVolume = -1f
    private var swipeGestureValueTrackerBrightness = -1f
    private var swipeGestureValueTrackerProgress = -1L
    private var swipeGestureVolumeOpen = false
    private var swipeGestureBrightnessOpen = false
    private var swipeGestureProgressOpen = false
    private var lastScaleEvent: Long = 0
    private var currentNumberOfPointers: Int = 0
    private var isZoomEnabled = false
    private var screenWidth = 0
    private var screenHeight = 0

    // Constants


    // Single tap and double tap detector
    private val tapGestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!playerView.isControllerFullyVisible) {
                    onShowControls()
                } else {
                    onHideControls()
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val viewWidth = playerView.measuredWidth
                val areaWidth = viewWidth / 5 // Divide into 5 parts: 2:1:2
                
                val leftmostAreaStart = 0
                val middleAreaStart = areaWidth * 2
                val rightmostAreaStart = middleAreaStart + areaWidth

                when (e.x.toInt()) {
                    in leftmostAreaStart until middleAreaStart -> {
                        onSeek(-SEEK_BACKWARD_MS)
                    }
                    in middleAreaStart until rightmostAreaStart -> {
                        playerView.player?.let { player ->
                            player.playWhenReady = !player.playWhenReady
                        }
                    }
                    in rightmostAreaStart until viewWidth -> {
                        onSeek(SEEK_FORWARD_MS)
                    }
                }
                return true
            }
        }
    )

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
                if (inExclusionArea(firstEvent)) return false

                // Check if swipe is horizontal
                if (abs(distanceX) > abs(distanceY)) {
                    return if ((abs(currentEvent.x - firstEvent.x) > 50 || swipeGestureProgressOpen) &&
                        !swipeGestureBrightnessOpen && !swipeGestureVolumeOpen &&
                        (SystemClock.elapsedRealtime() - lastScaleEvent) > 200
                    ) {
                        val difference = ((currentEvent.x - firstEvent.x) * 90).toLong()
                        swipeGestureValueTrackerProgress = difference
                        swipeGestureProgressOpen = true
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

                if (abs(distanceY) < abs(distanceX)) {
                    return false
                }
                if (swipeGestureValueTrackerProgress > -1 || swipeGestureProgressOpen) {
                    return false
                }

                val viewCenterX = playerView.measuredWidth / 2
                val distanceFull = playerView.measuredHeight * FULL_SWIPE_RANGE_SCREEN_RATIO
                val ratioChange = distanceY / distanceFull

                if (firstEvent.x.toInt() > viewCenterX) {
                    if (swipeGestureValueTrackerVolume == -1f) {
                        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        swipeGestureValueTrackerVolume = playerPreferences.getPlayerVolume() * maxVolume
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
                        swipeGestureValueTrackerBrightness = playerPreferences.getPlayerBrightness()
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
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean = true

            override fun onScale(detector: ScaleGestureDetector): Boolean {
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
                if (swipeGestureValueTrackerProgress > -1) {
                    onSeek(swipeGestureValueTrackerProgress)
                }
                swipeGestureProgressOpen = false
                swipeGestureValueTrackerProgress = -1L
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

        if (screenWidth == 0 || screenHeight == 0) {
            screenWidth = playerView.width
            screenHeight = playerView.height
        }

        when (event.pointerCount) {
            1 -> {
                tapGestureDetector.onTouchEvent(event)
                vbGestureDetector.onTouchEvent(event)
                seekGestureDetector.onTouchEvent(event)
            }
            2 -> {
                zoomGestureDetector.onTouchEvent(event)
            }
        }

        releaseAction(event)
        return true
    }
}
