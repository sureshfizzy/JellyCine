package com.jellycine.ui.screens.dashboard.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.resources.Res
import com.jellycine.resources.dashboard_failed_load_my_media
import com.jellycine.resources.libraries
import org.jetbrains.compose.resources.stringResource

@Composable
fun HomeLibrariesRow(
    libraries: List<BaseItemDto>,
    mediaRepository: MediaRepository,
    disablePosterEnhancers: Boolean,
    modifier: Modifier = Modifier,
    imageHeaders: Map<String, String> = emptyMap(),
    isLoading: Boolean = false,
    hasError: Boolean = false,
    onLibraryClick: (BaseItemDto) -> Unit = {}
) {
    val lazyRowState = rememberLazyListState()
    val flingBehavior = ScrollOptimization.rememberUltraSmoothFlingBehavior()
    val firstLibraryId = libraries.firstOrNull()?.id

    LaunchedEffect(firstLibraryId, libraries.size) {
        val hasScrolled = lazyRowState.firstVisibleItemIndex > 0 ||
            lazyRowState.firstVisibleItemScrollOffset > 0
        if (hasScrolled) lazyRowState.scrollToItem(0)
    }

    Column(modifier = modifier.fillMaxWidth().background(Color.Black)) {
        Text(
            text = stringResource(Res.string.libraries),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        when {
            isLoading -> Unit
            hasError && libraries.isEmpty() -> Box(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    text = stringResource(Res.string.dashboard_failed_load_my_media),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp
                )
            }
            libraries.isNotEmpty() -> LazyRow(
                state = lazyRowState,
                horizontalArrangement = Arrangement.spacedBy(ScrollOptimization.listItemSpacing),
                contentPadding = ScrollOptimization.optimizedContentPadding,
                flingBehavior = flingBehavior,
                modifier = ScrollOptimization.getScrollContainerModifier()
            ) {
                itemsIndexed(
                    items = libraries,
                    key = { index, library -> "${library.id ?: library.name ?: "library_item"}_$index" }
                ) { _, library ->
                    val onClick = remember(library.id, library.name) { { onLibraryClick(library) } }
                    LibraryItemCard(
                        item = library,
                        mediaRepository = mediaRepository,
                        imageHeaders = imageHeaders,
                        disableImageEnhancers = disablePosterEnhancers,
                        useLandscapeLayout = true,
                        titleAreaHeightOverride = 40.dp,
                        onClick = onClick
                    )
                }
            }
        }
    }
}
