package com.jellycine.app.ui.screens.player.mpv

import android.content.Context
import android.view.Surface
import com.jellycine.player.preferences.PlayerPreferences
import dev.jdtech.mpv.MPVLib
import dev.jdtech.mpv.MPVLib.MpvEvent
import dev.jdtech.mpv.MPVLib.MpvFormat

class MpvPlayerController(
    context: Context,
    private val hardwareDecoding: String,
    private val videoOutput: String,
    private val audioOutput: String,
    private val listener: Listener
) : MPVLib.EventObserver {

    interface Listener {
        fun onBuffering()
        fun onReady()
        fun onEnded()
    }

    private val mpv = MPVLib.create(context.applicationContext)
        ?: error("MPVLib.create() returned null")
    private var released = false
    private var pendingSeekMs: Long? = null
    private var ready = false
    private var durationMs: Long = 0L
    private var positionMs: Long = 0L
    private var playWhenReady = true
    private var pendingSubtitleUrls: List<String> = emptyList()
    private var pendingAudioTrackId: String? = null
    private var pendingSubtitleTrackId: String? = null
    private var pendingSelectedSubtitleUrl: String? = null
    private val playerPreferences = PlayerPreferences(context.applicationContext)

    val isPlaying: Boolean
        get() = ready && playWhenReady

    val currentPosition: Long
        get() = positionMs

    val duration: Long
        get() = durationMs

    init {
        configureMpv()
        mpv.init()
        mpv.addObserver(this)
        mpv.observeProperty("time-pos", MpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("duration", MpvFormat.MPV_FORMAT_DOUBLE)
        mpv.observeProperty("paused-for-cache", MpvFormat.MPV_FORMAT_FLAG)
        mpv.observeProperty("eof-reached", MpvFormat.MPV_FORMAT_FLAG)
    }

    fun load(
        url: String,
        subtitleUrls: List<String>,
        audioTrackId: String?,
        subtitleTrackId: String?,
        selectedSubtitleUrl: String?,
        startPositionMs: Long?,
        startPlayback: Boolean
    ) {
        if (released) return
        ready = false
        playWhenReady = startPlayback
        pendingSeekMs = startPositionMs?.takeIf { it > 0L }
        pendingSubtitleUrls = subtitleUrls
        pendingAudioTrackId = audioTrackId
        pendingSubtitleTrackId = subtitleTrackId
        pendingSelectedSubtitleUrl = selectedSubtitleUrl
        mpv.setPropertyBoolean("pause", true)
        listener.onBuffering()
        mpv.command(arrayOf("loadfile", url, "replace"))
    }

    fun attachSurface(surface: Surface, width: Int, height: Int) {
        if (released) return
        mpv.attachSurface(surface)
        mpv.setOptionString("force-window", "yes")
        mpv.setOptionString("vo", videoOutput)
        if (width > 0 && height > 0) {
            mpv.setPropertyString("android-surface-size", "${width}x$height")
        }
    }

    fun resizeSurface(width: Int, height: Int) {
        if (!released && width > 0 && height > 0) {
            mpv.setPropertyString("android-surface-size", "${width}x$height")
        }
    }

    fun setZoomMode(enabled: Boolean) {
        if (released) return
        if (enabled) {
            mpv.setOptionString("panscan", "1")
            mpv.setOptionString("sub-use-margins", "yes")
            mpv.setOptionString("sub-ass-force-margins", "yes")
        } else {
            mpv.setOptionString("panscan", "0")
            mpv.setOptionString("sub-use-margins", "no")
            mpv.setOptionString("sub-ass-force-margins", "no")
        }
    }

    fun applySubtitlePreferences() {
        if (released) return
        mpv.setOptionString("sub-ass-override", "strip")
        mpv.setOptionString("sub-scale", subtitleScale(playerPreferences.getSubtitleTextSize()))
        mpv.setOptionString(
            "sub-color",
            mpvColor(
                color = playerPreferences.getSubtitleTextColor(),
                opacityPercent = playerPreferences.getSubtitleTextOpacityPercent()
            )
        )
        mpv.setOptionString(
            "sub-back-color",
            mpvBackgroundColor(playerPreferences.getSubtitleBackgroundColor())
        )
        mpv.setOptionString(
            "sub-pos",
            (100 - playerPreferences.getSubtitlePosition().coerceIn(0, 50)).toString()
        )
        applySubtitleEdge(playerPreferences.getSubtitleEdgeType())
    }

    fun detachSurface() {
        if (released) return
        mpv.setOptionString("vo", "null")
        mpv.setOptionString("force-window", "no")
        mpv.detachSurface()
    }

    fun play() {
        if (released) return
        playWhenReady = true
        mpv.setPropertyBoolean("pause", false)
    }

    fun pause() {
        if (released) return
        playWhenReady = false
        mpv.setPropertyBoolean("pause", true)
    }

    fun seekTo(positionMs: Long) {
        if (released) return
        this.positionMs = positionMs.coerceAtLeast(0L)
        mpv.command(arrayOf("seek", (this.positionMs / 1000.0).toString(), "absolute+keyframes"))
    }

    fun setVolume(volume: Float) {
        if (!released) {
            mpv.setPropertyDouble("volume", (volume.coerceIn(0f, 1f) * 100f).toDouble())
        }
    }

    fun selectAudioTrack(trackId: String) {
        if (!released) {
            mpv.setPropertyString("aid", trackId)
        }
    }

    fun selectSubtitleTrack(trackId: String, externalUrl: String?) {
        if (released) return
        if (trackId == "no") {
            mpv.setPropertyString("sid", "no")
        } else if (externalUrl != null) {
            mpv.command(arrayOf("sub-add", externalUrl, "select"))
        } else {
            mpv.setPropertyString("sid", trackId)
        }
    }

    fun release() {
        if (released) return
        released = true
        runCatching { mpv.removeObserver(this) }
        runCatching { mpv.detachSurface() }
        runCatching { mpv.destroy() }
    }

    override fun eventProperty(property: String) = Unit

    override fun eventProperty(property: String, value: String) = Unit

    override fun eventProperty(property: String, value: Long) {
        eventProperty(property, value.toDouble())
    }

    override fun eventProperty(property: String, value: Double) {
        when (property) {
            "time-pos" -> positionMs = (value * 1000.0).toLong().coerceAtLeast(0L)
            "duration" -> durationMs = (value * 1000.0).toLong().coerceAtLeast(0L)
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        when (property) {
            "paused-for-cache" -> if (value) listener.onBuffering() else listener.onReady()
            "eof-reached" -> if (value) listener.onEnded()
        }
    }

    override fun event(eventId: Int) {
        when (eventId) {
            MpvEvent.MPV_EVENT_FILE_LOADED -> {
                durationMs = (mpv.getPropertyDouble("duration")?.times(1000.0))
                    ?.toLong()
                    ?.coerceAtLeast(0L)
                    ?: 0L
                pendingSubtitleUrls
                    .filterNot { subtitleUrl -> subtitleUrl == pendingSelectedSubtitleUrl }
                    .forEach { subtitleUrl ->
                        mpv.command(arrayOf("sub-add", subtitleUrl, "auto"))
                    }
                pendingSubtitleUrls = emptyList()
                pendingAudioTrackId?.let(::selectAudioTrack)
                pendingAudioTrackId = null
                pendingSubtitleTrackId?.let { trackId ->
                    selectSubtitleTrack(trackId, pendingSelectedSubtitleUrl)
                }
                pendingSubtitleTrackId = null
                pendingSelectedSubtitleUrl = null
                pendingSeekMs?.let { seekTo(it) }
                pendingSeekMs = null
            }
            MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                ready = true
                if (playWhenReady) {
                    mpv.setPropertyBoolean("pause", false)
                }
                listener.onReady()
            }
            MpvEvent.MPV_EVENT_SHUTDOWN -> Unit
            else -> Unit
        }
    }

    private fun configureMpv() {
        val cacheTimeSeconds = playerPreferences.getPlayerCacheTimeSeconds().toString()
        val cacheSizeMb = playerPreferences.getPlayerCacheSizeMb()

        mpv.setOptionString("config", "no")
        mpv.setOptionString("load-scripts", "no")
        mpv.setOptionString("load-auto-profiles", "no")
        mpv.setOptionString("load-stats-overlay", "no")
        mpv.setOptionString("load-console", "no")
        mpv.setOptionString("load-commands", "no")
        mpv.setOptionString("load-select", "no")
        mpv.setOptionString("load-positioning", "no")
        mpv.setOptionString("terminal", "no")
        mpv.setOptionString("quiet", "yes")
        mpv.setOptionString("really-quiet", "yes")
        mpv.setOptionString("msg-level", "all=no")
        mpv.setOptionString("scale", "bilinear")
        mpv.setOptionString("dscale", "bilinear")
        mpv.setOptionString("dither", "no")
        mpv.setOptionString("correct-downscaling", "no")
        mpv.setOptionString("linear-downscaling", "no")
        mpv.setOptionString("sigmoid-upscaling", "no")
        mpv.setOptionString("hdr-compute-peak", "no")
        mpv.setOptionString("allow-delayed-peak-detect", "yes")
        mpv.setOptionString("vo", videoOutput)
        mpv.setOptionString("gpu-context", "android")
        mpv.setOptionString("opengl-es", "yes")
        mpv.setOptionString("ao", audioOutput)
        mpv.setOptionString("hwdec", hardwareDecoding)
        mpv.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        mpv.setOptionString("tls-verify", "no")
        mpv.setOptionString("keep-open", "no")
        mpv.setOptionString("cache", "yes")
        mpv.setOptionString("cache-secs", cacheTimeSeconds)
        mpv.setOptionString("index", "no")
        mpv.setOptionString("hr-seek", "no")
        mpv.setOptionString("demuxer-mkv-probe-start-time", "no")
        mpv.setOptionString("demuxer-mkv-probe-video-duration", "no")
        mpv.setOptionString("demuxer-lavf-probesize", "64KiB")
        mpv.setOptionString("demuxer-lavf-analyzeduration", "1")
        mpv.setOptionString("demuxer-readahead-secs", cacheTimeSeconds)
        mpv.setOptionString("demuxer-max-bytes", "${cacheSizeMb}MiB")
        mpv.setOptionString("demuxer-max-back-bytes", "${cacheSizeMb / 2}MiB")
        mpv.setOptionString("sub-scale-with-window", "yes")
        mpv.setOptionString("sub-use-margins", "no")
        mpv.setOptionString("ytdl", "no")
        applySubtitlePreferences()
    }

    private fun subtitleScale(size: String): String {
        return when (size) {
            PlayerPreferences.SUBTITLE_TEXT_SIZE_SMALL -> "0.85"
            PlayerPreferences.SUBTITLE_TEXT_SIZE_LARGE -> "1.25"
            PlayerPreferences.SUBTITLE_TEXT_SIZE_EXTRA_LARGE -> "1.5"
            else -> "1.0"
        }
    }

    private fun mpvColor(color: String, opacityPercent: Int): String {
        val rgb = when (color) {
            PlayerPreferences.SUBTITLE_TEXT_COLOR_YELLOW -> "FFFF00"
            PlayerPreferences.SUBTITLE_TEXT_COLOR_GREEN -> "00FF00"
            PlayerPreferences.SUBTITLE_TEXT_COLOR_CYAN -> "00FFFF"
            PlayerPreferences.SUBTITLE_TEXT_COLOR_BLACK -> "000000"
            else -> "FFFFFF"
        }
        return "#${alphaHex(opacityPercent)}$rgb"
    }

    private fun mpvBackgroundColor(color: String): String {
        return when (color) {
            PlayerPreferences.SUBTITLE_BACKGROUND_BLACK -> "#CC000000"
            PlayerPreferences.SUBTITLE_BACKGROUND_WHITE -> "#CCFFFFFF"
            else -> "#00000000"
        }
    }

    private fun applySubtitleEdge(edgeType: String) {
        when (edgeType) {
            PlayerPreferences.SUBTITLE_EDGE_TYPE_OUTLINE -> {
                mpv.setOptionString("sub-border-size", "3")
                mpv.setOptionString("sub-shadow-offset", "0")
            }
            PlayerPreferences.SUBTITLE_EDGE_TYPE_DROP_SHADOW -> {
                mpv.setOptionString("sub-border-size", "0")
                mpv.setOptionString("sub-shadow-offset", "2")
            }
            else -> {
                mpv.setOptionString("sub-border-size", "0")
                mpv.setOptionString("sub-shadow-offset", "0")
            }
        }
        mpv.setOptionString("sub-border-color", "#FF000000")
        mpv.setOptionString("sub-shadow-color", "#CC000000")
    }

    private fun alphaHex(opacityPercent: Int): String {
        val alpha = ((opacityPercent.coerceIn(0, 100) / 100f) * 255f)
            .toInt()
            .coerceIn(0, 255)
        return alpha.toString(16).uppercase().padStart(2, '0')
    }
}