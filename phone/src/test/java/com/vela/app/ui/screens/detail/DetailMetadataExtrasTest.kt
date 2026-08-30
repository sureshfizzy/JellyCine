package com.vela.app.ui.screens.detail

import com.vela.data.model.BaseItemDto
import com.vela.data.model.NameGuidPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DetailMetadataExtrasTest {

    @Test
    fun metadataAggregationTagsDedupesTagsGenresAndStudios() {
        val item = BaseItemDto(
            tags = listOf("中文字幕", "SSNI", "动作"),
            genres = listOf("动作", "剧情"),
            studios = listOf(NameGuidPair(name = "S1 NO.1 STYLE"))
        )
        assertEquals(
            listOf("中文字幕", "SSNI", "动作", "剧情", "S1 NO.1 STYLE"),
            metadataAggregationTags(item)
        )
    }

    @Test
    fun formatStreamResolutionRequiresBothDimensions() {
        assertEquals("1920x1080", formatStreamResolution(1920, 1080))
        assertNull(formatStreamResolution(1920, null))
        assertNull(formatStreamResolution(0, 1080))
    }
}
