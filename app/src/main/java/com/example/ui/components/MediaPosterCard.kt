package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MediaType
import com.example.model.VideoItem
import com.example.ui.theme.YTSuccess
import com.example.ui.theme.YouTubeRed
import com.example.util.ImagePreset

private val PosterShape = RoundedCornerShape(12.dp)
private val RatingBadgeShape = RoundedCornerShape(6.dp)

/**
 * Netflix-style vertical 2:3 poster card that renders official movie/TV artwork
 * perfectly filling the portrait frame with zero black bars or side pillars.
 */
@Composable
fun MediaPosterCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isWatched: Boolean = false,
    isSaved: Boolean = false,
    isDownloaded: Boolean = false,
    progressFraction: Float? = null,
    continueLabel: String? = null,
    onToggleSave: (() -> Unit)? = null,
    onDownloadClick: (() -> Unit)? = null,
    onMoreOptions: (() -> Unit)? = null
) {
    val ratingFormatted = video.rating
        ?.takeIf { it > 0 }
        ?.let { String.format(java.util.Locale.US, "%.1f", it) }

    val releaseYear = video.releaseDateFormatted
        ?.take(4)
        ?.takeIf { it.all { char -> char.isDigit() } }

    val haptics = LocalHapticFeedback.current
    val effectiveProgress = progressFraction?.takeIf { it > 0.01f && it < 0.99f }
    val episodeLabel = if (video.mediaType == MediaType.TV_SHOW) {
        "S${video.currentSeason.coerceAtLeast(1)}:E${video.currentEpisode.coerceAtLeast(1)}"
    } else null

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(PosterShape)
            .clickable(
                onClick = onClick,
                role = Role.Button,
                onClickLabel = "Play ${video.title}"
            )
            .testTag("poster_card_${video.id}"),
        shape = PosterShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        // 0 elevation in grids: per-item shadows create offscreen buffers
        // re-rendered on scroll. Clip alone gives the card shape.
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f) // Standard 2:3 cinematic poster ratio
        ) {
            // Poster fills 100% of container with zero letterboxing
            FittedMediaThumbnail(
                thumbnailUrl = video.thumbnailUrl,
                backdropUrl = video.backdropUrl,
                posterUrl = video.posterUrl,
                isPosterRatio = true,
                contentDescription = video.title,
                modifier = Modifier.fillMaxSize(),
                imagePreset = ImagePreset.POSTER_CARD,
                isWatched = isWatched,
                shape = PosterShape
            )

            // Gradient scrim for text legibility at bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.50f to Color.Transparent,
                            0.75f to Color.Black.copy(alpha = 0.65f),
                            1.0f to Color.Black.copy(alpha = 0.95f)
                        )
                    )
            )

            // Top Badges (Rating, Media Type & Offline status + Save/Download actions)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Media type badge
                Box(
                    modifier = Modifier
                        .clip(RatingBadgeShape)
                        .background(
                            if (video.mediaType == MediaType.TV_SHOW) YouTubeRed else Color.Black.copy(alpha = 0.75f)
                        )
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = if (video.mediaType == MediaType.TV_SHOW) "TV" else "MOVIE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Downloaded checkmark badge if saved locally
                    if (isDownloaded) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(YTSuccess)
                                .padding(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Downloaded",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    if (onToggleSave != null) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable(
                                    role = Role.Checkbox,
                                    onClickLabel = if (isSaved) "Remove ${video.title} from My List" else "Save ${video.title} to My List"
                                ) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onToggleSave()
                                }
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (isSaved) "Saved to My List" else "Save to My List",
                                tint = if (isSaved) YouTubeRed else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    if (onDownloadClick != null && !isDownloaded) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "Download ${video.title}"
                                ) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onDownloadClick()
                                }
                                .background(Color.Black.copy(alpha = 0.55f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FileDownload,
                                contentDescription = "Download",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Content
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp)
            ) {
                Text(
                    text = video.title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (ratingFormatted != null) {
                            ImdbRatingBadge(rating = ratingFormatted, compact = true)
                        }

                        if (releaseYear != null) {
                            if (ratingFormatted != null) {
                                Text(
                                    text = " • ",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            Text(
                                text = releaseYear,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }

                    if (onMoreOptions != null) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "Options for ${video.title}",
                                    onClick = onMoreOptions
                                )
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options for ${video.title}",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Continue-watching / episode context under rating row.
                val bottomMeta = listOfNotNull(
                    episodeLabel,
                    continueLabel
                ).joinToString(" • ")
                if (bottomMeta.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = bottomMeta,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (continueLabel != null) YouTubeRed else Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Continue-watching progress bar across the poster bottom edge.
            if (effectiveProgress != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(effectiveProgress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(YouTubeRed)
                )
            }
        }
    }
}
