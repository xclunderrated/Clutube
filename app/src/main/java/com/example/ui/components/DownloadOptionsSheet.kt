package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.data.tmdb.TmdbEpisodeItem
import com.example.data.torrent.TorrentSourceRegistry
import com.example.data.torrent.VerifiedIndexer
import com.example.model.VideoItem
import com.example.ui.theme.YouTubeRed
import java.util.Locale

sealed class DownloadTarget {
    data class Movie(
        val video: VideoItem
    ) : DownloadTarget()

    data class Episode(
        val video: VideoItem,
        val episode: TmdbEpisodeItem
    ) : DownloadTarget()

    data class Season(
        val video: VideoItem,
        val seasonNumber: Int,
        val episodes: List<TmdbEpisodeItem>
    ) : DownloadTarget()
}

private data class QualityOption(
    val label: String,
    val estimatedMbPerMovie: Int,
    val estimatedMbPerEpisode: Int
)

private val QUALITY_OPTIONS = listOf(
    QualityOption("1080p Full HD", 1400, 450),
    QualityOption("720p HD", 800, 240),
    QualityOption("480p SD", 450, 130),
    QualityOption("360p Data Saver", 250, 70)
)

/**
 * Streamlined download options sheet. Selecting a quality immediately kicks off
 * a torrent search restricted to the target movie/episode/season.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadOptionsSheet(
    target: DownloadTarget,
    availableStorageBytes: Long,
    onDismiss: () -> Unit,
    onConfirmDownload: (server: String, quality: String, subtitleCc: String) -> Unit,
    onBrowseTorrentSources: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    isAutoPickBest: Boolean = true,
    onAutoPickChanged: ((Boolean) -> Unit)? = null,
    onAutoDownload: ((quality: String) -> Unit)? = null,
    torrentIndexers: List<VerifiedIndexer> = TorrentSourceRegistry.VERIFIED_INDEXERS,
    disabledTorrentIndexers: Set<String> = emptySet(),
    onToggleTorrentIndexer: ((String, Boolean) -> Unit)? = null,
    initialSubtitleLanguage: String = "en",
    isSubtitleAutoDownload: Boolean = true,
    onSubtitleAutoDownloadChanged: ((Boolean) -> Unit)? = null
) {
    var selectedQualityLabel by remember { mutableStateOf(QUALITY_OPTIONS[1].label) }
    var selectedSubtitleLanguage by remember(initialSubtitleLanguage) {
        mutableStateOf(
            com.example.data.subtitles.normalizeSubtitleLanguage(initialSubtitleLanguage)
        )
    }
    val selectedSubtitleCc = when (selectedSubtitleLanguage) {
        "es" -> "Spanish (CC)"
        "off" -> "Off"
        else -> "English (CC)"
    }

    val currentQualityInfo = QUALITY_OPTIONS.first { it.label == selectedQualityLabel }

    val itemCount = when (target) {
        is DownloadTarget.Movie -> 1
        is DownloadTarget.Episode -> 1
        is DownloadTarget.Season -> target.episodes.filter { it.seasonNumber == target.seasonNumber }.size
    }

    val estimatedBytes = when (target) {
        is DownloadTarget.Movie -> currentQualityInfo.estimatedMbPerMovie * 1024L * 1024L
        is DownloadTarget.Episode -> currentQualityInfo.estimatedMbPerEpisode * 1024L * 1024L
        is DownloadTarget.Season -> currentQualityInfo.estimatedMbPerEpisode * 1024L * 1024L * itemCount
    }

    val displayTitle = when (target) {
        is DownloadTarget.Movie -> target.video.title
        is DownloadTarget.Episode -> "${target.video.title} · S${target.episode.seasonNumber}:E${target.episode.episodeNumber}"
        is DownloadTarget.Season -> "${target.video.title} · Season ${target.seasonNumber}"
    }

    val displaySubtitle = when (target) {
        is DownloadTarget.Movie -> "${target.video.duration.ifBlank { "Movie" }} · ${target.video.releaseDateFormatted ?: ""}"
        is DownloadTarget.Episode -> target.episode.name
        is DownloadTarget.Season -> "$itemCount Episodes"
    }

    val thumbnailUrl = when (target) {
        is DownloadTarget.Movie -> target.video.posterUrl ?: target.video.thumbnailUrl
        is DownloadTarget.Episode -> target.episode.stillPath?.let { "https://image.tmdb.org/t/p/w500$it" } ?: target.video.thumbnailUrl
        is DownloadTarget.Season -> target.video.posterUrl ?: target.video.thumbnailUrl
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
        modifier = modifier.testTag("download_options_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Drag handle pill
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Header Media Preview Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FittedMediaThumbnail(
                        thumbnailUrl = thumbnailUrl,
                        backdropUrl = if (target is DownloadTarget.Movie) target.video.backdropUrl else null,
                        posterUrl = if (target is DownloadTarget.Movie) target.video.posterUrl else null,
                        contentDescription = displayTitle,
                        modifier = Modifier
                            .width(64.dp)
                            .aspectRatio(16f / 9f),
                        shape = RoundedCornerShape(6.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = displaySubtitle,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Quality Section
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Hd,
                    contentDescription = null,
                    tint = YouTubeRed,
                    modifier = Modifier.width(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Quality",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QUALITY_OPTIONS.forEach { quality ->
                    val isSelected = selectedQualityLabel == quality.label
                    val estMb = when (target) {
                        is DownloadTarget.Movie -> quality.estimatedMbPerMovie
                        is DownloadTarget.Episode -> quality.estimatedMbPerEpisode
                        is DownloadTarget.Season -> quality.estimatedMbPerEpisode * itemCount
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedQualityLabel = quality.label },
                        label = {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(
                                    text = quality.label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                )
                                Text(
                                    text = "~${formatBytes(estMb * 1024L * 1024L)}",
                                    fontSize = 10.sp,
                                    color = if (isSelected) YouTubeRed else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = YouTubeRed.copy(alpha = 0.15f),
                            selectedLabelColor = YouTubeRed
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) YouTubeRed else MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = YouTubeRed,
                            borderWidth = 1.dp,
                            enabled = true,
                            selected = isSelected
                        ),
                        modifier = Modifier.testTag("quality_chip_${quality.label}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Subtitles Section: language for the offline sidecar. Paired
            // torrent subs are kept automatically; otherwise the best match
            // is fetched after the download finishes (or on demand later).
            Text(
                text = "Subtitles",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("en" to "English", "es" to "Spanish", "off" to "Off").forEach { (code, label) ->
                    val isSelected = selectedSubtitleLanguage == code
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedSubtitleLanguage = code },
                        label = {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = YouTubeRed.copy(alpha = 0.15f),
                            selectedLabelColor = YouTubeRed
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = if (isSelected) YouTubeRed else MaterialTheme.colorScheme.outlineVariant,
                            selectedBorderColor = YouTubeRed,
                            borderWidth = 1.dp,
                            enabled = true,
                            selected = isSelected
                        ),
                        modifier = Modifier.testTag("subtitle_lang_chip_$code")
                    )
                }
            }

            if (selectedSubtitleLanguage != "off" && onSubtitleAutoDownloadChanged != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Download subtitles automatically",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Paired torrent subs first, then best online match",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isSubtitleAutoDownload,
                        onCheckedChange = { onSubtitleAutoDownloadChanged(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = YouTubeRed),
                        modifier = Modifier.testTag("subtitle_auto_download_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Storage Summary Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Est. Download: ~${formatBytes(estimatedBytes)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "Free Space: ${formatBytes(availableStorageBytes)}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Auto-download option: highest-seed torrent for the chosen resolution.
            if (onAutoDownload != null || onAutoPickChanged != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-download best torrent",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Highest seeds for $selectedQualityLabel, starts instantly",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isAutoPickBest,
                        onCheckedChange = { onAutoPickChanged?.invoke(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = YouTubeRed),
                        modifier = Modifier.testTag("auto_pick_best_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Verified torrent sources management (expandable toggles).
            if (onToggleTorrentIndexer != null && torrentIndexers.isNotEmpty()) {
                var sourcesExpanded by remember { mutableStateOf(false) }
                val enabledCount = torrentIndexers.count { it.id.lowercase() !in disabledTorrentIndexers.map(String::lowercase) }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .testTag("torrent_sources_section")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(
                                role = androidx.compose.ui.semantics.Role.Button,
                                onClickLabel = if (sourcesExpanded) "Hide torrent sources" else "Show torrent sources",
                                onClick = { sourcesExpanded = !sourcesExpanded }
                            )
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Torrent sources ($enabledCount of ${torrentIndexers.size} on)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Verified indexers searched for every download",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = if (sourcesExpanded) {
                                androidx.compose.material.icons.Icons.Default.ExpandLess
                            } else {
                                androidx.compose.material.icons.Icons.Default.ExpandMore
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (sourcesExpanded) {
                        torrentIndexers.forEach { indexer ->
                            val isOn = indexer.id.lowercase() !in disabledTorrentIndexers.map(String::lowercase)
                            // Never allow switching off the last enabled source.
                            val canToggleOff = enabledCount > 1 || !isOn
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = indexer.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = indexer.baseUrl.removePrefix("https://").removePrefix("http://"),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isOn,
                                    onCheckedChange = {
                                        if (it || canToggleOff) onToggleTorrentIndexer(indexer.id, it)
                                    },
                                    enabled = !isOn || canToggleOff,
                                    colors = SwitchDefaults.colors(checkedThumbColor = YouTubeRed),
                                    modifier = Modifier.testTag("torrent_source_toggle_${indexer.id}")
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Text("Cancel", fontSize = 14.sp)
                }

                if (isAutoPickBest && onAutoDownload != null) {
                    // Primary: one-tap auto download (resolution + highest seeds).
                    Button(
                        onClick = { onAutoDownload(selectedQualityLabel) },
                        colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("confirm_auto_download_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.width(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (itemCount > 1) "Download ($itemCount)" else "Download",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                } else if (onBrowseTorrentSources != null) {
                    // "Find Torrents" — opens a real torrent-source picker scoped to this target
                    Button(
                        onClick = onBrowseTorrentSources,
                        colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("confirm_find_torrents_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.width(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Find Torrents",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                } else {
                    Button(
                        onClick = {
                            onConfirmDownload(
                                "BitTorrent P2P",
                                currentQualityInfo.label,
                                selectedSubtitleCc
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(48.dp)
                            .testTag("confirm_start_download_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.width(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (itemCount > 1) "Download ($itemCount)" else "Download",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }

            // Secondary: manual browse when auto-pick is on.
            if (isAutoPickBest && onAutoDownload != null && onBrowseTorrentSources != null) {
                TextButton(
                    onClick = onBrowseTorrentSources,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("browse_torrents_instead_btn")
                ) {
                    Text(
                        text = "Browse all torrent sources instead",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = YouTubeRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
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
