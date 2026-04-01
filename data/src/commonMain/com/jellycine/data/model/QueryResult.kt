package com.jellycine.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QueryResult<T>(
    @SerialName("Items")
    val items: List<T>? = null,
    
    @SerialName("TotalRecordCount")
    val totalRecordCount: Int? = null,
    
    @SerialName("StartIndex")
    val startIndex: Int? = null
)

@Serializable
data class ExternalUrl(
    @SerialName("Name")
    val name: String? = null,
    
    @SerialName("Url")
    val url: String? = null
)

@Serializable
data class MediaSourceInfo(
    @SerialName("Protocol")
    val protocol: String? = null,
    
    @SerialName("Id")
    val id: String? = null,
    
    @SerialName("Path")
    val path: String? = null,
    
    @SerialName("EncoderPath")
    val encoderPath: String? = null,
    
    @SerialName("EncoderProtocol")
    val encoderProtocol: String? = null,
    
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
    
    @SerialName("IsoType")
    val isoType: String? = null,
    
    @SerialName("Video3DFormat")
    val video3DFormat: String? = null,
    
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

@Serializable
data class MediaUrl(
    @SerialName("Url")
    val url: String? = null,
    
    @SerialName("Name")
    val name: String? = null
)

@Serializable
data class BaseItemPerson(
    @SerialName("Name")
    val name: String? = null,
    
    @SerialName("Id")
    val id: String? = null,
    
    @SerialName("Role")
    val role: String? = null,
    
    @SerialName("Type")
    val type: String? = null,
    
    @SerialName("PrimaryImageTag")
    val primaryImageTag: String? = null,
    
    @SerialName("ImageBlurHashes")
    val imageBlurHashes: Map<String, Map<String, String>>? = null
)

@Serializable
data class NameGuidPair(
    @SerialName("Name")
    val name: String? = null,
    
    @SerialName("Id")
    val id: String? = null
)

@Serializable
data class UserItemDataDto(
    @SerialName("Rating")
    val rating: Double? = null,
    
    @SerialName("PlayedPercentage")
    val playedPercentage: Double? = null,
    
    @SerialName("UnplayedItemCount")
    val unplayedItemCount: Int? = null,
    
    @SerialName("PlaybackPositionTicks")
    val playbackPositionTicks: Long? = null,
    
    @SerialName("PlayCount")
    val playCount: Int? = null,
    
    @SerialName("IsFavorite")
    val isFavorite: Boolean? = null,
    
    @SerialName("Likes")
    val likes: Boolean? = null,
    
    @SerialName("LastPlayedDate")
    val lastPlayedDate: String? = null,
    
    @SerialName("Played")
    val played: Boolean? = null,
    
    @SerialName("Key")
    val key: String? = null,
    
    @SerialName("ItemId")
    val itemId: String? = null
)

@Serializable
data class ChapterInfo(
    @SerialName("StartPositionTicks")
    val startPositionTicks: Long? = null,
    
    @SerialName("Name")
    val name: String? = null,
    
    @SerialName("ImagePath")
    val imagePath: String? = null,
    
    @SerialName("ImageDateModified")
    val imageDateModified: String? = null,
    
    @SerialName("ImageTag")
    val imageTag: String? = null
)
