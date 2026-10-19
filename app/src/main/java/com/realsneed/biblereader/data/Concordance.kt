package com.realsneed.biblereader.data

import android.content.res.AssetManager
import java.io.IOException

/** One place a word occurs: a verse. */
data class Occurrence(val assetIndex: Int, val chapter: Int, val verse: Int)

/**
 * Where a Strong's number is used, anywhere in the Bible.
 *
 * The reader already has the data - `words_<book>` lists the numbers behind
 * every verse - so this is the other direction: number in, verses out. A single
 * pass over the 66 word files builds an index of 348,732 occurrences in about
 * 2 MB, which is why it is built once, on a background thread, and kept.
 *
 * Each occurrence packs into one `int` (7 bits of book, 8 of chapter, 8 of
 * verse), so 9,089 occurrences of "and" cost 36 KB rather than a list of objects.
 */
class Concordance(private val assets: AssetManager) {

    /** Grows without boxing every element the way an ArrayList would. */
    private class IntList(capacity: Int) {
        var values = IntArray(if (capacity < 4) 4 else capacity)
        var size = 0

        fun add(value: Int) {
            if (size == values.size) values = values.copyOf(size * 2)
            values[size++] = value
        }

        fun toArray(): IntArray = values.copyOf(size)
    }

    private val lock = Any()
    private var index: HashMap<String, IntArray>? = null

    /** Builds the index ahead of time. Call this from a background thread. */
    fun warmUp() {
        load()
    }

    /** True once the index is built, so a screen can show the count straight away. */
    val isReady: Boolean get() = synchronized(lock) { index != null }

    /** Every verse containing [strongs], in Bible order, or null if there is none. */
    fun references(strongs: String): IntArray? = load()[strongs]

    private fun load(): HashMap<String, IntArray> {
        synchronized(lock) { index?.let { return it } }
        val built = build()
        synchronized(lock) { index = built }
        return built
    }

    private fun build(): HashMap<String, IntArray> {
        val building = HashMap<String, IntList>(15000)
        for (assetIndex in 1..67) {
            try {
                assets.open("${StrongsRepository.ASSET_DIR}/words_$assetIndex")
                    .bufferedReader().use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            collect(assetIndex, line, building)
                        }
                    }
            } catch (e: IOException) {
                // A book with no word data simply contributes nothing.
            }
        }
        val finished = HashMap<String, IntArray>(building.size * 2)
        building.forEach { (number, list) -> finished[number] = list.toArray() }
        return finished
    }

    /** One line is `3:16` then, tab separated, `world<US>G2889<US>` per word. */
    private fun collect(assetIndex: Int, line: String, into: HashMap<String, IntList>) {
        val firstTab = line.indexOf('\t')
        if (firstTab < 3) return
        val colon = line.indexOf(':')
        if (colon < 1 || colon > firstTab) return
        val chapter = line.substring(0, colon).toIntOrNull() ?: return
        val verse = line.substring(colon + 1, firstTab).toIntOrNull() ?: return
        if (chapter > 255 || verse > 255) return
        val packed = assetIndex shl 16 or (chapter shl 8) or verse

        var index = firstTab + 1
        while (index < line.length) {
            val chunkEnd = line.indexOf('\t', index).let { if (it < 0) line.length else it }
            val separator = line.indexOf(SEPARATOR, index)
            if (separator in index until chunkEnd) {
                val numberEnd = line.indexOf(SEPARATOR, separator + 1)
                    .let { if (it < 0 || it > chunkEnd) chunkEnd else it }
                if (numberEnd > separator + 1) {
                    val number = line.substring(separator + 1, numberEnd)
                    val list = into.getOrPut(number) { IntList(8) }
                    // A word used twice in one verse is still one occurrence here,
                    // and lines are in order, so the repeat is always the last one.
                    if (list.size == 0 || list.values[list.size - 1] != packed) list.add(packed)
                }
            }
            if (chunkEnd >= line.length) break
            index = chunkEnd + 1
        }
    }

    companion object {
        private const val SEPARATOR = '\u001F'

        fun assetIndexOf(packed: Int): Int = packed shr 16
        fun chapterOf(packed: Int): Int = packed shr 8 and 0xFF
        fun verseOf(packed: Int): Int = packed and 0xFF

        @Volatile
        private var instance: Concordance? = null

        fun get(assets: AssetManager): Concordance =
            instance ?: synchronized(this) {
                instance ?: Concordance(assets).also { instance = it }
            }
    }
}
