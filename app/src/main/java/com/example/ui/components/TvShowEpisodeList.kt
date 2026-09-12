package com.example.ui.components

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.tmdb.TmdbEpisodeItem
import com.example.model.WatchHistoryEntry
import com.example.model.formatPlaybackTime
import com.example.model.isUnreleased
import com.example.ui.theme.YTSuccess
import com.example.ui.theme.YouTubeRed
import com.example.util.ImagePreset
import com.example.util.rememberOptimizedImageRequest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val EpisodeCardShape = RoundedCornerShape(12.dp)
private val SeasonChipShape = RoundedCornerShape(9.dp)
private val BadgeShape = RoundedCornerShape(5.dp)

/** "2020-01-01" -> "Jan 1, 2020". Null when blank; raw fallback when unparseable. */
private fun formatEpisodeAirDate(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return null
    return runCatching {
        LocalDate.parse(value.take(10), DateTimeFormatter.ISO_LOCAL_DATE)
            .format(DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US))
    }.getOrNull() ?: value.take(10)
}

/** TMDB 0-10 vote -> "★ 7/10". Null when missing/zero, matching card style. */
private fun formatEpisodeRating(voteAverage: Double?): String? {
    if (voteAverage == null || voteAverage <= 0.0) return null
    return "★ ${voteAverage.roundToInt().coerceIn(1, 10)}/10"
}

@Composable
fun TvShowEpisodeList(
    episodes: List<TmdbEpisodeItem>,
    totalSeasons: Int,
    selectedSeason: Int,
    currentEpisodeNumber: Int,
    fallbackThumbnailUrl: String,
    onSelectSeason: (Int) -> Unit,
    onSelectEpisode: (Int, Int) -> Unit,
    onPlayNextEpisode: () -> Unit = {},
    isAutoNextEnabled: Boolean = true,
    onToggleAutoNext: () -> Unit = {},
    isEpisodeAlertActive: (Int, Int) -> Boolean = { _, _ -> false },
    onNotifyEpisode: (TmdbEpisodeItem) -> Unit = {},
    onDownloadSeason: ((Int, List<TmdbEpisodeItem>) -> Unit)? = null,
    onDownloadEpisode: ((TmdbEpisodeItem) -> Unit)? = null,
    onQueueEpisode: ((TmdbEpisodeItem) -> Unit)? = null,
    onPlayEpisodeNext: ((TmdbEpisodeItem) -> Unit)? = null,
    onToggleEpisodeWatched: ((TmdbEpisodeItem) -> Unit)? = null,
    isEpisodeQueued: (Int, Int) -> Boolean = { _, _ -> false },
    isEpisodeDownloaded: (Int, Int) -> Boolean = { _, _ -> false },
    getEpisodeDownloadProgress: (Int, Int) -> Int? = { _, _ -> null },
    isEpisodeWatched: (Int, Int) -> Boolean = { _, _ -> false },
    /** Watch-history entry per episode, if ever watched. Null = no progress UI. */
    getEpisodeWatchProgress: (Int, Int) -> WatchHistoryEntry? = { _, _ -> null },
    watchedCountBySeason: Map<Int, Int> = emptyMap(),
    totalCountBySeason: Map<Int, Int> = emptyMap(),
    isLoading: Boolean = false,
    modifier: Modifier = Modifier,
    // Playing position's season. Defaults to the viewed season so existing
    // callers/tests keep working; Watch passes video.currentSeason so the
    // NOW PLAYING badge tracks playback, not the season tab being browsed.
    currentSeasonNumber: Int = selectedSeason
) {
    // Open by default (Netflix behaviour) — one less tap to reach episodes.
    var isExpanded by rememberSaveable { mutableStateOf(true) }
    val haptics = LocalHapticFeedback.current
    // Never invent phantom seasons from stale selectedSeason state.
    val seasonsCount = totalSeasons.coerceAtLeast(1)
    val safeSelectedSeason = selectedSeason.coerceIn(1, seasonsCount)
    // Windowing: a 20-ep season as one giant Column item stalls scroll.
    // Show 6 first, expand on demand. Reset when season changes.
    var showAllEpisodes by rememberSaveable { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(safeSelectedSeason) {
        showAllEpisodes = false
    }
    val safeCurrentSeason = currentSeasonNumber.coerceAtLeast(1)
    val seasonEpisodes = episodes
        .asSequence()
        .filter { it.seasonNumber == safeSelectedSeason }
        .sortedBy { it.episodeNumber }
        .toList()
    // Up Next only makes sense while viewing the season that's playing —
    // otherwise browsing S2 during S1E1 would suggest S2E2 as "next".
    val browsingPlayingSeason = safeSelectedSeason == safeCurrentSeason
    val nextEpisode = seasonEpisodes.firstOrNull {
        browsingPlayingSeason &&
            it.episodeNumber > currentEpisodeNumber && !isUnreleased(it.airDate)
    }
    val nextSeasonAvailable = nextEpisode == null && safeSelectedSeason < seasonsCount
    val watchedInSeason = seasonEpisodes.count { isEpisodeWatched(it.seasonNumber, it.episodeNumber) }
    // Released-only slice for the season download action so upcoming episodes
    // never burn torrent searches or land as FAILED rows.
    val releasedSeasonEpisodes = seasonEpisodes.filterNot { isUnreleased(it.airDate) }
    val downloadedInSeason =
        releasedSeasonEpisodes.count { isEpisodeDownloaded(it.seasonNumber, it.episodeNumber) }
    val allReleasedDownloaded =
        releasedSeasonEpisodes.isNotEmpty() && downloadedInSeason >= releasedSeasonEpisodes.size

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .testTag("tv_episodes_section")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (isExpanded) "Collapse episodes" else "Expand episodes",
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        isExpanded = !isExpanded
                    }
                )
                .padding(vertical = 6.dp)
                .testTag("episodes_toggle"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tv,
                    contentDescription = null,
                    tint = YouTubeRed,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Episodes",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = if (seasonEpisodes.isEmpty()) {
                            "Season $safeSelectedSeason · Episode $currentEpisodeNumber"
                        } else if (watchedInSeason > 0) {
                            "Season $safeSelectedSeason · ${seasonEpisodes.size} episodes • $watchedInSeason watched"
                        } else {
                            "Season $safeSelectedSeason · ${seasonEpisodes.size} episodes"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isAutoNextEnabled) YouTubeRed.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .clickable(
                        role = Role.Switch,
                        onClickLabel = "Toggle auto-play next episode",
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onToggleAutoNext()
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("auto_next_toggle_pill"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = null,
                    tint = if (isAutoNextEnabled) YouTubeRed else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Auto-next ${if (isAutoNextEnabled) "On" else "Off"}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isAutoNextEnabled) YouTubeRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Compact season-download trailing action (replaces the tall
            // full-width banner). State lives in the content description so
            // the header stays one line: "Download season 1 (6 of 8)".
            if (onDownloadSeason != null && seasonEpisodes.isNotEmpty()) {
                val seasonLabel = when {
                    releasedSeasonEpisodes.isEmpty() -> "No released episodes yet"
                    allReleasedDownloaded ->
                        "Season $safeSelectedSeason downloaded (${releasedSeasonEpisodes.size} of ${seasonEpisodes.size})"
                    downloadedInSeason > 0 ->
                        "Download season $safeSelectedSeason ($downloadedInSeason of ${releasedSeasonEpisodes.size} of ${seasonEpisodes.size})"

                    releasedSeasonEpisodes.size != seasonEpisodes.size ->
                        "Download season $safeSelectedSeason (${releasedSeasonEpisodes.size} of ${seasonEpisodes.size})"

                    else -> "Download season $safeSelectedSeason (${seasonEpisodes.size})"
                }
                IconButton(
                    onClick = { onDownloadSeason(safeSelectedSeason, releasedSeasonEpisodes) },
                    enabled = releasedSeasonEpisodes.isNotEmpty(),
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("download_season_btn")
                ) {
                    Icon(
                        imageVector = if (allReleasedDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                        contentDescription = seasonLabel,
                        tint = when {
                            allReleasedDownloaded -> YTSuccess
                            releasedSeasonEpisodes.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.38f
                            )

                            else -> YouTubeRed
                        },
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse episodes" else "Expand episodes",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(200)) + fadeIn(tween(200)),
            exit = shrinkVertically(animationSpec = tween(150)) + fadeOut(tween(120))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(4.dp))

                LazyRow(
                    contentPadding = PaddingValues(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("season_selector_row")
                ) {
                    items(
                        count = seasonsCount,
                        key = { index -> index + 1 },
                        contentType = { "season_chip" }
                    ) { index ->
                        val season = index + 1
                        val isSelected = season == safeSelectedSeason
                        val chipWatched = watchedCountBySeason[season]
                            ?: if (isSelected) watchedInSeason else 0
                        val chipTotal = totalCountBySeason[season]
                            ?: if (isSelected) seasonEpisodes.size else null
                        val chipText = when {
                            chipTotal != null && chipTotal > 0 && chipWatched > 0 ->
                                "S$season · $chipWatched/$chipTotal"

                            chipWatched > 0 -> "S$season · $chipWatched"
                            else -> "S$season"
                        }
                        Row(
                            modifier = Modifier
                                .clip(SeasonChipShape)
                                .background(
                                    if (isSelected) YouTubeRed
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                )
                                .clickable(
                                    role = Role.Tab,
                                    onClickLabel = "Show season $season",
                                    onClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelectSeason(season)
                                    }
                                )
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                .testTag("season_chip_$season"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                            Text(
                                text = chipText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (seasonEpisodes.isEmpty()) {
                    if (isLoading) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            repeat(4) { EpisodeItemCardSkeleton() }
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                .padding(16.dp)
                                .testTag("episodes_empty_state"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Inbox,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "No episodes available for Season $safeSelectedSeason yet.",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Windowed: first 6 compose immediately; rest on demand.
                    // A 20-ep season as one item was the Watch scroll stall.
                    val visibleEpisodes = remember(seasonEpisodes, showAllEpisodes) {
                        if (showAllEpisodes) seasonEpisodes else seasonEpisodes.take(6)
                    }
                    val hiddenCount = seasonEpisodes.size - visibleEpisodes.size
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        visibleEpisodes.forEach { episode ->
                            // Playing badge follows PLAYBACK (current season +
                            // episode), never the season tab being browsed —
                            // otherwise S1E1 playing would also light up S2E1.
                            val isCurrent = episode.episodeNumber == currentEpisodeNumber &&
                                episode.seasonNumber == safeCurrentSeason
                            val isUpcoming = isUnreleased(episode.airDate)
                            val isDownloaded = isEpisodeDownloaded(episode.seasonNumber, episode.episodeNumber)
                            val progress = getEpisodeDownloadProgress(episode.seasonNumber, episode.episodeNumber)
                            val isWatched = isEpisodeWatched(episode.seasonNumber, episode.episodeNumber)
                            val watchEntry = if (isUpcoming) {
                                null
                            } else {
                                getEpisodeWatchProgress(episode.seasonNumber, episode.episodeNumber)
                            }
                            EpisodeItemCard(
                                episode = episode,
                                thumbnailUrl = episode.stillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                                    ?: fallbackThumbnailUrl,
                                isPlaying = isCurrent,
                                isUpcoming = isUpcoming,
                                isWatched = isWatched,
                                watchEntry = watchEntry,
                                isAlertActive = isEpisodeAlertActive(episode.seasonNumber, episode.episodeNumber),
                                isDownloaded = isDownloaded,
                                downloadProgress = progress,
                                isQueued = isEpisodeQueued(episode.seasonNumber, episode.episodeNumber),
                                onQueue = if (onQueueEpisode != null && !isUpcoming) {
                                    { onQueueEpisode(episode) }
                                } else null,
                                onPlayNext = if (onPlayEpisodeNext != null && !isUpcoming) {
                                    { onPlayEpisodeNext(episode) }
                                } else null,
                                onToggleWatched = if (onToggleEpisodeWatched != null && !isUpcoming) {
                                    { onToggleEpisodeWatched(episode) }
                                } else null,
                                onDownload = if (onDownloadEpisode != null && !isUpcoming) {
                                    { onDownloadEpisode(episode) }
                                } else null,
                                onClick = {
                                    if (!isUpcoming) {
                                        onSelectEpisode(episode.seasonNumber, episode.episodeNumber)
                                    }
                                },
                                onNotify = { onNotifyEpisode(episode) }
                            )
                        }

                        // Expand affordance for long seasons (YouTube-like subtle).
                        if (hiddenCount > 0) {
                            androidx.compose.material3.TextButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showAllEpisodes = true
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("episodes_show_all")
                            ) {
                                Text(
                                    text = "Show all ${seasonEpisodes.size} episodes",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = YouTubeRed
                                )
                            }
                        }

                        if (browsingPlayingSeason && (nextEpisode != null || nextSeasonAvailable)) {
                            UpNextEpisodeCard(
                                nextEpisode = nextEpisode,
                                nextSeason = if (nextEpisode == null) safeSelectedSeason + 1 else null,
                                isAutoNextEnabled = isAutoNextEnabled,
                                onClick = onPlayNextEpisode
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeItemCard(
    episode: TmdbEpisodeItem,
    thumbnailUrl: String,
    isPlaying: Boolean,
    onClick: () -> Unit,
    isUpcoming: Boolean,
    isAlertActive: Boolean,
    onNotify: () -> Unit,
    isDownloaded: Boolean = false,
    isWatched: Boolean = false,
    watchEntry: WatchHistoryEntry? = null,
    downloadProgress: Int? = null,
    isQueued: Boolean = false,
    onQueue: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onToggleWatched: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val imageRequest = rememberOptimizedImageRequest(
        data = thumbnailUrl,
        preset = ImagePreset.EPISODE_THUMBNAIL
    )
    var menuExpanded by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val episodeMeta = listOfNotNull(
        formatEpisodeAirDate(episode.airDate),
        episode.runtime?.takeIf { it > 0 }?.let { "$it min" },
        formatEpisodeRating(episode.voteAverage)
    ).joinToString(" · ")

    Card(
        shape = EpisodeCardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isPlaying) {
                YouTubeRed.copy(alpha = 0.10f)
            } else if (isUpcoming) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            } else if (isWatched) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
            }
        ),
        // Border only on the playing row — borders on every row looked heavy.
        border = if (isPlaying) {
            androidx.compose.foundation.BorderStroke(1.dp, YouTubeRed.copy(alpha = 0.58f))
        } else {
            null
        },
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = !isUpcoming,
                role = Role.Button,
                onClickLabel = "Play ${episode.name}",
                onClick = onClick
            )
            .testTag("episode_item_${episode.seasonNumber}_${episode.episodeNumber}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(112.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = episode.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .clip(BadgeShape)
                        .background(Color.Black.copy(alpha = 0.76f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "E${episode.episodeNumber}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(28.dp)
                            .clip(RoundedCornerShape(50))
                            .background(YouTubeRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Now playing",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                } else if (isWatched && !isUpcoming) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp)
                            .clip(RoundedCornerShape(50))
                            .background(Color.Black.copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Watched",
                            tint = YTSuccess,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                if (isUpcoming) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(5.dp)
                            .clip(BadgeShape)
                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.88f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "UPCOMING",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Duration badge (bottom-end) matching feed cards — hidden
                // when runtime is unknown instead of fabricating a value.
                val runtimeBadge = episode.runtime?.takeIf { it > 0 }?.let { "${it}m" }
                if (runtimeBadge != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .clip(BadgeShape)
                            .background(Color.Black.copy(alpha = 0.85f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = runtimeBadge,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // YouTube-style watch progress: thin track flush to the
                // thumbnail bottom edge, red fill to the second-precise
                // position. Completed rows render a full bar.
                val watchFraction: Float? = when {
                    isUpcoming -> null
                    watchEntry == null -> null
                    watchEntry.durationSeconds <= 0L -> null
                    watchEntry.completed -> 1f
                    watchEntry.positionSeconds <= 0L -> null
                    else -> watchEntry.progressFraction.coerceIn(0f, 1f).takeIf { it > 0f }
                }
                if (watchFraction != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.35f))
                            .testTag("episode_progress_bar_${episode.seasonNumber}_${episode.episodeNumber}")
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth(watchFraction.coerceIn(0f, 1f))
                                .height(3.dp)
                                .background(YouTubeRed)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 2.dp)
            ) {
                if (isPlaying || isUpcoming || (isWatched && !isUpcoming)) {
                    Text(
                        text = when {
                            isPlaying -> "NOW PLAYING"
                            isUpcoming -> "UPCOMING"
                            else -> "WATCHED"
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            isPlaying -> YouTubeRed
                            isUpcoming -> MaterialTheme.colorScheme.primary
                            else -> YTSuccess
                        },
                        letterSpacing = 0.4.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = "E${episode.episodeNumber} · ${episode.name}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = if (isPlaying) YouTubeRed else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 17.sp
                )
                if (episodeMeta.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = episodeMeta,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!episode.overview.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = episode.overview,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 14.sp
                    )
                }
                // Clean second-precise progression under every watched row.
                // Partial: "12:34 / 45:00 • 32:26 left". Completed with a real
                // duration: "Watched • 45:00". Synthetic 1s/1s manual marks
                // show just "Watched". Hidden when no history / unknown
                // duration / upcoming so rows stay clean.
                val progressLine: String? = when {
                    isUpcoming -> null
                    watchEntry == null -> null
                    watchEntry.durationSeconds <= 0L -> null
                    watchEntry.completed -> {
                        if (watchEntry.durationSeconds > 1L) {
                            "Watched • ${formatPlaybackTime(watchEntry.durationSeconds)}"
                        } else {
                            "Watched"
                        }
                    }
                    watchEntry.positionSeconds <= 0L -> null
                    else -> "${formatPlaybackTime(watchEntry.positionSeconds)} / " +
                        "${formatPlaybackTime(watchEntry.durationSeconds)} • " +
                        "${formatPlaybackTime(watchEntry.remainingSeconds)} left"
                }
                if (progressLine != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = progressLine,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 14.sp,
                        modifier = Modifier.testTag(
                            "episode_progress_${episode.seasonNumber}_${episode.episodeNumber}"
                        )
                    )
                }
            }

            // Single trailing affordance (YouTube-style): the whole card plays,
            // queue/download/watched/notify live in the overflow menu so rows
            // never carry two competing icon buttons.
            Box {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuExpanded = true
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("episode_menu_${episode.seasonNumber}_${episode.episodeNumber}")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options for ${episode.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    if (isUpcoming) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isAlertActive) "Alert on" else "Notify me",
                                    fontSize = 13.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (isAlertActive) Icons.Default.NotificationsActive
                                    else Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onNotify()
                            },
                            modifier = Modifier.testTag(
                                "episode_notify_${episode.seasonNumber}_${episode.episodeNumber}"
                            )
                        )
                    } else {
                        if (onPlayNext != null) {
                            DropdownMenuItem(
                                text = { Text("Play next", fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.SkipNext,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onPlayNext()
                                },
                                modifier = Modifier.testTag(
                                    "episode_menu_play_next_${episode.seasonNumber}_${episode.episodeNumber}"
                                )
                            )
                        }
                        if (onQueue != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isQueued) "In queue" else "Add to queue",
                                        fontSize = 13.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        if (isQueued) Icons.Default.CheckCircle else Icons.Default.QueueMusic,
                                        contentDescription = null,
                                        tint = if (isQueued) YTSuccess else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onQueue()
                                },
                                modifier = Modifier.testTag(
                                    "episode_queue_${episode.seasonNumber}_${episode.episodeNumber}"
                                )
                            )
                        }
                        if (onDownload != null) {
                            val downloadLabel = when {
                                isDownloaded -> "Downloaded"
                                downloadProgress != null -> "Downloading $downloadProgress%"
                                else -> "Download"
                            }
                            DropdownMenuItem(
                                text = { Text(downloadLabel, fontSize = 13.sp) },
                                leadingIcon = {
                                    Icon(
                                        if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                                        contentDescription = null,
                                        tint = if (isDownloaded) YTSuccess else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onDownload()
                                },
                                modifier = Modifier.testTag(
                                    "episode_download_${episode.seasonNumber}_${episode.episodeNumber}"
                                )
                            )
                        }
                        if (onToggleWatched != null) {
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        if (isWatched) "Mark as unwatched" else "Mark as watched",
                                        fontSize = 13.sp
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    onToggleWatched()
                                },
                                modifier = Modifier.testTag(
                                    "episode_menu_watched_${episode.seasonNumber}_${episode.episodeNumber}"
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpNextEpisodeCard(
    nextEpisode: TmdbEpisodeItem?,
    nextSeason: Int?,
    isAutoNextEnabled: Boolean,
    onClick: () -> Unit
) {
    val label = nextEpisode?.let { "S${it.seasonNumber} · E${it.episodeNumber} · ${it.name}" }
        ?: "Season $nextSeason · Episode 1"
    Card(
        shape = EpisodeCardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("end_of_episodes_next_ep_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isAutoNextEnabled) "UP NEXT · AUTO-PLAY" else "UP NEXT",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                    color = YouTubeRed,
                    letterSpacing = 0.4.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(YouTubeRed)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 13.dp, vertical = 9.dp)
                    .testTag("next_ep_button_end"),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = "Play next episode",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Next",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}
