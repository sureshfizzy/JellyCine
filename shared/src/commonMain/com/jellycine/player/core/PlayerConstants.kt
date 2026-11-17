package com.jellycine.player.core

/**
 * Shared constants for the player module to avoid duplication
 */
object PlayerConstants {
    // Gesture exclusion areas (in dp)
    const val GESTURE_EXCLUSION_AREA_VERTICAL = 48
    const val GESTURE_EXCLUSION_AREA_HORIZONTAL = 24
    
    // Swipe gesture sensitivity
    const val FULL_SWIPE_RANGE_SCREEN_RATIO = 0.66f
    
    // Zoom gesture settings
    const val ZOOM_SCALE_BASE = 1f
    const val ZOOM_SCALE_THRESHOLD = 0.01f
    
    // Auto-hide delays (in milliseconds)
    const val CONTROLS_AUTO_HIDE_DELAY = 3000L
    const val GESTURE_INDICATOR_HIDE_DELAY = 2000L
    const val NEXT_EPISODE_AUTOPLAY_DELAY = 10_000L
    const val NEXT_EPISODE_PROGRESS_UPDATE_DELAY = 16L
    
    // Player preferences defaults
    const val DEFAULT_BRIGHTNESS = 0.5f
    const val DEFAULT_VOLUME = 0.5f
    
    // UI dimensions
    const val PROGRESS_BAR_HEIGHT_DP = 12
    const val GESTURE_INDICATOR_PADDING_DP = 32
}
