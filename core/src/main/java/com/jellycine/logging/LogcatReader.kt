package com.jellycine.logging

import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Snapshots this process' current logcat buffer as [LogEntry]s, filtering OEM/framework
 * noise and merging multi-line log calls (stack traces, dumps) back into single entries.
 */
object LogcatReader {

    suspend fun read(maxEntries: Int = 2000): List<LogEntry> = withContext(Dispatchers.IO) {
        val result = ArrayList<LogEntry>()
        var lastTag: String? = null
        var lastLevel: LogLevel? = null
        var lastTimestamp: String? = null

        try {
            val pid = Process.myPid()
            val proc = Runtime.getRuntime().exec(
                arrayOf("logcat", "-d", "-v", "time", "--pid=$pid")
            )
            proc.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    if (line.startsWith("---------")) continue
                    val p = parseLine(line) ?: continue
                    if (p.tag.startsWith("mpv")) continue // arrives via AppLogger
                    val last = result.lastOrNull()
                    val merge = last != null &&
                        p.tag == lastTag &&
                        p.level == lastLevel &&
                        (p.timestamp == lastTimestamp || isContinuation(p.message))
                    if (merge) {
                        result[result.size - 1] =
                            last!!.copy(message = last.message + "\n" + p.message)
                    } else {
                        result.add(
                            LogEntry(
                                timeMillis = p.timeMillis,
                                source = LogSource.APP,
                                level = p.level,
                                tag = p.tag,
                                message = p.message
                            )
                        )
                    }
                    lastTag = p.tag
                    lastLevel = p.level
                    lastTimestamp = p.timestamp
                }
            }
            proc.waitFor()
        } catch (e: Exception) {
            result.add(
                LogEntry(
                    timeMillis = System.currentTimeMillis(),
                    source = LogSource.APP,
                    level = LogLevel.WARN,
                    tag = "logcat",
                    message = "Logcat unavailable on this device: ${e.message}"
                )
            )
        }
        if (result.size > maxEntries) result.subList(0, result.size - maxEntries).clear()
        result
    }

    private data class ParsedLine(
        val timestamp: String,
        val timeMillis: Long,
        val level: LogLevel,
        val tag: String,
        val message: String
    )

    private fun parseLine(line: String): ParsedLine? {
        val match = LINE_REGEX.find(line)
            ?: return ParsedLine("", System.currentTimeMillis(), LogLevel.VERBOSE, "logcat", line)
        val (timestamp, levelChar, rawTag, message) = match.destructured
        val tag = rawTag.trim()
        if (isNoise(tag)) return null
        return ParsedLine(timestamp, parseMillis(timestamp), levelChar.toLogLevel(), tag, message)
    }

    private fun isNoise(tag: String): Boolean =
        tag in NOISE_TAGS || NOISE_TAG_PREFIXES.any { tag.startsWith(it, ignoreCase = true) }

    private fun isContinuation(message: String): Boolean {
        if (message.isEmpty()) return false
        if (message[0] == ' ' || message[0] == '\t') return true
        val t = message.trimStart()
        return t.startsWith("at ") ||
            t.startsWith("Caused by:") ||
            t.startsWith("Suppressed:") ||
            t.startsWith("... ") ||
            t.startsWith("#")
    }

    private fun String.toLogLevel(): LogLevel = when (this) {
        "F", "A" -> LogLevel.FATAL
        "E" -> LogLevel.ERROR
        "W" -> LogLevel.WARN
        "I" -> LogLevel.INFO
        "D" -> LogLevel.DEBUG
        else -> LogLevel.VERBOSE
    }

    private fun parseMillis(timestamp: String): Long {
        if (timestamp.isEmpty()) return System.currentTimeMillis()
        return runCatching {
            val parsed = timeFormat.parse(timestamp) ?: return System.currentTimeMillis()
            val cal = Calendar.getInstance()
            val year = cal.get(Calendar.YEAR)
            cal.time = parsed
            cal.set(Calendar.YEAR, year)
            cal.timeInMillis
        }.getOrDefault(System.currentTimeMillis())
    }

    private val LINE_REGEX =
        Regex("""^(\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d{3})\s+([VDIWEFA])/([^(]*)\(\s*\d+\):\s?(.*)$""")

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

    private val NOISE_TAGS = setOf(
        "ViewRootImplExtImpl",
        "ViewRootImpl",
        "OpenGLRenderer",
        "InsetsController",
        "InsetsSourceConsumer",
        "ImeTracker",
        "BLASTBufferQueue",
        "BufferQueueProducer",
        "SurfaceView",
        "VRI",
        "WindowOnBackDispatcher",
        "HandWritingStubImpl",
        "ViewGroup",
        "VelocityTracker",
        "Vibrator",
        "VibratorManager",
        "VibratorService",
        "SystemVibrator",
        "SystemVibratorManager"
    )

    private val NOISE_TAG_PREFIXES = listOf(
        "Oplus",
        "Oppo",
        "OnePlus",
        "ColorOs",
        "Hans",
        "Miui",
        "Xiaomi"
    )
}