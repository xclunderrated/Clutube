package com.example.model

import androidx.compose.runtime.Immutable
import kotlin.math.max
import kotlin.math.min
import java.util.Locale

enum class WatchLaterSort {
    RECENTLY_ADDED,
    OLDEST,
    DURATION
}

enum class PlaybackQuality(val wireValue: String) {
    AUTO("auto"),
    P1080("1080p"),
    P720("720p"),
    P480("480p"),
    P360("360p")
}

enum class SubtitlePreference(val wireValue: String) {
    OFF("off"),
    AUTO("auto"),
    ENGLISH("en"),
    SPANISH("es")
}

@Immutable
data class PlaybackPreferences(
    val quality: PlaybackQuality = PlaybackQuality.AUTO,
    val subtitles: SubtitlePreference = SubtitlePreference.OFF
)

enum class PlaybackErrorType {
    NETWORK,
    TIMEOUT,
    HTTP,
    RENDERER,
    PROVIDER,
    UNKNOWN
}

/**
 * A key that identifies the playable piece of content independently of the
 * selected stream provider. TV episodes include their season and episode so
 * that progress for one episode can never replace another episode's progress.
 */
fun VideoItem.playbackKey(): String {
    val contentId = (tmdbId ?: id).trim().ifEmpty { id.trim() }
    return buildString {
        append(mediaType.name.lowercase())
        append(':')
        append(contentId)
        if (mediaType == MediaType.TV_SHOW) {
            append(":s")
            append(currentSeason.coerceAtLeast(1))
            append(":e")
            append(currentEpisode.coerceAtLeast(1))
        }
    }
}

@Immutable
data class WatchHistoryEntry(
    val key: String,
    val video: VideoItem,
    val positionSeconds: Long = 0L,
    val durationSeconds: Long = 0L,
    val lastWatchedAtMillis: Long = 0L,
    val completed: Boolean = false
) {
    val progressFraction: Float
        get() {
            if (durationSeconds <= 0L) return 0f
            return (positionSeconds.toDouble() / durationSeconds.toDouble())
                .coerceIn(0.0, 1.0)
                .toFloat()
        }

    val remainingSeconds: Long
        get() = if (durationSeconds > 0L) {
            max(0L, durationSeconds - positionSeconds)
        } else {
            0L
        }

    fun normalized(): WatchHistoryEntry {
        val safeDuration = max(0L, durationSeconds)
        val safePosition = if (safeDuration > 0L) {
            min(max(0L, positionSeconds), safeDuration)
        } else {
            max(0L, positionSeconds)
        }
        return copy(
            key = video.playbackKey(),
            positionSeconds = safePosition,
            durationSeconds = safeDuration,
            lastWatchedAtMillis = max(0L, lastWatchedAtMillis)
        )
    }
}

/**
 * Continue Watching is a title-level shelf. Playback history remains
 * episode-specific, but a TV show should occupy one shelf slot and point to
 * the episode watched most recently.
 */
fun deduplicateContinueWatching(entries: List<WatchHistoryEntry>): List<WatchHistoryEntry> {
    return entries
        .asSequence()
        .filterNot { it.completed }
        .groupBy { entry ->
            val contentId = listOf(entry.video.tmdbId, entry.video.imdbId, entry.video.id)
                .firstOrNull { !it.isNullOrBlank() }
                ?.trim()
                ?.lowercase(Locale.ROOT)
                .orEmpty()
            if (entry.video.mediaType == MediaType.TV_SHOW) {
                "tv:$contentId"
            } else {
                // Movie progress is already episode-free; preserve its exact
                // playback key so unrelated titles cannot be merged.
                entry.key
            }
        }
        .mapNotNull { (_, groupedEntries) ->
            groupedEntries.maxWithOrNull(
                compareBy<WatchHistoryEntry> { it.lastWatchedAtMillis }
                    .thenBy { it.positionSeconds }
            )
        }
        .sortedByDescending { it.lastWatchedAtMillis }
        .toList()
}

@Immutable
data class PlayerSnapshot(
    val key: String,
    val generation: Long,
    val positionSeconds: Double,
    val durationSeconds: Double,
    val isPlaying: Boolean,
    val isMuted: Boolean,
    val bufferedPositionSeconds: Double = 0.0,
    val playbackRate: Double = 1.0
) {
    val normalizedPositionSeconds: Long
        get() = positionSeconds.takeIf { it.isFinite() && it >= 0.0 }?.toLong() ?: 0L

    val normalizedDurationSeconds: Long
        get() = durationSeconds.takeIf { it.isFinite() && it >= 0.0 }?.toLong() ?: 0L

    val normalizedBufferedPositionSeconds: Long
        get() = bufferedPositionSeconds.takeIf { it.isFinite() && it >= 0.0 }?.toLong() ?: 0L

    val normalizedPlaybackRate: Double
        get() = playbackRate.takeIf { it.isFinite() && it > 0.0 } ?: 1.0
}

sealed class PlayerEvent {
    abstract val key: String
    abstract val generation: Long

    data class Ready(
        override val key: String,
        override val generation: Long
    ) : PlayerEvent()

    data class Progress(
        val snapshot: PlayerSnapshot
    ) : PlayerEvent() {
        override val key: String = snapshot.key
        override val generation: Long = snapshot.generation
    }

    data class Ended(
        override val key: String,
        override val generation: Long
    ) : PlayerEvent()

    data class Error(
        override val key: String,
        override val generation: Long,
        val message: String? = null
    ) : PlayerEvent()
}

fun formatPlaybackTime(totalSeconds: Long): String {
    val safeSeconds = totalSeconds.coerceAtLeast(0L)
    val hours = safeSeconds / 3600L
    val minutes = (safeSeconds % 3600L) / 60L
    val seconds = safeSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
