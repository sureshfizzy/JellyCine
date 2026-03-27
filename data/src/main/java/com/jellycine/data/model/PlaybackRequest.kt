package com.jellycine.data.model

import android.net.Uri
import com.jellycine.data.network.NetworkModule
import com.jellycine.data.util.buildServerUrl
import com.jellycine.data.util.getServerUrl
import com.jellycine.data.util.removeQueryParameter

data class PlaybackRequest(
    val url: String,
    val requestHeaders: Map<String, String> = emptyMap()
)

internal data class PlaybackAuthContext(
    val serverUrl: String,
    val serverType: NetworkModule.ServerType?,
    val accessToken: String?,
    val deviceId: String,
    val clientVersion: String
)

internal data class PlaybackStreamOptions(
    val maxStreamingBitrate: Int? = null,
    val maxStreamingHeight: Int? = null,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val audioTranscodeMode: AudioTranscodeMode = AudioTranscodeMode.AUTO,
    val includeAccessTokenInQuery: Boolean = false
)

internal object PlaybackUrlBuilder {
    private const val API_KEY_QUERY_PARAM = "api_key"

    fun normalizePlaybackInfoUrls(
        serverUrl: String,
        playbackInfo: PlaybackInfoResponse
    ): PlaybackInfoResponse {
        return playbackInfo.copy(
            mediaSources = playbackInfo.mediaSources?.map { mediaSource ->
                mediaSource.copy(
                    transcodingUrl = getServerUrl(
                        baseUrl = serverUrl,
                        url = mediaSource.transcodingUrl
                    ) ?: mediaSource.transcodingUrl,
                    mediaStreams = mediaSource.mediaStreams?.map { mediaStream ->
                        mediaStream.copy(
                            deliveryUrl = resolvePlaybackUrl(
                                serverUrl = serverUrl,
                                url = mediaStream.deliveryUrl,
                                removeApiKey = true
                            ),
                            path = getServerUrl(
                                baseUrl = serverUrl,
                                url = mediaStream.path
                            ) ?: mediaStream.path
                        )
                    },
                    mediaAttachments = mediaSource.mediaAttachments?.map { mediaAttachment ->
                        mediaAttachment.copy(
                            deliveryUrl = resolvePlaybackUrl(
                                serverUrl = serverUrl,
                                url = mediaAttachment.deliveryUrl,
                                removeApiKey = true
                            )
                        )
                    }
                )
            }
        )
    }

    fun createLocalPlaybackRequest(
        authContext: PlaybackAuthContext,
        itemId: String,
        playbackInfo: PlaybackInfoResponse,
        options: PlaybackStreamOptions
    ): Result<PlaybackRequest> {
        return buildStreamingUrl(
            authContext = authContext,
            itemId = itemId,
            playbackInfo = playbackInfo,
            options = options.copy(includeAccessTokenInQuery = false)
        ).mapCatching { streamingUrl ->
            PlaybackRequest(
                url = streamingUrl,
                requestHeaders = buildPlaybackRequestHeaders(authContext)
            )
        }
    }

    fun createCastStreamingUrl(
        authContext: PlaybackAuthContext,
        itemId: String,
        playbackInfo: PlaybackInfoResponse,
        options: PlaybackStreamOptions
    ): Result<String> {
        return buildStreamingUrl(
            authContext = authContext,
            itemId = itemId,
            playbackInfo = playbackInfo,
            options = options.copy(includeAccessTokenInQuery = true)
        )
    }

    private fun buildPlaybackRequestHeaders(authContext: PlaybackAuthContext): Map<String, String> {
        if (authContext.accessToken.isNullOrBlank()) {
            return emptyMap()
        }

        val authHeader = AuthHeaderDto.fromServerType(
            serverType = authContext.serverType,
            deviceId = authContext.deviceId,
            version = authContext.clientVersion,
            accessToken = authContext.accessToken
        ).asHeaderValue()

        return mapOf(
            "Authorization" to authHeader,
            "X-Emby-Authorization" to authHeader
        )
    }

    private fun buildStreamingUrl(
        authContext: PlaybackAuthContext,
        itemId: String,
        playbackInfo: PlaybackInfoResponse,
        options: PlaybackStreamOptions
    ): Result<String> {
        return try {
            val mediaSource = playbackInfo.mediaSources?.firstOrNull()
                ?: return Result.failure(Exception("No media source available"))
            val normalizedSubtitleStreamIndex = normalizeSubtitleStreamIndex(options.subtitleStreamIndex)
            val selectedAudioStream = getSelectedAudioStream(
                mediaSource = mediaSource,
                requestedAudioStreamIndex = options.audioStreamIndex
            )
            val hasQualityCap = (options.maxStreamingBitrate ?: 0) > 0 || (options.maxStreamingHeight ?: 0) > 0
            val needsAudioTranscoding = needsAudioTranscode(
                audioTranscodeMode = options.audioTranscodeMode,
                selectedAudioStream = selectedAudioStream
            )

            val serverTranscodingUrl = !mediaSource.transcodingUrl.isNullOrBlank() &&
                (
                    hasQualityCap ||
                        needsAudioTranscoding ||
                        (mediaSource.supportsDirectPlay != true &&
                            mediaSource.supportsDirectStream != true)
                )
            if (serverTranscodingUrl) {
                val resolvedTranscodingUrl = getServerUrl(
                    baseUrl = authContext.serverUrl,
                    url = mediaSource.transcodingUrl
                )
                if (!resolvedTranscodingUrl.isNullOrBlank()) {
                    val selectedTranscodingUrl = applyTranscodingSelectionOverrides(
                        streamingUrl = resolvedTranscodingUrl,
                        audioStreamIndex = options.audioStreamIndex,
                        audioTranscodeMode = options.audioTranscodeMode,
                        sourceVideoBitrate = mediaSource.bitrate,
                        preserveOriginalVideo = !hasQualityCap && needsAudioTranscoding
                    )
                    return Result.success(selectedTranscodingUrl)
                }
            }

            val streamQueryParams = mutableListOf<Pair<String, String?>>()
            streamQueryParams.add("mediaSourceId" to mediaSource.id)
            options.audioStreamIndex?.let { streamQueryParams.add("audioStreamIndex" to it.toString()) }
            normalizeSubtitleStreamIndex(options.subtitleStreamIndex)?.let {
                streamQueryParams.add("subtitleStreamIndex" to it.toString())
            }
            streamQueryParams.add("PlaySessionId" to playbackInfo.playSessionId)
            streamQueryParams.add("DeviceId" to authContext.deviceId)
            if (options.includeAccessTokenInQuery) {
                streamQueryParams.add(API_KEY_QUERY_PARAM to authContext.accessToken)
            }

            if (hasQualityCap) {
                return Result.failure(
                    Exception("Negotiated transcoding URL not available for item $itemId")
                )
            }

            val streamingUrl = buildStreamUrl(
                serverUrl = authContext.serverUrl,
                itemId = itemId,
                queryParams = streamQueryParams,
                useStaticStream = mediaSource.supportsDirectPlay == true || mediaSource.supportsDirectStream == true
            )

            Result.success(streamingUrl)
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    private fun normalizeSubtitleStreamIndex(subtitleStreamIndex: Int?): Int? {
        return subtitleStreamIndex?.takeIf { it >= 0 }
    }

    private fun resolvePlaybackUrl(
        serverUrl: String,
        url: String?,
        removeApiKey: Boolean = false
    ): String? {
        val resolvedUrl = getServerUrl(
            baseUrl = serverUrl,
            url = url
        ) ?: url
        return resolvedUrl?.let { candidateUrl ->
            if (removeApiKey) {
                removeQueryParameter(candidateUrl, API_KEY_QUERY_PARAM)
            } else {
                candidateUrl
            }
        }
    }

    private fun buildStreamUrl(
        serverUrl: String,
        itemId: String,
        queryParams: List<Pair<String, String?>>,
        useStaticStream: Boolean
    ): String {
        val finalQueryParams = if (useStaticStream) {
            buildList {
                add("static" to "true")
                addAll(queryParams)
            }
        } else {
            queryParams
        }
        return buildServerUrl(
            baseUrl = serverUrl,
            encodedPath = "Videos/$itemId/stream",
            queryParams = finalQueryParams
        )
    }

    private fun getSelectedAudioStream(
        mediaSource: MediaSource,
        requestedAudioStreamIndex: Int?
    ): MediaStream? {
        val audioStreams = mediaSource.mediaStreams
            ?.filter { stream -> stream.type.equals("Audio", ignoreCase = true) }
            .orEmpty()
        if (audioStreams.isEmpty()) {
            return null
        }

        val targetIndex = requestedAudioStreamIndex
            ?: mediaSource.defaultAudioStreamIndex
            ?: audioStreams.firstOrNull { it.isDefault == true }?.index

        return audioStreams.firstOrNull { stream -> stream.index == targetIndex }
            ?: audioStreams.first()
    }

    private fun needsAudioTranscode(
        audioTranscodeMode: AudioTranscodeMode,
        selectedAudioStream: MediaStream?
    ): Boolean {
        val codec = selectedAudioStream?.codec?.lowercase()
        return when (audioTranscodeMode) {
            AudioTranscodeMode.STEREO -> {
                val channels = selectedAudioStream?.channels ?: 2
                channels > 2 || codec !in setOf("aac", "mp3", "opus", "vorbis", "pcm")
            }
            AudioTranscodeMode.SURROUND_5_1 -> codec != "eac3"
            else -> false
        }
    }

    private fun applyTranscodingSelectionOverrides(
        streamingUrl: String,
        audioStreamIndex: Int?,
        audioTranscodeMode: AudioTranscodeMode = AudioTranscodeMode.AUTO,
        sourceVideoBitrate: Int? = null,
        preserveOriginalVideo: Boolean = false
    ): String {
        val sourceUri = Uri.parse(streamingUrl)
        val builder = sourceUri.buildUpon().clearQuery()
        val overrideParams = linkedMapOf<String, String>()

        audioStreamIndex?.let {
            overrideParams["AudioStreamIndex"] = it.toString()
        }

        if (preserveOriginalVideo) {
            overrideParams["allowVideoStreamCopy"] = "true"
            overrideParams["allowAudioStreamCopy"] = "false"
            sourceVideoBitrate?.takeIf { it > 0 }?.let { bitrate ->
                overrideParams["VideoBitrate"] = bitrate.toString()
            }
            when (audioTranscodeMode) {
                AudioTranscodeMode.STEREO -> {
                    overrideParams["AudioCodec"] = "aac"
                    overrideParams["TranscodingMaxAudioChannels"] = "2"
                }
                AudioTranscodeMode.SURROUND_5_1 -> {
                    overrideParams["AudioCodec"] = "eac3"
                    overrideParams["TranscodingMaxAudioChannels"] = "6"
                }
                else -> Unit
            }
        } else if (audioTranscodeMode == AudioTranscodeMode.PASSTHROUGH) {
            overrideParams["allowAudioStreamCopy"] = "true"
        }

        sourceUri.queryParameterNames
            .filterNot { queryName ->
                overrideParams.keys.any { key ->
                    queryName.equals(key, ignoreCase = true)
                }
            }
            .forEach { queryName ->
                sourceUri.getQueryParameters(queryName).forEach { queryValue ->
                    builder.appendQueryParameter(queryName, queryValue)
                }
            }

        overrideParams.forEach { (key, value) ->
            builder.appendQueryParameter(key, value)
        }

        return builder.build().toString()
    }
}