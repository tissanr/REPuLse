# Phase M — Lisp Superpowers: defsynth, Macros & TCO

## Goal

Four language-level additions that make REPuLse-Lisp a genuinely expressive instrument —
not just a shell around pattern combinators:

1. **`defsynth`** — user-defined synthesis instruments built from Web Audio node graphs
2. **`defmacro`** — compile-time macro expansion with quasiquote/unquote
3. **`loop`/`recur`** — tail-call optimised iteration
4. **Number notation** — `1/4` rationals and `120bpm` sugar in the reader

```lisp
;; Before — only built-in drum voices and raw sine tones:
(seq :bd :sd :hh :sd)
(seq :c4 :e4 :g4)

;; After — user-defined synths from Web Audio node graphs:
(defsynth pluck [freq]
  (-> (saw freq)
      (lpf (* freq 2))
      (env-perc 0.01 0.3)))

(defsynth pad [freq]
  (-> (mix (sin freq) (sin (* freq 1.002)))
      (lpf 2000)
      (env-asr 0.3 0.8 1.0)))

(->> (scale :minor :c4 (seq 0 2 4 7))
     (synth :pluck)
     (amp 0.7))

;; After — macros:
(defmacro swing [amount pat]
  `(off ~amount identity ~pat))

(swing 0.1 (seq :bd :sd))
;; → (off 0.1 identity (seq :bd :sd))

;; After — tail-call iteration:
(defn gen-melody [seed]
  (loop [notes [] i 0]
    (if (>= i 16)
      (apply seq notes)
      (recur (conj notes (+ 200 (* i 30))) (+ i 1)))))

;; After — number notation:
(slow 1/4 (seq :bd :sd))   ; rational literal
(bpm 120bpm)               ; BPM suffix
```

---

## Background

### Current evaluator

`packages/lisp/src/repulse/lisp/eval.cljs` — special forms: `def`, `let`, `fn`/`lambda`,
`if`, `do`, `->>`. `make-env` holds all pattern/math/map bindings. `eval-form` is the
recursive evaluator. Uses `SourcedVal` wrappers for source-position tracking.

There is no macro system, no `loop`/`recur`, no way to define synths, and no reader
support for rationals or suffixed numbers.

### Current reader

`packages/lisp/src/repulse/lisp/reader.cljs` — hand-written recursive-descent parser.
Returns nested lists/vectors/maps/keywords/numbers/strings with `SourcedVal` source
annotations. No support for backtick, `~`, `~@`, or rational number syntax.

### Current audio dispatch

`app/src/repulse/audio.cljs` — `play-event` dispatches on value type:
- `:_` → silence
- `{:note ... :amp ...}` → parameter map → WASM trigger_v2 or JS fallback
- `{:bank ...}` → sample bank
- keyword → note keyword → Hz tone, or drum/sample fallback
- number → Hz frequency

There is no dispatch for user-defined synth names. All synthesis is either WASM
(AudioWorklet) or the JS fallback voices (kick, snare, hihat, sine).

### Current JS synthesis

The JS fallback voices in `audio.cljs` (`make-kick`, `make-snare`, `make-hihat`,
`make-sine`) directly construct Web Audio oscillators/filters/gains. The `defsynth`
system generalises this pattern — each UGen function creates and connects Web Audio nodes
from a higher-level specification.

---

## Design

### 1. `defsynth` — synthesis graph factory

**Key decision: main-thread AudioContext, not AudioWorklet.** User-defined synths use
Web Audio's native node graph on the main thread. The AudioWorklet is kept exclusively
for the built-in WASM voices. This avoids the complexity of compiling arbitrary graphs
to WASM and is simpler to debug. Web Audio nodes handle scheduling, envelopes, and
garbage collection natively.

**Architecture:**

`defsynth` is a special form in the evaluator. It registers a *synth definition* in
the environment — a name mapped to a specification that includes parameter names and a
graph-builder function. When an event with `:synth` key is played, the audio dispatcher
looks up the synth definition and calls the graph builder, which instantiates a chain of
Web Audio nodes, connects them, starts oscillators, and schedules envelopes.

**Signal flow:**

```
(defsynth pluck [freq]        ; declaration — stores spec in env
  (-> (saw freq)              ; source: OscillatorNode type=sawtooth
      (lpf (* freq 2))        ; processor: BiquadFilterNode type=lowpass
      (env-perc 0.01 0.3)))   ; envelope: GainNode with automation

; At event time — instantiation:
OscillatorNode(sawtooth, freq) → BiquadFilterNode(lowpass, cutoff) → GainNode(envelope) → masterGain
```

Each synth instance is ephemeral — created at event time, auto-destroyed when the
envelope completes. Nodes are disconnected and dereferenced after the total duration
(attack + decay/release + a small safety margin).

**Synth events in the pattern layer:**

The `synth` function in the evaluator wraps each event value in a map:

```clojure
{:synth :pluck :freq 440.0}
```

`play-event` in `audio.cljs` checks for `:synth` key before any other dispatch. If
present, it looks up the synth definition and calls the graph factory.

### 2. `defmacro` — compile-time transforms

**Key decision: simplest useful macro system.** Quasiquote + unquote + splice-unquote.
No gensym, no hygiene, no nested quasiquote. This covers the 90% use case (syntactic
sugar for pattern combinators) without the complexity.

Macros are stored in a separate `*macros*` atom in the environment. When the evaluator
encounters a list whose head resolves to a macro name, it:
1. Calls the macro function with the **unevaluated** argument forms
2. Gets back an expanded form
3. Evaluates the expanded form

Reader changes add three new reader macros:
- `` ` `` (backtick) → `(quasiquote ...)`
- `~` (unquote) → `(unquote ...)`
- `~@` (splice-unquote) → `(splice-unquote ...)`

The evaluator handles `quasiquote` by walking the form tree, leaving symbols/values
as-is (quoted), but evaluating `(unquote ...)` sub-forms and splicing `(splice-unquote ...)`
lists.

### 3. `loop`/`recur` — trampoline-style TCO

**Key decision: sentinel-based trampoline, not exception-based.** `recur` returns a
special sentinel object that `loop` catches in its iteration. This avoids the overhead
of exception creation on every iteration.

`loop` establishes bindings and a recursion point. `recur` returns new binding values
to the enclosing `loop`. Implementation uses a JavaScript while-loop internally.

### 4. Number notation — reader-level sugar

Two additions to the reader's number parsing:
- `1/4` → rational pair `[1 4]` (REPuLse's internal rational representation)
- `120bpm` → the number `120` with a `bpm` suffix that evaluates to `(bpm 120)`

---

## Implementation

### 1. New file: `app/src/repulse/synth.cljs` — graph builder + UGen vocabulary

This module lives in `app/` (not `packages/core`) because it depends on Web Audio APIs.

```clojure
(ns repulse.synth
  (:require [repulse.audio :refer [master-gain]]))

;;; ── Synth definition registry ──────────────────────────────────────

;; Stores synth definitions: name (keyword) → {:params [...] :build-fn (fn [ac t params] node)}
(defonce synth-defs (atom {}))

(defn register-synth!
  "Register a synth definition. build-fn takes [ac t param-map] and returns
   the final output AudioNode (already connected internally)."
  [synth-name params build-fn]
  (swap! synth-defs assoc synth-name {:params params :build-fn build-fn}))

(defn lookup-synth [synth-name]
  (get @synth-defs synth-name))

;;; ── UGen functions ─────────────────────────────────────────────────
;;
;; Each UGen function takes an AudioContext, a scheduled start time, and
;; parameters. Source UGens return a node that is not yet connected to a
;; destination. Processor UGens take a source node as their last argument,
;; connect the source into themselves, and return the processor node.
;; Envelope UGens wrap a source in a GainNode with scheduled automation.

(defn sin-osc
  "Sine oscillator. Returns an OscillatorNode (not started)."
  [ac freq]
  (let [osc (.createOscillator ac)]
    (set! (.-type osc) "sine")
    (.setValueAtTime (.-frequency osc) freq 0)
    osc))

(defn saw-osc
  "Sawtooth oscillator."
  [ac freq]
  (let [osc (.createOscillator ac)]
    (set! (.-type osc) "sawtooth")
    (.setValueAtTime (.-frequency osc) freq 0)
    osc))

(defn square-osc
  "Square wave oscillator."
  [ac freq]
  (let [osc (.createOscillator ac)]
    (set! (.-type osc) "square")
    (.setValueAtTime (.-frequency osc) freq 0)
    osc))

(defn tri-osc
  "Triangle wave oscillator."
  [ac freq]
  (let [osc (.createOscillator ac)]
    (set! (.-type osc) "triangle")
    (.setValueAtTime (.-frequency osc) freq 0)
    osc))

(defn noise-src
  "White noise buffer source."
  [ac]
  (let [buf-size 8192
        buf  (.createBuffer ac 1 buf-size (.-sampleRate ac))
        data (.getChannelData buf 0)
        _    (dotimes [i buf-size]
               (aset data i (- (* 2 (Math/random)) 1)))
        src  (.createBufferSource ac)]
    (set! (.-buffer src) buf)
    src))

(defn lpf-node
  "Lowpass filter. Connects source → filter, returns filter node."
  [ac cutoff source]
  (let [flt (.createBiquadFilter ac)]
    (set! (.-type flt) "lowpass")
    (.setValueAtTime (.-frequency flt) cutoff 0)
    (.connect source flt)
    flt))

(defn hpf-node
  "Highpass filter."
  [ac cutoff source]
  (let [flt (.createBiquadFilter ac)]
    (set! (.-type flt) "highpass")
    (.setValueAtTime (.-frequency flt) cutoff 0)
    (.connect source flt)
    flt))

(defn bpf-node
  "Bandpass filter."
  [ac freq source]
  (let [flt (.createBiquadFilter ac)]
    (set! (.-type flt) "bandpass")
    (.setValueAtTime (.-frequency flt) freq 0)
    (.connect source flt)
    flt))

(defn gain-node
  "Static gain. Connects source → gain, returns gain node."
  [ac level source]
  (let [g (.createGain ac)]
    (.setValueAtTime (.-gain g) level 0)
    (.connect source g)
    g))

(defn delay-line
  "Delay line. Connects source → delay, returns delay node."
  [ac time source]
  (let [d (.createDelay ac 5.0)]
    (.setValueAtTime (.-delayTime d) time 0)
    (.connect source d)
    d))

(defn mix-node
  "Mix two source nodes into a single output. Returns a GainNode."
  [ac source-a source-b]
  (let [g (.createGain ac)]
    (.setValueAtTime (.-gain g) 1.0 0)
    (.connect source-a g)
    (.connect source-b g)
    g))

(defn env-perc-node
  "Percussive envelope (linear attack, exponential decay).
   Wraps source in a GainNode with scheduled automation.
   Returns {:node gain-node :duration (+ attack decay)}."
  [ac t attack decay source]
  (let [g (.createGain ac)
        atk (max 0.001 attack)]
    (.setValueAtTime (.-gain g) 0.0001 t)
    (.linearRampToValueAtTime (.-gain g) 1.0 (+ t atk))
    (.exponentialRampToValueAtTime (.-gain g) 0.0001 (+ t atk decay))
    (.connect source g)
    {:node g :duration (+ atk decay)}))

(defn env-asr-node
  "ASR envelope (attack, sustain-level, release).
   The sustain phase lasts until release begins. Total duration = attack + 1.0 + release
   (1 second sustain hold as default; future: gate-based release).
   Returns {:node gain-node :duration total}."
  [ac t attack sustain release source]
  (let [g   (.createGain ac)
        atk (max 0.001 attack)
        rel (max 0.001 release)
        sustain-hold 1.0
        total (+ atk sustain-hold rel)]
    (.setValueAtTime (.-gain g) 0.0001 t)
    (.linearRampToValueAtTime (.-gain g) sustain (+ t atk))
    (.setValueAtTime (.-gain g) sustain (+ t atk sustain-hold))
    (.exponentialRampToValueAtTime (.-gain g) 0.0001 (+ t total))
    (.connect source g)
    {:node g :duration total}))

;;; ── Synth instantiation ────────────────────────────────────────────

(defn play-synth!
  "Instantiate a synth definition at scheduled time t with the given parameter map.
   Creates Web Audio nodes, connects them, starts oscillators, and schedules cleanup."
  [ac t synth-def param-map]
  (let [{:keys [build-fn]} synth-def
        result (build-fn ac t param-map)
        ;; result is either:
        ;;   - an AudioNode (source with no envelope — use default 1.5s duration)
        ;;   - a map {:node AudioNode :duration N} from an envelope UGen
        [out-node duration] (if (map? result)
                              [(:node result) (:duration result)]
                              [result 1.5])
        dest (or @master-gain (.-destination ac))]
    ;; Connect to master output
    (.connect out-node dest)
    ;; Start all oscillator/buffer sources in the graph
    ;; (build-fn is responsible for collecting startable nodes)
    ;; Cleanup: disconnect all nodes after envelope completes
    (js/setTimeout
      (fn []
        (try (.disconnect out-node) (catch :default _)))
      (* (+ duration 0.1) 1000))))
```

> **Note on node lifecycle:** The build function creates nodes, connects them
> internally, and returns the final output. `play-synth!` connects it to the master
> chain and schedules cleanup. Oscillators/BufferSources must be `.start(t)`-ed by the
> build function. The envelope UGens (`env-perc-node`, `env-asr-node`) provide their
> duration, which is used to schedule disconnection.

---

### 2. `packages/lisp/src/repulse/lisp/reader.cljs` — backtick, unquote, rationals

#### 2a. New reader macros: backtick, unquote, splice-unquote

Add three new branches to `read-form*`, before the `sym-char?` catch-all:

```clojure
;; In read-form*:
(= \` ch)   ;; Quasiquote
(do (advance r)
    (let [inner (read-form r)]
      (list (symbol "quasiquote") inner)))

(= \~ ch)   ;; Unquote or splice-unquote
(do (advance r)
    (if (= \@ (peek-char r))
      (do (advance r)
          (let [inner (read-form r)]
            (list (symbol "splice-unquote") inner)))
      (let [inner (read-form r)]
        (list (symbol "unquote") inner))))
```

#### 2b. Rational number syntax

Extend `read-number` to handle `N/D` notation and `bpm` suffix:

```clojure
(defn read-number [r]
  (loop [acc []]
    (let [ch (peek-char r)]
      (if (and ch (or (digit? ch) (= \. ch)))
        (recur (conj acc (advance r)))
        (let [s (apply str acc)]
          (cond
            ;; Rational: 1/4, 3/8, etc.
            (= \/ (peek-char r))
            (do (advance r) ; consume /
                (let [denom (read-number r)]
                  [(if (re-find #"\." s) (js/parseFloat s) (js/parseInt s 10))
                   denom]))

            ;; BPM suffix: 120bpm → parsed by evaluator
            (and (= \b (peek-char r))
                 (let [ahead (subs (:src r) @(:pos r))]
                   (.startsWith ahead "bpm")))
            (do (dotimes [_ 3] (advance r)) ; consume "bpm"
                (let [n (if (re-find #"\." s) (js/parseFloat s) (js/parseInt s 10))]
                  (list (symbol "bpm") n)))

            :else
            (if (re-find #"\." s) (js/parseFloat s) (js/parseInt s 10))))))))
```

#### 2c. Add backtick and tilde to valid characters

The backtick and tilde are currently not in `sym-char?` and would trigger the
"Unexpected char" error. Since they are handled as special cases in `read-form*` before
the `sym-char?` branch, no change to `sym-char?` is needed — just ensure the new
branches are placed before the `sym-char?` fallback.

**Important:** The `~` character must also be added to the `identStart` set in the Lezer
grammar if we want the mini-notation `(~ "bd sd")` to continue working. Currently `~`
is handled as a symbol in the reader because it passes `sym-char?`. After this change,
`~` at the start of a form triggers unquote. The mini-notation `~` remains safe because
it appears as a bare symbol (no following `@`), and the `(~ "...")` form is a list where
`~` is the head — the unquote reader would wrap it as `(unquote "...")`, which evaluator
can dispatch on. **Resolution:** Redefine `~` at the reader level: when `~` is followed
by whitespace or `"`, treat it as a symbol (the mini-notation operator). When followed
by anything else, treat it as unquote. This preserves backward compatibility.

```clojure
;; Updated ~ handling in read-form*:
(= \~ ch)
(do (advance r)
    (let [next-ch (peek-char r)]
      (cond
        ;; ~@ → splice-unquote
        (= \@ next-ch)
        (do (advance r)
            (let [inner (read-form r)]
              (list (symbol "splice-unquote") inner)))

        ;; ~ followed by whitespace or " or ) → treat as symbol (mini-notation operator)
        (or (nil? next-ch) (whitespace? next-ch) (= \" next-ch) (= \) next-ch))
        (symbol "~")

        ;; ~ followed by anything else → unquote
        :else
        (let [inner (read-form r)]
          (list (symbol "unquote") inner)))))
```

---

### 3. `packages/lisp/src/repulse/lisp/eval.cljs` — defsynth, defmacro, loop/recur, quasiquote

#### 3a. Add `*macros*` and `*synths*` atoms to `make-env`

```clojure
;; In make-env, alongside :*defs*:
:*macros* (atom {})    ; macro name → (fn [& unevaluated-forms] expanded-form)
:*synths* (atom {})    ; synth name → {:params [...] :build-fn ...}
```

#### 3b. Quasiquote expansion helper

Add before `eval-form`:

```clojure
(defn- expand-quasiquote
  "Walk a quasiquoted form, replacing (unquote x) with the evaluated value
   of x and splicing (splice-unquote x) into the surrounding list."
  [form env]
  (cond
    ;; (unquote expr) → evaluate expr
    (and (seq? form) (= (first form) (symbol "unquote")))
    (eval-form (second form) env)

    ;; List containing possible splice-unquotes
    (seq? form)
    (apply list
      (reduce
        (fn [acc item]
          (if (and (seq? item) (= (first item) (symbol "splice-unquote")))
            (into acc (eval-form (second item) env))
            (conj acc (expand-quasiquote item env))))
        [] form))

    ;; Vector containing possible splice-unquotes
    (vector? form)
    (reduce
      (fn [acc item]
        (if (and (seq? item) (= (first item) (symbol "splice-unquote")))
          (into acc (eval-form (second item) env))
          (conj acc (expand-quasiquote item env))))
      [] form)

    ;; Anything else (symbol, keyword, number, string) → return as-is (quoted)
    :else form))
```

#### 3c. `recur` sentinel

```clojure
;; Sentinel type for loop/recur — a plain map, not an exception
(def ^:private recur-sentinel-type ::recur-sentinel)

(defn- recur-sentinel [args]
  {::type recur-sentinel-type ::args args})

(defn- recur-sentinel? [x]
  (and (map? x) (= (::type x) recur-sentinel-type)))
```

#### 3d. New special forms in `eval-form`

Add these cases to the `case` dispatch, after the existing `"->>"`
case and before the function-call default:

```clojure
"defsynth"
;; (defsynth name [param-names] body...)
;; Registers a synth definition in the *synths* atom.
;; The body is a chain of UGen calls that build a Web Audio node graph.
(let [[name-sym params & body] tail
      synth-name (keyword (str name-sym))
      param-names (mapv str params)]
  (when-let [synths (:*synths* env)]
    (let [build-fn (fn [ac t param-map]
                     ;; Create a local env with UGen functions bound + synth params
                     (let [ugen-env (merge env
                                     (zipmap param-names
                                             (map #(get param-map (keyword %)) param-names))
                                     ;; UGen bindings — each returns a Web Audio node
                                     {"sin"        (fn [freq] (synth/sin-osc ac (unwrap freq)))
                                      "saw"        (fn [freq] (synth/saw-osc ac (unwrap freq)))
                                      "square"     (fn [freq] (synth/square-osc ac (unwrap freq)))
                                      "tri"        (fn [freq] (synth/tri-osc ac (unwrap freq)))
                                      "noise"      (fn [] (synth/noise-src ac))
                                      "lpf"        (fn [cutoff src] (synth/lpf-node ac (unwrap cutoff) (unwrap src)))
                                      "hpf"        (fn [cutoff src] (synth/hpf-node ac (unwrap cutoff) (unwrap src)))
                                      "bpf"        (fn [freq src] (synth/bpf-node ac (unwrap freq) (unwrap src)))
                                      "gain"       (fn [level src] (synth/gain-node ac (unwrap level) (unwrap src)))
                                      "delay-node" (fn [time src] (synth/delay-line ac (unwrap time) (unwrap src)))
                                      "mix"        (fn [a b] (synth/mix-node ac (unwrap a) (unwrap b)))
                                      "env-perc"   (fn [atk dec src] (synth/env-perc-node ac t (unwrap atk) (unwrap dec) (unwrap src)))
                                      "env-asr"    (fn [atk sus rel src] (synth/env-asr-node ac t (unwrap atk) (unwrap sus) (unwrap rel) (unwrap src)))})]
                       ;; Evaluate the body in the UGen-enriched env
                       (last (map #(eval-form % ugen-env) body))))]
      (swap! synths assoc synth-name {:params param-names :build-fn build-fn})
      ;; Also register in *defs* so the name is visible
      (when-let [defs (:*defs* env)]
        (swap! defs assoc (str name-sym) synth-name))))
  synth-name)

"defmacro"
;; (defmacro name [params] body)
;; Stores a macro function that transforms unevaluated forms.
(let [[name-sym params & body] tail
      macro-fn (fn [& args]
                 (let [bound (zipmap (map str params) args)
                       local (merge env bound)]
                   (last (map #(eval-form % local) body))))]
  (when-let [macros (:*macros* env)]
    (swap! macros assoc (str name-sym) macro-fn))
  nil)

"quasiquote"
;; (quasiquote form) — produced by the ` reader macro
(expand-quasiquote (first tail) env)

"loop"
;; (loop [bindings...] body...)
;; Establishes a recursion point. `recur` jumps back with new values.
(let [[bindings & body] tail
      pairs (partition 2 bindings)]
  (loop [current-vals (mapv (fn [[_ vf]] (eval-form vf env)) pairs)
         binding-names (mapv (fn [[s _]] (str s)) pairs)]
    (let [local (reduce (fn [e [n v]] (assoc e n v))
                        env
                        (map vector binding-names current-vals))
          result (reduce (fn [_ form]
                           (let [r (eval-form form local)]
                             (if (recur-sentinel? r)
                               (reduced r)
                               r)))
                         nil body)]
      (if (recur-sentinel? result)
        (recur (::args result) binding-names)
        result))))

"recur"
;; (recur expr ...) — jump to enclosing loop with new binding values
(recur-sentinel (mapv #(eval-form % env) tail))
```

#### 3e. Macro expansion in function-call dispatch

Modify the function-call default branch (the final `else` in the `case`) to check
for macros before evaluating as a function call:

```clojure
;; Replace the existing function-call default:
(let [head-name (when (symbol? head) (str head))
      macros    (some-> (:*macros* env) deref)]
  (if-let [macro-fn (and head-name macros (get macros head-name))]
    ;; Macro expansion: call with unevaluated forms, then eval the result
    (let [expanded (apply macro-fn tail)]
      (eval-form expanded env))
    ;; Normal function call
    (let [f (eval-form head env)]
      (if (fn? f)
        (apply f (map #(eval-form % env) tail))
        (throw (ex-info (str (pr-str head) " is not a function")
                        {:type :eval-error}))))))
```

#### 3f. `synth` binding in `make-env`

Add to `make-env`:

```clojure
;; Synth application — wraps pattern events with :synth key
"synth" (fn [synth-name pat]
          (let [sn (unwrap synth-name)]
            (core/fmap (fn [v]
                         (let [m (if (map? v) v {:note v})
                               freq (or (:freq m)
                                        (when (number? (:note m)) (:note m))
                                        (when (keyword? (:note m))
                                          (theory/note->hz (:note m))))]
                           (assoc m :synth sn :freq freq)))
                       (unwrap pat))))

;; Helper list/vector operations for macro and loop use
"conj"    (fn [coll v] (conj (unwrap coll) (unwrap v)))
"apply"   (fn [f & args]
            (let [all-args (unwrap (last args))
                  init-args (map unwrap (butlast args))]
              (apply f (concat init-args all-args))))
"list"    (fn [& vs] (apply list (map unwrap vs)))
"count"   (fn [coll] (count (unwrap coll)))
"nth"     (fn [coll i] (nth (unwrap coll) (unwrap i)))
"first"   (fn [coll] (first (unwrap coll)))
"rest"    (fn [coll] (rest (unwrap coll)))
"empty?"  (fn [coll] (empty? (unwrap coll)))
"cons"    (fn [x coll] (cons (unwrap x) (unwrap coll)))
"concat"  (fn [& colls] (apply concat (map unwrap colls)))
"vec"     (fn [coll] (vec (unwrap coll)))
"map"     (fn [f coll] (map f (unwrap coll)))
"range"   (fn
            ([n] (range (unwrap n)))
            ([a b] (range (unwrap a) (unwrap b)))
            ([a b step] (range (unwrap a) (unwrap b) (unwrap step))))
"mod"     (fn [a b] (mod (unwrap a) (unwrap b)))
"identity" (fn [x] x)
```

#### 3g. `defn` as sugar for `def` + `fn`

Add as a special form:

```clojure
"defn"
;; (defn name [params] body...) → (def name (fn [params] body...))
(let [[name-sym params & body] tail
      f (make-closure params body env)]
  (when-let [defs (:*defs* env)]
    (swap! defs assoc (str name-sym) f))
  f)
```

---

### 4. `app/src/repulse/audio.cljs` — synth dispatch

Add a new branch in `play-event`, as the **first** map check (before the existing
`(:note value)` branch):

```clojure
;; User-defined synth — {:synth :pluck :freq 440.0 ...}
(and (map? value) (:synth value))
(let [synth-name (:synth value)
      synth-def  (synth/lookup-synth synth-name)]
  (if synth-def
    (synth/play-synth! ac t synth-def value)
    (js/console.warn "[REPuLse] Unknown synth:" (name synth-name))))
```

Add `[repulse.synth :as synth]` to the namespace require.

---

### 5. `app/src/repulse/lisp-lang/repulse-lisp.grammar` — new tokens

Add to `BuiltinName`:

```
"defsynth" | "defmacro" | "defn" | "loop" | "recur" |
"sin" | "saw" | "square" | "tri" | "noise" |
"lpf" | "hpf" | "bpf" | "mix" | "env-perc" | "env-asr" |
"gain" | "delay-node" | "synth" |
```

Also add `` "`" `` and `"~"` to the `identStart` set so they are valid symbol starters
in the grammar (the actual semantic handling is in the reader).

After editing the grammar, **run `npm run gen:grammar`** to regenerate `parser.js`.

---

### 6. `app/src/repulse/lisp-lang/completions.js` — new entries

```javascript
// --- Synth definition ---
{ label: "defsynth",   type: "keyword",  detail: "(defsynth name [params] body) — define a synth instrument from Web Audio nodes" },
{ label: "synth",      type: "function", detail: "(synth :name pat) — apply a user-defined synth to a pattern" },
{ label: "defn",       type: "keyword",  detail: "(defn name [params] body) — define a named function (sugar for def + fn)" },
// --- UGens (inside defsynth) ---
{ label: "sin",        type: "function", detail: "(sin freq) — sine oscillator UGen (inside defsynth)" },
{ label: "saw",        type: "function", detail: "(saw freq) — sawtooth oscillator UGen (inside defsynth)" },
{ label: "square",     type: "function", detail: "(square freq) — square wave UGen (inside defsynth)" },
{ label: "tri",        type: "function", detail: "(tri freq) — triangle wave UGen (inside defsynth)" },
{ label: "noise",      type: "function", detail: "(noise) — white noise UGen (inside defsynth)" },
{ label: "lpf",        type: "function", detail: "(lpf cutoff signal) — lowpass filter UGen" },
{ label: "hpf",        type: "function", detail: "(hpf cutoff signal) — highpass filter UGen" },
{ label: "bpf",        type: "function", detail: "(bpf freq signal) — bandpass filter UGen" },
{ label: "mix",        type: "function", detail: "(mix a b) — mix two signals into one" },
{ label: "env-perc",   type: "function", detail: "(env-perc attack decay signal) — percussive envelope" },
{ label: "env-asr",    type: "function", detail: "(env-asr attack sustain release signal) — ASR envelope" },
{ label: "gain",       type: "function", detail: "(gain level signal) — static gain UGen" },
{ label: "delay-node", type: "function", detail: "(delay-node time signal) — delay line UGen" },
// --- Macros ---
{ label: "defmacro",   type: "keyword",  detail: "(defmacro name [params] body) — define a compile-time macro" },
// --- Iteration ---
{ label: "loop",       type: "keyword",  detail: "(loop [bindings] body) — iteration with recursion point" },
{ label: "recur",      type: "keyword",  detail: "(recur exprs...) — jump to enclosing loop with new values" },
// --- Collection operations ---
{ label: "conj",       type: "function", detail: "(conj coll val) — add value to collection" },
{ label: "count",      type: "function", detail: "(count coll) — number of items" },
{ label: "nth",        type: "function", detail: "(nth coll i) — get item at index" },
{ label: "first",      type: "function", detail: "(first coll) — first item" },
{ label: "rest",       type: "function", detail: "(rest coll) — all items except first" },
{ label: "empty?",     type: "function", detail: "(empty? coll) — true if collection has no items" },
{ label: "cons",       type: "function", detail: "(cons x coll) — prepend item to collection" },
{ label: "concat",     type: "function", detail: "(concat coll ...) — concatenate collections" },
{ label: "vec",        type: "function", detail: "(vec coll) — convert to vector" },
{ label: "map",        type: "function", detail: "(map f coll) — apply f to each item" },
{ label: "range",      type: "function", detail: "(range n) or (range a b) or (range a b step) — number sequence" },
{ label: "mod",        type: "function", detail: "(mod a b) — remainder" },
{ label: "identity",   type: "function", detail: "(identity x) — returns x unchanged" },
{ label: "apply",      type: "function", detail: "(apply f args) — call f with args as argument list" },
{ label: "defn",       type: "keyword",  detail: "(defn name [params] body) — define a named function" },
```

---

### 7. `packages/lisp/test/repulse/lisp/eval_test.cljs` — new tests

Add tests for the new special forms. These tests exercise the evaluator without audio
(no Web Audio needed).

```clojure
(ns repulse.lisp.eval-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [repulse.lisp.reader :as reader]
            [repulse.lisp.eval :as eval]
            [repulse.lisp.core :as lisp]))

(defn- eval-str [src]
  (let [env (eval/make-env (fn [] nil) (fn [_] nil))]
    (:result (lisp/eval-string src env))))

(defn- eval-all [src]
  "Evaluate all forms, return the last result. Shares a single env."
  (let [env (eval/make-env (fn [] nil) (fn [_] nil))
        forms (reader/read-all src)]
    (reduce (fn [_ form] (eval/eval-form form env)) nil forms)))

;;; ── loop/recur ─────────────────────────────────────────────────────

(deftest loop-basic
  (is (= 10 (eval-str "(loop [i 0] (if (>= i 10) i (recur (+ i 1))))"))))

(deftest loop-multiple-bindings
  (is (= 55 (eval-str "(loop [i 0 sum 0] (if (> i 10) sum (recur (+ i 1) (+ sum i))))"))))

(deftest loop-with-body
  ;; loop body can have multiple expressions; last is the value
  (is (= 42 (eval-str "(loop [x 42] x)"))))

;;; ── defn ───────────────────────────────────────────────────────────

(deftest defn-basic
  (is (= 6 (eval-all "(defn double [x] (* x 2)) (double 3)"))))

(deftest defn-recursive
  (is (= 120 (eval-all "(defn fact [n] (if (<= n 1) 1 (* n (fact (- n 1))))) (fact 5)"))))

;;; ── defmacro + quasiquote ──────────────────────────────────────────

(deftest defmacro-simple
  ;; Define a macro that wraps a value in a list call
  (is (= '(1 2 3)
         (eval-all "(defmacro wrap [x] `(list ~x 2 3)) (wrap 1)"))))

(deftest defmacro-splice
  (is (= '(1 2 3 4 5)
         (eval-all "(defmacro spread [xs] `(list 1 ~@xs 5)) (spread (list 2 3 4))"))))

;;; ── number notation (if reader supports it) ────────────────────────

(deftest rational-literal
  ;; 1/4 should parse to the rational pair [1 4]
  (let [forms (reader/read-all "1/4")]
    (is (= [1 4] (eval/unwrap (first forms))))))

(deftest rational-arithmetic
  ;; Rationals should work with pattern functions
  ;; (slow 1/4 ...) should be equivalent to (slow [1 4] ...)
  (is (some? (eval-str "(slow 1/4 (seq :bd :sd))"))))
```

---

### 8. `packages/core/test/repulse/synth_test.cljs` — synth definition tests

Since synth graph building requires Web Audio APIs (browser-only), unit tests for the
synth module are limited. Instead, test the evaluator-level synth registration:

```clojure
(ns repulse.synth-test
  (:require [cljs.test :refer-macros [deftest is]]
            [repulse.lisp.reader :as reader]
            [repulse.lisp.eval :as eval]
            [repulse.core :as core]))

(defn- make-test-env []
  (eval/make-env (fn [] nil) (fn [_] nil)))

(defn- eval-all-env [src env]
  (let [forms (reader/read-all src)]
    (reduce (fn [_ form] (eval/eval-form form env)) nil forms)))

(deftest defsynth-registers-in-synths-atom
  (let [env (make-test-env)]
    (eval-all-env "(defsynth test-synth [freq] freq)" env)
    (is (contains? @(:*synths* env) :test-synth))))

(deftest synth-creates-event-map
  (let [env (make-test-env)
        _ (eval-all-env "(defsynth beep [freq] freq)" env)
        pat (eval-all-env "(synth :beep (pure 440))" env)
        evs (core/query pat {:start [0 1] :end [1 1]})]
    (is (= :beep (:synth (:value (first evs)))))
    (is (= 440.0 (:freq (:value (first evs)))))))
```

---

### 9. `docs/USAGE.md` — new sections

Add three new sections:

#### Synth definition

```markdown
## User-defined synths: defsynth

Define custom instruments from Web Audio node graphs:

    (defsynth pluck [freq]
      (-> (saw freq)
          (lpf (* freq 2))
          (env-perc 0.01 0.3)))

    (defsynth warm-pad [freq]
      (-> (mix (sin freq) (sin (* freq 1.002)))
          (lpf 2000)
          (env-asr 0.3 0.8 1.0)))

Apply a synth to a pattern with `synth`:

    (->> (scale :minor :c4 (seq 0 2 4 7))
         (synth :pluck)
         (amp 0.7))

### Available UGens

| UGen | Description |
|---|---|
| `(sin freq)` | Sine oscillator |
| `(saw freq)` | Sawtooth oscillator |
| `(square freq)` | Square wave |
| `(tri freq)` | Triangle wave |
| `(noise)` | White noise |
| `(lpf cutoff signal)` | Lowpass filter |
| `(hpf cutoff signal)` | Highpass filter |
| `(bpf freq signal)` | Bandpass filter |
| `(mix a b)` | Mix two signals |
| `(env-perc attack decay signal)` | Percussive envelope |
| `(env-asr attack sustain release signal)` | Attack-sustain-release envelope |
| `(gain level signal)` | Static gain control |
| `(delay-node time signal)` | Delay line |
```

#### Macros

```markdown
## Macros: defmacro

Define compile-time transforms with quasiquote syntax:

    (defmacro swing [amount pat]
      `(off ~amount identity ~pat))

    (swing 0.1 (seq :bd :sd))
    ;; expands to: (off 0.1 identity (seq :bd :sd))

    (defmacro call-and-response [a b]
      `(cat ~a ~b))

Quasiquote syntax:
- `` ` `` — template (produces a form with symbols as-is)
- `~` — unquote (insert evaluated value)
- `~@` — splice-unquote (splice a list into the template)
```

#### Iteration

```markdown
## Iteration: loop/recur

Tail-call optimised loops for generating patterns algorithmically:

    (loop [notes [] i 0]
      (if (>= i 8)
        (apply seq notes)
        (recur (conj notes (+ 200 (* i 50))) (+ i 1))))

    ;; Fibonacci melody:
    (defn fib-melody [n]
      (loop [a 1 b 1 notes [] i 0]
        (if (>= i n)
          (apply seq notes)
          (recur b (+ a b) (conj notes (mod (* a 100) 800)) (+ i 1)))))

    (fib-melody 16)
```

---

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/synth.cljs` | **New** — synth definition registry, UGen vocabulary, graph builder, `play-synth!` |
| `packages/lisp/src/repulse/lisp/reader.cljs` | Backtick, unquote, splice-unquote reader macros; rational `N/D` syntax; `bpm` suffix |
| `packages/lisp/src/repulse/lisp/eval.cljs` | `defsynth`, `defmacro`, `defn`, `loop`/`recur`, `quasiquote` special forms; macro expansion; `*macros*`/`*synths*` atoms; `synth` binding; collection helpers; `expand-quasiquote` helper; `recur-sentinel` |
| `app/src/repulse/audio.cljs` | `:synth` dispatch branch in `play-event`; require `repulse.synth` |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add ~20 new tokens to `BuiltinName`; add `` ` `` and `~` to `identStart` |
| `app/src/repulse/lisp-lang/completions.js` | Add ~30 new completion entries (synth, UGens, macros, iteration, collections) |
| `packages/lisp/test/repulse/lisp/eval_test.cljs` | **New** — tests for `loop`/`recur`, `defn`, `defmacro`, quasiquote, rational literals |
| `packages/core/test/repulse/synth_test.cljs` | **New** — tests for synth registration and event map creation |
| `packages/core/src/repulse/test_runner.cljs` | Require new test namespaces |
| `docs/USAGE.md` | New sections: "User-defined synths", "Macros", "Iteration" |
| `README.md` | Add `defsynth`, `defmacro`, `loop`/`recur`, `defn` to language reference |
| `CLAUDE.md` | Mark Phase M as ✓ delivered |

---

## Definition of done

### defsynth — synthesis graph factory

- [ ] `(defsynth beep [freq] (-> (sin freq) (env-perc 0.01 0.5)))` registers the synth
- [ ] `(synth :beep (pure :c4))` produces events with `{:synth :beep :freq 261.63}`
- [ ] Playing a defsynth pattern produces audible sound through Web Audio nodes
- [ ] `(defsynth pluck [freq] (-> (saw freq) (lpf (* freq 2)) (env-perc 0.01 0.3)))` sounds like a filtered sawtooth with fast decay
- [ ] `(defsynth pad [freq] (-> (mix (sin freq) (sin (* freq 1.002))) (lpf 2000) (env-asr 0.3 0.8 1.0)))` produces a slow-attack pad
- [ ] All 5 oscillator types work: `sin`, `saw`, `square`, `tri`, `noise`
- [ ] All 3 filter types work: `lpf`, `hpf`, `bpf`
- [ ] `mix` combines two source signals
- [ ] `env-perc` and `env-asr` both produce audible envelopes
- [ ] `gain` and `delay-node` UGens work
- [ ] Synth instances are cleaned up after envelope completes (no audio leaks)
- [ ] `(synth :pluck (scale :minor :c4 (seq 0 2 4 7)))` plays a minor arpeggio through the pluck synth
- [ ] `(->> (scale :major :c4 (seq 0 1 2 3)) (synth :pad) (amp 0.5))` composes with existing param system
- [ ] Unknown synth name logs a warning, does not crash

### defmacro — compile-time transforms

- [ ] `(defmacro wrap [x] `(list ~x))` followed by `(wrap 42)` returns `(42)`
- [ ] Unquote `~` evaluates the expression and inserts the value
- [ ] Splice-unquote `~@` splices a list into the surrounding form
- [ ] Macros are expanded before evaluation (arguments are not pre-evaluated)
- [ ] `(defmacro double-seq [pat] `(fast 2 ~pat))` works with pattern expressions
- [ ] Backtick `` ` `` in the reader produces `(quasiquote ...)` forms
- [ ] `~` in the reader produces `(unquote ...)` forms
- [ ] `~@` in the reader produces `(splice-unquote ...)` forms
- [ ] Mini-notation `(~ "bd sd")` still works (backward compatibility)

### loop/recur — tail-call iteration

- [ ] `(loop [i 0] (if (>= i 10) i (recur (+ i 1))))` returns `10`
- [ ] `(loop [i 0 sum 0] (if (> i 10) sum (recur (+ i 1) (+ sum i))))` returns `55`
- [ ] `(loop [i 0] (if (>= i 10000) i (recur (+ i 1))))` completes without stack overflow
- [ ] `recur` outside a `loop` produces a meaningful error
- [ ] Multiple `recur` bindings are evaluated left-to-right before rebinding

### defn — named functions

- [ ] `(defn double [x] (* x 2))` followed by `(double 5)` returns `10`
- [ ] `(defn fact [n] (if (<= n 1) 1 (* n (fact (- n 1)))))` works (recursive)
- [ ] defn functions are visible in subsequent expressions and in other defn bodies

### Number notation

- [ ] `1/4` parses to the rational pair `[1 4]`
- [ ] `3/8` parses to `[3 8]`
- [ ] `(slow 1/4 (seq :bd :sd))` works (rational passed to `slow`)
- [ ] `120bpm` desugars to `(bpm 120)` and sets the tempo

### Collection helpers

- [ ] `conj`, `count`, `nth`, `first`, `rest`, `empty?`, `cons`, `concat`, `vec`, `range`, `map`, `mod`, `identity`, `apply` all work
- [ ] `(apply seq (list :bd :sd :hh))` creates a pattern from a dynamic list

### Composition

- [ ] defsynth + defmacro + loop/recur all coexist without conflicts
- [ ] Existing patterns (`seq`, `stack`, `fast`, `slow`, `rev`, `every`, etc.) are unaffected
- [ ] Existing parameter functions (`amp`, `attack`, `decay`, `pan`) compose with synth patterns
- [ ] All existing tests still pass (`npm run test:core`)

### UI

- [ ] All new tokens receive syntax highlighting as built-in names
- [ ] All new functions/forms appear in autocomplete with correct detail strings
- [ ] Grammar regenerated (`npm run gen:grammar`) and `parser.js` committed
