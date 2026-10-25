package com.reelshield.app.data.rules

import org.json.JSONArray
import org.json.JSONObject

/**
 * Platform-specific dynamic heuristic rules loaded locally or refreshed remotely.
 */
data class AppHeuristicRule(
    val fuzzyResourceIds: List<String>,
    val labelKeywords: List<String>,
    val actionRailKeywords: List<String>,
    val containerClasses: List<String>,
    val minCoverageFraction: Float = 0.90f,
    val actionRailRightThresholdFraction: Float = 0.70f,
    val minActionButtons: Int = 2,
    val storyViewIds: List<String> = emptyList(),
    val dmViewIds: List<String> = emptyList()
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("fuzzyResourceIds", JSONArray(fuzzyResourceIds))
            put("labelKeywords", JSONArray(labelKeywords))
            put("actionRailKeywords", JSONArray(actionRailKeywords))
            put("containerClasses", JSONArray(containerClasses))
            put("minCoverageFraction", minCoverageFraction.toDouble())
            put("actionRailRightThresholdFraction", actionRailRightThresholdFraction.toDouble())
            put("minActionButtons", minActionButtons)
            put("storyViewIds", JSONArray(storyViewIds))
            put("dmViewIds", JSONArray(dmViewIds))
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject, default: AppHeuristicRule): AppHeuristicRule {
            return try {
                AppHeuristicRule(
                    fuzzyResourceIds = obj.optJsonArrayToStringList("fuzzyResourceIds", default.fuzzyResourceIds),
                    labelKeywords = obj.optJsonArrayToStringList("labelKeywords", default.labelKeywords),
                    actionRailKeywords = obj.optJsonArrayToStringList("actionRailKeywords", default.actionRailKeywords),
                    containerClasses = obj.optJsonArrayToStringList("containerClasses", default.containerClasses),
                    minCoverageFraction = obj.optDouble("minCoverageFraction", default.minCoverageFraction.toDouble()).toFloat(),
                    actionRailRightThresholdFraction = obj.optDouble("actionRailRightThresholdFraction", default.actionRailRightThresholdFraction.toDouble()).toFloat(),
                    minActionButtons = obj.optInt("minActionButtons", default.minActionButtons),
                    storyViewIds = obj.optJsonArrayToStringList("storyViewIds", default.storyViewIds),
                    dmViewIds = obj.optJsonArrayToStringList("dmViewIds", default.dmViewIds)
                )
            } catch (_: Exception) {
                default
            }
        }

        fun defaultInstagramRules(): AppHeuristicRule {
            return AppHeuristicRule(
                fuzzyResourceIds = listOf(
                    "reel", "clip", "reel_viewer", "clips_viewer",
                    "clips_viewer_view_pager", "clips_viewer_container", "root_clips_layout",
                    "clips_video_container", "clips_swipe_refresh_container", "clips_item_container",
                    "clips_media_component", "clips_viewer_media_container"
                ),
                labelKeywords = listOf("reels", "clips", "original audio", "sound", "remix"),
                actionRailKeywords = listOf("action", "like", "comment", "share", "audio", "pivot"),
                containerClasses = listOf("ViewPager2", "RecyclerView", "view_pager", "clips_viewer", "clips_video_container"),
                minCoverageFraction = 0.90f,
                actionRailRightThresholdFraction = 0.70f,
                minActionButtons = 2,
                storyViewIds = listOf(
                    "com.instagram.android:id/story_progress",
                    "com.instagram.android:id/stories_progress_bar",
                    "com.instagram.android:id/segment_progress_bar",
                    "com.instagram.android:id/reel_viewer_progress_bar",
                    "com.instagram.android:id/reel_viewer_header",
                    "com.instagram.android:id/reel_viewer_view_pager",
                    "com.instagram.android:id/reel_viewer_root",
                    "com.instagram.android:id/reel_viewer_container",
                    "com.instagram.android:id/story_item",
                    "com.instagram.android:id/story_viewer_reply",
                    "com.instagram.android:id/story_reply_composer"
                ),
                dmViewIds = listOf(
                    "com.instagram.android:id/direct_message_list",
                    "com.instagram.android:id/row_thread_composer",
                    "com.instagram.android:id/direct_thread_header",
                    "com.instagram.android:id/direct_thread_composer",
                    "com.instagram.android:id/message_composer",
                    "com.instagram.android:id/direct_inbox",
                    "com.instagram.android:id/inbox_recycler_view",
                    "com.instagram.android:id/direct_thread_view"
                )
            )
        }

        fun defaultYouTubeRules(): AppHeuristicRule {
            return AppHeuristicRule(
                fuzzyResourceIds = listOf(
                    "reel_recycler", "reel_player", "shorts_player",
                    "shorts_container", "shorts_pager", "shorts_video"
                ),
                labelKeywords = listOf("shorts", "sound", "remix", "original sound"),
                actionRailKeywords = listOf("action", "like", "dislike", "comment", "share", "remix"),
                containerClasses = listOf("ViewPager2", "RecyclerView", "reel_recycler"),
                minCoverageFraction = 0.90f,
                actionRailRightThresholdFraction = 0.70f,
                minActionButtons = 2
            )
        }
    }
}

/**
 * Global detection rules, including video surface detection and tree limits.
 */
data class GlobalHeuristicRule(
    val videoSurfaceClasses: List<String> = listOf("SurfaceView", "TextureView", "PlayerView", "VideoView"),
    val targetAspectRatio: Float = 1.777f, // Vertical 9:16 portrait video (16 / 9)
    val aspectRatioTolerance: Float = 0.12f, // Occupying 9:16 aspect ratios (1.77 ± 0.1)
    val minVideoHeightFraction: Float = 0.50f,
    val maxTreeDepth: Int = 7,
    val maxNodesInspected: Int = 100
) {
    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("videoSurfaceClasses", JSONArray(videoSurfaceClasses))
            put("targetAspectRatio", targetAspectRatio.toDouble())
            put("aspectRatioTolerance", aspectRatioTolerance.toDouble())
            put("minVideoHeightFraction", minVideoHeightFraction.toDouble())
            put("maxTreeDepth", maxTreeDepth)
            put("maxNodesInspected", maxNodesInspected)
        }
    }

    companion object {
        fun fromJsonObject(obj: JSONObject, default: GlobalHeuristicRule): GlobalHeuristicRule {
            return try {
                GlobalHeuristicRule(
                    videoSurfaceClasses = obj.optJsonArrayToStringList("videoSurfaceClasses", default.videoSurfaceClasses),
                    targetAspectRatio = obj.optDouble("targetAspectRatio", default.targetAspectRatio.toDouble()).toFloat(),
                    aspectRatioTolerance = obj.optDouble("aspectRatioTolerance", default.aspectRatioTolerance.toDouble()).toFloat(),
                    minVideoHeightFraction = obj.optDouble("minVideoHeightFraction", default.minVideoHeightFraction.toDouble()).toFloat(),
                    maxTreeDepth = obj.optInt("maxTreeDepth", default.maxTreeDepth),
                    maxNodesInspected = obj.optInt("maxNodesInspected", default.maxNodesInspected)
                )
            } catch (_: Exception) {
                default
            }
        }

        fun defaultGlobalRules(): GlobalHeuristicRule = GlobalHeuristicRule()
    }
}

/**
 * Top-level container for all dynamic heuristic rules.
 */
data class DynamicHeuristicRules(
    val version: Int = 1,
    val lastUpdated: Long = System.currentTimeMillis(),
    val instagramRules: AppHeuristicRule = AppHeuristicRule.defaultInstagramRules(),
    val youtubeRules: AppHeuristicRule = AppHeuristicRule.defaultYouTubeRules(),
    val globalRules: GlobalHeuristicRule = GlobalHeuristicRule.defaultGlobalRules()
) {
    fun toJson(): String {
        return JSONObject().apply {
            put("version", version)
            put("lastUpdated", lastUpdated)
            put("instagram", instagramRules.toJsonObject())
            put("youtube", youtubeRules.toJsonObject())
            put("global", globalRules.toJsonObject())
        }.toString(2)
    }

    companion object {
        fun defaultRules(): DynamicHeuristicRules = DynamicHeuristicRules()

        fun fromJson(jsonStr: String): DynamicHeuristicRules {
            return try {
                val obj = JSONObject(jsonStr)
                val defaults = defaultRules()
                DynamicHeuristicRules(
                    version = obj.optInt("version", defaults.version),
                    lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis()),
                    instagramRules = if (obj.has("instagram")) AppHeuristicRule.fromJsonObject(obj.getJSONObject("instagram"), defaults.instagramRules) else defaults.instagramRules,
                    youtubeRules = if (obj.has("youtube")) AppHeuristicRule.fromJsonObject(obj.getJSONObject("youtube"), defaults.youtubeRules) else defaults.youtubeRules,
                    globalRules = if (obj.has("global")) GlobalHeuristicRule.fromJsonObject(obj.getJSONObject("global"), defaults.globalRules) else defaults.globalRules
                )
            } catch (e: Exception) {
                android.util.Log.e("DynamicHeuristicRules", "Failed to parse rules JSON, using defaults", e)
                defaultRules()
            }
        }
    }
}

private fun JSONObject.optJsonArrayToStringList(key: String, default: List<String>): List<String> {
    val arr = optJSONArray(key) ?: return default
    val list = ArrayList<String>(arr.length())
    for (i in 0 until arr.length()) {
        val item = arr.optString(i, "")
        if (item.isNotEmpty()) {
            list.add(item)
        }
    }
    return if (list.isNotEmpty()) list else default
}
