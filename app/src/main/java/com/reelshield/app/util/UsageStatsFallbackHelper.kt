package com.reelshield.app.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import android.util.Log

/**
 * UsageStatsFallbackHelper:
 * Redundant fallback detector using UsageStatsManager.
 * If AccessibilityService is disabled, killed by OEM deep sleep, or permission is revoked,
 * this helper queries foreground activity events to detect monitored packages
 * (Instagram, YouTube) and enforce focus limits.
 */
object UsageStatsFallbackHelper {

    private const val TAG = "UsageStatsFallback"

    /**
     * Checks if PACKAGE_USAGE_STATS permission (Usage Access) has been granted by the user.
     */
    fun hasUsageStatsPermission(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            Log.e(TAG, "Error checking usage stats permission", e)
            false
        }
    }

    /**
     * Launches the system Usage Access settings screen for Kontre.
     */
    fun openUsageAccessSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val genericIntent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(genericIntent)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed to open usage access settings", ex)
            }
        }
    }

    /**
     * Detects the currently active foreground package using UsageEvents.
     */
    fun getForegroundPackage(context: Context): String? {
        if (!hasUsageStatsPermission(context)) return null

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val now = System.currentTimeMillis()
        val startTime = now - 10_000L // inspect last 10 seconds

        return try {
            val events = usageStatsManager.queryEvents(startTime, now)
            val event = UsageEvents.Event()
            var lastForegroundPkg: String? = null
            var lastTimestamp = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val type = event.eventType
                if (type == UsageEvents.Event.ACTIVITY_RESUMED ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && type == UsageEvents.Event.ACTIVITY_PAUSED.inv())
                ) {
                    if (event.timeStamp >= lastTimestamp) {
                        lastForegroundPkg = event.packageName
                        lastTimestamp = event.timeStamp
                    }
                }
            }

            if (lastForegroundPkg != null) {
                return lastForegroundPkg
            }

            // Secondary fallback via queryUsageStats
            val statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 60_000L,
                now
            )
            statsList?.maxByOrNull { it.lastTimeUsed }?.packageName
        } catch (e: Exception) {
            Log.e(TAG, "Error querying foreground package via UsageStatsManager", e)
            null
        }
    }
}
