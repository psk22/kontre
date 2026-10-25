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

    // Debounce and active video signature tracking per package
    private var lastYouTubeEventTime = 0L
    private var lastInstagramEventTime = 0L
    private var lastYouTubeVideoKey = ""
    private var lastInstagramVideoKey = ""

    // Ejection rate-limiter to prevent multiple back presses from kicking the user out of the app
    private var lastEjectionTime = 0L

    // Screen off broadcast receiver to immediately dismiss the floater when screen locks
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                hideFloatingOverlay()
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
        Log.i(TAG, "ContentTrackerService unbound.")
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        isServiceRunning = false
        if (activeInstance == this) {
            activeInstance = null
        }
        hideFloatingOverlay()
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
            val activePkg = rootInActiveWindow?.packageName?.toString()
            if (activePkg != null &&
                activePkg != PACKAGE_YOUTUBE &&
                activePkg != PACKAGE_INSTAGRAM &&
                activePkg != PACKAGE_INSTAGRAM_LITE &&
                activePkg != "com.android.systemui" &&
                activePkg != applicationContext.packageName
            ) {
                hideFloatingOverlay()
            }
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
            // User is on home feed, search, normal 16:9 video, or comments - hide floating overlay immediately
            hideFloatingOverlay()
            lastYouTubeVideoKey = ""
            return
        }

        // Check if daily limit already reached: auto-block immediate entrance!
        if (quotaManager.isLimitReached && quotaManager.isYouTubeBlockEnabled) {
            if (quotaManager.isFloatingCounterEnabled) {
                floatingCounterManager.showOrUpdate(quotaManager.totalCount, quotaManager.dailyLimit)
            }
            ejectFromVerticalFeed(PACKAGE_YOUTUBE, analysis.homeTabNode, "YouTube Shorts")
            return
        }

        // Show/refresh floating overlay on vertical feed (only if enabled)
        updateFloatingOverlayState()

        val videoKey = analysis.getVideoKey()
        val currentTime = System.currentTimeMillis()
        val isNewVideo = videoKey.isNotEmpty() && videoKey != lastYouTubeVideoKey
        val isScroll = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        val isFirstOpen = lastYouTubeVideoKey.isEmpty() && videoKey.isNotEmpty()

        if (isNewVideo || isScroll || isFirstOpen) {
            if (currentTime - lastYouTubeEventTime >= DEBOUNCE_DELAY_MS) {
                lastYouTubeEventTime = currentTime
                if (videoKey.isNotEmpty()) {
                    lastYouTubeVideoKey = videoKey
                }
                val eventName = if (isNewVideo) "VIDEO_TRANSITION" else getEventName(event.eventType)
                Log.i(TAG, ">> [CONFIRMED VERTICAL FEED] YouTube Shorts | Event: $eventName | Key: '$videoKey'")

                dispatchDetection(
                    targetPackage = PACKAGE_YOUTUBE,
                    targetPlatform = "YouTube Shorts",
                    eventType = eventName
                )
            } else if (isNewVideo) {
                lastYouTubeVideoKey = videoKey
            }
        }
    }

    private fun handleInstagramEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: event.source ?: return

        // Multi-factor Vertical Video Feed Heuristic
        val analysis = InstagramFeedAnalysis()
        val isVerticalFeed = confirmInstagramVerticalFeed(rootNode, event, analysis)
        Log.i(TAG, "handleInstagramEvent: isVerticalFeed=$isVerticalFeed, reelsTab=${analysis.isReelsTabSelected}, nonReelsTab=${analysis.isNonReelsTabSelected}, bottomNav=${analysis.bottomNavFound}, storiesTray=${analysis.hasStoriesTray}, clipsContainer=${analysis.hasFullScreenClipsContainer}, actions=${analysis.stackedRightEdgeActions}")

        if (!isVerticalFeed) {
            // User is on home feed, stories, DMs, profile, or explorer - hide floating overlay immediately
            hideFloatingOverlay()
            lastInstagramVideoKey = ""
            return
        }

        // Check if daily limit already reached: auto-block immediate entrance!
        if (quotaManager.isLimitReached && quotaManager.isInstagramBlockEnabled) {
            if (quotaManager.isFloatingCounterEnabled) {
                floatingCounterManager.showOrUpdate(quotaManager.totalCount, quotaManager.dailyLimit)
            }
            ejectFromVerticalFeed(PACKAGE_INSTAGRAM, analysis.homeTabNode, "Instagram Reels")
            return
        }

        // Show/refresh floating overlay on vertical feed (only if enabled)
        updateFloatingOverlayState()

        val videoKey = analysis.getVideoKey()
        val currentTime = System.currentTimeMillis()
        val isNewVideo = videoKey.isNotEmpty() && videoKey != lastInstagramVideoKey
        val isScroll = event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED
        val isFirstOpen = lastInstagramVideoKey.isEmpty() && videoKey.isNotEmpty()

        if (isNewVideo || isScroll || isFirstOpen) {
            if (currentTime - lastInstagramEventTime >= DEBOUNCE_DELAY_MS) {
                lastInstagramEventTime = currentTime
                if (videoKey.isNotEmpty()) {
                    lastInstagramVideoKey = videoKey
                }
                val eventName = if (isNewVideo) "REEL_TRANSITION" else getEventName(event.eventType)
                Log.i(TAG, ">> [CONFIRMED VERTICAL FEED] Instagram Reels | Event: $eventName | Key: '$videoKey'")

                dispatchDetection(
                    targetPackage = PACKAGE_INSTAGRAM,
                    targetPlatform = "Instagram Reels",
                    eventType = eventName
                )
            } else if (isNewVideo) {
                lastInstagramVideoKey = videoKey
            }
        }
    }

    private fun updateFloatingOverlayState() {
        if (quotaManager.isFloatingCounterEnabled) {
            val currentCount = quotaManager.totalCount
            val limit = quotaManager.dailyLimit
            floatingCounterManager.showOrUpdate(currentCount, limit)
            startLivenessWatchdog()
        } else {
            hideFloatingOverlay()
        }
    }

    fun hideFloatingOverlay() {
        stopLivenessWatchdog()
        floatingCounterManager.hide()
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
     * Guarantees the popup disappears within ~250ms when leaving Shorts/Reels or app,
     * and strictly blocks once the daily limit (e.g. 50) is reached.
     */
    private val livenessWatchdog = object : Runnable {
        override fun run() {
            if (!quotaManager.isFloatingCounterEnabled) {
                hideFloatingOverlay()
                return
            }

            if (!floatingCounterManager.isShowing) return

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
                    return
                }
                mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
                return
            }

            val pkg = rootNode.packageName?.toString() ?: ""
            val isStillInTargetApp = pkg == PACKAGE_YOUTUBE || pkg == PACKAGE_INSTAGRAM || pkg == PACKAGE_INSTAGRAM_LITE

            if (!isStillInTargetApp) {
                hideFloatingOverlay()
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
                hideFloatingOverlay()
                return
            }

            // Daily limit enforcement inside watchdog: if limit hit and block enabled, redirect gracefully!
            if (quotaManager.isLimitReached) {
                val isBlockEnabled = if (pkg == PACKAGE_YOUTUBE) quotaManager.isYouTubeBlockEnabled else quotaManager.isInstagramBlockEnabled
                if (isBlockEnabled) {
                    val homeNode = ytAnalysis?.homeTabNode ?: igAnalysis?.homeTabNode
                    ejectFromVerticalFeed(pkg, homeNode, if (pkg == PACKAGE_YOUTUBE) "YouTube Shorts" else "Instagram Reels")
                    return
                }
            }

            mainHandler.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private fun ejectFromVerticalFeed(
        targetPackage: String,
        homeNode: AccessibilityNodeInfo?,
        platformName: String
    ) {
        val now = System.currentTimeMillis()
        if (now - lastEjectionTime < EJECTION_COOLDOWN_MS) {
            return
        }
        lastEjectionTime = now

        mainHandler.post {
            var redirected = false

            val nodeToClick = homeNode ?: findHomeTabInRoot(rootInActiveWindow, targetPackage)
            if (nodeToClick != null) {
                redirected = performClickOnNodeOrParent(nodeToClick)
            }

            if (redirected) {
                Log.i(TAG, "Limit reached ($platformName): Smoothly redirected to Home feed.")
                Toast.makeText(
                    applicationContext,
                    "🛑 Daily limit of ${quotaManager.dailyLimit} completed! Redirected to Home.",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Log.i(TAG, "Limit reached ($platformName): Performing single BACK to exit vertical feed.")
                performGlobalAction(GLOBAL_ACTION_BACK)
                Toast.makeText(
                    applicationContext,
                    "🛑 Daily limit of ${quotaManager.dailyLimit} completed! $platformName exited.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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

        // Factor 2: If Home/Subscriptions/Library is selected, not Shorts
        if (outAnalysis.isNonShortsTabSelected && outAnalysis.bottomNavFound) {
            return false
        }

        // Factor 3: Standard watch player or browse grid
        if (outAnalysis.hasStandardWatchPlayer || outAnalysis.hasBrowseOrSearchResults) {
            return false
        }

        // Factor 4: Reel container + right-edge action buttons
        val hasReelSignals = outAnalysis.hasFullScreenReelContainer || outAnalysis.hasReelPlayerOverlay
        val hasStackedActions = outAnalysis.stackedRightEdgeActions >= 1

        val isConfirmed = hasReelSignals && hasStackedActions
        Log.d(
            TAG,
            "[YouTube Heuristic] result=$isConfirmed (reelSignals=$hasReelSignals, " +
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

        // Author / Channel heuristic
        if (out.videoAuthor.isEmpty()) {
            if (viewId.contains("channel_name") || viewId.contains("author") || viewId.contains("user_name")) {
                if (rawText.isNotEmpty()) out.videoAuthor = rawText.trim()
                else if (rawDesc.isNotEmpty()) out.videoAuthor = rawDesc.trim()
            } else if (rawDesc.startsWith("@") || rawText.startsWith("@")) {
                out.videoAuthor = if (rawText.isNotEmpty()) rawText.trim() else rawDesc.trim()
            }
        }

        // Video Title heuristic
        if (out.videoTitle.isEmpty()) {
            if (viewId.contains("title") || viewId.contains("video_title") || viewId.contains("reel_title")) {
                if (rawText.isNotEmpty()) out.videoTitle = rawText.trim()
                else if (rawDesc.isNotEmpty()) out.videoTitle = rawDesc.trim()
            } else if (bounds.left < (screen.width * 0.70f) && bounds.top > (screen.height * 0.60f)) {
                val candidate = when {
                    rawText.length > 5 -> rawText
                    rawDesc.length > 5 -> rawDesc
                    else -> ""
                }
                val candidateLower = candidate.lowercase()
                val isSystemOrButton = candidateLower.contains("home") ||
                        candidateLower.contains("shorts") ||
                        candidateLower.contains("subscriptions") ||
                        candidateLower.contains("subscribe") ||
                        candidateLower.contains("channel") ||
                        candidateLower.contains("like this video") ||
                        candidateLower.contains("comments") ||
                        candidateLower.contains("save") ||
                        candidateLower.contains("share") ||
                        candidateLower.contains("remix") ||
                        candidateLower.contains("sound") ||
                        candidateLower.contains("more") ||
                        candidateLower.contains("search") ||
                        candidateLower.contains("minute") ||
                        candidateLower.contains("second")

                if (candidate.isNotEmpty() && !isSystemOrButton) {
                    out.videoTitle = candidate.trim()
                }
            }
        }

        // Backup signature from unique like / sound action description
        if (out.backupSignature.isEmpty()) {
            if (contentDesc.contains("like this video along with")) {
                out.backupSignature = rawDesc.trim()
            } else if (contentDesc.contains("using this sound")) {
                out.backupSignature = rawDesc.trim()
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

        // Full-screen Shorts reel container
        val minFeedHeight = (screen.height * 0.65f).toInt()
        val minFeedWidth = (screen.width * 0.70f).toInt()
        if ((viewId.contains("reel") || viewId.contains("shorts")) &&
            bounds.height() >= minFeedHeight && bounds.width() >= minFeedWidth
        ) {
            out.hasFullScreenReelContainer = true
        }

        if (viewId.contains("reel_player_overlay") || viewId.contains("shorts_player") || viewId.contains("reel_player")) {
            out.hasReelPlayerOverlay = true
        }

        // Right-edge Shorts action buttons: Like, Dislike, Comments, Share, Remix
        val isRightEdge = bounds.left >= (screen.width * 0.70f)
        val isMiddleVertical = bounds.top > (screen.height * 0.15f) && bounds.bottom < (screen.height * 0.90f)

        if (isRightEdge && isMiddleVertical) {
            val isAction = viewId.contains("like") || contentDesc.contains("like") ||
                    viewId.contains("dislike") || contentDesc.contains("dislike") ||
                    viewId.contains("comment") || contentDesc.contains("comment") ||
                    viewId.contains("share") || contentDesc.contains("share") ||
                    viewId.contains("remix") || contentDesc.contains("remix")

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

        // Factor 1: Stories tray is visible on Home Feed -> DEFINITELY NOT REELS
        if (outAnalysis.hasStoriesTray) {
            return false
        }

        // Factor 2: Explicit Reels Tab selected in bottom bar -> REEL!
        if (outAnalysis.isReelsTabSelected) {
            Log.d(TAG, "[Instagram Heuristic] Reels tab selected in bottom bar. Result = true")
            return true
        }

        // Factor 3: Non-reels tab selected in bottom bar (Home, Search, Profile)
        // If bottom bar is present and a non-reels tab is selected, any background clips container is pre-warmed cache!
        if (outAnalysis.isNonReelsTabSelected && outAnalysis.bottomNavFound) {
            return false
        }

        // Factor 4: Full-screen clips container + right-edge action buttons
        // (Used when user opened a Reel from Feed or Explore into dedicated full-screen viewer)
        val hasClipsSignals = outAnalysis.hasFullScreenClipsContainer
        val hasStackedActions = outAnalysis.stackedRightEdgeActions >= 1

        val isConfirmed = hasClipsSignals && hasStackedActions
        Log.d(
            TAG,
            "[Instagram Heuristic] result=$isConfirmed (clipsContainer=$hasClipsSignals, " +
                    "stackedActions=$hasStackedActions, " +
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

        // Extract author / caption / audio
        if (out.reelAuthor.isEmpty()) {
            if (viewId.contains("profile_name") || viewId.contains("user_name") || viewId.contains("username")) {
                if (rawText.isNotEmpty()) out.reelAuthor = rawText.trim()
            } else if (rawDesc.startsWith("reel by ", ignoreCase = true)) {
                out.reelAuthor = rawDesc.trim()
            }
        }

        if (out.reelAudioOrCaption.isEmpty()) {
            if (viewId.contains("music_title") || viewId.contains("audio_track") || viewId.contains("caption")) {
                if (rawText.isNotEmpty()) out.reelAudioOrCaption = rawText.trim()
                else if (rawDesc.isNotEmpty()) out.reelAudioOrCaption = rawDesc.trim()
            }
        }

        if (out.backupSignature.isEmpty()) {
            if (viewId.contains("like_count") || rawDesc.contains("like", ignoreCase = true)) {
                if (rawText.isNotEmpty()) out.backupSignature = rawText.trim()
                else if (rawDesc.isNotEmpty()) out.backupSignature = rawDesc.trim()
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

            val isSelectedOrActive = node.isSelected || node.isChecked || contentDesc.contains("selected")

            if (isReelsTab) {
                out.bottomNavFound = true
                if (isSelectedOrActive) out.isReelsTabSelected = true
            } else if (isHome) {
                out.bottomNavFound = true
                out.homeTabNode = node
                if (isSelectedOrActive) out.isNonReelsTabSelected = true
            } else if (isOtherTab) {
                out.bottomNavFound = true
                if (isSelectedOrActive) out.isNonReelsTabSelected = true
            }
        }

        // Detect Stories tray at top of home feed (feed_reels)
        if (viewId.contains("feed_reels") || viewId.contains("stories_tray")) {
            out.hasStoriesTray = true
        }

        // Full-screen clips/reels container
        val isClipsContainer = (viewId.contains("clips_video_container") ||
                viewId.contains("clips_viewer_view_pager") ||
                viewId.contains("clips_viewer") ||
                viewId.contains("reel_viewer") ||
                viewId.contains("reel_viewer_layout")) &&
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
                    viewId.contains("remix") || contentDesc.contains("remix")

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
        var homeTabNode: AccessibilityNodeInfo? = null

        var videoAuthor: String = ""
        var videoTitle: String = ""
        var backupSignature: String = ""

        private val actionCenterYs = mutableListOf<Int>()

        fun recordRightEdgeActionY(centerY: Int) {
            if (actionCenterYs.none { abs(it - centerY) < 40 }) {
                actionCenterYs.add(centerY)
            }
        }

        val stackedRightEdgeActions: Int
            get() = actionCenterYs.size

        fun getVideoKey(): String {
            return when {
                videoAuthor.isNotEmpty() && videoTitle.isNotEmpty() -> "$videoAuthor|$videoTitle"
                videoAuthor.isNotEmpty() -> videoAuthor
                videoTitle.isNotEmpty() -> videoTitle
                backupSignature.isNotEmpty() -> backupSignature
                else -> ""
            }
        }
    }

    private class InstagramFeedAnalysis {
        var bottomNavFound: Boolean = false
        var isReelsTabSelected: Boolean = false
        var isNonReelsTabSelected: Boolean = false
        var hasStoriesTray: Boolean = false
        var hasFullScreenClipsContainer: Boolean = false
        var homeTabNode: AccessibilityNodeInfo? = null

        var reelAuthor: String = ""
        var reelAudioOrCaption: String = ""
        var backupSignature: String = ""

        private val actionCenterYs = mutableListOf<Int>()

        fun recordRightEdgeActionY(centerY: Int) {
            if (actionCenterYs.none { abs(it - centerY) < 40 }) {
                actionCenterYs.add(centerY)
            }
        }

        val stackedRightEdgeActions: Int
            get() = actionCenterYs.size

        fun getVideoKey(): String {
            return when {
                reelAuthor.isNotEmpty() && reelAudioOrCaption.isNotEmpty() -> "$reelAuthor|$reelAudioOrCaption"
                reelAuthor.isNotEmpty() -> reelAuthor
                reelAudioOrCaption.isNotEmpty() -> reelAudioOrCaption
                backupSignature.isNotEmpty() -> backupSignature
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

    private fun dispatchDetection(
        targetPackage: String,
        targetPlatform: String,
        eventType: String
    ) {
        serviceScope.launch {
            val shouldBlock = when (targetPackage) {
                PACKAGE_YOUTUBE -> quotaManager.isYouTubeBlockEnabled
                PACKAGE_INSTAGRAM, PACKAGE_INSTAGRAM_LITE -> quotaManager.isInstagramBlockEnabled
                else -> false
            }

            // Increment short-form count in QuotaManager
            val todayTotal = if (targetPackage == PACKAGE_YOUTUBE) {
                quotaManager.incrementShorts()
            } else {
                quotaManager.incrementReels()
            }

            val dailyLimit = quotaManager.dailyLimit
            val isLimitReached = todayTotal >= dailyLimit
            Log.i(TAG, "Incremented count: $todayTotal / $dailyLimit for $targetPlatform")

            // Update floating counter overlay with "3/50" format
            if (quotaManager.isFloatingCounterEnabled) {
                floatingCounterManager.showOrUpdate(todayTotal, dailyLimit)
                startLivenessWatchdog()
            }

            // BLOCKING ENFORCEMENT: After hitting 50 reels (or dailyLimit), auto-block!
            if (isLimitReached && shouldBlock) {
                ejectFromVerticalFeed(targetPackage, null, targetPlatform)
            }
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
