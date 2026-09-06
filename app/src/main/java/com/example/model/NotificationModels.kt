package com.example.model

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

enum class NotificationKind {
    RELEASE_ALERT,
    SUBSCRIPTION_RELEASE,
    WATCHED_SHOW_EPISODE
}

@Immutable
data class ReleaseAlert(
    val id: String,
    val video: VideoItem,
    val releaseAtMillis: Long,
    val kind: NotificationKind = NotificationKind.RELEASE_ALERT,
    val season: Int? = null,
    val episode: Int? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val deliveredAtMillis: Long? = null
) {
    val isDelivered: Boolean
        get() = deliveredAtMillis != null

    val targetVideo: VideoItem
        get() = if (season != null && episode != null) {
            video.copy(
                currentSeason = season.coerceAtLeast(1),
                currentEpisode = episode.coerceAtLeast(1)
            )
        } else {
            video
        }
}

@Immutable
data class AppNotification(
    val id: String,
    val kind: NotificationKind,
    val title: String,
    val message: String,
    val video: VideoItem,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val releaseAtMillis: Long? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val isRead: Boolean = false,
    val isDismissed: Boolean = false
) {
    val targetVideo: VideoItem
        get() = if (season != null && episode != null) {
            video.copy(
                currentSeason = season.coerceAtLeast(1),
                currentEpisode = episode.coerceAtLeast(1)
            )
        } else {
            video
        }
}

fun releaseAlertId(video: VideoItem, season: Int? = null, episode: Int? = null): String {
    val contentId = (video.tmdbId ?: video.id).trim().ifEmpty { video.id }
    return if (season != null && episode != null) {
        "release:tv:$contentId:s${season.coerceAtLeast(1)}:e${episode.coerceAtLeast(1)}"
    } else {
        "release:${video.mediaType.name.lowercase()}:$contentId"
    }
}

fun releaseDateMillis(rawDate: String?): Long? {
    val value = rawDate?.trim()?.take(10).orEmpty()
    if (value.isBlank()) return null
    return runCatching {
        LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()
}

fun isUnreleased(rawDate: String?, nowMillis: Long = System.currentTimeMillis()): Boolean =
    releaseDateMillis(rawDate)?.let { it > nowMillis } == true

/**
 * Max age for a subscription item to count as "new". Catalogs shift over
 * time, so a 2005 title entering a channel's top list is backfill, not news —
 * without this check it would push a "New from ..." notification.
 */
const val SUBSCRIPTION_FRESHNESS_DAYS = 30L

/**
 * True only for items released within [maxAgeDays]. Unknown dates return true
 * (staleness can't be proven); future dates return false (upcoming content is
 * covered by release alerts, not subscription pings).
 */
fun isFreshRelease(
    rawDate: String?,
    nowMillis: Long = System.currentTimeMillis(),
    maxAgeDays: Long = SUBSCRIPTION_FRESHNESS_DAYS
): Boolean {
    val releaseAt = releaseDateMillis(rawDate) ?: return true
    if (releaseAt > nowMillis) return false
    return nowMillis - releaseAt <= TimeUnit.DAYS.toMillis(maxAgeDays)
}
