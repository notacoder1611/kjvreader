package com.realsneed.biblereader.data

import android.content.Context

/**
 * The daily reading reminder: whether it is on, and the minute of the day.
 *
 * Kept apart from [ReadingPrefs] because the alarm and the notification read it
 * from a broadcast receiver, where a `Context` is all there is.
 */
class ReminderPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** The hour of the day, 0-23. Eight in the evening by default. */
    var hour: Int
        get() = prefs.getInt(KEY_HOUR, DEFAULT_HOUR).coerceIn(0, 23)
        set(value) = prefs.edit().putInt(KEY_HOUR, value.coerceIn(0, 23)).apply()

    var minute: Int
        get() = prefs.getInt(KEY_MINUTE, 0).coerceIn(0, 59)
        set(value) = prefs.edit().putInt(KEY_MINUTE, value.coerceIn(0, 59)).apply()

    /** "20:05", for the setting that shows it. */
    fun timeLabel(): String = String.format(java.util.Locale.US, "%02d:%02d", hour, minute)

    private companion object {
        const val KEY_ENABLED = "enabled"
        const val KEY_HOUR = "hour"
        const val KEY_MINUTE = "minute"
        const val DEFAULT_HOUR = 20
    }
}
