package com.example.viewmodel

import androidx.compose.runtime.Immutable
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.StreamService
import com.example.data.NetworkMonitor
import com.example.data.local.LocalStore
import com.example.data.tmdb.TmdbEpisodeItem
import com.example.data.tmdb.TmdbRepository
import com.example.model.ChannelItem
import com.example.model.CommentItem
import com.example.model.DeviceLayoutMode
import com.example.model.MediaType
import com.example.model.PlayerEvent
import com.example.model.PlayerSnapshot
import com.example.model.ShortItem
import com.example.model.VideoItem
import com.example.model.WatchHistoryEntry
import com.example.model.WatchLaterSort
import com.example.model.AppNotification
import com.example.model.PlaybackPreferences
import com.example.model.ReleaseAlert
import com.example.model.SearchHistoryItem
import com.example.model.deduplicateContinueWatching
import com.example.model.isUnreleased
import com.example.model.playbackKey
import com.example.model.releaseAlertId
import com.example.model.releaseDateMillis
import com.example.notification.ReleaseNotificationScheduler
import com.example.data.download.DownloadManager
import com.example.data.local.DownloadEntity
import com.example.data.model.TorrentSource
import com.example.data.torrent.TorrentIndexerService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

@Immutable
data class YouTubeUiState(
    val selectedTab: Int = 0,
    val selectedCategory: String = "All",
    val videos: List<VideoItem> = emptyList(),
    val upcomingVideos: List<VideoItem> = emptyList(),
    val isUpcomingLoading: Boolean = false,
    val shorts: List<ShortItem> = emptyList(),
    val isShortsLoading: Boolean = true,
    val channels: List<ChannelItem> = TmdbRepository.getStudioChannels(),
    val comments: List<CommentItem> = emptyList(),
    val currentPlayingVideo: VideoItem? = null,
    val relatedVideos: List<VideoItem> = emptyList(),
    val tvEpisodes: List<TmdbEpisodeItem> = emptyList(),
    val totalSeasons: Int = 1,
    val selectedSeason: Int = 1,
    val isPlayerExpanded: Boolean = false,
    val isPlaying: Boolean = true,
    val isMuted: Boolean = false,
    val selectedServerId: String = StreamService.DEFAULT_SERVER_ID,
    val selectedVidSrcServerHost: String = StreamService.DEFAULT_VIDSRC_SERVER_HOST,
    val vidSrcServerOrder: List<String> = StreamService.VIDSRC_SERVER_HOSTS,
    val currentShortIndex: Int = 0,
    val isDarkMode: Boolean = true,
    val deviceLayoutMode: DeviceLayoutMode = DeviceLayoutMode.MOBILE,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<VideoItem> = emptyList(),
    val isLoading: Boolean = true,
    val isFeedRefreshing: Boolean = false,
    val feedErrorMessage: String? = null,
    val isOffline: Boolean = false,
    val isLoadingMore: Boolean = false,
    val canLoadMore: Boolean = true,
    val isSearchLoading: Boolean = false,
    val searchErrorMessage: String? = null,
    val searchHistory: List<SearchHistoryItem> = emptyList(),
    val isSearchCacheStale: Boolean = false,
    val likedVideoIds: Set<String> = emptySet(),
    val dislikedVideoIds: Set<String> = emptySet(),
    val savedVideoIds: Set<String> = emptySet(),
    val watchedVideoIds: Set<String> = emptySet(),
    val notInterestedVideoIds: Set<String> = emptySet(),
    val notRecommendedChannelNames: Set<String> = emptySet(),
    val savedVideoOrder: List<String> = emptyList(),
    val persistentWatchLaterVideos: List<VideoItem> = emptyList(),
    val queue: List<VideoItem> = emptyList(),
    val isQueuePanelOpen: Boolean = false,
    val watchLaterSort: WatchLaterSort = WatchLaterSort.RECENTLY_ADDED,
    val localProfileName: String = "Clutube",
    val localProfileAvatar: String = "C",
    val subscribedChannelNames: Set<String> = setOf("Warner Bros. Pictures", "Netflix", "Marvel Studios"),
    val selectedChannel: ChannelItem? = null,
    val isChannelScreenOpen: Boolean = false,
    val channelVideos: List<VideoItem> = emptyList(),
    val isChannelLoading: Boolean = false,
    val watchHistory: List<WatchHistoryEntry> = emptyList(),
    val showCommentsSheet: Boolean = false,
    val showCreateSheet: Boolean = false,
    val showServerDialog: Boolean = false,
    val showCastDialog: Boolean = false,
    val showNotificationsSheet: Boolean = false,
    val notifications: List<AppNotification> = emptyList(),
    val releaseAlerts: List<ReleaseAlert> = emptyList(),
    val userNotificationCount: Int = 0,
    val userFeedbackMessage: String? = null,
    val customStreamInputId: String = "",
    val customStreamInputTitle: String = "",
    val isAutoNextEpisodeEnabled: Boolean = true,
    val showContinueWatchingOnHome: Boolean = false,
    val releaseNotificationsEnabled: Boolean = true,
    val currentPlaybackSnapshot: PlayerSnapshot? = null,
    val showHistoryScreen: Boolean = false,
    val showDownloadsScreen: Boolean = false,
    val downloads: List<DownloadEntity> = emptyList(),
    val activeDownloadSpeeds: Map<String, Long> = emptyMap(),
    val usedStorageBytes: Long = 0L,
    val availableStorageBytes: Long = 0L,
    val totalStorageBytes: Long = 0L,
    val pendingDownloadTarget: com.example.ui.components.DownloadTarget? = null,
    val showAddMagnetDialog: Boolean = false,
    val showTorrentSourceDialog: Boolean = false,
    val torrentSources: List<TorrentSource> = emptyList(),
    val isLoadingTorrentSources: Boolean = false,
    val selectedTorrentMedia: VideoItem? = null,
    val selectedTorrentSeason: Int? = null,
    val selectedTorrentEpisode: Int? = null,
    val playbackPreferences: PlaybackPreferences = PlaybackPreferences()
) {
    val continueWatching: List<WatchHistoryEntry>
        get() = deduplicateContinueWatching(watchHistory)

    /** The two most recently touched titles are promoted to normal Home cards. */
    val recentWatched: List<WatchHistoryEntry>
        get() = watchHistory
            .sortedByDescending { it.lastWatchedAtMillis }
            .groupBy(::recentHistoryGroupKey)
            .values
            .mapNotNull { entries ->
                entries.maxWithOrNull(
                    compareBy<WatchHistoryEntry> { it.lastWatchedAtMillis }
                        .thenBy { it.positionSeconds }
                )
            }
            .sortedByDescending { it.lastWatchedAtMillis }
            .take(2)

    val currentHistoryEntry: WatchHistoryEntry?
        get() = currentPlayingVideo?.playbackKey()?.let { key ->
            watchHistory.firstOrNull { it.key == key }
        }

    val savedVideos: List<VideoItem>
        get() {
            val knownVideos = (
                persistentWatchLaterVideos +
                    videos +
                    relatedVideos +
                    channelVideos +
                    queue +
                    watchHistory.map { it.video } +
                    listOfNotNull(currentPlayingVideo)
                ).distinctBy { it.id }
            val byId = knownVideos.associateBy { it.id }
            val orderedIds = savedVideoOrder + (savedVideoIds - savedVideoOrder.toSet())
            val fromKnown = orderedIds.mapNotNull(byId::get)
                .filter { it.id in savedVideoIds }
            val existingIds = fromKnown.map { it.id }.toSet()
            val remaining = persistentWatchLaterVideos.filter { it.id in savedVideoIds && it.id !in existingIds }
            return fromKnown + remaining
        }
}

class YouTubeViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(YouTubeUiState())
    val uiState: StateFlow<YouTubeUiState> = _uiState.asStateFlow()

    private var settingsManager: com.example.data.SettingsManager? = null
    private var localStore: LocalStore? = null
    private var downloadManager: DownloadManager? = null
    private var networkMonitor: NetworkMonitor? = null
    private var playbackContext: Context? = null
    private var searchJob: Job? = null
    private var pendingHistorySaveJob: Job? = null
    private var lastHistoryPersistAtMillis: Long = 0L
    private var lastEndedKey: String? = null
    private var lastEndedGeneration: Long? = null
    private var failoverMediaKey: String? = null
    private val failoverAttemptedServers = linkedSetOf<String>()
    private var vidSrcFailoverMediaKey: String? = null
    private val vidSrcAttemptedServerHosts = linkedSetOf<String>()
    private var pendingNextEpisodeLookupKey: String? = null

    private var currentFeedPage: Int = 1
    private var isCurrentlyLoadingMore: Boolean = false

    init {
        viewModelScope.launch {
            TmdbRepository.warmLogoCache()
            _uiState.update { current ->
                val remapArtwork: (VideoItem) -> VideoItem = TmdbRepository::applyCachedChannelArtwork
                current.copy(
                    channels = computeSubscribedChannels(current.subscribedChannelNames),
                    videos = current.videos.map(remapArtwork),
                    relatedVideos = current.relatedVideos.map(remapArtwork),
                    channelVideos = current.channelVideos.map(remapArtwork),
                    queue = current.queue.map(remapArtwork),
                    shorts = current.shorts.map { TmdbRepository.applyCachedChannelArtwork(it) },
                    currentPlayingVideo = current.currentPlayingVideo?.let(remapArtwork),
                    watchHistory = current.watchHistory.map { entry -> entry.copy(video = remapArtwork(entry.video)) }
                )
            }
        }
        com.example.util.PlayerViewManager.onPlayerEvent = ::handlePlayerEvent
        com.example.util.PlayerViewManager.onVidSrcServerSelected = { host ->
            setVidSrcServer(host, reload = false)
        }
        com.example.util.PlayerViewManager.onVidSrcServerOrderChanged = { order ->
            setVidSrcServerOrder(order)
        }
    }

    private fun computeSubscribedChannels(subs: Set<String>): List<ChannelItem> {
        val allStudios = TmdbRepository.getStudioChannels()
        val list = mutableListOf<ChannelItem>()
        for (studio in allStudios) {
            if (subs.contains(studio.name)) {
                list.add(studio.copy(isSubscribed = true))
            }
        }
        for (name in subs) {
            if (list.none { it.name.equals(name, ignoreCase = true) }) {
                list.add(TmdbRepository.getStudioChannelByName(name).copy(isSubscribed = true))
            }
        }
        return if (list.isNotEmpty()) list else allStudios.take(4).map { it.copy(isSubscribed = false) }
    }

    private fun canonicalizeChannelNames(names: Set<String>): Set<String> = names
        .map { TmdbRepository.getStudioChannelByName(it).name }
        .toSet()

    fun initSettings(context: android.content.Context) {
        if (settingsManager != null) return
        val manager = com.example.data.SettingsManager(context)
        settingsManager = manager
        localStore = LocalStore(context)
        networkMonitor = NetworkMonitor(context).also { it.start() }
        playbackContext = context.applicationContext

        val savedHistory = manager.getWatchHistoryEntries()
        val savedOrder = manager.savedVideoOrder
            .ifEmpty { manager.savedVideoIds.toList() }
        val subs = canonicalizeChannelNames(manager.subscribedChannelNames)
        manager.subscribedChannelNames = subs
        val syncedChannels = computeSubscribedChannels(subs)

        _uiState.update { current ->
            current.copy(
                isDarkMode = manager.isDarkMode,
                selectedServerId = manager.selectedServerId,
                selectedVidSrcServerHost = manager.selectedVidSrcServerId,
                vidSrcServerOrder = manager.vidSrcServerOrder,
                likedVideoIds = manager.likedVideoIds,
                dislikedVideoIds = manager.dislikedVideoIds,
                savedVideoIds = manager.savedVideoIds,
                watchedVideoIds = manager.watchedVideoIds,
                notInterestedVideoIds = manager.notInterestedVideoIds,
                notRecommendedChannelNames = manager.notRecommendedChannelNames,
                savedVideoOrder = savedOrder,
                queue = manager.getQueue(),
                localProfileName = manager.localProfileName,
                localProfileAvatar = manager.localProfileAvatar,
                subscribedChannelNames = subs,
                channels = syncedChannels,
                watchHistory = savedHistory,
                isAutoNextEpisodeEnabled = manager.isAutoNextEnabled,
                showContinueWatchingOnHome = manager.showContinueWatchingOnHome,
                releaseNotificationsEnabled = manager.releaseNotificationsEnabled,
                playbackPreferences = manager.getPlaybackPreferences(manager.selectedServerId),
                isOffline = networkMonitor?.isOnline?.value == false
            )
        }

        viewModelScope.launch {
            val store = localStore ?: return@launch
            val notifications = store.getNotifications()
            val alerts = store.getReleaseAlerts()
            val searches = store.getSearchHistory()
            val watchLaterVideos = store.getWatchLaterVideos()
            val watchLaterIds = watchLaterVideos.map { it.id }.toSet()
            _uiState.update {
                val combinedSavedIds = (it.savedVideoIds + watchLaterIds)
                val combinedOrder = (it.savedVideoOrder + watchLaterVideos.map { v -> v.id }).distinct()
                it.copy(
                    persistentWatchLaterVideos = watchLaterVideos,
                    savedVideoIds = combinedSavedIds,
                    savedVideoOrder = combinedOrder,
                    notifications = notifications,
                    releaseAlerts = alerts,
                    searchHistory = searches,
                    userNotificationCount = notifications.count { item -> !item.isRead && !item.isDismissed }
                )
            }
        }

        viewModelScope.launch {
            val monitor = networkMonitor ?: return@launch
            var wasOnline = monitor.isOnline.value
            monitor.isOnline.collect { online ->
                _uiState.update { it.copy(isOffline = !online) }
                if (online && !wasOnline) {
                    reloadCurrentCategory()
                    loadUpcomingContent()
                    loadTrailerShorts()
                    if (com.example.util.PlayerViewManager.hasPlayerError.value) {
                        retryCurrentPlayback()
                    }
                }
                wasOnline = online
            }
        }

        val dm = DownloadManager.getInstance(context)
        downloadManager = dm

        viewModelScope.launch {
            dm.getAllDownloadsFlow().collect { allDownloads ->
                _uiState.update { current ->
                    current.copy(
                        downloads = allDownloads,
                        usedStorageBytes = dm.getUsedStorageBytes(),
                        availableStorageBytes = dm.getAvailableStorageBytes(),
                        totalStorageBytes = dm.getTotalStorageBytes()
                    )
                }
            }
        }

        viewModelScope.launch {
            dm.activeDownloadSpeed.collect { speeds ->
                _uiState.update { it.copy(activeDownloadSpeeds = speeds) }
            }
        }

        loadUpcomingContent()
        loadTrailerShorts()
        reloadCurrentCategory()
        ReleaseNotificationScheduler.schedule(context.applicationContext)
    }

    fun selectTab(index: Int) {
        _uiState.update { it.copy(selectedTab = index, isSearching = false) }
    }

    fun setWatchLaterSort(sort: WatchLaterSort) {
        _uiState.update { it.copy(watchLaterSort = sort) }
    }

    fun saveLocalProfile(name: String, avatar: String) {
        val safeName = name.trim().ifBlank { "Clutube" }
        val avatarValue = avatar.trim()
        val safeAvatar = if (isProfileImageReference(avatarValue)) {
            avatarValue
        } else {
            avatarValue.take(2).ifBlank { safeName.take(1) }.uppercase()
        }
        settingsManager?.localProfileName = safeName
        settingsManager?.localProfileAvatar = safeAvatar
        _uiState.update { it.copy(localProfileName = safeName, localProfileAvatar = safeAvatar) }
    }

    fun selectCategory(category: String) {
        _uiState.update { it.copy(selectedCategory = category) }
        loadCategoryContent(category)
    }

    private fun loadCategoryContent(category: String) {
        isCurrentlyLoadingMore = false
        viewModelScope.launch {
            currentFeedPage = 1
            val cacheKey = "feed:${category.trim().lowercase()}:page:1"
            val cached = localStore?.getCatalog(cacheKey)
            _uiState.update {
                it.copy(
                    relatedVideos = emptyList(),
                    videos = cached?.videos?.map(TmdbRepository::applyCachedChannelArtwork)
                        ?: it.videos,
                    isLoading = cached == null,
                    isFeedRefreshing = cached != null,
                    feedErrorMessage = null,
                    canLoadMore = true,
                    isLoadingMore = false
                )
            }
            if (_uiState.value.isOffline) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isFeedRefreshing = false,
                        feedErrorMessage = if (cached == null) "Connect to load the catalog." else null,
                        canLoadMore = cached != null
                    )
                }
                return@launch
            }
            val result = when (category) {
                "All" -> TmdbRepository.getTrendingFeed(page = 1)
                "Movies" -> TmdbRepository.getMoviesFeed("Popular", page = 1)
                "Series / TV" -> TmdbRepository.getTvShowsFeed("Popular", page = 1)
                "Top Rated" -> TmdbRepository.getMoviesFeed("Top Rated", page = 1)
                "Now Playing" -> TmdbRepository.getMoviesFeed("Now Playing", page = 1)
                else -> {
                    if (TmdbRepository.GENRE_NAME_TO_ID.containsKey(category)) {
                        TmdbRepository.getByGenre(category, page = 1)
                    } else {
                        TmdbRepository.getTrendingFeed(page = 1)
                    }
                }
            }

            result.onSuccess { fetchedVideos ->
                if (_uiState.value.selectedCategory != category) return@onSuccess
                localStore?.putCatalog(cacheKey, fetchedVideos)
                if (fetchedVideos.isNotEmpty()) {
                    _uiState.update { current ->
                        current.copy(
                            videos = fetchedVideos.map(TmdbRepository::applyCachedChannelArtwork),
                            isLoading = false,
                            isFeedRefreshing = false,
                            feedErrorMessage = null,
                            canLoadMore = true
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isFeedRefreshing = false,
                            feedErrorMessage = "No titles are available right now.",
                            canLoadMore = false
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isFeedRefreshing = false,
                        feedErrorMessage = if (cached == null) {
                            error.message?.takeIf(String::isNotBlank) ?: "The catalog could not be loaded."
                        } else {
                            "Showing saved catalog data. Pull to refresh when connected."
                        },
                        canLoadMore = cached != null
                    )
                }
            }
        }
    }

    private fun loadUpcomingContent() {
        viewModelScope.launch {
            val cacheKey = "feed:coming-soon:page:1"
            val cached = localStore?.getCatalog(cacheKey)
            _uiState.update {
                it.copy(
                    upcomingVideos = cached?.videos.orEmpty()
                        .filter { video -> isUnreleased(video.releaseDateIso ?: video.releaseDateFormatted) }
                        .map(TmdbRepository::applyCachedChannelArtwork),
                    isUpcomingLoading = cached == null
                )
            }
            if (_uiState.value.isOffline) {
                _uiState.update { it.copy(isUpcomingLoading = false) }
                return@launch
            }
            TmdbRepository.getUpcomingFeed()
                .onSuccess { videos ->
                    val upcoming = videos.filter { video ->
                        isUnreleased(video.releaseDateIso ?: video.releaseDateFormatted)
                    }
                    localStore?.putCatalog(cacheKey, videos)
                    _uiState.update {
                        it.copy(
                            upcomingVideos = upcoming.map(TmdbRepository::applyCachedChannelArtwork),
                            isUpcomingLoading = false
                        )
                    }
                }
                .onFailure { _uiState.update { it.copy(isUpcomingLoading = false) } }
        }
    }

    fun loadNextPage() {
        val currentState = _uiState.value
        if (isCurrentlyLoadingMore || currentState.isLoading || currentState.isLoadingMore || !currentState.canLoadMore || currentState.isSearching || currentState.isOffline) {
            return
        }

        isCurrentlyLoadingMore = true
        _uiState.update { it.copy(isLoadingMore = true) }

        viewModelScope.launch {
            val nextPage = currentFeedPage + 1
            val category = currentState.selectedCategory

            val result = when (category) {
                "All" -> TmdbRepository.getTrendingFeed(page = nextPage)
                "Movies" -> TmdbRepository.getMoviesFeed("Popular", page = nextPage)
                "Series / TV" -> TmdbRepository.getTvShowsFeed("Popular", page = nextPage)
                "Top Rated" -> TmdbRepository.getMoviesFeed("Top Rated", page = nextPage)
                "Now Playing" -> TmdbRepository.getMoviesFeed("Now Playing", page = nextPage)
                else -> {
                    if (TmdbRepository.GENRE_NAME_TO_ID.containsKey(category)) {
                        TmdbRepository.getByGenre(category, page = nextPage)
                    } else {
                        TmdbRepository.getTrendingFeed(page = nextPage)
                    }
                }
            }

            result.onSuccess { newVideos ->
                if (newVideos.isNotEmpty()) {
                    currentFeedPage = nextPage
                    localStore?.putCatalog(
                        "feed:${category.trim().lowercase()}:page:$nextPage",
                        newVideos
                    )
                    _uiState.update { state ->
                        val existingIds = state.videos.map { it.id }.toSet()
                        val uniqueNewVideos = newVideos
                            .map(TmdbRepository::applyCachedChannelArtwork)
                            .filter { it.id !in existingIds }
                        state.copy(
                            videos = state.videos + uniqueNewVideos,
                            isLoadingMore = false,
                            canLoadMore = uniqueNewVideos.isNotEmpty() && nextPage < 50
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoadingMore = false, canLoadMore = false) }
                }
                isCurrentlyLoadingMore = false
            }.onFailure {
                _uiState.update { it.copy(isLoadingMore = false) }
                isCurrentlyLoadingMore = false
            }
        }
    }

    fun setDeviceLayoutMode(mode: DeviceLayoutMode) {
        _uiState.update { it.copy(deviceLayoutMode = mode) }
    }

    fun reloadCurrentCategory() {
        loadCategoryContent(_uiState.value.selectedCategory)
    }

    private fun loadTrailerShorts() {
        if (_uiState.value.isOffline) {
            _uiState.update { it.copy(isShortsLoading = false) }
            return
        }
        _uiState.update { it.copy(isShortsLoading = true) }
        viewModelScope.launch {
            TmdbRepository.getTrailerShorts()
                .onSuccess { trailers ->
                    if (trailers.isNotEmpty()) {
                        _uiState.update { current ->
                            current.copy(
                                shorts = trailers.map { TmdbRepository.applyCachedChannelArtwork(it) },
                                currentShortIndex = current.currentShortIndex.coerceIn(0, trailers.lastIndex),
                                isShortsLoading = false
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isShortsLoading = false) }
                    }
                }
                .onFailure {
                    // Keep the local fallback cards visible when TMDB or
                    // YouTube is unavailable. A later refresh can replace
                    // them with real trailer entries.
                    _uiState.update { it.copy(isShortsLoading = false) }
                }
        }
    }

    fun resumeWatch(entry: WatchHistoryEntry, expand: Boolean = true) {
        playVideo(entry.video, expand = expand)
    }

    fun removeWatchHistoryEntry(key: String) {
        val updated = _uiState.value.watchHistory.filterNot { it.key == key }
        _uiState.update { it.copy(watchHistory = updated) }
        settingsManager?.removeWatchHistoryEntry(key)
    }

    fun clearWatchHistory() {
        _uiState.update { it.copy(watchHistory = emptyList()) }
        settingsManager?.clearWatchHistory()
    }

    fun setShowHistoryScreen(show: Boolean) {
        _uiState.update { it.copy(showHistoryScreen = show) }
    }

    fun flushPlaybackProgress() {
        // evaluateJavascript is asynchronous. Saving immediately here races
        // the final VidLink timeupdate and persisted resume points can lag by
        // minutes. Persist only after the WebView has reported its snapshot.
        com.example.util.PlayerViewManager.requestPlaybackSnapshot {
            saveHistoryNow(_uiState.value.watchHistory)
        }
    }

    private fun handlePlayerEvent(event: PlayerEvent) {
        when (event) {
            is PlayerEvent.Ready -> {
                // The manager already owns the loading state. A ready event is
                // intentionally lightweight so it cannot overwrite real media state.
            }

            is PlayerEvent.Progress -> handlePlaybackSnapshot(event.snapshot)
            is PlayerEvent.Ended -> handlePlaybackEnded(event.key, event.generation)
            is PlayerEvent.Error -> {
                // Retain the latest progress, then silently try the next
                // eligible route. The error surface is only shown when all
                // configured routes have been exhausted.
                flushPlaybackProgress()
                if (!tryAutomaticVidSrcMirrorFailover(event.key)) {
                    tryAutomaticServerFailover(event.key)
                }
            }
        }
    }

    private fun handlePlaybackSnapshot(snapshot: PlayerSnapshot) {
        val state = _uiState.value
        val currentVideo = state.currentPlayingVideo ?: return
        if (currentVideo.playbackKey() != snapshot.key) return

        val previousEntry = state.watchHistory.firstOrNull { it.key == snapshot.key } ?: return
        val rawDuration = snapshot.normalizedDurationSeconds
        val rawPosition = snapshot.normalizedPositionSeconds
        val previousDuration = previousEntry.durationSeconds

        // VidLink can briefly report 0/0 or a tiny placeholder duration while
        // its JW player is mounting. Keep the last trusted duration instead of
        // turning a valid resume point into a bogus 0:00/short episode.
        val duration = when {
            rawDuration <= 0L -> previousDuration
            previousDuration > 0L && rawDuration < MIN_RELIABLE_DURATION_SECONDS -> previousDuration
            previousDuration > 0L && rawDuration < previousDuration / 2L -> previousDuration
            else -> rawDuration
        }

        // Providers can emit a transient 0/0 snapshot while their metadata is
        // loading. Do not erase a saved resume point with that placeholder.
        if (previousEntry.positionSeconds > 0L &&
            state.currentPlaybackSnapshot == null &&
            rawPosition <= RESUME_RESET_TOLERANCE_SECONDS
        ) {
            return
        }

        val position = if (duration > 0L) min(rawPosition, duration) else rawPosition
        val completed = duration > 0L &&
            (position >= duration * 0.90 || duration - position <= COMPLETION_REMAINING_SECONDS)
        val updatedEntry = previousEntry.copy(
            key = snapshot.key,
            video = currentVideo,
            positionSeconds = position,
            durationSeconds = duration,
            lastWatchedAtMillis = System.currentTimeMillis(),
            completed = completed
        )
        val updatedHistory = upsertHistory(state.watchHistory, updatedEntry)
        val updatedWatched = if (completed && currentVideo.id.isNotBlank()) {
            val s = state.watchedVideoIds + currentVideo.id
            settingsManager?.watchedVideoIds = s
            s
        } else {
            state.watchedVideoIds
        }
        _uiState.update { current ->
            if (current.currentPlayingVideo?.playbackKey() != snapshot.key) current
            else current.copy(
                watchHistory = updatedHistory,
                watchedVideoIds = updatedWatched,
                isPlaying = snapshot.isPlaying,
                isMuted = snapshot.isMuted,
                currentPlaybackSnapshot = snapshot
            )
        }
        scheduleHistorySave()
    }

    private fun handlePlaybackEnded(key: String, generation: Long) {
        if (lastEndedKey == key && lastEndedGeneration == generation) return
        val state = _uiState.value
        if (state.currentPlayingVideo?.playbackKey() != key) return
        lastEndedKey = key
        lastEndedGeneration = generation

        val entry = state.watchHistory.firstOrNull { it.key == key } ?: return
        val duration = state.currentPlaybackSnapshot?.normalizedDurationSeconds
            ?.takeIf { it >= MIN_RELIABLE_DURATION_SECONDS }
            ?: entry.durationSeconds
        val completedEntry = entry.copy(
            positionSeconds = if (duration > 0L) duration else entry.positionSeconds,
            durationSeconds = duration,
            lastWatchedAtMillis = System.currentTimeMillis(),
            completed = true
        )
        val updatedHistory = upsertHistory(state.watchHistory, completedEntry)
        val updatedWatched = if (entry.video.id.isNotBlank()) {
            val s = state.watchedVideoIds + entry.video.id
            settingsManager?.watchedVideoIds = s
            s
        } else {
            state.watchedVideoIds
        }
        _uiState.update {
            if (it.currentPlayingVideo?.playbackKey() != key) it
            else it.copy(
                watchHistory = updatedHistory,
                watchedVideoIds = updatedWatched,
                isPlaying = false,
                currentPlaybackSnapshot = it.currentPlaybackSnapshot?.copy(
                    positionSeconds = completedEntry.positionSeconds.toDouble(),
                    durationSeconds = completedEntry.durationSeconds.toDouble(),
                    isPlaying = false
                )
            )
        }
        saveHistoryNow(updatedHistory)

        val queuedVideo = _uiState.value.queue.firstOrNull()
        if (queuedVideo != null) {
            removeFromQueue(queuedVideo.playbackKey())
            playVideo(queuedVideo, expand = _uiState.value.isPlayerExpanded)
            return
        }
        if (_uiState.value.isAutoNextEpisodeEnabled) {
            playNextEpisode()
        }
    }

    private fun markCurrentEntryCompleted() {
        val state = _uiState.value
        val video = state.currentPlayingVideo ?: return
        val key = video.playbackKey()
        val entry = state.watchHistory.firstOrNull { it.key == key } ?: return
        val duration = state.currentPlaybackSnapshot?.normalizedDurationSeconds
            ?.takeIf { it >= MIN_RELIABLE_DURATION_SECONDS }
            ?: entry.durationSeconds
        val completedEntry = entry.copy(
            positionSeconds = if (duration > 0L) duration else entry.positionSeconds,
            durationSeconds = duration,
            lastWatchedAtMillis = System.currentTimeMillis(),
            completed = true
        )
        val updatedHistory = upsertHistory(state.watchHistory, completedEntry)
        _uiState.update { it.copy(watchHistory = updatedHistory) }
        saveHistoryNow(updatedHistory)
    }

    private fun upsertHistory(
        history: List<WatchHistoryEntry>,
        entry: WatchHistoryEntry
    ): List<WatchHistoryEntry> {
        val normalized = entry.normalized()
        return (listOf(normalized) + history.filterNot { it.key == normalized.key })
            .take(HISTORY_LIMIT)
    }

    private fun saveHistoryNow(history: List<WatchHistoryEntry>) {
        pendingHistorySaveJob?.cancel()
        pendingHistorySaveJob = null
        settingsManager?.saveWatchHistoryEntries(history)
        lastHistoryPersistAtMillis = System.currentTimeMillis()
    }

    private fun scheduleHistorySave() {
        if (settingsManager == null || pendingHistorySaveJob?.isActive == true) return
        val waitMillis = (HISTORY_SAVE_INTERVAL_MILLIS -
            (System.currentTimeMillis() - lastHistoryPersistAtMillis)).coerceAtLeast(0L)
        pendingHistorySaveJob = viewModelScope.launch {
            delay(waitMillis)
            saveHistoryNow(_uiState.value.watchHistory)
        }
    }

    private fun resetAutomaticFailover() {
        failoverMediaKey = null
        failoverAttemptedServers.clear()
        vidSrcFailoverMediaKey = null
        vidSrcAttemptedServerHosts.clear()
        pendingNextEpisodeLookupKey = null
    }

    private fun setStreamServerInternal(serverId: String, remember: Boolean) {
        val normalizedServerId = StreamService.AVAILABLE_SERVERS
            .firstOrNull { it.id == serverId }
            ?.id
            ?: StreamService.DEFAULT_SERVER_ID
        if (remember) settingsManager?.selectedServerId = normalizedServerId
        _uiState.update { current ->
            val currentVideo = current.currentPlayingVideo
            val updatedVideo = currentVideo?.withStreamUrl(
                serverId = normalizedServerId,
                vidSrcHost = current.selectedVidSrcServerHost
            )
            val activeKey = currentVideo?.playbackKey()
            val updatedHistory = if (updatedVideo != null && activeKey != null) {
                current.watchHistory.map { entry ->
                    if (entry.key == activeKey) entry.copy(video = updatedVideo) else entry
                }
            } else {
                current.watchHistory
            }
            current.copy(
                selectedServerId = normalizedServerId,
                playbackPreferences = settingsManager?.getPlaybackPreferences(normalizedServerId)
                    ?: current.playbackPreferences,
                currentPlayingVideo = updatedVideo,
                watchHistory = updatedHistory,
                currentPlaybackSnapshot = null,
                isPlaying = if (updatedVideo != null) current.isPlaying else false
            )
        }
        saveHistoryNow(_uiState.value.watchHistory)
    }

    /**
     * Tries the next configured source without interrupting the user with the
     * server picker. The picker remains the final recovery action only after
     * every eligible route has failed.
     */
    private fun tryAutomaticServerFailover(key: String): Boolean {
        val current = _uiState.value
        val currentVideo = current.currentPlayingVideo ?: return false
        if (currentVideo.playbackKey() != key) return false

        if (failoverMediaKey != key) {
            failoverMediaKey = key
            failoverAttemptedServers.clear()
        }
        failoverAttemptedServers += current.selectedServerId

        val nextServer = StreamService
            .fallbackServerIds(current.selectedServerId)
            .firstOrNull { it !in failoverAttemptedServers }
            ?: return false

        failoverAttemptedServers += nextServer
        setStreamServerInternal(nextServer, remember = false)
        return true
    }

    private fun tryAutomaticVidSrcMirrorFailover(key: String): Boolean {
        val current = _uiState.value
        if (current.currentPlayingVideo?.playbackKey() != key ||
            current.selectedServerId != StreamService.VIDSRC_SERVER_ID
        ) return false

        if (vidSrcFailoverMediaKey != key) {
            vidSrcFailoverMediaKey = key
            vidSrcAttemptedServerHosts.clear()
        }
        vidSrcAttemptedServerHosts += current.selectedVidSrcServerHost

        val nextHost = current.vidSrcServerOrder
            .firstOrNull { it !in vidSrcAttemptedServerHosts }
            ?: return false
        vidSrcAttemptedServerHosts += nextHost
        setVidSrcServerInternal(nextHost, reload = true, resetFailover = false)
        return true
    }

    fun watchNowFromShort(video: VideoItem) {
        // The Watch screen is a destination, not a layer above Shorts. Move
        // the underlying tab to Home before expanding so minimizing returns
        // to the catalog instead of leaving Shorts mounted underneath.
        _uiState.update {
            it.copy(
                selectedTab = 0,
                isSearching = false,
                showCommentsSheet = false
            )
        }
        playVideo(video, expand = true)
    }

    fun playVideo(video: VideoItem, expand: Boolean = true) {
        resetAutomaticFailover()
        val current = _uiState.value
        val tmdbIdInt = video.tmdbId?.toIntOrNull()
        val isTv = video.mediaType == MediaType.TV_SHOW
        val readyVideo = video.withStreamUrl(
            serverId = current.selectedServerId,
            vidSrcHost = current.selectedVidSrcServerHost
        )
        val key = readyVideo.playbackKey()
        val previousEntry = current.watchHistory.firstOrNull { it.key == key }
        val resumedEntry = if (previousEntry != null && !previousEntry.completed) {
            previousEntry.copy(
                key = key,
                video = readyVideo,
                lastWatchedAtMillis = System.currentTimeMillis()
            )
        } else {
            WatchHistoryEntry(
                key = key,
                video = readyVideo,
                lastWatchedAtMillis = System.currentTimeMillis()
            )
        }
        val updatedHistory = upsertHistory(current.watchHistory, resumedEntry)

        _uiState.update { current ->
            current.copy(
                currentPlayingVideo = readyVideo,
                isPlayerExpanded = expand,
                isPlaying = true,
                watchHistory = updatedHistory,
                selectedSeason = readyVideo.currentSeason,
                tvEpisodes = emptyList(),
                currentPlaybackSnapshot = null,
                showHistoryScreen = false,
                // A channel page is a navigable surface, not a background
                // layer. Selecting one of its titles must replace it with
                // the Watch page instead of leaving the channel over the
                // newly expanded player.
                isChannelScreenOpen = false,
                selectedChannel = null,
                channelVideos = emptyList(),
                isChannelLoading = false
            )
        }
        saveHistoryNow(updatedHistory)
        if (previousEntry?.completed == true &&
            com.example.util.PlayerViewManager.activeMediaKey == key
        ) {
            playbackContext?.let { playerContext ->
                com.example.util.PlayerViewManager.reloadCurrentPlayer(
                    context = playerContext,
                    video = readyVideo,
                    serverId = current.selectedServerId,
                    resumePositionSeconds = 0.0,
                    playWhenReady = true,
                    vidSrcServerHost = current.selectedVidSrcServerHost
                )
            }
        }

        // Fetch full rich movie/series metadata, actors/cast, director, ratings, and real channel/studio name
        if (tmdbIdInt != null) {
            viewModelScope.launch {
                val fullDetailsResult = TmdbRepository.fetchFullMediaDetails(readyVideo)
                fullDetailsResult.onSuccess { enrichedVideo ->
                    _uiState.update { current ->
                        if (current.currentPlayingVideo?.playbackKey() == key) {
                                val enriched = enrichedVideo
                                .copy(
                                    currentSeason = readyVideo.currentSeason,
                                    currentEpisode = readyVideo.currentEpisode
                                )
                                .withStreamUrl(
                                    serverId = current.selectedServerId,
                                    vidSrcHost = current.selectedVidSrcServerHost
                                )
                            val enrichedHistory = current.watchHistory.map { entry ->
                                if (entry.key == key) entry.copy(video = enriched) else entry
                            }
                            current.copy(
                                currentPlayingVideo = enriched,
                                watchHistory = enrichedHistory
                            )
                        } else {
                            current
                        }
                    }
                }
            }
        }

        // Fetch TV show seasons & episodes if TV show
        if (isTv && tmdbIdInt != null) {
            fetchTvSeasonEpisodes(tmdbIdInt, readyVideo.currentSeason)
        }

        // Fetch Recommendations for "Up next"
        if (tmdbIdInt != null) {
            viewModelScope.launch {
                val recsResult = TmdbRepository.getRecommendations(tmdbIdInt, isTv)
                recsResult.onSuccess { recs ->
                    if (recs.isNotEmpty() && _uiState.value.currentPlayingVideo?.playbackKey() == key) {
                        _uiState.update { it.copy(relatedVideos = recs) }
                    }
                }
            }
        } else {
            _uiState.update { it.copy(relatedVideos = _uiState.value.videos) }
        }
    }

    fun selectTvSeason(season: Int) {
        val currentVideo = _uiState.value.currentPlayingVideo ?: return
        val tmdbIdInt = currentVideo.tmdbId?.toIntOrNull() ?: return

        val safeSeason = season.coerceAtLeast(1)
        _uiState.update {
            it.copy(
                selectedSeason = safeSeason,
                tvEpisodes = emptyList()
            )
        }
        fetchTvSeasonEpisodes(tmdbIdInt, safeSeason)
    }

    fun selectTvEpisode(season: Int, episode: Int) {
        resetAutomaticFailover()
        val current = _uiState.value
        val currentVideo = current.currentPlayingVideo ?: return
        val safeSeason = season.coerceAtLeast(1)
        val safeEpisode = episode.coerceAtLeast(1)
        val requestedEpisode = current.tvEpisodes.firstOrNull {
            it.seasonNumber == safeSeason && it.episodeNumber == safeEpisode
        }
        if (requestedEpisode != null && isUnreleased(requestedEpisode.airDate)) return
        val updatedVideo = currentVideo.copy(
            currentSeason = safeSeason,
            currentEpisode = safeEpisode
        ).withStreamUrl(
            serverId = current.selectedServerId,
            vidSrcHost = current.selectedVidSrcServerHost
        )
        val key = updatedVideo.playbackKey()
        val previousEntry = current.watchHistory.firstOrNull { it.key == key }
        val entry = if (previousEntry != null && !previousEntry.completed) {
            previousEntry.copy(
                key = key,
                video = updatedVideo,
                lastWatchedAtMillis = System.currentTimeMillis()
            )
        } else {
            WatchHistoryEntry(
                key = key,
                video = updatedVideo,
                lastWatchedAtMillis = System.currentTimeMillis()
            )
        }
        val updatedHistory = upsertHistory(current.watchHistory, entry)

        _uiState.update { current ->
            current.copy(
                currentPlayingVideo = updatedVideo,
                selectedSeason = safeSeason,
                isPlaying = true,
                watchHistory = updatedHistory,
                tvEpisodes = if (current.selectedSeason == safeSeason) current.tvEpisodes else emptyList(),
                currentPlaybackSnapshot = null
            )
        }
        saveHistoryNow(updatedHistory)

        if (current.selectedSeason != safeSeason) {
            fetchTvSeasonEpisodes(currentVideo.tmdbId?.toIntOrNull() ?: return, safeSeason)
        }
    }

    fun toggleAutoNextEpisode() {
        val enabled = !_uiState.value.isAutoNextEpisodeEnabled
        settingsManager?.isAutoNextEnabled = enabled
        _uiState.update { it.copy(isAutoNextEpisodeEnabled = enabled) }
    }

    fun setShowContinueWatchingOnHome(enabled: Boolean) {
        settingsManager?.showContinueWatchingOnHome = enabled
        _uiState.update { it.copy(showContinueWatchingOnHome = enabled) }
    }

    fun setReleaseNotificationsEnabled(enabled: Boolean) {
        settingsManager?.releaseNotificationsEnabled = enabled
        _uiState.update { it.copy(releaseNotificationsEnabled = enabled) }
        if (enabled) {
            playbackContext?.let { ReleaseNotificationScheduler.schedule(it) }
        }
    }

    fun playNextEpisode() {
        val current = _uiState.value
        val currentVideo = current.currentPlayingVideo ?: return
        if (currentVideo.mediaType != MediaType.TV_SHOW) return

        val totalSeasons = max(current.totalSeasons, currentVideo.totalSeasons)
        val next = EpisodeNavigator.nextEpisode(
            currentSeason = currentVideo.currentSeason,
            currentEpisode = currentVideo.currentEpisode,
            episodes = current.tvEpisodes.filterNot { isUnreleased(it.airDate) },
            totalSeasons = totalSeasons
        )

        if (next != null) {
            advanceToEpisode(next)
            return
        }

        // A player can finish before the TMDB episode request completes (or
        // when a custom TV title has no TMDB id). Retry the current season
        // once before giving up, so the provider cannot fall back to replaying
        // the just-finished episode.
        val tmdbId = currentVideo.tmdbId?.toIntOrNull()
        if (current.tvEpisodes.isNotEmpty() || tmdbId == null) {
            if (tmdbId == null) {
                val fallback = when {
                    currentVideo.totalEpisodes > currentVideo.currentEpisode ->
                        NextEpisode(currentVideo.currentSeason, currentVideo.currentEpisode + 1)
                    currentVideo.currentSeason < totalSeasons ->
                        NextEpisode(currentVideo.currentSeason + 1, 1)
                    else -> null
                }
                fallback?.let(::advanceToEpisode)
            }
            return
        }

        val currentKey = currentVideo.playbackKey()
        if (pendingNextEpisodeLookupKey == currentKey) return
        pendingNextEpisodeLookupKey = currentKey
        viewModelScope.launch {
            try {
                val episodes = TmdbRepository
                    .getTvEpisodes(tmdbId, currentVideo.currentSeason)
                    .getOrNull()
                    .orEmpty()
                    .sortedBy { it.episodeNumber }
                val latest = _uiState.value
                if (latest.currentPlayingVideo?.playbackKey() != currentKey) return@launch
                _uiState.update { state ->
                    if (state.currentPlayingVideo?.playbackKey() == currentKey) {
                        state.copy(tvEpisodes = episodes)
                    } else state
                }
                EpisodeNavigator.nextEpisode(
                    currentSeason = currentVideo.currentSeason,
                    currentEpisode = currentVideo.currentEpisode,
                    episodes = episodes.filterNot { isUnreleased(it.airDate) },
                    totalSeasons = max(
                        _uiState.value.totalSeasons,
                        _uiState.value.currentPlayingVideo?.totalSeasons ?: 1
                    )
                )?.let(::advanceToEpisode)
            } finally {
                if (pendingNextEpisodeLookupKey == currentKey) {
                    pendingNextEpisodeLookupKey = null
                }
            }
        }
    }

    private fun advanceToEpisode(next: NextEpisode) {
        markCurrentEntryCompleted()
        selectTvEpisode(next.season, next.episode)
    }

    private fun fetchTvSeasonEpisodes(tvId: Int, season: Int) {
        viewModelScope.launch {
            val seasonsCount = TmdbRepository.getTvTotalSeasons(tvId)
            val episodesResult = TmdbRepository.getTvEpisodes(tvId, season)
            episodesResult.onSuccess { episodes ->
                _uiState.update { current ->
                    val activeVideo = current.currentPlayingVideo
                    if (activeVideo?.tmdbId?.toIntOrNull() != tvId || current.selectedSeason != season) {
                        current
                    } else {
                        current.copy(
                            tvEpisodes = episodes.sortedBy { it.episodeNumber },
                            totalSeasons = seasonsCount.coerceAtLeast(1),
                            currentPlayingVideo = activeVideo.copy(
                                totalSeasons = seasonsCount.coerceAtLeast(1)
                            )
                        )
                    }
                }
            }
        }
    }

    fun togglePlayerExpand() {
        _uiState.update { it.copy(isPlayerExpanded = !it.isPlayerExpanded) }
    }

    fun minimizePlayer() {
        _uiState.update { it.copy(isPlayerExpanded = false) }
    }

    fun closePlayer() {
        resetAutomaticFailover()
        flushPlaybackProgress()
        com.example.util.PlayerViewManager.releasePlayer()
        _uiState.update {
            it.copy(
                currentPlayingVideo = null,
                isPlayerExpanded = false,
                currentPlaybackSnapshot = null
            )
        }
    }

    fun retryCurrentPlayback() {
        val state = _uiState.value
        val video = state.currentPlayingVideo ?: return
        val context = playbackContext ?: return
        resetAutomaticFailover()
        val resumePosition = state.currentPlaybackSnapshot?.normalizedPositionSeconds?.toDouble()
            ?: state.currentHistoryEntry?.positionSeconds?.toDouble()
            ?: 0.0
        com.example.util.PlayerViewManager.reloadCurrentPlayer(
            context = context,
            video = video,
            serverId = state.selectedServerId,
            resumePositionSeconds = resumePosition,
            playWhenReady = state.isPlaying,
            vidSrcServerHost = state.selectedVidSrcServerHost
        )
        _uiState.update { it.copy(currentPlaybackSnapshot = null) }
    }

    fun togglePlayPause() {
        val newPlaying = !_uiState.value.isPlaying
        com.example.util.PlayerViewManager.togglePlayPause(newPlaying)
        _uiState.update { it.copy(isPlaying = newPlaying) }
    }

    fun toggleMute() {
        val newMuted = !_uiState.value.isMuted
        com.example.util.PlayerViewManager.setMuted(newMuted)
        _uiState.update { it.copy(isMuted = newMuted) }
    }

    fun setStreamServer(serverId: String) {
        resetAutomaticFailover()
        setStreamServerInternal(serverId, remember = true)
    }

    fun setVidSrcServer(host: String, reload: Boolean = true) {
        setVidSrcServerInternal(host, reload, resetFailover = true)
    }

    private fun setVidSrcServerInternal(
        host: String,
        reload: Boolean,
        resetFailover: Boolean
    ) {
        if (!StreamService.isVidSrcServerHost(host)) return
        if (resetFailover) {
            vidSrcFailoverMediaKey = null
            vidSrcAttemptedServerHosts.clear()
        }
        val normalized = StreamService.normalizeVidSrcServerHost(host)
        settingsManager?.selectedVidSrcServerId = normalized
        val current = _uiState.value
        _uiState.update { state ->
            val currentVideo = state.currentPlayingVideo
            val updatedVideo = currentVideo?.withStreamUrl(
                serverId = state.selectedServerId,
                vidSrcHost = normalized
            )
            val activeKey = currentVideo?.playbackKey()
            val updatedHistory = if (updatedVideo != null && activeKey != null) {
                state.watchHistory.map { entry ->
                    if (entry.key == activeKey) entry.copy(video = updatedVideo) else entry
                }
            } else {
                state.watchHistory
            }
            state.copy(
                selectedVidSrcServerHost = normalized,
                currentPlayingVideo = updatedVideo,
                watchHistory = updatedHistory,
                currentPlaybackSnapshot = if (reload) null else state.currentPlaybackSnapshot
            )
        }
        saveHistoryNow(_uiState.value.watchHistory)

        if (reload &&
            current.selectedServerId == StreamService.VIDSRC_SERVER_ID &&
            _uiState.value.currentPlayingVideo != null
        ) {
            playbackContext?.let { context ->
                com.example.util.PlayerViewManager.reloadCurrentPlayer(
                    context = context,
                    video = _uiState.value.currentPlayingVideo!!,
                    serverId = current.selectedServerId,
                    resumePositionSeconds = _uiState.value.currentPlaybackSnapshot?.normalizedPositionSeconds?.toDouble()
                        ?: _uiState.value.currentHistoryEntry?.positionSeconds?.toDouble()
                        ?: 0.0,
                    playWhenReady = current.isPlaying,
                    vidSrcServerHost = normalized
                )
            }
        }
    }

    fun setVidSrcServerOrder(order: List<String>) {
        val normalized = StreamService.normalizeVidSrcServerOrder(order)
        settingsManager?.vidSrcServerOrder = normalized
        // The first saved mirror is the preferred mirror for the next load.
        val preferred = normalized.firstOrNull() ?: StreamService.DEFAULT_VIDSRC_SERVER_HOST
        settingsManager?.selectedVidSrcServerId = preferred
        _uiState.update {
            it.copy(
                vidSrcServerOrder = normalized,
                selectedVidSrcServerHost = preferred
            )
        }
    }

    fun toggleLike(videoId: String) {
        _uiState.update { current ->
            val liked = current.likedVideoIds.toMutableSet()
            val disliked = current.dislikedVideoIds.toMutableSet()
            if (liked.contains(videoId)) {
                liked.remove(videoId)
            } else {
                liked.add(videoId)
                disliked.remove(videoId)
            }
            settingsManager?.likedVideoIds = liked
            settingsManager?.dislikedVideoIds = disliked
            current.copy(likedVideoIds = liked, dislikedVideoIds = disliked)
        }
    }

    fun toggleDislike(videoId: String) {
        _uiState.update { current ->
            val liked = current.likedVideoIds.toMutableSet()
            val disliked = current.dislikedVideoIds.toMutableSet()
            if (disliked.contains(videoId)) {
                disliked.remove(videoId)
            } else {
                disliked.add(videoId)
                liked.remove(videoId)
            }
            settingsManager?.likedVideoIds = liked
            settingsManager?.dislikedVideoIds = disliked
            current.copy(likedVideoIds = liked, dislikedVideoIds = disliked)
        }
    }

    fun toggleSubscribe(channelName: String) {
        val canonicalName = TmdbRepository.getStudioChannelByName(channelName).name
        _uiState.update { current ->
            val subs = current.subscribedChannelNames.toMutableSet()
            val wasSubscribed = subs.contains(canonicalName)
            if (wasSubscribed) {
                subs.remove(canonicalName)
            } else {
                subs.add(canonicalName)
            }
            settingsManager?.subscribedChannelNames = subs

            val updatedSelectedChannel = if (current.selectedChannel?.name.equals(canonicalName, ignoreCase = true)) {
                current.selectedChannel?.copy(isSubscribed = !wasSubscribed)
            } else {
                current.selectedChannel
            }

            val updatedChannels = computeSubscribedChannels(subs)

            current.copy(
                subscribedChannelNames = subs,
                selectedChannel = updatedSelectedChannel,
                channels = updatedChannels
            )
        }
    }

    fun openChannel(channelName: String, avatarUrl: String? = null, handle: String? = null) {
        val channel = TmdbRepository.getStudioChannelByName(channelName, avatarUrl, handle).let {
            it.copy(isSubscribed = _uiState.value.subscribedChannelNames.contains(it.name))
        }

        _uiState.update { current ->
            current.copy(
                selectedChannel = channel,
                isChannelScreenOpen = true,
                // Opening a channel is navigation away from the Watch page;
                // keep the active media alive in the floating mini-player.
                isPlayerExpanded = false,
                isChannelLoading = true,
                channelVideos = emptyList()
            )
        }

        viewModelScope.launch {
            val cacheKey = "channel:${channel.name.trim().lowercase()}:page:1"
            val cached = localStore?.getCatalog(cacheKey)
            if (cached != null) {
                _uiState.update { current ->
                    if (current.selectedChannel?.name == channel.name) {
                        current.copy(
                            channelVideos = cached.videos.map(TmdbRepository::applyCachedChannelArtwork),
                            isChannelLoading = false
                        )
                    } else current
                }
            }
            if (_uiState.value.isOffline) return@launch
            val result = TmdbRepository.getChannelMedia(channelName)
            result.onSuccess { list ->
                localStore?.putCatalog(cacheKey, list)
                _uiState.update { current ->
                    if (current.selectedChannel?.name == channel.name) {
                        current.copy(channelVideos = list, isChannelLoading = false)
                    } else {
                        current
                    }
                }
            }.onFailure {
                _uiState.update { it.copy(isChannelLoading = false) }
            }
        }
    }

    fun openChannelFromItem(channel: ChannelItem) {
        openChannel(channel.name, channel.avatarUrl, channel.handle)
    }

    fun closeChannel() {
        _uiState.update {
            it.copy(
                isChannelScreenOpen = false,
                selectedChannel = null,
                channelVideos = emptyList(),
                isChannelLoading = false
            )
        }
    }

    fun toggleSave(videoId: String) {
        val current = _uiState.value
        val saved = current.savedVideoIds.toMutableSet()
        val order = current.savedVideoOrder.toMutableList()
        val isSaving = !saved.contains(videoId)

        if (isSaving) {
            saved.add(videoId)
            order.remove(videoId)
            order.add(videoId)
            val allKnown = (current.persistentWatchLaterVideos + current.videos + current.relatedVideos + current.savedVideos + current.channelVideos + current.queue + current.watchHistory.map { it.video } + listOfNotNull(current.currentPlayingVideo))
            val candidateVideo = allKnown.firstOrNull { it.id == videoId }
            viewModelScope.launch {
                candidateVideo?.let { localStore?.saveWatchLaterVideo(it, order.size) }
            }
            val updatedPersistent = if (candidateVideo != null) {
                current.persistentWatchLaterVideos.filter { it.id != videoId } + candidateVideo
            } else {
                current.persistentWatchLaterVideos
            }
            settingsManager?.savedVideoIds = saved
            settingsManager?.savedVideoOrder = order
            _uiState.update { it.copy(savedVideoIds = saved, savedVideoOrder = order, persistentWatchLaterVideos = updatedPersistent) }
        } else {
            saved.remove(videoId)
            order.remove(videoId)
            viewModelScope.launch {
                localStore?.removeWatchLaterVideo(videoId)
            }
            val updatedPersistent = current.persistentWatchLaterVideos.filter { it.id != videoId }
            settingsManager?.savedVideoIds = saved
            settingsManager?.savedVideoOrder = order
            _uiState.update { it.copy(savedVideoIds = saved, savedVideoOrder = order, persistentWatchLaterVideos = updatedPersistent) }
        }
    }

    fun toggleWatched(videoId: String) {
        val current = _uiState.value
        val watched = current.watchedVideoIds.toMutableSet()
        val isNowWatched = if (watched.contains(videoId)) {
            watched.remove(videoId)
            false
        } else {
            watched.add(videoId)
            true
        }
        settingsManager?.watchedVideoIds = watched

        // Synchronize with watch history single source of truth:
        val updatedHistory = current.watchHistory.map { entry ->
            if (entry.video.id == videoId) {
                entry.copy(
                    completed = isNowWatched,
                    positionSeconds = if (isNowWatched) {
                        if (entry.durationSeconds > 0) entry.durationSeconds else kotlin.math.max(entry.positionSeconds, 1L)
                    } else 0L
                )
            } else {
                entry
            }
        }
        saveHistoryNow(updatedHistory)

        _uiState.update { it.copy(watchedVideoIds = watched, watchHistory = updatedHistory) }
    }

    fun markNotInterested(videoId: String) {
        val updated = _uiState.value.notInterestedVideoIds + videoId
        settingsManager?.notInterestedVideoIds = updated
        _uiState.update { it.copy(notInterestedVideoIds = updated) }
    }

    fun blockRecommendedChannel(channelName: String) {
        val normalized = channelName.trim().lowercase().takeIf { it.isNotEmpty() } ?: return
        val updated = _uiState.value.notRecommendedChannelNames + normalized
        settingsManager?.notRecommendedChannelNames = updated
        _uiState.update { it.copy(notRecommendedChannelNames = updated) }
    }

    fun clearRecommendationPreferences() {
        settingsManager?.notInterestedVideoIds = emptySet()
        settingsManager?.notRecommendedChannelNames = emptySet()
        _uiState.update {
            it.copy(
                notInterestedVideoIds = emptySet(),
                notRecommendedChannelNames = emptySet()
            )
        }
    }

    fun openSharedVideo(
        videoId: String,
        title: String? = null,
        season: Int? = null,
        episode: Int? = null,
        notificationId: String? = null
    ) {
        val current = _uiState.value
        val knownVideo = (
            current.videos +
                current.relatedVideos +
                current.channelVideos +
                current.queue +
                current.watchHistory.map { it.video } +
                listOfNotNull(current.currentPlayingVideo)
            ).firstOrNull { it.id == videoId }

        val target = knownVideo?.let { video ->
            if (season != null && episode != null) {
                video.copy(
                    currentSeason = season.coerceAtLeast(1),
                    currentEpisode = episode.coerceAtLeast(1)
                )
            } else video
        }
        if (notificationId != null) markNotificationRead(notificationId)
        if (target != null) {
            playVideo(target, expand = true)
            return
        }

        val searchTerm = title?.trim()?.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            TmdbRepository.searchTmdb(searchTerm)
                .onSuccess { matches ->
                    val match = matches.firstOrNull { it.id == videoId } ?: matches.firstOrNull()
                    match?.let { video ->
                        val targetVideo = if (season != null && episode != null) {
                            video.copy(
                                currentSeason = season.coerceAtLeast(1),
                                currentEpisode = episode.coerceAtLeast(1)
                            )
                        } else video
                        playVideo(targetVideo, expand = true)
                    }
                }
        }
    }

    fun addToQueue(video: VideoItem, playNext: Boolean = false) {
        val current = _uiState.value
        val queueKey = video.playbackKey()
        if (current.currentPlayingVideo?.playbackKey() == queueKey ||
            current.queue.any { it.playbackKey() == queueKey }
        ) return

        val updatedQueue = if (playNext) {
            listOf(video) + current.queue
        } else {
            current.queue + video
        }
            .distinctBy { it.playbackKey() }
            .take(MAX_QUEUE_SIZE)
        settingsManager?.saveQueue(updatedQueue)
        _uiState.update { it.copy(queue = updatedQueue) }
    }

    fun removeFromQueue(queueKey: String) {
        val updatedQueue = _uiState.value.queue.filterNot { it.playbackKey() == queueKey }
        settingsManager?.saveQueue(updatedQueue)
        _uiState.update { it.copy(queue = updatedQueue) }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value
        if (fromIndex !in current.queue.indices || toIndex !in current.queue.indices || fromIndex == toIndex) return
        val reordered = current.queue.toMutableList().apply {
            add(toIndex, removeAt(fromIndex))
        }
        settingsManager?.saveQueue(reordered)
        _uiState.update { it.copy(queue = reordered) }
    }

    fun clearQueue() {
        settingsManager?.saveQueue(emptyList())
        _uiState.update { it.copy(queue = emptyList()) }
    }

    fun playQueuedVideo(video: VideoItem) {
        val queueKey = video.playbackKey()
        if (_uiState.value.queue.none { it.playbackKey() == queueKey }) return
        removeFromQueue(queueKey)
        playVideo(video, expand = true)
        setQueuePanelOpen(false)
    }

    fun setQueuePanelOpen(show: Boolean) {
        _uiState.update { it.copy(isQueuePanelOpen = show) }
    }

    fun clearLocalData() {
        settingsManager?.clearLocalData()
        viewModelScope.launch { localStore?.clearAll() }
        _uiState.update {
            it.copy(
                likedVideoIds = emptySet(),
                dislikedVideoIds = emptySet(),
                savedVideoIds = emptySet(),
                watchedVideoIds = emptySet(),
                notInterestedVideoIds = emptySet(),
                notRecommendedChannelNames = emptySet(),
                savedVideoOrder = emptyList(),
                watchHistory = emptyList(),
                queue = emptyList(),
                localProfileName = "Clutube",
                localProfileAvatar = "C",
                showContinueWatchingOnHome = false,
                releaseNotificationsEnabled = true,
                notifications = emptyList(),
                releaseAlerts = emptyList(),
                searchHistory = emptyList(),
                userNotificationCount = 0,
            )
        }
    }

    fun toggleTheme() {
        _uiState.update { current ->
            val newDark = !current.isDarkMode
            settingsManager?.isDarkMode = newDark
            current.copy(isDarkMode = newDark)
        }
    }

    fun setShowCommentsSheet(show: Boolean) {
        _uiState.update { it.copy(showCommentsSheet = show) }
    }

    fun setShowCreateSheet(show: Boolean) {
        _uiState.update { it.copy(showCreateSheet = show) }
    }

    fun setShowServerDialog(show: Boolean) {
        _uiState.update { it.copy(showServerDialog = show) }
    }

    fun setShowCastDialog(show: Boolean) {
        _uiState.update { it.copy(showCastDialog = show) }
    }

    fun setShowNotificationsSheet(show: Boolean) {
        _uiState.update { it.copy(showNotificationsSheet = show) }
    }

    fun refreshNotifications() {
        viewModelScope.launch {
            val store = localStore ?: return@launch
            val notifications = store.getNotifications()
            val alerts = store.getReleaseAlerts()
            _uiState.update {
                it.copy(
                    notifications = notifications,
                    releaseAlerts = alerts,
                    userNotificationCount = notifications.count { item ->
                        !item.isRead && !item.isDismissed
                    }
                )
            }
        }
    }

    fun markNotificationRead(id: String, isRead: Boolean = true) {
        viewModelScope.launch {
            localStore?.setNotificationRead(id, isRead)
            refreshNotifications()
        }
    }

    fun markAllNotificationsRead() {
        viewModelScope.launch {
            localStore?.markAllNotificationsRead()
            refreshNotifications()
        }
    }

    fun dismissNotification(id: String) {
        viewModelScope.launch {
            localStore?.dismissNotification(id)
            refreshNotifications()
        }
    }

    fun clearReadNotifications() {
        viewModelScope.launch {
            localStore?.clearReadNotifications()
            refreshNotifications()
        }
    }

    fun openNotification(notification: AppNotification) {
        markNotificationRead(notification.id)
        playVideo(notification.targetVideo, expand = true)
    }

    fun isReleaseAlertActive(video: VideoItem, season: Int? = null, episode: Int? = null): Boolean {
        val id = releaseAlertId(video, season, episode)
        return _uiState.value.releaseAlerts.any { it.id == id && !it.isDelivered }
    }

    fun toggleReleaseAlert(
        video: VideoItem,
        season: Int? = null,
        episode: Int? = null,
        releaseAtMillisOverride: Long? = null
    ) {
        val releaseAtMillis = releaseAtMillisOverride
            ?: releaseDateMillis(video.releaseDateIso ?: video.releaseDateFormatted)
            ?: return
        if (releaseAtMillis <= System.currentTimeMillis()) return

        val id = releaseAlertId(video, season, episode)
        val store = localStore ?: return
        val context = playbackContext ?: return
        viewModelScope.launch {
            val existing = store.getReleaseAlert(id)
            val feedback = if (existing != null && !existing.isDelivered) {
                store.removeReleaseAlert(id)
                ReleaseNotificationScheduler.cancelAlert(context, id)
                "Reminder removed for ${video.title}"
            } else if (existing == null) {
                val alert = ReleaseAlert(
                    id = id,
                    video = video.copy(
                        currentSeason = season ?: video.currentSeason,
                        currentEpisode = episode ?: video.currentEpisode
                    ),
                    releaseAtMillis = releaseAtMillis,
                    season = season,
                    episode = episode
                )
                store.putReleaseAlert(alert)
                ReleaseNotificationScheduler.scheduleAlert(context, alert)
                "Reminder set for ${video.title}"
            } else {
                null
            }
            refreshNotifications()
            if (feedback != null) {
                _uiState.update { it.copy(userFeedbackMessage = feedback) }
            }
        }
    }

    fun clearUserFeedbackMessage() {
        _uiState.update { it.copy(userFeedbackMessage = null) }
    }

    fun showFeedback(message: String) {
        _uiState.update { it.copy(userFeedbackMessage = message) }
    }

    fun setPlaybackQuality(quality: com.example.model.PlaybackQuality) {
        val current = _uiState.value.playbackPreferences
        val updated = current.copy(quality = quality)
        settingsManager?.savePlaybackPreferences(updated)
        _uiState.update { it.copy(playbackPreferences = updated) }
        com.example.util.PlayerViewManager.setQuality(quality)
    }

    fun setSubtitlePreference(subtitles: com.example.model.SubtitlePreference) {
        val current = _uiState.value.playbackPreferences
        val updated = current.copy(subtitles = subtitles)
        settingsManager?.savePlaybackPreferences(updated)
        _uiState.update { it.copy(playbackPreferences = updated) }
        com.example.util.PlayerViewManager.setSubtitles(subtitles)
    }

    fun addComment(text: String) {
        if (text.isBlank()) return
        val newComment = CommentItem(
            id = "c_${System.currentTimeMillis()}",
            author = "Alex (You)",
            avatarUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=100&auto=format&fit=crop&q=80",
            timeAgo = "Just now",
            text = text,
            likes = "1",
            isLiked = true
        )
        _uiState.update { it.copy(comments = listOf(newComment) + it.comments) }
    }

    fun likeComment(commentId: String) {
        _uiState.update { current ->
            val updated = current.comments.map {
                if (it.id == commentId) {
                    val count = (it.likes.toIntOrNull() ?: 10) + (if (it.isLiked) -1 else 1)
                    it.copy(isLiked = !it.isLiked, likes = count.toString())
                } else it
            }
            current.copy(comments = updated)
        }
    }

    fun search(query: String) {
        updateSearchQuery(query)
    }

    fun updateSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                isSearching = true,
                searchErrorMessage = null
            )
        }
        searchJob?.cancel()

        if (query.isBlank()) {
            _uiState.update {
                it.copy(
                    searchResults = emptyList(),
                    isSearchLoading = false,
                    isSearchCacheStale = false
                )
            }
            return
        }

        searchJob = viewModelScope.launch {
            delay(280)
            executeSearch(query.trim())
        }
    }

    fun submitSearch(query: String) {
        val normalized = query.trim()
        _uiState.update {
            it.copy(
                searchQuery = query,
                isSearching = true,
                searchErrorMessage = null
            )
        }
        searchJob?.cancel()
        if (normalized.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList(), isSearchLoading = false) }
            return
        }
        viewModelScope.launch {
            localStore?.recordSearch(normalized)
            val history = localStore?.getSearchHistory().orEmpty()
            _uiState.update { it.copy(searchHistory = history) }
        }
        searchJob = viewModelScope.launch { executeSearch(normalized) }
    }

    private suspend fun executeSearch(query: String) {
        if (query.isBlank()) return
        val cached = localStore?.getSearch(query)
        val cacheIsFresh = cached != null &&
            System.currentTimeMillis() - cached.updatedAtMillis <= SEARCH_CACHE_TTL_MILLIS
        if (cached != null) {
            _uiState.update {
                if (it.searchQuery.trim().equals(query, ignoreCase = true)) {
                    it.copy(
                        searchResults = cached.videos,
                        isSearchCacheStale = !cacheIsFresh,
                        isSearchLoading = !it.isOffline && !cacheIsFresh
                    )
                } else it
            }
        } else {
            _uiState.update {
                if (it.searchQuery.trim().equals(query, ignoreCase = true)) {
                    it.copy(isSearchLoading = !it.isOffline)
                } else it
            }
        }

        if (_uiState.value.isOffline) {
            _uiState.update {
                if (it.searchQuery.trim().equals(query, ignoreCase = true)) {
                    it.copy(
                        isSearchLoading = false,
                        searchErrorMessage = if (cached == null) {
                            "Search is unavailable offline. Try a saved search when you reconnect."
                        } else null
                    )
                } else it
            }
            return
        }

        val result = TmdbRepository.searchTmdb(query)
        result.onSuccess { list ->
            localStore?.putSearch(query, 1, list)
            _uiState.update {
                if (it.searchQuery.trim().equals(query, ignoreCase = true)) {
                    it.copy(
                        searchResults = list,
                        isSearchLoading = false,
                        searchErrorMessage = null,
                        isSearchCacheStale = false
                    )
                } else it
            }
        }.onFailure { error ->
            _uiState.update {
                if (it.searchQuery.trim().equals(query, ignoreCase = true)) {
                    it.copy(
                        isSearchLoading = false,
                        searchErrorMessage = if (cached != null) {
                            "Showing saved results. Pull to retry when connected."
                        } else {
                            error.message?.takeIf(String::isNotBlank) ?: "Search failed. Try again."
                        }
                    )
                } else it
            }
        }
    }

    fun removeSearchHistory(query: String) {
        viewModelScope.launch {
            localStore?.removeSearch(query)
            _uiState.update { it.copy(searchHistory = localStore?.getSearchHistory().orEmpty()) }
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch {
            localStore?.clearSearchHistory()
            _uiState.update { it.copy(searchHistory = emptyList()) }
        }
    }

    fun exitSearch() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                isSearching = false,
                searchQuery = "",
                searchResults = emptyList(),
                isSearchLoading = false,
                searchErrorMessage = null,
                isSearchCacheStale = false
            )
        }
    }

    fun streamCustomMedia(
        title: String,
        idOrQuery: String,
        isTv: Boolean = false,
        season: Int = 1,
        episode: Int = 1
    ) {
        val cleanId = idOrQuery.trim().ifEmpty { "157336" }
        val cleanTitle = title.trim().ifEmpty { "Cinema Stream: $cleanId" }
        val embedUrl = StreamService.buildEmbedUrl(
            mediaType = if (isTv) MediaType.TV_SHOW else MediaType.MOVIE,
            id = cleanId,
            season = season,
            episode = episode,
            serverId = _uiState.value.selectedServerId,
            vidSrcHost = _uiState.value.selectedVidSrcServerHost
        )

        val customVideo = VideoItem(
            id = "custom_${System.currentTimeMillis()}",
            title = "$cleanTitle [4K Stream]",
            description = "Streaming seamlessly in high fidelity.\nMedia ID: $cleanId\nType: ${if (isTv) "TV Series S$season:E$episode" else "Movie Feature"}",
            channelName = "Cinema Hub",
            channelAvatarUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=150&auto=format&fit=crop&q=80",
            views = "",
            publishedAt = "Premiered Today",
            duration = if (isTv) "TV SERIES" else "2:15:00",
            thumbnailUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=800&auto=format&fit=crop&q=80",
            streamUrl = "",
            embedStreamUrl = embedUrl,
            mediaType = if (isTv) MediaType.TV_SHOW else MediaType.MOVIE,
            tmdbId = cleanId,
            currentSeason = season,
            currentEpisode = episode,
            category = "Movies",
            tags = listOf("#MovieStream", "#MovieNight", "#Streaming", "#4K")
        )

        playVideo(customVideo, expand = true)
        _uiState.update { it.copy(showServerDialog = false, showCreateSheet = false) }
    }

    fun nextShort() {
        _uiState.update { current ->
            if (current.shorts.isEmpty()) return@update current
            val next = (current.currentShortIndex + 1) % current.shorts.size
            current.copy(currentShortIndex = next)
        }
    }

    fun previousShort() {
        _uiState.update { current ->
            if (current.shorts.isEmpty()) return@update current
            val prev = if (current.currentShortIndex > 0) current.currentShortIndex - 1 else current.shorts.size - 1
            current.copy(currentShortIndex = prev)
        }
    }

    fun selectShort(index: Int) {
        _uiState.update { current ->
            if (current.shorts.isEmpty()) current
            else current.copy(currentShortIndex = index.coerceIn(0, current.shorts.lastIndex))
        }
    }

    override fun onCleared() {
        flushPlaybackProgress()
        networkMonitor?.stop()
        pendingHistorySaveJob?.cancel()
        if (com.example.util.PlayerViewManager.onPlayerEvent != null) {
            com.example.util.PlayerViewManager.onPlayerEvent = null
        }
        com.example.util.PlayerViewManager.onVidSrcServerSelected = null
        com.example.util.PlayerViewManager.onVidSrcServerOrderChanged = null
        com.example.util.PlayerViewManager.releasePlayer()
        super.onCleared()
    }

    fun openDownloadsScreen() {
        _uiState.update { it.copy(showDownloadsScreen = true) }
    }

    fun closeDownloadsScreen() {
        _uiState.update { it.copy(showDownloadsScreen = false) }
    }

    fun requestDownloadMovie(video: VideoItem) {
        _uiState.update { it.copy(pendingDownloadTarget = com.example.ui.components.DownloadTarget.Movie(video)) }
    }

    fun requestDownloadEpisode(video: VideoItem, episode: TmdbEpisodeItem) {
        _uiState.update { it.copy(pendingDownloadTarget = com.example.ui.components.DownloadTarget.Episode(video, episode)) }
    }

    fun requestDownloadSeason(video: VideoItem, seasonNumber: Int, episodes: List<TmdbEpisodeItem>) {
        _uiState.update { it.copy(pendingDownloadTarget = com.example.ui.components.DownloadTarget.Season(video, seasonNumber, episodes)) }
    }

    fun dismissDownloadOptions() {
        _uiState.update { it.copy(pendingDownloadTarget = null) }
    }

    fun startConfiguredDownload(
        target: com.example.ui.components.DownloadTarget,
        server: String,
        quality: String,
        subtitleCc: String
    ) {
        _uiState.update { it.copy(pendingDownloadTarget = null) }
        when (target) {
            is com.example.ui.components.DownloadTarget.Movie -> {
                downloadManager?.downloadMovie(
                    video = target.video,
                    quality = quality,
                    server = server,
                    subtitleCc = subtitleCc
                )
                showFeedback("Queued '${target.video.title}' ($quality · $server)")
            }
            is com.example.ui.components.DownloadTarget.Episode -> {
                downloadManager?.downloadEpisode(
                    video = target.video,
                    episode = target.episode,
                    quality = quality,
                    server = server,
                    subtitleCc = subtitleCc
                )
                showFeedback("Queued S${target.episode.seasonNumber}:E${target.episode.episodeNumber} ($quality · $server)")
            }
            is com.example.ui.components.DownloadTarget.Season -> {
                downloadManager?.downloadSeason(
                    video = target.video,
                    seasonNumber = target.seasonNumber,
                    episodes = target.episodes,
                    quality = quality,
                    server = server,
                    subtitleCc = subtitleCc
                )
                val count = target.episodes.filter { it.seasonNumber == target.seasonNumber }.size
                showFeedback("Queued Season ${target.seasonNumber} ($count episodes · $quality · $server)")
            }
        }
    }

    fun startConfiguredTorrentDownload(
        target: com.example.ui.components.DownloadTarget,
        source: TorrentSource
    ) {
        _uiState.update { it.copy(pendingDownloadTarget = null, showTorrentSourceDialog = false) }
        when (target) {
            is com.example.ui.components.DownloadTarget.Movie -> {
                downloadManager?.downloadTorrent(
                    video = target.video,
                    source = source
                )
                showFeedback("Queued '${target.video.title}' (${source.quality} · ${source.provider})")
            }
            is com.example.ui.components.DownloadTarget.Episode -> {
                downloadManager?.downloadTorrent(
                    video = target.video,
                    source = source,
                    season = target.episode.seasonNumber,
                    episode = target.episode.episodeNumber
                )
                showFeedback("Queued S${target.episode.seasonNumber}:E${target.episode.episodeNumber} (${source.quality} · ${source.provider})")
            }
            is com.example.ui.components.DownloadTarget.Season -> {
                val seasonEpisodes = target.episodes.filter { it.seasonNumber == target.seasonNumber }
                seasonEpisodes.forEach { ep ->
                    downloadManager?.downloadTorrent(
                        video = target.video,
                        source = source.copy(season = target.seasonNumber, episode = ep.episodeNumber),
                        season = target.seasonNumber,
                        episode = ep.episodeNumber
                    )
                }
                showFeedback("Queued Season ${target.seasonNumber} (${seasonEpisodes.size} episodes · ${source.quality})")
            }
        }
    }

    fun setShowAddMagnetDialog(show: Boolean) {
        _uiState.update { it.copy(showAddMagnetDialog = show) }
    }

    fun setShowTorrentSourceDialog(show: Boolean) {
        _uiState.update { it.copy(showTorrentSourceDialog = show) }
    }

    fun addCustomMagnet(magnetUri: String, customTitle: String? = null, directDownloadUrl: String? = null) {
        downloadManager?.downloadMagnet(magnetUri, customTitle, directDownloadUrl)
        showFeedback("Torrent download queued")
        _uiState.update { it.copy(showAddMagnetDialog = false) }
    }

    fun openTorrentSourcesForMedia(video: VideoItem, season: Int? = null, episode: Int? = null) {
        _uiState.update {
            it.copy(
                selectedTorrentMedia = video,
                selectedTorrentSeason = season,
                selectedTorrentEpisode = episode,
                showTorrentSourceDialog = true,
                isLoadingTorrentSources = true,
                torrentSources = emptyList()
            )
        }
        loadTorrentSources(video, season, episode)
    }

    fun loadTorrentSources(video: VideoItem, season: Int? = null, episode: Int? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingTorrentSources = true) }
            val year = video.releaseDateFormatted?.take(4)
            val sources = if (video.mediaType == MediaType.TV_SHOW) {
                TorrentIndexerService.resolveTvTorrents(
                    imdbId = video.imdbId ?: video.tmdbId,
                    showTitle = video.title,
                    seasonNumber = season,
                    episodeNumber = episode
                )
            } else {
                TorrentIndexerService.resolveMovieTorrents(
                    imdbId = video.imdbId ?: video.tmdbId,
                    title = video.title,
                    year = year
                )
            }
            _uiState.update {
                it.copy(
                    torrentSources = sources,
                    isLoadingTorrentSources = false
                )
            }
        }
    }

    fun downloadTorrentSource(source: TorrentSource, video: VideoItem, season: Int? = null, episode: Int? = null) {
        downloadManager?.downloadTorrent(video, source, season, episode)
        showFeedback("Queued '${source.title}' via ${source.provider}")
        _uiState.update { it.copy(showTorrentSourceDialog = false) }
    }

    fun downloadMovie(video: VideoItem) {
        requestDownloadMovie(video)
    }

    fun downloadEpisode(video: VideoItem, episode: TmdbEpisodeItem) {
        requestDownloadEpisode(video, episode)
    }

    fun downloadSeason(video: VideoItem, seasonNumber: Int, episodes: List<TmdbEpisodeItem>) {
        requestDownloadSeason(video, seasonNumber, episodes)
    }

    /**
     * Triggered from 3-dot overflow menu on any video or show card.
     * For TV shows, seamlessly targets the exact episode where the user arrived or is watching.
     */
    fun downloadVideoFromMenu(video: VideoItem) {
        if (video.mediaType == MediaType.TV_SHOW) {
            val historyEntry = _uiState.value.watchHistory.firstOrNull {
                it.video.id == video.id || (it.video.tmdbId != null && it.video.tmdbId == video.tmdbId)
            }
            val targetSeason = historyEntry?.video?.currentSeason ?: video.currentSeason.coerceAtLeast(1)
            val targetEpisode = historyEntry?.video?.currentEpisode ?: video.currentEpisode.coerceAtLeast(1)

            val matchingEpisode = _uiState.value.tvEpisodes.firstOrNull {
                it.seasonNumber == targetSeason && it.episodeNumber == targetEpisode
            } ?: TmdbEpisodeItem(
                id = (video.tmdbId?.hashCode() ?: 1) * 1000 + targetSeason * 100 + targetEpisode,
                name = "Episode $targetEpisode",
                overview = "",
                seasonNumber = targetSeason,
                episodeNumber = targetEpisode,
                airDate = "",
                stillPath = null,
                runtime = video.runtimeMinutes
            )

            requestDownloadEpisode(video, matchingEpisode)
        } else {
            requestDownloadMovie(video)
        }
    }

    fun pauseDownload(id: String) {
        downloadManager?.pauseDownload(id)
    }

    fun resumeDownload(id: String) {
        downloadManager?.resumeDownload(id)
    }

    fun retryDownload(id: String) {
        downloadManager?.retryDownload(id)
    }

    fun cancelDownload(id: String) {
        downloadManager?.cancelDownload(id)
    }

    fun deleteDownload(id: String) {
        downloadManager?.deleteDownload(id)
        showFeedback("Download deleted")
    }

    fun pauseAllDownloads() {
        downloadManager?.pauseAll()
    }

    fun resumeAllDownloads() {
        downloadManager?.resumeAll()
    }

    fun clearAllDownloads() {
        downloadManager?.clearCompleted()
        showFeedback("Cleared completed downloads")
    }

    private companion object {
        const val HISTORY_LIMIT = 50
        const val HISTORY_SAVE_INTERVAL_MILLIS = 10_000L
        const val COMPLETION_REMAINING_SECONDS = 15L
        const val MIN_RELIABLE_DURATION_SECONDS = 30L
        const val RESUME_RESET_TOLERANCE_SECONDS = 2L
        const val SEARCH_CACHE_TTL_MILLIS = 7L * 24L * 60L * 60L * 1000L
        const val MAX_QUEUE_SIZE = 50
    }
}

private fun VideoItem.withStreamUrl(
    serverId: String,
    vidSrcHost: String = StreamService.DEFAULT_VIDSRC_SERVER_HOST
): VideoItem = copy(
    embedStreamUrl = StreamService.buildEmbedUrl(
        mediaType = mediaType,
        id = tmdbId ?: id,
        season = currentSeason,
        episode = currentEpisode,
        serverId = serverId,
        vidSrcHost = vidSrcHost
    )
)

private fun isProfileImageReference(value: String): Boolean {
    val normalized = value.trim().lowercase()
    return normalized.startsWith("content://") ||
        normalized.startsWith("file://") ||
        normalized.startsWith("android.resource://") ||
        normalized.startsWith("data:image/")
}

private fun recentHistoryGroupKey(entry: WatchHistoryEntry): String {
    val video = entry.video
    val contentId = listOf(video.tmdbId, video.imdbId, video.id)
        .firstOrNull { !it.isNullOrBlank() }
        ?.trim()
        ?.lowercase()
        .orEmpty()
        .ifBlank { entry.key }
    return "${video.mediaType.name.lowercase()}:$contentId"
}
