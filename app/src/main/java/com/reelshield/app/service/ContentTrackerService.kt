package com.reelshield.app.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import com.reelshield.app.data.QuotaManager
import com.reelshield.app.data.rules.DynamicRuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.math.abs

open class ContentTrackerService : AccessibilityService() {

    protected val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val mainHandler = Handler(Looper.getMainLooper())

    protected val quotaManager by lazy { QuotaManager(this) }
    protected val floatingCounterManager by lazy { FloatingCounterManager(this) }
    protected val dynamicRuleEngine by lazy { DynamicRuleEngine.getInstance(applicationContext) }

    protected val reelSessionTracker by lazy {
        ReelSessionTracker(
            serviceScope = serviceScope,
            quotaManager = quotaManager,
            onCountUpdated = { newCount, platform ->
                Log.i(TAG, "ReelSessionTracker count updated: $newCount on $platform")
                mainHandler.post {
                    if (quotaManager.isFloatingCounterEnabled && !quotaManager.isLimitReached && (quotaManager.dailyLimit == 0 || newCount < quotaManager.dailyLimit)) {
                        floatingCounterManager.showOrUpdate(newCount, quotaManager.dailyLimit)
                        startLivenessWatchdog()
                    } else {
                        floatingCounterManager.hide()
                    }
                }
            },
            onLimitReachedWarning = { platform ->
                Log.w(TAG, "ReelSessionTracker limit reached warning on $platform")
                mainHandler.post {
                    floatingCounterManager.hide()
                }
            },
            onEjectionRequired = { platform ->
                Log.w(TAG, "ReelSessionTracker ejection required on $platform")
                val targetPkg = when {
                    platform.contains("YouTube", ignoreCase = true) -> PACKAGE_YOUTUBE
                    else -> PACKAGE_INSTAGRAM
                }
                ejectFromVerticalFeed(targetPkg, null, platform)
            }
        )
    }

    // Rate-limiters for redirects and back presses
    private var lastBackEjectionTime = 0L
    private var lastHomeRedirectTime = 0L

    // Anti-flutter debouncer for navigating away (prevents scroll transitions from prematurely cancelling dwell)
    private var pendingNavAwayRunnable: Runnable? = null

    private fun cancelPendingNavAway() {
        pendingNavAwayRunnable?.let {
            mainHandler.removeCallbacks(it)
            pendingNavAwayRunnable = null
        }
    }

    private fun scheduleNavAway(delayMs: Long = 450L) {
        if (pendingNavAwayRunnable != null) return
        val runnable = Runnable {
            pendingNavAwayRunnable = null
            hideFloatingOverlay()
        }
        pendingNavAwayRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    // Screen off broadcast receiver to immediately dismiss the floater when screen locks
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                hideFloatingOverlay()
                reelSessionTracker.clearSession()
            }
        }
    }

    // Watchdog ping receiver to respond with PONG to WatcherForegroundService
    private val watchdogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == WatcherForegroundService.ACTION_WATCHDOG_PING) {
                try {
                    val pongIntent = Intent(WatcherForegroundService.ACTION_WATCHDOG_PONG).apply {
                        setPackage(packageName)
                    }
                    sendBroadcast(pongIntent)
                } catch (_: Exception) {}
            }
        }
    }

    companion object {
        private const val TAG = "ContentTrackerService"
        private const val DEBOUNCE_DELAY_MS = 1200L
        private const val WATCHDOG_INTERVAL_MS = 250L
        private const val EJECTION_COOLDOWN_MS = 2000L

        const val PACKAGE_YOUTUBE = "com.google.android.youtube"
        const val PACKAGE_INSTAGRAM = "com.instagram.android"
        const val PACKAGE_INSTAGRAM_LITE = "com.instagram.lite"

        var isServiceRunning = false
            protected set

        var activeInstance: ContentTrackerService? = null
            protected set

        fun hideOverlayIfActive() {
            activeInstance?.mainHandler?.post {
                activeInstance?.hideFloatingOverlay()
            }
        }

        fun testEject(): Boolean {
            activeInstance?.performGlobalAction(GLOBAL_ACTION_BACK)
            return activeInstance != null
        }

        fun isGenericText(text: String): Boolean {
            val lower = text.lowercase().trim()
            if (lower.isEmpty() || lower.length < 2) return true
            return lower == "like" || lower == "view likes" || lower == "liked" ||
                    lower == "dislike" || lower == "disliked" ||
                    lower == "comment" || lower == "view comments" || lower == "comments" ||
                    lower == "share" || lower == "repost" || lower == "save" || lower == "more" ||
                    lower == "audio" || lower == "original audio" || lower == "original sound" ||
                    lower == "sound" || lower == "music" ||
                    lower == "follow" || lower == "view" || lower == "ai content" ||
                    lower == "search" || lower == "search and explore" || lower == "home" ||
                    lower == "reels" || lower == "shorts" || lower == "remix" ||
                    lower.startsWith("double tap to")
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true
        activeInstance = this
        try {
            val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
            registerReceiver(screenReceiver, filter)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering screenReceiver", e)
        }
        try {
            val pingFilter = IntentFilter(WatcherForegroundService.ACTION_WATCHDOG_PING)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(watchdogReceiver, pingFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(watchdogReceiver, pingFilter)
            }
            // Send immediate PONG upon connection
            val pongIntent = Intent(WatcherForegroundService.ACTION_WATCHDOG_PONG).apply {
                setPackage(packageName)
            }
            sendBroadcast(pongIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error registering watchdogReceiver", e)
        }
        Log.i(TAG, "ContentTrackerService connected and monitoring started.")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        isServiceRunning = false
        hideFloatingOverlay()
        reelSessionTracker.clearSession()
        Log.i(TAG, "ContentTrackerService unbound.")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isServiceRunning = false
        if (activeInstance == this) {
            activeInstance = null
        }
        hideFloatingOverlay()
        reelSessionTracker.clearSession()
        try {
            unregisterReceiver(screenReceiver)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(watchdogReceiver)
        } catch (_: Exception) {}
        floatingCounterManager.destroy()
        serviceScope.cancel()
        Log.i(TAG, "ContentTrackerService destroyed.")
        super.onDestroy()
    }

    override fun onInterrupt() {
        Log.w(TAG, "ContentTrackerService interrupted.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // 1. If event is from our OWN application package (ReelShield/Kontre):
        if (packageName == applicationContext.packageName) {
            if (com.reelshield.app.MainActivity.isResumed) {
                hideFloatingOverlay()
            }
            return
        }

        // 2. Ignore system UI / keyboard events (status bar, volume, notifications, Gboard)
        if (packageName == "com.android.systemui" || packageName == "android" ||
            packageName.contains("inputmethod") || packageName.contains("keyboard")
        ) {
            return
        }

        // 3. If event is from outside YouTube and Instagram:
        if (packageName != PACKAGE_YOUTUBE && packageName != PACKAGE_INSTAGRAM && packageName != PACKAGE_INSTAGRAM_LITE) {
            hideFloatingOverlay()
            reelSessionTracker.clearSession()
            return
        }

        // 4. For target apps, route events directly to vertical feed handlers
        when (packageName) {
            PACKAGE_YOUTUBE -> handleYouTubeEvent(event)
            PACKAGE_INSTAGRAM, PACKAGE_INSTAGRAM_LITE -> handleInstagramEvent(event)
        }
    }

    private fun handleYouTubeEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: event.source ?: return

        // Multi-factor Vertical Video Feed Heuristic
        val analysis = YouTubeFeedAnalysis()
        val isVerticalFeed = confirmYouTubeVerticalFeed(rootNode, event, analysis)

        if (!isVerticalFeed) {
            scheduleNavAway()
            return
        }

        cancelPendingNavAway()

        // Check if daily limit already reached: auto-block immediate entrance
        if (quotaManager.isLimitReached && quotaManager.isYouTubeBlockEnabled) {
            hideFloatingOverlay()
            val homeNode = if (analysis.isShortsTabSelected && !isNodeSelected(analysis.homeTabNode)) analysis.homeTabNode else null
            ejectFromVerticalFeed(PACKAGE_YOUTUBE, homeNode, "YouTube Shorts")
            return
        }

        // Show/refresh floating overlay on vertical feed (only if enabled and under limit)
        updateFloatingOverlayState()

        val videoFingerprint = analysis.getVideoKey().ifEmpty {
            ReelFingerprintExtractor.extractReelFingerprint(rootNode) ?: ""
        }

        if (videoFingerprint.isNotEmpty()) {
            reelSessionTracker.onReelNodeDetected(videoFingerprint, "YouTube Shorts")
        }
    }

    private fun handleInstagramEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: event.source ?: return

        // Multi-factor Vertical Video Feed Heuristic
        val analysis = InstagramFeedAnalysis()
        val isVerticalFeed = confirmInstagramVerticalFeed(rootNode, event, analysis)

        if (!isVerticalFeed) {
            scheduleNavAway()
            return
        }

        cancelPendingNavAway()

        // Check if daily limit already reached: auto-block immediate entrance
        if (quotaManager.isLimitReached && quotaManager.isInstagramBlockEnabled) {
            hideFloatingOverlay()
            val homeNode = if (analysis.isReelsTabSelected && !isNodeSelected(analysis.homeTabNode)) analysis.homeTabNode else null
            ejectFromVerticalFeed(PACKAGE_INSTAGRAM, homeNode, "Instagram Reels")
            return
        }

        // Show/refresh floating overlay on vertical feed (only if enabled and under limit)
        updateFloatingOverlayState()

        val videoFingerprint = analysis.getVideoKey().ifEmpty {
            ReelFingerprintExtractor.extractReelFingerprint(rootNode) ?: ""
        }

        if (videoFingerprint.isNotEmpty()) {
            reelSessionTracker.onReelNodeDetected(videoFingerprint, "Instagram Reels")
        }
    }

    private fun updateFloatingOverlayState() {
        if (quotaManager.isFloatingCounterEnabled && !quotaManager.isLimitReached) {
            val currentCount = quotaManager.totalCount
            val limit = quotaManager.dailyLimit
            floatingCounterManager.showOrUpdate(currentCount, limit)
            startLivenessWatchdog()
        } else {
            hideFloatingOverlay()
        }
    }

    fun hideFloatingOverlay() {
        cancelPendingNavAway()
        stopLivenessWatchdog()
        floatingCounterManager.hide()
        reelSessionTracker.onUserNavigatedAway()
    }

    private fun startLivenessWatchdog() {
        mainHandler.post {
            mainHandler.removeCallbacks(livenessWatchdog)
            mainHandler.postDelayed(livenessWatchdog, WATCHDOG_INTERVAL_MS)
        }
    }

    private fun stopLivenessWatchdog() {
        mainHandler.post {
            mainHandler.removeCallbacks(livenessWatchdog)
        }
    }

    /**
     * Active Watchdog: Periodically validates that the user is genuinely still
     * watching short-form videos while the floating badge is showing.
     * Guarantees the popup disappears when leaving Shorts/Reels or app,
     * with anti-flutter debounce during scroll transitions.
     */
    private val livenessWatchdog = object : Runnable {
        override fun run() {
            // If user is actively dragging, keep watchdog running without dismissing
            if (floatingCounterManager.isDragging) {
                mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
                return
            }

            val rootNode = rootInActiveWindow
            if (rootNode == null) {
                val activeWindow = windows.firstOrNull { it.isActive || it.isFocused }
                val activePkg = activeWindow?.root?.packageName?.toString() ?: ""
                val isTargetAppActive = activePkg == PACKAGE_YOUTUBE || activePkg == PACKAGE_INSTAGRAM || activePkg == PACKAGE_INSTAGRAM_LITE

                if (!isTargetAppActive) {
                    hideFloatingOverlay()
                    reelSessionTracker.clearSession()
                    return
                }
                mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
                return
            }

            val pkg = rootNode.packageName?.toString() ?: ""
            val isStillInTargetApp = pkg == PACKAGE_YOUTUBE || pkg == PACKAGE_INSTAGRAM || pkg == PACKAGE_INSTAGRAM_LITE

            if (!isStillInTargetApp) {
                hideFloatingOverlay()
                reelSessionTracker.clearSession()
                return
            }

            // Verify if still in vertical video feed
            val ytAnalysis = if (pkg == PACKAGE_YOUTUBE) YouTubeFeedAnalysis() else null
            val igAnalysis = if (pkg != PACKAGE_YOUTUBE) InstagramFeedAnalysis() else null

            val isStillInVerticalFeed = when (pkg) {
                PACKAGE_YOUTUBE -> confirmYouTubeVerticalFeed(rootNode, null, ytAnalysis!!)
                PACKAGE_INSTAGRAM, PACKAGE_INSTAGRAM_LITE -> confirmInstagramVerticalFeed(rootNode, null, igAnalysis!!)
                else -> false
            }

            if (!isStillInVerticalFeed) {
                scheduleNavAway()
                mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
                return
            }
            cancelPendingNavAway()

            // Daily limit enforcement inside watchdog: if limit hit and block enabled, redirect gracefully!
            if (quotaManager.isLimitReached) {
                val isBlockEnabled = if (pkg == PACKAGE_YOUTUBE) quotaManager.isYouTubeBlockEnabled else quotaManager.isInstagramBlockEnabled
                if (isBlockEnabled) {
                    hideFloatingOverlay()
                    val isDedicatedTabSelected = ytAnalysis?.isShortsTabSelected == true || igAnalysis?.isReelsTabSelected == true
                    val targetHomeNode = if (pkg == PACKAGE_YOUTUBE) ytAnalysis?.homeTabNode else igAnalysis?.homeTabNode
                    val homeNode = if (isDedicatedTabSelected && !isNodeSelected(targetHomeNode)) targetHomeNode else null
                    ejectFromVerticalFeed(pkg, homeNode, if (pkg == PACKAGE_YOUTUBE) "YouTube Shorts" else "Instagram Reels")
                    return
                }
            }

            // Ensure floating counter is hidden if limit is reached or disabled
            if (quotaManager.isLimitReached || !quotaManager.isFloatingCounterEnabled) {
                floatingCounterManager.hide()
            }

            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private fun ejectFromVerticalFeed(
        targetPackage: String,
        homeNode: AccessibilityNodeInfo?,
        platformName: String
    ) {
        hideFloatingOverlay()

        mainHandler.post {
            val now = System.currentTimeMillis()

            // 1. Safety guard: If recently redirected to Home, suppress further actions!
            // Prevents double-clicking Home tab or triggering BACK immediately after landing on Home,
            // which causes Instagram to refresh the feed / scroll to top.
            if (now - lastHomeRedirectTime < 2500L) {
                Log.d(TAG, "Ejection suppressed: Recently redirected to Home (${now - lastHomeRedirectTime}ms ago)")
                return@post
            }

            // 2. If Home tab is ALREADY selected, DO NOT click or press BACK!
            // Pressing BACK or clicking Home when already on the Home tab refreshes the feed.
            if (homeNode != null && isNodeSelected(homeNode)) {
                Log.d(TAG, "Already on Home tab. Suppressing ejection to prevent feed refresh.")
                return@post
            }

            // 3. If on dedicated short-form tab (Reels/Shorts tab) and Home tab is NOT selected:
            // Smoothly click Home tab once.
            if (homeNode != null && homeNode.isVisibleToUser && !isNodeSelected(homeNode)) {
                lastHomeRedirectTime = now
                val clicked = performClickOnNodeOrParent(homeNode)
                if (clicked) {
                    Log.i(TAG, "Limit reached ($platformName): Smoothly switched to Home tab.")
                    return@post
                }
            }

            // 4. Fullscreen short-form player (opened from feed, search, DM):
            // Perform single BACK to dismiss the player cleanly and return to feed without refreshing.
            if (now - lastBackEjectionTime > 1500L) {
                lastBackEjectionTime = now
                Log.i(TAG, "Limit reached ($platformName): Performing single BACK to exit vertical feed without refreshing.")
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    private fun isNodeSelected(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val text = node.text?.toString()?.lowercase() ?: ""
        return node.isSelected || node.isChecked || desc.contains("selected") || text.contains("selected")
    }

    private fun findHomeTabInRoot(root: AccessibilityNodeInfo?, targetPackage: String): AccessibilityNodeInfo? {
        if (root == null) return null
        val screen = getScreenDimensions()
        return scanForHomeNode(root, targetPackage, screen)
    }

    private fun scanForHomeNode(node: AccessibilityNodeInfo, targetPackage: String, screen: ScreenSize): AccessibilityNodeInfo? {
        if (!node.isVisibleToUser) return null
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.width() <= 0 || bounds.height() <= 0) return null

        val isBottomOrTopRegion = bounds.top >= (screen.height * 0.75f) || bounds.bottom <= (screen.height * 0.20f)
        if (isBottomOrTopRegion) {
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            val text = node.text?.toString()?.lowercase() ?: ""
            val viewId = node.viewIdResourceName?.lowercase() ?: ""

            val isHome = desc == "home" || text == "home" ||
                    (targetPackage == PACKAGE_INSTAGRAM && (viewId.contains("feed_tab") || viewId.contains("tab_home") || viewId.contains("main_feed_tab"))) ||
                    (targetPackage == PACKAGE_YOUTUBE && (viewId.contains("home_pivot_button") || viewId.contains("home_tab")))

            if (isHome) {
                return node
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = scanForHomeNode(child, targetPackage, screen)
            if (found != null) return found
        }
        return null
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var current = node
        var depth = 0
        while (current != null && depth < 4) {
            if (current.isClickable) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }
        return false
    }

    // =========================================================================
    // YouTube Shorts Multi-Factor Heuristic Engine
    // =========================================================================
    private fun confirmYouTubeVerticalFeed(
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent?,
        outAnalysis: YouTubeFeedAnalysis
    ): Boolean {
        val screen = getScreenDimensions()
        scanYouTubeTree(root, screen, outAnalysis)

        // Factor 1: Explicit Shorts Tab selected in bottom bar
        if (outAnalysis.isShortsTabSelected) {
            Log.d(TAG, "[YouTube Heuristic] Shorts tab selected in bottom bar. Result = true")
            return true
        }

        // Factor 2: If bottom nav bar is found and Shorts tab is NOT selected (Home, Subscriptions, Library, You)
        if (outAnalysis.bottomNavFound && !outAnalysis.isShortsTabSelected) {
            Log.d(TAG, "[YouTube Heuristic] Bottom navigation present and Shorts tab not selected -> Result = false")
            return false
        }

        // Factor 3: Standard watch player or browse grid
        if (outAnalysis.hasStandardWatchPlayer || outAnalysis.hasBrowseOrSearchResults) {
            return false
        }

        // Factor 4: Reel container + right-edge action buttons (at least 2 stacked actions)
        val hasReelSignals = outAnalysis.hasFullScreenReelContainer || outAnalysis.hasReelPlayerOverlay || outAnalysis.has9by16VideoSurface
        val hasStackedActions = outAnalysis.stackedRightEdgeActions >= 2

        val isConfirmed = hasReelSignals && hasStackedActions
        Log.d(
            TAG,
            "[YouTube Heuristic] result=$isConfirmed (reelSignals=$hasReelSignals, " +
                    "surface9x16=${outAnalysis.has9by16VideoSurface}, " +
                    "stackedActions=${outAnalysis.stackedRightEdgeActions})"
        )

        return isConfirmed
    }

    private fun scanYouTubeTree(
        node: AccessibilityNodeInfo,
        screen: ScreenSize,
        out: YouTubeFeedAnalysis
    ) {
        if (!node.isVisibleToUser) {
            return
        }

        val rawDesc = node.contentDescription?.toString() ?: ""
        val rawText = node.text?.toString() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = rawText.lowercase()
        val contentDesc = rawDesc.lowercase()

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return
        }

        val ytRules = dynamicRuleEngine.activeRules.youtubeRules

        // Evaluate dynamic 9:16 portrait video surface (SurfaceView, TextureView, PlayerView)
        if (!out.has9by16VideoSurface && dynamicRuleEngine.isVertical9by16VideoSurface(node, screen.width, screen.height)) {
            out.has9by16VideoSurface = true
        }

        // Author / Channel heuristic
        if (out.videoAuthor.isEmpty()) {
            if (viewId.contains("channel_name") || viewId.contains("author") || viewId.contains("user_name")) {
                val candidate = if (rawText.isNotEmpty()) rawText.trim() else rawDesc.trim()
                if (!isGenericText(candidate)) out.videoAuthor = candidate
            } else if (rawDesc.startsWith("@") || rawText.startsWith("@")) {
                val candidate = if (rawText.isNotEmpty()) rawText.trim() else rawDesc.trim()
                if (!isGenericText(candidate)) out.videoAuthor = candidate
            }
        }

        // Video Title heuristic
        if (out.videoTitle.isEmpty()) {
            if (viewId.contains("title") || viewId.contains("video_title") || viewId.contains("reel_title") || viewId.contains("shorts_title")) {
                val candidate = if (rawText.isNotEmpty()) rawText.trim() else rawDesc.trim()
                if (!isGenericText(candidate)) out.videoTitle = candidate
            } else if (bounds.left < (screen.width * 0.70f) && bounds.top > (screen.height * 0.60f)) {
                val candidate = when {
                    rawText.length > 5 -> rawText.trim()
                    rawDesc.length > 5 -> rawDesc.trim()
                    else -> ""
                }
                if (candidate.isNotEmpty() && !isGenericText(candidate)) {
                    out.videoTitle = candidate
                }
            }
        }

        // Metrics from sound or like action description
        if (contentDesc.contains("like this video along with") || contentDesc.contains("using this sound") || contentDesc.contains("subscribers")) {
            if (!isGenericText(rawDesc)) {
                out.recordMetric(rawDesc.trim())
            }
        }

        // Check Bottom Navigation Bar
        val isBottomBarRegion = bounds.top >= (screen.height * 0.85f)
        if (isBottomBarRegion) {
            val isShorts = contentDesc.contains("shorts") || text.contains("shorts") ||
                    viewId.contains("shorts_tab") || viewId.contains("tab_shorts")
            val isHome = contentDesc.contains("home") || text.contains("home") ||
                    viewId.contains("home_pivot_button") || viewId.contains("home_tab")
            val isOther = !isShorts && (isHome ||
                    contentDesc.contains("subscriptions") || text.contains("subscriptions") ||
                    contentDesc.contains("you") || text.contains("you") ||
                    contentDesc.contains("library") || text.contains("library") ||
                    viewId.contains("pivot_button"))

            val isSelectedOrActive = node.isSelected || node.isChecked || contentDesc.contains("selected")

            if (isShorts) {
                out.bottomNavFound = true
                if (isSelectedOrActive) out.isShortsTabSelected = true
            } else if (isHome) {
                out.bottomNavFound = true
                out.homeTabNode = node
                if (isSelectedOrActive) out.isNonShortsTabSelected = true
            } else if (isOther) {
                out.bottomNavFound = true
                if (isSelectedOrActive) out.isNonShortsTabSelected = true
            }
        }

        // Standard 16:9 YouTube Watch Player (not Shorts)
        if ((viewId.contains("watch_player") || viewId.contains("player_view")) &&
            bounds.height() < (screen.height * 0.55f) && bounds.top < (screen.height * 0.40f)
        ) {
            out.hasStandardWatchPlayer = true
        }

        // Browse / Search results container
        if (viewId.contains("browse_fragment") || viewId.contains("results") || viewId.contains("filter_bar")) {
            out.hasBrowseOrSearchResults = true
        }

        // Full-screen Shorts reel container (including dynamic fuzzy IDs)
        val minFeedHeight = (screen.height * 0.65f).toInt()
        val minFeedWidth = (screen.width * 0.70f).toInt()
        val matchesYtFuzzy = dynamicRuleEngine.matchesFuzzyResourceId(viewId, isInstagram = false)
        if ((viewId.contains("reel") || viewId.contains("shorts") || matchesYtFuzzy) &&
            bounds.height() >= minFeedHeight && bounds.width() >= minFeedWidth
        ) {
            out.hasFullScreenReelContainer = true
        }

        if (viewId.contains("reel_player_overlay") || viewId.contains("shorts_player") || viewId.contains("reel_player")) {
            out.hasReelPlayerOverlay = true
        }

        // Right-edge Shorts action buttons: Like, Dislike, Comments, Share, Remix, Action Rail
        val isRightEdge = bounds.left >= (screen.width * 0.70f)
        val isMiddleVertical = bounds.top > (screen.height * 0.15f) && bounds.bottom < (screen.height * 0.90f)

        if (isRightEdge && isMiddleVertical) {
            val isAction = viewId.contains("like") || contentDesc.contains("like") ||
                    viewId.contains("dislike") || contentDesc.contains("dislike") ||
                    viewId.contains("comment") || contentDesc.contains("comment") ||
                    viewId.contains("share") || contentDesc.contains("share") ||
                    viewId.contains("remix") || contentDesc.contains("remix") ||
                    ytRules.actionRailKeywords.any { kw -> viewId.contains(kw) || contentDesc.contains(kw) }

            if (isAction) {
                out.recordRightEdgeActionY(bounds.centerY())
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            scanYouTubeTree(child, screen, out)
        }
    }

    // =========================================================================
    // Instagram Reels Multi-Factor Heuristic Engine
    // =========================================================================
    private fun confirmInstagramVerticalFeed(
        root: AccessibilityNodeInfo,
        event: AccessibilityEvent?,
        outAnalysis: InstagramFeedAnalysis
    ): Boolean {
        val screen = getScreenDimensions()
        scanInstagramTree(root, screen, outAnalysis)

        // Factor 0: Story or DM detected dynamically -> DEFINITELY NOT REELS
        if (outAnalysis.isStoryOrDm) {
            return false
        }

        // Factor 1: Stories tray or Home Feed detected -> DEFINITELY NOT REELS
        if (outAnalysis.hasStoriesTray || outAnalysis.isHomeFeed) {
            Log.d(TAG, "[Instagram Heuristic] Stories tray or Home feed detected. Result = false")
            return false
        }

        // Factor 2: Explicit Reels Tab selected in bottom bar -> REEL!
        if (outAnalysis.isReelsTabSelected) {
            Log.d(TAG, "[Instagram Heuristic] Reels tab selected in bottom bar. Result = true")
            return true
        }

        // Factor 3: Bottom navigation is present and Reels tab is NOT selected
        // In full-screen Reels (opened from feed or explore), the bottom navigation bar is not displayed.
        // Therefore, if the bottom bar is present and Reels is not selected, the user is on Home/Explore/Profile.
        if (outAnalysis.bottomNavFound && !outAnalysis.isReelsTabSelected) {
            Log.d(TAG, "[Instagram Heuristic] Bottom navigation present and Reels tab not selected -> Result = false")
            return false
        }

        // Factor 4: Dedicated Full-screen clips container + vertical right-edge action rail (at least 2 stacked actions)
        val minActions = dynamicRuleEngine.activeRules.instagramRules.minActionButtons.coerceAtLeast(2)
        val hasClipsSignals = outAnalysis.hasFullScreenClipsContainer || outAnalysis.has9by16VideoSurface
        val hasStackedActions = outAnalysis.stackedRightEdgeActions >= minActions

        val isConfirmed = hasClipsSignals && hasStackedActions
        Log.d(
            TAG,
            "[Instagram Heuristic] result=$isConfirmed (clipsContainer=${outAnalysis.hasFullScreenClipsContainer}, " +
                    "surface9x16=${outAnalysis.has9by16VideoSurface}, " +
                    "stackedActions=${outAnalysis.stackedRightEdgeActions}, " +
                    "minActions=$minActions, " +
                    "bottomNavFound=${outAnalysis.bottomNavFound}, " +
                    "reelsTabSelected=${outAnalysis.isReelsTabSelected})"
        )

        return isConfirmed
    }

    private fun scanInstagramTree(
        node: AccessibilityNodeInfo,
        screen: ScreenSize,
        out: InstagramFeedAnalysis
    ) {
        if (!node.isVisibleToUser) {
            return
        }

        val rawDesc = node.contentDescription?.toString() ?: ""
        val rawText = node.text?.toString() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        val text = rawText.lowercase()
        val contentDesc = rawDesc.lowercase()

        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return
        }

        val minFeedHeight = (screen.height * 0.65f).toInt()
        val minFeedWidth = (screen.width * 0.70f).toInt()
        val igRules = dynamicRuleEngine.activeRules.instagramRules

        // Dynamic Story & DM check from DynamicRuleEngine
        if (!out.isStoryOrDm) {
            val isStory = igRules.storyViewIds.any { sId ->
                val simpleId = sId.substringAfter(":id/").lowercase()
                viewId.contains(simpleId) || viewId == sId.lowercase()
            }
            val isDm = igRules.dmViewIds.any { dmId ->
                val simpleId = dmId.substringAfter(":id/").lowercase()
                viewId.contains(simpleId) || viewId == dmId.lowercase()
            }
            if (isStory || isDm) {
                out.isStoryOrDm = true
            }
        }

        // Dynamic 9:16 portrait video surface evaluation (SurfaceView, TextureView, PlayerView)
        if (!out.has9by16VideoSurface && dynamicRuleEngine.isVertical9by16VideoSurface(node, screen.width, screen.height)) {
            out.has9by16VideoSurface = true
        }

        // Extract author / caption / audio / metrics
        if (out.reelAuthor.isEmpty()) {
            if (viewId.contains("clips_author_username") || viewId.contains("profile_name") || viewId.contains("user_name") || viewId.contains("username")) {
                val candidate = if (rawText.isNotEmpty()) rawText.trim() else rawDesc.trim()
                if (!isGenericText(candidate)) {
                    out.reelAuthor = candidate
                }
            } else if (rawDesc.startsWith("reel by ", ignoreCase = true)) {
                val candidate = rawDesc.substringAfter("reel by ", "").substringBefore(".").trim()
                if (!isGenericText(candidate)) {
                    out.reelAuthor = candidate
                }
            } else if (rawDesc.startsWith("profile picture of ", ignoreCase = true)) {
                val candidate = rawDesc.substringAfter("profile picture of ", "").trim()
                if (!isGenericText(candidate)) {
                    out.reelAuthor = candidate
                }
            }
        }

        if (out.reelAudioOrCaption.isEmpty()) {
            if (viewId.contains("clips_caption") || viewId.contains("caption") || viewId.contains("subtitle") || viewId.contains("description")) {
                val candidate = if (rawText.isNotEmpty()) rawText.trim() else rawDesc.trim()
                if (!isGenericText(candidate)) {
                    out.reelAudioOrCaption = candidate.take(40)
                }
            }
        }

        if (out.audioTitle.isEmpty()) {
            if (viewId.contains("music") || viewId.contains("audio") || viewId.contains("sound")) {
                val candidate = if (rawText.isNotEmpty()) rawText.trim() else rawDesc.trim()
                if (!isGenericText(candidate)) {
                    out.audioTitle = candidate.take(40)
                }
            }
        }

        // Metrics: comment count, repost count, save count, like count (with numbers)
        if (viewId.contains("comment_count") || viewId.contains("save_count") || viewId.contains("repost_count") || viewId.contains("like_count")) {
            val candidate = if (rawText.isNotEmpty()) rawText.trim() else rawDesc.trim()
            if (!isGenericText(candidate)) {
                out.recordMetric(candidate)
            }
        }

        // Detect Home Feed signals:
        // 1. Stories tray at top of home feed (feed_reels)
        if (viewId.contains("feed_reels") || viewId.contains("stories_tray") || viewId.contains("stories_recycler")) {
            out.hasStoriesTray = true
            out.isHomeFeed = true
        }

        // 2. Main feed recyclerView / container
        if (viewId.contains("feed_recycler") || viewId.contains("main_feed") || viewId.contains("sticky_header_list") ||
            viewId.contains("layout_container_main") || viewId.contains("feed_composer")) {
            out.isHomeFeed = true
        }

        // 3. Instagram top action bar
        if (bounds.top < (screen.height * 0.15f)) {
            if (viewId.contains("action_bar_title") || viewId.contains("action_bar_inbox") ||
                viewId.contains("action_bar_notification") || viewId.contains("action_bar_share") ||
                rawText.equals("Instagram", ignoreCase = true) || rawDesc.equals("Instagram", ignoreCase = true)) {
                out.isHomeFeed = true
            }
        }

        // Check Bottom Navigation Bar
        val isBottomBarRegion = bounds.top >= (screen.height * 0.80f)
        if (isBottomBarRegion) {
            val isReelsTab = viewId.contains("clips_tab") || viewId.contains("reels_tab") ||
                    viewId.contains("tab_reels") || contentDesc.contains("reels") || text.contains("reels")
            val isHome = contentDesc.contains("home") || text.contains("home") ||
                    viewId.contains("feed_tab") || viewId.contains("tab_home") || viewId.contains("main_feed_tab")
            val isOtherTab = !isReelsTab && (isHome ||
                    viewId.contains("search_tab") || viewId.contains("profile_tab") ||
                    viewId.contains("direct_tab") || viewId.contains("activity_tab") ||
                    contentDesc.contains("search") || text.contains("search") ||
                    contentDesc.contains("explore") || text.contains("explore") ||
                    contentDesc.contains("profile") || text.contains("profile") ||
                    contentDesc.contains("create") || contentDesc.contains("direct") ||
                    contentDesc.contains("message") || contentDesc.contains("messages"))

            val isSelectedOrActive = isNodeSelected(node)

            if (isReelsTab) {
                out.bottomNavFound = true
                if (isSelectedOrActive) out.isReelsTabSelected = true
            } else if (isHome) {
                out.bottomNavFound = true
                out.homeTabNode = node
                if (isSelectedOrActive) {
                    out.isNonReelsTabSelected = true
                    out.isHomeFeed = true
                }
            } else if (isOtherTab) {
                out.bottomNavFound = true
                if (isSelectedOrActive) out.isNonReelsTabSelected = true
            }
        }

        // Full-screen clips/reels container (including dynamic fuzzy IDs)
        val matchesIgFuzzy = dynamicRuleEngine.matchesFuzzyResourceId(viewId, isInstagram = true)
        val isClipsContainer = (viewId.contains("clips_video_container") ||
                viewId.contains("clips_viewer_view_pager") ||
                viewId.contains("clips_viewer") ||
                viewId.contains("reel_viewer") ||
                viewId.contains("reel_viewer_layout") ||
                matchesIgFuzzy) &&
                !viewId.contains("feed_reels")

        if (isClipsContainer && bounds.height() >= minFeedHeight && bounds.width() >= minFeedWidth) {
            out.hasFullScreenClipsContainer = true
        }

        // Right-edge Reels action buttons:
        val isRightEdge = bounds.left >= (screen.width * 0.70f)
        val isMiddleVertical = bounds.top > (screen.height * 0.15f) && bounds.bottom < (screen.height * 0.90f)

        if (isRightEdge && isMiddleVertical) {
            val isAction = viewId.contains("like") || contentDesc.contains("like") ||
                    viewId.contains("comment") || contentDesc.contains("comment") ||
                    viewId.contains("share") || contentDesc.contains("share") ||
                    viewId.contains("audio") || contentDesc.contains("audio") ||
                    viewId.contains("remix") || contentDesc.contains("remix") ||
                    igRules.actionRailKeywords.any { kw -> viewId.contains(kw) || contentDesc.contains(kw) }

            if (isAction) {
                out.recordRightEdgeActionY(bounds.centerY())
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            scanInstagramTree(child, screen, out)
        }
    }

    // =========================================================================
    // Helpers & Models
    // =========================================================================
    private data class ScreenSize(val width: Int, val height: Int)

    private class YouTubeFeedAnalysis {
        var bottomNavFound: Boolean = false
        var isShortsTabSelected: Boolean = false
        var isNonShortsTabSelected: Boolean = false
        var hasStandardWatchPlayer: Boolean = false
        var hasBrowseOrSearchResults: Boolean = false
        var hasFullScreenReelContainer: Boolean = false
        var hasReelPlayerOverlay: Boolean = false
        var has9by16VideoSurface: Boolean = false
        var homeTabNode: AccessibilityNodeInfo? = null

        var videoAuthor: String = ""
        var videoTitle: String = ""
        var audioTrack: String = ""
        val metricsList = mutableListOf<String>()

        private val actionCenterYs = mutableListOf<Int>()

        fun recordRightEdgeActionY(centerY: Int) {
            if (actionCenterYs.none { abs(it - centerY) < 40 }) {
                actionCenterYs.add(centerY)
            }
        }

        val stackedRightEdgeActions: Int
            get() = actionCenterYs.size

        fun recordMetric(metric: String) {
            if (metricsList.size < 4 && !metricsList.contains(metric)) {
                metricsList.add(metric)
            }
        }

        fun getVideoKey(): String {
            val author = videoAuthor.trim()
            val title = videoTitle.trim().ifEmpty { audioTrack.trim() }
            val metrics = metricsList.joinToString(";")

            return when {
                author.isNotEmpty() && title.isNotEmpty() -> "$author|$title"
                author.isNotEmpty() && metrics.isNotEmpty() -> "$author|$metrics"
                author.isNotEmpty() -> author
                title.isNotEmpty() && metrics.isNotEmpty() -> "$title|$metrics"
                title.isNotEmpty() -> title
                metrics.isNotEmpty() -> "yt_m:$metrics"
                else -> ""
            }
        }
    }

    private class InstagramFeedAnalysis {
        var bottomNavFound: Boolean = false
        var isReelsTabSelected: Boolean = false
        var isNonReelsTabSelected: Boolean = false
        var hasStoriesTray: Boolean = false
        var isHomeFeed: Boolean = false
        var hasFullScreenClipsContainer: Boolean = false
        var has9by16VideoSurface: Boolean = false
        var isStoryOrDm: Boolean = false
        var homeTabNode: AccessibilityNodeInfo? = null

        var reelAuthor: String = ""
        var reelAudioOrCaption: String = ""
        var audioTitle: String = ""
        val metricsList = mutableListOf<String>()

        private val actionCenterYs = mutableListOf<Int>()

        fun recordRightEdgeActionY(centerY: Int) {
            if (actionCenterYs.none { abs(it - centerY) < 40 }) {
                actionCenterYs.add(centerY)
            }
        }

        val stackedRightEdgeActions: Int
            get() = actionCenterYs.size

        fun recordMetric(metric: String) {
            if (metricsList.size < 4 && !metricsList.contains(metric)) {
                metricsList.add(metric)
            }
        }

        fun getVideoKey(): String {
            val author = reelAuthor.trim()
            val caption = reelAudioOrCaption.trim().ifEmpty { audioTitle.trim() }
            val metrics = metricsList.joinToString(";")

            return when {
                author.isNotEmpty() && caption.isNotEmpty() -> "$author|$caption"
                author.isNotEmpty() && metrics.isNotEmpty() -> "$author|$metrics"
                author.isNotEmpty() -> author
                caption.isNotEmpty() && metrics.isNotEmpty() -> "$caption|$metrics"
                caption.isNotEmpty() -> caption
                metrics.isNotEmpty() -> "ig_m:$metrics"
                else -> ""
            }
        }
    }

    private fun getScreenDimensions(): ScreenSize {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            val bounds = wm?.currentWindowMetrics?.bounds
            if (bounds != null) {
                ScreenSize(bounds.width(), bounds.height())
            } else {
                ScreenSize(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
            }
        } else {
            ScreenSize(resources.displayMetrics.widthPixels, resources.displayMetrics.heightPixels)
        }
    }

    private fun getEventName(eventType: Int): String {
        return when (eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> "TYPE_VIEW_SCROLLED"
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "TYPE_WINDOW_STATE_CHANGED"
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> "TYPE_WINDOWS_CHANGED"
            AccessibilityEvent.TYPE_VIEW_CLICKED -> "TYPE_VIEW_CLICKED"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "TYPE_WINDOW_CONTENT_CHANGED"
            else -> "EVENT_$eventType"
        }
    }
}
