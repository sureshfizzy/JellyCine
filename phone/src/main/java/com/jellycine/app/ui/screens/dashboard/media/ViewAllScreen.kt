package com.jellycine.app.ui.screens.dashboard.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.LocalOffer
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
import androidx.annotation.StringRes
import kotlinx.coroutines.flow.first
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellycine.app.R
import com.jellycine.app.ui.components.common.containerWidthDp
import com.jellycine.app.ui.components.common.isTabletLayout
import com.jellycine.shared.ui.components.common.FilterChip as MediaFilterChip
import com.jellycine.shared.ui.components.common.PosterCountBadge
import com.jellycine.shared.util.image.DisableEmbyPosterEnhancers
import com.jellycine.shared.util.image.WarmImageUrl
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.data.model.BaseItemDto

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ViewAllScreen(
    contentType: ContentType,
    parentId: String? = null,
    title: String = "",
    genreId: String? = null,
    onItemClick: (BaseItemDto) -> Unit,
    viewModel: ViewAllViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val screenWidthDp = containerWidthDp()
    val isTablet = isTabletLayout(screenWidthDp)

    val gridCells = remember(screenWidthDp) {
        if (screenWidthDp >= 1200.dp) {
            GridCells.Adaptive(minSize = 160.dp)
        } else if (screenWidthDp >= 600.dp) {
            GridCells.Adaptive(minSize = 140.dp)
        } else {
            GridCells.Fixed(3)
        }
    }

    val horizontalPadding = if (isTablet) 24.dp else 16.dp
    val verticalSpacing = if (isTablet) 20.dp else 16.dp
    val horizontalSpacing = if (isTablet) 16.dp else 12.dp

    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    
    var showSortSheet by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val resolvedTitle = title.takeIf { it.isNotBlank() } ?: stringResource(R.string.view_all_title)
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
    val genreIncludeItemTypes = remember(contentType) {
        when (contentType) {
            ContentType.MOVIES, ContentType.MOVIES_GENRE -> "Movie"
            ContentType.SERIES, ContentType.TVSHOWS_GENRE -> "Series"
            ContentType.ALL -> "Movie,Series"
            ContentType.EPISODES -> null
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

    // Load initial data
    LaunchedEffect(contentType, parentId, genreId) {
        viewModel.ensureItemsLoaded(contentType, parentId, genreId)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding(),
                color = Color.Black
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = horizontalPadding, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = resolvedTitle,
                            color = Color.White,
                            fontSize = if (isTablet) 28.sp else 24.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (headerTotalCount > 0) {
                            Text(
                                text = stringResource(R.string.view_all_count, displayItems.size, headerTotalCount),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = if (isTablet) 15.sp else 13.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                when {
                    uiState.isLoading && items.isEmpty() -> {
                        LazyVerticalGrid(
                            columns = gridCells,
                            contentPadding = PaddingValues(
                                start = horizontalPadding,
                                top = 16.dp,
                                end = horizontalPadding,
                                bottom = 120.dp
                            ),
                            horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                            verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                            modifier = Modifier.fillMaxSize()
                        ) {
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
                    
                    displayItems.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
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
                    
                    else -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                columns = gridCells,
                                state = gridState,
                                contentPadding = PaddingValues(
                                    start = horizontalPadding,
                                    top = 16.dp,
                                    end = horizontalPadding,
                                    bottom = 120.dp
                                ),
                                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = displayItems,
                                    key = ::viewAllItemKey
                                ) { item ->
                                    PosterCard(
                                        item = item,
                                        isTablet = isTablet,
                                        mediaRepository = mediaRepository,
                                        onClick = { onItemClick(item) }
                                    )
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
                                    .padding(top = 8.dp),
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

        SortFAB(
            onClick = { showSortSheet = true },
            modifier = Modifier.align(Alignment.BottomEnd)
        )

        if (showSortSheet) {
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecentlyAddedCard(
    item: BaseItemDto,
    mediaRepository: MediaRepository,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val disablePosterEnhancers = DisableEmbyPosterEnhancers()
    var imageUrl by remember(item.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(item.id, disablePosterEnhancers) {
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
                    width = 200,
                    height = 300,
                    quality = 90,
                    enableImageEnhancers = !disablePosterEnhancers
                ).first()
                imageUrl = url
            } catch (e: Exception) {
            }
        }
    }

    Card(
        modifier = Modifier
            .width(120.dp)
            .height(180.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Gray.copy(alpha = 0.3f),
                        RoundedCornerShape(8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = item.name?.take(2)?.uppercase() ?: stringResource(R.string.view_all_unknown_initial),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
                    .padding(8.dp)
            ) {
                Text(
                    text = item.name ?: stringResource(R.string.search_result_unknown_title),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PosterCard(
    item: BaseItemDto,
    isTablet: Boolean,
    mediaRepository: MediaRepository,
    onClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val disablePosterEnhancers = DisableEmbyPosterEnhancers()
    var imageUrl by remember(item.id) { mutableStateOf<String?>(null) }
    var isLoading by remember(item.id) { mutableStateOf(true) }

    LaunchedEffect(item.id, disablePosterEnhancers) {
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
                    enableImageEnhancers = !disablePosterEnhancers
                ).first()
                imageUrl = url
                isLoading = false
            } catch (e: Exception) {
                imageUrl = null
                isLoading = false
            }
        }
    }

    // Show series name for episodes, otherwise show item name
    val displayName = if (item.type == "Episode" && !item.seriesName.isNullOrBlank()) {
        item.seriesName!!
    } else {
        item.name ?: stringResource(R.string.search_result_unknown_title)
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
                    contentScale = ContentScale.Crop,
                    placeholder = null,
                    error = null,
                    fallback = null
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

                // Episode/item count badge
                val itemCount = when {
                    item.type == "Series" -> {
                        when {
                            item.episodeCount != null && item.episodeCount!! > 0 -> item.episodeCount
                            item.recursiveItemCount != null && item.recursiveItemCount!! > 0 -> item.recursiveItemCount
                            item.childCount != null && item.childCount!! > 0 -> item.childCount
                            else -> null
                        }
                    }
                    else -> null
                }

                itemCount?.let { count ->
                    PosterCountBadge(
                        count = count,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 4.dp)
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
private fun SkeletonPosterCard() {
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

            val sortOptions = listOf(
                SortOption("DateCreated", "Descending"),
                SortOption("SortName", "Ascending"),
                SortOption("SortName", "Descending"),
                SortOption("ProductionYear", "Descending"),
                SortOption("ProductionYear", "Ascending"),
                SortOption("CommunityRating", "Descending"),
                SortOption("CommunityRating", "Ascending")
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                sortOptions.forEach { option ->
                    MediaFilterChip(
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

private data class SortOption(
    val sortBy: String,
    val sortOrder: String
)

private fun viewAllItemKey(item: BaseItemDto): String {
    return item.id ?: "${item.name}_${item.type}_${item.seriesId}_${item.indexNumber ?: 0}"
}

@StringRes
private fun sortOptionLabelRes(sortBy: String, sortOrder: String): Int {
    return when (sortBy to sortOrder) {
        "DateCreated" to "Descending" -> R.string.view_all_sort_recently_added
        "SortName" to "Ascending" -> R.string.view_all_sort_name_az
        "SortName" to "Descending" -> R.string.view_all_sort_name_za
        "ProductionYear" to "Descending" -> R.string.view_all_sort_year_newest
        "ProductionYear" to "Ascending" -> R.string.view_all_sort_year_oldest
        "CommunityRating" to "Descending" -> R.string.view_all_sort_rating_high
        "CommunityRating" to "Ascending" -> R.string.view_all_sort_rating_low
        else -> R.string.view_all_sort_recently_added
    }
}
