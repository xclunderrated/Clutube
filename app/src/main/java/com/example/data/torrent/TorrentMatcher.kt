package com.example.data.torrent

/**
 * Strict release-name matching so an S/E request can never resolve to a
 * random episode, season pack, or unrelated title.
 *
 * Pure Kotlin (no Android APIs) so it stays unit-testable on the JVM.
 */
enum class EpisodeMatch {
    EXACT,
    PACK_SEASON,
    PACK_COMPLETE,
    MULTI,
    WRONG
}

data class EpisodeMatchResult(
    val kind: EpisodeMatch,
    /** True when the name is a pack that *could* contain the episode. */
    val isPack: Boolean = false
)

object TorrentMatcher {

    // ------------------------------------------------------------------
    // Catalog title helpers: UI keeps "Title (year)" badge, queries use clean.
    // ------------------------------------------------------------------

    private val trailingYearParen = Regex("\\s*[\\(\\[]\\s*(19\\d{2}|20\\d{2})\\s*[\\)\\]]\\s*$")
    private val trailingYearBare = Regex("\\s+(19\\d{2}|20\\d{2})\\s*$")
    private val nonAlphaNum = Regex("[^a-zA-Z0-9 ]")
    private val multiSpace = Regex("\\s+")

    /** "Dune (2024)" -> "Dune"; "Breaking Bad" stays as-is. */
    fun cleanCatalogTitle(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        var t = raw.trim()
        t = trailingYearParen.replace(t, "")
        // Only strip a bare trailing year when it looks like a suffix, not part of the name.
        // "2012" (the movie) must survive, so require length > 4 before stripping.
        if (t.length > 5) t = trailingYearBare.replace(t, "")
        t = nonAlphaNum.replace(t, " ")
        t = multiSpace.replace(t, " ").trim()
        return t.ifBlank { raw.trim() }
    }

    /** Normalized lowercase form used for contains-comparison. */
    fun normalizeTitle(raw: String?): String =
        cleanCatalogTitle(raw).lowercase().trim()

    /** Query-safe title: cleaned, single-spaced, no punctuation. */
    fun queryTitle(raw: String?): String = cleanCatalogTitle(raw)

    /** Extracts a 4-digit year from "Title (2024)", ISO date, or trailing year. */
    fun extractYear(vararg candidates: String?): String? {
        val yearRegex = Regex("(19\\d{2}|20\\d{2})")
        for (c in candidates) {
            if (c.isNullOrBlank()) continue
            val m = yearRegex.find(c)
            if (m != null) return m.groupValues[1]
        }
        return null
    }

    // ------------------------------------------------------------------
    // Episode matching
    // ------------------------------------------------------------------

    private fun epCode(season: Int, episode: Int): String =
        "S%02dE%02d".format(season, episode)

    /**
     * Classifies [releaseName] against the requested S/E.
     * Order matters: EXACT beats PACK beats WRONG; a name mentioning a
     * *different* episode is always WRONG, even if it also mentions the season.
     */
    fun matchEpisode(releaseName: String, season: Int, episode: Int): EpisodeMatchResult {
        val name = releaseName.lowercase()
        val s = season.coerceAtLeast(1)
        val e = episode.coerceAtLeast(1)
        val code = epCode(s, e).lowercase() // s01e02
        val compact = "s${s}e${e}"

        // 1. Exact SxxEyy variants: S01E02, s01e02, S01.E02, S01-E02, S01 E02
        val exactPatterns = listOf(
            Regex("s0*${s}e0*${e}\\b"),
            Regex("s0*${s}[ ._-]+e0*${e}\\b"),
            Regex("s0*${s}[ ._-]*ep?0*${e}\\b")
        )
        if (exactPatterns.any { it.containsMatchIn(name) }) {
            // A range like E01-E08 / E01-08 / E01toE08 also contains E02 textually;
            // treat ranges as MULTI, not EXACT.
            if (isMultiEpisodeRange(name, s)) {
                return EpisodeMatchResult(EpisodeMatch.MULTI, isPack = true)
            }
            return EpisodeMatchResult(EpisodeMatch.EXACT)
        }

        // 2. Classic 1x02 style.
        if (Regex("\\b${s}x0*${e}\\b").containsMatchIn(name)) {
            return if (isMultiEpisodeRange(name, s)) {
                EpisodeMatchResult(EpisodeMatch.MULTI, isPack = true)
            } else {
                EpisodeMatchResult(EpisodeMatch.EXACT)
            }
        }

        // 3. Bare "episode 2" / "ep 02" — only exact when the season also matches
        // and no other episode number is present.
        val bareEp = Regex("\\bep?(?:isode)?[ ._-]*0*${e}\\b").containsMatchIn(name)
        val seasonTag = Regex("\\bseason[ ._-]*0*${s}\\b").containsMatchIn(name) ||
            Regex("\\bs0*${s}\\b").containsMatchIn(name)
        if (bareEp && seasonTag && !mentionsOtherEpisode(name, s, e)) {
            return EpisodeMatchResult(EpisodeMatch.EXACT)
        }

        // 4. Mentions a DIFFERENT episode of the same/different season -> WRONG.
        // This must come before pack detection so "S01E03 Complete Pack" style
        // names don't get mislabeled as packs for an E02 request.
        if (mentionsOtherEpisode(name, s, e)) {
            return EpisodeMatchResult(EpisodeMatch.WRONG)
        }

        // 5. Packs: complete-series vs season pack vs multi-episode range.
        if (isCompletePack(name)) {
            return EpisodeMatchResult(EpisodeMatch.PACK_COMPLETE, isPack = true)
        }
        if (isMultiEpisodeRange(name, s)) {
            return EpisodeMatchResult(EpisodeMatch.MULTI, isPack = true)
        }
        if (isSeasonPack(name, s)) {
            return EpisodeMatchResult(EpisodeMatch.PACK_SEASON, isPack = true)
        }

        // 6. No episode info at all (e.g. "Breaking Bad S01 1080p") — ambiguous,
        // treat as season pack so it never auto-plays as an exact episode.
        if (seasonTag && !Regex("e\\d|episode|\\dx\\d").containsMatchIn(name)) {
            return EpisodeMatchResult(EpisodeMatch.PACK_SEASON, isPack = true)
        }

        return EpisodeMatchResult(EpisodeMatch.WRONG)
    }

    fun isExactEpisode(releaseName: String, season: Int, episode: Int): Boolean =
        matchEpisode(releaseName, season, episode).kind == EpisodeMatch.EXACT

    /** True for names that can never be auto-played as a single exact episode. */
    fun isPackName(releaseName: String, season: Int? = null): Boolean {
        val name = releaseName.lowercase()
        if (isCompletePack(name)) return true
        if (season != null && isMultiEpisodeRange(name, season)) return true
        if (season != null && isSeasonPack(name, season)) return true
        return Regex("\\b(pack|collection|complete|season\\s+\\d+|s\\d{1,2}\\s+complete)\\b")
            .containsMatchIn(name)
    }

    private fun mentionsOtherEpisode(name: String, season: Int, episode: Int): Boolean {
        // Any SxxEyy with a different episode (same or different season).
        Regex("s(\\d{1,2})[ ._-]*e(\\d{1,3})").findAll(name).forEach { m ->
            val ms = m.groupValues[1].toIntOrNull() ?: return@forEach
            val me = m.groupValues[2].toIntOrNull() ?: return@forEach
            if (ms == season && me != episode) return true
            // Different-season exact codes are wrong for this request too.
            if (ms != season) return true
        }
        // 1x03 style with different episode.
        Regex("\\b(\\d{1,2})x(\\d{1,3})\\b").findAll(name).forEach { m ->
            val ms = m.groupValues[1].toIntOrNull() ?: return@forEach
            val me = m.groupValues[2].toIntOrNull() ?: return@forEach
            if (ms == season && me != episode) return true
        }
        return false
    }

    private fun isCompletePack(name: String): Boolean {
        if (Regex("\\bcomplete(\\s+series)?\\b").containsMatchIn(name)) return true
        if (Regex("\\bseries\\s+pack\\b").containsMatchIn(name)) return true
        if (Regex("\\bs\\d{1,2}\\s*[-–]\\s*s\\d{1,2}\\b").containsMatchIn(name)) return true // S01-S05
        if (Regex("\\bseasons?\\s+\\d+\\s*[-–&+]\\s*\\d+\\b").containsMatchIn(name)) return true
        if (Regex("\\bcollection\\b").containsMatchIn(name)) return true
        return false
    }

    private fun isMultiEpisodeRange(name: String, season: Int): Boolean {
        // E01-E08, E01-08, E01toE08, E01~E08 within the requested season.
        if (Regex("e0*\\d{1,3}\\s*[-–~]\\s*e?0*\\d{1,3}").containsMatchIn(name)) return true
        if (Regex("e0*\\d{1,3}\\s+to\\s+e?0*\\d{1,3}").containsMatchIn(name)) return true
        if (Regex("\\bep?(?:isode)?s\\s+\\d+\\s*[-–]\\s*\\d+").containsMatchIn(name)) return true
        return false
    }

    private fun isSeasonPack(name: String, season: Int): Boolean {
        if (Regex("\\bseason[ ._-]*0*${season}\\b").containsMatchIn(name)) return true
        if (Regex("\\bs0*${season}\\b").containsMatchIn(name) &&
            !Regex("e\\d").containsMatchIn(name)
        ) return true
        if (Regex("\\bseason\\s+pack\\b").containsMatchIn(name)) return true
        if (Regex("\\bs0*${season}\\s+complete\\b").containsMatchIn(name)) return true
        return false
    }

    // ------------------------------------------------------------------
    // Movie matching
    // ------------------------------------------------------------------

    /**
     * True when [releaseName] is plausibly the requested movie:
     * normalized title contained + year within ±1 when both known.
     */
    fun matchMovie(releaseName: String, cleanTitle: String, year: String?): Boolean {
        val normRelease = releaseName.lowercase()
        val normTitle = normalizeTitle(cleanTitle)
        if (normTitle.isBlank()) return false
        // Every significant word of the title must appear (order-free so
        // "Dune: Part Two" matches "Dune Part Two 2024 1080p BluRay").
        val words = normTitle.split(" ").filter { it.length >= 3 }
        if (words.isEmpty()) {
            if (!normRelease.contains(normTitle)) return false
        } else {
            if (!words.all { normRelease.contains(it) }) return false
        }
        if (!year.isNullOrBlank()) {
            val want = year.toIntOrNull()
            val found = Regex("(19\\d{2}|20\\d{2})").findAll(normRelease)
                .mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
            if (want != null && found.isNotEmpty()) {
                if (found.none { kotlin.math.abs(it - want) <= 1 }) return false
            }
        }
        return true
    }

    /** Display helper: "S01E02". */
    fun epCodeLabel(season: Int, episode: Int): String = epCode(season, episode).uppercase()
}
