package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.MediaStream
import com.jellycine.player.core.defaultSubtitleDisplayTitle
import com.jellycine.player.core.mediaStreamDisplayTitles

@Composable
internal fun TrackSection(
    isWidescreenLayout: Boolean,
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
    val trackFieldCount = listOf(hasVideoSection, hasAudioSection, hasSubtitleSection)
        .count { it }
    val tabletTrackFieldMaxWidth = when (trackFieldCount) {
        3 -> 230.dp
        2 -> 300.dp
        else -> 360.dp
    }

    if (isWidescreenLayout) {
        if (hasVideoSection || hasAudioSection || hasSubtitleSection) {
            Row(
                modifier = modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasVideoSection) {
                    val sharesRow = hasSubtitleSection || hasAudioSection
                    TrackField(
                        modifier = if (sharesRow) {
                            Modifier.widthIn(max = tabletTrackFieldMaxWidth)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                        label = "Video",
                        selectedOption = displayedSelectedVideo,
                        options = videoOptions,
                        inlineMetaText = videoInlineMetaText,
                        singleValueFillWidth = !sharesRow,
                        onOptionSelected = onVideoOptionSelected
                    )
                }

                if (hasAudioSection) {
                    val sharesRow = hasSubtitleSection || hasVideoSection
                    TrackField(
                        modifier = if (sharesRow) {
                            Modifier.widthIn(max = tabletTrackFieldMaxWidth)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                        label = "Audio",
                        selectedOption = selectedAudio,
                        options = audioOptions,
                        singleValueFillWidth = !sharesRow,
                        onOptionSelected = onAudioOptionSelected
                    )
                }

                if (hasSubtitleSection) {
                    val sharesRow = hasVideoSection || hasAudioSection
                    TrackField(
                        modifier = if (sharesRow) {
                            Modifier.widthIn(max = tabletTrackFieldMaxWidth)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                        label = "Subtitles",
                        selectedOption = selectedSubtitle,
                        options = subtitleOptions,
                        singleValueFillWidth = !sharesRow,
                        onOptionSelected = onSubtitleOptionSelected
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    } else {
        if (hasVideoSection) {
            TrackField(
                label = "Video",
                selectedOption = displayedSelectedVideo,
                options = videoOptions,
                inlineMetaText = videoInlineMetaText,
                onOptionSelected = onVideoOptionSelected
            )
        }

        if (hasVideoSection && hasAudioSection) {
            Spacer(modifier = Modifier.height(4.dp))
        }

        if (hasAudioSection) {
            TrackField(
                label = "Audio",
                selectedOption = selectedAudio,
                options = audioOptions,
                onOptionSelected = onAudioOptionSelected
            )
        }

        if (hasSubtitleSection) {
            Spacer(modifier = Modifier.height(4.dp))
            TrackField(
                label = "Subtitles",
                selectedOption = selectedSubtitle,
                options = subtitleOptions,
                onOptionSelected = onSubtitleOptionSelected
            )
        }
    }
}


@Composable
internal fun DetailInfoRow(
    label: String,
    value: String,
    fillWidth: Boolean = true
) {
    Row(
        modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label  ",
            fontSize = 13.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun TrackField(
    label: String,
    selectedOption: String,
    options: List<String>,
    modifier: Modifier = Modifier,
    inlineMetaText: String? = null,
    singleValueFillWidth: Boolean = true,
    onOptionSelected: (String) -> Unit
) {
    if (options.isEmpty()) return

    Box(modifier = modifier) {
        if (options.size > 1) {
            OptionSelectorRow(
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
            DetailInfoRow(
                label = label,
                value = value,
                fillWidth = singleValueFillWidth
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
    inlineMetaText: String? = null,
    onOptionSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label  ",
            fontSize = 13.sp,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )

        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.fillMaxWidth()
        ) {
            Surface(
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true
                    )
                    .fillMaxWidth()
                    .heightIn(min = 38.dp),
                color = Color(0xFF1F1F24),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
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
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Select $label",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
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