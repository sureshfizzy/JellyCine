package com.jellycine.data.api

import com.jellycine.data.model.ClipFeedItem
import com.jellycine.data.model.MediaExtra
import com.jellycine.data.model.TmdbFindResponse
import com.jellycine.data.model.TmdbImage
import com.jellycine.data.model.TmdbImagesResponse
import com.jellycine.data.model.TmdbReview
import com.jellycine.data.model.TmdbReviewsResponse
import com.jellycine.data.model.TmdbGenreListResponse
import com.jellycine.data.model.TmdbTrendingItem
import com.jellycine.data.model.TmdbTrendingResponse
import com.jellycine.data.model.TmdbVideo
import com.jellycine.data.model.TmdbVideosResponse
import com.jellycine.data.model.toMediaExtras
import com.jellycine.data.model.toRawVideos
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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

    private suspend fun fetchTrendingClipsPage(
        page: Int,
        window: String = "week",
        maxTitles: Int = 20,
        maxClipsPerTitle: Int = 2
    ): List<ClipFeedItem> = coroutineScope {
        val trending = runCatching {
            client.get("https://api.themoviedb.org/3/trending/all/$window") {
                parameter("api_key", apiKey)
                parameter("page", page)
                parameter("language", "en-US")
            }.body<TmdbTrendingResponse>().results
        }.getOrDefault(emptyList())
            .filter { it.id > 0 && (it.mediaType == "movie" || it.mediaType == "tv") }
            .take(maxTitles)

        val genreMap = genreMap()

        trending
            .map { title ->
                async {
                    val mediaType = title.mediaType ?: return@async emptyList()
                    val name = title.title?.takeIf { it.isNotBlank() }
                        ?: title.name?.takeIf { it.isNotBlank() }
                        ?: return@async emptyList()
                    val genres = title.genreIds.mapNotNull { genreMap[it] }
                    fetchVideosRaw(mediaType, title.id.toString())
                        .filter { video ->
                            video.site.equals("YouTube", ignoreCase = true) &&
                                video.official == true &&
                                !video.key.isNullOrBlank() &&
                                video.type?.lowercase() in CLIP_FEED_TYPES
                        }
                        .distinctBy { it.key }
                        .sortedBy { clipTypeRank(it.type) }
                        .take(maxClipsPerTitle)
                        .map { video ->
                            ClipFeedItem(
                                tmdbId = title.id.toString(),
                                mediaType = mediaType,
                                title = name,
                                youtubeKey = video.key!!,
                                clipName = video.name?.takeIf { it.isNotBlank() } ?: name,
                                clipType = video.type ?: "Trailer",
                                backdropUrl = imageUrl(title.backdropPath, "w780"),
                                posterUrl = imageUrl(title.posterPath, "w342"),
                                overview = title.overview?.takeIf { it.isNotBlank() },
                                genres = genres,
                                publishedAt = video.publishedAt
                            )
                        }
                }
            }
            .awaitAll()
            .flatten()
            .distinctBy { it.youtubeKey }
            .shuffled()
    }

    private val usedTrendingPages = mutableSetOf<Int>()

    suspend fun fetchTrendingClips(): List<ClipFeedItem> = coroutineScope {
        val pool = (TRENDING_PAGE_RANGE.toSet() - usedTrendingPages).ifEmpty {
            usedTrendingPages.clear()
            TRENDING_PAGE_RANGE.toSet()
        }
        val pages = pool.shuffled().take(PAGES_PER_BATCH).also { usedTrendingPages += it }
        pages.map { async { fetchTrendingClipsPage(it) } }
            .awaitAll()
            .flatten()
            .distinctBy { it.youtubeKey }
            .shuffled()
    }

    private var cachedGenreMap: Map<Int, String>? = null

    private suspend fun genreMap(): Map<Int, String> = coroutineScope {
        cachedGenreMap?.let { return@coroutineScope it }
        val movie = async { fetchGenreList("movie") }
        val tv = async { fetchGenreList("tv") }
        val merged = (movie.await() + tv.await())
        if (merged.isNotEmpty()) cachedGenreMap = merged
        merged
    }

    private suspend fun fetchGenreList(mediaType: String): Map<Int, String> = runCatching {
        client.get("https://api.themoviedb.org/3/genre/$mediaType/list") {
            parameter("api_key", apiKey)
            parameter("language", "en-US")
        }.body<TmdbGenreListResponse>()
            .genres
            .mapNotNull { entry -> entry.name?.takeIf { it.isNotBlank() }?.let { entry.id to it } }
            .toMap()
    }.getOrDefault(emptyMap())

    private suspend fun fetchVideosRaw(mediaType: String, tmdbId: String): List<TmdbVideo> = runCatching {
        client.get("https://api.themoviedb.org/3/$mediaType/$tmdbId/videos") {
            parameter("api_key", apiKey)
        }.body<TmdbVideosResponse>().results
    }.getOrDefault(emptyList())

    suspend fun fetchReviews(mediaType: String, tmdbId: String): List<TmdbReview> = runCatching {
        client.get("https://api.themoviedb.org/3/$mediaType/$tmdbId/reviews") {
            parameter("api_key", apiKey)
            parameter("language", "en-US")
        }.body<TmdbReviewsResponse>().results
    }.getOrDefault(emptyList())

    // Maps an external id (IMDb/TheTVDB) to a TMDB id via /find, for items scraped without a Tmdb id.
    suspend fun findTmdbId(externalId: String, externalSource: String, mediaType: String): String? = runCatching {
        val response = client.get("https://api.themoviedb.org/3/find/$externalId") {
            parameter("api_key", apiKey)
            parameter("external_source", externalSource)
        }.body<TmdbFindResponse>()
        val results = if (mediaType == "tv") response.tvResults else response.movieResults
        results.firstOrNull()?.id?.takeIf { it > 0 }?.toString()
    }.getOrNull()

    private companion object {
        private const val TMDB_API_KEY = "4219e299c89411838049ab0dab19ebd5"
        private val CLIP_FEED_TYPES = setOf("trailer", "clip", "teaser")
        private val TRENDING_PAGE_RANGE = 1..15
        private const val PAGES_PER_BATCH = 3

        private fun clipTypeRank(type: String?): Int = when (type?.lowercase()) {
            "clip" -> 0
            "teaser" -> 1
            "trailer" -> 2
            else -> 3
        }

        private fun imageUrl(imagePath: String?, size: String): String? {
            val path = imagePath?.takeIf { it.isNotBlank() } ?: return null
            return "https://image.tmdb.org/t/p/$size/${path.removePrefix("/")}"
        }
    }
}