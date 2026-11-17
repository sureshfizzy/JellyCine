package com.jellycine.ui.screens.detail

import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.AuthRepository
import com.jellycine.util.formatDecimal
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.resources.Res
import com.jellycine.resources.detail_file_size_under_1_mb
import com.jellycine.resources.detail_video_fallback
import com.jellycine.resources.ic_dolby_atmos
import com.jellycine.resources.ic_dolby_logo
import com.jellycine.resources.ic_spatial_audio
import com.jellycine.preferences.Preferences
import com.jellycine.util.image.JellyfinPosterImage
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaStream
import com.jellycine.data.model.UserItemDataDto
import com.jellycine.detail.CodecUtils
import com.jellycine.ui.components.common.activeDetailMediaSources
import com.jellycine.ui.components.common.buildInlineText
import com.jellycine.ui.components.common.buildLocalVersionEntries
import com.jellycine.ui.components.common.OverviewSection
import com.jellycine.ui.components.common.WatchedActionButton
import com.jellycine.ui.components.common.selectedVideoOption
import com.jellycine.ui.components.common.BackButton
import com.jellycine.ui.components.common.CompactTopChip
import com.jellycine.ui.components.common.CompactTopLogo
import com.jellycine.ui.components.common.compactHeaderLogo
import com.jellycine.playback.UserDataRefreshEvent
import com.jellycine.playback.UserDataRefreshSignals
import com.jellycine.ui.components.common.DetailBackdropHero
import com.jellycine.ui.components.common.DetailBackdropHeroStyle
import com.jellycine.ui.components.common.DetailHeroCastButtonOverlay
import com.jellycine.ui.components.common.containerHeightDp
import com.jellycine.ui.components.common.containerWidthDp
import com.jellycine.ui.components.common.detailActionWidth
import com.jellycine.ui.components.common.isTabletDetailLayout
import com.jellycine.ui.components.common.isTabletLayout
import com.jellycine.ui.components.common.rememberCompactProgress
import kotlinx.coroutines.launch


@Composable
fun DetailContent(
    item: BaseItemDto,
    mediaRepository: MediaRepository,
    authRepository: AuthRepository,
    preferences: Preferences,
    platform: DetailPlatform,
    isLoading: Boolean = false,
    forceMergeVersions: Boolean = false,
    trackSelectionSyncVersion: Int = 0,
    onBackPressed: () -> Unit = {},
    onPlayClick: (Int?, Int?) -> Unit = { _, _ -> },
    onRemoteTrailerClick: (String, String?) -> Unit = { _, _ -> },
    onPreferredStreamIndexesChanged: (Int?, Int?) -> Unit = { _, _ -> },
    onSimilarItemClick: (String) -> Unit = {},
    onEpisodeClick: (String) -> Unit = onSimilarItemClick,
    onVersionItemSelected: (String) -> Unit = {},
    onPersonClick: (String) -> Unit = {},
    onCastButtonClick: () -> Unit = {},
    onSeriesOverviewClick: (String) -> Unit = {},
    onSeasonClick: (String, String, String?, String?, String?) -> Unit = { _, _, _, _, _ -> }
) {
    val coroutineScope = rememberCoroutineScope()
    val userDataRefreshEvent by UserDataRefreshSignals.refreshEvent.collectAsState()
    val activeServerId by authRepository.getActiveServerId()
        .collectAsState(initial = authRepository.getActiveSessionSnapshot().activeServerId)
    val screenWidthDp = containerWidthDp()
    val screenHeightDp = containerHeightDp()
    val isWidescreenLayout = isTabletLayout(screenWidthDp)
    val useTabletBackdropLayout = isTabletDetailLayout(
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp
    )
    val mergeVersionsEnabled by preferences.MergeVersionsEnabled()
        .collectAsState(initial = preferences.isMergeVersionsEnabled())
    val shouldMergeVersions = forceMergeVersions || mergeVersionsEnabled
    val showReviewsEnabled by preferences.ShowReviewsEnabled()
        .collectAsState(initial = preferences.isShowReviewsEnabled())
    val metadataScrollState = rememberScrollState()
    val detailListState = rememberLazyListState()
    val isEpisode = item.type == "Episode"
    val overviewInteractionSource = remember { MutableInteractionSource() }
    val overviewModifier = item.seriesId?.takeIf {
        isEpisode && it.isNotBlank()
    }?.let { seriesId ->
        Modifier.clickable(
            interactionSource = overviewInteractionSource,
            indication = null
        ) {
            onSeriesOverviewClick(seriesId)
        }
    } ?: Modifier
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

    var refreshedMediaStreams by remember(item.id) { mutableStateOf<List<MediaStream>?>(null) }
    val effectiveMediaStreams = remember(item.mediaStreams, activeMediaSources, refreshedMediaStreams) {
        refreshedMediaStreams ?: run {
            val fromSources = activeMediaSources.flatMap { it.mediaStreams.orEmpty() }
            if (fromSources.isNotEmpty()) fromSources else item.mediaStreams.orEmpty()
        }
    }
    val extras = platform.rememberExtras(
        item = item,
        mediaStreams = effectiveMediaStreams,
        onMediaStreamsRefreshed = { refreshedMediaStreams = it },
        onRemoteTrailerClick = onRemoteTrailerClick,
        onCastClick = onCastButtonClick
    )
    val isSeerDetail = extras.isSeerDetail
    val savedAudioOption = remember(item.id, effectiveMediaStreams, trackSelectionSyncVersion) {
        val currentItemId = item.id ?: return@remember null
        AudioStreamIndex(
            streams = effectiveMediaStreams,
            streamIndex = platform.trackPreferences.getPreferredAudioStreamIndex(currentItemId)
        )
    }
    val savedSubtitleOption = remember(item.id, effectiveMediaStreams, trackSelectionSyncVersion) {
        val currentItemId = item.id ?: return@remember null
        SubtitleStreamIndex(
            streams = effectiveMediaStreams,
            streamIndex = platform.trackPreferences.getPreferredSubtitleStreamIndex(currentItemId)
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
    var isFavorite by remember(item.id, item.userData?.isFavorite) {
        mutableStateOf(item.userData?.isFavorite == true)
    }
    var isWatched by remember(
        item.id,
        item.type,
        item.userData?.played,
        item.userData?.unplayedItemCount
    ) {
        mutableStateOf(
            if (item.type == "Series") {
                item.userData?.unplayedItemCount?.let { it == 0 }
                    ?: (item.userData?.played == true)
            } else {
                item.userData?.played == true
            }
        )
    }
    var moreFromSeasonEpisodes by remember(item.id, item.seriesId, item.seasonId) {
        mutableStateOf<List<BaseItemDto>>(emptyList())
    }
    var boxSetItems by remember(item.id) { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var localVersions by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
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

    fun toggleWatched() {
        if (isSeerDetail) return
        val currentItemId = item.id ?: return
        val targetState = !isWatched
        coroutineScope.launch {
            val result = if (item.type == "Series") {
                mediaRepository.setSeriesPlayedStatus(
                    seriesId = currentItemId,
                    isPlayed = targetState
                )
            } else {
                mediaRepository.setPlayedStatus(
                    itemId = currentItemId,
                    isPlayed = targetState
                )
            }
            if (result.isSuccess) {
                isWatched = targetState
                UserDataRefreshSignals.notifyUserDataChanged(
                    itemId = currentItemId,
                    played = targetState
                )
            }
        }
    }

    val selectedRemoteTrailer = remember(item.remoteTrailers) {
        item.remoteTrailers
            .orEmpty()
            .filter { trailer -> !trailer.url.isNullOrBlank() }
            .maxByOrNull { trailer ->
                val label = listOfNotNull(trailer.name, trailer.url)
                    .joinToString(separator = " ")
                    .lowercase()
                when {
                    "official trailer" in label -> 3
                    "trailer" in label -> 2
                    "official" in label -> 1
                    else -> 0
                }
            }
    }

    fun playTrailer() {
        selectedRemoteTrailer?.let { remoteTrailer ->
            onRemoteTrailerClick(
                remoteTrailer.url.orEmpty(),
                remoteTrailer.name?.takeIf { it.isNotBlank() } ?: item.name
            )
        }
    }

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
    val detailLogoCompactProgress = rememberCompactProgress(
        state = detailListState,
        compactDistance = if (useTabletBackdropLayout) 260.dp else 190.dp
    )
    val onBackdropLoadError: (Boolean) -> Unit = { hasError ->
        if (
            hasError &&
            backdropImageUrl == heroImageCandidates.getOrNull(heroImageIndex) &&
            heroImageIndex < heroImageCandidates.lastIndex
        ) {
            heroImageIndex += 1
        }
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
            val nextLogoImageUrl = platform.loadLogoImage(
                item = item,
                activeServerId = activeServerId,
                mediaRepository = mediaRepository
            )
            if (nextLogoImageUrl != logoImageUrl) {
                logoImageUrl = nextLogoImageUrl
            }
            logoResolved = true
        } finally {
            logoLookup = false
        }
    }

    val hasTrailer = selectedRemoteTrailer != null
    LaunchedEffect(
        item.id,
        item.type,
        item.seriesId,
        item.seasonId,
        item.parentIndexNumber,
        userDataRefreshEvent
    ) {
        if (!isEpisode) {
            moreFromSeasonEpisodes = emptyList()
            return@LaunchedEffect
        }

        val seriesId = item.seriesId?.takeIf { it.isNotBlank() }
        if (seriesId.isNullOrBlank()) {
            moreFromSeasonEpisodes = emptyList()
            return@LaunchedEffect
        }

        moreFromSeasonEpisodes = moreFromSeasonEpisodes.withUserDataRefresh(userDataRefreshEvent)

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
                    { it.id.orEmpty() }
                )
            )
            .distinctBy { episode ->
                episode.indexNumber?.let { number ->
                    "${episode.parentIndexNumber ?: Int.MIN_VALUE}:$number"
                } ?: "id:${episode.id.orEmpty()}"
            }
            .withUserDataRefresh(userDataRefreshEvent)
    }

    LaunchedEffect(item.id, item.type) {
        if (item.type != "BoxSet") {
            boxSetItems = emptyList()
            return@LaunchedEffect
        }
        val boxSetId = item.id?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        val result = mediaRepository.getUserItems(
            parentId = boxSetId,
            sortBy = "SortName",
            sortOrder = "Ascending",
            fields = "CommunityRating,ProductionYear,OfficialRating,Overview,Genres,People"
        )
        boxSetItems = result.getOrNull()?.items.orEmpty()
    }

    LaunchedEffect(item.id, item.type, isSeerDetail, shouldMergeVersions) {
        val supportsLocalVersions = item.type.equals("Movie", ignoreCase = true) ||
            item.type.equals("Episode", ignoreCase = true)
        if (
            !shouldMergeVersions ||
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
    val videoFallbackLabel = stringResource(Res.string.detail_video_fallback)
    val smallFileSizeLabel = stringResource(Res.string.detail_file_size_under_1_mb)
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
            platform.trackPreferences.setPreferredAudioStreamIndex(currentItemId, audioStreamIndex)
            platform.trackPreferences.setPreferredSubtitleStreamIndex(currentItemId, subtitleStreamIndex)
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
                    DetailHeroCastButtonOverlay(
                        showWatchedButton = true,
                        isWatched = isWatched,
                        onWatchedClick = ::toggleWatched,
                        castButton = { extras.HeroCastButton() }
                    )
                }
            }
        }

        LazyColumn(
            state = detailListState,
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
                            DetailHeroCastButtonOverlay(
                                showWatchedButton = true,
                                isWatched = isWatched,
                                onWatchedClick = ::toggleWatched,
                                castButton = { extras.HeroCastButton() }
                            )
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
                                    .then(overviewModifier)
                            ) {
                                if (!logoImageUrl.isNullOrBlank() && !logoLoadError) {
                                    JellyfinPosterImage(
                                        imageUrl = logoImageUrl,
                                        contentDescription = item.name,
                                        modifier = Modifier
                                            .fillMaxWidth(0.94f)
                                            .height(layout.logoContainerHeight)
                                            .align(Alignment.CenterStart)
                                            .compactHeaderLogo(detailLogoCompactProgress),
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
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = overviewModifier
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
                                        text = (formatDecimal(rating, 1)),
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

                            extras.VersionRequestBadge()

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

                        extras.CastStatusBanner()

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
                                            ) Res.drawable.ic_dolby_logo else null,
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
                                            customIcon = Res.drawable.ic_spatial_audio,
                                            customIconTint = Color(0xFF8DFFB3)
                                        )
                                    }
                                }

                                if (codecBadges.dolbyAudioBadgeText.isNotBlank()) {
                                    item {
                                        CapabilityBadge(
                                            text = codecBadges.dolbyAudioBadgeText,
                                            customIcon = if (codecBadges.hasDolbyAtmos) Res.drawable.ic_dolby_atmos else Res.drawable.ic_dolby_logo,
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

                        if (isWidescreenLayout && extras.showSeerrRequestRow) {
                            extras.SeerrRequestRow(
                                modifier = Modifier
                                    .fillMaxWidth(
                                        detailActionWidth(
                                            screenWidthDp,
                                            useTabletLayout = useTabletBackdropLayout
                                        )
                                    )
                                    .padding(top = if (hasDescriptionContent) 12.dp else 14.dp),
                                buttonHeight = detailActionButtonHeight,
                                hasTrailer = hasTrailer,
                                onTrailerClick = ::playTrailer
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

                        if (item.type != "Series" && item.type != "BoxSet" && !isSeerDetail) {
                            ActionSection(
                                screenWidthDp = screenWidthDp,
                                useTabletLayout = useTabletBackdropLayout,
                                buttonHeight = detailActionButtonHeight,
                                playButtonText = playButtonText,
                                isPartiallyWatched = isPartiallyWatched,
                                resumeProgress = resumeProgress,
                                isFavorite = isFavorite,
                                hasTrailer = hasTrailer,
                                extras = extras,
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
                                onTrailerClick = ::playTrailer,
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

                        if (!isWidescreenLayout && extras.showSeerrRequestRow) {
                            extras.SeerrRequestRow(
                                modifier = Modifier
                                    .fillMaxWidth(
                                        detailActionWidth(
                                            screenWidthDp,
                                            useTabletLayout = useTabletBackdropLayout
                                        )
                                    )
                                    .padding(top = if (hasDescriptionContent) 14.dp else 18.dp),
                                buttonHeight = detailActionButtonHeight,
                                hasTrailer = hasTrailer,
                                onTrailerClick = ::playTrailer
                            )
                        }

                        if (!isWidescreenLayout) {
                            DirectorCreditRow(
                                label = directorCreditLabel,
                                directors = directors,
                                onPersonClick = onPersonClick
                            )
                        }

                        if ((item.type == "Series" || isEpisode) && !item.people.isNullOrEmpty()) {
                            BoxSetCastRow(
                                people = item.people!!.filter { !it.name.isNullOrBlank() },
                                mediaRepository = mediaRepository,
                                onPersonClick = onPersonClick
                            )
                        }

                        if (isEpisode) {
                            MoreFromSeasonSection(
                                episodes = moreFromSeasonEpisodes,
                                mediaRepository = mediaRepository,
                                title = moreFromSeasonTitle,
                                onEpisodeClick = onEpisodeClick
                            )
                        }

                        if (item.type == "BoxSet" && boxSetItems.isNotEmpty()) {
                            val boxSetCast = remember(boxSetItems) {
                                boxSetItems.flatMap { it.people.orEmpty() }
                                    .filter { it.type == "Actor" && !it.id.isNullOrBlank() }
                                    .distinctBy { it.id }
                            }
                            if (boxSetCast.isNotEmpty()) {
                                BoxSetCastRow(
                                    people = boxSetCast,
                                    mediaRepository = mediaRepository,
                                    onPersonClick = onPersonClick
                                )
                            }
                        }

                        if (item.type == "BoxSet" && boxSetItems.isNotEmpty()) {
                            BoxSetItemsSection(
                                items = boxSetItems,
                                mediaRepository = mediaRepository,
                                onItemClick = onSimilarItemClick
                            )
                        }

                        if (item.type == "Series" && !isSeerDetail) {
                            SeriesActionSection(
                                screenWidthDp = screenWidthDp,
                                useTabletLayout = useTabletBackdropLayout,
                                buttonHeight = detailActionButtonHeight,
                                isFavorite = isFavorite,
                                hasTrailer = hasTrailer,
                                extras = extras,
                                onTrailerClick = ::playTrailer,
                                onFavoriteClick = ::toggleFavorite
                            )

                            item.id?.let { seriesId ->
                                extras.Seasons(
                                    series = item,
                                    seriesId = seriesId,
                                    mediaRepository = mediaRepository,
                                    activeServerId = activeServerId,
                                    onSeasonClick = { seasonSeriesId, seasonId, seasonName ->
                                        onSeasonClick(
                                            seasonSeriesId,
                                            seasonId,
                                            seasonName,
                                            backdropImageUrl,
                                            logoImageUrl?.takeIf { !logoLoadError }
                                        )
                                    }
                                )
                            }
                        }

                        if (!item.people.isNullOrEmpty() && item.type != "BoxSet" && item.type != "Series" && !isEpisode) {
                            BoxSetCastRow(
                                people = item.people!!.filter { !it.name.isNullOrBlank() },
                                mediaRepository = mediaRepository,
                                onPersonClick = onPersonClick
                            )
                        }

                        TrailersExtrasSection(
                            item = item,
                            isSeerDetail = isSeerDetail,
                            mediaRepository = mediaRepository,
                            onExtraClick = onRemoteTrailerClick
                        )

                        if (showReviewsEnabled) {
                            ReviewsSection(
                                item = item,
                                isSeerDetail = isSeerDetail,
                                mediaRepository = mediaRepository
                            )
                        }

                        extras.Recommendations(
                            item = item,
                            directors = directors,
                            mediaRepository = mediaRepository,
                            activeServerId = activeServerId,
                            onItemClick = onSimilarItemClick
                        )
                    }
                }
            }
        }

        BackButton(
            onClick = onBackPressed,
            modifier = Modifier.align(Alignment.TopStart)
        )

        if (!logoImageUrl.isNullOrBlank() && !logoLoadError) {
            CompactTopLogo(
                imageUrl = logoImageUrl.orEmpty(),
                contentDescription = item.name,
                progress = detailLogoCompactProgress,
                isTablet = isWidescreenLayout,
                onClick = {
                    coroutineScope.launch {
                        detailListState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 48.dp)
            )
        }

        if (!isSeerDetail) {
            DetailActionsOverlay(
                progress = detailLogoCompactProgress,
                isWatched = isWatched,
                onWatchedClick = ::toggleWatched,
                extras = extras,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }
    }

    extras.Dialogs(backdropImageUrl = backdropImageUrl ?: item.backdropImageUrl ?: item.imageUrl)
}

@Composable
private fun DetailActionsOverlay(
    progress: Float,
    isWatched: Boolean,
    onWatchedClick: () -> Unit,
    extras: DetailExtras,
    modifier: Modifier = Modifier
) {
    CompactTopChip(
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 10.dp, end = 14.dp),
        progress = progress,
        height = 40.dp,
        shape = RoundedCornerShape(20.dp),
        containerColor = Color.Black.copy(alpha = 0.42f)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp)
        ) {
            WatchedActionButton(
                isWatched = isWatched,
                onClick = onWatchedClick,
                size = 30.dp
            )
            extras.HeroCastButton(size = 30.dp)
        }
    }
}

private fun List<BaseItemDto>.withUserDataRefresh(
    event: UserDataRefreshEvent?
): List<BaseItemDto> {
    val itemId = event?.itemId?.takeIf { it.isNotBlank() } ?: return this
    val played = event.played ?: return this

    return map { episode ->
        if (episode.id != itemId) {
            episode
        } else {
            episode.copy(
                userData = (episode.userData ?: UserItemDataDto(itemId = itemId)).copy(
                    played = played,
                    playbackPositionTicks = 0L
                )
            )
        }
    }
}
