package com.jellycine.app.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.shared.R
import com.jellycine.shared.preferences.Preferences
import com.jellycine.shared.util.image.JellyfinPosterImage
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.UserItemDataDto
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.detail.CodecUtils
import com.jellycine.shared.ui.components.common.activeDetailMediaSources
import com.jellycine.shared.ui.components.common.buildInlineText
import com.jellycine.shared.ui.components.common.buildLocalVersionEntries
import com.jellycine.shared.ui.components.common.selectedVideoOption
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.shared.playback.UserDataRefreshEvent
import com.jellycine.shared.playback.UserDataRefreshSignals
import java.util.Locale
import kotlinx.coroutines.launch


@Composable
fun DetailContent(
    item: BaseItemDto,
    isLoading: Boolean = false,
    forceMergeVersions: Boolean = false,
    trackSelectionSyncVersion: Int = 0,
    onBackPressed: () -> Unit = {},
    onPlayClick: (Int?, Int?) -> Unit = { _, _ -> },
    onPreferredStreamIndexesChanged: (Int?, Int?) -> Unit = { _, _ -> },
    onSimilarItemClick: (String) -> Unit = {},
    onVersionItemSelected: (String) -> Unit = {},
    onPersonClick: (String) -> Unit = {},
    onSeasonClick: (String, String, String?, String?, String?) -> Unit = { _, _, _, _, _ -> }
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val playerPreferences = remember { PlayerPreferences(context) }
    val preferences = remember { Preferences(context) }
    val coroutineScope = rememberCoroutineScope()
    var userProfileImageUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val user = runCatching { mediaRepository.getCurrentUser().getOrNull() }.getOrNull()
        userProfileImageUrl = runCatching {
            mediaRepository.getUserProfileImageUrl(user?.primaryImageTag)
        }.getOrNull()
    }
    val userDataRefreshEvent by UserDataRefreshSignals.refreshEvent.collectAsState()
    val mergeVersionsEnabled by preferences.MergeVersionsEnabled()
        .collectAsState(initial = preferences.isMergeVersionsEnabled())
    val shouldMergeVersions = forceMergeVersions || mergeVersionsEnabled
    val isEpisode = item.type == "Episode"
    val isSeries = item.type == "Series"
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
    var logoImageUrl by remember(item.id) { mutableStateOf<String?>(null) }
    var logoResolved by remember(item.id) { mutableStateOf(false) }
    var logoLookup by remember(item.id) { mutableStateOf(true) }
    var logoLoadError by remember(item.id) { mutableStateOf(false) }
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
    var selectedAudio by rememberSaveable(item.id, trackSelectionSyncVersion) { mutableStateOf(initialAudioOption) }
    var selectedSubtitle by rememberSaveable(item.id, trackSelectionSyncVersion) { mutableStateOf(initialSubtitleOption) }
    val runtimeTicks = item.runTimeTicks
    val playbackPositionTicks = item.userData?.playbackPositionTicks ?: 0L
    val isPartiallyWatched = runtimeTicks != null && playbackPositionTicks > 0L && playbackPositionTicks < runtimeTicks
    val playButtonText = if (isPartiallyWatched) {
        val remainingTicks = (runtimeTicks - playbackPositionTicks).coerceAtLeast(0L)
        "${CodecUtils.formatRuntime(remainingTicks)} left"
    } else {
        "Play"
    }
    val logoFallbackTitle = if (isEpisode) {
        item.seriesName?.takeIf { it.isNotBlank() }
            ?: item.name?.takeIf { it.isNotBlank() }
            ?: "Unknown"
    } else {
        item.name?.takeIf { it.isNotBlank() } ?: "Unknown"
    }
    val showTitleFallback = !isLoading && !logoLookup && (logoImageUrl.isNullOrBlank() || logoLoadError)
    val genresText = remember(item.genres) {
        item.genres?.takeIf { it.isNotEmpty() }?.joinToString(", ")
    }
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
            if (isSeries) {
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
    val directors = remember(item.people) {
        item.people?.filter { person ->
            listOf(person.role, person.type).any { field ->
                field?.contains("Director", ignoreCase = true) == true
            }
        }.orEmpty()
    }
    fun toggleFavorite() {
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
        val currentItemId = item.id ?: return
        val targetState = !isWatched
        coroutineScope.launch {
            val result = if (isSeries) {
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

    LaunchedEffect(item.id) {
        if (item.id == null) {
            logoLookup = false
            return@LaunchedEffect
        }
        heroImageCandidates = heroImageCandidates(
            item = item,
            mediaRepository = mediaRepository
        )
        heroImageIndex = 0

        if (logoResolved) {
            logoLookup = false
            return@LaunchedEffect
        }

        logoLookup = true
        logoLoadError = false
        try {
            val nextLogoImageUrl = logoImage(
                item = item,
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
                    { it.name.orEmpty() },
                    { it.id.orEmpty() }
                )
            )
            .withUserDataRefresh(userDataRefreshEvent)
    }

    LaunchedEffect(item.id, item.type) {
        if (item.type != "BoxSet" || item.id.isNullOrBlank()) {
            boxSetItems = emptyList()
            return@LaunchedEffect
        }
        val result = mediaRepository.getUserItems(
            parentId = item.id!!,
            includeItemTypes = "Movie",
            sortBy = "SortName",
            sortOrder = "Ascending",
            fields = "Genres,CommunityRating,ProductionYear,Overview,UserData,People",
            recursive = true
        )
        boxSetItems = result.getOrNull()?.items.orEmpty()
    }

    LaunchedEffect(item.id, item.type, shouldMergeVersions) {
        val supportsLocalVersions = item.type.equals("Movie", ignoreCase = true) ||
            item.type.equals("Episode", ignoreCase = true)
        if (
            !shouldMergeVersions ||
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
    val subtitleOptions = remember(effectiveMediaStreams) { buildSubtitleOptions(effectiveMediaStreams) }
    val defaultSubtitleOption = remember(effectiveMediaStreams) { buildDefaultSubtitleOption(effectiveMediaStreams) }
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

    val playFocusRequester = remember { FocusRequester() }
    val favoriteFocusRequester = remember { FocusRequester() }
    val watchedFocusRequester = remember { FocusRequester() }
    val profileFocusRequester = remember { FocusRequester() }
    var contentPanelPage by remember { mutableStateOf(0) }
    val showContentPanel = contentPanelPage > 0

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        JellyfinPosterImage(
            imageUrl = backdropImageUrl,
            contentDescription = item.name,
            modifier = Modifier.fillMaxSize(),
            context = context,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            onErrorStateChange = { hasError ->
                if (
                    hasError &&
                    backdropImageUrl == heroImageCandidates.getOrNull(heroImageIndex) &&
                    heroImageIndex < heroImageCandidates.lastIndex
                ) {
                    heroImageIndex += 1
                }
            }
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Black.copy(alpha = 0.35f),
                            0.20f to Color.Black.copy(alpha = 0.20f),
                            0.45f to Color.Black.copy(alpha = 0.04f),
                            1.0f to Color.Transparent
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            0.72f to Color.Black.copy(alpha = 0.22f),
                            0.88f to Color.Black.copy(alpha = 0.55f),
                            1.0f to Color.Black.copy(alpha = 0.80f)
                        )
                    )
                )
        )

        // User profile avatar
        var profileFocused by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 24.dp, end = 32.dp)
                .size(38.dp)
                .clip(CircleShape)
                .background(
                    if (profileFocused) Color.White.copy(alpha = 0.3f)
                    else Color.White.copy(alpha = 0.12f)
                )
                .focusRequester(profileFocusRequester)
                .onFocusChanged { profileFocused = it.isFocused }
                .focusProperties {
                    down = if (isSeries) watchedFocusRequester else playFocusRequester
                }
                .focusable(),
            contentAlignment = Alignment.Center
        ) {
            if (!userProfileImageUrl.isNullOrBlank()) {
                JellyfinPosterImage(
                    imageUrl = userProfileImageUrl,
                    contentDescription = "User",
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                    context = context,
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = "User",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = !showContentPanel,
            modifier = Modifier.align(Alignment.BottomStart),
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(200))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .padding(start = 40.dp, bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                    if (!genresText.isNullOrBlank()) {
                        Text(
                            text = genresText.replace(", ", " • "),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!logoImageUrl.isNullOrBlank() && !logoLoadError) {
                        JellyfinPosterImage(
                            imageUrl = logoImageUrl,
                            contentDescription = item.name,
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(56.dp),
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
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            lineHeight = 28.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isEpisode) {
                        episodeHeaderText?.let { header ->
                            Text(
                                text = header,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item.productionYear?.let { year ->
                            Text(
                                text = year.toString(),
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        item.runTimeTicks?.let { ticks ->
                            Text(
                                text = "• ${CodecUtils.formatRuntime(ticks)}",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        val officialRatingLabel = item.officialRating?.takeIf { it.isNotBlank() } ?: "NR"
                        Surface(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(3.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = officialRatingLabel,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }

                    val overviewText = item.overview?.takeIf { it.isNotBlank() }
                    if (overviewText != null) {
                        Text(
                            text = overviewText,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = Color.White,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (directors.isNotEmpty()) {
                            DirectorCreditRow(
                                label = "Director:",
                                directors = directors,
                                onPersonClick = onPersonClick
                            )
                        }

                        item.communityRating?.let { rating ->
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.1f", rating),
                                    fontSize = 18.sp,
                                    color = Color(0xFFFFC107),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "/10",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(bottom = 1.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TrackSection(
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

                    if (isSeries) {
                        SeriesHeroActions(
                            isFavorite = isFavorite,
                            isWatched = isWatched,
                            watchedFocusRequester = watchedFocusRequester,
                            favoriteFocusRequester = favoriteFocusRequester,
                            onWatchedClick = ::toggleWatched,
                            onFavoriteClick = ::toggleFavorite,
                            onDownPressed = { contentPanelPage = 1 }
                        )
                    } else {
                        DetailHeroActions(
                            playButtonText = playButtonText,
                            isFavorite = isFavorite,
                            playFocusRequester = playFocusRequester,
                            favoriteFocusRequester = favoriteFocusRequester,
                            onPlayClick = {
                                val (selectedAudioStreamIndex, selectedSubtitleStreamIndex) =
                                    persistTrackSelection(
                                        audioOption = selectedAudio,
                                        subtitleOption = selectedSubtitle
                                    )
                                onPlayClick(selectedAudioStreamIndex, selectedSubtitleStreamIndex)
                            },
                            onFavoriteClick = ::toggleFavorite,
                            onDownPressed = { contentPanelPage = 1 }
                        )
                    }
                }
        }

        AnimatedVisibility(
            visible = showContentPanel,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(200))
        ) {
            BackHandler {
                contentPanelPage = 0
            }

            when {
                isSeries && contentPanelPage == 1 -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.92f))
                            .padding(horizontal = 48.dp, vertical = 32.dp)
                    ) {
                        item.id?.let { seriesId ->
                            SeasonsSection(
                                seriesId = seriesId,
                                mediaRepository = mediaRepository,
                                onEpisodeClick = onSimilarItemClick,
                                onDismiss = { contentPanelPage = 0 },
                                onNextSection = { contentPanelPage = 2 }
                            )
                        }
                    }
                }

                isSeries && contentPanelPage >= 2 -> {
                    val castListState = rememberLazyListState()
                    val castIsAtTop = remember {
                        derivedStateOf {
                            castListState.firstVisibleItemIndex == 0 &&
                                castListState.firstVisibleItemScrollOffset == 0
                        }
                    }
                    LazyColumn(
                        state = castListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.92f))
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp && castIsAtTop.value) {
                                    contentPanelPage = 1
                                    true
                                } else {
                                    false
                                }
                            },
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!item.people.isNullOrEmpty()) {
                            item(key = "cast") {
                                BoxSetCastRow(
                                    people = item.people!!.filter { !it.name.isNullOrBlank() },
                                    mediaRepository = mediaRepository,
                                    onPersonClick = onPersonClick
                                )
                            }
                        }

                        item(key = "recs") {
                            Recommendations(
                                item = item,
                                directors = directors,
                                mediaRepository = mediaRepository,
                                onItemClick = onSimilarItemClick
                            )
                        }
                    }
                }

                else -> {
                    val contentListState = rememberLazyListState()
                    val isAtTop = remember {
                        derivedStateOf {
                            contentListState.firstVisibleItemIndex == 0 &&
                                contentListState.firstVisibleItemScrollOffset == 0
                        }
                    }
                    LazyColumn(
                        state = contentListState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.90f))
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp && isAtTop.value) {
                                    contentPanelPage = 0
                                    true
                                } else {
                                    false
                                }
                            },
                        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isEpisode && !item.people.isNullOrEmpty()) {
                            item(key = "episode_cast") {
                                BoxSetCastRow(
                                    people = item.people!!.filter { !it.name.isNullOrBlank() },
                                    mediaRepository = mediaRepository,
                                    onPersonClick = onPersonClick
                                )
                            }
                        }

                        if (isEpisode && moreFromSeasonEpisodes.isNotEmpty()) {
                            item(key = "more_from_season") {
                                MoreFromSeasonSection(
                                    episodes = moreFromSeasonEpisodes,
                                    mediaRepository = mediaRepository,
                                    title = moreFromSeasonTitle,
                                    onEpisodeClick = onSimilarItemClick
                                )
                            }
                        }

                        if (item.type == "BoxSet" && boxSetItems.isNotEmpty()) {
                            val boxSetCast = boxSetItems.flatMap { it.people.orEmpty() }
                                .filter { it.type == "Actor" && !it.id.isNullOrBlank() }
                                .distinctBy { it.id }
                            if (boxSetCast.isNotEmpty()) {
                                item(key = "boxset_cast") {
                                    BoxSetCastRow(
                                        people = boxSetCast,
                                        mediaRepository = mediaRepository,
                                        onPersonClick = onPersonClick
                                    )
                                }
                            }
                            item(key = "boxset_items") {
                                BoxSetItemsSection(
                                    items = boxSetItems,
                                    mediaRepository = mediaRepository,
                                    onItemClick = onSimilarItemClick
                                )
                            }
                        }

                        if (!item.people.isNullOrEmpty() && item.type != "BoxSet" && item.type != "Series" && !isEpisode) {
                            item(key = "cast") {
                                BoxSetCastRow(
                                    people = item.people!!.filter { !it.name.isNullOrBlank() },
                                    mediaRepository = mediaRepository,
                                    onPersonClick = onPersonClick
                                )
                            }
                        }

                        item(key = "recs") {
                            Recommendations(
                                item = item,
                                directors = directors,
                                mediaRepository = mediaRepository,
                                onItemClick = onSimilarItemClick
                            )
                        }
                    }
                }
            }
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