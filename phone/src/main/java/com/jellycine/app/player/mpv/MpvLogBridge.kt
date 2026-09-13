package com.jellycine.app.player.mpv

import com.jellycine.logging.AppLogger
import com.jellycine.logging.LogLevel
import com.jellycine.logging.LogSource
import org.jellycine.mpv.MPVLib

/**
 * Bridges libmpv's log callback into the shared [AppLogger]. Attached per [MPVLib] instance
 * in [MpvPlayerController]; lives here because libmpv is only a phone-module dependency.
 */
object MpvLogBridge : MPVLib.LogObserver {

    override fun logMessage(prefix: String, level: Int, text: String) {
        AppLogger.log(
            level = mapMpvLevel(level),
            tag = "mpv/$prefix",
            message = text.trimEnd(),
            source = LogSource.MPV
        )
    }

    private fun mapMpvLevel(level: Int): LogLevel = when (level) {
        MPVLib.MpvLogLevel.MPV_LOG_LEVEL_FATAL -> LogLevel.FATAL
        MPVLib.MpvLogLevel.MPV_LOG_LEVEL_ERROR -> LogLevel.ERROR
        MPVLib.MpvLogLevel.MPV_LOG_LEVEL_WARN -> LogLevel.WARN
        MPVLib.MpvLogLevel.MPV_LOG_LEVEL_INFO -> LogLevel.INFO
        MPVLib.MpvLogLevel.MPV_LOG_LEVEL_DEBUG -> LogLevel.DEBUG
        else -> LogLevel.VERBOSE
    }
}