package com.realsneed.biblereader.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.realsneed.biblereader.R
import com.realsneed.biblereader.data.BibleMeta
import com.realsneed.biblereader.data.BibleRepository
import com.realsneed.biblereader.data.ReadingClock
import com.realsneed.biblereader.data.BookInfo
import com.realsneed.biblereader.data.VerseStore
import com.realsneed.biblereader.data.VisitedAt
import java.util.concurrent.Executors

/**
 * Where the reader has been, newest first: the chapters turned to and the verses
 * jumped to. Tapping one goes straight back there, with the same jump and flash
 * as everywhere else; long-press a row to remove just that one.
 */
class HistoryActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var store: VerseStore
    private lateinit var adapter: HistoryAdapter
    private lateinit var emptyView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        store = VerseStore(this)
        adapter = HistoryAdapter(
            textColor = textColor(),
            onOpen = { visit -> open(visit) },
            onRemove = { visit -> remove(visit) }
        )

        emptyView = findViewById(R.id.historyEmpty)
        val list = findViewById<RecyclerView>(R.id.historyList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        findViewById<ImageButton>(R.id.historyBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.historyClear).setOnClickListener { clear() }

        reload()
    }

    override fun onResume() {
        super.onResume()
        ReadingClock.resume()
    }

    override fun onPause() {
        ReadingClock.pause(this)
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    private fun reload() {
        val repository = BibleRepository.get(assets)
        executor.execute {
            val rows = store.history().mapNotNull { visit ->
                val book = BibleMeta.byId(visit.bookId) ?: return@mapNotNull null
                HistoryRow(
                    visit = visit,
                    book = book,
                    text = repository.chapter(book, visit.chapter)
                        ?.verses?.firstOrNull { it.number == visit.verse }?.text.orEmpty()
                )
            }
            handler.post {
                adapter.submit(rows)
                emptyView.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun open(visit: VisitedAt) {
        // The reader that receives this result reads the book as an **asset
        // index**; sending the book id ("JHN") made it fall back to 1 and open
        // Genesis. Resolve the id here, where the book is known.
        val book = BibleMeta.byId(visit.bookId) ?: return
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(EXTRA_BOOK, book.assetIndex)
                .putExtra(EXTRA_CHAPTER, visit.chapter)
                .putExtra(EXTRA_VERSE, visit.verse)
        )
        finish()
    }

    private fun remove(visit: VisitedAt) {
        executor.execute {
            store.removeVisit(visit.id)
            handler.post { reload() }
        }
    }

    private fun clear() {
        executor.execute {
            store.clearHistory()
            handler.post {
                Toast.makeText(this, R.string.history_cleared, Toast.LENGTH_SHORT).show()
                reload()
            }
        }
    }

    private fun textColor(): Int =
        if (isDarkTheme()) 0xFFE4E4E4.toInt() else 0xFF1A1A1A.toInt()

    private fun isDarkTheme(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    companion object {
        /** Same contract as the Notes screen: the reader jumps to what comes back. */
        const val EXTRA_BOOK = MarksActivity.EXTRA_BOOK
        const val EXTRA_CHAPTER = MarksActivity.EXTRA_CHAPTER
        const val EXTRA_VERSE = MarksActivity.EXTRA_VERSE
    }
}

/** One row: where it was, and how long ago. */
class HistoryRow(val visit: VisitedAt, val book: BookInfo, val text: String)

private class HistoryAdapter(
    private val textColor: Int,
    private val onOpen: (VisitedAt) -> Unit,
    private val onRemove: (VisitedAt) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val reference: TextView = view.findViewById(R.id.historyReference)
        val whenView: TextView = view.findViewById(R.id.historyWhen)
        val text: TextView = view.findViewById(R.id.historyText)
    }

    private var rows: List<HistoryRow> = emptyList()

    fun submit(newRows: List<HistoryRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_history_row, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = rows[position]
        val visit = row.visit
        // A chapter landing reads "Genesis 12"; a jump reads "John 3:16".
        holder.reference.text = if (visit.verse <= 1) {
            holder.itemView.context.getString(
                R.string.history_chapter, row.book.name, visit.chapter
            )
        } else {
            holder.itemView.context.getString(
                R.string.history_verse, row.book.name, visit.chapter, visit.verse
            )
        }
        holder.whenView.text = DateUtils.getRelativeTimeSpanString(
            visit.viewedAt, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS
        )
        holder.text.text = row.text
        holder.text.setTextColor(textColor)
        holder.itemView.setOnClickListener { onOpen(visit) }
        holder.itemView.setOnLongClickListener { anchor ->
            val popup = PopupMenu(anchor.context, anchor)
            popup.menu.add(Menu.NONE, MENU_REMOVE, 0, R.string.history_remove)
            popup.setOnMenuItemClickListener {
                onRemove(visit)
                true
            }
            popup.show()
            true
        }
    }

    private companion object {
        const val MENU_REMOVE = 1
    }
}
