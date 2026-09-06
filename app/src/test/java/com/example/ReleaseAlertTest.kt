package com.example

import com.example.model.AppNotification
import com.example.model.MediaType
import com.example.model.NotificationKind
import com.example.model.ReleaseAlert
import com.example.model.VideoItem
import com.example.model.isFreshRelease
import com.example.model.isUnreleased
import com.example.model.releaseAlertId
import com.example.model.releaseDateMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseAlertTest {

    @Test
    fun `release ids keep episodes separate from each other and the title alert`() {
        val show = testVideo(MediaType.TV_SHOW)

        assertNotEquals(releaseAlertId(show, season = 1, episode = 1), releaseAlertId(show, season = 1, episode = 2))
        assertNotEquals(releaseAlertId(show), releaseAlertId(show, season = 1, episode = 1))
        assertEquals(releaseAlertId(show, season = 1, episode = 1), releaseAlertId(show, season = 1, episode = 1))
    }

    @Test
    fun `release dates distinguish future content and malformed dates`() {
        val comparisonTime = releaseDateMillis("2026-01-01")!!

        assertTrue(isUnreleased("2999-12-31", nowMillis = comparisonTime))
        assertFalse(isUnreleased("2020-01-01", nowMillis = comparisonTime))
        assertNull(releaseDateMillis("not-a-date"))
    }

    @Test
    fun `freshness gate keeps stale catalog arrivals out of subscription pings`() {
        val now = releaseDateMillis("2026-06-15")!!

        // A 2005 title surfacing in a channel list is backfill, not news.
        assertFalse(isFreshRelease("2005-07-15", nowMillis = now))
        // Released 40 days ago: outside the 30-day window.
        assertFalse(isFreshRelease("2026-05-06", nowMillis = now))
        // Released 10 days ago: genuinely new.
        assertTrue(isFreshRelease("2026-06-05", nowMillis = now))
        // Released today: new.
        assertTrue(isFreshRelease("2026-06-15", nowMillis = now))
        // Future dates belong to release alerts, not subscription pings.
        assertFalse(isFreshRelease("2026-12-25", nowMillis = now))
        // Unknown date: staleness can't be proven, so don't suppress.
        assertTrue(isFreshRelease(null, nowMillis = now))
        assertTrue(isFreshRelease("not-a-date", nowMillis = now))
    }

    @Test
    fun `episode alerts and notifications open the requested episode`() {
        val show = testVideo(MediaType.TV_SHOW)
        val alert = ReleaseAlert(
            id = releaseAlertId(show, season = 2, episode = 4),
            video = show,
            releaseAtMillis = releaseDateMillis("2999-12-31")!!,
            kind = NotificationKind.WATCHED_SHOW_EPISODE,
            season = 2,
            episode = 4
        )
        val notification = AppNotification(
            id = "notification:${alert.id}",
            kind = alert.kind,
            title = "New episode available",
            message = "Ready to watch",
            video = show,
            season = alert.season,
            episode = alert.episode
        )

        assertEquals(2, alert.targetVideo.currentSeason)
        assertEquals(4, alert.targetVideo.currentEpisode)
        assertEquals(2, notification.targetVideo.currentSeason)
        assertEquals(4, notification.targetVideo.currentEpisode)
    }

    private fun testVideo(mediaType: MediaType) = VideoItem(
        id = "video-id",
        title = "Test show",
        description = "",
        channelName = "Test channel",
        channelAvatarUrl = "",
        publishedAt = "",
        duration = "",
        thumbnailUrl = "",
        mediaType = mediaType,
        tmdbId = "tv-42"
    )
}
