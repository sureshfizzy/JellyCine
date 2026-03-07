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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.*
import com.jellycine.app.R
import com.jellycine.data.network.NetworkModule
import com.jellycine.data.preferences.NetworkPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Settings(
    onLogout: () -> Unit = {},
    onNavigateToPlayerSettings: () -> Unit = {},
    onNavigateToInterfaceSettings: () -> Unit = {},
    onNavigateToDownloads: () -> Unit = {},
    onNavigateToCacheSettings: () -> Unit = {},
    onAddServer: () -> Unit = {},
    onAddUser: (serverUrl: String, serverName: String?) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = viewModel { SettingsViewModel(context) }
    val uiState by viewModel.uiState.collectAsState()
    val supportedCodecs = remember(context) { getSupportedCodecsSummary(context) }
    val listState = rememberLazyListState()

    var showNetworkDialog by remember { mutableStateOf(false) }
    var editingNetworkTimeout by remember { mutableStateOf<NetworkTimeoutField?>(null) }
    var showServerSwitchDialog by remember { mutableStateOf(false) }
    var showUserSwitchDialog by remember { mutableStateOf(false) }
    var userSwitchUsers by remember { mutableStateOf<List<SavedServerUiModel>>(emptyList()) }
    var userSwitchServerName by remember { mutableStateOf<String?>(null) }
    var serverPendingRemoval by remember { mutableStateOf<SavedServerUiModel?>(null) }
    val usersForCurrentServer = remember(uiState.savedServers, uiState.serverUrl) {
        val currentServerUrl = uiState.serverUrl
        uiState.savedServers
            .filter { savedServer ->
                currentServerUrl != null &&
                    NetworkModule.sameServerUrl(savedServer.serverUrl, currentServerUrl)
            }
            .sortedWith(
                compareByDescending<SavedServerUiModel> { if (it.isActive) 1 else 0 }
                    .thenBy { it.username.lowercase() }
            )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(stringResource(R.string.settings)) },
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
                    profileImageUrl = uiState.profileImageUrl,
                    onUserClick = {
                        userSwitchUsers = usersForCurrentServer
                        userSwitchServerName = uiState.serverName
                        showUserSwitchDialog = true
                    },
                    onServerClick = { showServerSwitchDialog = true },
                    onNavigateToDownloads = onNavigateToDownloads
                )
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

    if (showServerSwitchDialog) {
        ServerSwitchDialog(
            servers = uiState.savedServers,
            isSwitching = uiState.isSwitchingServer || uiState.isRemovingServer,
            onDismiss = { showServerSwitchDialog = false },
            onAddServer = {
                showServerSwitchDialog = false
                onAddServer()
            },
            onRequestRemoveServer = { server ->
                serverPendingRemoval = server
            },
            onOpenServerUsers = { serverName, users ->
                userSwitchServerName = serverName
                userSwitchUsers = users
                showServerSwitchDialog = false
                showUserSwitchDialog = true
            },
            onServerSelected = { server ->
                viewModel.switchServer(server.id) {
                    showServerSwitchDialog = false
                }
            }
        )
    }

    if (showUserSwitchDialog) {
        UserSwitchDialog(
            users = userSwitchUsers,
            serverName = userSwitchServerName,
            isSwitching = uiState.isSwitchingServer || uiState.isRemovingServer,
            onDismiss = {
                showUserSwitchDialog = false
                userSwitchUsers = emptyList()
                userSwitchServerName = null
            },
            onAddUser = {
                showUserSwitchDialog = false
                val currentServerUrl = uiState.serverUrl?.takeIf { it.isNotBlank() }
                if (currentServerUrl != null) {
                    onAddUser(currentServerUrl, uiState.serverName)
                }
                userSwitchUsers = emptyList()
                userSwitchServerName = null
            },
            onRequestRemoveUser = { server ->
                serverPendingRemoval = server
            },
            onUserSelected = { server ->
                viewModel.switchServer(server.id) {
                    showUserSwitchDialog = false
                    userSwitchUsers = emptyList()
                    userSwitchServerName = null
                }
            }
        )
    }

    serverPendingRemoval?.let { server ->
        RemoveServerConfirmDialog(
            server = server,
            isRemoving = uiState.isRemovingServer,
            onDismiss = {
                if (!uiState.isRemovingServer) {
                    serverPendingRemoval = null
                }
            },
            onConfirm = {
                viewModel.removeServer(server.id) {
                    serverPendingRemoval = null
                }
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
    profileImageUrl: String?,
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

            if (user?.policy?.isAdministrator == true) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.settings_administrator),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
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
                }
            }
        }
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

@Composable
private fun ProfileImageLoader(
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasError by remember { mutableStateOf(false) }
    val profileRequest = remember(imageUrl) {
        imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(300)
                .build()
        }
    }

    LaunchedEffect(profileRequest) {
        hasError = false
    }
    val avatarImage = profileRequest == null || hasError

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (avatarImage) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(60.dp)
                )
            }
        } else {
            AsyncImage(
                model = profileRequest,
                contentDescription = stringResource(R.string.settings_profile_picture),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                onError = {
                    hasError = true
                }
            )
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
private fun ServerSwitchDialog(
    servers: List<SavedServerUiModel>,
    isSwitching: Boolean,
    onDismiss: () -> Unit,
    onAddServer: () -> Unit,
    onRequestRemoveServer: (SavedServerUiModel) -> Unit,
    onOpenServerUsers: (String, List<SavedServerUiModel>) -> Unit,
    onServerSelected: (SavedServerUiModel) -> Unit
) {
    val serverGroups = remember(servers) {
        servers
            .groupBy { NetworkModule.canonicalServerUrlKey(it.serverUrl) }
            .map { (_, groupedUsers) ->
                val sortedUsers = groupedUsers.sortedWith(
                    compareByDescending<SavedServerUiModel> { if (it.isActive) 1 else 0 }
                        .thenBy { it.username.lowercase() }
                )
                val activeUser = sortedUsers.firstOrNull { it.isActive }
                val primary = activeUser ?: sortedUsers.first()
                ServerGroupUiModel(
                    serverName = primary.serverName,
                    serverUrl = primary.serverUrl,
                    users = sortedUsers,
                    activeUser = activeUser
                )
            }
            .sortedWith(
                compareByDescending<ServerGroupUiModel> { if (it.activeUser != null) 1 else 0 }
                    .thenBy { it.serverName.lowercase() }
            )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text(stringResource(R.string.settings_switch_server)) },
        text = {
            if (serverGroups.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_saved_servers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 340.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    serverGroups.forEachIndexed { index, group ->
                        val hasMultipleUsers = group.users.size > 1
                        val singleUser = group.users.firstOrNull()
                        val selectSingleUser = singleUser != null && !singleUser.isActive
                        val clickGroup = !isSwitching && (hasMultipleUsers || selectSingleUser)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = clickGroup) {
                                    if (hasMultipleUsers) {
                                        onOpenServerUsers(group.serverName, group.users)
                                    } else {
                                        singleUser?.let(onServerSelected)
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = group.serverName.ifBlank { stringResource(R.string.settings_media_server) },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (group.activeUser != null) Color(0xFF22D3EE) else Color.White
                                )
                                Text(
                                    text = group.serverUrl,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.72f)
                                )
                                if (hasMultipleUsers) {
                                    Text(
                                        text = pluralStringResource(
                                            R.plurals.settings_saved_users_count,
                                            group.users.size,
                                            group.users.size
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.58f)
                                    )
                                }
                            }
                            when {
                                isSwitching && group.activeUser != null -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF22D3EE)
                                    )
                                }
                                hasMultipleUsers -> {
                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = stringResource(R.string.settings_choose_user),
                                        tint = Color.White.copy(alpha = 0.48f)
                                    )
                                }
                                singleUser?.isActive == true -> {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = stringResource(R.string.settings_active_server),
                                        tint = Color(0xFF22D3EE),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                else -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (singleUser != null) {
                                            IconButton(
                                                enabled = !isSwitching,
                                                onClick = { onRequestRemoveServer(singleUser) }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Delete,
                                                    contentDescription = stringResource(R.string.settings_remove_user),
                                                    tint = Color(0xFFFF6B6B),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Rounded.ChevronRight,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.48f)
                                        )
                                    }
                                }
                            }
                        }
                        if (index < serverGroups.lastIndex) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_close), color = Color(0xFF22D3EE))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSwitching,
                onClick = onAddServer
            ) {
                Text(stringResource(R.string.settings_add_server), color = Color(0xFFF97316))
            }
        }
    )
}

private data class ServerGroupUiModel(
    val serverName: String,
    val serverUrl: String,
    val users: List<SavedServerUiModel>,
    val activeUser: SavedServerUiModel?
)

@Composable
private fun UserSwitchDialog(
    users: List<SavedServerUiModel>,
    serverName: String?,
    isSwitching: Boolean,
    onDismiss: () -> Unit,
    onAddUser: () -> Unit,
    onRequestRemoveUser: (SavedServerUiModel) -> Unit,
    onUserSelected: (SavedServerUiModel) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text(stringResource(R.string.settings_whos_watching)) },
        text = {
            if (users.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_saved_users_for_server),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (!serverName.isNullOrBlank()) {
                        Text(
                            text = serverName,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    WhoWatchingGrid(
                        users = users,
                        isSwitching = isSwitching,
                        onUserSelected = onUserSelected,
                        onRequestRemoveUser = onRequestRemoveUser
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_close), color = Color(0xFF22D3EE))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isSwitching,
                onClick = onAddUser
            ) {
                Text(stringResource(R.string.settings_add_user), color = Color(0xFFF97316))
            }
        }
    )
}

@Composable
private fun WhoWatchingGrid(
    users: List<SavedServerUiModel>,
    isSwitching: Boolean,
    onUserSelected: (SavedServerUiModel) -> Unit,
    onRequestRemoveUser: (SavedServerUiModel) -> Unit
) {
    val rows = remember(users) { users.chunked(2) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        rows.forEach { userRow ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                userRow.forEach { user ->
                    WhoWatchingUserCard(
                        user = user,
                        isSwitching = isSwitching,
                        onUserSelected = onUserSelected,
                        onRequestRemoveUser = onRequestRemoveUser,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (userRow.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun WhoWatchingUserCard(
    user: SavedServerUiModel,
    isSwitching: Boolean,
    onUserSelected: (SavedServerUiModel) -> Unit,
    onRequestRemoveUser: (SavedServerUiModel) -> Unit,
    modifier: Modifier = Modifier
) {
    val canSelect = !isSwitching && !user.isActive

    Card(
        modifier = modifier.clickable(enabled = canSelect) { onUserSelected(user) },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101010)),
        border = BorderStroke(
            width = 1.dp,
            color = if (user.isActive) Color(0xFF22D3EE) else Color.White.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(74.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            brush = if (user.isActive) {
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF0891B2), Color(0xFF22D3EE))
                                )
                            } else {
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF334155), Color(0xFF64748B))
                                )
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = user.username.ifBlank { stringResource(R.string.settings_unknown_username) },
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(6.dp))

                Box(
                    modifier = Modifier.height(18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isSwitching && user.isActive -> {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF22D3EE)
                            )
                        }

                        user.isActive -> {
                            Text(
                                text = stringResource(R.string.settings_watching),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF22D3EE)
                            )
                        }

                        else -> {
                            Text(
                                text = stringResource(R.string.settings_tap_to_switch),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.58f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(1.dp))
            }

            if (!user.isActive) {
                IconButton(
                    enabled = !isSwitching,
                    onClick = { onRequestRemoveUser(user) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.settings_remove_user),
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
@Composable
private fun RemoveServerConfirmDialog(
    server: SavedServerUiModel,
    isRemoving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = { Text(stringResource(R.string.settings_remove_saved_account)) },
        text = {
            Text(
                text = stringResource(
                    R.string.settings_remove_saved_account_message,
                    server.username,
                    server.serverName
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        },
        confirmButton = {
            TextButton(
                enabled = !isRemoving,
                onClick = onConfirm
            ) {
                if (isRemoving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFFF6B6B)
                    )
                } else {
                    Text(stringResource(R.string.settings_remove), color = Color(0xFFFF6B6B))
                }
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isRemoving,
                onClick = onDismiss
            ) {
                Text(stringResource(R.string.cancel), color = Color.White.copy(alpha = 0.8f))
            }
        }
    )
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
