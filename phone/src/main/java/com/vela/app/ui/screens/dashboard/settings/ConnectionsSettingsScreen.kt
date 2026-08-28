package com.vela.app.ui.screens.dashboard.settings

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
import com.vela.data.model.BaseItemDto
import com.vela.data.model.SeerrItemIds
import com.vela.data.model.SeerrRequestedItem
import com.vela.app.ui.components.common.AmoledDialogFrame
import com.vela.player.discord.DiscordRpcManager
import com.vela.shared.R

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
    val isDiscordAppInstalled = remember { discordRpcManager.isDiscordInstalled() }
    var isDiscordAuthorized by remember { mutableStateOf(discordRpcManager.isAuthorized()) }
    val preferences = remember { com.vela.shared.preferences.Preferences(context) }
    var discordRpcEnabled by remember { mutableStateOf(preferences.isDiscordRpcEnabled()) }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val wasAuthorized = isDiscordAuthorized
                isDiscordAuthorized = discordRpcManager.isAuthorized()
                if (!wasAuthorized && isDiscordAuthorized) {
                    preferences.setDiscordRpcEnabled(true)
                    discordRpcEnabled = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val discordUsername = remember(isDiscordAuthorized) { discordRpcManager.getStoredUsername() }
    var showSeerrDialog by remember { mutableStateOf(false) }
    var showDiscordDialog by remember { mutableStateOf(false) }

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
            item { SectionLabel(stringResource(R.string.settings_server_lines)) }
            item {
                val activeServer = uiState.savedServers.firstOrNull { it.id == uiState.activeServerId }
                ServerLinesSection(
                    server = activeServer,
                    isBusy = uiState.isLineBusy,
                    onAddLine = { url, name -> viewModel.addServerLine(url, name) },
                    onSwitchLine = viewModel::switchServerLine,
                    onRemoveLine = viewModel::removeServerLine,
                    onAutoSelect = viewModel::autoSelectServerLine,
                    onSetAutoRoute = viewModel::setAutoRouteEnabled
                )
            }
            item { Spacer(modifier = Modifier.height(4.dp)) }

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
                    onClick = {
                        if (isDiscordAuthorized) {
                            showDiscordDialog = true
                        } else {
                            context.startActivity(
                                Intent(context, com.vela.app.discord.DiscordAuthActivity::class.java)
                            )
                        }
                    }
                )
            }
            if (!isDiscordAppInstalled) {
                item {
                    Text(
                        text = stringResource(R.string.settings_discord_note_app_required),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
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

    if (showDiscordDialog) {
        DiscordConnectionDialog(
            username = discordUsername,
            rpcEnabled = discordRpcEnabled,
            onRpcEnabledChange = { enabled ->
                preferences.setDiscordRpcEnabled(enabled)
                discordRpcEnabled = enabled
            },
            onDisconnect = {
                discordRpcManager.disconnect()
                preferences.setDiscordRpcEnabled(false)
                discordRpcEnabled = false
                isDiscordAuthorized = false
                showDiscordDialog = false
            },
            onDismiss = { showDiscordDialog = false }
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
    onClick: () -> Unit
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

            if (isAuthorized) {
                DiscordStatusChip()
            } else {
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

@Composable
private fun DiscordConnectionDialog(
    username: String?,
    rpcEnabled: Boolean,
    onRpcEnabledChange: (Boolean) -> Unit,
    onDisconnect: () -> Unit,
    onDismiss: () -> Unit
) {
    val discordBlurple = Color(0xFF5865F2)

    AmoledDialogFrame(
        dismissOnRequest = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_discord),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-10).dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.84f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0x14000000),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    color = discordBlurple.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_discord),
                                contentDescription = null,
                                tint = discordBlurple,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_discord),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = if (username != null) {
                                    stringResource(R.string.settings_discord_subtitle_connected, username)
                                } else {
                                    stringResource(R.string.settings_discord_status_connected)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.72f)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            DiscordStatusChip()
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = Color.White.copy(alpha = 0.06f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Visibility,
                        contentDescription = null,
                        tint = discordBlurple,
                        modifier = Modifier.size(20.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_discord_show_activity),
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.settings_discord_show_activity_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }

                    Switch(
                        checked = rpcEnabled,
                        onCheckedChange = onRpcEnabledChange
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDisconnect,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFEF4444),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.LinkOff,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.settings_discord_disconnect_button),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun DiscordStatusChip() {
    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(
                text = stringResource(R.string.settings_discord_status_connected),
                style = MaterialTheme.typography.labelMedium
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = Color(0xFF10B981).copy(alpha = 0.14f),
            disabledLabelColor = Color(0xFF10B981)
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = false,
            borderColor = Color(0xFF10B981).copy(alpha = 0.24f)
        )
    )
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