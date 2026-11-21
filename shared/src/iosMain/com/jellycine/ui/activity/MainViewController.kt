package com.jellycine.ui.activity

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.jellycine.data.datastore.DataStoreProvider
import com.jellycine.data.preferences.NetworkPreferences
import com.jellycine.data.repository.*
import com.jellycine.data.security.SecureSessionStore
import com.jellycine.player.preferences.PlayerPreferences
import com.jellycine.preferences.*
import com.jellycine.ui.navigation.AppNavigation
import com.jellycine.ui.screens.detail.*
import com.jellycine.ui.screens.player.*
import com.jellycine.util.image.applyImageCacheSettings
import com.jellycine.util.text
import okio.Path.Companion.toPath
import platform.Foundation.*
import platform.UIKit.*

private const val IMAGE_CACHE_MAX_BYTES = 256L * 1024 * 1024
private const val IMAGE_MEMORY_AUTO_PERCENT = 0.15

private val imageCacheDirectory = run {
    val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
    "$caches/image_cache".toPath()
}

private val secureSessionStore = SecureSessionStore()

private val authRepository: AuthRepository by lazy {
    AuthRepository(
        dataStore = DataStoreProvider.getDataStore(),
        secureSessionStore = secureSessionStore,
        platform = IosAuthPlatform { message, args -> message.text(args) }
    )
}

private val mediaRepository: MediaRepository by lazy {
    MediaRepository(
        dataStore = DataStoreProvider.getDataStore(),
        secureSessionStore = secureSessionStore,
        platform = IosMediaPlatform { message, args -> message.text(args) }
    )
}

private val seerrRepository by lazy {
    SeerrRepository(SeerrPlatform(secureSessionStore) { message, args -> message.text(args) })
}

private val awardsRepository by lazy { AwardsRepository(mediaRepository) }

private val appVersion: String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""

private val preferences by lazy { Preferences(UserDefaultsStore()) }

private val networkPreferences by lazy { NetworkPreferences() }

/** Player settings, including the audio and subtitle choice, shared by the detail screen and the player. */
private val playerPreferences by lazy {
    PlayerPreferences(UserDefaultsStore(NSUserDefaults(suiteName = PlayerPreferences.PREFS_NAME)))
}

fun MainViewController(bridgeFactory: IosMpvBridgeFactory) = ComposeUIViewController {
    var playerRequest by remember { mutableStateOf<IosPlayerRequest?>(null) }
    var trackSelectionVersion by remember { mutableIntStateOf(0) }
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
                add(SvgDecoder.Factory())
            }
            .applyImageCacheSettings(
                context = context,
                settings = networkPreferences,
                diskDirectory = imageCacheDirectory,
                autoMemoryPercent = IMAGE_MEMORY_AUTO_PERCENT,
                autoDiskBytes = IMAGE_CACHE_MAX_BYTES
            )
            .build()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppNavigation(
            authRepository = authRepository,
            mediaRepository = mediaRepository,
            awardsRepository = awardsRepository,
            preferences = preferences,
            networkSettings = networkPreferences,
            seerrRepository = seerrRepository,
            appVersion = appVersion,
            detailPlatform = remember {
                DefaultDetailPlatform(
                    trackPreferences = PlayerPreferencesTrackPreferences(playerPreferences),
                    trackSelectionSyncVersion = { trackSelectionVersion },
                    onPlay = { item, fallbackItemId, audioStreamIndex, subtitleStreamIndex ->
                        playerRequest = IosPlayerRequest.Item(
                            mediaId = item?.id?.takeIf { it.isNotBlank() } ?: fallbackItemId,
                            item = item,
                            audioStreamIndex = audioStreamIndex,
                            subtitleStreamIndex = subtitleStreamIndex
                        )
                    },
                    onRemoteTrailer = { url, title -> playerRequest = IosPlayerRequest.Remote(url, title) },
                    seerr = SeerrDetailSupport(seerrRepository),
                    authRepository = authRepository,
                    themeMusic = ThemeMusicSupport(
                        player = IosMusicPlayer(),
                        mediaRepository = mediaRepository,
                        preferences = preferences,
                        isBlocked = { playerRequest != null }
                    )
                )
            },
            onOpenLanguageSettings = {
                NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
                    UIApplication.sharedApplication.openURL(url)
                }
            }
        )

        playerRequest?.let { request ->
            IosPlayerScreen(
                request = request,
                mediaRepository = mediaRepository,
                preferences = playerPreferences,
                bridgeFactory = bridgeFactory,
                onClose = {
                    trackSelectionVersion++
                    playerRequest = null
                }
            )
        }
    }
}
