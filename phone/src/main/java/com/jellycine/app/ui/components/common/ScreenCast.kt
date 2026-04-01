package com.jellycine.app.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jellycine.app.cast.CastController

@Composable
fun ScreenCastButton(
    onConnectedClick: () -> Unit,
    size: Dp = 34.dp
) {
    val context = LocalContext.current
    val castPlaybackState by CastController.playbackState.collectAsState()
    var showCastDevicePicker by remember { mutableStateOf(false) }

    LaunchedEffect(context) {
        CastController.ensureInitialized(context)
    }

    CastActionButton(
        isConnected = castPlaybackState.isConnected,
        onClick = {
            if (castPlaybackState.isConnected) {
                onConnectedClick()
            } else {
                showCastDevicePicker = true
            }
        },
        size = size
    )

    CastDevicePicker(
        isVisible = showCastDevicePicker,
        onDismissRequest = { showCastDevicePicker = false }
    )
}