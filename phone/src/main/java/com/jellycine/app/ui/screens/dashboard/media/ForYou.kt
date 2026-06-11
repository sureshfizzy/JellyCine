package com.jellycine.app.ui.screens.dashboard.media
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.shared.R
import com.jellycine.app.ui.components.common.AwardsCompactHeader
import com.jellycine.app.ui.components.common.CompactPageHeader
import com.jellycine.app.ui.components.common.CompactTopText
import com.jellycine.app.ui.components.common.SeerTitleCard
import com.jellycine.app.ui.components.common.fetchSeerCreditTitles
import com.jellycine.app.ui.components.common.rememberCompactProgress
import com.jellycine.app.ui.screens.dashboard.home.UserProfileAvatar
import com.jellycine.data.model.SeerrPersonRole
import com.jellycine.data.model.filterSeerTitlesForRow
import com.jellycine.data.model.seerPersonId
import com.jellycine.data.model.toSeerDetailItem
import com.jellycine.data.model.SeerrItemIds
import com.jellycine.app.ui.screens.dashboard.home.LibraryItemCard
import com.jellycine.shared.util.image.disableEmbyPosterEnhancers
import com.jellycine.data.model.AwardMode
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.RecommendationDto
import com.jellycine.data.model.SeerrRecommendationTitle
import com.jellycine.data.model.seerRole
import com.jellycine.data.model.title
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.AwardsRepositoryProvider
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.data.repository.SeerrRepository
import com.jellycine.shared.recommendations.loadWatchedFeed
import com.jellycine.shared.ui.components.common.ShimmerPosterRail
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private data class RecommendationSectionUi(
    val title: String,
    val items: List<BaseItemDto>,
    val seerItems: List<SeerrRecommendationTitle> = emptyList(),
    val seerRole: SeerrPersonRole? = null,
    val personName: String? = null
)

internal const val WATCHED_VIEW_ALL_PARENT_ID = "__watched__"

private data class RecommendationFeedState(
    val sections: List<RecommendationSectionUi>,
    val error: String?
)

@Composable
fun ForYou(
    onItemClick: (BaseItemDto) -> Unit = {},
    onWatchedItemClick: (BaseItemDto) -> Unit = onItemClick,
    onNavigateToViewAll: (ContentType, String?, String) -> Unit = { _, _, _ -> }
) {
    var sections by remember { mutableStateOf<List<RecommendationSectionUi>>(emptyList()) }
    var watchedSections by remember { mutableStateOf<List<RecommendationSectionUi>>(emptyList()) }
    var feed by rememberSaveable { mutableStateOf(ForYouFeed.RECOMMENDATIONS) }
    var awardsHeaderTitle by remember { mutableStateOf<String?>(null) }
    var awardsBack by remember { mutableStateOf<(() -> Unit)?>(null) }
    val showWatchedTab = feed == ForYouFeed.WATCHED
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val authRepository = remember { AuthRepositoryProvider.getInstance(context) }
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val seerrRepository = remember(context) { SeerrRepository(context) }
    val awardsRepository = remember(context) { AwardsRepositoryProvider.getInstance(context) }
    val disablePosterEnhancers = disableEmbyPosterEnhancers()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val compactHeaderProgress = rememberCompactProgress(
        state = listState,
        compactDistance = 92.dp
    )
    val activeSessionSnapshot = remember { authRepository.getActiveSessionSnapshot() }
    val sessionSnapshot by authRepository.observeActiveSession()
        .collectAsState(initial = activeSessionSnapshot)
    val username = sessionSnapshot.username
    val fallbackHeaderTitle = stringResource(R.string.dashboard_for_you)
    val greetingName = username?.trim()?.takeIf { it.isNotEmpty() }
    val headerTitle = if (greetingName != null) {
        stringResource(R.string.dashboard_for_you_greeting, greetingName)
    } else {
        fallbackHeaderTitle
    }
    val activeServerId = sessionSnapshot.activeServerId
    val seerrConnected = remember(activeServerId) {
        activeServerId?.let { seerrRepository.getSavedConnectionInfo(it)?.isVerified == true } ?: false
    }
    val activeSavedServer = remember(sessionSnapshot.savedServers, sessionSnapshot.activeServerId) {
        sessionSnapshot.savedServers.firstOrNull { savedServer ->
            savedServer.id == sessionSnapshot.activeServerId
        }
    }
    val profileImageUrl = activeSavedServer?.profileImageUrl
    val watchedMoviesTitle = stringResource(R.string.movies)
    val watchedShowsTitle = stringResource(R.string.search_results_shows)
    val watchedEpisodesTitle = stringResource(R.string.search_results_episodes)

    fun refresh() {
        scope.launch {
            isLoading = true
            val recommendationResult = async { loadRecommendationFeed(mediaRepository) }
            val watchedResult = async {
                loadWatchedFeed(
                    mediaRepository = mediaRepository,
                    moviesTitle = watchedMoviesTitle,
                    showsTitle = watchedShowsTitle,
                    episodesTitle = watchedEpisodesTitle
                )
            }
            val result = recommendationResult.await()
            val watched = watchedResult.await()
            sections = result.sections
            watchedSections = watched.sections.map { section ->
                RecommendationSectionUi(section.title, section.items)
            }
            error = result.error ?: watched.error
            isLoading = false

            result.sections
                .filter { it.seerRole != null }
                .forEach { section ->
                    launch {
                        val seerItems = loadSectionSeerRecommendations(
                            section = section,
                            activeServerId = activeServerId,
                            mediaRepository = mediaRepository,
                            seerrRepository = seerrRepository
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
    }

    LaunchedEffect(activeServerId) {
        refresh()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val activeSections = if (showWatchedTab) watchedSections else sections
        val hasAnySections = sections.isNotEmpty() || watchedSections.isNotEmpty()
        val showStaticHeader = sections.isEmpty() && watchedSections.isEmpty()

        Column(modifier = Modifier.fillMaxSize()) {
            if (feed == ForYouFeed.AWARDS) {
                AwardsCompactHeader(
                    title = awardsHeaderTitle
                        ?: stringResource(R.string.dashboard_for_you_awards),
                    onBack = awardsBack
                ) {
                    UserProfileAvatar(
                        imageUrl = profileImageUrl,
                        serverTypeRaw = sessionSnapshot.serverType,
                        onClick = {},
                        modifier = Modifier.size(34.dp)
                    )
                }
                if (awardsBack == null) {
                    ForYouFeedPills(
                        feed = feed,
                        onFeedChange = { feed = it }
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    CompositionLocalProvider(LocalAwardSeerrConnected provides seerrConnected) {
                        AwardsContent(
                            awardsRepository = awardsRepository,
                            onItemClick = onItemClick,
                            onViewAllCategory = { qid, mode, title ->
                                onNavigateToViewAll(ContentType.AWARD, "${qid}_${mode.name}", title)
                            },
                            onHeaderTitleChange = { awardsHeaderTitle = it },
                            onBackChange = { awardsBack = it },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            } else {
            if (showStaticHeader) {
                ForYouHeader(
                    title = headerTitle,
                    profileImageUrl = profileImageUrl,
                    serverTypeRaw = sessionSnapshot.serverType
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    isLoading -> {
                        ForYouLoadingSkeleton(
                            feed = feed,
                            onFeedChange = { feed = it }
                        )
                    }

                    hasAnySections -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 110.dp)
                        ) {
                            item(key = "for_you_header") {
                                ForYouHeader(
                                    title = headerTitle,
                                    profileImageUrl = profileImageUrl,
                                    serverTypeRaw = sessionSnapshot.serverType
                                )
                            }

                            item(key = "for_you_feed_pills") {
                                ForYouFeedPills(
                                    feed = feed,
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
                                RecommendationRail(
                                    section = section,
                                    mediaRepository = mediaRepository,
                                    disablePosterEnhancers = disablePosterEnhancers,
                                    isWatchedFeed = showWatchedTab,
                                    onViewAllClick = {
                                        onNavigateToViewAll(viewAllContentType, WATCHED_VIEW_ALL_PARENT_ID, section.title)
                                    },
                                    onItemClick = if (showWatchedTab) onWatchedItemClick else onItemClick
                                )
                            }

                        }
                    }

                    error != null -> {
                        ErrorCard(
                            message = error.orEmpty(),
                            onRetry = ::refresh
                        )
                    }

                    else -> {
                        EmptyCard()
                    }
                }
            }
            }
        }

        if (feed != ForYouFeed.AWARDS && (sections.isNotEmpty() || watchedSections.isNotEmpty())) {
            CompactTopText(
                text = headerTitle,
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
    mediaRepository: MediaRepository,
    seerrRepository: SeerrRepository
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
        seerrTitles = fetchSeerCreditTitles(
            item = seedItem,
            personId = personId,
            role = role,
            activeServerId = activeServerId,
            mediaRepository = mediaRepository,
            seerrRepository = seerrRepository
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
private fun ForYouHeader(
    title: String,
    profileImageUrl: String?,
    serverTypeRaw: String?
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        CompactPageHeader(title = title)
        UserProfileAvatar(
            imageUrl = profileImageUrl,
            serverTypeRaw = serverTypeRaw,
            onClick = {},
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(end = 16.dp, top = 12.dp)
                .size(36.dp)
        )
    }
}

@Composable
private fun ForYouFeedPills(
    feed: ForYouFeed,
    onFeedChange: (ForYouFeed) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            ForYouFeed.RECOMMENDATIONS to stringResource(R.string.dashboard_for_you_recommendations),
            ForYouFeed.WATCHED to stringResource(R.string.watched),
            ForYouFeed.AWARDS to stringResource(R.string.dashboard_for_you_awards)
        ).forEach { (tab, label) ->
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
private fun ForYouLoadingSkeleton(
    feed: ForYouFeed,
    onFeedChange: (ForYouFeed) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ForYouFeedPills(
            feed = feed,
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
    onViewAllClick: () -> Unit,
    onItemClick: (BaseItemDto) -> Unit
) {
    if (!isWatchedFeed && section.items.size + section.seerItems.size <= 1) return

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
            if (isWatchedFeed) {
                IconButton(
                    onClick = onViewAllClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = stringResource(R.string.dashboard_view_all),
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

            itemsIndexed(
                items = section.seerItems,
                key = { index, item ->
                    "${section.title}-${SeerrItemIds.detailId(item.tmdbId, item.mediaType)}_$index"
                }
            ) { _, item ->
                SeerTitleCard(
                    item = item,
                    onClick = { onItemClick(item.toSeerDetailItem()) }
                )
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
                text = stringResource(R.string.dashboard_for_you_load_error),
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
                Text(text = stringResource(R.string.try_again))
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
                text = stringResource(R.string.dashboard_for_you_empty_title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.dashboard_for_you_empty_message),
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}