package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertTextContains
import com.example.data.tmdb.TmdbEpisodeItem
import com.example.ui.components.TvShowEpisodeList
import com.example.ui.theme.YouTubeTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class TvShowEpisodeListTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `upcoming episode is not playable and exposes notify action via overflow`() {
        val upcoming = TmdbEpisodeItem(
            id = 102,
            episodeNumber = 2,
            seasonNumber = 1,
            name = "Coming next",
            airDate = "2999-12-31"
        )
        val released = TmdbEpisodeItem(
            id = 101,
            episodeNumber = 1,
            seasonNumber = 1,
            name = "Available now",
            airDate = "2020-01-01"
        )
        var selectedEpisode: Pair<Int, Int>? = null
        var notifiedEpisode: Pair<Int, Int>? = null

        composeTestRule.setContent {
            YouTubeTheme {
                TvShowEpisodeList(
                    episodes = listOf(released, upcoming),
                    totalSeasons = 1,
                    selectedSeason = 1,
                    currentEpisodeNumber = 1,
                    fallbackThumbnailUrl = "",
                    onSelectSeason = {},
                    onSelectEpisode = { season, episode -> selectedEpisode = season to episode },
                    isEpisodeAlertActive = { _, _ -> false },
                    onNotifyEpisode = { episode ->
                        notifiedEpisode = episode.seasonNumber to episode.episodeNumber
                    }
                )
            }
        }

        // Episodes are expanded by default; no toggle click needed.
        composeTestRule.onNodeWithTag("episode_item_1_2")
            .assertIsDisplayed()
            .assertTextContains("UPCOMING")
            .assertIsNotEnabled()
        // Notify lives in the single trailing overflow menu (YouTube-style).
        composeTestRule.onNodeWithTag("episode_menu_1_2").performClick()
        composeTestRule.onNodeWithTag("episode_notify_1_2").performClick()
        composeTestRule.onNodeWithTag("episode_item_1_1").performClick()

        assertEquals(1 to 2, notifiedEpisode)
        assertEquals(1 to 1, selectedEpisode)
    }

    @Test
    fun `meta shows formatted date and rating, title uses non-bold style`() {
        val episode = TmdbEpisodeItem(
            id = 101,
            episodeNumber = 1,
            seasonNumber = 1,
            name = "Pilot",
            airDate = "2020-01-01",
            runtime = 45,
            voteAverage = 7.4
        )

        composeTestRule.setContent {
            YouTubeTheme {
                TvShowEpisodeList(
                    episodes = listOf(episode),
                    totalSeasons = 1,
                    selectedSeason = 1,
                    currentEpisodeNumber = 2,
                    fallbackThumbnailUrl = "",
                    onSelectSeason = {},
                    onSelectEpisode = { _, _ -> }
                )
            }
        }

        // Raw TMDB date must be formatted; rating uses quiet star style.
        composeTestRule.onNodeWithTag("episode_item_1_1")
            .assertIsDisplayed()
            .assertTextContains("Jan 1, 2020")
            .assertTextContains("45 min")
            .assertTextContains("★ 7/10")
    }

    @Test
    fun `released row overflow exposes play-next queue download watched`() {
        val episode = TmdbEpisodeItem(
            id = 101,
            episodeNumber = 1,
            seasonNumber = 1,
            name = "Pilot",
            airDate = "2020-01-01",
            runtime = 45
        )
        var queued: Pair<Int, Int>? = null
        var playedNext: Pair<Int, Int>? = null
        var downloaded: Pair<Int, Int>? = null
        var toggledWatched: Pair<Int, Int>? = null

        composeTestRule.setContent {
            YouTubeTheme {
                TvShowEpisodeList(
                    episodes = listOf(episode),
                    totalSeasons = 1,
                    selectedSeason = 1,
                    currentEpisodeNumber = 2,
                    fallbackThumbnailUrl = "",
                    onSelectSeason = {},
                    onSelectEpisode = { _, _ -> },
                    onQueueEpisode = { ep -> queued = ep.seasonNumber to ep.episodeNumber },
                    onPlayEpisodeNext = { ep -> playedNext = ep.seasonNumber to ep.episodeNumber },
                    onDownloadEpisode = { ep -> downloaded = ep.seasonNumber to ep.episodeNumber },
                    onToggleEpisodeWatched = { ep ->
                        toggledWatched = ep.seasonNumber to ep.episodeNumber
                    }
                )
            }
        }

        composeTestRule.onNodeWithTag("episode_menu_1_1").performClick()
        composeTestRule.onNodeWithTag("episode_menu_play_next_1_1")
            .assertIsDisplayed()
            .performClick()
        assertEquals(1 to 1, playedNext)

        composeTestRule.onNodeWithTag("episode_menu_1_1").performClick()
        composeTestRule.onNodeWithTag("episode_queue_1_1").performClick()
        assertEquals(1 to 1, queued)

        composeTestRule.onNodeWithTag("episode_menu_1_1").performClick()
        composeTestRule.onNodeWithTag("episode_download_1_1").performClick()
        assertEquals(1 to 1, downloaded)

        composeTestRule.onNodeWithTag("episode_menu_1_1").performClick()
        composeTestRule.onNodeWithTag("episode_menu_watched_1_1").performClick()
        assertEquals(1 to 1, toggledWatched)
    }

    @Test
    fun `season chip shows watched counts and header has compact download action`() {
        val episodes = listOf(
            TmdbEpisodeItem(id = 1, episodeNumber = 1, seasonNumber = 1, name = "E1", airDate = "2020-01-01"),
            TmdbEpisodeItem(id = 2, episodeNumber = 2, seasonNumber = 1, name = "E2", airDate = "2020-01-08")
        )

        composeTestRule.setContent {
            YouTubeTheme {
                TvShowEpisodeList(
                    episodes = episodes,
                    totalSeasons = 2,
                    selectedSeason = 1,
                    currentEpisodeNumber = 1,
                    fallbackThumbnailUrl = "",
                    onSelectSeason = {},
                    onSelectEpisode = { _, _ -> },
                    onDownloadSeason = { _, _ -> },
                    isEpisodeWatched = { _, episode -> episode == 1 },
                    watchedCountBySeason = mapOf(1 to 1),
                    totalCountBySeason = mapOf(1 to 2)
                )
            }
        }

        composeTestRule.onNodeWithTag("season_chip_1")
            .assertIsDisplayed()
            .assertTextContains("1/2")
        composeTestRule.onNodeWithTag("download_season_btn").assertIsDisplayed()
    }
}
