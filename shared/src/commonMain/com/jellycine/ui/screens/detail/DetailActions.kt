package com.jellycine.ui.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jellycine.ui.components.common.detailActionWidth
import com.jellycine.ui.components.common.DetailPlayActionButton
import com.jellycine.ui.components.common.DetailTrailerButton
import com.jellycine.ui.components.common.FavoriteActionButton

@Composable
internal fun ActionSection(
    screenWidthDp: Dp,
    useTabletLayout: Boolean,
    buttonHeight: Dp,
    playButtonText: String,
    isPartiallyWatched: Boolean,
    resumeProgress: Float,
    isFavorite: Boolean,
    hasTrailer: Boolean,
    extras: DetailExtras,
    onPlayClick: () -> Unit,
    onTrailerClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconOnlyDownload = !useTabletLayout

    Row(
        modifier = modifier
            .fillMaxWidth(
                detailActionWidth(
                    screenWidthDp,
                    useTabletLayout = useTabletLayout
                )
            )
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

        if (extras.supportsDownload) {
            Box(
                modifier = Modifier
                    .then(
                        if (iconOnlyDownload) {
                            Modifier.size(buttonHeight)
                        } else {
                            Modifier
                                .weight(1f)
                                .height(buttonHeight)
                        }
                    )
            ) {
                extras.ItemDownloadAction(
                    buttonHeight = buttonHeight,
                    iconOnly = iconOnlyDownload
                )
            }
        }

        if (hasTrailer) {
            DetailTrailerButton(
                onClick = onTrailerClick,
                size = buttonHeight
            )
        }

        if (extras.canSearchSubtitles) {
            extras.SubtitleSearchAction(size = buttonHeight)
        }

        FavoriteActionButton(
            isFavorite = isFavorite,
            onClick = onFavoriteClick,
            size = buttonHeight
        )
    }
}

@Composable
internal fun SeriesActionSection(
    screenWidthDp: Dp,
    useTabletLayout: Boolean,
    buttonHeight: Dp,
    isFavorite: Boolean,
    hasTrailer: Boolean,
    extras: DetailExtras,
    onTrailerClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth(
                detailActionWidth(
                    screenWidthDp,
                    useTabletLayout = useTabletLayout
                )
            )
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (extras.supportsDownload) {
            extras.SeriesDownloadAction(
                modifier = Modifier.weight(1f),
                buttonHeight = buttonHeight
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        if (hasTrailer) {
            DetailTrailerButton(
                onClick = onTrailerClick,
                size = buttonHeight
            )
        }

        FavoriteActionButton(
            isFavorite = isFavorite,
            onClick = onFavoriteClick,
            size = buttonHeight
        )
    }
}
