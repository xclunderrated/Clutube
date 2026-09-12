package com.example.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.data.StreamService
import com.example.data.SettingsManager
import com.example.model.PlayerEvent
import com.example.model.PlayerSnapshot
import com.example.model.PlaybackQuality
import com.example.model.SubtitlePreference
import com.example.model.VideoItem
import com.example.model.playbackKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

/** Owns the persistent WebView used by the documented VidSrc and VidLink players. */
object PlayerViewManager {
    private const val TAG = "WebPlaybackManager"
    private const val PLAYER_UI_HIDE_DELAY_MS = 3200L

    /**
     * Top-document nudge for the autoplay watchdog: clicks the provider's
     * landing/resume gates and plays any same-document video. Nested frames
     * get the same via the play postMessage + their injected runtimes.
     */
    private const val AUTOPLAY_NUDGE_JS = """
        (function() {
            try {
                var btn = document.getElementById('bigPlay');
                if (btn && btn.offsetParent !== null) { try { btn.click(); } catch (_) {} }
                var candidates = document.querySelectorAll('button, [role="button"]');
                for (var i = 0; i < candidates.length; i++) {
                    var el = candidates[i];
                    if (!el || el.offsetParent === null) continue;
                    var label = (el.innerText || el.textContent || '');
                    if (/resume|continue\s*watching/i.test(label)) {
                        try { el.click(); } catch (_) {}
                        break;
                    }
                }
            } catch (_) {}
            try {
                document.querySelectorAll('video').forEach(function(v) {
                    try { v.muted = false; } catch (_) {}
                    try { var p = v.play(); if (p && p.catch) p.catch(function() {}); } catch (_) {}
                });
            } catch (_) {}
        })();
    """

    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainScope = CoroutineScope(Dispatchers.Main.immediate)
    private var persistentWebView: WebView? = null
    private var hostContext: Context? = null
    private var activeServerId: String? = null
    private var loadGeneration = 0L
    private var pendingResumePositionSeconds = 0.0
    private var pendingPlayWhenReady = true
    private var pendingQuality = PlaybackQuality.AUTO.wireValue
    private var pendingSubtitles = SubtitlePreference.OFF.wireValue
    private var activeVidSrcServerHost = StreamService.DEFAULT_VIDSRC_SERVER_HOST
    /**
     * Option-B Continue preload: a hidden background `loadUrl` for the MRU
     * Continue Watching title (movie or current S/E episode) issued on WiFi
     * while idle. The page loads paused at the resume point; tapping the card
     * promotes it to playing without a reload. Null when no preload is live.
     * Never set while real playback (`currentPlayingVideo`) is active — the
     * warmer checks before calling, and any real `loadMedia` for a different
     * key replaces the preload.
     */
    var speculativePreloadKey: String? = null
        private set
    var isSpeculativePreload: Boolean = false
        private set
    private var reportedPageErrorGeneration = -1L
    private var loadingMaskJob: Job? = null
    private var loadWatchdogJob: Job? = null
    // Autoplay backstop: a load requested with playWhenReady=true must start
    // without a tap. If the provider parks a Resume gate the injected
    // runtimes missed, re-assert play + click the gates. Cancelled by a new
    // load, an error, explicit user pause, or the first playing snapshot.
    private var autoplayWatchdogJob: Job? = null
    private var lastExplicitPauseAtMillis = 0L
    private var hasUsablePlaybackSignal = false
    // Highest position seen from VidLink snapshots this generation. If the
    // position advances, frames are rendering even when the provider's
    // playing flag is stuck — that also arms the watchdog (see bridge).
    private var lastVidLinkProgressPosition = Double.NaN
    private var miniPlayerMode = false
    private var audioFocusRequest: AudioFocusRequest? = null
    private var legacyAudioFocusHeld = false
    private var pausedForAudioLoss: Boolean = false
    private val audioFocusChangeListener =
        AudioManager.OnAudioFocusChangeListener { focusChange ->
            if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
            ) {
                // Incoming call / other media: pause the embed. The next JS
                // snapshot flips ViewModel.isPlaying so the notification
                // follows without an extra callback path.
                mainHandler.post {
                    pausedForAudioLoss = lastKnownIsPlaying
                    lastKnownIsPlaying = false
                    updateBackgroundWakeLock()
                    setUserPausedFlag(true)
                    dispatchVideoCommand("pause")
                }
            } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                // Let the system duck; pausing here would kill background
                // audio for every navigation prompt.
            } else if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
                mainHandler.post {
                    if (pausedForAudioLoss && backgroundPlayEnabled) {
                        pausedForAudioLoss = false
                        lastKnownIsPlaying = true
                        updateBackgroundWakeLock()
                        setUserPausedFlag(false)
                        dispatchVideoCommand("play")
                    } else {
                        pausedForAudioLoss = false
                    }
                }
            }
        }
    /** Background play: audio continues with screen off / app backgrounded. */
    var backgroundPlayEnabled: Boolean = true
        private set
    private var backgroundWakeLock: android.os.PowerManager.WakeLock? = null
    // Delayed-release for the CPU hold: a provider-initiated pause on
    // screen-off fires dispatchSnapshot(false) before the JS resume guard
    // (250ms/1s/3s/6s retries) can recover. Releasing immediately lets the CPU
    // sleep and the retries never run. Hold 8s so resume wins; genuine
    // pauses still release after the window. Applies to both VidSrc and
    // VidLink (shared WebView path).
    private var wakeReleaseJob: Job? = null
    private var lastKnownIsPlaying: Boolean = false
    private var playerUiHideRunnable: Runnable? = null
    private var lastPresentationModeKey: String? = null
    private var lastSnapshotEmitMillis = 0L
    private var lastSnapshotPosition = Double.NaN
    private var lastSnapshotPlaying: Boolean? = null

    var activeMediaKey: String? = null
        private set
    var currentStreamUrl: String? = null
        private set

    private val _isCleanOverlayLoading = MutableStateFlow(false)
    val isCleanOverlayLoading: StateFlow<Boolean> = _isCleanOverlayLoading.asStateFlow()
    private val _isPlayerLoading = MutableStateFlow(false)
    val isPlayerLoading: StateFlow<Boolean> = _isPlayerLoading.asStateFlow()
    private val _hasPlayerError = MutableStateFlow(false)
    val hasPlayerError: StateFlow<Boolean> = _hasPlayerError.asStateFlow()
    private val _playerErrorMessage = MutableStateFlow<String?>(null)
    val playerErrorMessage: StateFlow<String?> = _playerErrorMessage.asStateFlow()
    private val _isPlayerUiVisible = MutableStateFlow(false)
    val isPlayerUiVisible: StateFlow<Boolean> = _isPlayerUiVisible.asStateFlow()

    var onPlayerEvent: ((PlayerEvent) -> Unit)? = null
    var onVidSrcServerSelected: ((String) -> Unit)? = null
    var onVidSrcServerOrderChanged: ((List<String>) -> Unit)? = null
    /**
     * Fired on any direct touch-down on the player surface (inline WebView or
     * native fullscreen container). Used to cancel the pending skip-segment
     * auto-skip and hide the pill when the user interacts with the player
     * instead of tapping the pill. Taps on the pill itself never reach here.
     */
    var onPlayerInteraction: (() -> Unit)? = null

    fun notifyPlayerInteraction() {
        onPlayerInteraction?.invoke()
    }

    private fun setLoading(loading: Boolean) {
        _isPlayerLoading.value = loading
    }

    private fun setError(error: Boolean, message: String? = null) {
        _hasPlayerError.value = error
        _playerErrorMessage.value = if (error) message?.trim()?.takeIf { it.isNotEmpty() } else null
    }

    /**
     * Keeps every app-owned player affordance on the same visibility clock as
     * the provider controls. The provider can report an exact state through
     * the bridge; touch-driven calls remain the fallback for providers that do
     * not expose their control state.
     */
    fun togglePlayerUi() {
        mainHandler.post {
            setPlayerUiVisibleInternal(!_isPlayerUiVisible.value)
            if (_isPlayerUiVisible.value) schedulePlayerUiHide()
        }
    }

    fun showPlayerUi() {
        mainHandler.post {
            setPlayerUiVisibleInternal(true)
            schedulePlayerUiHide()
        }
    }

    fun hidePlayerUi() {
        mainHandler.post { hidePlayerUiInternal() }
    }

    private fun updatePlayerUiVisibility(visible: Boolean, generation: Long) {
        mainHandler.post {
            if (generation != loadGeneration) return@post
            setPlayerUiVisibleInternal(visible)
            if (visible) schedulePlayerUiHide() else cancelPlayerUiHide()
        }
    }

    private fun setPlayerUiVisibleInternal(visible: Boolean) {
        if (_isPlayerUiVisible.value == visible) {
            FullscreenHelper.setPlayerUiVisible(visible)
            return
        }
        _isPlayerUiVisible.value = visible
        FullscreenHelper.setPlayerUiVisible(visible)
    }

    private fun schedulePlayerUiHide() {
        cancelPlayerUiHide()
        val hideRunnable = Runnable { hidePlayerUiInternal() }
        playerUiHideRunnable = hideRunnable
        mainHandler.postDelayed(hideRunnable, PLAYER_UI_HIDE_DELAY_MS)
    }

    private fun cancelPlayerUiHide() {
        playerUiHideRunnable?.let(mainHandler::removeCallbacks)
        playerUiHideRunnable = null
    }

    private fun hidePlayerUiInternal() {
        cancelPlayerUiHide()
        setPlayerUiVisibleInternal(false)
    }

    private fun reportPageError(message: String?) {
        if (reportedPageErrorGeneration == loadGeneration) return
        reportedPageErrorGeneration = loadGeneration
        lastKnownIsPlaying = false
        updateBackgroundWakeLock()
        hidePlayerUiInternal()
        setLoading(false)
        setError(true, message)
        activeMediaKey?.let { key ->
            onPlayerEvent?.invoke(PlayerEvent.Error(key, loadGeneration, message))
        }
    }

    private fun dispatchSnapshot(
        generation: Long,
        positionSeconds: Double,
        durationSeconds: Double,
        isPlaying: Boolean,
        isMuted: Boolean
    ) {
        // Truth from the provider page drives the CPU hold: playing -> hold,
        // paused -> release. Runs even for coalesced ticks below.
        if (generation == loadGeneration) {
            lastKnownIsPlaying = isPlaying
            updateBackgroundWakeLock()
        }
        // Coalesce duplicate ticks: identical position/playing reports
        // within 1500ms never reach Compose (saves a full Watch+Home
        // recompose). Threshold 1.5s of position drift keeps progress bars
        // and UpNext (10s window) smooth while cutting Binder traffic.
        val now = System.currentTimeMillis()
        if (generation == loadGeneration &&
            lastSnapshotPlaying == isPlaying &&
            !lastSnapshotPosition.isNaN() &&
            kotlin.math.abs(positionSeconds - lastSnapshotPosition) < 1.5 &&
            now - lastSnapshotEmitMillis < 1500
        ) {
            FullscreenHelper.updateUpNextPlayback(positionSeconds, durationSeconds)
            return
        }
        lastSnapshotEmitMillis = now
        lastSnapshotPosition = positionSeconds
        lastSnapshotPlaying = isPlaying
        mainHandler.post {
            val key = activeMediaKey ?: return@post
            if (generation != loadGeneration) return@post
            // Fullscreen is a native decor layer above Compose, so feed it
            // the raw provider position directly. This also catches a seek
            // into the final minute before the Compose state has recomposed.
            FullscreenHelper.updateUpNextPlayback(positionSeconds, durationSeconds)
            onPlayerEvent?.invoke(
                PlayerEvent.Progress(
                    PlayerSnapshot(
                        key = key,
                        generation = generation,
                        positionSeconds = positionSeconds,
                        durationSeconds = durationSeconds,
                        isPlaying = isPlaying,
                        isMuted = isMuted
                    )
                )
            )
        }
    }

    class AndroidPlayerBridge {
        @android.webkit.JavascriptInterface
        fun onPlayerReady(generation: Long) {
            dispatchGenerationEvent(generation) { key ->
                hasUsablePlaybackSignal = true
                loadWatchdogJob?.cancel()
                loadWatchdogJob = null
                setLoading(false)
                setError(false)
                PlayerEvent.Ready(key, generation)
            }
        }

        @android.webkit.JavascriptInterface
        fun onPlaybackSnapshot(
            positionSeconds: Double,
            durationSeconds: Double,
            isPlaying: Boolean,
            isMuted: Boolean,
            generation: Long
        ) {
            if (durationSeconds > 0.0 || positionSeconds > 0.0) {
                dispatchGenerationState(generation) {
                    if (activeServerId == StreamService.VIDLINK_SERVER_ID) {
                        // A playing snapshot with real duration proves frames
                        // are rendering, so it arms the watchdog like Ready.
                        // Advancing position counts too: it proves playback
                        // even when the provider's playing flag is stuck.
                        // Metadata-only or paused snapshots still don't arm:
                        // VidLink reports those before its first frame, and
                        // the custom veil stays up until then.
                        val positionAdvanced = !lastVidLinkProgressPosition.isNaN() &&
                            positionSeconds > lastVidLinkProgressPosition + 1.5
                        if (positionSeconds.isFinite()) {
                            lastVidLinkProgressPosition = positionSeconds
                        }
                        if ((isPlaying && durationSeconds > 0.0) || positionAdvanced) {
                            hasUsablePlaybackSignal = true
                            loadWatchdogJob?.cancel()
                            loadWatchdogJob = null
                            setLoading(false)
                            setError(false)
                        } else if (hasUsablePlaybackSignal) {
                            setLoading(false)
                        }
                    } else {
                        hasUsablePlaybackSignal = true
                        setLoading(false)
                    }
                }
            }
            dispatchSnapshot(
                generation,
                positionSeconds,
                durationSeconds,
                isPlaying,
                isMuted
            )
        }

        @android.webkit.JavascriptInterface
        fun onPlayerLoading(generation: Long) {
            dispatchGenerationState(generation) {
                hidePlayerUiInternal()
                setLoading(true)
                setError(false)
            }
        }

        @android.webkit.JavascriptInterface
        fun onPlayerUiVisibilityChanged(visible: Boolean, generation: Long) {
            updatePlayerUiVisibility(visible, generation)
        }

        @android.webkit.JavascriptInterface
        fun onPlayerBuffering(isBuffering: Boolean, generation: Long) {
            dispatchGenerationState(generation) {
                if (!hasPlayerError.value) setLoading(isBuffering)
            }
        }

        @android.webkit.JavascriptInterface
        fun onPlayerError(message: String?, generation: Long) {
            lastKnownIsPlaying = false
            updateBackgroundWakeLock()
            dispatchGenerationState(generation) {
                hidePlayerUiInternal()
                setLoading(false)
                setError(true, message)
                activeMediaKey?.let { key ->
                    onPlayerEvent?.invoke(PlayerEvent.Error(key, generation, message))
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun onVideoEnded(generation: Long) {
            lastKnownIsPlaying = false
            updateBackgroundWakeLock()
            dispatchGenerationEvent(generation) { key -> PlayerEvent.Ended(key, generation) }
        }

        @android.webkit.JavascriptInterface
        fun onVidSrcServerSelected(host: String?) {
            val rawHost = host?.trim().orEmpty()
            if (!StreamService.isVidSrcServerHost(rawHost)) return
            val normalized = StreamService.normalizeVidSrcServerHost(rawHost)
            hostContext?.applicationContext?.let { context ->
                SettingsManager(context).selectedVidSrcServerId = normalized
            }
            mainHandler.post {
                if (activeServerId == StreamService.VIDSRC_SERVER_ID) {
                    activeVidSrcServerHost = normalized
                    currentStreamUrl = currentStreamUrl?.let { currentUrl ->
                        runCatching {
                            Uri.parse(currentUrl).buildUpon().authority(normalized).build().toString()
                        }.getOrNull() ?: currentUrl
                    }
                    onVidSrcServerSelected?.invoke(normalized)
                }
            }
        }

        @android.webkit.JavascriptInterface
        fun onVidSrcServerOrderChanged(orderJson: String?) {
            val order = runCatching {
                val array = JSONArray(orderJson ?: "[]")
                buildList {
                    for (index in 0 until array.length()) {
                        array.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
            }.getOrDefault(emptyList())
            val normalized = StreamService.normalizeVidSrcServerOrder(order)
            hostContext?.applicationContext?.let { context ->
                SettingsManager(context).vidSrcServerOrder = normalized
            }
            mainHandler.post {
                if (activeServerId == StreamService.VIDSRC_SERVER_ID) {
                    onVidSrcServerOrderChanged?.invoke(normalized)
                }
            }
        }
    }

    fun getMediaKey(video: VideoItem, serverId: String): String = video.playbackKey()

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(context: Context): WebView {
        hostContext = context
        persistentWebView?.let { return it }
        return WebView(context.applicationContext).apply {
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.BLACK)

                CookieManager.getInstance().let { cookies ->
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(this, true)
                }

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    allowFileAccess = false
                    allowContentAccess = false
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    cacheMode = WebSettings.LOAD_DEFAULT
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    // Video playback: disable pre-raster of offscreen pages
                    // (saves GPU during fullscreen video) and keep default
                    // render priority bound to the app lifecycle.
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        offscreenPreRaster = false
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
                    userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                }
                // Bound renderer priority keeps the video compositor responsive
                // without pinning high-priority GPU while backgrounded.
                // setRendererPriorityPolicy needs API 26+ (minSdk 24).
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    try {
                        setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_BOUND, true)
                    } catch (_: Exception) {}
                }

                addJavascriptInterface(AndroidPlayerBridge(), "AndroidPlayerBridge")

                webChromeClient = object : WebChromeClient() {
                    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                        if (view == null || callback == null) return
                        FullscreenHelper.findActivity(hostContext ?: context)?.let {
                            FullscreenHelper.showCustomView(it, view, callback)
                        } ?: callback.onCustomViewHidden()
                    }

                    override fun onHideCustomView() {
                        FullscreenHelper.findActivity(hostContext ?: context)?.let {
                            FullscreenHelper.hideCustomView(it)
                        }
                    }

                    override fun onPermissionRequest(request: PermissionRequest?) {
                        // Embed playback does not need camera/microphone access.
                        request?.deny()
                    }

                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: Message?
                    ): Boolean = true
                }

                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        val webView = view ?: return
                        if (webView !== persistentWebView) return
                        setLoading(true)
                        setError(false)
                        StreamAdBlocker.injectAdblockProtection(webView)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val webView = view ?: return
                        if (webView !== persistentWebView || activeMediaKey == null) return
                        val generation = loadGeneration
                        StreamAdBlocker.injectAdblockProtection(webView)
                        if (activeServerId != StreamService.VIDLINK_SERVER_ID) {
                            setLoading(false)
                        }
                        setError(false)
                        webView.evaluateJavascript(
                            PlaybackScript.build(
                                generation = generation,
                                resumePositionSeconds = pendingResumePositionSeconds,
                                autoplay = pendingPlayWhenReady,
                                preferredQuality = pendingQuality,
                                preferredSubtitles = pendingSubtitles,
                                backgroundPlayEnabled = backgroundPlayEnabled
                            ),
                            null
                        )
                        applyPlayerPresentationMode(webView)
                    }

                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        if (StreamAdBlocker.shouldBlockRequest(request?.url?.toString())) {
                            return StreamAdBlocker.createEmptyResponse()
                        }
                        StreamPlayerSkin.interceptPlayerEmbedHtml(request)?.let { return it }
                        StreamPlayerSkin.interceptPlayerJs(request)?.let { return it }
                        StreamPlayerSkin.interceptVidLinkPlayerJs(request)?.let { return it }
                        StreamPlayerSkin.interceptPlayerCss(request)?.let { return it }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: ""
                        val isMainFrame = request?.isForMainFrame == true
                        if (StreamAdBlocker.isRedirectTrap(url, currentStreamUrl)) return true
                        return isMainFrame && !StreamAdBlocker.isAllowedStreamNavigation(url, currentStreamUrl)
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        val requestUrl = request?.url?.toString() ?: ""
                        if (request?.isForMainFrame != true || view !== persistentWebView || activeMediaKey == null) return
                        if (requestUrl.startsWith("about:") || StreamAdBlocker.shouldBlockRequest(requestUrl)) return
                        reportPageError(error?.description?.toString())
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        if (request?.isForMainFrame != true || view !== persistentWebView || activeMediaKey == null) return
                        val statusCode = errorResponse?.statusCode ?: return
                        if (statusCode >= 400) {
                            reportPageError("Embed server returned HTTP $statusCode")
                        }
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        // Never weaken TLS for a provider page.
                        handler?.cancel()
                    }

                    override fun onRenderProcessGone(
                        view: WebView?,
                        detail: RenderProcessGoneDetail?
                    ): Boolean {
                        val key = activeMediaKey
                        val generation = loadGeneration
                        val player = persistentWebView

                        // Clear ownership before notifying the ViewModel. The
                        // recovery callback may immediately create a fresh
                        // WebView; keeping the dead instance referenced until
                        // after that callback would make failover reuse it.
                        persistentWebView = null
                        activeMediaKey = null
                        currentStreamUrl = null
                        activeServerId = null
                        isSpeculativePreload = false
                        speculativePreloadKey = null
                        loadingMaskJob?.cancel()
                        loadingMaskJob = null
                        loadWatchdogJob?.cancel()
                        loadWatchdogJob = null
                        autoplayWatchdogJob?.cancel()
                        autoplayWatchdogJob = null
                        hidePlayerUiInternal()
                        _isCleanOverlayLoading.value = false
                        setLoading(false)
                        setError(false)

                        player?.let { deadPlayer ->
                            (deadPlayer.parent as? ViewGroup)?.removeView(deadPlayer)
                            deadPlayer.destroy()
                        }
                        if (key != null) {
                            mainHandler.post {
                                onPlayerEvent?.invoke(
                                    PlayerEvent.Error(key, generation, "Embed player process stopped")
                                )
                            }
                        }
                        return true
                    }
                }
                persistentWebView = this
            }
    }

    fun loadMedia(
        context: Context,
        video: VideoItem,
        serverId: String,
        resumePositionSeconds: Double = 0.0,
        forceReload: Boolean = false,
        playWhenReady: Boolean = true,
        vidSrcServerHost: String? = null,
        isSpeculative: Boolean = false
    ) {
        val webView = getOrCreateWebView(context)
        val targetKey = video.playbackKey()
        val settings = SettingsManager(context.applicationContext)
        val preferences = settings.getPlaybackPreferences(serverId)
        pendingQuality = preferences.quality.wireValue
        pendingSubtitles = preferences.subtitles.wireValue
        val resolvedVidSrcHost = if (serverId == StreamService.VIDSRC_SERVER_ID) {
            StreamService.normalizeVidSrcServerHost(
                vidSrcServerHost ?: settings.selectedVidSrcServerId
            )
        } else {
            StreamService.DEFAULT_VIDSRC_SERVER_HOST
        }
        val targetUrl = StreamService.buildEmbedUrl(
            mediaType = video.mediaType,
            id = video.tmdbId ?: video.id,
            season = video.currentSeason,
            episode = video.currentEpisode,
            serverId = serverId,
            vidSrcHost = resolvedVidSrcHost
        )
        val shouldLoad = activeMediaKey != targetKey ||
            currentStreamUrl != targetUrl ||
            activeServerId != serverId ||
            forceReload
        if (!shouldLoad) {
            // Promotion: a hidden Continue preload for this exact key is now
            // a real tap. The page is already at the resume point paused —
            // just unpause instead of reloading from 0:00.
            if (!isSpeculative && isSpeculativePreload && speculativePreloadKey == targetKey &&
                activeMediaKey == targetKey && !hasPlayerError.value
            ) {
                isSpeculativePreload = false
                speculativePreloadKey = null
                setError(false)
                requestPlaybackAudioFocus(webView.context)
                pendingPlayWhenReady = playWhenReady
                if (playWhenReady) {
                    play()
                    // The unpause is a postMessage into the nested provider
                    // frame; if that frame missed our injected runtime the
                    // video stays parked behind its Resume gate. Same backstop
                    // as fresh loads so a tap still starts instantly.
                    armAutoplayWatchdog(loadGeneration)
                }
            }
            return
        }
        // A real load for a different title replaces any live preload.
        if (!isSpeculative) {
            isSpeculativePreload = false
            speculativePreloadKey = null
        }

        activeMediaKey = targetKey
        currentStreamUrl = targetUrl
        activeServerId = serverId
        activeVidSrcServerHost = resolvedVidSrcHost
        // Never load an unresolvable URL: an empty target means the media id
        // couldn't be mapped to a provider title (e.g. demo ids). State is
        // assigned first so the error below reaches the ViewModel event path.
        // Surface a fast error instead of a blank player + 18s watchdog.
        if (targetUrl.isBlank()) {
            if (isSpeculative) {
                // Stale preload, not a user-visible error: drop state so a
                // later real tap surfaces the fast error path above.
                isSpeculativePreload = false
                speculativePreloadKey = null
                activeMediaKey = null
                currentStreamUrl = null
                activeServerId = null
                setLoading(false)
                return
            }
            hidePlayerUiInternal()
            setLoading(false)
            reportPageError("This title can't be played: missing provider id")
            return
        }
        // Speculative preloads never take audio focus, wake locks, or audible
        // autoplay: the page loads paused at the resume point.
        if (!isSpeculative) {
            requestPlaybackAudioFocus(webView.context)
        }
        pendingResumePositionSeconds = resumePositionSeconds.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        pendingPlayWhenReady = if (isSpeculative) false else playWhenReady
        lastKnownIsPlaying = if (isSpeculative) false else playWhenReady
        if (isSpeculative) {
            isSpeculativePreload = true
            speculativePreloadKey = targetKey
            setUserPausedFlag(true)
        }
        updateBackgroundWakeLock()
        loadGeneration += 1L
        val generation = loadGeneration
        reportedPageErrorGeneration = -1L
        hasUsablePlaybackSignal = false
        lastVidLinkProgressPosition = Double.NaN
        lastSnapshotEmitMillis = 0L
        lastSnapshotPosition = Double.NaN
        lastSnapshotPlaying = null
        setLoading(true)
        setError(false)
        loadingMaskJob?.cancel()
        loadWatchdogJob?.cancel()
        autoplayWatchdogJob?.cancel()
        autoplayWatchdogJob = null
        loadingMaskJob = mainScope.launch {
            _isCleanOverlayLoading.value = true
            // VidLink keeps its real loading state through isPlayerLoading;
            // this short minimum only hides the provider's first blank frame.
            delay(if (serverId == StreamService.VIDLINK_SERVER_ID) 450L else 3000L)
            _isCleanOverlayLoading.value = false
        }
        loadWatchdogJob = mainScope.launch {
            // Failover must never kill a slow-but-healthy load: providers
            // routinely need 6-12s on real networks (HLS manifest + first
            // segment), so VidLink gets 15s and VidSrc 18s. A premature
            // timeout reloads a half-started player from 0:00 (looks like
            // "paused at 0:00") or flips a working VidLink load to VidSrc.
            delay(if (serverId == StreamService.VIDLINK_SERVER_ID) 15000L else 18000L)
            if (generation == loadGeneration &&
                activeServerId == serverId &&
                !hasUsablePlaybackSignal
            ) {
                reportPageError(
                    if (serverId == StreamService.VIDLINK_SERVER_ID) {
                        "VidLink player timed out while loading"
                    } else {
                        "VidSrc player timed out while loading"
                    }
                )
            }
        }
        // Autoplay backstop (see field): fresh real loads that asked to play
        // must not sit paused behind a provider Resume gate. Three bounded
        // nudges at ~4s/~9s/~15s; each is a no-op once frames are moving,
        // after an explicit pause, or after an error. Speculative preloads
        // stay paused by design.
        if (!isSpeculative && playWhenReady) {
            armAutoplayWatchdog(generation)
        }

        if (serverId == StreamService.VIDSRC_SERVER_ID) {
            val playbackUrl = Uri.parse(targetUrl).buildUpon().apply {
                if (pendingResumePositionSeconds > 0.0) {
                    appendQueryParameter("startAt", pendingResumePositionSeconds.toString())
                }
            }.build().toString()
            val refererUrl = "https://$resolvedVidSrcHost/"
            webView.loadUrl(
                playbackUrl,
                mutableMapOf(
                    "Referer" to refererUrl,
                    "Origin" to refererUrl.removeSuffix("/")
                )
            )
            Log.d(TAG, "Loaded documented VidSrc embed generation $generation for $targetKey via $resolvedVidSrcHost")
        } else {
            // Keep VidLink Pro's existing URL, headers, and page-loading path unchanged.
            val refererUrl = "https://vidlink.pro/"
            webView.loadUrl(
                targetUrl,
                mutableMapOf(
                    "Referer" to refererUrl,
                    "Origin" to refererUrl.removeSuffix("/")
                )
            )
            Log.d(TAG, "Loaded VidLink Pro generation $generation for $targetKey")
        }
    }

    /**
     * Arms the autoplay backstop for [generation]: three bounded nudges while a
     * load that asked to play is still paused with no explicit pause or
     * error. Each nudge re-asserts play into every frame and clicks the
     * provider's landing/resume gates. Self-cancels on the next load.
     */
    private fun armAutoplayWatchdog(generation: Long) {
        autoplayWatchdogJob?.cancel()
        autoplayWatchdogJob = mainScope.launch {
            repeat(3) { attempt ->
                delay(if (attempt == 0) 4000L else if (attempt == 1) 5000L else 6000L)
                if (generation != loadGeneration) return@launch
                if (_hasPlayerError.value) return@launch
                if (!pendingPlayWhenReady) return@launch
                if (System.currentTimeMillis() - lastExplicitPauseAtMillis < 15000L) return@launch
                if (lastKnownIsPlaying) {
                    autoplayWatchdogJob = null
                    return@launch
                }
                Log.d(TAG, "Autoplay watchdog nudge generation $generation (attempt ${attempt + 1})")
                setUserPausedFlag(false)
                dispatchVideoCommand("play")
                persistentWebView?.evaluateJavascript(AUTOPLAY_NUDGE_JS, null)
            }
            autoplayWatchdogJob = null
        }
    }

    fun attachToContainer(
        context: Context,
        container: ViewGroup,
        video: VideoItem,
        serverId: String,
        resumePositionSeconds: Double = 0.0,
        playWhenReady: Boolean = true,
        vidSrcServerHost: String? = null
    ): WebView {
        val webView = getOrCreateWebView(context)
        if (FullscreenHelper.isPlayerViewHosted(webView)) {
            // While a fullscreen episode transition is loading, the
            // persistent WebView is temporarily hosted above Compose. Keep it
            // there until fullscreen is explicitly closed.
            FullscreenHelper.updatePlayerViewRestoreContainer(container)
        } else if (webView.parent !== container) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            container.removeAllViews()
            container.addView(webView, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        loadMedia(
            context = context,
            video = video,
            serverId = serverId,
            resumePositionSeconds = resumePositionSeconds,
            playWhenReady = playWhenReady,
            vidSrcServerHost = vidSrcServerHost
        )
        return webView
    }

    fun detachFromContainerIfCurrent(container: ViewGroup) {
        persistentWebView?.let { webView ->
            if (webView.parent === container) container.removeView(webView)
        }
    }

    fun attachPlayerToContainer(
        context: Context,
        video: VideoItem,
        serverId: String,
        resumePositionSeconds: Double = 0.0,
        playWhenReady: Boolean = true,
        vidSrcServerHost: String? = null
    ): WebView = attachToContainer(
        context,
        FrameLayoutCompat(context),
        video,
        serverId,
        resumePositionSeconds,
        playWhenReady,
        vidSrcServerHost
    )

    fun play() {
        pausedForAudioLoss = false
        lastKnownIsPlaying = true
        updateBackgroundWakeLock()
        setUserPausedFlag(false)
        dispatchVideoCommand("play")
    }

    fun pause() {
        pausedForAudioLoss = false
        lastKnownIsPlaying = false
        lastExplicitPauseAtMillis = System.currentTimeMillis()
        autoplayWatchdogJob?.cancel()
        autoplayWatchdogJob = null
        updateBackgroundWakeLock()
        // The page's screen-off guard must not undo an explicit pause
        // (notification, background-disabled, focus loss): top-level videos
        // are paused directly, bypassing the postMessage path that sets this.
        setUserPausedFlag(true)
        dispatchVideoCommand("pause")
    }

    fun togglePlayPause(isPlaying: Boolean) {
        pausedForAudioLoss = false
        lastKnownIsPlaying = isPlaying
        if (!isPlaying) {
            lastExplicitPauseAtMillis = System.currentTimeMillis()
            autoplayWatchdogJob?.cancel()
            autoplayWatchdogJob = null
        }
        updateBackgroundWakeLock()
        setUserPausedFlag(!isPlaying)
        if (isPlaying) dispatchVideoCommand("play") else dispatchVideoCommand("pause")
    }

    /** Mirrors explicit play/pause intent into the page's hide-guard. */
    private fun setUserPausedFlag(userPaused: Boolean) {
        persistentWebView?.evaluateJavascript(
            "window.__cluUserPaused = ${if (userPaused) "true" else "false"};",
            null
        )
    }

    /**
     * Mirrors the Settings toggle into the provider page (visibility-guard in
     * PlaybackScript) and re-arms the CPU wake hold. Safe to call any time;
     * no-ops when the value is unchanged except for re-asserting the JS flag
     * on the live page.
     */
    fun setBackgroundPlaybackEnabled(enabled: Boolean) {
        backgroundPlayEnabled = enabled
        persistentWebView?.evaluateJavascript(
            "window.__cluBackgroundPlayEnabled = ${if (enabled) "true" else "false"};",
            null
        )
        updateBackgroundWakeLock()
    }

    /**
     * Holds a PARTIAL_WAKE_LOCK while an embed is actively playing with
     * background play ON. A provider-initiated pause (screen-off hidden
     * pause) holds 8s before releasing so the JS resume guard can recover
     * audio; explicit release paths (pause/ended/error/release) still clear
     * immediately via [releaseBackgroundWakeLock].
     */
    private fun updateBackgroundWakeLock() {
        mainHandler.post {
            val shouldHold = backgroundPlayEnabled && lastKnownIsPlaying && activeMediaKey != null
            if (!shouldHold) {
                // Explicitly disabled or no media: release now, cancel grace.
                if (!backgroundPlayEnabled || activeMediaKey == null) {
                    wakeReleaseJob?.cancel()
                    wakeReleaseJob = null
                    backgroundWakeLock?.let { if (it.isHeld) runCatching { it.release() } }
                    return@post
                }
                // Grace already counting down: keep it, don't re-arm.
                if (wakeReleaseJob?.isActive == true) return@post
                // Transient pause (likely screen-off): delay release 8s.
                wakeReleaseJob?.cancel()
                wakeReleaseJob = mainScope.launch {
                    delay(8000L)
                    if (!lastKnownIsPlaying && backgroundWakeLock?.isHeld == true) {
                        runCatching { backgroundWakeLock?.let { if (it.isHeld) it.release() } }
                    }
                    wakeReleaseJob = null
                }
                return@post
            }
            wakeReleaseJob?.cancel()
            wakeReleaseJob = null
            try {
                val appContext = hostContext?.applicationContext
                    ?: persistentWebView?.context?.applicationContext
                    ?: return@post
                var lock = backgroundWakeLock
                if (lock == null) {
                    val pm = appContext.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                        ?: return@post
                    lock = pm.newWakeLock(
                        android.os.PowerManager.PARTIAL_WAKE_LOCK,
                        "CluTube:BackgroundPlayback"
                    ).apply { setReferenceCounted(false) }
                    backgroundWakeLock = lock
                }
                if (!lock.isHeld) lock.acquire()
            } catch (_: Exception) {
            }
        }
    }

    private fun releaseBackgroundWakeLock() {
        lastKnownIsPlaying = false
        wakeReleaseJob?.cancel()
        wakeReleaseJob = null
        try {
            backgroundWakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
    }

    fun setMuted(isMuted: Boolean) {
        dispatchVideoCommand("setMuted", if (isMuted) 1.0 else 0.0)
    }

    fun seekTo(positionSeconds: Double) {
        val safe = positionSeconds.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        dispatchVideoCommand("seekTo", safe)
        requestPlaybackSnapshotAfterSeek()
    }

    fun seekBy(deltaSeconds: Double) {
        val safe = deltaSeconds.takeIf { it.isFinite() } ?: 0.0
        dispatchVideoCommand("seekBy", safe)
        requestPlaybackSnapshotAfterSeek()
    }

    fun setPlaybackRate(rate: Double) {
        val safe = rate.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
        dispatchVideoCommand("playbackRate", safe)
    }

    fun setQuality(quality: PlaybackQuality) {
        pendingQuality = quality.wireValue
        dispatchPreferenceCommand("quality", pendingQuality)
    }

    fun setSubtitles(subtitles: SubtitlePreference) {
        pendingSubtitles = subtitles.wireValue
        dispatchPreferenceCommand("subtitles", pendingSubtitles)
    }

    /**
     * Claims transient-exclusive music focus for embed playback so navigation
     * prompts and other media pause/duck instead of mixing. No-op when
     * already held. Callers pass any Context; the app context is used.
     */
    private fun requestPlaybackAudioFocus(context: Context) {
        val appContext = context.applicationContext
        val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (audioFocusRequest != null) return
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener, mainHandler)
                .build()
            if (audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                audioFocusRequest = request
            }
        } else {
            @Suppress("DEPRECATION")
            if (!legacyAudioFocusHeld && audioManager.requestAudioFocus(
                    audioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            ) {
                legacyAudioFocusHeld = true
            }
        }
    }

    private fun abandonPlaybackAudioFocus(context: Context?) {
        val appContext = context?.applicationContext
        val audioManager = appContext?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else if (legacyAudioFocusHeld) {
            @Suppress("DEPRECATION")
            audioManager?.abandonAudioFocus(null)
            legacyAudioFocusHeld = false
        }
    }

    /** Uses Android's music stream so hardware buttons and the system volume UI stay authoritative. */
    fun adjustDeviceVolume(context: Context, direction: Int) {        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val adjustment = when {
            direction > 0 -> AudioManager.ADJUST_RAISE
            direction < 0 -> AudioManager.ADJUST_LOWER
            else -> AudioManager.ADJUST_SAME
        }
        if (adjustment != AudioManager.ADJUST_SAME) {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, adjustment, 0)
        }
    }

    /** Adjusts only this activity's brightness; it does not change the system setting. */
    fun adjustScreenBrightness(context: Context, delta: Float) {
        val activity = findActivity(context) ?: return
        val attributes = activity.window.attributes
        val current = attributes.screenBrightness
            .takeIf { it in 0f..1f }
            ?: 0.5f
        attributes.screenBrightness = (current + delta).coerceIn(0.05f, 1f)
        activity.window.attributes = attributes
    }

    fun toggleFullscreen() = requestNativeFullscreen()

    /**
     * Requests true native fullscreen from the provider element. Called when
     * Continue Watching auto-fullscreens after Ready: Chromium then fires
     * onShowCustomView -> FullscreenHelper decor layer (correct aspect +
     * UpNext/skip overlays). Tries provider players first, falls back to
     * HTML5 fullscreen, then document fullscreen.
     */
    fun requestNativeFullscreen() {
        persistentWebView?.evaluateJavascript(
            """
            (function() {
                try {
                    var jw = null;
                    try {
                        if (typeof window.jwplayer === 'function') {
                            try { jw = window.jwplayer(document.querySelector('.jwplayer')) || window.jwplayer(); } catch (_) {}
                        }
                        if (!jw && window.__JW && typeof window.__JW.setFullscreen === 'function') jw = window.__JW;
                    } catch (_) {}
                    if (jw) {
                        try { if (typeof jw.setFullscreen === 'function') { jw.setFullscreen(true); return 'jw'; } } catch (_) {}
                        try { if (typeof jw.setFullScreen === 'function') { jw.setFullScreen(true); return 'jw'; } } catch (_) {}
                    }
                    try {
                        var vjs = window.videojs && window.videojs.players ? window.videojs.players[Object.keys(window.videojs.players)[0]] : null;
                        if (vjs && typeof vjs.requestFullscreen === 'function') { vjs.requestFullscreen(); return 'vjs'; }
                        if (vjs && typeof vjs.enterFullWindow === 'function') { vjs.enterFullWindow(); return 'vjs-window'; }
                    } catch (_) {}
                    var v = document.querySelector('video');
                    if (v) {
                        try { if (typeof v.requestFullscreen === 'function') { v.requestFullscreen(); return 'video'; } } catch (_) {}
                        try { if (typeof v.webkitEnterFullscreen === 'function') { v.webkitEnterFullscreen(); return 'webkit'; } } catch (_) {}
                    }
                    var root = document.querySelector('.jwplayer') || document.querySelector('.video-js') || document.querySelector('#player') || document.documentElement;
                    try { if (root && typeof root.requestFullscreen === 'function') { root.requestFullscreen(); return 'root'; } } catch (_) {}
                } catch (_) {}
                return 'none';
            })();
            """.trimIndent(),
            null
        )
    }

    /** Warms the pooled WebView + DNS so the first Continue tap has no init jank. */
    fun prewarm(context: Context) {
        mainHandler.post {
            runCatching { getOrCreateWebView(context.applicationContext) }
        }
    }

    /**
     * Option-B hidden preload for the MRU Continue Watching title. Loads the
     * provider embed paused at [resumePositionSeconds] without audio focus or
     * wake lock. Must run on the main thread (WebView) — posts internally so
     * callers can invoke from any dispatcher. No-op when real playback is
     * active; the warmer guards that, and a second guard lives here so a race
     * with a fresh tap can never evict audible playback.
     */
    fun preloadContinueMedia(
        context: Context,
        video: VideoItem,
        serverId: String,
        resumePositionSeconds: Double = 0.0,
        vidSrcServerHost: String? = null
    ) {
        mainHandler.post {
            // Never evict audible playback for a speculative load.
            if (activeMediaKey != null && !isSpeculativePreload) return@post
            val targetKey = video.playbackKey()
            if (isSpeculativePreload && speculativePreloadKey == targetKey) return@post
            runCatching {
                loadMedia(
                    context = context.applicationContext,
                    video = video,
                    serverId = serverId,
                    resumePositionSeconds = resumePositionSeconds,
                    forceReload = false,
                    playWhenReady = false,
                    vidSrcServerHost = vidSrcServerHost,
                    isSpeculative = true
                )
            }
            Log.d(TAG, "Speculative Continue preload issued for $targetKey")
        }
    }

    /**
     * Drops a stale preload without destroying the pooled WebView. Called when
     * history/server changes make the preloaded key obsolete, or when a
     * speculative load errored and a real tap must force a fresh reload.
     */
    fun clearSpeculativePreload() {
        if (!isSpeculativePreload && speculativePreloadKey == null) return
        isSpeculativePreload = false
        speculativePreloadKey = null
    }

    fun requestPlaybackSnapshot(onComplete: (() -> Unit)? = null) {
        val webView = persistentWebView
        if (webView == null) {
            onComplete?.invoke()
            return
        }
        webView.evaluateJavascript(
            "(function(){if(window.__cluReportPlayback)window.__cluReportPlayback();})();"
        ) {
            mainHandler.post { onComplete?.invoke() }
        }
    }

    fun reloadCurrentPlayer(
        context: Context,
        video: VideoItem,
        serverId: String,
        resumePositionSeconds: Double = pendingResumePositionSeconds,
        playWhenReady: Boolean = pendingPlayWhenReady,
        vidSrcServerHost: String? = activeVidSrcServerHost
    ) {
        loadMedia(
            context = context,
            video = video,
            serverId = serverId,
            resumePositionSeconds = resumePositionSeconds,
            forceReload = true,
            playWhenReady = playWhenReady,
            vidSrcServerHost = vidSrcServerHost
        )
    }

    /**
     * The provider page stays alive while the app changes between the full
     * Watch page and the floating player. Hide provider controls in the small
     * surface so the app's own mini-player controls remain the only chrome.
     */
    fun setMiniPlayerMode(enabled: Boolean) {
        // Called from Compose on every player recomposition; the CSS
        // injection below stalls the provider page, so skip no-ops.
        if (miniPlayerMode == enabled) return
        miniPlayerMode = enabled
        if (enabled) hidePlayerUiInternal()
        persistentWebView?.let(::applyPlayerPresentationMode)
        // The nested provider frame is cross-origin: top-document CSS can't
        // reach its captions. Broadcast mini mode so the injected command
        // runtime toggles html.clu-mini there (caption pinning).
        dispatchVideoCommand("miniMode", if (enabled) 1.0 else 0.0)
    }

    fun releasePlayer() {
        releaseLegacyPlayer()
    }

    fun releaseLegacyPlayer() {
        hidePlayerUiInternal()
        abandonPlaybackAudioFocus(hostContext)
        releaseBackgroundWakeLock()
        try {
            backgroundWakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
        backgroundWakeLock = null
        // Drop the Activity reference: the WebView itself runs on the app
        // context, hostContext is only needed for fullscreen lookup.
        hostContext = null
        loadingMaskJob?.cancel()
        loadingMaskJob = null
        loadWatchdogJob?.cancel()
        loadWatchdogJob = null
        autoplayWatchdogJob?.cancel()
        autoplayWatchdogJob = null
        _isCleanOverlayLoading.value = false
        loadGeneration += 1L
        persistentWebView?.let { webView ->
            try {
                (webView.parent as? ViewGroup)?.removeView(webView)
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.clearHistory()
                webView.removeAllViews()
                webView.destroy()
            } catch (_: Exception) {
            }
        }
        persistentWebView = null
        activeMediaKey = null
        currentStreamUrl = null
        activeServerId = null
        isSpeculativePreload = false
        speculativePreloadKey = null
        activeVidSrcServerHost = StreamService.DEFAULT_VIDSRC_SERVER_HOST
        pendingResumePositionSeconds = 0.0
        pendingPlayWhenReady = true
        pendingQuality = PlaybackQuality.AUTO.wireValue
        pendingSubtitles = SubtitlePreference.OFF.wireValue
        hasUsablePlaybackSignal = false
        lastVidLinkProgressPosition = Double.NaN
        miniPlayerMode = false
        setLoading(false)
        setError(false)
    }

    private fun applyPlayerPresentationMode(webView: WebView) {
        // The CSS payload is ~5KB of evaluateJavascript. Without this gate it
        // ran on every onPageFinished + every mini-mode toggle (jank). Key on
        // miniMode + generation so repeats are free.
        val modeKey = "$miniPlayerMode|$loadGeneration"
        if (lastPresentationModeKey == modeKey) return
        lastPresentationModeKey = modeKey
        val miniMode = miniPlayerMode
        webView.evaluateJavascript(
            """
            (function() {
                var oldGlassStyle = document.getElementById('clu-liquid-glass-style');
                if (oldGlassStyle) oldGlassStyle.remove();
                var youtubeStyleId = 'clu-youtube-player-style';
                var youtubeStyle = document.getElementById(youtubeStyleId);
                if (!youtubeStyle) {
                    youtubeStyle = document.createElement('style');
                    youtubeStyle.id = youtubeStyleId;
                    (document.head || document.documentElement).appendChild(youtubeStyle);
                }
                youtubeStyle.textContent = `
                    /* YouTube treatment for the outer VidSrc shell and any
                       same-document Video.js player. */
                    #vs-bar {
                        top: 0;
                        left: 0;
                        right: 0;
                        padding: 14px 16px 36px;
                        border: 0;
                        border-radius: 0;
                        color: #fff;
                        text-shadow: 0 1px 3px rgba(0,0,0,.7);
                        background: linear-gradient(to bottom, rgba(0,0,0,.72), rgba(0,0,0,0));
                        box-shadow: none;
                        -webkit-backdrop-filter: none;
                        backdrop-filter: none;
                    }
                    #vs-bar.show { transform: translateY(0); }
                    #vs-title { color: #fff; text-shadow: 0 1px 3px rgba(0,0,0,.7); }
                    #vs-bar select {
                        color: #fff;
                        background: rgba(28,28,28,.94);
                        border-color: rgba(255,255,255,.18);
                    }
                    .video-js {
                        border-radius: 0;
                        overflow: hidden;
                    }
                    .video-js .vjs-control-bar {
                        left: 0;
                        right: 0;
                        bottom: 0;
                        width: 100%;
                        min-height: 48px;
                        padding: 0 12px 8px;
                        border: 0;
                        border-radius: 0;
                        color: #fff;
                        background: linear-gradient(to top, rgba(0,0,0,.86), rgba(0,0,0,0));
                        box-shadow: none;
                        -webkit-backdrop-filter: none;
                        backdrop-filter: none;
                    }
                    .video-js .vjs-control,
                    .video-js .vjs-current-time,
                    .video-js .vjs-duration,
                    .video-js .vjs-time-divider { color: #fff; }
                    .video-js .vjs-play-progress { background: #f00; }
                    .video-js .vjs-volume-level { background: #fff; }
                    .video-js .vjs-volume-panel { display: none; }
                    .video-js .vjs-slider { background: rgba(255,255,255,.42); }
                    .video-js .vjs-big-play-button {
                        color: #fff;
                        border: 0;
                        border-radius: 50%;
                        background: transparent;
                        box-shadow: none;
                    }
                    .video-js .vjs-menu,
                    .video-js .vjs-settings-menu {
                        color: #fff;
                        border: 1px solid rgba(255,255,255,.14);
                        border-radius: 12px;
                        background: rgba(28,28,28,.98);
                        box-shadow: 0 8px 28px rgba(0,0,0,.55);
                        -webkit-backdrop-filter: none;
                        backdrop-filter: none;
                    }
                `;
                var styleId = 'clu-mini-player-style';
                var style = document.getElementById(styleId);
                if ($miniMode) {
                    var miniCss =
                        '.jwplayer .jw-controls,.jwplayer .jw-controlbar,' +
                        '.jwplayer .jw-display-icon-container,.jwplayer .jw-title,' +
                        '.video-js .vjs-control-bar,.video-js .vjs-big-play-button,' +
                        '.vjs-control-bar { opacity:0!important; visibility:hidden!important; pointer-events:none!important; }' +
                        // Captions are bottom-anchored above the control
                        // bar. With the bar hidden in the floating window
                        // that gap strands cues high up — pin them low.
                        '.jwplayer .jw-captions,.jwplayer .jw-caption,' +
                        '.video-js .vjs-text-track-display { bottom:4px!important; padding-bottom:0!important; }';
                    if (!style) {
                        style = document.createElement('style');
                        style.id = styleId;
                        style.textContent = miniCss;
                        (document.head || document.documentElement).appendChild(style);
                    } else if (style.textContent !== miniCss) {
                        style.textContent = miniCss;
                    }
                    document.querySelectorAll('video').forEach(function(video) {
                        if (video.__cluControlsBeforeMini === undefined) {
                            video.__cluControlsBeforeMini = video.controls;
                        }
                        video.controls = false;
                    });
                    if (document.activeElement && document.activeElement.blur) {
                        document.activeElement.blur();
                    }
                } else {
                    if (style) style.remove();
                    document.querySelectorAll('video').forEach(function(video) {
                        if (video.__cluControlsBeforeMini !== undefined) {
                            video.controls = video.__cluControlsBeforeMini;
                            delete video.__cluControlsBeforeMini;
                        }
                    });
                }
            })();
            """.trimIndent(),
            null
        )
    }

    private fun dispatchVideoCommand(action: String, value: Double? = null) {
        val safeValue = value
            ?.takeIf { it.isFinite() }
            ?.toString()
            ?: "null"
        persistentWebView?.evaluateJavascript(
            """
            (function() {
                var command = {
                    type: 'CLUTUBE_PLAYER_COMMAND',
                    action: '$action',
                    value: $safeValue
                };
                function applyToVideo(video) {
                    if (!video) return;
                    try {
                        if (command.action === 'play') video.play();
                        else if (command.action === 'pause') video.pause();
                        else if (command.action === 'setMuted') {
                            video.muted = command.value === 1;
                            video.defaultMuted = video.muted;
                        } else if (command.action === 'seekTo') {
                            video.currentTime = Math.max(0, command.value || 0);
                        } else if (command.action === 'seekBy') {
                            video.currentTime = Math.max(0, video.currentTime + (command.value || 0));
                        } else if (command.action === 'playbackRate') {
                            video.playbackRate = command.value > 0 ? command.value : 1;
                        }
                    } catch (_) {}
                }
                document.querySelectorAll('video').forEach(applyToVideo);
                document.querySelectorAll('iframe').forEach(function(frame) {
                    try { if (frame.contentWindow) frame.contentWindow.postMessage(command, '*'); } catch (_) {}
                });
            })();
            """.trimIndent(),
            null
        )
    }

    private fun dispatchPreferenceCommand(action: String, value: String) {
        val safeValue = value.filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        val safeQuality = (if (action == "quality") value else pendingQuality)
            .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        val safeSubtitles = (if (action == "subtitles") value else pendingSubtitles)
            .filter { it.isLetterOrDigit() || it == '_' || it == '-' }
        persistentWebView?.evaluateJavascript(
            """
            (function() {
                var command = {
                    type: 'CLUTUBE_PLAYER_PREFERENCE',
                    action: '$action',
                    value: '$safeValue',
                    quality: '$safeQuality',
                    subtitles: '$safeSubtitles'
                };
                try {
                    if (window.__cluApplyPlaybackPreference) {
                        window.__cluApplyPlaybackPreference(command);
                    }
                } catch (_) {}
                document.querySelectorAll('video').forEach(function(video) {
                    try {
                        video.__cluPreferredQuality = command.quality;
                        video.__cluPreferredSubtitles = command.subtitles;
                    } catch (_) {}
                });
                document.querySelectorAll('iframe').forEach(function(frame) {
                    try { if (frame.contentWindow) frame.contentWindow.postMessage(command, '*'); } catch (_) {}
                });
            })();
            """.trimIndent(),
            null
        )
    }

    private fun requestPlaybackSnapshotAfterSeek() {
        // Provider seek events are not consistent across VidSrc mirrors and
        // VidLink. Ask the active video for a fresh position as a fallback.
        mainHandler.postDelayed({ requestPlaybackSnapshot() }, 180L)
    }

    private fun evaluateVideo(command: String) {
        persistentWebView?.evaluateJavascript(
            "(function(){var v=document.querySelector('video');if(!v)return;try{v.$command}catch(_){}})();",
            null
        )
    }

    private fun dispatchGenerationEvent(generation: Long, factory: (String) -> PlayerEvent) {
        mainHandler.post {
            val key = activeMediaKey ?: return@post
            if (generation != loadGeneration) return@post
            onPlayerEvent?.invoke(factory(key))
        }
    }

    private fun dispatchGenerationState(generation: Long, update: () -> Unit) {
        mainHandler.post {
            if (activeMediaKey == null || generation != loadGeneration) return@post
            update()
        }
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context? = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }

    /** Tiny container used only for the legacy PlayerUtils compatibility API. */
    private class FrameLayoutCompat(context: Context) : android.widget.FrameLayout(context)
}
