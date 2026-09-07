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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.example.model.isUnreleased
import com.example.model.playbackKey
import com.example.ui.theme.YTBlueVerified
import com.example.ui.theme.YouTubeRed
import com.example.util.ImagePreset
import kotlin.math.roundToInt

private val ThumbnailShape = RoundedCornerShape(12.dp)
private val BadgeShape = RoundedCornerShape(4.dp)
// Bottom-only scrim (40% height via alignment) — cheaper than full-fill gradient.
private val CardBottomGradient = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f))
)

@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onSaveToWatchLater: () -> Unit,
    onShare: () -> Unit,
    onDownload: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    isWatched: Boolean = false,
    isSaved: Boolean = false,
    progressFraction: Float? = null,
    continueLabel: String? = null,
    onToggleWatched: (() -> Unit)? = null,
    onNotInterested: (() -> Unit)? = null,
    onNotRecommendChannel: (() -> Unit)? = null,
    onChannelClick: ((String) -> Unit)? = null,
    isReleaseAlertActive: Boolean = false,
    onToggleReleaseAlert: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val channelClickModifier = remember(onChannelClick, video.channelName) {
        if (onChannelClick != null) {
            Modifier.clickable { onChannelClick(video.channelName) }
        } else {
            Modifier
        }
    }

    val haptics = LocalHapticFeedback.current
    // Static dim passed straight to thumbnail (no per-card animation).
    // Watched toggles are rare; snap is invisible at 0.52f vs animated.
    val dimAlpha = if (isWatched) 0.52f else 1f
    // Badge text: movies show the real length (2h 14m) once enrichment
    // resolves it; series show totals as "S4 - 10 ep". Resume cards (with
    // progress) keep the S:E position. Nothing fake is ever rendered —
    // unknown stays hidden instead of showing S1:E1 or "TV SERIES".
    val badgeText: String? = remember(video, progressFraction, continueLabel) {
        if (video.mediaType == MediaType.TV_SHOW) {
            if (video.totalSeasons > 0 && video.totalEpisodes > 0) {
                "S${video.totalSeasons} - ${video.totalEpisodes} ep"
            } else if (progressFraction != null || !continueLabel.isNullOrBlank()) {
                "S${video.currentSeason.coerceAtLeast(1)}:E${video.currentEpisode.coerceAtLeast(1)}"
            } else {
                null
            }
        } else {
            video.duration.takeUnless {
                it.isBlank() || it.equals("TV SERIES", ignoreCase = true)
            }
        }
    }
    val isLive = video.duration == "LIVE"
    val effectiveProgress = progressFraction?.takeIf { it > 0.01f && it < 0.99f }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                role = Role.Button,
                onClickLabel = "Play ${video.title}"
            )
            .padding(bottom = 16.dp)
            .testTag("video_card_${video.id}")
    ) {
        // Thumbnail Box with 12.dp rounded corners matching Morphe screenshot
        // IMDb-style rating renders below the channel line as star + X/10.
        val ratingOutOfTen = remember(video.rating) {
            video.rating?.takeIf { it > 0 }?.let { "${it.roundToInt().coerceIn(1, 10)}/10" }
        }

        FittedMediaThumbnail(
            thumbnailUrl = video.thumbnailUrl,
            backdropUrl = video.backdropUrl,
            posterUrl = video.posterUrl,
            contentDescription = video.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            imagePreset = ImagePreset.THUMBNAIL,
            isWatched = false,
            dimAlpha = dimAlpha,
            shape = ThumbnailShape
        ) {
            // Subtle gradient overlay at bottom only (40% height, not full-fill)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomStart)
                    .background(CardBottomGradient)
            )

            // "WATCHED" marker for watched non-cinema items (no media-type
            // pill on the artwork — the type now lives in the metadata line).
            if (isWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(BadgeShape)
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "WATCHED",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isSaved) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(BadgeShape)
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 6.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "SAVED",
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Duration / S:E Badge (bottom-right rounded pill). Unknown durations
            // stay hidden instead of showing a fabricated value.
            if (!badgeText.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(BadgeShape)
                        .background(if (isLive) YouTubeRed else Color.Black.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.3.sp
                    )
                }
            }

            // Continue-watching progress (YouTube-style red bar).
            if (effectiveProgress != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(effectiveProgress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(YouTubeRed)
                )
            }

            if (onToggleReleaseAlert != null &&
                isUnreleased(video.releaseDateIso ?: video.releaseDateFormatted)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                ) {
                    IconButton(
                        onClick = onToggleReleaseAlert,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("video_notify_${video.id}")
                    ) {
                        Icon(
                            imageVector = if (isReleaseAlertActive) {
                                Icons.Default.NotificationsActive
                            } else {
                                Icons.Default.NotificationsNone
                            },
                            contentDescription = if (isReleaseAlertActive) "Release alert enabled" else "Notify me when released",
                            tint = if (isReleaseAlertActive) YouTubeRed else MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Details Row - compact, non-bold, cleaner text under thumbnail.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Channel Avatar — fall back to the title's own poster art when
            // the feed item has no studio avatar yet (list responses carry
            // no company info; the real studio logo lands on open via
            // fetchFullMediaDetails). Never an empty circle.
            StudioLogoAvatar(
                logoUrl = video.channelAvatarUrl.takeIf { it.isNotBlank() }
                    ?: video.posterUrl?.takeIf { it.isNotBlank() }
                    ?: video.thumbnailUrl.takeIf { it.isNotBlank() },
                contentDescription = video.channelName,
                modifier = Modifier
                    .size(32.dp)
                    .then(channelClickModifier)
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Title & Metadata
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // The catalog title carries a " (YYYY)" suffix, so strip it
                // for display — the year is shown once in the meta line below.
                val releaseYear = remember(video) {
                    video.releaseDateFormatted?.take(4)?.takeIf { it.all(Char::isDigit) }
                        ?: video.releaseDateIso?.take(4)?.takeIf { it.all(Char::isDigit) }
                }
                val displayTitle = remember(video.title, releaseYear) {
                    val raw = video.title.trim()
                    if (!releaseYear.isNullOrBlank()) {
                        raw.removeSuffix(" ($releaseYear)").trim().ifBlank { raw }
                    } else {
                        raw
                    }
                }
                Text(
                    text = displayTitle,
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp
                )

                Spacer(modifier = Modifier.height(2.dp))

                // YouTube-style metadata: the studio name slot shows a shimmer
                // placeholder until enrichment resolves it — the "TMDB Catalog"
                // placeholder string is never rendered. Year/views/date (all
                // real data) render immediately.
                val studioPending = remember(video.channelName) {
                    isStudioPending(video.channelName)
                }
                val metaTail = remember(video, releaseYear, continueLabel) {
                    buildList {
                        if (!releaseYear.isNullOrBlank()) add(releaseYear)
                        if (!video.views.isBlank()) add(video.views)
                        val published = video.publishedAt.takeIf { it.isNotBlank() }
                        if (published != null && published != releaseYear) add(published)
                    }.joinToString(" • ")
                }
                val typeLabel = remember(video.mediaType) {
                    when (video.mediaType) {
                        MediaType.TV_SHOW -> "TV"
                        MediaType.MOVIE -> "MOVIE"
                        else -> null
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = channelClickModifier
                ) {
                    if (studioPending) {
                        PendingStudioPlaceholder()
                    } else {
                        Text(
                            text = video.channelName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (metaTail.isNotBlank()) {
                        if (studioPending) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = metaTail,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        } else {
                            Text(
                                text = " • $metaTail",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (video.isVerified && !studioPending) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified Channel",
                            tint = YTBlueVerified,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (!typeLabel.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = typeLabel,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            letterSpacing = 0.6.sp,
                            maxLines = 1
                        )
                    }
                }
                // IMDb rating below the channel line: amber star + X/10,
                // quiet YouTube-style metadata text.
                if (!ratingOutOfTen.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("video_rating_${video.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFC107),
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = ratingOutOfTen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (!continueLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = continueLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = YouTubeRed,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // 3-Dot Overflow Menu (48dp touch target for a11y, YouTube look kept)
            Box {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuExpanded = true
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .heightIn(min = 40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options for ${video.title}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                if (menuExpanded) {
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (onDownload != null) {
                            val dlText = if (video.mediaType == MediaType.TV_SHOW) {
                                "Download (S${video.currentSeason.coerceAtLeast(1)}:E${video.currentEpisode.coerceAtLeast(1)})"
                            } else {
                                "Download"
                            }
                            DropdownMenuItem(
                                text = { Text(dlText) },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onDownload()
                                }
                            )
                        }
                        if (onAddToQueue != null) {
                            DropdownMenuItem(
                                text = { Text("Add to queue") },
                                leadingIcon = { Icon(Icons.Default.QueueMusic, null) },
                                onClick = {
                                    menuExpanded = false
                                    onAddToQueue()
                                }
                            )
                        }
                        if (onToggleWatched != null) {
                            DropdownMenuItem(
                                text = { Text(if (isWatched) "Mark as unwatched" else "Mark as watched") },
                                leadingIcon = { Icon(Icons.Default.CheckCircle, null) },
                                onClick = {
                                    menuExpanded = false
                                    onToggleWatched()
                                }
                            )
                        }
                        if (onNotInterested != null) {
                            DropdownMenuItem(
                                text = { Text("Not interested") },
                                leadingIcon = { Icon(Icons.Default.VisibilityOff, null) },
                                onClick = {
                                    menuExpanded = false
                                    onNotInterested()
                                }
                            )
                        }
                        if (onNotRecommendChannel != null) {
                            DropdownMenuItem(
                                text = { Text("Don't recommend this channel") },
                                leadingIcon = { Icon(Icons.Default.Block, null) },
                                onClick = {
                                    menuExpanded = false
                                    onNotRecommendChannel()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Save to Watch later") },
                            leadingIcon = { Icon(Icons.Default.WatchLater, null) },
                            onClick = {
                                menuExpanded = false
                                onSaveToWatchLater()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, null) },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                    }
                }
            }
        }
    }
}

