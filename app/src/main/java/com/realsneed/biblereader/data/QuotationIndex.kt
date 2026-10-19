package com.realsneed.biblereader.data

/** A phrase in a verse that its writer took from an earlier passage. */
class Quotation(
    val reference: String,
    val phrase: String,
    val sourceBook: String,
    val sourceChapter: Int,
    val sourceVerse: Int
)

/**
 * The quotation index: which words each verse borrows, and where they sit.
 *
 * Deliberately free of Android, so the whole of it - parsing the shipped asset and
 * matching a phrase inside a verse - can be run and checked on a desktop JVM
 * rather than only on a phone. [CrossReferences] is the thin Android wrapper that
 * feeds it the asset.
 */
class QuotationIndex {

    private var index: Map<String, List<Quotation>> = emptyMap()

    var isLoaded: Boolean = false
        private set

    /** How many quotations were read. */
    var count: Int = 0
        private set

    /** Reads lines of `BOOK chapter verse sourceBook sourceChapter sourceVerse reference phrase`. */
    fun parse(lines: Sequence<String>) {
        val loaded = HashMap<String, MutableList<Quotation>>(4096)
        var read = 0
        for (line in lines) {
            if (line.isEmpty() || line[0] == '#') continue
            val parts = line.split('\t')
            if (parts.size < 8) continue
            val chapter = parts[1].toIntOrNull() ?: continue
            val verse = parts[2].toIntOrNull() ?: continue
            val sourceChapter = parts[4].toIntOrNull() ?: continue
            val sourceVerse = parts[5].toIntOrNull() ?: continue
            loaded.getOrPut(key(parts[0], chapter, verse)) { ArrayList(2) }
                .add(
                    Quotation(
                        reference = parts[6],
                        phrase = parts[7],
                        sourceBook = parts[3],
                        sourceChapter = sourceChapter,
                        sourceVerse = sourceVerse
                    )
                )
            read++
        }
        index = loaded
        count = read
        isLoaded = read > 0
    }

    fun forVerse(bookId: String, chapter: Int, verse: Int): List<Quotation> =
        index[key(bookId, chapter, verse)] ?: emptyList()

    /** A bare word, and where it sits in the text it came from. */
    private class Word(val text: String, val start: Int, val end: Int)

    /**
     * Where [phrase] sits in [text], as a character range, or null if it is not
     * there word for word. Matching is by words, not punctuation, so a comma or a
     * different space cannot throw it off.
     */
    fun rangeIn(text: String, phrase: String): IntRange? {
        val wanted = words(phrase)
        if (wanted.isEmpty()) return null
        val found = words(text)
        if (found.size < wanted.size) return null
        for (start in 0..found.size - wanted.size) {
            var matches = true
            for (offset in wanted.indices) {
                if (!found[start + offset].text.equals(wanted[offset].text, ignoreCase = true)) {
                    matches = false
                    break
                }
            }
            if (matches) {
                return found[start].start until found[start + wanted.size - 1].end
            }
        }
        return null
    }

    private fun words(source: String): List<Word> {
        val words = ArrayList<Word>(24)
        var i = 0
        while (i < source.length) {
            if (source[i].isLetterOrDigit()) {
                val start = i
                while (i < source.length && (source[i].isLetterOrDigit() || source[i] == '\'')) i++
                words.add(Word(source.substring(start, i), start, i))
            } else {
                i++
            }
        }
        return words
    }

    companion object {
        fun key(bookId: String, chapter: Int, verse: Int): String = "$bookId|$chapter|$verse"
    }
}
