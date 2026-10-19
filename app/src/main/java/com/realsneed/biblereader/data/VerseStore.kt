package com.realsneed.biblereader.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * The highlight palette.
 *
 * Tokens (not hex colours) are persisted, so the palette can be re-themed for
 * light and dark mode without migrating any user data.
 */
enum class HighlightColor(val token: String, val light: Int, val dark: Int) {
    YELLOW("yellow", 0xFFFFF176.toInt(), 0xFF5C4F1B.toInt()),
    GREEN("green", 0xFFB9F6CA.toInt(), 0xFF1E4A32.toInt()),
    BLUE("blue", 0xFFB3E5FC.toInt(), 0xFF17394D.toInt()),
    RED("red", 0xFFFFCDD2.toInt(), 0xFF4E2328.toInt());

    fun colorFor(darkTheme: Boolean): Int = if (darkTheme) dark else light

    companion object {
        fun fromToken(token: String?): HighlightColor? =
            if (token == null) null else values().firstOrNull { it.token == token }
    }
}

/**
 * Everything attached to one verse: its highlight colour, and how many notes
 * **end** here. A note covers a range of verses but is only marked once, at the
 * end of that range - so a block carries one dagger, not one per verse.
 */
data class VerseState(val color: HighlightColor?, val noteEnds: Int) {
    val hasNote: Boolean get() = noteEnds > 0
}

/**
 * One note, covering [startVerse] to [endVerse] inclusive. A note on a single
 * verse has `startVerse == endVerse`; a note added to a block of verses is **one
 * row** spanning the block, not a copy on every verse.
 */
data class VerseNote(
    val id: Long,
    val startVerse: Int,
    val endVerse: Int,
    val body: String,
    val createdAt: Long
) {
    val isRange: Boolean get() = endVerse > startVerse

    /** "3:15-16" for a range, "3:16" for a single verse. */
    fun reference(chapter: Int): String =
        if (isRange) "$chapter:$startVerse-$endVerse" else "$chapter:$startVerse"
}

/** A verse the reader has been to, with when they were there. */
data class VisitedAt(
    val id: Long,
    val bookId: String,
    val chapter: Int,
    val verse: Int,
    val viewedAt: Long
)

/** A note plus where it lives, for the Notes & highlights screen. */
data class NoteAt(
    val id: Long,
    val bookId: String,
    val chapter: Int,
    val verse: Int,
    val endVerse: Int,
    val body: String
)

/** A highlighted verse, or a run of them, for the Notes & highlights screen. */
data class ColourAt(
    val bookId: String,
    val chapter: Int,
    val verse: Int,
    val endVerse: Int,
    val color: HighlightColor
)

/**
 * All of the reader's own data in one small SQLite file: highlight colours,
 * notes (many per verse) and the folder tree.
 */
class VerseStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE verse_mark (" +
                "translation TEXT NOT NULL, " +
                "book TEXT NOT NULL, " +
                "chapter INTEGER NOT NULL, " +
                "verse INTEGER NOT NULL, " +
                "color TEXT, " +
                "note TEXT, " +
                "updated_at INTEGER NOT NULL, " +
                "PRIMARY KEY (translation, book, chapter, verse))"
        )
        createNoteTable(db)
        createFolderTables(db)
        createHistoryTable(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) createFolderTables(db)
        if (oldVersion < 3) {
            // One note per verse became many: move any existing notes across,
            // then stop using the old column. Nothing is lost.
            createNoteTable(db)
            db.execSQL(
                "INSERT INTO verse_note (translation, book, chapter, verse, end_verse, body, created_at) " +
                    "SELECT translation, book, chapter, verse, verse, note, updated_at FROM verse_mark " +
                    "WHERE note IS NOT NULL AND TRIM(note) <> ''"
            )
            db.execSQL("UPDATE verse_mark SET note = NULL WHERE note IS NOT NULL")
        }
        if (oldVersion < 4) {
            // A note now covers a range, so a block is one row. Existing notes
            // are single verses, and identical ones sitting on consecutive verses
            // (what a block note used to create) are folded back together.
            db.execSQL("ALTER TABLE verse_note ADD COLUMN end_verse INTEGER")
            db.execSQL("UPDATE verse_note SET end_verse = verse WHERE end_verse IS NULL")
            mergeBlockNotes(db)
        }
        if (oldVersion < 5) createHistoryTable(db)
    }

    private fun createNoteTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS verse_note (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "translation TEXT NOT NULL, " +
                "book TEXT NOT NULL, " +
                "chapter INTEGER NOT NULL, " +
                "verse INTEGER NOT NULL, " +
                "end_verse INTEGER NOT NULL, " +
                "body TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_verse_note ON verse_note " +
                "(translation, book, chapter, verse)"
        )
    }

    /** One row of verse_note while the migration is folding ranges together. */
    private class RawNote(
        val id: Long,
        val book: String,
        val chapter: Int,
        val verse: Int,
        val body: String
    )

    /**
     * Before ranges existed, putting one note on a block of verses wrote the same
     * note onto every verse of it. This folds each run of identical, consecutive
     * notes back into a single ranged row, so nothing shows up several times.
     *
     * Rows are grouped by their text first, so another note sitting inside the
     * block (say on verse 16 of a 15-18 block) does not split the block in two.
     */
    private fun mergeBlockNotes(db: SQLiteDatabase) {
        val byText = LinkedHashMap<String, MutableList<RawNote>>()
        db.rawQuery(
            "SELECT id, book, chapter, verse, body FROM verse_note " +
                "ORDER BY book, chapter, verse, created_at",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val note = RawNote(
                    cursor.getLong(0), cursor.getString(1),
                    cursor.getInt(2), cursor.getInt(3), cursor.getString(4)
                )
                // The query is already in verse order, so each group stays sorted.
                byText.getOrPut("${note.book}|${note.chapter}|${note.body}") { ArrayList() }
                    .add(note)
            }
        }
        for (group in byText.values) {
            var first = 0
            while (first < group.size) {
                var last = first
                while (last + 1 < group.size && group[last + 1].verse == group[last].verse + 1) {
                    last++
                }
                if (last > first) {
                    db.execSQL(
                        "UPDATE verse_note SET end_verse = ? WHERE id = ?",
                        arrayOf(group[last].verse, group[first].id)
                    )
                    for (i in first + 1..last) {
                        db.execSQL("DELETE FROM verse_note WHERE id = ?", arrayOf(group[i].id))
                    }
                }
                first = last + 1
            }
        }
    }

    private fun createHistoryTable(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS $TABLE_HISTORY (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "translation TEXT NOT NULL, " +
                "book TEXT NOT NULL, " +
                "chapter INTEGER NOT NULL, " +
                "verse INTEGER NOT NULL, " +
                "viewed_at INTEGER NOT NULL)"
        )
        // One row per verse: visiting it again replaces the old row, which is what
        // moves it to the top of the list instead of duplicating it.
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_history_verse ON $TABLE_HISTORY " +
                "(translation, book, chapter, verse)"
        )
    }

    private fun createFolderTables(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS folder (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "parent_id INTEGER, " +
                "title TEXT NOT NULL, " +
                "created_at INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_folder_parent ON folder (parent_id)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS folder_verse (" +
                "folder_id INTEGER NOT NULL, " +
                "translation TEXT NOT NULL, " +
                "book TEXT NOT NULL, " +
                "chapter INTEGER NOT NULL, " +
                "verse INTEGER NOT NULL, " +
                "added_at INTEGER NOT NULL, " +
                "PRIMARY KEY (folder_id, translation, book, chapter, verse))"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_folder_verse ON folder_verse (folder_id)")
    }

    // ------------------------------------------------------------------ colours

    /** Highlight colours for one chapter, keyed by verse. One query per chapter. */
    fun colorsFor(book: String, chapter: Int): Map<Int, HighlightColor> {
        val colors = HashMap<Int, HighlightColor>()
        readMarks(book, chapter).use { cursor ->
            while (cursor.moveToNext()) {
                val color = HighlightColor.fromToken(cursor.getString(1)) ?: continue
                colors[cursor.getInt(0)] = color
            }
        }
        return colors
    }

    fun setColor(book: String, chapter: Int, verse: Int, color: HighlightColor?) {
        if (color == null) {
            writableDatabase.delete(TABLE_MARK, markWhere(), markArgs(book, chapter, verse))
            return
        }
        val values = ContentValues(7).apply {
            put("translation", TRANSLATION)
            put("book", book)
            put("chapter", chapter)
            put("verse", verse)
            put("color", color.token)
            put("note", null as String?)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict(
            TABLE_MARK, null, values, SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    private fun readMarks(book: String, chapter: Int): Cursor =
        readableDatabase.query(
            TABLE_MARK,
            arrayOf("verse", "color"),
            "translation = ? AND book = ? AND chapter = ?",
            arrayOf(TRANSLATION, book, chapter.toString()),
            null, null, null
        )

    private fun markWhere() = "translation = ? AND book = ? AND chapter = ? AND verse = ?"

    private fun markArgs(book: String, chapter: Int, verse: Int) =
        arrayOf(TRANSLATION, book, chapter.toString(), verse.toString())

    // -------------------------------------------------------------------- notes

    /** Notes for one chapter, keyed by the verse each one starts at. */
    fun notesFor(book: String, chapter: Int): Map<Int, List<VerseNote>> {
        val notes = HashMap<Int, MutableList<VerseNote>>()
        readableDatabase.query(
            TABLE_NOTE,
            arrayOf("id", "verse", "end_verse", "body", "created_at"),
            "translation = ? AND book = ? AND chapter = ?",
            arrayOf(TRANSLATION, book, chapter.toString()),
            null, null, "verse ASC, created_at ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val start = cursor.getInt(1)
                val end = cursor.getInt(2).coerceAtLeast(start)
                notes.getOrPut(start) { ArrayList(2) }.add(
                    VerseNote(
                        cursor.getLong(0), start, end, cursor.getString(3), cursor.getLong(4)
                    )
                )
            }
        }
        return notes
    }

    /** Every note covering [verse] - a block note belongs to each verse it spans. */
    fun notesCovering(book: String, chapter: Int, verse: Int): List<VerseNote> =
        notesFor(book, chapter).values.asSequence()
            .flatten()
            .filter { verse >= it.startVerse && verse <= it.endVerse }
            .toList()

    fun notes(book: String, chapter: Int, verse: Int): List<VerseNote> =
        notesCovering(book, chapter, verse)

    /**
     * One note covering [startVerse] to [endVerse]. A single row, so putting a
     * note on a block does not put it on every verse of that block.
     */
    fun addNote(book: String, chapter: Int, startVerse: Int, endVerse: Int, body: String): Long {
        val values = ContentValues(7).apply {
            put("translation", TRANSLATION)
            put("book", book)
            put("chapter", chapter)
            put("verse", startVerse)
            put("end_verse", endVerse.coerceAtLeast(startVerse))
            put("body", body.trim())
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insert(TABLE_NOTE, null, values)
    }

    fun deleteNote(noteId: Long) {
        writableDatabase.delete(TABLE_NOTE, "id = ?", arrayOf(noteId.toString()))
    }

    // ----------------------------------------------------------------- combined

    fun statesFor(book: String, chapter: Int): Map<Int, VerseState> {
        val colors = colorsFor(book, chapter)
        val notes = notesFor(book, chapter)
        val states = HashMap<Int, VerseState>(colors.size + notes.size)
        colors.forEach { (verse, color) -> states[verse] = VerseState(color, 0) }
        // A note is marked once, at the end of the range it covers.
        notes.values.forEach { list ->
            for (note in list) {
                val existing = states[note.endVerse]
                states[note.endVerse] = VerseState(existing?.color, (existing?.noteEnds ?: 0) + 1)
            }
        }
        return states
    }

    fun state(book: String, chapter: Int, verse: Int): VerseState {
        val color = colorsFor(book, chapter)[verse]
        return VerseState(color, notesFor(book, chapter).values.sumOf { list ->
            list.count { it.endVerse == verse }
        })
    }

    /**
     * Where the reader has been: the chapters turned to and the verses jumped to,
     * newest first. One row per verse - visiting somewhere again moves it to the
     * top rather than adding a duplicate - and only the last [HISTORY_LIMIT] are
     * kept, so the list stays a history and not an archive.
     */
    fun recordVisit(book: String, chapter: Int, verse: Int) {
        val values = ContentValues(6).apply {
            put("translation", TRANSLATION)
            put("book", book)
            put("chapter", chapter)
            put("verse", verse)
            put("viewed_at", System.currentTimeMillis())
        }
        val db = writableDatabase
        db.insertWithOnConflict(TABLE_HISTORY, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        db.execSQL(
            "DELETE FROM $TABLE_HISTORY WHERE id NOT IN (" +
                "SELECT id FROM $TABLE_HISTORY ORDER BY viewed_at DESC LIMIT $HISTORY_LIMIT)"
        )
    }

    fun history(): List<VisitedAt> {
        val visits = ArrayList<VisitedAt>()
        readableDatabase.query(
            TABLE_HISTORY,
            arrayOf("id", "book", "chapter", "verse", "viewed_at"),
            "translation = ?",
            arrayOf(TRANSLATION),
            null, null, "viewed_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                visits.add(
                    VisitedAt(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getInt(2),
                        cursor.getInt(3),
                        cursor.getLong(4)
                    )
                )
            }
        }
        return visits
    }

    fun removeVisit(id: Long) {
        writableDatabase.delete(TABLE_HISTORY, "id = ?", arrayOf(id.toString()))
    }

    fun clearHistory() {
        writableDatabase.delete(TABLE_HISTORY, "translation = ?", arrayOf(TRANSLATION))
    }

    // ------------------------------------------------- for the list screens

    fun allNotes(): List<NoteAt> {
        val notes = ArrayList<NoteAt>()
        readableDatabase.query(
            TABLE_NOTE,
            arrayOf("id", "book", "chapter", "verse", "end_verse", "body"),
            "translation = ?",
            arrayOf(TRANSLATION),
            null, null, "book, chapter, verse, created_at"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val verse = cursor.getInt(3)
                notes.add(
                    NoteAt(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getInt(2),
                        verse,
                        cursor.getInt(4).coerceAtLeast(verse),
                        cursor.getString(5)
                    )
                )
            }
        }
        return notes
    }

    /**
     * Highlighted verses, with runs of the same colour already folded together -
     * a highlighted block is one entry, not one per verse.
     */
    fun allColors(): List<ColourAt> {
        val runs = ArrayList<ColourAt>()
        var lastBook = ""
        var lastChapter = -1
        var lastVerse = -1
        var lastColor: HighlightColor? = null
        readableDatabase.query(
            TABLE_MARK,
            arrayOf("book", "chapter", "verse", "color"),
            "translation = ?",
            arrayOf(TRANSLATION),
            null, null, "book, chapter, verse"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val color = HighlightColor.fromToken(cursor.getString(3)) ?: continue
                val book = cursor.getString(0)
                val chapter = cursor.getInt(1)
                val verse = cursor.getInt(2)
                val continues = book == lastBook && chapter == lastChapter &&
                    verse == lastVerse + 1 && color == lastColor
                if (continues) {
                    runs[runs.size - 1] = runs.last().copy(endVerse = verse)
                } else {
                    runs.add(ColourAt(book, chapter, verse, verse, color))
                }
                lastBook = book
                lastChapter = chapter
                lastVerse = verse
                lastColor = color
            }
        }
        return runs
    }

    companion object {
        /** The only translation the reader stores data against, for now. */
        const val TRANSLATION = "kjv"
        const val DB_NAME = "verse_marks.db"
        const val DB_VERSION = 5
        const val TABLE_MARK = "verse_mark"
        const val TABLE_NOTE = "verse_note"
        const val TABLE_HISTORY = "verse_history"

        /** How many visits are kept - enough to retrace a session, not an archive. */
        private const val HISTORY_LIMIT = 200
    }
}
