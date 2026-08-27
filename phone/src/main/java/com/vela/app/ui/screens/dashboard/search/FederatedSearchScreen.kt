package com.vela.app.ui.screens.dashboard.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.vela.data.model.BaseItemDto
import com.vela.data.model.SearchMediaType
import com.vela.data.repository.FederatedMediaItem
import com.vela.shared.R
import kotlinx.coroutines.launch

private val SearchPageBackground = Color(0xFF0D0D11)
private val SearchCardBackground = Color(0xFF19191F)
private val SearchAccent = Color(0xFFB9C3FF)

@Composable
fun FederatedSearchScreen(
    onNavigateToDetail: (BaseItemDto) -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val viewModel: FederatedSearchViewModel = viewModel {
        FederatedSearchViewModel(context.applicationContext as android.app.Application)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val visibleItems = remember(uiState.items, uiState.selectedServerId) {
        uiState.items.filter { result ->
            uiState.selectedServerId == null || result.serverId == uiState.selectedServerId
        }
    }

    LaunchedEffect(uiState.actionError) {
        val error = uiState.actionError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        viewModel.clearActionError()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SearchPageBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FederatedSearchHeader(
                query = uiState.query,
                onQueryChange = viewModel::updateQuery,
                onSearch = {
                    keyboardController?.hide()
                    viewModel.submitSearch()
                },
                onBack = onBack,
                focusRequester = focusRequester
            )
            ServerFilterRow(
                servers = uiState.servers,
                selectedServerId = uiState.selectedServerId,
                onSelected = viewModel::selectServer
            )
            MediaTypeFilterRow(
                selectedTypes = uiState.selectedTypes,
                onToggle = viewModel::toggleType
            )

            when {
                uiState.servers.isEmpty() -> FederatedSearchMessage(
                    stringResource(R.string.federated_search_no_servers)
                )
                uiState.query.isBlank() -> FederatedSearchMessage(
                    stringResource(R.string.federated_search_prompt)
                )
                uiState.isSearching && visibleItems.isEmpty() -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = SearchAccent)
                }
                visibleItems.isEmpty() -> FederatedSearchMessage(
                    stringResource(R.string.federated_search_empty)
                )
                else -> FederatedResultGrid(
                    items = visibleItems,
                    showServerSections = uiState.selectedServerId == null,
                    onItemClick = { result ->
                        viewModel.openResult(result, onNavigateToDetail)
                    }
                )
            }
        }

        if (uiState.failures.isNotEmpty()) {
            AssistChip(
                onClick = {
                    val message = uiState.failures.joinToString("\n") { failure ->
                        "${failure.serverName}: ${failure.message}"
                    }
                    // 单服失败是可降级状态，详细错误按需展开，不阻断其他结果浏览。
                    scope.launch { snackbarHostState.showSnackbar(message) }
                },
                label = {
                    Text(
                        stringResource(
                            R.string.federated_search_partial_failure,
                            uiState.failures.size
                        )
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF3A271B),
                    labelColor = Color(0xFFFFC48C)
                ),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 94.dp)
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 92.dp)
        )

        if (uiState.openingServerId != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = SearchAccent)
            }
        }
    }
}

@Composable
private fun FederatedSearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    focusRequester: FocusRequester
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back_button),
                    tint = Color.White.copy(alpha = 0.84f)
                )
            }
            Text(
                text = stringResource(R.string.federated_search_title),
                color = Color.White,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { focusRequester.requestFocus() }) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = stringResource(R.string.search),
                    tint = Color.White.copy(alpha = 0.84f)
                )
            }
        }
        TextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            placeholder = {
                Text(
                    text = stringResource(R.string.federated_search_hint),
                    color = Color.White.copy(alpha = 0.5f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null
                )
            },
            trailingIcon = if (query.isNotEmpty()) {
                {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.clear_search)
                        )
                    }
                }
            } else null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1B1B22),
                unfocusedContainerColor = Color(0xFF1B1B22),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedLeadingIconColor = SearchAccent,
                unfocusedLeadingIconColor = Color.White.copy(alpha = 0.62f),
                focusedTrailingIconColor = Color.White.copy(alpha = 0.72f),
                unfocusedTrailingIconColor = Color.White.copy(alpha = 0.72f)
            ),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
        )
    }
}

@Composable
private fun ServerFilterRow(
    servers: List<com.vela.data.repository.FederatedServer>,
    selectedServerId: String?,
    onSelected: (String?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        item {
            FederatedFilterChip(
                label = stringResource(R.string.federated_search_all_servers),
                selected = selectedServerId == null,
                onClick = { onSelected(null) }
            )
        }
        rowItems(servers, key = { it.id }) { server ->
            FederatedFilterChip(
                label = server.name,
                selected = selectedServerId == server.id,
                onClick = { onSelected(server.id) }
            )
        }
    }
}

@Composable
private fun MediaTypeFilterRow(
    selectedTypes: Set<SearchMediaType>,
    onToggle: (SearchMediaType) -> Unit
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchMediaType.entries.forEach { type ->
            FederatedFilterChip(
                label = when (type) {
                    SearchMediaType.MOVIE -> stringResource(R.string.movies)
                    SearchMediaType.SERIES -> stringResource(R.string.search_results_shows)
                    SearchMediaType.EPISODE -> stringResource(R.string.search_results_episodes)
                },
                selected = type in selectedTypes,
                onClick = { onToggle(type) }
            )
        }
    }
}

@Composable
private fun FederatedFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White.copy(alpha = 0.08f),
            labelColor = Color.White.copy(alpha = 0.72f),
            selectedContainerColor = SearchAccent,
            selectedLabelColor = Color(0xFF171A2B)
        ),
        border = null
    )
}

@Composable
private fun FederatedResultGrid(
    items: List<FederatedMediaItem>,
    showServerSections: Boolean,
    onItemClick: (FederatedMediaItem) -> Unit
) {
    val groups = remember(items, showServerSections) {
        if (showServerSections) items.groupBy { it.serverId }.values.toList() else listOf(items)
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 112.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        groups.forEach { serverItems ->
            if (showServerSections) {
                item(
                    key = "header_${serverItems.first().serverId}",
                    span = { GridItemSpan(maxLineSpan) }
                ) {
                    Text(
                        text = serverItems.first().serverName,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
            }
            gridItems(
                items = serverItems,
                key = { result -> "${result.serverId}:${result.item.id}" }
            ) { result ->
                FederatedResultCard(result = result, onClick = { onItemClick(result) })
            }
        }
    }
}

@Composable
private fun FederatedResultCard(result: FederatedMediaItem, onClick: () -> Unit) {
    val item = result.item
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(SearchCardBackground),
            contentAlignment = Alignment.Center
        ) {
            if (result.imageUrl != null) {
                AsyncImage(
                    model = result.imageUrl,
                    contentDescription = item.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Movie,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.52f),
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                text = result.serverName,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(
                        color = Color.Black.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(10.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Text(
            text = item.name ?: stringResource(R.string.search_result_unknown_title),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp)
        )
        val subtitle = item.searchSubtitle()
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.64f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun FederatedSearchMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = Color.White.copy(alpha = 0.62f),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}

private fun BaseItemDto.searchSubtitle(): String = when {
    type.equals("Episode", ignoreCase = true) -> buildString {
        seriesName?.let(::append)
        if (parentIndexNumber != null || indexNumber != null) {
            if (isNotEmpty()) append(" · ")
            append("S${parentIndexNumber ?: 0}E${indexNumber ?: 0}")
        }
    }
    productionYear != null -> productionYear.toString()
    else -> type.orEmpty()
}
