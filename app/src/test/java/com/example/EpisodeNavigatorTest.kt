package com.example

import com.example.data.tmdb.TmdbEpisodeItem
import com.example.viewmodel.EpisodeNavigator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EpisodeNavigatorTest {

    @Test
    fun `chooses the smallest sorted episode after current`() {
        val next = EpisodeNavigator.nextEpisode(
            currentSeason = 1,
            currentEpisode = 1,
            episodes = listOf(episode(4), episode(3), episode(2)),
            totalSeasons = 2
        )

        assertEquals(2, next?.episode)
        assertEquals(1, next?.season)
    }

    @Test
    fun `advances to episode one of the next season at a season boundary`() {
        val next = EpisodeNavigator.nextEpisode(
            currentSeason = 1,
            currentEpisode = 3,
            episodes = listOf(episode(1), episode(2), episode(3)),
            totalSeasons = 2
        )

        assertEquals(2, next?.season)
        assertEquals(1, next?.episode)
    }

    @Test
    fun `does not guess an episode when metadata is unavailable`() {
        val next = EpisodeNavigator.nextEpisode(
            currentSeason = 1,
            currentEpisode = 3,
            episodes = emptyList(),
            totalSeasons = 2
        )

        assertNull(next)
    }

    @Test
    fun `stops at the final known season`() {
        val next = EpisodeNavigator.nextEpisode(
            currentSeason = 2,
            currentEpisode = 10,
            episodes = listOf(episode(10, season = 2)),
            totalSeasons = 2
        )

        assertNull(next)
    }

    private fun episode(number: Int, season: Int = 1) = TmdbEpisodeItem(
        id = number,
        episodeNumber = number,
        seasonNumber = season,
        name = "Episode $number"
    )
}
