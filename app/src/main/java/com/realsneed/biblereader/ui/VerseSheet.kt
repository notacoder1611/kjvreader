package com.realsneed.biblereader.ui

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.os.Build
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.realsneed.biblereader.R
import com.realsneed.biblereader.data.BookInfo
import com.realsneed.biblereader.data.CrossReferences
import com.realsneed.biblereader.data.FolderStore
import com.realsneed.biblereader.data.Footnote
import com.realsneed.biblereader.data.HighlightColor
import com.realsneed.biblereader.data.Quotation
import com.realsneed.biblereader.data.Verse
import com.realsneed.biblereader.data.VerseNote
import com.realsneed.biblereader.data.VerseStore

/**
 * The **Verse menu** - the sheet that opens when a verse is tapped.
 *
 * Everything about a verse lives here: highlight colour, folders, notes and the
 * copy action. It also accepts **several verses at once** (chosen by holding a
 * verse in the reader), in which case every action is applied as a block and the
 * reference is compacted - `John 3:15-16 (KJV)` rather than one line per verse.
 */
class VerseSheet(
    private val context: Context,
    private val store: VerseStore,
    private val folderStore: FolderStore,
    private val book: BookInfo,
    private val chapter: Int,
    private val verses: List<Verse>,
    private val darkTheme: Boolean,
    private val showFootnotes: Boolean,
    private val crossReferences: CrossReferences,
    private val onChanged: () -> Unit,
    private val onDismissed: () -> Unit = {},
    private val onWordStudy: () -> Unit = {},
    private val onSelectVerses: () -> Unit = {},
    private val onQuotation: (Quotation) -> Unit = {}
) {

    private val dialog = Dialog(context)
    private val numbers: List<Int> = verses.map { it.number }.sorted()
    private val single: Boolean = verses.size == 1
    private var selectedColor: HighlightColor? = null
    private var heightCapped = false

    /**
     * Set when a row in this sheet opens something else. `Dialog.dismiss()` posts
     * its dismiss callback rather than calling it there and then, so without this
     * the "I was dismissed" cleanup would run *after* the next screen had already
     * been set up - and would undo it.
     */
    private var openedSomething = false

    private lateinit var colorRow: LinearLayout
    private lateinit var noteList: LinearLayout
    private lateinit var notesScroll: ScrollView
    private lateinit var noteField: EditText
    private lateinit var folderLabel: TextView

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_verse_actions)

        dialog.findViewById<TextView>(R.id.verseRef).text = referenceText()
        dialog.findViewById<TextView>(R.id.versePreview).text =
            verses.joinToString(" ") { it.text }
        dialog.findViewById<TextView>(R.id.verseDone).setOnClickListener { dialog.dismiss() }

        colorRow = dialog.findViewById(R.id.verseColors)
        noteList = dialog.findViewById(R.id.verseNoteList)
        notesScroll = dialog.findViewById(R.id.verseNotesScroll)
        noteField = dialog.findViewById(R.id.verseNote)
        folderLabel = dialog.findViewById(R.id.verseFolderLabel)

        dialog.findViewById<View>(R.id.verseFolders).setOnClickListener { openFolderChooser() }
        dialog.findViewById<ImageButton>(R.id.verseCopy).apply {
            setOnClickListener { copyText() }
            setOnLongClickListener { copyReferenceOnly(); true }
        }
        dialog.findViewById<ImageButton>(R.id.verseAddNote).setOnClickListener { addNote() }

        // Word study opens over the reader, so this sheet steps out of the way.
        dialog.findViewById<View>(R.id.verseWordStudy).setOnClickListener {
            openedSomething = true
            dialog.dismiss()
            onWordStudy()
        }
        val selectVerses = dialog.findViewById<View>(R.id.verseSelectVerses)
        if (single) {
            selectVerses.setOnClickListener {
                openedSomething = true
                dialog.dismiss()
                onSelectVerses()
            }
        } else {
            // A block is already being picked out; select it from the reader.
            selectVerses.visibility = View.GONE
        }
        noteField.setOnEditorActionListener { _, actionId, event ->
            val enter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_SEND || enter) {
                addNote()
                true
            } else {
                false
            }
        }

        if (single) {
            selectedColor = store.state(book.id, chapter, numbers.first()).color
        } else {
            selectedColor = sharedColour()
        }
        reloadNotes()
        reloadFootnotes()
        reloadQuotations()

        buildColorChips()
        updateFolderLabel()

        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener { if (!openedSomething) onDismissed() }
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(0))
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    // ---------------------------------------------------------------- reference

    private fun referenceText(): String = if (single) {
        context.getString(R.string.reference_full, book.name, chapter, numbers.first())
    } else {
        context.getString(R.string.reference_span, book.name, compactReference())
    }

    /** "3:15-16" for a run, "3:15,17" for gaps. */
    private fun compactReference(): String {
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

    // ------------------------------------------------------------------- notes

    private fun reloadNotes() {
        val notes = notesForSelection()
        noteList.removeAllViews()
        if (notes.isEmpty()) {
            val empty = TextView(context)
            empty.text = context.getString(R.string.no_notes_yet)
            empty.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            empty.textSize = 14f
            empty.setPadding(dp(20), dp(4), dp(20), dp(4))
            noteList.addView(empty)
        } else {
            val inflater = LayoutInflater.from(context)
            notes.forEach { note ->
                val row = inflater.inflate(R.layout.item_verse_note, noteList, false)
                row.findViewById<TextView>(R.id.noteBody).text = noteText(note)
                row.findViewById<ImageButton>(R.id.noteDelete).setOnClickListener {
                    store.deleteNote(note.id)
                    reloadNotes()
                    onChanged()
                }
                noteList.addView(row)
            }
        }
        capNotesHeight()
    }

    /** The KJV's own margin notes for what is open, if the reader shows them. */
    private fun reloadFootnotes() {
        val heading = dialog.findViewById<View>(R.id.verseFootnotesHeading)
        val list = dialog.findViewById<LinearLayout>(R.id.verseFootnoteList)
        list.removeAllViews()
        val notes = ArrayList<Pair<Int, Footnote>>(4)
        for (verse in verses) {
            for (note in verse.footnotes) notes.add(verse.number to note)
        }
        if (!showFootnotes || notes.isEmpty()) {
            heading.visibility = View.GONE
            list.visibility = View.GONE
            return
        }
        heading.visibility = View.VISIBLE
        list.visibility = View.VISIBLE
        val inflater = LayoutInflater.from(context)
        for ((verseNumber, note) in notes) {
            val row = inflater.inflate(R.layout.item_verse_footnote, list, false)
            val label = StringBuilder()
            if (verses.size > 1) label.append(verseNumber).append(" · ")
            if (note.reference.isNotEmpty()) label.append(note.reference)
            if (note.quote != null) {
                if (label.isNotEmpty()) label.append(" · ")
                label.append(note.quote)
            }
            row.findViewById<TextView>(R.id.footnoteRef).text = label
            row.findViewById<TextView>(R.id.footnoteText).text = note.text
            list.addView(row)
        }
    }

    /**
     * What the open verse quotes - listed only where the phrase really is in the
     * text, so the menu and the underlines on the page can never disagree.
     */
    private fun reloadQuotations() {
        val heading = dialog.findViewById<View>(R.id.verseQuotationsHeading)
        val list = dialog.findViewById<LinearLayout>(R.id.verseQuotationList)
        list.removeAllViews()

        val found = ArrayList<Quotation>(2)
        for (verse in verses) {
            for (quotation in crossReferences.forVerse(book.id, chapter, verse.number)) {
                if (crossReferences.rangeIn(verse.text, quotation.phrase) == null) continue
                found.add(quotation)
            }
        }
        if (found.isEmpty()) {
            heading.visibility = View.GONE
            list.visibility = View.GONE
            return
        }
        heading.visibility = View.VISIBLE
        list.visibility = View.VISIBLE

        val inflater = LayoutInflater.from(context)
        val seen = HashSet<String>(4)
        for (quotation in found) {
            if (!seen.add(quotation.reference + "|" + quotation.phrase)) continue
            val row = inflater.inflate(R.layout.item_verse_quotation, list, false)
            row.findViewById<TextView>(R.id.quotationRef).text = quotation.reference
            row.findViewById<TextView>(R.id.quotationPhrase).text =
                context.getString(R.string.quotation_phrase, quotation.phrase)
            row.setOnClickListener {
                dialog.dismiss()
                onQuotation(quotation)
            }
            list.addView(row)
        }
    }

    /** Every note covering any verse of what is open, each listed once. */
    private fun notesForSelection(): List<VerseNote> {
        val seen = HashMap<Long, VerseNote>(4)
        numbers.forEach { verse ->
            store.notesCovering(book.id, chapter, verse).forEach { seen[it.id] = it }
        }
        return seen.values.sortedBy { it.startVerse }
    }

    /** A note that spans verses says so in front of its text. */
    private fun noteText(note: VerseNote): CharSequence {
        if (!note.isRange) return note.body
        val spannable = SpannableStringBuilder()
        val start = spannable.length
        spannable.append(note.reference(chapter))
        spannable.setSpan(StyleSpan(Typeface.BOLD), start, spannable.length, SPAN)
        spannable.setSpan(
            ForegroundColorSpan(ContextCompat.getColor(context, R.color.section_title)),
            start, spannable.length, SPAN
        )
        spannable.append("  ")
        spannable.append(note.body)
        return spannable
    }

    /** One note covering the whole selection, rather than one per verse. */
    private fun addNote() {
        val body = noteField.text?.toString()?.trim().orEmpty()
        if (body.isEmpty()) return
        store.addNote(book.id, chapter, numbers.first(), numbers.last(), body)
        noteField.setText("")
        reloadNotes()
        onChanged()
    }

    private fun capNotesHeight() {
        if (heightCapped) return
        notesScroll.post {
            if (heightCapped) return@post
            val max = (context.resources.displayMetrics.heightPixels * NOTES_MAX_FRACTION).toInt()
            if (notesScroll.height > max) {
                val params = notesScroll.layoutParams
                params.height = max
                notesScroll.layoutParams = params
                heightCapped = true
            }
        }
    }

    // -------------------------------------------------------------------- copy

    /** Plain, unstyled text - what you would paste into Discord. */
    private fun copyText() {
        copy(verses.joinToString(" ") { it.text } + "\n" + referenceText(), R.string.copied_verse)
    }

    private fun copyReferenceOnly() {
        val reference = referenceText()
        copy(reference, R.string.copied_reference, reference)
    }

    private fun copy(text: String, messageRes: Int, vararg formatArgs: Any) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text))
        // Android 13 and later show their own copy confirmation, so don't double up.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            Toast.makeText(context, context.getString(messageRes, *formatArgs), Toast.LENGTH_SHORT)
                .show()
        }
    }

    // ------------------------------------------------------------------- chips

    private fun sharedColour(): HighlightColor? {
        val colours = numbers.map { store.state(book.id, chapter, it).color }
        return if (colours.isNotEmpty() && colours.all { it == colours.first() }) colours.first()
        else null
    }

    private fun buildColorChips() {
        colorRow.removeAllViews()
        for (color in HighlightColor.values()) {
            colorRow.addView(colorChip(color))
        }
        colorRow.addView(clearChip())
    }

    private fun colorChip(color: HighlightColor): View {
        val chip = TextView(context)
        chip.layoutParams = chipParams()
        chip.background = circle(color.colorFor(darkTheme), color == selectedColor)
        chip.contentDescription = context.getString(colorDescription(color))
        chip.setOnClickListener {
            selectedColor = color
            numbers.forEach { store.setColor(book.id, chapter, it, color) }
            buildColorChips()
            onChanged()
        }
        return chip
    }

    private fun clearChip(): View {
        val chip = TextView(context)
        chip.layoutParams = chipParams()
        chip.text = context.getString(R.string.highlight_none)
        chip.gravity = Gravity.CENTER
        chip.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
        chip.textSize = 18f
        chip.background = circle(
            ContextCompat.getColor(context, R.color.chip_bg), selectedColor == null
        )
        chip.contentDescription = context.getString(R.string.cd_color_none)
        chip.setOnClickListener {
            selectedColor = null
            numbers.forEach { store.setColor(book.id, chapter, it, null) }
            buildColorChips()
            onChanged()
        }
        return chip
    }

    private fun chipParams(): LinearLayout.LayoutParams {
        val size = dp(42)
        val params = LinearLayout.LayoutParams(size, size)
        params.rightMargin = dp(12)
        return params
    }

    private fun circle(fill: Int, ring: Boolean): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
            if (ring) setStroke(dp(3), ContextCompat.getColor(context, R.color.text_primary))
        }

    private fun colorDescription(color: HighlightColor): Int = when (color) {
        HighlightColor.YELLOW -> R.string.cd_color_yellow
        HighlightColor.GREEN -> R.string.cd_color_green
        HighlightColor.BLUE -> R.string.cd_color_blue
        HighlightColor.RED -> R.string.cd_color_red
    }

    // ----------------------------------------------------------------- folders

    private fun updateFolderLabel() {
        val names = if (single) {
            folderStore.foldersForVerse(book.id, chapter, numbers.first()).map { it.title }
        } else {
            emptyList()
        }
        folderLabel.text = if (names.isEmpty()) {
            context.getString(R.string.save_to_folder)
        } else {
            context.getString(R.string.in_folders, names.joinToString(", "))
        }
    }

    private fun openFolderChooser() {
        FolderChooserSheet(context, folderStore, book, chapter, numbers) {
            updateFolderLabel()
        }.show()
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val NOTES_MAX_FRACTION = 0.34f
        const val SPAN = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
    }
}
