package com.realsneed.biblereader.ui

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.util.SparseArray
import android.util.TypedValue
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.realsneed.biblereader.R
import com.realsneed.biblereader.data.BibleMeta
import com.realsneed.biblereader.data.BibleRepository
import com.realsneed.biblereader.data.BookInfo
import com.realsneed.biblereader.data.CrossReferences
import com.realsneed.biblereader.data.Verse
import com.realsneed.biblereader.data.VerseState
import com.realsneed.biblereader.data.VerseStore

/** Things about how a verse is drawn that a tap needs to know. */
data class VerseStyle(
    val textSizeSp: Float,
    val lineSpacing: Float,
    val serif: Boolean,
    val textColor: Int,
    val numberColor: Int,
    val noteColor: Int,
    val darkTheme: Boolean,
    val showItalics: Boolean,
    val showFootnotes: Boolean,
    val showQuotations: Boolean,
    val quotationColor: Int
)

/**
 * One swipeable page per chapter of the whole Bible (1,189 pages). Pages are
 * recycled, so memory stays flat no matter how far the reader swipes.
 *
 * Marks are read once per page (a single query) and rendered as spans; tapping a
 * verse reports back through [onVerseTap]. [style] can be replaced at any time
 * and [restyleVisiblePages] redraws what is on screen **without** disturbing the
 * scroll position - which is what makes the reading settings feel live.
 */
class ChapterPagerAdapter(
    private val repository: BibleRepository,
    private val store: VerseStore,
    private val quotations: CrossReferences,
    var style: VerseStyle,
    private val onVerseTap: (BookInfo, Int, Int, Int) -> Unit,
    private val onVerseLongPress: (BookInfo, Int, Int, Int) -> Unit
) : RecyclerView.Adapter<ChapterPagerAdapter.PageHolder>() {

    class PageHolder(root: View) : RecyclerView.ViewHolder(root) {
        val scroll: ScrollView = root.findViewById(R.id.pageScroll)
        val container: LinearLayout = root.findViewById(R.id.pageVerses)
        val verseViews = SparseArray<View>()
    }

    private var attached: RecyclerView? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        attached = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        attached = null
    }

    override fun getItemCount(): Int = BibleMeta.TOTAL_CHAPTERS

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PageHolder =
        PageHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_chapter_page, parent, false)
        )

    override fun onBindViewHolder(holder: PageHolder, position: Int) {
        holder.verseViews.clear()
        holder.container.removeAllViews()
        holder.scroll.scrollTo(0, 0)

        val (book, chapterNumber) = BibleMeta.chapterAt(position)
        val chapter = repository.chapter(book, chapterNumber) ?: return
        val states = store.statesFor(book.id, chapterNumber)

        val params = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        for (verse in chapter.verses) {
            val view = TextView(holder.container.context)
            installTouch(view, book, chapterNumber, verse)
            renderVerse(view, book, chapterNumber, verse, states[verse.number])
            holder.container.addView(view, params)
            holder.verseViews.put(verse.number, view)
        }
    }

    /**
     * A long press reports **which word** of the verse is under the finger, so
     * the reader can look up the original behind it; a tap reports the character,
     * so that a tap on a footnote marker can open the note instead of the verse
     * menu. Both need the offset, which has to be worked out by hand because the
     * verse number is part of the same run of text.
     *
     * Returning false from the touch listener leaves the tap working normally -
     * the gesture detector only listens in, it does not take the events over.
     */
    private fun installTouch(view: TextView, book: BookInfo, chapterNumber: Int, verse: Verse) {
        var lastOffset = -1
        val detector = GestureDetector(
            view.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onLongPress(event: MotionEvent) {
                    val offset = offsetInVerse(view, verse, event)
                    if (offset >= 0) {
                        onVerseLongPress(book, chapterNumber, verse.number, offset)
                    }
                }
            }
        )
        view.setOnTouchListener { _, event ->
            lastOffset = offsetInVerse(view, verse, event)
            detector.onTouchEvent(event)
            false
        }
        view.setOnClickListener { onVerseTap(book, chapterNumber, verse.number, lastOffset) }
    }

    /** Which character of the verse - not counting the number - is under [event]. */
    private fun offsetInVerse(view: TextView, verse: Verse, event: MotionEvent): Int {
        val layout = view.layout ?: return -1
        val line = layout.getLineForVertical(
            (event.y - view.totalPaddingTop + view.scrollY).toInt()
        )
        val offset = layout.getOffsetForHorizontal(
            line, event.x - view.totalPaddingLeft + view.scrollX
        )
        return offset - prefixLength(verse)
    }

    /** The rendered verse begins with "16  ", which is not part of the verse. */
    private fun prefixLength(verse: Verse): Int = verse.number.toString().length + 2

    /** Re-renders a single already-bound verse in place (keeps scroll position). */
    fun refreshVerse(holder: PageHolder, position: Int, verseNumber: Int) {
        val view = holder.verseViews.get(verseNumber) as? TextView ?: return
        val (book, chapterNumber) = BibleMeta.chapterAt(position)
        val verse = repository.chapter(book, chapterNumber)
            ?.verses?.firstOrNull { it.number == verseNumber } ?: return
        renderVerse(view, book, chapterNumber, verse, store.state(book.id, chapterNumber, verseNumber))
    }

    /** Redraws the attached pages with the current [style], leaving scroll alone. */
    fun restyleVisiblePages() {
        val recycler = attached ?: return
        for (i in 0 until recycler.childCount) {
            val child = recycler.getChildAt(i)
            val holder = recycler.getChildViewHolder(child) as? PageHolder ?: continue
            restyle(holder, recycler.getChildAdapterPosition(child))
        }
    }

    private fun restyle(holder: PageHolder, position: Int) {
        if (position < 0) return
        val (book, chapterNumber) = BibleMeta.chapterAt(position)
        val chapter = repository.chapter(book, chapterNumber) ?: return
        val states = store.statesFor(book.id, chapterNumber)
        for (verse in chapter.verses) {
            val view = holder.verseViews.get(verse.number) as? TextView ?: continue
            renderVerse(view, book, chapterNumber, verse, states[verse.number])
        }
    }

    private fun renderVerse(
        view: TextView,
        book: BookInfo,
        chapterNumber: Int,
        verse: Verse,
        state: VerseState?
    ) {
        val spannable = SpannableStringBuilder()

        val numberStart = spannable.length
        spannable.append(verse.number.toString())
        val numberEnd = spannable.length
        spannable.setSpan(SuperscriptSpan(), numberStart, numberEnd, SPAN)
        spannable.setSpan(RelativeSizeSpan(0.70f), numberStart, numberEnd, SPAN)
        spannable.setSpan(StyleSpan(Typeface.BOLD), numberStart, numberEnd, SPAN)
        spannable.setSpan(ForegroundColorSpan(style.numberColor), numberStart, numberEnd, SPAN)

        spannable.append("  ")
        val textStart = spannable.length
        spannable.append(verse.text)

        // Footnote markers are inserted into the text further down, so the offsets
        // are worked out once, here, and used by the insertion below.
        val marks = if (style.showFootnotes) footnoteMarkerOffsets(verse) else IntArray(0)

        // The words this verse borrows from an earlier passage, underlined in a
        // colour of their own. The mark is a signpost, not a control: a tap still
        // opens the verse menu, where the quotations are listed.
        if (style.showQuotations) {
            for (quotation in quotations.forVerse(book.id, chapterNumber, verse.number)) {
                val range = quotations.rangeIn(verse.text, quotation.phrase) ?: continue
                val from = textStart + range.first
                val to = textStart + range.last + 1
                if (from >= textStart && to <= spannable.length && to > from) {
                    spannable.setSpan(
                        QuotationUnderlineSpan(style.quotationColor), from, to, SPAN
                    )
                }
            }
        }

        // The words the translators supplied, in italics as every printed KJV
        // has them - and the same words that have no Hebrew or Greek behind them.
        if (style.showItalics) {
            var i = 0
            while (i < verse.italics.size) {
                val from = textStart + verse.italics[i]
                val to = textStart + verse.italics[i + 1]
                if (from >= textStart && to <= spannable.length && to > from) {
                    spannable.setSpan(ItalicSpan(), from, to, SPAN)
                }
                i += 2
            }
        }

        // The translators' marginal notes are marked where they belong, and the
        // marker can be tapped (see [footnoteMarkerOffsets]).
        if (style.showFootnotes) {
            for (mark in marks) {
                val at = (textStart + mark).coerceIn(textStart, spannable.length)
                spannable.insert(at, FOOTNOTE_MARK)
                spannable.setSpan(RelativeSizeSpan(0.85f), at, at + 1, SPAN)
                spannable.setSpan(ForegroundColorSpan(style.noteColor), at, at + 1, SPAN)
                spannable.setSpan(StyleSpan(Typeface.BOLD), at, at + 1, SPAN)
            }
        }

        // The colour stops at the end of the verse text, so the note marker sits
        // outside the highlight at the very end instead of inside the prose.
        val highlightEnd = spannable.length

        if (state?.hasNote == true) {
            VerseLine.appendNoteMark(spannable, view.context, style.noteColor)
        }

        val highlight = state?.color?.colorFor(style.darkTheme)
        if (highlight != null) {
            spannable.setSpan(BackgroundColorSpan(highlight), 0, highlightEnd, SPAN)
        }

        view.setText(spannable, TextView.BufferType.SPANNABLE)
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.textSizeSp)
        view.setTextColor(style.textColor)
        view.setLineSpacing(0f, style.lineSpacing)
        view.typeface = if (style.serif) Typeface.SERIF else Typeface.SANS_SERIF
        val pad = dp(view.context, 6)
        view.setPadding(0, pad, 0, pad)
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    companion object {
        /** The traditional mark for a marginal note: a double dagger. */
        const val FOOTNOTE_MARK = "\u2021"

        /**
         * Where the footnote markers sit in [verse]'s text, in order. Each marker
         * shifts the text after it along by one, so the offsets account for the
         * markers before them - which is what lets a tap on a marker be matched
         * to the footnote it belongs to.
         */
        fun footnoteMarkerOffsets(verse: Verse): IntArray {
            val marks = IntArray(verse.footnotes.size)
            var inserted = 0
            for (i in verse.footnotes.indices) {
                marks[i] = verse.footnotes[i].anchor.coerceIn(0, verse.text.length) + inserted
                inserted++
            }
            return marks
        }

        private const val SPAN = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    }
}
