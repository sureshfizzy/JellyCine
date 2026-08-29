package com.vela.app.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vela.data.model.BaseItemDto
import com.vela.data.model.BaseItemPerson
import com.vela.data.repository.MediaRepository
import com.vela.player.core.ChapterMarker
import com.vela.shared.R
import com.vela.shared.util.image.JellyfinPosterImage
import com.vela.shared.util.image.rememberImageUrl
import kotlin.math.abs
import kotlin.math.roundToInt

private val PlaybackInfoPanelColor = Color(0xFF161618)
private val PlaybackInfoOverviewColor = Color.White.copy(alpha = 0.62f)
private val PlaybackInfoMetaColor = Color.White.copy(alpha = 0.48f)
private val PlaybackInfoBadgeColor = Color(0xE61B3358)
private val PlaybackInfoHandleColor = Color.White.copy(alpha = 0.28f)
private val PlaybackInfoEpisodeShape = RoundedCornerShape(10.dp)

@Composable
fun PlaybackInfoSheet(
    item: BaseItemDto?,
    title: String,
    isPortrait: Boolean,
    currentItemId: String?,
    mediaRepository: MediaRepository,
    onDismiss: () -> Unit,
    onEpisodeSelected: (String) -> Unit
) {
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 88.dp.toPx() }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val scrollState = rememberScrollState()

    fun settleDismiss(velocityY: Float = 0f) {
        val shouldDismiss = offsetY > dismissThresholdPx || velocityY > 1800f
        if (shouldDismiss) {
            onDismiss()
        } else {
            offsetY = 0f
        }
    }

    val nestedScroll = remember(dismissThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val delta = available.y
                if (delta > 0f && scrollState.value == 0) {
                    offsetY = (offsetY + delta).coerceAtLeast(0f)
                    return Offset(0f, delta)
                }
                if (delta < 0f && offsetY > 0f) {
                    val consumed = delta.coerceAtLeast(-offsetY)
                    offsetY += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetY != 0f || (scrollState.value == 0 && abs(available.y) > 1800f)) {
                    settleDismiss(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val panelModifier = Modifier
        .offset { IntOffset(0, offsetY.roundToInt()) }
        .nestedScroll(nestedScroll)

    if (isPortrait) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onDismiss)
            )
            // 竖屏保持视频居中，信息卡仅作为覆盖层，不再挤占视频的布局空间。
            PlaybackInfoPanel(
                item = item,
                title = title,
                showSeriesName = false,
                currentItemId = currentItemId,
                mediaRepository = mediaRepository,
                scrollState = scrollState,
                onEpisodeSelected = onEpisodeSelected,
                onHandleDrag = { delta ->
                    offsetY = (offsetY + delta).coerceAtLeast(0f)
                },
                onHandleDragEnd = { settleDismiss() },
                modifier = panelModifier
                    .align(Alignment.Center)
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .heightIn(
                        min = 320.dp,
                        max = maxHeight * 0.58f
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(PlaybackInfoPanelColor),
                applyBottomInset = false
            )
        }
    } else {
        LandscapePlaybackInfo(
            item = item,
            title = title,
            currentItemId = currentItemId,
            mediaRepository = mediaRepository,
            scrollState = scrollState,
            onDismiss = onDismiss,
            onEpisodeSelected = onEpisodeSelected
        )
    }
}


@Composable
private fun LandscapePlaybackInfo(
    item: BaseItemDto?,
    title: String,
    currentItemId: String?,
    mediaRepository: MediaRepository,
    scrollState: androidx.compose.foundation.ScrollState,
    onDismiss: () -> Unit,
    onEpisodeSelected: (String) -> Unit
) {
    val density = LocalDensity.current
    val collapseThresholdPx = with(density) { 96.dp.toPx() }
    // 摘要栏本身较矮，关闭阈值需小于可用拖动距离，避免手指抵达屏幕底部仍无法关闭。
    val dismissThresholdPx = with(density) { 40.dp.toPx() }
    var collapsed by remember { mutableStateOf(false) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    fun settle(velocityY: Float = 0f) {
        if (collapsed) {
            if (offsetY > dismissThresholdPx || velocityY > 900f) {
                onDismiss()
                return
            }
            if (velocityY < -900f || offsetY < -48f) {
                collapsed = false
                offsetY = 0f
                return
            }
            offsetY = 0f
            return
        }
        if (offsetY > collapseThresholdPx || velocityY > 1400f) {
            collapsed = true
            offsetY = 0f
            return
        }
        offsetY = 0f
    }

    val nestedScroll = remember(collapseThresholdPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val delta = available.y
                if (delta > 0f && scrollState.value == 0) {
                    offsetY = (offsetY + delta).coerceAtLeast(0f)
                    return Offset(0f, delta)
                }
                if (delta < 0f && offsetY > 0f) {
                    val consumed = delta.coerceAtLeast(-offsetY)
                    offsetY += consumed
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (collapsed) return Velocity.Zero
                if (offsetY != 0f || (scrollState.value == 0 && abs(available.y) > 1400f)) {
                    settle(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val posterUrl = rememberImageUrl(
        itemId = item?.id,
        imageType = "Primary",
        width = 480,
        height = 270,
        quality = 80,
        mediaRepository = mediaRepository
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onDismiss)
        )
        if (collapsed) {
            LandscapeInfoMiniBar(
                item = item,
                title = title,
                posterUrl = posterUrl,
                onClick = {
                    collapsed = false
                    offsetY = 0f
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 48.dp)
                    .offset { IntOffset(0, offsetY.roundToInt()) }
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(PlaybackInfoPanelColor)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, dragAmount ->
                                // 同步记录位移，抬手结算时不会再读到上一帧的旧值。
                                offsetY += dragAmount
                            },
                            onDragEnd = { settle() },
                            onDragCancel = { settle() }
                        )
                    }
            )
        } else {
            PlaybackInfoPanel(
                item = item,
                title = title,
                showSeriesName = true,
                currentItemId = currentItemId,
                mediaRepository = mediaRepository,
                scrollState = scrollState,
                onEpisodeSelected = onEpisodeSelected,
                onHandleDrag = { delta ->
                    offsetY = (offsetY + delta).coerceAtLeast(0f)
                },
                onHandleDragEnd = { settle() },
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                    .padding(start = 48.dp, top = 28.dp, end = 48.dp)
                    .offset { IntOffset(0, offsetY.roundToInt()) }
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(PlaybackInfoPanelColor)
                    .nestedScroll(nestedScroll)
            )
        }
    }
}

@Composable
private fun LandscapeInfoMiniBar(
    item: BaseItemDto?,
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val episodeTitle = playbackEpisodeTitle(item, title)
    val seriesName = item?.seriesName?.takeIf { it.isNotBlank() }
    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(108.dp)
                    .height(60.dp)
                    .clip(PlaybackInfoEpisodeShape)
                    .background(Color(0xFF2A2A2A))
            ) {
                if (!posterUrl.isNullOrBlank()) {
                    JellyfinPosterImage(
                        context = context,
                        imageUrl = posterUrl,
                        contentDescription = episodeTitle,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                if (!seriesName.isNullOrBlank()) {
                    Text(
                        text = seriesName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = episodeTitle,
                    color = Color.White.copy(alpha = 0.86f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PlaybackInfoPanel(
    item: BaseItemDto?,
    title: String,
    showSeriesName: Boolean,
    currentItemId: String?,
    mediaRepository: MediaRepository,
    scrollState: androidx.compose.foundation.ScrollState,
    onEpisodeSelected: (String) -> Unit,
    onHandleDrag: (Float) -> Unit,
    onHandleDragEnd: () -> Unit,
    applyBottomInset: Boolean = true,
    modifier: Modifier = Modifier
) {
    var resolvedPeople by remember(item?.id) {
        mutableStateOf(item?.people.orEmpty())
    }
    var seasonEpisodes by remember(item?.seriesId, item?.seasonId) {
        mutableStateOf<List<BaseItemDto>>(emptyList())
    }

    LaunchedEffect(item?.id, item?.people, item?.seriesId) {
        val localPeople = item?.people.orEmpty().filter { !it.name.isNullOrBlank() }
        if (localPeople.isNotEmpty()) {
            resolvedPeople = localPeople
        } else {
            val fromItem = item?.id
                ?.takeIf { it.isNotBlank() }
                ?.let { mediaRepository.getItemById(it).getOrNull()?.people }
                .orEmpty()
                .filter { !it.name.isNullOrBlank() }
            if (fromItem.isNotEmpty()) {
                resolvedPeople = fromItem
            } else {
                resolvedPeople = item?.seriesId
                    ?.takeIf { it.isNotBlank() }
                    ?.let { mediaRepository.getItemById(it).getOrNull()?.people }
                    .orEmpty()
                    .filter { !it.name.isNullOrBlank() }
            }
        }
    }

    LaunchedEffect(item?.seriesId, item?.seasonId) {
        val seriesId = item?.seriesId?.takeIf { it.isNotBlank() }
        if (seriesId == null || !item.type.equals("Episode", ignoreCase = true)) {
            seasonEpisodes = emptyList()
            return@LaunchedEffect
        }
        seasonEpisodes = mediaRepository.getEpisodes(
            seriesId = seriesId,
            seasonId = item.seasonId
        ).getOrNull().orEmpty()
    }

    val posterUrl = rememberImageUrl(
        itemId = playbackPosterItemId(item),
        imageType = "Primary",
        width = 240,
        height = 360,
        quality = 80,
        imageTag = playbackPosterImageTag(item),
        mediaRepository = mediaRepository
    )
    val episodeListState = rememberLazyListState()
    LaunchedEffect(seasonEpisodes, currentItemId) {
        val index = seasonEpisodes.indexOfFirst { it.id == currentItemId }
        if (index >= 0) {
            episodeListState.scrollToItem(index)
        }
    }

    Column(
        modifier = modifier
            .then(if (applyBottomInset) Modifier.navigationBarsPadding() else Modifier)
            .verticalScroll(scrollState)
            .padding(bottom = 20.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { _, dragAmount -> onHandleDrag(dragAmount) },
                        onDragEnd = onHandleDragEnd,
                        onDragCancel = onHandleDragEnd
                    )
                }
                .padding(top = 10.dp, bottom = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(PlaybackInfoHandleColor)
            )
        }

        PlaybackInfoHeader(
            item = item,
            title = title,
            showSeriesName = showSeriesName,
            posterUrl = posterUrl
        )

        val overview = item?.overview.orEmpty()
        if (overview.isNotBlank()) {
            Text(
                text = overview,
                color = PlaybackInfoOverviewColor,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
            )
        }


        if (seasonEpisodes.isNotEmpty()) {
            Text(
                text = playbackFromSeasonLabel(item),
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 12.dp)
            )
            LazyRow(
                state = episodeListState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 18.dp)
            ) {
                itemsIndexed(
                    items = seasonEpisodes,
                    key = { index, episode -> "${episode.id ?: episode.name}_$index" }
                ) { _, episode ->
                    PlaybackEpisodeCard(
                        episode = episode,
                        mediaRepository = mediaRepository,
                        onClick = {
                            episode.id?.takeIf { it.isNotBlank() }?.let(onEpisodeSelected)
                        }
                    )
                }
            }
        }

        val people = remember(resolvedPeople) {
            playbackPeople(resolvedPeople)
        }
        if (people.isNotEmpty()) {
            PlaybackPeopleSection(
                people = people,
                mediaRepository = mediaRepository,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun PlaybackInfoHeader(
    item: BaseItemDto?,
    title: String,
    showSeriesName: Boolean,
    posterUrl: String?
) {
    val context = LocalContext.current
    val episodeTitle = playbackEpisodeTitle(item, title)
    val seriesName = item?.seriesName?.takeIf { it.isNotBlank() }
    val dateLabel = formatPlaybackPremiereDate(item?.premiereDate)
    val ratingLabel = item?.officialRating?.takeIf { it.isNotBlank() }
    val communityRatingLabel = item?.communityRating
        ?.takeIf { it > 0f }
        ?.let { String.format("%.1f", it) }
    val tagLine = remember(item?.tags, item?.genres) {
        val tags = item?.tags.orEmpty().mapNotNull { it.takeIf { tag -> tag.isNotBlank() } }
        val genres = item?.genres.orEmpty().mapNotNull { it.takeIf { genre -> genre.isNotBlank() } }
        (tags.ifEmpty { genres }).joinToString(" / ")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(92.dp)
                .aspectRatio(2f / 3f)
                .clip(PlaybackInfoEpisodeShape)
                .background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) {
            if (!posterUrl.isNullOrBlank()) {
                JellyfinPosterImage(
                    context = context,
                    imageUrl = posterUrl,
                    contentDescription = episodeTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            if (showSeriesName && !seriesName.isNullOrBlank()) {
                Text(
                    text = seriesName,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = episodeTitle,
                color = Color.White,
                fontSize = if (showSeriesName) 16.sp else 17.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (communityRatingLabel != null || dateLabel != null || ratingLabel != null) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (communityRatingLabel != null) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            tint = Color(0xFFFF5A5A),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = communityRatingLabel,
                            color = Color.White.copy(alpha = 0.92f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (dateLabel != null) {
                        Text(
                            text = dateLabel,
                            color = PlaybackInfoMetaColor,
                            fontSize = 13.sp
                        )
                    }
                    if (ratingLabel != null) {
                        Text(
                            text = ratingLabel,
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            modifier = Modifier
                                .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(5.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            if (tagLine.isNotBlank()) {
                Text(
                    text = tagLine,
                    color = PlaybackInfoMetaColor,
                    fontSize = 12.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun PlaybackPeopleSection(
    people: List<BaseItemPerson>,
    mediaRepository: MediaRepository,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.detail_cast_and_crew),
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = 10.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 18.dp)
        ) {
            itemsIndexed(
                items = people,
                key = { index, person ->
                    "${person.id ?: "${person.name}-${person.role}-${person.type}"}_$index"
                }
            ) { _, person ->
                PlaybackPersonCard(
                    person = person,
                    mediaRepository = mediaRepository
                )
            }
        }
    }
}

@Composable
private fun PlaybackPersonCard(
    person: BaseItemPerson,
    mediaRepository: MediaRepository
) {
    val context = LocalContext.current
    val fetchedUrl = rememberImageUrl(
        itemId = person.id,
        imageType = "Primary",
        width = 240,
        height = null,
        quality = 80,
        imageTag = person.primaryImageTag,
        mediaRepository = mediaRepository
    )
    val imageUrl = person.imageUrl?.takeIf { it.isNotBlank() } ?: fetchedUrl
    val roleLabel = playbackPersonRole(person)
    val personName = person.name?.takeIf { it.isNotBlank() }

    Column(
        modifier = Modifier.width(86.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(PlaybackInfoEpisodeShape)
                .background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) {
            if (!imageUrl.isNullOrBlank()) {
                JellyfinPosterImage(
                    context = context,
                    imageUrl = imageUrl,
                    contentDescription = person.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = person.name,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        if (!personName.isNullOrBlank()) {
            Text(
                text = personName,
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (!roleLabel.isNullOrBlank()) {
            Text(
                text = roleLabel,
                color = Color.White.copy(alpha = 0.52f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun playbackPersonRole(person: BaseItemPerson): String? {
    val role = person.role?.takeIf { it.isNotBlank() }
    return when {
        person.type.equals("Director", ignoreCase = true) ||
            role.equals("Director", ignoreCase = true) -> {
            stringResource(R.string.player_info_director)
        }
        person.type.equals("Actor", ignoreCase = true) -> {
            role.takeUnless { it.equals("Actor", ignoreCase = true) }
                ?: stringResource(R.string.player_info_cast)
        }
        !role.isNullOrBlank() -> role
        !person.type.isNullOrBlank() -> person.type
        else -> null
    }
}

private fun playbackPosterItemId(item: BaseItemDto?): String? {
    val media = item ?: return null
    if (media.type.equals("Episode", ignoreCase = true)) {
        return media.parentPrimaryImageItemId?.takeIf { it.isNotBlank() }
            ?: media.seriesId?.takeIf { it.isNotBlank() }
            ?: media.id
    }
    return media.id
}

private fun playbackPosterImageTag(item: BaseItemDto?): String? {
    val media = item ?: return null
    if (media.type.equals("Episode", ignoreCase = true)) {
        return media.parentPrimaryImageTag ?: media.seriesPrimaryImageTag
    }
    return media.imageTags?.get("Primary")
}

private fun playbackPeople(people: List<BaseItemPerson>): List<BaseItemPerson> {
    val clean = people.filter { !it.name.isNullOrBlank() }
    if (clean.isEmpty()) return emptyList()
    val actors = clean.filter { it.type.equals("Actor", ignoreCase = true) }
    val crew = clean.filterNot { it.type.equals("Actor", ignoreCase = true) }
    return (actors + crew).distinctBy { it.id ?: "${it.name}-${it.role}-${it.type}" }
}

private fun aspectRatioFromSize(width: Float, height: Float): Float? {
    if (!width.isFinite() || !height.isFinite() || width <= 0f || height <= 0f) return null
    return (width / height).takeIf { it.isFinite() && it > 0f }
}

@Composable
private fun PlaybackEpisodeCard(
    episode: BaseItemDto,
    mediaRepository: MediaRepository,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val imageUrl = rememberImageUrl(
        itemId = episode.id,
        imageType = "Primary",
        width = 400,
        height = 225,
        quality = 80,
        mediaRepository = mediaRepository
    )
    val badge = playbackEpisodeBadge(episode)
    val label = playbackEpisodeCardTitle(episode)

    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp)
                .clip(PlaybackInfoEpisodeShape)
                .background(Color(0xFF2A2A2A))
        ) {
            if (!imageUrl.isNullOrBlank()) {
                JellyfinPosterImage(
                    context = context,
                    imageUrl = imageUrl,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            if (badge != null) {
                Text(
                    text = badge,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PlaybackInfoBadgeColor)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
    }
}

@Composable
private fun playbackFromSeasonLabel(item: BaseItemDto?): String {
    val seasonNumber = item?.parentIndexNumber
    if (seasonNumber != null) {
        return stringResource(R.string.player_info_from_season, seasonNumber)
    }
    val seasonName = item?.seasonName?.takeIf { it.isNotBlank() }
    return if (seasonName != null) {
        stringResource(R.string.player_info_from_season_named, seasonName)
    } else {
        stringResource(R.string.player_info_from_season_named, "")
    }
}

@Composable
private fun playbackEpisodeBadge(episode: BaseItemDto): String? {
    val runtimeSeconds = ticksToSeconds(episode.runTimeTicks) ?: return null
    val positionSeconds = ticksToSeconds(episode.userData?.playbackPositionTicks) ?: 0L
    val remainingSeconds = (runtimeSeconds - positionSeconds).coerceAtLeast(0L)
    val inProgress = positionSeconds > 0L &&
        episode.userData?.played != true &&
        remainingSeconds > 0L
    val shown = if (inProgress) remainingSeconds else runtimeSeconds
    val formatted = formatPlaybackDurationLabel(shown, compact = inProgress)
    return if (inProgress) {
        stringResource(R.string.player_info_remaining, formatted)
    } else {
        formatted
    }
}

@Composable
private fun formatPlaybackDurationLabel(totalSeconds: Long, compact: Boolean): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        stringResource(R.string.player_info_duration_hms, hours, minutes)
    } else if (compact) {
        stringResource(R.string.player_info_duration_ms_compact, minutes, seconds)
    } else {
        stringResource(R.string.player_info_duration_ms, minutes, seconds)
    }
}

private fun playbackEpisodeTitle(item: BaseItemDto?, fallback: String): String {
    if (item == null) return fallback
    if (!item.type.equals("Episode", ignoreCase = true)) {
        return item.name?.takeIf { it.isNotBlank() } ?: fallback
    }
    val episodeName = item.name?.takeIf { it.isNotBlank() } ?: fallback
    val season = item.parentIndexNumber
    val episode = item.indexNumber
    return if (season != null && episode != null) {
        "S${season}E$episode : $episodeName"
    } else {
        episodeName
    }
}

private fun playbackEpisodeCardTitle(episode: BaseItemDto): String {
    val name = episode.name?.takeIf { it.isNotBlank() }.orEmpty()
    val number = episode.indexNumber
    return if (number != null && name.isNotBlank()) {
        "$number.$name"
    } else {
        name.ifBlank { episode.id.orEmpty() }
    }
}

private fun formatPlaybackPremiereDate(premiereDate: String?): String? {
    val raw = premiereDate?.takeIf { it.isNotBlank() } ?: return null
    val datePart = raw.take(10)
    if (datePart.length == 10 && datePart[4] == '-' && datePart[7] == '-') {
        return "${datePart.substring(0, 4)}/${datePart.substring(5, 7)}/${datePart.substring(8, 10)}"
    }
    return raw
}

private fun ticksToSeconds(ticks: Long?): Long? {
    if (ticks == null || ticks <= 0L) return null
    return ticks / 10_000_000L
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChapterListSheet(
    chapters: List<ChapterMarker>,
    onDismiss: () -> Unit,
    onChapterSelected: (ChapterMarker) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF111111)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.player_chapters),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (chapters.isEmpty()) {
                Text(
                    text = stringResource(R.string.player_chapters_empty),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 14.dp, bottom = 16.dp)
                )
            } else {
                chapters.forEachIndexed { index, chapter ->
                    val label = chapter.label?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.player_chapters)
                    Text(
                        text = "${index + 1}.  $label    ${formatPlaybackTime(chapter.positionMs)}",
                        color = Color.White,
                        fontSize = 15.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChapterSelected(chapter) }
                            .padding(vertical = 12.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
