package com.jellycine.app.ui.screens.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.cast.CastController
import com.jellycine.app.download.DownloadRepositoryProvider
import com.jellycine.app.ui.components.common.DownloadActionMenu
import com.jellycine.app.ui.components.common.DownloadContent
import com.jellycine.app.ui.components.common.ScreenCastButton
import com.jellycine.app.ui.components.common.canResumeDownloads
import com.jellycine.app.ui.components.common.hasActiveDownloads
import com.jellycine.app.ui.components.common.isPausedDownloadState
import com.jellycine.app.ui.components.common.pausableItemIds
import com.jellycine.app.ui.components.common.rememberDownloadPanelProgress
import com.jellycine.app.ui.components.common.rememberDownloadPanelState
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.BaseItemPerson
import com.jellycine.data.model.DownloadStatus
import com.jellycine.data.model.ItemDownloadState
import com.jellycine.data.model.MediaStream
import com.jellycine.data.model.SeerrItemIds
import com.jellycine.data.model.SeerrRecommendationTitle
import com.jellycine.data.model.SeerrRequestState
import com.jellycine.data.repository.AuthRepository
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.SeerrRepository
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.player.preferences.TranscodeProfile
import com.jellycine.preferences.Preferences
import com.jellycine.shared.R
import com.jellycine.ui.components.common.DetailDownloadActionButton
import com.jellycine.ui.components.common.DetailDownloadActionState
import com.jellycine.ui.components.common.SeerrRequestButtonRow
import com.jellycine.ui.components.common.SubtitleSearchActionButton
import com.jellycine.ui.components.common.activeDetailMediaSources
import com.jellycine.ui.screens.detail.DetailController
import com.jellycine.ui.screens.detail.DetailExtras
import com.jellycine.ui.screens.detail.DetailPlatform
import com.jellycine.ui.screens.detail.DetailTrackPreferences
import com.jellycine.ui.screens.detail.SeasonsSection
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** Android implementation of the detail screen hooks: Seerr, downloads, cast, player. */
internal class DetailScreenPlatform(
    private val context: Context,
    private val seerrRepository: SeerrRepository,
    private val playerPreferences: PlayerPreferences,
    private val preferences: Preferences,
    private val mediaRepository: MediaRepository,
    private val authRepository: AuthRepository
) : DetailPlatform {

    override val trackPreferences: DetailTrackPreferences = object : DetailTrackPreferences {
        override fun getPreferredAudioStreamIndex(itemId: String) =
            playerPreferences.getPreferredAudioStreamIndex(itemId)

        override fun getPreferredSubtitleStreamIndex(itemId: String) =
            playerPreferences.getPreferredSubtitleStreamIndex(itemId)

        override fun setPreferredAudioStreamIndex(itemId: String, streamIndex: Int?) =
            playerPreferences.setPreferredAudioStreamIndex(itemId, streamIndex)

        override fun setPreferredSubtitleStreamIndex(itemId: String, streamIndex: Int?) =
            playerPreferences.setPreferredSubtitleStreamIndex(itemId, streamIndex)
    }

    override val seasonDownloadAction: (@Composable (String, List<BaseItemDto>) -> Unit) =
        { seasonId, episodes -> SeasonDownloadAction(seasonId, episodes) }

    override suspend fun loadItem(
        itemId: String,
        activeServerId: String?,
        mediaRepository: MediaRepository
    ): Result<BaseItemDto> = loadDetailItem(
        itemId = itemId,
        activeServerId = activeServerId,
        mediaRepository = mediaRepository,
        seerrRepository = seerrRepository
    )

    override suspend fun loadLogoImage(
        item: BaseItemDto,
        activeServerId: String?,
        mediaRepository: MediaRepository
    ): String? = detailLogoImage(
        item = item,
        activeServerId = activeServerId,
        isSeerDetail = SeerrItemIds.isDetailId(item.id),
        mediaRepository = mediaRepository,
        seerrRepository = seerrRepository
    )

    override suspend fun loadSeerrPerson(
        tmdbId: String,
        activeServerId: String?
    ): Pair<BaseItemDto?, List<SeerrRecommendationTitle>>? {
        val scopeId = activeServerId?.takeIf { it.isNotBlank() } ?: return null
        return coroutineScope {
            val person = async { seerrRepository.getPersonDetails(scopeId, tmdbId) }
            val directed = async {
                com.jellycine.app.ui.components.common.fetchSeerDirectedTitlesForTmdbPerson(
                    personTmdbId = tmdbId,
                    activeServerId = activeServerId,
                    seerrRepository = seerrRepository
                )
            }
            person.await().getOrNull() to directed.await()
        }
    }

    @Composable
    override fun rememberExtras(
        item: BaseItemDto,
        mediaStreams: List<MediaStream>,
        onMediaStreamsRefreshed: (List<MediaStream>) -> Unit,
        onRemoteTrailerClick: (String, String?) -> Unit,
        onCastClick: () -> Unit
    ): DetailExtras {
        val coroutineScope = rememberCoroutineScope()
        val downloadRepository = remember { DownloadRepositoryProvider.getInstance(context) }
        val activeServerId by authRepository.getActiveServerId()
            .collectAsState(initial = authRepository.getActiveSessionSnapshot().activeServerId)
        val isSeerDetail = SeerrItemIds.isDetailId(item.id)
        val seerrConnected = remember(activeServerId) {
            !activeServerId.isNullOrBlank() &&
                seerrRepository.getSavedConnectionInfo(activeServerId)?.isVerified == true
        }
        val ui = remember(item.id) { DetailExtrasUiState() }
        val itemDownloadStateFlow =
            if (isSeerDetail) null else item.id?.let { downloadRepository.observeItemDownload(it) }
        val itemDownloadState by (itemDownloadStateFlow?.collectAsState()
            ?: remember(item.id) { mutableStateOf(ItemDownloadState()) })
        val pausedDownloadMessage = stringResource(R.string.downloads_status_paused)
        val isPausedDownload = isPausedDownloadState(itemDownloadState, pausedDownloadMessage)
        val trackedDownloads by downloadRepository.observeTrackedDownloads()
            .collectAsState(initial = emptyList())
        val seriesDownloadEntries = remember(item.id, item.type, trackedDownloads) {
            val seriesId = item.id
            if (isSeerDetail || item.type != "Series" || seriesId.isNullOrBlank()) {
                emptyList()
            } else {
                trackedDownloads.filter { it.item?.seriesId == seriesId }
            }
        }
        val seriesDownload = rememberDownloadPanelState(entries = seriesDownloadEntries)
        val animatedDownloadProgress by animateFloatAsState(
            targetValue = when (itemDownloadState.status) {
                DownloadStatus.QUEUED -> itemDownloadState.progress.coerceIn(0f, 0.99f)
                DownloadStatus.DOWNLOADING -> itemDownloadState.progress.coerceIn(0f, 0.99f)
                DownloadStatus.COMPLETED -> 1f
                else -> 0f
            },
            animationSpec = tween(durationMillis = 350),
            label = "detail_download_progress"
        )
        val animatedSeriesDownloadProgress = rememberDownloadPanelProgress(
            panelState = seriesDownload,
            label = "series_download_progress"
        )
        val seerrRequestState = seerrRequestState(
            item = item,
            isSeerDetail = isSeerDetail,
            activeServerId = activeServerId,
            seerrRepository = seerrRepository,
            coroutineScope = coroutineScope
        )
        val alternateVersionRequestState = versionRequestState(
            item = item,
            isSeerDetail = isSeerDetail,
            activeServerId = activeServerId,
            seerrRepository = seerrRepository,
            requestOptions = seerrRequestState.requestOptions
        )
        val canManageSubtitles = remember {
            !isSeerDetail && mediaRepository.getPersistedHomeSnapshot()?.canManageSubtitles == true
        }
        val subtitlePreferredLanguage = remember {
            mediaRepository.getPersistedHomeSnapshot()?.subtitleLanguagePreference
        }
        val isSyncTranscodingAllowed = remember {
            mediaRepository.getPersistedHomeSnapshot()?.isSyncTranscodingAllowed ?: true
        }
        val busyLabel = stringResource(
            if (seerrRequestState.optionsLoading) {
                R.string.detail_seerr_loading_options
            } else {
                R.string.detail_seerr_requesting
            }
        )

        LaunchedEffect(itemDownloadState.status, itemDownloadState.message) {
            ui.downloadActionMenu = false
        }
        LaunchedEffect(itemDownloadState.status) {
            if (
                ui.previousDownloadStatus != DownloadStatus.FAILED &&
                itemDownloadState.status == DownloadStatus.FAILED
            ) {
                ui.downloadErrorDialogMessage = downloadFailure(state = itemDownloadState)
            }
            ui.previousDownloadStatus = itemDownloadState.status
        }
        LaunchedEffect(
            seriesDownload.status,
            seriesDownload.activeItemIds.size,
            seriesDownload.pausedItemIds.size
        ) {
            ui.seriesDownloadActionMenu = false
        }

        return PhoneDetailExtras(
            context = context,
            item = item,
            mediaStreams = mediaStreams,
            isSeerDetail = isSeerDetail,
            seerrConnected = seerrConnected,
            activeServerId = activeServerId,
            seerrRepository = seerrRepository,
            mediaRepository = mediaRepository,
            downloadRepository = downloadRepository,
            coroutineScope = coroutineScope,
            ui = ui,
            itemDownloadState = itemDownloadState,
            isPausedDownload = isPausedDownload,
            animatedDownloadProgress = animatedDownloadProgress,
            seriesDownload = seriesDownload,
            animatedSeriesDownloadProgress = animatedSeriesDownloadProgress,
            seerrRequestState = seerrRequestState,
            alternateVersionRequestState = alternateVersionRequestState,
            canManageSubtitles = canManageSubtitles,
            subtitlePreferredLanguage = subtitlePreferredLanguage,
            isSyncTranscodingAllowed = isSyncTranscodingAllowed,
            busyLabel = busyLabel,
            onMediaStreamsRefreshed = onMediaStreamsRefreshed,
            onRemoteTrailerClick = onRemoteTrailerClick,
            onCastClick = onCastClick
        )
    }

    @Composable
    override fun rememberController(
        itemId: String,
        item: BaseItemDto?,
        episodeItem: BaseItemDto?,
        onEpisodePlaybackChanged: (String) -> Unit
    ): DetailController {
        return rememberPhoneDetailController(
            context = context,
            itemId = itemId,
            item = item,
            episodeItem = episodeItem,
            mediaRepository = mediaRepository,
            authRepository = authRepository,
            playerPreferences = playerPreferences,
            preferences = preferences,
            onEpisodePlaybackChanged = onEpisodePlaybackChanged
        )
    }
}

internal class DetailExtrasUiState {
    var showDownloadQualityPicker by mutableStateOf(false)
    var pendingSeriesEpisodes by mutableStateOf<List<BaseItemDto>?>(null)
    var downloadErrorDialogMessage by mutableStateOf<String?>(null)
    var showSubtitleSearch by mutableStateOf(false)
    var downloadActionMenu by mutableStateOf(false)
    var previousDownloadStatus by mutableStateOf<DownloadStatus?>(null)
    var seriesQueueInProgress by mutableStateOf(false)
    var seriesStorageSelectionDialogState by mutableStateOf<SeriesSeasonSelectionDialogState?>(null)
    var seriesDownloadActionMenu by mutableStateOf(false)
}

private class PhoneDetailExtras(
    private val context: Context,
    private val item: BaseItemDto,
    private val mediaStreams: List<MediaStream>,
    override val isSeerDetail: Boolean,
    private val seerrConnected: Boolean,
    private val activeServerId: String?,
    private val seerrRepository: SeerrRepository,
    private val mediaRepository: MediaRepository,
    private val downloadRepository: com.jellycine.app.download.DownloadRepository,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope,
    private val ui: DetailExtrasUiState,
    private val itemDownloadState: ItemDownloadState,
    private val isPausedDownload: Boolean,
    private val animatedDownloadProgress: Float,
    private val seriesDownload: com.jellycine.app.ui.components.common.DownloadPanelState,
    private val animatedSeriesDownloadProgress: Float,
    private val seerrRequestState: SeerrRequestUiState,
    private val alternateVersionRequestState: SeerrVersions,
    private val canManageSubtitles: Boolean,
    private val subtitlePreferredLanguage: String?,
    private val isSyncTranscodingAllowed: Boolean,
    private val busyLabel: String,
    private val onMediaStreamsRefreshed: (List<MediaStream>) -> Unit,
    private val onRemoteTrailerClick: (String, String?) -> Unit,
    private val onCastClick: () -> Unit
) : DetailExtras {

    override val showSeerrRequestRow: Boolean get() = isSeerDetail && seerrConnected
    override val supportsDownload: Boolean get() = true
    override val canSearchSubtitles: Boolean get() = canManageSubtitles

    private val canDownloadItem: Boolean
        get() = item.id != null && item.canDownload != false && !isSeerDetail
    private val hasActiveDownload: Boolean
        get() = itemDownloadState.status == DownloadStatus.DOWNLOADING ||
            itemDownloadState.status == DownloadStatus.QUEUED

    @Composable
    override fun HeroCastButton(size: Dp) {
        ScreenCastButton(onConnectedClick = onCastClick, size = size)
    }

    @Composable
    override fun CastStatusBanner() {
        val castPlaybackState by CastController.playbackState.collectAsState()
        if (!castPlaybackState.isConnected || isSeerDetail) return
        Surface(
            color = Color(0xFF173025),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Cast,
                    contentDescription = null,
                    tint = Color(0xFFA7FFD7),
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (castPlaybackState.isCastingMedia) {
                        "Casting to ${castPlaybackState.deviceName?.takeIf { it.isNotBlank() } ?: "device"}"
                    } else {
                        "Connected to ${castPlaybackState.deviceName?.takeIf { it.isNotBlank() } ?: "device"}"
                    },
                    fontSize = 12.sp,
                    color = Color(0xFFE6FFF3),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    @Composable
    override fun VersionRequestBadge() {
        if (!alternateVersionRequestState.visible) return
        CompactSeerrRequestButton(
            state = alternateVersionRequestState,
            isBusy = seerrRequestState.isBusy &&
                seerrRequestState.prefer4KRequest &&
                seerrRequestState.preferredRequest == alternateVersionRequestState.targetIs4K,
            onClick = {
                seerrRequestState.onLoadQualityRequestOptions(alternateVersionRequestState.targetIs4K)
            }
        )
    }

    @Composable
    override fun SeerrRequestRow(
        modifier: Modifier,
        buttonHeight: Dp,
        hasTrailer: Boolean,
        onTrailerClick: () -> Unit
    ) {
        SeerrRequestButtonRow(
            requestState = seerrRequestState.requestState,
            isBusy = seerrRequestState.isBusy,
            busyLabel = busyLabel,
            onRequestClick = { seerrRequestState.onLoadRequestOptions(null) },
            buttonHeight = buttonHeight,
            hasTrailer = hasTrailer,
            onTrailerClick = onTrailerClick,
            modifier = modifier
        )
    }

    @Composable
    override fun ItemDownloadAction(buttonHeight: Dp, iconOnly: Boolean) {
        val downloadActionState = when {
            !canDownloadItem -> DetailDownloadActionState.Unavailable
            itemDownloadState.status == DownloadStatus.COMPLETED -> DetailDownloadActionState.Completed
            itemDownloadState.status == DownloadStatus.DOWNLOADING -> DetailDownloadActionState.Downloading
            isPausedDownload -> DetailDownloadActionState.Paused
            itemDownloadState.status == DownloadStatus.QUEUED -> DetailDownloadActionState.Queued
            else -> DetailDownloadActionState.Idle
        }

        DetailDownloadActionButton(
            state = downloadActionState,
            progress = animatedDownloadProgress,
            onClick = {
                when {
                    !canDownloadItem -> Unit
                    hasActiveDownload -> ui.downloadActionMenu = true
                    isSyncTranscodingAllowed -> ui.showDownloadQualityPicker = true
                    else -> coroutineScope.launch {
                        downloadRepository.enqueueItemDownload(
                            item, TranscodeProfile("Original", null, null), null
                        ).onFailure { throwable ->
                            ui.downloadErrorDialogMessage =
                                downloadFailure(rawMessage = throwable.message)
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize(),
            iconOnly = iconOnly
        )

        DownloadActionMenu(
            expanded = ui.downloadActionMenu,
            canResume = isPausedDownload,
            hasActiveDownloads = hasActiveDownload,
            onDismissRequest = { ui.downloadActionMenu = false },
            onPauseResume = {
                ui.downloadActionMenu = false
                item.id?.let { itemId ->
                    if (isPausedDownload) {
                        downloadRepository.resumeDownload(itemId)
                    } else {
                        downloadRepository.pauseDownload(itemId)
                    }
                }
            },
            onCancel = {
                ui.downloadActionMenu = false
                item.id?.let(downloadRepository::cancelDownload)
            }
        )
    }

    @Composable
    override fun SubtitleSearchAction(size: Dp) {
        SubtitleSearchActionButton(
            onClick = { ui.showSubtitleSearch = true },
            size = size
        )
    }

    @Composable
    override fun SeriesDownloadAction(modifier: Modifier, buttonHeight: Dp) {
        val hasActiveSeriesDownloads = seriesDownload.hasActiveDownloads
        val canResumeSeriesDownloads = seriesDownload.canResumeDownloads

        Box(
            modifier = modifier.height(buttonHeight)
        ) {
            OutlinedButton(
                onClick = {
                    if (hasActiveSeriesDownloads) {
                        ui.seriesDownloadActionMenu = true
                    } else {
                        coroutineScope.launch {
                            val seriesId = item.id
                            if (seriesId.isNullOrBlank()) return@launch
                            ui.seriesQueueInProgress = true
                            try {
                                downloadRepository.buildSeriesDownloadEstimate(seriesId).fold(
                                    onSuccess = { estimate ->
                                        ui.seriesStorageSelectionDialogState =
                                            SeriesSeasonSelectionDialogState.fromEstimate(estimate)
                                    },
                                    onFailure = { throwable ->
                                        ui.downloadErrorDialogMessage =
                                            downloadFailure(rawMessage = throwable.message)
                                    }
                                )
                            } finally {
                                ui.seriesQueueInProgress = false
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFF1F1F24),
                    contentColor = Color.White
                )
            ) {
                DownloadContent(
                    panelState = seriesDownload,
                    isQueueing = ui.seriesQueueInProgress,
                    progress = animatedSeriesDownloadProgress,
                    idleLabelRes = R.string.downloads_action_download_series,
                    fontSize = 14.sp,
                    iconSize = 18.dp,
                    progressSize = 18.dp
                )
            }

            DownloadActionMenu(
                expanded = ui.seriesDownloadActionMenu,
                canResume = canResumeSeriesDownloads,
                hasActiveDownloads = hasActiveSeriesDownloads,
                onDismissRequest = { ui.seriesDownloadActionMenu = false },
                onPauseResume = {
                    ui.seriesDownloadActionMenu = false
                    if (canResumeSeriesDownloads) {
                        seriesDownload.pausedItemIds.forEach(downloadRepository::resumeDownload)
                    } else {
                        seriesDownload.pausableItemIds.forEach(downloadRepository::pauseDownload)
                    }
                },
                onCancel = {
                    ui.seriesDownloadActionMenu = false
                    seriesDownload.activeItemIds.forEach(downloadRepository::cancelDownload)
                }
            )
        }
    }

    @Composable
    override fun Seasons(
        series: BaseItemDto,
        seriesId: String,
        mediaRepository: MediaRepository,
        activeServerId: String?,
        onSeasonClick: (String, String, String?) -> Unit
    ) {
        SeasonsSection(
            series = series,
            seriesId = seriesId,
            mediaRepository = mediaRepository,
            activeServerId = activeServerId,
            refreshKey = seerrRequestState.seasonRefreshKey,
            loadingSeasonNumber = seerrRequestState.pendingSeasonRequestNumber
                ?.takeIf { seerrRequestState.isBusy },
            onSeasonRequest = { seasonNumber ->
                seerrRequestState.onLoadRequestOptions(seasonNumber)
            },
            loadSeerrSeasons = { scopeId, tmdbId ->
                seerrRepository.getTitleSeasons(scopeId = scopeId, tmdbId = tmdbId)
                    .getOrDefault(emptyList())
            },
            onSeasonClick = onSeasonClick
        )
    }

    @Composable
    override fun Recommendations(
        item: BaseItemDto,
        directors: List<BaseItemPerson>,
        mediaRepository: MediaRepository,
        activeServerId: String?,
        onItemClick: (String) -> Unit
    ) {
        Recommendations(
            item = item,
            directors = directors,
            isSeerDetail = isSeerDetail,
            activeServerId = activeServerId,
            mediaRepository = mediaRepository,
            seerrRepository = seerrRepository,
            onItemClick = onItemClick
        )
    }

    @Composable
    override fun Dialogs(backdropImageUrl: String?) {
        ui.seriesStorageSelectionDialogState?.let { dialogState ->
            DownloadDialog(
                title = "Choose Seasons",
                subtitle = "Pick seasons to download. Selected total must fit available storage.",
                availableBytes = dialogState.availableBytes,
                options = dialogState.options,
                initialSelection = dialogState.options.map { it.id }.toSet(),
                confirmLabel = "Download Seasons",
                onDismiss = { ui.seriesStorageSelectionDialogState = null },
                onConfirm = { selectedIds ->
                    val selectedEpisodes = selectedIds
                        .flatMap { seasonId -> dialogState.episodesBySeasonId[seasonId].orEmpty() }
                        .distinctBy { it.id }
                    ui.seriesStorageSelectionDialogState = null
                    if (isSyncTranscodingAllowed) {
                        ui.pendingSeriesEpisodes = selectedEpisodes
                        ui.showDownloadQualityPicker = true
                    } else {
                        coroutineScope.launch {
                            ui.seriesQueueInProgress = true
                            try {
                                downloadRepository.enqueueEpisodeDownloads(
                                    selectedEpisodes, TranscodeProfile("Original", null, null), null
                                ).onFailure { throwable ->
                                    ui.downloadErrorDialogMessage =
                                        downloadFailure(rawMessage = throwable.message)
                                }
                            } finally {
                                ui.seriesQueueInProgress = false
                            }
                        }
                    }
                }
            )
        }

        ui.downloadErrorDialogMessage?.let { message ->
            FailureDialog(
                message = message,
                onDismiss = { ui.downloadErrorDialogMessage = null }
            )
        }

        SeerrRequestDialogs(
            state = seerrRequestState,
            itemName = item.name?.takeIf { it.isNotBlank() } ?: "Unknown",
            backdropImageUrl = backdropImageUrl
        )

        if (ui.showSubtitleSearch) {
            item.id?.let { subtitleItemId ->
                SubtitleSearchDialog(
                    itemId = subtitleItemId,
                    defaultLanguage = subtitlePreferredLanguage,
                    canDelete = canManageSubtitles,
                    onSubtitlesChanged = {
                        coroutineScope.launch {
                            mediaRepository.getItemById(subtitleItemId).getOrNull()?.let { fresh ->
                                val sources = fresh.activeDetailMediaSources()
                                onMediaStreamsRefreshed(
                                    sources
                                        .flatMap { it.mediaStreams.orEmpty() }
                                        .ifEmpty { fresh.mediaStreams.orEmpty() }
                                )
                            }
                        }
                    },
                    onDismiss = { ui.showSubtitleSearch = false }
                )
            }
        }

        if (ui.showDownloadQualityPicker) {
            val videoStream = remember(item.id, mediaStreams) {
                mediaStreams.firstOrNull { it.type.equals("Video", ignoreCase = true) }
            }
            val primarySource = remember(item.id, item.mediaSources) {
                item.mediaSources?.firstOrNull()
            }
            val audioStreams = remember(item.id, mediaStreams) {
                mediaStreams.filter { it.type.equals("Audio", ignoreCase = true) }
            }
            DownloadQualityPicker(
                runtimeTicks = item.runTimeTicks,
                sourceHeight = videoStream?.height,
                sourceBitrate = primarySource?.bitrate ?: videoStream?.bitRate,
                sourceFileSize = primarySource?.size,
                audioStreams = audioStreams,
                onDismiss = {
                    ui.showDownloadQualityPicker = false
                    ui.pendingSeriesEpisodes = null
                },
                onSelected = { selection ->
                    ui.showDownloadQualityPicker = false
                    val episodes = ui.pendingSeriesEpisodes
                    ui.pendingSeriesEpisodes = null
                    coroutineScope.launch {
                        if (episodes != null) {
                            ui.seriesQueueInProgress = true
                            try {
                                downloadRepository.enqueueEpisodeDownloads(
                                    episodes, selection.quality, selection.audioStreamIndex
                                ).onFailure { throwable ->
                                    ui.downloadErrorDialogMessage =
                                        downloadFailure(rawMessage = throwable.message)
                                }
                            } finally {
                                ui.seriesQueueInProgress = false
                            }
                        } else {
                            downloadRepository.enqueueItemDownload(
                                item, selection.quality, selection.audioStreamIndex
                            ).onFailure { throwable ->
                                ui.downloadErrorDialogMessage =
                                    downloadFailure(rawMessage = throwable.message)
                            }
                        }
                    }
                }
            )
        }
    }
}

@Composable
internal fun CompactSeerrRequestButton(
    state: SeerrVersions,
    isBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRequested = state.requestState == SeerrRequestState.REQUESTED
    val contentDescription = if (state.targetIs4K) {
        stringResource(R.string.detail_seerr_request_4k)
    } else {
        stringResource(R.string.detail_seerr_request_1080p)
    }
    val enabled = !isBusy && !isRequested
    val accentColor = if (state.targetIs4K) Color(0xFF9B7CFF) else Color(0xFF22D3EE)

    Surface(
        modifier = modifier
            .size(28.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(7.dp),
        color = if (isRequested) Color(0xFF1F1F24) else accentColor.copy(alpha = 0.18f),
        border = BorderStroke(
            1.dp,
            if (isRequested) Color.White.copy(alpha = 0.18f) else accentColor.copy(alpha = 0.55f)
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when {
                isBusy -> CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    strokeWidth = 2.dp,
                    color = accentColor
                )

                isRequested -> Icon(
                    imageVector = Icons.Rounded.Schedule,
                    contentDescription = stringResource(R.string.detail_seerr_requested),
                    tint = Color(0xFF9CDCFE),
                    modifier = Modifier.size(16.dp)
                )

                else -> Icon(
                    imageVector = Icons.Rounded.AddCircle,
                    contentDescription = contentDescription,
                    tint = accentColor,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}
