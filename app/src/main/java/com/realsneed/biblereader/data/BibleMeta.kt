package com.realsneed.biblereader.data

/**
 * One book of the Bible, identified by its USFM id (e.g. "GEN").
 *
 * [assetIndex] maps to the data file `assets/usfm/kjv/book_<assetIndex>`.
 * The indices are not fully contiguous (there is no book_40), so the asset
 * index must always come from this table rather than be computed.
 */
data class BookInfo(
    val assetIndex: Int,
    val id: String,
    val name: String
) {
    val isNewTestament: Boolean get() = assetIndex >= 41
}

/** The canonical 66-book KJV table plus the chapter geometry of the whole Bible. */
object BibleMeta {

    /** Chapters per book in canonical order (Genesis .. Revelation). Sums to 1,189. */
    private val CHAPTER_COUNTS = intArrayOf(
        50, 40, 27, 36, 34, 24, 21, 4, 31, 24, 22, 25, 29, 36, 10, 13, 10, 42,
        150, 31, 12, 8, 66, 52, 5, 48, 12, 14, 3, 9, 1, 4, 7, 3, 3, 3, 2, 14,
        4, 28, 16, 24, 21, 28, 16, 16, 13, 6, 6, 4, 4, 5, 3, 6, 4, 3, 1, 13, 5,
        5, 3, 5, 1, 1, 1, 22
    )

    val BOOKS: List<BookInfo> = listOf(
        BookInfo(1, "GEN", "Genesis"),
        BookInfo(2, "EXO", "Exodus"),
        BookInfo(3, "LEV", "Leviticus"),
        BookInfo(4, "NUM", "Numbers"),
        BookInfo(5, "DEU", "Deuteronomy"),
        BookInfo(6, "JOS", "Joshua"),
        BookInfo(7, "JDG", "Judges"),
        BookInfo(8, "RUT", "Ruth"),
        BookInfo(9, "1SA", "1 Samuel"),
        BookInfo(10, "2SA", "2 Samuel"),
        BookInfo(11, "1KI", "1 Kings"),
        BookInfo(12, "2KI", "2 Kings"),
        BookInfo(13, "1CH", "1 Chronicles"),
        BookInfo(14, "2CH", "2 Chronicles"),
        BookInfo(15, "EZR", "Ezra"),
        BookInfo(16, "NEH", "Nehemiah"),
        BookInfo(17, "EST", "Esther"),
        BookInfo(18, "JOB", "Job"),
        BookInfo(19, "PSA", "Psalms"),
        BookInfo(20, "PRO", "Proverbs"),
        BookInfo(21, "ECC", "Ecclesiastes"),
        BookInfo(22, "SNG", "Song of Solomon"),
        BookInfo(23, "ISA", "Isaiah"),
        BookInfo(24, "JER", "Jeremiah"),
        BookInfo(25, "LAM", "Lamentations"),
        BookInfo(26, "EZK", "Ezekiel"),
        BookInfo(27, "DAN", "Daniel"),
        BookInfo(28, "HOS", "Hosea"),
        BookInfo(29, "JOL", "Joel"),
        BookInfo(30, "AMO", "Amos"),
        BookInfo(31, "OBA", "Obadiah"),
        BookInfo(32, "JON", "Jonah"),
        BookInfo(33, "MIC", "Micah"),
        BookInfo(34, "NAM", "Nahum"),
        BookInfo(35, "HAB", "Habakkuk"),
        BookInfo(36, "ZEP", "Zephaniah"),
        BookInfo(37, "HAG", "Haggai"),
        BookInfo(38, "ZEC", "Zechariah"),
        BookInfo(39, "MAL", "Malachi"),
        BookInfo(41, "MAT", "Matthew"),
        BookInfo(42, "MRK", "Mark"),
        BookInfo(43, "LUK", "Luke"),
        BookInfo(44, "JHN", "John"),
        BookInfo(45, "ACT", "Acts"),
        BookInfo(46, "ROM", "Romans"),
        BookInfo(47, "1CO", "1 Corinthians"),
        BookInfo(48, "2CO", "2 Corinthians"),
        BookInfo(49, "GAL", "Galatians"),
        BookInfo(50, "EPH", "Ephesians"),
        BookInfo(51, "PHP", "Philippians"),
        BookInfo(52, "COL", "Colossians"),
        BookInfo(53, "1TH", "1 Thessalonians"),
        BookInfo(54, "2TH", "2 Thessalonians"),
        BookInfo(55, "1TI", "1 Timothy"),
        BookInfo(56, "2TI", "2 Timothy"),
        BookInfo(57, "TIT", "Titus"),
        BookInfo(58, "PHM", "Philemon"),
        BookInfo(59, "HEB", "Hebrews"),
        BookInfo(60, "JAS", "James"),
        BookInfo(61, "1PE", "1 Peter"),
        BookInfo(62, "2PE", "2 Peter"),
        BookInfo(63, "1JN", "1 John"),
        BookInfo(64, "2JN", "2 John"),
        BookInfo(65, "3JN", "3 John"),
        BookInfo(66, "JUD", "Jude"),
        BookInfo(67, "REV", "Revelation")
    )

    /** Total number of chapters in the Bible (1,189). */
    val TOTAL_CHAPTERS: Int

    /** Position (0-based) of each book in canonical order, keyed by asset index. */
    private val positionByAsset = HashMap<Int, Int>(BOOKS.size)

    /** Absolute index of the first chapter of each book. */
    private val firstChapterOfBook = IntArray(BOOKS.size)

    init {
        var running = 0
        for (i in BOOKS.indices) {
            positionByAsset[BOOKS[i].assetIndex] = i
            firstChapterOfBook[i] = running
            running += CHAPTER_COUNTS[i]
        }
        TOTAL_CHAPTERS = running
    }

    fun positionOf(assetIndex: Int): Int = positionByAsset[assetIndex] ?: -1

    private val byIdMap: Map<String, BookInfo> = BOOKS.associateBy { it.id }

    /** Look up a book by its USFM id, e.g. "JHN". */
    fun byId(id: String): BookInfo? = byIdMap[id]

    /** Canonical position of a book given its USFM id. */
    fun positionOfId(id: String): Int = byIdMap[id]?.let { positionOf(it.assetIndex) } ?: -1

    fun byAssetIndex(assetIndex: Int): BookInfo? =
        positionByAsset[assetIndex]?.let { BOOKS[it] }

    fun chapterCount(book: BookInfo): Int = chapterCountAt(positionOf(book.assetIndex))

    fun chapterCountAt(position: Int): Int =
        if (position in CHAPTER_COUNTS.indices) CHAPTER_COUNTS[position] else 0

    /** Absolute 0-based page index of the given book + chapter. */
    fun absoluteIndex(book: BookInfo, chapter: Int): Int {
        val position = positionOf(book.assetIndex)
        if (position < 0) return 0
        val count = CHAPTER_COUNTS[position]
        return firstChapterOfBook[position] + (chapter - 1).coerceIn(0, count - 1)
    }

    /** Inverse of [absoluteIndex]. */
    fun chapterAt(absoluteIndex: Int): Pair<BookInfo, Int> {
        val clamped = absoluteIndex.coerceIn(0, TOTAL_CHAPTERS - 1)
        var position = 0
        while (position + 1 < BOOKS.size && firstChapterOfBook[position + 1] <= clamped) {
            position++
        }
        return BOOKS[position] to (clamped - firstChapterOfBook[position] + 1)
    }
}
