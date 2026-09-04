package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.VideoRepository
import com.example.model.MediaType
import com.example.model.ShortItem
import com.example.model.VideoItem
import com.example.model.WatchHistoryEntry
import com.example.model.formatPlaybackTime
import com.example.model.releaseAlertId
import com.example.ui.components.FilterPillRow
import com.example.ui.components.FittedMediaThumbnail
import com.example.ui.components.ShortsShelf
import com.example.ui.components.ShortsShelfSkeleton
import com.example.ui.components.VideoCard
import com.example.ui.components.VideoCardSkeleton
import com.example.ui.theme.YouTubeRed
import com.example.util.ImagePreset
import com.example.util.rememberThumbnailRequestWithFallback

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

private val HeroBadgeShape = RoundedCornerShape(4.dp)
private val ButtonShape = RoundedCornerShape(20.dp)
private val ContinueCardShape = RoundedCornerShape(10.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    videos: List<VideoItem>,
    shorts: List<ShortItem>,
    continueWatching: List<WatchHistoryEntry> = emptyList(),
    recentWatched: List<WatchHistoryEntry> = emptyList(),
    showContinueWatching: Boolean = false,
    selectedCategory: String,
    isLoading: Boolean,
    isLoadingMore: Boolean = false,
    feedErrorMessage: String? = null,
    isOffline: Boolean = false,
    isDarkMode: Boolean,
    isTabletLayout: Boolean = false,
    onCategorySelected: (String) -> Unit,
    onLoadMore: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onVideoClick: (VideoItem) -> Unit,
    onContinueWatchingClick: (WatchHistoryEntry) -> Unit = { onVideoClick(it.video) },
    onShortClick: (Int) -> Unit,
    onSaveToWatchLater: (VideoItem) -> Unit,
    onShare: (VideoItem) -> Unit,
    onAddToQueue: (VideoItem) -> Unit = {},
    watchedVideoIds: Set<String> = emptySet(),
    onToggleWatched: (VideoItem) -> Unit = {},
    notInterestedVideoIds: Set<String> = emptySet(),
    notRecommendedChannelNames: Set<String> = emptySet(),
    onNotInterested: (VideoItem) -> Unit = {},
    onNotRecommendChannel: (VideoItem) -> Unit = {},
    onDownloadVideo: ((VideoItem) -> Unit)? = null,
    releaseAlertIds: Set<String> = emptySet(),
    onToggleReleaseAlert: (VideoItem) -> Unit = {},
    onOpenServerDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("home_screen")
    ) {
        val currentMaxWidth = maxWidth
        val isWide = isTabletLayout || currentMaxWidth >= 600.dp
        val gridColumns = if (currentMaxWidth >= 1000.dp) 3 else 2
        val blockedChannelNames = remember(notRecommendedChannelNames) {
            notRecommendedChannelNames.map { it.trim().lowercase() }.toSet()
        }

        val visibleVideos = remember(videos, notInterestedVideoIds, blockedChannelNames) {
            videos.filterNot { video ->
                video.id in notInterestedVideoIds ||
                    video.channelName.trim().lowercase() in blockedChannelNames
            }
        }
        val visibleContinueWatching = remember(continueWatching, notInterestedVideoIds, blockedChannelNames) {
            continueWatching.filterNot { entry ->
                entry.video.id in notInterestedVideoIds ||
                    entry.video.channelName.trim().lowercase() in blockedChannelNames
            }
        }
        val visibleRecentWatched = remember(
            recentWatched,
            notInterestedVideoIds,
            blockedChannelNames,
            showContinueWatching
        ) {
            if (showContinueWatching) {
                emptyList()
            } else {
                recentWatched
                    .filterNot { entry ->
                        entry.video.id in notInterestedVideoIds ||
                            entry.video.channelName.trim().lowercase() in blockedChannelNames
                    }
                    .take(2)
            }
        }
        val recentWatchedIds = remember(visibleRecentWatched) {
            visibleRecentWatched.map { it.video.id }.toSet()
        }
        val feedVideos = remember(visibleVideos, recentWatchedIds) {
            visibleVideos.filterNot { it.id in recentWatchedIds }
        }
        val firstChunk = remember(feedVideos) {
            if (feedVideos.size > 2) feedVideos.subList(0, 2) else feedVideos
        }
        val remainingVideos = remember(feedVideos) {
            if (feedVideos.size > 2) feedVideos.subList(2, feedVideos.size) else emptyList()
        }
        val latestOnLoadMore = rememberUpdatedState(onLoadMore)

        Column(modifier = Modifier.fillMaxSize()) {
            // Topic Filters
            FilterPillRow(
                categories = VideoRepository.CATEGORIES,
                selectedCategory = selectedCategory,
                onCategorySelected = onCategorySelected,
                isDarkMode = isDarkMode,
                onExploreClick = onOpenServerDialog
            )

            // Non-jumping loading indicator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            ) {
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxSize(),
                        color = YouTubeRed,
                        trackColor = MaterialTheme.colorScheme.background
                    )
                }
            }

            if (feedErrorMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                        .testTag("feed_error_banner"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isOffline) "Offline · $feedErrorMessage" else feedErrorMessage,
                        modifier = Modifier.weight(1f),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Retry",
                        modifier = Modifier
                            .clickable(onClick = onRefresh)
                            .padding(start = 10.dp, top = 4.dp, bottom = 4.dp)
                            .testTag("feed_retry"),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = YouTubeRed
                    )
                }
            }

            var isRefreshing by remember { mutableStateOf(false) }
            val pullToRefreshState = rememberPullToRefreshState()

            LaunchedEffect(isLoading) {
                if (!isLoading) isRefreshing = false
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    if (!isLoading && !isLoadingMore) {
                        isRefreshing = true
                        onRefresh()
                    }
                },
                state = pullToRefreshState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                // Video Feed
                if (visibleVideos.isEmpty() && visibleRecentWatched.isEmpty() && isLoading) {
                // Initial Load Animated Shimmer Feed (YouTube-style Shimmer Skeletons)
                if (isWide) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(gridColumns),
                        contentPadding = PaddingValues(bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp)
                            .testTag("home_skeleton_grid")
                    ) {
                        items(count = 2, contentType = { "video_skeleton" }) {
                            VideoCardSkeleton()
                        }
                        item(span = { GridItemSpan(gridColumns) }, contentType = "shorts_skeleton") {
                            ShortsShelfSkeleton()
                        }
                        items(count = 6, contentType = { "video_skeleton" }) {
                            VideoCardSkeleton()
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("home_skeleton_list")
                    ) {
                        items(count = 2, contentType = { "video_skeleton" }) {
                            VideoCardSkeleton()
                        }
                        item(contentType = "shorts_skeleton") {
                            ShortsShelfSkeleton()
                        }
                        items(count = 4, contentType = { "video_skeleton" }) {
                            VideoCardSkeleton()
                        }
                    }
                }
                } else if (visibleVideos.isEmpty() && visibleRecentWatched.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (videos.isEmpty()) "No videos found" else "No videos match your recommendation settings",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onCategorySelected("All") },
                            colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reload Feed")
                        }
                    }
                }
                } else if (isWide) {
                // Tablet Multi-Column Responsive Grid Layout with Infinite Scroll
                val gridState = rememberLazyGridState()

                val shouldPrefetch by remember(gridState, isLoadingMore, isLoading) {
                    derivedStateOf {
                        val layoutInfo = gridState.layoutInfo
                        val total = layoutInfo.totalItemsCount
                        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        total > 0 && lastVisible >= total - 3 && !isLoading && !isLoadingMore
                    }
                }

                LaunchedEffect(gridState, isLoadingMore, isLoading) {
                    snapshotFlow { shouldPrefetch }
                        .distinctUntilChanged()
                        .collect { shouldLoad ->
                            if (shouldLoad) latestOnLoadMore.value()
                        }
                }

                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(gridColumns),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp)
                        .testTag("home_video_grid")
                ) {
                    // Recent history is presented as ordinary cards at the top
                    // instead of a separate Continue Watching shelf.
                    items(
                        items = visibleRecentWatched,
                        key = { "recent_watched_${it.key}" },
                        contentType = { "recent_watched_card" }
                    ) { entry ->
                        val video = entry.video
                        VideoCard(
                            video = video,
                            onClick = { onContinueWatchingClick(entry) },
                            onSaveToWatchLater = { onSaveToWatchLater(video) },
                            onShare = { onShare(video) },
                            onDownload = onDownloadVideo?.let { { it(video) } },
                            onAddToQueue = { onAddToQueue(video) },
                            isWatched = video.id in watchedVideoIds,
                            onToggleWatched = { onToggleWatched(video) },
                            onNotInterested = { onNotInterested(video) },
                            onNotRecommendChannel = { onNotRecommendChannel(video) },
                            isReleaseAlertActive = releaseAlertId(video) in releaseAlertIds,
                            onToggleReleaseAlert = { onToggleReleaseAlert(video) }
                        )
                    }

                    // Top Normal Video Cards (First Chunk)
                    items(
                        items = firstChunk,
                        key = { it.id },
                        contentType = { "video_card" }
                    ) { video ->
                        val onClick = remember(video.id, onVideoClick) { { onVideoClick(video) } }
                        val onSave = remember(video.id, onSaveToWatchLater) { { onSaveToWatchLater(video) } }
                        val onShareLambda = remember(video.id, onShare) { { onShare(video) } }
                        val onDownloadLambda = remember(video.id, onDownloadVideo) {
                            onDownloadVideo?.let { { it(video) } }
                        }

                        VideoCard(
                            video = video,
                            onClick = onClick,
                            onSaveToWatchLater = onSave,
                            onShare = onShareLambda,
                            onDownload = onDownloadLambda,
                            onAddToQueue = { onAddToQueue(video) },
                            isWatched = video.id in watchedVideoIds,
                            onToggleWatched = { onToggleWatched(video) },
                            onNotInterested = { onNotInterested(video) },
                            onNotRecommendChannel = { onNotRecommendChannel(video) },
                            isReleaseAlertActive = releaseAlertId(video) in releaseAlertIds,
                            onToggleReleaseAlert = { onToggleReleaseAlert(video) }
                        )
                    }

                    // Continue Watching Section (Span all columns)
                    if (showContinueWatching && visibleContinueWatching.isNotEmpty()) {
                        item(
                            key = "continue_watching_section",
                            span = { GridItemSpan(gridColumns) },
                            contentType = "continue_watching"
                        ) {
                            ContinueWatchingSection(
                                items = visibleContinueWatching,
                                onItemClick = onContinueWatchingClick,
                                onDownloadClick = onDownloadVideo
                            )
                        }
                    }

                    // Remaining Video Cards in Grid
                    items(
                        items = remainingVideos,
                        key = { it.id },
                        contentType = { "video_card" }
                    ) { video ->
                        val onClick = remember(video.id, onVideoClick) { { onVideoClick(video) } }
                        val onSave = remember(video.id, onSaveToWatchLater) { { onSaveToWatchLater(video) } }
                        val onShareLambda = remember(video.id, onShare) { { onShare(video) } }
                        val onDownloadLambda = remember(video.id, onDownloadVideo) {
                            onDownloadVideo?.let { { it(video) } }
                        }

                        VideoCard(
                            video = video,
                            onClick = onClick,
                            onSaveToWatchLater = onSave,
                            onShare = onShareLambda,
                            onDownload = onDownloadLambda,
                            onAddToQueue = { onAddToQueue(video) },
                            isWatched = video.id in watchedVideoIds,
                            onToggleWatched = { onToggleWatched(video) },
                            onNotInterested = { onNotInterested(video) },
                            onNotRecommendChannel = { onNotRecommendChannel(video) },
                            isReleaseAlertActive = releaseAlertId(video) in releaseAlertIds,
                            onToggleReleaseAlert = { onToggleReleaseAlert(video) }
                        )
                    }

                    // Bottom Infinite Scroll Loading Shimmer Skeletons
                    if (isLoadingMore) {
                        items(
                            count = gridColumns,
                            key = { "grid_more_skel_$it" },
                            span = { GridItemSpan(1) },
                            contentType = { "video_skeleton" }
                        ) {
                            VideoCardSkeleton()
                        }
                        item(
                            key = "infinite_scroll_loading",
                            span = { GridItemSpan(gridColumns) },
                            contentType = "loading_indicator"
                        ) {
                            InfiniteLoadingIndicator()
                        }
                    }
                }
                } else {
                // Mobile Single-Column Layout with Infinite Scroll
                val listState = rememberLazyListState()

                val shouldPrefetch by remember(listState, isLoadingMore, isLoading) {
                    derivedStateOf {
                        val layoutInfo = listState.layoutInfo
                        val total = layoutInfo.totalItemsCount
                        val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        total > 0 && lastVisible >= total - 3 && !isLoading && !isLoadingMore
                    }
                }

                LaunchedEffect(listState, isLoadingMore, isLoading) {
                    snapshotFlow { shouldPrefetch }
                        .distinctUntilChanged()
                        .collect { shouldLoad ->
                            if (shouldLoad) latestOnLoadMore.value()
                        }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("home_video_list")
                ) {
                    // Recent history is presented as ordinary cards at the top
                    // instead of a separate Continue Watching shelf.
                    items(
                        items = visibleRecentWatched,
                        key = { "recent_watched_${it.key}" },
                        contentType = { "recent_watched_card" }
                    ) { entry ->
                        val video = entry.video
                        VideoCard(
                            video = video,
                            onClick = { onContinueWatchingClick(entry) },
                            onSaveToWatchLater = { onSaveToWatchLater(video) },
                            onShare = { onShare(video) },
                            onDownload = onDownloadVideo?.let { { it(video) } },
                            onAddToQueue = { onAddToQueue(video) },
                            isWatched = video.id in watchedVideoIds,
                            onToggleWatched = { onToggleWatched(video) },
                            onNotInterested = { onNotInterested(video) },
                            onNotRecommendChannel = { onNotRecommendChannel(video) },
                            isReleaseAlertActive = releaseAlertId(video) in releaseAlertIds,
                            onToggleReleaseAlert = { onToggleReleaseAlert(video) }
                        )
                    }

                    // Top Normal Video Cards (First Chunk)
                    items(
                        items = firstChunk,
                        key = { it.id },
                        contentType = { "video_card" }
                    ) { video ->
                        val onClick = remember(video.id, onVideoClick) { { onVideoClick(video) } }
                        val onSave = remember(video.id, onSaveToWatchLater) { { onSaveToWatchLater(video) } }
                        val onShareLambda = remember(video.id, onShare) { { onShare(video) } }
                        val onDownloadLambda = remember(video.id, onDownloadVideo) {
                            onDownloadVideo?.let { { it(video) } }
                        }

                        VideoCard(
                            video = video,
                            onClick = onClick,
                            onSaveToWatchLater = onSave,
                            onShare = onShareLambda,
                            onDownload = onDownloadLambda,
                            onAddToQueue = { onAddToQueue(video) },
                            isWatched = video.id in watchedVideoIds,
                            onToggleWatched = { onToggleWatched(video) },
                            onNotInterested = { onNotInterested(video) },
                            onNotRecommendChannel = { onNotRecommendChannel(video) },
                            isReleaseAlertActive = releaseAlertId(video) in releaseAlertIds,
                            onToggleReleaseAlert = { onToggleReleaseAlert(video) }
                        )
                    }

                    // Continue Watching Carousel under top normal cards
                    if (showContinueWatching && visibleContinueWatching.isNotEmpty()) {
                        item(key = "continue_watching_section", contentType = "continue_watching") {
                            ContinueWatchingSection(
                                items = visibleContinueWatching,
                                onItemClick = onContinueWatchingClick,
                                onDownloadClick = onDownloadVideo
                            )
                        }
                    }

                    // Dedicated Shorts Shelf Item
                    if (shorts.isNotEmpty()) {
                        item(key = "home_shorts_shelf", contentType = "shorts_shelf") {
                            ShortsShelf(
                                shorts = shorts,
                                onShortClick = onShortClick
                            )
                        }
                    }

                    // Remaining Videos for endless scroll
                    items(
                        items = remainingVideos,
                        key = { it.id },
                        contentType = { "video_card" }
                    ) { video ->
                        val onClick = remember(video.id, onVideoClick) { { onVideoClick(video) } }
                        val onSave = remember(video.id, onSaveToWatchLater) { { onSaveToWatchLater(video) } }
                        val onShareLambda = remember(video.id, onShare) { { onShare(video) } }
                        val onDownloadLambda = remember(video.id, onDownloadVideo) {
                            onDownloadVideo?.let { { it(video) } }
                        }

                        VideoCard(
                            video = video,
                            onClick = onClick,
                            onSaveToWatchLater = onSave,
                            onShare = onShareLambda,
                            onDownload = onDownloadLambda,
                            onAddToQueue = { onAddToQueue(video) },
                            isWatched = video.id in watchedVideoIds,
                            onToggleWatched = { onToggleWatched(video) },
                            onNotInterested = { onNotInterested(video) },
                            onNotRecommendChannel = { onNotRecommendChannel(video) },
                            isReleaseAlertActive = releaseAlertId(video) in releaseAlertIds,
                            onToggleReleaseAlert = { onToggleReleaseAlert(video) }
                        )
                    }

                    // Bottom Infinite Scroll Loading Shimmer Skeletons
                    if (isLoadingMore) {
                        items(
                            count = 2,
                            key = { "list_more_skel_$it" },
                            contentType = { "video_skeleton" }
                        ) {
                            VideoCardSkeleton()
                        }
                        item(key = "infinite_scroll_loading", contentType = "loading_indicator") {
                            InfiniteLoadingIndicator()
                        }
                    }

                    item(key = "bottom_spacer", contentType = "spacer") {
                        Spacer(modifier = Modifier.height(72.dp))
                    }
                }
                }
            }
        }
    }
}

@Composable
private fun InfiniteLoadingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = YouTubeRed
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Loading more titles...",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun ContinueWatchingSection(
    items: List<WatchHistoryEntry>,
    onItemClick: (WatchHistoryEntry) -> Unit,
    onDownloadClick: ((VideoItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("continue_watching_section")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = YouTubeRed,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Continue Watching",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        val continueWatchingRowState = rememberLazyListState()
        LazyRow(
            state = continueWatchingRowState,
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(
                items = items,
                key = { it.key },
                contentType = { "continue_watching_card" }
            ) { video ->
                ContinueWatchingCard(
                    entry = video,
                    onClick = { onItemClick(video) },
                    onDownload = onDownloadClick?.let { { it(video.video) } }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun ContinueWatchingCard(
    entry: WatchHistoryEntry,
    onClick: () -> Unit,
    onDownload: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val video = entry.video
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .width(160.dp)
            .clip(ContinueCardShape)
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
            shape = ContinueCardShape
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Resume",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(HeroBadgeShape)
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = if (video.mediaType == MediaType.TV_SHOW) {
                        "S${video.currentSeason}:E${video.currentEpisode}"
                    } else {
                        video.duration
                    },
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(entry.progressFraction)
                    .height(3.dp)
                    .background(YouTubeRed)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = video.channelName,
                    fontSize = 11.sp,
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

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }

                if (menuExpanded) {
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        if (onDownload != null) {
                            val dlText = if (video.mediaType == MediaType.TV_SHOW) {
                                "Download (S${video.currentSeason}:E${video.currentEpisode})"
                            } else {
                                "Download"
                            }
                            DropdownMenuItem(
                                text = { Text(dlText, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp)) },
                                onClick = {
                                    menuExpanded = false
                                    onDownload()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

