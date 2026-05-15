# Phase O2 — PWA & Offline Mode

## Goal

Make REPuLse installable and usable offline for the core app shell.

O1 already delivered the embeddable component. O2 only covers PWA/offline behavior:
manifest, service worker, cache strategy, and offline sample caching.

## Scope

- Add `app/public/manifest.json` and wire it from `index.html`.
- Add app icons using existing icon assets or generated PNG derivatives.
- Add a versioned service worker that precaches the app shell: `index.html`,
  compiled app JS, CSS, fonts, WASM, `worklet.js`, worklet polyfills, and built-in
  plugin JS.
- Cache samples on use where browser storage allows it.
- Add an explicit offline-bank workflow, e.g. `(download-bank! :AkaiLinn)`, only if
  the sample registry can provide the required URLs reliably.
- Do not cache authenticated API responses, Freesound search responses, community
  snippet mutations, or AI traffic.

## Current Code Notes

- The app shell is served from `app/public`.
- WASM is copied to `app/public/repulse_audio_bg.wasm` by `npm run build:wasm`.
- Production audio depends on `app/public/worklet.js` and built-in plugin JS under
  `app/public/plugins/`.
- Session state already persists via `app/src/repulse/session.cljs`; PWA install state
  should not be stored in the session snapshot.

## Definition Of Done

- [ ] App exposes valid manifest metadata and icons.
- [ ] Service worker precaches the app shell and cleans old cache versions.
- [ ] Reloading offline after one successful online load opens the app shell.
- [ ] WASM/worklet assets are not served stale after cache version changes.
- [ ] Network/API requests that should remain network-only are not cached.
- [ ] `docs/DEPLOYMENT.md` and user docs mention offline limits.
- [ ] `npm run test` and `npx shadow-cljs compile app` pass.
