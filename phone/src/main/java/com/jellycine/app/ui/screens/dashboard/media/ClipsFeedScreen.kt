package com.jellycine.app.ui.screens.dashboard.media

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Share
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import coil3.compose.AsyncImage
import com.jellycine.app.ui.components.common.InlineTrailerPlayer
import com.jellycine.app.ui.components.common.createInlineExoPlayer
import com.jellycine.data.model.ClipFeedFilters
import com.jellycine.data.model.ClipFeedItem
import com.jellycine.preferences.Preferences
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.player.core.RemoteTrailerUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val FEED_MAX_HEIGHT_PHONE = 720
private const val PLAYER_POOL_SIZE = 3
private val FLOATING_NAV_HEIGHT = 68.dp + 20.dp + 8.dp
private const val FIRST_BATCH_TITLES = 8
private const val MAX_TITLES_PER_BATCH = 30

private suspend fun MediaRepository.loadShorts(
    maxVideoHeight: Int,
    filters: ClipFeedFilters,
    titleLimit: Int = MAX_TITLES_PER_BATCH
): List<ClipFeedItem> = coroutineScope {
    val gate = Semaphore(6)
    getTrendingClips(filters)
        .take(titleLimit)
        .map { title ->
            async {
                gate.withPermit { RemoteTrailerUrl.searchShortClipKeys(title.title, maxVideoHeight) }
                    .map { key -> title.copy(youtubeKey = key) }
            }
        }
        .awaitAll()
        .flatten()
        .distinctBy { it.youtubeKey }
        .shuffled()
}

@Composable
fun ClipsFeedScreen(
    modifier: Modifier = Modifier,
    topOverlay: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600
    val preferences = remember { Preferences(context) }
    val maxQuality by preferences.clipsMaxQuality()
        .collectAsStateWithLifecycle(initialValue = preferences.getClipsMaxQuality())
    val contentType by preferences.clipsContentType()
        .collectAsStateWithLifecycle(initialValue = preferences.getClipsContentType())
    val trendingWindow by preferences.clipsTrendingWindow()
        .collectAsStateWithLifecycle(initialValue = preferences.getClipsTrendingWindow())
    val language = configuration.locales[0].toLanguageTag().takeIf { it != "und" } ?: "en-US"
    val excludedGenres by preferences.clipsExcludedGenres()
        .collectAsStateWithLifecycle(initialValue = preferences.getClipsExcludedGenres())
    val feedMaxHeight = when {
        maxQuality > 0 -> maxQuality
        isTablet -> Int.MAX_VALUE
        else -> FEED_MAX_HEIGHT_PHONE
    }
    val filters = remember(contentType, trendingWindow, language, excludedGenres) {
        ClipFeedFilters(
            window = trendingWindow,
            mediaType = contentType,
            language = language,
            excludedGenreIds = excludedGenres
        )
    }
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val scope = rememberCoroutineScope()
    val players = remember { List(PLAYER_POOL_SIZE) { createInlineExoPlayer(context) } }

    DisposableEffect(players) {
        onDispose {
            players.forEach { it.release() }
        }
    }

    var clips by remember { mutableStateOf<List<ClipFeedItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isAppending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var muted by rememberSaveable {
        mutableStateOf(preferences.isClipsMuted())
    }

    LaunchedEffect(filters, feedMaxHeight) {
        clips = emptyList()
        error = null
        isAppending = false
        isLoading = true
        val first = withContext(Dispatchers.IO) {
            mediaRepository.loadShorts(feedMaxHeight, filters, FIRST_BATCH_TITLES)
        }
        if (first.isEmpty()) {
            error = "Couldn't load clips. Check your connection and try again."
        } else {
            clips = first
        }
        isLoading = false
        if (first.isNotEmpty()) {
            isAppending = true
            val more = withContext(Dispatchers.IO) { mediaRepository.loadShorts(feedMaxHeight, filters) }
            val existing = clips.mapTo(HashSet()) { it.youtubeKey }
            clips = clips + more.filter { it.youtubeKey !in existing }
            isAppending = false
        }
    }

    val pagerState = rememberPagerState(pageCount = { clips.size })

    LaunchedEffect(pagerState, clips, filters, feedMaxHeight) {
        snapshotFlow { pagerState.currentPage }.collect { current ->
            for (offset in 1..2) {
                clips.getOrNull(current + offset)?.let { ahead ->
                    launch { RemoteTrailerUrl.prefetch(ahead.youtubeWatchUrl, feedMaxHeight) }
                }
            }
            if (current >= clips.size - 3 && !isAppending) {
                isAppending = true
                val more = withContext(Dispatchers.IO) { mediaRepository.loadShorts(feedMaxHeight, filters) }
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
                ClipsFeedLoader()
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
                    val advance = {
                        if (page + 1 < clips.size && pagerState.currentPage == page) {
                            scope.launch { pagerState.animateScrollToPage(page + 1) }
                        }
                    }
                    ClipPage(
                        clip = clip,
                        isActive = pagerState.currentPage == page,
                        loadEnabled = abs(pagerState.currentPage - page) <= 1,
                        player = players[page % players.size],
                        muted = muted,
                        onToggleMute = {
                            muted = !muted
                            preferences.setClipsMuted(muted)
                        },
                        onCompleted = advance
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

private const val LOADER_STEP_MS = 2200L
private const val LOADER_LAST_STEP_DELAY_MS = 6000L
private val LOADING_STEPS = listOf(
    "Finding what's trending",
    "Searching for the best short clips",
    "Picking clips worth your time",
    "Queuing up your feed",
    "Taking a bit longer than usual…"
)

@Composable
private fun ClipsFeedLoader(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "clipsLoader")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "rotation"
    )
    val sweep by transition.animateFloat(
        initialValue = 70f,
        targetValue = 250f,
        animationSpec = infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sweep"
    )
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (step < LOADING_STEPS.lastIndex) {
            delay(if (step == LOADING_STEPS.lastIndex - 1) LOADER_LAST_STEP_DELAY_MS else LOADER_STEP_MS)
            step++
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 3.dp.toPx()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.1f),
                        radius = (size.minDimension - stroke) / 2f,
                        style = Stroke(width = stroke)
                    )
                    rotate(rotation) {
                        drawArc(
                            brush = Brush.sweepGradient(listOf(Color.Transparent, Color.White)),
                            startAngle = 0f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = Offset(stroke / 2f, stroke / 2f),
                            size = Size(size.width - stroke, size.height - stroke),
                            style = Stroke(width = stroke, cap = StrokeCap.Round)
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Loading clips",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = step,
                transitionSpec = { fadeIn(tween(450)) togetherWith fadeOut(tween(300)) },
                label = "loaderStep"
            ) { index ->
                Text(
                    text = LOADING_STEPS[index],
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }
}

@Composable
private fun ClipPage(
    clip: ClipFeedItem,
    isActive: Boolean,
    loadEnabled: Boolean,
    player: ExoPlayer,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onCompleted: () -> Unit
) {
    val context = LocalContext.current
    var expanded by remember(clip.youtubeKey) { mutableStateOf(false) }
    var paused by remember(clip.youtubeKey) { mutableStateOf(false) }
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val contentBottom = navInset + FLOATING_NAV_HEIGHT
    val owns = { player.currentMediaItem?.mediaId == clip.youtubeWatchUrl }

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = clip.backdropUrl ?: clip.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        InlineTrailerPlayer(
            player = player,
            trailerUrl = clip.youtubeWatchUrl,
            isVisible = isActive,
            loadEnabled = loadEnabled,
            showBuiltInMute = false,
            mutedOverride = muted,
            modifier = Modifier.fillMaxSize(),
            onPlaybackCompleted = onCompleted
        )

        // Tap anywhere to pause / resume
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    if (isActive && owns()) {
                        paused = !paused
                        player.playWhenReady = !paused
                    }
                }
        )

        // Top + bottom scrims for legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.22f to Color.Transparent,
                        0.4f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.95f)
                    )
                )
        )

        AnimatedVisibility(
            visible = paused && isActive,
            enter = fadeIn() + scaleIn(initialScale = 0.8f),
            exit = fadeOut() + scaleOut(targetScale = 0.8f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        // Right action rail
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = contentBottom + 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .clickable(onClick = onToggleMute),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    contentDescription = if (muted) "Unmute" else "Mute",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                    .clickable {
                        runCatching {
                            val send = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, "${clip.title} ${clip.youtubeWatchUrl}")
                            context.startActivity(
                                Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            AsyncImage(
                model = clip.posterUrl ?: clip.thumbnailUrl,
                contentDescription = clip.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(46.dp)
                    .height(66.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(8.dp), clip = false)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.5.dp, Color.White, RoundedCornerShape(8.dp))
            )
        }

        // Bottom info
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.76f)
                .padding(start = 20.dp, bottom = contentBottom + 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (clip.mediaType.equals("tv", true)) "SERIES" else "MOVIE",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
            Text(
                text = clip.title,
                color = Color.White,
                fontSize = 26.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (clip.genreLine.isNotBlank()) {
                Text(
                    text = clip.genreLine,
                    color = Color.White.copy(alpha = 0.85f),
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
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = if (expanded) 8 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .animateContentSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { expanded = !expanded }
                )
            }
        }
    }
}