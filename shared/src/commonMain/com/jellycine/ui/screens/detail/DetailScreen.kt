package com.jellycine.ui.screens.detail

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.BaseItemPerson
import com.jellycine.data.model.MediaStream
import com.jellycine.data.model.SeerrItemIds
import com.jellycine.data.repository.AuthRepository
import com.jellycine.data.repository.MediaRepository
import com.jellycine.playback.UserDataRefreshSignals
import com.jellycine.preferences.Preferences
import com.jellycine.ui.components.common.BackButton
import com.jellycine.ui.components.common.ScreenWrapper
import com.jellycine.ui.components.common.ShimmerEffect
import com.jellycine.ui.components.common.detailContentMaxWidth
import com.jellycine.util.image.LocalDisablePosterEnhancers
import com.jellycine.util.image.imageTagFor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal val heroOverlayGradient = arrayOf(
    0.0f to Color.Transparent,
    0.62f to Color.Transparent,
    0.78f to Color.Black.copy(alpha = 0.08f),
    0.88f to Color.Black.copy(alpha = 0.18f),
    0.95f to Color.Black.copy(alpha = 0.30f),
    1.0f to Color.Black.copy(alpha = 0.46f)
)

internal val heroBottomFadeGradient = arrayOf(
    0.0f to Color.Transparent,
    0.12f to Color.Transparent,
    0.34f to Color.Black.copy(alpha = 0.05f),
    0.56f to Color.Black.copy(alpha = 0.16f),
    0.74f to Color.Black.copy(alpha = 0.34f),
    0.88f to Color.Black.copy(alpha = 0.62f),
    0.96f to Color.Black.copy(alpha = 0.86f),
    1.0f to Color.Black
)

private data class SeasonDetailData(
    val seriesId: String,
    val seasonId: String,
    val seasonName: String?,
    val initialHeroImageUrl: String?,
    val initialLogoImageUrl: String?
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DetailScreenContainer(
    itemId: String,
    mediaRepository: MediaRepository,
    authRepository: AuthRepository,
    preferences: Preferences,
    platform: DetailPlatform = remember { DefaultDetailPlatform() },
    forceMergeVersions: Boolean = false,
    onBackPressed: () -> Unit = {},
    onNavigateToDetail: (String) -> Unit = {},
    onNavigateToPerson: (String) -> Unit = {}
) {
    val activeServerId by authRepository.getActiveServerId()
        .collectAsState(initial = authRepository.getActiveSessionSnapshot().activeServerId)
    val serverType by authRepository.getServerType().collectAsState(initial = null)
    val posterEnhancersEnabled by preferences.PosterEnhancersEnabled()
        .collectAsState(initial = preferences.isPosterEnhancersEnabled())
    val disablePosterEnhancers =
        serverType.equals("EMBY", ignoreCase = true) && posterEnhancersEnabled

    var item by remember { mutableStateOf<BaseItemDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    // Navigation state
    var currentScreen by remember { mutableStateOf("detail") }
    var seasonDetailData by remember { mutableStateOf<SeasonDetailData?>(null) }
    var episodeDetailId by remember { mutableStateOf<String?>(null) }
    var episodeItem by remember { mutableStateOf<BaseItemDto?>(null) }
    var episodeHistory by remember { mutableStateOf<List<String>>(emptyList()) }
    var isEpisodeLoading by remember { mutableStateOf(false) }
    var episodeError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val userDataRefreshEvent by UserDataRefreshSignals.refreshEvent.collectAsState()
    val controller = platform.rememberController(
        itemId = itemId,
        item = item,
        episodeItem = episodeItem,
        onEpisodePlaybackChanged = { playedEpisodeId ->
            if (currentScreen == "episode") {
                episodeDetailId = playedEpisodeId
            }
        }
    )

    val handleBackNavigation: () -> Unit = {
        when {
            controller.handleBack() -> Unit

            currentScreen == "episode" && episodeHistory.isNotEmpty() -> {
                episodeDetailId = episodeHistory.last()
                episodeHistory = episodeHistory.dropLast(1)
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

    fun openEpisodeInline(targetEpisodeId: String) {
        val nextId = targetEpisodeId.takeIf { it.isNotBlank() } ?: return
        val currentId = episodeItem?.id ?: episodeDetailId
        if (nextId == currentId) return
        if (currentId != null) {
            episodeHistory = episodeHistory + currentId
        }
        episodeDetailId = nextId
        currentScreen = "episode"
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

    fun clearScreens() {
        seasonDetailData = null
        episodeDetailId = null
        episodeItem = null
        episodeHistory = emptyList()
    }

    fun navigateToSeriesOverview(seriesId: String) {
        val targetSeriesId = seriesId.takeIf { it.isNotBlank() } ?: return
        if (targetSeriesId == item?.id || targetSeriesId == itemId) {
            clearScreens()
            currentScreen = "detail"
            return
        }

        scope.launch {
            isLoading = true
            error = null
            mediaRepository.getItemById(targetSeriesId).fold(
                onSuccess = { seriesItem ->
                    item = seriesItem
                    clearScreens()
                    isLoading = false
                    currentScreen = "detail"
                },
                onFailure = {
                    isLoading = false
                    onNavigateToDetail(targetSeriesId)
                }
            )
        }
    }

    fun openSeasonDetail(
        seriesId: String,
        seasonId: String,
        seasonName: String?,
        heroImageUrl: String?,
        logoImageUrl: String?
    ) {
        seasonDetailData = SeasonDetailData(
            seriesId = seriesId,
            seasonId = seasonId,
            seasonName = seasonName,
            initialHeroImageUrl = heroImageUrl,
            initialLogoImageUrl = logoImageUrl
        )
        currentScreen = "season"
    }

    @Composable
    fun DetailPane(
        activeItem: BaseItemDto?,
        loading: Boolean,
        fallbackItemId: String,
        onEpisodeClick: (String) -> Unit = onNavigateToDetail
    ) {
        ScreenWrapper(isActive = true) {
            if (activeItem != null) {
                DetailScreen(
                    item = activeItem,
                    mediaRepository = mediaRepository,
                    authRepository = authRepository,
                    preferences = preferences,
                    platform = platform,
                    isLoading = loading,
                    forceMergeVersions = forceMergeVersions,
                    trackSelectionSyncVersion = controller.trackSelectionSyncVersion,
                    onBackPressed = handleBackNavigation,
                    onPlayClick = { audioStreamIndex, subtitleStreamIndex ->
                        controller.play(
                            item = activeItem,
                            fallbackItemId = fallbackItemId,
                            audioStreamIndex = audioStreamIndex,
                            subtitleStreamIndex = subtitleStreamIndex
                        )
                    },
                    onRemoteTrailerClick = controller::playRemoteTrailer,
                    onPreferredStreamIndexesChanged = controller::onPreferredStreamIndexesChanged,
                    onSimilarItemClick = onNavigateToDetail,
                    onEpisodeClick = onEpisodeClick,
                    onVersionItemSelected = ::selectLocalVersion,
                    onPersonClick = onNavigateToPerson,
                    onCastButtonClick = controller::openCast,
                    onSeriesOverviewClick = ::navigateToSeriesOverview,
                    onSeasonClick = ::openSeasonDetail
                )
            } else {
                DetailScreenSkeleton(onBackPressed = handleBackNavigation)
            }
        }
    }

    LaunchedEffect(itemId, activeServerId) {
        try {
            isLoading = true
            error = null

            val result = platform.loadItem(
                itemId = itemId,
                activeServerId = activeServerId,
                mediaRepository = mediaRepository
            )
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

    CompositionLocalProvider(LocalDisablePosterEnhancers provides disablePosterEnhancers) {
        if (error != null) {
            LaunchedEffect(Unit) {
                onBackPressed()
            }
        } else {
            if (controller.coversContent) {
                controller.Content()
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                ) {
                    DetailPane(
                        activeItem = item,
                        loading = isLoading,
                        fallbackItemId = itemId
                    )

                    AnimatedVisibility(
                        visible = currentScreen == "season" || currentScreen == "episode",
                        enter = fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)),
                        exit = fadeOut(animationSpec = tween(140, easing = FastOutSlowInEasing))
                    ) {
                        seasonDetailData?.let { seasonData ->
                            ScreenWrapper(isActive = true) {
                                SeasonDetailScreen(
                                    seriesId = seasonData.seriesId,
                                    seasonId = seasonData.seasonId,
                                    seasonName = seasonData.seasonName,
                                    mediaRepository = mediaRepository,
                                    seasonDownloadAction = platform.seasonDownloadAction,
                                    initialHeroImageUrl = seasonData.initialHeroImageUrl,
                                    initialLogoImageUrl = seasonData.initialLogoImageUrl,
                                    onBackPressed = handleBackNavigation,
                                    onEpisodeClick = { episodeId ->
                                        episodeHistory = emptyList()
                                        episodeDetailId = episodeId
                                        currentScreen = "episode"
                                    },
                                    onSeriesOverviewClick = ::navigateToSeriesOverview
                                )
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = currentScreen == "episode",
                        enter = fadeIn(animationSpec = tween(180, easing = FastOutSlowInEasing)),
                        exit = fadeOut(animationSpec = tween(140, easing = FastOutSlowInEasing))
                    ) {
                        episodeDetailId?.let { episodeId ->
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

                                episodeItem != null -> DetailPane(
                                    activeItem = episodeItem,
                                    loading = isEpisodeLoading,
                                    fallbackItemId = episodeId,
                                    onEpisodeClick = ::openEpisodeInline
                                )

                                else -> {
                                    DetailScreenSkeleton(onBackPressed = handleBackNavigation)
                                }
                            }
                        }
                    }
                }
            }

            controller.Overlays()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
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
    DetailContent(
        item = item,
        mediaRepository = mediaRepository,
        authRepository = authRepository,
        preferences = preferences,
        platform = platform,
        isLoading = isLoading,
        forceMergeVersions = forceMergeVersions,
        trackSelectionSyncVersion = trackSelectionSyncVersion,
        onBackPressed = onBackPressed,
        onPlayClick = onPlayClick,
        onRemoteTrailerClick = onRemoteTrailerClick,
        onPreferredStreamIndexesChanged = onPreferredStreamIndexesChanged,
        onSimilarItemClick = onSimilarItemClick,
        onEpisodeClick = onEpisodeClick,
        onVersionItemSelected = onVersionItemSelected,
        onPersonClick = onPersonClick,
        onCastButtonClick = onCastButtonClick,
        onSeriesOverviewClick = onSeriesOverviewClick,
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

internal data class DetailScreenLayoutSpec(
    val heroHeight: Dp,
    val backdropHeight: Dp,
    val headerOffset: Dp,
    val contentTopPadding: Dp,
    val horizontalPadding: Dp,
    val contentMaxWidth: Dp?,
    val logoContainerHeight: Dp,
    val logoBottomSpacing: Dp
)

internal fun detailScreenLayoutSpec(
    isWidescreenLayout: Boolean,
    useTabletBackdropLayout: Boolean,
    screenWidthDp: Dp,
    screenHeightDp: Dp
): DetailScreenLayoutSpec {
    val horizontalPadding = if (isWidescreenLayout) 30.dp else 14.dp

    return DetailScreenLayoutSpec(
        heroHeight = 330.dp,
        backdropHeight = if (useTabletBackdropLayout) screenHeightDp else 330.dp,
        headerOffset = if (useTabletBackdropLayout) 0.dp else (-96).dp,
        contentTopPadding = if (useTabletBackdropLayout) 312.dp else 0.dp,
        horizontalPadding = horizontalPadding,
        contentMaxWidth = detailContentMaxWidth(
            screenWidthDp = screenWidthDp,
            horizontalPadding = horizontalPadding
        ),
        logoContainerHeight = if (isWidescreenLayout) 74.dp else 78.dp,
        logoBottomSpacing = if (isWidescreenLayout) 18.dp else 8.dp
    )
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

    if (item.isSeerDetailItem()) {
        addCandidate(item.backdropImageUrl)
        addCandidate(item.imageUrl)
        return candidates
    }

    if (item.type == "Episode") {
        addCandidate(
            mediaRepository.getBackdropImageUrl(
                itemId = itemId,
                width = 1920,
                height = 1080,
                quality = 100,
                enableImageEnhancers = false,
                imageTag = item.imageTagFor(
                    imageType = "Backdrop",
                    targetItemId = itemId
                )
            ).first()
        )

        addCandidate(
            mediaRepository.getImageUrl(
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
            ).first()
        )

        addCandidate(
            mediaRepository.getImageUrl(
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
            ).first()
        )

        if (!seriesId.isNullOrBlank()) {
            addCandidate(
                mediaRepository.getBackdropImageUrl(
                    itemId = seriesId,
                    width = 1920,
                    height = 1080,
                    quality = 100,
                    enableImageEnhancers = false,
                    imageTag = item.imageTagFor(
                        imageType = "Backdrop",
                        targetItemId = seriesId
                    )
                ).first()
            )
            addCandidate(
                mediaRepository.getImageUrl(
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
                ).first()
            )
        }
    } else {
        addCandidate(
            mediaRepository.getBackdropImageUrl(
                itemId = itemId,
                width = 1200,
                height = 675,
                quality = 95,
                imageTag = item.imageTagFor(
                    imageType = "Backdrop",
                    targetItemId = itemId
                )
            ).first()
        )

        addCandidate(
            mediaRepository.getImageUrl(
                itemId = itemId,
                imageType = "Primary",
                width = 1200,
                height = 675,
                quality = 95,
                imageTag = item.imageTagFor(
                    imageType = "Primary",
                    targetItemId = itemId
                )
            ).first()
        )

    }

    return candidates
}

internal fun BaseItemPerson.isCreditType(expectedType: String): Boolean {
    return type.equals(expectedType, ignoreCase = true) ||
        role.equals(expectedType, ignoreCase = true)
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
fun DetailScreenSkeleton(
    onBackPressed: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
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
                        ShimmerEffect(modifier = Modifier
                            .width(56.dp)
                            .height(16.dp), cornerRadius = 8f)
                        ShimmerEffect(modifier = Modifier
                            .width(42.dp)
                            .height(16.dp), cornerRadius = 8f)
                        ShimmerEffect(modifier = Modifier
                            .width(64.dp)
                            .height(16.dp), cornerRadius = 8f)
                        ShimmerEffect(modifier = Modifier
                            .width(50.dp)
                            .height(18.dp), cornerRadius = 8f)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    ShimmerEffect(
                        modifier = Modifier
                            .fillMaxWidth(0.78f)
                            .height(14.dp),
                        cornerRadius = 8f
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    repeat(2) {
                        ShimmerEffect(
                            modifier = Modifier
                                .fillMaxWidth(0.82f)
                                .height(18.dp),
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
                        modifier = Modifier
                            .width(110.dp)
                            .height(22.dp),
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

        BackButton(
            onClick = onBackPressed,
            modifier = Modifier.align(Alignment.TopStart)
        )
    }
}

/** Persists the audio and subtitle track the user last picked for an item. */
interface DetailTrackPreferences {
    fun getPreferredAudioStreamIndex(itemId: String): Int?
    fun getPreferredSubtitleStreamIndex(itemId: String): Int?
    fun setPreferredAudioStreamIndex(itemId: String, streamIndex: Int?)
    fun setPreferredSubtitleStreamIndex(itemId: String, streamIndex: Int?)
}

class InMemoryDetailTrackPreferences : DetailTrackPreferences {
    private val audio = mutableMapOf<String, Int?>()
    private val subtitle = mutableMapOf<String, Int?>()

    override fun getPreferredAudioStreamIndex(itemId: String): Int? = audio[itemId]
    override fun getPreferredSubtitleStreamIndex(itemId: String): Int? = subtitle[itemId]
    override fun setPreferredAudioStreamIndex(itemId: String, streamIndex: Int?) {
        audio[itemId] = streamIndex
    }

    override fun setPreferredSubtitleStreamIndex(itemId: String, streamIndex: Int?) {
        subtitle[itemId] = streamIndex
    }
}

internal fun BaseItemDto.isSeerDetailItem(): Boolean = SeerrItemIds.isDetailId(id)

/**
 * Per item features that only some platforms provide: Seerr requests, downloads,
 * subtitle search and casting. The defaults render nothing, which is what iOS uses.
 */
interface DetailExtras {
    val isSeerDetail: Boolean

    /** Show the Seerr request row for a Seerr item. */
    val showSeerrRequestRow: Boolean get() = false
    val supportsDownload: Boolean get() = false
    val canSearchSubtitles: Boolean get() = false

    @Composable fun HeroCastButton(size: Dp = 34.dp) {}
    @Composable fun CastStatusBanner() {}
    @Composable fun VersionRequestBadge() {}

    @Composable
    fun SeerrRequestRow(
        modifier: Modifier,
        buttonHeight: Dp,
        hasTrailer: Boolean,
        onTrailerClick: () -> Unit
    ) {
    }

    @Composable fun ItemDownloadAction(buttonHeight: Dp, iconOnly: Boolean) {}
    @Composable fun SubtitleSearchAction(size: Dp) {}
    @Composable fun SeriesDownloadAction(modifier: Modifier, buttonHeight: Dp) {}

    @Composable
    fun Seasons(
        series: BaseItemDto,
        seriesId: String,
        mediaRepository: MediaRepository,
        activeServerId: String?,
        onSeasonClick: (seriesId: String, seasonId: String, seasonName: String?) -> Unit
    ) {
        SeasonsSection(
            series = series,
            seriesId = seriesId,
            mediaRepository = mediaRepository,
            activeServerId = activeServerId,
            refreshKey = 0,
            onSeasonRequest = {},
            onSeasonClick = onSeasonClick
        )
    }

    @Composable
    fun Recommendations(
        item: BaseItemDto,
        directors: List<com.jellycine.data.model.BaseItemPerson>,
        mediaRepository: MediaRepository,
        activeServerId: String?,
        onItemClick: (String) -> Unit
    ) {
        LocalRecommendations(
            item = item,
            mediaRepository = mediaRepository,
            onItemClick = onItemClick
        )
    }

    /** Dialogs owned by the extras (download pickers, Seerr requests, subtitle search). */
    @Composable fun Dialogs(backdropImageUrl: String?) {}
}

class NoDetailExtras(item: BaseItemDto) : DetailExtras {
    override val isSeerDetail: Boolean = item.isSeerDetailItem()
}

/** Playback, casting and theme music for the detail screen. */
interface DetailController {
    /** True while a player or trailer fully replaces the detail content. */
    val coversContent: Boolean
    val trackSelectionSyncVersion: Int

    fun play(
        item: BaseItemDto?,
        fallbackItemId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?
    )

    fun playRemoteTrailer(url: String, title: String?)
    fun openCast()
    fun onPreferredStreamIndexesChanged(audioStreamIndex: Int?, subtitleStreamIndex: Int?)

    /** Consumes back when an overlay is showing. Returns true when handled. */
    fun handleBack(): Boolean

    @Composable fun Content()
    @Composable fun Overlays()
}

object NoDetailController : DetailController {
    override val coversContent: Boolean = false
    override val trackSelectionSyncVersion: Int = 0
    override fun play(
        item: BaseItemDto?,
        fallbackItemId: String,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?
    ) {
    }

    override fun playRemoteTrailer(url: String, title: String?) {}
    override fun openCast() {}
    override fun onPreferredStreamIndexesChanged(audioStreamIndex: Int?, subtitleStreamIndex: Int?) {}
    override fun handleBack(): Boolean = false
    @Composable override fun Content() {}
    @Composable override fun Overlays() {}
}

interface DetailPlatform {
    val trackPreferences: DetailTrackPreferences

    @Composable
    fun rememberExtras(
        item: BaseItemDto,
        mediaStreams: List<MediaStream>,
        onMediaStreamsRefreshed: (List<MediaStream>) -> Unit,
        onRemoteTrailerClick: (String, String?) -> Unit,
        onCastClick: () -> Unit
    ): DetailExtras

    @Composable
    fun rememberController(
        itemId: String,
        item: BaseItemDto?,
        episodeItem: BaseItemDto?,
        onEpisodePlaybackChanged: (String) -> Unit
    ): DetailController

    /** Season level download button. Null hides it. */
    val seasonDownloadAction: (@Composable (seasonId: String, episodes: List<BaseItemDto>) -> Unit)?
        get() = null

    suspend fun loadItem(
        itemId: String,
        activeServerId: String?,
        mediaRepository: MediaRepository
    ): Result<BaseItemDto> {
        val seerParams = SeerrItemIds.detailParams(itemId)
            ?: return mediaRepository.getItemById(itemId)
        val (mediaType, tmdbId) = seerParams
        return mediaRepository.getTmdbTitleDetail(tmdbId, mediaType)
    }

    suspend fun loadLogoImage(
        item: BaseItemDto,
        activeServerId: String?,
        mediaRepository: MediaRepository
    ): String? = if (item.isSeerDetailItem()) {
        mediaRepository.getTmdbLogoUrl(item)
    } else {
        com.jellycine.util.image.logoImage(item = item, mediaRepository = mediaRepository)
    }

    /** Loads a Seerr person and the titles they directed. Null when Seerr is unavailable. */
    suspend fun loadSeerrPerson(
        tmdbId: String,
        activeServerId: String?
    ): Pair<BaseItemDto?, List<com.jellycine.data.model.SeerrRecommendationTitle>>? = null
}

class DefaultDetailPlatform(
    override val trackPreferences: DetailTrackPreferences = InMemoryDetailTrackPreferences()
) : DetailPlatform {
    @Composable
    override fun rememberExtras(
        item: BaseItemDto,
        mediaStreams: List<MediaStream>,
        onMediaStreamsRefreshed: (List<MediaStream>) -> Unit,
        onRemoteTrailerClick: (String, String?) -> Unit,
        onCastClick: () -> Unit
    ): DetailExtras = remember(item.id) { NoDetailExtras(item) }

    @Composable
    override fun rememberController(
        itemId: String,
        item: BaseItemDto?,
        episodeItem: BaseItemDto?,
        onEpisodePlaybackChanged: (String) -> Unit
    ): DetailController = NoDetailController
}
