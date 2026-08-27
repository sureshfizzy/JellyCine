package com.vela.data.repository

import android.content.Context
import com.vela.data.api.TmdbApi
import com.vela.data.api.WikidataAwardsClient
import com.vela.data.model.AwardDefinition
import com.vela.data.model.AwardMode
import com.vela.data.model.AwardRefsState
import com.vela.data.model.AwardRow
import com.vela.data.model.AwardTitleRef
import com.vela.data.model.SeerrRecommendationTitle
import com.vela.data.network.VelaJson
import com.vela.data.preferences.NetworkPreferences
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class AwardsRepository(
    context: Context,
    private val mediaRepository: MediaRepository
) {
    private val appContext = context.applicationContext
    private val networkPreferences = NetworkPreferences(appContext)
    private val wikidata by lazy { WikidataAwardsClient(createHttpClient()) }
    private val tmdb by lazy { TmdbApi(createHttpClient()) }

    private val refsCache = ConcurrentHashMap<String, List<AwardTitleRef>>()
    private val titleCache = ConcurrentHashMap<String, SeerrRecommendationTitle>()

    suspend fun getCategoryRefs(
        categoryQids: List<String>,
        mode: AwardMode,
        limit: Int = DEFAULT_REF_LIMIT
    ): Map<String, List<AwardTitleRef>> {
        val result = LinkedHashMap<String, List<AwardTitleRef>>()
        val uncached = mutableListOf<String>()
        for (qid in categoryQids) {
            val cached = refsCache["$qid|$mode"]
            if (cached != null) result[qid] = cached else uncached.add(qid)
        }
        if (uncached.isNotEmpty()) {
            val fetched = withContext(Dispatchers.IO) {
                runCatching { wikidata.categoryTitlesBatch(uncached, mode) }.getOrDefault(emptyMap())
            }
            for (qid in uncached) {
                val refs = (fetched[qid] ?: emptyList()).take(limit)
                refsCache["$qid|$mode"] = refs
                result[qid] = refs
            }
        }
        return result
    }

    suspend fun loadAwardRows(award: AwardDefinition): AwardRefsState = coroutineScope {
        val headline = award.categories.first()
        val winnersDeferred = async {
            getCategoryRefs(award.categories.map { it.qid }, AwardMode.WINNERS)
        }
        val nomineesDeferred = async {
            getCategoryRefs(listOf(headline.qid), AwardMode.NOMINEES)
        }
        val winnersMap = winnersDeferred.await()
        val nominees = nomineesDeferred.await()[headline.qid].orEmpty()

        val rows = buildList {
            val headlineRefs = winnersMap[headline.qid].orEmpty()
            if (headlineRefs.isNotEmpty()) {
                add(AwardRow("top_winners", "Top Winners", headline.label, headline.qid, AwardMode.WINNERS, headlineRefs))
            }
            if (nominees.isNotEmpty()) {
                add(AwardRow("nominees", "The Nominees Are…", "${headline.label} (Nominee)", headline.qid, AwardMode.NOMINEES, nominees))
            }
            award.categories.drop(1).forEach { category ->
                val refs = winnersMap[category.qid].orEmpty()
                if (refs.isNotEmpty()) {
                    add(AwardRow("cat_${category.qid}", category.label, category.label, category.qid, AwardMode.WINNERS, refs))
                }
            }
        }

        val years = (winnersMap.values.flatten() + nominees).mapNotNull { it.year }
        AwardRefsState(
            rows = rows,
            minYear = years.minOrNull(),
            maxYear = years.maxOrNull(),
            decades = years.map { (it / 10) * 10 }.distinct().sortedDescending()
        )
    }

    suspend fun hydrate(
        refs: List<AwardTitleRef>,
        limit: Int = DEFAULT_HYDRATE_LIMIT
    ): List<SeerrRecommendationTitle> {
        if (refs.isEmpty()) return emptyList()
        val target = refs.take(limit)
        val needsLocalLookup = target.filter { ref ->
            val cached = titleCache["${ref.mediaType}:${ref.tmdbId}"]
            cached == null || cached.jellyfinMediaId.isNullOrBlank()
        }
        val localIds = if (needsLocalLookup.isEmpty()) {
            emptyMap()
        } else {
            mediaRepository.findLocalItemIdsByTmdb(needsLocalLookup.map { it.tmdbId })
        }
        return coroutineScope {
            val gate = Semaphore(MAX_CONCURRENCY)
            target
                .map { ref ->
                    async(Dispatchers.IO) { gate.withPermit { hydrateOne(ref, localIds[ref.tmdbId]) } }
                }
                .awaitAll()
                .filterNotNull()
        }
    }

    private suspend fun hydrateOne(ref: AwardTitleRef, localId: String?): SeerrRecommendationTitle? {
        val cacheKey = "${ref.mediaType}:${ref.tmdbId}"
        titleCache[cacheKey]?.let { cached ->
            val updated = cached.copy(
                productionYear = cached.productionYear ?: ref.year,
                jellyfinMediaId = localId ?: cached.jellyfinMediaId
            )
            if (updated !== cached) titleCache[cacheKey] = updated
            return updated
        }

        val resolved = tmdb.titleSummary(ref.mediaType, ref.tmdbId) ?: return null
        val hydrated = SeerrRecommendationTitle(
            tmdbId = ref.tmdbId,
            title = resolved.title,
            mediaType = ref.mediaType,
            productionYear = resolved.year ?: ref.year,
            posterUrl = resolved.posterUrl,
            jellyfinMediaId = localId
        )
        titleCache[cacheKey] = hydrated
        return hydrated
    }

    private fun createHttpClient(): HttpClient {
        val timeouts = networkPreferences.getTimeoutConfig()
        val okHttpClient = OkHttpClient.Builder()
            .callTimeout(timeouts.requestTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .connectTimeout(timeouts.connectionTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .readTimeout(timeouts.socketTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .writeTimeout(timeouts.socketTimeoutMs.toLong(), TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return HttpClient(OkHttp) {
            expectSuccess = false
            engine { preconfigured = okHttpClient }
            install(ContentNegotiation) { json(VelaJson) }
        }
    }

    private companion object {
        private const val DEFAULT_REF_LIMIT = 300
        private const val DEFAULT_HYDRATE_LIMIT = 12
        private const val MAX_CONCURRENCY = 10
    }
}