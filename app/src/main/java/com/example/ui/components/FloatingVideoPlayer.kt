package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.model.VideoItem
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private const val RESIZE_ANIM_MS = 260

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
    modifier: Modifier = Modifier
) {
    var isEnlarged by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val scope = rememberCoroutineScope()
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val isTablet = maxWidth >= 600.dp

        // Tablet gets a large, readable window; phones keep the compact one.
        val targetWidthDp = if (isTablet) {
            (maxWidth * if (isEnlarged) 0.58f else 0.48f).coerceIn(
                if (isEnlarged) 480.dp else 400.dp,
                if (isEnlarged) 720.dp else 600.dp
            )
        } else if (isEnlarged) {
            (maxWidth * 0.78f).coerceIn(260.dp, 360.dp)
        } else {
            (maxWidth * 0.66f).coerceIn(220.dp, 300.dp)
        }
        // Animated resize so double-tap zoom glides instead of jumping.
        val windowWidthDp by animateDpAsState(
            targetValue = targetWidthDp,
            animationSpec = tween(RESIZE_ANIM_MS, easing = FastOutSlowInEasing),
            label = "pip_width"
        )
        val windowWidthPx = with(density) { windowWidthDp.toPx() }
        val windowHeightPx = windowWidthPx * (9f / 16f)
        // Target (settled) size in px, for clamping while the resize anim runs.
        val targetWidthPx = with(density) { targetWidthDp.toPx() }
        val targetHeightPx = targetWidthPx * (9f / 16f)

        val edgeMarginPx = with(density) { 12.dp.toPx() }
        val topMarginPx = with(density) { 32.dp.toPx() }
        val bottomMarginPx = with(density) { 58.dp.toPx() }

        fun boundsFor(widthPx: Float, heightPx: Float): Pair<Float, Float> {
            val maxX = (screenWidthPx - widthPx - edgeMarginPx).coerceAtLeast(edgeMarginPx)
            val maxY = (screenHeightPx - heightPx - bottomMarginPx)
                .coerceAtLeast(topMarginPx)
            return maxX to maxY
        }

        // Default initial position: bottom right, above the navigation bar.
        val defaultOffset = Offset(
            x = (screenWidthPx - targetWidthPx - with(density) { 16.dp.toPx() }).coerceAtLeast(edgeMarginPx),
            y = (screenHeightPx - targetHeightPx - with(density) { 90.dp.toPx() }).coerceAtLeast(topMarginPx)
        )

        // Position driven by an Animatable so drags track 1:1 (snapTo, no
        // recomposition through the offset lambda) and releases glide with
        // a spring to the nearest edge, YouTube-style.
        val position = remember { Animatable(defaultOffset, Offset.VectorConverter) }

        // Rotation / window-size change: glide back inside the new bounds.
        LaunchedEffect(screenWidthPx, screenHeightPx) {
            val (maxX, maxY) = boundsFor(targetWidthPx, targetHeightPx)
            val settled = Offset(
                position.value.x.coerceIn(edgeMarginPx, maxX),
                position.value.y.coerceIn(topMarginPx, maxY)
            )
            if (settled != position.value) {
                position.animateTo(settled, tween(200, easing = FastOutSlowInEasing))
            }
        }

        // Resize (double-tap zoom): glide the window so it stays on screen
        // while it grows/shrinks, using the same duration as the size anim.
        LaunchedEffect(isEnlarged) {
            val (maxX, maxY) = boundsFor(targetWidthPx, targetHeightPx)
            val settled = Offset(
                position.value.x.coerceIn(edgeMarginPx, maxX),
                position.value.y.coerceIn(topMarginPx, maxY)
            )
            if (settled != position.value) {
                position.animateTo(settled, tween(RESIZE_ANIM_MS, easing = FastOutSlowInEasing))
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(position.value.x.roundToInt(), position.value.y.roundToInt()) }
                .width(windowWidthDp)
                .aspectRatio(16f / 9f)
                // No shadow/border while video plays: shadow creates an offscreen
                // buffer re-rendered every WebView frame (micro-stutter source).
                // Rounded clip is kept (single cheap layer, no blur).
                .clip(RoundedCornerShape(if (isTablet) 12.dp else 10.dp))
                .background(Color.Black)
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

            // Transparent Gesture & Drag Interceptor Shield across entire mini window.
            // Single tap opens the watch page; Double tap toggles window size; Drag moves window.
            // Only the X button below is visible UI — the video stays unobstructed.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragEnd = {
                                val (maxX, maxY) = boundsFor(windowWidthPx, windowHeightPx)
                                val targetX = if (position.value.x + windowWidthPx / 2f < screenWidthPx / 2f) {
                                    edgeMarginPx
                                } else {
                                    maxX
                                }
                                val target = Offset(
                                    targetX,
                                    position.value.y.coerceIn(topMarginPx, maxY)
                                )
                                scope.launch {
                                    position.animateTo(
                                        target,
                                        spring(
                                            dampingRatio = Spring.DampingRatioNoBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    )
                                }
                            },
                            onDragCancel = {
                                val (maxX, maxY) = boundsFor(windowWidthPx, windowHeightPx)
                                scope.launch {
                                    position.animateTo(
                                        Offset(
                                            position.value.x.coerceIn(edgeMarginPx, maxX),
                                            position.value.y.coerceIn(topMarginPx, maxY)
                                        ),
                                        tween(200, easing = FastOutSlowInEasing)
                                    )
                                }
                            }
                        ) { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                position.snapTo(position.value + dragAmount)
                            }
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

            // Close button — the only visible control. The 44dp hit area is
            // transparent; only the 24dp circle is drawn, over the corner,
            // so the video stays fully visible.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(44.dp)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.75f)),
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
}
