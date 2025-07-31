package com.jellycine.app.util.logging

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.*

class CrashHandler private constructor(private val context: Context) : Thread.UncaughtExceptionHandler {
    
    private val defaultHandler: Thread.UncaughtExceptionHandler? = Thread.getDefaultUncaughtExceptionHandler()
    
    companion object {
        private const val TAG = "CrashHandler"
        private const val CRASH_DIR = "crashes"
        private const val MAX_CRASH_FILES = 10
        
        @Volatile
        private var instance: CrashHandler? = null
        
        fun initialize(context: Context) {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = CrashHandler(context.applicationContext)
                        Thread.setDefaultUncaughtExceptionHandler(instance)
                    }
                }
            }
        }
    }
    
    override fun uncaughtException(thread: Thread, exception: Throwable) {
        try {
            // Save crash to file
            saveCrashToFile(thread, exception)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash to file", e)
        }

        defaultHandler?.uncaughtException(thread, exception)
    }
    
    private fun saveCrashToFile(thread: Thread, exception: Throwable) {
        try {
            val crashDir = File(context.filesDir, CRASH_DIR)
            if (!crashDir.exists()) {
                crashDir.mkdirs()
            }
            
            // Clean up old crash files
            cleanupOldCrashFiles(crashDir)
            
            // Create crash file
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val crashFile = File(crashDir, "crash_$timestamp.txt")
            
            FileWriter(crashFile).use { writer ->
                writer.appendLine("=== JellyCine Crash Report ===")
                writer.appendLine("Time: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}")
                writer.appendLine("Thread: ${thread.name}")
                writer.appendLine()
                
                // App info
                try {
                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    writer.appendLine("App Version: ${packageInfo.versionName}")
                    val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    }
                    writer.appendLine("Version Code: $versionCode")
                } catch (e: Exception) {
                    writer.appendLine("Failed to get app version: ${e.message}")
                }
                
                // Device info
                writer.appendLine("Device: ${android.os.Build.DEVICE}")
                writer.appendLine("Model: ${android.os.Build.MODEL}")
                writer.appendLine("Android Version: ${android.os.Build.VERSION.RELEASE}")
                writer.appendLine("API Level: ${android.os.Build.VERSION.SDK_INT}")
                writer.appendLine()
                
                // Exception details
                writer.appendLine("=== Exception ===")
                writer.appendLine("Type: ${exception.javaClass.name}")
                writer.appendLine("Message: ${exception.message}")
                writer.appendLine()
                
                // Stack trace
                writer.appendLine("=== Stack Trace ===")
                val stringWriter = StringWriter()
                val printWriter = PrintWriter(stringWriter)
                exception.printStackTrace(printWriter)
                writer.appendLine(stringWriter.toString())
                
                // Caused by chain
                var cause = exception.cause
                while (cause != null) {
                    writer.appendLine("\nCaused by: ${cause.javaClass.name}: ${cause.message}")
                    val causeStringWriter = StringWriter()
                    val causePrintWriter = PrintWriter(causeStringWriter)
                    cause.printStackTrace(causePrintWriter)
                    writer.appendLine(causeStringWriter.toString())
                    cause = cause.cause
                }
            }
            
            Log.i(TAG, "Crash saved to: ${crashFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash to file", e)
        }
    }
    
    private fun cleanupOldCrashFiles(crashDir: File) {
        try {
            val crashFiles = crashDir.listFiles()?.filter { 
                it.name.startsWith("crash_") && it.name.endsWith(".txt") 
            }?.sortedByDescending { it.lastModified() }
            
            if (crashFiles != null && crashFiles.size > MAX_CRASH_FILES) {
                crashFiles.drop(MAX_CRASH_FILES).forEach { file ->
                    try {
                        file.delete()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete old crash file: ${file.name}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cleanup old crash files", e)
        }
    }
}
