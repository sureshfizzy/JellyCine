package com.jellycine.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Window, display and audio controls the player screen needs from the host platform.
 * Android implements it over Activity/Window/AudioManager, iOS over UIKit.
 */
interface PlayerPlatform {
    /** Landscape, keep the screen on and hide the system bars for the duration of playback. */
    fun enterPlayerMode()

    /** Undo [enterPlayerMode]; optionally put the device volume back to what it was. */
    fun exitPlayerMode(restoreVolume: Boolean)

    fun hideSystemBars()

    /** Window brightness in 0.01..1, or null to go back to the system setting. */
    fun setBrightness(level: Float?)

    /** Current device brightness in 0.01..1, or null when it can't be read. */
    fun deviceBrightness(): Float?

    /** Device media volume in 0..1. */
    fun deviceVolume(): Float

    fun setDeviceVolume(level: Float)

    /** Switch the display to an HDR color mode; false restores the previous mode. */
    fun setHdrMode(enabled: Boolean)

    /** Call from inside a dialog's content to hide the system bars for that dialog's window. */
    @Composable
    fun HideSystemBarsForDialog()
}

val LocalPlayerPlatform = staticCompositionLocalOf<PlayerPlatform> {
    error("No PlayerPlatform provided")
}
