package com.jellycine.shared.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlaybackRefreshEvent(
    val itemId: String?,
    val timestampMs: Long = System.currentTimeMillis()
)

object PlaybackRefreshSignals {
    private val _latestStopEvent = MutableStateFlow<PlaybackRefreshEvent?>(null)
    val latestStopEvent: StateFlow<PlaybackRefreshEvent?> = _latestStopEvent.asStateFlow()

    fun notifyPlaybackStopped(itemId: String?) {
        _latestStopEvent.value = PlaybackRefreshEvent(itemId = itemId)
    }
}
