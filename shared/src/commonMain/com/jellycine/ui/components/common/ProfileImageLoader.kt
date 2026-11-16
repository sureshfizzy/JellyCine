package com.jellycine.ui.components.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jellycine.resources.Res
import com.jellycine.resources.ic_emby_placeholder
import com.jellycine.resources.ic_jellyfin_placeholder
import com.jellycine.resources.settings_profile_picture
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun UserProfileAvatar(
    imageUrl: String?,
    serverTypeRaw: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imageHeaders: Map<String, String> = emptyMap()
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        ProfileImageLoader(
            imageUrl = imageUrl,
            serverTypeRaw = serverTypeRaw,
            modifier = Modifier.fillMaxSize(),
            imageHeaders = imageHeaders
        )
    }
}

@Composable
fun ProfileImageLoader(
    imageUrl: String?,
    serverTypeRaw: String? = null,
    modifier: Modifier = Modifier,
    imageHeaders: Map<String, String> = emptyMap()
) {
    val context = LocalPlatformContext.current
    var hasError by remember { mutableStateOf(false) }
    val profileRequest = remember(imageUrl, imageHeaders) {
        imageUrl?.takeIf { it.isNotBlank() }?.let { url ->
            ImageRequest.Builder(context)
                .data(url)
                .httpHeaders(
                    NetworkHeaders.Builder().apply {
                        imageHeaders.forEach { (name, value) -> set(name, value) }
                    }.build()
                )
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .crossfade(300)
                .build()
        }
    }

    LaunchedEffect(profileRequest) {
        hasError = false
    }
    val avatarImage = profileRequest == null || hasError
    val placeholderRes = remember(serverTypeRaw) {
        when {
            serverTypeRaw.equals("EMBY", ignoreCase = true) -> Res.drawable.ic_emby_placeholder
            else -> Res.drawable.ic_jellyfin_placeholder
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        if (avatarImage) {
            Image(
                painter = painterResource(placeholderRes),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(0.62f)
            )
        } else {
            AsyncImage(
                model = profileRequest,
                contentDescription = stringResource(Res.string.settings_profile_picture),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                onError = {
                    hasError = true
                }
            )
        }
    }
}
