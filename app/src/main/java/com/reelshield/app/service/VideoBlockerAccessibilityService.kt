package com.reelshield.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
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
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.TextView
import com.reelshield.app.R
import com.reelshield.app.data.QuotaManager
import com.reelshield.app.data.rules.AppHeuristicRule
import com.reelshield.app.data.rules.DynamicRuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

open class VideoBlockerAccessibilityService : AccessibilityService() {

    private val TAG = "VideoBlockerService"

    protected lateinit var quotaManager: QuotaManager
    private lateinit var windowManager: WindowManager
    val mainHandler = Handler(Looper.getMainLooper())
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Reel Session & Deduplication Tracker
    lateinit var reelSessionTracker: ReelSessionTracker
        private set

    // Dynamic Heuristic Rule Engine
    lateinit var dynamicRuleEngine: DynamicRuleEngine
        private set

    val isShortFormCurrentlyActive: Boolean
        get() = ::reelSessionTracker.isInitialized && reelSessionTracker.currentActiveHash != null

    // Floating Counter Pill
    private var pillView: View? = null
    private var isPillShowing = false

    // Debounce to prevent repeated intervention bursts
    private var lastInterventionTimestamp = 0L

    // --- Event Coalescing / Time-Window Throttling & Settle Debounce ---
    private var lastEventProcessedTimestamp = 0L
    private var pendingSettleRunnable: Runnable? = null
    private val THROTTLE_WINDOW_MS = 60L
    private val DEBOUNCE_SETTLE_MS = 75L

    // --- Zero-Allocation Service Thread Node Pooling & Buffers ---
    private val sharedRect = Rect()
    private val sharedChildRect = Rect()
    private val flatNodeQueue = ArrayDeque<AccessibilityNodeInfo>(128)

    class NodeDepthEntry(
        var node: AccessibilityNodeInfo? = null,
        var depth: Int = 0
    ) {
        fun set(n: AccessibilityNodeInfo, d: Int) {
            node = n
            depth = d
        }
        fun clear() {
            node = null
            depth = 0
        }
    }

    private val maxPoolCapacity = 160
    private val entryPool = Array(maxPoolCapacity) { NodeDepthEntry() }
    private var poolIndex = 0
    private val depthQueue = ArrayDeque<NodeDepthEntry>(maxPoolCapacity)
    private val pooledNodesToRecycle = ArrayList<AccessibilityNodeInfo>(maxPoolCapacity)

    private fun obtainDepthEntry(node: AccessibilityNodeInfo, depth: Int): NodeDepthEntry {
        val entry = if (poolIndex < maxPoolCapacity) {
            entryPool[poolIndex++]
        } else {
            NodeDepthEntry()
        }
        entry.set(node, depth)
        return entry
    }

    private fun resetDepthPool() {
        for (i in 0 until poolIndex) {
            entryPool[i].clear()
        }
        poolIndex = 0
        depthQueue.clear()
        pooledNodesToRecycle.clear()
    }

    private fun cancelPendingSettle() {
        pendingSettleRunnable?.let {
            mainHandler.removeCallbacks(it)
            pendingSettleRunnable = null
        }
    }

    /**
     * Checks if a node qualifies as the primary swipe container (e.g. ViewPager2 or RecyclerView)
     * occupying significant screen real estate (>= 60% width and >= 50% height).
     */
    private fun isMajorScrollContainer(
        node: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (!node.isVisibleToUser) return false
        node.getBoundsInScreen(sharedRect)
        if (sharedRect.isEmpty) return false

        val minWidth = (screenWidth * 0.60f).toInt()
        val minHeight = (screenHeight * 0.50f).toInt()
        if (sharedRect.width() < minWidth || sharedRect.height() < minHeight) {
            return false
        }

        val className = (node.className?.toString() ?: "").lowercase()
        val resId = (node.viewIdResourceName ?: "").lowercase()

        return className.contains("viewpager") ||
                className.contains("recyclerview") ||
                className.contains("scrollview") ||
                resId.contains("view_pager") ||
                resId.contains("recycler") ||
                resId.contains("clips") ||
                resId.contains("reel") ||
                resId.contains("short") ||
                resId.contains("player") ||
                resId.contains("container") ||
                resId.contains("layout")
    }

    // Lifecycle monitoring binding with WatcherForegroundService
    private var isWatcherBound = false
    private val watcherConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isWatcherBound = true
            Log.d(TAG, "Bound to WatcherForegroundService successfully.")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isWatcherBound = false
            Log.w(TAG, "Disconnected from WatcherForegroundService! Resuscitating foreground keeper...")
            WatcherForegroundService.start(this@VideoBlockerAccessibilityService)
            isWatcherBound = WatcherForegroundService.bind(this@VideoBlockerAccessibilityService, this)
        }
    }

    // Dual-Service Watchdog IPC Ping Receiver
    private var isWatchdogReceiverRegistered = false
    private val watchdogPingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WatcherForegroundService.ACTION_WATCHDOG_PING) {
                Log.d(TAG, "Received IPC watchdog PING from WatcherForegroundService; responding with PONG.")
                try {
                    val pongIntent = Intent(WatcherForegroundService.ACTION_WATCHDOG_PONG).apply {
                        setPackage(packageName)
                    }
                    sendBroadcast(pongIntent)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send watchdog pong broadcast", e)
                }

                // If foreground service was unbound or died, re-bind
                if (!isWatcherBound) {
                    WatcherForegroundService.start(this@VideoBlockerAccessibilityService)
                    isWatcherBound = WatcherForegroundService.bind(this@VideoBlockerAccessibilityService, watcherConnection)
                }
            }
        }
    }

    private fun registerWatchdogReceiver() {
        if (!isWatchdogReceiverRegistered) {
            try {
                val filter = IntentFilter(WatcherForegroundService.ACTION_WATCHDOG_PING)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(watchdogPingReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(watchdogPingReceiver, filter)
                }
                isWatchdogReceiverRegistered = true
                Log.d(TAG, "Registered watchdog ping receiver in AccessibilityService.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to register watchdog ping receiver", e)
            }
        }
    }

    private fun unregisterWatchdogReceiver() {
        if (isWatchdogReceiverRegistered) {
            try {
                unregisterReceiver(watchdogPingReceiver)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unregister watchdog ping receiver", e)
            } finally {
                isWatchdogReceiverRegistered = false
            }
        }
    }

    companion object {
        const val PACKAGE_INSTAGRAM = "com.instagram.android"
        const val PACKAGE_INSTAGRAM_LITE = "com.instagram.lite"
        const val PACKAGE_YOUTUBE = "com.google.android.youtube"

        // Dwell Threshold (1.5 seconds)
        const val DWELL_THRESHOLD_MS = ReelSessionTracker.DWELL_THRESHOLD_MS

        @Volatile
        var instance: VideoBlockerAccessibilityService? = null

        fun previewFloatingPill(previewState: Int = 0) { // 0: Normal, 1: Warning, 2: Locked
            val service = instance ?: return
            service.showOrUpdateCounterPill(previewState = previewState)
            service.mainHandler.postDelayed({
                if (!service.isShortFormCurrentlyActive) {
                    service.hideCounterPill()
                }
            }, 4000L)
        }

        fun testEjectAction(): Boolean {
            val service = instance ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_BACK)
        }

        /**
         * Tree traversal helper to extract a unique composite fingerprint hash.
         */
        fun extractReelFingerprint(rootNode: AccessibilityNodeInfo): String? {
            return ReelFingerprintExtractor.extractReelFingerprint(rootNode)
        }
    }

    private fun isSupportedPackage(pkg: String): Boolean {
        return pkg == PACKAGE_INSTAGRAM || pkg == PACKAGE_INSTAGRAM_LITE || pkg == PACKAGE_YOUTUBE
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        quotaManager = QuotaManager(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        reelSessionTracker = ReelSessionTracker(
            serviceScope = serviceScope,
            quotaManager = quotaManager,
            onCountUpdated = { _, _ ->
                mainHandler.post { showOrUpdateCounterPill() }
            },
            onLimitReachedWarning = { platform ->
                mainHandler.post {
                    showOrUpdateCounterPill()
                    try {
                        val limit = quotaManager.dailyLimit
                        android.widget.Toast.makeText(
                            applicationContext,
                            "⚠️ Daily quota reached ($limit/$limit)!\nThis is your last video. Next reel will be blocked.",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to show limit warning toast", e)
                    }
                }
            },
            onEjectionRequired = { platform ->
                mainHandler.post { triggerIntervention(platform) }
            }
        )

        WatcherForegroundService.start(this)
        isWatcherBound = WatcherForegroundService.bind(this, watcherConnection)
        registerWatchdogReceiver()

        // Initialize Dynamic Rule Engine & trigger background remote sync
        dynamicRuleEngine = DynamicRuleEngine.getInstance(applicationContext)
        dynamicRuleEngine.triggerBackgroundSync()

        Log.i(TAG, "VideoBlockerAccessibilityService initialized with ReelSessionTracker, WatcherForegroundService, and DynamicRuleEngine.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        WatcherForegroundService.start(this)
        isWatcherBound = WatcherForegroundService.bind(this, watcherConnection)
        registerWatchdogReceiver()

        // Send immediate initial PONG to notify WatcherForegroundService that accessibility is online
        try {
            val pongIntent = Intent(WatcherForegroundService.ACTION_WATCHDOG_PONG).apply {
                setPackage(packageName)
            }
            sendBroadcast(pongIntent)
        } catch (_: Exception) {}

        Log.i(TAG, "AccessibilityService connected, bound to WatcherForegroundService, and registered watchdog receiver.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // SAFEGUARD 1: Infinite Loop Protection (ignore our own app)
        if (packageName == applicationContext.packageName) {
            return
        }

        // SAFEGUARD 2: Package & Toggle Filtering
        if (!isSupportedPackage(packageName)) {
            cancelPendingSettle()
            cancelDwellTimer()
            hideCounterPill()
            return
        }

        if ((packageName == PACKAGE_INSTAGRAM || packageName == PACKAGE_INSTAGRAM_LITE) && !quotaManager.isInstagramBlockEnabled) {
            cancelPendingSettle()
            cancelDwellTimer()
            hideCounterPill()
            return
        }
        if (packageName == PACKAGE_YOUTUBE && !quotaManager.isYouTubeBlockEnabled) {
            cancelPendingSettle()
            cancelDwellTimer()
            hideCounterPill()
            return
        }

        // SAFEGUARD 3: If keyboard is visible, user is actively texting/typing!
        if (isKeyboardVisible()) {
            cancelPendingSettle()
            cancelDwellTimer()
            hideCounterPill()
            return
        }

        quotaManager.checkAndResetDaily()

        val eventType = event.eventType
        val isScrollOrContentChange = (eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED ||
                eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)

        val now = android.os.SystemClock.uptimeMillis()
        val delta = now - lastEventProcessedTimestamp

        // -----------------------------------------------------------------
        // EVENT COALESCING / TIME-WINDOW THROTTLING:
        // Accessibility events fire rapidly during fast scroll gestures (up to 40–60 events/sec).
        // Throttle immediate execution when events arrive faster than 60ms, and schedule a 75ms debounce
        // so tree traversal is executed once the UI settles after a swipe.
        // -----------------------------------------------------------------
        if (isScrollOrContentChange && delta < THROTTLE_WINDOW_MS) {
            cancelPendingSettle()
            val settleRunnable = Runnable {
                pendingSettleRunnable = null
                lastEventProcessedTimestamp = android.os.SystemClock.uptimeMillis()
                evaluateActiveWindow(packageName, candidateSubtree = null)
            }
            pendingSettleRunnable = settleRunnable
            mainHandler.postDelayed(settleRunnable, DEBOUNCE_SETTLE_MS)
            return
        }

        // Non-throttled event or UI state change: cancel pending settle and process immediately
        cancelPendingSettle()
        lastEventProcessedTimestamp = now

        // -----------------------------------------------------------------
        // TARGETED SUBTREE SCANNING INSTEAD OF ROOT TRAVERSALS:
        // Instead of always calling rootInActiveWindow, inspect event.source first if
        // eventType == TYPE_VIEW_SCROLLED or TYPE_WINDOW_CONTENT_CHANGED to limit traversal
        // to the active swipe container.
        // -----------------------------------------------------------------
        var candidateSubtree: AccessibilityNodeInfo? = null
        var intermediateParent: AccessibilityNodeInfo? = null
        val eventSource = if (isScrollOrContentChange) {
            try { event.source } catch (_: Exception) { null }
        } else {
            null
        }

        val metrics = resources.displayMetrics
        val screenWidth = if (metrics.widthPixels > 0) metrics.widthPixels else 1080
        val screenHeight = if (metrics.heightPixels > 0) metrics.heightPixels else 2400

        try {
            if (eventSource != null) {
                if (isMajorScrollContainer(eventSource, screenWidth, screenHeight)) {
                    candidateSubtree = eventSource
                } else {
                    val parent = try { eventSource.parent } catch (_: Exception) { null }
                    if (parent != null) {
                        if (isMajorScrollContainer(parent, screenWidth, screenHeight)) {
                            candidateSubtree = parent
                            intermediateParent = parent
                        } else {
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                                try { parent.recycle() } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }

            evaluateActiveWindow(packageName, candidateSubtree)
        } finally {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                intermediateParent?.let { try { it.recycle() } catch (_: Exception) {} }
                eventSource?.let { try { it.recycle() } catch (_: Exception) {} }
            }
        }
    }

    /**
     * Evaluates short-form video presence.
     * Prefers targeted subtree scanning on candidateSubtree first; falls back to rootInActiveWindow
     * if candidateSubtree is absent or did not confirm short-form video.
     */
    private fun evaluateActiveWindow(packageName: String, candidateSubtree: AccessibilityNodeInfo?) {
        // 1. Try Targeted Subtree Scanning first if candidateSubtree is available
        if (candidateSubtree != null) {
            val detectedInSubtree = detectShortFormVideo(candidateSubtree, packageName)
            if (detectedInSubtree) {
                handleShortFormDetected(candidateSubtree, packageName)
                return
            }
        }

        // 2. Fallback to Root Window Traversal if subtree scanning didn't detect or was unavailable
        val rootNode = rootInActiveWindow ?: return
        try {
            val isShortFormDetected = detectShortFormVideo(rootNode, packageName)
            if (isShortFormDetected) {
                handleShortFormDetected(rootNode, packageName)
            } else {
                // User is on Home screen/feed, chatting, or viewing stories:
                // Cancel dwell timer and hide the counter pill immediately!
                cancelDwellTimer()
                hideCounterPill()
            }
        } finally {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                try { rootNode.recycle() } catch (_: Exception) {}
            }
        }
    }

    private fun handleShortFormDetected(scanNode: AccessibilityNodeInfo, packageName: String) {
        // User is watching a Reel / Short: Show the live top counter pill!
        showOrUpdateCounterPill()

        // Total Block Mode: 0 allowance allowed, eject immediately
        if (quotaManager.isTotalBlockEnabled) {
            triggerIntervention(packageName)
            return
        }

        // Quota already exhausted and user is NOT on the granted last-video session:
        if (quotaManager.isLimitReached && reelSessionTracker.lastAllowedHash == null) {
            triggerIntervention(packageName)
            return
        }

        // Extract composite unique fingerprint hash
        val fingerprintHash = extractReelFingerprint(scanNode)
        val platform = if (packageName == PACKAGE_YOUTUBE) "YouTube" else "Instagram"

        if (fingerprintHash != null) {
            reelSessionTracker.onReelNodeDetected(fingerprintHash, platform)
        } else {
            // If fingerprint could not be extracted and quota was reached without active last session, eject
            if (quotaManager.isLimitReached && reelSessionTracker.lastAllowedHash == null) {
                triggerIntervention(packageName)
            }
        }
    }

    /**
     * Cancels any active dwell timer and resets active session hash.
     */
    fun cancelDwellTimer() {
        if (::reelSessionTracker.isInitialized) {
            reelSessionTracker.onUserNavigatedAway()
        }
    }

    /**
     * Tree traversal helper matching technical specification.
     */
    fun extractReelFingerprint(rootNode: AccessibilityNodeInfo): String? {
        return ReelFingerprintExtractor.extractReelFingerprint(rootNode)
    }

    /**
     * Intervention Handler:
     * Auto-navigates backward to pop out of the Reels/Shorts container.
     */
    private fun triggerIntervention(targetPackage: String) {
        val currentRoot = rootInActiveWindow ?: return
        try {
            val currentPkg = currentRoot.packageName?.toString() ?: ""

            // SAFEGUARD A: Strict Package Match
            if (!isSupportedPackage(currentPkg) || !isSupportedPackage(targetPackage)) {
                Log.d(TAG, "Intervention aborted: active window package is $currentPkg, expected $targetPackage")
                cancelDwellTimer()
                hideCounterPill()
                return
            }

            // SAFEGUARD B: Never intervene while texting, chatting, typing, or browsing Home feed!
            if (isKeyboardVisible() || isUserActivelyTyping(currentRoot)) {
                Log.d(TAG, "Intervention aborted: user is texting, chatting, or typing.")
                cancelDwellTimer()
                hideCounterPill()
                return
            }

            if (currentPkg == PACKAGE_INSTAGRAM || currentPkg == PACKAGE_INSTAGRAM_LITE) {
                if (isInstagramStory(currentRoot)) {
                    Log.d(TAG, "Intervention aborted: user is viewing an Instagram Story.")
                    cancelDwellTimer()
                    hideCounterPill()
                    return
                }
                if (isInstagramTextingOrChatting(currentRoot)) {
                    Log.d(TAG, "Intervention aborted: user is inside Instagram DMs/chat/inbox.")
                    cancelDwellTimer()
                    hideCounterPill()
                    return
                }
                if (isInstagramHomeFeed(currentRoot)) {
                    Log.d(TAG, "Intervention aborted: user is browsing Instagram Home feed.")
                    cancelDwellTimer()
                    hideCounterPill()
                    return
                }
            }

            // SAFEGUARD C: Re-evaluate full-screen short-form video presence
            if (!detectShortFormVideo(currentRoot, targetPackage)) {
                Log.d(TAG, "Intervention aborted: user is not in short-form player.")
                cancelDwellTimer()
                hideCounterPill()
                return
            }

            val now = System.currentTimeMillis()
            if (now - lastInterventionTimestamp < 1000L) {
                return
            }
            lastInterventionTimestamp = now

            quotaManager.incrementBlockedCount()
            showOrUpdateCounterPill()

            Log.i(TAG, "Limit reached / Total Block: auto-ejecting from Reel/Short via GLOBAL_ACTION_BACK.")
            performGlobalAction(GLOBAL_ACTION_BACK)

            if (Settings.canDrawOverlays(this)) {
                BlockingOverlayService.show(this, targetPackage)
            }
        } finally {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                try { currentRoot.recycle() } catch (_: Exception) {}
            }
        }
    }

    /**
     * Floating Reel Counter Pill at the Top:
     * Displays a minimal, non-intrusive HUD at the top center of the screen
     * indicating [ 🛡️ X / 50 (Y left) ] while watching short-form videos.
     * Supports previewState: 0 (Normal), 1 (Warning), 2 (Locked), or -1 (Live).
     */
    fun showOrUpdateCounterPill(previewState: Int = -1) {
        mainHandler.post {
            val total = when (previewState) {
                0 -> 12
                1 -> 50
                2 -> 50
                else -> quotaManager.totalCount
            }
            val limit = quotaManager.dailyLimit
            val isTotalBlock = quotaManager.isTotalBlockEnabled
            val isWatchingLastAllowed = ::reelSessionTracker.isInitialized && reelSessionTracker.lastAllowedHash != null
            val isLimitReached = quotaManager.isLimitReached

            if (isPillShowing && pillView != null) {
                updatePillContent(pillView!!, total, limit, isTotalBlock, isLimitReached, isWatchingLastAllowed, previewState)
                return@post
            }

            try {
                val inflater = LayoutInflater.from(this)
                val view = inflater.inflate(R.layout.layout_floating_reel_counter, null)
                updatePillContent(view, total, limit, isTotalBlock, isLimitReached, isWatchingLastAllowed, previewState)

                val density = resources.displayMetrics.density
                val topMarginPx = (38 * density).toInt()

                val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                else
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT

                val layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = topMarginPx
                }

                try {
                    windowManager.addView(view, layoutParams)
                } catch (e: Exception) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        windowManager.addView(view, layoutParams)
                    } else {
                        throw e
                    }
                }

                pillView = view
                isPillShowing = true
                Log.d(TAG, "Top reel counter pill attached.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach counter pill", e)
            }
        }
    }

    private fun updatePillContent(
        view: View,
        total: Int,
        limit: Int,
        isTotalBlock: Boolean,
        isLimitReached: Boolean,
        isWatchingLastAllowed: Boolean,
        previewState: Int
    ) {
        val pillRoot = view.findViewById<LinearLayout>(R.id.pill_root)
        val tvIcon = view.findViewById<TextView>(R.id.tv_pill_icon)
        val tvCounter = view.findViewById<TextView>(R.id.tv_pill_counter)

        val remaining = if (isTotalBlock) 0 else maxOf(0, limit - total)

        when {
            // Case 1: Total Block Mode (0 allowance, locked)
            isTotalBlock -> {
                pillRoot.setBackgroundResource(R.drawable.bg_floating_pill_locked)
                tvIcon.text = "🔒"
                tvCounter.text = "TOTAL BLOCK • LOCKED"
                tvCounter.setTextColor(Color.parseColor("#FFFF4A36"))
            }

            // Case 2: Locked / Exceeded limit / Blocked on next video
            previewState == 2 || (isLimitReached && !isWatchingLastAllowed) || (limit in 1..<total) -> {
                pillRoot.setBackgroundResource(R.drawable.bg_floating_pill_locked)
                tvIcon.text = "🔒"
                tvCounter.text = "$total / $limit • LOCKED"
                tvCounter.setTextColor(Color.parseColor("#FFFF4A36"))
            }

            // Case 3: Exactly Hit 50 (Warning on the 50th video, allowed to finish)
            previewState == 1 || (total == limit && isWatchingLastAllowed) -> {
                pillRoot.setBackgroundResource(R.drawable.bg_floating_pill_warning)
                tvIcon.text = "⚠️"
                tvCounter.text = "$total / $limit • LAST VIDEO"
                tvCounter.setTextColor(Color.parseColor("#FFFFB020"))
            }

            // Case 4: Approaching limit warning zone (e.g. 40 to 49)
            total >= 40 && limit > 0 -> {
                pillRoot.setBackgroundResource(R.drawable.bg_floating_pill_warning)
                tvIcon.text = "⚠️"
                tvCounter.text = "$total / $limit ($remaining left)"
                tvCounter.setTextColor(Color.parseColor("#FFFFB020"))
            }

            // Case 5: Normal allowance active (0 to 39)
            else -> {
                pillRoot.setBackgroundResource(R.drawable.bg_floating_pill)
                tvIcon.text = "🛡️"
                tvCounter.text = "$total / $limit ($remaining left)"
                tvCounter.setTextColor(Color.WHITE)
            }
        }
    }

    fun hideCounterPill() {
        mainHandler.post {
            if (isPillShowing && pillView != null) {
                try {
                    windowManager.removeView(pillView)
                } catch (e: Exception) {
                    Log.e(TAG, "Error removing counter pill", e)
                } finally {
                    pillView = null
                    isPillShowing = false
                    Log.d(TAG, "Top reel counter pill hidden.")
                }
            }
        }
    }

    /**
     * Checks if the on-screen software keyboard (IME) is currently open/visible.
     */
    private fun isKeyboardVisible(): Boolean {
        return try {
            windows?.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the user is actively typing / texting using a keyboard or input field.
     * Only returns true when the software keyboard is visible OR an editable input field
     * actively has focus. Merely having an unfocused EditText in the background does NOT count.
     */
    private fun isUserActivelyTyping(root: AccessibilityNodeInfo): Boolean {
        // 1. If software keyboard is showing, user is actively typing!
        if (isKeyboardVisible()) return true

        // 2. Check for focused input field (system-level)
        try {
            val focusedNode = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedNode != null) {
                val isInput = focusedNode.isEditable ||
                        (focusedNode.className?.contains("EditText", ignoreCase = true) == true)
                focusedNode.recycle()
                if (isInput) return true
            }
        } catch (_: Exception) {}

        // 3. Search for an actively FOCUSED editable node in the tree using flatNodeQueue
        flatNodeQueue.clear()
        flatNodeQueue.add(root)
        var visited = 0
        try {
            while (flatNodeQueue.isNotEmpty() && visited < 60) {
                val node = flatNodeQueue.removeFirst()
                visited++

                if (node.isFocused && (node.isEditable || (node.className?.contains("EditText", ignoreCase = true) == true))) {
                    return true
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { flatNodeQueue.add(it) }
                }
            }
        } finally {
            flatNodeQueue.clear()
        }
        return false
    }

    /**
     * Checks if a node occupies full-screen dimensions for YouTube Shorts player containers.
     * Uses sharedRect buffer on service thread to eliminate Rect heap allocation.
     */
    private fun isFullScreenNode(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        node.getBoundsInScreen(sharedRect)
        if (sharedRect.isEmpty) return false

        val metrics = resources.displayMetrics
        val screenHeight = if (metrics.heightPixels > 0) metrics.heightPixels else 2000
        val screenWidth = if (metrics.widthPixels > 0) metrics.widthPixels else 1000

        val minHeight = (screenHeight * 0.60f).toInt()
        val minWidth = (screenWidth * 0.60f).toInt()
        return sharedRect.height() >= minHeight && sharedRect.width() >= minWidth
    }

    /**
     * Checks if a node represents a true full-screen vertical immersive Reel player.
     * Requires at least 65% of screen height and 70% of screen width.
     * Uses sharedRect buffer on service thread to eliminate Rect heap allocation.
     */
    private fun isDedicatedFullscreenReel(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        node.getBoundsInScreen(sharedRect)
        if (sharedRect.isEmpty) return false

        val metrics = resources.displayMetrics
        val screenHeight = if (metrics.heightPixels > 0) metrics.heightPixels else 2000
        val screenWidth = if (metrics.widthPixels > 0) metrics.widthPixels else 1000

        val minHeight = (screenHeight * 0.65f).toInt()
        val minWidth = (screenWidth * 0.70f).toInt()
        return sharedRect.height() >= minHeight && sharedRect.width() >= minWidth
    }

    /**
     * Checks if the dedicated Reels bottom tab is currently selected.
     */
    private fun isInstagramReelsTabSelected(root: AccessibilityNodeInfo): Boolean {
        val clipsTab = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_tab")
        if (!clipsTab.isNullOrEmpty() && clipsTab.any { it.isSelected }) return true

        val tabNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabNodes.isNullOrEmpty()) {
            val tabBar = tabNodes.first()
            for (i in 0 until tabBar.childCount) {
                val child = tabBar.getChild(i) ?: continue
                if (child.isSelected) {
                    val desc = (child.contentDescription?.toString() ?: "").lowercase()
                    val resId = (child.viewIdResourceName ?: "").lowercase()
                    if (desc.contains("reel") || desc.contains("clip") || resId.contains("clips_tab")) {
                        return true
                    }
                }
            }
        }

        flatNodeQueue.clear()
        flatNodeQueue.add(root)
        var count = 0
        try {
            while (flatNodeQueue.isNotEmpty() && count < 40) {
                val node = flatNodeQueue.removeFirst()
                count++
                if (node.isSelected) {
                    val desc = (node.contentDescription?.toString() ?: "").lowercase()
                    val text = (node.text?.toString() ?: "").lowercase()
                    val resId = (node.viewIdResourceName ?: "").lowercase()
                    if (desc.contains("reel") || desc.contains("clip") || text.contains("reel") || text.contains("clip") || resId.contains("clips_tab")) {
                        return true
                    }
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { flatNodeQueue.add(it) }
                }
            }
        } finally {
            flatNodeQueue.clear()
        }

        return false
    }

    /**
     * Checks if the bottom Home / Feed navigation tab is currently selected.
     * Works across standard Instagram and Instagram Lite.
     */
    private fun isInstagramHomeTabSelected(root: AccessibilityNodeInfo): Boolean {
        val feedTab = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/feed_tab")
        if (!feedTab.isNullOrEmpty() && feedTab.any { it.isSelected }) return true

        val tabNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        if (!tabNodes.isNullOrEmpty()) {
            val tabBar = tabNodes.first()
            for (i in 0 until tabBar.childCount) {
                val child = tabBar.getChild(i) ?: continue
                if (child.isSelected) {
                    val desc = (child.contentDescription?.toString() ?: "").lowercase()
                    val resId = (child.viewIdResourceName ?: "").lowercase()
                    if (desc.contains("home") || desc.contains("feed") || resId.contains("feed_tab")) {
                        return true
                    }
                }
            }
        }

        flatNodeQueue.clear()
        flatNodeQueue.add(root)
        var count = 0
        try {
            while (flatNodeQueue.isNotEmpty() && count < 40) {
                val node = flatNodeQueue.removeFirst()
                count++
                if (node.isSelected) {
                    val desc = (node.contentDescription?.toString() ?: "").lowercase()
                    val text = (node.text?.toString() ?: "").lowercase()
                    val resId = (node.viewIdResourceName ?: "").lowercase()
                    if (desc == "home" || desc == "feed" || text == "home" || text == "feed" || resId.contains("feed_tab")) {
                        return true
                    }
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { flatNodeQueue.add(it) }
                }
            }
        } finally {
            flatNodeQueue.clear()
        }

        return false
    }

    /**
     * Complete immunity shield for Instagram Stories.
     * When users watch Stories (including Reels shared/embedded into a Story),
     * this ensures the app NEVER intervenes and NEVER ejects the user.
     */
    private fun isInstagramStory(root: AccessibilityNodeInfo): Boolean {
        // Direct self-check for Story container
        val rootResId = (root.viewIdResourceName ?: "").lowercase()
        if (rootResId.contains("reel_viewer_container") || rootResId.contains("story_progress")) {
            return true
        }

        // 1. Check known Story Progress Bar and Container View IDs
        val storyViewIds = listOf(
            "com.instagram.android:id/story_progress",
            "com.instagram.android:id/stories_progress_bar",
            "com.instagram.android:id/segment_progress_bar",
            "com.instagram.android:id/reel_viewer_progress_bar",
            "com.instagram.android:id/reel_viewer_header",
            "com.instagram.android:id/reel_viewer_title",
            "com.instagram.android:id/reel_viewer_subtitle",
            "com.instagram.android:id/reel_viewer_timestamp",
            "com.instagram.android:id/reel_viewer_view_pager",
            "com.instagram.android:id/reel_viewer_root",
            "com.instagram.android:id/story_item",
            "com.instagram.android:id/story_viewer_reply",
            "com.instagram.android:id/story_reply_composer",
            "com.instagram.android:id/direct_story_reply",
            "com.instagram.android:id/toolbar_container"
        )

        for (id in storyViewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty() && nodes.any { it.isVisibleToUser }) {
                return true
            }
        }

        // 2. Check for reel_viewer_container (Story viewer container in Instagram)
        val reelViewerContainer = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/reel_viewer_container")
        if (!reelViewerContainer.isNullOrEmpty() && reelViewerContainer.any { it.isVisibleToUser }) {
            return true
        }

        // 3. Tree-walk check for Story progress bar, reply prompts, or story header using flatNodeQueue
        flatNodeQueue.clear()
        flatNodeQueue.add(root)
        var visited = 0
        try {
            while (flatNodeQueue.isNotEmpty() && visited < 70) {
                val node = flatNodeQueue.removeFirst()
                visited++

                val resId = (node.viewIdResourceName ?: "").lowercase()
                val desc = (node.contentDescription?.toString() ?: "").trim().lowercase()
                val text = (node.text?.toString() ?: "").trim().lowercase()

                if (resId.contains("story_progress") || resId.contains("stories_progress") ||
                    resId.contains("segment_progress") || resId.contains("reel_viewer_progress")) {
                    return true
                }

                if (resId.contains("reel_viewer_header") || resId.contains("story_reply") ||
                    resId.contains("story_composer")) {
                    return true
                }

                if (desc.contains("story by") || text.contains("story by") ||
                    (desc.contains("stories") && desc.contains("progress"))) {
                    return true
                }

                // Story reply field placeholder at bottom
                if (text.startsWith("reply to ") || desc.startsWith("reply to ") ||
                    (text == "send message" && resId.contains("story")) ||
                    (desc == "send message" && resId.contains("story"))) {
                    return true
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { flatNodeQueue.add(it) }
                }
            }
        } finally {
            flatNodeQueue.clear()
        }

        return false
    }

    /**
     * Checks if a dedicated fullscreen Reel viewer is actively playing short-form video.
     * Catches Reels opened by tapping a Reel from Home feed, DMs, Explore, Profile, or the Reels tab.
     */
    private fun hasDedicatedFullscreenReel(root: AccessibilityNodeInfo): Boolean {
        // If user is inside an Instagram Story, NEVER classify as dedicated Reels!
        if (isInstagramStory(root)) {
            return false
        }

        // Direct self-check for targeted subtree scanning
        val rootResId = (root.viewIdResourceName ?: "").lowercase()
        if (rootResId.contains("clips_viewer_view_pager") ||
            rootResId.contains("clips_viewer_container") ||
            rootResId.contains("root_clips_layout")
        ) {
            if (isDedicatedFullscreenReel(root)) return true
        }

        val clipsPager = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_viewer_view_pager")
        if (!clipsPager.isNullOrEmpty() && clipsPager.any { isDedicatedFullscreenReel(it) }) {
            return true
        }

        val viewerContainer = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_viewer_container")
        if (!viewerContainer.isNullOrEmpty() && viewerContainer.any { isDedicatedFullscreenReel(it) }) {
            return true
        }

        val rootClips = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/root_clips_layout")
        if (!rootClips.isNullOrEmpty() && rootClips.any { isDedicatedFullscreenReel(it) }) {
            return true
        }

        if (isInstagramReelsTabSelected(root)) {
            return true
        }

        return false
    }

    /**
     * Complete immunity shield for Instagram Direct Messages (DMs), Chats, Inbox,
     * Message Threads, Primary/General filter tabs, Commenting, and Story/Note Replying.
     * If the user taps a shared Reel inside a chat, it opens a dedicated fullscreen Reel viewer,
     * in which case hasDedicatedFullscreenReel() will be true and immunity is released so the Reel is blocked.
     */
    private fun isInstagramTextingOrChatting(root: AccessibilityNodeInfo): Boolean {
        // 1. If actively typing with keyboard or focused EditText, user is texting!
        if (isKeyboardVisible() || isUserActivelyTyping(root)) {
            return true
        }

        // 2. If a dedicated fullscreen Reel viewer is open on top, user is watching a Reel (NOT chatting)!
        if (hasDedicatedFullscreenReel(root)) {
            return false
        }

        // Direct self-check for DM containers
        val rootResId = (root.viewIdResourceName ?: "").lowercase()
        if (rootResId.contains("direct_message") || rootResId.contains("direct_thread") || rootResId.contains("message_composer")) {
            return true
        }

        // 3. Direct Messages & Chat Known View IDs (standard Instagram)
        val dmViewIds = listOf(
            "com.instagram.android:id/direct_message_list",
            "com.instagram.android:id/row_thread_composer",
            "com.instagram.android:id/row_thread_composer_edittext",
            "com.instagram.android:id/direct_thread_header",
            "com.instagram.android:id/direct_thread_composer",
            "com.instagram.android:id/direct_text_message_composer",
            "com.instagram.android:id/message_composer",
            "com.instagram.android:id/composer_edittext",
            "com.instagram.android:id/composer_container",
            "com.instagram.android:id/direct_composer_edit_text",
            "com.instagram.android:id/direct_inbox",
            "com.instagram.android:id/inbox_recycler_view",
            "com.instagram.android:id/direct_inbox_filter_bar",
            "com.instagram.android:id/direct_search_box",
            "com.instagram.android:id/thread_list",
            "com.instagram.android:id/direct_thread_view",
            "com.instagram.android:id/direct_conversation",
            "com.instagram.android:id/direct_thread_root",
            "com.instagram.android:id/direct_action_bar",
            "com.instagram.android:id/direct_messages_action_bar",
            "com.instagram.android:id/thread_title",
            "com.instagram.android:id/message_content_container",
            "com.instagram.android:id/direct_recycler_view",
            "com.instagram.android:id/message_list_recycler_view",
            "com.instagram.android:id/chat_container"
        )

        for (id in dmViewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty() && nodes.any { it.isVisibleToUser }) {
                return true
            }
        }

        // 4. Content description, text, and keyword matching across the tree using flatNodeQueue
        flatNodeQueue.clear()
        flatNodeQueue.add(root)
        var visited = 0
        try {
            while (flatNodeQueue.isNotEmpty() && visited < 70) {
                val node = flatNodeQueue.removeFirst()
                visited++

                val text = (node.text?.toString() ?: "").trim().lowercase()
                val desc = (node.contentDescription?.toString() ?: "").trim().lowercase()
                val resId = (node.viewIdResourceName ?: "").lowercase()

                // DM filter tabs (General, Primary, Requests, Channels)
                if (text == "general" || desc == "general" || text == "primary" || desc == "primary" ||
                    text == "requests" || desc == "requests" || text == "channels" || desc == "channels") {
                    return true
                }

                // DMs header or message composer
                if (text == "messages" || desc == "messages" || text == "chats" || desc == "chats" ||
                    text == "direct" || desc == "direct") {
                    return true
                }

                if (text.startsWith("message...") || desc.startsWith("message...") ||
                    text.startsWith("send message") || desc.startsWith("send message") ||
                    text.startsWith("type a message") || desc.startsWith("type a message") ||
                    text.startsWith("write a message") || desc.startsWith("write a message")) {
                    return true
                }

                if (resId.contains("direct") || resId.contains("inbox") || resId.contains("composer")) {
                    return true
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { flatNodeQueue.add(it) }
                }
            }
        } finally {
            flatNodeQueue.clear()
        }

        return false
    }

    /**
     * Checks if the user is currently on the Instagram Home feed (scrolling/viewing posts).
     * In-feed video posts in the feed should NOT trigger Reel blocking.
     */
    private fun isInstagramHomeFeed(root: AccessibilityNodeInfo): Boolean {
        // 1. If dedicated fullscreen Reel viewer is active, user is watching a Reel (NOT feed)
        if (hasDedicatedFullscreenReel(root)) {
            return false
        }

        // Direct self-check for Home feed container
        val rootResId = (root.viewIdResourceName ?: "").lowercase()
        if (rootResId.contains("feed_recycler_view") || rootResId.contains("main_feed_container")) {
            return true
        }

        // 2. Check for Home feed list containers in standard Instagram
        val feedIds = listOf(
            "com.instagram.android:id/feed_recycler_view",
            "com.instagram.android:id/sticky_header_list",
            "com.instagram.android:id/main_feed_container",
            "com.instagram.android:id/refreshable_container"
        )
        for (id in feedIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty() && nodes.any { it.isVisibleToUser }) {
                return true
            }
        }

        // 3. Check if the bottom Home / Feed tab is selected (covers both standard and Lite)
        if (isInstagramHomeTabSelected(root)) {
            return true
        }

        return false
    }

    /**
     * Checks for a dedicated fullscreen Reel viewer when not on Home feed and not in DMs.
     * (E.g. tapped a Reel in Instagram Lite outside feed/DMs).
     */
    private fun isGenericFullscreenReel(root: AccessibilityNodeInfo): Boolean {
        flatNodeQueue.clear()
        flatNodeQueue.add(root)
        var count = 0
        var hasFullscreenVideo = false
        var hasReelIndicator = false

        try {
            while (flatNodeQueue.isNotEmpty() && count < 60) {
                val node = flatNodeQueue.removeFirst()
                count++

                val resId = (node.viewIdResourceName ?: "").lowercase()
                val desc = (node.contentDescription?.toString() ?: "").lowercase()

                if (isDedicatedFullscreenReel(node)) {
                    hasFullscreenVideo = true
                    if (desc.contains("reel") || desc.contains("clip") || resId.contains("clips")) {
                        hasReelIndicator = true
                    }
                }

                if (desc.contains("reel by") || desc.contains("remix") || desc.contains("use audio") ||
                    resId.contains("clips_action") || resId.contains("clips_viewer")) {
                    hasReelIndicator = true
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { flatNodeQueue.add(it) }
                }
            }
        } finally {
            flatNodeQueue.clear()
        }

        return hasFullscreenVideo && hasReelIndicator
    }

    private fun isYouTubeTextingOrComments(root: AccessibilityNodeInfo): Boolean {
        if (isUserActivelyTyping(root)) return true

        val rootResId = (root.viewIdResourceName ?: "").lowercase()
        if (rootResId.contains("watch_comments") || rootResId.contains("comment_sheet") || rootResId.contains("comment_composer")) {
            return true
        }

        val ytChatIds = listOf(
            "com.google.android.youtube:id/watch_comments",
            "com.google.android.youtube:id/comment_sheet",
            "com.google.android.youtube:id/live_chat",
            "com.google.android.youtube:id/comment_composer",
            "com.google.android.youtube:id/comment_input",
            "com.google.android.youtube:id/comment_recycler",
            "com.google.android.youtube:id/search_edit_text"
        )
        for (id in ytChatIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty() && nodes.any { it.isVisibleToUser }) return true
        }

        return false
    }

    /**
     * Heuristic Short-Form Video Detector:
     * Combines Resource ID matching, Content Description matching, and Hierarchy/Dimension structure.
     * Detects Reels opened from Reels Tab, Home Feed, DMs, or links, while safeguarding Chats, Stories, and Feeds.
     */
    private fun detectShortFormVideo(root: AccessibilityNodeInfo, packageName: String): Boolean {
        val rootPkg = root.packageName?.toString() ?: ""
        if (rootPkg.isNotEmpty() && !isSupportedPackage(rootPkg)) {
            return false
        }
        return when (packageName) {
            PACKAGE_INSTAGRAM, PACKAGE_INSTAGRAM_LITE -> detectInstagramReels(root)
            PACKAGE_YOUTUBE -> detectYouTubeShorts(root)
            else -> false
        }
    }

    private fun detectInstagramReels(root: AccessibilityNodeInfo): Boolean {
        // --- SAFEGUARD 1: Typing & Keyboard Immunity ---
        if (isKeyboardVisible() || isUserActivelyTyping(root)) {
            return false
        }

        // --- SAFEGUARD 2: Stories Viewer Immunity ---
        // If user is watching any Story (including Reels shared/embedded inside a Story), NEVER BLOCK!
        if (isInstagramStory(root)) {
            return false
        }

        // --- SAFEGUARD 3: Direct Messages & Chat Immunity (Text / Inbox / General Tab) ---
        // Complete protection when in DMs/Inbox/General tab unless a fullscreen Reel was explicitly opened on top!
        if (isInstagramTextingOrChatting(root)) {
            return false
        }

        // --- SIGNATURE 1: Dedicated Fullscreen Clips ViewPager or Root Clips Layout ---
        // Catches Reels opened by clicking on a Reel from Home feed, DMs, Explore, Profile, or links!
        if (hasDedicatedFullscreenReel(root)) {
            return true
        }

        // --- SAFEGUARD 4: Home Feed Immunity ---
        // If the user is on the Home feed scrolling/viewing regular posts, DO NOT BLOCK!
        // Allows scrolling the feed freely without getting kicked out.
        if (isInstagramHomeFeed(root)) {
            return false
        }

        // --- SIGNATURE 2: Tiered Heuristic Detection (detectReelContainer) & Generic Fullscreen Reel Player ---
        return detectReelContainer(root) || isGenericFullscreenReel(root)
    }

    private fun detectYouTubeShorts(root: AccessibilityNodeInfo): Boolean {
        // Typing / Comments / Search Safeguard
        if (isKeyboardVisible() || isUserActivelyTyping(root) || isYouTubeTextingOrComments(root)) {
            return false
        }

        // Direct self-check for YouTube Shorts container
        val rootResId = (root.viewIdResourceName ?: "").lowercase()
        if (rootResId.contains("reel_recycler") || rootResId.contains("reel_player_page_content")) {
            if (isFullScreenNode(root)) return true
        }

        // Normal long-form video player safeguard
        if (!root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/watch_while_layout").isNullOrEmpty()) {
            val reelRecycler = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_recycler")
            val reelPlayer = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_player_page_content")
            val hasReelRecycler = !reelRecycler.isNullOrEmpty() && reelRecycler.any { isFullScreenNode(it) }
            val hasReelPlayer = !reelPlayer.isNullOrEmpty() && reelPlayer.any { isFullScreenNode(it) }
            if (!hasReelRecycler && !hasReelPlayer) {
                return false
            }
        }

        // SIGNATURE 1: Dedicated Shorts player container (must be visible and full-screen)
        // Works whether launched from Home feed, Shorts tab, or link!
        val reelRecycler = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_recycler")
        if (!reelRecycler.isNullOrEmpty() && reelRecycler.any { isFullScreenNode(it) }) return true

        val reelPlayer = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/reel_player_page_content")
        if (!reelPlayer.isNullOrEmpty() && reelPlayer.any { isFullScreenNode(it) }) return true

        // SIGNATURE 2: Shorts Tab selected in bottom bar using flatNodeQueue
        flatNodeQueue.clear()
        flatNodeQueue.add(root)
        var visited = 0
        try {
            while (flatNodeQueue.isNotEmpty() && visited < 40) {
                val node = flatNodeQueue.removeFirst()
                visited++

                val desc = (node.contentDescription?.toString() ?: "").lowercase()
                val resId = (node.viewIdResourceName ?: "").lowercase()

                if (node.isSelected) {
                    if (desc.contains("short") || resId.contains("shorts_tab")) return true
                    if (desc.contains("home") || resId.contains("home_tab")) return false
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { flatNodeQueue.add(it) }
                }
            }
        } finally {
            flatNodeQueue.clear()
        }

        // SIGNATURE 3: Tiered Heuristic Detection (detectReelContainer)
        if (detectReelContainer(root)) return true

        return false
    }

    /**
     * Tiered Evaluation Pipeline powered by Dynamic Heuristic Rule Engine:
     * - Tier 1 — Fuzzy Resource ID Scanning: Dynamic regex & wildcard scanning from active remote/cached rules.
     * - Tier 2 — Label & Content Description Fallback: Dynamic keyword matching from active rules.
     * - Tier 3 — Structural & Geometry Layout Signature: Full-screen vertical swipe containers (ViewPager2/RecyclerView) >= 90% bounds with right-edge vertical action rail.
     * - Tier 4 — Aspect Ratio & Video Surface Detection: Detects SurfaceView, TextureView, or PlayerView occupying vertical 9:16 aspect ratios (1.77 ± 0.1) to confirm active video playback rather than static graphic carousels.
     * Clean Memory Management: Zero-allocation service thread node depth pooling, explicit .recycle() on API < 30.
     */
    fun detectReelContainer(root: AccessibilityNodeInfo): Boolean {
        val metrics = resources.displayMetrics
        val screenWidth = if (metrics.widthPixels > 0) metrics.widthPixels else 1080
        val screenHeight = if (metrics.heightPixels > 0) metrics.heightPixels else 2400

        val rootPkg = root.packageName?.toString() ?: ""
        val isInstagram = rootPkg.contains("instagram")
        val engine = if (::dynamicRuleEngine.isInitialized) dynamicRuleEngine else DynamicRuleEngine.getInstance(applicationContext)
        val activeRules = engine.activeRules
        val appRule = if (isInstagram) activeRules.instagramRules else activeRules.youtubeRules
        val globalRule = activeRules.globalRules

        resetDepthPool()
        depthQueue.add(obtainDepthEntry(root, 0))
        var visited = 0
        val maxVisited = globalRule.maxNodesInspected

        var hasTier1FuzzyId = false
        var hasTier2Label = false
        var hasTier3Layout = false
        var hasActive9by16VideoSurface = false

        try {
            while (depthQueue.isNotEmpty() && visited < maxVisited) {
                val entry = depthQueue.removeFirst()
                val node = entry.node ?: continue
                val depth = entry.depth
                visited++

                val resId = (node.viewIdResourceName ?: "").lowercase()
                val text = (node.text?.toString() ?: "").lowercase()
                val desc = (node.contentDescription?.toString() ?: "").lowercase()
                val className = (node.className?.toString() ?: "")

                // Aspect Ratio & Video Surface Detection:
                // Check node class names for SurfaceView, TextureView, or PlayerView occupying vertical 9:16 aspect ratios (1.77 ± 0.1)
                // Uses sharedRect buffer to eliminate Rect heap allocation.
                if (!hasActive9by16VideoSurface) {
                    if (engine.isVertical9by16VideoSurface(node, screenWidth, screenHeight, sharedRect)) {
                        hasActive9by16VideoSurface = true
                    }
                }

                // Tier 1 — Fuzzy Resource ID Scanning (driven dynamically)
                if (!hasTier1FuzzyId) {
                    if (appRule.fuzzyResourceIds.any { kw -> resId.contains(kw) }) {
                        hasTier1FuzzyId = true
                    }
                }

                // Tier 2 — Accessibility Label & Content Description Fallback (driven dynamically)
                if (!hasTier2Label) {
                    if (appRule.labelKeywords.any { kw ->
                            (text.isNotEmpty() && text.contains(kw)) || (desc.isNotEmpty() && desc.contains(kw))
                        }) {
                        hasTier2Label = true
                    }
                }

                // Tier 3 — Structural & Geometry Layout Signature:
                // Full-screen vertical swipe layout (ViewPager2 or RecyclerView) >= 90% screen height & width
                if (!hasTier3Layout) {
                    val isSwipeContainer = appRule.containerClasses.any { cls -> className.contains(cls) } ||
                            resId.contains("view_pager") ||
                            resId.contains("clips_viewer")

                    if (isSwipeContainer && node.isVisibleToUser) {
                        node.getBoundsInScreen(sharedRect)
                        val coversRequired = sharedRect.width() >= (screenWidth * appRule.minCoverageFraction) &&
                                sharedRect.height() >= (screenHeight * appRule.minCoverageFraction)

                        if (coversRequired && hasVerticalActionRailChildren(node, screenWidth, appRule)) {
                            hasTier3Layout = true
                        }
                    }
                }

                // Early exit conditions:
                // 1. Tier 3 structural swipe container confirmed
                // 2. Both Tier 1 fuzzy ID and Tier 2 label confirmed
                // 3. Active 9:16 video surface confirmed AND at least one short-form indicator (Tier 1 or Tier 2)
                if (hasTier3Layout || (hasTier1FuzzyId && hasTier2Label) ||
                    (hasActive9by16VideoSurface && (hasTier1FuzzyId || hasTier2Label))
                ) {
                    return true
                }

                // Depth-limited search (up to maxTreeDepth levels deep, default 7)
                if (depth < globalRule.maxTreeDepth) {
                    for (i in 0 until node.childCount) {
                        val child = node.getChild(i)
                        if (child != null) {
                            depthQueue.add(obtainDepthEntry(child, depth + 1))
                            pooledNodesToRecycle.add(child)
                        }
                    }
                }
            }
        } finally {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                for (i in 0 until pooledNodesToRecycle.size) {
                    try { pooledNodesToRecycle[i].recycle() } catch (_: Exception) {}
                }
            }
            resetDepthPool()
        }

        return hasTier3Layout || (hasTier1FuzzyId && hasTier2Label) ||
                (hasActive9by16VideoSurface && (hasTier1FuzzyId || hasTier2Label))
    }

    private fun hasVerticalActionRailChildren(
        container: AccessibilityNodeInfo,
        screenWidth: Int,
        appRule: AppHeuristicRule? = null
    ): Boolean {
        val thresholdFraction = appRule?.actionRailRightThresholdFraction ?: 0.70f
        val rightEdgeThreshold = (screenWidth * thresholdFraction).toInt()
        val requiredCount = appRule?.minActionButtons ?: 2
        val actionKeywords = appRule?.actionRailKeywords ?: listOf("action", "like", "comment", "share")
        var actionButtonCount = 0

        flatNodeQueue.clear()
        flatNodeQueue.add(container)
        var visited = 0
        try {
            while (flatNodeQueue.isNotEmpty() && visited < 40) {
                val node = flatNodeQueue.removeFirst()
                visited++

                node.getBoundsInScreen(sharedChildRect)

                // Child aligned near the right screen edge
                if (sharedChildRect.left >= rightEdgeThreshold || sharedChildRect.right >= rightEdgeThreshold) {
                    val isButton = node.isClickable ||
                            (node.className?.contains("Button", ignoreCase = true) == true) ||
                            (node.className?.contains("ImageView", ignoreCase = true) == true)
                    val resId = (node.viewIdResourceName ?: "").lowercase()
                    val desc = (node.contentDescription?.toString() ?: "").lowercase()

                    val matchesActionKw = actionKeywords.any { kw -> resId.contains(kw) || desc.contains(kw) }

                    if (isButton || matchesActionKw) {
                        actionButtonCount++
                        if (actionButtonCount >= requiredCount) {
                            return true
                        }
                    }
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { flatNodeQueue.add(it) }
                }
            }
        } finally {
            flatNodeQueue.clear()
        }
        return actionButtonCount >= requiredCount
    }

    override fun onInterrupt() {
        Log.w(TAG, "VideoBlockerAccessibilityService interrupted.")
        cancelPendingSettle()
        cancelDwellTimer()
        hideCounterPill()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelPendingSettle()
        unregisterWatchdogReceiver()
        if (isWatcherBound) {
            WatcherForegroundService.unbind(this, watcherConnection)
            isWatcherBound = false
        }
        if (::reelSessionTracker.isInitialized) {
            reelSessionTracker.clearSession()
        }
        instance = null
        serviceScope.cancel()
        cancelDwellTimer()
        hideCounterPill()
    }
}
