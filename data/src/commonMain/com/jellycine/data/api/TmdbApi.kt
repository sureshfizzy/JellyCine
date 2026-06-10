package com.jellycine.data.api

import com.jellycine.data.model.MediaExtra
import com.jellycine.data.model.TmdbImage
import com.jellycine.data.model.TmdbImagesResponse
import com.jellycine.data.model.TmdbVideosResponse
import com.jellycine.data.model.toMediaExtras
import com.jellycine.data.model.toRawVideos
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal data class TmdbTitleSummary(
    val title: String,
    val posterUrl: String?,
    val year: Int?
)

@Serializable
internal data class TmdbTitleDetail(
    val title: String? = null,
    val name: String? = null,
    val overview: String? = null,
    val tagline: String? = null,
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("first_air_date") val firstAirDate: String? = null,
    val runtime: Int? = null,
    @SerialName("episode_run_time") val episodeRunTime: List<Int> = emptyList(),
    @SerialName("vote_average") val voteAverage: Double? = null,
    val genres: List<TmdbGenre> = emptyList()
)

@Serializable
internal data class TmdbGenre(val name: String? = null)

internal class TmdbApi(
    private val client: HttpClient,
    private val apiKey: String = TMDB_API_KEY
) {
    suspend fun titleDetail(mediaType: String, tmdbId: String): TmdbTitleDetail? = runCatching {
        client.get("https://api.themoviedb.org/3/$mediaType/$tmdbId") {
            parameter("api_key", apiKey)
            parameter("language", "en-US")
        }.body<TmdbTitleDetail>()
    }.getOrNull()

    suspend fun titleSummary(mediaType: String, tmdbId: String): TmdbTitleSummary? {
        val detail = titleDetail(mediaType, tmdbId) ?: return null
        val title = detail.title?.takeIf { it.isNotBlank() }
            ?: detail.name?.takeIf { it.isNotBlank() }
            ?: return null
        val year = (detail.releaseDate ?: detail.firstAirDate)
            ?.takeIf { it.length >= 4 }
            ?.substring(0, 4)
            ?.toIntOrNull()
        return TmdbTitleSummary(title = title, posterUrl = imageUrl(detail.posterPath, "w500"), year = year)
    }

    suspend fun titleLogoPath(mediaType: String, tmdbId: String): String? = runCatching {
        client.get("https://api.themoviedb.org/3/$mediaType/$tmdbId/images") {
            parameter("api_key", apiKey)
        }.body<TmdbImagesResponse>()
            .logos
            .asSequence()
            .filter { image -> !image.filePath.isNullOrBlank() }
            .sortedWith(
                compareByDescending<TmdbImage> { image -> image.iso6391 == "en" }
                    .thenByDescending { image -> image.iso6391 == null }
                    .thenByDescending { image -> image.voteAverage ?: 0.0 }
                    .thenByDescending { image -> image.voteCount ?: 0 }
            )
            .firstOrNull()
            ?.filePath
    }.getOrNull()

    suspend fun titleLogoUrl(
        mediaType: String,
        tmdbId: String,
        size: String = "original"
    ): String? {
        return imageUrl(titleLogoPath(mediaType, tmdbId), size)
    }

    suspend fun fetchExtras(mediaType: String, tmdbId: String): List<MediaExtra> = runCatching {
        client.get("https://api.themoviedb.org/3/$mediaType/$tmdbId/videos") {
            parameter("api_key", apiKey)
        }.body<TmdbVideosResponse>()
            .results
            .toRawVideos()
            .toMediaExtras()
    }.getOrDefault(emptyList())

    private companion object {
        private const val TMDB_API_KEY = "4219e299c89411838049ab0dab19ebd5"

        private fun imageUrl(imagePath: String?, size: String): String? {
            val path = imagePath?.takeIf { it.isNotBlank() } ?: return null
            return "https://image.tmdb.org/t/p/$size/${path.removePrefix("/")}"
        }
    }
}