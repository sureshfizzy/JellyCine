package com.jellycine.app.ui.screens.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import java.util.Locale

data class MediaMetadataInfo(
    val hdrFormat: HdrFormatInfo? = null,
    val videoFormat: VideoFormatInfo? = null,
    val audioFormat: AudioFormatInfo? = null,
    val hardwareAcceleration: HardwareAccelerationInfo? = null,
    val streamContainer: String? = null,
    val streamBitrateKbps: Int? = null,
    val playMethod: String = "Direct Play"
)

data class HdrFormatInfo(
    val isSupported: Boolean,
    val currentFormat: String? = null,
    val deviceCapabilities: String,
    val analysisResult: String? = null
)

data class VideoFormatInfo(
    val codec: String,
    val resolution: String,
    val mimeType: String,
    val colorInfo: String? = null,
    val profile: String? = null,
    val frameRate: Float? = null,
    val bitrateKbps: Int? = null,
    val bitDepth: Int? = null
)

data class AudioFormatInfo(
    val codec: String,
    val channels: String,
    val bitrate: String? = null,
    val sampleRate: String? = null,
    val language: String? = null,
    val isDefault: Boolean = false
)

data class HardwareAccelerationInfo(
    val isHardwareDecoding: Boolean,
    val activeVideoCodec: String? = null,
    val activeAudioCodec: String? = null,
    val decoderType: String,
    val asyncModeEnabled: Boolean,
    val performanceMetrics: String? = null
)

@Composable
fun MediaInfoDialog(
    mediaInfo: MediaMetadataInfo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Popup(
        alignment = Alignment.CenterStart,
        onDismissRequest = onDismiss,
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            clippingEnabled = false
        )
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compact = maxWidth < 700.dp
            val panelWidthFraction = if (compact) 0.80f else 0.31f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = if (compact) 14.dp else 34.dp, end = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Card(
                    modifier = modifier
                        .fillMaxWidth(panelWidthFraction)
                        .widthIn(min = 240.dp, max = 390.dp)
                        .heightIn(max = if (compact) 170.dp else 190.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x9916191F)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SectionTitle("Stream")
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable(onClick = onDismiss),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "\u2715",
                                    color = Color(0xFFF2F2F2),
                                    fontSize = 14.sp,
                                    lineHeight = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        PrimaryLine(buildStreamLine(mediaInfo))
                        SecondaryLine("-> ${mediaInfo.playMethod}")

                        Spacer(modifier = Modifier.height(6.dp))

                        SectionTitle("Video")
                        PrimaryLine(buildVideoTitle(mediaInfo.videoFormat, mediaInfo.hdrFormat))
                        buildVideoDetails(mediaInfo.videoFormat)?.let { PrimaryLine(it) }
                        SecondaryLine("-> ${mediaInfo.playMethod}")
                        mediaInfo.hardwareAcceleration?.let {
                            MixedLine("Renderer", if (it.isHardwareDecoding) "MediaCodec" else "Software")
                            buildDisplayMode(mediaInfo.videoFormat)?.let { mode ->
                                MixedLine("Display Mode", mode)
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        SectionTitle("Audio")
                        PrimaryLine(buildAudioTitle(mediaInfo.audioFormat))
                        mediaInfo.audioFormat?.sampleRate?.let { PrimaryLine(it) }
                        mediaInfo.audioFormat?.bitrate?.let { PrimaryLine(it) }
                        SecondaryLine("-> ${mediaInfo.playMethod}")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = Color(0xFFF3F3F3),
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun PrimaryLine(text: String) {
    if (text.isBlank()) return
    Text(
        text = text,
        color = Color(0xFFF3F3F3),
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SecondaryLine(text: String) {
    Text(
        text = text,
        color = Color(0xFFD4D4D4),
        fontSize = 10.sp,
        lineHeight = 12.sp,
        fontWeight = FontWeight.Normal
    )
}

@Composable
private fun MixedLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = Color(0xFFF3F3F3),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            color = Color(0xFFD4D4D4),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

private fun buildStreamLine(mediaInfo: MediaMetadataInfo): String {
    val container = mediaInfo.streamContainer?.uppercase(Locale.US)
        ?: mediaInfo.videoFormat?.mimeType?.substringAfter("/", "")?.uppercase(Locale.US)
        ?: "STREAM"

    val bitrateKbps = mediaInfo.streamBitrateKbps ?: mediaInfo.videoFormat?.bitrateKbps
    return if (bitrateKbps != null && bitrateKbps > 0) {
        "$container (${formatMbps(bitrateKbps)} mbps)"
    } else {
        container
    }
}

private fun buildVideoTitle(videoInfo: VideoFormatInfo?, hdrInfo: HdrFormatInfo?): String {
    if (videoInfo == null) return "Unknown"

    val resolutionTag = when {
        videoInfo.resolution.startsWith("3840x", true) || videoInfo.resolution.startsWith("4096x", true) -> "4K"
        videoInfo.resolution.startsWith("2560x", true) -> "1440p"
        videoInfo.resolution.startsWith("1920x", true) -> "1080p"
        else -> videoInfo.resolution
    }

    val hdrTag = hdrInfo?.currentFormat?.takeIf { !it.isNullOrBlank() }
        ?: if (hdrInfo?.isSupported == true) "HDR" else null

    val codecTag = mapCodecForDisplay(videoInfo.codec)

    return listOfNotNull(resolutionTag, hdrTag, codecTag)
        .joinToString(" ")
        .trim()
}

private fun buildVideoDetails(videoInfo: VideoFormatInfo?): String? {
    if (videoInfo == null) return null

    val parts = mutableListOf<String>()
    videoInfo.profile?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
    videoInfo.bitDepth?.let { parts.add("${it}bit") }
    videoInfo.bitrateKbps?.takeIf { it > 0 }?.let { parts.add("${formatMbps(it)} mbps") }
    videoInfo.frameRate?.takeIf { it > 0f }?.let { parts.add(String.format(Locale.US, "%.3f fps", it)) }

    return parts.joinToString(" ").takeIf { it.isNotBlank() }
}

private fun buildDisplayMode(videoInfo: VideoFormatInfo?): String? {
    if (videoInfo == null) return null

    val width = videoInfo.resolution.substringBefore("x", "").trim().takeIf { it.isNotBlank() }
    val fps = videoInfo.frameRate?.takeIf { it > 0f }?.let { String.format(Locale.US, "%.2f", it) }

    return when {
        width != null && fps != null -> "$width/$fps"
        videoInfo.resolution.isNotBlank() && fps != null -> "${videoInfo.resolution}/$fps"
        else -> null
    }
}

private fun buildAudioTitle(audioInfo: AudioFormatInfo?): String {
    if (audioInfo == null) return "Unknown"

    val lang = audioInfo.language?.takeIf { it.isNotBlank() }?.replaceFirstChar {
        if (it.isLowerCase()) it.titlecase(Locale.US) else it.toString()
    }
    val defaultTag = if (audioInfo.isDefault) " (Default)" else ""

    return listOfNotNull(lang, audioInfo.codec, audioInfo.channels)
        .joinToString(" ")
        .trim() + defaultTag
}

private fun mapCodecForDisplay(codec: String): String {
    return when (codec.uppercase(Locale.US)) {
        "H.265", "H265", "HEVC" -> "HEVC"
        "H.264", "H264", "AVC" -> "AVC"
        else -> codec.uppercase(Locale.US)
    }
}

private fun formatMbps(kbps: Int): String {
    val mbps = kbps / 1000f
    return if (mbps >= 10f) String.format(Locale.US, "%.0f", mbps) else String.format(Locale.US, "%.1f", mbps)
}

