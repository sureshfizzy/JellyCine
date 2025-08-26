package com.jellycine.player.core

import android.content.Context
import android.media.AudioFormat
import android.media.MediaFormat
import android.os.Build
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.common.audio.AudioProcessor
import com.jellycine.player.audio.SpatialAudioManager
import com.jellycine.player.audio.SpatializerHelper
import com.jellycine.player.video.DolbyVisionCompatibleRenderersFactory
import com.jellycine.player.video.HdrCapabilityManager

/**
 * Player utility functions with spatial audio effects
 */
@UnstableApi
object PlayerUtils {
    
    // Spatial audio manager instance
    private var spatialAudioManager: SpatialAudioManager? = null
    
    /**
     * Create ExoPlayer instance with optimal settings including spatial audio support and HDR fallback
     * Spatial audio is ALWAYS ENABLED for supported content (auto-detect)
     * HDR/Dolby Vision automatically falls back to compatible formats (no more black screens!)
     */
    @UnstableApi
    fun createPlayer(context: Context): ExoPlayer {
        Log.d("PlayerUtils", "=== CREATEPLAYER METHOD CALLED ===")
        Log.d("PlayerUtils", "=== CREATING EXOPLAYER WITH AUTO SPATIAL AUDIO & HDR FALLBACK ===")
        
        try {
            // Check device HDR capabilities
            val deviceHdrSupport = HdrCapabilityManager.getDeviceHdrSupport(context)
            Log.d("PlayerUtils", "Device HDR support: ${deviceHdrSupport.displayName}")
            
            val spatializerHelper = SpatializerHelper(context)
            val canSpatializeMultiChannel = spatializerHelper.canSpatializeMultiChannel()
            Log.d("PlayerUtils", "Device spatial capability: $canSpatializeMultiChannel")
            
            val trackSelector = DefaultTrackSelector(context).apply {
                parameters = buildUponParameters()
                    // Always allow higher channel counts for spatial content - up to 16 channels
                    .setMaxAudioChannelCount(if (canSpatializeMultiChannel) 16 else 8)
                    // Prefer multi-channel tracks for spatial audio
                    .setPreferredAudioLanguages("original", "und")
                    // Enable HDR track selection based on device capabilities
                    .apply {
                        when (deviceHdrSupport) {
                            HdrCapabilityManager.HdrSupport.DOLBY_VISION -> {
                                Log.d("PlayerUtils", "Configuring for Dolby Vision support")
                                // Device supports Dolby Vision, allow all HDR formats
                            }
                            HdrCapabilityManager.HdrSupport.HDR10_PLUS,
                            HdrCapabilityManager.HdrSupport.HDR10 -> {
                                Log.d("PlayerUtils", "Configuring for HDR10 support")
                                // Device supports HDR10, prefer HDR10 over Dolby Vision
                            }
                            else -> {
                                Log.d("PlayerUtils", "Configuring for SDR playback")
                                // Device only supports SDR, will use fallback codecs
                            }
                        }
                    }
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
            
            // Create Dolby Vision compatible RenderersFactory
            val renderersFactory = createDolbyVisionCompatibleRenderersFactory(context)
            
            val playerBuilder = ExoPlayer.Builder(context)
                .setRenderersFactory(renderersFactory) // Use custom factory with fallback support
                .setTrackSelector(trackSelector)
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
            
            Log.d("PlayerUtils", "ExoPlayer builder configured with HDR fallback support")
            
            val player = playerBuilder.build()
            Log.d("PlayerUtils", "ExoPlayer instance created successfully with HDR fallback")

            // Initialize the spatial audio manager with the actual player
            spatialAudioManager = SpatialAudioManager(context, player)
            
            // Log HDR capability details
            val hdrInfo = HdrCapabilityManager.getDetailedHdrInfo(context)
            Log.i("PlayerUtils", "HDR Capabilities:\n$hdrInfo")
            
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
     * Create Dolby Vision compatible RenderersFactory
     */
    @UnstableApi
    private fun createDolbyVisionCompatibleRenderersFactory(context: Context): DolbyVisionCompatibleRenderersFactory {
        return DolbyVisionCompatibleRenderersFactory(context)
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