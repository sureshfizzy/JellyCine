package com.jellycine.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class AndroidPlayerPlatform(context: Context) : PlayerPlatform {
    private val appContext = context.applicationContext
    private val activity: Activity? = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>()
        .firstOrNull()
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var originalOrientation: Int? = null
    private var originalVolume: Int? = null
    private var originalColorMode: Int? = null

    override fun enterPlayerMode() {
        val act = activity ?: return
        originalOrientation = act.requestedOrientation
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()
    }

    override fun exitPlayerMode(restoreVolume: Boolean) {
        val act = activity ?: return
        act.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        act.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (restoreVolume) {
            originalVolume?.let { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) }
        }
        setBrightness(null)
        WindowCompat.getInsetsController(act.window, act.window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    override fun hideSystemBars() {
        val act = activity ?: return
        WindowCompat.getInsetsController(act.window, act.window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun setBrightness(level: Float?) {
        val act = activity ?: return
        val params = act.window.attributes
        params.screenBrightness = level ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        act.window.attributes = params
    }

    override fun deviceBrightness(): Float? = runCatching {
        Settings.System.getInt(appContext.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
            .toFloat()
            .div(255f)
            .coerceIn(0.01f, 1f)
    }.getOrNull()

    override fun deviceVolume(): Float {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return 0f
        return audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    override fun setDeviceVolume(level: Float) {
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volume = (level * max).toInt().coerceIn(0, max)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }

    override fun setHdrMode(enabled: Boolean) {
        val window = activity?.window ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (enabled) {
            if (originalColorMode == null) originalColorMode = window.colorMode
            window.colorMode = ActivityInfo.COLOR_MODE_HDR
            setDesiredHdrHeadroomCompat(window, 4.0f)
        } else {
            val original = originalColorMode ?: return
            window.colorMode = original
            setDesiredHdrHeadroomCompat(window, 1.0f)
            originalColorMode = null
        }
    }

    @Composable
    override fun HideSystemBarsForDialog() {
        val view = LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            window?.let { dialogWindow ->
                WindowCompat.setDecorFitsSystemWindows(dialogWindow, false)
                val controller = WindowCompat.getInsetsController(dialogWindow, dialogWindow.decorView)
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            onDispose { }
        }
    }

    /**
     * Applies Window.setDesiredHdrHeadroom on Android 15+ via reflection.
     *
     * The API only exists on API 35+, but with R8 minification a direct call gets outlined
     * into a shared synthetic method whose unresolved reference throws NoSuchMethodError on
     * older devices (e.g. Android 14) even when the SDK_INT branch is never taken. Invoking
     * reflectively keeps any hard reference to the missing method out of the dex.
     */
    private fun setDesiredHdrHeadroomCompat(window: android.view.Window, headroom: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        runCatching {
            android.view.Window::class.java
                .getMethod("setDesiredHdrHeadroom", Float::class.javaPrimitiveType)
                .invoke(window, headroom)
        }
    }
}
