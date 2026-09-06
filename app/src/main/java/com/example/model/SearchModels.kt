package com.example.model

import androidx.compose.runtime.Immutable

enum class SearchResultKind {
    MOVIE,
    TV,
    YOUTUBE_STYLE
}

@Immutable
data class SearchResultGroup(
    val kind: SearchResultKind,
    val title: String,
    val results: List<VideoItem>
)

@Immutable
data class SearchHistoryItem(
    val query: String,
    val lastUsedAtMillis: Long
)
