package com.jellycine.shared.ui.components.common

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaSourceInfo
import com.jellycine.data.model.MediaStream
import java.util.Locale

fun BaseItemDto.activeDetailMediaSources(): List<MediaSourceInfo> {
    val sources = mediaSources.orEmpty()
    val currentItemId = id?.takeIf { it.isNotBlank() } ?: return sources
    if (sources.size <= 1) return sources

    return sources
        .filter { source -> source.matchesItemId(currentItemId) }
        .ifEmpty { sources }
}

fun buildLocalVersionEntries(
    localVersions: List<BaseItemDto>,
    currentItemId: String?,
    videoFallbackLabel: String,
    smallFileSizeLabel: String
): List<Pair<String, BaseItemDto>> {
    val currentVersions = localVersions
        .takeIf { versions -> versions.any { version -> version.id == currentItemId } }
        .orEmpty()

    if (currentVersions.size <= 1) return emptyList()

    val orderedVersions = currentVersions.sortedByDescending { version -> version.id == currentItemId }
    return detailOptionLabels(
        orderedVersions.map { version ->
            version.localVersionVideoLabel(
                videoFallbackLabel = videoFallbackLabel,
                smallFileSizeLabel = smallFileSizeLabel
            )
        }
    )
        .zip(orderedVersions)
}

fun selectedVideoOption(
    localVersionEntries: List<Pair<String, BaseItemDto>>,
    currentItemId: String?,
    selectedVideo: String,
    videoOptions: List<String>,
    baseVideoOptions: List<String>
): String {
    return localVersionEntries
        .firstOrNull { (_, version) -> version.id == currentItemId }
        ?.first
        ?.takeIf { selectedVideo !in videoOptions || selectedVideo in baseVideoOptions }
        ?: selectedVideo.takeIf { it in videoOptions }
        ?: videoOptions.firstOrNull().orEmpty()
}

fun buildInlineText(
    mediaSources: List<MediaSourceInfo>,
    streams: List<MediaStream>,
    smallFileSizeLabel: String
): String? {
    val source = inlinePrimaryMediaSource(mediaSources)
    val parts = mutableListOf<String>()
    formatFileSize(
        sizeBytes = source?.size,
        smallFileSizeLabel = smallFileSizeLabel
    )?.let(parts::add)

    val fileBitrate = source?.bitrate?.toLong()
        ?: streams
            .sumOf { (it.bitRate ?: 0).toLong() }
            .takeIf { it > 0L }
    formatBitrate(fileBitrate)?.let(parts::add)

    return parts.takeIf { it.isNotEmpty() }?.joinToString(" / ")
}

private fun BaseItemDto.localVersionVideoLabel(
    videoFallbackLabel: String,
    smallFileSizeLabel: String
): String {
    val activeSources = activeDetailMediaSources()
    val streams = activeSources
        .flatMap { source -> source.mediaStreams.orEmpty() }
        .ifEmpty { mediaStreams.orEmpty() }
    val videoTitle = videoOptionLabels(streams).firstOrNull() ?: videoFallbackLabel
    val inlineText = buildInlineText(
        mediaSources = activeSources,
        streams = streams,
        smallFileSizeLabel = smallFileSizeLabel
    )
    return listOfNotNull(videoTitle, inlineText)
        .filter { it.isNotBlank() }
        .joinToString(" / ")
}

private fun videoOptionLabels(streams: List<MediaStream>): List<String> {
    return detailOptionLabels(
        streams
            .filter { it.type == "Video" }
            .sortedBy { it.index ?: Int.MAX_VALUE }
            .mapNotNull { stream -> stream.displayTitle?.takeIf { it.isNotBlank() } }
    )
}

private fun detailOptionLabels(options: List<String>): List<String> {
    val counts = mutableMapOf<String, Int>()
    return options.map { option ->
        val seen = (counts[option] ?: 0) + 1
        counts[option] = seen
        if (seen == 1) option else "$option ($seen)"
    }
}

private fun inlinePrimaryMediaSource(sources: List<MediaSourceInfo>): MediaSourceInfo? {
    return sources.firstOrNull { source ->
        !source.name.isNullOrBlank() ||
            !source.container.isNullOrBlank() ||
            source.size != null ||
            source.bitrate != null ||
            source.mediaStreams.orEmpty().isNotEmpty()
    } ?: sources.firstOrNull()
}

private fun MediaSourceInfo.matchesItemId(itemId: String): Boolean {
    val sourceId = id?.takeIf { it.isNotBlank() } ?: return false
    return sourceId.equals(itemId, ignoreCase = true) ||
        sourceId.equals("mediasource_$itemId", ignoreCase = true)
}

private fun formatFileSize(
    sizeBytes: Long?,
    smallFileSizeLabel: String
): String? {
    if (sizeBytes == null || sizeBytes <= 0) return null

    val gb = sizeBytes / (1024.0 * 1024.0 * 1024.0)
    val mb = sizeBytes / (1024.0 * 1024.0)

    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.0f MB", mb)
        else -> smallFileSizeLabel
    }
}

private fun formatBitrate(bitsPerSecond: Long?): String? {
    val value = bitsPerSecond?.takeIf { it > 0L } ?: return null
    return if (value >= 1_000_000L) {
        "${String.format(Locale.US, "%.1f", value / 1_000_000.0)} Mbps"
    } else {
        "${value / 1000L} kbps"
    }
}