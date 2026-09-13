package com.jellycine.logging

enum class LogSource {
    APP,
    MPV
}

enum class LogLevel {
    VERBOSE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL;

    val label: String
        get() = when (this) {
            VERBOSE -> "V"
            DEBUG -> "D"
            INFO -> "I"
            WARN -> "W"
            ERROR -> "E"
            FATAL -> "F"
        }
}

data class LogEntry(
    val timeMillis: Long,
    val source: LogSource,
    val level: LogLevel,
    val tag: String,
    val message: String
)