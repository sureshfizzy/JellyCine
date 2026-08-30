package com.vela.app.ui.screens.dashboard.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryImageStyleTest {

    @Test
    fun bannerFillsUniformWideFrame() {
        val style = LibraryImageStyle.BANNER
        assertEquals("Banner", style.imageType)
        assertEquals("Thumb", style.fallbackImageType)
        assertTrue(style.cropImage)
        assertEquals(1000f / 185f, style.aspectRatio, 0.001f)
    }

    @Test
    fun backdropUsesLandscapeImage() {
        val style = LibraryImageStyle.BACKDROP
        assertEquals("Backdrop", style.imageType)
        assertEquals("Thumb", style.fallbackImageType)
        assertTrue(style.cropImage)
        assertEquals(16f / 9f, style.aspectRatio, 0.001f)
    }

    @Test
    fun posterIsDefaultPersistedValue() {
        assertEquals(LibraryImageStyle.POSTER, LibraryImageStyle.fromPersisted(null))
        assertEquals(LibraryImageStyle.BANNER, LibraryImageStyle.fromPersisted("banner"))
    }
}
