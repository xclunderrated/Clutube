package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.MediaType
import com.example.model.VideoItem
import com.example.viewmodel.YouTubeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayVideoCrashReproTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: YouTubeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("clutube_app_preferences", Context.MODE_PRIVATE)
            .edit().clear().commit()
        viewModel = YouTubeViewModel()
        viewModel.initSettings(context)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `clicking a movie opens watch state without crashing`() {
        viewModel.playVideo(movie(), expand = true)
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.currentPlayingVideo)
        assertEquals(true, state.isPlayerExpanded)
    }

    @Test
    fun `clicking a series episode then resuming does not crash`() {
        val show = series()
        viewModel.playVideo(show, expand = true)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.selectTvEpisode(1, 2)
        testDispatcher.scheduler.advanceUntilIdle()

        val entry = viewModel.uiState.value.watchHistory.first()
        viewModel.resumeWatch(entry, expand = true)
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.currentPlayingVideo)
    }

    private fun movie() = VideoItem(
        id = "m1",
        title = "Test Movie",
        description = "",
        channelName = "Studio",
        channelAvatarUrl = "",
        views = "",
        publishedAt = "",
        duration = "1:30:00",
        thumbnailUrl = "",
        mediaType = MediaType.MOVIE
    )

    private fun series() = movie().copy(
        id = "s1",
        title = "Test Show",
        mediaType = MediaType.TV_SHOW
    )
}
