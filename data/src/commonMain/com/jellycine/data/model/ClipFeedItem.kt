package com.jellycine.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ClipFeedItem(
    val tmdbId: String,
    val mediaType: String,
    val title: String,
    val youtubeKey: String = "",
    val backdropUrl: String? = null,
    val posterUrl: String? = null,
    val overview: String? = null,
    val genres: List<String> = emptyList()
) {
    val youtubeWatchUrl: String
        get() = "https://www.youtube.com/watch?v=$youtubeKey"

    val thumbnailUrl: String
        get() = "https://img.youtube.com/vi/$youtubeKey/hqdefault.jpg"

    val genreLine: String
        get() = genres.take(3).joinToString(" · ")
}
/** Server-side filters applied when fetching the clips feed from TMDB. */
data class ClipFeedFilters(
    val window: String = "week",
    val mediaType: String = "all",
    val language: String = "en-US",
    val excludedGenreIds: Set<Int> = emptySet()
)
