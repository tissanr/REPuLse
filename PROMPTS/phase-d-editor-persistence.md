# Phase D — Editor Persistence

## Goal

Persist the editor content across page reloads using `localStorage`, so the user's
last session is always restored on revisit — the same behaviour Strudel.cc has.

---

## Implementation

All changes are confined to `app/src/repulse/app.cljs`.

### 1. Restore content on load

In `make-editor`, replace the hardcoded `initial-value` with a lookup:

```clojure
(defn- load-editor-content [fallback]
  (or (.getItem js/localStorage "repulse-editor") fallback))
```

Pass `(load-editor-content initial-value)` as the `:doc` in `EditorState.create`.

### 2. Save content on every change

Add an `updateListener` extension to the CodeMirror config that writes to
`localStorage` whenever the document actually changes:

```javascript
// ClojureScript interop for updateListener
EditorView.updateListener.of(
  fn [update]
    (when (.-docChanged update)
      (.setItem js/localStorage "repulse-editor"
                (.. update -state -doc (toString)))))
```

In CLJS:

```clojure
(def save-listener
  (.of EditorView.updateListener
       (fn [^js update]
         (when (.-docChanged update)
           (.setItem js/localStorage "repulse-editor"
                     (.. update -state -doc (toString)))))))
```

Add `save-listener` to the `extensions` array in `make-editor`.

### 3. Wire it up in `init`

Change the `make-editor` call in `init` from:

```clojure
(make-editor container "(seq :bd :sd :bd :sd)" evaluate!)
```

to:

```clojure
(make-editor container (load-editor-content "(seq :bd :sd :bd :sd)") evaluate!)
```

---

## Storage key

`"repulse-editor"` — a plain string in `localStorage`. No versioning needed; if the
stored content is broken the user can clear it via DevTools or just edit it.

---

## What NOT to do

- No debounce needed — CodeMirror fires `updateListener` only on actual document
  changes, not on every keystroke tick; it is not expensive.
- No serialisation of cursor position, selection, or history — content only.
- No cross-tab sync (`storage` event) — out of scope.
- No migration from previous formats — this is a brand-new key.

---

## Files to change

```
app/src/repulse/app.cljs    — add save-listener, load-editor-content, wire up init
```

No other files need changes.

---

## Acceptance criteria

- [ ] Typing code and reloading the page restores exactly the typed content
- [ ] A fresh browser (no localStorage entry) shows the default `(seq :bd :sd :bd :sd)`
- [ ] Clearing `localStorage.removeItem("repulse-editor")` in DevTools and reloading
      restores the default
- [ ] No console errors on browsers that block localStorage (private mode Safari)
      — wrap in try/catch if needed
