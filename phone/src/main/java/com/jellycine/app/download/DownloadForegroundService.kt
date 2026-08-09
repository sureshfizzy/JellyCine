package com.jellycine.app.download

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

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
                try {
                    if (!showForeground(notification)) {
                        stopSelfResult(startId)
                        return START_NOT_STICKY
                    }
                } catch (e: RuntimeException) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) throw e
                    runCatching { notificationManager.notifySummary(notification) }
                    stopSelfResult(startId)
                    return START_NOT_STICKY
                }
                ensureObservation()
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        observeJob?.cancel()
        releaseLocks()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(timeoutMillis: Int, fgsType: Int) {
        if (isForegroundRunning) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundRunning = false
        }
        observeJob?.cancel()
        observeJob = null
        releaseLocks()
        notificationManager.cancelSummary()
        stopSelf()
    }

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
                if (!showForeground(notification)) {
                    stopSelf()
                    return@collect
                }
            }
        }
    }

    private fun showForeground(notification: Notification): Boolean {
        if (!isForegroundRunning) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        DownloadNotificationContract.SUMMARY_NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    )
                } else {
                    startForeground(DownloadNotificationContract.SUMMARY_NOTIFICATION_ID, notification)
                }
            } catch (e: RuntimeException) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) throw e
                runCatching { notificationManager.notifySummary(notification) }
                return false
            }
            isForegroundRunning = true
            acquireLocks()
            return true
        }
        notificationManager.notifySummary(notification)
        return true
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "JellyCine::DownloadWakeLock"
            ).apply { acquire() }
        }
        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(
                WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "JellyCine::DownloadWifiLock"
            ).apply { acquire() }
        }
    }

    private fun releaseLocks() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        wifiLock?.let { if (it.isHeld) it.release() }
        wifiLock = null
    }

    private fun stopIfRunning() {
        observeJob?.cancel()
        observeJob = null
        notificationManager.cancelSummary()
        releaseLocks()
        if (isForegroundRunning) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundRunning = false
        }
    }

    private fun fallbackNotification(): Notification {
        return Notification.Builder(this, DownloadNotificationContract.CHANNEL_ID)
            .setSmallIcon(com.jellycine.shared.R.mipmap.ic_launcher)
            .setContentTitle(getString(com.jellycine.shared.R.string.downloads_notification_preparing))
            .setContentText(getString(com.jellycine.shared.R.string.downloads_notification_syncing_state))
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
                try {
                    ContextCompat.startForegroundService(context, startIntent)
                } catch (e: RuntimeException) {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) throw e
                }
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