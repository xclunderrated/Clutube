package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsManager
import com.example.data.StreamService
import com.example.model.MediaType
import com.example.model.PlaybackPreferences
import com.example.model.PlaybackQuality
import com.example.model.SubtitlePreference
import com.example.model.VideoItem
import com.example.model.WatchHistoryEntry
import com.example.model.playbackKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsManagerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Before
    fun clearPreferences() {
        prefs.edit().clear().commit()
    }

    @After
    fun restorePreferences() {
        prefs.edit().clear().commit()
    }

    @Test
    fun `history round trip persists progress and auto next`() {
        val video = testVideo(MediaType.MOVIE)
        val entry = WatchHistoryEntry(
            key = video.playbackKey(),
            video = video,
            positionSeconds = 30,
            durationSeconds = 120,
            lastWatchedAtMillis = 1234
        )
        val manager = SettingsManager(context)
        manager.saveWatchHistoryEntries(listOf(entry))
        manager.isAutoNextEnabled = false

        val reloaded = SettingsManager(context)
        val saved = reloaded.getWatchHistoryEntries().single()

        assertEquals(30, saved.positionSeconds)
        assertEquals(120, saved.durationSeconds)
        assertEquals(1234, saved.lastWatchedAtMillis)
        assertFalse(reloaded.isAutoNextEnabled)
    }

    @Test
    fun `legacy history migrates while preserving tv episode`() {
        val legacyVideo = testVideo(
            mediaType = MediaType.TV_SHOW,
            season = 2,
            episode = 4
        )
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val listType = Types.newParameterizedType(List::class.java, VideoItem::class.java)
        val json = moshi.adapter<List<VideoItem>>(listType).toJson(listOf(legacyVideo))
        prefs.edit().putString(KEY_LEGACY_HISTORY, json).commit()

        val migrated = SettingsManager(context).getWatchHistoryEntries()

        assertEquals(1, migrated.size)
        assertEquals(2, migrated.single().video.currentSeason)
        assertEquals(4, migrated.single().video.currentEpisode)
        assertEquals(legacyVideo.playbackKey(), migrated.single().key)
        assertTrue(prefs.contains(KEY_NEW_HISTORY))
    }

    @Test
    fun `history is capped and progress is normalized`() {
        val entries = (1..55).map { index ->
            val video = testVideo(id = "video-$index")
            WatchHistoryEntry(
                key = video.playbackKey(),
                video = video,
                positionSeconds = if (index == 1) 999 else -5,
                durationSeconds = if (index == 1) 100 else 50
            )
        }

        val manager = SettingsManager(context)
        manager.saveWatchHistoryEntries(entries)
        val saved = manager.getWatchHistoryEntries()

        assertEquals(50, saved.size)
        assertEquals(100, saved.first().positionSeconds)
        assertEquals(0, saved[1].positionSeconds)
    }

    @Test
    fun `playback preferences persist independently for each provider`() {
        val manager = SettingsManager(context)
        manager.savePlaybackPreferences(
            StreamService.VIDSRC_SERVER_ID,
            PlaybackPreferences(PlaybackQuality.P720, SubtitlePreference.ENGLISH)
        )
        manager.savePlaybackPreferences(
            StreamService.VIDLINK_SERVER_ID,
            PlaybackPreferences(PlaybackQuality.P1080, SubtitlePreference.SPANISH)
        )

        val reloaded = SettingsManager(context)

        assertEquals(
            PlaybackPreferences(PlaybackQuality.P720, SubtitlePreference.ENGLISH),
            reloaded.getPlaybackPreferences(StreamService.VIDSRC_SERVER_ID)
        )
        assertEquals(
            PlaybackPreferences(PlaybackQuality.P1080, SubtitlePreference.SPANISH),
            reloaded.getPlaybackPreferences(StreamService.VIDLINK_SERVER_ID)
        )
        assertEquals(
            PlaybackPreferences(),
            reloaded.getPlaybackPreferences("unknown-provider")
        )
    }

    @Test
    fun `account playback preferences are shared by every provider`() {
        val manager = SettingsManager(context)
        val expected = PlaybackPreferences(PlaybackQuality.P1080, SubtitlePreference.ENGLISH)

        manager.savePlaybackPreferences(expected)

        val reloaded = SettingsManager(context)
        assertEquals(expected, reloaded.getPlaybackPreferences(StreamService.VIDSRC_SERVER_ID))
        assertEquals(expected, reloaded.getPlaybackPreferences(StreamService.VIDLINK_SERVER_ID))
        assertEquals(expected, reloaded.getPlaybackPreferences())
    }

    private fun testVideo(
        mediaType: MediaType = MediaType.MOVIE,
        id: String = "video-id",
        season: Int = 1,
        episode: Int = 1
    ) = VideoItem(
        id = id,
        title = "Test video",
        description = "",
        channelName = "Test channel",
        channelAvatarUrl = "",
        views = "",
        publishedAt = "",
        duration = "",
        thumbnailUrl = "",
        mediaType = mediaType,
        tmdbId = if (mediaType == MediaType.TV_SHOW) "tv-42-$id" else id,
        currentSeason = season,
        currentEpisode = episode
    )

    private companion object {
        const val PREFS_NAME = "clutube_app_preferences"
        const val KEY_LEGACY_HISTORY = "watch_history_json"
        const val KEY_NEW_HISTORY = "watch_history_entries_json"
    }
}
