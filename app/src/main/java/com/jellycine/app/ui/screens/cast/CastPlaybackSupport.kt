package com.jellycine.app.ui.screens.cast

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jellycine.app.cast.CastPlaybackState
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaStream
import com.jellycine.data.repository.MediaRepository
import com.jellycine.player.preferences.PlayerPreferences
import java.util.Locale

internal data class CastPlaybackData(
    val item: BaseItemDto?,
    val artworkUrl: String?,
    val streams: List<MediaStream>,
    val selectedAudioStreamIndex: Int?,
    val selectedSubtitleStreamIndex: Int?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CastPlayback(
    castState: CastPlaybackState,
    streams: List<MediaStream>,
    fallbackArtworkUrl: String?,
    selectedAudioStreamIndex: Int?,
    selectedSubtitleStreamIndex: Int?,
    isTrackSelectionUpdating: Boolean,
    onDismissRequest: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onStopCasting: () -> Unit,
    onDisconnect: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onTrackSelectionChanged: (Int?, Int?) -> Unit
) {
    val audioTrackOptions = buildCastTrackOptions(
        streams = streams,
        streamType = "Audio"
    )
    val subtitleTrackOptions = buildCastTrackOptions(
        streams = streams,
        streamType = "Subtitle",
        includeOffOption = true
    )
    val activeAudioTrackIndex = selectedCastTrackIndex(
        selectedStreamIndex = selectedAudioStreamIndex,
        options = audioTrackOptions
    )
    val activeSubtitleTrackIndex = selectedCastTrackIndex(
        selectedStreamIndex = selectedSubtitleStreamIndex,
        options = subtitleTrackOptions
    )
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF0E131A),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
        dragHandle = null
    ) {
        CastingDisplayScreen(
            castState = castState,
            onBackPressed = onDismissRequest,
            onTogglePlayPause = onTogglePlayPause,
            onStopCasting = onStopCasting,
            onDisconnect = onDisconnect,
            onSeekTo = onSeekTo,
            fallbackArtworkUrl = fallbackArtworkUrl,
            audioTrackOptions = audioTrackOptions,
            selectedAudioTrackIndex = activeAudioTrackIndex,
            onAudioTrackSelected = { streamIndex ->
                if (streamIndex != activeAudioTrackIndex) {
                    onTrackSelectionChanged(streamIndex, activeSubtitleTrackIndex)
                }
            },
            subtitleTrackOptions = subtitleTrackOptions,
            selectedSubtitleTrackIndex = activeSubtitleTrackIndex,
            onSubtitleTrackSelected = { streamIndex ->
                if (streamIndex != activeSubtitleTrackIndex) {
                    onTrackSelectionChanged(activeAudioTrackIndex, streamIndex)
                }
            },
            isTrackSelectionUpdating = isTrackSelectionUpdating
        )
        Spacer(modifier = Modifier.height(12.dp))
    }
}

internal suspend fun loadCastPlaybackData(
    mediaRepository: MediaRepository,
    playerPreferences: PlayerPreferences,
    itemId: String,
    activeItem: BaseItemDto? = null
): CastPlaybackData {
    val item = activeItem ?: mediaRepository.getItemById(itemId).getOrNull()
    val artworkUrl = activeCastArtworkUrl(
        mediaRepository = mediaRepository,
        item = item,
        fallbackItemId = itemId
    )

    val playbackSource = mediaRepository
        .getPlaybackInfo(itemId = itemId)
        .getOrNull()
        ?.mediaSources
        ?.firstOrNull()
    val playbackStreams = playbackSource?.mediaStreams.orEmpty()
    val itemStreams = item
        ?.mediaSources
        .orEmpty()
        .flatMap { it.mediaStreams.orEmpty() }
        .ifEmpty { item?.mediaStreams.orEmpty() }
    val streams = (playbackStreams + itemStreams)
        .distinctBy { "${it.type.orEmpty().lowercase(Locale.US)}:${it.index ?: "na"}" }

    val selectedAudioStreamIndex = playerPreferences.getPreferredAudioStreamIndex(itemId)
        ?: playbackSource?.defaultAudioStreamIndex
    val selectedSubtitleStreamIndex = playerPreferences.getPreferredSubtitleStreamIndex(itemId)
        ?: playbackSource?.defaultSubtitleStreamIndex
        ?: -1

    return CastPlaybackData(
        item = item,
        artworkUrl = artworkUrl,
        streams = streams,
        selectedAudioStreamIndex = selectedAudioStreamIndex,
        selectedSubtitleStreamIndex = selectedSubtitleStreamIndex
    )
}

private fun selectedCastTrackIndex(
    selectedStreamIndex: Int?,
    options: List<CastTrackOption>
): Int? {
    return selectedStreamIndex
        ?.takeIf { selected -> options.any { option -> option.streamIndex == selected } }
        ?: options.firstOrNull()?.streamIndex
}

private fun buildCastTrackOptions(
    streams: List<MediaStream>,
    streamType: String,
    includeOffOption: Boolean = false
): List<CastTrackOption> {
    val labelPrefix = if (streamType.equals("Audio", ignoreCase = true)) "Audio" else "Subtitle"
    val matchingStreams = streams
        .asSequence()
        .filter { stream -> stream.type == streamType && stream.index != null }
        .distinctBy { stream -> stream.index }
        .sortedBy { stream -> stream.index }
        .toList()

    if (matchingStreams.isEmpty()) {
        return if (includeOffOption) listOf(CastTrackOption(label = "Off", streamIndex = -1)) else emptyList()
    }

    val labels = dedupeLabels(
        matchingStreams.mapIndexed { index, stream ->
            stream.displayTitle?.takeIf { it.isNotBlank() }
                ?: stream.title?.takeIf { it.isNotBlank() }
                ?: stream.language?.uppercase(Locale.US)
                ?: "$labelPrefix ${index + 1}"
        }
    )

    val options = matchingStreams.mapIndexed { index, stream ->
        CastTrackOption(
            label = labels.getOrElse(index) { "$labelPrefix ${index + 1}" },
            streamIndex = stream.index
        )
    }

    return if (includeOffOption) {
        listOf(CastTrackOption(label = "Off", streamIndex = -1)) + options
    } else {
        options
    }
}

internal suspend fun activeCastArtworkUrl(
    mediaRepository: MediaRepository,
    item: BaseItemDto?,
    fallbackItemId: String
): String? {
    suspend fun image(
        itemId: String?,
        imageType: String,
        width: Int,
        height: Int
    ): String? {
        if (itemId.isNullOrBlank()) return null
        return mediaRepository.getImageUrlString(
            itemId = itemId,
            imageType = imageType,
            width = width,
            height = height,
            quality = 90,
            enableImageEnhancers = false
        )
    }

    val itemId = item?.id?.takeIf { it.isNotBlank() } ?: fallbackItemId
    val seriesId = item?.seriesId?.takeIf { it.isNotBlank() }
    val isEpisode = item?.type.equals("Episode", ignoreCase = true)

    val candidates = if (isEpisode) {
        listOf(
            Triple(seriesId, "Primary", Pair(720, 1080)),
            Triple(seriesId, "Backdrop", Pair(1280, 720)),
            Triple(itemId, "Primary", Pair(720, 1080)),
            Triple(itemId, "Backdrop", Pair(1280, 720)),
            Triple(itemId, "Thumb", Pair(1280, 720))
        )
    } else {
        listOf(
            Triple(itemId, "Primary", Pair(720, 1080)),
            Triple(itemId, "Backdrop", Pair(1280, 720)),
            Triple(itemId, "Thumb", Pair(1280, 720)),
            Triple(seriesId, "Primary", Pair(720, 1080)),
            Triple(seriesId, "Backdrop", Pair(1280, 720))
        )
    }

    for ((candidateId, imageType, size) in candidates) {
        val (width, height) = size
        val imageUrl = image(
            itemId = candidateId,
            imageType = imageType,
            width = width,
            height = height
        )
        if (!imageUrl.isNullOrBlank()) return imageUrl
    }

    return null
}

private fun dedupeLabels(labels: List<String>): List<String> {
    val counts = mutableMapOf<String, Int>()
    return labels.map { label ->
        val seen = (counts[label] ?: 0) + 1
        counts[label] = seen
        if (seen == 1) label else "$label ($seen)"
    }
}
