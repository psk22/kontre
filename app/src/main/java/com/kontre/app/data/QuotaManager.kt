package com.kontre.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class QuotaManager(context: Context) {

    private val prefs: SharedPreferences = run {
        val newPrefs = context.getSharedPreferences("kontre_prefs", Context.MODE_PRIVATE)
        val oldPrefs = context.getSharedPreferences("reelshield_prefs", Context.MODE_PRIVATE)
        if (oldPrefs.all.isNotEmpty() && newPrefs.all.isEmpty()) {
            val editor = newPrefs.edit()
            oldPrefs.all.forEach { (k, v) ->
                when (v) {
                    is Boolean -> editor.putBoolean(k, v)
                    is Int -> editor.putInt(k, v)
                    is Long -> editor.putLong(k, v)
                    is Float -> editor.putFloat(k, v)
                    is String -> editor.putString(k, v)
                }
            }
            editor.apply()
        }
        newPrefs
    }

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
        const val KEY_SHOW_FLOATING_COUNTER = "key_show_floating_counter"

        private const val KEY_CURRENT_STREAK = "key_current_streak"
        private const val KEY_HIGHEST_STREAK = "key_highest_streak"
        private const val KEY_STREAK_START_DATE = "key_streak_start_date"

        const val DEFAULT_CURRENT_STREAK = 50
        const val DEFAULT_HIGHEST_STREAK = 61
        const val DEFAULT_STREAK_START_DATE = "21st July 2026"
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // Shared StateFlows for reactive count observation
    private val _totalCountFlow = MutableStateFlow(totalCount)
    val totalCountFlow: StateFlow<Int> = _totalCountFlow.asStateFlow()

    private val _reelsCountFlow = MutableStateFlow(reelsCount)
    val reelsCountFlow: StateFlow<Int> = _reelsCountFlow.asStateFlow()

    private val _dailyLimitFlow = MutableStateFlow(if (prefs.getBoolean(KEY_TOTAL_BLOCK, false)) 0 else prefs.getInt(KEY_DAILY_LIMIT, DEFAULT_DAILY_LIMIT).coerceIn(1, 100))
    val dailyLimitFlow: StateFlow<Int> = _dailyLimitFlow.asStateFlow()

    // Streak StateFlows
    val currentStreak: Int
        get() = prefs.getInt(KEY_CURRENT_STREAK, DEFAULT_CURRENT_STREAK)

    val highestStreak: Int
        get() = prefs.getInt(KEY_HIGHEST_STREAK, DEFAULT_HIGHEST_STREAK)

    val streakStartDate: String
        get() = prefs.getString(KEY_STREAK_START_DATE, DEFAULT_STREAK_START_DATE) ?: DEFAULT_STREAK_START_DATE

    private val _currentStreakFlow = MutableStateFlow(currentStreak)
    val currentStreakFlow: StateFlow<Int> = _currentStreakFlow.asStateFlow()

    private val _highestStreakFlow = MutableStateFlow(highestStreak)
    val highestStreakFlow: StateFlow<Int> = _highestStreakFlow.asStateFlow()

    private val _streakStartDateFlow = MutableStateFlow(streakStartDate)
    val streakStartDateFlow: StateFlow<String> = _streakStartDateFlow.asStateFlow()

    private fun getTodayDateString(): String {
        return dateFormat.format(Date())
    }

    fun formatNiceDate(date: Date = Date()): String {
        val cal = Calendar.getInstance().apply { time = date }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val suffix = when {
            day in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
        val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.US)
        return "$day$suffix ${monthYearFormat.format(date)}"
    }

    @Synchronized
    fun checkAndResetDaily() {
        val today = getTodayDateString()
        val lastSavedDate = prefs.getString(KEY_LAST_DATE, "")

        if (today != lastSavedDate) {
            if (!lastSavedDate.isNullOrEmpty()) {
                // Evaluate yesterday's usage for streak maintenance
                val yesterdayConsumption = prefs.getInt(KEY_REELS_COUNT, 0) + prefs.getInt(KEY_SHORTS_COUNT, 0)
                val yesterdayLimit = if (isTotalBlockEnabled) 0 else targetDailyLimit

                if (yesterdayConsumption <= yesterdayLimit) {
                    // Success: maintained or progressed streak
                    val updatedStreak = currentStreak + 1
                    val updatedHighest = maxOf(highestStreak, updatedStreak)
                    prefs.edit()
                        .putInt(KEY_CURRENT_STREAK, updatedStreak)
                        .putInt(KEY_HIGHEST_STREAK, updatedHighest)
                        .apply()

                    _currentStreakFlow.value = updatedStreak
                    _highestStreakFlow.value = updatedHighest
                } else {
                    // Exceeded limit: streak resets
                    val resetStartDate = formatNiceDate(Date())
                    prefs.edit()
                        .putInt(KEY_CURRENT_STREAK, 1)
                        .putString(KEY_STREAK_START_DATE, resetStartDate)
                        .apply()

                    _currentStreakFlow.value = 1
                    _streakStartDateFlow.value = resetStartDate
                }
            } else {
                // First initialization
                if (!prefs.contains(KEY_CURRENT_STREAK)) {
                    prefs.edit()
                        .putInt(KEY_CURRENT_STREAK, DEFAULT_CURRENT_STREAK)
                        .putInt(KEY_HIGHEST_STREAK, DEFAULT_HIGHEST_STREAK)
                        .putString(KEY_STREAK_START_DATE, DEFAULT_STREAK_START_DATE)
                        .apply()
                }
            }

            // New calendar day: reset counters to 0
            prefs.edit()
                .putString(KEY_LAST_DATE, today)
                .putInt(KEY_REELS_COUNT, 0)
                .putInt(KEY_SHORTS_COUNT, 0)
                .putInt(KEY_BLOCKED_COUNT, 0)
                .apply()

            _totalCountFlow.value = 0
            _reelsCountFlow.value = 0
        }
    }

    fun setStreakData(current: Int, highest: Int, startDate: String) {
        val safeHighest = maxOf(highest, current)
        prefs.edit()
            .putInt(KEY_CURRENT_STREAK, current)
            .putInt(KEY_HIGHEST_STREAK, safeHighest)
            .putString(KEY_STREAK_START_DATE, startDate)
            .apply()

        _currentStreakFlow.value = current
        _highestStreakFlow.value = safeHighest
        _streakStartDateFlow.value = startDate
    }

    // --- Settings Toggles ---
    var isInstagramBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_INSTAGRAM, true)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_INSTAGRAM, value).apply()

    var isYouTubeBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_BLOCK_YOUTUBE, true)
        set(value) = prefs.edit().putBoolean(KEY_BLOCK_YOUTUBE, value).apply()

    var isStrictMode: Boolean
        get() = prefs.getBoolean(KEY_STRICT_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_STRICT_MODE, value).apply()

    var isTotalBlockEnabled: Boolean
        get() = prefs.getBoolean(KEY_TOTAL_BLOCK, false)
        set(value) {
            prefs.edit().putBoolean(KEY_TOTAL_BLOCK, value).apply()
            _dailyLimitFlow.value = dailyLimit
        }

    var isFloatingCounterEnabled: Boolean
        get() = prefs.getBoolean(KEY_SHOW_FLOATING_COUNTER, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SHOW_FLOATING_COUNTER, value).apply()
            if (!value) {
                com.kontre.app.service.ContentTrackerService.hideOverlayIfActive()
            }
        }

    fun resetTodayCounts() {
        prefs.edit()
            .putInt(KEY_REELS_COUNT, 0)
            .putInt(KEY_SHORTS_COUNT, 0)
            .putInt(KEY_BLOCKED_COUNT, 0)
            .apply()

        _totalCountFlow.value = 0
        _reelsCountFlow.value = 0
    }

    // --- Daily Limit & Usage ---
    val dailyLimit: Int
        get() = if (isTotalBlockEnabled) 0 else targetDailyLimit

    val targetDailyLimit: Int
        get() = prefs.getInt(KEY_DAILY_LIMIT, DEFAULT_DAILY_LIMIT).coerceIn(1, 100)

    fun setDailyLimit(limit: Int) {
        val clamped = limit.coerceIn(1, 100)
        prefs.edit().putInt(KEY_DAILY_LIMIT, clamped).apply()
        _dailyLimitFlow.value = dailyLimit
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
        _totalCountFlow.value = totalCount
        _reelsCountFlow.value = newCount
        return totalCount
    }

    @Synchronized
    fun incrementShorts(): Int {
        checkAndResetDaily()
        val newCount = shortsCount + 1
        prefs.edit().putInt(KEY_SHORTS_COUNT, newCount).apply()
        _totalCountFlow.value = totalCount
        return totalCount
    }

    @Synchronized
    fun incrementBlockedCount(): Int {
        checkAndResetDaily()
        val count = blockedTodayCount + 1
        prefs.edit().putInt(KEY_BLOCKED_COUNT, count).apply()
        return count
    }

    /**
     * Records a uniquely consumed reel/short with metadata.
     * Persists timestamp, platform, and hash to SharedPreferences history log.
     * Emits the updated count to shared StateFlows.
     */
    @Synchronized
    fun recordConsumedReel(hash: String, platform: String, timestamp: Long = System.currentTimeMillis()): Int {
        checkAndResetDaily()
        return when {
            platform.contains("YouTube", ignoreCase = true) -> incrementShorts()
            else -> incrementReels()
        }
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

