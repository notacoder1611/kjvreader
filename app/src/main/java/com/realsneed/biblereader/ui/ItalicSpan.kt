package com.realsneed.biblereader.ui

import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan

/**
 * Italic that cannot be missed.
 *
 * The obvious way to italicise a word is `StyleSpan(Typeface.ITALIC)`, but that
 * only *asks* the current typeface for its italic face - and plenty of device
 * fonts, including some of the ones Android ships as `sans-serif` and `serif`,
 * have no italic face at all, so the word comes out looking exactly like the
 * words around it. This span both asks for the italic face and slants the text
 * itself, so italics show on every device.
 *
 * Android resets the paint for each run before applying spans, so the slant
 * cannot leak into neighbouring words (this is how the framework's own
 * fake-bold span works).
 */
internal class ItalicSpan : MetricAffectingSpan() {

    override fun updateDrawState(textPaint: TextPaint) {
        textPaint.typeface = Typeface.create(textPaint.typeface, Typeface.ITALIC)
        textPaint.textSkewX = SKEW
    }

    override fun updateMeasureState(textPaint: TextPaint) {
        textPaint.typeface = Typeface.create(textPaint.typeface, Typeface.ITALIC)
        textPaint.textSkewX = SKEW
    }

    private companion object {
        /** The same slant Android uses for "fake italic". */
        const val SKEW = -0.25f
    }
}
