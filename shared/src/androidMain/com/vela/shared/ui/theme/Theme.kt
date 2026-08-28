package com.vela.shared.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = VelaDarkPrimary,
    onPrimary = VelaDarkOnPrimary,
    primaryContainer = VelaDarkPrimaryContainer,
    onPrimaryContainer = VelaDarkOnPrimaryContainer,
    secondary = VelaDarkSecondary,
    onSecondary = VelaDarkOnSecondary,
    secondaryContainer = VelaDarkSecondaryContainer,
    onSecondaryContainer = VelaDarkOnSecondaryContainer,
    tertiary = VelaDarkTertiary,
    onTertiary = VelaDarkOnTertiary,
    tertiaryContainer = VelaDarkTertiaryContainer,
    onTertiaryContainer = VelaDarkOnTertiaryContainer,
    error = VelaDarkError,
    onError = VelaDarkOnError,
    errorContainer = VelaDarkErrorContainer,
    onErrorContainer = VelaDarkOnErrorContainer,
    background = VelaDarkBackground,
    onBackground = VelaDarkOnBackground,
    surface = VelaDarkSurface,
    onSurface = VelaDarkOnSurface,
    surfaceVariant = VelaDarkSurfaceVariant,
    onSurfaceVariant = VelaDarkOnSurfaceVariant,
    outline = VelaDarkOutline,
    outlineVariant = VelaDarkOutlineVariant,
    inverseSurface = VelaDarkOnSurface,
    inverseOnSurface = VelaDarkSurface,
    inversePrimary = VelaLightPrimary,
    surfaceTint = VelaDarkPrimary,
    scrim = Color.Black,
    surfaceDim = VelaDarkSurfaceDim,
    surfaceBright = VelaDarkSurfaceBright,
    surfaceContainerLowest = VelaDarkSurfaceContainerLowest,
    surfaceContainerLow = VelaDarkSurfaceContainerLow,
    surfaceContainer = VelaDarkSurfaceContainer,
    surfaceContainerHigh = VelaDarkSurfaceContainerHigh,
    surfaceContainerHighest = VelaDarkSurfaceContainerHighest
)

private val LightColorScheme = lightColorScheme(
    primary = VelaLightPrimary,
    onPrimary = VelaLightOnPrimary,
    primaryContainer = VelaLightPrimaryContainer,
    onPrimaryContainer = VelaLightOnPrimaryContainer,
    secondary = VelaLightSecondary,
    onSecondary = VelaLightOnSecondary,
    secondaryContainer = VelaLightSecondaryContainer,
    onSecondaryContainer = VelaLightOnSecondaryContainer,
    tertiary = VelaLightTertiary,
    onTertiary = VelaLightOnTertiary,
    tertiaryContainer = VelaLightTertiaryContainer,
    onTertiaryContainer = VelaLightOnTertiaryContainer,
    error = VelaLightError,
    onError = VelaLightOnError,
    errorContainer = VelaLightErrorContainer,
    onErrorContainer = VelaLightOnErrorContainer,
    background = VelaLightBackground,
    onBackground = VelaLightOnBackground,
    surface = VelaLightSurface,
    onSurface = VelaLightOnSurface,
    surfaceVariant = VelaLightSurfaceVariant,
    onSurfaceVariant = VelaLightOnSurfaceVariant,
    outline = VelaLightOutline,
    outlineVariant = VelaLightOutlineVariant,
    inverseSurface = VelaLightOnSurface,
    inverseOnSurface = VelaLightSurface,
    inversePrimary = VelaDarkPrimary,
    surfaceTint = VelaLightPrimary,
    scrim = Color.Black,
    surfaceDim = VelaLightSurfaceDim,
    surfaceBright = VelaLightSurfaceBright,
    surfaceContainerLowest = VelaLightSurfaceContainerLowest,
    surfaceContainerLow = VelaLightSurfaceContainerLow,
    surfaceContainer = VelaLightSurfaceContainer,
    surfaceContainerHigh = VelaLightSurfaceContainerHigh,
    surfaceContainerHighest = VelaLightSurfaceContainerHighest
)

@Composable
fun VelaTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        // 动态色由调用方显式开启；默认保持 Vela 品牌在不同设备上的一致性。
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    // Motion 与其余 M3 token 在同一主题边界提供，组件和页面只消费语义规格。
    CompositionLocalProvider(LocalVelaMotion provides VelaMotion) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = VelaTypography,
            shapes = VelaShapes,
            content = content
        )
    }
}
