package com.realsneed.biblereader.ui

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import com.realsneed.biblereader.R
import com.realsneed.biblereader.data.BookInfo
import com.realsneed.biblereader.data.Verse

/**
 * The KJV's own margin notes for one verse: what the Hebrew or Greek literally
 * says, and the readings the translators weighed. Opened by tapping the double
 * dagger in the text, or from the Verse menu.
 */
class FootnotesSheet(
    private val context: Context,
    private val book: BookInfo,
    private val chapter: Int,
    private val verse: Verse,
    private val darkTheme: Boolean
) {

    private val dialog = Dialog(context)

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_footnotes)

        dialog.findViewById<TextView>(R.id.footnotesRef).text =
            context.getString(R.string.reference_title, book.name, chapter, verse.number)
        dialog.findViewById<TextView>(R.id.footnotesDone).setOnClickListener { dialog.dismiss() }

        val list = dialog.findViewById<LinearLayout>(R.id.footnotesList)
        val inflater = LayoutInflater.from(context)
        for (note in verse.footnotes) {
            val row = inflater.inflate(R.layout.item_verse_footnote, list, false)
            row.findViewById<TextView>(R.id.footnoteRef).text =
                note.quote ?: note.reference.ifEmpty { "${chapter}:${verse.number}" }
            row.findViewById<TextView>(R.id.footnoteText).text = note.text
            list.addView(row)
        }

        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(0))
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
}
