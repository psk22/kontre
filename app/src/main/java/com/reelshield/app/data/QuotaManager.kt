package com.reelshield.app.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class QuotaManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("reelshield_prefs", Context.MODE_PRIVATE)

    companion object {
        const val DEFAULT_DAILY_LIMIT = 50

        private const val KEY_LAST_DATE = "key_last_date"
        private const val KEY_REELS_COUNT = "key_reels_count"
        private const val KEY_SHORTS_COUNT = "key_shorts_count"
        private const val KEY_BLOCKED_COUNT = "key_blocked_count"
        private const val KEY_DAILY_LIMIT = "key_daily_limit"

        private const val KEY_BLOCK_INSTAGRAM = "key_block_instagram"
        private const val KEY_BLOCK_YOUTUBE = "key_block_youtube"
        private const val KEY_STRICT_MODE = "key_strict_mode"
        private const val KEY_TOTAL_BLOCK = "key_total_block"
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private fun getTodayDateString(): String {
        return dateFormat.format(Date())
    }

    @Synchronized
    fun checkAndResetDaily() {
        val today = getTodayDateString()
        val lastSavedDate = prefs.getString(KEY_LAST_DATE, "")

        if (today != lastSavedDate) {
            // New calendar day: reset counters to 0
            prefs.edit()
                .putString(KEY_LAST_DATE, today)
                .putInt(KEY_REELS_COUNT, 0)
                .putInt(KEY_SHORTS_COUNT, 0)
                .putInt(KEY_BLOCKED_COUNT, 0)
                .apply()
        }
    }

    // --- Settings Toggles ---
    var isInstagramBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_INSTAGRAM, true)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_INSTAGRAM, value).apply()

    var isYouTubeBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_YOUTUBE, true)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_YOUTUBE, value).apply()

    var isStrictMode: Boolean
        get() = true
        set(_) {}

    var isTotalBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_TOTAL_BLOCK, false)
        set(value) = prefs.edit().putBoolean(KEY_TOTAL_BLOCK, value).apply()

    // --- Daily Limit & Usage ---
    val dailyLimit: Int
        get() = if (isTotalBlockEnabled) 0 else prefs.getInt(KEY_DAILY_LIMIT, DEFAULT_DAILY_LIMIT)

    fun setDailyLimit(limit: Int) {
        prefs.edit().putInt(KEY_DAILY_LIMIT, limit).apply()
    }

    val reelsCount: Int
        get() {
            checkAndResetDaily()
            return prefs.getInt(KEY_REELS_COUNT, 0)
        }

    val shortsCount: Int
        get() {
            checkAndResetDaily()
            return prefs.getInt(KEY_SHORTS_COUNT, 0)
        }

    val blockedTodayCount: Int
        get() {
            checkAndResetDaily()
            return prefs.getInt(KEY_BLOCKED_COUNT, 0)
        }

    val timeSavedMinutes: Int
        get() = (blockedTodayCount * 45) / 60

    val totalCount: Int
        get() = reelsCount + shortsCount

    val isLimitReached: Boolean
        get() = if (isTotalBlockEnabled) true else totalCount >= dailyLimit

    @Synchronized
    fun incrementReels(): Int {
        checkAndResetDaily()
        val newCount = reelsCount + 1
        prefs.edit().putInt(KEY_REELS_COUNT, newCount).apply()
        return totalCount
    }

    @Synchronized
    fun incrementShorts(): Int {
        checkAndResetDaily()
        val newCount = shortsCount + 1
        prefs.edit().putInt(KEY_SHORTS_COUNT, newCount).apply()
        return totalCount
    }

    @Synchronized
    fun incrementBlockedCount(): Int {
        checkAndResetDaily()
        val count = blockedTodayCount + 1
        prefs.edit().putInt(KEY_BLOCKED_COUNT, count).apply()
        return count
    }

    fun getTimeUntilMidnightMillis(): Long {
        val now = Calendar.getInstance()
        val midnight = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diff = midnight.timeInMillis - now.timeInMillis
        return if (diff > 0) diff else 0L
    }

    fun getTimeUntilMidnightFormatted(): String {
        val remainingMs = getTimeUntilMidnightMillis()
        val totalSecs = remainingMs / 1000
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        return String.format(Locale.US, "%02dh %02dm %02ds", hours, minutes, seconds)
    }
}
