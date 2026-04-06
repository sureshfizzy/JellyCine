package com.jellycine.player.core

import com.jellycine.data.model.ChapterInfo
import com.jellycine.data.model.PlaybackSegmentSource
import com.jellycine.data.model.PlaybackSegmentWindow

object PlaybackMarkerUtils {
    fun buildChapterMarkers(chapters: List<ChapterInfo>?): List<ChapterMarker> {
        return chapters
            ?.mapNotNull { chapter ->
                val positionMs = chapter.startPositionTicks
                    ?.takeIf { it >= 0L }
                    ?.div(10_000L)
                    ?: return@mapNotNull null
                ChapterMarker(
                    positionMs = positionMs,
                    label = chapter.name?.trim()?.takeIf { it.isNotEmpty() }
                )
            }
            ?.distinctBy { it.positionMs }
            ?.sortedBy { it.positionMs }
            .orEmpty()
    }

    fun extractIntroWindow(chapters: List<ChapterInfo>?): PlaybackSegmentWindow? {
        val chapterList = chapters
            ?.mapNotNull { chapter ->
                val positionMs = chapter.startPositionTicks
                    ?.takeIf { it >= 0L }
                    ?.div(10_000L)
                    ?: return@mapNotNull null
                chapter to positionMs
            }
            ?.sortedBy { it.second }
            .orEmpty()

        if (chapterList.isEmpty()) return null

        val introStartIndex = chapterList.indexOfFirst { (chapter, _) ->
            chapter.name.isIntroStartMarker()
        }
        if (introStartIndex == -1) return null

        val introStartMs = chapterList[introStartIndex].second
        val explicitIntroEndMs = chapterList
            .drop(introStartIndex + 1)
            .firstOrNull { (chapter, _) -> chapter.name.isIntroEndMarker() }
            ?.second
        val fallbackIntroEndMs = chapterList
            .getOrNull(introStartIndex + 1)
            ?.second
        val introEndMs = explicitIntroEndMs ?: fallbackIntroEndMs

        return introEndMs
            ?.takeIf { it > introStartMs }
            ?.let {
                PlaybackSegmentWindow(
                    startMs = introStartMs,
                    endMs = it,
                    source = PlaybackSegmentSource.SERVER_MARKER
                )
            }
    }

    private fun String?.isIntroStartMarker(): Boolean {
        val key = markerKey()
        return key == "intro" || key == "introstart"
    }

    private fun String?.isIntroEndMarker(): Boolean {
        return markerKey() == "introend"
    }

    private fun String?.markerKey(): String {
        return this
            ?.lowercase()
            ?.filterNot { it.isWhitespace() || it == '_' || it == '-' }
            .orEmpty()
    }
}
