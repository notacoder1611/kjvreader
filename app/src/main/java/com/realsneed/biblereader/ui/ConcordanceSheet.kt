package com.realsneed.biblereader.ui

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.realsneed.biblereader.R
import com.realsneed.biblereader.data.BibleMeta
import com.realsneed.biblereader.data.BibleRepository
import com.realsneed.biblereader.data.BookInfo
import com.realsneed.biblereader.data.Concordance
import com.realsneed.biblereader.data.StrongsRepository
import java.util.concurrent.Executor

/**
 * Every verse that uses one Hebrew or Greek word - a concordance, which is the
 * question a word study raises but a dictionary cannot answer: *where else is
 * this used?*
 *
 * Tapping a verse goes there and flashes it, using the same jump the rest of the
 * app uses. Verses are read as they scroll, so a word with 9,000 occurrences
 * costs no more memory than one with nine.
 */
class ConcordanceSheet(
    private val context: Context,
    private val repository: BibleRepository,
    private val concordance: Concordance,
    private val strongs: StrongsRepository,
    private val executor: Executor,
    private val body: Handler,
    private val heading: String,
    private val reference: String,
    private val packed: IntArray,
    private val onJump: (BookInfo, Int, Int) -> Unit
) {

    private val dialog = Dialog(context)

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_concordance)

        dialog.findViewById<TextView>(R.id.concordanceTitle).text = heading
        dialog.findViewById<TextView>(R.id.concordanceCount).text =
            context.resources.getQuantityString(
                R.plurals.concordance_count, packed.size, packed.size
            ) + "  ·  " + reference
        dialog.findViewById<TextView>(R.id.concordanceDone).setOnClickListener { dialog.dismiss() }

        val list = dialog.findViewById<RecyclerView>(R.id.concordanceList)
        list.layoutManager = LinearLayoutManager(context)
        list.adapter = OccurrenceAdapter(
            context, repository, strongs, reference, executor, body, packed
        ) { book, chapter, verse ->
            dialog.dismiss()
            onJump(book, chapter, verse)
        }

        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(0))
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
    }
}

private class OccurrenceAdapter(
    private val context: Context,
    private val repository: BibleRepository,
    private val strongs: StrongsRepository,
    private val number: String,
    private val executor: Executor,
    private val body: Handler,
    private val packed: IntArray,
    private val onOpen: (BookInfo, Int, Int) -> Unit
) : RecyclerView.Adapter<OccurrenceAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val reference: TextView = view.findViewById(R.id.occurrenceReference)
        val text: TextView = view.findViewById(R.id.occurrenceText)
    }

    override fun getItemCount(): Int = packed.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_occurrence, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val value = packed[position]
        val assetIndex = Concordance.assetIndexOf(value)
        val chapter = Concordance.chapterOf(value)
        val verse = Concordance.verseOf(value)
        val book = BibleMeta.byAssetIndex(assetIndex)
        holder.reference.text = if (book == null) "$chapter:$verse"
        else "${book.name} $chapter:$verse"
        holder.text.text = ""
        if (book == null) return

        // The verse text is read as the row comes on screen, and cached per
        // chapter by the repository, so scrolling stays smooth. The word the
        // number stands for is picked out, so nothing has to be guessed at.
        executor.execute {
            val text = repository.chapter(book, chapter)
                ?.verses?.firstOrNull { it.number == verse }?.text.orEmpty()
            val ranges = strongs.wordsFor(book, chapter)[verse].orEmpty()
                .filter { it.strongs == number }
                .mapNotNull { StrongsRepository.wordRange(text, it.start, it.end) }
            body.post {
                if (holder.adapterPosition != position) return@post
                if (ranges.isEmpty()) {
                    holder.text.text = text
                } else {
                    // A long verse would push its own word past the two lines this
                    // row shows, which is why long occurrences looked unhighlighted.
                    // The text is cut to start a little before the word instead, so
                    // the highlighted word is always on screen.
                    val (shown, shift) = windowAround(text, ranges.first().first)
                    val spannable = SpannableStringBuilder(shown)
                    for (range in ranges) {
                        val from = range.first + shift
                        val to = range.last + 1 + shift
                        if (from < 0 || to > spannable.length || to <= from) continue
                        spannable.setSpan(
                            BackgroundColorSpan(highlight),
                            from,
                            to,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    holder.text.text = spannable
                }
            }
        }
        holder.itemView.setOnClickListener { onOpen(book, chapter, verse) }
    }

    /** The same soft accent the reader uses to mark the verse being read. */
    private val highlight: Int get() = ContextCompat.getColor(context, R.color.active_verse)

    /**
     * [text] cut to open shortly before the word at [at], with an ellipsis in front.
     *
     * Returns the text to show and the amount to add to every character offset in
     * the original verse to find it in the shown text. The cut is moved forward to
     * a word boundary when there is one, so a word is never shown in half.
     */
    private fun windowAround(text: String, at: Int): Pair<String, Int> {
        val lead = 40
        if (at <= lead) return text to 0
        var start = at - lead
        val space = text.indexOf(' ', start)
        if (space in start until at) start = space + 1
        val prefix = "\u2026"
        return (prefix + text.substring(start)) to (prefix.length - start)
    }
}
