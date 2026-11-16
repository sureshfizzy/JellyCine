package com.jellycine.shared.util.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jellycine.shared.util.platformBitmapConfig

@Composable
fun WarmImageUrl(
    imageUrl: String?,
    allowRgb565: Boolean = true
) {
    val context = LocalPlatformContext.current

    LaunchedEffect(context, imageUrl, allowRgb565) {
        if (imageUrl.isNullOrBlank()) return@LaunchedEffect

        SingletonImageLoader.get(context).enqueue(
            ImageRequest.Builder(context)
                .data(imageUrl)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(CachePolicy.ENABLED)
                .platformBitmapConfig(allowRgb565)
                .crossfade(false)
                .build()
        )
    }
}
