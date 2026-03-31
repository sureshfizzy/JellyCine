package com.jellycine.data.model

import com.google.gson.annotations.SerializedName

/**
 * Request model for reporting playback start to Jellyfin server
 */
data class PlaybackStartRequest(
    @SerializedName("ItemId")
    val itemId: String,
    @SerializedName("PlaySessionId")
    val playSessionId: String? = null,
    @SerializedName("MediaSourceId")
    val mediaSourceId: String? = null,
    @SerializedName("AudioStreamIndex")
    val audioStreamIndex: Int? = null,
    @SerializedName("SubtitleStreamIndex")
    val subtitleStreamIndex: Int? = null,
    @SerializedName("IsPaused")
    val isPaused: Boolean = false,
    @SerializedName("IsMuted")
    val isMuted: Boolean = false,
    @SerializedName("PositionTicks")
    val positionTicks: Long? = null,
    @SerializedName("VolumeLevel")
    val volumeLevel: Int? = null,
    @SerializedName("Brightness")
    val brightness: Int? = null,
    @SerializedName("AspectRatio")
    val aspectRatio: String? = null,
    @SerializedName("PlayMethod")
    val playMethod: String? = null,
    @SerializedName("LiveStreamId")
    val liveStreamId: String? = null,
    @SerializedName("PlaylistItemId")
    val playlistItemId: String? = null,
    @SerializedName("CanSeek")
    val canSeek: Boolean = true
)

/**
 * Request model for reporting playback progress to Jellyfin server
 */
data class PlaybackProgressRequest(
    @SerializedName("ItemId")
    val itemId: String,
    @SerializedName("PlaySessionId")
    val playSessionId: String? = null,
    @SerializedName("MediaSourceId")
    val mediaSourceId: String? = null,
    @SerializedName("AudioStreamIndex")
    val audioStreamIndex: Int? = null,
    @SerializedName("SubtitleStreamIndex")
    val subtitleStreamIndex: Int? = null,
    @SerializedName("IsPaused")
    val isPaused: Boolean = false,
    @SerializedName("IsMuted")
    val isMuted: Boolean = false,
    @SerializedName("PositionTicks")
    val positionTicks: Long,
    @SerializedName("VolumeLevel")
    val volumeLevel: Int? = null,
    @SerializedName("Brightness")
    val brightness: Int? = null,
    @SerializedName("AspectRatio")
    val aspectRatio: String? = null,
    @SerializedName("PlayMethod")
    val playMethod: String? = null,
    @SerializedName("LiveStreamId")
    val liveStreamId: String? = null,
    @SerializedName("PlaylistItemId")
    val playlistItemId: String? = null,
    @SerializedName("RepeatMode")
    val repeatMode: String? = null,
    @SerializedName("BufferedRanges")
    val bufferedRanges: List<BufferedRange>? = null
)

/**
 * Request model for reporting playback stopped to Jellyfin server
 */
data class PlaybackStoppedRequest(
    @SerializedName("ItemId")
    val itemId: String,
    @SerializedName("PlaySessionId")
    val playSessionId: String? = null,
    @SerializedName("MediaSourceId")
    val mediaSourceId: String? = null,
    @SerializedName("PositionTicks")
    val positionTicks: Long? = null,
    @SerializedName("LiveStreamId")
    val liveStreamId: String? = null,
    @SerializedName("PlaylistItemId")
    val playlistItemId: String? = null,
    @SerializedName("Failed")
    val failed: Boolean = false,
    @SerializedName("NextMediaType")
    val nextMediaType: String? = null,
    @SerializedName("PlaybackOrder")
    val playbackOrder: String? = null
)

/**
 * Represents a buffered range in the media
 */
data class BufferedRange(
    @SerializedName("start")
    val start: Long,
    @SerializedName("end")
    val end: Long
)

/**
 * Enum class for play methods
 */
enum class PlayMethod {
    DirectPlay,
    DirectStream,
    Transcode
}