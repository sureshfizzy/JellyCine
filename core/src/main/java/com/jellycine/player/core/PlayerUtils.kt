package com.jellycine.player.core

import android.content.Context
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.jellycine.player.video.HardwareAcceleration
import com.jellycine.player.video.HdrCapabilityManager
import com.jellycine.player.preferences.PlayerPreferences

/**
 * Player utility functions with device spatializer support.
 */
@UnstableApi
object PlayerUtils {
    private fun buildTrackId(
        prefix: String,
        groupIndex: Int,
        trackIndex: Int
    ): String {
        return "${prefix}_${groupIndex}_${trackIndex}"
    }

    private fun languageCode(language: String?): String? {
        return language?.takeIf {
            it.isNotBlank() &&
                !it.equals("und", ignoreCase = true) &&
                !it.equals("unknown", ignoreCase = true)
        }
    }
    
    /**
     * Create ExoPlayer instance with optimal settings including spatial audio support and HDR fallback
     * Spatial audio follows system device spatializer state
     * HDR/Dolby Vision automatically falls back to compatible formats (no more black screens!)
     */
    @UnstableApi
    fun createPlayer(context: Context): ExoPlayer {
        val playerPreferences = PlayerPreferences(context)

        try {
            val trackSelector = DefaultTrackSelector(context)

            // Configure AudioAttributes to let system spatializer decide when active
            val audioAttributesBuilder = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioAttributesBuilder.setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_AUTO)
            }
            val audioAttributes = audioAttributesBuilder.build()

            // Create hardware acceleration
            val renderersFactory = HardwareAcceleration(context)
            
            val playerBuilder = ExoPlayer.Builder(context)
                .setRenderersFactory(renderersFactory) // Use custom factory with fallback support
                .setTrackSelector(trackSelector)
                .setLoadControl(createLoadControl(playerPreferences))
                .setAudioAttributes(audioAttributes, true)
                .setHandleAudioBecomingNoisy(true)
                .setWakeMode(C.WAKE_MODE_LOCAL)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
            
            // Apply battery optimization if enabled
            if (playerPreferences.isBatteryOptimizationEnabled()) {
                Log.d("PlayerUtils", "Applying battery optimization settings")
                applyBatteryOptimization(playerBuilder)
            }
            return playerBuilder.build()
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
        playerBuilder: ExoPlayer.Builder
    ) {
        try {
            // Set lower wake mode for battery saving
            playerBuilder.setWakeMode(C.WAKE_MODE_NONE)
            
        } catch (e: Exception) {
        }
    }

    @UnstableApi
    fun createStreamingMediaSource(
        context: Context,
        mediaItem: MediaItem,
        requestHeaders: Map<String, String> = emptyMap()
    ): MediaSource {
        val cacheSizeMb = PlayerPreferences(context).getPlayerCacheSizeMb()
        val dataSourceFactory = PlayerCacheManager.createDataSourceFactory(
            context = context,
            cacheSizeMb = cacheSizeMb,
            defaultRequestHeaders = requestHeaders
        )
        return DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(mediaItem)
    }

    @UnstableApi
    fun prefetchStreamingMedia(
        context: Context,
        streamUri: Uri,
        cacheKey: String?,
        maxBytes: Long,
        requestHeaders: Map<String, String> = emptyMap()
    ) {
        PlayerCacheManager.prefetchToCache(
            context = context,
            uri = streamUri,
            cacheKey = cacheKey,
            maxBytes = maxBytes,
            defaultRequestHeaders = requestHeaders
        )
    }

    @UnstableApi
    private fun createLoadControl(playerPreferences: PlayerPreferences): DefaultLoadControl {
        val requestedCacheTimeMs = playerPreferences.getPlayerCacheTimeSeconds() * 1000
        val batteryOptimizationEnabled = playerPreferences.isBatteryOptimizationEnabled()
        val minBufferMs = if (batteryOptimizationEnabled) {
            minOf(requestedCacheTimeMs, 15_000)
        } else {
            requestedCacheTimeMs
        }
        val maxBufferMs = if (batteryOptimizationEnabled) {
            minOf(requestedCacheTimeMs, 30_000)
        } else {
            requestedCacheTimeMs
        }
        val playbackBufferMs = if (batteryOptimizationEnabled) {
            minOf(1_500, minBufferMs)
        } else {
            minOf(1_000, minBufferMs)
        }
        val rebufferPlaybackMs = if (batteryOptimizationEnabled) {
            minOf(5_000, maxBufferMs)
        } else {
            minOf(2_000, maxBufferMs)
        }

        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minBufferMs,
                maxBufferMs,
                playbackBufferMs,
                rebufferPlaybackMs
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
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

        currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.mediaTrackGroup.length) {
                    val format = group.mediaTrackGroup.getFormat(i)
                    val language = languageCode(format.language)
                    val label = format.label?.takeIf { it.isNotBlank() }.orEmpty()

                    tracks.add(
                        AudioTrackInfo(
                            id = buildTrackId(
                                prefix = "audio",
                                groupIndex = groupIndex,
                                trackIndex = i
                            ),
                            label = label,
                            language = language,
                            channelCount = format.channelCount,
                            codec = format.codecs,
                            playerTrackId = buildTrackId(
                                prefix = "audio",
                                groupIndex = groupIndex,
                                trackIndex = i
                            )
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
                isDefault = false,
                playerTrackId = "off",
                streamIndex = -1
            )
        )

        val currentTracks = exoPlayer.currentTracks
        currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.mediaTrackGroup.length) {
                    val format = group.mediaTrackGroup.getFormat(i)
                    val language = languageCode(format.language)
                    val isForced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
                    val isDefault = format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0
                    val label = format.label?.takeIf { it.isNotBlank() }.orEmpty()
                    tracks.add(
                        SubtitleTrackInfo(
                            id = buildTrackId(
                                prefix = "subtitle",
                                groupIndex = groupIndex,
                                trackIndex = i
                            ),
                            label = label,
                            language = language,
                            isForced = isForced,
                            isDefault = isDefault,
                            playerTrackId = buildTrackId(
                                prefix = "subtitle",
                                groupIndex = groupIndex,
                                trackIndex = i
                            )
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

        currentTracks.groups.forEachIndexed { groupIndex, group ->
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
                            id = buildTrackId(
                                prefix = "video",
                                groupIndex = groupIndex,
                                trackIndex = i
                            ),
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

        currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_AUDIO) {
                for (i in 0 until group.mediaTrackGroup.length) {
                    if (group.isTrackSelected(i)) {
                        val format = group.mediaTrackGroup.getFormat(i)
                        val language = languageCode(format.language)
                        val label = format.label?.takeIf { it.isNotBlank() }.orEmpty()

                        return AudioTrackInfo(
                            id = buildTrackId(
                                prefix = "audio",
                                groupIndex = groupIndex,
                                trackIndex = i
                            ),
                            label = label,
                            language = language,
                            channelCount = format.channelCount,
                            codec = format.codecs,
                            playerTrackId = buildTrackId(
                                prefix = "audio",
                                groupIndex = groupIndex,
                                trackIndex = i
                            )
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

        currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type == C.TRACK_TYPE_TEXT) {
                for (i in 0 until group.mediaTrackGroup.length) {
                    if (group.isTrackSelected(i)) {
                        val format = group.mediaTrackGroup.getFormat(i)
                        val language = languageCode(format.language)
                        val isForced = format.selectionFlags and C.SELECTION_FLAG_FORCED != 0
                        val isDefault = format.selectionFlags and C.SELECTION_FLAG_DEFAULT != 0
                        val label = format.label?.takeIf { it.isNotBlank() }.orEmpty()

                        return SubtitleTrackInfo(
                            id = buildTrackId(
                                prefix = "subtitle",
                                groupIndex = groupIndex,
                                trackIndex = i
                            ),
                            label = label,
                            language = language,
                            isForced = isForced,
                            isDefault = isDefault,
                            playerTrackId = buildTrackId(
                                prefix = "subtitle",
                                groupIndex = groupIndex,
                                trackIndex = i
                            )
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
            isDefault = false,
            playerTrackId = "off",
            streamIndex = -1
        )
    }

    /**
     * Log active audio playback details for diagnostics.
     */
    @UnstableApi
    fun logAudioPlaybackDiagnostics(exoPlayer: ExoPlayer, reason: String = "track_update") {
        try {
            var selectedAudio: Triple<Int, Int, androidx.media3.common.Format>? = null
            exoPlayer.currentTracks.groups.forEachIndexed { groupIndex, group ->
                if (selectedAudio != null || group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
                val trackIndex = (0 until group.mediaTrackGroup.length)
                    .firstOrNull(group::isTrackSelected)
                    ?: return@forEachIndexed
                selectedAudio = Triple(groupIndex, trackIndex, group.mediaTrackGroup.getFormat(trackIndex))
            }

            val (groupIndex, trackIndex, format) = selectedAudio ?: run {
                Log.d("PlayerUtils", "Audio diagnostics ($reason): no selected audio track")
                return
            }

            val trackId = buildTrackId(prefix = "audio", groupIndex = groupIndex, trackIndex = trackIndex)
            val channels = if (format.channelCount > 0) format.channelCount.toString() else "unknown"
            val sampleRate = if (format.sampleRate > 0) "${format.sampleRate} Hz" else "unknown"
            val bitrate = if (format.bitrate > 0) "${format.bitrate / 1000} kbps" else "unknown"
            val codec = format.codecs ?: format.sampleMimeType ?: "unknown"
            val language = format.language ?: "und"

            Log.d(
                "PlayerUtils",
                "Audio diagnostics ($reason): track=$trackId, codec=$codec, language=$language, channels=$channels, sampleRate=$sampleRate, bitrate=$bitrate"
            )
        } catch (e: Exception) {
            Log.e("PlayerUtils", "Failed audio diagnostics logging ($reason)", e)
        }
    }

    /**
     * Select audio track by ID
     */
    @UnstableApi
    fun selectAudioTrack(exoPlayer: ExoPlayer, trackId: String) {
        try {
            val trackSelector = exoPlayer.trackSelector as? DefaultTrackSelector ?: return
            val currentTracks = exoPlayer.currentTracks

            currentTracks.groups.forEachIndexed { groupIndex, group ->
                if (group.type == C.TRACK_TYPE_AUDIO) {
                    for (i in 0 until group.mediaTrackGroup.length) {
                        val currentId = buildTrackId(
                            prefix = "audio",
                            groupIndex = groupIndex,
                            trackIndex = i
                        )

                        if (currentId == trackId) {
                            val override = TrackSelectionOverride(
                                group.mediaTrackGroup,
                                listOf(i)
                            )

                            val newParams = trackSelector.parameters
                                .buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false)
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
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()

                trackSelector.setParameters(newParams)
                Log.d("PlayerUtils", "Disabled subtitles")
                return
            }

            val currentTracks = exoPlayer.currentTracks

            currentTracks.groups.forEachIndexed { groupIndex, group ->
                if (group.type == C.TRACK_TYPE_TEXT) {
                    for (i in 0 until group.mediaTrackGroup.length) {
                        val currentId = buildTrackId(
                            prefix = "subtitle",
                            groupIndex = groupIndex,
                            trackIndex = i
                        )

                        if (currentId == trackId) {
                            val override = TrackSelectionOverride(
                                group.mediaTrackGroup,
                                listOf(i)
                            )

                            val newParams = trackSelector.parameters
                                .buildUpon()
                                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
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
