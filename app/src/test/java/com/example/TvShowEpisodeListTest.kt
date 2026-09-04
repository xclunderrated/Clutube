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
    fun `upcoming episode is not playable and exposes notify action`() {
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

        composeTestRule.onNodeWithTag("episodes_toggle").performClick()
        composeTestRule.onNodeWithTag("episode_item_1_2")
            .assertIsDisplayed()
            .assertTextContains("UPCOMING")
            .assertIsNotEnabled()
        composeTestRule.onNodeWithTag("episode_notify_1_2").performClick()
        composeTestRule.onNodeWithTag("episode_item_1_1").performClick()

        assertEquals(1 to 2, notifiedEpisode)
        assertEquals(1 to 1, selectedEpisode)
    }
}
