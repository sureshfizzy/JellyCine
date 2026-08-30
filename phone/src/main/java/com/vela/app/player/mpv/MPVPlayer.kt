package com.vela.app.player.mpv

import com.vela.data.model.MediaStream
import com.vela.data.model.PlaybackRequest
import com.vela.data.model.MediaSource
import com.vela.data.model.isStrmSource
import com.vela.data.model.requiresExternalSubtitleLoad
import com.vela.player.core.AudioTrackInfo
import com.vela.player.core.PlayerTrackState
import com.vela.player.core.SubtitleTrackInfo
import com.vela.player.preferences.PlayerPreferences
import java.io.File
import java.net.HttpURLConnection
import java.net.URI

object MPVPlayer {
    private val TEXT_SUBTITLE_CODECS = setOf(
        "ass",
        "ssa",
        "srt",
        "subrip",
        "vtt",
        "webvtt",
        "sub",
        "microdvd",
        "sami",
        "text"
    )
    fun hardwareDecodingFor(
        mediaSource: MediaSource?,
        userPreference: String
    ): String {
        // STRM 只是地址容器，不能据此禁用硬解；4K/HDR 远程流用软解反而可能无法起播。
        return userPreference
    }

    fun isRemoteHttpPlayback(mediaSource: MediaSource?): Boolean {
        return mediaSource?.isStrmSource() == true ||
            mediaSource?.isRemote == true ||
            mediaSource?.path?.startsWith("http", ignoreCase = true) == true
    }

    fun trackState(
        mediaStreams: List<MediaStream>?,
        selectedAudioStreamIndex: Int?,
        selectedSubtitleStreamIndex: Int?,
        defaultAudioStreamIndex: Int?,
        defaultSubtitleStreamIndex: Int?
    ): PlayerTrackState {
        val audioStreams = streams(mediaStreams, "Audio")
        val subtitleStreams = streams(mediaStreams, "Subtitle")
        val audioTracks = audioStreams.mapNotNull { stream ->
            val streamIndex = stream.index ?: return@mapNotNull null
            AudioTrackInfo(
                id = "audio:$streamIndex",
                label = stream.displayTitle ?: stream.title ?: stream.language ?: "Audio $streamIndex",
                language = stream.language,
                channelCount = stream.channels ?: 0,
                codec = stream.codec,
                playerTrackId = trackId(audioStreams, streamIndex) ?: return@mapNotNull null,
                streamIndex = streamIndex,
                requiresPlaybackRestart = false
            )
        }
        val subtitleTracks = listOf(
            SubtitleTrackInfo(
                id = "off",
                label = "Off",
                language = null,
                streamIndex = -1,
                requiresPlaybackRestart = false
            )
        ) + subtitleStreams.mapNotNull { stream ->
            val streamIndex = stream.index ?: return@mapNotNull null
            SubtitleTrackInfo(
                id = "subtitle:$streamIndex",
                label = stream.displayTitle ?: stream.title ?: stream.language ?: "Subtitle $streamIndex",
                language = stream.language,
                isForced = stream.isForced == true,
                isDefault = stream.isDefault == true,
                playerTrackId = trackId(subtitleStreams, streamIndex) ?: return@mapNotNull null,
                streamIndex = streamIndex,
                requiresPlaybackRestart = false
            )
        }

        return PlayerTrackState(
            availableAudioTracks = audioTracks,
            currentAudioTrack = audioTracks.firstOrNull {
                it.streamIndex == (selectedAudioStreamIndex ?: defaultAudioStreamIndex)
            } ?: audioTracks.firstOrNull(),
            availableSubtitleTracks = subtitleTracks,
            currentSubtitleTrack = subtitleTracks.firstOrNull {
                it.streamIndex == (selectedSubtitleStreamIndex ?: defaultSubtitleStreamIndex ?: -1)
            } ?: subtitleTracks.firstOrNull(),
            availableVideoTracks = emptyList()
        )
    }

    fun audioTrackId(mediaStreams: List<MediaStream>?, streamIndex: Int?): String? {
        if (streamIndex == null) return null
        return trackId(streams(mediaStreams, "Audio"), streamIndex)
    }

    fun subtitleTrackId(mediaStreams: List<MediaStream>?, streamIndex: Int?): String? {
        if (streamIndex == null || streamIndex < 0) return "no"
        return trackId(streams(mediaStreams, "Subtitle"), streamIndex)
    }

    fun selectAudioTrack(
        controller: MpvPlayerController?,
        track: AudioTrackInfo
    ): Int? {
        val streamIndex = track.streamIndex ?: return null
        val player = controller ?: return null
        player.selectAudioTrack(track.playerTrackId ?: return null)
        return streamIndex
    }

    fun selectSubtitleTrack(
        controller: MpvPlayerController?,
        track: SubtitleTrackInfo,
        externalSubtitleUrls: Map<Int, String>
    ): Int? {
        val streamIndex = track.streamIndex ?: return null
        val trackId = if (streamIndex < 0) "no" else track.playerTrackId ?: return null
        val player = controller ?: return null
        player.selectSubtitleTrack(
            trackId = trackId,
            externalUrl = externalSubtitleUrls[streamIndex]
        )
        return streamIndex
    }

    fun externalSubtitleUrls(
        playbackRequest: PlaybackRequest?,
        mediaStreams: List<MediaStream>,
        itemId: String? = null,
        mediaSourceId: String? = null
    ): Map<Int, String> {
        val request = playbackRequest ?: return emptyMap()
        val streamingUrl = request.url
        if (streamingUrl.isBlank()) return emptyMap()
        
        val baseUri = try { java.net.URI.create(streamingUrl) } catch (e: Exception) { return emptyMap() }
        val baseUrl = "${baseUri.scheme}://${baseUri.authority}/"
        val requestHeaders = request.requestHeaders

        return mediaStreams
            .asSequence()
            .filter(::shouldFetchExternalSubtitle)
            .mapNotNull { stream ->
                val streamIndex = stream.index ?: return@mapNotNull null
                var deliveryUrl = stream.deliveryUrl?.takeIf { it.isNotBlank() }
                
                if (deliveryUrl == null && itemId != null) {
                    val isEmby = requestHeaders["Authorization"]?.startsWith("Emby", ignoreCase = true) == true ||
                        requestHeaders["X-Emby-Authorization"]?.startsWith("Emby", ignoreCase = true) == true
            
                    val codec = when (stream.codec?.lowercase()) {
                        "ass", "ssa" -> stream.codec.orEmpty().lowercase()
                        "subrip", "srt" -> if (isEmby) "vtt" else "srt"
                        "webvtt", "vtt" -> "vtt"
                        else -> if (isEmby) "vtt" else "srt"
                    }

                    deliveryUrl = if (isEmby) {
                        val sourceId = mediaSourceId?.let { if (it.startsWith("mediasource")) it else "mediasource_$it" } ?: "mediasource_$itemId"
                        "emby/Videos/$itemId/$sourceId/Subtitles/$streamIndex/0/Stream.$codec"
                    } else {
                        val guidItemId = itemId.toGuid()
                        val sourceId = mediaSourceId?.replace("-", "") ?: itemId.replace("-", "")
                        "Videos/$guidItemId/$sourceId/Subtitles/$streamIndex/0/Stream.$codec"
                    }
                }
                
                if (deliveryUrl == null) return@mapNotNull null
                
                val fullUrl = if (deliveryUrl.startsWith("http", ignoreCase = true)) {
                    deliveryUrl
                } else {
                    baseUrl + deliveryUrl.trimStart('/')
                }
                
                streamIndex to request.authorizeRelatedUrl(fullUrl)
            }
            .toMap()
    }

    fun materializeSubtitleFiles(
        cacheDir: File,
        urls: Map<Int, String>,
        download: (String, File) -> Boolean = ::downloadHttpSubtitle
    ): Map<Int, String> {
        if (urls.isEmpty()) return emptyMap()
        val subtitleDir = cacheDir.resolve("mpv-subs").apply { mkdirs() }
        return urls.mapValues { (index, url) ->
            if (!url.startsWith("http", ignoreCase = true)) {
                return@mapValues url
            }
            val target = subtitleDir.resolve("$index.${subtitleFileExtension(url)}")
            if (download(url, target) && target.length() > 0L) {
                target.absolutePath
            } else {
                url
            }
        }
    }

    fun shouldFetchExternalSubtitle(stream: MediaStream): Boolean {
        if (!stream.type.equals("Subtitle", ignoreCase = true) || stream.index == null) {
            return false
        }
        return stream.requiresExternalSubtitleLoad() && isTextSubtitle(stream)
    }

    fun httpHeaderFields(requestHeaders: Map<String, String>): String? {
        val fields = requestHeaders.mapNotNull { (rawName, rawValue) ->
            val name = rawName.trim()
            val value = rawValue.trim()
            // 防止服务端返回的 header 注入额外请求行；非法项直接拒绝，不静默改写语义。
            if (!HTTP_HEADER_NAME.matches(name) || value.isEmpty() || value.contains('\r') || value.contains('\n')) {
                null
            } else {
                "$name: $value"
            }
        }
        return fields.takeIf { it.isNotEmpty() }?.joinToString(",")
    }

    fun resolvedSubtitleStreamIndex(
        preferredIndex: Int?,
        mediaSourceDefaultIndex: Int?,
        mediaStreams: List<MediaStream>?
    ): Int? {
        if (preferredIndex != null) {
            return preferredIndex
        }
        mediaSourceDefaultIndex?.takeIf { it >= 0 }?.let { return it }
        return mediaStreams
            .orEmpty()
            .filter { it.type.equals("Subtitle", ignoreCase = true) }
            .sortedBy { it.index ?: Int.MAX_VALUE }
            .firstOrNull { it.isDefault == true }
            ?.index
    }

    fun isTextSubtitle(stream: MediaStream): Boolean {
        if (stream.isTextSubtitleStream == true) return true
        val codec = stream.codec?.lowercase().orEmpty()
        return codec in TEXT_SUBTITLE_CODECS
    }

    fun redactPlaybackSecret(value: String?): String {
        if (value.isNullOrBlank()) return "none"
        return value.replace(
            Regex("([?&](?:api_key|ApiKey|access_token)=)[^&\\s]+", RegexOption.IGNORE_CASE),
            "$1***"
        )
    }

    private fun subtitleFileExtension(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".ssa") -> "ssa"
            path.endsWith(".srt") -> "srt"
            path.endsWith(".vtt") || path.endsWith(".webvtt") -> "vtt"
            else -> "ass"
        }
    }

    private val HTTP_HEADER_NAME = Regex("^[A-Za-z0-9!#$%&'*+.^_`|~-]+$")

    private fun downloadHttpSubtitle(url: String, target: File): Boolean {
        return runCatching {
            val connection = URI.create(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            try {
                if (connection.responseCode !in 200..299) return false
                connection.inputStream.use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                val header = target.inputStream().use { input ->
                    val buffer = ByteArray(64)
                    val read = input.read(buffer)
                    if (read <= 0) "" else String(buffer, 0, read, Charsets.UTF_8)
                }
                if (header.contains("<html", ignoreCase = true) ||
                    header.contains("<!doctype", ignoreCase = true)
                ) {
                    target.delete()
                    return false
                }
                true
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }

    private fun String.toGuid(): String {
        if (this.contains("-") || this.length != 32) return this
        return try {
            StringBuilder(this)
                .insert(8, "-")
                .insert(13, "-")
                .insert(18, "-")
                .insert(23, "-")
                .toString()
        } catch (e: Exception) {
            this
        }
    }

    fun isHdr(mediaStreams: List<MediaStream>?): Boolean {
        return mediaStreams.orEmpty().any { stream ->
            stream.type.equals("Video", ignoreCase = true) &&
                (
                    stream.colorTransfer?.contains("2084", ignoreCase = true) == true ||
                        stream.colorSpace?.contains("bt2020", ignoreCase = true) == true ||
                        stream.codec?.contains("dv", ignoreCase = true) == true
                    )
        }
    }

    private fun streams(mediaStreams: List<MediaStream>?, type: String): List<MediaStream> {
        return mediaStreams.orEmpty()
            .filter { it.type.equals(type, ignoreCase = true) && it.index != null }
            .distinctBy { it.index }
            .sortedBy { it.index ?: Int.MAX_VALUE }
    }

    private fun trackId(streams: List<MediaStream>, streamIndex: Int): String? {
        val trackIndex = streams.indexOfFirst { it.index == streamIndex }
        return if (trackIndex >= 0) (trackIndex + 1).toString() else null
    }
}
