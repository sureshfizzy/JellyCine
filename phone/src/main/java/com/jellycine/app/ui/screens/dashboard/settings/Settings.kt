package com.jellycine.app.ui.screens.dashboard.settings

import android.os.Build
import android.content.Context
import android.content.Intent
import android.media.MediaCodecList
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jellycine.app.ui.screens.auth.ProfileImageLoader
import com.jellycine.app.ui.screens.auth.ServerSwitchDialogsHost
import com.jellycine.app.ui.screens.auth.ServerSwitchViewModel
import com.jellycine.app.ui.screens.auth.rememberServerSwitchDialogsState
import com.jellycine.app.R
import com.jellycine.data.network.sameServerUrl
import com.jellycine.data.preferences.NetworkPreferences
import com.jellycine.data.repository.AuthRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(
    onLogout: () -> Unit = {},
    onNavigateToPlayerSettings: () -> Unit = {},
    onNavigateToInterfaceSettings: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToCacheSettings: () -> Unit = {},
    onNavigateToAbout: () -> Unit = {},
    onAddServer: () -> Unit = {},
    onAddUser: (serverUrl: String, serverName: String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(context) }
    val serverSwitchViewModel: ServerSwitchViewModel = viewModel {
        ServerSwitchViewModel(context.applicationContext as android.app.Application)
    }
    val uiState by viewModel.uiState.collectAsState()
    val serverSwitchUiState by serverSwitchViewModel.uiState.collectAsState()
    val supportedCodecs = remember(context) { getSupportedCodecsSummary(context) }
    val listState = rememberLazyListState()
    val serverSwitchDialogsState = rememberServerSwitchDialogsState()

    var showNetworkDialog by remember { mutableStateOf(false) }
    var editingNetworkTimeout by remember { mutableStateOf<NetworkTimeoutField?>(null) }
    var showSeerrDialog by remember { mutableStateOf(false) }
    val activeSavedServer = remember(uiState.savedServers, uiState.activeServerId) {
        uiState.savedServers.firstOrNull { it.id == uiState.activeServerId }
    }
    val usersForCurrentServer = remember(uiState.savedServers, uiState.serverUrl, uiState.activeServerId) {
        val currentServerUrl = uiState.serverUrl
        uiState.savedServers
            .filter { savedServer ->
                currentServerUrl != null &&
                    sameServerUrl(savedServer.serverUrl, currentServerUrl)
            }
            .sortedWith(
                compareByDescending<AuthRepository.SavedServer> {
                    if (it.id == uiState.activeServerId) 1 else 0
                }
                    .thenBy { it.username.lowercase() }
            )
    }

    LaunchedEffect(uiState.activeServerId) {
        viewModel.reloadSeerrConnection()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                actions = {
                    IconButton(onClick = onNavigateToAbout) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = stringResource(R.string.cd_about_button)
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
            state = listState,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                UserProfileSection(
                    user = uiState.user,
                    username = uiState.username ?: stringResource(R.string.settings_unknown_user),
                    serverName = uiState.serverName ?: stringResource(R.string.settings_unknown_server),
                    serverUrl = uiState.serverUrl,
                    seerr = uiState.seerr,
                    serverTypeRaw = activeSavedServer?.serverTypeRaw,
                    profileImageUrl = uiState.profileImageUrl,
                    isAdministrator = uiState.isAdministrator,
                    onUserClick = {
                        serverSwitchDialogsState.openUsers(uiState.serverName, usersForCurrentServer)
                    },
                    onServerClick = serverSwitchDialogsState::openServers,
                    onNavigateToDownloads = onNavigateToDownloads
                )
            }

            item {
                SettingsSection {
                    SettingsItem(
                        icon = Icons.Rounded.Link,
                        title = stringResource(R.string.settings_seerr),
                        subtitle = seerrSubtitle(uiState.seerr),
                        accentColor = seerrAccentColor(uiState.seerr.status),
                        trailing = {
                            SeerrStatusChip(uiState.seerr.status)
                        },
                        onClick = { showSeerrDialog = true }
                    )
                }
            }

            item {
                QuickActionsRow(
                    onOpenLanguageSettings = { openAppLanguageSettings(context) },
                    onNavigateToPlayerSettings = onNavigateToPlayerSettings
                )
            }

            item { SectionLabel(stringResource(R.string.settings_preferences)) }
            item {
                SettingsSection {
                    SettingsItem(
                        icon = Icons.Rounded.DisplaySettings,
                        title = stringResource(R.string.settings_interface),
                        subtitle = stringResource(R.string.settings_visual_options),
                        accentColor = Color(0xFF8B5CF6),
                        onClick = onNavigateToInterfaceSettings
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.Rounded.Wifi,
                        title = stringResource(R.string.settings_wifi_only_downloads),
                        subtitle = stringResource(
                            if (uiState.wifiOnlyDownloads) R.string.settings_enabled else R.string.settings_disabled
                        ),
                        accentColor = Color(0xFF0EA5E9),
                        trailing = {
                            Switch(
                                checked = uiState.wifiOnlyDownloads,
                                onCheckedChange = { viewModel.setWifiOnlyDownloads(it) }
                            )
                        }
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.Rounded.SettingsEthernet,
                        title = stringResource(R.string.settings_network),
                        subtitle = stringResource(R.string.settings_network_subtitle),
                        accentColor = Color(0xFF06B6D4),
                        onClick = { showNetworkDialog = true }
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.Rounded.Storage,
                        title = stringResource(R.string.settings_cache),
                        subtitle = stringResource(R.string.settings_cache_subtitle),
                        accentColor = Color(0xFF22D3EE),
                        onClick = onNavigateToCacheSettings
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.settings_device_info)) }
            item {
                SettingsSection {
                    SettingsItem(
                        icon = Icons.Rounded.Smartphone,
                        title = stringResource(R.string.settings_device_model),
                        subtitle = stringResource(
                            R.string.settings_device_model_value,
                            Build.MANUFACTURER,
                            Build.MODEL
                        ),
                        accentColor = Color(0xFF14B8A6)
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.Rounded.Android,
                        title = stringResource(R.string.settings_android_version),
                        subtitle = stringResource(
                            R.string.settings_android_version_value,
                            Build.VERSION.RELEASE,
                            Build.VERSION.SDK_INT
                        ),
                        accentColor = Color(0xFF10B981)
                    )
                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.Rounded.VideoLibrary,
                        title = stringResource(R.string.settings_video_codecs),
                        subtitle = supportedCodecs,
                        accentColor = Color(0xFFF59E0B)
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.settings_account)) }
            item {
                SettingsSection {
                    SettingsItem(
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        title = stringResource(R.string.logout),
                        subtitle = stringResource(R.string.settings_sign_out_subtitle),
                        onClick = { viewModel.logout(onLogout) },
                        isDestructive = true,
                        accentColor = Color(0xFFEF4444)
                    )
                }
            }
        }
    }

    uiState.error?.let { error ->
        LaunchedEffect(error) {
            viewModel.clearError()
        }
    }

    if (showNetworkDialog) {
        NetworkSettingsDialog(
            requestTimeoutMs = uiState.requestTimeoutMs,
            connectionTimeoutMs = uiState.connectionTimeoutMs,
            socketTimeoutMs = uiState.socketTimeoutMs,
            onDismiss = { showNetworkDialog = false },
            onSelectField = { field ->
                showNetworkDialog = false
                editingNetworkTimeout = field
            }
        )
    }

    editingNetworkTimeout?.let { field ->
        val initialValue = when (field) {
            NetworkTimeoutField.REQUEST -> uiState.requestTimeoutMs
            NetworkTimeoutField.CONNECTION -> uiState.connectionTimeoutMs
            NetworkTimeoutField.SOCKET -> uiState.socketTimeoutMs
        }
        TimeoutValueDialog(
            field = field,
            initialValue = initialValue,
            onDismiss = { editingNetworkTimeout = null },
            onSave = { value ->
                when (field) {
                    NetworkTimeoutField.REQUEST -> viewModel.setRequestTimeoutMs(value)
                    NetworkTimeoutField.CONNECTION -> viewModel.setConnectionTimeoutMs(value)
                    NetworkTimeoutField.SOCKET -> viewModel.setSocketTimeoutMs(value)
                }
                editingNetworkTimeout = null
            }
        )
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

    ServerSwitchDialogsHost(
        state = serverSwitchDialogsState,
        savedServers = uiState.savedServers,
        activeServerId = uiState.activeServerId,
        currentServerName = uiState.serverName,
        currentServerUrl = uiState.serverUrl,
        isSwitching = serverSwitchUiState.isBusy,
        onAddServer = onAddServer,
        onAddUser = onAddUser,
        onServerSelected = { server, dismissDialog ->
            serverSwitchViewModel.switchServer(
                serverId = server.id,
                activeServerId = uiState.activeServerId,
                onSwitchComplete = dismissDialog
            )
        },
        onRequestRemoveServer = serverSwitchDialogsState::requestRemoval,
        onRequestRemoveUser = serverSwitchDialogsState::requestRemoval,
        onRemoveServer = { serverId, onRemoveComplete ->
            serverSwitchViewModel.removeServer(
                serverId = serverId,
                onRemoveComplete = onRemoveComplete
            )
        }
    )
}

@Composable
private fun UserProfileSection(
    user: com.jellycine.data.model.UserDto?,
    username: String,
    serverName: String,
    serverUrl: String?,
    seerr: SeerrUiState,
    serverTypeRaw: String?,
    profileImageUrl: String?,
    isAdministrator: Boolean?,
    onUserClick: () -> Unit,
    onServerClick: () -> Unit,
    onNavigateToDownloads: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ProfileImageLoader(
                imageUrl = profileImageUrl,
                serverTypeRaw = serverTypeRaw,
                modifier = Modifier
                    .size(120.dp)
                    .clickable(onClick = onUserClick)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(horizontal = 10.dp)
                    .clickable(onClick = onUserClick)
            ) {
                Text(
                    text = user?.name ?: username,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
                IconButton(
                    onClick = onUserClick,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 16.dp, y = (-8).dp)
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AddCircle,
                        contentDescription = stringResource(R.string.settings_add_user),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isAdministrator == true) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_administrator),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF4FD06B),
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Transparent
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onServerClick),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Storage,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.settings_server_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = user?.serverName ?: serverName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            if (serverUrl != null) {
                                Text(
                                    text = serverUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = stringResource(R.string.settings_switch_server),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onNavigateToDownloads),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            tint = Color(0xFF06B6D4),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.downloads),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    val requestLimits = seerr.requestLimits
                    if (
                        requestLimits != null &&
                        (seerr.status == SeerrConnectionStatus.CONNECTED ||
                            seerr.status == SeerrConnectionStatus.CHECKING)
                    ) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                        )

                        SeerrRequestLimitsRow(requestLimits = requestLimits)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeerrRequestLimitsRow(
    requestLimits: com.jellycine.data.repository.SeerrUserRequestLimits
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.settings_seerr_request_limits),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SeerrLimitStat(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.LocalMovies,
                accentColor = Color(0xFFF59E0B),
                label = stringResource(R.string.settings_seerr_movie_limit),
                value = formatSeerrLimit(
                    limit = requestLimits.movieQuotaLimit,
                    days = requestLimits.movieQuotaDays
                )
            )
            SeerrLimitStat(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.LiveTv,
                accentColor = Color(0xFF06B6D4),
                label = stringResource(R.string.settings_seerr_tv_limit),
                value = formatSeerrLimit(
                    limit = requestLimits.tvQuotaLimit,
                    days = requestLimits.tvQuotaDays
                )
            )
        }
    }
}

@Composable
private fun SeerrLimitStat(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    accentColor: Color,
    label: String,
    value: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = accentColor.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun formatSeerrLimit(limit: Int?, days: Int?): String {
    if (limit == null || limit <= 0) {
        return stringResource(R.string.settings_seerr_unlimited)
    }

    val requestCount = pluralStringResource(
        R.plurals.settings_seerr_requests_count,
        limit,
        limit
    )

    return if (days != null && days > 0) {
        stringResource(R.string.settings_seerr_limit_every_days, requestCount, days)
    } else {
        requestCount
    }
}

@Composable
private fun QuickActionsRow(
    onOpenLanguageSettings: () -> Unit,
    onNavigateToPlayerSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionTile(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.Translate,
            title = stringResource(R.string.settings_language),
            subtitle = stringResource(R.string.settings_language_subtitle),
            accentColor = Color(0xFF14B8A6),
            onClick = onOpenLanguageSettings
        )

        ActionTile(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.PlayArrow,
            title = stringResource(R.string.player_settings_title),
            subtitle = stringResource(R.string.settings_player_settings_subtitle),
            accentColor = Color(0xFF3B82F6),
            onClick = onNavigateToPlayerSettings
        )
    }
}

@Composable
private fun ActionTile(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = accentColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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

private fun openAppLanguageSettings(context: Context) {
    val appLanguageIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Intent(AndroidSettings.ACTION_APP_LOCALE_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
    } else {
        Intent(AndroidSettings.ACTION_LOCALE_SETTINGS)
    }

    val fallbackIntent = Intent(AndroidSettings.ACTION_LOCALE_SETTINGS)
    val intentToLaunch = when {
        appLanguageIntent.resolveActivity(context.packageManager) != null -> appLanguageIntent
        fallbackIntent.resolveActivity(context.packageManager) != null -> fallbackIntent
        else -> return
    }

    context.startActivity(intentToLaunch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun SettingsSection(
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isLoading: Boolean = false,
    isDestructive: Boolean = false,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable {
            if (!isLoading) onClick()
        }
    } else Modifier

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(
                    color = if (isDestructive)
                        MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    else
                        accentColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive)
                    MaterialTheme.colorScheme.error
                else
                    accentColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isDestructive)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }

        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 2.dp
                )
            }
            trailing != null -> {
                trailing()
            }
            onClick != null -> {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SettingsPreview() {
    Settings()
}

private enum class NetworkTimeoutField(val titleRes: Int) {
    REQUEST(R.string.settings_request_timeout),
    CONNECTION(R.string.settings_connection_timeout),
    SOCKET(R.string.settings_socket_timeout)
}

@Composable
private fun NetworkSettingsDialog(
    requestTimeoutMs: Int,
    connectionTimeoutMs: Int,
    socketTimeoutMs: Int,
    onDismiss: () -> Unit,
    onSelectField: (NetworkTimeoutField) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text(stringResource(R.string.settings_network)) },
        text = {
            Column {
                NetworkDialogItem(
                    title = stringResource(R.string.settings_request_timeout),
                    value = "$requestTimeoutMs ms",
                    onClick = { onSelectField(NetworkTimeoutField.REQUEST) }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                NetworkDialogItem(
                    title = stringResource(R.string.settings_connection_timeout),
                    value = "$connectionTimeoutMs ms",
                    onClick = { onSelectField(NetworkTimeoutField.CONNECTION) }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                NetworkDialogItem(
                    title = stringResource(R.string.settings_socket_timeout),
                    value = "$socketTimeoutMs ms",
                    onClick = { onSelectField(NetworkTimeoutField.SOCKET) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_close), color = Color(0xFF22D3EE))
            }
        }
    )
}

@Composable
private fun NetworkDialogItem(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.72f)
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.72f)
        )
    }
}

@Composable
private fun TimeoutValueDialog(
    field: NetworkTimeoutField,
    initialValue: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var textValue by remember(initialValue) { mutableStateOf(initialValue.toString()) }
    val parsedValue = textValue.toIntOrNull()
    val isValid = parsedValue != null &&
        parsedValue in NetworkPreferences.MIN_TIMEOUT_MS..NetworkPreferences.MAX_TIMEOUT_MS
    val hasValidationError = textValue.isNotBlank() && !isValid

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text(stringResource(field.titleRes)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = textValue,
                    onValueChange = { input ->
                        textValue = input.filter { it.isDigit() }.take(6)
                    },
                    label = { Text(stringResource(R.string.settings_milliseconds)) },
                    singleLine = true,
                    isError = hasValidationError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = Color.White.copy(alpha = 0.9f),
                        unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
                        focusedBorderColor = Color(0xFF22D3EE),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.35f),
                        cursorColor = Color(0xFF22D3EE),
                        errorBorderColor = Color(0xFFFF6B6B),
                        errorLabelColor = Color(0xFFFF6B6B),
                        errorCursorColor = Color(0xFFFF6B6B)
                    )
                )
                Text(
                    text = stringResource(
                        R.string.settings_allowed_range_ms,
                        NetworkPreferences.MIN_TIMEOUT_MS,
                        NetworkPreferences.MAX_TIMEOUT_MS
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                if (hasValidationError) {
                    Text(
                        text = stringResource(R.string.settings_enter_valid_milliseconds),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFF6B6B)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { parsedValue?.let(onSave) }
            ) {
                Text(stringResource(R.string.settings_apply), color = Color(0xFF22D3EE))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Color.White.copy(alpha = 0.8f))
            }
        }
    )
}

private fun getSupportedCodecsSummary(context: Context): String {
    return try {
        val mediaCodecList = MediaCodecList(MediaCodecList.REGULAR_CODECS)
        val videoCodecs = mutableSetOf<String>()
        mediaCodecList.codecInfos.forEach { codecInfo ->
            if (!codecInfo.isEncoder) {
                codecInfo.supportedTypes.forEach { type ->
                    if (type.startsWith("video/")) {
                        videoCodecs.add(readableCodecName(type))
                    }
                }
            }
        }

        if (videoCodecs.isEmpty()) context.getString(R.string.settings_unavailable) else videoCodecs.sorted().joinToString(", ")
    } catch (_: Exception) {
        context.getString(R.string.settings_unavailable)
    }
}

private fun readableCodecName(mimeType: String): String {
    return when (mimeType.lowercase()) {
        "video/avc" -> "H.264"
        "video/hevc" -> "H.265"
        "video/x-vnd.on2.vp9" -> "VP9"
        "video/av01" -> "AV1"
        "video/dolby-vision" -> "Dolby Vision"
        "video/mp4v-es" -> "MPEG-4"
        "video/3gpp" -> "H.263"
        "video/mpeg2" -> "MPEG-2"
        "video/raw" -> "RAW"
        else -> {
            val subtype = mimeType.substringAfter('/', mimeType)
            subtype.substringAfterLast('.').uppercase()
        }
    }
}

