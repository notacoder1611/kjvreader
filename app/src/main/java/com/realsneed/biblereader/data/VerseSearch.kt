package com.realsneed.biblereader.data

/**
 * Full-text search over the KJV, built once and kept in memory.
 *
 * Every word maps to the verses that use it, so a query is a few list
 * intersections rather than a scan of four megabytes of text. A word matches
 * exactly, or as the beginning of a word ("beg" finds "beginning"); when neither
 * finds anything, words one letter away are tried too, so "begining" still works.
 * Several words must all be in the verse, and text in quotes must appear as a
 * phrase. Results come back in Bible order.
 */
class VerseSearch(private val repository: BibleRepository) {

    /** One matching verse, with its text as stored. */
    class Hit(val book: BookInfo, val chapter: Int, val verse: Int, val text: String)

    private class Doc(
        val book: BookInfo,
        val chapter: Int,
        val verse: Int,
        val text: String,
        val normalized: String
    )

    @Volatile private var docs: List<Doc>? = null
    @Volatile private var postings: Map<String, IntArray> = emptyMap()
    @Volatile private var vocabulary: Array<String> = emptyArray()

    val isReady: Boolean get() = docs != null

    /** Builds the index. Call from a background thread; it is idempotent. */
    fun build() {
        if (docs != null) return
        synchronized(this) {
            if (docs != null) return
            val built = ArrayList<Doc>(VERSE_CAPACITY)
            val index = HashMap<String, MutableList<Int>>(1 shl 14)
            for (book in BibleMeta.BOOKS) {
                for (chapter in repository.chapters(book)) {
                    for (verse in chapter.verses) {
                        val normalized = normalize(verse.text)
                        if (normalized.isEmpty()) continue
                        val position = built.size
                        built.add(Doc(book, chapter.number, verse.number, verse.text, normalized))
                        for (word in normalized.split(' ')) {
                            if (word.isEmpty()) continue
                            val list = index.getOrPut(word) { ArrayList(4) }
                            if (list.isEmpty() || list[list.size - 1] != position) list.add(position)
                        }
                    }
                }
            }
            val packed = HashMap<String, IntArray>(index.size)
            for ((word, list) in index) packed[word] = list.toIntArray()
            postings = packed
            vocabulary = packed.keys.sorted().toTypedArray()
            docs = built
        }
    }

    /** The words a query is asking for, for highlighting. */
    fun terms(query: String): List<String> {
        val phrases = PHRASES.findAll(query).map { normalize(it.groupValues[1]) }.toList()
        val plain = normalize(PHRASES.replace(query, " "))
        return (phrases + plain.split(' ')).flatMap { it.split(' ') }.filter { it.isNotEmpty() }
    }

    fun search(query: String, limit: Int = 300): List<Hit> {
        build()
        val all = docs ?: return emptyList()
        val asked = query.trim()
        if (asked.length < 2) return emptyList()

        val phrases = PHRASES.findAll(asked).map { normalize(it.groupValues[1]).trim() }
            .filter { it.isNotEmpty() }.toList()
        val tokens = normalize(PHRASES.replace(asked, " ")).split(' ')
            .filter { it.isNotEmpty() }
        if (tokens.isEmpty() && phrases.isEmpty()) return emptyList()

        var candidates: IntArray? = null
        for (token in tokens) {
            val group = alternatives(token)
            if (group.isEmpty()) return emptyList()
            val merged = merge(group.map { postings[it] ?: EMPTY })
            candidates = intersect(candidates, merged)
            if (candidates != null && candidates.isEmpty()) return emptyList()
        }

        val pool = candidates ?: IntArray(all.size) { it }
        val hits = ArrayList<Hit>(minOf(limit, 64))
        for (position in pool) {
            val doc = all[position]
            if (phrases.any { !doc.normalized.contains(it) }) continue
            hits.add(Hit(doc.book, doc.chapter, doc.verse, doc.text))
            if (hits.size >= limit) break
        }
        return hits
    }

    /**
     * The concrete words a token stands for: itself, then the words that begin
     * with it, then - only when the Bible has no such word at all - the words one
     * edit away.
     */
    private fun alternatives(token: String): List<String> {
        if (postings.containsKey(token)) return listOf(token)
        if (token.length >= 2) {
            val prefix = ArrayList<String>()
            for (word in vocabulary) {
                if (word.length > token.length && word.startsWith(token)) {
                    prefix.add(word)
                    if (prefix.size >= PREFIX_LIMIT) break
                }
            }
            if (prefix.isNotEmpty()) return prefix
        }
        if (token.length >= 4) {
            val nearby = ArrayList<String>()
            for (word in vocabulary) {
                if (near(word, token)) {
                    nearby.add(word)
                    if (nearby.size >= NEAR_LIMIT) break
                }
            }
            if (nearby.isNotEmpty()) return nearby
        }
        return emptyList()
    }

    private fun merge(lists: List<IntArray>): IntArray {
        if (lists.isEmpty()) return EMPTY
        if (lists.size == 1) return lists[0]
        val union = java.util.TreeSet<Int>()
        for (list in lists) for (value in list) union.add(value)
        return union.toIntArray()
    }

    private fun intersect(first: IntArray?, second: IntArray): IntArray {
        if (first == null) return second
        if (first.isEmpty() || second.isEmpty()) return EMPTY
        val out = ArrayList<Int>(minOf(first.size, second.size))
        var i = 0
        var j = 0
        while (i < first.size && j < second.size) {
            val a = first[i]
            val b = second[j]
            when {
                a == b -> {
                    out.add(a)
                    i++
                    j++
                }
                a < b -> i++
                else -> j++
            }
        }
        return out.toIntArray()
    }

    /** One insertion, deletion or substitution apart: "begining" for "beginning". */
    private fun near(candidate: String, typed: String): Boolean {
        val a: String
        val b: String
        if (candidate.length == typed.length) {
            var differences = 0
            for (position in typed.indices) {
                if (candidate[position] != typed[position] && ++differences > 1) return false
            }
            return differences <= 1
        }
        if (candidate.length > typed.length) {
            a = typed
            b = candidate
        } else {
            a = candidate
            b = typed
        }
        if (b.length - a.length != 1) return false
        var i = 0
        var j = 0
        var skipped = false
        while (i < a.length && j < b.length) {
            if (a[i] == b[j]) {
                i++
                j++
            } else if (skipped) {
                return false
            } else {
                skipped = true
                j++
            }
        }
        return true
    }

    /** Lower-case words only: "LORD's" becomes "lord s", punctuation disappears. */
    private fun normalize(raw: String): String {
        val out = StringBuilder(raw.length)
        var pendingSpace = false
        for (char in raw) {
            val lower = char.lowercaseChar()
            if (lower in 'a'..'z' || lower in '0'..'9') {
                if (pendingSpace && out.isNotEmpty()) out.append(' ')
                pendingSpace = false
                out.append(lower)
            } else if (out.isNotEmpty()) {
                pendingSpace = true
            }
        }
        return out.toString()
    }

    private companion object {
        val PHRASES = Regex("\"([^\"]+)\"")
        val EMPTY = IntArray(0)
        const val VERSE_CAPACITY = 32000
        const val PREFIX_LIMIT = 80
        const val NEAR_LIMIT = 24
    }
}
