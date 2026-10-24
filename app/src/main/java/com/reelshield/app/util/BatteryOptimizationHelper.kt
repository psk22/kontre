package com.reelshield.app.util

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

/**
 * Onboarding Intent Wizard:
 * Battery optimization checker & platform-specific OEM fallback intents
 * to survive aggressive OEM background process killers (One UI, HyperOS, ColorOS, OriginOS).
 */
object BatteryOptimizationHelper {

    private const val TAG = "BatteryOptimization"

    /**
     * Checks if the app is currently whitelisted from battery optimizations.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Requests battery optimization exemption using ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.
     * Falls back to general battery optimization settings if direct intent is blocked.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(context: Context) {
        val packageName = context.packageName
        if (isIgnoringBatteryOptimizations(context)) return

        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:$packageName")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Direct ignore battery optimization request failed, trying settings fallback", e)
            try {
                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (fallbackEx: Exception) {
                Log.e(TAG, "Battery optimization settings fallback failed", fallbackEx)
                openAppDetailsSettings(context)
            }
        }
    }

    /**
     * Dispatches platform-specific OEM fallback intents:
     * - Samsung: Auto-Sleep / Device Care
     * - Xiaomi: HyperOS / MIUI Auto-Start & PowerKeeper
     * - Oppo / Realme: ColorOS background lock / startup manager
     * - Vivo: FuntouchOS / OriginOS background power manager
     * - Huawei: Startup Manager / Process Protect
     */
    fun openOemBackgroundSettings(context: Context): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val intentList = when {
            manufacturer.contains("samsung") -> getSamsungIntents()
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> getXiaomiIntents()
            manufacturer.contains("oppo") || manufacturer.contains("realme") || manufacturer.contains("oneplus") -> getOppoIntents()
            manufacturer.contains("vivo") || manufacturer.contains("iqoo") -> getVivoIntents()
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> getHuaweiIntents()
            else -> emptyList()
        }

        for (intent in intentList) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            } catch (e: Exception) {
                Log.d(TAG, "Failed OEM intent: ${intent.component}", e)
            }
        }

        return openAppDetailsSettings(context)
    }

    private fun getSamsungIntents(): List<Intent> = listOf(
        Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
        Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity")),
        Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.dashboard.AppLimitsActivity"))
    )

    private fun getXiaomiIntents(): List<Intent> = listOf(
        Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")),
        Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
    )

    private fun getOppoIntents(): List<Intent> = listOf(
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity")),
        Intent().setComponent(ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"))
    )

    private fun getVivoIntents(): List<Intent> = listOf(
        Intent().setComponent(ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity")),
        Intent().setComponent(ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"))
    )

    private fun getHuaweiIntents(): List<Intent> = listOf(
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")),
        Intent().setComponent(ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"))
    )

    fun openAppDetailsSettings(context: Context): Boolean {
        return try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open application details settings", e)
            false
        }
    }
}
