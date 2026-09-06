package com.example

import com.example.data.introdb.IntroDbApiService
import com.example.data.introdb.IntroDbMediaResponse
import com.example.data.introdb.IntroDbRawSegment
import com.example.data.introdb.IntroDbRepository
import com.example.data.introdb.SkipSegment
import com.example.data.introdb.SkipSegmentType
import com.example.data.introdb.toSkipSegments
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipSegmentsTest {

    @Test
    fun `null start normalizes to zero and null end stays open`() {
        val response = IntroDbMediaResponse(
            tmdbId = 12345,
            type = "movie",
            intro = listOf(IntroDbRawSegment(startMs = null, endMs = 90_000)),
            credits = listOf(IntroDbRawSegment(startMs = 1_800_000, endMs = null))
        )

        val segments = response.toSkipSegments()

        assertEquals(2, segments.size)
        val intro = segments.first { it.type == SkipSegmentType.INTRO }
        assertEquals(0.0, intro.startSec, 0.001)
        assertEquals(90.0, intro.endSec ?: -1.0, 0.001)
        val credits = segments.first { it.type == SkipSegmentType.CREDITS }
        assertNull(credits.endSec)
        assertTrue(credits.endsAtMediaEnd)
        assertEquals("Next Episode", credits.label())
    }

    @Test
    fun `omitted arrays yield no segments and multiple entries are kept`() {
        val response = IntroDbMediaResponse(
            tmdbId = 67890,
            type = "tv",
            recap = listOf(
                IntroDbRawSegment(startMs = 25_000, endMs = 60_000),
                IntroDbRawSegment(startMs = 900_000, endMs = 960_000)
            )
        )

        val segments = response.toSkipSegments()

        assertEquals(2, segments.size)
        assertTrue(segments.all { it.type == SkipSegmentType.RECAP })
    }

    @Test
    fun `invalid entries are dropped`() {
        val response = IntroDbMediaResponse(
            intro = listOf(
                IntroDbRawSegment(startMs = null, endMs = null),
                IntroDbRawSegment(startMs = 90_000, endMs = 30_000),
                IntroDbRawSegment(startMs = -5_000, endMs = 10_000)
            )
        )

        assertTrue(response.toSkipSegments().isEmpty())
    }

    @Test
    fun `matcher honors pre-roll and picks earliest ending overlap`() {
        val segments = listOf(
            SkipSegment(SkipSegmentType.RECAP, startSec = 100.0, endSec = 160.0, endsAtMediaEnd = false),
            SkipSegment(SkipSegmentType.INTRO, startSec = 150.0, endSec = 210.0, endsAtMediaEnd = false)
        )

        // 2s pre-roll: visible just before start.
        assertEquals(
            SkipSegmentType.RECAP,
            SkipSegment.selectActive(segments, 98.5)?.type
        )
        // Overlap: earliest-ending (recap) wins.
        assertEquals(
            SkipSegmentType.RECAP,
            SkipSegment.selectActive(segments, 155.0)?.type
        )
        // After recap's post-roll, intro remains.
        assertEquals(
            SkipSegmentType.INTRO,
            SkipSegment.selectActive(segments, 162.0)?.type
        )
        assertNull(SkipSegment.selectActive(segments, 50.0))
    }

    @Test
    fun `open-ended tail stays visible past the nominal duration`() {
        // Manual seekers can land on or beyond the reported duration;
        // an open-ended credits tail must still match there.
        val tail = SkipSegment(
            type = SkipSegmentType.CREDITS,
            startSec = 3540.0,
            endSec = null,
            endsAtMediaEnd = true
        )

        assertTrue(tail.contains(3599.0))
        assertTrue(tail.contains(3600.0))
        assertTrue(tail.contains(9999.0))
        assertEquals(
            SkipSegmentType.CREDITS,
            SkipSegment.selectActive(listOf(tail), 3600.0)?.type
        )
    }

    @Test
    fun `repository returns empty for invalid ids and failures`() = runTest {
        IntroDbRepository.clearCache()
        val invalid = IntroDbRepository.getSegments("k", tmdbId = null)
        assertTrue(invalid.getOrNull().orEmpty().isEmpty())

        val outOfRange = IntroDbRepository.getSegments("k", tmdbId = 99_999_999)
        assertTrue(outOfRange.getOrNull().orEmpty().isEmpty())

        val halfTv = IntroDbRepository.getSegments("k", tmdbId = 42, season = 1, episode = null)
        assertTrue(halfTv.getOrNull().orEmpty().isEmpty())
    }

    @Test
    fun `repository caches per key and recovers from errors`() = runTest {
        IntroDbRepository.clearCache()
        val realService = IntroDbRepository.apiService
        try {
            var calls = 0
            IntroDbRepository.apiService = object : IntroDbApiService {
                override suspend fun getMedia(
                    tmdbId: Int?,
                    imdbId: String?,
                    tvdbId: Int?,
                    season: Int?,
                    episode: Int?,
                    durationMs: Long?
                ): IntroDbMediaResponse {
                    calls++
                    return IntroDbMediaResponse(intro = listOf(IntroDbRawSegment(null, 30_000)))
                }
            }
            val first = IntroDbRepository.getSegments("movie:1", tmdbId = 1)
            val second = IntroDbRepository.getSegments("movie:1", tmdbId = 1)
            assertEquals(1, first.getOrNull()?.size)
            assertEquals(1, second.getOrNull()?.size)
            assertEquals(1, calls)

            IntroDbRepository.apiService = object : IntroDbApiService {
                override suspend fun getMedia(
                    tmdbId: Int?,
                    imdbId: String?,
                    tvdbId: Int?,
                    season: Int?,
                    episode: Int?,
                    durationMs: Long?
                ): IntroDbMediaResponse {
                    throw java.io.IOException("offline")
                }
            }
            val fallback = IntroDbRepository.getSegments("movie:2", tmdbId = 2)
            assertTrue(fallback.isSuccess)
            assertTrue(fallback.getOrNull().orEmpty().isEmpty())
        } finally {
            IntroDbRepository.apiService = realService
            IntroDbRepository.clearCache()
        }
    }
}
