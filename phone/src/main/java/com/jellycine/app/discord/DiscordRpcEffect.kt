package com.jellycine.app.discord

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.jellycine.player.core.PlayerState
import com.jellycine.player.discord.DiscordRpcManager
import com.jellycine.player.discord.NowPlayingInfo
import com.jellycine.shared.preferences.Preferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Composable
fun DiscordRpcEffect(
    playerState: StateFlow<PlayerState>,
    mediaId: String?,
    seriesName: String?,
    year: Int?,
    mediaType: NowPlayingInfo.MediaType,
    imageUrlProvider: () -> String?
) {
    val context = LocalContext.current
    val preferences = remember { Preferences(context) }
    val rpcManager = remember {
        DiscordRpcManager.getInstance(context).also { manager ->
            (context as? Activity)?.let { manager.setActivity(it) }
        }
    }
    val isEnabled = preferences.isDiscordRpcEnabled()
    val isAuthorized = rpcManager.isAuthorized()

    if (!isEnabled || !isAuthorized || mediaId == null) return

    LaunchedEffect(mediaId) {
        playerState
            .map { state ->
                PresenceData(
                    isPlaying = state.isPlaying,
                    title = state.mediaTitle,
                    seasonEpisodeLabel = state.seasonEpisodeLabel,
                    currentPositionMs = state.currentPosition
                )
            }
            .distinctUntilChanged { old, new ->
                old.isPlaying == new.isPlaying && old.title == new.title &&
                    old.seasonEpisodeLabel == new.seasonEpisodeLabel
            }
            .collect { data ->
                if (data.isPlaying && data.title.isNotBlank()) {
                    delay(500)
                    val info = NowPlayingInfo(
                        mediaId = mediaId,
                        title = data.title,
                        seriesName = seriesName,
                        seasonEpisodeLabel = data.seasonEpisodeLabel,
                        year = year,
                        mediaType = mediaType,
                        startTimestampMs = System.currentTimeMillis() - data.currentPositionMs,
                        imageUrl = imageUrlProvider()
                    )
                    DiscordRpcService.updatePresence(context, info)
                } else {
                    DiscordRpcService.stopPresence(context)
                }
            }
    }

    DisposableEffect(mediaId) {
        onDispose {
            DiscordRpcService.stopPresence(context)
        }
    }
}

private data class PresenceData(
    val isPlaying: Boolean,
    val title: String,
    val seasonEpisodeLabel: String?,
    val currentPositionMs: Long
)
