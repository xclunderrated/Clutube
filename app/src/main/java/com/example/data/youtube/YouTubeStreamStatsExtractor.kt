package com.example.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamExtractor
import java.util.Locale

/**
 * Real YouTube view counts for trailers via NewPipe Extractor's
 * [StreamExtractor.viewCount]. TMDB exposes no view totals, so this is the
 * only honest source — never derive counts from popularity/votes.
 *
 * Contract: unknown (private/deleted/CAPTCHA/network/`<=0`) → null, callers
 * render blank. No `"0 views"` ever.
 */
object YouTubeStreamStatsExtractor {

    /** Swappable seam so unit tests never hit the network (NewPipe statics). */
    var provider: StreamStatsProvider = NewPipeStreamStatsProvider

    suspend fun fetchViewCount(videoId: String): Long? {
        val key = videoId.trim()
        if (key.isEmpty()) return null
        return runCatching { provider.viewCount(key) }.getOrNull()?.takeIf { it > 0L }
    }

    interface StreamStatsProvider {
        suspend fun viewCount(videoId: String): Long
    }

    private object NewPipeStreamStatsProvider : StreamStatsProvider {
        override suspend fun viewCount(videoId: String): Long = withContext(Dispatchers.IO) {
            NewPipeClient.ensureInitialized()
            val linkHandler = ServiceList.YouTube.getStreamLHFactory()
                .fromUrl("https://www.youtube.com/watch?v=$videoId")
            val extractor: StreamExtractor = ServiceList.YouTube.getStreamExtractor(linkHandler)
            extractor.fetchPage()
            extractor.viewCount
        }
    }
}

/**
 * YouTube-style short count: 942 / 1.5K / 3.4M / 2.1B. Unknown/<=0 → "".
 * The caller appends the qualifier, e.g. `"${formatTrailerCount(n)} trailer views"`.
 */
fun formatTrailerCount(count: Long): String {
    if (count <= 0L) return ""
    // Promote on rounding overflow (e.g. 999_450 → "1M", not "1000K":
    // trimCount rounds 999.45K up to "1000").
    if (count >= 999_450_000L) return trimCount(count / 1_000_000_000.0) + "B"
    if (count >= 999_450L) return trimCount(count / 1_000_000.0) + "M"
    return when {
        count < 1_000L -> count.toString()
        count < 1_000_000L -> trimCount(count / 1_000.0) + "K"
        count < 1_000_000_000L -> trimCount(count / 1_000_000.0) + "M"
        else -> trimCount(count / 1_000_000_000.0) + "B"
    }
}

fun formatTrailerViews(count: Long): String {
    val short = formatTrailerCount(count)
    return if (short.isEmpty()) "" else "$short views"
}

/** Same count with the explicit qualifier used under titles/cards. */
fun formatTrailerViewsLabel(count: Long): String {
    val short = formatTrailerCount(count)
    return if (short.isEmpty()) "" else "$short trailer views"
}

private fun trimCount(value: Double): String {
    val rounded = kotlin.math.round(value * 10.0) / 10.0
    return if (rounded >= 100.0 || rounded == kotlin.math.floor(rounded)) {
        String.format(Locale.US, "%.0f", rounded)
    } else {
        String.format(Locale.US, "%.1f", rounded)
    }
}
