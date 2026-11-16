package com.jellycine.shared.ui.components.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest

@Composable
fun AuthenticatedImage(
    url: String,
    headers: Map<String, String>,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onError: () -> Unit = {}
) {
    val context = LocalPlatformContext.current
    val request = remember(url, headers, context) {
        ImageRequest.Builder(context)
            .data(url)
            .httpHeaders(
                NetworkHeaders.Builder().apply {
                    headers.forEach { (name, value) -> set(name, value) }
                }.build()
            )
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        onError = { onError() }
    )
}
