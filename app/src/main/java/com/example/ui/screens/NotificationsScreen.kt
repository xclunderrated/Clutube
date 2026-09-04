package com.example.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.AppNotification
import com.example.model.MediaType
import com.example.model.NotificationKind
import com.example.model.VideoItem
import com.example.model.playbackKey
import com.example.model.releaseAlertId
import com.example.ui.components.FittedMediaThumbnail
import com.example.ui.components.StudioLogoAvatar
import com.example.ui.theme.YouTubeRed
import com.example.util.ImagePreset
import com.example.util.rememberOptimizedImageRequest
import com.example.util.rememberThumbnailRequestWithFallback

@Composable
fun NotificationsScreen(
    notifications: List<AppNotification> = emptyList(),
    upcomingVideos: List<VideoItem> = emptyList(),
    releaseAlertIds: Set<String> = emptySet(),
    onVideoClick: (AppNotification) -> Unit = {},
    onUpcomingVideoClick: (VideoItem) -> Unit = {},
    onToggleReleaseAlert: (VideoItem) -> Unit = {},
    onMarkRead: (String, Boolean) -> Unit = { _, _ -> },
    onMarkAllRead: () -> Unit = {},
    onDismiss: (String) -> Unit = {},
    onClearRead: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val visibleNotifications = remember(notifications) {
        notifications.filterNot { it.isDismissed }
    }
    val unreadCount = remember(visibleNotifications) {
        visibleNotifications.count { !it.isRead }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("notifications_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Notifications",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (unreadCount == 0) "You're all caught up" else "$unreadCount unread",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onMarkAllRead,
                enabled = unreadCount > 0,
                modifier = Modifier.testTag("notifications_mark_all_read")
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Mark all as read",
                    tint = if (unreadCount > 0) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                )
            }
            IconButton(
                onClick = onClearRead,
                enabled = visibleNotifications.any { it.isRead },
                modifier = Modifier.testTag("notifications_clear_read")
            ) {
                Icon(
                    imageVector = Icons.Default.ClearAll,
                    contentDescription = "Clear read notifications",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        if (upcomingVideos.isNotEmpty()) {
            UpcomingReleasesShelf(
                videos = upcomingVideos,
                releaseAlertIds = releaseAlertIds,
                onVideoClick = onUpcomingVideoClick,
                onToggleReleaseAlert = onToggleReleaseAlert
            )
        }

        if (visibleNotifications.isEmpty() && upcomingVideos.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "No notifications yet",
                        modifier = Modifier.padding(top = 10.dp),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Release alerts and subscription updates will appear here.",
                        modifier = Modifier.padding(top = 4.dp),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                items(
                    items = visibleNotifications,
                    key = { it.id },
                    contentType = { "notification" }
                ) { notification ->
                    NotificationRow(
                        notification = notification,
                        onClick = { onVideoClick(notification) },
                        onToggleRead = { onMarkRead(notification.id, !notification.isRead) },
                        onDismiss = { onDismiss(notification.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(
    notification: AppNotification,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onDismiss: () -> Unit
) {
    var menuOpen by remember(notification.id) { mutableStateOf(false) }
    val video = notification.targetVideo
    val (thumbnailRequest, onThumbnailError) = rememberThumbnailRequestWithFallback(
        primaryUrl = video.thumbnailUrl,
        fallbackUrl = video.backdropUrl,
        preset = ImagePreset.THUMBNAIL
    )
    val relativeTime = remember(notification.createdAtMillis) {
        DateUtils.getRelativeTimeSpanString(
            notification.createdAtMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }
    val kindLabel = when (notification.kind) {
        NotificationKind.RELEASE_ALERT -> "Release alert"
        NotificationKind.SUBSCRIPTION_RELEASE -> "Subscription"
        NotificationKind.WATCHED_SHOW_EPISODE -> "Watched show"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (!notification.isRead) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.background
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("notification_${notification.id}"),
        verticalAlignment = Alignment.Top
    ) {
        FittedMediaThumbnail(
            thumbnailUrl = video.thumbnailUrl,
            backdropUrl = video.backdropUrl,
            contentDescription = video.title,
            modifier = Modifier
                .width(112.dp)
                .aspectRatio(16f / 9f),
            imagePreset = ImagePreset.THUMBNAIL,
            isWatched = false,
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(
                imageVector = if (notification.kind == NotificationKind.RELEASE_ALERT ||
                    notification.kind == NotificationKind.WATCHED_SHOW_EPISODE
                ) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                contentDescription = kindLabel,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                    .padding(3.dp)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StudioLogoAvatar(
                    logoUrl = video.channelAvatarUrl,
                    contentDescription = video.channelName,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = video.channelName,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = notification.title,
                modifier = Modifier.padding(top = 4.dp),
                fontSize = 13.sp,
                fontWeight = if (notification.isRead) FontWeight.Medium else FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = notification.message,
                modifier = Modifier.padding(top = 2.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "$kindLabel · $relativeTime",
                modifier = Modifier.padding(top = 5.dp),
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f)
            )
        }

        Box {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Notification options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text(if (notification.isRead) "Mark as unread" else "Mark as read") },
                    onClick = {
                        menuOpen = false
                        onToggleRead()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Dismiss") },
                    onClick = {
                        menuOpen = false
                        onDismiss()
                    }
                )
            }
        }
    }
}

@Composable
private fun UpcomingReleasesShelf(
    videos: List<VideoItem>,
    releaseAlertIds: Set<String>,
    onVideoClick: (VideoItem) -> Unit,
    onToggleReleaseAlert: (VideoItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp)
            .testTag("upcoming_releases_shelf")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = YouTubeRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column {
                Text(
                    text = "Coming soon",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Get an alert when a movie or series is released",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = videos.take(10),
                key = { it.playbackKey() },
                contentType = { "upcoming_release_card" }
            ) { video ->
                val isAlertActive = releaseAlertId(video) in releaseAlertIds
                Column(
                    modifier = Modifier
                        .width(190.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onVideoClick(video) }
                        .testTag("upcoming_card_${video.id}")
                ) {
                    FittedMediaThumbnail(
                        thumbnailUrl = video.thumbnailUrl,
                        backdropUrl = video.backdropUrl,
                        contentDescription = video.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        imagePreset = ImagePreset.COMPACT_THUMBNAIL,
                        isWatched = false,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.9f))
                        ) {
                            IconButton(
                                onClick = { onToggleReleaseAlert(video) },
                                modifier = Modifier
                                    .size(34.dp)
                                    .testTag("upcoming_notify_${video.id}")
                            ) {
                                Icon(
                                    imageVector = if (isAlertActive) {
                                        Icons.Default.NotificationsActive
                                    } else {
                                        Icons.Default.NotificationsNone
                                    },
                                    contentDescription = if (isAlertActive) "Release alert enabled" else "Notify me when released",
                                    tint = if (isAlertActive) YouTubeRed else MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = video.title,
                        modifier = Modifier.padding(top = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = listOfNotNull(
                            if (video.mediaType == MediaType.TV_SHOW) "TV series" else "Movie",
                            video.releaseDateFormatted ?: video.releaseDateIso
                        ).joinToString(" · "),
                        modifier = Modifier.padding(top = 2.dp),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

        }
    }
}
