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
import com.example.data.torrent.MagnetParser
import com.example.data.torrent.TorrentIndexerService
import com.example.data.torrent.TrackerClient
import com.example.model.MediaType
import com.example.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlinx.coroutines.currentCoroutineContext
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import java.io.FileOutputStream
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
        subtitleCc: String = "English (CC)",
        enabledIndexers: Set<String>? = null
    ) {
        scope.launch {
            val tmdbId = canonicalTmdbId(video)
            val downloadId = movieDownloadId(tmdbId)
            // Match by deterministic id first, then by content key so legacy
            // torrent rows (dl_torrent_movie_<id>_<hash>) still resolve.
            val existing = dao.getDownloadById(downloadId)
                ?: dao.findDownload(tmdbId, null, null)
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

            var resolvedQuality = quality
            if (mediaUrl.isBlank()) {
                val year = video.releaseDateFormatted?.take(4)
                val torrents = withContext(Dispatchers.IO) {
                    TorrentIndexerService.resolveMovieTorrents(
                        imdbId = video.imdbId ?: video.tmdbId,
                        title = video.title,
                        year = year,
                        enabledIndexers = enabledIndexers
                    )
                }
                // Resolution-aware auto-pick: highest seeds for the requested
                // resolution, stepping up (then down) the ladder when the exact
                // resolution has no torrent, so the download never fails.
                // Records the actual quality served.
                val best = TorrentIndexerService.selectBestWithFallback(torrents, quality)?.source
                    ?: torrents.maxByOrNull { it.seeders }
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
                    resolvedQuality = best.quality
                }
            }

            val sanitizedTitle = sanitizeFilename(video.title)
            val ext = if (isTorrent && torrentFileUrl != null && totalBytes <= 0L) "torrent" else "mp4"
            // tmdbId in the filename keeps remakes / same-title movies apart
            // so two movies can never overwrite each other's bytes.
            val targetFile = File(downloadsDir, "movie_${tmdbId}_${sanitizedTitle}.$ext")

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
                quality = resolvedQuality,
                serverName = resolvedServer,
                subtitleCc = subtitleCc,
                subtitleLanguage = com.example.data.subtitles.normalizeSubtitleLanguage(subtitleCc),
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
        subtitleCc: String = "English (CC)",
        enabledIndexers: Set<String>? = null
    ) {
        scope.launch {
            val tmdbId = canonicalTmdbId(video)
            val downloadId = episodeDownloadId(tmdbId, episode.seasonNumber, episode.episodeNumber)
            // Deterministic id first, then content-key lookup so legacy
            // hash-suffixed torrent rows for the same S/E still resolve.
            val existing = dao.getDownloadById(downloadId)
                ?: dao.findDownload(tmdbId, episode.seasonNumber, episode.episodeNumber)
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

            var resolvedQuality = quality
            if (mediaUrl.isBlank()) {
                val torrents = withContext(Dispatchers.IO) {
                    TorrentIndexerService.resolveTvTorrents(
                        imdbId = video.imdbId ?: video.tmdbId,
                        showTitle = video.title,
                        seasonNumber = episode.seasonNumber,
                        episodeNumber = episode.episodeNumber,
                        enabledIndexers = enabledIndexers
                    )
                }
                // Resolution-aware auto-pick: highest seeds for the requested
                // resolution, stepping up (then down) the ladder when the exact
                // resolution has no torrent, so the download never fails.
                val best = TorrentIndexerService.selectBestWithFallback(torrents, quality)?.source
                    ?: torrents.maxByOrNull { it.seeders }
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
                    resolvedQuality = best.quality
                }
            }

            val sanitizedTitle = sanitizeFilename("${video.title}_S${episode.seasonNumber}E${episode.episodeNumber}")
            val ext = if (isTorrent && torrentFileUrl != null && totalBytes <= 0L) "torrent" else "mp4"
            // tmdbId + S/E in the filename guarantees episode A can never
            // overwrite episode B on disk, even for same-title remakes.
            val targetFile = File(downloadsDir, "tv_${tmdbId}_S${episode.seasonNumber}E${episode.episodeNumber}_${sanitizedTitle}.$ext")
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
                quality = resolvedQuality,
                serverName = resolvedServer,
                subtitleCc = subtitleCc,
                subtitleLanguage = com.example.data.subtitles.normalizeSubtitleLanguage(subtitleCc),
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
        subtitleCc: String = "English (CC)",
        enabledIndexers: Set<String>? = null
    ) {
        scope.launch {
            val seasonEpisodes = episodes.filter { it.seasonNumber == seasonNumber }
            if (seasonEpisodes.isEmpty()) return@launch

            for (episode in seasonEpisodes) {
                downloadEpisode(video, episode, quality, server, subtitleCc, enabledIndexers)
            }
        }
    }

    /**
     * Download media via authentic Torrent / P2P Swarm source.
     *
     * Episode identity is the (tmdbId, season, episode) triple — never the
     * info-hash. One deterministic row per episode guarantees the Downloads
     * label and the bytes on disk can never drift apart, and a re-download
     * of the same episode replaces its row instead of duplicating it.
     */
    fun downloadTorrent(
        video: VideoItem,
        source: TorrentSource,
        season: Int? = null,
        episode: Int? = null,
        episodeTitle: String? = null,
        episodeStillUrl: String? = null
    ) {
        scope.launch {
            val tmdbId = canonicalTmdbId(video)
            val isTv = video.mediaType == MediaType.TV_SHOW
            val safeSeason = (season ?: 1).coerceAtLeast(1)
            val safeEpisode = (episode ?: 1).coerceAtLeast(1)
            // Strict guard: WRONG-episode torrents never queue (packs + exact
            // pass; engine extracts the right inner file from packs).
            if (isTv) {
                val kind = com.example.data.torrent.TorrentMatcher
                    .matchEpisode(source.title, safeSeason, safeEpisode).kind
                if (kind == com.example.data.torrent.EpisodeMatch.WRONG) {
                    val downloadId = episodeDownloadId(tmdbId, safeSeason, safeEpisode)
                    dao.insertOrUpdate(
                        DownloadEntity(
                            id = downloadId,
                            tmdbId = tmdbId,
                            mediaType = video.mediaType.name,
                            title = "${video.title} - S${safeSeason}:E${safeEpisode}",
                            seriesTitle = video.title,
                            seasonNumber = safeSeason,
                            episodeNumber = safeEpisode,
                            downloadUrl = "",
                            localFilePath = File(
                                downloadsDir,
                                "tv_${tmdbId}_S${safeSeason}E${safeEpisode}.mp4"
                            ).absolutePath,
                            status = DownloadStatus.FAILED.name,
                            errorMessage = "Blocked: torrent is not S${safeSeason}:E${safeEpisode} (${source.title.take(60)})",
                            quality = source.quality,
                            serverName = "Torrent (${source.provider})"
                        )
                    )
                    return@launch
                }
            }
            // Deterministic id: same episode always maps to the same row,
            // regardless of which torrent (quality/provider) served it.
            val downloadId = if (isTv) {
                episodeDownloadId(tmdbId, safeSeason, safeEpisode)
            } else {
                movieDownloadId(tmdbId)
            }

            // Episode-scoped dedup: an info-hash match only counts when it is
            // for the SAME episode. A global hash match used to re-queue S1E1
            // when the user asked for S1E2 (season bulk reuses one source).
            val existingById = dao.getDownloadById(downloadId)
                ?: if (isTv) dao.findDownload(tmdbId, safeSeason, safeEpisode)
                else dao.findDownload(tmdbId, null, null)
            // Legacy hash-suffixed rows (dl_torrent_tv_.._<hash8>) for this
            // same episode resolve via findDownload above — migrate them onto
            // the deterministic id so tracking converges to one row per S/E.
            val legacyRow = if (isTv) {
                dao.getDownloadsForTmdbId(tmdbId).firstOrNull {
                    it.mediaType == MediaType.TV_SHOW.name &&
                        it.seasonNumber == safeSeason &&
                        it.episodeNumber == safeEpisode &&
                        it.id != downloadId
                }
            } else {
                dao.getDownloadsForTmdbId(tmdbId).firstOrNull {
                    it.mediaType != MediaType.TV_SHOW.name && it.id != downloadId
                }
            }
            val existing = existingById ?: legacyRow?.let {
                // Point the legacy row at the deterministic id; the old file
                // (if any) is kept until the new bytes land.
                dao.deleteById(it.id)
                dao.getDownloadById(downloadId)
            }
            if (existing != null) {
                val file = File(existing.localFilePath)
                val sameTorrent = existing.infoHash?.equals(source.infoHash, ignoreCase = true) == true
                if (existing.status == DownloadStatus.COMPLETED.name && file.exists() && file.length() > 0 && sameTorrent) {
                    return@launch
                }
                if (existing.status == DownloadStatus.COMPLETED.name && (!file.exists() || file.length() <= 0L)) {
                    // Completed row but bytes gone (user cleared storage):
                    // fall through and re-queue with the new torrent.
                } else if (sameTorrent) {
                    dao.markQueued(existing.id)
                    triggerQueueProcessing()
                    return@launch
                }
                // Same episode, different torrent (e.g. new quality): fall
                // through and REPLACE the row below so only one S/E row exists.
                // Remove the stale file so a failed replace can't leave the
                // old episode's bytes behind the new episode's label.
                runCatching { File("${existing.localFilePath}.part").delete() }
                if (!sameTorrent) {
                    runCatching { file.delete() }
                }
            }

            val sanitizedTitle = sanitizeFilename(
                if (isTv) "${video.title}_S${safeSeason}E${safeEpisode}_${source.quality}"
                else "${video.title}_${source.quality}"
            )
            val ext = if (source.torrentFileUrl != null && source.sizeBytes <= 0) "torrent" else "mp4"
            val targetFile = if (isTv) {
                File(downloadsDir, "tv_${tmdbId}_S${safeSeason}E${safeEpisode}_${sanitizedTitle}.$ext")
            } else {
                File(downloadsDir, "movie_${tmdbId}_${sanitizedTitle}.$ext")
            }

            val realEpisodeTitle = episodeTitle?.takeIf { it.isNotBlank() }
                ?: "${source.quality} ${source.releaseType}"
            val displayTitle = if (isTv) {
                val namePart = episodeTitle?.takeIf { it.isNotBlank() }?.let { " $it" } ?: ""
                "${video.title} - S${safeSeason}:E${safeEpisode}$namePart"
            } else {
                "${video.title} (${source.quality} ${source.releaseType})"
            }

            val entity = DownloadEntity(
                id = downloadId,
                tmdbId = tmdbId,
                mediaType = video.mediaType.name,
                title = displayTitle,
                seriesTitle = if (isTv) video.title else null,
                seasonNumber = if (isTv) safeSeason else null,
                episodeNumber = if (isTv) safeEpisode else null,
                episodeTitle = if (isTv) realEpisodeTitle else null,
                posterUrl = video.posterUrl ?: video.thumbnailUrl,
                backdropUrl = video.backdropUrl,
                thumbnailUrl = episodeStillUrl ?: video.thumbnailUrl,
                downloadUrl = source.torrentFileUrl ?: source.magnetUri,
                localFilePath = targetFile.absolutePath,
                status = DownloadStatus.QUEUED.name,
                quality = source.quality,
                serverName = "Torrent (${source.provider})",
                subtitleCc = "Built-in / CC",
                subtitleLanguage = "en",
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
     * Auto-download helper for the "Auto-pick best torrent" option: resolves
     * live sources, keeps only the requested resolution, picks the highest
     * seed count, and queues it without further user interaction.
     * Returns via callback so the UI can report what was actually picked
     * (or why nothing matched). Runs on the manager scope.
     */
    fun autoDownloadBestTorrent(
        video: VideoItem,
        requestedQuality: String,
        season: Int? = null,
        episode: Int? = null,
        onResult: ((TorrentSource?) -> Unit)? = null,
        enabledIndexers: Set<String>? = null,
        episodeTitle: String? = null,
        episodeStillUrl: String? = null
    ) {
        scope.launch {
            val isTv = video.mediaType == MediaType.TV_SHOW
            val torrents = withContext(Dispatchers.IO) {
                if (isTv) {
                    TorrentIndexerService.resolveTvTorrents(
                        imdbId = video.imdbId ?: video.tmdbId,
                        showTitle = video.title,
                        seasonNumber = season ?: 1,
                        episodeNumber = episode ?: 1,
                        enabledIndexers = enabledIndexers
                    )
                } else {
                    TorrentIndexerService.resolveMovieTorrents(
                        imdbId = video.imdbId ?: video.tmdbId,
                        title = video.title,
                        year = video.releaseDateFormatted?.take(4),
                        enabledIndexers = enabledIndexers
                    )
                }
            }
            // Stepwise fallback (exact -> higher -> lower) so a missing
            // resolution never means a failed download.
            val pick = TorrentIndexerService.selectBestWithFallback(torrents, requestedQuality)
            if (pick != null) {
                downloadTorrent(video, pick.source, season, episode, episodeTitle, episodeStillUrl)
                onResult?.invoke(pick.source)
                if (!pick.isExactMatch) {
                    Log.i(TAG, "Auto-pick fallback: wanted $requestedQuality, grabbed ${pick.source.quality} (▲${pick.source.seeders})")
                }
            } else {
                onResult?.invoke(null)
                Log.w(TAG, "Auto-pick found zero sources among ${torrents.size} candidates")
            }
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
            val directHttp = directDownloadUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
            val validHash = MagnetParser.normalizeInfoHash(parsed?.exactTopic)
            if (validHash == null && directHttp == null) {
                // Fail fast on malformed magnets instead of queueing an
                // undownloadable placeholder (previously hash_<timestamp>).
                dao.insertOrUpdate(
                    DownloadEntity(
                        id = "dl_magnet_invalid_${System.currentTimeMillis().toString(16)}",
                        tmdbId = "custom",
                        mediaType = "TORRENT",
                        title = customTitle?.takeIf { it.isNotBlank() } ?: "Invalid magnet link",
                        downloadUrl = magnetUri,
                        localFilePath = File(downloadsDir, "invalid_magnet.mp4").absolutePath,
                        status = DownloadStatus.FAILED.name,
                        errorMessage = "Invalid magnet link: missing a valid info-hash",
                        quality = "Custom P2P",
                        serverName = "Magnet Link",
                        subtitleCc = "Built-in / CC",
                subtitleLanguage = "en",
                        isTorrent = true,
                        infoHash = parsed?.exactTopic ?: "",
                        magnetUri = magnetUri
                    )
                )
                return@launch
            }
            val infoHash = validHash ?: "http_${System.currentTimeMillis().toString(16)}"
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
                subtitleLanguage = "en",
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
            clearProgressSnapshot(id)
            notifyService()
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
            deleteSidecarsFor(id)
            runCatching { stagingDirFor(id).deleteRecursively() }
            dao.deleteById(id)
            updateSpeed(id, 0L)
            clearProgressSnapshot(id)
            notifyService()
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
            deleteSidecarsFor(id)
            runCatching { stagingDirFor(id).deleteRecursively() }
            dao.deleteById(id)
            updateSpeed(id, 0L)
            clearProgressSnapshot(id)
            notifyService()
        }
    }

    fun pauseAll() {
        scope.launch {
            // Cancel per-download coroutines (not the queue processor itself).
            activeJobs.forEach { (_, job) -> job.cancel() }
            activeJobs.clear()
            dao.pauseAll()
            _activeDownloadSpeed.value = emptyMap()
            notifyService()
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

    fun retryAllFailed() {
        scope.launch {
            dao.retryAllFailed()
            triggerQueueProcessing()
        }
    }

    fun clearFailed() {
        scope.launch {
            // Sidecars belong to failed rows too (partial L0 pairings).
            val failed = runCatching { dao.getAllDownloads() }.getOrDefault(emptyList())
                .filter { it.status == DownloadStatus.FAILED.name }
            failed.forEach { deleteSidecarsFor(it.id) }
            dao.clearFailed()
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
                        // Launch each download in its own Job so that pause/cancel only affects
                        // that download, not the entire queue loop.
                        val perDownloadJob = coroutineScope {
                            launch {
                                executeDownloadWithRetry(nextDownload)
                            }
                        }
                        activeJobs[nextDownload.id] = perDownloadJob
                        try {
                            perDownloadJob.join()
                        } finally {
                            activeJobs.remove(nextDownload.id)
                        }
                        notifyService()
                    } else {
                        // Sleep briefly before checking for newly queued items
                        delay(1000L)
                    }
                    // Always pause to check if the queue is idle and we can stop the service
                    if (activeJobs.isEmpty()) {
                        notifyService()
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Queue processor encountered error", e)
            } finally {
                isLoopRunning.set(false)
                DownloadService.stop(context)
            }
        }
    }

    private fun notifyService() {
        val active = activeJobs.size
        if (active <= 0) {
            lastProgressSnapshot = null
            DownloadService.stop(context)
            return
        }
        val totalSpeed = _activeDownloadSpeed.value.values.sum()
        val snap = lastProgressSnapshot
        if (snap != null) {
            DownloadService.start(
                context,
                snap.copy(activeCount = active, speedBytesPerSec = totalSpeed)
            )
        } else {
            DownloadService.start(context, active, totalSpeed)
        }
    }

    // ------------------------------------------------------------------
    // Notification-bar progress (throttled so the shade never flickers)
    // ------------------------------------------------------------------

    @Volatile
    private var lastProgressSnapshot: DownloadService.ProgressSnapshot? = null
    @Volatile
    private var lastProgressId: String? = null
    @Volatile
    private var lastNotifPushMs = 0L

    /**
     * Caches the latest progress and forwards it to the foreground
     * notification at most once every 2 seconds. Cheap enough to call from
     * every progress tick on both the torrent and HTTP paths.
     */
    private fun pushProgressThrottled(
        downloadId: String,
        title: String,
        progressPercent: Int,
        speedBytesPerSec: Long,
        etaSeconds: Long
    ) {
        lastProgressId = downloadId
        lastProgressSnapshot = DownloadService.ProgressSnapshot(
            activeCount = activeJobs.size.coerceAtLeast(1),
            title = title,
            progressPercent = progressPercent.coerceIn(0, 100),
            speedBytesPerSec = speedBytesPerSec.coerceAtLeast(0L),
            etaSeconds = etaSeconds.coerceAtLeast(0L)
        )
        val now = System.currentTimeMillis()
        if (now - lastNotifPushMs < 2000L) return
        lastNotifPushMs = now
        val totalSpeed = _activeDownloadSpeed.value.values.sum()
        DownloadService.update(
            context,
            lastProgressSnapshot!!.copy(
                activeCount = activeJobs.size.coerceAtLeast(1),
                speedBytesPerSec = totalSpeed
            )
        )
    }

    /** Drops a stale snapshot so a finished file's title never lingers. */
    private fun clearProgressSnapshot(downloadId: String) {
        if (lastProgressId == downloadId) {
            lastProgressId = null
            lastProgressSnapshot = null
        }
    }

    /**
     * Executes the download with automatic quality fallback and mirror fallback.
     * Runs inside a per-download child coroutine;its cancellation only affects this download.
     */
    private suspend fun executeDownloadWithRetry(download: DownloadEntity) {
        if (download.isTorrent) {
            executeTorrentDownload(download)
            return
        }

        val candidates = buildCandidateStreams(download)
        var completed = false
        var lastErrorMsg: String? = null

        for ((index, candidate) in candidates.withIndex()) {
            val (candidateQuality, candidateUrl) = candidate
            if (completed || !currentCoroutineContext().isActive) break

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

        if (!completed && currentCoroutineContext().isActive) {
            val finalCheck = runCatching { dao.getDownloadById(download.id) }.getOrNull()
            if (finalCheck?.status != DownloadStatus.PAUSED.name && finalCheck?.status != DownloadStatus.CANCELLED.name) {
                val msg = lastErrorMsg ?: "Download failed. Check network connection and tap to retry."
                dao.markFailed(download.id, msg)
                clearProgressSnapshot(download.id)
                DownloadService.notifyFailed(context, download.id, download.title, msg)
            }
        }

        updateSpeed(download.id, 0L)
    }

    /**
     * Executes authentic P2P Torrent download with tracker swarm queries,
     * metadata extraction, chunked Range resumption, live speeds, and ETA.
     *
     * Each download stages into its OWN subfolder
     * (downloads/staging_<downloadId>/) and the chosen video payload is then
     * moved to this row's deterministic localFilePath. The DB row therefore
     * always points at exactly this episode's bytes — a previous episode's
     * file can never be picked up, and two episodes can never overwrite
     * each other even when their torrents share an internal filename.
     */
    private suspend fun executeTorrentDownload(download: DownloadEntity) {
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

            // 2. Real BitTorrent Swarm download via TorrentEngine, staged in
            // this download's own folder so torrents can never see or
            // overwrite each other's files.
            var downloadedFile: File? = null
            val stagingDir = stagingDirFor(download.id)
            runCatching { if (!stagingDir.exists()) stagingDir.mkdirs() }

            val onProgressCallback: (com.example.data.torrent.TorrentProgress) -> Unit = { p ->
                updateSpeed(download.id, p.downloadSpeed)
                scope.launch {
                    // Cap live progress at 99; the final 100 is only written
                    // after the video file is verified on disk below.
                    val capped = if (p.progress >= 1f) 99 else (p.progress * 100).toInt().coerceIn(0, 99)
                    dao.updateProgressWithEta(
                        download.id,
                        DownloadStatus.DOWNLOADING.name,
                        p.bytesDownloaded.coerceAtMost(p.totalBytes),
                        p.totalBytes,
                        capped,
                        p.downloadSpeed,
                        p.eta
                    )
                    pushProgressThrottled(download.id, download.title, capped, p.downloadSpeed, p.eta)
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
                val torrentBytes = runCatching {
                    val req = Request.Builder()
                        .url(download.torrentFileUrl)
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                    httpClient.newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        val bytes = resp.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) bytes else null
                    }
                }.getOrNull()
                if (torrentBytes != null && torrentBytes.isNotEmpty()) {
                    downloadedFile = com.example.data.torrent.TorrentEngine.downloadFromTorrentFile(
                        torrentBytes = torrentBytes,
                        downloadId = download.id,
                        savePath = stagingDir,
                        onProgress = onProgressCallback,
                        expectedSeason = download.seasonNumber,
                        expectedEpisode = download.episodeNumber
                    )
                } else {
                    Log.w(TAG, "Torrent file fetch failed for ${download.id}, falling back to magnet")
                }
            }

            if (downloadedFile == null && !download.magnetUri.isNullOrBlank()) {
                downloadedFile = com.example.data.torrent.TorrentEngine.downloadFromMagnet(
                    magnetUri = download.magnetUri,
                    downloadId = download.id,
                    savePath = stagingDir,
                    onProgress = onProgressCallback,
                    expectedSeason = download.seasonNumber,
                    expectedEpisode = download.episodeNumber
                )
            } else if (downloadedFile == null && download.downloadUrl.isNotBlank() && download.downloadUrl.startsWith("http", ignoreCase = true)) {
                performChunkedDownload(download)
                return
            }

            if (downloadedFile != null && downloadedFile.exists() && downloadedFile.length() > 0) {
                // Move the staged payload onto this row's deterministic file.
                // This is the 1:1 label<->bytes guarantee: the DB path below
                // is always download.localFilePath (tv_<tmdb>_S..E...mp4),
                // never the torrent's internal filename.
                val targetFile = File(download.localFilePath)
                runCatching { targetFile.parentFile?.mkdirs() }
                val stagedSize = downloadedFile.length()
                val movedOk = runCatching {
                    if (targetFile.exists()) targetFile.delete()
                    // Same-volume rename is atomic; cross-volume falls back
                    // to copy + delete of the staged file only.
                    val renamed = downloadedFile.renameTo(targetFile)
                    if (!renamed && downloadedFile.exists()) {
                        downloadedFile.copyTo(targetFile, overwrite = true)
                        downloadedFile.delete()
                    }
                    targetFile.exists() && targetFile.length() > 0
                }.getOrDefault(false)
                if (!movedOk) {
                    throw IllegalStateException("Could not finalize episode file for ${download.id}")
                }
                // L0 offline subtitles: collect staged sibling sidecars BEFORE
                // the staging wipe below. Same release = same timing, so these
                // need no offset correction. Runs inline (small files only).
                val stagedSidecar = runCatching {
                    collectStagedSubtitles(stagingDir, download)
                }.getOrNull()
                // Stop seeding + wipe the staging residue (the moved copy
                // lives outside stagingDir so it survives the cleanup).
                runCatching { com.example.data.torrent.TorrentEngine.cancel(download.id) }
                runCatching {
                    if (stagingDir.exists()) {
                        stagingDir.walkTopDown().filter { it.isFile }.forEach { runCatching { it.delete() } }
                        stagingDir.delete()
                    }
                }
                val finalSize = targetFile.length().takeIf { it > 0 } ?: stagedSize
                val finalPath = targetFile.absolutePath
                dao.updateProgress(download.id, DownloadStatus.COMPLETED.name, finalSize, finalSize, 100, 0L)
                dao.markCompleted(download.id, System.currentTimeMillis())
                val current = dao.getDownloadById(download.id)
                if (current != null) {
                    dao.insertOrUpdate(
                        current.copy(
                            localFilePath = finalPath,
                            totalBytes = finalSize,
                            bytesDownloaded = finalSize,
                            progressPercent = 100,
                            downloadSpeedBytesPerSec = 0L,
                            etaSeconds = 0L,
                            errorMessage = null
                        )
                    )
                }
                Log.i(TAG, "Torrent video payload successfully downloaded: $finalPath ($finalSize bytes)")
                clearProgressSnapshot(download.id)
                DownloadService.notifyCompleted(context, download.id, download.title)
                // L1 auto-fetch (fire-and-forget): only when no sibling sidecar
                // landed above and the user left auto-download on. Never blocks
                // the COMPLETED marking above.
                if (stagedSidecar == null) {
                    scope.launch {
                        runCatching {
                            val prefs = com.example.data.SettingsManager(context)
                            if (prefs.isSubtitleAutoDownloadEnabled) {
                                val result = com.example.data.subtitles.SubtitleManager
                                    .getInstance(context)
                                    .fetchBestForDownload(download.id)
                                if (result.file != null) {
                                    Log.i(TAG, "Auto-fetched ${result.source} subtitles for ${download.id}")
                                }
                            }
                        }
                    }
                }
            } else {
                throw IllegalStateException("No playable media payload downloaded from the torrent swarm")
            }
        } catch (e: CancellationException) {
            // Pause/cancel tears down this coroutine: never record it as FAILED,
            // just propagate so pause/resume/delete stay exact.
            Log.i(TAG, "Torrent download cancelled for ${download.id}")
            throw e
        } catch (e: Throwable) {
            Log.w(TAG, "Torrent download failed for ${download.id}: ${e.message}")
            val currentItem = runCatching { dao.getDownloadById(download.id) }.getOrNull()
            if (currentItem?.status != DownloadStatus.PAUSED.name && currentItem?.status != DownloadStatus.CANCELLED.name) {
                val msg = e.message ?: "Torrent transfer error"
                dao.markFailed(download.id, msg)
                clearProgressSnapshot(download.id)
                DownloadService.notifyFailed(context, download.id, download.title, msg)
            }
        } finally {
            updateSpeed(download.id, 0L)
        }
    }

    private fun sanitizeFilename(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
    }

    /** Per-download staging folder: torrents never share a save directory. */
    private fun stagingDirFor(downloadId: String): File {
        val safe = downloadId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
        return File(downloadsDir, "staging_$safe")
    }

    // ------------------------------------------------------------------
    // Offline subtitle (CC) sidecars
    // ------------------------------------------------------------------

    /**
     * L0 subtitle collection: scans [stagingDir] for downloaded sibling
     * sidecars, validates the best one parses, copies it into
     * downloads/subs/ under this download's id, and records it on the row.
     * Must be called BEFORE the staging wipe. Returns the saved file, if any.
     */
    private suspend fun collectStagedSubtitles(
        stagingDir: File,
        download: DownloadEntity
    ): File? {
        val language = com.example.data.subtitles.normalizeSubtitleLanguage(
            download.subtitleLanguage
                ?: com.example.data.SettingsManager(context).offlineSubtitleLanguage
        )
        if (language == "off" || !stagingDir.exists()) return null
        val staged = runCatching {
            stagingDir.walkTopDown().maxDepth(4).filter { f ->
                f.isFile && f.length() > 0 &&
                    f.length() <= com.example.data.subtitles.MAX_SIBLING_SUBTITLE_BYTES &&
                    f.name.substringAfterLast('.', "").lowercase() in
                    com.example.data.subtitles.SUBTITLE_EXTENSIONS
            }.toList()
        }.getOrDefault(emptyList())
        if (staged.isEmpty()) {
            // No sibling, but the container itself may carry tracks (mkv/mp4).
            val embedded = runCatching { probeEmbeddedSubtitles(File(download.localFilePath)) }
                .getOrDefault(false)
            if (embedded) dao.updateEmbeddedSubtitleFlag(download.id, true)
            return null
        }
        // Prefer a filename carrying the requested language, else the largest
        // (packs often hold several languages; largest ≈ most cues).
        val langLower = language.lowercase()
        val picked = staged.firstOrNull { f ->
            val n = f.name.lowercase()
            n.contains(".$langLower.") || n.contains("_$langLower") ||
                n.contains("[$langLower]") || n.contains(langLower)
        } ?: staged.maxByOrNull { it.length() } ?: return null
        val bytes = runCatching { picked.readBytes() }.getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: return null
        val text = com.example.data.subtitles.SrtSync.decodeBytes(bytes)
        if (com.example.data.subtitles.SrtSync.parse(text).isEmpty()) return null
        val ext = picked.name.substringAfterLast('.', "srt").lowercase()
            .takeIf { it in com.example.data.subtitles.SUBTITLE_EXTENSIONS } ?: "srt"
        val saved = saveSidecarFor(download.id, language, ext, bytes) ?: return null
        val embedded = runCatching { probeEmbeddedSubtitles(File(download.localFilePath)) }
            .getOrDefault(false)
        val info = com.example.data.subtitles.SubtitleFileInfo(
            path = saved.absolutePath,
            language = language,
            label = "${language.uppercase()} · ${ext.uppercase()} (paired)",
            format = ext,
            releaseName = download.title,
            hearingImpaired = false
        )
        dao.updateSubtitleLanguage(download.id, language)
        dao.updateSubtitleFiles(
            id = download.id,
            path = saved.absolutePath,
            filesJson = com.example.data.subtitles.encodeSubtitleManifest(listOf(info)),
            releaseName = download.title,
            hasEmbedded = embedded
        )
        Log.i(TAG, "Paired staged subtitles for ${download.id}: ${picked.name}")
        return saved
    }

    private fun saveSidecarFor(
        downloadId: String,
        language: String,
        ext: String,
        bytes: ByteArray
    ): File? {
        return try {
            val subsDir = File(downloadsDir, "subs")
            if (!subsDir.exists()) subsDir.mkdirs()
            val safe = downloadId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
            val file = File(subsDir, "$safe.$language.$ext")
            file.writeBytes(bytes)
            if (file.exists() && file.length() > 0) file else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Best-effort embedded-track probe via MediaExtractor (no playback).
     * True when the container exposes a text subtitle track.
     */
    private fun probeEmbeddedSubtitles(file: File): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        val ext = file.name.substringAfterLast('.', "").lowercase()
        if (ext !in com.example.data.subtitles.EMBEDDED_SUBTITLE_CONTAINERS) return false
        return try {
            val extractor = android.media.MediaExtractor()
            try {
                extractor.setDataSource(file.absolutePath)
                (0 until extractor.trackCount).any { i ->
                    val mime = extractor.getTrackFormat(i)
                        .getString(android.media.MediaFormat.KEY_MIME).orEmpty()
                    mime.startsWith("text/") || mime.startsWith("application/")
                }
            } finally {
                runCatching { extractor.release() }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * On-demand subtitle fetch (L1→L2→L3 cascade). Result is delivered on the
     * manager scope so callers can invoke from the UI thread.
     */
    fun fetchSubtitlesForDownload(
        id: String,
        force: Boolean = false,
        onResult: ((file: File?, message: String) -> Unit)? = null
    ) {
        scope.launch {
            val result = runCatching {
                com.example.data.subtitles.SubtitleManager.getInstance(context)
                    .fetchBestForDownload(id, force)
            }.getOrNull()
            if (result?.file != null) {
                onResult?.invoke(result.file, "Subtitles downloaded (${result.source})")
            } else {
                onResult?.invoke(null, result?.error ?: "Subtitle download failed")
            }
        }
    }

    /** Persists manual A/V sync correction (clamped). Applied by the player. */
    fun updateSubtitleOffset(id: String, offsetMs: Long) {
        scope.launch {
            val clamped = offsetMs.coerceIn(
                -com.example.data.subtitles.MAX_SUBTITLE_OFFSET_MS,
                com.example.data.subtitles.MAX_SUBTITLE_OFFSET_MS
            )
            dao.updateSubtitleOffset(id, clamped)
        }
    }

    fun setSelectedSubtitleTrack(id: String, trackId: String?) {
        scope.launch { dao.updateSelectedSubtitleTrack(id, trackId) }
    }

    /** Removes sidecar files belonging to [id] (called on delete/cancel). */
    private fun deleteSidecarsFor(id: String) {
        runCatching {
            val safe = id.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
            File(downloadsDir, "subs").listFiles { f -> f.isFile && f.name.startsWith("${safe}.") }
                ?.forEach { runCatching { it.delete() } }
        }
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
                            pushProgressThrottled(download.id, download.title, progress, currentSpeed, eta)
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
            clearProgressSnapshot(download.id)
            DownloadService.notifyCompleted(context, download.id, download.title)
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

    companion object {
        private const val TAG = "DownloadManager"

        /** Canonical content key: tmdbId when present, else the catalog id. */
        fun canonicalTmdbId(video: VideoItem): String = video.tmdbId ?: video.id

        /** One row per movie. Deterministic so re-downloads replace, never duplicate. */
        fun movieDownloadId(tmdbId: String): String = "dl_movie_$tmdbId"

        /**
         * One row per episode. Deterministic so S1E1 can never be confused
         * with S1E2, and a re-download of the same episode replaces its row
         * instead of creating a second row that could point at another file.
         */
        fun episodeDownloadId(tmdbId: String, season: Int, episode: Int): String =
            "dl_tv_${tmdbId}_s${season}_e${episode}"

        @Volatile
        private var instance: DownloadManager? = null

        fun getInstance(context: Context): DownloadManager = instance ?: synchronized(this) {
            instance ?: DownloadManager(context.applicationContext).also { instance = it }
        }
    }
}
