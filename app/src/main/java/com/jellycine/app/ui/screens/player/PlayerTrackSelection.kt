package com.jellycine.app.ui.screens.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.jellycine.data.model.MediaStream
import com.jellycine.player.core.AudioTrackInfo
import com.jellycine.player.core.PlayerUtils
import com.jellycine.player.core.SubtitleTrackInfo
import com.jellycine.player.preferences.PlayerPreferences

internal class PlayerTrackSelection {

    private var pendingPreferredAudioStreamIndex: Int? = null
    private var pendingPreferredSubtitleStreamIndex: Int? = null
    private var hasAppliedInitialTrackPreferences: Boolean = false

    fun resetPendingSelections(
        preferredAudioStreamIndex: Int?,
        preferredSubtitleStreamIndex: Int?
    ) {
        pendingPreferredAudioStreamIndex = preferredAudioStreamIndex
        pendingPreferredSubtitleStreamIndex = preferredSubtitleStreamIndex
        hasAppliedInitialTrackPreferences = false
    }

    fun clear() {
        pendingPreferredAudioStreamIndex = null
        pendingPreferredSubtitleStreamIndex = null
        hasAppliedInitialTrackPreferences = false
    }

    fun markManualTrackSelection() {
        pendingPreferredAudioStreamIndex = null
        pendingPreferredSubtitleStreamIndex = null
        hasAppliedInitialTrackPreferences = true
    }

    fun applyInitialSelections(
        player: ExoPlayer,
        mediaStreams: List<MediaStream>?
    ): Boolean {
        if (hasAppliedInitialTrackPreferences) return false

        val preferredAudioIndex = pendingPreferredAudioStreamIndex
        val preferredSubtitleIndex = pendingPreferredSubtitleStreamIndex

        if (preferredAudioIndex == null && preferredSubtitleIndex == null) {
            hasAppliedInitialTrackPreferences = true
            return false
        }

        val audioTracks = PlayerUtils.getAvailableAudioTracks(player)
        val subtitleTracks = PlayerUtils.getAvailableSubtitleTracks(player)

        var audioHandled = preferredAudioIndex == null
        var subtitleHandled = preferredSubtitleIndex == null
        var appliedAnySelection = false

        if (!audioHandled) {
            val audioTrackId = audioTrackIdFor(
                preferredStreamIndex = preferredAudioIndex,
                tracks = audioTracks,
                mediaStreams = mediaStreams
            )
            if (audioTrackId != null) {
                PlayerUtils.selectAudioTrack(player, audioTrackId)
                audioHandled = true
                appliedAnySelection = true
            }
        }

        if (!subtitleHandled) {
            if (preferredSubtitleIndex == -1) {
                PlayerUtils.selectSubtitleTrack(player, "off")
                subtitleHandled = true
                appliedAnySelection = true
            } else {
                val subtitleTrackId = subtitleTrackIdFor(
                    preferredStreamIndex = preferredSubtitleIndex,
                    tracks = subtitleTracks,
                    mediaStreams = mediaStreams
                )
                if (subtitleTrackId != null) {
                    PlayerUtils.selectSubtitleTrack(player, subtitleTrackId)
                    subtitleHandled = true
                    appliedAnySelection = true
                }
            }
        }

        if (audioHandled && subtitleHandled) {
            hasAppliedInitialTrackPreferences = true
            pendingPreferredAudioStreamIndex = null
            pendingPreferredSubtitleStreamIndex = null
        }

        return appliedAnySelection
    }

    fun syncPreferredIndexesFromCurrentTracks(
        context: Context?,
        mediaId: String?,
        availableAudioTracks: List<AudioTrackInfo>,
        currentAudioTrack: AudioTrackInfo?,
        availableSubtitleTracks: List<SubtitleTrackInfo>,
        currentSubtitleTrack: SubtitleTrackInfo?,
        mediaStreams: List<MediaStream>?,
        currentPublished: PreferredStreamIndexes
    ): PreferredStreamIndexes {
        if (context == null || mediaId.isNullOrBlank() || hasPendingInitialSelection()) {
            return currentPublished
        }

        val preferences = PlayerPreferences(context)
        val resolvedAudioStreamIndex = resolveAudioStreamIndexForCurrentTrack(
            selectedTrack = currentAudioTrack,
            availableTracks = availableAudioTracks,
            mediaStreams = mediaStreams
        )
        val resolvedSubtitleStreamIndex = resolveSubtitleStreamIndexForCurrentTrack(
            selectedTrack = currentSubtitleTrack,
            availableTracks = availableSubtitleTracks,
            mediaStreams = mediaStreams
        )

        var audioToPublish = currentPublished.audioStreamIndex
        var subtitleToPublish = currentPublished.subtitleStreamIndex

        if (currentAudioTrack != null && resolvedAudioStreamIndex != null) {
            preferences.setPreferredAudioStreamIndex(mediaId, resolvedAudioStreamIndex)
            audioToPublish = resolvedAudioStreamIndex
        }
        if (currentSubtitleTrack != null && resolvedSubtitleStreamIndex != null) {
            preferences.setPreferredSubtitleStreamIndex(mediaId, resolvedSubtitleStreamIndex)
            subtitleToPublish = resolvedSubtitleStreamIndex
        }

        return if (
            audioToPublish != currentPublished.audioStreamIndex ||
            subtitleToPublish != currentPublished.subtitleStreamIndex
        ) {
            PreferredStreamIndexes(
                audioStreamIndex = audioToPublish,
                subtitleStreamIndex = subtitleToPublish
            )
        } else {
            currentPublished
        }
    }

    private fun hasPendingInitialSelection(): Boolean {
        return !hasAppliedInitialTrackPreferences &&
            (pendingPreferredAudioStreamIndex != null || pendingPreferredSubtitleStreamIndex != null)
    }

    private fun resolveAudioStreamIndexForCurrentTrack(
        selectedTrack: AudioTrackInfo?,
        availableTracks: List<AudioTrackInfo>,
        mediaStreams: List<MediaStream>?
    ): Int? {
        if (selectedTrack == null) return null
        val audioStreams = mediaStreams
            .orEmpty()
            .filter { it.type == "Audio" && it.index != null }
            .sortedBy { it.index ?: Int.MAX_VALUE }
        if (audioStreams.isEmpty()) return null
        val trackOrdinal = availableTracks.indexOfFirst { it.id == selectedTrack.id }
        if (trackOrdinal < 0 || trackOrdinal >= audioStreams.size) return null
        return audioStreams[trackOrdinal].index
    }

    private fun resolveSubtitleStreamIndexForCurrentTrack(
        selectedTrack: SubtitleTrackInfo?,
        availableTracks: List<SubtitleTrackInfo>,
        mediaStreams: List<MediaStream>?
    ): Int? {
        if (selectedTrack == null) return null
        if (selectedTrack.id == "off") return -1

        val subtitleStreams = mediaStreams
            .orEmpty()
            .filter { it.type == "Subtitle" && it.index != null }
            .sortedBy { it.index ?: Int.MAX_VALUE }
        if (subtitleStreams.isEmpty()) return null
        val selectableTracks = availableTracks.filterNot { it.id == "off" }
        val trackOrdinal = selectableTracks.indexOfFirst { it.id == selectedTrack.id }
        if (trackOrdinal < 0 || trackOrdinal >= subtitleStreams.size) return null
        return subtitleStreams[trackOrdinal].index
    }

    private fun audioTrackIdFor(
        preferredStreamIndex: Int?,
        tracks: List<AudioTrackInfo>,
        mediaStreams: List<MediaStream>?
    ): String? {
        if (tracks.isEmpty()) return null
        val sortedAudioStreams = mediaStreams
            .orEmpty()
            .filter { it.type == "Audio" }
            .sortedBy { it.index ?: Int.MAX_VALUE }
        val ordinal = preferredStreamIndex?.let { target ->
            sortedAudioStreams.indexOfFirst { it.index == target }
        } ?: -1
        if (ordinal >= 0 && ordinal < tracks.size) {
            return tracks[ordinal].id
        }
        return null
    }

    private fun subtitleTrackIdFor(
        preferredStreamIndex: Int?,
        tracks: List<SubtitleTrackInfo>,
        mediaStreams: List<MediaStream>?
    ): String? {
        val subtitleTracks = tracks.filterNot { it.id == "off" }
        if (subtitleTracks.isEmpty()) return null
        val sortedSubtitleStreams = mediaStreams
            .orEmpty()
            .filter { it.type == "Subtitle" }
            .sortedBy { it.index ?: Int.MAX_VALUE }
        val ordinal = preferredStreamIndex?.let { target ->
            sortedSubtitleStreams.indexOfFirst { it.index == target }
        } ?: -1
        if (ordinal >= 0 && ordinal < subtitleTracks.size) {
            return subtitleTracks[ordinal].id
        }
        return null
    }
}
