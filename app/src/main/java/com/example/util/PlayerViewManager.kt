package com.example.util

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
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
    private var reportedPageErrorGeneration = -1L
    private var loadingMaskJob: Job? = null
    private var loadWatchdogJob: Job? = null
    private var hasUsablePlaybackSignal = false
    private var miniPlayerMode = false
    private var playerUiHideRunnable: Runnable? = null

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
                        // VidLink can report metadata before its first frame
                        // is available. Its custom veil should remain until
                        // PlaybackScript confirms a playable video element.
                        if (hasUsablePlaybackSignal) setLoading(false)
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
        if (persistentWebView == null) {
            persistentWebView = WebView(context.applicationContext).apply {
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
                    userAgentString =
                        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
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
                                preferredSubtitles = pendingSubtitles
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
                        loadingMaskJob?.cancel()
                        loadingMaskJob = null
                        loadWatchdogJob?.cancel()
                        loadWatchdogJob = null
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
            }
        }
        return persistentWebView!!
    }

    fun loadMedia(
        context: Context,
        video: VideoItem,
        serverId: String,
        resumePositionSeconds: Double = 0.0,
        forceReload: Boolean = false,
        playWhenReady: Boolean = true,
        vidSrcServerHost: String? = null
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
        if (!shouldLoad) return

        hidePlayerUiInternal()

        val restoreContainer = webView.parent as? ViewGroup
        val preserveFullscreen = shouldLoad &&
            FullscreenHelper.isFullscreen.value &&
            FullscreenHelper.isCustomViewActive
        if (preserveFullscreen) {
            FullscreenHelper.prepareForPlayerReload()
            FullscreenHelper.attachPlayerViewForReload(webView, restoreContainer)
        }

        activeMediaKey = targetKey
        currentStreamUrl = targetUrl
        activeServerId = serverId
        activeVidSrcServerHost = resolvedVidSrcHost
        pendingResumePositionSeconds = resumePositionSeconds.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0
        pendingPlayWhenReady = playWhenReady
        loadGeneration += 1L
        val generation = loadGeneration
        reportedPageErrorGeneration = -1L
        hasUsablePlaybackSignal = false
        setLoading(true)
        setError(false)
        loadingMaskJob?.cancel()
        loadWatchdogJob?.cancel()
        loadingMaskJob = mainScope.launch {
            _isCleanOverlayLoading.value = true
            // VidLink keeps its real loading state through isPlayerLoading;
            // this short minimum only hides the provider's first blank frame.
            delay(if (serverId == StreamService.VIDLINK_SERVER_ID) 450L else 3000L)
            _isCleanOverlayLoading.value = false
        }
        loadWatchdogJob = mainScope.launch {
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

    fun play() = dispatchVideoCommand("play")

    fun pause() = dispatchVideoCommand("pause")

    fun togglePlayPause(isPlaying: Boolean) {
        if (isPlaying) play() else pause()
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

    /** Uses Android's music stream so hardware buttons and the system volume UI stay authoritative. */
    fun adjustDeviceVolume(context: Context, direction: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
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

    fun toggleFullscreen() = evaluateVideo("requestFullscreen&&requestFullscreen()")

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
        miniPlayerMode = enabled
        if (enabled) hidePlayerUiInternal()
        persistentWebView?.let(::applyPlayerPresentationMode)
    }

    fun releasePlayer() {
        releaseLegacyPlayer()
    }

    fun releaseLegacyPlayer() {
        hidePlayerUiInternal()
        loadingMaskJob?.cancel()
        loadingMaskJob = null
        loadWatchdogJob?.cancel()
        loadWatchdogJob = null
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
        activeVidSrcServerHost = StreamService.DEFAULT_VIDSRC_SERVER_HOST
        pendingResumePositionSeconds = 0.0
        pendingPlayWhenReady = true
        pendingQuality = PlaybackQuality.AUTO.wireValue
        pendingSubtitles = SubtitlePreference.OFF.wireValue
        hasUsablePlaybackSignal = false
        miniPlayerMode = false
        setLoading(false)
        setError(false)
    }

    private fun applyPlayerPresentationMode(webView: WebView) {
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
                    if (!style) {
                        style = document.createElement('style');
                        style.id = styleId;
                        style.textContent =
                            '.jwplayer .jw-controls,.jwplayer .jw-controlbar,' +
                            '.jwplayer .jw-display-icon-container,.jwplayer .jw-title,' +
                            '.video-js .vjs-control-bar,.video-js .vjs-big-play-button,' +
                            '.vjs-control-bar { opacity:0!important; visibility:hidden!important; pointer-events:none!important; }';
                        (document.head || document.documentElement).appendChild(style);
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
