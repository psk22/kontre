package com.reelshield.app.service

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.reelshield.app.data.QuotaManager
import java.util.concurrent.TimeUnit

/**
 * ResuscitationWorker:
 * Hourly WorkManager periodic background resuscitation worker.
 * Configured with setRequiresBatteryNotLow(false) so aggressive OEM battery savers
 * (HyperOS, ColorOS, One UI, OriginOS) cannot starve or delay the periodic keep-alive.
 *
 * Responsibilities:
 * 1. Verifies quota database consistency (daily reset check).
 * 2. Ensures WatcherForegroundService is active and running.
 * 3. Triggers watchdog co-monitoring check between foreground service and accessibility service.
 */
class ResuscitationWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val TAG = "ResuscitationWorker"
        const val UNIQUE_PERIODIC_WORK_NAME = "ResuscitationPeriodicWork"

        /**
         * Schedules the hourly PeriodicWorkRequest with setRequiresBatteryNotLow(false).
         */
        fun schedule(context: Context) {
            try {
                val constraints = Constraints.Builder()
                    .setRequiresBatteryNotLow(false)
                    .build()

                val periodicWorkRequest = PeriodicWorkRequestBuilder<ResuscitationWorker>(
                    1, TimeUnit.HOURS
                )
                    .setConstraints(constraints)
                    .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    periodicWorkRequest
                )
                Log.i(TAG, "Hourly ResuscitationWorker scheduled with setRequiresBatteryNotLow(false).")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to schedule ResuscitationWorker", e)
            }
        }
    }

    override suspend fun doWork(): Result {
        Log.i(TAG, "Executing periodic resuscitation check...")
        return try {
            val context = applicationContext

            // 1. Verify database / quota consistency
            val quotaManager = QuotaManager(context)
            quotaManager.checkAndResetDaily()
            Log.d(TAG, "Quota consistency verified: ${quotaManager.totalCount}/${quotaManager.dailyLimit}")

            // 2. Ensure WatcherForegroundService is running
            WatcherForegroundService.start(context)

            // 3. Trigger watchdog verification between services
            WatcherForegroundService.instance?.performWatchdogCheck()

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "ResuscitationWorker execution error", e)
            Result.retry()
        }
    }
}
