package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.shared.preferences.Preferences
import com.jellycine.shared.util.image.JellyfinPosterImage
import com.jellycine.shared.util.image.imageTagFor
import com.jellycine.shared.ui.components.common.AnimatedCard
import com.jellycine.shared.ui.components.common.ShimmerEffect
import com.jellycine.app.ui.components.common.SeerrTopBadges
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.SeerrRequestState
import com.jellycine.data.model.SeerrSeasonRequestOption
import com.jellycine.data.model.seerTitleParams
import com.jellycine.data.repository.AuthRepositoryProvider
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.SeerrRepository
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonCard(
    season: BaseItemDto,
    mediaRepository: MediaRepository,
    onClick: () -> Unit = {},
    onRequestClick: (() -> Unit)? = null,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val preferences = remember { Preferences(context) }
    val authRepository = remember { AuthRepositoryProvider.getInstance(context) }
    val currentServerType by authRepository.getServerType().collectAsState(initial = null)
    val posterEnhancersEnabled by preferences.PosterEnhancersEnabled()
        .collectAsState(initial = preferences.isPosterEnhancersEnabled())
    val disableImageEnhancers = currentServerType.equals("EMBY", ignoreCase = true) && posterEnhancersEnabled
    var seasonImageCandidates by remember(season.id, season.seriesId, season.imageUrl) { mutableStateOf<List<String>>(emptyList()) }
    var seasonImageIndex by remember(season.id, season.seriesId, season.imageUrl) { mutableIntStateOf(0) }
    var hasImageLoadError by remember(season.id, season.seriesId, season.imageUrl) { mutableStateOf(false) }
    val seasonImageUrl = seasonImageCandidates.getOrNull(seasonImageIndex)
    val isAvailableLocally = !season.id.isNullOrBlank()
    val seasonRequestState = season.seerrRequestState ?: SeerrRequestState.NONE
    val canRequestSeason = !isAvailableLocally &&
        seasonRequestState == SeerrRequestState.NONE &&
        onRequestClick != null

    LaunchedEffect(season.id, season.seriesId, season.imageUrl, disableImageEnhancers) {
        hasImageLoadError = false
        seasonImageIndex = 0
        season.imageUrl?.takeIf { it.isNotBlank() }?.let { imageUrl ->
            seasonImageCandidates = listOf(imageUrl)
            return@LaunchedEffect
        }
        seasonImageCandidates = listOfNotNull(
            season.id?.let { seasonId ->
                mediaRepository.getImageUrl(
                    itemId = seasonId,
                    imageType = "Primary",
                    width = 200,
                    height = 300,
                    quality = 90,
                    enableImageEnhancers = !disableImageEnhancers,
                    imageTag = season.imageTagFor(
                        imageType = "Primary",
                        targetItemId = seasonId
                    )
                ).first()
            },
            season.seriesId?.let { seriesId ->
                mediaRepository.getImageUrl(
                    itemId = seriesId,
                    imageType = "Primary",
                    width = 200,
                    height = 300,
                    quality = 90,
                    enableImageEnhancers = !disableImageEnhancers,
                    imageTag = season.imageTagFor(
                        imageType = "Primary",
                        targetItemId = seriesId
                    )
                ).first()
            }
        ).distinct()
    }

    AnimatedCard(
        onClick = {
            if (!isLoading) {
                if (isAvailableLocally) {
                    onClick()
                } else if (canRequestSeason) {
                    onRequestClick?.invoke()
                }
            }
        },
        modifier = modifier.width(140.dp),
        enabled = isAvailableLocally || canRequestSeason,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Season poster
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    if (seasonImageUrl != null && !hasImageLoadError) {
                        JellyfinPosterImage(
                            context = context,
                            imageUrl = seasonImageUrl,
                            contentDescription = season.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onErrorStateChange = { hasError ->
                                if (!hasError) {
                                    hasImageLoadError = false
                                } else if (seasonImageIndex < seasonImageCandidates.lastIndex) {
                                    seasonImageIndex += 1
                                } else {
                                    hasImageLoadError = true
                                }
                            }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF2A2A2A)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = season.name?.take(1) ?: "S",
                                fontSize = 48.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                    }

                    // Gradient overlay for better text readability
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.3f)
                                    ),
                                    startY = 0f,
                                    endY = Float.POSITIVE_INFINITY
                                )
                            )
                    )

                    // Episode count badge
                    season.childCount?.let { episodeCount ->
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(12.dp),
                            color = Color.Black.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "$episodeCount episodes",
                                fontSize = 10.sp,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (!isAvailableLocally) {
                        SeerrTopBadges(
                            requestState = seasonRequestState,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }

                    if (isLoading) {
                        ShimmerEffect(
                            modifier = Modifier.fillMaxSize(),
                            cornerRadius = 12f
                        )
                    }
                }

                // Season info
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = season.name ?: "Unknown Season",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )

                    season.productionYear?.let { year ->
                        Text(
                            text = year.toString(),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SeasonsSection(
    series: BaseItemDto,
    seriesId: String,
    mediaRepository: MediaRepository,
    seerrRepository: SeerrRepository,
    activeServerId: String?,
    refreshKey: Int,
    loadingSeasonNumber: Int? = null,
    onSeasonRequest: (Int) -> Unit,
    onSeasonClick: (String, String, String?) -> Unit = { _, _, _ -> }
) {
    var localSeasons by remember(seriesId) { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var seerrSeasons by remember(seriesId) { mutableStateOf<List<SeerrSeasonRequestOption>>(emptyList()) }
    var isLoadingLocal by remember(seriesId) { mutableStateOf(true) }
    var isLoadingSeerr by remember(seriesId) { mutableStateOf(false) }
    val seerrParams = remember(series.id, series.providerIds, series.type) {
        series.seerTitleParams()?.takeIf { (mediaType, _) -> mediaType == "tv" }
    }
    val localSeasonNumbers = remember(localSeasons) {
        localSeasons.mapNotNull { season -> season.indexNumber }.toSet()
    }
    val seasons = remember(localSeasons, seerrSeasons) {
        (
            localSeasons +
                seerrSeasons
                    .filter { season -> season.seasonNumber !in localSeasonNumbers }
                    .map { season ->
                        BaseItemDto(
                            name = "Season ${season.seasonNumber}",
                            type = "Season",
                            indexNumber = season.seasonNumber,
                            childCount = season.episodeCount,
                            seriesId = seriesId,
                            imageUrl = season.posterUrl,
                            seerrRequestState = season.requestState
                        )
                    }
            ).sortedBy { season -> season.indexNumber ?: 0 }
    }

    LaunchedEffect(seriesId) {
        isLoadingLocal = true
        localSeasons = mediaRepository.getSeasons(seriesId)
            .getOrDefault(emptyList())
            .sortedBy { season -> season.indexNumber ?: 0 }
        isLoadingLocal = false
    }

    LaunchedEffect(activeServerId, seerrParams, refreshKey) {
        val scopeId = activeServerId?.takeIf { it.isNotBlank() }
        val (_, tmdbId) = seerrParams ?: run {
            seerrSeasons = emptyList()
            isLoadingSeerr = false
            return@LaunchedEffect
        }
        if (scopeId == null) {
            seerrSeasons = emptyList()
            isLoadingSeerr = false
            return@LaunchedEffect
        }

        isLoadingSeerr = true
        seerrSeasons = seerrRepository.getTitleSeasons(
            scopeId = scopeId,
            tmdbId = tmdbId
        ).getOrDefault(emptyList())
        isLoadingSeerr = false
    }

    Column(
        modifier = Modifier.padding(top = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Seasons",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        if (seasons.isEmpty() && (isLoadingLocal || isLoadingSeerr)) {
            SeasonSkeletonRow()
        } else if (seasons.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                items(seasons) { season ->
                    val seasonNumber = season.indexNumber ?: return@items
                    SeasonCard(
                        season = season,
                        mediaRepository = mediaRepository,
                        onClick = {
                            season.id?.let { seasonId ->
                                onSeasonClick(seriesId, seasonId, season.name)
                            }
                        },
                        onRequestClick = if (seerrParams != null) {
                            { onSeasonRequest(seasonNumber) }
                        } else {
                            null
                        },
                        isLoading = seasonNumber == loadingSeasonNumber
                    )
                }
            }
        }
    }
}

@Composable
private fun SeasonSkeletonRow() {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        items(3) {
            SeasonCardSkeleton()
        }
    }
}

@Composable
fun SeasonCardSkeleton(
    modifier: Modifier = Modifier
) {
    AnimatedCard(
        modifier = modifier.width(140.dp),
        enabled = false,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Poster skeleton using polished ShimmerEffect
            ShimmerEffect(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp),
                cornerRadius = 12f
            )

            // Title skeleton
            ShimmerEffect(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(14.dp),
                cornerRadius = 4f
            )

            // Year skeleton
            ShimmerEffect(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(12.dp),
                cornerRadius = 4f
            )
        }
    }
}