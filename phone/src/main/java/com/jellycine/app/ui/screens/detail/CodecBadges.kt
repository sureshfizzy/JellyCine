package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.MediaStream
import com.jellycine.detail.CodecCapabilityManager

data class CodecBadgeState(
    val has4K: Boolean = false,
    val hdrBadgeText: String = "",
    val hasSpatialAudio: Boolean = false,
    val dolbyAudioBadgeText: String = "",
    val hasDolbyAtmos: Boolean = false,
    val audioChannelBadgeText: String = ""
) {
    val hasAnyBadges: Boolean
        get() = has4K ||
            hdrBadgeText.isNotBlank() ||
            hasSpatialAudio ||
            dolbyAudioBadgeText.isNotBlank() ||
            audioChannelBadgeText.isNotBlank()
}

@Composable
fun CodecBadges(
    streams: List<MediaStream>,
    selectedVideo: String,
    selectedAudio: String
): CodecBadgeState {
    val videoStreams = remember(streams) { streams.filter { it.type == "Video" } }
    val audioStreams = remember(streams) { streams.filter { it.type == "Audio" } }

    val selectedVideoStream = remember(videoStreams, selectedVideo, streams) {
        resolveSelectedStream(streams, "Video", selectedVideo)
    }
    val selectedAudioStream = remember(audioStreams, selectedAudio, streams) {
        resolveSelectedStream(streams, "Audio", selectedAudio)
    }

    val hdrBadgeText = remember(videoStreams, selectedVideoStream) {
        val selectedBadge = detectHdrBadgeText(selectedVideoStream)
        if (selectedBadge.isNotBlank()) {
            selectedBadge
        } else {
            videoStreams
                .map { detectHdrBadgeText(it) }
                .filter { it.isNotBlank() }
                .maxByOrNull { hdrBadgeRank(it) }
                .orEmpty()
        }
    }

    val spatialFormat = remember(selectedAudioStream) {
        selectedAudioStream?.let { stream ->
            CodecCapabilityManager.detectSpatialAudio(
                stream.codec?.uppercase() ?: "",
                stream.channels ?: 0,
                stream.channelLayout,
                stream.title,
                stream.profile
            )
        }.orEmpty()
    }

    val dolbyAudioBadgeText = remember(audioStreams, selectedAudioStream, spatialFormat) {
        val selectedBadge = detectDolbyAudioBadgeText(selectedAudioStream, spatialFormat)
        if (!selectedBadge.isNullOrBlank()) {
            selectedBadge
        } else {
            audioStreams
                .mapNotNull { stream ->
                    val streamSpatial = CodecCapabilityManager.detectSpatialAudio(
                        stream.codec?.uppercase() ?: "",
                        stream.channels ?: 0,
                        stream.channelLayout,
                        stream.title,
                        stream.profile
                    )
                    detectDolbyAudioBadgeText(stream, streamSpatial)
                }
                .maxByOrNull { dolbyBadgeRank(it) }
                .orEmpty()
        }
    }

    val audioChannelBadgeText = remember(audioStreams, selectedAudioStream) {
        val selectedChannel = detectAudioChannelBadgeText(selectedAudioStream)
        if (!selectedChannel.isNullOrBlank()) {
            selectedChannel
        } else {
            audioStreams
                .mapNotNull { detectAudioChannelBadgeText(it) }
                .maxByOrNull { channelBadgeRank(it) }
                .orEmpty()
        }
    }

    val hasDolbyAtmos = remember(dolbyAudioBadgeText) {
        dolbyAudioBadgeText.equals("Dolby Atmos", ignoreCase = true)
    }

    val has4K = remember(videoStreams, selectedVideoStream) {
        val selectedWidth = selectedVideoStream?.width ?: 0
        val selectedHeight = selectedVideoStream?.height ?: 0
        (selectedWidth >= 3840 || selectedHeight >= 2160) ||
            videoStreams.any { stream ->
                val width = stream.width ?: 0
                val height = stream.height ?: 0
                width >= 3840 || height >= 2160
            }
    }

    return remember(
        has4K,
        hdrBadgeText,
        spatialFormat,
        dolbyAudioBadgeText,
        hasDolbyAtmos,
        audioChannelBadgeText
    ) {
        CodecBadgeState(
            has4K = has4K,
            hdrBadgeText = hdrBadgeText,
            hasSpatialAudio = spatialFormat.isNotBlank(),
            dolbyAudioBadgeText = dolbyAudioBadgeText,
            hasDolbyAtmos = hasDolbyAtmos,
            audioChannelBadgeText = audioChannelBadgeText
        )
    }
}

@Composable
fun CapabilityBadge(
    text: String,
    icon: ImageVector? = null,
    customIcon: Int? = null,
    iconTintUnspecified: Boolean = false,
    customIconTint: Color? = null
) {
    Surface(
        color = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            when {
                customIcon != null -> {
                    Icon(
                        painter = painterResource(customIcon),
                        contentDescription = text,
                        tint = when {
                            iconTintUnspecified -> Color.Unspecified
                            customIconTint != null -> customIconTint
                            else -> Color.White.copy(alpha = 0.9f)
                        },
                        modifier = Modifier.size(14.dp)
                    )
                }
                icon != null -> {
                    Icon(
                        imageVector = icon,
                        contentDescription = text,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                text = text,
                fontSize = 12.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

private fun resolveSelectedStream(
    streams: List<MediaStream>,
    type: String,
    selectedOption: String
): MediaStream? {
    val typedStreams = streams.filter { it.type == type }
    if (typedStreams.isEmpty()) return null
    if (selectedOption.isBlank()) return typedStreams.firstOrNull()

    val normalizedSelected = selectedOption.replace(Regex(""" \(\d+\)$"""), "")
    return typedStreams.firstOrNull { stream ->
        stream.displayTitle?.trim().orEmpty() == normalizedSelected
    } ?: typedStreams.firstOrNull()
}

private fun detectHdrBadgeText(videoStream: MediaStream?): String {
    if (videoStream == null) return ""

    val codec = videoStream.codec?.lowercase().orEmpty()
    val codecTag = videoStream.codecTag?.lowercase().orEmpty()
    val videoRange = videoStream.videoRange?.lowercase().orEmpty()
    val videoRangeType = videoStream.videoRangeType?.lowercase().orEmpty()
    val colorTransfer = videoStream.colorTransfer?.lowercase().orEmpty()
    val title = videoStream.title?.lowercase().orEmpty()
    val profile = videoStream.profile?.lowercase().orEmpty()
    val doviTitle = videoStream.videoDoViTitle?.lowercase().orEmpty()
    val displayTitle = videoStream.displayTitle?.lowercase().orEmpty()
    val comment = videoStream.comment?.lowercase().orEmpty()

    return when {
        videoRangeType.contains("dovi") ||
            videoRange.contains("dovi") ||
            title.contains("dolby vision") ||
            profile.contains("dolby vision") ||
            codec.contains("dvhe") ||
            codec.contains("dvh1") ||
            codecTag.contains("dvhe") ||
            codecTag.contains("dvh1") ||
            doviTitle.contains("dolby vision") ||
            doviTitle.contains("dovi") ||
            displayTitle.contains("dolby vision") ||
            displayTitle.contains("dovi") ||
            comment.contains("dolby vision") ||
            comment.contains("dovi") ||
            videoStream.dvProfile != null -> "Dolby Vision"
        videoRangeType.contains("hdr10+") ||
            videoRange.contains("hdr10+") ||
            title.contains("hdr10+") ||
            profile.contains("hdr10+") -> "HDR10+"
        videoRangeType.contains("hdr10") ||
            videoRange.contains("hdr10") ||
            colorTransfer.contains("smpte2084") -> "HDR10"
        videoRangeType.contains("hdr") ||
            videoRange.contains("hdr") ||
            colorTransfer.contains("hlg") -> "HDR"
        else -> CodecCapabilityManager.detectHDRFormat(videoStream).takeIf { it.isNotBlank() }.orEmpty()
    }
}

private fun detectDolbyAudioBadgeText(audioStream: MediaStream?, spatialFormat: String): String? {
    if (audioStream == null) return null
    val title = audioStream.title?.lowercase().orEmpty()
    val profile = audioStream.profile?.lowercase().orEmpty()
    if (
        spatialFormat.contains("Dolby Atmos", ignoreCase = true) ||
        title.contains("atmos") ||
        profile.contains("atmos")
    ) return "Dolby Atmos"

    val codec = audioStream.codec?.uppercase().orEmpty()

    return when {
        codec in setOf("EAC3", "E-AC-3", "EC-3") ||
            title.contains("dolby digital plus") ||
            title.contains("dd+") ||
            profile.contains("dolby digital plus") -> "Dolby Digital+"
        codec in setOf("AC3", "AC-3") ||
            title.contains("dolby digital") ||
            profile.contains("dolby digital") -> "Dolby Digital"
        codec == "TRUEHD" || title.contains("truehd") -> "Dolby TrueHD"
        else -> null
    }
}

private fun detectAudioChannelBadgeText(audioStream: MediaStream?): String? {
    if (audioStream == null) return null
    val channels = audioStream.channels
    val channelLayout = audioStream.channelLayout?.lowercase().orEmpty()

    return when {
        channelLayout.contains("7.1") || (channels != null && channels >= 8) -> "7.1"
        channelLayout.contains("5.1") || (channels != null && channels >= 6) -> "5.1"
        channels != null && channels > 2 -> "${channels}ch"
        else -> null
    }
}

private fun hdrBadgeRank(text: String): Int {
    return when (text.lowercase()) {
        "dolby vision" -> 4
        "hdr10+" -> 3
        "hdr10" -> 2
        "hdr" -> 1
        else -> 0
    }
}

private fun dolbyBadgeRank(text: String): Int {
    return when (text.lowercase()) {
        "dolby atmos" -> 4
        "dolby truehd" -> 3
        "dolby digital+" -> 2
        "dolby digital" -> 1
        else -> 0
    }
}

private fun channelBadgeRank(text: String): Int {
    return when (text.lowercase()) {
        "7.1" -> 3
        "5.1" -> 2
        else -> 1
    }
}
