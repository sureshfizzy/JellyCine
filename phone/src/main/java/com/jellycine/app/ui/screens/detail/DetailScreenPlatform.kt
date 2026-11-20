package com.jellycine.app.ui.screens.detail

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.jellycine.app.cast.CastController
import com.jellycine.app.download.DownloadRepositoryProvider
import com.jellycine.app.ui.components.common.*
import com.jellycine.data.model.*
import com.jellycine.data.repository.*
import com.jellycine.player.preferences.*
import com.jellycine.preferences.Preferences
import com.jellycine.shared.R
import com.jellycine.ui.components.common.*
import com.jellycine.ui.screens.detail.*
import kotlinx.coroutines.*

/** Android implementation of the detail screen hooks: Seerr, downloads, cast, player. */
internal class DetailScreenPlatform(
    private val context: Context,
    private val seerrRepository: SeerrRepository,
    private val playerPreferences: PlayerPreferences,
    private val preferences: Preferences,
    private val mediaRepository: MediaRepository,
    private val authRepository: AuthRepository
) : DetailPlatform {

    override val trackPreferences: DetailTrackPreferences = PlayerPreferencesTrackPreferences(playerPreferences)

    override val seasonDownloadAction: (@Composable (String, List<BaseItemDto>) -> Unit) =
        { seasonId, episodes -> SeasonDownloadAction(seasonId, episodes) }

    private val seerrSupport = SeerrDetailSupport(seerrRepository)

    override suspend fun loadItem(
        itemId: String,
        activeServerId: String?,
        mediaRepository: MediaRepository
    ): Result<BaseItemDto> = seerrSupport.loadItem(itemId, activeServerId, mediaRepository)

    override suspend fun loadLogoImage(
        item: BaseItemDto,
        activeServerId: String?,
        mediaRepository: MediaRepository
    ): String? = seerrSupport.loadLogoImage(item, activeServerId, mediaRepository)

    override suspend fun loadSeerrPerson(
        tmdbId: String,
        activeServerId: String?
    ): Pair<BaseItemDto?, List<SeerrRecommendationTitle>>? =
        seerrSupport.loadPerson(tmdbId, activeServerId)

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
        val seerrExtras = rememberSeerrDetailExtras(item, seerrRepository, activeServerId)
        val canManageSubtitles = remember {
            !isSeerDetail && mediaRepository.getPersistedHomeSnapshot()?.canManageSubtitles == true
        }
        val subtitlePreferredLanguage = remember {
            mediaRepository.getPersistedHomeSnapshot()?.subtitleLanguagePreference
        }
        val isSyncTranscodingAllowed = remember {
            mediaRepository.getPersistedHomeSnapshot()?.isSyncTranscodingAllowed ?: true
        }
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
            seerr = seerrExtras,
            activeServerId = activeServerId,
            mediaRepository = mediaRepository,
            downloadRepository = downloadRepository,
            coroutineScope = coroutineScope,
            ui = ui,
            itemDownloadState = itemDownloadState,
            isPausedDownload = isPausedDownload,
            animatedDownloadProgress = animatedDownloadProgress,
            seriesDownload = seriesDownload,
            animatedSeriesDownloadProgress = animatedSeriesDownloadProgress,
            canManageSubtitles = canManageSubtitles,
            subtitlePreferredLanguage = subtitlePreferredLanguage,
            isSyncTranscodingAllowed = isSyncTranscodingAllowed,
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
    private val seerr: SeerrDetailExtras,
    private val activeServerId: String?,
    private val mediaRepository: MediaRepository,
    private val downloadRepository: com.jellycine.app.download.DownloadRepository,
    private val coroutineScope: kotlinx.coroutines.CoroutineScope,
    private val ui: DetailExtrasUiState,
    private val itemDownloadState: ItemDownloadState,
    private val isPausedDownload: Boolean,
    private val animatedDownloadProgress: Float,
    private val seriesDownload: com.jellycine.app.ui.components.common.DownloadPanelState,
    private val animatedSeriesDownloadProgress: Float,
    private val canManageSubtitles: Boolean,
    private val subtitlePreferredLanguage: String?,
    private val isSyncTranscodingAllowed: Boolean,
    private val onMediaStreamsRefreshed: (List<MediaStream>) -> Unit,
    private val onRemoteTrailerClick: (String, String?) -> Unit,
    private val onCastClick: () -> Unit
) : SeerrDelegatingDetailExtras(seerr) {

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

        super.Dialogs(backdropImageUrl)

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
