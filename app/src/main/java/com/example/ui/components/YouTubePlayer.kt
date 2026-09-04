package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.GestureDetector
import android.view.MotionEvent
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.collectAsState
import com.example.data.StreamService
import com.example.model.VideoItem
import com.example.ui.theme.YouTubeRed
import com.example.util.PlayerViewManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The app's default player is the documented VidSrc embed page. VidLink Pro
 * is kept as the secondary provider and continues through its existing WebView
 * page path.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayer(
    video: VideoItem,
    selectedServerId: String,
    onSelectServer: (String) -> Unit,
    onOpenServerDialog: () -> Unit = {},
    modifier: Modifier = Modifier,
    isTouchEnabled: Boolean = true,
    resumePositionSeconds: Double = 0.0,
    playWhenReady: Boolean = true,
    onSwipeDown: (() -> Unit)? = null,
    onRetryPlayback: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val isCleanOverlayLoading by PlayerViewManager.isCleanOverlayLoading.collectAsState()
    val isPlayerLoading by PlayerViewManager.isPlayerLoading.collectAsState()
    val hasError by PlayerViewManager.hasPlayerError.collectAsState()
    val playerErrorMessage by PlayerViewManager.playerErrorMessage.collectAsState()
    val isVidLinkServer = selectedServerId == StreamService.VIDLINK_SERVER_ID
    val shouldShowLoadingOverlay = !hasError && (isCleanOverlayLoading || isPlayerLoading)
    var gestureFeedback by remember { mutableStateOf<String?>(null) }
    var gestureFeedbackToken by remember { mutableIntStateOf(0) }
    val feedbackScope = androidx.compose.runtime.rememberCoroutineScope()
    val showGestureFeedback: (String) -> Unit = { message ->
        val token = gestureFeedbackToken + 1
        gestureFeedbackToken = token
        gestureFeedback = message
        feedbackScope.launch {
            delay(900L)
            if (gestureFeedbackToken == token) gestureFeedback = null
        }
    }
    Box(
        modifier = modifier
            .background(Color.Black)
            .testTag("youtube_video_player_container")
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { ctx ->
                    PlayerTouchContainer(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.BLACK)
                        configurePlayerWebView(
                            context = ctx,
                            video = video,
                            serverId = selectedServerId,
                            touchEnabled = isTouchEnabled,
                            resumePositionSeconds = resumePositionSeconds,
                            playWhenReady = playWhenReady,
                            onSwipeDown = onSwipeDown,
                            onGestureFeedback = showGestureFeedback,
                        )
                    }
                },
                update = { container ->
                    container.configurePlayerWebView(
                        context = context,
                        video = video,
                        serverId = selectedServerId,
                        touchEnabled = isTouchEnabled,
                        resumePositionSeconds = resumePositionSeconds,
                        playWhenReady = playWhenReady,
                        onSwipeDown = onSwipeDown,
                        onGestureFeedback = showGestureFeedback,
                    )
                },
                onRelease = { container -> PlayerViewManager.detachFromContainerIfCurrent(container) },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (isVidLinkServer) {
            AnimatedVisibility(
                visible = shouldShowLoadingOverlay,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                VidLinkLoadingOverlay()
            }
        } else {
            AnimatedVisibility(
                visible = shouldShowLoadingOverlay,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.64f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = YouTubeRed, modifier = Modifier.size(38.dp), strokeWidth = 3.dp)
                }
            }
        }

        if (hasError) {
            PlayerErrorOverlay(
                message = playerErrorMessage,
                selectedServerId = selectedServerId,
                onRetry = onRetryPlayback ?: {
                    PlayerViewManager.reloadCurrentPlayer(
                        context = context,
                        video = video,
                        serverId = selectedServerId,
                        resumePositionSeconds = resumePositionSeconds,
                        playWhenReady = playWhenReady
                    )
                },
                onChooseServer = {
                    StreamService.fallbackServerIds(selectedServerId).firstOrNull()?.let(onSelectServer)
                        ?: onOpenServerDialog()
                }
            )
        }

        AnimatedVisibility(
            visible = gestureFeedback != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Text(
                text = gestureFeedback.orEmpty(),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }
    }
}

@Composable
private fun PlayerErrorOverlay(
    message: String?,
    selectedServerId: String,
    onRetry: () -> Unit,
    onChooseServer: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.ErrorOutline, null, tint = YouTubeRed, modifier = Modifier.size(34.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text("Stream unavailable", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message ?: "The player could not load this title.",
                color = Color.LightGray,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onRetry,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                ) { Text("Retry", color = Color.White) }
                Button(
                    onClick = onChooseServer,
                    colors = ButtonDefaults.buttonColors(containerColor = YouTubeRed)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        if (selectedServerId == StreamService.VIDSRC_SERVER_ID) "Try VidLink Pro" else "Try VidSrc",
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun FrameLayout.configurePlayerWebView(
    context: android.content.Context,
    video: VideoItem,
    serverId: String,
    touchEnabled: Boolean,
    resumePositionSeconds: Double,
    playWhenReady: Boolean,
    onSwipeDown: (() -> Unit)?,
    onGestureFeedback: ((String) -> Unit)?
) {
    val webView = PlayerViewManager.attachToContainer(
        context = context,
        container = this,
        video = video,
        serverId = serverId,
        resumePositionSeconds = resumePositionSeconds,
        playWhenReady = playWhenReady
    )
    webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    webView.setTouchEnabled(touchEnabled)
    webView.setGestureHandler(
        if (touchEnabled) onSwipeDown else null,
        if (touchEnabled) onGestureFeedback else null
    )
    (this as? PlayerTouchContainer)?.setMinimizeGestureHandler(
        if (touchEnabled) onSwipeDown else null
    )
    PlayerViewManager.setMiniPlayerMode(!touchEnabled)
}

private class PlayerTouchContainer(context: Context) : FrameLayout(context) {
    private val dragThreshold = 24f * resources.displayMetrics.density
    private var downX = 0f
    private var downY = 0f
    private var minimizeTriggered = false
    private var onMinimize: (() -> Unit)? = null

    fun setMinimizeGestureHandler(handler: (() -> Unit)?) {
        onMinimize = handler
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                minimizeTriggered = false
            }

            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.x - downX
                val deltaY = event.y - downY
                if (!minimizeTriggered &&
                    onMinimize != null &&
                    deltaY > dragThreshold &&
                    deltaY > kotlin.math.abs(deltaX) * 0.65f
                ) {
                    minimizeTriggered = true
                    onMinimize?.invoke()
                    return true
                }
            }
        }

        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            minimizeTriggered = false
        }
        return handled
    }
}

private fun WebView.setGestureHandler(
    onSwipeDown: (() -> Unit)?,
    onGestureFeedback: ((String) -> Unit)?
) {
    if (onSwipeDown == null && onGestureFeedback == null) return

    val density = resources.displayMetrics.density
    val touchSlop = 18f * density
    val verticalStep = 28f * density
    var gestureMode = GestureMode.NONE
    var downX = 0f
    var downY = 0f
    var longPressActive = false
    var verticalSteps = 0
    val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent): Boolean = true

        override fun onDoubleTap(event: MotionEvent): Boolean {
            if (width <= 0 || event.y < 52f * density || event.y > height - 64f * density) return false
            val delta = if (event.x < width / 2f) -10.0 else 10.0
            PlayerViewManager.seekBy(delta)
            onGestureFeedback?.invoke(if (delta < 0) "-10 seconds" else "+10 seconds")
            return true
        }

        override fun onLongPress(event: MotionEvent) {
            if (event.pointerCount > 1 || width <= 0 || event.y < 56f * density ||
                event.y > height - 76f * density ||
                event.x < width * 0.22f || event.x > width * 0.78f
            ) return
            longPressActive = true
            PlayerViewManager.setPlaybackRate(2.0)
            onGestureFeedback?.invoke("2x speed")
        }
    })

    setOnTouchListener { _, event ->
        gestureDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                gestureMode = GestureMode.NONE
                longPressActive = false
                verticalSteps = 0
                false
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1 || longPressActive) {
                    longPressActive
                } else {
                    val deltaX = event.x - downX
                    val deltaY = event.y - downY
                    if (gestureMode == GestureMode.NONE &&
                        (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)
                    ) {
                        gestureMode = when {
                            abs(deltaX) > abs(deltaY) * 1.15f -> GestureMode.SEEK
                            // A deliberate downward swipe anywhere on the
                            // player minimizes it. Keep horizontal seeking
                            // first so a diagonal seek remains predictable.
                            deltaY > 36f * density &&
                                deltaY > abs(deltaX) * 0.75f &&
                                onSwipeDown != null -> GestureMode.MINIMIZE
                            downX > width * 0.64f -> GestureMode.VOLUME
                            downX < width * 0.36f -> GestureMode.BRIGHTNESS
                            else -> GestureMode.NONE
                        }
                        if (gestureMode == GestureMode.MINIMIZE) {
                            onSwipeDown?.invoke()
                            return@setOnTouchListener true
                        }
                    }

                    when (gestureMode) {
                        GestureMode.VOLUME,
                        GestureMode.BRIGHTNESS -> {
                            val steps = ((abs(deltaY) - touchSlop) / verticalStep)
                                .toInt()
                                .coerceAtLeast(0)
                            while (verticalSteps < steps) {
                                val direction = if (deltaY < 0f) 1 else -1
                                if (gestureMode == GestureMode.VOLUME) {
                                    PlayerViewManager.adjustDeviceVolume(context, direction)
                                } else {
                                    PlayerViewManager.adjustScreenBrightness(context, direction * 0.05f)
                                }
                                verticalSteps++
                            }
                            if (steps > 0) {
                                onGestureFeedback?.invoke(
                                    if (gestureMode == GestureMode.VOLUME) {
                                        if (deltaY < 0f) "Volume up" else "Volume down"
                                    } else {
                                        if (deltaY < 0f) "Brightness up" else "Brightness down"
                                    }
                                )
                            }
                            true
                        }
                        GestureMode.SEEK -> true
                        else -> false
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val deltaX = event.x - downX
                val wasGesture = gestureMode != GestureMode.NONE
                if (gestureMode == GestureMode.SEEK && abs(deltaX) > touchSlop) {
                    val seekSeconds = (deltaX / width.coerceAtLeast(1)) * 60.0
                    PlayerViewManager.seekBy(seekSeconds)
                    val roundedSeconds = seekSeconds.roundToInt()
                    onGestureFeedback?.invoke(
                        if (roundedSeconds >= 0) "+${roundedSeconds}s" else "${roundedSeconds}s"
                    )
                }
                if (longPressActive) {
                    PlayerViewManager.setPlaybackRate(1.0)
                    onGestureFeedback?.invoke("1x speed")
                }
                gestureMode = GestureMode.NONE
                longPressActive = false
                verticalSteps = 0
                wasGesture
            }
            else -> false
        }
    }
}

private enum class GestureMode {
    NONE,
    SEEK,
    MINIMIZE,
    VOLUME,
    BRIGHTNESS
}

private fun WebView.setTouchEnabled(enabled: Boolean) {
    if (enabled) {
        setOnTouchListener(null)
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
    } else {
        setOnTouchListener { _, _ -> true }
        clearFocus()
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
    }
}
