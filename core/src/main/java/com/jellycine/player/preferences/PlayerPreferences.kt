package com.jellycine.player.preferences

import android.content.Context
import android.content.SharedPreferences
import com.jellycine.player.core.PlayerConstants.DEFAULT_BRIGHTNESS
import com.jellycine.player.core.PlayerConstants.DEFAULT_VOLUME

/**
 * Manages player-specific preferences like brightness, volume levels, and hardware acceleration settings
 * Follows ExoPlayer's approach to remember user settings per player session
 */
class PlayerPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "jellycine_player_prefs"
        private const val KEY_PLAYER_BRIGHTNESS = "player_brightness"
        private const val KEY_PLAYER_VOLUME = "player_volume"
        private const val KEY_HARDWARE_ACCELERATION = "hardware_acceleration_enabled"
        private const val KEY_ASYNC_MEDIACODEC = "async_mediacodec_enabled"
        private const val KEY_SPATIAL_AUDIO = "spatial_audio_enabled"
        private const val KEY_DECODER_PRIORITY = "decoder_priority"
        private const val KEY_BATTERY_OPTIMIZATION = "battery_optimization_enabled"
        private const val KEY_BUFFER_OPTIMIZATION = "buffer_optimization_enabled"
        private const val KEY_START_MAXIMIZED = "start_maximized"
        private const val KEY_HDR_ENABLED = "hdr_enabled"
        private const val KEY_HEAD_TRACKING = "head_tracking_enabled"
    }
    
    /**
     * Get the saved player brightness level (0.01f to 1.0f)
     * Returns the last used brightness or default if none saved
     */
    fun getPlayerBrightness(): Float {
        return prefs.getFloat(KEY_PLAYER_BRIGHTNESS, DEFAULT_BRIGHTNESS)
            .coerceIn(0.01f, 1.0f)
    }
    
    /**
     * Save the current player brightness level
     */
    fun setPlayerBrightness(brightness: Float) {
        prefs.edit()
            .putFloat(KEY_PLAYER_BRIGHTNESS, brightness.coerceIn(0.01f, 1.0f))
            .apply()
    }
    
    /**
     * Get the saved player volume level (0.0f to 1.0f)
     * Returns the last used volume or default if none saved
     */
    fun getPlayerVolume(): Float {
        return prefs.getFloat(KEY_PLAYER_VOLUME, DEFAULT_VOLUME)
            .coerceIn(0.0f, 1.0f)
    }
    
    /**
     * Save the current player volume level
     */
    fun setPlayerVolume(volume: Float) {
        prefs.edit()
            .putFloat(KEY_PLAYER_VOLUME, volume.coerceIn(0.0f, 1.0f))
            .apply()
    }
    
    /**
     * Clear all player preferences (useful for reset)
     */
    fun clearPreferences() {
        prefs.edit().clear().apply()
    }
    
    /**
     * Get hardware acceleration preference
     */
    fun isHardwareAccelerationEnabled(): Boolean {
        return prefs.getBoolean(KEY_HARDWARE_ACCELERATION, true)
    }
    
    /**
     * Set hardware acceleration preference
     */
    fun setHardwareAccelerationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HARDWARE_ACCELERATION, enabled).apply()
    }
    
    /**
     * Get asynchronous MediaCodec preference
     */
    fun isAsyncMediaCodecEnabled(): Boolean {
        return prefs.getBoolean(KEY_ASYNC_MEDIACODEC, false)
    }
    
    /**
     * Set asynchronous MediaCodec preference
     */
    fun setAsyncMediaCodecEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ASYNC_MEDIACODEC, enabled).apply()
    }
    
    /**
     * Get spatial audio preference
     */
    fun isSpatialAudioEnabled(): Boolean {
        return prefs.getBoolean(KEY_SPATIAL_AUDIO, true)
    }
    
    /**
     * Set spatial audio preference
     */
    fun setSpatialAudioEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SPATIAL_AUDIO, enabled).apply()
    }
    
    /**
     * Get decoder priority preference
     */
    fun getDecoderPriority(): String {
        return prefs.getString(KEY_DECODER_PRIORITY, "Auto") ?: "Auto"
    }
    
    /**
     * Set decoder priority preference
     */
    fun setDecoderPriority(priority: String) {
        prefs.edit().putString(KEY_DECODER_PRIORITY, priority).apply()
    }
    
    /**
     * Get battery optimization preference
     */
    fun isBatteryOptimizationEnabled(): Boolean {
        return prefs.getBoolean(KEY_BATTERY_OPTIMIZATION, false)
    }
    
    /**
     * Set battery optimization preference
     */
    fun setBatteryOptimizationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BATTERY_OPTIMIZATION, enabled).apply()
    }
    
    /**
     * Get buffer optimization preference
     */
    fun isBufferOptimizationEnabled(): Boolean {
        return prefs.getBoolean(KEY_BUFFER_OPTIMIZATION, true)
    }
    
    /**
     * Set buffer optimization preference
     */
    fun setBufferOptimizationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BUFFER_OPTIMIZATION, enabled).apply()
    }
    
    /**
     * Get start maximized preference
     */
    fun isStartMaximizedEnabled(): Boolean {
        return prefs.getBoolean(KEY_START_MAXIMIZED, false)
    }
    
    /**
     * Set start maximized preference
     */
    fun setStartMaximizedEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_START_MAXIMIZED, enabled).apply()
    }
    
    /**
     * Get HDR enabled preference
     */
    fun isHdrEnabled(): Boolean {
        return prefs.getBoolean(KEY_HDR_ENABLED, true)
    }
    
    /**
     * Set HDR enabled preference
     */
    fun setHdrEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HDR_ENABLED, enabled).apply()
    }
    

    /**
     * Get head tracking preference
     */
    fun isHeadTrackingEnabled(): Boolean {
        return prefs.getBoolean(KEY_HEAD_TRACKING, false)
    }
    
    /**
     * Set head tracking preference
     */
    fun setHeadTrackingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HEAD_TRACKING, enabled).apply()
    }
}
