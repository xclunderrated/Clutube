package com.example.data.subtitles

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Pure (network-free) subtitle source helpers: URL builders, response
 * parsers, and candidate scoring. All I/O lives in [SubtitleManager] so
 * this file stays unit-testable with fixture strings.
 */
object SubtitleSources {

    // ------------------------------------------------------------------
    // URL builders (proxy seam: pass [baseUrl] from SettingsManager)
    // ------------------------------------------------------------------

    fun buildWyzieSearchUrl(
        baseUrl: String,
        imdbId: String?,
        tmdbId: String?,
        season: Int?,
        episode: Int?,
        language: String,
        apiKey: String?
    ): String? {
        val id = imdbId?.trim()?.takeIf { it.isNotBlank() }
            ?: tmdbId?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val params = StringBuilder("id=").append(encode(id))
        if (season != null && episode != null && season > 0 && episode > 0) {
            params.append("&season=").append(season).append("&episode=").append(episode)
        }
        params.append("&language=").append(encode(language.lowercase()))
        params.append("&format=srt")
        if (!apiKey.isNullOrBlank()) params.append("&key=").append(encode(apiKey))
        return "${baseUrl.trimEnd('/')}/search?$params"
    }

    fun buildSubdlSearchUrl(
        apiKey: String,
        imdbId: String?,
        tmdbId: String?,
        title: String?,
        season: Int?,
        episode: Int?,
        language: String,
        isMovie: Boolean
    ): String? {
        if (apiKey.isBlank()) return null
        val params = StringBuilder("api_key=").append(encode(apiKey))
        val imdb = imdbId?.trim()?.takeIf { it.isNotBlank() }
        val tmdb = tmdbId?.trim()?.takeIf { it.isNotBlank() }
        when {
            imdb != null -> params.append("&imdb_id=").append(encode(imdb))
            tmdb != null -> params.append("&tmdb_id=").append(encode(tmdb))
            !title.isNullOrBlank() -> params.append("&film_name=").append(encode(title))
            else -> return null
        }
        params.append("&type=").append(if (isMovie) "movie" else "tv")
        params.append("&languages=").append(encode(language.uppercase()))
        if (!isMovie && season != null && episode != null) {
            params.append("&season_number=").append(season)
            params.append("&episode_number=").append(episode)
        }
        params.append("&unpack=1")
        return "https://api.subdl.com/api/v1/subtitles?$params"
    }

    // ------------------------------------------------------------------
    // Wyzie response parsing. Shape (v8.x):
    // [{ url, language/displayLanguage, format, releaseName/release,
    //    hearingImpaired/hi, downloads, rating, source }]
    // Field aliases are read defensively; unknown shapes yield emptyList().
    // ------------------------------------------------------------------

    fun parseWyzieResponse(body: String, source: String = "wyzie"): List<SubtitleCandidate> {
        val list = mutableListOf<SubtitleCandidate>()
        if (body.isBlank()) return emptyList()
        try {
            val trimmed = body.trim()
            val array: JSONArray = when {
                trimmed.startsWith("[") -> JSONArray(trimmed)
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    obj.optJSONArray("subtitles")
                        ?: obj.optJSONArray("results")
                        ?: obj.optJSONArray("data")
                        ?: return emptyList()
                }
                else -> return emptyList()
            }
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val url = item.optString("url").trim()
                    .ifBlank { item.optString("downloadUrl", "").trim() }
                if (url.isBlank()) continue
                val lang = item.optString("language").trim()
                    .ifBlank { item.optString("displayLanguage", "").trim() }
                    .ifBlank { item.optString("lang", "").trim() }
                    .lowercase().take(2)
                // Providers disagree on the format key; infer from the file
                // extension when it is missing so .vtt results stay .vtt.
                val rawFormat = item.optString("format", "").trim().lowercase()
                val format = rawFormat.ifBlank {
                    url.substringAfterLast('.', "").lowercase()
                        .substringBefore('?')
                        .takeIf { it in setOf("srt", "vtt", "ass", "ssa", "sub", "smi") }
                        ?: "srt"
                }
                val release = item.optString("releaseName").trim()
                    .ifBlank { item.optString("release", "").trim() }
                    .ifBlank { item.optString("release_name", "").trim() }
                val hi = item.optBoolean("hearingImpaired", false) ||
                    item.optBoolean("hi", false)
                list.add(
                    SubtitleCandidate(
                        downloadUrl = url,
                        language = lang.ifBlank { "en" },
                        format = format,
                        releaseName = release,
                        hearingImpaired = hi,
                        rating = item.optDouble("rating", 0.0),
                        downloadCount = item.optLong("downloads", 0L),
                        source = item.optString("source", source).ifBlank { source }
                    )
                )
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return list
    }

    // ------------------------------------------------------------------
    // SubDL response parsing. Shape:
    // { status, subtitles: [{ url, language, format, release_name,
    //   hi, season, episode, unpack_files: [{ file_n_id, name, language,
    //   hi, format, url }] }] }
    // With unpack=1, individual files carry the per-episode download URL.
    // ------------------------------------------------------------------

    fun parseSubdlResponse(body: String): List<SubtitleCandidate> {
        val list = mutableListOf<SubtitleCandidate>()
        if (body.isBlank()) return emptyList()
        try {
            val root = JSONObject(body.trim())
            if (!root.optBoolean("status", false)) return emptyList()
            val subs = root.optJSONArray("subtitles") ?: return emptyList()
            for (i in 0 until subs.length()) {
                val pack = subs.optJSONObject(i) ?: continue
                val packRelease = pack.optString("release_name", "").trim()
                val packLang = pack.optString("language", "").trim().uppercase()
                val unpacked = pack.optJSONArray("unpack_files")
                if (unpacked != null && unpacked.length() > 0) {
                    for (j in 0 until unpacked.length()) {
                        val f = unpacked.optJSONObject(j) ?: continue
                        val url = absoluteSubdlUrl(f.optString("url", "").trim()) ?: continue
                        list.add(
                            SubtitleCandidate(
                                downloadUrl = url,
                                language = f.optString("language", packLang).trim()
                                    .ifBlank { packLang }.lowercase().ifBlank { "en" },
                                format = f.optString("format", "srt").trim().lowercase().ifBlank { "srt" },
                                releaseName = f.optString("release_name", packRelease).trim(),
                                hearingImpaired = f.optBoolean("hi", false),
                                source = "subdl"
                            )
                        )
                    }
                } else {
                    val url = absoluteSubdlUrl(pack.optString("url", "").trim()) ?: continue
                    list.add(
                        SubtitleCandidate(
                            downloadUrl = url,
                            language = packLang.lowercase().ifBlank { "en" },
                            format = pack.optString("format", "srt").trim().lowercase().ifBlank { "srt" },
                            releaseName = packRelease,
                            hearingImpaired = pack.optBoolean("hi", false),
                            source = "subdl"
                        )
                    )
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return list
    }

    private fun absoluteSubdlUrl(raw: String): String? {
        if (raw.isBlank()) return null
        return when {
            raw.startsWith("http", ignoreCase = true) -> raw
            raw.startsWith("/") -> "https://dl.subdl.com$raw"
            else -> "https://dl.subdl.com/$raw"
        }
    }

    // ------------------------------------------------------------------
    // YIFY scrape (keyless fallback, movies only). Parses the movie page
    // table rows: | rating | language | release | uploader | download |.
    // Only the row's detail href is extractable without JS; the detail page
    // holds the direct .zip link. Returns detail-page URLs for the manager
    // to follow (max [maxRows]).
    // ------------------------------------------------------------------

    fun parseYifyMoviePage(html: String, maxRows: Int = 12): List<YifyRow> {
        val rows = mutableListOf<YifyRow>()
        if (html.isBlank()) return emptyList()
        try {
            // Row pattern: language cell, then a /subtitles/ href with release text.
            val rowPattern = Regex(
                """<td[^>]*>\s*([A-Za-z /()]+?)\s*</td>\s*<td[^>]*>.*?href="(/subtitles/[^"]+)".*?>([^<]{3,200}?)<""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            )
            for (match in rowPattern.findAll(html)) {
                if (rows.size >= maxRows) break
                val language = match.groupValues[1].trim()
                val href = match.groupValues[2].trim()
                val release = match.groupValues[3].trim().replace(Regex("""\s+"""), " ")
                if (href.isBlank() || release.length < 3) continue
                rows.add(YifyRow(language = language, detailPath = href, release = release))
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return rows
    }

    data class YifyRow(
        val language: String,
        val detailPath: String,
        val release: String
    )

    /** Detail page → direct .zip href (first .zip link on the page wins). */
    fun parseYifyDetailPage(html: String): String? {
        if (html.isBlank()) return null
        return try {
            val zip = Regex("""href="([^"]+\.zip[^"]*)"""", RegexOption.IGNORE_CASE)
                .findAll(html)
                .map { it.groupValues[1].trim() }
                .firstOrNull { it.isNotBlank() }
                ?: return null
            when {
                zip.startsWith("http", ignoreCase = true) -> zip
                zip.startsWith("/") -> "https://yifysubtitles.ch$zip"
                else -> "https://yifysubtitles.ch/$zip"
            }
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------------
    // Candidate scoring: release-name overlap is the sync signal.
    // ------------------------------------------------------------------

    /**
     * Picks the best-synced candidate for [torrentTitle] (the downloaded
     * file/torrent name). Token overlap between the torrent name and the
     * subtitle's release name predicts timing match (same rip = same timing).
     * Returns null only when [candidates] is empty.
     */
    fun selectBestCandidate(
        candidates: List<SubtitleCandidate>,
        torrentTitle: String,
        preferHearingImpaired: Boolean = false
    ): SubtitleCandidate? {
        if (candidates.isEmpty()) return null
        if (candidates.size == 1) return candidates.first()
        val torrentTokens = tokenize(torrentTitle)
        return candidates.maxWithOrNull(
            compareBy<SubtitleCandidate> { scoreCandidate(it, torrentTokens, preferHearingImpaired) }
                .thenBy { it.downloadCount }
                .thenBy { it.rating }
        )
    }

    private fun scoreCandidate(
        candidate: SubtitleCandidate,
        torrentTokens: Set<String>,
        preferHi: Boolean
    ): Int {
        var score = 0
        val releaseTokens = tokenize(candidate.releaseName)
        if (releaseTokens.isNotEmpty() && torrentTokens.isNotEmpty()) {
            val overlap = releaseTokens.intersect(torrentTokens).size
            score += overlap * 10
            // Exact release-name containment is a near-certain sync match.
            if (candidate.releaseName.isNotBlank() && releaseTokens.all { it in torrentTokens }) {
                score += 25
            }
        }
        // Format preference: SRT parses most reliably in the V1 overlay.
        score += when (candidate.format.lowercase()) {
            "srt" -> 5
            "vtt" -> 3
            else -> 0
        }
        // Hearing-impaired preference follows the user setting.
        if (candidate.hearingImpaired == preferHi) score += 2
        return score
    }

    private val NOISE_TOKENS = setOf(
        "the", "a", "an", "and", "of", "in", "on", "srt", "sub", "subtitle",
        "english", "spanish", "french", "german", "x264", "x265"
    )

    private fun tokenize(raw: String): Set<String> {
        return raw.lowercase()
            .replace(Regex("""[._\-+()\[\]{}]+"""), " ")
            .split(Regex("""\s+"""))
            .map { it.trim() }
            .filter { it.length >= 2 && it !in NOISE_TOKENS }
            .toSet()
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())
}
