package com.vela.shared.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val VelaFontFamily = FontFamily.SansSerif

// 完整 type scale 让标题、正文和标签在所有页面共享同一视觉层级。
val VelaTypography = Typography(
    displaySmall = velaTextStyle(FontWeight.Bold, 36, 44, -0.2f),
    headlineLarge = velaTextStyle(FontWeight.Bold, 32, 40, -0.2f),
    headlineMedium = velaTextStyle(FontWeight.SemiBold, 28, 36, 0f),
    headlineSmall = velaTextStyle(FontWeight.SemiBold, 24, 32, 0f),
    titleLarge = velaTextStyle(FontWeight.SemiBold, 22, 28, 0f),
    titleMedium = velaTextStyle(FontWeight.SemiBold, 16, 24, 0.1f),
    titleSmall = velaTextStyle(FontWeight.Medium, 14, 20, 0.1f),
    bodyLarge = velaTextStyle(FontWeight.Normal, 16, 24, 0.25f),
    bodyMedium = velaTextStyle(FontWeight.Normal, 14, 20, 0.25f),
    bodySmall = velaTextStyle(FontWeight.Normal, 12, 16, 0.4f),
    labelLarge = velaTextStyle(FontWeight.SemiBold, 14, 20, 0.1f),
    labelMedium = velaTextStyle(FontWeight.Medium, 12, 16, 0.5f),
    labelSmall = velaTextStyle(FontWeight.Medium, 11, 16, 0.5f)
)

private fun velaTextStyle(
    weight: FontWeight,
    fontSize: Int,
    lineHeight: Int,
    letterSpacing: Float
) = TextStyle(
    fontFamily = VelaFontFamily,
    fontWeight = weight,
    fontSize = fontSize.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp
)
