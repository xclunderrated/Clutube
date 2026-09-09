package com.example.data.torrent

/**
 * Registry of torrent indexers the app queries. Every entry is a known,
 * long-lived public indexer — results from these hosts are surfaced as
 * "verified" in the UI. Unknown/aggregated sub-providers (e.g. Torrentio's
 * embedded 1337x/TGX tags) inherit verification from their parent indexer.
 *
 * To add a new source: append a [VerifiedIndexer] here, implement a fetcher
 * in [TorrentIndexerService], and add it to the resolve fan-out. No other
 * call sites need to change.
 */
data class VerifiedIndexer(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val supportsMovies: Boolean = true,
    val supportsTv: Boolean = true,
    val enabledByDefault: Boolean = true
)

object TorrentSourceRegistry {
    const val TORRENTIO = "torrentio"
    const val YTS = "yts"
    const val PIRATE_BAY = "piratebay"
    const val EZTV = "eztv"
    const val SOLID_TORRENTS = "solidtorrents"
    const val NYAA = "nyaa"
    const val ANIME_TOSHO = "animetosho"

    val VERIFIED_INDEXERS: List<VerifiedIndexer> = listOf(
        VerifiedIndexer(
            id = TORRENTIO,
            displayName = "Torrentio",
            baseUrl = "https://torrentio.strem.fun",
            supportsMovies = true,
            supportsTv = true
        ),
        VerifiedIndexer(
            id = YTS,
            displayName = "YTS",
            baseUrl = "https://yts.mx",
            supportsMovies = true,
            supportsTv = false
        ),
        VerifiedIndexer(
            id = PIRATE_BAY,
            displayName = "ThePirateBay",
            baseUrl = "https://apibay.org",
            supportsMovies = true,
            supportsTv = true
        ),
        VerifiedIndexer(
            id = EZTV,
            displayName = "EZTV",
            baseUrl = "https://eztv.re",
            supportsMovies = false,
            supportsTv = true
        ),
        VerifiedIndexer(
            id = SOLID_TORRENTS,
            displayName = "SolidTorrents",
            baseUrl = "https://solidtorrents.to",
            supportsMovies = true,
            supportsTv = true
        ),
        VerifiedIndexer(
            id = NYAA,
            displayName = "Nyaa",
            baseUrl = "https://nyaa.si",
            supportsMovies = true,
            supportsTv = true
        ),
        VerifiedIndexer(
            id = ANIME_TOSHO,
            displayName = "AnimeTosho",
            baseUrl = "https://feed.animetosho.org",
            supportsMovies = true,
            supportsTv = true
        )
    )

    /**
     * Provider labels that count as verified in the UI. Keys are matched
     * case-insensitively; Torrentio's embedded sub-provider tags (1337x,
     * TorrentGalaxy, etc.) are verified through their parent aggregator.
     */
    private val VERIFIED_PROVIDERS: Set<String> = setOf(
        "torrentio", "yts", "thepiratebay", "piratebay", "eztv",
        "solidtorrents", "nyaa", "animetosho",
        "1337x", "torrentgalaxy", "tgx", "eztv", "yts", "tpb"
    )

    fun isKnownIndexer(id: String): Boolean =
        VERIFIED_INDEXERS.any { it.id.equals(id.trim(), ignoreCase = true) }

    fun indexerById(id: String): VerifiedIndexer? =
        VERIFIED_INDEXERS.firstOrNull { it.id.equals(id.trim(), ignoreCase = true) }

    fun isVerifiedProvider(provider: String): Boolean {
        val clean = provider.trim().lowercase().replace(Regex("[^a-z0-9]"), "")
        if (clean.isBlank()) return false
        return VERIFIED_PROVIDERS.any { known ->
            val k = known.lowercase().replace(Regex("[^a-z0-9]"), "")
            clean == k || clean.contains(k) || k.contains(clean)
        }
    }

    fun defaultEnabledIds(): Set<String> =
        VERIFIED_INDEXERS.filter { it.enabledByDefault }.map { it.id }.toSet()

    /**
     * Self-healing order, mirroring StreamService.normalizeVidSrcServerOrder:
     * drops unknown/duplicate ids and appends newly added official indexers.
     */
    fun normalizeIndexerOrder(order: List<String>): List<String> {
        val known = VERIFIED_INDEXERS.map { it.id }
        val cleaned = order.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
        val kept = cleaned.filter { candidate -> known.any { it.equals(candidate, ignoreCase = true) } }
            .map { candidate -> known.first { it.equals(candidate, ignoreCase = true) } }
        return (kept + known).distinct()
    }

    fun enabledIndexers(disabledIds: Set<String>): Set<String> {
        val disabled = disabledIds.map { it.trim().lowercase() }.toSet()
        return VERIFIED_INDEXERS.map { it.id }.filter { it.lowercase() !in disabled }.toSet()
    }
}
