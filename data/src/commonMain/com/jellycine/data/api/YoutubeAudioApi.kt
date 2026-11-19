package com.jellycine.data.api

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/*
 * Finds streamable theme-song audio on YouTube for every platform, through YouTube's internal
 * (unofficial) InnerTube API: a WEB search to find the video, then a player request that returns
 * direct AAC audio URLs.
 */
internal class YoutubeAudioApi(private val client: HttpClient) {
    private val cacheMutex = Mutex()
    private val queryMatches = HashMap<String, String>()
    private val urlCache = HashMap<String, Pair<String, TimeMark>>()
    private var visitorData: String? = null

    suspend fun audioUrl(url: String): String? {
        val videoId = videoId(url) ?: return null
        cacheMutex.withLock { urlCache[videoId] }?.let { (cachedUrl, mark) ->
            if (mark.elapsedNow() < URL_CACHE_TTL) return cachedUrl
        }
        val visitorData = visitorData() ?: return null
        val response = post(PLAYER_URL, playerBody(videoId, visitorData), PLAYER_USER_AGENT) ?: return null
        val formats = response.obj("streamingData")?.get("adaptiveFormats") as? JsonArray ?: return null
        val streamUrl = formats.mapNotNull { it as? JsonObject }
            .filter { format ->
                format.str("mimeType")?.startsWith("audio/mp4") == true && !format.str("url").isNullOrBlank()
            }
            .maxByOrNull { it.int("bitrate") ?: 0 }
            ?.str("url")
            ?: return null
        cacheMutex.withLock { urlCache[videoId] = streamUrl to TimeSource.Monotonic.markNow() }
        return streamUrl
    }

    suspend fun searchAudioUrl(query: String, requiredTitle: String): String? {
        val trimmedQuery = query.trim().ifBlank { return null }
        cacheMutex.withLock { queryMatches[trimmedQuery] }?.let { videoId -> audioUrl(videoId)?.let { return it } }
        val response = post(SEARCH_URL, searchBody(trimmedQuery)) ?: return null
        val requiredNorm = normalize(requiredTitle)
        val candidates = mutableListOf<Pair<SearchResult, String>>()
        collectVideos(response, candidates, requiredNorm)
        val match = candidates.firstOrNull { (_, norm) -> THEME_KEYWORDS.any { norm.containsPhrase(it) } }
            ?: candidates.firstOrNull { (_, norm) -> CREDITS_KEYWORDS.any { norm.containsPhrase(it) } }
            ?: candidates.firstOrNull { (_, norm) -> OFFICIAL_AUDIO_KEYWORDS.any { norm.containsPhrase(it) } }
            ?: return null
        val audio = audioUrl(match.first.videoId) ?: return null
        cacheMutex.withLock { queryMatches[trimmedQuery] = match.first.videoId }
        return audio
    }

    private data class SearchResult(val videoId: String, val title: String, val durationSeconds: Int)

    private fun collectVideos(
        element: JsonElement,
        out: MutableList<Pair<SearchResult, String>>,
        requiredNorm: String
    ) {
        when (element) {
            is JsonObject -> {
                element["videoRenderer"]?.let { renderer ->
                    val video = (renderer as? JsonObject)?.toSearchResult()
                    if (video != null) {
                        val norm = normalize(video.title)
                        val matches = (requiredNorm.isBlank() || norm.containsPhrase(requiredNorm)) &&
                            EXCLUDE_KEYWORDS.none { norm.containsPhrase(it) } &&
                            video.durationSeconds in MIN_THEME_DURATION_S..MAX_THEME_DURATION_S
                        if (matches) out += video to norm
                    }
                }
                element.values.forEach { collectVideos(it, out, requiredNorm) }
            }
            is JsonArray -> element.forEach { collectVideos(it, out, requiredNorm) }
            else -> Unit
        }
    }

    private fun JsonObject.toSearchResult(): SearchResult? {
        val id = str("videoId") ?: return null
        val title = obj("title")?.get("runs")?.jsonArray
            ?.joinToString("") { (it as? JsonObject)?.str("text").orEmpty() }
            ?.takeIf(String::isNotBlank) ?: return null
        val duration = obj("lengthText")?.str("simpleText")?.let(::parseDuration) ?: return null
        return SearchResult(id, title, duration)
    }

    private suspend fun post(url: String, body: String, userAgent: String? = null): JsonObject? = runCatching {
        val text = client.post(url) {
            contentType(ContentType.Application.Json)
            header("X-Goog-Api-Format-Version", "2")
            userAgent?.let { header("User-Agent", it) }
            setBody(body)
        }.bodyAsText()
        Json.parseToJsonElement(text).jsonObject.takeUnless { "error" in it }
    }.getOrNull()

    private suspend fun visitorData(): String? {
        visitorData?.let { return it }
        val response = post(VISITOR_ID_URL, VISITOR_ID_BODY, VISITOR_USER_AGENT) ?: return null
        val value = response.obj("responseContext")?.str("visitorData")?.takeIf(String::isNotBlank) ?: return null
        visitorData = value
        return value
    }

    private fun playerBody(videoId: String, visitorData: String) =
        """{"context":{"client":{"clientName":"VISIONOS","clientVersion":"$PLAYER_CLIENT_VERSION","clientScreen":"WATCH","platform":"MOBILE","visitorData":${JsonPrimitive(visitorData)},"deviceMake":"Apple","deviceModel":"RealityDevice14,1","osName":"visionOS","osVersion":"25.6.0.23O471","hl":"en-GB","gl":"GB","utcOffsetMinutes":0},"request":{"internalExperimentFlags":[],"useSsl":true},"user":{"lockedSafetyMode":false}},"videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true}"""

    private fun searchBody(query: String) =
        """{"query":${JsonPrimitive(query)},"context":{"client":{"clientName":"WEB","clientVersion":"2.20250101.00.00","hl":"en","gl":"US"}}}"""

    private fun videoId(url: String): String? {
        val trimmed = url.trim()
        if (ID_REGEX.matches(trimmed)) return trimmed
        return VIDEO_URL_REGEX.find(trimmed)?.groupValues?.get(1)
    }

    private fun parseDuration(text: String): Int? {
        val parts = text.split(':').map { it.toIntOrNull() ?: return null }
        return parts.fold(0) { total, part -> total * 60 + part }
    }

    private fun normalize(value: String) = value.lowercase().replace(NON_ALNUM, " ").trim()

    private fun String.containsPhrase(phrase: String) = phrase.isNotBlank() && " $this ".contains(" $phrase ")

    private fun JsonObject.obj(key: String) = this[key] as? JsonObject
    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull

    private companion object {
        const val PLAYER_URL = "https://youtubei.googleapis.com/youtubei/v1/player?prettyPrint=false"
        const val VISITOR_ID_URL = "https://youtubei.googleapis.com/youtubei/v1/visitor_id?prettyPrint=false"
        const val SEARCH_URL = "https://www.youtube.com/youtubei/v1/search?prettyPrint=false"
        const val PLAYER_CLIENT_VERSION = "1.02"
        const val PLAYER_USER_AGENT =
            "com.google.visionos.youtube/1.02(RealityDevice14,1; U; CPU visionOS 25_6_0 like Mac OS X; GB)"
        const val VISITOR_USER_AGENT = "com.google.android.youtube/21.03.36 (Linux; U; Android 15; GB) gzip"
        const val VISITOR_ID_BODY =
            """{"context":{"client":{"clientName":"ANDROID","clientVersion":"21.03.36","clientScreen":"WATCH","platform":"MOBILE","osName":"Android","osVersion":"16","androidSdkVersion":36,"hl":"en-GB","gl":"GB","utcOffsetMinutes":0},"request":{"internalExperimentFlags":[],"useSsl":true},"user":{"lockedSafetyMode":false}}}"""
        val URL_CACHE_TTL = 30.minutes

        const val MIN_THEME_DURATION_S = 20
        const val MAX_THEME_DURATION_S = 600
        val THEME_KEYWORDS = listOf(
            "theme music", "theme song", "title track", "title song",
            "background music", "soundtrack", "original score", "score suite",
            "bgm", "ost", "score", "theme"
        )
        val CREDITS_KEYWORDS = listOf(
            "end credits", "closing credits", "end title", "end titles",
            "closing theme", "end theme", "credits song"
        )
        val OFFICIAL_AUDIO_KEYWORDS = listOf("audio only", "official audio", "main title", "main theme")
        val EXCLUDE_KEYWORDS = listOf(
            "trailer", "teaser", "promo", "making", "interview", "review",
            "first look", "motion poster", "glimpse", "reaction", "explained",
            "announcement", "sneak peek", "behind the scene", "deleted scene"
        )

        val NON_ALNUM = Regex("[^a-z0-9]+")
        val ID_REGEX = Regex("[A-Za-z0-9_-]{11}")
        val VIDEO_URL_REGEX = Regex("""(?:v=|youtu\.be/|/shorts/|/embed/)([A-Za-z0-9_-]{11})""")
    }
}