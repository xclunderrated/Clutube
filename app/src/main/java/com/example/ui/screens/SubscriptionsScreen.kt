package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.ChannelItem
import com.example.model.VideoItem
import com.example.model.releaseAlertId
import com.example.ui.components.StudioLogoAvatar
import com.example.ui.components.VideoCard
import com.example.ui.theme.YTBlueVerified
import com.example.util.ImagePreset
import com.example.util.rememberOptimizedImageRequest

private val FilterPillShape = RoundedCornerShape(8.dp)
private val StoryIndicatorShape = CircleShape

@Composable
fun SubscriptionsScreen(
    channels: List<ChannelItem>,
    videos: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit,
    onChannelClick: (ChannelItem) -> Unit,
    onSaveToWatchLater: (VideoItem) -> Unit,
    onShare: (VideoItem) -> Unit,
    onAddToQueue: (VideoItem) -> Unit = {},
    watchedVideoIds: Set<String> = emptySet(),
    onToggleWatched: (VideoItem) -> Unit = {},
    onNotInterested: (VideoItem) -> Unit = {},
    onNotRecommendChannel: (VideoItem) -> Unit = {},
    releaseAlertIds: Set<String> = emptySet(),
    onToggleReleaseAlert: (VideoItem) -> Unit = {},
    onDownloadVideo: ((VideoItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedSubFilter by remember { mutableStateOf("All") }
    var selectedChannelFilterId by remember { mutableStateOf<String?>(null) }
    val filters = remember { listOf("All", "Movies", "Series", "Today", "Shorts", "Continue watching") }

    val filteredVideos = remember(videos, selectedSubFilter, selectedChannelFilterId, channels) {
        var list = videos
        if (selectedChannelFilterId != null) {
            val selectedChannel = channels.find { it.id == selectedChannelFilterId }
            if (selectedChannel != null) {
                list = list.filter { it.channelName.contains(selectedChannel.name, ignoreCase = true) || selectedChannel.name.contains(it.channelName, ignoreCase = true) }
            }
        }
        when (selectedSubFilter) {
            "Movies" -> list.filter { it.mediaType == com.example.model.MediaType.MOVIE }
            "Series" -> list.filter { it.mediaType == com.example.model.MediaType.TV_SHOW }
            "Shorts" -> list.filter { it.mediaType == com.example.model.MediaType.VIDEO || it.duration == "SHORT" }
            else -> list
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("subscriptions_screen")
    ) {
        if (channels.isNotEmpty()) {
            // Horizontal Story Avatars Bar with LazyRow
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(
                    items = channels,
                    key = { it.id },
                    contentType = { "channel_story" }
                ) { channel ->
                    val isSelected = selectedChannelFilterId == channel.id
                    ChannelStoryItem(
                        channel = channel,
                        isSelected = isSelected,
                        onClick = {
                            if (selectedChannelFilterId == channel.id) {
                                // If already selected, open full channel profile
                                onChannelClick(channel)
                            } else {
                                selectedChannelFilterId = channel.id
                            }
                        },
                        onLongClick = { onChannelClick(channel) }
                    )
                }

                item(key = "view_all_channels", contentType = "link") {
                    Text(
                        text = if (selectedChannelFilterId != null) "CLEAR" else "ALL",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = YTBlueVerified,
                        modifier = Modifier
                            .clickable {
                                if (selectedChannelFilterId != null) {
                                    selectedChannelFilterId = null
                                } else if (channels.isNotEmpty()) {
                                    onChannelClick(channels.first())
                                }
                            }
                            .padding(horizontal = 8.dp)
                    )
                }
            }
        }

        // Subscriptions Filter Pills with LazyRow
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(
                items = filters,
                key = { it },
                contentType = { "filter_pill" }
            ) { filter ->
                val isSelected = filter == selectedSubFilter
                Box(
                    modifier = Modifier
                        .clip(FilterPillShape)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { selectedSubFilter = filter }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = filter,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        if (filteredVideos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (channels.isEmpty()) "No Subscriptions Yet" else "No matching videos",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (channels.isEmpty()) "Subscribe to movie & TV studio channels to see all their uploads here." else "Try selecting another channel or filter.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            // Uploads Feed
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 8.dp)
            ) {
                items(
                    items = filteredVideos,
                    key = { it.id },
                    contentType = { "video_card" }
                ) { video ->
                    val onClick = remember(video.id, onVideoClick) { { onVideoClick(video) } }
                    val onSave = remember(video.id, onSaveToWatchLater) { { onSaveToWatchLater(video) } }
                    val onShareLambda = remember(video.id, onShare) { { onShare(video) } }
                    val onDownloadLambda = remember(video.id, onDownloadVideo) {
                        onDownloadVideo?.let { { it(video) } }
                    }
                    VideoCard(
                        video = video,
                        onClick = onClick,
                        onSaveToWatchLater = onSave,
                        onShare = onShareLambda,
                        onDownload = onDownloadLambda,
                        onAddToQueue = { onAddToQueue(video) },
                        isWatched = video.id in watchedVideoIds,
                        onToggleWatched = { onToggleWatched(video) },
                        onNotInterested = { onNotInterested(video) },
                        onNotRecommendChannel = { onNotRecommendChannel(video) },
                        isReleaseAlertActive = releaseAlertId(video) in releaseAlertIds,
                        onToggleReleaseAlert = { onToggleReleaseAlert(video) }
                    )
                }

                item(key = "sub_bottom_spacer", contentType = "spacer") {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}

@Composable
private fun ChannelStoryItem(
    channel: ChannelItem,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(58.dp)
            .clickable(onClick = onClick)
            .testTag("channel_story_${channel.id}")
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            StudioLogoAvatar(
                logoUrl = channel.avatarUrl,
                contentDescription = channel.name,
                modifier = Modifier.size(52.dp)
            )

            if (channel.hasNewStory) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(StoryIndicatorShape)
                        .background(YTBlueVerified)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = channel.name,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
