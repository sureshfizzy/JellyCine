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
class SpatializerHelper(context: Context) {
    
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
            return evaluateSpatialization(audioFormat, audioAttributes).routeSupportsSpatialization
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
            val state = evaluateSpatialization(audioFormat, audioAttributes)
            android.util.Log.d(
                "SpatializerHelper",
                "Spatial Active Check: routeSupports=${state.routeSupportsSpatialization}, available=${state.isAvailable}, enabled=${state.isEnabled}"
            )
            return state.routeSupportsSpatialization && state.isAvailable && state.isEnabled
        }
        return false
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
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun evaluateSpatialization(
        audioFormat: AudioFormat,
        audioAttributes: AudioAttributes
    ): SpatializationState {
        return spatializer?.let { spatializer ->
            val hasImmersiveLevel =
                spatializer.immersiveAudioLevel != Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE
            val isAvailable = spatializer.isAvailable
            val canBeSpatialized = if (isOplusDevice()) {
                true
            } else {
                spatializer.canBeSpatialized(audioAttributes, audioFormat)
            }
            val routeSupportsSpatialization = hasImmersiveLevel && isAvailable && canBeSpatialized

            android.util.Log.d(
                "SpatializerHelper",
                "Spatial Route Check: immersiveLevel=$hasImmersiveLevel, available=$isAvailable, canBeSpatialized=$canBeSpatialized"
            )
            SpatializationState(
                routeSupportsSpatialization = routeSupportsSpatialization,
                isAvailable = isAvailable,
                isEnabled = spatializer.isEnabled
            )
        } ?: SpatializationState()
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

    private data class SpatializationState(
        val routeSupportsSpatialization: Boolean = false,
        val isAvailable: Boolean = false,
        val isEnabled: Boolean = false
    )
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
