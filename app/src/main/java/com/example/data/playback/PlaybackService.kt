package com.example.data.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.session.MediaSession
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import android.support.v4.media.session.MediaSessionCompat
import coil.Coil
import coil.request.ImageRequest
import coil.size.Scale
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Foreground service that keeps online (WebView) and offline playback alive
 * when the screen locks or the app goes to the background, and surfaces
 * transport controls in the notification shade and on the lock screen.
 *
 * - Started/updated with [ACTION_SYNC] while a video is loaded; stopped with
 *   [ACTION_STOP] when the player closes.
 * - Playing → foreground, ongoing notification with Pause (+ Next when
 *   [EXTRA_HAS_NEXT]); paused → demoted to a regular (swipeable) notification
 *   with Resume so the service does not hold foreground forever.
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
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var lastArtworkUrl: String? = null
    private var lastArtworkBitmap: Bitmap? = null
    private var lastNotifiedKey: String? = null
    // Monotonic generation for artwork loads: a newer SYNC invalidates any
    // in-flight Coil fetch so a slow bitmap can never repost stale
    // title/artist/play-state with new art (or vice versa).
    private var artworkSeq: Long = 0L

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
            lastArtworkBitmap = null
            lastArtworkUrl = null
            lastNotifiedKey = null
            artworkSeq++ // invalidate any in-flight Coil fetch so it can't repost
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        val title = intent.getStringExtra(EXTRA_TITLE)?.trim().orEmpty().ifBlank { "CluTube" }
        val artist = intent.getStringExtra(EXTRA_ARTIST)?.trim().orEmpty()
        val isPlaying = intent.getBooleanExtra(EXTRA_IS_PLAYING, true)
        val hasNext = intent.getBooleanExtra(EXTRA_HAS_NEXT, intent.getBooleanExtra(EXTRA_IS_TV_SHOW, false))
        val artworkUrl = intent.getStringExtra(EXTRA_ARTWORK_URL)?.trim().orEmpty().ifBlank { null }
        val backgroundEnabled = intent.getBooleanExtra(EXTRA_BACKGROUND_ENABLED, true)
        if (isPlaying && backgroundEnabled) acquireWakeLock() else releaseWakeLock()
        val token: MediaSession.Token? = run {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(EXTRA_SESSION_TOKEN, MediaSession.Token::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_SESSION_TOKEN)
            }
        }
        // Dedupe identical reposts (e.g. repeated SYNCs): Binder + shade
        // flicker for free otherwise. Wake lock above still re-asserts.
        val notifiedKey = title + "|" + artist + "|" + isPlaying + "|" + hasNext + "|" + artworkUrl
        if (notifiedKey == lastNotifiedKey && artworkUrl == lastArtworkUrl) {
            return START_STICKY
        }
        lastNotifiedKey = notifiedKey
        val syncSeq = ++artworkSeq
        // Never paint the previous episode's bitmap under a new URL: post
        // text-only now, async load reposts once the right art resolves.
        val initialArt = if (artworkUrl == lastArtworkUrl) lastArtworkBitmap else null
        val notification = buildNotification(title, artist, isPlaying, hasNext, token, initialArt)
        if (isPlaying) {
            // Foreground while actually playing (required for screen-off audio).
            // Type param only exists on Q+; ServiceCompat handles older APIs.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
            }
        } else {
            // Paused: demote to a regular notification so it is swipeable and
            // the service stops holding foreground (battery). Delete intent
            // still stops the service when swiped away.
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
            } catch (_: Exception) {
            }
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
        // Sticky so the system restarts us if killed mid-playback; STOP path
        // above stays NOT_STICKY so an explicit close never resurrects us.
        // Artwork arrives async: repost once Coil resolves (same content, now
        // with the show/movie still as large icon). Stale guard via syncSeq.
        maybeLoadArtworkAsync(syncSeq, artworkUrl, title, artist, isPlaying, hasNext, token)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ioScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun maybeLoadArtworkAsync(
        syncSeq: Long,
        artworkUrl: String?,
        title: String,
        artist: String,
        isPlaying: Boolean,
        hasNext: Boolean,
        token: MediaSession.Token?
    ) {
        if (artworkUrl.isNullOrBlank()) {
            if (lastArtworkBitmap != null && lastArtworkUrl != null) {
                lastArtworkBitmap = null
                lastArtworkUrl = null
            }
            return
        }
        if (artworkUrl == lastArtworkUrl && lastArtworkBitmap != null) return
        lastArtworkUrl = artworkUrl
        ioScope.launch {
            val bitmap = loadBitmap(applicationContext, artworkUrl)
            if (bitmap == null) return@launch
            // A newer SYNC arrived while we fetched: drop this bitmap, the
            // newer generation's own fetch (or text-only post) wins.
            if (syncSeq != artworkSeq) return@launch
            lastArtworkBitmap = bitmap
            val repost = buildNotification(title, artist, isPlaying, hasNext, token, bitmap)
            try {
                if (isPlaying) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ServiceCompat.startForeground(
                            this@PlaybackService,
                            NOTIFICATION_ID,
                            repost,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        )
                    } else {
                        ServiceCompat.startForeground(this@PlaybackService, NOTIFICATION_ID, repost, 0)
                    }
                } else {
                    notificationManager?.notify(NOTIFICATION_ID, repost)
                }
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? {
        return withTimeoutOrNull(8_000L) {
            runCatching {
                // Shared singleton (disk/memory cache) like
                // ReleaseNotificationPublisher — never new ImageLoader() here.
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(512, 512)
                    .scale(Scale.FILL)
                    .allowHardware(false)
                    .build()
                val result = Coil.imageLoader(context).execute(request)
                ((result as? coil.request.SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            }.getOrNull()
        }
    }

    /**
     * Service is the single owner of the screen-off CPU hold while playing.
     * PlayerViewManager keeps a best-effort duplicate for the foreground
     * WebView; either lock alone keeps audio alive. Bounded to 10 min and
     * re-asserted on every SYNC while playing so a dead service can never
     * pin the CPU forever.
     */
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
            if (!lock.isHeld) lock.acquire(10L * 60L * 1000L)
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
        try {
            notificationManager?.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
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
        hasNext: Boolean,
        token: MediaSession.Token?,
        largeIcon: Bitmap?
    ): Notification {
        val style = MediaStyle()
        // The app owns a framework MediaSession; wrap its token so the compat
        // style can link the notification (lock-screen controls included).
        token?.let { style.setMediaSession(MediaSessionCompat.Token.fromToken(it)) }
        val compactIndices = mutableListOf(0).apply { if (hasNext) add(1) }
        style.setShowActionsInCompactView(*compactIndices.toIntArray())

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_now_playing)
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
        largeIcon?.let(builder::setLargeIcon)
        if (hasNext) {
            builder.addAction(
                android.R.drawable.ic_media_next,
                "Next",
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
        const val EXTRA_HAS_NEXT = "extra_playback_has_next"
        const val EXTRA_ARTWORK_URL = "extra_playback_artwork_url"
        const val EXTRA_DURATION_MS = "extra_playback_duration_ms"
        const val EXTRA_POSITION_MS = "extra_playback_position_ms"
        const val EXTRA_SESSION_TOKEN = "extra_playback_session_token"
        const val EXTRA_BACKGROUND_ENABLED = "extra_playback_background_enabled"

        const val CHANNEL_ID = "clutube_now_playing"
        const val NOTIFICATION_ID = 1002
        private const val REQUEST_OPEN_APP = 21
        private const val REQUEST_TOGGLE = 22
        private const val REQUEST_NEXT = 23
        private const val REQUEST_STOP = 24
    }
}
