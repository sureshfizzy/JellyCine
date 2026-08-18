package com.jellycine.app.ui.screens.dashboard.search

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepositoryProvider
import coil3.imageLoader
import coil3.request.*
import kotlinx.coroutines.launch

private fun buildImmersiveImageRequest(
    context: android.content.Context,
    imageUrl: String
): ImageRequest {
    return ImageRequest.Builder(context)
        .data(imageUrl)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .networkCachePolicy(CachePolicy.ENABLED)
        .crossfade(false)
        .allowHardware(true)
        .allowRgb565(true)
        .build()
}

@Composable
fun ImmersiveSection(
    movies: List<BaseItemDto>,
    isLoading: Boolean,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier,
    discoveryTabs: List<SearchDiscoveryTab> = emptyList(),
    selectedDiscoveryTab: SearchDiscoveryTab? = null,
    onDiscoveryTabClick: (SearchDiscoveryTab) -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val firstMovie = movies.firstOrNull()
    var isFirstImageReady by remember(isLoading, firstMovie?.id) {
        mutableStateOf(isLoading || firstMovie?.id == null)
    }

    LaunchedEffect(isLoading, firstMovie?.id) {
        if (isLoading) {
            isFirstImageReady = false
            return@LaunchedEffect
        }

        val itemId = firstMovie?.id
        if (itemId.isNullOrBlank()) {
            isFirstImageReady = true
            return@LaunchedEffect
        }

        isFirstImageReady = false
        val firstImageUrl = runCatching {
            firstMovie?.imageUrl ?: mediaRepository.getImageUrlString(
                itemId = itemId,
                imageType = "Primary",
                enableImageEnhancers = false
            )
        }.getOrNull()

        firstImageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
            runCatching {
                context.imageLoader.execute(buildImmersiveImageRequest(context, imageUrl))
            }
        }

        isFirstImageReady = true

        launch {
            val prioritizedIds = buildList {
                movies.getOrNull(1)?.id?.let(::add)
                movies.lastOrNull()?.id?.let(::add)
                movies.asSequence()
                    .drop(2)
                    .mapNotNull { it.id }
                    .forEach(::add)
            }
                .distinct()
                .take(10)

            prioritizedIds.firstOrNull()?.let { immersiveImageId ->
                val immersiveItem = movies.firstOrNull { item -> item.id == immersiveImageId }
                val immersiveImageUrl = runCatching {
                    immersiveItem?.imageUrl ?: mediaRepository.getImageUrlString(
                        itemId = immersiveImageId,
                        imageType = "Primary",
                        enableImageEnhancers = false
                    )
                }.getOrNull()

                immersiveImageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                    runCatching {
                        context.imageLoader.execute(
                            buildImmersiveImageRequest(context, imageUrl)
                        )
                    }
                }
            }

            prioritizedIds.drop(1).forEach { backgroundItemId ->
                val backgroundItem = movies.firstOrNull { item -> item.id == backgroundItemId }
                val backgroundImageUrl = runCatching {
                    backgroundItem?.imageUrl ?: mediaRepository.getImageUrlString(
                        itemId = backgroundItemId,
                        imageType = "Primary",
                        enableImageEnhancers = false
                    )
                }.getOrNull()

                backgroundImageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                    context.imageLoader.enqueue(
                        buildImmersiveImageRequest(context, imageUrl)
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(LocalConfiguration.current.screenHeightDp.dp)
    ) {
        if (isLoading || !isFirstImageReady) {
            SuggestionsShimmer()
        } else {
            SuggestionsStoriesView(
                suggestions = movies,
                onItemClick = onItemClick
            )
        }

        if (discoveryTabs.isNotEmpty()) {
            LazyRow(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 60.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                items(discoveryTabs) { tab ->
                    DiscoveryTabChip(
                        tab = tab,
                        selected = tab == selectedDiscoveryTab,
                        onClick = { onDiscoveryTabClick(tab) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionsShimmer() {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val offset by transition.animateFloat(
        initialValue = -1f, targetValue = 2f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "offset"
    )
    val brush = Brush.linearGradient(
        listOf(Color(0xFF111111), Color(0xFF1A1A1A), Color(0xFF222222), Color(0xFF1A1A1A), Color(0xFF111111)),
        start = androidx.compose.ui.geometry.Offset(offset * 1000f, 0f),
        end = androidx.compose.ui.geometry.Offset(offset * 1000f + 600f, 0f)
    )

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(Modifier.width(280.dp).aspectRatio(0.67f).background(brush, RoundedCornerShape(20.dp)))
            Spacer(Modifier.height(32.dp))
            Box(Modifier.width(200.dp).height(14.dp).background(brush, RoundedCornerShape(7.dp)))
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                repeat(5) { i ->
                    val w = if (i == 2) 16.dp else if (i == 1 || i == 3) 8.dp else 6.dp
                    Box(Modifier.width(w).height(6.dp).background(brush, RoundedCornerShape(3.dp)))
                }
            }
        }
    }
}

@Composable
private fun DiscoveryTabChip(
    tab: SearchDiscoveryTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.selectable(
            selected = selected,
            onClick = onClick
        ),
        color = if (selected) Color.White else Color.White.copy(alpha = 0.12f),
        contentColor = if (selected) Color.Black else Color.White,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Text(
            text = tab.label(),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}