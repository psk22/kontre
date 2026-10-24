package com.reelshield.app.service

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import java.security.MessageDigest
import java.util.ArrayDeque
import java.util.regex.Pattern

/**
 * Unique Reel & Short Fingerprint Extractor.
 *
 * Traverses the active AccessibilityNodeInfo tree to extract robust composite identifiers:
 * 1. Creator Handle (e.g. from Accessibility descriptions, @handles, author IDs, or avatar buttons)
 * 2. Caption Snippet (first 40 characters of the primary video description)
 * 3. Audio Track Name (normalized and stripped of marquee rotation)
 *
 * Designed to be completely immune to scrolling marquee text, live like counters, and UI re-renders.
 */
object ReelFingerprintExtractor {

    private val HANDLE_PATTERN = Pattern.compile("@[\\w.-]+")
    private val REEL_BY_PATTERN = Pattern.compile("(?:reel by|reel from|video by)\\s+@?([\\w.-]+)", Pattern.CASE_INSENSITIVE)
    private val PROFILE_OF_PATTERN = Pattern.compile("(?:profile picture of|open profile for)\\s+@?([\\w.-]+)", Pattern.CASE_INSENSITIVE)
    private val CHANNEL_PATTERN = Pattern.compile("(?:go to channel|channel)\\s+@?([\\w.-]+)", Pattern.CASE_INSENSITIVE)

    data class ReelDetails(
        val hash: String,
        val creator: String,
        val audioTitle: String,
        val captionSnippet: String,
        val platform: String
    )

    /**
     * Extracts a SHA-256 fingerprint hash for the active short-form video.
     */
    fun extractReelFingerprint(rootNode: AccessibilityNodeInfo): String? {
        return extractDetails(rootNode)?.hash
    }

    /**
     * Extracts full composite reel metadata.
     */
    fun extractDetails(rootNode: AccessibilityNodeInfo): ReelDetails? {
        val packageName = rootNode.packageName?.toString() ?: ""
        val isYouTube = packageName.contains("youtube")
        val isInstagram = packageName.contains("instagram")

        var creatorHandle = ""
        var audioTrackName = ""
        var captionSnippet = ""
        var fallbackIdentifier = ""

        val nodesToRecycle = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayDeque<AccessibilityNodeInfo>().apply { add(rootNode) }
        var visitedCount = 0
        val maxNodesToInspect = 250

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
                    // Pattern A: "Reel by [username]"
                    val reelByMatch = REEL_BY_PATTERN.matcher(desc)
                    if (reelByMatch.find()) {
                        creatorHandle = "@" + reelByMatch.group(1)!!.removePrefix("@")
                    }

                    // Pattern B: "Profile picture of [username]"
                    if (creatorHandle.isEmpty()) {
                        val profileMatch = PROFILE_OF_PATTERN.matcher(desc)
                        if (profileMatch.find()) {
                            creatorHandle = "@" + profileMatch.group(1)!!.removePrefix("@")
                        }
                    }

                    // Pattern C: "Go to channel [name]" in YouTube
                    if (creatorHandle.isEmpty()) {
                        val channelMatch = CHANNEL_PATTERN.matcher(desc)
                        if (channelMatch.find()) {
                            creatorHandle = "@" + channelMatch.group(1)!!.removePrefix("@")
                        }
                    }

                    // Pattern D: Explicit Handle with @
                    if (creatorHandle.isEmpty() && text.isNotEmpty()) {
                        val matcher = HANDLE_PATTERN.matcher(text)
                        if (matcher.find()) {
                            creatorHandle = matcher.group()
                        }
                    }

                    // Pattern E: Known Author View IDs
                    if (creatorHandle.isEmpty()) {
                        val isAuthorId = resId.contains("clips_author") ||
                                resId.contains("clips_user") ||
                                resId.contains("channel_name") ||
                                resId.contains("text_channel_name") ||
                                resId.contains("user_name") ||
                                resId.contains("profile_name")

                        if (isAuthorId) {
                            val candidate = if (text.isNotEmpty()) text else desc
                            if (candidate.isNotEmpty() && candidate.length in 2..35 && !isCommonUiButton(candidate)) {
                                creatorHandle = "@" + candidate.removePrefix("@").trim()
                            }
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
                            resId.contains("pivot_button") ||
                            resId.contains("clips_audio_mix_button")

                    val isAudioTextOrDesc = lowerText.contains("original audio") ||
                            lowerText.contains("original sound") ||
                            lowerDesc.contains("original audio") ||
                            lowerDesc.contains("original sound") ||
                            (lowerDesc.contains("audio") && lowerDesc.contains("by")) ||
                            (lowerDesc.contains("music") && lowerDesc.contains("by"))

                    if (isAudioId || isAudioTextOrDesc) {
                        val candidate = if (text.isNotEmpty()) text else desc
                        if (candidate.isNotEmpty() && candidate.length > 2) {
                            audioTrackName = cleanAudioTitle(candidate)
                        }
                    }
                }

                // -------------------------------------------------------------
                // 3. Caption Snippet Extraction
                // -------------------------------------------------------------
                if (captionSnippet.isEmpty()) {
                    val isCaptionId = resId.contains("caption") ||
                            resId.contains("subtitle") ||
                            resId.contains("description") ||
                            resId.contains("video_title") ||
                            resId.contains("shorts_title") ||
                            resId.contains("title")

                    if (isCaptionId && text.isNotEmpty()) {
                        captionSnippet = cleanCaption(text)
                    } else if (text.length in 6..120 && text != creatorHandle && !isCommonUiButton(text) && !lowerText.contains("audio") && !lowerText.contains("sound")) {
                        captionSnippet = cleanCaption(text)
                    }
                }

                // -------------------------------------------------------------
                // 4. Fallback Identifier (Like count, comment count)
                // -------------------------------------------------------------
                if (fallbackIdentifier.isEmpty()) {
                    if (lowerDesc.contains("like") || lowerDesc.contains("comment") || resId.contains("like") || resId.contains("comment")) {
                        if (desc.length > 5) fallbackIdentifier = desc
                    }
                }

                // Early exit if we found strong identifiers
                if (creatorHandle.isNotEmpty() && captionSnippet.isNotEmpty()) {
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
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                for (node in nodesToRecycle) {
                    try {
                        node.recycle()
                    } catch (_: Exception) {}
                }
            }
        }

        // Validate that we found at least one identifier
        if (creatorHandle.isEmpty() && audioTrackName.isEmpty() && captionSnippet.isEmpty() && fallbackIdentifier.isEmpty()) {
            return null
        }

        val platform = when {
            isYouTube -> "YouTube"
            isInstagram -> "Instagram"
            else -> "Social"
        }

        // Stable Composite Payload:
        // By relying on Creator + Caption first, marquee audio scrolling cannot corrupt the hash!
        val payload = when {
            creatorHandle.isNotEmpty() && captionSnippet.isNotEmpty() ->
                "$creatorHandle|$captionSnippet"
            creatorHandle.isNotEmpty() && audioTrackName.isNotEmpty() ->
                "$creatorHandle|$audioTrackName"
            creatorHandle.isNotEmpty() ->
                creatorHandle
            captionSnippet.isNotEmpty() ->
                "caption|$captionSnippet"
            audioTrackName.isNotEmpty() ->
                "audio|$audioTrackName"
            else ->
                "fallback|$fallbackIdentifier"
        }

        val hash = sha256("$platform|$payload")

        return ReelDetails(
            hash = hash,
            creator = creatorHandle,
            audioTitle = audioTrackName,
            captionSnippet = captionSnippet,
            platform = platform
        )
    }

    private fun isCommonUiButton(text: String): Boolean {
        val lower = text.trim().lowercase()
        return lower in listOf(
            "follow", "following", "like", "liked", "comment", "share", "remix", "save",
            "saved", "send", "subscribe", "subscribed", "sponsored", "suggested for you",
            "reply", "view comments", "double tap to like", "more", "audio", "original audio"
        )
    }

    private fun cleanCaption(input: String): String {
        return input.replace("\\s+".toRegex(), " ")
            .trim()
            .take(40)
    }

    private fun cleanAudioTitle(input: String): String {
        // Remove marquee text repetitions (strip anything after • or -)
        val primary = input.split("•", "-")[0].trim()
        return primary.replace("Original audio", "")
            .replace("Original sound", "")
            .replace("original_audio", "")
            .trim()
            .take(25)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
