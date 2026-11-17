package com.jellycine.app.ui.screens.detail

import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.BatchDownloadCandidate
import com.jellycine.data.model.BatchDownloadEstimate
import com.jellycine.data.model.ItemDownloadState
import java.util.Locale

internal fun downloadFailure(
    state: ItemDownloadState? = null,
    rawMessage: String? = null
): String {
    state?.storageShortageInfo?.let { storage ->
        val fileSize = storage.fileSizeBytes?.let(::formatStorageBytesForDialog) ?: "Unknown"
        val available = formatStorageBytesForDialog(storage.availableBytes)
        val needed = formatStorageBytesForDialog(storage.neededBytes)
        return buildString {
            appendLine("Not enough storage space on this device.")
            appendLine()
            appendLine("File size: $fileSize")
            appendLine("Available: $available")
            append("Needed: $needed")
        }
    }

    val resolvedMessage = rawMessage?.trim().takeUnless { it.isNullOrBlank() }
        ?: state?.message?.trim().takeUnless { it.isNullOrBlank() }
    return resolvedMessage ?: "Download failed. Please try again."
}

internal fun formatStorageBytesForDialog(bytes: Long): String {
    val value = bytes.coerceAtLeast(0L).toDouble()
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    return when {
        value >= gb -> String.format(Locale.US, "%.2f GB", value / gb)
        value >= mb -> String.format(Locale.US, "%.1f MB", value / mb)
        value >= kb -> String.format(Locale.US, "%.1f KB", value / kb)
        else -> "${bytes.coerceAtLeast(0L)} B"
    }
}

internal data class SeriesSeasonSelectionDialogState(
    val availableBytes: Long,
    val options: List<StorageSelectionOption>,
    val episodesBySeasonId: Map<String, List<BaseItemDto>>
) {
    companion object {
        fun fromEstimate(estimate: BatchDownloadEstimate): SeriesSeasonSelectionDialogState {
            data class SeasonGroupSummary(
                val id: String,
                val title: String,
                val subtitle: String,
                val requiredBytes: Long,
                val seasonNumber: Int?,
                val episodes: List<BaseItemDto>
            )

            val grouped = estimate.candidates
                .filter { !it.item.id.isNullOrBlank() }
                .groupBy { candidate -> seasonGroupKey(candidate.item) }

            val groupedSummaries = grouped.map { (seasonId, candidates) ->
                val orderedCandidates = candidates.sortedWith(
                    compareBy<BatchDownloadCandidate>(
                        { it.item.parentIndexNumber ?: Int.MAX_VALUE },
                        { it.item.indexNumber ?: Int.MAX_VALUE },
                        { it.item.name.orEmpty() }
                    )
                )
                val firstItem = orderedCandidates.first().item
                val seasonNumber = firstItem.parentIndexNumber
                val title = when {
                    !firstItem.seasonName.isNullOrBlank() -> firstItem.seasonName.orEmpty()
                    seasonNumber != null -> "Season $seasonNumber"
                    else -> "Season"
                }

                val episodeCount = orderedCandidates.size
                val requiredBytes = orderedCandidates.sumOf { it.remainingBytes ?: 0L }

                SeasonGroupSummary(
                    id = seasonId,
                    title = title,
                    subtitle = episodeCountLabel(episodeCount),
                    requiredBytes = requiredBytes,
                    seasonNumber = seasonNumber,
                    episodes = orderedCandidates.map { it.item }
                )
            }.sortedWith(
                compareBy<SeasonGroupSummary>({ it.seasonNumber ?: Int.MAX_VALUE }, { it.title })
            )

            return SeriesSeasonSelectionDialogState(
                availableBytes = estimate.availableBytes,
                options = groupedSummaries.map { summary ->
                    StorageSelectionOption(
                        id = summary.id,
                        title = summary.title,
                        subtitle = summary.subtitle,
                        requiredBytes = summary.requiredBytes
                    )
                },
                episodesBySeasonId = groupedSummaries.associate { it.id to it.episodes }
            )
        }
    }
}

internal fun seasonGroupKey(item: BaseItemDto): String {
    return item.seasonId?.takeIf { it.isNotBlank() }
        ?: item.parentId?.takeIf { it.isNotBlank() }
        ?: item.parentIndexNumber?.let { "season_$it" }
        ?: "season_unknown_${item.id.orEmpty()}"
}

internal fun episodeCountLabel(count: Int): String {
    return "$count episode" + if (count == 1) "" else "s"
}

