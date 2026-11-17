package com.jellycine.app.ui.screens.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.download.DownloadRepositoryProvider
import com.jellycine.app.ui.components.common.DownloadActionMenu
import com.jellycine.app.ui.components.common.DownloadContent
import com.jellycine.app.ui.components.common.canResumeDownloads
import com.jellycine.app.ui.components.common.hasActiveDownloads
import com.jellycine.app.ui.components.common.pausableItemIds
import com.jellycine.app.ui.components.common.rememberDownloadPanelProgress
import com.jellycine.app.ui.components.common.rememberDownloadPanelState
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.BatchDownloadCandidate
import com.jellycine.data.model.BatchDownloadEstimate
import com.jellycine.shared.R
import kotlinx.coroutines.launch

@Composable
internal fun SeasonDownloadAction(
    seasonId: String,
    episodes: List<BaseItemDto>
) {
    val context = LocalContext.current
    val downloadRepository = remember { DownloadRepositoryProvider.getInstance(context) }
    val coroutineScope = rememberCoroutineScope()
    var seasonQueueInProgress by remember(seasonId) { mutableStateOf(false) }
    var downloadErrorDialogMessage by remember(seasonId) { mutableStateOf<String?>(null) }
    var storageSelectionDialogState by remember(seasonId) {
        mutableStateOf<SeasonEpisodeSelectionDialogState?>(null)
    }
    val trackedDownloads by downloadRepository.observeTrackedDownloads()
        .collectAsState(initial = emptyList())
    val seasonEpisodeIds = remember(episodes) { episodes.mapNotNull { it.id }.toSet() }
    val seasonDownloadEntries = remember(trackedDownloads, seasonEpisodeIds) {
        trackedDownloads.filter { seasonEpisodeIds.contains(it.itemId) }
    }
    val seasonDownload = rememberDownloadPanelState(
        entries = seasonDownloadEntries,
        expectedCount = seasonEpisodeIds.size
    )
    val hasActiveSeasonDownloads = seasonDownload.hasActiveDownloads
    val canResumeSeasonDownloads = seasonDownload.canResumeDownloads
    var seasonDownloadActionMenu by remember(
        seasonId,
        seasonDownload.status,
        seasonDownload.activeItemIds.size,
        seasonDownload.pausedItemIds.size
    ) { mutableStateOf(false) }
    val animatedSeasonDownloadProgress = rememberDownloadPanelProgress(
        panelState = seasonDownload,
        label = "season_download_progress"
    )

    OutlinedButton(
        onClick = {
            when {
                hasActiveSeasonDownloads -> seasonDownloadActionMenu = true
                else -> {
                    coroutineScope.launch {
                        seasonQueueInProgress = true
                        try {
                            val estimateResult = downloadRepository.buildEpisodeBatchEstimate(episodes)
                            estimateResult.fold(
                                onSuccess = { estimate ->
                                    storageSelectionDialogState =
                                        SeasonEpisodeSelectionDialogState.fromEstimate(estimate)
                                },
                                onFailure = { throwable ->
                                    downloadErrorDialogMessage = throwable.message
                                        ?: "Failed to prepare season download."
                                }
                            )
                        } finally {
                            seasonQueueInProgress = false
                        }
                    }
                }
            }
        },
        enabled = episodes.isNotEmpty() && (!seasonQueueInProgress || hasActiveSeasonDownloads),
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(23.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color(0xFF1F1F24),
            contentColor = Color.White
        )
    ) {
        DownloadContent(
            panelState = seasonDownload,
            isQueueing = seasonQueueInProgress,
            supportsCompleted = true,
            progress = animatedSeasonDownloadProgress,
            idleLabelRes = R.string.downloads_action_download,
            fontSize = 13.sp,
            iconSize = 18.dp,
            progressSize = 16.dp
        )
    }

    DownloadActionMenu(
        expanded = seasonDownloadActionMenu,
        canResume = canResumeSeasonDownloads,
        hasActiveDownloads = hasActiveSeasonDownloads,
        onDismissRequest = { seasonDownloadActionMenu = false },
        onPauseResume = {
            seasonDownloadActionMenu = false
            if (canResumeSeasonDownloads) {
                seasonDownload.pausedItemIds.forEach(downloadRepository::resumeDownload)
            } else {
                seasonDownload.pausableItemIds.forEach(downloadRepository::pauseDownload)
            }
        },
        onCancel = {
            seasonDownloadActionMenu = false
            seasonDownload.activeItemIds.forEach(downloadRepository::cancelDownload)
        }
    )

    storageSelectionDialogState?.let { dialogState ->
        DownloadDialog(
            title = "Choose Episodes",
            subtitle = "Pick episodes to download. Selected total must fit available storage.",
            availableBytes = dialogState.availableBytes,
            options = dialogState.options,
            initialSelection = dialogState.options.map { it.id }.toSet(),
            confirmLabel = "Download Episodes",
            onDismiss = { storageSelectionDialogState = null },
            onConfirm = { selectedIds ->
                val selectedEpisodes = dialogState.options
                    .filter { selectedIds.contains(it.id) }
                    .mapNotNull { option -> dialogState.episodesById[option.id] }
                storageSelectionDialogState = null
                coroutineScope.launch {
                    seasonQueueInProgress = true
                    try {
                        downloadRepository.enqueueEpisodeDownloads(selectedEpisodes).onFailure { throwable ->
                            downloadErrorDialogMessage = throwable.message
                                ?: "Failed to queue selected episodes."
                        }
                    } finally {
                        seasonQueueInProgress = false
                    }
                }
            }
        )
    }

    downloadErrorDialogMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { downloadErrorDialogMessage = null },
            containerColor = Color(0xFF1A1C22),
            titleContentColor = Color.White,
            textContentColor = Color.White.copy(alpha = 0.92f),
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Download Failed",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 20.sp
                )
            },
            text = { Text(text = message) },
            confirmButton = {
                TextButton(
                    onClick = { downloadErrorDialogMessage = null },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = Color(0xFF22D3EE)
                    )
                ) {
                    Text("OK", fontWeight = FontWeight.SemiBold)
                }
            }
        )
    }
}

private data class SeasonEpisodeSelectionDialogState(
    val availableBytes: Long,
    val options: List<StorageSelectionOption>,
    val episodesById: Map<String, BaseItemDto>
) {
    companion object {
        fun fromEstimate(estimate: BatchDownloadEstimate): SeasonEpisodeSelectionDialogState {
            val candidates = estimate.candidates
                .filter { !it.item.id.isNullOrBlank() }
                .sortedWith(
                    compareBy<BatchDownloadCandidate>(
                        { it.item.parentIndexNumber ?: Int.MAX_VALUE },
                        { it.item.indexNumber ?: Int.MAX_VALUE },
                        { it.item.name.orEmpty() }
                    )
                )

            val options = candidates.map { candidate ->
                val episode = candidate.item
                val episodeId = episode.id.orEmpty()
                val episodeCode = buildString {
                    episode.parentIndexNumber?.let { season ->
                        episode.indexNumber?.let { episodeNumber ->
                            append("S$season:E$episodeNumber")
                        }
                    }
                }
                val episodeSubtitle = buildString {
                    if (episodeCode.isNotBlank()) append(episodeCode)
                    if (!episode.seriesName.isNullOrBlank()) {
                        if (isNotBlank()) append("  |  ")
                        append(episode.seriesName)
                    }
                }.ifBlank { null }

                StorageSelectionOption(
                    id = episodeId,
                    title = episode.name?.takeIf { it.isNotBlank() } ?: "Episode",
                    subtitle = episodeSubtitle,
                    requiredBytes = candidate.remainingBytes ?: 0L
                )
            }

            return SeasonEpisodeSelectionDialogState(
                availableBytes = estimate.availableBytes,
                options = options,
                episodesById = candidates.associate { it.item.id.orEmpty() to it.item }
            )
        }
    }
}

