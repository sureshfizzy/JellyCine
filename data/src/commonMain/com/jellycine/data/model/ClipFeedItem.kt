package com.jellycine.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ClipFeedItem(
    val tmdbId: String,
    val mediaType: String,
    val title: String,
    val youtubeKey: String,
    val clipName: String,
    val clipType: String,
    val backdropUrl: String? = null,
    val posterUrl: String? = null,
    val overview: String? = null,
    val genres: List<String> = emptyList(),
    val publishedAt: String? = null
) {
    val youtubeWatchUrl: String
        get() = "https://www.youtube.com/watch?v=$youtubeKey"

    val thumbnailUrl: String
        get() = "https://img.youtube.com/vi/$youtubeKey/hqdefault.jpg"

    val genreLine: String
        get() = genres.take(3).joinToString(" · ")
}