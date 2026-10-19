# KJV Reader

An offline King James Bible reader for Android. The Bible text, Strong's
dictionary, Webster's 1828 definitions, cross-references and quotation index are
all bundled in the APK. No network permission, no ads, no analytics.

| | |
|---|---|
| Application ID | `com.realsneed.biblereader` |
| Version | 0.25.5 (versionCode 39) |
| SDK | `minSdk 21`, `compileSdk` / `targetSdk 34` |
| Dependencies | `appcompat 1.7.0`, `recyclerview 1.3.2`, `viewpager2 1.1.0` |

## Features

**Reading**

* KJV text with the translators' italics and marginal footnotes.
* Swipe or page between chapters; passage picker grouped by Bible section.
* Reference search with shorthand and book aliases (`Jn 3:16`, `1 Cor 13`).
* Free-text word search and a concordance of occurrences.
* Reading settings: text size, line spacing, sans/serif, background theme, and
  switches for italics, footnotes and quotation underlines.
* History of recent readings, capped at 200 entries.

**Study**

* Tap a verse to open the **Verse menu**: highlight (four colours), note, save to
  folder, copy, footnotes and cross-references.
* Hold a word to look it up: Strong's entry, Webster's 1828 definition,
  concordance and other occurrences.
* Notes can cover a range of verses.
* Quotations are underlined; tap one to jump to the quoted passage.
* Recursive folders for saved verses.

**Listening and reminders**

* Read aloud through the device's own text-to-speech engine, with a foreground
  service and notification controls.
* Daily reading reminders, restored after reboot.

**Data**

* JSON backup and restore, with merge or replace.

## Project layout

```
kjvreader/
  app/src/main/assets/usfm/kjv/     KJV text, Strong's words and lexicon
  app/src/main/assets/usfm/kjv/webster   Webster's 1828 extract
  app/src/main/assets/xrefs         quotation index
  app/src/main/java/com/realsneed/biblereader/
    data/                           parser, repositories, SQLite store, backup
    audio/                          TTS speaker, player, foreground service
    notify/                         reminder scheduling
    ui/                             reader, picker, sheets, folders, search
  tools/                            asset generators
```

## Building

Requires **JDK 17** and **Android SDK 34** (`platforms;android-34`,
`build-tools;34.0.0`), with `ANDROID_HOME` exported. The Gradle wrapper is
committed, so no Gradle install is needed. `local.properties` is not required
when `ANDROID_HOME` is set.

```bash
./gradlew assembleDebug      # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # minified and resource-shrunk, unsigned
```

The project has no native libraries, so the same APK installs on any ABI. The
build is currently signed with the debug keystore; a real keystore is needed
before any store release.

## Data

* 66 books, 1,189 chapters, 31,102 verses.
* Strong's numbers and lexicon in `assets/usfm/kjv/`.
* 8,559 Webster's 1828 headwords, covering 95.5% of the KJV's vocabulary.
* 2,723 quotation links derived from openbible.info cross-references.

`tools/build_webster.py` and `tools/build_xrefs.py` regenerate the generated
assets. Neither runs during the build.

## Verification

Changes are verified rather than assumed:

1. Database logic is mirrored in real `sqlite3`, including upgrade paths.
2. Kotlin data classes are compiled and run on the desktop JVM against the real
   assets for text, dictionary and quotation checks.
3. UI changes are checked on an emulator (AVD `kjv29`, Android 10, x86_64) with
   screenshots.
4. If a fix depends on platform behaviour, the README or commit states the API
   level at which it applies and what happens below it.

## Known issues and deferred work

* A note covering a multi-verse selection appears under each verse instead of
  once at the end of the segment. Needs a design decision.
* The word "man" shows no Webster's entry although the data contains it. Needs a
  reproducing verse and word.
* Deferred: version comparison, recorded human audio, sleep timer and speed
  slider, Bible Memory, sticky headers in the passage picker, and pronunciation
  hints for archaic words.

## Conventions

* Bump `versionCode` and `versionName` every iteration. Detailed per-iteration
  notes are kept privately by the maintainer; this README stays an overview.
* Tap a verse opens the **Verse menu**; hold a word looks up that word. These two
  gestures are reserved and must not be taken over.
* Keep it zero-bloat: no Compose, no Firebase, no ads, no analytics, no network
  permission. Ask before adding a dependency.
* `THIRD_PARTY_NOTICES.md` records the bundled data and library licences and
  must stay.

## Licence

The source code is **all rights reserved**. No open-source licence is granted.
Bundled data and libraries are covered by `THIRD_PARTY_NOTICES.md`.
