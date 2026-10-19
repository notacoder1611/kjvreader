package com.realsneed.biblereader.ui

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan

/**
 * Builds the "16   For God so loved the world..." line used by the list screens.
 *
 * The note marker is a **typographic dagger**, i.e. ordinary text. Earlier it was
 * a Unicode pencil (which some device fonts do not have, so it vanished) and then
 * a drawn [android.text.style.ImageSpan] (which is painted at a computed offset
 * instead of being laid out, so it drifted and landed on top of the first word).
 * A plain character is positioned by the same text engine as the verse itself and
 * therefore cannot drift - and U+2020 is present in every Android font.
 */
internal object VerseLine {

    /** Dagger: the traditional note mark. */
    private const val NOTE_CHAR = "\u2020"

    fun appendNoteMark(spannable: SpannableStringBuilder, context: Context, tint: Int) {
        spannable.append("  ")
        val start = spannable.length
        spannable.append(NOTE_CHAR)
        spannable.setSpan(RelativeSizeSpan(0.9f), start, spannable.length, SPAN)
        spannable.setSpan(ForegroundColorSpan(tint), start, spannable.length, SPAN)
    }

    fun build(
        context: Context,
        verseNumber: Int,
        endVerse: Int,
        text: String,
        numberColor: Int,
        hasNote: Boolean,
        reference: String? = null
    ): CharSequence {
        val spannable = SpannableStringBuilder()
        val start = spannable.length
        // A folder can hold verses from anywhere, so it shows "John 3:16" rather
        // than a bare "16"; the Notes screen heads each chapter, so a number is
        // enough there.
        spannable.append(
            reference ?: if (endVerse > verseNumber) "$verseNumber-$endVerse" else "$verseNumber"
        )
        spannable.setSpan(StyleSpan(Typeface.BOLD), start, spannable.length, SPAN)
        spannable.setSpan(ForegroundColorSpan(numberColor), start, spannable.length, SPAN)
        spannable.append("   ")
        spannable.append(text)
        if (hasNote) appendNoteMark(spannable, context, numberColor)
        return spannable
    }

    private const val SPAN = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
}
