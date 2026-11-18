package com.jellycine.ui.screens.dashboard.media

import com.jellycine.data.model.*
import androidx.compose.ui.*
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.*
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import coil3.compose.AsyncImage
import coil3.request.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import coil3.compose.LocalPlatformContext
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.flow.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellycine.resources.*
import com.jellycine.ui.components.common.*
import com.jellycine.ui.screens.dashboard.home.LibraryItemCard
import com.jellycine.playback.UserDataRefreshSignals
import com.jellycine.util.image.WarmImageUrl
import com.jellycine.data.repository.*
import kotlinx.coroutines.*

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ViewAllScreen(
    mediaRepository: MediaRepository,
    authRepository: AuthRepository,
    contentType: ContentType,
    onBackPressed: () -> Unit,
    onItemClick: (BaseItemDto) -> Unit,
    parentId: String? = null,
    title: String = "",
    genreId: String? = null,
    disablePosterEnhancers: Boolean = false,
    awardsRepository: AwardsRepository? = null,
    seerr: ViewAllSeerrSource? = null,
    viewModel: ViewAllViewModel = viewModel(
        key = "view_all:$contentType:${parentId.orEmpty()}:${genreId.orEmpty()}"
    ) { ViewAllViewModel(mediaRepository, authRepository, awardsRepository, seerr, contentType, parentId, genreId) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val displayItems by viewModel.displayItems.collectAsStateWithLifecycle()
    val availableGenres by viewModel.availableGenres.collectAsStateWithLifecycle()
    val seerrLogoUrl = viewModel.seerrLogoUrl
    val userDataRefreshEvent by UserDataRefreshSignals.refreshEvent.collectAsState()

    BackHandler { onBackPressed() }

    val context = LocalPlatformContext.current
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

    var showSortSheet by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    val resolvedTitle = title.takeIf { it.isNotBlank() } ?: stringResource(Res.string.view_all_title)
    val filterSignature = remember(
        uiState.sortBy,
        uiState.sortOrder,
        uiState.selectedGenres
    ) {
        listOf(
            uiState.sortBy,
            uiState.sortOrder,
            uiState.selectedGenres.toList().sorted().joinToString("|")
        ).joinToString("::")
    }
    var lastAppliedFilterSignature by rememberSaveable(contentType, parentId, genreId) {
        mutableStateOf(filterSignature)
    }
    val headerTotalCount = uiState.totalItems
    val headerCountText = if (headerTotalCount > 0) {
        stringResource(Res.string.view_all_count, displayItems.size, headerTotalCount)
    } else {
        null
    }
    val compactHeaderProgress = rememberCompactProgress(
        state = gridState,
        compactDistance = if (isTablet) 132.dp else 92.dp
    )
    val compactHeader = if (usesCompactHeader) compactHeaderProgress else 0f

    LaunchedEffect(userDataRefreshEvent) {
        if (userDataRefreshEvent != null) viewModel.onUserDataChanged()
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
        val loadMore = items.isEmpty() || lastVisibleIndex >= displayItems.size - 5

        if (loadMore && uiState.hasMorePages && !uiState.isLoading) {
            viewModel.loadMoreItems()
        }
    }

    @Composable
    fun HeaderContent(
        modifier: Modifier = Modifier,
        compactProgress: Float = 0f
    ) {
        if (seerrLogoUrl == null) {
            CompactPageHeader(
                title = resolvedTitle,
                subtitle = headerCountText,
                modifier = modifier,
                includeStatusBarsPadding = false,
                horizontalPadding = if (usesCompactHeader) 0.dp else horizontalPadding,
                verticalPadding = if (usesCompactHeader) 18.dp else 20.dp,
                titleFontSize = if (isTablet) 28.sp else 24.sp,
                titleFontWeight = FontWeight.Bold,
                subtitleFontSize = if (isTablet) 15.sp else 13.sp,
                centered = usesCompactHeader
            )
            return
        }

        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
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
                                    text = stringResource(Res.string.view_all_error_title),
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = uiState.error ?: stringResource(Res.string.view_all_error_message),
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                Button(
                                    onClick = { viewModel.loadItems(refresh = true) },
                                    modifier = Modifier.padding(top = 24.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF0080FF)
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        stringResource(Res.string.try_again),
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }

                    displayItems.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = stringResource(Res.string.view_all_empty_title),
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = stringResource(Res.string.view_all_empty_message),
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
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
                                                onClick = { onItemClick(item) }
                                            )
                                        }
                                    } else {
                                        PosterCard(
                                            item = item,
                                            isTablet = isTablet,
                                            mediaRepository = mediaRepository,
                                            disablePosterEnhancers = disablePosterEnhancers,
                                            watchedViewAll = isWatchedViewAll,
                                            onClick = { onItemClick(item) }
                                        )
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
                                            text = stringResource(Res.string.loading),
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
            onClick = onBackPressed,
            modifier = Modifier.align(Alignment.TopStart)
        )

        if (seerrLogoUrl != null) {
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

        if (!isSeerrCatalog && !isWatchedEpisodeViewAll && !isAward) {
            val expanded by remember {
                derivedStateOf { gridState.firstVisibleItemIndex == 0 }
            }
            val hasActiveFilters = uiState.selectedGenres.isNotEmpty() ||
                uiState.sortBy != "DateCreated" || uiState.sortOrder != "Descending"
            SortFAB(
                onClick = { showSortSheet = true },
                expanded = expanded,
                hasActiveFilters = hasActiveFilters,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        if (showSortSheet && !isSeerrCatalog && !isWatchedEpisodeViewAll && !isAward) {
            SortBottomSheet(
                currentSortBy = uiState.sortBy,
                currentSortOrder = uiState.sortOrder,
                availableGenres = availableGenres,
                selectedGenres = uiState.selectedGenres,
                onSortSelected = viewModel::setSort,
                onGenreToggle = viewModel::toggleGenreFilter,
                onClearFilters = viewModel::clearFilters,
                onDismiss = { showSortSheet = false }
            )
        }
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

@Composable
internal fun PosterCard(
    item: BaseItemDto,
    isTablet: Boolean,
    mediaRepository: MediaRepository,
    disablePosterEnhancers: Boolean = false,
    watchedViewAll: Boolean = false,
    onClick: () -> Unit = {}
) {
    val context = LocalPlatformContext.current
    val directImageUrl = item.imageUrl?.takeIf { it.isNotBlank() }
    val isSeerrSource = item.id?.startsWith("seerr:") == true
    var imageUrl by remember(item.id, directImageUrl, watchedViewAll) { mutableStateOf(directImageUrl) }
    var isLoading by remember(item.id, directImageUrl, watchedViewAll) { mutableStateOf(directImageUrl == null) }

    LaunchedEffect(item.id, directImageUrl, disablePosterEnhancers, watchedViewAll) {
        if (directImageUrl != null) {
            imageUrl = directImageUrl
            isLoading = false
            return@LaunchedEffect
        }
        val itemId = item.id
        if (itemId != null) {
            try {
                val actualItemId = if (item.type == "Episode" && !item.seriesId.isNullOrBlank()) {
                    item.seriesId ?: itemId
                } else {
                    itemId
                }

                val url = mediaRepository.getImageUrl(
                    itemId = actualItemId,
                    width = 300,
                    height = 450,
                    quality = 90,
                    enableImageEnhancers = !disablePosterEnhancers && !watchedViewAll
                ).first()
                imageUrl = url
                isLoading = false
            } catch (e: Exception) {
                imageUrl = null
                isLoading = false
            }
        }
    }

    val displayName = if (item.type == "Episode" && !item.seriesName.isNullOrBlank()) {
        item.seriesName!!
    } else {
        item.name ?: stringResource(Res.string.search_result_unknown_title)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        WarmImageUrl(imageUrl = imageUrl, allowRgb565 = true)

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            shape = RoundedCornerShape(if (isTablet) 18.dp else 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            onClick = onClick
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                if (imageUrl == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0xFF1A1A2E),
                                        Color(0xFF16213E)
                                    )
                                ),
                                shape = RoundedCornerShape(if (isTablet) 18.dp else 16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                color = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = displayName.take(2).uppercase(),
                                color = Color.White.copy(alpha = 0.8f),
                                fontSize = if (isTablet) 22.sp else 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (isSeerrSource) {
                    SeerrTopBadges(
                        requestState = SeerrRequestState.NONE,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }

                // Episode/item count badge
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
                    else -> null
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
    expanded: Boolean = true,
    hasActiveFilters: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .navigationBarsPadding()
            .padding(end = 18.dp, bottom = 18.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(14.dp),
            color = Color(0xFF1A1A1A),
            contentColor = Color.White,
            border = androidx.compose.foundation.BorderStroke(
                0.6.dp,
                Color.White.copy(alpha = 0.15f)
            ),
            shadowElevation = 10.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = 0.8f,
                            stiffness = 400f
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = stringResource(Res.string.view_all_sort),
                        modifier = Modifier.size(18.dp),
                        tint = Color.White.copy(alpha = 0.9f)
                    )
                    if (hasActiveFilters) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 3.dp, y = (-2).dp)
                                .size(7.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF0080FF))
                        )
                    }
                }
                if (expanded) {
                    Text(
                        text = "Sort",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }
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
                    text = stringResource(Res.string.view_all_sort_and_filter),
                    color = Color.White,
                    fontSize = if (isTablet) 28.sp else 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ViewAllSortOptions.forEach { option ->
                    SortChip(
                        label = stringResource(sortOptionLabelRes(option.sortBy, option.sortOrder)),
                        isSelected = currentSortBy == option.sortBy && currentSortOrder == option.sortOrder,
                        onClick = { onSortSelected(option.sortBy, option.sortOrder) }
                    )
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
                        text = stringResource(Res.string.view_all_genres),
                        color = Color.White,
                        fontSize = if (isTablet) 20.sp else 18.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                if (selectedGenres.isNotEmpty()) {
                    TextButton(onClick = onClearFilters) {
                        Text(
                            text = stringResource(Res.string.view_all_clear_filters),
                            color = Color(0xFF3AA0FF),
                            fontSize = if (isTablet) 16.sp else 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (availableGenres.isEmpty()) {
                Text(
                    text = stringResource(Res.string.view_all_no_genres),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    availableGenres.forEach { genre ->
                        SortChip(
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