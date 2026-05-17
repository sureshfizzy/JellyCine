package com.jellycine.data.api

import com.jellycine.data.model.TmdbImage
import com.jellycine.data.model.TmdbImagesResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

internal class TmdbApi(
    private val client: HttpClient,
    private val apiKey: String = TMDB_API_KEY
) {
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

    private companion object {
        private const val TMDB_API_KEY = "4219e299c89411838049ab0dab19ebd5"

        private fun imageUrl(imagePath: String?, size: String): String? {
            val path = imagePath?.takeIf { it.isNotBlank() } ?: return null
            return "https://image.tmdb.org/t/p/$size/${path.removePrefix("/")}"
        }
    }
}