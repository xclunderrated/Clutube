package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.example.data.StreamService
import com.example.model.CommentItem
import com.example.model.MediaType
import com.example.model.VideoItem
import com.example.ui.screens.WatchScreen
import com.example.ui.theme.YouTubeTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class WatchScreenComposeReproTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `watch screen composes for a movie without crashing`() {
        composeTestRule.setContent {
            YouTubeTheme {
                WatchScreen(
                    video = movie(),
                    relatedVideos = emptyList(),
                    tvEpisodes = emptyList(),
                    totalSeasons = 1,
                    selectedSeason = 1,
                    selectedServerId = StreamService.DEFAULT_SERVER_ID,
                    resumePositionSeconds = 50.0,
                    isLiked = false,
                    isDisliked = false,
                    isSubscribed = false,
                    isSaved = false,
                    topComment = null,
                    onMinimize = {},
                    onSelectServer = {},
                    onToggleLike = {},
                    onToggleDislike = {},
                    onToggleSubscribe = {},
                    onToggleSave = {},
                    onOpenComments = {},
                    onOpenServerDialog = {},
                    onSelectSeason = {},
                    onSelectEpisode = { _, _ -> },
                    onSelectVideo = {},
                    onSaveToWatchLater = {},
                    onShare = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("youtube_video_player_container").assertExists()
    }

    @Test
    fun `watch screen composes for a series without crashing`() {
        composeTestRule.setContent {
            YouTubeTheme {
                WatchScreen(
                    video = series(),
                    relatedVideos = emptyList(),
                    tvEpisodes = emptyList(),
                    totalSeasons = 2,
                    selectedSeason = 1,
                    selectedServerId = StreamService.DEFAULT_SERVER_ID,
                    resumePositionSeconds = 0.0,
                    isLiked = false,
                    isDisliked = false,
                    isSubscribed = false,
                    isSaved = false,
                    topComment = null,
                    onMinimize = {},
                    onSelectServer = {},
                    onToggleLike = {},
                    onToggleDislike = {},
                    onToggleSubscribe = {},
                    onToggleSave = {},
                    onOpenComments = {},
                    onOpenServerDialog = {},
                    onSelectSeason = {},
                    onSelectEpisode = { _, _ -> },
                    onSelectVideo = {},
                    onSaveToWatchLater = {},
                    onShare = {}
                )
            }
        }

        composeTestRule.onNodeWithTag("youtube_video_player_container").assertExists()
    }

    private fun movie() = VideoItem(
        id = "m1",
        title = "Test Movie",
        description = "Desc",
        channelName = "Studio",
        channelAvatarUrl = "",
        views = "",
        publishedAt = "",
        duration = "1:30:00",
        thumbnailUrl = "",
        mediaType = MediaType.MOVIE,
        tmdbId = "100"
    )

    private fun series() = movie().copy(
        id = "s1",
        title = "Test Show",
        mediaType = MediaType.TV_SHOW,
        tmdbId = "200"
    )
}
