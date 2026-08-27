package com.vela.data.repository

import com.vela.data.model.BaseItemDto
import com.vela.data.model.PlaybackSegmentSource
import com.vela.data.model.PlaybackSegmentWindow
import com.vela.data.model.PlaybackSegments
import com.vela.data.network.VelaJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class IntroDbClient(
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

    private val cacheTtlMs = TimeUnit.HOURS.toMillis(6)
    private val playbackSegmentsCache = ConcurrentHashMap<String, CachedPlaybackSegments>()
    private val httpClient: OkHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    suspend fun getPlaybackSegments(item: BaseItemDto): PlaybackSegments? {
        val lookupRequest = buildLookupRequest(item) ?: return null
        getCachedPlaybackSegments(lookupRequest.cacheKey)?.let { cachedEntry ->
            return cachedEntry.segments
        }

        val playbackSegments = withContext(Dispatchers.IO) {
            fetchIntroDbSegments(lookupRequest)
        }
        playbackSegmentsCache[lookupRequest.cacheKey] = CachedPlaybackSegments(
            segments = playbackSegments,
            cachedAtMs = System.currentTimeMillis()
        )
        return playbackSegments
    }

    private fun getCachedPlaybackSegments(cacheKey: String): CachedPlaybackSegments? {
        val cachedEntry = playbackSegmentsCache[cacheKey] ?: return null
        if (System.currentTimeMillis() - cachedEntry.cachedAtMs > cacheTtlMs) {
            playbackSegmentsCache.remove(cacheKey)
            return null
        }
        return cachedEntry
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

    private fun fetchIntroDbSegments(requestParams: IntroDbLookupRequest): PlaybackSegments? {
        val url = "https://api.introdb.app/segments?imdb_id=${requestParams.imdbId}&season=${requestParams.seasonNumber}&episode=${requestParams.episodeNumber}&segment_type=intro"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null

            val responseBody = response.body?.string()?.takeIf { it.isNotBlank() } ?: return null
            val payload = VelaJson.decodeFromString<IntroDbSegmentsResponse>(responseBody)
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