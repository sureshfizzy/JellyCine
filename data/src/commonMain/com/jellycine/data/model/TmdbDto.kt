package com.jellycine.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class TmdbImagesResponse(
    val logos: List<TmdbImage> = emptyList()
)

@Serializable
internal data class TmdbImage(
    @SerialName("file_path")
    val filePath: String? = null,
    @SerialName("iso_639_1")
    val iso6391: String? = null,
    @SerialName("vote_average")
    val voteAverage: Double? = null,
    @SerialName("vote_count")
    val voteCount: Int? = null
)

@Serializable
internal data class TmdbVideosResponse(
    val results: List<TmdbVideo> = emptyList()
)

@Serializable
internal data class TmdbVideo(
    val key: String? = null,
    val name: String? = null,
    val site: String? = null,
    val type: String? = null,
    val official: Boolean? = null
)

internal fun List<TmdbVideo>.toRawVideos(): List<RawVideo> =
    map { video ->
        RawVideo(
            key = video.key,
            name = video.name,
            site = video.site,
            type = video.type,
            official = video.official
        )
    }