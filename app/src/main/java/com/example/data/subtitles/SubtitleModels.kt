package com.example.data.subtitles

import com.example.data.local.DownloadEntity

/**
 * Offline subtitle (CC) models.
 *
 * Two track kinds exist: sidecar files downloaded next to the video
 * ([SubtitleFileInfo]) and tracks embedded in the container itself
 * (mkv/mp4, flagged on the entity). The offline player renders sidecars
 * with its own cue overlay; embedded tracks are reported but need a
 * Media3-based player to render (documented limitation of the V1 player).
 */

/** A single timed cue, normalized from SRT or VTT. Times are milliseconds. */
data class SubtitleCue(
    val startMs: Long,
    val endMs: Long,
    val text: String
)

/** A sidecar subtitle file stored under downloads/subs/. */
data class SubtitleFileInfo(
    val path: String,
    val language: String,
    val label: String,
    val format: String,
    val releaseName: String? = null,
    val hearingImpaired: Boolean = false
)

/** One remote search result before download. */
data class SubtitleCandidate(
    val downloadUrl: String,
    val language: String,
    val format: String,
    val releaseName: String,
    val hearingImpaired: Boolean = false,
    val rating: Double = 0.0,
    val downloadCount: Long = 0L,
    val source: String
)

/** File extensions treated as subtitle payloads (L0 torrent siblings). */
val SUBTITLE_EXTENSIONS = setOf("srt", "vtt", "ass", "ssa", "sub", "smi")

/** Container extensions whose files may carry embedded subtitle tracks. */
val EMBEDDED_SUBTITLE_CONTAINERS = setOf("mkv", "mp4", "m4v", "webm")

const val MAX_SIBLING_SUBTITLE_BYTES = 5L * 1024L * 1024L

/** Offset clamp so a stray tap can never push cues out of the video. */
const val MAX_SUBTITLE_OFFSET_MS = 60_000L
const val SUBTITLE_OFFSET_STEP_MS = 500L

/**
 * True when the download has any usable CC: an embedded-track flag, a
 * primary sidecar path, or a non-empty sidecar manifest. The legacy
 * `subtitleCc` label string is deliberately NOT consulted — it is a display
 * label ("English (CC)"), never proof a track exists.
 */
fun hasUsableSubtitles(entity: DownloadEntity): Boolean {
    if (entity.hasEmbeddedSubtitles) return true
    if (!entity.subtitleFilePath.isNullOrBlank()) return true
    if (!entity.subtitleFilesJson.isNullOrBlank()) return true
    return false
}

/** Short badge label for a usable track, e.g. "CC·EN". Null when none. */
fun subtitleBadgeLabel(entity: DownloadEntity): String? {
    if (!hasUsableSubtitles(entity)) return null
    val lang = entity.subtitleLanguage?.takeIf { it.isNotBlank() && !it.equals("off", ignoreCase = true) }
        ?: "CC"
    return "CC·${lang.uppercase().take(3)}"
}

/**
 * Maps a [com.example.model.SubtitlePreference] or legacy subtitleCc label
 * to an ISO 639-1 code, or "off". Unknown values default to "en" so a
 * mislabeled legacy row still gets English rather than nothing.
 */
fun normalizeSubtitleLanguage(raw: String?): String {
    val v = raw?.trim()?.lowercase().orEmpty()
    return when {
        v.isEmpty() || v == "off" || v == "none" -> "off"
        v == "es" || v.startsWith("spanish") -> "es"
        v == "en" || v.startsWith("english") -> "en"
        v == "auto" -> "en"
        v.length == 2 && v.all { it.isLetter() } -> v
        else -> "en"
    }
}

/** Encodes a small manifest of [SubtitleFileInfo] without extra dependencies. */
fun encodeSubtitleManifest(files: List<SubtitleFileInfo>): String {
    return files.joinToString(separator = ";", prefix = "[", postfix = "]") {
        listOf(
            it.path.replace(";", "%3B"),
            it.language,
            it.label.replace(";", " "),
            it.format,
            (it.releaseName ?: "").replace(";", " "),
            if (it.hearingImpaired) "1" else "0"
        ).joinToString("|")
    }
}

/** Decodes [encodeSubtitleManifest]; malformed entries are skipped. */
fun decodeSubtitleManifest(json: String?): List<SubtitleFileInfo> {
    if (json.isNullOrBlank() || json.length < 3) return emptyList()
    return runCatching {
        json.removePrefix("[").removeSuffix("]").split(";")
            .filter { it.isNotBlank() }
            .mapNotNull { entry ->
                val parts = entry.split("|")
                if (parts.size < 4) return@mapNotNull null
                SubtitleFileInfo(
                    path = parts[0].replace("%3B", ";"),
                    language = parts[1],
                    label = parts[2],
                    format = parts[3],
                    releaseName = parts.getOrNull(4)?.takeIf { s -> s.isNotBlank() },
                    hearingImpaired = parts.getOrNull(5) == "1"
                )
            }
    }.getOrDefault(emptyList())
}
