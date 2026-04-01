package com.jellycine.app.ui.screens.dashboard.search

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellycine.app.R
import com.jellycine.app.util.image.disableEmbyPosterEnhancers
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepositoryProvider
import coil3.imageLoader
import coil3.request.*
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

private object SearchBurstImagePrefetcher {
    private val prefetchedPrimary = ConcurrentHashMap.newKeySet<String>()

    fun clear() {
        prefetchedPrimary.clear()
    }

    suspend fun preload(
        items: List<BaseItemDto>,
        mediaRepository: com.jellycine.data.repository.MediaRepository,
        context: android.content.Context,
        enableImageEnhancers: Boolean
    ) {
        if (items.isEmpty()) return
        val imageLoader = context.imageLoader
        val distinctItems = items.asSequence()
            .filter { !it.id.isNullOrBlank() }
            .distinctBy { it.id }
            .toList()
            .take(36)

        suspend fun enqueuePrimary(item: BaseItemDto) {
            val itemId = item.id ?: return
            if (!prefetchedPrimary.add(itemId)) return

            val itemEnhancersEnabled = if (item.type.equals("Episode", ignoreCase = true)) {
                false
            } else {
                enableImageEnhancers
            }

            val primaryUrl = mediaRepository.getImageUrlString(
                itemId = itemId,
                imageType = "Primary",
                width = 300,
                height = 450,
                quality = 80,
                enableImageEnhancers = itemEnhancersEnabled
            )

            if (!primaryUrl.isNullOrBlank()) {
                imageLoader.enqueue(
                    ImageRequest.Builder(context)
                        .data(primaryUrl)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .crossfade(false)
                        .allowHardware(true)
                        .allowRgb565(true)
                        .build()
                )
            }
        }

        distinctItems.take(10).forEach { item ->
            enqueuePrimary(item)
        }

        if (distinctItems.size > 10) {
            delay(200)
            distinctItems.drop(10).forEach { item ->
                enqueuePrimary(item)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContainer(
    onNavigateToDetail: (BaseItemDto) -> Unit = {},
    onCancel: () -> Unit = {},
    viewModel: SearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    val disablePosterEnhancers = disableEmbyPosterEnhancers()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val isSearchActive = searchQuery.isNotEmpty()
    val hasSearchResults = remember(
        uiState.movieResults,
        uiState.showResults,
        uiState.episodeResults
    ) {
        uiState.movieResults.isNotEmpty() ||
            uiState.showResults.isNotEmpty() ||
            uiState.episodeResults.isNotEmpty()
    }
    val burstPrefetchItems = remember(
        isSearchActive,
        uiState.movieResults,
        uiState.showResults,
        uiState.episodeResults
    ) {
        if (isSearchActive) {
            buildList {
                addAll(uiState.movieResults.take(12))
                addAll(uiState.showResults.take(12))
                addAll(uiState.episodeResults.take(12))
            }.filter { it.id != null && !it.name.isNullOrBlank() }
                .distinctBy { it.id }
        } else {
            emptyList()
        }
    }

    LaunchedEffect(disablePosterEnhancers) {
        SearchBurstImagePrefetcher.clear()
    }

    LaunchedEffect(burstPrefetchItems.hashCode(), disablePosterEnhancers) {
        if (burstPrefetchItems.isEmpty()) return@LaunchedEffect
        SearchBurstImagePrefetcher.preload(
            items = burstPrefetchItems,
            mediaRepository = mediaRepository,
            context = context,
            enableImageEnhancers = !disablePosterEnhancers
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (isSearchActive) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 72.dp)
            ) {
                if (uiState.isSearching) {
                    SearchResultsViewSkeleton()
                } else if (hasSearchResults) {
                    SearchResultsView(
                        uiState = uiState,
                        onItemClick = onNavigateToDetail,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptySearchState(
                            message = uiState.error ?: stringResource(R.string.search_no_results)
                        )
                    }
                }
            }
        } else {
            ImmersiveSection(
                title = stringResource(R.string.suggestions),
                movies = uiState.suggestions,
                isLoading = uiState.SuggestionsLoading,
                onItemClick = onNavigateToDetail,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 20.dp)
            )
        }

        SearchBar(
            query = searchQuery,
            onQueryChange = viewModel::updateSearchQuery,
            onCancel = onCancel,
            onSearch = viewModel::executeSearch,
            focusRequester = focusRequester,
            keyboardController = keyboardController,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
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
    keyboardController: SoftwareKeyboardController?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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
                    contentDescription = stringResource(R.string.search),
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
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(Color.White),
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
                            text = stringResource(R.string.search_hint),
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
                            contentDescription = stringResource(R.string.clear_search),
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
                text = stringResource(R.string.cancel),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}


