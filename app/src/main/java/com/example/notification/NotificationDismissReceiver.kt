package com.example.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.LocalStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles notification swipe-away ([deleteIntent]) and explicit dismiss
 * actions: marks the inbox item read so the badge count stays truthful.
 * Runs off-main via [goAsync] so the store write never blocks the broadcast.
 */
class NotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId =
            intent.getStringExtra(ReleaseNotificationPublisher.EXTRA_NOTIFICATION_ID)
                ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val store = LocalStore(context.applicationContext)
                if (intent.getBooleanExtra(ReleaseNotificationPublisher.EXTRA_MARK_READ, true)) {
                    store.setNotificationRead(notificationId, true)
                } else {
                    store.dismissNotification(notificationId)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
