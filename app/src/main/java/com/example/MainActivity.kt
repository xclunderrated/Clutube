package com.example

import android.Manifest
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
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

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PIP_PLAY_PAUSE -> {
                    viewModel.togglePlayPause()
                }
                ACTION_PIP_NEXT -> {
                    viewModel.playNextEpisode()
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
        }
        ContextCompat.registerReceiver(
            this,
            pipReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        viewModel.initSettings(applicationContext)
        com.example.data.torrent.TorrentEngine.initialize(applicationContext)
        setupMediaSession()
        handleDeepLink(intent)

        // Continuously update Picture-in-Picture auto-enter parameters when video playback state changes
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    updatePipParams(state.currentPlayingVideo, state.isPlaying)
                    updateMediaSession(state.currentPlayingVideo, state.isPlaying, state.currentPlaybackSnapshot)
                }
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
        try {
            unregisterReceiver(pipReceiver)
        } catch (_: Exception) {}
        mediaSession?.run {
            isActive = false
            release()
        }
        mediaSession = null
    }

    override fun onStop() {
        viewModel.flushPlaybackProgress()
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

    private fun createPipActions(isPlaying: Boolean, isTvShow: Boolean): List<RemoteAction> {
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

        // Next Episode Action for Series
        if (isTvShow) {
            val nextIcon = Icon.createWithResource(this, android.R.drawable.ic_media_next)
            val nextTitle = "Next Episode"
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

    private fun setupMediaSession() {
        mediaSession = MediaSession(this, "CluTubePlayback").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    if (!viewModel.uiState.value.isPlaying) viewModel.togglePlayPause()
                }

                override fun onPause() {
                    if (viewModel.uiState.value.isPlaying) viewModel.togglePlayPause()
                }

                override fun onSkipToNext() {
                    viewModel.playNextEpisode()
                }
            })
            setFlags(
                MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            isActive = true
        }
    }

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
        val position = snapshot?.normalizedPositionSeconds?.coerceAtLeast(0L) ?: 0L
        val actions = PlaybackState.ACTION_PLAY or
            PlaybackState.ACTION_PAUSE or
            if (video.mediaType == MediaType.TV_SHOW) PlaybackState.ACTION_SKIP_TO_NEXT else 0L
        session.setPlaybackState(
            PlaybackState.Builder()
                .setActions(actions)
                .setState(
                    if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                    position,
                    1f
                )
                .build()
        )
        session.setMetadata(
            MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, video.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, video.channelName)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, "CluTube")
                .build()
        )
    }

    private fun updatePipParams(video: VideoItem?, isPlaying: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shouldEnablePip = video != null && isPlaying
            val isTvShow = video?.mediaType == MediaType.TV_SHOW
            val actions = if (video != null) createPipActions(isPlaying, isTvShow) else emptyList()

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
                val isTvShow = state.currentPlayingVideo.mediaType == MediaType.TV_SHOW
                val actions = createPipActions(state.isPlaying, isTvShow)
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

    if (isInPipMode && uiState.currentPlayingVideo != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .testTag("pip_player_fullscreen")
        ) {
            YouTubePlayer(
                video = uiState.currentPlayingVideo!!,
                selectedServerId = uiState.selectedServerId,
                onSelectServer = { viewModel.setStreamServer(it) },
                isTouchEnabled = false,
                resumePositionSeconds = uiState.currentHistoryEntry?.positionSeconds?.toDouble() ?: 0.0,
                playWhenReady = uiState.isPlaying,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    BackHandler(
        enabled = isFullscreen ||
            uiState.showHistoryScreen ||
            uiState.showCommentsSheet ||
            uiState.isChannelScreenOpen ||
            uiState.isPlayerExpanded ||
            uiState.isSearching ||
            uiState.isQueuePanelOpen ||
            uiState.selectedTab == 1
    ) {
        when {
            isFullscreen -> activity?.let { com.example.util.FullscreenHelper.exitFullscreen(it) }
            uiState.showHistoryScreen -> viewModel.setShowHistoryScreen(false)
            uiState.showCommentsSheet -> viewModel.setShowCommentsSheet(false)
            uiState.isChannelScreenOpen -> viewModel.closeChannel()
            uiState.isPlayerExpanded -> viewModel.minimizePlayer()
            uiState.isSearching -> viewModel.exitSearch()
            uiState.isQueuePanelOpen -> viewModel.setQueuePanelOpen(false)
            uiState.selectedTab == 1 -> viewModel.selectTab(0)
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
                                badgeCount = uiState.userNotificationCount
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
                        when (uiState.selectedTab) {
                            0 -> HomeScreen(
                                videos = uiState.videos,
                                shorts = uiState.shorts,
                                continueWatching = uiState.continueWatching,
                                recentWatched = uiState.recentWatched,
                                showContinueWatching = uiState.showContinueWatchingOnHome,
                                selectedCategory = uiState.selectedCategory,
                                isLoading = uiState.isLoading,
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
                                onShortClick = {
                                    viewModel.selectShort(it)
                                    viewModel.selectTab(1)
                                },
                                 onSaveToWatchLater = { viewModel.toggleSave(it.id) },
                                 onShare = { shareVideo(context, it) },
                                 onAddToQueue = { viewModel.addToQueue(it) },
                                 watchedVideoIds = uiState.watchedVideoIds,
                                 onToggleWatched = { viewModel.toggleWatched(it.id) },
                                 notInterestedVideoIds = uiState.notInterestedVideoIds,
                                 notRecommendedChannelNames = uiState.notRecommendedChannelNames,
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
                                 onOpenServerDialog = { viewModel.setShowServerDialog(true) }
                            )

                            1 -> ShortsScreen(
                                shorts = uiState.shorts,
                                currentIndex = uiState.currentShortIndex,
                                isLoading = uiState.isShortsLoading,
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
                                 onToggleWatched = { viewModel.toggleWatched(it.id) },
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
                                 }
                            )

                            3 -> NotificationsScreen(
                                notifications = uiState.notifications,
                                upcomingVideos = uiState.upcomingVideos,
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
                                onOpenDownloads = { viewModel.openDownloadsScreen() }
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
                 onToggleWatched = { viewModel.toggleWatched(it.id) },
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
                 onDownloadVideo = { viewModel.downloadVideoFromMenu(it) }
            )
        }

        if (uiState.showHistoryScreen) {
            HistoryScreen(
                entries = uiState.watchHistory,
                onBack = { viewModel.setShowHistoryScreen(false) },
                onResume = { viewModel.resumeWatch(it, expand = true) },
                onRemove = { viewModel.removeWatchHistoryEntry(it) },
                onClear = { viewModel.clearWatchHistory() },
                onToggleWatched = { viewModel.toggleWatched(it.id) }
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
                onAddMagnet = { viewModel.setShowAddMagnetDialog(true) }
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
                    tvEpisodes = uiState.tvEpisodes,
                    totalSeasons = uiState.totalSeasons,
                    selectedSeason = uiState.selectedSeason,
                     selectedServerId = uiState.selectedServerId,
                     resumePositionSeconds = uiState.currentHistoryEntry?.positionSeconds?.toDouble() ?: 0.0,
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
                     onSelectVideo = { viewModel.playVideo(it, expand = true) },
                     onSaveToWatchLater = { viewModel.toggleSave(it.id) },
                     onShare = { shareVideo(context, it) },
                     onAddToQueue = { viewModel.addToQueue(it) },
                     watchedVideoIds = uiState.watchedVideoIds,
                     onToggleWatched = { viewModel.toggleWatched(it.id) },
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
                     isMovieDownloaded = uiState.downloads.any { it.mediaType == MediaType.MOVIE.name && it.id == video.id && it.status == com.example.data.local.DownloadStatus.COMPLETED.name },
                     movieDownloadProgress = uiState.downloads.firstOrNull { it.mediaType == MediaType.MOVIE.name && it.id == video.id && it.status == com.example.data.local.DownloadStatus.DOWNLOADING.name }?.progressPercent,
                     isEpisodeDownloaded = { s, e -> uiState.downloads.any { it.mediaType == MediaType.TV_SHOW.name && it.id == "${video.id}_s${s}_e${e}" && it.status == com.example.data.local.DownloadStatus.COMPLETED.name } },
                     getEpisodeDownloadProgress = { s, e -> uiState.downloads.firstOrNull { it.mediaType == MediaType.TV_SHOW.name && it.id == "${video.id}_s${s}_e${e}" && it.status == com.example.data.local.DownloadStatus.DOWNLOADING.name }?.progressPercent }
                 )
            }
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
            val channel = uiState.selectedChannel!!
            val isSubscribed = uiState.subscribedChannelNames.contains(channel.name)
            ChannelScreen(
                channel = channel,
                videos = uiState.channelVideos,
                isSubscribed = isSubscribed,
                isLoading = uiState.isChannelLoading,
                onBack = { viewModel.closeChannel() },
                onVideoClick = { viewModel.playVideo(it, expand = true) },
                 onToggleSubscribe = { viewModel.toggleSubscribe(channel.name) },
                 onSaveToWatchLater = { viewModel.toggleSave(it.id) },
                 onShareVideo = { shareVideo(context, it) },
                 onAddToQueue = { viewModel.addToQueue(it) },
                 watchedVideoIds = uiState.watchedVideoIds,
                 onToggleWatched = { viewModel.toggleWatched(it.id) },
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
                    resumePositionSeconds = uiState.currentHistoryEntry?.positionSeconds?.toDouble() ?: 0.0,
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
                }
            )
        }

        // Torrent Swarm Source Selector Dialog
        if (uiState.showTorrentSourceDialog && uiState.selectedTorrentMedia != null) {
            com.example.ui.components.TorrentSourceDialog(
                video = uiState.selectedTorrentMedia!!,
                sources = uiState.torrentSources,
                isLoading = uiState.isLoadingTorrentSources,
                selectedSeason = uiState.selectedTorrentSeason,
                selectedEpisode = uiState.selectedTorrentEpisode,
                onSeasonSelected = { s ->
                    viewModel.openTorrentSourcesForMedia(uiState.selectedTorrentMedia!!, s, uiState.selectedTorrentEpisode ?: 1)
                },
                onEpisodeSelected = { e ->
                    viewModel.openTorrentSourcesForMedia(uiState.selectedTorrentMedia!!, uiState.selectedTorrentSeason ?: 1, e)
                },
                onDownload = { source ->
                    viewModel.downloadTorrentSource(
                        source = source,
                        video = uiState.selectedTorrentMedia!!,
                        season = uiState.selectedTorrentSeason,
                        episode = uiState.selectedTorrentEpisode
                    )
                },
                onDismiss = { viewModel.setShowTorrentSourceDialog(false) }
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
    }
}

private fun shareVideo(context: android.content.Context, video: VideoItem) {
    val appLink = Uri.Builder()
        .scheme("clutube")
        .authority("watch")
        .appendPath(video.id)
        .appendQueryParameter("title", video.title)
        .build()
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
