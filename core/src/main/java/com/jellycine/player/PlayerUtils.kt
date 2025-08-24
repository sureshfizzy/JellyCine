package com.jellycine.player

import android.content.Context
import android.media.AudioFormat
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.common.audio.AudioProcessor
import com.jellycine.player.SpatialAudioManager

/**
 * Player utility functions with spatial audio effects
 */
@UnstableApi
object PlayerUtils {
    
    // Spatial audio manager instance
    private var spatialAudioManager: SpatialAudioManager? = null
    
    /**
     * Create ExoPlayer instance with optimal settings including spatial audio support
     * Spatial audio is ALWAYS ENABLED for supported content (auto-detect)
     */
    @UnstableApi
    fun createPlayer(context: Context): ExoPlayer {
        Log.d("PlayerUtils", "=== CREATEPLAYER METHOD CALLED ===")
        Log.d("PlayerUtils", "=== CREATING EXOPLAYER WITH AUTO SPATIAL AUDIO ===")
        
        try {
        val spatializerHelper = SpatializerHelper(context)
        val canSpatializeMultiChannel = spatializerHelper.canSpatializeMultiChannel()
        Log.d("PlayerUtils", "Device spatial capability: $canSpatializeMultiChannel")
        
        val trackSelector = DefaultTrackSelector(context).apply {
            parameters = buildUponParameters()
                // Always allow higher channel counts for spatial content - up to 16 channels
                .setMaxAudioChannelCount(if (canSpatializeMultiChannel) 16 else 8)
                // Prefer multi-channel tracks for spatial audio
                .setPreferredAudioLanguages("original", "und")
                .build()
        }
        
        Log.d("PlayerUtils", "Track selector configured with max channels: ${if (canSpatializeMultiChannel) 16 else 8}")

        // Configure AudioAttributes for spatial audio
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        
        Log.d("PlayerUtils", "Audio attributes configured for spatial audio")

        // No audio processors needed
        val audioProcessors = emptyArray<AudioProcessor>()
        
        // Configure audio sink
        val audioSink = DefaultAudioSink.Builder()
            .setAudioProcessors(audioProcessors)
            .build()
        
        val playerBuilder = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
        
        Log.d("PlayerUtils", "ExoPlayer builder configured")
        
        val player = playerBuilder.build()
        Log.d("PlayerUtils", "ExoPlayer instance created successfully")

        // Initialize the spatial audio manager with the actual player
        spatialAudioManager = SpatialAudioManager(context, player)
        
        return player
        
        } catch (e: Exception) {
            Log.e("PlayerUtils", "=== PLAYER CREATION FAILED ===", e)
            Log.e("PlayerUtils", "Exception type: ${e.javaClass.simpleName}")
            Log.e("PlayerUtils", "Exception message: ${e.message}")
            Log.e("PlayerUtils", "Exception cause: ${e.cause}")
            e.printStackTrace()
            throw e // Re-throw to let caller handle it
        }
    }
    
    /**
     * Get current spatial audio effects status - for info display
     */
    fun getSpatialAudioStatus(context: Context): String {
        return spatialAudioManager?.getCurrentEffectStatus() ?: "Spatial audio not initialized"
    }
    
    /**
     * Configure ExoPlayer spatial audio based on content format (auto-detection)
     * This method is now primarily for internal use since spatial audio is always enabled
     */
    @UnstableApi
    fun configureSpatialAudio(exoPlayer: ExoPlayer, context: Context, enabled: Boolean) {
        Log.d("PlayerUtils", "Configuring spatial audio: $enabled")
        
        try {
            // Get current track selection parameters
            val currentParams = exoPlayer.trackSelectionParameters
            
            // Content-format-based spatial audio selection
            val newParams = if (enabled) {
                Log.d("PlayerUtils", "Enabling spatial audio - multi-channel tracks")
                // Allow high-channel content for spatial processing
                currentParams.buildUpon()
                    .setMaxAudioChannelCount(16) // Support Dolby Atmos
                    .setPreferredAudioLanguages("original", "und")
                    .build()
            } else {
                Log.d("PlayerUtils", "Disabling spatial audio - stereo mode")
                // Limit to stereo for non-spatial experience
                currentParams.buildUpon()
                    .setMaxAudioChannelCount(2)
                    .build()
            }
            
            // Apply new track selection parameters
            exoPlayer.trackSelectionParameters = newParams
            Log.d("PlayerUtils", "Track selection updated")
            
            // Configure AudioAttributes for optimal spatial audio
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
                
            exoPlayer.setAudioAttributes(audioAttributes, true)
            Log.d("PlayerUtils", "Audio attributes updated")
            
            // Configure spatial audio
            spatialAudioManager?.let { manager ->
                if (enabled) {
                    manager.configureAudioForContent(
                        contentHasDolbyAtmos = true,
                        contentHasSurround = true
                    )
                } else {
                    manager.configureAudioForContent(
                        contentHasDolbyAtmos = false,
                        contentHasSurround = false
                    )
                }
                
                // Apply compatibility enhancements
                manager.applyCompatibilityEnhancements()
                
                // Apply effects
                manager.forceApplyEffects()
                
                // Schedule player ready callback
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    manager.onPlayerReady()
                }, 1000)
                
                val audioStatus = manager.getAudioStatus()
            }
            
            Log.d("PlayerUtils", "Spatial audio configuration completed successfully")
            
        } catch (e: Exception) {
            Log.e("PlayerUtils", "Failed to configure spatial audio", e)
            throw e
        }
    }
    
    /**
     * Configure spatial audio based on actual content detection results
     * Content-aware approach: effects work for compatible formats regardless of device limitations
     */
    @UnstableApi
    fun configureSpatialAudioForContent(
        exoPlayer: ExoPlayer, 
        context: Context, 
        spatializationResult: com.jellycine.detail.SpatializationResult?
    ) {
        Log.d("PlayerUtils", "Configuring spatial audio based on content analysis")
        
        try {
            val spatialFormat = spatializationResult?.spatialFormat ?: ""
            val contentSupportsSpatialization = spatializationResult?.canSpatialize == true
            
            // Enhanced content format detection
            val hasCompatibleAudioFormat = spatialFormat.lowercase().let { format ->
                format.contains("dolby atmos") || format.contains("atmos") ||
                format.contains("dts:x") || format.contains("dts-x") ||
                format.contains("5.1") || format.contains("7.1") ||
                format.contains("360 reality") || format.contains("mpeg-h") ||
                format.contains("auro-3d") ||
                (format.contains("ch") && spatialFormat.contains(Regex("\\d+ch"))) ||
                (format.contains("object") && format.contains("audio"))
            }
            
            // Content-first approach: Enable if format is compatible OR detection succeeded
            val shouldApplyEffects = hasCompatibleAudioFormat || contentSupportsSpatialization
            
            Log.d("PlayerUtils", "Content format: '$spatialFormat'")
            Log.d("PlayerUtils", "Compatible audio format: $hasCompatibleAudioFormat")
            Log.d("PlayerUtils", "Content supports spatialization: $contentSupportsSpatialization")
            Log.d("PlayerUtils", "Should apply effects: $shouldApplyEffects")
            
            if (!shouldApplyEffects) {
                Log.d("PlayerUtils", "Content format not compatible with spatial audio - disabling effects")
                spatialAudioManager?.disableSpatialEffects()
                return
            }
            
            // Analyze specific spatial audio format characteristics
            val hasDolbyAtmos = spatialFormat.contains("Dolby Atmos", ignoreCase = true) ||
                               spatialFormat.contains("Atmos", ignoreCase = true) ||
                               spatialFormat.contains("E-AC-3", ignoreCase = true) ||
                               spatialFormat.contains("TrueHD", ignoreCase = true)
            
            val hasDTSX = spatialFormat.contains("DTS:X", ignoreCase = true) ||
                         spatialFormat.contains("DTS-X", ignoreCase = true)
            
            val hasSurroundChannels = spatialFormat.contains("5.1") || 
                                    spatialFormat.contains("7.1") ||
                                    spatialFormat.contains("ch") ||
                                    spatialFormat.contains("Object", ignoreCase = true)
            
            Log.d("PlayerUtils", "Content analysis - Dolby Atmos: $hasDolbyAtmos, DTS:X: $hasDTSX, Surround: $hasSurroundChannels")
            
            // Configure track selection for spatial content
            val currentParams = exoPlayer.trackSelectionParameters
            val newParams = currentParams.buildUpon()
                .setMaxAudioChannelCount(if (shouldApplyEffects) 16 else 2)
                .setPreferredAudioLanguages("original", "und")
                .build()
            exoPlayer.trackSelectionParameters = newParams
            
            // Configure audio attributes for spatial content
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            exoPlayer.setAudioAttributes(audioAttributes, true)
            
            // Apply spatial audio effects for compatible content
            spatialAudioManager?.let { manager ->
                manager.configureAudioForContent(
                    contentHasDolbyAtmos = hasDolbyAtmos,
                    contentHasSurround = hasSurroundChannels || hasDTSX
                )
                
                // Apply effects
                manager.applyCompatibilityEnhancements()
                manager.forceApplyEffects()
                
                // Schedule player ready callback
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    manager.onPlayerReady()
                }, 1000)
                
                val audioStatus = manager.getAudioStatus()
                Log.d("PlayerUtils", "Spatial audio configured for compatible content: $audioStatus")
            }
            
            Log.d("PlayerUtils", "Content-aware spatial audio configuration completed")
            
        } catch (e: Exception) {
            Log.e("PlayerUtils", "Failed to configure spatial audio for content", e)
            throw e
        }
    }

    /**
     * Check if spatial audio is currently active and get details
     */
    fun isSpatialAudioActive(): Boolean {
        return spatialAudioManager?.isEffectsActive() ?: false
    }

    /**
     * Get detailed spatial audio information for UI display
     */
    fun getSpatialAudioInfo(): String {
        return spatialAudioManager?.let { manager ->
            val effectStatus = manager.getCurrentEffectStatus()
            val isActive = manager.isEffectsActive()

            if (isActive) {
                "Spatial Audio: Active\n$effectStatus"
            } else {
                "Spatial Audio: Inactive\n$effectStatus"
            }
        } ?: "Spatial Audio: Not Available"
    }

    /**
     * Format time in MM:SS format
     */
    fun formatTime(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    /**
     * Format time with hours if needed
     */
    fun formatTimeWithHours(timeMs: Long): String {
        val totalSeconds = timeMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}
