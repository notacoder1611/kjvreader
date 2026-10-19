package com.realsneed.biblereader.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.Spannable
import android.text.SpannableString
import android.text.TextWatcher
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.realsneed.biblereader.R
import com.realsneed.biblereader.data.BibleRepository
import com.realsneed.biblereader.data.ReadingClock
import com.realsneed.biblereader.data.VerseSearch
import java.util.concurrent.Executors

/**
 * Word search across the whole KJV.
 *
 * The index is built on this screen's background thread the first time it opens,
 * so nothing else in the app pays for it. Typing is debounced, queries run off the
 * main thread, and the matched words are bold in every result. Tapping a result
 * hands its book, chapter and verse back to the reader, like the history screen.
 */
class SearchActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var search: VerseSearch
    private lateinit var adapter: SearchAdapter
    private lateinit var input: EditText
    private lateinit var status: TextView
    private var pending: Runnable? = null

    @Volatile private var built = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)

        search = VerseSearch(BibleRepository.get(assets))
        adapter = SearchAdapter { hit -> open(hit) }

        val list = findViewById<RecyclerView>(R.id.searchList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        status = findViewById(R.id.searchStatus)
        input = findViewById(R.id.searchInput)
        findViewById<ImageButton>(R.id.searchBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.searchClear).setOnClickListener { input.setText("") }

        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = schedule()
        })

        buildIndex()
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
        pending?.let { handler.removeCallbacks(it) }
        super.onDestroy()
        executor.shutdown()
    }

    private fun buildIndex() {
        status.visibility = View.VISIBLE
        status.text = getString(R.string.search_building)
        executor.execute {
            search.build()
            handler.post {
                built = true
                status.text = getString(R.string.search_words_hint)
                runQuery(input.text.toString())
            }
        }
    }

    private fun schedule() {
        pending?.let { handler.removeCallbacks(it) }
        val task = Runnable { runQuery(input.text.toString()) }
        pending = task
        handler.postDelayed(task, DEBOUNCE_MS)
    }

    private fun runQuery(text: String) {
        if (!built) return
        val asked = text.trim()
        if (asked.length < 2) {
            adapter.submit(emptyList(), emptyList())
            status.visibility = View.VISIBLE
            status.text = getString(R.string.search_words_hint)
            return
        }
        executor.execute {
            val hits = search.search(asked)
            val terms = search.terms(asked)
            handler.post {
                adapter.submit(hits, terms)
                status.visibility = if (hits.isEmpty()) View.VISIBLE else View.GONE
                if (hits.isEmpty()) status.text = getString(R.string.search_none, asked)
            }
        }
    }

    private fun open(hit: VerseSearch.Hit) {
        // The same contract the history and notes screens use: the reader reads
        // the book as an asset index, not as an id.
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(MarksActivity.EXTRA_BOOK, hit.book.assetIndex)
                .putExtra(MarksActivity.EXTRA_CHAPTER, hit.chapter)
                .putExtra(MarksActivity.EXTRA_VERSE, hit.verse)
        )
        finish()
    }

    private companion object {
        const val DEBOUNCE_MS = 180L
    }
}

/** One result: where the verse is, and the verse with the asked words in bold. */
private class SearchAdapter(
    private val onOpen: (VerseSearch.Hit) -> Unit
) : RecyclerView.Adapter<SearchAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val reference: TextView = view.findViewById(R.id.searchReference)
        val text: TextView = view.findViewById(R.id.searchText)
    }

    private var hits: List<VerseSearch.Hit> = emptyList()
    private var terms: List<String> = emptyList()

    fun submit(newHits: List<VerseSearch.Hit>, newTerms: List<String>) {
        hits = newHits
        terms = newTerms
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = hits.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_search_row, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val hit = hits[position]
        holder.reference.text = holder.itemView.context.getString(
            R.string.search_reference, hit.book.name, hit.chapter, hit.verse
        )
        holder.text.text = highlight(hit.text, terms)
        holder.itemView.setOnClickListener { onOpen(hit) }
    }

    /** Bold every place the asked words appear, so a long verse shows why it matched. */
    private fun highlight(text: String, terms: List<String>): CharSequence {
        if (terms.isEmpty()) return text
        val spannable = SpannableString(text)
        for (term in terms) {
            if (term.length < 2) continue
            var from = text.indexOf(term, ignoreCase = true)
            while (from >= 0) {
                spannable.setSpan(
                    StyleSpan(Typeface.BOLD),
                    from,
                    from + term.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                from = text.indexOf(term, from + term.length, ignoreCase = true)
            }
        }
        return spannable
    }
}
