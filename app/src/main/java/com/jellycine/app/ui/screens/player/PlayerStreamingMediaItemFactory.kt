package com.jellycine.app.ui.screens.player

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes

internal fun streamingMediaItem(streamingUrl: String): MediaItem {
    val streamUri = Uri.parse(streamingUrl)
    val normalizedPath = streamUri.path.orEmpty().lowercase()
    val isHlsUrl = normalizedPath.endsWith(".m3u8") ||
        normalizedPath.contains("/master.m3u8") ||
        normalizedPath.contains("/main.m3u8")

    return if (isHlsUrl) {
        MediaItem.Builder()
            .setUri(streamUri)
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .build()
    } else {
        MediaItem.fromUri(streamUri)
    }
}
