package com.example.data.introdb

/**
 * Normalized skip segment derived from a TheIntroDB v3 `GET /media` response.
 *
 * Normalization rules (match the official `theintrodb` npm client):
 * - `null` start → `0.0` (segment starts at the beginning).
 * - `null` end → `endSec == null` + `endsAtMediaEnd == true`.
 */
enum class SkipSegmentType {
    INTRO,
    RECAP,
    CREDITS,
    PREVIEW
}

data class SkipSegment(
    val type: SkipSegmentType,
    val startSec: Double,
    val endSec: Double?,
    val endsAtMediaEnd: Boolean
) {
    val isOpenEnded: Boolean
        get() = endsAtMediaEnd || endSec == null

    /** Button copy. Open-ended tails become "Next Episode" (caller may fall back). */
    fun label(): String = when {
        isOpenEnded && (type == SkipSegmentType.CREDITS || type == SkipSegmentType.PREVIEW) -> "Next Episode"
        type == SkipSegmentType.INTRO -> "Skip Intro"
        type == SkipSegmentType.RECAP -> "Skip Recap"
        type == SkipSegmentType.CREDITS -> "Skip Credits"
        type == SkipSegmentType.PREVIEW -> "Skip Preview"
        else -> "Skip"
    }

    val isNextEpisodeStyle: Boolean
        get() = isOpenEnded &&
            (type == SkipSegmentType.CREDITS || type == SkipSegmentType.PREVIEW)

    /**
     * Visibility window for a button-only UX over a ~1Hz snapshot feed.
     * Pre-roll hides poll coarseness; post-roll prevents flicker on seek landing.
     */
    fun contains(positionSec: Double, preRollSec: Double = PRE_ROLL_SEC): Boolean {
        if (!positionSec.isFinite() || positionSec < 0.0) return false
        if (positionSec < startSec - preRollSec) return false
        val end = endSec
        if (end == null || endsAtMediaEnd) return true
        return positionSec <= end + POST_ROLL_SEC
    }

    companion object {
        const val PRE_ROLL_SEC = 2.0
        const val POST_ROLL_SEC = 1.0

        /**
         * Picks the single segment to show when several overlap (recap → intro
         * handoffs, split credits). Earliest-ending wins; ties break by
         * INTRO > RECAP > CREDITS > PREVIEW so deterministic copy is shown.
         */
        fun selectActive(
            segments: List<SkipSegment>,
            positionSec: Double,
            preRollSec: Double = PRE_ROLL_SEC
        ): SkipSegment? {
            if (!positionSec.isFinite() || positionSec < 0.0) return null
            return segments
                .asSequence()
                .filter { it.contains(positionSec, preRollSec) }
                .sortedWith(
                    compareBy<SkipSegment> { it.endSec ?: Double.MAX_VALUE }
                        .thenBy { it.startSec }
                        .thenBy { it.type.ordinal }
                )
                .firstOrNull()
        }
    }
}

/** Flattens a v3 media response into normalized segments, dropping invalid entries. */
fun IntroDbMediaResponse.toSkipSegments(): List<SkipSegment> {
    fun mapList(
        raw: List<IntroDbRawSegment>?,
        type: SkipSegmentType
    ): List<SkipSegment> {
        if (raw.isNullOrEmpty()) return emptyList()
        return raw.mapNotNull { entry ->
            val startMs = entry.startMs
            val endMs = entry.endMs
            // Fully undetermined entries carry no timing information.
            if (startMs == null && endMs == null) return@mapNotNull null
            if ((startMs ?: 0L) < 0L) return@mapNotNull null
            if (endMs != null && endMs < 0L) return@mapNotNull null
            val startSec = (startMs ?: 0L) / 1000.0
            if (!startSec.isFinite() || startSec < 0.0) return@mapNotNull null
            if (endMs != null) {
                val endSec = endMs / 1000.0
                if (!endSec.isFinite() || endSec <= startSec) return@mapNotNull null
                SkipSegment(
                    type = type,
                    startSec = startSec,
                    endSec = endSec,
                    endsAtMediaEnd = false
                )
            } else {
                SkipSegment(
                    type = type,
                    startSec = startSec,
                    endSec = null,
                    endsAtMediaEnd = true
                )
            }
        }
    }

    return mapList(intro, SkipSegmentType.INTRO) +
        mapList(recap, SkipSegmentType.RECAP) +
        mapList(credits, SkipSegmentType.CREDITS) +
        mapList(preview, SkipSegmentType.PREVIEW)
}
