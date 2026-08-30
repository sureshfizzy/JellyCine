package com.vela.app.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.vela.app.ui.activity.PlayerActivity
import com.vela.app.ui.activity.VelaActivity
import com.vela.shared.R as SharedR

class PlaybackMediaService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                PlaybackMediaSession.stop(this)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_PLAY -> PlaybackMediaSession.dispatchPlay()
            ACTION_PAUSE -> PlaybackMediaSession.dispatchPause()
            ACTION_NEXT -> PlaybackMediaSession.dispatchNext()
            ACTION_PREVIOUS -> PlaybackMediaSession.dispatchPrevious()
        }
        val notification = PlaybackMediaSession.currentNotification(this)
            ?: fallbackNotification()
        if (!showForeground(notification)) {
            stopSelf()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    override fun onTimeout(timeoutMillis: Int, fgsType: Int) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        PlaybackMediaSession.stop(this)
        stopSelf()
    }

    private fun showForeground(notification: Notification): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: RuntimeException) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) throw e
            false
        }
    }

    private fun fallbackNotification(): Notification {
        ensureChannel(this)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(SharedR.mipmap.ic_launcher)
            .setContentTitle(getString(SharedR.string.playback_notification_title))
            .setContentText(getString(SharedR.string.playback_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "playback"
        const val NOTIFICATION_ID = 14010
        const val ACTION_STOP = "com.vela.app.playback.STOP"
        const val ACTION_PLAY = "com.vela.app.playback.PLAY"
        const val ACTION_PAUSE = "com.vela.app.playback.PAUSE"
        const val ACTION_NEXT = "com.vela.app.playback.NEXT"
        const val ACTION_PREVIOUS = "com.vela.app.playback.PREVIOUS"

        fun ensureChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(SharedR.string.playback_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(SharedR.string.playback_notification_text)
                setShowBadge(false)
                setSound(null, null)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun start(context: Context) {
            val intent = Intent(context, PlaybackMediaService::class.java)
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: RuntimeException) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) throw e
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PlaybackMediaService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }

        fun actionPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, PlaybackMediaService::class.java).setAction(action)
            return PendingIntent.getService(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun contentPendingIntent(context: Context, mediaId: String?): PendingIntent {
            val launch = if (mediaId.isNullOrBlank()) {
                Intent(context, VelaActivity::class.java)
            } else {
                Intent(context, PlayerActivity::class.java).apply {
                    putExtra(PlayerActivity.EXTRA_MEDIA_ID, mediaId)
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                }
            }
            launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(
                context,
                0,
                launch,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}

internal fun buildMediaNotification(
    context: Context,
    session: MediaSessionCompat,
    title: String,
    subtitle: String?,
    playing: Boolean,
    artwork: Bitmap?
): Notification {
    PlaybackMediaService.ensureChannel(context)
    val builder = NotificationCompat.Builder(context, PlaybackMediaService.CHANNEL_ID)
        .setSmallIcon(SharedR.mipmap.ic_launcher)
        .setContentTitle(title)
        .setContentText(subtitle.orEmpty())
        .setLargeIcon(artwork)
        .setContentIntent(
            PlaybackMediaService.contentPendingIntent(context, session.controller.metadata?.getString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID))
        )
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .setOngoing(playing)
        .setStyle(
            MediaStyle()
                .setMediaSession(session.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
        )
        .addAction(
            android.R.drawable.ic_media_previous,
            context.getString(SharedR.string.player_previous_episode),
            PlaybackMediaService.actionPendingIntent(context, PlaybackMediaService.ACTION_PREVIOUS)
        )
        .addAction(
            if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            context.getString(if (playing) SharedR.string.pause else SharedR.string.play),
            PlaybackMediaService.actionPendingIntent(
                context,
                if (playing) PlaybackMediaService.ACTION_PAUSE else PlaybackMediaService.ACTION_PLAY
            )
        )
        .addAction(
            android.R.drawable.ic_media_next,
            context.getString(SharedR.string.player_next_episode),
            PlaybackMediaService.actionPendingIntent(context, PlaybackMediaService.ACTION_NEXT)
        )
    return builder.build()
}

internal fun playbackState(
    playing: Boolean,
    positionMs: Long,
    durationMs: Long,
    canSkip: Boolean
): PlaybackStateCompat {
    val actions = PlaybackStateCompat.ACTION_PLAY or
        PlaybackStateCompat.ACTION_PAUSE or
        PlaybackStateCompat.ACTION_PLAY_PAUSE or
        PlaybackStateCompat.ACTION_STOP or
        PlaybackStateCompat.ACTION_SEEK_TO
    val skipActions = if (canSkip) {
        PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
    } else {
        0L
    }
    return PlaybackStateCompat.Builder()
        .setActions(actions or skipActions)
        .setState(
            if (playing) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
            positionMs.coerceAtLeast(0L),
            if (playing) 1f else 0f
        )
        .setBufferedPosition(durationMs.coerceAtLeast(0L))
        .build()
}
