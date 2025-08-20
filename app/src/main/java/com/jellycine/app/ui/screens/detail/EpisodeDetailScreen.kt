package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.material3.CircularProgressIndicator
import com.jellycine.app.util.image.JellyfinPosterImage
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.MediaRepositoryProvider
import com.jellycine.detail.CodecUtils
import kotlinx.coroutines.flow.first
import com.jellycine.app.ui.components.common.CastSection
import com.jellycine.app.ui.components.common.CodecInfoSection
import com.jellycine.app.ui.components.common.ModernFileInfoRow
import com.jellycine.app.ui.components.common.OverviewSection
import com.jellycine.app.ui.components.common.TechnicalInfoSection
import com.jellycine.app.ui.components.common.ScreenWrapper
import com.jellycine.app.ui.components.common.AnimatedCard
import com.jellycine.app.ui.screens.dashboard.ShimmerEffect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailScreen(
    episodeId: String,
    onBackPressed: () -> Unit = {},
    onPlayClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    
    var episode by remember { mutableStateOf<BaseItemDto?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var episodeImageUrl by remember { mutableStateOf<String?>(null) }

    // Load episode details
    LaunchedEffect(episodeId) {
        isLoading = true
        try {
            val result = mediaRepository.getItemById(episodeId)
            result.fold(
                onSuccess = { episodeDetails ->
                    episode = episodeDetails
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

    // Load episode image
    LaunchedEffect(episode) {
        episode?.let { ep ->
            ep.id?.let { id ->
                try {
                    episodeImageUrl = mediaRepository.getImageUrl(
                        itemId = id,
                        imageType = "Primary",
                        width = 800,
                        height = 450,
                        quality = 90
                    ).first()
                } catch (e: Exception) {
                }
            }
        }
    }

    when {
        error != null -> {
            LaunchedEffect(Unit) {
                onBackPressed()
            }
        }
        episode != null -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // Top App Bar
                TopAppBar(
                    title = {
                        Text(
                            text = episode?.name ?: "Episode",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackPressed) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black
                    )
                )

                // Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(12.dp))
                    ) {
                        episodeImageUrl?.let { imageUrl ->
                            JellyfinPosterImage(
                                context = context,
                                imageUrl = imageUrl,
                                contentDescription = episode?.name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } ?: run {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF2A2A2A)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Movie,
                                    contentDescription = "Episode",
                                    tint = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }

                    // Series and season info
                    episode?.seriesName?.let { seriesName ->
                        val displayText = buildString {
                            append(seriesName)
                            episode?.productionYear?.let { year ->
                                append(" ($year)")
                            }
                        }
                        Text(
                            text = displayText,
                            fontSize = 16.sp,
                            color = Color(0xFF00BCD4),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Episode title and number
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        episode?.parentIndexNumber?.let { seasonNumber ->
                            Text(
                                text = "Season $seasonNumber",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }

                        episode?.indexNumber?.let { episodeNumber ->
                            Text(
                                text = "Episode $episodeNumber",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    // Episode title
                    Text(
                        text = episode?.name ?: "Unknown Episode",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    // Episode metadata
                    episode?.runTimeTicks?.let { ticks ->
                        Text(
                            text = CodecUtils.formatRuntime(ticks),
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    // Codec Information Section
                    episode?.mediaSources?.firstOrNull()?.mediaStreams?.let { streams ->
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Codecs Info",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            CodecInfoSection(mediaStreams = streams)
                        }
                    }

                    // Technical Information Section
                    episode?.let { ep ->
                        TechnicalInfoSection(
                            item = ep,
                            onPlayClick = onPlayClick,
                            onDownloadClick = { /* TODO: Download */ }
                        )
                    }

                    // Episode description
                    episode?.overview?.let { overview ->
                        OverviewSection(overview = overview)
                    }

                    // Cast Section
                    episode?.let { ep ->
                        CastSection(
                            item = ep,
                            mediaRepository = mediaRepository
                        )
                    }
                }
            }
        }
        else -> {
            EpisodeDetailSkeleton(onBackPressed = onBackPressed)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeDetailSkeleton(
    onBackPressed: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        TopAppBar(
            title = {
                ShimmerEffect(
                    modifier = Modifier
                        .width(150.dp)
                        .height(20.dp),
                    cornerRadius = 4f
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackPressed) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Black,
                titleContentColor = Color.White,
                navigationIconContentColor = Color.White
            )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Episode thumbnail skeleton
            ShimmerEffect(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                cornerRadius = 12f
            )

            // Series name skeleton
            ShimmerEffect(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(16.dp),
                cornerRadius = 4f
            )

            // Season and Episode number skeleton
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShimmerEffect(
                    modifier = Modifier
                        .width(80.dp)
                        .height(14.dp),
                    cornerRadius = 4f
                )
                ShimmerEffect(
                    modifier = Modifier
                        .width(90.dp)
                        .height(14.dp),
                    cornerRadius = 4f
                )
            }

            // Episode title skeleton
            ShimmerEffect(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(24.dp),
                cornerRadius = 4f
            )

            // Runtime skeleton
            ShimmerEffect(
                modifier = Modifier
                    .width(80.dp)
                    .height(14.dp),
                cornerRadius = 4f
            )

            // Play button skeleton
            ShimmerEffect(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                cornerRadius = 25f
            )

            // Codecs Info section skeleton
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ShimmerEffect(
                    modifier = Modifier
                        .width(120.dp)
                        .height(18.dp),
                    cornerRadius = 4f
                )
                repeat(2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ShimmerEffect(
                            modifier = Modifier
                                .width(100.dp)
                                .height(16.dp),
                            cornerRadius = 4f
                        )
                        ShimmerEffect(
                            modifier = Modifier
                                .width(120.dp)
                                .height(16.dp),
                            cornerRadius = 4f
                        )
                    }
                }
            }

            // Technical Information section skeleton
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ShimmerEffect(
                    modifier = Modifier
                        .width(160.dp)
                        .height(18.dp),
                    cornerRadius = 4f
                )
                repeat(3) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        ShimmerEffect(
                            modifier = Modifier
                                .width(90.dp)
                                .height(16.dp),
                            cornerRadius = 4f
                        )
                        ShimmerEffect(
                            modifier = Modifier
                                .width(110.dp)
                                .height(16.dp),
                            cornerRadius = 4f
                        )
                    }
                }
            }
        }
    }
}

