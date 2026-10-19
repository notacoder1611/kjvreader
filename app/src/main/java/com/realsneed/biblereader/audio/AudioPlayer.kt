package com.realsneed.biblereader.audio

import com.realsneed.biblereader.data.BibleMeta
import com.realsneed.biblereader.data.BibleRepository
import com.realsneed.biblereader.data.BookInfo

/**
 * Reads the Bible aloud, one verse at a time.
 *
 * Keeps a single moving cursor (book/chapter/verse), asks the [VerseSpeaker] for
 * one verse, and on completion steps to the next verse - rolling into the next
 * chapter and then the next book. A monotonic [token] invalidates completion
 * callbacks from utterances that have already been superseded, which is what
 * keeps rapid next/previous taps from double-advancing.
 */
class AudioPlayer(
    private val repository: BibleRepository,
    private val speaker: VerseSpeaker,
    private val onChanged: (State) -> Unit
) {

    data class State(
        val active: Boolean,
        val playing: Boolean,
        val bookId: String?,
        val bookName: String?,
        val chapter: Int,
        val verse: Int
    )

    private var book: BookInfo? = null
    private var chapter = 1
    private var verse = 1
    private var playing = false
    private var prepared = false
    private var token = 0

    fun currentState(): State =
        State(book != null, playing, book?.id, book?.name, chapter, verse)

    fun start(target: BookInfo, targetChapter: Int, targetVerse: Int) {
        book = target
        chapter = targetChapter
        verse = targetVerse
        play()
    }

    fun toggle() {
        if (playing) pause() else play()
    }

    fun play() {
        val target = book ?: return
        if (!prepared) {
            speaker.prepare { ok ->
                prepared = ok
                if (ok) play() else publish()
            }
            return
        }
        val text = verseText(target, chapter, verse)
        if (text == null) {
            playing = false
            publish()
            return
        }
        playing = true
        publish()
        val myToken = ++token
        speaker.speak(text) {
            if (myToken != token || !playing) return@speak
            if (stepForward()) play() else finish()
        }
    }

    fun pause() {
        token++
        playing = false
        speaker.stop()
        publish()
    }

    fun stop() {
        token++
        playing = false
        speaker.stop()
        book = null
        publish()
    }

    fun next() = move(1)

    fun previous() = move(-1)

    private fun move(delta: Int) {
        if (book == null) return
        val resume = playing
        token++
        speaker.stop()
        playing = false
        if (delta > 0) stepForward() else stepBackward()
        if (resume) play() else publish()
    }

    private fun finish() {
        playing = false
        publish()
    }

    /** Speaks a one-off sample so a voice can be auditioned. Leaves the cursor put. */
    fun preview(text: String) {
        token++
        playing = false
        speaker.stop()
        publish()
        if (!prepared) {
            speaker.prepare { ok ->
                prepared = ok
                if (ok) speaker.speak(text) {}
            }
            return
        }
        speaker.speak(text) {}
    }

    fun release() {
        token++
        speaker.release()
    }

    fun reconfigureSpeaker() {
        speaker.reconfigure()
        prepared = false
        if (playing) play()
    }

    // --------------------------------------------------------------- stepping

    /** @return false when the end of the Bible is reached. */
    private fun stepForward(): Boolean {
        val current = book ?: return false
        val lastVerse = repository.chapter(current, chapter)?.verses?.lastOrNull()?.number
            ?: return false
        if (verse < lastVerse) {
            verse++
            return true
        }
        if (chapter < BibleMeta.chapterCount(current)) {
            chapter++
            verse = 1
            return true
        }
        val next = BibleMeta.BOOKS.getOrNull(BibleMeta.positionOf(current.assetIndex) + 1)
            ?: return false
        book = next
        chapter = 1
        verse = 1
        return true
    }

    /** @return false when the start of the Bible is reached. */
    private fun stepBackward(): Boolean {
        val current = book ?: return false
        if (verse > 1) {
            verse--
            return true
        }
        if (chapter > 1) {
            chapter--
            verse = lastVerseOf(current, chapter)
            return true
        }
        val previous = BibleMeta.BOOKS.getOrNull(BibleMeta.positionOf(current.assetIndex) - 1)
            ?: return false
        book = previous
        chapter = BibleMeta.chapterCount(previous)
        verse = lastVerseOf(previous, chapter)
        return true
    }

    private fun lastVerseOf(book: BookInfo, chapter: Int): Int =
        repository.chapter(book, chapter)?.verses?.lastOrNull()?.number ?: 1

    private fun verseText(book: BookInfo, chapter: Int, verse: Int): String? =
        repository.chapter(book, chapter)?.verses?.firstOrNull { it.number == verse }?.text

    private fun publish() {
        val current = book
        onChanged(
            State(
                active = current != null,
                playing = playing,
                bookId = current?.id,
                bookName = current?.name,
                chapter = chapter,
                verse = verse
            )
        )
    }
}
