package com.jellycine.app.ui.components.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jellycine.app.R

@Composable
fun CastActionButton(
    modifier: Modifier = Modifier,
    isConnected: Boolean,
    onClick: () -> Unit,
    size: Dp = 42.dp
) {
    val iconSize = if (size <= 34.dp) 20.dp else 24.dp

    Box(
        modifier = modifier
            .size(size)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Cast,
            contentDescription = stringResource(id = R.string.cast_to_device),
            tint = if (isConnected) Color(0xFF62FFB6) else Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(iconSize)
        )
    }
}
