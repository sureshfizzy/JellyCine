package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
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
import com.jellycine.app.util.image.JellyfinPosterImage
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.detail.CodecUtils
import kotlinx.coroutines.flow.first

@Composable
fun SeasonDetailScreen(
    seriesId: String,
    seasonId: String,
    seasonName: String? = null,
    onBackPressed: () -> Unit = {},
    onEpisodeClick: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    
    var episodes by remember { mutableStateOf<List<BaseItemDto>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var heroImageCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var heroImageIndex by remember { mutableIntStateOf(0) }
    var logoImageCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var logoImageIndex by remember { mutableIntStateOf(0) }

    // Load episodes for this season
    LaunchedEffect(seasonId) {
        isLoading = true
        try {
            val result = mediaRepository.getEpisodes(
                seriesId = seriesId,
                seasonId = seasonId,
                limit = 100
            )
            result.fold(
                onSuccess = { episodeList ->
                    episodes = episodeList.sortedBy { it.indexNumber ?: 0 }
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

    // Prepare hero image candidates in fallback order
    LaunchedEffect(seasonId, seriesId) {
        try {
            heroImageCandidates = listOfNotNull(
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
            ).distinct()
            heroImageIndex = 0
        } catch (e: Exception) {
            heroImageCandidates = emptyList()
            heroImageIndex = 0
        }
    }

    // Prepare logo candidates (season first, then series)
    LaunchedEffect(seasonId, seriesId) {
        try {
            logoImageCandidates = listOfNotNull(
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
            ).distinct()
            logoImageIndex = 0
        } catch (e: Exception) {
            logoImageCandidates = emptyList()
            logoImageIndex = 0
        }
    }

    val currentHeroImageUrl = heroImageCandidates.getOrNull(heroImageIndex)
    val currentLogoImageUrl = logoImageCandidates.getOrNull(logoImageIndex)

    when {
        error != null -> {
            LaunchedEffect(Unit) {
                onBackPressed()
            }
        }
        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(380.dp)
                        ) {
                            if (currentHeroImageUrl != null) {
                                JellyfinPosterImage(
                                    context = context,
                                    imageUrl = currentHeroImageUrl,
                                    contentDescription = seasonName,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    onErrorStateChange = { hasError ->
                                        if (hasError) {
                                            if (heroImageIndex <= heroImageCandidates.lastIndex) {
                                                heroImageIndex += 1
                                            }
                                        }
                                    }
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color(0xFF20202A))
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.0f to Color.Transparent,
                                                0.62f to Color.Transparent,
                                                0.86f to Color.Black.copy(alpha = 0.58f),
                                                1.0f to Color.Black
                                            )
                                        )
                                    )
                            )

                            IconButton(
                                onClick = onBackPressed,
                                modifier = Modifier
                                    .statusBarsPadding()
                                    .padding(12.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
                                    .size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset(y = (-22).dp)
                                .padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (currentLogoImageUrl != null) {
                                JellyfinPosterImage(
                                    context = context,
                                    imageUrl = currentLogoImageUrl,
                                    contentDescription = seasonName,
                                    modifier = Modifier
                                        .fillMaxWidth(0.86f)
                                        .height(58.dp),
                                    contentScale = ContentScale.Fit,
                                    alignment = Alignment.CenterStart,
                                    onErrorStateChange = { hasError ->
                                        if (hasError) {
                                            if (logoImageIndex <= logoImageCandidates.lastIndex) {
                                                logoImageIndex += 1
                                            }
                                        }
                                    }
                                )
                            } else {
                                Text(
                                    text = seasonName ?: "Season",
                                    fontSize = 17.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
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

                            Button(
                                onClick = {
                                    episodes.firstOrNull()?.id?.let { firstEpisodeId ->
                                        onEpisodeClick(firstEpisodeId)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
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
    val context = LocalContext.current
    var episodeImageUrl by remember(episode.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(episode.id) {
        episode.id?.let { episodeId ->
            episodeImageUrl = mediaRepository.getImageUrl(
                itemId = episodeId,
                imageType = "Primary",
                width = 640,
                height = 360,
                quality = 90,
                enableImageEnhancers = false
            ).first() ?: episode.seriesId?.let { series ->
                mediaRepository.getBackdropImageUrl(
                    itemId = series,
                    imageIndex = 0,
                    width = 640,
                    height = 360,
                    quality = 90,
                    enableImageEnhancers = false
                ).first()
            }
        }
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
                        context = context,
                        imageUrl = episodeImageUrl,
                        contentDescription = episode.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
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
