package com.vela.player.preferences

import kotlin.math.roundToInt
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

    @Test
    fun defaultAssOverrideStripsEmbeddedBorders() {
        assertEquals("force", PlayerPreferences.mpvAssOverride(PlayerPreferences.DEFAULT_SUBTITLE_ASS_COMPATIBLE))
        val style = PlayerPreferences.mpvAssForceStyle(
            fontFamily = "Noto Sans CJK SC",
            edgeType = PlayerPreferences.DEFAULT_SUBTITLE_EDGE_TYPE,
            backgroundColor = PlayerPreferences.DEFAULT_SUBTITLE_BACKGROUND_COLOR,
            compatible = PlayerPreferences.DEFAULT_SUBTITLE_ASS_COMPATIBLE
        )
        assertTrue(style.contains("FontName=Noto Sans CJK SC"))
        assertTrue(style.contains("Outline=0"))
        assertTrue(style.contains("Shadow=0"))
        assertTrue(style.contains("BorderStyle=1"))
        assertTrue(style.contains("BackColour=&H00000000"))
        assertTrue(style.contains("OutlineColour=&H00000000"))
        assertTrue(style.contains("Alignment=2"))
        assertTrue(style.contains("MarginV=0"))
    }

    @Test
    fun assCompatibleForceStyleDoesNotOverrideEffects() {
        assertEquals(
            "",
            PlayerPreferences.mpvAssForceStyle(
                fontFamily = "Noto Sans CJK SC",
                edgeType = PlayerPreferences.SUBTITLE_EDGE_TYPE_NONE,
                backgroundColor = PlayerPreferences.SUBTITLE_BACKGROUND_TRANSPARENT,
                compatible = true
            )
        )
    }

    @Test
    fun landscapeScaleMapsPortrait175To125() {
        val userScale = 1.75f
        assertEquals(
            userScale,
            PlayerPreferences.mpvSubScaleForWindow(userScale, 1080, 1920),
            0.001f
        )
        assertEquals(
            1.25f,
            PlayerPreferences.mpvSubScaleForWindow(userScale, 1920, 1080),
            0.001f
        )
    }

    @Test
    fun mpvPosHugsVideoBottomInLandscapeAndDropsBelowInPortrait() {
        val userScale = 1.75f
        val landscapeRest = PlayerPreferences.mpvSubPosForWindow(0, 1920, 1080, userScale)
        val landscapeAtPortraitHabit = PlayerPreferences.mpvSubPosForWindow(30, 1920, 1080, userScale)
        val landscapeRaised = PlayerPreferences.mpvSubPosForWindow(60, 1920, 1080, userScale)
        assertTrue(landscapeRest in 90..99)
        assertTrue(kotlin.math.abs(landscapeAtPortraitHabit - landscapeRest) <= 2)
        assertTrue(landscapeRaised < landscapeAtPortraitHabit)

        val portraitRest = PlayerPreferences.mpvSubPosForWindow(0, 1080, 1920, userScale)
        val portraitRaised = PlayerPreferences.mpvSubPosForWindow(10, 1080, 1920, userScale)
        val videoBottomPos = (
            (
                PlayerPreferences.subtitleViewLetterboxInsetPx(1080, 1920) +
                    PlayerPreferences.fittedVideoHeightPx(1080, 1920)
                ) / 1920f * 100f
            ).roundToInt()
        assertTrue(portraitRest > videoBottomPos)
        assertTrue(portraitRest <= 150)
        assertTrue(portraitRaised < portraitRest)
    }

    @Test
    fun exoPixelSizeKeepsPortraitScaleAndShrinksInLandscape() {
        val userScale = 1.75f
        val landscape = PlayerPreferences.exoTextSizeFractionForWindow(userScale, 1920, 1080)
        val portrait = PlayerPreferences.exoTextSizeFractionForWindow(userScale, 1080, 1920)
        val landscapePx = landscape * 1080f
        val portraitPx = portrait * 1920f
        val portraitVideoHeight = PlayerPreferences.fittedVideoHeightPx(1080, 1920)
        assertEquals(
            PlayerPreferences.exoTextSizeFraction(userScale) * portraitVideoHeight,
            portraitPx,
            0.5f
        )
        assertEquals(
            PlayerPreferences.exoTextSizeFraction(1.25f) * 1080f,
            landscapePx,
            0.5f
        )
        assertTrue(landscapePx > portraitPx)
    }

    @Test
    fun exoPaddingHugsVideoBottomInLandscapeAndDropsBelowInPortrait() {
        assertEquals(0, PlayerPreferences.subtitleViewLetterboxInsetPx(1920, 1080))
        val userScale = 1.75f
        val landscapeRest = PlayerPreferences.subtitleViewBottomPaddingPx(
            0,
            1920,
            1080,
            userScale = userScale
        )
        val landscapeAtPortraitHabit = PlayerPreferences.subtitleViewBottomPaddingPx(
            30,
            1920,
            1080,
            userScale = userScale
        )
        val landscapeRaised = PlayerPreferences.subtitleViewBottomPaddingPx(
            60,
            1920,
            1080,
            userScale = userScale
        )
        assertTrue(landscapeRest > 0)
        assertTrue(landscapeRest < 80)
        assertTrue(kotlin.math.abs(landscapeAtPortraitHabit - landscapeRest) <= 8)
        assertTrue(landscapeRaised > landscapeAtPortraitHabit)

        val portraitInset = PlayerPreferences.subtitleViewLetterboxInsetPx(1080, 1920)
        val portraitBottom = PlayerPreferences.subtitleViewBottomPaddingPx(
            0,
            1080,
            1920,
            userScale = userScale
        )
        assertTrue(portraitBottom < portraitInset)
        assertTrue(portraitBottom >= 0)
    }
}
