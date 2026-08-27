package com.vela.app.ui.screens.detail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun DetailHeroActions(
    playButtonText: String,
    isFavorite: Boolean,
    playFocusRequester: FocusRequester,
    favoriteFocusRequester: FocusRequester,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDownPressed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeroPillButton(
            text = playButtonText,
            icon = Icons.Rounded.PlayArrow,
            isPrimary = true,
            focusRequester = playFocusRequester,
            focusProperties = {
                right = favoriteFocusRequester
            },
            onDownPressed = onDownPressed,
            onClick = onPlayClick
        )

        HeroPillButton(
            text = if (isFavorite) "Favorited" else "Favorite",
            icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            isPrimary = false,
            focusRequester = favoriteFocusRequester,
            focusProperties = {
                left = playFocusRequester
            },
            onDownPressed = onDownPressed,
            onClick = onFavoriteClick
        )
    }
}

@Composable
internal fun SeriesHeroActions(
    isFavorite: Boolean,
    isWatched: Boolean,
    watchedFocusRequester: FocusRequester,
    favoriteFocusRequester: FocusRequester,
    onWatchedClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onDownPressed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeroPillButton(
            text = if (isWatched) "Watched" else "Mark Watched",
            icon = Icons.Rounded.Check,
            isPrimary = true,
            focusRequester = watchedFocusRequester,
            focusProperties = {
                right = favoriteFocusRequester
            },
            onDownPressed = onDownPressed,
            onClick = onWatchedClick
        )

        HeroPillButton(
            text = if (isFavorite) "Favorited" else "Favorite",
            icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
            isPrimary = false,
            focusRequester = favoriteFocusRequester,
            focusProperties = {
                left = watchedFocusRequester
            },
            onDownPressed = onDownPressed,
            onClick = onFavoriteClick
        )
    }
}

@Composable
private fun HeroPillButton(
    text: String,
    icon: ImageVector,
    isPrimary: Boolean,
    focusRequester: FocusRequester,
    focusProperties: androidx.compose.ui.focus.FocusProperties.() -> Unit,
    onClick: () -> Unit,
    onDownPressed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.08f else 1f,
        animationSpec = tween(150)
    )

    val backgroundColor = when {
        isPrimary && isFocused -> Color.White
        isPrimary -> Color.White.copy(alpha = 0.85f)
        isFocused -> Color.White.copy(alpha = 0.20f)
        else -> Color.White.copy(alpha = 0.10f)
    }

    val borderColor = when {
        isFocused -> Color.White
        else -> Color.Transparent
    }

    val borderWidth = if (isFocused) 2.5.dp else 0.dp

    val contentColor = when {
        isPrimary && isFocused -> Color.Black
        isPrimary -> Color.Black.copy(alpha = 0.9f)
        isFocused -> Color.White
        else -> Color.White.copy(alpha = 0.7f)
    }
    val fontWeight = if (isPrimary) FontWeight.Bold else FontWeight.SemiBold

    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .widthIn(min = 110.dp)
            .clip(shape)
            .background(backgroundColor, shape)
            .then(
                if (isFocused) Modifier.border(borderWidth, borderColor, shape)
                else Modifier
            )
            .focusRequester(focusRequester)
            .focusProperties(focusProperties)
            .onFocusChanged { isFocused = it.isFocused }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionDown -> {
                            onDownPressed()
                            true
                        }
                        Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                            onClick()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .clickable(onClick = onClick)
            .focusable()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = fontWeight,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp)
        )
    }
}