package com.jellycine.player.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.jellycine.player.core.PlayerUtils

@UnstableApi
class ThemeMusicController(context: Context) {
    private val player = PlayerUtils.createPlayer(context.applicationContext)

    fun play(urls: List<String>, endless: Boolean) {
        stop()
        if (urls.isEmpty()) return
        player.repeatMode = if (endless) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        player.setMediaItems(urls.map(MediaItem::fromUri))
        player.prepare()
        player.playWhenReady = true
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    fun release() {
        player.release()
    }
}
