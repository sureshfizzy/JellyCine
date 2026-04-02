package com.jellycine.app.ui.screens.dashboard

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.R

internal sealed class DashboardDestination(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : DashboardDestination(
        route = "dashboard_home",
        labelRes = R.string.home,
        selectedIcon = Icons.Rounded.Home,
        unselectedIcon = Icons.Rounded.Home
    )

    data object Library : DashboardDestination(
        route = "dashboard_library",
        labelRes = R.string.libraries,
        selectedIcon = Icons.Rounded.VideoLibrary,
        unselectedIcon = Icons.Rounded.VideoLibrary
    )

    data object Search : DashboardDestination(
        route = "dashboard_search",
        labelRes = R.string.search,
        selectedIcon = Icons.Rounded.Search,
        unselectedIcon = Icons.Rounded.Search
    )

    data object Favorites : DashboardDestination(
        route = "dashboard_favorites",
        labelRes = R.string.favorites,
        selectedIcon = Icons.Rounded.Favorite,
        unselectedIcon = Icons.Rounded.FavoriteBorder
    )

    data object Settings : DashboardDestination(
        route = "dashboard_settings",
        labelRes = R.string.settings,
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Rounded.Settings
    )
}

internal fun onlineDestinations(): List<DashboardDestination> = listOf(
    DashboardDestination.Home,
    DashboardDestination.Library,
    DashboardDestination.Search,
    DashboardDestination.Favorites,
    DashboardDestination.Settings
)

internal fun offlineDestinations(): List<DashboardDestination> = listOf(
    DashboardDestination.Home,
    DashboardDestination.Settings
)

@Composable
internal fun TvSidebarRail(
    destinations: List<DashboardDestination>,
    currentRoute: String,
    entryFocusRequester: FocusRequester? = null,
    onMoveRight: () -> Boolean,
    onDestinationSelected: (DashboardDestination) -> Unit,
    onRailFocusChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var railHasFocus by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(
        targetValue = if (railHasFocus) 118.dp else 30.dp,
        label = "tv_sidebar_width"
    )
    val itemSlotHeight = 36.dp
    val itemSpacing = 12.dp
    val frameVerticalPadding = 22.dp
    val railHeight = (itemSlotHeight * destinations.size) +
        (itemSpacing * destinations.lastIndex.coerceAtLeast(0)) +
        frameVerticalPadding

    Box(
        modifier = modifier
            .width(railWidth)
            .height(railHeight)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(railWidth)
                .height(railHeight)
                .onFocusChanged {
                    railHasFocus = it.hasFocus
                    onRailFocusChanged(it.hasFocus)
                }
                .focusGroup()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                destinations.forEach { destination ->
                    TvSidebarItem(
                        destination = destination,
                        isSelected = currentRoute == destination.route,
                        entryFocusRequester = if (currentRoute == destination.route) {
                            entryFocusRequester
                        } else {
                            null
                        },
                        showLabel = railHasFocus,
                        onMoveRight = onMoveRight,
                        onClick = { onDestinationSelected(destination) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TvSidebarItem(
    destination: DashboardDestination,
    isSelected: Boolean,
    entryFocusRequester: FocusRequester?,
    showLabel: Boolean,
    onMoveRight: () -> Boolean,
    onClick: () -> Unit
) {
    val selectedColor = Color(0xFF8A5CFF)
    var isFocused by remember(destination.route) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .then(
                if (entryFocusRequester != null) {
                    Modifier.focusRequester(entryFocusRequester)
                } else {
                    Modifier
                }
            )
            .fillMaxWidth()
            .padding(horizontal = if (showLabel) 8.dp else 4.dp)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionRight) {
                    onMoveRight()
                } else {
                    false
                }
            }
            .onFocusChanged { state ->
                isFocused = state.isFocused
            }
            .clickable(onClick = onClick)
            .focusable()
            .padding(
                horizontal = if (showLabel) 8.dp else 4.dp,
                vertical = 6.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (showLabel) Arrangement.Start else Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .width(if (isSelected || isFocused) 2.dp else 0.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (isSelected || isFocused) {
                        selectedColor.copy(alpha = if (isFocused) 0.95f else 0.72f)
                    } else {
                        Color.Transparent
                    }
                )
        )
        Icon(
            imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
            contentDescription = stringResource(destination.labelRes),
            tint = if (isSelected || isFocused) Color.White else Color.White.copy(alpha = 0.62f),
            modifier = Modifier
                .padding(start = if (showLabel || isSelected || isFocused) 10.dp else 0.dp)
                .size(19.dp)
        )
        if (showLabel) {
            Text(
                text = stringResource(destination.labelRes),
                color = if (isSelected || isFocused) Color.White else Color.White.copy(alpha = 0.54f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (isSelected || isFocused) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp)
            )
        }
    }
}
