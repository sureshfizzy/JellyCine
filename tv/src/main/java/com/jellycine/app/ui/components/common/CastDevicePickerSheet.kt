package com.jellycine.app.ui.components.common

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.jellycine.app.R
import com.jellycine.app.cast.CastController
import com.jellycine.app.cast.CastRouteEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CastDevicePicker(
    isVisible: Boolean,
    onDismissRequest: () -> Unit
) {
    if (!isVisible) return

    val context = LocalContext.current
    val castState by CastController.playbackState.collectAsState()
    val routes by CastController.availableRoutes.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(context) {
        CastController.startRouteDiscovery(context)
    }

    DisposableEffect(context) {
        onDispose {
            CastController.stopRouteDiscovery()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF0E131A),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.cast_to_device),
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.cast_close_picker),
                        tint = Color.White
                    )
                }
            }

            if (castState.isConnected) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF16222E)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = castState.deviceName?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.cast_connected_device),
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = stringResource(R.string.cast_connected),
                                color = Color(0xFF8AE9C9),
                                fontSize = 12.sp
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                CastController.disconnect(context)
                                onDismissRequest()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                        ) {
                            Text(stringResource(R.string.cast_disconnect))
                        }
                    }
                }
            }

            if (routes.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF121923)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.cast_searching),
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 14.sp
                        )
                        Text(
                            text = stringResource(R.string.cast_same_wifi_hint),
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(routes, key = { it.id }) { route ->
                        CastRouteListItem(
                            route = route,
                            onClick = {
                                val connectResult = CastController.connectToRoute(context, route.id)
                                if (connectResult.isSuccess) {
                                    onDismissRequest()
                                }
                            }
                        )
                    }
                }
            }

            castState.lastError?.takeIf { it.isNotBlank() }?.let { error ->
                Text(
                    text = error,
                    color = Color(0xFFFF8989),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CastRouteListItem(
    route: CastRouteEntry,
    onClick: () -> Unit
) {
    val containerColor = when {
        route.isSelected -> Color(0xFF17362D)
        route.isEnabled -> Color(0xFF151B24)
        else -> Color(0xFF10151D)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = route.isEnabled && !route.isSelected, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Cast,
                    contentDescription = null,
                    tint = if (route.isSelected) Color(0xFF80F3C7) else Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = route.name.ifBlank { stringResource(R.string.cast_unnamed_device) },
                    color = if (route.isEnabled) Color.White else Color.White.copy(alpha = 0.5f),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val subtitle = when {
                    route.isSelected -> stringResource(R.string.cast_connected)
                    route.isConnecting -> stringResource(R.string.cast_connecting)
                    !route.isEnabled -> stringResource(R.string.settings_unavailable)
                    else -> route.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.cast_tap_to_connect)
                }

                Text(
                    text = subtitle,
                    color = if (route.isSelected) Color(0xFF98F4D6) else Color.White.copy(alpha = 0.65f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (route.isSelected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF80F3C7),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
