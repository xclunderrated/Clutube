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
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.graphicsLayer
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

private val ThumbnailShape = RoundedCornerShape(12.dp)
private val BadgeShape = RoundedCornerShape(4.dp)
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
    val animatedDim by animateFloatAsState(
        targetValue = if (isWatched) 0.52f else 1f,
        label = "watched_dim"
    )
    // Unified badge text: TV shows show S:E, movies show duration, others show duration.
    // Never render the literal "TV SERIES" as a duration badge.
    val badgeText: String? = remember(video) {
        if (video.mediaType == MediaType.TV_SHOW) {
            "S${video.currentSeason.coerceAtLeast(1)}:E${video.currentEpisode.coerceAtLeast(1)}"
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
        val formattedRating = video.rating
            ?.takeIf { it > 0 }
            ?.let { String.format(java.util.Locale.US, "%.1f", it) }

        FittedMediaThumbnail(
            thumbnailUrl = video.thumbnailUrl,
            backdropUrl = video.backdropUrl,
            posterUrl = video.posterUrl,
            logoUrl = video.logoUrl,
            hasTitledBackdrop = video.hasTitledBackdrop,
            contentDescription = video.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            imagePreset = ImagePreset.THUMBNAIL,
            isWatched = false,
            dimAlpha = animatedDim,
            shape = ThumbnailShape
        ) {
            // Subtle gradient overlay at bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
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

        // Details Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Channel Avatar
            StudioLogoAvatar(
                logoUrl = video.channelAvatarUrl,
                contentDescription = video.channelName,
                modifier = Modifier
                    .size(36.dp)
                    .then(channelClickModifier)
            )

            Spacer(modifier = Modifier.width(12.dp))

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
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 20.sp,
                    letterSpacing = (-0.1).sp
                )

                Spacer(modifier = Modifier.height(3.dp))

                // Single-line YouTube metadata: Studio • Year (exactly once —
                // publishedAt duplicates the year, so it is skipped then).
                // Trailer view counts are intentionally hidden on the Home
                // feed (main page) — the media-type tag sits right-aligned.
                val metaLine = remember(video, releaseYear, continueLabel) {
                    buildList {
                        add(video.channelName)
                        if (!releaseYear.isNullOrBlank()) add(releaseYear)
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
                    Text(
                        text = metaLine,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (video.isVerified) {
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
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            letterSpacing = 0.6.sp,
                            maxLines = 1
                        )
                    }
                }
                // Rating + format line under the channel name: star score plus
                // season/episode totals for shows or runtime for movies.
                // Totals render only when genuinely known (from TMDB details
                // or card enrichment) — never placeholder defaults.
                val extraInfo = remember(video) {
                    when (video.mediaType) {
                        MediaType.TV_SHOW -> {
                            val seasons = video.totalSeasons
                            val episodes = video.totalEpisodes
                            if (seasons > 1 || episodes > 1) {
                                val seasonPart = "$seasons Season" + if (seasons == 1) "" else "s"
                                val episodePart = "$episodes Episode" + if (episodes == 1) "" else "s"
                                "$seasonPart · $episodePart"
                            } else {
                                null
                            }
                        }
                        MediaType.MOVIE -> {
                            video.duration.takeIf { it.isNotBlank() }
                                ?: video.runtimeMinutes?.takeIf { it > 0 }?.let { mins ->
                                    val hrs = mins / 60
                                    val remain = mins % 60
                                    if (hrs > 0) "${hrs}h ${remain}m" else "${mins}m"
                                }
                        }
                        else -> null
                    }
                }
                if (!formattedRating.isNullOrBlank() || !extraInfo.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!formattedRating.isNullOrBlank()) {
                            ImdbRatingBadge(
                                rating = formattedRating,
                                compact = true
                            )
                        }
                        if (!formattedRating.isNullOrBlank() && !extraInfo.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "•",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        if (!extraInfo.isNullOrBlank()) {
                            Text(
                                text = extraInfo,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (!continueLabel.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = continueLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
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

