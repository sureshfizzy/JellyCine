package com.jellycine.ui.screens.dashboard.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jellycine.resources.*
import com.jellycine.ui.components.common.ProfileImageLoader
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountOverview(
    userName: String?,
    serverName: String?,
    profileImageUrl: String?,
    serverTypeRaw: String?,
    canChangeUser: Boolean,
    onDismiss: () -> Unit,
    onChangeUser: () -> Unit,
    onLogout: () -> Unit,
    imageHeaders: Map<String, String> = emptyMap()
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Black,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        scrimColor = Color.Black.copy(alpha = 0.76f),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color.White.copy(alpha = 0.18f)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                ) {
                    ProfileImageLoader(
                        imageUrl = profileImageUrl,
                        serverTypeRaw = serverTypeRaw,
                        modifier = Modifier.fillMaxSize(),
                        imageHeaders = imageHeaders
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = userName?.takeIf { it.isNotBlank() }
                            ?: stringResource(Res.string.settings_unknown_user),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = serverName?.takeIf { it.isNotBlank() }
                            ?: stringResource(Res.string.settings_unknown_server),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.68f)
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.10f))

            AccountActionRow(
                icon = Icons.Rounded.Person,
                label = stringResource(Res.string.settings_change_user),
                tint = Color(0xFF22D3EE),
                enabled = canChangeUser,
                onClick = onChangeUser
            )

            HorizontalDivider(
                modifier = Modifier.padding(start = 34.dp),
                color = Color.White.copy(alpha = 0.08f)
            )

            AccountActionRow(
                icon = Icons.AutoMirrored.Rounded.ExitToApp,
                label = stringResource(Res.string.logout),
                tint = Color(0xFFFF6B6B),
                onClick = onLogout
            )
        }
    }
}

@Composable
private fun AccountActionRow(
    icon: ImageVector,
    label: String,
    tint: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) tint else Color.White.copy(alpha = 0.34f),
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.34f),
            modifier = Modifier.weight(1f)
        )
        if (!enabled) {
            Text(
                text = stringResource(Res.string.settings_unavailable),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.34f)
            )
        }
    }
}
