package com.jellycine.ui.activity

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.svg.SvgDecoder
import com.jellycine.data.datastore.DataStoreProvider
import com.jellycine.data.repository.AuthRepository
import com.jellycine.data.repository.AwardsRepository
import com.jellycine.data.repository.IosAuthPlatform
import com.jellycine.data.repository.IosMediaPlatform
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.preferences.NetworkPreferences
import com.jellycine.data.security.SecureSessionStore
import com.jellycine.preferences.Preferences
import com.jellycine.preferences.UserDefaultsStore
import com.jellycine.ui.navigation.AppNavigation
import com.jellycine.ui.screens.detail.DefaultDetailPlatform
import com.jellycine.ui.screens.player.IosMpvBridgeFactory
import com.jellycine.ui.screens.player.IosPlayerRequest
import com.jellycine.ui.screens.player.IosPlayerScreen
import com.jellycine.util.text
import okio.Path.Companion.toPath
import platform.Foundation.NSBundle
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

private const val IMAGE_CACHE_MAX_BYTES = 256L * 1024 * 1024

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

private val awardsRepository by lazy { AwardsRepository(mediaRepository) }

private val appVersion: String =
    NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: ""

private val preferences by lazy { Preferences(UserDefaultsStore()) }

private val networkPreferences by lazy { NetworkPreferences() }

fun MainViewController(bridgeFactory: IosMpvBridgeFactory) = ComposeUIViewController {
    var playerRequest by remember { mutableStateOf<IosPlayerRequest?>(null) }
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory())
                add(SvgDecoder.Factory())
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(imageCacheDirectory)
                    .maxSizeBytes(IMAGE_CACHE_MAX_BYTES)
                    .build()
            }
            .build()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AppNavigation(
            authRepository = authRepository,
            mediaRepository = mediaRepository,
            awardsRepository = awardsRepository,
            preferences = preferences,
            networkSettings = networkPreferences,
            appVersion = appVersion,
            detailPlatform = remember {
                DefaultDetailPlatform(
                    onPlay = { item, fallbackItemId, audioStreamIndex, subtitleStreamIndex ->
                        playerRequest = IosPlayerRequest.Item(
                            mediaId = item?.id?.takeIf { it.isNotBlank() } ?: fallbackItemId,
                            item = item,
                            audioStreamIndex = audioStreamIndex,
                            subtitleStreamIndex = subtitleStreamIndex
                        )
                    },
                    onRemoteTrailer = { url, title -> playerRequest = IosPlayerRequest.Remote(url, title) }
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
                bridgeFactory = bridgeFactory,
                onClose = { playerRequest = null }
            )
        }
    }
}
