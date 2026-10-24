package com.reelshield.app.data.rules

import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

/**
 * Dynamic Heuristic Rule Engine:
 * - Maintains active in-memory rules with reactive StateFlow.
 * - Backed by AES-256 encrypted local cache (EncryptedRuleCache).
 * - Periodically or on-demand fetches updated rules over HTTPS from remote repository/endpoint.
 * - Provides active 9:16 video surface evaluation (SurfaceView, TextureView, PlayerView).
 */
class DynamicRuleEngine private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val encryptedCache = EncryptedRuleCache(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeRulesFlow = MutableStateFlow(loadInitialRules())
    val activeRulesFlow: StateFlow<DynamicHeuristicRules> = _activeRulesFlow.asStateFlow()

    val activeRules: DynamicHeuristicRules
        get() = _activeRulesFlow.value

    companion object {
        private const val TAG = "DynamicRuleEngine"
        const val DEFAULT_RULES_URL = "https://raw.githubusercontent.com/psk22/kontre/main/rules/heuristic_rules.json"

        @Volatile
        private var instance: DynamicRuleEngine? = null

        fun getInstance(context: Context): DynamicRuleEngine {
            return instance ?: synchronized(this) {
                instance ?: DynamicRuleEngine(context).also { instance = it }
            }
        }
    }

    private fun loadInitialRules(): DynamicHeuristicRules {
        val cached = encryptedCache.loadRules()
        if (cached != null) {
            Log.i(TAG, "Loaded dynamic rules v${cached.version} from encrypted cache.")
            return cached
        }
        val defaultRules = DynamicHeuristicRules.defaultRules()
        encryptedCache.saveRules(defaultRules)
        Log.i(TAG, "Initialized default dynamic rules v${defaultRules.version}.")
        return defaultRules
    }

    /**
     * Refreshes rules from remote HTTPS endpoint asynchronously.
     * Encrypts and persists to local cache on success.
     */
    suspend fun fetchRemoteRules(
        endpointUrl: String = DEFAULT_RULES_URL
    ): Result<DynamicHeuristicRules> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            Log.i(TAG, "Fetching dynamic heuristic rules from: $endpointUrl")
            val url = URL(endpointUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Kontre-RuleEngine/1.2")
                instanceFollowRedirects = true
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                return@withContext Result.failure(Exception("HTTP error $responseCode"))
            }

            val jsonString = connection.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            }

            if (jsonString.isBlank()) {
                return@withContext Result.failure(Exception("Received empty rule response"))
            }

            val newRules = DynamicHeuristicRules.fromJson(jsonString)
            // Persist encrypted locally
            encryptedCache.saveRules(newRules)
            _activeRulesFlow.value = newRules
            Log.i(TAG, "Successfully updated dynamic rules to v${newRules.version} from remote.")
            Result.success(newRules)
        } catch (e: Exception) {
            Log.w(TAG, "Remote rule sync failed, continuing with current rules v${activeRules.version}: ${e.message}")
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Triggers asynchronous background rule update if network is available.
     */
    fun triggerBackgroundSync() {
        scope.launch {
            fetchRemoteRules()
        }
    }

    /**
     * Aspect Ratio & Video Surface Detection:
     * Checks if a node's class name matches SurfaceView, TextureView, PlayerView, or VideoView
     * and occupies a vertical 9:16 aspect ratio (1.77 ± 0.1).
     *
     * Short-form immersive videos run inside a full-screen vertical container with an active
     * rendering surface whose height-to-width ratio is ~16/9 = 1.777.
     */
    fun isVertical9by16VideoSurface(
        node: AccessibilityNodeInfo,
        screenWidth: Int,
        screenHeight: Int
    ): Boolean {
        if (!node.isVisibleToUser) return false

        val className = node.className?.toString() ?: return false
        val globalRules = activeRules.globalRules

        // 1. Check node class names for SurfaceView, TextureView, PlayerView, or VideoView
        val isVideoSurfaceClass = globalRules.videoSurfaceClasses.any { targetClass ->
            className.contains(targetClass, ignoreCase = true)
        }

        if (!isVideoSurfaceClass) return false

        // 2. Measure bounds in screen coordinates
        val rect = Rect()
        node.getBoundsInScreen(rect)
        if (rect.isEmpty || rect.width() <= 0 || rect.height() <= 0) return false

        val width = rect.width().toFloat()
        val height = rect.height().toFloat()

        // 3. Compute aspect ratio (Height / Width for vertical 9:16 video)
        val aspectRatio = height / width
        val targetRatio = globalRules.targetAspectRatio // 1.777f (16/9)
        val tolerance = globalRules.aspectRatioTolerance // 0.12f (~1.65 to 1.90)

        val is9by16 = abs(aspectRatio - targetRatio) <= tolerance

        // 4. Ensure it covers significant screen height (not a tiny preview thumbnail)
        val coversRequiredHeight = height >= (screenHeight * globalRules.minVideoHeightFraction)

        val matches = is9by16 && coversRequiredHeight
        if (matches) {
            Log.d(TAG, "Detected active 9:16 video surface: class=$className, ratio=$aspectRatio (bounds: $rect)")
        }
        return matches
    }

    /**
     * Returns true if target node viewId or text matches any fuzzy reel keywords.
     */
    fun matchesFuzzyResourceId(resId: String, isInstagram: Boolean): Boolean {
        if (resId.isEmpty()) return false
        val lowerId = resId.lowercase()
        val rules = if (isInstagram) activeRules.instagramRules else activeRules.youtubeRules
        return rules.fuzzyResourceIds.any { keyword -> lowerId.contains(keyword) }
    }

    /**
     * Returns true if label or description contains short-form keywords.
     */
    fun matchesLabelKeyword(text: String, desc: String, isInstagram: Boolean): Boolean {
        val lowerText = text.lowercase()
        val lowerDesc = desc.lowercase()
        val rules = if (isInstagram) activeRules.instagramRules else activeRules.youtubeRules
        return rules.labelKeywords.any { kw ->
            (lowerText.isNotEmpty() && lowerText.contains(kw)) ||
                    (lowerDesc.isNotEmpty() && lowerDesc.contains(kw))
        }
    }
}
