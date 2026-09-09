package com.example.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat

/**
 * Foreground service that keeps downloads running in the background and
 * shows live progress in the phone's notification bar.
 *
 * - While active: one ongoing notification with the current file's title,
 *   percent, speed, ETA and a progress bar (refreshed ~every 2s).
 * - On finish/failure: a separate auto-dismissing alert. Tapping any of
 *   these opens the Downloads screen; failed ones offer a Retry action.
 * - Sends ACTION_PAUSE_ALL / ACTION_RETRY_DOWNLOAD broadcasts for the
 *   notification buttons (handled by MainActivity).
 */
class DownloadService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannels()

        // Acquire a partial wake lock so the CPU keeps running while downloading
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "clutube:download_service_wake")
        wl.setReferenceCounted(false)
        wakeLock = wl
        if (!wl.isHeld) {
            wl.acquire(24L * 60 * 60 * 1000L) // max 24 hours
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_NOTIFY_DONE -> {
                showDoneNotification(
                    downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID).orEmpty(),
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                )
                return START_NOT_STICKY
            }
            ACTION_NOTIFY_FAILED -> {
                showFailedNotification(
                    downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID).orEmpty(),
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    error = intent.getStringExtra(EXTRA_ERROR)
                )
                return START_NOT_STICKY
            }
        }

        val snapshot = ProgressSnapshot(
            activeCount = intent?.getIntExtra(EXTRA_ACTIVE_COUNT, 0) ?: 0,
            title = intent?.getStringExtra(EXTRA_TITLE),
            progressPercent = intent?.getIntExtra(EXTRA_PROGRESS, -1) ?: -1,
            speedBytesPerSec = intent?.getLongExtra(EXTRA_TOTAL_SPEED, 0L) ?: 0L,
            etaSeconds = intent?.getLongExtra(EXTRA_ETA, 0L) ?: 0L
        )
        startForeground(NOTIFICATION_ID, buildProgressNotification(snapshot))
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Downloads",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows active download progress"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
            notificationManager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID_ALERTS,
                    "Download alerts",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Notifies when downloads finish or fail"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
    }

    /** Ongoing foreground notification with title, % and progress bar. */
    private fun buildProgressNotification(s: ProgressSnapshot): Notification {
        val title = s.title?.takeIf { it.isNotBlank() } ?: "Clutube downloading"
        val speedLabel = formatSpeed(s.speedBytesPerSec)
        val contentText = buildString {
            if (s.progressPercent in 0..100) append("${s.progressPercent}%")
            if (speedLabel.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(speedLabel)
            }
            val eta = formatEta(s.etaSeconds)
            if (eta.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append("ETA $eta")
            }
            if (isEmpty()) append("Preparing…")
            if (s.activeCount > 1) append("  (+${s.activeCount - 1} more)")
        }

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(contentText)
            .setContentIntent(openDownloadsPending(this, 0))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)

        if (s.progressPercent in 0..100) {
            builder.setProgress(100, s.progressPercent, false)
        } else {
            builder.setProgress(0, 0, true)
        }

        if (s.activeCount > 0) {
            builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause all",
                pauseAllPending()
            )
        }
        return builder.build()
    }

    private fun showDoneNotification(downloadId: String, title: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(title.takeIf { it.isNotBlank() } ?: "Your video is ready to watch offline")
            .setContentIntent(openDownloadsPending(this, downloadId.hashCode()))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager?.notify(DONE_NOTIFICATION_ID_BASE + (downloadId.hashCode() and 0xFF), notification)
    }

    private fun showFailedNotification(downloadId: String, title: String, error: String?) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed")
            .setContentText(
                listOfNotNull(
                    title.takeIf { it.isNotBlank() },
                    error?.takeIf { it.isNotBlank() }
                ).joinToString(" — ").takeIf { it.isNotBlank() } ?: "Tap to open Downloads"
            )
            .setContentIntent(openDownloadsPending(this, downloadId.hashCode()))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (downloadId.isNotBlank()) {
            builder.addAction(
                android.R.drawable.ic_popup_sync,
                "Retry",
                retryPending(downloadId)
            )
        }
        notificationManager?.notify(FAILED_NOTIFICATION_ID_BASE + (downloadId.hashCode() and 0xFF), builder.build())
    }

    private fun pauseAllPending(): PendingIntent {
        val intent = Intent(ACTION_PAUSE_ALL).setPackage(packageName)
        return PendingIntent.getBroadcast(
            this, REQUEST_PAUSE_ALL, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun retryPending(downloadId: String): PendingIntent {
        val intent = Intent(ACTION_RETRY_DOWNLOAD).setPackage(packageName).apply {
            putExtra(EXTRA_DOWNLOAD_ID, downloadId)
        }
        return PendingIntent.getBroadcast(
            this, REQUEST_RETRY_BASE + (downloadId.hashCode() and 0xFFF), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0) return ""
        val kb = bytesPerSec / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB/s", mb)
            kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB/s", kb)
            else -> "$bytesPerSec B/s"
        }
    }

    private fun formatEta(seconds: Long): String {
        if (seconds <= 0) return ""
        val mins = seconds / 60
        return when {
            mins >= 60 -> "${mins / 60}h ${mins % 60}m"
            mins > 0 -> "${mins}m ${seconds % 60}s"
            else -> "${seconds}s"
        }
    }

    /** Latest known progress, passed with every start/update call. */
    data class ProgressSnapshot(
        val activeCount: Int = 0,
        val title: String? = null,
        val progressPercent: Int = -1,
        val speedBytesPerSec: Long = 0L,
        val etaSeconds: Long = 0L
    )

    companion object {
        private const val TAG = "DownloadService"
        private const val CHANNEL_ID = "clutube_downloads"
        private const val CHANNEL_ID_ALERTS = "clutube_download_alerts"
        private const val NOTIFICATION_ID = 1001
        private const val DONE_NOTIFICATION_ID_BASE = 2000
        private const val FAILED_NOTIFICATION_ID_BASE = 2100
        private const val REQUEST_PAUSE_ALL = 1
        private const val REQUEST_RETRY_BASE = 100

        const val ACTION_PAUSE_ALL = "com.example.action.PAUSE_ALL_DOWNLOADS"
        const val ACTION_RETRY_DOWNLOAD = "com.example.action.RETRY_DOWNLOAD"
        const val EXTRA_OPEN_DOWNLOADS = "com.example.data.download.EXTRA_OPEN_DOWNLOADS"
        private const val ACTION_NOTIFY_DONE = "com.example.data.download.ACTION_NOTIFY_DONE"
        private const val ACTION_NOTIFY_FAILED = "com.example.data.download.ACTION_NOTIFY_FAILED"
        const val EXTRA_DOWNLOAD_ID = "com.example.data.download.EXTRA_DOWNLOAD_ID"
        const val EXTRA_ACTIVE_COUNT = "com.example.data.download.EXTRA_ACTIVE_COUNT"
        const val EXTRA_TOTAL_SPEED = "com.example.data.download.EXTRA_TOTAL_SPEED"
        const val EXTRA_TITLE = "com.example.data.download.EXTRA_TITLE"
        const val EXTRA_PROGRESS = "com.example.data.download.EXTRA_PROGRESS"
        const val EXTRA_ETA = "com.example.data.download.EXTRA_ETA"
        const val EXTRA_ERROR = "com.example.data.download.EXTRA_ERROR"

        /** Starts (or refreshes) the foreground progress notification. */
        fun start(context: Context, snapshot: ProgressSnapshot) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_ACTIVE_COUNT, snapshot.activeCount)
                putExtra(EXTRA_TITLE, snapshot.title)
                putExtra(EXTRA_PROGRESS, snapshot.progressPercent)
                putExtra(EXTRA_TOTAL_SPEED, snapshot.speedBytesPerSec)
                putExtra(EXTRA_ETA, snapshot.etaSeconds)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { e ->
                // Background-start restrictions (Android 12+): the download
                // itself continues in-process; only the notification is lost.
                Log.w(TAG, "Could not start foreground service: ${e.message}")
            }
        }

        /** Backwards-compatible overload used by existing callers. */
        fun start(context: Context, activeCount: Int, totalSpeedBytesPerSec: Long) {
            start(context, ProgressSnapshot(activeCount = activeCount, speedBytesPerSec = totalSpeedBytesPerSec))
        }

        fun update(context: Context, snapshot: ProgressSnapshot) = start(context, snapshot)

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, DownloadService::class.java)) }
        }

        /** One-shot "download complete" alert (auto-dismissing). */
        fun notifyCompleted(context: Context, downloadId: String, title: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_NOTIFY_DONE
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(EXTRA_TITLE, title)
            }
            // Foreground start: plain startService is dropped for background
            // callers on API 31+, silently losing the notification.
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            }
        }

        /** One-shot "download failed" alert with a Retry button. */
        fun notifyFailed(context: Context, downloadId: String, title: String, error: String?) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_NOTIFY_FAILED
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_ERROR, error)
            }
            runCatching {
                androidx.core.content.ContextCompat.startForegroundService(context, intent)
            }
        }

        /** Tap action for every download notification: opens Downloads. */
        fun openDownloadsPending(context: Context, requestCode: Int): PendingIntent {
            val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                putExtra(EXTRA_OPEN_DOWNLOADS, true)
            } ?: Intent(context, Class.forName("com.example.MainActivity")).apply {
                putExtra(EXTRA_OPEN_DOWNLOADS, true)
            }
            return PendingIntent.getActivity(
                context, requestCode, openIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        }
    }
}
