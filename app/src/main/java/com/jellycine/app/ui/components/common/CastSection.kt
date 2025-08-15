package com.jellycine.app.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.BaseItemPerson
import com.jellycine.data.repository.MediaRepository
import kotlinx.coroutines.flow.first

@Composable
fun CastSection(
    item: BaseItemDto,
    mediaRepository: MediaRepository,
    modifier: Modifier = Modifier,
    title: String = "Cast & Crew",
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
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                items(actors) { person ->
                    ModernCastMemberCard(
                        person = person,
                        mediaRepository = mediaRepository
                    )
                }
            }
        }
    }
}

@Composable
private fun ModernCastMemberCard(
    person: BaseItemPerson,
    mediaRepository: MediaRepository
) {
    var personImageUrl by remember(person.id) { mutableStateOf<String?>(null) }

    LaunchedEffect(person.id) {
        person.id?.let { id ->
            personImageUrl = getPersonImageUrl(id, mediaRepository)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(100.dp)
    ) {
        // Profile Image
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A2A2A)),
            contentAlignment = Alignment.Center
        ) {
            if (personImageUrl != null) {
                AsyncImage(
                    model = personImageUrl,
                    contentDescription = person.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = person.name,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Name
        Text(
            text = person.name ?: "Unknown",
            fontSize = 12.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            lineHeight = 14.sp
        )

        // Role
        person.role?.let { role ->
            Text(
                text = role,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

private suspend fun getPersonImageUrl(personId: String, mediaRepository: MediaRepository): String? {
    return mediaRepository.getImageUrl(
        itemId = personId,
        imageType = "Primary",
        width = 120,
        height = 120,
        quality = 90
    ).first()
}