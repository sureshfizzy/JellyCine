package com.jellycine.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response model for Jellyfin playback info
 */
@Serializable
data class PlaybackInfoResponse(
    @SerialName("MediaSources")
    val mediaSources: List<MediaSource>? = null,
    @SerialName("PlaySessionId")
    val playSessionId: String? = null,
    @SerialName("ErrorCode")
    val errorCode: String? = null
)

/**
 * Media source information
 */
@Serializable
data class MediaSource(
    @SerialName("Id")
    val id: String? = null,
    @SerialName("Path")
    val path: String? = null,
    @SerialName("Type")
    val type: String? = null,
    @SerialName("Container")
    val container: String? = null,
    @SerialName("Size")
    val size: Long? = null,
    @SerialName("Name")
    val name: String? = null,
    @SerialName("IsRemote")
    val isRemote: Boolean? = null,
    @SerialName("ETag")
    val eTag: String? = null,
    @SerialName("RunTimeTicks")
    val runTimeTicks: Long? = null,
    @SerialName("ReadAtNativeFramerate")
    val readAtNativeFramerate: Boolean? = null,
    @SerialName("IgnoreDts")
    val ignoreDts: Boolean? = null,
    @SerialName("IgnoreIndex")
    val ignoreIndex: Boolean? = null,
    @SerialName("GenPtsInput")
    val genPtsInput: Boolean? = null,
    @SerialName("SupportsTranscoding")
    val supportsTranscoding: Boolean? = null,
    @SerialName("SupportsDirectStream")
    val supportsDirectStream: Boolean? = null,
    @SerialName("SupportsDirectPlay")
    val supportsDirectPlay: Boolean? = null,
    @SerialName("IsInfiniteStream")
    val isInfiniteStream: Boolean? = null,
    @SerialName("RequiresOpening")
    val requiresOpening: Boolean? = null,
    @SerialName("OpenToken")
    val openToken: String? = null,
    @SerialName("RequiresClosing")
    val requiresClosing: Boolean? = null,
    @SerialName("LiveStreamId")
    val liveStreamId: String? = null,
    @SerialName("BufferMs")
    val bufferMs: Int? = null,
    @SerialName("RequiresLooping")
    val requiresLooping: Boolean? = null,
    @SerialName("SupportsProbing")
    val supportsProbing: Boolean? = null,
    @SerialName("VideoType")
    val videoType: String? = null,
    @SerialName("MediaStreams")
    val mediaStreams: List<MediaStream>? = null,
    @SerialName("MediaAttachments")
    val mediaAttachments: List<MediaAttachment>? = null,
    @SerialName("Formats")
    val formats: List<String>? = null,
    @SerialName("Bitrate")
    val bitrate: Int? = null,
    @SerialName("Timestamp")
    val timestamp: String? = null,
    @SerialName("RequiredHttpHeaders")
    val requiredHttpHeaders: Map<String, String>? = null,
    @SerialName("TranscodingUrl")
    val transcodingUrl: String? = null,
    @SerialName("TranscodingSubProtocol")
    val transcodingSubProtocol: String? = null,
    @SerialName("TranscodingContainer")
    val transcodingContainer: String? = null,
    @SerialName("AnalyzeDurationMs")
    val analyzeDurationMs: Int? = null,
    @SerialName("DefaultAudioStreamIndex")
    val defaultAudioStreamIndex: Int? = null,
    @SerialName("DefaultSubtitleStreamIndex")
    val defaultSubtitleStreamIndex: Int? = null
)

/**
 * Media stream information (audio/video/subtitle tracks)
 */
@Serializable
data class MediaStream(
    @SerialName("Codec")
    val codec: String? = null,
    @SerialName("CodecTag")
    val codecTag: String? = null,
    @SerialName("Language")
    val language: String? = null,
    @SerialName("ColorRange")
    val colorRange: String? = null,
    @SerialName("ColorSpace")
    val colorSpace: String? = null,
    @SerialName("ColorTransfer")
    val colorTransfer: String? = null,
    @SerialName("ColorPrimaries")
    val colorPrimaries: String? = null,
    @SerialName("DvVersionMajor")
    val dvVersionMajor: Int? = null,
    @SerialName("DvVersionMinor")
    val dvVersionMinor: Int? = null,
    @SerialName("DvProfile")
    val dvProfile: Int? = null,
    @SerialName("DvLevel")
    val dvLevel: Int? = null,
    @SerialName("RpuPresentFlag")
    val rpuPresentFlag: Int? = null,
    @SerialName("ElPresentFlag")
    val elPresentFlag: Int? = null,
    @SerialName("BlPresentFlag")
    val blPresentFlag: Int? = null,
    @SerialName("DvBlSignalCompatibilityId")
    val dvBlSignalCompatibilityId: Int? = null,
    @SerialName("Comment")
    val comment: String? = null,
    @SerialName("TimeBase")
    val timeBase: String? = null,
    @SerialName("CodecTimeBase")
    val codecTimeBase: String? = null,
    @SerialName("Title")
    val title: String? = null,
    @SerialName("VideoRange")
    val videoRange: String? = null,
    @SerialName("VideoRangeType")
    val videoRangeType: String? = null,
    @SerialName("VideoDoViTitle")
    val videoDoViTitle: String? = null,
    @SerialName("LocalizedUndefined")
    val localizedUndefined: String? = null,
    @SerialName("LocalizedDefault")
    val localizedDefault: String? = null,
    @SerialName("LocalizedForced")
    val localizedForced: String? = null,
    @SerialName("LocalizedExternal")
    val localizedExternal: String? = null,
    @SerialName("DisplayTitle")
    val displayTitle: String? = null,
    @SerialName("NalLengthSize")
    val nalLengthSize: String? = null,
    @SerialName("IsInterlaced")
    val isInterlaced: Boolean? = null,
    @SerialName("IsAVC")
    val isAVC: Boolean? = null,
    @SerialName("ChannelLayout")
    val channelLayout: String? = null,
    @SerialName("BitRate")
    val bitRate: Int? = null,
    @SerialName("BitDepth")
    val bitDepth: Int? = null,
    @SerialName("RefFrames")
    val refFrames: Int? = null,
    @SerialName("PacketLength")
    val packetLength: Int? = null,
    @SerialName("Channels")
    val channels: Int? = null,
    @SerialName("SampleRate")
    val sampleRate: Int? = null,
    @SerialName("IsDefault")
    val isDefault: Boolean? = null,
    @SerialName("IsForced")
    val isForced: Boolean? = null,
    @SerialName("Height")
    val height: Int? = null,
    @SerialName("Width")
    val width: Int? = null,
    @SerialName("AverageFrameRate")
    val averageFrameRate: Float? = null,
    @SerialName("RealFrameRate")
    val realFrameRate: Float? = null,
    @SerialName("Profile")
    val profile: String? = null,
    @SerialName("Type")
    val type: String? = null,
    @SerialName("AspectRatio")
    val aspectRatio: String? = null,
    @SerialName("Index")
    val index: Int? = null,
    @SerialName("Score")
    val score: Int? = null,
    @SerialName("IsExternal")
    val isExternal: Boolean? = null,
    @SerialName("DeliveryMethod")
    val deliveryMethod: String? = null,
    @SerialName("DeliveryUrl")
    val deliveryUrl: String? = null,
    @SerialName("IsExternalUrl")
    val isExternalUrl: Boolean? = null,
    @SerialName("IsTextSubtitleStream")
    val isTextSubtitleStream: Boolean? = null,
    @SerialName("SupportsExternalStream")
    val supportsExternalStream: Boolean? = null,
    @SerialName("Path")
    val path: String? = null,
    @SerialName("PixelFormat")
    val pixelFormat: String? = null,
    @SerialName("Level")
    val level: Double? = null,
    @SerialName("IsAnamorphic")
    val isAnamorphic: Boolean? = null
)

/**
 * Media attachment information
 */
@Serializable
data class MediaAttachment(
    @SerialName("Codec")
    val codec: String? = null,
    @SerialName("CodecTag")
    val codecTag: String? = null,
    @SerialName("Comment")
    val comment: String? = null,
    @SerialName("Index")
    val index: Int? = null,
    @SerialName("FileName")
    val fileName: String? = null,
    @SerialName("MimeType")
    val mimeType: String? = null,
    @SerialName("DeliveryUrl")
    val deliveryUrl: String? = null
)