package com.example.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import com.example.model.ReleaseAlert

object ReleaseNotificationScheduler {
    private const val PERIODIC_WORK_NAME = "clutube_release_refresh"
    private const val ALERT_WORK_PREFIX = "clutube_release_alert_"
    private const val ALERT_ID_KEY = "release_alert_id"

    fun schedule(context: Context) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<ReleaseNotificationWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    fun scheduleAlert(context: Context, alert: ReleaseAlert) {
        runCatching {
            val delay = (alert.releaseAtMillis - System.currentTimeMillis()).coerceAtLeast(0L)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val input = Data.Builder().putString(ALERT_ID_KEY, alert.id).build()
            val request = OneTimeWorkRequestBuilder<ReleaseNotificationWorker>()
                .setInputData(input)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ALERT_WORK_PREFIX + alert.id,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
        schedule(context)
    }

    /**
     * Flushes already-due bells on cold start. A one-shot timer that never
     * fired (force-stop, cleared queue, missed window) would otherwise wait
     * for the next 6h sweep; this delivers it within moments of opening the
     * app. KEEP policy: rapid restarts don't pile up duplicate flushes.
     */
    fun flushDueNow(context: Context) {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = OneTimeWorkRequestBuilder<ReleaseNotificationWorker>()
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "clutube_release_flush_now",
                ExistingWorkPolicy.KEEP,
                request
            )
        }
        schedule(context)
    }

    fun cancelAlert(context: Context, alertId: String) {
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(ALERT_WORK_PREFIX + alertId)
        }
    }

    internal fun alertId(input: Data): String? = input.getString(ALERT_ID_KEY)
}
