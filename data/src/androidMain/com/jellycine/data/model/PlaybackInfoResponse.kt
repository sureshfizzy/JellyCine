package com.jellycine.data.model

import com.google.gson.annotations.SerializedName

/**
 * Response model for Jellyfin playback info
 */
data class PlaybackInfoResponse(
    @SerializedName("MediaSources")
    val mediaSources: List<MediaSource>? = null,
    @SerializedName("PlaySessionId")
    val playSessionId: String? = null,
    @SerializedName("ErrorCode")
    val errorCode: String? = null
)

/**
 * Media source information
 */
data class MediaSource(
    @SerializedName("Id")
    val id: String? = null,
    @SerializedName("Path")
    val path: String? = null,
    @SerializedName("Type")
    val type: String? = null,
    @SerializedName("Container")
    val container: String? = null,
    @SerializedName("Size")
    val size: Long? = null,
    @SerializedName("Name")
    val name: String? = null,
    @SerializedName("IsRemote")
    val isRemote: Boolean? = null,
    @SerializedName("ETag")
    val eTag: String? = null,
    @SerializedName("RunTimeTicks")
    val runTimeTicks: Long? = null,
    @SerializedName("ReadAtNativeFramerate")
    val readAtNativeFramerate: Boolean? = null,
    @SerializedName("IgnoreDts")
    val ignoreDts: Boolean? = null,
    @SerializedName("IgnoreIndex")
    val ignoreIndex: Boolean? = null,
    @SerializedName("GenPtsInput")
    val genPtsInput: Boolean? = null,
    @SerializedName("SupportsTranscoding")
    val supportsTranscoding: Boolean? = null,
    @SerializedName("SupportsDirectStream")
    val supportsDirectStream: Boolean? = null,
    @SerializedName("SupportsDirectPlay")
    val supportsDirectPlay: Boolean? = null,
    @SerializedName("IsInfiniteStream")
    val isInfiniteStream: Boolean? = null,
    @SerializedName("RequiresOpening")
    val requiresOpening: Boolean? = null,
    @SerializedName("OpenToken")
    val openToken: String? = null,
    @SerializedName("RequiresClosing")
    val requiresClosing: Boolean? = null,
    @SerializedName("LiveStreamId")
    val liveStreamId: String? = null,
    @SerializedName("BufferMs")
    val bufferMs: Int? = null,
    @SerializedName("RequiresLooping")
    val requiresLooping: Boolean? = null,
    @SerializedName("SupportsProbing")
    val supportsProbing: Boolean? = null,
    @SerializedName("VideoType")
    val videoType: String? = null,
    @SerializedName("MediaStreams")
    val mediaStreams: List<MediaStream>? = null,
    @SerializedName("MediaAttachments")
    val mediaAttachments: List<MediaAttachment>? = null,
    @SerializedName("Formats")
    val formats: List<String>? = null,
    @SerializedName("Bitrate")
    val bitrate: Int? = null,
    @SerializedName("Timestamp")
    val timestamp: String? = null,
    @SerializedName("RequiredHttpHeaders")
    val requiredHttpHeaders: Map<String, String>? = null,
    @SerializedName("TranscodingUrl")
    val transcodingUrl: String? = null,
    @SerializedName("TranscodingSubProtocol")
    val transcodingSubProtocol: String? = null,
    @SerializedName("TranscodingContainer")
    val transcodingContainer: String? = null,
    @SerializedName("AnalyzeDurationMs")
    val analyzeDurationMs: Int? = null,
    @SerializedName("DefaultAudioStreamIndex")
    val defaultAudioStreamIndex: Int? = null,
    @SerializedName("DefaultSubtitleStreamIndex")
    val defaultSubtitleStreamIndex: Int? = null
)

/**
 * Media stream information (audio/video/subtitle tracks)
 */
data class MediaStream(
    @SerializedName("Codec")
    val codec: String? = null,
    @SerializedName("CodecTag")
    val codecTag: String? = null,
    @SerializedName("Language")
    val language: String? = null,
    @SerializedName("ColorRange")
    val colorRange: String? = null,
    @SerializedName("ColorSpace")
    val colorSpace: String? = null,
    @SerializedName("ColorTransfer")
    val colorTransfer: String? = null,
    @SerializedName("ColorPrimaries")
    val colorPrimaries: String? = null,
    @SerializedName("DvVersionMajor")
    val dvVersionMajor: Int? = null,
    @SerializedName("DvVersionMinor")
    val dvVersionMinor: Int? = null,
    @SerializedName("DvProfile")
    val dvProfile: Int? = null,
    @SerializedName("DvLevel")
    val dvLevel: Int? = null,
    @SerializedName("RpuPresentFlag")
    val rpuPresentFlag: Int? = null,
    @SerializedName("ElPresentFlag")
    val elPresentFlag: Int? = null,
    @SerializedName("BlPresentFlag")
    val blPresentFlag: Int? = null,
    @SerializedName("DvBlSignalCompatibilityId")
    val dvBlSignalCompatibilityId: Int? = null,
    @SerializedName("Comment")
    val comment: String? = null,
    @SerializedName("TimeBase")
    val timeBase: String? = null,
    @SerializedName("CodecTimeBase")
    val codecTimeBase: String? = null,
    @SerializedName("Title")
    val title: String? = null,
    @SerializedName("VideoRange")
    val videoRange: String? = null,
    @SerializedName("VideoRangeType")
    val videoRangeType: String? = null,
    @SerializedName("VideoDoViTitle")
    val videoDoViTitle: String? = null,
    @SerializedName("LocalizedUndefined")
    val localizedUndefined: String? = null,
    @SerializedName("LocalizedDefault")
    val localizedDefault: String? = null,
    @SerializedName("LocalizedForced")
    val localizedForced: String? = null,
    @SerializedName("LocalizedExternal")
    val localizedExternal: String? = null,
    @SerializedName("DisplayTitle")
    val displayTitle: String? = null,
    @SerializedName("NalLengthSize")
    val nalLengthSize: String? = null,
    @SerializedName("IsInterlaced")
    val isInterlaced: Boolean? = null,
    @SerializedName("IsAVC")
    val isAVC: Boolean? = null,
    @SerializedName("ChannelLayout")
    val channelLayout: String? = null,
    @SerializedName("BitRate")
    val bitRate: Int? = null,
    @SerializedName("BitDepth")
    val bitDepth: Int? = null,
    @SerializedName("RefFrames")
    val refFrames: Int? = null,
    @SerializedName("PacketLength")
    val packetLength: Int? = null,
    @SerializedName("Channels")
    val channels: Int? = null,
    @SerializedName("SampleRate")
    val sampleRate: Int? = null,
    @SerializedName("IsDefault")
    val isDefault: Boolean? = null,
    @SerializedName("IsForced")
    val isForced: Boolean? = null,
    @SerializedName("Height")
    val height: Int? = null,
    @SerializedName("Width")
    val width: Int? = null,
    @SerializedName("AverageFrameRate")
    val averageFrameRate: Float? = null,
    @SerializedName("RealFrameRate")
    val realFrameRate: Float? = null,
    @SerializedName("Profile")
    val profile: String? = null,
    @SerializedName("Type")
    val type: String? = null,
    @SerializedName("AspectRatio")
    val aspectRatio: String? = null,
    @SerializedName("Index")
    val index: Int? = null,
    @SerializedName("Score")
    val score: Int? = null,
    @SerializedName("IsExternal")
    val isExternal: Boolean? = null,
    @SerializedName("DeliveryMethod")
    val deliveryMethod: String? = null,
    @SerializedName("DeliveryUrl")
    val deliveryUrl: String? = null,
    @SerializedName("IsExternalUrl")
    val isExternalUrl: Boolean? = null,
    @SerializedName("IsTextSubtitleStream")
    val isTextSubtitleStream: Boolean? = null,
    @SerializedName("SupportsExternalStream")
    val supportsExternalStream: Boolean? = null,
    @SerializedName("Path")
    val path: String? = null,
    @SerializedName("PixelFormat")
    val pixelFormat: String? = null,
    @SerializedName("Level")
    val level: Double? = null,
    @SerializedName("IsAnamorphic")
    val isAnamorphic: Boolean? = null
)

/**
 * Media attachment information
 */
data class MediaAttachment(
    @SerializedName("Codec")
    val codec: String? = null,
    @SerializedName("CodecTag")
    val codecTag: String? = null,
    @SerializedName("Comment")
    val comment: String? = null,
    @SerializedName("Index")
    val index: Int? = null,
    @SerializedName("FileName")
    val fileName: String? = null,
    @SerializedName("MimeType")
    val mimeType: String? = null,
    @SerializedName("DeliveryUrl")
    val deliveryUrl: String? = null
)
