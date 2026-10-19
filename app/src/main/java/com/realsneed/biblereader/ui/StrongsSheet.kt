package com.realsneed.biblereader.ui

import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ScrollView
import android.widget.TextView
import com.realsneed.biblereader.R
import com.realsneed.biblereader.data.Concordance
import com.realsneed.biblereader.data.StrongsEntry
import com.realsneed.biblereader.data.StrongsRepository
import com.realsneed.biblereader.data.WebsterRepository
import java.util.Locale
import java.util.concurrent.Executor

/**
 * The definition card: what the Hebrew or Greek word behind an English word
 * actually is, and the words the KJV translators used for it.
 *
 * [id] is null when the word held has no original behind it at all - one of the
 * many words the KJV needs to make an English sentence but the Hebrew and Greek
 * do not have. The card then says so and points at the verse's words, rather
 * than opening something else entirely.
 *
 * The header is the only place the word and its verse appear: the card used to
 * repeat the pair in a line under the concordance, which said nothing the
 * header had not already said.
 *
 * [strongs.warmUp] is called as the sheet opens, so the 1.4 MB lexicon is read on
 * the background thread while the sheet is still arriving rather than when a
 * word is tapped.
 */
class StrongsSheet(
    private val context: Context,
    private val strongs: StrongsRepository,
    private val webster: WebsterRepository,
    private val concordance: Concordance,
    private val indexer: Executor,
    private val id: String?,
    private val label: String,
    private val reference: String,
    private val executor: Executor,
    private val onWordStudy: () -> Unit = {},
    private val onConcordance: (String, String) -> Unit = { _, _ -> }
) {

    private val dialog = Dialog(context)
    private var capped = false

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_strongs)

        dialog.findViewById<TextView>(R.id.strongsWord).text = label
        dialog.findViewById<TextView>(R.id.strongsRef).text = reference
        dialog.findViewById<TextView>(R.id.strongsDone).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<View>(R.id.strongsVerseWords).setOnClickListener {
            dialog.dismiss()
            onWordStudy()
        }

        val englishLabel = dialog.findViewById<TextView>(R.id.strongsWebsterLabel)
        val english = dialog.findViewById<TextView>(R.id.strongsWebster)
        val credit = dialog.findViewById<TextView>(R.id.strongsWebsterCredit)
        val divider = dialog.findViewById<View>(R.id.strongsWebsterDivider)
        val concordanceLabel = dialog.findViewById<TextView>(R.id.strongsConcordanceLabel)
        val concordanceRow = dialog.findViewById<TextView>(R.id.strongsConcordance)
        val concordanceDivider = dialog.findViewById<View>(R.id.strongsConcordanceDivider)
        val original = dialog.findViewById<TextView>(R.id.strongsOriginal)
        val transliteration = dialog.findViewById<TextView>(R.id.strongsTransliteration)
        val language = dialog.findViewById<TextView>(R.id.strongsLanguage)
        val definition = dialog.findViewById<TextView>(R.id.strongsDefinition)
        val kjvLabel = dialog.findViewById<TextView>(R.id.strongsKjvLabel)
        val kjv = dialog.findViewById<TextView>(R.id.strongsKjv)

        // Something is shown immediately, then filled in - both lookup tables are
        // quick to read, but this keeps the card from ever appearing empty.
        original.text = label
        transliteration.text = ""
        kjvLabel.visibility = View.GONE
        kjv.visibility = View.GONE
        // Hidden until there is something to say - the index may still be building.
        val concordanceSection = listOf(concordanceLabel, concordanceRow, concordanceDivider)
        concordanceSection.forEach { it.visibility = View.GONE }
        if (id == null) {
            language.text = context.getString(R.string.strongs_none)
            definition.text = context.getString(R.string.strongs_none_body)
        } else {
            language.text = context.getString(R.string.strongs_number, id)
            definition.text = ""
        }

        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(0))
            setGravity(Gravity.BOTTOM)
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val number = id
        executor.execute {
            // The 1828 entry opens with the word itself, as it does in the printed
            // dictionary - "BAPTIZED, ..." - and that name is kept on purpose: it is
            // how the reader confirms the right entry was found.
            val englishEntry = webster.definition(label)
            val englishText = if (englishEntry.isNullOrEmpty()) {
                null
            } else {
                label.uppercase(Locale.ROOT) + ", " + englishEntry
            }

            val entry = if (number == null) null else strongs.entry(number)
            original.post {
                if (!dialog.isShowing) return@post
                if (englishText.isNullOrEmpty()) {
                    englishLabel.visibility = View.GONE
                    english.visibility = View.GONE
                    credit.visibility = View.GONE
                    divider.visibility = View.GONE
                } else {
                    english.text = englishText
                }
                bind(entry, original, transliteration, language, definition, kjvLabel, kjv)
                if (number == null && englishText.isNullOrEmpty()) {
                    definition.text = context.getString(R.string.definition_none)
                    definition.visibility = View.VISIBLE
                }
                capHeight()
            }
            if (number == null) return@execute
            // The index is 348,732 lookups over the whole Bible and is built on the
            // app's index thread. This waits for it without ever blocking the
            // reader's thread, retrying briefly until it is ready.
            publishConcordance(number, original, concordanceDivider, concordanceLabel, concordanceRow, 0)
        }
    }

    /** Adds the concordance row once the index is ready, or shortly after. */
    private fun publishConcordance(
        number: String,
        anchor: View,
        divider: View,
        label: View,
        row: TextView,
        attempt: Int
    ) {
        if (!concordance.isReady) {
            if (attempt < CONCORDANCE_RETRIES) {
                // Ask the index thread for it rather than building it here.
                if (attempt == 0) indexer.execute { concordance.warmUp() }
                anchor.postDelayed({
                    if (dialog.isShowing) {
                        publishConcordance(number, anchor, divider, label, row, attempt + 1)
                    }
                }, CONCORDANCE_RETRY_MS)
            }
            return
        }
        val packed = concordance.references(number) ?: return
        if (packed.isEmpty()) return
        anchor.post {
            if (!dialog.isShowing) return@post
            divider.visibility = View.VISIBLE
            label.visibility = View.VISIBLE
            row.visibility = View.VISIBLE
            row.text = context.resources.getQuantityString(
                R.plurals.concordance_count, packed.size, packed.size
            ) + "  ·  " + context.getString(R.string.concordance_open)
            row.setOnClickListener { onConcordance(number, this.label) }
            capHeight()
        }
    }

    private fun bind(
        entry: StrongsEntry?,
        original: TextView,
        transliteration: TextView,
        language: TextView,
        definition: TextView,
        kjvLabel: TextView,
        kjv: TextView
    ) {
        if (entry == null) {
            original.text = label
            transliteration.text = ""
            definition.text = context.getString(R.string.strongs_missing)
            return
        }
        original.text = entry.original
        transliteration.text = entry.transliteration
        language.text = context.getString(
            if (entry.isHebrew) R.string.strongs_hebrew else R.string.strongs_greek, entry.id
        )
        definition.text = entry.definition
        definition.visibility = if (entry.definition.isEmpty()) View.GONE else View.VISIBLE
        val renderings = entry.kjv
        if (renderings.isNullOrEmpty()) {
            kjvLabel.visibility = View.GONE
            kjv.visibility = View.GONE
        } else {
            kjvLabel.visibility = View.VISIBLE
            kjv.visibility = View.VISIBLE
            kjv.text = renderings
        }
    }

    /** A long definition should not push the sheet off the screen. */
    private fun capHeight() {
        if (capped) return
        val scroll = dialog.findViewById<ScrollView>(R.id.strongsScroll)
        val max = (context.resources.displayMetrics.heightPixels * MAX_HEIGHT_FRACTION).toInt()
        if (scroll.height <= max) return
        val params = scroll.layoutParams
        params.height = max
        scroll.layoutParams = params
        capped = true
    }

    private companion object {
        const val MAX_HEIGHT_FRACTION = 0.62f
        const val CONCORDANCE_RETRIES = 12
        const val CONCORDANCE_RETRY_MS = 250L
    }
}
