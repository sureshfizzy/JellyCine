package com.jellycine.shared.ui.screens.dashboard.home

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Stable
object ScrollOptimization {
    @Composable
    fun rememberUltraSmoothFlingBehavior(): FlingBehavior {
        return ScrollableDefaults.flingBehavior()
    }

    val optimizedContentPadding = PaddingValues(horizontal = 16.dp)

    val optimizedSpacing = 12.dp

    val listItemSpacing = 8.dp

    fun getScrollContainerModifier(): Modifier = Modifier
        .fillMaxWidth()
}
