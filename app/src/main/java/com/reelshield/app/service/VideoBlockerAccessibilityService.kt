package com.reelshield.app.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
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

    val isShortFormCurrentlyActive: Boolean
        get() = ::reelSessionTracker.isInitialized && reelSessionTracker.currentActiveHash != null

    // Floating Counter Pill
    private var pillView: View? = null
    private var pillLayoutParams: WindowManager.LayoutParams? = null
    private var isPillShowing = false
    private var hidePillRunnable: Runnable? = null

    // Debounce to prevent repeated intervention bursts
    private var lastInterventionTimestamp = 0L

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

    override fun onServiceConnected() {
        super.onServiceConnected()
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.flags = info.flags or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.notificationTimeout = 50
            serviceInfo = info
            Log.i(TAG, "VideoBlockerAccessibilityService connected with FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring service info in onServiceConnected", e)
        }
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
                val targetPkg = if (platform.contains("YouTube", ignoreCase = true)) PACKAGE_YOUTUBE else PACKAGE_INSTAGRAM
                mainHandler.post { triggerIntervention(targetPkg) }
            }
        )

        KontreForegroundService.start(this)
        Log.i(TAG, "VideoBlockerAccessibilityService initialized with ReelSessionTracker.")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return

        // SAFEGUARD 1: Infinite Loop Protection (ignore our own app)
        if (packageName == applicationContext.packageName) {
            return
        }

        // SAFEGUARD 2: Package Filtering
        if (!isSupportedPackage(packageName)) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                val activePkg = rootInActiveWindow?.packageName?.toString() ?: ""
                if (activePkg.isNotEmpty() && !isSupportedPackage(activePkg) && activePkg != "com.android.systemui" && activePkg != applicationContext.packageName) {
                    cancelPendingHidePill()
                    cancelDwellTimer()
                    hideCounterPill()
                }
            }
            return
        }

        if ((packageName == PACKAGE_INSTAGRAM || packageName == PACKAGE_INSTAGRAM_LITE) && !quotaManager.isInstagramBlockEnabled) {
            cancelPendingHidePill()
            cancelDwellTimer()
            hideCounterPill()
            return
        }
        if (packageName == PACKAGE_YOUTUBE && !quotaManager.isYouTubeBlockEnabled) {
            cancelPendingHidePill()
            cancelDwellTimer()
            hideCounterPill()
            return
        }

        // SAFEGUARD 3: If keyboard is visible, user is actively texting/typing!
        if (isKeyboardVisible()) {
            cancelPendingHidePill()
            cancelDwellTimer()
            hideCounterPill()
            return
        }

        quotaManager.checkAndResetDaily()

        // Robust root node resolution
        var rootNode = rootInActiveWindow
        if (rootNode == null || !isSupportedPackage(rootNode.packageName?.toString() ?: "")) {
            val appWindow = windows?.firstOrNull { win ->
                val r = win.root
                r != null && isSupportedPackage(r.packageName?.toString() ?: "")
            }
            if (appWindow?.root != null) {
                rootNode = appWindow.root
            } else if (event.source != null) {
                var node = event.source
                while (node?.parent != null) {
                    node = node.parent
                }
                if (node != null && isSupportedPackage(node.packageName?.toString() ?: "")) {
                    rootNode = node
                }
            }
        }
        if (rootNode == null) return

        // Evaluate if user is currently inside short-form video UI using heuristics
        val isShortFormDetected = detectShortFormVideo(rootNode, packageName)

        if (isShortFormDetected) {
            cancelPendingHidePill()

            // User is watching a Reel / Short: Show the live top counter pill!
            showOrUpdateCounterPill()

            // Total Block Mode: 0 allowance allowed, eject immediately
            if (quotaManager.isTotalBlockEnabled) {
                triggerIntervention(packageName)
                return
            }

            // Quota already exhausted and user is NOT on the granted last-video session:
            // (e.g. user re-opened Reels later after finishing 50/50)
            if (quotaManager.isLimitReached && reelSessionTracker.lastAllowedHash == null) {
                triggerIntervention(packageName)
                return
            }

            // Extract composite unique fingerprint details
            val details = ReelFingerprintExtractor.extractDetails(rootNode)
            val platform = if (packageName == PACKAGE_YOUTUBE) "YouTube" else "Instagram"

            if (details != null) {
                reelSessionTracker.onReelNodeDetected(details)
            } else {
                val fallbackHash = extractReelFingerprint(rootNode)
                if (fallbackHash != null) {
                    reelSessionTracker.onReelNodeDetected(fallbackHash, platform)
                } else if (quotaManager.isLimitReached && reelSessionTracker.lastAllowedHash == null) {
                    triggerIntervention(packageName)
                }
            }
        } else {
            // User is NOT watching a short-form video: immediately hide counter pill and cancel dwell timer!
            cancelPendingHidePill()
            cancelDwellTimer()
            hideCounterPill()
        }
    }

    private fun cancelPendingHidePill() {
        hidePillRunnable?.let {
            mainHandler.removeCallbacks(it)
            hidePillRunnable = null
        }
    }

    private fun scheduleDebouncedHidePill() {
        if (hidePillRunnable != null) return
        hidePillRunnable = Runnable {
            val currentRoot = rootInActiveWindow
            val currentPkg = currentRoot?.packageName?.toString() ?: ""
            if (currentRoot == null || !detectShortFormVideo(currentRoot, currentPkg)) {
                cancelDwellTimer()
                hideCounterPill()
            }
            hidePillRunnable = null
        }
        mainHandler.postDelayed(hidePillRunnable!!, 2500L)
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
        val currentPkg = currentRoot.packageName?.toString() ?: ""

        val resolvedPackage = when {
            targetPackage.contains("youtube", ignoreCase = true) -> PACKAGE_YOUTUBE
            targetPackage.contains("instagram", ignoreCase = true) -> PACKAGE_INSTAGRAM
            else -> targetPackage
        }

        // SAFEGUARD A: Strict Package Match
        if (!isSupportedPackage(currentPkg) || !isSupportedPackage(resolvedPackage)) {
            Log.d(TAG, "Intervention aborted: active window package is $currentPkg, expected $resolvedPackage")
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
            if (isInstagramProfileOrExplore(currentRoot)) {
                Log.d(TAG, "Intervention aborted: user is on Profile, Notifications, or Explore grid.")
                cancelDwellTimer()
                hideCounterPill()
                return
            }
        }

        // SAFEGUARD C: Re-evaluate full-screen short-form video presence
        if (!detectShortFormVideo(currentRoot, resolvedPackage)) {
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
                if (pillView?.isAttachedToWindow == true) {
                    updatePillContent(pillView!!, total, limit, isTotalBlock, isLimitReached, isWatchingLastAllowed, previewState)
                    pillView?.requestLayout()
                    pillView?.invalidate()
                    try {
                        pillLayoutParams?.let { lp -> windowManager.updateViewLayout(pillView, lp) }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to update pill view layout in WindowManager", e)
                    }
                    return@post
                } else {
                    try {
                        windowManager.removeViewImmediate(pillView)
                    } catch (_: Exception) {}
                    pillView = null
                    isPillShowing = false
                }
            }

            try {
                val inflater = LayoutInflater.from(this)
                val view = inflater.inflate(R.layout.layout_floating_reel_counter, null)
                updatePillContent(view, total, limit, isTotalBlock, isLimitReached, isWatchingLastAllowed, previewState)

                val density = resources.displayMetrics.density
                val topMarginPx = (32 * density).toInt()

                val preferredWindowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }

                val fallbackWindowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }

                val layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    preferredWindowType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = topMarginPx
                }

                pillLayoutParams = layoutParams

                try {
                    windowManager.addView(view, layoutParams)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to add pill with preferred window type $preferredWindowType, trying fallback $fallbackWindowType", e)
                    try {
                        layoutParams.type = fallbackWindowType
                        windowManager.addView(view, layoutParams)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Failed to add counter pill with both overlay types", e2)
                        return@post
                    }
                }

                pillView = view
                isPillShowing = true
                Log.d(TAG, "Top reel counter pill attached successfully.")
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
            if (pillView != null) {
                try {
                    windowManager.removeViewImmediate(pillView)
                } catch (e: Exception) {
                    try {
                        windowManager.removeView(pillView)
                    } catch (_: Exception) {}
                } finally {
                    pillView = null
                    pillLayoutParams = null
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

        // 3. Search for an actively FOCUSED editable node in the tree
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < 60) {
            val node = queue.removeFirst()
            visited++

            if (node.isFocused && (node.isEditable || (node.className?.contains("EditText", ignoreCase = true) == true))) {
                return true
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    /**
     * Checks if a node occupies full-screen dimensions for YouTube Shorts player containers.
     */
    private fun isFullScreenNode(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return true

        val metrics = resources.displayMetrics
        val screenHeight = if (metrics.heightPixels > 0) metrics.heightPixels else 2000
        val minHeight = (screenHeight * 0.40f).toInt()
        return rect.height() >= minHeight
    }

    /**
     * Checks if a node represents a true full-screen vertical immersive Reel player.
     * Requires at least 65% of screen height and 70% of screen width.
     * In-feed video posts (sharing the screen with top header, post author,
     * action buttons, caption, and bottom navigation bar) NEVER exceed 50-55% height.
     */
    private fun isDedicatedFullscreenReel(node: AccessibilityNodeInfo): Boolean {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return false

        val metrics = resources.displayMetrics
        val screenHeight = if (metrics.heightPixels > 0) metrics.heightPixels else 2000
        val screenWidth = if (metrics.widthPixels > 0) metrics.widthPixels else 1000

        val minHeight = (screenHeight * 0.65f).toInt()
        val minWidth = (screenWidth * 0.70f).toInt()
        return rect.height() >= minHeight && rect.width() >= minWidth
    }

    /**
     * Checks if the dedicated Reels bottom tab is currently selected.
     */
    private fun isInstagramReelsTabSelected(root: AccessibilityNodeInfo): Boolean {
        val clipsTab = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_tab")
        if (!clipsTab.isNullOrEmpty()) {
            if (clipsTab.any { it.isSelected }) return true
            for (tab in clipsTab) {
                for (i in 0 until tab.childCount) {
                    val child = tab.getChild(i) ?: continue
                    if (child.isSelected) return true
                }
            }
        }

        val tabNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        val bottomBars = if (!tabNodes.isNullOrEmpty()) tabNodes else root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/bottom_navigation_bar")
        if (!bottomBars.isNullOrEmpty()) {
            val tabBar = bottomBars.first()
            for (i in 0 until tabBar.childCount) {
                val child = tabBar.getChild(i) ?: continue
                val desc = (child.contentDescription?.toString() ?: "").lowercase()
                val resId = (child.viewIdResourceName ?: "").lowercase()
                val isSelected = child.isSelected || (0 until child.childCount).any { child.getChild(it)?.isSelected == true }
                if (isSelected && (resId.contains("clips_tab") || desc == "reels" || desc.startsWith("reels,") || desc == "clips")) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Checks if the bottom Home / Feed navigation tab is currently selected.
     * Works across standard Instagram and Instagram Lite.
     */
    private fun isInstagramHomeTabSelected(root: AccessibilityNodeInfo): Boolean {
        val feedTabs = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/feed_tab")
        if (!feedTabs.isNullOrEmpty()) {
            if (feedTabs.any { it.isSelected }) return true
            for (tab in feedTabs) {
                for (i in 0 until tab.childCount) {
                    val child = tab.getChild(i) ?: continue
                    if (child.isSelected) return true
                }
            }
        }

        val tabNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        val bottomBars = if (!tabNodes.isNullOrEmpty()) tabNodes else root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/bottom_navigation_bar")
        if (!bottomBars.isNullOrEmpty()) {
            val tabBar = bottomBars.first()
            for (i in 0 until tabBar.childCount) {
                val child = tabBar.getChild(i) ?: continue
                val desc = (child.contentDescription?.toString() ?: "").lowercase()
                val resId = (child.viewIdResourceName ?: "").lowercase()
                val isSelected = child.isSelected || (0 until child.childCount).any { child.getChild(it)?.isSelected == true }
                if (isSelected && (resId.contains("feed_tab") || desc == "home" || desc.startsWith("home,") || desc == "feed")) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * Complete immunity shield for Instagram Stories.
     * When users watch Stories (including Reels shared/embedded into a Story),
     * this ensures the app NEVER intervenes and NEVER ejects the user.
     */
    private fun isInstagramStory(root: AccessibilityNodeInfo): Boolean {
        // 1. Check known Story Progress Bar and Container View IDs
        val storyViewIds = listOf(
            "com.instagram.android:id/reel_viewer_container",
            "com.instagram.android:id/reel_viewer_progress_bar",
            "com.instagram.android:id/stories_progress_bar",
            "com.instagram.android:id/story_progress",
            "com.instagram.android:id/segment_progress_bar",
            "com.instagram.android:id/story_item",
            "com.instagram.android:id/story_viewer_reply",
            "com.instagram.android:id/story_reply_composer",
            "com.instagram.android:id/direct_story_reply",
            "com.instagram.android:id/reel_viewer_image_view",
            "com.instagram.android:id/viewer_media_view_container"
        )

        for (id in storyViewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty() && nodes.any { it.isVisibleToUser }) {
                return true
            }
        }

        // 2. Tree-walk check for Story progress bar, reply prompts, or story header
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < 80) {
            val node = queue.removeFirst()
            visited++

            val resId = (node.viewIdResourceName ?: "").lowercase()
            val desc = (node.contentDescription?.toString() ?: "").trim().lowercase()
            val text = (node.text?.toString() ?: "").trim().lowercase()

            if (resId.contains("story_progress") || resId.contains("stories_progress") ||
                resId.contains("segment_progress") || resId.contains("reel_viewer_progress") ||
                resId.contains("reel_viewer_container")) {
                return true
            }

            if (resId.contains("story_reply") || resId.contains("story_composer")) {
                return true
            }

            if (desc.contains("story by") || text.contains("story by") ||
                (desc.contains("stories") && desc.contains("progress"))) {
                return true
            }

            if (text.startsWith("reply to ") || desc.startsWith("reply to ") ||
                (text == "send message" && resId.contains("story")) ||
                (desc == "send message" && resId.contains("story"))) {
                return true
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return false
    }

    /**
     * Complete immunity shield for Instagram Direct Messages (DMs), Chats, Inbox,
     * Message Threads, Primary/General filter tabs, Commenting, and Note Replying.
     * Guaranteed 100% immunity: even if a chat contains shared Reel previews, the user will NOT be ejected.
     */
    private fun isInstagramTextingOrChatting(root: AccessibilityNodeInfo): Boolean {
        // 1. If actively typing with keyboard or focused EditText, user is texting!
        if (isKeyboardVisible() || isUserActivelyTyping(root)) {
            return true
        }

        // 2. Direct Messages & Chat Known View IDs
        val dmViewIds = listOf(
            "com.instagram.android:id/direct_message_list",
            "com.instagram.android:id/row_thread_composer",
            "com.instagram.android:id/row_thread_composer_edittext",
            "com.instagram.android:id/direct_thread_header",
            "com.instagram.android:id/direct_thread_composer",
            "com.instagram.android:id/direct_text_message_composer",
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
            if (!nodes.isNullOrEmpty()) {
                // If user deliberately tapped a Reel from chat and it opened in dedicated fullscreen Clips ViewPager:
                val clipsPager = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_viewer_view_pager")
                if (!clipsPager.isNullOrEmpty() && clipsPager.any { it.isVisibleToUser && isDedicatedFullscreenReel(it) }) {
                    return false
                }
                return true
            }
        }

        // 3. Content description, text, and keyword matching across the tree
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < 70) {
            val node = queue.removeFirst()
            visited++

            val text = (node.text?.toString() ?: "").trim().lowercase()
            val desc = (node.contentDescription?.toString() ?: "").trim().lowercase()
            val resId = (node.viewIdResourceName ?: "").lowercase()

            // DM filter tabs (General, Primary, Requests, Channels)
            if (text == "general" || desc == "general" || text == "primary" || desc == "primary" ||
                text == "requests" || desc == "requests" || text == "channels" || desc == "channels") {
                return true
            }

            // DMs message composer placeholder
            if (text.startsWith("message...") || desc.startsWith("message...") ||
                text.startsWith("type a message") || desc.startsWith("type a message") ||
                text.startsWith("write a message") || desc.startsWith("write a message")) {
                return true
            }

            if (resId.contains("direct_inbox") || resId.contains("direct_thread")) {
                return true
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return false
    }

    /**
     * Complete immunity shield for the Instagram Home feed (scrolling/viewing posts).
     * In-feed video posts in the feed should NEVER trigger Reel blocking or ejection.
     */
    private fun isInstagramHomeFeed(root: AccessibilityNodeInfo): Boolean {
        // If a dedicated fullscreen Clips ViewPager is visibly covering the screen, user tapped into a Reel
        val clipsPager = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_viewer_view_pager")
        if (!clipsPager.isNullOrEmpty() && clipsPager.any { it.isVisibleToUser && isDedicatedFullscreenReel(it) }) {
            return false
        }

        // 1. Check if the bottom Home / Feed tab is selected
        if (isInstagramHomeTabSelected(root)) {
            return true
        }

        // 2. Check for Home feed containers and headers
        val feedIds = listOf(
            "com.instagram.android:id/feed_recycler_view",
            "com.instagram.android:id/main_feed_container",
            "com.instagram.android:id/action_bar_title_logo",
            "com.instagram.android:id/action_bar_inbox_button",
            "com.instagram.android:id/action_bar_share_button",
            "com.instagram.android:id/stories_tray",
            "com.instagram.android:id/reel_tray",
            "com.instagram.android:id/tray_recycler_view"
        )
        for (id in feedIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty() && nodes.any { it.isVisibleToUser }) {
                return true
            }
        }

        // 3. Tree-walk check for Instagram home logo
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < 40) {
            val node = queue.removeFirst()
            visited++
            val desc = (node.contentDescription?.toString() ?: "").trim().lowercase()
            val resId = (node.viewIdResourceName ?: "").lowercase()
            if (desc == "instagram" || resId.contains("action_bar_title_logo") || resId.contains("feed_recycler")) {
                return true
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return false
    }

    /**
     * Complete immunity shield for Profile, Notifications/Activity, and Explore Search Grid.
     */
    private fun isInstagramProfileOrExplore(root: AccessibilityNodeInfo): Boolean {
        // 1. Profile tab selected
        val profileTabs = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/profile_tab")
        if (!profileTabs.isNullOrEmpty() && profileTabs.any { it.isSelected }) return true

        // 2. Activity / Notifications tab selected
        val newsTabs = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/news_tab")
        if (!newsTabs.isNullOrEmpty() && newsTabs.any { it.isSelected }) return true

        // 3. Tab bar checks
        val tabNodes = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/tab_bar")
        val bottomBars = if (!tabNodes.isNullOrEmpty()) tabNodes else root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/bottom_navigation_bar")
        if (!bottomBars.isNullOrEmpty()) {
            val tabBar = bottomBars.first()
            for (i in 0 until tabBar.childCount) {
                val child = tabBar.getChild(i) ?: continue
                val desc = (child.contentDescription?.toString() ?: "").lowercase()
                val isSelected = child.isSelected || (0 until child.childCount).any { child.getChild(it)?.isSelected == true }
                if (isSelected) {
                    if (desc.contains("profile") || desc.contains("activity") || desc.contains("notification")) {
                        return true
                    }
                    if (desc.contains("search") || desc.contains("explore")) {
                        val clipsPager = root.findAccessibilityNodeInfosByViewId("com.instagram.android:id/clips_viewer_view_pager")
                        val isClipsVisible = !clipsPager.isNullOrEmpty() && clipsPager.any { it.isVisibleToUser && isDedicatedFullscreenReel(it) }
                        if (!isClipsVisible) return true
                    }
                }
            }
        }

        // 4. Profile bio or header
        val profileHeaders = listOf(
            "com.instagram.android:id/profile_header_bio_text",
            "com.instagram.android:id/action_bar_title_profile",
            "com.instagram.android:id/edit_profile_button"
        )
        for (id in profileHeaders) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty() && nodes.any { it.isVisibleToUser }) return true
        }

        return false
    }

    /**
     * Checks if a dedicated fullscreen Reel viewer is actively playing short-form video.
     * Only matches when the dedicated Reels tab is selected or the fullscreen Clips ViewPager is active.
     */
    private fun isDedicatedReelViewerActive(root: AccessibilityNodeInfo): Boolean {
        // Condition 1: Dedicated Reels bottom navigation tab is selected
        if (isInstagramReelsTabSelected(root)) {
            return true
        }

        // Condition 2: Dedicated Clips ViewPager / Container is active and occupies full screen
        val clipsPagerIds = listOf(
            "com.instagram.android:id/clips_viewer_view_pager",
            "com.instagram.android:id/clips_viewer_container",
            "com.instagram.android:id/root_clips_layout"
        )
        for (id in clipsPagerIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                val activeNode = nodes.firstOrNull { it.isVisibleToUser && isDedicatedFullscreenReel(it) }
                if (activeNode != null) {
                    return true
                }
            }
        }

        return false
    }

    private fun isYouTubeHomeOrSubscriptionsSelected(root: AccessibilityNodeInfo): Boolean {
        val tabNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/bottom_navigation_bar")
        val tabs = if (!tabNodes.isNullOrEmpty()) tabNodes else root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/pivot_bar")
        if (!tabs.isNullOrEmpty()) {
            val bar = tabs.first()
            for (i in 0 until bar.childCount) {
                val child = bar.getChild(i) ?: continue
                val isSelected = child.isSelected || (0 until child.childCount).any { child.getChild(it)?.isSelected == true }
                if (isSelected) {
                    val desc = (child.contentDescription?.toString() ?: "").lowercase()
                    if (desc == "home" || desc.startsWith("home,") ||
                        desc.contains("subscriptions") || desc.contains("you")) {
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun isYouTubeTextingOrComments(root: AccessibilityNodeInfo): Boolean {
        if (isUserActivelyTyping(root)) return true

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
     * Detects Reels opened from Reels Tab or fullscreen viewer, while safeguarding Chats, Stories, and Feeds.
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

        // --- SAFEGUARD 2: Stories Viewer Immunity (Absolute Priority!) ---
        // If user is watching any Story (including Reels shared/embedded inside a Story), NEVER BLOCK!
        if (isInstagramStory(root)) {
            return false
        }

        // --- SAFEGUARD 3: Direct Messages & Chat Immunity (Absolute Priority!) ---
        // If user is inside Instagram DMs/inbox/chat, NEVER BLOCK!
        if (isInstagramTextingOrChatting(root)) {
            return false
        }

        // --- SAFEGUARD 4: Home Feed Immunity (Absolute Priority!) ---
        // If user is on the Home page, scrolling Home feed, viewing in-feed posts, NEVER BLOCK!
        if (isInstagramHomeFeed(root)) {
            return false
        }

        // --- SAFEGUARD 5: Profile, Notifications, Explore Grid Immunity ---
        if (isInstagramProfileOrExplore(root)) {
            return false
        }

        // --- ONLY DETECT DEDICATED REEL VIEWER ---
        return isDedicatedReelViewerActive(root)
    }

    private fun detectYouTubeShorts(root: AccessibilityNodeInfo): Boolean {
        // Typing / Comments / Search Safeguard
        if (isKeyboardVisible() || isUserActivelyTyping(root) || isYouTubeTextingOrComments(root)) {
            return false
        }

        val isHomeOrSubs = isYouTubeHomeOrSubscriptionsSelected(root)

        // SIGNATURE 1: Dedicated Shorts player container or time bar
        val shortsViewIds = listOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_player_page_content",
            "com.google.android.youtube:id/reel_watch_fragment",
            "com.google.android.youtube:id/reel_player_overlay_anchor",
            "com.google.android.youtube:id/reel_time_bar",
            "com.google.android.youtube:id/reel_container",
            "com.google.android.youtube:id/reel_player",
            "com.google.android.youtube:id/shorts_player_view"
        )
        for (id in shortsViewIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                val fullScreenNode = nodes.firstOrNull { isFullScreenNode(it) }
                if (fullScreenNode != null) {
                    return true
                }
            }
        }

        if (isHomeOrSubs) {
            return false
        }

        // SIGNATURE 2: Shorts Tab selected in bottom bar
        val tabNodes = root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/bottom_navigation_bar")
        val tabs = if (!tabNodes.isNullOrEmpty()) tabNodes else root.findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/pivot_bar")
        if (!tabs.isNullOrEmpty()) {
            val bar = tabs.first()
            for (i in 0 until bar.childCount) {
                val child = bar.getChild(i) ?: continue
                val isSelected = child.isSelected || (0 until child.childCount).any { child.getChild(it)?.isSelected == true }
                if (isSelected) {
                    val desc = (child.contentDescription?.toString() ?: "").lowercase()
                    val resId = (child.viewIdResourceName ?: "").lowercase()
                    if (desc.contains("short") || resId.contains("shorts_tab")) return true
                }
            }
        }

        return false
    }

    override fun onInterrupt() {
        Log.w(TAG, "VideoBlockerAccessibilityService interrupted.")
        cancelPendingHidePill()
        cancelDwellTimer()
        hideCounterPill()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::reelSessionTracker.isInitialized) {
            reelSessionTracker.clearSession()
        }
        instance = null
        serviceScope.cancel()
        cancelPendingHidePill()
        cancelDwellTimer()
        hideCounterPill()
    }
}
