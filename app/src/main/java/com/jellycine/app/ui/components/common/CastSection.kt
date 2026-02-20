package com.jellycine.app.ui.components.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
    val castAndCrew = remember(item.people, maxItems) {
        prioritizeCastAndCrew(
            people = item.people,
            maxItems = maxItems
        )
    }

    if (castAndCrew.isNotEmpty()) {
        Column(modifier = modifier) {
            Text(
                text = title,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
            )

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) {
                items(
                    items = castAndCrew,
                    key = { person ->
                        person.id ?: "${person.name}-${person.role}-${person.type}"
                    }
                ) { person ->
                    CastCrewMemberCard(
                        person = person,
                        mediaRepository = mediaRepository
                    )
                }
            }
        }
    }
}

@Composable
private fun CastCrewMemberCard(
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
        modifier = Modifier.width(116.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(166.dp)
                .clip(RoundedCornerShape(10.dp))
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

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = person.name ?: "Unknown",
            fontSize = 12.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            lineHeight = 14.sp
        )

        val roleLabel = person.role?.takeIf { it.isNotBlank() }
            ?: person.type?.takeIf { !it.equals("Actor", ignoreCase = true) && it.isNotBlank() }

        roleLabel?.let { role ->
            Text(
                text = role,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.62f),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

private fun prioritizeCastAndCrew(
    people: List<BaseItemPerson>?,
    maxItems: Int
): List<BaseItemPerson> {
    if (people.isNullOrEmpty() || maxItems <= 0) return emptyList()

    val clean = people.filter { !it.name.isNullOrBlank() }
    if (clean.isEmpty()) return emptyList()

    val actors = clean.filter { it.type.equals("Actor", ignoreCase = true) }
    val crew = clean.filterNot { it.type.equals("Actor", ignoreCase = true) }

    return (actors + crew)
        .distinctBy { it.id ?: "${it.name}-${it.role}-${it.type}" }
        .take(maxItems)
}

private suspend fun getPersonImageUrl(personId: String, mediaRepository: MediaRepository): String? {
    return mediaRepository.getImageUrl(
        itemId = personId,
        imageType = "Primary",
        width = 320,
        height = 480,
        quality = 90
    ).first()
}
