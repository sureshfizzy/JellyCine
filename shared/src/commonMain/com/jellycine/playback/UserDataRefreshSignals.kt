package com.jellycine.playback

import kotlin.time.Clock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UserDataRefreshEvent(
    val itemId: String?,
    val played: Boolean? = null,
    val timestampMs: Long = Clock.System.now().toEpochMilliseconds()
)

object UserDataRefreshSignals {
    private val _refreshEvent = MutableStateFlow<UserDataRefreshEvent?>(null)
    val refreshEvent: StateFlow<UserDataRefreshEvent?> = _refreshEvent.asStateFlow()

    fun notifyUserDataChanged(itemId: String?, played: Boolean? = null) {
        _refreshEvent.value = UserDataRefreshEvent(
            itemId = itemId,
            played = played
        )
    }
}