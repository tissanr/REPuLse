# Phase D2 — Full Session Persistence

## Goal

Persist **all session state** to localStorage so that reloading the page restores
exactly what the user had — effects, bank prefix, sample sources, mute/solo state,
MIDI mappings, and BPM. Add a `(reset!)` command that wipes everything back to
defaults. New sessions (first visit or after reset) start with a demo loaded
automatically.

```lisp
;; User builds a session:
(bpm 140)
(fx :reverb 0.3)
(fx :delay :wet 0.4 :time 0.25)
(bank :AkaiLinn)
(samples! "github:user/my-samples")
(play :drums (seq :bd :sd :bd :sd))
(mute! :perc)

;; Reload the page → everything is restored exactly as it was

;; Wipe it all:
(reset!)
;; → stops playback, clears all state, loads default demo
```

---

## Background

### Current persistence (Phase D)

Only two localStorage keys exist:

| Key | What | Saved when |
|-----|------|-----------|
| `"repulse-editor"` | Editor buffer text | Every document change |
| `"repulse-bpm"` | BPM number | Every scheduler state change |

Everything else — effects, bank prefix, sample sources, mute/solo, MIDI mappings —
is lost on reload.

### Current boot sequence

1. Build DOM
2. Initialize eval environment + fetch default sample manifests
3. Restore BPM from localStorage (or default 120)
4. Check URL hash for shared session (`#v1:...`)
5. Create editors, load editor text from localStorage (or fallback `"(seq :bd :sd :bd :sd)"`)
6. Auto-load all built-in plugins (visual + effect)
7. Start reactivity watchers
8. Render panels

### State that should persist

| State | Where it lives | Format |
|-------|---------------|--------|
| Editor text | CodeMirror doc | string |
| BPM | `audio/scheduler-state :bpm` | number |
| Effect chain | `fx/chain` atom | `[{:name "reverb" :params {:wet 0.3}} ...]` |
| Effect bypass | `fx/chain` each entry | `:bypassed? true/false` |
| Bank prefix | `samples/active-bank-prefix` | keyword or nil |
| External sample repos | `samples/loaded-sources` (Phase E2) | `[{:type :github :id "owner/repo"} ...]` |
| Track mute state | `audio/scheduler-state :muted` | set of keywords |
| MIDI CC mappings | `midi/cc-mappings` (Phase N1) | `{cc-num {:target kw :track kw}}` |

### What should NOT persist

| State | Why |
|-------|-----|
| Playing/stopped | Always start stopped — user clicks play |
| AudioContext, nodes | Web Audio objects can't be serialized |
| Sample buffers | Re-fetched on demand from cache/CDN |
| Active patterns | Rebuilt by re-evaluating the editor code |
| Cycle position | Meaningless across sessions |
| Eval environment | Rebuilt from code + built-ins on init |

---

## Design

### Single localStorage key

Replace the two separate keys with a single JSON blob under `"repulse-session"`:

```json
{
  "v": 2,
  "editor": "(bpm 140)\n(play :drums (seq :bd :sd :bd :sd))",
  "bpm": 140,
  "fx": [
    {"name": "reverb", "params": {"wet": 0.3}, "bypassed": false},
    {"name": "delay", "params": {"wet": 0.4, "time": 0.25, "feedback": 0.4}, "bypassed": false},
    {"name": "filter", "params": {"value": 800}, "bypassed": true}
  ],
  "bank": "AkaiLinn",
  "sources": [
    {"type": "github", "id": "user/my-samples"}
  ],
  "muted": ["perc"],
  "midi": {
    "1": {"target": "filter"},
    "7": {"target": "amp"}
  }
}
```

Version field (`"v": 2`) allows future schema migrations. The old keys
(`"repulse-editor"`, `"repulse-bpm"`) are read once for migration, then deleted.

### Save strategy

**Debounced auto-save.** A single `save-session!` function serializes all state
and writes to localStorage. Called via a debounced wrapper (300ms) from all
relevant watchers:

```clojure
(defonce save-timeout (atom nil))

(defn save-session! []
  (try
    (let [session (build-session-snapshot)]
      (.setItem js/localStorage "repulse-session"
                (js/JSON.stringify (clj->js session))))
    (catch :default _ nil)))  ;; silent fail for private browsing

(defn schedule-save! []
  (when-let [id @save-timeout]
    (js/clearTimeout id))
  (reset! save-timeout
    (js/setTimeout save-session! 300)))
```

Watchers on all relevant atoms call `schedule-save!`:
- `audio/scheduler-state` — BPM, muted set
- `fx/chain` — effect list, params, bypass state
- `samples/active-bank-prefix` — bank prefix
- `samples/loaded-sources` — external sources (Phase E2)
- `midi/cc-mappings` — MIDI mappings (Phase N1)
- Editor `updateListener` — editor text

### Restore strategy

On boot, `restore-session!` reads the JSON blob and applies each piece of state
in the right order:

```
1. Parse "repulse-session" from localStorage
2. If not found, check for legacy keys ("repulse-editor", "repulse-bpm")
3. If nothing found → first visit → load default demo

Restoration order (dependencies matter):
  a. BPM                    — no deps
  b. Bank prefix             — no deps
  c. External sample sources — triggers async fetch, must complete before eval
  d. Effect chain            — plugins must be loaded first (wait for init)
  e. Effect bypass states    — after effects are created
  f. Editor text             — set CodeMirror content
  g. Muted tracks            — after eval creates tracks
  h. MIDI mappings           — after midi module exists (Phase N1)
```

### Restore timing

Some state needs to be restored after async steps complete:

```
Boot
  │
  ├── Sync: set BPM, bank prefix, editor text
  │
  ├── Async: fetch external sample sources (samples!)
  │     └── on complete: continue
  │
  ├── After plugins loaded: restore effect chain + bypass
  │
  └── After editor eval (if auto-eval on restore):
        └── restore muted tracks
```

The editor text is restored but **not auto-evaluated**. The user hits Alt+Enter
to start playing. This avoids surprise audio on page load. Mute/solo state is
restored after the first manual eval.

Track mute state is stored and applied the first time `schedule-cycle!` runs
after eval — the scheduler checks `restored-mutes` and applies them.

### `(reset!)` command

Wipes all persisted state and reloads to a clean default:

```clojure
"reset!"
(fn []
  (audio/stop!)                                    ;; stop playback
  (.removeItem js/localStorage "repulse-session")  ;; clear persistence
  (.removeItem js/localStorage "repulse-editor")   ;; clear legacy keys
  (.removeItem js/localStorage "repulse-bpm")
  (.reload js/window.location))                    ;; full page reload → triggers first-visit flow
```

After reload, no session is found → first-visit flow → demo loads.

### First-visit demo

When no persisted session exists (first visit or after `(reset!)`), load a
random demo template automatically:

```clojure
(defn first-visit-setup! []
  (let [demos [:techno :ambient :house :dnb :minimal]
        pick  (rand-nth demos)
        demo  (get demo-templates pick)]
    ;; Set editor content to the demo code
    (set-editor-content! (:code demo))
    ;; Set BPM
    (audio/set-bpm! (:bpm demo))
    ;; Don't auto-play — user hits Alt+Enter
    (set-output! (str "Welcome to REPuLse! Loaded demo: " (name pick)
                      " — press Alt+Enter to play")
                 :success)))
```

The demo code is placed in the editor but not evaluated. A welcome message
tells the user what to do. This is friendlier than a blank `(seq :bd :sd :bd :sd)`.

### URL session override

If the URL contains a `#v1:...` hash (shared session), it takes precedence over
localStorage. This is already the case — keep this behavior.

### Migration from Phase D

On first load with the new code:

```clojure
(defn migrate-legacy-keys! []
  (let [editor (.getItem js/localStorage "repulse-editor")
        bpm    (.getItem js/localStorage "repulse-bpm")]
    (when (or editor bpm)
      ;; Build a v2 session from legacy keys
      (let [session {:v 2
                     :editor (or editor "(seq :bd :sd :bd :sd)")
                     :bpm    (or (some-> bpm js/parseFloat) 120)
                     :fx     []
                     :bank   nil
                     :sources []
                     :muted  []
                     :midi   {}}]
        (.setItem js/localStorage "repulse-session"
                  (js/JSON.stringify (clj->js session)))
        ;; Clean up legacy keys
        (.removeItem js/localStorage "repulse-editor")
        (.removeItem js/localStorage "repulse-bpm")
        session))))
```

---

## Implementation

### 1. `app/src/repulse/session.cljs` — **New** persistence module

```clojure
(ns repulse.session
  (:require [repulse.audio :as audio]
            [repulse.fx :as fx]
            [repulse.samples :as samples]))

(def current-version 2)
(def storage-key "repulse-session")

;;; ── Build snapshot ────────────────────────────────────────────────

(defn build-session-snapshot
  "Collect all session state into a serializable map."
  [get-editor-text]
  {:v       current-version
   :editor  (get-editor-text)
   :bpm     (or (:bpm @audio/scheduler-state) 120)
   :fx      (mapv (fn [{:keys [name params bypassed?]}]
                    {:name name :params (or params {}) :bypassed (boolean bypassed?)})
                  @fx/chain)
   :bank    (when-let [b @samples/active-bank-prefix] (cljs.core/name b))
   :sources (vec @samples/loaded-sources)
   :muted   (mapv cljs.core/name (:muted @audio/scheduler-state))
   ;; Phase N1 — include if atom exists
   ;; :midi (serialize-midi-mappings)
   })

;;; ── Save (debounced) ─────────────────────────────────────────────

(defonce save-timeout (atom nil))

(defn save-session! [get-editor-text]
  (try
    (let [data (build-session-snapshot get-editor-text)]
      (.setItem js/localStorage storage-key
                (js/JSON.stringify (clj->js data))))
    (catch :default _ nil)))

(defn schedule-save! [get-editor-text]
  (when-let [id @save-timeout]
    (js/clearTimeout id))
  (reset! save-timeout
    (js/setTimeout #(save-session! get-editor-text) 300)))

;;; ── Load ─────────────────────────────────────────────────────────

(defn load-session
  "Read session from localStorage. Returns a map or nil."
  []
  (try
    (when-let [raw (.getItem js/localStorage storage-key)]
      (let [data (js->clj (js/JSON.parse raw) :keywordize-keys true)]
        (when (= (:v data) current-version)
          data)))
    (catch :default _ nil)))

;;; ── Migration ────────────────────────────────────────────────────

(defn migrate-legacy!
  "Convert Phase D keys to v2 session format. Returns session or nil."
  []
  (let [editor (.getItem js/localStorage "repulse-editor")
        bpm    (.getItem js/localStorage "repulse-bpm")]
    (when (or editor bpm)
      (let [session {:v       current-version
                     :editor  (or editor "(seq :bd :sd :bd :sd)")
                     :bpm     (or (some-> bpm js/parseFloat) 120)
                     :fx      []
                     :bank    nil
                     :sources []
                     :muted   []
                     :midi    {}}]
        (.setItem js/localStorage storage-key
                  (js/JSON.stringify (clj->js session)))
        (.removeItem js/localStorage "repulse-editor")
        (.removeItem js/localStorage "repulse-bpm")
        session))))

;;; ── Reset ────────────────────────────────────────────────────────

(defn wipe!
  "Delete all persisted state."
  []
  (.removeItem js/localStorage storage-key)
  (.removeItem js/localStorage "repulse-editor")
  (.removeItem js/localStorage "repulse-bpm"))
```

### 2. `app/src/repulse/app.cljs` — boot sequence changes

Replace the current ad-hoc localStorage reads with a unified restore flow:

```clojure
(defn init []
  ;; ... (existing DOM setup, env init, plugin loading) ...

  ;; ── Session restore ──────────────────────────────────
  (let [url-session (decode-url-session)
        stored      (or (session/load-session)
                        (session/migrate-legacy!))
        session     (or url-session stored)]

    (if session
      ;; Restore existing session
      (do
        (audio/set-bpm! (or (:bpm session) 120))
        (when (:bank session)
          (reset! samples/active-bank-prefix (keyword (:bank session))))
        ;; Reload external sample sources (async)
        (doseq [{:keys [type id]} (:sources session)]
          (case type
            "github" (samples/load-github-repo! id)
            nil))
        ;; Set editor content
        (set-editor-content! (:editor session))
        ;; Restore effects after plugins are loaded (next tick)
        (js/setTimeout
          (fn []
            (doseq [{:keys [name params bypassed]} (:fx session)]
              (when-let [plugin (fx/find-plugin name)]
                (fx/set-params! name params)
                (when bypassed (fx/bypass! name))))
            ;; Store muted tracks for application after first eval
            (reset! pending-mutes (set (map keyword (:muted session)))))
          100))

      ;; First visit — load a random demo
      (first-visit-setup!)))

  ;; ── Save watchers ───────────────────────────────────
  (let [save! #(session/schedule-save! get-editor-text)]
    (add-watch audio/scheduler-state :session-save (fn [_ _ _ _] (save!)))
    (add-watch fx/chain              :session-save (fn [_ _ _ _] (save!)))
    (add-watch samples/active-bank-prefix :session-save (fn [_ _ _ _] (save!)))
    (add-watch samples/loaded-sources     :session-save (fn [_ _ _ _] (save!))))

  ;; ... (rest of existing init) ...
  )
```

### 3. `app/src/repulse/app.cljs` — `reset!` built-in

Add to `ensure-env!`:

```clojure
"reset!"
(fn []
  (audio/stop!)
  (session/wipe!)
  (.reload js/window.location)
  nil)
```

### 4. `app/src/repulse/app.cljs` — first-visit demo

```clojure
(defn- first-visit-setup! []
  (let [demos [:techno :ambient :house :dnb :minimal]
        pick  (rand-nth demos)
        demo  (get demo-templates pick)]
    (set-editor-content! (:code demo))
    (audio/set-bpm! (:bpm demo))
    (set-output!
      (str "Welcome to REPuLse! Loaded :" (name pick)
           " — press Alt+Enter to play")
      :success)))
```

### 5. Grammar, completions, hover docs

Add `reset!` to the grammar, completions, and hover docs:

**Grammar** — add to `BuiltinName`:
```
"reset!" |
```

**completions.js:**
```javascript
{ label: "reset!", type: "function", detail: "(reset!) — wipe session, restore defaults" },
```

**hover.js:**
```javascript
"reset!": { sig: "(reset!)", doc: "Stop playback, clear all persisted state (effects, bank, samples, mute/solo, MIDI mappings, editor), and reload with a fresh demo." },
```

Run `npm run gen:grammar` after editing the grammar.

### 6. URL session encoding — upgrade to v2

Update `share!` to include the full session state, not just editor + BPM:

```clojure
(defn share! []
  (let [session (session/build-session-snapshot get-editor-text)
        json    (js/JSON.stringify (clj->js session))
        encoded (js/btoa json)]
    (set! (.-hash js/window.location) (str "v2:" encoded))
    "Session URL copied to address bar"))
```

Keep backward compatibility: `decode-url-session` handles both `#v1:` and `#v2:`
prefixes.

---

## Files to change

| File | Change |
|------|--------|
| `app/src/repulse/session.cljs` | **New** — save, load, migrate, wipe |
| `app/src/repulse/app.cljs` | Unified boot restore; `reset!` and `first-visit-setup!`; save watchers; remove old localStorage reads; upgrade `share!` |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add `reset!` to `BuiltinName` |
| `app/src/repulse/lisp-lang/completions.js` | Add `reset!` entry |
| `app/src/repulse/lisp-lang/hover.js` | Add `reset!` hover doc |

No changes to `packages/core/`, `packages/lisp/`, or `packages/audio/` (Rust/WASM).

---

## Dependencies

- **Phase E2** (soft) — `loaded-sources` atom for sample source tracking. If E2
  isn't implemented yet, D2 creates the atom or skips source persistence.
- **Phase N1** (soft) — MIDI mapping persistence. If N1 isn't implemented yet,
  the `midi` field is omitted from the snapshot.

D2 can be implemented independently — it just won't persist sources/MIDI until
those phases exist.

---

## Definition of done

### Persistence

- [ ] Reload the page → editor text, BPM, effects, bank prefix all restored
- [ ] Effect parameters restored (reverb wet 0.3 stays 0.3)
- [ ] Effect bypass state restored (bypassed effects stay bypassed)
- [ ] Bank prefix restored (`(bank :AkaiLinn)` survives reload)
- [ ] External sample sources re-fetched (`samples!` repos reloaded)
- [ ] Track mute/solo state restored after first eval
- [ ] MIDI CC mappings restored (when Phase N1 exists)
- [ ] Save is debounced (300ms) — no localStorage thrashing

### Migration

- [ ] Existing users with `"repulse-editor"` / `"repulse-bpm"` keys are migrated
- [ ] Legacy keys are deleted after migration
- [ ] Migration happens once, silently

### `(reset!)`

- [ ] Stops all playback
- [ ] Clears all localStorage keys
- [ ] Page reloads to first-visit state
- [ ] After reset, a random demo is loaded
- [ ] `reset!` has syntax highlighting, completion, and hover doc

### First visit

- [ ] Brand new user (no localStorage) sees a random demo loaded in the editor
- [ ] Welcome message tells them to press Alt+Enter
- [ ] Demo is NOT auto-played (no surprise audio)
- [ ] BPM is set to match the demo

### URL sharing

- [ ] `(share!)` encodes full session state (v2 format)
- [ ] Loading a `#v2:...` URL restores effects, bank, BPM, editor
- [ ] Old `#v1:...` URLs still work (backward compatible)
- [ ] URL session takes precedence over localStorage

### Edge cases

- [ ] Private browsing (localStorage unavailable) — no errors, no persistence
- [ ] Corrupted localStorage JSON — falls back to first-visit flow
- [ ] Missing fields in stored session — defaults used for missing values
- [ ] Plugin not yet loaded when restoring effects — effects applied after plugin init

### No regressions

- [ ] All existing core tests pass (`npm run test:core`)
- [ ] Basic playback: `(seq :bd :sd :bd :sd)`
- [ ] Effects: `(fx :reverb 0.3)` — applied and audible
- [ ] `(stop)` stops playback
- [ ] `(demo :techno)` still works manually
- [ ] `(share!)` produces a working URL
- [ ] Alt+Enter evaluates the buffer
- [ ] Context panel displays correctly
