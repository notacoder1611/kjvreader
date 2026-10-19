'use strict';

/*
 * Offline, without precaching 19 MB.
 *
 * The page itself is cached at install; the Bible, the words, the lexicon and
 * the dictionary are cached the first time they are fetched, so the first read of
 * a book costs a fetch and every read after it does not.
 *
 * VERSION and the ?v= stamps in index.html are bumped together on every deploy:
 * that is what stops a returning reader seeing the previous build out of the
 * cache.
 */

const VERSION = 'kjv-web-3';
const STAMP = '3';
const SHELL = [
  './',
  './index.html',
  './app.js?v=' + STAMP,
  './style.css?v=' + STAMP,
  './manifest.webmanifest',
  './icon.svg'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(VERSION)
      .then((cache) => cache.addAll(SHELL))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key !== VERSION).map((key) => caches.delete(key))))
      .then(() => self.clients.claim())
  );
});

const isData = (url) => url.pathname.includes('/app/src/main/assets/usfm/kjv/');

self.addEventListener('fetch', (event) => {
  const request = event.request;
  if (request.method !== 'GET') return;
  const url = new URL(request.url);
  if (url.origin !== location.origin) return;

  if (isData(url)) {
    /* Stale-while-revalidate: instant from cache, refreshed in the background. */
    event.respondWith(
      caches.open(VERSION).then(async (cache) => {
        const hit = await cache.match(request);
        const network = fetch(request)
          .then((response) => {
            if (response.ok) cache.put(request, response.clone());
            return response;
          })
          .catch(() => hit);
        return hit || network;
      })
    );
    return;
  }

  event.respondWith(
    caches.match(request).then((hit) => hit || fetch(request).catch(() => caches.match('./index.html')))
  );
});
