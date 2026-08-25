package com.jellycine.app.discord

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.jellycine.player.discord.DiscordRpcManager
import com.jellycine.player.discord.NowPlayingInfo
import com.jellycine.shared.R as SharedR

class DiscordRpcService : Service() {

    private lateinit var rpcManager: DiscordRpcManager
    private var isForegroundRunning = false

    override fun onCreate() {
        super.onCreate()
        rpcManager = DiscordRpcManager.getInstance(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                rpcManager.clearPresence()
                stopIfRunning()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE -> {
                val info = intent.toNowPlayingInfo() ?: run {
                    stopIfRunning()
                    stopSelf()
                    return START_NOT_STICKY
                }
                ensureForeground()
                rpcManager.updatePresence(info)
                return START_STICKY
            }
            else -> return START_NOT_STICKY
        }
    }

    override fun onDestroy() {
        rpcManager.clearPresence()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTimeout(timeoutMillis: Int, fgsType: Int) {
        if (isForegroundRunning) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundRunning = false
        }
        rpcManager.clearPresence()
        stopSelf()
    }

    private fun ensureForeground() {
        if (isForegroundRunning) return
        ensureNotificationChannel()
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            isForegroundRunning = true
        } catch (e: RuntimeException) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) throw e
            stopSelf()
        }
    }

    private fun stopIfRunning() {
        if (isForegroundRunning) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundRunning = false
        }
    }

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(SharedR.string.discord_rpc_notification_title),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(SharedR.string.discord_rpc_notification_text)
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(SharedR.mipmap.ic_launcher)
            .setContentTitle(getString(SharedR.string.discord_rpc_notification_title))
            .setContentText(getString(SharedR.string.discord_rpc_notification_text))
            .setOngoing(true)
            .build()
    }

    private fun Intent.toNowPlayingInfo(): NowPlayingInfo? {
        val mediaId = getStringExtra(EXTRA_MEDIA_ID) ?: return null
        val title = getStringExtra(EXTRA_TITLE) ?: return null
        return NowPlayingInfo(
            mediaId = mediaId,
            title = title,
            seriesName = getStringExtra(EXTRA_SERIES_NAME),
            seasonEpisodeLabel = getStringExtra(EXTRA_SEASON_EPISODE_LABEL),
            year = getIntExtra(EXTRA_YEAR, -1).takeIf { it > 0 },
            mediaType = NowPlayingInfo.MediaType.entries.getOrElse(
                getIntExtra(EXTRA_MEDIA_TYPE, 0)
            ) { NowPlayingInfo.MediaType.MOVIE },
            startTimestampMs = getLongExtra(EXTRA_START_TIMESTAMP, System.currentTimeMillis()),
            imageUrl = getStringExtra(EXTRA_IMAGE_URL)
        )
    }

    companion object {
        private const val CHANNEL_ID = "discord_rpc"
        private const val NOTIFICATION_ID = 14001
        private const val ACTION_UPDATE = "com.jellycine.app.discord.UPDATE"
        private const val ACTION_STOP = "com.jellycine.app.discord.STOP"
        private const val EXTRA_MEDIA_ID = "media_id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_SERIES_NAME = "series_name"
        private const val EXTRA_SEASON_EPISODE_LABEL = "season_episode_label"
        private const val EXTRA_YEAR = "year"
        private const val EXTRA_MEDIA_TYPE = "media_type"
        private const val EXTRA_START_TIMESTAMP = "start_timestamp"
        private const val EXTRA_IMAGE_URL = "image_url"

        fun updatePresence(context: Context, info: NowPlayingInfo) {
            val intent = Intent(context, DiscordRpcService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_MEDIA_ID, info.mediaId)
                putExtra(EXTRA_TITLE, info.title)
                putExtra(EXTRA_SERIES_NAME, info.seriesName)
                putExtra(EXTRA_SEASON_EPISODE_LABEL, info.seasonEpisodeLabel)
                info.year?.let { putExtra(EXTRA_YEAR, it) }
                putExtra(EXTRA_MEDIA_TYPE, info.mediaType.ordinal)
                putExtra(EXTRA_START_TIMESTAMP, info.startTimestampMs)
                putExtra(EXTRA_IMAGE_URL, info.imageUrl)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: RuntimeException) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) throw e
            }
        }

        fun stopPresence(context: Context) {
            val intent = Intent(context, DiscordRpcService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }
}
