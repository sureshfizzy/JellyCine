package com.jellycine.shared.ui.components.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PosterCountBadge(
    count: Int,
    modifier: Modifier = Modifier
) {
    if (count <= 0) return

    val badgeSize = if (count >= 100) 24.dp else 20.dp
    val displayCount = if (count >= 100) "99+" else count.toString()
    val textSize = if (count >= 100) 7.sp else 8.sp

    Surface(
        modifier = modifier.size(badgeSize),
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.7f)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayCount,
                fontSize = textSize,
                lineHeight = textSize,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(
                        includeFontPadding = false
                    )
                )
            )
        }
    }
}
