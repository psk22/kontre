package com.reelshield.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.reelshield.app.R
import com.reelshield.app.data.QuotaManager

class BlockingOverlayService : Service() {

    private val TAG = "BlockingOverlayService"
    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isOverlayShowing = false

    private lateinit var quotaManager: QuotaManager
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        const val ACTION_SHOW = "com.reelshield.app.ACTION_SHOW_OVERLAY"
        const val ACTION_HIDE = "com.reelshield.app.ACTION_HIDE_OVERLAY"
        const val EXTRA_PACKAGE_NAME = "extra_package_name"

        fun show(context: Context, targetPackage: String) {
            val intent = Intent(context, BlockingOverlayService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_PACKAGE_NAME, targetPackage)
            }
            context.startService(intent)
        }

        fun hide(context: Context) {
            val intent = Intent(context, BlockingOverlayService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        quotaManager = QuotaManager(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                val targetPkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: ""
                showOverlay(targetPkg)
            }
            ACTION_HIDE -> {
                hideOverlay()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay(targetPackage: String) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w(TAG, "Cannot draw overlay: SYSTEM_ALERT_WINDOW permission not granted")
            return
        }

        mainHandler.post {
            if (isOverlayShowing && overlayView != null) {
                // Update live countdown
                overlayView?.findViewById<TextView>(R.id.tv_countdown)?.text =
                    quotaManager.getTimeUntilMidnightFormatted()
                return@post
            }

            try {
                val inflater = LayoutInflater.from(this)
                val view = inflater.inflate(R.layout.layout_reel_block_overlay, null)

                val btnExit = view.findViewById<Button>(R.id.btn_exit_reels)
                val btnDismiss = view.findViewById<TextView>(R.id.btn_dismiss_overlay)
                val tvCountdown = view.findViewById<TextView>(R.id.tv_countdown)
                val tvAppNote = view.findViewById<TextView>(R.id.tv_app_note)

                tvCountdown.text = quotaManager.getTimeUntilMidnightFormatted()
                val appLabel = if (targetPackage.contains("instagram")) "Instagram" else "YouTube"
                tvAppNote.text = "You can still use $appLabel for chats, DMs, stories, and feed posts."

                btnExit.text = "Back to $appLabel Home Feed"
                btnExit.setOnClickListener {
                    // Send home intent to open app main feed
                    val launchIntent = packageManager.getLaunchIntentForPackage(targetPackage)?.apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    }
                    if (launchIntent != null) {
                        startActivity(launchIntent)
                    }
                    hideOverlay()
                }

                btnDismiss.setOnClickListener {
                    hideOverlay()
                }

                val layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                    else
                        WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.CENTER
                }

                windowManager?.addView(view, layoutParams)
                overlayView = view
                isOverlayShowing = true
                Log.i(TAG, "Blocking overlay attached successfully via WindowManager.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to display overlay", e)
            }
        }
    }

    private fun hideOverlay() {
        mainHandler.post {
            if (isOverlayShowing && overlayView != null) {
                try {
                    windowManager?.removeView(overlayView)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing overlay", e)
                } finally {
                    overlayView = null
                    isOverlayShowing = false
                    Log.i(TAG, "Blocking overlay removed.")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
    }
}
