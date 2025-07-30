package com.jellycine.app.feature.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import com.jellycine.app.feature.player.viewmodel.PlayerViewModel

/**
 * Main Player Screen with modern UI and spatial audio support
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    mediaId: String,
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
    onBackPressed: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val playerState by viewModel.playerState.collectAsState()

    LaunchedEffect(Unit) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    // Handle back button press and restore orientation
    BackHandler {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onBackPressed?.invoke()
    }

    LaunchedEffect(mediaId) {
        viewModel.initializePlayer(context, mediaId)
    }

    DisposableEffect(Unit) {
        onDispose {
            val activity = context as? Activity
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            viewModel.releasePlayer()
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Video Player View
        key(viewModel.exoPlayer) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = true
                        controllerAutoShow = true
                        controllerHideOnTouch = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        setKeepContentOnPlayerReset(true)
                        // Set player immediately if available
                        player = viewModel.exoPlayer
                    }
                },
                update = { playerView ->
                    playerView.player = viewModel.exoPlayer
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Loading State
        if (playerState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        
        // Error State
        playerState.error?.let { error ->
            ErrorDialog(
                error = error,
                onDismiss = { viewModel.clearError() },
                onRetry = { viewModel.retryPlayback() }
            )
        }
    }
}

@Composable
private fun ErrorDialog(
    error: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback Error") },
        text = { Text(error) },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}