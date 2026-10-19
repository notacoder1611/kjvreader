package com.realsneed.biblereader.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** One highlighted verse, as it appears in a backup file. */
data class BackupHighlight(
    val book: String,
    val chapter: Int,
    val verse: Int,
    val color: HighlightColor,
    val updatedAt: Long
)

/** One note - or one block note - as it appears in a backup file. */
data class BackupNote(
    val book: String,
    val chapter: Int,
    val verse: Int,
    val endVerse: Int,
    val body: String,
    val createdAt: Long
)

/** A verse filed in a folder, as it appears in a backup file. */
data class BackupVerse(
    val book: String,
    val chapter: Int,
    val verse: Int,
    val addedAt: Long
)

/**
 * One folder, as it appears in a backup file.
 *
 * Folders are stored by **path** - [parent] holds the titles above it - rather
 * than by database id, because the ids on the phone being restored onto are
 * different ones. That is what lets a restore find "Sermons / Grace" again
 * instead of creating a second copy of it.
 */
data class BackupFolder(
    val parent: List<String>,
    val title: String,
    val createdAt: Long,
    val verses: List<BackupVerse>
) {
    val path: List<String> get() = parent + title
}

/** One history entry, as it appears in a backup file. */
data class BackupVisit(
    val book: String,
    val chapter: Int,
    val verse: Int,
    val viewedAt: Long
)

/** The whole of a backup file, in memory. */
data class Backup(
    val highlights: List<BackupHighlight>,
    val notes: List<BackupNote>,
    val folders: List<BackupFolder>,
    val visits: List<BackupVisit>,
    val exportedAt: Long
) {
    val verseCount: Int get() = folders.sumOf { it.verses.size }

    val isEmpty: Boolean
        get() = highlights.isEmpty() && notes.isEmpty() && folders.isEmpty() && visits.isEmpty()
}

/** What a restore actually changed - not what the file held. */
data class RestoreReport(
    val highlights: Int,
    val notes: Int,
    val folders: Int,
    val verses: Int,
    val visits: Int,
    val merged: Boolean
) {
    val total: Int get() = highlights + notes + folders + verses + visits
}

/** A file that cannot be read as a backup, with a message fit to show the reader. */
class BackupFormatException(message: String) : Exception(message)

/**
 * Reading and writing the reader's own data as one file.
 *
 * The file is plain JSON with a `format` marker and a `version`, so a file from a
 * future version of the app is refused with a sentence rather than half-imported.
 * Restores go through the database in **one transaction**, so they land whole or
 * not at all.
 */
object BackupFile {

    private const val MAGIC = "biblereader-backup"
    private const val VERSION = 1

    /** Shown when the chosen file is not one of ours at all. */
    private const val NOT_A_BACKUP = "That file isn't a KJV Reader backup."

    private const val T_MARK = "verse_mark"
    private const val T_NOTE = "verse_note"
    private const val T_FOLDER = "folder"
    private const val T_VERSE = "folder_verse"
    private const val T_HISTORY = "verse_history"

    /** Kept in step with VerseStore: a restore must not grow past the cap. */
    private const val HISTORY_LIMIT = 200

    /** A folder path separator that cannot occur inside a title. */
    private const val PATH_SEP = "\u0001"

    /** One row of the folder table, while paths are being worked out. */
    private class FolderRow(
        val id: Long,
        val parentId: Long?,
        val title: String,
        val createdAt: Long
    )

    // ---------------------------------------------------------------- snapshot

    /** Everything the reader has made, read in one go. */
    fun snapshot(store: VerseStore): Backup {
        val db = store.readableDatabase
        val translation = VerseStore.TRANSLATION
        return Backup(
            highlights = readHighlights(db, translation),
            notes = readNotes(db, translation),
            folders = readFolders(db, translation),
            visits = readVisits(db, translation),
            exportedAt = System.currentTimeMillis()
        )
    }

    private fun readHighlights(db: SQLiteDatabase, translation: String): List<BackupHighlight> {
        val highlights = ArrayList<BackupHighlight>()
        db.query(
            T_MARK,
            arrayOf("book", "chapter", "verse", "color", "updated_at"),
            "translation = ? AND color IS NOT NULL",
            arrayOf(translation),
            null, null, "book, chapter, verse"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val color = HighlightColor.fromToken(cursor.getString(3)) ?: continue
                highlights.add(
                    BackupHighlight(
                        cursor.getString(0), cursor.getInt(1), cursor.getInt(2), color,
                        cursor.getLong(4)
                    )
                )
            }
        }
        return highlights
    }

    private fun readNotes(db: SQLiteDatabase, translation: String): List<BackupNote> {
        val notes = ArrayList<BackupNote>()
        db.query(
            T_NOTE,
            arrayOf("book", "chapter", "verse", "end_verse", "body", "created_at"),
            "translation = ?",
            arrayOf(translation),
            null, null, "book, chapter, verse, created_at"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val start = cursor.getInt(2)
                notes.add(
                    BackupNote(
                        cursor.getString(0), cursor.getInt(1), start,
                        cursor.getInt(3).coerceAtLeast(start), cursor.getString(4), cursor.getLong(5)
                    )
                )
            }
        }
        return notes
    }

    private fun readFolders(db: SQLiteDatabase, translation: String): List<BackupFolder> {
        val rows = ArrayList<FolderRow>()
        db.query(
            T_FOLDER, arrayOf("id", "parent_id", "title", "created_at"),
            null, null, null, null, "id"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(
                    FolderRow(
                        cursor.getLong(0),
                        if (cursor.isNull(1)) null else cursor.getLong(1),
                        cursor.getString(2),
                        cursor.getLong(3)
                    )
                )
            }
        }
        if (rows.isEmpty()) return emptyList()

        val paths = pathsById(rows)

        val versesByFolder = HashMap<Long, MutableList<BackupVerse>>()
        db.query(
            T_VERSE,
            arrayOf("folder_id", "book", "chapter", "verse", "added_at"),
            "translation = ?",
            arrayOf(translation),
            null, null, "folder_id, book, chapter, verse"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                versesByFolder.getOrPut(cursor.getLong(0)) { ArrayList(4) }.add(
                    BackupVerse(
                        cursor.getString(1), cursor.getInt(2), cursor.getInt(3), cursor.getLong(4)
                    )
                )
            }
        }

        // Shallowest first, so a parent is written before anything inside it and
        // an import can create the tree in order.
        return rows
            .sortedWith(compareBy({ paths[it.id]?.size ?: 0 }, { it.title }))
            .map { row ->
                val path = paths[row.id] ?: emptyList()
                BackupFolder(
                    parent = path.dropLast(1),
                    title = row.title,
                    createdAt = row.createdAt,
                    verses = versesByFolder[row.id] ?: emptyList()
                )
            }
    }

    private fun readVisits(db: SQLiteDatabase, translation: String): List<BackupVisit> {
        val visits = ArrayList<BackupVisit>()
        db.query(
            T_HISTORY,
            arrayOf("book", "chapter", "verse", "viewed_at"),
            "translation = ?",
            arrayOf(translation),
            null, null, "viewed_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                visits.add(
                    BackupVisit(
                        cursor.getString(0), cursor.getInt(1), cursor.getInt(2), cursor.getLong(3)
                    )
                )
            }
        }
        return visits
    }

    // --------------------------------------------------------------------- json

    /** The backup as text, ready to write to a file. */
    fun toJson(backup: Backup): String {
        val root = JSONObject()
        root.put("app", "KJV Reader")
        root.put("format", MAGIC)
        root.put("version", VERSION)
        root.put("exportedAt", backup.exportedAt)
        root.put(
            "highlights",
            JSONArray().apply {
                backup.highlights.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("book", item.book)
                            put("chapter", item.chapter)
                            put("verse", item.verse)
                            put("color", item.color.token)
                            put("updatedAt", item.updatedAt)
                        }
                    )
                }
            }
        )
        root.put(
            "notes",
            JSONArray().apply {
                backup.notes.forEach { item ->
                    put(
                        JSONObject().apply {
                            put("book", item.book)
                            put("chapter", item.chapter)
                            put("verse", item.verse)
                            put("endVerse", item.endVerse)
                            put("body", item.body)
                            put("createdAt", item.createdAt)
                        }
                    )
                }
            }
        )
        root.put(
            "folders",
            JSONArray().apply {
                backup.folders.forEach { folder ->
                    put(
                        JSONObject().apply {
                            put("parent", JSONArray(folder.parent))
                            put("title", folder.title)
                            put("createdAt", folder.createdAt)
                            put(
                                "verses",
                                JSONArray().apply {
                                    folder.verses.forEach { verse ->
                                        put(
                                            JSONObject().apply {
                                                put("book", verse.book)
                                                put("chapter", verse.chapter)
                                                put("verse", verse.verse)
                                                put("addedAt", verse.addedAt)
                                            }
                                        )
                                    }
                                }
                            )
                        }
                    )
                }
            }
        )
        root.put(
            "history",
            JSONArray().apply {
                backup.visits.forEach { visit ->
                    put(
                        JSONObject().apply {
                            put("book", visit.book)
                            put("chapter", visit.chapter)
                            put("verse", visit.verse)
                            put("viewedAt", visit.viewedAt)
                        }
                    )
                }
            }
        )
        return root.toString(2)
    }

    /**
     * Reads a backup file. Anything unreadable is refused with a sentence the
     * reader can act on; a single malformed row is skipped rather than losing the
     * whole file.
     */
    fun parse(text: String): Backup {
        val root = try {
            JSONObject(text)
        } catch (error: JSONException) {
            throw BackupFormatException(NOT_A_BACKUP)
        }
        if (root.optString("format") != MAGIC) throw BackupFormatException(NOT_A_BACKUP)
        if (root.optInt("version", 0) > VERSION) {
            throw BackupFormatException("That backup was made by a newer version of the app.")
        }

        val highlights = objects(root, "highlights").mapNotNull { item ->
            val color = HighlightColor.fromToken(item.optString("color")) ?: return@mapNotNull null
            val book = item.optString("book")
            val chapter = item.optInt("chapter", 0)
            val verse = item.optInt("verse", 0)
            if (book.isBlank() || chapter < 1 || verse < 1) return@mapNotNull null
            BackupHighlight(book, chapter, verse, color, item.optLong("updatedAt", 0L))
        }

        val notes = objects(root, "notes").mapNotNull { item ->
            val body = item.optString("body")
            val book = item.optString("book")
            val chapter = item.optInt("chapter", 0)
            val verse = item.optInt("verse", 0)
            if (book.isBlank() || chapter < 1 || verse < 1 || body.isBlank()) {
                return@mapNotNull null
            }
            BackupNote(
                book, chapter, verse,
                item.optInt("endVerse", verse).coerceAtLeast(verse),
                body, item.optLong("createdAt", 0L)
            )
        }

        val folders = objects(root, "folders").mapNotNull { item ->
            val title = item.optString("title")
            if (title.isBlank()) return@mapNotNull null
            val parent = item.optJSONArray("parent")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    array.optString(index).takeIf { it.isNotBlank() }
                }
            } ?: emptyList()
            val verses = objects(item, "verses").mapNotNull { verse ->
                val book = verse.optString("book")
                val chapter = verse.optInt("chapter", 0)
                val number = verse.optInt("verse", 0)
                if (book.isBlank() || chapter < 1 || number < 1) return@mapNotNull null
                BackupVerse(book, chapter, number, verse.optLong("addedAt", 0L))
            }
            BackupFolder(parent, title, item.optLong("createdAt", 0L), verses)
        }

        val visits = objects(root, "history").mapNotNull { item ->
            val book = item.optString("book")
            val chapter = item.optInt("chapter", 0)
            val verse = item.optInt("verse", 0)
            if (book.isBlank() || chapter < 1 || verse < 1) return@mapNotNull null
            BackupVisit(book, chapter, verse, item.optLong("viewedAt", 0L))
        }

        return Backup(highlights, notes, folders, visits, root.optLong("exportedAt", 0L))
    }

    private fun objects(owner: JSONObject, name: String): List<JSONObject> {
        val array = owner.optJSONArray(name) ?: return emptyList()
        val items = ArrayList<JSONObject>(array.length())
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let { items.add(it) }
        }
        return items
    }

    /** Path from the root for every folder in [rows], keyed by id, title included. */
    private fun pathsById(rows: List<FolderRow>): Map<Long, List<String>> {
        val byId = rows.associateBy { it.id }
        val paths = HashMap<Long, List<String>>(rows.size)
        for (row in rows) {
            val path = ArrayList<String>()
            var parent = row.parentId
            var guard = 0
            while (parent != null && guard++ < 1000) {
                val above = byId[parent] ?: break
                path.add(0, above.title)
                parent = above.parentId
            }
            paths[row.id] = path + row.title
        }
        return paths
    }

    // ------------------------------------------------------------------ restore

    /**
     * Writes [backup] into the database.
     *
     * With [merge] on, nothing already on the phone is deleted: a highlight whose
     * stored copy is newer wins, an identical note is not added twice, and a folder
     * is matched by its path rather than created again. With it off the reader's
     * data is cleared first, and the file becomes the whole of it.
     */
    fun restore(store: VerseStore, backup: Backup, merge: Boolean): RestoreReport {
        val db = store.writableDatabase
        val translation = VerseStore.TRANSLATION
        var highlights = 0
        var notes = 0
        var folders = 0
        var verses = 0
        var visits = 0

        db.beginTransaction()
        try {
            if (!merge) clearAll(db, translation)

            // Highlights: one per verse, so the newer copy wins.
            backup.highlights.forEach { item ->
                val values = ContentValues(7).apply {
                    put("translation", translation)
                    put("book", item.book)
                    put("chapter", item.chapter)
                    put("verse", item.verse)
                    put("color", item.color.token)
                    put("note", null as String?)
                    put("updated_at", item.updatedAt)
                }
                val inserted =
                    db.insertWithOnConflict(T_MARK, null, values, SQLiteDatabase.CONFLICT_IGNORE)
                if (inserted != -1L) {
                    highlights++
                } else {
                    highlights += db.update(
                        T_MARK,
                        ContentValues(2).apply {
                            put("color", item.color.token)
                            put("updated_at", item.updatedAt)
                        },
                        "translation = ? AND book = ? AND chapter = ? AND verse = ? AND updated_at < ?",
                        arrayOf(
                            translation, item.book, item.chapter.toString(),
                            item.verse.toString(), item.updatedAt.toString()
                        )
                    )
                }
            }

            // Notes have no natural key, so an exact copy of one is the duplicate.
            val knownNotes = HashSet<String>()
            db.query(
                T_NOTE,
                arrayOf("book", "chapter", "verse", "end_verse", "body"),
                "translation = ?", arrayOf(translation), null, null, null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    knownNotes.add(
                        noteKey(
                            cursor.getString(0), cursor.getInt(1), cursor.getInt(2),
                            cursor.getInt(3), cursor.getString(4)
                        )
                    )
                }
            }
            backup.notes.forEach { item ->
                if (!knownNotes.add(
                        noteKey(item.book, item.chapter, item.verse, item.endVerse, item.body)
                    )
                ) {
                    return@forEach
                }
                val inserted = db.insert(
                    T_NOTE, null,
                    ContentValues(7).apply {
                        put("translation", translation)
                        put("book", item.book)
                        put("chapter", item.chapter)
                        put("verse", item.verse)
                        put("end_verse", item.endVerse)
                        put("body", item.body)
                        put("created_at", item.createdAt)
                    }
                )
                if (inserted != -1L) notes++
            }

            // Folders: matched by path, so a merge files new verses into the folder
            // that is already there instead of making a second "Sermons".
            val folderIds = HashMap<String, Long>()
            if (merge) readFolderPaths(db, folderIds)
            backup.folders.forEach { folder ->
                val key = folder.path.joinToString(PATH_SEP)
                val id: Long = folderIds[key] ?: run {
                    val parentId =
                        if (folder.parent.isEmpty()) {
                            null
                        } else {
                            folderIds[folder.parent.joinToString(PATH_SEP)]
                        }
                    val created = db.insert(
                        T_FOLDER, null,
                        ContentValues(4).apply {
                            put("parent_id", parentId)
                            put("title", folder.title)
                            put("created_at", folder.createdAt)
                        }
                    )
                    folderIds[key] = created
                    folders++
                    created
                }
                folder.verses.forEach { verse ->
                    val inserted = db.insertWithOnConflict(
                        T_VERSE, null,
                        ContentValues(6).apply {
                            put("folder_id", id)
                            put("translation", translation)
                            put("book", verse.book)
                            put("chapter", verse.chapter)
                            put("verse", verse.verse)
                            put("added_at", verse.addedAt)
                        },
                        SQLiteDatabase.CONFLICT_IGNORE
                    )
                    if (inserted != -1L) verses++
                }
            }

            // History: one row per verse, newest visit wins.
            backup.visits.forEach { item ->
                val values = ContentValues(5).apply {
                    put("translation", translation)
                    put("book", item.book)
                    put("chapter", item.chapter)
                    put("verse", item.verse)
                    put("viewed_at", item.viewedAt)
                }
                val inserted =
                    db.insertWithOnConflict(T_HISTORY, null, values, SQLiteDatabase.CONFLICT_IGNORE)
                if (inserted != -1L) {
                    visits++
                } else {
                    visits += db.update(
                        T_HISTORY,
                        ContentValues(1).apply { put("viewed_at", item.viewedAt) },
                        "translation = ? AND book = ? AND chapter = ? AND verse = ? AND viewed_at < ?",
                        arrayOf(
                            translation, item.book, item.chapter.toString(),
                            item.verse.toString(), item.viewedAt.toString()
                        )
                    )
                }
            }
            // The cap belongs to the phone, not the file.
            db.execSQL(
                "DELETE FROM $T_HISTORY WHERE id NOT IN (" +
                    "SELECT id FROM $T_HISTORY ORDER BY viewed_at DESC LIMIT $HISTORY_LIMIT)"
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return RestoreReport(highlights, notes, folders, verses, visits, merge)
    }

    /** Empties the reader's own data, leaving the tables in place. */
    private fun clearAll(db: SQLiteDatabase, translation: String) {
        db.delete(T_MARK, "translation = ?", arrayOf(translation))
        db.delete(T_NOTE, "translation = ?", arrayOf(translation))
        db.delete(T_HISTORY, "translation = ?", arrayOf(translation))
        db.delete(T_VERSE, null, null)
        db.delete(T_FOLDER, null, null)
    }

    /** Every folder path already on the phone, keyed by path. */
    private fun readFolderPaths(db: SQLiteDatabase, into: MutableMap<String, Long>) {
        val rows = ArrayList<FolderRow>()
        db.query(
            T_FOLDER, arrayOf("id", "parent_id", "title", "created_at"),
            null, null, null, null, "id"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(
                    FolderRow(
                        cursor.getLong(0),
                        if (cursor.isNull(1)) null else cursor.getLong(1),
                        cursor.getString(2),
                        cursor.getLong(3)
                    )
                )
            }
        }
        pathsById(rows).forEach { (id, path) -> into[path.joinToString(PATH_SEP)] = id }
    }

    private fun noteKey(
        book: String,
        chapter: Int,
        verse: Int,
        endVerse: Int,
        body: String
    ): String = "$book|$chapter|$verse|$endVerse|$body"
}
