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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
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
import androidx.compose.material.icons.filled.ClosedCaption
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.example.data.subtitles.hasUsableSubtitles
import com.example.data.subtitles.subtitleBadgeLabel
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
    onDownloadSubtitles: (String) -> Unit = {},
    onSubtitleOffsetChanged: (String, Long) -> Unit = { _, _ -> },
    onSubtitleTrackChanged: (String, String?) -> Unit = { _, _ -> },
    onRetryAllFailed: () -> Unit = {},
    onClearFailed: () -> Unit = {},
    onDownloadAllSubtitles: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var playingDownload by remember { mutableStateOf<DownloadEntity?>(null) }
    // Refresh the player snapshot when the underlying row changes (subtitle
    // sidecar arriving mid-playback, offset persisted), so a reopen is never
    // required to pick up fresh tracks.
    androidx.compose.runtime.LaunchedEffect(downloads, playingDownload?.id) {
        val id = playingDownload?.id ?: return@LaunchedEffect
        downloads.firstOrNull { it.id == id }?.let { playingDownload = it }
    }
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showClearFailedDialog by remember { mutableStateOf(false) }
    // Tabs: 0 = Downloaded, 1 = Downloading, 2 = Failed. Survives rotation.
    var selectedTab by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    // Downloaded-tab library controls (search + sort + section filter).
    var searchQuery by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
    var sortOption by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(DownloadSort.RECENT) }
    var librarySection by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(LibrarySection.ALL) }
    val expandedSeriesMap = remember { mutableStateMapOf<String, Boolean>() }
    val expandedSeasonMap = remember { mutableStateMapOf<String, Boolean>() } // key: "$seriesTitle-S$seasonNumber"

    val activeItems = remember(downloads) {
        downloads.filter {
            it.status == DownloadStatus.QUEUED.name ||
                it.status == DownloadStatus.DOWNLOADING.name ||
                it.status == DownloadStatus.PAUSED.name
        }
    }

    val failedItems = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.FAILED.name }
    }

    val downloadingNow = remember(activeItems) {
        activeItems.filter { it.status == DownloadStatus.DOWNLOADING.name }
    }
    val waitingItems = remember(activeItems) {
        activeItems.filter { it.status != DownloadStatus.DOWNLOADING.name }
    }

    val completedItems = remember(downloads) {
        downloads.filter { it.status == DownloadStatus.COMPLETED.name }
    }

    // Auto-jump to the Downloading tab when a fresh queue appears and the
    // user is sitting on an empty library (e.g. right after queuing their
    // first download).
    androidx.compose.runtime.LaunchedEffect(activeItems.isNotEmpty(), completedItems.isEmpty(), failedItems.isEmpty()) {
        if (activeItems.isNotEmpty() && completedItems.isEmpty() && failedItems.isEmpty()) {
            selectedTab = 1
        }
    }

    // Library search + sort. Search matches titles, series and episode names.
    val filteredCompleted = remember(completedItems, searchQuery, sortOption) {
        val q = searchQuery.trim().lowercase()
        val matching = if (q.isBlank()) completedItems else completedItems.filter {
            it.title.lowercase().contains(q) ||
                (it.seriesTitle?.lowercase()?.contains(q) == true) ||
                (it.episodeTitle?.lowercase()?.contains(q) == true)
        }
        when (sortOption) {
            DownloadSort.NAME -> matching.sortedBy { (it.seriesTitle ?: it.title).lowercase() }
            DownloadSort.SIZE -> matching.sortedByDescending {
                it.totalBytes.takeIf { b -> b > 0 } ?: File(it.localFilePath).length()
            }
            DownloadSort.RECENT -> matching.sortedByDescending { it.completedAtMillis ?: it.createdAtMillis }
        }
    }

    val completedMovies = remember(filteredCompleted) {
        filteredCompleted.filter { it.mediaType == MediaType.MOVIE.name }
    }

    val moviesBytes = remember(completedItems) {
        completedItems.filter { it.mediaType == MediaType.MOVIE.name }
            .sumOf { it.totalBytes.takeIf { b -> b > 0 } ?: File(it.localFilePath).length() }
    }
    val seriesBytes = remember(completedItems) {
        completedItems.filter { it.mediaType == MediaType.TV_SHOW.name }
            .sumOf { it.totalBytes.takeIf { b -> b > 0 } ?: File(it.localFilePath).length() }
    }

    // Group TV shows by content (tmdbId) so same-title remakes never merge,
    // and within each series group episodes by season number. Episodes are
    // the (season, episode) triple from the DB — the single source of truth
    // for which bytes belong to which label.
    val completedTvShowsBySeries = remember(filteredCompleted) {
        filteredCompleted.filter { it.mediaType == MediaType.TV_SHOW.name }
            .groupBy { it.tmdbId }
            .mapValues { entry ->
                entry.value.groupBy { it.seasonNumber ?: 1 }
            }
    }

    // Full screen offline player dialog — labels rebuilt from the
    // authoritative season/episode numbers so the header can never show a
    // stale title while different bytes play underneath.
    if (playingDownload != null) {
        val item = playingDownload!!
        val playerTitle = item.seriesTitle ?: item.title
        val playerSubtitle = if (item.seriesTitle != null || item.seasonNumber != null) {
            val se = "S${item.seasonNumber ?: 1}:E${item.episodeNumber ?: 1}"
            val name = item.episodeTitle?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
            "$se$name"
        } else {
            item.quality
        }
        OfflineVideoPlayer(
            title = playerTitle,
            subtitle = playerSubtitle,
            localFilePath = item.localFilePath,
            serverName = item.serverName,
            subtitleCc = item.subtitleCc,
            onClose = { playingDownload = null },
            subtitleFilePath = item.subtitleFilePath,
            initialSubtitleOffsetMs = item.subtitleOffsetMs,
            onSubtitleOffsetChanged = { onSubtitleOffsetChanged(item.id, it) },
            onRequestSubtitles = { onDownloadSubtitles(item.id) },
            subtitleLanguage = item.subtitleLanguage,
            initialTrackId = item.selectedSubtitleTrackId,
            onSubtitleTrackChanged = { onSubtitleTrackChanged(item.id, it) }
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

    if (showClearFailedDialog) {
        AlertDialog(
            onDismissRequest = { showClearFailedDialog = false },
            title = { Text("Clear failed downloads?") },
            text = { Text("This removes ${failedItems.size} failed ${if (failedItems.size == 1) "entry" else "entries"} from the list. Completed downloads are kept.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearFailedDialog = false
                        onClearFailed()
                    }
                ) {
                    Text("Clear", color = YouTubeRed)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearFailedDialog = false }) {
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Storage hero with Movies/Series breakdown (always visible).
                StorageHeroCard(
                    usedBytes = usedStorageBytes,
                    freeBytes = availableStorageBytes,
                    totalBytes = totalStorageBytes,
                    moviesBytes = moviesBytes,
                    seriesBytes = seriesBytes,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Fixed tabs: Downloaded | Downloading (badge) | Failed (badge).
                DownloadsTabRow(
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    downloadedCount = completedItems.size,
                    downloadingCount = activeItems.size,
                    failedCount = failedItems.size
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    when (selectedTab) {
                    1 -> {
                        // ---- Downloading tab ----
                        if (activeItems.isEmpty()) {
                            item(key = "downloading_empty") {
                                TabEmptyState(
                                    title = "Nothing downloading",
                                    message = "Queued movies and episodes will show up here with live progress, speed and ETA.",
                                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 48.dp)
                                )
                            }
                        } else {
                            item(key = "downloading_header") {
                                DownloadingHeader(
                                    activeCount = downloadingNow.size,
                                    waitingCount = waitingItems.size,
                                    onPauseAll = onPauseAll,
                                    onResumeAll = onResumeAll
                                )
                            }
                            if (downloadingNow.isNotEmpty()) {
                                item(key = "now_label") {
                                    QueueGroupLabel(
                                        text = "Downloading now (${downloadingNow.size})",
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                                items(downloadingNow, key = { it.id }) { download ->
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
                            }
                            if (waitingItems.isNotEmpty()) {
                                item(key = "waiting_label") {
                                    QueueGroupLabel(
                                        text = "Waiting (${waitingItems.size})",
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                    )
                                }
                                items(waitingItems, key = { it.id }) { download ->
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
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                    2 -> {
                        // ---- Failed tab ----
                        if (failedItems.isEmpty()) {
                            item(key = "failed_empty") {
                                TabEmptyState(
                                    title = "No failures",
                                    message = "Downloads that hit an error will land here with a retry button.",
                                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 48.dp)
                                )
                            }
                        } else {
                            item(key = "failed_header") {
                                FailedHeader(
                                    failedCount = failedItems.size,
                                    onRetryAll = onRetryAllFailed,
                                    onClear = { showClearFailedDialog = true }
                                )
                            }
                            items(failedItems, key = { it.id }) { download ->
                                QueuedDownloadCard(
                                    download = download,
                                    speedBytesPerSec = 0L,
                                    onPause = { onPauseDownload(download.id) },
                                    onResume = { onResumeDownload(download.id) },
                                    onRetry = { onRetryDownload(download.id) },
                                    onCancel = { onCancelDownload(download.id) },
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                )
                            }
                            item { Spacer(modifier = Modifier.height(16.dp)) }
                        }
                    }
                    else -> {
                        // ---- Downloaded library tab ----
                        item(key = "library_controls") {
                            LibraryControls(
                                searchQuery = searchQuery,
                                onSearchChange = { searchQuery = it },
                                sortOption = sortOption,
                                onSortChange = { sortOption = it },
                                section = librarySection,
                                onSectionChange = { librarySection = it },
                                movieCount = completedMovies.size,
                                episodeCount = completedTvShowsBySeries.values.sumOf { seasons -> seasons.values.sumOf { it.size } },
                                onDownloadAllSubtitles = onDownloadAllSubtitles,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }

                        val showSeries = librarySection != LibrarySection.MOVIES && completedTvShowsBySeries.isNotEmpty()
                        val showMovies = librarySection != LibrarySection.SERIES && completedMovies.isNotEmpty()
                        if (!showSeries && !showMovies) {
                            item(key = "library_empty") {
                                TabEmptyState(
                                    title = if (searchQuery.isBlank()) "No downloads yet" else "No matches for \"${searchQuery.trim()}\"",
                                    message = if (searchQuery.isBlank()) "Movies and episodes you download will live here, grouped and searchable." else "Try a different title, series or episode name.",
                                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 48.dp)
                                )
                            }
                        }

                // Downloaded TV Shows (Grouped by Shows -> Seasons -> Episodes)
                if (showSeries) {
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

                    completedTvShowsBySeries.forEach { (seriesKey, seasonsMap) ->
                        val allEpisodes = seasonsMap.values.flatten()
                        val seriesTitle = allEpisodes.firstOrNull()?.seriesTitle
                            ?: allEpisodes.firstOrNull()?.title ?: seriesKey
                        val isSeriesExpanded = expandedSeriesMap[seriesKey] ?: true
                        val totalSeriesBytes = allEpisodes.sumOf { it.totalBytes.takeIf { s -> s > 0 } ?: File(it.localFilePath).length() }
                        val firstEp = allEpisodes.minByOrNull { (it.seasonNumber ?: 1) * 1000 + (it.episodeNumber ?: 1) }

                        item(key = "series_group_$seriesKey") {
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
                                                expandedSeriesMap[seriesKey] = !isSeriesExpanded
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
                                                val seasonKey = "$seriesKey-S$seasonNumber"
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
                                                                         onDelete = { onDeleteDownload(episode.id) },
                                                                         onDownloadSubtitles = { onDownloadSubtitles(episode.id) }
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

                // Downloaded Movies Section
                if (showMovies) {
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
                            onDownloadSubtitles = { onDownloadSubtitles(movie.id) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    item { Spacer(modifier = Modifier.height(16.dp)) }
                }
                // Torrents are no longer a duplicate section: torrent rows live
                // in the Downloading tab / library above with a TORRENT badge.
                    } // end downloaded-library branch
                } // end tab switch
                } // end list
            }
        }
    }
}

/**
 * Library sort orders for the Downloaded tab.
 */
enum class DownloadSort { RECENT, NAME, SIZE }

/**
 * Section filter for the Downloaded tab.
 */
enum class LibrarySection { ALL, MOVIES, SERIES }

/**
 * Storage hero card at the top of Downloads: total usage bar plus a
 * Movies / TV Series breakdown so users see where space went.
 */
@Composable
private fun StorageHeroCard(
    usedBytes: Long,
    freeBytes: Long,
    totalBytes: Long,
    moviesBytes: Long,
    seriesBytes: Long,
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

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StorageLegendDot(color = YouTubeRed)
                Text(
                    text = "Movies ${formatBytes(moviesBytes)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                StorageLegendDot(color = Color(0xFF1E88E5))
                Text(
                    text = "Series ${formatBytes(seriesBytes)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val other = (usedBytes - moviesBytes - seriesBytes).coerceAtLeast(0L)
                if (other > 0) {
                    StorageLegendDot(color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = "Other ${formatBytes(other)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageLegendDot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

/**
 * Sticky tab row: Downloaded | Downloading (badge) | Failed (badge, red dot).
 */
@Composable
private fun DownloadsTabRow(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    downloadedCount: Int,
    downloadingCount: Int,
    failedCount: Int,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(
        "Downloaded" to downloadedCount,
        "Downloading" to downloadingCount,
        "Failed" to failedCount
    )
    TabRow(
        selectedTabIndex = selectedTab,
        modifier = modifier
            .fillMaxWidth()
            .testTag("downloads_tabs"),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        indicator = { positions ->
            TabRowDefaults.SecondaryIndicator(
                modifier = Modifier.tabIndicatorOffset(positions[selectedTab]),
                color = YouTubeRed
            )
        }
    ) {
        tabs.forEachIndexed { index, (label, count) ->
            Tab(
                selected = selectedTab == index,
                onClick = { onSelectTab(index) },
                modifier = Modifier.testTag("downloads_tab_$label"),
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium
                        )
                        if (count > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            index == 2 -> YouTubeRed
                                            selectedTab == index -> YouTubeRed
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    )
                                    .padding(horizontal = 6.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "$count",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedTab == index || index == 2) Color.White
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            )
        }
    }
}

/**
 * Search + section filter + sort controls for the Downloaded library tab.
 */
@Composable
private fun LibraryControls(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    sortOption: DownloadSort,
    onSortChange: (DownloadSort) -> Unit,
    section: LibrarySection,
    onSectionChange: (LibrarySection) -> Unit,
    movieCount: Int,
    episodeCount: Int,
    onDownloadAllSubtitles: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("downloads_search"),
            placeholder = { Text("Search downloads", fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(),
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LibrarySection.values().forEach { option ->
                    val isSelected = section == option
                    val count = when (option) {
                        LibrarySection.MOVIES -> movieCount
                        LibrarySection.SERIES -> episodeCount
                        LibrarySection.ALL -> movieCount + episodeCount
                    }
                    val label = when (option) {
                        LibrarySection.MOVIES -> "Movies"
                        LibrarySection.SERIES -> "Series"
                        LibrarySection.ALL -> "All"
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSectionChange(option) },
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

            Box {
                TextButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier.testTag("downloads_sort_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Sort,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = when (sortOption) {
                            DownloadSort.NAME -> "A–Z"
                            DownloadSort.SIZE -> "Largest"
                            DownloadSort.RECENT -> "Recent"
                        },
                        fontSize = 12.sp
                    )
                }
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Most recent") },
                        onClick = { showSortMenu = false; onSortChange(DownloadSort.RECENT) }
                    )
                    DropdownMenuItem(
                        text = { Text("Name (A–Z)") },
                        onClick = { showSortMenu = false; onSortChange(DownloadSort.NAME) }
                    )
                    DropdownMenuItem(
                        text = { Text("Largest first") },
                        onClick = { showSortMenu = false; onSortChange(DownloadSort.SIZE) }
                    )
                }
            }
        }

        TextButton(
            onClick = onDownloadAllSubtitles,
            modifier = Modifier
                .align(Alignment.Start)
                .testTag("downloads_fetch_all_subs_btn")
        ) {
            Text("Get missing subtitles", fontSize = 12.sp)
        }
    }
}

/**
 * Header for the Downloading tab with pause/resume-all actions.
 */
@Composable
private fun DownloadingHeader(
    activeCount: Int,
    waitingCount: Int,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = if (activeCount > 0) "Downloading ($activeCount) · Waiting ($waitingCount)"
            else "Waiting ($waitingCount)",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Row {
            TextButton(onClick = onPauseAll) { Text("Pause all", fontSize = 12.sp) }
            TextButton(onClick = onResumeAll) { Text("Resume", fontSize = 12.sp) }
        }
    }
}

@Composable
private fun QueueGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

/**
 * Header for the Failed tab with retry-all and clear actions.
 */
@Composable
private fun FailedHeader(
    failedCount: Int,
    onRetryAll: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$failedCount failed",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = YouTubeRed
        )
        Row {
            TextButton(onClick = onRetryAll) { Text("Retry all", fontSize = 12.sp) }
            TextButton(onClick = onClear) { Text("Clear", fontSize = 12.sp, color = YouTubeRed) }
        }
    }
}

/**
 * Per-tab empty state (queue / failed / filtered library).
 */
@Composable
private fun TabEmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 19.sp
        )
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
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
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
    modifier: Modifier = Modifier,
    onDownloadSubtitles: () -> Unit = {}
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

                    if (download.isTorrent) {
                        Surface(
                            color = Color(0xFF1E88E5).copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = if (download.seeders > 0) "TORRENT ▲${download.seeders}" else "TORRENT",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E88E5),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }

                    // Real CC badge: only when an embedded track or downloaded
                    // sidecar actually exists (never the legacy label string).
                    val movieCcLabel = subtitleBadgeLabel(download)
                    if (movieCcLabel != null) {
                        Surface(
                            color = YouTubeRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = movieCcLabel,
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

            // Fetch subtitles when none are usable yet.
            if (!hasUsableSubtitles(download)) {
                IconButton(onClick = onDownloadSubtitles) {
                    Icon(
                        imageVector = Icons.Default.ClosedCaption,
                        contentDescription = "Download subtitles",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
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
    modifier: Modifier = Modifier,
    onDownloadSubtitles: () -> Unit = {}
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
        // Episode rows keep the episode still (not the series poster).
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
            // Authoritative S/E from the DB triple; legacy rows stored
            // "1080p BluRay" in episodeTitle — hide those so the row can
            // never claim an episode name it doesn't actually have.
            val cleanEpisodeName = episode.episodeTitle?.takeIf {
                it.isNotBlank() &&
                    !it.contains("1080p", ignoreCase = true) &&
                    !it.contains("720p", ignoreCase = true) &&
                    !it.contains("480p", ignoreCase = true) &&
                    !it.contains("360p", ignoreCase = true) &&
                    !it.contains("2160", ignoreCase = true) &&
                    !it.contains("BluRay", ignoreCase = true) &&
                    !it.contains("WEB-DL", ignoreCase = true)
            } ?: "Episode ${episode.episodeNumber ?: 1}"
            Text(
                text = "S${episode.seasonNumber ?: 1}:E${episode.episodeNumber ?: 1} $cleanEpisodeName",
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
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

                val episodeCcLabel = subtitleBadgeLabel(episode)
                if (episodeCcLabel != null) {
                    Surface(
                        color = YouTubeRed.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = episodeCcLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = YouTubeRed,
                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                        )
                    }
                }
            }
        }

        if (!hasUsableSubtitles(episode)) {
            IconButton(onClick = onDownloadSubtitles, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.ClosedCaption,
                    contentDescription = "Download subtitles",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
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
