package com.jellycine.app.ui.screens.dashboard.media

import androidx.annotation.StringRes
import com.jellycine.shared.R

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
    @StringRes val labelRes: Int
)

data class LibraryRecommendationSection(
    val recommendationType: String?,
    val baselineName: String?,
    val items: List<com.jellycine.data.model.BaseItemDto>
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
        LibraryBrowseTab.ITEMS -> if (contentType == ContentType.SERIES) {
            R.string.library_tab_shows
        } else {
            R.string.library_tab_items
        }
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
    LibrarySortField("CommunityRating", "Descending", R.string.library_sort_imdb),
    LibrarySortField("Resolution", "Descending", R.string.library_sort_resolution),
    LibrarySortField("DateCreated", "Descending", R.string.library_sort_date_added),
    LibrarySortField("PremiereDate", "Descending", R.string.library_sort_premiere),
    LibrarySortField("Container", "Ascending", R.string.library_sort_container),
    LibrarySortField("OfficialRating", "Ascending", R.string.library_sort_official_rating),
    LibrarySortField("People", "Ascending", R.string.library_sort_director),
    LibrarySortField("VideoFrameRate", "Descending", R.string.library_sort_framerate),
    LibrarySortField("ProductionYear", "Descending", R.string.library_sort_year),
    LibrarySortField("CriticRating", "Descending", R.string.library_sort_critic),
    LibrarySortField("DatePlayed", "Descending", R.string.library_sort_date_played),
    LibrarySortField("Runtime", "Descending", R.string.library_sort_runtime),
    LibrarySortField("PlayCount", "Descending", R.string.library_sort_play_count),
    LibrarySortField("SortName", "Ascending", R.string.library_sort_filename),
    LibrarySortField("Size", "Descending", R.string.library_sort_size)
)

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
