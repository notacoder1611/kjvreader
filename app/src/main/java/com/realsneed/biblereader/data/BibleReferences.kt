package com.realsneed.biblereader.data

/** A resolved scripture reference. */
data class Reference(val book: BookInfo, val chapter: Int, val verse: Int)

/**
 * Parses shorthand scripture references, e.g. "j1:1", "john 1:1", "1jn2:3",
 * "ps23", "song of solomon 2:1" or "first corinthians 13:4".
 *
 * Aliases are lower-cased with spaces and periods removed, so "1 jn 2:3" and
 * "1Jn2:3" both resolve. Roman numerals and the words first/second/third are
 * converted to digits first. A unique prefix of a full book name also works
 * ("deuter 6:5" -> Deuteronomy 6:5).
 */
object BibleReferences {

    private val ORDINAL_III = Regex("\\b(iii|third)\\b")
    private val ORDINAL_II = Regex("\\b(ii|second)\\b")
    private val ORDINAL_I = Regex("\\b(i|first)\\b")
    private val SPACES = Regex("\\s+")
    private val VERSE_RANGE = Regex("\\s*[-\\u2013]\\s*\\d+\\s*$")
    private val BOOK_CHAPTER_VERSE = Regex("^(.*?)\\s*(\\d+)\\s*(?:[:.]\\s*(\\d+))?$")
    private val NOT_ALIAS_CHAR = Regex("[\\s.]+")

    private val ALIAS_TO_ASSET: Map<String, Int> = mapOf(
        "genesis" to 1, "gen" to 1, "ge" to 1, "gn" to 1,
        "exodus" to 2, "exo" to 2, "ex" to 2, "exod" to 2,
        "leviticus" to 3, "lev" to 3, "le" to 3, "lv" to 3,
        "numbers" to 4, "num" to 4, "nu" to 4, "nm" to 4, "numb" to 4,
        "deuteronomy" to 5, "deu" to 5, "deut" to 5, "dt" to 5,
        "joshua" to 6, "jos" to 6, "josh" to 6,
        "judges" to 7, "jdg" to 7, "judg" to 7, "jg" to 7,
        "ruth" to 8, "rut" to 8, "ru" to 8, "rth" to 8,
        "1samuel" to 9, "1sa" to 9, "1sam" to 9, "1sm" to 9,
        "2samuel" to 10, "2sa" to 10, "2sam" to 10, "2sm" to 10,
        "1kings" to 11, "1ki" to 11, "1kg" to 11, "1kgs" to 11, "1kin" to 11,
        "2kings" to 12, "2ki" to 12, "2kg" to 12, "2kgs" to 12, "2kin" to 12,
        "1chronicles" to 13, "1ch" to 13, "1chr" to 13, "1chron" to 13,
        "2chronicles" to 14, "2ch" to 14, "2chr" to 14, "2chron" to 14,
        "ezra" to 15, "ezr" to 15, "ez" to 15,
        "nehemiah" to 16, "neh" to 16, "ne" to 16,
        "esther" to 17, "est" to 17, "es" to 17, "esth" to 17,
        "job" to 18, "jb" to 18,
        "psalms" to 19, "psalm" to 19, "psa" to 19, "ps" to 19, "psm" to 19,
        "proverbs" to 20, "pro" to 20, "prov" to 20, "pr" to 20,
        "ecclesiastes" to 21, "ecc" to 21, "eccl" to 21, "ec" to 21, "qoh" to 21,
        "songofsolomon" to 22, "songofsongs" to 22, "song" to 22, "sos" to 22, "sng" to 22, "ss" to 22, "so" to 22,
        "isaiah" to 23, "isa" to 23, "is" to 23, "isai" to 23,
        "jeremiah" to 24, "jer" to 24, "je" to 24, "jere" to 24,
        "lamentations" to 25, "lam" to 25, "la" to 25, "lament" to 25,
        "ezekiel" to 26, "ezk" to 26, "eze" to 26, "ezek" to 26,
        "daniel" to 27, "dan" to 27, "da" to 27, "dn" to 27,
        "hosea" to 28, "hos" to 28, "ho" to 28,
        "joel" to 29, "jol" to 29, "joe" to 29, "jl" to 29,
        "amos" to 30, "amo" to 30, "am" to 30,
        "obadiah" to 31, "oba" to 31, "ob" to 31, "obad" to 31,
        "jonah" to 32, "jon" to 32, "jnh" to 32,
        "micah" to 33, "mic" to 33, "mi" to 33, "mc" to 33,
        "nahum" to 34, "nam" to 34, "nah" to 34, "na" to 34, "nh" to 34,
        "habakkuk" to 35, "hab" to 35, "hb" to 35, "habb" to 35,
        "zephaniah" to 36, "zep" to 36, "zeph" to 36, "zp" to 36,
        "haggai" to 37, "hag" to 37, "hg" to 37,
        "zechariah" to 38, "zec" to 38, "zech" to 38, "zc" to 38,
        "malachi" to 39, "mal" to 39, "ml" to 39,
        "matthew" to 41, "mat" to 41, "matt" to 41, "mt" to 41,
        "mark" to 42, "mrk" to 42, "mk" to 42, "mar" to 42, "mr" to 42,
        "luke" to 43, "luk" to 43, "lk" to 43, "lu" to 43,
        "john" to 44, "jhn" to 44, "joh" to 44, "jn" to 44, "j" to 44,
        "acts" to 45, "act" to 45, "ac" to 45,
        "romans" to 46, "rom" to 46, "ro" to 46, "rm" to 46,
        "1corinthians" to 47, "1co" to 47, "1cor" to 47, "1corinth" to 47,
        "2corinthians" to 48, "2co" to 48, "2cor" to 48, "2corinth" to 48,
        "galatians" to 49, "gal" to 49, "ga" to 49,
        "ephesians" to 50, "eph" to 50, "ep" to 50,
        "philippians" to 51, "php" to 51, "phil" to 51, "phili" to 51,
        "colossians" to 52, "col" to 52,
        "1thessalonians" to 53, "1th" to 53, "1thess" to 53, "1thes" to 53,
        "2thessalonians" to 54, "2th" to 54, "2thess" to 54, "2thes" to 54,
        "1timothy" to 55, "1ti" to 55, "1tim" to 55, "1tm" to 55,
        "2timothy" to 56, "2ti" to 56, "2tim" to 56, "2tm" to 56,
        "titus" to 57, "tit" to 57, "ti" to 57,
        "philemon" to 58, "phm" to 58, "philem" to 58, "phlm" to 58,
        "hebrews" to 59, "heb" to 59, "he" to 59, "hbr" to 59,
        "james" to 60, "jas" to 60, "jm" to 60, "jam" to 60,
        "1peter" to 61, "1pe" to 61, "1pet" to 61, "1pt" to 61,
        "2peter" to 62, "2pe" to 62, "2pet" to 62, "2pt" to 62,
        "1john" to 63, "1jn" to 63, "1joh" to 63, "1j" to 63,
        "2john" to 64, "2jn" to 64, "2joh" to 64, "2j" to 64,
        "3john" to 65, "3jn" to 65, "3joh" to 65, "3j" to 65,
        "jude" to 66, "jud" to 66, "jd" to 66,
        "revelation" to 67, "revelations" to 67, "rev" to 67, "re" to 67, "rv" to 67,
    )

    /** Parses [input]; returns null when it is not a recognisable reference. */
    fun parse(input: String): Reference? {
        var text = input.trim().lowercase()
        if (text.isEmpty()) return null

        text = ORDINAL_III.replace(text, "3")
        text = ORDINAL_II.replace(text, "2")
        text = ORDINAL_I.replace(text, "1")
        text = SPACES.replace(text, " ").trim()
        text = VERSE_RANGE.replace(text, "")

        val match = BOOK_CHAPTER_VERSE.find(text)
        val bookPart: String
        val chapter: Int
        val verse: Int
        if (match != null) {
            bookPart = match.groupValues[1]
            chapter = match.groupValues[2].toIntOrNull() ?: 1
            verse = match.groupValues[3].toIntOrNull() ?: 1
        } else {
            bookPart = text
            chapter = 1
            verse = 1
        }

        val assetIndex = resolve(NOT_ALIAS_CHAR.replace(bookPart, "")) ?: return null
        val book = BibleMeta.byAssetIndex(assetIndex) ?: return null
        return Reference(book, chapter.coerceAtLeast(1), verse.coerceAtLeast(1))
    }

    private fun resolve(key: String): Int? {
        if (key.isEmpty()) return null
        ALIAS_TO_ASSET[key]?.let { return it }
        var found: Int? = null
        for (book in BibleMeta.BOOKS) {
            if (book.name.lowercase().replace(" ", "").startsWith(key)) {
                if (found != null) return null
                found = book.assetIndex
            }
        }
        return found
    }
}
