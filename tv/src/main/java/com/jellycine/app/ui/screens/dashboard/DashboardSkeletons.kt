package com.jellycine.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jellycine.shared.ui.components.common.ShimmerEffect

@Composable
fun PosterSkeleton(
    modifier: Modifier = Modifier,
    width: Dp = 140.dp,
    height: Dp = 260.dp,
    cornerRadius: Float = 16f
) {
    Column(
        modifier = modifier
            .width(width)
            .height(height)
    ) {
        ShimmerEffect(
            modifier = Modifier
                .width(width)
                .aspectRatio(0.67f),
            cornerRadius = cornerRadius
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(top = 8.dp, start = 4.dp, end = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            ShimmerEffect(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .height(16.dp),
                cornerRadius = 4f
            )
            ShimmerEffect(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(12.dp),
                cornerRadius = 4f
            )
        }
    }
}

@Composable
private fun LibrarySkeleton(
    modifier: Modifier = Modifier,
    itemCount: Int = 6
) {
    LazyRow(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        items(itemCount) {
            PosterSkeleton()
        }
    }
}

@Composable
fun SectionTitleSkeleton(
    modifier: Modifier = Modifier,
    width: Dp = 150.dp
) {
    ShimmerEffect(
        modifier = modifier
            .width(width)
            .height(24.dp),
        cornerRadius = 4f
    )
}

@Composable
fun GenreSectionSkeleton(
    modifier: Modifier = Modifier,
    sectionCount: Int = 3
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black)
    ) {
        repeat(sectionCount) {
            Column {
                SectionTitleSkeleton(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                LibrarySkeleton()

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}