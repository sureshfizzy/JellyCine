package com.jellycine.app.ui.screens.dashboard.settings

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.SeerrItemIds
import com.jellycine.data.model.SeerrRequestedItem
import com.jellycine.player.discord.DiscordRpcManager
import com.jellycine.shared.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsSettingsScreen(
    onBackPressed: () -> Unit = {},
    onNavigateToRequestedItem: (BaseItemDto) -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(context) }
    val uiState by viewModel.uiState.collectAsState()

    val discordRpcManager = remember { DiscordRpcManager.getInstance(context) }
    var isDiscordAuthorized by remember { mutableStateOf(discordRpcManager.isAuthorized()) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                isDiscordAuthorized = discordRpcManager.isAuthorized()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val discordUsername = remember(isDiscordAuthorized) { discordRpcManager.getStoredUsername() }
    val preferences = remember { com.jellycine.shared.preferences.Preferences(context) }
    var discordRpcEnabled by remember { mutableStateOf(preferences.isDiscordRpcEnabled()) }
    var showSeerrDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.activeServerId) {
        viewModel.reloadSeerrConnection()
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings_connections)) },
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
            item { SectionLabel(stringResource(R.string.settings_seerr)) }
            item {
                SeerrConnectionCard(
                    seerr = uiState.seerr,
                    onClick = { showSeerrDialog = true }
                )
            }

            item { Spacer(modifier = Modifier.height(4.dp)) }

            item { SectionLabel(stringResource(R.string.settings_discord)) }
            item {
                DiscordConnectionCard(
                    isAuthorized = isDiscordAuthorized,
                    username = discordUsername,
                    rpcEnabled = discordRpcEnabled,
                    onRpcEnabledChange = { enabled ->
                        preferences.setDiscordRpcEnabled(enabled)
                        discordRpcEnabled = enabled
                    },
                    onConnect = {
                        context.startActivity(
                            Intent(context, com.jellycine.app.discord.DiscordAuthActivity::class.java)
                        )
                    },
                    onDisconnect = {
                        discordRpcManager.disconnect()
                        preferences.setDiscordRpcEnabled(false)
                        discordRpcEnabled = false
                        isDiscordAuthorized = false
                    }
                )
            }
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
                    text = stringResource(R.string.settings_seerr),
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

@Composable
private fun DiscordConnectionCard(
    isAuthorized: Boolean,
    username: String?,
    rpcEnabled: Boolean,
    onRpcEnabledChange: (Boolean) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val discordBlurple = Color(0xFF5865F2)

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
        Column {
            if (isAuthorized) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                color = discordBlurple.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_discord),
                            contentDescription = null,
                            tint = discordBlurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_discord),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (username != null) {
                                stringResource(R.string.settings_discord_subtitle_connected, username)
                            } else {
                                stringResource(R.string.settings_discord_status_connected)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = discordBlurple
                        )
                    }

                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.24f))
                    ) {
                        Text(
                            text = stringResource(R.string.settings_discord_status_connected),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF10B981),
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                color = discordBlurple.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Visibility,
                            contentDescription = null,
                            tint = discordBlurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_discord_show_activity),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_discord_show_activity_subtitle),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    Switch(
                        checked = rpcEnabled,
                        onCheckedChange = onRpcEnabledChange
                    )
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onDisconnect)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LinkOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.settings_discord_disconnect_button),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onConnect)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                color = discordBlurple.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_discord),
                            contentDescription = null,
                            tint = discordBlurple,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_discord),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_discord_subtitle_disconnected),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }

                    Surface(
                        color = discordBlurple,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_discord_connect_button),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp)
    )
}

private fun SeerrRequestedItem.toBaseItem(): BaseItemDto {
    return BaseItemDto(
        id = localItemId ?: SeerrItemIds.detailId(tmdbId, mediaType),
        name = title,
        type = if (mediaType.equals("tv", ignoreCase = true)) "Series" else "Movie",
        providerIds = mapOf("tmdb" to tmdbId),
        productionYear = productionYear,
        imageUrl = posterUrl
    )
}