package com.jellycine.app.ui.screens.dashboard.favorites

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.ui.components.common.CompactPageHeader
import com.jellycine.app.ui.components.common.CompactTopText
import com.jellycine.app.ui.components.common.rememberCompactProgress
import com.jellycine.app.ui.screens.dashboard.home.LibraryItemCard
import com.jellycine.app.ui.screens.dashboard.home.UserProfileAvatar
import com.jellycine.app.ui.screens.dashboard.media.ContentType
import com.jellycine.shared.R
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val FAVORITES_VIEW_ALL_PARENT_ID = "__favorites__"

@Composable
fun Favorites(
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (ContentType, String?, String) -> Unit = { _, _, _ -> },
    onProfileClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val authRepository = remember { AuthRepositoryProvider.getInstance(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val compactHeaderProgress = rememberCompactProgress(
        state = listState,
        compactDistance = 92.dp
    )

    val sessionSnapshot by authRepository.observeActiveSession()
        .collectAsState(initial = authRepository.getActiveSessionSnapshot())
    val serverTypeRaw = sessionSnapshot.serverType
    var profileImageUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val url = withContext(Dispatchers.IO) { mediaRepository.getUserProfileImageUrl() }
        if (!url.isNullOrBlank()) profileImageUrl = url
    }

    val headerTitle = stringResource(R.string.favorites)

    var favorites by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(refreshKey) {
        isLoading = true
        error = null
        val result = withContext(Dispatchers.IO) { mediaRepository.getFavoriteItems() }
        result.fold(
            onSuccess = { queryResult ->
                favorites = queryResult.items.orEmpty()
                isLoading = false
            },
            onFailure = { throwable ->
                favorites = emptyList()
                error = throwable.message ?: context.getString(R.string.favorites_load_failed)
                isLoading = false
            }
        )
    }

    val movies = remember(favorites) { favorites.filter { it.type == "Movie" } }
    val shows = remember(favorites) { favorites.filter { it.type == "Series" } }
    val episodes = remember(favorites) { favorites.filter { it.type == "Episode" } }

    val moviesTitle = stringResource(R.string.movies)
    val showsTitle = stringResource(R.string.search_results_shows)
    val episodesTitle = stringResource(R.string.search_results_episodes)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            isLoading -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    FavoritesHeader(title = headerTitle, profileImageUrl = profileImageUrl, serverTypeRaw = serverTypeRaw, onProfileClick = onProfileClick)
                    FavoritesLoadingSkeleton()
                }
            }

            error != null -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    FavoritesHeader(title = headerTitle, profileImageUrl = profileImageUrl, serverTypeRaw = serverTypeRaw, onProfileClick = onProfileClick)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = stringResource(R.string.favorites_load_failed),
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = error ?: stringResource(R.string.favorites_unknown_error),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                            Button(
                                onClick = { refreshKey++ },
                                modifier = Modifier.padding(top = 20.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0080FF))
                            ) {
                                Text(text = stringResource(R.string.try_again), color = Color.White)
                            }
                        }
                    }
                }
            }

            favorites.isEmpty() -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    FavoritesHeader(title = headerTitle, profileImageUrl = profileImageUrl, serverTypeRaw = serverTypeRaw, onProfileClick = onProfileClick)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.VideoLibrary,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.4f),
                                modifier = Modifier.size(60.dp)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = stringResource(R.string.favorites_empty_title),
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.favorites_empty_message),
                                color = Color.White.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    item(key = "favorites_header") {
                        FavoritesHeader(title = headerTitle, profileImageUrl = profileImageUrl, serverTypeRaw = serverTypeRaw, onProfileClick = onProfileClick)
                    }

                    if (movies.isNotEmpty()) {
                        item(key = "section_movies") {
                            FavoriteSection(
                                title = moviesTitle,
                                items = movies,
                                mediaRepository = mediaRepository,
                                isEpisodes = false,
                                onViewAllClick = {
                                    onNavigateToViewAll(ContentType.MOVIES, FAVORITES_VIEW_ALL_PARENT_ID, moviesTitle)
                                },
                                onItemClick = onItemClick
                            )
                        }
                    }

                    if (shows.isNotEmpty()) {
                        item(key = "section_shows") {
                            FavoriteSection(
                                title = showsTitle,
                                items = shows,
                                mediaRepository = mediaRepository,
                                isEpisodes = false,
                                onViewAllClick = {
                                    onNavigateToViewAll(ContentType.SERIES, FAVORITES_VIEW_ALL_PARENT_ID, showsTitle)
                                },
                                onItemClick = onItemClick
                            )
                        }
                    }

                    if (episodes.isNotEmpty()) {
                        item(key = "section_episodes") {
                            FavoriteSection(
                                title = episodesTitle,
                                items = episodes,
                                mediaRepository = mediaRepository,
                                isEpisodes = true,
                                onViewAllClick = {
                                    onNavigateToViewAll(ContentType.EPISODES, FAVORITES_VIEW_ALL_PARENT_ID, episodesTitle)
                                },
                                onItemClick = onItemClick
                            )
                        }
                    }
                }

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
}

@Composable
private fun FavoritesHeader(
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
private fun FavoriteSection(
    title: String,
    items: List<BaseItemDto>,
    mediaRepository: MediaRepository,
    isEpisodes: Boolean,
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
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
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

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
        ) {
            itemsIndexed(
                items = items,
                key = { index, item ->
                    "${item.id ?: "${item.name}_${item.type}_${item.indexNumber ?: index}"}_$index"
                }
            ) { _, item ->
                LibraryItemCard(
                    item = item,
                    mediaRepository = mediaRepository,
                    disableImageEnhancers = isEpisodes,
                    watchedFeedStyle = isEpisodes,
                    onClick = { if (item.id != null) onItemClick(item) }
                )
            }
        }
    }
}

@Composable
private fun FavoritesLoadingSkeleton() {
    val transition = rememberInfiniteTransition(label = "favorites_skeleton")
    val shimmer by transition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "favorites_skeleton_alpha"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        items(3) {
            Column {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth(0.55f)
                        .height(22.dp)
                        .background(
                            color = Color.White.copy(alpha = shimmer * 0.26f),
                            shape = RoundedCornerShape(7.dp)
                        )
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(4) {
                        Column(modifier = Modifier.width(112.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.68f)
                                    .background(
                                        color = Color.White.copy(alpha = shimmer * 0.22f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(12.dp)
                                    .background(
                                        color = Color.White.copy(alpha = shimmer * 0.2f),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}
