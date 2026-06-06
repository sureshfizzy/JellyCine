package com.jellycine.player.core

import androidx.media3.common.MimeTypes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.concurrent.TimeUnit

object RemoteTrailerUrl {
    @Volatile
    private var extractorInitialized = false

    suspend fun resolve(url: String): RemoteTrailerStream = withContext(Dispatchers.IO) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isBlank()) {
            throw IllegalArgumentException("Remote trailer URL is empty")
        }

        ensureExtractorInitialized()
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

        selectPlayableStream(streamInfo)
            ?: throw IllegalStateException("Remote trailer does not expose a playable stream")
    }

    @Synchronized
    private fun ensureExtractorInitialized() {
        if (extractorInitialized) return
        NewPipe.init(OkHttpExtractorDownloader())
        extractorInitialized = true
    }

    private fun selectPlayableStream(streamInfo: StreamInfo): RemoteTrailerStream? {
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

        val videoOnly = streamInfo.getVideoOnlyStreams()
            .orEmpty()
            .filter { stream -> stream.isUrl() && !stream.getUrl().isNullOrBlank() }
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
            .filter { stream -> !stream.isVideoOnly() && stream.isUrl() && !stream.getUrl().isNullOrBlank() }
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