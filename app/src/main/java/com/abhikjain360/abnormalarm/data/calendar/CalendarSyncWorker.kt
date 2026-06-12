package com.abhikjain360.abnormalarm.data.calendar

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.abhikjain360.abnormalarm.appContainer
import com.abhikjain360.abnormalarm.scheduling.DirectBoot
import java.time.Duration

/**
 * Periodic + on-demand reconciliation of calendar alarms (DESIGN.md §5). Deferrable and batched by
 * WorkManager, so Google API polling stays low-cost. The ContentObserver enqueues a one-shot copy
 * whenever the device calendar provider changes.
 */
class CalendarSyncWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            if (!DirectBoot.isUserUnlocked(applicationContext)) return Result.success()
            applicationContext.appContainer.calendarSync.sync(System.currentTimeMillis())
            Result.success()
        } catch (_: SecurityException) {
            // Calendar permission revoked mid-flight; nothing to do until re-granted.
            Result.success()
        }
    }

    companion object {
        private val PERIODIC_INTERVAL: Duration = Duration.ofHours(5)
        // Keep the legacy unique name so upgrades replace the old daily schedule in-place.
        private const val PERIODIC_NAME = "calendar-sync-daily"
        private const val ONESHOT_NAME = "calendar-sync-now"

        /** Schedule low-frequency Google/API safety-net sync: roughly 4-5 runs per day. */
        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(PERIODIC_INTERVAL).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_NAME, ExistingPeriodicWorkPolicy.UPDATE, request,
            )
        }

        /** Run a sync as soon as possible (used by observers, settings refresh, and app foreground). */
        fun syncNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<CalendarSyncWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONESHOT_NAME, ExistingWorkPolicy.REPLACE, request,
            )
        }
    }
}
