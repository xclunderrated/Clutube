package com.example

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.model.DeviceLayoutMode
import com.example.model.MediaType
import com.example.model.VideoItem
import com.example.model.playbackKey
import com.example.model.titleGroupKey
import com.example.model.toContinueUiModel
import com.example.ui.components.BottomNavBar
import com.example.ui.components.CommentsBottomSheet
import com.example.ui.components.CreateSheet
import com.example.ui.components.ClutubeLaunchIntro
import com.example.ui.components.FloatingVideoPlayer
import com.example.ui.components.QueueSheet
import com.example.ui.components.SideNavRail
import com.example.ui.components.StreamServerDialog
import com.example.ui.components.YouTubePlayer
import com.example.ui.components.YouTubeTopAppBar
import com.example.ui.screens.ChannelScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.NotificationsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.ShortsScreen
import com.example.ui.screens.SubscriptionsScreen
import com.example.ui.screens.WatchScreen
import com.example.ui.screens.YouScreen
import com.example.ui.theme.YouTubeTheme
import com.example.viewmodel.YouTubeViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: YouTubeViewModel by viewModels()
    private var mediaSession: MediaSession? = null

    private val _isInPipMode = MutableStateFlow(false)
    val isInPipMode: StateFlow<Boolean> = _isInPipMode.asStateFlow()

    // Voice search (SpeechRecognizer + RECORD_AUDIO). Created per request and
    // destroyed after a result/error so the mic is never held open.
    private var speechRecognizer: SpeechRecognizer? = null

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PIP_PLAY_PAUSE -> {
                    viewModel.togglePlayPause()
                }
                ACTION_PIP_NEXT -> {
                    viewModel.playNextEpisode()
                }
                // Notification-shade / lock-screen transport controls route
                // through the same ViewModel calls so playback state stays
                // consistent no matter where the tap originates. Offline
                // playback handles TOGGLE via its own receiver inside
                // OfflineVideoPlayer (it owns the ExoPlayer), so only drive
                // the online player here.
                com.example.data.playback.PlaybackService.ACTION_TOGGLE -> {
                    if (viewModel.uiState.value.currentPlayingVideo != null) {
                        viewModel.togglePlayPause()
                        // The STARTED collector below is inactive while the app is
                        // backgrounded, so refresh the notification explicitly.
                        syncPlaybackService(force = true)
                    } else if (viewModel.uiState.value.offlinePlayingTitle != null) {
                        // Offline player owns its ExoPlayer and handles TOGGLE
                        // via its own receiver; just repost our copy of state.
                        syncPlaybackService(force = true)
                    }
                }
                com.example.data.playback.PlaybackService.ACTION_NEXT -> {
                    if (viewModel.uiState.value.currentPlayingVideo != null ||
                        viewModel.uiState.value.queue.isNotEmpty()
                    ) {
                        viewModel.playNextEpisode()
                        syncPlaybackService(force = true)
                    }
                }
                android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY -> {
                    // Headphones unplugged: pause so audio doesn't blast.
                    if (viewModel.uiState.value.isPlaying &&
                        viewModel.uiState.value.currentPlayingVideo != null
                    ) {
                        viewModel.togglePlayPause()
                        syncPlaybackService(force = true)
                    }
                }
            }
        }
    }

    // Listens for notification-bar actions on download notifications.
    private val downloadServiceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                com.example.data.download.DownloadService.ACTION_PAUSE_ALL -> {
                    viewModel.pauseAllDownloads()
                }
                com.example.data.download.DownloadService.ACTION_RETRY_DOWNLOAD -> {
                    val id = intent.getStringExtra(
                        com.example.data.download.DownloadService.EXTRA_DOWNLOAD_ID
                    )
                    if (!id.isNullOrBlank()) viewModel.retryDownload(id)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Register PiP action receiver
        val filter = IntentFilter().apply {
            addAction(ACTION_PIP_PLAY_PAUSE)
            addAction(ACTION_PIP_NEXT)
            addAction(com.example.data.playback.PlaybackService.ACTION_TOGGLE)
            addAction(com.example.data.playback.PlaybackService.ACTION_NEXT)
            addAction(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        }
        ContextCompat.registerReceiver(
            this,
            pipReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Register the download notification action receiver
        // ("Pause all" on the progress notification, "Retry" on failures).
        ContextCompat.registerReceiver(
            this,
            downloadServiceReceiver,
            IntentFilter().apply {
                addAction(com.example.data.download.DownloadService.ACTION_PAUSE_ALL)
                addAction(com.example.data.download.DownloadService.ACTION_RETRY_DOWNLOAD)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

        // Request notification permission on Android 13+ so the foreground download
        // notification is actually visible to the user.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST_CODE)
            }
        }

        viewModel.initSettings(applicationContext)
        com.example.data.torrent.TorrentEngine.initialize(applicationContext)
        setupMediaSession()
        handleDeepLink(intent)

        // PiP params only need the foreground: entering PiP from background
        // is not allowed and screen-off never calls onUserLeaveHint.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updatePipParams(
                        state.currentPlayingVideo,
                        state.isPlaying,
                        hasNext = viewModel.hasNextTarget()
                    )
                }
            }
        }
        // Notification + MediaSession must keep updating after screen-off /
        // backgrounding (repeatOnLifecycle above goes inactive there), and the
        // service must already be foreground before that happens to satisfy
        // Android 12+ FGS-start rules — hence an unconditional collector.
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                updateMediaSession(state.currentPlayingVideo, state.isPlaying, state.currentPlaybackSnapshot)
                syncPlaybackService()
            }
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val inPip by isInPipMode.collectAsState()

            YouTubeTheme(darkTheme = uiState.isDarkMode) {
                YouTubeApp(
                    viewModel = viewModel,
                    isInPipMode = inPip
                )
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        _isInPipMode.value = isInPictureInPictureMode
        com.example.util.PlayerViewManager.setMiniPlayerMode(isInPictureInPictureMode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        // Taps on download notifications land here and open Downloads.
        if (intent?.getBooleanExtra(
                com.example.data.download.DownloadService.EXTRA_OPEN_DOWNLOADS, false
            ) == true
        ) {
            viewModel.openDownloadsScreen()
            return
        }
        val data = intent?.data ?: return
        if (data.scheme.equals("magnet", ignoreCase = true) || data.toString().startsWith("magnet:?")) {
            viewModel.openDownloadsScreen()
            viewModel.addCustomMagnet(data.toString())
            return
        }
        if (intent.action != Intent.ACTION_VIEW || data.scheme != "clutube" || data.host != "watch") return
        val videoId = data.pathSegments.firstOrNull()?.takeIf { it.isNotBlank() } ?: return
        viewModel.openSharedVideo(
            videoId = videoId,
            title = data.getQueryParameter("title"),
            season = data.getQueryParameter("season")?.toIntOrNull(),
            episode = data.getQueryParameter("episode")?.toIntOrNull(),
            notificationId = data.getQueryParameter("notification_id")
        )
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val state = viewModel.uiState.value
        // Automatically enter PiP when leaving app if video is actively playing
        if (!isInPictureInPictureMode && state.currentPlayingVideo != null && state.isPlaying) {
            enterPipMode()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVoiceListening()
        try {
            unregisterReceiver(pipReceiver)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(downloadServiceReceiver)
        } catch (_: Exception) {}
        // Only take the notification down when the activity is really going
        // away. Rotation / PiP transitions recreate the activity without
        // killing the WebView host, so STOP there would cut background audio.
        if (isFinishing) {
            try {
                startService(
                    Intent(this, com.example.data.playback.PlaybackService::class.java)
                        .setAction(com.example.data.playback.PlaybackService.ACTION_STOP)
                )
            } catch (_: Exception) {}
        }
        mediaSession?.run {
            isActive = false
            release()
        }
        mediaSession = null
    }

    override fun onStop() {
        // With background play OFF, pausing here restores legacy behavior
        // (screen-off stops audio). With it ON this is a no-op and the
        // foreground service + wake lock keep audio alive.
        viewModel.pauseForBackgroundIfDisabled()
        viewModel.flushPlaybackProgress()
        // Persist the paused state to the notification immediately; the
        // unconditional collector above may not emit again before stop.
        syncPlaybackService(force = true)
        super.onStop()
    }

    override fun onStart() {
        super.onStart()
        viewModel.refreshNotifications()
    }

    override fun onPause() {
        viewModel.flushPlaybackProgress()
        super.onPause()
    }

    private fun createPipActions(isPlaying: Boolean, hasNext: Boolean): List<RemoteAction> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()

        val actions = mutableListOf<RemoteAction>()

        // Play / Pause Action
        val playPauseIcon = if (isPlaying) {
            Icon.createWithResource(this, android.R.drawable.ic_media_pause)
        } else {
            Icon.createWithResource(this, android.R.drawable.ic_media_play)
        }
        val playPauseTitle = if (isPlaying) "Pause" else "Play"
        val playPauseIntent = PendingIntent.getBroadcast(
            this,
            101,
            Intent(ACTION_PIP_PLAY_PAUSE).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        actions.add(RemoteAction(playPauseIcon, playPauseTitle, playPauseTitle, playPauseIntent))

        // Next action follows the unified Next target (queue / episode /
        // movie), not TV-only.
        if (hasNext) {
            val nextIcon = Icon.createWithResource(this, android.R.drawable.ic_media_next)
            val nextTitle = "Next"
            val nextIntent = PendingIntent.getBroadcast(
                this,
                102,
                Intent(ACTION_PIP_NEXT).setPackage(packageName),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            actions.add(RemoteAction(nextIcon, nextTitle, nextTitle, nextIntent))
        }

        return actions
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    /** Entry point for the search-screen mic button. */
    fun startVoiceSearch() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            viewModel.showFeedback("Voice search isn't available on this device")
            return
        }
        if (
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startVoiceListening()
        } else {
            requestPermissions(
                arrayOf(Manifest.permission.RECORD_AUDIO),
                VOICE_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == VOICE_PERMISSION_REQUEST_CODE) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
                startVoiceListening()
            } else {
                viewModel.showFeedback("Microphone permission needed for voice search")
            }
        }
    }

    private fun startVoiceListening() {
        stopVoiceListening()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onError(error: Int) {
                stopVoiceListening()
                // Silence the no-speech timeout into a gentle hint; every
                // other error gets an explicit message.
                viewModel.showFeedback(
                    if (error == SpeechRecognizer.ERROR_NO_MATCH ||
                        error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    ) {
                        "Didn't catch that — try again"
                    } else {
                        "Voice search failed (code $error)"
                    }
                )
            }

            override fun onResults(results: Bundle?) {
                stopVoiceListening()
                val transcript = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                if (transcript.isNullOrBlank()) {
                    viewModel.showFeedback("Didn't catch that — try again")
                } else {
                    viewModel.submitSearch(transcript)
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        val started = runCatching {
            recognizer.startListening(intent)
            true
        }.getOrDefault(false)
        if (started) {
            viewModel.showFeedback("Listening… speak now")
        } else {
            stopVoiceListening()
            viewModel.showFeedback("Couldn't start voice search")
        }
    }

    private fun stopVoiceListening() {
        runCatching {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        }
        speechRecognizer = null
    }

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "CluTubePlayback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (!viewModel.uiState.value.isPlaying) {
                        viewModel.togglePlayPause()
                        syncPlaybackService(force = true)
                    }
                }

                override fun onPause() {
                    if (viewModel.uiState.value.isPlaying) {
                        viewModel.togglePlayPause()
                        syncPlaybackService(force = true)
                    }
                }

                override fun onSkipToNext() {
                    viewModel.playNextEpisode()
                    syncPlaybackService(force = true)
                }

                override fun onSeekTo(pos: Long) {
                    val targetSec = (pos / 1000L).coerceAtLeast(0L).toDouble()
                    com.example.util.PlayerViewManager.seekTo(targetSec)
                    syncPlaybackService(force = true)
                }

                override fun onStop() {
                    if (viewModel.uiState.value.isPlaying) {
                        viewModel.togglePlayPause()
                    }
                    syncPlaybackService(force = true)
                }
            })
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }
    }

    private var lastSessionArtworkUrl: String? = null
    private var lastSessionArtworkBitmap: android.graphics.Bitmap? = null
    private var sessionArtSeq: Long = 0L

    private fun updateMediaSession(
        video: VideoItem?,
        isPlaying: Boolean,
        snapshot: com.example.model.PlayerSnapshot?
    ) {
        val session = mediaSession ?: return
        if (video == null) {
            session.setPlaybackState(
                PlaybackState.Builder()
                    .setState(PlaybackState.STATE_NONE, 0L, 0f)
                    .build()
            )
            session.setMetadata(null)
            return
        }
        val state = viewModel.uiState.value
        val display = viewModel.nowPlayingDisplay()
        val title = display?.title?.takeIf { it.isNotBlank() } ?: video.title.ifBlank { "CluTube" }
        val artist = display?.subtitle?.takeIf { it.isNotBlank() } ?: video.channelName
        // PlaybackState expects milliseconds; snapshots are seconds. The old
        // code passed seconds straight through, so 5:00 showed as 0:00.
        val positionMs = snapshot?.positionSeconds
            ?.takeIf { it.isFinite() && it >= 0.0 }
            ?.let { (it * 1000L).toLong() } ?: 0L
        val durationMs = viewModel.currentTrustedDurationMs()
            .takeIf { it > 0L }
            ?: snapshot?.durationSeconds
                ?.takeIf { it.isFinite() && it > 0.0 }
                ?.let { (it * 1000L).toLong() } ?: 0L
        val hasNext = viewModel.hasNextTarget()
        var actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            PlaybackState.ACTION_STOP
        if (hasNext) actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        if (durationMs > 0L) actions = actions or PlaybackState.ACTION_SEEK_TO
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    positionMs.coerceAtLeast(0L),
                    1f,
                    android.os.SystemClock.elapsedRealtime()
                )
                .build()
        )
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "CluTube")
        if (durationMs > 0L) {
            metadata.putLong(MediaMetadata.METADATA_KEY_DURATION, durationMs)
        }
        lastSessionArtworkBitmap?.let { bitmap ->
            // Only reuse the cached bitmap for the same artwork URL; episode
            // changes must not keep showing the previous still.
            if (display?.artworkUrl == lastSessionArtworkUrl) {
                metadata.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
                metadata.putBitmap(MediaMetadata.METADATA_KEY_ART, bitmap)
            }
        }
        session.setMetadata(metadata.build())
        // Fetch artwork async; repost metadata once Coil resolves so the
        // lock-screen / shade shows the show/movie still instead of blank.
        // Stale guard: a newer emission invalidates in-flight fetches.
        val artworkUrl = display?.artworkUrl?.takeIf { it.isNotBlank() }
        if (!artworkUrl.isNullOrBlank() && artworkUrl != lastSessionArtworkUrl) {
            lastSessionArtworkUrl = artworkUrl
            val mySeq = ++sessionArtSeq
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val bitmap = loadSessionArtwork(artworkUrl)
                if (bitmap != null && mySeq == sessionArtSeq) {
                    lastSessionArtworkBitmap = bitmap
                    // Repost on the main thread with fresh position/duration.
                    launch(kotlinx.coroutines.Dispatchers.Main) {
                        if (mySeq != sessionArtSeq) return@launch
                        val cur = viewModel.uiState.value
                        updateMediaSession(
                            cur.currentPlayingVideo,
                            cur.isPlaying,
                            cur.currentPlaybackSnapshot
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadSessionArtwork(url: String): android.graphics.Bitmap? {
        return kotlinx.coroutines.withTimeoutOrNull(8_000L) {
            runCatching {
                val request = coil.request.ImageRequest.Builder(this@MainActivity)
                    .data(url)
                    .size(512, 512)
                    .scale(coil.size.Scale.FILL)
                    .allowHardware(false)
                    .build()
                val result = coil.Coil.imageLoader(this@MainActivity).execute(request)
                ((result as? coil.request.SuccessResult)?.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            }.getOrNull()
        }
    }

    /**
     * Keeps the background-playback foreground service in sync with the
     * player: a loaded video starts/updates the "Now playing" notification
     * (which keeps audio alive on the lock screen), closing the player stops
     * it. Safe to call on every uiState emission; the service only
     * re-posts the notification.
     */
    private var lastPlaybackSyncKey: String? = null

    private fun syncPlaybackService(force: Boolean = false) {
        val state = viewModel.uiState.value
        val video = state.currentPlayingVideo
        val offlineTitle = state.offlinePlayingTitle
        val display = viewModel.nowPlayingDisplay()
        val hasNext = viewModel.hasNextTarget()
        // Service reposts only on content change (video, episode label,
        // artwork, play state, next availability, background toggle). The
        // MediaSession (updated on every emission by the same collector)
        // owns per-second position/duration — reposting FGS for position
        // caused shade flicker + Binder spam for no benefit.
        val syncKey = when {
            video != null -> {
                video.playbackKey() + "|" + state.isPlaying + "|" +
                    (display?.title.orEmpty()) + "|" + (display?.artworkUrl.orEmpty()) +
                    "|" + hasNext + "|" + state.isBackgroundPlayEnabled
            }
            offlineTitle != null -> {
                "offline|" + offlineTitle + "|" + state.offlinePlayingIsPlaying + "|" + state.isBackgroundPlayEnabled
            }
            else -> "none"
        }
        if (!force && syncKey == lastPlaybackSyncKey) return
        lastPlaybackSyncKey = syncKey
        val serviceIntent =
            Intent(this, com.example.data.playback.PlaybackService::class.java)
        try {
            if (video == null && offlineTitle == null) {
                serviceIntent.action = com.example.data.playback.PlaybackService.ACTION_STOP
                startService(serviceIntent)
                return
            }
            serviceIntent.action = com.example.data.playback.PlaybackService.ACTION_SYNC
            if (video != null) {
                serviceIntent.putExtra(
                    com.example.data.playback.PlaybackService.EXTRA_TITLE,
                    display?.title?.takeIf { it.isNotBlank() } ?: video.title
                )
                serviceIntent.putExtra(
                    com.example.data.playback.PlaybackService.EXTRA_ARTIST,
                    display?.subtitle?.takeIf { it.isNotBlank() } ?: video.channelName
                )
                serviceIntent.putExtra(
                    com.example.data.playback.PlaybackService.EXTRA_IS_PLAYING, state.isPlaying
                )
                serviceIntent.putExtra(
                    com.example.data.playback.PlaybackService.EXTRA_IS_TV_SHOW,
                    video.mediaType == MediaType.TV_SHOW
                )
                serviceIntent.putExtra(
                    com.example.data.playback.PlaybackService.EXTRA_HAS_NEXT, hasNext
                )
                display?.artworkUrl?.takeIf { it.isNotBlank() }?.let {
                    serviceIntent.putExtra(
                        com.example.data.playback.PlaybackService.EXTRA_ARTWORK_URL, it
                    )
                }
            } else {
                serviceIntent.putExtra(
                    com.example.data.playback.PlaybackService.EXTRA_TITLE, offlineTitle
                )
                serviceIntent.putExtra(
                    com.example.data.playback.PlaybackService.EXTRA_ARTIST,
                    state.offlinePlayingArtist.orEmpty()
                )
                serviceIntent.putExtra(
                    com.example.data.playback.PlaybackService.EXTRA_IS_PLAYING,
                    state.offlinePlayingIsPlaying
                )
                serviceIntent.putExtra(
                    com.example.data.playback.PlaybackService.EXTRA_IS_TV_SHOW, false
                )
                serviceIntent.putExtra(
                    com.example.data.playback.PlaybackService.EXTRA_HAS_NEXT, false
                )
            }
            serviceIntent.putExtra(
                com.example.data.playback.PlaybackService.EXTRA_BACKGROUND_ENABLED,
                state.isBackgroundPlayEnabled
            )
            mediaSession?.sessionToken?.let { token ->
                serviceIntent.putExtra(
                    com.example.data.playback.PlaybackService.EXTRA_SESSION_TOKEN, token
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    startForegroundService(serviceIntent)
                } catch (e: android.app.ForegroundServiceStartNotAllowedException) {
                    // Backgrounded past the FGS window (Android 12+): session
                    // metadata above already updated the shade; skip the
                    // service repost rather than crashing background audio.
                    Log.w("MainActivity", "FGS start blocked, session-only: ${e.message}")
                }
            } else {
                startService(serviceIntent)
            }
        } catch (_: Exception) {
            // Background playback is best-effort: a failure here must never
            // interrupt foreground playback.
        }
    }

    private fun updatePipParams(video: VideoItem?, isPlaying: Boolean, hasNext: Boolean = false) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shouldEnablePip = video != null && isPlaying
            // PiP Next follows the same unified target as shade/session
            // (queue + movies included), not TV-only.
            val actions = if (video != null) createPipActions(isPlaying, hasNext) else emptyList()

            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .setActions(actions)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(shouldEnablePip)
                builder.setSeamlessResizeEnabled(true)
            }

            try {
                setPictureInPictureParams(builder.build())
            } catch (e: Exception) {
                Log.w("MainActivity", "Failed setting PiP params: ${e.message}")
            }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val state = viewModel.uiState.value
            if (state.currentPlayingVideo != null && state.isPlaying) {
                val actions = createPipActions(state.isPlaying, viewModel.hasNextTarget())
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .setActions(actions)
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            setSeamlessResizeEnabled(true)
                        }
                    }
                    .build()
                try {
                    enterPictureInPictureMode(params)
                } catch (e: Exception) {
                    Log.w("MainActivity", "Failed entering PiP mode: ${e.message}")
                }
            }
        }
    }

    companion object {
        const val ACTION_PIP_PLAY_PAUSE = "com.clutube.app.PIP_PLAY_PAUSE"
        const val ACTION_PIP_NEXT = "com.clutube.app.PIP_NEXT"
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 4701
        private const val VOICE_PERMISSION_REQUEST_CODE = 4702
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeApp(
    viewModel: YouTubeViewModel,
    isInPipMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.initSettings(context.applicationContext)
    }

    val isMobileLaunch = LocalConfiguration.current.screenWidthDp < 600
    var showLaunchIntro by rememberSaveable { mutableStateOf(isMobileLaunch) }
    LaunchedEffect(isMobileLaunch) {
        if (!isMobileLaunch) {
            showLaunchIntro = false
        } else if (showLaunchIntro) {
            kotlinx.coroutines.delay(900L)
            showLaunchIntro = false
        }
    }

    val isFullscreen by com.example.util.FullscreenHelper.isFullscreen.collectAsState()
    val activity = LocalContext.current as? android.app.Activity
    val requestNotificationPermission = {
        (activity as? MainActivity)?.requestNotificationPermissionIfNeeded()
    }

    val pipVideo = uiState.currentPlayingVideo
    if (isInPipMode && pipVideo != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("pip_player_fullscreen")
        ) {
            YouTubePlayer(
                video = pipVideo,
                selectedServerId = uiState.selectedServerId,
                onSelectServer = { viewModel.setStreamServer(it) },
                isTouchEnabled = false,
                resumePositionSeconds = uiState.pendingResumeOverrideKey
                    ?.takeIf { it == pipVideo.playbackKey() }
                    ?.let { uiState.pendingResumeOverrideSeconds }
                    ?: uiState.currentHistoryEntry?.positionSeconds?.toDouble() ?: 0.0,
                playWhenReady = uiState.isPlaying,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    BackHandler {
        when {
            isFullscreen -> activity?.let { com.example.util.FullscreenHelper.exitFullscreen(it) }
            uiState.showSettingsScreen -> viewModel.setShowSettingsScreen(false)
            uiState.showHistoryScreen -> viewModel.setShowHistoryScreen(false)
            uiState.showCommentsSheet -> viewModel.setShowCommentsSheet(false)
            uiState.pendingDownloadTarget != null -> viewModel.dismissDownloadOptions()
            uiState.showAddMagnetDialog -> viewModel.setShowAddMagnetDialog(false)
            uiState.showTorrentSourceDialog -> viewModel.setShowTorrentSourceDialog(false)
            uiState.showServerDialog -> viewModel.setShowServerDialog(false)
            uiState.showCreateSheet -> viewModel.setShowCreateSheet(false)
            uiState.showDownloadsScreen -> viewModel.closeDownloadsScreen()
            uiState.isChannelScreenOpen -> viewModel.closeChannel()
            uiState.isPlayerExpanded -> viewModel.minimizePlayer()
            uiState.isSearching -> viewModel.exitSearch()
            uiState.isQueuePanelOpen -> viewModel.setQueuePanelOpen(false)
            uiState.selectedTab == 1 -> viewModel.selectTab(0)
            uiState.selectedTab != 0 -> viewModel.selectTab(0)
            uiState.currentPlayingVideo != null -> viewModel.minimizePlayer()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isTablet = when (uiState.deviceLayoutMode) {
            DeviceLayoutMode.MOBILE -> false
            DeviceLayoutMode.TABLET -> true
            DeviceLayoutMode.AUTO -> maxWidth >= 600.dp
        }
        val snackbarHostState = remember { SnackbarHostState() }

        // Continue-watching progress + labels shared by every card surface.
        // Exact playback keys first; series-group fallbacks never overwrite
        // them, so an S1:E1 catalog card reflects the S3:E7 resume point
        // without letting the oldest episode win (no bare video-id keys).
        val progressFractions = remember(uiState.watchHistory) {
            buildMap {
                val live = uiState.watchHistory.filterNot { it.completed }
                live.forEach { entry ->
                    if (entry.progressFraction > com.example.model.MIN_VISIBLE_PROGRESS_FRACTION) {
                        put(entry.key, entry.progressFraction)
                    }
                }
                live.asSequence()
                    .filter { it.video.mediaType == MediaType.TV_SHOW }
                    .groupBy { it.titleGroupKey() }
                    .forEach { (group, grouped) ->
                        val latest = grouped.maxWithOrNull(
                            compareBy<com.example.model.WatchHistoryEntry> { it.lastWatchedAtMillis }
                                .thenBy { it.positionSeconds }
                        ) ?: return@forEach
                        if (latest.progressFraction > com.example.model.MIN_VISIBLE_PROGRESS_FRACTION) {
                            putIfAbsent(group, latest.progressFraction)
                        }
                    }
            }
        }
        val continueLabels = remember(uiState.watchHistory) {
            buildMap {
                val live = uiState.watchHistory.filterNot { it.completed }
                live.forEach { entry ->
                    if (entry.durationSeconds > 0L) {
                        put(entry.key, entry.toContinueUiModel().label)
                    }
                }
                live.asSequence()
                    .filter { it.video.mediaType == MediaType.TV_SHOW }
                    .groupBy { it.titleGroupKey() }
                    .forEach { (group, grouped) ->
                        val latest = grouped.maxWithOrNull(
                            compareBy<com.example.model.WatchHistoryEntry> { it.lastWatchedAtMillis }
                                .thenBy { it.positionSeconds }
                        ) ?: return@forEach
                        if (latest.durationSeconds > 0L) {
                            putIfAbsent(group, latest.toContinueUiModel().label)
                        }
                    }
            }
        }

        LaunchedEffect(uiState.userFeedbackMessage) {
            val msg = uiState.userFeedbackMessage ?: return@LaunchedEffect
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserFeedbackMessage()
        }

        Row(modifier = Modifier.fillMaxSize()) {
            if (isTablet && !uiState.isSearching) {
                SideNavRail(
                    selectedTab = uiState.selectedTab,
                    onTabSelected = { viewModel.selectTab(it) },
                    profileAvatar = uiState.localProfileAvatar,
                    onCreateClick = { viewModel.setShowCreateSheet(true) },
                    badgeCount = uiState.userNotificationCount
                )
            }

            // Main Screen Scaffold Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        if (uiState.selectedTab != 1 && !uiState.isSearching) {
                            YouTubeTopAppBar(
                                isDarkMode = uiState.isDarkMode,
                                onSearchClick = { viewModel.search("") },
                                onRefresh = { viewModel.reloadCurrentCategory() },
                                onThemeToggle = { viewModel.toggleTheme() },
                                onAvatarClick = { viewModel.selectTab(4) },
                                onNotificationsClick = { viewModel.selectTab(3) },
                                notificationBadgeCount = uiState.userNotificationCount
                            )
                        }
                    },
                    bottomBar = {
                        // Show BottomNavBar only on Phone / Mobile Mode
                        if (!isTablet) {
                            BottomNavBar(
                                selectedTab = uiState.selectedTab,
                                onTabSelected = { viewModel.selectTab(it) },
                                profileAvatar = uiState.localProfileAvatar,
                                badgeCount = uiState.userNotificationCount,
                                onCreateClick = { viewModel.setShowCreateSheet(true) }
                            )
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Coerced defensively: selectedTab is clamped in
                        // selectTab, but this guards any stale state.
                        when (uiState.selectedTab.coerceIn(0, 4)) {
                            0 -> HomeScreen(
                                videos = uiState.videos,
                                continueWatching = uiState.continueWatching,
                                recentWatched = uiState.recentWatched,
                                showContinueWatching = uiState.showContinueWatchingOnHome,
                                selectedCategory = uiState.selectedCategory,
                                isLoading = uiState.isLoading,
                                isRefreshing = uiState.isFeedRefreshing,
                                scrollToTopNonce = uiState.homeScrollToTopNonce,
                                isLoadingMore = uiState.isLoadingMore,
                                feedErrorMessage = uiState.feedErrorMessage,
                                isOffline = uiState.isOffline,
                                isDarkMode = uiState.isDarkMode,
                                isTabletLayout = isTablet,
                                onCategorySelected = { viewModel.selectCategory(it) },
                                onLoadMore = { viewModel.loadNextPage() },
                                onRefresh = { viewModel.reloadCurrentCategory() },
                                onVideoClick = { viewModel.playVideo(it, expand = true) },
                                onContinueWatchingClick = { viewModel.resumeWatch(it, expand = true) },
                                onRemoveContinueWatching = { viewModel.removeWatchHistoryEntry(it.key) },
                                onSaveToWatchLater = { viewModel.toggleSave(it.id) },
                                onShare = { shareVideo(context, it) },
                                onAddToQueue = { viewModel.addToQueue(it) },
                                watchedVideoIds = uiState.watchedVideoIds,
                                onToggleWatched = { viewModel.toggleWatched(it) },
                                notInterestedVideoIds = uiState.notInterestedVideoIds,
                                notRecommendedChannelNames = uiState.notRecommendedChannelNames,
                                onNotInterested = { viewModel.markNotInterested(it.id) },
                                onNotRecommendChannel = { viewModel.blockRecommendedChannel(it.channelName) },
                                onChannelClick = { viewModel.openChannel(it) },
                                onDownloadVideo = { viewModel.downloadVideoFromMenu(it) },
                                releaseAlertIds = uiState.releaseAlerts
                                     .filterNot { it.isDelivered }
                                     .map { it.id }
                                     .toSet(),
                                 onToggleReleaseAlert = {
                                      if (!viewModel.isReleaseAlertActive(it)) {
                                          requestNotificationPermission()
                                      }
                                      viewModel.toggleReleaseAlert(it)
                                  },
                                   onOpenServerDialog = { viewModel.setShowServerDialog(true) },
                                   savedVideoIds = uiState.savedVideoIds,
                                   progressFractions = progressFractions,
                                   continueLabels = continueLabels,
                                    onVisibleIdsChanged = {
                                        viewModel.ensureFeedStudios(it)
                                    }
                             )

                            1 -> ShortsScreen(
                                shorts = uiState.shorts,
                                currentIndex = uiState.currentShortIndex,
                                isLoading = uiState.isShortsLoading,
                                shortsErrorMessage = uiState.shortsErrorMessage,
                                onRetryShorts = { viewModel.retryTrailerShorts() },
                                savedVideoIds = uiState.savedVideoIds,
                                onNextShort = { viewModel.nextShort() },
                                onPrevShort = { viewModel.previousShort() },
                                onToggleSave = { viewModel.toggleSave(it.id) },
                                onWatchNow = { viewModel.watchNowFromShort(it) }
                            )

                            2 -> SubscriptionsScreen(
                                channels = uiState.channels,
                                videos = uiState.videos,
                                onVideoClick = { viewModel.playVideo(it, expand = true) },
                                onChannelClick = { viewModel.openChannelFromItem(it) },
                                 onSaveToWatchLater = { viewModel.toggleSave(it.id) },
                                 onShare = { shareVideo(context, it) },
                                 onAddToQueue = { viewModel.addToQueue(it) },
                                 watchedVideoIds = uiState.watchedVideoIds,
                                 onToggleWatched = { viewModel.toggleWatched(it) },
                                 onNotInterested = { viewModel.markNotInterested(it.id) },
                                 onNotRecommendChannel = { viewModel.blockRecommendedChannel(it.channelName) },
                                 onDownloadVideo = { viewModel.downloadVideoFromMenu(it) },
                                  releaseAlertIds = uiState.releaseAlerts
                                      .filterNot { it.isDelivered }
                                      .map { it.id }
                                      .toSet(),
                                  onToggleReleaseAlert = {
                                      if (!viewModel.isReleaseAlertActive(it)) {
                                          requestNotificationPermission()
                                      }
                                      viewModel.toggleReleaseAlert(it)
                                  },
                                  savedVideoIds = uiState.savedVideoIds,
                                  progressFractions = progressFractions,
                                  continueLabels = continueLabels
                             )

                            3 -> NotificationsScreen(
                                notifications = uiState.notifications,
                                upcomingVideos = uiState.upcomingVideos,
                                isUpcomingLoading = uiState.isUpcomingLoading,
                                onRetryUpcoming = { viewModel.refreshUpcoming() },
                                releaseAlertIds = uiState.releaseAlerts
                                    .filterNot { it.isDelivered }
                                    .map { it.id }
                                    .toSet(),
                                onVideoClick = { viewModel.openNotification(it) },
                                onUpcomingVideoClick = { viewModel.playVideo(it, expand = true) },
                                onToggleReleaseAlert = {
                                    if (!viewModel.isReleaseAlertActive(it)) {
                                        requestNotificationPermission()
                                    }
                                    viewModel.toggleReleaseAlert(it)
                                },
                                onMarkRead = { id, isRead -> viewModel.markNotificationRead(id, isRead) },
                                onMarkAllRead = { viewModel.markAllNotificationsRead() },
                                onDismiss = { viewModel.dismissNotification(it) },
                                onClearRead = { viewModel.clearReadNotifications() }
                            )

                            4 -> YouScreen(
                                watchHistory = uiState.watchHistory,
                                savedVideos = uiState.savedVideos,
                                likedVideosCount = uiState.likedVideoIds.size,
                                savedVideosCount = uiState.savedVideoIds.size,
                                queueCount = uiState.queue.size,
                                watchLaterSort = uiState.watchLaterSort,
                                showContinueWatchingOnHome = uiState.showContinueWatchingOnHome,
                                isBackgroundPlayEnabled = uiState.isBackgroundPlayEnabled,
                                onSetBackgroundPlayEnabled = { viewModel.setBackgroundPlayEnabled(it) },
                                releaseNotificationsEnabled = uiState.releaseNotificationsEnabled,
                                localProfileName = uiState.localProfileName,
                                localProfileAvatar = uiState.localProfileAvatar,
                                deviceLayoutMode = uiState.deviceLayoutMode,
                                onSelectDeviceLayoutMode = { viewModel.setDeviceLayoutMode(it) },
                                onVideoClick = { viewModel.playVideo(it, expand = true) },
                                onResumeHistory = { viewModel.resumeWatch(it, expand = true) },
                                onViewAllHistory = { viewModel.setShowHistoryScreen(true) },
                                onRemoveHistory = { viewModel.removeWatchHistoryEntry(it) },
                                onClearHistory = { viewModel.clearWatchHistory() },
                                onRemoveSaved = { viewModel.toggleSave(it) },
                                onSetWatchLaterSort = { viewModel.setWatchLaterSort(it) },
                                onSetContinueWatchingOnHome = { viewModel.setShowContinueWatchingOnHome(it) },
                                onSetReleaseNotificationsEnabled = { viewModel.setReleaseNotificationsEnabled(it) },
                                playbackPreferences = uiState.playbackPreferences,
                                onQualitySelected = { viewModel.setPlaybackQuality(it) },
                                onSubtitleSelected = { viewModel.setSubtitlePreference(it) },
                                onAddToQueue = { viewModel.addToQueue(it) },
                                onOpenQueue = { viewModel.setQueuePanelOpen(true) },
                                onSaveProfile = { name, avatar -> viewModel.saveLocalProfile(name, avatar) },
                                onClearLocalData = { viewModel.clearLocalData() },
                                notInterestedCount = uiState.notInterestedVideoIds.size,
                                notRecommendedChannelCount = uiState.notRecommendedChannelNames.size,
                                onClearRecommendationPreferences = { viewModel.clearRecommendationPreferences() },
                                onOpenServerDialog = { viewModel.setShowServerDialog(true) },
                                downloadsCount = uiState.downloads.count { it.status == com.example.data.local.DownloadStatus.COMPLETED.name },
                                downloads = uiState.downloads,
                                onOpenDownloads = { viewModel.openDownloadsScreen() },
                                onDownloadSubtitles = { viewModel.fetchSubtitlesForDownload(it) },
                                onSubtitleOffsetChanged = { id, offsetMs -> viewModel.updateSubtitleOffset(id, offsetMs) },
                                onSubtitleTrackChanged = { id, trackId -> viewModel.setSubtitleTrack(id, trackId) },
                                offlineSubtitleLanguage = uiState.offlineSubtitleLanguage,
                                isSubtitleAutoDownload = uiState.isSubtitleAutoDownload,
                                wyzieApiKey = uiState.wyzieApiKey,
                                subdlApiKey = uiState.subdlApiKey,
                                onOfflineSubtitleLanguageSelected = { viewModel.setOfflineSubtitleLanguage(it) },
                                onSubtitleAutoDownloadChanged = { viewModel.setSubtitleAutoDownload(it) },
                                onWyzieApiKeyChanged = { viewModel.setWyzieApiKey(it) },
                                onSubdlApiKeyChanged = { viewModel.setSubdlApiKey(it) },
                                onOfflineOpened = { title, artist -> viewModel.setOfflinePlaying(title, artist, true) },
                                onOfflineIsPlayingChanged = { playing ->
                                    val cur = viewModel.uiState.value
                                    cur.offlinePlayingTitle?.let { viewModel.setOfflinePlaying(it, cur.offlinePlayingArtist.orEmpty(), playing) }
                                },
                                onOfflineClosed = { viewModel.clearOfflinePlaying() },
                                onOpenSettings = { viewModel.setShowSettingsScreen(true) }
                            )
                        }
                    }
                }
            }
        }

        // Fullscreen Search Overlay
        if (uiState.isSearching) {
            SearchScreen(
                query = uiState.searchQuery,
                searchResults = uiState.searchResults,
                isSearchLoading = uiState.isSearchLoading,
                searchErrorMessage = uiState.searchErrorMessage,
                isSearchCacheStale = uiState.isSearchCacheStale,
                searchHistory = uiState.searchHistory,
                onQueryChange = { viewModel.updateSearchQuery(it) },
                onSearch = { viewModel.submitSearch(it) },
                onBack = { viewModel.exitSearch() },
                onVideoClick = { viewModel.playVideo(it, expand = true) },
                 onSaveToWatchLater = { viewModel.toggleSave(it.id) },
                 onShare = { shareVideo(context, it) },
                 onAddToQueue = { viewModel.addToQueue(it) },
                 watchedVideoIds = uiState.watchedVideoIds,
                 onToggleWatched = { viewModel.toggleWatched(it) },
                 onNotInterested = { viewModel.markNotInterested(it.id) },
                 onNotRecommendChannel = { viewModel.blockRecommendedChannel(it.channelName) },
                 releaseAlertIds = uiState.releaseAlerts
                     .filterNot { it.isDelivered }
                     .map { it.id }
                     .toSet(),
                 onToggleReleaseAlert = {
                     if (!viewModel.isReleaseAlertActive(it)) {
                         requestNotificationPermission()
                     }
                     viewModel.toggleReleaseAlert(it)
                 },
                 onRemoveSearchHistory = { viewModel.removeSearchHistory(it) },
                  onClearSearchHistory = { viewModel.clearSearchHistory() },
                  onDownloadVideo = { viewModel.downloadVideoFromMenu(it) },
                  savedVideoIds = uiState.savedVideoIds,
                  progressFractions = progressFractions,
                  continueLabels = continueLabels,
                  onVoiceSearch = { (activity as? MainActivity)?.startVoiceSearch() },
                  onChannelClick = { viewModel.openChannel(it) }
            )
        }

        if (uiState.showHistoryScreen) {
            HistoryScreen(
                entries = uiState.watchHistory,
                onBack = { viewModel.setShowHistoryScreen(false) },
                onResume = { viewModel.resumeWatch(it, expand = true) },
                onRemove = { viewModel.removeWatchHistoryEntry(it) },
                onClear = { viewModel.clearWatchHistory() },
                // VideoItem overload: episode-scoped toggle with id fallback.
                onToggleWatched = { viewModel.toggleWatched(it) }
            )
        }

        if (uiState.showDownloadsScreen) {
            DownloadsScreen(
                downloads = uiState.downloads,
                activeSpeeds = uiState.activeDownloadSpeeds,
                usedStorageBytes = uiState.usedStorageBytes,
                availableStorageBytes = uiState.availableStorageBytes,
                totalStorageBytes = uiState.totalStorageBytes,
                onBack = { viewModel.closeDownloadsScreen() },
                onPauseDownload = { viewModel.pauseDownload(it) },
                onResumeDownload = { viewModel.resumeDownload(it) },
                onRetryDownload = { viewModel.retryDownload(it) },
                onCancelDownload = { viewModel.cancelDownload(it) },
                onDeleteDownload = { viewModel.deleteDownload(it) },
                onPauseAll = { viewModel.pauseAllDownloads() },
                onResumeAll = { viewModel.resumeAllDownloads() },
                onClearAllDownloads = { viewModel.clearAllDownloads() },
                onExploreContent = {
                    viewModel.closeDownloadsScreen()
                    viewModel.selectTab(0)
                },
                onAddMagnet = { viewModel.setShowAddMagnetDialog(true) },
                onDownloadSubtitles = { viewModel.fetchSubtitlesForDownload(it) },
                onSubtitleOffsetChanged = { id, offsetMs -> viewModel.updateSubtitleOffset(id, offsetMs) },
                onRetryAllFailed = { viewModel.retryAllFailedDownloads() },
                onClearFailed = { viewModel.clearFailedDownloads() },
                onDownloadAllSubtitles = { viewModel.downloadAllMissingSubtitles() },
                onSubtitleTrackChanged = { id, trackId -> viewModel.setSubtitleTrack(id, trackId) },
                isBackgroundPlayEnabled = uiState.isBackgroundPlayEnabled,
                onOfflineOpened = { title, artist -> viewModel.setOfflinePlaying(title, artist, true) },
                onOfflineIsPlayingChanged = { playing ->
                    val cur = viewModel.uiState.value
                    cur.offlinePlayingTitle?.let { viewModel.setOfflinePlaying(it, cur.offlinePlayingArtist.orEmpty(), playing) }
                },
                onOfflineClosed = { viewModel.clearOfflinePlaying() }
            )
        }

        // Full Watch screen. Keeping a single active AndroidView host during
        // this handoff prevents the persistent provider surface from being
        // attached to two Compose containers during an exit animation.
        if (uiState.isPlayerExpanded && uiState.currentPlayingVideo != null) {
            uiState.currentPlayingVideo?.let { video ->
                val isLiked = uiState.likedVideoIds.contains(video.id)
                val isDisliked = uiState.dislikedVideoIds.contains(video.id)
                val isSubscribed = uiState.subscribedChannelNames.contains(video.channelName)
                val isSaved = uiState.savedVideoIds.contains(video.id)

                WatchScreen(
                    video = video,
                    relatedVideos = uiState.relatedVideos,
                    isRelatedLoading = uiState.isRelatedLoading,
                    relatedErrorMessage = uiState.relatedErrorMessage,
                    onRetryRelated = { viewModel.retryRelatedVideos() },
                    trailerViewsLabel = uiState.currentTrailerViewsLabel,
                    tvEpisodes = uiState.tvEpisodes,
                    totalSeasons = uiState.totalSeasons,
                    selectedSeason = uiState.selectedSeason,
                     selectedServerId = uiState.selectedServerId,
                     resumePositionSeconds = uiState.pendingResumeOverrideKey
                        ?.takeIf { it == video.playbackKey() }
                        ?.let { uiState.pendingResumeOverrideSeconds }
                        ?: uiState.currentHistoryEntry?.positionSeconds?.toDouble() ?: 0.0,
                     currentPlaybackSnapshot = uiState.currentPlaybackSnapshot,
                     isPlaying = uiState.isPlaying,
                    isLiked = isLiked,
                    isDisliked = isDisliked,
                    isSubscribed = isSubscribed,
                    isSaved = isSaved,
                    topComment = uiState.comments.firstOrNull(),
                    isTabletLayout = isTablet,
                    isAutoNextEnabled = uiState.isAutoNextEpisodeEnabled,
                    onPlayNextEpisode = { viewModel.playNextEpisode() },
                    onToggleAutoNext = { viewModel.toggleAutoNextEpisode() },
                    activeSkipSegment = uiState.activeSkipSegment,
                    isSkipSegmentsEnabled = uiState.isSkipSegmentsEnabled,
                    onSkipSegment = { viewModel.onSkipSegment(it) },
                    onRetryPlayback = { viewModel.retryCurrentPlayback() },
                    onMinimize = { viewModel.minimizePlayer() },
                    onSelectServer = { viewModel.setStreamServer(it) },
                    onToggleLike = { viewModel.toggleLike(video.id) },
                    onToggleDislike = { viewModel.toggleDislike(video.id) },
                    onToggleSubscribe = { viewModel.toggleSubscribe(video.channelName) },
                    onToggleSave = { viewModel.toggleSave(video.id) },
                     onOpenComments = { viewModel.setShowCommentsSheet(true) },
                     onOpenServerDialog = { viewModel.setShowServerDialog(true) },
                     onOpenQueue = { viewModel.setQueuePanelOpen(true) },
                     onOpenChannel = { viewModel.openChannel(it) },
                    onSelectSeason = { viewModel.selectTvSeason(it) },
                    onSelectEpisode = { season, episode -> viewModel.selectTvEpisode(season, episode) },
                    onQueueEpisode = { season, episode -> viewModel.queueEpisode(season, episode) },
                    isEpisodeQueued = { season, episode -> viewModel.isEpisodeQueued(season, episode) },
                    onPlayEpisodeNext = { season, episode -> viewModel.playEpisodeNext(season, episode) },
                    onToggleEpisodeWatched = { season, episode -> viewModel.toggleEpisodeWatched(season, episode) },
                    isEpisodeWatched = { season, episode -> viewModel.isEpisodeWatched(season, episode) },
                    getEpisodeWatchProgress = { season, episode ->
                        viewModel.getEpisodeWatchEntry(season, episode)
                    },
                    watchedCountBySeason = remember(video.playbackKey(), uiState.watchHistory, uiState.tvEpisodes) {
                        uiState.tvEpisodes.groupBy { it.seasonNumber }
                            .mapValues { (_, eps) ->
                                eps.count { ep -> viewModel.isEpisodeWatched(ep.seasonNumber, ep.episodeNumber) }
                            }
                    },
                    totalCountBySeason = remember(uiState.tvEpisodes) {
                        uiState.tvEpisodes.groupBy { it.seasonNumber }
                            .mapValues { (_, eps) -> eps.size }
                    },
                     onSelectVideo = { viewModel.playVideo(it, expand = true) },
                     onSaveToWatchLater = { viewModel.toggleSave(it.id) },
                     onShare = { shareVideo(context, it) },
                     onAddToQueue = { viewModel.addToQueue(it) },
                     watchedVideoIds = uiState.watchedVideoIds,
                     onToggleWatched = { viewModel.toggleWatched(it) },
                     onNotInterested = { viewModel.markNotInterested(it.id) },
                     onNotRecommendChannel = { viewModel.blockRecommendedChannel(it.channelName) },
                     isReleaseAlertActive = viewModel.isReleaseAlertActive(video),
                     onToggleReleaseAlert = {
                         if (!viewModel.isReleaseAlertActive(video)) {
                             requestNotificationPermission()
                         }
                         viewModel.toggleReleaseAlert(video)
                     },
                     isEpisodeAlertActive = { season, episode ->
                         viewModel.isReleaseAlertActive(video, season, episode)
                     },
                     onNotifyEpisode = { episode ->
                         if (!viewModel.isReleaseAlertActive(video, episode.seasonNumber, episode.episodeNumber)) {
                             requestNotificationPermission()
                         }
                         viewModel.toggleReleaseAlert(
                             video = video,
                             season = episode.seasonNumber,
                             episode = episode.episodeNumber,
                             releaseAtMillisOverride = com.example.model.releaseDateMillis(episode.airDate)
                         )
                     },
                     onDownloadMovie = { viewModel.downloadMovie(it) },
                     onDownloadEpisode = { vid, ep -> viewModel.downloadEpisode(vid, ep) },
                     onDownloadSeason = { vid, s, eps -> viewModel.downloadSeason(vid, s, eps) },
                     onDownloadVideo = { viewModel.downloadVideoFromMenu(it) },
                     // Episode identity is the (tmdbId, season, episode)
                     // triple — never a raw row id. This matches plain
                     // episode rows, torrent rows (deterministic or legacy
                     // hash-suffixed), and both tmdbId/id spellings, so the
                     // badge and progress can never stick to the wrong S/E.
                     isMovieDownloaded = remember(video.tmdbId, video.id, uiState.downloads) {
                         val canonical = video.tmdbId ?: video.id
                         uiState.downloads.any {
                             it.mediaType == MediaType.MOVIE.name &&
                                 (it.tmdbId == canonical || it.tmdbId == video.id) &&
                                 it.status == com.example.data.local.DownloadStatus.COMPLETED.name
                         }
                     },
                     movieDownloadProgress = remember(video.tmdbId, video.id, uiState.downloads) {
                         val canonical = video.tmdbId ?: video.id
                         uiState.downloads.firstOrNull {
                             it.mediaType == MediaType.MOVIE.name &&
                                 (it.tmdbId == canonical || it.tmdbId == video.id) &&
                                 (it.status == com.example.data.local.DownloadStatus.DOWNLOADING.name ||
                                     it.status == com.example.data.local.DownloadStatus.QUEUED.name ||
                                     it.status == com.example.data.local.DownloadStatus.PAUSED.name)
                         }?.progressPercent
                     },
                     isEpisodeDownloaded = { s, e ->
                         val canonical = video.tmdbId ?: video.id
                         uiState.downloads.any {
                             it.mediaType == MediaType.TV_SHOW.name &&
                                 (it.tmdbId == canonical || it.tmdbId == video.id) &&
                                 it.seasonNumber == s && it.episodeNumber == e &&
                                 it.status == com.example.data.local.DownloadStatus.COMPLETED.name
                         }
                     },
                     getEpisodeDownloadProgress = { s, e ->
                         val canonical = video.tmdbId ?: video.id
                         uiState.downloads.firstOrNull {
                             it.mediaType == MediaType.TV_SHOW.name &&
                                 (it.tmdbId == canonical || it.tmdbId == video.id) &&
                                 it.seasonNumber == s && it.episodeNumber == e &&
                                 (it.status == com.example.data.local.DownloadStatus.DOWNLOADING.name ||
                                     it.status == com.example.data.local.DownloadStatus.QUEUED.name ||
                                     it.status == com.example.data.local.DownloadStatus.PAUSED.name)
                         }?.progressPercent
                     }
                 )
            }
        }

        if (uiState.showSettingsScreen) {
            SettingsScreen(
                playbackPreferences = uiState.playbackPreferences,
                onQualitySelected = { viewModel.setPlaybackQuality(it) },
                onSubtitleSelected = { viewModel.setSubtitlePreference(it) },
                isAutoNextEpisodeEnabled = uiState.isAutoNextEpisodeEnabled,
                onToggleAutoNextEpisode = { viewModel.toggleAutoNextEpisode() },
                isBackgroundPlayEnabled = uiState.isBackgroundPlayEnabled,
                onSetBackgroundPlayEnabled = { viewModel.setBackgroundPlayEnabled(it) },
                showContinueWatchingOnHome = uiState.showContinueWatchingOnHome,
                onSetContinueWatchingOnHome = { viewModel.setShowContinueWatchingOnHome(it) },
                isSkipSegmentsEnabled = uiState.isSkipSegmentsEnabled,
                onSetSkipSegmentsEnabled = { viewModel.setSkipSegmentsEnabled(it) },
                isSkipAutoSkipEnabled = uiState.isSkipAutoSkipEnabled,
                onSetSkipAutoSkipEnabled = { viewModel.setSkipAutoSkipEnabled(it) },
                skipAutoSkipDelaySeconds = uiState.skipAutoSkipDelaySeconds,
                onSetSkipAutoSkipDelaySeconds = { viewModel.setSkipAutoSkipDelaySeconds(it) },
                isSkipAutoSkipIntroEnabled = uiState.isSkipAutoSkipIntroEnabled,
                onSetSkipAutoSkipIntroEnabled = { viewModel.setSkipAutoSkipIntroEnabled(it) },
                isSkipAutoSkipRecapEnabled = uiState.isSkipAutoSkipRecapEnabled,
                onSetSkipAutoSkipRecapEnabled = { viewModel.setSkipAutoSkipRecapEnabled(it) },
                isSkipAutoSkipCreditsEnabled = uiState.isSkipAutoSkipCreditsEnabled,
                onSetSkipAutoSkipCreditsEnabled = { viewModel.setSkipAutoSkipCreditsEnabled(it) },
                isSkipAutoSkipPreviewEnabled = uiState.isSkipAutoSkipPreviewEnabled,
                onSetSkipAutoSkipPreviewEnabled = { viewModel.setSkipAutoSkipPreviewEnabled(it) },
                isSkipIntroEnabled = uiState.isSkipIntroEnabled,
                onSetSkipIntroEnabled = { viewModel.setSkipIntroEnabled(it) },
                isSkipRecapEnabled = uiState.isSkipRecapEnabled,
                onSetSkipRecapEnabled = { viewModel.setSkipRecapEnabled(it) },
                isSkipCreditsEnabled = uiState.isSkipCreditsEnabled,
                onSetSkipCreditsEnabled = { viewModel.setSkipCreditsEnabled(it) },
                isSkipPreviewEnabled = uiState.isSkipPreviewEnabled,
                onSetSkipPreviewEnabled = { viewModel.setSkipPreviewEnabled(it) },
                deviceLayoutMode = uiState.deviceLayoutMode,
                onSelectDeviceLayoutMode = { viewModel.setDeviceLayoutMode(it) },
                releaseNotificationsEnabled = uiState.releaseNotificationsEnabled,
                onSetReleaseNotificationsEnabled = { viewModel.setReleaseNotificationsEnabled(it) },
                offlineSubtitleLanguage = uiState.offlineSubtitleLanguage,
                isSubtitleAutoDownload = uiState.isSubtitleAutoDownload,
                wyzieApiKey = uiState.wyzieApiKey,
                subdlApiKey = uiState.subdlApiKey,
                onOfflineSubtitleLanguageSelected = { viewModel.setOfflineSubtitleLanguage(it) },
                onSubtitleAutoDownloadChanged = { viewModel.setSubtitleAutoDownload(it) },
                onWyzieApiKeyChanged = { viewModel.setWyzieApiKey(it) },
                onSubdlApiKeyChanged = { viewModel.setSubdlApiKey(it) },
                onOpenDownloads = { viewModel.openDownloadsScreen() },
                onOpenServerDialog = { viewModel.setShowServerDialog(true) },
                notInterestedCount = uiState.notInterestedVideoIds.size,
                notRecommendedChannelCount = uiState.notRecommendedChannelNames.size,
                onClearRecommendationPreferences = { viewModel.clearRecommendationPreferences() },
                onClearLocalData = { viewModel.clearLocalData() },
                onBack = { viewModel.setShowSettingsScreen(false) }
            )
        }

        if (uiState.isQueuePanelOpen) {
            QueueSheet(
                queue = uiState.queue,
                currentVideo = uiState.currentPlayingVideo,
                onDismiss = { viewModel.setQueuePanelOpen(false) },
                onPlay = { viewModel.playQueuedVideo(it) },
                onRemove = { viewModel.removeFromQueue(it) },
                onMove = { from, to -> viewModel.moveQueueItem(from, to) },
                onClear = { viewModel.clearQueue() }
            )
        }

        // Keep the channel page above the full Watch screen. This also makes
        // channel clicks from Watch behave the same as channel clicks elsewhere.
        if (uiState.isChannelScreenOpen && uiState.selectedChannel != null) {
            val channel = uiState.selectedChannel ?: return@BoxWithConstraints
            val isSubscribed = uiState.subscribedChannelNames.contains(channel.name)
            ChannelScreen(
                channel = channel,
                videos = uiState.channelVideos,
                isSubscribed = isSubscribed,
                isLoading = uiState.isChannelLoading,
                channelErrorMessage = uiState.channelErrorMessage,
                onRetryChannel = { viewModel.retryChannelMedia() },
                onBack = { viewModel.closeChannel() },
                onVideoClick = { viewModel.playVideo(it, expand = true) },
                 onToggleSubscribe = { viewModel.toggleSubscribe(channel.name) },
                 onSaveToWatchLater = { viewModel.toggleSave(it.id) },
                 onShareVideo = { shareVideo(context, it) },
                 onAddToQueue = { viewModel.addToQueue(it) },
                 watchedVideoIds = uiState.watchedVideoIds,
                 onToggleWatched = { viewModel.toggleWatched(it) },
                 onNotInterested = { viewModel.markNotInterested(it.id) },
                 onNotRecommendChannel = { viewModel.blockRecommendedChannel(it.channelName) },
                 releaseAlertIds = uiState.releaseAlerts
                     .filterNot { it.isDelivered }
                     .map { it.id }
                     .toSet(),
                 onToggleReleaseAlert = {
                     requestNotificationPermission()
                     viewModel.toggleReleaseAlert(it)
                 },
                 onSearchClick = { viewModel.search(channel.name) },
                 onDownloadVideo = { viewModel.downloadVideoFromMenu(it) },
                isTabletLayout = isTablet
            )
        }

        // Keep the mini-player above channel content. Opening a channel
        // collapses Watch into this floating player instead of hiding the
        // active episode underneath the channel page.
        if (!uiState.isPlayerExpanded && uiState.currentPlayingVideo != null) {
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(animationSpec = tween(160)) +
                    scaleIn(
                        initialScale = 0.94f,
                        transformOrigin = TransformOrigin(1f, 1f),
                        animationSpec = tween(160)
                    ),
                // Remove it immediately when expanding so the persistent
                // WebView never has two animated hosts during the handoff.
                exit = ExitTransition.None
            ) {
                FloatingVideoPlayer(
                    video = uiState.currentPlayingVideo!!,
                    selectedServerId = uiState.selectedServerId,
                    onExpand = { viewModel.togglePlayerExpand() },
                    onClose = { viewModel.closePlayer() },
                    onSelectServer = { viewModel.setStreamServer(it) },
                    onOpenServerDialog = { viewModel.setShowServerDialog(true) },
                    isPlaying = uiState.isPlaying,
                    onTogglePlayPause = { viewModel.togglePlayPause() },
                    isMuted = uiState.isMuted,
                    onToggleMute = { viewModel.toggleMute() },
                    resumePositionSeconds = uiState.pendingResumeOverrideKey
                        ?.takeIf { it == uiState.currentPlayingVideo!!.playbackKey() }
                        ?.let { uiState.pendingResumeOverrideSeconds }
                        ?: uiState.currentHistoryEntry?.positionSeconds?.toDouble() ?: 0.0,
                    progressFraction = uiState.currentHistoryEntry?.progressFraction ?: 0f
                )
            }
        }

        // Comments Bottom Sheet
        if (uiState.showCommentsSheet) {
            CommentsBottomSheet(
                comments = uiState.comments,
                onDismiss = { viewModel.setShowCommentsSheet(false) },
                onAddComment = { viewModel.addComment(it) },
                onLikeComment = { viewModel.likeComment(it) }
            )
        }

        // Stream Server & API Launcher Dialog
        if (uiState.showServerDialog) {
            StreamServerDialog(
                currentServerId = uiState.selectedServerId,
                currentVidSrcServerHost = uiState.selectedVidSrcServerHost,
                vidSrcServerOrder = uiState.vidSrcServerOrder,
                onSelectServer = { viewModel.setStreamServer(it) },
                onSelectVidSrcServer = { viewModel.setVidSrcServer(it) },
                onSaveVidSrcServerOrder = { viewModel.setVidSrcServerOrder(it) },
                onDismiss = { viewModel.setShowServerDialog(false) },
                onStream = { title, id, isTv, season, episode ->
                    viewModel.streamCustomMedia(title, id, isTv, season, episode)
                }
            )
        }

        // Create / Upload / Stream Modal Sheet
        if (uiState.showCreateSheet) {
            CreateSheet(
                onDismiss = { viewModel.setShowCreateSheet(false) },
                onOpenStreamServer = { viewModel.setShowServerDialog(true) }
            )
        }

        // Download Options Sheet (Server, Quality, CC selection)
        uiState.pendingDownloadTarget?.let { target ->
            com.example.ui.components.DownloadOptionsSheet(
                target = target,
                availableStorageBytes = uiState.availableStorageBytes,
                onDismiss = { viewModel.dismissDownloadOptions() },
                onBrowseTorrentSources = {
                    val (video, season, ep) = when (target) {
                        is com.example.ui.components.DownloadTarget.Movie -> Triple(target.video, null, null)
                        is com.example.ui.components.DownloadTarget.Episode -> Triple(target.video, target.episode.seasonNumber, target.episode.episodeNumber)
                        is com.example.ui.components.DownloadTarget.Season -> Triple(target.video, target.seasonNumber, null)
                    }
                    viewModel.dismissDownloadOptions()
                    viewModel.openTorrentSourcesForMedia(video, season, ep)
                },
                onConfirmDownload = { server, quality, subtitleCc ->
                    viewModel.startConfiguredDownload(
                        target = target,
                        server = server,
                        quality = quality,
                        subtitleCc = subtitleCc
                    )
                },
                isAutoPickBest = uiState.isAutoPickBestTorrent,
                onAutoPickChanged = { viewModel.setAutoPickBestTorrent(it) },
                onAutoDownload = { quality ->
                    viewModel.startAutoBestDownload(
                        target = target,
                        quality = quality
                    )
                },
                torrentIndexers = remember(uiState.torrentIndexerOrder) {
                    val order = uiState.torrentIndexerOrder
                    val all = com.example.data.torrent.TorrentSourceRegistry.VERIFIED_INDEXERS
                    if (order.isEmpty()) all
                    else order.mapNotNull { id -> all.firstOrNull { it.id == id } } + all.filter { a -> order.none { it == a.id } }
                },
                disabledTorrentIndexers = uiState.disabledTorrentIndexers,
                onToggleTorrentIndexer = { id, on -> viewModel.setTorrentIndexerEnabled(id, on) },
                initialSubtitleLanguage = uiState.offlineSubtitleLanguage,
                isSubtitleAutoDownload = uiState.isSubtitleAutoDownload,
                onSubtitleAutoDownloadChanged = { viewModel.setSubtitleAutoDownload(it) }
            )
        }

        // Torrent Swarm Source Selector Dialog (exact matches + packs, real S/E)
        val torrentMedia = uiState.selectedTorrentMedia
        if (uiState.showTorrentSourceDialog && torrentMedia != null) {
            val selSeason = uiState.selectedTorrentSeason ?: 1
            val selEpisode = uiState.selectedTorrentEpisode ?: 1
            val realSeasons = (1..uiState.totalSeasons.coerceAtLeast(1).coerceAtMost(60)).toList()
            val seasonEps = uiState.tvEpisodes.filter { it.seasonNumber == selSeason }
                .map { it.episodeNumber }.distinct().sorted()
            com.example.ui.components.TorrentSourceDialog(
                video = torrentMedia,
                sources = uiState.torrentSources,
                isLoading = uiState.isLoadingTorrentSources,
                selectedSeason = uiState.selectedTorrentSeason,
                selectedEpisode = uiState.selectedTorrentEpisode,
                onSeasonSelected = { s ->
                    viewModel.openTorrentSourcesForMedia(torrentMedia, s, uiState.selectedTorrentEpisode ?: 1)
                },
                onEpisodeSelected = { e ->
                    viewModel.openTorrentSourcesForMedia(torrentMedia, uiState.selectedTorrentSeason ?: 1, e)
                },
                onDownload = { source ->
                    viewModel.downloadTorrentSource(
                        source = source,
                        video = torrentMedia,
                        season = uiState.selectedTorrentSeason,
                        episode = uiState.selectedTorrentEpisode
                    )
                },
                onDismiss = { viewModel.setShowTorrentSourceDialog(false) },
                packs = uiState.torrentPacks,
                availableSeasons = realSeasons,
                availableEpisodeNumbers = seasonEps.ifEmpty { (1..24).toList() },
                requestedEpCode = if (torrentMedia.mediaType == com.example.model.MediaType.TV_SHOW) {
                    "S%02dE%02d".format(selSeason, selEpisode)
                } else null
            )
        }

        // Custom Magnet Link Dialog
        if (uiState.showAddMagnetDialog) {
            com.example.ui.components.AddMagnetDialog(
                onDismiss = { viewModel.setShowAddMagnetDialog(false) },
                onAddMagnet = { uri, title, directUrl ->
                    viewModel.addCustomMagnet(uri, title, directUrl)
                }
            )
        }

        AnimatedVisibility(
            visible = showLaunchIntro && !isInPipMode,
            enter = fadeIn(animationSpec = tween(180)),
            exit = fadeOut(animationSpec = tween(260)),
            modifier = Modifier.fillMaxSize()
        ) {
            ClutubeLaunchIntro()
        }

        // Subtle film grain over the whole UI. Touch-transparent, one draw call.
        com.example.ui.components.FilmGrainOverlay(
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun shareVideo(context: android.content.Context, video: VideoItem) {
    val linkBuilder = Uri.Builder()
        .scheme("clutube")
        .authority("watch")
        .appendPath(video.id)
        .appendQueryParameter("title", video.title)
    // Preserve series position so the recipient opens the same episode.
    if (video.mediaType == com.example.model.MediaType.TV_SHOW) {
        linkBuilder
            .appendQueryParameter("season", video.currentSeason.coerceAtLeast(1).toString())
            .appendQueryParameter("episode", video.currentEpisode.coerceAtLeast(1).toString())
    }
    val appLink = linkBuilder.build()
    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(
            Intent.EXTRA_TEXT,
            "Watch '${video.title}' in CluTube: $appLink"
        )
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share video via"))
}
