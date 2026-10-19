package com.realsneed.biblereader.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.IBinder
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.view.Menu
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.content.res.Configuration
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.realsneed.biblereader.R
import com.realsneed.biblereader.audio.AudioPlayer
import com.realsneed.biblereader.audio.AudioService
import com.realsneed.biblereader.data.BibleMeta
import com.realsneed.biblereader.data.BibleRepository
import com.realsneed.biblereader.data.ReadingClock
import com.realsneed.biblereader.data.Backup
import com.realsneed.biblereader.data.BackupFile
import com.realsneed.biblereader.data.BackupFormatException
import com.realsneed.biblereader.data.BookInfo
import com.realsneed.biblereader.data.Concordance
import com.realsneed.biblereader.data.CrossReferences
import com.realsneed.biblereader.data.FolderStore
import com.realsneed.biblereader.data.ReadingPrefs
import com.realsneed.biblereader.data.Quotation
import com.realsneed.biblereader.data.ReminderPrefs
import com.realsneed.biblereader.data.StrongsRepository
import com.realsneed.biblereader.data.VoicePrefs
import com.realsneed.biblereader.data.Verse
import com.realsneed.biblereader.data.WebsterRepository
import com.realsneed.biblereader.data.VerseStore
import com.realsneed.biblereader.notify.ReminderScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The reader: every chapter of the Bible is one swipeable page.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var repository: BibleRepository
    private lateinit var strongs: StrongsRepository
    private lateinit var webster: WebsterRepository
    private lateinit var concordance: Concordance
    private lateinit var store: VerseStore
    private lateinit var folderStore: FolderStore
    private lateinit var crossReferences: CrossReferences
    private lateinit var voicePrefs: VoicePrefs
    private lateinit var readingPrefs: ReadingPrefs
    private lateinit var reminderPrefs: ReminderPrefs

    private lateinit var playerBar: View
    private lateinit var playerToggle: ImageButton
    private lateinit var playerLabel: TextView

    private var audioService: AudioService? = null
    private var audioBound = false
    private var lastAudioState: AudioPlayer.State? = null
    private var following = true
    private var audioDrivenPosition = -1

    /** Page + verse to flash once a jump has landed (-1 = none pending). */
    private var flashTargetPosition = -1
    private var flashTargetVerse = -1
    private var flashColor = 0
    private var readingVerse = -1
    private var readingPosition = -1

    /** Verse whose background is mid-flash; the tint painters leave it alone. */
    private var flashingVerse = -1
    private var flashAnimator: ValueAnimator? = null

    /** Verses picked out by long-press, painted as a block. */
    private val selectedVerses = sortedSetOf<Int>()
    private var selectionBook: BookInfo? = null
    private var selectionChapter = -1
    private lateinit var selectionBar: View
    private lateinit var selectionLabel: TextView

    private var activeVerseColor = 0
    private var selectionColor = 0
    private lateinit var pagerAdapter: ChapterPagerAdapter
    private lateinit var pager: ViewPager2
    private lateinit var titleView: TextView
    private lateinit var prefs: SharedPreferences

    private val executor = Executors.newSingleThreadExecutor()

    /**
     * The dictionaries and the concordance load on their own thread. They are 8 MB
     * of text and 349,000 lookups, and sharing one thread with the reader meant the
     * first page - and every verse menu - waited behind them. That was the "slow to
     * start" report.
     */
    private val indexer = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    /** Verse to scroll to once the target page is bound (-1 = none). */
    private var pendingVerse = -1

    /** Returns the verse chosen on the Notes & highlights screen. */
    private val jumpLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode != Activity.RESULT_OK || data == null) return@registerForActivityResult
        val book = BibleMeta.byAssetIndex(data.getIntExtra(MarksActivity.EXTRA_BOOK, 1))
            ?: return@registerForActivityResult
        goTo(
            book,
            data.getIntExtra(MarksActivity.EXTRA_CHAPTER, 1),
            data.getIntExtra(MarksActivity.EXTRA_VERSE, 1)
        )
    }

    /** Where a backup is written: the system picker chooses the file. */
    private val backupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) writeBackup(uri) }

    /** Which file a restore reads. */
    private val restoreLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) readBackup(uri) }

    /**
     * Android 13 asks before an app may post anything. The reminder is only armed
     * once the answer is known, so it cannot be switched on and silently do nothing.
     */
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, R.string.reminder_notifications_denied, Toast.LENGTH_LONG).show()
        }
        ReminderScheduler.schedule(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        repository = BibleRepository.get(assets)
        strongs = StrongsRepository.get(assets, repository)
        webster = WebsterRepository.get(assets)
        concordance = Concordance.get(assets)
        // Started once the first page has had a moment to draw, on its own thread,
        // so that holding a word is instant while the reader opens as fast as it
        // did before any of this existed. The concordance is left until a
        // definition card actually asks for it - it is the most expensive of the
        // three, and most launches never need it.
        handler.postDelayed({
            indexer.execute {
                // The quotation list is small and makes the first page's underlines
                // appear at once; the dictionaries are what take seconds.
                runCatching { crossReferences.load() }
                handler.post { pagerAdapter.restyleVisiblePages() }
                strongs.warmUp()
                webster.warmUp()
            }
        }, WARM_UP_DELAY_MS)
        store = VerseStore(this)
        folderStore = FolderStore(store)
        crossReferences = CrossReferences(this)
        voicePrefs = VoicePrefs(this)
        readingPrefs = ReadingPrefs(this)
        reminderPrefs = ReminderPrefs(this)
        // Re-set on every launch: it costs one alarm slot and means a reminder that
        // was switched on survives an update, a force-stop or a cleared cache.
        ReminderScheduler.schedule(this)

        pager = findViewById(R.id.pager)
        titleView = findViewById(R.id.title)

        pagerAdapter = ChapterPagerAdapter(
            repository = repository,
            store = store,
            quotations = crossReferences,
            style = currentVerseStyle(),
            onVerseTap = { book, chapter, verse, offset -> onVerseTapped(book, chapter, verse, offset) },
            onVerseLongPress = { book, chapter, verse, offset -> onVerseHeld(book, chapter, verse, offset) }
        )
        pager.adapter = pagerAdapter
        pager.setBackgroundColor(
            ReadingThemes.resolve(this, readingPrefs.background, isDarkTheme()).background
        )
        pager.offscreenPageLimit = 1
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                // A page change the reader made themselves (swipe, picker, notes)
                // means stop dragging them back to whatever is being read aloud.
                if (position != audioDrivenPosition) stopFollowing()
                audioDrivenPosition = -1
                applyPosition(position)
            }
        })

        titleView.setOnClickListener { showPicker(focusSearch = false) }
        findViewById<ImageButton>(R.id.historyButton).setOnClickListener {
            jumpLauncher.launch(Intent(this, HistoryActivity::class.java))
        }
        findViewById<ImageButton>(R.id.searchRef).setOnClickListener { showPicker(focusSearch = true) }
        findViewById<ImageButton>(R.id.moreActions).setOnClickListener { anchorView ->
            showMoreMenu(anchorView)
        }

        playerBar = findViewById(R.id.playerBar)
        playerToggle = findViewById(R.id.playerToggle)
        playerLabel = findViewById(R.id.playerLabel)
        activeVerseColor = ContextCompat.getColor(this, R.color.active_verse)
        flashColor = ContextCompat.getColor(this, R.color.jump_flash)
        selectionColor = ContextCompat.getColor(this, R.color.verse_selection)
        playerToggle.setOnClickListener { audioService?.toggle() }
        findViewById<ImageButton>(R.id.playerNext).setOnClickListener { audioService?.next() }
        findViewById<ImageButton>(R.id.playerPrevious).setOnClickListener { audioService?.previous() }
        findViewById<ImageButton>(R.id.playerVoice).setOnClickListener { showVoicePicker() }
        findViewById<ImageButton>(R.id.playerClose).setOnClickListener {
            audioService?.stopPlayback()
        }
        playerLabel.setOnClickListener { followReading() }

        selectionBar = findViewById(R.id.selectionBar)
        selectionLabel = findViewById(R.id.selectionLabel)
        findViewById<TextView>(R.id.selectionEdit).setOnClickListener {
            val book = selectionBook ?: return@setOnClickListener
            showVerseSheet(book, selectionChapter, selectedVerses.toList())
        }
        findViewById<ImageButton>(R.id.selectionCancel).setOnClickListener { exitSelection() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (selectedVerses.isNotEmpty()) exitSelection() else finish()
            }
        })

        val start = prefs.getInt(KEY_POSITION, 0).coerceIn(0, BibleMeta.TOTAL_CHAPTERS - 1)
        pager.setCurrentItem(start, false)
        applyPosition(start)
    }

    override fun onStart() {
        super.onStart()
        bindAudio()
    }

    override fun onStop() {
        audioService?.setListener(null)
        if (audioBound) {
            unbindService(audioConnection)
            audioBound = false
        }
        audioService = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        ReadingClock.resume()
    }

    override fun onPause() {
        ReadingClock.pause(this)
        super.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
        indexer.shutdown()
    }

    // ------------------------------------------------------------------ paging

    private fun applyPosition(position: Int) {
        val (book, chapterNumber) = BibleMeta.chapterAt(position)
        titleView.text = getString(R.string.chapter_title, book.name, chapterNumber)
        prefs.edit().putInt(KEY_POSITION, position).apply()
        // Where the reader turns to goes into history; a jump records its own
        // verse, so it is not also logged as the chapter's first verse.
        if (pendingVerse <= 0) remember(book.id, chapterNumber, 1)
        prewarm(position)
        if (pendingVerse > 0) pager.post { scrollToPendingVerse() }
        if (readingVerse > 0 || selectedVerses.isNotEmpty()) pager.post { paintVerseTints() }
        if (flashTargetPosition >= 0) pager.post { tryFlash() }
    }

    /** Parses the current book (and a neighbour near a book edge) off the UI thread. */
    private fun prewarm(position: Int) {
        executor.execute {
            val (book, chapterNumber) = BibleMeta.chapterAt(position)
            repository.chapters(book)
            val index = BibleMeta.positionOf(book.assetIndex)
            if (chapterNumber <= 2) {
                BibleMeta.BOOKS.getOrNull(index - 1)?.let { repository.chapters(it) }
            }
            if (chapterNumber >= BibleMeta.chapterCount(book) - 1) {
                BibleMeta.BOOKS.getOrNull(index + 1)?.let { repository.chapters(it) }
            }
        }
    }

    // ------------------------------------------------------------ verse marking

    /**
     * A plain tap opens the Verse menu - unless a block is being picked out, or
     * the tap landed on a footnote marker, which opens that note instead.
     */
    private fun onVerseTapped(book: BookInfo, chapterNumber: Int, verseNumber: Int, offset: Int) {
        // While a block is being picked out, every tap adds or removes a verse.
        if (offset < 0 || !readingPrefs.showFootnotes || selectedVerses.isNotEmpty()) {
            verseTapped(book, chapterNumber, verseNumber)
            return
        }
        executor.execute {
            val verse = repository.chapter(book, chapterNumber)
                ?.verses?.firstOrNull { it.number == verseNumber }
            val marks = if (verse == null) IntArray(0)
            else ChapterPagerAdapter.footnoteMarkerOffsets(verse)
            // A marker is one character wide, so the tap just after it counts too.
            val hit = marks.any { offset == it || offset == it + 1 }
            handler.post {
                if (hit && verse != null) {
                    openFootnotes(book, chapterNumber, verse)
                } else {
                    verseTapped(book, chapterNumber, verseNumber)
                }
            }
        }
    }

    private fun verseTapped(book: BookInfo, chapterNumber: Int, verseNumber: Int) {
        if (selectedVerses.isEmpty()) {
            showVerseSheet(book, chapterNumber, listOf(verseNumber))
        } else {
            toggleSelection(book, chapterNumber, verseNumber)
        }
    }

    /** The KJV's own notes on one verse, for a tap on its marker. */
    private fun openFootnotes(book: BookInfo, chapterNumber: Int, verse: Verse) {
        if (verse.footnotes.isEmpty()) return
        FootnotesSheet(
            context = this,
            book = book,
            chapter = chapterNumber,
            verse = verse,
            darkTheme = isDarkTheme()
        ).show()
    }

    /**
     * Holding a word looks it up. Every word now opens the same card: the Hebrew
     * or Greek behind it when there is one, and a plain explanation of why there
     * is not when there isn't - never a jump to somewhere else.
     */
    private fun onVerseHeld(book: BookInfo, chapterNumber: Int, verseNumber: Int, offset: Int) {
        if (offset < 0) return
        executor.execute {
            val verse = repository.chapter(book, chapterNumber)
                ?.verses?.firstOrNull { it.number == verseNumber } ?: return@execute
            val index = StrongsRepository.wordIndexAt(verse.text, offset)
            if (index < 0) return@execute
            val words = strongs.wordsFor(book, chapterNumber)[verseNumber].orEmpty()
            val word = words.firstOrNull { it.covers(index) }
            val label = if (word != null) word.label else StrongsRepository.wordAt(verse.text, index)
            handler.post {
                StrongsSheet(
                    context = this,
                    strongs = strongs,
                    webster = webster,
                    concordance = concordance,
                    indexer = indexer,
                    id = word?.strongs,
                    label = label,
                    reference = getString(
                        R.string.reference_title, book.name, chapterNumber, verseNumber
                    ),
                    executor = executor,
                    onWordStudy = { openWordStudy(book, chapterNumber, listOf(verseNumber)) },
                    onConcordance = { number, wordLabel -> openConcordance(number, wordLabel) }
                ).show()
            }
        }
    }

    /** Every verse that uses one Hebrew or Greek word. */
    private fun openConcordance(number: String, wordLabel: String) {
        // The index is built on the index thread, so this never holds the reader up.
        indexer.execute {
            concordance.warmUp()
            val packed = concordance.references(number) ?: return@execute
            if (packed.isEmpty()) return@execute
            handler.post {
                ConcordanceSheet(
                    context = this,
                    repository = repository,
                    concordance = concordance,
                    strongs = strongs,
                    executor = executor,
                    body = handler,
                    heading = wordLabel,
                    reference = number,
                    packed = packed,
                    onJump = { book, chapter, verse -> goTo(book, chapter, verse) }
                ).show()
            }
        }
    }

    /** Word study for one verse, or for every verse of a block. */
    private fun openWordStudy(book: BookInfo, chapterNumber: Int, verseNumbers: List<Int>) {
        executor.execute {
            val chapter = repository.chapter(book, chapterNumber) ?: return@execute
            val wanted = verseNumbers.sorted()
            val verses = wanted.mapNotNull { number ->
                chapter.verses.firstOrNull { it.number == number }
            }
            if (verses.isEmpty()) return@execute
            // The lexicon is 1.4 MB: read it while the sheet is still arriving so
            // the first word tapped has its definition ready.
            strongs.warmUp()
            val words = strongs.wordsFor(book, chapterNumber)
            handler.post {
                WordStudySheet(
                    context = this,
                    strongs = strongs,
                    webster = webster,
                    concordance = concordance,
                    indexer = indexer,
                    executor = executor,
                    book = book,
                    chapter = chapterNumber,
                    verses = verses,
                    words = words,
                    onConcordance = { number, wordLabel -> openConcordance(number, wordLabel) }
                ).show()
            }
        }
    }

    /** Starts a block from the Verse menu, with the tapped verse as its first. */
    private fun startSelection(book: BookInfo, chapterNumber: Int, verse: Int) {
        exitSelection()
        toggleSelection(book, chapterNumber, verse)
        Toast.makeText(this, R.string.selection_hint, Toast.LENGTH_SHORT).show()
    }

    private fun toggleSelection(book: BookInfo, chapterNumber: Int, verse: Int) {
        // A block lives inside one chapter - moving elsewhere starts a new one.
        if (selectedVerses.isNotEmpty() &&
            (selectionBook?.assetIndex != book.assetIndex || selectionChapter != chapterNumber)
        ) {
            selectedVerses.clear()
        }
        if (!selectedVerses.add(verse)) selectedVerses.remove(verse)
        selectionBook = book
        selectionChapter = chapterNumber
        if (selectedVerses.isEmpty()) {
            exitSelection()
        } else {
            selectionBar.visibility = View.VISIBLE
            selectionLabel.text = getResources().getQuantityString(
                R.plurals.selection_count, selectedVerses.size, selectedVerses.size
            ) + "  ·  " + selectionReference()
            paintVerseTints()
        }
    }

    /** "3:15-16" for a run, "3:15,17" for gaps. */
    private fun selectionReference(): String {
        val numbers = selectedVerses.toList()
        if (numbers.isEmpty()) return ""
        val parts = ArrayList<String>()
        var start = numbers.first()
        var previous = start
        for (number in numbers.drop(1)) {
            if (number == previous + 1) {
                previous = number
                continue
            }
            parts.add(if (start == previous) "$start" else "$start-$previous")
            start = number
            previous = number
        }
        parts.add(if (start == previous) "$start" else "$start-$previous")
        return "$selectionChapter:" + parts.joinToString(",")
    }

    private fun exitSelection() {
        selectedVerses.clear()
        selectionBook = null
        selectionChapter = -1
        selectionBar.visibility = View.GONE
        paintVerseTints()
    }

    private fun showVerseSheet(book: BookInfo, chapterNumber: Int, verseNumbers: List<Int>) {
        executor.execute {
            val chapter = repository.chapter(book, chapterNumber)
            val wanted = verseNumbers.sorted()
            val verses = wanted.mapNotNull { number ->
                chapter?.verses?.firstOrNull { it.number == number }
            }
            if (verses.isEmpty()) return@execute
            handler.post {
                VerseSheet(
                    context = this,
                    store = store,
                    folderStore = folderStore,
                    book = book,
                    chapter = chapterNumber,
                    verses = verses,
                    darkTheme = isDarkTheme(),
                    showFootnotes = readingPrefs.showFootnotes,
                    crossReferences = crossReferences,
                    onChanged = { wanted.forEach { refreshVerse(book, chapterNumber, it) } },
                    onDismissed = { exitSelection() },
                    onWordStudy = { openWordStudy(book, chapterNumber, wanted) },
                    onSelectVerses = { startSelection(book, chapterNumber, wanted.first()) },
                    onQuotation = { showQuotation(it) }
                ).show()
            }
        }
    }

    /** Writes one visit to history without holding up the reader. */
    private fun remember(bookId: String, chapterNumber: Int, verse: Int) {
        executor.execute { store.recordVisit(bookId, chapterNumber, verse) }
    }

    /** Re-renders one verse of the visible page without disturbing scroll. */
    private fun refreshVerse(book: BookInfo, chapterNumber: Int, verseNumber: Int) {
        val position = BibleMeta.absoluteIndex(book, chapterNumber)
        if (position != pager.currentItem) return
        val recycler = pager.getChildAt(0) as? RecyclerView ?: return
        val holder = recycler.findViewHolderForAdapterPosition(position)
            as? ChapterPagerAdapter.PageHolder ?: return
        pagerAdapter.refreshVerse(holder, position, verseNumber)
    }

    // ------------------------------------------------------------------ picker

    private fun showPicker(focusSearch: Boolean) {
        val (book, chapterNumber) = BibleMeta.chapterAt(pager.currentItem)
        PassagePicker(this, repository, executor, handler) { chosenBook, chosenChapter, verse ->
            goTo(chosenBook, chosenChapter, verse)
        }.show(book, chapterNumber, focusSearch)
    }

    private fun goTo(book: BookInfo, chapterNumber: Int, verse: Int) {
        val absolute = BibleMeta.absoluteIndex(book, chapterNumber)
        pendingVerse = verse
        flashTargetPosition = absolute
        flashTargetVerse = verse
        remember(book.id, chapterNumber, verse)
        if (absolute == pager.currentItem) {
            pager.post {
                scrollToPendingVerse()
                tryFlash()
            }
        } else {
            pager.setCurrentItem(absolute, false)
        }
    }

    /**
     * Scrolls to [pendingVerse] once its page has been bound. If the verse does
     * not exist in that chapter, falls back to the last verse of the page.
     */
    private fun scrollToPendingVerse(attempt: Int = 0) {
        val verse = pendingVerse
        val recycler = pager.getChildAt(0) as? RecyclerView
        val holder = recycler?.findViewHolderForAdapterPosition(pager.currentItem)
            as? ChapterPagerAdapter.PageHolder
        if (holder == null || holder.verseViews.size() == 0) {
            retryOrGiveUp(attempt) { scrollToPendingVerse(it) }
            return
        }
        if (verse > 1) {
            val view = holder.verseViews.get(verse)
                ?: holder.verseViews.get(holder.verseViews.keyAt(holder.verseViews.size() - 1))
            holder.scroll.scrollTo(0, view.top)
        }
        pendingVerse = -1
    }

    /**
     * Tints the landed-on verse and fades it out, so a jump is unmistakable - the
     * app is quick enough that otherwise you cannot tell you have moved at all.
     *
     * Runs on its own retry loop against the destination page, deliberately
     * independent of the scroll above: sharing that loop meant the flash was
     * skipped whenever the scroll gave up.
     */
    private fun tryFlash(attempt: Int = 0) {
        val position = flashTargetPosition
        val verse = flashTargetVerse
        if (position < 0 || verse <= 0) return
        val recycler = pager.getChildAt(0) as? RecyclerView
        val holder = recycler?.findViewHolderForAdapterPosition(position)
            as? ChapterPagerAdapter.PageHolder
        val view = if (pager.currentItem == position) {
            holder?.verseViews?.get(verse) as? TextView
        } else {
            null
        }
        if (view == null) {
            if (attempt < FLASH_RETRY_LIMIT) {
                pager.postDelayed({ tryFlash(attempt + 1) }, RETRY_DELAY_MS)
            } else {
                flashTargetPosition = -1
                flashTargetVerse = -1
            }
            return
        }
        flashTargetPosition = -1
        flashTargetVerse = -1
        fadeFlash(view, verse)
    }

    /**
     * Tints the view's **background** and fades it out. A background is drawn with
     * the view's bounds at draw time, which is the mechanism already proven by the
     * "now reading" tint - a View overlay here never rendered at all.
     *
     * [flashingVerse] tells the tint painters to skip this verse until the fade
     * finishes, and the fade re-paints on completion so the right tint returns.
     */
    private fun fadeFlash(view: TextView, verse: Int) {
        flashAnimator?.cancel()
        flashingVerse = verse
        val tint = ColorDrawable(flashColor)
        view.background = tint
        flashAnimator = ValueAnimator.ofInt(255, 0).apply {
            duration = FLASH_DURATION_MS
            interpolator = DecelerateInterpolator()
            addUpdateListener { tint.alpha = it.animatedValue as Int }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    flashingVerse = -1
                    view.background = null
                    paintVerseTints()
                }
            })
            start()
        }
    }

    private fun retryOrGiveUp(attempt: Int, retry: (Int) -> Unit) {
        if (attempt < RETRY_LIMIT) {
            pager.postDelayed({ retry(attempt + 1) }, RETRY_DELAY_MS)
        } else {
            pendingVerse = -1
        }
    }

    // ------------------------------------------------------------------- audio

    private val audioConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = (binder as? AudioService.LocalBinder)?.service()
            audioService = service
            audioBound = service != null
            service?.setListener { state -> onAudioState(state) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            audioService = null
            audioBound = false
        }
    }

    private fun bindAudio() {
        if (!audioBound) {
            bindService(Intent(this, AudioService::class.java), audioConnection, Context.BIND_AUTO_CREATE)
        }
    }

    /** Follows the reading: turns the page, scrolls, and tints the live verse. */
    private fun onAudioState(state: AudioPlayer.State) {
        lastAudioState = state
        playerBar.visibility = if (state.active) View.VISIBLE else View.GONE
        playerToggle.setImageResource(
            if (state.playing) R.drawable.ic_pause else R.drawable.ic_play
        )
        playerLabel.text = if (state.bookName != null) {
            getString(R.string.reference_title, state.bookName, state.chapter, state.verse)
        } else {
            ""
        }
        val tracking = state.active && following
        setFollowHint(state.active && !following)
        if (!state.active) {
            readingVerse = -1
            readingPosition = -1
            paintVerseTints()
            return
        }
        if (!tracking) {
            readingVerse = -1
            readingPosition = -1
            paintVerseTints()
            return
        }
        val book = state.bookId?.let { BibleMeta.byId(it) } ?: return
        val position = BibleMeta.absoluteIndex(book, state.chapter)
        readingVerse = state.verse
        readingPosition = position
        if (position != pager.currentItem) {
            pendingVerse = state.verse
            audioDrivenPosition = position
            pager.setCurrentItem(position, true)
        } else {
            paintVerseTints()
        }
    }

    /** The reader has moved away from the reading - stop pulling them back. */
    private fun stopFollowing() {
        if (!following) return
        following = false
        readingVerse = -1
        readingPosition = -1
        setFollowHint(lastAudioState?.active == true)
        paintVerseTints()
    }

    /** Tapping the reference in the player bar rejoins the reading. */
    private fun followReading() {
        val state = lastAudioState ?: return
        following = true
        setFollowHint(false)
        if (!state.active) return
        val book = state.bookId?.let { BibleMeta.byId(it) } ?: return
        readingVerse = state.verse
        readingPosition = BibleMeta.absoluteIndex(book, state.chapter)
        pendingVerse = state.verse
        audioDrivenPosition = readingPosition
        pager.setCurrentItem(audioDrivenPosition, true)
    }

    private fun setFollowHint(show: Boolean) {
        val icon = if (show) ContextCompat.getDrawable(this, R.drawable.ic_follow)?.apply {
            mutate().setTint(Color.WHITE)
            setBounds(0, 0, dp(16), dp(16))
        } else {
            null
        }
        playerLabel.setCompoundDrawables(icon, null, null, null)
        playerLabel.compoundDrawablePadding = dp(8)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun startReading() {
        val (book, chapterNumber) = BibleMeta.chapterAt(pager.currentItem)
        // Browsing earlier must not leave the pin showing before playback begins.
        following = true
        setFollowHint(false)
        bindAudio()
        ContextCompat.startForegroundService(
            this,
            AudioService.startIntent(this, book.assetIndex, chapterNumber, 1)
        )
    }

    private fun showVoicePicker() {
        bindAudio()
        VoicePickerSheet(
            context = this,
            prefs = voicePrefs,
            onVoiceChanged = { audioService?.applyVoiceChange() },
            onPreview = { sample -> audioService?.previewVoice(sample) }
        ).show()
    }

    /**
     * Paints every verse tint on the visible page from scratch.
     *
     * Selection beats the "now reading" tint, and whatever is mid-flash is left
     * untouched so its fade is not cut short. Callers use this on page changes,
     * on selection changes and when reading settings change.
     */
    private fun paintVerseTints(attempt: Int = 0) {
        val recycler = pager.getChildAt(0) as? RecyclerView
        val holder = recycler?.findViewHolderForAdapterPosition(pager.currentItem)
            as? ChapterPagerAdapter.PageHolder
        if (holder == null || holder.verseViews.size() == 0) {
            if (attempt < RETRY_LIMIT) {
                pager.postDelayed({ paintVerseTints(attempt + 1) }, RETRY_DELAY_MS)
            }
            return
        }
        val readingHere = if (pager.currentItem == readingPosition) readingVerse else -1
        for (i in 0 until holder.verseViews.size()) {
            val view = holder.verseViews.valueAt(i) as? TextView ?: continue
            val number = holder.verseViews.keyAt(i)
            if (number == flashingVerse) continue
            view.background = when {
                selectedVerses.contains(number) -> ColorDrawable(selectionColor)
                number == readingHere -> ColorDrawable(activeVerseColor)
                else -> null
            }
        }
    }

    // -------------------------------------------------------- reading settings

    private fun currentVerseStyle(): VerseStyle {
        val theme = ReadingThemes.resolve(this, readingPrefs.background, isDarkTheme())
        return VerseStyle(
            textSizeSp = readingPrefs.textSizeSp,
            lineSpacing = readingPrefs.lineSpacing,
            serif = readingPrefs.serif,
            textColor = theme.text,
            numberColor = theme.number,
            noteColor = theme.note,
            darkTheme = theme.dark,
            showItalics = readingPrefs.showItalics,
            showFootnotes = readingPrefs.showFootnotes,
            showQuotations = readingPrefs.showQuotations,
            quotationColor = ContextCompat.getColor(this, R.color.quotation_underline)
        )
    }

    /** Re-paints the reader in place - no adapter swap, so scroll is untouched. */
    private fun applyReadingStyle() {
        val theme = ReadingThemes.resolve(this, readingPrefs.background, isDarkTheme())
        pager.setBackgroundColor(theme.background)
        pagerAdapter.style = currentVerseStyle()
        pagerAdapter.restyleVisiblePages()
        paintVerseTints()
    }

    private fun showReadingSettings() {
        ReadingSettingsSheet(
            this,
            readingPrefs,
            isDarkTheme(),
            { applyReadingStyle() },
            crossReferences.count
        ).show()
    }

    // ----------------------------------------------------------------- reminder

    private fun showReminder() {
        ReminderSheet(this, reminderPrefs) { applyReminder() }.show()
    }

    /** Arms or clears the alarm, asking for the notification permission if needed. */
    private fun applyReminder() {
        if (reminderPrefs.enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val allowed = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!allowed) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        ReminderScheduler.schedule(this)
        if (reminderPrefs.enabled) {
            Toast.makeText(
                this,
                getString(R.string.reminder_set, reminderPrefs.timeLabel()),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // --------------------------------------------------------------- quotations

    /**
     * A tap on an underlined phrase: show the verse it quotes, in full, with the
     * offer to go there. The verse is read on the reader's thread, so the tap does
     * not stall the page.
     */
    private fun showQuotation(quotation: Quotation) {
        val book = BibleMeta.byId(quotation.sourceBook) ?: return
        val chapter = quotation.sourceChapter
        val verse = quotation.sourceVerse
        executor.execute {
            val text = repository.chapter(book, chapter)
                ?.verses?.firstOrNull { it.number == verse }?.text
            handler.post {
                if (isFinishing || text == null) return@post
                AlertDialog.Builder(this)
                    .setTitle(quotation.reference)
                    .setMessage(text)
                    .setPositiveButton(R.string.open_reference) { _, _ -> goTo(book, chapter, verse) }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }
    }

    // ------------------------------------------------------------------- backup

    /** Asks the system picker where to put a backup, then writes one there. */
    private fun startBackup() {
        val stamp = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        backupLauncher.launch(getString(R.string.backup_name, stamp))
    }

    /** Writes every note, highlight, folder and visit to the chosen file. */
    private fun writeBackup(uri: Uri) {
        executor.execute {
            val backup = runCatching { BackupFile.snapshot(store) }.getOrNull()
            var saved = false
            if (backup != null) {
                saved = runCatching {
                    val text = BackupFile.toJson(backup)
                    contentResolver.openOutputStream(uri)
                        ?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                        ?: error("no stream for $uri")
                }.isSuccess
            }
            val message = when {
                backup == null || !saved -> getString(R.string.backup_failed)
                backup.isEmpty -> getString(R.string.backup_nothing)
                else -> getString(
                    R.string.backup_done,
                    backup.highlights.size, backup.notes.size, backup.folders.size
                )
            }
            handler.post { Toast.makeText(this, message, Toast.LENGTH_LONG).show() }
        }
    }

    /** Reads a chosen file, then asks whether to merge it or replace everything. */
    private fun readBackup(uri: Uri) {
        executor.execute {
            val result = runCatching {
                val text = contentResolver.openInputStream(uri)?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                } ?: throw BackupFormatException(getString(R.string.restore_failed))
                BackupFile.parse(text)
            }
            handler.post {
                result.onSuccess { backup -> confirmRestore(backup) }
                    .onFailure { error ->
                        Toast.makeText(
                            this,
                            error.message ?: getString(R.string.restore_failed),
                            Toast.LENGTH_LONG
                        ).show()
                    }
            }
        }
    }

    private fun confirmRestore(backup: Backup) {
        if (backup.isEmpty) {
            Toast.makeText(this, R.string.restore_nothing, Toast.LENGTH_LONG).show()
            return
        }
        val message = getString(
            R.string.restore_summary,
            backup.highlights.size, backup.notes.size, backup.folders.size, backup.visits.size
        ) + "\n\n" + getString(R.string.restore_merge_hint) + "\n" +
            getString(R.string.restore_replace_hint)
        AlertDialog.Builder(this)
            .setTitle(R.string.restore_title)
            .setMessage(message)
            .setPositiveButton(R.string.restore_merge) { _, _ -> applyRestore(backup, true) }
            .setNeutralButton(R.string.restore_replace) { _, _ -> applyRestore(backup, false) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyRestore(backup: Backup, merge: Boolean) {
        executor.execute {
            val report = runCatching { BackupFile.restore(store, backup, merge) }.getOrNull()
            handler.post {
                if (report == null) {
                    Toast.makeText(this, R.string.restore_failed, Toast.LENGTH_LONG).show()
                    return@post
                }
                Toast.makeText(
                    this,
                    getString(
                        R.string.restore_done,
                        report.highlights, report.notes, report.folders
                    ),
                    Toast.LENGTH_LONG
                ).show()
                // Redraw the visible chapter, so new highlights and note marks show now.
                pagerAdapter.restyleVisiblePages()
                pager.post { paintVerseTints() }
            }
        }
    }

    private fun showMoreMenu(anchorView: View) {
        val popup = PopupMenu(this, anchorView)
        val menu = popup.menu
        menu.add(GROUP_LIBRARY, MENU_SEARCH_WORDS, 0, R.string.search_words)
        menu.add(GROUP_LIBRARY, MENU_MARKS, 1, R.string.notes_and_highlights)
        menu.add(GROUP_LIBRARY, MENU_FOLDERS, 2, R.string.folders)
        menu.add(GROUP_READING, MENU_READ_ALOUD, 2, R.string.read_aloud)
        menu.add(GROUP_READING, MENU_VOICE, 3, R.string.reading_voice)
        menu.add(GROUP_READING, MENU_READING_SETTINGS, 5, R.string.reading_settings)
        // The reminder and the reader's data sit together, below the second line:
        // one is a routine, the other the files it is built on, and neither is
        // about how the page looks.
        menu.add(GROUP_DATA, MENU_REMINDER, 6, R.string.reminder_setting)
        // The reader's own data is one entry with the two directions inside it,
        // so that backing up and importing cannot be mistaken for each other.
        val data = menu.addSubMenu(GROUP_DATA, Menu.NONE, 7, R.string.backup_menu)
        data.add(Menu.NONE, MENU_BACKUP, 0, R.string.backup_action)
        data.add(Menu.NONE, MENU_RESTORE, 1, R.string.import_action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // A line between the three groups. The call arrived in Android 9;
            // older versions simply show no line.
            menu.setGroupDividerEnabled(true)
        }
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                MENU_SEARCH_WORDS -> jumpLauncher.launch(Intent(this, SearchActivity::class.java))
                MENU_MARKS -> jumpLauncher.launch(Intent(this, MarksActivity::class.java))
                MENU_FOLDERS -> jumpLauncher.launch(Intent(this, FoldersActivity::class.java))
                MENU_READ_ALOUD -> startReading()
                MENU_VOICE -> showVoicePicker()
                MENU_READING_SETTINGS -> showReadingSettings()
                MENU_REMINDER -> showReminder()
                MENU_BACKUP -> startBackup()
                MENU_RESTORE -> restoreLauncher.launch(arrayOf("*/*"))
            }
            true
        }
        popup.show()
    }

    private fun isDarkTheme(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    private companion object {
        const val PREFS = "reader_state"
        const val KEY_POSITION = "position"
        const val MENU_MARKS = 1
        const val MENU_FOLDERS = 2
        const val MENU_READ_ALOUD = 3
        const val MENU_VOICE = 4
        const val MENU_READING_SETTINGS = 5
        const val MENU_BACKUP = 6
        const val MENU_RESTORE = 7
        const val MENU_REMINDER = 8
        const val MENU_SEARCH_WORDS = 10
        const val GROUP_LIBRARY = 1
        const val GROUP_READING = 2
        const val GROUP_DATA = 3
        const val RETRY_LIMIT = 4
        const val FLASH_DURATION_MS = 1200L
        const val FLASH_RETRY_LIMIT = 8
        const val RETRY_DELAY_MS = 80L
        const val WARM_UP_DELAY_MS = 600L
    }
}
