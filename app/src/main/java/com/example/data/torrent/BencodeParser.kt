package com.example.data.torrent

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class BencodeParser(private val input: InputStream) {

    data class TorrentMetadata(
        val infoHashHex: String,
        val name: String,
        val pieceLength: Long,
        val totalLength: Long,
        val announce: String?,
        val announceList: List<String>,
        val files: List<TorrentFileItem>
    )

    data class TorrentFileItem(
        val path: String,
        val length: Long
    )

    companion object {
        fun parseTorrentFile(bytes: ByteArray): TorrentMetadata {
            // Find start and end of 'info' dictionary to compute SHA-1
            val infoBytes = extractInfoBytes(bytes)
            val infoHashHex = if (infoBytes != null) {
                sha1Hex(infoBytes)
            } else {
                ""
            }

            val parser = BencodeParser(ByteArrayInputStream(bytes))
            val root = parser.parse() as? Map<*, *> ?: throw IllegalArgumentException("Invalid torrent file")

            val announce = (root["announce"] as? ByteArray)?.toString(StandardCharsets.UTF_8)
            val announceList = mutableListOf<String>()
            val rawAnnounceList = root["announce-list"] as? List<*>
            if (rawAnnounceList != null) {
                for (tier in rawAnnounceList) {
                    if (tier is List<*>) {
                        for (item in tier) {
                            if (item is ByteArray) {
                                announceList.add(item.toString(StandardCharsets.UTF_8))
                            }
                        }
                    }
                }
            }
            if (announce != null && announce !in announceList) {
                announceList.add(announce)
            }

            val info = root["info"] as? Map<*, *> ?: throw IllegalArgumentException("Missing info dictionary")
            val name = (info["name"] as? ByteArray)?.toString(StandardCharsets.UTF_8) ?: "Unknown"
            val pieceLength = (info["piece length"] as? Number)?.toLong() ?: 0L

            var totalLength = 0L
            val filesList = mutableListOf<TorrentFileItem>()

            val singleLength = (info["length"] as? Number)?.toLong()
            if (singleLength != null) {
                totalLength = singleLength
                filesList.add(TorrentFileItem(name, singleLength))
            } else {
                val multiFiles = info["files"] as? List<*>
                if (multiFiles != null) {
                    for (f in multiFiles) {
                        if (f is Map<*, *>) {
                            val fLen = (f["length"] as? Number)?.toLong() ?: 0L
                            totalLength += fLen
                            val pathList = f["path"] as? List<*>
                            val pathStr = pathList?.mapNotNull { (it as? ByteArray)?.toString(StandardCharsets.UTF_8) }
                                ?.joinToString("/") ?: "file"
                            filesList.add(TorrentFileItem(pathStr, fLen))
                        }
                    }
                }
            }

            return TorrentMetadata(
                infoHashHex = infoHashHex,
                name = name,
                pieceLength = pieceLength,
                totalLength = totalLength,
                announce = announce,
                announceList = announceList,
                files = filesList
            )
        }

        private fun extractInfoBytes(bytes: ByteArray): ByteArray? {
            val pattern = "4:info".toByteArray(StandardCharsets.ISO_8859_1)
            var index = indexOf(bytes, pattern)
            if (index == -1) return null
            val start = index + pattern.size
            // Now start must be 'd'
            if (start >= bytes.size || bytes[start] != 'd'.code.toByte()) return null

            var depth = 0
            var i = start
            while (i < bytes.size) {
                val b = bytes[i].toInt().toChar()
                when (b) {
                    'd', 'l' -> {
                        depth++
                        i++
                    }
                    'e' -> {
                        depth--
                        i++
                        if (depth == 0) {
                            return bytes.copyOfRange(start, i)
                        }
                    }
                    'i' -> {
                        i++
                        while (i < bytes.size && bytes[i].toInt().toChar() != 'e') i++
                        if (i < bytes.size) i++ // skip 'e'
                    }
                    in '0'..'9' -> {
                        val colon = indexOfChar(bytes, ':', i)
                        if (colon == -1) return null
                        val lenStr = String(bytes, i, colon - i, StandardCharsets.ISO_8859_1)
                        val len = lenStr.toIntOrNull() ?: return null
                        i = colon + 1 + len
                    }
                    else -> i++
                }
            }
            return null
        }

        private fun indexOf(src: ByteArray, target: ByteArray): Int {
            for (i in 0..src.size - target.size) {
                var found = true
                for (j in target.indices) {
                    if (src[i + j] != target[j]) {
                        found = false
                        break
                    }
                }
                if (found) return i
            }
            return -1
        }

        private fun indexOfChar(src: ByteArray, c: Char, start: Int): Int {
            for (i in start until src.size) {
                if (src[i].toInt().toChar() == c) return i
            }
            return -1
        }

        private fun sha1Hex(data: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-1")
            val digest = md.digest(data)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }

    fun parse(): Any? {
        val b = input.read()
        if (b == -1) return null
        val c = b.toChar()
        return when (c) {
            'i' -> parseInteger()
            'l' -> parseList()
            'd' -> parseDictionary()
            in '0'..'9' -> parseString(c)
            else -> null
        }
    }

    private fun parseInteger(): Long {
        val sb = StringBuilder()
        var b = input.read()
        while (b != -1 && b.toChar() != 'e') {
            sb.append(b.toChar())
            b = input.read()
        }
        return sb.toString().toLongOrNull() ?: 0L
    }

    private fun parseString(firstChar: Char): ByteArray {
        val sb = StringBuilder().append(firstChar)
        var b = input.read()
        while (b != -1 && b.toChar() != ':') {
            sb.append(b.toChar())
            b = input.read()
        }
        val length = sb.toString().toIntOrNull() ?: 0
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val n = input.read(bytes, read, length - read)
            if (n == -1) break
            read += n
        }
        return bytes
    }

    private fun parseList(): List<Any?> {
        val list = mutableListOf<Any?>()
        while (true) {
            input.mark(1)
            val b = input.read()
            if (b == -1 || b.toChar() == 'e') break
            input.reset()
            list.add(parse())
        }
        return list
    }

    private fun parseDictionary(): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>()
        while (true) {
            input.mark(1)
            val b = input.read()
            if (b == -1 || b.toChar() == 'e') break
            input.reset()
            val keyBytes = parse() as? ByteArray ?: break
            val key = String(keyBytes, StandardCharsets.UTF_8)
            val value = parse()
            map[key] = value
        }
        return map
    }
}
