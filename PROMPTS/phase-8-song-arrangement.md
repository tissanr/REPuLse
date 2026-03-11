# REPuLse — Song Arrangement Language (Phase 8)

## Context

REPuLse excels at live pattern manipulation. As pieces grow, a flat sequence of `def`
bindings mixed with cycle-counting logic becomes hard to read. This phase introduces
a **three-layer abstraction** that cleanly separates:

1. **Motifs** — small, reusable pattern building blocks
2. **Sections** — musical segments (intro, verse, chorus, bridge) built from motifs
3. **Arrangement** — the song form: which section plays when, and for how many cycles

The Lisp additions are minimal and composable — they fit naturally into the existing
language without any special syntax beyond what is already there.

---

## Goal for this session

By the end of this session:

1. `(arrange [[verse 8] [chorus 8] [verse 8] [chorus 8]])` plays a four-part song form
2. `(play-scenes [verse verse chorus bridge chorus])` plays sections sequentially, one cycle each
3. The arrangement loops after the total duration
4. Map literals `{:kick-dense true :transpose 2}` work in the reader
5. `get`, `assoc`, and `merge` are available for parametric section factories
6. All existing patterns continue to work — no breaking changes

---

## The three-layer pattern

```lisp
;;; === Layer 1: Motifs ===
(def drums-a (seq :bd :_ :sd :_))
(def drums-b (seq :bd :bd :sd :_))
(def bass-a  (seq 60 :_ 55 :_ 60 :_ 58 :_))
(def bass-b  (seq 65 :_ 65 :_ 69 :_ 60 :_))
(def lead-a  (seq 76 79 81 79))

;;; === Layer 2: Sections ===
(def intro  (stack (slow 2 drums-a)))
(def verse  (stack drums-a bass-a))
(def chorus (stack drums-b bass-b lead-a))
(def bridge (stack (rev drums-a) (slow 2 bass-b)))

;;; === Layer 3: Arrangement ===
(arrange
  [[intro  4]
   [verse  8]
   [chorus 8]
   [verse  8]
   [chorus 8]
   [bridge 4]
   [chorus 8]])
```

---

## Parametric sections

Because `fn` and `let` already exist, parametric sections work immediately with the
new map support:

```lisp
(def make-verse
  (fn [opts]
    (let [dense?    (get opts :kick-dense false)
          transpose (get opts :transpose 0)]
      (stack
        (if dense? drums-b drums-a)
        (fmap (fn [n] (+ n transpose)) bass-a)))))

(def verse-1 (make-verse {}))
(def verse-2 (make-verse {:open-hat true}))
(def verse-3 (make-verse {:kick-dense true :transpose 2}))

(arrange
  [[intro   4]
   [verse-1 8]
   [chorus  8]
   [verse-2 8]
   [chorus  8]
   [verse-3 8]
   [chorus  8]])
```

---

## `play-scenes` — one cycle per section

For Ableton-style scene chains where every section is exactly one cycle:

```lisp
(play-scenes [verse verse chorus verse chorus bridge chorus])
```

Equivalent to `(arrange [[verse 1] [verse 1] [chorus 1] ...])`.
Useful for live jamming where patterns are short (one bar each) and you want
to trigger sequences on the fly.

---

## Architecture

### New core combinator: `arrange*`

**`packages/core/src/repulse/core.cljs`**

`arrange*` takes an ordered vector of `[pattern duration-in-cycles]` pairs and returns
a Pattern that dispatches to the correct section based on the current cycle, looping
after the total duration.

```clojure
(defn arrange* [plan]
  "plan: [[pattern cycles] ...]
   Returns a Pattern that plays each section in order and loops."
  (let [;; Build timeline: [{:pat p :from 0 :to 4} {:pat p :from 4 :to 12} ...]
        timeline (reduce
                   (fn [acc [pat dur]]
                     (let [prev (:to (last acc) 0)]
                       (conj acc {:pat pat :from prev :to (+ prev dur)})))
                   [] plan)
        total    (:to (last timeline) 1)]
    (pattern
     (fn [{:keys [start end]}]
       (let [g-cycle   (int (Math/floor (rat->float start)))
             lc        (mod g-cycle total)         ; position within one loop
             loop-off  (- g-cycle lc)              ; how many full loops have elapsed
             entry     (some #(when (and (>= lc (:from %)) (< lc (:to %))) %) timeline)]
         (when entry
           (let [sec-off    (:from entry)          ; section starts at this loop-local cycle
                 offset     (+ loop-off sec-off)   ; shift from global to section-local time
                 ;; Query section at its local time
                 local-start (rat+ start [(- offset) 1])
                 local-end   (rat+ end   [(- offset) 1])
                 evs         (query (:pat entry) {:start local-start :end local-end})]
             ;; Shift event timestamps back to global time
             (map (fn [e]
                    (-> e
                        (update :whole #(span (rat+ (:start %) [offset 1])
                                              (rat+ (:end   %) [offset 1])))
                        (update :part  #(span (rat+ (:start %) [offset 1])
                                              (rat+ (:end   %) [offset 1])))))
                  evs))))))))
```

Add a unit test for `arrange*` in `packages/core/src/repulse/core_test.cljs`:

```clojure
(deftest arrange-test
  (let [a (pure :a)
        b (pure :b)
        arr (arrange* [[a 2] [b 2]])]
    ;; Cycle 0–1: section a
    (is (= [:a] (map :value (query arr (cycle-span 0)))))
    ;; Cycle 2–3: section b
    (is (= [:b] (map :value (query arr (cycle-span 2)))))
    ;; Cycle 4: back to a (loops)
    (is (= [:a] (map :value (query arr (cycle-span 4)))))))
```

---

### Reader: map literals

**`packages/lisp/src/repulse/lisp/reader.cljs`**

Add `{` / `}` support. Map literal syntax: `{:key1 val1 :key2 val2 ...}`.

Add to `read-form`:

```clojure
(= \{ ch) (read-map r)
```

Implement `read-map`:

```clojure
(defn read-map [r]
  (advance r) ; consume {
  (loop [m {}]
    (skip-ws-comments r)
    (let [ch (peek-char r)]
      (cond
        (nil? ch) (throw (ex-info "Unterminated map" {:type :read-error}))
        (= \} ch) (do (advance r) m)
        :else
        (let [k (read-form r)
              _ (skip-ws-comments r)
              v (read-form r)]
          (recur (assoc m k v)))))))
```

Test: `(read-one "{:a 1 :b 2}")` → `{:a 1 :b 2}`.

---

### Evaluator: map support + new built-ins

**`packages/lisp/src/repulse/lisp/eval.cljs`**

**1. Maps are self-evaluating** (add to the literal branch in `eval-form`):

```clojure
(map? form)
(into {} (map (fn [[k v]] [(eval-form k env) (eval-form v env)]) form))
```

**2. New built-ins in `make-env`:**

```clojure
;; Map operations
"get"    (fn [m k]   (get m k nil))
"get"    (fn [m k d] (get m k d))      ; optional default — use variadic arity
"assoc"  (fn [m k v] (assoc m k v))
"merge"  (fn [& ms]  (apply merge ms))
"keys"   (fn [m]     (keys m))
"vals"   (fn [m]     (vals m))

;; Arrangement
"arrange"     (fn [plan]
                (core/arrange*
                  (map (fn [[pat dur]] [pat dur]) plan)))

"play-scenes" (fn [sections]
                (core/arrange*
                  (map (fn [pat] [pat 1]) sections)))
```

> **Note on variadic `get`:** The existing evaluator uses fixed-arity ClojureScript functions.
> `get` with 2 or 3 args is handled naturally since ClojureScript's `get` already accepts
> an optional default. Bind it as `"get" cljs.core/get`.

**3. `if` with truthy/falsy** (verify existing `if` handles non-boolean values like `false`
and `nil` as falsy, and everything else as truthy — this is needed for `(get opts :key false)`
defaulting correctly).

---

### Lisp `core.cljs` public entry

**`packages/lisp/src/repulse/lisp/core.cljs`** — no changes needed. `eval-string` already
handles multiple top-level forms; the last one is returned. So:

```lisp
(def verse ...)
(def chorus ...)
(arrange [[verse 4] [chorus 4]])
```

…returns the Pattern from `arrange`, which `app.cljs` routes to the audio scheduler.

---

## Structural guide for users

Recommend (in USAGE.md) organizing code with section comments:

```lisp
; ===== motifs =====
(def kick-a  (seq :bd :_ :bd :_))
(def snare-a (seq :_ :sd :_ :sd))
(def hats    (fast 2 (seq :hh :_)))

; ===== sections =====
(def intro   (stack kick-a))
(def verse   (stack kick-a snare-a hats))
(def chorus  (stack kick-a snare-a (fast 4 (seq :hh :oh))))
(def bridge  (stack (rev kick-a) snare-a))

; ===== arrangement =====
(arrange
  [[intro  4]
   [verse  8]
   [chorus 8]
   [verse  8]
   [chorus 8]
   [bridge 4]
   [chorus 8]])
```

---

## Repository structure changes

```
packages/
├── core/src/repulse/core.cljs         updated — arrange* combinator
├── core/src/repulse/core_test.cljs    updated — arrange* tests
└── lisp/src/repulse/lisp/
    ├── reader.cljs                    updated — map literal { } support
    └── eval.cljs                      updated — map eval, get/assoc/merge, arrange/play-scenes
```

No changes to `app/`, `packages/audio/`, or the build system.

---

## Definition of Done

- [ ] `(arrange [[a 2] [b 2]])` plays `a` for 2 cycles then `b` for 2 cycles, then loops
- [ ] `(play-scenes [a b b a])` plays each pattern for 1 cycle in order, then loops
- [ ] Arrangement with 4+ sections plays in correct order (verified by listening)
- [ ] `(stop)` stops the arrangement immediately
- [ ] `{:key val}` parsed correctly: `(get {:a 1} :a)` → `1`
- [ ] Parametric section factory works: `(def make-v (fn [o] (if (get o :dense false) drums-b drums-a)))`
- [ ] `(arrange ...)` returned value is a Pattern (routes to the audio scheduler)
- [ ] All existing core unit tests still pass
- [ ] New `arrange*` unit test passes

---

## What NOT to do in this phase

- No multi-buffer editor UI — arrangement lives in a single editor buffer
- No visual timeline or piano roll (see Phase 4 / FUTURE-FEATURES.md)
- No `when` or `cond` with cycle-count arithmetic — the arrangement layer replaces that need
- No changes to the audio engine, scheduler, or WASM synthesis
- No Clojure-style namespaced keywords (`:ns/name`) — plain keywords only
