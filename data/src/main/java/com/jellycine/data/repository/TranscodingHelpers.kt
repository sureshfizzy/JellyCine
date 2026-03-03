package com.jellycine.data.repository

import com.jellycine.data.model.MediaStream

internal data class StreamResolutionCap(
    val maxWidth: Int,
    val maxHeight: Int
)

internal object TranscodingHelpers {
    fun streamResolutionCap(maxStreamingHeight: Int?): StreamResolutionCap? {
        return maxStreamingHeight?.takeIf { it > 0 }?.let { height ->
            when {
                height >= 2160 -> StreamResolutionCap(maxWidth = 3840, maxHeight = 2160)
                height >= 1080 -> StreamResolutionCap(maxWidth = 1920, maxHeight = 1080)
                height >= 720 -> StreamResolutionCap(maxWidth = 1280, maxHeight = 720)
                height >= 480 -> StreamResolutionCap(maxWidth = 854, maxHeight = 480)
                height >= 360 -> StreamResolutionCap(maxWidth = 640, maxHeight = 360)
                else -> StreamResolutionCap(maxWidth = 640, maxHeight = 360)
            }
        }
    }

    fun clampResolution(
        resolutionCap: StreamResolutionCap?,
        sourceVideoStream: MediaStream?
    ): StreamResolutionCap? {
        val cap = resolutionCap ?: return null
        val sourceHeight = sourceVideoStream?.height ?: return cap
        if (sourceHeight <= 0) return cap

        val sourceWidth = sourceVideoStream.width?.takeIf { it > 0 }
        val clampedHeight = minOf(cap.maxHeight, sourceHeight)
        val clampedWidth = sourceWidth?.let { minOf(cap.maxWidth, it) } ?: cap.maxWidth

        return StreamResolutionCap(
            maxWidth = clampedWidth,
            maxHeight = clampedHeight
        )
    }
}
