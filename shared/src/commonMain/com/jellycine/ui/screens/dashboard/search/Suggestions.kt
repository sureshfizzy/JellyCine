package com.jellycine.ui.screens.dashboard.search

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.resources.*
import org.jetbrains.compose.resources.stringResource
import com.jellycine.ui.components.common.containerWidthDp
import com.jellycine.ui.components.common.isTabletLayout
import com.jellycine.util.image.logoImage
import com.jellycine.data.model.BaseItemDto
import coil3.compose.*
import coil3.request.*
import com.jellycine.util.platformBitmapConfig
import kotlin.math.round
import com.jellycine.data.repository.MediaRepository

@Composable
internal fun suggestionsPagerTopPadding() =
    WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 60.dp + 40.dp + 20.dp

@Composable
internal fun suggestionsPagerBottomPadding() =
    160.dp + 40.dp + 40.dp + 12.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

@Composable
fun SuggestionsStoriesView(
    mediaRepository: MediaRepository,
    suggestions: List<BaseItemDto>,
    onItemClick: (BaseItemDto) -> Unit
) {
    if (suggestions.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(Res.string.suggestions_empty),
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
    val pagerTopPadding = suggestionsPagerTopPadding()
    val pagerBottomPadding = suggestionsPagerBottomPadding()
    val detailsBottomPadding = 160.dp + navigationBarInset
    val indicatorBottomPadding = 118.dp + navigationBarInset

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        val currentItem = suggestions[currentItemIndex]

        SuggestionImage(
            item = currentItem,
            mediaRepository = mediaRepository,
            contentDescription = stringResource(Res.string.cd_backdrop),
            modifier = Modifier.fillMaxSize()
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
                .padding(top = pagerTopPadding, bottom = pagerBottomPadding),
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
                onItemClick = { onItemClick(suggestions[itemIndex]) },
                mediaRepository = mediaRepository
            )
        }

        val typeText = when (currentItem.type) {
            "Movie" -> stringResource(Res.string.suggestions_type_movie)
            "Series" -> stringResource(Res.string.suggestions_type_tv_series)
            else -> currentItem.type ?: stringResource(Res.string.suggestions_type_media)
        }
        val yearText = currentItem.productionYear
            ?: currentItem.premiereDate?.take(4)?.toIntOrNull()
        val displayText = buildAnnotatedString {
            var needsSep = false
            currentItem.communityRating?.let { rating ->
                withStyle(SpanStyle(color = Color(0xFFFF4D4F))) { append("★ ") }
                append((round(rating * 10f) / 10f).toString())
                needsSep = true
            }
            yearText?.let {
                if (needsSep) append("  •  ")
                append(it.toString())
                needsSep = true
            }
            if (typeText.isNotBlank()) {
                if (needsSep) append("  •  ")
                append(typeText)
            }
        }

        var logoUrl by remember(currentItem.id) { mutableStateOf<String?>(null) }
        LaunchedEffect(currentItem.id) {
            logoUrl = logoImage(item = currentItem, mediaRepository = mediaRepository)
                ?: mediaRepository.getTmdbLogoUrl(currentItem)
        }
        var logoLoaded by remember(currentItem.id) { mutableStateOf(false) }
        if (!logoUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = detailsBottomPadding + 40.dp)
                    .padding(horizontal = 60.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = currentItem.name,
                    modifier = Modifier
                        .heightIn(max = 40.dp)
                        .widthIn(max = 180.dp)
                        .graphicsLayer { alpha = if (logoLoaded) 1f else 0f },
                    contentScale = ContentScale.Fit,
                    onState = { state ->
                        logoLoaded = state is AsyncImagePainter.State.Success
                    }
                )
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
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
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
    onItemClick: () -> Unit,
    mediaRepository: MediaRepository
) {
    var isPressed by remember { mutableStateOf(false) }
    
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressScale"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val distance = kotlin.math.abs(pageOffset).coerceIn(0f, 1f)
                val rotation = pageOffset * 25f
                val translationX = pageOffset * 50f
                val scale = (1f - 0.15f * distance) * pressScale

                scaleX = scale
                scaleY = scale
                this.alpha = 1f - 0.4f * distance
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
        val cardWidth = minOf(280.dp, maxHeight * 0.67f)
        Card(
            modifier = Modifier
                .width(cardWidth)
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
                SuggestionImage(
                    item = item,
                    mediaRepository = mediaRepository,
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp))
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.2f))
                )
            }
        }
    }
}

@Composable
private fun SuggestionImage(
    item: BaseItemDto,
    mediaRepository: MediaRepository,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalPlatformContext.current
    val imageUrl by produceState(item.imageUrl, item.id, item.imageUrl) {
        if (value.isNullOrBlank()) {
            value = item.id?.let { itemId ->
                mediaRepository.getImageUrlString(
                    itemId = itemId,
                    imageType = "Primary",
                    enableImageEnhancers = false
                )
            }
        }
    }
    val url = imageUrl ?: return
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(url)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .platformBitmapConfig(allowRgb565 = true)
            .crossfade(150)
            .build(),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop
    )
}
