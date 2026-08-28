package com.vela.shared.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Vela 的 motion token。Material3 1.4.0 尚未公开 MotionScheme，因此在这里集中表达
 * effects（透明度/颜色）与 spatial（位置/尺寸）两类语义，避免页面继续散落时长和弹簧参数。
 */
@Immutable
class VelaMotionScheme internal constructor() {
    fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = tween(
        durationMillis = 150,
        easing = LinearOutSlowInEasing
    )

    fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = tween(
        durationMillis = 220,
        easing = FastOutSlowInEasing
    )

    fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = tween(
        durationMillis = 300,
        easing = FastOutSlowInEasing
    )

    fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )

    fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )
}

internal val VelaMotion = VelaMotionScheme()
internal val LocalVelaMotion = staticCompositionLocalOf { VelaMotion }

val MaterialTheme.velaMotion: VelaMotionScheme
    @Composable
    @ReadOnlyComposable
    get() = LocalVelaMotion.current
