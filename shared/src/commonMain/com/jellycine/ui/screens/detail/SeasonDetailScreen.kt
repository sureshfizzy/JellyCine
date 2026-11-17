package com.jellycine.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.ui.components.common.BackButton
import com.jellycine.ui.components.common.CompactTopLogo
import com.jellycine.ui.components.common.DetailBackdropHero
import com.jellycine.ui.components.common.compactHeaderLogo
import com.jellycine.ui.components.common.containerHeightDp
import com.jellycine.ui.components.common.containerWidthDp
import com.jellycine.ui.components.common.isTabletDetailLayout
import com.jellycine.ui.components.common.rememberCompactProgress
import com.jellycine.playback.UserDataRefreshSignals
import com.jellycine.ui.components.common.WatchedIndicatorBadge
import com.jellycine.util.image.JellyfinPosterImage
import com.jellycine.util.image.getBackdrop
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.UserItemDataDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.detail.CodecUtils
import com.jellycine.playback.UserDataRefreshEvent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun SeasonDetailScreen(
    seriesId: String,
    seasonId: String,
    mediaRepository: MediaRepository,
    seasonName: String? = null,
    seasonDownloadAction: (@Composable (seasonId: String, episodes: List<BaseItemDto>) -> Unit)? = null,
    initialHeroImageUrl: String? = null,
    initialLogoImageUrl: String? = null,
    onBackPressed: () -> Unit = {},
    onEpisodeClick: (String) -> Unit = {},
    onSeriesOverviewClick: (String) -> Unit = {}
) {
    val screenWidthDp = containerWidthDp()
    val screenHeightDp = containerHeightDp()
    val useTabletBackdropLayout = isTabletDetailLayout(
        screenWidthDp = screenWidthDp,
        screenHeightDp = screenHeightDp
    )
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var episodes by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var seriesTitle by remember(seriesId) { mutableStateOf<String?>(null) }
    val initialHeroImageCandidates = remember(seasonId, seriesId, initialHeroImageUrl) {
        listOfNotNull(initialHeroImageUrl?.takeIf { it.isNotBlank() }).distinct()
    }
    var heroImageCandidates by remember(seasonId, seriesId, initialHeroImageUrl) {
        mutableStateOf(initialHeroImageCandidates)
    }
    var heroImageIndex by remember(seasonId, seriesId) { mutableIntStateOf(0) }
    val initialLogoImageCandidates = remember(seasonId, seriesId, initialLogoImageUrl) {
        listOfNotNull(initialLogoImageUrl?.takeIf { it.isNotBlank() }).distinct()
    }
    var logoImageCandidates by remember(seasonId, seriesId, initialLogoImageUrl) {
        mutableStateOf(initialLogoImageCandidates)
    }
    var logoImageIndex by remember(seasonId, seriesId) { mutableIntStateOf(0) }
    var logoCandidateLookup by remember(seasonId, seriesId) { mutableStateOf(true) }
    var logoLoadError by remember(seasonId, seriesId) { mutableStateOf(false) }
    val userDataRefreshEvent by UserDataRefreshSignals.refreshEvent.collectAsState()

    // Load episodes for this season
    LaunchedEffect(seasonId, userDataRefreshEvent) {
        if (episodes.isEmpty()) {
            isLoading = true
        }
        episodes = episodes.withUserDataRefresh(userDataRefreshEvent)
        try {
            val result = mediaRepository.getEpisodes(
                seriesId = seriesId,
                seasonId = seasonId
            )
            result.fold(
                onSuccess = { episodeList ->
                    episodes = episodeList
                        .sortedBy { it.indexNumber ?: 0 }
                        .withUserDataRefresh(userDataRefreshEvent)
                    isLoading = false
                },
                onFailure = { exception ->
                    error = exception.message
                    isLoading = false
                }
            )
        } catch (e: Exception) {
            error = e.message
            isLoading = false
        }
    }

    LaunchedEffect(seriesId) {
        seriesTitle = null
        try {
            mediaRepository.getItemById(seriesId).fold(
                onSuccess = { seriesItem ->
                    seriesTitle = seriesItem.name?.takeIf { it.isNotBlank() }
                },
                onFailure = {}
            )
        } catch (_: Exception) {
            seriesTitle = null
        }
    }

    // Prepare hero image candidates in fallback order
    LaunchedEffect(seasonId, seriesId, initialHeroImageUrl) {
        try {
            val nextHeroImageCandidates = (
                listOfNotNull(
                    mediaRepository.getBackdropImageUrl(
                        itemId = seasonId,
                        imageIndex = 0,
                        width = 1200,
                        height = 675,
                        quality = 92
                    ).first(),
                    mediaRepository.getBackdropImageUrl(
                        itemId = seriesId,
                        imageIndex = 0,
                        width = 1200,
                        height = 675,
                        quality = 92
                    ).first(),
                    mediaRepository.getImageUrl(
                        itemId = seasonId,
                        imageType = "Primary",
                        width = 900,
                        height = 1200,
                        quality = 92
                    ).first(),
                    mediaRepository.getImageUrl(
                        itemId = seriesId,
                        imageType = "Primary",
                        width = 900,
                        height = 1200,
                        quality = 92
                    ).first()
                ) + initialHeroImageCandidates
            ).distinct()
            heroImageCandidates = nextHeroImageCandidates
            heroImageIndex = 0
        } catch (e: Exception) {
            heroImageCandidates = initialHeroImageCandidates
            heroImageIndex = 0
        }
    }

    // Prepare logo candidates (season first, then series)
    LaunchedEffect(seasonId, seriesId, initialLogoImageUrl) {
        logoCandidateLookup = true
        logoLoadError = false
        try {
            logoImageCandidates = (
                listOfNotNull(
                    mediaRepository.getImageUrl(
                        itemId = seasonId,
                        imageType = "Logo",
                        width = 1200,
                        quality = 95
                    ).first(),
                    mediaRepository.getImageUrl(
                        itemId = seriesId,
                        imageType = "Logo",
                        width = 1200,
                        quality = 95
                    ).first()
                ) + initialLogoImageCandidates
            ).distinct()
            logoImageIndex = 0
        } catch (e: Exception) {
            logoImageCandidates = initialLogoImageCandidates
            logoImageIndex = 0
        } finally {
            logoCandidateLookup = false
        }
    }

    val currentHeroImageUrl = heroImageCandidates.getOrNull(heroImageIndex)
        ?: initialHeroImageCandidates.firstOrNull()
    val currentLogoImageUrl = logoImageCandidates.getOrNull(logoImageIndex)
        ?: initialLogoImageCandidates.firstOrNull()
    val showLogoImage = currentLogoImageUrl != null && !logoLoadError
    val reserveLogoSpace = showLogoImage || logoCandidateLookup
    val fallbackHeaderTitle = seriesTitle
        ?: episodes.firstOrNull()?.seriesName?.takeIf { it.isNotBlank() }
        ?: seasonName
        ?: "Season"
    val seriesOverviewModifier = Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null
    ) {
        onSeriesOverviewClick(seriesId)
    }
    val heroHeight = if (useTabletBackdropLayout) 430.dp else 380.dp
    val logoCompactProgress = rememberCompactProgress(
        state = listState,
        compactDistance = if (useTabletBackdropLayout) 260.dp else 220.dp
    )

    when {
        error != null -> {
            LaunchedEffect(Unit) {
                onBackPressed()
            }
        }
        else -> {
            Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        DetailBackdropHero(
                            imageUrl = currentHeroImageUrl,
                            contentDescription = seasonName,
                            heroHeight = heroHeight,
                            bottomFadeHeight = 120.dp,
                            onErrorStateChange = { hasError ->
                                if (hasError && heroImageIndex < heroImageCandidates.lastIndex) {
                                    heroImageIndex += 1
                                }
                            }
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (-22).dp)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.86f)
                                    .height(58.dp)
                                    .then(seriesOverviewModifier),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (showLogoImage) {
                                    JellyfinPosterImage(
                                        imageUrl = currentLogoImageUrl,
                                        contentDescription = seasonName,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .compactHeaderLogo(logoCompactProgress),
                                        contentScale = ContentScale.Fit,
                                        alignment = Alignment.CenterStart,
                                        onErrorStateChange = { hasError ->
                                            if (hasError) {
                                                if (logoImageIndex < logoImageCandidates.lastIndex) {
                                                    logoImageIndex += 1
                                                } else {
                                                    logoLoadError = true
                                                }
                                            } else {
                                                logoLoadError = false
                                            }
                                        }
                                    )
                                } else if (!reserveLogoSpace) {
                                    Text(
                                        text = fallbackHeaderTitle,
                                        fontSize = 17.sp,
                                        lineHeight = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Text(
                                text = seasonName ?: "Season",
                                fontSize = 15.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.96f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = "${episodes.size} episodes",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.86f)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        episodes.firstOrNull()?.id?.let { firstEpisodeId ->
                                            onEpisodeClick(firstEpisodeId)
                                        }
                                    },
                                    enabled = episodes.isNotEmpty(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp),
                                    shape = RoundedCornerShape(23.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Play season",
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Play",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                seasonDownloadAction?.let { downloadAction ->
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(46.dp)
                                    ) {
                                        downloadAction(seasonId, episodes)
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Episodes",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 10.dp)
                    )
                }

                if (isLoading) {
                    items(4) {
                        EpisodeCardSkeleton(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }
                } else {
                    items(episodes) { episode ->
                        EpisodeListItem(
                            episode = episode,
                            mediaRepository = mediaRepository,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            onClick = {
                                episode.id?.let { episodeId ->
                                    onEpisodeClick(episodeId)
                                }
                            }
                        )
                    }
                }
            }

            BackButton(
                onClick = onBackPressed,
                modifier = Modifier.align(Alignment.TopStart)
            )

            currentLogoImageUrl?.takeIf { showLogoImage }?.let { logoUrl ->
                CompactTopLogo(
                    imageUrl = logoUrl,
                    contentDescription = seasonName,
                    progress = logoCompactProgress,
                    isTablet = useTabletBackdropLayout,
                    onClick = {
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 48.dp)
                )
            }
            }
        }
    }
}

private fun List<BaseItemDto>.withUserDataRefresh(
    event: UserDataRefreshEvent?
): List<BaseItemDto> {
    val itemId = event?.itemId?.takeIf { it.isNotBlank() } ?: return this
    val played = event.played ?: return this

    return map { episode ->
        if (episode.id != itemId) {
            episode
        } else {
            episode.copy(
                userData = (episode.userData ?: UserItemDataDto(itemId = itemId)).copy(
                    played = played,
                    playbackPositionTicks = 0L
                )
            )
        }
    }
}

@Composable
private fun EpisodeListItem(
    episode: BaseItemDto,
    mediaRepository: MediaRepository,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    var episodeImageUrl by remember(episode.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(episode.id) {
        episodeImageUrl = getBackdrop(
            episode = episode,
            mediaRepository = mediaRepository,
            width = 1280,
            height = 720,
            quality = 95
        )
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .width(130.dp)
                        .height(74.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E1E1E))
                ) {
                    JellyfinPosterImage(
                        imageUrl = episodeImageUrl,
                        contentDescription = episode.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    if (episode.userData?.played == true) {
                        WatchedIndicatorBadge(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(5.dp)
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = buildString {
                            episode.indexNumber?.let { append("$it. ") }
                            append(episode.name ?: "Unknown Episode")
                        },
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    episode.runTimeTicks?.let { ticks ->
                        Text(
                            text = CodecUtils.formatRuntime(ticks),
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.82f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Text(
                    text = overview,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}