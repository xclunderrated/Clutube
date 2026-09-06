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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppNotification
import com.example.model.MediaType
import com.example.model.NotificationKind
import com.example.model.VideoItem
import com.example.model.playbackKey
import com.example.model.releaseAlertId
import com.example.model.releaseDateMillis
import com.example.ui.components.FittedMediaThumbnail
import com.example.ui.components.StudioLogoAvatar
import com.example.ui.theme.YouTubeRed
import com.example.util.ImagePreset
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

private enum class NotificationFilter(val label: String) {
    ALL("All"),
    RELEASES("Releases"),
    SUBSCRIPTIONS("Subscriptions"),
    WATCHED("Watched")
}

private fun NotificationFilter.matches(notification: AppNotification): Boolean = when (this) {
    NotificationFilter.ALL -> true
    NotificationFilter.RELEASES -> notification.kind == NotificationKind.RELEASE_ALERT
    NotificationFilter.SUBSCRIPTIONS -> notification.kind == NotificationKind.SUBSCRIPTION_RELEASE
    NotificationFilter.WATCHED -> notification.kind == NotificationKind.WATCHED_SHOW_EPISODE
}

private enum class NotificationAge(val label: String) {
    TODAY("Today"),
    THIS_WEEK("This week"),
    EARLIER("Earlier")
}

private fun ageOf(createdAtMillis: Long, nowMillis: Long): NotificationAge {
    val age = nowMillis - createdAtMillis
    return when {
        age < TimeUnit.DAYS.toMillis(1) -> NotificationAge.TODAY
        age < TimeUnit.DAYS.toMillis(7) -> NotificationAge.THIS_WEEK
        else -> NotificationAge.EARLIER
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    var selectedFilter by remember { mutableStateOf(NotificationFilter.ALL) }
    val visibleNotifications = remember(notifications) {
        notifications.filterNot { it.isDismissed }
    }
    val unreadCount = remember(visibleNotifications) {
        visibleNotifications.count { !it.isRead }
    }
    val filteredNotifications = remember(visibleNotifications, selectedFilter) {
        visibleNotifications.filter { selectedFilter.matches(it) }
    }
    val groupedNotifications = remember(filteredNotifications) {
        val now = System.currentTimeMillis()
        NotificationAge.values().mapNotNull { age ->
            val items = filteredNotifications.filter { ageOf(it.createdAtMillis, now) == age }
            if (items.isEmpty()) null else age to items.sortedByDescending { it.createdAtMillis }
        }
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
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
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

        // Netflix-style filter pills narrow the inbox without hiding the
        // upcoming shelf, which is global regardless of the filter.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = NotificationFilter.values().toList(),
                key = { it.name },
                contentType = { "notification_filter" }
            ) { filter ->
                val selected = filter == selectedFilter
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = if (selected) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { selectedFilter = filter }
                        .testTag("notifications_filter_${filter.name.lowercase()}")
                ) {
                    Text(
                        text = filter.label,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.background
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
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

        when {
            visibleNotifications.isEmpty() && upcomingVideos.isEmpty() -> {
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
            }
            filteredNotifications.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.NotificationsNone,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "Nothing under ${selectedFilter.label}",
                            modifier = Modifier.padding(top = 10.dp),
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Try a different filter to see more updates.",
                            modifier = Modifier.padding(top = 4.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Plain for-loop (not forEach): forEach would lose the
                    // LazyListScope receiver, so item()/items() would not resolve.
                    for ((age, sectionItems) in groupedNotifications) {
                        item(
                            key = "notifications_header_${age.name}",
                            contentType = "notification_group_header"
                        ) {
                            Text(
                                text = age.label,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                            )
                        }
                        items(
                            items = sectionItems,
                            key = { it.id },
                            contentType = { "notification" }
                        ) { notification ->
                            if (!notification.isRead &&
                                (notification.kind == NotificationKind.RELEASE_ALERT ||
                                    notification.kind == NotificationKind.WATCHED_SHOW_EPISODE)
                            ) {
                                NotificationHeroCard(
                                    notification = notification,
                                    onClick = { onVideoClick(notification) },
                                    onToggleRead = { onMarkRead(notification.id, !notification.isRead) },
                                    onDismiss = { onDismiss(notification.id) }
                                )
                            } else {
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
        }
    }
}

/**
 * Netflix-style hero card for fresh release alerts: full-width 16:9 show art
 * with a NEW badge, studio row, and an explicit Watch Now CTA.
 */
@Composable
private fun NotificationHeroCard(
    notification: AppNotification,
    onClick: () -> Unit,
    onToggleRead: () -> Unit,
    onDismiss: () -> Unit
) {
    var menuOpen by remember(notification.id) { mutableStateOf(false) }
    val video = notification.targetVideo
    val relativeTime = remember(notification.createdAtMillis) {
        DateUtils.getRelativeTimeSpanString(
            notification.createdAtMillis,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }
    val episodeTag = if (notification.season != null && notification.episode != null) {
        "S${notification.season}:E${notification.episode}"
    } else null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .clickable(onClick = onClick)
            .testTag("notification_${notification.id}")
    ) {
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
            shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)
        ) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = YouTubeRed
                ) {
                    Text(
                        text = "NEW",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
                if (episodeTag != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f)
                    ) {
                        Text(
                            text = episodeTag,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StudioLogoAvatar(
                logoUrl = video.channelAvatarUrl,
                contentDescription = video.channelName,
                modifier = Modifier.size(34.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = notification.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(video.channelName.takeIf { it.isNotBlank() }, relativeTime)
                        .joinToString(" · "),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.size(36.dp)
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

        Text(
            text = notification.message,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        TextButton(
            onClick = onClick,
            colors = ButtonDefaults.textButtonColors(contentColor = YouTubeRed),
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "Watch now", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (!notification.isRead) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag("notification_${notification.id}"),
        verticalAlignment = Alignment.Top
    ) {
        FittedMediaThumbnail(
            thumbnailUrl = video.thumbnailUrl,
            backdropUrl = video.backdropUrl,
            posterUrl = video.posterUrl,
            contentDescription = video.title,
            modifier = Modifier
                .width(128.dp)
                .aspectRatio(16f / 9f),
            imagePreset = ImagePreset.THUMBNAIL,
            isWatched = false,
            shape = RoundedCornerShape(8.dp)
        ) {
            if (!notification.isRead) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .clip(CircleShape)
                        .background(YouTubeRed)
                        .size(9.dp)
                )
            }
            Icon(
                imageVector = if (notification.kind == NotificationKind.RELEASE_ALERT ||
                    notification.kind == NotificationKind.WATCHED_SHOW_EPISODE
                ) Icons.Default.NotificationsActive else Icons.Default.NotificationsNone,
                contentDescription = kindLabel,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
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
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = YouTubeRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
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
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = videos.take(10),
                key = { it.playbackKey() },
                contentType = { "upcoming_release_card" }
            ) { video ->
                val isAlertActive = releaseAlertId(video) in releaseAlertIds
                val countdown = remember(video.releaseDateIso, video.releaseDateFormatted) {
                    countdownLabel(video.releaseDateIso ?: video.releaseDateFormatted)
                }
                Column(
                    modifier = Modifier
                        .width(210.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .clickable { onVideoClick(video) }
                        .testTag("upcoming_card_${video.id}")
                ) {
                    FittedMediaThumbnail(
                        thumbnailUrl = video.thumbnailUrl,
                        backdropUrl = video.backdropUrl,
                        posterUrl = video.posterUrl,
                        contentDescription = video.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f),
                        imagePreset = ImagePreset.COMPACT_THUMBNAIL,
                        isWatched = false,
                        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    ) {
                        // Type badge bottom-start, bell top-end.
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.72f),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                        ) {
                            Text(
                                text = if (video.mediaType == MediaType.TV_SHOW) "SERIES" else "MOVIE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (countdown != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = YouTubeRed,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = countdown,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = androidx.compose.ui.graphics.Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
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
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Text(
                            text = video.title,
                            fontSize = 13.sp,
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
}

/** Netflix-style countdown chip: "Today" / "Tomorrow" / "in 5d", null when released/unknown. */
private fun countdownLabel(rawDate: String?): String? {
    val millis = releaseDateMillis(rawDate) ?: return null
    val days = ceil((millis - System.currentTimeMillis()).toDouble() / TimeUnit.DAYS.toMillis(1)).toInt()
    return when {
        days <= 0 -> null
        days == 1 -> "Tomorrow"
        days < 30 -> "in ${days}d"
        else -> null
    }
}
