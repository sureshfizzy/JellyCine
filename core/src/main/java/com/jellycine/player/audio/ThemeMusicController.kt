package com.jellycine.player.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.jellycine.player.core.PlayerUtils
import com.jellycine.ui.screens.detail.ThemeMusicPlayer
import com.jellycine.ui.screens.detail.themeMusicGain

@UnstableApi
class ThemeMusicController(context: Context) : ThemeMusicPlayer {
    private val player = PlayerUtils.createPlayer(
        context.applicationContext,
        bufferOverride = PlayerUtils.PlaybackBufferOverride(
            minBufferMs = 2_000,
            maxBufferMs = 10_000,
            bufferForPlaybackMs = 500,
            bufferForPlaybackAfterRebufferMs = 1_500
        )
    )

    override fun play(urls: List<String>, endless: Boolean, volume: Float) {
        stop()
        if (urls.isEmpty()) return
        player.volume = themeMusicGain(volume)
        player.repeatMode = if (endless) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        player.setMediaItems(urls.map(MediaItem::fromUri))
        player.prepare()
        player.playWhenReady = true
    }

    override fun setVolume(volume: Float) {
        player.volume = themeMusicGain(volume)
    }

    override fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    override fun release() {
        player.release()
    }
}
