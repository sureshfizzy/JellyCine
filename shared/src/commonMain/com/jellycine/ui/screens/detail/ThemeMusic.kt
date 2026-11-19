package com.jellycine.ui.screens.detail

import androidx.compose.runtime.*
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.preferences.Preferences
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Platform audio output for theme music. */
interface ThemeMusicPlayer {
    fun play(urls: List<String>, endless: Boolean, volume: Float)
    fun setVolume(volume: Float)
    fun stop()
    fun release()
}

/**
 * Players take a linear amplitude gain, but perceived loudness is roughly logarithmic, so a linear
 * 0.5 sounds almost as loud as 1.0. Squaring the slider value approximates a perceptual taper, so
 * 50% on the UI sounds like ~half volume.
 */
fun themeMusicGain(volume: Float): Float {
    val v = volume.coerceIn(0f, 1f)
    return v * v
}

/** Everything a platform hands over to get theme music on the shared detail screen. */
class ThemeMusicSupport(
    val player: ThemeMusicPlayer,
    val mediaRepository: MediaRepository,
    val preferences: Preferences,
    val isBlocked: () -> Boolean = { false },
    val isActive: () -> Boolean = { true }
)

/** Plays the series/movie theme song while the detail screen is showing, following the user's settings. */
@Composable
fun ThemeMusicEffect(
    support: ThemeMusicSupport,
    item: BaseItemDto?,
    episodeItem: BaseItemDto? = null,
    refreshKey: Any? = null
) {
    val player = support.player
    val preferences = support.preferences
    val mode by preferences.themeMusicMode().collectAsState(initial = preferences.getThemeMusicMode())
    val volume by preferences.themeMusicVolume().collectAsState(initial = preferences.getThemeMusicVolume())
    val blocked = support.isBlocked()
    val active = support.isActive()
    val themeItemId = item?.let { it.seriesId?.takeIf(String::isNotBlank) ?: it.id }?.takeIf(String::isNotBlank)
    val playing = remember { PlayingUrls() }

    DisposableEffect(player) {
        onDispose { player.release() }
    }

    LaunchedEffect(themeItemId, mode, blocked, active, refreshKey) {
        if (themeItemId == null || mode == Preferences.THEME_MUSIC_NO || blocked || !active) {
            player.stop()
            playing.urls = null
            return@LaunchedEffect
        }

        var urls = support.mediaRepository.getThemeSongUrls(themeItemId).getOrDefault(emptyList())
        currentCoroutineContext().ensureActive()
        if (urls.isEmpty()) {
            val preloaded = listOfNotNull(item, episodeItem).firstOrNull { it.id == themeItemId }
            val fallbackUrl = support.mediaRepository.getYoutubeThemeAudioUrl(themeItemId, preloaded)
            currentCoroutineContext().ensureActive()
            if (!fallbackUrl.isNullOrBlank()) urls = listOf(fallbackUrl)
        }

        if (urls.isEmpty()) {
            player.stop()
            playing.urls = null
            return@LaunchedEffect
        }
        if (urls == playing.urls) return@LaunchedEffect

        player.stop()
        playing.urls = urls
        player.play(urls, endless = mode == Preferences.THEME_MUSIC_ENDLESS, volume = volume)
    }

    LaunchedEffect(volume) {
        player.setVolume(volume)
    }
}

private class PlayingUrls {
    var urls: List<String>? = null
}
