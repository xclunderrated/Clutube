package com.example.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadStatus
import com.example.model.DeviceLayoutMode
import com.example.model.MediaType
import com.example.model.PlaybackPreferences
import com.example.model.PlaybackQuality
import com.example.model.SubtitlePreference
import com.example.model.WatchHistoryEntry
import com.example.model.VideoItem
import com.example.model.WatchLaterSort
import com.example.model.formatPlaybackTime
import com.example.model.playbackKey
import com.example.ui.components.FittedMediaThumbnail
import com.example.ui.components.LocalProfileAvatar
import com.example.ui.components.OfflineVideoPlayer
import com.example.ui.components.isLocalProfileImageReference
import com.example.ui.theme.YTBlueVerified
import com.example.ui.theme.YouTubeRed
import com.example.util.ImagePreset
import com.example.util.rememberOptimizedImageRequest
import com.example.util.rememberThumbnailRequestWithFallback
import java.io.File
import java.util.Locale

private val QuickChipShape = RoundedCornerShape(18.dp)
private val HistoryCardShape = RoundedCornerShape(8.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun YouScreen(
    watchHistory: List<WatchHistoryEntry>,
    savedVideos: List<VideoItem> = emptyList(),
    likedVideosCount: Int,
    savedVideosCount: Int,
    queueCount: Int = 0,
    watchLaterSort: WatchLaterSort = WatchLaterSort.RECENTLY_ADDED,
    showContinueWatchingOnHome: Boolean = false,
    releaseNotificationsEnabled: Boolean = true,
    localProfileName: String = "Clutube",
    localProfileAvatar: String = "C",
    deviceLayoutMode: DeviceLayoutMode = DeviceLayoutMode.AUTO,
    onSelectDeviceLayoutMode: (DeviceLayoutMode) -> Unit = {},
    onVideoClick: (VideoItem) -> Unit,
    onResumeHistory: (WatchHistoryEntry) -> Unit = { onVideoClick(it.video) },
    onViewAllHistory: () -> Unit = {},
    onRemoveHistory: (String) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onRemoveSaved: (String) -> Unit = {},
    onSetWatchLaterSort: (WatchLaterSort) -> Unit = {},
    onSetContinueWatchingOnHome: (Boolean) -> Unit = {},
    onSetReleaseNotificationsEnabled: (Boolean) -> Unit = {},
    playbackPreferences: PlaybackPreferences = PlaybackPreferences(),
    onQualitySelected: (PlaybackQuality) -> Unit = {},
    onSubtitleSelected: (SubtitlePreference) -> Unit = {},
    onAddToQueue: (VideoItem) -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onSaveProfile: (String, String) -> Unit = { _, _ -> },
    onClearLocalData: () -> Unit = {},
    notInterestedCount: Int = 0,
    notRecommendedChannelCount: Int = 0,
    onClearRecommendationPreferences: () -> Unit = {},
    onOpenServerDialog: () -> Unit,
    downloadsCount: Int = 0,
    downloads: List<DownloadEntity> = emptyList(),
    onOpenDownloads: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var isEditProfileOpen by remember { mutableStateOf(false) }
    var isClearDataDialogOpen by remember { mutableStateOf(false) }
    var isRecommendationDialogOpen by remember { mutableStateOf(false) }
    var isPlaybackPreferencesOpen by remember { mutableStateOf(false) }
    var selectedSavedIds by remember { mutableStateOf(emptySet<String>()) }
    var playingOfflineDownload by remember { mutableStateOf<DownloadEntity?>(null) }

    val completedDownloads = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.COMPLETED.name }
    }
    val activeDownloads = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.DOWNLOADING.name || it.status == DownloadStatus.QUEUED.name }
    }
    val totalDownloadBytes = remember(completedDownloads) {
        completedDownloads.sumOf { it.totalBytes.takeIf { s -> s > 0 } ?: File(it.localFilePath).length() }
    }

    // Group TV Shows for summary display
    val completedTvShows = remember(completedDownloads) {
        completedDownloads.filter { it.mediaType == MediaType.TV_SHOW.name }
            .groupBy { it.seriesTitle ?: it.title }
    }
    val completedMovies = remember(completedDownloads) {
        completedDownloads.filter { it.mediaType == MediaType.MOVIE.name }
    }

    // Full screen offline player dialog
    if (playingOfflineDownload != null) {
        val item = playingOfflineDownload!!
        OfflineVideoPlayer(
            title = item.seriesTitle ?: item.title,
            subtitle = if (item.seriesTitle != null) item.title else item.quality,
            localFilePath = item.localFilePath,
            serverName = item.serverName,
            subtitleCc = item.subtitleCc,
            onClose = { playingOfflineDownload = null }
        )
        return
    }

    val sortedSavedVideos = when (watchLaterSort) {
        WatchLaterSort.RECENTLY_ADDED -> savedVideos
        WatchLaterSort.OLDEST -> savedVideos.asReversed()
        WatchLaterSort.DURATION -> savedVideos.sortedBy { durationSortValue(it.duration) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("you_screen")
    ) {
        // Local-first profile header. It intentionally does not look like a
        // signed-in YouTube account or load a remote person's avatar.
        item(key = "profile_header", contentType = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LocalProfileAvatar(
                    value = localProfileAvatar,
                    imagePreset = ImagePreset.LARGE_AVATAR,
                    textSize = 24.sp,
                    modifier = Modifier.size(68.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = localProfileName,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Local profile",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { isEditProfileOpen = true }) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit local profile",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = onOpenServerDialog) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Stream servers",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        item(key = "local_profile_stats", contentType = "stats") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LocalStat(label = "History", value = watchHistory.size.toString(), onClick = onViewAllHistory)
                LocalStat(label = "Downloads", value = completedDownloads.size.toString(), onClick = onOpenDownloads)
                LocalStat(label = "Saved", value = savedVideosCount.toString())
                LocalStat(label = "Liked", value = likedVideosCount.toString())
            }
        }

        // Only show capabilities that exist locally; there is no fake account
        // sync or unavailable Google account state here.
        item(key = "quick_chips_row", contentType = "chips") {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "chip_local", contentType = "quick_chip") {
                    YouQuickChip(icon = Icons.Default.Settings, label = "Stored on this device")
                }
                item(key = "chip_servers", contentType = "quick_chip") {
                    YouQuickChip(
                        icon = Icons.Default.Dns,
                        label = "Stream Servers",
                        isHighlighted = true,
                        onClick = onOpenServerDialog
                    )
                }
            }
        }

        item(key = "spacer_after_chips", contentType = "spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }

        item(key = "watch_later_header", contentType = "watch_later") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Watch Later",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Saved on this device",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                WatchLaterSortMenu(
                    selected = watchLaterSort,
                    onSelected = onSetWatchLaterSort
                )
            }
        }

        if (selectedSavedIds.isNotEmpty()) {
            item(key = "watch_later_selection", contentType = "selection") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedSavedIds.size} selected",
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = {
                        selectedSavedIds.forEach(onRemoveSaved)
                        selectedSavedIds = emptySet()
                    }) {
                        Text("Remove")
                    }
                }
            }
        }

        if (sortedSavedVideos.isEmpty()) {
            item(key = "watch_later_empty", contentType = "empty") {
                Text(
                    text = "Save a video to find it here.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        } else {
            items(
                items = sortedSavedVideos,
                key = { "saved_${it.id}" },
                contentType = { "saved_video" }
            ) { video ->
                val historyEntry = watchHistory.firstOrNull { it.video.id == video.id || it.key == video.playbackKey() }
                SavedVideoRow(
                    video = video,
                    progressFraction = historyEntry?.progressFraction ?: 0f,
                    isSelected = video.id in selectedSavedIds,
                    onClick = { onVideoClick(video) },
                    onLongClick = {
                        selectedSavedIds = if (video.id in selectedSavedIds) {
                            selectedSavedIds - video.id
                        } else {
                            selectedSavedIds + video.id
                        }
                    },
                    onRemove = { onRemoveSaved(video.id) },
                    onAddToQueue = { onAddToQueue(video) }
                )
            }
        }

        item(key = "divider_watch_later", contentType = "divider") {
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // History Section
        item(key = "history_section", contentType = "history") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "History",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "View all",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = YTBlueVerified,
                            modifier = Modifier.clickable(onClick = onViewAllHistory)
                        )
                        if (watchHistory.isNotEmpty()) {
                            Text(
                                text = "Clear",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable(onClick = onClearHistory)
                            )
                        }
                    }
                }

                if (watchHistory.isEmpty()) {
                    Text(
                        text = "No watch history yet. Videos you play will appear here.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                } else {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = watchHistory,
                            key = { it.key },
                            contentType = { "history_card" }
                        ) { entry ->
                            val onClick = remember(entry.key, onResumeHistory) { { onResumeHistory(entry) } }
                            HistoryCard(
                                entry = entry,
                                onClick = onClick
                            )
                        }
                    }
                }
            }
        }

        // Downloads & Offline Media Section
        item(key = "downloads_shelf_section", contentType = "downloads") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = YouTubeRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Downloads",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (completedDownloads.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "${completedDownloads.size} offline",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Text(
                        text = if (completedDownloads.isNotEmpty() || activeDownloads.isNotEmpty()) "View all" else "Manage",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = YTBlueVerified,
                        modifier = Modifier.clickable(onClick = onOpenDownloads)
                    )
                }

                // Active Downloading Progress Banner
                if (activeDownloads.isNotEmpty()) {
                    val activeItem = activeDownloads.first()
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .clickable(onClick = onOpenDownloads),
                        color = YouTubeRed.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Downloading,
                                contentDescription = null,
                                tint = YouTubeRed,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Downloading: ${activeItem.seriesTitle ?: activeItem.title}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { (activeItem.progressPercent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = YouTubeRed,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "${activeItem.progressPercent}%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = YouTubeRed
                            )
                        }
                    }
                }

                if (completedDownloads.isEmpty() && activeDownloads.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .clickable(onClick = onOpenDownloads),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "No offline media yet",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "Download movies and TV shows to watch anywhere without internet.",
                                    fontSize = 11.5.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else if (completedDownloads.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Display TV Series cards (Shows -> Seasons -> Episodes)
                        completedTvShows.forEach { (seriesTitle, episodes) ->
                            val firstEp = episodes.minByOrNull { (it.seasonNumber ?: 1) * 1000 + (it.episodeNumber ?: 1) } ?: episodes.first()
                            val seasonsCount = episodes.mapNotNull { it.seasonNumber }.distinct().size.coerceAtLeast(1)
                            val totalSeriesBytes = episodes.sumOf { it.totalBytes.takeIf { s -> s > 0 } ?: File(it.localFilePath).length() }

                            item(key = "tv_preview_$seriesTitle") {
                                DownloadedMediaPreviewCard(
                                    title = seriesTitle,
                                    subtitle = "${seasonsCount} ${if (seasonsCount == 1) "Season" else "Seasons"} · ${episodes.size} ${if (episodes.size == 1) "ep" else "eps"}",
                                    badgeLabel = "TV SHOW",
                                    quality = firstEp.quality,
                                    serverName = firstEp.serverName,
                                    sizeLabel = formatBytes(totalSeriesBytes),
                                    thumbnailUrl = firstEp.thumbnailUrl,
                                    posterUrl = firstEp.posterUrl,
                                    backdropUrl = firstEp.backdropUrl,
                                    onClick = { playingOfflineDownload = firstEp }
                                )
                            }
                        }

                        // Display Movie cards
                        items(completedMovies, key = { "movie_preview_${it.id}" }) { movie ->
                            val fileSizeBytes = movie.totalBytes.takeIf { it > 0 } ?: File(movie.localFilePath).length()
                            DownloadedMediaPreviewCard(
                                title = movie.title,
                                subtitle = movie.duration ?: "Movie",
                                badgeLabel = "MOVIE",
                                quality = movie.quality,
                                serverName = movie.serverName,
                                sizeLabel = formatBytes(fileSizeBytes),
                                thumbnailUrl = movie.thumbnailUrl,
                                posterUrl = movie.posterUrl,
                                backdropUrl = movie.backdropUrl,
                                onClick = { playingOfflineDownload = movie }
                            )
                        }
                    }
                }
            }
        }

        item(key = "divider_history", contentType = "divider") {
            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        item(key = "spacer_after_library", contentType = "spacer") {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Local library actions. Unsupported cloud-account actions are not
        // shown as if they were available.
        item(key = "menu_items", contentType = "menu") {
            Column(modifier = Modifier.fillMaxWidth()) {
                YouMenuItem(
                    icon = Icons.Default.History,
                    title = "Watch history",
                    subtitle = "${watchHistory.size} videos",
                    onClick = onViewAllHistory
                )
                YouMenuItem(
                    icon = Icons.Default.Download,
                    title = "Downloads",
                    subtitle = when {
                        activeDownloads.isNotEmpty() && completedDownloads.isNotEmpty() ->
                            "${completedDownloads.size} offline (${formatBytes(totalDownloadBytes)}) · ${activeDownloads.size} downloading"
                        activeDownloads.isNotEmpty() ->
                            "${activeDownloads.size} downloading..."
                        completedDownloads.isNotEmpty() ->
                            "${completedDownloads.size} offline · ${formatBytes(totalDownloadBytes)}"
                        else ->
                            "No downloaded media · Tap to manage"
                    },
                    onClick = onOpenDownloads,
                    testTag = "you_downloads_menu_item"
                )
                YouMenuItem(
                    icon = Icons.Default.QueueMusic,
                    title = "Queue",
                    subtitle = if (queueCount == 0) "Nothing queued" else "$queueCount up next",
                    onClick = onOpenQueue
                )
                YouMenuItem(
                    icon = Icons.Default.Dns,
                    title = "Stream servers",
                    subtitle = "Choose a playback provider",
                    onClick = onOpenServerDialog
                )
                YouMenuItem(
                    icon = Icons.Default.Settings,
                    title = "Playback preferences",
                    subtitle = "${playbackQualityLabel(playbackPreferences.quality)} quality · ${subtitlePreferenceLabel(playbackPreferences.subtitles)} captions",
                    onClick = { isPlaybackPreferencesOpen = true },
                    testTag = "playback_preferences_settings"
                )
                YouMenuItem(
                    icon = Icons.Default.History,
                    title = "Continue watching on Home",
                    subtitle = if (showContinueWatchingOnHome) {
                        "Resume shelf is shown on the Home feed"
                    } else {
                        "Off · recent titles appear as normal cards"
                    },
                    onClick = { onSetContinueWatchingOnHome(!showContinueWatchingOnHome) },
                    trailingContent = {
                        Switch(
                            checked = showContinueWatchingOnHome,
                            onCheckedChange = onSetContinueWatchingOnHome,
                            modifier = Modifier.testTag("continue_watching_home_toggle")
                        )
                    }
                )
                YouMenuItem(
                    icon = Icons.Default.NotificationsActive,
                    title = "Release notifications",
                    subtitle = if (releaseNotificationsEnabled) {
                        "Alerts are scheduled on this device"
                    } else {
                        "Off · alerts stay in the app inbox"
                    },
                    onClick = { onSetReleaseNotificationsEnabled(!releaseNotificationsEnabled) },
                    trailingContent = {
                        Switch(
                            checked = releaseNotificationsEnabled,
                            onCheckedChange = onSetReleaseNotificationsEnabled,
                            modifier = Modifier.testTag("release_notifications_toggle")
                        )
                    }
                )
                YouMenuItem(
                    icon = Icons.Default.VisibilityOff,
                    title = "Recommendation controls",
                    subtitle = if (notInterestedCount == 0 && notRecommendedChannelCount == 0) {
                        "Personalize the Home feed"
                    } else {
                        "$notInterestedCount hidden videos, $notRecommendedChannelCount blocked channels"
                    },
                    onClick = { isRecommendationDialogOpen = true }
                )
                YouMenuItem(
                    icon = Icons.Default.DeleteSweep,
                    title = "Clear local data",
                    subtitle = "Remove profile, history, saved videos, and queue",
                    onClick = { isClearDataDialogOpen = true }
                )
            }
        }

        item(key = "you_bottom_spacer", contentType = "spacer") {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }

    if (isPlaybackPreferencesOpen) {
        var qualityExpanded by remember { mutableStateOf(false) }
        var subtitlesExpanded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { isPlaybackPreferencesOpen = false },
            title = { Text("Playback preferences") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Saved to your account and used by every stream server.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { qualityExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("quality_preference_picker")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Playback quality")
                                Text(
                                    playbackQualityLabel(playbackPreferences.quality),
                                    color = YouTubeRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = qualityExpanded,
                            onDismissRequest = { qualityExpanded = false }
                        ) {
                            PlaybackQuality.values().forEach { quality ->
                                DropdownMenuItem(
                                    text = { Text(playbackQualityLabel(quality)) },
                                    onClick = {
                                        qualityExpanded = false
                                        onQualitySelected(quality)
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        TextButton(
                            onClick = { subtitlesExpanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("subtitle_preference_picker")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Closed captions")
                                Text(
                                    subtitlePreferenceLabel(playbackPreferences.subtitles),
                                    color = YouTubeRed,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = subtitlesExpanded,
                            onDismissRequest = { subtitlesExpanded = false }
                        ) {
                            SubtitlePreference.values().forEach { subtitles ->
                                DropdownMenuItem(
                                    text = { Text(subtitlePreferenceLabel(subtitles)) },
                                    onClick = {
                                        subtitlesExpanded = false
                                        onSubtitleSelected(subtitles)
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { isPlaybackPreferencesOpen = false }) {
                    Text("Done")
                }
            }
        )
    }

    if (isEditProfileOpen) {
        var nameDraft by remember(localProfileName) { mutableStateOf(localProfileName) }
        var avatarDraft by remember(localProfileAvatar) { mutableStateOf(localProfileAvatar) }
        val context = LocalContext.current
        val profileImagePicker = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument()
        ) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            avatarDraft = uri.toString()
        }
        AlertDialog(
            onDismissRequest = { isEditProfileOpen = false },
            title = { Text("Edit local profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        label = { Text("Profile name") },
                        singleLine = true
                    )
                    if (isLocalProfileImageReference(avatarDraft)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LocalProfileAvatar(
                                value = avatarDraft,
                                imagePreset = ImagePreset.LARGE_AVATAR,
                                modifier = Modifier.size(52.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Profile photo selected",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Stored locally on this device",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = avatarDraft,
                            onValueChange = { avatarDraft = it.take(2) },
                            label = { Text("Avatar letters") },
                            singleLine = true
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { profileImagePicker.launch(arrayOf("image/*")) }) {
                            Text("Choose photo")
                        }
                        if (isLocalProfileImageReference(avatarDraft)) {
                            TextButton(onClick = {
                                avatarDraft = nameDraft.trim().take(2).ifBlank { "C" }
                            }) {
                                Text("Use initials")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onSaveProfile(nameDraft, avatarDraft)
                    isEditProfileOpen = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { isEditProfileOpen = false }) { Text("Cancel") }
            }
        )
    }

    if (isClearDataDialogOpen) {
        AlertDialog(
            onDismissRequest = { isClearDataDialogOpen = false },
            title = { Text("Clear local data?") },
            text = { Text("This removes your local profile, history, saved videos, likes, and queue from this device.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearLocalData()
                    isClearDataDialogOpen = false
                }) { Text("Clear data") }
            },
            dismissButton = {
                TextButton(onClick = { isClearDataDialogOpen = false }) { Text("Cancel") }
            }
        )
    }

    if (isRecommendationDialogOpen) {
        AlertDialog(
            onDismissRequest = { isRecommendationDialogOpen = false },
            title = { Text("Recommendation controls") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Your Home feed respects choices made from a video's menu.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "$notInterestedCount videos hidden\n$notRecommendedChannelCount channels blocked",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onClearRecommendationPreferences()
                    isRecommendationDialogOpen = false
                }) {
                    Text("Clear choices")
                }
            },
            dismissButton = {
                TextButton(onClick = { isRecommendationDialogOpen = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun RowScope.LocalStat(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WatchLaterSortMenu(
    selected: WatchLaterSort,
    onSelected: (WatchLaterSort) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(
                text = when (selected) {
                    WatchLaterSort.RECENTLY_ADDED -> "Recently added"
                    WatchLaterSort.OLDEST -> "Oldest"
                    WatchLaterSort.DURATION -> "Duration"
                },
                fontSize = 12.sp
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WatchLaterSort.values().forEach { sort ->
                DropdownMenuItem(
                    text = {
                        Text(
                            when (sort) {
                                WatchLaterSort.RECENTLY_ADDED -> "Recently added"
                                WatchLaterSort.OLDEST -> "Oldest"
                                WatchLaterSort.DURATION -> "Duration"
                            }
                        )
                    },
                    onClick = {
                        expanded = false
                        onSelected(sort)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SavedVideoRow(
    video: VideoItem,
    progressFraction: Float,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit,
    onAddToQueue: () -> Unit
) {
    var dragOffset by remember(video.id) { mutableFloatStateOf(0f) }
    val (imageRequest, onImageError) = rememberThumbnailRequestWithFallback(
        primaryUrl = video.thumbnailUrl,
        fallbackUrl = video.backdropUrl,
        preset = ImagePreset.COMPACT_THUMBNAIL
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(8.dp))
                .background(YouTubeRed.copy(alpha = 0.14f))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Remove", color = YouTubeRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { androidx.compose.ui.unit.IntOffset(dragOffset.toInt(), 0) }
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface)
                .then(
                    if (isSelected) Modifier.background(YouTubeRed.copy(alpha = 0.10f)) else Modifier
                )
                .pointerInput(video.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            dragOffset = (dragOffset + amount).coerceIn(-220f, 0f)
                        },
                        onDragEnd = {
                            if (dragOffset < -110f) onRemove()
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f }
                    )
                }
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FittedMediaThumbnail(
                thumbnailUrl = video.thumbnailUrl,
                backdropUrl = video.backdropUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .width(132.dp)
                    .aspectRatio(16f / 9f),
                imagePreset = ImagePreset.COMPACT_THUMBNAIL,
                isWatched = false,
                shape = RoundedCornerShape(6.dp)
            ) {
                if (progressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.Black.copy(alpha = 0.5f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction)
                                .height(3.dp)
                                .background(YouTubeRed)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 19.sp,
                    letterSpacing = (-0.1).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.channelName,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${video.duration} • Saved locally",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onAddToQueue) {
                Icon(Icons.Default.QueueMusic, contentDescription = "Add to queue")
            }
        }
    }
}

private fun playbackQualityLabel(quality: PlaybackQuality): String = when (quality) {
    PlaybackQuality.AUTO -> "Auto"
    PlaybackQuality.P1080 -> "1080p"
    PlaybackQuality.P720 -> "720p"
    PlaybackQuality.P480 -> "480p"
    PlaybackQuality.P360 -> "360p"
}

private fun subtitlePreferenceLabel(preference: SubtitlePreference): String = when (preference) {
    SubtitlePreference.OFF -> "Off"
    SubtitlePreference.AUTO -> "Auto"
    SubtitlePreference.ENGLISH -> "English"
    SubtitlePreference.SPANISH -> "Spanish"
}

private fun durationSortValue(duration: String): Int {
    val parts = duration.split(":").mapNotNull { it.toIntOrNull() }
    return when (parts.size) {
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        2 -> parts[0] * 60 + parts[1]
        1 -> parts[0]
        else -> Int.MAX_VALUE
    }
}

@Composable
private fun YouQuickChip(
    icon: ImageVector,
    label: String,
    isHighlighted: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .clip(QuickChipShape)
            .background(if (isHighlighted) YouTubeRed.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
            .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isHighlighted) YouTubeRed else MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isHighlighted) YouTubeRed else MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun HistoryCard(
    entry: WatchHistoryEntry,
    onClick: () -> Unit
) {
    val video = entry.video
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
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
            shape = HistoryCardShape
        ) {
            // Progress bar
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.Black.copy(alpha = 0.5f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(entry.progressFraction)
                        .height(3.dp)
                        .background(YouTubeRed)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = video.title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 17.sp
        )

        Text(
            text = video.channelName,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (entry.durationSeconds > 0L) {
            Text(
                text = "${formatPlaybackTime(entry.remainingSeconds)} left",
                fontSize = 10.sp,
                color = YouTubeRed,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun YouMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(onClick?.let { Modifier.clickable(onClick = it) } ?: Modifier)
            .then(testTag?.let { Modifier.testTag(it) } ?: Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailingContent?.invoke()
    }
}

@Composable
private fun DownloadedMediaPreviewCard(
    title: String,
    subtitle: String,
    badgeLabel: String,
    quality: String,
    serverName: String?,
    sizeLabel: String,
    thumbnailUrl: String?,
    posterUrl: String?,
    backdropUrl: String?,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(HistoryCardShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val displayImage = thumbnailUrl ?: posterUrl ?: backdropUrl
            if (!displayImage.isNullOrBlank()) {
                AsyncImage(
                    model = rememberOptimizedImageRequest(
                        data = displayImage,
                        preset = ImagePreset.COMPACT_THUMBNAIL
                    ),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (badgeLabel == "TV SHOW") Icons.Default.Tv else Icons.Default.Movie,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            // Media type badge top-left
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
            ) {
                Text(
                    text = badgeLabel,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // Quality & Server badge top-right
            Surface(
                color = YouTubeRed.copy(alpha = 0.9f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
            ) {
                Text(
                    text = quality,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            // Play overlay button center
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play offline",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Size badge bottom-right
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            ) {
                Text(
                    text = sizeLabel,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(Locale.US, "%.0f MB", mb)
        else -> String.format(Locale.US, "%.0f KB", kb)
    }
}

