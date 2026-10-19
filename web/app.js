'use strict';

/*
 * KJV Reader, in the browser.
 *
 * No framework, no build step, no backend: the page reads the very same assets
 * the Android app ships (../app/src/main/assets/usfm/kjv) and keeps everything
 * else in localStorage. The parsers below are ports of UsfmParser.kt and
 * StrongsRepository.kt, kept close to the original where it matters, so a verse
 * reads the same in both.
 */

const DATA = new URL('../app/src/main/assets/usfm/kjv/', document.baseURI).href;

/* Canonical order, with the asset index each book's files are named after
   (there is no book_40) and the chapter count that goes with it. */
const BOOKS = [
  { id: 'GEN', name: 'Genesis', ai: 1, chapters: 50, ot: true },
  { id: 'EXO', name: 'Exodus', ai: 2, chapters: 40, ot: true },
  { id: 'LEV', name: 'Leviticus', ai: 3, chapters: 27, ot: true },
  { id: 'NUM', name: 'Numbers', ai: 4, chapters: 36, ot: true },
  { id: 'DEU', name: 'Deuteronomy', ai: 5, chapters: 34, ot: true },
  { id: 'JOS', name: 'Joshua', ai: 6, chapters: 24, ot: true },
  { id: 'JDG', name: 'Judges', ai: 7, chapters: 21, ot: true },
  { id: 'RUT', name: 'Ruth', ai: 8, chapters: 4, ot: true },
  { id: '1SA', name: '1 Samuel', ai: 9, chapters: 31, ot: true },
  { id: '2SA', name: '2 Samuel', ai: 10, chapters: 24, ot: true },
  { id: '1KI', name: '1 Kings', ai: 11, chapters: 22, ot: true },
  { id: '2KI', name: '2 Kings', ai: 12, chapters: 25, ot: true },
  { id: '1CH', name: '1 Chronicles', ai: 13, chapters: 29, ot: true },
  { id: '2CH', name: '2 Chronicles', ai: 14, chapters: 36, ot: true },
  { id: 'EZR', name: 'Ezra', ai: 15, chapters: 10, ot: true },
  { id: 'NEH', name: 'Nehemiah', ai: 16, chapters: 13, ot: true },
  { id: 'EST', name: 'Esther', ai: 17, chapters: 10, ot: true },
  { id: 'JOB', name: 'Job', ai: 18, chapters: 42, ot: true },
  { id: 'PSA', name: 'Psalms', ai: 19, chapters: 150, ot: true },
  { id: 'PRO', name: 'Proverbs', ai: 20, chapters: 31, ot: true },
  { id: 'ECC', name: 'Ecclesiastes', ai: 21, chapters: 12, ot: true },
  { id: 'SNG', name: 'Song of Solomon', ai: 22, chapters: 8, ot: true },
  { id: 'ISA', name: 'Isaiah', ai: 23, chapters: 66, ot: true },
  { id: 'JER', name: 'Jeremiah', ai: 24, chapters: 52, ot: true },
  { id: 'LAM', name: 'Lamentations', ai: 25, chapters: 5, ot: true },
  { id: 'EZK', name: 'Ezekiel', ai: 26, chapters: 48, ot: true },
  { id: 'DAN', name: 'Daniel', ai: 27, chapters: 12, ot: true },
  { id: 'HOS', name: 'Hosea', ai: 28, chapters: 14, ot: true },
  { id: 'JOL', name: 'Joel', ai: 29, chapters: 3, ot: true },
  { id: 'AMO', name: 'Amos', ai: 30, chapters: 9, ot: true },
  { id: 'OBA', name: 'Obadiah', ai: 31, chapters: 1, ot: true },
  { id: 'JON', name: 'Jonah', ai: 32, chapters: 4, ot: true },
  { id: 'MIC', name: 'Micah', ai: 33, chapters: 7, ot: true },
  { id: 'NAM', name: 'Nahum', ai: 34, chapters: 3, ot: true },
  { id: 'HAB', name: 'Habakkuk', ai: 35, chapters: 3, ot: true },
  { id: 'ZEP', name: 'Zephaniah', ai: 36, chapters: 3, ot: true },
  { id: 'HAG', name: 'Haggai', ai: 37, chapters: 2, ot: true },
  { id: 'ZEC', name: 'Zechariah', ai: 38, chapters: 14, ot: true },
  { id: 'MAL', name: 'Malachi', ai: 39, chapters: 4, ot: true },
  { id: 'MAT', name: 'Matthew', ai: 41, chapters: 28, ot: false },
  { id: 'MRK', name: 'Mark', ai: 42, chapters: 16, ot: false },
  { id: 'LUK', name: 'Luke', ai: 43, chapters: 24, ot: false },
  { id: 'JHN', name: 'John', ai: 44, chapters: 21, ot: false },
  { id: 'ACT', name: 'Acts', ai: 45, chapters: 28, ot: false },
  { id: 'ROM', name: 'Romans', ai: 46, chapters: 16, ot: false },
  { id: '1CO', name: '1 Corinthians', ai: 47, chapters: 16, ot: false },
  { id: '2CO', name: '2 Corinthians', ai: 48, chapters: 13, ot: false },
  { id: 'GAL', name: 'Galatians', ai: 49, chapters: 6, ot: false },
  { id: 'EPH', name: 'Ephesians', ai: 50, chapters: 6, ot: false },
  { id: 'PHP', name: 'Philippians', ai: 51, chapters: 4, ot: false },
  { id: 'COL', name: 'Colossians', ai: 52, chapters: 4, ot: false },
  { id: '1TH', name: '1 Thessalonians', ai: 53, chapters: 5, ot: false },
  { id: '2TH', name: '2 Thessalonians', ai: 54, chapters: 3, ot: false },
  { id: '1TI', name: '1 Timothy', ai: 55, chapters: 6, ot: false },
  { id: '2TI', name: '2 Timothy', ai: 56, chapters: 4, ot: false },
  { id: 'TIT', name: 'Titus', ai: 57, chapters: 3, ot: false },
  { id: 'PHM', name: 'Philemon', ai: 58, chapters: 1, ot: false },
  { id: 'HEB', name: 'Hebrews', ai: 59, chapters: 13, ot: false },
  { id: 'JAS', name: 'James', ai: 60, chapters: 5, ot: false },
  { id: '1PE', name: '1 Peter', ai: 61, chapters: 5, ot: false },
  { id: '2PE', name: '2 Peter', ai: 62, chapters: 3, ot: false },
  { id: '1JN', name: '1 John', ai: 63, chapters: 5, ot: false },
  { id: '2JN', name: '2 John', ai: 64, chapters: 1, ot: false },
  { id: '3JN', name: '3 John', ai: 65, chapters: 1, ot: false },
  { id: 'JUD', name: 'Jude', ai: 66, chapters: 1, ot: false },
  { id: 'REV', name: 'Revelation', ai: 67, chapters: 22, ot: false }
];

const DEFAULTS = { size: 18, spacing: 1.35, serif: false, italics: true, footnotes: true, theme: 'default' };
const HL_COLORS = ['yellow', 'green', 'blue', 'red'];

/* ------------------------------------------------------------------ storage */

function readJSON(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    return raw ? JSON.parse(raw) : fallback;
  } catch (e) {
    return fallback;
  }
}

function writeJSON(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value));
  } catch (e) {
    /* private mode or quota: reading still works, the note is simply not kept */
  }
}

let prefs = Object.assign({}, DEFAULTS, readJSON('kjv.prefs', {}));
let highlights = readJSON('kjv.highlights', {});
let notes = readJSON('kjv.notes', {});

const keyOf = (book, chapter, verse) => book.id + ' ' + chapter + ':' + verse;

/* -------------------------------------------------------------- the parsers */

const STRUCTURAL_LINE = /^\\[pqdmb][a-z0-9]*[^\n]*$/gm;
const GLUED_SENTENCE = /(?<=[.!?])(?=[A-Z])/g;
const FOOTNOTE_REFERENCE = /\\fr\s+(.*?)\s*\\fr\*/s;
const FOOTNOTE_QUOTE = /\\fq\s+(.*?)\s*\\fq\*/s;
const FOOTNOTE_TEXT = /\\ft\s+(.*?)\s*\\ft\*/s;

function isSpace(c) {
  return c === ' ' || c === '\n' || c === '\r' || c === '\t' || c === '\u00A0';
}

/* One verse, marker-free, with the supplied words and the margin notes kept.
   Port of UsfmParser.clean(): the offsets are measured as the text is built, so
   they are offsets in the finished verse. */
function cleanVerse(source) {
  const raw = source.replace(GLUED_SENTENCE, ' ');
  const italics = [];
  const footnotes = [];
  let out = '';
  let italicStart = -1;

  const space = () => {
    if (out.length === 0 || out.endsWith(' ')) return;
    out += ' ';
  };
  const appendRaw = (from, to) => {
    let i = from;
    while (i < to) {
      if (isSpace(raw[i])) {
        let j = i;
        while (j < to && isSpace(raw[j])) j++;
        space();
        i = j;
      } else {
        out += raw[i];
        i++;
      }
    }
  };
  const closeItalic = () => {
    if (italicStart < 0 || out.length <= italicStart) return;
    italics.push(italicStart, out.length);
  };

  const marker = /\\[a-zA-Z+][a-zA-Z0-9]*\*?/g;
  let pos = 0;
  while (pos < raw.length) {
    marker.lastIndex = pos;
    const m = marker.exec(raw);
    if (!m) {
      appendRaw(pos, raw.length);
      break;
    }
    appendRaw(pos, m.index);
    const token = m[0];
    if (token === '\\add') {
      space();
      italicStart = out.length;
    } else if (token === '\\add*') {
      closeItalic();
      italicStart = -1;
    } else if (token === '\\f') {
      const end = raw.indexOf('\\f*', m.index + 2);
      const stop = end < 0 ? raw.length : end;
      space();
      footnotes.push(readFootnote(raw.slice(m.index + 2, stop), out.length));
      if (end < 0) break;
      pos = end + 3;
      continue;
    } else if (!token.endsWith('*')) {
      space();
    }
    pos = m.index + token.length;
  }
  closeItalic();

  const text = out.replace(/\s+$/, '');
  for (const note of footnotes) {
    if (note.anchor > text.length) note.anchor = text.length;
  }
  return { text, italics, footnotes };
}

function readFootnote(body, anchor) {
  const ref = FOOTNOTE_REFERENCE.exec(body);
  const quote = FOOTNOTE_QUOTE.exec(body);
  const text = FOOTNOTE_TEXT.exec(body);
  return {
    anchor,
    reference: ref ? ref[1].trim() : '',
    quote: quote && quote[1].trim() ? quote[1].trim() : null,
    text: text ? text[1].trim() : ''
  };
}

function parseVerses(content) {
  const re = /\\v\s+(\d+)\s?/g;
  const found = [];
  let m;
  while ((m = re.exec(content))) {
    found.push({ number: Number(m[1]), start: m.index, end: m.index + m[0].length });
  }
  const verses = [];
  for (let i = 0; i < found.length; i++) {
    const from = found[i].end;
    const to = i + 1 < found.length ? found[i + 1].start : content.length;
    if (to <= from) continue;
    const cleaned = cleanVerse(content.slice(from, to));
    if (cleaned.text) {
      verses.push({
        number: found[i].number,
        text: cleaned.text,
        italics: cleaned.italics,
        footnotes: cleaned.footnotes
      });
    }
  }
  return verses;
}

function parseBook(usfm) {
  const first = usfm.search(/\\c\s+\d+/);
  if (first < 0) return [];
  const body = usfm.slice(first).replace(STRUCTURAL_LINE, '');
  const re = /\\c\s+(\d+)/g;
  const marks = [];
  let m;
  while ((m = re.exec(body))) {
    marks.push({ number: Number(m[1]), start: m.index, end: m.index + m[0].length });
  }
  const chapters = [];
  for (let i = 0; i < marks.length; i++) {
    const from = marks[i].end;
    const to = i + 1 < marks.length ? marks[i + 1].start : body.length;
    chapters.push({
      number: marks[i].number,
      verses: to > from ? parseVerses(body.slice(from, to)) : []
    });
  }
  return chapters;
}

/* ------------------------------------------------------- words and lexicon */

const SPAN_LIMIT = 5;
const MATCH_WINDOW = 16;
const UNIT = '\u001F';

function normalise(raw) {
  let out = '';
  for (const c of raw) {
    if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
      out += c.toLowerCase();
    } else if (c === "'") {
      out += c;
    }
  }
  return out;
}

function normaliseEntry(raw) {
  return raw.split(/[ \t]/).map(normalise).filter(Boolean).join(' ');
}

function matchAt(normal, from, target) {
  let key = '';
  for (let span = 1; span <= SPAN_LIMIT; span++) {
    const next = from + span - 1;
    if (next >= normal.length) break;
    if (!normal[next]) continue;
    key = key ? key + ' ' + normal[next] : normal[next];
    if (key === target) return span;
  }
  return 0;
}

function matchesWithin(normal, from, target) {
  const last = Math.min(from + MATCH_WINDOW, normal.length - 1);
  for (let i = from + 1; i <= last; i++) {
    if (matchAt(normal, i, target) > 0) return true;
  }
  return false;
}

/* A word gets a number only when it matches an entry exactly, word for word -
   never a plausible neighbour. Port of StrongsRepository.align(). */
function alignVerse(text, entries) {
  const tokens = text.split(' ').filter(Boolean);
  if (!tokens.length || !entries.length) return [];
  const normal = tokens.map(normalise);

  const present = new Set();
  for (let i = 0; i < normal.length; i++) {
    let key = '';
    for (let span = 1; span <= SPAN_LIMIT; span++) {
      const next = i + span - 1;
      if (next >= normal.length) break;
      if (!normal[next]) continue;
      key = key ? key + ' ' + normal[next] : normal[next];
      present.add(key);
    }
  }

  const words = [];
  let word = 0;
  let entry = 0;
  while (word < normal.length && entry < entries.length) {
    const target = normaliseEntry(entries[entry].word);
    if (!target || !present.has(target)) {
      entry++;
      continue;
    }
    const span = matchAt(normal, word, target);
    if (span > 0) {
      words.push({
        text: tokens.slice(word, word + span).join(' '),
        start: word,
        end: word + span,
        strongs: entries[entry].strongs
      });
      word += span;
      entry++;
      continue;
    }
    if (matchesWithin(normal, word, target)) word++;
    else entry++;
  }
  return words;
}

const TRIM = ' ,.;:!?()"\u201C\u201D\u2019';

function trimWord(raw) {
  let start = 0;
  let end = raw.length;
  while (start < end && TRIM.includes(raw[start])) start++;
  while (end > start && TRIM.includes(raw[end - 1])) end--;
  return raw.slice(start, end);
}

function wordIndexAt(text, offset) {
  let index = 0;
  let start = 0;
  while (start <= text.length) {
    const space = text.indexOf(' ', start);
    const end = space < 0 ? text.length : space;
    if (end > start) {
      if (offset <= end) return index;
      index++;
    }
    if (space < 0) break;
    start = space + 1;
  }
  return -1;
}

function wordAt(text, index) {
  let i = 0;
  let start = 0;
  while (start <= text.length) {
    const space = text.indexOf(' ', start);
    const end = space < 0 ? text.length : space;
    if (end > start) {
      if (i === index) return trimWord(text.slice(start, end));
      i++;
    }
    if (space < 0) break;
    start = space + 1;
  }
  return '';
}

/* `3:16` then, tab separated, `world<US>G2889<US>` per word. */
function parseWords(text) {
  const map = new Map();
  for (const line of text.split('\n')) {
    const firstTab = line.indexOf('\t');
    if (firstTab < 3) continue;
    const colon = line.indexOf(':');
    if (colon < 1 || colon > firstTab) continue;
    const chapter = Number(line.slice(0, colon));
    const verse = Number(line.slice(colon + 1, firstTab));
    if (!chapter || !verse) continue;
    const list = [];
    for (const chunk of line.slice(firstTab + 1).split('\t')) {
      const parts = chunk.split(UNIT);
      if (!parts[0]) continue;
      list.push({ word: parts[0], strongs: parts[1] || '' });
    }
    if (list.length) map.set(chapter * 1000 + verse, list);
  }
  return map;
}

function lexiconEntry(tail) {
  const first = tail.indexOf('\t');
  if (first < 0) return { original: tail, transliteration: '', definition: '', kjv: null };
  const second = tail.indexOf('\t', first + 1);
  const original = tail.slice(0, first);
  const transliteration = second < 0 ? tail.slice(first + 1) : tail.slice(first + 1, second);
  const gloss = second < 0 ? '' : tail.slice(second + 1);
  const mark = gloss.indexOf('\u2014 KJV:');
  if (mark >= 0) {
    return {
      original,
      transliteration,
      definition: gloss.slice(0, mark).trim().replace(/[\s:;,\-]+$/, ''),
      kjv: gloss.slice(mark + 6).trim()
    };
  }
  if (gloss.startsWith('KJV:')) {
    return { original, transliteration, definition: '', kjv: gloss.slice(4).trim() };
  }
  return { original, transliteration, definition: gloss.trim(), kjv: null };
}

function websterKey(word) {
  let out = '';
  for (const c of word) {
    if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c === "'" || c === '-') out += c;
    else if (c >= 'A' && c <= 'Z') out += c.toLowerCase();
  }
  return out;
}

/* -------------------------------------------------------------- data access */

const rawCache = new Map();    /* url -> text */
const bookCache = new Map();   /* asset index -> chapters */
const wordsCache = new Map();  /* asset index -> Map */
let lexicon = null;
let webster = null;

async function fetchText(url) {
  const hit = rawCache.get(url);
  if (hit !== undefined) return hit;
  const response = await fetch(url);
  if (!response.ok) throw new Error(url + ' \u2014 HTTP ' + response.status);
  const text = await response.text();
  rawCache.set(url, text);
  return text;
}

async function loadBook(book) {
  if (bookCache.has(book.ai)) return bookCache.get(book.ai);
  const chapters = parseBook(await fetchText(DATA + 'book_' + book.ai));
  bookCache.set(book.ai, chapters);
  if (bookCache.size > 3) bookCache.delete(bookCache.keys().next().value);
  return chapters;
}

async function loadWords(book) {
  if (wordsCache.has(book.ai)) return wordsCache.get(book.ai);
  let map = new Map();
  try {
    map = parseWords(await fetchText(DATA + 'words_' + book.ai));
  } catch (e) {
    /* no word data for this book: reading still works */
  }
  wordsCache.set(book.ai, map);
  if (wordsCache.size > 3) wordsCache.delete(wordsCache.keys().next().value);
  return map;
}

async function loadLexicon() {
  if (lexicon) return lexicon;
  const map = new Map();
  for (const line of (await fetchText(DATA + 'lexicon')).split('\n')) {
    const tab = line.indexOf('\t');
    if (tab <= 0) continue;
    map.set(line.slice(0, tab), line.slice(tab + 1));
  }
  lexicon = map;
  return map;
}

async function loadWebster() {
  if (webster) return webster;
  const map = new Map();
  for (const line of (await fetchText(DATA + 'webster')).split('\n')) {
    const tab = line.indexOf('\t');
    if (tab <= 0) continue;
    map.set(line.slice(0, tab), line.slice(tab + 1).split(UNIT).join('\n'));
  }
  webster = map;
  return map;
}

/* ---------------------------------------------------------------- the page */

const content = document.getElementById('content');
const where = document.getElementById('where');
const whereLabel = document.getElementById('bookChapter');
const status = document.getElementById('status');

const state = { book: BOOKS[0], chapter: 1, verses: new Map(), words: new Map() };

function say(message) {
  status.hidden = !message;
  status.textContent = message || '';
}

function applyPrefs() {
  const root = document.documentElement;
  root.dataset.theme = prefs.theme;
  root.style.setProperty('--reading-size', prefs.size + 'px');
  root.style.setProperty('--reading-spacing', String(prefs.spacing));
  root.style.setProperty('--reading-font', prefs.serif
    ? 'Georgia, "Iowan Old Style", "Palatino Linotype", serif'
    : 'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif');
  document.body.classList.toggle('no-italics', !prefs.italics);
  document.body.classList.toggle('no-footnotes', !prefs.footnotes);
}

/* ------------------------------------------------------------ verse drawing */

function textFragment(verse) {
  const fragment = document.createDocumentFragment();
  const points = [];
  for (let i = 0; i < verse.italics.length; i += 2) {
    points.push({ at: verse.italics[i], kind: 'open' });
    points.push({ at: verse.italics[i + 1], kind: 'close' });
  }
  if (prefs.footnotes) {
    verse.footnotes.forEach((note, index) => points.push({ at: note.anchor, kind: 'note', note, index }));
  }
  /* A close sits before an open at the same offset, so "supplied" runs do not
     swallow each other. */
  points.sort((a, b) => a.at - b.at || (a.kind === 'close' ? -1 : 1));

  let parent = fragment;
  let italic = null;
  let last = 0;

  const emit = (to) => {
    if (to <= last) return;
    parent.append(document.createTextNode(verse.text.slice(last, to)));
    last = to;
  };

  for (const point of points) {
    emit(Math.max(last, Math.min(point.at, verse.text.length)));
    if (point.kind === 'open') {
      if (!italic) {
        italic = document.createElement('i');
        fragment.append(italic);
        parent = italic;
      }
    } else if (point.kind === 'close') {
      if (italic) {
        parent = fragment;
        italic = null;
      }
    } else {
      const sup = document.createElement('sup');
      sup.className = 'fn';
      sup.dataset.fn = String(point.index);
      sup.textContent = '\u2020';
      sup.title = point.note.text || point.note.reference || 'Note';
      parent.append(sup);
    }
  }
  emit(verse.text.length);
  return fragment;
}

function verseNode(verse) {
  const p = document.createElement('p');
  p.className = 'verse';
  p.dataset.v = String(verse.number);

  const number = document.createElement('span');
  number.className = 'num';
  number.textContent = verse.number;
  p.append(number, textFragment(verse));

  const key = keyOf(state.book, state.chapter, verse.number);
  const colour = highlights[key];
  if (colour) p.classList.add('hl-' + colour);
  if (notes[key]) p.classList.add('has-note');
  return p;
}

async function render(pushHash, keepScroll) {
  say('');
  whereLabel.textContent = state.book.name + ' ' + state.chapter;

  let chapters;
  try {
    chapters = await loadBook(state.book);
  } catch (error) {
    say('Could not load ' + state.book.name + ': ' + error.message +
        ' \u2014 the page has to be served from the top of the repository.');
    return;
  }
  const chapter = chapters.find((c) => c.number === state.chapter);
  if (!chapter || !chapter.verses.length) {
    const empty = document.createElement('p');
    empty.className = 'empty';
    empty.textContent = 'This chapter could not be read.';
    content.replaceChildren(empty);
    return;
  }

  state.verses = new Map(chapter.verses.map((v) => [v.number, v]));

  let raw = new Map();
  try {
    raw = await loadWords(state.book);
  } catch (e) { /* no word data */ }
  state.words = new Map();
  for (const verse of chapter.verses) {
    const entries = raw.get(state.chapter * 1000 + verse.number);
    if (entries) state.words.set(verse.number, alignVerse(verse.text, entries));
  }

  content.replaceChildren(...chapter.verses.map(verseNode));
  if (!keepScroll) window.scrollTo(0, 0);
  if (pushHash) {
    const hash = '#/' + state.book.id + '/' + state.chapter;
    if (location.hash !== hash) history.pushState(null, '', hash);
  }
  try {
    localStorage.setItem('kjv.last', state.book.id + '/' + state.chapter);
  } catch (e) { /* ignore */ }
}

function step(delta) {
  const bookIndex = BOOKS.indexOf(state.book);
  let chapter = state.chapter + delta;
  let book = state.book;
  if (chapter < 1) {
    if (bookIndex === 0) return;
    book = BOOKS[bookIndex - 1];
    chapter = book.chapters;
  } else if (chapter > book.chapters) {
    if (bookIndex === BOOKS.length - 1) return;
    book = BOOKS[bookIndex + 1];
    chapter = 1;
  }
  go(book, chapter);
}

function go(book, chapter) {
  state.book = book;
  state.chapter = Math.min(Math.max(1, chapter), book.chapters);
  render(true);
}

/* ------------------------------------------------------------------ sheets */

let openSheet = null;

function closeSheet() {
  if (!openSheet) return;
  const sheet = openSheet;
  openSheet = null;
  sheet.classList.remove('open');
  setTimeout(() => sheet.remove(), 180);
}

function sheet(title, options = {}) {
  closeSheet();
  const backdrop = document.createElement('div');
  backdrop.className = 'sheet-backdrop';

  const card = document.createElement('div');
  card.className = 'sheet' + (options.tall ? ' tall' : '');
  card.setAttribute('role', 'dialog');
  card.setAttribute('aria-modal', 'true');

  const head = document.createElement('div');
  head.className = 'sheet-head';
  const heading = document.createElement('h2');
  heading.textContent = title;
  const done = document.createElement('button');
  done.type = 'button';
  done.className = 'link';
  done.textContent = 'Done';
  done.addEventListener('click', closeSheet);
  head.append(heading, done);

  const body = document.createElement('div');
  body.className = 'sheet-body';

  card.append(head, body);
  backdrop.append(card);
  backdrop.addEventListener('click', (event) => {
    if (event.target === backdrop) closeSheet();
  });
  document.body.append(backdrop);
  requestAnimationFrame(() => backdrop.classList.add('open'));
  openSheet = backdrop;
  return body;
}

/* -------------------------------------------------------------- word study */

async function wordStudy(verse, index) {
  const list = state.words.get(verse.number) || [];
  const held = list.find((w) => index >= w.start && index < w.end);
  const label = held ? trimWord(held.text) : wordAt(verse.text, index);
  const id = held && held.strongs ? held.strongs : null;
  const reference = state.book.name + ' ' + state.chapter + ':' + verse.number;

  const body = sheet(label || 'Word');

  const head = document.createElement('div');
  head.className = 'ws-head';
  const word = document.createElement('div');
  word.className = 'ws-word';
  word.textContent = label;
  const ref = document.createElement('div');
  ref.className = 'ws-ref';
  ref.textContent = reference;
  head.append(word, ref);
  body.append(head);

  const notice = document.createElement('p');
  notice.className = 'muted';
  notice.textContent = 'Looking it up\u2026';
  body.append(notice);

  let english = null;
  try {
    english = (await loadWebster()).get(websterKey(label)) || null;
  } catch (e) { /* no dictionary */ }

  let entry = null;
  if (id) {
    try {
      const map = await loadLexicon();
      const tail = map.get(id);
      if (tail !== undefined) entry = lexiconEntry(tail);
    } catch (e) { /* no lexicon */ }
  }

  notice.remove();

  if (english) {
    const labelEl = document.createElement('h3');
    labelEl.textContent = "WEBSTER'S 1828";
    const text = document.createElement('p');
    text.className = 'entry';
    text.style.whiteSpace = 'pre-line';
    text.textContent = label.toUpperCase() + ', ' + english;
    const credit = document.createElement('p');
    credit.className = 'credit';
    credit.textContent = 'Webster\u2019s 1828 American Dictionary of the English Language \u00B7 CC BY-SA 4.0';
    body.append(labelEl, text, credit);
  }

  if (!id) {
    const labelEl = document.createElement('h3');
    labelEl.textContent = 'NO HEBREW OR GREEK';
    const text = document.createElement('p');
    text.textContent = 'This word has no Hebrew or Greek behind it. The KJV translators supplied it \u2014 the words in italics were added to carry the English sentence.';
    body.append(labelEl, text);
    if (!english) {
      const none = document.createElement('p');
      none.className = 'muted';
      none.textContent = 'Nothing is recorded for this word in either dictionary.';
      body.append(none);
    }
    return;
  }

  if (!entry) {
    const missing = document.createElement('p');
    missing.className = 'muted';
    missing.textContent = 'No entry for this number in the lexicon.';
    body.append(missing);
    return;
  }

  const original = document.createElement('div');
  original.className = 'ws-original';
  original.textContent = entry.original;
  const translit = document.createElement('div');
  translit.className = 'ws-translit';
  translit.textContent = entry.transliteration;
  const language = document.createElement('h3');
  language.textContent = (id.startsWith('H') ? 'HEBREW \u00B7 ' : 'GREEK \u00B7 ') + id;
  body.append(original, translit, language);

  if (entry.definition) {
    const definition = document.createElement('p');
    definition.textContent = entry.definition;
    body.append(definition);
  }
  if (entry.kjv) {
    const labelEl = document.createElement('h3');
    labelEl.textContent = 'KJV:';
    const kjv = document.createElement('p');
    kjv.className = 'muted';
    kjv.textContent = entry.kjv;
    body.append(labelEl, kjv);
  }
}

/* -------------------------------------------------------------- verse menu */

function openVerseMenu(verse) {
  const reference = state.book.name + ' ' + state.chapter + ':' + verse.number;
  const key = keyOf(state.book, state.chapter, verse.number);
  const body = sheet(reference);
  const redraw = () => { closeSheet(); render(false, true); };

  const swatches = document.createElement('div');
  swatches.className = 'swatches';

  const none = document.createElement('button');
  none.type = 'button';
  none.className = 'swatch none' + (highlights[key] ? '' : ' active');
  none.title = 'Remove highlight';
  none.addEventListener('click', () => {
    delete highlights[key];
    writeJSON('kjv.highlights', highlights);
    redraw();
  });
  swatches.append(none);

  for (const colour of HL_COLORS) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'swatch ' + colour + (highlights[key] === colour ? ' active' : '');
    button.title = colour.charAt(0).toUpperCase() + colour.slice(1) + ' highlight';
    button.addEventListener('click', () => {
      highlights[key] = colour;
      writeJSON('kjv.highlights', highlights);
      redraw();
    });
    swatches.append(button);
  }
  body.append(swatches);

  const noteLabel = document.createElement('h3');
  noteLabel.textContent = 'NOTE';
  const note = document.createElement('textarea');
  note.className = 'note-input';
  note.rows = 3;
  note.placeholder = 'Add a note\u2026';
  note.value = notes[key] || '';

  const save = document.createElement('button');
  save.type = 'button';
  save.className = 'wide';
  save.textContent = 'Save note';
  save.addEventListener('click', () => {
    const text = note.value.trim();
    if (text) notes[key] = text;
    else delete notes[key];
    writeJSON('kjv.notes', notes);
    redraw();
  });
  body.append(noteLabel, note, save);

  if (notes[key]) {
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.className = 'wide subtle';
    remove.textContent = 'Delete note';
    remove.addEventListener('click', () => {
      delete notes[key];
      writeJSON('kjv.notes', notes);
      redraw();
    });
    body.append(remove);
  }

  const copyVerse = document.createElement('button');
  copyVerse.type = 'button';
  copyVerse.className = 'wide';
  copyVerse.textContent = 'Copy verse';
  copyVerse.addEventListener('click', () => {
    copy(verse.text + '\n' + reference + ' (KJV)');
    closeSheet();
  });

  const copyReference = document.createElement('button');
  copyReference.type = 'button';
  copyReference.className = 'wide subtle';
  copyReference.textContent = 'Copy reference';
  copyReference.addEventListener('click', () => {
    copy(reference);
    closeSheet();
  });

  body.append(copyVerse, copyReference);
}

function copy(text) {
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text).catch(() => fallbackCopy(text));
  } else {
    fallbackCopy(text);
  }
}

function fallbackCopy(text) {
  const area = document.createElement('textarea');
  area.value = text;
  area.style.position = 'fixed';
  area.style.opacity = '0';
  document.body.append(area);
  area.select();
  try { document.execCommand('copy'); } catch (e) { /* nothing to do */ }
  area.remove();
}

/* ---------------------------------------------------------------- footnotes */

function openFootnotes(verse, index) {
  const note = verse.footnotes[index];
  if (!note) return;
  const body = sheet('Footnote \u00B7 ' + state.book.name + ' ' + state.chapter + ':' + verse.number);
  if (note.reference) {
    const reference = document.createElement('p');
    reference.className = 'muted';
    reference.textContent = note.reference;
    body.append(reference);
  }
  const text = document.createElement('p');
  text.textContent = note.text;
  body.append(text);
  if (note.quote) {
    const quote = document.createElement('p');
    quote.className = 'muted';
    quote.textContent = '\u201C' + note.quote + '\u201D';
    body.append(quote);
  }
}

/* --------------------------------------------------------------- pickers */

function openPicker() {
  const body = sheet('Choose a book', { tall: true });
  const tabs = document.createElement('div');
  tabs.className = 'tabs';
  const list = document.createElement('div');
  list.className = 'book-list';

  const fill = (ot) => {
    list.replaceChildren();
    for (const book of BOOKS.filter((b) => b.ot === ot)) {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'book' + (book === state.book ? ' current' : '');
      button.textContent = book.name;
      button.addEventListener('click', () => openChapters(book));
      list.append(button);
    }
  };

  for (const [label, ot] of [['OLD TESTAMENT', true], ['NEW TESTAMENT', false]]) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'tab';
    button.textContent = label;
    button.addEventListener('click', () => {
      for (const tab of tabs.children) tab.classList.remove('active');
      button.classList.add('active');
      fill(ot);
    });
    tabs.append(button);
  }
  tabs.firstChild.classList.add('active');

  body.append(tabs, list);
  fill(true);
}

function openChapters(book) {
  const body = sheet(book.name);
  const grid = document.createElement('div');
  grid.className = 'chapter-grid';
  for (let n = 1; n <= book.chapters; n++) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'chapter' + (book === state.book && n === state.chapter ? ' current' : '');
    button.textContent = n;
    button.addEventListener('click', () => {
      closeSheet();
      go(book, n);
    });
    grid.append(button);
  }
  body.append(grid);
}

/* ---------------------------------------------------------------- settings */

function openSettings() {
  const body = sheet('Reading settings');

  const sizeLabel = document.createElement('div');
  sizeLabel.className = 'row';
  sizeLabel.innerHTML = '<span>Text size</span><output></output>';
  const size = document.createElement('input');
  size.type = 'range';
  size.min = '14';
  size.max = '32';
  size.step = '1';
  size.value = String(prefs.size);
  const sizeOut = sizeLabel.querySelector('output');
  sizeOut.textContent = prefs.size + ' px';
  size.addEventListener('input', () => {
    prefs.size = Number(size.value);
    sizeOut.textContent = prefs.size + ' px';
    writeJSON('kjv.prefs', prefs);
    applyPrefs();
  });

  const spacingLabel = document.createElement('div');
  spacingLabel.className = 'row';
  spacingLabel.innerHTML = '<span>Line spacing</span><output></output>';
  const spacing = document.createElement('input');
  spacing.type = 'range';
  spacing.min = '1';
  spacing.max = '2.2';
  spacing.step = '0.05';
  spacing.value = String(prefs.spacing);
  const spacingOut = spacingLabel.querySelector('output');
  spacingOut.textContent = '\u00D7' + prefs.spacing.toFixed(2);
  spacing.addEventListener('input', () => {
    prefs.spacing = Number(spacing.value);
    spacingOut.textContent = '\u00D7' + prefs.spacing.toFixed(2);
    writeJSON('kjv.prefs', prefs);
    applyPrefs();
  });

  const themeLabel = document.createElement('div');
  themeLabel.className = 'row';
  themeLabel.innerHTML = '<span>Background</span>';
  const theme = document.createElement('select');
  for (const [value, name] of [['default', 'Default'], ['paper', 'Paper'], ['sepia', 'Sepia'],
                               ['grey', 'Grey'], ['night', 'Night']]) {
    const option = document.createElement('option');
    option.value = value;
    option.textContent = name;
    if (prefs.theme === value) option.selected = true;
    theme.append(option);
  }
  theme.addEventListener('change', () => {
    prefs.theme = theme.value;
    writeJSON('kjv.prefs', prefs);
    applyPrefs();
  });
  themeLabel.append(theme);

  body.append(sizeLabel, size, spacingLabel, spacing, themeLabel);

  for (const [name, field] of [['Serif', 'serif'],
                               ['Italic words (supplied by the translators)', 'italics'],
                               ['Footnotes', 'footnotes']]) {
    const row = document.createElement('label');
    row.className = 'row toggle';
    const span = document.createElement('span');
    span.textContent = name;
    const input = document.createElement('input');
    input.type = 'checkbox';
    input.checked = prefs[field];
    input.addEventListener('change', () => {
      prefs[field] = input.checked;
      writeJSON('kjv.prefs', prefs);
      applyPrefs();
    });
    row.append(span, input);
    body.append(row);
  }

  const footer = document.createElement('p');
  footer.className = 'credit';
  footer.textContent = 'KJV and Strong\u2019s: public domain. Webster\u2019s 1828: CC BY-SA 4.0. Cross-references are not in this build yet.';
  body.append(footer);
}

/* ------------------------------------------------------------- interaction */

let press = null;
let longPressed = false;

function clearPress() {
  if (press) clearTimeout(press.timer);
  press = null;
}

content.addEventListener('pointerdown', (event) => {
  const verseEl = event.target.closest('.verse');
  if (!verseEl) return;
  longPressed = false;
  const timer = setTimeout(() => {
    longPressed = true;
    lookupAt(event.clientX, event.clientY, verseEl);
  }, 450);
  press = { timer, x: event.clientX, y: event.clientY };
});

content.addEventListener('pointerup', clearPress);
content.addEventListener('pointercancel', clearPress);
content.addEventListener('pointermove', (event) => {
  if (!press) return;
  /* A thumb is never perfectly still; only a real drag counts as scrolling. */
  if (Math.hypot(event.clientX - press.x, event.clientY - press.y) > 10) clearPress();
});
content.addEventListener('contextmenu', (event) => {
  if (event.target.closest('.verse')) event.preventDefault();
});

function lookupAt(x, y, verseEl) {
  const verse = state.verses.get(Number(verseEl.dataset.v));
  if (!verse) return;

  let node = null;
  let offset = 0;
  if (document.caretRangeFromPoint) {
    const range = document.caretRangeFromPoint(x, y);
    if (range) { node = range.startContainer; offset = range.startOffset; }
  } else if (document.caretPositionFromPoint) {
    const position = document.caretPositionFromPoint(x, y);
    if (position) { node = position.offsetNode; offset = position.offset; }
  }
  if (!node) return;

  const total = offsetInVerse(verseEl, node, offset);
  const index = wordIndexAt(verse.text, total);
  if (index < 0) return;
  const selection = window.getSelection();
  if (selection) selection.removeAllRanges();
  wordStudy(verse, index);
}

/* Character offset of the caret in the verse's own text: the number and the
   footnote daggers are not part of it, so they are skipped. */
function offsetInVerse(verseEl, node, offset) {
  let total = 0;
  const walker = document.createTreeWalker(verseEl, NodeFilter.SHOW_TEXT);
  let current;
  while ((current = walker.nextNode())) {
    const parent = current.parentElement;
    if (parent.closest('.num') || parent.closest('[data-fn]')) continue;
    if (current === node) return total + offset;
    total += current.nodeValue.length;
  }
  return total;
}

content.addEventListener('click', (event) => {
  const wasLong = longPressed;
  longPressed = false;
  if (wasLong) return;

  const verseEl = event.target.closest('.verse');
  if (!verseEl) return;
  const verse = state.verses.get(Number(verseEl.dataset.v));
  if (!verse) return;

  const marker = event.target.closest('.fn');
  if (marker) {
    openFootnotes(verse, Number(marker.dataset.fn));
    return;
  }
  openVerseMenu(verse);
});

document.getElementById('prev').addEventListener('click', () => step(-1));
document.getElementById('next').addEventListener('click', () => step(1));
where.addEventListener('click', openPicker);
document.getElementById('menu').addEventListener('click', openSettings);

document.addEventListener('keydown', (event) => {
  const tag = event.target.tagName;
  if (tag === 'TEXTAREA' || tag === 'INPUT' || tag === 'SELECT') return;
  if (event.key === 'Escape') { closeSheet(); return; }
  if (openSheet) return;
  if (event.key === 'ArrowLeft') step(-1);
  if (event.key === 'ArrowRight') step(1);
});

function route() {
  const match = /^#\/([A-Z0-9]{3})\/(\d+)$/.exec(location.hash);
  if (!match) return false;
  const book = BOOKS.find((b) => b.id === match[1]);
  if (!book) return false;
  state.book = book;
  state.chapter = Math.min(Math.max(1, Number(match[2])), book.chapters);
  render(false);
  return true;
}

window.addEventListener('popstate', () => {
  if (!route()) render(false);
});

/* -------------------------------------------------------------------- boot */

applyPrefs();

const start = (() => {
  const match = /^#\/([A-Z0-9]{3})\/(\d+)$/.exec(location.hash);
  if (match) {
    const book = BOOKS.find((b) => b.id === match[1]);
    if (book) return { book, chapter: Number(match[2]) };
  }
  let last = null;
  try { last = localStorage.getItem('kjv.last'); } catch (e) { /* ignore */ }
  if (last) {
    const [id, chapter] = last.split('/');
    const book = BOOKS.find((b) => b.id === id);
    if (book) return { book, chapter: Number(chapter) || 1 };
  }
  return { book: BOOKS[0], chapter: 1 };
})();

state.book = start.book;
state.chapter = start.chapter;
render(true);

if ('serviceWorker' in navigator && location.protocol.startsWith('http')) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('./sw.js').catch(() => { /* offline is a bonus */ });
  });
}
