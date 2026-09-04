package com.example.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import coil.compose.AsyncImage
import com.example.model.ChannelItem
import com.example.model.MediaType
import com.example.model.VideoItem
import com.example.model.releaseAlertId
import com.example.ui.components.CompactRelatedVideoCard
import com.example.ui.components.StudioLogoAvatar
import com.example.ui.components.VideoCard
import com.example.ui.components.VideoCardSkeleton
import com.example.ui.theme.YTBlueVerified
import com.example.ui.theme.YouTubeRed
import com.example.util.ImagePreset
import com.example.util.rememberOptimizedImageRequest

@Composable
fun ChannelScreen(
    channel: ChannelItem,
    videos: List<VideoItem>,
    isSubscribed: Boolean,
    isLoading: Boolean,
    onBack: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onToggleSubscribe: () -> Unit,
    onSaveToWatchLater: (VideoItem) -> Unit,
    onShareVideo: (VideoItem) -> Unit,
    onAddToQueue: (VideoItem) -> Unit = {},
    watchedVideoIds: Set<String> = emptySet(),
    onToggleWatched: (VideoItem) -> Unit = {},
    onNotInterested: (VideoItem) -> Unit = {},
    onNotRecommendChannel: (VideoItem) -> Unit = {},
    releaseAlertIds: Set<String> = emptySet(),
    onToggleReleaseAlert: (VideoItem) -> Unit = {},
    onDownloadVideo: ((VideoItem) -> Unit)? = null,
    onSearchClick: () -> Unit,
    isTabletLayout: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = remember { listOf("HOME", "MOVIES", "SERIES", "ABOUT") }
    val bannerRequest = rememberOptimizedImageRequest(
        data = channel.bannerUrl,
        preset = ImagePreset.BANNER
    )
    val movies = remember(videos) { videos.filter { it.mediaType == MediaType.MOVIE } }
    val tvSeries = remember(videos) { videos.filter { it.mediaType == MediaType.TV_SHOW } }
    val spotlightItem = remember(videos) { videos.firstOrNull() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("channel_screen_${channel.id}")
    ) {
        // Channel Screen Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("channel_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                text = channel.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )

            IconButton(onClick = onSearchClick) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Channel",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            IconButton(
                onClick = {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Check out ${channel.name} on CluTube: ${channel.handle}"
                        )
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share channel via"))
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share Channel",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Keep phone channels as a single feed, but use an adaptive grid on
        // tablets so the channel page does not waste the available width on
        // one oversized card.
        LazyVerticalGrid(
            columns = if (isTabletLayout) GridCells.Adaptive(minSize = 260.dp) else GridCells.Fixed(1),
            modifier = Modifier
                .fillMaxSize()
                .testTag("channel_content_list"),
            contentPadding = PaddingValues(bottom = 72.dp),
            horizontalArrangement = Arrangement.spacedBy(if (isTabletLayout) 12.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(if (isTabletLayout) 12.dp else 0.dp)
        ) {
            // Channel Banner
            item(
                key = "channel_banner",
                contentType = "banner",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 5f)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (!channel.bannerUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = bannerRequest,
                            contentDescription = "${channel.name} Banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFF1E1E24),
                                            Color(0xFF2E3138),
                                            Color(0xFF16161A)
                                        )
                                    )
                                )
                        )
                    }

                    // Bottom scrim gradient
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.7f to Color.Transparent,
                                    1f to MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
                                )
                            )
                    )
                }
            }

            // Channel Info Header & Bio
            item(
                key = "channel_header_info",
                contentType = "header_info",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Avatar
                        StudioLogoAvatar(
                            logoUrl = channel.avatarUrl,
                            contentDescription = channel.name,
                            modifier = Modifier
                                .size(68.dp)
                                .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = channel.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified Studio",
                                    tint = YTBlueVerified,
                                    modifier = Modifier.size(15.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))

                            val subtitleParts = listOfNotNull(
                                channel.handle.takeIf { it.isNotBlank() },
                                channel.subscribers.takeIf { it.isNotBlank() },
                                if (videos.isNotEmpty()) "${videos.size} titles" else channel.videosCount.takeIf { it.isNotBlank() }
                            )
                            if (subtitleParts.isNotEmpty()) {
                                Text(
                                    text = subtitleParts.joinToString(" • "),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                            }

                            Text(
                                text = "Official Studio Network & Stream Partner",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    if (channel.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = channel.description,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 17.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Subscribe & Action Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ElevatedButton(
                            onClick = onToggleSubscribe,
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.onBackground,
                                contentColor = if (isSubscribed) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.background
                            ),
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("channel_subscribe_button")
                        ) {
                            if (isSubscribed) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = "Subscribed",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Subscribed",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = "Subscribe",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Join / Share Pill
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    val shareIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Explore ${channel.name} movies and TV series: ${channel.handle}"
                                        )
                                        type = "text/plain"
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share"))
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                        ) {
                            Text(
                                text = "Share",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Channel Tabs
            item(
                key = "channel_tab_row",
                contentType = "tab_row",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 14.dp,
                    containerColor = MaterialTheme.colorScheme.background,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    indicator = { tabPositions ->
                        if (selectedTabIndex < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                                color = MaterialTheme.colorScheme.onBackground,
                                height = 3.dp
                            )
                        }
                    },
                    divider = {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            thickness = 1.dp
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selectedTabIndex == index) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        )
                    }
                }
            }

            // Tab Contents
            when (selectedTabIndex) {
                0 -> {
                    // HOME TAB
                    if (isLoading && videos.isEmpty()) {
                        items(
                            count = 4,
                            key = { "chan_skel_$it" },
                            contentType = { "video_skeleton" }
                        ) {
                            VideoCardSkeleton()
                        }
                    } else if (videos.isEmpty()) {
                        item(
                            key = "empty_home",
                            contentType = "empty_state",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            EmptyChannelState(channelName = channel.name)
                        }
                    } else {
                        // Channel Spotlight / Featured Premiere
                        spotlightItem?.let { spotlight ->
                            item(
                                key = "spotlight_header",
                                contentType = "spotlight_card",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                val onSpotlightClick = remember(spotlight.id, onVideoClick) {
                                    { onVideoClick(spotlight) }
                                }
                                val onSpotlightSave = remember(spotlight.id, onSaveToWatchLater) {
                                    { onSaveToWatchLater(spotlight) }
                                }
                                val onSpotlightShare = remember(spotlight.id, onShareVideo) {
                                    { onShareVideo(spotlight) }
                                }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp, bottom = 6.dp)
                                ) {
                                    Text(
                                        text = "Featured Spotlight",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )

                                    VideoCard(
                                        video = spotlight,
                                        onClick = onSpotlightClick,
                                        onSaveToWatchLater = onSpotlightSave,
                                        onShare = onSpotlightShare,
                                        onDownload = onDownloadVideo?.let { { it(spotlight) } },
                                        onAddToQueue = { onAddToQueue(spotlight) },
                                        isWatched = spotlight.id in watchedVideoIds,
                                        onToggleWatched = { onToggleWatched(spotlight) },
                                        onNotInterested = { onNotInterested(spotlight) },
                                        onNotRecommendChannel = { onNotRecommendChannel(spotlight) },
                                        isReleaseAlertActive = releaseAlertId(spotlight) in releaseAlertIds,
                                        onToggleReleaseAlert = { onToggleReleaseAlert(spotlight) }
                                    )
                                }
                            }
                        }

                        // Popular Movies Horizontal Carousel
                        if (movies.isNotEmpty()) {
                            item(
                                key = "movies_carousel_section",
                                contentType = "movie_carousel",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Movie,
                                                contentDescription = "Movies",
                                                tint = YouTubeRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Blockbuster Movies",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        }

                                        Text(
                                            text = "${movies.size} titles",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(
                                            items = movies.take(10),
                                            key = { "pop_mov_${it.id}" },
                                            contentType = { "compact_movie_card" }
                                        ) { movie ->
                                            val onClick = remember(movie.id, onVideoClick) { { onVideoClick(movie) } }
                                            val onSave = remember(movie.id, onSaveToWatchLater) { { onSaveToWatchLater(movie) } }
                                            val onShare = remember(movie.id, onShareVideo) { { onShareVideo(movie) } }
                                            CompactRelatedVideoCard(
                                                video = movie,
                                                onClick = onClick,
                                                onSaveToWatchLater = onSave,
                                                onShare = onShare,
                                                onDownload = onDownloadVideo?.let { { it(movie) } },
                                                onAddToQueue = { onAddToQueue(movie) },
                                                isWatched = movie.id in watchedVideoIds,
                                                onToggleWatched = { onToggleWatched(movie) },
                                                onNotInterested = { onNotInterested(movie) },
                                                onNotRecommendChannel = { onNotRecommendChannel(movie) },
                                                modifier = Modifier.width(260.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // TV Series Horizontal Carousel
                        if (tvSeries.isNotEmpty()) {
                            item(
                                key = "series_carousel_section",
                                contentType = "series_carousel",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Tv,
                                                contentDescription = "Series",
                                                tint = Color(0xFF4285F4),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Original Series & Shows",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                        }

                                        Text(
                                            text = "${tvSeries.size} series",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        items(
                                            items = tvSeries.take(10),
                                            key = { "pop_tv_${it.id}" },
                                            contentType = { "compact_series_card" }
                                        ) { series ->
                                            val onClick = remember(series.id, onVideoClick) { { onVideoClick(series) } }
                                            val onSave = remember(series.id, onSaveToWatchLater) { { onSaveToWatchLater(series) } }
                                            val onShare = remember(series.id, onShareVideo) { { onShareVideo(series) } }
                                            CompactRelatedVideoCard(
                                                video = series,
                                                onClick = onClick,
                                                onSaveToWatchLater = onSave,
                                                onShare = onShare,
                                                onDownload = onDownloadVideo?.let { { it(series) } },
                                                onAddToQueue = { onAddToQueue(series) },
                                                isWatched = series.id in watchedVideoIds,
                                                onToggleWatched = { onToggleWatched(series) },
                                                onNotInterested = { onNotInterested(series) },
                                                onNotRecommendChannel = { onNotRecommendChannel(series) },
                                                modifier = Modifier.width(260.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Latest Releases Vertical Feed
                        item(
                            key = "all_uploads_title",
                            contentType = "section_title",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            Text(
                                text = "All Releases & Uploads",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }

                        items(
                            items = videos.drop(1),
                            key = { "chan_vid_${it.id}" },
                            contentType = { "video_card" }
                        ) { video ->
                            val onClick = remember(video.id, onVideoClick) { { onVideoClick(video) } }
                            val onSave = remember(video.id, onSaveToWatchLater) { { onSaveToWatchLater(video) } }
                            val onShare = remember(video.id, onShareVideo) { { onShareVideo(video) } }
                            VideoCard(
                                video = video,
                                onClick = onClick,
                                onSaveToWatchLater = onSave,
                                onShare = onShare,
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
                    }
                }

                1 -> {
                    // MOVIES TAB
                    if (isLoading && movies.isEmpty()) {
                        items(
                            count = 4,
                            key = { "chan_mov_skel_$it" },
                            contentType = { "video_skeleton" }
                        ) {
                            VideoCardSkeleton()
                        }
                    } else if (movies.isEmpty()) {
                        item(
                            key = "empty_movies",
                            contentType = "empty_state",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            EmptyChannelCategoryState(title = "No Movies", description = "No feature movies found for ${channel.name}.")
                        }
                    } else {
                        items(
                            items = movies,
                            key = { "tab_mov_${it.id}" },
                            contentType = { "video_card" }
                        ) { movie ->
                            val onClick = remember(movie.id, onVideoClick) { { onVideoClick(movie) } }
                            val onSave = remember(movie.id, onSaveToWatchLater) { { onSaveToWatchLater(movie) } }
                            val onShare = remember(movie.id, onShareVideo) { { onShareVideo(movie) } }
                            VideoCard(
                                video = movie,
                                onClick = onClick,
                                onSaveToWatchLater = onSave,
                                onShare = onShare,
                                onDownload = onDownloadVideo?.let { { it(movie) } },
                                onAddToQueue = { onAddToQueue(movie) },
                                isWatched = movie.id in watchedVideoIds,
                                onToggleWatched = { onToggleWatched(movie) },
                                onNotInterested = { onNotInterested(movie) },
                                onNotRecommendChannel = { onNotRecommendChannel(movie) },
                                isReleaseAlertActive = releaseAlertId(movie) in releaseAlertIds,
                                onToggleReleaseAlert = { onToggleReleaseAlert(movie) }
                            )
                        }
                    }
                }

                2 -> {
                    // SERIES TAB
                    if (isLoading && tvSeries.isEmpty()) {
                        items(
                            count = 4,
                            key = { "chan_tv_skel_$it" },
                            contentType = { "video_skeleton" }
                        ) {
                            VideoCardSkeleton()
                        }
                    } else if (tvSeries.isEmpty()) {
                        item(
                            key = "empty_series",
                            contentType = "empty_state",
                            span = { GridItemSpan(maxLineSpan) }
                        ) {
                            EmptyChannelCategoryState(title = "No TV Series", description = "No TV shows found for ${channel.name}.")
                        }
                    } else {
                        items(
                            items = tvSeries,
                            key = { "tab_tv_${it.id}" },
                            contentType = { "video_card" }
                        ) { series ->
                            val onClick = remember(series.id, onVideoClick) { { onVideoClick(series) } }
                            val onSave = remember(series.id, onSaveToWatchLater) { { onSaveToWatchLater(series) } }
                            val onShare = remember(series.id, onShareVideo) { { onShareVideo(series) } }
                            VideoCard(
                                video = series,
                                onClick = onClick,
                                onSaveToWatchLater = onSave,
                                onShare = onShare,
                                onDownload = onDownloadVideo?.let { { it(series) } },
                                onAddToQueue = { onAddToQueue(series) },
                                isWatched = series.id in watchedVideoIds,
                                onToggleWatched = { onToggleWatched(series) },
                                onNotInterested = { onNotInterested(series) },
                                onNotRecommendChannel = { onNotRecommendChannel(series) },
                                isReleaseAlertActive = releaseAlertId(series) in releaseAlertIds,
                                onToggleReleaseAlert = { onToggleReleaseAlert(series) }
                            )
                        }
                    }
                }

                3 -> {
                    // ABOUT TAB
                    item(
                        key = "about_section",
                        contentType = "about_section",
                        span = { GridItemSpan(maxLineSpan) }
                    ) {
                        ChannelAboutSection(channel = channel)
                    }
                }
            }

            item(
                key = "channel_bottom_spacer",
                contentType = "spacer",
                span = { GridItemSpan(maxLineSpan) }
            ) {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }
}

@Composable
private fun ChannelAboutSection(channel: ChannelItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "About ${channel.name}",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = channel.description,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Channel details",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (channel.handle.isNotBlank()) {
            AboutInfoRow(label = "Handle", value = channel.handle)
        }
        if (channel.subscribers.isNotBlank()) {
            AboutInfoRow(label = "Subscribers", value = channel.subscribers)
        }
        if (channel.totalViews.isNotBlank()) {
            AboutInfoRow(label = "Total views", value = channel.totalViews)
        }
        if (channel.joinedDate.isNotBlank()) {
            AboutInfoRow(label = "Joined", value = channel.joinedDate)
        }
        if (channel.location.isNotBlank()) {
            AboutInfoRow(label = "Country", value = channel.location)
        }
        AboutInfoRow(label = "Streaming API", value = "Multi-Server Embed")
    }
}

@Composable
private fun AboutInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun EmptyChannelState(channelName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No releases available for $channelName",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Check back soon for new movie and series premieres.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun EmptyChannelCategoryState(title: String, description: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = description,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
