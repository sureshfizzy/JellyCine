package com.jellycine.data.repository

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.IntroWindow
import com.jellycine.data.model.IntroWindowSource
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

internal class IntroDbClient(
    private val getSeriesItem: suspend (String) -> BaseItemDto?
) {
    private data class CachedCommunityIntroWindow(
        val window: IntroWindow?,
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

    private val communityIntroCacheTtlMs = TimeUnit.HOURS.toMillis(6)
    private val communityIntroCache = ConcurrentHashMap<String, CachedCommunityIntroWindow>()
    private val communityIntroHttpClient: OkHttpClient by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        OkHttpClient.Builder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    suspend fun getIntroWindow(item: BaseItemDto): IntroWindow? {
        val lookupRequest = communityIntroLookupRequest(item) ?: return null
        getCachedCommunityIntroWindow(lookupRequest.cacheKey)?.let { cachedEntry ->
            return cachedEntry.window
        }

        val introWindow = withContext(Dispatchers.IO) {
            fetchIntroDbWindow(lookupRequest)
        }
        communityIntroCache[lookupRequest.cacheKey] = CachedCommunityIntroWindow(
            window = introWindow,
            cachedAtMs = System.currentTimeMillis()
        )
        return introWindow
    }

    private fun getCachedCommunityIntroWindow(cacheKey: String): CachedCommunityIntroWindow? {
        val cachedEntry = communityIntroCache[cacheKey] ?: return null
        if (System.currentTimeMillis() - cachedEntry.cachedAtMs > communityIntroCacheTtlMs) {
            communityIntroCache.remove(cacheKey)
            return null
        }
        return cachedEntry
    }

    private suspend fun communityIntroLookupRequest(item: BaseItemDto): IntroDbLookupRequest? {
        val seriesImdbId = item.seriesId
            ?.takeIf { it.isNotBlank() && it != item.id }
            ?.let { seriesId ->
                getSeriesItem(seriesId)
                    ?.providerIds
                    .providerId("imdb")
            }
        val episodeImdbId = item.providerIds.providerId("imdb")
        val finalImdbId = seriesImdbId ?: episodeImdbId ?: return null
        val seasonNumber = item.parentIndexNumber ?: return null
        val episodeNumber = item.indexNumber ?: return null

        return IntroDbLookupRequest(
            imdbId = finalImdbId,
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

    private fun fetchIntroDbWindow(requestParams: IntroDbLookupRequest): IntroWindow? {
        val url = "https://api.introdb.app/segments?imdb_id=${requestParams.imdbId}&season=${requestParams.seasonNumber}&episode=${requestParams.episodeNumber}&segment_type=intro"
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        communityIntroHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null

            val responseBody = response.body?.string()?.takeIf { it.isNotBlank() } ?: return null
            val payload = JellyCineJson.decodeFromString<IntroDbSegmentsResponse>(responseBody)
            val startMs = payload.intro?.startMs ?: return null
            val endMs = payload.intro?.endMs ?: return null
            if (endMs <= startMs) return null

            return IntroWindow(
                startMs = startMs,
                endMs = endMs,
                source = IntroWindowSource.INTRO_DB
            )
        }
    }
}