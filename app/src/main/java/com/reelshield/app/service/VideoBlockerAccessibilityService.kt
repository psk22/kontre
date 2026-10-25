package com.reelshield.app.service

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Primary Accessibility Service for ReelShield.
 * Inherits the exact real-time short-form tree scanner, 250ms watchdog,
 * and 50-reel blocking enforcement from ContentTrackerService.
 */
class VideoBlockerAccessibilityService : ContentTrackerService() {

    companion object {
        private const val TAG = "VideoBlockerService"

        const val PACKAGE_INSTAGRAM = ContentTrackerService.PACKAGE_INSTAGRAM
        const val PACKAGE_INSTAGRAM_LITE = ContentTrackerService.PACKAGE_INSTAGRAM_LITE
        const val PACKAGE_YOUTUBE = ContentTrackerService.PACKAGE_YOUTUBE

        var instance: VideoBlockerAccessibilityService? = null
            private set

        fun testEjectAction(): Boolean {
            instance?.performGlobalAction(GLOBAL_ACTION_BACK)
            return instance != null
        }
    }

    override fun onServiceConnected() {
        instance = this
        super.onServiceConnected()
        Log.i(TAG, "VideoBlockerAccessibilityService connected and delegating to ContentTrackerService engine.")
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        super.onDestroy()
        Log.i(TAG, "VideoBlockerAccessibilityService destroyed.")
    }
}
