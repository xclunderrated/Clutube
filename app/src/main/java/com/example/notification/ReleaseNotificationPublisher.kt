package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.example.MainActivity
import com.example.R
import com.example.data.SettingsManager
import com.example.model.AppNotification
import com.example.model.NotificationKind
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Netflix-style system notifications: the status-bar small icon is a
 * monochrome glyph (Android renders small icons as a white template, so a
 * full-color poster or the app logo can never go there), while the actual
 * movie/show artwork arrives via [NotificationCompat.Builder.setLargeIcon]
 * (poster/logo tile) and [NotificationCompat.BigPictureStyle] (16:9 hero).
 *
 * Image loading is best-effort with a short timeout: any failure falls back
 * to a locally drawn letter tile (never the app logo), and then to
 * text-only, so a slow CDN can never suppress the alert itself.
 */
object ReleaseNotificationPublisher {
    /** Legacy channel, kept so already-created channels are not orphaned. */
    const val CHANNEL_ID = "clutube_releases"

    /** Heads-up channel for release alerts and new watched-show episodes. */
    const val CHANNEL_RELEASES_HIGH = "clutube_releases_high"

    /** Quieter channel for subscription uploads. */
    const val CHANNEL_SUBSCRIPTIONS = "clutube_subscriptions"

    const val EXTRA_NOTIFICATION_ID = "notification_id"
    const val EXTRA_MARK_READ = "mark_read"

    private const val IMAGE_TIMEOUT_MS = 4_000L
    private const val HERO_WIDTH = 1024
    private const val HERO_HEIGHT = 576
    private const val LARGE_ICON_SIZE = 256

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Releases and subscriptions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "New releases, upcoming episodes, and subscribed content"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_RELEASES_HIGH,
                "New releases",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Release alerts and new episodes of shows you watch"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_SUBSCRIPTIONS,
                "Subscriptions",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "New uploads from studios you follow"
            }
        )
    }

    private fun channelFor(kind: NotificationKind): String = when (kind) {
        NotificationKind.RELEASE_ALERT,
        NotificationKind.WATCHED_SHOW_EPISODE -> CHANNEL_RELEASES_HIGH
        NotificationKind.SUBSCRIPTION_RELEASE -> CHANNEL_SUBSCRIPTIONS
    }

    private fun priorityFor(kind: NotificationKind): Int = when (kind) {
        NotificationKind.RELEASE_ALERT,
        NotificationKind.WATCHED_SHOW_EPISODE -> NotificationCompat.PRIORITY_HIGH
        NotificationKind.SUBSCRIPTION_RELEASE -> NotificationCompat.PRIORITY_DEFAULT
    }

    suspend fun publish(context: Context, notification: AppNotification) {
        if (!SettingsManager(context).releaseNotificationsEnabled) return
        ensureChannel(context)
        val video = notification.targetVideo
        val targetUri = Uri.Builder()
            .scheme("clutube")
            .authority("watch")
            .appendPath(video.id)
            .appendQueryParameter("title", video.title)
            .appendQueryParameter("tmdbId", video.tmdbId.orEmpty())
            .appendQueryParameter("mediaType", video.mediaType.name)
            .apply {
                notification.season?.let { appendQueryParameter("season", it.toString()) }
                notification.episode?.let { appendQueryParameter("episode", it.toString()) }
                appendQueryParameter(EXTRA_NOTIFICATION_ID, notification.id)
            }
            .build()
        val intent = Intent(Intent.ACTION_VIEW, targetUri, context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val requestCode = notification.id.hashCode() and Int.MAX_VALUE
        val contentIntent = PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val watchNowIntent = PendingIntent.getActivity(
            context,
            requestCode + 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissIntent = PendingIntent.getBroadcast(
            context,
            requestCode + 2,
            Intent(context, NotificationDismissReceiver::class.java).apply {
                putExtra(EXTRA_NOTIFICATION_ID, notification.id)
                putExtra(EXTRA_MARK_READ, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Show/movie artwork first: poster tile, then the TMDB title logo,
        // then backdrop/thumbnail, then the studio avatar. The app logo is
        // never used — when every URL fails, a letter tile is drawn below.
        val heroUrl = video.backdropUrl?.takeIf { it.isNotBlank() }
            ?: video.thumbnailUrl.takeIf { it.isNotBlank() }
        val tileUrl = video.posterUrl?.takeIf { it.isNotBlank() }
            ?: video.logoUrl?.takeIf { it.isNotBlank() }
            ?: video.thumbnailUrl.takeIf { it.isNotBlank() }
            ?: video.channelAvatarUrl.takeIf { it.isNotBlank() }
        val heroBitmap = heroUrl?.let { loadBitmap(context, it, HERO_WIDTH, HERO_HEIGHT) }
        val tileBitmap = if (tileUrl != null && tileUrl != heroUrl) {
            loadBitmap(context, tileUrl, LARGE_ICON_SIZE, LARGE_ICON_SIZE)
        } else {
            null
        } ?: letterTileBitmap(video.title)

        // Rich text: IMDb rating + meta (year · runtime or S/E) plus the
        // synopsis, so the alert says what the title is about. All
        // blank-safe — unknown pieces are omitted, never faked.
        val metaLine = buildList {
            video.rating?.takeIf { it > 0 }?.let { add("★ %.1f".format(java.util.Locale.US, it)) }
            val year = video.releaseDateFormatted?.take(4)?.takeIf { it.all(Char::isDigit) }
                ?: video.releaseDateIso?.take(4)?.takeIf { it.all(Char::isDigit) }
            if (!year.isNullOrBlank()) add(year)
            if (video.mediaType == com.example.model.MediaType.TV_SHOW) {
                val s = notification.season ?: video.currentSeason
                val e = notification.episode ?: video.currentEpisode
                if (s > 0 && e > 0) add("S$s E$e")
            } else {
                video.runtimeMinutes?.takeIf { it > 0 }?.let { mins ->
                    val hrs = mins / 60
                    add(if (hrs > 0) "${hrs}h ${mins % 60}m" else "${mins}m")
                }
            }
        }.joinToString(" · ")
        val synopsis = video.description.trim().takeIf { it.isNotBlank() }
        val expandedText = listOfNotNull(
            metaLine.takeIf { it.isNotBlank() },
            notification.message,
            synopsis
        ).joinToString("\n\n")

        val builder = NotificationCompat.Builder(context, channelFor(notification.kind))
            .setSmallIcon(R.drawable.ic_stat_now_playing)
            .setContentTitle(notification.title)
            .setContentText(if (metaLine.isNotBlank()) "$metaLine · ${notification.message}" else notification.message)
            .setSubText(video.channelName.takeIf { it.isNotBlank() })
            .setContentIntent(contentIntent)
            .setDeleteIntent(dismissIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(priorityFor(notification.kind))
            .setLargeIcon(tileBitmap)
            .addAction(
                NotificationCompat.Action.Builder(0, "Watch Now", watchNowIntent).build()
            )
        if (heroBitmap != null) {
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(heroBitmap)
                    .bigLargeIcon(null as Bitmap?)
                    .setSummaryText(metaLine.takeIf { it.isNotBlank() } ?: notification.message)
            )
        } else {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
        }
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(requestCode, builder.build())
        }
    }

    /**
     * Last-resort large icon: title initial on a dark tile, drawn locally.
     * Guarantees the notification shows title art even with no connectivity
     * instead of falling back to the app icon.
     */
    private fun letterTileBitmap(title: String): Bitmap {
        val size = LARGE_ICON_SIZE
        return runCatching {
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(0xFF111827.toInt())
            val initial = title.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "•"
            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = size * 0.44f
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(initial, size / 2f, y, paint)
            bitmap
        }.getOrElse {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
    }

    private suspend fun loadBitmap(context: Context, url: String, width: Int, height: Int): Bitmap? {        return withTimeoutOrNull(IMAGE_TIMEOUT_MS) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(width, height)
                    .scale(Scale.FILL)
                    .allowHardware(false)
                    .build()
                val result = Coil.imageLoader(context).execute(request)
                ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            }.getOrNull()
        }
    }
}
