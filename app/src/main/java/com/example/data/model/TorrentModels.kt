package com.example.data.model

data class TorrentSource(
    val title: String,
    val infoHash: String,
    val magnetUri: String,
    val torrentFileUrl: String? = null,
    val quality: String = "1080p", // 720p, 1080p, 2160p (4K), etc.
    val releaseType: String = "BluRay", // BluRay, WEB-DL, HDTV
    val sizeBytes: Long = 0L,
    val sizeDisplay: String = "",
    val seeders: Int = 0,
    val leechers: Int = 0,
    val provider: String = "YTS", // YTS, PirateBay, EZTV, Custom
    val season: Int? = null,
    val episode: Int? = null
) {
    val healthScore: Int
        get() = when {
            seeders > 50 -> 3 // high
            seeders > 10 -> 2 // medium
            seeders > 0 -> 1 // low
            else -> 0 // dead
        }
}

data class MagnetInfo(
    val exactTopic: String, // infoHash (urn:btih:...)
    val displayName: String,
    val trackers: List<String> = emptyList(),
    val rawUri: String
)
