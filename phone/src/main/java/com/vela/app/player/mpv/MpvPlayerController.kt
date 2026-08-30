package com.vela.app.player.mpv

import android.content.Context
import android.graphics.Bitmap
import android.system.Os
import android.util.Log
import android.view.Surface
import androidx.media3.common.util.UnstableApi
import com.vela.player.core.PlayerUtils
import com.vela.player.preferences.PlayerPreferences
import `is`.xyz.mpv.MPVLib
import `is`.xyz.mpv.MPVLib.MpvEvent
import `is`.xyz.mpv.MPVLib.MpvFormat
import java.io.File
import java.util.Locale

class MpvPlayerController(
    context: Context,
    private val hardwareDecoding: String,
    private val videoOutput: String,
    private val audioOutput: String,
    listener: Listener
) : MPVLib.EventObserver {

    companion object {
        private const val SUBTITLE_LOG_TAG = "JellyCine-Sub"
    }

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
    private var cacheAheadMs: Long = 0L
    private var playWhenReady = true
    private var pendingSubtitleUrls: List<String> = emptyList()
    private var pendingSelectedSubtitleUrl: String? = null
    private var pendingSubtitleTrackId: String? = null
    private var preferFastSeek = false
    private var pendingStartPositionMs: Long? = null
    private var pendingRemoteHttpPlayback = false
    private val playerPreferences = PlayerPreferences(context.applicationContext)
    @Volatile
    private var listener: Listener = listener

    val isPlaying: Boolean
        get() = ready && playWhenReady

    val currentPosition: Long
        get() = positionMs

    val duration: Long
        get() = durationMs

    val bufferedPosition: Long
        get() = (positionMs + cacheAheadMs).coerceAtLeast(positionMs)

    init {
        MPVLib.create(appContext)
        configureMpv()
        MPVLib.init()
        MPVLib.addObserver(this)
        MPVLib.observeProperty("time-pos", MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("duration", MpvFormat.MPV_FORMAT_DOUBLE)
        MPVLib.observeProperty("demuxer-cache-duration", MpvFormat.MPV_FORMAT_DOUBLE)
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
        opticalDiscPlayback: Boolean,
        remoteHttpPlayback: Boolean = false
    ) {
        if (released) return
        ready = false
        playWhenReady = startPlayback
        preferFastSeek = opticalDiscPlayback
        pendingStartPositionMs = startPositionMs?.takeIf { it > 0L }
        pendingRemoteHttpPlayback = remoteHttpPlayback
        pendingSubtitleUrls = subtitleUrls
        pendingSelectedSubtitleUrl = selectedSubtitleUrl
        pendingSubtitleTrackId = subtitleTrackId?.takeUnless { it == "no" }
        MPVLib.setPropertyBoolean("pause", true)
        listener.onBuffering()
        applyRemoteStreamOptions(remoteHttpPlayback)
        val needsEmbeddedSubtitleProbe =
            selectedSubtitleUrl == null && pendingSubtitleTrackId != null
        if (needsEmbeddedSubtitleProbe && !remoteHttpPlayback) {
            MPVLib.setOptionString("demuxer-lavf-probe-info", "on")
        }
        val loadOptions = buildList {
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
        applyCachePolicy(asOptions = false)
        Log.i(
            SUBTITLE_LOG_TAG,
            "load sid=$subtitleTrackId selectedUrl=${MPVPlayer.redactPlaybackSecret(selectedSubtitleUrl)} " +
                "external=${subtitleUrls.size} remote=$remoteHttpPlayback"
        )
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
        setMpv("sub-visibility", "yes")
        val compatible = playerPreferences.isSubtitleAssCompatible()
        setMpv("sub-ass-override", PlayerPreferences.mpvAssOverride(compatible))
        MPVLib.setPropertyDouble(
            "sub-delay",
            playerPreferences.getSubtitleDelayMs() / 1000.0
        )
        val scale = String.format(
            Locale.US,
            "%.3f",
            playerPreferences.getSubtitleScale()
        )
        setMpv("sub-scale", scale)
        if (compatible) {
            return
        }
        val subPos = PlayerPreferences.mpvSubPosFromBottomPercent(
            playerPreferences.getSubtitlePosition()
        ).toString()
        setMpv("sub-pos", subPos)
        setMpv(
            "sub-color",
            mpvColor(
                color = playerPreferences.getSubtitleTextColor(),
                opacityPercent = playerPreferences.getSubtitleTextOpacityPercent()
            )
        )
        setMpv(
            "sub-back-color",
            mpvBackgroundColor(playerPreferences.getSubtitleBackgroundColor())
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
        if (playWhenReady) {
            MPVLib.setPropertyBoolean("pause", false)
        }
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
            return
        }
        if (externalUrl != null) {
            addSubtitleTrack(externalUrl, "select")
        } else {
            MPVLib.setPropertyString("sid", trackId)
        }
        applySubtitlePreferences()
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
            "demuxer-cache-duration" -> cacheAheadMs = (value * 1000.0).toLong().coerceAtLeast(0L)
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
                        addSubtitleTrack(subtitleUrl, "auto")
                    }
                pendingSelectedSubtitleUrl?.let { subtitleUrl ->
                    addSubtitleTrack(subtitleUrl, "select")
                }
                if (pendingSelectedSubtitleUrl == null) {
                    pendingSubtitleTrackId?.let { trackId ->
                        MPVLib.setPropertyString("sid", trackId)
                    }
                }
                pendingSubtitleUrls = emptyList()
                pendingSelectedSubtitleUrl = null
                pendingSubtitleTrackId = null
                applySubtitlePreferences()
                logSubtitleTracks("FILE_LOADED")
                val resumePositionMs = pendingStartPositionMs
                pendingStartPositionMs = null
                if (resumePositionMs != null) {
                    seekTo(resumePositionMs, exact = false)
                }
            }
            MpvEvent.MPV_EVENT_PLAYBACK_RESTART -> {
                val firstReady = !ready
                ready = true
                if (playWhenReady) {
                    MPVLib.setPropertyBoolean("pause", false)
                }
                listener.onReady()
                if (firstReady) {
                    logSubtitleTracks("READY")
                }
            }
            MpvEvent.MPV_EVENT_SHUTDOWN -> Unit
            else -> Unit
        }
    }

    private fun configureMpv() {
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
        MPVLib.setOptionString("msg-level", "all=no,cplayer=warn,ffmpeg=error,sub=info,demux=warn")
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
        MPVLib.setOptionString("force-seekable", "yes")
        applyCachePolicy(asOptions = true)
        MPVLib.setOptionString("index", "default")
        MPVLib.setOptionString("hr-seek", "yes")
        MPVLib.setOptionString("demuxer", "+lavf")
        MPVLib.setOptionString("demuxer-mkv-probe-start-time", "no")
        MPVLib.setOptionString("demuxer-mkv-probe-video-duration", "no")
        MPVLib.setOptionString("demuxer-mkv-subtitle-preroll", "yes")
        MPVLib.setOptionString("demuxer-mkv-subtitle-preroll-secs", "60")
        MPVLib.setOptionString("demuxer-mkv-subtitle-preroll-secs-index", "60")
        MPVLib.setOptionString("demuxer-lavf-probe-info", "nostreams")
        MPVLib.setOptionString("demuxer-lavf-probesize", "64KiB")
        MPVLib.setOptionString("demuxer-lavf-analyzeduration", "1")
        MPVLib.setOptionString("sub-ass", "yes")
        MPVLib.setOptionString("embeddedfonts", "yes")
        MPVLib.setOptionString("sub-ass-use-video-data", "aspect-ratio")
        MPVLib.setOptionString("sub-scale-with-window", "yes")
        MPVLib.setOptionString("sub-use-margins", "no")
        configureSubtitleFonts()
        MPVLib.setOptionString("ytdl", "no")
        applySubtitlePreferences()
    }

    @OptIn(UnstableApi::class)
    fun refreshCachePolicy() {
        if (!released) {
            applyCachePolicy(asOptions = false)
            if (pendingRemoteHttpPlayback) {
                MPVLib.setPropertyString("cache", "yes")
                MPVLib.setPropertyString("force-seekable", "yes")
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun applyRemoteStreamOptions(remoteHttpPlayback: Boolean) {
        if (remoteHttpPlayback) {
            MPVLib.setOptionString("demuxer-lavf-probesize", "5MiB")
            MPVLib.setOptionString("demuxer-lavf-analyzeduration", "10")
            MPVLib.setOptionString("demuxer-lavf-probe-info", "on")
            MPVLib.setPropertyString("cache", "yes")
            MPVLib.setPropertyString("force-seekable", "yes")
            applyCachePolicy(asOptions = false)
        } else {
            MPVLib.setOptionString("demuxer-lavf-probesize", "64KiB")
            MPVLib.setOptionString("demuxer-lavf-analyzeduration", "1")
            MPVLib.setOptionString("demuxer-lavf-probe-info", "nostreams")
        }
    }

    @OptIn(UnstableApi::class)
    private fun applyCachePolicy(asOptions: Boolean) {
        val budget = PlayerUtils.playbackCacheBudget(appContext, playerPreferences)
        val cacheTimeSeconds = budget.cacheTimeSeconds.toString()
        val cacheSize = "${budget.cacheSizeMb}MiB"
        val backCacheSize = "${(budget.cacheSizeMb / 2).coerceAtLeast(32)}MiB"
        if (asOptions) {
            MPVLib.setOptionString("cache-secs", cacheTimeSeconds)
            MPVLib.setOptionString("demuxer-readahead-secs", cacheTimeSeconds)
            MPVLib.setOptionString("demuxer-max-bytes", cacheSize)
            MPVLib.setOptionString("demuxer-max-back-bytes", backCacheSize)
        } else {
            MPVLib.setPropertyString("cache-secs", cacheTimeSeconds)
            MPVLib.setPropertyString("demuxer-readahead-secs", cacheTimeSeconds)
            MPVLib.setPropertyString("demuxer-max-bytes", cacheSize)
            MPVLib.setPropertyString("demuxer-max-back-bytes", backCacheSize)
        }
    }

    private fun addSubtitleTrack(url: String, flags: String) {
        MPVLib.command(arrayOf("sub-add", url, flags, "ASS"))
    }

    private fun configureSubtitleFonts() {
        val fontsDir = appContext.filesDir.resolve("mpv-fonts").apply { mkdirs() }
        val cacheDir = appContext.cacheDir.resolve("fontconfig").apply { mkdirs() }
        val fallback = installFallbackSubtitleFont(fontsDir)
        val fontsConf = appContext.filesDir.resolve("mpv/fonts.conf")
        fontsConf.parentFile?.mkdirs()
        fontsConf.writeText(
            """
            <?xml version="1.0"?>
            <!DOCTYPE fontconfig SYSTEM "fonts.dtd">
            <fontconfig>
              <dir>/system/fonts</dir>
              <dir>/product/fonts</dir>
              <dir>${fontsDir.path}</dir>
              <cachedir>${cacheDir.path}</cachedir>
            </fontconfig>
            """.trimIndent()
        )
        runCatching { Os.setenv("FONTCONFIG_FILE", fontsConf.absolutePath, true) }
        MPVLib.setOptionString("sub-fonts-dir", fontsDir.path)
        MPVLib.setOptionString("osd-fonts-dir", fontsDir.path)
        val family = fallback?.family ?: "sans-serif"
        MPVLib.setOptionString("sub-font", family)
        MPVLib.setOptionString("sub-ass-force-style", "FontName=$family")
        Log.i(
            SUBTITLE_LOG_TAG,
            "subtitle font family=$family file=${fallback?.file?.name ?: "none"}"
        )
    }

    private fun installFallbackSubtitleFont(fontsDir: File): InstalledSubtitleFont? {
        val candidates = listOf(
            "Droid Sans Fallback" to listOf(
                "/system/fonts/DroidSansFallback.ttf",
                "/system/fonts/DroidSansFallbackFull.ttf"
            ),
            "Noto Sans CJK SC" to listOf(
                "/system/fonts/NotoSansSC-Regular.otf",
                "/system/fonts/NotoSansSC-Regular.ttf",
                "/system/fonts/NotoSansCJKsc-Regular.otf",
                "/system/fonts/NotoSansCJK-Regular.ttc",
                "/product/fonts/NotoSansCJK-Regular.ttc"
            ),
            "MiSans" to listOf(
                "/system/fonts/MiSans-Regular.ttf",
                "/system/fonts/MiSansVF.ttf",
                "/system/fonts/MiSans.ttf",
                "/product/fonts/MiSansVF.ttf"
            )
        )
        val match = candidates.firstNotNullOfOrNull { (family, paths) ->
            paths.firstOrNull { path -> File(path).exists() }?.let { family to File(it) }
        } ?: return null
        val (family, source) = match
        val target = File(fontsDir, source.name)
        if (!target.exists()) {
            val linked = runCatching {
                Os.symlink(source.absolutePath, target.absolutePath)
                true
            }.getOrDefault(false)
            if (!linked) {
                return InstalledSubtitleFont(family, source)
            }
        }
        return InstalledSubtitleFont(family, if (target.exists()) target else source)
    }

    private data class InstalledSubtitleFont(
        val family: String,
        val file: File
    )

    private fun logSubtitleTracks(stage: String) {
        val count = MPVLib.getPropertyInt("track-list/count") ?: 0
        val tracks = buildList {
            for (index in 0 until count) {
                val type = MPVLib.getPropertyString("track-list/$index/type") ?: continue
                if (type != "sub") continue
                add(
                    "id=${MPVLib.getPropertyString("track-list/$index/id")} " +
                        "codec=${MPVLib.getPropertyString("track-list/$index/codec")} " +
                        "lang=${MPVLib.getPropertyString("track-list/$index/lang")} " +
                        "title=${MPVPlayer.redactPlaybackSecret(MPVLib.getPropertyString("track-list/$index/title"))} " +
                        "selected=${MPVLib.getPropertyBoolean("track-list/$index/selected")} " +
                        "external=${MPVLib.getPropertyBoolean("track-list/$index/external")}"
                )
            }
        }
        Log.i(
            SUBTITLE_LOG_TAG,
            "$stage sid=${MPVLib.getPropertyString("sid")} " +
                "vis=${MPVLib.getPropertyString("sub-visibility")} " +
                "override=${MPVLib.getPropertyString("sub-ass-override")} " +
                "font=${MPVLib.getPropertyString("sub-font")} " +
                "text=${MPVPlayer.redactPlaybackSecret(MPVLib.getPropertyString("sub-text")?.take(80))} " +
                "tracks=$tracks"
        )
    }

    private fun setMpv(name: String, value: String) {
        MPVLib.setOptionString(name, value)
        MPVLib.setPropertyString(name, value)
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
                setMpv("sub-border-size", "3")
                setMpv("sub-shadow-offset", "0")
            }
            PlayerPreferences.SUBTITLE_EDGE_TYPE_DROP_SHADOW -> {
                setMpv("sub-border-size", "0")
                setMpv("sub-shadow-offset", "2")
            }
            else -> {
                setMpv("sub-border-size", "2")
                setMpv("sub-shadow-offset", "0")
            }
        }
        setMpv("sub-border-color", "#FF000000")
        setMpv("sub-shadow-color", "#CC000000")
    }

    private fun alphaHex(opacityPercent: Int): String {
        val alpha = ((opacityPercent.coerceIn(0, 100) / 100f) * 255f)
            .toInt()
            .coerceIn(0, 255)
        return alpha.toString(16).uppercase().padStart(2, '0')
    }
}
