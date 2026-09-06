package com.example.data.torrent

import com.example.data.model.MagnetInfo
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object MagnetParser {
    fun parse(uri: String): MagnetInfo? {
        if (!uri.startsWith("magnet:?", ignoreCase = true)) return null

        val query = uri.substringAfter("magnet:?")
        val params = query.split("&")

        var xt: String? = null
        var dn: String? = null
        val trackers = mutableListOf<String>()

        for (param in params) {
            val parts = param.split("=", limit = 2)
            if (parts.size < 2) continue
            val key = parts[0].lowercase()
            val value = try {
                URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name())
            } catch (e: Exception) {
                parts[1]
            }

            when (key) {
                "xt" -> {
                    if (value.startsWith("urn:btih:", ignoreCase = true)) {
                        xt = value.substringAfter("urn:btih:").trim().lowercase()
                    }
                }
                "dn" -> dn = value
                "tr" -> trackers.add(value)
            }
        }

        val infoHash = xt ?: return null
        val displayName = dn ?: "Torrent_${infoHash.take(8)}"

        return MagnetInfo(
            exactTopic = infoHash,
            displayName = displayName,
            trackers = trackers,
            rawUri = uri
        )
    }

    private val HEX40 = Regex("^[a-f0-9]{40}$")
    private val BASE32 = Regex("^[A-Z2-7]{32}$")

    /**
     * Strict info-hash check: 40 hex chars (v1) or 32 base32 chars.
     * Rejects the dummy/placeholder hashes historically allowed through
     * (hash_<timestamp>, all-zeros) so unverifiable magnets never queue.
     */
    fun isValidInfoHash(raw: String?): Boolean {
        val clean = raw?.trim() ?: return false
        if (clean.isBlank()) return false
        if (clean.all { it == '0' }) return false
        if (clean.startsWith("hash_", ignoreCase = true)) return false
        return HEX40.matches(clean.lowercase()) || BASE32.matches(clean.uppercase())
    }

    /** Lowercased hash when valid, null otherwise. */
    fun normalizeInfoHash(raw: String?): String? {
        val clean = raw?.trim() ?: return null
        return if (isValidInfoHash(clean)) clean.lowercase() else null
    }

    fun buildMagnet(infoHash: String, name: String, trackers: List<String> = emptyList()): String {
        val encodedName = java.net.URLEncoder.encode(name, StandardCharsets.UTF_8.name())
        val sb = StringBuilder("magnet:?xt=urn:btih:$infoHash&dn=$encodedName")
        for (tr in trackers) {
            val encTr = java.net.URLEncoder.encode(tr, StandardCharsets.UTF_8.name())
            sb.append("&tr=").append(encTr)
        }
        return sb.toString()
    }
}
