package com.jellycine.app.ui.screens.dashboard.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy

import com.jellycine.app.ui.components.common.LazyImageLoader
import com.jellycine.app.ui.components.common.JellyfinImageLoader
import com.jellycine.app.util.image.rememberImageUrl
import com.jellycine.data.repository.getImageUrl
import com.jellycine.data.repository.getFormattedRuntime
import com.jellycine.data.repository.getYearAndGenre
import com.jellycine.data.repository.getFormattedRating
import com.jellycine.data.model.BaseItemDto
import com.jellycine.app.ui.screens.dashboard.SearchResultsSkeleton
import com.jellycine.app.ui.screens.dashboard.GridSkeleton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContainer(
    onNavigateToDetail: (BaseItemDto) -> Unit = {},
    onCancel: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isSearchActive = searchQuery.isNotEmpty()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Search Bar
        SearchBar(
            query = searchQuery,
            onQueryChange = viewModel::updateSearchQuery,
            onCancel = onCancel,
            onSearch = viewModel::executeSearch,
            focusRequester = focusRequester,
            keyboardController = keyboardController
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Content based on state
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when {
                uiState.isSearching -> {
                    SearchResultsSkeleton()
                }
                
                uiState.isSearchExecuted && searchQuery.isNotEmpty() -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(bottom = 100.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (uiState.movieResults.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Movies",
                                    onSeeAllClick = { /* Handle see all movies */ }
                                )
                            }
                            
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    items(uiState.movieResults) { movie ->
                                        Box(modifier = Modifier.width(120.dp)) {
                                            MovieCard(
                                                item = movie,
                                                onItemClick = { onNavigateToDetail(movie) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Shows Section
                        if (uiState.showResults.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Shows",
                                    onSeeAllClick = { /* Handle see all shows */ }
                                )
                            }
                            
                            item {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    items(uiState.showResults) { show ->
                                        Box(modifier = Modifier.width(120.dp)) {
                                            MovieCard(
                                                item = show,
                                                onItemClick = { onNavigateToDetail(show) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        // Episodes Section
                        if (uiState.episodeResults.isNotEmpty()) {
                            item {
                                SectionHeader(
                                    title = "Episodes",
                                    onSeeAllClick = { /* Handle see all episodes */ }
                                )
                            }
                            
                            items(uiState.episodeResults) { episode ->
                                SearchResultCard(
                                    item = episode,
                                    onItemClick = { onNavigateToDetail(episode) }
                                )
                            }
                        }
                        
                        // No results message
                        if (uiState.movieResults.isEmpty() && 
                            uiState.showResults.isEmpty() && 
                            uiState.episodeResults.isEmpty() && 
                            !uiState.isSearching) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "No results found for \"$searchQuery\"",
                                            color = Color.Gray,
                                            fontSize = 16.sp
                                        )
                                        
                                        uiState.error?.let { error ->
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "Error: $error",
                                                color = Color.Red.copy(alpha = 0.7f),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                isSearchActive && searchQuery.isNotEmpty() -> {
                    // Quick Search Suggestions
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 100.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(uiState.searchResults) { result ->
                            SearchResultCard(
                                item = result,
                                onItemClick = { onNavigateToDetail(result) }
                            )
                        }
                        
                        if (uiState.searchResults.isEmpty() && !uiState.isSearching) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No suggestions found",
                                        color = Color.Gray,
                                        fontSize = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
                
                else -> {
                    // Popular Movies Section
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        SectionHeader(
                            title = "Popular Movies",
                            onSeeAllClick = { /* Handle see all */ }
                        )
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (uiState.isLoading) {
                            GridSkeleton()
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(bottom = 100.dp)
                            ) {
                                items(uiState.popularMovies) { movie ->
                                    MovieCard(
                                        item = movie,
                                        onItemClick = { onNavigateToDetail(movie) }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onCancel: () -> Unit,
    onSearch: () -> Unit,
    focusRequester: FocusRequester,
    keyboardController: androidx.compose.ui.platform.SoftwareKeyboardController?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            modifier = Modifier.weight(1f),
            colors = CardDefaults.cardColors(
                containerColor = Color.White.copy(alpha = 0.1f)
            ),
            shape = RoundedCornerShape(25.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color.Gray,
                    modifier = Modifier.size(20.dp)
                )
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = Color.White,
                            fontSize = 16.sp
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                onSearch()
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                    )
                    
                    if (query.isEmpty()) {
                        Text(
                            text = "Search...",
                            color = Color.Gray,
                            fontSize = 16.sp
                        )
                    }
                }
                
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = Color.Gray,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        TextButton(
            onClick = onCancel,
            colors = ButtonDefaults.textButtonColors(
                contentColor = Color.White
            )
        ) {
            Text(
                text = "Cancel",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SearchResultCard(
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
        // Movie Poster
        Card(
            modifier = Modifier.size(70.dp),
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
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Movie Info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = item.name ?: "Unknown Title",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            val year = item.productionYear?.toString() ?: "Unknown"
            val genres = item.genres?.take(2)?.joinToString("/") ?: "Unknown"
            val runtime = item.getFormattedRuntime()
            
            Text(
                text = "$year • $genres${if (runtime.isNotEmpty()) " • $runtime" else ""}",
                color = Color.Gray,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            item.getFormattedRating()?.let { rating ->
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Rating",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = rating,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    onSeeAllClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        
        TextButton(
            onClick = onSeeAllClick,
            colors = ButtonDefaults.textButtonColors(
                contentColor = Color.Gray
            )
        ) {
            Text(
                text = "See all",
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun MovieCard(
    item: BaseItemDto,
    onItemClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.65f)
            .clickable { onItemClick() }
    ) {
        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(
                containerColor = Color.Gray.copy(alpha = 0.2f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Movie Poster
                val imageUrl = rememberImageUrl(itemId = item.id, imageType = "Primary")
                LazyImageLoader(
                    imageUrl = imageUrl,
                    contentDescription = item.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop,
                    cornerRadius = 16
                )
                
                // Gradient overlay for text readability
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.8f)
                                ),
                                startY = 0.7f
                            )
                        )
                )
                
                // Movie info at bottom
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Text(
                        text = item.name ?: "Unknown Title",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Text(
                        text = item.getYearAndGenre(),
                        color = Color.Gray,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun SearchContainerPreview() {
    SearchContainer()
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun SearchBarPreview() {
    val focusRequester = remember { FocusRequester() }
    
    SearchBar(
        query = "Avengers",
        onQueryChange = {},
        onCancel = {},
        onSearch = {},
        focusRequester = focusRequester,
        keyboardController = null
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun SearchResultCardPreview() {
    val mockItem = BaseItemDto(
        name = "Avengers: Endgame",
        id = "1",
        productionYear = 2019,
        genres = listOf("Action", "Sci-fi"),
        runTimeTicks = 10920000000L,
        communityRating = 4.9f,
        primaryImageAspectRatio = 1.5
    )
    
    SearchResultCard(
        item = mockItem,
        onItemClick = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun MovieCardPreview() {
    val mockItem = BaseItemDto(
        name = "The Northman",
        id = "1",
        productionYear = 2022,
        genres = listOf("Action", "Drama"),
        primaryImageAspectRatio = 1.5
    )
    
    Box(modifier = Modifier.width(150.dp)) {
        MovieCard(
            item = mockItem,
            onItemClick = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun SectionHeaderPreview() {
    SectionHeader(
        title = "Popular Movies",
        onSeeAllClick = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun SearchContainerLoadingPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = Color.White
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
fun SearchContainerEmptyPreview() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "No results found for \"Avengers\"",
            color = Color.Gray,
            fontSize = 16.sp
        )
    }
}