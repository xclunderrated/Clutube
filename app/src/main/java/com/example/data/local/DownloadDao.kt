package com.example.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["status"]),
        Index(value = ["tmdbId", "seasonNumber", "episodeNumber"])
    ]
)
data class DownloadEntity(
    @PrimaryKey val id: String,
    val tmdbId: String,
    val mediaType: String,
    val title: String,
    val seriesTitle: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val thumbnailUrl: String? = null,
    val downloadUrl: String,
    val localFilePath: String,
    val status: String = DownloadStatus.QUEUED.name,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
    val progressPercent: Int = 0,
    val downloadSpeedBytesPerSec: Long = 0L,
    val quality: String = "1080p Full HD",
    val serverName: String = "VidSrc (vidsrc2.ru)",
    val subtitleCc: String = "English (CC)",
    val duration: String? = null,
    val isTorrent: Boolean = false,
    val infoHash: String? = null,
    val magnetUri: String? = null,
    val torrentFileUrl: String? = null,
    val seeders: Int = 0,
    val leechers: Int = 0,
    val etaSeconds: Long = 0L,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val completedAtMillis: Long? = null,
    val errorMessage: String? = null,
    // --- Offline subtitle (CC) sidecar support (DB v6) ---
    // Requested subtitle language as ISO 639-1 ("en", "es", ...) or "off".
    val subtitleLanguage: String? = "en",
    // Absolute path of the primary downloaded sidecar (.srt/.vtt), if any.
    val subtitleFilePath: String? = null,
    // JSON manifest of all known sidecar files for this download.
    // Kept as raw JSON (not a Room type-converter) so V1 stays dependency-free.
    val subtitleFilesJson: String? = null,
    // Id of the user-selected subtitle track (sidecar path or embedded label).
    val selectedSubtitleTrackId: String? = null,
    // Manual A/V sync correction in milliseconds. Positive = show cues later.
    // Applied by shifting sidecar cue timestamps (ExoPlayer has no delay API).
    val subtitleOffsetMs: Long = 0L,
    // Release name of the chosen subtitle (e.g. "BluRay x264 YTS") for diagnostics.
    val subtitleReleaseName: String? = null,
    // True when the video container itself carries subtitle tracks (mkv/mp4).
    val hasEmbeddedSubtitles: Boolean = false
)

@Dao
interface DownloadDao {
    @Query("UPDATE downloads SET status = 'QUEUED' WHERE status = 'DOWNLOADING'")
    suspend fun resetInterruptedDownloads()

    @Query("SELECT * FROM downloads ORDER BY createdAtMillis DESC")
    fun getAllDownloadsFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'DOWNLOADING', 'PAUSED') ORDER BY createdAtMillis ASC")
    fun getQueueFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' ORDER BY completedAtMillis DESC, createdAtMillis DESC")
    fun getCompletedFlow(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY createdAtMillis DESC")
    suspend fun getAllDownloads(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun getDownloadById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status = 'QUEUED' ORDER BY createdAtMillis ASC LIMIT 1")
    suspend fun getNextQueuedDownload(): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status = 'DOWNLOADING' LIMIT 1")
    suspend fun getActiveDownload(): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE tmdbId = :tmdbId AND (seasonNumber = :season OR (:season IS NULL AND seasonNumber IS NULL)) AND (episodeNumber = :episode OR (:episode IS NULL AND episodeNumber IS NULL)) LIMIT 1")
    suspend fun findDownload(tmdbId: String, season: Int?, episode: Int?): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE tmdbId = :tmdbId")
    suspend fun getDownloadsForTmdbId(tmdbId: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE tmdbId = :tmdbId AND seasonNumber = :season")
    suspend fun getDownloadsForSeason(tmdbId: String, season: Int): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(download: DownloadEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(downloads: List<DownloadEntity>)

    @Query("UPDATE downloads SET status = :status, bytesDownloaded = :bytesDownloaded, totalBytes = :totalBytes, progressPercent = :progress, downloadSpeedBytesPerSec = :speed WHERE id = :id")
    suspend fun updateProgress(id: String, status: String, bytesDownloaded: Long, totalBytes: Long, progress: Int, speed: Long)

    @Query("UPDATE downloads SET status = :status, bytesDownloaded = :bytesDownloaded, totalBytes = :totalBytes, progressPercent = :progress, downloadSpeedBytesPerSec = :speed, etaSeconds = :eta WHERE id = :id")
    suspend fun updateProgressWithEta(id: String, status: String, bytesDownloaded: Long, totalBytes: Long, progress: Int, speed: Long, eta: Long)

    @Query("UPDATE downloads SET seeders = :seeders, leechers = :leechers WHERE id = :id")
    suspend fun updateSwarmHealth(id: String, seeders: Int, leechers: Int)

    @Query("SELECT * FROM downloads WHERE infoHash = :infoHash LIMIT 1")
    suspend fun getDownloadByInfoHash(infoHash: String): DownloadEntity?

    @Query("UPDATE downloads SET status = 'COMPLETED', bytesDownloaded = totalBytes, progressPercent = 100, completedAtMillis = :completedAt, downloadSpeedBytesPerSec = 0, errorMessage = null WHERE id = :id")
    suspend fun markCompleted(id: String, completedAt: Long)

    @Query("UPDATE downloads SET status = 'FAILED', errorMessage = :error, downloadSpeedBytesPerSec = 0 WHERE id = :id")
    suspend fun markFailed(id: String, error: String)

    @Query("UPDATE downloads SET status = 'PAUSED', downloadSpeedBytesPerSec = 0 WHERE id = :id")
    suspend fun markPaused(id: String)

    @Query("UPDATE downloads SET status = 'QUEUED', errorMessage = null WHERE id = :id")
    suspend fun markQueued(id: String)

    @Query("UPDATE downloads SET status = 'PAUSED', downloadSpeedBytesPerSec = 0 WHERE status IN ('DOWNLOADING', 'QUEUED')")
    suspend fun pauseAll()

    @Query("UPDATE downloads SET status = 'QUEUED', errorMessage = null WHERE status IN ('PAUSED', 'FAILED')")
    suspend fun resumeAll()

    @Query("UPDATE downloads SET status = 'QUEUED', errorMessage = null WHERE status = 'FAILED'")
    suspend fun retryAllFailed()

    @Query("DELETE FROM downloads WHERE status = 'FAILED'")
    suspend fun clearFailed()

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM downloads WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()

    @Query("DELETE FROM downloads")
    suspend fun clearAll()

    // --- Offline subtitle (CC) sidecar support (DB v6) ---

    @Query("UPDATE downloads SET subtitleLanguage = :language WHERE id = :id")
    suspend fun updateSubtitleLanguage(id: String, language: String?)

    @Query("UPDATE downloads SET subtitleFilePath = :path, subtitleFilesJson = :filesJson, subtitleReleaseName = :releaseName, hasEmbeddedSubtitles = :hasEmbedded WHERE id = :id")
    suspend fun updateSubtitleFiles(
        id: String,
        path: String?,
        filesJson: String?,
        releaseName: String?,
        hasEmbedded: Boolean
    )

    @Query("UPDATE downloads SET subtitleOffsetMs = :offsetMs WHERE id = :id")
    suspend fun updateSubtitleOffset(id: String, offsetMs: Long)

    @Query("UPDATE downloads SET selectedSubtitleTrackId = :trackId WHERE id = :id")
    suspend fun updateSelectedSubtitleTrack(id: String, trackId: String?)

    @Query("UPDATE downloads SET hasEmbeddedSubtitles = :hasEmbedded WHERE id = :id")
    suspend fun updateEmbeddedSubtitleFlag(id: String, hasEmbedded: Boolean)

    @Query("SELECT * FROM downloads WHERE status = 'COMPLETED' AND (subtitleFilePath IS NULL OR subtitleFilePath = '') AND (subtitleLanguage IS NOT NULL AND subtitleLanguage != 'off')")
    suspend fun getCompletedWithoutSubtitles(): List<DownloadEntity>
}
