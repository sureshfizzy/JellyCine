package com.jellycine.ui.screens.dashboard.home

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.*
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.HomeLibrarySectionData
import com.jellycine.data.repository.MediaRepository
import com.jellycine.resources.*
import com.jellycine.ui.components.common.*
import com.jellycine.ui.screens.dashboard.home.ImageLoader
import com.jellycine.ui.screens.dashboard.home.ScrollOptimization
import com.jellycine.ui.screens.dashboard.home.StableBaseItem
import org.jetbrains.compose.resources.stringResource

@Stable
@Immutable
data class HomeLibrarySectionUi(
    val libraryId: String,
    val libraryName: String,
    val collectionType: String?,
    val items: List<BaseItemDto>
)

fun viewAllContentType(collectionType: String?): String = when (collectionType) {
    "movies" -> "MOVIES"
    "tvshows" -> "SERIES"
    else -> "ALL"
}

fun HomeLibrarySectionUi.toPersistedSection(): HomeLibrarySectionData {
    val library = BaseItemDto(
        id = libraryId,
        name = libraryName,
        collectionType = collectionType,
        type = "CollectionFolder"
    )
    return HomeLibrarySectionData(
        library = library,
        items = items
    )
}

fun HomeLibrarySectionData.toUiSection(): HomeLibrarySectionUi {
    return HomeLibrarySectionUi(
        libraryId = library.id.orEmpty(),
        libraryName = library.name.orEmpty(),
        collectionType = library.collectionType,
        items = items
    )
}


@Composable
fun BurstLibrarySection(
    section: HomeLibrarySectionUi,
    mediaRepository: MediaRepository,
    disablePosterEnhancers: Boolean,
    imageHeaders: Map<String, String> = emptyMap(),
    onItemClick: (BaseItemDto) -> Unit = {},
    onNavigateToViewAll: (String, String?, String) -> Unit = { _, _, _ -> }
) {
    val libraryRowState = rememberLazyListState()
    val libraryFlingBehavior = ScrollOptimization.rememberUltraSmoothFlingBehavior()
    val firstSectionItemId = section.items.firstOrNull()?.id
    val sectionTitle = section.libraryName.ifBlank { stringResource(Res.string.my_media_library_fallback) }
    var lastFirstSectionItemId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(firstSectionItemId, section.items.size) {
        val hasScrolled = libraryRowState.firstVisibleItemIndex > 0 ||
            libraryRowState.firstVisibleItemScrollOffset > 0
        if (firstSectionItemId != null && lastFirstSectionItemId != null && firstSectionItemId != lastFirstSectionItemId) {
            if (hasScrolled) {
                libraryRowState.scrollToItem(0)
            }
        }
        lastFirstSectionItemId = firstSectionItemId ?: lastFirstSectionItemId
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(Res.string.dashboard_recently_added, sectionTitle),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = {
                    onNavigateToViewAll(viewAllContentType(section.collectionType), section.libraryId, sectionTitle)
                },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = stringResource(Res.string.dashboard_view_all),
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (section.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(Res.string.dashboard_no_items_found),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
            return
        }

        LazyRow(
            state = libraryRowState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            flingBehavior = libraryFlingBehavior,
            modifier = Modifier.fillMaxWidth()
        ) {
            itemsIndexed(
                items = section.items,
                key = { index, item -> "${item.id ?: section.libraryId}_$index" }
            ) { index, item ->
                val stableOnClick = remember(item.id, item.name) { { onItemClick(item) } }
                val isVisible by remember(libraryRowState, index) {
                    derivedStateOf {
                        libraryRowState.layoutInfo.visibleItemsInfo.any { it.index == index }
                    }
                }
                var hasEntered by remember(item.id ?: "${section.libraryId}_$index") {
                    mutableStateOf(false)
                }
                LaunchedEffect(isVisible) {
                    if (isVisible && !hasEntered) {
                        hasEntered = true
                    }
                }
                val entranceProgress by animateFloatAsState(
                    targetValue = if (hasEntered) 1f else 0.88f,
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    label = "burst_library_item_entrance"
                )

                LibraryItemCard(
                    item = item,
                    modifier = Modifier.graphicsLayer {
                        alpha = entranceProgress
                        translationX = (1f - entranceProgress) * 26f
                        val scale = 0.96f + (0.04f * entranceProgress)
                        scaleX = scale
                        scaleY = scale
                    },
                    mediaRepository = mediaRepository,
                    imageHeaders = imageHeaders,
                    disableImageEnhancers = disablePosterEnhancers,
                    onClick = stableOnClick
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryItemCard(
    item: BaseItemDto,
    modifier: Modifier = Modifier,
    mediaRepository: MediaRepository,
    imageHeaders: Map<String, String> = emptyMap(),
    disableImageEnhancers: Boolean = false,
    useLandscapeLayout: Boolean = false,
    watchedFeedStyle: Boolean = false,
    titleAreaHeightOverride: Dp? = null,
    onClick: () -> Unit = {}
) {
    val stableItem = remember(item.id, item.userData) { StableBaseItem.from(item) }
    val useWatchedEpisodeImage = watchedFeedStyle && item.type == "Episode"
    val useWatchedSeriesBadge = watchedFeedStyle && item.type == "Series"
    val unknownTitle = stringResource(Res.string.search_result_unknown_title)
    val unknownEpisode = stringResource(Res.string.search_result_unknown_episode)

    val displayName = if (useWatchedEpisodeImage) {
        item.episodeSeriesTitle(unknownTitle, unknownEpisode)
    } else {
        item.preferredDisplayTitle(
            unknownTitle = unknownTitle,
            unknownEpisode = unknownEpisode
        )
    }

    val landscapeLayout = useLandscapeLayout || useWatchedEpisodeImage
    val cardWidth = if (landscapeLayout) 200.dp else 112.dp
    val imageHeight = if (landscapeLayout) 120.dp else 166.dp
    val titleAreaHeight = titleAreaHeightOverride ?: if (landscapeLayout) 64.dp else 46.dp
    val cardHeight = titleAreaHeightOverride?.let { imageHeight + it } ?: if (landscapeLayout) 182.dp else 214.dp
    val titleTopPadding = if (landscapeLayout) 6.dp else 4.dp
    val titleFontSize = 13.sp
    val titleLineHeight = 15.sp
    val titleMaxLines = if (useWatchedEpisodeImage) 1 else if (landscapeLayout) 2 else 1
    val metadataFontSize = 12.sp
    val metadataLineHeight = 13.sp

    Column(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
    ) {
        Card(
            modifier = Modifier
                .width(cardWidth)
                .height(imageHeight),
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
                val imageType = if (landscapeLayout && !useWatchedEpisodeImage) "Thumb" else "Primary"
                val fallbackImageType = if (landscapeLayout) "Backdrop" else null

                ImageLoader(
                    itemId = stableItem.id,
                    seriesId = stableItem.seriesId,
                    imageType = imageType,
                    fallbackImageType = fallbackImageType,
                    extraFallbackImageTypes = if (useWatchedEpisodeImage) listOf("Thumb") else if (landscapeLayout) listOf("Primary") else emptyList(),
                    preferSeriesIdForThumbBackdrop = landscapeLayout && !useWatchedEpisodeImage,
                    preferSeriesIdForEpisodePrimary = !useWatchedEpisodeImage,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    cornerRadius = 12,
                    crossfadeMillis = 0,
                    mediaRepository = mediaRepository,
                    imageHeaders = imageHeaders,
                    imageMetadata = item,
                    itemType = stableItem.type, // Pass item type for proper episode handling
                    hasImageEnhancers = !disableImageEnhancers,
                    imageTag = null
                )

                val episodeCount = when {
                    item.type == "Series" && item.userData?.unplayedItemCount != null -> item.userData?.unplayedItemCount
                    useWatchedSeriesBadge -> null
                    item.type == "Series" && item.episodeCount != null && item.episodeCount!! > 0 -> item.episodeCount!!
                    item.type == "Series" && item.recursiveItemCount != null && item.recursiveItemCount!! > 0 -> item.recursiveItemCount!!
                    else -> null
                }

                val isFullyWatched = item.type == "Series" &&
                    item.userData?.unplayedItemCount == 0

                episodeCount?.takeIf { it > 0 }?.let { count ->
                    if (useWatchedSeriesBadge) {
                        PosterTextBadge(
                            text = stringResource(Res.string.count_left, count),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 8.dp, bottom = 8.dp)
                        )
                    } else {
                        PosterCountBadge(
                            count = count,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 4.dp)
                        )
                    }
                }

                if (!watchedFeedStyle && (isFullyWatched || (episodeCount == null && stableItem.isWatched))) {
                    WatchedIndicatorBadge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                    )
                }

            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(titleAreaHeight)
                .padding(top = titleTopPadding, start = 4.dp, end = 4.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = displayName,
                    color = Color.White,
                    fontSize = titleFontSize,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = titleMaxLines,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = titleLineHeight,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                val metadataText = if (useWatchedEpisodeImage) item.episodeDisplaySubtitle() else buildMetadataText(item)
                if (metadataText.isNotEmpty()) {
                    Text(
                        text = metadataText,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = metadataFontSize,
                        lineHeight = metadataLineHeight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun buildMetadataText(item: BaseItemDto): String {
    val StartYear = item.productionYear ?: item.premiereDate
        ?.take(4)
        ?.toIntOrNull()
    return buildString {
        when (item.type) {
            "Series" -> {
                StartYear?.let { startYear ->
                    append(startYear.toString())
                    item.endDate?.let { endDate ->
                        val endYear = endDate.take(4).toIntOrNull()
                        if (endYear != null && endYear != startYear) {
                            append(" - $endYear")
                        }
                    } ?: run {
                    }
                }
            }
            "Movie" -> {
                StartYear?.let { year ->
                    append(year.toString())
                }
            }
            "Episode" -> {
                val seasonNumber = item.parentIndexNumber
                val episodeNumber = item.indexNumber
                if (seasonNumber != null && episodeNumber != null) {
                    append("S${seasonNumber}:E${episodeNumber}")
                    item.name?.let { episodeName ->
                        if (episodeName != item.seriesName) {
                            append(" - ${episodeName.take(20)}")
                            if (episodeName.length > 20) append("...")
                        }
                    }
                }
            }
            else -> {
                StartYear?.let { year ->
                    append(year.toString())
                }
            }
        }
    }
}
