package com.realsneed.biblereader.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.realsneed.biblereader.R
import com.realsneed.biblereader.data.BibleMeta
import com.realsneed.biblereader.data.BibleRepository
import com.realsneed.biblereader.data.BookInfo
import com.realsneed.biblereader.data.Folder
import com.realsneed.biblereader.data.FolderStore
import com.realsneed.biblereader.data.HighlightColor
import com.realsneed.biblereader.data.SavedVerse
import com.realsneed.biblereader.data.VerseStore
import java.util.concurrent.Executors

/**
 * The user's folder tree: topics that verses are filed under, nested to any
 * depth. Drill down with a breadcrumb, create sub-folders with +, rename or
 * delete a folder from its overflow, and long-press a verse to take it out of
 * the folder.
 */
class FoldersActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var store: VerseStore
    private lateinit var folders: FolderStore
    private lateinit var repository: BibleRepository
    private lateinit var adapter: FolderListAdapter
    private lateinit var breadcrumb: LinearLayout
    private lateinit var breadcrumbScroll: android.widget.HorizontalScrollView
    private lateinit var emptyView: TextView

    private var currentFolderId: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_folders)

        store = VerseStore(this)
        folders = FolderStore(store)
        repository = BibleRepository.get(assets)

        breadcrumb = findViewById(R.id.foldersBreadcrumb)
        breadcrumbScroll = findViewById(R.id.foldersBreadcrumbParent)
        emptyView = findViewById(R.id.foldersEmpty)

        adapter = FolderListAdapter(
            numberColor = ContextCompat.getColor(this, R.color.section_title),
            textColor = ContextCompat.getColor(this, R.color.text_primary),
            darkTheme = isDarkTheme(),
            onOpenFolder = { navigateTo(it.id) },
            onFolderMenu = { folder, anchor -> showFolderMenu(folder, anchor) },
            onOpenVerse = { saved, book -> openVerse(saved, book) },
            onVerseMenu = { saved, anchor -> showVerseMenu(saved, anchor) }
        )

        val list = findViewById<RecyclerView>(R.id.foldersList)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter

        findViewById<ImageButton>(R.id.foldersBack).setOnClickListener {
            val id = currentFolderId
            if (id == null) finish() else navigateTo(folders.folder(id)?.parentId)
        }
        findViewById<ImageButton>(R.id.foldersNew).setOnClickListener { promptNewFolder() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val id = currentFolderId
                if (id == null) finish() else navigateTo(folders.folder(id)?.parentId)
            }
        })

        reload()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }

    // ------------------------------------------------------------ navigation

    private fun navigateTo(folderId: Long?) {
        currentFolderId = folderId
        reload()
    }

    private fun reload() {
        val folderId = currentFolderId
        executor.execute {
            val subFolders = folders.children(folderId).map { child ->
                child to getString(
                    R.string.folder_summary,
                    folders.childCount(child.id),
                    folders.subtreeVerseCount(child.id)
                )
            }
            val verses = if (folderId == null) emptyList() else folders.versesIn(folderId)
            val verseRows = verses.mapNotNull { saved ->
                val book = BibleMeta.byId(saved.bookId) ?: return@mapNotNull null
                val text = repository.chapter(book, saved.chapter)
                    ?.verses?.firstOrNull { it.number == saved.verse }?.text.orEmpty()
                val state = store.state(book.id, saved.chapter, saved.verse)
                val note = store.notes(book.id, saved.chapter, saved.verse)
                    .firstOrNull()?.body
                SavedRow(saved, book, text, note, state.color)
            }.sortedWith(
                compareBy(
                    { BibleMeta.positionOfId(it.saved.bookId) },
                    { it.saved.chapter },
                    { it.saved.verse }
                )
            )
            val crumbs = folderId?.let { folders.ancestors(it) } ?: emptyList()
            val currentTitle = folderId?.let { folders.folder(it)?.title }

            handler.post {
                if (folderId != currentFolderId) return@post
                updateBreadcrumb(crumbs, currentTitle, folderId)
                val rows = ArrayList<FolderRow>(subFolders.size + verseRows.size + 1)
                subFolders.forEach { (folder, subtitle) -> rows.add(FolderRow.FolderItem(folder, subtitle)) }
                if (verseRows.isNotEmpty()) {
                    rows.add(FolderRow.Header(getString(R.string.verses_heading)))
                    verseRows.forEach { row ->
                        rows.add(FolderRow.VerseItem(row.saved, row.book, row.text, row.note, row.color))
                    }
                }
                adapter.submit(rows)
                if (rows.isEmpty()) {
                    emptyView.setText(
                        if (folderId == null) R.string.no_folders else R.string.empty_folder
                    )
                    emptyView.visibility = View.VISIBLE
                } else {
                    emptyView.visibility = View.GONE
                }
            }
        }
    }

    // ------------------------------------------------------------- breadcrumb

    private fun updateBreadcrumb(crumbs: List<Folder>, currentTitle: String?, folderId: Long?) {
        breadcrumb.removeAllViews()
        addCrumb(getString(R.string.folders), null, folderId == null)
        crumbs.forEach { folder ->
            addSeparator()
            addCrumb(folder.title, folder.id, false)
        }
        if (folderId != null) {
            addSeparator()
            addCrumb(currentTitle.orEmpty(), folderId, true)
        }
        breadcrumbScroll.post { breadcrumbScroll.fullScroll(View.FOCUS_RIGHT) }
    }

    private fun addCrumb(label: String, folderId: Long?, isCurrent: Boolean) {
        val view = TextView(this)
        view.text = label
        view.textSize = 16f
        view.maxLines = 1
        view.ellipsize = android.text.TextUtils.TruncateAt.END
        view.setPadding(dp(6), dp(8), dp(6), dp(8))
        view.setTextColor(if (isCurrent) Color.WHITE else 0xCCFFFFFF.toInt())
        view.setTypeface(view.typeface, if (isCurrent) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        if (!isCurrent) {
            view.isClickable = true
            view.setBackgroundResource(android.R.drawable.list_selector_background)
            view.setOnClickListener { navigateTo(folderId) }
        }
        breadcrumb.addView(view)
    }

    private fun addSeparator() {
        val chevron = ImageView(this)
        val size = dp(16)
        chevron.layoutParams = LinearLayout.LayoutParams(size, size)
        chevron.setImageResource(R.drawable.ic_chevron_right)
        chevron.setColorFilter(0x99FFFFFF.toInt())
        breadcrumb.addView(chevron)
    }

    // ------------------------------------------------------------ folder menu

    private fun promptNewFolder() {
        promptForTitle(getString(R.string.new_folder), null) { title ->
            folders.create(currentFolderId, title)
            reload()
        }
    }

    private fun showFolderMenu(folder: Folder, anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(Menu.NONE, MENU_RENAME, 0, R.string.rename) 
        popup.menu.add(Menu.NONE, MENU_DELETE, 1, R.string.delete)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_RENAME -> promptForTitle(getString(R.string.rename_folder), folder.title) { title ->
                    folders.rename(folder.id, title)
                    reload()
                }
                MENU_DELETE -> confirmDelete(folder)
            }
            true
        }
        popup.show()
    }

    private fun confirmDelete(folder: Folder) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_folder)
            .setMessage(getString(R.string.delete_folder_confirm, folder.title))
            .setPositiveButton(R.string.delete) { _, _ ->
                if (folder.id == currentFolderId) currentFolderId = folder.parentId
                folders.delete(folder.id)
                reload()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun promptForTitle(titleRes: String, initial: String?, onDone: (String) -> Unit) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_text_input, null)
        val field = view.findViewById<EditText>(R.id.textInputField)
        field.hint = getString(R.string.folder_name)
        if (initial != null) field.setText(initial)
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setView(view)
            .setPositiveButton(R.string.create) { _, _ ->
                val title = field.text.toString().trim()
                if (title.isNotEmpty()) onDone(title)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ------------------------------------------------------------------ verses

    private fun openVerse(saved: SavedVerse, book: BookInfo) {
        setResult(
            Activity.RESULT_OK,
            Intent()
                .putExtra(MarksActivity.EXTRA_BOOK, book.assetIndex)
                .putExtra(MarksActivity.EXTRA_CHAPTER, saved.chapter)
                .putExtra(MarksActivity.EXTRA_VERSE, saved.verse)
        )
        finish()
    }

    private fun showVerseMenu(saved: SavedVerse, anchor: View) {
        val folderId = currentFolderId ?: return
        val popup = PopupMenu(this, anchor)
        popup.menu.add(Menu.NONE, MENU_REMOVE_VERSE, 0, R.string.remove_from_folder)
        popup.setOnMenuItemClickListener { item ->
            if (item.itemId == MENU_REMOVE_VERSE) {
                folders.removeVerse(folderId, saved.bookId, saved.chapter, saved.verse)
                reload()
            }
            true
        }
        popup.show()
    }

    // ------------------------------------------------------------------ helpers

    private fun isDarkTheme(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val MENU_RENAME = 1
        const val MENU_DELETE = 2
        const val MENU_REMOVE_VERSE = 3
    }
}

/** A saved verse resolved for display. */
private class SavedRow(
    val saved: SavedVerse,
    val book: BookInfo,
    val text: String,
    val note: String?,
    val color: HighlightColor?
)

/** One row of the folders screen. */
private sealed class FolderRow {
    class FolderItem(val folder: Folder, val subtitle: String) : FolderRow()
    class VerseItem(
        val saved: SavedVerse,
        val book: BookInfo,
        val text: String,
        val note: String?,
        val color: HighlightColor?
    ) : FolderRow()
    class Header(val label: String) : FolderRow()
}

private class FolderListAdapter(
    private val numberColor: Int,
    private val textColor: Int,
    private val darkTheme: Boolean,
    private val onOpenFolder: (Folder) -> Unit,
    private val onFolderMenu: (Folder, View) -> Unit,
    private val onOpenVerse: (SavedVerse, BookInfo) -> Unit,
    private val onVerseMenu: (SavedVerse, View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    class FolderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.folderTitle)
        val subtitle: TextView = view.findViewById(R.id.folderSubtitle)
        val menu: ImageButton = view.findViewById(R.id.folderMenu)
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.pickerSectionTitle)
    }

    class VerseHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dot: View = view.findViewById(R.id.markDot)
        val text: TextView = view.findViewById(R.id.markText)
        val note: TextView = view.findViewById(R.id.markNote)
    }

    private var rows: List<FolderRow> = emptyList()

    fun submit(newRows: List<FolderRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is FolderRow.FolderItem -> TYPE_FOLDER
        is FolderRow.Header -> TYPE_HEADER
        else -> TYPE_VERSE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_FOLDER -> FolderHolder(inflater.inflate(R.layout.item_folder_row, parent, false))
            TYPE_HEADER -> HeaderHolder(inflater.inflate(R.layout.item_picker_section, parent, false))
            else -> VerseHolder(inflater.inflate(R.layout.item_mark_row, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val row = rows[position]
        when (holder) {
            is FolderHolder -> {
                val item = row as FolderRow.FolderItem
                holder.title.text = item.folder.title
                holder.subtitle.text = item.subtitle
                holder.itemView.setOnClickListener { onOpenFolder(item.folder) }
                holder.menu.setOnClickListener { onFolderMenu(item.folder, it) }
            }
            is HeaderHolder -> holder.title.text = (row as FolderRow.Header).label
            is VerseHolder -> {
                val item = row as FolderRow.VerseItem
                val hasNote = !item.note.isNullOrEmpty()
                holder.text.text = VerseLine.build(
                    holder.itemView.context, item.saved.verse, item.saved.verse,
                    item.text, numberColor, hasNote,
                    "${item.book.name} ${item.saved.chapter}:${item.saved.verse}"
                )
                holder.text.setTextColor(textColor)
                if (hasNote) {
                    holder.note.visibility = View.VISIBLE
                    holder.note.text = item.note
                    holder.note.setTextColor(numberColor)
                } else {
                    holder.note.visibility = View.GONE
                }
                val color = item.color
                if (color == null) {
                    holder.dot.visibility = View.INVISIBLE
                } else {
                    holder.dot.visibility = View.VISIBLE
                    holder.dot.background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color.colorFor(darkTheme))
                    }
                }
                holder.itemView.setOnClickListener { onOpenVerse(item.saved, item.book) }
                holder.itemView.setOnLongClickListener {
                    onVerseMenu(item.saved, it)
                    true
                }
            }
        }
    }

    private companion object {
        const val TYPE_FOLDER = 0
        const val TYPE_HEADER = 1
        const val TYPE_VERSE = 2
    }
}
