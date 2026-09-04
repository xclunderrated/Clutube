package com.example.data.local

import android.content.Context
import com.example.model.AppNotification
import com.example.model.NotificationKind
import com.example.model.ReleaseAlert
import com.example.model.SearchHistoryItem
import com.example.model.VideoItem
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class LocalStore(context: Context) {
    private val dao = LocalDatabase.get(context).cacheDao()
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val videoAdapter = moshi.adapter(VideoItem::class.java)
    private val videoListAdapter = moshi.adapter<List<VideoItem>>(
        Types.newParameterizedType(List::class.java, VideoItem::class.java)
    )

    suspend fun getCatalog(key: String): CachedList? = runCatching {
        dao.getCatalog(key)?.let { entity ->
            CachedList(videoListAdapter.fromJson(entity.payloadJson).orEmpty(), entity.updatedAtMillis)
        }
    }.getOrNull()

    suspend fun putCatalog(key: String, videos: List<VideoItem>, now: Long = System.currentTimeMillis()) {
        runCatching {
            dao.putCatalog(
                CatalogCacheEntity(
                    cacheKey = key,
                    payloadJson = videoListAdapter.toJson(videos),
                    updatedAtMillis = now
                )
            )
        }
    }

    suspend fun getSearch(query: String, page: Int = 1): CachedList? = runCatching {
        dao.getSearch(searchKey(query, page))?.let { entity ->
            CachedList(videoListAdapter.fromJson(entity.payloadJson).orEmpty(), entity.updatedAtMillis)
        }
    }.getOrNull()

    suspend fun putSearch(
        query: String,
        page: Int,
        videos: List<VideoItem>,
        now: Long = System.currentTimeMillis()
    ) {
        runCatching {
            dao.putSearch(
                SearchCacheEntity(
                    cacheKey = searchKey(query, page),
                    payloadJson = videoListAdapter.toJson(videos),
                    updatedAtMillis = now
                )
            )
        }
    }

    suspend fun getSearchHistory(limit: Int = 20): List<SearchHistoryItem> = runCatching {
        dao.getSearchHistory(limit.coerceIn(1, 50)).map {
            SearchHistoryItem(it.displayQuery, it.lastUsedAtMillis)
        }
    }.getOrDefault(emptyList())

    suspend fun recordSearch(query: String, now: Long = System.currentTimeMillis()) {
        val display = query.trim()
        val normalized = normalizeQuery(display)
        if (normalized.isBlank()) return
        runCatching {
            dao.putSearchHistory(
                SearchHistoryEntity(
                    normalizedQuery = normalized,
                    displayQuery = display,
                    lastUsedAtMillis = now
                )
            )
        }
    }

    suspend fun removeSearch(query: String) {
        runCatching { dao.deleteSearchHistory(normalizeQuery(query)) }
    }

    suspend fun clearSearchHistory() {
        runCatching { dao.clearSearchHistory() }
    }

    suspend fun getReleaseAlerts(): List<ReleaseAlert> = runCatching {
        dao.getReleaseAlerts().mapNotNull { entity ->
            val video = videoAdapter.fromJson(entity.videoJson) ?: return@mapNotNull null
            ReleaseAlert(
                id = entity.id,
                video = video,
                releaseAtMillis = entity.releaseAtMillis,
                kind = runCatching { NotificationKind.valueOf(entity.kind) }
                    .getOrDefault(NotificationKind.RELEASE_ALERT),
                season = entity.season,
                episode = entity.episode,
                createdAtMillis = entity.createdAtMillis,
                deliveredAtMillis = entity.deliveredAtMillis
            )
        }
    }.getOrDefault(emptyList())

    suspend fun getReleaseAlert(id: String): ReleaseAlert? = getReleaseAlerts().firstOrNull { it.id == id }

    suspend fun putReleaseAlert(alert: ReleaseAlert) {
        runCatching {
            dao.putReleaseAlert(
                ReleaseAlertEntity(
                    id = alert.id,
                    videoJson = videoAdapter.toJson(alert.video),
                    releaseAtMillis = alert.releaseAtMillis,
                    kind = alert.kind.name,
                    season = alert.season,
                    episode = alert.episode,
                    createdAtMillis = alert.createdAtMillis,
                    deliveredAtMillis = alert.deliveredAtMillis
                )
            )
        }
    }

    suspend fun removeReleaseAlert(id: String) {
        runCatching { dao.deleteReleaseAlert(id) }
    }

    suspend fun markReleaseAlertDelivered(id: String, deliveredAt: Long = System.currentTimeMillis()) {
        runCatching { dao.markReleaseAlertDelivered(id, deliveredAt) }
    }

    suspend fun getNotifications(): List<AppNotification> = runCatching {
        dao.getNotifications().mapNotNull { entity ->
            val video = videoAdapter.fromJson(entity.videoJson) ?: return@mapNotNull null
            val kind = runCatching { NotificationKind.valueOf(entity.kind) }
                .getOrDefault(NotificationKind.RELEASE_ALERT)
            AppNotification(
                id = entity.id,
                kind = kind,
                title = entity.title,
                message = entity.message,
                video = video,
                createdAtMillis = entity.createdAtMillis,
                releaseAtMillis = entity.releaseAtMillis,
                season = entity.season,
                episode = entity.episode,
                isRead = entity.isRead,
                isDismissed = entity.isDismissed
            )
        }
    }.getOrDefault(emptyList())

    suspend fun hasNotification(id: String): Boolean = runCatching { dao.getNotification(id) != null }.getOrDefault(false)

    suspend fun putNotification(notification: AppNotification) {
        runCatching {
            dao.putNotification(
                AppNotificationEntity(
                    id = notification.id,
                    kind = notification.kind.name,
                    title = notification.title,
                    message = notification.message,
                    videoJson = videoAdapter.toJson(notification.video),
                    createdAtMillis = notification.createdAtMillis,
                    releaseAtMillis = notification.releaseAtMillis,
                    season = notification.season,
                    episode = notification.episode,
                    isRead = notification.isRead,
                    isDismissed = notification.isDismissed
                )
            )
        }
    }

    suspend fun setNotificationRead(id: String, isRead: Boolean) {
        runCatching { dao.setNotificationRead(id, isRead) }
    }

    suspend fun markAllNotificationsRead() {
        runCatching { dao.markAllNotificationsRead() }
    }

    suspend fun dismissNotification(id: String) {
        runCatching { dao.dismissNotification(id) }
    }

    suspend fun clearReadNotifications() {
        runCatching { dao.clearReadNotifications() }
    }

    suspend fun unreadNotificationCount(): Int = runCatching { dao.unreadNotificationCount() }.getOrDefault(0)

    suspend fun getWatchLaterVideos(): List<VideoItem> = runCatching {
        dao.getWatchLaterItems().map { entity ->
            val mediaType = runCatching { com.example.model.MediaType.valueOf(entity.mediaType) }
                .getOrDefault(com.example.model.MediaType.MOVIE)
            VideoItem(
                id = entity.id,
                title = entity.title,
                description = "",
                channelName = entity.channelName,
                channelAvatarUrl = entity.channelAvatarUrl.orEmpty(),
                publishedAt = entity.releaseDateFormatted.orEmpty(),
                duration = entity.duration.orEmpty(),
                thumbnailUrl = entity.thumbnailUrl ?: entity.posterUrl ?: entity.backdropUrl.orEmpty(),
                posterUrl = entity.posterUrl,
                backdropUrl = entity.backdropUrl,
                mediaType = mediaType,
                tmdbId = entity.tmdbId,
                currentSeason = entity.season,
                currentEpisode = entity.episode,
                rating = entity.rating,
                releaseDateFormatted = entity.releaseDateFormatted,
                isSaved = true
            )
        }
    }.getOrDefault(emptyList())

    suspend fun saveWatchLaterVideo(video: VideoItem, orderIndex: Int = 0) {
        runCatching {
            dao.putWatchLaterItem(
                WatchLaterEntity(
                    id = video.id,
                    tmdbId = video.tmdbId,
                    mediaType = video.mediaType.name,
                    season = video.currentSeason,
                    episode = video.currentEpisode,
                    title = video.title,
                    posterUrl = video.posterUrl,
                    backdropUrl = video.backdropUrl,
                    thumbnailUrl = video.thumbnailUrl,
                    channelName = video.channelName,
                    channelAvatarUrl = video.channelAvatarUrl,
                    rating = video.rating,
                    duration = video.duration,
                    releaseDateFormatted = video.releaseDateFormatted,
                    addedAtMillis = System.currentTimeMillis(),
                    orderIndex = orderIndex
                )
            )
        }
    }

    suspend fun removeWatchLaterVideo(id: String) {
        runCatching { dao.deleteWatchLaterItem(id) }
    }

    suspend fun clearWatchLater() {
        runCatching { dao.clearWatchLater() }
    }

    suspend fun clearAll() {
        runCatching {
            dao.clearCatalog()
            dao.clearSearchCache()
            dao.clearAllSearchHistory()
            dao.clearReleaseAlerts()
            dao.clearAllNotifications()
            dao.clearWatchLater()
        }
    }

    data class CachedList(val videos: List<VideoItem>, val updatedAtMillis: Long)

    companion object {
        fun normalizeQuery(query: String): String = query.trim().replace(Regex("\\s+"), " ").lowercase()

        private fun searchKey(query: String, page: Int): String =
            "search:${normalizeQuery(query)}:page:${page.coerceAtLeast(1)}"
    }
}
