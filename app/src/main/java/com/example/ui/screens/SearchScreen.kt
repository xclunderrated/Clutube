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
import androidx.compose.foundation.lazy.animateItem
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VideoItem
import com.example.model.MediaType
import com.example.model.SearchHistoryItem
import com.example.model.playbackKey
import com.example.model.titleGroupKey
import com.example.model.releaseAlertId
import com.example.ui.components.VideoCard
import com.example.ui.components.VideoCardSkeleton
import com.example.ui.theme.YouTubeRed

private val PopularSearchSuggestions = listOf(
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
    savedVideoIds: Set<String> = emptySet(),
    progressFractions: Map<String, Float> = emptyMap(),
    continueLabels: Map<String, String> = emptyMap(),
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
    val haptics = LocalHapticFeedback.current
    val hasActiveFilters = typeFilter != SearchTypeFilter.ALL ||
        durationFilter != SearchDurationFilter.ANY ||
        sort != SearchSort.RELEVANCE
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
                // Movies/series use runtimeMinutes so they are not hidden by
                // YouTube-style duration thresholds. 0 = unknown -> always pass
                // except when a specific bucket is requested and unknown.
                val duration = searchDurationSeconds(video)
                when (durationFilter) {
                    SearchDurationFilter.ANY -> true
                    SearchDurationFilter.SHORT -> duration in 1..2399
                    SearchDurationFilter.MEDIUM -> duration in 2400..5400
                    SearchDurationFilter.LONG -> duration > 5400 || duration == 0
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
    // Unified ordering: movies + series intermixed by default (user request).
    // Type filter narrows; we never split ALL into separate Movies/TV sections.
    val unifiedResults = remember(filteredResults) { filteredResults }

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

            // Voice search isn't supported locally, so no mic button.
            // When text is present this acts as an explicit search submit.
            if (query.isNotBlank()) {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSearch(query)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("voice_search_button")
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
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
                hasActiveFilters = hasActiveFilters,
                onTypeFilterChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    typeFilter = it
                },
                onDurationFilterChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    durationFilter = it
                },
                onSortChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    sort = it
                },
                onClearFilters = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    typeFilter = SearchTypeFilter.ALL
                    durationFilter = SearchDurationFilter.ANY
                    sort = SearchSort.RELEVANCE
                }
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
                            text = "Popular searches",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                items(
                    items = PopularSearchSuggestions,
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
                items(
                    count = 3,
                    key = { "search_skel_$it" },
                    contentType = { "video_skeleton" }
                ) {
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No movies or series found for \"$query\"",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (hasActiveFilters) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(
                            onClick = {
                                typeFilter = SearchTypeFilter.ALL
                                durationFilter = SearchDurationFilter.ANY
                                sort = SearchSort.RELEVANCE
                            },
                            modifier = Modifier.testTag("clear_search_filters")
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterListOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear filters")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 4.dp)
            ) {
                item(key = "search_header", contentType = "header") {
                    val scopeLabel = when (typeFilter) {
                        SearchTypeFilter.ALL -> "Movies & series"
                        SearchTypeFilter.MOVIES -> "Movies"
                        SearchTypeFilter.SERIES -> "Series"
                    }
                    Text(
                        text = "${unifiedResults.size} results • $scopeLabel",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                // Unified Movies + Series feed (no split sections unless filtered).
                items(
                    items = unifiedResults,
                    key = { "search_${it.playbackKey()}" },
                    contentType = { "video_card" }
                ) { video ->
                    val key = video.playbackKey()
                    val onClick = remember(video.id, onVideoClick) { { onVideoClick(video) } }
                    val onSave = remember(video.id, onSaveToWatchLater) { { onSaveToWatchLater(video) } }
                    val onShareLambda = remember(video.id, onShare) { { onShare(video) } }
                    val onDownloadLambda = remember(video.id, onDownloadVideo) {
                        onDownloadVideo?.let { dl -> { dl(video) } }
                    }
                    val onQueue = remember(video.id, onAddToQueue) { { onAddToQueue(video) } }
                    val onWatched = remember(video.id, onToggleWatched) { { onToggleWatched(video) } }
                    val onNotInt = remember(video.id, onNotInterested) { { onNotInterested(video) } }
                    val onNotRec = remember(video.id, onNotRecommendChannel) { { onNotRecommendChannel(video) } }
                    val onAlert = remember(video.id, onToggleReleaseAlert) { { onToggleReleaseAlert(video) } }
                    VideoCard(
                        video = video,
                        onClick = onClick,
                        onSaveToWatchLater = onSave,
                        onShare = onShareLambda,
                        onDownload = onDownloadLambda,
                        onAddToQueue = onQueue,
                        isWatched = video.id in watchedVideoIds,
                        isSaved = video.id in savedVideoIds,
                        progressFraction = progressFractions[key] ?: progressFractions[video.titleGroupKey()],
                        continueLabel = continueLabels[key] ?: continueLabels[video.titleGroupKey()],
                        onToggleWatched = onWatched,
                        onNotInterested = onNotInt,
                        onNotRecommendChannel = onNotRec,
                        isReleaseAlertActive = releaseAlertId(video) in releaseAlertIds,
                        onToggleReleaseAlert = onAlert,
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchFilterRow(
    typeFilter: SearchTypeFilter,
    durationFilter: SearchDurationFilter,
    sort: SearchSort,
    hasActiveFilters: Boolean,
    onTypeFilterChange: (SearchTypeFilter) -> Unit,
    onDurationFilterChange: (SearchDurationFilter) -> Unit,
    onSortChange: (SearchSort) -> Unit,
    onClearFilters: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SearchFilterMenu(
            label = when (typeFilter) {
                SearchTypeFilter.ALL -> "Type: All"
                SearchTypeFilter.MOVIES -> "Type: Movies"
                SearchTypeFilter.SERIES -> "Type: Series"
            },
            options = listOf("All", "Movies", "Series"),
            selectedIndex = typeFilter.ordinal,
            contentDescription = "Filter by type",
            onSelected = { index -> onTypeFilterChange(SearchTypeFilter.values()[index]) }
        )
        SearchFilterMenu(
            label = when (durationFilter) {
                SearchDurationFilter.ANY -> "Runtime: Any"
                SearchDurationFilter.SHORT -> "Runtime: <40 min"
                SearchDurationFilter.MEDIUM -> "Runtime: 40-90 min"
                SearchDurationFilter.LONG -> "Runtime: >90 min"
            },
            options = listOf("Any", "<40 min", "40-90 min", ">90 min"),
            selectedIndex = durationFilter.ordinal,
            contentDescription = "Filter by runtime",
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
            contentDescription = "Sort results",
            onSelected = { index -> onSortChange(SearchSort.values()[index]) }
        )
        if (hasActiveFilters) {
            Text(
                text = "Clear",
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(YouTubeRed.copy(alpha = 0.12f))
                    .clickable(
                        role = Role.Button,
                        onClickLabel = "Clear search filters",
                        onClick = onClearFilters
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .heightIn(min = 28.dp)
                    .testTag("clear_search_filters_row"),
                color = YouTubeRed,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SearchFilterMenu(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    contentDescription: String,
    onSelected: (Int) -> Unit
) {
    var expanded by remember(label) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    Box {
        Text(
            text = label,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(
                    role = Role.DropdownList,
                    onClickLabel = contentDescription,
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        expanded = true
                    }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .heightIn(min = 28.dp),
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

private fun searchDurationSeconds(video: VideoItem): Int {
    // Movies/series carry authoritative runtimeMinutes — use it so series are
    // not hidden by YouTube-style thresholds. 0 = unknown (passes LONG, hidden
    // from SHORT/MEDIUM to avoid false positives).
    if (video.mediaType == MediaType.MOVIE || video.mediaType == MediaType.TV_SHOW) {
        val runtime = (video.runtimeMinutes ?: 0).coerceAtLeast(0)
        if (runtime > 0) return runtime * 60
    }
    val value = video.duration.trim().lowercase()
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
