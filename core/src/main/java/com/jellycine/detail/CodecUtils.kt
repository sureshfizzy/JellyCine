package com.jellycine.detail

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaStream

/**
 * Utility functions for codec display formatting and media information
 */
object CodecUtils {

    /**
     * Get enhanced audio information for display
     */
    fun getEnhancedAudioInfo(audioStream: MediaStream): String {
        val codec = audioStream.codec?.uppercase() ?: ""
        val channels = audioStream.channels ?: 0
        val channelLayout = audioStream.channelLayout
        val title = audioStream.title
        val profile = audioStream.profile

        return buildString {
            // Codec information
            val displayCodec = getDisplayCodecName(codec, false)
            if (displayCodec.isNotEmpty()) {
                append(displayCodec)
            }

            // Channel information
            val channelInfo = when {
                channels > 0 -> when (channels) {
                    1 -> "Mono"
                    2 -> "Stereo"
                    6 -> "5.1"
                    8 -> "7.1"
                    else -> "${channels}ch"
                }
                else -> ""
            }

            if (channelInfo.isNotEmpty()) {
                if (isNotEmpty()) append(" ")
                append(channelInfo)
            }
        }
    }

    /**
     * Get audio display information for codec badges
     */
    fun getAudioDisplayInfo(audioStream: MediaStream): String {
        val codec = audioStream.codec?.uppercase() ?: ""
        val channels = audioStream.channels ?: 0
        val channelLayout = audioStream.channelLayout
        val title = audioStream.title
        val profile = audioStream.profile

        val spatialAudioInfo = CodecCapabilityManager.detectSpatialAudio(codec, channels, channelLayout, title, profile)
        val channelInfo = when {
            spatialAudioInfo.isNotEmpty() -> spatialAudioInfo
            channels > 0 -> when (channels) {
                1 -> "Mono"
                2 -> "Stereo"
                6 -> "5.1"
                8 -> "7.1"
                else -> "${channels}ch"
            }
            else -> ""
        }

        return buildString {
            val displayCodec = getDisplayCodecName(codec, spatialAudioInfo.isNotEmpty())
            if (displayCodec.isNotEmpty()) {
                append(displayCodec)
            }

            if (channelInfo.isNotEmpty()) {
                if (isNotEmpty()) append(" ")
                append(channelInfo)
            }
        }
    }

    /**
     * Get display-friendly codec name
     */
    private fun getDisplayCodecName(codec: String, isSpatial: Boolean): String {
        return when (codec.uppercase()) {
            "EAC3", "E-AC-3", "EC-3" -> if (isSpatial) "Dolby Digital+" else "DD+"
            "AC3", "AC-3" -> "DD"
            "TRUEHD" -> if (isSpatial) "Dolby TrueHD" else "TrueHD"
            "DTS" -> "DTS"
            "DTSHD" -> "DTS-HD"
            "AAC" -> "AAC"
            "MP3" -> "MP3"
            "FLAC" -> "FLAC"
            "PCM" -> "PCM"
            "OPUS" -> "Opus"
            "VORBIS" -> "Vorbis"
            else -> codec.uppercase()
        }
    }

    /**
     * Get subtitle codec display name
     */
    fun getSubtitleCodecName(codec: String?): String {
        return when (codec?.uppercase()) {
            "SUBRIP" -> "SRT"
            "ASS" -> "ASS"
            "SSA" -> "SSA"
            "WEBVTT" -> "WebVTT"
            "TTML" -> "TTML"
            null, "" -> "SRT"
            else -> codec.uppercase()
        }
    }

    /**
     * Format runtime from ticks to human readable format
     */
    fun formatRuntime(ticks: Long?): String {
        if (ticks == null) return ""
        val minutes = (ticks / 10000000) / 60
        val hours = minutes / 60
        val remainingMinutes = minutes % 60
        return if (hours > 0) "${hours}h ${remainingMinutes}m" else "${minutes}m"
    }

    /**
     * Get video codec information
     */
    fun getVideoCodecInfo(item: BaseItemDto): String? {
        val videoStream = item.mediaStreams?.find { it.type == "Video" }
        return videoStream?.codec?.uppercase()
    }

    /**
     * Get audio codec information
     */
    fun getAudioCodecInfo(item: BaseItemDto): String? {
        val audioStream = item.mediaStreams?.find { it.type == "Audio" }
        return audioStream?.codec?.uppercase()
    }

    /**
     * Get resolution information
     */
    fun getResolutionInfo(item: BaseItemDto): String? {
        val videoStream = item.mediaStreams?.find { it.type == "Video" }
        return if (videoStream?.width != null && videoStream.height != null) {
            "${videoStream.width}x${videoStream.height}"
        } else null
    }

    /**
     * Get comprehensive video information for display
     */
    fun getVideoDisplayInfo(videoStream: MediaStream): String {
        return buildString {
            // Resolution
            if (videoStream.width != null && videoStream.height != null) {
                append("${videoStream.width}x${videoStream.height}")
            }

            // Codec
            videoStream.codec?.let { codec ->
                if (isNotEmpty()) append(" • ")
                append(codec.uppercase())
            }

            // Frame rate
            videoStream.realFrameRate?.let { fps ->
                if (isNotEmpty()) append(" • ")
                append("${fps.toInt()}fps")
            }

            // HDR information
            val hdrInfo = CodecCapabilityManager.detectHDRFormat(videoStream)
            if (hdrInfo.isNotEmpty()) {
                if (isNotEmpty()) append(" • ")
                append(hdrInfo)
            }

            // Bitrate
            videoStream.bitRate?.let { bitrate ->
                if (isNotEmpty()) append(" • ")
                val mbps = bitrate / 1000000.0
                append("%.1f Mbps".format(mbps))
            }
        }
    }

    /**
     * Get video quality badge text
     */
    fun getVideoQualityBadge(videoStream: MediaStream): String? {
        val height = videoStream.height ?: return null
        return when {
            height >= 2160 -> "4K"
            height >= 1440 -> "1440p"
            height >= 1080 -> "1080p"
            height >= 720 -> "720p"
            height >= 480 -> "480p"
            else -> null
        }
    }

    /**
     * Get audio quality description
     */
    fun getAudioQualityDescription(audioStream: MediaStream): String {
        return buildString {
            // Bitrate
            audioStream.bitRate?.let { bitrate ->
                val kbps = bitrate / 1000
                append("${kbps} kbps")
            }

            // Sample rate
            audioStream.sampleRate?.let { sampleRate ->
                if (isNotEmpty()) append(" • ")
                append("${sampleRate / 1000}kHz")
            }

            // Bit depth
            audioStream.bitDepth?.let { bitDepth ->
                if (isNotEmpty()) append(" • ")
                append("${bitDepth}-bit")
            }
        }
    }

    /**
     * Check if video has high quality indicators
     */
    fun isHighQualityVideo(videoStream: MediaStream): Boolean {
        val height = videoStream.height ?: 0
        val bitrate = videoStream.bitRate ?: 0
        
        return height >= 1080 || bitrate >= 10000000 // 10 Mbps
    }

    /**
     * Check if audio is lossless
     */
    fun isLosslessAudio(audioStream: MediaStream): Boolean {
        val codec = audioStream.codec?.uppercase() ?: ""
        return codec.contains("FLAC") ||
               codec.contains("TRUEHD") ||
               codec.contains("PCM") ||
               codec.contains("DTS-HD") ||
               codec.contains("ALAC")
    }

    /**
     * Get container format display name
     */
    fun getContainerFormat(item: BaseItemDto): String? {
        return item.container?.uppercase()
    }

    /**
     * Get total bitrate for the item
     */
    fun getTotalBitrate(item: BaseItemDto): String? {
        val totalBitrate = item.mediaStreams?.sumOf { it.bitRate ?: 0 } ?: 0
        return if (totalBitrate > 0) {
            val mbps = totalBitrate / 1000000.0
            "%.1f Mbps".format(mbps)
        } else null
    }

    /**
     * Get file size display
     */
    fun getFileSize(sizeBytes: Long?): String? {
        if (sizeBytes == null || sizeBytes <= 0) return null
        
        val gb = sizeBytes / (1024.0 * 1024.0 * 1024.0)
        val mb = sizeBytes / (1024.0 * 1024.0)
        
        return when {
            gb >= 1.0 -> "%.1f GB".format(gb)
            mb >= 1.0 -> "%.0f MB".format(mb)
            else -> "< 1 MB"
        }
    }
}
