package com.jellycine.ui.screens.dashboard.search

import androidx.compose.foundation.text.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellycine.resources.*
import org.jetbrains.compose.resources.stringResource
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.SearchMediaType
import com.jellycine.data.repository.AuthRepository
import com.jellycine.data.repository.MediaRepository
import com.jellycine.util.ConcurrentCache
import com.jellycine.util.platformBitmapConfig
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.*
import kotlinx.coroutines.delay

private class SearchBurstImagePrefetcher {
    private val prefetchedPrimary = ConcurrentCache<String, Boolean>()

    suspend fun preload(
        items: List<BaseItemDto>,
        mediaRepository: MediaRepository,
        context: PlatformContext,
        enableImageEnhancers: Boolean
    ) {
        if (items.isEmpty()) return
        val imageLoader = SingletonImageLoader.get(context)
        val distinctItems = items.asSequence()
            .filter { !it.id.isNullOrBlank() }
            .distinctBy { it.id }
            .toList()
            .take(36)

        suspend fun enqueuePrimary(item: BaseItemDto) {
            val itemId = item.id ?: return
            if (prefetchedPrimary.putIfAbsent(itemId, true) != null) return

            val itemEnhancersEnabled = if (item.type.equals("Episode", ignoreCase = true)) {
                false
            } else {
                enableImageEnhancers
            }

            val primaryUrl = mediaRepository.getImageUrlString(
                itemId = itemId,
                imageType = "Primary",
                width = 300,
                height = 450,
                quality = 80,
                enableImageEnhancers = itemEnhancersEnabled
            )

            if (!primaryUrl.isNullOrBlank()) {
                imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(primaryUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .crossfade(false)
                        .platformBitmapConfig(allowRgb565 = true)
                        .build()
                )
            }
        }

        distinctItems.take(10).forEach { item ->
            enqueuePrimary(item)
        }

        if (distinctItems.size > 10) {
            delay(200)
            distinctItems.drop(10).forEach { item ->
                enqueuePrimary(item)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContainer(
    mediaRepository: MediaRepository,
    authRepository: AuthRepository,
    disablePosterEnhancers: Boolean,
    modifier: Modifier = Modifier,
    seerr: SearchSeerrSource? = null,
    onNavigateToDetail: (BaseItemDto) -> Unit = {},
    onCancel: () -> Unit = {}
) {
    val viewModel: SearchViewModel = viewModel { SearchViewModel(mediaRepository, authRepository, seerr) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedDiscoveryTab by viewModel.selectedDiscoveryTab.collectAsStateWithLifecycle()
    val selectedSearchTypes by viewModel.selectedSearchTypes.collectAsStateWithLifecycle()

    val context = LocalPlatformContext.current
    val activeServerId by authRepository.getActiveServerId().collectAsStateWithLifecycle(
        initialValue = authRepository.getActiveSessionSnapshot().activeServerId
    )
    val prefetcher = remember(disablePosterEnhancers) { SearchBurstImagePrefetcher() }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isSearchActive = searchQuery.isNotEmpty()
    val hasSearchResults = remember(
        uiState.movieResults,
        uiState.showResults,
        uiState.episodeResults,
        uiState.seerrMovieResults,
        uiState.seerrShowResults
    ) {
        uiState.movieResults.isNotEmpty() ||
            uiState.showResults.isNotEmpty() ||
            uiState.episodeResults.isNotEmpty() ||
            uiState.seerrMovieResults.isNotEmpty() ||
            uiState.seerrShowResults.isNotEmpty()
    }
    val burstPrefetchItems = remember(
        isSearchActive,
        uiState.movieResults,
        uiState.showResults,
        uiState.episodeResults
    ) {
        if (isSearchActive) {
            buildList {
                addAll(uiState.movieResults.take(12))
                addAll(uiState.showResults.take(12))
                addAll(uiState.episodeResults.take(12))
            }.filter { it.id != null && !it.name.isNullOrBlank() }
                .distinctBy { it.id }
        } else {
            emptyList()
        }
    }

    LaunchedEffect(activeServerId) {
        viewModel.refreshSeerrConnectionState(activeServerId)
    }

    DisposableEffect(lifecycleOwner, activeServerId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSeerrConnectionState(activeServerId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(burstPrefetchItems.hashCode(), disablePosterEnhancers) {
        if (burstPrefetchItems.isEmpty()) return@LaunchedEffect
        prefetcher.preload(
            items = burstPrefetchItems,
            mediaRepository = mediaRepository,
            context = context,
            enableImageEnhancers = !disablePosterEnhancers
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isSearchActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 120.dp)
            ) {
                if (uiState.isSearching) {
                    SearchResultsViewSkeleton()
                } else if (hasSearchResults) {
                    SearchResultsView(
                        uiState = uiState,
                        mediaRepository = mediaRepository,
                        disablePosterEnhancers = disablePosterEnhancers,
                        onItemClick = onNavigateToDetail,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptySearchState(
                            message = uiState.error ?: stringResource(Res.string.search_no_results)
                        )
                    }
                }
            }
        } else {
            val showSeerrDiscovery = selectedDiscoveryTab.seerrCategory != null && uiState.isSeerrConnected &&
                (uiState.seerrDiscoveryLoading || uiState.seerrDiscoveryItems.isNotEmpty())
            ImmersiveSection(
                mediaRepository = mediaRepository,
                movies = if (showSeerrDiscovery) uiState.seerrDiscoveryItems else uiState.suggestions,
                isLoading = if (showSeerrDiscovery) uiState.seerrDiscoveryLoading else uiState.SuggestionsLoading,
                onItemClick = { item ->
                    onNavigateToDetail(item.withJellyfinNavigationId())
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 20.dp),
                discoveryTabs = if (uiState.isSeerrConnected) SearchDiscoveryTab.entries.toList() else listOf(SearchDiscoveryTab.SUGGESTIONS),
                selectedDiscoveryTab = selectedDiscoveryTab,
                onDiscoveryTabClick = viewModel::selectDiscoveryTab
            )
        }

        if (isSearchActive) {
            SearchTypeChips(
                selectedTypes = selectedSearchTypes,
                onToggle = viewModel::toggleSearchType,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 72.dp, end = 16.dp)
            )
        }

        SearchBar(
            query = searchQuery,
            onQueryChange = viewModel::updateSearchQuery,
            onCancel = onCancel,
            onSearch = viewModel::executeSearch,
            focusRequester = focusRequester,
            keyboardController = keyboardController,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun SearchTypeChips(
    selectedTypes: Set<SearchMediaType>,
    onToggle: (SearchMediaType) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchMediaType.entries.forEach { type ->
            val selected = type in selectedTypes
            Card(
                modifier = Modifier.clickable { onToggle(type) },
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) {
                        Color.White
                    } else {
                        Color.White.copy(alpha = 0.12f)
                    }
                ),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    text = type.label(),
                    color = if (selected) Color.Black else Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchMediaType.label(): String = when (this) {
    SearchMediaType.MOVIE -> stringResource(Res.string.movies)
    SearchMediaType.SERIES -> stringResource(Res.string.search_results_shows)
    SearchMediaType.EPISODE -> stringResource(Res.string.search_results_episodes)
}

private fun BaseItemDto.withJellyfinNavigationId(): BaseItemDto {
    val jellyfinMediaId = providerIds?.get("jellyfin")?.takeIf { it.isNotBlank() } ?: return this
    return copy(id = jellyfinMediaId)
}

@Composable
fun SearchDiscoveryTab.label(): String = when (this) {
    SearchDiscoveryTab.SUGGESTIONS -> stringResource(Res.string.suggestions)
    SearchDiscoveryTab.TRENDING -> stringResource(Res.string.search_discover_trending)
    SearchDiscoveryTab.POPULAR_MOVIES -> stringResource(Res.string.search_discover_popular_movies)
    SearchDiscoveryTab.POPULAR_SHOWS -> stringResource(Res.string.search_discover_popular_shows)
    SearchDiscoveryTab.UPCOMING_MOVIES -> stringResource(Res.string.search_discover_upcoming_movies)
    SearchDiscoveryTab.UPCOMING_SHOWS -> stringResource(Res.string.search_discover_upcoming_shows)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    keyboardController: SoftwareKeyboardController?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(25.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(Res.string.search),
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                onSearch()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                    
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.search_hint),
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                }
                
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(Res.string.clear_search),
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        TextButton(
            onClick = onCancel,
            colors = ButtonDefaults.textButtonColors(
                contentColor = Color.White
            )
        ) {
            Text(
                text = stringResource(Res.string.cancel),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}