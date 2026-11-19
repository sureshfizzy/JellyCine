package com.jellycine.ui.screens.player

import com.jellycine.ui.screens.detail.ThemeMusicPlayer
import com.jellycine.ui.screens.detail.themeMusicGain
import platform.AVFoundation.*
import platform.Foundation.*

class IosMusicPlayer : ThemeMusicPlayer {
    private var player: AVQueuePlayer? = null
    private var observer: Any? = null

    override fun play(urls: List<String>, endless: Boolean, volume: Float) {
        stop()
        val items = urls.mapNotNull(::itemFor)
        if (items.isEmpty()) return
        val queue = AVQueuePlayer(items)
        queue.volume = themeMusicGain(volume)
        player = queue
        observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = AVPlayerItemDidPlayToEndTimeNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { notification ->
            val finished = notification?.`object` as? AVPlayerItem ?: return@addObserverForName
            val current = player ?: return@addObserverForName
            if (endless && current.items().contains(finished)) {
                val url = (finished.asset as? AVURLAsset)?.URL
                if (url != null) current.insertItem(AVPlayerItem(uRL = url), afterItem = null)
            }
        }
        queue.play()
    }

    override fun setVolume(volume: Float) {
        player?.volume = themeMusicGain(volume)
    }

    override fun stop() {
        observer?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        observer = null
        player?.pause()
        player?.removeAllItems()
        player = null
    }

    override fun release() = stop()

    private fun itemFor(url: String): AVPlayerItem? =
        NSURL.URLWithString(url)?.let { AVPlayerItem(uRL = it) }
}
