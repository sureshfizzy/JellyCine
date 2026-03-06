package com.jellycine.app.ui.screens.player

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import com.jellycine.data.model.MediaStream
import com.jellycine.player.core.AudioTrackInfo
import com.jellycine.player.core.PlayerTrack
import com.jellycine.player.core.PlayerUtils
import com.jellycine.player.core.SubtitleTrackInfo
import com.jellycine.player.core.displayTitleOrNull
import com.jellycine.player.preferences.PlayerPreferences
import java.util.Locale

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
        mediaStreams: List<MediaStream>?,
        isTranscoding: Boolean
    ): Boolean {
        if (hasAppliedInitialTrackPreferences) return false

        val preferredAudioIndex = pendingPreferredAudioStreamIndex
        val preferredSubtitleIndex = pendingPreferredSubtitleStreamIndex

        if (preferredAudioIndex == null && preferredSubtitleIndex == null) {
            hasAppliedInitialTrackPreferences = true
            return false
        }

        val resolvedTracks = PlayerTrack.currentTrackState(
            exoPlayer = player,
            mediaStreams = mediaStreams,
            isTranscoding = false,
            selectedAudioStreamIndex = null,
            selectedSubtitleStreamIndex = null,
            defaultAudioStreamIndex = null,
            defaultSubtitleStreamIndex = null
        )

        var audioHandled = preferredAudioIndex == null || isTranscoding
        var subtitleHandled = preferredSubtitleIndex == null
        var appliedAnySelection = false

        if (!audioHandled) {
            val audioTrack = resolvedTracks.availableAudioTracks.firstOrNull {
                it.streamIndex == preferredAudioIndex && !it.playerTrackId.isNullOrBlank()
            }
            val playerTrackId = audioTrack?.playerTrackId
            if (playerTrackId != null) {
                PlayerUtils.selectAudioTrack(player, playerTrackId)
                audioHandled = true
                appliedAnySelection = true
            }
        }

        if (!subtitleHandled) {
            if (preferredSubtitleIndex == -1) {
                PlayerUtils.selectSubtitleTrack(player, "off")
                subtitleHandled = true
                appliedAnySelection = true
            } else if (isTranscoding) {
                val targetSubtitleIndex = preferredSubtitleIndex ?: return appliedAnySelection
                transcodingSubtitleTrackId(
                    player = player,
                    mediaStreams = mediaStreams,
                    preferredSubtitleIndex = targetSubtitleIndex
                )?.let { playerTrackId ->
                    PlayerUtils.selectSubtitleTrack(player, playerTrackId)
                    appliedAnySelection = true
                }
                subtitleHandled = true
            } else {
                val subtitleTrack = resolvedTracks.availableSubtitleTracks.firstOrNull {
                    it.streamIndex == preferredSubtitleIndex && !it.playerTrackId.isNullOrBlank()
                }
                val playerTrackId = subtitleTrack?.playerTrackId
                if (playerTrackId != null) {
                    PlayerUtils.selectSubtitleTrack(player, playerTrackId)
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

    private fun transcodingSubtitleTrackId(
        player: ExoPlayer,
        mediaStreams: List<MediaStream>?,
        preferredSubtitleIndex: Int
    ): String? {
        val targetStream = mediaStreams
            .orEmpty()
            .firstOrNull { stream ->
                stream.type == "Subtitle" && stream.index == preferredSubtitleIndex
            }
        val targetLanguage = targetStream
            ?.language
            ?.takeIf { it.isNotBlank() }
            ?.lowercase(Locale.US)
            ?.take(2)
        val targetLabel = targetStream?.displayTitleOrNull()

        val liveSubtitleTracks = PlayerUtils.getAvailableSubtitleTracks(player)
            .filter { it.id != "off" && !it.playerTrackId.isNullOrBlank() }

        return liveSubtitleTracks.firstOrNull { track ->
            !targetLanguage.isNullOrBlank() &&
                track.language
                    ?.lowercase(Locale.US)
                    ?.take(2) == targetLanguage
        }?.playerTrackId
            ?: liveSubtitleTracks.firstOrNull { track ->
                !targetLabel.isNullOrBlank() &&
                    track.label.equals(targetLabel, ignoreCase = true) &&
                    !track.language.isNullOrBlank()
            }?.playerTrackId
            ?: liveSubtitleTracks.lastOrNull { track ->
                !track.language.isNullOrBlank() || track.label.isNotBlank()
            }?.playerTrackId
            ?: liveSubtitleTracks.lastOrNull()?.playerTrackId
    }

    fun syncPreferredIndexesFromCurrentTracks(
        context: Context?,
        mediaId: String?,
        currentAudioTrack: AudioTrackInfo?,
        currentSubtitleTrack: SubtitleTrackInfo?,
        currentPublished: PreferredStreamIndexes
    ): PreferredStreamIndexes {
        if (context == null || mediaId.isNullOrBlank() || hasPendingInitialSelection()) {
            return currentPublished
        }

        val preferences = PlayerPreferences(context)
        val resolvedAudioStreamIndex = currentAudioTrack?.streamIndex
        val resolvedSubtitleStreamIndex = currentSubtitleTrack?.streamIndex

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
}
