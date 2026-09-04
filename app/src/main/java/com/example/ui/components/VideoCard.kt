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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.MediaType
import com.example.model.VideoItem
import com.example.model.isUnreleased
import com.example.ui.theme.YTBlueVerified
import com.example.ui.theme.YouTubeRed
import com.example.util.ImagePreset
import com.example.util.rememberThumbnailRequestWithFallback

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

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
            contentDescription = video.title,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            imagePreset = ImagePreset.THUMBNAIL,
            isWatched = isWatched,
            shape = ThumbnailShape
        ) {
            // Subtle gradient overlay at bottom
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(CardBottomGradient)
            )

            // Duration Badge (bottom-right rounded pill). Unknown durations
            // stay hidden instead of showing a fabricated value.
            val cardDuration = video.duration.takeUnless { it.equals("TV SERIES", ignoreCase = true) }
            if (!cardDuration.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(BadgeShape)
                        .background(if (video.duration == "LIVE") YouTubeRed else Color.Black.copy(alpha = 0.85f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = cardDuration,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.3.sp
                    )
                }
            }

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
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = video.title,
                        modifier = Modifier.weight(1f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                        letterSpacing = (-0.1).sp
                    )
                    if (!formattedRating.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        ImdbRatingBadge(
                            rating = formattedRating,
                            compact = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = channelClickModifier
                ) {
                    Text(
                        text = video.channelName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (video.mediaType == MediaType.MOVIE || video.mediaType == MediaType.TV_SHOW) {
                        Text(
                            text = "• ${if (video.mediaType == MediaType.TV_SHOW) "TV Series" else "Movie"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 16.sp,
                            maxLines = 1
                        )
                    }

                    if (video.isVerified) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Verified Channel",
                            tint = YTBlueVerified,
                            modifier = Modifier.size(12.dp)
                        )
                    }

                    if (video.views.isNotBlank()) {
                        Text(
                            text = "•",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = video.views,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (video.publishedAt.isNotBlank()) {
                        Text(
                            text = "•",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = video.publishedAt,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 3-Dot Overflow Menu (w-5 h-5 opacity-60)
            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
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

