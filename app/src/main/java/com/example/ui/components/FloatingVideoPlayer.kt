package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.VideoItem
import kotlin.math.roundToInt

@Composable
fun FloatingVideoPlayer(
    video: VideoItem,
    selectedServerId: String,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onSelectServer: (String) -> Unit,
    onOpenServerDialog: () -> Unit = {},
    isPlaying: Boolean = true,
    onTogglePlayPause: () -> Unit = {},
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    resumePositionSeconds: Double = 0.0,
    progressFraction: Float = 0f,
    modifier: Modifier = Modifier
) {
    var isEnlarged by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val isTablet = maxWidth >= 600.dp

        // Keep the in-app mini player close to Android PiP proportions while
        // leaving enough room for the watch page underneath.
        val windowWidthDp = if (isTablet) {
            (maxWidth * if (isEnlarged) 0.48f else 0.40f).coerceIn(
                if (isEnlarged) 400.dp else 340.dp,
                if (isEnlarged) 560.dp else 480.dp
            )
        } else if (isEnlarged) {
            (maxWidth * 0.78f).coerceIn(260.dp, 360.dp)
        } else {
            (maxWidth * 0.66f).coerceIn(220.dp, 300.dp)
        }
        val windowWidthPx = with(density) { windowWidthDp.toPx() }
        val windowHeightPx = windowWidthPx * (9f / 16f)

        // Default initial position in bottom right above navigation bar
        val defaultOffsetX = (screenWidthPx - windowWidthPx - with(density) { 16.dp.toPx() }).coerceAtLeast(0f)
        val defaultOffsetY = (screenHeightPx - windowHeightPx - with(density) { 90.dp.toPx() }).coerceAtLeast(0f)

        var offsetX by remember { mutableFloatStateOf(defaultOffsetX) }
        var offsetY by remember { mutableFloatStateOf(defaultOffsetY) }

        // Keep inside screen bounds
        val clampedX = offsetX.coerceIn(0f, (screenWidthPx - windowWidthPx).coerceAtLeast(0f))
        val clampedY = offsetY.coerceIn(
            with(density) { 40.dp.toPx() },
            (screenHeightPx - windowHeightPx - with(density) { 50.dp.toPx() }).coerceAtLeast(0f)
        )

        Box(
            modifier = Modifier
                .offset { IntOffset(clampedX.roundToInt(), clampedY.roundToInt()) }
                .width(windowWidthDp)
                .aspectRatio(16f / 9f)
                .shadow(elevation = 14.dp, shape = RoundedCornerShape(10.dp), clip = false)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.28f), RoundedCornerShape(10.dp))
                .testTag("floating_pip_window")
        ) {
            // Live Stream Video Player with internal touches disabled so clicks don't hit web player UI
            YouTubePlayer(
                video = video,
                selectedServerId = selectedServerId,
                onSelectServer = onSelectServer,
                isTouchEnabled = false,
                resumePositionSeconds = resumePositionSeconds,
                playWhenReady = isPlaying,
                modifier = Modifier.fillMaxSize()
            )

            // Transparent Gesture & Drag Interceptor Shield across entire mini window
            // Single tap opens the watch page immediately; Double tap toggles window size; Drag moves window
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                val edgeMargin = with(density) { 12.dp.toPx() }
                                val bottomMargin = with(density) { 58.dp.toPx() }
                                offsetX = if (offsetX + windowWidthPx / 2f < screenWidthPx / 2f) {
                                    edgeMargin
                                } else {
                                    (screenWidthPx - windowWidthPx - edgeMargin).coerceAtLeast(0f)
                                }
                                offsetY = offsetY.coerceIn(
                                    with(density) { 32.dp.toPx() },
                                    (screenHeightPx - windowHeightPx - bottomMargin).coerceAtLeast(0f)
                                )
                            },
                            onDragCancel = {
                                offsetX = offsetX.coerceIn(0f, (screenWidthPx - windowWidthPx).coerceAtLeast(0f))
                                offsetY = offsetY.coerceIn(
                                    with(density) { 32.dp.toPx() },
                                    (screenHeightPx - windowHeightPx - with(density) { 58.dp.toPx() }).coerceAtLeast(0f)
                                )
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
                    .pointerInput(isEnlarged) {
                        detectTapGestures(
                            onDoubleTap = {
                                isEnlarged = !isEnlarged
                            },
                            onTap = {
                                onExpand()
                            }
                        )
                    }
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.28f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(Color(0xFFFF0000))
                )
            }

            // Close button, matching the system PiP dismiss affordance.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.75f))
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Mini Player",
                    tint = Color.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
    }
}
