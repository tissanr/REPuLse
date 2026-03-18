# Phase I — Pattern Combinators & Stochastic Transforms

## Goal

Add eight new pattern combinators that unlock rhythmic algorithms, time-shifting,
stochastic variation, and spatial layering. These complete the combinator toolkit,
making REPuLse competitive with Tidal Cycles' core vocabulary.

```lisp
;; Before — limited to seq/stack/fast/slow/rev/every:
(fast 2 (seq :bd :sd :hh :oh))

;; After — Euclidean rhythms:
(euclidean 5 8 :bd)                        ; Björklund: 5 onsets in 8 steps
(euclidean 3 8 :sd 2)                      ; with rotation

;; After — concatenation across cycles:
(cat (seq :bd :sd) (seq :hh :oh :hh :oh))  ; pattern A for 1 cycle, then B, loop

;; After — time shifting:
(late 0.25 (seq :bd :sd :bd :sd))          ; delay all events by 1/4 cycle
(early 0.125 hihat)                        ; advance by 1/8 cycle

;; After — stochastic transforms:
(sometimes rev (seq :c4 :e4 :g4 :c5))     ; reverse ~50% of cycles
(often (fast 2) (seq :hh :oh))             ; speed up ~75% of cycles
(rarely rev melody)                        ; reverse ~25% of cycles
(degrade (seq :hh :hh :hh :hh))           ; drop ~50% of events
(degrade-by 0.3 (fast 4 (seq :hh :oh)))   ; drop 30% of events

;; After — random selection:
(choose [:bd :sd :hh :oh])                 ; pick one per cycle
(wchoose [[0.5 :bd] [0.3 :sd] [0.2 :hh]]) ; weighted random

;; After — spatial layering:
(jux rev (seq :c4 :e4 :g4))               ; original left + reversed right
(jux-by 0.5 (fast 2) hihat)               ; partial stereo width

;; After — time-offset layering:
(off 0.125 (fast 2) (seq :c4 :e4 :g4))   ; original + shifted+transformed copy

;; Composition with existing combinators:
(->> (euclidean 5 8 :bd)
     (sometimes rev)
     (jux (fast 2))
     (amp 0.8))
```

---

## Background

### Current combinator vocabulary

The pattern algebra in `packages/core/src/repulse/core.cljs` provides:

| Function    | Purpose                                   |
|-------------|-------------------------------------------|
| `pure`      | Constant value over full cycle            |
| `seq*`      | Subdivide one cycle into equal-length steps |
| `stack*`    | Layer patterns in parallel                |
| `fmap`      | Transform event values                    |
| `combine`   | Applicative liftA2 — pair overlapping events |
| `fast`      | Speed up (compress time)                  |
| `slow`      | Slow down (stretch time)                  |
| `rev`       | Reverse event order within each cycle     |
| `every`     | Apply transform every n cycles            |
| `arrange*`  | Section-based song arrangement            |

Missing: there is no Euclidean rhythm generator, no time-shifting (`late`/`early`),
no stochastic functions (`sometimes`, `degrade`, `choose`), no stereo spatial
layering (`jux`), and no time-offset layering (`off`).

### Rational time system

All time is expressed as `[numerator denominator]` rational vectors. Key functions:
`rat`, `rat+`, `rat-`, `rat*`, `rat-div`, `rat<`, `rat->float`, `int->rat`.

A `span` is `{:start [n d] :end [n d]}`. An `event` is `{:value v :whole span :part span}`.
Optionally `:source` for editor highlighting.

A `pattern` is `{:query fn}` where `fn` takes a span and returns events.

### Evaluator bindings

`make-env` in `packages/lisp/src/repulse/lisp/eval.cljs` maps Lisp names to
ClojureScript functions. All arguments must be `unwrap`-ed (to strip `SourcedVal`
wrappers from the reader).

### Params namespace

`packages/core/src/repulse/params.cljs` provides `amp`, `attack`, `decay`, `release`,
`pan`. The `pan` function is needed by `jux`.

---

## Design

### 1. All functions are pure

Every new combinator is a pure function of time. No audio, no DOM, no side effects.
Stochastic functions use deterministic hashes seeded from time positions, so patterns
are reproducible across evaluations and shareable via URL.

### 2. Rational time for all time-shifting

`late` and `early` shift event positions using rational arithmetic, never floats.
The offset parameter comes in as a float from user code but is converted to a rational
via `(rat (int (* amount 1000)) 1000)` to maintain exact arithmetic within the engine.

### 3. Deterministic randomness

Two different seeds:

- **Cycle-based** (for `sometimes`, `choose`): hash the integer cycle number.
  Formula: `(mod (+ (* cycle 48271) 12345) 100)`. Same cycle always gets the same
  decision. Different cycles get sufficiently different values.

- **Event-based** (for `degrade`): hash the event's `:whole` start position.
  Formula: `(mod (+ (* (first start) 48271) (* (second start) 22543)) 100)` where
  `start` is `(:start (:whole event))`. Each event in the same cycle gets a different
  random value.

### 4. Euclidean rhythms via Björklund

The Björklund algorithm distributes k onsets across n steps as evenly as possible.
It works by iteratively merging remainder sequences (like Euclid's GCD algorithm on
sequence groups). The result is a vector of booleans, mapped to values and `:_` rests,
then fed to `seq*`.

### 5. `jux` uses `params/pan`

`jux` stacks two copies: the original panned left, and the transformed copy panned
right. It imports `repulse.params` and uses `params/pan`. The `jux-by` variant
accepts a stereo width parameter (0.0 = mono, 1.0 = full stereo).

---

## Implementation

### 1. `packages/core/src/repulse/core.cljs` — add 8 combinators

Add after the existing `arrange*` function. Requires adding `[repulse.params :as params]`
to the namespace declaration.

#### 1a. `euclidean` — Björklund algorithm

```clojure
(defn euclidean
  "Björklund's algorithm: distribute k onsets across n steps as evenly as possible.
   Returns a seq pattern of val and :_ rests.
   (euclidean 5 8 :bd)     — 5 hits in 8 steps
   (euclidean 5 8 :bd 2)   — rotated 2 steps"
  ([k n val] (euclidean k n val 0))
  ([k n val rotation]
   (let [;; Björklund algorithm — iteratively distribute remainders
         result
         (loop [groups  (into (vec (repeat k [true]))
                              (repeat (- n k) [false]))]
           (let [cnt-a (count (filter #(= (first %) (first (first groups))) groups))
                 cnt-b (- (count groups) cnt-a)]
             (if (or (<= cnt-b 1) (= (count groups) n))
               (vec (mapcat identity groups))
               (let [take-n (min cnt-a cnt-b)
                     head   (subvec groups 0 take-n)
                     mid    (subvec groups take-n cnt-a)
                     tail   (subvec groups cnt-a)]
                 (recur (into (mapv (fn [a b] (into a b))
                                    head (subvec tail 0 take-n))
                              (into mid (subvec tail take-n))))))))
         ;; Apply rotation
         rotated (let [r (mod rotation n)
                       v (vec result)]
                   (into (subvec v r) (subvec v 0 r)))
         ;; Map booleans to values
         values (mapv #(if % val :_) rotated)]
     (seq* values))))
```

#### 1b. `cat` — concatenation across cycles

```clojure
(defn cat*
  "Concatenate patterns: each plays for one full cycle, then the whole sequence loops.
   Unlike seq* (which subdivides one cycle), cat* gives each pattern its own cycle.
   (cat* [p1 p2 p3]) — 3-cycle loop: p1 for cycle 0, p2 for cycle 1, p3 for cycle 2."
  [pats]
  (let [n (count pats)]
    (if (zero? n)
      (pattern (fn [_] []))
      (pattern
       (fn [{:keys [start end] :as sp}]
         (let [cycle  (int (Math/floor (rat->float start)))
               idx    (mod cycle n)
               pat    (nth pats idx)]
           (query pat sp)))))))
```

#### 1c. `late` and `early` — time shifting

```clojure
(defn late
  "Shift all events forward in time by amount (fraction of a cycle).
   Queries the pattern at (start - offset, end - offset), then shifts events
   back by +offset. Preserves :source for editor highlighting.
   (late 0.25 pat) — delay by 1/4 cycle"
  [amount pat]
  (let [off (if (vector? amount) amount (rat (int (* amount 1000)) 1000))]
    (pattern
     (fn [{:keys [start end]}]
       (let [q-start (rat- start off)
             q-end   (rat- end off)
             evs     (query pat (span q-start q-end))]
         (map (fn [e]
                (-> e
                    (update :whole #(span (rat+ (:start %) off)
                                          (rat+ (:end %) off)))
                    (update :part  #(span (rat+ (:start %) off)
                                          (rat+ (:end %) off)))))
              evs))))))

(defn early
  "Shift all events backward in time by amount (fraction of a cycle).
   Equivalent to (late (- amount) pat).
   (early 0.25 pat) — advance by 1/4 cycle"
  [amount pat]
  (let [neg-off (if (vector? amount)
                  [(- (first amount)) (second amount)]
                  (rat (- (int (* amount 1000))) 1000))]
    (late neg-off pat)))
```

#### 1d. `sometimes-by`, `sometimes`, `often`, `rarely` — stochastic transforms

```clojure
(defn- cycle-hash
  "Deterministic hash of a cycle number. Returns 0–99."
  [cycle]
  (mod (+ (* (Math/abs cycle) 48271) 12345) 100))

(defn sometimes-by
  "Apply transform f to pat on cycles where (cycle-hash cycle) < (prob * 100).
   prob is 0.0–1.0. Deterministic: same cycle number always makes the same choice.
   (sometimes-by 0.5 rev pat) — reverse ~50% of cycles"
  [prob f pat]
  (let [threshold (int (* prob 100))]
    (pattern
     (fn [sp]
       (let [cycle (int (Math/floor (rat->float (:start sp))))]
         (if (< (cycle-hash cycle) threshold)
           (query (f pat) sp)
           (query pat sp)))))))

(defn sometimes
  "Apply transform on ~50% of cycles.
   (sometimes rev pat)"
  [f pat]
  (sometimes-by 0.5 f pat))

(defn often
  "Apply transform on ~75% of cycles.
   (often (fast 2) pat)"
  [f pat]
  (sometimes-by 0.75 f pat))

(defn rarely
  "Apply transform on ~25% of cycles.
   (rarely rev pat)"
  [f pat]
  (sometimes-by 0.25 f pat))
```

#### 1e. `degrade-by` and `degrade` — per-event dropout

```clojure
(defn- event-hash
  "Deterministic hash of an event's start position. Returns 0–99.
   Uses the :whole start [numerator denominator] to seed."
  [event]
  (let [[n d] (:start (:whole event))]
    (mod (+ (* (Math/abs n) 48271) (* (Math/abs d) 22543) 9137) 100)))

(defn degrade-by
  "Randomly drop events from pat with probability prob (0.0–1.0).
   Uses deterministic hash of each event's time position.
   (degrade-by 0.3 pat) — drop ~30% of events"
  [prob pat]
  (let [threshold (int (* prob 100))]
    (pattern
     (fn [sp]
       (filter #(>= (event-hash %) threshold) (query pat sp))))))

(defn degrade
  "Drop ~50% of events randomly. Shorthand for (degrade-by 0.5 pat).
   (degrade (fast 4 (seq :hh :oh :hh :oh)))"
  [pat]
  (degrade-by 0.5 pat))
```

#### 1f. `choose` and `wchoose` — random selection per cycle

```clojure
(defn choose
  "Pick one value from xs per cycle (deterministic based on cycle number).
   Returns a pattern that produces one event per cycle.
   (choose [:bd :sd :hh :oh])"
  [xs]
  (let [n (count xs)]
    (pattern
     (fn [{:keys [start end] :as sp}]
       (let [start-c (int (Math/floor (rat->float start)))
             end-c   (int (Math/ceil  (rat->float end)))]
         (for [c (range start-c end-c)
               :let [idx   (mod (cycle-hash c) n)
                     whole (cycle-span c)
                     part  (span-intersect whole (span start end))]
               :when part]
           (event (nth xs idx) whole part)))))))

(defn wchoose
  "Weighted random choice per cycle. Takes a vector of [weight value] pairs.
   Weights are relative (don't need to sum to 1.0).
   (wchoose [[0.5 :bd] [0.3 :sd] [0.2 :hh]])"
  [pairs]
  (let [total  (reduce + (map first pairs))
        ;; Normalize weights to cumulative thresholds 0–99
        cumulative (reductions + (map #(* 100 (/ (first %) total)) pairs))
        values     (mapv second pairs)]
    (pattern
     (fn [{:keys [start end] :as sp}]
       (let [start-c (int (Math/floor (rat->float start)))
             end-c   (int (Math/ceil  (rat->float end)))]
         (for [c (range start-c end-c)
               :let [h     (cycle-hash c)
                     idx   (or (first (keep-indexed
                                        (fn [i thresh]
                                          (when (< h thresh) i))
                                        cumulative))
                               (dec (count values)))
                     whole (cycle-span c)
                     part  (span-intersect whole (span start end))]
               :when part]
           (event (nth values idx) whole part)))))))
```

#### 1g. `jux` and `jux-by` — stereo spatial layering

This function requires `repulse.params`. Add the require to the `core.cljs` namespace:

```clojure
(ns repulse.core
  (:require [repulse.params :as params]))
```

**IMPORTANT — circular dependency:** `repulse.params` already requires `repulse.core`.
Adding a reverse require would create a circular dependency. Instead, `jux` and `jux-by`
must go in a separate namespace or `params` must be required lazily.

**Solution:** Place `jux` and `jux-by` in `packages/core/src/repulse/params.cljs`
(which already requires `repulse.core`). This is the natural home since they use `pan`.

Add to the **bottom** of `packages/core/src/repulse/params.cljs`:

```clojure
(defn jux
  "Stack the original pattern panned left with (f pat) panned right.
   Creates stereo width by splitting original and transformed copies.
   (jux rev (seq :c4 :e4 :g4))  — original left, reversed right"
  [f pat]
  (core/stack* [(pan -1 pat) (pan 1 (f pat))]))

(defn jux-by
  "Like jux but with adjustable stereo width.
   width 0.0 = both copies centre (mono), 1.0 = full left/right.
   (jux-by 0.5 rev pat) — half stereo width"
  [width f pat]
  (core/stack* [(pan (- width) pat) (pan width (f pat))]))
```

#### 1h. `off` — time-offset layering

Place `off` in `packages/core/src/repulse/core.cljs` (no circular dependency issue):

```clojure
(defn off
  "Layer the original pattern with a time-shifted, transformed copy.
   (off 0.125 (fast 2) pat) — original + 1/8-cycle-shifted double-speed copy"
  [amount f pat]
  (stack* [pat (late amount (f pat))]))
```

---

### 2. `packages/core/src/repulse/core.cljs` — full changeset summary

Add these functions after `arrange*`, in this order:

1. `euclidean` (2 arities)
2. `cat*`
3. `late`
4. `early`
5. `cycle-hash` (private)
6. `sometimes-by`
7. `sometimes`
8. `often`
9. `rarely`
10. `event-hash` (private)
11. `degrade-by`
12. `degrade`
13. `choose`
14. `wchoose`
15. `off`

And add to `packages/core/src/repulse/params.cljs`:

16. `jux`
17. `jux-by`

---

### 3. `packages/core/src/repulse/core_test.cljs` — add unit tests

Add after the existing `arrange-test`:

```clojure
;;; ── Phase I: Pattern Combinators ──────────────────────────────────

(deftest euclidean-5-8
  (testing "euclidean 5 8 distributes 5 onsets across 8 steps"
    (let [evs (c/query (c/euclidean 5 8 :bd) (c/cycle-span 0))
          vals (mapv :value evs)]
      (is (= 8 (count vals)))
      (is (= 5 (count (filter #(= :bd %) vals))))
      (is (= 3 (count (filter #(= :_ %) vals)))))))

(deftest euclidean-3-8-rotation
  (testing "euclidean with rotation shifts the pattern"
    (let [evs-no-rot (mapv :value (c/query (c/euclidean 3 8 :x) (c/cycle-span 0)))
          evs-rot    (mapv :value (c/query (c/euclidean 3 8 :x 2) (c/cycle-span 0)))]
      ;; Rotation should produce a different arrangement but same counts
      (is (= 3 (count (filter #(= :x %) evs-rot))))
      (is (= 5 (count (filter #(= :_ %) evs-rot))))
      ;; Rotated pattern should differ from unrotated (for k=3, n=8, rot=2)
      (is (not= evs-no-rot evs-rot)))))

(deftest euclidean-4-4
  (testing "euclidean k=n produces all onsets"
    (let [vals (mapv :value (c/query (c/euclidean 4 4 :bd) (c/cycle-span 0)))]
      (is (= [:bd :bd :bd :bd] vals)))))

(deftest cat-basic
  (testing "cat* plays each pattern for one cycle in sequence"
    (let [a   (c/pure :a)
          b   (c/pure :b)
          c-p (c/pure :c)
          pat (c/cat* [a b c-p])]
      ;; Cycle 0: a
      (is (= [:a] (mapv :value (c/query pat (c/cycle-span 0)))))
      ;; Cycle 1: b
      (is (= [:b] (mapv :value (c/query pat (c/cycle-span 1)))))
      ;; Cycle 2: c
      (is (= [:c] (mapv :value (c/query pat (c/cycle-span 2)))))
      ;; Cycle 3: loops back to a
      (is (= [:a] (mapv :value (c/query pat (c/cycle-span 3))))))))

(deftest cat-with-seq
  (testing "cat* with seq patterns preserves internal structure"
    (let [pat (c/cat* [(c/seq* [:bd :sd]) (c/pure :hh)])]
      (is (= [:bd :sd] (mapv :value (c/query pat (c/cycle-span 0)))))
      (is (= [:hh]     (mapv :value (c/query pat (c/cycle-span 1))))))))

(deftest late-shifts-forward
  (testing "late shifts events forward in time"
    (let [pat (c/late 0.5 (c/pure :bd))
          evs (c/query pat (c/span [0 1] [2 1]))]
      ;; Events should exist but be shifted by 0.5
      (is (pos? (count evs)))
      (let [e (first evs)
            s (:start (:whole e))]
        ;; The whole start should be shifted by 0.5 from cycle boundary
        (is (= [1 2] s))))))

(deftest early-shifts-backward
  (testing "early is the inverse of late"
    (let [pat (c/early 0.25 (c/pure :bd))
          evs (c/query pat (c/span [0 1] [2 1]))]
      (is (pos? (count evs)))
      (let [e (first evs)
            s (:start (:whole e))]
        (is (= [-1 4] s))))))

(deftest sometimes-deterministic
  (testing "sometimes produces consistent results for the same cycle"
    (let [base (c/pure :bd)
          pat  (c/sometimes c/rev base)
          evs1 (c/query pat (c/cycle-span 5))
          evs2 (c/query pat (c/cycle-span 5))]
      ;; Same cycle should always produce the same result
      (is (= (mapv :value evs1) (mapv :value evs2))))))

(deftest sometimes-by-zero-never-applies
  (testing "sometimes-by 0.0 never applies the transform"
    (let [pat (c/sometimes-by 0.0 (fn [p] (c/fmap (constantly :WRONG) p))
                              (c/pure :bd))]
      (doseq [cy (range 20)]
        (is (= [:bd] (mapv :value (c/query pat (c/cycle-span cy)))))))))

(deftest sometimes-by-one-always-applies
  (testing "sometimes-by 1.0 always applies the transform"
    (let [pat (c/sometimes-by 1.0 (fn [p] (c/fmap (constantly :YES) p))
                              (c/pure :bd))]
      (doseq [cy (range 20)]
        (is (= [:YES] (mapv :value (c/query pat (c/cycle-span cy)))))))))

(deftest degrade-drops-some-events
  (testing "degrade drops approximately half the events"
    (let [pat (c/degrade (c/seq* [:a :b :c :d :e :f :g :h]))
          evs (c/query pat (c/cycle-span 0))]
      ;; Should have fewer than 8 events (probabilistic, but deterministic)
      (is (< (count evs) 8))
      (is (pos? (count evs))))))

(deftest degrade-by-zero-keeps-all
  (testing "degrade-by 0.0 keeps all events"
    (let [pat (c/degrade-by 0.0 (c/seq* [:a :b :c :d]))
          evs (c/query pat (c/cycle-span 0))]
      (is (= 4 (count evs))))))

(deftest degrade-deterministic
  (testing "degrade produces same results for same query"
    (let [pat  (c/degrade (c/seq* [:a :b :c :d :e :f :g :h]))
          evs1 (c/query pat (c/cycle-span 0))
          evs2 (c/query pat (c/cycle-span 0))]
      (is (= (mapv :value evs1) (mapv :value evs2))))))

(deftest choose-picks-one-per-cycle
  (testing "choose returns exactly one event per cycle"
    (let [pat (c/choose [:a :b :c :d])]
      (doseq [cy (range 10)]
        (let [evs (c/query pat (c/cycle-span cy))]
          (is (= 1 (count evs)))
          (is (contains? #{:a :b :c :d} (:value (first evs)))))))))

(deftest choose-deterministic
  (testing "choose is deterministic for the same cycle"
    (let [pat (c/choose [:a :b :c :d])]
      (doseq [cy (range 10)]
        (is (= (mapv :value (c/query pat (c/cycle-span cy)))
               (mapv :value (c/query pat (c/cycle-span cy)))))))))

(deftest wchoose-picks-one-per-cycle
  (testing "wchoose returns exactly one event per cycle"
    (let [pat (c/wchoose [[0.5 :bd] [0.3 :sd] [0.2 :hh]])]
      (doseq [cy (range 10)]
        (let [evs (c/query pat (c/cycle-span cy))]
          (is (= 1 (count evs)))
          (is (contains? #{:bd :sd :hh} (:value (first evs)))))))))

(deftest off-layers-original-and-shifted
  (testing "off produces events from both original and transformed copy"
    (let [pat (c/off 0.5 c/rev (c/seq* [:a :b]))
          evs (c/query pat (c/span [0 1] [2 1]))]
      ;; Should have events from both the original and the shifted copy
      (is (> (count evs) 2)))))
```

---

### 4. `packages/core/src/repulse/params_test.cljs` — add jux tests

Add after the existing `amp-preserves-source` test:

```clojure
;;; ── jux ────────────────────────────────────────────────────────────

(deftest jux-stacks-panned
  ;; jux should produce events panned left and right
  (let [evs (core/query (params/jux core/rev (core/seq* [:a :b])) one-cycle)
        pans (set (map #(get (:value %) :pan) evs))]
    ;; Should have both -1 and 1 pan values
    (is (contains? pans -1))
    (is (contains? pans 1))))

(deftest jux-by-half-width
  (let [evs (core/query (params/jux-by 0.5 core/rev (core/seq* [:a :b])) one-cycle)
        pans (set (map #(get (:value %) :pan) evs))]
    (is (contains? pans -0.5))
    (is (contains? pans 0.5))))
```

---

### 5. `packages/core/src/repulse/test_runner.cljs` — no changes needed

The test runner already requires `repulse.core-test` and `repulse.params-test`. All new
core tests go in `core_test.cljs` and jux tests go in `params_test.cljs`, so no
additional requires are needed.

---

### 6. `packages/lisp/src/repulse/lisp/eval.cljs` — add bindings

Add these entries to `make-env`, after the existing `"pan"` binding and before `"comp"`:

```clojure
     ;; Pattern combinators — Phase I
     "euclidean" (fn
                   ([k n v]     (core/euclidean (unwrap k) (unwrap n) (unwrap v)))
                   ([k n v r]   (core/euclidean (unwrap k) (unwrap n) (unwrap v) (unwrap r))))
     "cat"       (fn [& ps]    (core/cat* (mapv unwrap ps)))
     "late"      (fn [a p]     (core/late (unwrap a) (unwrap p)))
     "early"     (fn [a p]     (core/early (unwrap a) (unwrap p)))
     "sometimes" (fn [f p]     (core/sometimes (unwrap f) (unwrap p)))
     "often"     (fn [f p]     (core/often (unwrap f) (unwrap p)))
     "rarely"    (fn [f p]     (core/rarely (unwrap f) (unwrap p)))
     "sometimes-by" (fn [prob f p] (core/sometimes-by (unwrap prob) (unwrap f) (unwrap p)))
     "degrade"   (fn [p]       (core/degrade (unwrap p)))
     "degrade-by" (fn [prob p] (core/degrade-by (unwrap prob) (unwrap p)))
     "choose"    (fn [xs]      (core/choose (mapv unwrap (unwrap xs))))
     "wchoose"   (fn [pairs]   (core/wchoose (mapv (fn [[w v]] [(unwrap w) (unwrap v)])
                                                    (unwrap pairs))))
     "jux"       (fn [f p]     (params/jux (unwrap f) (unwrap p)))
     "jux-by"    (fn [w f p]   (params/jux-by (unwrap w) (unwrap f) (unwrap p)))
     "off"       (fn [a f p]   (core/off (unwrap a) (unwrap f) (unwrap p)))
```

**Note on `sometimes`/`often`/`rarely`/`jux` — the `f` argument:** These functions
receive a transform function from the Lisp evaluator (e.g., `rev` resolves to the
ClojureScript `core/rev` function, or `(fast 2)` resolves to a curried function).
The `f` argument is already a ClojureScript function, so it does not need `unwrap`
beyond stripping a possible SourcedVal. However, since `unwrap` on a function is a
no-op (it returns the function unchanged), it is safe to apply `unwrap` uniformly.

---

### 7. `app/src/repulse/lisp-lang/repulse-lisp.grammar` — add to BuiltinName

Add the new names to the `BuiltinName` token list. Insert after the existing
`"pan"` line:

```
    "euclidean" | "cat" | "late" | "early" |
    "sometimes" | "often" | "rarely" | "sometimes-by" |
    "degrade" | "degrade-by" |
    "choose" | "wchoose" |
    "jux" | "jux-by" | "off" |
```

The full `BuiltinName` block should read:

```
  BuiltinName {
    "seq" | "stack" | "pure" | "fast" | "slow" | "rev" | "every" | "fmap" |
    "scale" | "chord" | "transpose" |
    "->>" | "amp" | "attack" | "decay" | "release" | "pan" |
    "euclidean" | "cat" | "late" | "early" |
    "sometimes" | "often" | "rarely" | "sometimes-by" |
    "degrade" | "degrade-by" |
    "choose" | "wchoose" |
    "jux" | "jux-by" | "off" |
    "def" | "let" | "fn" | "lambda" | "if" | "do" |
    "bpm" | "stop" | "fx" | "load-plugin" | "arrange" | "play-scenes" | "sound" |
    "get" | "assoc" | "merge" | "not" | "and" | "or" | "comp" |
    "play" | "mute!" | "unmute!" | "solo!" | "clear!" | "tracks" | "tap!" | "midi-sync!" |
    "upd"
  }
```

**After editing the grammar, run `npm run gen:grammar`** to regenerate `parser.js`.
Commit both `repulse-lisp.grammar` and the regenerated `parser.js`.

---

### 8. `app/src/repulse/lisp-lang/completions.js` — add completion entries

Add after the existing `pan` entry and before `comp`:

```javascript
  // --- Pattern combinators ---
  { label: "euclidean",    type: "function", detail: "(euclidean k n val) — Björklund algorithm: k onsets in n steps; optional rotation (euclidean k n val rot)" },
  { label: "cat",          type: "function", detail: "(cat pat …) — play patterns one per cycle, then loop the sequence" },
  { label: "late",         type: "function", detail: "(late amount pat) — shift events forward by fraction of a cycle" },
  { label: "early",        type: "function", detail: "(early amount pat) — shift events backward by fraction of a cycle" },
  { label: "sometimes",    type: "function", detail: "(sometimes f pat) — apply transform on ~50% of cycles" },
  { label: "often",        type: "function", detail: "(often f pat) — apply transform on ~75% of cycles" },
  { label: "rarely",       type: "function", detail: "(rarely f pat) — apply transform on ~25% of cycles" },
  { label: "sometimes-by", type: "function", detail: "(sometimes-by prob f pat) — apply transform with probability 0.0–1.0" },
  { label: "degrade",      type: "function", detail: "(degrade pat) — randomly drop ~50% of events" },
  { label: "degrade-by",   type: "function", detail: "(degrade-by prob pat) — drop events with probability 0.0–1.0" },
  { label: "choose",       type: "function", detail: "(choose [vals]) — pick one value per cycle (deterministic)" },
  { label: "wchoose",      type: "function", detail: "(wchoose [[weight val] …]) — weighted random choice per cycle" },
  { label: "jux",          type: "function", detail: "(jux f pat) — original panned left + (f pat) panned right" },
  { label: "jux-by",       type: "function", detail: "(jux-by width f pat) — like jux with adjustable stereo width 0.0–1.0" },
  { label: "off",          type: "function", detail: "(off amount f pat) — layer original with time-shifted transformed copy" },
```

---

## Files to change

| File | Change |
|---|---|
| `packages/core/src/repulse/core.cljs` | Add `euclidean`, `cat*`, `late`, `early`, `sometimes-by`, `sometimes`, `often`, `rarely`, `degrade-by`, `degrade`, `choose`, `wchoose`, `off` + private helpers `cycle-hash`, `event-hash` |
| `packages/core/src/repulse/params.cljs` | Add `jux`, `jux-by` |
| `packages/core/src/repulse/core_test.cljs` | Add 17 unit tests for all new core combinators |
| `packages/core/src/repulse/params_test.cljs` | Add 2 unit tests for `jux` and `jux-by` |
| `packages/lisp/src/repulse/lisp/eval.cljs` | Add 15 bindings to `make-env` |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add 15 tokens to BuiltinName |
| `app/src/repulse/lisp-lang/completions.js` | Add 15 completion entries |
| `CLAUDE.md` | Mark Phase I as delivered when done |

No changes to `packages/audio` (Rust/WASM), `app/src/repulse/audio.cljs`, or any CSS files.

---

## Definition of done

### Euclidean rhythms

- [ ] `(euclidean 5 8 :bd)` produces 8 events with exactly 5 `:bd` and 3 `:_`
- [ ] `(euclidean 3 8 :sd 2)` produces a rotated pattern different from `(euclidean 3 8 :sd)`
- [ ] `(euclidean 4 4 :bd)` produces `[:bd :bd :bd :bd]` (all onsets)
- [ ] `(euclidean 1 8 :bd)` produces 1 onset and 7 rests

### Concatenation

- [ ] `(cat (seq :bd :sd) (seq :hh :oh))` plays `:bd :sd` in cycle 0, `:hh :oh` in cycle 1, then loops
- [ ] `(cat a b c)` loops every 3 cycles
- [ ] `(cat (pure :a))` is equivalent to `(pure :a)`

### Time shifting

- [ ] `(late 0.25 (seq :bd :_ :_ :_))` shifts the kick to the second step position
- [ ] `(early 0.25 pat)` is the inverse of `(late 0.25 pat)` — events cancel when stacked with `(late 0.25 (early 0.25 pat))`
- [ ] `(late 0 pat)` is equivalent to `pat` (identity)

### Stochastic transforms

- [ ] `(sometimes rev pat)` applies `rev` on some cycles and not others
- [ ] `(sometimes-by 0.0 f pat)` never applies the transform (all 20 test cycles pass)
- [ ] `(sometimes-by 1.0 f pat)` always applies the transform
- [ ] `(often f pat)` applies more frequently than `(sometimes f pat)`
- [ ] `(rarely f pat)` applies less frequently than `(sometimes f pat)`
- [ ] Same cycle number always produces the same choice (deterministic)

### Event degradation

- [ ] `(degrade (seq :a :b :c :d :e :f :g :h))` returns fewer than 8 events
- [ ] `(degrade-by 0.0 pat)` keeps all events
- [ ] `(degrade pat)` produces identical results when queried twice for the same span
- [ ] Different events in the same cycle can have different drop decisions

### Random choice

- [ ] `(choose [:a :b :c :d])` returns exactly 1 event per cycle
- [ ] All selected values are from the input vector
- [ ] Same cycle number always picks the same value
- [ ] `(wchoose [[0.5 :bd] [0.3 :sd] [0.2 :hh]])` returns exactly 1 event per cycle

### Stereo layering (jux)

- [ ] `(jux rev (seq :a :b))` produces events with both `:pan -1` and `:pan 1`
- [ ] `(jux-by 0.5 rev pat)` produces events with `:pan -0.5` and `:pan 0.5`
- [ ] Jux events have correct `:note` values from both original and transformed copies

### Time-offset layering (off)

- [ ] `(off 0.5 rev pat)` produces events from both original and time-shifted copy
- [ ] `(off 0 identity pat)` produces doubled events (two copies at same position)

### Composition with existing combinators

- [ ] `(->> (euclidean 5 8 :bd) (sometimes rev) (amp 0.8))` works
- [ ] `(fast 2 (degrade (seq :hh :oh :hh :oh)))` works
- [ ] `(every 4 rev (cat (pure :a) (pure :b)))` works
- [ ] `(jux (fast 2) (scale :minor :c4 (seq 0 2 4)))` works with music theory

### Tests

- [ ] All new unit tests pass (`npm run test:core`)
- [ ] All existing core-test, theory-test, and params-test still pass

### UI

- [ ] All 15 new names appear in autocomplete with correct detail strings
- [ ] All 15 new names receive syntax highlighting as built-in names
- [ ] `npm run gen:grammar` has been run and both `.grammar` and `parser.js` are committed

---

## What NOT to do in this phase

- No changes to the audio engine (Rust/WASM) or audio bridge (`audio.cljs`)
- No new synthesis voices or effects
- No changes to the reader (`reader.cljs`) — all new functions are regular bindings
- No changes to the editor UI, context panel, or command bar
- No external dependencies — only `cljs.core` functions
