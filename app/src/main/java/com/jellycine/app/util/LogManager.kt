package com.jellycine.app.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.jellycine.data.datastore.DataStoreProvider
import kotlinx.coroutines.flow.first
import java.io.BufferedReader
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

class LogManager(private val context: Context) {
    
    private val dataStore: DataStore<Preferences> = DataStoreProvider.getDataStore(context)
    
    companion object {
        private const val TAG = "LogManager"
        private const val LOG_FILE_NAME = "jellycine_logs.txt"
        private const val MAX_LOG_LINES = 1000
    }
    
    /**
     * Generate comprehensive logs for debugging
     */
    suspend fun generateLogFile(): File? {
        return try {
            val logFile = File(context.cacheDir, LOG_FILE_NAME)
            val writer = FileWriter(logFile)
            
            writer.use { w ->
                // Header
                w.appendLine("=== JellyCine Debug Logs ===")
                w.appendLine("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                w.appendLine()
                
                // App Information
                writeAppInfo(w)
                w.appendLine()
                
                // Device Information
                writeDeviceInfo(w)
                w.appendLine()
                
                // User Configuration (sanitized)
                writeUserConfig(w)
                w.appendLine()
                
                // Recent App Logs
                writeAppLogs(w)
                w.appendLine()

                // Crash Logs
                writeCrashLogs(w)
                w.appendLine()

                // Network Configuration
                writeNetworkInfo(w)
            }
            
            Log.i(TAG, "Log file generated: ${logFile.absolutePath}")
            logFile
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate log file", e)
            null
        }
    }
    
    private fun writeAppInfo(writer: FileWriter) {
        writer.appendLine("=== App Information ===")
        try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            writer.appendLine("App Version: ${packageInfo.versionName}")
            writer.appendLine("Version Code: ${packageInfo.longVersionCode}")
            writer.appendLine("Package Name: ${packageInfo.packageName}")
            writer.appendLine("Target SDK: ${packageInfo.applicationInfo?.targetSdkVersion ?: "Unknown"}")
        } catch (e: PackageManager.NameNotFoundException) {
            writer.appendLine("Failed to get app info: ${e.message}")
        }
    }
    
    private fun writeDeviceInfo(writer: FileWriter) {
        writer.appendLine("=== Device Information ===")
        writer.appendLine("Device: ${Build.DEVICE}")
        writer.appendLine("Model: ${Build.MODEL}")
        writer.appendLine("Manufacturer: ${Build.MANUFACTURER}")
        writer.appendLine("Brand: ${Build.BRAND}")
        writer.appendLine("Android Version: ${Build.VERSION.RELEASE}")
        writer.appendLine("API Level: ${Build.VERSION.SDK_INT}")
        writer.appendLine("Architecture: ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        
        // Memory info
        val runtime = Runtime.getRuntime()
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val totalMemory = runtime.totalMemory() / (1024 * 1024)
        val freeMemory = runtime.freeMemory() / (1024 * 1024)
        
        writer.appendLine("Max Memory: ${maxMemory}MB")
        writer.appendLine("Total Memory: ${totalMemory}MB")
        writer.appendLine("Free Memory: ${freeMemory}MB")
        writer.appendLine("Used Memory: ${totalMemory - freeMemory}MB")
    }
    
    private suspend fun writeUserConfig(writer: FileWriter) {
        writer.appendLine("=== User Configuration ===")
        try {
            val preferences = dataStore.data.first()
            
            // Only include non-sensitive configuration
            writer.appendLine("Has Server URL: ${preferences.asMap().containsKey(stringPreferencesKey("server_url"))}")
            writer.appendLine("Has Access Token: ${preferences.asMap().containsKey(stringPreferencesKey("access_token"))}")
            writer.appendLine("Has User ID: ${preferences.asMap().containsKey(stringPreferencesKey("user_id"))}")
            writer.appendLine("Is Authenticated: ${preferences[booleanPreferencesKey("is_authenticated")] ?: false}")

            // Server URL (sanitized - only show domain)
            val serverUrl = preferences[stringPreferencesKey("server_url")]
            if (serverUrl != null) {
                try {
                    val url = java.net.URL(serverUrl)
                    writer.appendLine("Server Domain: ${url.host ?: "Unknown"}")
                    writer.appendLine("Server Port: ${if (url.port != -1) url.port else "default"}")
                    writer.appendLine("Server Protocol: ${url.protocol ?: "Unknown"}")
                } catch (e: Exception) {
                    writer.appendLine("Server URL Format: Invalid")
                }
            }
        } catch (e: Exception) {
            writer.appendLine("Failed to read user config: ${e.message}")
        }
    }
    
    private fun writeAppLogs(writer: FileWriter) {
        writer.appendLine("=== Recent App Logs ===")
        try {
            val process = Runtime.getRuntime().exec("logcat -d -t $MAX_LOG_LINES")
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))

            var lineCount = 0
            bufferedReader.useLines { lines ->
                lines.forEach { line ->
                    if (lineCount >= MAX_LOG_LINES) return@forEach

                    // Filter for app-related logs (excluding crashes which are handled separately)
                    if ((line.contains("jellycine", ignoreCase = true) ||
                        line.contains(context.packageName) ||
                        line.contains("JellyCine")) &&
                        !line.contains("E/AndroidRuntime") &&
                        !line.contains("FATAL EXCEPTION")) {
                        writer.appendLine(line)
                        lineCount++
                    }
                }
            }

            if (lineCount == 0) {
                writer.appendLine("No recent app logs found")
            }
        } catch (e: IOException) {
            writer.appendLine("Failed to read system logs: ${e.message}")
            writer.appendLine("Note: System log access may be restricted on this device")
        }
    }

    private fun writeCrashLogs(writer: FileWriter) {
        writer.appendLine("=== Crash Logs ===")
        try {
            // Get crash logs from logcat
            val process = Runtime.getRuntime().exec("logcat -d -b crash")
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))

            var crashLineCount = 0
            var foundCrashes = false

            bufferedReader.useLines { lines ->
                lines.forEach { line ->
                    if (crashLineCount >= MAX_LOG_LINES) return@forEach

                    // Look for crash-related logs
                    if (line.contains("E/AndroidRuntime") ||
                        line.contains("FATAL EXCEPTION") ||
                        line.contains("Process: ${context.packageName}") ||
                        line.contains("java.lang.") ||
                        line.contains("Caused by:") ||
                        line.contains("at ${context.packageName}") ||
                        (foundCrashes && line.trim().startsWith("at "))) {

                        writer.appendLine(line)
                        crashLineCount++
                        foundCrashes = true
                    }
                }
            }

            // Also check main logcat for recent crashes
            if (crashLineCount == 0) {
                val mainProcess = Runtime.getRuntime().exec("logcat -d -t 500")
                val mainBufferedReader = BufferedReader(InputStreamReader(mainProcess.inputStream))

                var inCrashSection = false
                mainBufferedReader.useLines { lines ->
                    lines.forEach { line ->
                        if (crashLineCount >= MAX_LOG_LINES) return@forEach

                        // Start of a crash
                        if (line.contains("FATAL EXCEPTION") &&
                            (line.contains(context.packageName) || line.contains("jellycine", ignoreCase = true))) {
                            inCrashSection = true
                            writer.appendLine(line)
                            crashLineCount++
                        }
                        // Continue crash section
                        else if (inCrashSection) {
                            writer.appendLine(line)
                            crashLineCount++

                            // End crash section when we hit a new log entry that's not part of the stack trace
                            if (!line.trim().startsWith("at ") &&
                                !line.contains("Caused by:") &&
                                !line.contains("java.lang.") &&
                                !line.trim().isEmpty()) {
                                inCrashSection = false
                            }
                        }
                    }
                }
            }

            if (crashLineCount == 0) {
                writer.appendLine("No recent crash logs found")
            } else {
                writer.appendLine("Found $crashLineCount crash-related log lines")
            }

        } catch (e: IOException) {
            writer.appendLine("Failed to read crash logs: ${e.message}")
            writer.appendLine("Note: Crash log access may be restricted on this device")
        }

        // Also check for any stored crash files in app directory
        try {
            val crashDir = File(context.filesDir, "crashes")
            if (crashDir.exists() && crashDir.isDirectory()) {
                val crashFiles = crashDir.listFiles()?.filter { it.name.endsWith(".txt") || it.name.endsWith(".log") }

                if (!crashFiles.isNullOrEmpty()) {
                    writer.appendLine("\n=== Stored Crash Files ===")
                    crashFiles.take(5).forEach { file ->
                        writer.appendLine("--- ${file.name} (${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(file.lastModified()))}) ---")
                        try {
                            val content = file.readText().take(2000) // Limit content size
                            writer.appendLine(content)
                            if (file.length() > 2000) {
                                writer.appendLine("... (truncated)")
                            }
                        } catch (e: Exception) {
                            writer.appendLine("Failed to read crash file: ${e.message}")
                        }
                        writer.appendLine()
                    }
                }
            }
        } catch (e: Exception) {
            writer.appendLine("Failed to check stored crash files: ${e.message}")
        }
    }
    
    private suspend fun writeNetworkInfo(writer: FileWriter) {
        writer.appendLine("=== Network Configuration ===")
        try {
            val preferences = dataStore.data.first()
            val serverUrl = preferences[stringPreferencesKey("server_url")]
            
            if (serverUrl != null) {
                writer.appendLine("Testing connectivity to server...")
                
                try {
                    val url = java.net.URL(serverUrl)
                    val connection = url.openConnection()
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.connect()
                    writer.appendLine("Server connectivity: SUCCESS")
                } catch (e: Exception) {
                    writer.appendLine("Server connectivity: FAILED - ${e.message}")
                }
            } else {
                writer.appendLine("No server URL configured")
            }
            
            // Network state
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                val activeNetwork = connectivityManager.activeNetwork
                val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
                writer.appendLine("Network Available: ${activeNetwork != null && networkCapabilities != null}")
                writer.appendLine("Network Type: ${when {
                    networkCapabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true -> "WiFi"
                    networkCapabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
                    networkCapabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                    else -> "Unknown"
                }}")
            } else {
                @Suppress("DEPRECATION")
                val activeNetwork = connectivityManager.activeNetworkInfo
                @Suppress("DEPRECATION")
                writer.appendLine("Network Available: ${activeNetwork?.isConnected ?: false}")
                @Suppress("DEPRECATION")
                writer.appendLine("Network Type: ${activeNetwork?.typeName ?: "Unknown"}")
            }
            
        } catch (e: Exception) {
            writer.appendLine("Failed to check network info: ${e.message}")
        }
    }
}
