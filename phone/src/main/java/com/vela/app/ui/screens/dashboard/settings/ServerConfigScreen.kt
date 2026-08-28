package com.vela.app.ui.screens.dashboard.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.vela.app.ui.components.common.amoledAuthFieldColors
import com.vela.data.repository.AuthRepository
import com.vela.shared.R

@Composable
internal fun ServerConfigScreen(
    server: AuthRepository.SavedServer,
    isBusy: Boolean,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onSave: (
        note: String,
        preferStrmOriginalPath: Boolean,
        host: String,
        https: Boolean,
        port: String,
        path: String
    ) -> Unit,
    onAddLine: (url: String, name: String) -> Unit,
    onSwitchLine: (String) -> Unit,
    onRemoveLine: (String) -> Unit,
    onAutoSelect: () -> Unit,
    onSetAutoRoute: (Boolean) -> Unit
) {
    val initialAddress = remember(server.id, server.serverUrl) {
        parseServerUrl(server.serverUrl)
    }
    var note by remember(server.id, server.note) {
        mutableStateOf(server.note.orEmpty())
    }
    var host by remember(server.id, server.serverUrl) {
        mutableStateOf(initialAddress.host)
    }
    var https by remember(server.id, server.serverUrl) {
        mutableStateOf(initialAddress.https)
    }
    var port by remember(server.id, server.serverUrl) {
        mutableStateOf(initialAddress.port.ifBlank { defaultPort(initialAddress.https) })
    }
    var path by remember(server.id, server.serverUrl) {
        mutableStateOf(initialAddress.path)
    }
    var preferStrmOriginalPath by remember(server.id, server.preferStrmOriginalPath) {
        mutableStateOf(server.preferStrmOriginalPath)
    }
    var advancedExpanded by remember { mutableStateOf(server.preferStrmOriginalPath) }

    BackHandler(enabled = !isBusy) { onDismiss() }

    Dialog(
        onDismissRequest = {
            if (!isBusy) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isBusy,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .statusBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    enabled = !isBusy,
                    onClick = onDismiss
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cancel),
                        tint = Color.White
                    )
                }
                Text(
                    text = stringResource(R.string.settings_server_config),
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFB8C7FF)
                    )
                } else {
                    TextButton(
                        enabled = !isBusy && host.isNotBlank(),
                        onClick = {
                            onSave(note, preferStrmOriginalPath, host, https, port, path)
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.save),
                            color = Color(0xFFB8C7FF),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
                ServerAddressFields(
                    host = host,
                    path = path,
                    port = port,
                    https = https,
                    enabled = !isBusy,
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
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.settings_server_login),
                    color = Color.White.copy(alpha = 0.55f),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = server.username,
                    onValueChange = {},
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.username)) },
                    singleLine = true,
                    enabled = false,
                    colors = amoledAuthFieldColors()
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_server_note)) },
                    placeholder = { Text(stringResource(R.string.settings_server_note_placeholder)) },
                    singleLine = true,
                    enabled = !isBusy,
                    colors = amoledAuthFieldColors()
                )
                Spacer(modifier = Modifier.height(20.dp))
                ServerLinesSection(
                    server = server,
                    isBusy = isBusy,
                    onAddLine = onAddLine,
                    onSwitchLine = onSwitchLine,
                    onRemoveLine = onRemoveLine,
                    onAutoSelect = onAutoSelect,
                    onSetAutoRoute = onSetAutoRoute
                )
                Spacer(modifier = Modifier.height(20.dp))
                AdvancedStrmSection(
                    expanded = advancedExpanded,
                    preferStrmOriginalPath = preferStrmOriginalPath,
                    enabled = !isBusy,
                    onExpandedChange = { advancedExpanded = it },
                    onPreferStrmChange = { preferStrmOriginalPath = it }
                )
            }
        }
    }
}

@Composable
internal fun ServerAddressFields(
    host: String,
    path: String,
    port: String,
    https: Boolean,
    enabled: Boolean,
    onHostChange: (String) -> Unit,
    onPathChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onHttpsChange: (Boolean) -> Unit
) {
    OutlinedTextField(
        value = host,
        onValueChange = onHostChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.settings_server_host)) },
        singleLine = true,
        enabled = enabled,
        colors = amoledAuthFieldColors()
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = path,
        onValueChange = onPathChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.settings_server_path)) },
        placeholder = { Text(stringResource(R.string.settings_server_path_hint)) },
        singleLine = true,
        enabled = enabled,
        colors = amoledAuthFieldColors()
    )
    Spacer(modifier = Modifier.height(12.dp))
    OutlinedTextField(
        value = port,
        onValueChange = onPortChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(R.string.settings_server_port)) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        colors = amoledAuthFieldColors()
    )
    Spacer(modifier = Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.settings_server_https),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium
        )
        Switch(
            checked = https,
            onCheckedChange = onHttpsChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF4FD06B),
                checkedBorderColor = Color(0xFF4FD06B)
            )
        )
    }
}

@Composable
private fun AdvancedStrmSection(
    expanded: Boolean,
    preferStrmOriginalPath: Boolean,
    enabled: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPreferStrmChange: (Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { onExpandedChange(!expanded) }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = stringResource(R.string.settings_server_advanced),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Icon(
                imageVector = if (expanded) {
                    Icons.Rounded.KeyboardArrowUp
                } else {
                    Icons.Rounded.KeyboardArrowDown
                },
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f)
            )
        }
        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_server_strm_original),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.settings_server_strm_original_subtitle),
                        color = Color.White.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(
                    checked = preferStrmOriginalPath,
                    onCheckedChange = onPreferStrmChange,
                    enabled = enabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4FD06B),
                        checkedBorderColor = Color(0xFF4FD06B)
                    )
                )
            }
        }
    }
}
