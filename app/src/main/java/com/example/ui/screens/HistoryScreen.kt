package com.example.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.MediaType
import com.example.model.VideoItem
import com.example.model.WatchHistoryEntry
import com.example.model.formatPlaybackTime
import com.example.ui.components.FittedMediaThumbnail
import com.example.ui.theme.YouTubeRed
import com.example.util.ImagePreset
import com.example.util.rememberThumbnailRequestWithFallback
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    entries: List<WatchHistoryEntry>,
    onBack: () -> Unit,
    onResume: (WatchHistoryEntry) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit,
    onToggleWatched: (VideoItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("history_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = if (selectedKeys.isEmpty()) "History" else "${selectedKeys.size} selected",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
            )
            if (selectedKeys.isNotEmpty()) {
                IconButton(
                    onClick = {
                        selectedKeys.forEach(onRemove)
                        selectedKeys = emptySet()
                    },
                    modifier = Modifier.testTag("remove_selected_history")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove selected",
                        tint = YouTubeRed
                    )
                }
                TextButton(
                    onClick = {
                        entries
                            .filter { it.key in selectedKeys }
                            .forEach { onToggleWatched(it.video) }
                        selectedKeys = emptySet()
                    },
                    modifier = Modifier.testTag("mark_selected_history_watched")
                ) {
                    Text(text = "Watched", color = YouTubeRed)
                }
            } else if (entries.isNotEmpty()) {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.testTag("clear_history_button")
                ) {
                    Text(text = "Clear", color = YouTubeRed)
                }
            }
        }

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Your watch history is empty.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 24.dp
                )
            ) {
                items(entries, key = { it.key }, contentType = { "history_entry" }) { entry ->
                    HistoryEntryRow(
                        entry = entry,
                        isSelected = entry.key in selectedKeys,
                        onClick = {
                            if (selectedKeys.isNotEmpty()) {
                                selectedKeys = selectedKeys.toggle(entry.key)
                            } else {
                                onResume(entry)
                            }
                        },
                        onLongClick = {
                            selectedKeys = selectedKeys.toggle(entry.key)
                        },
                        onSwipeLeft = { onRemove(entry.key) },
                        onSwipeRight = { onResume(entry) },
                        onRemove = { onRemove(entry.key) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryEntryRow(
    entry: WatchHistoryEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onRemove: () -> Unit
) {
    val video = entry.video
    val (imageRequest, onImageError) = rememberThumbnailRequestWithFallback(
        primaryUrl = video.thumbnailUrl,
        fallbackUrl = video.backdropUrl,
        preset = ImagePreset.COMPACT_THUMBNAIL
    )
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 110.dp.toPx() }
    var dragOffset by remember(entry.key) { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    dragOffset < 0f -> YouTubeRed.copy(alpha = 0.18f)
                    dragOffset > 0f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else -> Color.Transparent
                }
            )
            .pointerInput(entry.key) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragOffset <= -swipeThreshold -> onSwipeLeft()
                            dragOffset >= swipeThreshold -> onSwipeRight()
                        }
                        dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f }
                ) { change, amount ->
                    change.consume()
                    dragOffset = (dragOffset + amount).coerceIn(
                        -with(density) { 170.dp.toPx() },
                        with(density) { 170.dp.toPx() }
                    )
                }
            }
    ) {
        if (dragOffset < 0f || dragOffset > 0f) {
            Text(
                text = if (dragOffset < 0f) "Remove" else "Watch",
                color = if (dragOffset < 0f) YouTubeRed else MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(if (dragOffset < 0f) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(horizontal = 14.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(dragOffset.roundToInt(), 0) }
                .background(
                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.background
                )
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FittedMediaThumbnail(
                thumbnailUrl = video.thumbnailUrl,
                backdropUrl = video.backdropUrl,
                contentDescription = video.title,
                modifier = Modifier
                    .width(140.dp)
                    .aspectRatio(16f / 9f),
                imagePreset = ImagePreset.COMPACT_THUMBNAIL,
                isWatched = false,
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(entry.progressFraction)
                        .height(3.dp)
                        .background(YouTubeRed)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 19.sp,
                    letterSpacing = (-0.1).sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (video.mediaType == MediaType.TV_SHOW) {
                        "S${video.currentSeason}:E${video.currentEpisode}"
                    } else {
                        video.channelName
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Normal,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.durationSeconds > 0L) {
                    Text(
                        text = if (entry.completed) {
                            "Completed"
                        } else {
                            "${formatPlaybackTime(entry.positionSeconds)} watched - " +
                                "${formatPlaybackTime(entry.remainingSeconds)} left"
                        },
                        fontSize = 11.sp,
                        color = YouTubeRed,
                        maxLines = 1
                    )
                }
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.testTag("remove_history_${entry.key}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Remove from history",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (contains(value)) this - value else this + value
