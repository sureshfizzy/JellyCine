package com.vela.app.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vela.data.model.MediaStream
import com.vela.player.core.defaultSubtitleDisplayTitle
import com.vela.player.core.mediaStreamDisplayTitles

@Composable
internal fun TrackSection(
    displayedSelectedVideo: String,
    videoOptions: List<String>,
    videoInlineMetaText: String?,
    selectedAudio: String,
    audioOptions: List<String>,
    selectedSubtitle: String,
    subtitleOptions: List<String>,
    onVideoOptionSelected: (String) -> Unit,
    onAudioOptionSelected: (String) -> Unit,
    onSubtitleOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val hasVideoSection = videoOptions.isNotEmpty()
    val hasAudioSection = audioOptions.isNotEmpty()
    val hasSubtitleSection = subtitleOptions.size > 1

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasVideoSection) {
            TrackField(
                modifier = Modifier.weight(1.2f),
                label = "Video",
                selectedOption = displayedSelectedVideo,
                options = videoOptions,
                inlineMetaText = videoInlineMetaText,
                onOptionSelected = onVideoOptionSelected
            )
        }

        if (hasAudioSection) {
            TrackField(
                modifier = Modifier.weight(1f),
                label = "Audio",
                selectedOption = selectedAudio,
                options = audioOptions,
                onOptionSelected = onAudioOptionSelected
            )
        }

        if (hasSubtitleSection) {
            TrackField(
                modifier = Modifier.weight(0.8f),
                label = "Subtitles",
                selectedOption = selectedSubtitle,
                options = subtitleOptions,
                onOptionSelected = onSubtitleOptionSelected
            )
        }
    }
}

@Composable
internal fun TrackField(
    label: String,
    selectedOption: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    inlineMetaText: String? = null,
    onOptionSelected: (String) -> Unit
) {
    if (options.isEmpty()) return

    if (options.size > 1) {
        OptionSelectorRow(
            modifier = modifier,
            label = label,
            selectedOption = selectedOption,
            options = options,
            inlineMetaText = inlineMetaText,
            onOptionSelected = onOptionSelected
        )
    } else {
        val value = if (!inlineMetaText.isNullOrBlank()) {
            "${options.first()} / $inlineMetaText"
        } else {
            options.first()
        }
        Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = value,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OptionSelectorRow(
    label: String,
    selectedOption: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    inlineMetaText: String? = null,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.width(10.dp))

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it }
        ) {
            Surface(
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
                    .heightIn(min = 34.dp)
                    .onFocusChanged { isFocused = it.isFocused }
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown) {
                            when (event.key) {
                                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                    expanded = true
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
                    .focusable(),
                color = if (isFocused) Color(0xFF3A3A40) else Color(0xFF2A2A2E),
                shape = RoundedCornerShape(8.dp),
                border = if (isFocused) BorderStroke(1.5.dp, Color.White.copy(alpha = 0.6f)) else null
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val displayText = buildString {
                        append(selectedOption.ifBlank { options.firstOrNull().orEmpty() })
                        if (!inlineMetaText.isNullOrBlank()) {
                            append(" / ")
                            append(inlineMetaText)
                        }
                    }
                    Text(
                        text = displayText,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Select $label",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.widthIn(min = 200.dp)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

internal fun buildVideoOptions(streams: List<MediaStream>): List<String> {
    return OptionLabels(mediaStreamDisplayTitles(streams, "Video"))
}

internal fun buildAudioOptions(streams: List<MediaStream>): List<String> {
    return OptionLabels(mediaStreamDisplayTitles(streams, "Audio"))
}

internal fun buildSubtitleOptions(streams: List<MediaStream>): List<String> {
    val options = mutableListOf("Off")
    options += mediaStreamDisplayTitles(streams, "Subtitle")
    return OptionLabels(options)
}

internal fun buildDefaultSubtitleOption(streams: List<MediaStream>): String {
    return defaultSubtitleDisplayTitle(streams)
}

internal fun OptionLabels(options: List<String>): List<String> {
    val counts = mutableMapOf<String, Int>()
    return options.map { option ->
        val seen = (counts[option] ?: 0) + 1
        counts[option] = seen
        if (seen == 1) option else "$option ($seen)"
    }
}

internal fun AudioStreamIndex(
    streams: List<MediaStream>,
    selectedOption: String
): Int? {
    val audioStreams = streams
        .filter { it.type == "Audio" }
        .sortedBy { it.index ?: Int.MAX_VALUE }
    if (audioStreams.isEmpty()) return null
    if (selectedOption.isBlank()) return audioStreams.firstOrNull()?.index
    val audioOptions = buildAudioOptions(streams)
    val optionOrdinal = audioOptions.indexOf(selectedOption)
    if (optionOrdinal < 0 || optionOrdinal >= audioStreams.size) return null
    return audioStreams[optionOrdinal].index
}

internal fun SubtitleStreamIndex(
    streams: List<MediaStream>,
    selectedOption: String
): Int? {
    if (selectedOption == "Off") return -1

    val subtitleStreams = streams
        .filter { it.type == "Subtitle" }
        .sortedBy { it.index ?: Int.MAX_VALUE }
    if (subtitleStreams.isEmpty()) return null
    if (selectedOption.isBlank()) return subtitleStreams.firstOrNull()?.index
    val subtitleOptions = buildSubtitleOptions(streams).drop(1)
    val optionOrdinal = subtitleOptions.indexOf(selectedOption)
    if (optionOrdinal < 0 || optionOrdinal >= subtitleStreams.size) return null
    return subtitleStreams[optionOrdinal].index
}

internal fun AudioStreamIndex(
    streams: List<MediaStream>,
    streamIndex: Int?
): String? {
    if (streamIndex == null) return null

    val audioStreams = streams
        .filter { it.type == "Audio" }
        .sortedBy { it.index ?: Int.MAX_VALUE }
    val streamOrdinal = audioStreams.indexOfFirst { it.index == streamIndex }
    if (streamOrdinal < 0) return null
    return buildAudioOptions(streams).getOrNull(streamOrdinal)
}

internal fun SubtitleStreamIndex(
    streams: List<MediaStream>,
    streamIndex: Int?
): String? {
    if (streamIndex == null) return null
    if (streamIndex == -1) return "Off"

    val subtitleStreams = streams
        .filter { it.type == "Subtitle" }
        .sortedBy { it.index ?: Int.MAX_VALUE }
    val streamOrdinal = subtitleStreams.indexOfFirst { it.index == streamIndex }
    if (streamOrdinal < 0) return null
    val subtitleOptions = buildSubtitleOptions(streams).drop(1)
    return subtitleOptions.getOrNull(streamOrdinal)
}
