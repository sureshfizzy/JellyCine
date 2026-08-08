package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jellycine.shared.ui.components.common.DetailPlayActionButton
import com.jellycine.shared.ui.components.common.FavoriteActionButton
import com.jellycine.shared.ui.components.common.WatchedActionButton

@Composable
internal fun ActionSection(
    buttonHeight: Dp,
    playButtonText: String,
    isPartiallyWatched: Boolean,
    resumeProgress: Float,
    isFavorite: Boolean,
    onPlayClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(buttonHeight)
                .clip(RoundedCornerShape(24.dp))
        ) {
            DetailPlayActionButton(
                text = playButtonText,
                isPartiallyWatched = isPartiallyWatched,
                resumeProgress = resumeProgress,
                onClick = onPlayClick,
                modifier = Modifier.fillMaxSize()
            )
        }

        FavoriteActionButton(
            isFavorite = isFavorite,
            onClick = onFavoriteClick
        )
    }
}

@Composable
internal fun SeriesActionSection(
    isFavorite: Boolean,
    isWatched: Boolean,
    onWatchedClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WatchedActionButton(
            isWatched = isWatched,
            onClick = onWatchedClick
        )

        FavoriteActionButton(
            isFavorite = isFavorite,
            onClick = onFavoriteClick
        )
    }
}
