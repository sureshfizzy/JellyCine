package com.vela.app.player.mpv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvPlaybackTuningTest {

    @Test
    fun normalSpeedKeepsSmoothMotionPath() {
        assertFalse(MpvPlaybackTuning.isHighSpeed(1.0))
        assertEquals("yes", MpvPlaybackTuning.interpolation(smoothMotion = true, speed = 1.0))
        assertEquals("display-resample", MpvPlaybackTuning.videoSync(smoothMotion = true, speed = 1.0))
        assertEquals("vo", MpvPlaybackTuning.framedrop(1.0))
        assertFalse(MpvPlaybackTuning.skipNonRefFrames(1.0))
    }

    @Test
    fun highSpeedDropsFramesAndDisablesInterpolation() {
        assertTrue(MpvPlaybackTuning.isHighSpeed(1.5))
        assertEquals("no", MpvPlaybackTuning.interpolation(smoothMotion = true, speed = 1.5))
        assertEquals("audio", MpvPlaybackTuning.videoSync(smoothMotion = true, speed = 1.5))
        assertEquals("decoder+vo", MpvPlaybackTuning.framedrop(1.5))
        assertFalse(MpvPlaybackTuning.skipNonRefFrames(1.5))
    }

    @Test
    fun doubleSpeedSkipsNonRefFrames() {
        assertTrue(MpvPlaybackTuning.skipNonRefFrames(2.0))
        assertEquals("decoder+vo", MpvPlaybackTuning.framedrop(2.5))
        assertEquals("audio", MpvPlaybackTuning.videoSync(smoothMotion = false, speed = 2.0))
        assertEquals("no", MpvPlaybackTuning.interpolation(smoothMotion = false, speed = 2.0))
    }
}
