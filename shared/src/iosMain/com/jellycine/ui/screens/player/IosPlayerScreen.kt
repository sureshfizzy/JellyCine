package com.jellycine.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository
import com.jellycine.player.core.EnginePlayerController
import com.jellycine.player.core.PlayerEngine
import com.jellycine.player.core.PlayerEngineListener
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.preferences.UserDefaultsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIApplication
import platform.UIKit.UIScreen
import platform.UIKit.UIView

/** Implemented in Swift on top of libmpv; Kotlin drives playback through it. */
interface IosMpvBridge {
    fun createVideoView(): UIView

    fun load(
        url: String,
        headers: Map<String, String>,
        startPositionMs: Long,
        startPlayback: Boolean,
        audioTrackId: String?,
        subtitleTrackId: String?,
        subtitleUrls: List<String>,
        selectedSubtitleUrl: String?
    )

    fun setPaused(paused: Boolean)
    fun seekTo(positionMs: Long)
    fun setVolume(volume: Float)
    fun selectAudioTrack(trackId: String)
    fun selectSubtitleTrack(trackId: String, externalUrl: String?)
    fun setZoom(enabled: Boolean)
    fun viewSizeChanged()
    fun attachListener(listener: IosMpvBridgeListener)

    fun position(): Long
    fun duration(): Long
    fun bufferedPosition(): Long
    fun isPlaying(): Boolean

    fun shutdown()
}

interface IosMpvBridgeListener {
    fun onBuffering(isBuffering: Boolean)
    fun onReady()
    fun onEnded()
    fun onError(message: String)
}

fun interface IosMpvBridgeFactory {
    fun create(): IosMpvBridge
}

class IosMpvEngine(private val bridge: IosMpvBridge) : PlayerEngine {
    private var listener: PlayerEngineListener? = null
    private var released = false

    init {
        bridge.attachListener(
            object : IosMpvBridgeListener {
                override fun onBuffering(isBuffering: Boolean) {
                    if (isBuffering) listener?.onBuffering() else listener?.onReady()
                }

                override fun onReady() {
                    listener?.onReady()
                }

                override fun onEnded() {
                    listener?.onEnded()
                }

                override fun onError(message: String) {
                    listener?.onError(message)
                }
            }
        )
    }

    override val isPlaying: Boolean get() = !released && bridge.isPlaying()
    override val currentPosition: Long get() = if (released) 0L else bridge.position()
    override val bufferedPosition: Long get() = if (released) 0L else bridge.bufferedPosition()
    override val duration: Long get() = if (released) 0L else bridge.duration()

    fun createVideoView(): UIView = bridge.createVideoView()

    fun viewSizeChanged() {
        if (!released) bridge.viewSizeChanged()
    }

    override fun load(
        url: String,
        subtitleUrls: List<String>,
        audioTrackId: String?,
        subtitleTrackId: String?,
        selectedSubtitleUrl: String?,
        startPositionMs: Long?,
        startPlayback: Boolean,
        requestHeaders: Map<String, String>
    ) {
        if (released) return
        bridge.load(
            url = url,
            headers = requestHeaders,
            startPositionMs = startPositionMs ?: 0L,
            startPlayback = startPlayback,
            audioTrackId = audioTrackId,
            subtitleTrackId = subtitleTrackId,
            subtitleUrls = subtitleUrls,
            selectedSubtitleUrl = selectedSubtitleUrl
        )
    }

    override fun setListener(listener: PlayerEngineListener) {
        this.listener = listener
    }

    override fun play() {
        if (!released) bridge.setPaused(false)
    }

    override fun pause() {
        if (!released) bridge.setPaused(true)
    }

    override fun seekTo(positionMs: Long) {
        if (!released) bridge.seekTo(positionMs.coerceAtLeast(0L))
    }

    override fun setVolume(volume: Float) {
        if (!released) bridge.setVolume(volume.coerceIn(0f, 1f))
    }

    override fun selectAudioTrack(trackId: String) {
        if (!released) bridge.selectAudioTrack(trackId)
    }

    override fun selectSubtitleTrack(trackId: String, externalUrl: String?) {
        if (!released) bridge.selectSubtitleTrack(trackId, externalUrl)
    }

    override fun setZoomMode(enabled: Boolean) {
        if (!released) bridge.setZoom(enabled)
    }

    override fun applySubtitlePreferences() = Unit

    override fun release() {
        if (released) return
        released = true
        bridge.shutdown()
    }
}

sealed interface IosPlayerRequest {
    class Item(
        val mediaId: String,
        val item: BaseItemDto?,
        val audioStreamIndex: Int?,
        val subtitleStreamIndex: Int?
    ) : IosPlayerRequest

    class Remote(val url: String, val title: String?) : IosPlayerRequest
}

/** Keeps the screen awake and drives brightness and volume for the player on iOS. */
class IosPlayerPlatform(private val engine: PlayerEngine) : PlayerPlatform {
    private var originalBrightness: Double? = null
    private var volume = 1f

    override fun enterPlayerMode() {
        originalBrightness = UIScreen.mainScreen.brightness
        UIApplication.sharedApplication.idleTimerDisabled = true
    }

    override fun exitPlayerMode(restoreVolume: Boolean) {
        UIApplication.sharedApplication.idleTimerDisabled = false
        originalBrightness?.let { UIScreen.mainScreen.brightness = it }
    }

    override fun hideSystemBars() = Unit

    override fun setBrightness(level: Float?) {
        val target = level?.toDouble() ?: originalBrightness ?: return
        UIScreen.mainScreen.brightness = target
    }

    override fun deviceBrightness(): Float = UIScreen.mainScreen.brightness.toFloat()

    // The system volume can't be set from an app, so the gesture volume scales the player's own volume.
    override fun deviceVolume(): Float = volume

    override fun setDeviceVolume(level: Float) {
        volume = level
        engine.setVolume(level)
    }

    override fun setHdrMode(enabled: Boolean) = Unit

    @Composable
    override fun HideSystemBarsForDialog() = Unit
}

@Composable
private fun IosVideoSurface(engine: IosMpvEngine, modifier: Modifier) {
    UIKitView(
        factory = { engine.createVideoView() },
        modifier = modifier,
        update = { engine.viewSizeChanged() },
        properties = UIKitInteropProperties(isInteractive = false, isNativeAccessibilityEnabled = false)
    )
}

/** The shared player screen running on the iOS mpv engine. */
@Composable
fun IosPlayerScreen(
    request: IosPlayerRequest,
    mediaRepository: MediaRepository,
    bridgeFactory: IosMpvBridgeFactory,
    onClose: () -> Unit
) {
    val preferences = remember {
        PlayerPreferences(UserDefaultsStore(NSUserDefaults(suiteName = PlayerPreferences.PREFS_NAME)))
    }
    val controller = remember(request) {
        EnginePlayerController(
            mediaRepository = mediaRepository,
            preferences = preferences,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Main),
            engineFactory = { IosMpvEngine(bridgeFactory.create()) },
            onPlaybackCompleted = { onClose() }
        )
    }
    val engine = controller.engine as IosMpvEngine
    val platform = remember(controller) { IosPlayerPlatform(engine) }

    DisposableEffect(controller) {
        platform.enterPlayerMode()
        when (request) {
            is IosPlayerRequest.Item -> controller.start(
                mediaId = request.mediaId,
                initialItemDetails = request.item,
                preferredAudioStreamIndex = request.audioStreamIndex,
                preferredSubtitleStreamIndex = request.subtitleStreamIndex
            )
            is IosPlayerRequest.Remote -> controller.startRemote(request.url, request.title)
        }
        onDispose {
            platform.exitPlayerMode(restoreVolume = false)
            controller.releasePlayer()
        }
    }

    PlayerContent(
        controller = controller,
        preferences = preferences,
        platform = platform,
        mediaId = when (request) {
            is IosPlayerRequest.Item -> request.mediaId
            is IosPlayerRequest.Remote -> request.url
        },
        sourceVideoHeight = controller.getSourceVideoHeight(),
        mediaInfoProvider = { null },
        onApplyPlaybackSettings = { _, _ -> controller.restart() },
        onBackPressed = onClose
    ) { surfaceModifier ->
        IosVideoSurface(engine, surfaceModifier)
    }
}
