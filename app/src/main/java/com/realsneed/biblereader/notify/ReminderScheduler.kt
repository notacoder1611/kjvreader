package com.realsneed.biblereader.notify

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.realsneed.biblereader.R
import com.realsneed.biblereader.data.ReminderPrefs
import com.realsneed.biblereader.ui.MainActivity
import java.util.Calendar

/**
 * The once-a-day reading nudge.
 *
 * The alarm is deliberately **inexact** (`setAndAllowWhileIdle`): an exact alarm
 * needs a permission the reader would have to grant, and being reminded "around
 * eight" is enough for reading. Anything more precise would be a worse trade for
 * the reader than for the reminder.
 *
 * The alarm is re-set after every delivery and after a reboot or an app update,
 * so it survives everything except the reminder being switched off.
 */
object ReminderScheduler {

    /** Action of the alarm itself, so the receiver can tell it from a reboot. */
    const val ACTION_REMIND = "com.realsneed.biblereader.REMIND"

    private const val REQUEST_ALARM = 7101
    private const val REQUEST_OPEN = 7102
    private const val NOTIFICATION_ID = 7103
    private const val CHANNEL_ID = "reminders"

    /** Books the alarm for the next occurrence of the chosen time. */
    fun schedule(context: Context) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val prefs = ReminderPrefs(context)
        val pending = alarmIntent(context)
        if (!prefs.enabled) {
            alarms.cancel(pending)
            return
        }
        val at = nextTrigger(prefs.hour, prefs.minute)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarms.set(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    /** The next time [hour]:[minute] comes round, in milliseconds. */
    fun nextTrigger(
        hour: Int,
        minute: Int,
        now: Long = System.currentTimeMillis()
    ): Long {
        val next = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
        }
        return next.timeInMillis
    }

    /** Midnight today, the line between "read today" and "did not". */
    fun startOfToday(now: Long = System.currentTimeMillis()): Long =
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    /** Posts the nudge, tapping which opens the reader where it was left. */
    fun notify(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
            }
            manager.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            pendingFlags()
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bell)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(context.getString(R.string.reminder_text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_ALARM,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
        pendingFlags()
    )

    private fun pendingFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
}
