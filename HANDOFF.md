# HANDOFF

Everything needed to pick this project up with no conversation history: what to
copy, how to build it elsewhere, how the code is arranged, where the data came
from, what is verified, and what is still open.

Two other documents carry the rest:

* **`README.md`** - the chronological build log, 36 numbered steps: what was built
  in what order, and the evidence behind each claim.
* **The Google Doc** - requirements and bugs as the owner tracks them. It is
  shared privately; ask the owner for access.

---

## 1. What this is

An offline King James Bible reader for Android, 5.7 MB, no ads, no analytics, no
network access of any kind. Every byte of text it shows - the Bible, Strong's
dictionary, the Webster's 1828 definitions, the quotation index - is bundled in
the APK.

Target device: Samsung Galaxy S9+ (Android 10). `minSdk 21`.

## 2. What to copy

The repository is **this folder only**: about 20 MB across 266 files.

| Not part of the repo | Size | Why |
|---|---|---|
| `app/build/` | ~530 MB | Gradle output, regenerated |
| `.gradle/` | ~10 MB | local cache |
| `local.properties` | 130 B | holds this machine's SDK path; a build elsewhere must not inherit it |
| `tools/__pycache__/` | - | Python bytecode |

The extracted original APK (`bijbel-v8.2`) and the local JDK/Android SDK
(`.toolchain`) live beside this folder and are **not** part of it. The first was
only a data source and its text is already inside `app/src/main/assets`; the
second is this machine's toolchain.

`.gitignore` already excludes all of the above, so `git add .` is safe.

## 3. Building

Needs **JDK 17** and **Android SDK 34** (`platforms;android-34`,
`build-tools;34.0.0`), with `ANDROID_HOME` exported. The Gradle wrapper is
committed, so no Gradle install is needed.

```bash
./gradlew assembleDebug     # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease   # minified + resource-shrunk, unsigned
```

There are **no native libraries**, so the same APK installs on any ABI - phone or
emulator. No ABI filters, no `-P` flags.

Both artefacts are currently signed with the **debug keystore**, which is fine for
sideloading but must be replaced with a real keystore before any store release.

### Emulator

An AVD named `kjv29` (Android 10, x86_64) was used here. Start it detached so it
outlives the shell:

```bash
setsid nohup $ANDROID_HOME/emulator/emulator -avd kjv29 -no-window -no-audio \
  -no-boot-anim -no-snapshot -gpu swiftshader_indirect -memory 3072 -cores 4 \
  </dev/null >/tmp/emu.log 2>&1 & disown
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

`-no-audio` proves the code runs, not that sound is audible - judge audio on a
real phone.

## 4. Current state

* `applicationId` `com.realsneed.biblereader`, version **0.25.5** (`versionCode 39`)
* `minSdk 21`, `compileSdk`/`targetSdk 34`
* Release **5.67 MB**, debug **9.33 MB**
* Permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`,
  `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`. **No `INTERNET`.**
* Dependencies, deliberately minimal: `appcompat 1.7.0`, `recyclerview 1.3.2`,
  `viewpager2 1.1.0`. No Compose, no Firebase, no ads, no analytics, no OkHttp.

## 5. Code map (44 Kotlin files)

| Package | Responsibility |
|---|---|
| `data/` | `BibleMeta` (66 books, chapter geometry), `UsfmParser`, `BibleRepository` (assets + LRU cache), `BibleReferences` (shorthand reference search), `VerseSearch` (word index for free-text search), `VerseStore` (all SQLite), `FolderStore`, `Backup` (JSON export/import), `StrongsRepository`, `Concordance`, `WebsterRepository`, `CrossReferences` + `QuotationIndex`, `ReadingPrefs`, `ReadingTime`, `ReminderPrefs`, `VoicePrefs` |
| `audio/` | `VerseSpeaker` interface, `SystemTtsSpeaker` (the device's engine), `AudioPlayer` (verse sequencing), `AudioService` (foreground service + notification), `VoiceCatalog` |
| `notify/` | `ReminderScheduler`, `ReminderReceiver` |
| `ui/` | `MainActivity` (reader), `ChapterPagerAdapter` (one page per chapter), plus the sheets and screens: passage picker, verse menu, word study, Strong's card, concordance, footnotes, folders, notes, history, search, reading settings, reminder, voice picker |

## 6. Persistence

One SQLite database through `VerseStore`, **schema version 5**:

* `verse_mark` - highlight colour per verse
* `verse_note` - many notes per verse, each covering `verse..end_verse`
* `folder` - self-referencing `parent_id`, so the tree is recursive to any depth
* `folder_verse` - verse-to-folder filing (a verse may sit in several folders)
* `history` - recent landings, capped at 200

Migrations are **additive** (`onUpgrade`, `VerseStore.kt:109`). Any change to the
schema must migrate existing notes, highlights and folders, and must be tested
against a real SQLite database first - that is the established practice here, and
it has already caught one real migration bug.

## 7. Data provenance

| Asset | Source | Licence |
|---|---|---|
| `assets/usfm/kjv/book_*`, `words_*`, `lexicon` | extracted from the `bijbel-v8.2` APK | KJV and Strong's: public domain |
| `assets/webster` (8,559 entries) | Webster's 1828, transcription at `github.com/akaitsurugi/webster1828` | **CC BY-SA 4.0**, attributed on the card |
| `assets/xrefs` (2,723 quotations) | openbible.info cross-references | **CC BY 4.0**, full text in `assets/xrefs.LICENSE` |

See `THIRD_PARTY_NOTICES.md`.

Two generators in `tools/` rebuild those assets from upstream. Neither runs during
the build; both download their input if not given a path, and both print a report:

```bash
python3 tools/build_webster.py     # -> app/src/main/assets/webster
python3 tools/build_xrefs.py       # -> app/src/main/assets/xrefs
```

## 8. How things were verified

1. **Database logic** - the SQL is mirrored in real `sqlite3` (Python) and the
   upgrade paths and edge cases are run before shipping.
2. **Data logic in Kotlin** - the project's own classes are compiled with the
   Kotlin compiler against the real assets and run on the desktop JVM. This is how
   "31,102 verses byte-identical", "8,559 headwords / 95.5% coverage" and the
   quotation alignment were proven rather than assumed.
3. **UI** - emulator (AVD `kjv29`) plus screenshots. Any new dialog must reuse the
   existing sheet construction: transparent window plus
   `@drawable/picker_sheet_bg` on the layout root. Two panels shipped see-through
   because that was missed.
4. **Platform behaviour** - check the API level at which a behaviour actually
   exists. `TextPaint.underlineColor` is only drawn from Android 11, which is why
   an underline feature silently did nothing for a while.

**Rule that came out of the bugs:** if a fix depends on platform behaviour, say
which API level it starts at and what happens below it.

## 9. Open items

1. **Multi-verse notes sit under each verse** rather than once at the end of the
   segment. `[Later]` in the doc; needs a design decision about where the marker
   belongs.
2. **The word "man" shows no Webster's entry.** `[Later]`. The data does contain it
   (CSV line 4556), so it is a lookup-path bug - needs the verse and word that
   reproduce it.
3. **Version Compare** - needs a second public-domain text (WEB or ASV 1901); it
   changes the page layout, so it was deferred.
4. **Recorded human audio** - needs a per-chapter host plus a timing file per
   chapter for exact verse seeking; it would be a second `VerseSpeaker` backend.
5. **Sleep timer and speed slider** - `VoicePrefs` stores a rate (0.6x-1.8x); the
   control and the timer are missing.
6. **Bible Memory** - own spaced repetition, with "add to memory" in the verse menu.
7. **Small things** - sticky headers in the passage picker; a pronunciation hint
   for archaic words (`shew`, `begat`) if the device voice mangles them.

Removed, with reasons: **reading plans** and **bubble buttons** (`[SKIP]` in the
doc), **thumb-reach navigation** (scrapped), and the **neural TTS engine**
(Step 36 in the README: four attempts, never produced sound on the target phone;
reading aloud now uses the device's own engine behind the same interface).

## 10. Conventions

* Bump `versionCode` and `versionName` every iteration, and append a numbered
  `**Step N - ...**` section to `README.md` with the evidence for the change.
* The sheet you get by tapping a verse is called the **Verse menu** everywhere -
  code, docs and conversation.
* Two gestures are reserved: **tap a verse** = Verse menu, **hold a word** = that
  word's definition. Other features must not take them over.
