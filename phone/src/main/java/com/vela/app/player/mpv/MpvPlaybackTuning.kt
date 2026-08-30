package com.vela.app.player.mpv

internal object MpvPlaybackTuning {
    private const val NORMAL_SPEED = 1.0
    private const val HIGH_SPEED_EPSILON = 0.01
    private const val SKIP_NONREF_SPEED = 2.0

    fun isHighSpeed(speed: Double): Boolean {
        return speed > NORMAL_SPEED + HIGH_SPEED_EPSILON
    }

    fun interpolation(smoothMotion: Boolean, speed: Double): String {
        return if (smoothMotion && !isHighSpeed(speed)) "yes" else "no"
    }

    fun videoSync(smoothMotion: Boolean, speed: Double): String {
        return if (smoothMotion && !isHighSpeed(speed)) "display-resample" else "audio"
    }

    fun framedrop(speed: Double): String {
        return if (isHighSpeed(speed)) "decoder+vo" else "vo"
    }

    fun skipNonRefFrames(speed: Double): Boolean {
        return speed >= SKIP_NONREF_SPEED
    }
}
