package com.jellycine.app.ui.screens.player

import android.net.Uri
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

private fun subtitleConfiguration(
    streamingUrl: String,
    subtitleStream: MediaStream
): MediaItem.SubtitleConfiguration? {
    val deliveryUrl = subtitleStream.deliveryUrl?.takeIf { it.isNotBlank() } ?: return null
    val resolvedUri = runCatching {
        URI.create(streamingUrl).resolve(deliveryUrl).toString()
    }.getOrNull() ?: return null

    val mimeType = subtitleMimeType(subtitleStream, deliveryUrl)

    return MediaItem.SubtitleConfiguration.Builder(Uri.parse(resolvedUri))
        .setMimeType(mimeType)
        .setLanguage(subtitleStream.language)
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
