package com.jellycine.app.ui.screens.dashboard.search

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.util.image.disableEmbyPosterEnhancers
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
    title: String,
    movies: List<BaseItemDto>,
    isLoading: Boolean,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val disablePosterEnhancers = disableEmbyPosterEnhancers()
    val firstMovie = movies.firstOrNull()
    var isFirstImageReady by remember(isLoading, firstMovie?.id) {
        mutableStateOf(isLoading || firstMovie?.id == null)
    }

    LaunchedEffect(isLoading, firstMovie?.id, disablePosterEnhancers) {
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
            mediaRepository.getImageUrlString(
                itemId = itemId,
                imageType = "Primary",
                enableImageEnhancers = !disablePosterEnhancers
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
                val immersiveImageUrl = runCatching {
                    mediaRepository.getImageUrlString(
                        itemId = immersiveImageId,
                        imageType = "Primary",
                        enableImageEnhancers = !disablePosterEnhancers
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
                val backgroundImageUrl = runCatching {
                    mediaRepository.getImageUrlString(
                        itemId = backgroundItemId,
                        imageType = "Primary",
                        enableImageEnhancers = !disablePosterEnhancers
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
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        } else {
            SuggestionsStoriesView(
                suggestions = movies,
                onItemClick = onItemClick
            )
        }

        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(top = 60.dp, start = 24.dp)
        )
    }
}
