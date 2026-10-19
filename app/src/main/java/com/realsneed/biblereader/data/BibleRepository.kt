package com.realsneed.biblereader.data

import android.content.res.AssetManager
import java.io.IOException

/**
 * Loads and caches Bible text from `assets/usfm/kjv`.
 *
 * Parsing a book is cheap, but we keep a small LRU cache so flipping chapters
 * inside one book never re-parses it. All methods are thread-safe; call them
 * from a background thread.
 */
class BibleRepository(private val assets: AssetManager) {

    private val lock = Any()
    private val bookCache = LinkedHashMap<Int, List<Chapter>>()
    private val chapterCounts = HashMap<Int, Int>()
    private var anyFailure = false

    /** True if any asset failed to load (used to surface a one-off message). */
    val hadFailure: Boolean get() = anyFailure

    fun chapters(book: BookInfo): List<Chapter> = synchronized(lock) {
        bookCache[book.assetIndex]?.let { return it }
        val parsed = try {
            UsfmParser.parse(readAsset("book_${book.assetIndex}"))
        } catch (e: IOException) {
            anyFailure = true
            emptyList()
        }
        bookCache[book.assetIndex] = parsed
        while (bookCache.size > CACHE_LIMIT) {
            val oldest = bookCache.keys.firstOrNull() ?: break
            bookCache.remove(oldest)
        }
        parsed
    }

    fun chapter(book: BookInfo, chapterNumber: Int): Chapter? =
        chapters(book).firstOrNull { it.number == chapterNumber }

    fun chapterCount(book: BookInfo): Int = synchronized(lock) {
        chapterCounts[book.assetIndex]?.let { return it }
        val count = chapters(book).size
        chapterCounts[book.assetIndex] = count
        count
    }

    private fun readAsset(fileName: String): String =
        assets.open("$ASSET_DIR/$fileName").use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        }

    companion object {
        const val ASSET_DIR = "usfm/kjv"
        private const val CACHE_LIMIT = 3

        @Volatile
        private var instance: BibleRepository? = null

        /** Shared instance, so every screen reuses the same parsed-book cache. */
        fun get(assets: AssetManager): BibleRepository =
            instance ?: synchronized(this) {
                instance ?: BibleRepository(assets).also { instance = it }
            }
    }
}
