# Phase AI5 — Variation Workflows & Live Audition

## Goal

Extend the AI3 tool-using agent with two tightly coupled capabilities: generating
multiple named code variants for any selection of the buffer (a single track, a group
of defs, the entire arrangement), and live-auditioning any variant by having the
scheduler play it immediately — without touching the editor buffer — so the user hears
the change before committing. When the user clicks "Use this", the edit writes to the
buffer and pushes to the AI4 undo stack unconditionally (regardless of the auto-apply
setting). The result is a *hear → compare → commit* loop that transforms AI generation
from guesswork into an audible creative dialogue.

```lisp
;; Before (AI3) — one proposal at a time; must apply blindly to hear it:
;; User:      "Give me a more driving kick pattern"
;; Assistant: proposes (seq :bd :bd :_ :bd) via diff overlay
;; User:      applies, listens, undoes, asks again...

;; After (AI5) — three auditionable variants, side by side:
;; User:      "Give me three variations of the kick"
;; Variations panel shows tabs: A | B | C
;; Clicking A → scheduler plays (seq :bd :bd :_ :bd) live, buffer untouched
;; Clicking B → scheduler switches to (euclidean 5 8 :bd), buffer still untouched
;; User clicks "Use B" → edit writes to buffer, pushed to undo stack, panel closes
```

---

## Background

### AI3 tool layer (dependency)

`app/src/repulse/ai/tools.cljs` — typed tool registry written in AI3. AI5 adds two new
entries: `generate_variations` and `audition_track`. Both follow the same
`{:description … :params … :side-effects … :execute …}` shape as existing tools.

`app/src/repulse/ai/agent_loop.cljs` — bounded go-loop from AI3. The new tools are
dispatched through the same loop; no changes to the loop itself are needed.

### AI4 undo stack (dependency for "Use this")

`app/src/repulse/ai/undo.cljs` (new in AI4) — `record-pre-edit!` + `revert-turn!`. The
"Use this" button calls `record-pre-edit!` before applying the chosen variant so the
commit is rollback-able.

### Scheduler state

`app/src/repulse/audio.cljs` — `scheduler-state` atom shape (line ~517):

```clojure
{:playing?     false
 :tracks       {}    ; keyword → Pattern   ← real patterns
 :muted        #{}
 :cycle        0
 :cycle-dur    2.0
 :lookahead    0.2
 :interval-id  nil
 :on-beat      nil
 :on-event     nil
 :on-fx-event  nil
 :tween-state  {}}
```

AI5 adds a parallel `:audition-tracks` map with the same `keyword → Pattern` shape.
`schedule-cycle!` (line ~549) iterates `(:tracks state)` — it gains a one-line
override: for each track-name check `(:audition-tracks state)` first.

`play-track!` (line ~667) is the public entry point for registering patterns. Audition
does **not** call `play-track!` — it writes directly to the `:audition-tracks` key so
the real track pattern is preserved and the change never fires a `propose_edit`.

### Reader source ranges

`packages/lisp/src/repulse/lisp/reader.cljs` — `read-form` wraps every top-level form
in either a `SourcedVal` (for primitives) or with `{:source {:from N :to N}}` metadata
(for collections). The buffer-scope pass uses this to build a name → char-range index.

`packages/lisp/src/repulse/lisp/eval.cljs` — top-level `def` special form binds a name
to a value. The `(def name expr)` list form is what the buffer-scope pass identifies.

### Assistant panel

`app/src/repulse/ui/assistant_panel.cljs` — DOM helper pattern with `(el id)` for
getElementById lookups; messages rendered via `render-panel!`. The variations strip is
a new `<div id="repulse-variations">` inserted directly below the chat history.

---

## Implementation

### 1. `app/src/repulse/ai/buffer_scope.cljs` — def-name → char-range index

Parse all top-level `def`/`defn`/`defsynth`/`defmacro` forms in a buffer string and
return a map of `{name {:from N :to N}}`. Used by `generate_variations` so the agent
can say "give me variants of :kick" and the tool finds the exact char range without the
user specifying offsets.

```clojure
(ns repulse.ai.buffer-scope
  (:require [repulse.lisp.reader :as reader]))

(defn def-ranges
  "Returns {name {:from N :to N}} for every top-level def* form in buffer."
  [buffer-text]
  (let [forms (reader/read-all buffer-text)]
    (into {}
      (for [form forms
            :let [src (or (:source (meta form))
                          (when (instance? reader/SourcedVal form) (:source form)))
                  head (when (list? form) (first form))
                  name-form (when (list? form) (second form))
                  sym (cond
                        (instance? reader/SourcedVal head) (:v head)
                        (symbol? head) head
                        :else nil)]
            :when (and (symbol? sym)
                       (#{'def 'defn 'defsynth 'defmacro} sym)
                       src)]
        [(name (cond
                 (instance? reader/SourcedVal name-form) (:v name-form)
                 (symbol? name-form) name-form
                 :else name-form))
         src]))))
```

`reader/read-all` is a thin wrapper over the existing `read-form` loop — add it to
`reader.cljs` if it does not already exist:

```clojure
(defn read-all [text]
  (let [r (make-reader text)]
    (loop [forms []]
      (skip-ws-comments r)
      (if (>= (:pos @r) (count text))
        forms
        (recur (conj forms (read-form r)))))))
```

### 2. `app/src/repulse/ai/variations.cljs` — variations state

```clojure
(ns repulse.ai.variations
  (:require [repulse.audio :as audio]))

;; {:variants [{:label "A" :code "..." :from N :to N} ...]
;;  :active   nil  ; label of currently auditioning variant
;;  :track-name nil} ; keyword of auditioned track, or nil for whole-buffer
(defonce state (atom nil))

(defn set-variants! [variants track-name]
  (reset! state {:variants variants :active nil :track-name track-name}))

(defn audition-variant!
  "Swap the scheduler's audition layer to the pattern for label.
   Does NOT touch the editor buffer."
  [label parsed-pattern track-name]
  (swap! state assoc :active label)
  (swap! audio/scheduler-state assoc-in [:audition-tracks track-name] parsed-pattern))

(defn cancel-audition!
  "Remove the audition layer; original track resumes on next cycle."
  []
  (when-let [{:keys [track-name]} @state]
    (swap! audio/scheduler-state update :audition-tracks dissoc track-name))
  (reset! state nil))
```

### 3. `app/src/repulse/audio.cljs` — audition override in `schedule-cycle!`

Add `:audition-tracks {}` to the `scheduler-state` initial value, then add a one-line
override inside `schedule-cycle!` where patterns are resolved:

```clojure
;; In scheduler-state initial value — add one key:
:audition-tracks {}    ; keyword → Pattern  (temporary, set by AI5 variations)

;; In schedule-cycle!, replace:
(doseq [[track-name pattern] tracks]
;; with:
(let [all-tracks (merge tracks (:audition-tracks state))]
  (doseq [[track-name pattern] all-tracks]
```

No other change to `audio.cljs` is needed. The audition pattern silently shadows
the real pattern for exactly one track at a time; `cancel-audition!` restores the
original by removing the key from `:audition-tracks`.

### 4. `app/src/repulse/ai/tools.cljs` — two new tools

Add to the tool registry (AI3 file):

```clojure
:generate_variations
{:description
 "Generate N code variants for a named def or the full buffer.
  Returns a list of {label code from to} objects."
 :params {:target {:type "string"
                   :description "Def name to vary (e.g. 'kick'), or 'buffer' for full text."}
          :n      {:type "integer" :default 3
                   :description "Number of variants to generate (2–4)."}}
 :side-effects #{:none}
 :execute generate-variations!}

:audition_track
{:description
 "Temporarily swap one track's pattern to a given REPuLse-Lisp expression.
  The scheduler plays the new pattern live; the buffer is not changed.
  Call cancel_audition to revert."
 :params {:track  {:type "string" :description "Track keyword name, e.g. 'kick'"}
          :code   {:type "string" :description "REPuLse-Lisp expression to evaluate"}}
 :side-effects #{:audio}
 :execute audition-track!}

:cancel_audition
{:description "Remove the active audition; the original track pattern resumes."
 :params {}
 :side-effects #{:audio}
 :execute (fn [_] (variations/cancel-audition!) {:ok true})}
```

`generate-variations!` calls the AI model a second time (or extracts multiple
`<variant>` blocks from a single structured response) and returns:

```clojure
(defn generate-variations! [{:keys [target n]}]
  ;; 1. Build prompt asking model to produce N variants of target
  ;; 2. Parse structured response into [{:label "A" :code "…" :from N :to N} …]
  ;; 3. Call (variations/set-variants! variants track-keyword)
  ;; 4. Trigger panel render
  ;; 5. Return {:ok true :count (count variants)}
  )
```

The model prompt for variations instructs the assistant to return each variant wrapped
in `<variant label="A">…code…</variant>` XML so the tool can parse them deterministically.

`audition-track!` evaluates the code string, resolves the track keyword, and calls
`variations/audition-variant!`:

```clojure
(defn audition-track! [{:keys [track code]}]
  (let [track-kw (keyword track)
        result   (repulse.eval-orchestrator/evaluate-pattern-only! code)]
    (if (:ok result)
      (do (variations/audition-variant! nil (:pattern result) track-kw)
          {:ok true})
      {:ok false :error (:error result)})))
```

`evaluate-pattern-only!` is a new thin wrapper (or re-use of `eval_preview` path from
AI3) that returns `{:ok true :pattern <Pattern>}` without scheduling audio.

### 5. `app/src/repulse/ui/assistant_panel.cljs` — variations strip

Render a `<div id="repulse-variations">` strip when `variations/state` is non-nil.
The strip contains:
- One `<button class="variant-tab [active]">` per variant (labelled A, B, C…)
- A "Use this" button and a "Cancel" button

```clojure
(defn- render-variations! []
  (when-let [panel (el "repulse-variations")]
    (if-let [{:keys [variants active track-name]} @variations/state]
      (set! (.-innerHTML panel)
        (str "<div class='variations-strip'>"
             (str/join
               (map (fn [{:keys [label code from to]}]
                      (str "<button class='variant-tab"
                           (when (= label active) " active") "'"
                           " data-label='" label "'"
                           " data-code='" (escape-html code) "'"
                           " data-from='" from "'"
                           " data-to='" to "'>"
                           label "</button>"))
                    variants))
             "<button class='variant-use'>Use this</button>"
             "<button class='variant-cancel'>Cancel</button>"
             "</div>"))
      (set! (.-innerHTML panel) ""))))
```

Event delegation on the strip:
- Click `.variant-tab` → call `audition-track!` for the tab's code/track, mark active
- Click `.variant-use` → `ai4-undo/record-pre-edit!`, `apply-edit! from to code`,
  `cancel-audition!`, close strip
- Click `.variant-cancel` → `cancel-audition!`, close strip

---

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/ai/buffer_scope.cljs` | **New** — `def-ranges`, `read-all` wrapper |
| `app/src/repulse/ai/variations.cljs` | **New** — variations state, `set-variants!`, `audition-variant!`, `cancel-audition!` |
| `app/src/repulse/ai/tools.cljs` | Add `generate_variations`, `audition_track`, `cancel_audition` tools (AI3 file) |
| `app/src/repulse/audio.cljs` | Add `:audition-tracks {}` to `scheduler-state`; merge with `:tracks` in `schedule-cycle!` |
| `app/src/repulse/ui/assistant_panel.cljs` | Add `render-variations!`, strip DOM, event delegation for tab/use/cancel |
| `app/src/repulse/eval_orchestrator.cljs` | Add `evaluate-pattern-only!` returning `{:ok :pattern}` without audio scheduling |
| `packages/lisp/src/repulse/lisp/reader.cljs` | Add `read-all` if not present |

---

## Definition of done

- [ ] `generate_variations` tool returns 2–4 code variants for a named def; e.g. asking
      for variations of `(def kick (seq :bd :_ :bd :_))` produces at least two distinct
      alternative `seq`/`euclidean` expressions for the kick pattern
- [ ] Variations panel appears below the chat after `generate_variations` resolves;
      tabs labelled A, B, C (and D if 4 variants) are rendered and clickable
- [ ] Clicking tab A switches the scheduler to play variant A live; clicking tab B
      switches to variant B within the same cycle; buffer text is unchanged throughout
- [ ] Only one track is ever auditioning at a time; clicking a new tab cancels the
      previous audition before applying the new one
- [ ] `audition_track` tool can be called directly by the agent with an arbitrary
      REPuLse-Lisp expression; e.g. `{:track "kick" :code "(euclidean 5 8 :bd)"}`
      causes the kick to play the euclidean pattern live without editing the buffer
- [ ] `cancel_audition` tool and the "Cancel" button both restore the original track
      within one scheduler cycle; `scheduler-state :audition-tracks` is empty after cancel
- [ ] Clicking "Use this" on an auditioning variant: (a) calls `record-pre-edit!` from
      the AI4 undo stack, (b) applies the variant code to the correct `from`/`to` range
      in the editor, (c) calls `cancel-audition!`, (d) closes the variations panel —
      in that order
- [ ] "Use this" works correctly regardless of the AI4 auto-apply toggle state (it
      always writes to the buffer and always records to the undo stack)
- [ ] `buffer_scope/def-ranges` returns correct character ranges for a buffer containing
      `def`, `defn`, `defsynth`, and `defmacro` forms; ranges round-trip: slicing the
      buffer text at `[:from :to]` reproduces the original form
- [ ] `generate_variations` with `target "buffer"` generates variants for the full
      buffer text, not a single named def; the panel's "Use this" replaces the entire
      buffer content
- [ ] Variations panel is absent (DOM node exists but has no children) when
      `variations/state` is nil; re-evaluating the buffer does not break this invariant
- [ ] `npm run test` passes; `npx shadow-cljs compile app` succeeds with no warnings

---

## What NOT to do

- Do not implement concurrent auditions — only one track may be in the `:audition-tracks`
  map at a time; cancel the previous before starting a new one.
- Do not call `play-track!` from the audition path — it replaces the real pattern in
  `:tracks`. Write only to `:audition-tracks`.
- Do not add a per-variation audio preview that re-evaluates code on every tab hover —
  audition happens only on tab click to avoid rapid scheduler thrashing.
- Do not implement variation generation for MIDI, sample stems, or audio regions —
  REPuLse generates code only; all variants are REPuLse-Lisp expressions.
- Do not build a persistent variation history or branch management UI here — that scope
  belongs to a future phase. Each `generate_variations` call replaces the previous state.
- Do not add new Lisp built-ins (no `(variations ...)` form); all variation control is
  through the AI tool layer and panel UI.
