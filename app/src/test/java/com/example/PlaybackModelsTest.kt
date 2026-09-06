package com.example

import com.example.model.MediaType
import com.example.model.VideoItem
import com.example.model.WatchHistoryEntry
import com.example.model.deduplicateContinueWatching
import com.example.model.findProgressEntry
import com.example.model.playbackKey
import com.example.model.resumePositionSeconds
import com.example.model.toContinueUiModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackModelsTest {

    @Test
    fun `playback keys keep tv episodes separate and ignore stream urls`() {
        val episodeTwo = testVideo(
            mediaType = MediaType.TV_SHOW,
            tmdbId = "42",
            season = 1,
            episode = 2,
            embedUrl = "https://old-server.example/one"
        )
        val episodeThree = episodeTwo.copy(
            currentEpisode = 3,
            embedStreamUrl = "https://different-server.example/two"
        )

        assertNotEquals(episodeTwo.playbackKey(), episodeThree.playbackKey())
        assertEquals(
            "tv_show:42:s1:e2",
            episodeTwo.playbackKey()
        )
    }

    @Test
    fun `history normalization clamps invalid progress`() {
        val entry = WatchHistoryEntry(
            key = "stale-key",
            video = testVideo(),
            positionSeconds = 500,
            durationSeconds = 100,
            lastWatchedAtMillis = -10
        ).normalized()

        assertEquals(testVideo().playbackKey(), entry.key)
        assertEquals(100, entry.positionSeconds)
        assertEquals(100, entry.durationSeconds)
        assertEquals(0, entry.lastWatchedAtMillis)
        assertEquals(1f, entry.progressFraction, 0.001f)
    }

    @Test
    fun `unknown duration does not fabricate progress`() {
        val entry = WatchHistoryEntry(
            key = "key",
            video = testVideo(),
            positionSeconds = 30,
            durationSeconds = 0
        )

        assertEquals(0f, entry.progressFraction, 0.001f)
        assertEquals(0L, entry.remainingSeconds)
        assertTrue(entry.positionSeconds > 0)
    }

    @Test
    fun `continue watching keeps only the most recent unfinished episode per show`() {
        val firstEpisode = testVideo(
            mediaType = MediaType.TV_SHOW,
            tmdbId = "show-42",
            season = 1,
            episode = 1
        )
        val secondEpisode = firstEpisode.copy(currentEpisode = 2)
        val entries = listOf(
            WatchHistoryEntry(
                key = firstEpisode.playbackKey(),
                video = firstEpisode,
                positionSeconds = 80,
                lastWatchedAtMillis = 100
            ),
            WatchHistoryEntry(
                key = secondEpisode.playbackKey(),
                video = secondEpisode,
                positionSeconds = 20,
                lastWatchedAtMillis = 200
            )
        )

        val shelf = deduplicateContinueWatching(entries)

        assertEquals(1, shelf.size)
        assertEquals(2, shelf.single().video.currentEpisode)
    }

    @Test
    fun `resume rewinds ten seconds and restarts barely started titles`() {
        val mid = WatchHistoryEntry(
            key = "k1",
            video = testVideo(),
            positionSeconds = 100,
            durationSeconds = 600
        )
        val early = mid.copy(positionSeconds = 10)
        val done = mid.copy(completed = true)

        assertEquals(90.0, mid.resumePositionSeconds(), 0.001)
        assertEquals(0.0, early.resumePositionSeconds(), 0.001)
        assertEquals(0.0, done.resumePositionSeconds(), 0.001)
    }

    @Test
    fun `shelf models coerce episode numbers and share one label format`() {
        val zeroEpisode = testVideo(
            mediaType = MediaType.TV_SHOW,
            tmdbId = "show-7",
            season = 0,
            episode = 0
        )
        val entry = WatchHistoryEntry(
            key = zeroEpisode.playbackKey(),
            video = zeroEpisode,
            positionSeconds = 60,
            durationSeconds = 600,
            lastWatchedAtMillis = 5
        )

        val models = listOf(entry).toContinueUiModels()

        assertEquals(1, models.size)
        assertEquals(1, models.single().displaySeason)
        assertEquals(1, models.single().displayEpisode)
        assertEquals("S1:E1 • 9:00 left", models.single().label)
    }

    @Test
    fun `shelf models hide zero progress ghosts`() {
        val ghost = WatchHistoryEntry(
            key = testVideo().playbackKey(),
            video = testVideo(),
            positionSeconds = 0,
            durationSeconds = 600,
            lastWatchedAtMillis = 5
        )

        assertTrue(listOf(ghost).toContinueUiModels().isEmpty())
    }

    @Test
    fun `progress lookup prefers exact episode then series latest, never oldest`() {
        val base = testVideo(mediaType = MediaType.TV_SHOW, tmdbId = "show-9")
        val old = WatchHistoryEntry(
            key = base.copy(currentEpisode = 1).playbackKey(),
            video = base.copy(currentEpisode = 1),
            positionSeconds = 50,
            durationSeconds = 100,
            lastWatchedAtMillis = 100
        )
        val latest = WatchHistoryEntry(
            key = base.copy(currentEpisode = 3).playbackKey(),
            video = base.copy(currentEpisode = 3),
            positionSeconds = 10,
            durationSeconds = 100,
            lastWatchedAtMillis = 300
        )
        val entries = listOf(latest, old)

        // Exact S:E hit.
        assertEquals(
            3,
            findProgressEntry(base.copy(currentEpisode = 3), entries)?.video?.currentEpisode
        )
        assertEquals(
            1,
            findProgressEntry(base.copy(currentEpisode = 1), entries)?.video?.currentEpisode
        )
        // A catalog card with no entry of its own resolves to the
        // series-latest resume point instead of nothing.
        assertEquals(
            3,
            findProgressEntry(base.copy(currentEpisode = 2), entries)?.video?.currentEpisode
        )
        // Unknown title resolves to nothing instead of a stranger's progress.
        assertNull(findProgressEntry(testVideo(tmdbId = "other"), entries))
    }

    private fun testVideo(
        mediaType: MediaType = MediaType.MOVIE,
        tmdbId: String? = "100",
        season: Int = 1,
        episode: Int = 1,
        embedUrl: String = ""
    ) = VideoItem(
        id = "video-id",
        title = "Test video",
        description = "",
        channelName = "Test channel",
        channelAvatarUrl = "",
        views = "",
        publishedAt = "",
        duration = "",
        thumbnailUrl = "",
        embedStreamUrl = embedUrl,
        mediaType = mediaType,
        tmdbId = tmdbId,
        currentSeason = season,
        currentEpisode = episode
    )
}
