package com.jellycine.ui.screens.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.resources.*
import kotlin.math.min
import kotlin.math.round
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

val DashboardNavigationBarHeight = 68.dp

@Composable
fun DashboardNavigationBar(
    currentRoute: String?,
    isNetworkAvailable: Boolean,
    isOnline: Boolean,
    onNavigate: (DashboardDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val containerSize = LocalWindowInfo.current.containerSize
    val screenWidth = with(density) { containerSize.width.toDp() }
    val screenHeight = with(density) { containerSize.height.toDp() }
    val isLandscape = screenWidth > screenHeight
    val isTablet = screenWidth >= 600.dp
    val shouldUseMobileBarWidth = isLandscape || isTablet
    val mobileLikeBarWidth = (min(screenWidth.value, screenHeight.value).dp - 32.dp)
        .coerceIn(320.dp, 390.dp)
    val barOuterHorizontalPadding = if (shouldUseMobileBarWidth) 0.dp else 16.dp
    val barInnerHorizontalPadding = if (shouldUseMobileBarWidth) 10.dp else 16.dp
    val navGroupSpacing = if (shouldUseMobileBarWidth) 12.dp else 20.dp
    val innerItemOffset = if (shouldUseMobileBarWidth) 16.dp else 22.dp
    val outerItemOffset = if (shouldUseMobileBarWidth) 0.dp else 10.dp
    val bottomBarHeight = DashboardNavigationBarHeight

    val sideDestinations = if (isNetworkAvailable) {
        listOf(
            DashboardDestination.Home,
            DashboardDestination.MyMedia,
            DashboardDestination.Favorites,
            DashboardDestination.Settings
        )
    } else {
        listOf(
            DashboardDestination.Home,
            DashboardDestination.Settings
        )
    }
    val isOfflineTwoTabMode = !isNetworkAvailable && sideDestinations.size == 2
    val offlineItemSlotWidth = 72.dp
    val offlineItemSpacing = 18.dp
    val offlineRowHorizontalPadding = 12.dp
    val offlineBarWidth = (offlineRowHorizontalPadding * 2) +
        (offlineItemSlotWidth * sideDestinations.size) +
        (offlineItemSpacing * (sideDestinations.size - 1).coerceAtLeast(0))

    Box(
        modifier = modifier
            .then(
                if (isOfflineTwoTabMode) {
                    Modifier.width(offlineBarWidth)
                } else if (shouldUseMobileBarWidth) {
                    Modifier.width(mobileLikeBarWidth)
                } else {
                    Modifier.fillMaxWidth()
                }
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(
                horizontal = if (isOfflineTwoTabMode) 0.dp else barOuterHorizontalPadding,
                vertical = 10.dp
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bottomBarHeight)
                .shadow(
                    elevation = 18.dp,
                    shape = RoundedCornerShape(30.dp),
                    clip = false
                )
                .graphicsLayer(
                    rotationX = if (isOfflineTwoTabMode) 0f else -3f,
                    transformOrigin = TransformOrigin(0.5f, 1f),
                    scaleY = 1f,
                    cameraDistance = 8f * density.density
                )
                .drawBehind {
                    if (isOfflineTwoTabMode) {
                        drawSimpleNavigationBar(
                            width = size.width,
                            height = size.height
                        )
                    } else {
                        draw3DCurvedNavigationBar(
                            width = size.width,
                            height = size.height
                        )
                    }
                }
        )

        if (isNetworkAvailable) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomBarHeight)
                    .padding(horizontal = barInnerHorizontalPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(navGroupSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sideDestinations.take(2).forEach { destination ->
                        NavigationItem(
                            modifier = when (destination.route) {
                                DashboardDestination.MyMedia.route -> Modifier.offset(x = -innerItemOffset)
                                else -> Modifier.offset(x = -outerItemOffset)
                            },
                            destination = destination,
                            isOnline = isOnline,
                            isSelected = currentRoute == destination.route,
                            onClick = { onNavigate(destination) }
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(navGroupSpacing),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    sideDestinations.drop(2).forEach { destination ->
                        NavigationItem(
                            modifier = when (destination.route) {
                                DashboardDestination.Favorites.route -> Modifier.offset(x = innerItemOffset)
                                else -> Modifier.offset(x = outerItemOffset)
                            },
                            destination = destination,
                            isOnline = isOnline,
                            isSelected = currentRoute == destination.route,
                            onClick = { onNavigate(destination) }
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .offset(y = (-22).dp),
                contentAlignment = Alignment.Center
            ) {
                FloatingSearchButton(
                    isSelected = currentRoute == DashboardDestination.Search.route,
                    onClick = { onNavigate(DashboardDestination.Search) }
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bottomBarHeight)
                    .padding(horizontal = offlineRowHorizontalPadding),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                sideDestinations.forEachIndexed { index, destination ->
                    NavigationItem(
                        modifier = Modifier.width(offlineItemSlotWidth),
                        destination = destination,
                        isOnline = true,
                        isSelected = currentRoute == destination.route,
                        itemWidth = 56.dp,
                        onClick = { onNavigate(destination) }
                    )
                    if (index < sideDestinations.lastIndex) {
                        Spacer(modifier = Modifier.width(offlineItemSpacing))
                    }
                }
            }
        }
    }
}

private fun DrawScope.draw3DCurvedNavigationBar(
    width: Float,
    height: Float
) {
    val centerWidth = width / 2f
    val topCornerRadius = 24.dp.toPx()
    val bottomCornerRadius = 26.dp.toPx()

    // Use dimensions that are proportional to the FAB size for a good fit
    val fabRadius = 28.dp.toPx()
    val curveDepth = fabRadius + (fabRadius / 3f)
    val targetCurveWidth = (fabRadius * 2) + (fabRadius * 1.5f)
    val sideInset = topCornerRadius + 6.dp.toPx()
    val targetHalfWidth = targetCurveWidth / 2f
    fun snapX(value: Float): Float = round(value * 2f) / 2f
    val notchCenterX = snapX(centerWidth)
    val availableHalfWidth = min(
        notchCenterX - sideInset,
        (width - sideInset) - notchCenterX
    ).coerceAtLeast(0f)
    val curveHalfWidth = min(targetHalfWidth, availableHalfWidth)
    var curveStartX = snapX(notchCenterX - curveHalfWidth)
    var curveEndX = snapX(notchCenterX + (notchCenterX - curveStartX))
    val maxRightX = width - sideInset
    if (curveEndX > maxRightX) {
        curveEndX = maxRightX
        curveStartX = snapX(notchCenterX - (curveEndX - notchCenterX))
    }
    val adjustedCurveWidth = curveEndX - curveStartX

    // Build left control points first, then reflect for perfect symmetry.
    val controlPoint1X = snapX(curveStartX + adjustedCurveWidth * 0.12f)
    val controlPoint2X = snapX(notchCenterX - adjustedCurveWidth * 0.35f)
    val controlPoint3X = snapX(notchCenterX + (notchCenterX - controlPoint2X))
    val controlPoint4X = snapX(notchCenterX + (notchCenterX - controlPoint1X))

    val backgroundPath = Path().apply {
        moveTo(0f, topCornerRadius)
        quadraticTo(0f, 0f, topCornerRadius, 0f)
        lineTo(curveStartX, 0f)

        cubicTo(
            x1 = controlPoint1X, y1 = 0f,
            x2 = controlPoint2X, y2 = curveDepth,
            x3 = notchCenterX, y3 = curveDepth
        )

        cubicTo(
            x1 = controlPoint3X, y1 = curveDepth,
            x2 = controlPoint4X, y2 = 0f,
            x3 = curveEndX, y3 = 0f
        )

        lineTo(width - topCornerRadius, 0f)
        quadraticTo(width, 0f, width, topCornerRadius)
        lineTo(width, height - bottomCornerRadius)
        quadraticTo(width, height, width - bottomCornerRadius, height)
        lineTo(bottomCornerRadius, height)
        quadraticTo(0f, height, 0f, height - bottomCornerRadius)
        close()
    }

    // AMOLED Black background with subtle depth
    drawPath(
        path = backgroundPath,
        color = Color.Black
    )

    drawPath(
        path = backgroundPath,
        color = Color.White.copy(alpha = 0.05f),
        style = Stroke(width = 1.dp.toPx())
    )

    val notchGlowPath = Path().apply {
        moveTo(curveStartX, 0f)
        cubicTo(
            x1 = controlPoint1X, y1 = 0f,
            x2 = controlPoint2X, y2 = curveDepth,
            x3 = notchCenterX, y3 = curveDepth
        )
        cubicTo(
            x1 = controlPoint3X, y1 = curveDepth,
            x2 = controlPoint4X, y2 = 0f,
            x3 = curveEndX, y3 = 0f
        )
    }

    val glowCenterColor = Color(0xFFBEE8FF)
    val glowBrush = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            glowCenterColor.copy(alpha = 0.14f),
            Color.Transparent
        ),
        startX = curveStartX,
        endX = curveEndX
    )
    val glowCoreBrush = Brush.horizontalGradient(
        colors = listOf(
            Color.Transparent,
            glowCenterColor.copy(alpha = 0.28f),
            Color.Transparent
        ),
        startX = curveStartX,
        endX = curveEndX
    )

    drawPath(
        path = notchGlowPath,
        brush = glowBrush,
        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
    )
    drawPath(
        path = notchGlowPath,
        brush = glowCoreBrush,
        style = Stroke(width = 1.3.dp.toPx(), cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawSimpleNavigationBar(
    width: Float,
    height: Float
) {
    val corner = 26.dp.toPx()
    drawRoundRect(
        color = Color.Black,
        size = Size(width, height),
        cornerRadius = CornerRadius(corner, corner)
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.05f),
        size = Size(width, height),
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = 1.dp.toPx())
    )
}

@Composable
private fun FloatingSearchButton(
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        )
    )

    val elevation by animateDpAsState(
        targetValue = if (isSelected) 16.dp else 8.dp,
        animationSpec = tween(300)
    )

    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.95f),
        animationSpec = tween(300)
    )

    Box(
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                transformOrigin = TransformOrigin.Center
            )
            .shadow(
                elevation = elevation,
                shape = CircleShape,
                clip = false
            )
            .background(
                color = backgroundColor,
                shape = CircleShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Filled.Search else Icons.Outlined.Search,
            contentDescription = stringResource(Res.string.search),
            tint = Color.Black,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun NavigationItem(
    modifier: Modifier = Modifier,
    destination: DashboardDestination,
    isOnline: Boolean,
    isSelected: Boolean,
    itemWidth: Dp = 44.dp,
    onClick: () -> Unit
) {
    val showsMyMedia = destination == DashboardDestination.Favorites && !isOnline
    val title = stringResource(if (showsMyMedia) Res.string.my_media else destination.title)
    val selectedIcon: ImageVector = if (showsMyMedia) Icons.Filled.PlayArrow else destination.selectedIcon
    val unselectedIcon: ImageVector = if (showsMyMedia) Icons.Outlined.PlayArrow else destination.unselectedIcon
    val haptic = LocalHapticFeedback.current
    val iconTint by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.76f),
        animationSpec = tween(durationMillis = 220),
        label = "nav_icon_tint"
    )
    val labelAlpha by animateFloatAsState(
        targetValue = if (isSelected) 1f else 0.55f,
        animationSpec = tween(durationMillis = 220),
        label = "nav_label_alpha"
    )
    val textTint by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.76f),
        animationSpec = tween(durationMillis = 220),
        label = "nav_text_tint"
    )

    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            )
            .height(64.dp)
            .padding(horizontal = 8.dp)
            .width(itemWidth),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isSelected) Color.White.copy(alpha = 0.12f) else Color.Transparent
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSelected) selectedIcon else unselectedIcon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Text(
            text = title,
            color = textTint,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.alpha(labelAlpha)
        )
    }
}

sealed class DashboardDestination(
    val route: String,
    val title: StringResource,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Home : DashboardDestination(
        "dashboard_home",
        Res.string.home,
        Icons.Filled.Home,
        Icons.Outlined.Home
    )

    object MyMedia : DashboardDestination(
        "my_media",
        Res.string.dashboard_discover,
        Icons.Filled.Explore,
        Icons.Outlined.Explore
    )

    object Search : DashboardDestination(
        "search",
        Res.string.search,
        Icons.Filled.Search,
        Icons.Outlined.Search
    )

    object Favorites : DashboardDestination(
        "favorites",
        Res.string.dashboard_discover_clips,
        Icons.Filled.Subscriptions,
        Icons.Outlined.Subscriptions
    )

    object Settings : DashboardDestination(
        "settings",
        Res.string.settings,
        Icons.Filled.Settings,
        Icons.Outlined.Settings
    )
}
