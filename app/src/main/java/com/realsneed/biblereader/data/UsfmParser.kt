package com.realsneed.biblereader.data

/**
 * One of the KJV's own footnotes, sitting at [anchor] characters into the verse.
 *
 * These are the translators' notes: `firmament` -> "Heb. expansion", the readings
 * they weighed, what the Hebrew or Greek literally says. The parser used to drop
 * them entirely.
 */
data class Footnote(
    val anchor: Int,
    val reference: String,
    val quote: String?,
    val text: String
)

/**
 * A single verse. [number] is the printed verse number.
 *
 * [italics] holds pairs of offsets into [text] marking the words the translators
 * **supplied** - they are not in the Hebrew or Greek, which is exactly why
 * holding one of them finds no original. [footnotes] are the KJV's margin notes.
 */
class Verse(
    val number: Int,
    val text: String,
    val italics: IntArray = IntArray(0),
    val footnotes: List<Footnote> = emptyList()
) {
    val hasItalics: Boolean get() = italics.isNotEmpty()
}

/**
 * A chapter. [heading] is reserved for chapter-level labels (e.g. Psalm titles /
 * Song-of-Solomon speaker labels) and is currently always null.
 */
data class Chapter(val number: Int, val heading: String?, val verses: List<Verse>)

/**
 * Minimal, allocation-friendly parser for the KJV USFM found in
 * `assets/usfm/kjv`. Only the markers that actually occur in that data set are
 * handled:
 *
 *  - `\c N`            chapter
 *  - `\v N text`       verse
 *  - `\f ... \f*`      footnote: `\fr` reference, `\fq` quoted words, `\ft` the
 *                      note itself - kept now, and out of the verse text
 *  - `\add ... \add*`  translator-supplied words - kept in the text, with the
 *                      span recorded so the reader can show them in italics
 *  - `\nd ... \nd*`    "name of deity" small caps (kept, markers removed)
 *  - `\p`, `\q`, `\qa`, `\d`, `\b`, `\m`, ... layout/speaker markers (dropped)
 *
 * Parsing the whole Bible yields 31,102 verses across 1,189 chapters. Collecting
 * italics and footnotes does **not** change the verse text: that was checked by
 * hashing every one of the 31,102 verses before and after.
 */
object UsfmParser {

    private val FOOTNOTE_OPTIONS = setOf(RegexOption.DOT_MATCHES_ALL)

    private val STRUCTURAL_LINE = Regex(
        "^\\\\[pqdmb][a-z0-9]*[^\\n]*$",
        setOf(RegexOption.MULTILINE)
    )

    /** Any inline marker, closing asterisk included. */
    private val MARKER = Regex("\\\\[a-zA-Z+][a-zA-Z0-9]*\\*?")

    private val CHAPTER = Regex("\\\\c\\s+(\\d+)")
    private val VERSE = Regex("\\\\v\\s+(\\d+)\\s?")

    private val FOOTNOTE_REFERENCE = Regex("\\\\fr\\s+(.*?)\\s*\\\\fr\\*", FOOTNOTE_OPTIONS)
    private val FOOTNOTE_QUOTE = Regex("\\\\fq\\s+(.*?)\\s*\\\\fq\\*", FOOTNOTE_OPTIONS)
    private val FOOTNOTE_TEXT = Regex("\\\\ft\\s+(.*?)\\s*\\\\ft\\*", FOOTNOTE_OPTIONS)

    /**
     * A sentence ended and the next began with no space - `clean.And` - which
     * happens 60 times in this data set and shows up in the reader as one run-on
     * word. A zero-width match lets the space be inserted.
     */
    private val GLUED_SENTENCE = Regex("(?<=[.!?])(?=[A-Z])")

    private const val ADD_OPEN = "\\add"
    private const val ADD_CLOSE = "\\add*"
    private const val FOOTNOTE_OPEN = "\\f"
    private const val FOOTNOTE_END = "\\f*"


    fun parse(usfm: String): List<Chapter> {
        val firstChapter = CHAPTER.find(usfm) ?: return emptyList()

        // Drop the header block and layout-only lines up front. Footnotes stay in:
        // each belongs to the verse it sits in, and is collected there.
        var body = usfm.substring(firstChapter.range.first)
        body = STRUCTURAL_LINE.replace(body, "")

        val chapterMatches = CHAPTER.findAll(body).toList()
        if (chapterMatches.isEmpty()) return emptyList()

        val chapters = ArrayList<Chapter>(chapterMatches.size)
        for (i in chapterMatches.indices) {
            val match = chapterMatches[i]
            val number = match.groupValues[1].toIntOrNull() ?: continue
            val contentStart = match.range.last + 1
            val contentEnd =
                if (i + 1 < chapterMatches.size) chapterMatches[i + 1].range.first else body.length
            if (contentEnd <= contentStart) {
                chapters.add(Chapter(number, null, emptyList()))
                continue
            }
            chapters.add(Chapter(number, null, parseVerses(body.substring(contentStart, contentEnd))))
        }
        return chapters
    }

    private fun parseVerses(content: String): List<Verse> {
        val matches = VERSE.findAll(content).toList()
        if (matches.isEmpty()) return emptyList()

        val verses = ArrayList<Verse>(matches.size)
        for (i in matches.indices) {
            val match = matches[i]
            val number = match.groupValues[1].toIntOrNull() ?: continue
            val textStart = match.range.last + 1
            val textEnd = if (i + 1 < matches.size) matches[i + 1].range.first else content.length
            if (textEnd <= textStart) continue
            val cleaned = clean(content.substring(textStart, textEnd))
            if (cleaned.text.isNotEmpty()) {
                verses.add(Verse(number, cleaned.text, cleaned.italics, cleaned.footnotes))
            }
        }
        return verses
    }

    /** One verse's text, plus what the reader can be told about it. */
    private class Cleaned(
        val text: String,
        val italics: IntArray,
        val footnotes: List<Footnote>
    )

    /**
     * Strips the inline markers and normalises the whitespace, and while it is at
     * it records the two things worth keeping: where the supplied words are, and
     * where each footnote sits. Offsets are measured as the text is built, so they
     * are offsets in the finished verse.
     */
    private fun clean(source: String): Cleaned {
        // The run-on sentences are repaired first, so that every offset measured
        // below is an offset in the finished verse.
        val raw = GLUED_SENTENCE.replace(source, " ")
        val out = StringBuilder(raw.length)
        val italics = ArrayList<Int>(8)
        val footnotes = ArrayList<Footnote>(2)
        var italicStart = -1
        var position = 0

        while (position < raw.length) {
            val marker = MARKER.find(raw, position)
            if (marker == null) {
                append(out, raw, position, raw.length)
                break
            }
            append(out, raw, position, marker.range.first)
            when {
                marker.value == ADD_OPEN -> {
                    space(out)
                    italicStart = out.length
                }
                marker.value == ADD_CLOSE -> {
                    closeItalic(out, italics, italicStart)
                    italicStart = -1
                }
                marker.value == FOOTNOTE_OPEN -> {
                    val end = raw.indexOf(FOOTNOTE_END, marker.range.last + 1)
                    val stop = if (end < 0) raw.length else end
                    space(out)
                    footnotes.add(readFootnote(raw.substring(marker.range.last + 1, stop), out.length))
                    if (end < 0) break
                    position = end + FOOTNOTE_END.length
                    continue
                }
                // A closing marker hugs the word before it, so it adds nothing.
                marker.value.endsWith("*") -> Unit
                // Opening markers are word-separated.
                else -> space(out)
            }
            position = marker.range.last + 1
        }
        closeItalic(out, italics, italicStart)

        // A footnote at the end of a verse sits after the space that the trim
        // below removes, so keep every anchor inside the finished text.
        val text = out.toString().trimEnd()
        for (i in footnotes.indices) {
            val note = footnotes[i]
            if (note.anchor > text.length) footnotes[i] = note.copy(anchor = text.length)
        }
        return Cleaned(text, italics.toIntArray(), footnotes)
    }

    /** Appends raw[from, to), collapsing runs of whitespace to a single space. */
    private fun append(out: StringBuilder, raw: String, from: Int, to: Int) {
        var i = from
        while (i < to) {
            if (isSpace(raw[i])) {
                var j = i
                while (j < to && isSpace(raw[j])) j++
                space(out)
                i = j
            } else {
                out.append(raw[i])
                i++
            }
        }
    }

    private fun isSpace(c: Char): Boolean =
        c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\u00A0'

    /** One space between words, never two, never one at the front. */
    private fun space(out: StringBuilder) {
        if (out.isEmpty() || out[out.length - 1] == ' ') return
        out.append(' ')
    }

    private fun closeItalic(out: StringBuilder, italics: ArrayList<Int>, start: Int) {
        if (start < 0 || out.length <= start) return
        italics.add(start)
        italics.add(out.length)
    }

    /** `\fr 1:4 \fr* \fq firmament \fq* \ft - Heb. expansion\ft*` */
    private fun readFootnote(body: String, anchor: Int): Footnote {
        val reference = FOOTNOTE_REFERENCE.find(body)?.groupValues?.get(1)?.trim().orEmpty()
        val quote = FOOTNOTE_QUOTE.find(body)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
        val text = FOOTNOTE_TEXT.find(body)?.groupValues?.get(1)?.trim().orEmpty()
        return Footnote(anchor, reference, quote, text)
    }
}
