package com.jellycine.app.ui.screens.detail

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Visibility
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
import androidx.compose.ui.unit.sp
import com.jellycine.app.util.image.JellyfinPosterImage
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
    var seasonImageUrl by remember(season.id) { mutableStateOf<String?>(null) }
    var imageLoadingFailed by remember(season.id) { mutableStateOf(false) }
    var isHovered by remember { mutableStateOf(false) }
    var showPreviewOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(season.id) {
        season.id?.let { seasonId ->
            seasonImageUrl = mediaRepository.getImageUrl(
                itemId = seasonId,
                imageType = "Primary",
                width = 200,
                height = 300,
                quality = 90
            ).first()
        }
    }

    // Auto-hide preview overlay after 3 seconds
    LaunchedEffect(showPreviewOverlay) {
        if (showPreviewOverlay) {
            delay(3000)
            showPreviewOverlay = false
        }
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .width(140.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        showPreviewOverlay = !showPreviewOverlay
                    }
                )
            },
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
                    if (seasonImageUrl != null && !imageLoadingFailed) {
                        JellyfinPosterImage(
                            context = context,
                            imageUrl = seasonImageUrl!!,
                            contentDescription = season.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onErrorStateChange = { hasError ->
                                imageLoadingFailed = hasError
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                            border = androidx.compose.foundation.BorderStroke(
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
    Column(
        modifier = modifier.width(140.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Poster skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2A2A2A))
        )

        // Title skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .height(14.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2A2A2A))
        )

        // Year skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF2A2A2A))
        )
    }
}
