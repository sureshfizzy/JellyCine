package com.vela.app.playback

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Build
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object PlaybackMediaSession {

    interface Controller {
        fun play()
        fun pause()
        fun seekTo(positionMs: Long)
        fun skipToNext()
        fun skipToPrevious()
        fun stop()
    }

    @Volatile
    private var session: MediaSessionCompat? = null
    @Volatile
    private var controller: Controller? = null
    @Volatile
    private var lastNotification: android.app.Notification? = null
    @Volatile
    private var lastArtworkUrl: String? = null
    @Volatile
    private var lastArtwork: Bitmap? = null
    @Volatile
    private var lastMediaId: String? = null
    @Volatile
    private var lastPlaying: Boolean = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var artworkJob: Job? = null

    fun attach(context: Context, controller: Controller) {
        this.controller = controller
        val app = context.applicationContext
        if (session == null) {
            session = MediaSessionCompat(app, "VelaPlayback").apply {
                setCallback(sessionCallback)
                setFlags(
                    MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
                )
                setPlaybackToLocal(AudioManager.STREAM_MUSIC)
                setSessionActivity(
                    PlaybackMediaService.contentPendingIntent(app, lastMediaId)
                )
                isActive = true
            }
        }
        PlaybackMediaService.start(app)
    }

    fun detach(context: Context, controller: Controller) {
        if (this.controller !== controller) return
        this.controller = null
        stop(context)
    }

    fun dispatchPlay() {
        controller?.play()
    }

    fun dispatchPause() {
        controller?.pause()
    }

    fun dispatchNext() {
        controller?.skipToNext()
    }

    fun dispatchPrevious() {
        controller?.skipToPrevious()
    }

    fun currentNotification(context: Context): android.app.Notification? {
        return lastNotification ?: session?.let { active ->
            buildMediaNotification(
                context = context,
                session = active,
                title = context.getString(com.vela.shared.R.string.playback_notification_title),
                subtitle = null,
                playing = false,
                artwork = lastArtwork
            )
        }
    }

    fun update(
        context: Context,
        mediaId: String?,
        title: String,
        subtitle: String?,
        durationMs: Long,
        positionMs: Long,
        playing: Boolean,
        artworkUrl: String?,
        canSkip: Boolean
    ) {
        val app = context.applicationContext
        val active = session ?: return
        lastMediaId = mediaId
        lastPlaying = playing
        active.setSessionActivity(PlaybackMediaService.contentPendingIntent(app, mediaId))
        active.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId.orEmpty())
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, subtitle.orEmpty())
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, subtitle.orEmpty())
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs.coerceAtLeast(0L))
                .apply {
                    lastArtwork?.let { bitmap ->
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                        putBitmap(MediaMetadataCompat.METADATA_KEY_ART, bitmap)
                    }
                }
                .build()
        )
        active.setPlaybackState(
            playbackState(
                playing = playing,
                positionMs = positionMs,
                durationMs = durationMs,
                canSkip = canSkip
            )
        )
        active.isActive = true
        lastNotification = buildMediaNotification(
            context = app,
            session = active,
            title = title,
            subtitle = subtitle,
            playing = playing,
            artwork = lastArtwork
        )
        lastNotification?.let { notification ->
            val manager = app.getSystemService(android.app.NotificationManager::class.java)
            manager.notify(PlaybackMediaService.NOTIFICATION_ID, notification)
        }
        PlaybackMediaService.start(app)
        if (artworkUrl != lastArtworkUrl) {
            lastArtworkUrl = artworkUrl
            loadArtwork(app, artworkUrl, mediaId, title, subtitle, durationMs, positionMs, playing, canSkip)
        }
    }

    fun stop(context: Context) {
        artworkJob?.cancel()
        artworkJob = null
        lastArtworkUrl = null
        lastArtwork = null
        lastNotification = null
        lastMediaId = null
        lastPlaying = false
        session?.let { active ->
            active.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_STOPPED, 0L, 0f)
                    .build()
            )
            active.isActive = false
            active.release()
        }
        session = null
        val app = context.applicationContext
        app.getSystemService(android.app.NotificationManager::class.java)
            .cancel(PlaybackMediaService.NOTIFICATION_ID)
        runCatching { app.stopService(Intent(app, PlaybackMediaService::class.java)) }
    }

    private fun loadArtwork(
        context: Context,
        url: String?,
        mediaId: String?,
        title: String,
        subtitle: String?,
        durationMs: Long,
        positionMs: Long,
        playing: Boolean,
        canSkip: Boolean
    ) {
        artworkJob?.cancel()
        if (url.isNullOrBlank()) {
            lastArtwork = null
            return
        }
        artworkJob = scope.launch {
            val bitmap = withContext(Dispatchers.IO) { decodeBitmap(url) }
            if (url != lastArtworkUrl) return@launch
            lastArtwork = bitmap
            if (bitmap != null) {
                update(
                    context = context,
                    mediaId = mediaId,
                    title = title,
                    subtitle = subtitle,
                    durationMs = durationMs,
                    positionMs = positionMs,
                    playing = playing,
                    artworkUrl = url,
                    canSkip = canSkip
                )
            }
        }
    }

    private fun decodeBitmap(url: String): Bitmap? {
        return runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.connectTimeout = 5_000
            connection.readTimeout = 5_000
            connection.instanceFollowRedirects = true
            connection.inputStream.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            controller?.play()
        }

        override fun onPause() {
            controller?.pause()
        }

        override fun onStop() {
            controller?.stop()
        }

        override fun onSeekTo(pos: Long) {
            controller?.seekTo(pos)
        }

        override fun onSkipToNext() {
            controller?.skipToNext()
        }

        override fun onSkipToPrevious() {
            controller?.skipToPrevious()
        }

        override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
            val event = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
                @Suppress("DEPRECATION")
                mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            } ?: return super.onMediaButtonEvent(mediaButtonEvent)
            if (event.action != KeyEvent.ACTION_DOWN) return true
            when (event.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY -> controller?.play()
                KeyEvent.KEYCODE_MEDIA_PAUSE -> controller?.pause()
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> {
                    if (lastPlaying) controller?.pause() else controller?.play()
                }
                KeyEvent.KEYCODE_MEDIA_NEXT -> controller?.skipToNext()
                KeyEvent.KEYCODE_MEDIA_PREVIOUS -> controller?.skipToPrevious()
                KeyEvent.KEYCODE_MEDIA_STOP -> controller?.stop()
                else -> return super.onMediaButtonEvent(mediaButtonEvent)
            }
            return true
        }
    }
}
