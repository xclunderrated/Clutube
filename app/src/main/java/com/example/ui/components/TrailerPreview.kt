package com.example.ui.components

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.tmdb.TmdbRepository
import com.example.model.VideoItem
import com.example.model.playbackKey
import com.example.util.ImagePreset
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

/**
 * Hold-to-preview session. Single active inline trailer across the whole app
 * (only one WebView alive at a time). [isMuted] lives in memory only so it
 * survives navigation but resets to muted on process restart.
 */
object TrailerPreviewSession {
    var activeKey by mutableStateOf<String?>(null)
        private set
    var isMuted by mutableStateOf(true)

    private val hits = ConcurrentHashMap<String, String>()
    private val misses = ConcurrentHashMap.newKeySet<String>()

    fun cachedHit(key: String): String? = hits[key]
    fun isKnownMiss(key: String): Boolean = misses.contains(key)
    fun storeHit(key: String, trailerId: String) {
        if (trailerId.isNotBlank()) {
            hits[key] = trailerId
            misses.remove(key)
        }
    }
    fun storeMiss(key: String) {
        misses.add(key)
    }

    fun activate(key: String) {
        activeKey = key
    }

    fun deactivate() {
        activeKey = null
    }

    fun deactivateIf(key: String) {
        if (activeKey == key) activeKey = null
    }

    fun toggleMute() {
        isMuted = !isMuted
    }
}

/**
 * Drop-in 16:9 (or poster) thumbnail that starts a muted inline YouTube
 * trailer on long-press, seamlessly in place of the artwork. While previewing,
 * all normal badges/progress are hidden — only the mute button is shown.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PreviewableThumbnail(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    imagePreset: ImagePreset = ImagePreset.THUMBNAIL,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    isWatched: Boolean = false,
    dimAlpha: Float? = null,
    isPosterRatio: Boolean = false,
    overlayContent: @Composable (BoxScope.() -> Unit)? = null
) {
    val previewKey = remember(video) { video.playbackKey() }
    val isActive = TrailerPreviewSession.activeKey == previewKey
    var trailerId by remember(previewKey) {
        mutableStateOf(TrailerPreviewSession.cachedHit(previewKey))
    }
    var isResolving by remember(previewKey) { mutableStateOf(false) }
    var isReady by remember(previewKey) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(isActive) {
        if (!isActive) isReady = false
    }

    fun startPreview() {
        if (TrailerPreviewSession.activeKey == previewKey && trailerId != null) return
        if (TrailerPreviewSession.isKnownMiss(previewKey)) return
        val hit = TrailerPreviewSession.cachedHit(previewKey) ?: trailerId
        if (!hit.isNullOrBlank()) {
            trailerId = hit
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            TrailerPreviewSession.activate(previewKey)
            return
        }
        if (isResolving) return
        isResolving = true
        scope.launch {
            val key = try {
                TmdbRepository.resolveTrailerKey(video)
            } catch (_: Exception) {
                null
            }
            isResolving = false
            if (!key.isNullOrBlank()) {
                TrailerPreviewSession.storeHit(previewKey, key)
                trailerId = key
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                TrailerPreviewSession.activate(previewKey)
            } else {
                // Silent miss: hold does nothing per spec.
                TrailerPreviewSession.storeMiss(previewKey)
            }
        }
    }

    Box(
        modifier = modifier
            .clip(shape)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Play ${video.title}",
                onClick = {
                    if (isActive) TrailerPreviewSession.deactivate()
                    onClick()
                },
                onLongClick = { startPreview() }
            )
            .testTag("preview_thumb_$previewKey"),
        contentAlignment = Alignment.Center
    ) {
        // Artwork stays underneath so the swap is seamless (no white flash).
        FittedMediaThumbnail(
            thumbnailUrl = video.thumbnailUrl,
            backdropUrl = video.backdropUrl,
            posterUrl = video.posterUrl,
            logoUrl = video.logoUrl,
            hasTitledBackdrop = video.hasTitledBackdrop,
            contentDescription = video.title,
            modifier = Modifier.fillMaxSize(),
            imagePreset = imagePreset,
            isWatched = isWatched,
            dimAlpha = dimAlpha,
            shape = shape,
            isPosterRatio = isPosterRatio,
            overlayContent = if (isActive) null else overlayContent
        )

        if (isActive && !trailerId.isNullOrBlank()) {
            val currentId = trailerId!!
            val muted = TrailerPreviewSession.isMuted
            val animatedAlpha by animateFloatAsState(
                targetValue = if (isReady) 1f else 0f,
                label = "preview_fade"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = animatedAlpha }
                    .background(Color.Black)
            ) {
                androidx.compose.runtime.key(currentId) {
                    AndroidView(
                        factory = { ctx ->
                            TrailerPreviewWebView(ctx).apply {
                                onFirstFrame = { isReady = true }
                                loadTrailer(currentId, TrailerPreviewSession.isMuted)
                            }
                        },
                        update = { view ->
                            view.setAudioEnabled(!TrailerPreviewSession.isMuted)
                        },
                        onRelease = { it.releaseTrailer() },
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("video_preview_$previewKey")
                    )
                }
            }
            // ONLY UI over the trailer: mute button, bottom-right.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        TrailerPreviewSession.toggleMute()
                    },
                    modifier = Modifier
                        .size(32.dp)
                        .testTag("trailer_preview_mute")
                ) {
                    Icon(
                        imageVector = if (muted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = if (muted) "Unmute trailer" else "Mute trailer",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Stops the active preview only after a real scroll (buffered ~24dp), so a
 * light touch or tiny jitter never kills playback.
 */
@Composable
fun StopTrailerPreviewOnListScroll(
    state: LazyListState,
    thresholdDp: Int = 24
) {
    val active = TrailerPreviewSession.activeKey
    val density = LocalDensity.current
    LaunchedEffect(active, state) {
        if (active == null) return@LaunchedEffect
        val thresholdPx = with(density) { thresholdDp.dp.toPx() }
        val anchorIndex = state.firstVisibleItemIndex
        val anchorOffset = state.firstVisibleItemScrollOffset
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (TrailerPreviewSession.activeKey == null) return@collect
                if (index != anchorIndex) {
                    TrailerPreviewSession.deactivate()
                } else if (abs(offset - anchorOffset) > thresholdPx) {
                    TrailerPreviewSession.deactivate()
                }
            }
    }
}

@Composable
fun StopTrailerPreviewOnGridScroll(
    state: LazyGridState,
    thresholdDp: Int = 24
) {
    val active = TrailerPreviewSession.activeKey
    val density = LocalDensity.current
    LaunchedEffect(active, state) {
        if (active == null) return@LaunchedEffect
        val thresholdPx = with(density) { thresholdDp.dp.toPx() }
        val anchorIndex = state.firstVisibleItemIndex
        val anchorOffset = state.firstVisibleItemScrollOffset
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (TrailerPreviewSession.activeKey == null) return@collect
                if (index != anchorIndex) {
                    TrailerPreviewSession.deactivate()
                } else if (abs(offset - anchorOffset) > thresholdPx) {
                    TrailerPreviewSession.deactivate()
                }
            }
    }
}

@Composable
fun StopTrailerPreviewOnColumnScroll(
    state: ScrollState,
    thresholdDp: Int = 24
) {
    val active = TrailerPreviewSession.activeKey
    val density = LocalDensity.current
    LaunchedEffect(active, state) {
        if (active == null) return@LaunchedEffect
        val thresholdPx = with(density) { thresholdDp.dp.toPx() }
        val anchor = state.value
        snapshotFlow { state.value }
            .collect { offset ->
                if (TrailerPreviewSession.activeKey == null) return@collect
                if (abs(offset - anchor) > thresholdPx) TrailerPreviewSession.deactivate()
            }
    }
}

/**
 * Lightweight muted YouTube-nocookie iframe player for inline previews.
 * Single-purpose: autoplay + loop, no chrome. Extracted from the Shorts
 * TrailerWebView pattern.
 */
@SuppressLint("SetJavaScriptEnabled")
class TrailerPreviewWebView(context: android.content.Context) : WebView(context) {
    var onFirstFrame: (() -> Unit)? = null
    private var loadedTrailerId: String? = null
    private var lastAudioEnabled: Boolean? = null
    private var readyFired = false

    init {
        setBackgroundColor(android.graphics.Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                if (!readyFired) {
                    readyFired = true
                    // Small delay lets the first frame paint before crossfade.
                    postDelayed({ onFirstFrame?.invoke() }, 600L)
                }
            }
        }
        webChromeClient = WebChromeClient()
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
    }

    fun loadTrailer(videoId: String, startMuted: Boolean) {
        if (videoId.isBlank() || loadedTrailerId == videoId) return
        loadedTrailerId = videoId
        readyFired = false
        lastAudioEnabled = !startMuted
        val encodedId = Uri.encode(videoId)
        val muteParam = if (startMuted) 1 else 0
        val html = """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
              <style>
                html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; display: flex; align-items: center; justify-content: center; }
                iframe { width: 100%; aspect-ratio: 16 / 9; border: 0; background: #000; }
                .chrome-mask { position: fixed; left: 0; right: 0; z-index: 20; pointer-events: none; background: #000; }
                .chrome-mask.top { top: 0; height: 52px; }
                .chrome-mask.bottom { bottom: 0; height: 56px; }
              </style>
            </head>
            <body>
              <iframe id="trailer_frame"
                src="https://www.youtube-nocookie.com/embed/$encodedId?autoplay=1&mute=$muteParam&vq=medium&controls=0&playsinline=1&rel=0&modestbranding=1&showinfo=0&autohide=1&cc_load_policy=0&iv_load_policy=3&fs=0&disablekb=1&loop=1&playlist=$encodedId&enablejsapi=1"
                title="Trailer preview"
                allow="autoplay; encrypted-media"
                allowfullscreen="false"></iframe>
              <div class="chrome-mask top"></div>
              <div class="chrome-mask bottom"></div>
              <script>
                (function () {
                  var frame = document.getElementById('trailer_frame');
                  function send(command, args) {
                    if (!frame || !frame.contentWindow) return;
                    frame.contentWindow.postMessage(JSON.stringify({event:'command', func:command, args:args || []}), '*');
                  }
                  function enforce() {
                    send('${if (startMuted) "mute" else "unMute"}');
                    ${if (startMuted) "send('mute');" else "send('unMute'); send('setVolume', [100]);"}
                  }
                  if (frame) frame.addEventListener('load', function () {
                    [100, 350, 800, 1500, 2500].forEach(function (delay) {
                      window.setTimeout(enforce, delay);
                    });
                  });
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
        loadDataWithBaseURL(
            "https://www.youtube-nocookie.com",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    fun setAudioEnabled(enabled: Boolean) {
        if (lastAudioEnabled == enabled) return
        lastAudioEnabled = enabled
        val command = if (enabled) "unMute" else "mute"
        evaluateJavascript(
            """
            (function() {
              var frame = document.getElementById('trailer_frame');
              if (frame && frame.contentWindow) {
                frame.contentWindow.postMessage(JSON.stringify({event:'command', func:'$command', args:[]}), '*');
                ${if (enabled) "frame.contentWindow.postMessage(JSON.stringify({event:'command', func:'setVolume', args:[100]}), '*');" else ""}
              }
            })();
            """.trimIndent(),
            null
        )
    }

    fun releaseTrailer() {
        onFirstFrame = null
        stopLoading()
        loadUrl("about:blank")
        removeAllViews()
        destroy()
    }
}
