package com.reelshield.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
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

    // Dwell Coroutine Job
    private var dwellJob: Job? = null
    var isShortFormCurrentlyActive = false
    private var activeDwellPackage: String? = null

    // Floating Counter Pill
    private var pillView: View? = null
    private var isPillShowing = false

    // Debounce to prevent repeated intervention bursts
    private var lastInterventionTimestamp = 0L

    companion object {
        const val PACKAGE_INSTAGRAM = "com.instagram.android"
        const val PACKAGE_INSTAGRAM_LITE = "com.instagram.lite"
        const val PACKAGE_YOUTUBE = "com.google.android.youtube"

        // Habit Dwell Threshold (1.8 seconds)
        const val DWELL_THRESHOLD_MS = 1800L

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
    }

    private fun isSupportedPackage(pkg: String): Boolean {
        return pkg == PACKAGE_INSTAGRAM || pkg == PACKAGE_INSTAGRAM_LITE || pkg == PACKAGE_YOUTUBE
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        quotaManager = QuotaManager(applicationContext)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        KontreForegroundService.start(this)
        Log.i(TAG, "VideoBlockerAccessibilityService initialized.")
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
            cancelDwellTimer()
            hideCounterPill()
            return
        }

        if ((packageName == PACKAGE_INSTAGRAM || packageName == PACKAGE_INSTAGRAM_LITE) && !quotaManager.isInstagramBlockEnabled) {
            cancelDwellTimer()
            hideCounterPill()
            return
        }
        if (packageName == PACKAGE_YOUTUBE && !quotaManager.isYouTubeBlockEnabled) {
            cancelDwellTimer()
            hideCounterPill()
            return
        }

        // SAFEGUARD 3: If keyboard is visible, user is actively texting/typing!
        if (isKeyboardVisible()) {
            cancelDwellTimer()
            hideCounterPill()
            return
        }

        quotaManager.checkAndResetDaily()
        val rootNode = rootInActiveWindow ?: return

        // Evaluate if user is currently inside short-form video UI using heuristics
        val isShortFormDetected = detectShortFormVideo(rootNode, packageName)

        if (isShortFormDetected) {
            // User is watching a Reel / Short: Show the live top counter pill!
            showOrUpdateCounterPill()

            if (quotaManager.isLimitReached || quotaManager.isStrictMode) {
                // Limit reached (50/50, Total Block, or Strict Mode): Eject without delay
                triggerIntervention(packageName)
                return
            }

            if (!isShortFormCurrentlyActive) {
                startDwellTimer(packageName)
            }
        } else {
            // User is on Home screen/feed, chatting, or viewing stories:
            // Cancel dwell timer and hide the counter pill immediately!
            cancelDwellTimer()
            hideCounterPill()
        }
    }

    /**
     * Dwell Engine:
     * Starts a 1.8-second Coroutine timer.
     * RE-VERIFIES that the user is still actively on a short-form video when timer expires.
     */
    private fun startDwellTimer(targetPackage: String) {
        cancelDwellTimer()
        isShortFormCurrentlyActive = true
        activeDwellPackage = targetPackage

        dwellJob = serviceScope.launch {
            delay(DWELL_THRESHOLD_MS)

            // RE-VERIFY: Check active window state before performing any action!
            val currentRoot = rootInActiveWindow
            val currentPkg = currentRoot?.packageName?.toString()
            if (currentRoot == null || currentPkg != targetPackage) {
                Log.d(TAG, "Aborting dwell action: active window is null or switched to $currentPkg.")
                cancelDwellTimer()
                hideCounterPill()
                return@launch
            }

            // Texting/typing check on dwell completion
            if (isKeyboardVisible() || isUserActivelyTyping(currentRoot)) {
                Log.d(TAG, "Aborting dwell action: user is texting/chatting.")
                cancelDwellTimer()
                hideCounterPill()
                return@launch
            }

            if ((targetPackage == PACKAGE_INSTAGRAM || targetPackage == PACKAGE_INSTAGRAM_LITE) &&
                (isInstagramTextingOrChatting(currentRoot) || isInstagramHomeFeed(currentRoot) || isInstagramStory(currentRoot))) {
                Log.d(TAG, "Aborting dwell action: user is in DMs, on Home feed, or viewing Stories.")
                cancelDwellTimer()
                hideCounterPill()
                return@launch
            }

            if (!detectShortFormVideo(currentRoot, targetPackage)) {
                Log.d(TAG, "Aborting dwell action: user is no longer watching a short-form video.")
                cancelDwellTimer()
                hideCounterPill()
                return@launch
            }

            onDwellThresholdReached(targetPackage)
        }
    }

    private fun cancelDwellTimer() {
        if (dwellJob != null && dwellJob?.isActive == true) {
            dwellJob?.cancel()
        }
        dwellJob = null
        isShortFormCurrentlyActive = false
        activeDwellPackage = null
    }

    private fun onDwellThresholdReached(targetPackage: String) {
        val count = when (targetPackage) {
            PACKAGE_INSTAGRAM, PACKAGE_INSTAGRAM_LITE -> quotaManager.incrementReels()
            PACKAGE_YOUTUBE -> quotaManager.incrementShorts()
            else -> quotaManager.totalCount
        }
        Log.i(TAG, "Short-form video dwell completed! Today's count: $count / ${quotaManager.dailyLimit}")

        // Update top counter pill immediately
        showOrUpdateCounterPill()

        // Check if daily allowance is reached
        if (quotaManager.isLimitReached || quotaManager.isStrictMode) {
            triggerIntervention(targetPackage)
        }
    }

    /**
     * Intervention Handler:
     * Auto-navigates backward to pop out of the Reels/Shorts container.
     */
    private fun triggerIntervention(targetPackage: String) {
        val currentRoot = rootInActiveWindow ?: return
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
    }

    /**
     * Floating Reel Counter Pill at the Top:
     * Displays a minimal, non-intrusive HUD at the top center of the screen
     * indicating [ 🛡️ X / 50 ] while watching short-form videos.
     * Supports previewState: 0 (Normal), 1 (Warning), 2 (Locked), or -1 (Live).
     */
    fun showOrUpdateCounterPill(previewState: Int = -1) {
        mainHandler.post {
            val total = when (previewState) {
                0 -> 12
                1 -> 45
                2 -> 50
                else -> quotaManager.totalCount
            }
            val limit = quotaManager.dailyLimit
            val isLocked = previewState == 2 || (previewState == -1 && quotaManager.isLimitReached)

            if (isPillShowing && pillView != null) {
                updatePillContent(pillView!!, total, limit, isLocked)
                return@post
            }

            try {
                val inflater = LayoutInflater.from(this)
                val view = inflater.inflate(R.layout.layout_floating_reel_counter, null)
                updatePillContent(view, total, limit, isLocked)

                val density = resources.displayMetrics.density
                val topMarginPx = (38 * density).toInt()

                val layoutParams = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                    else
                        WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = topMarginPx
                }

                windowManager.addView(view, layoutParams)
                pillView = view
                isPillShowing = true
                Log.d(TAG, "Top reel counter pill attached.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to attach counter pill", e)
            }
        }
    }

    private fun updatePillContent(view: View, total: Int, limit: Int, isLocked: Boolean) {
        val pillRoot = view.findViewById<LinearLayout>(R.id.pill_root)
        val tvIcon = view.findViewById<TextView>(R.id.tv_pill_icon)
        val tvCounter = view.findViewById<TextView>(R.id.tv_pill_counter)

        if (isLocked) {
            pillRoot.setBackgroundResource(R.drawable.bg_floating_pill_locked)
            tvIcon.text = "🔒"
            tvCounter.text = "$total / $limit LOCKED"
            tvCounter.setTextColor(Color.parseColor("#FFFF4444"))
        } else {
            pillRoot.setBackgroundResource(R.drawable.bg_floating_pill)
            tvIcon.text = if (total >= 40) "⚠️" else "🛡️"
            tvCounter.text = "$total / $limit"
            tvCounter.setTextColor(
                if (total >= 40) Color.parseColor("#FFFFD60A") else Color.WHITE
            )
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
        if (!node.isVisibleToUser) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return false

        val metrics = resources.displayMetrics
        val screenHeight = if (metrics.heightPixels > 0) metrics.heightPixels else 2000
        val screenWidth = if (metrics.widthPixels > 0) metrics.widthPixels else 1000

        val minHeight = (screenHeight * 0.60f).toInt()
        val minWidth = (screenWidth * 0.60f).toInt()
        return rect.height() >= minHeight && rect.width() >= minWidth
    }

    /**
     * Checks if a node represents a true full-screen vertical immersive Reel player.
     * Requires at least 75% of screen height and 85% of screen width.
     * In-feed video posts (sharing the screen with top header, post author,
     * action buttons, caption, and bottom navigation bar) NEVER exceed 55-60% height.
     */
    private fun isDedicatedFullscreenReel(node: AccessibilityNodeInfo): Boolean {
        if (!node.isVisibleToUser) return false
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty) return false

        val metrics = resources.displayMetrics
        val screenHeight = if (metrics.heightPixels > 0) metrics.heightPixels else 2000
        val screenWidth = if (metrics.widthPixels > 0) metrics.widthPixels else 1000

        val minHeight = (screenHeight * 0.75f).toInt()
        val minWidth = (screenWidth * 0.85f).toInt()
        return rect.height() >= minHeight && rect.width() >= minWidth
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

        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var count = 0
        while (queue.isNotEmpty() && count < 40) {
            val node = queue.removeFirst()
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
                node.getChild(i)?.let { queue.add(it) }
            }
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

        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var count = 0
        while (queue.isNotEmpty() && count < 40) {
            val node = queue.removeFirst()
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
                node.getChild(i)?.let { queue.add(it) }
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

        // 3. Tree-walk check for Story progress bar, reply prompts, or story header
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < 70) {
            val node = queue.removeFirst()
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
                node.getChild(i)?.let { queue.add(it) }
            }
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

        // 4. Content description, text, and keyword matching across the tree
        // Covers Instagram Lite and custom DM tabs (General, Primary, Requests, etc.)
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
                node.getChild(i)?.let { queue.add(it) }
            }
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
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var count = 0
        var hasFullscreenVideo = false
        var hasReelIndicator = false

        while (queue.isNotEmpty() && count < 60) {
            val node = queue.removeFirst()
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
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return hasFullscreenVideo && hasReelIndicator
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

        // --- SIGNATURE 2: Generic / Lite Fullscreen Reel Player (when not on Home feed and not in DMs) ---
        return isGenericFullscreenReel(root)
    }

    private fun detectYouTubeShorts(root: AccessibilityNodeInfo): Boolean {
        // Typing / Comments / Search Safeguard
        if (isKeyboardVisible() || isUserActivelyTyping(root) || isYouTubeTextingOrComments(root)) {
            return false
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

        // SIGNATURE 2: Shorts Tab selected in bottom bar
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(root) }
        var visited = 0
        while (queue.isNotEmpty() && visited < 40) {
            val node = queue.removeFirst()
            visited++

            val desc = (node.contentDescription?.toString() ?: "").lowercase()
            val resId = (node.viewIdResourceName ?: "").lowercase()

            if (node.isSelected) {
                if (desc.contains("short") || resId.contains("shorts_tab")) return true
                if (desc.contains("home") || resId.contains("home_tab")) return false
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return false
    }

    override fun onInterrupt() {
        Log.w(TAG, "VideoBlockerAccessibilityService interrupted.")
        cancelDwellTimer()
        hideCounterPill()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        serviceScope.cancel()
        cancelDwellTimer()
        hideCounterPill()
    }
}
