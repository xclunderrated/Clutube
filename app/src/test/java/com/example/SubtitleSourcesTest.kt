package com.example

import com.example.data.local.DownloadEntity
import com.example.data.subtitles.SubtitleCandidate
import com.example.data.subtitles.SubtitleSources
import com.example.data.subtitles.decodeSubtitleManifest
import com.example.data.subtitles.encodeSubtitleManifest
import com.example.data.subtitles.hasUsableSubtitles
import com.example.data.subtitles.normalizeSubtitleLanguage
import com.example.data.subtitles.SubtitleFileInfo
import com.example.data.subtitles.subtitleBadgeLabel
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
class SubtitleSourcesTest {

    @Test
    fun `wyzie url carries id season episode language and key`() {
        val url = SubtitleSources.buildWyzieSearchUrl(
            baseUrl = "https://sub.wyzie.io/",
            imdbId = "tt1375666",
            tmdbId = "27205",
            season = 1,
            episode = 2,
            language = "en",
            apiKey = "k123"
        )

        assertTrue(url!!.startsWith("https://sub.wyzie.io/search?"))
        assertTrue(url.contains("id=tt1375666"))
        assertTrue(url.contains("season=1"))
        assertTrue(url.contains("episode=2"))
        assertTrue(url.contains("language=en"))
        assertTrue(url.contains("format=srt"))
        assertTrue(url.contains("key=k123"))
    }

    @Test
    fun `wyzie url prefers imdb and omits season for movies`() {
        val url = SubtitleSources.buildWyzieSearchUrl(
            baseUrl = "https://proxy.example.workers.dev",
            imdbId = null,
            tmdbId = "27205",
            season = null,
            episode = null,
            language = "es",
            apiKey = null
        )!!

        assertTrue(url.startsWith("https://proxy.example.workers.dev/search?"))
        assertTrue(url.contains("id=27205"))
        assertTrue(url.contains("language=es"))
        assertFalse(url.contains("season="))
        assertFalse(url.contains("key="))
    }

    @Test
    fun `wyzie url returns null without any id`() {
        assertNull(
            SubtitleSources.buildWyzieSearchUrl(
                baseUrl = "https://sub.wyzie.io",
                imdbId = null,
                tmdbId = " ",
                season = null,
                episode = null,
                language = "en",
                apiKey = null
            )
        )
    }

    @Test
    fun `wyzie response parses alias shapes`() {
        val body = """
            [
              {"url": "https://cdn.example/a.srt", "language": "en", "format": "srt",
               "releaseName": "Inception.2010.1080p.BluRay.x264", "hi": false,
               "downloads": 42, "source": "charlie"},
              {"downloadUrl": "https://cdn.example/b.vtt", "displayLanguage": "English",
               "release_name": "Inception 720p WEB-DL", "hearingImpaired": true}
            ]
        """.trimIndent()

        val parsed = SubtitleSources.parseWyzieResponse(body)

        assertEquals(2, parsed.size)
        assertEquals("https://cdn.example/a.srt", parsed[0].downloadUrl)
        assertEquals("en", parsed[0].language)
        assertEquals(42L, parsed[0].downloadCount)
        assertEquals("charlie", parsed[0].source)
        assertEquals("https://cdn.example/b.vtt", parsed[1].downloadUrl)
        assertEquals("vtt", parsed[1].format)
        assertTrue(parsed[1].hearingImpaired)
    }

    @Test
    fun `wyzie response rejects garbage`() {
        assertTrue(SubtitleSources.parseWyzieResponse("").isEmpty())
        assertTrue(SubtitleSources.parseWyzieResponse("<html>nope</html>").isEmpty())
        assertTrue(SubtitleSources.parseWyzieResponse("""[{"no_url": true}]""").isEmpty())
    }

    @Test
    fun `subdl response unpacks episode files`() {
        val body = """
            {"status": true, "subtitles": [
              {"release_name": "Show S01E01 1080p WEB", "language": "EN",
               "unpack_files": [
                 {"name": "s01e01.srt", "language": "EN", "hi": false,
                  "format": "srt", "release_name": "Show S01E01 1080p WEB",
                  "url": "/subtitle/1/abc"},
                 {"name": "s01e01-es.srt", "language": "ES", "hi": false,
                  "format": "srt", "url": "https://dl.subdl.com/subtitle/1/def"}
               ]}
            ]}
        """.trimIndent()

        val parsed = SubtitleSources.parseSubdlResponse(body)

        assertEquals(2, parsed.size)
        assertEquals("https://dl.subdl.com/subtitle/1/abc", parsed[0].downloadUrl)
        assertEquals("en", parsed[0].language)
        assertEquals("https://dl.subdl.com/subtitle/1/def", parsed[1].downloadUrl)
        assertEquals("es", parsed[1].language)
    }

    @Test
    fun `subdl response rejects failure status`() {
        assertTrue(SubtitleSources.parseSubdlResponse("""{"status": false}""").isEmpty())
        assertTrue(SubtitleSources.parseSubdlResponse("nope").isEmpty())
    }

    @Test
    fun `yify movie page extracts rows`() {
        val html = """
            <table><tr>
            <td>English</td>
            <td><a href="/subtitles/movie-2010-english-yify-123">subtitle Movie.2010.1080p.BluRay.x264-[YTS.AM]</a></td>
            </tr><tr>
            <td>Arabic</td>
            <td><a href="/subtitles/movie-2010-arabic-yify-124">subtitle Movie.2010.720p.BluRay.x264.[YTS.AG]</a></td>
            </tr></table>
        """.trimIndent()

        val rows = SubtitleSources.parseYifyMoviePage(html)

        assertEquals(2, rows.size)
        assertEquals("English", rows[0].language)
        assertEquals("/subtitles/movie-2010-english-yify-123", rows[0].detailPath)
        assertTrue(rows[0].release.contains("1080p"))
    }

    @Test
    fun `yify detail page finds zip link`() {
        val html = """<a href="/subtitles/zip/movie-en.zip">Download</a>"""
        assertEquals(
            "https://yifysubtitles.ch/subtitles/zip/movie-en.zip",
            SubtitleSources.parseYifyDetailPage(html)
        )
        assertNull(SubtitleSources.parseYifyDetailPage("<p>no links</p>"))
    }

    @Test
    fun `scoring prefers same-release over popular`() {
        val torrent = "Inception.2010.1080p.BluRay.x264-[YTS.AM]"
        val sameRelease = SubtitleCandidate(
            downloadUrl = "https://a/s.srt", language = "en", format = "srt",
            releaseName = "Inception.2010.1080p.BluRay.x264-[YTS.AM]",
            downloadCount = 3, source = "wyzie"
        )
        val popularMismatch = SubtitleCandidate(
            downloadUrl = "https://b/s.srt", language = "en", format = "srt",
            releaseName = "Inception 720p HDTV x264-ORENJI",
            downloadCount = 50_000, source = "wyzie"
        )

        val best = SubtitleSources.selectBestCandidate(
            listOf(popularMismatch, sameRelease), torrent
        )

        assertEquals(sameRelease, best)
    }

    @Test
    fun `scoring honors hearing-impaired preference`() {
        val hi = SubtitleCandidate(
            downloadUrl = "https://a/s.srt", language = "en", format = "srt",
            releaseName = "Movie 1080p BluRay", hearingImpaired = true, source = "wyzie"
        )
        val plain = hi.copy(downloadUrl = "https://b/s.srt", hearingImpaired = false)

        assertEquals(
            hi,
            SubtitleSources.selectBestCandidate(listOf(plain, hi), "Movie 1080p BluRay", preferHearingImpaired = true)
        )
        assertEquals(
            plain,
            SubtitleSources.selectBestCandidate(listOf(plain, hi), "Movie 1080p BluRay", preferHearingImpaired = false)
        )
    }

    @Test
    fun `language normalization maps labels and prefs`() {
        assertEquals("en", normalizeSubtitleLanguage("English (CC)"))
        assertEquals("es", normalizeSubtitleLanguage("es"))
        assertEquals("es", normalizeSubtitleLanguage("Spanish"))
        assertEquals("off", normalizeSubtitleLanguage("Off"))
        assertEquals("off", normalizeSubtitleLanguage(null))
        assertEquals("en", normalizeSubtitleLanguage("auto"))
        assertEquals("en", normalizeSubtitleLanguage("Weird Label"))
    }

    @Test
    fun `badge reflects real tracks not legacy label`() {
        val base = DownloadEntity(
            id = "dl_movie_1",
            tmdbId = "1",
            mediaType = "MOVIE",
            title = "Movie",
            downloadUrl = "https://example/x",
            localFilePath = "/tmp/x.mp4",
            subtitleCc = "English (CC)"
        )

        // Legacy label alone is NOT proof of a track.
        assertFalse(hasUsableSubtitles(base))
        assertNull(subtitleBadgeLabel(base))

        assertTrue(hasUsableSubtitles(base.copy(subtitleFilePath = "/tmp/x.en.srt")))
        assertEquals("CC·EN", subtitleBadgeLabel(base.copy(subtitleFilePath = "/tmp/x.en.srt")))
        assertTrue(hasUsableSubtitles(base.copy(hasEmbeddedSubtitles = true)))
    }

    @Test
    fun `manifest round trips file list`() {
        val files = listOf(
            SubtitleFileInfo("/a/x.en.srt", "en", "EN · SRT", "srt", "Movie BluRay", false),
            SubtitleFileInfo("/a/x.es.srt", "es", "ES · SRT", "srt", null, true)
        )

        val decoded = decodeSubtitleManifest(encodeSubtitleManifest(files))

        assertEquals(2, decoded.size)
        assertEquals("/a/x.en.srt", decoded[0].path)
        assertEquals("Movie BluRay", decoded[0].releaseName)
        assertTrue(decoded[1].hearingImpaired)
        assertTrue(decodeSubtitleManifest(null).isEmpty())
        assertTrue(decodeSubtitleManifest("garbage").isEmpty())
    }
}
