package com.vela.app.ui.screens.dashboard.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vela.data.model.BaseItemDto
import com.vela.data.repository.MediaRepositoryProvider
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
    pagerFocusRequester: FocusRequester = remember { FocusRequester() },
    onUpPressed: () -> Unit = {}
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

            prioritizedIds.firstOrNull()?.let { nextId ->
                val nextItem = movies.firstOrNull { it.id == nextId }
                val nextImageUrl = runCatching {
                    nextItem?.imageUrl ?: mediaRepository.getImageUrlString(
                        itemId = nextId,
                        imageType = "Primary",
                        enableImageEnhancers = false
                    )
                }.getOrNull()

                nextImageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                    runCatching {
                        context.imageLoader.execute(buildImmersiveImageRequest(context, imageUrl))
                    }
                }
            }

            prioritizedIds.drop(1).forEach { bgId ->
                val bgItem = movies.firstOrNull { it.id == bgId }
                val bgImageUrl = runCatching {
                    bgItem?.imageUrl ?: mediaRepository.getImageUrlString(
                        itemId = bgId,
                        imageType = "Primary",
                        enableImageEnhancers = false
                    )
                }.getOrNull()

                bgImageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
                    context.imageLoader.enqueue(buildImmersiveImageRequest(context, imageUrl))
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isLoading || !isFirstImageReady) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            SuggestionsStoriesView(
                suggestions = movies,
                onItemClick = onItemClick,
                mediaRepository = mediaRepository,
                pagerFocusRequester = pagerFocusRequester,
                onUpPressed = onUpPressed
            )
        }
    }
}

@Composable
internal fun DiscoveryTabChip(
    tab: SearchDiscoveryTab,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.selectable(selected = selected, onClick = onClick),
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