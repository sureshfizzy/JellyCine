package com.jellycine.app.cast

import android.content.Context
import android.net.Uri
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaSeekOptions
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManager
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.google.android.gms.common.images.WebImage
import com.jellycine.app.R
import com.jellycine.shared.util.image.imageTagFor
import com.jellycine.data.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

data class CastPlaybackState(
    val isConnected: Boolean = false,
    val isCastingMedia: Boolean = false,
    val isPlaying: Boolean = false,
    val deviceName: String? = null,
    val mediaTitle: String? = null,
    val mediaSubtitle: String? = null,
    val artworkUrl: String? = null,
    val currentItemId: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val lastError: String? = null
)

data class CastRouteEntry(
    val id: String,
    val name: String,
    val description: String?,
    val isSelected: Boolean,
    val isConnecting: Boolean,
    val isEnabled: Boolean
)

object CastController {
    private const val CAST_TRACK_SELECTION_BITRATE = 80_000_000

    private val _playbackState = MutableStateFlow(CastPlaybackState())
    val playbackState: StateFlow<CastPlaybackState> = _playbackState.asStateFlow()
    private val _availableRoutes = MutableStateFlow<List<CastRouteEntry>>(emptyList())
    val availableRoutes: StateFlow<List<CastRouteEntry>> = _availableRoutes.asStateFlow()

    private var sessionManager: SessionManager? = null
    private var remoteMediaClient: RemoteMediaClient? = null
    private var mediaRouter: MediaRouter? = null
    private var routeSelector: MediaRouteSelector? = null
    private var isRouteCallbackRegistered = false
    private var initialized = false

    private val remoteMediaCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() = publishState()
        override fun onMetadataUpdated() = publishState()
        override fun onQueueStatusUpdated() = publishState()
        override fun onPreloadStatusUpdated() = publishState()
    }

    private val progressListener = RemoteMediaClient.ProgressListener { progressMs, durationMs ->
        _playbackState.update { state ->
            state.copy(
                positionMs = progressMs.coerceAtLeast(0L),
                durationMs = durationMs.coerceAtLeast(0L)
            )
        }
    }

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) = publishState()

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            attachRemoteClient(session.remoteMediaClient)
            publishState()
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            publishState(error = "Unable to start cast session ($error)")
        }

        override fun onSessionEnding(session: CastSession) = publishState()

        override fun onSessionEnded(session: CastSession, error: Int) {
            detachRemoteClient()
            val endedByError = error != 0
            publishState(error = if (endedByError) "Cast session ended ($error)" else null)
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) = publishState()

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            attachRemoteClient(session.remoteMediaClient)
            publishState()
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            publishState(error = "Unable to resume cast session ($error)")
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            publishState(error = "Cast session suspended")
        }
    }

    private val mediaRouterCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = publishAvailableRoutes()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = publishAvailableRoutes()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = publishAvailableRoutes()
        override fun onRouteSelected(router: MediaRouter, route: MediaRouter.RouteInfo) = publishAvailableRoutes()
        override fun onRouteUnselected(router: MediaRouter, route: MediaRouter.RouteInfo) = publishAvailableRoutes()
    }

    fun ensureInitialized(context: Context) {
        if (initialized) {
            publishState()
            return
        }

        runCatching {
            val appContext = context.applicationContext
            val activeCastContext = CastContext.getSharedInstance(appContext)
            val activeSessionManager = activeCastContext.sessionManager
            val receiverAppId = appContext.getString(R.string.cast_receiver_app_id)
                .ifBlank { CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID }
            val selector = MediaRouteSelector.Builder()
                .addControlCategory(CastMediaControlIntent.categoryForCast(receiverAppId))
                .build()

            sessionManager = activeSessionManager
            mediaRouter = MediaRouter.getInstance(appContext)
            routeSelector = selector
            activeSessionManager.addSessionManagerListener(
                sessionManagerListener,
                CastSession::class.java
            )
            attachRemoteClient(activeSessionManager.currentCastSession?.remoteMediaClient)
            initialized = true
        }.onFailure { throwable ->
            _playbackState.update { state ->
                state.copy(
                    isConnected = false,
                    isCastingMedia = false,
                    lastError = throwable.message
                )
            }
            _availableRoutes.value = emptyList()
            return
        }

        publishState()
    }

    fun startRouteDiscovery(context: Context) {
        ensureInitialized(context)
        val router = mediaRouter ?: return
        val selector = routeSelector ?: return
        if (!isRouteCallbackRegistered) {
            router.addCallback(
                selector,
                mediaRouterCallback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY or MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN
            )
            isRouteCallbackRegistered = true
        }
        publishAvailableRoutes()
    }

    fun stopRouteDiscovery() {
        val router = mediaRouter ?: return
        if (isRouteCallbackRegistered) {
            router.removeCallback(mediaRouterCallback)
            isRouteCallbackRegistered = false
        }
    }

    fun connectToRoute(context: Context, routeId: String): Result<Unit> {
        ensureInitialized(context)
        val router = mediaRouter ?: return Result.failure(IllegalStateException("Media router unavailable"))
        val route = router.routes.firstOrNull { it.id == routeId }
            ?: return Result.failure(IllegalArgumentException("Route not found"))
        if (!route.isEnabled) {
            return Result.failure(IllegalStateException("Route is not enabled"))
        }
        val result = runCatching {
            route.select()
            publishAvailableRoutes()
        }
        if (result.isSuccess) {
            _playbackState.update { state -> state.copy(lastError = null) }
        } else {
            result.exceptionOrNull()?.message?.let { message ->
                _playbackState.update { state -> state.copy(lastError = message) }
            }
        }
        return result
    }

    suspend fun castItem(
        context: Context,
        mediaRepository: MediaRepository,
        itemId: String,
        title: String?,
        subtitle: String?,
        itemType: String?,
        artworkUrl: String? = null,
        startPositionMs: Long = 0L,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null
    ): Result<Unit> {
        ensureInitialized(context)

        val castSession = sessionManager?.currentCastSession
        if (castSession?.isConnected != true) {
            return Result.failure(IllegalStateException("No cast device is connected"))
        }

        val payload = withContext(Dispatchers.IO) {
            buildLoadPayload(
                mediaRepository = mediaRepository,
                itemId = itemId,
                title = title,
                subtitle = subtitle,
                itemType = itemType,
                artworkUrl = artworkUrl,
                startPositionMs = startPositionMs,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex
            )
        }

        if (payload.isFailure) {
            val message = payload.exceptionOrNull()?.message
            _playbackState.update { state -> state.copy(lastError = message) }
            return Result.failure(payload.exceptionOrNull() ?: Exception("Failed to prepare cast media"))
        }

        return withContext(Dispatchers.Main.immediate) {
            loadMediaToCast(payload.getOrThrow())
        }
    }

    fun togglePlayPause(context: Context) {
        ensureInitialized(context)
        val client = getRemoteMediaClient() ?: return
        val playerState = client.mediaStatus?.playerState ?: MediaStatus.PLAYER_STATE_IDLE
        if (playerState == MediaStatus.PLAYER_STATE_PLAYING || playerState == MediaStatus.PLAYER_STATE_BUFFERING) {
            client.pause()
        } else {
            client.play()
        }
    }

    fun seekTo(context: Context, positionMs: Long) {
        ensureInitialized(context)
        val client = getRemoteMediaClient() ?: return
        client.seek(
            MediaSeekOptions.Builder()
                .setPosition(positionMs.coerceAtLeast(0L))
                .setResumeState(MediaSeekOptions.RESUME_STATE_UNCHANGED)
                .build()
        )
    }

    fun stopPlayback(context: Context) {
        ensureInitialized(context)
        getRemoteMediaClient()?.stop()
        publishState()
    }

    fun disconnect(context: Context) {
        ensureInitialized(context)
        sessionManager?.endCurrentSession(true)
        publishState()
    }

    private suspend fun buildLoadPayload(
        mediaRepository: MediaRepository,
        itemId: String,
        title: String?,
        subtitle: String?,
        itemType: String?,
        artworkUrl: String?,
        startPositionMs: Long,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?
    ): Result<CastLoadPayload> {
        return runCatching {
            val enforceTrackSelectionStream = audioStreamIndex != null || subtitleStreamIndex != null
            val streamingUrl = mediaRepository.getCastStreamingUrl(
                itemId = itemId,
                maxStreamingBitrate = if (enforceTrackSelectionStream) CAST_TRACK_SELECTION_BITRATE else null,
                audioStreamIndex = audioStreamIndex,
                subtitleStreamIndex = subtitleStreamIndex
            ).getOrThrow()
            val itemMetadata = mediaRepository.getItemById(itemId).getOrNull()

            val activeArtwork = artworkUrl
                ?: mediaRepository.getImageUrlString(
                    itemId = itemId,
                    imageType = "Primary",
                    width = 960,
                    height = 1440,
                    quality = 90,
                    enableImageEnhancers = false,
                    imageTag = itemMetadata?.imageTagFor(
                        imageType = "Primary",
                        targetItemId = itemId
                    )
                )
                ?: mediaRepository.getImageUrlString(
                    itemId = itemId,
                    imageType = "Backdrop",
                    width = 1280,
                    height = 720,
                    quality = 90,
                    enableImageEnhancers = false,
                    imageTag = itemMetadata?.imageTagFor(
                        imageType = "Backdrop",
                        targetItemId = itemId
                    )
                )

            CastLoadPayload(
                itemId = itemId,
                streamUrl = streamingUrl,
                title = title?.takeIf { it.isNotBlank() } ?: "JellyCine",
                subtitle = subtitle?.takeIf { it.isNotBlank() },
                itemType = itemType,
                artworkUrl = activeArtwork,
                startPositionMs = startPositionMs.coerceAtLeast(0L)
            )
        }
    }

    private suspend fun loadMediaToCast(payload: CastLoadPayload): Result<Unit> {
        val client = getRemoteMediaClient()
            ?: return Result.failure(IllegalStateException("Remote media client unavailable"))

        val mediaMetadataType = if (payload.itemType.equals("Audio", ignoreCase = true)) {
            MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
        } else {
            MediaMetadata.MEDIA_TYPE_MOVIE
        }

        val metadata = MediaMetadata(mediaMetadataType).apply {
            putString(MediaMetadata.KEY_TITLE, payload.title)
            payload.subtitle?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
            payload.artworkUrl
                ?.takeIf { it.isNotBlank() }
                ?.let { addImage(WebImage(Uri.parse(it))) }
        }

        val mediaInfo = MediaInfo.Builder(payload.streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(if (payload.itemType.equals("Audio", ignoreCase = true)) "audio/*" else "video/*")
            .setMetadata(metadata)
            .setCustomData(
                JSONObject().apply {
                    put("itemId", payload.itemId)
                    put("source", "jellycine")
                }
            )
            .build()

        val loadRequestData = MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .setCurrentTime(payload.startPositionMs)
            .build()

        return suspendCancellableCoroutine { continuation ->
            client.load(loadRequestData).setResultCallback { result ->
                if (!continuation.isActive) return@setResultCallback

                if (result.status.isSuccess) {
                    _playbackState.update { state ->
                        state.copy(
                            isConnected = true,
                            isCastingMedia = true,
                            isPlaying = true,
                            mediaTitle = payload.title,
                            mediaSubtitle = payload.subtitle,
                            artworkUrl = payload.artworkUrl,
                            currentItemId = payload.itemId,
                            positionMs = payload.startPositionMs,
                            lastError = null
                        )
                    }
                    publishState()
                    continuation.resume(Result.success(Unit))
                } else {
                    val message = result.status.statusMessage
                        ?.takeIf { it.isNotBlank() }
                        ?: "Could not start cast playback"
                    _playbackState.update { state -> state.copy(lastError = message) }
                    continuation.resume(Result.failure(IllegalStateException(message)))
                }
            }
        }
    }

    private fun getRemoteMediaClient(): RemoteMediaClient? {
        val client = sessionManager?.currentCastSession?.remoteMediaClient
        if (client != null) {
            attachRemoteClient(client)
        }
        return remoteMediaClient
    }

    private fun attachRemoteClient(client: RemoteMediaClient?) {
        if (remoteMediaClient === client) return
        detachRemoteClient()
        remoteMediaClient = client
        client?.registerCallback(remoteMediaCallback)
        client?.addProgressListener(progressListener, 1000L)
    }

    private fun detachRemoteClient() {
        remoteMediaClient?.removeProgressListener(progressListener)
        remoteMediaClient?.unregisterCallback(remoteMediaCallback)
        remoteMediaClient = null
    }

    private fun publishState(error: String? = null) {
        val session = sessionManager?.currentCastSession
        val connected = session?.isConnected == true
        val client = session?.remoteMediaClient ?: remoteMediaClient
        val status = client?.mediaStatus
        val mediaInfo = status?.mediaInfo
        val metadata = mediaInfo?.metadata
        val playerState = status?.playerState ?: MediaStatus.PLAYER_STATE_IDLE
        val isPlaying = playerState == MediaStatus.PLAYER_STATE_PLAYING ||
            playerState == MediaStatus.PLAYER_STATE_BUFFERING
        val metadataImageUrl = metadata?.images
            ?.firstOrNull()
            ?.url
            ?.toString()
        val itemId = mediaInfo?.customData
            ?.optString("itemId")
            ?.takeIf { it.isNotBlank() }

        _playbackState.update { previous ->
            if (!connected) {
                previous.copy(
                    isConnected = false,
                    isCastingMedia = false,
                    isPlaying = false,
                    deviceName = null,
                    mediaTitle = null,
                    mediaSubtitle = null,
                    artworkUrl = null,
                    currentItemId = null,
                    positionMs = 0L,
                    durationMs = 0L,
                    lastError = error ?: previous.lastError
                )
            } else {
                val hasMedia = mediaInfo != null
                previous.copy(
                    isConnected = true,
                    isCastingMedia = hasMedia,
                    isPlaying = hasMedia && isPlaying,
                    deviceName = session.castDevice?.friendlyName,
                    mediaTitle = metadata?.getString(MediaMetadata.KEY_TITLE)
                        ?: previous.mediaTitle,
                    mediaSubtitle = metadata?.getString(MediaMetadata.KEY_SUBTITLE)
                        ?: previous.mediaSubtitle,
                    artworkUrl = metadataImageUrl ?: previous.artworkUrl,
                    currentItemId = itemId ?: previous.currentItemId,
                    positionMs = client?.approximateStreamPosition?.coerceAtLeast(0L)
                        ?: previous.positionMs,
                    durationMs = mediaInfo?.streamDuration?.coerceAtLeast(0L)
                        ?: previous.durationMs,
                    lastError = error
                )
            }
        }
    }

    private fun publishAvailableRoutes() {
        val selector = routeSelector
        val routes = mediaRouter
            ?.routes
            .orEmpty()
            .filter { route ->
                val matchesCastCategory = selector?.let(route::matchesSelector) ?: true
                matchesCastCategory && !route.isDefaultOrBluetooth
            }
            .distinctBy { it.id }
            .map { route ->
                CastRouteEntry(
                    id = route.id,
                    name = route.name?.toString().orEmpty(),
                    description = route.description?.toString(),
                    isSelected = route.isSelected,
                    isConnecting = route.isConnecting || route.connectionState == MediaRouter.RouteInfo.CONNECTION_STATE_CONNECTING,
                    isEnabled = route.isEnabled
                )
            }
            .sortedWith(
                compareByDescending<CastRouteEntry> { it.isSelected }
                    .thenByDescending { it.isEnabled }
                    .thenBy { it.name.lowercase() }
            )

        _availableRoutes.value = routes
    }

    private data class CastLoadPayload(
        val itemId: String,
        val streamUrl: String,
        val title: String,
        val subtitle: String?,
        val itemType: String?,
        val artworkUrl: String?,
        val startPositionMs: Long
    )
}
