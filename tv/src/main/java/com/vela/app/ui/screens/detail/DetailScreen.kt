package com.vela.app.ui.screens.detail

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vela.shared.R
import com.vela.shared.util.image.JellyfinPosterImage
import com.vela.shared.util.image.imageTagFor
import com.vela.data.model.BaseItemDto
import com.vela.data.model.MediaStream
import com.vela.data.repository.MediaRepository
import com.vela.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.flow.first
import android.content.res.Configuration
import com.vela.app.ui.screens.player.PlayerScreen
import com.vela.detail.CodecUtils
import com.vela.shared.ui.components.common.ScreenWrapper
import com.vela.shared.ui.components.common.ShimmerEffect
import com.vela.player.preferences.PlayerPreferences
import com.vela.shared.playback.UserDataRefreshSignals
import androidx.media3.common.util.UnstableApi
import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch


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
            currentScreen == "episode" -> {
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
                                    onSeasonClick = { _, _, _, _, _ -> }
                                )
                            } else {
                                DetailScreenSkeleton(onBackPressed = handleBackNavigation)
                            }
                        }
                    }

                    "episode" -> {
                        episodeDetailId?.let { episodeId ->
                            ScreenWrapper(isActive = true) {
                                when {
                                    episodeError != null -> {
                                        LaunchedEffect(episodeError) {
                                            currentScreen = "detail"
                                            episodeError = null
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
                                            onSeasonClick = { _, _, _, _, _ -> }
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
    onEpisodeClick: (String) -> Unit = {},
    onDismiss: () -> Unit = {},
    onNextSection: () -> Unit = {}
) {
    var seasons by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isLoadingSeasons by remember { mutableStateOf(true) }
    var selectedSeasonIndex by remember { mutableStateOf(0) }
    var episodes by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isLoadingEpisodes by remember { mutableStateOf(false) }
    val episodeListState = rememberLazyListState()
    val seasonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(seriesId) {
        isLoadingSeasons = true
        try {
            val result = mediaRepository.getSeasons(seriesId)
            result.fold(
                onSuccess = { seasonList ->
                    seasons = seasonList.sortedBy { it.indexNumber ?: 0 }
                    isLoadingSeasons = false
                },
                onFailure = { isLoadingSeasons = false }
            )
        } catch (e: Exception) {
            isLoadingSeasons = false
        }
    }

    val selectedSeason = seasons.getOrNull(selectedSeasonIndex)

    LaunchedEffect(selectedSeason?.id) {
        val seasonId = selectedSeason?.id ?: return@LaunchedEffect
        isLoadingEpisodes = true
        episodes = emptyList()
        try {
            val result = mediaRepository.getEpisodes(seriesId = seriesId, seasonId = seasonId)
            result.fold(
                onSuccess = { episodeList ->
                    episodes = episodeList.sortedBy { it.indexNumber ?: 0 }
                    isLoadingEpisodes = false
                },
                onFailure = { isLoadingEpisodes = false }
            )
        } catch (e: Exception) {
            isLoadingEpisodes = false
        }
    }

    LaunchedEffect(selectedSeasonIndex) {
        episodeListState.scrollToItem(0)
    }

    if (isLoadingSeasons || seasons.isEmpty()) return

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            modifier = Modifier
                .width(180.dp)
                .focusRequester(seasonFocusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionDown -> {
                                if (selectedSeasonIndex < seasons.lastIndex) {
                                    selectedSeasonIndex++
                                } else {
                                    onNextSection()
                                }
                                true
                            }
                            Key.DirectionUp -> {
                                if (selectedSeasonIndex > 0) {
                                    selectedSeasonIndex--
                                } else {
                                    onDismiss()
                                }
                                true
                            }
                            Key.DirectionLeft -> {
                                onDismiss()
                                true
                            }
                            else -> false
                        }
                    } else false
                }
                .focusable()
        ) {
            seasons.forEachIndexed { index, season ->
                val isSelected = index == selectedSeasonIndex
                Surface(
                    color = Color.Transparent,
                    shape = RoundedCornerShape(6.dp),
                    border = if (isSelected) BorderStroke(1.5.dp, Color.White) else null,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = season.name ?: "Season ${season.indexNumber ?: (index + 1)}",
                            fontSize = 14.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        season.childCount?.let { count ->
                            Text(
                                text = "$count episodes",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.5f),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            selectedSeason?.let { season ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 10.dp)
                ) {
                    Text(
                        text = season.name ?: "Season",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    season.officialRating?.takeIf { it.isNotBlank() }?.let { rating ->
                        Surface(
                            color = Color.Transparent,
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
                        ) {
                            Text(
                                text = rating,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    season.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                        Text(
                            text = genres.joinToString(", "),
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (isLoadingEpisodes) {
                repeat(3) {
                    ShimmerEffect(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        cornerRadius = 8f
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            } else {
                LazyColumn(
                    state = episodeListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(episodes) { index, episode ->
                        val isLastEpisode = index == episodes.lastIndex
                        SeasonEpisodeRow(
                            episode = episode,
                            mediaRepository = mediaRepository,
                            onClick = {
                                episode.id?.let { episodeId ->
                                    onEpisodeClick(episodeId)
                                }
                            },
                            onLeftPressed = { seasonFocusRequester.requestFocus() },
                            onDownPressed = if (isLastEpisode) onNextSection else null
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        seasonFocusRequester.requestFocus()
    }
}

@Composable
private fun SeasonEpisodeRow(
    episode: BaseItemDto,
    mediaRepository: MediaRepository,
    onClick: () -> Unit = {},
    onLeftPressed: (() -> Unit)? = null,
    onDownPressed: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var episodeImageUrl by remember(episode.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(episode.id) {
        episodeImageUrl = com.vela.shared.util.image.getBackdrop(
            episode = episode,
            mediaRepository = mediaRepository,
            width = 640,
            height = 360,
            quality = 90
        )
    }

    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onLeftPressed?.invoke()
                            onLeftPressed != null
                        }
                        Key.DirectionDown -> {
                            if (onDownPressed != null) {
                                onDownPressed()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            },
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(180.dp)
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E1E))
            ) {
                JellyfinPosterImage(
                    context = context,
                    imageUrl = episodeImageUrl,
                    contentDescription = episode.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = "S${episode.parentIndexNumber ?: 1}: E${episode.indexNumber ?: 1}",
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .background(
                            Color.Black.copy(alpha = 0.7f),
                            RoundedCornerShape(topEnd = 6.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = episode.name ?: "Episode ${episode.indexNumber ?: ""}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    Text(
                        text = overview,
                        fontSize = 13.sp,
                        lineHeight = 17.sp,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                episode.runTimeTicks?.let { ticks ->
                    Text(
                        text = "(${CodecUtils.formatRuntime(ticks)})",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
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