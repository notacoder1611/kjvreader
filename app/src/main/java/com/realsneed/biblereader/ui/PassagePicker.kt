package com.realsneed.biblereader.ui

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.realsneed.biblereader.R
import com.realsneed.biblereader.data.BibleMeta
import com.realsneed.biblereader.data.BibleReferences
import com.realsneed.biblereader.data.BibleRepository
import com.realsneed.biblereader.data.BookInfo
import com.realsneed.biblereader.data.Reference
import java.util.concurrent.ExecutorService

/**
 * Bottom-sheet passage picker.
 *
 * Three steps that stay open while browsing: book -> chapter -> optional verse,
 * with a reference search field on top ("j1:1", "1jn2:3", "ps23"). Nothing is
 * opened until a verse chip is tapped, the pinned "Go" arrow is tapped (start of
 * the selected chapter), or a typed reference is confirmed with the IME Go key.
 */
class PassagePicker(
    private val context: Context,
    private val repository: BibleRepository,
    private val executor: ExecutorService,
    private val handler: Handler,
    private val onGo: (book: BookInfo, chapter: Int, verse: Int) -> Unit
) {

    private val dialog = Dialog(context)

    private lateinit var titleView: TextView
    private lateinit var listView: RecyclerView
    private lateinit var backView: ImageButton
    private lateinit var selectionView: TextView
    private lateinit var searchView: EditText
    private lateinit var searchHintView: TextView

    private var stage = STAGE_BOOKS
    private var book: BookInfo = BibleMeta.BOOKS.first()
    private var chapter = 1
    private var typedReference: Reference? = null

    fun show(startBook: BookInfo, startChapter: Int, focusSearch: Boolean) {
        book = startBook
        chapter = startChapter.coerceAtLeast(1)

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_passage_picker)

        titleView = dialog.findViewById(R.id.pickerTitle)
        listView = dialog.findViewById(R.id.pickerList)
        backView = dialog.findViewById(R.id.pickerBack)
        selectionView = dialog.findViewById(R.id.pickerSelection)
        searchView = dialog.findViewById(R.id.pickerSearch)
        searchHintView = dialog.findViewById(R.id.pickerSearchHint)

        backView.setOnClickListener { goBack() }
        // Always opens at the start of the chapter, unless a reference was typed.
        dialog.findViewById<ImageButton>(R.id.pickerGo).setOnClickListener { openTarget() }

        searchView.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) = onSearchChanged(s?.toString().orEmpty())
        })
        searchView.setOnEditorActionListener { _, actionId, event ->
            val enter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                openTarget()
                true
            } else {
                false
            }
        }

        showBooks()
        dialog.show()

        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(0))
            setGravity(Gravity.BOTTOM)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * SHEET_HEIGHT_FRACTION).toInt()
            )
        }
        if (focusSearch) {
            searchView.requestFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
        }
    }

    // ------------------------------------------------------------------- steps

    private fun showBooks() {
        stage = STAGE_BOOKS
        backView.visibility = View.GONE
        titleView.text = context.getString(R.string.choose_book)
        updateSelection()
        listView.layoutManager = LinearLayoutManager(context)
        listView.adapter = BookListAdapter { chosen ->
            book = chosen
            chapter = 1
            clearSearch()
            showChapters()
        }
        listView.scrollToPosition(0)
    }

    private fun showChapters() {
        stage = STAGE_CHAPTERS
        backView.visibility = View.VISIBLE
        titleView.text = book.name
        updateSelection()
        listView.layoutManager = GridLayoutManager(context, CHIP_COLUMNS)
        listView.adapter = NumberAdapter(1, BibleMeta.chapterCount(book)) { chosen ->
            chapter = chosen
            showVerses()
        }
        listView.scrollToPosition(0)
    }

    private fun showVerses() {
        stage = STAGE_VERSES
        backView.visibility = View.VISIBLE
        titleView.text = context.getString(R.string.chapter_title, book.name, chapter)
        updateSelection()
        val wantedBook = book
        val wantedChapter = chapter
        executor.execute {
            val count = repository.chapter(wantedBook, wantedChapter)
                ?.verses?.lastOrNull()?.number ?: 0
            handler.post {
                if (stage == STAGE_VERSES && book == wantedBook && chapter == wantedChapter) {
                    bindVerses(count)
                }
            }
        }
    }

    private fun bindVerses(count: Int) {
        if (count <= 0) {
            openTarget()
            return
        }
        listView.layoutManager = GridLayoutManager(context, CHIP_COLUMNS)
        listView.adapter = NumberAdapter(1, count) { verse ->
            clearSearch()
            open(book, chapter, verse)
        }
        listView.scrollToPosition(0)
    }

    private fun goBack() {
        when (stage) {
            STAGE_VERSES -> showChapters()
            STAGE_CHAPTERS -> showBooks()
        }
    }

    // ------------------------------------------------------------------ search

    private fun onSearchChanged(text: String) {
        if (text.isBlank()) {
            typedReference = null
            searchHintView.visibility = View.GONE
            updateSelection()
            return
        }
        val reference = BibleReferences.parse(text)
        typedReference = reference
        if (reference == null) {
            searchHintView.setText(R.string.search_no_match)
            searchHintView.visibility = View.VISIBLE
        } else {
            searchHintView.visibility = View.GONE
        }
        updateSelection()
    }

    private fun clearSearch() {
        if (searchView.text.isNotEmpty()) searchView.setText("")
    }

    private fun updateSelection() {
        val reference = typedReference
        selectionView.text = if (reference != null) {
            context.getString(
                R.string.reference_title,
                reference.book.name,
                reference.chapter,
                reference.verse
            )
        } else {
            context.getString(R.string.chapter_title, book.name, chapter)
        }
    }

    private fun openTarget() {
        val reference = typedReference
        if (reference != null) {
            open(reference.book, reference.chapter, reference.verse)
        } else {
            open(book, chapter, 1)
        }
    }

    private fun open(targetBook: BookInfo, targetChapter: Int, verse: Int) {
        dialog.dismiss()
        onGo(targetBook, targetChapter, verse)
    }

    // ---------------------------------------------------------------- adapters

    private class BookListAdapter(
        private val onClick: (BookInfo) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        class SectionHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.pickerSectionTitle)
        }

        class BookHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById(R.id.bookRowTitle)
        }

        private val rows: List<PickerRow> = buildRows()

        override fun getItemCount(): Int = rows.size

        override fun getItemViewType(position: Int): Int = when (rows[position]) {
            is PickerRow.Section -> TYPE_SECTION
            else -> TYPE_BOOK
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_SECTION) {
                SectionHolder(inflater.inflate(R.layout.item_picker_section, parent, false))
            } else {
                BookHolder(inflater.inflate(R.layout.item_picker_book, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = rows[position]
            when (holder) {
                is SectionHolder -> holder.title.setText((row as PickerRow.Section).labelRes)
                is BookHolder -> {
                    val book = (row as PickerRow.Book).book
                    holder.text.text = book.name
                    holder.itemView.setOnClickListener { onClick(book) }
                }
            }
        }

        private companion object {
            const val TYPE_SECTION = 0
            const val TYPE_BOOK = 1

            /** Section label -> asset index range, canonical order. */
            val OLD_TESTAMENT_SECTIONS = listOf(
                R.string.group_pentateuch to 1..5,
                R.string.group_history to 6..17,
                R.string.group_poetry to 18..22,
                R.string.group_major_prophets to 23..27,
                R.string.group_minor_prophets to 28..39
            )

            val NEW_TESTAMENT_SECTIONS = listOf(
                R.string.group_gospels to 41..44,
                R.string.group_apostolic_history to 45..45,
                R.string.group_pauline to 46..58,
                R.string.group_general to 59..66,
                R.string.group_apocalyptic to 67..67
            )

            fun buildRows(): List<PickerRow> {
                val rows = ArrayList<PickerRow>(BibleMeta.BOOKS.size + 10)
                appendSections(rows, OLD_TESTAMENT_SECTIONS)
                appendSections(rows, NEW_TESTAMENT_SECTIONS)
                return rows
            }

            fun appendSections(rows: MutableList<PickerRow>, sections: List<Pair<Int, IntRange>>) {
                for ((labelRes, range) in sections) {
                    rows.add(PickerRow.Section(labelRes))
                    for (assetIndex in range) {
                        BibleMeta.byAssetIndex(assetIndex)?.let { rows.add(PickerRow.Book(it)) }
                    }
                }
            }
        }
    }

    /** One row of the picker's book list. */
    private sealed class PickerRow {
        class Section(val labelRes: Int) : PickerRow()
        class Book(val book: BookInfo) : PickerRow()
    }

    private class NumberAdapter(
        private val first: Int,
        private val last: Int,
        private val onClick: (Int) -> Unit
    ) : RecyclerView.Adapter<NumberAdapter.Holder>() {

        class Holder(view: View) : RecyclerView.ViewHolder(view) {
            val chip: TextView = view.findViewById(R.id.chipText)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_picker_chip, parent, false))

        override fun getItemCount(): Int = (last - first + 1).coerceAtLeast(0)

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val number = first + position
            holder.chip.text = number.toString()
            holder.itemView.setOnClickListener { onClick(number) }
        }
    }

    private companion object {
        const val STAGE_BOOKS = 0
        const val STAGE_CHAPTERS = 1
        const val STAGE_VERSES = 2
        const val CHIP_COLUMNS = 6
        const val SHEET_HEIGHT_FRACTION = 0.7f
    }
}
