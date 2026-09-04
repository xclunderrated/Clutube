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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.MediaType
import com.example.model.VideoItem
import com.example.ui.theme.YTBlueVerified
import com.example.ui.theme.YouTubeRed
import com.example.util.ImagePreset
import com.example.util.rememberThumbnailRequestWithFallback

/**
 * Compact, sleek horizontal card designed specifically for "Up Next & Related" side panels on tablets
 * and related recommendation queues. Space-efficient and pleasing to look at.
 */
@Composable
fun CompactRelatedVideoCard(
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
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f))
            .clickable { onClick() }
            .padding(8.dp)
            .testTag("compact_related_video_card_${video.id}"),
        verticalAlignment = Alignment.Top
    ) {
        // Compact 16:9 Thumbnail displaying uncropped poster with ambient background
        FittedMediaThumbnail(
            thumbnailUrl = video.thumbnailUrl,
            backdropUrl = video.backdropUrl,
            posterUrl = video.posterUrl,
            contentDescription = video.title,
            modifier = Modifier
                .width(132.dp)
                .aspectRatio(16f / 9f),
            imagePreset = ImagePreset.COMPACT_THUMBNAIL,
            isWatched = isWatched,
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = "WATCHED",
                        color = Color.White,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Media Type Badge (Top Right)
            if (video.mediaType == MediaType.MOVIE || video.mediaType == MediaType.TV_SHOW) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(YouTubeRed.copy(alpha = 0.9f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = if (video.mediaType == MediaType.TV_SHOW) "TV" else "4K",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Duration Badge (Bottom Right)
            if (video.duration.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (video.duration == "LIVE") YouTubeRed else Color.Black.copy(alpha = 0.82f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = video.duration,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Video Metadata Column
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 1.dp)
        ) {
            Text(
                text = video.title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = video.channelName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (video.isVerified) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Verified",
                        tint = YTBlueVerified,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }

            val metadata = listOfNotNull(
                video.views.takeIf { it.isNotBlank() },
                video.publishedAt.takeIf { it.isNotBlank() }
            ).joinToString(" • ")
            if (metadata.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = metadata,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Context Menu Icon
        Box {
            IconButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }

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
                        text = { Text(dlText, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onDownload()
                        }
                    )
                }
                if (onAddToQueue != null) {
                    DropdownMenuItem(
                        text = { Text("Add to queue", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.QueueMusic, null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onAddToQueue()
                        }
                    )
                }
                if (onToggleWatched != null) {
                    DropdownMenuItem(
                        text = { Text(if (isWatched) "Mark as unwatched" else "Mark as watched", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onToggleWatched()
                        }
                    )
                }
                if (onNotInterested != null) {
                    DropdownMenuItem(
                        text = { Text("Not interested", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.VisibilityOff, null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onNotInterested()
                        }
                    )
                }
                if (onNotRecommendChannel != null) {
                    DropdownMenuItem(
                        text = { Text("Don't recommend this channel", fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Block, null, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            menuExpanded = false
                            onNotRecommendChannel()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Save to Watch later", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.WatchLater, null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        menuExpanded = false
                        onSaveToWatchLater()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Share", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        menuExpanded = false
                        onShare()
                    }
                )
            }
        }
    }
}
