package com.example.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.SettingsManager
import com.example.R
import com.example.model.AppNotification

object ReleaseNotificationPublisher {
    const val CHANNEL_ID = "clutube_releases"
    const val EXTRA_NOTIFICATION_ID = "notification_id"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Releases and subscriptions",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "New releases, upcoming episodes, and subscribed content"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun publish(context: Context, notification: AppNotification) {
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
        val pendingIntent = PendingIntent.getActivity(
            context,
            notification.id.hashCode() and Int.MAX_VALUE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notification.title)
            .setContentText(notification.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notification.message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context)
                .notify(notification.id.hashCode() and Int.MAX_VALUE, builder.build())
        }
    }
}
