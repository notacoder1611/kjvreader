package com.realsneed.biblereader.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.realsneed.biblereader.data.ReadingTime
import com.realsneed.biblereader.data.ReminderPrefs

/**
 * Delivers the daily nudge - or stays quiet if today has already been read.
 *
 * "Read today" is measured time, not a guess: [ReadingTime] accumulates the
 * minutes an activity spends in front, pauses when the app goes away, and resets
 * at midnight. Ten minutes of real reading is what silences the nudge, which also
 * means the reminder can finally be tested by opening the app briefly.
 *
 * This receiver also takes the reboot and app-update broadcasts, because an alarm
 * does not survive either one.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = ReminderPrefs(context)
        if (!prefs.enabled) return

        if (intent.action != ReminderScheduler.ACTION_REMIND) {
            ReminderScheduler.schedule(context)
            return
        }

        if (!readToday(context)) runCatching { ReminderScheduler.notify(context) }
        // Tomorrow's alarm, whether or not today's nudge was needed.
        ReminderScheduler.schedule(context)
    }

    /**
     * Ten minutes with the app in front counts as reading. Time spent away is not
     * counted, and neither is a quick look at the app - which is what makes the
     * reminder testable.
     */
    private fun readToday(context: Context): Boolean = ReadingTime(context).hasReadToday()
}
