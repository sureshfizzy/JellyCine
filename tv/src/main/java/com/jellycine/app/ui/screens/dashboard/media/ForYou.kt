package com.jellycine.app.ui.screens.dashboard.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.jellycine.app.ui.screens.dashboard.home.UserProfileAvatar
import com.jellycine.app.ui.screens.dashboard.home.LibraryItemCard
import androidx.compose.foundation.layout.statusBarsPadding
import com.jellycine.shared.util.image.disableEmbyPosterEnhancers
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.RecommendationDto
import com.jellycine.data.model.title
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.AwardsRepositoryProvider
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.shared.recommendations.loadWatchedFeed
import com.jellycine.shared.ui.components.common.ShimmerPosterRail
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private data class RecommendationSectionUi(
    val title: String,
    val items: List<BaseItemDto>
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
    val awardsRepository = remember(context) { AwardsRepositoryProvider.getInstance(context) }
    val disablePosterEnhancers = disableEmbyPosterEnhancers()
    val scope = rememberCoroutineScope()
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
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val activeSections = if (showWatchedTab) watchedSections else sections
        val hasAnySections = sections.isNotEmpty() || watchedSections.isNotEmpty()

        Column(modifier = Modifier.fillMaxSize()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = Color.Black
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val awardsOnBack = awardsBack
                        if (feed == ForYouFeed.AWARDS && awardsOnBack != null) {
                            IconButton(onClick = awardsOnBack, modifier = Modifier.size(34.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.size(8.dp))
                        }
                        Text(
                            text = if (feed == ForYouFeed.AWARDS) {
                                awardsHeaderTitle ?: stringResource(R.string.dashboard_for_you_awards)
                            } else {
                                headerTitle
                            },
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    UserProfileAvatar(
                        imageUrl = profileImageUrl,
                        serverTypeRaw = sessionSnapshot.serverType,
                        onClick = {},
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    feed == ForYouFeed.AWARDS -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (awardsBack == null) {
                                ForYouFeedPills(
                                    feed = feed,
                                    onFeedChange = { feed = it }
                                )
                            }
                            AwardsContent(
                                awardsRepository = awardsRepository,
                                onItemClick = onItemClick,
                                onHeaderTitleChange = { awardsHeaderTitle = it },
                                onBackChange = { awardsBack = it },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    isLoading -> {
                        ForYouLoadingSkeleton(
                            feed = feed,
                            onFeedChange = { feed = it }
                        )
                    }

                    hasAnySections -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 110.dp)
                        ) {
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
                            items = items
                        )
                    }
                }
        }

        val sections = rawSections
            .groupBy { it.title }
            .map { (title, groupedSections) ->
                RecommendationSectionUi(
                    title = title,
                    items = groupedSections
                        .flatMap { it.items }
                        .distinctBy { item -> item.id ?: item.name.orEmpty() }
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