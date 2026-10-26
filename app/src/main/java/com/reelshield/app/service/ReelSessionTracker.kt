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
 * Thread-safe Reel and Short Session Deduplication & Dwell Debounce Tracker.
 *
 * Rules:
 * - Unique Signature Hashing: rawKey = "${creatorUsername}|${audioTitle}|${captionPrefix.take(30)}" -> reelHash = rawKey.hashCode().toString()
 * - In-memory LRU set (capacity: 50) of consumedHashes.
 * - Dwell Debounce:
 *     - If reelHash == activeReelHash, return immediately (same video).
 *     - If reelHash is already in consumedHashes, set activeReelHash = reelHash but do not start a dwell timer (already counted).
 *     - If genuinely new:
 *         - Cancel existing dwellJob.
 *         - Set activeReelHash = reelHash.
 *         - Launch coroutine with delay(1500L):
 *             - If user scrolls before 1.5s, the job cancels and no count registers.
 *             - If 1.5s completes, call recordReelConsumed(reelHash) and trigger enforcement if daily limit is exceeded.
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
        const val DWELL_THRESHOLD_MS = 1000L
        const val LRU_CAPACITY = 100
    }

    private val lock = Any()

    @Volatile
    var activeReelHash: String? = null
        private set

    // Backward-compatibility alias
    val currentActiveHash: String?
        get() = activeReelHash

    /**
     * Stores the hash of the video that hit the daily limit (e.g. 50th) and was granted
     * a grace warning to finish watching.
     */
    @Volatile
    var lastAllowedHash: String? = null
        private set

    // Thread-safe LRU set of recently consumed hashes with capacity 100
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

    private fun isPlatformBlockEnabled(platform: String): Boolean {
        return when {
            platform.contains("YouTube", ignoreCase = true) -> quotaManager.isYouTubeBlockEnabled
            else -> quotaManager.isInstagramBlockEnabled
        }
    }

    /**
     * Dwell-Gated Counter & Deduplication Pipeline:
     * Evaluates candidate reelHash against activeReelHash and consumedHashes LRU.
     */
    fun onReelNodeDetected(reelHash: String, platform: String = "Instagram") {
        if (reelHash.isBlank()) return

        synchronized(lock) {
            val isBlockEnabled = isPlatformBlockEnabled(platform)

            // Case 1: Total Block Mode (0 allowance) - Eject immediately if platform block is active
            if (quotaManager.isTotalBlockEnabled && isBlockEnabled) {
                Log.w(TAG, "Total Block Mode active. Ejecting immediately!")
                onEjectionRequired(platform)
                return
            }

            // Case 2: Daily quota already reached
            if (quotaManager.isLimitReached && isBlockEnabled) {
                // Allow finishing the video that triggered the warning
                if (lastAllowedHash != null && reelHash == lastAllowedHash) {
                    activeReelHash = reelHash
                    return
                }

                // Swiped to next video or re-opened: Eject immediately!
                Log.w(TAG, "Daily quota reached (${quotaManager.totalCount}/${quotaManager.dailyLimit}). Ejecting!")
                onEjectionRequired(platform)
                return
            }

            // Case 3: Same video already actively playing & dwelling: return immediately
            if (reelHash == activeReelHash) {
                return
            }

            // Case 4: Already in consumedHashes: set activeReelHash = reelHash, do not start dwell timer
            if (consumedHashes.contains(reelHash)) {
                Log.d(TAG, "Reel already in consumedHashes LRU ($reelHash). Setting active without dwell.")
                activeReelHash = reelHash
                cancelDwellTimerLocked()
                return
            }

            // Case 5: Genuinely new: cancel existing dwellJob, set activeReelHash, launch 1000ms debounce
            Log.i(TAG, "New reel detected [$platform]: $reelHash. Launching ${DWELL_THRESHOLD_MS}ms dwell timer.")
            cancelDwellTimerLocked()
            activeReelHash = reelHash

            val targetHash = reelHash
            dwellJob = serviceScope.launch(Dispatchers.Default) {
                delay(DWELL_THRESHOLD_MS)

                synchronized(lock) {
                    if (activeReelHash == targetHash) {
                        Log.i(TAG, "Dwell completed for $targetHash [$platform]. Recording consumed reel!")
                        recordReelConsumed(targetHash, platform)
                    } else {
                        Log.d(TAG, "Dwell aborted: activeReelHash ($activeReelHash) != target ($targetHash)")
                    }
                }
            }
        }
    }

    /**
     * Called when user scrolls away, leaves the reel player, or navigates back.
     * Cancels any active dwell timer to prevent false counts.
     */
    fun onUserNavigatedAway() {
        synchronized(lock) {
            if (activeReelHash != null || dwellJob != null) {
                Log.d(TAG, "User navigated away from reel. Cancelling dwell job.")
                cancelDwellTimerLocked()
                activeReelHash = null
                lastAllowedHash = null
            }
        }
    }

    /**
     * Records reel as consumed, saves to storage, and checks daily limit enforcement.
     */
    fun recordReelConsumed(reelHash: String, platform: String = "Instagram"): Int {
        consumedHashes.add(reelHash)
        val timestamp = System.currentTimeMillis()

        val newCount = quotaManager.recordConsumedReel(
            hash = reelHash,
            platform = platform,
            timestamp = timestamp
        )

        _sessionReelCount.value = newCount
        Log.i(TAG, "Reel consumed [$platform]: $reelHash. Daily count is now: $newCount / ${quotaManager.dailyLimit}")

        onCountUpdated(newCount, platform)

        val limit = quotaManager.dailyLimit
        val isBlockEnabled = isPlatformBlockEnabled(platform)

        if (isBlockEnabled) {
            when {
                // Case A: Total Block Mode - Immediate Ejection
                quotaManager.isTotalBlockEnabled -> {
                    Log.w(TAG, "Total Block Mode active. Ejecting!")
                    onEjectionRequired(platform)
                }

                // Case B: Exactly hit daily limit on this video (e.g. 50/50)!
                // Deliver warning! Allow finishing this 50th video.
                limit in 1..newCount && newCount == limit -> {
                    Log.w(TAG, "Daily quota of $limit reached on video $reelHash. Giving warning. Next video will be blocked!")
                    lastAllowedHash = reelHash
                    onLimitReachedWarning(platform)
                }

                // Case C: Exceeded limit (51+)
                limit in 1..<newCount -> {
                    Log.w(TAG, "Daily quota exceeded ($newCount > $limit). Ejecting!")
                    onEjectionRequired(platform)
                }
            }
        }

        return newCount
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
            activeReelHash = null
            lastAllowedHash = null
            consumedHashes.clear()
        }
    }
}
