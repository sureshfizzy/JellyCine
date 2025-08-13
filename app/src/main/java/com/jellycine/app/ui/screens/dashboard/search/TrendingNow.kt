package com.jellycine.app.ui.screens.dashboard.search

import androidx.compose.animation.core.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.jellycine.app.ui.components.common.LazyImageLoader
import com.jellycine.app.util.image.rememberImageUrl
import com.jellycine.data.model.BaseItemDto

@Composable
fun TrendingStoriesView(
    trendingMovies: List<BaseItemDto>,
    onItemClick: (BaseItemDto) -> Unit
) {
    if (trendingMovies.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No trending movies available",
                color = Color.Gray,
                fontSize = 16.sp
            )
        }
        return
    }
    
    val pagerState = rememberPagerState(pageCount = { trendingMovies.size })
    val currentPage = pagerState.currentPage

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val currentMovie = trendingMovies[currentPage]
        val backgroundImageUrl = rememberImageUrl(itemId = currentMovie.id, imageType = "Primary")
        
        LazyImageLoader(
            imageUrl = backgroundImageUrl,
            contentDescription = "Background",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            cornerRadius = 0
        )
        
        // Gradient overlay for better depth and color grading
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.radialGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Black.copy(alpha = 0.6f),
                            Color.Black.copy(alpha = 0.9f)
                        ),
                        radius = 1200f
                    )
                )
        )
        
        // Vertical gradient for better contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
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
        
        // Horizontal Pager for cards with rotation effects
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(), // No top padding - cards should be perfectly centered
            contentPadding = PaddingValues(horizontal = 60.dp),
            pageSpacing = 16.dp
        ) { page ->
            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            
            TrendingCard(
                item = trendingMovies[page],
                isActive = page == currentPage,
                pageOffset = pageOffset,
                onItemClick = { onItemClick(trendingMovies[page]) }
            )
        }

        // Genre labels for the current tab
        val typeText = when (currentMovie.type) {
            "Movie" -> "Movie"
            "Series" -> "TV Series"
            else -> currentMovie.type ?: "Media"
        }

        val genreText = currentMovie.genres?.take(3)?.joinToString(" • ") ?: ""
        val displayText = if (genreText.isNotEmpty()) {
            "$typeText • $genreText"
        } else {
            typeText
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 32.dp)
                .padding(bottom = 160.dp), // Position higher above pagination dots
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

        if (trendingMovies.size > 1) {
            val maxVisibleDots = 7
            val totalPages = trendingMovies.size

            val visibleDots = if (totalPages <= maxVisibleDots) {
                (0 until totalPages).toList()
            } else {
                val halfVisible = maxVisibleDots / 2
                when {
                    currentPage <= halfVisible -> {
                        (0 until maxVisibleDots).toList()
                    }
                    currentPage >= totalPages - halfVisible -> {
                        (totalPages - maxVisibleDots until totalPages).toList()
                    }
                    else -> {
                        (currentPage - halfVisible..currentPage + halfVisible).toList()
                    }
                }
            }
            
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 100.dp), // Moved pagination higher for better positioning
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                visibleDots.forEach { index ->
                    val isActive = index == currentPage
                    val dotSize by animateDpAsState(
                        targetValue = if (isActive) 10.dp else 6.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessHigh
                        ),
                        label = "dotSize"
                    )
                    
                    Box(
                        modifier = Modifier
                            .size(dotSize)
                            .background(
                                color = if (isActive) {
                                    Color.Red
                                } else {
                                    Color.White.copy(alpha = 0.4f)
                                },
                                shape = CircleShape
                            )
                            .graphicsLayer {
                                if (isActive) {
                                    shadowElevation = 2.dp.toPx()
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrendingCard(
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
                                brush = androidx.compose.ui.graphics.Brush.radialGradient(
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
                            contentDescription = "Play",
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
fun TrendingStoriesViewPreview() {
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
    
    TrendingStoriesView(
        trendingMovies = mockMovies,
        onItemClick = {}
    )
}