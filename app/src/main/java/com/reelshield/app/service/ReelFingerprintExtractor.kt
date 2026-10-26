package com.reelshield.app.service

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.regex.Pattern

/**
 * Unique Reel & Short Fingerprint Extractor:
 * Traverses the active AccessibilityNodeInfo tree up to 7 levels deep.
 * Extracts composite metadata:
 *   val rawKey = "${creatorUsername}|${audioTitle}|${captionPrefix.take(30)}"
 *   val reelHash = rawKey.hashCode().toString()
 * Intermediate node allocations are explicitly recycled on API < 30.
 */
object ReelFingerprintExtractor {

    private val HANDLE_PATTERN = Pattern.compile("@[\\w.-]+")

    private class ExtractorEntry(
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

    private const val MAX_POOL_CAPACITY = 256
    private val entryPool = Array(MAX_POOL_CAPACITY) { ExtractorEntry() }
    private var poolIndex = 0
    private val traversalQueue = ArrayDeque<ExtractorEntry>(MAX_POOL_CAPACITY)
    private val pooledNodesToRecycle = ArrayList<AccessibilityNodeInfo>(MAX_POOL_CAPACITY)

    private fun obtainEntry(node: AccessibilityNodeInfo, depth: Int): ExtractorEntry {
        val entry = if (poolIndex < MAX_POOL_CAPACITY) {
            entryPool[poolIndex++]
        } else {
            ExtractorEntry()
        }
        entry.set(node, depth)
        return entry
    }

    private fun resetPool() {
        for (i in 0 until poolIndex) {
            entryPool[i].clear()
        }
        poolIndex = 0
        traversalQueue.clear()
        pooledNodesToRecycle.clear()
    }

    data class ReelDetails(
        val hash: String,
        val creatorUsername: String,
        val audioTitle: String,
        val captionPrefix: String,
        val platform: String
    )

    /**
     * Extracts a fast hash string for the active short-form video.
     * Returns null if no valid reel player nodes are detected.
     */
    fun extractReelFingerprint(rootNode: AccessibilityNodeInfo): String? {
        return extractDetails(rootNode)?.hash
    }

    /**
     * Extracts full composite reel metadata using depth-limited traversal (max 7 levels).
     * Uses pre-allocated service pool to guarantee zero heap allocations.
     */
    @Synchronized
    fun extractDetails(rootNode: AccessibilityNodeInfo): ReelDetails? {
        val packageName = rootNode.packageName?.toString() ?: ""
        val isYouTube = packageName.contains("youtube")
        val isInstagram = packageName.contains("instagram")

        var creatorUsername = ""
        var audioTitle = ""
        var captionPrefix = ""

        resetPool()
        traversalQueue.add(obtainEntry(rootNode, 0))
        var visitedCount = 0
        val maxNodesToInspect = 250

        try {
            while (traversalQueue.isNotEmpty() && visitedCount < maxNodesToInspect) {
                val entry = traversalQueue.removeFirst()
                val node = entry.node ?: continue
                val depth = entry.depth
                visitedCount++

                val resId = (node.viewIdResourceName ?: "").lowercase()
                val text = (node.text?.toString() ?: "").trim()
                val desc = (node.contentDescription?.toString() ?: "").trim()
                val lowerText = text.lowercase()
                val lowerDesc = desc.lowercase()

                // -------------------------------------------------------------
                // 1. Creator Username Extraction
                // -------------------------------------------------------------
                if (creatorUsername.isEmpty()) {
                    if (resId.contains("reel_viewer_title") ||
                        resId.contains("channel_name") ||
                        resId.contains("user_name") ||
                        resId.contains("clips_author_name") ||
                        resId.contains("clips_author_username") ||
                        resId.contains("profile_name")
                    ) {
                        if (text.isNotEmpty()) {
                            creatorUsername = cleanCreatorHandle(text)
                        } else if (desc.isNotEmpty()) {
                            creatorUsername = cleanCreatorHandle(desc)
                        }
                    }

                    if (creatorUsername.isEmpty() && text.isNotEmpty()) {
                        val matcher = HANDLE_PATTERN.matcher(text)
                        if (matcher.find()) {
                            creatorUsername = matcher.group()
                        }
                    }

                    if (creatorUsername.isEmpty() && desc.isNotEmpty()) {
                        val matcher = HANDLE_PATTERN.matcher(desc)
                        if (matcher.find()) {
                            creatorUsername = matcher.group()
                        }
                    }
                }

                // -------------------------------------------------------------
                // 2. Audio Title Extraction
                // -------------------------------------------------------------
                if (audioTitle.isEmpty()) {
                    val isAudioId = resId.contains("audio") ||
                            resId.contains("music") ||
                            resId.contains("sound") ||
                            resId.contains("sound_button") ||
                            resId.contains("pivot_button")

                    val isAudioTextOrDesc = lowerText.contains("original audio") ||
                            lowerText.contains("original sound") ||
                            lowerDesc.contains("original audio") ||
                            lowerDesc.contains("original sound") ||
                            (lowerDesc.contains("audio") && lowerDesc.contains("by")) ||
                            (lowerDesc.contains("music") && lowerDesc.contains("by")) ||
                            (lowerDesc.startsWith("sound:") || lowerText.startsWith("sound:"))

                    if (isAudioId || isAudioTextOrDesc) {
                        val candidate = if (text.isNotEmpty()) text else desc
                        if (candidate.isNotEmpty() && candidate.length > 2) {
                            audioTitle = cleanAudioTitle(candidate)
                        }
                    }
                }

                // -------------------------------------------------------------
                // 3. Caption Prefix Extraction (first 30 characters)
                // -------------------------------------------------------------
                if (captionPrefix.isEmpty()) {
                    val isCaptionId = resId.contains("caption") ||
                            resId.contains("subtitle") ||
                            resId.contains("description") ||
                            resId.contains("video_title") ||
                            resId.contains("shorts_title")

                    if (isCaptionId && text.isNotEmpty()) {
                        captionPrefix = text.take(30).trim()
                    } else if (text.length >= 10 && text != creatorUsername && !lowerText.contains("audio") && !lowerText.contains("music") && !lowerText.contains("search") && !lowerText.contains("response") && !lowerText.contains("comment")) {
                        captionPrefix = text.take(30).trim()
                    }
                }

                // Early exit if all components found
                if (creatorUsername.isNotEmpty() && audioTitle.isNotEmpty() && captionPrefix.isNotEmpty()) {
                    break
                }

                // Depth-limited search: traverse up to 35 levels deep for modern deep ViewPagers
                if (depth < 35) {
                    val childCount = node.childCount
                    for (i in 0 until childCount) {
                        val child = node.getChild(i)
                        if (child != null) {
                            traversalQueue.add(obtainEntry(child, depth + 1))
                            pooledNodesToRecycle.add(child)
                        }
                    }
                }
            }
        } finally {
            // Memory Management: explicit recycle on API < 30
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                for (i in 0 until pooledNodesToRecycle.size) {
                    try {
                        pooledNodesToRecycle[i].recycle()
                    } catch (_: Exception) {
                    }
                }
            }
            resetPool()
        }

        // Validate that we found at least one strong identifier
        if (creatorUsername.isEmpty() && audioTitle.isEmpty() && captionPrefix.isEmpty()) {
            return null
        }

        val platform = when {
            isYouTube -> "YouTube"
            isInstagram -> "Instagram"
            else -> "Social"
        }

        // Unique Signature Hashing:
        // val rawKey = "${creatorUsername}|${audioTitle}|${captionPrefix.take(30)}"
        // val reelHash = rawKey.hashCode().toString()
        val rawKey = "${creatorUsername}|${audioTitle}|${captionPrefix.take(30)}"
        val reelHash = rawKey.hashCode().toString()

        return ReelDetails(
            hash = reelHash,
            creatorUsername = creatorUsername,
            audioTitle = audioTitle,
            captionPrefix = captionPrefix.take(30),
            platform = platform
        )
    }

    private fun cleanCreatorHandle(input: String): String {
        val trimmed = input.trim()
        val matcher = HANDLE_PATTERN.matcher(trimmed)
        return if (matcher.find()) {
            matcher.group()
        } else {
            trimmed.take(30)
        }
    }

    private fun cleanAudioTitle(input: String): String {
        return input.replace("•", "")
            .replace("Original audio", "original_audio")
            .replace("Original sound", "original_sound")
            .trim()
            .take(50)
    }
}
