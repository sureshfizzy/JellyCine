package com.jellycine.detail

import android.content.Context
import com.jellycine.data.model.MediaStream
import com.jellycine.player.audio.SpatializerHelper

/**
 * Check if the device can spatialize the given audio stream.
 * Returns true only when content is spatializable and the current route can render it.
 */
fun CodecCapabilityManager.canSpatializeAudioStream(
    context: Context,
    audioStream: MediaStream,
    spatializerHelper: SpatializerHelper? = null
): SpatializationResult {
    val spatialFormat = detectSpatialAudio(
        audioStream.codec?.uppercase() ?: "",
        audioStream.channels ?: 0,
        audioStream.channelLayout,
        audioStream.title,
        audioStream.profile
    )

    if (spatialFormat.isEmpty()) {
        return SpatializationResult(
            canSpatialize = false,
            reason = "Content is not multichannel/object-based spatial audio",
            spatialFormat = "Stereo"
        )
    }

    val helper = spatializerHelper ?: SpatializerHelper(context)
    val requestedChannelCount = (audioStream.channels ?: 2).coerceAtLeast(2)
    val audioFormat = helper.getRecommendedAudioFormat(requestedChannelCount)

    if (audioFormat == null) {
        return SpatializationResult(
            canSpatialize = false,
            reason = "Spatializable content detected, but no compatible spatial audio format is available",
            spatialFormat = spatialFormat
        )
    }

    val routeSupportsSpatialization = helper.canSpatializeOnTrack(audioFormat)
    val spatializerActive = helper.canSpatializeAudio(audioFormat)

    return if (spatializerActive) {
        SpatializationResult(
            canSpatialize = true,
            reason = "Spatializable content detected and device spatializer is active for this route",
            spatialFormat = spatialFormat
        )
    } else if (routeSupportsSpatialization) {
        SpatializationResult(
            canSpatialize = false,
            reason = "Spatializable content detected, but device spatializer is disabled in system settings",
            spatialFormat = spatialFormat
        )
    } else {
        SpatializationResult(
            canSpatialize = false,
            reason = "Spatializable content detected, but device spatializer is unavailable for current route/format",
            spatialFormat = spatialFormat
        )
    }
}
