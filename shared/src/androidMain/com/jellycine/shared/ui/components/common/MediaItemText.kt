package com.jellycine.shared.ui.components.common

import com.jellycine.data.model.BaseItemDto

fun BaseItemDto.preferredDisplayTitle(
    unknownTitle: String,
    unknownEpisode: String = unknownTitle
): String {
    val cleanName = name?.takeIf { it.isNotBlank() }
    val cleanSeriesName = seriesName?.takeIf { it.isNotBlank() }
    val cleanSeasonName = seasonName?.takeIf { it.isNotBlank() }
    val cleanEpisodeTitle = episodeTitle?.takeIf { it.isNotBlank() }

    return when (type) {
        "Episode" -> cleanSeriesName ?: cleanName ?: cleanEpisodeTitle ?: unknownEpisode
        "Season" -> cleanSeriesName ?: cleanSeasonName ?: cleanName ?: unknownTitle
        else -> cleanName ?: unknownTitle
    }
}

fun BaseItemDto.episodeSeriesTitle(
    unknownTitle: String,
    unknownEpisode: String = unknownTitle
): String {
    return seriesName?.takeIf { it.isNotBlank() }
        ?: preferredDisplayTitle(unknownTitle, unknownEpisode)
}

fun BaseItemDto.episodeDisplaySubtitle(fallback: String = ""): String {
    val cleanName = name?.takeIf { it.isNotBlank() }
    val cleanEpisodeTitle = episodeTitle?.takeIf { it.isNotBlank() }
    val episodeLabel = when {
        !cleanEpisodeTitle.isNullOrBlank() -> cleanEpisodeTitle
        !cleanName.isNullOrBlank() && cleanName != seriesName -> cleanName
        else -> fallback
    }
    val seasonEpisodeCode = when {
        parentIndexNumber != null && indexNumber != null ->
            "S${parentIndexNumber.toString().padStart(2, '0')} E${indexNumber.toString().padStart(2, '0')}"
        parentIndexNumber != null -> "S${parentIndexNumber.toString().padStart(2, '0')}"
        indexNumber != null -> "E${indexNumber.toString().padStart(2, '0')}"
        !seasonName.isNullOrBlank() -> seasonName.orEmpty()
        else -> ""
    }

    return listOfNotNull(
        seasonEpisodeCode.takeIf { it.isNotBlank() },
        episodeLabel.takeIf { it.isNotBlank() && !(it == fallback && seasonEpisodeCode.isBlank()) }
    ).joinToString(" - ").ifBlank { fallback }
}