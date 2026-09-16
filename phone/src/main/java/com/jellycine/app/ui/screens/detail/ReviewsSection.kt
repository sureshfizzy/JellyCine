package com.jellycine.app.ui.screens.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.jellycine.data.model.BaseItemDto
import com.jellycine.data.model.TmdbReview
import com.jellycine.data.repository.MediaRepository
import com.jellycine.shared.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val REVIEW_CARD_COLOR = Color(0xFF1E1E22)

@Composable
internal fun ReviewsSection(
    item: BaseItemDto,
    isSeerDetail: Boolean,
    mediaRepository: MediaRepository,
    modifier: Modifier = Modifier
) {
    if (isSeerDetail) return

    var reviews by remember(item.id) { mutableStateOf(emptyList<TmdbReview>()) }
    var selectedReview by remember(item.id) { mutableStateOf<TmdbReview?>(null) }

    LaunchedEffect(item.id) {
        reviews = mediaRepository.getTmdbReviews(item)
    }

    if (reviews.isEmpty()) return

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.detail_reviews),
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = reviews,
                key = { it.id }
            ) { review ->
                ReviewCard(
                    review = review,
                    onClick = { selectedReview = review }
                )
            }
        }
    }

    selectedReview?.let { review ->
        ReviewDetailSheet(
            review = review,
            onDismissRequest = { selectedReview = null }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewCard(
    review: TmdbReview,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .width(300.dp)
            .height(212.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = REVIEW_CARD_COLOR),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            ReviewAuthorRow(review = review, compact = true)

            Spacer(modifier = Modifier.height(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                Text(
                    text = review.content.trim(),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    color = Color.White.copy(alpha = 0.78f),
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(28.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, REVIEW_CARD_COLOR)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.detail_reviews_read_full),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF6FA8FF)
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = Color(0xFF6FA8FF),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ReviewAuthorRow(
    review: TmdbReview,
    compact: Boolean = false
) {
    val avatarSize = if (compact) 38.dp else 44.dp

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(Color(0xFF2E2E32)),
            contentAlignment = Alignment.Center
        ) {
            val avatarUrl = review.avatarUrl
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = review.displayName,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Text(
                    text = review.displayName.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    fontSize = if (compact) 15.sp else 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = review.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            review.formattedDate()?.let { date ->
                Text(
                    text = date,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        review.rating?.let { rating ->
            Spacer(modifier = Modifier.width(8.dp))
            RatingChip(rating = rating)
        }
    }
}

@Composable
private fun RatingChip(rating: Double) {
    val accent = ratingColor(rating)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.16f))
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.Star,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = stringResource(R.string.detail_reviews_rating, rating.formatRating()),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )
    }
}

private fun ratingColor(rating: Double): Color = when {
    rating >= 7.0 -> Color(0xFF66BB6A)
    rating >= 5.0 -> Color(0xFFFFC107)
    else -> Color(0xFFEF5350)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReviewDetailSheet(
    review: TmdbReview,
    onDismissRequest: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color(0xFF0E131A),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ReviewAuthorRow(review = review)
                }
                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = review.content,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = Color.White.copy(alpha = 0.9f),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

private val REVIEW_DATE_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

private fun TmdbReview.formattedDate(): String? {
    val raw = createdAt?.takeIf { it.length >= 10 } ?: return null
    return runCatching {
        LocalDate.parse(raw.substring(0, 10)).format(REVIEW_DATE_FORMATTER)
    }.getOrNull()
}

private fun Double.formatRating(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString()