package com.vela.app.ui.screens.dashboard.media

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Check
import com.vela.app.ui.screens.dashboard.home.ImageLoader
import com.vela.app.ui.screens.dashboard.home.ImagePreloader
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.animation.*
import coil3.compose.AsyncImage
import coil3.request.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vela.shared.R
import com.vela.app.ui.components.common.BackButton
import com.vela.app.ui.components.common.CompactPageHeader
import com.vela.app.ui.components.common.CompactTopLogo
import com.vela.app.ui.components.common.CompactTopText
import com.vela.app.ui.components.common.compactHeaderLogo
import com.vela.app.ui.components.common.containerWidthDp
import com.vela.app.ui.components.common.isTabletLayout
import com.vela.app.ui.components.common.rememberCompactProgress
import com.vela.app.ui.screens.dashboard.home.LibraryItemCard
import com.vela.shared.ui.components.common.FilterChip as MediaFilterChip
import com.vela.shared.ui.components.common.PosterCountBadge
import com.vela.shared.ui.components.common.WatchedIndicatorBadge
import com.vela.shared.playback.UserDataRefreshSignals
import com.vela.shared.util.image.DisableEmbyPosterEnhancers
import com.vela.shared.util.image.WarmImageUrl
import com.vela.data.repository.MediaRepository
import com.vela.data.repository.MediaRepositoryProvider
import com.vela.data.model.BaseItemDto
import com.vela.data.model.SeerrRequestState
import com.vela.data.model.SeerrCatalog
import com.vela.app.ui.components.common.SeerrTopBadges
import com.vela.app.ui.screens.dashboard.favorites.FAVORITES_VIEW_ALL_PARENT_ID
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun ViewAllScreen(
    contentType: ContentType,
    parentId: String? = null,
    title: String = "",
    genreId: String? = null,
    onBackPressed: () -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    onPlayFromBeginning: (String) -> Unit = {},
    viewModel: ViewAllViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val userDataRefreshEvent by UserDataRefreshSignals.refreshEvent.collectAsState()

    val context = LocalContext.current
    val screenWidthDp = containerWidthDp()
    val isTablet = isTabletLayout(screenWidthDp)
    val isSeerrCatalog = contentType.isSeerrCatalog()
    val isLibraryCatalog = contentType.isLibraryCatalog() && !parentId.isNullOrBlank()
    val isGenreCatalog = contentType.isGenreCatalog()
    val isAward = contentType == ContentType.AWARD
    val isWatchedViewAll = parentId == WATCHED_VIEW_ALL_PARENT_ID
    val isFavoritesViewAll = parentId == FAVORITES_VIEW_ALL_PARENT_ID
    val isWatchedEpisodeViewAll = (isWatchedViewAll || isFavoritesViewAll) && contentType == ContentType.EPISODES
    val usesCompactHeader = isSeerrCatalog || isLibraryCatalog || isGenreCatalog || isAward

    val gridCells = remember(screenWidthDp) {
        if (screenWidthDp >= 1200.dp) {
            GridCells.Adaptive(minSize = 160.dp)
        } else if (screenWidthDp >= 600.dp) {
            GridCells.Adaptive(minSize = 140.dp)
        } else {
            GridCells.Fixed(3)
        }
    }
    val viewAllGridCells = if (isWatchedEpisodeViewAll) {
        if (isTablet) GridCells.Adaptive(minSize = 200.dp) else GridCells.Fixed(2)
    } else {
        gridCells
    }

    val horizontalPadding = if (isTablet) 24.dp else 16.dp
    val verticalSpacing = if (isTablet) 20.dp else 16.dp
    val horizontalSpacing = if (isTablet) 16.dp else 12.dp

    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val seerrLogoUrl = remember(contentType, parentId) {
        when (contentType) {
            ContentType.SEERR_STUDIO -> SeerrCatalog.popularStudios()
            ContentType.SEERR_NETWORK -> SeerrCatalog.popularNetworks()
            else -> emptyList()
        }.firstOrNull { item -> item.id == parentId }
            ?.logoUrl
    }
    var showSortSheet by remember { mutableStateOf(false) }
    var overflowItem by remember { mutableStateOf<BaseItemDto?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val browseTabs = remember(contentType, isLibraryCatalog, isWatchedViewAll, isFavoritesViewAll) {
        if (isLibraryCatalog && !isWatchedViewAll && !isFavoritesViewAll) {
            libraryBrowseTabs(contentType)
        } else {
            emptyList()
        }
    }
    val showLibraryChrome = browseTabs.isNotEmpty()
    val successMessage = stringResource(R.string.item_action_success)
    val failedMessage = stringResource(R.string.item_action_failed)
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val resolvedTitle = uiState.folderTitle?.takeIf { it.isNotBlank() }
        ?: title.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.view_all_title)
    val filterSignature = remember(
        uiState.sortBy,
        uiState.sortOrder,
        uiState.selectedGenres,
        uiState.browseTab,
        uiState.folderTitle
    ) {
        listOf(
            uiState.sortBy,
            uiState.sortOrder,
            uiState.browseTab.name,
            uiState.folderTitle.orEmpty(),
            uiState.selectedGenres.toList().sorted().joinToString("|")
        ).joinToString("::")
    }
    var lastAppliedFilterSignature by rememberSaveable(contentType, parentId, genreId) {
        mutableStateOf(filterSignature)
    }
    val genreIncludeItemTypes = remember(contentType, isWatchedViewAll, isFavoritesViewAll) {
        if (isWatchedViewAll || isFavoritesViewAll) {
            null
        } else when (contentType) {
            ContentType.MOVIES, ContentType.MOVIES_GENRE -> "Movie"
            ContentType.SERIES, ContentType.TVSHOWS_GENRE -> "Series"
            ContentType.ALL -> "Movie,Series"
            ContentType.EPISODES,
            ContentType.SEERR_STUDIO,
            ContentType.SEERR_NETWORK,
            ContentType.AWARD -> null
        }
    }
    var serverGenres by rememberSaveable(contentType, parentId, genreId) {
        mutableStateOf(emptyList<String>())
    }
    LaunchedEffect(contentType, parentId, genreId, genreIncludeItemTypes) {
        if (genreIncludeItemTypes == null) {
            serverGenres = emptyList()
        } else {
            mediaRepository.getFilteredGenres(
                parentId = parentId,
                includeItemTypes = genreIncludeItemTypes
            ).fold(
                onSuccess = { genres ->
                    serverGenres = genres.mapNotNull { it.name?.trim()?.takeIf(String::isNotEmpty) }
                },
                onFailure = {
                    serverGenres = emptyList()
                }
            )
        }
    }
    val availableGenres = remember(serverGenres, uiState.selectedGenres) {
        serverGenres
            .plus(uiState.selectedGenres)
            .distinct()
            .sorted()
    }
    val displayItems = remember(items, uiState.selectedGenres) {
        val filteredItems = if (uiState.selectedGenres.size > 1) {
            items.filter { item ->
                val itemGenres = item.genres.orEmpty().toSet()
                uiState.selectedGenres.all { genre -> itemGenres.contains(genre) }
            }
        } else {
            items
        }
        filteredItems.distinctBy(::viewAllItemKey)
    }
    val headerTotalCount = uiState.totalItems
    val headerCountText = if (headerTotalCount > 0) {
        stringResource(R.string.view_all_count, displayItems.size, headerTotalCount)
    } else {
        null
    }
    val compactHeaderProgress = rememberCompactProgress(
        state = gridState,
        compactDistance = if (isTablet) 132.dp else 92.dp
    )
    val compactHeader = if (usesCompactHeader) compactHeaderProgress else 0f

    // Load initial data
    LaunchedEffect(contentType, parentId, genreId) {
        viewModel.ensureItemsLoaded(contentType, parentId, genreId)
    }

    LaunchedEffect(userDataRefreshEvent, contentType, parentId, genreId) {
        if (userDataRefreshEvent == null || !contentType.includesSeriesItems()) {
            return@LaunchedEffect
        }
        viewModel.loadItems(contentType, parentId, refresh = true, genreId = genreId)
    }

    LaunchedEffect(filterSignature) {
        if (lastAppliedFilterSignature != filterSignature) {
            gridState.scrollToItem(0)
            lastAppliedFilterSignature = filterSignature
        }
    }

    LaunchedEffect(
        gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index,
        items.size
    ) {
        val lastVisibleIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val loadMore = when {
            items.isEmpty() -> true
            else -> lastVisibleIndex >= displayItems.size - 5
        }

        if (loadMore && uiState.hasMorePages && !uiState.isLoading) {
            viewModel.loadMoreItems(contentType, parentId, genreId)
        }
    }

    LaunchedEffect(displayItems, gridState.firstVisibleItemIndex) {
        val from = gridState.firstVisibleItemIndex.coerceAtLeast(0)
        val window = displayItems.drop(from).take(18)
        if (window.isNotEmpty()) {
            ImagePreloader.preloadCriticalImages(window, mediaRepository, context)
        }
    }

    LaunchedEffect(uiState.actionMessage) {
        val message = uiState.actionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            if (message == "ok") successMessage else failedMessage
        )
        viewModel.clearActionMessage()
    }

    val handleItemClick: (BaseItemDto) -> Unit = { item ->
        when {
            item.type.equals("Genre", ignoreCase = true) -> {
                viewModel.openGenre(item, contentType, parentId, genreId)
            }
            uiState.browseTab == LibraryBrowseTab.FOLDERS &&
                (item.isFolder == true || item.type.equals("Folder", ignoreCase = true)) -> {
                viewModel.openFolder(item, contentType, parentId, genreId)
            }
            else -> onItemClick(item)
        }
    }

    @Composable
    fun HeaderContent(
        modifier: Modifier = Modifier,
        compactProgress: Float = 0f
    ) {
        Column(modifier = modifier.fillMaxWidth()) {
            if (!isSeerrCatalog || seerrLogoUrl == null) {
                CompactPageHeader(
                    title = resolvedTitle,
                    subtitle = headerCountText,
                    includeStatusBarsPadding = false,
                    horizontalPadding = if (usesCompactHeader) 0.dp else horizontalPadding,
                    verticalPadding = if (usesCompactHeader) 18.dp else 20.dp,
                    titleFontSize = if (isTablet) 28.sp else 24.sp,
                    titleFontWeight = FontWeight.Bold,
                    subtitleFontSize = if (isTablet) 15.sp else 13.sp,
                    centered = usesCompactHeader
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (usesCompactHeader) 0.dp else horizontalPadding,
                            vertical = if (usesCompactHeader) 18.dp else 20.dp
                        ),
                    horizontalAlignment = if (usesCompactHeader) {
                        Alignment.CenterHorizontally
                    } else {
                        Alignment.Start
                    }
                ) {
                    WarmImageUrl(imageUrl = seerrLogoUrl, allowRgb565 = true)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (isTablet) 132.dp else 92.dp)
                            .padding(horizontal = if (isTablet) 128.dp else 52.dp)
                            .clip(RoundedCornerShape(if (isTablet) 18.dp else 14.dp))
                            .compactHeaderLogo(compactProgress),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(seerrLogoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = resolvedTitle,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    if (headerCountText != null) {
                        Text(
                            text = headerCountText,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = if (isTablet) 15.sp else 13.sp,
                            textAlign = if (usesCompactHeader) TextAlign.Center else TextAlign.Unspecified,
                            modifier = Modifier.padding(top = if (usesCompactHeader) 8.dp else 4.dp)
                        )
                    }
                }
            }
            if (showLibraryChrome) {
                LibraryBrowseTabRow(
                    tabs = browseTabs,
                    selected = uiState.browseTab,
                    contentType = contentType,
                    onSelected = { tab ->
                        viewModel.setBrowseTab(tab, contentType, parentId, genreId)
                    }
                )
            }
        }
    }

    BackHandler {
        if (!viewModel.popBrowseLevel(contentType, parentId, genreId)) {
            onBackPressed()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            if (!usesCompactHeader) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding(),
                    color = Color.Black
                ) {
                    HeaderContent()
                }
            }

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    uiState.isLoading && items.isEmpty() -> {
                        LazyVerticalGrid(
                            columns = viewAllGridCells,
                            contentPadding = PaddingValues(
                                start = horizontalPadding,
                                top = if (usesCompactHeader) 0.dp else 16.dp,
                                end = horizontalPadding,
                                bottom = 120.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            compactHeaderItem(usesCompactHeader) {
                                HeaderContent(
                                    modifier = Modifier
                                        .statusBarsPadding()
                                        .padding(bottom = 2.dp),
                                    compactProgress = compactHeader
                                )
                            }

                            items(24) {
                                SkeletonPosterCard()
                            }
                        }
                    }
                    
                    uiState.error != null && items.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 48.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.view_all_error_title),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = uiState.error ?: stringResource(R.string.view_all_error_message),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                Button(
                                    onClick = { viewModel.loadItems(contentType, parentId, refresh = true, genreId = genreId) },
                                    modifier = Modifier.padding(top = 24.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0080FF)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.try_again),
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                    
                    displayItems.isEmpty() && uiState.recommendationSections.isEmpty() -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            if (usesCompactHeader) {
                                HeaderContent(
                                    modifier = Modifier
                                        .statusBarsPadding()
                                        .padding(horizontal = horizontalPadding),
                                    compactProgress = 0f
                                )
                            }
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = stringResource(R.string.view_all_empty_title),
                                        color = Color.White,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = stringResource(R.string.view_all_empty_message),
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 14.sp,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    else -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                columns = viewAllGridCells,
                                state = gridState,
                                contentPadding = PaddingValues(
                                    start = horizontalPadding,
                                    top = if (usesCompactHeader) 0.dp else 16.dp,
                                    end = horizontalPadding,
                                    bottom = 120.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                compactHeaderItem(usesCompactHeader) {
                                    HeaderContent(
                                        modifier = Modifier
                                            .statusBarsPadding()
                                            .padding(bottom = 2.dp),
                                        compactProgress = compactHeader
                                    )
                                }

                                if (uiState.browseTab == LibraryBrowseTab.RECOMMENDED &&
                                    uiState.recommendationSections.isNotEmpty()
                                ) {
                                    uiState.recommendationSections.forEach { section ->
                                        item(
                                            key = "rec_${section.recommendationType}_${section.baselineName}",
                                            span = { GridItemSpan(maxLineSpan) }
                                        ) {
                                            val title = section.baselineName?.takeIf { it.isNotBlank() }?.let { name ->
                                                stringResource(recommendationTitleRes(section.recommendationType), name)
                                            } ?: stringResource(R.string.library_tab_recommended)
                                            Text(
                                                text = title,
                                                color = Color.White,
                                                fontSize = if (isTablet) 20.sp else 18.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                            )
                                        }
                                        item(
                                            key = "rec_row_${section.recommendationType}_${section.baselineName}",
                                            span = { GridItemSpan(maxLineSpan) }
                                        ) {
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                contentPadding = PaddingValues(bottom = 12.dp)
                                            ) {
                                                lazyRowItems(
                                                    items = section.items,
                                                    key = ::viewAllItemKey
                                                ) { item ->
                                                    LibraryItemCard(
                                                        item = item,
                                                        mediaRepository = mediaRepository,
                                                        onClick = { handleItemClick(item) },
                                                        onLongClick = { overflowItem = item }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    items(
                                        items = displayItems,
                                        key = ::viewAllItemKey
                                    ) { item ->
                                        if (isWatchedEpisodeViewAll) {
                                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                                LibraryItemCard(
                                                    item = item,
                                                    mediaRepository = mediaRepository,
                                                    disableImageEnhancers = true,
                                                    watchedFeedStyle = true,
                                                    onClick = { handleItemClick(item) },
                                                    onLongClick = { overflowItem = item }
                                                )
                                            }
                                        } else {
                                            PosterCard(
                                                item = item,
                                                isTablet = isTablet,
                                                mediaRepository = mediaRepository,
                                                watchedViewAll = isWatchedViewAll,
                                                onClick = { handleItemClick(item) },
                                                onLongClick = { overflowItem = item }
                                            )
                                        }
                                    }
                                }

                                if (uiState.hasMorePages) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (uiState.isLoading) {
                                                CircularProgressIndicator(
                                                    color = Color(0xFF0080FF),
                                                    modifier = Modifier.size(32.dp),
                                                    strokeWidth = 3.dp
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = uiState.isLoading && displayItems.isNotEmpty(),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = if (usesCompactHeader) 62.dp else 8.dp),
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Surface(
                                    color = Color(0xCC111111),
                                    shape = RoundedCornerShape(999.dp),
                                    tonalElevation = 2.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = Color(0xFF0080FF),
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = stringResource(R.string.loading),
                                            color = Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        BackButton(
            onClick = {
                if (!viewModel.popBrowseLevel(contentType, parentId, genreId)) {
                    onBackPressed()
                }
            },
            modifier = Modifier.align(Alignment.TopStart)
        )

        if (isSeerrCatalog && seerrLogoUrl != null) {
            CompactTopLogo(
                imageUrl = seerrLogoUrl,
                contentDescription = resolvedTitle,
                progress = compactHeader,
                isTablet = isTablet,
                onClick = {
                    coroutineScope.launch {
                        gridState.animateScrollToItem(0)
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 48.dp)
            )
        }

        if (isLibraryCatalog || isGenreCatalog) {
            CompactTopText(
                text = resolvedTitle,
                progress = compactHeader,
                isTablet = isTablet,
                onClick = {
                    coroutineScope.launch {
                        gridState.animateScrollToItem(0)
                    }
                },
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 48.dp)
            )
        }

        if (usesCompactHeader && headerCountText != null) {
            CompactTopText(
                text = headerCountText,
                progress = compactHeader,
                isTablet = isTablet,
                alignEnd = true,
                fontSize = if (isTablet) 13.sp else 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        val showSort = !isSeerrCatalog &&
            !isWatchedEpisodeViewAll &&
            !isAward &&
            (!showLibraryChrome || uiState.browseTab.supportsSort())
        if (showSort) {
            SortFAB(
                onClick = { showSortSheet = true },
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        if (showSortSheet && !isSeerrCatalog && !isWatchedEpisodeViewAll && !isAward) {
            SortBottomSheet(
                currentSortBy = uiState.sortBy,
                currentSortOrder = uiState.sortOrder,
                availableGenres = availableGenres,
                selectedGenres = uiState.selectedGenres,
                onSortSelected = { sortBy, sortOrder ->
                    viewModel.setSort(sortBy, sortOrder, contentType, parentId, genreId)
                },
                onGenreToggle = { genre ->
                    viewModel.toggleGenreFilter(genre, contentType, parentId, genreId)
                },
                onClearFilters = { viewModel.clearFilters(contentType, parentId, genreId) },
                onDismiss = { showSortSheet = false }
            )
        }

        overflowItem?.let { item ->
            ItemOverflowSheet(
                item = item,
                isAdministrator = uiState.isAdministrator,
                mediaRepository = mediaRepository,
                onDismiss = { overflowItem = null },
                onPlayFromBeginning = { playableId ->
                    overflowItem = null
                    onPlayFromBeginning(playableId)
                },
                onItemMutated = { updated ->
                    updated.id?.let { id -> viewModel.updateLocalItem(id) { updated } }
                },
                onItemDeleted = { id -> viewModel.removeLocalItem(id) },
                onMessage = { success -> viewModel.showActionResult(success) }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 96.dp)
        )
    }
}

private fun LazyGridScope.compactHeaderItem(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    if (!visible) return

    item(span = { GridItemSpan(maxLineSpan) }) {
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PosterCard(
    item: BaseItemDto,
    isTablet: Boolean,
    mediaRepository: MediaRepository,
    watchedViewAll: Boolean = false,
    showSeerrBadge: Boolean = true,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null
) {
    val disablePosterEnhancers = DisableEmbyPosterEnhancers()
    val isSeerrSource = item.id?.startsWith("seerr:") == true
    val displayName = if (item.type == "Episode" && !item.seriesName.isNullOrBlank()) {
        item.seriesName!!
    } else {
        item.name ?: stringResource(R.string.search_result_unknown_title)
    }
    val corner = if (isTablet) 18.dp else 16.dp

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(corner))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
        ) {
            ImageLoader(
                itemId = item.id,
                seriesId = item.seriesId,
                contentDescription = displayName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                cornerRadius = if (isTablet) 18 else 16,
                crossfadeMillis = 0,
                mediaRepository = mediaRepository,
                imageMetadata = item,
                itemType = item.type,
                hasImageEnhancers = !disablePosterEnhancers && !watchedViewAll
            )

            if (isSeerrSource && showSeerrBadge) {
                SeerrTopBadges(
                    requestState = SeerrRequestState.NONE,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }

            val itemCount = when {
                item.type == "Series" -> {
                    when {
                        item.userData?.unplayedItemCount != null -> item.userData?.unplayedItemCount
                        item.episodeCount != null && item.episodeCount!! > 0 -> item.episodeCount
                        item.recursiveItemCount != null && item.recursiveItemCount!! > 0 -> item.recursiveItemCount
                        item.childCount != null && item.childCount!! > 0 -> item.childCount
                        else -> null
                    }
                }
                else -> item.childCount ?: item.recursiveItemCount
            }

            val isFullyWatched = item.type == "Series" &&
                item.userData?.unplayedItemCount == 0

            itemCount?.takeIf { it > 0 && !watchedViewAll }?.let { count ->
                PosterCountBadge(
                    count = count,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 4.dp)
                )
            }

            if (!watchedViewAll && (isFullyWatched || (itemCount == null && item.userData?.played == true))) {
                WatchedIndicatorBadge(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = displayName,
            color = Color.White,
            fontSize = if (isTablet) 15.sp else 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = if (isTablet) 18.sp else 16.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp)
        )

        item.productionYear?.let { year ->
            Text(
                text = year.toString(),
                color = Color.White.copy(alpha = 0.6f),
                fontSize = if (isTablet) 13.sp else 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
internal fun SkeletonPosterCard() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.05f),
                            Color.White.copy(alpha = 0.02f)
                        )
                    ),
                    shape = RoundedCornerShape(16.dp)
                )
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .height(13.dp)
                .background(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(6.dp)
                )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(11.dp)
                .background(
                    color = Color.White.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(5.dp)
                )
        )
    }
}

@Composable
private fun SortFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
            .navigationBarsPadding()
            .padding(end = 24.dp, bottom = 24.dp)
            .size(64.dp),
        containerColor = Color(0xFF1A1A1A),
        contentColor = Color.White,
        elevation = FloatingActionButtonDefaults.elevation(
            defaultElevation = 8.dp,
            pressedElevation = 12.dp
        )
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Sort,
            contentDescription = stringResource(R.string.view_all_sort),
            modifier = Modifier.size(28.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SortBottomSheet(
    currentSortBy: String,
    currentSortOrder: String,
    availableGenres: List<String>,
    selectedGenres: Set<String>,
    onSortSelected: (String, String) -> Unit,
    onGenreToggle: (String) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenWidthDp = containerWidthDp()
    val isTablet = isTabletLayout(screenWidthDp)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0F0F0F),
        scrimColor = Color.Black.copy(alpha = 0.7f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color.White.copy(alpha = 0.4f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .let { 
                    if (isTablet) it.widthIn(max = 640.dp).align(Alignment.CenterHorizontally) 
                    else it 
                }
                .heightIn(max = if (isTablet) 700.dp else 560.dp)
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    color = Color(0xFF0080FF).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = null,
                            tint = Color(0xFF0080FF),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = stringResource(R.string.view_all_sort_and_filter),
                    color = Color.White,
                    fontSize = if (isTablet) 28.sp else 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            librarySortFields().forEach { field ->
                val selected = currentSortBy == field.sortBy
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            val nextOrder = if (selected) {
                                if (currentSortOrder == "Descending") "Ascending" else "Descending"
                            } else {
                                field.defaultOrder
                            }
                            onSortSelected(field.sortBy, nextOrder)
                        }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = stringResource(field.labelRes),
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                    )
                    if (selected) {
                        Icon(
                            imageVector = if (currentSortOrder == "Descending") {
                                Icons.Filled.KeyboardArrowDown
                            } else {
                                Icons.Filled.KeyboardArrowUp
                            },
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        color = Color(0xFFFF9F43).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Filled.LocalOffer,
                                contentDescription = null,
                                tint = Color(0xFFFF9F43),
                                modifier = Modifier.size(20.dp)
                           )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = stringResource(R.string.view_all_genres),
                        color = Color.White,
                        fontSize = if (isTablet) 20.sp else 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (selectedGenres.isNotEmpty()) {
                    TextButton(onClick = onClearFilters) {
                        Text(
                            text = stringResource(R.string.view_all_clear_filters),
                            color = Color(0xFF3AA0FF),
                            fontSize = if (isTablet) 16.sp else 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (availableGenres.isEmpty()) {
                Text(
                    text = stringResource(R.string.view_all_no_genres),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    availableGenres.forEach { genre ->
                        MediaFilterChip(
                            label = genre,
                            isSelected = selectedGenres.contains(genre),
                            onClick = { onGenreToggle(genre) }
                        )
                    }
                }
            }
        }
    }
}

private fun viewAllItemKey(item: BaseItemDto): String {
    return item.id ?: "${item.name}_${item.type}_${item.seriesId}_${item.indexNumber ?: 0}"
}

private fun ContentType.includesSeriesItems(): Boolean {
    return this == ContentType.SERIES ||
        this == ContentType.TVSHOWS_GENRE ||
        this == ContentType.ALL
}

@Composable
private fun LibraryBrowseTabRow(
    tabs: List<LibraryBrowseTab>,
    selected: LibraryBrowseTab,
    contentType: ContentType,
    onSelected: (LibraryBrowseTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tabs.forEach { tab ->
            val isSelected = tab == selected
            Surface(
                onClick = { onSelected(tab) },
                shape = RoundedCornerShape(999.dp),
                color = if (isSelected) Color.White.copy(alpha = 0.16f) else Color.Transparent
            ) {
                Text(
                    text = stringResource(tab.labelRes(contentType)),
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.62f),
                    fontSize = 14.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}