package com.jellycine.app.ui.screens.detail

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.shared.R
import com.jellycine.shared.preferences.Preferences
import com.jellycine.shared.util.image.JellyfinPosterImage
import com.jellycine.shared.util.image.imageTagFor
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.DownloadStatus
import com.jellycine.data.model.ItemDownloadState
import com.jellycine.data.model.MediaStream
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.detail.CodecUtils
import com.jellycine.shared.ui.components.common.activeDetailMediaSources
import com.jellycine.shared.ui.components.common.buildInlineText
import com.jellycine.shared.ui.components.common.buildLocalVersionEntries
import com.jellycine.shared.ui.components.common.OverviewSection
import com.jellycine.shared.ui.components.common.selectedVideoOption
import com.jellycine.app.ui.components.common.canResumeDownloads
import com.jellycine.app.ui.components.common.hasActiveDownloads
import com.jellycine.app.ui.components.common.pausableItemIds
import com.jellycine.app.ui.components.common.isPausedDownloadState
import com.jellycine.app.ui.components.common.rememberDownloadPanelProgress
import com.jellycine.app.ui.components.common.rememberDownloadPanelState
import com.jellycine.app.cast.CastController
import com.jellycine.app.download.DownloadRepositoryProvider
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.app.ui.components.common.DetailBackdropHero
import com.jellycine.app.ui.components.common.DetailBackdropHeroStyle
import com.jellycine.app.ui.components.common.containerHeightDp
import com.jellycine.app.ui.components.common.containerWidthDp
import com.jellycine.app.ui.components.common.detailActionWidth
import com.jellycine.app.ui.components.common.isTabletDetailLayout
import com.jellycine.app.ui.components.common.isTabletLayout
import java.util.Locale
import kotlinx.coroutines.launch


@Composable
fun DetailContent(
    item: BaseItemDto,
    isLoading: Boolean = false,
    trackSelectionSyncVersion: Int = 0,
    onBackPressed: () -> Unit = {},
    onPlayClick: (Int?, Int?) -> Unit = { _, _ -> },
    onPreferredStreamIndexesChanged: (Int?, Int?) -> Unit = { _, _ -> },
    onSimilarItemClick: (String) -> Unit = {},
    onVersionItemSelected: (String) -> Unit = {},
    onPersonClick: (String) -> Unit = {},
    onCastButtonClick: () -> Unit = {},
    onSeasonClick: (String, String, String?) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val authRepository = remember { AuthRepositoryProvider.getInstance(context) }
    val seerrRepository = rememberSeerrRepository(context)
    val downloadRepository = remember { DownloadRepositoryProvider.getInstance(context) }
    val playerPreferences = remember { PlayerPreferences(context) }
    val preferences = remember { Preferences(context) }
    val coroutineScope = rememberCoroutineScope()
    val castPlaybackState by CastController.playbackState.collectAsState()
    val activeServerId by authRepository.getActiveServerId()
        .collectAsState(initial = authRepository.getActiveSessionSnapshot().activeServerId)
    val screenWidthDp = containerWidthDp()
    val screenHeightDp = containerHeightDp()
    val isWidescreenLayout = isTabletLayout(screenWidthDp)
    val useTabletBackdropLayout = isTabletDetailLayout(
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp
    )
    val isSeerDetail = item.isSeerDetailItem()
    val mergeVersionsEnabled by preferences.MergeVersionsEnabled()
        .collectAsState(initial = preferences.isMergeVersionsEnabled())
    val metadataScrollState = rememberScrollState()
    val isEpisode = item.type == "Episode"
    val episodeHeaderText = remember(
        item.type,
        item.parentIndexNumber,
        item.indexNumber,
        item.name
    ) {
        episodeHeaderText(item)
    }
    var heroImageCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var heroImageIndex by remember(item.id) { mutableStateOf(0) }
    val backdropImageUrl = heroImageCandidates.getOrNull(heroImageIndex)
    var logoImageUrl by remember { mutableStateOf<String?>(null) }
    var logoResolved by remember { mutableStateOf(false) }
    var logoLookup by remember { mutableStateOf(true) }
    var logoLoadError by remember { mutableStateOf(false) }
    val activeMediaSources = remember(item.id, item.mediaSources) {
        item.activeDetailMediaSources()
    }
    val effectiveMediaStreams = remember(item.mediaStreams, activeMediaSources) {
        val fromSources = activeMediaSources.flatMap { it.mediaStreams.orEmpty() }
        if (fromSources.isNotEmpty()) fromSources else item.mediaStreams.orEmpty()
    }
    val savedAudioOption = remember(item.id, effectiveMediaStreams, trackSelectionSyncVersion) {
        val currentItemId = item.id ?: return@remember null
        AudioStreamIndex(
            streams = effectiveMediaStreams,
            streamIndex = playerPreferences.getPreferredAudioStreamIndex(currentItemId)
        )
    }
    val savedSubtitleOption = remember(item.id, effectiveMediaStreams, trackSelectionSyncVersion) {
        val currentItemId = item.id ?: return@remember null
        SubtitleStreamIndex(
            streams = effectiveMediaStreams,
            streamIndex = playerPreferences.getPreferredSubtitleStreamIndex(currentItemId)
        )
    }
    val initialVideoOption = remember(item.id, effectiveMediaStreams) {
        buildVideoOptions(effectiveMediaStreams).firstOrNull().orEmpty()
    }
    val initialAudioOption = remember(item.id, effectiveMediaStreams, savedAudioOption) {
        savedAudioOption ?: buildAudioOptions(effectiveMediaStreams).firstOrNull().orEmpty()
    }
    val initialSubtitleOption = remember(item.id, effectiveMediaStreams, savedSubtitleOption) {
        savedSubtitleOption ?: buildDefaultSubtitleOption(effectiveMediaStreams)
    }
    var selectedVideo by rememberSaveable(item.id) { mutableStateOf(initialVideoOption) }
    var selectedAudio by rememberSaveable(item.id, trackSelectionSyncVersion) {
        mutableStateOf(
            initialAudioOption
        )
    }
    var selectedSubtitle by rememberSaveable(item.id, trackSelectionSyncVersion) {
        mutableStateOf(
            initialSubtitleOption
        )
    }
    val runtimeTicks = item.runTimeTicks
    val playbackPositionTicks = item.userData?.playbackPositionTicks ?: 0L
    val isPartiallyWatched =
        runtimeTicks != null && playbackPositionTicks > 0L && playbackPositionTicks < runtimeTicks
    val playButtonText = if (isPartiallyWatched) {
        val remainingTicks = (runtimeTicks - playbackPositionTicks).coerceAtLeast(0L)
        "${CodecUtils.formatRuntime(remainingTicks)} left"
    } else {
        "Play"
    }
    val resumeProgress = if (runtimeTicks != null && runtimeTicks > 0) {
        (playbackPositionTicks.toFloat() / runtimeTicks.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val logoFallbackTitle = if (isEpisode) {
        item.seriesName?.takeIf { it.isNotBlank() }
            ?: item.name?.takeIf { it.isNotBlank() }
            ?: "Unknown"
    } else {
        item.name?.takeIf { it.isNotBlank() } ?: "Unknown"
    }
    val reserveLogoSpace = isLoading || (!logoImageUrl.isNullOrBlank() && !logoLoadError) || logoLookup
    val showTitleFallback = !isLoading && !logoLookup && (logoImageUrl.isNullOrBlank() || logoLoadError)
    val genresText = remember(item.genres) {
        item.genres?.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }
    val descriptionTagline = remember(item.taglines) {
        item.taglines?.firstOrNull { !it.isNullOrBlank() }
    }
    val directors = remember(item.people, isSeerDetail) {
        val directorPeople = item.people?.filter { person ->
            person.isCreditType("Director")
        }.orEmpty()
        if (directorPeople.isNotEmpty() || !isSeerDetail) {
            directorPeople
        } else {
            item.people?.filter { person ->
                person.isCreditType("Creator")
            }.orEmpty()
        }
    }
    val directorCreditLabel = remember(directors) {
        if (directors.any { person -> person.isCreditType("Director") }) {
            "Director:"
        } else {
            "Creator:"
        }
    }
    val hasDescriptionContent = !item.overview.isNullOrBlank() || !descriptionTagline.isNullOrBlank()
    val canDownloadItem = item.id != null && item.canDownload != false && !isSeerDetail
    val pausedDownloadMessage = stringResource(R.string.downloads_status_paused)
    val itemDownloadStateFlow = if (isSeerDetail) null else item.id?.let { downloadRepository.observeItemDownload(it) }
    val itemDownloadState by (itemDownloadStateFlow?.collectAsState()
        ?: remember(item.id) { mutableStateOf(ItemDownloadState()) })
    val isPausedDownload = isPausedDownloadState(itemDownloadState, pausedDownloadMessage)
    val hasActiveDownload = itemDownloadState.status == DownloadStatus.DOWNLOADING ||
            itemDownloadState.status == DownloadStatus.QUEUED
    var downloadErrorDialogMessage by remember(item.id) { mutableStateOf<String?>(null) }
    var downloadActionMenu by remember(
        item.id,
        itemDownloadState.status,
        itemDownloadState.message
    ) {
        mutableStateOf(false)
    }
    var previousDownloadStatus by remember(item.id) { mutableStateOf(itemDownloadState.status) }
    var seriesQueueInProgress by remember(item.id) { mutableStateOf(false) }
    var seriesStorageSelectionDialogState by remember(item.id) {
        mutableStateOf<SeriesSeasonSelectionDialogState?>(
            null
        )
    }
    val trackedDownloads by downloadRepository.observeTrackedDownloads()
        .collectAsState(initial = emptyList())
    var isFavorite by remember(item.id, item.userData?.isFavorite) {
        mutableStateOf(item.userData?.isFavorite == true)
    }
    var moreFromSeasonEpisodes by remember(item.id, item.seriesId, item.seasonId) {
        mutableStateOf<List<BaseItemDto>>(emptyList())
    }
    var localVersions by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    val seerrRequestState = seerrRequestState(
        item = item,
        isSeerDetail = isSeerDetail,
        activeServerId = activeServerId,
        seerrRepository = seerrRepository,
        coroutineScope = coroutineScope
    )
    val seriesDownloadEntries = remember(item.id, item.type, trackedDownloads) {
        val seriesId = item.id
        if (isSeerDetail || item.type != "Series" || seriesId.isNullOrBlank()) {
            emptyList()
        } else {
            trackedDownloads.filter { it.item?.seriesId == seriesId }
        }
    }
    val seriesDownload = rememberDownloadPanelState(entries = seriesDownloadEntries)
    val hasActiveSeriesDownloads = seriesDownload.hasActiveDownloads
    val canResumeSeriesDownloads = seriesDownload.canResumeDownloads
    var seriesDownloadActionMenu by remember(
        item.id,
        seriesDownload.status,
        seriesDownload.activeItemIds.size,
        seriesDownload.pausedItemIds.size
    ) { mutableStateOf(false) }

    fun toggleFavorite() {
        if (isSeerDetail) return
        val currentItemId = item.id ?: return
        val targetState = !isFavorite
        coroutineScope.launch {
            val result = mediaRepository.setFavoriteStatus(
                itemId = currentItemId,
                isFavorite = targetState
            )
            if (result.isSuccess) {
                isFavorite = targetState
            }
        }
    }

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
    val layout = detailScreenLayoutSpec(
        isWidescreenLayout = isWidescreenLayout,
        useTabletBackdropLayout = useTabletBackdropLayout,
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp
    )
    val detailActionButtonHeight = if (useTabletBackdropLayout) 40.dp else 46.dp
    val contentFadeStart = if (useTabletBackdropLayout && layout.backdropHeight.value > 0f) {
        (
            ((layout.contentTopPadding + layout.logoContainerHeight) - 56.dp).value /
                layout.backdropHeight.value
            ).coerceIn(0f, 1f)
    } else {
        null
    }
    val onBackdropLoadError: (Boolean) -> Unit = { hasError ->
        if (
            hasError &&
            backdropImageUrl == heroImageCandidates.getOrNull(heroImageIndex) &&
            heroImageIndex < heroImageCandidates.lastIndex
        ) {
            heroImageIndex += 1
        }
    }
    LaunchedEffect(itemDownloadState.status) {
        if (
            previousDownloadStatus != DownloadStatus.FAILED &&
            itemDownloadState.status == DownloadStatus.FAILED
        ) {
            downloadErrorDialogMessage = downloadFailure(state = itemDownloadState)
        }
        previousDownloadStatus = itemDownloadState.status
    }

    LaunchedEffect(item.id) {
        if (item.id == null) {
            return@LaunchedEffect
        }
        heroImageCandidates = heroImageCandidates(
            item = item,
            mediaRepository = mediaRepository
        )
        heroImageIndex = 0
    }

    LaunchedEffect(item.id, activeServerId) {
        if (item.id == null) {
            logoLookup = false
            return@LaunchedEffect
        }
        if (logoResolved) {
            logoLookup = false
            return@LaunchedEffect
        }

        logoLookup = true
        logoLoadError = false
        try {
            val nextLogoImageUrl = detailLogoImage(
                item = item,
                activeServerId = activeServerId,
                isSeerDetail = isSeerDetail,
                mediaRepository = mediaRepository,
                seerrRepository = seerrRepository
            )
            if (nextLogoImageUrl != logoImageUrl) {
                logoImageUrl = nextLogoImageUrl
            }
            logoResolved = true
        } finally {
            logoLookup = false
        }
    }

    LaunchedEffect(item.id, item.type, item.seriesId, item.seasonId, item.parentIndexNumber) {
        if (!isEpisode) {
            moreFromSeasonEpisodes = emptyList()
            return@LaunchedEffect
        }

        val seriesId = item.seriesId?.takeIf { it.isNotBlank() }
        if (seriesId.isNullOrBlank()) {
            moreFromSeasonEpisodes = emptyList()
            return@LaunchedEffect
        }

        val seasonId = item.seasonId?.takeIf { it.isNotBlank() }
        val result = if (seasonId != null) {
            mediaRepository.getEpisodes(seriesId = seriesId, seasonId = seasonId)
        } else {
            mediaRepository.getEpisodes(seriesId = seriesId)
        }

        moreFromSeasonEpisodes = result
            .getOrNull()
            .orEmpty()
            .filter { episode ->
                when {
                    seasonId != null -> episode.seasonId == seasonId
                    item.parentIndexNumber != null -> episode.parentIndexNumber == item.parentIndexNumber
                    else -> true
                }
            }
            .filter { episode ->
                val episodeId = episode.id
                !episodeId.isNullOrBlank() && episodeId != item.id
            }
            .sortedWith(
                compareBy<BaseItemDto>(
                    { it.indexNumber ?: Int.MAX_VALUE },
                    { it.name.orEmpty() },
                    { it.id.orEmpty() }
                )
            )
    }

    LaunchedEffect(item.id, item.type, isSeerDetail, mergeVersionsEnabled) {
        val supportsLocalVersions = item.type.equals("Movie", ignoreCase = true) ||
            item.type.equals("Episode", ignoreCase = true)
        if (
            !mergeVersionsEnabled ||
            isSeerDetail ||
            !supportsLocalVersions ||
            item.id.isNullOrBlank()
        ) {
            localVersions = emptyList()
            return@LaunchedEffect
        }

        mediaRepository.getLocalVersions(item)
            .getOrNull()
            ?.filter { version -> !version.id.isNullOrBlank() }
            ?.let { versions ->
                val hasCurrentVersionList = localVersions.any { version -> version.id == item.id }
                if (versions.size > 1 || !hasCurrentVersionList) {
                    localVersions = versions
                }
            }
    }

    val moreFromSeasonTitle = remember(item.seasonName, item.parentIndexNumber) {
        val seasonLabel = item.parentIndexNumber?.let { "Season $it" }
            ?: item.seasonName?.takeIf { it.isNotBlank() }
            ?: "Season"
        "More from $seasonLabel"
    }

    val baseVideoOptions = remember(effectiveMediaStreams) { buildVideoOptions(effectiveMediaStreams) }
    val videoFallbackLabel = stringResource(R.string.detail_video_fallback)
    val smallFileSizeLabel = stringResource(R.string.detail_file_size_under_1_mb)
    val localVersionEntries = remember(localVersions, item.id, videoFallbackLabel, smallFileSizeLabel) {
        buildLocalVersionEntries(
            localVersions = localVersions,
            currentItemId = item.id,
            videoFallbackLabel = videoFallbackLabel,
            smallFileSizeLabel = smallFileSizeLabel
        )
    }
    val localVersionOptions = localVersionEntries.map { (label, _) -> label }
    val videoOptions = localVersionOptions.ifEmpty { baseVideoOptions }
    val displayedSelectedVideo = selectedVideoOption(
        localVersionEntries = localVersionEntries,
        currentItemId = item.id,
        selectedVideo = selectedVideo,
        videoOptions = videoOptions,
        baseVideoOptions = baseVideoOptions
    )
    val audioOptions = remember(effectiveMediaStreams) { buildAudioOptions(effectiveMediaStreams) }
    val subtitleOptions =
        remember(effectiveMediaStreams) { buildSubtitleOptions(effectiveMediaStreams) }
    val defaultSubtitleOption =
        remember(effectiveMediaStreams) { buildDefaultSubtitleOption(effectiveMediaStreams) }
    val codecBadges = CodecBadges(
        streams = effectiveMediaStreams,
        selectedVideo = displayedSelectedVideo,
        selectedAudio = selectedAudio
    )
    val videoInlineMetaText = remember(
        activeMediaSources,
        effectiveMediaStreams,
        localVersionOptions
    ) {
        if (localVersionOptions.isNotEmpty()) {
            null
        } else {
            buildInlineText(
                mediaSources = activeMediaSources,
                streams = effectiveMediaStreams,
                smallFileSizeLabel = smallFileSizeLabel
            )
        }
    }

    fun persistTrackSelection(audioOption: String, subtitleOption: String): Pair<Int?, Int?> {
        val audioStreamIndex = AudioStreamIndex(
            streams = effectiveMediaStreams,
            selectedOption = audioOption
        )
        val subtitleStreamIndex = SubtitleStreamIndex(
            streams = effectiveMediaStreams,
            selectedOption = subtitleOption
        )
        item.id?.let { currentItemId ->
            playerPreferences.setPreferredAudioStreamIndex(currentItemId, audioStreamIndex)
            playerPreferences.setPreferredSubtitleStreamIndex(currentItemId, subtitleStreamIndex)
        }
        onPreferredStreamIndexesChanged(audioStreamIndex, subtitleStreamIndex)
        return audioStreamIndex to subtitleStreamIndex
    }

    val onVideoOptionSelected: (String) -> Unit = { option ->
        val selectedVersion = localVersionEntries.firstOrNull { (label, _) -> label == option }?.second
        val selectedVersionId = selectedVersion?.id
        if (selectedVersionId != null && selectedVersionId != item.id) {
            onVersionItemSelected(selectedVersionId)
        } else {
            selectedVideo = option
        }
    }
    val onAudioOptionSelected: (String) -> Unit = { option ->
        selectedAudio = option
        persistTrackSelection(
            audioOption = option,
            subtitleOption = selectedSubtitle
        )
    }
    val onSubtitleOptionSelected: (String) -> Unit = { option ->
        selectedSubtitle = option
        persistTrackSelection(
            audioOption = selectedAudio,
            subtitleOption = option
        )
    }

    LaunchedEffect(displayedSelectedVideo, audioOptions, subtitleOptions, defaultSubtitleOption) {
        if (selectedVideo != displayedSelectedVideo) selectedVideo = displayedSelectedVideo
        if (selectedAudio !in audioOptions) {
            selectedAudio = audioOptions.firstOrNull().orEmpty()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (useTabletBackdropLayout) {
            DetailBackdropHero(
                imageUrl = backdropImageUrl,
                contentDescription = item.name,
                heroHeight = layout.backdropHeight,
                style = DetailBackdropHeroStyle.TabletBackdrop,
                bottomFadeHeight = 0.dp,
                contentFadeStartFraction = contentFadeStart,
                onErrorStateChange = onBackdropLoadError
            ) {
                if (!isSeerDetail) {
                    DetailHeroCastButtonOverlay(onCastButtonClick = onCastButtonClick)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(if (useTabletBackdropLayout) Color.Transparent else Color.Black),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {

            if (!useTabletBackdropLayout) {
                item {
                    DetailBackdropHero(
                        imageUrl = backdropImageUrl,
                        contentDescription = item.name,
                        heroHeight = layout.heroHeight,
                        bottomFadeHeight = 156.dp,
                        overlayGradient = heroOverlayGradient,
                        bottomFadeGradient = heroBottomFadeGradient,
                        onErrorStateChange = onBackdropLoadError
                    ) {
                        if (!isSeerDetail) {
                            DetailHeroCastButtonOverlay(onCastButtonClick = onCastButtonClick)
                        }
                    }
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = layout.horizontalPadding)
                        .padding(top = layout.contentTopPadding)
                        .offset(y = layout.headerOffset)
                ) {
                    Column(
                        modifier = if (layout.contentMaxWidth != null) {
                            Modifier
                                .fillMaxWidth()
                                .widthIn(max = layout.contentMaxWidth)
                        } else {
                            Modifier.fillMaxWidth()
                        }
                    ) {
                        if (reserveLogoSpace || showTitleFallback) {
                            Box(
                                modifier = Modifier
                                    .height(layout.logoContainerHeight)
                                    .fillMaxWidth()
                            ) {
                                if (!logoImageUrl.isNullOrBlank() && !logoLoadError) {
                                    JellyfinPosterImage(
                                        imageUrl = logoImageUrl,
                                        contentDescription = item.name,
                                        modifier = Modifier
                                            .fillMaxWidth(0.94f)
                                            .height(layout.logoContainerHeight)
                                            .align(Alignment.CenterStart),
                                        context = context,
                                        contentScale = ContentScale.Fit,
                                        alignment = Alignment.CenterStart,
                                        onErrorStateChange = { hasError ->
                                            logoLoadError = hasError
                                        }
                                    )
                                } else if (showTitleFallback) {
                                    Text(
                                        text = logoFallbackTitle,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        lineHeight = 30.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .offset(y = (-2).dp)
                                    )
                                }
                            }
                        }

                        if (isEpisode) {
                            episodeHeaderText?.let { header ->
                                Text(
                                    text = header,
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.88f),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isWidescreenLayout) {
                                        Modifier.horizontalScroll(metadataScrollState)
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            item.communityRating?.let { rating ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Star,
                                        contentDescription = "Rating",
                                        tint = Color(0xFFFF4D4F),
                                        modifier = Modifier.size(17.dp)
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.1f", rating),
                                        fontSize = 14.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }

                            item.productionYear?.let { year ->
                                Text(
                                    text = year.toString(),
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }

                            item.runTimeTicks?.let { ticks ->
                                Text(
                                    text = CodecUtils.formatRuntime(ticks),
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }

                            val officialRatingLabel =
                                item.officialRating?.takeIf { it.isNotBlank() } ?: "NR"
                            Surface(
                                color = Color.Transparent,
                                shape = RoundedCornerShape(5.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
                            ) {
                                Text(
                                    text = officialRatingLabel,
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                    maxLines = 1
                                )
                            }

                            if (isWidescreenLayout && !genresText.isNullOrBlank()) {
                                Text(
                                    text = genresText,
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (!isWidescreenLayout && !genresText.isNullOrBlank()) {
                            Text(
                                text = genresText,
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 6.dp)
                            )
                        }

                        if (castPlaybackState.isConnected && !isSeerDetail) {
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

                        if (codecBadges.hasAnyBadges) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp)
                            ) {
                                if (codecBadges.has4K) {
                                    item {
                                        CapabilityBadge(text = "4K")
                                    }
                                }

                                if (codecBadges.hdrBadgeText.isNotBlank()) {
                                    item {
                                        CapabilityBadge(
                                            text = codecBadges.hdrBadgeText,
                                            icon = if (codecBadges.hdrBadgeText.equals(
                                                    "Dolby Vision",
                                                    ignoreCase = true
                                                )
                                            ) null else Icons.Rounded.HdrOn,
                                            customIcon = if (codecBadges.hdrBadgeText.equals(
                                                    "Dolby Vision",
                                                    ignoreCase = true
                                                )
                                            ) R.drawable.ic_dolby_logo else null,
                                            iconTintUnspecified = codecBadges.hdrBadgeText.equals(
                                                "Dolby Vision",
                                                ignoreCase = true
                                            )
                                        )
                                    }
                                }

                                if (codecBadges.hasSpatialAudio) {
                                    item {
                                        CapabilityBadge(
                                            text = "Spatial Audio",
                                            customIcon = R.drawable.ic_spatial_audio,
                                            customIconTint = Color(0xFF8DFFB3)
                                        )
                                    }
                                }

                                if (codecBadges.dolbyAudioBadgeText.isNotBlank()) {
                                    item {
                                        CapabilityBadge(
                                            text = codecBadges.dolbyAudioBadgeText,
                                            customIcon = if (codecBadges.hasDolbyAtmos) R.drawable.ic_dolby_atmos else R.drawable.ic_dolby_logo,
                                            iconTintUnspecified = true
                                        )
                                    }
                                }

                                if (codecBadges.audioChannelBadgeText.isNotBlank()) {
                                    item {
                                        CapabilityBadge(
                                            text = codecBadges.audioChannelBadgeText
                                        )
                                    }
                                }
                            }
                        }

                        if (isWidescreenLayout && hasDescriptionContent) {
                            OverviewSection(
                                overview = item.overview,
                                tagline = descriptionTagline,
                                modifier = Modifier.padding(top = 14.dp)
                            )
                        }

                        if (isWidescreenLayout && isSeerDetail) {
                            SeerrRequestButton(
                                state = seerrRequestState,
                                modifier = Modifier
                                    .fillMaxWidth(
                                        detailActionWidth(
                                            screenWidthDp,
                                            useTabletLayout = useTabletBackdropLayout
                                        )
                                    )
                                    .padding(top = if (hasDescriptionContent) 12.dp else 14.dp)
                                    .height(detailActionButtonHeight)
                            )
                        }

                        if (isWidescreenLayout) {
                            DirectorCreditRow(
                                label = directorCreditLabel,
                                directors = directors,
                                onPersonClick = onPersonClick
                            )
                        }

                        Spacer(modifier = Modifier.height(layout.logoBottomSpacing))

                        TrackSection(
                            isWidescreenLayout = isWidescreenLayout,
                            displayedSelectedVideo = displayedSelectedVideo,
                            videoOptions = videoOptions,
                            videoInlineMetaText = videoInlineMetaText,
                            selectedAudio = selectedAudio,
                            audioOptions = audioOptions,
                            selectedSubtitle = selectedSubtitle,
                            subtitleOptions = subtitleOptions,
                            onVideoOptionSelected = onVideoOptionSelected,
                            onAudioOptionSelected = onAudioOptionSelected,
                            onSubtitleOptionSelected = onSubtitleOptionSelected
                        )

                        if (item.type != "Series" && !isSeerDetail) {
                            ActionSection(
                                screenWidthDp = screenWidthDp,
                                useTabletLayout = useTabletBackdropLayout,
                                buttonHeight = detailActionButtonHeight,
                                playButtonText = playButtonText,
                                isPartiallyWatched = isPartiallyWatched,
                                resumeProgress = resumeProgress,
                                canDownloadItem = canDownloadItem,
                                itemDownloadState = itemDownloadState,
                                isPausedDownload = isPausedDownload,
                                hasActiveDownload = hasActiveDownload,
                                downloadActionMenu = downloadActionMenu,
                                downloadProgress = animatedDownloadProgress,
                                isFavorite = isFavorite,
                                onPlayClick = {
                                    val (selectedAudioStreamIndex, selectedSubtitleStreamIndex) =
                                        persistTrackSelection(
                                            audioOption = selectedAudio,
                                            subtitleOption = selectedSubtitle
                                        )
                                    onPlayClick(
                                        selectedAudioStreamIndex,
                                        selectedSubtitleStreamIndex
                                    )
                                },
                                onDownloadClick = {
                                    coroutineScope.launch {
                                        downloadRepository.enqueueItemDownload(item)
                                            .onFailure { throwable ->
                                                downloadErrorDialogMessage =
                                                    downloadFailure(
                                                        rawMessage = throwable.message
                                                    )
                                            }
                                    }
                                },
                                onDownloadMenuChange = { expanded ->
                                    downloadActionMenu = expanded
                                },
                                onPauseResumeDownload = {
                                    downloadActionMenu = false
                                    item.id?.let { itemId ->
                                        if (isPausedDownload) {
                                            downloadRepository.resumeDownload(itemId)
                                        } else {
                                            downloadRepository.pauseDownload(itemId)
                                        }
                                    }
                                },
                                onCancelDownload = {
                                    downloadActionMenu = false
                                    item.id?.let(downloadRepository::cancelDownload)
                                },
                                onFavoriteClick = ::toggleFavorite
                            )
                        }

                        if (!isWidescreenLayout && hasDescriptionContent) {
                            OverviewSection(
                                overview = item.overview,
                                tagline = descriptionTagline,
                                modifier = Modifier.padding(top = 18.dp)
                            )
                        }

                        if (!isWidescreenLayout && isSeerDetail) {
                            SeerrRequestButton(
                                state = seerrRequestState,
                                modifier = Modifier
                                    .fillMaxWidth(
                                        detailActionWidth(
                                            screenWidthDp,
                                            useTabletLayout = useTabletBackdropLayout
                                        )
                                    )
                                    .padding(top = if (hasDescriptionContent) 14.dp else 18.dp)
                                    .height(detailActionButtonHeight)
                            )
                        }

                        if (!isWidescreenLayout) {
                            DirectorCreditRow(
                                label = directorCreditLabel,
                                directors = directors,
                                onPersonClick = onPersonClick
                            )
                        }

                        if (isEpisode) {
                            MoreFromSeasonSection(
                                episodes = moreFromSeasonEpisodes,
                                mediaRepository = mediaRepository,
                                title = moreFromSeasonTitle,
                                onEpisodeClick = onSimilarItemClick
                            )
                        }

                        if (item.type == "Series" && !isSeerDetail) {
                            SeriesActionSection(
                                screenWidthDp = screenWidthDp,
                                useTabletLayout = useTabletBackdropLayout,
                                buttonHeight = detailActionButtonHeight,
                                seriesDownload = seriesDownload,
                                seriesQueueInProgress = seriesQueueInProgress,
                                seriesDownloadProgress = animatedSeriesDownloadProgress,
                                seriesDownloadActionMenu = seriesDownloadActionMenu,
                                canResumeSeriesDownloads = canResumeSeriesDownloads,
                                hasActiveSeriesDownloads = hasActiveSeriesDownloads,
                                isFavorite = isFavorite,
                                onSeriesDownloadClick = {
                                    coroutineScope.launch {
                                        val seriesId = item.id
                                        if (seriesId.isNullOrBlank()) {
                                            return@launch
                                        }
                                        seriesQueueInProgress = true
                                        try {
                                            val estimateResult =
                                                downloadRepository.buildSeriesDownloadEstimate(
                                                    seriesId
                                                )
                                            estimateResult.fold(
                                                onSuccess = { estimate ->
                                                    seriesStorageSelectionDialogState =
                                                        SeriesSeasonSelectionDialogState
                                                            .fromEstimate(estimate)
                                                },
                                                onFailure = { throwable ->
                                                    downloadErrorDialogMessage =
                                                        downloadFailure(
                                                            rawMessage = throwable.message
                                                        )
                                                }
                                            )
                                        } finally {
                                            seriesQueueInProgress = false
                                        }
                                    }
                                },
                                onSeriesDownloadMenuChange = { expanded ->
                                    seriesDownloadActionMenu = expanded
                                },
                                onPauseResumeSeriesDownloads = {
                                    seriesDownloadActionMenu = false
                                    if (canResumeSeriesDownloads) {
                                        seriesDownload.pausedItemIds.forEach(
                                            downloadRepository::resumeDownload
                                        )
                                    } else {
                                        seriesDownload.pausableItemIds.forEach(
                                            downloadRepository::pauseDownload
                                        )
                                    }
                                },
                                onCancelSeriesDownloads = {
                                    seriesDownloadActionMenu = false
                                    seriesDownload.activeItemIds.forEach(
                                        downloadRepository::cancelDownload
                                    )
                                },
                                onFavoriteClick = ::toggleFavorite
                            )

                            item.id?.let { seriesId ->
                                SeasonsSection(
                                    series = item,
                                    seriesId = seriesId,
                                    mediaRepository = mediaRepository,
                                    seerrRepository = seerrRepository,
                                    activeServerId = activeServerId,
                                    refreshKey = seerrRequestState.seasonRefreshKey,
                                    loadingSeasonNumber = seerrRequestState.pendingSeasonRequestNumber
                                        ?.takeIf { seerrRequestState.isBusy },
                                    onSeasonRequest = { seasonNumber ->
                                        seerrRequestState.onLoadRequestOptions(seasonNumber)
                                    },
                                    onSeasonClick = onSeasonClick
                                )
                            }
                        }

                        CastCrewSection(
                            item = item,
                            mediaRepository = mediaRepository,
                            onPersonClick = onPersonClick
                        )

                        Recommendations(
                            item = item,
                            directors = directors,
                            isSeerDetail = isSeerDetail,
                            activeServerId = activeServerId,
                            mediaRepository = mediaRepository,
                            seerrRepository = seerrRepository,
                            onItemClick = onSimilarItemClick
                        )
                    }
                }
            }
        }
    }

    Dialogs(
        seriesStorageSelectionDialogState = seriesStorageSelectionDialogState,
        downloadErrorDialogMessage = downloadErrorDialogMessage,
        seerrRequestState = seerrRequestState,
        itemName = item.name?.takeIf { it.isNotBlank() } ?: "Unknown",
        backdropImageUrl = backdropImageUrl ?: item.backdropImageUrl ?: item.imageUrl,
        onDismissSeriesSelection = {
            seriesStorageSelectionDialogState = null
        },
        onConfirmSeriesSelection = { selectedIds ->
            seriesStorageSelectionDialogState?.let { dialogState ->
                val selectedEpisodes = selectedIds
                    .flatMap { seasonId -> dialogState.episodesBySeasonId[seasonId].orEmpty() }
                    .distinctBy { it.id }
                seriesStorageSelectionDialogState = null
                coroutineScope.launch {
                    seriesQueueInProgress = true
                    try {
                        downloadRepository.enqueueEpisodeDownloads(selectedEpisodes)
                            .onFailure { throwable ->
                                downloadErrorDialogMessage =
                                    downloadFailure(rawMessage = throwable.message)
                            }
                    } finally {
                        seriesQueueInProgress = false
                    }
                }
            }
        },
        onDismissDownloadError = {
            downloadErrorDialogMessage = null
        }
    )

}

@Composable
private fun Dialogs(
    seriesStorageSelectionDialogState: SeriesSeasonSelectionDialogState?,
    downloadErrorDialogMessage: String?,
    seerrRequestState: SeerrRequestUiState,
    itemName: String,
    backdropImageUrl: String?,
    onDismissSeriesSelection: () -> Unit,
    onConfirmSeriesSelection: (Set<String>) -> Unit,
    onDismissDownloadError: () -> Unit
) {
    seriesStorageSelectionDialogState?.let { dialogState ->
        DownloadDialog(
            title = "Choose Seasons",
            subtitle = "Pick seasons to download. Selected total must fit available storage.",
            availableBytes = dialogState.availableBytes,
            options = dialogState.options,
            initialSelection = dialogState.options.map { it.id }.toSet(),
            confirmLabel = "Download Seasons",
            onDismiss = onDismissSeriesSelection,
            onConfirm = onConfirmSeriesSelection
        )
    }

    downloadErrorDialogMessage?.let { message ->
        FailureDialog(
            message = message,
            onDismiss = onDismissDownloadError
        )
    }

    SeerrRequestDialogs(
        state = seerrRequestState,
        itemName = itemName,
        backdropImageUrl = backdropImageUrl
    )
}