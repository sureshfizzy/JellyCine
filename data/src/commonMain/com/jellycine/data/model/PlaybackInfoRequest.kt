package com.jellycine.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request body for POST /Items/{id}/PlaybackInfo.
 * Using explicit serialized names keeps request compatibility with Emby and Jellyfin.
 */
@Serializable
data class PlaybackInfoRequest(
    @SerialName("UserId")
    val userId: String,
    @SerialName("MediaSourceId")
    val mediaSourceId: String? = null,
    @SerialName("MaxStreamingBitrate")
    val maxStreamingBitrate: Long? = null,
    @SerialName("AudioStreamIndex")
    val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex")
    val subtitleStreamIndex: Int? = null,
    @SerialName("IsPlayback")
    val isPlayback: Boolean = true,
    @SerialName("AutoOpenLiveStream")
    val autoOpenLiveStream: Boolean = true,
    @SerialName("EnableDirectPlay")
    val enableDirectPlay: Boolean? = null,
    @SerialName("EnableDirectStream")
    val enableDirectStream: Boolean? = null,
    @SerialName("EnableTranscoding")
    val enableTranscoding: Boolean? = null,
    @SerialName("DeviceProfile")
    val deviceProfile: DeviceProfile? = null
)
