package com.jellycine.app.ui.screens.player.mpv

import com.jellycine.data.model.MediaStream
import com.jellycine.data.model.PlaybackRequest
import com.jellycine.player.core.AudioTrackInfo
import com.jellycine.player.core.PlayerTrackState
import com.jellycine.player.core.SubtitleTrackInfo
import java.net.URI

object MPVPlayer {
    fun trackState(
        mediaStreams: List<MediaStream>?,
        selectedAudioStreamIndex: Int?,
        selectedSubtitleStreamIndex: Int?,
        defaultAudioStreamIndex: Int?,
        defaultSubtitleStreamIndex: Int?
    ): PlayerTrackState {
        val audioStreams = streams(mediaStreams, "Audio")
        val subtitleStreams = streams(mediaStreams, "Subtitle")
        val audioTracks = audioStreams.mapNotNull { stream ->
            val streamIndex = stream.index ?: return@mapNotNull null
            AudioTrackInfo(
                id = "audio:$streamIndex",
                label = stream.displayTitle ?: stream.title ?: stream.language ?: "Audio $streamIndex",
                language = stream.language,
                channelCount = stream.channels ?: 0,
                codec = stream.codec,
                playerTrackId = trackId(audioStreams, streamIndex) ?: return@mapNotNull null,
                streamIndex = streamIndex,
                requiresPlaybackRestart = false
            )
        }
        val subtitleTracks = listOf(
            SubtitleTrackInfo(
                id = "off",
                label = "Off",
                language = null,
                streamIndex = -1,
                requiresPlaybackRestart = false
            )
        ) + subtitleStreams.mapNotNull { stream ->
            val streamIndex = stream.index ?: return@mapNotNull null
            SubtitleTrackInfo(
                id = "subtitle:$streamIndex",
                label = stream.displayTitle ?: stream.title ?: stream.language ?: "Subtitle $streamIndex",
                language = stream.language,
                isForced = stream.isForced == true,
                isDefault = stream.isDefault == true,
                playerTrackId = trackId(subtitleStreams, streamIndex) ?: return@mapNotNull null,
                streamIndex = streamIndex,
                requiresPlaybackRestart = false
            )
        }

        return PlayerTrackState(
            availableAudioTracks = audioTracks,
            currentAudioTrack = audioTracks.firstOrNull {
                it.streamIndex == (selectedAudioStreamIndex ?: defaultAudioStreamIndex)
            } ?: audioTracks.firstOrNull(),
            availableSubtitleTracks = subtitleTracks,
            currentSubtitleTrack = subtitleTracks.firstOrNull {
                it.streamIndex == (selectedSubtitleStreamIndex ?: defaultSubtitleStreamIndex ?: -1)
            } ?: subtitleTracks.firstOrNull(),
            availableVideoTracks = emptyList()
        )
    }

    fun audioTrackId(mediaStreams: List<MediaStream>?, streamIndex: Int?): String? {
        if (streamIndex == null) return null
        return trackId(streams(mediaStreams, "Audio"), streamIndex)
    }

    fun subtitleTrackId(mediaStreams: List<MediaStream>?, streamIndex: Int?): String? {
        if (streamIndex == null || streamIndex < 0) return "no"
        return trackId(streams(mediaStreams, "Subtitle"), streamIndex)
    }

    fun selectAudioTrack(
        controller: MpvPlayerController?,
        track: AudioTrackInfo
    ): Int? {
        val streamIndex = track.streamIndex ?: return null
        val player = controller ?: return null
        player.selectAudioTrack(track.playerTrackId ?: return null)
        return streamIndex
    }

    fun selectSubtitleTrack(
        controller: MpvPlayerController?,
        track: SubtitleTrackInfo,
        externalSubtitleUrls: Map<Int, String>
    ): Int? {
        val streamIndex = track.streamIndex ?: return null
        val trackId = if (streamIndex < 0) "no" else track.playerTrackId ?: return null
        val player = controller ?: return null
        player.selectSubtitleTrack(
            trackId = trackId,
            externalUrl = externalSubtitleUrls[streamIndex]
        )
        return streamIndex
    }

    fun externalSubtitleUrls(
        playbackRequest: PlaybackRequest?,
        mediaStreams: List<MediaStream>
    ): Map<Int, String> {
        val request = playbackRequest ?: return emptyMap()
        val streamingUrl = request.url
        if (streamingUrl.isBlank()) return emptyMap()
        return mediaStreams
            .asSequence()
            .filter { it.type.equals("Subtitle", ignoreCase = true) && it.index != null }
            .filter { it.deliveryMethod.equals("External", ignoreCase = true) }
            .mapNotNull { stream ->
                val streamIndex = stream.index ?: return@mapNotNull null
                val deliveryUrl = stream.deliveryUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val parsedUrl = runCatching {
                    URI.create(streamingUrl).resolve(deliveryUrl).toString()
                }.getOrNull() ?: return@mapNotNull null
                streamIndex to request.authorizeRelatedUrl(parsedUrl)
            }
            .toMap()
    }

    fun isHdr(mediaStreams: List<MediaStream>?): Boolean {
        return mediaStreams.orEmpty().any { stream ->
            stream.type.equals("Video", ignoreCase = true) &&
                (
                    stream.colorTransfer?.contains("2084", ignoreCase = true) == true ||
                        stream.colorSpace?.contains("bt2020", ignoreCase = true) == true ||
                        stream.codec?.contains("dv", ignoreCase = true) == true
                    )
        }
    }

    private fun streams(mediaStreams: List<MediaStream>?, type: String): List<MediaStream> {
        return mediaStreams.orEmpty()
            .filter { it.type.equals(type, ignoreCase = true) && it.index != null }
            .distinctBy { it.index }
            .sortedBy { it.index ?: Int.MAX_VALUE }
    }

    private fun trackId(streams: List<MediaStream>, streamIndex: Int): String? {
        val trackIndex = streams.indexOfFirst { it.index == streamIndex }
        return if (trackIndex >= 0) (trackIndex + 1).toString() else null
    }
}