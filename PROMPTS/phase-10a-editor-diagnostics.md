# Phase 10a — Editor Error Diagnostics

## Goal

When evaluation fails, show a **red squiggle underline on the offending code** in the
CodeMirror editor. The user sees exactly which form caused the problem without reading
the output bar. A tooltip on the underlined region shows the full error message.

```
(synth :saw (seq :c3) (amp 0.7))
                       ^^^^^^^^
                       Error: amp must be chained with ->>
```

On the next successful evaluation all diagnostics are cleared.

---

## Background

### What exists today

- **Reader** (`packages/lisp/src/repulse/lisp/reader.cljs`) — returns `{:error "…"}`
  for malformed input. Token positions are tracked internally but not surfaced in the
  error map.
- **Evaluator** (`packages/lisp/src/repulse/lisp/eval.cljs`) — wraps every value in a
  `SourcedVal` record that carries `{:from N :to N}` byte offsets into the original
  source string. Runtime errors thrown by built-ins are caught at the call site.
  The current return format is `{:error "message"}` with no position info.
- **`evaluate!`** (`app/src/repulse/app.cljs`) — calls `lisp/eval-string`, reads
  `{:error}`, calls `(set-output! err :error)` and `(clear-highlights!)`. Nothing
  writes back to the editor.
- **CodeMirror 6** is already installed and used. The `@codemirror/lint` package is
  **not yet installed**.

### SourcedVal position tracking

Every value produced by the reader is wrapped:
```clojure
(defrecord SourcedVal [value source])
;; source = {:from 12 :to 27}  — byte offsets in the original string
```

When a built-in function throws, the call site in the evaluator knows which `SourcedVal`
was being reduced. This position can be added to the error result.

### CodeMirror linting API

```javascript
import { setDiagnostics } from "@codemirror/lint";

// Create a diagnostic
const diag = { from: 22, to: 31, severity: "error", message: "amp must be chained with ->>" };

// Push it into the editor view
view.dispatch(setDiagnostics(view.state, [diag]));

// Clear all diagnostics
view.dispatch(setDiagnostics(view.state, []));
```

`setDiagnostics` is a transaction creator — it returns a `TransactionSpec` that is
dispatched to the view. The lint extension draws red squiggles under the range and
shows the message in a tooltip on hover. No polling / background linting is needed —
we drive it imperatively from `evaluate!`.

---

## Design

### 1. Enrich error results with source position

**Reader errors** — the reader already knows the byte offset where it failed.
Add `:from` / `:to` to the reader's `{:error}` map:

```clojure
;; Before
{:error "Unmatched '('"}

;; After
{:error "Unmatched '('" :from 34 :to 35}
```

**Evaluator errors** — when a built-in throws a `js/Error`, the evaluator catches it.
The catch site has access to the `SourcedVal` that was being applied. Add the source
range to the returned error map:

```clojure
;; In the catch clause of eval-apply:
(catch :default e
  {:error (.-message e) :from (:from source) :to (:to source)})
```

When no position is available (e.g., the error is structural, not tied to one form),
omit `:from` / `:to` — the caller treats it as a whole-document error and shows only
the output bar message.

### 2. `@codemirror/lint` integration

Install the package:
```bash
npm install @codemirror/lint
```

Add to the editor's extension list in `make-editor` (`app/src/repulse/app.cljs`):

```clojure
(ns repulse.app
  (:require ["@codemirror/lint" :refer [lintGutter]]))

;; In make-editor extensions vector:
(lintGutter)
```

`lintGutter` adds the red-dot gutter column that appears when diagnostics are present.
The squiggle underlines appear without it, but the gutter gives an extra visual anchor.

### 3. Drive diagnostics from `evaluate!`

```clojure
(ns repulse.app
  (:require ["@codemirror/lint" :refer [setDiagnostics lintGutter]]))

(defn- set-diagnostics!
  "Push a single error diagnostic into the editor, or clear all diagnostics."
  [view from to message]
  (let [diags (if (and from to)
                #js [#js {:from from :to to :severity "error" :message message}]
                #js [])]
    (.dispatch view (setDiagnostics (.-state view) diags))))

(defn evaluate! [code]
  (ensure-env!)
  (swap! fx/chain (fn [c] (mapv #(assoc % :active? false) c)))
  (let [env    (assoc @env-atom "stop" (make-stop-fn))
        result (lisp/eval-string code env)]
    (if-let [err (:error result)]
      (do
        (clear-highlights!)
        ;; Show diagnostic in editor if position is available
        (when-let [view @editor-view]
          (set-diagnostics! view (:from result) (:to result) err))
        (set-output! err :error))
      (do
        ;; Clear any stale diagnostics on success
        (when-let [view @editor-view]
          (set-diagnostics! view nil nil nil))
        ;; … existing success path …
        ))))
```

### 4. Clear on keypress (optional UX polish)

Diagnostics that linger after the user starts editing feel stale. Add an
`EditorView.updateListener` that clears diagnostics when `docChanged` is true:

```clojure
(def ^:private clear-diag-listener
  (.of EditorView.updateListener
       (fn [^js update]
         (when (.-docChanged update)
           (.dispatch (.-view update)
                      (setDiagnostics (.-state update) #js []))))))
```

Add to the editor's extensions list alongside `save-listener`.

---

## Implementation

### Files to change

| File | Change |
|------|--------|
| `packages/lisp/src/repulse/lisp/reader.cljs` | Add `:from`/`:to` to reader error maps |
| `packages/lisp/src/repulse/lisp/eval.cljs` | Add `:from`/`:to` to caught runtime error maps |
| `app/src/repulse/app.cljs` | `set-diagnostics!` helper; call from `evaluate!`; add `lintGutter` + clear-on-edit listener to editor extensions |
| `package.json` (root) | Add `@codemirror/lint` dependency |

No changes to `packages/core/`, `packages/audio/`, grammar, completions, or CSS.

### Reader changes (reader.cljs)

Find every place that returns `{:error "…"}` and include the current reader position.
The reader already maintains a cursor position `pos` as it walks the token stream.
Add it to the error return:

```clojure
;; e.g. in parse-list:
{:error (str "Unmatched '(' at position " open-pos) :from open-pos :to (inc open-pos)}
```

For errors where only a start position is known, set `:to (inc :from)` so the
diagnostic covers at least one character (CodeMirror requires `from < to`).

### Evaluator changes (eval.cljs)

In `eval-apply` (the function application path), the catch block currently does:
```clojure
(catch :default e {:error (.-message e)})
```

Change to:
```clojure
(catch :default e
  (let [src (source-of fn-val)]  ; source of the outermost form
    {:error (.-message e)
     :from  (:from src)
     :to    (:to src)}))
```

`source-of` is already defined in `eval.cljs` — it extracts the `:source` map from a
`SourcedVal`. Use the source of the **function form** (the head of the list) so the
underline covers the function name, not all its arguments.

If `source-of` returns nil (e.g., the form is synthetic), omit `:from`/`:to` — the
caller handles nil gracefully.

---

## Edge cases

| Case | Behaviour |
|------|-----------|
| Error with no position info | Only output bar shows; no underline |
| Multi-expression editor content, error in second expr | Underline covers the failing form only |
| User edits after an error | Diagnostics cleared immediately on first keystroke |
| Eval succeeds | All diagnostics cleared |
| Reader error (unclosed paren) | Underline covers the unmatched delimiter |
| `from === to` | Set `to = from + 1` before dispatching (CodeMirror requires `from < to`) |

---

## Definition of done

### Error display

- [ ] `(synth :saw (seq :c3) (amp 0.7))` — red squiggle under `(amp 0.7)`, tooltip shows
  the transformer-chain error message
- [ ] `(seq :bd :sd` (unclosed paren) — red squiggle at or near the missing `)`, tooltip
  shows reader error message
- [ ] `(foo 1 2)` (undefined symbol) — red squiggle under `foo`, tooltip shows
  "undefined: foo — did you mean …?"
- [ ] Squiggle appears in the gutter column (red dot)
- [ ] Tooltip appears on hover over the squiggled region

### Lifecycle

- [ ] Squiggle disappears immediately when the user starts typing (first keystroke)
- [ ] Successful evaluation clears all diagnostics
- [ ] Multiple successive errors each show only the latest diagnostic

### No regressions

- [ ] Output bar still shows the error message text (existing behaviour kept)
- [ ] All existing `npm run test:core` tests pass
- [ ] Hot-reload (shadow-cljs watch) does not interfere with diagnostics state
- [ ] `@codemirror/lint` installation does not break the existing editor extensions
