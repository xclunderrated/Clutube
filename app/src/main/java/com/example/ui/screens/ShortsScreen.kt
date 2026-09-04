package com.example.ui.screens

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.example.model.ShortItem
import com.example.model.VideoItem
import com.example.ui.components.ImdbRatingBadge
import com.example.ui.theme.YTBlueVerified
import com.example.ui.theme.YouTubeRed
import com.example.util.ImagePreset
import com.example.util.rememberOptimizedImageRequest
import java.util.Locale

@Composable
fun ShortsScreen(
    shorts: List<ShortItem>,
    currentIndex: Int,
    onNextShort: () -> Unit,
    onPrevShort: () -> Unit,
    isLoading: Boolean = false,
    savedVideoIds: Set<String> = emptySet(),
    onToggleSave: (VideoItem) -> Unit = {},
    onWatchNow: (VideoItem) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val swipeThreshold = with(density) { 72.dp.toPx() }

    if (shorts.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("shorts_empty_state"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (isLoading) "Loading trailers…" else "No trailers available",
                color = Color.White,
                fontSize = 16.sp
            )
        }
        return
    }

    val safeCurrentIndex = currentIndex.coerceIn(0, shorts.lastIndex)
    val short = shorts[safeCurrentIndex]
    val media = short.mediaItem
    val isSaved = media?.id?.let(savedVideoIds::contains) == true
    val displayTitle = media?.title?.takeIf { it.isNotBlank() } ?: short.title
    val displayStudio = media?.channelName?.takeIf { it.isNotBlank() } ?: short.channelName
    val displayRating = media?.rating
        ?.takeIf { it > 0 }
        ?.let { String.format(Locale.US, "%.1f", it) }
    var isPlaying by remember(short.id) { mutableStateOf(true) }
    var audioEnabled by remember(short.id) { mutableStateOf(true) }

    val thumbnailRequest = rememberOptimizedImageRequest(
        data = short.thumbnailUrl,
        preset = ImagePreset.SHORT_BACKGROUND
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("shorts_fullscreen_player")
    ) {
        // Keep the poster behind the trailer WebView while YouTube initializes.
        AsyncImage(
            model = thumbnailRequest,
            contentDescription = short.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        short.trailerVideoId?.takeIf { it.isNotBlank() }?.let { trailerId ->
            AndroidView(
                factory = { viewContext ->
                    TrailerWebView(viewContext).apply { loadTrailer(trailerId) }
                },
                update = { trailerView ->
                    trailerView.loadTrailer(trailerId)
                    trailerView.setPlaying(isPlaying)
                    trailerView.setAudioEnabled(audioEnabled)
                },
                onRelease = { it.releaseTrailer() },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("short_trailer_player")
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.52f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.9f)
                        )
                    )
                )
        )

        // The WebView is intentionally covered by this gesture layer. Trailer
        // controls are hidden, so vertical swipes belong to the Shorts feed.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(short.id, swipeThreshold) {
                    var totalDragY = 0f
                    detectDragGestures(
                        onDragStart = {
                            totalDragY = 0f
                            audioEnabled = true
                        },
                        onDrag = { change, dragAmount ->
                            totalDragY += dragAmount.y
                            change.consume()
                        },
                        onDragEnd = {
                            when {
                                totalDragY <= -swipeThreshold -> onNextShort()
                                totalDragY >= swipeThreshold -> onPrevShort()
                            }
                        },
                        onDragCancel = { totalDragY = 0f }
                    )
                }
                .pointerInput(short.id) {
                    detectTapGestures(
                        onTap = {
                            if (!audioEnabled) audioEnabled = true
                            else isPlaying = !isPlaying
                        }
                    )
                }
                .zIndex(1f)
        )

        // Minimal top-right controls
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(14.dp)
                .zIndex(2f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { audioEnabled = !audioEnabled },
                modifier = Modifier.testTag("short_audio_button")
            ) {
                Icon(
                    imageVector = if (audioEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    contentDescription = if (audioEnabled) "Mute trailer" else "Enable trailer sound",
                    tint = Color.White
                )
            }
        }

        AnimatedVisibility(
            visible = !isPlaying,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(3f)
        ) {
            IconButton(
                onClick = { isPlaying = true },
                modifier = Modifier
                    .size(70.dp)
                    .testTag("short_play_button")
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play trailer",
                    tint = Color.White,
                    modifier = Modifier.size(52.dp)
                )
            }
        }

        // Keep the catalog identity visible while the trailer plays. This is
        // sourced from the linked TMDB item, not the YouTube trailer chrome.
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 46.dp, bottom = 74.dp)
                .fillMaxWidth(0.84f)
                .zIndex(2f)
        ) {
            Column {
                Text(
                    text = displayTitle,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = displayStudio,
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    displayRating?.let { rating ->
                        Spacer(modifier = Modifier.width(10.dp))
                        ImdbRatingBadge(rating = rating, compact = true)
                    }
                }
            }
        }

        // Bottom-left Watch Now button
        media?.let { linkedMedia ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = 28.dp)
                    .zIndex(2f)
            ) {
                Button(
                    onClick = { onWatchNow(linkedMedia) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("short_watch_now_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("Watch Now", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Right-side Save button
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 14.dp, bottom = 28.dp)
                .zIndex(2f)
        ) {
            IconButton(
                onClick = { media?.let(onToggleSave) },
                enabled = media != null,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("short_save_button")
            ) {
                Icon(
                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (isSaved) "Saved" else "Save",
                    tint = if (isSaved) YTBlueVerified else Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        if (isLoading) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.64f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .zIndex(4f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    color = YouTubeRed,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Finding trailers", color = Color.White, fontSize = 11.sp)
            }
        }

        Box(
            modifier = Modifier
                    .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.3f))
                .zIndex(4f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((safeCurrentIndex + 1f) / shorts.size.coerceAtLeast(1))
                    .height(2.dp)
                    .background(YouTubeRed)
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private class TrailerWebView(context: android.content.Context) : WebView(context) {
    private var loadedTrailerId: String? = null
    private var audioCommandGeneration = 0

    init {
        setBackgroundColor(android.graphics.Color.BLACK)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        webViewClient = WebViewClient()
        webChromeClient = WebChromeClient()
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
    }

    fun loadTrailer(videoId: String) {
        if (videoId.isBlank() || loadedTrailerId == videoId) return
        loadedTrailerId = videoId
        audioCommandGeneration++
        val encodedId = Uri.encode(videoId)
        val html = """
            <!doctype html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no">
              <style>
                html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #000; overflow: hidden; }
                iframe { position: fixed; inset: 0; width: 100%; height: 100%; border: 0; background: #000; }
                /* Keep YouTube's title/watch-on-YouTube chrome from flashing
                   over the clean Shorts surface during iframe startup. */
                .chrome-mask { position: fixed; left: 0; right: 0; z-index: 20; pointer-events: none; background: #000; }
                .chrome-mask.top { top: 0; height: 62px; }
                .chrome-mask.bottom { bottom: 0; height: 72px; }
              </style>
            </head>
            <body>
              <iframe id="trailer_frame"
                src="https://www.youtube-nocookie.com/embed/$encodedId?autoplay=1&mute=0&vq=medium&controls=0&playsinline=1&rel=0&modestbranding=1&showinfo=0&autohide=1&cc_load_policy=0&iv_load_policy=3&fs=0&disablekb=1&loop=1&playlist=$encodedId&enablejsapi=1"
                title="Movie trailer"
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
                  function requestAudio() {
                    send('unMute');
                    send('setVolume', [100]);
                  }
                  if (frame) frame.addEventListener('load', function () {
                    [100, 350, 800, 1500, 2500].forEach(function (delay) {
                      window.setTimeout(requestAudio, delay);
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

    fun setPlaying(playing: Boolean) {
        val command = if (playing) "playVideo" else "pauseVideo"
        evaluateJavascript(
            """
            (function() {
              var frame = document.getElementById('trailer_frame');
              if (frame && frame.contentWindow) {
                frame.contentWindow.postMessage(JSON.stringify({event:'command', func:'$command', args:[]}), '*');
              }
            })();
            """.trimIndent(),
            null
        )
    }

    fun setAudioEnabled(enabled: Boolean) {
        val command = if (enabled) "unMute" else "mute"
        val generation = ++audioCommandGeneration
        // YouTube's iframe API can finish its handshake after the WebView has
        // already received Compose's first update. Repeat the command during
        // that short window so every new Short starts with the requested audio state.
        listOf(0L, 180L, 450L, 900L, 1600L, 2600L).forEach { delayMillis ->
            postDelayed({
                if (generation == audioCommandGeneration) {
                    sendPlayerCommand(command)
                    if (enabled) sendPlayerCommand("setVolume", "[100]")
                }
            }, delayMillis)
        }
    }

    private fun sendPlayerCommand(command: String, argsJson: String = "[]") {
        evaluateJavascript(
            """
            (function() {
              var frame = document.getElementById('trailer_frame');
              if (frame && frame.contentWindow) {
                frame.contentWindow.postMessage(JSON.stringify({event:'command', func:'$command', args:$argsJson}), '*');
              }
            })();
            """.trimIndent(),
            null
        )
    }

    fun releaseTrailer() {
        stopLoading()
        loadUrl("about:blank")
        removeAllViews()
        destroy()
    }
}
