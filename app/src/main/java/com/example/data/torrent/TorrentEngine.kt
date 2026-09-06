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

    private data class VideoCandidate(val index: Int, val path: String, val size: Long)

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

    /** Parses "dl_tv_<tmdb>_s1_e2" -> (1, 2); nulls for movies/custom magnets. */
    fun parseSeasonEpisode(downloadId: String): Pair<Int?, Int?> {
        val m = Regex("_s(\\d+)_e(\\d+)").find(downloadId.lowercase())
        return if (m != null) {
            Pair(m.groupValues[1].toIntOrNull(), m.groupValues[2].toIntOrNull())
        } else Pair(null, null)
    }

    suspend fun downloadFromMagnet(
        magnetUri: String,
        downloadId: String,
        savePath: File,
        onProgress: (TorrentProgress) -> Unit,
        expectedSeason: Int? = null,
        expectedEpisode: Int? = null
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

            // A previous failed attempt leaves its handle in the session, so a
            // re-add can throw "duplicate torrent". Fall back to reusing the
            // existing handle so retry-after-failure keeps working.
            val handle: TorrentHandle? = runCatching {
                if (torrentInfo != null) {
                    sm.download(torrentInfo, savePath)
                    sm.find(torrentInfo.infoHash())
                } else {
                    // If fetchMagnet metadata times out, initiate download directly via magnet URI
                    sm.download(magnetUri, savePath, TorrentFlags.SEQUENTIAL_DOWNLOAD)
                    if (infoHash != null) sm.find(infoHash) else null
                }
            }.getOrNull()
                ?: infoHash?.let { runCatching { sm.find(it) }.getOrNull() }?.takeIf { it.isValid() }

            if (handle == null || !handle.isValid()) {
                Log.e(TAG, "Failed to get valid TorrentHandle for magnet: $magnetUri")
                return@withContext null
            }

            activeDownloads[downloadId] = handle
            return@withContext processTorrent(
                handle, downloadId, savePath, onProgress, expectedSeason, expectedEpisode
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading from magnet", e)
            return@withContext null
        }
    }

    suspend fun downloadFromTorrentFile(
        torrentBytes: ByteArray,
        downloadId: String,
        savePath: File,
        onProgress: (TorrentProgress) -> Unit,
        expectedSeason: Int? = null,
        expectedEpisode: Int? = null
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

            // Same duplicate-handle reuse as the magnet path: a failed attempt
            // stays in the session, so retry must reuse instead of re-add.
            val handle = runCatching {
                sm.download(torrentInfo, savePath)
                sm.find(torrentInfo.infoHash())
            }.getOrNull()
                ?: runCatching { sm.find(torrentInfo.infoHash()) }.getOrNull()?.takeIf { it.isValid() }
            if (handle == null || !handle.isValid()) {
                Log.e(TAG, "Failed to get valid TorrentHandle for torrent file")
                return@withContext null
            }

            activeDownloads[downloadId] = handle
            return@withContext processTorrent(
                handle, downloadId, savePath, onProgress, expectedSeason, expectedEpisode
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading from torrent file", e)
            return@withContext null
        }
    }

    private suspend fun processTorrent(
        handle: TorrentHandle,
        downloadId: String,
        savePath: File,
        onProgress: (TorrentProgress) -> Unit,
        expectedSeasonArg: Int? = null,
        expectedEpisodeArg: Int? = null
    ): File? = withContext(Dispatchers.IO) {
        // A reused handle (retry path) may still be paused from a previous
        // pause/cancel; resume is idempotent on a running handle.
        runCatching { handle.resume() }
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
        // L0 offline subtitles: small subtitle siblings ride along with the
        // video payload. Same release = same timing, so these are perfectly
        // synced with zero network/quota cost. Oversized files are skipped
        // (likely mislabeled packs, not cues).
        val subtitleExtensions = setOf("srt", "vtt", "ass", "ssa", "sub", "smi")
        val maxSiblingSubtitleBytes = 5L * 1024L * 1024L
        var maxFileIndex = -1
        var maxSize = -1L
        var videoFileName: String? = null
        var videoFilePath: String? = null
        var siblingSubtitleCount = 0

        // Expected S/E: explicit args win, else parse "dl_tv_<tmdb>_s1_e2".
        // Movies/custom magnets have nulls and keep largest-file behavior.
        val parsedSe = parseSeasonEpisode(downloadId)
        val wantSeason = (expectedSeasonArg ?: parsedSe.first)?.coerceAtLeast(1)
        val wantEpisode = (expectedEpisodeArg ?: parsedSe.second)?.coerceAtLeast(1)

        val fileStorage = ti.files()
        val numFiles = ti.numFiles()

        val videoCandidates = mutableListOf<VideoCandidate>()

        for (i in 0 until numFiles) {
            val path = fileStorage.filePath(i)
            val size = fileStorage.fileSize(i)
            val ext = path.substringAfterLast('.', "").lowercase()

            handle.filePriority(i, Priority.IGNORE)

            if (ext in videoExtensions && size > 0) {
                videoCandidates.add(VideoCandidate(i, path, size))
            } else if (ext in subtitleExtensions && size > 0 && size <= maxSiblingSubtitleBytes) {
                // Download alongside the video; DownloadManager collects these
                // from the staging dir after the video payload finalizes.
                handle.filePriority(i, Priority.DEFAULT)
                siblingSubtitleCount++
            }
        }

        if (videoCandidates.isNotEmpty() && wantSeason != null && wantEpisode != null) {
            // Pack-aware pick: prefer the inner file whose name matches S/E.
            // A season pack's largest file is a *random* episode — never fall
            // back to it silently. Fail fast so the row reports "pack without
            // this episode" instead of playing the wrong bytes behind the label.
            val exactInner = videoCandidates
                .filter {
                    TorrentMatcher.isExactEpisode(File(it.path).name, wantSeason, wantEpisode)
                }
                .maxByOrNull { it.size }
            if (exactInner != null) {
                maxFileIndex = exactInner.index
                maxSize = exactInner.size
                videoFileName = File(exactInner.path).name
                videoFilePath = exactInner.path
                Log.i(TAG, "Pack-aware pick S${wantSeason}E${wantEpisode}: $videoFileName (${maxSize} bytes)")
            } else if (videoCandidates.size > 1) {
                Log.e(TAG, "Torrent holds ${videoCandidates.size} videos but none matches S${wantSeason}E${wantEpisode} for $downloadId; refusing largest-file fallback")
                return@withContext null
            } else {
                val only = videoCandidates.single()
                maxFileIndex = only.index
                maxSize = only.size
                videoFileName = File(only.path).name
                videoFilePath = only.path
            }
        } else {
            for (c in videoCandidates) {
                if (c.size > maxSize) {
                    maxSize = c.size
                    maxFileIndex = c.index
                    videoFileName = File(c.path).name
                    videoFilePath = c.path
                }
            }
        }

        if (maxFileIndex != -1) {
            handle.filePriority(maxFileIndex, Priority.TOP_PRIORITY)
            handle.setFlags(TorrentFlags.SEQUENTIAL_DOWNLOAD)
            Log.i(TAG, "Prioritizing video payload: $videoFileName ($maxSize bytes, index $maxFileIndex) + $siblingSubtitleCount subtitle siblings")
        } else {
            // If no recognized video extension, download all files
            for (i in 0 until numFiles) {
                handle.filePriority(i, Priority.DEFAULT)
            }
        }

        // Active download monitoring loop.
        // Uses totalWantedDone/totalWanted (wanted-piece accounting) instead of
        // totalDone (all-piece accounting) so progress can actually reach 1.0
        // when only the largest video file is prioritized.
        // NOTE: on-disk File.length() is deliberately NOT used for progress or
        // completion — libtorrent pre-sizes sparse files to their full logical
        // length, so an empty file already reports "full size" and would fake
        // an instant 100%. Only libtorrent's verified piece accounting counts.
        val loopStartMs = System.currentTimeMillis()
        var lastBytes = -1L
        var lastProgressMs = loopStartMs
        while (currentCoroutineContext().isActive) {
            val status = handle.status()
            val state = mapState(status.state())
            val downloadSpeed = status.downloadRate().toLong()
            val wanted = status.totalWanted().takeIf { it > 0 } ?: maxSize.coerceAtLeast(1L)
            val wantedDone = runCatching { status.totalWantedDone() }.getOrDefault(status.totalDone())
            val libProgress = status.progress().coerceIn(0f, 1f)
            val totalBytes = wanted.coerceAtLeast(1L)

            val bytesDownloaded = maxOf(wantedDone, (libProgress * totalBytes).toLong())
                .coerceIn(0L, totalBytes)
            val progress = (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

            if (bytesDownloaded != lastBytes) {
                lastBytes = bytesDownloaded
                lastProgressMs = System.currentTimeMillis()
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

            val finished = runCatching { status.isFinished() }.getOrDefault(false)
            val seeding = runCatching { status.isSeeding() }.getOrDefault(false)
            val bytesComplete = bytesDownloaded >= totalBytes && totalBytes > 0

            if (state == TorrentState.FINISHED || state == TorrentState.SEEDING ||
                finished || seeding || bytesComplete
            ) {
                Log.i(TAG, "Torrent payload download finished for $downloadId ($bytesDownloaded/$totalBytes bytes)")
                // Emit a final 100% callback so the UI never sticks at 99%.
                onProgress(tp.copy(bytesDownloaded = totalBytes, progress = 1f, state = TorrentState.FINISHED, eta = 0L))
                break
            }
            if (state == TorrentState.ERROR) {
                Log.e(TAG, "Torrent download failed with ERROR state for $downloadId")
                return@withContext null
            }

            // Stall guards: fail fast with a retryable FAILED instead of
            // hanging at 99% forever. Partial files stay on disk, so a retry
            // rechecks hashes and resumes instead of starting over.
            val stalledMs = System.currentTimeMillis() - lastProgressMs
            if (stalledMs > 10L * 60L * 1000L && status.numPeers() == 0 && status.numSeeds() == 0) {
                Log.e(TAG, "Torrent stalled with no peers for 10min: $downloadId")
                return@withContext null
            }
            if (stalledMs > 30L * 60L * 1000L) {
                Log.e(TAG, "Torrent made zero progress for 30min ($bytesDownloaded/$totalBytes): $downloadId")
                return@withContext null
            }

            delay(1000L)
        }

        val downloadedFile = locateVideoFile(savePath, ti, videoFilePath)

        if (downloadedFile == null || !downloadedFile.exists() || downloadedFile.length() <= 0L) {
            Log.e(TAG, "Torrent finished but video file missing for $downloadId")
            return@withContext null
        }
        return@withContext downloadedFile
    }

    /**
     * Resolves the downloaded video file on disk. libtorrent saves under
     * savePath/<torrentName>/<filePath>, but filePath() already embeds the
     * torrent root for multi-file torrents, so the direct join usually works.
     * Falls back to a disk scan for the largest playable video file inside the
     * torrent's folder so renames / nested folders can't yield a dangling path.
     */
    private fun locateVideoFile(savePath: File, ti: TorrentInfo, videoFilePath: String?): File? {
        val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "m4v", "ts")
        if (!videoFilePath.isNullOrBlank()) {
            val direct = File(savePath, videoFilePath)
            if (direct.exists() && direct.length() > 0) return direct
            // Some sessions nest one extra root folder level.
            val byName = File(File(savePath, ti.name()), File(videoFilePath).name)
            if (byName.exists() && byName.length() > 0) return byName
        } else {
            val single = File(savePath, ti.name())
            if (single.isFile && single.exists() && single.length() > 0) return single
        }
        // Fallback: largest video file under the torrent root (or savePath).
        val roots = listOf(File(savePath, ti.name()), savePath).filter { it.exists() }
        var best: File? = null
        var bestSize = -1L
        for (root in roots) {
            runCatching {
                root.walkTopDown().maxDepth(4).filter { f ->
                    f.isFile && f.length() > bestSize &&
                        f.name.substringAfterLast('.', "").lowercase() in videoExtensions
                }.forEach { f ->
                    // Prefer files inside this torrent's folder when scanning savePath.
                    val insideTorrent = f.absolutePath.contains(ti.name()) || root.name == ti.name()
                    if (root.name == ti.name() || insideTorrent) {
                        if (f.length() > bestSize) {
                            bestSize = f.length()
                            best = f
                        }
                    }
                }
            }
        }
        return best
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
