package com.example.data

import android.content.Context
import android.content.SharedPreferences
import com.example.model.WatchHistoryEntry
import com.example.model.VideoItem
import com.example.model.PlaybackPreferences
import com.example.model.PlaybackQuality
import com.example.model.SubtitlePreference
import com.example.model.playbackKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlin.math.max

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val videoListType = Types.newParameterizedType(List::class.java, VideoItem::class.java)
    private val videoListAdapter = moshi.adapter<List<VideoItem>>(videoListType)
    private val historyEntryListType = Types.newParameterizedType(List::class.java, WatchHistoryEntry::class.java)
    private val historyEntryListAdapter = moshi.adapter<List<WatchHistoryEntry>>(historyEntryListType)

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var selectedServerId: String
        get() {
            val saved = prefs.getString(KEY_SELECTED_SERVER, null)
            return saved?.takeIf { id -> StreamService.AVAILABLE_SERVERS.any { it.id == id } }
                ?: StreamService.DEFAULT_SERVER_ID
        }
        set(value) = prefs.edit().putString(KEY_SELECTED_SERVER, value).apply()

    var vidSrcServerOrder: List<String>
        get() {
            val saved = prefs.getString(KEY_VIDSRC_SERVER_ORDER, null)
                ?.split(SERVER_ORDER_SEPARATOR)
                .orEmpty()
            return StreamService.normalizeVidSrcServerOrder(saved)
        }
        set(value) {
            val normalized = StreamService.normalizeVidSrcServerOrder(value)
            prefs.edit()
                .putString(KEY_VIDSRC_SERVER_ORDER, normalized.joinToString(SERVER_ORDER_SEPARATOR))
                .apply()
        }

    var selectedVidSrcServerId: String
        get() {
            val saved = prefs.getString(KEY_SELECTED_VIDSRC_SERVER, null)
            return saved?.takeIf(StreamService::isVidSrcServerHost)
                ?: vidSrcServerOrder.firstOrNull()
                ?: StreamService.DEFAULT_VIDSRC_SERVER_HOST
        }
        set(value) = prefs.edit()
            .putString(KEY_SELECTED_VIDSRC_SERVER, StreamService.normalizeVidSrcServerHost(value))
            .apply()

    var isAutoNextEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_NEXT_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_NEXT_ENABLED, value).apply()

    var releaseNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_RELEASE_NOTIFICATIONS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_RELEASE_NOTIFICATIONS_ENABLED, value).apply()

    fun getPlaybackPreferences(serverId: String): PlaybackPreferences {
        val suffix = preferenceKeySuffix(serverId)
        val quality = prefs.getString(KEY_GLOBAL_PLAYBACK_QUALITY, null)
            ?.let { value -> runCatching { PlaybackQuality.valueOf(value) }.getOrNull() }
            ?: prefs.getString(KEY_PLAYBACK_QUALITY + suffix, null)
            ?.let { value -> runCatching { PlaybackQuality.valueOf(value) }.getOrNull() }
            ?: PlaybackQuality.AUTO
        val subtitles = prefs.getString(KEY_GLOBAL_PLAYBACK_SUBTITLES, null)
            ?.let { value -> runCatching { SubtitlePreference.valueOf(value) }.getOrNull() }
            ?: prefs.getString(KEY_PLAYBACK_SUBTITLES + suffix, null)
            ?.let { value -> runCatching { SubtitlePreference.valueOf(value) }.getOrNull() }
            ?: SubtitlePreference.OFF
        return PlaybackPreferences(quality = quality, subtitles = subtitles)
    }

    /** Account-level defaults shared by VidSrc, VidLink, and future providers. */
    fun getPlaybackPreferences(): PlaybackPreferences = getPlaybackPreferences(selectedServerId)

    fun savePlaybackPreferences(preferences: PlaybackPreferences) {
        prefs.edit()
            .putString(KEY_GLOBAL_PLAYBACK_QUALITY, preferences.quality.name)
            .putString(KEY_GLOBAL_PLAYBACK_SUBTITLES, preferences.subtitles.name)
            .apply()
    }

    fun savePlaybackPreferences(serverId: String, preferences: PlaybackPreferences) {
        val suffix = preferenceKeySuffix(serverId)
        prefs.edit()
            .putString(KEY_PLAYBACK_QUALITY + suffix, preferences.quality.name)
            .putString(KEY_PLAYBACK_SUBTITLES + suffix, preferences.subtitles.name)
            .apply()
    }

    var showContinueWatchingOnHome: Boolean
        get() = prefs.getBoolean(KEY_SHOW_CONTINUE_WATCHING_ON_HOME, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_CONTINUE_WATCHING_ON_HOME, value).apply()

    var localProfileName: String
        get() = prefs.getString(KEY_LOCAL_PROFILE_NAME, "Clutube")?.trim().orEmpty().ifBlank { "Clutube" }
        set(value) = prefs.edit().putString(KEY_LOCAL_PROFILE_NAME, value.trim().ifBlank { "Clutube" }).apply()

    var localProfileAvatar: String
        get() = prefs.getString(KEY_LOCAL_PROFILE_AVATAR, "C")?.trim().orEmpty().ifBlank { "C" }
        set(value) {
            val normalized = value.trim()
            val stored = if (isProfileImageReference(normalized)) {
                normalized
            } else {
                normalized.take(2).ifBlank { "C" }
            }
            prefs.edit().putString(KEY_LOCAL_PROFILE_AVATAR, stored).apply()
        }

    var likedVideoIds: Set<String>
        get() = prefs.getStringSet(KEY_LIKED_VIDEOS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_LIKED_VIDEOS, value).apply()

    var dislikedVideoIds: Set<String>
        get() = prefs.getStringSet(KEY_DISLIKED_VIDEOS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_DISLIKED_VIDEOS, value).apply()

    var savedVideoIds: Set<String>
        get() = prefs.getStringSet(KEY_SAVED_VIDEOS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_SAVED_VIDEOS, value).apply()

    var watchedVideoIds: Set<String>
        get() = prefs.getStringSet(KEY_WATCHED_VIDEOS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_WATCHED_VIDEOS, value).apply()

    var notInterestedVideoIds: Set<String>
        get() = prefs.getStringSet(KEY_NOT_INTERESTED_VIDEOS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_NOT_INTERESTED_VIDEOS, value).apply()

    var notRecommendedChannelNames: Set<String>
        get() = prefs.getStringSet(KEY_NOT_RECOMMENDED_CHANNELS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(KEY_NOT_RECOMMENDED_CHANNELS, value).apply()

    /** Keeps Watch Later's insertion order because SharedPreferences string sets are unordered. */
    var savedVideoOrder: List<String>
        get() = prefs.getString(KEY_SAVED_VIDEO_ORDER, null)
            ?.split(SAVED_VIDEO_ORDER_SEPARATOR)
            ?.filter(String::isNotBlank)
            .orEmpty()
        set(value) = prefs.edit()
            .putString(KEY_SAVED_VIDEO_ORDER, value.filter(String::isNotBlank).distinct().joinToString(SAVED_VIDEO_ORDER_SEPARATOR))
            .apply()

    var subscribedChannelNames: Set<String>
        get() = prefs.getStringSet(KEY_SUBSCRIBED_CHANNELS, setOf("Warner Bros. Pictures", "Netflix", "Marvel Studios"))
            ?: setOf("Warner Bros. Pictures", "Netflix", "Marvel Studios")
        set(value) = prefs.edit().putStringSet(KEY_SUBSCRIBED_CHANNELS, value).apply()

    fun getWatchHistory(): List<VideoItem> {
        return getWatchHistoryEntries().map { it.video }
    }

    fun saveWatchHistory(history: List<VideoItem>) {
        val now = System.currentTimeMillis()
        saveWatchHistoryEntries(
            history.mapIndexed { index, video ->
                WatchHistoryEntry(
                    key = video.playbackKey(),
                    video = video,
                    lastWatchedAtMillis = max(0L, now - index)
                )
            }
        )
    }

    /**
     * Reads the current history format and migrates the original
     * watch_history_json list the first time it is encountered.
     */
    fun getWatchHistoryEntries(): List<WatchHistoryEntry> {
        val currentJson = prefs.getString(KEY_WATCH_HISTORY_ENTRIES, null)
        if (currentJson != null) {
            parseHistoryEntries(currentJson)?.let { return sanitizeHistory(it) }
        }

        val legacyJson = prefs.getString(KEY_WATCH_HISTORY, null) ?: return emptyList()
        val legacy = try {
            videoListAdapter.fromJson(legacyJson).orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        if (legacy.isEmpty()) return emptyList()

        val migrated = sanitizeHistory(
            legacy.mapIndexed { index, video ->
                WatchHistoryEntry(
                    key = video.playbackKey(),
                    video = video,
                    lastWatchedAtMillis = max(0L, System.currentTimeMillis() - index)
                )
            }
        )
        saveWatchHistoryEntries(migrated)
        return migrated
    }

    fun saveWatchHistoryEntries(history: List<WatchHistoryEntry>) {
        try {
            val json = history
                .map { it.normalized() }
                .distinctBy { it.key }
                .take(50)
                .let(historyEntryListAdapter::toJson)
            prefs.edit().putString(KEY_WATCH_HISTORY_ENTRIES, json).apply()
        } catch (_: Exception) {
            // Persistence must never prevent playback from continuing.
        }
    }

    fun removeWatchHistoryEntry(key: String) {
        saveWatchHistoryEntries(getWatchHistoryEntries().filterNot { it.key == key })
    }

    fun clearWatchHistory() {
        prefs.edit()
            .remove(KEY_WATCH_HISTORY_ENTRIES)
            .remove(KEY_WATCH_HISTORY)
            .apply()
    }

    fun getQueue(): List<VideoItem> {
        val json = prefs.getString(KEY_QUEUE_JSON, null) ?: return emptyList()
        return try {
            sanitizeQueue(videoListAdapter.fromJson(json).orEmpty())
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveQueue(queue: List<VideoItem>) {
        try {
            prefs.edit()
                .putString(KEY_QUEUE_JSON, videoListAdapter.toJson(sanitizeQueue(queue)))
                .apply()
        } catch (_: Exception) {
            // Queue persistence must never interrupt playback.
        }
    }

    private fun sanitizeQueue(queue: List<VideoItem>): List<VideoItem> = queue
        .distinctBy { it.playbackKey() }
        .take(MAX_QUEUE_SIZE)

    fun clearLocalData() {
        prefs.edit().clear().apply()
    }

    private fun parseHistoryEntries(json: String): List<WatchHistoryEntry>? {
        return try {
            historyEntryListAdapter.fromJson(json)
        } catch (_: Exception) {
            null
        }
    }

    private fun sanitizeHistory(history: List<WatchHistoryEntry>): List<WatchHistoryEntry> {
        return history
            .map { it.normalized() }
            .distinctBy { it.key }
            .take(50)
    }

    companion object {
        private const val PREFS_NAME = "clutube_app_preferences"
        private const val KEY_DARK_MODE = "is_dark_mode"
        private const val KEY_SELECTED_SERVER = "selected_server_id"
        private const val KEY_VIDSRC_SERVER_ORDER = "vidsrc_server_order"
        private const val KEY_SELECTED_VIDSRC_SERVER = "selected_vidsrc_server"
        private const val SERVER_ORDER_SEPARATOR = "|"
        private const val KEY_AUTO_NEXT_ENABLED = "auto_next_enabled"
        private const val KEY_RELEASE_NOTIFICATIONS_ENABLED = "release_notifications_enabled"
        private const val KEY_GLOBAL_PLAYBACK_QUALITY = "playback_quality_global"
        private const val KEY_GLOBAL_PLAYBACK_SUBTITLES = "playback_subtitles_global"
        private const val KEY_PLAYBACK_QUALITY = "playback_quality"
        private const val KEY_PLAYBACK_SUBTITLES = "playback_subtitles"
        private const val KEY_SHOW_CONTINUE_WATCHING_ON_HOME = "show_continue_watching_on_home"
        private const val KEY_LOCAL_PROFILE_NAME = "local_profile_name"
        private const val KEY_LOCAL_PROFILE_AVATAR = "local_profile_avatar"
        private const val KEY_LIKED_VIDEOS = "liked_video_ids"
        private const val KEY_DISLIKED_VIDEOS = "disliked_video_ids"
        private const val KEY_SAVED_VIDEOS = "saved_video_ids"
        private const val KEY_WATCHED_VIDEOS = "watched_video_ids"
        private const val KEY_NOT_INTERESTED_VIDEOS = "not_interested_video_ids"
        private const val KEY_NOT_RECOMMENDED_CHANNELS = "not_recommended_channel_names"
        private const val KEY_SAVED_VIDEO_ORDER = "saved_video_order"
        private const val KEY_QUEUE_JSON = "playback_queue_json"
        private const val KEY_SUBSCRIBED_CHANNELS = "subscribed_channel_names"
        private const val KEY_WATCH_HISTORY = "watch_history_json"
        private const val KEY_WATCH_HISTORY_ENTRIES = "watch_history_entries_json"
        private const val SAVED_VIDEO_ORDER_SEPARATOR = "|"
        private const val MAX_QUEUE_SIZE = 50
    }

    private fun isProfileImageReference(value: String): Boolean {
        val normalized = value.trim().lowercase()
        return normalized.startsWith("content://") ||
            normalized.startsWith("file://") ||
            normalized.startsWith("android.resource://") ||
            normalized.startsWith("data:image/")
    }

    private fun preferenceKeySuffix(serverId: String): String =
        ":" + serverId.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "_")
}
