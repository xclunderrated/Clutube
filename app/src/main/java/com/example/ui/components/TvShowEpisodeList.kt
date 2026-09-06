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
import androidx.compose.animation.shrinkVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.model.isUnreleased
import com.example.ui.theme.YouTubeRed
import com.example.util.ImagePreset
import com.example.util.rememberOptimizedImageRequest

private val EpisodeCardShape = RoundedCornerShape(12.dp)
private val SeasonChipShape = RoundedCornerShape(9.dp)
private val BadgeShape = RoundedCornerShape(5.dp)

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
    isEpisodeDownloaded: (Int, Int) -> Boolean = { _, _ -> false },
    getEpisodeDownloadProgress: (Int, Int) -> Int? = { _, _ -> null },
    isEpisodeWatched: (Int, Int) -> Boolean = { _, _ -> false },
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
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isExpanded) "Collapse episodes" else "Expand episodes",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )

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
                    fontWeight = FontWeight.Bold,
                    color = if (isAutoNextEnabled) YouTubeRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
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
                                text = "S$season",
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (onDownloadSeason != null && seasonEpisodes.isNotEmpty()) {
                    val allSeasonDownloaded = seasonEpisodes.all { isEpisodeDownloaded(it.seasonNumber, it.episodeNumber) }
                    val downloadedCount = seasonEpisodes.count { isEpisodeDownloaded(it.seasonNumber, it.episodeNumber) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Download season $safeSelectedSeason",
                                onClick = { onDownloadSeason(safeSelectedSeason, seasonEpisodes) }
                            )
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                            .testTag("download_season_btn"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (allSeasonDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                                contentDescription = null,
                                tint = if (allSeasonDownloaded) Color(0xFF4CAF50) else YouTubeRed,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (allSeasonDownloaded) {
                                    "Season $safeSelectedSeason downloaded (${seasonEpisodes.size})"
                                } else if (downloadedCount > 0) {
                                    "Season $safeSelectedSeason ($downloadedCount/${seasonEpisodes.size})"
                                } else {
                                    "Download season $safeSelectedSeason (${seasonEpisodes.size})"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        seasonEpisodes.forEach { episode ->
                            // Playing badge follows PLAYBACK (current season +
                            // episode), never the season tab being browsed —
                            // otherwise S1E1 playing would also light up S2E1.
                            val isCurrent = episode.episodeNumber == currentEpisodeNumber &&
                                episode.seasonNumber == safeCurrentSeason
                            val isUpcoming = isUnreleased(episode.airDate)
                            val isDownloaded = isEpisodeDownloaded(episode.seasonNumber, episode.episodeNumber)
                            val progress = getEpisodeDownloadProgress(episode.seasonNumber, episode.episodeNumber)
                            val isWatched = isEpisodeWatched(episode.seasonNumber, episode.episodeNumber)
                            EpisodeItemCard(
                                episode = episode,
                                thumbnailUrl = episode.stillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                                    ?: fallbackThumbnailUrl,
                                isPlaying = isCurrent,
                                isUpcoming = isUpcoming,
                                isWatched = isWatched,
                                isAlertActive = isEpisodeAlertActive(episode.seasonNumber, episode.episodeNumber),
                                isDownloaded = isDownloaded,
                                downloadProgress = progress,
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
    downloadProgress: Int? = null,
    onDownload: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val imageRequest = rememberOptimizedImageRequest(
        data = thumbnailUrl,
        preset = ImagePreset.EPISODE_THUMBNAIL
    )
    val episodeMeta = listOfNotNull(
        episode.airDate?.takeIf { it.isNotBlank() },
        episode.runtime?.takeIf { it > 0 }?.let { "$it min" }
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
                        fontWeight = FontWeight.Bold
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
                            tint = Color(0xFF4CAF50),
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
                            fontWeight = FontWeight.Black
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
                        fontWeight = FontWeight.Black,
                        color = when {
                            isPlaying -> YouTubeRed
                            isUpcoming -> MaterialTheme.colorScheme.primary
                            else -> Color(0xFF4CAF50)
                        },
                        letterSpacing = 0.4.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = "E${episode.episodeNumber} · ${episode.name}",
                    fontSize = 13.sp,
                    fontWeight = if (isPlaying || isUpcoming) FontWeight.Bold else FontWeight.SemiBold,
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 14.sp
                    )
                }
            }

            if (isUpcoming) {
                IconButton(
                    onClick = onNotify,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("episode_notify_${episode.seasonNumber}_${episode.episodeNumber}")
                ) {
                    Icon(
                        imageVector = if (isAlertActive) {
                            Icons.Default.NotificationsActive
                        } else {
                            Icons.Default.NotificationsNone
                        },
                        contentDescription = if (isAlertActive) {
                            "Release alert enabled"
                        } else {
                            "Notify me when released"
                        },
                        tint = if (isAlertActive) YouTubeRed else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (onDownload != null) {
                IconButton(
                    onClick = onDownload,
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("episode_download_${episode.seasonNumber}_${episode.episodeNumber}")
                ) {
                    if (isDownloaded) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(18.dp)
                        )
                    } else if (downloadProgress != null) {
                        CircularProgressIndicator(
                            progress = { downloadProgress / 100f },
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = YouTubeRed
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download episode",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
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
                    fontWeight = FontWeight.Black,
                    color = YouTubeRed,
                    letterSpacing = 0.4.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
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
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
