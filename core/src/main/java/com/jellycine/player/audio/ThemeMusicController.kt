package com.jellycine.player.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.jellycine.player.core.PlayerUtils

@UnstableApi
class ThemeMusicController(context: Context) {
    private val player = PlayerUtils.createPlayer(context.applicationContext)

    fun play(urls: List<String>, endless: Boolean, volume: Float = 1f) {
        stop()
        if (urls.isEmpty()) return
        player.volume = perceptualGain(volume)
        player.repeatMode = if (endless) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        player.setMediaItems(urls.map(MediaItem::fromUri))
        player.prepare()
        player.playWhenReady = true
    }

    fun setVolume(volume: Float) {
        player.volume = perceptualGain(volume)
    }

    /**
     * ExoPlayer's [Player.setVolume] takes a linear amplitude gain, but perceived loudness is
     * roughly logarithmic, so a linear 0.5 sounds almost as loud as 1.0. Square the slider value
     * to approximate a perceptual taper, so 50% on the UI sounds like ~half volume.
     */
    private fun perceptualGain(volume: Float): Float {
        val v = volume.coerceIn(0f, 1f)
        return v * v
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
    }

    fun release() {
        player.release()
    }
}
