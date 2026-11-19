package com.jellycine.ui.screens.dashboard.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.toBaseItem
import com.jellycine.resources.*
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun ConnectionsSettingsScreen(
    viewModel: SettingsViewModel,
    onBackPressed: () -> Unit = {},
    onNavigateToRequestedItem: (BaseItemDto) -> Unit = {},
    extraItems: LazyListScope.() -> Unit = {},
    extraDialogs: @Composable () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSeerrDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.activeServerId) {
        viewModel.reloadSeerrConnection()
    }

    BackHandler { onBackPressed() }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.settings_connections)) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { SectionLabel(stringResource(Res.string.settings_seerr)) }
            item {
                SeerrConnectionCard(
                    seerr = uiState.seerr,
                    onClick = { showSeerrDialog = true }
                )
            }
            extraItems()
        }
    }

    if (showSeerrDialog) {
        SeerrConnectionDialog(
            connectionState = uiState.seerr,
            isBusy = uiState.seerr.status == SeerrConnectionStatus.CONNECTING ||
                uiState.seerr.status == SeerrConnectionStatus.CHECKING,
            onDismiss = {
                if (uiState.seerr.status != SeerrConnectionStatus.CONNECTING &&
                    uiState.seerr.status != SeerrConnectionStatus.CHECKING
                ) {
                    showSeerrDialog = false
                }
            },
            onConnect = { serverUrl, username, password ->
                viewModel.connectSeerr(
                    serverUrl = serverUrl,
                    username = username,
                    password = password
                ) { result ->
                    if (result.isSuccess) {
                        showSeerrDialog = false
                    }
                }
            },
            onDisconnect = {
                viewModel.disconnectSeerr()
                showSeerrDialog = false
            },
            onRefreshStatus = viewModel::refreshSeerrConnection
        )
    }

    if (uiState.seerrRequestedItems.mediaType != null) {
        SeerrRequestedItemsDialog(
            state = uiState.seerrRequestedItems,
            onDismiss = viewModel::clearSeerrRequestedItems,
            onItemClick = { item ->
                viewModel.clearSeerrRequestedItems()
                onNavigateToRequestedItem(item.toBaseItem())
            }
        )
    }

    extraDialogs()
}

@Composable
private fun SeerrConnectionCard(
    seerr: SeerrUiState,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = seerrAccentColor(seerr.status).copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Link,
                    contentDescription = null,
                    tint = seerrAccentColor(seerr.status),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.settings_seerr),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = seerrSubtitle(seerr),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            SeerrStatusChip(seerr.status)
        }
    }
}
