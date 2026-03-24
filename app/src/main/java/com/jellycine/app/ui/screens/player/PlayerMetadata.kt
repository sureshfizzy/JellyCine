package com.jellycine.app.ui.screens.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.jellycine.data.model.MediaStream
import com.jellycine.player.core.PlayerUtils
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.player.video.HdrCapabilityManager

internal object PlayerMetadata {

    fun isCurrentPlaybackHdr(exoPlayer: ExoPlayer?): Boolean {
        exoPlayer?.currentTracks?.let { tracks ->
            tracks.groups.forEach { group ->
                if (group.type == C.TRACK_TYPE_VIDEO) {
                    for (i in 0 until group.mediaTrackGroup.length) {
                        if (group.isTrackSelected(i)) {
                            val format = group.mediaTrackGroup.getFormat(i)
                            val colorInfo = format.colorInfo?.toString()
                            val mimeType = format.sampleMimeType
                            val codecs = format.codecs

                            val isHdrByColorInfo =
                                colorInfo?.contains("HDR", ignoreCase = true) == true ||
                                    colorInfo?.contains("Dolby Vision", ignoreCase = true) == true ||
                                    colorInfo?.contains("SMPTE2084", ignoreCase = true) == true ||
                                    colorInfo?.contains("BT.2020", ignoreCase = true) == true ||
                                    colorInfo?.contains("PQ", ignoreCase = true) == true

                            val isHdrByMimeType =
                                mimeType?.contains("dolby-vision", ignoreCase = true) == true ||
                                    mimeType?.contains("hdr", ignoreCase = true) == true

                            val isHdrByCodec =
                                codecs?.contains("dvhe", ignoreCase = true) == true ||
                                    codecs?.contains("dvh1", ignoreCase = true) == true ||
                                    codecs?.contains("hev1", ignoreCase = true) == true ||
                                    codecs?.contains("hvc1", ignoreCase = true) == true

                            val videoFormatAnalysis =
                                HdrCapabilityManager.analyzeVideoFormat(mimeType, codecs, colorInfo)
                            return isHdrByColorInfo ||
                                isHdrByMimeType ||
                                isHdrByCodec ||
                                videoFormatAnalysis.hdrSupport != HdrCapabilityManager.HdrSupport.SDR
                        }
                    }
                }
            }
        }
        return false
    }

    fun buildHdrFormatInfo(
        context: Context?,
        exoPlayer: ExoPlayer?
    ): String {
        return context?.let { ctx ->
            val hdrInfo = PlayerUtils.getHdrCapabilityInfo(ctx)
            val analysisResult = exoPlayer?.currentTracks
                ?.groups
                ?.firstOrNull { it.type == C.TRACK_TYPE_VIDEO }
                ?.let { group ->
                    (0 until group.mediaTrackGroup.length)
                        .firstOrNull { trackIndex -> group.isTrackSelected(trackIndex) }
                        ?.let { selectedTrackIndex ->
                            val format = group.mediaTrackGroup.getFormat(selectedTrackIndex)
                            PlayerUtils.analyzeVideoFormatForPlayback(
                                ctx,
                                format.sampleMimeType,
                                format.codecs,
                                format.colorInfo?.toString()
                            )
                        }
                }

            if (analysisResult.isNullOrBlank()) {
                hdrInfo
            } else {
                "$hdrInfo\n$analysisResult"
            }
        } ?: "HDR info not available - player not initialized"
    }

    fun buildMediaMetadataInfo(
        context: Context?,
        exoPlayer: ExoPlayer?,
        mediaStreams: List<MediaStream>?,
        mediaSourceContainer: String?,
        mediaSourceBitrateKbps: Int?,
        playMethodDisplayName: String
    ): MediaMetadataInfo {
        val hdrFormatInfo = context?.let { ctx ->
            val deviceHdrInfo = PlayerUtils.getHdrCapabilityInfo(ctx)
            val deviceSupportsHdr = deviceHdrInfo.contains("HDR10") ||
                deviceHdrInfo.contains("Dolby Vision") ||
                deviceHdrInfo.contains("HDR")

            var currentFormat: String? = null
            var isContentHdr = false
            var analysisResult: String? = null
            var originalContentFormat: String? = null

            exoPlayer?.currentTracks?.let { tracks ->
                tracks.groups.forEach { group ->
                    if (group.type == C.TRACK_TYPE_VIDEO) {
                        for (i in 0 until group.mediaTrackGroup.length) {
                            if (group.isTrackSelected(i)) {
                                val format = group.mediaTrackGroup.getFormat(i)
                                val colorInfo = format.colorInfo?.toString()
                                val mimeType = format.sampleMimeType
                                val codecs = format.codecs

                                val isHdrByColorInfo =
                                    colorInfo?.contains("HDR", ignoreCase = true) == true ||
                                        colorInfo?.contains("Dolby Vision", ignoreCase = true) == true ||
                                        colorInfo?.contains("SMPTE2084", ignoreCase = true) == true ||
                                        colorInfo?.contains("BT.2020", ignoreCase = true) == true ||
                                        colorInfo?.contains("PQ", ignoreCase = true) == true

                                val isHdrByMimeType =
                                    mimeType?.contains("dolby-vision", ignoreCase = true) == true ||
                                        mimeType?.contains("hdr", ignoreCase = true) == true

                                val isHdrByCodec =
                                    codecs?.contains("dvhe", ignoreCase = true) == true ||
                                        codecs?.contains("dvh1", ignoreCase = true) == true ||
                                        codecs?.contains("hev1", ignoreCase = true) == true ||
                                        codecs?.contains("hvc1", ignoreCase = true) == true

                                val videoFormatAnalysis =
                                    HdrCapabilityManager.analyzeVideoFormat(mimeType, codecs, colorInfo)
                                val bestFormat = HdrCapabilityManager.getBestPlayableFormat(
                                    ctx,
                                    videoFormatAnalysis
                                )
                                originalContentFormat = videoFormatAnalysis.hdrSupport.displayName

                                isContentHdr = isHdrByColorInfo ||
                                    isHdrByMimeType ||
                                    isHdrByCodec ||
                                    videoFormatAnalysis.hdrSupport != HdrCapabilityManager.HdrSupport.SDR

                                if (isContentHdr) {
                                    currentFormat = when {
                                        colorInfo?.contains("Dolby Vision", ignoreCase = true) == true -> "Dolby Vision"
                                        codecs?.contains("dvhe", ignoreCase = true) == true ||
                                            codecs?.contains("dvh1", ignoreCase = true) == true -> "Dolby Vision"
                                        colorInfo?.contains("HDR10+", ignoreCase = true) == true -> "HDR10+"
                                        colorInfo?.contains("HDR10", ignoreCase = true) == true ||
                                            colorInfo?.contains("SMPTE2084", ignoreCase = true) == true -> "HDR10"
                                        colorInfo?.contains("HLG", ignoreCase = true) == true -> "HLG"
                                        else -> "HDR"
                                    }
                                }

                                analysisResult = if (videoFormatAnalysis.hdrSupport != bestFormat.hdrSupport) {
                                    "Content: ${originalContentFormat} -> Playing: ${bestFormat.hdrSupport.displayName}"
                                } else if (isContentHdr) {
                                    "Playing in native ${currentFormat} format"
                                } else {
                                    "Standard Dynamic Range (SDR)"
                                }

                                break
                            }
                        }
                    }
                }
            }

            HdrFormatInfo(
                isSupported = isContentHdr,
                currentFormat = currentFormat,
                deviceCapabilities = if (deviceSupportsHdr) "Yes" else "No",
                analysisResult = analysisResult
            )
        }

        val videoFormatInfo = mediaStreams?.find { it.type == "Video" }?.let { videoStream ->
            val rawCodec = videoStream.codec ?: "Unknown"
            val displayCodec = getDisplayVideoCodecName(rawCodec)
            VideoFormatInfo(
                codec = displayCodec,
                resolution = if (videoStream.width != null && videoStream.height != null) {
                    "${videoStream.width}x${videoStream.height}"
                } else {
                    "Unknown"
                },
                mimeType = videoStream.codec?.let { "video/${it.lowercase()}" } ?: "Unknown",
                colorInfo = listOfNotNull(
                    videoStream.colorSpace,
                    videoStream.colorTransfer,
                    videoStream.colorPrimaries
                ).joinToString(", ").takeIf { it.isNotEmpty() },
                profile = videoStream.profile,
                frameRate = videoStream.realFrameRate ?: videoStream.averageFrameRate,
                bitrateKbps = videoStream.bitRate?.div(1000),
                bitDepth = videoStream.bitDepth
            )
        }

        val audioFormatInfo = mediaStreams?.find { it.type == "Audio" }?.let { audioStream ->
            val channels = audioStream.channels?.let { channelCount ->
                when (channelCount) {
                    1 -> "Mono"
                    2 -> "Stereo"
                    6 -> "5.1"
                    8 -> "7.1"
                    else -> "${channelCount}ch"
                }
            } ?: "Unknown"

            val rawCodec = audioStream.codec ?: "Unknown"
            val displayCodec = getDisplayAudioCodecName(rawCodec)

            AudioFormatInfo(
                codec = displayCodec,
                channels = channels,
                bitrate = audioStream.bitRate?.let { "${it / 1000} kbps" },
                sampleRate = audioStream.sampleRate?.let { "${it} Hz" },
                language = audioStream.language,
                isDefault = audioStream.isDefault == true
            )
        }

        val hardwareAccelerationInfo = context?.let { ctx ->
            val playerPreferences = PlayerPreferences(ctx)
            val isHwAccelEnabled = playerPreferences.isHardwareAccelerationEnabled()
            val isAsyncEnabled = playerPreferences.isAsyncMediaCodecEnabled()
            val decoderPriority = playerPreferences.getDecoderPriority()

            var activeVideoCodec: String? = null
            var activeAudioCodec: String? = null
            var isUsingHardwareDecoder = false

            exoPlayer?.currentTracks?.let { tracks ->
                tracks.groups.forEach { group ->
                    when (group.type) {
                        C.TRACK_TYPE_VIDEO -> {
                            for (i in 0 until group.mediaTrackGroup.length) {
                                if (group.isTrackSelected(i)) {
                                    val format = group.mediaTrackGroup.getFormat(i)
                                    activeVideoCodec = getDisplayVideoCodecName(
                                        format.codecs ?: format.sampleMimeType ?: "Unknown"
                                    )
                                    isUsingHardwareDecoder = isHwAccelEnabled &&
                                        (format.sampleMimeType?.contains("video/") == true)
                                    break
                                }
                            }
                        }

                        C.TRACK_TYPE_AUDIO -> {
                            for (i in 0 until group.mediaTrackGroup.length) {
                                if (group.isTrackSelected(i)) {
                                    val format = group.mediaTrackGroup.getFormat(i)
                                    activeAudioCodec = getDisplayAudioCodecName(
                                        format.codecs ?: format.sampleMimeType ?: "Unknown"
                                    )
                                    break
                                }
                            }
                        }
                    }
                }
            }

            val decoderType = when {
                !isHwAccelEnabled -> "Software"
                isUsingHardwareDecoder -> "Hardware"
                decoderPriority == PlayerPreferences.DECODER_PRIORITY_SOFTWARE -> "Software Decoder"
                else -> "Hardware Decoder"
            }

            HardwareAccelerationInfo(
                isHardwareDecoding = isHwAccelEnabled,
                activeVideoCodec = activeVideoCodec,
                activeAudioCodec = activeAudioCodec,
                decoderType = decoderType,
                asyncModeEnabled = isHwAccelEnabled && isAsyncEnabled,
                performanceMetrics = if (isHwAccelEnabled) "GPU-accelerated" else "CPU-only"
            )
        }

        return MediaMetadataInfo(
            hdrFormat = hdrFormatInfo,
            videoFormat = videoFormatInfo,
            audioFormat = audioFormatInfo,
            hardwareAcceleration = hardwareAccelerationInfo,
            streamContainer = mediaSourceContainer,
            streamBitrateKbps = mediaSourceBitrateKbps,
            playMethod = playMethodDisplayName
        )
    }

    fun getSourceVideoHeight(mediaStreams: List<MediaStream>?): Int? {
        return mediaStreams
            .orEmpty()
            .asSequence()
            .filter { it.type == "Video" }
            .mapNotNull { stream ->
                val width = stream.width ?: 0
                val height = stream.height ?: 0
                when {
                    width >= 3840 || height >= 2160 -> 2160
                    width >= 1920 || height >= 1080 -> 1080
                    width >= 1280 || height >= 720 -> 720
                    width >= 854 || height >= 480 -> 480
                    height > 0 -> height
                    else -> null
                }
            }
            .maxOrNull()
    }

    private fun getDisplayVideoCodecName(codec: String): String {
        return when (codec.uppercase()) {
            "H264", "AVC", "AVC1" -> "H.264"
            "H265", "HEVC", "HEV1", "HVC1" -> "H.265"
            "VP8" -> "VP8"
            "VP9" -> "VP9"
            "AV1" -> "AV1"
            "MPEG2", "MPEG-2" -> "MPEG-2"
            "MPEG4", "MPEG-4" -> "MPEG-4"
            "XVID" -> "Xvid"
            "DIVX" -> "DivX"
            "WMV" -> "WMV"
            else -> codec.uppercase()
        }
    }

    private fun getDisplayAudioCodecName(codec: String): String {
        return when (codec.uppercase()) {
            "EAC3", "E-AC-3", "EC-3" -> "Dolby Digital+"
            "AC3", "AC-3" -> "Dolby Digital"
            "TRUEHD" -> "Dolby TrueHD"
            "DTS" -> "DTS"
            "DTSHD", "DTS-HD" -> "DTS-HD"
            "AAC", "MP4A", "MP4A.40.2" -> "AAC"
            "MP3" -> "MP3"
            "FLAC" -> "FLAC"
            "PCM" -> "PCM"
            "OPUS" -> "Opus"
            "VORBIS" -> "Vorbis"
            "WMA" -> "WMA"
            "ALAC" -> "ALAC"
            else -> codec.uppercase()
        }
    }
}
