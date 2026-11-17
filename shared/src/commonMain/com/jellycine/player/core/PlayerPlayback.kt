package com.jellycine.player.core

import com.jellycine.data.model.AudioTranscodeMode
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaSource
import com.jellycine.data.model.MediaStream
import com.jellycine.data.model.PlaybackRequest
import com.jellycine.data.model.PlaybackSegmentWindow
import com.jellycine.data.repository.MediaRepository
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.resources.*
import org.jetbrains.compose.resources.getString

private const val TICKS_PER_MS = 10_000L

/** Access to downloaded media for hosts that support offline playback. */
interface PlayerOfflineSource {
    fun getOfflineFilePath(itemId: String): String?
    fun offlineItemMetadata(itemId: String): BaseItemDto?
    fun isTranscodedDownload(itemId: String): Boolean
}

/** Everything the player needs to know about one item before an engine starts playing it. */
class ResolvedPlayback(
    val itemDetails: BaseItemDto?,
    val mediaTitle: String,
    val mediaLogoUrl: String?,
    val posterUrl: String?,
    val seasonEpisodeLabel: String?,
    val chapterMarkers: List<ChapterMarker>,
    val introSegment: PlaybackSegmentWindow?,
    val storedResumePositionMs: Long?,
    val mediaStreams: List<MediaStream>?,
    val defaultAudioStreamIndex: Int?,
    val defaultSubtitleStreamIndex: Int?,
    val session: PlaybackSessionContext,
    val streamUrl: String?,
    val playbackRequest: PlaybackRequest?,
    val offlineFilePath: String?,
    val isTranscodedDownload: Boolean,
    val isVideoTranscodingAllowed: Boolean,
    val isAudioTranscodingAllowed: Boolean,
    val audioTranscodeMode: AudioTranscodeMode
)

sealed interface PlaybackResolution {
    class Ready(val playback: ResolvedPlayback) : PlaybackResolution
    class Failed(val message: String) : PlaybackResolution
}

/**
 * Looks up item details, stream info and the playback URL for an item. This is the part of
 * starting playback that doesn't depend on which player engine is used.
 */
class PlayerPlayback(private val mediaRepository: MediaRepository) {
    private var videoTranscodingAllowed: Boolean? = null
    private var audioTranscodingAllowed: Boolean? = null

    suspend fun resolve(
        mediaId: String,
        initialItemDetails: BaseItemDto?,
        preferences: PlayerPreferences,
        preferredAudioStreamIndex: Int?,
        preferredSubtitleStreamIndex: Int?,
        offline: PlayerOfflineSource?,
        includeAccessToken: Boolean
    ): PlaybackResolution {
        val isVideoTranscodingAllowed = isVideoTranscodingAllowedForUser()
        val isAudioTranscodingAllowed = isAudioTranscodingAllowedForUser()
        val audioTranscodeMode = if (isAudioTranscodingAllowed) {
            preferences.getAudioTranscodeMode()
        } else {
            AudioTranscodeMode.AUTO
        }
        val maxStreamingBitrate = if (isVideoTranscodingAllowed) preferences.getMaxStreamingBitrate() else null
        val maxStreamingHeight = if (isVideoTranscodingAllowed) preferences.getStreamingQualityMaxHeight() else null

        val offlinePath = offline?.getOfflineFilePath(mediaId)
        val hasOfflineFile = !offlinePath.isNullOrBlank()
        val offlineItemDetails = if (hasOfflineFile) offline.offlineItemMetadata(mediaId) else null

        val itemDetails = when {
            initialItemDetails?.id == mediaId -> initialItemDetails
            hasOfflineFile -> offlineItemDetails ?: mediaRepository.getItemById(mediaId).getOrNull()
            else -> mediaRepository.getItemById(mediaId).getOrNull()
        }

        val resumePositionTicks = itemDetails?.userData?.playbackPositionTicks
        val storedResumePositionMs = resumePositionTicks?.takeIf { it > 0 }?.div(TICKS_PER_MS)
        val logoSourceId = when {
            itemDetails?.imageTags?.containsKey("Logo") == true && !itemDetails.id.isNullOrBlank() -> itemDetails.id
            !itemDetails?.parentLogoItemId.isNullOrBlank() && !itemDetails.parentLogoImageTag.isNullOrBlank() ->
                itemDetails.parentLogoItemId
            else -> null
        }
        val mediaLogoUrl = logoSourceId?.let { sourceId ->
            mediaRepository.getImageUrlString(
                itemId = sourceId,
                imageType = "Logo",
                width = 320,
                quality = 90,
                enableImageEnhancers = false
            )
        } ?: itemDetails?.let { mediaRepository.getTmdbLogoUrl(it) }
        val posterItemId = if (itemDetails?.type.equals("Episode", ignoreCase = true)) {
            itemDetails?.seriesId ?: itemDetails?.id
        } else {
            itemDetails?.id
        }
        val posterUrl = posterItemId?.let { id ->
            mediaRepository.getImageUrlString(
                itemId = id,
                imageType = "Primary",
                width = 300,
                quality = 80
            )
        }

        var primaryMediaSource: MediaSource? = null
        var playSessionId: String? = null
        var mediaSourceId: String? = null
        var mediaSourceContainer: String? = null
        var mediaSourceBitrateKbps: Int? = null
        var playMethod = PlayMethod.DIRECT_PLAY
        var streamUrl: String? = null
        var playbackRequest: PlaybackRequest? = null
        val mediaStreams: List<MediaStream>?
        val defaultAudioStreamIndex: Int?
        val defaultSubtitleStreamIndex: Int?
        var isTranscodedDownload = false

        if (hasOfflineFile) {
            playMethod = PlayMethod.OFFLINE
            isTranscodedDownload = offline.isTranscodedDownload(mediaId)
            val offlineItem = offlineItemDetails ?: itemDetails
            mediaStreams = offlineItem?.let {
                mergeApiMediaStreams(itemDetails = it, playbackMediaSource = null)
            }
            val offlineSource = offlineItem?.mediaSources?.firstOrNull()
            defaultAudioStreamIndex = offlineSource?.defaultAudioStreamIndex
            defaultSubtitleStreamIndex = offlineSource?.defaultSubtitleStreamIndex
        } else {
            val playbackInfo = mediaRepository.getPlaybackInfo(
                itemId = mediaId,
                maxStreamingBitrate = maxStreamingBitrate,
                audioStreamIndex = preferredAudioStreamIndex,
                subtitleStreamIndex = preferredSubtitleStreamIndex,
                audioTranscodeMode = audioTranscodeMode
            ).let { result ->
                if (result.isFailure) {
                    return PlaybackResolution.Failed(
                        result.exceptionOrNull()?.message ?: getString(Res.string.player_error_playback_info_failed)
                    )
                }
                result.getOrNull() ?: return PlaybackResolution.Failed(getString(Res.string.player_error_playback_info_missing))
            }

            primaryMediaSource = playbackInfo.mediaSources?.firstOrNull()
            mediaStreams = mergeApiMediaStreams(
                itemDetails = itemDetails,
                playbackMediaSource = primaryMediaSource
            )
            defaultAudioStreamIndex = primaryMediaSource?.defaultAudioStreamIndex
            defaultSubtitleStreamIndex = primaryMediaSource?.defaultSubtitleStreamIndex
            playSessionId = playbackInfo.playSessionId
            mediaSourceId = primaryMediaSource?.id
            mediaSourceContainer = primaryMediaSource?.container
            mediaSourceBitrateKbps = primaryMediaSource?.bitrate?.div(1000)
            playMethod = when {
                primaryMediaSource?.supportsDirectPlay == true -> PlayMethod.DIRECT_PLAY
                primaryMediaSource?.supportsDirectStream == true -> PlayMethod.DIRECT_STREAM
                else -> PlayMethod.TRANSCODE
            }

            val requestResult = mediaRepository.getPlaybackRequest(
                itemId = mediaId,
                maxStreamingBitrate = maxStreamingBitrate,
                maxStreamingHeight = maxStreamingHeight,
                audioStreamIndex = preferredAudioStreamIndex,
                subtitleStreamIndex = preferredSubtitleStreamIndex,
                audioTranscodeMode = audioTranscodeMode,
                playbackInfo = playbackInfo,
                includeAccessToken = includeAccessToken
            )
            if (requestResult.isFailure) {
                return PlaybackResolution.Failed(
                    requestResult.exceptionOrNull()?.message ?: getString(Res.string.player_error_playback_request_failed)
                )
            }
            playbackRequest = requestResult.getOrNull()
            streamUrl = playbackRequest?.url
            if (streamUrl.isNullOrEmpty()) {
                return PlaybackResolution.Failed(getString(Res.string.player_error_playback_url_missing))
            }
            (streamUrl.queryParameter("PlaySessionId") ?: streamUrl.queryParameter("playSessionId"))
                ?.takeIf { it.isNotBlank() }
                ?.let { playSessionId = it }
            playMethod = playMethodForUrl(streamUrl, playMethod)
        }

        return PlaybackResolution.Ready(
            ResolvedPlayback(
                itemDetails = itemDetails,
                mediaTitle = itemDetails?.name ?: getString(Res.string.player_title_unknown),
                mediaLogoUrl = mediaLogoUrl,
                posterUrl = posterUrl,
                seasonEpisodeLabel = itemDetails?.let(::seasonEpisodeLabel),
                chapterMarkers = PlaybackMarkerUtils.buildChapterMarkers(itemDetails?.chapters),
                introSegment = PlaybackMarkerUtils.extractIntroWindow(itemDetails?.chapters),
                storedResumePositionMs = storedResumePositionMs,
                mediaStreams = mediaStreams,
                defaultAudioStreamIndex = defaultAudioStreamIndex,
                defaultSubtitleStreamIndex = defaultSubtitleStreamIndex,
                session = PlaybackSessionContext(
                    mediaId = mediaId,
                    playSessionId = playSessionId,
                    mediaSourceId = mediaSourceId,
                    mediaSourceContainer = mediaSourceContainer,
                    mediaSourceBitrateKbps = mediaSourceBitrateKbps,
                    playMethod = playMethod,
                    isOfflinePlayback = hasOfflineFile
                ),
                streamUrl = streamUrl,
                playbackRequest = playbackRequest,
                offlineFilePath = offlinePath.takeIf { hasOfflineFile },
                isTranscodedDownload = isTranscodedDownload,
                isVideoTranscodingAllowed = isVideoTranscodingAllowed,
                isAudioTranscodingAllowed = isAudioTranscodingAllowed,
                audioTranscodeMode = audioTranscodeMode
            )
        )
    }

    suspend fun isVideoTranscodingAllowedForUser(): Boolean {
        videoTranscodingAllowed?.let { return it }
        mediaRepository.loadPersistedHomeSnapshot()?.isVideoTranscodingAllowed?.let {
            videoTranscodingAllowed = it
            return it
        }

        val user = mediaRepository.getCurrentUser().getOrNull()
        val allowed = user?.policy?.enableVideoPlaybackTranscoding
            ?: user?.let { true }
            ?: false

        videoTranscodingAllowed = allowed
        mediaRepository.persistHomeSnapshot(isVideoTranscodingAllowed = allowed)
        return allowed
    }

    suspend fun isAudioTranscodingAllowedForUser(): Boolean {
        audioTranscodingAllowed?.let { return it }
        mediaRepository.loadPersistedHomeSnapshot()?.isAudioTranscodingAllowed?.let {
            audioTranscodingAllowed = it
            return it
        }

        val user = mediaRepository.getCurrentUser().getOrNull()
        val allowed = user?.policy?.enableAudioPlaybackTranscoding
            ?: user?.let { true }
            ?: false

        audioTranscodingAllowed = allowed
        mediaRepository.persistHomeSnapshot(isAudioTranscodingAllowed = allowed)
        return allowed
    }
}

private fun seasonEpisodeLabel(item: BaseItemDto): String? {
    val season = item.parentIndexNumber
    val episode = item.indexNumber
    if (!item.type.equals("Episode", ignoreCase = true) || season == null || episode == null) return null
    val episodeName = item.episodeTitle?.takeIf { it.isNotBlank() }
        ?: item.name?.takeIf { it.isNotBlank() }
    return buildString {
        append("S")
        append(season)
        append(":E")
        append(episode)
        episodeName?.let {
            append(" - ")
            append(it)
        }
    }
}

/** Reads one query parameter from a URL without needing a platform URL class. */
internal fun String.queryParameter(name: String): String? {
    val query = substringAfter('?', "").substringBefore('#')
    return query.split('&')
        .firstNotNullOfOrNull { pair ->
            val key = pair.substringBefore('=')
            if (key == name) pair.substringAfter('=', "") else null
        }
}

internal fun playMethodForUrl(streamingUrl: String, fallback: PlayMethod): PlayMethod {
    val path = streamingUrl.substringBefore('?')
        .substringAfter("://")
        .substringAfter('/', "")
        .lowercase()
    if (path.contains("master.m3u8") || path.contains("transcode") || path.contains("transcoding")) {
        return PlayMethod.TRANSCODE
    }
    return when (streamingUrl.queryParameter("static")?.lowercase()) {
        "true" -> PlayMethod.DIRECT_PLAY
        "false" -> PlayMethod.DIRECT_STREAM
        else -> fallback
    }
}

/** Combines the streams reported by playback info with those on the item, preferring ones that carry delivery info. */
fun mergeApiMediaStreams(
    itemDetails: BaseItemDto?,
    playbackMediaSource: MediaSource?
): List<MediaStream> {
    val playbackStreams = playbackMediaSource?.mediaStreams.orEmpty()
    val sourceStreams = itemDetails
        ?.mediaSources
        .orEmpty()
        .firstOrNull { source ->
            !playbackMediaSource?.id.isNullOrBlank() && source.id == playbackMediaSource.id
        }
        ?.mediaStreams
        .orEmpty()
    val itemStreams = sourceStreams.ifEmpty {
        itemDetails?.mediaStreams.orEmpty().ifEmpty {
            itemDetails
                ?.mediaSources
                .orEmpty()
                .firstOrNull()
                ?.mediaStreams
                .orEmpty()
        }
    }

    val allStreams = playbackStreams + itemStreams
    return allStreams
        .groupBy { "${it.type.orEmpty().lowercase()}:${it.index ?: "na"}" }
        .map { (_, group) ->
            group.firstOrNull { !it.deliveryUrl.isNullOrBlank() }
                ?: group.firstOrNull { it.deliveryMethod != null }
                ?: group.first()
        }
}

/** Highest source video resolution bucket (2160, 1080, 720, 480) among the streams, if any. */
fun sourceVideoHeight(mediaStreams: List<MediaStream>?): Int? {
    return mediaStreams
        .orEmpty()
        .asSequence()
        .filter { it.type == "Video" }
        .mapNotNull { stream ->
            val width = stream.width ?: 0
            val height = stream.height ?: 0
            when {
                width >= 3840 || height >= 2160 -> 2160
                width >= 1920 || height >= 1080 -> 1080
                width >= 1280 || height >= 720 -> 720
                width >= 854 || height >= 480 -> 480
                height > 0 -> height
                else -> null
            }
        }
        .maxOrNull()
}
