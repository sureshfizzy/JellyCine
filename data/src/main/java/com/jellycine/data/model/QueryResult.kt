package com.jellycine.data.model

import com.google.gson.annotations.SerializedName

data class QueryResult<T>(
    @SerializedName("Items")
    val items: List<T>? = null,
    
    @SerializedName("TotalRecordCount")
    val totalRecordCount: Int? = null,
    
    @SerializedName("StartIndex")
    val startIndex: Int? = null
)

data class ExternalUrl(
    @SerializedName("Name")
    val name: String? = null,
    
    @SerializedName("Url")
    val url: String? = null
)

data class MediaSourceInfo(
    @SerializedName("Protocol")
    val protocol: String? = null,
    
    @SerializedName("Id")
    val id: String? = null,
    
    @SerializedName("Path")
    val path: String? = null,
    
    @SerializedName("EncoderPath")
    val encoderPath: String? = null,
    
    @SerializedName("EncoderProtocol")
    val encoderProtocol: String? = null,
    
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
    
    @SerializedName("IsoType")
    val isoType: String? = null,
    
    @SerializedName("Video3DFormat")
    val video3DFormat: String? = null,
    
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

data class MediaUrl(
    @SerializedName("Url")
    val url: String? = null,
    
    @SerializedName("Name")
    val name: String? = null
)

data class BaseItemPerson(
    @SerializedName("Name")
    val name: String? = null,
    
    @SerializedName("Id")
    val id: String? = null,
    
    @SerializedName("Role")
    val role: String? = null,
    
    @SerializedName("Type")
    val type: String? = null,
    
    @SerializedName("PrimaryImageTag")
    val primaryImageTag: String? = null,
    
    @SerializedName("ImageBlurHashes")
    val imageBlurHashes: Map<String, Map<String, String>>? = null
)

data class NameGuidPair(
    @SerializedName("Name")
    val name: String? = null,
    
    @SerializedName("Id")
    val id: String? = null
)

data class UserItemDataDto(
    @SerializedName("Rating")
    val rating: Double? = null,
    
    @SerializedName("PlayedPercentage")
    val playedPercentage: Double? = null,
    
    @SerializedName("UnplayedItemCount")
    val unplayedItemCount: Int? = null,
    
    @SerializedName("PlaybackPositionTicks")
    val playbackPositionTicks: Long? = null,
    
    @SerializedName("PlayCount")
    val playCount: Int? = null,
    
    @SerializedName("IsFavorite")
    val isFavorite: Boolean? = null,
    
    @SerializedName("Likes")
    val likes: Boolean? = null,
    
    @SerializedName("LastPlayedDate")
    val lastPlayedDate: String? = null,
    
    @SerializedName("Played")
    val played: Boolean? = null,
    
    @SerializedName("Key")
    val key: String? = null,
    
    @SerializedName("ItemId")
    val itemId: String? = null
)

data class ChapterInfo(
    @SerializedName("StartPositionTicks")
    val startPositionTicks: Long? = null,
    
    @SerializedName("Name")
    val name: String? = null,
    
    @SerializedName("ImagePath")
    val imagePath: String? = null,
    
    @SerializedName("ImageDateModified")
    val imageDateModified: String? = null,
    
    @SerializedName("ImageTag")
    val imageTag: String? = null
)
