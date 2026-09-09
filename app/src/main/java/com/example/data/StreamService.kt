package com.example.data

import android.net.Uri
import com.example.model.MediaType
import com.example.model.StreamServer

/**
 * The app-level stream catalog. VidSrc is the default documented embed player
 * and VidLink Pro remains the secondary provider.
 */
object StreamService {
    const val VIDSRC_SERVER_ID = "vidsrc"
    const val VIDLINK_SERVER_ID = "vidlink"
    const val DEFAULT_SERVER_ID = VIDSRC_SERVER_ID
    const val DEFAULT_VIDSRC_SERVER_HOST = "vidsrc2.ru"

    /** Ordered mirrors shown in the player and persisted by SettingsManager. */
    val VIDSRC_SERVER_HOSTS = listOf(
        "vidsrc2.ru",
        "vidsrc.ir",
        "vidsrcme.ru",
        "vidsrcme.su",
        "vidsrc-me.ru",
        "vidsrc-me.su",
        "vidsrc-embed.ru",
        "vidsrc-embed.su",
        "vsrc.su"
    )

    private val blockedDevelopmentSourceMarkers = listOf(
        "interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4",
        "commondatastorage.googleapis.com/gtv-videos-bucket/sample/"
    )

    private val knownPresetIds = mapOf(
        "v_cinema_dune" to "693134",
        "v_cinema_interstellar" to "157336",
        "v_cinema_stranger_things" to "66732",
        "v_cinema_arcane" to "94605",
        "v_cinema_oppenheimer" to "872585",
        "v_cinema_shogun" to "126308",
        "v_cinema_avengers" to "299534",
        "v_cinema_dark_knight" to "155",
        "v_cinema_fallout" to "106379",
        "v_cinema_spiderverse" to "569094"
    )

    val AVAILABLE_SERVERS = listOf(
        StreamServer(
            id = VIDSRC_SERVER_ID,
            name = "VidSrc",
            provider = "VidSrc",
            quality = "Official Embed / Adaptive Quality",
            urlTemplate = "https://vidsrc2.ru/embed/movie/%s?autoplay=1",
            isRecommended = true
        ),
        StreamServer(
            id = "vidlink",
            name = "VidLink Pro",
            provider = "vidlink.pro",
            quality = "1080p Ultra Fast / Multi-Lang",
            urlTemplate = "https://vidlink.pro/movie/%s?autoplay=true",
            isRecommended = true
        )
    )

    fun isVidSrcServerId(serverId: String): Boolean = serverId == VIDSRC_SERVER_ID

    fun isVidSrcServerHost(host: String): Boolean =
        VIDSRC_SERVER_HOSTS.any { it.equals(host.trim(), ignoreCase = true) }

    fun normalizeVidSrcServerHost(host: String): String =
        VIDSRC_SERVER_HOSTS.firstOrNull { it.equals(host.trim(), ignoreCase = true) }
            ?: DEFAULT_VIDSRC_SERVER_HOST

    /**
     * Keeps saved mirror preferences safe and complete: unknown or duplicate
     * hosts are removed and any newly added official mirror is appended.
     */
    fun normalizeVidSrcServerOrder(order: List<String>): List<String> {
        val known = order.mapNotNull { raw ->
            VIDSRC_SERVER_HOSTS.firstOrNull { it.equals(raw.trim(), ignoreCase = true) }
        }
        return (known + VIDSRC_SERVER_HOSTS).distinct()
    }

    /** Provider-level failover. VidSrc mirror failover is handled by the app. */
    fun fallbackServerIds(currentServerId: String): List<String> =
        AVAILABLE_SERVERS.map { it.id }.filterNot { it == currentServerId }

    /**
     * Returns a direct media URL only when it is a real HTTP(S) source. Old
     * builds used public sample videos as placeholders; reject those values.
     */
    fun directSourceOrNull(rawUrl: String?): String? {
        val source = rawUrl?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalized = source.lowercase()
        if (!normalized.startsWith("https://") && !normalized.startsWith("http://")) return null
        if (source.any(Char::isWhitespace)) return null
        if (blockedDevelopmentSourceMarkers.any(normalized::contains)) return null
        val host = runCatching { Uri.parse(source).host }.getOrNull()
        if (host.isNullOrBlank()) return null
        return source
    }

    fun resolveMediaId(rawId: String): String {
        val trimmed = rawId.trim()
        if (trimmed.isEmpty()) return ""
        if (knownPresetIds.containsKey(trimmed)) return knownPresetIds[trimmed]!!
        if (trimmed.startsWith("tmdb_")) {
            val stripped = trimmed.removePrefix("tmdb_")
            if (stripped.all { it.isDigit() }) return stripped
        }
        if (trimmed.all { it.isDigit() }) return trimmed
        // Unknown demo IDs or invalid strings should never silently redirect to another movie:
        if (trimmed.startsWith("v_cinema_")) return ""
        return trimmed
    }

    /**
     * Builds a standard provider URL. VidSrc is also exposed here for share
     * links and history entries; the player loads this documented embed URL.
     */
    fun buildEmbedUrl(
        mediaType: MediaType,
        id: String,
        season: Int = 1,
        episode: Int = 1,
        serverId: String = DEFAULT_SERVER_ID,
        vidSrcHost: String = DEFAULT_VIDSRC_SERVER_HOST
    ): String {
        val cleanId = resolveMediaId(id)
        if (cleanId.isEmpty()) return ""
        val s = if (season <= 0) 1 else season
        val e = if (episode <= 0) 1 else episode

        return when (serverId) {
            VIDSRC_SERVER_ID -> {
                val host = normalizeVidSrcServerHost(vidSrcHost)
                if (mediaType == MediaType.TV_SHOW) {
                    "https://$host/embed/tv/$cleanId/$s/$e?autoplay=1"
                } else {
                    "https://$host/embed/movie/$cleanId?autoplay=1"
                }
            }
            "vidlink" -> {
                if (mediaType == MediaType.TV_SHOW) {
                    "https://vidlink.pro/tv/$cleanId/$s/$e?primaryColor=ff0000&secondaryColor=121212&iconColor=ffffff&autoplay=true"
                } else {
                    "https://vidlink.pro/movie/$cleanId?primaryColor=ff0000&secondaryColor=121212&iconColor=ffffff&autoplay=true"
                }
            }
            else -> {
                if (mediaType == MediaType.TV_SHOW) {
                    "https://vidlink.pro/tv/$cleanId/$s/$e?primaryColor=ff0000&secondaryColor=121212&iconColor=ffffff&autoplay=true"
                } else {
                    "https://vidlink.pro/movie/$cleanId?primaryColor=ff0000&secondaryColor=121212&iconColor=ffffff&autoplay=true"
                }
            }
        }
    }
}
