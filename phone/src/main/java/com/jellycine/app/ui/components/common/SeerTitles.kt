package com.jellycine.app.ui.components.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.BaseItemPerson
import com.jellycine.data.model.SeerrPersonCreditType
import com.jellycine.data.model.SeerrRecommendationTitle
import com.jellycine.data.repository.MediaRepository
import com.jellycine.data.repository.SeerrRepository
import java.util.Locale

internal enum class SeerPersonRole(
    val creditType: SeerrPersonCreditType
) {
    DIRECTOR(SeerrPersonCreditType.DIRECTOR),
    ACTOR(SeerrPersonCreditType.ACTOR)
}

internal suspend fun fetchSeerCreditTitles(
    item: BaseItemDto,
    personId: String,
    role: SeerPersonRole,
    activeServerId: String?,
    mediaRepository: MediaRepository,
    seerrRepository: SeerrRepository
): List<SeerrRecommendationTitle> {
    val scopeId = activeServerId?.takeIf { it.isNotBlank() } ?: return emptyList()
    val connectionInfo = seerrRepository.getSavedConnectionInfo(scopeId)
    if (connectionInfo?.isVerified != true) return emptyList()

    val personItem = mediaRepository.getItemById(personId).getOrNull() ?: return emptyList()
    val personTmdbId = personItem.providerIds.providerId("tmdb") ?: return emptyList()
    val seerrMediaType = if (item.type.equals("Movie", ignoreCase = true)) "movie" else "tv"

    return seerrRepository.getPersonTitles(
        scopeId = scopeId,
        personTmdbId = personTmdbId,
        mediaType = seerrMediaType,
        creditType = role.creditType
    ).getOrElse { emptyList() }
}

internal fun filterSeerTitlesForRow(
    seerrTitles: List<SeerrRecommendationTitle>,
    baseTitles: List<BaseItemDto>,
    item: BaseItemDto
): List<SeerrRecommendationTitle> {
    if (seerrTitles.isEmpty()) return emptyList()

    val seerBaseTitles = baseTitles.map { baseTitle ->
        baseTitle.name.normalizedMatchKey() to baseTitle.productionYear
    }.toSet()
    val existingLocalIds = buildSet {
        item.id?.let(::add)
        baseTitles.mapNotNullTo(this) { it.id }
    }

    return seerrTitles.filter { seerrTitle ->
        val normalizedSeerrTitle = seerrTitle.title.normalizedMatchKey()
        val matchesCurrentItem = normalizedSeerrTitle == item.name.normalizedMatchKey() &&
            yearMatch(item.productionYear, seerrTitle.productionYear)
        val matchesBaseTitle = seerBaseTitles.any { (titleKey, year) ->
            titleKey == normalizedSeerrTitle && yearMatch(year, seerrTitle.productionYear)
        }
        val localId = seerrTitle.jellyfinMediaId?.let(existingLocalIds::contains) == true

        !matchesCurrentItem && !matchesBaseTitle && !localId
    }
}

internal fun filterSeerTitles(
    seerrTitles: List<SeerrRecommendationTitle>,
    localItems: List<BaseItemDto>
): List<SeerrRecommendationTitle> {
    if (seerrTitles.isEmpty()) return emptyList()

    val localTitles = localItems.map { item ->
        item.name.normalizedMatchKey() to item.productionYear
    }.toSet()
    val localIds = localItems.mapNotNull { item -> item.id }.toSet()

    return seerrTitles.filterNot { seerrTitle ->
        val normalizedTitle = seerrTitle.title.normalizedMatchKey()
        val localIdMatch = seerrTitle.jellyfinMediaId?.let(localIds::contains) == true
        val titleMatch = localTitles.any { (titleKey, year) ->
            titleKey == normalizedTitle && yearMatch(year, seerrTitle.productionYear)
        }

        localIdMatch || titleMatch
    }
}

internal suspend fun seerPersonId(
    items: List<BaseItemDto>,
    personName: String,
    role: SeerPersonRole,
    mediaRepository: MediaRepository
): String? {
    val seerPersonName = personName.normalizedMatchKey()
    fun resolveFrom(people: Sequence<BaseItemPerson>) = findPersonId(
        people = people,
        seerPersonName = seerPersonName
    ) { person -> role.matches(person) }

    resolveFrom(items.asSequence().flatMap { item -> item.people.orEmpty().asSequence() })
        ?.let { return it }

    val fallbackItemIds = items
        .asSequence()
        .mapNotNull { item -> item.id }
        .distinct()
        .take(6)
        .toList()

    fallbackItemIds.forEach { itemId ->
        val detailItem = mediaRepository.getItemById(itemId).getOrNull() ?: return@forEach
        resolveFrom(detailItem.people.orEmpty().asSequence())?.let { return it }
    }

    return null
}

@Composable
internal fun SeerTitleCard(
    item: SeerrRecommendationTitle,
    modifier: Modifier = Modifier
) {
    val posterUrl = remember(item.posterPath) {
        item.posterPath
            ?.takeIf { it.isNotBlank() }
            ?.let { path -> "https://image.tmdb.org/t/p/w500$path" }
    }

    Column(
        modifier = modifier.width(116.dp)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(166.dp),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF23232A)
            )
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (!posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = item.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Movie,
                            contentDescription = item.title,
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    shape = RoundedCornerShape(999.dp),
                    color = Color.Black.copy(alpha = 0.72f)
                ) {
                    Text(
                        text = "Seerr",
                        fontSize = 11.sp,
                        color = Color(0xFF9CDCFE),
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                item.roleLabel
                    ?.takeIf { it.isNotBlank() }
                    ?.let { roleLabel ->
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = Color.Black.copy(alpha = 0.72f)
                        ) {
                            Text(
                                text = roleLabel,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.92f),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = item.title,
            fontSize = 12.sp,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            lineHeight = 14.sp
        )

        val subtitle = item.productionYear?.toString()
            ?: item.mediaType.replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase(Locale.US) else char.toString()
            }

        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.62f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp)
            )
        }
    }
}

private fun SeerPersonRole.matches(person: BaseItemPerson): Boolean {
    return when (this) {
        SeerPersonRole.DIRECTOR -> listOf(person.role, person.type).any { field ->
            field?.contains("Director", ignoreCase = true) == true
        }

        SeerPersonRole.ACTOR -> {
            val isDirector = listOf(person.role, person.type).any { field ->
                field?.contains("Director", ignoreCase = true) == true
            }
            if (isDirector) {
                false
            } else {
                listOf(person.role, person.type).any { field ->
                    field?.contains("Actor", ignoreCase = true) == true ||
                        field?.contains("Star", ignoreCase = true) == true ||
                        field?.contains("Cast", ignoreCase = true) == true
                } || person.type.isNullOrBlank()
            }
        }
    }
}

private fun findPersonId(
    people: Sequence<BaseItemPerson>,
    seerPersonName: String,
    matches: (BaseItemPerson) -> Boolean = { true }
): String? {
    return people
        .filter { person ->
            person.id != null &&
                person.name.normalizedMatchKey() == seerPersonName &&
                matches(person)
        }
        .groupBy { person -> person.id.orEmpty() }
        .maxByOrNull { (_, matches) -> matches.size }
        ?.key
        ?.takeIf { it.isNotBlank() }
}

private fun Map<String, String>?.providerId(providerName: String): String? {
    return this?.entries
        ?.firstOrNull { (key, value) ->
            key.equals(providerName, ignoreCase = true) && value.isNotBlank()
        }
        ?.value
}

private fun String?.normalizedMatchKey(): String {
    return this
        .orEmpty()
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "")
}

private fun yearMatch(localYear: Int?, seerrYear: Int?): Boolean {
    return localYear == null || seerrYear == null || localYear == seerrYear
}