# Phase J — Onboarding & Discoverability

## Goal

Lower the barrier to entry for new users by making REPuLse self-teaching. Three
features, all accessible from inside the instrument itself:

1. **Starter templates** — `(demo :techno)` loads a curated, ready-to-play multi-track
   pattern into the editor. Users hear music immediately and learn idioms by reading it.
2. **Hover documentation** — hovering over any built-in name in the editor shows its
   signature, parameter names, and a one-line example in a tooltip.
3. **Interactive tutorial** — `(tutorial)` loads chapter 1 of a progressive walkthrough.
   Each chapter is a playable REPuLse program with comments explaining the concepts.

```lisp
;; Before — a new user stares at a blank editor, unsure what to type.
;; The only way to learn is reading external docs.

;; After — type in the command bar:
(demo :techno)
;; → editor fills with a four-on-the-floor pattern, BPM is set, it plays immediately

;; After — hover over "fast" in the editor:
;; ┌────────────────────────────────────────────────────┐
;; │ fast                                               │
;; │ (fast factor pat) — speed up a pattern by factor   │
;; │ Example: (fast 2 (seq :bd :sd))                    │
;; └────────────────────────────────────────────────────┘

;; After — type in the command bar:
(tutorial)
;; → editor fills with chapter 1: "First sound"
;; → user reads comments, presses Ctrl+Enter, hears the pattern,
;;   then types (tutorial 2) for the next lesson
```

---

## Background

### Current state

- The editor starts with `(seq :bd :sd :bd :sd)` — functional but not inspiring.
- Built-ins have short `detail` strings in the autocomplete popup, but there is no way to
  see documentation without triggering completion.
- Users must read external docs (`docs/USAGE.md`, `README.md`) to discover `play`, `fx`,
  `scale`, `amp`, `->>`, and other features. There is no in-app learning path.

### Existing infrastructure

- `ensure-env!` in `app/src/repulse/app.cljs` registers built-ins that need DOM/audio access
  (`play`, `mute!`, `fx`, `bpm`, etc.). `demo` and `tutorial` go here since they need the
  `@editor-view` atom to set editor content.
- `evaluate!` in `app.cljs` evaluates code and routes patterns to audio. After setting the
  editor content, calling `evaluate!` on it starts playback.
- The CM6 language extension is in `app/src/repulse/lisp-lang/index.js`. It already bundles
  completions and rainbow brackets — adding a hover tooltip extension goes here.
- Completions are in `app/src/repulse/lisp-lang/completions.js` — an array of
  `{label, type, detail}` objects.
- Grammar is in `app/src/repulse/lisp-lang/repulse-lisp.grammar`. New built-in names must be
  added to `BuiltinName`. Run `npm run gen:grammar` after editing.

---

## Design

### 1. `demo` and `tutorial` are Lisp built-ins, not UI chrome

They live in the same namespace as `play`, `bpm`, `stop` — callable from both the editor
and the command bar. This is consistent with REPuLse's philosophy: everything is an
expression.

### 2. Templates set the editor, set BPM, and auto-evaluate

When the user calls `(demo :techno)`:
1. The BPM is set (via `audio/set-bpm!`).
2. The editor content is replaced with the template code.
3. `evaluate!` is called on the new code — sound starts immediately.

This means one command in the command bar does everything.

### 3. Tutorial chapters do NOT auto-evaluate

`(tutorial N)` replaces the editor content but does **not** call `evaluate!`. The user
reads the comments first, then presses Ctrl+Enter themselves. This teaches the
evaluate-and-listen workflow from chapter 1.

### 4. Hover tooltip reuses completion data, extended with richer docs

The hover tooltip provider looks up the word under the cursor in a docs map. This map is a
superset of the completions array — same labels, but with full signatures, parameter names,
and examples. Defined once in a new `hover.js` file.

### 5. `(demo)` with no args lists available templates

Returns a formatted string: `"available demos: :techno :ambient :dnb :minimal :house :dub :experimental"`.
Same pattern as `(tracks)` and `(sample-banks)`.

---

## Implementation

### 1. `app/src/repulse/app.cljs` — demo templates

Add a `demo-templates` map and a `tutorial-chapters` vector at the top of the file (after
the namespace declaration and before DOM helpers). Then register `demo` and `tutorial` as
built-ins in `ensure-env!`.

#### Demo template data

```clojure
(def demo-templates
  {:techno
   {:bpm 130
    :code
";; TECHNO — four-on-the-floor kick, offbeat hats, snare on 2/4, acid bassline
(bpm 130)

(play :kick
  (seq :bd :bd :bd :bd))

(play :hat
  (->> (fast 2 (seq :_ :oh :_ :oh))
       (amp (seq 0.5 0.7 0.5 0.9))))

(play :snare
  (seq :_ :sd :_ :sd))

(play :bass
  (->> (scale :minor :c2 (seq 0 0 3 5))
       (fast 2)
       (decay 0.15)
       (amp 0.8)))
"}

   :ambient
   {:bpm 72
    :code
";; AMBIENT — slow pad chords with reverb, gentle melodic line
(bpm 72)

(play :pad
  (->> (chord :minor7 :a3)
       (amp 0.3)
       (attack 0.4)
       (decay 3.0)))

(play :melody
  (->> (scale :minor :a4 (seq 0 2 4 7 4 2))
       (slow 2)
       (amp 0.4)
       (attack 0.1)
       (decay 1.5)))

(play :pulse
  (->> (seq :c5 :_ :e5 :_)
       (slow 4)
       (amp 0.15)
       (decay 0.8)))
"}

   :dnb
   {:bpm 174
    :code
";; DRUM & BASS — fast breakbeat, sub bass, amen-style rhythm
(bpm 174)

(play :break
  (seq :bd :_ :_ :bd :_ :_ :sd :_
       :bd :_ :bd :_ :_ :sd :_ :_))

(play :hat
  (->> (fast 2 (seq :hh :hh :oh :hh))
       (amp (seq 0.6 0.4 0.8 0.4))))

(play :sub
  (->> (scale :minor :e1 (seq 0 :_ 0 :_ 3 :_ 5 :_))
       (amp 0.9)
       (decay 0.2)))
"}

   :minimal
   {:bpm 120
    :code
";; MINIMAL — sparse kick, subtle hi-hats, one-note bass
(bpm 120)

(play :kick
  (seq :bd :_ :_ :_ :bd :_ :_ :_))

(play :hat
  (->> (seq :_ :hh :_ :hh :_ :hh :_ :_)
       (amp 0.35)))

(play :bass
  (->> (pure :c2)
       (amp 0.6)
       (decay 0.12)))
"}

   :house
   {:bpm 124
    :code
";; HOUSE — classic four-on-the-floor, organ stab chords, open hat
(bpm 124)

(play :kick
  (seq :bd :bd :bd :bd))

(play :hat
  (->> (seq :_ :oh :_ :oh)
       (amp 0.5)))

(play :clap
  (seq :_ :sd :_ :sd))

(play :chord
  (->> (every 4 (fast 2) (chord :dom7 :c4))
       (amp 0.4)
       (attack 0.02)
       (decay 0.25)))

(play :bass
  (->> (scale :minor :c2 (seq 0 0 3 0 5 0 3 0))
       (amp 0.7)
       (decay 0.1)))
"}

   :dub
   {:bpm 140
    :code
";; DUB — heavy bass, delay-heavy snare, sparse hats
(bpm 140)

(play :kick
  (seq :bd :_ :_ :_ :_ :_ :bd :_))

(play :snare
  (->> (seq :_ :_ :_ :sd :_ :_ :_ :_)
       (amp 0.8)
       (decay 0.3)))

(play :hat
  (->> (seq :_ :hh :_ :_ :_ :_ :hh :_)
       (amp 0.3)))

(play :bass
  (->> (scale :minor :g1 (seq 0 :_ :_ 0 :_ 3 :_ :_))
       (amp 0.9)
       (attack 0.01)
       (decay 0.4)))
"}

   :experimental
   {:bpm 110
    :code
";; EXPERIMENTAL — algorithmic patterns using every, rev, fmap
(bpm 110)

(play :rhythm
  (every 3 rev
    (seq :bd :_ :sd :_ :bd :bd :_ :sd)))

(play :texture
  (->> (every 2 (fast 2) (seq :hh :oh :hh :_))
       (amp (seq 0.3 0.6 0.4 0.8))))

(play :melody
  (->> (scale :dorian :d3 (seq 0 2 4 6 7 4 2 0))
       (every 4 rev)
       (every 3 (fast 2))
       (amp 0.5)
       (decay 0.6)))

(play :drone
  (->> (chord :sus4 :d2)
       (amp 0.2)
       (attack 0.5)
       (decay 2.5)))
"}})
```

#### Tutorial chapter data

```clojure
(def tutorial-chapters
  [
   ;; Chapter 1: First sound
   ";; === Tutorial 1/8 — First Sound ===
;;
;; Welcome to REPuLse! Let's make some noise.
;;
;; `seq` creates a sequence of sounds.  Each value plays
;; for one equal subdivision of the cycle:
;;   :bd = bass drum   :sd = snare   :hh = hi-hat
;;
;; Press Ctrl+Enter (Cmd+Enter on Mac) to hear this:

(seq :bd :sd :bd :sd)

;; Try changing :sd to :hh and press Ctrl+Enter again.
;; When you're ready, type (tutorial 2) in the command bar."

   ;; Chapter 2: Layering
   ";; === Tutorial 2/8 — Layering with stack ===
;;
;; `stack` plays multiple patterns at the same time.
;; Each pattern runs in parallel, like tracks in a mixer.

(stack
  (seq :bd :_ :bd :_)
  (seq :_ :sd :_ :sd)
  (seq :hh :hh :hh :hh))

;; :_ is a rest — silence for that step.
;; Try adding a fourth layer!
;; Next: (tutorial 3)"

   ;; Chapter 3: Speed
   ";; === Tutorial 3/8 — Speed: fast & slow ===
;;
;; `fast` speeds up a pattern by a factor.
;; `slow` does the opposite.

(stack
  (seq :bd :_ :bd :_)
  (fast 2 (seq :hh :oh))
  (slow 2 (seq :sd :_ :_ :_)))

;; (fast 2 pat) plays pat twice per cycle.
;; (slow 2 pat) stretches pat over two cycles.
;; Try (fast 4 (seq :hh :oh)) for rapid hi-hats.
;; Next: (tutorial 4)"

   ;; Chapter 4: Evolution
   ";; === Tutorial 4/8 — Evolution: every ===
;;
;; `every` applies a transformation only on certain cycles.
;; (every N transform pattern) — transform every Nth cycle.

(stack
  (every 4 (fast 2) (seq :bd :_ :bd :_))
  (seq :_ :sd :_ :sd)
  (every 3 rev (seq :hh :oh :hh :_)))

;; The kick doubles speed every 4th cycle.
;; The hats reverse every 3rd cycle.
;; This is how patterns stay alive without manual changes.
;; Next: (tutorial 5)"

   ;; Chapter 5: Naming
   ";; === Tutorial 5/8 — Naming: def ===
;;
;; `def` binds a name to a value. Use it to build
;; a vocabulary of reusable parts.

(def kick (seq :bd :_ :bd :_))
(def snare (seq :_ :sd :_ :sd))
(def hat (fast 2 (seq :hh :oh)))

(stack kick snare hat)

;; Now you can refer to `kick`, `snare`, `hat` by name.
;; Try: (def kick (seq :bd :bd :_ :bd)) and re-evaluate.
;; Next: (tutorial 6)"

   ;; Chapter 6: Multi-track
   ";; === Tutorial 6/8 — Multi-Track: play ===
;;
;; `play` starts a named track.  Each track runs
;; independently — you can update one without stopping others.

(play :kick
  (seq :bd :_ :bd :bd))

(play :snare
  (seq :_ :sd :_ :sd))

(play :hat
  (fast 2 (seq :hh :oh)))

;; In the command bar, try:
;;   (mute! :hat)     — silence the hats
;;   (unmute! :hat)   — bring them back
;;   (solo! :kick)    — hear only the kick
;;   (clear!)         — stop everything
;; Next: (tutorial 7)"

   ;; Chapter 7: Melody
   ";; === Tutorial 7/8 — Melody: scale & chord ===
;;
;; Note keywords like :c4 play pitched tones.
;; `scale` maps degree numbers (0, 1, 2, …) to a musical scale.
;; `chord` stacks the tones of a chord.

(play :bass
  (scale :minor :c3 (seq 0 0 3 5)))

(play :chords
  (slow 2 (chord :minor :c4)))

(play :melody
  (scale :minor :c4 (seq 0 2 4 7 4 2)))

(play :kick
  (seq :bd :bd :bd :bd))

;; Try changing :minor to :dorian or :blues.
;; Try (transpose 5 ...) around the melody.
;; Next: (tutorial 8)"

   ;; Chapter 8: Expression
   ";; === Tutorial 8/8 — Expression: amp, decay, ->> ===
;;
;; Per-event parameters make patterns expressive.
;; `->>` threads a pattern through a chain of transformers.

(play :kick
  (->> (seq :bd :bd :bd :bd)
       (amp (seq 0.9 0.5 0.7 0.5))))

(play :lead
  (->> (scale :minor :c4 (seq 0 2 4 7 4 2 0 :_))
       (amp 0.6)
       (attack 0.02)
       (decay 0.5)))

(play :pad
  (->> (chord :minor7 :c3)
       (amp 0.25)
       (attack 0.3)
       (decay 2.0)))

;; (amp val) sets amplitude 0.0–1.0
;; (attack secs) sets onset time
;; (decay secs) sets fade time
;; (pan pos) sets stereo position -1.0 to 1.0
;;
;; That's the basics! Try (demo :techno) or (demo :experimental)
;; to hear full compositions, or start writing your own."
])
```

#### `ensure-env!` additions

Add the following entries inside `ensure-env!`, after the `"fx"` registration block:

```clojure
                   ;; --- Demo templates ---
                   "demo"
                   (fn [& args]
                     (let [kw (when (seq args) (leval/unwrap (first args)))]
                       (if (nil? kw)
                         ;; No args — list available demos
                         (str "available demos: "
                              (cstr/join " " (map #(str ":" (name %))
                                                  (sort (keys demo-templates)))))
                         ;; Load demo
                         (if-let [{:keys [bpm code]} (get demo-templates kw)]
                           (do
                             ;; Set BPM
                             (audio/set-bpm! bpm)
                             ;; Set editor content
                             (when-let [view @editor-view]
                               (.dispatch view
                                          #js {:changes #js {:from 0
                                                             :to   (.. view -state -doc -length)
                                                             :insert code}})
                               ;; Auto-evaluate
                               (js/setTimeout #(evaluate! code) 50))
                             (str "=> loaded demo :" (name kw)))
                           (str "unknown demo :" (name kw)
                                " — available: "
                                (cstr/join " " (map #(str ":" (name %))
                                                    (sort (keys demo-templates)))))))))
                   ;; --- Tutorial ---
                   "tutorial"
                   (fn [& args]
                     (let [n (if (seq args)
                               (int (leval/unwrap (first args)))
                               1)
                           idx (dec n)]
                       (if (and (>= idx 0) (< idx (count tutorial-chapters)))
                         (let [code (nth tutorial-chapters idx)]
                           ;; Set editor content but do NOT auto-evaluate
                           (when-let [view @editor-view]
                             (.dispatch view
                                        #js {:changes #js {:from 0
                                                           :to   (.. view -state -doc -length)
                                                           :insert code}}))
                           (str "=> tutorial chapter " n "/" (count tutorial-chapters)
                                " — press Ctrl+Enter to play"))
                         (str "tutorial has chapters 1–" (count tutorial-chapters)
                              " — try (tutorial 1)"))))
```

---

### 2. New file: `app/src/repulse/lisp-lang/hover.js` — hover documentation

Create this file. It exports a `hoverTooltip` provider that shows rich documentation
when the user hovers over built-in names.

```javascript
// hover.js — Hover documentation tooltips for REPuLse-Lisp built-ins
import { hoverTooltip } from "@codemirror/view";
import { syntaxTree } from "@codemirror/language";

/**
 * Documentation map: name → { signature, description, example }.
 * A superset of completions.js — same labels but richer content.
 */
const DOCS = {
  // --- Pattern constructors ---
  "seq": {
    signature: "(seq val ...)",
    description: "Create a sequence of values, each occupying one equal step per cycle.",
    example: '(seq :bd :sd :bd :sd)',
  },
  "stack": {
    signature: "(stack pat ...)",
    description: "Play multiple patterns simultaneously in parallel.",
    example: '(stack (seq :bd :_ :bd :_) (seq :_ :sd :_ :sd))',
  },
  "pure": {
    signature: "(pure val)",
    description: "A constant pattern that plays a single value for the entire cycle.",
    example: '(pure :bd)',
  },

  // --- Transformations ---
  "fast": {
    signature: "(fast factor pat)",
    description: "Speed up a pattern by the given factor. 2 = twice as fast.",
    example: '(fast 2 (seq :bd :sd))',
  },
  "slow": {
    signature: "(slow factor pat)",
    description: "Slow down a pattern by the given factor. 2 = half speed.",
    example: '(slow 2 (seq :bd :sd :hh :oh))',
  },
  "rev": {
    signature: "(rev pat)",
    description: "Reverse the order of events within each cycle.",
    example: '(rev (seq :bd :sd :hh :oh))',
  },
  "every": {
    signature: "(every n transform pat)",
    description: "Apply a transformation every nth cycle. Other cycles play unmodified.",
    example: '(every 4 (fast 2) (seq :bd :sd))',
  },
  "fmap": {
    signature: "(fmap f pat)",
    description: "Map a function over every value in the pattern.",
    example: '(fmap (fn [x] (* x 2)) (seq 1 2 3))',
  },

  // --- Music theory ---
  "scale": {
    signature: "(scale scale-kw root pat)",
    description: "Map degree integers (0, 1, 2, ...) to Hz frequencies using a named scale. Degrees outside the scale wrap into higher/lower octaves.",
    example: '(scale :minor :c4 (seq 0 2 4 7))',
  },
  "chord": {
    signature: "(chord chord-kw root)",
    description: "Stack the tones of a chord as simultaneous Hz values.",
    example: '(chord :minor7 :a3)',
  },
  "transpose": {
    signature: "(transpose semitones pat)",
    description: "Shift all Hz values in a pattern up or down by n semitones. Keywords pass through unchanged.",
    example: '(transpose 7 (scale :major :c4 (seq 0 1 2 3)))',
  },

  // --- Per-event parameters ---
  "->>": {
    signature: "(->> pat (f args) ...)",
    description: "Thread-last: pass the pattern as the last argument of each successive form. The natural way to chain parameter transforms.",
    example: '(->> (seq :c4 :e4) (amp 0.7) (decay 0.5))',
  },
  "amp": {
    signature: "(amp val pat) or (amp val)",
    description: "Set event amplitude (0.0 = silent, 1.0 = full). One-arg form returns a transformer for use with ->> or comp.",
    example: '(amp 0.8 (seq :c4 :e4 :g4))',
  },
  "attack": {
    signature: "(attack secs pat) or (attack secs)",
    description: "Set envelope attack time in seconds. 0.001 = percussive, 0.3 = slow swell.",
    example: '(attack 0.1 (pure :c4))',
  },
  "decay": {
    signature: "(decay secs pat) or (decay secs)",
    description: "Set envelope decay time in seconds. 0.08 = short stab, 2.0 = long tone.",
    example: '(decay 0.5 (seq :c4 :e4))',
  },
  "release": {
    signature: "(release secs pat) or (release secs)",
    description: "Set envelope release time in seconds. Defaults to decay value when omitted.",
    example: '(release 0.3 (seq :c4 :e4))',
  },
  "pan": {
    signature: "(pan pos pat) or (pan pos)",
    description: "Set stereo panning. -1.0 = hard left, 0.0 = centre, 1.0 = hard right.",
    example: '(pan -0.5 (seq :c4 :e4))',
  },
  "comp": {
    signature: "(comp f g ...)",
    description: "Compose transformers right-to-left. Useful for building named presets.",
    example: '(def pluck (comp (amp 0.8) (attack 0.003) (decay 0.15)))',
  },

  // --- Sound & playback ---
  "bpm": {
    signature: "(bpm n)",
    description: "Set the tempo in beats per minute.",
    example: '(bpm 130)',
  },
  "stop": {
    signature: "(stop)",
    description: "Stop all playback immediately.",
    example: '(stop)',
  },
  "play": {
    signature: "(play :name pattern)",
    description: "Start or replace a named track. Each track runs independently.",
    example: '(play :kick (seq :bd :_ :bd :_))',
  },
  "mute!": {
    signature: "(mute! :name)",
    description: "Silence a track without removing it. Use in the command bar.",
    example: '(mute! :kick)',
  },
  "unmute!": {
    signature: "(unmute! :name)",
    description: "Re-enable a muted track.",
    example: '(unmute! :kick)',
  },
  "solo!": {
    signature: "(solo! :name)",
    description: "Play only this track, muting all others.",
    example: '(solo! :bass)',
  },
  "clear!": {
    signature: "(clear! :name) or (clear!)",
    description: "Remove a track by name, or remove all tracks.",
    example: '(clear! :kick)',
  },
  "tracks": {
    signature: "(tracks)",
    description: "List all currently active track names.",
    example: '(tracks)',
  },
  "upd": {
    signature: "(upd)",
    description: "Hot-swap: re-evaluate the editor buffer and update running tracks without stopping playback.",
    example: '(upd)',
  },
  "tap!": {
    signature: "(tap!)",
    description: "Register a BPM tap. Four consecutive taps set the tempo.",
    example: '(tap!)',
  },
  "midi-sync!": {
    signature: "(midi-sync! true/false)",
    description: "Enable or disable MIDI clock synchronisation.",
    example: '(midi-sync! true)',
  },

  // --- Samples ---
  "samples!": {
    signature: '(samples! "github:owner/repo")',
    description: "Load an external sample bank from a GitHub repository.",
    example: '(samples! "github:tidalcycles/Dirt-Samples")',
  },
  "sample-banks": {
    signature: "(sample-banks)",
    description: "List all registered sample bank names.",
    example: '(sample-banks)',
  },
  "bank": {
    signature: "(bank :prefix)",
    description: "Set a default bank prefix for all subsequent keyword lookups.",
    example: '(bank :AkaiLinn)',
  },
  "sound": {
    signature: "(sound bank n)",
    description: "Select sample number n from the named bank.",
    example: '(sound :808 0)',
  },

  // --- Effects ---
  "fx": {
    signature: "(fx :name :param val ...)",
    description: "Set effect parameters. Use (fx :off :name) to bypass, (fx :on :name) to re-enable.",
    example: '(fx :reverb :wet 0.4)',
  },
  "load-plugin": {
    signature: '(load-plugin "url")',
    description: "Load a REPuLse plugin from a URL (visual or effect).",
    example: '(load-plugin "/plugins/reverb.js")',
  },

  // --- Arrangement ---
  "arrange": {
    signature: "(arrange [[pat cycles] ...])",
    description: "Sequence patterns by duration. Each [pattern cycles] pair plays for the given number of cycles.",
    example: '(arrange [[(seq :bd :sd) 4] [(seq :hh :oh) 2]])',
  },
  "play-scenes": {
    signature: "(play-scenes [pat ...])",
    description: "Play patterns as sequential 1-cycle scenes.",
    example: '(play-scenes [(seq :bd :sd) (seq :hh :oh)])',
  },

  // --- Special forms ---
  "def": {
    signature: "(def name value)",
    description: "Bind a name in the global environment. The name persists across evaluations.",
    example: '(def kick (seq :bd :_ :bd :_))',
  },
  "let": {
    signature: "(let [name val ...] body)",
    description: "Create local bindings. Names are only visible inside the let body.",
    example: '(let [x (seq :bd :sd)] (fast 2 x))',
  },
  "fn": {
    signature: "(fn [params ...] body)",
    description: "Create an anonymous function.",
    example: '(fn [p] (fast 2 p))',
  },
  "lambda": {
    signature: "(lambda [params ...] body)",
    description: "Create an anonymous function (alias for fn).",
    example: '(lambda [p] (fast 2 p))',
  },
  "if": {
    signature: "(if condition then else)",
    description: "Conditional expression. Evaluates then if condition is truthy, else otherwise.",
    example: '(if (> x 0) :bd :sd)',
  },
  "do": {
    signature: "(do expr ...)",
    description: "Evaluate expressions in sequence, return the value of the last one.",
    example: '(do (def x 1) (+ x 2))',
  },

  // --- Demo & Tutorial ---
  "demo": {
    signature: "(demo :name) or (demo)",
    description: "Load a starter template into the editor and play it. With no arguments, lists available demos.",
    example: '(demo :techno)',
  },
  "tutorial": {
    signature: "(tutorial) or (tutorial n)",
    description: "Load tutorial chapter n into the editor. Defaults to chapter 1. Press Ctrl+Enter to play.",
    example: '(tutorial 3)',
  },
};

/**
 * Find the word at a given position in the document.
 * Returns { from, to, word } or null.
 */
function wordAt(state, pos) {
  const tree = syntaxTree(state);
  let node = tree.resolveInner(pos, 0);

  // Walk up to find a BuiltinName or Symbol node
  while (node) {
    if (node.name === "BuiltinName" || node.name === "Symbol") {
      const word = state.sliceDoc(node.from, node.to);
      return { from: node.from, to: node.to, word };
    }
    // If we hit a structural node, stop
    if (node.name === "List" || node.name === "Vector" || node.name === "Program") {
      break;
    }
    node = node.parent;
  }
  return null;
}

/**
 * CM6 hoverTooltip provider for REPuLse-Lisp built-ins.
 */
export const lispHoverTooltip = hoverTooltip((view, pos) => {
  const hit = wordAt(view.state, pos);
  if (!hit) return null;

  const doc = DOCS[hit.word];
  if (!doc) return null;

  return {
    pos: hit.from,
    end: hit.to,
    above: true,
    create() {
      const container = document.createElement("div");
      container.className = "repulse-hover-doc";

      const sig = document.createElement("div");
      sig.className = "repulse-hover-sig";
      sig.textContent = doc.signature;
      container.appendChild(sig);

      const desc = document.createElement("div");
      desc.className = "repulse-hover-desc";
      desc.textContent = doc.description;
      container.appendChild(desc);

      if (doc.example) {
        const ex = document.createElement("div");
        ex.className = "repulse-hover-example";
        ex.textContent = doc.example;
        container.appendChild(ex);
      }

      return { dom: container };
    },
  };
});
```

---

### 3. `app/src/repulse/lisp-lang/index.js` — wire hover tooltip

Import the hover tooltip and add it to the language extension:

```javascript
import { parser } from "./parser.js";
import { LRLanguage, LanguageSupport, indentNodeProp, foldNodeProp } from "@codemirror/language";
import { autocompletion } from "@codemirror/autocomplete";
import { repulseLispHighlight } from "./highlight.js";
import { rainbowBrackets } from "./rainbow.js";
import { builtinCompletions } from "./completions.js";
import { defsCompletionSource } from "./defs-completion.js";
import { keywordsCompletionSource } from "./keywords-completion.js";
import { lispHoverTooltip } from "./hover.js";

const lispParser = parser.configure({
  props: [
    repulseLispHighlight,
    indentNodeProp.add({
      List:   cx => cx.baseIndent + 2,
      Vector: cx => cx.baseIndent + 2,
      Map:    cx => cx.baseIndent + 2,
    }),
    foldNodeProp.add({
      List:   node => ({ from: node.from + 1, to: node.to - 1 }),
      Vector: node => ({ from: node.from + 1, to: node.to - 1 }),
    }),
  ],
});

export const lispLanguage = new LanguageSupport(
  LRLanguage.define({ parser: lispParser, languageData: { commentTokens: { line: ";" } } }),
  [
    rainbowBrackets,
    lispHoverTooltip,
    autocompletion({ override: [builtinCompletions, defsCompletionSource, keywordsCompletionSource] }),
  ]
);
```

The single change is: import `lispHoverTooltip` from `./hover.js` and add it to the
extensions array. `hoverTooltip` returns a plain extension — it is listed alongside
`rainbowBrackets` and `autocompletion`.

---

### 4. `app/public/css/main.css` — hover tooltip styling

Add the following CSS rules at the end of the file:

```css
/* --- Hover documentation tooltip --- */
.repulse-hover-doc {
  font-family: "JetBrains Mono", "Fira Code", monospace;
  font-size: 13px;
  max-width: 480px;
  padding: 8px 12px;
  background: #21252b;
  border: 1px solid #3a3f4b;
  border-radius: 4px;
  color: #abb2bf;
  line-height: 1.5;
}

.repulse-hover-sig {
  color: #c678dd;
  font-weight: 600;
  margin-bottom: 4px;
}

.repulse-hover-desc {
  margin-bottom: 4px;
}

.repulse-hover-example {
  color: #98c379;
  font-style: italic;
  padding-top: 4px;
  border-top: 1px solid #3a3f4b;
}
```

These colours match the oneDark theme: purple for the signature (same as built-in name
highlighting), green for the example (same as strings).

---

### 5. `app/src/repulse/lisp-lang/completions.js` — add demo and tutorial entries

Add after the `"upd"` entry in the Tracks section:

```javascript
  // --- Onboarding ---
  { label: "demo",      type: "function", detail: "(demo :name) — load a starter template; (demo) lists available demos" },
  { label: "tutorial",  type: "function", detail: "(tutorial n) — load tutorial chapter n into the editor" },
```

---

### 6. `app/src/repulse/lisp-lang/repulse-lisp.grammar` — add to BuiltinName

Add `"demo"` and `"tutorial"` to the `BuiltinName` alternatives. Place them at the end
of the second line (after `"upd"`):

```
    "play" | "mute!" | "unmute!" | "solo!" | "clear!" | "tracks" | "tap!" | "midi-sync!" |
    "upd" | "demo" | "tutorial"
```

Then run `npm run gen:grammar` to regenerate `parser.js` and `parser.terms.js`.

---

### 7. `app/package.json` — verify `@codemirror/view` exports `hoverTooltip`

`hoverTooltip` is part of `@codemirror/view`, which is already a dependency. No new
npm package is needed. Verify the import resolves:

```javascript
import { hoverTooltip } from "@codemirror/view";
```

If the installed version of `@codemirror/view` is < 6.2, `hoverTooltip` may not exist.
In that case, update:

```bash
cd app && npm install @codemirror/view@latest
```

---

### 8. `docs/USAGE.md` — add onboarding section

Add a new section after "Getting started", before "The editor":

```markdown
## Demos and tutorials

### Starter templates

Type in the command bar (the `>` input at the bottom):

    (demo :techno)

This loads a full multi-track techno pattern into the editor and starts playing it.
Available demos:

| Demo | Description |
|---|---|
| `:techno` | Four-on-the-floor, offbeat hats, acid bassline |
| `:ambient` | Slow pad chords, gentle melody |
| `:dnb` | 174 BPM breakbeat, sub bass |
| `:minimal` | Sparse kick, subtle hats, one-note bass |
| `:house` | Classic house beat, organ stabs |
| `:dub` | Heavy bass, delay-heavy snare |
| `:experimental` | Algorithmic patterns: every, rev, fmap |

Type `(demo)` with no arguments to see the list.

### Interactive tutorial

    (tutorial)        ;; loads chapter 1
    (tutorial 3)      ;; loads chapter 3

Eight chapters that teach REPuLse from first principles:

1. First sound — `seq`
2. Layering — `stack`
3. Speed — `fast` and `slow`
4. Evolution — `every`
5. Naming — `def`
6. Multi-track — `play`
7. Melody — `scale` and `chord`
8. Expression — `amp`, `decay`, `->>`

Each chapter is a playable program. Press Ctrl+Enter to hear it, read the comments,
experiment, then move to the next chapter.

### Hover documentation

Hover over any built-in name in the editor to see its signature, description, and
an example.
```

---

### 9. `README.md` — add demo/tutorial rows to language reference

In the language reference table, add:

```markdown
| Demo template  | `(demo :techno)`, `(demo)`                    |
| Tutorial       | `(tutorial)`, `(tutorial 3)`                   |
```

---

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/app.cljs` | Add `demo-templates` map, `tutorial-chapters` vector, register `demo` and `tutorial` in `ensure-env!` |
| `app/src/repulse/lisp-lang/hover.js` | **New** — `hoverTooltip` provider with full docs map |
| `app/src/repulse/lisp-lang/index.js` | Import `lispHoverTooltip`, add to extension array |
| `app/src/repulse/lisp-lang/completions.js` | Add `demo` and `tutorial` entries |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add `"demo"` and `"tutorial"` to `BuiltinName` |
| `app/src/repulse/lisp-lang/parser.js` | **Regenerated** — `npm run gen:grammar` |
| `app/src/repulse/lisp-lang/parser.terms.js` | **Regenerated** — `npm run gen:grammar` |
| `app/public/css/main.css` | Add `.repulse-hover-doc` tooltip styles |
| `docs/USAGE.md` | New "Demos and tutorials" section |
| `README.md` | Add demo/tutorial rows to language reference table |
| `CLAUDE.md` | Mark Phase J as delivered when done |

No changes to `packages/core`, `packages/lisp`, `packages/audio`, or any Rust files.

---

## Definition of done

### Demo templates

- [ ] `(demo :techno)` replaces editor content with a techno pattern, sets BPM to 130, and starts playing
- [ ] `(demo :ambient)` loads and plays an ambient pattern at 72 BPM
- [ ] `(demo :dnb)` loads and plays a drum & bass pattern at 174 BPM
- [ ] `(demo :minimal)` loads and plays a minimal pattern at 120 BPM
- [ ] `(demo :house)` loads and plays a house pattern at 124 BPM
- [ ] `(demo :dub)` loads and plays a dub pattern at 140 BPM
- [ ] `(demo :experimental)` loads and plays an algorithmic pattern at 110 BPM
- [ ] `(demo)` with no arguments returns a list of available demo names
- [ ] `(demo :nonexistent)` returns a helpful error listing available demos
- [ ] Demos work from both the command bar and the main editor
- [ ] Each demo showcases different language features (multi-track, effects params, scale, every, etc.)

### Tutorial

- [ ] `(tutorial)` loads chapter 1 into the editor without auto-playing
- [ ] `(tutorial 1)` through `(tutorial 8)` each load the correct chapter
- [ ] Each chapter is a valid, playable REPuLse program (pressing Ctrl+Enter produces sound)
- [ ] Each chapter has `;` comments explaining the concepts being demonstrated
- [ ] Chapters progressively introduce: seq, stack, fast/slow, every, def, play, scale/chord, amp/decay/->>
- [ ] `(tutorial 0)` or `(tutorial 99)` returns a helpful message with the valid range
- [ ] Tutorial works from both the command bar and the main editor

### Hover documentation

- [ ] Hovering over `fast` shows signature `(fast factor pat)`, description, and example
- [ ] Hovering over `seq` shows documentation
- [ ] Hovering over `->>` shows documentation
- [ ] Hovering over `amp`, `scale`, `chord`, `play`, `fx`, `demo`, `tutorial` all show docs
- [ ] Hovering over user-defined names (e.g. `kick` after `(def kick ...)`) does NOT show a tooltip
- [ ] Hovering over keywords (`:bd`, `:minor`) does NOT show a tooltip
- [ ] Hovering over numbers and strings does NOT show a tooltip
- [ ] Tooltip styling matches oneDark theme — dark background, purple signature, green example
- [ ] Tooltip does not interfere with autocomplete popup
- [ ] Tooltip dismisses when mouse leaves the word

### Grammar & completions

- [ ] `demo` and `tutorial` receive syntax highlighting as built-in names (purple in oneDark)
- [ ] `demo` and `tutorial` appear in autocomplete suggestions
- [ ] All existing built-ins still highlight and complete correctly
- [ ] `npm run gen:grammar` runs without errors after grammar edit

### No regressions

- [ ] Active-event amber flash (Phase 5) still works
- [ ] Rainbow brackets (Phase 10) still work
- [ ] Code completion (Phase C) still works
- [ ] Editor persistence (Phase D) still works — editor content survives reload
- [ ] Context panel (Phase E) still updates bindings and effects
- [ ] Track panel (Phase 4) still shows track rows with playheads
- [ ] No console errors in Chrome, Firefox, or Safari
