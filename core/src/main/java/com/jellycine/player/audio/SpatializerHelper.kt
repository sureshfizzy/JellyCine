package com.jellycine.player.audio

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
     * Check whether the current route supports spatialization for the given format.
     * This does not require the system spatializer toggle to be enabled.
     */
    fun canSpatializeOnTrack(
        audioFormat: AudioFormat,
        audioAttributes: AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
            .build()
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return spatializer?.let { spatializer ->
                val hasImmersiveLevel = spatializer.immersiveAudioLevel != Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE
                val isAvailable = spatializer.isAvailable
                val canBeSpatialized = spatializer.canBeSpatialized(audioAttributes, audioFormat)
                
                android.util.Log.d(
                    "SpatializerHelper",
                    "Spatial Route Check: immersiveLevel=$hasImmersiveLevel, available=$isAvailable, canBeSpatialized=$canBeSpatialized")

                // Some OEM builds report unavailable when system toggle is off.
                // Compatibility processing only requires route-capable content.
                hasImmersiveLevel && canBeSpatialized
            } ?: false
        }
        return false
    }

    /**
     * Check if the system spatializer is currently active for this route and format.
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
                val routeSupportsSpatialization = canSpatializeOnTrack(audioFormat, audioAttributes)
                val isEnabled = spatializer.isEnabled

                android.util.Log.d(
                    "SpatializerHelper",
                    "Spatial Active Check: routeSupports=$routeSupportsSpatialization, enabled=$isEnabled")

                routeSupportsSpatialization && isEnabled
            } ?: false
        }
        return false
    }
    
    /**
     * Check whether the current output route can spatialize multichannel content.
     * Uses runtime capability checks and never assumes support.
     */
    fun canSpatializeMultiChannel(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false
        }

        val candidateChannelCounts = intArrayOf(8, 6)
        return candidateChannelCounts.any { channelCount ->
            val audioFormat = getRecommendedAudioFormat(channelCount) ?: return@any false
            canSpatializeOnTrack(audioFormat)
        }
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
 * Listener interface for spatializer state changes
 */
interface SpatializerStateListener {
    fun onSpatializerAvailabilityChanged(isAvailable: Boolean)
    fun onSpatializerEnabledChanged(isEnabled: Boolean)
}
