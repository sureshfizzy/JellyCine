package com.vela.app.ui.screens.dashboard.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vela.app.ui.components.common.AmoledDialogFrame
import com.vela.app.ui.components.common.amoledAuthFieldColors
import com.vela.app.ui.screens.auth.ProfileImageLoader
import com.vela.app.ui.screens.auth.RemoveServerConfirmDialog
import com.vela.data.repository.AuthRepository
import com.vela.shared.R

private val ServerCardColor = Color(0xFF16181D)
private val OnlineDotColor = Color(0xFF4FD06B)
private val OfflineDotColor = Color.White.copy(alpha = 0.28f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServersScreen(
    onServerSwitched: () -> Unit = {},
    onAddUser: (serverUrl: String, serverName: String?) -> Unit = { _, _ -> },
    reserveHomeNavigationSpace: Boolean = false
) {
    val context = LocalContext.current
    val viewModel: ServersViewModel = viewModel {
        ServersViewModel(context.applicationContext as android.app.Application)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var serverPendingRemoval by remember { mutableStateOf<AuthRepository.SavedServer?>(null) }
    var configuringServerId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(uiState.actionError) {
        val error = uiState.actionError ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error)
        viewModel.clearActionError()
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = Color.Black,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 8.dp)
            ) {
                Text(
                    text = stringResource(R.string.settings_server_label),
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFFB8C7FF),
                contentColor = Color(0xFF1B2550),
                modifier = Modifier.padding(bottom = if (reserveHomeNavigationSpace) 76.dp else 0.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.settings_add_server)
                )
            }
        }
    ) { innerPadding ->
        if (uiState.servers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.settings_servers_empty),
                    color = Color.White.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = if (reserveHomeNavigationSpace) 112.dp else 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(uiState.servers, key = { it.id }) { server ->
                    ServerAccountRow(
                        server = server,
                        isActive = server.id == uiState.activeServerId,
                        isOnline = server.id in uiState.reachableIds,
                        enabled = !uiState.isBusy,
                        onClick = {
                            viewModel.switchServer(server.id, onServerSwitched)
                        },
                        onConfigure = { configuringServerId = server.id },
                        onSwitchLine = { lineId ->
                            viewModel.switchServerLine(server.id, lineId, onServerSwitched)
                        },
                        onAddUser = { onAddUser(server.serverUrl, server.displayName()) },
                        onRemove = {
                            if (server.id != uiState.activeServerId) {
                                serverPendingRemoval = server
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddServerDialog(
            isConnecting = uiState.isConnecting,
            errorMessage = uiState.connectError,
            onDismiss = {
                if (!uiState.isConnecting) {
                    viewModel.clearConnectError()
                    showAddDialog = false
                }
            },
            onConnect = { host, https, port, path, username, password, note ->
                viewModel.addServer(
                    host = host,
                    https = https,
                    port = port,
                    path = path,
                    username = username,
                    password = password,
                    note = note,
                    onSuccess = {
                        showAddDialog = false
                        onServerSwitched()
                    }
                )
            }
        )
    }

    serverPendingRemoval?.let { server ->
        RemoveServerConfirmDialog(
            server = server,
            isRemoving = uiState.isRemoving,
            onDismiss = {
                if (!uiState.isRemoving) {
                    serverPendingRemoval = null
                }
            },
            onConfirm = {
                viewModel.removeServer(server.id)
                serverPendingRemoval = null
            }
        )
    }

    val configuringServer = uiState.servers.firstOrNull { it.id == configuringServerId }
    configuringServer?.let { server ->
        ServerConfigScreen(
            server = server,
            isBusy = uiState.isBusy,
            isSaving = uiState.isSavingConfig,
            onDismiss = {
                if (!uiState.isBusy) {
                    configuringServerId = null
                }
            },
            onSave = { note, preferStrmOriginalPath, host, https, port, path ->
                viewModel.saveServerConfig(
                    serverId = server.id,
                    note = note,
                    preferStrmOriginalPath = preferStrmOriginalPath,
                    host = host,
                    https = https,
                    port = port,
                    path = path,
                    onSuccess = { configuringServerId = null }
                )
            },
            onAddLine = { url, name -> viewModel.addServerLine(server.id, url, name) },
            onSwitchLine = { lineId ->
                viewModel.switchServerLine(server.id, lineId, onServerSwitched)
            },
            onRemoveLine = { lineId -> viewModel.removeServerLine(server.id, lineId) },
            onAutoSelect = { viewModel.autoSelectServerLine(server.id) }
        )
    }

    if (uiState.isSwitching) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(enabled = true, onClick = {}),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Color(0xFFB8C7FF),
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp)
            )
        }
    }
    }
}

@Composable
private fun ServerAccountRow(
    server: AuthRepository.SavedServer,
    isActive: Boolean,
    isOnline: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onConfigure: () -> Unit,
    onSwitchLine: (String) -> Unit,
    onAddUser: () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var lineMenuExpanded by remember { mutableStateOf(false) }
    val lines = server.resolvedLines()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(ServerCardColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center
        ) {
            ProfileImageLoader(
                imageUrl = server.profileImageUrl,
                serverTypeRaw = server.serverTypeRaw,
                modifier = Modifier.size(48.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = server.displayName().ifBlank { stringResource(R.string.settings_media_server) },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isOnline || isActive) OnlineDotColor else OfflineDotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = server.username,
                    color = Color.White.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box {
            IconButton(
                enabled = enabled,
                onClick = { menuExpanded = true }
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.settings_server_menu),
                    tint = Color.White.copy(alpha = 0.55f)
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_server_config)) },
                    onClick = {
                        menuExpanded = false
                        onConfigure()
                    }
                )
                if (lines.size > 1) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.settings_switch_line)) },
                        onClick = {
                            menuExpanded = false
                            lineMenuExpanded = true
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.settings_add_user)) },
                    onClick = {
                        menuExpanded = false
                        onAddUser()
                    }
                )
                DropdownMenuItem(
                    enabled = !isActive,
                    text = { Text(stringResource(R.string.settings_remove)) },
                    onClick = {
                        menuExpanded = false
                        onRemove()
                    }
                )
            }
            ServerLineSwitchMenu(
                lines = lines,
                activeLineId = server.activeLine()?.id,
                expanded = lineMenuExpanded,
                onDismiss = { lineMenuExpanded = false },
                onSwitchLine = { lineId ->
                    lineMenuExpanded = false
                    onSwitchLine(lineId)
                }
            )
        }
    }
}

@Composable
private fun AddServerDialog(
    isConnecting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onConnect: (
        host: String,
        https: Boolean,
        port: String,
        path: String,
        username: String,
        password: String,
        note: String
    ) -> Unit
) {
    var note by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var https by remember { mutableStateOf(false) }
    var port by remember { mutableStateOf(defaultPort(false)) }
    var path by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    AmoledDialogFrame(
        dismissOnRequest = !isConnecting,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.settings_add_server_dialog_title),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(18.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_server_note)) },
                placeholder = { Text(stringResource(R.string.settings_server_note_placeholder)) },
                singleLine = true,
                enabled = !isConnecting,
                colors = amoledAuthFieldColors()
            )
            Spacer(modifier = Modifier.height(12.dp))
            ServerAddressFields(
                host = host,
                path = path,
                port = port,
                https = https,
                enabled = !isConnecting,
                onHostChange = { value ->
                    val parsed = parseServerAddressInput(value, https, port, path)
                    host = if (value.contains("://") || '/' in value) parsed.host else value
                    https = parsed.https
                    port = parsed.port
                    if (parsed.path.isNotBlank()) {
                        path = parsed.path
                    }
                },
                onPathChange = { path = it },
                onPortChange = { port = it.filter(Char::isDigit).take(5) },
                onHttpsChange = { enabled ->
                    if (port == defaultPort(https) || port.isBlank()) {
                        port = defaultPort(enabled)
                    }
                    https = enabled
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.username)) },
                singleLine = true,
                enabled = !isConnecting,
                colors = amoledAuthFieldColors()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.password)) },
                singleLine = true,
                enabled = !isConnecting,
                visualTransformation = PasswordVisualTransformation(),
                colors = amoledAuthFieldColors()
            )
            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = errorMessage,
                    color = Color(0xFFFF8A80),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    enabled = !isConnecting,
                    onClick = onDismiss
                ) {
                    Text(stringResource(R.string.cancel), color = Color.White.copy(alpha = 0.8f))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        onConnect(host, https, port, path, username, password, note)
                    },
                    enabled = !isConnecting && host.isNotBlank() && username.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB8C7FF),
                        contentColor = Color(0xFF1B2550)
                    )
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF1B2550)
                        )
                    } else {
                        Text(stringResource(R.string.settings_server_connect))
                    }
                }
            }
        }
    }
}
