package com.vela.app.ui.screens.dashboard.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vela.data.repository.AuthRepository
import com.vela.shared.R

@Composable
internal fun ServerLinesDialog(
    server: AuthRepository.SavedServer?,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onAddLine: (url: String, name: String) -> Unit,
    onSwitchLine: (String) -> Unit,
    onRemoveLine: (String) -> Unit,
    onAutoSelect: () -> Unit
) {
    val lines = server?.resolvedLines().orEmpty()
    val activeLine = server?.activeLine()
    var showAddDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111111),
        title = {
            Text(stringResource(R.string.settings_server_lines), color = Color.White)
        },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.settings_server_lines_subtitle),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(12.dp))
                if (lines.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_server_line_empty),
                        color = Color.White.copy(alpha = 0.7f)
                    )
                } else {
                    lines.forEach { line ->
                        ServerLineRow(
                            line = line,
                            isActive = line.id == activeLine?.id,
                            canRemove = lines.size > 1,
                            enabled = !isBusy,
                            onSwitch = { onSwitchLine(line.id) },
                            onRemove = { onRemoveLine(line.id) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = { showAddDialog = true },
                    enabled = !isBusy && server != null
                ) {
                    Text(stringResource(R.string.settings_server_line_add), color = Color(0xFFF97316))
                }
                TextButton(
                    onClick = onAutoSelect,
                    enabled = !isBusy && lines.size > 1
                ) {
                    if (isBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = Color.White
                        )
                    } else {
                        Text(stringResource(R.string.settings_server_line_auto_select), color = Color.White)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.settings_close), color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    )

    if (showAddDialog) {
        AddServerLineDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { url, name ->
                onAddLine(url, name)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun ServerLineRow(
    line: AuthRepository.ServerLine,
    isActive: Boolean,
    canRemove: Boolean,
    enabled: Boolean,
    onSwitch: () -> Unit,
    onRemove: () -> Unit
) {
    val displayName = line.name.ifBlank {
        stringResource(
            when {
                line.isLan() -> R.string.settings_server_line_lan
                else -> R.string.settings_server_line_wan
            }
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !isActive, onClick = onSwitch),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isActive) Icons.Rounded.CheckCircle else Icons.Rounded.Router,
            contentDescription = null,
            tint = if (isActive) Color(0xFF4FD06B) else Color.White.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isActive) {
                    "$displayName · ${stringResource(R.string.settings_server_line_current)}"
                } else {
                    displayName
                },
                color = if (isActive) Color(0xFF4FD06B) else Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = line.url,
                color = Color.White.copy(alpha = 0.65f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (canRemove) {
            IconButton(onClick = onRemove, enabled = enabled) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = stringResource(R.string.settings_server_line_remove),
                    tint = Color(0xFFFF6B6B)
                )
            }
        }
    }
}

@Composable
private fun AddServerLineDialog(
    onDismiss: () -> Unit,
    onConfirm: (url: String, name: String) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF111111),
        title = {
            Text(stringResource(R.string.settings_server_line_add), color = Color.White)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_server_line_name)) },
                    placeholder = { Text(stringResource(R.string.settings_server_line_name_placeholder)) },
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_server_line_url)) },
                    placeholder = { Text(stringResource(R.string.auth_server_url_placeholder)) },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(url.trim(), name.trim()) },
                enabled = url.trim().isNotEmpty()
            ) {
                Text(stringResource(R.string.save), color = Color(0xFFF97316))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = Color.White.copy(alpha = 0.8f))
            }
        }
    )
}
