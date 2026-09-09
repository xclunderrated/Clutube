package com.example.data.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.session.MediaSession
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import com.example.MainActivity

/**
 * Foreground service that keeps online (WebView) and offline playback alive
 * when the screen locks or the app goes to the background, and surfaces
 * transport controls in the notification shade and on the lock screen.
 *
 * - Started/updated with [ACTION_SYNC] while a video is loaded; stopped with
 *   [ACTION_STOP] when the player closes.
 * - Playing → ongoing notification with Pause (+ Next for series);
 *   paused → same notification with Resume, swipeable to dismiss.
 * - Swiping the paused notification away fires [ACTION_STOP] via the delete
 *   intent so the service does not linger.
 * - Play/pause/next taps are plain broadcasts handled by MainActivity, so the
 *   ViewModel stays the single source of truth for playback state.
 * - Holds a PARTIAL_WAKE_LOCK while playing so screen-off audio keeps
 *   decoding. Must be started eagerly while the app is foreground (on play),
 *   then only updated from background to satisfy Android 12+ FGS rules.
 */
class PlaybackService : Service() {

    private var notificationManager: NotificationManager? = null
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null || intent.action == null) {
            // START_STICKY restart with no player state: don't post a ghost
            // notification. The next play will start us again eagerly.
            releaseWakeLock()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent.action == ACTION_STOP) {
            releaseWakeLock()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent.getStringExtra(EXTRA_TITLE)?.trim().orEmpty().ifBlank { "CluTube" }
        val artist = intent.getStringExtra(EXTRA_ARTIST)?.trim().orEmpty()
        val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
        val isTvShow = intent.getBooleanExtra(EXTRA_IS_TV_SHOW, false)
        val backgroundEnabled = intent.getBooleanExtra(EXTRA_BACKGROUND_ENABLED, true)
        if (isPlaying && backgroundEnabled) acquireWakeLock() else releaseWakeLock()
        @Suppress("DEPRECATION")
        val token: MediaSession.Token? = intent?.getParcelableExtra(EXTRA_SESSION_TOKEN)
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(title, artist, isPlaying, isTvShow, token),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
        // Sticky so the system restarts us if killed mid-playback; STOP path
        // above stays NOT_STICKY so an explicit close never resurrects us.
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        try {
            var lock = wakeLock
            if (lock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
                    ?: return
                lock = pm.newWakeLock(
                    android.os.PowerManager.PARTIAL_WAKE_LOCK,
                    "CluTube:PlaybackService"
                ).apply { setReferenceCounted(false) }
                wakeLock = lock
            }
            if (!lock.isHeld) lock.acquire()
        } catch (_: Exception) {
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let { if (it.isHeld) it.release() }
        } catch (_: Exception) {
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Now playing",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Playback controls for what is currently playing"
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
        }
    }

    private fun buildNotification(
        title: String,
        artist: String,
        isPlaying: Boolean,
        isTvShow: Boolean,
        token: MediaSession.Token?
    ): Notification {
        val style = MediaStyle()
        // The app owns a framework MediaSession; wrap its token so the compat
        // style can link the notification (lock-screen controls included).
        token?.let { style.setMediaSession(MediaSessionCompat.Token.fromToken(it)) }
        val compactIndices = mutableListOf(0).apply { if (isTvShow) add(1) }
        style.setShowActionsInCompactView(*compactIndices.toIntArray())

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(
                when {
                    artist.isNotBlank() && isPlaying -> "$artist · Playing"
                    artist.isNotBlank() -> "$artist · Paused"
                    isPlaying -> "Playing"
                    else -> "Paused"
                }
            )
            .setContentIntent(openAppPending())
            .setDeleteIntent(stopSelfPending())
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setStyle(style)
            .addAction(
                if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                if (isPlaying) "Pause" else "Play",
                togglePending()
            )
        if (isTvShow) {
            builder.addAction(
                android.R.drawable.ic_media_next,
                "Next episode",
                nextPending()
            )
        }
        return builder.build()
    }

    private fun openAppPending(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addCategory(Intent.CATEGORY_LAUNCHER)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(
            this, REQUEST_OPEN_APP, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun togglePending(): PendingIntent {
        val intent = Intent(ACTION_TOGGLE).setPackage(packageName)
        return PendingIntent.getBroadcast(
            this, REQUEST_TOGGLE, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun nextPending(): PendingIntent {
        val intent = Intent(ACTION_NEXT).setPackage(packageName)
        return PendingIntent.getBroadcast(
            this, REQUEST_NEXT, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun stopSelfPending(): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).setAction(ACTION_STOP)
        return PendingIntent.getService(
            this, REQUEST_STOP, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    companion object {
        const val ACTION_SYNC = "com.clutube.app.PLAYBACK_SYNC"
        const val ACTION_STOP = "com.clutube.app.PLAYBACK_STOP"
        const val ACTION_TOGGLE = "com.clutube.app.PLAYBACK_TOGGLE"
        const val ACTION_NEXT = "com.clutube.app.PLAYBACK_NEXT"

        const val EXTRA_TITLE = "extra_playback_title"
        const val EXTRA_ARTIST = "extra_playback_artist"
        const val EXTRA_IS_PLAYING = "extra_playback_is_playing"
        const val EXTRA_IS_TV_SHOW = "extra_playback_is_tv_show"
        const val EXTRA_SESSION_TOKEN = "extra_playback_session_token"
        const val EXTRA_BACKGROUND_ENABLED = "extra_playback_background_enabled"

        private const val CHANNEL_ID = "clutube_now_playing"
        private const val NOTIFICATION_ID = 1002
        private const val REQUEST_OPEN_APP = 21
        private const val REQUEST_TOGGLE = 22
        private const val REQUEST_NEXT = 23
        private const val REQUEST_STOP = 24
    }
}
