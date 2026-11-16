package com.jellycine.app.ui.components.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.jellycine.util.image.JellyfinPosterImage
import com.jellycine.ui.components.common.CompactTopChip

@Composable
fun CompactTopLogo(
    imageUrl: String,
    contentDescription: String?,
    progress: Float,
    isTablet: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = if (isTablet) 122.dp else 92.dp,
    height: Dp = if (isTablet) 48.dp else 36.dp,
    shape: Shape = RoundedCornerShape(10.dp),
    contentScale: ContentScale = ContentScale.Fit,
    horizontalImagePadding: Dp = 10.dp,
    verticalImagePadding: Dp = 7.dp,
    overlayTitle: String? = null
) {
    val context = LocalContext.current

    CompactTopChip(
        modifier = modifier
            .statusBarsPadding()
            .padding(start = 16.dp, top = 10.dp),
        progress = progress,
        width = width,
        height = height,
        shape = shape,
        onClick = onClick
    ) {
        JellyfinPosterImage(
            imageUrl = imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalImagePadding, vertical = verticalImagePadding),
            context = context,
            contentScale = contentScale,
            alignment = Alignment.Center
        )
        overlayTitle?.let { title ->
            Text(
                text = title,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = if (isTablet) 10.sp else 8.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 5.dp, vertical = 3.dp)
            )
        }
    }
}
