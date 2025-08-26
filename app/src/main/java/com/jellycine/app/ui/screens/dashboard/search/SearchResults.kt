package com.jellycine.app.ui.screens.dashboard.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.jellycine.app.ui.components.common.LazyImageLoader
import com.jellycine.app.util.image.rememberImageUrl
import com.jellycine.data.repository.getYearAndGenre
import com.jellycine.data.model.BaseItemDto

@Composable
fun SearchResultsView(
    uiState: SearchUiState,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Movies Section
        if (uiState.movieResults.isNotEmpty()) {
            item {
                Text(
                    text = "Movies",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(uiState.movieResults) { movie ->
                        SearchResultCard(
                            item = movie,
                            onItemClick = { onItemClick(movie) }
                        )
                    }
                }
            }
        }
        
        // TV Shows Section
        if (uiState.showResults.isNotEmpty()) {
            item {
                Text(
                    text = "Shows",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    items(uiState.showResults) { show ->
                        SearchResultCard(
                            item = show,
                            onItemClick = { onItemClick(show) }
                        )
                    }
                }
            }
        }
        
        // Episodes Section
        if (uiState.episodeResults.isNotEmpty()) {
            item {
                Text(
                    text = "Episodes",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
            
            items(uiState.episodeResults) { episode ->
                EpisodeResultCard(
                    item = episode,
                    onItemClick = { onItemClick(episode) }
                )
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    item: BaseItemDto,
    onItemClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onItemClick() }
    ) {
        // Poster
        Card(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(0.67f),
            colors = CardDefaults.cardColors(
                containerColor = Color.Gray.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            val imageUrl = rememberImageUrl(itemId = item.id, imageType = "Primary")
            LazyImageLoader(
                imageUrl = imageUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                cornerRadius = 12
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // Title
        Text(
            text = item.name ?: "Unknown Title",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )
        
        // Year and Genre
        Text(
            text = item.getYearAndGenre(),
            color = Color.Gray,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EpisodeResultCard(
    item: BaseItemDto,
    onItemClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Episode Thumbnail
        Card(
            modifier = Modifier.size(80.dp, 60.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Gray.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            val imageUrl = rememberImageUrl(itemId = item.id, imageType = "Primary")
            LazyImageLoader(
                imageUrl = imageUrl,
                contentDescription = item.name,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                cornerRadius = 8
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Episode Info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.name ?: "Unknown Episode",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = item.getYearAndGenre(),
                color = Color.Gray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LiveSearchResults(
    searchResults: List<BaseItemDto>,
    onItemClick: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        items(searchResults) { result ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onItemClick(result) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Poster/Thumbnail
                Card(
                    modifier = Modifier.size(60.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Gray.copy(alpha = 0.2f)
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    val imageUrl = rememberImageUrl(itemId = result.id, imageType = "Primary")
                    LazyImageLoader(
                        imageUrl = imageUrl,
                        contentDescription = result.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                        cornerRadius = 8
                    )
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = result.name ?: "Unknown Title",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = result.getYearAndGenre(),
                        color = Color.Gray,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}