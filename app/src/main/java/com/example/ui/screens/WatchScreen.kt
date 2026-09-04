package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.tmdb.TmdbEpisodeItem
import com.example.model.CommentItem
import com.example.model.MediaType
import com.example.model.PlayerSnapshot
import com.example.model.VideoItem
import com.example.model.playbackKey
import com.example.model.isUnreleased
import com.example.ui.components.TvShowEpisodeList
import com.example.ui.components.AmbientLightBackdrop
import com.example.ui.components.VideoCard
import com.example.ui.components.VideoCardSkeleton
import com.example.ui.components.VideoWatchDetails
import com.example.ui.components.YouTubePlayer
import com.example.ui.theme.YouTubeRed
import com.example.util.FullscreenHelper
import kotlinx.coroutines.delay
import kotlin.math.ceil

private val HeaderPillShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    video: VideoItem,
    relatedVideos: List<VideoItem>,
    tvEpisodes: List<TmdbEpisodeItem>,
    totalSeasons: Int,
    selectedSeason: Int,
    selectedServerId: String,
    resumePositionSeconds: Double = 0.0,
    currentPlaybackSnapshot: PlayerSnapshot? = null,
    isPlaying: Boolean = true,
    isLiked: Boolean,
    isDisliked: Boolean,
    isSubscribed: Boolean,
    isSaved: Boolean,
    topComment: CommentItem?,
    isTabletLayout: Boolean = false,
    isAutoNextEnabled: Boolean = true,
    onPlayNextEpisode: () -> Unit = {},
    onToggleAutoNext: () -> Unit = {},
    onRetryPlayback: () -> Unit = {},
    onMinimize: () -> Unit,
    onSelectServer: (String) -> Unit,
    onToggleLike: () -> Unit,
    onToggleDislike: () -> Unit,
    onToggleSubscribe: () -> Unit,
    onToggleSave: () -> Unit,
    onOpenComments: () -> Unit,
    onOpenServerDialog: () -> Unit,
    onOpenQueue: () -> Unit = {},
    onOpenChannel: (String) -> Unit = {},
    onSelectSeason: (Int) -> Unit,
    onSelectEpisode: (Int, Int) -> Unit,
    onSelectVideo: (VideoItem) -> Unit,
    onSaveToWatchLater: (VideoItem) -> Unit,
    onShare: (VideoItem) -> Unit,
    onAddToQueue: (VideoItem) -> Unit = {},
    watchedVideoIds: Set<String> = emptySet(),
    onToggleWatched: (VideoItem) -> Unit = {},
    onNotInterested: (VideoItem) -> Unit = {},
    onNotRecommendChannel: (VideoItem) -> Unit = {},
    isReleaseAlertActive: Boolean = false,
    onToggleReleaseAlert: () -> Unit = {},
    isEpisodeAlertActive: (Int, Int) -> Boolean = { _, _ -> false },
    onNotifyEpisode: (TmdbEpisodeItem) -> Unit = {},
    onDownloadMovie: ((VideoItem) -> Unit)? = null,
    onDownloadEpisode: ((VideoItem, TmdbEpisodeItem) -> Unit)? = null,
    onDownloadSeason: ((VideoItem, Int, List<TmdbEpisodeItem>) -> Unit)? = null,
    onDownloadVideo: ((VideoItem) -> Unit)? = null,
    isMovieDownloaded: Boolean = false,
    movieDownloadProgress: Int? = null,
    isEpisodeDownloaded: (Int, Int) -> Boolean = { _, _ -> false },
    getEpisodeDownloadProgress: (Int, Int) -> Int? = { _, _ -> null },
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("watch_screen")
    ) {
        // Landscape tablets get a scrollable details pane beside the player;
        // otherwise a full-width player can consume nearly the entire height
        // and leave no useful area for the title, actions, and episodes.
        val isLandscape = maxWidth > maxHeight
        val isTabletDevice = isTabletLayout || maxWidth >= 500.dp && maxHeight >= 700.dp
        val showTabletRightPane = isTabletDevice && isLandscape
        val displayRelatedVideos = remember(video.id, relatedVideos) {
            relatedVideos
                .filterNot { it.id == video.id }
                .distinctBy { it.id }
        }
        val nextEpisode = remember(video.id, selectedSeason, video.currentEpisode, tvEpisodes) {
            if (video.mediaType != MediaType.TV_SHOW) {
                null
            } else {
                tvEpisodes
                    .asSequence()
                    .filter {
                        it.seasonNumber == selectedSeason &&
                            it.episodeNumber > video.currentEpisode
                    }
                    .sortedBy { it.episodeNumber }
                    .firstOrNull()
            }
        }
        val nextSeason = if (video.mediaType == MediaType.TV_SHOW &&
            nextEpisode == null &&
            selectedSeason < maxOf(totalSeasons, video.totalSeasons, selectedSeason, 1)
        ) {
            selectedSeason + 1
        } else {
            null
        }
        val nextMovie = if (video.mediaType == MediaType.TV_SHOW) {
            null
        } else {
            displayRelatedVideos.firstOrNull()
        }
        val nextTargetKey = when {
            nextEpisode != null -> "episode:${nextEpisode.seasonNumber}:${nextEpisode.episodeNumber}"
            nextSeason != null -> "season:$nextSeason:episode:1"
            nextMovie != null -> "movie:${nextMovie.id}"
            else -> null
        }
        val nextTargetTitle = when {
            nextEpisode != null -> nextEpisode.name
            nextSeason != null -> "Season $nextSeason · Episode 1"
            nextMovie != null -> nextMovie.title
            else -> null
        }
        val nextTargetSubtitle = when {
            nextEpisode != null -> "S${nextEpisode.seasonNumber} · E${nextEpisode.episodeNumber}"
            nextSeason != null -> "Next season"
            nextMovie != null -> nextMovie.channelName
            else -> null
        }
        val onPlayNext: (() -> Unit)? = when {
            video.mediaType == MediaType.TV_SHOW && (nextEpisode != null || nextSeason != null) ->
                onPlayNextEpisode
            nextMovie != null -> nextMovie.let { movie -> { onSelectVideo(movie) } }
            else -> null
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (showTabletRightPane) {
                // Tablet Landscape 2-Pane Split Layout
                // Left pane takes 73% (large cinematic player + details), Right pane is reduced to 27% (compact sidebar)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                ) {
                    // Left Pane: Large Video Player + Video Details (Scrollable)
                    val leftScrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .weight(0.73f)
                            .fillMaxHeight()
                            .verticalScroll(leftScrollState)
                    ) {
                        PlayerSurfaceWithUpNext(
                            video = video,
                            selectedServerId = selectedServerId,
                            onSelectServer = onSelectServer,
                            onOpenServerDialog = onOpenServerDialog,
                            resumePositionSeconds = resumePositionSeconds,
                            currentPlaybackSnapshot = currentPlaybackSnapshot,
                            playWhenReady = isPlaying,
                            onSwipeDown = onMinimize,
                            isAutoNextEnabled = isAutoNextEnabled,
                            nextKey = nextTargetKey,
                            nextTitle = nextTargetTitle,
                            nextSubtitle = nextTargetSubtitle,
                            onPlayNext = onPlayNext,
                            onRetryPlayback = onRetryPlayback,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((this@BoxWithConstraints.maxHeight * 0.52f).coerceIn(280.dp, 480.dp))
                        )

                        VideoWatchDetails(
                            video = video,
                            isLiked = isLiked,
                            isDisliked = isDisliked,
                            isSubscribed = isSubscribed,
                            isSaved = isSaved,
                            topComment = topComment,
                            onToggleLike = onToggleLike,
                            onToggleDislike = onToggleDislike,
                            onToggleSubscribe = onToggleSubscribe,
                            onToggleSave = onToggleSave,
                            onOpenComments = onOpenComments,
                            onOpenServerDialog = onOpenServerDialog,
                            onOpenQueue = onOpenQueue,
                            onOpenChannel = onOpenChannel,
                            onPlayNextEpisode = if (video.mediaType == MediaType.TV_SHOW) onPlayNextEpisode else null,
                            isReleaseAlertActive = isReleaseAlertActive,
                            onToggleReleaseAlert = onToggleReleaseAlert,
                            isDownloaded = if (video.mediaType == MediaType.MOVIE) isMovieDownloaded else false,
                            downloadProgress = if (video.mediaType == MediaType.MOVIE) movieDownloadProgress else null,
                            onDownloadClick = if (video.mediaType == MediaType.MOVIE && onDownloadMovie != null) {
                                { onDownloadMovie(video) }
                            } else null,
                        )

                        if (video.mediaType == MediaType.TV_SHOW) {
                            TvShowEpisodeList(
                                episodes = tvEpisodes,
                                totalSeasons = totalSeasons,
                                selectedSeason = selectedSeason,
                                currentEpisodeNumber = video.currentEpisode ?: 1,
                                fallbackThumbnailUrl = video.thumbnailUrl,
                                onSelectSeason = onSelectSeason,
                                onSelectEpisode = onSelectEpisode,
                                onPlayNextEpisode = onPlayNextEpisode,
                                isAutoNextEnabled = isAutoNextEnabled,
                                onToggleAutoNext = onToggleAutoNext,
                                isEpisodeAlertActive = isEpisodeAlertActive,
                                onNotifyEpisode = onNotifyEpisode,
                                onDownloadSeason = if (onDownloadSeason != null) { { s, eps -> onDownloadSeason(video, s, eps) } } else null,
                                onDownloadEpisode = if (onDownloadEpisode != null) { { ep -> onDownloadEpisode(video, ep) } } else null,
                                isEpisodeDownloaded = isEpisodeDownloaded,
                                getEpisodeDownloadProgress = getEpisodeDownloadProgress,
                            )
                        }
                    }

                    // Vertical Separator
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    )

                    // Right Pane: two-card related grid for tablet-sized screens.
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .weight(0.36f)
                            .fillMaxHeight()
                            .padding(horizontal = 6.dp)
                            .testTag("watch_related_videos_list"),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        item(
                            key = "tablet_right_header",
                            contentType = "header",
                            span = { GridItemSpan(2) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = YouTubeRed,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (video.mediaType == MediaType.TV_SHOW) "More shows" else "Up next",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }

                                IconButton(
                                    onClick = onMinimize,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Minimize",
                                        tint = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }

                        if (displayRelatedVideos.isEmpty()) {
                            items(
                                count = 4,
                                key = { "tab_rel_skel_$it" },
                                contentType = { "related_skeleton" }
                            ) {
                                VideoCardSkeleton()
                            }
                        } else {
                            items(
                                items = displayRelatedVideos,
                                key = { it.id },
                                contentType = { "tablet_related_card" }
                            ) { relatedVideo ->
                                val onClick = remember(relatedVideo.id, onSelectVideo) {
                                    { onSelectVideo(relatedVideo) }
                                }
                                val onSave = remember(relatedVideo.id, onSaveToWatchLater) {
                                    { onSaveToWatchLater(relatedVideo) }
                                }
                                val onShareLambda = remember(relatedVideo.id, onShare) {
                                    { onShare(relatedVideo) }
                                }
                                val onDownloadLambda = remember(relatedVideo.id, onDownloadVideo) {
                                    onDownloadVideo?.let { { it(relatedVideo) } }
                                }
                                VideoCard(
                                    video = relatedVideo,
                                    onClick = onClick,
                                    onSaveToWatchLater = onSave,
                                    onShare = onShareLambda,
                                    onDownload = onDownloadLambda,
                                    onAddToQueue = { onAddToQueue(relatedVideo) },
                                    isWatched = relatedVideo.id in watchedVideoIds,
                                    onToggleWatched = { onToggleWatched(relatedVideo) },
                                    onNotInterested = { onNotInterested(relatedVideo) },
                                    onNotRecommendChannel = { onNotRecommendChannel(relatedVideo) },
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                // Portrait / Single-Column Layout:
                // Match YouTube's edge-to-edge 16:9 player surface. Ambient
                // light lives outside this surface so it can bleed into the
                // title and metadata below without shrinking the video.
                val playerHeight = this@BoxWithConstraints.maxWidth * (9f / 16f)
                val ambientArtwork = video.backdropUrl ?: video.thumbnailUrl

                Box(modifier = Modifier.fillMaxSize()) {
                    if (ambientArtwork.isNotBlank()) {
                        AmbientLightBackdrop(
                            artworkUrl = ambientArtwork,
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.TopCenter)
                        )
                    }

                    Column(modifier = Modifier.fillMaxSize()) {
                        PlayerSurfaceWithUpNext(
                            video = video,
                            selectedServerId = selectedServerId,
                            onSelectServer = onSelectServer,
                            onOpenServerDialog = onOpenServerDialog,
                            resumePositionSeconds = resumePositionSeconds,
                            currentPlaybackSnapshot = currentPlaybackSnapshot,
                            playWhenReady = isPlaying,
                            onSwipeDown = onMinimize,
                            isAutoNextEnabled = isAutoNextEnabled,
                            nextKey = nextTargetKey,
                            nextTitle = nextTargetTitle,
                            nextSubtitle = nextTargetSubtitle,
                            onPlayNext = onPlayNext,
                            onRetryPlayback = onRetryPlayback,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(playerHeight)
                        )

                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                    item(key = "watch_details", contentType = "details") {
                        VideoWatchDetails(
                            video = video,
                            isLiked = isLiked,
                            isDisliked = isDisliked,
                            isSubscribed = isSubscribed,
                            isSaved = isSaved,
                            topComment = topComment,
                            onToggleLike = onToggleLike,
                            onToggleDislike = onToggleDislike,
                            onToggleSubscribe = onToggleSubscribe,
                            onToggleSave = onToggleSave,
                            onOpenComments = onOpenComments,
                            onOpenServerDialog = onOpenServerDialog,
                            onOpenQueue = onOpenQueue,
                            onOpenChannel = onOpenChannel,
                            onPlayNextEpisode = if (video.mediaType == MediaType.TV_SHOW) onPlayNextEpisode else null,
                            isReleaseAlertActive = isReleaseAlertActive,
                            onToggleReleaseAlert = onToggleReleaseAlert,
                            isDownloaded = if (video.mediaType == MediaType.MOVIE) isMovieDownloaded else false,
                            downloadProgress = if (video.mediaType == MediaType.MOVIE) movieDownloadProgress else null,
                            onDownloadClick = if (video.mediaType == MediaType.MOVIE && onDownloadMovie != null) {
                                { onDownloadMovie(video) }
                            } else null,
                        )
                    }

                    // TV Episodes Component
                    if (video.mediaType == MediaType.TV_SHOW) {
                        item(key = "watch_episodes", contentType = "episodes") {
                            TvShowEpisodeList(
                                episodes = tvEpisodes,
                                totalSeasons = totalSeasons,
                                selectedSeason = selectedSeason,
                                currentEpisodeNumber = video.currentEpisode ?: 1,
                                fallbackThumbnailUrl = video.thumbnailUrl,
                                onSelectSeason = onSelectSeason,
                                onSelectEpisode = onSelectEpisode,
                                onPlayNextEpisode = onPlayNextEpisode,
                                isAutoNextEnabled = isAutoNextEnabled,
                                onToggleAutoNext = onToggleAutoNext,
                                isEpisodeAlertActive = isEpisodeAlertActive,
                                onNotifyEpisode = onNotifyEpisode,
                                onDownloadSeason = if (onDownloadSeason != null) { { s, eps -> onDownloadSeason(video, s, eps) } } else null,
                                onDownloadEpisode = if (onDownloadEpisode != null) { { ep -> onDownloadEpisode(video, ep) } } else null,
                                isEpisodeDownloaded = isEpisodeDownloaded,
                                getEpisodeDownloadProgress = getEpisodeDownloadProgress,
                            )
                        }
                    }

                    item(key = "watch_up_next_title", contentType = "title") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = YouTubeRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = if (video.mediaType == MediaType.TV_SHOW) "More shows" else "More like this",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    if (displayRelatedVideos.isEmpty()) {
                        items(
                            count = if (isTabletDevice) 2 else 4,
                            key = { "port_rel_skel_$it" },
                            contentType = { "video_card_skeleton" }
                        ) {
                            if (isTabletDevice) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    VideoCardSkeleton(modifier = Modifier.weight(1f))
                                    VideoCardSkeleton(modifier = Modifier.weight(1f))
                                }
                            } else {
                                VideoCardSkeleton()
                            }
                        }
                    } else {
                        items(
                            items = displayRelatedVideos.chunked(if (isTabletDevice) 2 else 1),
                            key = { row -> row.joinToString("|") { it.id } },
                            contentType = { "video_card" }
                        ) { relatedRow ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                relatedRow.forEach { relatedVideo ->
                                    val onClick = remember(relatedVideo.id, onSelectVideo) {
                                        { onSelectVideo(relatedVideo) }
                                    }
                                    val onSave = remember(relatedVideo.id, onSaveToWatchLater) {
                                        { onSaveToWatchLater(relatedVideo) }
                                    }
                                    val onShareLambda = remember(relatedVideo.id, onShare) {
                                        { onShare(relatedVideo) }
                                    }
                                    val onDownloadLambda = remember(relatedVideo.id, onDownloadVideo) {
                                        onDownloadVideo?.let { { it(relatedVideo) } }
                                    }
                                    VideoCard(
                                        video = relatedVideo,
                                        onClick = onClick,
                                        onSaveToWatchLater = onSave,
                                        onShare = onShareLambda,
                                        onDownload = onDownloadLambda,
                                        onAddToQueue = { onAddToQueue(relatedVideo) },
                                        isWatched = relatedVideo.id in watchedVideoIds,
                                        onToggleWatched = { onToggleWatched(relatedVideo) },
                                        onNotInterested = { onNotInterested(relatedVideo) },
                                        onNotRecommendChannel = { onNotRecommendChannel(relatedVideo) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (isTabletDevice && relatedRow.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ComingSoonPlayerPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = YouTubeRed,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Coming soon",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Use Notify me below for a release alert.",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

private const val UP_NEXT_LEAD_SECONDS = 60.0
private const val UP_NEXT_COUNTDOWN_SECONDS = 10.0

@Composable
private fun PlayerSurfaceWithUpNext(
    video: VideoItem,
    selectedServerId: String,
    onSelectServer: (String) -> Unit,
    onOpenServerDialog: () -> Unit,
    resumePositionSeconds: Double,
    currentPlaybackSnapshot: PlayerSnapshot?,
    playWhenReady: Boolean,
    onSwipeDown: () -> Unit,
    isAutoNextEnabled: Boolean,
    nextKey: String?,
    nextTitle: String?,
    nextSubtitle: String?,
    onPlayNext: (() -> Unit)?,
    onRetryPlayback: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (isUnreleased(video.releaseDateIso ?: video.releaseDateFormatted)) {
            ComingSoonPlayerPlaceholder()
        } else {
            YouTubePlayer(
                video = video,
                selectedServerId = selectedServerId,
                onSelectServer = onSelectServer,
                onOpenServerDialog = onOpenServerDialog,
                resumePositionSeconds = resumePositionSeconds,
                playWhenReady = playWhenReady,
                onSwipeDown = onSwipeDown,
                onRetryPlayback = onRetryPlayback,
                modifier = Modifier.fillMaxSize()
            )
        }

        val isFullscreen by FullscreenHelper.isFullscreen.collectAsState()
        val durationSeconds = currentPlaybackSnapshot?.durationSeconds
            ?.takeIf { it.isFinite() && it > 0.0 }
            ?: 0.0
        val positionSeconds = currentPlaybackSnapshot?.positionSeconds
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?: 0.0
        val validPlaybackSnapshot = durationSeconds > 0.0 &&
            positionSeconds >= 0.0 &&
            positionSeconds <= durationSeconds + 1.0
        val remainingSeconds = (durationSeconds - positionSeconds).coerceAtLeast(0.0)
        val showAutoNext = video.mediaType == MediaType.TV_SHOW &&
            onPlayNext != null &&
            nextKey != null &&
            !nextTitle.isNullOrBlank() &&
            durationSeconds > UP_NEXT_LEAD_SECONDS &&
            validPlaybackSnapshot &&
            remainingSeconds <= UP_NEXT_LEAD_SECONDS
        val remainingWholeSecond = ceil(remainingSeconds).toInt()
        val latestOnPlayNext by rememberUpdatedState(onPlayNext)
        val hasNativeFullscreen = isFullscreen && FullscreenHelper.isCustomViewActive

        DisposableEffect(video.playbackKey(), nextKey, nextTitle, nextSubtitle, isAutoNextEnabled) {
            if (video.mediaType == MediaType.TV_SHOW &&
                nextKey != null &&
                !nextTitle.isNullOrBlank() &&
                onPlayNext != null
            ) {
                FullscreenHelper.setUpNextTarget(
                    key = nextKey,
                    title = nextTitle,
                    subtitle = nextSubtitle,
                    autoNextEnabled = isAutoNextEnabled,
                    onPlayNext = { latestOnPlayNext?.invoke() }
                )
            } else {
                FullscreenHelper.clearUpNextTarget()
            }
            onDispose {
                FullscreenHelper.clearUpNextTarget(nextKey)
            }
        }

        SideEffect {
            currentPlaybackSnapshot?.let { snapshot ->
                FullscreenHelper.updateUpNextPlayback(
                    positionSeconds = snapshot.positionSeconds,
                    durationSeconds = snapshot.durationSeconds
                )
            }
        }

        var autoNextTriggered by remember(video.playbackKey(), nextKey) {
            mutableStateOf(false)
        }
        LaunchedEffect(
            video.playbackKey(),
            nextKey,
            isAutoNextEnabled,
            remainingWholeSecond,
            showAutoNext,
            hasNativeFullscreen
        ) {
            if (!showAutoNext || hasNativeFullscreen || !isAutoNextEnabled || autoNextTriggered) {
                return@LaunchedEffect
            }

            if (remainingSeconds <= UP_NEXT_COUNTDOWN_SECONDS) {
                delay((remainingSeconds * 1000L).toLong().coerceAtLeast(0L))
                if (!autoNextTriggered) {
                    autoNextTriggered = true
                    onPlayNext?.invoke()
                }
            }
        }
    }
}
