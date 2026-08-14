package com.jellycine.app.ui.screens.dashboard.search

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.jellycine.shared.R
import com.jellycine.shared.ui.components.common.LazyImageLoader
import com.jellycine.shared.util.image.rememberImageUrl
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun SuggestionsStoriesView(
    suggestions: List<BaseItemDto>,
    onItemClick: (BaseItemDto) -> Unit,
    mediaRepository: MediaRepository,
    pagerFocusRequester: FocusRequester = remember { FocusRequester() },
    onUpPressed: () -> Unit = {}
) {
    if (suggestions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.suggestions_empty),
                color = Color.Gray,
                fontSize = 16.sp
            )
        }
        return
    }

    val itemCount = suggestions.size
    val initialPage = remember(itemCount) {
        if (itemCount <= 1) 0
        else {
            val midpoint = Int.MAX_VALUE / 2
            midpoint - (midpoint % itemCount)
        }
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { if (itemCount <= 1) 1 else Int.MAX_VALUE }
    )
    val currentItemIndex = ((pagerState.currentPage % itemCount) + itemCount) % itemCount
    val coroutineScope = rememberCoroutineScope()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val cardWidth = 260.dp
    val horizontalPadding = (screenWidth - cardWidth) / 2

    Box(modifier = Modifier.fillMaxSize()) {
        val currentItem = suggestions[currentItemIndex]

        val primaryUrl = currentItem.imageUrl
            ?: rememberImageUrl(
                itemId = currentItem.id,
                imageType = "Primary",
                enableImageEnhancers = false,
                mediaRepository = mediaRepository
            )

        var displayUrl by remember { mutableStateOf(primaryUrl) }
        if (!primaryUrl.isNullOrBlank()) displayUrl = primaryUrl

        LazyImageLoader(
            imageUrl = displayUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            cornerRadius = 0
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.6f),
                            Color.Black.copy(alpha = 0.9f)
                        ),
                        radius = 1200f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(pagerFocusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionLeft -> {
                                if (pagerState.currentPage > 0) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                                true
                            }
                            Key.DirectionRight -> {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                                true
                            }
                            Key.DirectionUp -> {
                                onUpPressed()
                                true
                            }
                            Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                                onItemClick(suggestions[currentItemIndex])
                                true
                            }
                            else -> false
                        }
                    } else false
                }
                .focusable(),
            contentPadding = PaddingValues(
                start = horizontalPadding,
                end = horizontalPadding,
                top = 60.dp,
                bottom = 90.dp
            ),
            pageSpacing = 8.dp,
            verticalAlignment = Alignment.CenterVertically
        ) { page ->
            val rawOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val itemIndex = ((page % itemCount) + itemCount) % itemCount

            SuggestionsCard(
                item = suggestions[itemIndex],
                pageOffset = rawOffset,
                mediaRepository = mediaRepository
            )
        }

        val typeText = when (currentItem.type) {
            "Movie" -> stringResource(R.string.suggestions_type_movie)
            "Series" -> stringResource(R.string.suggestions_type_tv_series)
            else -> currentItem.type ?: stringResource(R.string.suggestions_type_media)
        }
        val yearText = currentItem.productionYear
            ?: currentItem.premiereDate?.take(4)?.toIntOrNull()
        val genreText = currentItem.genres?.take(3)?.joinToString(" | ").orEmpty()
        val displayText = buildString {
            yearText?.let { append(it) }
            if (typeText.isNotBlank()) {
                if (isNotEmpty()) append(" | ")
                append(typeText)
            }
            if (genreText.isNotBlank()) {
                if (isNotEmpty()) append(" | ")
                append(genreText)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = displayText,
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 6.dp)
            )

            if (suggestions.size > 1) {
                val maxVisibleDots = 7
                val visibleCount = minOf(itemCount, maxVisibleDots)
                val centerSlot = visibleCount / 2

                val slotWidth = 10.dp
                val slotHeight = 6.dp
                val slotSpacing = 7.dp
                val step = slotWidth + slotSpacing
                val offsetFraction = pagerState.currentPageOffsetFraction.coerceIn(-1f, 1f)
                val trackOffset = step * (-offsetFraction)

                Box(
                    modifier = Modifier.padding(top = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.offset(x = trackOffset),
                        horizontalArrangement = Arrangement.spacedBy(slotSpacing)
                    ) {
                        repeat(visibleCount) { slot ->
                            val relative = slot - centerSlot
                            val distanceFromCenter = kotlin.math.abs(relative + offsetFraction)
                            val dotWidth = when {
                                distanceFromCenter < 0.6f -> 10.dp
                                distanceFromCenter < 1.6f -> 8.dp
                                else -> 6.dp
                            }
                            val dotAlpha = when {
                                distanceFromCenter < 0.6f -> 0.40f
                                distanceFromCenter < 1.6f -> 0.32f
                                else -> 0.24f
                            }

                            Box(
                                modifier = Modifier
                                    .width(slotWidth)
                                    .height(slotHeight)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .width(dotWidth)
                                        .height(slotHeight)
                                        .background(
                                            color = Color.White.copy(alpha = dotAlpha),
                                            shape = CircleShape
                                        )
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(slotHeight)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFFFF2D2D),
                                        Color(0xFFFF0000)
                                    )
                                ),
                                shape = CircleShape
                            )
                            .graphicsLayer { shadowElevation = 6.dp.toPx() }
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        pagerFocusRequester.requestFocus()
    }
}

@Composable
private fun SuggestionsCard(
    item: BaseItemDto,
    pageOffset: Float,
    mediaRepository: MediaRepository
) {
    val absOffset = pageOffset.absoluteValue

    val scale by animateFloatAsState(
        targetValue = 1f - (absOffset.coerceIn(0f, 2f) * 0.06f),
        animationSpec = tween(200),
        label = "scale"
    )

    val alpha by animateFloatAsState(
        targetValue = 1f - (absOffset.coerceIn(0f, 2f) * 0.20f),
        animationSpec = tween(200),
        label = "alpha"
    )

    val rotation = pageOffset.coerceIn(-2f, 2f) * 25f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.67f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                rotationY = rotation
                transformOrigin = if (pageOffset > 0) {
                    TransformOrigin(0f, 0.5f)
                } else {
                    TransformOrigin(1f, 0.5f)
                }
                cameraDistance = 8f * density
                compositingStrategy = CompositingStrategy.Offscreen
            },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (absOffset < 0.5f) 16.dp else 4.dp
            )
        ) {
            val imageUrl = item.imageUrl ?: rememberImageUrl(
                itemId = item.id,
                imageType = "Primary",
                enableImageEnhancers = false,
                mediaRepository = mediaRepository
            )
            LazyImageLoader(
                imageUrl = imageUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
                cornerRadius = 16
            )
        }
    }
}