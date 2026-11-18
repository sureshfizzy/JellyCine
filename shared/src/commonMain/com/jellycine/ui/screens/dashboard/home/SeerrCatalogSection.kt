package com.jellycine.ui.screens.dashboard.home

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import coil3.compose.*
import coil3.request.ImageRequest
import com.jellycine.data.model.SeerrCatalogItem
import com.jellycine.resources.Res
import com.jellycine.util.image.JellyfinPosterImage

/** Keeps the catalog entries whose studio or network exists in the user's library. */
fun availableInLibrary(items: List<SeerrCatalogItem>, localNames: Set<String>?): List<SeerrCatalogItem> {
    val names = localNames ?: return emptyList()
    return items.filter { item -> item.localMatchNames.any { it.trim().lowercase() in names } }
}

/** A row of studio or network logo tiles. */
@Composable
fun SeerrCatalogSection(
    title: String,
    items: List<SeerrCatalogItem>,
    modifier: Modifier = Modifier,
    onItemClick: ((SeerrCatalogItem) -> Unit)? = null
) {
    Column(modifier = modifier.fillMaxWidth().background(Color.Black)) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp)
        ) {
            itemsIndexed(items, key = { index, item -> "${item.id}_$index" }) { _, item ->
                Surface(
                    modifier = Modifier
                        .size(width = 124.dp, height = 62.dp)
                        .then(if (onItemClick != null) Modifier.clickable { onItemClick(item) } else Modifier)
                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.10f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize().padding(14.dp)
                    ) {
                        val logo = item.logoUrl
                        if (logo != null) {
                            SeerrLogo(logo = logo, name = item.name)
                        } else {
                            Text(
                                text = item.name,
                                color = Color.White.copy(alpha = 0.78f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
private const val BUNDLED_LOGO_PREFIX = "file:///android_asset/logos/"

/** Loads a remote logo, or a bundled SVG from the shared resources. */
@Composable
private fun SeerrLogo(logo: String, name: String) {
    if (!logo.startsWith(BUNDLED_LOGO_PREFIX)) {
        JellyfinPosterImage(
            imageUrl = logo,
            contentDescription = name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
        return
    }
    val bytes by produceState<ByteArray?>(null, logo) {
        value = runCatching { Res.readBytes("files/logos/" + logo.removePrefix(BUNDLED_LOGO_PREFIX)) }.getOrNull()
    }
    bytes?.let {
        AsyncImage(
            model = ImageRequest.Builder(LocalPlatformContext.current).data(it).build(),
            contentDescription = name,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}
