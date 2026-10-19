# Third-party notices

The app's own source code carries no open-source licence (all rights reserved by
the author). The material below ships inside the app or is compiled into it, and
is included under the terms stated.

## No native or network code

Version 0.25.5 removed the on-device neural speech engine, its 37 MB AAR and the
`INTERNET` permission. The APK now contains **no native libraries** and makes no
network requests. Speech uses the device's own text-to-speech engine, which is
part of Android rather than of this app.

## Compiled into the app

| Component | Licence |
|---|---|
| AndroidX AppCompat, RecyclerView, ViewPager2 | Apache License 2.0 |
| Kotlin standard library and coroutines runtime | Apache License 2.0 |

Apache License 2.0: <https://www.apache.org/licenses/LICENSE-2.0>

No modification has been made to these libraries. They are consumed as published
artefacts from Google's Maven repository.

## Bible text and study data

### King James Version

The KJV text is in the **public domain in the United States**. Note that in the
United Kingdom the Authorised Version is subject to perpetual Crown copyright
under letters patent, and other jurisdictions may differ. Anyone redistributing
this app commercially should satisfy themselves about their own territory.

### Strong's numbers and lexicon

`assets/usfm/kjv/words_*` and `assets/usfm/kjv/lexicon` derive from *Strong's
Exhaustive Concordance of the Bible* (1890), which is in the public domain.

### Webster's 1828 American Dictionary of the English Language

`assets/webster` is a reduced extract of the 1828 dictionary, containing only the
8,559 headwords the KJV uses.

* The dictionary text itself is in the public domain.
* The transcription used as input is published at
  <https://github.com/akaitsurugi/webster1828> under
  **Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)**.
* Accordingly this asset is distributed under **CC BY-SA 4.0**, and the app
  attributes it on the definition card.
* Licence text: <https://creativecommons.org/licenses/by-sa/4.0/legalcode>

### Cross-references and quotation index

`assets/xrefs` (2,723 quotation links) is derived from the cross-reference data
published by **openbible.info**, used under the
**Creative Commons Attribution 4.0 International (CC BY 4.0)** licence.

* Source: <https://www.openbible.info/labs/cross-references/>
* Licence text: <https://creativecommons.org/licenses/by/4.0/legalcode>
* The full attribution ships in `app/src/main/assets/xrefs.LICENSE`.

The derivation is a filter, not a copy: only pairs that share a contiguous run of
words in the KJV are kept, and the quoted phrase is stored alongside the
reference. Regenerate with `tools/build_xrefs.py`.
