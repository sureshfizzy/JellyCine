package com.jellycine.app.ui.screens.dashboard.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jellycine.app.ui.components.common.InlineTrailerPlayer
import com.jellycine.data.model.ClipFeedItem
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.player.core.RemoteTrailerUrl
import com.jellycine.shared.ui.components.common.ShimmerEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FEED_MAX_HEIGHT_PHONE = 720

@Composable
fun ClipsFeedScreen(
    modifier: Modifier = Modifier,
    topOverlay: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val feedMaxHeight = if (isTablet) Int.MAX_VALUE else FEED_MAX_HEIGHT_PHONE
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val scope = rememberCoroutineScope()

    var clips by remember { mutableStateOf<List<ClipFeedItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isAppending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (clips.isNotEmpty()) return@LaunchedEffect
        isLoading = true
        val first = withContext(Dispatchers.IO) { mediaRepository.getTrendingClips() }
        if (first.isEmpty()) {
            error = "Couldn't load clips. Check your connection and try again."
        } else {
            clips = first
            first.firstOrNull()?.let { launch { RemoteTrailerUrl.prefetch(it.youtubeWatchUrl, feedMaxHeight) } }
        }
        isLoading = false
    }

    val pagerState = rememberPagerState(pageCount = { clips.size })

    LaunchedEffect(pagerState, clips) {
        snapshotFlow { pagerState.currentPage }.collect { current ->
            for (offset in 1..2) {
                clips.getOrNull(current + offset)?.let { ahead ->
                    launch { RemoteTrailerUrl.prefetch(ahead.youtubeWatchUrl, feedMaxHeight) }
                }
            }
            if (current >= clips.size - 3 && !isAppending) {
                isAppending = true
                val more = withContext(Dispatchers.IO) { mediaRepository.getTrendingClips() }
                if (more.isNotEmpty()) {
                    val existing = clips.mapTo(HashSet()) { it.youtubeKey }
                    clips = clips + more.filter { it.youtubeKey !in existing }
                }
                isAppending = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading && clips.isEmpty() -> {
                ClipsFeedShimmer()
            }

            error != null && clips.isEmpty() -> {
                Text(
                    text = error.orEmpty(),
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(32.dp)
                )
            }

            else -> {
                VerticalPager(
                    state = pagerState,
                    beyondViewportPageCount = 0,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val clip = clips[page]
                    ClipPage(
                        clip = clip,
                        isActive = pagerState.currentPage == page,
                        onCompleted = {
                            if (page + 1 < clips.size) {
                                scope.launch { pagerState.animateScrollToPage(page + 1) }
                            }
                        }
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
        ) {
            topOverlay()
        }
    }
}

@Composable
private fun ClipsFeedShimmer(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        ShimmerEffect(
            modifier = Modifier.fillMaxSize(),
            cornerRadius = 0f
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 118.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ShimmerEffect(
                modifier = Modifier.size(58.dp),
                shape = CircleShape
            )
            ShimmerEffect(
                modifier = Modifier
                    .width(48.dp)
                    .height(10.dp),
                cornerRadius = 4f
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.72f)
                .padding(start = 20.dp, bottom = 118.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ShimmerEffect(
                modifier = Modifier
                    .fillMaxWidth(0.75f)
                    .height(24.dp),
                cornerRadius = 8f
            )
            ShimmerEffect(
                modifier = Modifier
                    .fillMaxWidth(0.4f)
                    .height(13.dp),
                cornerRadius = 6f
            )
            ShimmerEffect(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .height(14.dp),
                cornerRadius = 6f
            )
        }
    }
}

@Composable
private fun ClipPage(
    clip: ClipFeedItem,
    isActive: Boolean,
    onCompleted: () -> Unit
) {
    var expanded by remember(clip.youtubeKey) { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = clip.backdropUrl ?: clip.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        InlineTrailerPlayer(
            trailerUrl = clip.youtubeWatchUrl,
            isVisible = isActive,
            showBuiltInMute = false,
            modifier = Modifier.fillMaxSize(),
            onPlaybackCompleted = onCompleted
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.9f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 118.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AsyncImage(
                model = clip.posterUrl ?: clip.thumbnailUrl,
                contentDescription = clip.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(58.dp)
                    .shadow(elevation = 8.dp, shape = CircleShape, clip = false)
                    .clip(CircleShape)
                    .border(2.dp, Color.White, CircleShape)
            )
            Text(
                text = clip.title.uppercase(),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(64.dp)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.72f)
                .padding(start = 20.dp, bottom = 118.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = clip.title,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (clip.genreLine.isNotBlank()) {
                Text(
                    text = clip.genreLine,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val synopsis = clip.overview
            if (!synopsis.isNullOrBlank()) {
                Text(
                    text = synopsis,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 14.sp,
                    maxLines = if (expanded) 6 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = !expanded }
                )
            }
        }
    }
}