package com.jellycine.app.feature.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
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
import com.jellycine.app.util.JellyfinPosterImage
import com.jellycine.data.model.BaseItemDto
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    item: BaseItemDto,
    onBackPressed: () -> Unit = {},
    onPlayClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val mediaRepository = remember { com.jellycine.data.repository.MediaRepositoryProvider.getInstance(context) }
    
    var backdropImageUrl by remember { mutableStateOf<String?>(null) }
    var posterImageUrl by remember { mutableStateOf<String?>(null) }
    var isFavorite by remember { mutableStateOf(item.userData?.isFavorite == true) }
    
    // Load images
    LaunchedEffect(item.id) {
        val itemId = item.id
        if (itemId != null) {
            backdropImageUrl = mediaRepository.getBackdropImageUrl(
                itemId = itemId,
                width = 1200,
                height = 675,
                quality = 95
            ).first()
            
            posterImageUrl = mediaRepository.getImageUrl(
                itemId = itemId,
                width = 400,
                height = 600,
                quality = 95
            ).first()
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // Hero Section with Backdrop
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
            ) {
                // Backdrop Image
                JellyfinPosterImage(
                    imageUrl = backdropImageUrl,
                    contentDescription = item.name,
                    modifier = Modifier.fillMaxSize(),
                    context = context
                )
                
                // Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.3f),
                                    Color.Black.copy(alpha = 0.7f),
                                    Color.Black
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        )
                )
                
                // Top Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBackPressed,
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    
                    IconButton(
                        onClick = { isFavorite = !isFavorite },
                        modifier = Modifier
                            .background(
                                Color.Black.copy(alpha = 0.5f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (isFavorite) Color.Red else Color.White
                        )
                    }
                }
                
                // Content Info at Bottom
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Poster
                    JellyfinPosterImage(
                        imageUrl = posterImageUrl,
                        contentDescription = item.name,
                        modifier = Modifier
                            .width(120.dp)
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        context = context
                    )
                    
                    // Info
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = item.name ?: "Unknown",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Text(
                            text = "Released: ${item.productionYear ?: "Unknown"}",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        
                        // Rating
                        item.communityRating?.let { rating ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Star,
                                    contentDescription = "Rating",
                                    tint = Color.Yellow,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = String.format("%.1f/10", rating),
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
            
            // Content Details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Play Button
                Button(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.Red
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Play",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
                
                // Storyline
                item.overview?.let { overview ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Storyline",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = overview,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.8f),
                            lineHeight = 20.sp
                        )
                    }
                }
                
                // Genres
                item.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(genres) { genre ->
                                Surface(
                                    color = Color.White.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = genre,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
