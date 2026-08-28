package com.vela.app.ui.screens.dashboard.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vela.app.ui.components.common.AmoledDialogFrame
import com.vela.app.ui.components.common.amoledAuthFieldColors
import com.vela.data.repository.AuthRepository
import com.vela.shared.R

@Composable
internal fun ServerLinesSection(
    server: AuthRepository.SavedServer?,
    isBusy: Boolean,
    onAddLine: (url: String, name: String) -> Unit,
    onSwitchLine: (String) -> Unit,
    onRemoveLine: (String) -> Unit,
    onAutoSelect: () -> Unit,
    onSetAutoRoute: (Boolean) -> Unit
) {
    val lines = server?.resolvedLines().orEmpty()
    val activeLine = server?.activeLine()
    var showAddDialog by remember { mutableStateOf(false) }

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
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.settings_server_lines),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.settings_server_lines_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_server_line_auto_route),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.settings_server_line_auto_route_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = server?.autoRouteEnabled != false,
                    onCheckedChange = onSetAutoRoute,
                    enabled = !isBusy && server != null && lines.size > 1,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Color(0xFF4FD06B),
                        checkedBorderColor = Color(0xFF4FD06B)
                    )
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (lines.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_server_line_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
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

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { showAddDialog = true },
                    enabled = !isBusy && server != null
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.settings_server_line_add))
                }
                OutlinedButton(
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
                        Icon(
                            imageVector = Icons.Rounded.Speed,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        stringResource(
                            if (isBusy) {
                                R.string.settings_server_line_auto_selecting
                            } else {
                                R.string.settings_server_line_auto_select
                            }
                        )
                    )
                }
            }
        }
    }

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
            .clickable(enabled = enabled && !isActive, onClick = onSwitch)
            .padding(vertical = 8.dp),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = displayName,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isActive) {
                    Text(
                        text = " · ${stringResource(R.string.settings_server_line_current)}",
                        color = Color(0xFF4FD06B),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
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

    AmoledDialogFrame(
        dismissOnRequest = true,
        onDismiss = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF111111), RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_server_line_add),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_server_line_name)) },
                placeholder = { Text(stringResource(R.string.settings_server_line_name_placeholder)) },
                singleLine = true,
                colors = amoledAuthFieldColors()
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.settings_server_line_url)) },
                placeholder = { Text(stringResource(R.string.auth_server_url_placeholder)) },
                singleLine = true,
                colors = amoledAuthFieldColors()
            )
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel), color = Color.White.copy(alpha = 0.8f))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(url.trim(), name.trim()) },
                    enabled = url.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316))
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
internal fun ServerLineSwitchButton(
    lines: List<AuthRepository.ServerLine>,
    activeLineId: String?,
    enabled: Boolean = true,
    onSwitchLine: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (lines.size <= 1) return
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        IconButton(
            enabled = enabled,
            onClick = { expanded = true }
        ) {
            Icon(
                imageVector = Icons.Rounded.Bolt,
                contentDescription = stringResource(R.string.settings_switch_line),
                tint = Color.White
            )
        }
        ServerLineSwitchMenu(
            lines = lines,
            activeLineId = activeLineId,
            expanded = expanded,
            onDismiss = { expanded = false },
            onSwitchLine = { lineId ->
                expanded = false
                onSwitchLine(lineId)
            }
        )
    }
}

@Composable
internal fun ServerLineSwitchMenu(
    lines: List<AuthRepository.ServerLine>,
    activeLineId: String?,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSwitchLine: (String) -> Unit
) {
    val lanLabel = stringResource(R.string.settings_server_line_lan)
    val wanLabel = stringResource(R.string.settings_server_line_wan)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        Text(
            text = stringResource(R.string.settings_switch_line),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        lines.forEach { line ->
            val label = line.name.trim().ifBlank {
                if (line.isLan()) lanLabel else wanLabel
            }
            DropdownMenuItem(
                text = { Text(label) },
                trailingIcon = {
                    if (line.id == activeLineId) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color(0xFF4FD06B)
                        )
                    }
                },
                onClick = { onSwitchLine(line.id) }
            )
        }
    }
}
