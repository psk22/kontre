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
import java.util.Collections
import java.util.LinkedHashSet

/**
 * Thread-safe Reel and Short Session Deduplication & Dwell Tracker.
 *
 * Enforces:
 * - 1500ms dwell threshold before a video is marked as watched.
 * - LRU cache (capacity: 50) of consumed video hashes to prevent double-counting
 *   when users swipe up and down across recently viewed reels.
 * - Cancellation on scroll away, app switch, or navigation away.
 * - Persistence & ejection callback when daily quota is reached.
 */
class ReelSessionTracker(
    private val serviceScope: CoroutineScope,
    private val quotaManager: QuotaManager,
    private val onCountUpdated: (newCount: Int, platform: String) -> Unit,
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
     * Manages deduplication and initiates the 1500ms dwell countdown.
     */
    fun onReelNodeDetected(fingerprintHash: String, platform: String) {
        synchronized(lock) {
            // 1. Same video already actively playing & dwelling: ignore UI spam
            if (fingerprintHash == currentActiveHash) {
                return
            }

            // 2. Previously consumed video (user swiped back to an already counted reel)
            if (consumedHashes.contains(fingerprintHash)) {
                Log.d(TAG, "Reel already consumed in LRU cache ($fingerprintHash). Updating active hash without dwell.")
                currentActiveHash = fingerprintHash
                cancelDwellTimerLocked()
                return
            }

            // 3. Genuinely new video: cancel previous dwell and start fresh 1500ms timer
            Log.d(TAG, "New reel detected ($fingerprintHash) on $platform. Starting 1500ms dwell countdown.")
            cancelDwellTimerLocked()
            currentActiveHash = fingerprintHash

            dwellJob = serviceScope.launch(Dispatchers.Default) {
                delay(DWELL_THRESHOLD_MS)

                // Check that the video is still active when timer finishes
                synchronized(lock) {
                    if (currentActiveHash == fingerprintHash) {
                        markReelAsConsumedLocked(fingerprintHash, platform)
                    }
                }
            }
        }
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
            }
        }
    }

    /**
     * Marks a reel as consumed:
     * 1. Adds hash to LRU cache.
     * 2. Persists metadata (timestamp, platform, hash) in repository.
     * 3. Emits updated count to StateFlow.
     * 4. Evaluates daily quota and triggers ejection hook if limit is reached.
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

        // Ejection Hook: Check against configured user limits
        if (quotaManager.isLimitReached || quotaManager.isStrictMode) {
            Log.w(TAG, "Daily allowance reached ($newCount). Triggering ejection hook!")
            onEjectionRequired(platform)
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
            consumedHashes.clear()
        }
    }
}
