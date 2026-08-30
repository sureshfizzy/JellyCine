package com.vela.app.ui.activity

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.core.view.WindowCompat
import androidx.media3.common.util.UnstableApi
import com.vela.app.locale.AppLanguageManager
import com.vela.app.ui.player.PictureInPictureHost
import com.vela.app.ui.player.applyPlayerPipParams
import com.vela.app.ui.player.findActivity
import com.vela.app.ui.screens.player.PlayerScreen
import com.vela.data.repository.MediaRepositoryProvider
import com.vela.shared.ui.theme.VelaTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@UnstableApi
@AndroidEntryPoint
class PlayerActivity : ComponentActivity(), PictureInPictureHost {

    private val pipMode = MutableStateFlow(false)
    private val playbackArgs = MutableStateFlow<PlaybackArgs?>(null)

    override val pictureInPictureMode: StateFlow<Boolean> = pipMode.asStateFlow()
    override var userLeaveHintHandler: (() -> Unit)? = null

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppLanguageManager.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppLanguageManager.applySavedLanguage(this)
        applyEdgeToEdgeSystemBars()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
        playbackArgs.value = PlaybackArgs.from(intent)
        pipMode.value = isInPictureInPictureMode

        setContent {
            VelaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ComposeColor.Black
                ) {
                    val args by playbackArgs.collectAsState()
                    val current = args ?: return@Surface
                    PlayerRoute(args = current)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        playbackArgs.value = PlaybackArgs.from(intent)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        userLeaveHintHandler?.invoke()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipMode.value = isInPictureInPictureMode
        applyEdgeToEdgeSystemBars()
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun applyEdgeToEdgeSystemBars() {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    companion object {
        const val EXTRA_MEDIA_ID = "media_id"
        const val EXTRA_FROM_START = "from_start"
        const val EXTRA_SEEK_MS = "seek_ms"
        const val EXTRA_MEDIA_SOURCE_ID = "media_source_id"
        const val EXTRA_AUDIO_INDEX = "audio_index"
        const val EXTRA_SUBTITLE_INDEX = "subtitle_index"
        const val EXTRA_REMOTE_URL = "remote_url"
        const val EXTRA_REMOTE_TITLE = "remote_title"

        fun start(
            context: Context,
            mediaId: String,
            startFromBeginning: Boolean = false,
            seekPositionMs: Long? = null,
            mediaSourceId: String? = null,
            audioStreamIndex: Int? = null,
            subtitleStreamIndex: Int? = null,
            remoteUrl: String? = null,
            remoteTitle: String? = null
        ) {
            if (mediaId.isBlank() && remoteUrl.isNullOrBlank()) return
            val activity = context.findActivity()
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_MEDIA_ID, mediaId)
                putExtra(EXTRA_FROM_START, startFromBeginning)
                seekPositionMs?.let { putExtra(EXTRA_SEEK_MS, it) }
                mediaSourceId?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_MEDIA_SOURCE_ID, it) }
                audioStreamIndex?.let { putExtra(EXTRA_AUDIO_INDEX, it) }
                subtitleStreamIndex?.let { putExtra(EXTRA_SUBTITLE_INDEX, it) }
                remoteUrl?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_REMOTE_URL, it) }
                remoteTitle?.takeIf { it.isNotBlank() }?.let { putExtra(EXTRA_REMOTE_TITLE, it) }
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                if (activity == null) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            @Suppress("DEPRECATION")
            activity?.overridePendingTransition(0, 0)
        }
    }
}

private data class PlaybackArgs(
    val mediaId: String,
    val startFromBeginning: Boolean,
    val seekPositionMs: Long?,
    val mediaSourceId: String?,
    val audioStreamIndex: Int?,
    val subtitleStreamIndex: Int?,
    val remoteUrl: String?,
    val remoteTitle: String?
) {
    companion object {
        fun from(intent: Intent?): PlaybackArgs? {
            if (intent == null) return null
            val mediaId = intent.getStringExtra(PlayerActivity.EXTRA_MEDIA_ID).orEmpty()
            val remoteUrl = intent.getStringExtra(PlayerActivity.EXTRA_REMOTE_URL)
            if (mediaId.isBlank() && remoteUrl.isNullOrBlank()) return null
            return PlaybackArgs(
                mediaId = mediaId.ifBlank { "remote_${remoteUrl.hashCode()}" },
                startFromBeginning = intent.getBooleanExtra(PlayerActivity.EXTRA_FROM_START, false),
                seekPositionMs = if (intent.hasExtra(PlayerActivity.EXTRA_SEEK_MS)) {
                    intent.getLongExtra(PlayerActivity.EXTRA_SEEK_MS, 0L)
                } else {
                    null
                },
                mediaSourceId = intent.getStringExtra(PlayerActivity.EXTRA_MEDIA_SOURCE_ID),
                audioStreamIndex = if (intent.hasExtra(PlayerActivity.EXTRA_AUDIO_INDEX)) {
                    intent.getIntExtra(PlayerActivity.EXTRA_AUDIO_INDEX, 0)
                } else {
                    null
                },
                subtitleStreamIndex = if (intent.hasExtra(PlayerActivity.EXTRA_SUBTITLE_INDEX)) {
                    intent.getIntExtra(PlayerActivity.EXTRA_SUBTITLE_INDEX, 0)
                } else {
                    null
                },
                remoteUrl = remoteUrl,
                remoteTitle = intent.getStringExtra(PlayerActivity.EXTRA_REMOTE_TITLE)
            )
        }
    }
}

@UnstableApi
@androidx.compose.runtime.Composable
private fun PlayerRoute(args: PlaybackArgs) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val mediaRepository = remember { MediaRepositoryProvider.getInstance(context) }
    var mediaId by remember(args.mediaId, args.remoteUrl) { mutableStateOf(args.mediaId) }
    var previousEpisodeId by remember { mutableStateOf<String?>(null) }
    var nextEpisodeId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(args) {
        mediaId = args.mediaId
    }

    LaunchedEffect(mediaId, args.remoteUrl) {
        if (!args.remoteUrl.isNullOrBlank()) {
            previousEpisodeId = null
            nextEpisodeId = null
            return@LaunchedEffect
        }
        val navigation = mediaRepository.getEpisodeNavigationIds(mediaId)
        previousEpisodeId = navigation.previousEpisodeId
        nextEpisodeId = navigation.nextEpisodeId
    }

    LaunchedEffect(Unit) {
        context.findActivity()?.let { applyPlayerPipParams(it, playing = true) }
    }

    PlayerScreen(
        mediaId = mediaId,
        remoteMediaUrl = args.remoteUrl,
        remoteMediaTitle = args.remoteTitle,
        preferredAudioStreamIndex = args.audioStreamIndex,
        preferredSubtitleStreamIndex = args.subtitleStreamIndex,
        startFromBeginning = args.startFromBeginning,
        initialSeekPositionMs = args.seekPositionMs,
        mediaSourceId = args.mediaSourceId,
        previousEpisodeId = previousEpisodeId,
        nextEpisodeId = nextEpisodeId,
        onWatchPreviousEpisode = { episodeId -> mediaId = episodeId },
        onWatchNextEpisode = { episodeId -> mediaId = episodeId },
        onPlaybackCompleted = { completedId ->
            val nextId = nextEpisodeId
            if (!nextId.isNullOrBlank() && nextId != completedId) {
                mediaId = nextId
            }
        },
        onBackPressed = {
            context.findActivity()?.finish()
        }
    )
}
