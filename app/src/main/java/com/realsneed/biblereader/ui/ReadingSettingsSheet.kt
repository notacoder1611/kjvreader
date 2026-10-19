package com.realsneed.biblereader.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.realsneed.biblereader.R
import com.realsneed.biblereader.data.ReadingPrefs
import java.util.Locale

/**
 * Reading settings: text size, line spacing, serif or sans, and the page colour.
 *
 * Everything applies live - the sample above the controls is drawn with exactly
 * the same values as the reader, so what you see is what you get.
 */
class ReadingSettingsSheet(
    private val context: Context,
    private val prefs: ReadingPrefs,
    private val systemDark: Boolean,
    private val onChanged: () -> Unit,
    private val quotationCount: Int
) {

    private val dialog = Dialog(context)

    private lateinit var preview: TextView
    private lateinit var sizeSeek: SeekBar
    private lateinit var spacingSeek: SeekBar
    private lateinit var sizeValue: TextView
    private lateinit var spacingValue: TextView
    private lateinit var fontSans: TextView
    private lateinit var fontSerif: TextView
    private lateinit var italicsOn: TextView
    private lateinit var italicsOff: TextView
    private lateinit var footnotesOn: TextView
    private lateinit var footnotesOff: TextView
    private lateinit var quotationsOn: TextView
    private lateinit var quotationsOff: TextView
    private lateinit var quotationsLabel: TextView
    private lateinit var themeLabel: TextView
    private lateinit var themeRow: LinearLayout

    fun show() {
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_reading_settings)

        preview = dialog.findViewById(R.id.readingPreview)
        sizeSeek = dialog.findViewById(R.id.readingSizeSeek)
        spacingSeek = dialog.findViewById(R.id.readingSpacingSeek)
        sizeValue = dialog.findViewById(R.id.readingSizeValue)
        spacingValue = dialog.findViewById(R.id.readingSpacingValue)
        fontSans = dialog.findViewById(R.id.readingFontSans)
        fontSerif = dialog.findViewById(R.id.readingFontSerif)
        italicsOn = dialog.findViewById(R.id.readingItalicsOn)
        italicsOff = dialog.findViewById(R.id.readingItalicsOff)
        footnotesOn = dialog.findViewById(R.id.readingFootnotesOn)
        footnotesOff = dialog.findViewById(R.id.readingFootnotesOff)
        quotationsOn = dialog.findViewById(R.id.readingQuotationsOn)
        quotationsOff = dialog.findViewById(R.id.readingQuotationsOff)
        quotationsLabel = dialog.findViewById(R.id.readingQuotations)
        themeLabel = dialog.findViewById(R.id.readingThemeLabel)
        themeRow = dialog.findViewById(R.id.readingThemeRow)

        dialog.findViewById<TextView>(R.id.readingDone).setOnClickListener { dialog.dismiss() }

        sizeSeek.max = (ReadingPrefs.MAX_SIZE - ReadingPrefs.MIN_SIZE).toInt()
        sizeSeek.progress = (prefs.textSizeSp - ReadingPrefs.MIN_SIZE).toInt()
        sizeSeek.setOnSeekBarChangeListener(onSeek { value ->
            prefs.textSizeSp = ReadingPrefs.MIN_SIZE + value
            refresh()
        })

        spacingSeek.max = ((ReadingPrefs.MAX_SPACING - ReadingPrefs.MIN_SPACING) * 100f).toInt()
        spacingSeek.progress = ((prefs.lineSpacing - ReadingPrefs.MIN_SPACING) * 100f).toInt()
        spacingSeek.setOnSeekBarChangeListener(onSeek { value ->
            prefs.lineSpacing = ReadingPrefs.MIN_SPACING + value / 100f
            refresh()
        })

        fontSans.setOnClickListener {
            prefs.serif = false
            refresh()
        }
        fontSerif.setOnClickListener {
            prefs.serif = true
            refresh()
        }

        italicsOn.setOnClickListener {
            prefs.showItalics = true
            refresh()
            onChanged()
        }
        italicsOff.setOnClickListener {
            prefs.showItalics = false
            refresh()
            onChanged()
        }
        footnotesOn.setOnClickListener {
            prefs.showFootnotes = true
            refresh()
            onChanged()
        }
        footnotesOff.setOnClickListener {
            prefs.showFootnotes = false
            refresh()
            onChanged()
        }
        quotationsOn.setOnClickListener {
            prefs.showQuotations = true
            refresh()
            onChanged()
        }
        quotationsOff.setOnClickListener {
            prefs.showQuotations = false
            refresh()
            onChanged()
        }

        refresh()
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

    private fun onSeek(onValue: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) =
            onValue(progress)

        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun refresh() {
        val theme = ReadingThemes.resolve(context, prefs.background, systemDark)

        // The sample is Genesis 1:16 exactly as the data has it - "he made" really
        // is a supplied word there, and the verse really does carry a footnote -
        // so the toggles below can be watched working rather than taken on trust.
        val sample = SpannableStringBuilder()
        sample.append(context.getString(R.string.reading_preview))
        sample.append("\n")
        val second = context.getString(R.string.reading_preview_two)
        val lineStart = sample.length
        sample.append(second)
        if (prefs.showItalics) {
            val at = second.indexOf(SUPPLIED_WORDS)
            if (at >= 0) {
                sample.setSpan(
                    ItalicSpan(),
                    lineStart + at,
                    lineStart + at + SUPPLIED_WORDS.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        if (prefs.showFootnotes) sample.append("  \u2021")

        // A third line shows a real quotation: Mark 1:3 repeats Isaiah 40:3 nearly
        // word for word, and this is precisely the phrase the reader underlines.
        val third = context.getString(R.string.reading_preview_three)
        sample.append("\n")
        val thirdStart = sample.length
        sample.append(third)
        if (prefs.showQuotations) {
            val at = third.indexOf(QUOTED_WORDS)
            if (at >= 0) {
                sample.setSpan(
                    QuotationUnderlineSpan(
                        ContextCompat.getColor(context, R.color.quotation_underline)
                    ),
                    thirdStart + at,
                    thirdStart + at + QUOTED_WORDS.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        preview.text = sample

        sizeValue.text = context.getString(R.string.text_size_value, prefs.textSizeSp.toInt())
        spacingValue.text = context.getString(
            R.string.line_spacing_value,
            String.format(Locale.ROOT, "%.2f", prefs.lineSpacing)
        )

        val on = ContextCompat.getDrawable(context, R.drawable.chip_pill_on)
        val off = ContextCompat.getDrawable(context, R.drawable.chip_pill)
        fontSans.background = if (prefs.serif) off else on
        fontSerif.background = if (prefs.serif) on else off
        fontSans.setTextColor(if (prefs.serif) theme.text else 0xFFFFFFFF.toInt())
        fontSerif.setTextColor(if (prefs.serif) 0xFFFFFFFF.toInt() else theme.text)

        italicsOn.background = if (prefs.showItalics) on else off
        italicsOff.background = if (prefs.showItalics) off else on
        italicsOn.setTextColor(if (prefs.showItalics) 0xFFFFFFFF.toInt() else theme.text)
        italicsOff.setTextColor(if (prefs.showItalics) theme.text else 0xFFFFFFFF.toInt())

        footnotesOn.background = if (prefs.showFootnotes) on else off
        footnotesOff.background = if (prefs.showFootnotes) off else on
        footnotesOn.setTextColor(if (prefs.showFootnotes) 0xFFFFFFFF.toInt() else theme.text)
        footnotesOff.setTextColor(if (prefs.showFootnotes) theme.text else 0xFFFFFFFF.toInt())

        quotationsOn.background = if (prefs.showQuotations) on else off
        quotationsOff.background = if (prefs.showQuotations) off else on
        quotationsOn.setTextColor(if (prefs.showQuotations) 0xFFFFFFFF.toInt() else theme.text)
        quotationsOff.setTextColor(if (prefs.showQuotations) theme.text else 0xFFFFFFFF.toInt())
        quotationsLabel.text = if (quotationCount > 0) {
            context.getString(R.string.quotations_label_count, quotationCount)
        } else {
            context.getString(R.string.quotations_label)
        }

        themeLabel.text = context.getString(ReadingThemes.labelRes(prefs.background))
        buildSwatches()

        preview.background = ColorDrawable(theme.background)
        preview.setTextColor(theme.text)
        preview.textSize = prefs.textSizeSp
        preview.typeface = if (prefs.serif) Typeface.SERIF else Typeface.SANS_SERIF
        preview.setLineSpacing(0f, prefs.lineSpacing)

        onChanged()
    }

    private fun buildSwatches() {
        themeRow.removeAllViews()
        ReadingThemes.ALL.forEach { id ->
            val themeId = id
            val swatch = View(context)
            val size = dp(40)
            val params = LinearLayout.LayoutParams(size, size)
            params.rightMargin = dp(14)
            swatch.layoutParams = params
            val selected = themeId == prefs.background
            swatch.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ReadingThemes.resolve(context, themeId, systemDark).background)
                setStroke(
                    dp(if (selected) 3 else 1),
                    ContextCompat.getColor(
                        context,
                        if (selected) R.color.section_title else R.color.sheet_separator
                    )
                )
            }
            swatch.contentDescription = context.getString(ReadingThemes.labelRes(themeId))
            swatch.setOnClickListener {
                prefs.background = themeId
                refresh()
            }
            themeRow.addView(swatch)
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private companion object {
        const val HEIGHT_FRACTION = 0.8f

        /** The supplied words in the sample line, italicised to show the toggle. */
        const val SUPPLIED_WORDS = "he made"

        /** The quoted words in the third sample line, underlined to show the toggle. */
        const val QUOTED_WORDS = "in the wilderness, Prepare ye the way of the Lord, make"
    }
}
