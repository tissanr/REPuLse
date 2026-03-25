# Phase E2 — Live Session Dashboard

## Goal

Upgrade the session context panel from a basic status display into a **live mirror
of everything happening in the session**. If it's playing, mapped, loaded, or active,
it's visible.

```
──── Status ────────────────────────
120 BPM  ● playing  [wasm]  AkaiLinn

──── Tracks ────────────────────────
▶ :drums    amp 0.8  pan -0.3  decay 0.2
▶ :bass     amp 0.6  synth :saw  attack 0.05
▶ :melody   amp 0.4  bank :tabla
■ :perc     (muted)
★ :lead     (solo)

──── Arrangement ───────────────────
scene 2/4 ▸ :drop  [cycle 3/8]

──── FX ────────────────────────────
reverb      wet 0.30
filter      freq 1200

──── MIDI ──────────────────────────
CC #1 → :filter   [====----] 1247 Hz
CC #7 → :amp      [=======-]  0.92

──── Sources ───────────────────────
♫ github:tidalcycles/Dirt-Samples
♫ freesound: kick 808 (3 loaded)

──── Bindings ──────────────────────
kick        pattern
my-scale    fn
```

---

## Background

### Current context panel (Phase E)

The panel lives in `app/src/repulse/app.cljs` — `render-context-panel!` builds
innerHTML and writes it to three DOM containers. It watches four atoms:

| Atom | What triggers re-render |
|------|------------------------|
| `env-atom` | User `def` bindings change |
| `fx/chain` | Effects added, removed, or bypassed |
| `audio/scheduler-state` | BPM changes, playing/stopped |
| `samples/active-bank-prefix` | `(bank :name)` called |

**What it currently shows:**
1. Status bar: BPM, bank prefix, playing indicator
2. Bindings: user-defined names + inferred type
3. Effects: all effects (even if none active), name + first param

**What it does NOT show:**
- Per-track parameters (amp, pan, decay, attack, release, synth, sample bank)
- Track mute/solo state
- Active arrangement or scene
- MIDI mappings
- Loaded external sources (GitHub repos, Freesound)
- Audio backend (WASM vs JS)
- Conditional FX display (always shown even when empty)

### Where the data lives

| Data | Atom / Source |
|------|--------------|
| Track patterns | `audio/scheduler-state` → `:tracks {name pattern}` |
| Track mute/solo | `audio/scheduler-state` → `:muted #{...}` |
| Per-event params | Query one cycle of each track's pattern → extract from event values |
| Track FX chains | `audio/track-nodes` → `{name {:fx-chain [...]}}` |
| Global FX chain | `fx/chain` atom |
| Arrangement state | `audio/scheduler-state` → `:arrangement`, `:scene-index`, `:scene-cycle` |
| MIDI CC mappings | `midi/cc-mappings` atom (Phase N1) |
| MIDI param overrides | `midi/param-overrides` atom (Phase N1) |
| Loaded sample repos | `samples/loaded-repos` or similar atom (track what `samples!` loaded) |
| Freesound results | Need a new atom or extend sample registry metadata |
| Audio backend | `audio/scheduler-state` → `:backend` or detect from WASM module presence |
| User bindings | `env-atom` |
| BPM | `audio/scheduler-state` → `:bpm` |
| Bank prefix | `samples/active-bank-prefix` |

---

## Design

### Section architecture

The panel is divided into **collapsible sections**, each rendered independently.
A section is hidden when it has no content (conditional display).

```
┌─────────────────────────────┐
│ Status     always visible   │
├─────────────────────────────┤
│ Tracks     if any tracks    │
├─────────────────────────────┤
│ Arrangement if arrange/scene│
├─────────────────────────────┤
│ FX         if any active    │ ← hidden when no effects
├─────────────────────────────┤
│ MIDI       if any mappings  │ ← hidden until Phase N1
├─────────────────────────────┤
│ Sources    if any loaded    │
├─────────────────────────────┤
│ Bindings   if any defs      │
└─────────────────────────────┘
```

### Per-track parameter extraction

To show what params a track uses, query one cycle of its pattern and collect
the parameter keys from the event values:

```clojure
(defn extract-track-params
  "Query cycle 0 of a pattern and collect unique param keys from event values."
  [pattern]
  (let [events (core/query pattern {:start [0 1] :end [1 1]})
        maps   (filter map? (map :value events))
        ;; Collect all param keys that appear, with their first value
        param-keys #{:amp :attack :decay :release :pan :synth :rate :begin :end :loop}
        found  (reduce (fn [acc m]
                         (reduce (fn [a k]
                                   (if (and (contains? m k) (not (contains? a k)))
                                     (assoc a k (get m k))
                                     a))
                                 acc param-keys))
                       {} maps)]
    found))
```

This gives `{:amp 0.8 :decay 0.2 :pan -0.3}` — the params actually in use.

For sample banks, check if event values contain `:bank` keys.

For synth type, check for `:synth` keys (`:saw`, `:square`, `:fm`, `:noise`).

### Track display

Each track line shows:
- **State icon**: `▶` playing, `■` muted, `★` solo
- **Track name**: bold cyan
- **Params**: only the ones that are defined in the pattern (compact, inline)
- **MIDI overrides**: if Phase N1 `param-overrides` has values for this track, show them with a different indicator (e.g. `decay 0.2→0.5` or small `M` badge)

```
▶ :drums    amp 0.8  pan -0.3  decay 0.2
▶ :bass     amp 0.6  synth :saw  M:decay
■ :perc     (muted)
★ :lead     (solo)  amp 1.0
```

### Conditional FX display

Only render the FX section when `fx/chain` contains at least one active (non-bypassed)
effect. When all effects are bypassed or none exist, hide the section entirely.

### Sources section

Track what has been loaded from external sources:

**GitHub repos** — `(samples! "github:owner/repo")` already loads samples. Add
metadata tracking: store the repo identifier when loaded.

**Freesound** — `(freesound! "query")` loads samples from Freesound (Phase N).
Track the query string and count of loaded samples.

**Strudel CDN** — the built-in sample library loaded at startup. Show only if
the user might care (optional — could omit since it's always present).

```
──── Sources ───────────────────
♫ github:tidalcycles/Dirt-Samples
♫ github:myuser/my-samples (12 banks)
♫ freesound: "kick 808" (3 samples)
```

Implementation: add a `loaded-sources` atom in `samples.cljs`:

```clojure
(defonce loaded-sources (atom []))
;; [{:type :github :id "owner/repo" :banks 12}
;;  {:type :freesound :query "kick 808" :count 3}]
```

Append to this atom in `samples!` and (future) `freesound!` handlers.

### Arrangement section

When `(arrange ...)` or `(play-scenes ...)` is active, show:
- Current scene name or index
- Which cycle within the scene
- Total scenes

```
──── Arrangement ───────────────
scene 2/4 ▸ :drop  [cycle 3/8]
```

Data source: `audio/scheduler-state` already tracks arrangement state from Phase 8.

### MIDI section

Prepare the UI slot. When `midi/cc-mappings` is empty, hide the section.
When Phase N1 is implemented, the section appears automatically:

```
──── MIDI ──────────────────────
CC #1 → :filter   1247 Hz
CC #7 → :amp       0.92
```

For now, the panel code should check if the `repulse.midi` namespace exists
(or if `cc-mappings` atom is available) and render conditionally. This keeps
E2 independent of N1 — it works whether N1 is implemented or not.

### Status bar enhancements

Add to the existing status line:
- **Audio backend**: `[wasm]` or `[js]` — small gray tag
- **Bank prefix**: already shown, keep it

```
120 BPM  ● playing  [wasm]  AkaiLinn
```

---

## Implementation

### 1. `app/src/repulse/app.cljs` — rewrite `render-context-panel!`

Replace the monolithic innerHTML approach with section-based rendering.
Each section is a helper function returning an HTML string (or empty string
if nothing to show).

```clojure
(defn- render-status-section []
  ;; BPM, playing indicator, audio backend, bank prefix
  ...)

(defn- render-tracks-section []
  ;; For each track in scheduler-state:
  ;;   - query one cycle, extract params
  ;;   - check muted/solo state
  ;;   - format inline param display
  ...)

(defn- render-arrangement-section []
  ;; If arrangement or play-scenes active, show progress
  ;; Otherwise return ""
  ...)

(defn- render-fx-section []
  ;; If any non-bypassed effects exist, show them
  ;; Otherwise return ""
  ...)

(defn- render-midi-section []
  ;; If midi/cc-mappings is available and non-empty, show mappings
  ;; Otherwise return ""
  ...)

(defn- render-sources-section []
  ;; If loaded-sources is non-empty, show them
  ;; Otherwise return ""
  ...)

(defn- render-bindings-section []
  ;; Same as current, user defs only
  ...)

(defn render-context-panel! []
  (let [html (str (render-status-section)
                  (render-tracks-section)
                  (render-arrangement-section)
                  (render-fx-section)
                  (render-midi-section)
                  (render-sources-section)
                  (render-bindings-section))]
    (set! (.-innerHTML ctx-el) html)))
```

### 2. `app/src/repulse/samples.cljs` — source tracking

Add a `loaded-sources` atom and append to it when external sources load:

```clojure
(defonce loaded-sources (atom []))

;; In the samples! handler, after successful load:
(swap! loaded-sources conj
  {:type :github :id repo-str :banks (count loaded-banks)})
```

### 3. `app/src/repulse/app.cljs` — additional watchers

Add watches for new atoms that should trigger re-render:

```clojure
;; Existing watches (keep):
;; env-atom, fx/chain, audio/scheduler-state, samples/active-bank-prefix

;; New watches:
(add-watch samples/loaded-sources :ctx-sources
  (fn [_ _ _ _] (render-context-panel!)))

;; Future (Phase N1) — only if atom exists:
;; (add-watch midi/cc-mappings :ctx-midi ...)
;; (add-watch midi/param-overrides :ctx-midi-vals ...)
```

### 4. `app/public/css/main.css` — panel styling updates

```css
/* Section headers */
.ctx-section-header {
  color: #5c6370;
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 1px;
  margin: 8px 0 4px 0;
  border-bottom: 1px solid #3e4451;
  padding-bottom: 2px;
}

/* Track lines */
.ctx-track {
  display: flex;
  align-items: baseline;
  gap: 6px;
  padding: 1px 0;
}
.ctx-track-icon { width: 12px; }
.ctx-track-name { color: #56b6c2; font-weight: bold; }
.ctx-track-params { color: #abb2bf; font-size: 11px; }
.ctx-track-muted { color: #5c6370; font-style: italic; }
.ctx-track-solo { color: #e5c07b; }

/* Source lines */
.ctx-source { color: #98c379; font-size: 11px; }

/* Param values */
.ctx-param-key { color: #5c6370; }
.ctx-param-val { color: #d19a66; }
```

### 5. Throttle rendering

With more watchers, `render-context-panel!` could fire rapidly (especially
during MIDI CC input). Throttle to ~60ms using `requestAnimationFrame`:

```clojure
(defonce render-scheduled? (atom false))

(defn schedule-render! []
  (when-not @render-scheduled?
    (reset! render-scheduled? true)
    (js/requestAnimationFrame
      (fn []
        (reset! render-scheduled? false)
        (render-context-panel!)))))
```

All watchers call `schedule-render!` instead of `render-context-panel!` directly.

---

## Files to change

| File | Change |
|------|--------|
| `app/src/repulse/app.cljs` | Rewrite `render-context-panel!` with section-based rendering; add watchers; add `extract-track-params` helper; throttle with rAF |
| `app/src/repulse/samples.cljs` | Add `loaded-sources` atom; append on `samples!` load |
| `app/public/css/main.css` | New CSS classes for tracks, sections, sources, params |

No changes to `packages/core/`, `packages/lisp/`, or `packages/audio/` (Rust/WASM).
No changes to the grammar, completions, or hover docs (no new Lisp built-ins).

---

## What this phase does NOT include

- **MIDI mapping logic** — that's Phase N1. E2 only adds the UI slot.
- **Editable params** — clicking a param value to change it (future work).
- **Spectrum/waveform visuals** — that's Phase B.
- **Per-event granularity** — showing that event 3 has different amp than event 1
  would be too noisy. Show the first/representative value per track.
- **Freesound loading logic** — that's Phase N. E2 only displays what's loaded.

---

## Definition of done

### Status section

- [ ] BPM shown (yellow, bold)
- [ ] Playing indicator: green `●` when playing, gray `○` when stopped
- [ ] Audio backend shown: `[wasm]` or `[js]` in gray
- [ ] Bank prefix shown when active (purple)

### Tracks section

- [ ] Each active track shown with name and state icon (`▶`, `■`, `★`)
- [ ] Per-track params shown inline: only params that are defined in the pattern
- [ ] `amp`, `pan`, `decay`, `attack`, `release` values displayed when present
- [ ] Synth type shown when not default (`:saw`, `:square`, `:fm`)
- [ ] Sample bank name shown when track plays samples
- [ ] Muted tracks show `(muted)` with dimmed styling
- [ ] Soloed tracks show `(solo)` with highlight styling
- [ ] Section hidden when no tracks are active

### Arrangement section

- [ ] Shows current scene name/index and cycle progress when `arrange` or `play-scenes` is active
- [ ] Hidden when no arrangement is running

### FX section

- [ ] Only displayed when at least one non-bypassed effect is active
- [ ] Hidden when no effects or all effects bypassed
- [ ] Each effect shows name and primary parameter value
- [ ] Bypassed effects shown with `off` indicator (same as current)

### MIDI section (UI slot)

- [ ] Section appears when `midi/cc-mappings` atom exists and is non-empty
- [ ] Hidden when no MIDI mappings (or Phase N1 not implemented)
- [ ] When N1 is present: shows `CC #N → :target  value` per mapping

### Sources section

- [ ] Shows loaded GitHub repos with identifier
- [ ] Shows Freesound queries with count (when Phase N adds Freesound)
- [ ] Hidden when no external sources loaded (built-in Strudel CDN not listed)
- [ ] Updates when new sources are loaded via `(samples! ...)`

### Bindings section

- [ ] User-defined names shown with inferred type (same as current)
- [ ] Built-in names filtered out
- [ ] Hidden when no user bindings

### Rendering

- [ ] Panel updates automatically when any relevant atom changes
- [ ] Rendering throttled via `requestAnimationFrame` (no rapid flicker)
- [ ] Panel fits within existing 190px sidebar width
- [ ] All text uses oneDark color scheme
- [ ] Sections use consistent header styling

### No regressions

- [ ] All existing core tests pass (`npm run test:core`)
- [ ] Basic playback works: `(seq :bd :sd :bd :sd)`
- [ ] Effects still apply: `(fx :reverb 0.3)`
- [ ] Track management works: `(play :drums ...)`, `(mute! :drums)`, `(solo! :drums)`
- [ ] Arrangement works: `(arrange [[p 4] [q 8]])`
- [ ] Editor keybindings (Alt+Enter, Ctrl+.) still work
- [ ] Command bar works
- [ ] Panel does not cause performance issues during playback
