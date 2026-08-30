package com.vela.app.ui.screens.dashboard.media

import com.vela.data.model.DisplayPreferencesDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibrarySortTest {

    @Test
    fun matchedLibrarySortByUsesFirstKnownField() {
        assertEquals("DateCreated", matchedLibrarySortBy("DateCreated,SortName"))
        assertEquals("CommunityRating", matchedLibrarySortBy("CommunityRating"))
        assertNull(matchedLibrarySortBy("UnknownField"))
        assertNull(matchedLibrarySortBy(null))
    }

    @Test
    fun librarySortOrderNormalizesCase() {
        assertEquals("Ascending", librarySortOrder("ascending"))
        assertEquals("Descending", librarySortOrder("Descending"))
        assertEquals("Descending", librarySortOrder("nope", fallback = "Descending"))
        assertEquals("Ascending", librarySortOrder(null, fallback = "Ascending"))
    }

    @Test
    fun resolvedLibrarySortByReadsCustomPrefs() {
        val prefs = DisplayPreferencesDto(
            sortBy = null,
            sortOrder = null,
            customPrefs = mapOf("SortBy" to "PremiereDate", "SortOrder" to "Ascending")
        )
        assertEquals("PremiereDate", prefs.resolvedLibrarySortBy())
        assertEquals("Ascending", prefs.resolvedLibrarySortOrder("Descending"))
    }

    @Test
    fun librarySortMemoryKeyUsesSearchAndTagScopes() {
        assertEquals("srv|search:蜘蛛", librarySortMemoryKey("srv", null, "蜘蛛", null))
        assertEquals("srv|tag:中文字幕", librarySortMemoryKey("srv", "lib", null, "中文字幕"))
        assertEquals("srv|lib:abc", librarySortMemoryKey("srv", "abc", null, null))
        assertNull(librarySortMemoryKey("srv", WATCHED_VIEW_ALL_PARENT_ID, null, null))
    }
}
