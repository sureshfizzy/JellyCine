package com.jellycine.ui.screens.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DashboardShellScope internal constructor(
    val currentRoute: String,
    val homeScrollState: LazyListState,
    val navigateTo: (DashboardDestination) -> Unit
)

private val offlineAllowedRoutes = setOf(
    DashboardDestination.Home.route,
    DashboardDestination.Settings.route
)

@Composable
fun DashboardShell(
    isNetworkAvailable: Boolean,
    isOnline: Boolean,
    modifier: Modifier = Modifier,
    overlay: @Composable DashboardShellScope.() -> Unit = {},
    content: @Composable DashboardShellScope.(route: String) -> Unit
) {
    var currentRoute by rememberSaveable { mutableStateOf(DashboardDestination.Home.route) }
    val homeScrollState = rememberLazyListState()
    var homeScrollToTop by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val navigationBarInsetPx = WindowInsets.navigationBars.getBottom(density).toFloat()
    val bottomBarHideDistancePx =
        with(density) { (DashboardNavigationBarHeight + 36.dp).toPx() } + navigationBarInsetPx
    val hideThresholdPx = with(density) { 22.dp.toPx() }
    val showThresholdPx = with(density) { 14.dp.toPx() }
    var isBottomBarVisible by remember { mutableStateOf(true) }
    var accumulatedScrollPx by remember { mutableFloatStateOf(0f) }
    val keepBottomBarVisible = remember { mutableStateOf(false) }

    val navigateTo: (DashboardDestination) -> Unit = { destination ->
        if (
            destination == DashboardDestination.Home &&
            currentRoute == DashboardDestination.Home.route
        ) {
            homeScrollToTop += 1
        }
        if (currentRoute != destination.route) {
            currentRoute = destination.route
        }
    }

    val bottomBarScrollConnection = remember(hideThresholdPx, showThresholdPx, isNetworkAvailable) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!isNetworkAvailable) return Offset.Zero
                if (keepBottomBarVisible.value) return Offset.Zero
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                val deltaY = available.y
                if (deltaY == 0f) return Offset.Zero

                if (abs(available.y) < abs(available.x)) return Offset.Zero

                if (deltaY < 0f) {
                    accumulatedScrollPx = min(0f, accumulatedScrollPx + deltaY)
                    if (isBottomBarVisible && -accumulatedScrollPx >= hideThresholdPx) {
                        isBottomBarVisible = false
                        accumulatedScrollPx = 0f
                    }
                } else {
                    accumulatedScrollPx = max(0f, accumulatedScrollPx + deltaY)
                    if (!isBottomBarVisible && accumulatedScrollPx >= showThresholdPx) {
                        isBottomBarVisible = true
                        accumulatedScrollPx = 0f
                    }
                }

                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!isNetworkAvailable) return Velocity.Zero
                if (keepBottomBarVisible.value) return Velocity.Zero
                if (available.y < -500f) {
                    isBottomBarVisible = false
                } else if (available.y > 500f) {
                    isBottomBarVisible = true
                }
                accumulatedScrollPx = 0f
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(currentRoute, isOnline) {
        isBottomBarVisible = true
        accumulatedScrollPx = 0f
        keepBottomBarVisible.value = currentRoute == DashboardDestination.Favorites.route && isOnline
    }
    LaunchedEffect(keepBottomBarVisible.value) {
        if (keepBottomBarVisible.value) {
            isBottomBarVisible = true
            accumulatedScrollPx = 0f
        }
    }
    LaunchedEffect(isNetworkAvailable) {
        if (!isNetworkAvailable) {
            isBottomBarVisible = true
            accumulatedScrollPx = 0f
        }
    }

    val bottomBarTransition = updateTransition(
        targetState = isBottomBarVisible,
        label = "bottom_bar_visibility"
    )
    val bottomBarTranslationPx by bottomBarTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) 440 else 320,
                easing = FastOutSlowInEasing
            )
        },
        label = "bottom_bar_translation"
    ) { visible ->
        if (visible) 0f else bottomBarHideDistancePx
    }
    val bottomBarAlpha by bottomBarTransition.animateFloat(
        transitionSpec = {
            tween(
                durationMillis = if (targetState) 420 else 260,
                easing = LinearOutSlowInEasing
            )
        },
        label = "bottom_bar_alpha"
    ) { visible ->
        if (visible) 1f else 0f
    }

    LaunchedEffect(currentRoute, homeScrollToTop) {
        if (
            currentRoute != DashboardDestination.Home.route ||
            homeScrollToTop == 0
        ) {
            return@LaunchedEffect
        }

        if (
            homeScrollState.firstVisibleItemIndex != 0 ||
            homeScrollState.firstVisibleItemScrollOffset != 0
        ) {
            homeScrollState.animateScrollToItem(0)
        }
        homeScrollToTop = 0
    }

    LaunchedEffect(isNetworkAvailable, currentRoute) {
        if (!isNetworkAvailable && currentRoute !in offlineAllowedRoutes) {
            currentRoute = DashboardDestination.Home.route
        }
    }

    val scope = DashboardShellScope(
        currentRoute = currentRoute,
        homeScrollState = homeScrollState,
        navigateTo = navigateTo
    )
    val stateHolder = rememberSaveableStateHolder()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = currentRoute,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (isNetworkAvailable) {
                            Modifier.nestedScroll(bottomBarScrollConnection)
                        } else {
                            Modifier
                        }
                    )
                    .graphicsLayer(
                        translationY = when (currentRoute) {
                            DashboardDestination.Search.route -> -2f
                            else -> 0f
                        },
                        transformOrigin = TransformOrigin.Center
                    ),
                transitionSpec = {
                    fadeIn(tween(220, easing = FastOutSlowInEasing)) togetherWith
                        fadeOut(tween(180, easing = FastOutSlowInEasing))
                },
                label = "dashboard_tabs"
            ) { route ->
                stateHolder.SaveableStateProvider(route) {
                    scope.content(route)
                }
            }

            scope.overlay()

            DashboardNavigationBar(
                currentRoute = currentRoute,
                isNetworkAvailable = isNetworkAvailable,
                isOnline = isOnline,
                onNavigate = navigateTo,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .graphicsLayer {
                        translationY = bottomBarTranslationPx
                        alpha = bottomBarAlpha
                        clip = false
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
            )
        }
    }
}
