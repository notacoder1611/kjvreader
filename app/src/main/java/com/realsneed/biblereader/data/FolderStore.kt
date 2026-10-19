package com.realsneed.biblereader.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/** A user-created folder. Folders nest to any depth through [parentId]. */
data class Folder(val id: Long, val parentId: Long?, val title: String)

/** A verse saved into a folder. */
data class SavedVerse(val bookId: String, val chapter: Int, val verse: Int, val addedAt: Long)

/**
 * The user's folder tree - the topics they file verses under.
 *
 * Folders are recursive (a folder may hold sub-folders without limit) and a
 * verse may be saved into more than one folder, so topics can overlap. Shares
 * [VerseStore]'s database, so highlights, notes and folders live in one file.
 */
class FolderStore(private val store: VerseStore) {

    private val read get() = store.readableDatabase
    private val write get() = store.writableDatabase

    private val columns = arrayOf(COL_ID, COL_PARENT, COL_TITLE)

    // ------------------------------------------------------------------ folders

    fun children(parentId: Long?): List<Folder> {
        val selection = if (parentId == null) "$COL_PARENT IS NULL" else "$COL_PARENT = ?"
        val args = if (parentId == null) null else arrayOf(parentId.toString())
        read.query(TABLE, columns, selection, args, null, null, ORDER).use { return it.toFolders() }
    }

    fun all(): List<Folder> =
        read.query(TABLE, columns, null, null, null, null, ORDER).use { it.toFolders() }

    fun folder(id: Long): Folder? =
        read.query(TABLE, columns, "$COL_ID = ?", arrayOf(id.toString()), null, null, null)
            .use { if (it.moveToFirst()) it.toFolder() else null }

    fun create(parentId: Long?, title: String): Long {
        val values = ContentValues(4).apply {
            put(COL_PARENT, parentId)
            put(COL_TITLE, title.trim())
            put(COL_CREATED, System.currentTimeMillis())
        }
        return write.insert(TABLE, null, values)
    }

    fun rename(id: Long, title: String) {
        write.update(
            TABLE,
            ContentValues(1).apply { put(COL_TITLE, title.trim()) },
            "$COL_ID = ?",
            arrayOf(id.toString())
        )
    }

    /** Deletes a folder, every folder nested inside it, and all their verses. */
    fun delete(id: Long) {
        val doomed = subtree(id, all())
        if (doomed.isEmpty()) return
        val args = doomed.map { it.toString() }.toTypedArray()
        val placeholders = doomed.joinToString(",") { "?" }
        write.delete(TABLE_VERSES, "$COL_FOLDER IN ($placeholders)", args)
        write.delete(TABLE, "$COL_ID IN ($placeholders)", args)
    }

    /** Root-first chain above [id], for the breadcrumb. */
    fun ancestors(id: Long): List<Folder> {
        val byId = all().associateBy { it.id }
        val chain = ArrayList<Folder>()
        var parent = byId[id]?.parentId
        while (parent != null) {
            val folder = byId[parent] ?: break
            chain.add(0, folder)
            parent = folder.parentId
        }
        return chain
    }

    fun childCount(folderId: Long): Int =
        read.rawQuery("SELECT COUNT(*) FROM $TABLE WHERE $COL_PARENT = ?", arrayOf(folderId.toString()))
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    // ------------------------------------------------------------ saved verses

    fun addVerse(folderId: Long, book: String, chapter: Int, verse: Int) {
        val values = ContentValues(7).apply {
            put(COL_FOLDER, folderId)
            put(COL_TRANSLATION, VerseStore.TRANSLATION)
            put(COL_BOOK, book)
            put(COL_CHAPTER, chapter)
            put(COL_VERSE, verse)
            put(COL_ADDED, System.currentTimeMillis())
        }
        write.insertWithOnConflict(TABLE_VERSES, null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun removeVerse(folderId: Long, book: String, chapter: Int, verse: Int) {
        write.delete(TABLE_VERSES, verseWhere(), verseArgs(folderId, book, chapter, verse))
    }

    fun isSaved(folderId: Long, book: String, chapter: Int, verse: Int): Boolean =
        read.query(TABLE_VERSES, arrayOf(COL_FOLDER), verseWhere(), verseArgs(folderId, book, chapter, verse), null, null, null)
            .use { it.moveToFirst() }

    fun foldersForVerse(book: String, chapter: Int, verse: Int): List<Folder> {
        val ids = ArrayList<Long>()
        read.query(
            TABLE_VERSES,
            arrayOf(COL_FOLDER),
            "$COL_TRANSLATION = ? AND $COL_BOOK = ? AND $COL_CHAPTER = ? AND $COL_VERSE = ?",
            arrayOf(VerseStore.TRANSLATION, book, chapter.toString(), verse.toString()),
            null, null, null
        ).use { while (it.moveToNext()) ids.add(it.getLong(0)) }
        if (ids.isEmpty()) return emptyList()
        val byId = all().associateBy { it.id }
        return ids.mapNotNull { byId[it] }
    }

    fun versesIn(folderId: Long): List<SavedVerse> {
        val verses = ArrayList<SavedVerse>()
        read.query(
            TABLE_VERSES,
            arrayOf(COL_BOOK, COL_CHAPTER, COL_VERSE, COL_ADDED),
            "$COL_FOLDER = ?",
            arrayOf(folderId.toString()),
            null, null, null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                verses.add(
                    SavedVerse(cursor.getString(0), cursor.getInt(1), cursor.getInt(2), cursor.getLong(3))
                )
            }
        }
        return verses
    }

    /** Verses filed directly in this folder. */
    fun verseCount(folderId: Long): Int =
        read.rawQuery("SELECT COUNT(*) FROM $TABLE_VERSES WHERE $COL_FOLDER = ?", arrayOf(folderId.toString()))
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }

    /** Verses in this folder *and* everything nested inside it. */
    fun subtreeVerseCount(folderId: Long): Int {
        val ids = subtree(folderId, all())
        if (ids.isEmpty()) return 0
        val args = ids.map { it.toString() }.toTypedArray()
        val placeholders = ids.joinToString(",") { "?" }
        return read.rawQuery("SELECT COUNT(*) FROM $TABLE_VERSES WHERE $COL_FOLDER IN ($placeholders)", args)
            .use { if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    // ------------------------------------------------------------------ helpers

    /** [id] plus every folder in its subtree (breadth first). */
    private fun subtree(id: Long, all: List<Folder>): Set<Long> {
        val byParent = all.groupBy { it.parentId }
        val collected = LinkedHashSet<Long>()
        val queue = ArrayDeque<Long>()
        queue.addLast(id)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!collected.add(current)) continue
            byParent[current]?.forEach { queue.addLast(it.id) }
        }
        return collected
    }

    private fun verseWhere() =
        "$COL_FOLDER = ? AND $COL_TRANSLATION = ? AND $COL_BOOK = ? AND $COL_CHAPTER = ? AND $COL_VERSE = ?"

    private fun verseArgs(folderId: Long, book: String, chapter: Int, verse: Int) =
        arrayOf(folderId.toString(), VerseStore.TRANSLATION, book, chapter.toString(), verse.toString())

    private fun Cursor.toFolders(): List<Folder> {
        val folders = ArrayList<Folder>(count)
        while (moveToNext()) folders.add(toFolder())
        return folders
    }

    private fun Cursor.toFolder(): Folder = Folder(
        getLong(0),
        if (isNull(1)) null else getLong(1),
        getString(2)
    )

    private companion object {
        const val TABLE = "folder"
        const val TABLE_VERSES = "folder_verse"
        const val COL_ID = "id"
        const val COL_PARENT = "parent_id"
        const val COL_TITLE = "title"
        const val COL_CREATED = "created_at"
        const val COL_FOLDER = "folder_id"
        const val COL_TRANSLATION = "translation"
        const val COL_BOOK = "book"
        const val COL_CHAPTER = "chapter"
        const val COL_VERSE = "verse"
        const val COL_ADDED = "added_at"
        const val ORDER = "$COL_TITLE COLLATE NOCASE"
    }
}
