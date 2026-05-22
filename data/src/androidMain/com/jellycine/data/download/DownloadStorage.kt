package com.jellycine.data.download

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.DownloadStorageBehavior
import com.jellycine.data.preferences.DownloadPreferences
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.UUID

data class DownloadDestination(
    val location: String
)

class DownloadStorage(
    private val context: Context,
    private val preferences: DownloadPreferences
) {
    private val appContext = context.applicationContext

    fun createDestination(
        extension: String?,
        displayName: String,
        item: BaseItemDto?
    ): DownloadDestination {
        val safeExtension = extension
            ?.trim()
            ?.trimStart('.')
            ?.lowercase()
            ?.takeIf { it.matches(Regex("^[a-z0-9]{1,8}$")) }

        val fileName = buildDownloadFileName(
            displayName = displayName,
            item = item,
            extension = safeExtension
        )

        if (preferences.getStorageBehavior() == DownloadStorageBehavior.APP_STORAGE) {
            return createAppStorageDestination(fileName)
        }

        preferences.getDeviceDownloadsTreeUri()
            ?.let { treeUri -> createTreeDestination(treeUri, fileName, mimeTypeForExtension(safeExtension)) }
            ?.let { return it }

        return createPublicDownloadsDestination(fileName, safeExtension)
    }

    fun prepare(destination: DownloadDestination) {
        fileForLocation(destination.location)?.parentFile?.let { parent ->
            if (!parent.exists()) {
                parent.mkdirs()
            }
        }
    }

    fun exists(location: String): Boolean {
        fileForLocation(location)?.let { return it.exists() }
        val uri = location.toContentUriOrNull() ?: return false
        return runCatching {
            appContext.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true
        }.getOrDefault(false)
    }

    fun length(location: String): Long {
        fileForLocation(location)?.let { file ->
            return if (file.exists()) file.length().coerceAtLeast(0L) else 0L
        }
        val uri = location.toContentUriOrNull() ?: return 0L
        val descriptorSize = runCatching {
            appContext.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                descriptor.statSize.takeIf { it >= 0L }
            }
        }.getOrNull()
        if (descriptorSize != null) return descriptorSize

        return runCatching {
            appContext.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                    cursor.getLong(index).coerceAtLeast(0L)
                } else {
                    0L
                }
            } ?: 0L
        }.getOrDefault(0L)
    }

    suspend fun deleteWithRetry(location: String): Boolean {
        repeat(FILE_DELETE_RETRY_COUNT) { attempt ->
            if (delete(location) || !exists(location)) {
                return true
            }
            if (attempt < FILE_DELETE_RETRY_COUNT - 1) {
                delay(FILE_DELETE_RETRY_DELAY_MS)
            }
        }
        return !exists(location)
    }

    fun outputStream(location: String, append: Boolean): OutputStream {
        fileForLocation(location)?.let { file ->
            val parent = file.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            return FileOutputStream(file, append)
        }
        val uri = location.toContentUriOrNull()
            ?: throw IllegalArgumentException("Unsupported download location")
        val mode = if (append) "wa" else "wt"
        return appContext.contentResolver.openOutputStream(uri, mode)
            ?: throw IllegalStateException("Failed to open download output")
    }

    fun publish(location: String) {
        val uri = location.toContentUriOrNull() ?: return
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            appContext.contentResolver.update(uri, values, null, null)
        }
    }

    fun availableBytes(): Long {
        return when (preferences.getStorageBehavior()) {
            DownloadStorageBehavior.APP_STORAGE -> appStorageDir().also { it.mkdirs() }.usableSpace.takeIf { it > 0L }
            DownloadStorageBehavior.DEVICE_DOWNLOADS -> publicDownloadsDir().also { it.mkdirs() }.usableSpace.takeIf { it > 0L }
        } ?: appContext.filesDir.usableSpace.coerceAtLeast(0L)
    }

    private fun createTreeDestination(
        treeUriString: String,
        fileName: String,
        mimeType: String
    ): DownloadDestination? {
        val treeUri = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: return null
        return runCatching {
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri)
            )
            DocumentsContract.createDocument(
                appContext.contentResolver,
                parentUri,
                mimeType,
                fileName
            )?.let { DownloadDestination(it.toString()) }
        }.getOrNull()
    }

    private fun createAppStorageDestination(fileName: String): DownloadDestination {
        val baseDir = appStorageDir()
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        return DownloadDestination(File(baseDir, fileName).absolutePath)
    }

    private fun createPublicDownloadsDestination(
        fileName: String,
        extension: String?
    ): DownloadDestination {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeTypeForExtension(extension))
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/JellyCine")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            appContext.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            )?.let { uri ->
                return DownloadDestination(uri.toString())
            }
        }

        val baseDir = publicDownloadsDir().resolve("JellyCine")
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        return DownloadDestination(File(baseDir, fileName).absolutePath)
    }

    private fun delete(location: String): Boolean {
        fileForLocation(location)?.let { file ->
            return !file.exists() || file.delete()
        }
        val uri = location.toContentUriOrNull() ?: return false
        return runCatching {
            appContext.contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }

    private fun fileForLocation(location: String): File? {
        val uri = runCatching { Uri.parse(location) }.getOrNull()
        return when {
            uri?.scheme.isNullOrBlank() -> File(location)
            uri?.scheme == "file" -> uri.path?.let(::File)
            else -> null
        }
    }

    private fun String.toContentUriOrNull(): Uri? {
        val uri = runCatching { Uri.parse(this) }.getOrNull() ?: return null
        return uri.takeIf { it.scheme == "content" }
    }

    private fun appStorageDir(): File {
        return appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: appContext.filesDir.resolve(Environment.DIRECTORY_DOWNLOADS)
    }

    private fun publicDownloadsDir(): File {
        @Suppress("DEPRECATION")
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    private fun buildDownloadFileName(
        displayName: String,
        item: BaseItemDto?,
        extension: String?
    ): String {
        val baseName = listOfNotNull(
            item?.seriesName?.takeIf { it.isNotBlank() }?.let { seriesName ->
                val season = item.parentIndexNumber?.let { "S${it.toString().padStart(2, '0')}" }
                val episode = item.indexNumber?.let { "E${it.toString().padStart(2, '0')}" }
                val episodeCode = listOfNotNull(season, episode).joinToString("")
                listOf(seriesName, episodeCode, item.name)
                    .filter { it?.isNotBlank() == true }
                    .joinToString(" - ")
            },
            item?.name,
            displayName,
            UUID.randomUUID().toString()
        ).first { it.isNotBlank() }

        val sanitizedBase = baseName
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(96)
            .ifBlank { UUID.randomUUID().toString() }

        return if (extension != null && !sanitizedBase.endsWith(".$extension", ignoreCase = true)) {
            "$sanitizedBase.$extension"
        } else {
            sanitizedBase
        }
    }

    private fun mimeTypeForExtension(extension: String?): String {
        return when (extension) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            else -> "application/octet-stream"
        }
    }

    private companion object {
        private const val FILE_DELETE_RETRY_COUNT = 3
        private const val FILE_DELETE_RETRY_DELAY_MS = 150L
    }
}