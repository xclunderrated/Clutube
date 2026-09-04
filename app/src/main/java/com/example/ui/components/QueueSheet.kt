package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.model.VideoItem
import com.example.model.playbackKey
import com.example.ui.theme.YouTubeRed
import com.example.util.ImagePreset
import com.example.util.rememberOptimizedImageRequest
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueSheet(
    queue: List<VideoItem>,
    currentVideo: VideoItem?,
    onDismiss: () -> Unit,
    onPlay: (VideoItem) -> Unit,
    onRemove: (String) -> Unit,
    onMove: (Int, Int) -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = modifier.testTag("queue_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = YouTubeRed)
                Text(
                    text = "Queue",
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (queue.isNotEmpty()) {
                    TextButton(onClick = onClear) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Clear", modifier = Modifier.padding(start = 4.dp))
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close queue")
                }
            }

            if (currentVideo != null) {
                Text(
                    text = "Playing",
                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = YouTubeRed
                )
                QueueVideoRow(video = currentVideo, isPlaying = true, onClick = {})
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            }

            Text(
                text = "Up next",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )

            if (queue.isEmpty()) {
                Text(
                    text = "Add videos from any card to build your queue.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(queue, key = { _, video -> "queue_${video.playbackKey()}" }) { index, video ->
                        QueueVideoRow(
                            video = video,
                            onClick = { onPlay(video) },
                            onRemove = { onRemove(video.playbackKey()) },
                            onMove = { delta ->
                                val target = (index + delta).coerceIn(0, queue.lastIndex)
                                if (target != index) onMove(index, target)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueVideoRow(
    video: VideoItem,
    isPlaying: Boolean = false,
    onClick: () -> Unit,
    onRemove: (() -> Unit)? = null,
    onMove: ((Int) -> Unit)? = null
) {
    val imageRequest = rememberOptimizedImageRequest(
        data = video.thumbnailUrl,
        preset = ImagePreset.COMPACT_THUMBNAIL
    )
    var isDragging by remember(video.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isPlaying) YouTubeRed.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .then(
                if (onMove != null) {
                    Modifier.pointerInput(video.id) {
                        var dragDistance = 0f
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                isDragging = true
                                dragDistance = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragDistance += amount.y
                                val rowHeight = 58f
                                if (abs(dragDistance) >= rowHeight) {
                                    onMove(if (dragDistance > 0f) 1 else -1)
                                    dragDistance -= if (dragDistance > 0f) rowHeight else -rowHeight
                                }
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false }
                        )
                    }
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FittedMediaThumbnail(
            thumbnailUrl = video.thumbnailUrl,
            backdropUrl = video.backdropUrl,
            contentDescription = video.title,
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(16f / 9f),
            imagePreset = ImagePreset.COMPACT_THUMBNAIL,
            isWatched = false,
            shape = RoundedCornerShape(5.dp)
        ) {
            if (isPlaying) {
                Icon(Icons.Default.PauseCircle, contentDescription = null, tint = YouTubeRed, modifier = Modifier.size(28.dp))
            }
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(
                text = video.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = video.channelName,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (onMove != null) {
            Icon(Icons.Default.DragHandle, contentDescription = "Drag to reorder", tint = if (isDragging) YouTubeRed else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onRemove != null) {
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, contentDescription = "Remove from queue")
            }
        }
    }
}
