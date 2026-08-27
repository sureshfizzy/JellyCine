package com.vela.player.discord

data class NowPlayingInfo(
    val mediaId: String,
    val title: String,
    val seriesName: String? = null,
    val seasonEpisodeLabel: String? = null,
    val year: Int? = null,
    val mediaType: MediaType = MediaType.MOVIE,
    val startTimestampMs: Long = System.currentTimeMillis(),
    val imageUrl: String? = null
) {
    enum class MediaType {
        MOVIE, EPISODE, MUSIC, LIVE_TV
    }

    val discordDetails: String
        get() = when (mediaType) {
            MediaType.EPISODE -> seriesName ?: title
            else -> buildString {
                append(title)
                year?.let { append(" ($it)") }
            }
        }

    val discordState: String?
        get() = when (mediaType) {
            MediaType.EPISODE -> seasonEpisodeLabel
            else -> null
        }
}