package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VideoItem
import com.example.model.MediaType
import com.example.model.SearchHistoryItem
import com.example.model.playbackKey
import com.example.model.releaseAlertId
import com.example.ui.components.VideoCard
import com.example.ui.components.VideoCardSkeleton
import com.example.ui.theme.YouTubeRed

private val TrendingSearches = listOf(
    "Dune: Part Two",
    "Stranger Things Season 4",
    "Oppenheimer 4K",
    "Breaking Bad",
    "The Dark Knight",
    "The Last of Us",
    "Spider-Man: Across the Spider-Verse",
    "Interstellar",
    "Game of Thrones",
    "Avatar: The Way of Water"
)

private enum class SearchTypeFilter { ALL, MOVIES, SERIES }
private enum class SearchDurationFilter { ANY, SHORT, MEDIUM, LONG }
private enum class SearchSort { RELEVANCE, NEWEST, RATING, TITLE }

@Composable
fun SearchScreen(
    query: String,
    searchResults: List<VideoItem>,
    isSearchLoading: Boolean = false,
    searchErrorMessage: String? = null,
    isSearchCacheStale: Boolean = false,
    searchHistory: List<SearchHistoryItem> = emptyList(),
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onBack: () -> Unit,
    onVideoClick: (VideoItem) -> Unit,
    onSaveToWatchLater: (VideoItem) -> Unit,
    onShare: (VideoItem) -> Unit,
    onAddToQueue: (VideoItem) -> Unit = {},
    watchedVideoIds: Set<String> = emptySet(),
    onToggleWatched: (VideoItem) -> Unit = {},
    onNotInterested: (VideoItem) -> Unit = {},
    onNotRecommendChannel: (VideoItem) -> Unit = {},
    releaseAlertIds: Set<String> = emptySet(),
    onToggleReleaseAlert: (VideoItem) -> Unit = {},
    onDownloadVideo: ((VideoItem) -> Unit)? = null,
    onRemoveSearchHistory: (String) -> Unit = {},
    onClearSearchHistory: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var typeFilter by remember { mutableStateOf(SearchTypeFilter.ALL) }
    var durationFilter by remember { mutableStateOf(SearchDurationFilter.ANY) }
    var sort by remember { mutableStateOf(SearchSort.RELEVANCE) }
    val filteredResults = remember(searchResults, typeFilter, durationFilter, sort) {
        searchResults
            .asSequence()
            .filter { video ->
                when (typeFilter) {
                    SearchTypeFilter.ALL -> true
                    SearchTypeFilter.MOVIES -> video.mediaType == com.example.model.MediaType.MOVIE
                    SearchTypeFilter.SERIES -> video.mediaType == com.example.model.MediaType.TV_SHOW
                }
            }
            .filter { video ->
                val duration = searchDurationSeconds(video.duration)
                when (durationFilter) {
                    SearchDurationFilter.ANY -> true
                    SearchDurationFilter.SHORT -> duration in 1..239
                    SearchDurationFilter.MEDIUM -> duration in 240..1200
                    SearchDurationFilter.LONG -> duration > 1200
                }
            }
            .let { sequence ->
                when (sort) {
                    SearchSort.RELEVANCE -> sequence
                    SearchSort.NEWEST -> sequence.sortedByDescending { it.publishedAt }
                    SearchSort.RATING -> sequence.sortedByDescending { it.rating ?: -1.0 }
                    SearchSort.TITLE -> sequence.sortedBy { it.title.lowercase() }
                }
            }
            .toList()
    }
    val movieResults = remember(filteredResults) {
        filteredResults.filter { it.mediaType == MediaType.MOVIE }
    }
    val seriesResults = remember(filteredResults) {
        filteredResults.filter { it.mediaType == MediaType.TV_SHOW }
    }
    val youtubeStyleResults = remember(filteredResults) {
        filteredResults.filter { it.mediaType != MediaType.MOVIE && it.mediaType != MediaType.TV_SHOW }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .testTag("search_screen")
    ) {
        // Top Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        "Search movies, series, titles...",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("search_input_field"),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = YouTubeRed,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            onQueryChange("")
                            onSearch("")
                        }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query) })
            )

            Spacer(modifier = Modifier.width(6.dp))

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice Search",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Live Search Loading Bar
        AnimatedVisibility(visible = isSearchLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = YouTubeRed,
                trackColor = MaterialTheme.colorScheme.background
            )
        }

        if (query.isNotBlank()) {
            SearchFilterRow(
                typeFilter = typeFilter,
                durationFilter = durationFilter,
                sort = sort,
                onTypeFilterChange = { typeFilter = it },
                onDurationFilterChange = { durationFilter = it },
                onSortChange = { sort = it }
            )
        }

        if (searchErrorMessage != null) {
            Text(
                text = searchErrorMessage,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (isSearchCacheStale && query.isNotBlank()) {
            Text(
                text = "Showing saved results while we refresh.",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Search Results or Suggestions
        if (query.isBlank()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (searchHistory.isNotEmpty()) {
                    item(key = "recent_searches_header", contentType = "header") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent searches",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Clear",
                                modifier = Modifier
                                    .clickable(onClick = onClearSearchHistory)
                                    .padding(6.dp)
                                    .testTag("clear_search_history"),
                                fontSize = 11.sp,
                                color = YouTubeRed,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    items(
                        items = searchHistory,
                        key = { "history_${it.query}" },
                        contentType = { "search_history" }
                    ) { historyItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onQueryChange(historyItem.query)
                                    onSearch(historyItem.query)
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = historyItem.query,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onRemoveSearchHistory(historyItem.query) },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(Icons.Default.Clear, contentDescription = "Remove search")
                            }
                        }
                    }
                }
                item(key = "trending_header", contentType = "header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalMovies,
                            contentDescription = null,
                            tint = YouTubeRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Trending TMDb Movies & Series",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                items(
                    items = TrendingSearches,
                    key = { it },
                    contentType = { "trending_search" }
                ) { suggestion ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onQueryChange(suggestion)
                                onSearch(suggestion)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = suggestion,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.NorthWest,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        } else if (filteredResults.isEmpty() && isSearchLoading) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp)
            ) {
                items(count = 4, contentType = { "video_skeleton" }) {
                    VideoCardSkeleton()
                }
            }
        } else if (filteredResults.isEmpty() && !isSearchLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No movies or series found for \"$query\"",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp)
            ) {
                item {
                    Text(
                        text = "${filteredResults.size} results",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                searchResultSection(
                    title = "Movies",
                    results = movieResults,
                    onVideoClick = onVideoClick,
                    onSaveToWatchLater = onSaveToWatchLater,
                    onShare = onShare,
                    onAddToQueue = onAddToQueue,
                    watchedVideoIds = watchedVideoIds,
                    onToggleWatched = onToggleWatched,
                    onNotInterested = onNotInterested,
                    onNotRecommendChannel = onNotRecommendChannel,
                    releaseAlertIds = releaseAlertIds,
                    onToggleReleaseAlert = onToggleReleaseAlert,
                    onDownloadVideo = onDownloadVideo
                )
                searchResultSection(
                    title = "TV series",
                    results = seriesResults,
                    onVideoClick = onVideoClick,
                    onSaveToWatchLater = onSaveToWatchLater,
                    onShare = onShare,
                    onAddToQueue = onAddToQueue,
                    watchedVideoIds = watchedVideoIds,
                    onToggleWatched = onToggleWatched,
                    onNotInterested = onNotInterested,
                    onNotRecommendChannel = onNotRecommendChannel,
                    releaseAlertIds = releaseAlertIds,
                    onToggleReleaseAlert = onToggleReleaseAlert,
                    onDownloadVideo = onDownloadVideo
                )
                searchResultSection(
                    title = "YouTube-style catalog",
                    results = youtubeStyleResults,
                    onVideoClick = onVideoClick,
                    onSaveToWatchLater = onSaveToWatchLater,
                    onShare = onShare,
                    onAddToQueue = onAddToQueue,
                    watchedVideoIds = watchedVideoIds,
                    onToggleWatched = onToggleWatched,
                    onNotInterested = onNotInterested,
                    onNotRecommendChannel = onNotRecommendChannel,
                    releaseAlertIds = releaseAlertIds,
                    onToggleReleaseAlert = onToggleReleaseAlert,
                    onDownloadVideo = onDownloadVideo
                )
            }
        }
    }
}

private fun LazyListScope.searchResultSection(
    title: String,
    results: List<VideoItem>,
    onVideoClick: (VideoItem) -> Unit,
    onSaveToWatchLater: (VideoItem) -> Unit,
    onShare: (VideoItem) -> Unit,
    onAddToQueue: (VideoItem) -> Unit,
    watchedVideoIds: Set<String>,
    onToggleWatched: (VideoItem) -> Unit,
    onNotInterested: (VideoItem) -> Unit,
    onNotRecommendChannel: (VideoItem) -> Unit,
    releaseAlertIds: Set<String>,
    onToggleReleaseAlert: (VideoItem) -> Unit,
    onDownloadVideo: ((VideoItem) -> Unit)? = null
) {
    if (results.isEmpty()) return
    item(key = "search_section_$title", contentType = "search_section_header") {
        Text(
            text = "$title (${results.size})",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
    items(
        items = results,
        key = { "search_${it.playbackKey()}" },
        contentType = { "video_card" }
    ) { video ->
        VideoCard(
            video = video,
            onClick = { onVideoClick(video) },
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
}

@Composable
private fun SearchFilterRow(
    typeFilter: SearchTypeFilter,
    durationFilter: SearchDurationFilter,
    sort: SearchSort,
    onTypeFilterChange: (SearchTypeFilter) -> Unit,
    onDurationFilterChange: (SearchDurationFilter) -> Unit,
    onSortChange: (SearchSort) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchFilterMenu(
            label = when (typeFilter) {
                SearchTypeFilter.ALL -> "Type: All"
                SearchTypeFilter.MOVIES -> "Type: Movies"
                SearchTypeFilter.SERIES -> "Type: Series"
            },
            options = listOf("All", "Movies", "Series"),
            selectedIndex = typeFilter.ordinal,
            onSelected = { index -> onTypeFilterChange(SearchTypeFilter.values()[index]) }
        )
        SearchFilterMenu(
            label = when (durationFilter) {
                SearchDurationFilter.ANY -> "Duration: Any"
                SearchDurationFilter.SHORT -> "Duration: <4 min"
                SearchDurationFilter.MEDIUM -> "Duration: 4-20 min"
                SearchDurationFilter.LONG -> "Duration: >20 min"
            },
            options = listOf("Any", "<4 min", "4-20 min", ">20 min"),
            selectedIndex = durationFilter.ordinal,
            onSelected = { index -> onDurationFilterChange(SearchDurationFilter.values()[index]) }
        )
        SearchFilterMenu(
            label = when (sort) {
                SearchSort.RELEVANCE -> "Sort: Relevance"
                SearchSort.NEWEST -> "Sort: Newest"
                SearchSort.RATING -> "Sort: Rating"
                SearchSort.TITLE -> "Sort: Title"
            },
            options = listOf("Relevance", "Newest", "Rating", "Title"),
            selectedIndex = sort.ordinal,
            onSelected = { index -> onSortChange(SearchSort.values()[index]) }
        )
    }
}

@Composable
private fun SearchFilterMenu(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    var expanded by remember(label) { mutableStateOf(false) }
    Box {
        Text(
            text = label,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelected(index)
                    },
                    trailingIcon = if (index == selectedIndex) {
                        { Text("✓", color = YouTubeRed) }
                    } else null
                )
            }
        }
    }
}

private fun searchDurationSeconds(rawDuration: String): Int {
    val value = rawDuration.trim().lowercase()
    if (value.isBlank() || value == "live" || value == "tv series") return 0
    if (value.contains(":")) {
        val parts = value.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size == 2) return parts[0] * 60 + parts[1]
        if (parts.size == 3) return parts[0] * 3600 + parts[1] * 60 + parts[2]
    }
    val hours = Regex("(\\d+)\\s*h").find(value)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
    val minutes = Regex("(\\d+)\\s*m").find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: Regex("(\\d+)\\s*min").find(value)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: 0
    return hours * 3600 + minutes * 60
}
