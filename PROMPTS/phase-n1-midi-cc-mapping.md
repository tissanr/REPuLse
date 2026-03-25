# Phase N1 — MIDI CC → Parameter Mapping

## Goal

Let users bind any MIDI controller knob/fader to any numeric parameter in REPuLse.
This is the first slice of Phase N (MIDI & I/O), focused exclusively on **controller
input** — the most immediately useful MIDI feature for live performance.

```lisp
;; Map CC #1 (mod wheel) to master filter cutoff
(midi-map 1 :filter)

;; Map CC #7 to master amp
(midi-map 7 :amp)

;; Map CC #10 to BPM (scaled 60–240)
(midi-map 10 :bpm)

;; Map CC #74 to reverb wet mix
(midi-map 74 :reverb)

;; Map any CC to any per-event param on a specific track
(midi-map 2 :decay :track :bass)

;; Remove a mapping
(midi-unmap 1)

;; See all active mappings
(midi-maps)

;; Learn mode — move a knob, it maps to the given target
(midi-learn :filter)
```

---

## Background

### What exists today

- **`midi-sync!`** (Phase 4) already calls `navigator.requestMIDIAccess()`, enumerates
  inputs, and listens for clock messages. This lives in `app/src/repulse/audio.cljs`
  (lines ~636–679).
- **Per-event parameters** (Phase H) — `amp`, `attack`, `decay`, `release`, `pan`,
  `rate`, `begin`, `end*` — are fully implemented in `packages/core/src/repulse/params.cljs`.
- **Effects** (Phase 6b / A) — `reverb`, `delay`, `filter`, `compressor`, `dattorro-reverb`,
  `chorus`, `phaser`, `tremolo`, `overdrive`, `bitcrusher` — managed via `app/src/repulse/fx.cljs`.
- **BPM** — stored in `audio/scheduler-state` atom, settable via `(bpm N)`.

### Web MIDI API

`navigator.requestMIDIAccess()` — Chrome and Edge only. A CC message is 3 bytes:
`[0xBn, cc-number, value]` where `n` is the channel (0–15) and value is 0–127.

### Target parameter types

| Target | Where it lives | Range | How to set |
|--------|---------------|-------|------------|
| `:filter` | Master lowpass via `fx.cljs` | 20–20000 Hz | `fx/set-param!` |
| `:amp` | Master gain node | 0.0–1.0 | `audio/set-master-gain!` |
| `:bpm` | `audio/scheduler-state` | 60–240 | `audio/set-bpm!` |
| `:reverb` | Effect wet mix | 0.0–1.0 | `fx/set-param!` |
| `:delay` | Effect wet mix | 0.0–1.0 | `fx/set-param!` |
| `:dattorro-reverb` | Effect wet mix | 0.0–1.0 | `fx/set-param!` |
| `:chorus` | Effect wet mix | 0.0–1.0 | `fx/set-param!` |
| `:phaser` | Effect wet mix | 0.0–1.0 | `fx/set-param!` |
| `:tremolo` | Effect wet mix | 0.0–1.0 | `fx/set-param!` |
| `:overdrive` | Effect drive amount | 0.0–1.0 | `fx/set-param!` |
| `:bitcrusher` | Effect wet mix | 0.0–1.0 | `fx/set-param!` |
| `:attack` | Per-event param | 0.001–2.0 s | Atom override in scheduler |
| `:decay` | Per-event param | 0.01–5.0 s | Atom override in scheduler |
| `:release` | Per-event param | 0.01–5.0 s | Atom override in scheduler |
| `:pan` | Per-event param | -1.0–1.0 | Atom override in scheduler |

---

## Design

### New file: `app/src/repulse/midi.cljs`

A dedicated MIDI namespace. Phase N1 only implements the **input** side. Later N-phases
(note output, clock output, file export) add to this module.

```
State atoms:
  midi-access    — the MIDIAccess object (nil until first request)
  cc-mappings    — {cc-number {:target kw, :min f, :max f, :track kw-or-nil}}
  learn-target   — nil or {:target kw} when in learn mode
```

### CC message flow

```
MIDI Input Device
  │
  ▼
on-midi-message (listener on all inputs)
  │
  ├── CC message? (status & 0xF0 == 0xB0)
  │     │
  │     ▼
  │   lookup cc-number in @cc-mappings
  │     │
  │     ▼
  │   scale 0–127 → target range (min..max)
  │     │
  │     ▼
  │   dispatch to target:
  │     :filter  → fx/set-param! "filter" "value" (scaled Hz)
  │     :amp     → audio/set-master-gain! (0–1)
  │     :bpm     → audio/set-bpm! (60–240)
  │     :reverb  → fx/set-param! "reverb" "wet" (0–1)
  │     :decay   → swap! param-overrides assoc :decay (scaled)
  │     …etc
  │
  └── Learn mode active?
        │
        ▼
      auto-map this cc-number to @learn-target, exit learn mode
```

### Parameter override mechanism

For per-event parameters (`amp`, `attack`, `decay`, `release`, `pan`), MIDI CC values
need to override what the pattern specifies. Add a `param-overrides` atom:

```clojure
(defonce param-overrides (atom {}))
;; e.g. {:decay 0.3, :pan -0.2}
```

The audio scheduler reads `@param-overrides` when dispatching events. If a key is
present, it replaces the event's value. This gives real-time knob control without
re-evaluating the pattern.

Optionally scope overrides to a track: `{:bass {:decay 0.3}}`.

### Scaling

CC values are 0–127. Each target has a sensible default range:

```clojure
(def target-ranges
  {:filter  {:min 20    :max 20000 :curve :exponential}
   :amp     {:min 0.0   :max 1.0   :curve :linear}
   :bpm     {:min 60    :max 240   :curve :linear}
   :reverb  {:min 0.0   :max 1.0   :curve :linear}
   :delay   {:min 0.0   :max 1.0   :curve :linear}
   :attack  {:min 0.001 :max 2.0   :curve :exponential}
   :decay   {:min 0.01  :max 5.0   :curve :exponential}
   :release {:min 0.01  :max 5.0   :curve :exponential}
   :pan     {:min -1.0  :max 1.0   :curve :linear}})
```

Exponential scaling for frequency/time params (`:filter`, `:attack`, `:decay`):
```
scaled = min * (max/min) ^ (cc/127)
```

Linear scaling for everything else:
```
scaled = min + (cc/127) * (max - min)
```

### Context panel integration

The session context panel (Phase E) should show active MIDI mappings. Add a
"MIDI Mappings" section that lists `CC #N → :target (current value)` and updates
in real time as knobs move.

---

## Implementation

### 1. New file: `app/src/repulse/midi.cljs`

```clojure
(ns repulse.midi
  (:require [repulse.fx :as fx]
            [repulse.audio :as audio]))

;;; ── State ─────────────────────────────────────────────────────────

(defonce midi-access (atom nil))
(defonce cc-mappings (atom {}))
(defonce learn-target (atom nil))
(defonce param-overrides (atom {}))

(def target-ranges
  {:filter          {:min 20    :max 20000 :curve :exponential}
   :amp             {:min 0.0   :max 1.0   :curve :linear}
   :bpm             {:min 60    :max 240   :curve :linear}
   :reverb          {:min 0.0   :max 1.0   :curve :linear}
   :delay           {:min 0.0   :max 1.0   :curve :linear}
   :dattorro-reverb {:min 0.0   :max 1.0   :curve :linear}
   :chorus          {:min 0.0   :max 1.0   :curve :linear}
   :phaser          {:min 0.0   :max 1.0   :curve :linear}
   :tremolo         {:min 0.0   :max 1.0   :curve :linear}
   :overdrive       {:min 0.0   :max 1.0   :curve :linear}
   :bitcrusher      {:min 0.0   :max 1.0   :curve :linear}
   :attack          {:min 0.001 :max 2.0   :curve :exponential}
   :decay           {:min 0.01  :max 5.0   :curve :exponential}
   :release         {:min 0.01  :max 5.0   :curve :exponential}
   :pan             {:min -1.0  :max 1.0   :curve :linear}})

;;; ── Scaling ───────────────────────────────────────────────────────

(defn- scale-cc
  "Scale a 0–127 CC value to the target's range."
  [cc-value {:keys [min max curve]}]
  (let [normalized (/ cc-value 127.0)]
    (case curve
      :exponential (* min (Math/pow (/ max min) normalized))
      ;; :linear (default)
      (+ min (* normalized (- max min))))))

;;; ── Dispatch ──────────────────────────────────────────────────────

(def ^:private global-targets
  "Targets that affect the master bus / global state."
  #{:filter :amp :bpm :reverb :delay :dattorro-reverb
    :chorus :phaser :tremolo :overdrive :bitcrusher})

(def ^:private param-targets
  "Targets that override per-event parameters."
  #{:attack :decay :release :pan})

(defn- dispatch-cc!
  "Apply a scaled CC value to its target."
  [target scaled-value track]
  (cond
    ;; Global: master gain
    (= target :amp)
    (audio/set-master-gain! scaled-value)

    ;; Global: tempo
    (= target :bpm)
    (audio/set-bpm! (Math/round scaled-value))

    ;; Global: filter cutoff
    (= target :filter)
    (fx/set-param! "filter" "value" scaled-value)

    ;; Global: effect wet mix
    (contains? #{:reverb :delay :dattorro-reverb :chorus
                 :phaser :tremolo :overdrive :bitcrusher} target)
    (fx/set-param! (name target) "wet" scaled-value)

    ;; Per-event parameter override
    (contains? param-targets target)
    (if track
      (swap! param-overrides assoc-in [track target] scaled-value)
      (swap! param-overrides assoc target scaled-value))))

;;; ── MIDI message handler ──────────────────────────────────────────

(defn- on-midi-message [event]
  (let [data   (.-data event)
        status (aget data 0)
        cmd    (bit-and status 0xF0)]
    ;; CC message: 0xB0
    (when (= cmd 0xB0)
      (let [cc-num (aget data 1)
            value  (aget data 2)]
        ;; Learn mode: first CC received maps to learn-target
        (if-let [lt @learn-target]
          (do
            (swap! cc-mappings assoc cc-num
                   (merge (get target-ranges (:target lt))
                          {:target (:target lt) :track (:track lt)}))
            (reset! learn-target nil)
            (let [{:keys [target track]} lt
                  range-info (get target-ranges target)
                  scaled (scale-cc value range-info)]
              (dispatch-cc! target scaled track)))
          ;; Normal mode: look up mapping
          (when-let [mapping (get @cc-mappings cc-num)]
            (let [{:keys [target track]} mapping
                  range-info (get target-ranges target)
                  scaled (scale-cc value range-info)]
              (dispatch-cc! target scaled track))))))))

;;; ── Access ────────────────────────────────────────────────────────

(defn- attach-listeners!
  "Attach CC listener to all MIDI inputs."
  [access]
  (.forEach (.-inputs access)
            (fn [input]
              (set! (.-onmidimessage input) on-midi-message))))

(defn ensure-access!
  "Request MIDI access if not already granted. Returns a Promise."
  []
  (if @midi-access
    (js/Promise.resolve @midi-access)
    (if-not (exists? js/navigator.requestMIDIAccess)
      (js/Promise.reject
        (js/Error. "MIDI not supported in this browser — use Chrome or Edge"))
      (-> (js/navigator.requestMIDIAccess #js {:sysex false})
          (.then (fn [access]
                   (reset! midi-access access)
                   (attach-listeners! access)
                   ;; Re-attach when devices are plugged in
                   (set! (.-onstatechange access)
                         (fn [_] (attach-listeners! access)))
                   access))))))

;;; ── Public API ────────────────────────────────────────────────────

(defn map-cc!
  "Map a MIDI CC number to a parameter target.
   Optional :track keyword scopes the mapping to one track."
  [cc-num target & {:keys [track]}]
  (let [range-info (or (get target-ranges target)
                       {:min 0.0 :max 1.0 :curve :linear})]
    (swap! cc-mappings assoc cc-num
           (merge range-info {:target target :track track}))
    (-> (ensure-access!)
        (.catch (fn [e] (str (.-message e)))))))

(defn unmap-cc!
  "Remove a CC mapping."
  [cc-num]
  (swap! cc-mappings dissoc cc-num))

(defn start-learn!
  "Enter learn mode: the next CC message received will be mapped to target."
  [target & {:keys [track]}]
  (reset! learn-target {:target target :track track})
  (ensure-access!))

(defn get-mappings
  "Return the current CC mapping table as a seq of maps."
  []
  (mapv (fn [[cc {:keys [target track]}]]
          (cond-> {:cc cc :target target}
            track (assoc :track track)))
        @cc-mappings))

(defn get-param-override
  "Get the current MIDI override value for a param, optionally scoped to a track."
  [param-kw track-kw]
  (or (get-in @param-overrides [track-kw param-kw])
      (get @param-overrides param-kw)))
```

---

### 2. `app/src/repulse/audio.cljs` — read param overrides

In the event dispatch section of `schedule-cycle!`, merge MIDI overrides before
playing the event. Wherever the event's param map is assembled, add:

```clojure
(let [overrides @midi/param-overrides
      track-overrides (get overrides track-name)
      global-overrides (select-keys overrides [:attack :decay :release :pan :amp])
      merged (merge params global-overrides track-overrides)]
  ;; use `merged` instead of `params` for dispatch
  )
```

This is a minimal change — a single `merge` before the existing dispatch code.

---

### 3. `app/src/repulse/app.cljs` — Lisp bindings

Add to `ensure-env!`:

```clojure
;; --- MIDI CC mapping (Phase N1) ---
"midi-map"
(fn [& args]
  (let [args' (mapv leval/unwrap args)
        cc-num (int (first args'))
        target (second args')
        opts   (apply hash-map (drop 2 args'))]
    (midi/map-cc! cc-num target :track (:track opts))
    (str "mapped CC #" cc-num " → " (name target))))

"midi-unmap"
(fn [cc]
  (let [n (int (leval/unwrap cc))]
    (midi/unmap-cc! n)
    (str "unmapped CC #" n)))

"midi-maps"
(fn []
  (let [maps (midi/get-mappings)]
    (if (empty? maps)
      "no MIDI mappings"
      (clojure.string/join "\n"
        (map (fn [{:keys [cc target track]}]
               (str "CC #" cc " → :" (name target)
                    (when track (str " (track :" (name track) ")"))))
             maps)))))

"midi-learn"
(fn [& args]
  (let [args' (mapv leval/unwrap args)
        target (first args')
        opts   (apply hash-map (rest args'))]
    (midi/start-learn! target :track (:track opts))
    (str "move a knob to map it to :" (name target) "…")))
```

---

### 4. Grammar and completions

Add to `BuiltinName` in `repulse-lisp.grammar`:

```
"midi-map" | "midi-unmap" | "midi-maps" | "midi-learn" |
```

Add to `completions.js`:

```javascript
{ label: "midi-map",   type: "function", detail: "(midi-map cc# :target) — bind MIDI CC to a parameter" },
{ label: "midi-unmap", type: "function", detail: "(midi-unmap cc#) — remove a MIDI CC mapping" },
{ label: "midi-maps",  type: "function", detail: "(midi-maps) — list all active MIDI CC mappings" },
{ label: "midi-learn", type: "function", detail: "(midi-learn :target) — move a knob to map it" },
```

Add to `hover.js`:

```javascript
"midi-map":   { sig: "(midi-map cc# :target)", doc: "Map a MIDI CC number to a parameter. Targets: :filter, :amp, :bpm, :reverb, :delay, :attack, :decay, :release, :pan, and all effect names. Optional :track keyword scopes to one track." },
"midi-unmap": { sig: "(midi-unmap cc#)",        doc: "Remove a MIDI CC mapping." },
"midi-maps":  { sig: "(midi-maps)",             doc: "List all active MIDI CC mappings with their current values." },
"midi-learn": { sig: "(midi-learn :target)",    doc: "Enter learn mode: move any knob/fader on your MIDI controller and it will be mapped to the given target." },
```

Run `npm run gen:grammar` after editing the grammar.

---

### 5. Context panel integration

In the context panel (`app.cljs`), add a "MIDI" section below the effects list
that shows active mappings. Watch `midi/cc-mappings` atom and re-render on change.

```
──── MIDI ────
CC #1  → :filter   [====----] 1247 Hz
CC #7  → :amp      [=======-]  0.92
CC #74 → :reverb   [==------]  0.28
```

---

## Files to change

| File | Change |
|------|--------|
| `app/src/repulse/midi.cljs` | **New** — MIDI access, CC mapping, learn mode, param overrides |
| `app/src/repulse/app.cljs` | `midi-map`, `midi-unmap`, `midi-maps`, `midi-learn` bindings; context panel MIDI section |
| `app/src/repulse/audio.cljs` | Merge `param-overrides` in event dispatch; require `repulse.midi` |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add 4 tokens to `BuiltinName` |
| `app/src/repulse/lisp-lang/completions.js` | Add 4 completion entries |
| `app/src/repulse/lisp-lang/hover.js` | Add 4 hover docs |
| `README.md` | Add MIDI mapping rows to language reference table |

No changes to `packages/core/`, `packages/audio/` (Rust/WASM), or `packages/lisp/`.

---

## Platform requirements

| Feature | Browser support |
|---------|----------------|
| MIDI CC mapping | Chrome, Edge (Web MIDI API) |
| MIDI learn mode | Chrome, Edge |
| Parameter overrides | All (the override atom works regardless of input source) |

For Firefox/Safari, all `midi-*` functions return:
`"MIDI not supported in this browser — use Chrome or Edge"`

---

## Definition of done

### CC → parameter mapping

- [ ] `(midi-map 1 :filter)` maps CC #1 to master filter cutoff
- [ ] Turning the knob changes filter cutoff in real time (20 Hz – 20 kHz, exponential)
- [ ] `(midi-map 7 :amp)` maps CC #7 to master gain (0–1, linear)
- [ ] `(midi-map 10 :bpm)` maps CC #10 to tempo (60–240, linear)
- [ ] `(midi-map 74 :reverb)` maps CC #74 to reverb wet mix (0–1)
- [ ] All other effects work as targets: `:delay`, `:chorus`, `:phaser`, etc.
- [ ] Per-event params work: `(midi-map 2 :decay)` overrides decay on all events
- [ ] Track-scoped mapping: `(midi-map 2 :decay :track :bass)` only affects `:bass`
- [ ] Multiple CC mappings work simultaneously
- [ ] `(midi-unmap 1)` removes the mapping
- [ ] `(midi-maps)` lists all active mappings

### Learn mode

- [ ] `(midi-learn :filter)` enters learn mode
- [ ] Moving any CC knob creates the mapping and exits learn mode
- [ ] Only one learn target active at a time

### Context panel

- [ ] Active MIDI mappings appear in the session context panel
- [ ] Values update in real time as knobs move

### Error handling

- [ ] Firefox/Safari: clear error message about browser support
- [ ] Unknown target keyword: error with list of valid targets
- [ ] No MIDI devices connected: clear message

### No regressions

- [ ] Existing `(midi-sync! true)` clock input still works
- [ ] All existing core tests pass (`npm run test:core`)
- [ ] Patterns without MIDI mapping are unaffected
- [ ] Hot-plugging a MIDI controller after page load works (onstatechange)
