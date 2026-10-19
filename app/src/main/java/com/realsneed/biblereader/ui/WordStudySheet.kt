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
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.realsneed.biblereader.R
import com.realsneed.biblereader.data.BookInfo
import com.realsneed.biblereader.data.Concordance
import com.realsneed.biblereader.data.StrongsRepository
import com.realsneed.biblereader.data.Verse
import com.realsneed.biblereader.data.VerseWord
import com.realsneed.biblereader.data.WebsterRepository
import java.util.concurrent.Executor

/**
 * **Word study**: the Hebrew and Greek words behind a verse, in the order they
 * appear. Tapping one opens its definition.
 *
 * Only the words that have an original are listed - that is the studyable text,
 * and it saves the reader scrolling past `the` and `and`. For a block every verse
 * is listed in turn, so nothing is hidden.
 */
class WordStudySheet(
    private val context: Context,
    private val strongs: StrongsRepository,
    private val webster: WebsterRepository,
    private val concordance: Concordance,
    private val indexer: Executor,
    private val executor: Executor,
    private val book: BookInfo,
    private val chapter: Int,
    private val verses: List<Verse>,
    private val words: Map<Int, List<VerseWord>>,
    private val onConcordance: (String, String) -> Unit = { _, _ -> }
) {

    private val dialog = Dialog(context)
    private var capped = false

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_word_study)

        dialog.findViewById<TextView>(R.id.wordStudyRef).text =
            context.getString(R.string.word_study_reference, book.name, referenceText())
        dialog.findViewById<TextView>(R.id.wordStudyDone).setOnClickListener { dialog.dismiss() }

        val list = dialog.findViewById<LinearLayout>(R.id.wordStudyList)
        val empty = dialog.findViewById<TextView>(R.id.wordStudyEmpty)

        val inflater = LayoutInflater.from(context)
        var rows = 0
        for (verse in verses) {
            val numbered = words[verse.number].orEmpty()
            if (numbered.isEmpty()) continue
            if (verses.size > 1) {
                list.addView(verseHeading(verse.number))
            }
            for (word in numbered) {
                list.addView(wordRow(inflater, word, verse.number, list))
                rows++
            }
        }
        list.visibility = if (rows == 0) View.GONE else View.VISIBLE
        empty.visibility = if (rows == 0) View.VISIBLE else View.GONE

        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(0))
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        capHeight()
    }

    /** "15" above the words of a verse, when a block is open. */
    private fun verseHeading(verseNumber: Int): View {
        val heading = TextView(context)
        heading.text = verseNumber.toString()
        heading.setTextColor(ContextCompat.getColor(context, R.color.section_title))
        heading.textSize = 12f
        heading.setTypeface(null, android.graphics.Typeface.BOLD)
        heading.setPadding(dp(20), dp(10), dp(20), dp(2))
        return heading
    }

    private fun wordRow(
        inflater: LayoutInflater,
        word: VerseWord,
        verseNumber: Int,
        parent: ViewGroup
    ): View {
        val row = inflater.inflate(R.layout.item_word_row, parent, false)
        row.findViewById<TextView>(R.id.wordText).text = word.label
        row.findViewById<TextView>(R.id.wordStrongs).text = word.strongs
        row.setOnClickListener {
            StrongsSheet(
                context = context,
                strongs = strongs,
                webster = webster,
                concordance = concordance,
                indexer = indexer,
                id = word.strongs,
                label = word.label,
                reference = context.getString(R.string.reference_title, book.name, chapter, verseNumber),
                executor = executor,
                onConcordance = onConcordance
            ).show()
        }
        return row
    }

    private fun referenceText(): String {
        val numbers = verses.map { it.number }
        if (numbers.size == 1) return "$chapter:${numbers.first()}"
        val parts = ArrayList<String>()
        var start = numbers.first()
        var previous = start
        for (number in numbers.drop(1)) {
            if (number == previous + 1) {
                previous = number
                continue
            }
            parts.add(if (start == previous) "$start" else "$start-$previous")
            start = number
            previous = number
        }
        parts.add(if (start == previous) "$start" else "$start-$previous")
        return "$chapter:" + parts.joinToString(",")
    }

    private fun capHeight() {
        if (capped) return
        val scroll = dialog.findViewById<ScrollView>(R.id.wordStudyScroll)
        val max = (context.resources.displayMetrics.heightPixels * MAX_HEIGHT_FRACTION).toInt()
        if (scroll.height <= max) return
        val params = scroll.layoutParams
        params.height = max
        scroll.layoutParams = params
        capped = true
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val MAX_HEIGHT_FRACTION = 0.68f
    }
}
