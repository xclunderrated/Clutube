package com.example.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.SettingsManager
import com.example.data.local.LocalStore
import com.example.data.tmdb.TmdbRepository
import com.example.model.AppNotification
import com.example.model.NotificationKind
import com.example.model.ReleaseAlert
import com.example.model.VideoItem
import com.example.model.isUnreleased
import com.example.model.releaseAlertId
import com.example.model.releaseDateMillis

class ReleaseNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val store = LocalStore(context)
        return try {
            deliverDueAlerts(context, store)
            if (ReleaseNotificationScheduler.alertId(inputData) == null) {
                syncSubscriptions(store)
                syncWatchedShows(store)
                deliverDueAlerts(context, store)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun deliverDueAlerts(context: Context, store: LocalStore) {
        val now = System.currentTimeMillis()
        store.getReleaseAlerts()
            .filter { !it.isDelivered && it.releaseAtMillis <= now }
            .forEach { alert ->
                val target = alert.targetVideo
                val notificationId = "notification:${alert.id}"
                val notification = AppNotification(
                    id = notificationId,
                    kind = alert.kind,
                    title = when (alert.kind) {
                        NotificationKind.WATCHED_SHOW_EPISODE -> "New episode available"
                        else -> "Now available"
                    },
                    message = if (alert.episode != null) {
                        "${target.title} is ready to watch."
                    } else {
                        "${target.title} is now available."
                    },
                    video = target,
                    releaseAtMillis = alert.releaseAtMillis,
                    season = alert.season,
                    episode = alert.episode
                )
                if (!store.hasNotification(notificationId)) {
                    store.putNotification(notification)
                    ReleaseNotificationPublisher.publish(context, notification)
                }
                store.markReleaseAlertDelivered(alert.id, now)
            }
    }

    private suspend fun syncSubscriptions(store: LocalStore) {
        val settings = SettingsManager(applicationContext)
        settings.subscribedChannelNames.forEach { channelName ->
            val key = "subscription_seen:${channelName.trim().lowercase()}"
            val current = TmdbRepository.getChannelMedia(channelName).getOrElse { return@forEach }
                .filterNot { isUnreleased(it.releaseDateIso ?: it.releaseDateFormatted) }
            val previous = store.getCatalog(key)?.videos
            if (previous == null) {
                store.putCatalog(key, current)
                return@forEach
            }
            val previousIds = previous.map { it.id }.toSet()
            current.filterNot { it.id in previousIds }.forEach { video ->
                if (settings.watchedVideoIds.none { it == video.id }) {
                    val notification = AppNotification(
                        id = "subscription:${channelName.trim().lowercase()}:${video.id}",
                        kind = NotificationKind.SUBSCRIPTION_RELEASE,
                        title = "New from ${video.channelName}",
                        message = video.title,
                        video = video
                    )
                    if (!store.hasNotification(notification.id)) {
                        store.putNotification(notification)
                        ReleaseNotificationPublisher.publish(applicationContext, notification)
                    }
                }
            }
            store.putCatalog(key, current)
        }
    }

    private suspend fun syncWatchedShows(store: LocalStore) {
        val settings = SettingsManager(applicationContext)
        val shows = settings.getWatchHistoryEntries()
            .map { it.video }
            .filter { it.mediaType == com.example.model.MediaType.TV_SHOW && !it.tmdbId.isNullOrBlank() }
            .distinctBy { "${it.tmdbId}:${it.currentSeason}" }
            .take(20)
        shows.forEach { show ->
            val tmdbId = show.tmdbId?.toIntOrNull() ?: return@forEach
            val episodes = TmdbRepository.getTvEpisodes(tmdbId, show.currentSeason).getOrElse { return@forEach }
            episodes.filter { episode ->
                !episode.airDate.isNullOrBlank() &&
                    isUnreleased(episode.airDate) &&
                    episode.episodeNumber > show.currentEpisode
            }.forEach { episode ->
                val releaseAt = releaseDateMillis(episode.airDate) ?: return@forEach
                val id = releaseAlertId(show, episode.seasonNumber, episode.episodeNumber)
                if (store.getReleaseAlert(id) == null) {
                    store.putReleaseAlert(
                        ReleaseAlert(
                            id = id,
                            video = show,
                            releaseAtMillis = releaseAt,
                            kind = NotificationKind.WATCHED_SHOW_EPISODE,
                            season = episode.seasonNumber,
                            episode = episode.episodeNumber
                        )
                    )
                }
            }
        }
    }
}
