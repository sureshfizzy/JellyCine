package com.vela.app.player.mpv

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface
import com.vela.player.preferences.PlayerPreferences
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVLib.MpvEvent
import `is`.xyz.mpv.MPVLib.MpvFormat

class MpvPlayerController(
    context: Context,
    private val hardwareDecoding: String,
    private val videoOutput: String,
    private val audioOutput: String,
    listener: Listener
) : MPVLib.EventObserver {

    interface Listener {
        fun onBuffering()
        fun onReady()
        fun onEnded()
    }

    private val appContext = context.applicationContext
    @Volatile
    private var released = false
    private var ready = false
    private var durationMs: Long = 0L
    private var positionMs: Long = 0L
    private var playWhenReady = true
    private var pendingSubtitleUrls: List<String> = emptyList()
    private var pendingSelectedSubtitleUrl: String? = null
    private var preferFastSeek = false
    private val playerPreferences = PlayerPreferences(context.applicationContext)
    @Volatile
    private var listener: Listener = listener

    val isPlaying: Boolean
        get() = ready && playWhenReady

    val currentPosition: Long
        get() = positionMs

    val duration: Long
        get() = durationMs

    init {
        MPVLib.create(appContext)
        configureMpv()
        MPVLib.init()
        MPVLib.addObserver(this)
        MPVLib.observeProperty("time-pos", MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration", MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("paused-for-cache", MpvFormat.MPV_FORMAT_FLAG)
        MPVLib.observeProperty("eof-reached", MpvFormat.MPV_FORMAT_FLAG)
    }

    fun load(
        url: String,
        subtitleUrls: List<String>,
        audioTrackId: String?,
        subtitleTrackId: String?,
        selectedSubtitleUrl: String?,
        startPositionMs: Long?,
        startPlayback: Boolean,
        opticalDiscPlayback: Boolean
    ) {
        if (released) return
        ready = false
        playWhenReady = startPlayback
        preferFastSeek = opticalDiscPlayback
        val startPositionSeconds = startPositionMs
            ?.takeIf { it > 0L }
            ?.let { it / 1000.0 }
        pendingSubtitleUrls = subtitleUrls
        pendingSelectedSubtitleUrl = selectedSubtitleUrl
        MPVLib.setPropertyBoolean("pause", true)
        listener.onBuffering()
        val loadOptions = buildList {
            startPositionSeconds?.let { add("start=$it") }
            audioTrackId?.let { add("aid=$it") }
            if (selectedSubtitleUrl == null) {
                subtitleTrackId?.let { add("sid=$it") }
            }
            if (opticalDiscPlayback) {
                add("hr-seek=no")
                add("stream-buffer-size=1MiB")
            }
        }
        val loadCommand = if (loadOptions.isEmpty()) {
            arrayOf("loadfile", url, "replace")
        } else {
            arrayOf("loadfile", url, "replace", "-1", loadOptions.joinToString(","))
        }
        MPVLib.command(loadCommand)
    }

    fun setListener(listener: Listener) {
        this.listener = listener
    }

    fun attachSurface(surface: Surface, width: Int, height: Int) {
        if (released) return
        MPVLib.attachSurface(surface)
        MPVLib.setOptionString("force-window", "yes")
        MPVLib.setOptionString("vo", videoOutput)
        if (width > 0 && height > 0) {
            MPVLib.setPropertyString("android-surface-size", "${width}x$height")
        }
    }

    fun resizeSurface(width: Int, height: Int) {
        if (!released && width > 0 && height > 0) {
            MPVLib.setPropertyString("android-surface-size", "${width}x$height")
        }
    }

    fun screenshotToFile(path: String) {
        if (released) return
        MPVLib.command(arrayOf("screenshot-to-file", path, "subtitles"))
    }

    fun setZoomMode(enabled: Boolean) {
        if (released) return
        if (enabled) {
            MPVLib.setOptionString("panscan", "1")
            MPVLib.setOptionString("sub-use-margins", "yes")
            MPVLib.setOptionString("sub-ass-force-margins", "yes")
        } else {
            MPVLib.setOptionString("panscan", "0")
            MPVLib.setOptionString("sub-use-margins", "no")
            MPVLib.setOptionString("sub-ass-force-margins", "no")
        }
    }

    fun applySubtitlePreferences() {
        if (released) return
        MPVLib.setOptionString("sub-ass-override", "strip")
        MPVLib.setOptionString("sub-scale", subtitleScale(playerPreferences.getSubtitleTextSize()))
        MPVLib.setOptionString(
            "sub-color",
            mpvColor(
                color = playerPreferences.getSubtitleTextColor(),
                opacityPercent = playerPreferences.getSubtitleTextOpacityPercent()
            )
        )
        MPVLib.setOptionString(
            "sub-back-color",
            mpvBackgroundColor(playerPreferences.getSubtitleBackgroundColor())
        )
        MPVLib.setOptionString(
            "sub-pos",
            (100 - playerPreferences.getSubtitlePosition().coerceIn(0, 50)).toString()
        )
        applySubtitleEdge(playerPreferences.getSubtitleEdgeType())
    }

    fun detachSurface() {
        if (released) return
        MPVLib.setOptionString("vo", "null")
        MPVLib.setOptionString("force-window", "no")
        MPVLib.detachSurface()
    }

    fun play() {
        if (released) return
        playWhenReady = true
        MPVLib.setPropertyBoolean("pause", false)
    }

    fun pause() {
        if (released) return
        playWhenReady = false
        MPVLib.setPropertyBoolean("pause", true)
    }

    fun grabThumbnail(dimension: Int): Bitmap? {
        if (released) return null
        return MPVLib.grabThumbnail(dimension)
    }

    fun seekTo(positionMs: Long, exact: Boolean = true) {
        if (released) return
        this.positionMs = positionMs.coerceAtLeast(0L)
        val flags = if (exact && !preferFastSeek) "absolute+exact" else "absolute"
        MPVLib.command(
            arrayOf("seek", (this.positionMs / 1000.0).toString(), flags)
        )
    }

    fun setHardwareDecoding(mode: String) {
        if (!released) {
            MPVLib.setPropertyString("hwdec", mode)
        }
    }

    fun setVolume(volume: Float) {
        if (!released) {
            MPVLib.setPropertyDouble("volume", (volume.coerceIn(0f, 1f) * 100f).toDouble())
        }
    }

    fun setSpeed(speed: Double) {
        if (!released) {
            MPVLib.setPropertyDouble("speed", speed.coerceIn(0.25, 4.0))
        }
    }

    fun selectAudioTrack(trackId: String) {
        if (!released) {
            MPVLib.setPropertyString("aid", trackId)
        }
    }

    fun selectSubtitleTrack(trackId: String, externalUrl: String?) {
        if (released) return
        if (trackId == "no") {
            MPVLib.setPropertyString("sid", "no")
        } else if (externalUrl != null) {
            MPVLib.command(arrayOf("sub-add", externalUrl, "select"))
        } else {
            MPVLib.setPropertyString("sid", trackId)
        }
    }

    fun release() {
        if (released) return
        released = true
        runCatching { MPVLib.removeObserver(this) }
        runCatching { MPVLib.detachSurface() }
        runCatching { MPVLib.destroy() }
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
                durationMs = (MPVLib.getPropertyDouble("duration")?.times(1000.0))
                    ?.toLong()
                    ?.coerceAtLeast(0L)
                    ?: 0L
                pendingSubtitleUrls
                    .filterNot { subtitleUrl -> subtitleUrl == pendingSelectedSubtitleUrl }
                    .forEach { subtitleUrl ->
                        MPVLib.command(arrayOf("sub-add", subtitleUrl, "auto"))
                    }
                pendingSelectedSubtitleUrl?.let { subtitleUrl ->
                    MPVLib.command(arrayOf("sub-add", subtitleUrl, "select"))
                }
                pendingSubtitleUrls = emptyList()
                pendingSelectedSubtitleUrl = null
            }
            MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                ready = true
                if (playWhenReady) {
                    MPVLib.setPropertyBoolean("pause", false)
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
        val shaderCacheDir = appContext.cacheDir.resolve("mpv-shaders")
        shaderCacheDir.mkdirs()

        MPVLib.setOptionString("gpu-shader-cache-dir", shaderCacheDir.path)
        MPVLib.setOptionString("icc-cache-dir", shaderCacheDir.path)
        MPVLib.setOptionString("config", "no")
        MPVLib.setOptionString("load-scripts", "no")
        MPVLib.setOptionString("load-auto-profiles", "no")
        MPVLib.setOptionString("load-stats-overlay", "no")
        MPVLib.setOptionString("load-console", "no")
        MPVLib.setOptionString("load-commands", "no")
        MPVLib.setOptionString("load-select", "no")
        MPVLib.setOptionString("load-positioning", "no")

        val upscaleFilter = playerPreferences.getMpvUpscaleFilter()
        val downscaleFilter = playerPreferences.getMpvDownscaleFilter()
        val smoothMotion = playerPreferences.getMpvSmoothMotion()
        val deband = playerPreferences.getMpvDeband()

        MPVLib.setOptionString("profile", "fast")
        MPVLib.setOptionString("terminal", "no")
        MPVLib.setOptionString("msg-level", "all=no")
        MPVLib.setOptionString("vo", "null")
        MPVLib.setOptionString("gpu-context", "android")
        MPVLib.setOptionString("opengl-es", "yes")
        MPVLib.setOptionString("scale", upscaleFilter)
        MPVLib.setOptionString("dscale", downscaleFilter)
        MPVLib.setOptionString("deband", if (deband) "yes" else "no")
        MPVLib.setOptionString("target-prim", playerPreferences.getMpvTargetPrim())
        MPVLib.setOptionString("target-trc", playerPreferences.getMpvTargetTrc())
        MPVLib.setOptionString("video-output-levels", playerPreferences.getMpvOutputLevels())
        MPVLib.setOptionString(
            "hdr-compute-peak",
            if (playerPreferences.getMpvDynamicPeak()) "yes" else "no"
        )
        if (smoothMotion) {
            MPVLib.setOptionString("interpolation", "yes")
            MPVLib.setOptionString("tscale", "oversample")
            MPVLib.setOptionString("video-sync", "display-resample")
        } else {
            MPVLib.setOptionString("video-sync", "audio")
        }
        MPVLib.setOptionString("ao", audioOutput)
        MPVLib.setOptionString("hwdec", hardwareDecoding)
        MPVLib.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        MPVLib.setOptionString("tls-verify", "no")
        MPVLib.setOptionString("keep-open", "no")
        MPVLib.setOptionString("cache", "yes")
        MPVLib.setOptionString("cache-secs", cacheTimeSeconds)
        MPVLib.setOptionString("index", "default")
        MPVLib.setOptionString("hr-seek", "yes")
        MPVLib.setOptionString("demuxer", "+lavf")
        MPVLib.setOptionString("demuxer-mkv-probe-start-time", "no")
        MPVLib.setOptionString("demuxer-mkv-probe-video-duration", "no")
        MPVLib.setOptionString("demuxer-lavf-probe-info", "nostreams")
        MPVLib.setOptionString("demuxer-lavf-probesize", "64KiB")
        MPVLib.setOptionString("demuxer-lavf-analyzeduration", "1")
        MPVLib.setOptionString("demuxer-readahead-secs", cacheTimeSeconds)
        MPVLib.setOptionString("demuxer-max-bytes", "${cacheSizeMb}MiB")
        MPVLib.setOptionString("demuxer-max-back-bytes", "${cacheSizeMb / 2}MiB")
        MPVLib.setOptionString("sub-scale-with-window", "yes")
        MPVLib.setOptionString("sub-use-margins", "no")
        MPVLib.setOptionString("ytdl", "no")
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
                MPVLib.setOptionString("sub-border-size", "3")
                MPVLib.setOptionString("sub-shadow-offset", "0")
            }
            PlayerPreferences.SUBTITLE_EDGE_TYPE_DROP_SHADOW -> {
                MPVLib.setOptionString("sub-border-size", "0")
                MPVLib.setOptionString("sub-shadow-offset", "2")
            }
            else -> {
                MPVLib.setOptionString("sub-border-size", "0")
                MPVLib.setOptionString("sub-shadow-offset", "0")
            }
        }
        MPVLib.setOptionString("sub-border-color", "#FF000000")
        MPVLib.setOptionString("sub-shadow-color", "#CC000000")
    }

    private fun alphaHex(opacityPercent: Int): String {
        val alpha = ((opacityPercent.coerceIn(0, 100) / 100f) * 255f)
            .toInt()
            .coerceIn(0, 255)
        return alpha.toString(16).uppercase().padStart(2, '0')
    }
}
