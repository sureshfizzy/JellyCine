package com.jellycine.player.core

import com.jellycine.data.model.MediaStream

fun MediaStream.displayTitleOrNull(): String? {
    return displayTitle?.takeIf { it.isNotBlank() }
}

fun mediaStreamDisplayTitles(
    streams: List<MediaStream>,
    streamType: String
): List<String> {
    return streams
        .filter { it.type == streamType }
        .sortedBy { it.index ?: Int.MAX_VALUE }
        .mapNotNull { it.displayTitleOrNull() }
}

fun defaultSubtitleDisplayTitle(streams: List<MediaStream>): String {
    return streams
        .filter { it.type == "Subtitle" }
        .sortedBy { it.index ?: Int.MAX_VALUE }
        .firstOrNull { it.isDefault == true }
        ?.displayTitleOrNull()
        ?: "Off"
}