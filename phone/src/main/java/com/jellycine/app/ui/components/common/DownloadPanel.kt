package com.jellycine.app.ui.components.common

import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.jellycine.app.R
import com.jellycine.app.download.DownloadStatus
import com.jellycine.app.download.ItemDownloadState
import com.jellycine.app.download.TrackedDownload

enum class DownloadGroupState {
    IDLE,
    DOWNLOADING,
    QUEUED,
    PAUSED,
    COMPLETED
}

data class DownloadPanelState(
    val status: DownloadGroupState = DownloadGroupState.IDLE,
    val progress: Float = 0f,
    val activeItemIds: List<String> = emptyList(),
    val pausedItemIds: Set<String> = emptySet()
)

val DownloadPanelState.hasActiveDownloads: Boolean
    get() = activeItemIds.isNotEmpty()

val DownloadPanelState.canResumeDownloads: Boolean
    get() = hasActiveDownloads && activeItemIds.all(pausedItemIds::contains)

val DownloadPanelState.pausableItemIds: List<String>
    get() = activeItemIds.filterNot(pausedItemIds::contains)

enum class DownloadButtonVisualState {
    IDLE,
    QUEUING,
    DOWNLOADING,
    QUEUED,
    PAUSED,
    COMPLETED
}

fun pausedDownloadMessage(context: Context): String =
    context.getString(R.string.downloads_status_paused)

fun isPausedDownloadState(
    status: DownloadStatus,
    message: String?,
    pausedMessage: String
): Boolean {
    if (status != DownloadStatus.QUEUED) return false
    val normalizedMessage = message?.trim() ?: return false
    return normalizedMessage.equals(pausedMessage, ignoreCase = true)
}

fun isPausedDownloadState(
    state: ItemDownloadState,
    pausedMessage: String
): Boolean = isPausedDownloadState(
    status = state.status,
    message = state.message,
    pausedMessage = pausedMessage
)

fun isPausedTrackedDownload(
    entry: TrackedDownload,
    pausedMessage: String
): Boolean = isPausedDownloadState(entry.state, pausedMessage)

fun buildDownloadPanelState(
    entries: List<TrackedDownload>,
    expectedCount: Int? = null,
    pausedMessage: String
): DownloadPanelState {
    val activeEntries = entries.filter {
        it.state.status == DownloadStatus.DOWNLOADING || it.state.status == DownloadStatus.QUEUED
    }
    val pausedEntries = activeEntries.filter { isPausedTrackedDownload(it, pausedMessage) }
    val queuedEntries = activeEntries.filter {
        it.state.status == DownloadStatus.QUEUED && !isPausedTrackedDownload(it, pausedMessage)
    }
    val downloadingEntries = activeEntries.filter { it.state.status == DownloadStatus.DOWNLOADING }
    val completedCount = entries.count { it.isOfflineAvailable }

    val status = when {
        downloadingEntries.isNotEmpty() -> DownloadGroupState.DOWNLOADING
        queuedEntries.isNotEmpty() -> DownloadGroupState.QUEUED
        pausedEntries.isNotEmpty() -> DownloadGroupState.PAUSED
        expectedCount != null && expectedCount > 0 && completedCount >= expectedCount ->
            DownloadGroupState.COMPLETED
        else -> DownloadGroupState.IDLE
    }

    return DownloadPanelState(
        status = status,
        progress = downloadingEntries.maxOfOrNull(::trackedDownloadProgress) ?: 0f,
        activeItemIds = activeEntries.map { it.itemId },
        pausedItemIds = pausedEntries.mapTo(linkedSetOf()) { it.itemId }
    )
}

@Composable
fun rememberDownloadPanelState(
    entries: List<TrackedDownload>,
    expectedCount: Int? = null
): DownloadPanelState {
    val pausedMessage = stringResource(R.string.downloads_status_paused)
    return remember(entries, expectedCount, pausedMessage) {
        buildDownloadPanelState(
            entries = entries,
            expectedCount = expectedCount,
            pausedMessage = pausedMessage
        )
    }
}

@Composable
fun rememberDownloadPanelProgress(
    panelState: DownloadPanelState,
    label: String
): Float {
    return animateFloatAsState(
        targetValue = when (panelState.status) {
            DownloadGroupState.DOWNLOADING -> panelState.progress.coerceIn(0f, 0.99f)
            else -> 0f
        },
        animationSpec = tween(durationMillis = 350),
        label = label
    ).value
}

fun downloadButtonVisualState(
    panelState: DownloadPanelState,
    isQueueing: Boolean = false,
    supportsCompleted: Boolean = false
): DownloadButtonVisualState {
    return when {
        isQueueing && !panelState.hasActiveDownloads -> DownloadButtonVisualState.QUEUING
        panelState.status == DownloadGroupState.DOWNLOADING -> DownloadButtonVisualState.DOWNLOADING
        panelState.status == DownloadGroupState.QUEUED -> DownloadButtonVisualState.QUEUED
        panelState.status == DownloadGroupState.PAUSED -> DownloadButtonVisualState.PAUSED
        supportsCompleted && panelState.status == DownloadGroupState.COMPLETED ->
            DownloadButtonVisualState.COMPLETED
        else -> DownloadButtonVisualState.IDLE
    }
}

@Composable
fun DownloadLabelContent(
    icon: ImageVector,
    label: String,
    contentDescription: String = label,
    modifier: Modifier = Modifier,
    iconSize: Dp,
    fontSize: TextUnit,
    tint: Color? = null,
    textColor: Color? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = tint ?: LocalContentColor.current
        )
        Spacer(modifier = Modifier.size(6.dp))
        Text(
            text = label,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            color = textColor ?: LocalContentColor.current
        )
    }
}

@Composable
fun DownloadActionMenu(
    expanded: Boolean,
    canResume: Boolean,
    hasActiveDownloads: Boolean,
    onDismissRequest: () -> Unit,
    onPauseResume: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    DropdownMenu(
        modifier = modifier.widthIn(min = 136.dp, max = 160.dp),
        expanded = expanded && hasActiveDownloads,
        onDismissRequest = onDismissRequest
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(
                        if (canResume) R.string.resume else R.string.downloads_action_pause
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                )
            },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            onClick = onPauseResume
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.downloads_action_cancel),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp)
                )
            },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            onClick = onCancel
        )
    }
}

@Composable
fun DownloadContent(
    visualState: DownloadButtonVisualState,
    progress: Float,
    @StringRes idleLabelRes: Int,
    modifier: Modifier = Modifier,
    fontSize: TextUnit,
    iconSize: Dp,
    progressSize: Dp,
    @StringRes completedLabelRes: Int = R.string.downloads_status_downloaded
) {
    AnimatedContent(
        targetState = visualState,
        transitionSpec = {
            fadeIn(animationSpec = tween(220)) togetherWith
                fadeOut(animationSpec = tween(180))
        },
        label = "download_group_button_content"
    ) { state ->
        when (state) {
            DownloadButtonVisualState.QUEUING -> {
                Row(
                    modifier = modifier,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(progressSize),
                        strokeWidth = 2.dp,
                        color = Color(0xFF03A9F4)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = stringResource(R.string.downloads_status_queuing),
                        fontSize = fontSize,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            DownloadButtonVisualState.DOWNLOADING -> {
                Row(
                    modifier = modifier,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        progress = { progress.coerceIn(0f, 0.99f) },
                        modifier = Modifier.size(progressSize),
                        strokeWidth = 2.dp,
                        color = Color(0xFF03A9F4)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = stringResource(R.string.downloads_status_downloading_short),
                        fontSize = fontSize,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            DownloadButtonVisualState.QUEUED -> {
                DownloadLabelContent(
                    icon = Icons.Rounded.Download,
                    label = stringResource(R.string.downloads_status_queued),
                    modifier = modifier,
                    iconSize = iconSize,
                    fontSize = fontSize
                )
            }
            DownloadButtonVisualState.PAUSED -> {
                DownloadLabelContent(
                    icon = Icons.Rounded.PauseCircle,
                    label = stringResource(R.string.downloads_status_paused),
                    modifier = modifier,
                    iconSize = iconSize,
                    fontSize = fontSize,
                    tint = Color(0xFFFFC107)
                )
            }
            DownloadButtonVisualState.COMPLETED -> {
                DownloadLabelContent(
                    icon = Icons.Rounded.CheckCircle,
                    label = stringResource(completedLabelRes),
                    modifier = modifier,
                    iconSize = iconSize,
                    fontSize = fontSize,
                    tint = Color(0xFF4CAF50),
                    textColor = Color(0xFF4CAF50)
                )
            }
            DownloadButtonVisualState.IDLE -> {
                DownloadLabelContent(
                    icon = Icons.Rounded.Download,
                    label = stringResource(idleLabelRes),
                    modifier = modifier,
                    iconSize = iconSize,
                    fontSize = fontSize
                )
            }
        }
    }
}

private fun trackedDownloadProgress(entry: TrackedDownload): Float {
    val downloadedBytes = entry.state.downloadedBytes
    val totalBytes = entry.state.totalBytes
    if (downloadedBytes > 0L && totalBytes > 0L) {
        return (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    }
    return 0f
}
