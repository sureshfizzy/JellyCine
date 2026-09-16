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

@Serializable
internal data class TmdbReviewsResponse(
    val results: List<TmdbReview> = emptyList()
)

@Serializable
data class TmdbReview(
    val id: String = "",
    val author: String = "",
    @SerialName("author_details")
    val authorDetails: TmdbReviewAuthor? = null,
    val content: String = "",
    @SerialName("created_at")
    val createdAt: String? = null
) {
    val displayName: String
        get() = authorDetails?.name?.takeIf { it.isNotBlank() }
            ?: authorDetails?.username?.takeIf { it.isNotBlank() }
            ?: author

    val rating: Double?
        get() = authorDetails?.rating

    val avatarUrl: String?
        get() {
            val path = authorDetails?.avatarPath?.takeIf { it.isNotBlank() } ?: return null
            // TMDB sometimes stores Gravatar avatars as a "/https://..." path.
            val cleaned = path.removePrefix("/")
            return if (cleaned.startsWith("http", ignoreCase = true)) {
                cleaned
            } else {
                "https://image.tmdb.org/t/p/w185/$cleaned"
            }
        }
}

@Serializable
internal data class TmdbFindResponse(
    @SerialName("movie_results")
    val movieResults: List<TmdbFindResult> = emptyList(),
    @SerialName("tv_results")
    val tvResults: List<TmdbFindResult> = emptyList()
)

@Serializable
internal data class TmdbFindResult(
    val id: Long = 0
)

@Serializable
data class TmdbReviewAuthor(
    val name: String? = null,
    val username: String? = null,
    @SerialName("avatar_path")
    val avatarPath: String? = null,
    val rating: Double? = null
)