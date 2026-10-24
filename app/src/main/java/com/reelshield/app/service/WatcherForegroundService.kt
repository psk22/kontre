package com.reelshield.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.reelshield.app.MainActivity
import com.reelshield.app.R

/**
 * WatcherForegroundService:
 * Foreground keep-alive service engineered to withstand aggressive OEM background process killers.
 * Runs with low-priority ongoing status bar notification ("Focus Monitor Active").
 * Bound to VideoBlockerAccessibilityService for lifecycle co-monitoring.
 */
open class WatcherForegroundService : Service() {

    inner class LocalBinder : Binder() {
        fun getService(): WatcherForegroundService = this@WatcherForegroundService
    }

    private val binder = LocalBinder()

    companion object {
        const val CHANNEL_ID = "channel_focus_monitor"
        const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            try {
                val intent = Intent(context, WatcherForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("WatcherFGS", "Failed to start foreground service", e)
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, WatcherForegroundService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                android.util.Log.e("WatcherFGS", "Failed to stop foreground service", e)
            }
        }

        fun bind(context: Context, connection: ServiceConnection): Boolean {
            return try {
                val intent = Intent(context, WatcherForegroundService::class.java)
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            } catch (e: Exception) {
                android.util.Log.e("WatcherFGS", "Failed to bind foreground service", e)
                false
            }
        }

        fun unbind(context: Context, connection: ServiceConnection) {
            try {
                context.unbindService(connection)
            } catch (e: Exception) {
                android.util.Log.e("WatcherFGS", "Failed to unbind foreground service", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            createNotificationChannel()
        } catch (e: Exception) {
            android.util.Log.e("WatcherFGS", "Failed to create notification channel", e)
        }
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
            android.util.Log.e("WatcherFGS", "Failed in startForeground", e)
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Focus Monitor Active",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Focus Monitor active in background without battery kill"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
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
}
