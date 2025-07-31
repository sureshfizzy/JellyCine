package com.jellycine.app.ui.components.common

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jellycine.app.ui.screens.detail.CastMemberCard
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.repository.MediaRepository

@Composable
fun CastSection(
    item: BaseItemDto,
    mediaRepository: MediaRepository,
    modifier: Modifier = Modifier,
    title: String = "Cast",
    maxItems: Int = 8
) {
    val actors = item.people?.filter { it.type == "Actor" }?.take(maxItems) ?: emptyList()
    
    if (actors.isNotEmpty()) {
        Column(modifier = modifier) {
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 24.dp, bottom = 16.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                items(actors) { person ->
                    CastMemberCard(
                        person = person,
                        mediaRepository = mediaRepository
                    )
                }
            }
        }
    }
}