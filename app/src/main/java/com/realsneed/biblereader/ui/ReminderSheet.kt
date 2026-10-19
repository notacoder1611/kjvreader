package com.realsneed.biblereader.ui

import android.app.Dialog
import android.app.TimePickerDialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.realsneed.biblereader.R
import com.realsneed.biblereader.data.ReminderPrefs

/**
 * The daily reading reminder as a small panel: on or off, and the time it fires.
 *
 * Turning it on is enough - the time starts at eight in the evening and tapping
 * the time row changes it. The panel does not schedule anything itself: it
 * reports every change through [onChanged], and the reader decides whether a
 * notification permission still has to be asked for.
 */
class ReminderSheet(
    private val context: Context,
    private val prefs: ReminderPrefs,
    private val onChanged: () -> Unit
) {

    private val dialog = Dialog(context)

    private lateinit var on: TextView
    private lateinit var off: TextView
    private lateinit var time: TextView

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_reminder)

        on = dialog.findViewById(R.id.reminderOn)
        off = dialog.findViewById(R.id.reminderOff)
        time = dialog.findViewById(R.id.reminderTime)

        on.setOnClickListener {
            prefs.enabled = true
            refresh()
            onChanged()
        }
        off.setOnClickListener {
            prefs.enabled = false
            refresh()
            onChanged()
        }
        time.setOnClickListener { pickTime() }
        dialog.findViewById<TextView>(R.id.reminderDone).setOnClickListener { dialog.dismiss() }

        refresh()
        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(0))
            setGravity(Gravity.BOTTOM)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun pickTime() {
        TimePickerDialog(
            context,
            { _, hour, minute ->
                prefs.hour = hour
                prefs.minute = minute
                // Choosing a time is a clear wish to be reminded.
                prefs.enabled = true
                refresh()
                onChanged()
            },
            prefs.hour,
            prefs.minute,
            true
        ).show()
    }

    private fun refresh() {
        val selected = ContextCompat.getDrawable(context, R.drawable.chip_pill_on)
        val plain = ContextCompat.getDrawable(context, R.drawable.chip_pill)
        val text = ContextCompat.getColor(context, R.color.text_primary)

        on.background = if (prefs.enabled) selected else plain
        off.background = if (prefs.enabled) plain else selected
        on.setTextColor(if (prefs.enabled) 0xFFFFFFFF.toInt() else text)
        off.setTextColor(if (prefs.enabled) text else 0xFFFFFFFF.toInt())
        time.text = context.getString(R.string.reminder_time, prefs.timeLabel())
    }
}
