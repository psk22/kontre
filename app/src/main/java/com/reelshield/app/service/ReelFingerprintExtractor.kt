package com.reelshield.app.service

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.regex.Pattern

/**
 * Unique Reel & Short Fingerprint Extractor.
 *
 * Traverses the active AccessibilityNodeInfo tree to extract composite identifiers:
 * 1. Creator Handle (e.g. @username, channel_name, reel_viewer_title)
 * 2. Audio Track Name (e.g. "Original audio", music_title, sound_track)
 * 3. Caption Snippet (first 40 characters of the primary video description)
 *
 * Computes a SHA-256 fingerprint hash of "$creator|$audioTitle|$captionSnippet".
 * Intermediate AccessibilityNodeInfo instances are safely recycled on API < 30.
 */
object ReelFingerprintExtractor {

    private val HANDLE_PATTERN = Pattern.compile("@[\\w.-]+")

    data class ReelDetails(
        val hash: String,
        val creator: String,
        val audioTitle: String,
        val captionSnippet: String,
        val platform: String
    )

    /**
     * Extracts a SHA-256 fingerprint hash for the active short-form video.
     * Returns null if no valid reel player nodes are detected.
     */
    fun extractReelFingerprint(rootNode: AccessibilityNodeInfo): String? {
        return extractDetails(rootNode)?.hash
    }

    /**
     * Extracts full composite reel metadata.
     * Returns null if the view does not contain a valid short-form video player.
     */
    fun extractDetails(rootNode: AccessibilityNodeInfo): ReelDetails? {
        val packageName = rootNode.packageName?.toString() ?: ""
        val isYouTube = packageName.contains("youtube")
        val isInstagram = packageName.contains("instagram")

        var creatorHandle = ""
        var audioTrackName = ""
        var captionSnippet = ""

        val nodesToRecycle = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(rootNode) }
        var visitedCount = 0
        val maxNodesToInspect = 120

        try {
            while (queue.isNotEmpty() && visitedCount < maxNodesToInspect) {
                val node = queue.removeFirst()
                visitedCount++

                val resId = (node.viewIdResourceName ?: "").lowercase()
                val text = (node.text?.toString() ?: "").trim()
                val desc = (node.contentDescription?.toString() ?: "").trim()
                val lowerText = text.lowercase()
                val lowerDesc = desc.lowercase()

                // -------------------------------------------------------------
                // 1. Creator Handle Extraction
                // -------------------------------------------------------------
                if (creatorHandle.isEmpty()) {
                    if (resId.contains("reel_viewer_title") ||
                        resId.contains("channel_name") ||
                        resId.contains("user_name") ||
                        resId.contains("clips_author_name") ||
                        resId.contains("profile_name")
                    ) {
                        if (text.isNotEmpty()) {
                            creatorHandle = cleanCreatorHandle(text)
                        } else if (desc.isNotEmpty()) {
                            creatorHandle = cleanCreatorHandle(desc)
                        }
                    }

                    if (creatorHandle.isEmpty() && text.isNotEmpty()) {
                        val matcher = HANDLE_PATTERN.matcher(text)
                        if (matcher.find()) {
                            creatorHandle = matcher.group()
                        }
                    }

                    if (creatorHandle.isEmpty() && desc.isNotEmpty()) {
                        val matcher = HANDLE_PATTERN.matcher(desc)
                        if (matcher.find()) {
                            creatorHandle = matcher.group()
                        }
                    }
                }

                // -------------------------------------------------------------
                // 2. Audio Track Name Extraction
                // -------------------------------------------------------------
                if (audioTrackName.isEmpty()) {
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
                            audioTrackName = cleanAudioTitle(candidate)
                        }
                    }
                }

                // -------------------------------------------------------------
                // 3. Caption Snippet Extraction (First 40 characters)
                // -------------------------------------------------------------
                if (captionSnippet.isEmpty()) {
                    val isCaptionId = resId.contains("caption") ||
                            resId.contains("subtitle") ||
                            resId.contains("description") ||
                            resId.contains("video_title") ||
                            resId.contains("shorts_title")

                    if (isCaptionId && text.isNotEmpty()) {
                        captionSnippet = text.take(40).trim()
                    } else if (text.length >= 15 && text != creatorHandle && !lowerText.contains("audio") && !lowerText.contains("music")) {
                        // Candidate descriptive text associated with video
                        captionSnippet = text.take(40).trim()
                    }
                }

                // If all 3 components are found, we have a complete fingerprint
                if (creatorHandle.isNotEmpty() && audioTrackName.isNotEmpty() && captionSnippet.isNotEmpty()) {
                    break
                }

                // Enqueue children safely
                val childCount = node.childCount
                for (i in 0 until childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        queue.add(child)
                        nodesToRecycle.add(child)
                    }
                }
            }
        } finally {
            // Memory Management: explicit recycle on API < 30
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                for (node in nodesToRecycle) {
                    try {
                        node.recycle()
                    } catch (_: Exception) {
                        // Ignore already recycled nodes safely
                    }
                }
            }
        }

        // Validate that we found at least one strong unique identifier
        if (creatorHandle.isEmpty() && audioTrackName.isEmpty() && captionSnippet.isEmpty()) {
            return null
        }

        val platform = when {
            isYouTube -> "YouTube"
            isInstagram -> "Instagram"
            else -> "Social"
        }

        // Composite hash payload: "$creator|$audioTitle|$captionSnippet"
        val payload = "$creatorHandle|$audioTrackName|$captionSnippet"
        val hash = sha256(payload)

        return ReelDetails(
            hash = hash,
            creator = creatorHandle,
            audioTitle = audioTrackName,
            captionSnippet = captionSnippet,
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

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
