package com.jellycine.app.ui.screens.dashboard.home

import androidx.annotation.StringRes
import com.jellycine.app.R

internal object HomeCategory {
    const val HOME = "home"
    const val MOVIES = "movies"
    const val TV_SHOWS = "tv_shows"

    val all = listOf(HOME, MOVIES, TV_SHOWS)

    @StringRes
    fun titleRes(category: String): Int {
        return when (category) {
            MOVIES -> R.string.movies
            TV_SHOWS -> R.string.tv_shows
            else -> R.string.home
        }
    }
}
