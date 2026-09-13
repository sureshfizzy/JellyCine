package com.jellycine.logging

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-lifetime, in-memory ring buffer of recent log lines (app + mpv), read when
 * exporting logs. Stays free of any player/mpv dependency so it can live in `core`.
 */
object AppLogger {

    private const val MAX_ENTRIES = 2000

    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>(MAX_ENTRIES)

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun log(
        level: LogLevel,
        tag: String,
        message: String,
        source: LogSource = LogSource.APP
    ) {
        val entry = LogEntry(
            timeMillis = System.currentTimeMillis(),
            source = source,
            level = level,
            tag = tag,
            message = message
        )
        synchronized(lock) {
            if (buffer.size >= MAX_ENTRIES) {
                buffer.removeFirst()
            }
            buffer.addLast(entry)
            _entries.value = buffer.toList()
        }
    }
}