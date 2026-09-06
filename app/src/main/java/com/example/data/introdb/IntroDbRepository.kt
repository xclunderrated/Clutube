package com.example.data.introdb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Read-only repository over `GET /media`, cached per playback key.
 * Every failure resolves to an empty list so playback UI never depends on
 * segment data being available.
 */
object IntroDbRepository {
    const val MAX_TMDB_ID = 10_000_000

    private val cache = LinkedHashMap<String, List<SkipSegment>>()

    var apiService: IntroDbApiService = IntroDbClient.apiService

    @Synchronized
    fun clearCache() {
        cache.clear()
    }

    @Synchronized
    fun cachedSegments(cacheKey: String): List<SkipSegment>? = cache[cacheKey]

    /**
     * @param tmdbId numeric TMDB id (1..10M). Non-numeric ids must be filtered
     * by callers; this returns empty for out-of-range values.
     * @param durationMs authoritative video duration when known (snapshot),
     * sent as v3 `duration_ms` to disambiguate release cuts. Null on first pass.
     */
    suspend fun getSegments(
        cacheKey: String,
        tmdbId: Int?,
        season: Int? = null,
        episode: Int? = null,
        durationMs: Long? = null
    ): Result<List<SkipSegment>> = withContext(Dispatchers.IO) {
        runCatching {
            val id = tmdbId?.takeIf { it in 1..MAX_TMDB_ID }
                ?: return@runCatching emptyList()
            val safeDurationMs = durationMs
                ?.takeIf { it.isFiniteLongPositive() }
            // TV episodes require both numbers; movies pass neither.
            val tvSeason = season?.takeIf { it >= 1 }
            val tvEpisode = episode?.takeIf { it >= 1 }
            if ((tvSeason == null) != (tvEpisode == null)) {
                return@runCatching emptyList()
            }

            val effectiveKey = buildString {
                append(cacheKey)
                if (safeDurationMs != null) {
                    // Bucket to 30s so minor duration jitter doesn't bust the cache.
                    append("|d")
                    append(safeDurationMs / 30_000L)
                }
            }
            synchronized(this@IntroDbRepository) {
                cache[effectiveKey]?.let { return@runCatching it }
            }

            val response = apiService.getMedia(
                tmdbId = id,
                season = tvSeason,
                episode = tvEpisode,
                durationMs = safeDurationMs
            )
            val segments = response.toSkipSegments()
            synchronized(this@IntroDbRepository) {
                if (cache.size >= 100) {
                    val oldest = cache.keys.firstOrNull()
                    if (oldest != null) cache.remove(oldest)
                }
                cache[effectiveKey] = segments
            }
            segments
        }.recover { emptyList() }
    }

    private fun Long.isFiniteLongPositive(): Boolean = this > 0L
}
