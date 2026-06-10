package com.jellycine.data.api

import com.jellycine.data.model.AwardMode
import com.jellycine.data.model.AwardTitleRef
import com.jellycine.data.network.JellyCineJson
import io.ktor.client.HttpClient
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable

internal class WikidataAwardsClient(
    private val client: HttpClient
) {
    suspend fun categoryTitlesBatch(
        categoryQids: List<String>,
        mode: AwardMode
    ): Map<String, List<AwardTitleRef>> {
        if (categoryQids.isEmpty()) return emptyMap()
        val query = buildQuery(categoryQids, mode)
        val raw = client.get(SPARQL_ENDPOINT) {
            parameter("format", "json")
            parameter("query", query)
            accept(ContentType.parse("application/sparql-results+json"))
            header(HttpHeaders.UserAgent, USER_AGENT)
        }.bodyAsText()

        val parsed = JellyCineJson.decodeFromString<SparqlResponse>(raw)
        val grouped = LinkedHashMap<String, MutableList<AwardTitleRef>>()
        for (binding in parsed.results.bindings) {
            val qid = binding["cat"]?.value?.substringAfterLast("/")?.takeIf { it.isNotBlank() }
                ?: continue
            val tmdbId = binding["tmdb"]?.value?.takeIf { it.isNotBlank() } ?: continue
            val mediaType = binding["type"]?.value?.takeIf { it == "movie" || it == "tv" } ?: continue
            val year = binding["year"]?.value?.toIntOrNull()
            grouped.getOrPut(qid) { mutableListOf() }
                .add(AwardTitleRef(tmdbId = tmdbId, mediaType = mediaType, year = year))
        }
        return grouped
    }

    private fun buildQuery(categoryQids: List<String>, mode: AwardMode): String {
        val prop = if (mode == AwardMode.NOMINEES) "P1411" else "P166"
        val values = categoryQids.joinToString(" ") { "wd:$it" }
        return """
            SELECT ?cat ?tmdb ?type (MAX(?yr) AS ?year) WHERE {
              VALUES ?cat { $values }
              { ?work wdt:$prop ?cat . }
              UNION { ?person p:$prop ?st . ?st ps:$prop ?cat ; pq:P1686 ?work . }
              { ?work wdt:P4947 ?tmdb . BIND("movie" AS ?type) }
              UNION { ?work wdt:P4983 ?tmdb . BIND("tv" AS ?type) }
              OPTIONAL { ?work wdt:P577 ?d . BIND(YEAR(?d) AS ?yr) }
            }
            GROUP BY ?cat ?tmdb ?type
            ORDER BY DESC(?year)
        """.trimIndent()
    }

    @Serializable
    private data class SparqlResponse(val results: SparqlResults = SparqlResults())

    @Serializable
    private data class SparqlResults(val bindings: List<Map<String, SparqlValue>> = emptyList())

    @Serializable
    private data class SparqlValue(val value: String = "")

    private companion object {
        private const val SPARQL_ENDPOINT = "https://query.wikidata.org/sparql"
        private const val USER_AGENT = "JellyCine/awards (https://github.com/jellycine)"
    }
}