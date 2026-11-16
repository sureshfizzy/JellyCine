package com.jellycine.shared.ui.screens.dashboard.home

import com.jellycine.shared.resources.Res
import com.jellycine.shared.resources.home
import com.jellycine.shared.resources.movies
import com.jellycine.shared.resources.tv_shows
import org.jetbrains.compose.resources.StringResource

object HomeCategory {
    const val HOME = "home"
    const val MOVIES = "movies"
    const val TV_SHOWS = "tv_shows"

    val all = listOf(HOME, MOVIES, TV_SHOWS)

    fun titleRes(category: String): StringResource {
        return when (category) {
            MOVIES -> Res.string.movies
            TV_SHOWS -> Res.string.tv_shows
            else -> Res.string.home
        }
    }
}
