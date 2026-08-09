package com.jellycine.app.ui.screens.dashboard.search

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
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepositoryProvider
import coil3.imageLoader
import coil3.request.*
import kotlinx.coroutines.launch

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
    var isPreloaded by remember(isLoading, movies.firstOrNull()?.id) {
        mutableStateOf(isLoading || movies.isEmpty())
    }

    LaunchedEffect(isLoading, movies.firstOrNull()?.id) {
        if (isLoading || movies.isEmpty()) {
            isPreloaded = false
            return@LaunchedEffect
        }

        isPreloaded = false
        val imageLoader = context.imageLoader
        val itemsToPreload = movies.take(5)

        itemsToPreload.firstOrNull()?.let { item ->
            val imageUrl = item.imageUrl ?: runCatching {
                mediaRepository.getImageUrlString(
                    itemId = item.id ?: "",
                    imageType = "Primary",
                    enableImageEnhancers = false
                )
            }.getOrNull()

            imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                runCatching {
                    imageLoader.execute(
                        ImageRequest.Builder(context)
                            .data(url)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .crossfade(false)
                            .allowHardware(true)
                            .build()
                    )
                }
            }
        }

        isPreloaded = true

        launch {
            itemsToPreload.drop(1).forEach { item ->
                val imageUrl = item.imageUrl ?: runCatching {
                    mediaRepository.getImageUrlString(
                        itemId = item.id ?: "",
                        imageType = "Primary",
                        enableImageEnhancers = false
                    )
                }.getOrNull()

                imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
                    imageLoader.enqueue(
                        ImageRequest.Builder(context)
                            .data(url)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .crossfade(false)
                            .allowHardware(true)
                            .build()
                    )
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isLoading || !isPreloaded) {
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