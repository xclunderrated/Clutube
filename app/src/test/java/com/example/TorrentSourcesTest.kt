package com.example

import com.example.data.model.TorrentSource
import com.example.data.torrent.MagnetParser
import com.example.data.torrent.TorrentIndexerService
import com.example.data.torrent.TorrentSourceRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TorrentSourcesTest {

    private val hashA = "a94a8fe5ccb19ba61c4c0873d391e987982fbbd3"
    private val hashB = "da39a3ee5e6b4b0d3255bfef95601890afd80709"

    @Test
    fun `registry knows all shipped indexers`() {
        val ids = TorrentSourceRegistry.VERIFIED_INDEXERS.map { it.id }
        assertTrue(ids.containsAll(listOf("torrentio", "yts", "piratebay", "eztv", "solidtorrents", "nyaa", "animetosho")))
        assertTrue(TorrentSourceRegistry.isKnownIndexer("YTS"))
        assertFalse(TorrentSourceRegistry.isKnownIndexer("rarbg-clone"))
    }

    @Test
    fun `normalizeIndexerOrder drops unknown and appends new official indexers`() {
        val normalized = TorrentSourceRegistry.normalizeIndexerOrder(listOf("yts", "nope", "YTS", "  "))
        assertFalse("unknown dropped" in normalized)
        assertEquals(normalized.distinct(), normalized)
        assertTrue(normalized.containsAll(TorrentSourceRegistry.VERIFIED_INDEXERS.map { it.id }))
        assertEquals("yts", normalized.first())
    }

    @Test
    fun `enabledIndexers honors disabled set`() {
        val enabled = TorrentSourceRegistry.enabledIndexers(setOf("yts", "NYAA"))
        assertFalse(enabled.contains("yts"))
        assertFalse(enabled.contains("nyaa"))
        assertTrue(enabled.contains("eztv"))
        assertEquals(
            TorrentSourceRegistry.VERIFIED_INDEXERS.size,
            TorrentSourceRegistry.enabledIndexers(emptySet()).size
        )
    }

    @Test
    fun `isVerifiedProvider matches known labels and rejects unknown`() {
        assertTrue(TorrentSourceRegistry.isVerifiedProvider("YTS"))
        assertTrue(TorrentSourceRegistry.isVerifiedProvider("SolidTorrents"))
        assertTrue(TorrentSourceRegistry.isVerifiedProvider("1337x"))
        assertTrue(TorrentSourceRegistry.isVerifiedProvider("ThePirateBay"))
        assertFalse(TorrentSourceRegistry.isVerifiedProvider(""))
        assertFalse(TorrentSourceRegistry.isVerifiedProvider("evil-mirror-xyz"))
    }

    @Test
    fun `info hash validation accepts v1 hex and base32, rejects placeholders`() {
        assertTrue(MagnetParser.isValidInfoHash(hashA))
        assertTrue(MagnetParser.isValidInfoHash(hashA.uppercase()))
        assertTrue(MagnetParser.isValidInfoHash("JBSWY3DPEHPK3PXPJBSWY3DPEHPK3PXP"))
        assertFalse(MagnetParser.isValidInfoHash(null))
        assertFalse(MagnetParser.isValidInfoHash(""))
        assertFalse(MagnetParser.isValidInfoHash("abc123"))
        assertFalse(MagnetParser.isValidInfoHash("0".repeat(40)))
        assertFalse(MagnetParser.isValidInfoHash("hash_19abcdef"))
        assertEquals(hashA, MagnetParser.normalizeInfoHash("  $hashA  "))
    }

    @Test
    fun `solidtorrents parser keeps valid results and skips bad hashes`() {
        val body = """
            {"results": [
              {"title": "Dune Part Two 2024 1080p BluRay", "magnet": "magnet:?xt=urn:btih:$hashA&dn=dune", "size": 2147483648, "seeders": 120, "leechers": 10},
              {"title": "Bogus entry", "magnet": "magnet:?xt=urn:btih:short&dn=bogus", "size": 100, "seeders": 9999, "leechers": 0},
              {"title": "Size string entry", "info_hash": "$hashB", "size": "1.4 GB", "seeders": 5, "leechers": 1}
            ]}
        """.trimIndent()
        val out = TorrentIndexerService.parseSolidTorrentsBody(body)
        assertEquals(2, out.size)
        assertTrue(out.all { it.isVerified })
        assertEquals(TorrentSourceRegistry.SOLID_TORRENTS, out.first().indexerId)
        assertEquals(120, out.first().seeders)
        assertEquals(2147483648L, out.first().sizeBytes)
    }

    @Test
    fun `nyaa rss parser reads namespaced fields and skips hashless items`() {
        val xml = """
            <rss version="2.0" xmlns:nyaa="https://nyaa.si/xmlns/nyaa"><channel>
              <item><title>[Subs] Dune S01E01 1080p</title><link>https://nyaa.si/download/1.torrent</link>
                <nyaa:infoHash>$hashA</nyaa:infoHash><nyaa:size>1.2 GiB</nyaa:size>
                <nyaa:seeders>42</nyaa:seeders><nyaa:leechers>3</nyaa:leechers></item>
              <item><title>No hash here</title><link>https://nyaa.si/download/2.torrent</link>
                <nyaa:size>700 MiB</nyaa:size><nyaa:seeders>1</nyaa:seeders></item>
            </channel></rss>
        """.trimIndent()
        val out = TorrentIndexerService.parseNyaaRss(xml, season = 1, episode = 1)
        assertEquals(1, out.size)
        assertEquals(hashA, out.single().infoHash)
        assertEquals("Nyaa", out.single().provider)
        assertTrue(out.single().isVerified)
        assertEquals(1, out.single().season)
        assertEquals("https://nyaa.si/download/1.torrent", out.single().torrentFileUrl)
    }

    @Test
    fun `animetosho parser handles array payload with alias fields`() {
        val body = """
            [{"title": "Dune 2024 2160p", "link_magnet": "magnet:?xt=urn:btih:$hashB&dn=dune",
              "torrent_url": "https://animetosho.org/t/1.torrent", "size": "8.4 GB", "seeders": 77}]
        """.trimIndent()
        val out = TorrentIndexerService.parseAnimeToshoBody(body)
        assertEquals(1, out.size)
        assertEquals(hashB, out.single().infoHash)
        assertEquals("AnimeTosho", out.single().provider)
        assertTrue(out.single().isVerified)
    }

    @Test
    fun `filterVerified keeps registry results and drops custom magnets`() {
        val verified = TorrentSource(
            title = "A", infoHash = hashA, magnetUri = "magnet:?xt=urn:btih:$hashA",
            provider = "SolidTorrents", indexerId = "solidtorrents", isVerified = true
        )
        val custom = TorrentSource(
            title = "B", infoHash = hashB, magnetUri = "magnet:?xt=urn:btih:$hashB",
            provider = "Custom", indexerId = "", isVerified = false
        )
        val out = TorrentIndexerService.filterVerified(listOf(verified, custom))
        assertEquals(listOf(verified), out)
    }
}
