package com.jellycine.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import com.jellycine.app.util.CrashHandler
import com.jellycine.app.util.ImageLoaderConfig
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class JellyCineApplication : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()

        // Initialize crash handler for better crash log collection
        CrashHandler.initialize(this)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoaderConfig.createOptimizedImageLoader(this)
    }
}
