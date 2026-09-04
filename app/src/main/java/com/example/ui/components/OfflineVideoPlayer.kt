package com.example.ui.components

import android.media.MediaPlayer
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.YouTubeRed
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/**
 * Full-screen offline video player for playing locally downloaded movies and episodes.
 * Functions 100% offline with zero network connectivity.
 */
@Composable
fun OfflineVideoPlayer(
    title: String,
    subtitle: String?,
    localFilePath: String,
    serverName: String? = null,
    subtitleCc: String? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPositionMs by remember { mutableIntStateOf(0) }
    var durationMs by remember { mutableIntStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var isDraggingSlider by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isCcActive by remember { mutableStateOf(subtitleCc != null && !subtitleCc.contains("Off", ignoreCase = true)) }
    var ccToastMessage by remember { mutableStateOf<String?>(null) }

    var videoViewInstance: VideoView? by remember { mutableStateOf(null) }

    LaunchedEffect(ccToastMessage) {
        if (ccToastMessage != null) {
            delay(2000L)
            ccToastMessage = null
        }
    }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(lastInteractionTime, isPlaying) {
        if (isPlaying && controlsVisible && !isDraggingSlider) {
            delay(4000L)
            controlsVisible = false
        }
    }

    // Position tracking loop
    LaunchedEffect(isPlaying, isDraggingSlider) {
        while (true) {
            if (!isDraggingSlider && videoViewInstance != null) {
                videoViewInstance?.let { view ->
                    if (view.isPlaying) {
                        currentPositionMs = view.currentPosition
                        durationMs = view.duration.coerceAtLeast(1)
                        sliderPosition = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    }
                }
            }
            delay(500L)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            videoViewInstance?.stopPlayback()
            videoViewInstance = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
                lastInteractionTime = System.currentTimeMillis()
            }
            .testTag("offline_video_player")
    ) {
        // VideoView host
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    val file = File(localFilePath)
                    if (file.exists()) {
                        setVideoURI(Uri.fromFile(file))
                    }

                    setOnPreparedListener { mp ->
                        isBuffering = false
                        durationMs = mp.duration
                        mp.start()
                        isPlaying = true
                        mp.setOnVideoSizeChangedListener { _, _, _ -> }
                    }

                    setOnCompletionListener {
                        isPlaying = false
                        controlsVisible = true
                    }

                    setOnErrorListener { _, _, _ ->
                        isBuffering = false
                        true
                    }
                    videoViewInstance = this
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading spinner while opening video file
        if (isBuffering) {
            CircularProgressIndicator(
                color = YouTubeRed,
                modifier = Modifier
                    .size(48.dp)
                    .align(Alignment.Center)
            )
        }

        // Overlay Controls
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.testTag("offline_player_back_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Close player",
                            tint = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!subtitle.isNullOrBlank()) {
                                Text(
                                    text = subtitle,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.75f),
                                    maxLines = 1
                                )
                            }
                            if (!serverName.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.White.copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = serverName,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    // CC Toggle Button
                    if (!subtitleCc.isNullOrBlank() && !subtitleCc.contains("Off", ignoreCase = true)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isCcActive) YouTubeRed else Color.White.copy(alpha = 0.2f))
                                .clickable {
                                    isCcActive = !isCcActive
                                    ccToastMessage = if (isCcActive) "Subtitles: $subtitleCc" else "Subtitles: Off"
                                    lastInteractionTime = System.currentTimeMillis()
                                }
                                .padding(horizontal = 7.dp, vertical = 4.dp)
                                .testTag("offline_cc_toggle")
                        ) {
                            Text(
                                text = "CC",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Offline Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF2E7D32))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "OFFLINE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // Center Play/Pause & Rewind/Forward Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(36.dp)
                ) {
                    // Seek -10s
                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            videoViewInstance?.let { view ->
                                val target = (view.currentPosition - 10000).coerceAtLeast(0)
                                view.seekTo(target)
                                currentPositionMs = target
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .testTag("offline_player_replay_10")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Replay10,
                            contentDescription = "Rewind 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Main Play / Pause Button
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(YouTubeRed)
                            .clickable {
                                lastInteractionTime = System.currentTimeMillis()
                                videoViewInstance?.let { view ->
                                    if (view.isPlaying) {
                                        view.pause()
                                        isPlaying = false
                                    } else {
                                        view.start()
                                        isPlaying = true
                                    }
                                }
                            }
                            .testTag("offline_player_play_pause_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp)
                        )
                    }

                    // Seek +10s
                    IconButton(
                        onClick = {
                            lastInteractionTime = System.currentTimeMillis()
                            videoViewInstance?.let { view ->
                                val target = (view.currentPosition + 10000).coerceAtMost(durationMs)
                                view.seekTo(target)
                                currentPositionMs = target
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .testTag("offline_player_forward_10")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Forward10,
                            contentDescription = "Forward 10 seconds",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Bottom Timeline & Scrubber Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 20.dp)
                ) {
                    Slider(
                        value = if (isDraggingSlider) sliderPosition else (currentPositionMs.toFloat() / durationMs.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f),
                        onValueChange = { newValue ->
                            isDraggingSlider = true
                            sliderPosition = newValue
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        onValueChangeFinished = {
                            isDraggingSlider = false
                            val targetMs = (sliderPosition * durationMs).toInt()
                            videoViewInstance?.seekTo(targetMs)
                            currentPositionMs = targetMs
                            lastInteractionTime = System.currentTimeMillis()
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = YouTubeRed,
                            activeTrackColor = YouTubeRed,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .testTag("offline_player_slider")
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTimeMs(currentPositionMs),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                        Text(
                            text = formatTimeMs(durationMs),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }
        }

        // Subtitle / CC Status Toast notification
        AnimatedVisibility(
            visible = ccToastMessage != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 80.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = ccToastMessage ?: "",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun formatTimeMs(millis: Int): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes % 60, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
