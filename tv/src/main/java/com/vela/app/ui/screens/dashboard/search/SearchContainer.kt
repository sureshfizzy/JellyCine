package com.vela.app.ui.screens.dashboard.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vela.shared.R
import com.vela.shared.util.image.disableEmbyPosterEnhancers
import com.vela.data.model.BaseItemDto
import com.vela.data.model.SearchMediaType
import com.vela.data.repository.AuthRepositoryProvider
import com.vela.data.repository.MediaRepositoryProvider
import coil3.imageLoader
import coil3.request.*
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

private object SearchBurstImagePrefetcher {
    private val prefetchedPrimary = ConcurrentHashMap.newKeySet<String>()

    fun clear() { prefetchedPrimary.clear() }

    suspend fun preload(
        items: List<BaseItemDto>,
        mediaRepository: com.vela.data.repository.MediaRepository,
        context: android.content.Context,
        enableImageEnhancers: Boolean
    ) {
        if (items.isEmpty()) return
        val imageLoader = context.imageLoader
        val distinctItems = items.asSequence()
            .filter { !it.id.isNullOrBlank() }
            .distinctBy { it.id }
            .toList()
            .take(36)

        suspend fun enqueuePrimary(item: BaseItemDto) {
            val itemId = item.id ?: return
            if (!prefetchedPrimary.add(itemId)) return
            val enhancers = !item.type.equals("Episode", ignoreCase = true) && enableImageEnhancers
            val url = mediaRepository.getImageUrlString(
                itemId = itemId,
                imageType = "Primary",
                width = 300,
                height = 450,
                quality = 80,
                enableImageEnhancers = enhancers
            )
            if (!url.isNullOrBlank()) {
                imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(url)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .crossfade(false)
                        .allowHardware(true)
                        .allowRgb565(true)
                        .build()
                )
            }
        }

        distinctItems.take(10).forEach { enqueuePrimary(it) }
        if (distinctItems.size > 10) {
            delay(200)
            distinctItems.drop(10).forEach { enqueuePrimary(it) }
        }
    }
}

@Composable
fun SearchContainer(
    onNavigateToDetail: (BaseItemDto) -> Unit = {},
    onCancel: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedDiscoveryTab by viewModel.selectedDiscoveryTab.collectAsStateWithLifecycle()
    val selectedSearchTypes by viewModel.selectedSearchTypes.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val authRepository = remember(context) { AuthRepositoryProvider.getInstance(context) }
    val activeServerId by authRepository.getActiveServerId().collectAsStateWithLifecycle(
        initialValue = authRepository.getActiveSessionSnapshot().activeServerId
    )
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val disablePosterEnhancers = disableEmbyPosterEnhancers()
    val pagerFocusRequester = remember { FocusRequester() }
    val searchIconFocusRequester = remember { FocusRequester() }
    val keyboardFocusRequester = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current

    var isKeyboardOpen by remember { mutableStateOf(false) }
    val isSearchActive = searchQuery.isNotEmpty()
    val hasSearchResults = remember(
        uiState.movieResults, uiState.showResults, uiState.episodeResults,
        uiState.seerrMovieResults, uiState.seerrShowResults
    ) {
        uiState.movieResults.isNotEmpty() || uiState.showResults.isNotEmpty() ||
            uiState.episodeResults.isNotEmpty() || uiState.seerrMovieResults.isNotEmpty() ||
            uiState.seerrShowResults.isNotEmpty()
    }

    LaunchedEffect(disablePosterEnhancers) { SearchBurstImagePrefetcher.clear() }

    LaunchedEffect(activeServerId) { viewModel.refreshSeerrConnectionState(activeServerId) }

    DisposableEffect(lifecycleOwner, activeServerId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshSeerrConnectionState(activeServerId)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val burstPrefetchItems = remember(isSearchActive, uiState.movieResults, uiState.showResults, uiState.episodeResults) {
        if (isSearchActive) {
            buildList {
                addAll(uiState.movieResults.take(12))
                addAll(uiState.showResults.take(12))
                addAll(uiState.episodeResults.take(12))
            }.filter { it.id != null && !it.name.isNullOrBlank() }.distinctBy { it.id }
        } else emptyList()
    }

    LaunchedEffect(burstPrefetchItems.hashCode(), disablePosterEnhancers) {
        if (burstPrefetchItems.isEmpty()) return@LaunchedEffect
        SearchBurstImagePrefetcher.preload(burstPrefetchItems, mediaRepository, context, !disablePosterEnhancers)
    }

    LaunchedEffect(isKeyboardOpen) {
        if (isKeyboardOpen) keyboardFocusRequester.requestFocus()
    }

    BackHandler(enabled = isKeyboardOpen) {
        viewModel.updateSearchQuery("")
        isKeyboardOpen = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val showSeerrDiscovery = selectedDiscoveryTab.seerrCategory != null && uiState.isSeerrConnected &&
            (uiState.seerrDiscoveryLoading || uiState.seerrDiscoveryItems.isNotEmpty())

        if (!isKeyboardOpen) {
            ImmersiveSection(
                movies = if (showSeerrDiscovery) uiState.seerrDiscoveryItems else uiState.suggestions,
                isLoading = if (showSeerrDiscovery) uiState.seerrDiscoveryLoading else uiState.SuggestionsLoading,
                onItemClick = { onNavigateToDetail(it.withJellyfinNavigationId()) },
                modifier = Modifier.fillMaxSize(),
                pagerFocusRequester = pagerFocusRequester,
                onUpPressed = { searchIconFocusRequester.requestFocus() }
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val tabs = if (uiState.isSeerrConnected) SearchDiscoveryTab.entries.toList() else listOf(SearchDiscoveryTab.SUGGESTIONS)
                    tabs.forEach { tab ->
                        DiscoveryTabChip(
                            tab = tab,
                            selected = tab == selectedDiscoveryTab,
                            onClick = { viewModel.selectDiscoveryTab(tab) }
                        )
                    }
                }

                SearchIconButton(
                    focusRequester = searchIconFocusRequester,
                    onClick = { isKeyboardOpen = true },
                    onDownPressed = { pagerFocusRequester.requestFocus() }
                )
            }
        }

        AnimatedVisibility(
            visible = isKeyboardOpen,
            enter = fadeIn(tween(200)) + slideInHorizontally(tween(250)) { -it },
            exit = fadeOut(tween(150)) + slideOutHorizontally(tween(200)) { -it }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                LetterKeyboard(
                    onLetterClick = { viewModel.updateSearchQuery(searchQuery + it) },
                    onDeleteClick = {
                        if (searchQuery.isNotEmpty()) viewModel.updateSearchQuery(searchQuery.dropLast(1))
                    },
                    onClearClick = { viewModel.updateSearchQuery("") },
                    onBackPressed = {
                        viewModel.updateSearchQuery("")
                        isKeyboardOpen = false
                    },
                    focusRequester = keyboardFocusRequester,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(260.dp)
                        .padding(start = 24.dp, top = 24.dp, bottom = 24.dp, end = 16.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 16.dp, top = 24.dp, end = 32.dp, bottom = 24.dp)
                ) {
                    Surface(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = searchQuery.ifEmpty { stringResource(R.string.search_hint) },
                                color = if (searchQuery.isEmpty()) Color.White.copy(alpha = 0.4f) else Color.White,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (isSearchActive) {
                        SearchTypeChips(
                            selectedTypes = selectedSearchTypes,
                            onToggle = viewModel::toggleSearchType,
                            modifier = Modifier.padding(bottom = 14.dp)
                        )

                        if (uiState.isSearching) {
                            SearchResultsViewSkeleton(modifier = Modifier.weight(1f))
                        } else if (hasSearchResults) {
                            SearchResultsView(
                                uiState = uiState,
                                onItemClick = onNavigateToDetail,
                                modifier = Modifier.weight(1f)
                            )
                        } else {
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                EmptySearchState(
                                    message = uiState.error ?: stringResource(R.string.search_no_results)
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .offset(y = (-32).dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.2f),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.search_empty_default),
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchIconButton(
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onDownPressed: () -> Unit = {}
) {
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .size(48.dp)
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> { onClick(); true }
                        Key.DirectionDown -> { onDownPressed(); true }
                        else -> false
                    }
                } else false
            }
            .clickable { onClick() }
            .focusable(),
        color = if (isFocused) Color.White else Color.White.copy(alpha = 0.15f),
        shape = CircleShape
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.search),
                tint = if (isFocused) Color.Black else Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun LetterKeyboard(
    onLetterClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    onClearClick: () -> Unit,
    onBackPressed: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val keys = remember {
        listOf(
            "a", "b", "c", "d", "e", "f",
            "g", "h", "i", "j", "k", "l",
            "m", "n", "o", "p", "q", "r",
            "s", "t", "u", "v", "w", "x",
            "y", "z", "1", "2", "3", "4",
            "5", "6", "7", "8", "9", "0"
        )
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = modifier
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Back) {
                    onBackPressed(); true
                } else false
            },
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(keys) { letter ->
            KeyboardKey(label = letter, onClick = { onLetterClick(letter) })
        }
        item { KeyboardKey(label = "␣", onClick = { onLetterClick(" ") }) }
        item { KeyboardKey(label = "⌫", onClick = onDeleteClick) }
        item { KeyboardKey(label = "CLR", onClick = onClearClick) }
    }
}

@Composable
private fun KeyboardKey(label: String, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .background(
                color = if (isFocused) Color.White else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp)
            )
            .onFocusChanged { isFocused = it.isFocused }
            .clickable { onClick() }
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (isFocused) Color.Black else Color.White,
            fontSize = if (label.length > 1) 14.sp else 18.sp,
            fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
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
                    containerColor = if (selected) Color.White else Color.White.copy(alpha = 0.12f)
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
    SearchMediaType.MOVIE -> stringResource(R.string.movies)
    SearchMediaType.SERIES -> stringResource(R.string.search_results_shows)
    SearchMediaType.EPISODE -> stringResource(R.string.search_results_episodes)
}

private fun BaseItemDto.withJellyfinNavigationId(): BaseItemDto {
    val jellyfinMediaId = providerIds?.get("jellyfin")?.takeIf { it.isNotBlank() } ?: return this
    return copy(id = jellyfinMediaId)
}

@Composable
fun SearchDiscoveryTab.label(): String = when (this) {
    SearchDiscoveryTab.SUGGESTIONS -> stringResource(R.string.suggestions)
    SearchDiscoveryTab.TRENDING -> stringResource(R.string.search_discover_trending)
    SearchDiscoveryTab.POPULAR_MOVIES -> stringResource(R.string.search_discover_popular_movies)
    SearchDiscoveryTab.POPULAR_SHOWS -> stringResource(R.string.search_discover_popular_shows)
    SearchDiscoveryTab.UPCOMING_MOVIES -> stringResource(R.string.search_discover_upcoming_movies)
    SearchDiscoveryTab.UPCOMING_SHOWS -> stringResource(R.string.search_discover_upcoming_shows)
}