# Phase K — Mini-Notation & Sharing

## Goal

Add three capabilities that make REPuLse faster to write and easier to share:

1. **Mini-notation** — `(~ "bd sd [hh hh] bd")` parses a compact Tidal/Strudel-style
   string into REPuLse patterns using existing combinators.
2. **Gist import** — `(load-gist "https://gist.github.com/user/id")` fetches a Gist,
   loads the raw content into the editor, and optionally auto-evaluates.
3. **WAV export** — `(export 4)` renders 4 cycles of the current pattern to a downloadable
   WAV file via `OfflineAudioContext`.

```lisp
;; Before — verbose:
(seq :bd (seq :sd :hh) :bd :sd)

;; After — mini-notation:
(~ "bd [sd hh] bd sd")

;; Before — no way to share a snippet as a file:
;; (manually copy-paste into editor)

;; After — one-line import:
(load-gist "https://gist.github.com/user/abc123")

;; Before — no export:
;; (record system audio externally)

;; After — offline render:
(export 4)   ; downloads 4 cycles as WAV
```

Mini-notation is opt-in sugar — the Lisp is the host language, and `~` is just another
function that returns a Pattern. Everything composes:

```lisp
;; Mini-notation inside Lisp combinators:
(fast 2 (~ "bd sd"))
(stack (~ "bd _ bd _") (~ "_ sd _ sd"))
(->> (~ "c4 e4 g4") (amp 0.6) (attack 0.02))
(every 4 rev (~ "bd sd [hh oh] bd"))

;; Alternation — cycle-dependent:
(~ "<bd sd> hh")   ; cycle 1: "bd hh", cycle 2: "sd hh", …

;; Probability:
(~ "bd? sd hh?")   ; bd and hh play with 50% probability

;; Repetition:
(~ "hh*4 sd")      ; ≡ (seq (seq :hh :hh :hh :hh) :sd)

;; Weight / elongation:
(~ "bd@3 sd")       ; bd takes 3/4 of the cycle, sd takes 1/4

;; Sample index:
(~ "bd:2 sd:0")     ; ≡ (seq (sound :bd 2) (sound :sd 0))
```

---

## Background

### Current state

REPuLse patterns are built exclusively from Lisp function calls:

```lisp
(seq :bd :sd (seq :hh :hh) :bd)
(stack (seq :bd :_ :bd :_) (fast 2 (seq :hh :oh)))
```

This is expressive but verbose for simple rhythms. Tidal Cycles and Strudel use a
compact string notation that fits an entire pattern into one line. REPuLse should
support this as embedded sugar inside the Lisp, not as a replacement for it.

### Existing infrastructure

- **Pattern combinators:** `core/seq*`, `core/stack*`, `core/fast`, `core/slow`,
  `core/pure`, `core/fmap`, `core/every`, `core/combine` — all in `packages/core/src/repulse/core.cljs`
- **Lisp reader:** recursive-descent parser in `packages/lisp/src/repulse/lisp/reader.cljs`
- **Evaluator:** `make-env` in `packages/lisp/src/repulse/lisp/eval.cljs` registers built-ins
- **App bindings:** `ensure-env!` in `app/src/repulse/app.cljs` adds DOM/network functions
- **Audio dispatch:** `play-event` in `app/src/repulse/audio.cljs` handles maps, keywords, numbers
- **Session URLs:** `#v1:base64-json` hash encoding with share button (Phase 4)
- **Grammar:** `app/src/repulse/lisp-lang/repulse-lisp.grammar` for syntax highlighting
- **Completions:** `app/src/repulse/lisp-lang/completions.js`

### No changes needed to

- `packages/core/src/repulse/core.cljs` — the mini-notation compiles down to existing combinators
- `packages/audio/` — Rust/WASM synthesis unchanged
- `app/src/repulse/audio.cljs` — scheduler and dispatch unchanged (mini-notation
  produces the same Pattern values that existing Lisp expressions do)

---

## Design

### 1. Mini-notation syntax

| Syntax | Meaning | Desugars to |
|---|---|---|
| `"bd sd hh"` | sequence | `(seq :bd :sd :hh)` |
| `"bd [sd hh]"` | subdivision | `(seq :bd (seq :sd :hh))` |
| `"bd*3"` | repetition | `(seq :bd :bd :bd)` — equivalently `(fast 3 (pure :bd))` |
| `"<bd sd>"` | alternation (cycle-dependent) | on cycle N pick element `(mod N count)` |
| `"~"` or `"_"` | rest / silence | `:_` |
| `"bd?"` | 50% probability | `(degrade-by 0.5 (pure :bd))` |
| `"bd:2"` | sample index | `{:bank :bd :n 2}` (same as `(sound :bd 2)`) |
| `"bd@3"` | weight / elongation | that element takes 3 units in the sequence |
| `"440"` | number stays a number | `440` |
| `"c4"` | note name becomes keyword | `:c4` |

### 2. Parser architecture

The parser lives in `packages/lisp/src/repulse/lisp/mini.cljs` as a pure namespace that
requires only `repulse.core`. It has three stages:

1. **Tokeniser** — scans the string character by character, producing a flat sequence of
   tokens: `:open-bracket`, `:close-bracket`, `:open-angle`, `:close-angle`, `:star`,
   `:question`, `:colon`, `:at`, `:tilde`, `:underscore`, `:atom`.
2. **Parser** — recursive-descent over the token stream, producing an AST of nested nodes:
   `{:type :seq :children [...]}`, `{:type :alt :children [...]}`,
   `{:type :atom :value :bd}`, `{:type :rest}`, etc.
3. **Compiler** — walks the AST and returns a Pattern by calling `core/seq*`, `core/stack*`,
   `core/fast`, `core/pure`, etc.

### 3. Alternation (`<>`)

Alternation requires cycle-aware behaviour: on cycle N, pick element `(mod N count)`.
This is implemented as a new pattern constructor `alt*` that queries the underlying
patterns selectively based on the cycle number.

```clojure
(defn alt*
  "Alternation: on cycle N, play the (mod N count)-th pattern."
  [pats]
  (let [n (count pats)]
    (core/pattern
      (fn [sp]
        (let [cycle (int (Math/floor (core/rat->float (:start sp))))
              idx   (mod cycle n)]
          (core/query (nth pats idx) sp))))))
```

This is defined in `mini.cljs` since it's only needed by mini-notation. If it turns out
to be useful in the Lisp layer later, it can be promoted to `core.cljs`.

### 4. Probability (`?`)

`bd?` means "play bd with 50% probability, silence otherwise." Implemented inline:

```clojure
(defn degrade
  "With probability 0.5, replace each event with silence (empty)."
  [pat]
  (core/pattern
    (fn [sp]
      (filter (fn [_] (> (Math/random) 0.5)) (core/query pat sp)))))
```

This is a simple random gate — not deterministic across cycles. Good enough for
live performance. A seeded PRNG can be added later if needed.

### 5. The `~` binding

The `~` function is bound in `make-env` in `eval.cljs`. It takes one string argument
and returns a Pattern:

```clojure
"~" (fn [s] (mini/parse (unwrap s)))
```

The `~` inside mini-notation strings (rest) does NOT conflict with the `~` function name
in Lisp — the function is called with a string argument `(~ "bd ~ sd")`, and `~` inside
that string is just a character parsed by the mini-notation tokeniser.

### 6. Gist import

`(load-gist url)` is registered in `ensure-env!` in `app.cljs` since it needs:
- `js/fetch` for network access
- The editor view atom to set content
- Optionally `evaluate!` to auto-run the loaded code

It supports two URL formats:
- `https://gist.github.com/user/id` — extracts raw URL via the Gist API
- `https://gist.githubusercontent.com/...` — fetches directly as raw text

### 7. WAV export

`(export N)` is registered in `ensure-env!` in `app.cljs`. It:
1. Creates an `OfflineAudioContext` with the correct sample rate and duration
2. Replays the current pattern through the offline context using the JS synthesis
   fallback (the same `make-kick`, `make-snare`, `make-hihat`, `make-sine` functions
   that already exist in `audio.cljs`)
3. Encodes the rendered `AudioBuffer` as WAV (PCM 16-bit, stereo)
4. Creates a download link via `URL.createObjectURL`

The JS fallback is used because `AudioWorklet` is not reliably available in
`OfflineAudioContext` across all browsers.

---

## Implementation

### 1. New file: `packages/lisp/src/repulse/lisp/mini.cljs`

The complete mini-notation parser and compiler. Requires only `repulse.core`.

```clojure
(ns repulse.lisp.mini
  (:require [repulse.core :as core]))

;;; ── Tokeniser ──────────────────────────────────────────────────────

;; Token types:
;;   :open-bracket   [
;;   :close-bracket  ]
;;   :open-angle     <
;;   :close-angle    >
;;   :star           *
;;   :question       ?
;;   :colon          :
;;   :at             @
;;   :atom           (with :text field — the raw word)

(defn- whitespace? [ch]
  (contains? #{\space \tab \newline \return} ch))

(defn- special? [ch]
  (contains? #{\[ \] \< \> \* \? \: \@} ch))

(defn- tokenise
  "Scan a mini-notation string into a flat sequence of tokens."
  [s]
  (loop [i 0, tokens []]
    (if (>= i (count s))
      tokens
      (let [ch (nth s i)]
        (cond
          (whitespace? ch)
          (recur (inc i) tokens)

          (= ch \[) (recur (inc i) (conj tokens {:type :open-bracket}))
          (= ch \]) (recur (inc i) (conj tokens {:type :close-bracket}))
          (= ch \<) (recur (inc i) (conj tokens {:type :open-angle}))
          (= ch \>) (recur (inc i) (conj tokens {:type :close-angle}))
          (= ch \*) (recur (inc i) (conj tokens {:type :star}))
          (= ch \?) (recur (inc i) (conj tokens {:type :question}))
          (= ch \:) (recur (inc i) (conj tokens {:type :colon}))
          (= ch \@) (recur (inc i) (conj tokens {:type :at}))

          :else
          ;; Accumulate word characters until whitespace or special
          (let [end (loop [j i]
                      (if (or (>= j (count s))
                              (whitespace? (nth s j))
                              (special? (nth s j)))
                        j
                        (recur (inc j))))
                word (subs s i end)]
            (recur end (conj tokens {:type :atom :text word}))))))))

;;; ── Parser ─────────────────────────────────────────────────────────

;; Recursive-descent parser over the token stream.
;; Returns an AST node and the remaining token index.
;;
;; Grammar (informal):
;;   sequence = element+
;;   element  = group | alt-group | atom-expr
;;   group    = "[" sequence "]"
;;   alt      = "<" sequence ">"      (but children are alternatives, not subsequence)
;;   atom-expr = atom suffix*
;;   suffix   = "*" number | "?" | ":" number | "@" number
;;
;; Precedence: suffixes bind tighter than sequencing.
;; Sequence is implicit — space-separated elements form a seq.

(declare parse-sequence)

(defn- peek-token [tokens pos]
  (when (< pos (count tokens))
    (nth tokens pos)))

(defn- parse-number-text
  "Parse a string as a number. Returns nil if not a number."
  [s]
  (let [n (js/parseFloat s)]
    (when-not (js/isNaN n) n)))

(defn- note-name?
  "True if s looks like a note name: a-g, optional accidental (s/b), then digit(s)."
  [s]
  (boolean (re-matches #"[a-g][sb]?\d+" s)))

(defn- atom-value
  "Convert a raw text token to its REPuLse value: keyword, number, or note keyword."
  [text]
  (cond
    (or (= text "~") (= text "_"))  :_
    (parse-number-text text)         (parse-number-text text)
    (note-name? text)                (keyword text)
    :else                            (keyword text)))

(defn- parse-atom
  "Parse a single atom token (not brackets/angles), then consume any suffixes.
   Returns [ast-node next-pos]."
  [tokens pos]
  (let [tok (nth tokens pos)
        base-val (atom-value (:text tok))
        pos (inc pos)]
    ;; Consume suffixes: * ? : @
    (loop [node {:type :atom :value base-val :weight 1}
           p pos]
      (let [tok (peek-token tokens p)]
        (cond
          ;; *N — repetition
          (and tok (= (:type tok) :star))
          (let [next-tok (peek-token tokens (inc p))]
            (if (and next-tok (= (:type next-tok) :atom))
              (let [n (or (parse-number-text (:text next-tok)) 1)]
                (recur {:type :repeat :child node :times (int n) :weight (:weight node)}
                       (+ p 2)))
              ;; bare * with no number — treat as *2
              (recur {:type :repeat :child node :times 2 :weight (:weight node)}
                     (inc p))))

          ;; ? — probability (50% chance of rest)
          (and tok (= (:type tok) :question))
          (recur {:type :degrade :child node :weight (:weight node)}
                 (inc p))

          ;; :N — sample index
          (and tok (= (:type tok) :colon))
          (let [next-tok (peek-token tokens (inc p))]
            (if (and next-tok (= (:type next-tok) :atom))
              (let [n (or (parse-number-text (:text next-tok)) 0)]
                (recur (assoc node :sample-index (int n))
                       (+ p 2)))
              [node p]))

          ;; @N — weight / elongation
          (and tok (= (:type tok) :at))
          (let [next-tok (peek-token tokens (inc p))]
            (if (and next-tok (= (:type next-tok) :atom))
              (let [w (or (parse-number-text (:text next-tok)) 1)]
                (recur (assoc node :weight (int w))
                       (+ p 2)))
              [node p]))

          ;; No more suffixes
          :else
          [node p])))))

(defn- parse-element
  "Parse one element: a bracketed group, an angle-bracket alternation, or an atom."
  [tokens pos]
  (let [tok (peek-token tokens pos)]
    (cond
      ;; [ ... ] — subdivision group
      (= (:type tok) :open-bracket)
      (let [[seq-node next-pos] (parse-sequence tokens (inc pos))
            close (peek-token tokens next-pos)]
        (when-not (and close (= (:type close) :close-bracket))
          (throw (ex-info "Expected ]" {:type :mini-parse-error})))
        ;; After the ], check for suffixes on the group
        (let [pos' (inc next-pos)]
          (loop [node seq-node p pos']
            (let [tok (peek-token tokens p)]
              (cond
                (and tok (= (:type tok) :star))
                (let [next-tok (peek-token tokens (inc p))]
                  (if (and next-tok (= (:type next-tok) :atom))
                    (let [n (or (parse-number-text (:text next-tok)) 2)]
                      (recur {:type :repeat :child node :times (int n) :weight 1} (+ p 2)))
                    (recur {:type :repeat :child node :times 2 :weight 1} (inc p))))
                (and tok (= (:type tok) :question))
                (recur {:type :degrade :child node :weight 1} (inc p))
                (and tok (= (:type tok) :at))
                (let [next-tok (peek-token tokens (inc p))]
                  (if (and next-tok (= (:type next-tok) :atom))
                    (let [w (or (parse-number-text (:text next-tok)) 1)]
                      (recur (assoc node :weight (int w)) (+ p 2)))
                    [node p]))
                :else
                [node p])))))

      ;; < ... > — alternation
      (= (:type tok) :open-angle)
      (let [[seq-node next-pos] (parse-sequence tokens (inc pos))
            close (peek-token tokens next-pos)]
        (when-not (and close (= (:type close) :close-angle))
          (throw (ex-info "Expected >" {:type :mini-parse-error})))
        [{:type :alt :children (:children seq-node)} (inc next-pos)])

      ;; atom
      (= (:type tok) :atom)
      (parse-atom tokens pos)

      :else
      (throw (ex-info (str "Unexpected token: " (pr-str tok))
                       {:type :mini-parse-error})))))

(defn- parse-sequence
  "Parse a sequence of elements until we hit ], >, or end of tokens.
   Returns [{:type :seq :children [...]} next-pos]."
  [tokens pos]
  (loop [children [] p pos]
    (let [tok (peek-token tokens p)]
      (if (or (nil? tok)
              (= (:type tok) :close-bracket)
              (= (:type tok) :close-angle))
        [{:type :seq :children children} p]
        (let [[node next-pos] (parse-element tokens p)]
          (recur (conj children node) next-pos))))))

;;; ── Compiler: AST → Pattern ────────────────────────────────────────

(declare compile-node)

(defn- alt*
  "Alternation: on cycle N, play the (mod N count)-th pattern."
  [pats]
  (let [n (count pats)]
    (core/pattern
      (fn [sp]
        (let [cycle (int (Math/floor (core/rat->float (:start sp))))
              idx   (mod cycle n)]
          (core/query (nth pats idx) sp))))))

(defn- degrade
  "Randomly gate events — each event has a 50% chance of being dropped."
  [pat]
  (core/pattern
    (fn [sp]
      (filter (fn [_] (> (Math/random) 0.5)) (core/query pat sp)))))

(defn- compile-node
  "Compile an AST node to a Pattern."
  [node]
  (case (:type node)
    :atom
    (let [v (:value node)]
      (if (:sample-index node)
        (core/pure {:bank v :n (:sample-index node)})
        (core/pure v)))

    :rest
    (core/pure :_)

    :seq
    (let [children (:children node)]
      (if (= 1 (count children))
        (compile-node (first children))
        ;; Calculate total weight for proportional timing
        (let [total-weight (reduce + (map #(or (:weight %) 1) children))
              all-unit?    (every? #(= 1 (or (:weight %) 1)) children)]
          (if all-unit?
            ;; Simple case: all weights are 1 — use seq*
            (core/seq* (mapv compile-node children))
            ;; Weighted case: use fast + stack to give proportional time slices
            ;; Each child of weight w gets w/total-weight of the cycle.
            ;; Implementation: slow the whole seq by total-weight, then use
            ;; arrange* to give each child its proportional share.
            (core/arrange*
              (mapv (fn [child]
                      [(compile-node child) (or (:weight child) 1)])
                    children))))))

    :alt
    (let [children (:children node)]
      (if (= 1 (count children))
        (compile-node (first children))
        (alt* (mapv compile-node children))))

    :repeat
    (let [child-pat (compile-node (:child node))
          times     (:times node)]
      (core/fast times child-pat))

    :degrade
    (degrade (compile-node (:child node)))

    ;; Fallback
    (core/pure :_)))

;;; ── Public API ─────────────────────────────────────────────────────

(defn parse
  "Parse a mini-notation string and return a Pattern.
   (parse \"bd sd [hh hh] bd\")  → pattern equivalent to (seq :bd :sd (seq :hh :hh) :bd)"
  [s]
  (let [tokens (tokenise s)
        [ast _] (parse-sequence tokens 0)]
    (compile-node ast)))
```

---

### 2. New file: `packages/lisp/test/repulse/lisp/mini_test.cljs`

Unit tests for the tokeniser, parser, and compiler.

```clojure
(ns repulse.lisp.mini-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [repulse.lisp.mini :as mini]
            [repulse.core :as core]))

(def ^:private one-cycle
  (core/span (core/int->rat 0) (core/int->rat 1)))

(defn- values [pat]
  (mapv :value (core/query pat one-cycle)))

;;; ── Basic sequences ────────────────────────────────────────────────

(deftest simple-sequence
  (is (= [:bd :sd :hh] (values (mini/parse "bd sd hh")))))

(deftest single-element
  (is (= [:bd] (values (mini/parse "bd")))))

(deftest rest-tilde
  (is (= [:bd :_ :sd] (values (mini/parse "bd ~ sd")))))

(deftest rest-underscore
  (is (= [:bd :_ :sd] (values (mini/parse "bd _ sd")))))

;;; ── Numbers and note names ─────────────────────────────────────────

(deftest number-value
  (is (= [440] (values (mini/parse "440")))))

(deftest note-keyword
  (is (= [:c4 :e4 :g4] (values (mini/parse "c4 e4 g4")))))

(deftest mixed-types
  (is (= [:bd 440 :c4] (values (mini/parse "bd 440 c4")))))

;;; ── Subdivision ────────────────────────────────────────────────────

(deftest subdivision
  ;; "bd [sd hh]" → 2 top-level slots; second slot has 2 sub-events
  (let [evs (core/query (mini/parse "bd [sd hh]") one-cycle)]
    (is (= 3 (count evs)))
    (is (= :bd (:value (first evs))))
    (is (= :sd (:value (second evs))))
    (is (= :hh (:value (nth evs 2))))))

(deftest nested-subdivision
  ;; "bd [[sd hh] cp]" → bd takes half, then [sd hh] and cp split the other half
  (let [evs (core/query (mini/parse "bd [[sd hh] cp]") one-cycle)]
    (is (= 4 (count evs)))
    (is (= :bd (:value (first evs))))))

;;; ── Repetition ─────────────────────────────────────────────────────

(deftest repetition
  ;; "hh*4" → fast 4 of a single hh — produces 4 events per cycle
  (let [evs (core/query (mini/parse "hh*4") one-cycle)]
    (is (= 4 (count evs)))
    (is (every? #(= :hh (:value %)) evs))))

(deftest repetition-in-sequence
  ;; "bd hh*2 sd" → 3 top-level slots; hh slot has 2 sub-events
  (let [evs (core/query (mini/parse "bd hh*2 sd") one-cycle)]
    (is (= 4 (count evs)))
    (is (= :bd (:value (first evs))))
    (is (= :hh (:value (second evs))))
    (is (= :hh (:value (nth evs 2))))
    (is (= :sd (:value (nth evs 3))))))

;;; ── Alternation ────────────────────────────────────────────────────

(deftest alternation-cycle-0
  ;; "<bd sd>" on cycle 0 → bd
  (let [evs (core/query (mini/parse "<bd sd>") (core/span [0 1] [1 1]))]
    (is (= [:bd] (mapv :value evs)))))

(deftest alternation-cycle-1
  ;; "<bd sd>" on cycle 1 → sd
  (let [evs (core/query (mini/parse "<bd sd>") (core/span [1 1] [2 1]))]
    (is (= [:sd] (mapv :value evs)))))

(deftest alternation-wraps
  ;; "<bd sd cp>" on cycle 3 → wraps back to bd
  (let [evs (core/query (mini/parse "<bd sd cp>") (core/span [3 1] [4 1]))]
    (is (= [:bd] (mapv :value evs)))))

;;; ── Sample index ───────────────────────────────────────────────────

(deftest sample-index
  (let [evs (core/query (mini/parse "bd:2") one-cycle)]
    (is (= 1 (count evs)))
    (is (= {:bank :bd :n 2} (:value (first evs))))))

(deftest sample-index-in-sequence
  (let [vals (values (mini/parse "bd:0 sd:1"))]
    (is (= [{:bank :bd :n 0} {:bank :sd :n 1}] vals))))

;;; ── Probability ────────────────────────────────────────────────────

(deftest degrade-produces-subset
  ;; Run the pattern many times — sometimes events are dropped
  ;; With 50% probability over 100 trials, we should see both 0 and 1 events
  (let [pat   (mini/parse "bd?")
        counts (map (fn [_] (count (core/query pat one-cycle))) (range 100))
        has-0  (some zero? counts)
        has-1  (some #(= 1 %) counts)]
    (is (or has-0 has-1) "degrade should sometimes drop events")))

;;; ── Weight / elongation ────────────────────────────────────────────

(deftest weight-simple
  ;; "bd@3 sd" → bd takes 3/4 of the cycle, sd takes 1/4
  ;; Total of 2 events (bd over 3 cycles + sd over 1 cycle via arrange*)
  (let [evs (core/query (mini/parse "bd@3 sd") one-cycle)]
    ;; The arrange* will play bd on cycle 0 (which falls in the 3-cycle section)
    (is (= :bd (:value (first evs))))))

;;; ── Composition with Lisp ──────────────────────────────────────────

(deftest fast-of-mini
  (let [pat (core/fast 2 (mini/parse "bd sd"))
        evs (core/query pat one-cycle)]
    (is (= 4 (count evs)))))

(deftest stack-of-minis
  (let [pat (core/stack* [(mini/parse "bd sd") (mini/parse "hh hh hh hh")])
        evs (core/query pat one-cycle)]
    (is (= 6 (count evs)))))

;;; ── Edge cases ─────────────────────────────────────────────────────

(deftest empty-string
  (let [evs (core/query (mini/parse "") one-cycle)]
    (is (empty? evs))))

(deftest extra-whitespace
  (is (= [:bd :sd] (values (mini/parse "  bd   sd  ")))))

(deftest group-with-suffix
  ;; "[bd sd]*2" — the group is repeated twice
  (let [evs (core/query (mini/parse "[bd sd]*2") one-cycle)]
    (is (= 4 (count evs)))))
```

---

### 3. `packages/lisp/src/repulse/lisp/eval.cljs` — add `~` binding

Add `[repulse.lisp.mini :as mini]` to the namespace require. Then add one entry to
`make-env`:

```clojure
"~" (fn [s] (mini/parse (unwrap s)))
```

Place it after the `"comp"` entry, near the pattern constructors.

The `~` function simply delegates to `mini/parse`. It takes one string argument and
returns a Pattern. If called with a non-string argument, `mini/parse` will throw
a clear error.

---

### 4. `app/src/repulse/app.cljs` — add `load-gist` and `export` bindings

In `ensure-env!`, add after the existing `"fx"` entry:

```clojure
;; --- Mini-notation shorthand (also available from eval.cljs, but
;;     re-exported here for discoverability) ---

;; --- Gist import ---
"load-gist"
(fn [url]
  (let [url' (leval/unwrap url)
        raw-url (if (re-find #"gist\.githubusercontent\.com" url')
                  url'
                  ;; Convert gist.github.com/user/id → API URL
                  (let [[_ gist-id] (re-find #"/([a-f0-9]+)/?$" url')]
                    (str "https://api.github.com/gists/" gist-id)))]
    (if (re-find #"api\.github\.com" raw-url)
      ;; API path — fetch JSON, extract first file's content
      (-> (js/fetch raw-url)
          (.then #(.json %))
          (.then (fn [data]
                   (let [files  (js->clj (.-files data))
                         first-file (second (first files))
                         content (get first-file "content")]
                     (when-let [view @editor-view]
                       (.dispatch view
                                  #js {:changes #js {:from   0
                                                     :to     (.. view -state -doc -length)
                                                     :insert content}})
                       (evaluate! content)))))
          (.catch (fn [e]
                    (set-output! (str "Gist load failed: " e) :error))))
      ;; Raw URL — fetch text directly
      (-> (js/fetch raw-url)
          (.then #(.text %))
          (.then (fn [text]
                   (when-let [view @editor-view]
                     (.dispatch view
                                #js {:changes #js {:from   0
                                                   :to     (.. view -state -doc -length)
                                                   :insert text}})
                     (evaluate! text))))
          (.catch (fn [e]
                    (set-output! (str "Gist load failed: " e) :error)))))
    (str "loading gist…")))

;; --- WAV export ---
"export"
(fn [cycles-arg]
  (let [n-cycles (or (leval/unwrap cycles-arg) 4)
        state    @audio/scheduler-state
        tracks   (:tracks state)
        cycle-dur (:cycle-dur state)
        duration (* n-cycles cycle-dur)
        sr       44100
        n-frames (int (* sr duration))
        offline  (js/OfflineAudioContext. 2 n-frames sr)]
    (if (empty? tracks)
      "Error: no active tracks to export"
      (do
        ;; Schedule all events for N cycles into the offline context
        (doseq [c (range n-cycles)]
          (let [sp {:start [(int c) 1] :end [(int (inc c)) 1]}]
            (doseq [[track-name pattern] tracks]
              (when pattern
                (let [evs (core/query pattern sp)]
                  (doseq [ev evs]
                    (let [part-start (core/rat->float (:start (:part ev)))
                          t          (* (- part-start c) cycle-dur)
                          abs-t      (+ (* c cycle-dur) t)]
                      (when (>= abs-t 0)
                        (audio/play-event offline abs-t (:value ev))))))))))
        ;; Render and encode
        (-> (.startRendering offline)
            (.then (fn [buffer]
                     (let [ch-l   (.getChannelData buffer 0)
                           ch-r   (.getChannelData buffer 1)
                           n      (.-length ch-l)
                           ;; WAV header + interleaved 16-bit PCM
                           bps    16
                           n-ch   2
                           data-bytes (* n n-ch (/ bps 8))
                           buf    (js/ArrayBuffer. (+ 44 data-bytes))
                           view   (js/DataView. buf)]
                       ;; RIFF header
                       (doto view
                         (.setUint8  0 82)  (.setUint8  1 73)   ; R I
                         (.setUint8  2 70)  (.setUint8  3 70)   ; F F
                         (.setUint32 4 (+ 36 data-bytes) true)
                         (.setUint8  8 87)  (.setUint8  9 65)   ; W A
                         (.setUint8 10 86)  (.setUint8 11 69)   ; V E
                         ;; fmt chunk
                         (.setUint8 12 102) (.setUint8 13 109)  ; f m
                         (.setUint8 14 116) (.setUint8 15 32)   ; t _
                         (.setUint32 16 16 true)                ; chunk size
                         (.setUint16 20 1 true)                 ; PCM
                         (.setUint16 22 n-ch true)
                         (.setUint32 24 sr true)
                         (.setUint32 28 (* sr n-ch (/ bps 8)) true)
                         (.setUint16 32 (* n-ch (/ bps 8)) true)
                         (.setUint16 34 bps true)
                         ;; data chunk
                         (.setUint8 36 100) (.setUint8 37 97)   ; d a
                         (.setUint8 38 116) (.setUint8 39 97)   ; t a
                         (.setUint32 40 data-bytes true))
                       ;; Interleaved samples
                       (dotimes [i n]
                         (let [l (Math/max -1 (Math/min 1 (aget ch-l i)))
                               r (Math/max -1 (Math/min 1 (aget ch-r i)))
                               offset (+ 44 (* i 4))]
                           (.setInt16 view offset
                                      (int (* l 32767)) true)
                           (.setInt16 view (+ offset 2)
                                      (int (* r 32767)) true)))
                       ;; Download
                       (let [blob (js/Blob. #js [buf] #js {:type "audio/wav"})
                             url  (.createObjectURL js/URL blob)
                             a    (.createElement js/document "a")]
                         (set! (.-href a) url)
                         (set! (.-download a) (str "repulse-" n-cycles "cycles.wav"))
                         (.click a)
                         (.revokeObjectURL js/URL url)))))
            (.catch (fn [e]
                      (js/console.error "[REPuLse] export failed:" e))))
        (str "exporting " n-cycles " cycles…")))))
```

---

### 5. `packages/lisp/src/repulse/lisp/eval.cljs` — full diff

Add to the `:require` block:

```clojure
[repulse.lisp.mini :as mini]
```

Add to `make-env`, after the `"comp"` entry:

```clojure
;; Mini-notation
"~"      (fn [s] (mini/parse (unwrap s)))
```

---

### 6. `app/src/repulse/lisp-lang/completions.js` — add entries

Add after the `"comp"` entry in the BUILTINS array:

```javascript
// --- Mini-notation ---
{ label: "~",           type: "function", detail: "(~ \"bd sd [hh hh] bd\") — parse mini-notation string into pattern" },
// --- Sharing ---
{ label: "load-gist",   type: "function", detail: "(load-gist url) — fetch a Gist and load into editor" },
{ label: "export",       type: "function", detail: "(export n) — render n cycles to downloadable WAV file" },
```

---

### 7. `app/src/repulse/lisp-lang/repulse-lisp.grammar` — add to BuiltinName

Add to the existing `BuiltinName` token alternatives:

```
"~" | "load-gist" | "export" |
```

The `~` token needs to be recognized by the tokeniser. Since `~` is not in `identStart`
or `identChar`, it must be added there **or** listed as a literal in BuiltinName.

Update `identStart` and `identChar` to include `~`:

```
identStart { @asciiLetter | "-" | "_" | "!" | "?" | "+" | "*" | "/" | "=" | "<" | ">" | "." | "'" | "~" }
identChar  { @asciiLetter | @digit | "-" | "_" | "!" | "?" | "+" | "*" | "/" | "=" | "<" | ">" | "." | "'" | "~" }
```

After editing the grammar, **run `npm run gen:grammar`** to regenerate `parser.js`.

---

### 8. `packages/core/src/repulse/test_runner.cljs` — add mini-test

```clojure
(ns repulse.test-runner
  (:require [cljs.test :as test]
            [repulse.core-test]
            [repulse.theory-test]
            [repulse.params-test]
            [repulse.lisp.mini-test]))

(defn main []
  (test/run-tests 'repulse.core-test 'repulse.theory-test
                  'repulse.params-test 'repulse.lisp.mini-test))
```

**Note:** The mini-test namespace lives in the `lisp` package but needs to be accessible
to the test runner. Check `shadow-cljs.edn` to ensure the test build includes both
`packages/core/src` and `packages/lisp/src` (and `packages/lisp/test`) on its source paths.
If the test runner build does not include the lisp package paths, add them or create a
separate test build for the lisp package. Alternatively, move the mini-test into
`packages/core/test/repulse/` and only import `mini` — since `mini.cljs` only depends on
`repulse.core`, it can be tested from either package.

---

### 9. `shadow-cljs.edn` — verify test build paths

Ensure the `:test` build includes both packages:

```edn
:test {:target :node-test
       :output-to "out/test.js"
       :ns-regexp ".*-test$"
       :source-paths ["packages/core/src"
                      "packages/core/test"
                      "packages/lisp/src"
                      "packages/lisp/test"]}
```

If the existing config already includes both, no change is needed.

---

## Files to change

| File | Change |
|---|---|
| `packages/lisp/src/repulse/lisp/mini.cljs` | **New** — tokeniser, parser, compiler for mini-notation |
| `packages/lisp/test/repulse/lisp/mini_test.cljs` | **New** — unit tests for mini-notation |
| `packages/lisp/src/repulse/lisp/eval.cljs` | Require `mini`; add `~` binding to `make-env` |
| `app/src/repulse/app.cljs` | Add `load-gist` and `export` bindings in `ensure-env!` |
| `app/src/repulse/lisp-lang/completions.js` | Add 3 entries (`~`, `load-gist`, `export`) |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add `~`, `load-gist`, `export` to BuiltinName; add `~` to identStart/identChar |
| `packages/core/src/repulse/test_runner.cljs` | Require `mini-test` |
| `shadow-cljs.edn` | Verify lisp package paths in test build (may be no-op) |
| `docs/USAGE.md` | New "Mini-notation", "Gist import", and "WAV export" sections |
| `README.md` | Add `~`, `load-gist`, `export` to language reference table |
| `CLAUDE.md` | Mark Phase K as delivered when done |

No changes to `packages/core/src/repulse/core.cljs`, `packages/audio/`, or
`app/src/repulse/audio.cljs`.

---

## Mini-notation reference

| Syntax | Example | Meaning |
|---|---|---|
| space-separated | `bd sd hh` | sequence of 3 equal slots |
| `[…]` | `bd [sd hh]` | subdivision — contents share one parent slot |
| `*N` | `hh*4` | repeat N times (applies `fast N`) |
| `<…>` | `<bd sd cp>` | alternation — cycle N picks element `mod N` |
| `~` or `_` | `bd ~ sd` | rest / silence (`:_`) |
| `?` | `bd?` | 50% probability of playing |
| `:N` | `bd:2` | sample index (produces `{:bank :bd :n 2}`) |
| `@N` | `bd@3 sd` | weight — bd takes 3 units, sd takes 1 |
| bare number | `440` | numeric value (Hz frequency) |
| note name | `c4 eb3` | keyword (`:c4`, `:eb3`) for note resolution |

**Nesting:** brackets can be nested arbitrarily: `"bd [sd [hh oh]] cp"`.

**Suffixes bind tight:** `hh*4?` means "repeat hh 4 times, then degrade the group."

---

## Definition of done

### Mini-notation parser

- [ ] `(~ "bd sd hh")` produces a pattern with 3 events per cycle: `:bd`, `:sd`, `:hh`
- [ ] `(~ "bd [sd hh]")` produces 3 events: `:bd` takes half the cycle, `:sd` and `:hh` split the other half
- [ ] `(~ "bd [sd [hh oh]]")` handles nested subdivision correctly
- [ ] `(~ "hh*4")` produces 4 `:hh` events per cycle
- [ ] `(~ "bd hh*2 sd")` produces 4 events: `:bd`, `:hh`, `:hh`, `:sd`
- [ ] `(~ "<bd sd>")` plays `:bd` on even cycles and `:sd` on odd cycles
- [ ] `(~ "<bd sd cp>")` rotates through 3 elements across cycles
- [ ] `(~ "bd ~ sd")` produces `:bd`, `:_`, `:sd`
- [ ] `(~ "bd _ sd")` produces `:bd`, `:_`, `:sd`
- [ ] `(~ "bd?")` produces `:bd` approximately 50% of the time (nondeterministic — test with many runs)
- [ ] `(~ "bd:2")` produces `{:bank :bd :n 2}`
- [ ] `(~ "bd@3 sd")` gives bd 3/4 of the cycle and sd 1/4
- [ ] `(~ "440")` produces the number `440`
- [ ] `(~ "c4 e4 g4")` produces `:c4`, `:e4`, `:g4`
- [ ] `(~ "")` returns an empty pattern (no events)
- [ ] `(~ "  bd   sd  ")` handles extra whitespace
- [ ] `(~ "[bd sd]*2")` repeats the group: 4 events total

### Composition with Lisp

- [ ] `(fast 2 (~ "bd sd"))` produces 4 events
- [ ] `(stack (~ "bd _ bd _") (~ "_ sd _ sd"))` stacks two patterns
- [ ] `(->> (~ "c4 e4 g4") (amp 0.6))` applies amplitude to mini-notation pattern
- [ ] `(every 4 rev (~ "bd sd hh cp"))` works correctly
- [ ] `(def kick (~ "bd _ bd _"))` followed by `(play :k kick)` works
- [ ] `(play :drums (~ "bd sd [hh hh] sd"))` starts a named track

### Gist import

- [ ] `(load-gist "https://gist.github.com/user/hexid")` fetches and loads into editor
- [ ] `(load-gist "https://gist.githubusercontent.com/user/hexid/raw/file.clj")` fetches raw directly
- [ ] Network errors show a clear message in the footer
- [ ] The loaded code is auto-evaluated after insertion

### WAV export

- [ ] `(export 4)` renders 4 cycles and triggers a WAV download
- [ ] The WAV file is valid — plays in any audio player
- [ ] `(export 1)` with a simple `(seq :bd :sd)` produces audible kick and snare
- [ ] Export works with multi-track patterns (all tracks are mixed)
- [ ] `(export)` or `(export 4)` when no tracks are playing returns an error message

### UI

- [ ] `~`, `load-gist`, `export` appear in autocomplete
- [ ] All three tokens receive syntax highlighting as built-in names
- [ ] `npm run gen:grammar` has been run after grammar changes

### Tests

- [ ] All mini-notation unit tests pass (`npm run test:core`)
- [ ] Existing core-test, theory-test, and params-test still pass
