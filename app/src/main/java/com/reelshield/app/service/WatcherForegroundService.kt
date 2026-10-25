package com.reelshield.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.reelshield.app.MainActivity
import com.reelshield.app.R
import com.reelshield.app.util.BatteryOptimizationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * WatcherForegroundService:
 * Foreground keep-alive service engineered to withstand aggressive OEM background process killers
 * (HyperOS, ColorOS, One UI, OriginOS).
 *
 * Responsibilities:
 * 1. Runs with low-priority ongoing status bar notification ("Focus Monitor Active").
 * 2. Dual-Service Watchdog Heartbeat: Pings VideoBlockerAccessibilityService via IPC broadcast
 *    every 60 seconds.
 * 3. Detects if accessibility service was killed by OEM deep sleep or disabled; fires a local
 *    high-priority reminder notification directing the user to tap and re-enable.
 * 4. Bound to VideoBlockerAccessibilityService for reciprocal lifecycle co-monitoring.
 */
open class WatcherForegroundService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): WatcherForegroundService = this@WatcherForegroundService
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watchdogJob: Job? = null

    // Heartbeat & Watchdog Timestamps
    private var lastAccessibilityPongTimestamp = 0L
    private var lastPingSentTimestamp = 0L
    private var serviceStartTime = 0L
    private var isAlertNotificationShowing = false
    private var isPongReceiverRegistered = false

    companion object {
        private const val TAG = "WatcherFGS"

        const val CHANNEL_ID = "channel_focus_monitor"
        const val CHANNEL_ALERT_ID = "channel_accessibility_alert"
        const val CHANNEL_BATTERY_ID = "channel_battery_optimization"

        const val NOTIFICATION_ID = 1001
        const val ALERT_NOTIFICATION_ID = 2002
        const val BATTERY_NOTIFICATION_ID = 2003

        const val ACTION_WATCHDOG_PING = "com.reelshield.app.ACTION_WATCHDOG_PING"
        const val ACTION_WATCHDOG_PONG = "com.reelshield.app.ACTION_WATCHDOG_PONG"

        const val WATCHDOG_INTERVAL_MS = 60_000L // 60-second IPC watchdog heartbeat

        @Volatile
        var instance: WatcherForegroundService? = null
            private set

        fun start(context: Context) {
            try {
                val intent = Intent(context, WatcherForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, WatcherForegroundService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to stop foreground service", e)
            }
        }

        fun bind(context: Context, connection: ServiceConnection): Boolean {
            return try {
                val intent = Intent(context, WatcherForegroundService::class.java)
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bind foreground service", e)
                false
            }
        }

        fun unbind(context: Context, connection: ServiceConnection) {
            try {
                context.unbindService(connection)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unbind foreground service", e)
            }
        }

        /**
         * Checks whether VideoBlockerAccessibilityService is enabled in Android Secure Settings.
         */
        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val expectedComponent = ComponentName(context, VideoBlockerAccessibilityService::class.java)
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false

            val colonSplitter = TextUtils.SimpleStringSplitter(':')
            colonSplitter.setString(enabledServices)
            while (colonSplitter.hasNext()) {
                val componentNameString = colonSplitter.next()
                val enabledComponent = ComponentName.unflattenFromString(componentNameString)
                if (enabledComponent != null && enabledComponent == expectedComponent) {
                    return true
                }
                if (componentNameString.contains(context.packageName) &&
                    componentNameString.contains("VideoBlockerAccessibilityService")
                ) {
                    return true
                }
            }
            return false
        }
    }

    // IPC Broadcast Receiver listening for PONG from VideoBlockerAccessibilityService
    private val pongReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_WATCHDOG_PONG) {
                lastAccessibilityPongTimestamp = System.currentTimeMillis()
                Log.d(TAG, "Dual-service watchdog: received PONG from AccessibilityService.")
                dismissAccessibilityAlertNotification()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        serviceStartTime = System.currentTimeMillis()
        lastAccessibilityPongTimestamp = System.currentTimeMillis()

        try {
            createNotificationChannels()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create notification channels", e)
        }

        // Register IPC Pong BroadcastReceiver
        registerPongReceiver()

        // Start 60-second periodic watchdog heartbeat
        startWatchdogHeartbeat()

        Log.i(TAG, "WatcherForegroundService created with Dual-Service Watchdog Heartbeat (60s).")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed in startForeground", e)
        }

        // Trigger immediate watchdog ping on start
        performWatchdogCheck()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun registerPongReceiver() {
        if (!isPongReceiverRegistered) {
            try {
                val filter = IntentFilter(ACTION_WATCHDOG_PONG)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(pongReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(pongReceiver, filter)
                }
                isPongReceiverRegistered = true
                Log.d(TAG, "Registered watchdog pong receiver.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register pong receiver", e)
            }
        }
    }

    private fun unregisterPongReceiver() {
        if (isPongReceiverRegistered) {
            try {
                unregisterReceiver(pongReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister pong receiver", e)
            } finally {
                isPongReceiverRegistered = false
            }
        }
    }

    private fun startWatchdogHeartbeat() {
        if (watchdogJob == null || watchdogJob?.isActive != true) {
            watchdogJob = serviceScope.launch {
                // Initial 15s grace period on boot / service launch
                delay(15_000L)
                while (isActive) {
                    performWatchdogCheck()
                    delay(WATCHDOG_INTERVAL_MS)
                }
            }
        }
    }

    /**
     * Executes the Dual-Service Watchdog check:
     * 1. Broadcasts an IPC ping to VideoBlockerAccessibilityService.
     * 2. Inspects whether the accessibility service is alive or killed by OEM deep sleep.
     * 3. Triggers a high-priority reminder notification if dead.
     */
    fun performWatchdogCheck() {
        val now = System.currentTimeMillis()
        val isSettingsEnabled = isAccessibilityServiceEnabled(this)
        val isServiceInstantiated = VideoBlockerAccessibilityService.instance != null

        // 1. Send IPC Broadcast Ping
        lastPingSentTimestamp = now
        try {
            val pingIntent = Intent(ACTION_WATCHDOG_PING).apply {
                setPackage(packageName)
            }
            sendBroadcast(pingIntent)
            Log.d(TAG, "Broadcasted watchdog PING to VideoBlockerAccessibilityService.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to broadcast watchdog ping", e)
        }

        // 2. Evaluate liveness criteria:
        // A service is considered dead if:
        // - It has been disabled in Android Settings, OR
        // - It is not instantiated after startup grace period (30s), OR
        // - It missed pongs for > 90 seconds (1.5 watchdog intervals)
        val hasMissedPong = lastAccessibilityPongTimestamp > 0L && (now - lastAccessibilityPongTimestamp > 90_000L)
        val isNotRunning = !isServiceInstantiated && (now - serviceStartTime > 30_000L)

        val isDead = !isSettingsEnabled || isNotRunning || hasMissedPong

        if (isDead) {
            Log.w(
                TAG,
                "Watchdog detected AccessibilityService is DEAD or KILLED: " +
                        "settingsEnabled=$isSettingsEnabled, instantiated=$isServiceInstantiated, " +
                        "lastPongMsAgo=${now - lastAccessibilityPongTimestamp}"
            )
            showAccessibilityAlertNotification()
        } else {
            dismissAccessibilityAlertNotification()
        }
    }

    /**
     * Shows a local high-priority reminder notification directing user to re-enable accessibility.
     */
    private fun showAccessibilityAlertNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return

        // Intent to open Android Accessibility Settings directly
        val settingsIntent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val settingsPendingIntent = PendingIntent.getActivity(
            this,
            101,
            settingsIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Intent to open MainActivity
        val appIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("EXTRA_OPEN_ACCESSIBILITY", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val appPendingIntent = PendingIntent.getActivity(
            this,
            102,
            appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ALERT_ID)
            .setContentTitle("⚠️ Focus Shield Protection Inactive")
            .setContentText("Short-form video blocker was stopped by the system. Tap to re-enable.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Video Blocker Accessibility Service was stopped by Android or OEM deep sleep " +
                            "(HyperOS, ColorOS, One UI). Tap Re-Enable to restore your focus limits."
                )
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(appPendingIntent)
            .addAction(0, "Re-Enable", settingsPendingIntent)
            .build()

        notificationManager.notify(ALERT_NOTIFICATION_ID, notification)
        isAlertNotificationShowing = true
        Log.w(TAG, "High-priority accessibility alert notification displayed.")
    }

    private fun dismissAccessibilityAlertNotification() {
        if (isAlertNotificationShowing) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.cancel(ALERT_NOTIFICATION_ID)
            isAlertNotificationShowing = false
            Log.i(TAG, "Accessibility alert notification dismissed (service is alive).")
        }
    }

    /**
     * Alerts the user if battery optimization exemption is missing, directing them to OEM settings.
     */
    fun notifyBatteryOptimizationNeeded() {
        if (BatteryOptimizationHelper.isIgnoringBatteryOptimizations(this)) return

        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        val appIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("EXTRA_OPEN_BATTERY_SETTINGS", true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            103,
            appIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_BATTERY_ID)
            .setContentTitle("⚠️ Background Keep-Alive Warning")
            .setContentText("Exempt ReelShield from battery optimization to prevent OEM kills.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Aggressive OEM task killers (One UI, HyperOS, ColorOS) may terminate background monitoring. " +
                            "Tap to configure auto-start and unconstrained background battery permissions."
                )
            )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(BATTERY_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java) ?: return

            // 1. Ongoing Keep-Alive Channel (Low Importance)
            val monitorChannel = NotificationChannel(
                CHANNEL_ID,
                "Focus Monitor Active",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Focus Monitor active in background without battery kill"
                setShowBadge(false)
            }
            manager.createNotificationChannel(monitorChannel)

            // 2. High-Priority Alert Channel for Accessibility Loss
            val alertChannel = NotificationChannel(
                CHANNEL_ALERT_ID,
                "Focus Protection Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when accessibility service is killed by OEM power managers"
                enableLights(true)
                enableVibration(true)
            }
            manager.createNotificationChannel(alertChannel)

            // 3. Battery Optimization Guidance Channel
            val batteryChannel = NotificationChannel(
                CHANNEL_BATTERY_ID,
                "Battery Optimization Warnings",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Guidance for configuring OEM auto-start and battery exemptions"
            }
            manager.createNotificationChannel(batteryChannel)
        }
    }

    private fun buildNotification(): Notification {
        val launchIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Focus Monitor Active")
            .setContentText("Protecting your focus • Short-form limits enforced")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        watchdogJob?.cancel()
        serviceScope.cancel()
        unregisterPongReceiver()
        instance = null
        Log.i(TAG, "WatcherForegroundService destroyed.")
    }
}
