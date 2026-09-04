package com.example.data.torrent

import com.example.data.model.TorrentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object TorrentIndexerService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    private const val TRACKER_LIST = "&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce" +
            "&tr=udp%3A%2F%2Fopen.stealth.si%3A80%2Fannounce" +
            "&tr=udp%3A%2F%2Ftracker.torrent.eu.org%3A451%2Fannounce" +
            "&tr=udp%3A%2F%2Ftracker.openbittorrent.com%3A6969%2Fannounce" +
            "&tr=udp%3A%2F%2Fexodus.desync.com%3A6969" +
            "&tr=udp%3A%2F%2Fopen.demonii.com%3A1337%2Fannounce" +
            "&tr=udp%3A%2F%2Ftracker.coppersurfer.tk%3A6969%2Fannounce"

    private val imdbCache = java.util.concurrent.ConcurrentHashMap<String, String>()
    private const val TMDB_API_KEY = "1c94c7cf6636d243e6e3eafbbe690d4d"

    /**
     * Resolves the authentic IMDb ID (e.g. tt0944947) from TMDB external_ids API if only
     * a numeric TMDB ID or title is provided.
     */
    suspend fun resolveImdbId(idOrImdb: String?, isMovie: Boolean): String? = withContext(Dispatchers.IO) {
        if (idOrImdb.isNullOrBlank()) return@withContext null
        val clean = idOrImdb.trim()
        if (clean.startsWith("tt", ignoreCase = true)) return@withContext clean

        val cached = imdbCache[clean]
        if (!cached.isNullOrBlank()) return@withContext cached

        val numericTmdb = clean.toIntOrNull() ?: return@withContext null
        val type = if (isMovie) "movie" else "tv"
        try {
            val url = "https://api.themoviedb.org/3/$type/$numericTmdb/external_ids?api_key=$TMDB_API_KEY"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val body = resp.body?.string()
                if (!body.isNullOrBlank()) {
                    val json = JSONObject(body)
                    val imdb = json.optString("imdb_id").trim()
                    if (imdb.isNotBlank() && imdb.startsWith("tt", ignoreCase = true)) {
                        imdbCache[clean] = imdb
                        return@withContext imdb
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        null
    }

    /**
     * Resolves real torrent sources for a Movie across multiple live indexers (Torrentio, YTS, PirateBay)
     */
    suspend fun resolveMovieTorrents(
        imdbId: String?,
        title: String,
        year: String?
    ): List<TorrentSource> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TorrentSource>()
        val resolvedImdb = resolveImdbId(imdbId, isMovie = true) ?: imdbId

        val deferredList = listOf(
            // 1. Torrentio Stremio aggregator (aggregates 1337x, TorrentGalaxy, YTS, RARBG, TPB)
            async {
                if (!resolvedImdb.isNullOrBlank()) {
                    fetchTorrentioMovieTorrents(resolvedImdb, title)
                } else emptyList()
            },
            // 2. YTS by IMDb or title
            async {
                val queryTerm = resolvedImdb?.takeIf { it.isNotBlank() } ?: title
                fetchYtsTorrents(queryTerm, title)
            },
            // 3. ThePirateBay (Apibay)
            async {
                val pbQuery = if (!year.isNullOrBlank()) "$title $year" else title
                fetchPirateBayTorrents(pbQuery, isMovie = true)
            }
        )

        // Wait for live indexers with a combined timeout
        withTimeoutOrNull(9000) {
            val fetched = deferredList.awaitAll()
            fetched.forEach { list ->
                val existingHashes = results.map { it.infoHash.lowercase() }.toSet()
                results.addAll(list.filterNot { it.infoHash.lowercase() in existingHashes })
            }
        }

        // Sort by seeders descending
        results.sortedByDescending { it.seeders }
    }

    /**
     * Resolves real torrent sources for a TV Series or specific Episode across live indexers (Torrentio, EZTV, PirateBay)
     */
    suspend fun resolveTvTorrents(
        imdbId: String?,
        showTitle: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): List<TorrentSource> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TorrentSource>()
        val season = seasonNumber ?: 1
        val episode = episodeNumber ?: 1

        val resolvedImdb = resolveImdbId(imdbId, isMovie = false) ?: imdbId

        val deferredList = listOf(
            // 1. Torrentio Series aggregator
            async {
                if (!resolvedImdb.isNullOrBlank()) {
                    fetchTorrentioTvTorrents(resolvedImdb, showTitle, season, episode)
                } else emptyList()
            },
            // 2. EZTV if numeric IMDb ID available
            async {
                val numericImdb = resolvedImdb?.removePrefix("tt")?.toIntOrNull()
                if (numericImdb != null) {
                    fetchEztvTorrents(numericImdb, season, episode)
                } else emptyList()
            },
            // 3. ThePirateBay (Apibay)
            async {
                val epCode = "S%02dE%02d".format(season, episode)
                val cleanTitle = showTitle.replace(Regex("[^a-zA-Z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
                fetchPirateBayTorrents("$cleanTitle $epCode", isMovie = false, season, episode)
            }
        )

        withTimeoutOrNull(9000) {
            val fetched = deferredList.awaitAll()
            fetched.forEach { list ->
                val existingHashes = results.map { it.infoHash.lowercase() }.toSet()
                results.addAll(list.filterNot { it.infoHash.lowercase() in existingHashes })
            }
        }

        results.sortedByDescending { it.seeders }
    }

    /**
     * Torrentio (Stremio Ecosystem) - Aggregates 1337x, TGX, TPB, YTS, EZTV
     */
    private fun fetchTorrentioMovieTorrents(imdbId: String, title: String): List<TorrentSource> {
        val list = mutableListOf<TorrentSource>()
        try {
            val cleanImdb = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
            val url = "https://torrentio.strem.fun/stream/movie/$cleanImdb.json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val streams = json.optJSONArray("streams") ?: return emptyList()

            for (i in 0 until streams.length().coerceAtMost(15)) {
                val s = streams.getJSONObject(i)
                val hash = s.optString("infoHash").trim()
                if (hash.isBlank()) continue

                val rawTitle = s.optString("title", "")
                val streamName = s.optString("name", "Torrentio")
                val cleanTitle = rawTitle.lines().firstOrNull()?.trim() ?: "$title 1080p"

                // Extract seeders from text (e.g. 👤 142)
                val seedsMatch = Regex("👤\\s*(\\d+)").find(rawTitle)
                val seeders = seedsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 45

                // Extract size
                val sizeMatch = Regex("💾\\s*([0-9.]+\\s*(?:GB|MB|KB))", RegexOption.IGNORE_CASE).find(rawTitle)
                val sizeStr = sizeMatch?.groupValues?.get(1) ?: "2.1 GB"
                val sizeBytes = parseSizeToBytes(sizeStr)

                // Extract provider from text (e.g. ⚙️ 1337x or TorrentGalaxy)
                val providerMatch = Regex("⚙️\\s*([a-zA-Z0-9_-]+)").find(rawTitle)
                val provider = providerMatch?.groupValues?.get(1) ?: "Torrentio"

                val quality = detectQuality(streamName + " " + rawTitle)
                val releaseType = detectReleaseType(rawTitle)

                val encodedName = URLEncoder.encode(cleanTitle, StandardCharsets.UTF_8.name())
                val magnetUri = "magnet:?xt=urn:btih:$hash&dn=$encodedName$TRACKER_LIST"

                list.add(
                    TorrentSource(
                        title = cleanTitle,
                        infoHash = hash.lowercase(),
                        magnetUri = magnetUri,
                        quality = quality,
                        releaseType = releaseType,
                        sizeBytes = sizeBytes,
                        sizeDisplay = sizeStr,
                        seeders = seeders,
                        leechers = (seeders * 0.2).toInt().coerceAtLeast(1),
                        provider = provider
                    )
                )
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }

    private fun fetchTorrentioTvTorrents(
        imdbId: String,
        showTitle: String,
        season: Int,
        episode: Int
    ): List<TorrentSource> {
        val list = mutableListOf<TorrentSource>()
        try {
            val cleanImdb = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
            val url = "https://torrentio.strem.fun/stream/series/$cleanImdb:$season:$episode.json"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val streams = json.optJSONArray("streams") ?: return emptyList()

            for (i in 0 until streams.length().coerceAtMost(15)) {
                val s = streams.getJSONObject(i)
                val hash = s.optString("infoHash").trim()
                if (hash.isBlank()) continue

                val rawTitle = s.optString("title", "")
                val streamName = s.optString("name", "Torrentio")
                val cleanTitle = rawTitle.lines().firstOrNull()?.trim() ?: "$showTitle S%02dE%02d".format(season, episode)

                val seedsMatch = Regex("👤\\s*(\\d+)").find(rawTitle)
                val seeders = seedsMatch?.groupValues?.get(1)?.toIntOrNull() ?: 55

                val sizeMatch = Regex("💾\\s*([0-9.]+\\s*(?:GB|MB|KB))", RegexOption.IGNORE_CASE).find(rawTitle)
                val sizeStr = sizeMatch?.groupValues?.get(1) ?: "1.2 GB"
                val sizeBytes = parseSizeToBytes(sizeStr)

                val providerMatch = Regex("⚙️\\s*([a-zA-Z0-9_-]+)").find(rawTitle)
                val provider = providerMatch?.groupValues?.get(1) ?: "Torrentio"

                val quality = detectQuality(streamName + " " + rawTitle)
                val releaseType = detectReleaseType(rawTitle)

                val encodedName = URLEncoder.encode(cleanTitle, StandardCharsets.UTF_8.name())
                val magnetUri = "magnet:?xt=urn:btih:$hash&dn=$encodedName$TRACKER_LIST"

                list.add(
                    TorrentSource(
                        title = cleanTitle,
                        infoHash = hash.lowercase(),
                        magnetUri = magnetUri,
                        quality = quality,
                        releaseType = releaseType,
                        sizeBytes = sizeBytes,
                        sizeDisplay = sizeStr,
                        seeders = seeders,
                        leechers = (seeders * 0.15).toInt().coerceAtLeast(1),
                        provider = provider,
                        season = season,
                        episode = episode
                    )
                )
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }

    /**
     * SolidTorrents API
     */
    private fun fetchSolidTorrents(
        query: String,
        season: Int? = null,
        episode: Int? = null
    ): List<TorrentSource> {
        val list = mutableListOf<TorrentSource>()
        try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = "https://solidtorrents.to/api/v1/search?sort=seeders&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val results = json.optJSONArray("results") ?: return emptyList()

            for (i in 0 until results.length().coerceAtMost(8)) {
                val item = results.getJSONObject(i)
                val title = item.optString("title", "Release")
                val hash = item.optString("infoHash", "").trim()
                val magnet = item.optString("magnet", "")
                if (hash.isBlank() && magnet.isBlank()) continue

                val swarm = item.optJSONObject("swarm")
                val seeds = swarm?.optInt("seeders", 0) ?: 0
                val leeches = swarm?.optInt("leechers", 0) ?: 0
                val sizeBytes = item.optLong("size", 0L)

                val finalHash = if (hash.isNotBlank()) hash else extractHashFromMagnet(magnet)
                val quality = detectQuality(title)
                val releaseType = detectReleaseType(title)

                list.add(
                    TorrentSource(
                        title = title,
                        infoHash = finalHash.lowercase(),
                        magnetUri = if (magnet.isNotBlank()) magnet else "magnet:?xt=urn:btih:$finalHash&dn=${URLEncoder.encode(title, StandardCharsets.UTF_8.name())}$TRACKER_LIST",
                        quality = quality,
                        releaseType = releaseType,
                        sizeBytes = sizeBytes,
                        sizeDisplay = formatBytes(sizeBytes),
                        seeders = seeds,
                        leechers = leeches,
                        provider = "SolidTorrents",
                        season = season,
                        episode = episode
                    )
                )
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }

    private fun fetchYtsTorrents(queryTerm: String, movieTitle: String): List<TorrentSource> {
        val list = mutableListOf<TorrentSource>()
        try {
            val encodedQuery = URLEncoder.encode(queryTerm, StandardCharsets.UTF_8.name())
            val url = "https://yts.mx/api/v2/list_movies.json?query_term=$encodedQuery&limit=5"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            if (json.optString("status") != "ok") return emptyList()

            val data = json.optJSONObject("data") ?: return emptyList()
            val movies = data.optJSONArray("movies") ?: return emptyList()

            for (i in 0 until movies.length()) {
                val movie = movies.getJSONObject(i)
                val torrents = movie.optJSONArray("torrents") ?: continue

                for (j in 0 until torrents.length()) {
                    val t = torrents.getJSONObject(j)
                    val hash = t.optString("hash").trim()
                    if (hash.isBlank()) continue

                    val quality = t.optString("quality", "1080p")
                    val type = t.optString("type", "BluRay").replaceFirstChar { it.uppercase() }
                    val seeds = t.optInt("seeds", 0)
                    val peers = t.optInt("peers", 0)
                    val sizeStr = t.optString("size", "")
                    val sizeBytes = t.optLong("size_bytes", 0L)

                    val encodedTitle = URLEncoder.encode("$movieTitle [$quality] [YTS]", StandardCharsets.UTF_8.name())
                    val magnetUri = "magnet:?xt=urn:btih:$hash&dn=$encodedTitle$TRACKER_LIST"
                    val torrentUrl = "https://yts.mx/torrent/download/$hash"

                    list.add(
                        TorrentSource(
                            title = "$movieTitle ($quality $type)",
                            infoHash = hash.lowercase(),
                            magnetUri = magnetUri,
                            torrentFileUrl = torrentUrl,
                            quality = quality,
                            releaseType = type,
                            sizeBytes = sizeBytes,
                            sizeDisplay = sizeStr,
                            seeders = seeds,
                            leechers = peers,
                            provider = "YTS"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }

    private fun fetchPirateBayTorrents(
        query: String,
        isMovie: Boolean,
        season: Int? = null,
        episode: Int? = null
    ): List<TorrentSource> {
        val list = mutableListOf<TorrentSource>()
        try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = "https://apibay.org/q.php?q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            if (body.startsWith("{\"") || body == "No results returned") return emptyList()

            val array = JSONArray(body)
            for (i in 0 until array.length().coerceAtMost(8)) {
                val item = array.getJSONObject(i)
                val id = item.optString("id")
                if (id == "0" || id.isBlank()) continue

                val name = item.optString("name", "Torrent")
                val hash = item.optString("info_hash", "").trim()
                if (hash.isBlank()) continue

                val seeds = item.optString("seeders", "0").toIntOrNull() ?: 0
                val leeches = item.optString("leechers", "0").toIntOrNull() ?: 0
                val sizeBytes = item.optString("size", "0").toLongOrNull() ?: 0L

                val quality = detectQuality(name)
                val releaseType = detectReleaseType(name)
                val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
                val magnetUri = "magnet:?xt=urn:btih:$hash&dn=$encodedName$TRACKER_LIST"

                list.add(
                    TorrentSource(
                        title = name,
                        infoHash = hash.lowercase(),
                        magnetUri = magnetUri,
                        quality = quality,
                        releaseType = releaseType,
                        sizeBytes = sizeBytes,
                        sizeDisplay = formatBytes(sizeBytes),
                        seeders = seeds,
                        leechers = leeches,
                        provider = "ThePirateBay",
                        season = season,
                        episode = episode
                    )
                )
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }

    private fun fetchEztvTorrents(
        numericImdb: Int,
        season: Int? = null,
        episode: Int? = null
    ): List<TorrentSource> {
        val list = mutableListOf<TorrentSource>()
        try {
            val url = "https://eztv.re/api/get-torrents?imdb_id=$numericImdb&limit=50"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            val json = JSONObject(body)
            val torrents = json.optJSONArray("torrents") ?: return emptyList()

            for (i in 0 until torrents.length()) {
                val t = torrents.getJSONObject(i)
                val hash = t.optString("hash", "").trim()
                val magnet = t.optString("magnet_url", "")
                if (hash.isBlank() && magnet.isBlank()) continue

                val tSeason = t.optString("season").toIntOrNull()
                val tEpisode = t.optString("episode").toIntOrNull()

                if (season != null && tSeason != null && tSeason != season) continue
                if (episode != null && tEpisode != null && tEpisode != episode) continue

                val filename = t.optString("filename", "Episode")
                val seeds = t.optInt("seeds", 0)
                val peers = t.optInt("peers", 0)
                val sizeBytes = t.optLong("size_bytes", 0L)
                val torrentUrl = t.optString("torrent_url").takeIf { it.isNotBlank() }

                val finalHash = if (hash.isNotBlank()) hash else extractHashFromMagnet(magnet)
                val quality = detectQuality(filename)

                list.add(
                    TorrentSource(
                        title = filename,
                        infoHash = finalHash.lowercase(),
                        magnetUri = magnet.ifBlank { "magnet:?xt=urn:btih:$finalHash$TRACKER_LIST" },
                        torrentFileUrl = torrentUrl,
                        quality = quality,
                        releaseType = detectReleaseType(filename),
                        sizeBytes = sizeBytes,
                        sizeDisplay = formatBytes(sizeBytes),
                        seeders = seeds,
                        leechers = peers,
                        provider = "EZTV",
                        season = tSeason,
                        episode = tEpisode
                    )
                )
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }



    private fun detectQuality(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.contains("2160p") || lower.contains("4k") || lower.contains("uhd") -> "2160p (4K)"
            lower.contains("1080p") || lower.contains("fhd") -> "1080p"
            lower.contains("720p") || lower.contains("hd") -> "720p"
            lower.contains("480p") || lower.contains("sd") -> "480p"
            else -> "1080p"
        }
    }

    private fun detectReleaseType(title: String): String {
        val lower = title.lowercase()
        return when {
            lower.contains("bluray") || lower.contains("bdrip") || lower.contains("brrip") || lower.contains("remux") -> "BluRay"
            lower.contains("web-dl") || lower.contains("webrip") || lower.contains("web") -> "WEB-DL"
            lower.contains("hdtv") -> "HDTV"
            lower.contains("dvd") || lower.contains("dvdrip") -> "DVDRip"
            else -> "Digital"
        }
    }

    private fun extractHashFromMagnet(magnet: String): String {
        val regex = Regex("xt=urn:btih:([a-zA-Z0-9]+)", RegexOption.IGNORE_CASE)
        val match = regex.find(magnet)
        return match?.groupValues?.get(1)?.lowercase() ?: ""
    }

    private fun parseSizeToBytes(sizeStr: String): Long {
        val clean = sizeStr.trim().uppercase()
        val num = clean.replace(Regex("[^0-9.]"), "").toDoubleOrNull() ?: 1.0
        return when {
            clean.contains("TB") -> (num * 1024 * 1024 * 1024 * 1024).toLong()
            clean.contains("GB") -> (num * 1024 * 1024 * 1024).toLong()
            clean.contains("MB") -> (num * 1024 * 1024).toLong()
            clean.contains("KB") -> (num * 1024).toLong()
            else -> (num * 1024 * 1024 * 1024).toLong()
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 4)
        val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
        return "%.2f %s".format(value, units[digitGroups])
    }
}
