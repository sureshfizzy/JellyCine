package com.jellycine.data.model

import com.google.gson.annotations.SerializedName

/**
 * Request body for POST /Items/{id}/PlaybackInfo.
 * Using explicit serialized names keeps request compatibility with Emby and Jellyfin.
 */
data class PlaybackInfoRequest(
    @SerializedName("UserId")
    val userId: String,
    @SerializedName("MaxStreamingBitrate")
    val maxStreamingBitrate: Long? = null,
    @SerializedName("AudioStreamIndex")
    val audioStreamIndex: Int? = null,
    @SerializedName("SubtitleStreamIndex")
    val subtitleStreamIndex: Int? = null,
    @SerializedName("IsPlayback")
    val isPlayback: Boolean = true,
    @SerializedName("AutoOpenLiveStream")
    val autoOpenLiveStream: Boolean = true,
    @SerializedName("EnableDirectPlay")
    val enableDirectPlay: Boolean = true,
    @SerializedName("EnableDirectStream")
    val enableDirectStream: Boolean = true,
    @SerializedName("EnableTranscoding")
    val enableTranscoding: Boolean = true
)
