package com.jellycine.data.repository

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.PlaybackSegmentSource
import com.jellycine.data.model.PlaybackSegmentWindow
import com.jellycine.data.model.PlaybackSegments
import com.jellycine.data.network.JellyCineJson
import com.jellycine.data.util.currentTimeMillis
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

internal class IntroDbClient(
    private val httpClient: HttpClient,
    private val getSeriesItem: suspend (String) -> BaseItemDto?
) {
    private data class CachedPlaybackSegments(
        val segments: PlaybackSegments?,
        val cachedAtMs: Long
    )

    private data class IntroDbLookupRequest(
        val imdbId: String,
        val seasonNumber: Int,
        val episodeNumber: Int
    ) {
        val cacheKey: String
            get() = "introdb|$imdbId|s$seasonNumber|e$episodeNumber|intro"
    }

    @Serializable
    private data class IntroDbSegmentsResponse(
        @SerialName("imdb_id")
        val imdbId: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val intro: IntroDbSegmentWindow? = null
    )

    @Serializable
    private data class IntroDbSegmentWindow(
        @SerialName("start_ms")
        val startMs: Long? = null,
        @SerialName("end_ms")
        val endMs: Long? = null
    )

    private val cacheTtlMs = 6L * 60L * 60L * 1000L
    private val playbackSegmentsCache = mutableMapOf<String, CachedPlaybackSegments>()
    private val cacheMutex = Mutex()
    suspend fun getPlaybackSegments(item: BaseItemDto): PlaybackSegments? {
        val lookupRequest = buildLookupRequest(item) ?: return null
        getCachedPlaybackSegments(lookupRequest.cacheKey)?.let { cachedEntry ->
            return cachedEntry.segments
        }

        val playbackSegments = fetchIntroDbSegments(lookupRequest)
        cacheMutex.withLock {
            playbackSegmentsCache[lookupRequest.cacheKey] = CachedPlaybackSegments(
                segments = playbackSegments,
                cachedAtMs = currentTimeMillis()
            )
        }
        return playbackSegments
    }

    private suspend fun getCachedPlaybackSegments(cacheKey: String): CachedPlaybackSegments? {
        return cacheMutex.withLock {
            val cachedEntry = playbackSegmentsCache[cacheKey] ?: return@withLock null
            if (currentTimeMillis() - cachedEntry.cachedAtMs > cacheTtlMs) {
                playbackSegmentsCache.remove(cacheKey)
                return@withLock null
            }
            cachedEntry
        }
    }

    private suspend fun buildLookupRequest(item: BaseItemDto): IntroDbLookupRequest? {
        val seriesImdbId = item.seriesId
            ?.takeIf { it.isNotBlank() && it != item.id }
            ?.let { seriesId ->
                getSeriesItem(seriesId)
                    ?.providerIds
                    .providerId("imdb")
            }
        val episodeImdbId = item.providerIds.providerId("imdb")
        val imdbId = seriesImdbId ?: episodeImdbId ?: return null
        val seasonNumber = item.parentIndexNumber ?: return null
        val episodeNumber = item.indexNumber ?: return null

        return IntroDbLookupRequest(
            imdbId = imdbId,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber
        )
    }

    private fun Map<String, String>?.providerId(providerName: String): String? {
        return this
            ?.entries
            ?.firstOrNull { (key, value) ->
                key.equals(providerName, ignoreCase = true) && value.isNotBlank()
            }
            ?.value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private suspend fun fetchIntroDbSegments(requestParams: IntroDbLookupRequest): PlaybackSegments? {
        val url = "https://api.introdb.app/segments?imdb_id=${requestParams.imdbId}&season=${requestParams.seasonNumber}&episode=${requestParams.episodeNumber}&segment_type=intro"
        val response = httpClient.get(url)
        if (response.status.value !in 200..299) return null

        run {
            val responseBody = response.bodyAsText().takeIf { it.isNotBlank() } ?: return null
            val payload = JellyCineJson.decodeFromString<IntroDbSegmentsResponse>(responseBody)
            val startMs = payload.intro?.startMs ?: return null
            val endMs = payload.intro?.endMs ?: return null
            if (endMs <= startMs) return null

            return PlaybackSegments(
                intro = PlaybackSegmentWindow(
                    startMs = startMs,
                    endMs = endMs,
                    source = PlaybackSegmentSource.INTRO_DB
                )
            )
        }
    }
}