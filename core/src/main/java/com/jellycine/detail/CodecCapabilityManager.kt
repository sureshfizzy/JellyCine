package com.jellycine.detail

import android.content.Context
import com.jellycine.data.model.MediaStream
import com.jellycine.player.audio.SpatializerHelper

/**
 * Manager class for codec capability detection and spatial audio analysis
 */
object CodecCapabilityManager {

    /**
     * Detect spatial audio format from media stream metadata
     */
    fun detectSpatialAudio(
        codec: String, 
        channels: Int, 
        channelLayout: String?, 
        title: String?, 
        profile: String?
    ): String {
        val codecLower = codec.lowercase()
        val titleLower = title?.lowercase() ?: ""
        val layoutLower = channelLayout?.lowercase() ?: ""
        val profileLower = profile?.lowercase() ?: ""

        // Spatial Audio Detection
        when {
            // === DOLBY ATMOS DETECTION ===
            
            // Explicit Atmos indicators in metadata (most reliable)
            titleLower.contains("dolby atmos") ||
            titleLower.contains("atmos") ||
            layoutLower.contains("atmos") ||
            profileLower.contains("atmos") ||
            codecLower.contains("eac3-joc") ||
            codecLower.contains("ec+3") ||
            titleLower.contains("joc") ||
            profileLower.contains("joc") ||
            titleLower.contains("object audio") -> return "Dolby Atmos"

            // E-AC-3 JOC (AC-3 with Joint Object Coding)
            titleLower.contains("e-ac-3 joc") ||
            titleLower.contains("eac3 joc") ||
            titleLower.contains("joint object coding") ||
            titleLower.contains("dolby digital plus with dolby atmos") -> return "Dolby Atmos"

            // TrueHD with Atmos markers.
            // Plain TrueHD 7.1 is not guaranteed to be Atmos.
            codecLower.contains("truehd") && (
                titleLower.contains("atmos") ||
                profileLower.contains("atmos") ||
                titleLower.contains("object") ||
                profileLower.contains("joc") ||
                titleLower.contains("joc")
            ) -> return "Dolby Atmos"

            // E-AC-3 with Atmos indicators (streaming format)
            (codecLower.contains("eac3") || codecLower.contains("e-ac-3") || codecLower.contains("ec-3")) && (
                titleLower.contains("atmos") ||
                profileLower.contains("atmos") ||
                titleLower.contains("joc") ||
                profileLower.contains("joc") ||
                titleLower.contains("object") ||
                profileLower.contains("object")
            ) -> return "Dolby Atmos"

            // === DTS:X DETECTION ===
            
            // Explicit DTS:X indicators
            titleLower.contains("dts:x") ||
            titleLower.contains("dtsx") ||
            titleLower.contains("dts-x") ||
            layoutLower.contains("dts:x") ||
            profileLower.contains("dts:x") -> return "DTS:X"

            // DTS with object audio indicators
            codecLower.contains("dts") && (
                titleLower.contains("object") ||
                titleLower.contains("immersive") ||
                channels >= 10 // DTS with 10+ channels likely DTS:X
            ) -> return "DTS:X"

            // === SPATIAL CHANNEL LAYOUTS ===
            
            // Object-based audio detection by specific channel layouts
            layoutLower.contains("7.1.4") || layoutLower.contains("(7.1.4)") -> return "7.1.4 (Atmos)"
            layoutLower.contains("5.1.4") || layoutLower.contains("(5.1.4)") -> return "5.1.4 (Atmos)"
            layoutLower.contains("7.1.2") || layoutLower.contains("(7.1.2)") -> return "7.1.2 (Atmos)"
            layoutLower.contains("5.1.2") || layoutLower.contains("(5.1.2)") -> return "5.1.2 (Atmos)"
            layoutLower.contains("9.1.6") || layoutLower.contains("(9.1.6)") -> return "9.1.6 (Atmos)"
            layoutLower.contains("11.1.4") || layoutLower.contains("(11.1.4)") -> return "11.1.4 (Atmos)"

            // === OTHER SPATIAL FORMATS ===
            
            // Sony 360 Reality Audio
            titleLower.contains("360 reality audio") ||
            titleLower.contains("360ra") ||
            profileLower.contains("360 reality") -> return "360 Reality Audio"

            // MPEG-H Audio
            codecLower.contains("mpegh") ||
            codecLower.contains("mpeg-h") ||
            titleLower.contains("mpeg-h audio") -> return "MPEG-H Audio"

            // Auro-3D
            titleLower.contains("auro-3d") ||
            titleLower.contains("auro3d") ||
            layoutLower.contains("auro") -> return "Auro-3D"

            // === CHANNEL-BASED SURROUND (SPATIALIZABLE BEDS) ===

            // Channel layouts commonly treated as spatializable multichannel beds
            layoutLower.contains("7.1") || layoutLower.contains("(7.1)") -> return "7.1 Surround"
            layoutLower.contains("5.1") || layoutLower.contains("(5.1)") -> return "5.1 Surround"

            // Channel-count fallback for spatializable multichannel beds
            channels >= 12 -> return "${channels}ch (Spatial)"
            channels >= 10 -> return "${channels}ch Surround"
            channels >= 8 -> return "7.1 Surround"
            channels >= 6 -> return "5.1 Surround"
        }

        return ""
    }

    /**
     * Detect HDR format from video stream
     */
    fun detectHDRFormat(videoStream: MediaStream): String {
        val videoRange = videoStream.videoRange?.lowercase() ?: ""
        val videoRangeType = videoStream.videoRangeType?.lowercase() ?: ""
        val codec = videoStream.codec?.lowercase() ?: ""
        val title = videoStream.title?.lowercase() ?: ""
        val profile = videoStream.profile?.lowercase() ?: ""
        
        // Check for Dolby Vision
        when {
            videoRangeType.contains("dovi") ||
            videoRange.contains("dovi") ||
            title.contains("dolby vision") ||
            title.contains("dv") ||
            profile.contains("dolby vision") ||
            profile.contains("dovi") ||
            // Check for DV profile indicators
            videoStream.dvProfile != null -> return "Dolby Vision"
            
            // Check for HDR10+
            videoRangeType.contains("hdr10+") ||
            videoRange.contains("hdr10+") ||
            title.contains("hdr10+") ||
            profile.contains("hdr10+") -> return "HDR10+"
            
            // Check for HDR10
            videoRangeType.contains("hdr10") ||
            videoRange.contains("hdr10") ||
            title.contains("hdr10") ||
            profile.contains("hdr10") -> return "HDR10"
            
            // Generic HDR detection
            videoRange.contains("hdr") ||
            videoRangeType.contains("hdr") ||
            title.contains("hdr") -> return "HDR"
        }
        
        return ""
    }

    /**
     * Check if audio stream is Dolby-based
     */
    fun isDolbyAudio(audioStream: MediaStream): Boolean {
        val codec = audioStream.codec?.uppercase() ?: ""
        val title = audioStream.title?.lowercase() ?: ""
        val profile = audioStream.profile?.lowercase() ?: ""
        val channelLayout = audioStream.channelLayout?.lowercase() ?: ""

        return codec.contains("EAC3") ||
               codec.contains("E-AC-3") ||
               codec.contains("EC-3") ||
               codec.contains("AC3") ||
               codec.contains("AC-3") ||
               codec.contains("TRUEHD") ||
               title.contains("dolby") ||
               title.contains("atmos") ||
               title.contains("digital+") ||
               title.contains("digital plus") ||
               profile.contains("dolby") ||
               profile.contains("atmos") ||
               channelLayout.contains("dolby") ||
               channelLayout.contains("atmos")
    }

    /**
     * Check if audio has spatial/immersive capabilities
     */
    fun hasSpatialAudio(audioStream: MediaStream): Boolean {
        val spatialInfo = detectSpatialAudio(
            audioStream.codec?.uppercase() ?: "",
            audioStream.channels ?: 0,
            audioStream.channelLayout,
            audioStream.title,
            audioStream.profile
        )
        return spatialInfo.isNotEmpty()
    }
    
    /**
     * Check if the device can spatialize the given audio stream.
     * Returns true only when content is spatializable and the current route can render it.
     */
    fun canSpatializeAudioStream(context: Context, audioStream: MediaStream): SpatializationResult {
        val spatialFormat = detectSpatialAudio(
            audioStream.codec?.uppercase() ?: "",
            audioStream.channels ?: 0,
            audioStream.channelLayout,
            audioStream.title,
            audioStream.profile
        )
        
        if (spatialFormat.isEmpty()) {
            return SpatializationResult(
                canSpatialize = false,
                reason = "Content is not multichannel/object-based spatial audio",
                spatialFormat = "Stereo"
            )
        }

        val spatializerHelper = SpatializerHelper(context)
        val requestedChannelCount = (audioStream.channels ?: 2).coerceAtLeast(2)

        val routeSupportsSpatialization = spatializerHelper.let { helper ->
            val audioFormat = helper.getRecommendedAudioFormat(requestedChannelCount)
            audioFormat?.let { helper.canSpatializeOnTrack(it) } ?: false
        }
        val spatializerActive = spatializerHelper.let { helper ->
            val audioFormat = helper.getRecommendedAudioFormat(requestedChannelCount)
            audioFormat?.let { helper.canSpatializeAudio(it) } ?: false
        }

        return if (spatializerActive) {
            SpatializationResult(
                canSpatialize = true,
                reason = "Spatializable content detected and device spatializer is active for this route",
                spatialFormat = spatialFormat
            )
        } else if (routeSupportsSpatialization) {
            SpatializationResult(
                canSpatialize = false,
                reason = "Spatializable content detected, but device spatializer is disabled in system settings",
                spatialFormat = spatialFormat
            )
        } else {
            SpatializationResult(
                canSpatialize = false,
                reason = "Spatializable content detected, but device spatializer is unavailable for current route/format",
                spatialFormat = spatialFormat
            )
        }
    }
}

/**
 * Result of spatialization capability check
 */
data class SpatializationResult(
    val canSpatialize: Boolean,
    val reason: String,
    val spatialFormat: String
)
