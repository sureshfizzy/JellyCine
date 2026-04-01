package com.jellycine.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request model for reporting playback start to Jellyfin server
 */
@Serializable
data class PlaybackStartRequest(
    @SerialName("ItemId")
    val itemId: String,
    @SerialName("PlaySessionId")
    val playSessionId: String? = null,
    @SerialName("MediaSourceId")
    val mediaSourceId: String? = null,
    @SerialName("AudioStreamIndex")
    val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex")
    val subtitleStreamIndex: Int? = null,
    @SerialName("IsPaused")
    val isPaused: Boolean = false,
    @SerialName("IsMuted")
    val isMuted: Boolean = false,
    @SerialName("PositionTicks")
    val positionTicks: Long? = null,
    @SerialName("VolumeLevel")
    val volumeLevel: Int? = null,
    @SerialName("Brightness")
    val brightness: Int? = null,
    @SerialName("AspectRatio")
    val aspectRatio: String? = null,
    @SerialName("PlayMethod")
    val playMethod: String? = null,
    @SerialName("LiveStreamId")
    val liveStreamId: String? = null,
    @SerialName("PlaylistItemId")
    val playlistItemId: String? = null,
    @SerialName("CanSeek")
    val canSeek: Boolean = true
)

/**
 * Request model for reporting playback progress to Jellyfin server
 */
@Serializable
data class PlaybackProgressRequest(
    @SerialName("ItemId")
    val itemId: String,
    @SerialName("PlaySessionId")
    val playSessionId: String? = null,
    @SerialName("MediaSourceId")
    val mediaSourceId: String? = null,
    @SerialName("AudioStreamIndex")
    val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex")
    val subtitleStreamIndex: Int? = null,
    @SerialName("IsPaused")
    val isPaused: Boolean = false,
    @SerialName("IsMuted")
    val isMuted: Boolean = false,
    @SerialName("PositionTicks")
    val positionTicks: Long,
    @SerialName("VolumeLevel")
    val volumeLevel: Int? = null,
    @SerialName("Brightness")
    val brightness: Int? = null,
    @SerialName("AspectRatio")
    val aspectRatio: String? = null,
    @SerialName("PlayMethod")
    val playMethod: String? = null,
    @SerialName("LiveStreamId")
    val liveStreamId: String? = null,
    @SerialName("PlaylistItemId")
    val playlistItemId: String? = null,
    @SerialName("RepeatMode")
    val repeatMode: String? = null,
    @SerialName("BufferedRanges")
    val bufferedRanges: List<BufferedRange>? = null
)

/**
 * Request model for reporting playback stopped to Jellyfin server
 */
@Serializable
data class PlaybackStoppedRequest(
    @SerialName("ItemId")
    val itemId: String,
    @SerialName("PlaySessionId")
    val playSessionId: String? = null,
    @SerialName("MediaSourceId")
    val mediaSourceId: String? = null,
    @SerialName("PositionTicks")
    val positionTicks: Long? = null,
    @SerialName("LiveStreamId")
    val liveStreamId: String? = null,
    @SerialName("PlaylistItemId")
    val playlistItemId: String? = null,
    @SerialName("Failed")
    val failed: Boolean = false,
    @SerialName("NextMediaType")
    val nextMediaType: String? = null,
    @SerialName("PlaybackOrder")
    val playbackOrder: String? = null
)

/**
 * Represents a buffered range in the media
 */
@Serializable
data class BufferedRange(
    @SerialName("start")
    val start: Long,
    @SerialName("end")
    val end: Long
)

/**
 * Enum class for play methods
 */
@Serializable
enum class PlayMethod {
    DirectPlay,
    DirectStream,
    Transcode
}