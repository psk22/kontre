package com.reelshield.app.service

import android.util.Log
import com.reelshield.app.data.QuotaManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.LinkedHashSet

/**
 * Thread-safe Reel and Short Session Deduplication & Dwell Tracker.
 *
 * Rules:
 * - When Total Block Mode is OFF, user has a 50 reels/shorts daily allowance.
 * - Live counter updates as videos are watched.
 * - Dwell tracking is protected against minor UI re-renders, marquee scrolls, and like animations.
 * - When user hits the 50th video (quota reached):
 *     1. A warning is delivered to the user (pill warning & toast alert).
 *     2. User is allowed to finish watching that 50th video.
 * - From NEXT TIME (user swipes to the next video or re-opens reels):
 *     User is immediately blocked and auto-ejected.
 */
class ReelSessionTracker(
    private val serviceScope: CoroutineScope,
    private val quotaManager: QuotaManager,
    private val onCountUpdated: (newCount: Int, platform: String) -> Unit,
    private val onLimitReachedWarning: (platform: String) -> Unit,
    private val onEjectionRequired: (platform: String) -> Unit
) {

    private val TAG = "ReelSessionTracker"

    companion object {
        const val DWELL_THRESHOLD_MS = 1500L
        const val LRU_CAPACITY = 50
    }

    private val lock = Any()

    @Volatile
    var currentActiveHash: String? = null
        private set

    @Volatile
    var currentActiveCreator: String? = null
        private set

    @Volatile
    var currentActiveCaption: String? = null
        private set

    /**
     * Stores the hash of the 50th video that was allowed to finish with a warning.
     * While the user stays on this video, they are not ejected.
     * As soon as they scroll to the NEXT video, they are blocked.
     */
    @Volatile
    var lastAllowedHash: String? = null
        private set

    // Thread-safe LRU set of recently counted hashes with capacity 50
    val consumedHashes: LinkedHashSet<String> = object : LinkedHashSet<String>() {
        override fun add(element: String): Boolean {
            val added = super.add(element)
            while (size > LRU_CAPACITY) {
                val it = iterator()
                if (it.hasNext()) {
                    it.next()
                    it.remove()
                }
            }
            return added
        }
    }

    private var dwellJob: Job? = null

    // Shared StateFlow of currently tracked reel count for reactive UI
    private val _sessionReelCount = MutableStateFlow(quotaManager.totalCount)
    val sessionReelCount: StateFlow<Int> = _sessionReelCount.asStateFlow()

    /**
     * Called whenever a candidate reel fingerprint is extracted from the active window.
     * Manages deduplication, allowance checks, and initiates the 1500ms dwell countdown.
     */
    fun onReelNodeDetected(details: ReelFingerprintExtractor.ReelDetails) {
        val fingerprintHash = details.hash
        val platform = details.platform

        synchronized(lock) {
            // Case 1: Total Block Mode (0 allowance) - Block instantly
            if (quotaManager.isTotalBlockEnabled) {
                Log.w(TAG, "Total Block Mode active. Ejecting immediately!")
                onEjectionRequired(platform)
                return
            }

            // Case 2: Daily quota already reached (50/50 or higher)
            if (quotaManager.isLimitReached) {
                val isSameAllowedVideo = lastAllowedHash != null && (
                    fingerprintHash == lastAllowedHash ||
                    (details.creator.isNotEmpty() && details.creator == currentActiveCreator)
                )

                if (isSameAllowedVideo) {
                    currentActiveHash = fingerprintHash
                    return
                }

                // NEXT TIME: User swiped to a new video after hitting 50! BLOCK!
                Log.w(TAG, "Daily quota reached (${quotaManager.totalCount}/${quotaManager.dailyLimit}). Next video blocked! Ejecting!")
                onEjectionRequired(platform)
                return
            }

            // Case 3: Same video already actively playing & dwelling: ignore UI spam
            if (fingerprintHash == currentActiveHash) {
                return
            }

            // Case 3B: Same Creator Session (UI re-render, caption loaded, or audio marquee scrolled)
            if (currentActiveCreator != null && details.creator.isNotEmpty() && details.creator == currentActiveCreator) {
                val captionMatches = details.captionSnippet.isEmpty() ||
                        currentActiveCaption.isNullOrEmpty() ||
                        details.captionSnippet == currentActiveCaption

                if (captionMatches) {
                    currentActiveHash = fingerprintHash
                    if (currentActiveCaption.isNullOrEmpty() && details.captionSnippet.isNotEmpty()) {
                        currentActiveCaption = details.captionSnippet
                    }
                    return // Keep dwell timer running uninterrupted!
                }
            }

            // Case 4: Previously consumed video in LRU cache (user swiped back up/down)
            if (consumedHashes.contains(fingerprintHash)) {
                Log.d(TAG, "Reel already consumed in LRU cache ($fingerprintHash). Updating active hash without dwell.")
                currentActiveHash = fingerprintHash
                currentActiveCreator = details.creator
                currentActiveCaption = details.captionSnippet
                cancelDwellTimerLocked()
                return
            }

            // Case 5: Genuinely new video: cancel previous dwell and start fresh 1500ms timer
            Log.d(TAG, "New reel detected [${details.creator}]: $fingerprintHash on $platform. Starting 1500ms dwell countdown.")
            cancelDwellTimerLocked()
            currentActiveHash = fingerprintHash
            currentActiveCreator = details.creator
            currentActiveCaption = details.captionSnippet

            dwellJob = serviceScope.launch(Dispatchers.Default) {
                delay(DWELL_THRESHOLD_MS)

                // Check that the video is still active when timer finishes
                synchronized(lock) {
                    val isStillActive = currentActiveHash == fingerprintHash ||
                            (currentActiveCreator != null && details.creator.isNotEmpty() && currentActiveCreator == details.creator)

                    if (isStillActive) {
                        markReelAsConsumedLocked(fingerprintHash, platform)
                    }
                }
            }
        }
    }

    fun onReelNodeDetected(fingerprintHash: String, platform: String) {
        onReelNodeDetected(
            ReelFingerprintExtractor.ReelDetails(
                hash = fingerprintHash,
                creator = "",
                audioTitle = "",
                captionSnippet = "",
                platform = platform
            )
        )
    }

    /**
     * Called when user scrolls away, leaves the reel player, or navigates back.
     */
    fun onUserNavigatedAway() {
        synchronized(lock) {
            if (currentActiveHash != null || dwellJob != null) {
                Log.d(TAG, "User navigated away from reel. Cancelling dwell job.")
                cancelDwellTimerLocked()
                currentActiveHash = null
                currentActiveCreator = null
                currentActiveCaption = null
                lastAllowedHash = null
            }
        }
    }

    /**
     * Marks a reel as consumed:
     * 1. Adds hash to LRU cache.
     * 2. Persists metadata in repository.
     * 3. Emits updated count to StateFlow.
     * 4. If exactly reaching limit (50/50):
     *      - Give a warning to the user!
     *      - Allow them to finish this video (lastAllowedHash = hash).
     * 5. If limit was already exceeded:
     *      - Eject immediately.
     */
    private fun markReelAsConsumedLocked(hash: String, platform: String) {
        consumedHashes.add(hash)
        val timestamp = System.currentTimeMillis()

        val newCount = quotaManager.recordConsumedReel(
            hash = hash,
            platform = platform,
            timestamp = timestamp
        )

        _sessionReelCount.value = newCount
        Log.i(TAG, "Reel consumed [$platform]: $hash. Daily count is now: $newCount / ${quotaManager.dailyLimit}")

        onCountUpdated(newCount, platform)

        val limit = quotaManager.dailyLimit

        when {
            // Case A: Total Block Mode - Immediate Ejection
            quotaManager.isTotalBlockEnabled -> {
                Log.w(TAG, "Total Block Mode active. Ejecting!")
                onEjectionRequired(platform)
            }

            // Case B: Exactly hit daily limit on this video (e.g. 50/50)!
            // GIVE A WARNING! Do NOT block yet; allow finishing this 50th video.
            limit in 1..newCount && newCount == limit -> {
                Log.w(TAG, "Daily quota of $limit reached on video $hash. Giving warning. Next video will be blocked!")
                lastAllowedHash = hash
                onLimitReachedWarning(platform)
            }

            // Case C: Exceeded limit (51+)
            limit in 1..<newCount -> {
                Log.w(TAG, "Daily quota exceeded ($newCount > $limit). Ejecting!")
                onEjectionRequired(platform)
            }
        }
    }

    private fun cancelDwellTimerLocked() {
        dwellJob?.cancel()
        dwellJob = null
    }

    fun isHashConsumed(hash: String): Boolean {
        synchronized(lock) {
            return consumedHashes.contains(hash)
        }
    }

    fun clearSession() {
        synchronized(lock) {
            cancelDwellTimerLocked()
            currentActiveHash = null
            currentActiveCreator = null
            currentActiveCaption = null
            lastAllowedHash = null
            consumedHashes.clear()
        }
    }
}
