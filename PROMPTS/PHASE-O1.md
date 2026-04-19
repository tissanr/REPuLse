# Phase O1 — Embeddable Component

## Goal

A `<repulse-editor>` custom element that anyone can drop into a blog post,
documentation page, or tutorial and get a fully-functional, live-coded REPuLse
instance — no build step, no account, no server.

```html
<!-- embed a live pattern anywhere -->
<script src="https://repulse.netlify.app/embed.js"></script>

<repulse-editor
  code='(track :kick (seq :bd :bd :bd :bd))'
  autoplay>
</repulse-editor>

<!-- or pre-seed from the curated snippet library -->
<repulse-editor snippet="acid-303" autoplay height="260px"></repulse-editor>
```

**Before:** REPuLse only runs at its own URL. Sharing a pattern requires copying
a `#v2:…` URL and directing someone to the full app.

**After:** Any static website can host a live, editable REPuLse pattern inline.
The snippet library (S1) makes the `snippet="id"` attribute immediately useful.

---

## Background

### Why O1 before the rest of Phase O

Phase O groups four unrelated capabilities (PWA, embed, collab, mobile). The
embeddable component is the most self-contained and has the clearest value
proposition now that S1 exists: snippet IDs give embeds a stable, human-readable
content address.

PWA, collaboration, and mobile are orthogonal and remain in Phase O.

### Relation to existing work

| Existing feature | How O1 reuses it |
|---|---|
| Shadow-cljs build + CLJS app | Embed is a second build target pointing at a new entry namespace |
| CodeMirror 6 editor (`editor.cljs`) | Embed mounts a CM6 view inside the Shadow DOM |
| `(evaluate!)` in eval-orchestrator | Embed calls the same pipeline; no forked audio code |
| Snippet library (`snippets.cljs`) | `snippet="id"` fetches from `/snippets/library.json` and seeds `code` |
| `app/public/` static assets | `embed.js` lands alongside them; Netlify serves it at `/embed.js` |

### What Shadow DOM buys us

The custom element uses Shadow DOM (`{ mode: "open" }`):
- Host-page CSS cannot style inner editor elements
- Inner CSS cannot accidentally affect the host page
- Multiple `<repulse-editor>` instances on the same page are fully isolated

---

## Attributes

| Attribute | Type | Default | Description |
|---|---|---|---|
| `code` | string | `""` | REPuLse-Lisp code to pre-load into the editor |
| `snippet` | string | — | Snippet ID from `library.json`; overrides `code` if both present |
| `autoplay` | boolean (presence) | false | Evaluate `code` automatically on connect |
| `bpm` | number | 120 | Initial BPM; applied before autoplay |
| `height` | CSS length | `"220px"` | Height of the editor region |
| `theme` | `"dark"` \| `"light"` | `"dark"` | Colour theme (light TBD; dark ships first) |

---

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/embed.cljs` | **New** — entry point; defines `<repulse-editor>` custom element |
| `app/src/repulse/embed_css.cljs` | **New** — inline CSS string (subset of `main.css`) for Shadow DOM |
| `app/public/embed-test.html` | **New** — minimal test page with 2–3 `<repulse-editor>` instances |
| `shadow-cljs.edn` | Add `:embed` build target outputting `app/public/embed.js` |
| `app/public/index.html` | No change — embed is a separate bundle |

The embed entry namespace (`repulse.embed`) **must not** import `repulse.app/init`
directly, because `app/init` builds the full-page DOM (header, footer, panels). Instead
it calls only the subset of functions needed for a minimal editor:

- `editor/make-editor` — creates the CM6 view
- `builtins/ensure-env!` + `builtins/init!` — sets up the Lisp environment
- `eo/evaluate!` — runs code
- `audio/get-ctx` — initialises the Web Audio context on first interaction

---

## Implementation sketch

### shadow-cljs.edn

```edn
:embed {:target      :browser
        :output-dir  "app/public"
        :asset-path  "/"
        :modules     {:embed {:init-fn repulse.embed/init!}}
        :compiler-options {:language-out :ecmascript-2020
                           :externs ["app/externs/lezer-lr.js"]}}
```

The `:browser` target with a single module named `:embed` produces
`app/public/embed.js`.

### embed.cljs (skeleton)

```clojure
(ns repulse.embed
  (:require [repulse.ui.editor :as editor]
            [repulse.env.builtins :as builtins]
            [repulse.eval-orchestrator :as eo]
            [repulse.audio :as audio]
            [repulse.snippets :as snippets]
            [repulse.embed-css :refer [EMBED_CSS]]))

(defn- mount-instance! [host-el]
  (let [shadow (.attachShadow host-el #js {:mode "open"})
        style  (doto (.createElement js/document "style")
                 (-> .-textContent (set! EMBED_CSS)))
        wrap   (.createElement js/document "div")]
    (.appendChild shadow style)
    (.appendChild shadow wrap)
    wrap))

(defn- connect-callback [^js this]
  (let [code-attr    (.getAttribute this "code")
        snippet-attr (.getAttribute this "snippet")
        bpm-attr     (js/parseInt (or (.getAttribute this "bpm") "120"))
        height       (or (.getAttribute this "height") "220px")
        autoplay?    (.hasAttribute this "autoplay")]
    (set! (.. this -style -display) "block")
    (let [wrap (mount-instance! this)]
      (set! (.-style wrap) (str "height:" height))
      (builtins/init! {:on-beat-fn      (fn [])
                       :set-playing!-fn (fn [_])
                       :set-output!-fn  (fn [_ _])
                       :make-stop-fn-fn (fn [] (fn [] (audio/stop!)))
                       :share!-fn       (fn [])})
      (builtins/ensure-env!)
      (if snippet-attr
        ;; Fetch snippet by ID then seed
        (do (snippets/load!)
            (add-watch snippets/library-atom ::embed-load
              (fn [_ _ _ _]
                (remove-watch snippets/library-atom ::embed-load)
                (when-let [s (snippets/by-id snippet-attr)]
                  (let [code (:code s)]
                    (let [view (editor/make-editor wrap code eo/evaluate!)]
                      (reset! editor/editor-view view)
                      (when autoplay? (js/setTimeout #(eo/evaluate! code) 150))))))))
        ;; Use code attribute directly
        (let [code (or code-attr "")
              view (editor/make-editor wrap code eo/evaluate!)]
          (reset! editor/editor-view view)
          (when (and autoplay? (seq code))
            (js/setTimeout #(eo/evaluate! code) 150)))))))

(defn init! []
  (when (.-customElements js/window)
    (.define (.-customElements js/window) "repulse-editor"
      (js/class
        (extends js/HTMLElement)
        (connectedCallback [this] (connect-callback this))))))
```

> **Note:** Multiple embed instances on the same page share the WASM audio engine
> (one `AudioContext`). Each instance has its own CM6 editor and Lisp env atom, but
> they share the scheduler's track table. Track name collisions between instances must
> be documented as a known limitation for O1; full isolation is addressed in O.

### embed-css.cljs

A `def EMBED_CSS` string containing the subset of `main.css` needed for the editor:
`body` reset, `.cm-editor`, `.cm-focused`, rainbow delimiters, `.active-event`,
`.cm-lintRange`, hover tooltip styles.

### embed-test.html

```html
<!DOCTYPE html>
<html>
<head>
  <script src="/embed.js" defer></script>
</head>
<body>
  <h2>Default code</h2>
  <repulse-editor
    code='(track :kick (seq :bd :bd :bd :bd))'
    autoplay height="200px">
  </repulse-editor>

  <h2>From snippet library</h2>
  <repulse-editor snippet="acid-303" autoplay height="220px"></repulse-editor>

  <h2>No autoplay</h2>
  <repulse-editor
    code='(track :melody (scale :minor :c4 (seq 0 3 5 7)))'
    height="180px">
  </repulse-editor>
</body>
</html>
```

---

## Definition of done

- [ ] `npx shadow-cljs compile embed` produces `app/public/embed.js` with no errors
- [ ] `embed-test.html` served by dev server loads without JS errors
- [ ] `<repulse-editor code='...' autoplay>` evaluates and plays on page load
- [ ] `<repulse-editor snippet="four-on-the-floor" autoplay>` fetches from `library.json` and plays
- [ ] `<repulse-editor code='...'>` without `autoplay` shows a static editor; Alt+Enter evaluates
- [ ] `height` attribute changes the editor region height
- [ ] Two `<repulse-editor>` instances on the same page do not interfere visually (Shadow DOM isolation confirmed)
- [ ] Host-page CSS (e.g. `* { font-family: serif }`) does not bleed into the editor
- [ ] `embed.js` bundle size reported (target: < 500 KB gzipped; warn if exceeded)
- [ ] No new npm dependencies (collab deps stay out)
- [ ] `npm run test` still passes (core + lisp tests unaffected)

---

## Out of scope (handled in Phase O)

- PWA / service worker / offline support
- Collaborative sessions (Yjs + WebRTC)
- Mobile layout and touch targets
- Light theme
- Multiple isolated audio contexts (one per instance) — O1 shares one `AudioContext`
- `<repulse-editor>` in cross-origin iframes — security/CSP considerations deferred

---

## Open questions

1. **Build output name:** `embed.js` vs `repulse-embed.js` — the latter is clearer for
   third-party users who might host it themselves. Lean toward `repulse-embed.js`.

2. **Multi-instance track collision:** Should O1 namespace track names by instance ID
   (e.g. `:embed-0/kick`)? Simplest for O1: document the limitation; full isolation
   in Phase O proper.

3. **WASM path:** The embed bundle will need to load `repulse_audio_bg.wasm`. The WASM
   load path must be absolute (pointing to the Netlify host) or configurable via a
   `wasm-url` attribute so third-party hosters can serve it themselves.
