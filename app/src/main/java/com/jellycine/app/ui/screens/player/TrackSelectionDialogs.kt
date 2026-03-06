package com.jellycine.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jellycine.player.core.AudioTrackInfo
import com.jellycine.player.core.SubtitleTrackInfo

@Composable
fun AudioTrackSelectionDialog(
    isVisible: Boolean,
    audioTracks: List<AudioTrackInfo>,
    currentAudioTrack: AudioTrackInfo?,
    onTrackSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    TrackSelectionDialog(
        title = "Audio",
        helperText = "Select preferred playback track",
        icon = Icons.Rounded.GraphicEq,
        accentColor = Color(0xFF00A9D6),
        tracks = audioTracks,
        currentTrack = currentAudioTrack,
        onTrackSelected = onTrackSelected,
        onDismiss = onDismiss,
        trackKey = { track -> track.id },
        isTrackSelected = { track, selected -> track.id == selected?.id },
        trackDisplayInfo = { track ->
            TrackDisplayInfo(
                title = track.label.takeIf { it.isNotBlank() }.orEmpty(),
                subtitle = buildAudioTrackSubtitle(track),
                description = buildAudioTrackDescription(track)
            )
        }
    )
}

@Composable
fun SubtitleTrackSelectionDialog(
    isVisible: Boolean,
    subtitleTracks: List<SubtitleTrackInfo>,
    currentSubtitleTrack: SubtitleTrackInfo?,
    onTrackSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    TrackSelectionDialog(
        title = "Subtitles",
        helperText = "Select subtitle track",
        icon = Icons.Rounded.ClosedCaption,
        accentColor = Color(0xFFFF6B3B),
        tracks = subtitleTracks,
        currentTrack = currentSubtitleTrack,
        onTrackSelected = onTrackSelected,
        onDismiss = onDismiss,
        trackKey = { track -> track.id },
        isTrackSelected = { track, selected -> track.id == selected?.id },
        trackDisplayInfo = { track ->
            TrackDisplayInfo(
                title = track.label.takeIf { it.isNotBlank() }.orEmpty(),
                subtitle = buildSubtitleTrackSubtitle(track),
                description = buildSubtitleTrackDescription(track)
            )
        }
    )
}

@Composable
fun StreamingQualitySelectionDialog(
    isVisible: Boolean,
    qualityOptions: List<String>,
    currentQuality: String,
    onQualitySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    val options = qualityOptions.map { quality ->
        StreamingQualityOption(
            id = quality,
            label = quality,
            description = if (quality.equals("Original", ignoreCase = true)) {
                "No bitrate cap"
            } else {
                ""
            }
        )
    }
    val selectedOption = options.firstOrNull { it.id == currentQuality }

    TrackSelectionDialog(
        title = "Streaming Quality",
        helperText = "",
        icon = Icons.Rounded.Tune,
        accentColor = Color(0xFF3B82F6),
        tracks = options,
        currentTrack = selectedOption,
        onTrackSelected = onQualitySelected,
        onDismiss = onDismiss,
        trackKey = { option -> option.id },
        isTrackSelected = { option, selected -> option.id == selected?.id },
        trackDisplayInfo = { option ->
            TrackDisplayInfo(
                title = option.label,
                subtitle = option.description,
                description = ""
            )
        }
    )
}

@Composable
private fun <T> TrackSelectionDialog(
    title: String,
    helperText: String,
    icon: ImageVector,
    accentColor: Color,
    tracks: List<T>,
    currentTrack: T?,
    onTrackSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    trackKey: (T) -> String,
    isTrackSelected: (T, T?) -> Boolean,
    trackDisplayInfo: (T) -> TrackDisplayInfo
) where T : Any {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        HideSystemBarsForDialogWindow()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.64f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints {
                val isLandscape = maxWidth > maxHeight
                val dialogWidthFraction = if (isLandscape) 0.68f else 0.84f
                val dialogMaxWidth: Dp = if (isLandscape) 380.dp else 460.dp
                val listMaxHeight: Dp = if (isLandscape) 220.dp else 300.dp

                Surface(
                    modifier = Modifier
                        .fillMaxWidth(dialogWidthFraction)
                        .widthIn(max = dialogMaxWidth)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        ),
                    shape = RoundedCornerShape(24.dp),
                    tonalElevation = 12.dp,
                    shadowElevation = 22.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        width = 1.dp,
                        color = accentColor.copy(alpha = 0.25f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        DialogHeader(
                            title = title,
                            helperText = helperText,
                            icon = icon,
                            accentColor = accentColor,
                            trackCount = tracks.size,
                            onClose = onDismiss
                        )

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))

                        if (tracks.isEmpty()) {
                            EmptyState()
                        } else {
                            LazyColumn(
                                modifier = Modifier.heightIn(max = listMaxHeight),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(
                                    items = tracks,
                                    key = { _, track ->
                                        trackKey(track)
                                    }
                                ) { index, track ->
                                    val displayInfo = trackDisplayInfo(track)
                                    val isSelected = isTrackSelected(track, currentTrack)
                                    val trackId = trackKey(track)

                                    TrackRow(
                                        indexLabel = (index + 1).toString(),
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
    }
}

@Composable
private fun HideSystemBarsForDialogWindow() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        window?.let { dialogWindow ->
            val controller = WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose { }
    }
}

@Composable
private fun DialogHeader(
    title: String,
    helperText: String,
    icon: ImageVector,
    accentColor: Color,
    trackCount: Int,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = accentColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = accentColor.copy(alpha = 0.34f),
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

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val helperLine = if (helperText.isBlank()) {
                    "$trackCount tracks"
                } else {
                    "$trackCount tracks - $helperText"
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = helperLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrackRow(
    indexLabel: String,
    title: String,
    subtitle: String,
    description: String,
    isSelected: Boolean,
    accentColor: Color,
    onSelected: () -> Unit
) {
    val containerColor = if (isSelected) {
        accentColor.copy(alpha = 0.11f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.46f)
    }

    val borderColor = if (isSelected) {
        accentColor.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onSelected),
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surface,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
                )
            ) {
                Text(
                    text = indexLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (title.isNotEmpty()) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (description.isNotEmpty()) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            SelectionIndicator(
                isSelected = isSelected,
                accentColor = accentColor
            )
        }
    }
}

@Composable
private fun SelectionIndicator(
    isSelected: Boolean,
    accentColor: Color
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .background(
                color = if (isSelected) accentColor else Color.Transparent,
                shape = CircleShape
            )
            .border(
                width = 1.5.dp,
                color = if (isSelected) accentColor else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
        )
    ) {
        Text(
            text = "No tracks available",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)
        )
    }
}

private data class TrackDisplayInfo(
    val title: String,
    val subtitle: String,
    val description: String = ""
)

private data class StreamingQualityOption(
    val id: String,
    val label: String,
    val description: String
)

private fun buildAudioTrackSubtitle(track: AudioTrackInfo): String {
    return buildList {
        track.language?.takeIf { it.isNotEmpty() && !it.equals("und", ignoreCase = true) }?.let {
            add(it.uppercase())
        }

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
    }.joinToString(" | ")
}

private fun buildAudioTrackDescription(track: AudioTrackInfo): String {
    return buildList {
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

        if (track.channelCount >= 6) {
            add("Surround Sound")
        }
    }.joinToString(" | ")
}

private fun buildSubtitleTrackSubtitle(track: SubtitleTrackInfo): String {
    return buildList {
        track.language?.takeIf {
            it.isNotEmpty() &&
                !it.equals("und", ignoreCase = true)
        }?.let {
            add(it.uppercase())
        }
        if (track.isForced) add("FORCED")
        if (track.isDefault) add("DEFAULT")
    }.joinToString(" | ")
}

private fun buildSubtitleTrackDescription(track: SubtitleTrackInfo): String {
    return buildList {
        if (track.isForced) add("Forced subtitles")
        if (track.isDefault) add("Default track")
    }.joinToString(" | ")
}
