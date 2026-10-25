package com.reelshield.app.service

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
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
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.hypot

class FloatingCounterManager(private val service: AccessibilityService) {

    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var overlayView: FrameLayout? = null
    private var counterTextView: TextView? = null

    // Dismiss target ("X" at bottom)
    private var dismissTargetView: LinearLayout? = null
    private var dismissCircleView: FrameLayout? = null
    private var isDismissTargetAttached = false

    @Volatile
    var isShowing = false
        private set

    var onDismissRequested: (() -> Unit)? = null

    private var currentCount = 0
    private var currentLimit = 50

    @Volatile
    private var isDismissedForSession = false

    fun resetSessionDismissal() {
        isDismissedForSession = false
    }

    private val overlayLayoutParams by lazy {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(16f).toInt()
            y = dpToPx(120f).toInt()
        }
    }

    private val dismissLayoutParams by lazy {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dpToPx(56f).toInt()
        }
    }

    companion object {
        private const val TAG = "FloatingCounterManager"
    }

    private fun safeAddView(view: View?, params: WindowManager.LayoutParams): Boolean {
        if (view == null) return false
        return try {
            if (!view.isAttachedToWindow) {
                windowManager.addView(view, params)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "safeAddView suppressed error: ${e.message}")
            false
        }
    }

    private fun safeRemoveView(view: View?): Boolean {
        if (view == null) return false
        return try {
            if (view.isAttachedToWindow) {
                windowManager.removeView(view)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "safeRemoveView suppressed error: ${e.message}")
            false
        }
    }

    private fun safeUpdateViewLayout(view: View?, params: WindowManager.LayoutParams): Boolean {
        if (view == null) return false
        return try {
            if (view.isAttachedToWindow) {
                windowManager.updateViewLayout(view, params)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "safeUpdateViewLayout suppressed error: ${e.message}")
            false
        }
    }

    fun showOrUpdate(count: Int, limit: Int = 50) {
        if (isDismissedForSession) return
        currentCount = count
        currentLimit = limit
        mainHandler.post {
            try {
                val view = overlayView
                if (isShowing && view != null && view.isAttachedToWindow) {
                    updateText(count, limit)
                } else {
                    createAndAttachOverlay(count, limit)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error displaying floating counter overlay", e)
            }
        }
    }

    fun hide() {
        mainHandler.post {
            try {
                hideDismissTarget()
                val view = overlayView
                if (view != null) {
                    safeRemoveView(view)
                    overlayView = null
                }
                isShowing = false
                Log.i(TAG, "Floating counter overlay removed from window.")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing floating counter overlay", e)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createAndAttachOverlay(initialCount: Int, initialLimit: Int) {
        val minDim = dpToPx(38f).toInt()
        val padH = dpToPx(12f).toInt()
        val padV = dpToPx(6f).toInt()

        val container = FrameLayout(service).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            minimumWidth = minDim
            minimumHeight = minDim
            setPadding(padH, padV, padH, padV)
            elevation = dpToPx(10f)
        }

        val isOver = initialCount >= initialLimit && initialLimit > 0
        val backgroundDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dpToPx(20f)
            setColor(Color.parseColor("#EE12141F")) // Sleek translucent midnight plum
            setStroke(
                dpToPx(1.5f).toInt(),
                if (isOver) Color.parseColor("#FF4A36") else Color.parseColor("#55FF4A36")
            )
        }
        container.background = backgroundDrawable

        val tv = TextView(service).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            text = if (initialLimit > 0) "$initialCount / $initialLimit" else "$initialCount"
            textSize = 13f
            setTextColor(if (isOver) Color.parseColor("#FF4A36") else Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        container.addView(tv)
        counterTextView = tv

        // Touch listener for dragging and snapping
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var isDragging = false
        var isHoveringDismiss = false
        var lastLayoutUpdateTime = 0L

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = overlayLayoutParams.x
                    initialY = overlayLayoutParams.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isDragging = false
                    isHoveringDismiss = false
                    lastLayoutUpdateTime = android.os.SystemClock.uptimeMillis()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val deltaX = (event.rawX - touchStartX).toInt()
                    val deltaY = (event.rawY - touchStartY).toInt()

                    if (!isDragging && (hypot(deltaX.toDouble(), deltaY.toDouble()) > dpToPx(6f))) {
                        isDragging = true
                        showDismissTarget()
                    }

                    if (isDragging) {
                        overlayLayoutParams.x = initialX + deltaX
                        overlayLayoutParams.y = initialY + deltaY

                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastLayoutUpdateTime >= 16L) {
                            lastLayoutUpdateTime = now
                            safeUpdateViewLayout(overlayView, overlayLayoutParams)
                        }

                        val badgeW = container.width.takeIf { it > 0 } ?: dpToPx(44f).toInt()
                        val badgeH = container.height.takeIf { it > 0 } ?: dpToPx(44f).toInt()

                        val isNear = isNearDismissTarget(
                            touchX = event.rawX,
                            touchY = event.rawY,
                            badgeX = overlayLayoutParams.x,
                            badgeY = overlayLayoutParams.y,
                            badgeW = badgeW,
                            badgeH = badgeH
                        )

                        if (isNear != isHoveringDismiss) {
                            isHoveringDismiss = isNear
                            setDismissTargetHighlighted(isNear)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val badgeW = container.width.takeIf { it > 0 } ?: dpToPx(44f).toInt()
                    val badgeH = container.height.takeIf { it > 0 } ?: dpToPx(44f).toInt()

                    val isDroppedOnDismiss = isHoveringDismiss || isNearDismissTarget(
                        touchX = event.rawX,
                        touchY = event.rawY,
                        badgeX = overlayLayoutParams.x,
                        badgeY = overlayLayoutParams.y,
                        badgeW = badgeW,
                        badgeH = badgeH
                    )

                    hideDismissTarget()

                    if (isDroppedOnDismiss) {
                        Log.i(TAG, "Floating counter dragged to dismiss target. Removing for this session...")
                        isDismissedForSession = true
                        hide()
                        onDismissRequested?.invoke()
                    } else if (isDragging) {
                        // Snap badge to the nearest screen edge (left or right)
                        val (screenW, screenH) = getScreenDimensions()

                        val snapX = if (overlayLayoutParams.x + badgeW / 2 < screenW / 2) {
                            dpToPx(16f).toInt()
                        } else {
                            screenW - badgeW - dpToPx(16f).toInt()
                        }

                        val minY = dpToPx(60f).toInt()
                        val maxY = screenH - dpToPx(140f).toInt()
                        val snapY = overlayLayoutParams.y.coerceIn(minY, maxY)

                        overlayLayoutParams.x = snapX
                        overlayLayoutParams.y = snapY

                        safeUpdateViewLayout(overlayView, overlayLayoutParams)
                    }
                    true
                }

                else -> false
            }
        }

        // Clean up previous overlay if any
        overlayView?.let { safeRemoveView(it) }
        overlayView = container
        val added = safeAddView(container, overlayLayoutParams)
        isShowing = added
        Log.i(TAG, "Floating counter badge displayed (success=$added): '$currentCount / $currentLimit'")
    }

    private fun updateText(count: Int, limit: Int) {
        val isOver = count >= limit && limit > 0
        counterTextView?.apply {
            text = if (limit > 0) "$count / $limit" else "$count"
            setTextColor(if (isOver) Color.parseColor("#FF4A36") else Color.WHITE)
        }
        overlayView?.let { container ->
            (container.background as? GradientDrawable)?.setStroke(
                dpToPx(1.5f).toInt(),
                if (isOver) Color.parseColor("#FF4A36") else Color.parseColor("#55FF4A36")
            )
        }
    }

    private fun showDismissTarget() {
        if (dismissTargetView == null) {
            createDismissTargetView()
        }
        val target = dismissTargetView
        if (!isDismissTargetAttached && target != null) {
            setDismissTargetHighlighted(false)
            val added = safeAddView(target, dismissLayoutParams)
            isDismissTargetAttached = added
        }
    }

    private fun hideDismissTarget() {
        val target = dismissTargetView
        if (target != null) {
            safeRemoveView(target)
            isDismissTargetAttached = false
        }
    }

    private fun isNearDismissTarget(
        touchX: Float,
        touchY: Float,
        badgeX: Int,
        badgeY: Int,
        badgeW: Int,
        badgeH: Int
    ): Boolean {
        val (screenW, screenH) = getScreenDimensions()
        val targetCenterX = screenW / 2f
        val targetCenterY = screenH - dismissLayoutParams.y - dpToPx(28f)

        val threshold = dpToPx(110f)

        val fingerDist = hypot((touchX - targetCenterX).toDouble(), (touchY - targetCenterY).toDouble())
        if (fingerDist < threshold) return true

        val badgeCenterX = badgeX + (badgeW / 2f)
        val badgeCenterY = badgeY + (badgeH / 2f)
        val badgeDist = hypot((badgeCenterX - targetCenterX).toDouble(), (badgeCenterY - targetCenterY).toDouble())
        if (badgeDist < threshold) return true

        return false
    }

    private fun createDismissTargetView() {
        val root = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val circleSize = dpToPx(60f).toInt()
        val circleContainer = FrameLayout(service).apply {
            layoutParams = LinearLayout.LayoutParams(circleSize, circleSize).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            elevation = dpToPx(12f)
        }

        val xIcon = TextView(service).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            text = "✕"
            textSize = 22f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        }
        circleContainer.addView(xIcon)
        root.addView(circleContainer)

        val label = TextView(service).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpToPx(6f).toInt()
                gravity = Gravity.CENTER_HORIZONTAL
            }
            text = "Drag here to remove"
            textSize = 12f
            setTextColor(Color.parseColor("#E0E0E0"))
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(dpToPx(3f), 0f, dpToPx(1f), Color.BLACK)
        }
        root.addView(label)

        dismissCircleView = circleContainer
        dismissTargetView = root
    }

    private fun setDismissTargetHighlighted(highlighted: Boolean) {
        val circle = dismissCircleView ?: return
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (highlighted) {
                setColor(Color.parseColor("#E6D32F2F")) // Vivid Crimson Red
                setStroke(dpToPx(2.5f).toInt(), Color.WHITE)
            } else {
                setColor(Color.parseColor("#CC1E1E2C")) // Translucent dark
                setStroke(dpToPx(1.5f).toInt(), Color.parseColor("#80FFFFFF"))
            }
        }
        circle.background = bg
        circle.scaleX = if (highlighted) 1.2f else 1.0f
        circle.scaleY = if (highlighted) 1.2f else 1.0f
    }

    private fun getScreenDimensions(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val size = android.graphics.Point()
            @Suppress("DEPRECATION")
            display.getSize(size)
            Pair(size.x, size.y)
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
