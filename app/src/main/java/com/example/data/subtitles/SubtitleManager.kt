package com.example.data.subtitles

import android.content.Context
import android.util.Log
import com.example.data.SettingsManager
import com.example.data.local.DownloadDao
import com.example.data.local.LocalDatabase
import com.example.data.torrent.TorrentIndexerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Offline subtitle (CC) fetch cascade.
 *
 * Order: Wyzie aggregator (8 providers, one call) → SubDL direct →
 * YIFY scrape (movies, keyless). Torrent-embedded/sibling subtitles (L0)
 * are handled separately in [com.example.data.download.DownloadManager]
 * because they need zero network and are perfectly synced.
 *
 * Key resolution: user BYOK ([SettingsManager.wyzieApiKey]/[subdlApiKey])
 * → embedded [BuildConfig] key from local .env → unauthenticated
 * (works only for keyless fallbacks). The Wyzie [baseUrl] comes from
 * [SettingsManager.subtitleProxyBaseUrl], which defaults to sub.wyzie.io
 * but can point at a self-hosted Cloudflare Worker that injects the key
 * server-side (Wyzie's guidance for distributed apps).
 */
class SubtitleManager private constructor(private val appContext: Context) {

    private val dao: DownloadDao = LocalDatabase.get(appContext).downloadDao()
    private val settings: SettingsManager = SettingsManager(appContext)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    fun subsDir(): File {
        val external = appContext.getExternalFilesDir(null)
        val dir = File(external ?: appContext.filesDir, "downloads/subs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    data class FetchResult(
        val file: File?,
        val candidate: SubtitleCandidate?,
        val source: String,
        val error: String?
    )

    /**
     * Fetches the best-synced sidecar for [downloadId] and persists it.
     * Safe to call repeatedly: skips when a sidecar already exists unless
     * [force] is true. Returns the saved file or the reason it failed.
     */
    suspend fun fetchBestForDownload(
        downloadId: String,
        force: Boolean = false
    ): FetchResult = withContext(Dispatchers.IO) {
        val entity = dao.getDownloadById(downloadId)
            ?: return@withContext FetchResult(null, null, "none", "Download not found")
        val language = normalizeSubtitleLanguage(
            entity.subtitleLanguage ?: settings.offlineSubtitleLanguage
        )
        if (language == "off") {
            return@withContext FetchResult(null, null, "none", "Subtitles are off for this download")
        }
        if (!force && !entity.subtitleFilePath.isNullOrBlank() && File(entity.subtitleFilePath).exists()) {
            return@withContext FetchResult(
                File(entity.subtitleFilePath), null, "cache",
                error = null
            )
        }

        val isTv = entity.mediaType == "TV_SHOW"
        val season = entity.seasonNumber ?: 1
        val episode = entity.episodeNumber ?: 1
        val imdbId = TorrentIndexerService.resolveImdbId(entity.tmdbId, isMovie = !isTv)
            ?: entity.tmdbId.takeIf { it.startsWith("tt", ignoreCase = true) }
        val titleForSearch = entity.seriesTitle ?: entity.title
        val torrentHint = listOfNotNull(
            entity.title,
            entity.quality,
            entity.serverName
        ).joinToString(" ")

        // L1: Wyzie aggregator.
        // Key order: user BYOK → embedded .env key → unauthenticated.
        // BuildConfig fields come from .env/.env.example via the secrets
        // plugin; reflection keeps compilation safe when a key is absent.
        val wyzieKey = settings.wyzieApiKey.takeIf { it.isNotBlank() }
            ?: buildConfigKey("WYZIE_API_KEY").takeIf { it.isNotBlank() }
        val baseUrl = settings.subtitleProxyBaseUrl
        val wyzieCandidates = if (imdbId != null || entity.tmdbId.isNotBlank()) {
            fetchWyzie(
                baseUrl = baseUrl,
                imdbId = imdbId,
                tmdbId = entity.tmdbId,
                season = season.takeIf { isTv },
                episode = episode.takeIf { isTv },
                language = language,
                apiKey = wyzieKey
            )
        } else emptyList()

        // L2: SubDL direct (generous anonymous quota).
        val subdlKey = settings.subdlApiKey.takeIf { it.isNotBlank() }
            ?: buildConfigKey("SUBDL_API_KEY").takeIf { it.isNotBlank() }
        val subdlCandidates = if (!subdlKey.isNullOrBlank()) {
            fetchSubdl(
                apiKey = subdlKey,
                imdbId = imdbId,
                tmdbId = entity.tmdbId,
                title = titleForSearch,
                season = season.takeIf { isTv },
                episode = episode.takeIf { isTv },
                language = language,
                isMovie = !isTv
            )
        } else emptyList()

        // L3: YIFY scrape (movies only, keyless).
        val yifyCandidates = if (!isTv) {
            fetchYify(
                imdbId = imdbId ?: entity.tmdbId,
                language = language
            )
        } else emptyList()

        val all = (wyzieCandidates + subdlCandidates + yifyCandidates)
            .filter { it.language.isBlank() || it.language == language }
        if (all.isEmpty()) {
            val hint = if (wyzieKey.isNullOrBlank() && subdlKey.isNullOrBlank()) {
                "No subtitles found. Add a free Wyzie/SubDL key in subtitle settings, or import an .srt file."
            } else {
                "No $language subtitles found for this title yet. Try another language or import an .srt file."
            }
            return@withContext FetchResult(null, null, "none", hint)
        }

        val best = SubtitleSources.selectBestCandidate(
            candidates = all,
            torrentTitle = torrentHint,
            preferHearingImpaired = settings.isSubtitlePreferHearingImpaired
        ) ?: return@withContext FetchResult(null, null, "none", "No subtitles found")

        return@withContext downloadCandidate(entity.id, language, best)
    }

    /** Imports a user-picked .srt/.vtt file (SAF uri bytes) as the sidecar. */
    suspend fun importSidecar(
        downloadId: String,
        fileName: String,
        bytes: ByteArray,
        language: String
    ): FetchResult = withContext(Dispatchers.IO) {
        val entity = dao.getDownloadById(downloadId)
            ?: return@withContext FetchResult(null, null, "import", "Download not found")
        val lang = normalizeSubtitleLanguage(language)
        val saved = saveSidecarBytes(
            downloadId = downloadId,
            language = lang,
            format = fileName.substringAfterLast('.', "srt").lowercase(),
            bytes = bytes,
            releaseName = "Imported file"
        ) ?: return@withContext FetchResult(null, null, "import", "Could not read that subtitle file")
        persistSidecar(entity.id, lang, saved, releaseName = "Imported file", hasEmbedded = entity.hasEmbeddedSubtitles)
        FetchResult(saved, null, "import", error = null)
    }

    // ------------------------------------------------------------------
    // L1/L2/L3 fetchers (network)
    // ------------------------------------------------------------------

    private suspend fun fetchWyzie(
        baseUrl: String,
        imdbId: String?,
        tmdbId: String,
        season: Int?,
        episode: Int?,
        language: String,
        apiKey: String?
    ): List<SubtitleCandidate> = withContext(Dispatchers.IO) {
        val url = SubtitleSources.buildWyzieSearchUrl(
            baseUrl = baseUrl,
            imdbId = imdbId,
            tmdbId = tmdbId,
            season = season,
            episode = episode,
            language = language,
            apiKey = apiKey
        ) ?: return@withContext emptyList()
        val body = withTimeoutOrNull(9_000L) { getBody(url) } ?: return@withContext emptyList()
        SubtitleSources.parseWyzieResponse(body)
    }

    private suspend fun fetchSubdl(
        apiKey: String,
        imdbId: String?,
        tmdbId: String,
        title: String,
        season: Int?,
        episode: Int?,
        language: String,
        isMovie: Boolean
    ): List<SubtitleCandidate> = withContext(Dispatchers.IO) {
        val url = SubtitleSources.buildSubdlSearchUrl(
            apiKey = apiKey,
            imdbId = imdbId,
            tmdbId = tmdbId,
            title = title,
            season = season,
            episode = episode,
            language = language,
            isMovie = isMovie
        ) ?: return@withContext emptyList()
        val body = withTimeoutOrNull(9_000L) { getBody(url) } ?: return@withContext emptyList()
        SubtitleSources.parseSubdlResponse(body)
    }

    private suspend fun fetchYify(
        imdbId: String,
        language: String
    ): List<SubtitleCandidate> = withContext(Dispatchers.IO) {
        val clean = imdbId.trim().takeIf { it.isNotBlank() } ?: return@withContext emptyList()
        val imdb = if (clean.startsWith("tt", ignoreCase = true)) clean else return@withContext emptyList()
        // yts-subs.com first (lighter pages), yifysubtitles.ch as mirror.
        val pageBodies = listOf(
            "https://yts-subs.com/movie-imdb/$imdb",
            "https://yifysubtitles.ch/movie-imdb/$imdb"
        ).mapNotNull { url -> withTimeoutOrNull(9_000L) { getBody(url) } }
        val langPrefix = language.lowercase().take(2)
        val rows = pageBodies.flatMap { SubtitleSources.parseYifyMoviePage(it) }
            .filter { row ->
                val l = row.language.lowercase()
                l.startsWith(langPrefix) || (langPrefix == "en" && l.startsWith("english"))
            }
            .take(6)
        if (rows.isEmpty()) return@withContext emptyList()
        // Follow each row's detail page to its .zip (bounded: 3 pages max).
        val candidates = mutableListOf<SubtitleCandidate>()
        for (row in rows.take(3)) {
            val base = if (row.detailPath.startsWith("http")) row.detailPath
            else "https://yts-subs.com${row.detailPath}"
            val detail = withTimeoutOrNull(9_000L) { getBody(base) }
                ?: withTimeoutOrNull(9_000L) {
                    getBody("https://yifysubtitles.ch${row.detailPath}")
                }
                ?: continue
            val zip = SubtitleSources.parseYifyDetailPage(detail) ?: continue
            candidates.add(
                SubtitleCandidate(
                    downloadUrl = zip,
                    language = langPrefix,
                    format = "srt",
                    releaseName = row.release,
                    source = "yify"
                )
            )
        }
        candidates
    }

    // ------------------------------------------------------------------
    // Download + persist
    // ------------------------------------------------------------------

    private suspend fun downloadCandidate(
        downloadId: String,
        language: String,
        candidate: SubtitleCandidate
    ): FetchResult = withContext(Dispatchers.IO) {
        val entity = dao.getDownloadById(downloadId)
            ?: return@withContext FetchResult(null, null, candidate.source, "Download not found")
        val bytes = withTimeoutOrNull(20_000L) { getBytes(candidate.downloadUrl) }
            ?: return@withContext FetchResult(null, candidate, candidate.source, "Subtitle download failed, check connection and retry")
        val payload = extractSubtitlePayload(bytes, candidate)
        if (payload == null) {
            return@withContext FetchResult(null, candidate, candidate.source, "Subtitle file was empty or unreadable")
        }
        val saved = saveSidecarBytes(
            downloadId = downloadId,
            language = language,
            format = payload.format,
            bytes = payload.bytes,
            releaseName = candidate.releaseName
        ) ?: return@withContext FetchResult(null, candidate, candidate.source, "Could not save subtitle file")
        persistSidecar(downloadId, language, saved, candidate.releaseName, entity.hasEmbeddedSubtitles)
        FetchResult(saved, candidate, candidate.source, error = null)
    }

    private data class Payload(val bytes: ByteArray, val format: String)

    /** Unzips archives (SubDL/YIFY return .zip); passes raw .srt/.vtt through. */
    private fun extractSubtitlePayload(bytes: ByteArray, candidate: SubtitleCandidate): Payload? {
        if (bytes.isEmpty()) return null
        val isZip = bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()
        if (!isZip) {
            val text = SrtSync.decodeBytes(bytes)
            if (text.isBlank() || SrtSync.parse(text).isEmpty()) return null
            return Payload(bytes, candidate.format.ifBlank { "srt" })
        }
        return try {
            ZipInputStream(bytes.inputStream()).use { zip ->
                var entry = zip.nextEntry
                var best: Payload? = null
                while (entry != null) {
                    val name = entry.name.lowercase()
                    val ext = name.substringAfterLast('.', "")
                    if (!entry.isDirectory && ext in SUBTITLE_EXTENSIONS) {
                        val content = zip.readBytes()
                        val text = SrtSync.decodeBytes(content)
                        if (text.isNotBlank() && SrtSync.parse(text).isNotEmpty()) {
                            // Prefer the largest parseable subtitle in the archive.
                            if (best == null || content.size > best.bytes.size) {
                                best = Payload(content, ext)
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
                best
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveSidecarBytes(
        downloadId: String,
        language: String,
        format: String,
        bytes: ByteArray,
        releaseName: String?
    ): File? {
        return try {
            val safeId = downloadId.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(80)
            val ext = format.lowercase().takeIf { it in SUBTITLE_EXTENSIONS } ?: "srt"
            val file = File(subsDir(), "$safeId.$language.$ext")
            file.writeBytes(bytes)
            if (!file.exists() || file.length() <= 0L) return null
            file
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun persistSidecar(
        downloadId: String,
        language: String,
        file: File,
        releaseName: String?,
        hasEmbedded: Boolean
    ) {
        val info = SubtitleFileInfo(
            path = file.absolutePath,
            language = language,
            label = "${language.uppercase()} · ${file.extension.uppercase()}",
            format = file.extension.lowercase(),
            releaseName = releaseName,
            hearingImpaired = false
        )
        dao.updateSubtitleLanguage(downloadId, language)
        dao.updateSubtitleFiles(
            id = downloadId,
            path = file.absolutePath,
            filesJson = encodeSubtitleManifest(listOf(info)),
            releaseName = releaseName,
            hasEmbedded = hasEmbedded
        )
    }

    // ------------------------------------------------------------------
    // HTTP helpers (no key material is ever logged)
    // ------------------------------------------------------------------

    private fun getBody(url: String): String? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "CluTube/1.2 (offline-subtitles)")
                .header("Accept", "application/json, text/html")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()?.takeIf { it.isNotBlank() }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun getBytes(url: String): ByteArray? {
        return try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", "CluTube/1.2 (offline-subtitles)")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.bytes()?.takeIf { it.isNotEmpty() }
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val TAG = "SubtitleManager"

        /**
         * Reads an optional secrets-plugin BuildConfig field without a hard
         * compile dependency: missing/empty/"UNSET" keys yield "" and the
         * cascade simply skips that provider (keyless fallbacks still work).
         */
        fun buildConfigKey(name: String): String = runCatching {
            Class.forName("com.example.BuildConfig").getField(name).get(null) as? String
        }.getOrNull()?.trim().orEmpty()
            .takeIf { it.isNotEmpty() && !it.equals("UNSET", ignoreCase = true) }
            .orEmpty()

        @Volatile
        private var instance: SubtitleManager? = null

        fun getInstance(context: Context): SubtitleManager = instance ?: synchronized(this) {
            instance ?: SubtitleManager(context.applicationContext).also { instance = it }
        }

        fun logWarning(message: String) = Log.w(TAG, message)
    }
}
