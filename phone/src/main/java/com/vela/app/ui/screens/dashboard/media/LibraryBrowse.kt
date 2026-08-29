package com.vela.app.ui.screens.dashboard.media

import androidx.annotation.StringRes
import com.vela.shared.R

enum class LibraryBrowseTab {
    ITEMS,
    RECOMMENDED,
    TRAILERS,
    GENRES,
    COLLECTIONS,
    FOLDERS
}

data class LibrarySortField(
    val sortBy: String,
    val defaultOrder: String,
    @StringRes val labelRes: Int,
    val supportsOrder: Boolean = true,
    val primary: Boolean = true
)

data class LibraryRecommendationSection(
    val recommendationType: String?,
    val baselineName: String?,
    val items: List<com.vela.data.model.BaseItemDto>
)

data class LibraryFolderNav(
    val id: String,
    val name: String
)

data class LibraryGenreNav(
    val id: String,
    val name: String
)

fun libraryBrowseTabs(contentType: ContentType): List<LibraryBrowseTab> {
    return when (contentType) {
        ContentType.MOVIES -> listOf(
            LibraryBrowseTab.ITEMS,
            LibraryBrowseTab.RECOMMENDED,
            LibraryBrowseTab.TRAILERS,
            LibraryBrowseTab.GENRES,
            LibraryBrowseTab.COLLECTIONS,
            LibraryBrowseTab.FOLDERS
        )
        ContentType.SERIES -> listOf(
            LibraryBrowseTab.ITEMS,
            LibraryBrowseTab.RECOMMENDED,
            LibraryBrowseTab.GENRES,
            LibraryBrowseTab.FOLDERS
        )
        ContentType.ALL -> listOf(
            LibraryBrowseTab.ITEMS,
            LibraryBrowseTab.RECOMMENDED,
            LibraryBrowseTab.GENRES,
            LibraryBrowseTab.COLLECTIONS,
            LibraryBrowseTab.FOLDERS
        )
        else -> emptyList()
    }
}

@StringRes
fun LibraryBrowseTab.labelRes(contentType: ContentType): Int {
    return when (this) {
        LibraryBrowseTab.ITEMS -> R.string.library_tab_all
        LibraryBrowseTab.RECOMMENDED -> R.string.library_tab_recommended
        LibraryBrowseTab.TRAILERS -> R.string.library_tab_trailers
        LibraryBrowseTab.GENRES -> R.string.library_tab_genres
        LibraryBrowseTab.COLLECTIONS -> R.string.library_tab_collections
        LibraryBrowseTab.FOLDERS -> R.string.library_tab_folders
    }
}

fun LibraryBrowseTab.supportsSort(): Boolean {
    return this == LibraryBrowseTab.ITEMS ||
        this == LibraryBrowseTab.TRAILERS ||
        this == LibraryBrowseTab.COLLECTIONS ||
        this == LibraryBrowseTab.FOLDERS
}

fun librarySortFields(): List<LibrarySortField> = listOf(
    LibrarySortField("DateLastContentAdded", "Descending", R.string.library_sort_date_updated),
    LibrarySortField("DateCreated", "Descending", R.string.library_sort_date_added),
    LibrarySortField("CommunityRating", "Descending", R.string.library_sort_community),
    LibrarySortField("SortName", "Ascending", R.string.library_sort_title),
    LibrarySortField("PremiereDate", "Descending", R.string.library_sort_premiere),
    LibrarySortField("OfficialRating", "Ascending", R.string.library_sort_official_rating),
    LibrarySortField("ProductionYear", "Descending", R.string.library_sort_year),
    LibrarySortField("CriticRating", "Descending", R.string.library_sort_critic),
    LibrarySortField("DatePlayed", "Descending", R.string.library_sort_date_played),
    LibrarySortField("Runtime", "Descending", R.string.library_sort_runtime),
    LibrarySortField("Bitrate", "Descending", R.string.library_sort_bitrate),
    LibrarySortField("Size", "Descending", R.string.library_sort_size),
    LibrarySortField("Random", "Ascending", R.string.library_sort_random, supportsOrder = false),
    LibrarySortField("Resolution", "Descending", R.string.library_sort_resolution, primary = false),
    LibrarySortField("Container", "Ascending", R.string.library_sort_container, primary = false),
    LibrarySortField("VideoFrameRate", "Descending", R.string.library_sort_framerate, primary = false),
    LibrarySortField("People", "Ascending", R.string.library_sort_director, primary = false),
    LibrarySortField("PlayCount", "Descending", R.string.library_sort_play_count, primary = false)
)

fun matchedLibrarySortBy(raw: String?): String? {
    val candidate = raw
        ?.split(',')
        ?.map { it.trim() }
        ?.firstOrNull { it.isNotBlank() }
        ?: return null
    return librarySortFields().firstOrNull { field ->
        field.sortBy.equals(candidate, ignoreCase = true)
    }?.sortBy
}

fun librarySortOrder(raw: String?, fallback: String = "Descending"): String {
    return when {
        raw.equals("Ascending", ignoreCase = true) -> "Ascending"
        raw.equals("Descending", ignoreCase = true) -> "Descending"
        else -> fallback
    }
}

@StringRes
fun recommendationTitleRes(recommendationType: String?): Int {
    return when (recommendationType) {
        "HasDirectorFromRecentlyPlayed",
        "HasLikedDirector" -> R.string.library_rec_director
        "HasActorFromRecentlyPlayed",
        "HasLikedActor" -> R.string.library_rec_actor
        "SimilarToLikedItem" -> R.string.library_rec_liked
        "SimilarToRecentlyPlayed" -> R.string.library_rec_watched
        else -> R.string.library_tab_recommended
    }
}

internal const val LIBRARY_ITEM_FIELDS =
    "ChildCount,RecursiveItemCount,EpisodeCount,SeriesName,SeriesId,Genres,CommunityRating,CriticRating,ProductionYear,Overview,UserData,CanDelete,CanDownload,LockData,ProviderIds,OfficialRating,PremiereDate,RunTimeTicks,People"
