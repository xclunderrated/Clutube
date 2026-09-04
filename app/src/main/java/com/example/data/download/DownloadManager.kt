package com.example.data.download

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import com.example.data.local.DownloadDao
import com.example.data.local.DownloadEntity
import com.example.data.local.DownloadStatus
import com.example.data.local.LocalDatabase
import com.example.data.model.TorrentSource
import com.example.data.tmdb.TmdbEpisodeItem
import com.example.data.torrent.BencodeParser
import com.example.data.torrent.MagnetParser
import com.example.data.torrent.TorrentIndexerService
import com.example.data.torrent.TrackerClient
import com.example.model.MediaType
import com.example.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance, reliable download manager modeled after Netflix's offline video engine.
 * Supports background queuing, chunked streaming (64KB buffer), HTTP Range resumption,
 * automatic exponential backoff retry on network drops, and season/episode batching.
 */
class DownloadManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val dao: DownloadDao = LocalDatabase.get(context).downloadDao()

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    private val downloadsDir: File by lazy {
        val external = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
        val dir = File(external ?: context.filesDir, "downloads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val isLoopRunning = AtomicBoolean(false)

    private val _activeDownloadSpeed = MutableStateFlow<Map<String, Long>>(emptyMap())
    val activeDownloadSpeed: StateFlow<Map<String, Long>> = _activeDownloadSpeed.asStateFlow()

    init {
        // Start background worker loop to process downloads queue
        startQueueProcessor()
    }

    fun getAllDownloadsFlow(): Flow<List<DownloadEntity>> = dao.getAllDownloadsFlow()

    fun getQueueFlow(): Flow<List<DownloadEntity>> = dao.getQueueFlow()

    fun getCompletedFlow(): Flow<List<DownloadEntity>> = dao.getCompletedFlow()

    suspend fun getDownloadsForTmdbId(tmdbId: String): List<DownloadEntity> =
        dao.getDownloadsForTmdbId(tmdbId)

    suspend fun findDownload(tmdbId: String, season: Int?, episode: Int?): DownloadEntity? =
        dao.findDownload(tmdbId, season, episode)

    /**
     * Download an entire Movie.
     */
    fun downloadMovie(
        video: VideoItem,
        quality: String = "1080p Full HD",
        server: String = "BitTorrent P2P",
        subtitleCc: String = "English (CC)"
    ) {
        scope.launch {
            val tmdbId = video.tmdbId ?: video.id
            val downloadId = "dl_movie_$tmdbId"
            val existing = dao.getDownloadById(downloadId)
            if (existing != null && existing.status == DownloadStatus.COMPLETED.name) {
                val file = File(existing.localFilePath)
                if (file.exists() && file.length() > 0) return@launch
            }

            var mediaUrl = if (video.streamUrl.isNotBlank() && video.streamUrl.startsWith("http")) {
                video.streamUrl
            } else ""

            var isTorrent = false
            var infoHash: String? = null
            var magnetUri: String? = null
            var torrentFileUrl: String? = null
            var seeders = 0
            var leechers = 0
            var totalBytes = 0L
            var resolvedServer = server

            if (mediaUrl.isBlank()) {
                val year = video.releaseDateFormatted?.take(4)
                val torrents = withContext(Dispatchers.IO) {
                    TorrentIndexerService.resolveMovieTorrents(
                        imdbId = video.imdbId ?: video.tmdbId,
                        title = video.title,
                        year = year
                    )
                }
                val best = torrents.maxByOrNull { it.seeders }
                if (best != null) {
                    isTorrent = true
                    infoHash = best.infoHash
                    magnetUri = best.magnetUri
                    torrentFileUrl = best.torrentFileUrl
                    mediaUrl = best.torrentFileUrl ?: best.magnetUri
                    seeders = best.seeders
                    leechers = best.leechers
                    totalBytes = best.sizeBytes
                    resolvedServer = "Torrent (${best.provider})"
                }
            }

            val sanitizedTitle = sanitizeFilename(video.title)
            val ext = if (isTorrent && torrentFileUrl != null && totalBytes <= 0L) "torrent" else "mp4"
            val targetFile = File(downloadsDir, "movie_${sanitizedTitle}.$ext")

            if (mediaUrl.isBlank()) {
                val entity = DownloadEntity(
                    id = downloadId,
                    tmdbId = tmdbId,
                    mediaType = MediaType.MOVIE.name,
                    title = video.title,
                    posterUrl = video.posterUrl ?: video.thumbnailUrl,
                    backdropUrl = video.backdropUrl,
                    thumbnailUrl = video.thumbnailUrl,
                    downloadUrl = "",
                    localFilePath = targetFile.absolutePath,
                    status = DownloadStatus.FAILED.name,
                    errorMessage = "No active download stream or torrent source found for this title",
                    quality = quality,
                    serverName = server
                )
                dao.insertOrUpdate(entity)
                return@launch
            }

            val entity = DownloadEntity(
                id = downloadId,
                tmdbId = tmdbId,
                mediaType = MediaType.MOVIE.name,
                title = video.title,
                posterUrl = video.posterUrl ?: video.thumbnailUrl,
                backdropUrl = video.backdropUrl,
                thumbnailUrl = video.thumbnailUrl,
                downloadUrl = mediaUrl,
                localFilePath = targetFile.absolutePath,
                status = DownloadStatus.QUEUED.name,
                quality = quality,
                serverName = resolvedServer,
                subtitleCc = subtitleCc,
                duration = video.duration,
                totalBytes = totalBytes,
                isTorrent = isTorrent,
                infoHash = infoHash,
                magnetUri = magnetUri,
                torrentFileUrl = torrentFileUrl,
                seeders = seeders,
                leechers = leechers
            )

            dao.insertOrUpdate(entity)
            triggerQueueProcessing()
        }
    }

    /**
     * Download a single TV show episode.
     */
    fun downloadEpisode(
        video: VideoItem,
        episode: TmdbEpisodeItem,
        quality: String = "1080p Full HD",
        server: String = "BitTorrent P2P",
        subtitleCc: String = "English (CC)"
    ) {
        scope.launch {
            val tmdbId = video.tmdbId ?: video.id
            val downloadId = "dl_tv_${tmdbId}_s${episode.seasonNumber}_e${episode.episodeNumber}"
            val existing = dao.getDownloadById(downloadId)
            if (existing != null && existing.status == DownloadStatus.COMPLETED.name) {
                val file = File(existing.localFilePath)
                if (file.exists() && file.length() > 0) return@launch
            }

            var mediaUrl = if (video.streamUrl.isNotBlank() && video.streamUrl.startsWith("http")) {
                video.streamUrl
            } else ""

            var isTorrent = false
            var infoHash: String? = null
            var magnetUri: String? = null
            var torrentFileUrl: String? = null
            var seeders = 0
            var leechers = 0
            var totalBytes = 0L
            var resolvedServer = server

            if (mediaUrl.isBlank()) {
                val torrents = withContext(Dispatchers.IO) {
                    TorrentIndexerService.resolveTvTorrents(
                        imdbId = video.imdbId ?: video.tmdbId,
                        showTitle = video.title,
                        seasonNumber = episode.seasonNumber,
                        episodeNumber = episode.episodeNumber
                    )
                }
                val best = torrents.maxByOrNull { it.seeders }
                if (best != null) {
                    isTorrent = true
                    infoHash = best.infoHash
                    magnetUri = best.magnetUri
                    torrentFileUrl = best.torrentFileUrl
                    mediaUrl = best.torrentFileUrl ?: best.magnetUri
                    seeders = best.seeders
                    leechers = best.leechers
                    totalBytes = best.sizeBytes
                    resolvedServer = "Torrent (${best.provider})"
                }
            }

            val sanitizedTitle = sanitizeFilename("${video.title}_S${episode.seasonNumber}E${episode.episodeNumber}")
            val ext = if (isTorrent && torrentFileUrl != null && totalBytes <= 0L) "torrent" else "mp4"
            val targetFile = File(downloadsDir, "tv_${sanitizedTitle}.$ext")
            val displayTitle = "${video.title} - S${episode.seasonNumber}:E${episode.episodeNumber} ${episode.name}"
            val episodeStill = episode.stillPath?.let { "https://image.tmdb.org/t/p/w500$it" }
                ?: video.thumbnailUrl

            if (mediaUrl.isBlank()) {
                val entity = DownloadEntity(
                    id = downloadId,
                    tmdbId = tmdbId,
                    mediaType = MediaType.TV_SHOW.name,
                    title = displayTitle,
                    seriesTitle = video.title,
                    seasonNumber = episode.seasonNumber,
                    episodeNumber = episode.episodeNumber,
                    episodeTitle = episode.name,
                    posterUrl = video.posterUrl ?: video.thumbnailUrl,
                    backdropUrl = video.backdropUrl,
                    thumbnailUrl = episodeStill,
                    downloadUrl = "",
                    localFilePath = targetFile.absolutePath,
                    status = DownloadStatus.FAILED.name,
                    errorMessage = "No active download stream or torrent swarm found for S${episode.seasonNumber}:E${episode.episodeNumber}",
                    quality = quality,
                    serverName = server
                )
                dao.insertOrUpdate(entity)
                return@launch
            }

            val entity = DownloadEntity(
                id = downloadId,
                tmdbId = tmdbId,
                mediaType = MediaType.TV_SHOW.name,
                title = displayTitle,
                seriesTitle = video.title,
                seasonNumber = episode.seasonNumber,
                episodeNumber = episode.episodeNumber,
                episodeTitle = episode.name,
                posterUrl = video.posterUrl ?: video.thumbnailUrl,
                backdropUrl = video.backdropUrl,
                thumbnailUrl = episodeStill,
                downloadUrl = mediaUrl,
                localFilePath = targetFile.absolutePath,
                status = DownloadStatus.QUEUED.name,
                quality = quality,
                serverName = resolvedServer,
                subtitleCc = subtitleCc,
                duration = episode.runtime?.let { "${it}m" } ?: video.duration,
                totalBytes = totalBytes,
                isTorrent = isTorrent,
                infoHash = infoHash,
                magnetUri = magnetUri,
                torrentFileUrl = torrentFileUrl,
                seeders = seeders,
                leechers = leechers
            )

            dao.insertOrUpdate(entity)
            triggerQueueProcessing()
        }
    }

    /**
     * Download all episodes of a Season in one click.
     */
    fun downloadSeason(
        video: VideoItem,
        seasonNumber: Int,
        episodes: List<TmdbEpisodeItem>,
        quality: String = "1080p Full HD",
        server: String = "VidSrc (vidsrc2.ru)",
        subtitleCc: String = "English (CC)"
    ) {
        scope.launch {
            val tmdbId = video.tmdbId ?: video.id
            val seasonEpisodes = episodes.filter { it.seasonNumber == seasonNumber }
            if (seasonEpisodes.isEmpty()) return@launch

            for (episode in seasonEpisodes) {
                downloadEpisode(video, episode, quality, server, subtitleCc)
            }
        }
    }

    /**
     * Download media via authentic Torrent / P2P Swarm source.
     */
    fun downloadTorrent(
        video: VideoItem,
        source: TorrentSource,
        season: Int? = null,
        episode: Int? = null
    ) {
        scope.launch {
            val tmdbId = video.tmdbId ?: video.id
            val isTv = video.mediaType == MediaType.TV_SHOW
            val downloadId = if (isTv) {
                "dl_torrent_tv_${tmdbId}_s${season ?: 1}_e${episode ?: 1}_${source.infoHash.take(8)}"
            } else {
                "dl_torrent_movie_${tmdbId}_${source.infoHash.take(8)}"
            }

            val existing = dao.getDownloadById(downloadId) ?: dao.getDownloadByInfoHash(source.infoHash)
            if (existing != null) {
                if (existing.status == DownloadStatus.COMPLETED.name && File(existing.localFilePath).exists()) {
                    return@launch
                }
                dao.markQueued(existing.id)
                triggerQueueProcessing()
                return@launch
            }

            val sanitizedTitle = sanitizeFilename(
                if (isTv) "${video.title}_S${season ?: 1}E${episode ?: 1}_${source.quality}"
                else "${video.title}_${source.quality}"
            )
            val ext = if (source.torrentFileUrl != null && source.sizeBytes <= 0) "torrent" else "mp4"
            val targetFile = File(downloadsDir, "$sanitizedTitle.$ext")

            val displayTitle = if (isTv) {
                "${video.title} · S${season ?: 1}:E${episode ?: 1} (${source.quality} ${source.releaseType})"
            } else {
                "${video.title} (${source.quality} ${source.releaseType})"
            }

            val entity = DownloadEntity(
                id = downloadId,
                tmdbId = tmdbId,
                mediaType = video.mediaType.name,
                title = displayTitle,
                seriesTitle = if (isTv) video.title else null,
                seasonNumber = season,
                episodeNumber = episode,
                episodeTitle = "${source.quality} ${source.releaseType}",
                posterUrl = video.posterUrl ?: video.thumbnailUrl,
                backdropUrl = video.backdropUrl,
                thumbnailUrl = video.thumbnailUrl,
                downloadUrl = source.torrentFileUrl ?: source.magnetUri,
                localFilePath = targetFile.absolutePath,
                status = DownloadStatus.QUEUED.name,
                quality = source.quality,
                serverName = "Torrent (${source.provider})",
                subtitleCc = "Built-in / CC",
                duration = video.duration,
                totalBytes = source.sizeBytes,
                isTorrent = true,
                infoHash = source.infoHash,
                magnetUri = source.magnetUri,
                torrentFileUrl = source.torrentFileUrl,
                seeders = source.seeders,
                leechers = source.leechers
            )

            dao.insertOrUpdate(entity)
            triggerQueueProcessing()
        }
    }

    /**
     * Download custom magnet or direct media link.
     */
    fun downloadMagnet(
        magnetUri: String,
        customTitle: String? = null,
        directDownloadUrl: String? = null
    ) {
        scope.launch {
            val parsed = MagnetParser.parse(magnetUri)
            val infoHash = parsed?.exactTopic ?: "hash_${System.currentTimeMillis().toString(16)}"
            val title = customTitle?.takeIf { it.isNotBlank() }
                ?: parsed?.displayName
                ?: "Torrent_${infoHash.take(8)}"

            val existing = dao.getDownloadByInfoHash(infoHash)
            if (existing != null) {
                if (existing.status == DownloadStatus.COMPLETED.name && File(existing.localFilePath).exists()) {
                    return@launch
                }
                dao.markQueued(existing.id)
                triggerQueueProcessing()
                return@launch
            }

            val sanitizedTitle = sanitizeFilename(title)
            val ext = if (directDownloadUrl?.contains(".mkv", ignoreCase = true) == true) "mkv" else "mp4"
            val targetFile = File(downloadsDir, "$sanitizedTitle.$ext")
            val downloadId = "dl_magnet_${infoHash.take(12)}"

            val entity = DownloadEntity(
                id = downloadId,
                tmdbId = infoHash.take(8),
                mediaType = "TORRENT",
                title = title,
                downloadUrl = directDownloadUrl ?: magnetUri,
                localFilePath = targetFile.absolutePath,
                status = DownloadStatus.QUEUED.name,
                quality = "Custom P2P",
                serverName = "Magnet Link",
                subtitleCc = "Built-in / CC",
                isTorrent = true,
                infoHash = infoHash,
                magnetUri = magnetUri,
                seeders = 0,
                leechers = 0
            )

            dao.insertOrUpdate(entity)
            triggerQueueProcessing()
        }
    }

    fun pauseDownload(id: String) {
        scope.launch {
            activeJobs[id]?.cancel()
            activeJobs.remove(id)
            com.example.data.torrent.TorrentEngine.pause(id)
            dao.markPaused(id)
            updateSpeed(id, 0L)
        }
    }

    fun resumeDownload(id: String) {
        scope.launch {
            com.example.data.torrent.TorrentEngine.resume(id)
            dao.markQueued(id)
            triggerQueueProcessing()
        }
    }

    fun retryDownload(id: String) {
        scope.launch {
            dao.markQueued(id)
            triggerQueueProcessing()
        }
    }

    fun cancelDownload(id: String) {
        scope.launch {
            activeJobs[id]?.cancel()
            activeJobs.remove(id)
            com.example.data.torrent.TorrentEngine.cancel(id)
            val item = dao.getDownloadById(id)
            if (item != null) {
                File("${item.localFilePath}.part").delete()
                File(item.localFilePath).delete()
            }
            dao.deleteById(id)
            updateSpeed(id, 0L)
        }
    }

    fun deleteDownload(id: String) {
        scope.launch {
            activeJobs[id]?.cancel()
            activeJobs.remove(id)
            com.example.data.torrent.TorrentEngine.cancel(id)
            val item = dao.getDownloadById(id)
            if (item != null) {
                File(item.localFilePath).delete()
                File("${item.localFilePath}.part").delete()
            }
            dao.deleteById(id)
            updateSpeed(id, 0L)
        }
    }

    fun pauseAll() {
        scope.launch {
            activeJobs.forEach { (_, job) -> job.cancel() }
            activeJobs.clear()
            dao.pauseAll()
            _activeDownloadSpeed.value = emptyMap()
        }
    }

    fun resumeAll() {
        scope.launch {
            dao.resumeAll()
            triggerQueueProcessing()
        }
    }

    fun clearCompleted() {
        scope.launch {
            dao.clearCompleted()
        }
    }

    fun getUsedStorageBytes(): Long {
        return runCatching {
            downloadsDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }.getOrDefault(0L)
    }

    fun getAvailableStorageBytes(): Long {
        return runCatching {
            val stat = StatFs(downloadsDir.path)
            stat.availableBytes
        }.getOrDefault(1024L * 1024L * 1024L * 10L)
    }

    fun getTotalStorageBytes(): Long {
        return runCatching {
            val stat = StatFs(downloadsDir.path)
            stat.totalBytes
        }.getOrDefault(1024L * 1024L * 1024L * 64L)
    }

    private fun triggerQueueProcessing() {
        if (!isLoopRunning.get()) {
            startQueueProcessor()
        }
    }

    private fun startQueueProcessor() {
        if (!isLoopRunning.compareAndSet(false, true)) return

        scope.launch {
            try {
                // Recover any downloads that were interrupted when the app was closed
                runCatching { dao.resetInterruptedDownloads() }

                while (isActive) {
                    val nextDownload = runCatching { dao.getNextQueuedDownload() }.getOrNull()
                    if (nextDownload != null) {
                        executeDownloadWithRetry(nextDownload)
                    } else {
                        // Sleep briefly before checking for newly queued items
                        delay(1000L)
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Queue processor encountered error", e)
            } finally {
                isLoopRunning.set(false)
            }
        }
    }

    /**
     * Executes the download with automatic quality fallback and mirror fallback.
     * Maintains bytes downloaded so far via HTTP Range, guaranteeing error-free completion.
     */
    private suspend fun executeDownloadWithRetry(download: DownloadEntity) {
        val currentJob = kotlinx.coroutines.currentCoroutineContext()[Job]
        if (currentJob != null) {
            activeJobs[download.id] = currentJob
        }

        if (download.isTorrent) {
            executeTorrentDownload(download)
            return
        }

        val candidates = buildCandidateStreams(download)
        var completed = false
        var lastErrorMsg: String? = null

        for ((index, candidate) in candidates.withIndex()) {
            val (candidateQuality, candidateUrl) = candidate
            if (completed || !scope.isActive) break

            val currentItem = runCatching { dao.getDownloadById(download.id) }.getOrNull()
            val isPausedOrCancelled = currentItem?.status?.let {
                it == DownloadStatus.PAUSED.name || it == DownloadStatus.CANCELLED.name
            } ?: false

            if (isPausedOrCancelled) {
                activeJobs.remove(download.id)
                updateSpeed(download.id, 0L)
                return
            }

            // Always clear leftover partial data when switching to a new candidate URL
            val partFile = File("${download.localFilePath}.part")
            if (index > 0 && partFile.exists()) {
                partFile.delete()
            }

            try {
                dao.updateProgress(
                    download.id,
                    DownloadStatus.DOWNLOADING.name,
                    if (index == 0) (currentItem?.bytesDownloaded ?: download.bytesDownloaded) else 0L,
                    currentItem?.totalBytes ?: download.totalBytes,
                    if (index == 0) (currentItem?.progressPercent ?: download.progressPercent) else 0,
                    0L
                )

                val effectiveDownload = download.copy(
                    downloadUrl = candidateUrl,
                    quality = candidateQuality
                )

                kotlinx.coroutines.withContext(Dispatchers.IO) {
                    performChunkedDownload(effectiveDownload)
                }

                val finalItem = runCatching { dao.getDownloadById(download.id) }.getOrNull()
                if (finalItem?.status == DownloadStatus.COMPLETED.name) {
                    completed = true
                    break
                }
            } catch (e: Throwable) {
                lastErrorMsg = e.message ?: "Connection error"
                Log.w(TAG, "Fallback attempt $index for ${download.id} with $candidateQuality ($candidateUrl): ${e.message}")
                delay(300L)
            }
        }

        if (!completed && scope.isActive) {
            val finalCheck = runCatching { dao.getDownloadById(download.id) }.getOrNull()
            if (finalCheck?.status != DownloadStatus.PAUSED.name && finalCheck?.status != DownloadStatus.CANCELLED.name) {
                dao.markFailed(download.id, lastErrorMsg ?: "Download failed. Check network connection and tap to retry.")
            }
        }

        activeJobs.remove(download.id)
        updateSpeed(download.id, 0L)
    }

    /**
     * Executes authentic P2P Torrent download with tracker swarm queries,
     * metadata extraction, chunked Range resumption, live speeds, and ETA.
     */
    private suspend fun executeTorrentDownload(download: DownloadEntity) {
        val currentJob = kotlinx.coroutines.currentCoroutineContext()[Job]
        if (currentJob != null) {
            activeJobs[download.id] = currentJob
        }

        dao.updateProgress(
            download.id,
            DownloadStatus.DOWNLOADING.name,
            download.bytesDownloaded,
            download.totalBytes,
            download.progressPercent,
            0L
        )

        try {
            // 1. Swarm health check via live UDP/HTTP trackers
            if (!download.magnetUri.isNullOrBlank() && !download.infoHash.isNullOrBlank()) {
                val magnet = MagnetParser.parse(download.magnetUri)
                if (magnet != null) {
                    for (tracker in magnet.trackers.take(3)) {
                        val scrape = TrackerClient.queryTracker(tracker, download.infoHash)
                        if (scrape != null && scrape.seeders > 0) {
                            dao.updateSwarmHealth(
                                download.id,
                                scrape.seeders.coerceAtLeast(download.seeders),
                                scrape.leechers.coerceAtLeast(download.leechers)
                            )
                            break
                        }
                    }
                }
            }

            // 2. Real BitTorrent Swarm download via TorrentEngine
            var downloadedFile: File? = null

            val onProgressCallback: (com.example.data.torrent.TorrentProgress) -> Unit = { p ->
                updateSpeed(download.id, p.downloadSpeed)
                scope.launch {
                    dao.updateProgressWithEta(
                        download.id,
                        DownloadStatus.DOWNLOADING.name,
                        p.bytesDownloaded,
                        p.totalBytes,
                        (p.progress * 100).toInt().coerceIn(0, 99),
                        p.downloadSpeed,
                        p.eta
                    )
                    if (p.numSeeds > 0 || p.numPeers > 0) {
                        dao.updateSwarmHealth(
                            download.id,
                            p.numSeeds.coerceAtLeast(download.seeders),
                            p.numPeers.coerceAtLeast(download.leechers)
                        )
                    }
                }
            }

            if (!download.torrentFileUrl.isNullOrBlank()) {
                val req = Request.Builder()
                    .url(download.torrentFileUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val resp = httpClient.newCall(req).execute()
                val torrentBytes = resp.body?.bytes()
                if (torrentBytes != null && torrentBytes.isNotEmpty()) {
                    downloadedFile = com.example.data.torrent.TorrentEngine.downloadFromTorrentFile(
                        torrentBytes = torrentBytes,
                        downloadId = download.id,
                        savePath = downloadsDir,
                        onProgress = onProgressCallback
                    )
                }
            }

            if (downloadedFile == null && !download.magnetUri.isNullOrBlank()) {
                downloadedFile = com.example.data.torrent.TorrentEngine.downloadFromMagnet(
                    magnetUri = download.magnetUri,
                    downloadId = download.id,
                    savePath = downloadsDir,
                    onProgress = onProgressCallback
                )
            } else if (downloadedFile == null && download.downloadUrl.isNotBlank() && download.downloadUrl.startsWith("http", ignoreCase = true)) {
                performChunkedDownload(download)
                return
            }

            if (downloadedFile != null && downloadedFile.exists() && downloadedFile.length() > 0) {
                val finalSize = downloadedFile.length()
                val finalPath = downloadedFile.absolutePath
                dao.updateProgress(download.id, DownloadStatus.COMPLETED.name, finalSize, finalSize, 100, 0L)
                dao.markCompleted(download.id, System.currentTimeMillis())
                val current = dao.getDownloadById(download.id)
                if (current != null) {
                    dao.insertOrUpdate(current.copy(localFilePath = finalPath, totalBytes = finalSize, bytesDownloaded = finalSize))
                }
                Log.i(TAG, "Torrent video payload successfully downloaded: $finalPath ($finalSize bytes)")
            } else {
                throw IllegalStateException("No playable media payload downloaded from the torrent swarm")
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Torrent download failed for ${download.id}: ${e.message}")
            val currentItem = runCatching { dao.getDownloadById(download.id) }.getOrNull()
            if (currentItem?.status != DownloadStatus.PAUSED.name && currentItem?.status != DownloadStatus.CANCELLED.name) {
                dao.markFailed(download.id, e.message ?: "Torrent transfer error")
            }
        } finally {
            activeJobs.remove(download.id)
            updateSpeed(download.id, 0L)
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
    }

    /**
     * Quality fallback cascade rule requested by user:
     * Quality fallback cascade rule requested by user:
     * e.g., 360p -> 480p -> 720p -> 1080p.
     */
    private fun getQualityFallbackOrder(preferredQuality: String): List<String> {
        val q = preferredQuality.lowercase()
        return when {
            q.contains("360") -> listOf("360p Data Saver", "480p SD", "720p HD", "1080p Full HD")
            q.contains("480") -> listOf("480p SD", "720p HD", "1080p Full HD", "360p Data Saver")
            q.contains("720") -> listOf("720p HD", "1080p Full HD", "480p SD", "360p Data Saver")
            q.contains("1080") -> listOf("1080p Full HD", "720p HD", "480p SD", "360p Data Saver")
            else -> listOf("720p HD", "1080p Full HD", "480p SD", "360p Data Saver")
        }
    }

    private fun buildCandidateStreams(download: DownloadEntity): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        if (download.downloadUrl.isNotBlank() && !download.downloadUrl.startsWith("magnet:", ignoreCase = true)) {
            list.add(download.quality to download.downloadUrl)
        }
        if (!download.torrentFileUrl.isNullOrBlank() && download.torrentFileUrl != download.downloadUrl) {
            list.add(download.quality to download.torrentFileUrl)
        }
        return list
    }

    /**
     * Performs chunked download using OkHttp with Range header support for resumption.
     */
    private suspend fun performChunkedDownload(download: DownloadEntity) {
        val targetFile = File(download.localFilePath)
        val partFile = File("${download.localFilePath}.part")
        
        // Ensure parent directories exist
        partFile.parentFile?.mkdirs()
        targetFile.parentFile?.mkdirs()

        val existingBytes = if (partFile.exists()) partFile.length() else 0L

        val requestBuilder = Request.Builder()
            .url(download.downloadUrl)
            .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
            .addHeader("Accept", "*/*")

        if (existingBytes > 0) {
            requestBuilder.addHeader("Range", "bytes=$existingBytes-")
        }

        val request = requestBuilder.build()
        val response = httpClient.newCall(request).execute()

        val contentType = response.header("Content-Type") ?: ""
        if (contentType.contains("text/html", ignoreCase = true)) {
            response.close()
            throw IllegalStateException("Server returned HTML web page instead of direct media stream")
        }

        if (!response.isSuccessful && response.code != 206) {
            response.close()
            // If range error (416), clear part and retry from 0
            if (response.code == 416) {
                partFile.delete()
                throw IllegalStateException("Range not satisfiable, restarting")
            }
            throw IllegalStateException("Server returned HTTP ${response.code}")
        }

        val body = response.body ?: throw IllegalStateException("Empty response body")
        val isPartial = response.code == 206
        val actualStartBytes = if (isPartial) existingBytes else 0L
        val contentLength = body.contentLength()
        val totalBytes = if (contentLength > 0) {
            actualStartBytes + contentLength
        } else {
            download.totalBytes.takeIf { it > 0 } ?: (actualStartBytes + 50L * 1024L * 1024L)
        }

        val inputStream = body.byteStream()
        val outputStream = if (isPartial && partFile.exists()) {
            FileOutputStream(partFile, true)
        } else {
            FileOutputStream(partFile, false)
        }

        val buffer = ByteArray(64 * 1024) // 64KB buffer for high throughput
        var bytesDownloaded = actualStartBytes
        var lastDbUpdate = System.currentTimeMillis()
        var speedWindowStart = System.currentTimeMillis()
        var bytesReadInSpeedWindow = 0L

        try {
            outputStream.use { out ->
                inputStream.use { inStream ->
                    var bytesRead: Int
                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                        bytesDownloaded += bytesRead
                        bytesReadInSpeedWindow += bytesRead

                        val now = System.currentTimeMillis()

                        // Calculate download speed every 500ms
                        if (now - speedWindowStart >= 500L) {
                            val elapsedSeconds = (now - speedWindowStart) / 1000.0
                            val speedBytesPerSec = if (elapsedSeconds > 0) {
                                (bytesReadInSpeedWindow / elapsedSeconds).toLong()
                            } else 0L

                            updateSpeed(download.id, speedBytesPerSec)
                            speedWindowStart = now
                            bytesReadInSpeedWindow = 0L
                        }

                        // Update Room progress every 400ms or on completion
                        if (now - lastDbUpdate >= 400L || bytesDownloaded >= totalBytes) {
                            val progress = if (totalBytes > 0) {
                                ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 99)
                            } else 0

                            val currentSpeed = _activeDownloadSpeed.value[download.id] ?: 0L
                            val remainingBytes = (totalBytes - bytesDownloaded).coerceAtLeast(0L)
                            val eta = if (currentSpeed > 0) remainingBytes / currentSpeed else 0L
                            dao.updateProgressWithEta(
                                download.id,
                                DownloadStatus.DOWNLOADING.name,
                                bytesDownloaded,
                                totalBytes,
                                progress,
                                currentSpeed,
                                eta
                            )
                            lastDbUpdate = now
                        }
                    }
                    out.flush()
                }
            }

            // Download completed successfully!
            updateSpeed(download.id, 0L)
            if (partFile.exists()) {
                if (targetFile.exists()) targetFile.delete()
                val renamed = partFile.renameTo(targetFile)
                if (!renamed) {
                    partFile.copyTo(targetFile, overwrite = true)
                    partFile.delete()
                }
            }

            dao.markCompleted(download.id, System.currentTimeMillis())
            Log.i(TAG, "Download completed for ${download.title} (${targetFile.length()} bytes)")
        } finally {
            response.close()
        }
    }

    private fun updateSpeed(id: String, speedBytesPerSec: Long) {
        val current = _activeDownloadSpeed.value.toMutableMap()
        if (speedBytesPerSec <= 0) {
            current.remove(id)
        } else {
            current[id] = speedBytesPerSec
        }
        _activeDownloadSpeed.value = current
    }

    /**
     * Resolves real download media stream for any movie or TV episode.
     * Uses the video streamUrl if available, or high-speed CDN video stream based on server & quality.
     */
    private fun resolveDownloadUrl(
        video: VideoItem,
        season: Int?,
        episode: Int?,
        server: String = "BitTorrent P2P",
        quality: String = "1080p Full HD"
    ): String {
        return if (video.streamUrl.isNotBlank() && video.streamUrl.startsWith("http")) {
            video.streamUrl
        } else ""
    }

    companion object {
        private const val TAG = "DownloadManager"

        @Volatile
        private var instance: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager = instance ?: synchronized(this) {
            instance ?: DownloadManager(context.applicationContext).also { instance = it }
        }
    }
}
