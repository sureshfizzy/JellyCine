package com.jellycine.app.ui.screens.dashboard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester

@Stable
internal class TvDashboardFocusState(
    val sidebar: FocusRequester,
    val homeHeader: FocusRequester,
    val homeHeaderEnd: FocusRequester,
    val homeContent: FocusRequester
) {
    private var homeReturnRequester: FocusRequester? = null

    fun updateHomeReturnFocus(focusRequester: FocusRequester) {
        homeReturnRequester = focusRequester
    }

    fun requestHomeReturnFocus(): Boolean {
        val target = homeReturnRequester ?: homeContent
        return runCatching {
            target.requestFocus()
            true
        }.getOrDefault(false)
    }
}

@Composable
internal fun rememberTvDashboardFocusState(): TvDashboardFocusState {
    val sidebar = remember { FocusRequester() }
    val homeHeader = remember { FocusRequester() }
    val homeHeaderEnd = remember { FocusRequester() }
    val homeContent = remember { FocusRequester() }
    return remember(sidebar, homeHeader, homeHeaderEnd, homeContent) {
        TvDashboardFocusState(
            sidebar = sidebar,
            homeHeader = homeHeader,
            homeHeaderEnd = homeHeaderEnd,
            homeContent = homeContent
        )
    }
}