package com.example.data.introdb

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * TheIntroDB v3 read-only API. `GET /media` is public and needs no API key.
 * See https://theintrodb.org/docs (v3: `duration_ms` replaces v2 `details`).
 */
interface IntroDbApiService {
    @GET("media")
    suspend fun getMedia(
        @Query("tmdb_id") tmdbId: Int? = null,
        @Query("imdb_id") imdbId: String? = null,
        @Query("tvdb_id") tvdbId: Int? = null,
        @Query("season") season: Int? = null,
        @Query("episode") episode: Int? = null,
        @Query("duration_ms") durationMs: Long? = null
    ): IntroDbMediaResponse
}
