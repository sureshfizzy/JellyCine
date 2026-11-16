package com.jellycine.shared.ui.activity

import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.jellycine.data.datastore.DataStoreProvider
import com.jellycine.data.repository.AuthRepository
import com.jellycine.data.repository.IosAuthPlatform
import com.jellycine.data.repository.IosMediaPlatform
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.security.SecureSessionStore
import com.jellycine.shared.ui.navigation.AppNavigation
import com.jellycine.shared.util.text
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

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

fun MainViewController() = ComposeUIViewController {
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }

    AppNavigation(
        authRepository = authRepository,
        mediaRepository = mediaRepository,
        onOpenLanguageSettings = {
            NSURL.URLWithString(UIApplicationOpenSettingsURLString)?.let { url ->
                UIApplication.sharedApplication.openURL(url)
            }
        }
    )
}
