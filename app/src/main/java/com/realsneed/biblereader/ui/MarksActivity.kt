package com.realsneed.biblereader.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.realsneed.biblereader.R
import com.realsneed.biblereader.data.BibleMeta
import com.realsneed.biblereader.data.BibleRepository
import com.realsneed.biblereader.data.BookInfo
import com.realsneed.biblereader.data.ColourAt
import com.realsneed.biblereader.data.HighlightColor
import com.realsneed.biblereader.data.NoteAt
import com.realsneed.biblereader.data.VerseStore
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Every highlight and note in the Bible, grouped by book and chapter.
 *
 * One row per note (which can span a block of verses) plus one row per run of
 * highlighted verses with no notes, so every mark can be removed on its own -
 * long-press a row for the delete options.
 */
class MarksActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: VerseStore
    private lateinit var adapter: MarksAdapter
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_marks)

        store = VerseStore(this)
        adapter = MarksAdapter(
            textColor = ContextCompat.getColor(this, R.color.text_primary),
            numberColor = ContextCompat.getColor(this, R.color.section_title),
            darkTheme = isDarkTheme(),
            onOpen = { book, chapter, verse -> open(book, chapter, verse) },
            onMenu = { entry, anchor -> showEntryMenu(entry, anchor) }
        )

        emptyView = findViewById(R.id.marksEmpty)
        val list = findViewById<RecyclerView>(R.id.marksList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        findViewById<ImageButton>(R.id.marksBack).setOnClickListener { finish() }

        reload()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun reload() {
        val repository = BibleRepository.get(assets)
        executor.execute {
            val rows = buildRows(repository)
            handler.post {
                adapter.submit(rows)
                emptyView.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun buildRows(repository: BibleRepository): List<MarkRow> {
        val notes = store.allNotes()
        val colours = store.allColors()
        val colourByVerse = HashMap<String, HighlightColor>()
        colours.forEach { colourByVerse[key(it.bookId, it.chapter, it.verse)] = it.color }

        val entries = ArrayList<Entry>(notes.size + colours.size)
        notes.forEach { note ->
            entries.add(
                Entry(
                    bookId = note.bookId,
                    chapter = note.chapter,
                    verse = note.verse,
                    endVerse = note.endVerse,
                    noteId = note.id,
                    note = note.body,
                    color = colourByVerse[key(note.bookId, note.chapter, note.verse)]
                )
            )
        }
        // A run of highlighted verses with no notes of their own is one row.
        val versesWithNotes = notes.map { key(it.bookId, it.chapter, it.verse) }.toHashSet()
        colours.forEach { colour ->
            val covers = (colour.verse..colour.endVerse).any {
                key(colour.bookId, colour.chapter, it) in versesWithNotes
            }
            if (!covers) {
                entries.add(
                    Entry(
                        bookId = colour.bookId,
                        chapter = colour.chapter,
                        verse = colour.verse,
                        endVerse = colour.endVerse,
                        noteId = 0L,
                        note = null,
                        color = colour.color
                    )
                )
            }
        }
        if (entries.isEmpty()) return emptyList()

        entries.sortWith(
            compareBy(
                { BibleMeta.positionOfId(it.bookId) },
                { it.chapter },
                { it.verse },
                { it.noteId }
            )
        )

        val rows = ArrayList<MarkRow>(entries.size + 16)
        var currentBook: BookInfo? = null
        var currentChapter = -1
        var verseTexts: Map<Int, String> = emptyMap()
        for (entry in entries) {
            val book = BibleMeta.byId(entry.bookId) ?: continue
            if (book != currentBook || entry.chapter != currentChapter) {
                currentBook = book
                currentChapter = entry.chapter
                verseTexts = repository.chapter(book, entry.chapter)
                    ?.verses?.associate { it.number to it.text } ?: emptyMap()
                rows.add(MarkRow.Header("${book.name.uppercase(Locale.ROOT)} ${entry.chapter}"))
            }
            rows.add(MarkRow.Entry(entry, book, verseTexts[entry.verse].orEmpty()))
        }
        return rows
    }

    private fun open(book: BookInfo, chapter: Int, verse: Int) {
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_BOOK, book.assetIndex)
                .putExtra(EXTRA_CHAPTER, chapter)
                .putExtra(EXTRA_VERSE, verse)
        )
        finish()
    }

    private fun showEntryMenu(entry: Entry, anchor: View) {
        val popup = PopupMenu(this, anchor)
        if (entry.noteId > 0L) popup.menu.add(Menu.NONE, MENU_DELETE_NOTE, 0, R.string.delete_note)
        if (entry.color != null) popup.menu.add(Menu.NONE, MENU_REMOVE_HIGHLIGHT, 1, R.string.remove_highlight)
        if (popup.menu.size() == 0) return
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_DELETE_NOTE -> store.deleteNote(entry.noteId)
                MENU_REMOVE_HIGHLIGHT ->
                    for (verse in entry.verse..entry.endVerse) {
                        store.setColor(entry.bookId, entry.chapter, verse, null)
                    }
            }
            reload()
            true
        }
        popup.show()
    }

    private fun isDarkTheme(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun key(book: String, chapter: Int, verse: Int) = "$book|$chapter|$verse"

    companion object {
        const val EXTRA_BOOK = "book"
        const val EXTRA_CHAPTER = "chapter"
        const val EXTRA_VERSE = "verse"
        private const val MENU_DELETE_NOTE = 1
        private const val MENU_REMOVE_HIGHLIGHT = 2
    }
}

/** One thing to show: a note, or a run of highlights with no notes. */
class Entry(
    val bookId: String,
    val chapter: Int,
    val verse: Int,
    val endVerse: Int,
    val noteId: Long,
    val note: String?,
    val color: HighlightColor?
)

private sealed class MarkRow {
    class Header(val label: String) : MarkRow()
    class Entry(
        val entry: com.realsneed.biblereader.ui.Entry,
        val book: BookInfo,
        val verseText: String
    ) : MarkRow()
}

private class MarksAdapter(
    private val textColor: Int,
    private val numberColor: Int,
    private val darkTheme: Boolean,
    private val onOpen: (BookInfo, Int, Int) -> Unit,
    private val onMenu: (Entry, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.pickerSectionTitle)
    }

    class ItemHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dot: View = view.findViewById(R.id.markDot)
        val text: TextView = view.findViewById(R.id.markText)
        val note: TextView = view.findViewById(R.id.markNote)
    }

    private var rows: List<MarkRow> = emptyList()

    fun submit(newRows: List<MarkRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int =
        if (rows[position] is MarkRow.Header) TYPE_HEADER else TYPE_ITEM

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.item_picker_section, parent, false))
        } else {
            ItemHolder(inflater.inflate(R.layout.item_mark_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = rows[position]
        when (holder) {
            is HeaderHolder -> holder.title.text = (row as MarkRow.Header).label
            is ItemHolder -> bind(holder, row as MarkRow.Entry)
        }
    }

    private fun bind(holder: ItemHolder, row: MarkRow.Entry) {
        val entry = row.entry
        val hasNote = !entry.note.isNullOrEmpty()
        holder.text.text = VerseLine.build(
            holder.itemView.context, entry.verse, entry.endVerse, row.verseText, numberColor, hasNote
        )
        holder.text.setTextColor(textColor)

        // A soft-edged chip in the verse's own colour, at the start of the row.
        // Tinting the whole row made the list hard to read.
        val colour = entry.color?.colorFor(darkTheme)
        holder.dot.visibility = if (colour == null) View.INVISIBLE else View.VISIBLE
        if (colour != null) {
            holder.dot.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(holder.itemView.context, DOT_RADIUS_DP).toFloat()
                setColor(colour)
            }
        }

        if (hasNote) {
            holder.note.visibility = View.VISIBLE
            holder.note.text = noteText(entry)
            holder.note.setTextColor(numberColor)
        } else {
            holder.note.visibility = View.GONE
        }

        holder.itemView.setOnClickListener { onOpen(row.book, entry.chapter, entry.verse) }
        holder.itemView.setOnLongClickListener { anchor ->
            onMenu(entry, anchor)
            true
        }
    }

    /** A note that spans verses says which verses it covers. */
    private fun noteText(entry: Entry): CharSequence {
        if (entry.endVerse <= entry.verse) return entry.note.orEmpty()
        val spannable = SpannableStringBuilder()
        spannable.append("${entry.verse}-${entry.endVerse}  ")
        spannable.setSpan(
            StyleSpan(Typeface.BOLD), 0, spannable.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        spannable.append(entry.note.orEmpty())
        return spannable
    }

    private fun dp(context: android.content.Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
        const val DOT_RADIUS_DP = 3
    }
}
