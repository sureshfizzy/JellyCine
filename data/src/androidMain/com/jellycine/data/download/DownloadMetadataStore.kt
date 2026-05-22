package com.jellycine.data.download

import android.content.Context
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.DownloadStatus
import com.jellycine.data.model.PersistedDownloadMetadata
import com.jellycine.data.network.JellyCineJson
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.concurrent.atomic.AtomicLong

class DownloadMetadataStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun read(itemId: String): PersistedDownloadMetadata? {
        val raw = prefs.getString(metadataKey(itemId), null) ?: return null
        return runCatching { JellyCineJson.decodeFromString<PersistedDownloadMetadata>(raw) }.getOrNull()
    }

    fun persist(metadata: PersistedDownloadMetadata) {
        prefs.edit().putString(metadataKey(metadata.itemId), JellyCineJson.encodeToString(metadata)).apply()
    }

    fun remove(itemId: String) {
        prefs.edit()
            .remove(downloadKey(itemId))
            .remove(metadataKey(itemId))
            .commit()
    }

    fun allTrackedItemIds(): Set<String> {
        return prefs.all.keys
            .filter { it.startsWith(METADATA_PREFIX) || it.startsWith(DOWNLOAD_PREFIX) }
            .map {
                when {
                    it.startsWith(METADATA_PREFIX) -> it.removePrefix(METADATA_PREFIX)
                    it.startsWith(DOWNLOAD_PREFIX) -> it.removePrefix(DOWNLOAD_PREFIX)
                    else -> ""
                }
            }
            .filter { it.isNotBlank() }
            .toSet()
    }

    fun downloadId(itemId: String): Long? {
        return prefs.getLong(downloadKey(itemId), -1L).takeIf { it > 0L }
    }

    fun saveDownloadId(itemId: String, downloadId: Long) {
        prefs.edit().putLong(downloadKey(itemId), downloadId).apply()
    }

    fun nextDownloadId(): Long = ID_GENERATOR.incrementAndGet()

    fun serializeItem(item: BaseItemDto?): String? {
        if (item == null) return null
        return runCatching { JellyCineJson.encodeToString(item) }.getOrNull()
    }

    fun parseItem(raw: String?, fallbackItemId: String? = null): BaseItemDto? {
        if (raw.isNullOrBlank()) return null
        return runCatching { JellyCineJson.decodeFromString<BaseItemDto>(raw) }
            .getOrNull()
            ?.let { item ->
                if (item.id.isNullOrBlank() && !fallbackItemId.isNullOrBlank()) {
                    item.copy(id = fallbackItemId)
                } else {
                    item
                }
            }
    }

    fun parseStatus(raw: String?): DownloadStatus {
        return runCatching { DownloadStatus.valueOf(raw ?: DownloadStatus.IDLE.name) }
            .getOrElse { DownloadStatus.IDLE }
    }

    private fun downloadKey(itemId: String): String = "$DOWNLOAD_PREFIX$itemId"
    private fun metadataKey(itemId: String): String = "$METADATA_PREFIX$itemId"

    private companion object {
        private const val PREFS_NAME = "jellycine_download_state"
        private const val DOWNLOAD_PREFIX = "download_item_"
        private const val METADATA_PREFIX = "download_meta_"
        private val ID_GENERATOR = AtomicLong(System.currentTimeMillis())
    }
}