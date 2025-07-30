package com.jellycine.app

import android.app.Application
import com.jellycine.app.util.CrashHandler

class JellyCineApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize crash handler for better crash log collection
        CrashHandler.initialize(this)
    }
}
