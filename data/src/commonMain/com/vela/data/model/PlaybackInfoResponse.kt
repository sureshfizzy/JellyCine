package com.vela.data.model

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

const val STRM_PLAYBACK_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"

fun MediaSource.strmOriginalPlaybackUrl(): String? {
    val rawPath = path?.trim()?.takeIf { candidate ->
        candidate.startsWith("http://", ignoreCase = true) ||
            candidate.startsWith("https://", ignoreCase = true)
    } ?: return null
    if (!isStrmSource()) return null
    return rawPath
}

fun parseStrmPlaylistUrl(content: String?): String? {
    val text = content?.trim()?.removePrefix("\uFEFF") ?: return null
    if (text.isEmpty()) return null
    return text.lineSequence()
        .map { line -> line.trim().removePrefix("\uFEFF").trim('"') }
        .firstOrNull { line ->
            line.isNotEmpty() &&
                !line.startsWith("#") &&
                (
                    line.startsWith("http://", ignoreCase = true) ||
                        line.startsWith("https://", ignoreCase = true)
                    )
        }
}

fun MediaSource.isStrmSource(): Boolean {
    val containerName = container.orEmpty()
    val sourcePath = path.orEmpty().substringBefore('?')
    val sourceName = name.orEmpty()
    return containerName.equals("strm", ignoreCase = true) ||
        sourcePath.endsWith(".strm", ignoreCase = true) ||
        sourceName.endsWith(".strm", ignoreCase = true)
}

fun MediaSource.shouldUseOriginalContainerDownload(): Boolean {
    if (supportsDirectPlay != true) return false
    if (isStrmSource()) return false
    if (isRemote == true) return false
    val sourcePath = path.orEmpty()
    if (
        sourcePath.startsWith("http://", ignoreCase = true) ||
        sourcePath.startsWith("https://", ignoreCase = true)
    ) {
        return false
    }
    return !isCloudMountPath(sourcePath)
}

fun isCloudMountPath(path: String): Boolean {
    val normalized = path.lowercase()
    return normalized.contains("/115open") ||
        normalized.contains("/mnt/115") ||
        normalized.contains("clouddrive") ||
        normalized.contains("/openlist") ||
        normalized.contains("/alist/")
}

fun PlaybackInfoResponse.selectedMediaSource(mediaSourceId: String?): MediaSource? {
    val sources = mediaSources.orEmpty()
    if (sources.isEmpty()) return null
    return mediaSourceId
        ?.takeIf { it.isNotBlank() }
        ?.let { id -> sources.firstOrNull { source -> source.id.equals(id, ignoreCase = true) } }
        ?: sources.first()
}

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
 * 只把真正的外置字幕交给独立字幕加载器。
 *
 * 服务端也会给内嵌字幕提供 External delivery endpoint；若据此把内嵌 ASS 抽取成外置文件，
 * 会丢掉容器附件字体和时间基准。旧版服务端缺少 IsExternal 时才回退到 DeliveryMethod。
 */
fun MediaStream.requiresExternalSubtitleLoad(): Boolean {
    if (!type.equals("Subtitle", ignoreCase = true)) return false
    return isExternal == true ||
        (isExternal == null && deliveryMethod.equals("External", ignoreCase = true))
}

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
