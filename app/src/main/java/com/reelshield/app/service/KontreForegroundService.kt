package com.reelshield.app.service

import android.content.Context

/**
 * Backward compatibility alias for WatcherForegroundService.
 */
class KontreForegroundService : WatcherForegroundService() {

    companion object {
        fun start(context: Context) {
            WatcherForegroundService.start(context)
        }

        fun stop(context: Context) {
            WatcherForegroundService.stop(context)
        }
    }
}
