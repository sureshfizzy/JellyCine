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
                
                // Log current spatializer state for debugging
                spatializer?.let { s ->
                    android.util.Log.d("SpatializerHelper", "Spatializer immersive level: ${s.immersiveAudioLevel}")
                    android.util.Log.d("SpatializerHelper", "Spatializer available: ${s.isAvailable}")
                    android.util.Log.d("SpatializerHelper", "Spatializer enabled: ${s.isEnabled}")
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
                val canBeSpatialized = if (isOplusDevice()) {
                    // Oplus framework may spam AudioEffect getParameter warnings for this probe.
                    // Use route availability + immersive support instead.
                    true
                } else {
                    spatializer.canBeSpatialized(audioAttributes, audioFormat)
                }
                
                android.util.Log.d(
                    "SpatializerHelper",
                    "Spatial Route Check: immersiveLevel=$hasImmersiveLevel, available=$isAvailable, canBeSpatialized=$canBeSpatialized")

                hasImmersiveLevel && isAvailable && canBeSpatialized
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
                val isAvailable = spatializer.isAvailable

                android.util.Log.d(
                    "SpatializerHelper",
                    "Spatial Active Check: routeSupports=$routeSupportsSpatialization, available=$isAvailable, enabled=$isEnabled")

                routeSupportsSpatialization && isAvailable && isEnabled
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
     * Check whether device spatializer is currently enabled in system settings for active route.
     */
    fun isSpatializerEnabled(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return false
        }

        val info = getSpatialAudioInfo()
        return info.isSupported && info.isAvailable && info.isEnabled
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
     * Get spatial audio capabilities info
     */
    fun getSpatialAudioInfo(): SpatialAudioInfo {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            spatializer?.let { spatializer ->
                return SpatialAudioInfo(
                    isSupported = spatializer.immersiveAudioLevel != Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE,
                    isAvailable = spatializer.isAvailable,
                    isEnabled = spatializer.isEnabled,
                    immersiveLevel = spatializer.immersiveAudioLevel
                )
            }
        }
        return SpatialAudioInfo()
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        // No resources to clean up currently.
    }

    private fun isOplusDevice(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        return manufacturer.contains("oneplus") ||
            manufacturer.contains("oppo") ||
            manufacturer.contains("realme") ||
            brand.contains("oneplus") ||
            brand.contains("oppo") ||
            brand.contains("realme")
    }
}

/**
 * Data class for spatial audio information
 */
data class SpatialAudioInfo(
    val isSupported: Boolean = false,
    val isAvailable: Boolean = false,
    val isEnabled: Boolean = false,
    val immersiveLevel: Int = 0
)
