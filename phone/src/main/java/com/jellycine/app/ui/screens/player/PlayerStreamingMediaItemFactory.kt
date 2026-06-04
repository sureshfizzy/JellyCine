package com.jellycine.app.ui.screens.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import com.jellycine.data.model.MediaStream
import java.net.URI

private const val TAG = "PlayerMediaItemFactory"

@UnstableApi
internal fun streamingMediaItem(
    streamingUrl: String,
    itemId: String? = null,
    mediaSourceId: String? = null,
    selectedSubtitleStream: MediaStream? = null,
    requestHeaders: Map<String, String> = emptyMap()
): MediaItem {
    val streamUri = Uri.parse(streamingUrl)

    val builder = MediaItem.Builder()
        .setUri(streamUri)

    streamCacheKey(streamUri)?.let(builder::setCustomCacheKey)

    if (selectedSubtitleStream != null) {
        val canDeliverExternally = selectedSubtitleStream.deliveryMethod.equals("External", ignoreCase = true) ||
            !selectedSubtitleStream.deliveryUrl.isNullOrBlank() ||
            selectedSubtitleStream.isExternal == true

        if (canDeliverExternally) {
            val subtitleConfiguration = subtitleConfiguration(
                streamingUrl = streamingUrl,
                itemId = itemId,
                mediaSourceId = mediaSourceId,
                subtitleStream = selectedSubtitleStream,
                requestHeaders = requestHeaders
            )
            if (subtitleConfiguration != null) {
                Log.d(TAG, "Adding external subtitle configuration: ${subtitleConfiguration.uri}")
                builder.setSubtitleConfigurations(listOf(subtitleConfiguration))
            }
        }
    }

    return builder.build()
}

private fun streamCacheKey(streamUri: Uri): String? {
    if (isHlsStreaming(streamUri)) {
        return null
    }

    val metaParams = setOf(
        "deviceid",
        "playsessionid"
    )

    val cacheKeyParams = streamUri.queryParameterNames
        .asSequence()
        .filterNot { parameterName ->
            metaParams.contains(parameterName.lowercase())
        }
        .sorted()
        .flatMap { parameterName ->
            streamUri.getQueryParameters(parameterName)
                .asSequence()
                .sorted()
                .map { parameterValue -> "${parameterName.lowercase()}=$parameterValue" }
        }
        .toList()

    val keyUri = streamUri.buildUpon()
        .clearQuery()
        .build()
        .toString()

    return if (cacheKeyParams.isEmpty()) {
        keyUri
    } else {
        "$keyUri?${cacheKeyParams.joinToString("&")}"
    }
}

private fun isHlsStreaming(streamUri: Uri): Boolean {
    val normalizedUrl = streamUri.toString().lowercase()
    return normalizedUrl.contains(".m3u8") ||
        normalizedUrl.contains("transcoding") ||
        normalizedUrl.contains("transcode")
}

@UnstableApi
private fun subtitleConfiguration(
    streamingUrl: String,
    itemId: String?,
    mediaSourceId: String?,
    subtitleStream: MediaStream,
    requestHeaders: Map<String, String>
): MediaItem.SubtitleConfiguration? {
    var deliveryUrl = subtitleStream.deliveryUrl?.takeIf { it.isNotBlank() }

    // if deliveryUrl is missing but it's an external subtitle, construct it using standard Jellyfin patterns
    if (deliveryUrl == null && (subtitleStream.isExternal == true || subtitleStream.deliveryMethod.equals("External", ignoreCase = true)) && itemId != null) {
        val codec = when (subtitleStream.codec?.lowercase()) {
            "subrip", "srt" -> "subrip" 
            "webvtt", "vtt" -> "vtt"
            else -> "subrip"
        }
        val index = subtitleStream.index ?: 0
        
        val guidItemId = itemId.toGuid()
        val sourceId = mediaSourceId?.replace("-", "") ?: itemId.replace("-", "")
        
        deliveryUrl = "Videos/$guidItemId/$sourceId/Subtitles/$index/0/Stream.$codec"
        Log.d(TAG, "Constructed delivery URL: $deliveryUrl")
    }

    if (deliveryUrl == null) return null

    val resolvedUri = if (deliveryUrl.startsWith("http", ignoreCase = true)) {
        deliveryUrl
    } else {
        runCatching {
            val baseUri = URI.create(streamingUrl)
            "${baseUri.scheme}://${baseUri.authority}/${deliveryUrl.trimStart('/')}"
        }.getOrNull() ?: return null
    }

    val authorizedUri = authorizeUrl(resolvedUri, streamingUrl, requestHeaders)
    Log.d(TAG, "Resolved authorized subtitle URI: $authorizedUri")

    val mimeType = subtitleMimeType(subtitleStream, deliveryUrl)
    val selectionFlags = subtitleSelectionFlags(subtitleStream)

    return MediaItem.SubtitleConfiguration.Builder(Uri.parse(authorizedUri))
        .setMimeType(mimeType)
        .setLanguage(subtitleStream.language)
        .setSelectionFlags(selectionFlags)
        .setLabel(
            subtitleStream.displayTitle
                ?: subtitleStream.title
                ?: subtitleStream.language
                ?: "Subtitle"
        )
        .build()
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

private fun authorizeUrl(targetUrl: String, sourceUrl: String, requestHeaders: Map<String, String>): String {
    val targetUri = Uri.parse(targetUrl)
    
    val hasAuth = targetUri.queryParameterNames.any { 
        it.equals("api_key", ignoreCase = true) || 
        it.equals("ApiKey", ignoreCase = true) || 
        it.equals("Token", ignoreCase = true) 
    }
    if (hasAuth) return targetUrl

    val sourceUri = Uri.parse(sourceUrl)
    
    var authValue = sourceUri.queryParameterNames.firstOrNull { 
        it.equals("api_key", ignoreCase = true) || 
        it.equals("ApiKey", ignoreCase = true) || 
        it.equals("Token", ignoreCase = true) 
    }?.let { sourceUri.getQueryParameter(it) }

    if (authValue == null) {
        val authHeader = requestHeaders["Authorization"] ?: requestHeaders["X-Emby-Authorization"]
        if (authHeader != null) {
            authValue = when {
                authHeader.contains("Token=\"", ignoreCase = true) -> 
                    authHeader.substringAfter("Token=\"").substringBefore("\"")
                authHeader.contains("Token ", ignoreCase = true) -> 
                    authHeader.substringAfter("Token ").trim()
                else -> null
            }
        }
    }

    return if (authValue != null) {
        targetUri.buildUpon().appendQueryParameter("ApiKey", authValue).build().toString()
    } else {
        targetUrl
    }
}

@UnstableApi
private fun subtitleMimeType(
    subtitleStream: MediaStream,
    deliveryUrl: String
): String? {
    val codec = subtitleStream.codec?.lowercase()
    if (!codec.isNullOrBlank()) {
        return when (codec) {
            "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
            "vtt", "webvtt" -> MimeTypes.TEXT_VTT
            "ttml" -> MimeTypes.APPLICATION_TTML
            "ass", "ssa" -> MimeTypes.TEXT_SSA
            else -> null
        }
    }

    val extension = deliveryUrl
        .substringBefore('?')
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()

    return when (extension) {
        "srt", "subrip" -> MimeTypes.APPLICATION_SUBRIP
        "vtt", "webvtt" -> MimeTypes.TEXT_VTT
        "ttml" -> MimeTypes.APPLICATION_TTML
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        else -> null
    }
}

@UnstableApi
private fun subtitleSelectionFlags(subtitleStream: MediaStream): Int {
    var selectionFlags = 0

    if (subtitleStream.isForced == true) {
        selectionFlags = selectionFlags or C.SELECTION_FLAG_FORCED
    }
    if (subtitleStream.isDefault == true || selectionFlags == 0) {
        selectionFlags = selectionFlags or C.SELECTION_FLAG_DEFAULT
    }

    return selectionFlags
}
