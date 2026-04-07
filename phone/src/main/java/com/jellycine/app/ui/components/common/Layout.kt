package com.jellycine.app.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun containerWidthDp(): Dp {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    return with(density) { windowInfo.containerSize.width.toDp() }
}

@Composable
fun containerHeightDp(): Dp {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    return with(density) { windowInfo.containerSize.height.toDp() }
}

fun isTabletLayout(screenWidthDp: Dp): Boolean = screenWidthDp >= 600.dp

fun detailContentMaxWidth(
    screenWidthDp: Dp,
    horizontalPadding: Dp
): Dp? {
    return if (isTabletLayout(screenWidthDp)) {
        (screenWidthDp - (horizontalPadding * 2)).coerceAtMost(780.dp)
    } else {
        null
    }
}

fun detailActionWidth(screenWidthDp: Dp): Float =
    if (isTabletLayout(screenWidthDp)) 0.40f else 1f
