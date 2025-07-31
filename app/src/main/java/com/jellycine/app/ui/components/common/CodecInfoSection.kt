package com.jellycine.app.ui.components.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.R
import com.jellycine.data.model.MediaStream
import com.jellycine.detail.CodecCapabilityManager
import com.jellycine.detail.CodecUtils

@Composable
fun CodecInfoSection(
    mediaStreams: List<MediaStream>?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val deviceCapabilities = rememberAudioCapabilities(context)

    if (!mediaStreams.isNullOrEmpty()) {
        LazyRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {


            // Video Codec Badge
            mediaStreams.find { it.type == "Video" }?.let { videoStream ->
                videoStream.codec?.let { codec ->
                    item {
                        CodecBadge(
                            text = codec.uppercase(),
                            icon = Icons.Rounded.VideoLibrary,
                            color = Color(0xFFE91E63)
                        )
                    }
                }
            }

            // Audio Codec with Spatial Detection
            mediaStreams.find { it.type == "Audio" }?.let { audioStream ->
                val audioDisplayInfo = CodecUtils.getAudioDisplayInfo(audioStream)
                val spatialInfo = CodecCapabilityManager.detectSpatialAudio(
                    audioStream.codec?.uppercase() ?: "",
                    audioStream.channels ?: 0,
                    audioStream.channelLayout,
                    audioStream.title,
                    audioStream.profile
                )

                if (audioDisplayInfo.isNotEmpty()) {
                    item {
                        CodecBadge(
                            text = audioDisplayInfo,
                            icon = when {
                                spatialInfo.contains("Dolby Atmos") -> null
                                spatialInfo.contains("DTS:X") -> Icons.Rounded.SurroundSound
                                spatialInfo.isNotEmpty() -> null
                                CodecCapabilityManager.isDolbyAudio(audioStream) -> null
                                else -> Icons.AutoMirrored.Rounded.VolumeUp
                            },
                            color = Color(0xFF2196F3),
                            customIcon = when {
                                spatialInfo.contains("Dolby Atmos") -> R.drawable.ic_dolby_atmos
                                spatialInfo.isNotEmpty() -> R.drawable.ic_spatial_audio
                                CodecCapabilityManager.isDolbyAudio(audioStream) -> R.drawable.ic_dolby_logo
                                else -> null
                            }
                        )
                    }
                }
            }

            // Audio Channels Badge
            mediaStreams.find { it.type == "Audio" }?.channels?.let { channels ->
                item {
                    CodecBadge(
                        text = when (channels) {
                            1 -> "Mono"
                            2 -> "Stereo"
                            6 -> "5.1"
                            8 -> "7.1"
                            else -> "${channels}ch"
                        },
                        icon = Icons.Rounded.SurroundSound,
                        color = Color(0xFF9C27B0)
                    )
                }
            }

            // HDR Support Badge
            mediaStreams.find { it.type == "Video" }?.let { videoStream ->
                val hdrInfo = CodecCapabilityManager.detectHDRFormat(videoStream)
                if (hdrInfo.isNotEmpty()) {
                    item {
                        CodecBadge(
                            text = hdrInfo,
                            icon = when {
                                hdrInfo.contains("Dolby Vision") -> null
                                else -> Icons.Rounded.HighQuality
                            },
                            color = when {
                                hdrInfo.contains("HDR10+") -> Color(0xFF00BCD4)
                                else -> Color(0xFFFFEB3B)
                            },
                            customIcon = if (hdrInfo.contains("Dolby Vision")) R.drawable.ic_dolby_logo else null
                        )
                    }
                }
            }

            // Device Audio Capability Badge
            if (deviceCapabilities.connectedAudioDevice != "Unknown") {
                item {
                    CodecBadge(
                        text = deviceCapabilities.connectedAudioDevice,
                        icon = when (deviceCapabilities.connectedAudioDevice) {
                            "HDMI" -> Icons.Rounded.Tv
                            "Bluetooth" -> Icons.Rounded.Bluetooth
                            "USB Headset" -> Icons.Rounded.Headphones
                            "Wired Headphones", "Wired Headset" -> Icons.Rounded.Headphones
                            else -> Icons.Rounded.Speaker
                        },
                        color = if (deviceCapabilities.canProcessSpatialAudio) Color(0xFF4CAF50) else Color(0xFF757575)
                    )
                }
            }

            // Spatial Audio Badge
            mediaStreams.find { it.type == "Audio" }?.let { audioStream ->
                val spatialInfo = CodecCapabilityManager.detectSpatialAudio(
                    audioStream.codec?.uppercase() ?: "",
                    audioStream.channels ?: 0,
                    audioStream.channelLayout,
                    audioStream.title,
                    audioStream.profile
                )

                if (spatialInfo.isNotEmpty()) {
                    item {
                        CodecBadge(
                            text = "Spatial Audio",
                            icon = null,
                            color = Color(0xFF4CAF50),
                            customIcon = R.drawable.ic_spatial_audio
                        )
                    }
                }
            }

            // Subtitles Badge
            if (mediaStreams.any { it.type == "Subtitle" }) {
                item {
                    CodecBadge(
                        text = "CC",
                        icon = Icons.Rounded.Subtitles,
                        color = Color(0xFFFF9800)
                    )
                }
            }
        }
    }
}

@Composable
private fun CodecBadge(
    text: String,
    icon: ImageVector?,
    color: Color,
    customIcon: Int? = null
) {
    Surface(
        color = Color(0xFF1A1A1A),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            when {
                customIcon != null -> {
                    Icon(
                        painter = painterResource(id = customIcon),
                        contentDescription = text,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(16.dp)
                    )
                }
                icon != null -> {
                    Icon(
                        imageVector = icon,
                        contentDescription = text,
                        tint = color,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Text(
                text = text,
                fontSize = 14.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}