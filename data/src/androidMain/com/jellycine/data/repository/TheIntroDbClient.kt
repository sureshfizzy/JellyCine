package com.jellycine.data.repository

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.PlaybackSegmentSource
import com.jellycine.data.model.PlaybackSegmentWindow
import com.jellycine.data.model.PlaybackSegments
import com.jellycine.data.network.JellyCineJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

internal class TheIntroDbClient(
    private val getSeriesItem: suspend (String) -> BaseItemDto?
) {
    private data class CachedPlaybackSegments(
        val segments: PlaybackSegments?,
        val cachedAtMs: Long
    )

    private data class TheIntroDbLookupRequest(
        val tmdbId: String,
        val seasonNumber: Int,
        val episodeNumber: Int
    ) {
        val cacheKey: String
            get() = "theintrodb|$tmdbId|s$seasonNumber|e$episodeNumber|segments"
    }

    @Serializable
    private data class TheIntroDbMediaResponse(
        @SerialName("tmdb_id")
        val tmdbId: Long? = null,
        val type: String? = null,
        val intro: List<TheIntroDbSegment> = emptyList(),
        val recap: List<TheIntroDbSegment> = emptyList(),
        val credits: List<TheIntroDbSegment> = emptyList(),
        val preview: List<TheIntroDbSegment> = emptyList()
    )

    @Serializable
    private data class TheIntroDbSegment(
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
            fetchPlaybackSegments(lookupRequest)
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

    private suspend fun buildLookupRequest(item: BaseItemDto): TheIntroDbLookupRequest? {
        val seriesTmdbId = item.seriesId
            ?.takeIf { it.isNotBlank() && it != item.id }
            ?.let { seriesId ->
                getSeriesItem(seriesId)
                    ?.providerIds
                    .providerId("tmdb")
            }
        val episodeTmdbId = item.providerIds.providerId("tmdb")
        val tmdbId = seriesTmdbId ?: episodeTmdbId ?: return null
        val seasonNumber = item.parentIndexNumber ?: return null
        val episodeNumber = item.indexNumber ?: return null

        return TheIntroDbLookupRequest(
            tmdbId = tmdbId,
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

    private fun fetchPlaybackSegments(requestParams: TheIntroDbLookupRequest): PlaybackSegments? {
        val url = "https://api.theintrodb.org/v2/media?tmdb_id=${requestParams.tmdbId}&season=${requestParams.seasonNumber}&episode=${requestParams.episodeNumber}"

        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null

            val responseBody = response.body?.string()?.takeIf { it.isNotBlank() } ?: return null
            val payload = JellyCineJson.decodeFromString<TheIntroDbMediaResponse>(responseBody)
            val playbackSegments = PlaybackSegments(
                intro = payload.intro.firstOrNull()?.toPlaybackSegmentWindow(),
                recap = payload.recap.firstOrNull()?.toPlaybackSegmentWindow(),
                credits = payload.credits.firstOrNull()?.toPlaybackSegmentWindow(),
                preview = payload.preview.firstOrNull()?.toPlaybackSegmentWindow()
            ).takeIf { it.hasAnySegments() } ?: return null

            return playbackSegments
        }
    }

    private fun TheIntroDbSegment.toPlaybackSegmentWindow(): PlaybackSegmentWindow? {
        val segmentStartMs = startMs ?: 0L
        val segmentEndMs = endMs
        if (startMs == null && segmentEndMs == null) return null
        if (segmentEndMs != null && segmentEndMs <= segmentStartMs) return null

        return PlaybackSegmentWindow(
            startMs = segmentStartMs,
            endMs = segmentEndMs,
            source = PlaybackSegmentSource.THE_INTRO_DB
        )
    }
}