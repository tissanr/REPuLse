# Phase O — Platform & Deployment

## Goal

Four capabilities that expand where REPuLse runs and who can use it:

1. **PWA / offline mode** — install REPuLse as a desktop app, work without internet
2. **Embeddable web component** — `<repulse-editor>` custom element for blogs and tutorials
3. **Collaborative sessions** — real-time multi-user editing via WebRTC
4. **Mobile layout** — touch-optimised editor and controls for tablets/phones

```html
<!-- Embeddable component in any website: -->
<repulse-editor code='(seq :bd :sd :bd :sd)' autoplay></repulse-editor>

<!-- PWA install prompt shows a native app-like experience -->
```

```lisp
;; Collaborative session — one user starts, others join:
(collab-start!)         ; → "session: abc123 — share this code"
(collab-join! "abc123") ; → connected, code syncs in real time
```

---

## Background

### Current deployment

REPuLse runs as a single-page app served by `shadow-cljs` dev server or a static file
host. All assets are loaded on demand:

- CLJS bundle (`app.js`) — shadow-cljs compiled output
- WASM module (`repulse_audio_bg.wasm`) — Rust synthesis engine
- CSS (`main.css`) — single stylesheet
- Samples — fetched from Strudel CDN on demand via `samples!`

There is no service worker, no manifest, no offline capability. The app cannot be
installed. It cannot be embedded in other pages. There is no multi-user support.

### CodeMirror 6 features

CM6 has built-in support for:
- **Yjs integration** via `y-codemirror.next` — CRDT-based collaborative editing
- **Mobile editing** — basic touch support, virtual keyboard handling
- **Custom element** hosting — CM6 views can be attached to any DOM element

### WebRTC / Yjs

Yjs is a CRDT library for real-time collaboration. `y-webrtc` provides a WebRTC
transport — peer-to-peer, no server needed for small groups. The flow:

1. Create a `Y.Doc` shared document
2. Bind it to the CodeMirror editor via `y-codemirror.next`
3. Connect via `y-webrtc` with a shared room name
4. All connected peers see each other's edits in real time

Since REPuLse patterns are deterministic (same code = same audio), synced code means
synced audio. No audio streaming is needed.

---

## Design

### 1. PWA: Service worker + manifest

#### Web app manifest

`app/public/manifest.json`:
```json
{
  "name": "REPuLse",
  "short_name": "REPuLse",
  "description": "A Lisp that beats — live coding music in the browser",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#282c34",
  "theme_color": "#c678dd",
  "icons": [
    { "src": "/icons/icon-192.png", "sizes": "192x192", "type": "image/png" },
    { "src": "/icons/icon-512.png", "sizes": "512x512", "type": "image/png" }
  ]
}
```

#### Service worker

`app/public/sw.js` — cache-first strategy for app shell, network-first for samples:

- **App shell** (precache): `index.html`, `app.js`, `main.css`, `repulse_audio_bg.wasm`,
  `worklet.js`, built-in plugin JS files
- **Samples** (cache-on-use): Strudel CDN URLs cached after first fetch, available offline
- **API calls** (network-only): Freesound, Gist API — not cached

The service worker uses the Cache API (`caches.open`, `cache.addAll`, `cache.match`).
Cache versioning via a `CACHE_VERSION` constant ensures stale caches are cleaned up on
update.

#### Registration

In `app/src/repulse/app.cljs`, after DOM mount:

```clojure
(when (.-serviceWorker js/navigator)
  (.register (.-serviceWorker js/navigator) "/sw.js"))
```

#### Offline sample management

"Download for offline" flow:
- `(download-bank! :AkaiLinn)` — fetches all sample URLs for a bank and caches them
- The sample registry (`samples.cljs`) checks the service worker cache before network

### 2. Embeddable web component

#### Architecture

A `<repulse-editor>` custom element that bundles a complete REPuLse instance:

```html
<repulse-editor
  code='(seq :bd :sd :bd :sd)'
  bpm="120"
  autoplay
  height="200px">
</repulse-editor>
```

Built as a separate shadow-cljs build target (`:esm` or `:browser`) that outputs a
single JS file. Uses Shadow DOM for style isolation.

#### Build target

Add to `shadow-cljs.edn`:

```edn
:embed {:target    :esm
        :output-dir "dist/"
        :modules   {:repulse-embed {:init-fn repulse.embed/init!}}
        :js-options {:output-format :iife}}
```

#### Implementation

New file: `app/src/repulse/embed.cljs`:

```clojure
(ns repulse.embed
  (:require [repulse.app :as app]))

(defn init! []
  (when (.-customElements js/window)
    (.define (.-customElements js/window) "repulse-editor"
      (js/class
        (extends js/HTMLElement)
        (constructor [this]
          (super)
          (let [shadow (.attachShadow this #js {:mode "open"})
                style  (.createElement js/document "style")
                editor (.createElement js/document "div")]
            (set! (.-textContent style) EMBED_CSS)
            (.appendChild shadow style)
            (.appendChild shadow editor)
            (set! (.-_editorEl this) editor)))
        (connectedCallback [this]
          (let [code    (or (.getAttribute this "code") "(seq :bd :sd)")
                bpm     (js/parseInt (or (.getAttribute this "bpm") "120"))
                auto?   (.hasAttribute this "autoplay")
                height  (or (.getAttribute this "height") "200px")]
            (set! (.. this -_editorEl -style -height) height)
            (app/mount-editor! (.-_editorEl this) code)
            (when auto?
              (js/setTimeout
                #(app/evaluate! code) 100))))))))
```

The component:
1. Creates a Shadow DOM root
2. Injects scoped CSS (a subset of `main.css`)
3. Mounts a CM6 editor inside the shadow root
4. Optionally auto-evaluates the `code` attribute

### 3. Collaborative sessions via Yjs + WebRTC

#### Dependencies

```bash
npm install yjs y-webrtc y-codemirror.next
```

These are the only new external dependencies in the project (they go in `app/package.json`).

#### Module

New file: `app/src/repulse/collab.cljs`:

```clojure
(ns repulse.collab
  (:require ["yjs" :as Y]
            ["y-webrtc" :refer [WebrtcProvider]]
            ["y-codemirror.next" :refer [yCollab yUndoManagerKeymap]]))

(defonce doc (atom nil))
(defonce provider (atom nil))

(defn start-session!
  "Create a new collaborative session. Returns the room code."
  [editor-view]
  (let [room-id   (str "repulse-" (subs (str (random-uuid)) 0 8))
        ydoc      (Y/Doc.)
        ytext     (.getText ydoc "code")
        wp        (WebrtcProvider. room-id ydoc
                    #js {:signaling #js ["wss://signaling.yjs.dev"]})]
    ;; Seed the document with current editor content
    (.insert ytext 0 (.. editor-view -state -doc (toString)))
    (reset! doc ydoc)
    (reset! provider wp)
    ;; Return extensions to add to the editor
    {:room-id    room-id
     :extensions [(yCollab ytext)
                  (yUndoManagerKeymap)]}))

(defn join-session!
  "Join an existing collaborative session by room code."
  [room-id editor-view]
  (let [ydoc (Y/Doc.)
        ytext (.getText ydoc "code")
        wp    (WebrtcProvider. room-id ydoc
                #js {:signaling #js ["wss://signaling.yjs.dev"]})]
    (reset! doc ydoc)
    (reset! provider wp)
    {:room-id    room-id
     :extensions [(yCollab ytext)
                  (yUndoManagerKeymap)]}))

(defn stop-session! []
  (when-let [wp @provider]
    (.destroy wp)
    (reset! provider nil))
  (when-let [d @doc]
    (.destroy d)
    (reset! doc nil)))
```

#### Editor integration

When a session starts/joins, the returned CM6 extensions are dynamically added to the
editor view via `EditorView.dispatch({effects: StateEffect.appendConfig.of(extensions)})`.

#### Lisp bindings

In `app.cljs` `ensure-env!`:

```clojure
"collab-start!"
(fn []
  (when-let [view @editor-view]
    (let [{:keys [room-id extensions]} (collab/start-session! view)]
      ;; Add collaboration extensions to editor
      (.dispatch view
        #js {:effects (.of cm-state/StateEffect.appendConfig
                           (clj->js extensions))})
      (str "session started — share code: " room-id))))

"collab-join!"
(fn [code]
  (let [room-id (leval/unwrap code)]
    (when-let [view @editor-view]
      (let [{:keys [extensions]} (collab/join-session! room-id view)]
        (.dispatch view
          #js {:effects (.of cm-state/StateEffect.appendConfig
                             (clj->js extensions))})
        (str "joined session " room-id)))))

"collab-stop!"
(fn []
  (collab/stop-session!)
  "session ended")
```

### 4. Mobile layout

#### CSS changes

Media queries in `main.css` for screens ≤768px:

```css
@media (max-width: 768px) {
  .editor-wrapper {
    font-size: 14px;
    height: 50vh;
  }
  .command-bar {
    font-size: 16px;  /* prevents iOS zoom */
    height: 48px;
    padding: 8px 12px;
  }
  .context-panel {
    position: fixed;
    bottom: 0;
    left: 0;
    right: 0;
    height: 30vh;
    overflow-y: auto;
  }
  .toolbar {
    flex-wrap: wrap;
    gap: 4px;
  }
  .toolbar button {
    min-width: 44px;
    min-height: 44px;  /* touch target */
  }
}
```

#### Touch input

Add touch-friendly play/stop buttons with larger hit targets. The command bar input
gets `font-size: 16px` to prevent iOS auto-zoom.

#### Drum pad mode (optional stretch goal)

A visual grid of 8–16 pads that trigger samples on touch. Each pad maps to a keyword
(`:bd`, `:sd`, `:hh`, etc.) and calls `audio/play-event` directly. This is a visual
plugin, not a core feature.

---

## Implementation order

1. **PWA** — service worker + manifest + icon generation + registration
2. **Mobile layout** — CSS media queries + touch targets
3. **Embeddable component** — shadow-cljs build target + custom element
4. **Collaborative sessions** — Yjs + WebRTC + editor integration

PWA and mobile are independent and can be done in parallel. The component and collab
features each require more significant integration work.

---

## Files to change

| File | Change |
|---|---|
| `app/public/manifest.json` | **New** — PWA manifest |
| `app/public/sw.js` | **New** — service worker with cache-first strategy |
| `app/public/icons/icon-192.png` | **New** — PWA icon (192×192) |
| `app/public/icons/icon-512.png` | **New** — PWA icon (512×512) |
| `app/public/index.html` | Add `<link rel="manifest">`, `<meta name="theme-color">` |
| `app/src/repulse/app.cljs` | Service worker registration; `collab-start!`, `collab-join!`, `collab-stop!`, `download-bank!` bindings |
| `app/src/repulse/embed.cljs` | **New** — `<repulse-editor>` custom element |
| `app/src/repulse/collab.cljs` | **New** — Yjs/WebRTC session management |
| `app/public/css/main.css` | Mobile media queries, touch targets |
| `shadow-cljs.edn` | `:embed` build target |
| `app/package.json` | Add `yjs`, `y-webrtc`, `y-codemirror.next` dependencies |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add `collab-start!`, `collab-join!`, `collab-stop!`, `download-bank!` |
| `app/src/repulse/lisp-lang/completions.js` | Add 4 entries |
| `app/src/repulse/lisp-lang/hover.js` | Add hover docs for new built-ins |
| `docs/USAGE.md` | New sections: "PWA / offline", "Embedding", "Collaboration", "Mobile" |
| `README.md` | Add new feature rows to capabilities table |
| `CLAUDE.md` | Mark Phase O as delivered when done |

---

## External dependencies

| Package | Purpose | Reason |
|---|---|---|
| `yjs` | CRDT document model | Industry standard for collaborative editing |
| `y-webrtc` | Peer-to-peer transport | No server needed for ≤10 users |
| `y-codemirror.next` | CM6 binding for Yjs | Native integration, handles cursors + awareness |

These are the first external JS dependencies added to REPuLse's `app/` layer. They are
only loaded when collaboration is activated (dynamic import or code-split).

---

## Definition of done

### PWA / offline

- [ ] Visiting REPuLse shows a browser install prompt (Chrome "Add to Home Screen")
- [ ] Installing creates a standalone window with the REPuLse icon
- [ ] After installation, the app loads without internet (airplane mode test)
- [ ] The CLJS bundle, WASM module, and CSS are cached by the service worker
- [ ] Built-in drum sounds (`:bd`, `:sd`, `:hh`, `:oh`) work offline
- [ ] `(download-bank! :AkaiLinn)` caches the bank's samples for offline use
- [ ] After downloading a bank, its samples play offline
- [ ] Service worker updates when a new version is deployed (cache busting)
- [ ] `manifest.json` passes Chrome Lighthouse PWA audit

### Embeddable component

- [ ] `<repulse-editor code='(seq :bd :sd)'>` renders an editor in any HTML page
- [ ] The component is style-isolated via Shadow DOM
- [ ] `autoplay` attribute starts playback on load
- [ ] `bpm` attribute sets the tempo
- [ ] `height` attribute controls the editor height
- [ ] The component works in a page with its own CSS without conflicts
- [ ] A single `repulse-embed.js` file is all that's needed to use the component
- [ ] Multiple `<repulse-editor>` instances on the same page work independently
- [ ] `npm run build:embed` produces the embeddable bundle

### Collaborative sessions

- [ ] `(collab-start!)` returns a room code
- [ ] `(collab-join! "code")` connects to the session
- [ ] Two users see each other's edits in real time
- [ ] Pressing Ctrl+Enter on either side produces sound on both sides
- [ ] Remote cursor positions are visible (Yjs awareness)
- [ ] `(collab-stop!)` cleanly disconnects
- [ ] Works peer-to-peer without a custom server
- [ ] Sessions survive page reloads (reconnect with same room code)

### Mobile layout

- [ ] On a 768px-wide screen, the editor fills the top half, controls fill the bottom
- [ ] All buttons have ≥44px touch targets
- [ ] The command bar input doesn't trigger iOS auto-zoom (font-size ≥16px)
- [ ] Play/stop is accessible via large touch buttons
- [ ] The context panel scrolls vertically on small screens
- [ ] The toolbar wraps gracefully on narrow screens

### No regressions

- [ ] Desktop layout is unaffected by mobile CSS (media queries only)
- [ ] All existing core tests pass (`npm run test:core`)
- [ ] Non-PWA browser experience is unchanged
- [ ] Service worker doesn't interfere with dev server hot-reload
