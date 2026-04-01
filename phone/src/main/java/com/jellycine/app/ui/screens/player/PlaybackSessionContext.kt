package com.jellycine.app.ui.screens.player

internal enum class PlayMethod(
    val displayName: String,
    val reportValue: String
) {
    DIRECT_PLAY(displayName = "Direct Play", reportValue = "DirectPlay"),
    DIRECT_STREAM(displayName = "Direct Stream", reportValue = "DirectStream"),
    TRANSCODE(displayName = "Transcode", reportValue = "Transcode"),
    OFFLINE(displayName = "Offline", reportValue = "DirectPlay")
}

internal data class PlaybackSessionContext(
    val mediaId: String? = null,
    val playSessionId: String? = null,
    val mediaSourceId: String? = null,
    val mediaSourceContainer: String? = null,
    val mediaSourceBitrateKbps: Int? = null,
    val playMethod: PlayMethod = PlayMethod.DIRECT_PLAY,
    val isOfflinePlayback: Boolean = false
)
