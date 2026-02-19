package com.jellycine.app.download

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jellycine.app.R
import kotlin.math.roundToInt

private object DownloadNotificationContract {
    const val EXTRA_ITEM_ID = "item_id"
    const val ACTION_PAUSE = "com.jellycine.app.download.PAUSE"
    const val ACTION_RESUME = "com.jellycine.app.download.RESUME"
    const val ACTION_CANCEL = "com.jellycine.app.download.CANCEL"
    const val CHANNEL_ID = "download_progress"
    const val SUMMARY_NOTIFICATION_ID = 13001
}

class DownloadNotificationManager(private val context: Context) {
    private val notificationManager = NotificationManagerCompat.from(context)

    fun render(tracked: List<TrackedDownload>) {
        ensureChannel()
        if (!canPostNotifications()) return

        val active = tracked.filter {
            it.state.status == DownloadStatus.DOWNLOADING || it.state.status == DownloadStatus.QUEUED
        }

        if (active.isEmpty()) {
            notificationManager.cancel(DownloadNotificationContract.SUMMARY_NOTIFICATION_ID)
            return
        }

        val lead = active.first()
        val progress = (lead.state.progress.coerceIn(0f, 1f) * 100f).roundToInt()
        val isPaused = lead.state.message == "Paused"
        val title = if (isPaused) {
            "Paused ${active.size} item" + if (active.size > 1) "s" else ""
        } else {
            "Downloading ${active.size} item" + if (active.size > 1) "s" else ""
        }
        val sizeText = formatSizeProgress(lead.state.downloadedBytes, lead.state.totalBytes)
        val content = "${lead.title}\n$progress%$sizeText"

        val summaryBuilder = NotificationCompat.Builder(context, DownloadNotificationContract.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText("${lead.title} - $progress%")
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress, lead.state.totalBytes <= 0L)
            .addAction(
                0,
                "Cancel",
                actionIntent(DownloadNotificationContract.ACTION_CANCEL, lead.itemId)
            )

        if (isPaused) {
            summaryBuilder.addAction(
                0,
                "Resume",
                actionIntent(DownloadNotificationContract.ACTION_RESUME, lead.itemId)
            )
        } else {
            summaryBuilder.addAction(
                0,
                "Pause",
                actionIntent(DownloadNotificationContract.ACTION_PAUSE, lead.itemId)
            )
        }

        notificationManager.notify(DownloadNotificationContract.SUMMARY_NOTIFICATION_ID, summaryBuilder.build())
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

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
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
