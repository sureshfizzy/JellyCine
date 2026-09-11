package com.jellycine.player.core

import androidx.media3.common.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object RemoteTrailerUrl {
    private const val AUDIO_CACHE_TTL_MS = 30 * 60 * 1000L
    private const val MIN_THEME_DURATION_S = 20L
    private const val MAX_THEME_DURATION_S = 600L
    private val THEME_KEYWORDS = listOf(
        "theme music", "theme song", "title track", "title song",
        "background music", "soundtrack", "original score", "score suite",
        "bgm", "ost", "score", "theme"
    )
    private val CREDITS_KEYWORDS = listOf(
        "end credits", "closing credits", "end title", "end titles",
        "closing theme", "end theme", "credits song"
    )
    private val EXCLUDE_KEYWORDS = listOf(
        "trailer", "teaser", "promo", "making", "interview", "review",
        "first look", "motion poster", "glimpse", "reaction", "explained",
        "announcement", "sneak peek", "behind the scene", "deleted scene"
    )
    private val audioUrlCache = ConcurrentHashMap<String, CachedAudio>()

    private data class CachedAudio(val url: String, val timestampMs: Long)

    @Volatile
    private var extractorInitialized = false

    suspend fun resolve(url: String, maxVideoHeight: Int = Int.MAX_VALUE): RemoteTrailerStream = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            throw IllegalArgumentException("Remote trailer URL is empty")
        }

        initExtractor()
        val service = runCatching { NewPipe.getServiceByUrl(trimmedUrl) }.getOrNull()
            ?: throw IllegalStateException("Unsupported remote trailer URL")

        val streamInfo = runCatching {
            StreamInfo.getInfo(service, trimmedUrl)
        }.getOrElse { error ->
            throw IllegalStateException(
                "Unable to resolve remote trailer stream: ${error.message ?: "unsupported URL"}",
                error
            )
        }

        selectPlayableStream(streamInfo, maxVideoHeight)
            ?: throw IllegalStateException("Remote trailer does not expose a playable stream")
    }

    suspend fun getAudioUrl(url: String): String? = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) return@withContext null

        audioUrlCache[trimmedUrl]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestampMs < AUDIO_CACHE_TTL_MS) {
                return@withContext cached.url
            }
            audioUrlCache.remove(trimmedUrl)
        }

        initExtractor()
        val service = runCatching { NewPipe.getServiceByUrl(trimmedUrl) }.getOrNull()
            ?: return@withContext null
        val audioStreams = runCatching {
            service.getStreamExtractor(trimmedUrl).apply { fetchPage() }.audioStreams
        }.getOrNull() ?: return@withContext null

        val audioUrl = audioStreams
            .orEmpty()
            .filter { stream -> stream.isUrl() && !stream.getUrl().isNullOrBlank() }
            .maxWithOrNull(compareBy<AudioStream> { if (it.isAacAudio()) 1 else 0 }
                .thenBy { maxOf(it.getAverageBitrate(), it.getBitrate()).coerceAtLeast(0) })
            ?.getUrl()
            ?.takeIf(String::isNotBlank)
        if (audioUrl != null) {
            audioUrlCache[trimmedUrl] = CachedAudio(audioUrl, System.currentTimeMillis())
        }
        audioUrl
    }

    suspend fun searchAudioUrl(query: String, requiredTitle: String): String? = withContext(Dispatchers.IO) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank()) return@withContext null

        audioUrlCache[trimmedQuery]?.let { cached ->
            if (System.currentTimeMillis() - cached.timestampMs < AUDIO_CACHE_TTL_MS) {
                return@withContext cached.url
            }
            audioUrlCache.remove(trimmedQuery)
        }

        initExtractor()
        val requiredNorm = normalizeForMatch(requiredTitle)
        val candidates = runCatching {
            ServiceList.YouTube.getSearchExtractor(trimmedQuery).apply { fetchPage() }
                .initialPage.items
                .filterIsInstance<StreamInfoItem>()
                .mapNotNull { candidate ->
                    val name = candidate.name
                    val url = candidate.url
                    if (url.isNullOrBlank() || name.isNullOrBlank()) null
                    else candidate to normalizeForMatch(name)
                }
                .filter { (_, norm) -> requiredNorm.isBlank() || norm.containsPhrase(requiredNorm) }
                .filter { (_, norm) -> EXCLUDE_KEYWORDS.none { norm.containsPhrase(it) } }
                .filter { (candidate, _) -> candidate.duration in MIN_THEME_DURATION_S..MAX_THEME_DURATION_S }
        }.getOrElse { return@withContext null }

        val videoUrl = (candidates.firstOrNull { (_, norm) -> THEME_KEYWORDS.any { norm.containsPhrase(it) } }
            ?: candidates.firstOrNull { (_, norm) -> CREDITS_KEYWORDS.any { norm.containsPhrase(it) } })
            ?.first?.url
            ?.takeIf(String::isNotBlank)
            ?: return@withContext null

        val audioUrl = getAudioUrl(videoUrl) ?: return@withContext null
        audioUrlCache[trimmedQuery] = CachedAudio(audioUrl, System.currentTimeMillis())
        audioUrl
    }

    private fun normalizeForMatch(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun String.containsPhrase(phrase: String): Boolean =
        phrase.isNotBlank() && " $this ".contains(" $phrase ")

    @Synchronized
    private fun initExtractor() {
        if (extractorInitialized) return
        NewPipe.init(OkHttpExtractorDownloader())
        extractorInitialized = true
    }

    private fun selectPlayableStream(streamInfo: StreamInfo, maxVideoHeight: Int): RemoteTrailerStream? {
        if (maxVideoHeight == Int.MAX_VALUE) {
            streamInfo.getDashMpdUrl()
                ?.takeIf(String::isNotBlank)
                ?.let {
                    return RemoteTrailerStream(
                        url = it,
                        mimeType = MimeTypes.APPLICATION_MPD
                    )
                }

            streamInfo.getHlsUrl()
                ?.takeIf(String::isNotBlank)
                ?.let {
                    return RemoteTrailerStream(
                        url = it,
                        mimeType = MimeTypes.APPLICATION_M3U8
                    )
                }
        }

        val videoOnly = streamInfo.getVideoOnlyStreams()
            .orEmpty()
            .filter { stream ->
                stream.isUrl() && !stream.getUrl().isNullOrBlank() &&
                    stream.getHeight().coerceAtLeast(0) <= maxVideoHeight
            }
            .maxWithOrNull(compareBy<VideoStream> { it.getHeight().coerceAtLeast(0) }
                .thenBy { it.getFps().coerceAtLeast(0) }
                .thenBy { it.getBitrate().coerceAtLeast(0) })
        val audio = streamInfo.getAudioStreams()
            .orEmpty()
            .filter { stream -> stream.isUrl() && !stream.getUrl().isNullOrBlank() }
            .maxWithOrNull(compareBy<AudioStream> { if (it.isAacAudio()) 1 else 0 }
                .thenBy { maxOf(it.getAverageBitrate(), it.getBitrate()).coerceAtLeast(0) })

        if (videoOnly != null && audio != null) {
            val videoUrl = videoOnly.getUrl()?.takeIf(String::isNotBlank) ?: return null
            val audioUrl = audio.getUrl()?.takeIf(String::isNotBlank) ?: return null
            val stream = RemoteTrailerStream(
                url = videoUrl,
                mimeType = videoOnly.getFormat()?.getMimeType(),
                audioUrl = audioUrl,
                audioMimeType = audio.getFormat()?.getMimeType()
            )
            return stream
        }

        val progressiveVideo = streamInfo.getVideoStreams()
            .orEmpty()
            .filter { stream ->
                !stream.isVideoOnly() && stream.isUrl() && !stream.getUrl().isNullOrBlank() &&
                    stream.getHeight().coerceAtLeast(0) <= maxVideoHeight
            }
            .maxByOrNull { stream -> stream.getHeight().takeIf { it > 0 } ?: 0 }
        if (progressiveVideo != null) {
            val progressiveUrl = progressiveVideo.getUrl()?.takeIf(String::isNotBlank) ?: return null
            val stream = RemoteTrailerStream(
                url = progressiveUrl,
                mimeType = progressiveVideo.getFormat()?.getMimeType()
            )
            return stream
        }

        return null
    }

    data class RemoteTrailerStream(
        val url: String,
        val mimeType: String? = null,
        val audioUrl: String? = null,
        val audioMimeType: String? = null
    )

    private fun AudioStream.isAacAudio(): Boolean {
        val codec = getCodec().orEmpty()
        val formatName = getFormat()?.getName().orEmpty()
        val mimeType = getFormat()?.getMimeType().orEmpty()
        return codec.contains("mp4a", ignoreCase = true) ||
            formatName.contains("m4a", ignoreCase = true) ||
            mimeType.contains("mp4", ignoreCase = true)
    }

    private class OkHttpExtractorDownloader : Downloader() {
        private val client = OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(45, TimeUnit.SECONDS)
            .build()

        override fun execute(request: Request): Response {
            val mediaType = request.headers()
                .entries
                .firstOrNull { (name, _) -> name.equals("Content-Type", ignoreCase = true) }
                ?.value
                ?.firstOrNull()
                ?.toMediaTypeOrNull()
            val requestBody = request.dataToSend()?.let { data ->
                data.toRequestBody(mediaType)
            } ?: when (request.httpMethod().uppercase()) {
                "POST", "PUT", "PATCH" -> ByteArray(0).toRequestBody(mediaType)
                else -> null
            }

            val okhttpRequest = okhttp3.Request.Builder()
                .url(request.url())
                .method(request.httpMethod(), requestBody)
                .apply {
                    Request.getHeadersFromLocalization(request.localization()).forEach { (name, values) ->
                        values.forEach { value -> addHeader(name, value) }
                    }
                    request.headers().forEach { (name, values) ->
                        values.forEach { value -> addHeader(name, value) }
                    }
                }
                .build()

            client.newCall(okhttpRequest).execute().use { response ->
                return Response(
                    response.code,
                    response.message,
                    response.headers.toMultimap(),
                    response.body?.string().orEmpty(),
                    response.request.url.toString()
                )
            }
        }
    }
}