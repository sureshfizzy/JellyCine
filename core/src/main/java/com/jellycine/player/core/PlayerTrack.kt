package com.jellycine.player.core

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaSource
import com.jellycine.data.model.MediaStream
import java.util.Locale

data class PlayerTrackState(
    val availableAudioTracks: List<AudioTrackInfo>,
    val currentAudioTrack: AudioTrackInfo?,
    val availableSubtitleTracks: List<SubtitleTrackInfo>,
    val currentSubtitleTrack: SubtitleTrackInfo?,
    val availableVideoTracks: List<VideoTrackInfo>
)

@UnstableApi
object PlayerTrack {

    fun currentTrackState(
        exoPlayer: ExoPlayer,
        mediaStreams: List<MediaStream>?,
        isTranscoding: Boolean,
        selectedAudioStreamIndex: Int?,
        selectedSubtitleStreamIndex: Int?,
        defaultAudioStreamIndex: Int?,
        defaultSubtitleStreamIndex: Int?
    ): PlayerTrackState {
        val liveAudioTracks = PlayerUtils.getAvailableAudioTracks(exoPlayer)
        val liveSubtitleTracks = PlayerUtils.getAvailableSubtitleTracks(exoPlayer)
        val liveVideoTracks = PlayerUtils.getAvailableVideoTracks(exoPlayer)
        val currentLiveAudioTrack = PlayerUtils.getCurrentAudioTrack(exoPlayer)
        val currentLiveSubtitleTrack = PlayerUtils.getCurrentSubtitleTrack(exoPlayer)

        val audioStreams = indexedStreams(mediaStreams, streamType = "Audio")
        val subtitleStreams = indexedStreams(mediaStreams, streamType = "Subtitle")

        val apiNamedAudioTracks = liveAudioTracks.mapIndexed { index, track ->
            val stream = audioStreams.getOrNull(index)
            track.copy(
                label = stream?.displayTitleOrNull() ?: track.label,
                streamIndex = stream?.index
            )
        }
        val apiNamedCurrentAudioTrack = currentLiveAudioTrack?.let { selected ->
            apiNamedAudioTracks.firstOrNull { it.id == selected.id }
                ?: selected.copy(streamIndex = null)
        }

        var subtitleOrdinal = 0
        val apiNamedSubtitleTracks = liveSubtitleTracks.map { track ->
            if (track.streamIndex == -1 || track.id == "off") {
                track.copy(streamIndex = -1)
            } else {
                val stream = subtitleStreams.getOrNull(subtitleOrdinal++)
                track.copy(
                    label = stream?.displayTitleOrNull() ?: track.label,
                    streamIndex = stream?.index
                )
            }
        }
        val apiNamedCurrentSubtitleTrack = currentLiveSubtitleTrack?.let { selected ->
            apiNamedSubtitleTracks.firstOrNull { it.id == selected.id }
                ?: selected.copy(streamIndex = if (selected.id == "off") -1 else null)
        }

        val apiAudioTracks = audioStreams.mapNotNull { stream ->
            val streamIndex = stream.index ?: return@mapNotNull null
            AudioTrackInfo(
                id = "audio:$streamIndex",
                label = stream.displayTitleOrNull().orEmpty(),
                language = null,
                channelCount = 0,
                codec = null,
                streamIndex = streamIndex,
                requiresPlaybackRestart = true
            )
        }
        val apiSubtitleTracks = listOf(
            SubtitleTrackInfo(
                id = "off",
                label = "Off",
                language = null,
                isForced = false,
                isDefault = false,
                streamIndex = -1,
                requiresPlaybackRestart = true
            )
        ) + subtitleStreams.mapNotNull { stream ->
            val streamIndex = stream.index ?: return@mapNotNull null
            SubtitleTrackInfo(
                id = "subtitle:$streamIndex",
                label = stream.displayTitleOrNull().orEmpty(),
                language = null,
                isForced = false,
                isDefault = false,
                streamIndex = streamIndex,
                requiresPlaybackRestart = true
            )
        }

        val useApiAudioTracks = isTranscoding && apiAudioTracks.isNotEmpty()
        val useApiSubtitleTracks = isTranscoding && apiSubtitleTracks.size > 1

        val availableAudioTracks = if (useApiAudioTracks) {
            apiAudioTracks
        } else {
            apiNamedAudioTracks
        }
        val currentAudioTrack = if (useApiAudioTracks) {
            val targetStreamIndex = selectedAudioStreamIndex ?: defaultAudioStreamIndex
            targetStreamIndex?.let { target ->
                availableAudioTracks.firstOrNull { it.streamIndex == target }
            } ?: availableAudioTracks.firstOrNull()
        } else {
            apiNamedCurrentAudioTrack
        }

        val availableSubtitleTracks = if (useApiSubtitleTracks) {
            apiSubtitleTracks
        } else {
            apiNamedSubtitleTracks
        }
        val currentSubtitleTrack = if (useApiSubtitleTracks) {
            when (val targetStreamIndex = selectedSubtitleStreamIndex ?: defaultSubtitleStreamIndex ?: -1) {
                -1 -> availableSubtitleTracks.firstOrNull { it.streamIndex == -1 }
                else -> availableSubtitleTracks.firstOrNull { it.streamIndex == targetStreamIndex }
            } ?: availableSubtitleTracks.firstOrNull { it.streamIndex == -1 }
        } else {
            apiNamedCurrentSubtitleTrack
        }

        return PlayerTrackState(
            availableAudioTracks = availableAudioTracks,
            currentAudioTrack = currentAudioTrack,
            availableSubtitleTracks = availableSubtitleTracks,
            currentSubtitleTrack = currentSubtitleTrack,
            availableVideoTracks = liveVideoTracks
        )
    }

    fun resolveApiMediaStreams(
        itemDetails: BaseItemDto?,
        playbackMediaSource: MediaSource?
    ): List<MediaStream> {
        val playbackStreams = playbackMediaSource?.mediaStreams.orEmpty()
        val sourceStreams = itemDetails
            ?.mediaSources
            .orEmpty()
            .firstOrNull { source ->
                !playbackMediaSource?.id.isNullOrBlank() && source.id == playbackMediaSource?.id
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

        return (itemStreams + playbackStreams)
            .distinctBy { "${it.type.orEmpty().lowercase(Locale.US)}:${it.index ?: "na"}" }
    }

    private fun indexedStreams(
        mediaStreams: List<MediaStream>?,
        streamType: String
    ) = mediaStreams
        .orEmpty()
        .filter { it.type == streamType && it.index != null }
        .distinctBy { it.index }
        .sortedBy { it.index ?: Int.MAX_VALUE }

}
