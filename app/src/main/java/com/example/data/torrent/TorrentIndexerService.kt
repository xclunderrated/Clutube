package com.example.data.torrent

import com.example.data.model.TorrentSource
import kotlinx.coroutines.Deferred
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
     * Resolves real torrent sources for a Movie across all verified live
     * indexers (see TorrentSourceRegistry). Pass [enabledIndexers] to honor
     * user-disabled sources; null/empty means all.
     */
    /**
     * Partitioned TV result: exact single-episode torrents vs packs that may
     * contain the episode. Auto-download only ever uses [exact]; the picker
     * surfaces [packs] in a separate collapsed section for manual file-pick.
     */
    data class TvTorrentResult(
        val exact: List<TorrentSource>,
        val packs: List<TorrentSource>
    )

    suspend fun resolveMovieTorrents(
        imdbId: String?,
        title: String,
        year: String?,
        enabledIndexers: Set<String>? = null
    ): List<TorrentSource> = withContext(Dispatchers.IO) {
        val results = mutableListOf<TorrentSource>()
        val resolvedImdb = resolveImdbId(imdbId, isMovie = true) ?: imdbId
        val enabled = enabledIndexers?.map { it.trim().lowercase() }?.toSet().orEmpty()
        fun on(id: String) = enabled.isEmpty() || id.lowercase() in enabled
        // Canonical query title: strip " (YEAR)" suffix so "Dune (2024) 2024"
        // duplication can never happen; year is appended once below.
        val cleanQueryTitle = TorrentMatcher.queryTitle(title).ifBlank { title.trim() }
        val cleanYear = year?.trim()?.takeIf { it.matches(Regex(".*(19\\d{2}|20\\d{2}).*")) }
            ?.let { Regex("(19\\d{2}|20\\d{2})").find(it)?.groupValues?.get(1) ?: it.take(4) }
            ?: TorrentMatcher.extractYear(year)

        val deferredList: List<Deferred<List<TorrentSource>>> = listOfNotNull(
            // 1. Torrentio Stremio aggregator (aggregates 1337x, TorrentGalaxy, YTS, RARBG, TPB)
            if (on(TorrentSourceRegistry.TORRENTIO)) async {
                if (!resolvedImdb.isNullOrBlank()) {
                    fetchTorrentioMovieTorrents(resolvedImdb, cleanQueryTitle)
                } else emptyList()
            } else null,
            // 2. YTS by IMDb or title (with mirror fallback + title/year verify)
            if (on(TorrentSourceRegistry.YTS)) async {
                val queryTerm = resolvedImdb?.takeIf { it.isNotBlank() } ?: cleanQueryTitle
                fetchYtsTorrents(queryTerm, cleanQueryTitle, cleanYear)
            } else null,
            // 3. ThePirateBay (Apibay)
            if (on(TorrentSourceRegistry.PIRATE_BAY)) async {
                val pbQuery = if (!cleanYear.isNullOrBlank()) "$cleanQueryTitle $cleanYear" else cleanQueryTitle
                fetchPirateBayTorrents(pbQuery, isMovie = true)
                    .filter { TorrentMatcher.matchMovie(it.title, cleanQueryTitle, cleanYear) }
            } else null,
            // 4. SolidTorrents DHT index
            if (on(TorrentSourceRegistry.SOLID_TORRENTS)) async {
                val q = if (!cleanYear.isNullOrBlank()) "$cleanQueryTitle $cleanYear" else cleanQueryTitle
                fetchSolidTorrents(q)
                    .filter { TorrentMatcher.matchMovie(it.title, cleanQueryTitle, cleanYear) }
            } else null,
            // 5. Nyaa RSS
            if (on(TorrentSourceRegistry.NYAA)) async {
                val q = if (!cleanYear.isNullOrBlank()) "$cleanQueryTitle $cleanYear" else cleanQueryTitle
                fetchNyaaTorrents(q)
                    .filter { TorrentMatcher.matchMovie(it.title, cleanQueryTitle, cleanYear) }
            } else null,
            // 6. AnimeTosho JSON feed
            if (on(TorrentSourceRegistry.ANIME_TOSHO)) async {
                val q = if (!cleanYear.isNullOrBlank()) "$cleanQueryTitle $cleanYear" else cleanQueryTitle
                fetchAnimeToshoTorrents(q)
                    .filter { TorrentMatcher.matchMovie(it.title, cleanQueryTitle, cleanYear) }
            } else null
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
     * Resolves real torrent sources for a TV Series or specific Episode
     * across all verified live indexers (see TorrentSourceRegistry).
     * Pass [enabledIndexers] to honor user-disabled sources; null/empty = all.
     */
    suspend fun resolveTvTorrents(
        imdbId: String?,
        showTitle: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        enabledIndexers: Set<String>? = null
    ): List<TorrentSource> = resolveTvTorrentsPartitioned(
        imdbId, showTitle, seasonNumber, episodeNumber, enabledIndexers
    ).exact

    /**
     * Strict partitioned resolution: generic indexers (TPB/Solid/Nyaa/Tosho)
     * are filtered to EXACT S/E release names; Torrentio + EZTV are trusted
     * exact by construction (imdb:S:E endpoint / S-E filtered API). Anything
     * else (packs, ranges, wrong episodes) lands in [TvTorrentResult.packs]
     * or is dropped, so auto-pick can never grab a random episode.
     */
    suspend fun resolveTvTorrentsPartitioned(
        imdbId: String?,
        showTitle: String,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        enabledIndexers: Set<String>? = null
    ): TvTorrentResult = withContext(Dispatchers.IO) {
        val season = (seasonNumber ?: 1).coerceAtLeast(1)
        val episode = (episodeNumber ?: 1).coerceAtLeast(1)

        val resolvedImdb = resolveImdbId(imdbId, isMovie = false) ?: imdbId
        val enabled = enabledIndexers?.map { it.trim().lowercase() }?.toSet().orEmpty()
        fun on(id: String) = enabled.isEmpty() || id.lowercase() in enabled
        val epCode = "S%02dE%02d".format(season, episode)
        val cleanTitle = TorrentMatcher.queryTitle(showTitle)

        // Trusted-exact indexers: no name filtering needed.
        val trustedDeferred: List<Deferred<List<TorrentSource>>> = listOfNotNull(
            // 1. Torrentio Series aggregator (exact imdb:S:E endpoint)
            if (on(TorrentSourceRegistry.TORRENTIO)) async {
                if (!resolvedImdb.isNullOrBlank()) {
                    fetchTorrentioTvTorrents(resolvedImdb, cleanTitle, season, episode)
                } else emptyList()
            } else null,
            // 2. EZTV if numeric IMDb ID available (S/E filtered server-side)
            if (on(TorrentSourceRegistry.EZTV)) async {
                val numericImdb = resolvedImdb?.removePrefix("tt")?.removePrefix("TT")?.toIntOrNull()
                if (numericImdb != null) {
                    fetchEztvTorrents(numericImdb, season, episode)
                } else emptyList()
            } else null
        )

        // Untrusted generic indexers: must prove EXACT via release-name match.
        val genericDeferred: List<Deferred<List<TorrentSource>>> = listOfNotNull(
            // 3. ThePirateBay (Apibay)
            if (on(TorrentSourceRegistry.PIRATE_BAY)) async {
                fetchPirateBayTorrents("$cleanTitle $epCode", isMovie = false, season, episode)
            } else null,
            // 4. SolidTorrents DHT index
            if (on(TorrentSourceRegistry.SOLID_TORRENTS)) async {
                fetchSolidTorrents("$cleanTitle $epCode", season, episode)
            } else null,
            // 5. Nyaa RSS
            if (on(TorrentSourceRegistry.NYAA)) async {
                fetchNyaaTorrents("$cleanTitle $epCode", season, episode)
            } else null,
            // 6. AnimeTosho JSON feed
            if (on(TorrentSourceRegistry.ANIME_TOSHO)) async {
                fetchAnimeToshoTorrents("$cleanTitle $epCode", season, episode)
            } else null
        )

        val exact = mutableListOf<TorrentSource>()
        val packs = mutableListOf<TorrentSource>()
        withTimeoutOrNull(9000) {
            val trusted = trustedDeferred.awaitAll()
            val generic = genericDeferred.awaitAll()
            val seen = mutableSetOf<String>()
            fun addUnique(dst: MutableList<TorrentSource>, src: TorrentSource) {
                val h = src.infoHash.lowercase()
                if (seen.add(h)) dst.add(src)
            }
            trusted.forEach { list -> (list ?: emptyList()).forEach { addUnique(exact, it) } }
            generic.forEach { list -> (list ?: emptyList()).forEach { src ->
                when (TorrentMatcher.matchEpisode(src.title, season, episode).kind) {
                    EpisodeMatch.EXACT -> addUnique(exact, src)
                    EpisodeMatch.PACK_SEASON,
                    EpisodeMatch.PACK_COMPLETE,
                    EpisodeMatch.MULTI -> addUnique(packs, src)
                    EpisodeMatch.WRONG -> Unit // drop: wrong episode, never surface
                }
            } }
        }

        TvTorrentResult(
            exact = exact.sortedByDescending { it.seeders },
            packs = packs.sortedByDescending { it.seeders }
        )
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
                val hash = MagnetParser.normalizeInfoHash(s.optString("infoHash")) ?: continue

                val rawTitle = s.optString("title", "")
                val streamName = s.optString("name", "Torrentio")
                val cleanTitle = rawTitle.lines().firstOrNull()?.trim() ?: "$title 1080p"

                // Extract seeders from text (e.g. 👤 142). Unknown stays
                // unknown (-1): never invent 45 seeds.
                val seedsMatch = Regex("👤\\s*(\\d+)").find(rawTitle)
                val seeders = seedsMatch?.groupValues?.get(1)?.toIntOrNull() ?: -1

                // Extract size. Unknown stays "—" with 0 bytes.
                val sizeMatch = Regex("💾\\s*([0-9.]+\\s*(?:GB|MB|KB))", RegexOption.IGNORE_CASE).find(rawTitle)
                val sizeStr = sizeMatch?.groupValues?.get(1) ?: "—"
                val sizeBytes = sizeMatch?.let { parseSizeToBytes(it.groupValues[1]) } ?: 0L

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
                        infoHash = hash,
                        magnetUri = magnetUri,
                        quality = quality,
                        releaseType = releaseType,
                        sizeBytes = sizeBytes,
                        sizeDisplay = sizeStr,
                        seeders = seeders,
                        leechers = if (seeders >= 0) (seeders * 0.2).toInt().coerceAtLeast(1) else 0,
                        provider = provider,
                        indexerId = TorrentSourceRegistry.TORRENTIO,
                        isVerified = true
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
                val hash = MagnetParser.normalizeInfoHash(s.optString("infoHash")) ?: continue

                val rawTitle = s.optString("title", "")
                val streamName = s.optString("name", "Torrentio")
                val cleanTitle = rawTitle.lines().firstOrNull()?.trim() ?: "$showTitle S%02dE%02d".format(season, episode)

                val seedsMatch = Regex("👤\\s*(\\d+)").find(rawTitle)
                val seeders = seedsMatch?.groupValues?.get(1)?.toIntOrNull() ?: -1

                val sizeMatch = Regex("💾\\s*([0-9.]+\\s*(?:GB|MB|KB))", RegexOption.IGNORE_CASE).find(rawTitle)
                val sizeStr = sizeMatch?.groupValues?.get(1) ?: "—"
                val sizeBytes = sizeMatch?.let { parseSizeToBytes(it.groupValues[1]) } ?: 0L

                val providerMatch = Regex("⚙️\\s*([a-zA-Z0-9_-]+)").find(rawTitle)
                val provider = providerMatch?.groupValues?.get(1) ?: "Torrentio"

                val quality = detectQuality(streamName + " " + rawTitle)
                val releaseType = detectReleaseType(rawTitle)

                val encodedName = URLEncoder.encode(cleanTitle, StandardCharsets.UTF_8.name())
                val magnetUri = "magnet:?xt=urn:btih:$hash&dn=$encodedName$TRACKER_LIST"

                list.add(
                    TorrentSource(
                        title = cleanTitle,
                        infoHash = hash,
                        magnetUri = magnetUri,
                        quality = quality,
                        releaseType = releaseType,
                        sizeBytes = sizeBytes,
                        sizeDisplay = sizeStr,
                        seeders = seeders,
                        leechers = if (seeders >= 0) (seeders * 0.15).toInt().coerceAtLeast(1) else 0,
                        provider = provider,
                        season = season,
                        episode = episode,
                        indexerId = TorrentSourceRegistry.TORRENTIO,
                        isVerified = true
                    )
                )
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }

    private val YTS_MIRRORS = listOf("https://yts.mx", "https://yts.am")

    private fun fetchYtsTorrents(
        queryTerm: String,
        movieTitle: String,
        movieYear: String? = null
    ): List<TorrentSource> {
        for (mirror in YTS_MIRRORS) {
            val found = fetchYtsFromMirror(mirror, queryTerm, movieTitle, movieYear)
            if (found.isNotEmpty()) return found
        }
        return emptyList()
    }

    private fun fetchYtsFromMirror(
        mirror: String,
        queryTerm: String,
        movieTitle: String,
        movieYear: String? = null
    ): List<TorrentSource> {
        val list = mutableListOf<TorrentSource>()
        try {
            val encodedQuery = URLEncoder.encode(queryTerm, StandardCharsets.UTF_8.name())
            val url = "$mirror/api/v2/list_movies.json?query_term=$encodedQuery&limit=5"
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
                // Strict verify: YTS fuzzy search can return unrelated same-word
                // titles ("Avatar" -> many Avatar movies). Only keep the
                // closest title/year match so a random movie is never queued.
                val ytsTitle = movie.optString("title_english")
                    .ifBlank { movie.optString("title", "") }
                val ytsYear = movie.optInt("year", 0).takeIf { it > 1900 }?.toString()
                if (!TorrentMatcher.matchMovie(
                        "$ytsTitle ${ytsYear ?: ""}",
                        movieTitle,
                        movieYear ?: ytsYear
                    ) && i > 0
                ) {
                    // Keep first result only when IMDb id drove the query
                    // (queryTerm looks like tt1234567); otherwise skip fuzzy extras.
                    val imdbDriven = queryTerm.trim().startsWith("tt", ignoreCase = true)
                    if (!imdbDriven) continue
                }
                val torrents = movie.optJSONArray("torrents") ?: continue

                for (j in 0 until torrents.length()) {
                    val t = torrents.getJSONObject(j)
                    val hash = MagnetParser.normalizeInfoHash(t.optString("hash")) ?: continue

                    val quality = t.optString("quality", "1080p")
                    val type = t.optString("type", "BluRay").replaceFirstChar { it.uppercase() }
                    val seeds = t.optInt("seeds", 0)
                    val peers = t.optInt("peers", 0)
                    val sizeStr = t.optString("size", "")
                    val sizeBytes = t.optLong("size_bytes", 0L)

                    val encodedTitle = URLEncoder.encode("$movieTitle [$quality] [YTS]", StandardCharsets.UTF_8.name())
                    val magnetUri = "magnet:?xt=urn:btih:$hash&dn=$encodedTitle$TRACKER_LIST"
                    val torrentUrl = "$mirror/torrent/download/$hash"

                    list.add(
                        TorrentSource(
                            title = "$movieTitle ($quality $type)",
                            infoHash = hash,
                            magnetUri = magnetUri,
                            torrentFileUrl = torrentUrl,
                            quality = quality,
                            releaseType = type,
                            sizeBytes = sizeBytes,
                            sizeDisplay = sizeStr,
                            seeders = seeds,
                            leechers = peers,
                            provider = "YTS",
                            indexerId = TorrentSourceRegistry.YTS,
                            isVerified = true
                        )
                    )
                }
            }
        } catch (e: Exception) {
            // ignore — caller tries the next mirror
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
            // Wider pool (15): exact S/E filtering happens at the caller, so
            // a wrong-episode result in the top 8 must not crowd out the exact
            // match sitting at position 9-15.
            for (i in 0 until array.length().coerceAtMost(15)) {
                val item = array.getJSONObject(i)
                val id = item.optString("id")
                if (id == "0" || id.isBlank()) continue

                val name = item.optString("name", "Torrent")
                val hash = MagnetParser.normalizeInfoHash(item.optString("info_hash", "")) ?: continue

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
                        infoHash = hash,
                        magnetUri = magnetUri,
                        quality = quality,
                        releaseType = releaseType,
                        sizeBytes = sizeBytes,
                        sizeDisplay = formatBytes(sizeBytes),
                        seeders = seeds,
                        leechers = leeches,
                        provider = "ThePirateBay",
                        season = season,
                        episode = episode,
                        indexerId = TorrentSourceRegistry.PIRATE_BAY,
                        isVerified = true
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
                val magnet = t.optString("magnet_url", "")
                val rawHash = t.optString("hash", "").trim().ifBlank { extractHashFromMagnet(magnet) }
                val hash = MagnetParser.normalizeInfoHash(rawHash) ?: continue

                val tSeason = t.optString("season").toIntOrNull()
                val tEpisode = t.optString("episode").toIntOrNull()

                if (season != null && tSeason != null && tSeason != season) continue
                if (episode != null && tEpisode != null && tEpisode != episode) continue

                val filename = t.optString("filename", "Episode")
                val seeds = t.optInt("seeds", 0)
                val peers = t.optInt("peers", 0)
                val sizeBytes = t.optLong("size_bytes", 0L)
                val torrentUrl = t.optString("torrent_url").takeIf { it.isNotBlank() }

                val quality = detectQuality(filename)

                list.add(
                    TorrentSource(
                        title = filename,
                        infoHash = hash,
                        magnetUri = magnet.ifBlank { "magnet:?xt=urn:btih:$hash$TRACKER_LIST" },
                        torrentFileUrl = torrentUrl,
                        quality = quality,
                        releaseType = detectReleaseType(filename),
                        sizeBytes = sizeBytes,
                        sizeDisplay = formatBytes(sizeBytes),
                        seeders = seeds,
                        leechers = peers,
                        provider = "EZTV",
                        season = tSeason,
                        episode = tEpisode,
                        indexerId = TorrentSourceRegistry.EZTV,
                        isVerified = true
                    )
                )
            }
        } catch (e: Exception) {
            // ignore
        }
        return list
    }



    // ------------------------------------------------------------------
    // New verified indexers (see TorrentSourceRegistry)
    // ------------------------------------------------------------------

    /**
     * SolidTorrents DHT index — public JSON API, no key required.
     * Pure parsing lives in [parseSolidTorrentsBody] so it stays unit-testable.
     */
    fun fetchSolidTorrents(
        query: String,
        season: Int? = null,
        episode: Int? = null
    ): List<TorrentSource> {
        return try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = "https://solidtorrents.to/api/v1/search/$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            parseSolidTorrentsBody(body, season, episode)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseSolidTorrentsBody(
        body: String,
        season: Int? = null,
        episode: Int? = null
    ): List<TorrentSource> {
        val list = mutableListOf<TorrentSource>()
        try {
            val root = JSONObject(body)
            val results = root.optJSONArray("results")
                ?: root.optJSONArray("torrents")
                ?: return emptyList()
            for (i in 0 until results.length().coerceAtMost(10)) {
                val item = results.optJSONObject(i) ?: continue
                val title = item.optString("title").ifBlank { item.optString("name", "Torrent") }
                val magnet = item.optString("magnet").ifBlank { item.optString("magnet_uri", "") }
                    .ifBlank { item.optString("magnetLink", "") }
                val rawHash = item.optString("infohash", "").ifBlank { item.optString("info_hash", "") }
                    .ifBlank { item.optString("hash", "") }
                    .ifBlank { extractHashFromMagnet(magnet) }
                val hash = MagnetParser.normalizeInfoHash(rawHash) ?: continue

                val seeders = item.optInt("seeders", -1).takeIf { it >= 0 }
                    ?: item.optString("seeders", "0").toIntOrNull()
                    ?: item.optInt("seeds", 0)
                val leechers = item.optInt("leechers", -1).takeIf { it >= 0 }
                    ?: item.optString("leechers", "0").toIntOrNull()
                    ?: item.optInt("peers", 0)
                val sizeBytes = item.optLong("size", -1L).takeIf { it >= 0 }
                    ?: item.optString("size", "0").toLongOrNull()
                    ?: parseSizeToBytes(item.optString("size", "0"))

                val quality = detectQuality(title)
                val encodedName = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
                val magnetUri = magnet.ifBlank { "magnet:?xt=urn:btih:$hash&dn=$encodedName$TRACKER_LIST" }
                list.add(
                    TorrentSource(
                        title = title,
                        infoHash = hash,
                        magnetUri = magnetUri,
                        quality = quality,
                        releaseType = detectReleaseType(title),
                        sizeBytes = sizeBytes,
                        sizeDisplay = formatBytes(sizeBytes),
                        seeders = seeders,
                        leechers = leechers,
                        provider = "SolidTorrents",
                        season = season,
                        episode = episode,
                        indexerId = TorrentSourceRegistry.SOLID_TORRENTS,
                        isVerified = true
                    )
                )
            }
        } catch (e: Exception) {
            // ignore malformed payloads
        }
        return list
    }

    /**
     * Nyaa.si public RSS feed — the long-lived verified indexer for anime
     * and a useful extra swarm for general titles. Pure parsing lives in
     * [parseNyaaRss] so it stays unit-testable without network.
     */
    fun fetchNyaaTorrents(
        query: String,
        season: Int? = null,
        episode: Int? = null
    ): List<TorrentSource> {
        return try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = "https://nyaa.si/?page=rss&q=$encoded&c=0_0&f=0"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            parseNyaaRss(body, season, episode)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseNyaaRss(
        xml: String,
        season: Int? = null,
        episode: Int? = null
    ): List<TorrentSource> {
        val list = mutableListOf<TorrentSource>()
        try {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
            val items = doc.getElementsByTagName("item")
            for (i in 0 until items.length.coerceAtMost(10)) {
                val node = items.item(i) ?: continue
                val children = node.childNodes
                var title = ""
                var link = ""
                var hash = ""
                var sizeStr = ""
                var seeders = 0
                var leechers = 0
                for (j in 0 until children.length) {
                    val child = children.item(j) ?: continue
                    when (child.nodeName.substringAfter(":").lowercase()) {
                        "title" -> title = child.textContent?.trim().orEmpty()
                        "link" -> link = child.textContent?.trim().orEmpty()
                        "infohash" -> hash = child.textContent?.trim().orEmpty()
                        "size" -> sizeStr = child.textContent?.trim().orEmpty()
                        "seeders" -> seeders = child.textContent?.trim()?.toIntOrNull() ?: 0
                        "leechers" -> leechers = child.textContent?.trim()?.toIntOrNull() ?: 0
                    }
                }
                val infoHash = MagnetParser.normalizeInfoHash(hash) ?: continue
                if (title.isBlank()) title = "Nyaa $infoHash"
                val sizeBytes = parseSizeToBytes(sizeStr)
                val encodedName = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
                list.add(
                    TorrentSource(
                        title = title,
                        infoHash = infoHash,
                        magnetUri = "magnet:?xt=urn:btih:$infoHash&dn=$encodedName$TRACKER_LIST",
                        torrentFileUrl = link.takeIf { it.startsWith("http", ignoreCase = true) },
                        quality = detectQuality(title),
                        releaseType = detectReleaseType(title),
                        sizeBytes = sizeBytes,
                        sizeDisplay = if (sizeStr.isBlank()) formatBytes(sizeBytes) else sizeStr,
                        seeders = seeders,
                        leechers = leechers,
                        provider = "Nyaa",
                        season = season,
                        episode = episode,
                        indexerId = TorrentSourceRegistry.NYAA,
                        isVerified = true
                    )
                )
            }
        } catch (e: Exception) {
            // ignore malformed feeds
        }
        return list
    }

    /**
     * AnimeTosho public JSON feed. Schema is read defensively (multiple
     * field aliases) so feed-side renames degrade to empty results instead
     * of crashes. Pure parsing lives in [parseAnimeToshoBody].
     */
    fun fetchAnimeToshoTorrents(
        query: String,
        season: Int? = null,
        episode: Int? = null
    ): List<TorrentSource> {
        return try {
            val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.name())
            val url = "https://feed.animetosho.org/json?qx=1&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string() ?: return emptyList()
            parseAnimeToshoBody(body, season, episode)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun parseAnimeToshoBody(
        body: String,
        season: Int? = null,
        episode: Int? = null
    ): List<TorrentSource> {
        val list = mutableListOf<TorrentSource>()
        try {
            val trimmed = body.trim()
            val array = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                else -> JSONObject(trimmed).let {
                    it.optJSONArray("results")
                        ?: it.optJSONArray("torrents")
                        ?: it.optJSONArray("items")
                        ?: return emptyList()
                }
            }
            for (i in 0 until array.length().coerceAtMost(10)) {
                val item = array.optJSONObject(i) ?: continue
                val title = item.optString("title").ifBlank { item.optString("name", "Torrent") }
                val magnet = item.optString("link_magnet").ifBlank { item.optString("magnet", "") }
                    .ifBlank { item.optString("magnet_uri", "") }
                    .ifBlank { item.optString("magnetLink", "") }
                val torrentUrl = item.optString("torrent_url").ifBlank { item.optString("torrent_link", "") }
                    .ifBlank { item.optString("link", "") }
                    .takeIf { it.startsWith("http", ignoreCase = true) }
                val rawHash = item.optString("info_hash").ifBlank { item.optString("infohash", "") }
                    .ifBlank { item.optString("hash", "") }
                    .ifBlank { extractHashFromMagnet(magnet) }
                val hash = MagnetParser.normalizeInfoHash(rawHash) ?: continue
                val seeders = item.optInt("seeders", -1).takeIf { it >= 0 }
                    ?: item.optString("seeders", "0").toIntOrNull()
                    ?: item.optInt("seeds", 0)
                val sizeRaw = item.opt("size")
                val sizeBytes = when (sizeRaw) {
                    is Number -> sizeRaw.toLong().coerceAtLeast(0L)
                    is String -> if (sizeRaw.any { it.isDigit() } && sizeRaw.all { it.isDigit() || it == '.' }) {
                        sizeRaw.toLongOrNull() ?: parseSizeToBytes(sizeRaw)
                    } else parseSizeToBytes(sizeRaw)
                    else -> 0L
                }
                val quality = detectQuality(title)
                val encodedName = URLEncoder.encode(title, StandardCharsets.UTF_8.name())
                list.add(
                    TorrentSource(
                        title = title,
                        infoHash = hash,
                        magnetUri = magnet.ifBlank { "magnet:?xt=urn:btih:$hash&dn=$encodedName$TRACKER_LIST" },
                        torrentFileUrl = torrentUrl,
                        quality = quality,
                        releaseType = detectReleaseType(title),
                        sizeBytes = sizeBytes,
                        sizeDisplay = formatBytes(sizeBytes),
                        seeders = seeders,
                        leechers = item.optInt("leechers", 0),
                        provider = "AnimeTosho",
                        season = season,
                        episode = episode,
                        indexerId = TorrentSourceRegistry.ANIME_TOSHO,
                        isVerified = true
                    )
                )
            }
        } catch (e: Exception) {
            // ignore malformed payloads
        }
        return list
    }

    /** Keeps only results from registry-verified indexers/providers. */
    fun filterVerified(sources: List<TorrentSource>): List<TorrentSource> =
        sources.filter { it.isVerified || TorrentSourceRegistry.isVerifiedProvider(it.provider) }

    /**
     * Canonical resolution bucket used for quality-aware filtering.
     * Accepts both torrent labels ("2160p (4K)", "1080p", "BluRay") and UI
     * labels ("1080p Full HD", "720p HD", "480p SD", "360p Data Saver").
     */
    fun normalizeQuality(quality: String): String {
        val lower = quality.lowercase()
        return when {
            lower.contains("2160") || lower.contains("4k") || lower.contains("uhd") -> "2160p"
            lower.contains("1080") || lower.contains("fhd") || lower.contains("full hd") -> "1080p"
            lower.contains("720") -> "720p"
            // Bare "hd"/"hdtv" without a number historically means 720p.
            lower.contains("480") -> "480p"
            lower.contains("360") -> "360p"
            lower.contains("hdtv") || lower == "hd" || lower.contains(" 720p ") -> "720p"
            else -> "unknown"
        }
    }

    /**
     * Returns only sources matching the requested resolution. Empty means no
     * exact match exists (caller decides whether to fall back).
     */
    fun filterByQuality(sources: List<TorrentSource>, requestedQuality: String): List<TorrentSource> {
        val want = normalizeQuality(requestedQuality)
        if (want == "unknown") return sources
        return sources.filter { normalizeQuality(it.quality) == want }
    }

    /**
     * Highest-seed source for the requested resolution, or null when no exact
     * match exists. Never silently substitutes a different resolution.
     */
    fun selectBestForQuality(sources: List<TorrentSource>, requestedQuality: String): TorrentSource? {
        return filterByQuality(sources, requestedQuality).maxByOrNull { it.seeders }
    }

    /** Resolution ladder, lowest to highest, for stepwise fallback. */
    val QUALITY_LADDER = listOf("360p", "480p", "720p", "1080p", "2160p")

    data class QualityPick(
        val source: TorrentSource,
        val matchedQuality: String,
        val isExactMatch: Boolean
    )

    /**
     * Picks the highest-seed torrent for the requested resolution, stepping UP
     * to the next resolution when none exists (360p -> 480p -> 720p -> ...),
     * then stepping DOWN as a last resort — so a download is queued whenever
     * any torrent exists at all. Returns null only when [sources] is empty.
     */
    fun selectBestWithFallback(sources: List<TorrentSource>, requestedQuality: String): QualityPick? {
        if (sources.isEmpty()) return null
        val want = normalizeQuality(requestedQuality)
        val startIndex = QUALITY_LADDER.indexOf(want)
        if (startIndex == -1) {
            val best = sources.maxByOrNull { it.seeders } ?: return null
            return QualityPick(best, normalizeQuality(best.quality), false)
        }
        // 1. Exact match, then upward (360p -> 480p -> 720p -> ...).
        for (i in startIndex until QUALITY_LADDER.size) {
            val q = QUALITY_LADDER[i]
            val best = sources.filter { normalizeQuality(it.quality) == q }.maxByOrNull { it.seeders }
            if (best != null) return QualityPick(best, q, i == startIndex)
        }
        // 2. Downward last resort so the download never fails outright.
        for (i in startIndex - 1 downTo 0) {
            val q = QUALITY_LADDER[i]
            val best = sources.filter { normalizeQuality(it.quality) == q }.maxByOrNull { it.seeders }
            if (best != null) return QualityPick(best, q, false)
        }
        // 3. Anything with an unrecognized quality label.
        val best = sources.maxByOrNull { it.seeders } ?: return null
        return QualityPick(best, normalizeQuality(best.quality), false)
    }

    private fun detectQuality(title: String): String {
        val lower = title.lowercase()
        // Order matters: check numbered resolutions before bare tags so
        // "1080p WEB-DL" is not misclassified via the "web"/"hd" substrings.
        // Word-boundary regex avoids "hd" matching inside unrelated words.
        val hdWord = Regex("\\bhd\\b|\\bhdtv\\b")
        return when {
            lower.contains("2160p") || lower.contains("2160i") || lower.contains("4k") || lower.contains("uhd") -> "2160p (4K)"
            lower.contains("1080p") || lower.contains("1080i") || lower.contains("fhd") || lower.contains("full hd") -> "1080p"
            lower.contains("720p") || lower.contains("720i") -> "720p"
            lower.contains("480p") || lower.contains("480i") -> "480p"
            lower.contains("360p") || lower.contains("360i") -> "360p"
            hdWord.containsMatchIn(lower) -> "720p"
            lower.contains(" dvd") || lower.contains("dvdrip") || lower.contains(" sd") || lower == "sd" -> "480p"
            // Unknown quality stays unknown — never claim 1080p.
            else -> "Unknown"
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
