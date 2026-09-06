package com.example

import com.example.data.torrent.EpisodeMatch
import com.example.data.torrent.TorrentEngine
import com.example.data.torrent.TorrentMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TorrentMatcherTest {

    @Test
    fun `exact SxxEyy variants match`() {
        listOf(
            "Breaking Bad S01E02 1080p BluRay",
            "Breaking.Bad.s01e02.WEB-DL",
            "Breaking Bad S01.E02 720p",
            "Breaking Bad S01-E02 HDTV",
            "Breaking Bad 1x02 1080p"
        ).forEach {
            assertEquals("$it should be EXACT", EpisodeMatch.EXACT, TorrentMatcher.matchEpisode(it, 1, 2).kind)
            assertTrue(TorrentMatcher.isExactEpisode(it, 1, 2))
        }
    }

    @Test
    fun `wrong episode is never exact`() {
        listOf(
            "Breaking Bad S01E03 1080p",
            "Breaking Bad S02E02 1080p",
            "Breaking Bad 1x03 HDTV",
            "Breaking Bad S01E01-S01E03 pack"
        ).forEach {
            assertFalse("$it must not be EXACT for S01E02", TorrentMatcher.isExactEpisode(it, 1, 2))
        }
        assertEquals(
            EpisodeMatch.WRONG,
            TorrentMatcher.matchEpisode("Breaking Bad S01E03 1080p", 1, 2).kind
        )
    }

    @Test
    fun `packs classified not exact`() {
        assertEquals(
            EpisodeMatch.PACK_SEASON,
            TorrentMatcher.matchEpisode("Breaking Bad Season 1 1080p BluRay", 1, 2).kind
        )
        assertEquals(
            EpisodeMatch.PACK_COMPLETE,
            TorrentMatcher.matchEpisode("Breaking Bad Complete Series 1080p", 1, 2).kind
        )
        assertEquals(
            EpisodeMatch.MULTI,
            TorrentMatcher.matchEpisode("Breaking Bad S01E01-E08 1080p", 1, 2).kind
        )
        // Bare season tag without episode is a pack, never exact.
        assertFalse(TorrentMatcher.isExactEpisode("Breaking Bad S01 1080p", 1, 2))
    }

    @Test
    fun `clean title strips year suffix once`() {
        assertEquals("Dune", TorrentMatcher.cleanCatalogTitle("Dune (2024)"))
        assertEquals("Dune", TorrentMatcher.cleanCatalogTitle("Dune 2024"))
        assertEquals("Breaking Bad", TorrentMatcher.cleanCatalogTitle("Breaking Bad"))
        assertEquals("Dune Part Two", TorrentMatcher.queryTitle("Dune: Part Two (2024)"))
    }

    @Test
    fun `movie matcher checks title words and year tolerance`() {
        assertTrue(TorrentMatcher.matchMovie("Dune Part Two 2024 1080p BluRay", "Dune Part Two", "2024"))
        assertTrue(TorrentMatcher.matchMovie("Dune Part Two 2023 2160p", "Dune Part Two", "2024"))
        assertFalse(TorrentMatcher.matchMovie("Dune 1984 1080p", "Dune Part Two", "2024"))
        assertFalse(TorrentMatcher.matchMovie("Oppenheimer 2023 IMAX", "Dune Part Two", "2024"))
    }

    @Test
    fun `download id parses season episode`() {
        assertEquals(Pair(1, 2), TorrentEngine.parseSeasonEpisode("dl_tv_123_s1_e2"))
        assertEquals(Pair(12, 24), TorrentEngine.parseSeasonEpisode("dl_tv_123_s12_e24"))
        assertEquals(Pair(null, null), TorrentEngine.parseSeasonEpisode("dl_movie_123"))
    }
}
