package com.example

import com.example.data.subtitles.SrtSync
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubtitleSyncTest {

    private val sampleSrt = """
        1
        00:00:01,000 --> 00:00:04,000
        Hello <i>world</i>

        2
        00:00:05,500 --> 00:00:07,000
        Second {comment}cue

        3
        00:01:10,250 --> 00:01:12,000
        Third cue
    """.trimIndent()

    @Test
    fun `parse reads srt cues and strips tags`() {
        val cues = SrtSync.parse(sampleSrt)

        assertEquals(3, cues.size)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals(4_000L, cues[0].endMs)
        assertEquals("Hello world", cues[0].text)
        assertEquals("Second cue", cues[1].text)
        assertEquals(70_250L, cues[2].startMs)
    }

    @Test
    fun `parse reads vtt cues with settings`() {
        val vtt = """
            WEBVTT

            00:01.000 --> 00:04.000 align:start position:0%
            Hello vtt

            NOTE a comment block

            cue-2
            00:05.500 --> 00:07.000
            Second cue
        """.trimIndent()

        val cues = SrtSync.parse(vtt)

        assertEquals(2, cues.size)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals("Hello vtt", cues[0].text)
        assertEquals(5_500L, cues[1].startMs)
    }

    @Test
    fun `parse returns empty for garbage`() {
        assertTrue(SrtSync.parse("").isEmpty())
        assertTrue(SrtSync.parse("not subtitles at all\njust text").isEmpty())
        // End before start is dropped.
        assertTrue(SrtSync.parse("1\n00:00:05,000 --> 00:00:01,000\nBackwards").isEmpty())
    }

    @Test
    fun `shift moves cues and clamps at zero`() {
        val cues = SrtSync.parse(sampleSrt)

        val later = SrtSync.shift(cues, 1_500L)
        assertEquals(2_500L, later[0].startMs)
        assertEquals(5_500L, later[0].endMs)

        val earlier = SrtSync.shift(cues, -2_000L)
        // First cue would start at -1000 -> clamped to 0, still visible.
        assertEquals(0L, earlier[0].startMs)
        assertEquals(2_000L, earlier[0].endMs)

        val gone = SrtSync.shift(cues, -70_000L)
        // Clamp caps the offset at -60s; the third cue survives shifted.
        assertTrue(gone.isNotEmpty())
        val dropped = SrtSync.shift(cues.take(1), -60_000L)
        assertTrue(dropped.isEmpty())
    }

    @Test
    fun `shift round trips through srt serialization`() {
        val cues = SrtSync.parse(sampleSrt)
        val shifted = SrtSync.shift(cues, 500L)
        val reparsed = SrtSync.parse(SrtSync.toSrt(shifted))

        assertEquals(shifted.size, reparsed.size)
        assertEquals(1_500L, reparsed[0].startMs)
        assertEquals("Hello world", reparsed[0].text)
    }

    @Test
    fun `findCueAt locates active cue`() {
        val cues = SrtSync.parse(sampleSrt)

        assertEquals("Hello world", SrtSync.findCueAt(cues, 2_000L)?.text)
        assertEquals("Second cue", SrtSync.findCueAt(cues, 6_000L)?.text)
        assertNull(SrtSync.findCueAt(cues, 4_500L))
        assertNull(SrtSync.findCueAt(cues, 0L))
        assertNotNull(SrtSync.findCueAt(cues, 71_000L))
    }

    @Test
    fun `timestamps parse comma dot and short forms`() {
        assertEquals(3_661_000L, SrtSync.parseTimestamp("01:01:01,000"))
        assertEquals(62_500L, SrtSync.parseTimestamp("01:02.500"))
        assertEquals(62_500L, SrtSync.parseTimestamp("01:02,500"))
        assertNull(SrtSync.parseTimestamp("99:99:99,999"))
        assertNull(SrtSync.parseTimestamp("nope"))
    }

    @Test
    fun `decodeBytes strips bom and tolerates latin1`() {
        val withBom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            "1\n00:00:01,000 --> 00:00:02,000\nHi".toByteArray(Charsets.UTF_8)
        assertEquals(1, SrtSync.parse(SrtSync.decodeBytes(withBom)).size)

        val latin1 = "1\n00:00:01,000 --> 00:00:02,000\ncaf\u00e9".toByteArray(charset("windows-1252"))
        val decoded = SrtSync.decodeBytes(latin1)
        assertTrue(decoded.contains("caf"))
        assertEquals(1, SrtSync.parse(decoded).size)
    }
}
