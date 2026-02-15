package com.jellycine.app.ui.screens.detail

import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import com.jellycine.app.util.image.JellyfinPosterImage
import com.jellycine.app.ui.components.common.AnimatedCard
import com.jellycine.app.ui.components.common.ShimmerEffect
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeasonCard(
    season: BaseItemDto,
    mediaRepository: MediaRepository,
    onClick: () -> Unit = {},
    onPreviewClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var seasonImageCandidates by remember(season.id, season.seriesId) { mutableStateOf<List<String>>(emptyList()) }
    var seasonImageIndex by remember(season.id, season.seriesId) { mutableIntStateOf(0) }
    var hasImageLoadError by remember(season.id, season.seriesId) { mutableStateOf(false) }
    var showPreviewOverlay by remember { mutableStateOf(false) }
    val seasonImageUrl = seasonImageCandidates.getOrNull(seasonImageIndex)

    LaunchedEffect(season.id, season.seriesId) {
        hasImageLoadError = false
        seasonImageIndex = 0
        season.id?.let { seasonId ->
            seasonImageCandidates = listOfNotNull(
                mediaRepository.getImageUrl(
                    itemId = seasonId,
                    imageType = "Primary",
                    width = 200,
                    height = 300,
                    quality = 90
                ).first(),
                season.seriesId?.let { seriesId ->
                    mediaRepository.getImageUrl(
                        itemId = seriesId,
                        imageType = "Primary",
                        width = 200,
                        height = 300,
                        quality = 90
                    ).first()
                }
            ).distinct()
        }
    }

    // Auto-hide preview overlay after 3 seconds
    LaunchedEffect(showPreviewOverlay) {
        if (showPreviewOverlay) {
            delay(3000)
            showPreviewOverlay = false
        }
    }

    AnimatedCard(
        onClick = {
            onClick()
        },
        modifier = modifier.width(140.dp),
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
                        // Show fallback when URL is null or loading failed
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

            // Preview overlay
            androidx.compose.animation.AnimatedVisibility(
                visible = showPreviewOverlay,
                enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                    initialScale = 0.8f,
                    animationSpec = tween(300)
                ),
                exit = fadeOut(animationSpec = tween(200)) + scaleOut(
                    targetScale = 0.8f,
                    animationSpec = tween(200)
                )
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    color = Color.Black.copy(alpha = 0.9f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Preview button
                        Button(
                            onClick = onPreviewClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF00BCD4)
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Visibility,
                                contentDescription = "Preview",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Preview",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // View all button
                        OutlinedButton(
                            onClick = onClick,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = BorderStroke(
                                1.dp,
                                Color.White.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = "View All",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "View All",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
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
