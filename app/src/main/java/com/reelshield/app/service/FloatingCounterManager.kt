package com.reelshield.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.reelshield.app.data.QuotaManager

class FloatingCounterManager(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs = service.getSharedPreferences("floating_counter_prefs", Context.MODE_PRIVATE)
    private val quotaManager by lazy { QuotaManager(service) }

    // Strict single instance pattern: exactly one container & one textview
    private var overlayContainer: FrameLayout? = null
    private var counterTextView: TextView? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null

    @Volatile
    var isShowing = false
        private set

    @Volatile
    var isDragging = false
        private set

    private var currentCount = 0
    private var currentLimit = 50

    companion object {
        private const val TAG = "FloatingCounterManager"
        private const val PREF_X = "counter_x"
        private const val PREF_Y = "counter_y"
    }

    private fun buildLayoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val (screenWidth, screenHeight) = getScreenDimensions()
        val maxX = (screenWidth - dpToPx(60f).toInt()).coerceAtLeast(0)
        val maxY = (screenHeight - dpToPx(40f).toInt()).coerceAtLeast(0)

        val savedX = prefs.getInt(PREF_X, dpToPx(16f).toInt()).coerceIn(0, maxX)
        val savedY = prefs.getInt(PREF_Y, dpToPx(120f).toInt()).coerceIn(0, maxY)

        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }
    }

    private fun getOrCreateOverlayView(): FrameLayout {
        overlayContainer?.let { return it }

        val minDim = dpToPx(38f).toInt()
        val padH = dpToPx(12f).toInt()
        val padV = dpToPx(6f).toInt()

        val container = FrameLayout(service).apply {
            minimumWidth = minDim
            minimumHeight = minDim
            setPadding(padH, padV, padH, padV)
            elevation = dpToPx(10f)
            isClickable = true
            isFocusable = false
        }

        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(20f)
            setColor(Color.parseColor("#EE12141F"))
            setStroke(dpToPx(1.5f).toInt(), Color.parseColor("#55FF4A36"))
        }
        container.background = backgroundDrawable

        val tv = TextView(service).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            text = "$currentCount/$currentLimit"
            textSize = 13f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        container.addView(tv)
        counterTextView = tv
        overlayContainer = container

        val params = windowLayoutParams ?: buildLayoutParams().also { windowLayoutParams = it }

        var initialTouchX = 0f
        var initialTouchY = 0f
        var initialParamX = 0
        var initialParamY = 0

        container.setOnTouchListener { v, event ->
            val lp = windowLayoutParams ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = true
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialParamX = lp.x
                    initialParamY = lp.y
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    val (screenWidth, screenHeight) = getScreenDimensions()
                    val vWidth = if (v.width > 0) v.width else dpToPx(56f).toInt()
                    val vHeight = if (v.height > 0) v.height else dpToPx(36f).toInt()

                    val maxX = (screenWidth - vWidth).coerceAtLeast(0)
                    val maxY = (screenHeight - vHeight).coerceAtLeast(0)

                    lp.x = (initialParamX + dx).coerceIn(0, maxX)
                    lp.y = (initialParamY + dy).coerceIn(0, maxY)

                    if (isShowing && v.isAttachedToWindow) {
                        try {
                            windowManager.updateViewLayout(v, lp)
                        } catch (e: Exception) {
                            Log.w(TAG, "updateViewLayout error: ${e.message}")
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_OUTSIDE -> {
                    isDragging = false
                    prefs.edit()
                        .putInt(PREF_X, lp.x)
                        .putInt(PREF_Y, lp.y)
                        .apply()
                    true
                }
                else -> false
            }
        }

        return container
    }

    fun showOrUpdate(count: Int, limit: Int = 50) {
        // Enforce toggle: if disabled, hide immediately and do not show
        if (!quotaManager.isFloatingCounterEnabled) {
            hide()
            return
        }

        currentCount = count
        currentLimit = limit
        mainHandler.post {
            try {
                if (!quotaManager.isFloatingCounterEnabled) {
                    hide()
                    return@post
                }

                val container = getOrCreateOverlayView()
                updateText(count, limit)

                if (isDragging) {
                    return@post
                }

                val lp = windowLayoutParams ?: buildLayoutParams().also { windowLayoutParams = it }

                if (!isShowing || !container.isAttachedToWindow) {
                    if (!container.isAttachedToWindow) {
                        try {
                            windowManager.addView(container, lp)
                            isShowing = true
                            Log.i(TAG, "Floating counter overlay attached to window ($count/$limit).")
                        } catch (e: IllegalStateException) {
                            isShowing = true
                        } catch (e: Exception) {
                            Log.w(TAG, "Error adding overlay view: ${e.message}")
                        }
                    } else {
                        isShowing = true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in showOrUpdate", e)
            }
        }
    }

    fun hide() {
        mainHandler.post {
            try {
                isDragging = false
                val container = overlayContainer
                if (container != null && container.isAttachedToWindow) {
                    try {
                        windowManager.removeViewImmediate(container)
                    } catch (e: Exception) {
                        try {
                            windowManager.removeView(container)
                        } catch (e2: Exception) {
                            Log.w(TAG, "removeView error: ${e2.message}")
                        }
                    }
                }
                if (isShowing) {
                    Log.i(TAG, "Floating counter overlay hidden.")
                }
                isShowing = false
            } catch (e: Exception) {
                Log.e(TAG, "Error in hide", e)
            }
        }
    }

    fun destroy() {
        mainHandler.post {
            isDragging = false
            val container = overlayContainer
            if (container != null && container.isAttachedToWindow) {
                try {
                    windowManager.removeViewImmediate(container)
                } catch (e: Exception) {
                    try {
                        windowManager.removeView(container)
                    } catch (_: Exception) {}
                }
            }
            overlayContainer = null
            counterTextView = null
            windowLayoutParams = null
            isShowing = false
        }
    }

    private fun updateText(count: Int, limit: Int) {
        val isOver = count >= limit && limit > 0
        counterTextView?.apply {
            text = if (limit > 0) "$count/$limit" else "$count"
            setTextColor(if (isOver) Color.parseColor("#FF4A36") else Color.WHITE)
        }
        overlayContainer?.let { container ->
            (container.background as? GradientDrawable)?.setStroke(
                dpToPx(1.5f).toInt(),
                if (isOver) Color.parseColor("#FF4A36") else Color.parseColor("#55FF4A36")
            )
        }
    }

    private fun getScreenDimensions(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val bounds = windowManager.currentWindowMetrics.bounds
                Pair(bounds.width(), bounds.height())
            } catch (e: Exception) {
                val dm = service.resources.displayMetrics
                Pair(dm.widthPixels, dm.heightPixels)
            }
        } else {
            val dm = service.resources.displayMetrics
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    private fun dpToPx(dp: Float): Float {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            service.resources.displayMetrics
        )
    }
}
