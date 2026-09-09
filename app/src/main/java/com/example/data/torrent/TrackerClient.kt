package com.example.data.torrent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.Random
import java.util.concurrent.TimeUnit

object TrackerClient {
    data class TrackerScrapeResult(
        val seeders: Int,
        val leechers: Int,
        val peers: List<String> = emptyList()
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * Attempts to query trackers (HTTP or UDP) to retrieve live seeders and peers count.
     */
    suspend fun queryTracker(
        trackerUrl: String,
        infoHashHex: String
    ): TrackerScrapeResult? = withContext(Dispatchers.IO) {
        try {
            if (trackerUrl.startsWith("http://", ignoreCase = true) || trackerUrl.startsWith("https://", ignoreCase = true)) {
                queryHttpTracker(trackerUrl, infoHashHex)
            } else if (trackerUrl.startsWith("udp://", ignoreCase = true)) {
                queryUdpTracker(trackerUrl, infoHashHex)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun queryHttpTracker(trackerUrl: String, infoHashHex: String): TrackerScrapeResult? {
        val infoHashBytes = hexToBytes(infoHashHex)
        val encodedHash = buildUrlEncodedBytes(infoHashBytes)
        val peerId = "-TD0001-" + Random().nextInt(100000000).toString().padStart(12, '0')
        val announceUrl = buildString {
            append(trackerUrl)
            if (trackerUrl.contains("?")) append("&") else append("?")
            append("info_hash=").append(encodedHash)
            append("&peer_id=").append(peerId)
            append("&port=6881&uploaded=0&downloaded=0&left=1000000&compact=1")
        }

        val request = Request.Builder()
            .url(announceUrl)
            .header("User-Agent", "TorrentDownloader/1.0")
            .build()

        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) return null
        val bytes = response.body?.bytes() ?: return null

        val parser = BencodeParser(ByteArrayInputStream(bytes))
        val root = parser.parse() as? Map<*, *> ?: return null

        val seeders = (root["complete"] as? Number)?.toInt() ?: 0
        val leechers = (root["incomplete"] as? Number)?.toInt() ?: 0
        val peersBytes = root["peers"] as? ByteArray
        val peersList = mutableListOf<String>()

        if (peersBytes != null) {
            for (i in 0 until peersBytes.size step 6) {
                if (i + 6 <= peersBytes.size) {
                    val ip = "${peersBytes[i].toInt() and 0xFF}.${peersBytes[i+1].toInt() and 0xFF}.${peersBytes[i+2].toInt() and 0xFF}.${peersBytes[i+3].toInt() and 0xFF}"
                    val port = ((peersBytes[i+4].toInt() and 0xFF) shl 8) or (peersBytes[i+5].toInt() and 0xFF)
                    peersList.add("$ip:$port")
                }
            }
        }

        return TrackerScrapeResult(
            seeders = seeders.coerceAtLeast(peersList.size),
            leechers = leechers,
            peers = peersList
        )
    }

    private fun queryUdpTracker(trackerUrl: String, infoHashHex: String): TrackerScrapeResult? {
        val uri = URI(trackerUrl)
        val host = uri.host ?: return null
        val port = if (uri.port > 0) uri.port else 80

        val socket = DatagramSocket()
        socket.soTimeout = 4000
        val address = InetAddress.getByName(host)

        try {
            // 1. Connect Request
            val transactionId = Random().nextInt()
            val connectBuf = ByteBuffer.allocate(16)
            connectBuf.putLong(0x41727101980L) // Protocol magic
            connectBuf.putInt(0) // Action: 0 (connect)
            connectBuf.putInt(transactionId)

            val packetOut = DatagramPacket(connectBuf.array(), 16, address, port)
            socket.send(packetOut)

            val inBuf = ByteArray(1024)
            val packetIn = DatagramPacket(inBuf, inBuf.size)
            socket.receive(packetIn)

            val resBuf = ByteBuffer.wrap(inBuf, 0, packetIn.length)
            if (resBuf.remaining() < 16) return null
            val action = resBuf.getInt()
            val recvTransId = resBuf.getInt()
            if (action != 0 || recvTransId != transactionId) return null
            val connectionId = resBuf.getLong()

            // 2. Announce Request
            val announceTransId = Random().nextInt()
            val announceBuf = ByteBuffer.allocate(98)
            announceBuf.putLong(connectionId)
            announceBuf.putInt(1) // Action: 1 (announce)
            announceBuf.putInt(announceTransId)
            announceBuf.put(hexToBytes(infoHashHex)) // 20 bytes info_hash
            val peerId = ("-TD0001-" + Random().nextInt(100000000).toString().padStart(12, '0')).toByteArray(StandardCharsets.US_ASCII)
            announceBuf.put(peerId.copyOf(20))
            announceBuf.putLong(0L) // downloaded
            announceBuf.putLong(1000000L) // left
            announceBuf.putLong(0L) // uploaded
            announceBuf.putInt(0) // event: 0 (none)
            announceBuf.putInt(0) // IP: 0 (default)
            announceBuf.putInt(Random().nextInt()) // key
            announceBuf.putInt(20) // num_want: 20
            announceBuf.putShort(6881.toShort()) // port

            val annPacket = DatagramPacket(announceBuf.array(), 98, address, port)
            socket.send(annPacket)

            val annInBuf = ByteArray(1024)
            val annRecvPacket = DatagramPacket(annInBuf, annInBuf.size)
            socket.receive(annRecvPacket)

            val annResBuf = ByteBuffer.wrap(annInBuf, 0, annRecvPacket.length)
            if (annResBuf.remaining() < 20) return null
            val annAction = annResBuf.getInt()
            val annResTransId = annResBuf.getInt()
            if (annAction != 1 || annResTransId != announceTransId) return null

            val interval = annResBuf.getInt()
            val leechers = annResBuf.getInt()
            val seeders = annResBuf.getInt()

            val peers = mutableListOf<String>()
            while (annResBuf.remaining() >= 6) {
                val b1 = annResBuf.get().toInt() and 0xFF
                val b2 = annResBuf.get().toInt() and 0xFF
                val b3 = annResBuf.get().toInt() and 0xFF
                val b4 = annResBuf.get().toInt() and 0xFF
                val p = annResBuf.getShort().toInt() and 0xFFFF
                peers.add("$b1.$b2.$b3.$b4:$p")
            }

            return TrackerScrapeResult(
                seeders = seeders.coerceAtLeast(peers.size),
                leechers = leechers,
                peers = peers
            )
        } finally {
            socket.close()
        }
    }

    private fun hexToBytes(hex: String): ByteArray {
        val clean = hex.trim().replace(" ", "")
        val len = clean.length
        val data = ByteArray(20)
        for (i in 0 until 20) {
            val idx = i * 2
            if (idx + 2 <= len) {
                data[i] = ((Character.digit(clean[idx], 16) shl 4) + Character.digit(clean[idx + 1], 16)).toByte()
            }
        }
        return data
    }

    private fun buildUrlEncodedBytes(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            if ((c in 'a'.code..'z'.code) || (c in 'A'.code..'Z'.code) || (c in '0'.code..'9'.code) || c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code) {
                sb.append(c.toChar())
            } else {
                sb.append("%").append("%02X".format(c))
            }
        }
        return sb.toString()
    }
}
