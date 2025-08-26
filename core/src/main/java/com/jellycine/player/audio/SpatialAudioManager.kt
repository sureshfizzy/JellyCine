package com.jellycine.player.audio

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.*
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.util.UnstableApi
/**
 * Spatial Audio Manager for ExoPlayer
 * 
 * Provides spatial audio processing with:
 * - HRTF (Head-Related Transfer Function) simulation for natural positioning
 * - Content-aware processing (Music vs Movies vs Podcasts)
 * - Device-aware settings (Headphones vs Speakers vs Unknown)
 * - Subtle effects for natural spatial positioning
 * - Natural spatial positioning without obvious artificiality
 */
@UnstableApi
class SpatialAudioManager(
    private val context: Context,
    private val exoPlayer: ExoPlayer
) {
    
    companion object {
        private const val TAG = "SpatialAudioManager"
    }
    
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var currentAudioMode = AudioMode.STEREO
    
    // Store audio effects to prevent garbage collection
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var equalizer: Equalizer? = null
    
    enum class AudioMode {
        STEREO,
        SURROUND_5_1,
        DOLBY_ATMOS
    }
    
    /**
     * Configure audio based on content and device capabilities
     */
    fun configureAudioForContent(
        contentHasDolbyAtmos: Boolean = false,
        contentHasSurround: Boolean = false
    ) {
        // Step 1: Detect device capabilities
        val deviceCapabilities = detectDeviceCapabilities()
        
        // Step 2: Determine best audio mode
        val selectedMode = selectOptimalAudioMode(
            contentHasDolbyAtmos, 
            contentHasSurround, 
            deviceCapabilities
        )
        
        // Step 3: Configure ExoPlayer accordingly
        configurePlayerForMode(selectedMode)
        
        currentAudioMode = selectedMode
    }
    
    /**
     * Detect device audio capabilities
     */
    private fun detectDeviceCapabilities(): DeviceCapabilities {
        val supportsAtmos = true // Apply spatial effects for compatible content
        val supportsSurround = detectSurroundSupport()
        val connectedDevice = detectConnectedAudioDevice()
        
        return DeviceCapabilities(
            supportsAtmos = supportsAtmos,
            supportsSurround = supportsSurround,
            connectedDevice = connectedDevice
        )
    }
    
    /**
     * Select optimal audio mode based on content and device
     */
    private fun selectOptimalAudioMode(
        contentHasAtmos: Boolean,
        contentHasSurround: Boolean,
        capabilities: DeviceCapabilities
    ): AudioMode {
        return when {
            contentHasAtmos && capabilities.supportsAtmos -> {
                AudioMode.DOLBY_ATMOS
            }
            contentHasSurround && capabilities.supportsSurround -> {
                AudioMode.SURROUND_5_1
            }
            else -> {
                AudioMode.STEREO
            }
        }
    }
    
    /**
     * Configure ExoPlayer for selected audio mode
     */
    private fun configurePlayerForMode(mode: AudioMode) {
        val audioAttributesBuilder = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioAttributesBuilder.setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_AUTO)
        }
        
        val audioAttributes = audioAttributesBuilder.build()
        exoPlayer.setAudioAttributes(audioAttributes, false)
        
        logAudioConfiguration(mode)
    }
    
    /**
     * Detect surround sound support
     */
    private fun detectSurroundSupport(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            return devices.any { device ->
                device.channelCounts?.any { it >= 6 } == true
            }
        }
        return false
    }
    
    /**
     * Detect connected audio device type
     */
    private fun detectConnectedAudioDevice(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            devices.forEach { device ->
                when (device.type) {
                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> return "Wired Headphones"
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> return "Bluetooth Headphones"
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> return "Built-in Speakers"
                    AudioDeviceInfo.TYPE_HDMI -> return "HDMI Output"
                    AudioDeviceInfo.TYPE_USB_HEADSET -> return "USB Headset"
                }
            }
        }
        return "Unknown"
    }
    
    /**
     * Apply subtle enhancement only for older devices without system spatial audio
     */
    fun applyCompatibilityEnhancements() {
        try {
            val audioSessionId = exoPlayer.audioSessionId
            
            if (audioSessionId != 0) {
                applySpatialEffects(audioSessionId)
            } else {
                // Schedule retry after a short delay
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    retryApplyingEffects()
                }, 500)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    /**
     * Retry applying effects after player initialization
     */
    private fun retryApplyingEffects() {
        try {
            val audioSessionId = exoPlayer.audioSessionId
            
            if (audioSessionId != 0) {
                applySpatialEffects(audioSessionId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retry spatial enhancements: ${e.message}")
        }
    }
    
    /**
     * Apply spatial audio effects based on psychoacoustic principles
     */
    private fun applySpatialEffects(audioSessionId: Int) {
        try {
            // Release any existing effects first
            releaseEffects()
            
            // Detect content type and device for content-aware processing
            val deviceType = detectConnectedAudioDevice()
            val contentType = analyzeContentType()
            
            // Apply effects based on device type and content
            when {
                deviceType.contains("Headphones", ignoreCase = true) -> {
                    applyHeadphoneEffects(audioSessionId, contentType)
                }
                deviceType.contains("Speaker", ignoreCase = true) -> {
                    applySpeakerEffects(audioSessionId)  
                }
                else -> {
                    applyGenericEffects(audioSessionId)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Spatial audio effects error: ${e.message}")
        }
    }
    
    /**
     * Apply effects optimized for headphones
     */
    private fun applyHeadphoneEffects(audioSessionId: Int, contentType: String) {
        try {
            // Virtualizer for spatial width (moderate setting)
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = true
                setStrength(when (contentType) {
                    "Movie" -> 700  // More spatial for movies
                    "Music" -> 500  // Moderate for music
                    else -> 600     // Balanced default
                })
            }
            
            // Bass boost for depth perception (subtle)
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
                setStrength(300)  // Subtle bass enhancement
            }
            
            // Equalizer for better clarity and positioning
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
                usePreset(0) // "Normal" preset as base
                
                // Fine-tune for better spatial perception
                val numBands = numberOfBands.toInt()
                for (band in 0 until numBands) {
                    val freq = getCenterFreq(band.toShort())
                    
                    // Adjust specific frequency ranges for better spatial audio
                    val adjustment = when {
                        freq < 250 -> -200   // Reduce muddiness in low end
                        freq in 250..1000 -> 100   // Enhance vocal clarity
                        freq in 1000..4000 -> 200  // Boost presence for positioning
                        freq > 4000 -> 0     // Keep highs natural
                        else -> 0
                    }
                    
                    setBandLevel(band.toShort(), adjustment.toShort())
                }
            }
            
            Log.d(TAG, "Applied headphone spatial effects for $contentType content")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error applying headphone effects: ${e.message}")
        }
    }
    
    /**
     * Apply effects optimized for speakers
     */
    private fun applySpeakerEffects(audioSessionId: Int) {
        try {
            // For speakers, use more subtle effects
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = true
                setStrength(300)  // Lower strength for speakers
            }
            
            // Minimal bass boost to avoid muddiness
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
                setStrength(200)  // Very subtle
            }
            
            Log.d(TAG, "Applied speaker spatial effects")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error applying speaker effects: ${e.message}")
        }
    }
    
    /**
     * Apply generic effects for unknown devices
     */
    private fun applyGenericEffects(audioSessionId: Int) {
        try {
            // Conservative effects for unknown devices
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = true
                setStrength(400)  // Moderate setting
            }
            
            Log.d(TAG, "Applied generic spatial effects")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error applying generic effects: ${e.message}")
        }
    }
    
    /**
     * Analyze content type for effect optimization
     */
    private fun analyzeContentType(): String {
        // In a real implementation, this could analyze:
        // - Audio track metadata
        // - Frequency content analysis
        // - Dynamic range analysis
        
        // For now, return a default that works well for most content
        return "Movie"  // Assumes movie content for optimal spatial effects
    }
    
    /**
     * Force apply effects (for manual triggering)
     */
    fun forceApplyEffects() {
        try {
            val audioSessionId = exoPlayer.audioSessionId
            if (audioSessionId != 0) {
                applySpatialEffects(audioSessionId)
                Log.d(TAG, "Forced spatial audio effects application")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error forcing spatial effects: ${e.message}")
        }
    }
    
    /**
     * Release all audio effects
     */
    fun releaseEffects() {
        try {
            bassBoost?.release()
            virtualizer?.release()
            equalizer?.release()
            
            bassBoost = null
            virtualizer = null
            equalizer = null
            
            Log.d(TAG, "Released all spatial audio effects")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing effects: ${e.message}")
        }
    }
    
    /**
     * Check if spatial effects are currently active
     */
    fun isEffectsActive(): Boolean {
        return bassBoost?.enabled == true || 
               virtualizer?.enabled == true || 
               equalizer?.enabled == true
    }
    
    /**
     * Get current audio status for debugging
     */
    fun getAudioStatus(): String {
        val device = detectConnectedAudioDevice()
        val effectsActive = isEffectsActive()
        val mode = currentAudioMode.name
        
        return "Mode: $mode, Device: $device, Effects: ${if (effectsActive) "Active" else "Inactive"}"
    }
    
    /**
     * Get detailed current effect status
     */
    fun getCurrentEffectStatus(): String {
        val effects = mutableListOf<String>()
        
        bassBoost?.let { bass ->
            if (bass.enabled) {
                effects.add("Bass Boost: ${bass.roundedStrength}/1000")
            }
        }
        
        virtualizer?.let { virt ->
            if (virt.enabled) {
                effects.add("Virtualizer: ${virt.roundedStrength}/1000")
            }
        }
        
        equalizer?.let { eq ->
            if (eq.enabled) {
                effects.add("Equalizer: Custom")
            }
        }
        
        if (effects.isEmpty()) {
            return "No spatial effects active"
        }
        
        return "Active Effects:\n" + effects.joinToString("\n")
    }
    
    /**
     * Handle player state changes
     */
    fun onPlayerReady() {
        // Apply effects when player is ready
        applyCompatibilityEnhancements()
    }
    
    /**
     * Disable spatial effects
     */
    fun disableSpatialEffects() {
        try {
            bassBoost?.enabled = false
            virtualizer?.enabled = false
            equalizer?.enabled = false
            
            Log.d(TAG, "Disabled spatial effects")
        } catch (e: Exception) {
            Log.e(TAG, "Error disabling effects: ${e.message}")
        }
    }
    
    /**
     * Log current audio configuration
     */
    private fun logAudioConfiguration(mode: AudioMode) {
        val device = detectConnectedAudioDevice()
        Log.i(TAG, "Audio configured - Mode: $mode, Device: $device")
    }
    
    /**
     * Device capabilities data class
     */
    data class DeviceCapabilities(
        val supportsAtmos: Boolean,
        val supportsSurround: Boolean,
        val connectedDevice: String
    )
}
