package com.reelshield.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.reelshield.app.data.QuotaManager
import com.reelshield.app.service.ResuscitationWorker
import com.reelshield.app.service.WatcherForegroundService
import com.reelshield.app.util.BatteryOptimizationHelper

/**
 * BootCompletedReceiver:
 * BroadcastReceiver for ACTION_BOOT_COMPLETED and ACTION_MY_PACKAGE_REPLACED.
 * Also handles OEM fast-boot actions (HTC/MIUI QUICKBOOT_POWERON).
 *
 * Responsibilities:
 * 1. Immediately restarts WatcherForegroundService upon phone reboot or app update.
 * 2. Enqueues the WorkManager hourly PeriodicWorkRequest (ResuscitationWorker).
 * 3. Verifies battery optimization exemptions (detecting aggressive OEM killers).
 */
class BootCompletedReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        val action = intent.action ?: return
        Log.i(TAG, "BootCompletedReceiver triggered with action: $action")

        val validBootActions = listOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON"
        )

        if (action in validBootActions) {
            val quotaManager = QuotaManager(context)
            quotaManager.checkAndResetDaily()

            // 1. Immediately restart the foreground keep-alive service
            Log.i(TAG, "Auto-starting WatcherForegroundService on boot/package replaced.")
            WatcherForegroundService.start(context)

            // 2. Schedule the hourly PeriodicWorkRequest for WorkManager
            ResuscitationWorker.schedule(context)

            // 3. Verify Battery Optimization status
            val isWhitelisted = BatteryOptimizationHelper.isIgnoringBatteryOptimizations(context)
            Log.i(TAG, "Battery optimization exemption status on boot: isIgnoring=$isWhitelisted")
            if (!isWhitelisted && (quotaManager.isInstagramBlockEnabled || quotaManager.isYouTubeBlockEnabled)) {
                WatcherForegroundService.instance?.notifyBatteryOptimizationNeeded()
            }
        }
    }
}
