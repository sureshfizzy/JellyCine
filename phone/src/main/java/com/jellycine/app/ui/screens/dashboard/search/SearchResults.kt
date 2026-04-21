package com.jellycine.app.ui.screens.dashboard.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.R
import com.jellycine.app.ui.components.common.SeerrTopBadges
import com.jellycine.shared.ui.components.common.ShimmerEffect
import com.jellycine.shared.ui.components.common.LazyImageLoader
import com.jellycine.shared.ui.components.common.episodeDisplaySubtitle
import com.jellycine.shared.ui.components.common.preferredDisplayTitle
import com.jellycine.app.ui.screens.dashboard.PosterSkeleton
import com.jellycine.app.ui.screens.dashboard.SectionTitleSkeleton
import com.jellycine.shared.util.image.rememberImageUrl
import com.jellycine.data.model.SeerrRecommendationTitle
import com.jellycine.data.repository.getYearAndGenre
import com.jellycine.data.model.BaseItemDto
import coil3.compose.AsyncImage
import java.util.Locale

@Composable
fun SearchResultsViewSkeleton(
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        searchPosterSectionSkeleton(titleWidth = 90.dp)
        searchPosterSectionSkeleton(titleWidth = 100.dp)

        item {
            SectionTitleSkeleton(
                modifier = Modifier.padding(bottom = 12.dp),
                width = 120.dp
            )
        }

        items(3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShimmerEffect(
                    modifier = Modifier.size(80.dp, 60.dp),
                    cornerRadius = 8f
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    ShimmerEffect(
                        modifier = Modifier
                            .fillMaxWidth(0.75f)
                            .height(16.dp),
                        cornerRadius = 4f
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    ShimmerEffect(
                        modifier = Modifier
                            .fillMaxWidth(0.45f)
                            .height(12.dp),
                        cornerRadius = 4f
                    )
                }
            }
        }
    }
}

private fun LazyListScope.searchPosterSectionSkeleton(titleWidth: Dp) {
    item {
        SectionTitleSkeleton(
            modifier = Modifier.padding(bottom = 12.dp),
            width = titleWidth
        )
    }

    item {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(4) {
                PosterSkeleton(
                    width = 120.dp,
                    height = 230.dp,
                    cornerRadius = 12f
                )
            }
        }
    }
}

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
        if (uiState.movieResults.isNotEmpty() || uiState.seerrMovieResults.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.movies),
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

                    items(
                        items = uiState.seerrMovieResults,
                        key = { seerrItem -> "seerr-movie-${seerrItem.tmdbId}" }
                    ) { seerrItem ->
                        SearchSeerrResultCard(item = seerrItem)
                    }
                }
            }
        }
        
        // TV Shows Section
        if (uiState.showResults.isNotEmpty() || uiState.seerrShowResults.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.search_results_shows),
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

                    items(
                        items = uiState.seerrShowResults,
                        key = { seerrItem -> "seerr-show-${seerrItem.tmdbId}" }
                    ) { seerrItem ->
                        SearchSeerrResultCard(item = seerrItem)
                    }
                }
            }
        }
        
        // Episodes Section
        if (uiState.episodeResults.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.search_results_episodes),
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
    val unknownTitle = stringResource(R.string.search_result_unknown_title)
    val unknownEpisode = stringResource(R.string.search_result_unknown_episode)
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
            val imageUrl = rememberImageUrl(
                itemId = item.id,
                imageType = "Primary",
                width = 300,
                height = 450,
                quality = 80
            )
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
            text = item.preferredDisplayTitle(
                unknownTitle = unknownTitle,
                unknownEpisode = unknownEpisode
            ),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
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
    val unknownTitle = stringResource(R.string.search_result_unknown_title)
    val unknownEpisode = stringResource(R.string.search_result_unknown_episode)
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
            val imageUrl = rememberImageUrl(
                itemId = item.id,
                imageType = "Primary",
                width = 240,
                height = 180,
                quality = 75,
                enableImageEnhancers = false
            )
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
                text = item.preferredDisplayTitle(
                    unknownTitle = unknownTitle,
                    unknownEpisode = unknownEpisode
                ),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = item.episodeDisplaySubtitle(fallback = unknownEpisode),
                color = Color.Gray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SearchSeerrResultCard(
    item: SeerrRecommendationTitle
) {
    val posterUrl = remember(item.posterPath) {
        item.posterPath
            ?.takeIf { it.isNotBlank() }
            ?.let { path -> "https://image.tmdb.org/t/p/w500$path" }
    }

    Column(
        modifier = Modifier.width(120.dp)
    ) {
        Card(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(0.67f),
            colors = CardDefaults.cardColors(
                containerColor = Color.Gray.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = item.title,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }

                SeerrTopBadges(
                    requestState = item.requestState,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = item.title,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp
        )

        Text(
            text = item.productionYear?.toString()
                ?: item.mediaType.replaceFirstChar { char ->
                    if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
                },
            color = Color.Gray,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
