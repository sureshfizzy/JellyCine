package com.jellycine.app.ui.screens.dashboard.search

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.res.stringResource
import com.jellycine.app.R
import com.jellycine.app.ui.components.common.containerWidthDp
import com.jellycine.app.ui.components.common.isTabletLayout
import com.jellycine.shared.ui.components.common.LazyImageLoader
import com.jellycine.shared.util.image.rememberImageUrl
import com.jellycine.data.model.BaseItemDto

@Composable
fun SuggestionsStoriesView(
    suggestions: List<BaseItemDto>,
    onItemClick: (BaseItemDto) -> Unit
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
        if (itemCount <= 1) {
            0
        } else {
            val midpoint = Int.MAX_VALUE / 2
            midpoint - (midpoint % itemCount)
        }
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { if (itemCount <= 1) 1 else Int.MAX_VALUE }
    )
    val currentItemIndex = ((pagerState.currentPage % itemCount) + itemCount) % itemCount
    val screenWidthDp = containerWidthDp()
    val isTablet = isTabletLayout(screenWidthDp)
    val tabletPageWidth = 320.dp
    val navigationBarInset = WindowInsets.navigationBars
        .asPaddingValues()
        .calculateBottomPadding()
    val horizontalContentPadding = if (isTablet) {
        ((screenWidthDp - tabletPageWidth) / 2).coerceAtLeast(24.dp)
    } else {
        60.dp
    }
    val pagerBottomPadding = 72.dp + navigationBarInset
    val detailsBottomPadding = 160.dp + navigationBarInset
    val indicatorBottomPadding = 118.dp + navigationBarInset

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val currentItem = suggestions[currentItemIndex]
        val backgroundImageUrl = rememberImageUrl(itemId = currentItem.id, imageType = "Primary")

        LazyImageLoader(
            imageUrl = backgroundImageUrl,
            contentDescription = stringResource(R.string.cd_backdrop),
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
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )
        
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = pagerBottomPadding),
            contentPadding = PaddingValues(horizontal = horizontalContentPadding),
            pageSpacing = 16.dp,
            pageSize = if (isTablet) PageSize.Fixed(tabletPageWidth) else PageSize.Fill
        ) { page ->
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val itemIndex = ((page % itemCount) + itemCount) % itemCount

            SuggestionsCard(
                item = suggestions[itemIndex],
                isActive = itemIndex == currentItemIndex,
                pageOffset = pageOffset,
                onItemClick = { onItemClick(suggestions[itemIndex]) }
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

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp)
                .padding(bottom = detailsBottomPadding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayText,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }

        if (suggestions.size > 1) {
            val totalPages = itemCount
            val maxVisibleDots = 7
            val visibleCount = minOf(totalPages, maxVisibleDots)
            val centerSlot = visibleCount / 2

            val slotWidth = 10.dp
            val slotHeight = 6.dp
            val slotSpacing = 7.dp
            val step = slotWidth + slotSpacing
            val offsetFraction = pagerState.currentPageOffsetFraction.coerceIn(-1f, 1f)
            val trackOffset = step * (-offsetFraction)

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = indicatorBottomPadding),
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

@Composable
private fun SuggestionsCard(
    item: BaseItemDto,
    isActive: Boolean,
    pageOffset: Float,
    onItemClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val scale by animateFloatAsState(
        targetValue = when {
            isPressed -> 0.95f
            isActive -> 1.0f
            else -> 0.85f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )
    
    val alpha by animateFloatAsState(
        targetValue = if (isActive) 1.0f else 0.6f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val rotation = pageOffset * 25f
                val translationX = pageOffset * 50f
                
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                rotationY = rotation
                this.translationX = translationX
                cameraDistance = 12f * density
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                    },
                    onTap = {
                        onItemClick()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(280.dp)
                .aspectRatio(0.67f),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black
            ),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = if (isActive) 12.dp else 6.dp
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val imageUrl = rememberImageUrl(itemId = item.id, imageType = "Primary")
                LazyImageLoader(
                    imageUrl = imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Crop,
                    cornerRadius = 20
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                )

                if (isActive) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 16.dp)
                            .size(60.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        Color.White.copy(alpha = 0.95f),
                                        Color.White.copy(alpha = 0.85f)
                                    ),
                                    radius = 50f
                                ),
                                shape = CircleShape
                            )
                            .clickable { onItemClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = stringResource(R.string.play),
                            tint = Color.Black,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun SuggestionsStoriesViewPreview() {
    val mockMovies = listOf(
        BaseItemDto(
            name = "Guru",
            id = "1",
            productionYear = 2007,
            genres = listOf("Drama", "Biography", "Musical")
        ),
        BaseItemDto(
            name = "Scott Pilgrim vs. The World",
            id = "2",
            productionYear = 2010,
            genres = listOf("Action", "Comedy", "Romance")
        )
    )
    
    SuggestionsStoriesView(
        suggestions = mockMovies,
        onItemClick = {}
    )
}
