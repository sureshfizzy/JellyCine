package com.jellycine.app

import android.app.Application
import coil3.PlatformContext
import coil3.ImageLoader
import coil3.SingletonImageLoader
import com.jellycine.app.util.logging.CrashHandler
import com.jellycine.app.util.image.ImageLoaderConfig
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class JellyCineApplication : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()

        // Initialize crash handler for better crash log collection
        CrashHandler.initialize(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoaderConfig.createOptimizedImageLoader(context)
    }
}

