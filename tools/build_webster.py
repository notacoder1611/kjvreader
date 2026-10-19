#!/usr/bin/env python3
"""Build the Webster's 1828 asset that ships inside the app.

The KJV is 789,628 words but only uses 12,671 distinct forms, and the full 1828
dictionary holds 62,388 headwords. Shipping all of it would add 16 MB to the APK
for words this Bible never uses, so this reduces it to the KJV's own vocabulary,
written one entry per line as `word<TAB>definition`.

Doing the work here rather than in the app means the lookup at runtime is a plain
map hit - no stemming heuristics on the device, and no chance of the two drifting
apart. The archaic forms the KJV is full of (`hath`, `saith`, `spake`) are
resolved here, once.

Usage:
    python3 tools/build_webster.py [path/to/webster1828.csv]

The CSV - a transcription of the 1828 American Dictionary of the English
Language (public domain), licensed CC BY-SA 4.0 by its transcriber - comes from
https://github.com/akaitsurugi/webster1828 and is fetched if not given.
"""
import csv
import gzip
import os
import re
import sys
import urllib.request

CSV_URL = "https://raw.githubusercontent.com/akaitsurugi/webster1828/master/webster1828.csv"

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets", "usfm", "kjv")
OUT = os.path.join(ASSETS, "webster")
LICENCE = os.path.join(ASSETS, "webster.LICENSE")

# The same markers UsfmParser handles, so the vocabulary here is the reader's.
FOOTNOTE = re.compile(r"\\f\s.*?\\f\*", re.S)
MARKER_CLOSE = re.compile(r"\\[a-zA-Z+][a-zA-Z0-9]*\*")
MARKER_OPEN = re.compile(r"\\[a-zA-Z+][a-zA-Z0-9]*")
STRUCTURAL = re.compile(r"^\\[pqdmb][a-z0-9]*[^\n]*$", re.M)
CHAPTER = re.compile(r"\\c\s+(\d+)")
VERSE = re.compile(r"\\v\s+(\d+)\s?")
WHITESPACE = re.compile(r"[\s\u00A0]+")
GLUED = re.compile(r"(?<=[.!?])(?=[A-Z])")

# Letters a lookup keeps; everything else is verse punctuation.
KEEP = set("abcdefghijklmnopqrstuvwxyz'-'")

# A paragraph becomes this in the asset: one entry per line, so a real
# newline would break the format. The reader turns it back into a line break.
PARAGRAPH = '\u001F'

# Forms the ordinary endings cannot reach, because the relation is not a suffix.
ARCHAIC = {
    "hath": "have", "hast": "have", "hadst": "have", "saith": "say", "sayest": "say",
    "dost": "do", "doth": "do", "doest": "do", "didst": "do", "shalt": "shall",
    "spake": "speak", "spakest": "speak", "shew": "show", "shewed": "show",
    "sheweth": "show", "shewn": "show", "begat": "beget", "begot": "beget",
    "beginneth": "begin", "bare": "bear", "brake": "break", "drave": "drive",
    "sate": "sit", "clave": "cleave", "sware": "swear", "ware": "wear",
    "gat": "get", "girt": "gird", "went": "go", "art": "be", "wrought": "work",
    "smote": "smite", "canst": "can", "couldest": "can", "mayest": "may",
    "mightest": "might", "wouldest": "would", "shouldest": "should",
    "goeth": "go", "lieth": "lie", "dealt": "deal", "drew": "draw",
    "graven": "grave", "clomb": "climb", "holden": "hold", "shaken": "shake",
    "smiteth": "smite", "did": "do", "was": "be", "were": "be", "am": "be",
    "been": "be", "is": "be", "are": "be", "gotten": "get", "began": "begin",
    "yourselves": "yourself", "themselves": "themself", "ourselves": "ourself",
    "thyself": "self",
}

# A few forms the transcription has no entry for; mapped so they still say something.
MANUAL = {
    "might": "mighty", "meat": "victuals", "flood": "deluge", "corn": "maize",
    "oil": "oil", "honey": "honey", "wine": "wine",
}

def clean(word):
    return "".join(c for c in word.lower() if c in KEEP)

def candidates(form):
    """Every headword worth trying for one KJV form, best first."""
    out = [form]

    def add(value):
        if value and len(value) >= 3 and value not in out:
            out.append(value)

    add(form.replace("-", "").replace("'", ""))
    if form.endswith("'s"):
        add(form[:-2])
    if form in ARCHAIC:
        add(ARCHAIC[form])
    if form in MANUAL:
        add(MANUAL[form])
    for base in list(out):
        if base.endswith("ies") or base.endswith("ied"):
            add(base[:-3] + "y")
        for suffix in ("eth", "est", "ing", "ed", "es", "s", "ly"):
            if base.endswith(suffix) and len(base) - len(suffix) >= 3:
                stem = base[: -len(suffix)]
                add(stem)
                add(stem + "e")
    return out

KEEP = set("abcdefghijklmnopqrstuvwxyz'-")


def verse_words():
    """Every word form the KJV uses, and how often.

    The book files are USFM, so this mirrors UsfmParser: footnotes and
    layout-only lines out, markup off, whitespace normalised. The word counts it
    prints are the ones the app itself sees.
    """
    forms = {}
    verses = 0
    for index in range(1, 68):
        path = os.path.join(ASSETS, "book_%d" % index)
        if not os.path.exists(path):
            continue
        with open(path, encoding="utf-8") as handle:
            raw = handle.read()
        first = CHAPTER.search(raw)
        if not first:
            continue
        body = raw[first.start():]
        body = FOOTNOTE.sub(" ", body)
        body = STRUCTURAL.sub("", body)
        chapters = list(CHAPTER.finditer(body))
        for position, chapter in enumerate(chapters):
            end = chapters[position + 1].start() if position + 1 < len(chapters) else len(body)
            content = body[chapter.end():end]
            marks = list(VERSE.finditer(content))
            for slot, mark in enumerate(marks):
                stop = marks[slot + 1].start() if slot + 1 < len(marks) else len(content)
                text = MARKER_CLOSE.sub("", content[mark.end():stop])
                text = MARKER_OPEN.sub(" ", text)
                text = WHITESPACE.sub(" ", GLUED.sub(" ", text)).strip()
                if not text:
                    continue
                verses += 1
                for token in text.split(" "):
                    form = clean(token)
                    if form:
                        forms[form] = forms.get(form, 0) + 1
    print("verses parsed           : %d" % verses)
    if len(forms) < 11000 or verses < 30000:
        print("WARNING: that does not look like the whole KJV - check the parser")
    return forms

def plain(markup):
    """The source stores HTML-ish markup; the card shows plain text.

    Every `<p>` is kept as its own line. In the source those paragraphs are the
    part of speech, then one per numbered sense, then one per scripture quotation
    - flattening them into a single blob is what made the definitions a wall of
    text. Lines are joined with the unit separator, because the asset stores one
    entry per line and the reader turns it back into line breaks.
    """
    text = markup.replace('</p>', PARAGRAPH).replace('<p>', PARAGRAPH)
    text = re.sub(r'<[^>]+>', '', text)
    lines = [' '.join(line.split()) for line in text.split(PARAGRAPH)]
    lines = [line for line in lines if line]
    # Drop the shouted headword the entry opens with; the card already shows it.
    if lines:
        first = lines[0]
        parts = first.split(' ', 1)
        if parts[0].rstrip(',').isupper():
            lines[0] = parts[1].lstrip(', ') if len(parts) > 1 else ''
    return PARAGRAPH.join(line for line in lines if line)

def main():
    source = sys.argv[1] if len(sys.argv) > 1 else os.path.join("/tmp", "webster1828.csv")
    if not os.path.exists(source):
        print("downloading", CSV_URL)
        urllib.request.urlretrieve(CSV_URL, source)

    headwords = {}
    with open(source, encoding="utf-8", errors="replace") as handle:
        for row in csv.reader(handle):
            if len(row) >= 2 and row[0].strip():
                key = clean(row[0])
                if key and key not in headwords:
                    headwords[key] = row[1]
    print("headwords in the source : %d" % len(headwords))

    forms = verse_words()
    print("distinct KJV word forms : %d" % len(forms))

    lines = []
    missing = {}
    for form in sorted(forms):
        hit = next((word for word in candidates(form) if word in headwords), None)
        if hit is None:
            missing[form] = forms[form]
            continue
        lines.append(form + "\t" + plain(headwords[hit]))

    blob = "\n".join(lines).encode("utf-8")
    with open(OUT, "wb") as handle:
        handle.write(blob)

    with open(LICENCE, "w", encoding="utf-8") as handle:
        handle.write(
            "Definitions in `webster` come from Webster's 1828 American Dictionary of\n"
            "the English Language (public domain), transcribed by akaitsurugi and\n"
            "published under CC BY-SA 4.0:\n"
            "https://github.com/akaitsurugi/webster1828\n\n"
            "`webster` is a reduced, plain-text subset of that transcription: only the\n"
            "words the KJV itself uses, resolved by tools/build_webster.py. The subset\n"
            "stays under CC BY-SA 4.0.\n"
        )

    covered = sum(count for form, count in forms.items() if form not in missing)
    total = sum(forms.values())
    print("entries written         : %d" % len(lines))
    print("KJV forms covered       : %d of %d" % (len(forms) - len(missing), len(forms)))
    print("KJV words covered       : %d of %d (%.1f%%)" % (covered, total, 100.0 * covered / total))
    print("asset size              : %.2f MB raw, %.2f MB gzipped"
          % (len(blob) / 1048576.0, len(gzip.compress(blob, 9)) / 1048576.0))
    print("largest entry           : %d chars" % max(len(line) for line in lines))
    print()
    print("most common words still without a definition:")
    for form, count in sorted(missing.items(), key=lambda kv: -kv[1])[:15]:
        print("    %-16s x%d" % (form, count))
    print()
    print("-> %s" % OUT)

if __name__ == "__main__":
    main()
