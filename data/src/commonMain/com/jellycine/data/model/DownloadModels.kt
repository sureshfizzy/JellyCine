package com.jellycine.data.model

import kotlinx.serialization.Serializable

enum class DownloadStatus {
    IDLE,
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

enum class DownloadStorageBehavior {
    DEVICE_DOWNLOADS,
    APP_STORAGE
}

data class ItemDownloadState(
    val status: DownloadStatus = DownloadStatus.IDLE,
    val progress: Float = 0f,
    val downloadId: Long? = null,
    val message: String? = null,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val filePath: String? = null,
    val storageShortageInfo: StorageShortageInfo? = null
)

data class StorageShortageInfo(
    val fileSizeBytes: Long? = null,
    val availableBytes: Long,
    val neededBytes: Long
)

data class BatchDownloadCandidate(
    val item: BaseItemDto,
    val remainingBytes: Long?
)

data class BatchDownloadEstimate(
    val candidates: List<BatchDownloadCandidate>,
    val availableBytes: Long,
    val requiredBytes: Long
) {
    val exceedsStorage: Boolean
        get() = requiredBytes > availableBytes
}

object DownloadItemMetadata {
    fun subtitle(item: BaseItemDto?): String? {
        if (item == null) return null
        return when (item.type) {
            "Episode" -> {
                val season = item.parentIndexNumber?.let { "S${it.toString().padStart(2, '0')}" }
                val episode = item.indexNumber?.let { "E${it.toString().padStart(2, '0')}" }
                val code = listOfNotNull(season, episode).joinToString("")
                val seriesName = item.seriesName?.takeIf { it.isNotBlank() }
                when {
                    code.isNotBlank() && !seriesName.isNullOrBlank() -> "$seriesName - $code"
                    code.isNotBlank() -> code
                    !seriesName.isNullOrBlank() -> seriesName
                    else -> item.seasonName
                }
            }
            "Movie" -> item.productionYear?.toString()
            else -> null
        }
    }
}

@Serializable
data class PersistedDownloadMetadata(
    val itemId: String,
    val title: String,
    val subtitle: String? = null,
    val mediaType: String? = null,
    val year: Int? = null,
    val requestedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val localPath: String? = null,
    val status: String = DownloadStatus.IDLE.name,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val message: String? = null,
    val downloadId: Long? = null,
    val fullItemJson: String? = null
)

data class TrackedDownload(
    val itemId: String,
    val item: BaseItemDto?,
    val title: String,
    val mediaType: String?,
    val year: Int?,
    val state: ItemDownloadState,
    val isOfflineAvailable: Boolean,
    val fileSizeBytes: Long?,
    val requestedAt: Long,
    val completedAt: Long?
)