package com.jellycine.app.ui.screens.player

import com.jellycine.app.R
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class MediaMetadataInfo(
    val spatialAudio: SpatialAudioInfo? = null,
    val hdrFormat: HdrFormatInfo? = null,
    val videoFormat: VideoFormatInfo? = null,
    val audioFormat: AudioFormatInfo? = null,
    val hardwareAcceleration: HardwareAccelerationInfo? = null
)

data class SpatialAudioInfo(
    val isActive: Boolean,
    val format: String,
    val hasHeadTracking: Boolean,
    val deviceEnhancement: Boolean,
    val reason: String? = null
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
    val colorInfo: String? = null
)

data class AudioFormatInfo(
    val codec: String,
    val channels: String,
    val bitrate: String? = null,
    val sampleRate: String? = null
)

data class HardwareAccelerationInfo(
    val isHardwareDecoding: Boolean,
    val activeVideoCodec: String? = null,
    val activeAudioCodec: String? = null,
    val decoderType: String, // "Hardware" or "Software"
    val asyncModeEnabled: Boolean,
    val performanceMetrics: String? = null
)

@Composable
fun MediaInfoDialog(
    mediaInfo: MediaMetadataInfo,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight(0.8f),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF2A2A2A),
                                        Color(0xFF1E1E1E)
                                    )
                                )
                            )
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Media Information",
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.1f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    
                    // Content - Side by side layout
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // Spatial Audio Section
                            mediaInfo.spatialAudio?.let { spatialInfo ->
                                MediaInfoSection(
                                    title = "Spatial Audio",
                                    icon = Icons.Outlined.SurroundSound,
                                    iconTint = if (spatialInfo.isActive) Color(0xFF4CAF50) else Color(0xFF757575)
                                ) {
                                    MediaInfo(
                                        label = "Status",
                                        value = if (spatialInfo.isActive) "ACTIVE" else "INACTIVE",
                                        valueColor = if (spatialInfo.isActive) Color(0xFF4CAF50) else Color(0xFF757575)
                                    )
                                    
                                    MediaInfo(
                                        label = "Format",
                                        value = spatialInfo.format
                                    )
                                    
                                    MediaInfo(
                                        label = "Head Tracking",
                                        value = if (spatialInfo.hasHeadTracking) "Yes" else "No",
                                        valueColor = if (spatialInfo.hasHeadTracking) Color(0xFF4CAF50) else Color(0xFF757575)
                                    )
                                    
                                    MediaInfo(
                                        label = "Enhancement",
                                        value = if (spatialInfo.deviceEnhancement) "Active" else "Inactive",
                                        valueColor = if (spatialInfo.deviceEnhancement) Color(0xFF4CAF50) else Color(0xFF757575)
                                    )
                                    
                                    if (!spatialInfo.isActive && spatialInfo.reason != null) {
                                        MediaInfo(
                                            label = "Reason",
                                            value = spatialInfo.reason,
                                            valueColor = Color(0xFF757575)
                                        )
                                    }
                                }
                            }
                            
                            // Audio Format Section
                            mediaInfo.audioFormat?.let { audioInfo ->
                                MediaInfoSection(
                                    title = "Audio Stream",
                                    icon = Icons.Rounded.VolumeUp,
                                    iconTint = Color(0xFF9C27B0)
                                ) {
                                    MediaInfo(
                                        label = "Codec",
                                        value = audioInfo.codec
                                    )
                                    
                                    MediaInfo(
                                        label = "Channels",
                                        value = audioInfo.channels
                                    )
                                    
                                    audioInfo.bitrate?.let { bitrate ->
                                        MediaInfo(
                                            label = "Bitrate",
                                            value = bitrate
                                        )
                                    }
                                    
                                    audioInfo.sampleRate?.let { sampleRate ->
                                        MediaInfo(
                                            label = "Sample Rate",
                                            value = sampleRate
                                        )
                                    }
                                }
                            }
                            
                            // Hardware Acceleration Section
                            mediaInfo.hardwareAcceleration?.let { hwInfo ->
                                MediaInfoSection(
                                    title = "Hardware Acceleration",
                                    icon = Icons.Rounded.Speed,
                                    iconTint = if (hwInfo.isHardwareDecoding) Color(0xFF4CAF50) else Color(0xFF757575)
                                ) {
                                    MediaInfo(
                                        label = "Status",
                                        value = if (hwInfo.isHardwareDecoding) "ENABLED" else "DISABLED",
                                        valueColor = if (hwInfo.isHardwareDecoding) Color(0xFF4CAF50) else Color(0xFF757575)
                                    )
                                    
                                    MediaInfo(
                                        label = "Decoder Type",
                                        value = hwInfo.decoderType,
                                        valueColor = if (hwInfo.decoderType == "Hardware") Color(0xFF4CAF50) else Color(0xFF757575)
                                    )
                                    
                                    hwInfo.activeVideoCodec?.let { codec ->
                                        MediaInfo(
                                            label = "Video Decoder",
                                            value = codec
                                        )
                                    }
                                    
                                    hwInfo.activeAudioCodec?.let { codec ->
                                        MediaInfo(
                                            label = "Audio Decoder",
                                            value = codec
                                        )
                                    }
                                    
                                    MediaInfo(
                                        label = "Async Mode",
                                        value = if (hwInfo.asyncModeEnabled) "Yes" else "No",
                                        valueColor = if (hwInfo.asyncModeEnabled) Color(0xFF4CAF50) else Color(0xFF757575)
                                    )
                                    
                                    hwInfo.performanceMetrics?.let { metrics ->
                                        MediaInfo(
                                            label = "Performance",
                                            value = metrics,
                                            valueColor = Color(0xFF03DAC6)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Right Column - Video & HDR Info
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            // HDR Format Section  
                            mediaInfo.hdrFormat?.let { hdrInfo ->
                                MediaInfoSection(
                                    title = "HDR Information",
                                    icon = Icons.Outlined.HighQuality,
                                    iconTint = if (hdrInfo.isSupported) Color(0xFFFF9800) else Color(0xFF757575)
                                ) {
                                    MediaInfo(
                                        label = "Content HDR",
                                        value = if (hdrInfo.isSupported) "Yes" else "No",
                                        valueColor = if (hdrInfo.isSupported) Color(0xFFFF9800) else Color(0xFF757575)
                                    )
                                    
                                    MediaInfo(
                                        label = "Device Support",
                                        value = hdrInfo.deviceCapabilities,
                                        valueColor = if (hdrInfo.deviceCapabilities == "Yes") Color(0xFF4CAF50) else Color(0xFF757575)
                                    )
                                    
                                    if (hdrInfo.isSupported && hdrInfo.currentFormat != null) {
                                        MediaInfo(
                                            label = "HDR Format",
                                            value = hdrInfo.currentFormat,
                                            valueColor = Color(0xFFFF9800)
                                        )
                                    }
                                    
                                    // Show analysis result (fallback info or status)
                                    hdrInfo.analysisResult?.let { analysis ->
                                        val isFallback = analysis.contains("fallback", ignoreCase = true)
                                        MediaInfo(
                                            label = if (isFallback) "Playback" else "Status",
                                            value = analysis,
                                            valueColor = if (isFallback) Color(0xFFFFA726) else if (hdrInfo.isSupported) Color(0xFF4CAF50) else Color(0xFF757575)
                                        )
                                    }
                                    
                                    if (!hdrInfo.isSupported && hdrInfo.analysisResult == null) {
                                        MediaInfo(
                                            label = "Status",
                                            value = "Standard Dynamic Range (SDR)",
                                            valueColor = Color(0xFF757575)
                                        )
                                    }
                                }
                            }
                            
                            // Video Format Section
                            mediaInfo.videoFormat?.let { videoInfo ->
                                MediaInfoSection(
                                    title = "Video Stream",
                                    icon = Icons.Rounded.VideoFile,
                                    iconTint = Color(0xFF2196F3)
                                ) {
                                    MediaInfo(
                                        label = "Codec",
                                        value = videoInfo.codec
                                    )
                                    
                                    MediaInfo(
                                        label = "Resolution",
                                        value = videoInfo.resolution
                                    )
                                    
                                    MediaInfo(
                                        label = "MIME Type",
                                        value = videoInfo.mimeType
                                    )
                                    
                                    videoInfo.colorInfo?.let { colorInfo ->
                                        MediaInfo(
                                            label = "Color Info",
                                            value = colorInfo
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaInfoSection(
    title: String,
    icon: ImageVector,
    iconTint: Color = Color.White,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Section Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = iconTint.copy(alpha = 0.15f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
        }
        
        // Section Content
        Column(
            modifier = Modifier.padding(start = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = content
        )
    }
}

@Composable
private fun MediaInfo(
    label: String,
    value: String,
    valueColor: Color = Color.White,
    modifier: Modifier = Modifier
) {
    // Use the same dark transparent background as unselected track items
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = valueColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.5f)
            )
        }
    }
}