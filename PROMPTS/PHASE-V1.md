# Phase V1 — Version 1 Launch: Public Address, First Impression & 1.0.0 Release

> **Automation:** 🧑 human-in-loop. Registering the domain, the Vercel and Supabase settings,
> the Safari export check, recording the demo video and cutting the release all need the
> owner. The code parts (meta tags, mobile header, snippet CSS) are small and testable.
> (Markers are defined at the top of `ROADMAP.md`.)

**Milestone:** none (release track; runs after M1 and M2 are complete)
**Depends on:** MST1 (master output stage & loudness). QA5 parts 1–3 (CSP, origin gate,
AI proxies) plus its "generic 500 bodies" item. The rest of QA5 (migrations, trending
SQL, trigram indexes, `api/` tests) and VIZ1 are recommended but don't block V1.

## Goal

REPuLse works, but strangers can't find it, and the page looks unfinished when they do.
Production only lives at `repulse-ten.vercel.app` (`repulse.vercel.app` belongs to an
unrelated app). The README has no link to the live app. A shared link previews as a bare
title. On a phone, the header pushes the play button off-screen. V1 gives REPuLse a
public address, link previews, a phone fallback where play stays reachable, fixes for the
two open GitHub issues (#11, #73), a demo video and a tagged `1.0.0` release. Nothing in
the language changes.

```lisp
;; Before — share link points at a domain REPuLse doesn't own (docs/USAGE.md)
(share!)   ; docs: "copies URL like https://repulse.app/#v3:…"
           ; the page at that URL: no preview card, play off-screen on a phone

;; After — same code, the link uses the real domain and previews with an image
(share!)   ; "copied https://<domain>/#v3:…" — Slack/Discord/iMessage show title,
           ; description and og.png; on a phone the banner says "made for desktop,
           ; listen here" and ▶ play is visible in the header
(demo :techno)    ; unchanged; first visits still load a random demo
(export 4)        ; Safari: the WAV has no buzz (#11)
```

## Decisions (owner, settle before implementing)

| # | Question | Recommendation |
|---|---|---|
| D1 | **Domain.** Which domain, and who registers it? | Buy a short domain (check `repulse.app` availability first: the docs and `ai/client.cljs` already assume it). Fallback: rename the Vercel project so `repulse-<x>.vercel.app` reads well. Registering is a purchase, so the owner does it. |
| D2 | **Landing page or straight into the app?** | Straight into the app with a demo loaded (as today, a random one of five). That is the strongest pitch. The README and the release notes do the explaining. No separate landing page in V1. |
| D3 | **Phone experience.** Full editing, or listen-only? | Listen and tweak: the editor stays editable, a dismissible banner says the app is made for a desktop keyboard, and ▶ play is always visible. Full touch layout is O4 (deferred). |
| D4 | **Legal pages.** The site has accounts (GitHub OAuth via Supabase), stores snippets and uses Speed Insights. Does the owner need an imprint and a privacy notice for a public site in their country? | Owner decision; this spec doesn't give legal advice. If needed: a static `/legal.html` linked from a small footer or the ⓘ area, no cookie banner (no tracking cookies are set). |
| D5 | **Demo video.** Where is it hosted? | A 30–60 s screen recording with sound. MP4 uploaded to a GitHub release asset or a GitHub user-attachment and embedded at the top of the README; a silent GIF (≤ 5 MB, `docs/media/demo.gif`) as a fallback. |
| D6 | **Version number.** | `1.0.0`, cut with `/create-release` from `main` after the dev → main release PR. |

## Background

### Deployment and addresses

- Vercel project `repulse` (team `tissanrs-projects`). Production aliases today:
  `repulse-ten.vercel.app`, `repulse-tissanrs-projects.vercel.app`,
  `repulse-git-main-tissanrs-projects.vercel.app`. No custom domain on the team.
  Production deploys from `main`; `dev` is ahead of `main` (20 commits on 2026-11-27).
- `vercel.json`: build command, rewrites (SPA fallback to `index.html`) and the security
  headers (CSP etc.). QA5 rewrites the CSP; V1 only adds the new domain where needed.
- `api/_lib.ts` `originAllowed`: QA5 part 2 makes it accept "the production domain" from
  config. V1 sets that value (`ALLOWED_ORIGINS` env var or the constant QA5 introduces)
  to the D1 domain.
- Supabase GitHub OAuth (`docs/DEPLOYMENT.md` §1): the Supabase *Site URL* and
  *Redirect URLs* must include the new domain, or sign-in sends users back to the old
  host. The GitHub OAuth app's *Homepage URL* changes too (its callback stays the
  Supabase URL).
- `app/src/repulse/ai/client.cljs:203` sends `"HTTP-Referer" "https://repulse.app"` to
  OpenRouter; `client_test.cljs:57` asserts it.

### First page load

- `app/public/index.html`: `<title>REPuLse</title>`, an SVG favicon and nothing else in
  `<head>`. No `description`, no Open Graph or Twitter tags, no `theme-color`, no
  `<noscript>`.
- `app/src/repulse/app.cljs` `build-dom!` (~line 175) writes the header as an HTML string:
  `.brand-mark`, `.nav-actions` (tap, lib, hist, share), `.nav-spacer`, `.auth-slot`
  (sign in), `.transport-controls` (▶ play, playing dot).
- `app/src/repulse/content/first_visit.cljs` `first-visit-setup!`: on a first visit (no
  localStorage) it loads a random demo from `[:techno :ambient :house :dnb :minimal]` and
  writes `Welcome to REPuLse! Loaded :<demo> — press Alt+Enter to play`.
- `app/public/css/main.css` ~line 2514, `@media (max-width: 760px)`: only shrinks the
  header buttons' font and padding. At 375 px the header is still wider than the screen,
  so "sign in" is cut off and ▶ play is off-screen. On a phone there is no Alt key, so
  the play button is the only way to start sound.
- Share links: `app.cljs:147` builds the link from the current page's base URL via
  `session/share-link` (`session.cljs:249`), so links follow whatever domain the page is
  served from. Only `docs/USAGE.md:4839` and `session_test.cljs:222–229` mention
  `repulse.app` (the tests use it as an arbitrary base and can stay).

### Issue #11 — Safari WAV export buzz

Filed 2026-03-18, before EXP1. At that time `(export …)` rendered with the JS fallback
voices in an `OfflineAudioContext`. Since EXP1, `app/src/repulse/offline_render.cljs`
`render!` builds the offline graph with the **WASM worklet** at the live context's sample
rate (`(.-sampleRate (audio/get-ctx))`), and falls back to real-time capture when
`precondition-failure` returns a reason (no `AudioWorkletNode` offline, active buses,
unsupported plugin FX). WAV encoding is `app/src/repulse/wav.cljs`. Whether the buzz
still happens in Safari, and on which of the two paths, is unknown.

### Issue #73 — Report and Delete buttons misaligned

`app/src/repulse/ui/snippet_panel.cljs` ~line 180 renders `.snippet-meta-row` with the
stars, `.snippet-report-btn` and (own snippets only) `.snippet-delete-btn`. The CSS has
two conflicting rule sets: `main.css` ~828–860 (`padding: 2px 5px`, `font-size: 0.65rem`,
transparent border) and the later redesign block ~2082 that groups them with
`.snippet-btn` (`padding: 4px 0`, `font-size: 10px`, visible `var(--bd)` border). The
later block wins, so the two buttons have zero horizontal padding and a border that
doesn't match the star row.

### Release tooling

- `.claude/commands/test-protocol.md` (`/test-protocol`): bumps the version, tags `dev`,
  writes the copy-paste test protocol into the dev → main release PR.
- `.claude/commands/create-release.md` (`/create-release`): drafts notes from delivered
  phases, bumps `package.json` via PR, tags the release.
- `package.json` `"version": "0.2.2"`.

## Implementation

Work in this order. Parts A–C are code (one PR against `dev`); parts D–F are owner steps
recorded in the PR's test protocol and in `ROADMAP.md`.

### Part A — Address (owner + small code change)

1. Owner registers the D1 domain and adds it to the Vercel project as the production
   domain (`vercel domains add <domain> repulse` or the dashboard). Redirect
   `repulse-ten.vercel.app` → the domain (Vercel's domain redirect setting).
2. Supabase → Authentication → URL configuration: *Site URL* = `https://<domain>`, add it
   to *Redirect URLs*. GitHub OAuth app *Homepage URL* = `https://<domain>`.
3. Set the production origin for QA5's origin gate (`ALLOWED_ORIGINS` env var in Vercel,
   production scope) to `https://<domain>`.
4. Code: replace `https://repulse.app` in `ai/client.cljs:203` and `client_test.cljs:57`
   with the D1 domain if it differs. Update `docs/USAGE.md:4839` to the real domain.
5. `docs/DEPLOYMENT.md`: add a "Custom domain" section listing steps 1–3, so a later
   domain change touches all three places.

### Part B — Link previews and head metadata

Edit `app/public/index.html` `<head>` (absolute URLs, D1 domain):

```html
<meta name="description" content="REPuLse — a live coding instrument in the browser. Write music patterns in a small Lisp and hear them instantly.">
<meta name="theme-color" content="#131928">
<link rel="canonical" href="https://<domain>/">
<meta property="og:type" content="website">
<meta property="og:site_name" content="REPuLse">
<meta property="og:title" content="REPuLse — a Lisp that beats">
<meta property="og:description" content="Live-code music in the browser: patterns in a small Lisp, synthesis in Rust/WASM.">
<meta property="og:url" content="https://<domain>/">
<meta property="og:image" content="https://<domain>/og.png">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta name="twitter:card" content="summary_large_image">
<link rel="apple-touch-icon" href="/icon.png">
```

- `app/public/og.png`: 1200×630, ≤ 300 KB. A screenshot of the app playing the techno
  demo (editor + track panel) with the wordmark. Take it from the production build at a
  1200×630 viewport.
- Add `<noscript>`: "REPuLse needs JavaScript and Web Audio." inside `<body>`.
- Unsupported browser message: in `app.cljs` init, if neither `AudioContext` nor
  `webkitAudioContext` exists, set the output line to
  `This browser has no Web Audio — try a current Chrome, Firefox or Safari on desktop.`
  instead of failing silently later.
- Share links already carry the `#v3:` hash and load the same `index.html`, so they
  preview with the same card. No per-snippet images in V1.
- If QA5's CSP touches `img-src`, check `og.png` stays same-origin (`'self'`).

### Part C — Phone fallback (D3)

1. **Header fits at 375 px.** In the `@media (max-width: 760px)` block of `main.css`:
   - hide `.tap-btn` and `.history-toggle-btn` (tap tempo and history are desktop tools);
   - keep lib, share, sign-in and ▶ play; let `.nav-actions` shrink
     (`min-width: 0; overflow: hidden`) and give `.transport-controls`
     `flex-shrink: 0` so play is never the element that gets pushed out;
   - check at 320 px too (iPhone SE): if it still overflows, hide `.share-btn` next.
2. **Desktop notice.** New `app/src/repulse/ui/mobile_notice.cljs`: on load, when
   `(.-matches (js/matchMedia "(pointer: coarse) and (max-width: 760px)"))`, insert a
   one-line banner above the buffer tabs:
   `Made for a desktop keyboard. Press ▶ play to listen; open on a computer to code.`
   with a ✕ button. Dismissal is stored in `localStorage` key
   `repulse:mobile-notice-dismissed` (wrapped in try/catch, like the other
   localStorage reads). The banner has `role="note"`. Call it from `app.cljs` after
   `build-dom!`.
3. **Play is the obvious action.** On a coarse pointer, the first-visit output line reads
   `Welcome to REPuLse! Loaded :<demo> — press ▶ play` instead of `… press Alt+Enter …`
   (`first_visit.cljs` `first-visit-setup!`; take a `touch?` argument from the caller so
   the namespace stays DOM-free, and put the media query in one helper in
   `mobile_notice.cljs`).
4. Do not change editor, panels or tracks layout beyond this; that is O4.

### Part D — Issues #11 and #73

- **#73 (code).** Remove the conflict: delete `.snippet-report-btn` and
  `.snippet-delete-btn` from the grouped rule at `main.css` ~2082 and from the colour
  group ~2063, and keep one rule set (the ~828–860 block, updated to the redesign's
  `font-size: 10px` and `border-radius: 0`). Make `.snippet-meta-row` a
  `display: flex; align-items: center; gap: 6px;` row with the report and delete buttons
  pushed right (`margin-left: auto` on the first of them). Check both states: own snippet
  (report + delete) and others' snippets (report only, disabled when logged out).
- **#11 (owner check first).** In Safari (macOS, current version) on the production
  build:
  1. `(bpm 110) (track :rhythm (seq :bd :_ :sd :_ :bd :bd :_ :sd))`, then `(export 4)`;
  2. `(export :song)` on `(demo :techno)`;
  3. a pattern with a `(bus …)` (forces the real-time capture path), then `(export 4)`.
  Listen to each WAV. If none buzzes, close #11 with a comment naming the Safari version
  and the three cases (EXP1 replaced the JS offline voices). If one buzzes, note which
  path (offline WASM vs real-time capture) and open a focused bug with that finding;
  fixing it is in scope for V1 only if the cause is in `wav.cljs` (encoding) or the
  sample-rate handling of `offline_render.cljs`. Otherwise mark Safari export as a known
  issue in the README's *Browser support* section and the release notes.

### Part E — README, docs and demo video

- `README.md` top: a **Try it → https://<domain>** link directly under the tagline, and
  the demo video (D5) right below it. Keep the badges; change the Node badge to the
  version `engines` actually requires.
- README *Quick start*: put "Open https://<domain> and press ▶ play" first, then
  `(tutorial)` and `(demo)`, then the local build steps under "Run it locally".
- README *Browser support*: state what was actually checked in Part F, with versions.
- Demo video (owner records): 30–60 s, starts from an empty buffer, builds a beat in ~4
  evaluations, shows a slider drag and a section change, ends on a full mix. Sound on.
  Record after MST1 so the level matches streaming audio.

### Part F — Cross-browser pass and release (owner)

1. Manual pass on the production preview of the release PR, current versions of desktop
   Chrome, Firefox and Safari, plus iOS Safari and Android Chrome for Part C only. Per
   desktop browser: first load plays the loaded demo; `(tutorial)` chapter 1 plays;
   `(export 4)` downloads a WAV; sign in with GitHub and back; a share link opens in a
   new window with the same code. Record results in the release PR's test protocol.
2. `/test-protocol` on `dev` → dev → main release PR; work through it; merge.
3. `/create-release` → version `1.0.0`, tag `v1.0.0`, GitHub release with notes grouped by
   milestone (M1, M2, M4 parts delivered) plus "Known issues".
4. Check the production domain serves the `v1.0.0` build (`vercel inspect <domain>`), and
   that the link preview renders (paste the domain into a Slack or Discord message, or
   use the Open Graph debugger of any social site).

## Files to change

| File | Change |
|---|---|
| `app/public/index.html` | description, canonical, Open Graph, Twitter card, theme-color, apple-touch-icon, `<noscript>` |
| `app/public/og.png` | new, 1200×630 preview image |
| `app/public/css/main.css` | phone header rules in the `max-width: 760px` block; mobile notice styles; #73 snippet button rules de-duplicated and `.snippet-meta-row` flex |
| `app/src/repulse/ui/mobile_notice.cljs` | new: coarse-pointer helper, dismissible banner |
| `app/src/repulse/app.cljs` | call the mobile notice; pass `touch?` to `first-visit-setup!`; no-Web-Audio message |
| `app/src/repulse/content/first_visit.cljs` | welcome text says `press ▶ play` when `touch?` |
| `app/src/repulse/ai/client.cljs`, `ai/client_test.cljs` | `HTTP-Referer` → D1 domain (only if it isn't `repulse.app`) |
| `app/src/repulse/ui/snippet_panel.cljs` | only if the #73 fix needs a wrapper element around report/delete |
| `README.md` | Try-it link, demo video, quick start order, browser support with checked versions |
| `docs/USAGE.md` | `(share!)` example URL → real domain |
| `docs/DEPLOYMENT.md` | "Custom domain" section (Vercel domain, Supabase URLs, GitHub OAuth app, `ALLOWED_ORIGINS`) |
| `docs/media/demo.gif` | optional GIF fallback for the video (≤ 5 MB) |
| `e2e/mobile.spec.ts` | new: 375×812 and 320×568 viewports — play button inside the viewport and clickable, banner shown on touch emulation and gone after ✕ and reload |
| `e2e/head-meta.spec.ts` | new (or a test in an existing spec): `index.html` has `og:title`, `og:image`, `description`; `og.png` returns 200 with `image/png` |
| `ROADMAP.md` | mark V1 delivered, *Where we stand* |
| `package.json` | `1.0.0` (via `/create-release`, not by hand) |
| Vercel project settings | production domain, redirect from `repulse-ten.vercel.app`, `ALLOWED_ORIGINS` (owner) |
| Supabase auth settings, GitHub OAuth app | Site URL, Redirect URLs, Homepage URL (owner) |

## Definition of done

- [ ] `https://<domain>/` serves the production build; `https://repulse-ten.vercel.app/`
      redirects to it.
- [ ] Sign in with GitHub on `https://<domain>` returns to `https://<domain>` signed in.
- [ ] `(share!)` on the production domain copies a URL starting with `https://<domain>/#v3:`;
      opening it in a fresh window loads the same code.
- [ ] Pasting `https://<domain>` into Slack or Discord shows the title
      "REPuLse — a Lisp that beats", the description and `og.png`.
- [ ] At 375×812 and 320×568, ▶ play is fully inside the viewport and starts the
      loaded demo when tapped (`e2e/mobile.spec.ts`, plus a real iPhone or Android check).
- [ ] With touch emulation the banner
      `Made for a desktop keyboard. Press ▶ play to listen; open on a computer to code.`
      appears; after ✕ and a reload it stays hidden. On desktop it never appears.
- [ ] On a phone the first-visit line reads `… press ▶ play`; on desktop it still reads
      `… press Alt+Enter to play` (check after `(reset!)`).
- [ ] Snippet cards (#73): report and delete sit on the star row's baseline, right-aligned,
      with equal padding, for own and others' snippets; #73 is closed with a screenshot.
- [ ] #11 is closed with the Safari version and the three export cases checked
      (`(export 4)`, `(export :song)` on `(demo :techno)`, `(export 4)` with a bus), or a
      narrower bug is open and Safari export is listed under Known issues.
- [ ] The README shows a working Try-it link and the demo video above the fold; the
      Quick start begins with the hosted app.
- [ ] `docs/DEPLOYMENT.md` has the Custom domain section; no file outside tests mentions a
      domain REPuLse doesn't own.
- [ ] MST1 is delivered and the QA5 parts named in *Depends on* are delivered before the
      release is tagged.
- [ ] `npm run test:all` passes on the release commit; `npx shadow-cljs compile app`
      has no warnings.
- [ ] `v1.0.0` is tagged on `main`, the GitHub release lists features by milestone and a
      Known issues section, and `package.json` says `1.0.0`.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5)

## What NOT to do

- No full mobile/touch layout, on-screen key bar or responsive editor panels. That is O4
  (deferred). V1 only keeps play reachable and says what the phone is for.
- No landing or marketing page, pricing, newsletter sign-up or analytics beyond the
  existing Speed Insights. If D4 calls for legal pages, they are plain static pages.
- No new language features, built-ins, demos or sound changes. Loudness is MST1, track
  visuals are VIZ1.
- Don't do the QA5 security work inside this phase; V1 only waits for it and sets the
  production origin it needs.
- No PWA, offline mode or install prompt (O2).
- Don't change the BYOK model for AI keys or the share-link format (`#v3:`).
