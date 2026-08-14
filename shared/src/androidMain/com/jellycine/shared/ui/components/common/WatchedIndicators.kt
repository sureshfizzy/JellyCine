package com.jellycine.shared.ui.components.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jellycine.shared.R

@Composable
fun WatchedIndicatorBadge(
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.size(20.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF4CAF50)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = stringResource(R.string.watched),
                tint = Color.White,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
fun WatchedActionButton(
    isWatched: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp
) {
    val visualSize = if (isWatched) size * 0.82f else size
    val iconSize = if (isWatched) size * 0.46f else size * 0.54f

    Box(
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(visualSize),
            shape = RoundedCornerShape(visualSize / 2f),
            color = if (isWatched) Color(0xFF4CAF50) else Color.Transparent,
            border = BorderStroke(
                1.dp,
                if (isWatched) Color.Transparent else Color.White.copy(alpha = 0.22f)
            )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(
                        if (isWatched) R.string.watched else R.string.unwatched
                    ),
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}