package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadStatus
import com.example.model.MediaType
import com.example.ui.components.FittedMediaThumbnail
import com.example.ui.components.OfflineVideoPlayer
import com.example.ui.theme.YouTubeRed
import com.example.util.ImagePreset
import java.io.File
import java.util.Locale

/**
 * Dedicated, Netflix-grade Downloads screen that organizes completed downloads
 * into Movies and TV Series (grouped by Season), displays real-time queued downloads,
 * and provides full offline playback.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    downloads: List<DownloadEntity>,
    activeSpeeds: Map<String, Long>,
    usedStorageBytes: Long,
    availableStorageBytes: Long,
    totalStorageBytes: Long,
    onBack: () -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onRetryDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onClearAllDownloads: () -> Unit,
    onExploreContent: () -> Unit,
    onAddMagnet: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var playingDownload by remember { mutableStateOf<DownloadEntity?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("All") } // "All", "Movies", "TV Shows", "Queue"
    val expandedSeriesMap = remember { mutableStateMapOf<String, Boolean>() }
    val expandedSeasonMap = remember { mutableStateMapOf<String, Boolean>() } // key: "$seriesTitle-S$seasonNumber"

    val queuedItems = remember(downloads) {
        downloads.filter {
            it.status == DownloadStatus.QUEUED.name ||
                it.status == DownloadStatus.DOWNLOADING.name ||
                it.status == DownloadStatus.PAUSED.name ||
                it.status == DownloadStatus.FAILED.name
        }
    }

    val completedItems = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.COMPLETED.name }
    }

    val completedMovies = remember(completedItems) {
        completedItems.filter { it.mediaType == MediaType.MOVIE.name }
    }

    val allTorrents = remember(downloads) {
        downloads.filter { it.isTorrent }
    }

    // Group TV shows by series title, and within each series, group episodes by season number
    val completedTvShowsBySeries = remember(completedItems) {
        completedItems.filter { it.mediaType == MediaType.TV_SHOW.name }
            .groupBy { it.seriesTitle ?: it.title }
            .mapValues { entry ->
                entry.value.groupBy { it.seasonNumber ?: 1 }
            }
    }

    // Full screen offline player dialog
    if (playingDownload != null) {
        val item = playingDownload!!
        OfflineVideoPlayer(
            title = item.seriesTitle ?: item.title,
            subtitle = if (item.seriesTitle != null) item.title else item.quality,
            localFilePath = item.localFilePath,
            serverName = item.serverName,
            subtitleCc = item.subtitleCc,
            onClose = { playingDownload = null }
        )
        return
    }

    // Confirmation dialog for clearing all downloads
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete all downloads?") },
            text = { Text("This will remove all downloaded movies and episodes from your device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        onClearAllDownloads()
                    }
                ) {
                    Text("Delete All", color = YouTubeRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Downloads",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("downloads_back_btn")) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (onAddMagnet != null) {
                        IconButton(
                            onClick = onAddMagnet,
                            modifier = Modifier.testTag("downloads_add_magnet_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Magnet / Torrent",
                                tint = YouTubeRed
                            )
                        }
                    }
                    if (downloads.isNotEmpty()) {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Pause all downloads") },
                                onClick = {
                                    showMenu = false
                                    onPauseAll()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Resume all downloads") },
                                onClick = {
                                    showMenu = false
                                    onResumeAll()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete all downloads", color = YouTubeRed) },
                                onClick = {
                                    showMenu = false
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize().testTag("downloads_screen")
    ) { innerPadding ->
        if (downloads.isEmpty()) {
            EmptyDownloadsView(
                onExplore = onExploreContent,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Storage Bar
                item(key = "storage_info_bar") {
                    StorageInfoBar(
                        usedBytes = usedStorageBytes,
                        freeBytes = availableStorageBytes,
                        totalBytes = totalStorageBytes,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Category Filter Chips (All, Movies, TV Shows, Queue)
                item(key = "filter_chips_row") {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val torrentCount = downloads.count { it.isTorrent }
                        val filterOptions = mutableListOf(
                            "All" to (completedItems.size + queuedItems.size),
                            "TV Shows" to completedTvShowsBySeries.values.sumOf { seasons -> seasons.values.sumOf { it.size } },
                            "Movies" to completedMovies.size
                        )
                        if (torrentCount > 0) {
                            filterOptions.add("Torrents" to torrentCount)
                        }
                        if (queuedItems.isNotEmpty()) {
                            filterOptions.add(1, "Queue" to queuedItems.size)
                        }

                        items(filterOptions, key = { it.first }) { (label, count) ->
                            val isSelected = selectedFilter == label
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilter = label },
                                label = {
                                    Text(
                                        text = "$label ($count)",
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YouTubeRed,
                                    selectedLabelColor = Color.White,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }
                }

                // Active Queue Section (Visible in 'All' or 'Queue' filters)
                if (queuedItems.isNotEmpty() && (selectedFilter == "All" || selectedFilter == "Queue")) {
                    item(key = "queue_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Downloading & Queue",
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(YouTubeRed)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${queuedItems.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            Row {
                                TextButton(onClick = onPauseAll) {
                                    Text("Pause all", fontSize = 12.sp)
                                }
                                TextButton(onClick = onResumeAll) {
                                    Text("Resume", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    items(queuedItems, key = { it.id }) { download ->
                        QueuedDownloadCard(
                            download = download,
                            speedBytesPerSec = activeSpeeds[download.id] ?: 0L,
                            onPause = { onPauseDownload(download.id) },
                            onResume = { onResumeDownload(download.id) },
                            onRetry = { onRetryDownload(download.id) },
                            onCancel = { onCancelDownload(download.id) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                // Downloaded TV Shows (Grouped by Shows -> Seasons -> Episodes)
                if (completedTvShowsBySeries.isNotEmpty() && (selectedFilter == "All" || selectedFilter == "TV Shows")) {
                    item(key = "completed_tv_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tv,
                                contentDescription = null,
                                tint = YouTubeRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "TV Shows (${completedTvShowsBySeries.size})",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    completedTvShowsBySeries.forEach { (seriesTitle, seasonsMap) ->
                        val isSeriesExpanded = expandedSeriesMap[seriesTitle] ?: true
                        val allEpisodes = seasonsMap.values.flatten()
                        val totalSeriesBytes = allEpisodes.sumOf { it.totalBytes.takeIf { s -> s > 0 } ?: File(it.localFilePath).length() }
                        val firstEp = allEpisodes.minByOrNull { (it.seasonNumber ?: 1) * 1000 + (it.episodeNumber ?: 1) }

                        item(key = "series_group_$seriesTitle") {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                                    .animateContentSize(),
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                )
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Series Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                expandedSeriesMap[seriesTitle] = !isSeriesExpanded
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Poster thumbnail
                                        FittedMediaThumbnail(
                                            thumbnailUrl = allEpisodes.firstOrNull()?.thumbnailUrl,
                                            backdropUrl = allEpisodes.firstOrNull()?.backdropUrl,
                                            posterUrl = allEpisodes.firstOrNull()?.posterUrl,
                                            isPosterRatio = true,
                                            contentDescription = seriesTitle,
                                            modifier = Modifier
                                                .width(52.dp)
                                                .aspectRatio(2f / 3f),
                                            imagePreset = ImagePreset.POSTER_CARD,
                                            shape = RoundedCornerShape(8.dp)
                                        )

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = seriesTitle,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onBackground,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = "${seasonsMap.size} ${if (seasonsMap.size == 1) "Season" else "Seasons"} · ${allEpisodes.size} Episodes · ${formatBytes(totalSeriesBytes)}",
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        if (firstEp != null) {
                                            IconButton(
                                                onClick = { playingDownload = firstEp },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = "Play first episode",
                                                    tint = YouTubeRed,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }

                                        Icon(
                                            imageVector = if (isSeriesExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                            contentDescription = if (isSeriesExpanded) "Collapse" else "Expand",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    // Expandable Seasons within Show
                                    AnimatedVisibility(visible = isSeriesExpanded) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            seasonsMap.toSortedMap().forEach { (seasonNumber, episodesInSeason) ->
                                                val seasonKey = "$seriesTitle-S$seasonNumber"
                                                val isSeasonExpanded = expandedSeasonMap[seasonKey] ?: true
                                                val seasonBytes = episodesInSeason.sumOf { it.totalBytes.takeIf { s -> s > 0 } ?: File(it.localFilePath).length() }

                                                // Season Accordion Container
                                                Surface(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                                    shape = RoundedCornerShape(10.dp)
                                                ) {
                                                    Column(modifier = Modifier.fillMaxWidth().animateContentSize()) {
                                                        // Season Header Row
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    expandedSeasonMap[seasonKey] = !isSeasonExpanded
                                                                }
                                                                .padding(horizontal = 12.dp, vertical = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Surface(
                                                                color = YouTubeRed.copy(alpha = 0.15f),
                                                                shape = RoundedCornerShape(6.dp)
                                                            ) {
                                                                Text(
                                                                    text = "Season $seasonNumber",
                                                                    fontSize = 12.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = YouTubeRed,
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                                )
                                                            }

                                                            Spacer(modifier = Modifier.width(8.dp))

                                                            Text(
                                                                text = "${episodesInSeason.size} ${if (episodesInSeason.size == 1) "episode" else "episodes"} · ${formatBytes(seasonBytes)}",
                                                                fontSize = 11.sp,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )

                                                            Spacer(modifier = Modifier.weight(1f))

                                                            Icon(
                                                                imageVector = if (isSeasonExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                                modifier = Modifier.size(18.dp)
                                                            )
                                                        }

                                                        // Episodes within this Season
                                                        AnimatedVisibility(visible = isSeasonExpanded) {
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                                verticalArrangement = Arrangement.spacedBy(6.dp)
                                                            ) {
                                                                episodesInSeason.sortedBy { it.episodeNumber ?: 1 }.forEach { episode ->
                                                                    DownloadedEpisodeRow(
                                                                        episode = episode,
                                                                        onPlay = { playingDownload = episode },
                                                                        onDelete = { onDeleteDownload(episode.id) }
                                                                    )
                                                                }
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                // Downloaded Movies Section (Visible in 'All' or 'Movies' filters)
                if (completedMovies.isNotEmpty() && (selectedFilter == "All" || selectedFilter == "Movies")) {
                    item(key = "completed_movies_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = YouTubeRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Movies (${completedMovies.size})",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    items(completedMovies, key = { it.id }) { movie ->
                        DownloadedMovieCard(
                            download = movie,
                            onPlay = { playingDownload = movie },
                            onDelete = { onDeleteDownload(movie.id) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }

                // Dedicated Torrents Section (Visible in 'All' or 'Torrents' filters)
                if (allTorrents.isNotEmpty() && (selectedFilter == "All" || selectedFilter == "Torrents")) {
                    item(key = "torrents_header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = YouTubeRed,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Torrents & P2P Swarms (${allTorrents.size})",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    items(allTorrents, key = { "torrent_${it.id}" }) { torrent ->
                        if (torrent.status == DownloadStatus.COMPLETED.name) {
                            DownloadedMovieCard(
                                download = torrent,
                                onPlay = { playingDownload = torrent },
                                onDelete = { onDeleteDownload(torrent.id) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        } else {
                            QueuedDownloadCard(
                                download = torrent,
                                speedBytesPerSec = activeSpeeds[torrent.id] ?: 0L,
                                onPause = { onPauseDownload(torrent.id) },
                                onResume = { onResumeDownload(torrent.id) },
                                onRetry = { onRetryDownload(torrent.id) },
                                onCancel = { onCancelDownload(torrent.id) },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
            }
        }
    }
}

/**
 * Storage utilization meter bar at top of Downloads screen.
 */
@Composable
private fun StorageInfoBar(
    usedBytes: Long,
    freeBytes: Long,
    totalBytes: Long,
    modifier: Modifier = Modifier
) {
    val progress = if (totalBytes > 0) {
        (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0.01f, 1f)
    } else 0.05f

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = YouTubeRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CluTube Downloads: ${formatBytes(usedBytes)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Text(
                    text = "Free: ${formatBytes(freeBytes)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { progress },
                color = YouTubeRed,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
            )
        }
    }
}

/**
 * Live download card in active queue showing real-time progress, speed and pause/resume actions.
 */
@Composable
private fun QueuedDownloadCard(
    download: DownloadEntity,
    speedBytesPerSec: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDownloading = download.status == DownloadStatus.DOWNLOADING.name
    val isPaused = download.status == DownloadStatus.PAUSED.name
    val isFailed = download.status == DownloadStatus.FAILED.name
    val animatedProgress by animateFloatAsState(
        targetValue = download.progressPercent / 100f,
        label = "download_progress"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("queued_download_${download.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Thumbnail
                FittedMediaThumbnail(
                    thumbnailUrl = download.thumbnailUrl,
                    backdropUrl = download.backdropUrl,
                    posterUrl = download.posterUrl,
                    contentDescription = download.title,
                    modifier = Modifier
                        .width(72.dp)
                        .aspectRatio(16f / 9f),
                    imagePreset = ImagePreset.COMPACT_THUMBNAIL,
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = download.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Status and stats
                    val statusText = when {
                        isDownloading -> {
                            val base = "${download.progressPercent}% · ${formatSpeed(speedBytesPerSec)}"
                            val eta = if (download.etaSeconds > 0) " · ETA ${formatEta(download.etaSeconds)}" else ""
                            val seeds = if (download.seeders > 0) " · ▲${download.seeders}" else ""
                            "$base$eta$seeds"
                        }
                        isPaused -> "Paused · ${download.progressPercent}%"
                        isFailed -> download.errorMessage ?: "Download failed"
                        else -> {
                            val seedStr = if (download.seeders > 0) " · ▲${download.seeders} seeds" else ""
                            "Queued · ${download.quality} (${download.serverName})$seedStr"
                        }
                    }

                    val statusColor = when {
                        isFailed -> YouTubeRed
                        isPaused -> MaterialTheme.colorScheme.onSurfaceVariant
                        isDownloading -> Color(0xFF1E88E5)
                        else -> Color(0xFFFFB300)
                    }

                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        color = statusColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Action button (Pause, Resume, Retry, Cancel)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!download.magnetUri.isNullOrBlank()) {
                        val context = LocalContext.current
                        IconButton(onClick = {
                            openExternalMagnetUri(context, download.magnetUri)
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open in External App",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    if (isDownloading) {
                        IconButton(onClick = onPause) {
                            Icon(
                                imageVector = Icons.Default.Pause,
                                contentDescription = "Pause",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (isPaused) {
                        IconButton(onClick = onResume) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Resume",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    } else if (isFailed) {
                        IconButton(onClick = onRetry) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = YouTubeRed
                            )
                        }
                    }

                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                color = if (isFailed) YouTubeRed else YouTubeRed,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
    }
}

/**
 * Card for completed movie with play offline button.
 */
@Composable
private fun DownloadedMovieCard(
    download: DownloadEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileSizeBytes = download.totalBytes.takeIf { it > 0 }
        ?: File(download.localFilePath).takeIf { it.exists() }?.length()
        ?: 0L

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .testTag("downloaded_movie_${download.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster thumbnail
            FittedMediaThumbnail(
                thumbnailUrl = download.thumbnailUrl,
                backdropUrl = download.backdropUrl,
                posterUrl = download.posterUrl,
                isPosterRatio = true,
                contentDescription = download.title,
                modifier = Modifier
                    .width(60.dp)
                    .aspectRatio(2f / 3f),
                imagePreset = ImagePreset.POSTER_CARD,
                shape = RoundedCornerShape(8.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = download.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${download.quality} · ${formatBytes(fileSizeBytes)}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = download.serverName.take(10),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }

                    if (!download.subtitleCc.contains("Off", ignoreCase = true)) {
                        Surface(
                            color = YouTubeRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "CC",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = YouTubeRed,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            if (!download.magnetUri.isNullOrBlank()) {
                val context = LocalContext.current
                IconButton(onClick = { openExternalMagnetUri(context, download.magnetUri) }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open in External App",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Play button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(YouTubeRed)
                    .clickable(onClick = onPlay)
                    .testTag("play_offline_${download.id}"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play offline",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Delete button
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete download",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Row item for a completed TV show episode.
 */
@Composable
private fun DownloadedEpisodeRow(
    episode: DownloadEntity,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileSizeBytes = episode.totalBytes.takeIf { it > 0 }
        ?: File(episode.localFilePath).takeIf { it.exists() }?.length()
        ?: 0L

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
            .clickable(onClick = onPlay)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        FittedMediaThumbnail(
            thumbnailUrl = episode.thumbnailUrl,
            backdropUrl = episode.backdropUrl,
            posterUrl = episode.posterUrl,
            contentDescription = episode.title,
            modifier = Modifier
                .width(68.dp)
                .aspectRatio(16f / 9f),
            imagePreset = ImagePreset.EPISODE_THUMBNAIL,
            shape = RoundedCornerShape(6.dp)
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "S${episode.seasonNumber ?: 1}:E${episode.episodeNumber ?: 1} ${episode.episodeTitle ?: episode.title}",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${episode.duration ?: "45m"} · ${formatBytes(fileSizeBytes)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(3.dp)
                ) {
                    Text(
                        text = episode.quality.take(5),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                    )
                }

                if (!episode.subtitleCc.contains("Off", ignoreCase = true)) {
                    Surface(
                        color = YouTubeRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = "CC",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = YouTubeRed,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                        )
                    }
                }
            }
        }

        IconButton(onClick = onPlay, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play offline",
                tint = YouTubeRed,
                modifier = Modifier.size(22.dp)
            )
        }

        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete episode",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Friendly empty state for Downloads screen with call to action.
 */
@Composable
private fun EmptyDownloadsView(
    onExplore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.FileDownload,
                contentDescription = null,
                tint = YouTubeRed,
                modifier = Modifier.size(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No downloads yet",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Movies and TV shows you download will appear here so you can watch offline anytime, anywhere.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onExplore,
            colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .height(48.dp)
                .testTag("explore_downloads_btn")
        ) {
            Text(
                text = "Find Something to Download",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White
            )
        }
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

private fun formatSpeed(speedBytesPerSec: Long): String {
    if (speedBytesPerSec <= 0) return "0 KB/s"
    val kb = speedBytesPerSec / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1.0 -> String.format(Locale.US, "%.1f MB/s", mb)
        else -> String.format(Locale.US, "%.0f KB/s", kb)
    }
}

private fun formatEta(seconds: Long): String {
    if (seconds <= 0) return "--"
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}

private fun openExternalMagnetUri(context: Context, magnetUri: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(magnetUri)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Open with Torrent App"))
    } catch (e: Exception) {
        Toast.makeText(context, "No external torrent client found", Toast.LENGTH_SHORT).show()
    }
}
