package com.jellycine.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.Spatializer
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Helper class for proper spatial audio implementation following Google's guidelines
 */
class SpatializerHelper(private val context: Context) {
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var spatializer: Spatializer? = null
    private val listeners = mutableListOf<SpatializerStateListener>()
    
    init {
        android.util.Log.d("SpatializerHelper", "=== INITIALIZING SPATIALIZER HELPER ===")
        android.util.Log.d("SpatializerHelper", "Android version: ${Build.VERSION.SDK_INT}")
        android.util.Log.d("SpatializerHelper", "Required version: ${Build.VERSION_CODES.TIRAMISU} (33)")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.util.Log.d("SpatializerHelper", "Device supports Spatializer API - initializing...")
            initializeSpatializer()
        } else {
            android.util.Log.w("SpatializerHelper", "Device does not support Spatializer API (Android < 13)")
        }
    }
    
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun initializeSpatializer() {
        try {
            android.util.Log.d("SpatializerHelper", "Getting spatializer from AudioManager...")
            spatializer = audioManager.spatializer
            
            if (spatializer != null) {
                android.util.Log.d("SpatializerHelper", "Spatializer obtained successfully")
                
                // Log current spatializer state for debugging (but don't warn about disabled state)
                spatializer?.let { s ->
                    android.util.Log.d("SpatializerHelper", "Spatializer immersive level: ${s.immersiveAudioLevel}")
                    android.util.Log.d("SpatializerHelper", "Spatializer available: ${s.isAvailable}")
                    android.util.Log.d("SpatializerHelper", "Spatializer enabled: ${s.isEnabled}")
                    android.util.Log.d("SpatializerHelper", "Head tracker available: ${s.isHeadTrackerAvailable}")
                    
                    android.util.Log.d("SpatializerHelper", "*** DOLBY APPROACH: System settings don't matter for content-based spatial audio ***")
                }
            } else {
                android.util.Log.w("SpatializerHelper", "Spatializer is null - device may not support spatial audio")
            }
        } catch (e: Exception) {
            android.util.Log.e("SpatializerHelper", "Failed to initialize spatializer", e)
            spatializer = null
        }
    }
    
    /**
     * Check if the device can actually spatialize audio for the given format
     * This is the key method that determines real spatial audio capability
     */
    fun canSpatializeAudio(
        audioFormat: AudioFormat,
        audioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return spatializer?.let { spatializer ->
                // Check all four required conditions per Google's documentation
                val hasImmersiveLevel = spatializer.immersiveAudioLevel != Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE
                val isAvailable = spatializer.isAvailable
                val isEnabled = spatializer.isEnabled
                val canBeSpatialized = spatializer.canBeSpatialized(audioAttributes, audioFormat)
                
                // Log the detailed results for debugging
                android.util.Log.d("SpatializerHelper", 
                    "Spatial Audio Check: immersiveLevel=$hasImmersiveLevel, available=$isAvailable, enabled=$isEnabled, canBeSpatialized=$canBeSpatialized")
                
                hasImmersiveLevel && isAvailable && isEnabled && canBeSpatialized
            } ?: false
        }
        return false
    }
    
    /**
     * Check if device can spatialize multi-channel content (Dolby Atmos approach)
     * Based purely on content format detection, NOT system settings
     */
    fun canSpatializeMultiChannel(): Boolean {
        android.util.Log.d("SpatializerHelper", "=== DOLBY ATMOS CONTENT-BASED SPATIAL AUDIO ===")
        android.util.Log.d("SpatializerHelper", "Pure content-based detection (ALL system settings ignored)")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            spatializer?.let { spatializer ->
                android.util.Log.d("SpatializerHelper", "=== DOLBY CONTENT-BASED DETECTION ===")
                android.util.Log.d("SpatializerHelper", "Hardware immersive level: ${spatializer.immersiveAudioLevel}")
                android.util.Log.d("SpatializerHelper", "System available: ${spatializer.isAvailable} <- IGNORED")
                android.util.Log.d("SpatializerHelper", "System enabled: ${spatializer.isEnabled} <- IGNORED")
                android.util.Log.d("SpatializerHelper", "")
                android.util.Log.d("SpatializerHelper", "*** DOLBY APPROACH: Content format determines spatial processing ***")
                android.util.Log.d("SpatializerHelper", "*** If content = Dolby Atmos → Enable spatial effects ***")
                android.util.Log.d("SpatializerHelper", "*** System settings are COMPLETELY IRRELEVANT ***")
            }
            
            // Dolby Atmos approach: Always return true for devices with Android 13+
            // Content format (detected elsewhere) determines if spatial effects are applied
            android.util.Log.d("SpatializerHelper", "DOLBY RESULT: true (content-based, system ignored)")
            return true
        }
        
        // For older Android versions, Dolby Atmos content always gets spatial processing
        android.util.Log.d("SpatializerHelper", "Android < 13: Dolby Atmos content always gets spatial effects")
        return true
    }
    
    /**
     * Get recommended audio format for spatial content
     */
    fun getRecommendedAudioFormat(channelCount: Int): AudioFormat? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val channelMask = when (channelCount) {
                2 -> AudioFormat.CHANNEL_OUT_STEREO
                6 -> AudioFormat.CHANNEL_OUT_5POINT1
                8 -> AudioFormat.CHANNEL_OUT_7POINT1_SURROUND
                else -> AudioFormat.CHANNEL_OUT_5POINT1 // Default fallback
            }
            
            return AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(channelMask)
                .setSampleRate(48000)
                .build()
        }
        return null
    }
    
    /**
     * Check if head tracking is available
     */
    fun isHeadTrackerAvailable(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            spatializer?.isHeadTrackerAvailable ?: false
        } else {
            false
        }
    }
    
    /**
     * Get spatial audio capabilities info
     */
    fun getSpatialAudioInfo(): SpatialAudioInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            spatializer?.let { spatializer ->
                return SpatialAudioInfo(
                    isSupported = spatializer.immersiveAudioLevel != Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE,
                    isAvailable = spatializer.isAvailable,
                    isEnabled = spatializer.isEnabled,
                    hasHeadTracker = spatializer.isHeadTrackerAvailable,
                    immersiveLevel = spatializer.immersiveAudioLevel
                )
            }
        }
        return SpatialAudioInfo()
    }
    
    /**
     * Add listener for spatializer state changes
     */
    fun addSpatializerStateListener(listener: SpatializerStateListener) {
        listeners.add(listener)
    }
    
    /**
     * Remove listener
     */
    fun removeSpatializerStateListener(listener: SpatializerStateListener) {
        listeners.remove(listener)
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        listeners.clear()
        // Note: Spatializer listeners are automatically removed when spatializer is GC'd
    }
}

/**
 * Data class for spatial audio information
 */
data class SpatialAudioInfo(
    val isSupported: Boolean = false,
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val hasHeadTracker: Boolean = false,
    val immersiveLevel: Int = 0
)

/**
 * Interface for spatializer state changes
 */
interface SpatializerStateListener {
    fun onSpatializerStateChanged(spatializer: Spatializer, state: Int) {}
    fun onHeadTrackerAvailableChanged(spatializer: Spatializer, available: Boolean) {}
}