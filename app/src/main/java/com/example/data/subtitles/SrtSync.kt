package com.example.data.subtitles

/**
 * Minimal SRT/VTT cue parsing and timing correction.
 *
 * ExoPlayer/Media3 exposes no subtitle-delay API (google/ExoPlayer#854),
 * so "keep it synced" is implemented here: the player stores a per-download
 * `subtitleOffsetMs` and the sidecar cues are shifted by that amount before
 * display. Positive offset = show cues later (subs ahead of audio).
 */
object SrtSync {

    private val SRT_TIME = Regex("""^(?:(\d{1,3}):)?(\d{1,2}):(\d{2})[,.](\d{1,3})$""")
    private val VTT_HEADER = Regex("""^\s*WEBVTT.*""")
    private val VTT_CUE_SETTINGS = Regex("""\s+(align|line|position|size|vertical):\S+""")
    private val HTML_TAG = Regex("""</?[^>]+>""")
    private val ASS_OVERRIDE = Regex("""\{[^}]*\}""")

    /** Parses SRT or VTT [content] into cues. Never throws; returns empty on garbage. */
    fun parse(content: String): List<SubtitleCue> {
        if (content.isBlank()) return emptyList()
        val normalized = content.replace("\r\n", "\n").replace("\r", "\n").trim()
        if (normalized.isEmpty()) return emptyList()
        return if (VTT_HEADER.containsMatchIn(normalized.lines().firstOrNull().orEmpty())) {
            parseBlocks(normalized, isVtt = true)
        } else {
            parseBlocks(normalized, isVtt = false)
        }
    }

    private fun parseBlocks(content: String, isVtt: Boolean): List<SubtitleCue> {
        val cues = mutableListOf<SubtitleCue>()
        val blocks = content.split(Regex("""\n\s*\n"""))
        for (block in blocks) {
            val lines = block.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
            if (lines.isEmpty()) continue
            // Drop VTT header / NOTE / STYLE / REGION blocks.
            val first = lines.first()
            if (isVtt && (VTT_HEADER.containsMatchIn(first) || first.startsWith("NOTE") ||
                    first == "STYLE" || first == "REGION")
            ) continue
            // Timestamp line is lines[0], or lines[1] when a numeric/VTT cue id precedes it.
            val timeLineIndex = lines.indexOfFirst { it.contains("-->") }
            if (timeLineIndex == -1) continue
            val times = lines[timeLineIndex].split("-->")
            if (times.size != 2) continue
            val start = parseTimestamp(times[0].trim()) ?: continue
            // VTT cue settings trail the end timestamp ("00:01.000 align:start").
            val endToken = times[1].trim().split(Regex("""\s+""")).firstOrNull() ?: continue
            val end = parseTimestamp(endToken) ?: continue
            if (end <= start) continue
            val text = lines.drop(timeLineIndex + 1)
                .joinToString("\n") { cleanCueText(it) }
                .trim()
            if (text.isBlank()) continue
            cues.add(SubtitleCue(startMs = start, endMs = end, text = text))
        }
        return cues.sortedBy { it.startMs }
    }

    private fun cleanCueText(raw: String): String {
        // Strip ASS overrides {\...}, then basic HTML-like tags (<i>, <b>, <font>),
        // keeping the inner text so styling never leaks into the overlay.
        return ASS_OVERRIDE.replace(HTML_TAG.replace(raw, ""), "").trim()
    }

    /**
     * Parses "HH:MM:SS,mmm" (SRT) and "MM:SS.mmm" (VTT, hours optional)
     * into milliseconds.
     */
    fun parseTimestamp(raw: String): Long? {
        val m = SRT_TIME.find(raw.trim()) ?: return null
        val hours = m.groupValues[1].takeIf { it.isNotEmpty() }?.toLongOrNull() ?: 0L
        val minutes = m.groupValues[2].toLongOrNull() ?: return null
        val seconds = m.groupValues[3].toLongOrNull() ?: return null
        val millisRaw = m.groupValues[4]
        val millis = when (millisRaw.length) {
            1 -> millisRaw.toLongOrNull()?.times(100)
            2 -> millisRaw.toLongOrNull()?.times(10)
            else -> millisRaw.take(3).toLongOrNull()
        } ?: return null
        if (minutes >= 60 || seconds >= 60) return null
        return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + millis
    }

    fun formatTimestamp(ms: Long): String {
        val safe = ms.coerceAtLeast(0L)
        val h = safe / 3_600_000L
        val m = (safe % 3_600_000L) / 60_000L
        val s = (safe % 60_000L) / 1_000L
        val r = safe % 1_000L
        return "%02d:%02d:%02d,%03d".format(h, m, s, r)
    }

    /**
     * Returns cues shifted by [offsetMs], clamped at zero and to
     * [MAX_SUBTITLE_OFFSET_MS]. Cues fully shifted out (end <= 0) are dropped.
     */
    fun shift(cues: List<SubtitleCue>, offsetMs: Long): List<SubtitleCue> {
        val offset = offsetMs.coerceIn(-MAX_SUBTITLE_OFFSET_MS, MAX_SUBTITLE_OFFSET_MS)
        if (offset == 0L) return cues
        return cues.mapNotNull { cue ->
            val start = cue.startMs + offset
            val end = cue.endMs + offset
            if (end <= 0L) return@mapNotNull null
            cue.copy(startMs = start.coerceAtLeast(0L), endMs = end.coerceAtLeast(1L))
        }
    }

    /** Serializes cues back to SRT (used when persisting a re-synced copy). */
    fun toSrt(cues: List<SubtitleCue>): String {
        return cues.mapIndexed { index, cue ->
            "${index + 1}\n${formatTimestamp(cue.startMs)} --> ${formatTimestamp(cue.endMs)}\n${cue.text}"
        }.joinToString("\n\n") + if (cues.isEmpty()) "" else "\n"
    }

    /** Active cue at [positionMs] (already offset-adjusted by the caller). */
    fun findCueAt(cues: List<SubtitleCue>, positionMs: Long): SubtitleCue? {
        if (cues.isEmpty()) return null
        // Linear scan from the end is fine for overlay tick rates; cue lists
        // are small and positions usually advance monotonically.
        for (i in cues.indices.reversed()) {
            val cue = cues[i]
            if (positionMs >= cue.startMs && positionMs <= cue.endMs) return cue
            if (cue.startMs < positionMs) break
        }
        // Fallback binary search for seeks backwards past the tail.
        var lo = 0
        var hi = cues.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val cue = cues[mid]
            when {
                positionMs < cue.startMs -> hi = mid - 1
                positionMs > cue.endMs -> lo = mid + 1
                else -> return cue
            }
        }
        return null
    }

    /**
     * Best-effort bytes-to-text: strips a BOM and tries UTF-8 first, then
     * Windows-1252 (covers latin-1 / windows-1250 subs common on Podnapisi).
     */
    fun decodeBytes(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        var offset = 0
        var charsetName = "UTF-8"
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            offset = 3
        } else if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return runCatching { String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE) }.getOrDefault("")
        }
        val slice = bytes.copyOfRange(offset, bytes.size)
        val utf8 = runCatching { String(slice, Charsets.UTF_8) }.getOrNull()
        // If UTF-8 decoding produced replacement chars, the file is probably
        // single-byte encoded; re-decode as Windows-1252 (superset of latin-1).
        if (utf8 != null && !utf8.contains('�')) return utf8
        return runCatching { String(slice, charset("windows-1252")) }.getOrDefault(utf8.orEmpty())
    }
}
