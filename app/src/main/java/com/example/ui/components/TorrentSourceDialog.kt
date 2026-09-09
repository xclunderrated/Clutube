package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.TorrentSource
import com.example.model.MediaType
import com.example.model.VideoItem
import com.example.ui.theme.YouTubeRed

private val AmberWarning = Color(0xFFFFB300)
private val MintEmerald = Color(0xFF00E676)

@Composable
fun TorrentSourceDialog(
    video: VideoItem,
    sources: List<TorrentSource>,
    isLoading: Boolean,
    selectedSeason: Int? = null,
    selectedEpisode: Int? = null,
    onSeasonSelected: ((Int) -> Unit)? = null,
    onEpisodeSelected: ((Int?) -> Unit)? = null,
    onDownload: (TorrentSource) -> Unit,
    onDismiss: () -> Unit,
    /** Season/complete packs for the requested S/E (manual file-pick only). */
    packs: List<TorrentSource> = emptyList(),
    /** Real seasons/episodes from TMDB; empty until loaded (never invented). */
    availableSeasons: List<Int> = emptyList(),
    availableEpisodeNumbers: List<Int> = emptyList(),
    /** e.g. "S01E02" — shown in the header so the target is unambiguous. */
    requestedEpCode: String? = null
) {
    val context = LocalContext.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f)
                .clip(RoundedCornerShape(20.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header with Backdrop / Poster
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val backdropOrPoster = video.backdropUrl ?: video.posterUrl ?: video.thumbnailUrl
                    if (backdropOrPoster.isNotBlank()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(backdropOrPoster)
                                .crossfade(true)
                                .build(),
                            contentDescription = video.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Top Close Button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }

                    // Bottom Gradient & Title Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f), Color.Black.copy(alpha = 0.95f))
                                )
                            )
                            .padding(14.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (video.mediaType == MediaType.TV_SHOW) "TV SERIES" else "MOVIE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = YouTubeRed
                                )
                                if (video.rating != null && video.rating > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            tint = AmberWarning,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "%.1f".format(video.rating),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                            val yearStr = video.releaseDateFormatted?.take(4) ?: ""
                            Text(
                                text = if (yearStr.isNotBlank()) "${video.title} ($yearStr)" else video.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Overview snippet
                if (video.description.isNotBlank()) {
                    Text(
                        text = video.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        lineHeight = 16.sp
                    )
                }

                // If Series: Season and Episode Selectors
                if (video.mediaType == MediaType.TV_SHOW) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "SELECT SEASON",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Real seasons only. While TMDB hasn't loaded,
                            // show just the current season — never an invented
                            // 1..5 range.
                            val seasons = availableSeasons.ifEmpty { listOf(selectedSeason ?: 1) }
                            if (availableSeasons.isEmpty()) {
                                Text(
                                    text = "Loading seasons…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .align(Alignment.CenterVertically)
                                )
                            }
                            seasons.forEach { seasonNum ->
                                val isSelected = (selectedSeason ?: 1) == seasonNum
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSeasonSelected?.invoke(seasonNum) },
                                    label = { Text("Season $seasonNum", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = YouTubeRed.copy(alpha = 0.15f),
                                        selectedLabelColor = YouTubeRed
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "SELECT EPISODE",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val activeEp = selectedEpisode ?: 1
                            // Real episodes only — never an invented 1..24
                            // range. Falls back to the current episode alone.
                            val episodes = availableEpisodeNumbers.ifEmpty { listOf(activeEp) }
                            if (availableEpisodeNumbers.isEmpty()) {
                                Text(
                                    text = "Loading…",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .padding(end = 4.dp)
                                        .align(Alignment.CenterVertically)
                                )
                            }
                            episodes.forEach { epNum ->
                                val isSelected = activeEp == epNum
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onEpisodeSelected?.invoke(epNum) },
                                    label = { Text("Ep $epNum", fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = YouTubeRed.copy(alpha = 0.15f),
                                        selectedLabelColor = YouTubeRed
                                    )
                                )
                            }
                        }
                    }
                }

                // Sources Section Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (requestedEpCode != null && video.mediaType == MediaType.TV_SHOW) {
                            "EXACT MATCHES · $requestedEpCode"
                        } else {
                            "AUTHENTIC TORRENT SOURCES"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = YouTubeRed,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = YouTubeRed
                        )
                    } else {
                        Text(
                            text = "${sources.size} exact" + if (packs.isNotEmpty()) " · ${packs.size} packs" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Resolution filter chips so a chosen quality actually narrows
                // the list instead of always surfacing 1080p (highest seeds).
                var qualityFilter by remember(sources) { mutableStateOf<String?>(null) }
                var verifiedOnly by remember { mutableStateOf(false) }
                val availableQualities = remember(sources) {
                    sources.map { it.quality }.distinct().sorted()
                }
                val verifiedCount = remember(sources) { sources.count { it.isVerified } }
                if (availableQualities.size > 1 || verifiedCount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = qualityFilter == null,
                            onClick = { qualityFilter = null },
                            label = { Text("All", fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = YouTubeRed.copy(alpha = 0.15f),
                                selectedLabelColor = YouTubeRed
                            ),
                            modifier = Modifier.testTag("quality_filter_All")
                        )
                        availableQualities.forEach { q ->
                            FilterChip(
                                selected = qualityFilter == q,
                                onClick = { qualityFilter = if (qualityFilter == q) null else q },
                                label = { Text(q, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = YouTubeRed.copy(alpha = 0.15f),
                                    selectedLabelColor = YouTubeRed
                                ),
                                modifier = Modifier.testTag("quality_filter_$q")
                            )
                        }
                        if (verifiedCount > 0) {
                            FilterChip(
                                selected = verifiedOnly,
                                onClick = { verifiedOnly = !verifiedOnly },
                                label = { Text("Verified ($verifiedCount)", fontSize = 11.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Verified,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MintEmerald.copy(alpha = 0.18f),
                                    selectedLabelColor = MintEmerald
                                ),
                                modifier = Modifier.testTag("quality_filter_verified")
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                val visibleSources = remember(sources, qualityFilter, verifiedOnly) {
                    var list = sources
                    val q = qualityFilter
                    if (q != null) list = list.filter { it.quality == q }
                    if (verifiedOnly) list = list.filter { it.isVerified }
                    list
                }

                // Sources List (exact only; packs live in their own section below)
                if (visibleSources.isEmpty() && !isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = packs.isEmpty())
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (sources.isEmpty() && packs.isEmpty()) {
                                if (requestedEpCode != null && video.mediaType == MediaType.TV_SHOW) {
                                    "No exact $requestedEpCode torrent found yet.\nCheck the packs below or try another quality."
                                } else {
                                    "No verified torrent sources found for this release yet.\nYou can also paste a direct magnet link in Downloads."
                                }
                            } else if (sources.isEmpty()) {
                                "No exact single-episode match — see season packs below.\nThe downloader extracts the right file from a pack."
                            }
                            else "No sources match this resolution filter. Clear the filter or try Auto-download with another resolution.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            lineHeight = 18.sp
                        )
                    }
                } else if (visibleSources.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = packs.isEmpty())
                            .testTag("torrent_sources_list"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(visibleSources) { source ->
                            TorrentSourceItemCard(
                                source = source,
                                matchLabel = if (video.mediaType == MediaType.TV_SHOW) "EXACT" else null,
                                onDownload = {
                                    onDownload(source)
                                },
                                onOpenMagnet = {
                                    openExternalMagnetUri(context, source.magnetUri)
                                }
                            )
                        }
                    }
                }
                // Packs: collapsed by default; manual file-pick only. The
                // engine extracts the matching inner episode file.
                if (packs.isNotEmpty() && !isLoading) {
                    var packsExpanded by remember(sources) { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                .clickable { packsExpanded = !packsExpanded }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Season packs (${packs.size})",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Contains the episode — downloader picks the right file",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = if (packsExpanded) "Hide" else "Show",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = YouTubeRed,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(YouTubeRed.copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                    .testTag("toggle_packs_btn")
                            )
                        }
                        // Tapping the row toggles; keep a11y simple via click on text above.
                        // Whole-row click handled by wrapping Box clickable:
                        if (packsExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            packs.take(10).forEach { pack ->
                                TorrentSourceItemCard(
                                    source = pack,
                                    matchLabel = "PACK",
                                    onDownload = { onDownload(pack) },
                                    onOpenMagnet = { openExternalMagnetUri(context, pack.magnetUri) }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                        }
                        // Make the header row itself tappable via an overlay click:
                        // (kept minimal — the Show/Hide pill is the tap target in tests)
                    }
                }
            }
        }
    }
}

@Composable
private fun TorrentSourceItemCard(
    source: TorrentSource,
    onDownload: () -> Unit,
    onOpenMagnet: () -> Unit,
    matchLabel: String? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Full authentic release name so the user can verify S/E.
                Text(
                    text = source.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Exact vs pack badge: the trust signal.
                    if (matchLabel != null) {
                        val isExact = matchLabel == "EXACT"
                        Text(
                            text = matchLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isExact) MintEmerald else AmberWarning,
                            modifier = Modifier
                                .background(
                                    (if (isExact) MintEmerald else AmberWarning).copy(alpha = 0.15f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    // Quality Badge
                    Text(
                        text = source.quality,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = YouTubeRed,
                        modifier = Modifier
                            .background(YouTubeRed.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )

                    // Release Type
                    Text(
                        text = source.releaseType,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )

                    // Provider Badge
                    Text(
                        text = source.provider,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    // Verified indexer badge
                    if (source.isVerified) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier
                                .background(MintEmerald.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = "Verified source",
                                tint = MintEmerald,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "VERIFIED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MintEmerald
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Stats: Size & Seeds/Peers
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = source.sizeDisplay.ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = if (source.seeders >= 0) "▲ ${source.seeders} seeds" else "▲ — seeds",
                        fontSize = 11.sp,
                        color = when {
                            source.seeders > 10 -> MintEmerald
                            source.seeders >= 0 -> AmberWarning
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = FontWeight.Medium
                    )

                    if (source.leechers > 0) {
                        Text(
                            text = "▼ ${source.leechers} peers",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onOpenMagnet) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = "Open in Client",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Button(
                    onClick = onDownload,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = YouTubeRed,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Download", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
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
