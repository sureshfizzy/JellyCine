package com.jellycine.logging

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A crash report persisted on disk by the app's crash handler.
 */
data class CrashFileEntry(
    val name: String,
    val lastModified: Long,
    val content: String
)

/**
 * Read-only access to the crash reports written to `filesDir/crashes/crash_*.txt`.
 */
object CrashLogStore {
    private const val CRASH_DIR = "crashes"

    suspend fun readCrashFiles(context: Context): List<CrashFileEntry> =
        withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, CRASH_DIR)
            val files = dir.listFiles { file ->
                file.name.startsWith("crash_") && file.name.endsWith(".txt")
            } ?: return@withContext emptyList()

            files
                .sortedByDescending { it.lastModified() }
                .map { file ->
                    CrashFileEntry(
                        name = file.name,
                        lastModified = file.lastModified(),
                        content = runCatching { file.readText() }
                            .getOrElse { "Failed to read ${file.name}: ${it.message}" }
                    )
                }
        }
}