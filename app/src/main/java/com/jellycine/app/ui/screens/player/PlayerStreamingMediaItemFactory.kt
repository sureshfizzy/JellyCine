package com.jellycine.app.ui.screens.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.jellycine.data.model.MediaStream
import java.net.URI

internal fun streamingMediaItem(
    streamingUrl: String,
    selectedSubtitleStream: MediaStream? = null
): MediaItem {
    val streamUri = Uri.parse(streamingUrl)

    val builder = MediaItem.Builder()
        .setUri(streamUri)
        .setCustomCacheKey(streamCacheKey(streamUri))

    selectedSubtitleStream
        ?.takeIf { it.deliveryMethod.equals("External", ignoreCase = true) }
        ?.let { subtitleStream ->
            val subtitleConfiguration = subtitleConfiguration(
                streamingUrl = streamingUrl,
                subtitleStream = subtitleStream
            )
            if (subtitleConfiguration != null) {
                builder.setSubtitleConfigurations(listOf(subtitleConfiguration))
            }
        }

    return builder.build()
}

private fun streamCacheKey(streamUri: Uri): String {
    val metaParams = setOf(
        "api_key",
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

private fun subtitleConfiguration(
    streamingUrl: String,
    subtitleStream: MediaStream
): MediaItem.SubtitleConfiguration? {
    val deliveryUrl = subtitleStream.deliveryUrl?.takeIf { it.isNotBlank() } ?: return null
    val resolvedUri = runCatching {
        URI.create(streamingUrl).resolve(deliveryUrl).toString()
    }.getOrNull() ?: return null

    val mimeType = subtitleMimeType(subtitleStream, deliveryUrl)
    val selectionFlags = subtitleSelectionFlags(subtitleStream)

    return MediaItem.SubtitleConfiguration.Builder(Uri.parse(resolvedUri))
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
