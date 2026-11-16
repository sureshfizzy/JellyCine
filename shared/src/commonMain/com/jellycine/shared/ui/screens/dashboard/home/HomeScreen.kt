package com.jellycine.shared.ui.screens.dashboard.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.AuthRepository
import com.jellycine.data.repository.MediaRepository
import com.jellycine.shared.resources.Res
import com.jellycine.shared.resources.dashboard_failed_next_up
import com.jellycine.shared.resources.dashboard_next_up
import com.jellycine.shared.ui.components.common.AuthenticatedImage
import com.jellycine.shared.ui.components.common.UserProfileAvatar

@Composable
fun HomeScreen(
    mediaRepository: MediaRepository,
    authRepository: AuthRepository,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onItemClick: (BaseItemDto) -> Unit = {}
) {
    val viewModel: HomeViewModel = viewModel { HomeViewModel(mediaRepository, authRepository) }
    val state by viewModel.uiState.collectAsState()
    val imageLoader = remember(state.imageHeaders) { HomeImages(viewModel, state.imageHeaders) }
    var showAccountOverview by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item(key = "header") {
                Box {
                    if (state.featured.isNotEmpty()) {
                        FeaturedPager(state.featured, imageLoader, onItemClick)
                    } else {
                        Spacer(Modifier.statusBarsPadding().height(56.dp))
                    }
                    BrandHeader(
                        serverName = state.serverName,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .statusBarsPadding()
                    ) {
                        UserProfileAvatar(
                            imageUrl = state.profileImageUrl,
                            serverTypeRaw = state.serverType,
                            imageHeaders = state.imageHeaders,
                            onClick = { showAccountOverview = true },
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }

            if (state.continueWatching.isNotEmpty()) {
                item(key = "continue_watching") {
                    ContinueWatchingSection(
                        items = state.continueWatching,
                        isLoading = false,
                        error = null,
                        mediaRepository = mediaRepository,
                        imageHeaders = state.imageHeaders,
                        onItemClick = onItemClick
                    )
                }
            }

            if (state.nextUp.isNotEmpty()) {
                item(key = "next_up") {
                    ContinueWatchingSection(
                        titleRes = Res.string.dashboard_next_up,
                        errorMessageRes = Res.string.dashboard_failed_next_up,
                        items = state.nextUp,
                        isLoading = false,
                        error = null,
                        mediaRepository = mediaRepository,
                        imageHeaders = state.imageHeaders,
                        onItemClick = onItemClick
                    )
                }
            }

            items(state.sections, key = { it.library.id.orEmpty() }) { section ->
                BurstLibrarySection(
                    section = section.toUiSection(),
                    mediaRepository = mediaRepository,
                    disablePosterEnhancers = false,
                    imageHeaders = state.imageHeaders,
                    onItemClick = onItemClick
                )
            }
        }

        if (showAccountOverview) {
            AccountOverview(
                userName = state.username,
                serverName = state.serverName,
                profileImageUrl = state.profileImageUrl,
                imageHeaders = state.imageHeaders,
                serverTypeRaw = state.serverType,
                canChangeUser = false,
                onDismiss = { showAccountOverview = false },
                onChangeUser = {},
                onLogout = {
                    showAccountOverview = false
                    viewModel.logout()
                }
            )
        }
    }
}

private class HomeImages(
    private val viewModel: HomeViewModel,
    val headers: Map<String, String>
) {
    suspend fun url(itemId: String, type: String, width: Int, tag: String? = null): String? {
        return viewModel.imageUrl(itemId, type, width, tag)
    }
}

@Composable
private fun RemoteImage(
    images: HomeImages,
    itemId: String?,
    imageType: String,
    width: Int,
    tag: String?,
    modifier: Modifier = Modifier
) {
    val url by produceState<String?>(null, itemId, imageType, width, tag) {
        value = itemId?.let { images.url(it, imageType, width, tag) }
    }
    Box(modifier = modifier.background(Color(0xFF14141A))) {
        url?.let { imageUrl ->
            AuthenticatedImage(
                url = imageUrl,
                headers = images.headers,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun FeaturedPager(
    items: List<BaseItemDto>,
    images: HomeImages,
    onItemClick: (BaseItemDto) -> Unit
) {
    val pagerState = rememberPagerState { items.size }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f)
    ) { page ->
        val item = items[page]
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onItemClick(item) }
        ) {
            RemoteImage(
                images = images,
                itemId = item.id,
                imageType = if (item.backdropImageTags.isNullOrEmpty()) "Primary" else "Backdrop",
                width = 1080,
                tag = item.backdropImageTags?.firstOrNull() ?: item.imageTags?.get("Primary"),
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f), Color.Black)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.name.orEmpty(),
                    color = Color.White,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val meta = listOfNotNull(
                    item.productionYear?.toString(),
                    item.officialRating,
                    item.communityRating?.let { "★ ${(it * 10).toInt() / 10.0}" }
                ).joinToString("  •  ")
                if (meta.isNotEmpty()) {
                    Text(text = meta, color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                }
                item.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                    Text(
                        text = overview,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

