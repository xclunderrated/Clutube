package com.example.data.introdb

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Raw TheIntroDB v3 `GET /media` response.
 *
 * Each segment array is omitted by the API when no submissions exist for that
 * type, and may contain multiple entries (e.g. split credits, double recaps).
 * `start_ms: null` means the segment starts at the beginning of the media;
 * `end_ms: null` means it continues to the end of the media.
 */
@JsonClass(generateAdapter = true)
data class IntroDbMediaResponse(
    @Json(name = "tmdb_id") val tmdbId: Int? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "intro") val intro: List<IntroDbRawSegment>? = null,
    @Json(name = "recap") val recap: List<IntroDbRawSegment>? = null,
    @Json(name = "credits") val credits: List<IntroDbRawSegment>? = null,
    @Json(name = "preview") val preview: List<IntroDbRawSegment>? = null
)

@JsonClass(generateAdapter = true)
data class IntroDbRawSegment(
    @Json(name = "start_ms") val startMs: Long? = null,
    @Json(name = "end_ms") val endMs: Long? = null
)
