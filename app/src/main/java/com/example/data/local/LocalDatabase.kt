package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "catalog_cache")
data class CatalogCacheEntity(
    @androidx.room.PrimaryKey val cacheKey: String,
    val payloadJson: String,
    val updatedAtMillis: Long
)

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["normalizedQuery"], unique = true)]
)
data class SearchHistoryEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val normalizedQuery: String,
    val displayQuery: String,
    val lastUsedAtMillis: Long
)

@Entity(tableName = "search_cache")
data class SearchCacheEntity(
    @androidx.room.PrimaryKey val cacheKey: String,
    val payloadJson: String,
    val updatedAtMillis: Long
)

@Entity(tableName = "release_alerts")
data class ReleaseAlertEntity(
    @androidx.room.PrimaryKey val id: String,
    val videoJson: String,
    val releaseAtMillis: Long,
    val kind: String,
    val season: Int?,
    val episode: Int?,
    val createdAtMillis: Long,
    val deliveredAtMillis: Long?
)

@Entity(tableName = "app_notifications")
data class AppNotificationEntity(
    @androidx.room.PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val message: String,
    val videoJson: String,
    val createdAtMillis: Long,
    val releaseAtMillis: Long?,
    val season: Int?,
    val episode: Int?,
    val isRead: Boolean,
    val isDismissed: Boolean
)

@Entity(tableName = "watch_later")
data class WatchLaterEntity(
    @androidx.room.PrimaryKey val id: String,
    val tmdbId: String?,
    val mediaType: String,
    val season: Int = 1,
    val episode: Int = 1,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val thumbnailUrl: String?,
    val channelName: String,
    val channelAvatarUrl: String?,
    val rating: Double?,
    val duration: String?,
    val releaseDateFormatted: String?,
    val addedAtMillis: Long,
    val orderIndex: Int
)

@Dao
interface LocalCacheDao {
    @Query("SELECT * FROM catalog_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun getCatalog(key: String): CatalogCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putCatalog(entity: CatalogCacheEntity)

    @Query("SELECT * FROM search_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun getSearch(key: String): SearchCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSearch(entity: SearchCacheEntity)

    @Query("SELECT * FROM search_history ORDER BY lastUsedAtMillis DESC LIMIT :limit")
    suspend fun getSearchHistory(limit: Int): List<SearchHistoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSearchHistory(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE normalizedQuery = :query")
    suspend fun deleteSearchHistory(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()

    @Query("SELECT * FROM release_alerts ORDER BY releaseAtMillis ASC")
    suspend fun getReleaseAlerts(): List<ReleaseAlertEntity>

    @Query("SELECT * FROM release_alerts WHERE id = :id LIMIT 1")
    suspend fun getReleaseAlert(id: String): ReleaseAlertEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putReleaseAlert(entity: ReleaseAlertEntity)

    @Query("DELETE FROM release_alerts WHERE id = :id")
    suspend fun deleteReleaseAlert(id: String)

    @Query("UPDATE release_alerts SET deliveredAtMillis = :deliveredAt WHERE id = :id")
    suspend fun markReleaseAlertDelivered(id: String, deliveredAt: Long)

    @Query("SELECT * FROM app_notifications ORDER BY createdAtMillis DESC")
    suspend fun getNotifications(): List<AppNotificationEntity>

    @Query("SELECT COUNT(*) FROM app_notifications WHERE isRead = 0 AND isDismissed = 0")
    suspend fun unreadNotificationCount(): Int

    @Query("SELECT * FROM app_notifications WHERE id = :id LIMIT 1")
    suspend fun getNotification(id: String): AppNotificationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putNotification(entity: AppNotificationEntity)

    @Query("UPDATE app_notifications SET isRead = :isRead WHERE id = :id")
    suspend fun setNotificationRead(id: String, isRead: Boolean)

    @Query("UPDATE app_notifications SET isRead = 1 WHERE isDismissed = 0")
    suspend fun markAllNotificationsRead()

    @Query("UPDATE app_notifications SET isDismissed = 1, isRead = 1 WHERE id = :id")
    suspend fun dismissNotification(id: String)

    @Query("DELETE FROM app_notifications WHERE isDismissed = 1 OR isRead = 1")
    suspend fun clearReadNotifications()

    @Query("DELETE FROM catalog_cache")
    suspend fun clearCatalog()

    @Query("DELETE FROM search_cache")
    suspend fun clearSearchCache()

    @Query("DELETE FROM search_history")
    suspend fun clearAllSearchHistory()

    @Query("DELETE FROM release_alerts")
    suspend fun clearReleaseAlerts()

    @Query("DELETE FROM app_notifications")
    suspend fun clearAllNotifications()

    @Query("SELECT * FROM watch_later ORDER BY orderIndex ASC, addedAtMillis DESC")
    suspend fun getWatchLaterItems(): List<WatchLaterEntity>

    @Query("SELECT * FROM watch_later WHERE id = :id LIMIT 1")
    suspend fun getWatchLaterItem(id: String): WatchLaterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putWatchLaterItem(item: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE id = :id")
    suspend fun deleteWatchLaterItem(id: String)

    @Query("DELETE FROM watch_later")
    suspend fun clearWatchLater()
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `watch_later` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `tmdbId` TEXT,
                `mediaType` TEXT NOT NULL,
                `season` INTEGER NOT NULL,
                `episode` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `posterUrl` TEXT,
                `backdropUrl` TEXT,
                `thumbnailUrl` TEXT,
                `channelName` TEXT NOT NULL,
                `channelAvatarUrl` TEXT,
                `rating` REAL,
                `duration` TEXT,
                `releaseDateFormatted` TEXT,
                `addedAtMillis` INTEGER NOT NULL,
                `orderIndex` INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `downloads` (
                `id` TEXT NOT NULL PRIMARY KEY,
                `tmdbId` TEXT NOT NULL,
                `mediaType` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `seriesTitle` TEXT,
                `seasonNumber` INTEGER,
                `episodeNumber` INTEGER,
                `episodeTitle` TEXT,
                `posterUrl` TEXT,
                `backdropUrl` TEXT,
                `thumbnailUrl` TEXT,
                `downloadUrl` TEXT NOT NULL,
                `localFilePath` TEXT NOT NULL,
                `status` TEXT NOT NULL,
                `bytesDownloaded` INTEGER NOT NULL,
                `totalBytes` INTEGER NOT NULL,
                `progressPercent` INTEGER NOT NULL,
                `downloadSpeedBytesPerSec` INTEGER NOT NULL,
                `quality` TEXT NOT NULL,
                `duration` TEXT,
                `createdAtMillis` INTEGER NOT NULL,
                `completedAtMillis` INTEGER,
                `errorMessage` TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_status` ON `downloads` (`status`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_tmdbId_seasonNumber_episodeNumber` ON `downloads` (`tmdbId`, `seasonNumber`, `episodeNumber`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `serverName` TEXT NOT NULL DEFAULT 'VidSrc (vidsrc2.ru)'")
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `subtitleCc` TEXT NOT NULL DEFAULT 'English (CC)'")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `isTorrent` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `infoHash` TEXT")
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `magnetUri` TEXT")
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `torrentFileUrl` TEXT")
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `seeders` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `leechers` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `downloads` ADD COLUMN `etaSeconds` INTEGER NOT NULL DEFAULT 0")
    }
}

@Database(
    entities = [
        CatalogCacheEntity::class,
        SearchHistoryEntity::class,
        SearchCacheEntity::class,
        ReleaseAlertEntity::class,
        AppNotificationEntity::class,
        WatchLaterEntity::class,
        DownloadEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class LocalDatabase : RoomDatabase() {
    abstract fun cacheDao(): LocalCacheDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var instance: LocalDatabase? = null

        fun get(context: Context): LocalDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                LocalDatabase::class.java,
                "clutube_local.db"
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
