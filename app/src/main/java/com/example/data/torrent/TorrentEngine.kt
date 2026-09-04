package com.example.data.torrent

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.libtorrent4j.Priority
import org.libtorrent4j.SessionManager
import org.libtorrent4j.SessionParams
import org.libtorrent4j.SettingsPack
import org.libtorrent4j.Sha1Hash
import org.libtorrent4j.TorrentFlags
import org.libtorrent4j.TorrentHandle
import org.libtorrent4j.TorrentInfo
import org.libtorrent4j.TorrentStatus
import org.libtorrent4j.swig.session_handle
import org.libtorrent4j.swig.settings_pack
import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class TorrentState {
    CHECKING,
    DOWNLOADING_METADATA,
    DOWNLOADING,
    FINISHED,
    SEEDING,
    PAUSED,
    ERROR
}

data class TorrentProgress(
    val downloadId: String,
    val infoHash: String,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val downloadSpeed: Long,   // bytes/sec
    val uploadSpeed: Long,     // bytes/sec  
    val numPeers: Int,
    val numSeeds: Int,
    val progress: Float,       // 0.0 to 1.0
    val state: TorrentState,
    val eta: Long,             // seconds remaining
    val videoFileName: String?
)

object TorrentEngine {
    private const val TAG = "TorrentEngine"
    private val activeDownloads = ConcurrentHashMap<String, TorrentHandle>()
    private var sessionManager: SessionManager? = null
    private var isInitialized = false

    fun initialize(context: Context) {
        if (isInitialized) return

        val sm = SessionManager()
        sessionManager = sm

        val settingsPack = SettingsPack()
        settingsPack.setBoolean(settings_pack.bool_types.enable_dht.swigValue(), true)
        settingsPack.setBoolean(settings_pack.bool_types.enable_lsd.swigValue(), true)
        settingsPack.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(), true)
        settingsPack.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(), true)
        settingsPack.setInteger(settings_pack.int_types.active_downloads.swigValue(), 4)
        settingsPack.setInteger(settings_pack.int_types.active_seeds.swigValue(), 0)

        val defaultSavePath = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "downloads")
        if (!defaultSavePath.exists()) {
            defaultSavePath.mkdirs()
        }

        val sessionParams = SessionParams(settingsPack)
        sm.start(sessionParams)
        isInitialized = true
        Log.i(TAG, "TorrentEngine initialized successfully. Save path: ${defaultSavePath.absolutePath}")
    }

    suspend fun downloadFromMagnet(
        magnetUri: String,
        downloadId: String,
        savePath: File,
        onProgress: (TorrentProgress) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val sm = sessionManager
        if (!isInitialized || sm == null) {
            Log.e(TAG, "TorrentEngine not initialized")
            return@withContext null
        }

        try {
            Log.i(TAG, "Starting magnet download for task: $downloadId")
            if (!savePath.exists()) savePath.mkdirs()

            val rawHash = MagnetParser.parse(magnetUri)?.exactTopic
            val infoHash = if (!rawHash.isNullOrBlank()) runCatching { Sha1Hash.parseHex(rawHash) }.getOrNull() else null

            // First attempt to fetch torrent metadata within 45 seconds
            val metaBytes = runCatching { sm.fetchMagnet(magnetUri, 45, savePath) }.getOrNull()
            val torrentInfo = if (metaBytes != null && metaBytes.isNotEmpty()) {
                runCatching { TorrentInfo(metaBytes) }.getOrNull()
            } else null

            val handle: TorrentHandle? = if (torrentInfo != null) {
                sm.download(torrentInfo, savePath)
                sm.find(torrentInfo.infoHash())
            } else {
                // If fetchMagnet metadata times out, initiate download directly via magnet URI
                sm.download(magnetUri, savePath, TorrentFlags.SEQUENTIAL_DOWNLOAD)
                if (infoHash != null) sm.find(infoHash) else null
            }

            if (handle == null || !handle.isValid()) {
                Log.e(TAG, "Failed to get valid TorrentHandle for magnet: $magnetUri")
                return@withContext null
            }

            activeDownloads[downloadId] = handle
            return@withContext processTorrent(handle, downloadId, savePath, onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading from magnet", e)
            return@withContext null
        }
    }

    suspend fun downloadFromTorrentFile(
        torrentBytes: ByteArray,
        downloadId: String,
        savePath: File,
        onProgress: (TorrentProgress) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val sm = sessionManager
        if (!isInitialized || sm == null) {
            Log.e(TAG, "TorrentEngine not initialized")
            return@withContext null
        }

        try {
            Log.i(TAG, "Starting download from torrent file bytes for task: $downloadId")
            if (!savePath.exists()) savePath.mkdirs()

            val torrentInfo = runCatching { TorrentInfo(torrentBytes) }.getOrNull()
            if (torrentInfo == null) {
                Log.e(TAG, "Invalid torrent file bytes")
                return@withContext null
            }

            sm.download(torrentInfo, savePath)
            val handle = sm.find(torrentInfo.infoHash())
            if (handle == null || !handle.isValid()) {
                Log.e(TAG, "Failed to get valid TorrentHandle for torrent file")
                return@withContext null
            }

            activeDownloads[downloadId] = handle
            return@withContext processTorrent(handle, downloadId, savePath, onProgress)
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading from torrent file", e)
            return@withContext null
        }
    }

    private suspend fun processTorrent(
        handle: TorrentHandle,
        downloadId: String,
        savePath: File,
        onProgress: (TorrentProgress) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        // Wait for metadata if handle doesn't have it yet
        var ti = handle.torrentFile()
        var waitSeconds = 0
        while (ti == null && waitSeconds < 90 && currentCoroutineContext().isActive) {
            delay(1000L)
            waitSeconds++
            ti = handle.torrentFile()
        }

        if (ti == null) {
            Log.e(TAG, "Torrent metadata unavailable after timeout for $downloadId")
            return@withContext null
        }

        val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts")
        var maxFileIndex = -1
        var maxSize = -1L
        var videoFileName: String? = null
        var videoFilePath: String? = null

        val fileStorage = ti.files()
        val numFiles = ti.numFiles()

        for (i in 0 until numFiles) {
            val path = fileStorage.filePath(i)
            val size = fileStorage.fileSize(i)
            val ext = path.substringAfterLast('.', "").lowercase()

            handle.filePriority(i, Priority.IGNORE)

            if (ext in videoExtensions && size > maxSize) {
                maxSize = size
                maxFileIndex = i
                videoFileName = File(path).name
                videoFilePath = path
            }
        }

        if (maxFileIndex != -1) {
            handle.filePriority(maxFileIndex, Priority.TOP_PRIORITY)
            handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
            Log.i(TAG, "Prioritizing video payload: $videoFileName ($maxSize bytes, index $maxFileIndex)")
        } else {
            // If no recognized video extension, download all files
            for (i in 0 until numFiles) {
                handle.filePriority(i, Priority.DEFAULT)
            }
        }

        // Active download monitoring loop
        while (currentCoroutineContext().isActive) {
            val status = handle.status()
            val state = mapState(status.state())
            val downloadSpeed = status.downloadRate().toLong()
            val totalBytes = status.totalWanted().takeIf { it > 0 } ?: maxSize.coerceAtLeast(1L)
            val bytesDownloaded = status.totalDone()
            val progress = if (totalBytes > 0) {
                (bytesDownloaded.toFloat() / totalBytes).coerceIn(0f, 1f)
            } else {
                status.progress()
            }

            val remainingBytes = (totalBytes - bytesDownloaded).coerceAtLeast(0L)
            val eta = if (downloadSpeed > 0) remainingBytes / downloadSpeed else 0L

            val tp = TorrentProgress(
                downloadId = downloadId,
                infoHash = handle.infoHash().toString(),
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                downloadSpeed = downloadSpeed,
                uploadSpeed = status.uploadRate().toLong(),
                numPeers = status.numPeers(),
                numSeeds = status.numSeeds(),
                progress = progress,
                state = state,
                eta = eta,
                videoFileName = videoFileName
            )

            onProgress(tp)

            if (state == TorrentState.FINISHED || state == TorrentState.SEEDING || (bytesDownloaded >= totalBytes && totalBytes > 0)) {
                Log.i(TAG, "Torrent payload download finished for $downloadId ($bytesDownloaded bytes)")
                break
            }
            if (state == TorrentState.ERROR) {
                Log.e(TAG, "Torrent download failed with ERROR state for $downloadId")
                return@withContext null
            }

            delay(1000L)
        }

        val downloadedFile = if (videoFilePath != null) {
            File(savePath, videoFilePath)
        } else {
            File(savePath, ti.name())
        }

        return@withContext downloadedFile
    }

    private fun mapState(libtorrentState: TorrentStatus.State): TorrentState {
        return when (libtorrentState) {
            TorrentStatus.State.CHECKING_FILES,
            TorrentStatus.State.CHECKING_RESUME_DATA -> TorrentState.CHECKING
            TorrentStatus.State.DOWNLOADING_METADATA -> TorrentState.DOWNLOADING_METADATA
            TorrentStatus.State.DOWNLOADING -> TorrentState.DOWNLOADING
            TorrentStatus.State.FINISHED -> TorrentState.FINISHED
            TorrentStatus.State.SEEDING -> TorrentState.SEEDING
            TorrentStatus.State.UNKNOWN -> TorrentState.ERROR
        }
    }

    fun pause(downloadId: String) {
        activeDownloads[downloadId]?.pause()
    }

    fun resume(downloadId: String) {
        activeDownloads[downloadId]?.resume()
    }

    fun cancel(downloadId: String) {
        val handle = activeDownloads.remove(downloadId)
        if (handle != null && handle.isValid()) {
            sessionManager?.remove(handle, session_handle.delete_files)
        }
    }

    fun shutdown() {
        if (!isInitialized) return
        sessionManager?.stop()
        sessionManager = null
        activeDownloads.clear()
        isInitialized = false
        Log.i(TAG, "TorrentEngine shutdown")
    }
}
