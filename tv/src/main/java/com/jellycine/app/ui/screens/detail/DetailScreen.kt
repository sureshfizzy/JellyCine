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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.shared.R
import com.jellycine.shared.util.image.JellyfinPosterImage
import com.jellycine.shared.util.image.imageTagFor
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.MediaStream
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.flow.first
import android.content.res.Configuration
import com.jellycine.app.ui.screens.player.PlayerScreen
import com.jellycine.detail.CodecUtils
import com.jellycine.shared.ui.components.common.ScreenWrapper
import com.jellycine.shared.ui.components.common.ShimmerEffect
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.shared.playback.UserDataRefreshSignals
import androidx.media3.common.util.UnstableApi
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch

private data class SeasonDetailData(
    val seriesId: String,
    val seasonId: String,
    val seasonName: String?,
    val initialHeroImageUrl: String?,
    val initialLogoImageUrl: String?
)

@UnstableApi
@Composable
fun DetailScreenContainer(
    itemId: String,
    forceMergeVersions: Boolean = false,
    onBackPressed: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToPerson: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val playerPreferences = remember { PlayerPreferences(context) }

    var item by remember { mutableStateOf<BaseItemDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPlayer by remember { mutableStateOf(false) }
    var playbackItemId by remember { mutableStateOf<String?>(null) }
    var availablePreviousEpisodeId by remember { mutableStateOf<String?>(null) }
    var availableNextEpisodeId by remember { mutableStateOf<String?>(null) }
    var preferredAudioStreamIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var preferredSubtitleStreamIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var trackSelectionSyncVersion by rememberSaveable { mutableStateOf(0) }

    // Navigation state
    var currentScreen by remember { mutableStateOf("detail") }
    var seasonDetailData by remember { mutableStateOf<SeasonDetailData?>(null) }
    var episodeDetailId by remember { mutableStateOf<String?>(null) }
    var episodeItem by remember { mutableStateOf<BaseItemDto?>(null) }
    var isEpisodeLoading by remember { mutableStateOf(false) }
    var episodeError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val userDataRefreshEvent by UserDataRefreshSignals.refreshEvent.collectAsState()

    fun startPlaybackForItem(
        targetItem: BaseItemDto?,
        fallbackItemId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?
    ) {
        val activeItemId = targetItem?.id?.takeIf { it.isNotBlank() } ?: fallbackItemId
        preferredAudioStreamIndex = audioStreamIndex
        preferredSubtitleStreamIndex = subtitleStreamIndex
        playbackItemId = activeItemId
        showPlayer = true
    }

    val handleBackNavigation: () -> Unit = {
        when {
            showPlayer -> {
                val playedItemId = playbackItemId ?: itemId
                preferredAudioStreamIndex = playerPreferences.getPreferredAudioStreamIndex(playedItemId)
                preferredSubtitleStreamIndex = playerPreferences.getPreferredSubtitleStreamIndex(playedItemId)
                trackSelectionSyncVersion += 1
                showPlayer = false
                playbackItemId = null
            }
            currentScreen == "episode" && seasonDetailData != null -> {
                currentScreen = "season"
            }
            currentScreen == "season" -> {
                currentScreen = "detail"
            }
            else -> onBackPressed()
        }
    }

    fun playEpisode(episodeId: String) {
        playbackItemId = episodeId
        availablePreviousEpisodeId = null
        availableNextEpisodeId = null
        if (currentScreen == "episode") {
            episodeDetailId = episodeId
        }
    }

    fun selectLocalVersion(selectedItemId: String) {
        val selectingEpisode = currentScreen == "episode" && episodeItem != null
        val currentItemId = if (selectingEpisode) episodeItem?.id else item?.id
        if (selectedItemId.isBlank() || selectedItemId == currentItemId) return

        scope.launch {
            if (selectingEpisode) {
                isEpisodeLoading = true
                episodeError = null
            } else {
                isLoading = true
                error = null
            }

            mediaRepository.getItemById(selectedItemId).fold(
                onSuccess = { selectedItem ->
                    if (selectingEpisode) {
                        episodeItem = selectedItem
                        isEpisodeLoading = false
                    } else {
                        item = selectedItem
                        isLoading = false
                    }
                },
                onFailure = { exception ->
                    if (selectingEpisode) {
                        episodeError = exception.message
                        isEpisodeLoading = false
                    } else {
                        error = exception.message
                        isLoading = false
                    }
                }
            )
        }
    }

    LaunchedEffect(itemId) {
        try {
            isLoading = true
            error = null

            val result = mediaRepository.getItemById(itemId)
            result.fold(
                onSuccess = { fetchedItem ->
                    item = fetchedItem
                    isLoading = false
                },
                onFailure = { exception ->
                    error = exception.message
                    isLoading = false
                }
            )
        } catch (e: Exception) {
            error = e.message
            isLoading = false
        }
    }

    LaunchedEffect(currentScreen, episodeDetailId) {
        val targetEpisodeId = episodeDetailId
        if (currentScreen != "episode" || targetEpisodeId.isNullOrBlank()) {
            return@LaunchedEffect
        }

        try {
            isEpisodeLoading = true
            episodeError = null
            episodeItem = null

            val result = mediaRepository.getItemById(targetEpisodeId)
            result.fold(
                onSuccess = { fetchedEpisode ->
                    episodeItem = fetchedEpisode
                    isEpisodeLoading = false
                },
                onFailure = { exception ->
                    episodeError = exception.message
                    episodeItem = null
                    isEpisodeLoading = false
                }
            )
        } catch (e: Exception) {
            episodeError = e.message
            episodeItem = null
            isEpisodeLoading = false
        }
    }

    LaunchedEffect(showPlayer, playbackItemId, itemId) {
        if (!showPlayer) {
            availablePreviousEpisodeId = null
            availableNextEpisodeId = null
            return@LaunchedEffect
        }

        val activePlaybackId = playbackItemId ?: itemId
        val episodeNavigationIds = mediaRepository.getEpisodeNavigationIds(activePlaybackId)
        availablePreviousEpisodeId = episodeNavigationIds.previousEpisodeId
        availableNextEpisodeId = episodeNavigationIds.nextEpisodeId
    }

    LaunchedEffect(userDataRefreshEvent) {
        val refreshEvent = userDataRefreshEvent ?: return@LaunchedEffect
        val refreshedItemId = refreshEvent.itemId ?: return@LaunchedEffect

        if (item?.id == refreshedItemId || itemId == refreshedItemId) {
            mediaRepository.getItemById(refreshedItemId).getOrNull()?.let { refreshedItem ->
                item = refreshedItem
            }
        }

        if (episodeItem?.id == refreshedItemId || episodeDetailId == refreshedItemId) {
            mediaRepository.getItemById(refreshedItemId).getOrNull()?.let { refreshedEpisode ->
                episodeItem = refreshedEpisode
            }
        }
    }

    BackHandler {
        handleBackNavigation()
    }

    if (error != null) {
        LaunchedEffect(Unit) {
            onBackPressed()
        }
    } else {
        if (showPlayer) {
            val activePlaybackId = playbackItemId ?: itemId
            val initialPlaybackItemDetails = when (activePlaybackId) {
                item?.id -> item
                episodeItem?.id -> episodeItem
                else -> null
            }
            PlayerScreen(
                mediaId = activePlaybackId,
                initialItemDetails = initialPlaybackItemDetails,
                preferredAudioStreamIndex = preferredAudioStreamIndex,
                preferredSubtitleStreamIndex = preferredSubtitleStreamIndex,
                onPreferredStreamIndexesChanged = { audioStreamIndex, subtitleStreamIndex ->
                    preferredAudioStreamIndex = audioStreamIndex
                    preferredSubtitleStreamIndex = subtitleStreamIndex
                },
                onBackPressed = {
                    val playedItemId = playbackItemId ?: itemId
                    preferredAudioStreamIndex = playerPreferences.getPreferredAudioStreamIndex(playedItemId)
                    preferredSubtitleStreamIndex = playerPreferences.getPreferredSubtitleStreamIndex(playedItemId)
                    trackSelectionSyncVersion += 1
                    showPlayer = false
                    playbackItemId = null
                },
                previousEpisodeId = availablePreviousEpisodeId,
                onWatchPreviousEpisode = ::playEpisode,
                nextEpisodeId = availableNextEpisodeId,
                onWatchNextEpisode = ::playEpisode,
                onPlaybackCompleted = { completedItemId ->
                    scope.launch {
                        val nextEpisodeId = mediaRepository.getNextEpisodeId(completedItemId) ?: return@launch
                        playEpisode(nextEpisodeId)
                    }
                }
            )
        } else {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(
                        animationSpec = tween(400, easing = FastOutSlowInEasing)
                    ) togetherWith fadeOut(
                        animationSpec = tween(300, easing = LinearOutSlowInEasing)
                    )
                },
                label = "screen_navigation"
            ) { screen ->
                when (screen) {
                    "detail" -> {
                        ScreenWrapper(isActive = true) {
                            val currentItem = item
                            if (currentItem != null) {
                                DetailScreen(
                                    item = currentItem,
                                    isLoading = isLoading,
                                    forceMergeVersions = forceMergeVersions,
                                    trackSelectionSyncVersion = trackSelectionSyncVersion,
                                    onBackPressed = handleBackNavigation,
                                    onPlayClick = { audioStreamIndex, subtitleStreamIndex ->
                                        startPlaybackForItem(
                                            targetItem = currentItem,
                                            fallbackItemId = itemId,
                                            audioStreamIndex = audioStreamIndex,
                                            subtitleStreamIndex = subtitleStreamIndex
                                        )
                                    },
                                    onPreferredStreamIndexesChanged = { audioStreamIndex, subtitleStreamIndex ->
                                        preferredAudioStreamIndex = audioStreamIndex
                                        preferredSubtitleStreamIndex = subtitleStreamIndex
                                    },
                                    onSimilarItemClick = { selectedItemId ->
                                        onNavigateToDetail(selectedItemId)
                                    },
                                    onVersionItemSelected = ::selectLocalVersion,
                                    onPersonClick = { personId ->
                                        onNavigateToPerson(personId)
                                    },
                                    onSeasonClick = { seriesId, seasonId, seasonName, heroImageUrl, logoImageUrl ->
                                        seasonDetailData = SeasonDetailData(
                                            seriesId = seriesId,
                                            seasonId = seasonId,
                                            seasonName = seasonName,
                                            initialHeroImageUrl = heroImageUrl,
                                            initialLogoImageUrl = logoImageUrl
                                        )
                                        currentScreen = "season"
                                    }
                                )
                            } else {
                                DetailScreenSkeleton(onBackPressed = handleBackNavigation)
                            }
                        }
                    }

                    "season" -> {
                        seasonDetailData?.let { seasonData ->
                            ScreenWrapper(isActive = true) {
                                SeasonDetailScreen(
                                    seriesId = seasonData.seriesId,
                                    seasonId = seasonData.seasonId,
                                    seasonName = seasonData.seasonName,
                                    initialHeroImageUrl = seasonData.initialHeroImageUrl,
                                    initialLogoImageUrl = seasonData.initialLogoImageUrl,
                                    onBackPressed = handleBackNavigation,
                                    onEpisodeClick = { episodeId ->
                                        episodeDetailId = episodeId
                                        currentScreen = "episode"
                                    }
                                )
                            }
                        }
                    }

                    "episode" -> {
                        episodeDetailId?.let { episodeId ->
                            ScreenWrapper(isActive = true) {
                                when {
                                    episodeError != null -> {
                                        LaunchedEffect(episodeError) {
                                            if (seasonDetailData != null) {
                                                currentScreen = "season"
                                                episodeError = null
                                            } else {
                                                onBackPressed()
                                            }
                                        }
                                    }

                                    episodeItem != null -> {
                                        DetailScreen(
                                            item = episodeItem!!,
                                            isLoading = isEpisodeLoading,
                                            trackSelectionSyncVersion = trackSelectionSyncVersion,
                                            onBackPressed = handleBackNavigation,
                                            onPlayClick = { audioStreamIndex, subtitleStreamIndex ->
                                                startPlaybackForItem(
                                                    targetItem = episodeItem,
                                                    fallbackItemId = episodeId,
                                                    audioStreamIndex = audioStreamIndex,
                                                    subtitleStreamIndex = subtitleStreamIndex
                                                )
                                            },
                                            onPreferredStreamIndexesChanged = { audioStreamIndex, subtitleStreamIndex ->
                                                preferredAudioStreamIndex = audioStreamIndex
                                                preferredSubtitleStreamIndex = subtitleStreamIndex
                                            },
                                            onSimilarItemClick = { selectedItemId ->
                                                onNavigateToDetail(selectedItemId)
                                            },
                                            onVersionItemSelected = ::selectLocalVersion,
                                            onPersonClick = { personId ->
                                                onNavigateToPerson(personId)
                                            },
                                            onSeasonClick = { seriesId, seasonId, seasonName, heroImageUrl, logoImageUrl ->
                                                seasonDetailData = SeasonDetailData(
                                                    seriesId = seriesId,
                                                    seasonId = seasonId,
                                                    seasonName = seasonName,
                                                    initialHeroImageUrl = heroImageUrl,
                                                    initialLogoImageUrl = logoImageUrl
                                                )
                                                currentScreen = "season"
                                            }
                                        )
                                    }

                                    else -> {
                                        DetailScreenSkeleton(onBackPressed = handleBackNavigation)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
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
    DetailContent(
        item = item,
        isLoading = isLoading,
        forceMergeVersions = forceMergeVersions,
        trackSelectionSyncVersion = trackSelectionSyncVersion,
        onBackPressed = onBackPressed,
        onPlayClick = onPlayClick,
        onPreferredStreamIndexesChanged = onPreferredStreamIndexesChanged,
        onSimilarItemClick = onSimilarItemClick,
        onVersionItemSelected = onVersionItemSelected,
        onPersonClick = onPersonClick,
        onSeasonClick = onSeasonClick
    )
}

@Composable
internal fun MoreFromSeasonSection(
    episodes: List<BaseItemDto>,
    mediaRepository: MediaRepository,
    title: String,
    onEpisodeClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (episodes.isEmpty()) return

    Column(
        modifier = modifier.padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            itemsIndexed(
                items = episodes,
                key = { index, episode ->
                    "${episode.id ?: "${episode.name}-${episode.indexNumber}"}_$index"
                }
            ) { _, episode ->
                EpisodePreviewCard(
                    episode = episode,
                    mediaRepository = mediaRepository,
                    cardWidth = 224.dp,
                    thumbnailHeight = 126.dp,
                    onClick = {
                        episode.id?.let(onEpisodeClick)
                    }
                )
            }
        }
    }
}

internal suspend fun heroImageCandidates(
    item: BaseItemDto,
    mediaRepository: MediaRepository
): List<String> {
    val itemId = item.id ?: return emptyList()
    val seriesId = item.seriesId
    val candidates = mutableListOf<String>()

    fun addCandidate(url: String?) {
        if (!url.isNullOrBlank() && !candidates.contains(url)) {
            candidates.add(url)
        }
    }

    if (item.type == "Episode") {
        addCandidate(mediaRepository.getBackdropImageUrl(
            itemId = itemId,
            width = 1920,
            height = 1080,
            quality = 100,
            enableImageEnhancers = false,
            imageTag = item.imageTagFor(
                imageType = "Backdrop",
                targetItemId = itemId
            )
        ).first())

        addCandidate(mediaRepository.getImageUrl(
            itemId = itemId,
            imageType = "Primary",
            width = 1920,
            height = 1080,
            quality = 100,
            enableImageEnhancers = false,
            imageTag = item.imageTagFor(
                imageType = "Primary",
                targetItemId = itemId
            )
        ).first())

        addCandidate(mediaRepository.getImageUrl(
            itemId = itemId,
            imageType = "Thumb",
            width = 1920,
            height = 1080,
            quality = 100,
            enableImageEnhancers = false,
            imageTag = item.imageTagFor(
                imageType = "Thumb",
                targetItemId = itemId
            )
        ).first())

        if (!seriesId.isNullOrBlank()) {
            addCandidate(mediaRepository.getBackdropImageUrl(
                itemId = seriesId,
                width = 1920,
                height = 1080,
                quality = 100,
                enableImageEnhancers = false,
                imageTag = item.imageTagFor(
                    imageType = "Backdrop",
                    targetItemId = seriesId
                )
            ).first())
            addCandidate(mediaRepository.getImageUrl(
                itemId = seriesId,
                imageType = "Primary",
                width = 1920,
                height = 1080,
                quality = 100,
                enableImageEnhancers = false,
                imageTag = item.imageTagFor(
                    imageType = "Primary",
                    targetItemId = seriesId
                )
            ).first())
        }
    } else {
        addCandidate(mediaRepository.getBackdropImageUrl(
            itemId = itemId,
            width = 1200,
            height = 675,
            quality = 95,
            imageTag = item.imageTagFor(
                imageType = "Backdrop",
                targetItemId = itemId
            )
        ).first())

        addCandidate(mediaRepository.getImageUrl(
            itemId = itemId,
            imageType = "Primary",
            width = 1200,
            height = 675,
            quality = 95,
            imageTag = item.imageTagFor(
                imageType = "Primary",
                targetItemId = itemId
            )
        ).first())
    }

    return candidates
}

internal suspend fun logoImage(
    item: BaseItemDto,
    mediaRepository: MediaRepository
): String? {
    val logoItemId = item.logoItemId() ?: return null
    val logoImageTag = item.imageTagFor(
        imageType = "Logo",
        targetItemId = logoItemId
    ) ?: return mediaRepository.getTmdbLogoUrl(item)

    return mediaRepository.getImageUrl(
        itemId = logoItemId,
        imageType = "Logo",
        width = 1200,
        quality = 95,
        imageTag = logoImageTag
    ).first()
}

internal fun BaseItemDto.logoItemId(): String? {
    return if (type == "Episode") {
        seriesId ?: id
    } else {
        id
    }
}

internal fun episodeHeaderText(item: BaseItemDto): String? {
    if (item.type != "Episode") return null
    val title = item.name?.takeIf { it.isNotBlank() } ?: "Unknown"
    val season = item.parentIndexNumber
    val episode = item.indexNumber
    return when {
        season != null && episode != null -> "S${season}:E${episode} - $title"
        episode != null -> "Episode $episode - $title"
        else -> title
    }
}


@Composable
internal fun SeasonsSection(
    seriesId: String,
    mediaRepository: MediaRepository,
    onSeasonClick: (String, String, String?) -> Unit = { _, _, _ -> }
) {
    var seasons by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isLoadingSeasons by remember { mutableStateOf(true) }

    // Load seasons
    LaunchedEffect(seriesId) {
        isLoadingSeasons = true
        try {
            val result = mediaRepository.getSeasons(seriesId)
            result.fold(
                onSuccess = { seasonList ->
                    seasons = seasonList.sortedBy { it.indexNumber ?: 0 }
                    isLoadingSeasons = false
                },
                onFailure = {
                    isLoadingSeasons = false
                }
            )
        } catch (e: Exception) {
            isLoadingSeasons = false
        }
    }

    Column(
        modifier = Modifier.padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Seasons Section
        Text(
            text = "Seasons",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        when {
            isLoadingSeasons -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(3) {
                        SeasonCardSkeleton()
                    }
                }
            }
            seasons.isNotEmpty() -> {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    items(seasons) { season ->
                        SeasonCard(
                            season = season,
                            mediaRepository = mediaRepository,
                            onClick = {
                                season.id?.let { seasonId ->
                                    onSeasonClick(seriesId, seasonId, season.name)
                                }
                            },
                            onPreviewClick = {
                                // TODO: Implement season preview functionality
                                season.id?.let { seasonId ->
                                    onSeasonClick(seriesId, seasonId, season.name)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
fun DetailScreenPreview() {
    MaterialTheme {
        val mockItem = BaseItemDto(
            id = "mock-id",
            name = "Seal Team",
            overview = "After his best friend is killed in a shark attack, Quinn, a lovable " +
                "yet tenacious seal assembles a SEAL TEAM to fight back against a gang of sharks.",
            productionYear = 2021,
            runTimeTicks = 6000000000L, // 1h 40m
            communityRating = 7.6f,
            officialRating = "TV-Y7",
            genres = listOf("Animation", "Family", "Adventure"),
            userData = null,
            people = null,
            studios = null,
            mediaStreams = listOf(
                MediaStream(
                    type = "Video",
                    codec = "h264",
                    width = 1920,
                    height = 1080
                ),
                MediaStream(
                    type = "Audio",
                    codec = "aac",
                    channels = 2,
                    language = "eng"
                )
            )
        )

        DetailContent(
            item = mockItem,
            onBackPressed = {},
            onPlayClick = { _, _ -> }
        )
    }
}

@Composable
fun DetailScreenSkeleton(
    onBackPressed: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp)
            ) {
                ShimmerEffect(
                    modifier = Modifier.fillMaxSize(),
                    cornerRadius = 0f
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Black.copy(alpha = 0.5f),
                                    Color.Black.copy(alpha = 0.8f),
                                    Color.Black
                                )
                            )
                        )
                )

            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
                    .offset(y = (-58).dp)
            ) {
                ShimmerEffect(
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height(62.dp),
                    cornerRadius = 10f
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ShimmerEffect(modifier = Modifier.width(56.dp).height(16.dp), cornerRadius = 8f)
                    ShimmerEffect(modifier = Modifier.width(42.dp).height(16.dp), cornerRadius = 8f)
                    ShimmerEffect(modifier = Modifier.width(64.dp).height(16.dp), cornerRadius = 8f)
                    ShimmerEffect(modifier = Modifier.width(50.dp).height(18.dp), cornerRadius = 8f)
                }

                Spacer(modifier = Modifier.height(8.dp))

                ShimmerEffect(
                    modifier = Modifier.fillMaxWidth(0.78f).height(14.dp),
                    cornerRadius = 8f
                )

                Spacer(modifier = Modifier.height(14.dp))

                repeat(2) {
                    ShimmerEffect(
                        modifier = Modifier.fillMaxWidth(0.82f).height(18.dp),
                        cornerRadius = 8f
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                ShimmerEffect(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp),
                    cornerRadius = 18f
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ShimmerEffect(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        cornerRadius = 24f
                    )
                    ShimmerEffect(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp),
                        cornerRadius = 24f
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                ShimmerEffect(
                    modifier = Modifier.width(110.dp).height(22.dp),
                    cornerRadius = 10f
                )
                Spacer(modifier = Modifier.height(10.dp))
                repeat(3) {
                    ShimmerEffect(
                        modifier = Modifier
                            .fillMaxWidth(if (it == 2) 0.72f else 1f)
                            .height(14.dp),
                        cornerRadius = 8f
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}