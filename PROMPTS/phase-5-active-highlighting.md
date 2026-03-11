# REPuLse — Active Code Highlighting (Phase 5) (delivered)

## Context

REPuLse is a browser-based live coding instrument. Phases 1–3 delivered the pattern engine,
WASM synthesis, and AudioWorklet. This phase adds **active code highlighting**: as a pattern
plays, the editor highlights the specific values in the source code that are generating the
current sound — exactly like [Strudel.cc](https://strudel.cc) does.

When the user writes:

```lisp
(seq :bd :sd :hh :sd)
```

…the `:bd` token flashes when the kick plays, `:sd` flashes on beats 2 and 4, and `:hh`
flashes on beat 3. This creates a visual feedback loop between the code and the music.

Current state after Phase 3:
- `packages/lisp/reader.cljs` — recursive-descent parser, **no source position tracking**
- `packages/lisp/eval.cljs` — environment-based evaluator, values carry no source metadata
- `packages/core/core.cljs` — pattern events: `{:value v :whole span :part span}`, no `:source`
- `app/src/repulse/audio.cljs` — scheduler fires `play-event` at event time, no UI callback
- `app/src/repulse/app.cljs` — CodeMirror 6 editor, no decoration infrastructure yet

---

## Goal for this session

By the end of this session:

1. The reader attaches source character ranges `{:from N :to N}` to every parsed form
2. The evaluator propagates source ranges from literals into pattern events as a `:source` key
3. When the scheduler fires an event, it schedules a UI highlight at the correct wall-clock time
4. The editor flashes a CSS highlight class on the source range for ~120 ms
5. All existing patterns continue to work unchanged — source tracking is purely additive

---

## Architecture

```
reader.cljs          attach {:from N :to N} metadata to each parsed form via with-meta

eval.cljs            literals (keywords, numbers) → preserve :source from form metadata
                     seq / stack / pure calls → :source flows into the event map

core.cljs            events gain an optional :source key — no changes to pattern logic

audio.cljs           play-event receives :source from event
                     → schedules setTimeout(highlight-fn, delay-ms)

app.cljs             highlight-fn applies CodeMirror StateEffect to mark the range
                     → removes it after 120ms via a follow-up dispatch
```

---

## Step 1 — Reader: source position metadata

Extend the reader state to expose the current position, then wrap each form in `with-meta`.

**`packages/lisp/src/repulse/lisp/reader.cljs`**

The reader state record is already `{:src src :pos (atom 0)}`. Capture `@pos` before
reading each form, and after, wrap the result with source metadata:

```clojure
(defn read-form [r]
  (skip-ws-comments r)
  (let [from @(:pos r)
        form (read-form* r)     ; rename current read-form body to read-form*
        to   @(:pos r)]
    (if (or (nil? form) (= form ::eof))
      form
      (try
        (with-meta form {:source {:from from :to to}})
        (catch :default _
          ;; primitives like booleans/nil can't carry metadata — wrap in a box
          form)))))
```

> **Note on `with-meta`:** In ClojureScript, `with-meta` only works on values that implement
> `IMeta` — collections, symbols, and tagged literals. Plain numbers, booleans, strings, and
> `nil` do not support metadata. For those types, store source in a side-channel map instead
> (see Step 2).

### Side-channel source map

Because primitive literals can't carry metadata, use a `WeakMap`-style side channel: a plain
CLJS atom containing a map from object identity to source range. For primitives (number,
string, boolean, nil), use a separate per-evaluation source-map atom passed through the reader:

```clojure
;; In read-all / read-one, pass a source-map atom alongside r
(defn read-all
  ([src] (read-all src (atom {})))
  ([src source-map]
   (let [r {:src src :pos (atom 0) :source-map source-map}]
     ...)))
```

Inside `read-form`, after computing `[from to]`:
- If the form supports metadata (symbol, list, vector): use `(with-meta form {:source {:from from :to to}})`
- Otherwise (number, string, boolean, nil): store `{form-object {:from from :to to}}` in `:source-map`
  using a generated unique key:

```clojure
;; Generate a tagged box so primitives carry source
(defrecord SourcedPrimitive [value source])
```

Return a `SourcedPrimitive` record instead of the raw primitive when source tracking is active.
The evaluator unwraps it transparently.

---

## Step 2 — Evaluator: propagate source into events

**`packages/lisp/src/repulse/lisp/eval.cljs`**

Extend `eval-form` to unwrap `SourcedPrimitive` and thread the source range into values.

```clojure
(defn- source-of [form]
  (or (:source (meta form))
      (when (instance? repulse.lisp.reader/SourcedPrimitive form)
        (:source form))))

(defn- unwrap [form]
  (if (instance? repulse.lisp.reader/SourcedPrimitive form)
    (:value form)
    form))
```

In `eval-form`, for literals, attach the source range to the returned value using metadata
where possible, or return a `SourcedValue` wrapper:

```clojure
;; In the literal branch:
(or (number? ...) (string? ...) (keyword? ...) ...)
(let [v    (unwrap form)
      src  (source-of form)]
  (if src
    (with-source v src)      ; keyword/symbol → with-meta; number → SourcedValue wrapper
    v))
```

Define `with-source` as a helper:

```clojure
(defn with-source [v src]
  (try
    (with-meta v {:source src})
    (catch :default _
      ;; number, nil, boolean — return as-is; caller must extract source separately
      v)))
```

For the initial environment (`make-env`), wrap `seq*` and `stack*` to extract sources from
their arguments before passing raw values to `core`:

```clojure
"seq"  (fn [& vs]
         (let [srcs (mapv #(or (:source (meta %)) nil) vs)
               vals (mapv unwrap-value vs)]
           (core/seq* vals srcs)))
```

---

## Step 3 — Core: events carry `:source`

**`packages/core/src/repulse/core.cljs`**

`seq*` currently takes `[value]`. Extend it to accept an optional `sources` vector:

```clojure
(defn seq*
  ([values] (seq* values nil))
  ([values sources]
   (let [n (count values)]
     {:query
      (fn [{:keys [start end]}]
        (let [cycle-start (rat-floor start)]
          (mapcat
           (fn [i]
             (let [v   (nth values i)
                   src (when sources (nth sources i nil))
                   w-start (rat-add [cycle-start 1] (rat-mul [i 1] [1 n]))
                   w-end   (rat-add w-start [1 n])
                   whole   {:start w-start :end w-end}]
               (when (rats-overlap? whole {:start start :end end})
                 [(cond-> {:value v :whole whole
                           :part (trim-span whole start end)}
                    src (assoc :source src))])))
           (range n))))})))
```

The `:source` key is purely additive — existing consumers that don't know about it are
unaffected. `stack*`, `fmap`, `fast`, `slow`, `rev`, `every` all pass events through and
should preserve `:source` when building new events:

```clojure
;; In fmap:
(fn [ev]
  (let [new-val (f (:value ev))]
    (cond-> (assoc ev :value new-val)
      ;; preserve source if f doesn't add a new one
      (nil? (:source (meta new-val))) (update :source #(or (:source ev) %)))))
```

---

## Step 4 — Scheduler: schedule highlight callbacks

**`app/src/repulse/audio.cljs`**

Add an optional `on-event` callback to the scheduler state:

```clojure
(def scheduler-state
  (atom {:playing?    false
         :pattern     nil
         :cycle       0
         :cycle-dur   2.0
         :lookahead   0.2
         :interval-id nil
         :on-beat     nil
         :on-event    nil}))   ; NEW — (fn [source t]) called per event
```

In `schedule-cycle!`, after `play-event`, schedule the highlight:

```clojure
(when (> t (.-currentTime ac))
  (play-event ac t (:value ev))
  (when-let [on-event (:on-event state)]
    (when-let [src (:source ev)]
      (let [delay-ms (max 0 (* 1000 (- t (.-currentTime ac))))]
        (js/setTimeout #(on-event src) delay-ms))))
  ...)
```

Update `start!` to accept `on-event-fn`:

```clojure
(defn start! [pattern on-beat-fn on-event-fn]
  ...
  (swap! scheduler-state assoc
         :on-event on-event-fn
         ...))
```

---

## Step 5 — Editor: CodeMirror highlight decorations

**`app/src/repulse/app.cljs`**

Add CodeMirror 6 decoration infrastructure. Import the required CM6 APIs:

```clojure
["@codemirror/state" :refer [EditorState StateEffect StateField]]
["@codemirror/view"  :refer [EditorView Decoration keymap lineNumbers]]
```

Define a state effect and field for active highlights:

```clojure
(def add-highlight    (StateEffect/define))
(def remove-highlight (StateEffect/define))

(def highlight-field
  (StateField/define
    #js {:create (fn [] (.-none Decoration))
         :update (fn [decos tr]
                   (-> decos
                       (.map (.-changes tr))
                       (apply-effects (.-effects tr))))
         :provide (fn [f] (.from EditorView.decorations f))}))
```

The highlight mark uses a CSS class `active-event`:

```clojure
(def active-mark (Decoration/mark #js {:class "active-event"}))
```

Implement `highlight-range!`:

```clojure
(defn highlight-range! [{:keys [from to]}]
  (when-let [view @editor-view]
    (let [doc-len (.. view -state -doc -length)
          from'   (min from doc-len)
          to'     (min to   doc-len)]
      (when (< from' to')
        ;; Apply decoration
        (.dispatch view
          #js {:effects #js [(.of add-highlight
                                  (.range active-mark from' to'))]})
        ;; Remove after 120ms
        (js/setTimeout
          (fn []
            (.dispatch view
              #js {:effects #js [(.of remove-highlight
                                      #js {:from from' :to to'})]}))
          120)))))
```

Pass `highlight-range!` to `audio/start!`:

```clojure
(audio/start! val on-beat highlight-range!)
```

Add CSS for the highlight effect — in `app/public/style.css`:

```css
.active-event {
  background-color: rgba(255, 200, 50, 0.35);
  border-radius: 2px;
  transition: background-color 0.1s ease-out;
}
```

---

## CSS flash behaviour

The highlight should feel snappy:
- Applied immediately when the event fires (at its scheduled wall-clock time)
- Held for **120 ms** (one 8th note at 125 BPM; fast enough to feel like a flash)
- Removed cleanly, no accumulation of stale decorations

Multiple overlapping highlights (e.g. stacked patterns) are independent — each has its own
120 ms timer.

---

## Testing checklist

Verify each scenario manually in the browser:

| Expression | Expected highlight behaviour |
|---|---|
| `(seq :bd :sd :hh :sd)` | Each keyword flashes individually at its beat |
| `(seq 220 440 330 550)` | Each number flashes at its beat |
| `(stack (seq :bd :bd) (seq :hh :hh))` | Both lines flash simultaneously |
| `(fast 2 (seq :bd :sd))` | Keywords flash at double speed |
| `(every 4 (fast 2) (seq :bd :sd))` | On every 4th bar the highlights run faster |
| `(def kick (seq :bd :_)) (stack kick kick)` | The `:bd` inside the `def` flashes |
| No WASM / JS fallback mode | Highlights still work (source tracking is independent of audio backend) |
| Editing the buffer mid-playback | Highlights disappear / don't crash (stale ranges clamped to doc length) |

---

## Definition of Done

- [ ] Reader attaches `{:from N :to N}` source ranges to all parsed forms
- [ ] Pattern events carry `:source` for literal values in `seq` expressions
- [ ] `(seq :bd :sd :hh)` — each keyword flashes in the editor when it plays
- [ ] `(seq 220 440)` — each number flashes when it plays
- [ ] `(stack ...)` — highlights fire simultaneously for layered patterns
- [ ] `(fast 2 ...)` — highlights run at the correct speed
- [ ] Editing source mid-playback does not crash (out-of-range `:source` values clamped)
- [ ] Highlight duration is ~120 ms — visible but not distracting
- [ ] No regressions: all existing patterns play correctly, all core tests pass

---

## What NOT to do in this phase

- No changes to the Lisp language semantics
- No source tracking for `let` / `fn` / `def` body expressions — only top-level values in
  `seq`, `stack`, `pure` calls need highlighting for the MVP
- No multi-cursor or range-select feedback
- No changes to the audio engine or scheduler timing
- No persistence or serialisation of source maps
