package com.realsneed.biblereader.ui

import android.os.Build
import android.text.TextPaint
import android.text.style.CharacterStyle

/**
 * An underline in a colour of its own, leaving the words' colour alone.
 *
 * `UnderlineSpan` paints the line in the text colour, which on a highlighted
 * verse would be invisible and would read as ordinary emphasis. A TextPaint can
 * carry a **separate** underline colour instead, and `TextLine` draws that stroke
 * with its own brush - but only from Android 11 onwards. Older versions collected
 * the span's paint into a shared run and dropped the colour, because
 * `equalAttributes` did not compare it, which is exactly how a span can be set
 * and still draw nothing.
 *
 * So the coloured stroke is used where the platform honours it, and a plain
 * underline (still not touching the text colour) everywhere else. Both are
 * versions of the same mark; only the colour is lost on old devices.
 */
internal class QuotationUnderlineSpan(private val color: Int) : CharacterStyle() {

    override fun updateDrawState(textPaint: TextPaint) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            textPaint.underlineColor = color
        } else {
            textPaint.isUnderlineText = true
        }
    }
}
