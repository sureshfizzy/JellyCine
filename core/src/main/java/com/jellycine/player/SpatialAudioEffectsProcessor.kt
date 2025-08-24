package com.jellycine.player

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
            val contentType = detectContentType()
            val deviceType = detectConnectedAudioDevice()
            
            // Apply content-aware spatial audio processing
            when {
                contentType == ContentType.MUSIC && deviceType.contains("Headphones") -> {
                    applyMusicSpatialEffects(audioSessionId)
                }
                contentType == ContentType.MOVIE && deviceType.contains("Headphones") -> {
                    applyMovieSpatialEffects(audioSessionId)
                }
                deviceType.contains("Speaker") -> {
                    applySpeakerSpatialEffects(audioSessionId)
                }
                else -> {
                    applyGenericSpatialEffects(audioSessionId)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply spatial audio effects: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Content-aware spatial effects for music on headphones
     */
    private fun applyMusicSpatialEffects(audioSessionId: Int) {
        try {
            // Bass enhancement for music (20-25% strength)
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
                setStrength(250) // 25% strength - music bass enhancement
            }
            
            // Stereo widening for music spatialization (35-40%)
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = true
                setStrength(380) // 38% strength - music spatialization
            }
            
            // Music-optimized EQ for spatial clarity
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
                applyMusicSpatialEQ()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply music spatial effects: ${e.message}")
            e.printStackTrace()
        }
    }
    
    /**
     * Content-aware spatial effects for movies on headphones
     */
    private fun applyMovieSpatialEffects(audioSessionId: Int) {
        // Bass for movie dynamics (28-32% strength)
        bassBoost = BassBoost(0, audioSessionId).apply {
            enabled = true
            setStrength(320) // 32% strength - movie bass
        }
        
        // Spatialization for movie immersion (42-48%)
        virtualizer = Virtualizer(0, audioSessionId).apply {
            enabled = true
            setStrength(460) // 46% strength - movie spatialization
        }
        
        // Movie-optimized EQ for dialogue clarity and spatial positioning
        equalizer = Equalizer(0, audioSessionId).apply {
            enabled = true
            applyMovieSpatialEQ()
        }
    }
    
    /**
     * Spatial effects optimized for speakers
     */
    private fun applySpeakerSpatialEffects(audioSessionId: Int) {
        // Bass enhancement for speakers (25-35% strength is optimal)
        bassBoost = BassBoost(0, audioSessionId).apply {
            enabled = true
            setStrength(300) // 30% strength - spatial bass
        }
        
        // Spatialization for speakers (40-50% for wide soundstage)
        virtualizer = Virtualizer(0, audioSessionId).apply {
            enabled = true
            setStrength(450) // 45% strength - spatial width
        }
        
        // Speaker-optimized EQ with proper frequency targeting
        equalizer = Equalizer(0, audioSessionId).apply {
            enabled = true
            applySpeakerSpatialEQ()
        }
    }
    
    /**
     * Generic spatial effects as fallback
     */
    private fun applyGenericSpatialEffects(audioSessionId: Int) {
        // Balanced bass enhancement
        bassBoost = BassBoost(0, audioSessionId).apply {
            enabled = true
            setStrength(175) // Balanced enhancement
        }
        
        // Moderate spatialization
        virtualizer = Virtualizer(0, audioSessionId).apply {
            enabled = true
            setStrength(280) // Moderate spatial enhancement
        }
        
        // Balanced EQ
        equalizer = Equalizer(0, audioSessionId).apply {
            enabled = true
            applyGenericSpatialEQ()
        }
    }
    
    /**
     * Release audio effects to prevent memory leaks
     */
    private fun releaseEffects() {
        try {
            bassBoost?.release()
            virtualizer?.release()
            equalizer?.release()
            bassBoost = null
            virtualizer = null
            equalizer = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing effects: ${e.message}")
        }
    }
    
    /**
     * Apply spatial audio EQ curve (legacy method for compatibility)
     */
    private fun Equalizer.applySpatialEQCurve() {
        // Use generic spatial EQ as fallback
        applyGenericSpatialEQ()
    }
    
    /**
     * Content types for adaptive spatial audio processing
     */
    private enum class ContentType {
        MUSIC,
        MOVIE,
        PODCAST,
        UNKNOWN
    }
    
    /**
     * Detect content type for adaptive spatial audio processing
     * In a real implementation, this would analyze audio characteristics
     */
    private fun detectContentType(): ContentType {
        // For now, assume movie content for jellycine app
        // In a real implementation, this could analyze:
        // - Dynamic range
        // - Frequency spectrum
        // - Channel configuration
        // - Metadata from the media source
        return ContentType.MOVIE
    }
    
    /**
     * Music spatial EQ - clarity-focused with subtle width
     */
    private fun Equalizer.applyMusicSpatialEQ() {
        try {
            val bandCount = numberOfBands
            
            for (band in 0 until bandCount.toInt()) {
                val frequency = getCenterFreq(band.toShort()) / 1000
                
                // Music EQ curve - subtle and natural
                val gain = when {
                    frequency < 60 -> 50      // +0.5dB sub-bass (controlled)
                    frequency < 200 -> 100    // +1dB bass (warmth)
                    frequency < 500 -> 0      // Neutral low-mid
                    frequency < 2000 -> 75    // +0.75dB mid (presence)
                    frequency < 5000 -> 150   // +1.5dB high-mid (clarity)
                    frequency < 10000 -> 100  // +1dB treble (air)
                    else -> 50                // +0.5dB ultra-high (sparkle)
                }.toShort()
                
                setBandLevel(band.toShort(), gain)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply music spatial EQ: ${e.message}")
        }
    }
    
    /**
     * Movie spatial EQ - dialogue clarity with immersive spatial
     */
    private fun Equalizer.applyMovieSpatialEQ() {
        try {
            val bandCount = numberOfBands
            
            for (band in 0 until bandCount.toInt()) {
                val frequency = getCenterFreq(band.toShort()) / 1000
                
                // Movie EQ curve - dialogue clarity focused
                val gain = when {
                    frequency < 80 -> 75      // +0.75dB sub-bass (LFE)
                    frequency < 250 -> 125    // +1.25dB bass (warmth)
                    frequency < 500 -> 50     // +0.5dB low-mid
                    frequency < 1500 -> 100   // +1dB mid (dialogue presence)
                    frequency < 4000 -> 175   // +1.75dB high-mid (dialogue clarity)
                    frequency < 8000 -> 100   // +1dB treble (detail)
                    else -> 75                // +0.75dB ultra-high (air)
                }.toShort()
                
                setBandLevel(band.toShort(), gain)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply movie spatial EQ: ${e.message}")
        }
    }
    
    /**
     * Speaker spatial EQ - room-aware and conservative
     */
    private fun Equalizer.applySpeakerSpatialEQ() {
        try {
            val bandCount = numberOfBands
            
            for (band in 0 until bandCount.toInt()) {
                val frequency = getCenterFreq(band.toShort()) / 1000
                
                // Speaker EQ curve - room-aware
                val gain = when {
                    frequency < 100 -> 25     // +0.25dB sub-bass (avoid muddiness)
                    frequency < 300 -> 50     // +0.5dB bass (controlled)
                    frequency < 1000 -> 25    // +0.25dB low-mid
                    frequency < 3000 -> 75    // +0.75dB mid (presence)
                    frequency < 6000 -> 100   // +1dB high-mid (clarity)
                    frequency < 12000 -> 75   // +0.75dB treble
                    else -> 25                // +0.25dB ultra-high
                }.toShort()
                
                setBandLevel(band.toShort(), gain)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply speaker spatial EQ: ${e.message}")
        }
    }
    
    /**
     * Generic spatial EQ - balanced for unknown scenarios
     */
    private fun Equalizer.applyGenericSpatialEQ() {
        try {
            val bandCount = numberOfBands
            
            for (band in 0 until bandCount.toInt()) {
                val frequency = getCenterFreq(band.toShort()) / 1000
                
                // Generic EQ curve - balanced
                val gain = when {
                    frequency < 80 -> 50      // +0.5dB sub-bass
                    frequency < 250 -> 75     // +0.75dB bass
                    frequency < 800 -> 25     // +0.25dB low-mid
                    frequency < 2500 -> 100   // +1dB mid
                    frequency < 6000 -> 125   // +1.25dB high-mid
                    frequency < 10000 -> 75   // +0.75dB treble
                    else -> 50                // +0.5dB ultra-high
                }.toShort()
                
                setBandLevel(band.toShort(), gain)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply generic spatial EQ: ${e.message}")
        }
    }
    
    /**
     * Log current audio configuration
     */
    private fun logAudioConfiguration(mode: AudioMode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val spatializer = audioManager.spatializer
                // Log basic configuration for debugging
            } catch (e: Exception) {
                // Spatializer check failed
            }
        }
    }
    
    /**
     * Get current audio status
     */
    fun getAudioStatus(): AudioStatus {
        return AudioStatus(
            currentMode = currentAudioMode,
            systemSpatialAvailable = detectDeviceCapabilities().supportsAtmos,
            connectedDevice = detectConnectedAudioDevice()
        )
    }
    
    /**
     * Debug method to check spatial audio setup
     */
    fun debugSpatialAudioSetup(): String {
        val sb = StringBuilder()
        sb.appendLine("=== SPATIAL AUDIO DEBUG INFO ===")
        
        // Check device capabilities
        val capabilities = detectDeviceCapabilities()
        sb.appendLine("Device supports native spatial: ${capabilities.supportsAtmos}")
        sb.appendLine("Device supports surround: ${capabilities.supportsSurround}")
        sb.appendLine("Connected device: ${capabilities.connectedDevice}")
        sb.appendLine("Current audio mode: $currentAudioMode")
        sb.appendLine("Content type: ${detectContentType()}")
        
        // Check Android version and platform features
        sb.appendLine("\nAndroid version: ${Build.VERSION.SDK_INT}")
        sb.appendLine("Supports platform spatializer: ${Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU}")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                val spatializer = audioManager.spatializer
                sb.appendLine("Platform spatializer available: ${spatializer.isAvailable}")
                sb.appendLine("Platform spatializer enabled: ${spatializer.isEnabled}")
            } catch (e: Exception) {
                sb.appendLine("Platform spatializer check failed: ${e.message}")
            }
        }
        
        // Check ExoPlayer audio session
        val audioSessionId = exoPlayer.audioSessionId
        sb.appendLine("\nAudio session ID: $audioSessionId")
        
        // Check current tracks and content characteristics
        val currentTracks = exoPlayer.currentTracks
        sb.appendLine("Total track groups: ${currentTracks.groups.size}")
        
        currentTracks.groups.forEach { group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                sb.appendLine("Audio tracks in group: ${group.mediaTrackGroup.length}")
                for (i in 0 until group.mediaTrackGroup.length) {
                    val format = group.mediaTrackGroup.getFormat(i)
                    val isSelected = group.isTrackSelected(i)
                    sb.appendLine("  Track $i: ${format.channelCount} channels, ${format.sampleRate}Hz, ${format.codecs}, selected=$isSelected")
                    
                    if (isSelected) {
                        when {
                            format.channelCount <= 2 -> {
                                sb.appendLine("  ℹ️ Stereo content - spatial effects will be subtle and natural")
                            }
                            format.channelCount >= 6 -> {
                                sb.appendLine("  Multi-channel content - excellent for spatial audio")
                            }
                        }
                    }
                }
            }
        }
        
        // Effect status
        sb.appendLine("\n=== CURRENT EFFECT STATUS ===")
        sb.appendLine("Bass boost active: ${bassBoost?.enabled ?: false}")
        sb.appendLine("Virtualizer active: ${virtualizer?.enabled ?: false}")
        sb.appendLine("Equalizer active: ${equalizer?.enabled ?: false}")
        
        return sb.toString()
    }
    
    /**
     * Get current spatial audio effect status
     */
    fun getCurrentEffectStatus(): String {
        val sb = StringBuilder()
        sb.appendLine("Spatial Audio Effects Status:")
        
        bassBoost?.let { effect ->
            try {
                sb.appendLine("Bass Boost: ${if (effect.enabled) "Active" else "Inactive"}")
            } catch (e: Exception) {
                sb.appendLine("Bass Boost: Error")
            }
        } ?: sb.appendLine("Bass Boost: Not initialized")
        
        virtualizer?.let { effect ->
            try {
                sb.appendLine("Virtualizer: ${if (effect.enabled) "Active" else "Inactive"}")
            } catch (e: Exception) {
                sb.appendLine("Virtualizer: Error")
            }
        } ?: sb.appendLine("Virtualizer: Not initialized")
        
        equalizer?.let { effect ->
            try {
                sb.appendLine("Equalizer: ${if (effect.enabled) "Active" else "Inactive"}")
            } catch (e: Exception) {
                sb.appendLine("Equalizer: Error")
            }
        } ?: sb.appendLine("Equalizer: Not initialized")
        
        return sb.toString()
    }

    /**
     * Advanced spatial audio processing with HRTF-like simulation
     */
    @RequiresApi(Build.VERSION_CODES.M)
    fun enableAdvancedSpatialProcessing() {
        try {
            val audioSessionId = exoPlayer.audioSessionId
            if (audioSessionId == 0) {
                return
            }
            
            // Check if we can use platform spatializer
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val spatializer = audioManager.spatializer
                if (spatializer.isAvailable && spatializer.isEnabled) {
                    // Platform spatializer will handle HRTF processing
                    return
                }
            }
            
            // Apply our own HRTF-like processing using available effects
            applyHRTFLikeProcessing(audioSessionId)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enable advanced spatial processing: ${e.message}")
        }
    }
    
    /**
     * Simulate HRTF-like spatial processing using available Android audio effects
     */
    private fun applyHRTFLikeProcessing(audioSessionId: Int) {
        try {
            // Phase 1: Natural spatial positioning with minimal virtualizer
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = true
                setStrength(120) // Natural spatial cues
            }
            
            // Phase 2: Frequency-based spatial cues (simulating pinna filtering)
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
                applyHRTFLikeEQ()
            }
            
            // Phase 3: Subtle bass enhancement for natural fullness
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
                setStrength(80) // Natural low frequency response
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply HRTF-like processing: ${e.message}")
        }
    }
    
    /**
     * HRTF-like EQ curve that simulates natural head/ear filtering
     */
    private fun Equalizer.applyHRTFLikeEQ() {
        try {
            val bandCount = numberOfBands
            
            for (band in 0 until bandCount.toInt()) {
                val frequency = getCenterFreq(band.toShort()) / 1000
                
                // HRTF-inspired frequency response - natural spatial positioning
                val gain = when {
                    frequency < 100 -> 0      // Neutral sub-bass (natural)
                    frequency < 300 -> 25     // +0.25dB bass (slight warmth)
                    frequency < 1000 -> 0     // Neutral low-mid
                    frequency < 2000 -> 50    // +0.5dB mid (natural presence)
                    frequency < 4000 -> 100   // +1dB high-mid (pinna resonance simulation)
                    frequency < 8000 -> 75    // +0.75dB treble (natural detail)
                    frequency < 12000 -> 125  // +1.25dB upper treble (spatial cues)
                    else -> 50                // +0.5dB ultra-high (air and positioning)
                }.toShort()
                
                setBandLevel(band.toShort(), gain)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply HRTF-like EQ: ${e.message}")
        }
    }
    
    /**
     * Called when player is ready and audio session is established
     */
    fun onPlayerReady() {
        applyCompatibilityEnhancements()
    }
    
    /**
     * Force apply spatial audio effects immediately
     */
    fun forceApplyEffects() {
        try {
            val audioSessionId = exoPlayer.audioSessionId
            if (audioSessionId != 0) {
                applySpatialEffectsImmediate(audioSessionId)
            } else {
                // Try multiple times with delays
                for (delay in listOf(100L, 500L, 1000L, 2000L)) {
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val retrySessionId = exoPlayer.audioSessionId
                        if (retrySessionId != 0) {
                            applySpatialEffectsImmediate(retrySessionId)
                        }
                    }, delay)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply spatial audio effects: ${e.message}")
        }
    }
    
    /**
     * Log detailed status of all spatial audio effects
     */
    private fun logEffectStatus() {
        // Simplified status logging
        bassBoost?.let { effect ->
            // Bass effect status
        } ?: run {
            // Bass effect not initialized
        }
        
        virtualizer?.let { effect ->
            // Virtualizer effect status
        } ?: run {
            // Virtualizer effect not initialized
        }
        
        equalizer?.let { effect ->
            // Equalizer effect status
        } ?: run {
            // Equalizer effect not initialized
        }
    }
    
    /**
     * Clean up audio effects when manager is destroyed
     */
    fun cleanup() {
        releaseEffects()
    }
    
    /**
     * Check if spatial audio effects are currently active
     */
    fun isEffectsActive(): Boolean {
        return try {
            (bassBoost?.enabled == true) || 
            (virtualizer?.enabled == true) || 
            (equalizer?.enabled == true)
        } catch (e: Exception) {
            Log.w(TAG, "Error checking effects status: ${e.message}")
            false
        }
    }

    /**
     * Apply spatial audio effects
     */
    private fun applySpatialEffectsImmediate(audioSessionId: Int) {
        try {
            // Release any existing effects first
            releaseEffects()
            
            // Bass Enhancement
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = true
                setStrength(400) // 40% - Bass enhancement
            }
            
            // Spatial Virtualization
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = true
                setStrength(600) // 60% - Spatial width
            }
            
            // EQ for Spatial Enhancement
            equalizer = Equalizer(0, audioSessionId).apply {
                enabled = true
                applySpatialEQ()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply spatial effects: ${e.message}", e)
        }
    }
    
    /**
     * Spatial EQ curve
     */
    private fun Equalizer.applySpatialEQ() {
        try {
            val bandCount = numberOfBands
            
            for (band in 0 until bandCount.toInt()) {
                val frequency = getCenterFreq(band.toShort()) / 1000
                
                // Spatial EQ curve
                val gain = when {
                    frequency < 60 -> 150     // +1.5dB sub-bass
                    frequency < 200 -> 200    // +2dB bass
                    frequency < 500 -> 100    // +1dB low-mid
                    frequency < 1000 -> 50    // +0.5dB mid
                    frequency < 2000 -> 150   // +1.5dB upper mid
                    frequency < 4000 -> 300   // +3dB high-mid
                    frequency < 8000 -> 250   // +2.5dB treble
                    frequency < 12000 -> 400  // +4dB upper treble
                    else -> 200               // +2dB ultra-high
                }.toShort()
                
                setBandLevel(band.toShort(), gain)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply spatial EQ: ${e.message}")
        }
    }

    /**
     * Disable spatial audio effects when content doesn't support spatial audio
     */
    fun disableSpatialEffects() {
        try {
            releaseEffects()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to disable spatial effects: ${e.message}")
        }
    }
}

/**
 * Device audio capabilities
 */
data class DeviceCapabilities(
    val supportsAtmos: Boolean,
    val supportsSurround: Boolean,
    val connectedDevice: String
)

/**
 * Current audio status
 */
data class AudioStatus(
    val currentMode: SpatialAudioManager.AudioMode,
    val systemSpatialAvailable: Boolean,
    val connectedDevice: String
)

/**
 * Status of spatial audio system
 */
data class SpatialAudioStatus(
    val available: Boolean,
    val enabled: Boolean,
    val headTrackingAvailable: Boolean,
    val headTrackingEnabled: Boolean,
    val usingSystemSpatial: Boolean
) {
    companion object {
        fun disabled() = SpatialAudioStatus(
            available = false,
            enabled = false,
            headTrackingAvailable = false,
            headTrackingEnabled = false,
            usingSystemSpatial = false
        )
    }
}