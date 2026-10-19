package com.realsneed.biblereader.data

import android.content.res.AssetManager
import java.io.IOException

/**
 * One word of a verse, and the Hebrew or Greek word behind it.
 *
 * [start] and [end] index the verse's words - not characters - because some
 * entries cover a phrase: "unto you" is a single entry, G5213.
 */
data class VerseWord(
    val text: String,
    val start: Int,
    val end: Int,
    val strongs: String
) {
    fun covers(word: Int): Boolean = word >= start && word < end

    /** The word as the lexicon would print it, without the verse's punctuation. */
    val label: String get() = trimWord(text)
}

/**
 * Drops the punctuation a verse carries but a dictionary does not, so "earth."
 * is looked up as "earth".
 */
internal fun trimWord(raw: String): String =
    raw.trim(' ', ',', '.', ';', ':', '!', '?', '(', ')', '"', '\u201C', '\u201D', '\u2019')

/** A lexicon entry: [id] is a Strong's number such as `H430` or `G2316`. */
data class StrongsEntry(
    val id: String,
    val original: String,
    val transliteration: String,
    val definition: String,
    val kjv: String?
) {
    val isHebrew: Boolean get() = id.startsWith("H")
}

/**
 * Hebrew and Greek word data, straight out of the assets that already ship with
 * the app - nothing to download, no licence to worry about.
 *
 * `words_<book>` lists every verse's words that carry a Strong's number. It is
 * **not** a copy of the KJV text, and the differences are why this walks the two
 * together rather than zipping them:
 *
 *  - words with no original are absent (`In`, `the`, and the italicised words the
 *    translators supplied) - only 44% of the Bible's words have a number;
 *  - one entry can cover a phrase, up to seven words long;
 *  - it carries the Hebrew titles of the Psalms (`\d` lines) that the reader does
 *    not display, so a few entries per Psalm belong to no word at all.
 *
 * `lexicon` is 1.4 MB and is only read when a definition is actually asked for.
 */
class StrongsRepository(
    private val assets: AssetManager,
    private val bible: BibleRepository
) {

    /** A words-file entry before it has been matched to a word of the verse. */
    private class RawWord(val word: String, val strongs: String)

    private val lock = Any()

    /** Words files read so far, oldest first (kept small). */
    private val books = LinkedHashMap<Int, Map<Int, List<RawWord>>>()

    /** Aligned chapters, keyed by `assetIndex * 1000 + chapter`. */
    private val aligned = LinkedHashMap<Int, Map<Int, List<VerseWord>>>()

    private var lexicon: HashMap<String, String>? = null

    // ------------------------------------------------------------------- words

    /** Verse number to the words of that verse that carry a Strong's number. */
    fun wordsFor(book: BookInfo, chapterNumber: Int): Map<Int, List<VerseWord>> {
        val key = book.assetIndex * 1000 + chapterNumber
        synchronized(lock) { aligned[key]?.let { return it } }

        val chapter = bible.chapter(book, chapterNumber)
        val result: Map<Int, List<VerseWord>> = if (chapter == null) {
            emptyMap()
        } else {
            val raw = rawFor(book)
            val verses = HashMap<Int, List<VerseWord>>(chapter.verses.size)
            for (verse in chapter.verses) {
                val entries = raw[chapterNumber * 1000 + verse.number] ?: continue
                val words = align(verse.text, entries)
                if (words.isNotEmpty()) verses[verse.number] = words
            }
            verses
        }

        synchronized(lock) {
            aligned[key] = result
            while (aligned.size > CHAPTER_LIMIT) {
                val oldest = aligned.keys.firstOrNull() ?: break
                aligned.remove(oldest)
            }
        }
        return result
    }

    private fun rawFor(book: BookInfo): Map<Int, List<RawWord>> {
        synchronized(lock) { books[book.assetIndex]?.let { return it } }
        val loaded = readWords(book.assetIndex)
        synchronized(lock) {
            books[book.assetIndex] = loaded
            while (books.size > BOOK_LIMIT) {
                val oldest = books.keys.firstOrNull() ?: break
                books.remove(oldest)
            }
        }
        return loaded
    }

    private fun readWords(assetIndex: Int): Map<Int, List<RawWord>> {
        val verses = HashMap<Int, List<RawWord>>(1600)
        try {
            assets.open("$ASSET_DIR/words_$assetIndex").bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val parsed = parseWordsLine(line) ?: continue
                    verses[parsed.first] = parsed.second
                }
            }
        } catch (e: IOException) {
            // No word data for this book: the reader carries on without it.
        }
        return verses
    }

    /**
     * One line is `3:16` then, tab separated, `world<US>G2889<US>` per word - the
     * unit separator splitting each word from its number.
     */
    private fun parseWordsLine(line: String): Pair<Int, List<RawWord>>? {
        val firstTab = line.indexOf('\t')
        if (firstTab < 3) return null
        val colon = line.indexOf(':')
        if (colon < 1 || colon > firstTab) return null
        val chapter = line.substring(0, colon).toIntOrNull() ?: return null
        val verse = line.substring(colon + 1, firstTab).toIntOrNull() ?: return null

        val words = ArrayList<RawWord>(16)
        var index = firstTab + 1
        while (index < line.length) {
            val chunkEnd = line.indexOf('\t', index).let { if (it < 0) line.length else it }
            val separator = line.indexOf(SEPARATOR, index)
            if (separator in index until chunkEnd) {
                val word = line.substring(index, separator)
                if (word.isNotEmpty()) {
                    val numberEnd = line.indexOf(SEPARATOR, separator + 1)
                        .let { if (it < 0 || it > chunkEnd) chunkEnd else it }
                    words.add(RawWord(word, line.substring(separator + 1, numberEnd)))
                }
            }
            if (chunkEnd >= line.length) break
            index = chunkEnd + 1
        }
        return if (words.isEmpty()) null else (chapter * 1000 + verse) to words
    }

    /**
     * Pairs the verse with its entries, never guessing: a word is given a number
     * only when it matches an entry exactly, word for word, so a word either gets
     * the right number or none at all - never the wrong one.
     *
     *  - a word with no original is simply skipped;
     *  - an entry of up to [SPAN_LIMIT] words matches that whole run of words;
     *  - an entry that is not in this verse is dropped rather than shifting
     *    everything after it, and the search for one is bounded so that a stray
     *    entry cannot cost the rest of the verse.
     *
     * Across the whole Bible this places 99.8% of entries.
     */
    private fun align(text: String, entries: List<RawWord>): List<VerseWord> {
        val tokens = text.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty() || entries.isEmpty()) return emptyList()

        val normal = ArrayList<String>(tokens.size)
        for (token in tokens) normal.add(normalise(token))

        // Every run of up to SPAN_LIMIT words that occurs in this verse, so an
        // entry that is not here at all can be recognised as such.
        val present = HashSet<String>(normal.size * 2)
        for (i in normal.indices) {
            var key = ""
            for (span in 1..SPAN_LIMIT) {
                val next = i + span - 1
                if (next >= normal.size) break
                if (normal[next].isEmpty()) continue
                key = if (key.isEmpty()) normal[next] else "$key ${normal[next]}"
                present.add(key)
            }
        }

        val words = ArrayList<VerseWord>(entries.size)
        var word = 0
        var entry = 0
        while (word < normal.size && entry < entries.size) {
            val target = normaliseEntry(entries[entry].word)
            if (target.isEmpty() || target !in present) {   // not in this verse at all
                entry++
                continue
            }
            val span = matchAt(normal, word, target)
            if (span > 0) {
                words.add(
                    VerseWord(
                        tokens.subList(word, word + span).joinToString(" "),
                        word,
                        word + span,
                        entries[entry].strongs
                    )
                )
                word += span
                entry++
                continue
            }
            // Either this word has no original and the entry lies further on, or
            // the entry has no counterpart here. Only the first is worth walking
            // for, and only so far.
            if (matchesWithin(normal, word, target)) word++ else entry++
        }
        return words
    }

    /** How many words the phrase at [from] has, if it equals [target]; else 0. */
    private fun matchAt(normal: List<String>, from: Int, target: String): Int {
        var key = ""
        for (span in 1..SPAN_LIMIT) {
            val next = from + span - 1
            if (next >= normal.size) break
            if (normal[next].isEmpty()) continue
            key = if (key.isEmpty()) normal[next] else "$key ${normal[next]}"
            if (key == target) return span
        }
        return 0
    }

    private fun matchesWithin(normal: List<String>, from: Int, target: String): Boolean {
        val last = minOf(from + MATCH_WINDOW, normal.size - 1)
        for (i in from + 1..last) {
            if (matchAt(normal, i, target) > 0) return true
        }
        return false
    }

    private fun normalise(raw: String): String {
        val out = StringBuilder(raw.length)
        for (c in raw) {
            when {
                c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' -> out.append(c.lowercaseChar())
                c == '\'' -> out.append(c)   // "man's" keeps its own apostrophe
            }
        }
        return out.toString()
    }

    /**
     * Normalised word by word, so a phrasal entry keeps its single spaces:
     * `untoyou` would never be found, `unto you` is.
     */
    private fun normaliseEntry(raw: String): String {
        var result = ""
        val word = StringBuilder(raw.length)
        for (c in raw) {
            if (c == ' ' || c == '\t') {
                result = appendPart(result, word.toString())
                word.setLength(0)
            } else {
                word.append(c)
            }
        }
        return appendPart(result, word.toString())
    }

    private fun appendPart(result: String, rawPart: String): String {
        val part = normalise(rawPart)
        if (part.isEmpty()) return result
        return if (result.isEmpty()) part else "$result $part"
    }

    // ----------------------------------------------------------------- lexicon

    /** Reads the lexicon ahead of time. Call this from a background thread. */
    fun warmUp() {
        lexicon()
    }

    fun entry(id: String): StrongsEntry? {
        val tail = lexicon()[id] ?: return null
        val firstTab = tail.indexOf('\t')
        if (firstTab < 0) return StrongsEntry(id, tail, "", "", null)

        val secondTab = tail.indexOf('\t', firstTab + 1)
        val original = tail.substring(0, firstTab)
        val transliteration = if (secondTab < 0) tail.substring(firstTab + 1)
        else tail.substring(firstTab + 1, secondTab)
        val gloss = if (secondTab < 0) "" else tail.substring(secondTab + 1)

        // Most entries read "definition — KJV: the words it is translated as".
        val mark = gloss.indexOf(KJV_MARK)
        return when {
            mark >= 0 -> StrongsEntry(
                id = id,
                original = original,
                transliteration = transliteration,
                definition = gloss.substring(0, mark).trim().trimEnd(':', ';', ',', '-', ' '),
                kjv = gloss.substring(mark + KJV_MARK.length).trim()
            )
            gloss.startsWith(KJV_PREFIX) -> StrongsEntry(
                id, original, transliteration, "", gloss.substring(KJV_PREFIX.length).trim()
            )
            else -> StrongsEntry(id, original, transliteration, gloss.trim(), null)
        }
    }

    private fun lexicon(): HashMap<String, String> {
        synchronized(lock) { lexicon?.let { return it } }
        val built = readLexicon()
        synchronized(lock) { lexicon = built }
        return built
    }

    /** Strong's number to the raw remainder of its line, split only when wanted. */
    private fun readLexicon(): HashMap<String, String> {
        val map = HashMap<String, String>(LEXICON_ENTRIES)
        try {
            assets.open("$ASSET_DIR/lexicon").bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val tab = line.indexOf('\t')
                    if (tab <= 0) continue
                    map[line.substring(0, tab)] = line.substring(tab + 1)
                }
            }
        } catch (e: IOException) {
            // No lexicon: word study simply has nothing to show.
        }
        return map
    }

    companion object {
        const val ASSET_DIR = "usfm/kjv"

        /** Longest entry is seven words; five covers all but a handful. */
        private const val SPAN_LIMIT = 5

        /** How far ahead to look for an entry before giving up on it. */
        private const val MATCH_WINDOW = 16

        private const val BOOK_LIMIT = 2
        private const val CHAPTER_LIMIT = 8
        private const val LEXICON_ENTRIES = 14057
        private const val SEPARATOR = '\u001F'
        private const val KJV_MARK = "\u2014 KJV:"
        private const val KJV_PREFIX = "KJV:"

        @Volatile
        private var instance: StrongsRepository? = null

        fun get(assets: AssetManager, bible: BibleRepository): StrongsRepository =
            instance ?: synchronized(this) {
                instance ?: StrongsRepository(assets, bible).also { instance = it }
            }

        /**
         * Which word of [text] sits at [offset] characters in, or -1. Splits the
         * text exactly as [align] does, so the two always agree - a touch on the
         * space between two words counts as the word before it.
         */
        fun wordIndexAt(text: String, offset: Int): Int {
            var index = 0
            var start = 0
            while (start <= text.length) {
                val space = text.indexOf(' ', start)
                val end = if (space < 0) text.length else space
                if (end > start) {
                    if (offset <= end) return index
                    index++
                }
                if (space < 0) break
                start = space + 1
            }
            return -1
        }

        /** The word at [index] of [text], as a dictionary would print it. */
        fun wordAt(text: String, index: Int): String {
            var i = 0
            var start = 0
            while (start <= text.length) {
                val space = text.indexOf(' ', start)
                val end = if (space < 0) text.length else space
                if (end > start) {
                    if (i == index) return trimWord(text.substring(start, end))
                    i++
                }
                if (space < 0) break
                start = space + 1
            }
            return ""
        }

        /**
         * The character range covering the words [start, end) of [text], or null.
         * Used to highlight a verse's word wherever a concordance lists it.
         */
        fun wordRange(text: String, start: Int, end: Int): IntRange? {
            var index = 0
            var from = -1
            var position = 0
            while (position <= text.length) {
                val space = text.indexOf(' ', position)
                val stop = if (space < 0) text.length else space
                if (stop > position) {
                    if (index == start) from = position
                    if (index == end - 1) {
                        return if (from < 0 || stop <= from) null else from until stop
                    }
                    index++
                }
                if (space < 0) break
                position = space + 1
            }
            return null
        }
    }
}


