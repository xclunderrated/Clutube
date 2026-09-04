package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.SettingsManager
import com.example.data.StreamService
import com.example.data.tmdb.TmdbRepository
import com.example.data.tmdb.TmdbVideoItem
import com.example.model.MediaType
import com.example.util.StreamPlayerSkin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Clutube", context.getString(R.string.app_name))
    }

    @Test
    fun `only VidSrc and VidLink Pro are exposed as providers`() {
        assertEquals(
            listOf(StreamService.VIDSRC_SERVER_ID, StreamService.VIDLINK_SERVER_ID),
            StreamService.AVAILABLE_SERVERS.map { it.id }
        )
        assertEquals(StreamService.VIDSRC_SERVER_ID, StreamService.DEFAULT_SERVER_ID)
        assertTrue(StreamService.AVAILABLE_SERVERS.first().isRecommended)
        assertTrue(StreamService.AVAILABLE_SERVERS.last().isRecommended)
    }

    @Test
    fun `VidSrc mirror catalog is complete and ordered`() {
        assertEquals(
            listOf(
                "vidsrc2.ru",
                "vidsrc.ir",
                "vidsrcme.ru",
                "vidsrcme.su",
                "vidsrc-me.ru",
                "vidsrc-me.su",
                "vidsrc-embed.ru",
                "vidsrc-embed.su",
                "vsrc.su"
            ),
            StreamService.VIDSRC_SERVER_HOSTS
        )
        val normalized = StreamService.normalizeVidSrcServerOrder(
            listOf("vsrc.su", "vsrc.su", "unknown.example")
        )
        assertEquals("vsrc.su", normalized.first())
        assertEquals(StreamService.VIDSRC_SERVER_HOSTS.toSet(), normalized.toSet())
        assertEquals(StreamService.VIDSRC_SERVER_HOSTS.size, normalized.size)
    }

    @Test
    fun `VidSrc embed urls use the selected mirror`() {
        val movieUrl = StreamService.buildEmbedUrl(
            mediaType = MediaType.MOVIE,
            id = "693134",
            serverId = StreamService.VIDSRC_SERVER_ID,
            vidSrcHost = "vidsrc.ir"
        )
        assertEquals("https://vidsrc.ir/embed/movie/693134?autoplay=1", movieUrl)

        val tvUrl = StreamService.buildEmbedUrl(
            mediaType = MediaType.TV_SHOW,
            id = "66732",
            season = 4,
            episode = 9,
            serverId = StreamService.VIDSRC_SERVER_ID,
            vidSrcHost = "vsrc.su"
        )
        assertEquals("https://vsrc.su/embed/tv/66732/4/9?autoplay=1", tvUrl)
    }

    @Test
    fun `VidLink Pro embed urls remain unchanged`() {
        val movieUrl = StreamService.buildEmbedUrl(
            mediaType = MediaType.MOVIE,
            id = "693134",
            serverId = StreamService.VIDLINK_SERVER_ID
        )
        assertEquals(
            "https://vidlink.pro/movie/693134?primaryColor=ff0000&secondaryColor=121212&iconColor=ffffff&autoplay=true",
            movieUrl
        )

        val tvUrl = StreamService.buildEmbedUrl(
            mediaType = MediaType.TV_SHOW,
            id = "66732",
            season = 4,
            episode = 9,
            serverId = StreamService.VIDLINK_SERVER_ID
        )
        assertEquals(
            "https://vidlink.pro/tv/66732/4/9?primaryColor=ff0000&secondaryColor=121212&iconColor=ffffff&autoplay=true",
            tvUrl
        )
    }

    @Test
    fun `trailer selection prefers official YouTube trailers`() {
        val selected = TmdbRepository.selectTrailerVideo(
            listOf(
                TmdbVideoItem(
                    id = "vimeo_clip",
                    key = "vimeo-key",
                    site = "Vimeo",
                    type = "Trailer",
                    official = true
                ),
                TmdbVideoItem(
                    id = "youtube_teaser",
                    key = "teaser-key",
                    site = "YouTube",
                    type = "Teaser",
                    official = true
                ),
                TmdbVideoItem(
                    id = "youtube_trailer",
                    key = "trailer-key",
                    site = "YouTube",
                    type = "Trailer",
                    official = true
                )
            )
        )

        assertEquals("trailer-key", selected?.key)
    }

    @Test
    fun `provider failover switches between VidSrc and VidLink Pro`() {
        assertEquals(
            listOf(StreamService.VIDLINK_SERVER_ID),
            StreamService.fallbackServerIds(StreamService.VIDSRC_SERVER_ID)
        )
        assertEquals(
            listOf(StreamService.VIDSRC_SERVER_ID),
            StreamService.fallbackServerIds(StreamService.VIDLINK_SERVER_ID)
        )
    }

    @Test
    fun `development sample videos cannot be used as direct title sources`() {
        assertNull(
            StreamService.directSourceOrNull(
                "https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4"
            )
        )
        assertNull(
            StreamService.directSourceOrNull(
                "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
            )
        )
        assertEquals(
            "https://cdn.example/title/master.m3u8",
            StreamService.directSourceOrNull(" https://cdn.example/title/master.m3u8 ")
        )
    }

    @Test
    fun `player skin only intercepts the VidSrc player stylesheet`() {
        assertTrue(
            StreamPlayerSkin.isPlayerCssUrl(
                "https://cloudorchestranova.com/embed/iframe_player/assets/player.css?v=1"
            )
        )
        assertTrue(
            StreamPlayerSkin.isPlayerJsUrl(
                "https://cloudorchestranova.com/embed/iframe_player/assets/player.js?v=1"
            )
        )
        assertTrue(
            StreamPlayerSkin.isPlayerEmbedHtmlUrl(
                "https://cloudorchestranova.com/embed/movie/693134?autoplay=1"
            )
        )
        assertFalse(
            StreamPlayerSkin.isPlayerEmbedHtmlUrl(
                "https://cloudorchestranova.com/embed/player/movie/693134?autoplay=1"
            )
        )
        assertFalse(
            StreamPlayerSkin.isPlayerCssUrl(
                "https://cloudorchestranova.com/embed/iframe_player/assets/player.js"
            )
        )
        assertFalse(
            StreamPlayerSkin.isPlayerCssUrl(
                "https://example.com/embed/iframe_player/assets/player.css"
            )
        )
        assertTrue(StreamPlayerSkin.YOUTUBE_PLAYER_CSS.contains("#ff0000"))
        assertTrue(StreamPlayerSkin.YOUTUBE_PLAYER_CSS.contains(".jw .jw-controls"))
        assertTrue(StreamPlayerSkin.YOUTUBE_PLAYER_CSS.contains("#ccBtn"))
        assertTrue(StreamPlayerSkin.YOUTUBE_PLAYER_CSS.contains(".jw .jw-vol"))
        assertTrue(StreamPlayerSkin.YOUTUBE_PLAYER_CSS.contains("background: transparent"))
    }

    @Test
    fun `settings persist provider and VidSrc preferences`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = SettingsManager(context)
        manager.selectedServerId = StreamService.VIDLINK_SERVER_ID
        manager.vidSrcServerOrder = listOf("vsrc.su", "vidsrc.ir")
        manager.selectedVidSrcServerId = "vsrc.su"

        val newManager = SettingsManager(context)
        assertEquals(StreamService.VIDLINK_SERVER_ID, newManager.selectedServerId)
        assertEquals("vsrc.su", newManager.vidSrcServerOrder.first())
        assertEquals("vsrc.su", newManager.selectedVidSrcServerId)
        assertFalse(newManager.vidSrcServerOrder.contains("unknown.example"))
    }
}
