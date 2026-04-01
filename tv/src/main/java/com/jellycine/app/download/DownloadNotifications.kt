package com.jellycine.app.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.jellycine.app.R
import com.jellycine.app.ui.components.common.isPausedDownloadState
import com.jellycine.app.ui.components.common.pausedDownloadMessage
import kotlin.math.roundToInt

internal object DownloadNotificationContract {
    const val EXTRA_ITEM_ID = "item_id"
    const val ACTION_PAUSE = "com.jellycine.app.download.PAUSE"
    const val ACTION_RESUME = "com.jellycine.app.download.RESUME"
    const val ACTION_CANCEL = "com.jellycine.app.download.CANCEL"
    const val CHANNEL_ID = "download_progress"
    const val SUMMARY_NOTIFICATION_ID = 13001
}

class DownloadNotificationManager(private val context: Context) {
    private val notificationManager = NotificationManagerCompat.from(context)

    fun buildSummaryNotification(tracked: List<TrackedDownload>): Notification? {
        ensureChannel()
        val active = activeDownloads(tracked)
        if (active.isEmpty()) return null

        val pausedMessage = pausedDownloadMessage(context)
        val lead = selectLead(active, pausedMessage)
        val progress = (stateProgress(lead.state) * 100f).roundToInt()
        val downloading = active.count { it.state.status == DownloadStatus.DOWNLOADING }
        val queued = active.count { isWaitingState(it.state, pausedMessage) }
        val paused = active.count { isPausedDownloadState(it.state, pausedMessage) }

        val title = when {
            downloading > 0 && queued > 0 ->
                "Downloading $downloading item" +
                    if (downloading > 1) "s" else "" +
                    " ($queued queued)"
            downloading > 0 ->
                "Downloading $downloading item" + if (downloading > 1) "s" else ""
            queued > 0 ->
                "Queued $queued item" + if (queued > 1) "s" else ""
            else ->
                "$pausedMessage $paused item" + if (paused > 1) "s" else ""
        }

        val sizeText = formatSizeProgress(lead.state.downloadedBytes, lead.state.totalBytes)
        val stateText = when {
            lead.state.status == DownloadStatus.DOWNLOADING && lead.state.totalBytes > 0L -> "$progress%$sizeText"
            lead.state.status == DownloadStatus.DOWNLOADING -> "Downloading$sizeText"
            isPausedDownloadState(lead.state, pausedMessage) -> "$pausedMessage$sizeText"
            else -> context.getString(R.string.downloads_status_queued)
        }
        val content = "${lead.title}\n$stateText"

        val summaryBuilder = NotificationCompat.Builder(context, DownloadNotificationContract.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("${lead.title} - $stateText")
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress, lead.state.status != DownloadStatus.DOWNLOADING || lead.state.totalBytes <= 0L)
            .addAction(
                0,
                context.getString(R.string.downloads_action_cancel),
                actionIntent(DownloadNotificationContract.ACTION_CANCEL, lead.itemId)
            )

        val isPaused = isPausedDownloadState(lead.state, pausedMessage)
        summaryBuilder.addAction(
            0,
            context.getString(if (isPaused) R.string.resume else R.string.downloads_action_pause),
            actionIntent(
                if (isPaused) DownloadNotificationContract.ACTION_RESUME
                else DownloadNotificationContract.ACTION_PAUSE,
                lead.itemId
            )
        )
        return summaryBuilder.build()
    }

    fun notifySummary(notification: Notification) {
        notificationManager.notify(DownloadNotificationContract.SUMMARY_NOTIFICATION_ID, notification)
    }

    fun cancelSummary() {
        notificationManager.cancel(DownloadNotificationContract.SUMMARY_NOTIFICATION_ID)
    }

    fun hasActiveDownloads(tracked: List<TrackedDownload>): Boolean = activeDownloads(tracked).isNotEmpty()

    private fun activeDownloads(tracked: List<TrackedDownload>): List<TrackedDownload> {
        return tracked.filter {
            it.state.status == DownloadStatus.DOWNLOADING || it.state.status == DownloadStatus.QUEUED
        }
    }

    private fun selectLead(active: List<TrackedDownload>, pausedMessage: String): TrackedDownload {
        active.filter { it.state.status == DownloadStatus.DOWNLOADING }
            .maxByOrNull { stateProgress(it.state) }
            ?.let { return it }
        active.filter { isWaitingState(it.state, pausedMessage) }
            .minByOrNull { it.requestedAt }
            ?.let { return it }
        return active.minByOrNull { it.requestedAt } ?: active.first()
    }

    private fun stateProgress(state: ItemDownloadState): Float {
        val downloadedBytes = state.downloadedBytes
        val totalBytes = state.totalBytes
        if (downloadedBytes > 0L && totalBytes > 0L) {
            return (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        }
        return 0f
    }

    private fun isWaitingState(state: ItemDownloadState, pausedMessage: String): Boolean {
        return state.status == DownloadStatus.QUEUED &&
            !isPausedDownloadState(state, pausedMessage)
    }

    private fun actionIntent(action: String, itemId: String): PendingIntent {
        val intent = Intent(context, DownloadActionReceiver::class.java).apply {
            this.action = action
            putExtra(DownloadNotificationContract.EXTRA_ITEM_ID, itemId)
        }
        val requestCode = (action + itemId).hashCode()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val existing = manager.getNotificationChannel(DownloadNotificationContract.CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            DownloadNotificationContract.CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Offline media downloads"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun formatSizeProgress(downloaded: Long, total: Long): String {
        if (downloaded <= 0L && total <= 0L) return ""
        val downloadedMb = downloaded / (1024f * 1024f)
        if (total > 0L) {
            val totalMb = total / (1024f * 1024f)
            return String.format(" (%.0f MB/%.0f MB)", downloadedMb, totalMb)
        }
        return String.format(" (%.0f MB)", downloadedMb)
    }
}

class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra(DownloadNotificationContract.EXTRA_ITEM_ID) ?: return
        val repository = DownloadRepositoryProvider.getInstance(context.applicationContext)
        when (intent.action) {
            DownloadNotificationContract.ACTION_PAUSE -> repository.pauseDownload(itemId)
            DownloadNotificationContract.ACTION_RESUME -> repository.resumeDownload(itemId)
            DownloadNotificationContract.ACTION_CANCEL -> repository.cancelDownload(itemId)
        }
    }
}
