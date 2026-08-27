package com.vela.app.ui.screens.dashboard.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.vela.data.model.BaseItemDto
import com.vela.data.repository.FederatedMediaItem
import com.vela.shared.R
import kotlinx.coroutines.launch

private val FederatedBackground = Color(0xFF0D0D11)
private val FederatedAccent = Color(0xFFB9C3FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FederatedViewScreen(
    onNavigateToDetail: (BaseItemDto) -> Unit,
    onNavigateToLibrary: (BaseItemDto) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: FederatedHomeViewModel = viewModel {
        FederatedHomeViewModel(context.applicationContext as android.app.Application)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showContinueSettings by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.actionError) {
        val error = uiState.actionError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        viewModel.clearActionError()
    }

    if (showSearch) {
        FederatedSearchScreen(
            onNavigateToDetail = onNavigateToDetail,
            onBack = { showSearch = false },
            modifier = modifier
        )
        return
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FederatedBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FederatedViewHeader(
                onSearch = { showSearch = true },
                onConfigure = { showContinueSettings = true }
            )
            FederatedTabRow(
                selectedTab = uiState.selectedTab,
                onSelected = viewModel::selectTab
            )

            when (val content = uiState.content) {
                FederatedContentUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = FederatedAccent)
                }
                is FederatedContentUiState.Ready -> FederatedHomeContent(
                    tab = uiState.selectedTab,
                    items = content.items,
                    onItemClick = { result ->
                        viewModel.openItem(result) { item ->
                            if (uiState.selectedTab == FederatedHomeTab.LIBRARIES) {
                                onNavigateToLibrary(item)
                            } else {
                                onNavigateToDetail(item)
                            }
                        }
                    }
                )
            }
        }

        val failures = (uiState.content as? FederatedContentUiState.Ready)?.failures.orEmpty()
        if (failures.isNotEmpty()) {
            AssistChip(
                onClick = {
                    val failedServers = failures.joinToString { it.serverName }
                    scope.launch { snackbarHostState.showSnackbar(failedServers) }
                },
                label = {
                    Text(stringResource(R.string.federated_search_partial_failure, failures.size))
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
                CircularProgressIndicator(color = FederatedAccent)
            }
        }
    }

    if (showContinueSettings) {
        ContinueWatchingServerSheet(
            servers = uiState.servers,
            excludedServerIds = uiState.excludedContinueWatchingServerIds,
            onIncludedChange = viewModel::setContinueWatchingServerIncluded,
            onDismiss = { showContinueSettings = false }
        )
    }
}

@Composable
private fun FederatedViewHeader(
    onSearch: () -> Unit,
    onConfigure: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.federated_search_title),
            color = Color.White,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onSearch) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = stringResource(R.string.search),
                tint = Color.White.copy(alpha = 0.84f)
            )
        }
        IconButton(onClick = onConfigure) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = stringResource(R.string.federated_continue_settings),
                tint = Color.White.copy(alpha = 0.84f)
            )
        }
    }
}

@Composable
private fun FederatedTabRow(
    selectedTab: FederatedHomeTab,
    onSelected: (FederatedHomeTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FederatedHomeTab.entries.forEach { tab ->
            val selected = tab == selectedTab
            Column(
                modifier = Modifier
                    .clickable { onSelected(tab) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = tab.label(),
                    color = if (selected) FederatedAccent else Color.White.copy(alpha = 0.78f),
                    fontSize = 16.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(7.dp))
                Box(
                    modifier = Modifier
                        .width(48.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) FederatedAccent else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun FederatedHomeTab.label(): String = when (this) {
    FederatedHomeTab.CONTINUE_WATCHING -> stringResource(R.string.dashboard_continue_watching)
    FederatedHomeTab.FAVORITES -> stringResource(R.string.favorites)
    FederatedHomeTab.LIBRARIES -> stringResource(R.string.federated_libraries_tab)
}

@Composable
private fun FederatedHomeContent(
    tab: FederatedHomeTab,
    items: List<FederatedMediaItem>,
    onItemClick: (FederatedMediaItem) -> Unit
) {
    if (items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = when (tab) {
                    FederatedHomeTab.CONTINUE_WATCHING -> stringResource(R.string.federated_continue_empty)
                    FederatedHomeTab.FAVORITES -> stringResource(R.string.federated_favorites_empty)
                    FederatedHomeTab.LIBRARIES -> stringResource(R.string.federated_libraries_empty)
                },
                color = Color.White.copy(alpha = 0.62f),
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
        return
    }

    val groups = remember(items) { items.groupBy { it.serverId }.values.toList() }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 112.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        groups.forEach { serverItems ->
            item(
                key = "home_header_${serverItems.first().serverId}",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                Text(
                    text = serverItems.first().serverName,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                )
            }
            gridItems(
                items = serverItems,
                key = { result -> "home_${tab.name}_${result.serverId}:${result.item.id}" }
            ) { result ->
                FederatedHomeCard(
                    result = result,
                    tab = tab,
                    onClick = { onItemClick(result) }
                )
            }
        }
    }
}

@Composable
private fun FederatedHomeCard(
    result: FederatedMediaItem,
    tab: FederatedHomeTab,
    onClick: () -> Unit
) {
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
                .aspectRatio(if (tab == FederatedHomeTab.LIBRARIES) 1.55f else 16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF19191F)),
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
                    tint = Color.White.copy(alpha = 0.48f),
                    modifier = Modifier.size(32.dp)
                )
            }
            if (tab == FederatedHomeTab.CONTINUE_WATCHING) {
                val progress = remember(item.runTimeTicks, item.userData?.playbackPositionTicks) {
                    val runtime = item.runTimeTicks ?: 0L
                    val position = item.userData?.playbackPositionTicks ?: 0L
                    if (runtime > 0L) (position.toFloat() / runtime).coerceIn(0f, 1f) else 0f
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(4.dp)
                            .background(FederatedAccent)
                    )
                }
            }
        }
        Text(
            text = if (tab == FederatedHomeTab.CONTINUE_WATCHING && item.type == "Episode") {
                item.seriesName ?: item.name ?: stringResource(R.string.search_result_unknown_title)
            } else {
                item.name ?: stringResource(R.string.search_result_unknown_title)
            },
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp)
        )
        val subtitle = when {
            item.type == "Episode" -> buildString {
                if (tab != FederatedHomeTab.CONTINUE_WATCHING) item.seriesName?.let(::append)
                if (item.parentIndexNumber != null || item.indexNumber != null) {
                    if (isNotEmpty()) append(" · ")
                    append("S${item.parentIndexNumber ?: 0}E${item.indexNumber ?: 0}")
                }
                if (tab == FederatedHomeTab.CONTINUE_WATCHING && !item.name.isNullOrBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(item.name)
                }
            }
            tab == FederatedHomeTab.LIBRARIES -> ""
            item.productionYear != null -> item.productionYear.toString()
            else -> ""
        }
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContinueWatchingServerSheet(
    servers: List<com.vela.data.repository.FederatedServer>,
    excludedServerIds: Set<String>,
    onIncludedChange: (String, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF17171C),
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = stringResource(R.string.federated_continue_settings),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.federated_continue_settings_description),
                color = Color.White.copy(alpha = 0.64f),
                modifier = Modifier.padding(top = 6.dp, bottom = 12.dp)
            )
            LazyColumn(contentPadding = PaddingValues(bottom = 28.dp)) {
                items(servers, key = { it.id }) { server ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onIncludedChange(server.id, server.id in excludedServerIds)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = server.name,
                            color = Color.White,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Switch(
                            checked = server.id !in excludedServerIds,
                            onCheckedChange = { included ->
                                onIncludedChange(server.id, included)
                            }
                        )
                    }
                }
            }
        }
    }
}
