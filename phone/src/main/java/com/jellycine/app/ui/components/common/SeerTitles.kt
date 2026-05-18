package com.jellycine.app.ui.components.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import coil3.compose.AsyncImage
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.BaseItemPerson
import com.jellycine.data.model.SeerrPersonCreditType
import com.jellycine.data.model.SeerrItemIds
import com.jellycine.data.model.SeerrRecommendationTitle
import com.jellycine.data.model.SeerrRequestState
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
    val personItem = mediaRepository.getItemById(personId).getOrNull() ?: return emptyList()
    val personTmdbId = personItem.providerIds.providerId("tmdb") ?: return emptyList()
    return fetchSeerTmdbPerson(
        item = item,
        personTmdbId = personTmdbId,
        role = role,
        activeServerId = activeServerId,
        seerrRepository = seerrRepository
    )
}

internal suspend fun fetchSeerTmdbPerson(
    item: BaseItemDto,
    personTmdbId: String,
    role: SeerPersonRole,
    activeServerId: String?,
    seerrRepository: SeerrRepository
): List<SeerrRecommendationTitle> {
    val scopeId = seerrRepository.verifiedScopeId(activeServerId) ?: return emptyList()

    val seerrMediaType = if (item.type.equals("Movie", ignoreCase = true)) "movie" else "tv"
    return seerrRepository.getPersonTitles(
        scopeId = scopeId,
        personTmdbId = personTmdbId,
        mediaType = seerrMediaType,
        creditType = role.creditType
    ).getOrElse { emptyList() }
}

internal suspend fun fetchSeerTmdbPersonId(
    item: BaseItemDto,
    personId: String,
    role: SeerPersonRole,
    activeServerId: String?,
    mediaRepository: MediaRepository,
    seerrRepository: SeerrRepository
): List<SeerrRecommendationTitle> {
    val tmdbPersonId = SeerrItemIds.personTmdbId(personId)
    return if (tmdbPersonId != null) {
        fetchSeerTmdbPerson(
            item = item,
            personTmdbId = tmdbPersonId,
            role = role,
            activeServerId = activeServerId,
            seerrRepository = seerrRepository
        )
    } else {
        fetchSeerCreditTitles(
            item = item,
            personId = personId,
            role = role,
            activeServerId = activeServerId,
            mediaRepository = mediaRepository,
            seerrRepository = seerrRepository
        )
    }
}

internal suspend fun fetchSeerDirectedTitlesForTmdbPerson(
    personTmdbId: String,
    activeServerId: String?,
    seerrRepository: SeerrRepository
): List<SeerrRecommendationTitle> {
    val movieTitles = fetchSeerTmdbPerson(
        item = BaseItemDto(type = "Movie"),
        personTmdbId = personTmdbId,
        role = SeerPersonRole.DIRECTOR,
        activeServerId = activeServerId,
        seerrRepository = seerrRepository
    )
    val showTitles = fetchSeerTmdbPerson(
        item = BaseItemDto(type = "Series"),
        personTmdbId = personTmdbId,
        role = SeerPersonRole.DIRECTOR,
        activeServerId = activeServerId,
        seerrRepository = seerrRepository
    )
    return (movieTitles + showTitles).distinctBy { item -> "${item.mediaType}:${item.tmdbId}" }
}

internal fun LazyListScope.seerTitleItems(
    items: List<SeerrRecommendationTitle>,
    onItemClick: (String) -> Unit
) {
    itemsIndexed(
        items = items,
        key = { index, item ->
            val itemId = item.jellyfinMediaId?.takeIf { it.isNotBlank() }
                ?: SeerrItemIds.detailId(tmdbId = item.tmdbId, mediaType = item.mediaType)
            "${itemId}_$index"
        }
    ) { _, item ->
        SeerTitleCard(
            item = item,
            onClick = {
                val itemId = item.jellyfinMediaId?.takeIf { it.isNotBlank() }
                    ?: SeerrItemIds.detailId(tmdbId = item.tmdbId, mediaType = item.mediaType)
                onItemClick(itemId)
            }
        )
    }
}

@Composable
internal fun SeerTitlesRow(
    title: String,
    items: List<SeerrRecommendationTitle>,
    onItemClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 24.dp,
    verticalSpacing: Dp = 12.dp,
    horizontalPadding: Dp = 0.dp,
    titleFontSize: TextUnit = 21.sp
) {
    if (items.isEmpty()) return

    Column(
        modifier = modifier.padding(top = topPadding),
        verticalArrangement = Arrangement.spacedBy(verticalSpacing)
    ) {
        Text(
            text = title,
            fontSize = titleFontSize,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(horizontal = horizontalPadding)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = horizontalPadding)
        ) {
            seerTitleItems(
                items = items,
                onItemClick = onItemClick
            )
        }
    }
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

internal fun seerPersonId(
    items: List<BaseItemDto>,
    personName: String,
    role: SeerPersonRole
): String? {
    val seerPersonName = personName.normalizedMatchKey()
    return findPersonId(
        people = items.asSequence().flatMap { item -> item.people.orEmpty().asSequence() },
        seerPersonName = seerPersonName
    ) { person -> role.matches(person) }
}

@Composable
internal fun SeerTitleCard(
    item: SeerrRecommendationTitle,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val posterUrl = remember(item.posterUrl) { item.posterUrl?.takeIf { it.isNotBlank() } }

    val clickableModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier

    Column(
        modifier = clickableModifier.width(116.dp)
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

                if (item.jellyfinMediaId.isNullOrBlank()) {
                    SeerrTopBadges(
                        requestState = item.requestState,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                } else if (item.requestState == SeerrRequestState.REQUESTED) {
                    SeerrRequestBadge(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 4.dp, end = 6.dp)
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

@Composable
internal fun SeerrTopBadges(
    requestState: SeerrRequestState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        SeerrSourceBadge()

        if (requestState == SeerrRequestState.REQUESTED) {
            SeerrRequestBadge()
        }
    }
}

@Composable
internal fun SeerrSourceBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.height(20.dp),
        shape = RoundedCornerShape(999.dp),
        color = Color.Black.copy(alpha = 0.72f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Seerr",
                fontSize = 9.sp,
                color = Color(0xFF9CDCFE),
                fontWeight = FontWeight.SemiBold,
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                )
            )
        }
    }
}

@Composable
internal fun SeerrRequestBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color(0xFFE4E5FF),
        border = BorderStroke(1.dp, Color(0xFF8E90FF))
    ) {
        Box(
            modifier = Modifier.size(18.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = "Requested on Seerr",
                tint = Color(0xFF5F65E8),
                modifier = Modifier.size(13.dp)
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

private fun SeerrRepository.verifiedScopeId(activeServerId: String?): String? {
    val scopeId = activeServerId?.takeIf { it.isNotBlank() } ?: return null
    return scopeId.takeIf { getSavedConnectionInfo(it)?.isVerified == true }
}

internal fun SeerrRecommendationTitle.toSeerDetailItem(): BaseItemDto {
    return BaseItemDto(
        id = SeerrItemIds.detailId(tmdbId = tmdbId, mediaType = mediaType)
    )
}

internal fun BaseItemDto.seerTitleParams(): Pair<String, String>? {
    SeerrItemIds.detailParams(id.orEmpty())?.let { return it }
    val tmdbId = providerIds.providerId("tmdb") ?: return null
    val mediaType = when {
        type.equals("Series", ignoreCase = true) -> "tv"
        type.equals("Movie", ignoreCase = true) -> "movie"
        else -> return null
    }
    return mediaType to tmdbId
}