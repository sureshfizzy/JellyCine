package com.jellycine.shared.ui.screens.dashboard.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.jellycine.shared.ui.components.common.AuthenticatedImage
import com.jellycine.shared.ui.components.common.UserProfileAvatar
import com.jellycine.shared.resources.dashboard_continue_watching
import com.jellycine.shared.resources.dashboard_next_up
import org.jetbrains.compose.resources.stringResource

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
            if (state.featured.isNotEmpty()) {
                item { FeaturedPager(state.featured, imageLoader, onItemClick) }
            } else {
                item { Spacer(Modifier.statusBarsPadding().height(56.dp)) }
            }

            if (state.continueWatching.isNotEmpty()) {
                item {
                    LandscapeRow(
                        title = stringResource(Res.string.dashboard_continue_watching),
                        items = state.continueWatching,
                        images = imageLoader,
                        onItemClick = onItemClick
                    )
                }
            }

            if (state.nextUp.isNotEmpty()) {
                item {
                    LandscapeRow(
                        title = stringResource(Res.string.dashboard_next_up),
                        items = state.nextUp,
                        images = imageLoader,
                        onItemClick = onItemClick
                    )
                }
            }

            items(state.sections, key = { it.library.id.orEmpty() }) { section ->
                PosterRow(
                    title = section.library.name.orEmpty(),
                    items = section.items,
                    images = imageLoader,
                    onItemClick = onItemClick
                )
            }
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

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        color = Color.White,
        fontSize = 19.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 20.dp)
    )
}

@Composable
private fun LandscapeRow(
    title: String,
    items: List<BaseItemDto>,
    images: HomeImages,
    onItemClick: (BaseItemDto) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.id.orEmpty() }) { item ->
                Column(
                    modifier = Modifier
                        .width(240.dp)
                        .clickable { onItemClick(item) },
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box {
                        val useEpisodeBackdrop = item.type.equals("Episode", ignoreCase = true)
                        RemoteImage(
                            images = images,
                            itemId = if (useEpisodeBackdrop) item.seriesId ?: item.id else item.id,
                            imageType = if (item.backdropImageTags.isNullOrEmpty() && !useEpisodeBackdrop) "Primary" else "Backdrop",
                            width = 600,
                            tag = if (useEpisodeBackdrop) null else item.backdropImageTags?.firstOrNull(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        val progress = item.userData?.playedPercentage
                        if (progress != null && progress > 0.0) {
                            LinearProgressIndicator(
                                progress = { (progress / 100.0).toFloat().coerceIn(0f, 1f) },
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.25f),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .height(3.dp)
                            )
                        }
                    }
                    Text(
                        text = item.seriesName ?: item.name.orEmpty(),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.seriesName != null) {
                        Text(
                            text = item.name.orEmpty(),
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterRow(
    title: String,
    items: List<BaseItemDto>,
    images: HomeImages,
    onItemClick: (BaseItemDto) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle(title)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.id.orEmpty() }) { item ->
                Column(
                    modifier = Modifier
                        .width(120.dp)
                        .clickable { onItemClick(item) },
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RemoteImage(
                        images = images,
                        itemId = item.id,
                        imageType = "Primary",
                        width = 360,
                        tag = item.imageTags?.get("Primary"),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(10.dp))
                    )
                    Text(
                        text = item.name.orEmpty(),
                        color = Color.White,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
