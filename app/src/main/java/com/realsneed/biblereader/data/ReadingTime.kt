package com.realsneed.biblereader.data

import android.content.Context
import android.os.SystemClock
import java.util.Calendar
import java.util.Locale

/**
 * How long the app has actually been in front today.
 *
 * The nudge could not be tested before: any visit - even opening the app to see
 * why no reminder arrived - counted as reading for the whole day. Time is now
 * accumulated only while an activity is in front, so leaving the app pauses the
 * count and [REQUIRED_SECONDS] of real reading is what silences the nudge.
 */
class ReadingTime(context: Context) {

    private val prefs = context.getSharedPreferences("reading_time", Context.MODE_PRIVATE)

    /** Adds time spent in front, and returns the new total for today. */
    fun addActiveSeconds(seconds: Long): Long {
        if (seconds <= 0L) return secondsToday()
        rollOver()
        val total = prefs.getLong(KEY_SECONDS, 0L) + seconds
        prefs.edit().putLong(KEY_SECONDS, total).apply()
        return total
    }

    fun secondsToday(): Long {
        rollOver()
        return prefs.getLong(KEY_SECONDS, 0L)
    }

    /** Ten minutes in front is what counts as having read today. */
    fun hasReadToday(): Boolean = secondsToday() >= REQUIRED_SECONDS

    /** A new day starts at midnight, so yesterday's minutes never carry over. */
    private fun rollOver() {
        val today = todayKey()
        if (prefs.getString(KEY_DAY, null) == today) return
        prefs.edit().putString(KEY_DAY, today).putLong(KEY_SECONDS, 0L).apply()
    }

    private fun todayKey(): String {
        val now = Calendar.getInstance()
        return String.format(
            Locale.US, "%04d-%02d-%02d",
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH)
        )
    }

    companion object {
        /** Ten minutes, so the reminder can be reached - and tested - in one sitting. */
        const val REQUIRED_SECONDS = 10 * 60L
        private const val KEY_DAY = "day"
        private const val KEY_SECONDS = "seconds"
    }
}

/**
 * Times the activities' time in front. One clock is enough: Android resumes one
 * activity at a time, and the elapsed clock keeps counting across a pause.
 */
object ReadingClock {

    private var resumedAt = 0L

    fun resume() {
        if (resumedAt == 0L) resumedAt = SystemClock.elapsedRealtime()
    }

    fun pause(context: Context) {
        if (resumedAt == 0L) return
        val seconds = (SystemClock.elapsedRealtime() - resumedAt) / 1000L
        resumedAt = 0L
        if (seconds > 0L) ReadingTime(context.applicationContext).addActiveSeconds(seconds)
    }
}
