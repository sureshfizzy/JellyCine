package com.jellycine.app.ui.screens.dashboard.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.jellycine.app.R
import com.jellycine.app.ui.components.common.AmoledDialogFrame
import com.jellycine.app.ui.components.common.amoledAuthFieldColors
import com.jellycine.shared.ui.theme.JellyBlue
import com.jellycine.shared.ui.theme.JellyRed

@Composable
internal fun SeerrConnectionDialog(
    connectionState: SeerrUiState,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onConnect: (serverUrl: String, username: String, password: String) -> Unit,
    onDisconnect: () -> Unit,
    onRefreshStatus: () -> Unit
) {
    var serverUrlInput by rememberSaveable(connectionState.serverUrl) {
        mutableStateOf(connectionState.serverUrl.orEmpty())
    }
    var usernameInput by rememberSaveable { mutableStateOf("") }
    var passwordInput by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    val canSubmit = !isBusy &&
        serverUrlInput.isNotBlank() &&
        usernameInput.isNotBlank() &&
        passwordInput.isNotBlank()
    val showDisconnectDialog = connectionState.hasSavedConnection &&
        (connectionState.status == SeerrConnectionStatus.CONNECTED ||
            connectionState.status == SeerrConnectionStatus.CHECKING)

    AmoledDialogFrame(
        dismissOnRequest = !isBusy,
        onDismiss = onDismiss,
        topPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.settings_seerr_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    enabled = !isBusy,
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 6.dp, y = (-10).dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cancel),
                        tint = Color.White.copy(alpha = 0.84f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            SeerrStatusHeader(connectionState = connectionState)

            if (showDisconnectDialog) {
                Spacer(modifier = Modifier.height(18.dp))

                DisconnectSeerrDialogContent(
                    isBusy = isBusy,
                    onRefreshStatus = onRefreshStatus,
                    onDisconnect = onDisconnect
                )
            } else {
                Spacer(modifier = Modifier.height(24.dp))

                AuthStyledField(
                    value = serverUrlInput,
                    onValueChange = { serverUrlInput = it },
                    enabled = !isBusy,
                    label = stringResource(R.string.server_url),
                    placeholder = stringResource(R.string.settings_seerr_url_hint)
                )

                Spacer(modifier = Modifier.height(12.dp))

                AuthStyledField(
                    value = usernameInput,
                    onValueChange = { usernameInput = it },
                    enabled = !isBusy,
                    label = stringResource(R.string.username),
                    leadingIcon = Icons.Rounded.Person
                )

                Spacer(modifier = Modifier.height(12.dp))

                AuthStyledField(
                    value = passwordInput,
                    onValueChange = { passwordInput = it },
                    enabled = !isBusy,
                    label = stringResource(R.string.password),
                    leadingIcon = Icons.Rounded.Lock,
                    visualTransformation = if (showPassword) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) {
                                    Icons.Rounded.VisibilityOff
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = if (showPassword) {
                                    stringResource(R.string.auth_hide_password)
                                } else {
                                    stringResource(R.string.auth_show_password)
                                }
                            )
                        }
                    }
                )

                if (connectionState.status == SeerrConnectionStatus.ERROR &&
                    !connectionState.message.isNullOrBlank()
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = connectionState.message,
                        color = JellyRed,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = { onConnect(serverUrlInput, usernameInput, passwordInput) },
                    enabled = canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = JellyBlue,
                        contentColor = Color.White,
                        disabledContainerColor = Color(0xFF1E1E1E),
                        disabledContentColor = Color.White.copy(alpha = 0.4f)
                    )
                ) {
                    if (connectionState.status == SeerrConnectionStatus.CONNECTING) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            stringResource(
                                if (connectionState.hasSavedConnection) {
                                    R.string.settings_seerr_reconnect
                                } else {
                                    R.string.settings_seerr_connect_button
                                }
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisconnectSeerrDialogContent(
    isBusy: Boolean,
    onRefreshStatus: () -> Unit,
    onDisconnect: () -> Unit
) {
    TextButton(
        onClick = onRefreshStatus,
        enabled = !isBusy,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(
            contentColor = Color(0xFF06B6D4)
        )
    ) {
        Icon(
            imageVector = Icons.Rounded.Refresh,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.settings_seerr_refresh_status))
    }

    Spacer(modifier = Modifier.height(8.dp))

    Button(
        onClick = onDisconnect,
        enabled = !isBusy,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = JellyRed,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF1E1E1E),
            disabledContentColor = Color.White.copy(alpha = 0.4f)
        )
    ) {
        Icon(
            imageVector = Icons.Rounded.LinkOff,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(stringResource(R.string.settings_seerr_disconnect))
    }
}

@Composable
internal fun seerrSubtitle(connectionState: SeerrUiState): String {
    return when (connectionState.status) {
        SeerrConnectionStatus.CONNECTED -> stringResource(R.string.settings_seerr_connected_in_app_requests)
        SeerrConnectionStatus.CONNECTING -> stringResource(R.string.settings_seerr_connecting)
        SeerrConnectionStatus.CHECKING -> stringResource(R.string.settings_seerr_checking)
        SeerrConnectionStatus.ERROR -> connectionState.message
            ?: stringResource(R.string.settings_seerr_error)
        SeerrConnectionStatus.DISCONNECTED -> stringResource(R.string.settings_seerr_disconnected)
    }
}

internal fun seerrAccentColor(status: SeerrConnectionStatus): Color {
    return when (status) {
        SeerrConnectionStatus.CONNECTED -> Color(0xFF10B981)
        SeerrConnectionStatus.CONNECTING,
        SeerrConnectionStatus.CHECKING -> Color(0xFF0EA5E9)
        SeerrConnectionStatus.ERROR -> Color(0xFFF97316)
        SeerrConnectionStatus.DISCONNECTED -> Color(0xFF8B5CF6)
    }
}

@Composable
internal fun SeerrStatusChip(status: SeerrConnectionStatus) {
    val (label, containerColor, contentColor) = when (status) {
        SeerrConnectionStatus.CONNECTED -> Triple(
            stringResource(R.string.settings_seerr_status_connected),
            Color(0xFF10B981).copy(alpha = 0.14f),
            Color(0xFF10B981)
        )

        SeerrConnectionStatus.CONNECTING,
        SeerrConnectionStatus.CHECKING -> Triple(
            stringResource(R.string.settings_seerr_status_checking),
            Color(0xFF0EA5E9).copy(alpha = 0.14f),
            Color(0xFF0EA5E9)
        )

        SeerrConnectionStatus.ERROR -> Triple(
            stringResource(R.string.settings_seerr_status_issue),
            Color(0xFFF97316).copy(alpha = 0.14f),
            Color(0xFFF97316)
        )

        SeerrConnectionStatus.DISCONNECTED -> Triple(
            stringResource(R.string.settings_seerr_status_not_connected),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = containerColor,
            disabledLabelColor = contentColor
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = false,
            borderColor = contentColor.copy(alpha = 0.24f)
        )
    )
}

@Composable
private fun SeerrStatusHeader(
    connectionState: SeerrUiState
) {
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
                            color = seerrAccentColor(connectionState.status).copy(alpha = 0.14f),
                            shape = RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Link,
                        contentDescription = null,
                        tint = seerrAccentColor(connectionState.status),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_seerr),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = seerrSubtitle(connectionState),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.72f)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    SeerrStatusChip(connectionState.status)
                }
            }

            connectionState.serverUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { savedUrl ->
                    Text(
                        text = stringResource(R.string.settings_seerr_saved_url, savedUrl),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

            connectionState.serverVersion
                ?.takeIf { it.isNotBlank() }
                ?.let { version ->
                    Text(
                        text = stringResource(R.string.settings_seerr_version, version),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
        }
    }
}

@Composable
private fun AuthStyledField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    label: String,
    leadingIcon: ImageVector? = null,
    placeholder: String? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = leadingIcon?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null
                )
            }
        },
        placeholder = placeholder?.let { hint ->
            {
                Text(
                    text = hint,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        },
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = amoledAuthFieldColors(hasLeadingIcon = leadingIcon != null)
    )
}
