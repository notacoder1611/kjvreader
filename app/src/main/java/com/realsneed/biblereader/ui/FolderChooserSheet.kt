package com.realsneed.biblereader.ui

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.Menu
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.realsneed.biblereader.R
import com.realsneed.biblereader.data.BookInfo
import com.realsneed.biblereader.data.Folder
import com.realsneed.biblereader.data.FolderStore

/**
 * Bottom sheet that files a verse into folders.
 *
 * Every folder is listed flattened with indentation so the nesting is visible.
 * Tapping toggles membership and the sheet stays open, so a verse can be filed
 * under several topics in a single visit.
 */
class FolderChooserSheet(
    private val context: Context,
    private val folders: FolderStore,
    private val book: BookInfo,
    private val chapter: Int,
    private val verses: List<Int>,
    private val onChanged: () -> Unit
) {

    private val dialog = Dialog(context)
    private lateinit var adapter: ChoiceAdapter

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_folder_chooser)

        dialog.findViewById<TextView>(R.id.chooserDone).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<LinearLayout>(R.id.chooserNew).setOnClickListener {
            promptNewFolder(parentId = null)
        }

        val list = dialog.findViewById<RecyclerView>(R.id.chooserList)
        list.layoutManager = LinearLayoutManager(context)
        adapter = ChoiceAdapter(
            onToggle = { folder, wasSelected -> toggle(folder, wasSelected) },
            onLongPress = { folder, anchor -> showFolderMenu(folder, anchor) }
        )
        list.adapter = adapter
        reload()

        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(0))
            setGravity(Gravity.BOTTOM)
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (context.resources.displayMetrics.heightPixels * HEIGHT_FRACTION).toInt()
            )
        }
    }

    private fun reload() {
        // A folder counts as chosen only when every verse in the block is in it.
        val perVerse = verses.map { verse ->
            folders.foldersForVerse(book.id, chapter, verse).map { it.id }.toSet()
        }
        val selected = perVerse.reduceOrNull { a, b -> a.intersect(b) } ?: emptySet()
        adapter.submit(flatten(folders.all()), selected)
    }

    private fun toggle(folder: Folder, wasSelected: Boolean) {
        verses.forEach { verse ->
            if (wasSelected) {
                folders.removeVerse(folder.id, book.id, chapter, verse)
            } else {
                folders.addVerse(folder.id, book.id, chapter, verse)
            }
        }
        reload()
        onChanged()
    }

    /** Long-pressing a folder nests a new folder inside it, right where it is needed. */
    private fun showFolderMenu(folder: Folder, anchor: View) {
        val popup = PopupMenu(context, anchor)
        popup.menu.add(Menu.NONE, MENU_NEW_SUBFOLDER, 0, R.string.new_subfolder)
        popup.menu.add(Menu.NONE, MENU_RENAME, 1, R.string.rename)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_NEW_SUBFOLDER -> promptNewFolder(parentId = folder.id)
                MENU_RENAME -> promptRename(folder)
            }
            true
        }
        popup.show()
    }

    /**
     * Creates a folder and files this verse into it straight away - creating a
     * folder while saving a verse should save the verse, not leave you to do it
     * again.
     */
    private fun promptNewFolder(parentId: Long?) {
        promptForTitle(context.getString(R.string.new_folder), null) { title ->
            val id = folders.create(parentId, title)
            verses.forEach { verse -> folders.addVerse(id, book.id, chapter, verse) }
            reload()
            onChanged()
        }
    }

    private fun promptRename(folder: Folder) {
        promptForTitle(context.getString(R.string.rename_folder), folder.title) { title ->
            folders.rename(folder.id, title)
            reload()
        }
    }

    private fun promptForTitle(title: String, initial: String?, onDone: (String) -> Unit) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_text_input, null)
        val field = view.findViewById<EditText>(R.id.textInputField)
        field.hint = context.getString(R.string.folder_name)
        if (initial != null) field.setText(initial)
        AlertDialog.Builder(context)
            .setTitle(title)
            .setView(view)
            .setPositiveButton(R.string.create) { _, _ ->
                val text = field.text.toString().trim()
                if (text.isNotEmpty()) onDone(text)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Depth-first flattening of the tree, so indentation can show the nesting. */
    private fun flatten(all: List<Folder>): List<Pair<Folder, Int>> {
        val byParent = all.groupBy { it.parentId }
        val out = ArrayList<Pair<Folder, Int>>(all.size)
        fun walk(parentId: Long?, depth: Int) {
            byParent[parentId]?.forEach { folder ->
                out.add(folder to depth)
                walk(folder.id, depth + 1)
            }
        }
        walk(null, 0)
        return out
    }

    private companion object {
        const val HEIGHT_FRACTION = 0.6f
        const val MENU_NEW_SUBFOLDER = 1
        const val MENU_RENAME = 2
    }
}

private class ChoiceAdapter(
    private val onToggle: (Folder, Boolean) -> Unit,
    private val onLongPress: (Folder, View) -> Unit
) : RecyclerView.Adapter<ChoiceAdapter.Holder>() {

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.choiceTitle)
        val check: ImageView = view.findViewById(R.id.choiceCheck)
    }

    private var items: List<Pair<Folder, Int>> = emptyList()
    private var selected: Set<Long> = emptySet()

    fun submit(newItems: List<Pair<Folder, Int>>, newSelected: Set<Long>) {
        items = newItems
        selected = newSelected
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_folder_choice, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val (folder, depth) = items[position]
        holder.title.text = folder.title
        val density = holder.itemView.resources.displayMetrics.density
        holder.itemView.setPadding(
            ((20 + depth * 18) * density).toInt(),
            holder.itemView.paddingTop,
            holder.itemView.paddingRight,
            holder.itemView.paddingBottom
        )
        val isSelected = folder.id in selected
        holder.check.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.itemView.setOnClickListener {
            onToggle(folder, isSelected)
        }
        holder.itemView.setOnLongClickListener { anchor ->
            onLongPress(folder, anchor)
            true
        }
    }
}
