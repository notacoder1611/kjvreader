# KJV Reader

A clean, zero-bloat offline Bible reader for Android.

Rebuilt from scratch in Kotlin around the public-domain **King James Version** text
that shipped inside the extracted `bijbel-v8.2` APK. The old APK is only a data
source and is left untouched.

## Design goals

* **Zero bloat** - no Firebase, no ads, no analytics, no tracking.
* **Hyper efficient** - classic Android Views (no Compose), no OkHttp.
  Dependencies: `appcompat`, `recyclerview`, `viewpager2` only.
* **Runs on old phones** - `minSdk 21` (Android 5.0). Target device: Galaxy S9.
* **Offline first** - all text is bundled in `assets/usfm/kjv`.

## Data

`app/src/main/assets/usfm/kjv/` (18 MB: 12 MB of text from the original APK,
plus the 6.5 MB Webster's extract):

| Files | Contents |
|---|---|
| `book_1` .. `book_67` | 66 books in USFM (index 40 is unused) |
| `words_1` .. `words_67` | per-verse Strong's numbers (used by word study) |
| `lexicon` | Strong's dictionary |
| `metadata.json` | translation name / language / available books |

Totals verified by the parser: **1,189 chapters**, **31,102 verses**, 0 empty.

## Project layout

```
kjvreader/
  settings.gradle.kts, build.gradle.kts, gradle.properties
  gradlew, gradle/wrapper/                 # Gradle wrapper
  local.properties                         # SDK path (local machine only)
  app/
    build.gradle.kts, proguard-rules.pro
    src/main/AndroidManifest.xml
    src/main/assets/usfm/kjv/...           # Bible text
    src/main/java/com/realsneed/biblereader/
      data/BibleMeta.kt                    # 66-book table + chapter geometry
      data/UsfmParser.kt                   # USFM -> chapters/verses
      data/BibleRepository.kt              # asset loading + LRU cache
      data/BibleReferences.kt              # shorthand reference parser + aliases
      data/VerseStore.kt                   # SQLite highlights/notes + palette
      ui/ChapterPagerAdapter.kt            # one recycled page per chapter
      ui/PassagePicker.kt                  # stepped bottom-sheet picker + search
      ui/VerseSheet.kt                     # highlight + note action sheet
      ui/MarksActivity.kt                  # Notes & highlights screen
      ui/MainActivity.kt                   # reader screen
    src/main/res/                          # theme, layouts, icons
```

## Building

Requires **JDK 17** and **Android SDK 34**, with `ANDROID_HOME` exported (or a
`local.properties` naming `sdk.dir`). The Gradle wrapper is committed.

```bash
cd kjvreader
./gradlew assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`

### Checking on an emulator

There are no native libraries, so the same APK installs on a phone or an
emulator, whatever the ABI.

The AVD set up here is `kjv29` (Android 10, x86_64). Start it detached so it
outlives the shell that started it:

```bash
setsid nohup $ANDROID_HOME/emulator/emulator -avd kjv29 -no-window -no-audio \
  -no-boot-anim -no-snapshot -gpu swiftshader_indirect -memory 3072 -cores 4 \
  </dev/null >/tmp/emu.log 2>&1 & disown
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### On a PC

Open the `kjvreader` folder in Android Studio (it uses the wrapper), or run
`./gradlew assembleDebug`.

### Installing on the phone

Copy `app-debug.apk` to the phone and open it (allow "install unknown apps"), or:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Features implemented

**Step 1 - reader foundation**

* KJV text parsed from bundled USFM, verse numbers, chapter titles.
* Light / dark theme. Remembers the last read position.

**Step 2 - paging and the passage picker**

* **Swipe left / right to turn pages** - every chapter is one page
  (`ViewPager2` over all 1,189 chapters, recycled).
* Prev / next chevrons in the app bar still step by one chapter.
* Passage picker (bottom sheet): book list -> chapter grid -> optional verse
  grid, all as grey circular chips, 6 per row.
* A pinned **Go arrow** in the thumb zone opens the **start of the selected
  chapter**. Tapping a verse chip opens that verse. Nothing opens until then.
* Background pre-parse of the current book (and a neighbouring book at book
  edges) keeps swiping smooth.

**Step 3 - reference search**
* **Search field** at the top of the picker (also reachable with the magnifier
  in the app bar) accepting shorthand references:
  `j1:1`, `john 1:1`, `1jn2:3`, `1 jn 2:3`, `i john 1:9`, `ps23`, `psa 23:1`,
  `song of solomon 2:1`, `1cor13:4`, `first corinthians 13:4`, `deuter 6:5`,
  `rev22:21`, `john 3:16-17`.
* Roman numerals and first/second/third are understood; a unique prefix of a
  full book name also resolves. The live result is previewed above the Go arrow
  and opens on the IME Go key or the Go arrow.

**Step 4 - picker hierarchy**

* Two levels in the book list: sections, then books. The section titles are the
  only visual separator - 17sp, **regular weight** (not bold), soft burgundy
  (`@color/section_title`, #8E3B4E light / #D08D9A dark), with generous spacing.
  There are deliberately no testament headings and no divider lines.
* OT sections: PENTATEUCH, HISTORY, POETRY & WISDOM, MAJOR PROPHETS,
  MINOR PROPHETS (5 + 12 + 5 + 5 + 12 = 39 books).
* NT sections: GOSPELS, APOSTOLIC HISTORY, PAULINE EPISTLES (Romans-Philemon),
  GENERAL EPISTLES (Hebrews, James, 1-2 Peter, 1-3 John, Jude), APOCALYPTIC
  (4 + 1 + 13 + 8 + 1 = 27 books). Hebrews sits with the General Epistles and
  Paul's letters are kept together, in canonical order.
* The grouping is pure data (`OLD_TESTAMENT_SECTIONS` / `NEW_TESTAMENT_SECTIONS`
  in `PassagePicker.kt`), consumed by a `PickerRow` row model, so re-splitting
  it is a one-table edit.

**Step 5 - highlights, notes and the verse action sheet**

* **Tap any verse** to open one sheet holding *both* the colour chips and the
  note field, so a verse can be highlighted and annotated in a single visit.
* Four highlight colours (yellow / green / blue / red) plus a dash chip that
  removes the highlight. Stored as tokens, so the palette can be re-themed for
  light/dark without migrating data (`HighlightColor` in `data/VerseStore.kt`).
* The colour applies the instant a chip is tapped (the verse behind updates in
  place, without losing scroll position). The note is committed when the sheet
  goes away - by tapping the dimmed area outside it, the Done button, or Back.
* Persistence: a tiny SQLite store (`data/VerseStore.kt`, table `verse_mark`,
  keyed by translation + book + chapter + verse). A row is only created once a
  verse is touched and deleted again when both colour and note are cleared, so
  the database stays the size of what was actually marked.
* Verses with a note show a small superscript pencil marker; highlighted verses
  get a `BackgroundColorSpan` across the verse text.

**Step 6 - Notes & highlights screen**

* New screen listing every highlight and note in the Bible, grouped under
  burgundy book+chapter headers (reusing the picker's section style).
* Each row shows the verse number, a two-line snippet of the verse text, the
  note underneath in burgundy, and a dot in the highlight colour.
* Tapping a row returns to the reader and jumps straight to that verse.
* Reached from the overflow menu (the three dots in the app bar) - which is
  where Folders and Settings will live too.
* `BibleRepository` is now a process-wide singleton so both screens share one
  parsed-book cache.

**Step 7 - recursive folders for saved verses**

* A folder tree for filing verses under topics, nested to any depth
  (`Jesus > Names of God > Yahweh`).
* Folders are **titled** by the user (create / rename), and deleting a folder
  takes its whole subtree and the verses filed inside it.
* Every folder row shows recursive counts (`3 subfolders · 5 verses`), where the
  verse count includes everything nested underneath.
* Drill down with a scrollable **breadcrumb**; + creates a sub-folder at the
  level you are viewing. Rename/Delete live in each folder's overflow.
* Verses are filed from the verse action sheet: the new **Save to folder** row
  opens a chooser that lists the whole tree with indentation and toggles
  membership, so one verse can sit under several topics. Long-press a verse in
  a folder to take it back out.
* The chooser can create folders inline: tap **New folder** for a top-level
  topic, or **long-press any folder to nest a new sub-folder inside it**. Either
  way the verse being viewed is filed into the new folder straight away, so a
  brand-new topic never means leaving the verse to go and build it in Folders.
  Long-press also offers **Rename**.
* The note marker (✎) now comes from a single shared helper (`ui/VerseLine.kt`)
  used by every list that shows a verse, so it appears wherever a verse is
  presented - reader, Notes & highlights, and folder contents.
* Schema v2 adds `folder` and `folder_verse`. Purely additive, so highlights
  and notes made on v1 are preserved.

**Step 8 - the Audio Bible**

* **Read aloud** (overflow menu) starts reading from the verse you are on and
  works through the Bible verse by verse, rolling into the next chapter and
  then the next book.
* **Follow along**: the reader turns the page, scrolls to the live verse and
  tints it, so you can watch as it is spoken.
* **Keeps going with the screen off** - a media-playback foreground service with
  a notification offering play/pause and stop, and audio focus so it pauses
  when a call or another player takes over.
* A **player bar** appears at the bottom of the reader only while audio is on:
  previous verse, play/pause, next verse, the current reference, and the voice
  button.
* **Reading voice** picker: chooses the TTS **engine** (Samsung vs Google, a big
  quality difference), filters voices by **Male / Female**, lists what the device
  actually has, prefers the more natural **network** voices, and can preview a
  voice. The choice is remembered and applies instantly - if the Bible is being
  read, you hear the new voice on the next verse.
* Speech goes through a small pluggable `VerseSpeaker` interface
  (`audio/VerseSpeaker.kt`), so a different engine can be dropped in later
  without touching the player, the service or the UI.

**Step 9 - bug fixes (v0.8.1)**

* **The reading no longer steals the page.** Swiping, using the picker or
  jumping from Notes & highlights stops the auto-follow, so you are never
  yanked back to whatever is being read. The player bar shows a small pin icon
  when following is off, and tapping the reference rejoins the reading.
* **The player bar can be dismissed** - a close (X) button on the right stops
  the reading and hides the bar.
* **Tapping Stop in the notification now works.** It previously only removed
  the notification without stopping playback; stopping the player is now part
  of the same call.
* **Many notes per verse.** Schema v3 adds a `verse_note` table, so a verse can
  hold as many notes as you like instead of one. Existing notes are migrated
  across automatically and nothing is lost.
* **Delete from anywhere.** Each note in the verse sheet has its own X; the
  Notes & highlights screen now lists one row per note plus one row per
  highlight-only verse, and long-pressing a row offers *Delete note* and/or
  *Remove highlight*.
* **The note marker actually shows now.** It was a Unicode pencil (U+270E),
  which some device fonts render as nothing - it is now a drawn icon
  (`ImageSpan` over `ic_note`), so it appears in the reader, the notes screen
  and inside folders.

**Step 10 - phase A fixes (v0.8.2)**

* The note marker now sits **outside** the highlight, at the very end of the
  verse, instead of inside the coloured block.
* The Notes & highlights screen tints the **whole verse row** with its highlight
  colour rather than showing a small dot beside it (ripple feedback kept via a
  layered background).
* The "no notes on this verse yet" line is indented like the note rows.
* The follow pin only appears once you actually navigate away: the following
  flag is reset when reading starts, so browsing beforehand no longer leaves it
  stuck on.

**Step 11 - phase B: copy + note-marker fix (v0.8.3)**

* **The note marker is now a typographic dagger (U+2020), i.e. ordinary text.**
  It has been through three forms: a Unicode pencil (absent from some device
  fonts, so it vanished), an `ImageSpan` (painted at a *computed* offset rather
  than laid out, so it drifted and landed on top of the verse's first word), and
  now the dagger, which the text engine positions itself and which every Android
  font contains.
* Notes on a tinted row are the theme red again (a readability experiment in
  v0.8.2 that was wrong).
* **Copy lives in the Verse menu** (the sheet that opens on tapping a verse - the
  project-standard name for it): a standard copy glyph beside *Done*.
  **Tap** copies the verse then the reference on the next line; **long-press**
  copies just the reference. Both are plain unstyled text - no markup - for
  pasting into Discord. The reference reads `John 3:16 (KJV)`.
* There is deliberately **no drag-to-select-to-copy**: copying is a single tap.

**Step 12 - jump flash (v0.9.0)**

* Landing on a verse now **tints it and fades the tint out over 1.2s**, so a jump
  is unmistakable. The app is fast enough that otherwise you cannot tell you
  have moved.
* It fires on every deliberate jump: shorthand reference search, the passage
  picker (book/chapter/verse chips, or the Go arrow), and a tap in Notes &
  highlights or in a folder. All of those already route through `goTo`, so it
  is armed in one place.
* It deliberately does **not** fire on swiping or on the app-bar chapter arrows,
  where the page change is already obvious, nor while the audio reader is
  following along.
* Colours: `@color/jump_flash` (#3D5C6BC0 light / #4D9FA8DA dark), separate from
  the fainter `@color/active_verse` tint used for the verse being read aloud.
  When the flash finishes it restores that reading tint if the same verse.

**Step 13 - jump flash fix + reading settings (v0.10.0)**

* **Jump flash fixed.** Two faults: it painted the view's *background*, which
  `paintActiveVerse` wipes (and `stopFollowing` wipes on the first navigation
  after launch), and it was bolted onto the *scroll* retry loop, so it was
  skipped whenever the scroll gave up. It now draws into the view's **overlay**
  and runs its own retry loop against the destination page.
* **Reading settings** (overflow menu): text size 14-32sp, line spacing
  1.00-2.20, a **serif / sans** switch, and five page colours - Default (follows
  the system), Paper, Sepia, Grey and Night.
* A **live sample** sits above the controls, drawn with exactly the same values
  as the reader, so the preview is what you get.
* Applying a change repaints only the pages that are attached
  (`ChapterPagerAdapter.restyleVisiblePages`), so **scroll position is never
  lost** and the adapter is never swapped.
* `VerseStyle` (`ui/ChapterPagerAdapter.kt`) holds every drawing choice;
  `ReadingTheme` (`ui/ReadingTheme.kt`) maps a page colour to the text, verse
  number and note colours that suit it, and decides whether the dark highlight
  palette is used. Preferences live in `data/ReadingPrefs.kt`.

**Step 14 - flash fixed properly, multi-verse blocks (v0.11.0)**

* **Jump flash.** Third and final attempt. The first two both failed for the same
  reason: `ImageSpan`-style mechanisms draw at an offset the framework computes,
  and a **View overlay never rendered at all** here. It now uses the mechanism
  the app has already proven - a plain **view background** - and the cause of the
  earlier wipe-out is gone: `paintVerseTints` repaints from scratch but **skips
  the verse that is mid-flash**, and the fade re-paints when it ends.
* **Multi-verse selection.** Holding a verse in the reader starts a block; tapping
  other verses in the same chapter adds or removes them; a tinted verse and a
  selection bar show what is picked. The bar shows `2 verses · 3:15-16` and a
  **Verse menu** button. Back (or the X) clears the selection.
* The **Verse menu now accepts a block**: one colour, one note (added to every
  verse), one folder choice, all applied together. Notes are listed per verse
  only when a single verse is selected.
* **Block copy** produces one compact reference - `John 3:15-16 (KJV)` - not one
  reference per verse, so pasting a passage into Discord reads naturally.
* The folder chooser treats a folder as chosen only when **every** verse in the
  block is in it, and filing/unfiling applies to the whole block.

**Step 15 - Word study, and defining a word by holding it (v0.12.0)**

* **Hold a word** in the reader and you get the Hebrew or Greek behind it: the
  original, how it is pronounced, what it means, and the English words the KJV
  translators used for it. The word under your finger is worked out from the text
  layout, so it is the word you actually touched.
* **Hold is now define, not select.** Selecting verses moved to the Verse menu,
  where **Select verses** starts a block - tap the verses you want and they tint.
* **Word study** in the Verse menu lists every word of the verse with its Strong's
  number, in order, ready to tap. A block lists each verse in turn. Words with no
  original (`In`, `the`, and the italicised words the translators added) are left
  out, because they are not there to study - all of the data already shipped in
  the APK and had never been used.
* The lexicon running behind it is 14,057 entries, read once on a background
  thread. No download, no licence.

**The interesting part.** `words_<book>` is *not* a copy of the KJV: it omits the
words with no original (only 44% of the Bible's words have a number), joins some
phrases into one entry (`unto you` is a single entry, G5213), and carries the
Hebrew titles of the Psalms that the reader never displays. Zipping the two lists
together would have put the wrong number on thousands of words. Instead the two
are **walked together and only matched on exact equality**, so a word gets the
right number or none at all - never a wrong one - and an entry with no counterpart
here is dropped rather than shifting everything after it.

That walk was verified by taking the shipped functions, compiling them standalone
and running them over **all 31,102 verses**: the output is byte-identical to the
reference implementation, placing **99.8%** of the 349,308 entries. The residue is
the Psalms' Hebrew titles, plus a handful of places where the source text is
itself missing a space - `clean.And immediately` - which is now fixed for the
reader as well, since it was showing up as a run-on word 60 times.

**Step 16 - the Select-verses race, one card for every word, and notes on blocks (v0.12.1)**

* **Select verses was broken by a race I introduced.** `Dialog.dismiss()` does not
  call its dismiss listener there and then - the framework *posts* it to the main
  queue. So the sheet's "I was dismissed" cleanup ran one loop **after** the block
  had been created, and quietly wiped it: the verses flashed, the bar vanished and
  the next tap opened the ordinary single-verse menu. A dismissal caused by tapping
  a row no longer triggers the cleanup, so the block survives.
* **Holding a word always opens the same card.** Half the time it used to open the
  verse's word list instead, because only 44% of the KJV's words have a Hebrew or
  Greek original behind them - the other half are words the translators supplied.
  The card now says exactly that, and offers the word list as a choice.
* **A note on a block is one note.** `verse_note` gained an `end_verse`, so a note
  covering Isaiah 53:4-6 is a single row with a range rather than a copy on every
  verse. The dagger is drawn **once, at the end of the range**; the Verse menu lists
  a ranged note once, labelled `53:4-6`; and the migration folds existing duplicate
  rows back together - verified against a real SQLite database, including the case
  where another note sits *inside* the block.
* **Notes & highlights rows** lost the full-row tint (it made the list hard to
  read) and went back to a soft-edged chip in the verse's colour at the start. Runs
  of highlighted verses with no notes are now one row per block, not one per verse.

**Step 17 - Webster's 1828 and the concordance (v0.13.0)**

Holding a word now gives you the English **and** the original, in that order:

```
earth                      ← the KJV word you held
────────────────────────
WEBSTER'S 1828
The globe on which we live; the terraqueous globe...
────────────────────────
H776 · HEBREW · אֶרֶץ  ʼerets
land, earth, country... KJV: common, country, earth, field, ground...
────────────────────────
CONCORDANCE — 2,504 verses · Every occurrence →
```

* **Webster's 1828** is the dictionary written to be read beside this Bible: it
  defines `conversation` as "general course of manners; behavior", `prevent` as "to
  go before", and **quotes the KJV's own verses** in its examples. Measured against
  the KJV's 789,628 words it resolves **95.5%**, against 72.9% for Webster's 1913 -
  which turns out to have no entry at all for `and`, `to`, `that` or `he`.
* **The APK grew by 1.6 MB, not 16.** `tools/build_webster.py` reduces the 62,388
  headwords to the **8,559 the KJV actually uses** and writes them keyed by the
  KJV's own forms, so the archaic ones (`hath`, `spake`, `beginneth`) and every
  inflection are resolved at build time and the lookup on the device is a plain map
  hit. The generator reads the same USFM the app does, and the app's own `key()`
  function was compiled standalone and run over all 12,671 forms to confirm it
  agrees with the generator exactly - **8,559 forms, 754,138 words, 95.51% both
  ways.** Definitions licensed CC BY-SA 4.0, attributed on the card.
* **Between the two dictionaries, holding a word finds something 99.8% of the
  time** - 1,483 words in the whole Bible have neither, and they are almost all
  proper names (Israel, David, Jerusalem) which dictionaries do not define.
* **Concordance:** every verse using that word, from the `words_*` data that was
  already shipped. 348,732 occurrences pack into an `int` each, so "and" with its
  5,150 verses costs 20 KB rather than a list of objects. Built once in the
  background, read lazily as it scrolls, tap to jump (flash included).

**Step 18 - the KJV's own italics and footnotes (v0.14.0)**

The reader has always thrown both away. It now keeps them, and they explain
something the word study could only hint at:

* **Italic words** are the words the translators *supplied* - not in the Hebrew or
  Greek, added so the English would read. That is precisely why holding `the`,
  `was` or `is` finds no original. **21,462 spans covering 26,998 words**, shown in
  italics exactly as a printed KJV shows them, with a toggle in Reading settings.
* **Footnotes** are the translators' margin notes: `firmament` -> "Heb. expansion",
  "Heb. between the light and between the darkness". **6,959 of them**, marked in
  the text with a double dagger `‡` and listed in the Verse menu with the words
  they are about. Same toggle.
* Reading settings gained a **"KJV printer's marks"** section with Shown/Hidden for
  each, applying live like every other setting.

**Why the parser had to be rebuilt rather than patched.** The verse text is what
everything else depends on - the word study aligns Hebrew and Greek against it by
walks and exact matches. So before touching a line, the old parser was compiled
standalone and made to hash all **31,102 verses**; after the rewrite the same
hashes were produced again, proving **not one character of the Bible moved** while
italics and footnotes were picked up. Two bugs were caught that way:

* every footnote anchor came out **one character past the end** of the verse (the
  trailing space the trim removes) - all 6,959 were out of bounds until clamped;
* the italic spans could be **off by one** wherever the run-on sentence repair
  fired, fixed by repairing those before any offset is measured.

The counts then reconciled exactly against the raw USFM: 6,959 footnotes, 21,462
italic spans in verse text, and the 41 that are *not* counted are the ones sitting
on the Psalm title lines (`\d To the chief Musician, \add A Psalm\add* of David`)
that the reader never displays.

**Step 19 - four fixes from testing (v0.15.0)**

* **Webster's read as a wall of text.** The 1828 source marks every part of
  speech, every numbered sense and every scripture quotation as its own
  paragraph, and the converter had flattened all of it onto one line. The
  paragraph breaks are kept now, so an entry reads as an entry:
  `noun` / `1. General course of manners; behavior; deportment; …` /
  `Let your conversation be as becometh the gospel. Philippians 1:27.`
  Same size (1.57 MB gzipped) - the breaks come out of markup that was there all
  along.
* **Italics that cannot be missed.** `StyleSpan(Typeface.ITALIC)` only *asks* the
  current typeface for its italic face, and plenty of device fonts - including
  some Android ships as `sans-serif` and `serif` - have no italic face at all, so
  the supplied words came out looking identical to the rest. The reader now uses a
  span that asks for the italic face **and** slants the text itself, so italics
  show on every device.
* **Footnotes are now a thing you can see and touch.** The double dagger `‡` is
  bold and larger, and **tapping it opens the note** - the tap offset is matched
  against the marker positions the renderer used, so it opens the right one. They
  are still listed in the Verse menu too.
* **The reading-settings sample now demonstrates both toggles.** It shows *"2 And
  the earth **was** without form, and void. ‡"* - a real supplied word from
  Genesis 1:2 and a real footnote marker - so Shown/Hidden is visibly doing
  something instead of having to be taken on trust. The two rows also got their
  own lines with half-width buttons rather than being crammed onto one line.
* **Folders now say which book and chapter.** A folder can hold verses from
  anywhere, and the rows said only "16". They read **"John 3:16"** now.

**Step 20 - navigation down where thumbs are, and two corrections (v0.16.0)**

* **Chapter navigation moved off the top bar to the bottom of the page**, as
  soft-edged **bubble buttons with a shadow behind them** (56dp, 18dp radius,
  8dp elevation) floating over the text at the bottom corners. The chapter's last
  lines can never hide behind them - the page gained 104dp of foot room. The top
  bar keeps the title, search and menu, and looks less crowded for it.
* **The reading-settings sample was telling a small lie.** It showed a footnote
  mark after Genesis 1:2, and the real Genesis 1:2 has none - the sample is now
  **Genesis 1:16 exactly as the data has it**: *"…and the lesser light to rule the
  night: he made the stars also. ‡"*, where *he made* really is a supplied word and
  the verse really does carry a footnote. Both toggles can be watched working.
* **The concordance now shows you the word.** Every row highlights the English
  word that the Strong's number stands for, in the same soft accent the reader
  uses, so a list of 2,504 verses is no longer a guessing game.

**On "verses that should have a margin note don't" (Luke 17:36).** I checked that
one carefully, and it is not a bug in the app: in this data set Luke 17:36 *is*
verse 36, and its authenticity is recorded by **italicising the supplied word** -
`Two \add men\add* shall be in the field` - rather than by moving the verse to the
margin as a printed KJV does. So it shows italics instead of a dagger. The parser
captures 100% of the footnotes the data contains (6,959 of 6,959), so where a
note is absent, it is absent upstream.

**Step 21 - faster start, no arrows, and History (v0.17.0)**

* **Why the app had got slow.** The dictionaries (1.4 MB lexicon + 6.4 MB Webster's)
  and the concordance (349,000 lookups) were being loaded on the **same thread the
  reader uses to parse chapters**. Every launch put the first page behind 8 MB of
  dictionary work, and opening a verse menu waited for it too. They now load on
  their **own thread**, **600 ms after launch** (so the first page draws first),
  and the concordance is left until a definition card actually asks for it. The
  card asks the index thread for it and polls without blocking, so the reader is
  never held up - which is also why "occurrences" felt slow.
* **The arrows are gone**, top and bottom. Swiping turns the page and the title
  opens the picker, so they were indeed surplus.
* **History.** The overflow menu gained **History**: chapters you turn to and
  verses you jump to (from notes, folders, the concordance, search or the picker),
  newest first, with a "2 minutes ago" stamp and the verse's first lines. Tap to go
  back there - with the usual jump and flash - or long-press to remove one. It
  de-duplicates (revisiting moves an entry up rather than adding another) and keeps
  the last 200, so it stays a history and not an archive. Clearing it is one tap.
* Your doc's **[SKIP] on reading plans** is noted - not building them.
* **History moved to the top left** as a one-tap icon, per the updated list: a
  minimalist clock, drawn here rather than borrowed, so it has no licence attached
  at all (the ring is four Bézier curves, because arcs render unreliably on the
  oldest supported Android versions). It replaces the overflow-menu entry, and the
  title stays centred - its left padding is set to the width of the buttons on the
  right, so the text box is symmetric.

**Step 22 - Backing up the reader's own data (v0.18.0)**

* The overflow menu gained **Back up notes and highlights** and **Restore from a
  backup**. The first writes one JSON file through the system file picker (named
  `bible-backup-2026-10-17.json`); the second reads one back and asks whether to
  **Merge** or **Replace**.
* **Nothing is lost by a restore going wrong.** The file is plain JSON carrying a
  `format` marker and a `version`, so a stranger's file is refused with a sentence
  and a file from a *newer* version of the app is refused rather than half-read.
  The whole restore is one transaction: it lands whole or not at all.
* **Merge** never deletes. A highlight is decided by its timestamp - the newer copy
  wins, so restoring an old backup cannot undo a highlight made since. An identical
  note is not added twice. A folder is matched by its **path**, not its database id,
  so restoring onto a new phone files verses into the "Sermons" that is already
  there instead of making a second one. Visits keep the newer timestamp.
* **Replace** clears the reader's own data first, then writes the file in.
* **No permissions, no new dependencies, no FileProvider** - it is all system
  storage-access framework, so it works on Android 5 as well as 15.
* Checked before shipping, by mirroring the restore's SQL in SQLite: a newer
  highlight keeps its colour (yellow@500 over red@100) while a missing one is added;
  a duplicate note is skipped and a new one added; a folder is reused by path and a
  nested one created under it; restoring the same file twice changes nothing the
  second time; and 250 imported visits are capped to the newest 200. The writer and
  the reader were also checked field by field, so every key written is read back
  with the same type.
* Known limit, stated plainly: notes have no natural key, so if a note is *edited*
  on two devices, a merge keeps both copies rather than guessing which is right.

**Step 23 - Quotations underlined, with a switch (v0.19.0)**

* The reader now underlines the words an author is **quoting from an earlier
  passage** - the one thing a printed KJV cannot show you. On by default, and
  turned off in **Reading settings → Quotations underlined**: which parallels
  count as a quotation is a judgement call, so the mark is offered, not imposed.
* **Not cross-references.** A study Bible's cross-references are mostly thematic
  ("this verse is about the same idea") and underlining those would mark half the
  Bible. `tools/build_xrefs.py` starts from openbible.info's 344,754 reference
  pairs (CC BY 4.0) and keeps a pair only when the two verses share a **contiguous
  run of words**, four or more long, that
  1. is **not the KJV's own boilerplate** - a phrase that recurs across the Bible
     ("gathered unto his people", "the high places of the earth") is the
     translators' formula, not a writer reaching back;
  2. is **not mostly names** - "the Hivites, and the Jebusites" is rare and says
     nothing; names are found by which words the KJV capitalises mid-sentence;
  3. carries at least two words with meaning, at least one of them uncommon.
  What survives: **2,723 quotations in 2,198 verses** (1,642 in the Old
  Testament, 1,081 in the New), a 151 KB asset. Genesis has none, correctly -
  nothing came before it.
* **Direction.** The underline goes on the verse that *quotes*, so a pair is kept
  only for the later book. In the Gospels that is not canonical order: Mark was
  written first and Matthew and Luke both used him, so the phrase is marked in
  **them**, not in Mark - rank Mark before both, and rank Matthew and Luke equal
  so a phrase they share is left alone rather than attributed to one of them.
  Checked: Mark 1:3 ← Isaiah 40:3; Matthew 3:3 and Luke 3:4 ← both Mark 1:3 and
  Isaiah 40:3.
* **The colour is the underline's own.** `UnderlineSpan` paints in the text
  colour, which would vanish on a highlighted verse; a TextPaint carries a
  separate underline colour and `TextLine` has drawn that stroke since Android 5
  (verified in the platform source, `wp.underlineColor`), so the words keep their
  colour and only the line is plum. Setting `isUnderlineText` as well would draw
  the text-coloured line *on top*, so it is deliberately not set.
* **Checked before shipping:** every one of the 2,723 phrases is findable, word
  for word, in its own verse (the app matches by words, not punctuation); 6 of 9
  well-known quotations are found - and the three misses are honest: John 19:24
  quotes "they part my garments" as "they parted my raiment", Romans 3:10 shares
  only "there is none", and 1 Peter 2:24's "by whose stripes" is "with his
  stripes" in Isaiah 53:5. Paraphrase is not a quotation, and no mark appears.
* A sample of twenty random underlines was read: roughly fifteen are plain
  quotations or parallel accounts, the rest are near-paraphrase echoes.
* Regenerate with `python3 tools/build_xrefs.py`; it downloads the reference data,
  prints the numbers above, the spot checks and a sample of what it rejected.
  Attribution ships in `assets/xrefs.LICENSE`.
* Also per the doc: the menu item is now **Backup Notes and Folders**, with
  **Back up…** and **Import…** inside it rather than two separate entries, and the
  menu is divided into three groups (the divider needs Android 9; older versions
  show the groups without the line).

**Step 24 - Read reminders, and the quotation underline fixed (v0.20.0)**

* **Read reminder** (overflow menu, and a second way in from Reading settings, as
  the doc asked): on or off, and a time - eight in the evening until changed.
  The panel is a bottom sheet like the other reading controls.
* **It stays quiet on days you have read.** That is not a guess: the reader's own
  history already records every verse it has been to with a timestamp, so at the
  hour the reminder asks it whether anything happened since midnight. If it did,
  no notification - and tomorrow's alarm is still set.
* **Deliberately inexact.** The alarm uses `setAndAllowWhileIdle`, not an exact
  alarm: exact needs a permission the reader would have to grant, and being
  nudged "around eight" is enough for reading. One more trade: a reminder that
  cannot be silenced by a battery optimiser is worth more than a reminder to the
  second.
* **It survives what normally kills alarms**: a reboot, an app update, and every
  launch re-sets it. Nothing but switching it off clears it.
* **Android 13 and later ask for the notification permission at the moment the
  reminder is switched on** - not at launch - and if it is refused the app says so
  rather than pretending a reminder is armed. Notification: "Time to read - open
  where you left off", tapping which opens the reader at the last position.
* New permissions: `POST_NOTIFICATIONS` and `RECEIVE_BOOT_COMPLETED`. The bell is
  drawn in the project, like every other icon.
* **Bug: the quotation underlines never appeared.** The span set
  `TextPaint.underlineColor`, which is a *decoration* the platform only draws from
  **Android 11**; before that the per-run paints are merged and `equalAttributes`
  does not compare that field, so the span was set on every verse and drew
  nothing - which is exactly what was reported, toggling or not. Now the coloured
  stroke is used where it is honoured, and a plain underline (text colour, the
  words still not recoloured) everywhere else, so the mark appears on every
  version the app supports.
* **And the data path is now proven rather than assumed.** The matching moved out
  of the Android class into `QuotationIndex`, which has no Android in it, so it
  can be compiled with the app's **own** `UsfmParser` and run on a desktop JVM
  against the real assets: 2,723 quotations parse, and Mark 1:3, Matthew 3:3,
  Luke 3:4, Matthew 1:23, John 2:17, Hebrews 1:5 and Acts 2:17 each come back with
  the exact phrase and character range in the verse the reader draws. (The same
  harness caught a wrong assumption of mine: `book_41` is Matthew, not Mark - the
  asset indices are not canonical order.)
* The reading-settings row now says how many are loaded ("2,723 marked"), so if the
  asset ever fails to load that is visible at a glance instead of silent.

**Step 25 - wrong-book history jumps, Webster's wording, and tappable quotations (v0.21.0)**

* **Bug: tapping a history entry opened the wrong book** - John 3 landed in
  Genesis. `HistoryActivity` sent the book *id* ("JHN") in the extra that the
  reader parses as an **asset index**, so the `getIntExtra` fell back to its
  default of 1: Genesis, chapter 3. Every other jump - notes, folders, audio -
  already sent the index; history was the odd one out. Fixed where the book is
  known, so the extra is now the same kind of thing everywhere.
* **Webster's now opens with the word**, as the printed 1828 does: **"MAN, noun
  plural men. [Heb. species, kind, image, similitude.] 1. Mankind..."** - the
  asset had always begun at the part of speech, which made the card look like it
  might be the wrong entry.
* **The read reminder is its own section again**, per the doc: the row is gone
  from reading settings, and the overflow menu entry ("Read reminder") is the one
  way in, standing beside Backup Notes and Folders.
* **The underline is now tappable.** Tapping the underlined words shows the verse
  they quote - title, full text - with **Open** to go there; tapping anywhere else
  in the verse still opens the verse menu, and a long press still looks up the
  word. The asset gained the source's own coordinates (`BOOK ch v` plus
  `source BOOK ch v`, reference, phrase), because turning "Isaiah 40:3" back into
  a book at runtime would mean re-parsing a display string - fragile for "1 Samuel".
* Tap ranges are kept in **laid-out** space, shifted by one for each footnote
  marker before them, so a tap lands on the right words even in a verse that shows
  markers. The JVM harness now prints those coordinates too - Mark 1:3 -> ISA
  40:3, Matthew 3:3 -> MRK 1:3 and ISA 40:3, Hebrews 1:5 -> PSA 2:7 and ACT 13:33 -
  so the tap has something real to fetch.
* The doc's "[Later] basic words like man have no entry" is parked as asked. For
  the record: the asset does hold `man` (line 4556), so if it reappears it is a
  lookup-path issue, not missing data - telling me the verse and the word will
  pin it in one pass.


**Step 26 - the underline stops stealing taps (v0.22.0)**

* **A tap now always opens the verse menu again.** The underline is a signpost,
  not a control: it stays visible while reading, and what it marks is reached the
  way everything else in a verse is reached - through the menu.
* **The verse menu gained a QUOTATIONS section**, under the KJV footnotes. Each
  row is the reference and the quoted phrase - "Isaiah 40:3 / 'in the wilderness,
  Prepare ye the way of the Lord, make'" - and tapping one shows the source verse
  in full, with **Open** to go there. Only the phrases actually underlined on the
  page are listed, so the menu and the text can never disagree.
* **Why the change.** Tapping used to mean two things depending on where it
  landed: on an underlined phrase it opened the quotation card, anywhere else it
  opened the menu. That worked for the `‡` footnote marker, which is one character
  and unmistakable, but an underline can run for twenty words - a large stretch of
  a verse where the menu could no longer be opened. The gestures are now
  unambiguous: **tap a verse = verse menu**, **hold a word = its definition**,
  with the `‡` marker the one small, visible exception.
* **A bug fell out of the review**: while picking out a multi-verse block, a tap
  on an underlined phrase opened the quotation card instead of adding the verse -
  the hit test ran before the selection check. With the underline no longer
  intercepting taps that cannot recur, and the page renderer no longer decides
  what a tap means.


**Step 27 - the reminder in the right section, and long occurrences (v0.23.0)**

* **Read reminder moved below the second divider**, into the same section as
  Backup Notes and Folders - which is where the doc wanted it twice. The split now
  reads: notes and folders, then the reading controls (read aloud, voice, reading
  settings), then the reader's own routine and files (read reminder, backup).
  Reminder above the backup submenu, since one is a habit and the other its files.
* **Bug: long verses in the concordance's occurrences showed no highlight.** Each
  row shows two lines of the verse with the matching word picked out, and a long
  verse simply pushed its own word past those two lines - so the row looked
  unhighlighted even though the span was there. The row now opens 40 characters
  before the word, cuts at a word boundary, and marks the cut with an ellipsis.
* Checked against the longest verse in the KJV (Esther 8:9, 528 characters) by
  walking every one of its **91** words: no highlight is lost, and the furthest any
  of them can land is **40 characters** into the row - inside the first line, every
  time. A short verse is not cut at all ("Jesus wept." still shows whole).
* **Thumb-reach navigation is dropped** from the roadmap, as the doc says. The
  reading bar stays as it is: the title opens the picker and swiping turns pages.


**Step 28 - a neural voice, spoken on the phone (v0.24.0) - REMOVED in Step 36**

* **Four downloadable neural voices**: US female (Amy), US male (Ryan), UK female
  (Jenny), UK male (Alan). Overflow menu -> **Neural voice**; tapping one downloads
  it (~21 MB) and switches to it, and **Use the device's own voice** switches back.
* **Why this shape.** Verse-based narration is only possible without an alignment
  pass if the speech is *generated per verse*, so that is what this does: each verse
  is synthesised as it is reached, so there is nothing to estimate and nothing to
  align. It also needs no hosting and no API key - the voices come from the
  sherpa-onnx project's own release URLs.
* **Piper, not Kokoro.** Kokoro is one 147 MB file carrying every voice; Piper is
  one ~21 MB model per voice, downloaded only when chosen, and it runs faster on a
  phone CPU. Four int8 VITS models at ~21 MB each is the smallest arrangement that
  still gives male/female in both US and UK.
* **Engine**: sherpa-onnx 1.13.8 (Apache-2.0), shipped as a local AAR because it is
  not on Maven Central, with `-keep` rules since native code calls into it. The
  archives are `.tar.bz2`, which Android cannot open, so they are unpacked on the
  device with Apache Commons Compress - one small dependency instead of a hosting
  service and a re-packed zip.
* **Nothing is ever left silent**: the neural voice is used only when it is chosen
  *and* installed; otherwise the device's own engine speaks. A failed or partial
  download simply falls back.
* **Speed is a synthesis parameter**, not a playback trick - so a faster setting
  keeps the pitch instead of sounding like a chipmunk (0.6x to 1.8x).
* **Size**: the release APK goes from 5.9 MB to 18.1 MB (debug 24.1 MB). The native
  engine is 31.5 MB uncompressed (ONNX Runtime alone is 22 MB); compressed, it costs
  about 12 MB of download. `abiFilters` is arm64 only - a second architecture would
  cost another 22-31 MB for phones that could barely run it.
* Verified here: the AAR's classes and signatures (javap), and one real voice
  archive's contents - `en_US-amy-medium.onnx`, `tokens.txt`, `espeak-ng-data/` -
  which is exactly what the loader looks for. **Not verifiable here: the inference
  itself.** The native libraries are arm64 Android and cannot run in this
  environment, so the first real judgement of speed and sound is on the phone.
* Known refinements: a pronunciation lexicon for the KJV's archaic words, moving
  this panel into the existing voice picker, showing and deleting downloaded voices,
  and a rate slider in the reader itself.

**Step 29 - the two see-through panels (v0.24.1)**

* **Bug: the Read reminder and Neural voice panels were transparent.** Both are
  bottom sheets built the same way as every other one: a dialog with a transparent
  window, and the *layout* painting the sheet. Every other sheet's layout carries
  `@drawable/picker_sheet_bg` on its root - these two did not, so they had nothing
  to paint with and the page showed straight through them.
* Both roots now carry the same background as the rest, and all the dialog layouts
  were swept for the same oversight: only these two were affected (the third
  without a background, `dialog_text_input`, sits inside an AlertDialog, which
  supplies its own).
* This is why the neural voice has not been judged yet - the panel could not be
  read, let alone used.

**Step 30 - why the voices would not download (v0.24.2)**

* **The app had no `INTERNET` permission.** Until the neural voices there was
  nothing to fetch - every asset ships inside the APK - so the permission had never
  been declared, and every download died immediately with "permission denied".
* **And the failure was silent.** The store reported only true/false, and the panel
  then rebuilt its rows, wiping the message in the same frame - which is exactly
  what "tapping looks like it tried, then went back to normal" was.
* Now: `INTERNET` is declared (confirmed in the built APK's manifest), a failed
  download leaves its **reason** on the row ("Couldn't download: ...") and stays
  tappable, and one API-24-only call (`contentLengthLong`) was replaced with the
  API-1 form so this also works on Android 5 and 6.
* Not Play Protect: it warns about sideloaded *apps* at install time; it does not
  block an app from fetching a data file afterwards.

**Step 31 - the engine that closed the app (v0.25.0)**

* **The report**: the voice downloaded, then the app was force-closed. That is a
  native crash - the engine is C++ behind JNI, and when it dies it takes the process
  with it. No Kotlin `try` can catch it, and nothing is left in a log the reader can
  see.
* **The likeliest cause was mine, not the engine's**: the synthesis thread built an
  `AudioTrack` and wrote to it with no guard, so any exception there (a device
  refusing a track at 22,050 Hz, a buffer it will not accept) was uncaught on a
  background thread - which force-closes the app. Playback now uses the modern
  builder, every call around it is guarded, and a failure ends the verse instead of
  the app.
* **Breadcrumbs for a crash that cannot be caught**: before loading the model and
  before the engine's first sentence, a step is written to a file, and only success
  clears it. Next launch the panel can therefore say *"the voice engine stopped
  while loading the model / while starting the engine"* - and it switches back to
  the device's voice so the same voice cannot crash it twice.
* **A warm-up sentence at load**: one short verse is synthesised as part of loading,
  so inference is proven once, there, rather than in the middle of a chapter.
* **A truncated model can no longer reach the engine**: a voice counts as installed
  only if its `.onnx` is at least 5 MB.
* **One thread instead of two** for synthesis - a single verse needs no more, and it
  keeps the engine on the simplest path through the runtime.
* **Smaller engine, as a side effect**: the build with ONNX Runtime statically
  linked ships one library instead of two - 24.2 MB raw against 30.5, 9.74 MiB packed
  against 11.40. The release APK goes from 18.1 MB to **14.5 MB**, the debug from
  24.1 to 19.9.
* **Android 10 gate**, per the doc: below Android 10 the panel now says so instead
  of offering voices the phone may not manage.
* **A one-tap test**: **Say a verse** appears once a voice is in use, so the engine
  can be proven without going through read-aloud first.





**Step 32 - "while loading the model", and proving the download (v0.25.1)**

* The breadcrumb said **loading**, not speaking, so inference was never the problem -
  it is the model files or the session built from them.
* Checked here, on the real archive: the unpacking rules produce exactly the right
  tree (359 files, `en_US-amy-medium.onnx` at 18,681,741 bytes, `tokens.txt`,
  `espeak-ng-data/` with `phontab` and `phondata`). So the logic was not wrong.
* Which left the copy on the phone. **The only check was "model bigger than 5 MB"**,
  so a download that stopped at 12 MB, or an unpack interrupted when the app was
  killed, would pass - and be handed to the engine, which does not fail gracefully,
  it takes the process down.
* **Now every voice carries its archive's SHA-256 and its model's exact byte count.**
  The archive is hashed after downloading and unpacked only if it matches; the model
  counts as installed only at its exact size. A mismatch means "Download" again, and
  a fresh download is verified before anything tries to load it. An existing bad
  copy is therefore replaced rather than re-crashed.
* **Failures now say why.** The breadcrumb records the exception when the engine
  refuses (a message like `UnsatisfiedLinkError: …`), and leaves no message when the
  process died - so the panel distinguishes "the engine could not start: <reason>"
  from "the engine stopped", instead of calling both a crash.
* The fingerprints, for the record: amy `bd23c0aa…`, ryan `376eb489…`,
  jenny `db7ed4ed…`, alan `f0b2526c…`.


**Step 33 - the asset manager, found on an emulator (v0.25.2)**

* First the model was proven, away from Android. The same `en_US-amy-medium.onnx`,
  run through sherpa-onnx on this machine, loads in 0.84 s and speaks 3.4 s of
  audio in 0.43 s (RTF 0.13, 22,050 Hz). The archive, the tokens, the espeak data
  and the config values were never the problem - the fault had to be Android.
* To see it, an emulator was installed here (Android 10, x86_64). The build takes
  `-PemulatorAbi` to swap the arm64 library for an x86_64 one for testing; the
  shipped APKs stay arm64-only.
* The crash reproduced exactly, and logcat named it:
  `You are using an absolute path '.../en_US-amy-medium.onnx', but assetManager is
  NOT set to null`, then `F sherpa-onnx: Read binary file: Load '...' failed`.
* The cause was one argument: `OfflineTts(context.assets, config)`. With an asset
  manager set, sherpa-onnx resolves every model path through the APK instead of the
  filesystem, so a downloaded voice can never be opened - and it does not return an
  error, it takes the process down (sherpa-onnx issue #2562).
* **The fix is `OfflineTts(null, config)`**, commented so it is not "tidied" back.
* Verified on the emulator, v0.25.2: **Say a verse** speaks, and **Read aloud**
  reads Genesis 1 verse by verse - 20 AudioTracks delivered, no fatal line, the
  process still alive.
* Also here: the On/Off toggles are compact pills (13sp, 7dp padding, 64dp minimum)
  in Reading settings and Read reminder, the sliders untouched; and the Define card
  no longer stacks the word on its own title - the 1828 entry is shown as it stands,
  with a capital to open it.
* The emulator stays installed (AVD `kjv29`), so the next visual change can be
  looked at here before it ships.


**Step 34 - the doc's other items, and a safety net for the neural voice (v0.25.3)**

* **The Define card was corrected, not changed.** Step 33's reading was wrong: the
  request was word + verse reference at the top and the original word with its
  number at the bottom, *with* Webster naming the entry. The 1828 entry therefore
  opens "BEGINNING, ..." again, because that name is how the reader confirms the
  right entry was found. Verified: "Jacob" / "Genesis 29:1" at the top, and
  "HEBREW · H3290" with the Hebrew at the bottom.
* **The reminder can be tested now.** "Read today" used to be any visit, so opening
  the app to check the reminder counted as reading. `ReadingTime` now accumulates
  only the time an activity is in front - leaving the app pauses it, midnight
  resets it - and ten minutes is what silences the nudge. Verified on the emulator:
  eight seconds in front wrote `seconds=7` for the day and stopped when the app was
  backgrounded.
* **A neural voice that loads but says nothing no longer leaves silence.** The
  engine's "no audio" result was reported as done. Now `NeuralSpeaker` reports it,
  and `SwitchingSpeaker` hands that verse - and the rest of the session - to the
  device's own voice.
* Still open from the doc, marked [Later] there: the multi-verse note placement
  problem, and "man" having no 1828 entry.


**Step 35 - word search, and why the neural voice was silent (v0.25.4)**

* **Word search across the whole KJV.** Any words the reader remembers, in any
  order: `VerseSearch` builds an in-memory index once (word -> verses that use it)
  the first time the search screen opens, so a query is a few list intersections
  rather than a scan of 4.4 MB of text.
  * A word matches exactly, or as the *start* of a word ("beginn" finds
    "beginning"); when the Bible has no such word at all, words one edit away are
    tried, so "begining" still works.
  * Several words must all be in the verse; text in quotes must appear as a phrase.
  * Results keep Bible order, the asked words are bold, and tapping one opens the
    reader there (same contract as History and Notes).
  * Verified on the emulator: "god created heaven" -> Genesis 1:1 and
    Deuteronomy 4:32; "begining" -> the beginning verses; tapping the first result
    opened Genesis 1.1.
* **The silence had nothing to do with the model.** `createTrack` sized the audio
  buffer to the *entire* clip, so every write returned instantly, and the track was
  stopped as soon as the last byte was handed over - the buffer was thrown away
  before the speaker rendered any of it. The emulator was started with `-no-audio`,
  so it could never show this; it only ever proved that frames were *written*.
  * Now a small buffer (two minimums) keeps the stream flowing, `play()` runs
    before the writes, and the track is stopped only once `playbackHeadPosition`
    has reached the end of the clip (with a grace period so a stuck head cannot
    hang the reader).
  * A failed track or an empty synthesis result now reports itself, so
    `SwitchingSpeaker` hands the verse to the device's voice instead of silence.
* The logcat from the phone was only a system-error slice (112 lines, AudioFlinger
  DVFS only) - `adb logcat -d > file` on the phone, unfiltered, is what is needed
  if anything else misbehaves.
* Built APKs are kept outside the repository, in a folder shared with the host.


**Step 36 - the neural voice is gone (v0.25.5)**

The on-device neural engine was removed in full: `NeuralSpeaker`, `NeuralVoices`,
`NeuralVoiceStore`, `NeuralAttempts`, `SwitchingSpeaker`, the voice panel and its
layout, the 37 MB sherpa-onnx AAR, `commons-compress`, the proguard keeps, and the
`INTERNET` permission.

* **Why.** It never produced sound on the target phone (a Galaxy S9+) across four
  attempts. The last diagnosis was real - the audio buffer was discarded before it
  ever played - but after that the signal stopped improving, and an engine that
  costs 9 MB of APK plus a 21 MB download has to work the first time. Shipping a
  control that can silence the reader is worse than shipping the device's voice.
* **What remains.** Reading aloud still works, verse by verse, through the
  device's TTS engine behind the same `VerseSpeaker` interface, with the
  **Reading voice** picker (engine, male/female, network voices, preview).
  Steps 28-35 are kept above as the record of a removed experiment.
* **The app got smaller and simpler.** Release APK **14.51 -> 5.67 MB**, debug
  **19.92 -> 9.33 MB**, **zero native libraries**, and no `INTERNET` permission at
  all - the app now makes no network requests of any kind.
* **ABI filters are gone.** With no native code the same APK installs on every
  device and on an x86_64 emulator; `-PemulatorAbi` no longer exists.
* Verified on the emulator: installs, opens Genesis 1, **Read aloud** starts the
  foreground service, the player bar follows to Genesis 1:2, pause and stop work,
  and logcat shows no fatal exception.


## Roadmap

1. **Version Compare** - needs a second public-domain text (WEB or ASV 1901). It
   changes the page layout, so it comes after the reading features above.
2. **Recorded audio (later)** - needs a per-chapter host and a timing file per
   chapter, so verse seeking stays exact; it would arrive as a second
   `VerseSpeaker` backend behind the same interface.
3. **Sleep timer and a speed slider** - `VoicePrefs` already stores a rate
   (0.6x-1.8x); what is missing is the control, and the timer.
4. **Bible Memory** - our own spaced repetition, with "add to memory" in the verse
   menu. There is no Bible Memory app API to hand off to.
5. **Small things**: sticky headers in the passage picker, and a pronunciation
   hint for the KJV's archaic words (`shew`, `begat`) if the device voice mangles
   them.

Closed, with reasons: **reading plans** and **bubble buttons** (both marked
`[SKIP]` in the doc - the first is bloat, the second is not worth the time).
