package com.vela.player.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleAppearanceTest {

    @Test
    fun discreteSizesMapToScaleFactors() {
        assertEquals(0.85f, PlayerPreferences.scaleForTextSize(PlayerPreferences.SUBTITLE_TEXT_SIZE_SMALL))
        assertEquals(1.0f, PlayerPreferences.scaleForTextSize(PlayerPreferences.SUBTITLE_TEXT_SIZE_NORMAL))
        assertEquals(1.25f, PlayerPreferences.scaleForTextSize(PlayerPreferences.SUBTITLE_TEXT_SIZE_LARGE))
        assertEquals(1.5f, PlayerPreferences.scaleForTextSize(PlayerPreferences.SUBTITLE_TEXT_SIZE_EXTRA_LARGE))
        assertEquals(1.5f, PlayerPreferences.scaleForTextSize("unknown"))
    }

    @Test
    fun continuousScaleSnapsToNearestDiscreteSize() {
        assertEquals(
            PlayerPreferences.SUBTITLE_TEXT_SIZE_SMALL,
            PlayerPreferences.textSizeForScale(0.85f)
        )
        assertEquals(
            PlayerPreferences.SUBTITLE_TEXT_SIZE_NORMAL,
            PlayerPreferences.textSizeForScale(1.0f)
        )
        assertEquals(
            PlayerPreferences.SUBTITLE_TEXT_SIZE_LARGE,
            PlayerPreferences.textSizeForScale(1.25f)
        )
        assertEquals(
            PlayerPreferences.SUBTITLE_TEXT_SIZE_EXTRA_LARGE,
            PlayerPreferences.textSizeForScale(2.46f)
        )
    }

    @Test
    fun bottomPercentMapsToMpvSubPos() {
        assertEquals(100, PlayerPreferences.mpvSubPosFromBottomPercent(0))
        assertEquals(90, PlayerPreferences.mpvSubPosFromBottomPercent(10))
        assertEquals(50, PlayerPreferences.mpvSubPosFromBottomPercent(50))
        assertEquals(0, PlayerPreferences.mpvSubPosFromBottomPercent(100))
        assertEquals(0, PlayerPreferences.mpvSubPosFromBottomPercent(150))
        assertEquals(100, PlayerPreferences.mpvSubPosFromBottomPercent(-8))
    }

    @Test
    fun exoFractionScalesFromNormalBaseline() {
        assertEquals(0.0533f, PlayerPreferences.exoTextSizeFraction(1.0f), 0.0001f)
        assertEquals(0.1066f, PlayerPreferences.exoTextSizeFraction(2.0f), 0.0001f)
        assertEquals(0.02665f, PlayerPreferences.exoTextSizeFraction(0.5f), 0.0001f)
        assertTrue(PlayerPreferences.exoTextSizeFraction(5.0f) <= 0.25f)
        assertTrue(PlayerPreferences.exoTextSizeFraction(0.1f) >= 0.02f)
    }

    @Test
    fun assCompatibleLeavesStylesButAllowsScale() {
        assertEquals("scale", PlayerPreferences.mpvAssOverride(true))
        assertEquals("force", PlayerPreferences.mpvAssOverride(false))
    }
}
