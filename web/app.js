'use strict';

/*
 * KJV Reader, in the browser.
 *
 * No framework, no build step, no backend: the page reads the very same assets
 * the Android app ships (../app/src/main/assets/usfm/kjv) and keeps everything
 * else in localStorage. The parsers are ports of UsfmParser.kt and
 * StrongsRepository.kt, kept close to the original where it matters, so a verse
 * reads the same in both.
 */

const DATA = new URL('../app/src/main/assets/usfm/kjv/', document.baseURI).href;

/* Canonical order, with the asset index each book's files are named after
   (there is no book_40) and the chapter count that goes with it. */
const BOOKS = [
  { id: 'GEN', name: 'Genesis', ai: 1, chapters: 50 },
  { id: 'EXO', name: 'Exodus', ai: 2, chapters: 40 },
  { id: 'LEV', name: 'Leviticus', ai: 3, chapters: 27 },
  { id: 'NUM', name: 'Numbers', ai: 4, chapters: 36 },
  { id: 'DEU', name: 'Deuteronomy', ai: 5, chapters: 34 },
  { id: 'JOS', name: 'Joshua', ai: 6, chapters: 24 },
  { id: 'JDG', name: 'Judges', ai: 7, chapters: 21 },
  { id: 'RUT', name: 'Ruth', ai: 8, chapters: 4 },
  { id: '1SA', name: '1 Samuel', ai: 9, chapters: 31 },
  { id: '2SA', name: '2 Samuel', ai: 10, chapters: 24 },
  { id: '1KI', name: '1 Kings', ai: 11, chapters: 22 },
  { id: '2KI', name: '2 Kings', ai: 12, chapters: 25 },
  { id: '1CH', name: '1 Chronicles', ai: 13, chapters: 29 },
  { id: '2CH', name: '2 Chronicles', ai: 14, chapters: 36 },
  { id: 'EZR', name: 'Ezra', ai: 15, chapters: 10 },
  { id: 'NEH', name: 'Nehemiah', ai: 16, chapters: 13 },
  { id: 'EST', name: 'Esther', ai: 17, chapters: 10 },
  { id: 'JOB', name: 'Job', ai: 18, chapters: 42 },
  { id: 'PSA', name: 'Psalms', ai: 19, chapters: 150 },
  { id: 'PRO', name: 'Proverbs', ai: 20, chapters: 31 },
  { id: 'ECC', name: 'Ecclesiastes', ai: 21, chapters: 12 },
  { id: 'SNG', name: 'Song of Solomon', ai: 22, chapters: 8 },
  { id: 'ISA', name: 'Isaiah', ai: 23, chapters: 66 },
  { id: 'JER', name: 'Jeremiah', ai: 24, chapters: 52 },
  { id: 'LAM', name: 'Lamentations', ai: 25, chapters: 5 },
  { id: 'EZK', name: 'Ezekiel', ai: 26, chapters: 48 },
  { id: 'DAN', name: 'Daniel', ai: 27, chapters: 12 },
  { id: 'HOS', name: 'Hosea', ai: 28, chapters: 14 },
  { id: 'JOL', name: 'Joel', ai: 29, chapters: 3 },
  { id: 'AMO', name: 'Amos', ai: 30, chapters: 9 },
  { id: 'OBA', name: 'Obadiah', ai: 31, chapters: 1 },
  { id: 'JON', name: 'Jonah', ai: 32, chapters: 4 },
  { id: 'MIC', name: 'Micah', ai: 33, chapters: 7 },
  { id: 'NAM', name: 'Nahum', ai: 34, chapters: 3 },
  { id: 'HAB', name: 'Habakkuk', ai: 35, chapters: 3 },
  { id: 'ZEP', name: 'Zephaniah', ai: 36, chapters: 3 },
  { id: 'HAG', name: 'Haggai', ai: 37, chapters: 2 },
  { id: 'ZEC', name: 'Zechariah', ai: 38, chapters: 14 },
  { id: 'MAL', name: 'Malachi', ai: 39, chapters: 4 },
  { id: 'MAT', name: 'Matthew', ai: 41, chapters: 28 },
  { id: 'MRK', name: 'Mark', ai: 42, chapters: 16 },
  { id: 'LUK', name: 'Luke', ai: 43, chapters: 24 },
  { id: 'JHN', name: 'John', ai: 44, chapters: 21 },
  { id: 'ACT', name: 'Acts', ai: 45, chapters: 28 },
  { id: 'ROM', name: 'Romans', ai: 46, chapters: 16 },
  { id: '1CO', name: '1 Corinthians', ai: 47, chapters: 16 },
  { id: '2CO', name: '2 Corinthians', ai: 48, chapters: 13 },
  { id: 'GAL', name: 'Galatians', ai: 49, chapters: 6 },
  { id: 'EPH', name: 'Ephesians', ai: 50, chapters: 6 },
  { id: 'PHP', name: 'Philippians', ai: 51, chapters: 4 },
  { id: 'COL', name: 'Colossians', ai: 52, chapters: 4 },
  { id: '1TH', name: '1 Thessalonians', ai: 53, chapters: 5 },
  { id: '2TH', name: '2 Thessalonians', ai: 54, chapters: 3 },
  { id: '1TI', name: '1 Timothy', ai: 55, chapters: 6 },
  { id: '2TI', name: '2 Timothy', ai: 56, chapters: 4 },
  { id: 'TIT', name: 'Titus', ai: 57, chapters: 3 },
  { id: 'PHM', name: 'Philemon', ai: 58, chapters: 1 },
  { id: 'HEB', name: 'Hebrews', ai: 59, chapters: 13 },
  { id: 'JAS', name: 'James', ai: 60, chapters: 5 },
  { id: '1PE', name: '1 Peter', ai: 61, chapters: 5 },
  { id: '2PE', name: '2 Peter', ai: 62, chapters: 3 },
  { id: '1JN', name: '1 John', ai: 63, chapters: 5 },
  { id: '2JN', name: '2 John', ai: 64, chapters: 1 },
  { id: '3JN', name: '3 John', ai: 65, chapters: 1 },
  { id: 'JUD', name: 'Jude', ai: 66, chapters: 1 },
  { id: 'REV', name: 'Revelation', ai: 67, chapters: 22 }
];

/* The groups the printed Bible uses, as ranges over BOOKS. */
const GROUPS = [
  ['PENTATEUCH', 0, 4, true],
  ['HISTORY', 5, 16, true],
  ['POETRY & WISDOM', 17, 21, true],
  ['MAJOR PROPHETS', 22, 26, true],
  ['MINOR PROPHETS', 27, 38, true],
  ['GOSPELS', 39, 42, false],
  ['APOSTOLIC HISTORY', 43, 43, false],
  ['PAULINE EPISTLES', 44, 55, false],
  ['GENERAL EPISTLES', 56, 64, false],
  ['APOCALYPTIC', 65, 65, false]
];

const DEFAULTS = { size: 18, spacing: 1.35, serif: false, italics: true, footnotes: true, theme: 'default' };
const HL_COLORS = ['yellow', 'green', 'blue', 'red'];
const THEMES = [
  ['default', 'Default', '#fbfaf7'],
  ['paper', 'Paper', '#f6f1e6'],
  ['sepia', 'Sepia', '#efe3cd'],
  ['grey', 'Grey', '#2a2b2d'],
  ['night', 'Night', '#101012']
];

/* Abbreviations that resolve to a book. A bare "j" is John, "j3.15" is
   John 3:15 - the shorthand the owner asked for. */
const ABBREVIATIONS = {
  gen: 'GEN', ge: 'GEN', ex: 'EXO', exo: 'EXO', lev: 'LEV', le: 'LEV', num: 'NUM', nu: 'NUM',
  deu: 'DEU', deut: 'DEU', dt: 'DEU', jos: 'JOS', jdg: 'JDG', jg: 'JDG', rut: 'RUT', ru: 'RUT',
  '1sa': '1SA', '1sam': '1SA', '2sa': '2SA', '2sam': '2SA', '1ki': '1KI', '1kg': '1KI',
  '2ki': '2KI', '2kg': '2KI', '1ch': '1CH', '1chr': '1CH', '2ch': '2CH', '2chr': '2CH',
  ezr: 'EZR', neh: 'NEH', est: 'EST', job: 'JOB', ps: 'PSA', psa: 'PSA', psm: 'PSA',
  psalm: 'PSA', pro: 'PRO', prv: 'PRO', prov: 'PRO', ecc: 'ECC', ec: 'ECC', eccl: 'ECC',
  sng: 'SNG', sos: 'SNG', song: 'SNG', isa: 'ISA', is: 'ISA', jer: 'JER', je: 'JER',
  lam: 'LAM', la: 'LAM', ezk: 'EZK', eze: 'EZK', ezek: 'EZK', dan: 'DAN', da: 'DAN',
  hos: 'HOS', ho: 'HOS', jol: 'JOL', joe: 'JOL', joel: 'JOL', amo: 'AMO', am: 'AMO',
  oba: 'OBA', ob: 'OBA', jon: 'JON', mic: 'MIC', mi: 'MIC', nam: 'NAM', nah: 'NAM',
  hab: 'HAB', zep: 'ZEP', zeph: 'ZEP', hag: 'HAG', zec: 'ZEC', zech: 'ZEC', mal: 'MAL',
  mat: 'MAT', mt: 'MAT', mrk: 'MRK', mk: 'MRK', luk: 'LUK', lk: 'LUK', jhn: 'JHN',
  jn: 'JHN', joh: 'JHN', j: 'JHN', act: 'ACT', ac: 'ACT', rom: 'ROM', ro: 'ROM',
  '1co': '1CO', '1cor': '1CO', '2co': '2CO', '2cor': '2CO', gal: 'GAL', ga: 'GAL',
  eph: 'EPH', php: 'PHP', phil: 'PHP', col: 'COL', '1th': '1TH', '1thess': '1TH',
  '2th': '2TH', '2thess': '2TH', '1ti': '1TI', '1tim': '1TI', '2ti': '2TI', '2tim': '2TI',
  tit: 'TIT', phm: 'PHM', heb: 'HEB', jas: 'JAS', ja: 'JAS', '1pe': '1PE', '1pet': '1PE',
  '2pe': '2PE', '2pet': '2PE', '1jn': '1JN', '2jn': '2JN', '3jn': '3JN', jud: 'JUD',
  jude: 'JUD', rev: 'REV', re: 'REV'
};

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

const rawCache = new Map();
const bookCache = new Map();
const wordsCache = new Map();
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

const breathe = () => new Promise((resolve) => setTimeout(resolve, 0));

/* ---------------------------------------------------------------- the page */

const content = document.getElementById('content');
const where = document.getElementById('where');
const whereLabel = document.getElementById('bookChapter');
const status = document.getElementById('status');

const state = { book: BOOKS[0], chapter: 1, verses: new Map(), words: new Map(), turn: null };

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

/* The word-to-number alignment for one chapter, done off the render path: the
   chapter is painted first and aligned second, which is what stops a page turn
   hesitating. A hold waits for it (see wordStudy). */
let alignTask = null;

async function alignChapter(book, chapter) {
  let raw = new Map();
  try {
    raw = await loadWords(book);
  } catch (e) {
    return; /* no word data: reading still works */
  }
  const words = new Map();
  for (const verse of chapter.verses) {
    const entries = raw.get(chapter.number * 1000 + verse.number);
    if (entries) words.set(verse.number, alignVerse(verse.text, entries));
  }
  if (state.book === book && state.chapter === chapter.number) state.words = words;
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
  state.words = new Map();

  content.replaceChildren(...chapter.verses.map(verseNode));

  /* A turn should look like a turn: one short lurch in the direction of
     travel. Removing the class first is what lets the same turn repeat. */
  if (state.turn) {
    const direction = state.turn;
    state.turn = null;
    content.classList.remove('turn-next', 'turn-prev');
    void content.offsetWidth;
    content.classList.add('turn-' + direction);
  }

  if (!keepScroll) window.scrollTo(0, 0);
  if (pushHash) {
    const hash = '#/' + state.book.id + '/' + state.chapter;
    if (location.hash !== hash) history.pushState(null, '', hash);
  }
  try {
    localStorage.setItem('kjv.last', state.book.id + '/' + state.chapter);
  } catch (e) { /* ignore */ }

  alignTask = alignChapter(state.book, chapter);
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
  go(book, chapter, undefined, delta > 0 ? 'next' : 'prev');
}

function go(book, chapter, verse, turn) {
  state.book = book;
  state.chapter = Math.min(Math.max(1, chapter), book.chapters);
  state.turn = turn || null;
  render(true).then(() => {
    if (verse) revealVerse(verse);
  });
}

function revealVerse(verse) {
  const node = content.querySelector('[data-v="' + verse + '"]');
  if (!node) return;
  node.scrollIntoView({ block: 'center', behavior: 'smooth' });
  node.classList.add('flash');
  setTimeout(() => node.classList.remove('flash'), 1400);
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
  /* An occasional action belongs in the header, beside Done, the way the app
     puts copy on the verse sheet: out of the reading path, one thumb away. */
  const tools = document.createElement('div');
  tools.className = 'sheet-tools';
  if (options.action) tools.append(options.action);
  tools.append(done);
  head.append(heading, tools);

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
  /* Alignment runs behind the paint; a hold taken in the instant after a turn
     waits for it rather than showing a word with no number behind it. */
  if (alignTask) {
    try { await alignTask; } catch (e) { /* fall back to the plain word */ }
  }

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
  let dictionary = null;
  try {
    dictionary = await loadWebster();
    english = dictionary.get(websterKey(label)) || null;
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

    /* "BROUGHT, ... preterit of bring, [See Bring.]" is a signpost, not a
       definition. Short entries carry the entry they point at, once. */
    const base = pointedWord(english);
    if (base) {
      const next = dictionary.get(websterKey(base));
      if (next && next !== english) {
        const alsoLabel = document.createElement('h3');
        alsoLabel.textContent = 'SEE ALSO \u00B7 ' + base.toUpperCase();
        const also = document.createElement('p');
        also.className = 'entry';
        also.style.whiteSpace = 'pre-line';
        also.textContent = base.toUpperCase() + ', ' + next;
        body.append(alsoLabel, also);
      }
    }
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
    labelEl.textContent = 'TRANSLATED AS, IN THE KJV';
    const kjv = document.createElement('p');
    kjv.className = 'muted';
    kjv.textContent = entry.kjv;
    body.append(labelEl, kjv);
  }

  const every = document.createElement('button');
  every.type = 'button';
  every.className = 'wide subtle';
  every.textContent = 'Every occurrence of ' + id;
  every.addEventListener('click', () => openOccurrences(id, label));
  body.append(every);
}

/* The word a short Webster entry points at: "\u2026 of bring, \u2026 [See Bring.]" */
function pointedWord(definition) {
  if (!definition || definition.length > 200) return null;
  const match = /(?:of|see)\s+([A-Za-z][A-Za-z'-]{2,})/i.exec(definition);
  return match ? match[1].toLowerCase() : null;
}

/* ------------------------------------------------------------ occurrences */

let occurrenceMap = null;

/* strongs number -> packed book/chapter/verse, over the whole Bible. */
async function buildOccurrences(progress) {
  if (occurrenceMap) return occurrenceMap;
  const map = new Map();
  for (let i = 0; i < BOOKS.length; i++) {
    const book = BOOKS[i];
    progress?.(i + 1, BOOKS.length, book.name);
    let words = new Map();
    try {
      words = parseWords(await fetchText(DATA + 'words_' + book.ai));
    } catch (e) {
      continue;
    }
    for (const [key, entries] of words) {
      const chapter = Math.floor(key / 1000);
      const verse = key % 1000;
      const packed = i * 1000000 + chapter * 1000 + verse;
      for (const item of entries) {
        if (!item.strongs) continue;
        let refs = map.get(item.strongs);
        if (!refs) map.set(item.strongs, refs = []);
        if (refs[refs.length - 1] !== packed) refs.push(packed);
      }
    }
    await breathe();
  }
  occurrenceMap = map;
  return map;
}

/* The opening words of a verse, for an occurrence row: enough to recognise the
   line without turning the list into the chapter. Empty on failure, so the row
   still works as a link. */
async function verseOpening(book, chapter, verse, limit) {
  try {
    const chapters = await loadBook(book);
    const found = chapters.find((c) => c.number === chapter);
    const line = found && found.verses.find((v) => v.number === verse);
    if (!line || !line.text) return '';
    const text = line.text;
    if (text.length <= limit) return text;
    const cut = text.slice(0, limit);
    const space = cut.lastIndexOf(' ');
    const kept = space > limit * 0.6 ? cut.slice(0, space) : cut;
    return kept.replace(/[\s,.;:]+$/, '') + '\u2026';
  } catch (e) {
    return '';
  }
}

async function openOccurrences(id, label) {
  const body = sheet(label + ' \u00B7 ' + id);
  const progress = document.createElement('p');
  progress.className = 'muted';
  progress.textContent = 'Counting the occurrences\u2026';
  body.append(progress);

  let map;
  try {
    map = await buildOccurrences((done, total, name) => {
      progress.textContent = 'Reading ' + name + '\u2026 ' + done + ' of ' + total;
    });
  } catch (error) {
    progress.textContent = 'Could not build the index: ' + error.message;
    return;
  }

  const refs = (map.get(id) || []).slice();
  progress.remove();

  if (!refs.length) {
    const none = document.createElement('p');
    none.className = 'muted';
    none.textContent = 'No occurrences are recorded for this number.';
    body.append(none);
    return;
  }

  const head = document.createElement('p');
  head.className = 'muted';
  head.textContent = refs.length + (refs.length === 1 ? ' verse' : ' verses');
  const list = document.createElement('div');
  list.className = 'results';

  const rows = [];
  for (const packed of refs) {
    const book = BOOKS[Math.floor(packed / 1000000)];
    const chapter = Math.floor((packed % 1000000) / 1000);
    const verse = packed % 1000;
    const row = document.createElement('button');
    row.type = 'button';
    row.className = 'result';
    const ref = document.createElement('span');
    ref.className = 'ref';
    ref.textContent = book.name + ' ' + chapter + ':' + verse;
    const opening = document.createElement('span');
    opening.className = 'snip';
    row.append(ref, opening);
    row.addEventListener('click', () => {
      closeSheet();
      go(book, chapter, verse);
    });
    list.append(row);
    rows.push({ book, chapter, verse, opening });
  }

  body.append(head, list);

  /* The rows work as links the moment they appear. The verse text follows them
     one book at a time, in list order, so the same book is read once rather
     than once per row. */
  (async () => {
    for (const item of rows) {
      const text = await verseOpening(item.book, item.chapter, item.verse, 90);
      if (text) item.opening.textContent = text;
    }
  })();
}

/* Build the occurrence index after the first chapter is on screen, so the first
   "every occurrence" is instant. It reads about 5 MB of words files, once, and
   the service worker keeps them \u2014 so it waits a few seconds, keeps quiet on a
   metered connection, and shows no progress: nothing is waiting on it. */
let preloadStarted = false;

function preloadOccurrences() {
  if (preloadStarted || occurrenceMap) return;
  if (navigator.onLine === false) return;
  if (navigator.connection && navigator.connection.saveData) return;
  preloadStarted = true;
  setTimeout(() => {
    buildOccurrences().catch(() => { /* the button still builds it on demand */ });
  }, 6000);
}

/* -------------------------------------------------------------- verse menu */

function openVerseMenu(verse) {
  const reference = state.book.name + ' ' + state.chapter + ':' + verse.number;
  const key = keyOf(state.book, state.chapter, verse.number);

  /* Copy, in the header where the app puts it: tap for the verse and its
     reference, hold for the reference alone. It is not the phone's own copy
     menu - that one takes the long press that belongs to the word lookup. */
  const copyButton = document.createElement('button');
  copyButton.type = 'button';
  copyButton.className = 'icon-btn';
  copyButton.title = 'Copy the verse and reference \u00B7 hold for the reference alone';
  copyButton.setAttribute('aria-label', 'Copy the verse and reference; hold to copy the reference alone');
  copyButton.innerHTML =
    '<svg viewBox="0 0 24 24" width="21" height="21" aria-hidden="true" fill="none" ' +
    'stroke="currentColor" stroke-width="1.9" stroke-linejoin="round">' +
    '<rect x="9" y="9" width="11" height="11" rx="2"></rect>' +
    '<path d="M6 15H5.5A1.5 1.5 0 0 1 4 13.5v-9A1.5 1.5 0 0 1 5.5 3h9A1.5 1.5 0 0 1 16 4.5V5"></path>' +
    '</svg>';

  let copyTimer = null;
  let copiedReference = false;
  const stopCopy = () => {
    if (copyTimer) clearTimeout(copyTimer);
    copyTimer = null;
  };

  copyButton.addEventListener('pointerdown', (event) => {
    stopCopy();
    copiedReference = false;
    /* A 21px target is easy to drift off; the capture keeps the hold alive. */
    try { copyButton.setPointerCapture?.(event.pointerId); } catch (e) { /* older browsers */ }
    copyTimer = setTimeout(() => {
      copyTimer = null;
      copiedReference = true;
      copy(reference);
    }, 500);
  });
  copyButton.addEventListener('pointerup', stopCopy);
  copyButton.addEventListener('pointercancel', () => { stopCopy(); copiedReference = false; });
  copyButton.addEventListener('click', () => {
    stopCopy();
    if (copiedReference) {
      copiedReference = false;
      closeSheet();
      return;
    }
    copy(verse.text + '\n' + reference + ' (KJV)');
    closeSheet();
  });

  const body = sheet(reference, { action: copyButton });
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

/* ---------------------------------------------------------------- search */

let searchVerses = null;

async function buildSearchIndex(progress) {
  if (searchVerses) return searchVerses;
  const list = [];
  for (let i = 0; i < BOOKS.length; i++) {
    progress?.(i, BOOKS.length, BOOKS[i].name);
    let chapters = [];
    try {
      chapters = await loadBook(BOOKS[i]);
    } catch (e) { /* skip a book that will not load */ }
    for (const chapter of chapters) {
      for (const verse of chapter.verses) {
        list.push({
          book: BOOKS[i],
          chapter: chapter.number,
          verse: verse.number,
          text: verse.text,
          lower: verse.text.toLowerCase()
        });
      }
    }
    await breathe();
  }
  searchVerses = list;
  progress?.(BOOKS.length, BOOKS.length, '');
  return list;
}

function parseReference(input) {
  const text = input.trim().toLowerCase().replace(/\s+/g, ' ');
  const match = /^([1-3]?\s?[a-z]+)\.?\s*(\d{1,3})(?:\s*[.:]\s*(\d{1,3}))?$/.exec(text);
  if (!match) return null;

  const name = match[1].replace(/\s+/g, '');
  let book = null;
  const id = ABBREVIATIONS[name] || ABBREVIATIONS[name.slice(0, 4)] || ABBREVIATIONS[name.slice(0, 3)];
  if (id) book = BOOKS.find((b) => b.id === id);
  if (!book) {
    book = BOOKS.find((b) => b.name.toLowerCase() === name) ||
           BOOKS.find((b) => b.name.toLowerCase().startsWith(name)) ||
           BOOKS.find((b) => b.name.toLowerCase().replace(/\s+/g, '').startsWith(name));
  }
  if (!book) return null;

  const chapter = Math.min(Math.max(1, Number(match[2])), book.chapters);
  const verse = match[3] ? Math.max(1, Number(match[3])) : 0;
  return { book, chapter, verse };
}

function isReferenceOnly(input) {
  return parseReference(input) !== null && !/\s\s|"/.test(input);
}

function wordStart(text, term) {
  let from = 0;
  while (true) {
    const at = text.indexOf(term, from);
    if (at < 0) return false;
    if (at === 0 || !/[a-z0-9]/.test(text[at - 1])) return true;
    from = at + 1;
  }
}

function termsOf(query) {
  const terms = [];
  const re = /"([^"]+)"|(\S+)/g;
  let m;
  while ((m = re.exec(query.toLowerCase()))) {
    if (m[1]) terms.push({ text: m[1], phrase: true });
    else if (m[2]) terms.push({ text: m[2], phrase: false });
  }
  return terms;
}

function matchVerses(list, query) {
  const terms = termsOf(query);
  if (!terms.length) return [];
  const out = [];
  for (const verse of list) {
    let ok = true;
    for (const term of terms) {
      if (term.phrase) {
        if (!verse.lower.includes(term.text)) { ok = false; break; }
      } else if (!wordStart(verse.lower, term.text)) {
        ok = false;
        break;
      }
    }
    if (ok) {
      out.push(verse);
      if (out.length >= 60) break;
    }
  }
  return out;
}

function snippet(verse, terms) {
  const text = verse.text;
  let at = -1;
  for (const term of terms) {
    at = verse.lower.indexOf(term.text);
    if (at >= 0) break;
  }
  if (at < 0 || text.length <= 150) return text;
  const from = Math.max(0, at - 45);
  const to = Math.min(text.length, from + 150);
  return (from > 0 ? '\u2026' : '') + text.slice(from, to).trim() + (to < text.length ? '\u2026' : '');
}

function openSearch() {
  const body = sheet('Search', { tall: true });

  const input = document.createElement('input');
  input.type = 'search';
  input.className = 'search-input';
  input.placeholder = 'j3.15 \u00B7 John 3:15 \u00B7 or words from a verse';
  input.autocomplete = 'off';
  input.spellcheck = false;

  const info = document.createElement('p');
  info.className = 'muted';
  info.textContent = 'Type a reference, or words you remember. Quotes search a phrase.';

  const results = document.createElement('div');
  results.className = 'results';

  body.append(input, info, results);

  let timer = null;
  let running = 0;

  const showReference = (parsed) => {
    results.replaceChildren();
    const row = document.createElement('button');
    row.type = 'button';
    row.className = 'result';
    row.textContent = 'Go to ' + parsed.book.name + ' ' + parsed.chapter +
      (parsed.verse ? ':' + parsed.verse : '');
    row.addEventListener('click', () => {
      closeSheet();
      go(parsed.book, parsed.chapter, parsed.verse);
    });
    results.append(row);
    info.textContent = 'Press Enter to go there.';
  };

  const run = async (value, enter) => {
    const query = value.trim();
    results.replaceChildren();
    if (!query) {
      info.textContent = 'Type a reference, or words you remember. Quotes search a phrase.';
      return;
    }

    const parsed = parseReference(query);
    if (parsed && (isReferenceOnly(query) || enter)) {
      showReference(parsed);
      if (enter) {
        closeSheet();
        go(parsed.book, parsed.chapter, parsed.verse);
      }
      return;
    }

    const ticket = ++running;
    info.textContent = 'Indexing the whole Bible, once\u2026';
    const list = await buildSearchIndex((done, total, name) => {
      if (ticket === running) info.textContent = 'Indexing\u2026 ' + name + ' (' + done + '/' + total + ')';
    });
    if (ticket !== running) return;

    const found = matchVerses(list, query);
    info.textContent = found.length
      ? found.length + (found.length === 60 ? '+' : '') + ' verses'
      : 'No verse contains \u201C' + query + '\u201D';
    if (!found.length) return;

    const terms = termsOf(query);
    for (const verse of found) {
      const row = document.createElement('button');
      row.type = 'button';
      row.className = 'result';
      const ref = document.createElement('span');
      ref.className = 'ref';
      ref.textContent = verse.book.name + ' ' + verse.chapter + ':' + verse.verse;
      const text = document.createElement('span');
      text.className = 'snip';
      text.textContent = snippet(verse, terms);
      row.append(ref, text);
      row.addEventListener('click', () => {
        closeSheet();
        go(verse.book, verse.chapter, verse.verse);
      });
      results.append(row);
    }
  };

  input.addEventListener('input', () => {
    clearTimeout(timer);
    timer = setTimeout(() => run(input.value, false), 260);
  });
  input.addEventListener('keydown', (event) => {
    if (event.key === 'Enter') {
      clearTimeout(timer);
      run(input.value, true);
    }
  });

  setTimeout(() => input.focus(), 240);
}

/* ----------------------------------------------------------------- pickers */

function openPicker() {
  const body = sheet('Choose a book', { tall: true });
  const tabs = document.createElement('div');
  tabs.className = 'tabs';
  const list = document.createElement('div');
  list.className = 'book-list';

  const fill = (ot) => {
    list.replaceChildren();
    for (const [name, from, to, isOt] of GROUPS) {
      if (isOt !== ot) continue;
      const heading = document.createElement('h3');
      heading.className = 'group';
      heading.textContent = name;
      list.append(heading);
      for (let i = from; i <= to; i++) {
        const book = BOOKS[i];
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'book' + (book === state.book ? ' current' : '');
        button.textContent = book.name;
        button.addEventListener('click', () => openChapters(book));
        list.append(button);
      }
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
  size.max = '34';
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

  const themeLabel = document.createElement('h3');
  themeLabel.textContent = 'BACKGROUND';
  const themeRow = document.createElement('div');
  themeRow.className = 'theme-row';
  for (const [value, name, colour] of THEMES) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'theme-dot' + (prefs.theme === value ? ' active' : '');
    button.style.background = colour;
    button.title = name;
    button.setAttribute('aria-label', name);
    button.addEventListener('click', () => {
      prefs.theme = value;
      writeJSON('kjv.prefs', prefs);
      applyPrefs();
      for (const dot of themeRow.children) dot.classList.remove('active');
      button.classList.add('active');
    });
    themeRow.append(button);
  }

  body.append(sizeLabel, size, spacingLabel, spacing, themeLabel, themeRow);

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

  const offlineLabel = document.createElement('h3');
  offlineLabel.textContent = 'OFFLINE';
  const offline = document.createElement('button');
  offline.type = 'button';
  offline.className = 'wide subtle';
  offline.textContent = 'Download the whole Bible for offline';
  offline.addEventListener('click', () => downloadAll(offline));
  const offlineNote = document.createElement('p');
  offlineNote.className = 'credit';
  offlineNote.textContent = 'About 19 MB, once. After that the reader opens with no network at all.';

  body.append(offlineLabel, offline, offlineNote);

  const footer = document.createElement('p');
  footer.className = 'credit';
  footer.textContent = 'KJV and Strong\u2019s: public domain. Webster\u2019s 1828: CC BY-SA 4.0. Cross-references are not in this build yet.';
  body.append(footer);
}

async function downloadAll(button) {
  const files = [];
  for (const book of BOOKS) files.push('book_' + book.ai, 'words_' + book.ai);
  files.push('lexicon', 'webster', 'xrefs');

  button.disabled = true;
  for (let i = 0; i < files.length; i++) {
    button.textContent = 'Downloading ' + (i + 1) + ' of ' + files.length + '\u2026';
    try {
      /* Straight to the cache: the service worker stores every data file it
         serves, so this is what makes the app work with the network off. */
      await fetch(DATA + files[i], { cache: 'reload' });
    } catch (e) { /* keep going: one missing file should not stop the rest */ }
  }
  button.disabled = false;
  button.textContent = 'Downloaded \u2014 the reader now works offline';
}

/* ------------------------------------------------------------- interaction */

let press = null;
let longPressed = false;
let swipe = null;

function clearPress() {
  if (press) clearTimeout(press.timer);
  press = null;
}

content.addEventListener('pointerdown', (event) => {
  const verseEl = event.target.closest('.verse');
  if (!verseEl) return;
  longPressed = false;
  swipe = { x: event.clientX, y: event.clientY, done: false, el: verseEl };
  /* Keep every later event of this touch on the verse it started on. Without
     the capture, a drag that leaves the verse sends its pointerup elsewhere and
     the page never turns. */
  try { verseEl.setPointerCapture?.(event.pointerId); } catch (e) { /* older browsers */ }
  const timer = setTimeout(() => {
    longPressed = true;
    lookupAt(event.clientX, event.clientY, verseEl);
  }, 450);
  press = { timer, x: event.clientX, y: event.clientY };
});

content.addEventListener('pointerup', (event) => {
  clearPress();
  if (!swipe) return;
  const drag = swipe;
  try { drag.el.releasePointerCapture?.(event.pointerId); } catch (e) { /* older browsers */ }
  const dx = event.clientX - drag.x;
  const dy = event.clientY - drag.y;
  /* A page turn: a long, flat drag. Anything steeper is a scroll. The decision
     is taken from the drag itself, never from the press timer, because a thumb
     that drifts has already cancelled the press by here. */
  if (!drag.done && Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy) * 1.8) {
    drag.done = true;
    longPressed = false;
    step(dx < 0 ? 1 : -1);
  }
});

content.addEventListener('pointercancel', () => { clearPress(); swipe = null; });
content.addEventListener('pointermove', (event) => {
  if (!press) return;
  /* A thumb is never perfectly still; only a real drag counts as scrolling. */
  if (Math.hypot(event.clientX - press.x, event.clientY - press.y) > 12) clearPress();
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
  const turned = swipe && swipe.done;
  swipe = null;
  if (wasLong || turned) return;

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
document.getElementById('search').addEventListener('click', openSearch);
where.addEventListener('click', openPicker);
document.getElementById('menu').addEventListener('click', openSettings);

document.addEventListener('keydown', (event) => {
  const tag = event.target.tagName;
  if (tag === 'TEXTAREA' || tag === 'INPUT' || tag === 'SELECT') {
    if (event.key === 'Escape') event.target.blur();
    return;
  }
  if (event.key === 'Escape') { closeSheet(); return; }
  if (openSheet) return;
  if (event.key === 'ArrowLeft') step(-1);
  if (event.key === 'ArrowRight') step(1);
  if (event.key === '/') { event.preventDefault(); openSearch(); }
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
render(true).then(preloadOccurrences);

if ('serviceWorker' in navigator && location.protocol.startsWith('http')) {
  window.addEventListener('load', () => {
    navigator.serviceWorker.register('./sw.js').catch(() => { /* offline is a bonus */ });
  });
}
