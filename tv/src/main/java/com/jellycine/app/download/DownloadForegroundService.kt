package com.jellycine.app.download

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class DownloadForegroundService : Service() {
    private lateinit var repository: DownloadRepository
    private lateinit var notificationManager: DownloadNotificationManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observeJob: Job? = null
    private var isForegroundRunning = false

    override fun onCreate() {
        super.onCreate()
        repository = DownloadRepositoryProvider.getInstance(applicationContext)
        notificationManager = DownloadNotificationManager(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopIfRunning()
                stopSelfResult(startId)
                return START_NOT_STICKY
            }
            ACTION_START,
            null -> {
                val tracked = repository.trackedDownloadsSnapshot()
                if (!notificationManager.hasActiveDownloads(tracked)) {
                    stopIfRunning()
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                val notification = notificationManager.buildSummaryNotification(tracked)
                    ?: fallbackNotification()
                showForeground(notification)
                ensureObservation()
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        observeJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureObservation() {
        if (observeJob?.isActive == true) return
        observeJob = serviceScope.launch {
            repository.observeTrackedDownloads().collect { tracked ->
                if (!notificationManager.hasActiveDownloads(tracked)) {
                    stopIfRunning()
                    stopSelf()
                    return@collect
                }
                val notification = notificationManager.buildSummaryNotification(tracked)
                    ?: fallbackNotification()
                showForeground(notification)
            }
        }
    }

    private fun showForeground(notification: Notification) {
        if (!isForegroundRunning) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    DownloadNotificationContract.SUMMARY_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(DownloadNotificationContract.SUMMARY_NOTIFICATION_ID, notification)
            }
            isForegroundRunning = true
            return
        }
        notificationManager.notifySummary(notification)
    }

    private fun stopIfRunning() {
        observeJob?.cancel()
        observeJob = null
        notificationManager.cancelSummary()
        if (isForegroundRunning) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundRunning = false
        }
    }

    private fun fallbackNotification(): Notification {
        return Notification.Builder(this, DownloadNotificationContract.CHANNEL_ID)
            .setSmallIcon(com.jellycine.app.R.mipmap.ic_launcher)
            .setContentTitle("Preparing downloads")
            .setContentText("Syncing download state")
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val ACTION_START = "com.jellycine.app.download.FGS_START"
        private const val ACTION_STOP = "com.jellycine.app.download.FGS_STOP"

        fun sync(context: Context, hasActiveDownloads: Boolean) {
            if (hasActiveDownloads) {
                val startIntent = Intent(context, DownloadForegroundService::class.java).apply {
                    action = ACTION_START
                }
                ContextCompat.startForegroundService(context, startIntent)
                return
            }

            val stopIntent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching {
                context.startService(stopIntent)
            }
        }
    }
}
