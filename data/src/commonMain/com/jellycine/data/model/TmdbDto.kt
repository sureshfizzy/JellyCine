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