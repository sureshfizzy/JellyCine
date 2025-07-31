package com.jellycine.player

import android.content.Context
import android.content.SharedPreferences
import com.jellycine.player.PlayerConstants.DEFAULT_BRIGHTNESS
import com.jellycine.player.PlayerConstants.DEFAULT_VOLUME

/**
 * Manages player-specific preferences like brightness and volume levels
 * Follows 's approach to remember user settings per player session
 */
class PlayerPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "jellycine_player_prefs"
        private const val KEY_PLAYER_BRIGHTNESS = "player_brightness"
        private const val KEY_PLAYER_VOLUME = "player_volume"
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
}
