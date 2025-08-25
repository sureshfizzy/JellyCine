package com.jellycine.app.ui.screens.player

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Subtitles
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.jellycine.player.AudioTrackInfo
import com.jellycine.player.SubtitleTrackInfo

/**
 * Audio track selection dialog with clean design
 */
@Composable
fun AudioTrackSelectionDialog(
    isVisible: Boolean,
    audioTracks: List<AudioTrackInfo>,
    currentAudioTrack: AudioTrackInfo?,
    onTrackSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        TrackSelectionDialog(
            title = "Audio Tracks",
            icon = Icons.Rounded.GraphicEq,
            accentColor = Color(0xFF00D4FF),
            tracks = audioTracks,
            currentTrack = currentAudioTrack,
            onTrackSelected = onTrackSelected,
            onDismiss = onDismiss,
            trackDisplayInfo = { track ->
                val trackIndex = audioTracks.indexOf(track) + 1
                val title = if (!track.label.isNullOrEmpty()) {
                    track.label
                } else {
                    "Audio Track $trackIndex"
                }
                TrackDisplayInfo(
                    title = title,
                    subtitle = buildAudioTrackSubtitle(track),
                    description = buildAudioTrackDescription(track)
                )
            }
        )
    }
}

/**
 * Subtitle track selection dialog with clean design
 */
@Composable
fun SubtitleTrackSelectionDialog(
    isVisible: Boolean,
    subtitleTracks: List<SubtitleTrackInfo>,
    currentSubtitleTrack: SubtitleTrackInfo?,
    onTrackSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (isVisible) {
        TrackSelectionDialog(
            title = "Subtitle Tracks",
            icon = Icons.Rounded.ClosedCaption,
            accentColor = Color(0xFFFF6B35),
            tracks = subtitleTracks,
            currentTrack = currentSubtitleTrack,
            onTrackSelected = onTrackSelected,
            onDismiss = onDismiss,
            trackDisplayInfo = { track ->
                val trackIndex = subtitleTracks.indexOf(track) + 1
                val title = if (!track.label.isNullOrEmpty()) {
                    track.label
                } else {
                    "Subtitle Track $trackIndex"
                }
                TrackDisplayInfo(
                    title = title,
                    subtitle = buildSubtitleTrackSubtitle(track),
                    description = buildSubtitleTrackDescription(track)
                )
            }
        )
    }
}

/**
 * Track selection dialog with glassmorphism design
 */
@Composable
private fun <T> TrackSelectionDialog(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    tracks: List<T>,
    currentTrack: T?,
    onTrackSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    trackDisplayInfo: (T) -> TrackDisplayInfo
) where T : Any {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(animationSpec = tween(400)) + scaleIn(
                initialScale = 0.85f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
            exit = fadeOut(animationSpec = tween(250)) + scaleOut(
                targetScale = 0.85f,
                animationSpec = tween(250)
            )
        ) {
            // Much smaller dialog container
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .wrapContentHeight()
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1A1A1A).copy(alpha = 0.95f),
                                Color(0xFF0A0A0A).copy(alpha = 0.98f)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.3f),
                                Color.White.copy(alpha = 0.1f)
                            )
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    DialogHeader(
                        title = title,
                        icon = icon,
                        accentColor = accentColor,
                        onClose = onDismiss
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Compact track list
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(tracks) { track ->
                            val displayInfo = trackDisplayInfo(track)
                            val isSelected = when (track) {
                                is AudioTrackInfo -> track.id == (currentTrack as? AudioTrackInfo)?.id
                                is SubtitleTrackInfo -> track.id == (currentTrack as? SubtitleTrackInfo)?.id
                                else -> false
                            }
                            val trackId = when (track) {
                                is AudioTrackInfo -> track.id
                                is SubtitleTrackInfo -> track.id
                                else -> ""
                            }
                            
                            CompactTrackItem(
                                title = displayInfo.title,
                                subtitle = displayInfo.subtitle,
                                description = displayInfo.description,
                                isSelected = isSelected,
                                accentColor = accentColor,
                                onSelected = { onTrackSelected(trackId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Dialog header with gradient background
 */
@Composable
private fun DialogHeader(
    title: String,
    icon: ImageVector,
    accentColor: Color,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Compact icon container
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                accentColor.copy(alpha = 0.2f),
                                accentColor.copy(alpha = 0.05f)
                            )
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = accentColor.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                fontSize = 18.sp
            )
        }
        
        // Compact close button
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .size(36.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF2A2A2A),
                            Color(0xFF1A1A1A)
                        )
                    ),
                    shape = CircleShape
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.1f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Compact track item with full text display
 */
@Composable
private fun CompactTrackItem(
    title: String,
    subtitle: String,
    description: String,
    isSelected: Boolean,
    accentColor: Color,
    onSelected: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        ),
        label = "scale_animation"
    )
    
    val animatedBackgroundBrush by remember(isSelected, accentColor) {
        derivedStateOf {
            if (isSelected) {
                Brush.horizontalGradient(
                    colors = listOf(
                        accentColor.copy(alpha = 0.15f),
                        accentColor.copy(alpha = 0.08f)
                    )
                )
            } else {
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF1A1A1A).copy(alpha = 0.3f),
                        Color(0xFF0F0F0F).copy(alpha = 0.5f)
                    )
                )
            }
        }
    }
    
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.5f) else Color(0xFF333333),
        animationSpec = tween(300),
        label = "border_animation"
    )
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(animatedScale)
            .clip(RoundedCornerShape(12.dp))
            .background(animatedBackgroundBrush)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = animatedBorderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable {
                isPressed = true
                onSelected()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Small selection indicator
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(
                        color = if (isSelected) accentColor else Color.Transparent,
                        shape = CircleShape
                    )
                    .border(
                        width = 2.dp,
                        color = if (isSelected) accentColor else Color(0xFF555555),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
            
            // Compact track information
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Title - full display without truncation
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    lineHeight = 16.sp
                )
                
                // Subtitle with better visibility
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSelected) accentColor.copy(alpha = 0.9f) else Color(0xFFBBBBBB),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }
        }
    }
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(120)
            isPressed = false
        }
    }
}

/**
 * Data class for track display information
 */
private data class TrackDisplayInfo(
    val title: String,
    val subtitle: String,
    val description: String = ""
)

/**
 * Helper function to build audio track subtitle
 */
private fun buildAudioTrackSubtitle(track: AudioTrackInfo): String {
    return buildList {
        // Language
        track.language?.takeIf { it.isNotEmpty() && it != "Unknown" && it != "und" }?.let { 
            add(it.uppercase()) 
        }
        // Codec with full name
        track.codec?.takeIf { it.isNotEmpty() }?.let { codec ->
            val codecName = when (codec.lowercase()) {
                "aac" -> "AAC"
                "mp3" -> "MP3"
                "ac3" -> "Dolby Digital"
                "eac3" -> "Dolby Digital Plus"
                "truehd" -> "Dolby TrueHD"
                "dts" -> "DTS"
                "dtshd" -> "DTS-HD"
                "flac" -> "FLAC"
                "opus" -> "Opus"
                "vorbis" -> "Vorbis"
                else -> codec.uppercase()
            }
            add(codecName)
        }
        // Channel count
        if (track.channelCount > 0) {
            val channelText = when (track.channelCount) {
                1 -> "Mono"
                2 -> "Stereo"
                6 -> "5.1"
                8 -> "7.1"
                else -> "${track.channelCount}ch"
            }
            add(channelText)
        }
    }.joinToString(" • ")
}

/**
 * Helper function to build audio track description
 */
private fun buildAudioTrackDescription(track: AudioTrackInfo): String {
    return buildList {
        // Add quality indicators based on codec
        track.codec?.lowercase()?.let { codec ->
            when {
                codec.contains("truehd") -> add("Lossless Audio")
                codec.contains("flac") -> add("Lossless Audio")
                codec.contains("dts") -> add("High Quality Audio")
                codec.contains("eac3") -> add("Enhanced Audio")
                codec.contains("ac3") -> add("Standard Audio")
                codec.contains("aac") -> add("Compressed Audio")
                codec.contains("mp3") -> add("Basic Audio")
            }
        }
        
        // Add additional info if available
        if (track.channelCount >= 6) {
            add("Surround Sound")
        }
    }.joinToString(" • ")
}

/**
 * Helper function to build subtitle track subtitle
 */
private fun buildSubtitleTrackSubtitle(track: SubtitleTrackInfo): String {
    return buildList {
        // Language
        track.language?.takeIf { it.isNotEmpty() && it != "Unknown" && it != "und" }?.let { 
            add(it.uppercase()) 
        }
        // Track type indicators
        if (track.isForced) add("FORCED")
        if (track.isDefault) add("DEFAULT")
    }.joinToString(" • ")
}

/**
 * Helper function to build subtitle track description
 */
private fun buildSubtitleTrackDescription(track: SubtitleTrackInfo): String {
    return buildList {
        if (track.isForced) add("Forced subtitles")
        if (track.isDefault) add("Default track")
        if (!track.isForced && !track.isDefault) add("Optional subtitles")
    }.joinToString(" • ")
}