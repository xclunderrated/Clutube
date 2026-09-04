package com.example

import com.example.model.MediaType
import com.example.model.VideoItem
import com.example.model.WatchHistoryEntry
import com.example.model.deduplicateContinueWatching
import com.example.model.playbackKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
