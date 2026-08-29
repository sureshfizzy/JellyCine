package com.vela.app.ui.screens.dashboard.media

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
}
