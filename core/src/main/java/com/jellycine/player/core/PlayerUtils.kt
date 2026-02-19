package com.jellycine.player.core

import android.content.Context
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.jellycine.player.audio.SpatialAudioManager
import com.jellycine.player.audio.SpatializerHelper
import com.jellycine.player.video.HardwareAcceleration
import com.jellycine.player.video.HdrCapabilityManager
import com.jellycine.player.preferences.PlayerPreferences

/**
 * Player utility functions with spatial audio effects
 */
@UnstableApi
object PlayerUtils {
    private const val MAX_MULTICHANNEL_AUDIO_CHANNELS = 16
    
    // Spatial audio manager instance
    private var spatialAudioManager: SpatialAudioManager? = null
    
    /**
     * Create ExoPlayer instance with optimal settings including spatial audio support and HDR fallback
     * Spatial audio enabled based on user preferences
     * HDR/Dolby Vision automatically falls back to compatible formats (no more black screens!)
     */
    @UnstableApi
    fun createPlayer(context: Context): ExoPlayer {
        val playerPreferences = PlayerPreferences(context)
        val spatialAudioEnabled = playerPreferences.isSpatialAudioEnabled()
        val hdrEnabled = playerPreferences.isHdrEnabled()
        
        try {
            // Check device HDR capabilities and user preference
            val deviceHdrSupport = HdrCapabilityManager.getDeviceHdrSupport(context)
            
            // Only use HDR capabilities if user has enabled HDR
            val effectiveHdrSupport = if (hdrEnabled) {
                deviceHdrSupport
            } else {
                HdrCapabilityManager.HdrSupport.SDR
            }
            
            val spatializerHelper = SpatializerHelper(context)
            val canSpatializeMultiChannel = spatializerHelper.canSpatializeMultiChannel()
            
            val trackSelector = DefaultTrackSelector(context).apply {
                parameters = buildUponParameters()
                    .setMaxAudioChannelCount(MAX_MULTICHANNEL_AUDIO_CHANNELS)
                    .setPreferredAudioLanguages("original", "und")
                    .apply {
                        when (effectiveHdrSupport) {
                            HdrCapabilityManager.HdrSupport.DOLBY_VISION -> {
                                // Device supports Dolby Vision and user enabled HDR, allow all HDR formats
                            }
                            HdrCapabilityManager.HdrSupport.HDR10_PLUS,
                            HdrCapabilityManager.HdrSupport.HDR10 -> {
                                Log.d("PlayerUtils", "Configuring for HDR playback")
                            }
                            else -> {
                                Log.d("PlayerUtils", "Configuring for SDR playback (HDR disabled or not supported)")
                            }
                        }
                    }
                    .build()
            }
            
            Log.d(
                "PlayerUtils", 
                "Track selector configured with max channels: $MAX_MULTICHANNEL_AUDIO_CHANNELS " +
                    "(spatial enabled: $spatialAudioEnabled, device multi-channel: $canSpatializeMultiChannel)"
            )

            // Configure AudioAttributes based on spatial audio preference
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            
            Log.d("PlayerUtils", "Audio attributes configured. Spatial audio: $spatialAudioEnabled")

            // Create hardware acceleration
            val renderersFactory = createHardwareAcceleration(context)
            
            val playerBuilder = ExoPlayer.Builder(context)
                .setRenderersFactory(renderersFactory) // Use custom factory with fallback support
                .setTrackSelector(trackSelector)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
            
            // Apply battery optimization if enabled
            if (playerPreferences.isBatteryOptimizationEnabled()) {
                Log.d("PlayerUtils", "Applying battery optimization settings")
                applyBatteryOptimization(playerBuilder, playerPreferences)
            }

            Log.d("PlayerUtils", "ExoPlayer builder configured with HDR fallback support")
            
            val player = playerBuilder.build()

            // Initialize spatial audio manager only if enabled by user
            if (spatialAudioEnabled) {
                spatialAudioManager = SpatialAudioManager(context, player)
            } else {
                spatialAudioManager = null
            }
            
            return player
            
        } catch (e: Exception) {
            Log.e("PlayerUtils", "Player creation failed", e)
            throw e // Re-throw to let caller handle it
        }
    }
    
    /**
     * Apply battery optimization settings to the player
     */
    @UnstableApi
    private fun applyBatteryOptimization(
        playerBuilder: ExoPlayer.Builder,
        playerPreferences: PlayerPreferences
    ) {
        try {
            // Reduce buffer sizes for lower memory usage
            if (playerPreferences.isBufferOptimizationEnabled()) {
                val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        15000,
                        30000,
                        1500,
                        5000
                    )
                    .setTargetBufferBytes(-1)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
                    
                playerBuilder.setLoadControl(loadControl)
                Log.d("PlayerUtils", "Applied reduced buffer sizes for battery optimization")
            }
            
            // Set lower wake mode for battery saving
            playerBuilder.setWakeMode(C.WAKE_MODE_NONE)
            
        } catch (e: Exception) {
        }
    }
    
    /**
     * Create enhanced hardware acceleration
     */
    @UnstableApi
    private fun createHardwareAcceleration(context: Context): HardwareAcceleration {
        return HardwareAcceleration(context)
    }
    
    /**
     * Get current spatial audio effects status - for info display
     */
    fun getSpatialAudioStatus(context: Context): String {
        return spatialAudioManager?.getCurrentEffectStatus() ?: "Spatial audio not initialized"
    }
    
    /**
     * Configure ExoPlayer spatial audio based on content format and user preferences
     * This method allows dynamic control of spatial audio during playback
     */
    @UnstableApi
    fun configureSpatialAudio(exoPlayer: ExoPlayer, context: Context, enabled: Boolean) {
        
        try {
            // Get current track selection parameters
            val currentParams = exoPlayer.trackSelectionParameters
            
            // Content-format-based spatial audio selection
            val newParams = if (enabled) {
                Log.d("PlayerUtils", "Enabling spatial audio - multi-channel tracks")
                // Allow high-channel content for spatial processing
                currentParams.buildUpon()
                    .setMaxAudioChannelCount(MAX_MULTICHANNEL_AUDIO_CHANNELS)
                    .setPreferredAudioLanguages("original", "und")
                    .build()
            } else {
                currentParams.buildUpon()
                    .setMaxAudioChannelCount(MAX_MULTICHANNEL_AUDIO_CHANNELS)
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
                    
                    // Apply compatibility enhancements and effects only when enabled
                    manager.applyCompatibilityEnhancements()
                    manager.forceApplyEffects()
                    
                    // Schedule player ready callback
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        manager.onPlayerReady()
                    }, 1000)
                } else {
                    manager.configureAudioForContent(
                        contentHasDolbyAtmos = false,
                        contentHasSurround = false
                    )
                    manager.disableSpatialEffects()
                }
                
                val audioStatus = manager.getAudioStatus()
            }
            
            Log.d("PlayerUtils", "Spatial audio configuration completed successfully")
            
        } catch (e: Exception) {
            Log.e("PlayerUtils", "Failed to configure spatial audio", e)
            throw e
        }
    }
    
    /**
     * Configure spatial audio based on actual content detection results and user preferences
     * Respects user's spatial audio toggle setting
     */
    @UnstableApi
    fun configureSpatialAudioForContent(
        exoPlayer: ExoPlayer, 
        context: Context, 
        spatializationResult: com.jellycine.detail.SpatializationResult?
    ) {
        Log.d("PlayerUtils", "Configuring spatial audio based on content analysis")
        
        // First check user preference
        val playerPreferences = PlayerPreferences(context)
        val userSpatialAudioEnabled = playerPreferences.isSpatialAudioEnabled()
        
        if (!userSpatialAudioEnabled) {
            Log.d("PlayerUtils", "Spatial audio disabled by user preference - skipping content-based configuration")
            spatialAudioManager?.disableSpatialEffects()
            return
        }
        
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
                .setMaxAudioChannelCount(MAX_MULTICHANNEL_AUDIO_CHANNELS)
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
                
                // Only apply effects and enhancements when both user preference AND content compatibility are met
                if (shouldApplyEffects) {
                    manager.applyCompatibilityEnhancements()
                    manager.forceApplyEffects()
                    
                    // Schedule player ready callback
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        manager.onPlayerReady()
                    }, 1000)
                    
                    val audioStatus = manager.getAudioStatus()
                    Log.d("PlayerUtils", "Spatial audio configured for compatible content: $audioStatus")
                } else {
                    Log.d("PlayerUtils", "Spatial audio effects disabled - not applying")
                }
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
     * Get HDR capability information for display in UI
     */
    fun getHdrCapabilityInfo(context: Context): String {
        return HdrCapabilityManager.getDetailedHdrInfo(context)
    }
    
    /**
     * Analyze video format and get fallback information
     */
    fun analyzeVideoFormatForPlayback(
        context: Context, 
        mimeType: String?, 
        codec: String?, 
        colorInfo: String? = null
    ): String {
        val videoFormat = HdrCapabilityManager.analyzeVideoFormat(mimeType, codec, colorInfo)
        val deviceSupport = HdrCapabilityManager.getDeviceHdrSupport(context)
        val bestFormat = HdrCapabilityManager.getBestPlayableFormat(context, videoFormat)
        
        return HdrCapabilityManager.getPlaybackFormatDescription(deviceSupport, videoFormat, bestFormat)
    }
    
    /**
     * Check if HDR content will play without black screens on this device
     */
    fun willHdrContentPlayCorrectly(context: Context, mimeType: String?, codec: String?): Boolean {
        val videoFormat = HdrCapabilityManager.analyzeVideoFormat(mimeType, codec)
        val bestFormat = HdrCapabilityManager.getBestPlayableFormat(context, videoFormat)
        
        // If we can play the content in some format (even fallback), it won't be a black screen
        return bestFormat.mimeType.isNotEmpty()
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

    /**
     * Get available audio tracks from ExoPlayer
     */
    @UnstableApi
    fun getAvailableAudioTracks(exoPlayer: ExoPlayer): List<AudioTrackInfo> {
        val tracks = mutableListOf<AudioTrackInfo>()
        val currentTracks = exoPlayer.currentTracks

        currentTracks.groups.forEach { group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.mediaTrackGroup.length) {
                    val format = group.mediaTrackGroup.getFormat(i)
                    val isSelected = group.isTrackSelected(i)

                    val language = format.language ?: "Unknown"
                    val label = when {
                        language.isNotEmpty() && format.label != null -> "${format.label} ($language)"
                        language.isNotEmpty() -> language
                        format.label != null -> format.label!!
                        else -> "Audio Track ${i + 1}"
                    }

                    tracks.add(
                        AudioTrackInfo(
                            id = "audio_${group.mediaTrackGroup.getFormat(i).id ?: i}",
                            label = label,
                            language = language,
                            channelCount = format.channelCount,
                            codec = format.codecs
                        )
                    )
                }
            }
        }

        return tracks
    }

    /**
     * Get available subtitle tracks from ExoPlayer
     */
    @UnstableApi
    fun getAvailableSubtitleTracks(exoPlayer: ExoPlayer): List<SubtitleTrackInfo> {
        val tracks = mutableListOf<SubtitleTrackInfo>()

        tracks.add(
            SubtitleTrackInfo(
                id = "off",
                label = "Off",
                language = null,
                isForced = false,
                isDefault = false
            )
        )

        val currentTracks = exoPlayer.currentTracks
        currentTracks.groups.forEach { group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.mediaTrackGroup.length) {
                    val format = group.mediaTrackGroup.getFormat(i)
                    val language = format.language ?: "Unknown"
                    val isForced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
                    val isDefault = format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0
                    val label = when {
                        language.isNotEmpty() && format.label != null -> "${format.label} ($language)"
                        language.isNotEmpty() -> language
                        format.label != null -> format.label!!
                        else -> "Subtitle Track ${i + 1}"
                    }
                    tracks.add(
                        SubtitleTrackInfo(
                            id = "subtitle_${group.mediaTrackGroup.getFormat(i).id ?: i}",
                            label = label,
                            language = language,
                            isForced = isForced,
                            isDefault = isDefault
                        )
                    )
                }
            }
        }
        return tracks
    }

    /**
     * Get available video tracks from ExoPlayer
     */
    @UnstableApi
    fun getAvailableVideoTracks(exoPlayer: ExoPlayer): List<VideoTrackInfo> {
        val tracks = mutableListOf<VideoTrackInfo>()
        val currentTracks = exoPlayer.currentTracks

        currentTracks.groups.forEach { group ->
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.mediaTrackGroup.length) {
                    val format = group.mediaTrackGroup.getFormat(i)

                    val label = when {
                        format.height > 0 -> "${format.height}p"
                        format.width > 0 && format.height > 0 -> "${format.width}x${format.height}"
                        format.label != null -> format.label!!
                        else -> "Video Track ${i + 1}"
                    }

                    tracks.add(
                        VideoTrackInfo(
                            id = "video_${group.mediaTrackGroup.getFormat(i).id ?: i}",
                            label = label,
                            width = format.width,
                            height = format.height,
                            codec = format.codecs
                        )
                    )
                }
            }
        }
        return tracks
    }

    /**
     * Get currently selected audio track
     */
    @UnstableApi
    fun getCurrentAudioTrack(exoPlayer: ExoPlayer): AudioTrackInfo? {
        val currentTracks = exoPlayer.currentTracks

        currentTracks.groups.forEach { group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.mediaTrackGroup.length) {
                    if (group.isTrackSelected(i)) {
                        val format = group.mediaTrackGroup.getFormat(i)
                        val language = format.language ?: "Unknown"
                        val label = when {
                            language.isNotEmpty() && format.label != null -> "${format.label} ($language)"
                            language.isNotEmpty() -> language
                            format.label != null -> format.label!!
                            else -> "Audio Track ${i + 1}"
                        }

                        return AudioTrackInfo(
                            id = "audio_${group.mediaTrackGroup.getFormat(i).id ?: i}",
                            label = label,
                            language = language,
                            channelCount = format.channelCount,
                            codec = format.codecs
                        )
                    }
                }
            }
        }
        return null
    }

    /**
     * Get currently selected subtitle track
     */
    @UnstableApi
    fun getCurrentSubtitleTrack(exoPlayer: ExoPlayer): SubtitleTrackInfo? {
        val currentTracks = exoPlayer.currentTracks

        currentTracks.groups.forEach { group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.mediaTrackGroup.length) {
                    if (group.isTrackSelected(i)) {
                        val format = group.mediaTrackGroup.getFormat(i)
                        val language = format.language ?: "Unknown"
                        val isForced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
                        val isDefault = format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0

                        val label = when {
                            language.isNotEmpty() && format.label != null -> "${format.label} ($language)"
                            language.isNotEmpty() -> language
                            format.label != null -> format.label!!
                            else -> "Subtitle Track ${i + 1}"
                        }

                        return SubtitleTrackInfo(
                            id = "subtitle_${group.mediaTrackGroup.getFormat(i).id ?: i}",
                            label = label,
                            language = language,
                            isForced = isForced,
                            isDefault = isDefault
                        )
                    }
                }
            }
        }

        return SubtitleTrackInfo(
            id = "off",
            label = "Off",
            language = null,
            isForced = false,
            isDefault = false
        )
    }

    /**
     * Select audio track by ID
     */
    @UnstableApi
    fun selectAudioTrack(exoPlayer: ExoPlayer, trackId: String) {
        try {
            val trackSelector = exoPlayer.trackSelector as? DefaultTrackSelector ?: return
            val currentTracks = exoPlayer.currentTracks

            currentTracks.groups.forEach { group ->
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until group.mediaTrackGroup.length) {
                        val format = group.mediaTrackGroup.getFormat(i)
                        val currentId = "audio_${format.id ?: i}"

                        if (currentId == trackId) {
                            val override = TrackSelectionOverride(
                                group.mediaTrackGroup,
                                listOf(i)
                            )

                            val newParams = trackSelector.parameters
                                .buildUpon()
                                .addOverride(override)
                                .build()

                            trackSelector.setParameters(newParams)

                            Log.d("PlayerUtils", "Selected audio track: $trackId")
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerUtils", "Failed to select audio track: $trackId", e)
        }
    }

    /**
     * Select subtitle track by ID
     */
    @UnstableApi
    fun selectSubtitleTrack(exoPlayer: ExoPlayer, trackId: String) {
        try {
            val trackSelector = exoPlayer.trackSelector as? DefaultTrackSelector ?: return

            if (trackId == "off") {
                val newParams = trackSelector.parameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()

                trackSelector.setParameters(newParams)
                Log.d("PlayerUtils", "Disabled subtitles")
                return
            }

            val currentTracks = exoPlayer.currentTracks

            currentTracks.groups.forEach { group ->
                if (group.type == C.TRACK_TYPE_TEXT) {
                    for (i in 0 until group.mediaTrackGroup.length) {
                        val format = group.mediaTrackGroup.getFormat(i)
                        val currentId = "subtitle_${format.id ?: i}"

                        if (currentId == trackId) {
                            val override = TrackSelectionOverride(
                                group.mediaTrackGroup,
                                listOf(i)
                            )

                            val newParams = trackSelector.parameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .addOverride(override)
                                .build()

                            trackSelector.setParameters(newParams)
                            Log.d("PlayerUtils", "Selected subtitle track: $trackId")
                            return
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PlayerUtils", "Failed to select subtitle track: $trackId", e)
        }
    }
}
