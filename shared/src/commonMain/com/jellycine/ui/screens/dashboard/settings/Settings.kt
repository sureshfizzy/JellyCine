package com.jellycine.ui.screens.dashboard.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jellycine.data.preferences.NetworkSettingsStore
import com.jellycine.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import com.jellycine.data.network.sameServerUrl
import com.jellycine.data.repository.SavedServer
import com.jellycine.ui.components.common.ProfileImageLoader

/**
 * Shared settings tab. Rows whose destination is `null` are hidden, so each platform shows only what it supports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
    onLogout: () -> Unit = {},
    onNavigateToPlayerSettings: (() -> Unit)? = null,
    onNavigateToInterfaceSettings: (() -> Unit)? = null,
    onNavigateToClipsSettings: (() -> Unit)? = null,
    onNavigateToConnections: (() -> Unit)? = null,
    onNavigateToDownloads: (() -> Unit)? = null,
    onNavigateToCacheSettings: (() -> Unit)? = null,
    onNavigateToAbout: (() -> Unit)? = null,
    onNavigateToServerInfo: (() -> Unit)? = null,
    onNavigateToScreenTime: (() -> Unit)? = null,
    onOpenLanguageSettings: (() -> Unit)? = null,
    onExportLogs: (() -> Unit)? = null,
    onUserClick: (serverName: String?, users: List<SavedServer>) -> Unit = { _, _ -> },
    onServerClick: () -> Unit = {},
    discordAvailable: Boolean = false,
    showOfflineOptions: Boolean = false,
    showAboutRow: Boolean = false,
    appVersion: String = "",
    deviceInfo: (@Composable () -> Unit)? = null
) {
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    var showNetworkDialog by remember { mutableStateOf(false) }
    var editingNetworkTimeout by remember { mutableStateOf<NetworkTimeoutField?>(null) }
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
                compareByDescending<SavedServer> {
                    if (it.id == uiState.activeServerId) 1 else 0
                }
                    .thenBy { it.username.lowercase() }
            )
    }

    LaunchedEffect(uiState.activeServerId) {
        viewModel.reloadSeerrConnection()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(Res.string.settings)) },
                actions = {
                    onOpenLanguageSettings?.let { openLanguage ->
                        IconButton(onClick = openLanguage) {
                            Icon(
                                imageVector = Icons.Rounded.Translate,
                                contentDescription = stringResource(Res.string.settings_language)
                            )
                        }
                    }
                    onNavigateToAbout?.let { openAbout ->
                        IconButton(onClick = openAbout) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = stringResource(Res.string.cd_about_button)
                            )
                        }
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
                    username = uiState.username ?: stringResource(Res.string.settings_unknown_user),
                    serverName = uiState.serverName ?: stringResource(Res.string.settings_unknown_server),
                    serverUrl = uiState.serverUrl,
                    seerr = uiState.seerr,
                    serverTypeRaw = activeSavedServer?.serverTypeRaw,
                    profileImageUrl = uiState.profileImageUrl,
                    isAdministrator = uiState.isAdministrator,
                    onUserClick = { onUserClick(uiState.serverName, usersForCurrentServer) },
                    onServerClick = onServerClick,
                    onNavigateToDownloads = onNavigateToDownloads,
                    onNavigateToServerInfo = onNavigateToServerInfo,
                    onSeerrLimitClick = viewModel::loadSeerrRequestedItems
                )
            }

            if (onNavigateToScreenTime != null || onNavigateToConnections != null) {
                item {
                    SettingsSection {
                        onNavigateToScreenTime?.let { openScreenTime ->
                            SettingsItem(
                                icon = Icons.Rounded.Timer,
                                title = stringResource(Res.string.settings_screen_time),
                                subtitle = stringResource(Res.string.settings_screen_time_subtitle),
                                accentColor = Color(0xFF22D3EE),
                                onClick = openScreenTime
                            )
                        }
                        if (onNavigateToScreenTime != null && onNavigateToConnections != null) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                        onNavigateToConnections?.let { openConnections ->
                            SettingsItem(
                                icon = Icons.Rounded.Link,
                                title = stringResource(Res.string.settings_connections),
                                subtitle = stringResource(
                                    if (discordAvailable) {
                                        Res.string.settings_connections_subtitle
                                    } else {
                                        Res.string.settings_seerr
                                    }
                                ),
                                accentColor = Color(0xFF8B5CF6),
                                onClick = openConnections
                            )
                        }
                    }
                }
            }

            onNavigateToPlayerSettings?.let { openPlayerSettings ->
                item {
                    QuickActionsRow(
                        onNavigateToPlayerSettings = openPlayerSettings
                    )
                }
            }

            item { SectionLabel(stringResource(Res.string.settings_preferences)) }
            item {
                SettingsSection {
                    val rows = buildList<@Composable () -> Unit> {
                        onNavigateToInterfaceSettings?.let { openInterface ->
                            add {
                                SettingsItem(
                                    icon = Icons.Rounded.DisplaySettings,
                                    title = stringResource(Res.string.settings_interface),
                                    subtitle = stringResource(Res.string.settings_visual_options),
                                    accentColor = Color(0xFF8B5CF6),
                                    onClick = openInterface
                                )
                            }
                        }
                        onNavigateToClipsSettings?.let { openClips ->
                            add {
                                SettingsItem(
                                    icon = Icons.Rounded.VideoLibrary,
                                    title = stringResource(Res.string.settings_clips),
                                    subtitle = stringResource(Res.string.settings_clips_subtitle),
                                    accentColor = Color(0xFFEC4899),
                                    onClick = openClips
                                )
                            }
                        }
                        if (showOfflineOptions) {
                            add {
                                SettingsItem(
                                    icon = Icons.Rounded.Wifi,
                                    title = stringResource(Res.string.settings_wifi_only_downloads),
                                    subtitle = stringResource(
                                        if (uiState.wifiOnlyDownloads) Res.string.settings_enabled else Res.string.settings_disabled
                                    ),
                                    accentColor = Color(0xFF0EA5E9),
                                    trailing = {
                                        Switch(
                                            checked = uiState.wifiOnlyDownloads,
                                            onCheckedChange = { viewModel.setWifiOnlyDownloads(it) },
                                            colors = accentSwitchColors(Color(0xFF0EA5E9))
                                        )
                                    }
                                )
                            }
                            add {
                                SettingsItem(
                                    icon = Icons.Rounded.CloudOff,
                                    title = stringResource(Res.string.settings_force_offline),
                                    subtitle = stringResource(Res.string.settings_force_offline_subtitle),
                                    accentColor = Color(0xFFF97316),
                                    trailing = {
                                        Switch(
                                            checked = uiState.forceOffline,
                                            onCheckedChange = { viewModel.setForceOffline(it) },
                                            colors = accentSwitchColors(Color(0xFFF97316))
                                        )
                                    }
                                )
                            }
                        }
                        add {
                            SettingsItem(
                                icon = Icons.Rounded.SettingsEthernet,
                                title = stringResource(Res.string.settings_network),
                                subtitle = stringResource(Res.string.settings_network_subtitle),
                                accentColor = Color(0xFF06B6D4),
                                onClick = { showNetworkDialog = true }
                            )
                        }
                        onNavigateToCacheSettings?.let { openCache ->
                            add {
                                SettingsItem(
                                    icon = Icons.Rounded.Storage,
                                    title = stringResource(Res.string.settings_cache),
                                    subtitle = stringResource(Res.string.settings_cache_subtitle),
                                    accentColor = Color(0xFF22D3EE),
                                    onClick = openCache
                                )
                            }
                        }
                        onExportLogs?.let { exportLogs ->
                            add {
                                SettingsItem(
                                    icon = Icons.AutoMirrored.Rounded.Notes,
                                    title = stringResource(Res.string.settings_logs),
                                    subtitle = stringResource(Res.string.settings_logs_subtitle),
                                    accentColor = Color(0xFF64748B),
                                    onClick = exportLogs
                                )
                            }
                        }
                    }
                    rows.forEachIndexed { index, row ->
                        if (index > 0) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                        row()
                    }
                }
            }

            deviceInfo?.let { deviceInfoRows ->
                item { SectionLabel(stringResource(Res.string.settings_device_info)) }
                item {
                    SettingsSection {
                        deviceInfoRows()
                    }
                }
            }

            if (showAboutRow && onNavigateToAbout != null) {
                item {
                    SettingsSection {
                        SettingsItem(
                            icon = Icons.Rounded.Info,
                            title = stringResource(Res.string.about_title),
                            subtitle = stringResource(Res.string.about_version_chip, appVersion),
                            accentColor = Color(0xFF3B82F6),
                            onClick = onNavigateToAbout
                        )
                    }
                }
            }

            item { SectionLabel(stringResource(Res.string.settings_account)) }
            item {
                SettingsSection {
                    SettingsItem(
                        icon = Icons.AutoMirrored.Rounded.Logout,
                        title = stringResource(Res.string.logout),
                        subtitle = stringResource(Res.string.settings_sign_out_subtitle),
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
    onNavigateToDownloads: (() -> Unit)?,
    onNavigateToServerInfo: (() -> Unit)?,
    onSeerrLimitClick: (String) -> Unit
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
                        contentDescription = stringResource(Res.string.settings_add_user),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isAdministrator == true) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier.clickable(enabled = onNavigateToServerInfo != null) { onNavigateToServerInfo?.invoke() },
                    color = Color(0xFF4FD06B).copy(alpha = 0.12f),
                    shape = RoundedCornerShape(999.dp),
                    border = BorderStroke(1.dp, Color(0xFF4FD06B).copy(alpha = 0.24f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AdminPanelSettings,
                            contentDescription = null,
                            tint = Color(0xFF4FD06B),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(Res.string.settings_administrator),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF4FD06B),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
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
                                    text = stringResource(Res.string.settings_server_label),
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
                            contentDescription = stringResource(Res.string.settings_switch_server),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (onNavigateToDownloads != null) {
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
                                text = stringResource(Res.string.downloads),
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

                        SeerrRequestLimitsRow(
                            requestLimits = requestLimits,
                            onLimitClick = onSeerrLimitClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeerrRequestLimitsRow(
    requestLimits: com.jellycine.data.model.SeerrUserRequestLimits,
    onLimitClick: (String) -> Unit
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
                text = stringResource(Res.string.settings_seerr_request_limits),
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
                label = stringResource(Res.string.settings_seerr_movie_limit),
                value = formatSeerrLimit(
                    limit = requestLimits.movieQuotaLimit,
                    days = requestLimits.movieQuotaDays
                ),
                onClick = { onLimitClick("movie") }
            )
            SeerrLimitStat(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.LiveTv,
                accentColor = Color(0xFF06B6D4),
                label = stringResource(Res.string.settings_seerr_tv_limit),
                value = formatSeerrLimit(
                    limit = requestLimits.tvQuotaLimit,
                    days = requestLimits.tvQuotaDays
                ),
                onClick = { onLimitClick("tv") }
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
    value: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
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
        return stringResource(Res.string.settings_seerr_unlimited)
    }

    val requestCount = pluralStringResource(
        Res.plurals.settings_seerr_requests_count,
        limit,
        limit
    )

    return if (days != null && days > 0) {
        stringResource(Res.string.settings_seerr_limit_every_days, requestCount, days)
    } else {
        requestCount
    }
}

@Composable
private fun QuickActionsRow(
    onNavigateToPlayerSettings: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ActionTile(
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Rounded.PlayArrow,
            title = stringResource(Res.string.player_settings_title),
            subtitle = stringResource(Res.string.settings_player_settings_subtitle),
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

private enum class NetworkTimeoutField(val titleRes: StringResource) {
    REQUEST(Res.string.settings_request_timeout),
    CONNECTION(Res.string.settings_connection_timeout),
    SOCKET(Res.string.settings_socket_timeout)
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
        title = { Text(stringResource(Res.string.settings_network)) },
        text = {
            Column {
                NetworkDialogItem(
                    title = stringResource(Res.string.settings_request_timeout),
                    value = "$requestTimeoutMs ms",
                    onClick = { onSelectField(NetworkTimeoutField.REQUEST) }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                NetworkDialogItem(
                    title = stringResource(Res.string.settings_connection_timeout),
                    value = "$connectionTimeoutMs ms",
                    onClick = { onSelectField(NetworkTimeoutField.CONNECTION) }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                NetworkDialogItem(
                    title = stringResource(Res.string.settings_socket_timeout),
                    value = "$socketTimeoutMs ms",
                    onClick = { onSelectField(NetworkTimeoutField.SOCKET) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.settings_close), color = Color(0xFF22D3EE))
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
        parsedValue in NetworkSettingsStore.MIN_TIMEOUT_MS..NetworkSettingsStore.MAX_TIMEOUT_MS
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
                    label = { Text(stringResource(Res.string.settings_milliseconds)) },
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
                        Res.string.settings_allowed_range_ms,
                        NetworkSettingsStore.MIN_TIMEOUT_MS,
                        NetworkSettingsStore.MAX_TIMEOUT_MS
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f)
                )
                if (hasValidationError) {
                    Text(
                        text = stringResource(Res.string.settings_enter_valid_milliseconds),
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
                Text(stringResource(Res.string.settings_apply), color = Color(0xFF22D3EE))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel), color = Color.White.copy(alpha = 0.8f))
            }
        }
    )
}
