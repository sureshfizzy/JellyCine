package com.jellycine.ui.screens.dashboard.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.ContentType
import com.jellycine.data.model.SeerrItemIds
import com.jellycine.data.model.SeerrPersonRole
import com.jellycine.data.model.SeerrRecommendationTitle
import com.jellycine.data.model.filterSeerTitlesForRow
import com.jellycine.data.model.seerPersonId
import com.jellycine.data.model.seerRole
import com.jellycine.data.model.title
import com.jellycine.data.model.toSeerDetailItem
import com.jellycine.data.repository.AuthRepository
import com.jellycine.data.repository.MediaRepository
import com.jellycine.recommendations.loadWatchedFeed
import com.jellycine.resources.Res
import com.jellycine.resources.dashboard_discover
import com.jellycine.resources.dashboard_discover_awards
import com.jellycine.resources.dashboard_discover_empty_message
import com.jellycine.resources.dashboard_discover_empty_title
import com.jellycine.resources.dashboard_discover_greeting
import com.jellycine.resources.dashboard_discover_load_error
import com.jellycine.resources.dashboard_discover_recommendations
import com.jellycine.resources.dashboard_view_all
import com.jellycine.resources.favorites
import com.jellycine.resources.movies
import com.jellycine.resources.search_results_episodes
import com.jellycine.resources.search_results_shows
import com.jellycine.resources.try_again
import com.jellycine.resources.watched
import com.jellycine.ui.components.common.CompactPageHeader
import com.jellycine.ui.components.common.CompactTopText
import com.jellycine.ui.components.common.ShimmerPosterRail
import com.jellycine.ui.components.common.UserProfileAvatar
import com.jellycine.ui.components.common.rememberCompactProgress
import com.jellycine.ui.screens.dashboard.home.LibraryItemCard
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

const val WATCHED_VIEW_ALL_PARENT_ID = "__watched__"
const val FAVORITES_VIEW_ALL_PARENT_ID = "__favorites__"

enum class DiscoverFeed { RECOMMENDATIONS, WATCHED, FAVORITES, AWARDS }

private val DiscoverFeedSaver = Saver<DiscoverFeed, String>(
    save = { it.name },
    restore = { name -> DiscoverFeed.entries.firstOrNull { it.name == name } }
)

/**
 * Optional Seerr hooks. Platforms that have no Seerr support pass `null`.
 */
interface DiscoverSeerrBridge {
    suspend fun creditTitles(
        seedItem: BaseItemDto,
        personId: String,
        role: SeerrPersonRole,
        activeServerId: String
    ): List<SeerrRecommendationTitle>

    @Composable
    fun TitleCard(item: SeerrRecommendationTitle, onClick: () -> Unit)
}

/**
 * Slot for the Awards feed. Platforms without awards support pass `null`, which hides the tab.
 * [feedPills] is the feed switcher and [profileAvatar] the header avatar, so the slot can place
 * them inside its own header.
 */
typealias DiscoverAwardsContent = @Composable (
    feedPills: @Composable () -> Unit,
    profileAvatar: @Composable () -> Unit
) -> Unit

private data class RecommendationSectionUi(
    val title: String,
    val items: List<BaseItemDto>,
    val seerItems: List<SeerrRecommendationTitle> = emptyList(),
    val seerRole: SeerrPersonRole? = null,
    val personName: String? = null
)

private data class RecommendationFeedState(
    val sections: List<RecommendationSectionUi>,
    val error: String?
)

@Composable
fun Discover(
    mediaRepository: MediaRepository,
    authRepository: AuthRepository,
    disablePosterEnhancers: Boolean,
    modifier: Modifier = Modifier,
    seerr: DiscoverSeerrBridge? = null,
    awardsContent: DiscoverAwardsContent? = null,
    onItemClick: (BaseItemDto) -> Unit = {},
    onWatchedItemClick: (BaseItemDto) -> Unit = onItemClick,
    onNavigateToViewAll: (ContentType, String?, String) -> Unit = { _, _, _ -> },
    onProfileClick: () -> Unit = {}
) {
    var sections by remember { mutableStateOf<List<RecommendationSectionUi>>(emptyList()) }
    var watchedSections by remember { mutableStateOf<List<RecommendationSectionUi>>(emptyList()) }
    var favoritesSections by remember { mutableStateOf<List<RecommendationSectionUi>>(emptyList()) }
    var feed by rememberSaveable(stateSaver = DiscoverFeedSaver) {
        mutableStateOf(DiscoverFeed.RECOMMENDATIONS)
    }
    val showWatchedTab = feed == DiscoverFeed.WATCHED
    val showFavoritesTab = feed == DiscoverFeed.FAVORITES
    var loadingFeed by remember { mutableStateOf(true) }
    var loadingWatched by remember { mutableStateOf(true) }
    var loadingFavorites by remember { mutableStateOf(true) }
    val isLoading = when (feed) {
        DiscoverFeed.WATCHED -> loadingWatched && watchedSections.isEmpty()
        DiscoverFeed.FAVORITES -> loadingFavorites && favoritesSections.isEmpty()
        else -> loadingFeed && sections.isEmpty()
    }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val feedListState = rememberLazyListState()
    val watchedListState = rememberLazyListState()
    val favoritesListState = rememberLazyListState()
    val listState = when (feed) {
        DiscoverFeed.WATCHED -> watchedListState
        DiscoverFeed.FAVORITES -> favoritesListState
        else -> feedListState
    }
    val compactHeaderProgress = rememberCompactProgress(
        state = listState,
        compactDistance = 92.dp
    )
    val sessionSnapshot by authRepository.observeActiveSession()
        .collectAsState(initial = authRepository.getActiveSessionSnapshot())
    val username = sessionSnapshot.username
    val fallbackHeaderTitle = stringResource(Res.string.dashboard_discover)
    val greetingName = username?.trim()?.takeIf { it.isNotEmpty() }
    val headerTitle = if (greetingName != null) {
        stringResource(Res.string.dashboard_discover_greeting, greetingName)
    } else {
        fallbackHeaderTitle
    }
    val activeServerId = sessionSnapshot.activeServerId
    var profileImageUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val url = mediaRepository.getUserProfileImageUrl()
        if (!url.isNullOrBlank()) profileImageUrl = url
    }
    val compactTitle = when (feed) {
        DiscoverFeed.RECOMMENDATIONS -> stringResource(Res.string.dashboard_discover_recommendations)
        DiscoverFeed.WATCHED -> stringResource(Res.string.watched)
        DiscoverFeed.FAVORITES -> stringResource(Res.string.favorites)
        DiscoverFeed.AWARDS -> stringResource(Res.string.dashboard_discover_awards)
    }
    val watchedMoviesTitle = stringResource(Res.string.movies)
    val watchedShowsTitle = stringResource(Res.string.search_results_shows)
    val watchedEpisodesTitle = stringResource(Res.string.search_results_episodes)
    val awardsEnabled = awardsContent != null

    LaunchedEffect(awardsEnabled) {
        if (!awardsEnabled && feed == DiscoverFeed.AWARDS) feed = DiscoverFeed.RECOMMENDATIONS
    }

    fun refresh() {
        scope.launch {
            loadingFeed = true
            val result = loadRecommendationFeed(mediaRepository)
            sections = result.sections
            error = result.error
            loadingFeed = false

            if (seerr == null) return@launch
            result.sections
                .filter { it.seerRole != null }
                .forEach { section ->
                    launch {
                        val seerItems = loadSectionSeerRecommendations(
                            section = section,
                            activeServerId = activeServerId,
                            seerr = seerr
                        )
                        var updatedSection = section.copy(seerItems = seerItems)
                        if (updatedSection != section) {
                            sections = sections.replaceSection(updatedSection)
                        }

                        seerItems
                            .mapNotNull { seerItem ->
                                val seedItemId = updatedSection.items.firstOrNull()?.id
                                val jellyfinMediaId = seerItem.jellyfinMediaId
                                    ?.takeIf { it.isNotBlank() && it != seedItemId }
                                    ?: return@mapNotNull null
                                jellyfinMediaId to seerItem
                            }
                            .distinctBy { (jellyfinMediaId, _) -> jellyfinMediaId }
                            .forEach { (jellyfinMediaId, seerItem) ->
                                val localItem = mediaRepository.getItemById(jellyfinMediaId).getOrNull() ?: return@forEach
                                val localItemId = localItem.id ?: return@forEach
                                updatedSection = if (updatedSection.items.any { it.id == localItemId }) {
                                    updatedSection.copy(
                                        seerItems = updatedSection.seerItems.filterNot { it.tmdbId == seerItem.tmdbId }
                                    )
                                } else {
                                    updatedSection.copy(
                                        items = updatedSection.items + localItem,
                                        seerItems = updatedSection.seerItems.filterNot { it.tmdbId == seerItem.tmdbId }
                                    )
                                }
                                sections = sections.replaceSection(updatedSection)
                            }
                    }
                }
        }

        scope.launch {
            val watched = loadWatchedFeed(
                mediaRepository = mediaRepository,
                moviesTitle = watchedMoviesTitle,
                showsTitle = watchedShowsTitle,
                episodesTitle = watchedEpisodesTitle
            )
            watchedSections = watched.sections.map { section ->
                RecommendationSectionUi(section.title, section.items)
            }
            if (error == null) error = watched.error
            loadingWatched = false
        }
    }

    LaunchedEffect(activeServerId) {
        refresh()
    }

    LaunchedEffect(activeServerId) {
        loadingFavorites = true
        val items = mediaRepository.getFavoriteItems().getOrNull()?.items.orEmpty()
        favoritesSections = buildList {
            items.filter { it.type == "Movie" }.takeIf { it.isNotEmpty() }
                ?.let { add(RecommendationSectionUi(watchedMoviesTitle, it)) }
            items.filter { it.type == "Series" }.takeIf { it.isNotEmpty() }
                ?.let { add(RecommendationSectionUi(watchedShowsTitle, it)) }
            items.filter { it.type == "Episode" }.takeIf { it.isNotEmpty() }
                ?.let { add(RecommendationSectionUi(watchedEpisodesTitle, it)) }
        }
        loadingFavorites = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val activeSections = when (feed) {
            DiscoverFeed.WATCHED -> watchedSections
            DiscoverFeed.FAVORITES -> favoritesSections
            else -> sections
        }
        val hasAnySections = activeSections.isNotEmpty()
        val showStaticHeader = activeSections.isEmpty()

        Column(modifier = Modifier.fillMaxSize()) {
            if (feed == DiscoverFeed.AWARDS && awardsContent != null) {
                awardsContent(
                    { DiscoverFeedPills(feed = feed, awardsEnabled = true, onFeedChange = { feed = it }) },
                    {
                        UserProfileAvatar(
                            imageUrl = profileImageUrl,
                            serverTypeRaw = sessionSnapshot.serverType,
                            onClick = onProfileClick,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                )
            } else {
                if (showStaticHeader) {
                    DiscoverHeader(
                        title = headerTitle,
                        profileImageUrl = profileImageUrl,
                        serverTypeRaw = sessionSnapshot.serverType,
                        onProfileClick = onProfileClick
                    )
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        isLoading -> {
                            DiscoverLoadingSkeleton(
                                feed = feed,
                                awardsEnabled = awardsEnabled,
                                onFeedChange = { feed = it }
                            )
                        }

                        hasAnySections -> {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 110.dp)
                            ) {
                                item(key = "discover_header") {
                                    DiscoverHeader(
                                        title = headerTitle,
                                        profileImageUrl = profileImageUrl,
                                        serverTypeRaw = sessionSnapshot.serverType,
                                        onProfileClick = onProfileClick
                                    )
                                }

                                item(key = "discover_feed_pills") {
                                    DiscoverFeedPills(
                                        feed = feed,
                                        awardsEnabled = awardsEnabled,
                                        onFeedChange = { feed = it }
                                    )
                                }

                                itemsIndexed(
                                    items = activeSections,
                                    key = { index, section -> "${section.title}_$index" }
                                ) { _, section ->
                                    val viewAllContentType = when (section.title) {
                                        watchedMoviesTitle -> ContentType.MOVIES
                                        watchedShowsTitle -> ContentType.SERIES
                                        else -> ContentType.EPISODES
                                    }
                                    val viewAllParentId = if (showFavoritesTab) {
                                        FAVORITES_VIEW_ALL_PARENT_ID
                                    } else {
                                        WATCHED_VIEW_ALL_PARENT_ID
                                    }
                                    RecommendationRail(
                                        section = section,
                                        mediaRepository = mediaRepository,
                                        disablePosterEnhancers = disablePosterEnhancers,
                                        isWatchedFeed = showWatchedTab,
                                        showViewAll = showWatchedTab || showFavoritesTab,
                                        seerr = seerr,
                                        onViewAllClick = {
                                            onNavigateToViewAll(viewAllContentType, viewAllParentId, section.title)
                                        },
                                        onItemClick = if (showWatchedTab) onWatchedItemClick else onItemClick
                                    )
                                }
                            }
                        }

                        else -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                DiscoverFeedPills(
                                    feed = feed,
                                    awardsEnabled = awardsEnabled,
                                    onFeedChange = { feed = it }
                                )
                                Box(modifier = Modifier.weight(1f)) {
                                    if (error != null) {
                                        ErrorCard(
                                            message = error.orEmpty(),
                                            onRetry = ::refresh
                                        )
                                    } else {
                                        EmptyCard()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (feed != DiscoverFeed.AWARDS && hasAnySections) {
            CompactTopText(
                text = compactTitle,
                progress = compactHeaderProgress,
                isTablet = false,
                onClick = {
                    scope.launch {
                        listState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

private suspend fun loadRecommendationFeed(mediaRepository: MediaRepository): RecommendationFeedState {
    return try {
        val viewsResult = mediaRepository.getUserViews()
        val movieLibraries = viewsResult.getOrNull()?.items
            .orEmpty()
            .filter { view ->
                view.id != null &&
                    view.collectionType.equals("movies", ignoreCase = true)
            }

        val requests = if (movieLibraries.isNotEmpty()) {
            movieLibraries
        } else {
            listOf(BaseItemDto(name = null, id = null, collectionType = "movies"))
        }

        val results = coroutineScope {
            requests.map { library ->
                async {
                    mediaRepository.getMovieRecommendations(
                        parentId = library.id,
                        categoryLimit = 8,
                        itemLimit = 18
                    )
                }
            }.awaitAll()
        }

        val rawSections = results.flatMap { result ->
            result.getOrNull()
                .orEmpty()
                .mapNotNull { recommendation ->
                    val title = recommendation.title()
                    val items = recommendation.items.orEmpty()
                    if (title == null || items.isEmpty()) {
                        null
                    } else {
                        RecommendationSectionUi(
                            title = title,
                            items = items,
                            seerRole = recommendation.seerRole(),
                            personName = recommendation.baselineItemName?.takeIf { it.isNotBlank() }
                        )
                    }
                }
        }

        val sections = rawSections
            .groupBy { it.title }
            .map { (_, groupedSections) ->
                val seed = groupedSections.first()
                RecommendationSectionUi(
                    title = seed.title,
                    items = groupedSections
                        .flatMap { it.items }
                        .distinctBy { item -> item.id ?: item.name.orEmpty() },
                    seerRole = seed.seerRole,
                    personName = seed.personName
                )
            }
            .filter { it.items.isNotEmpty() }

        if (sections.isNotEmpty()) {
            RecommendationFeedState(sections = sections, error = null)
        } else {
            val fallback = mediaRepository.getSuggestions(
                mediaType = "Movie",
                limit = 24
            )
            val fallbackItems = fallback.getOrNull().orEmpty()
            if (fallbackItems.isNotEmpty()) {
                RecommendationFeedState(
                    sections = listOf(
                        RecommendationSectionUi(
                            title = "Suggestions",
                            items = fallbackItems
                        )
                    ),
                    error = null
                )
            } else {
                val recommendationError = results
                    .asSequence()
                    .mapNotNull { it.exceptionOrNull()?.message }
                    .firstOrNull()
                RecommendationFeedState(
                    sections = emptyList(),
                    error = listOfNotNull(
                        recommendationError,
                        fallback.exceptionOrNull()?.message,
                        viewsResult.exceptionOrNull()?.message
                    ).firstOrNull()
                )
            }
        }
    } catch (e: Exception) {
        RecommendationFeedState(emptyList(), e.message ?: "Unknown error")
    }
}

private suspend fun loadSectionSeerRecommendations(
    section: RecommendationSectionUi,
    activeServerId: String?,
    seerr: DiscoverSeerrBridge
): List<SeerrRecommendationTitle> {
    val role = section.seerRole ?: return emptyList()
    val personName = section.personName ?: return emptyList()
    val seedItem = section.items.firstOrNull() ?: return emptyList()
    if (activeServerId.isNullOrBlank()) return emptyList()
    val personId = seerPersonId(
        items = section.items,
        personName = personName,
        role = role
    ) ?: return emptyList()

    return filterSeerTitlesForRow(
        seerrTitles = seerr.creditTitles(
            seedItem = seedItem,
            personId = personId,
            role = role,
            activeServerId = activeServerId
        ),
        baseTitles = section.items,
        item = seedItem,
    )
}

private fun List<RecommendationSectionUi>.replaceSection(
    updatedSection: RecommendationSectionUi
): List<RecommendationSectionUi> {
    return map { section ->
        if (section.title == updatedSection.title) updatedSection else section
    }
}

@Composable
private fun DiscoverHeader(
    title: String,
    profileImageUrl: String?,
    serverTypeRaw: String?,
    onProfileClick: () -> Unit = {}
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        CompactPageHeader(title = title)
        UserProfileAvatar(
            imageUrl = profileImageUrl,
            serverTypeRaw = serverTypeRaw,
            onClick = onProfileClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 16.dp, top = 15.dp)
                .size(34.dp)
        )
    }
}

@Composable
private fun DiscoverFeedPills(
    feed: DiscoverFeed,
    awardsEnabled: Boolean,
    onFeedChange: (DiscoverFeed) -> Unit
) {
    val tabs = buildList {
        add(DiscoverFeed.RECOMMENDATIONS to stringResource(Res.string.dashboard_discover_recommendations))
        add(DiscoverFeed.WATCHED to stringResource(Res.string.watched))
        add(DiscoverFeed.FAVORITES to stringResource(Res.string.favorites))
        if (awardsEnabled) {
            add(DiscoverFeed.AWARDS to stringResource(Res.string.dashboard_discover_awards))
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { (tab, label) ->
            val selected = feed == tab
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (selected) Color.White else Color.White.copy(alpha = 0.12f),
                modifier = Modifier.clickable { onFeedChange(tab) }
            ) {
                Text(
                    text = label,
                    color = if (selected) Color.Black else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun DiscoverLoadingSkeleton(
    feed: DiscoverFeed,
    awardsEnabled: Boolean,
    onFeedChange: (DiscoverFeed) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        DiscoverFeedPills(
            feed = feed,
            awardsEnabled = awardsEnabled,
            onFeedChange = onFeedChange
        )
        repeat(3) { ShimmerPosterRail() }
    }
}

@Composable
private fun RecommendationRail(
    section: RecommendationSectionUi,
    mediaRepository: MediaRepository,
    disablePosterEnhancers: Boolean,
    isWatchedFeed: Boolean,
    showViewAll: Boolean,
    seerr: DiscoverSeerrBridge?,
    onViewAllClick: () -> Unit,
    onItemClick: (BaseItemDto) -> Unit
) {
    if (!showViewAll && section.items.size + section.seerItems.size <= 1) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = section.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (showViewAll) {
                IconButton(
                    onClick = onViewAllClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(Res.string.dashboard_view_all),
                        tint = Color.White.copy(alpha = 0.72f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        ) {
            itemsIndexed(
                items = section.items,
                key = { index, item ->
                    "${item.id ?: item.name ?: section.title}_$index"
                }
            ) { _, item ->
                LibraryItemCard(
                    item = item,
                    mediaRepository = mediaRepository,
                    disableImageEnhancers = disablePosterEnhancers || isWatchedFeed,
                    watchedFeedStyle = isWatchedFeed,
                    onClick = { onItemClick(item) }
                )
            }

            if (seerr != null) {
                itemsIndexed(
                    items = section.seerItems,
                    key = { index, item ->
                        "${section.title}-${SeerrItemIds.detailId(item.tmdbId, item.mediaType)}_$index"
                    }
                ) { _, item ->
                    seerr.TitleCard(
                        item = item,
                        onClick = { onItemClick(item.toSeerDetailItem()) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onRetry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(Res.string.dashboard_discover_load_error),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE86E2F))
            ) {
                Text(text = stringResource(Res.string.try_again))
            }
        }
    }
}

@Composable
private fun EmptyCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121212))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.42f),
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = stringResource(Res.string.dashboard_discover_empty_title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.dashboard_discover_empty_message),
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}
