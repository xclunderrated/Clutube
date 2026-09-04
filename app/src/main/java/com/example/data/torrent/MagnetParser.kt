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
