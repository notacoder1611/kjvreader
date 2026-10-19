package com.realsneed.biblereader.data

import android.content.Context

/**
 * The words the biblical writers quote, read from `assets/xrefs`.
 *
 * Built by `tools/build_xrefs.py` from openbible.info's cross-references (CC BY
 * 4.0) and cut down to quotations: a reference survives only if the two verses
 * share a **contiguous run of words**, four or more long, that is not the KJV's
 * own boilerplate, not mostly names, and contains at least two words that carry
 * meaning. Thematic cross-references - "this verse is about the same thing" - do
 * not survive, which is the point: an underline should only appear where the
 * writer actually reached back for the words.
 *
 * The mark goes on the verse that *quotes*, so a pair is only kept for the later
 * book - with Mark ranked before Matthew and Luke, since they used him.
 *
 * The asset is 151 KB and is read once, on the index thread. The matching itself
 * lives in [QuotationIndex], which has no Android in it and is therefore checked
 * on a desktop JVM rather than only on a phone.
 */
class CrossReferences(private val context: Context) {

    private val index = QuotationIndex()

    val isLoaded: Boolean get() = index.isLoaded

    /** How many quotations the asset holds. */
    val count: Int get() = index.count

    /** Reads the asset. Call off the UI thread. */
    fun load() {
        context.assets.open(ASSET).bufferedReader().useLines { index.parse(it) }
    }

    fun forVerse(bookId: String, chapter: Int, verse: Int): List<Quotation> =
        index.forVerse(bookId, chapter, verse)

    fun rangeIn(text: String, phrase: String): IntRange? = index.rangeIn(text, phrase)

    private companion object {
        const val ASSET = "xrefs"
    }
}
