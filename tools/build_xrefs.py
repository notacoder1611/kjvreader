#!/usr/bin/env python3
"""Build the quotation-underlining asset that ships inside the app.

The reader can underline the words a biblical author is *quoting*. That is not
the same as a cross-reference: a study Bible's cross-references are mostly
thematic ("this verse is about the same idea"), and underlining those would put
a mark on half the Bible. What is wanted here is narrower - did the writer
actually take the words of an earlier passage and use them?

So this reduces the reference data to quotations, by one measurable rule: the two
verses must share a **contiguous run of words**, at least four long, containing
at least two that are not filler, and at least one that is rare in the KJV. Short
shared phrases ("and it came to pass", "the Lord said unto") cannot survive that,
which is what keeps the underlines off the formulaic language the KJV is full of.

Direction matters too: the underline goes on the verse that *does the quoting*,
so a pair is kept only for the later book in canonical order.

Input:  https://a.openbible.info/data/cross-references.zip  (CC BY, openbible.info)
Output: app/src/main/assets/xrefs  - one line per quotation:

    BOOK <TAB> chapter <TAB> verse <TAB> reference <TAB> quoted phrase

Usage:
    python3 tools/build_xrefs.py [path/to/cross_references.txt]

Run it and read the report: it prints how many pairs survived, the numbers behind
a handful of well-known quotations, and a sample of what it *rejected*.
"""
import collections
import difflib
import io
import os
import re
import sys
import urllib.request
import zipfile

URL = "https://a.openbible.info/data/cross-references.zip"

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
USFM = os.path.join(ASSETS, "usfm", "kjv")
OUT = os.path.join(ASSETS, "xrefs")
LICENCE = os.path.join(ASSETS, "xrefs.LICENSE")
META = os.path.join(ROOT, "app", "src", "main", "java", "com", "realsneed",
                   "biblereader", "data", "BibleMeta.kt")

# The same markers UsfmParser handles, so the text here is the reader's text.
FOOTNOTE = re.compile(r"\\f\s.*?\\f\*", re.S)
MARKER_CLOSE = re.compile(r"\\[a-zA-Z+][a-zA-Z0-9]*\*")
MARKER_OPEN = re.compile(r"\\[a-zA-Z+][a-zA-Z0-9]*")
STRUCTURAL = re.compile(r"^\\[pqdmb][a-z0-9]*[^\n]*$", re.M)
CHAPTER = re.compile(r"\\c\s+(\d+)")
VERSE = re.compile(r"\\v\s+(\d+)\s?")
WHITESPACE = re.compile(r"[\s\u00A0]+")
GLUED = re.compile(r"(?<=[.!?])(?=[A-Z])")

WORD = re.compile(r"[a-z0-9']+")

# openbible.info's book abbreviations -> the app's USFM ids.
CODES = {
    "Gen": "GEN", "Exod": "EXO", "Lev": "LEV", "Num": "NUM", "Deut": "DEU",
    "Josh": "JOS", "Judg": "JDG", "Ruth": "RUT", "1Sam": "1SA", "2Sam": "2SA",
    "1Kgs": "1KI", "2Kgs": "2KI", "1Chr": "1CH", "2Chr": "2CH", "Ezra": "EZR",
    "Neh": "NEH", "Esth": "EST", "Job": "JOB", "Ps": "PSA", "Prov": "PRO",
    "Eccl": "ECC", "Song": "SNG", "Isa": "ISA", "Jer": "JER", "Lam": "LAM",
    "Ezek": "EZK", "Dan": "DAN", "Hos": "HOS", "Joel": "JOL", "Amos": "AMO",
    "Obad": "OBA", "Jonah": "JON", "Mic": "MIC", "Nah": "NAM", "Hab": "HAB",
    "Zeph": "ZEP", "Hag": "HAG", "Zech": "ZEC", "Mal": "MAL", "Matt": "MAT",
    "Mark": "MRK", "Luke": "LUK", "John": "JHN", "Acts": "ACT", "Rom": "ROM",
    "1Cor": "1CO", "2Cor": "2CO", "Gal": "GAL", "Eph": "EPH", "Phil": "PHP",
    "Col": "COL", "1Thess": "1TH", "2Thess": "2TH", "1Tim": "1TI", "2Tim": "2TI",
    "Titus": "TIT", "Phlm": "PHM", "Heb": "HEB", "Jas": "JAS", "1Pet": "1PE",
    "2Pet": "2PE", "1John": "1JN", "2John": "2JN", "3John": "3JN", "Jude": "JUD",
    "Rev": "REV",
}

# Filler: matched words here carry no weight, because every verse has them.
STOP = set("""
a an the and or but if then so as of to in into on upon at by for from with
that this these those there their them they he she it his her him its i me my
we us our you your ye thou thee thy thine shall will would should may might must
can could do did doth dost done have hath hast had has am is are was were be been
being not no nor all any every each very more most also both same own out up
down over under again now here when where while because therefore thus than
which who whom whose what how unto upon among between through after before
""".split())
def book_table():
    """(assetIndex, id, name) from BibleMeta, in canonical order."""
    source = open(META, encoding="utf-8").read()
    rows = re.findall(r'BookInfo\((\d+),\s*"([A-Z0-9]{3})",\s*"([^"]+)"\)', source)
    books = [(int(asset), book_id, name) for asset, book_id, name in rows]
    if len(books) != 66:
        sys.exit("BibleMeta gave %d books, expected 66 - check the parser" % len(books))
    return books


def verse_texts(books):
    """{(book, chapter, verse): text}, using the app's own extraction rules."""
    texts = {}
    for asset, book_id, _ in books:
        path = os.path.join(USFM, "book_%d" % asset)
        if not os.path.exists(path):
            continue
        raw = open(path, encoding="utf-8").read()
        first = CHAPTER.search(raw)
        if not first:
            continue
        body = FOOTNOTE.sub(" ", raw[first.start():])
        body = STRUCTURAL.sub("", body)
        chapters = list(CHAPTER.finditer(body))
        for position, chapter in enumerate(chapters):
            end = chapters[position + 1].start() if position + 1 < len(chapters) else len(body)
            content = body[chapter.end():end]
            number = int(chapter.group(1))
            marks = list(VERSE.finditer(content))
            for slot, mark in enumerate(marks):
                stop = marks[slot + 1].start() if slot + 1 < len(marks) else len(content)
                text = MARKER_CLOSE.sub("", content[mark.end():stop])
                text = MARKER_OPEN.sub(" ", text)
                text = WHITESPACE.sub(" ", GLUED.sub(" ", text)).strip()
                if text:
                    texts[(book_id, number, int(mark.group(1)))] = text
    return texts


def parse_point(ref):
    """'Gen.1.1' -> ('GEN', 1, 1)."""
    parts = ref.split(".")
    if len(parts) != 3:
        return None
    book = CODES.get(parts[0])
    if book is None:
        return None
    try:
        return book, int(parts[1]), int(parts[2])
    except ValueError:
        return None


def parse_refs(ref):
    """'Isa.53.4-Isa.53.6' -> the three verses it names, in order."""
    ends = ref.split("-")
    first = parse_point(ends[0])
    if first is None:
        return []
    if len(ends) == 1:
        return [first]
    last = parse_point(ends[1])
    if last is None or last[:2] != first[:2] or last[2] < first[2]:
        return [first]
    return [(first[0], first[1], verse) for verse in range(first[2], last[2] + 1)]


def load_pairs(path):
    """Every (quoting verse, quoted verse) the reference data names.

    A reference to a range is expanded, because the words an author quotes are
    often one verse inside it - 1 Peter 2:24's "by whose stripes ye were healed"
    is the fifth verse of Isaiah 53:4-6, not the fourth.
    """
    pairs = []
    with open(path, encoding="utf-8") as handle:
        next(handle, None)
        for line in handle:
            parts = line.rstrip("\n").split("\t")
            if len(parts) < 2:
                continue
            sources = parse_refs(parts[0])
            if len(sources) != 1:
                continue
            for target in parse_refs(parts[1]):
                if target != sources[0]:
                    pairs.append((sources[0], target))
    return pairs


def proper_nouns(texts):
    """Words the KJV capitalises mid-sentence: names, which prove nothing.

    "the Hivites, and the Jebusites" is a rare run of words and still says
    nothing about quotation - a list of nations reads the same everywhere. Only
    capitals *inside* a sentence count, so "Behold" at the start of one is not
    mistaken for a name.
    """
    seen, capitalised = collections.Counter(), collections.Counter()
    for text in texts.values():
        for match in re.finditer(r"[A-Za-z][A-Za-z'\u2019]*", text):
            word = match.group(0)
            seen[word.lower()] += 1
            before = text[:match.start()].rstrip()
            if before and before[-1] not in ".!?:;\u2014" and word[0].isupper():
                capitalised[word.lower()] += 1
    return {word for word, count in seen.items()
            if count >= 3 and capitalised[word] / count >= 0.7}


def analyse(text):
    """The verse's words, and where each one sits in the text."""
    lowered = text.lower()
    if len(lowered) != len(text):
        lowered = text  # non-ASCII made it longer; keep the offsets honest
    words, spans = [], []
    for match in WORD.finditer(lowered):
        words.append(match.group(0))
        spans.append((match.start(), match.end()))
    return words, spans


def fetch():
    """Downloads the reference data once and returns the text file inside it."""
    cache = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".xrefs")
    os.makedirs(cache, exist_ok=True)
    target = os.path.join(cache, "cross_references.txt")
    if os.path.exists(target):
        return target
    print("downloading %s" % URL)
    blob = urllib.request.urlopen(URL).read()
    with zipfile.ZipFile(io.BytesIO(blob)) as archive:
        name = next(name for name in archive.namelist() if name.endswith(".txt"))
        open(target, "wb").write(archive.read(name))
    return target


LICENCE_TEXT = """\
Cross-reference data in this file and in assets/xrefs comes from openbible.info
(https://www.openbible.info/labs/cross-references/), which is licensed under the
Creative Commons Attribution 4.0 licence (CC BY 4.0).

The app uses it only as a starting point: the quotations that ship in assets/xrefs
are the subset of those references whose two verses share a contiguous run of
distinctive words, which is how a thematic cross-reference is told apart from a
quotation. That filtering, and the direction rule (the underline is only ever put
on the later book), are this project's own work.

The verse text itself is the KJV, which is public domain in most of the world.
"""


def display(verse, names):
    return "%s %d:%d" % (names[verse[0]], verse[1], verse[2])


def main():
    books = book_table()
    order = {book_id: index for index, (_, book_id, _) in enumerate(books)}
    names = {book_id: name for _, book_id, name in books}
    # Canonical order is not composition order, and in the Gospels that matters.
    # Mark was written first and both Matthew and Luke used him, so a phrase they
    # share belongs to Mark's account and is quoted *by* the others - marking it
    # in Mark would say the opposite. Matthew and Luke are therefore ranked equal
    # (so a phrase they share is left alone rather than attributed to one of
    # them), while John stays after all three, where he belongs.
    order["MAT"] = order["LUK"]

    texts = verse_texts(books)
    print("verses parsed          : %d" % len(texts))

    words_of, spans_of = {}, {}
    document_frequency = collections.Counter()
    for key, text in texts.items():
        words, spans = analyse(text)
        words_of[key], spans_of[key] = words, spans
        document_frequency.update(set(words))
    print("distinct word forms    : %d" % len(document_frequency))

    path = sys.argv[1] if len(sys.argv) > 1 else fetch()
    pairs = load_pairs(path)
    print("reference pairs        : %d" % len(pairs))

    rare_df, min_run, min_content, formula_limit = 400, 4, 2, 6
    names_in_corpus = proper_nouns(texts)
    print("names in the text      : %d" % len(names_in_corpus))

    # How often each four-word run occurs anywhere in the Bible. A phrase that
    # repeats - "gathered unto his people" - is the KJV's own boilerplate, not a
    # writer reaching back for an older text, however rare its single words are.
    four_grams = collections.Counter()
    for words in words_of.values():
        for start in range(len(words) - 3):
            four_grams[tuple(words[start:start + 4])] += 1

    found = collections.defaultdict(list)
    rejected = []
    counts = collections.Counter()

    for source, target in pairs:
        if source not in words_of or target not in words_of:
            continue
        # The mark belongs on the verse that does the quoting - the later book.
        if order[source[0]] <= order[target[0]]:
            counts["not later"] += 1
            continue
        left, right = words_of[source], words_of[target]
        if len(set(left) & set(right)) < 3:
            counts["too little shared"] += 1
            continue
        block = difflib.SequenceMatcher(None, left, right, autojunk=False) \
            .find_longest_match(0, len(left), 0, len(right))
        if block.size < min_run:
            counts["run too short"] += 1
            continue
        run = left[block.a:block.a + block.size]
        if four_grams[tuple(run[:4])] > formula_limit:
            counts["formulaic"] += 1
            if len(rejected) < 12:
                rejected.append((source, target, " ".join(run)))
            continue
        content = {word for word in run
                   if word not in STOP and word not in names_in_corpus}
        rare = {word for word in content if document_frequency[word] <= rare_df}
        if len(content) < min_content or (not rare and block.size < 8):
            counts["not distinctive"] += 1
            if len(rejected) < 12:
                rejected.append((source, target, " ".join(run)))
            continue
        text = texts[source]
        phrase = text[spans_of[source][block.a][0]:spans_of[source][block.a + block.size - 1][1]]
        found[source].append((
            block.a, block.a + block.size, display(target, names), phrase,
            target[0], target[1], target[2],
        ))

    # Several references can quote overlapping words of one verse - Hebrews 1:5
    # quotes two Psalms in the same sentence - so overlaps are kept, not merged.
    # They are drawn in the same colour, so a doubled underline is invisible, but
    # an exact repeat of the same phrase from the same source is stored once.
    chosen = {}
    for verse, items in found.items():
        unique = {}
        for item in items:
            unique.setdefault((item[0], item[1], item[2]), item)
        chosen[verse] = sorted(unique.values())

    lines = [
        "# Quotations only: words an author takes from an earlier passage.",
        "# Cross-reference data from openbible.info, CC BY 4.0 - see xrefs.LICENSE.",
        "# BOOK\tchapter\tverse\tsource BOOK\tchapter\tverse\treference\tquoted phrase",
    ]
    for verse in sorted(chosen, key=lambda key: (order[key[0]], key[1], key[2])):
        for _, _, reference, phrase, source_book, source_chapter, source_verse in chosen[verse]:
            lines.append(
                "%s\t%d\t%d\t%s\t%d\t%d\t%s\t%s" % (
                    verse[0], verse[1], verse[2],
                    source_book, source_chapter, source_verse,
                    reference, phrase))
    open(OUT, "w", encoding="utf-8").write("\n".join(lines) + "\n")
    open(LICENCE, "w", encoding="utf-8").write(LICENCE_TEXT)

    written = len(lines) - 3
    print()
    print("quotations kept        : %d, in %d verses across %d books"
          % (written, len(chosen), len({verse[0] for verse in chosen})))
    print("written to             : %s (%.0f KB)"
          % (os.path.relpath(OUT, ROOT), os.path.getsize(OUT) / 1024.0))
    for reason in ["not later", "too little shared", "run too short", "formulaic",
                   "not distinctive"]:
        print("dropped, %-18s: %d" % (reason, counts[reason]))

    checks = [
        (("MAT", 1, 23), ("ISA", 7, 14)),
        (("MAT", 4, 4), ("DEU", 8, 3)),
        (("MAT", 2, 18), ("JER", 31, 15)),
        (("MRK", 1, 3), ("ISA", 40, 3)),
        (("JHN", 19, 24), ("PSA", 22, 18)),
        (("ACT", 2, 17), ("JOL", 2, 28)),
        (("ROM", 3, 10), ("PSA", 14, 1)),
        (("1PE", 2, 24), ("ISA", 53, 5)),
        (("HEB", 1, 5), ("PSA", 2, 7)),
    ]
    print()
    print("spot checks - known quotations")
    passes = 0
    for source, target in checks:
        wanted = display(target, names)
        hit = next((item for item in chosen.get(source, []) if item[2] == wanted), None)
        label = "%s -> %s" % (display(source, names), wanted)
        if hit:
            passes += 1
            print("  found  %-30s %r" % (label, hit[3]))
        else:
            print("  MISSED %-30s (%d underlines in that verse)"
                  % (label, len(chosen.get(source, []))))
    print("  %d of %d found" % (passes, len(checks)))

    print()
    print("sample rejections - shared words, but not a quotation:")
    for source, target, run in rejected:
        print("  %-14s vs %-14s %r"
              % (display(source, names), display(target, names), run[:52]))


if __name__ == "__main__":
    main()

