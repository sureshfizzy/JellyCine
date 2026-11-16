package com.jellycine.ui.screens.dashboard.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.*
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.resources.*
import com.jellycine.ui.components.common.*
import com.jellycine.ui.screens.dashboard.home.ImageLoader
import com.jellycine.ui.screens.dashboard.home.ScrollOptimization
import com.jellycine.ui.screens.dashboard.home.StableBaseItem
import com.jellycine.util.formatRuntime
import kotlinx.coroutines.flow.collect
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ContinueWatchingSection(
    titleRes: StringResource = Res.string.dashboard_continue_watching,
    errorMessageRes: StringResource = Res.string.dashboard_failed_continue_watching,
    items: List<BaseItemDto>,
    isLoading: Boolean,
    error: String?,
    mediaRepository: MediaRepository,
    imageHeaders: Map<String, String> = emptyMap(),
    onItemClick: (BaseItemDto) -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black) // AMOLED black background
    ) {
        Text(
            text = stringResource(titleRes),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when {
            isLoading -> Unit

            items.isNotEmpty() -> {
                val lazyRowState = rememberLazyListState()
                val flingBehavior = ScrollOptimization.rememberUltraSmoothFlingBehavior()
                val firstItemId = items.firstOrNull()?.id
                var renderedCount by remember(items) {
                    mutableIntStateOf(items.size.coerceAtMost(4))
                }

                LaunchedEffect(items) {
                    renderedCount = items.size.coerceAtMost(4)
                }

                LaunchedEffect(firstItemId, items.size) {
                    val hasScrolled = lazyRowState.firstVisibleItemIndex > 0 ||
                        lazyRowState.firstVisibleItemScrollOffset > 0
                    if (hasScrolled) {
                        lazyRowState.scrollToItem(0)
                    }
                }

                LaunchedEffect(lazyRowState, items) {
                    snapshotFlow {
                        lazyRowState.firstVisibleItemIndex + lazyRowState.layoutInfo.visibleItemsInfo.size
                    }.collect { visibleEnd ->
                        if (visibleEnd >= renderedCount - 2 && renderedCount < items.size) {
                            renderedCount = (renderedCount + 4).coerceAtMost(items.size)
                        }
                    }
                }

                LazyRow(
                    state = lazyRowState,
                    horizontalArrangement = Arrangement.spacedBy(ScrollOptimization.listItemSpacing),
                    contentPadding = ScrollOptimization.optimizedContentPadding,
                    flingBehavior = flingBehavior,
                    modifier = ScrollOptimization.getScrollContainerModifier()
                ) {
                    items(
                        count = renderedCount.coerceAtMost(items.size),
                        key = { index -> "${items.getOrNull(index)?.id ?: "item"}_$index" }
                    ) { index ->
                        val item = items.getOrNull(index)
                        if (item != null) {
                            val stableOnClick = remember(item.id) { { onItemClick(item) } }

                            Box {
                                ContinueWatchingCard(
                                    item = item,
                                    mediaRepository = mediaRepository,
                                    imageHeaders = imageHeaders,
                                    onClick = stableOnClick
                                )
                            }
                        }
                    }
                }
            }

            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "!",
                            fontSize = 24.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(errorMessageRes),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = error,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )

                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContinueWatchingCard(
    item: BaseItemDto,
    mediaRepository: MediaRepository,
    imageHeaders: Map<String, String> = emptyMap(),
    onClick: () -> Unit = {}
) {
    val stableItem = remember(item.id, item.userData) { StableBaseItem.from(item) }
    val unknownTitle = stringResource(Res.string.search_result_unknown_title)
    val unknownEpisode = stringResource(Res.string.search_result_unknown_episode)

    val itemName = remember(
        item.type,
        item.seriesName,
        item.seasonName,
        item.episodeTitle,
        stableItem.name
    ) {
        item.preferredDisplayTitle(
            unknownTitle = unknownTitle,
            unknownEpisode = unknownEpisode
        )
    }
    val metadataText = remember(
        item.type,
        item.productionYear,
        item.parentIndexNumber,
        item.indexNumber,
        item.episodeTitle,
        item.name,
        item.seriesName,
        item.seasonName
    ) {
        when (item.type) {
            "Movie" -> {
                item.productionYear?.toString().orEmpty()
            }
            "Episode" -> {
                item.episodeDisplaySubtitle()
            }
            "Series" -> {
                val year = item.productionYear ?: item.premiereDate?.take(4)?.toIntOrNull()
                year?.toString().orEmpty()
            }
            else -> ""
        }
    }
    val remainingText = remember(item.runTimeTicks, item.userData?.playbackPositionTicks) {
        val runtimeTicks = item.runTimeTicks
        val playbackPositionTicks = item.userData?.playbackPositionTicks ?: 0L
        if (runtimeTicks != null && runtimeTicks > 0L && playbackPositionTicks > 0L && playbackPositionTicks < runtimeTicks) {
            val remainingTicks = (runtimeTicks - playbackPositionTicks).coerceAtLeast(0L)
            "${formatRuntime(remainingTicks)} left"
        } else {
            ""
        }
    }

    Column(
        modifier = Modifier
            .width(200.dp)
            .height(180.dp)
    ) {
        Card(
            modifier = Modifier
                .width(200.dp)
                .height(120.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            onClick = onClick
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                ImageLoader(
                    itemId = stableItem.id,
                    seriesId = stableItem.seriesId,
                    imageType = "Thumb",
                    fallbackImageType = "Backdrop",
                    extraFallbackImageTypes = listOf("Primary"),
                    preferSeriesIdForThumbBackdrop = true,
                    allowRgb565 = false,
                    contentDescription = stableItem.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    cornerRadius = 12,
                    crossfadeMillis = 0,
                    mediaRepository = mediaRepository,
                    imageHeaders = imageHeaders,
                    imageMetadata = item,
                    itemType = stableItem.type, // Pass item type for proper episode handling
                    imageTag = null
                )

                remainingText.takeIf { it.isNotBlank() }?.let { text ->
                    PosterTextBadge(
                        text = text,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(
                                top = if (stableItem.isWatched) 4.5.dp else 8.dp,
                                end = if (stableItem.isWatched) 28.dp else 4.dp
                            )
                    )
                }

                if (stableItem.isWatched) {
                    WatchedIndicatorBadge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    )
                }

                stableItem.userData?.playedPercentage?.let { percentage ->
                    val progress = remember(percentage) {
                        (percentage.toFloat() / 100f).coerceIn(0f, 1f)
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .align(Alignment.BottomCenter),
                        color = Color.Red,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }

            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp) // Fixed height for title area
                .padding(top = 8.dp, start = 4.dp, end = 4.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = itemName,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))

            if (metadataText.isNotBlank()) {
                Text(
                    text = metadataText,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
