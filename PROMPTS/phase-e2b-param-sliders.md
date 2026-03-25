# Phase E2b — Parameter Sliders

## Goal

Make numeric parameters in the session dashboard **interactive sliders** that
provide real-time audio feedback AND update the corresponding number in the
editor code. The dashboard is the control surface; the code stays the source
of truth.

```
──── Tracks ────────────────────────
▶ :drums
    amp     [========--]  0.80      ← drag to 0.65, code updates live
    decay   [===-------]  0.20
    pan     [-----●----] -0.30

▶ :bass
    amp     [======----]  0.60
    attack  [=---------]  0.05
    synth   :saw
```

Dragging the `amp` slider for `:drums`:
1. **Immediately** updates the audio (via `param-overrides` — same as MIDI CC)
2. **Simultaneously** rewrites `(amp 0.80)` → `(amp 0.65)` in the editor

When you stop dragging, the code reflects exactly what you hear. Hit Alt+Enter
and nothing changes — the code is already in sync.

---

## Background

### Editor access

The CodeMirror 6 editor instance is accessible from `app.cljs`. It exposes:
- `editor-view` — the `EditorView` instance
- `editor-view.state.doc` — the current document text
- `editor-view.dispatch({changes: ...})` — apply edits programmatically
- The Lezer parse tree via `syntaxTree(state)` — gives AST access for finding
  parameter positions

### Parameter extraction (Phase E2)

Phase E2 extracts per-track params by querying cycle 0 of each pattern:

```clojure
(extract-track-params pattern)
;; → {:amp 0.8 :decay 0.2 :pan -0.3}
```

This tells us **what** params exist and their values, but not **where** in the
code they are defined. E2b needs the source position too.

### Param override mechanism (Phase N1)

Phase N1 introduces `midi/param-overrides` — an atom that overrides event params
at dispatch time:

```clojure
(defonce param-overrides (atom {}))
;; {:decay 0.3}                    — global override
;; {:bass {:decay 0.3}}            — track-scoped override
```

Sliders write to this same atom for instant audio feedback, then update the code
for persistence.

---

## Design

### Source position tracking

To update code, we need to find where `(amp 0.8)` lives in the editor. Two approaches:

**Option A — Lezer parse tree search (recommended):**

Walk the parse tree to find call expressions matching `(param-name number)`. The
Lezer grammar already parses REPuLse-Lisp into a tree with `CallExpression`,
`BuiltinName`, and `Number` nodes.

```javascript
// Find position of a parameter's number literal in the editor
function findParamPosition(view, trackName, paramName) {
  const tree = syntaxTree(view.state);
  let result = null;

  tree.iterate({
    enter(node) {
      // Look for CallExpression nodes where first child is paramName
      // and contains a Number literal
      if (node.type.name === "CallExpression") {
        const text = view.state.doc.sliceString(node.from, node.to);
        // Match (paramName <number>) pattern
        const match = text.match(
          new RegExp(`^\\(${paramName}\\s+(-?[\\d.]+)`)
        );
        if (match) {
          // Find the number's position within the node
          const numStr = match[1];
          const numStart = node.from + text.indexOf(numStr, paramName.length + 1);
          result = { from: numStart, to: numStart + numStr.length, value: numStr };
        }
      }
    }
  });

  return result;
}
```

For track-scoped params, first find the `(play :trackname ...)` expression, then
search within that subtree.

**Option B — Regex on document text:**

Simpler but fragile. Search for `(param-name <number>)` near the track definition.
Falls apart with complex nesting.

**Decision: Option A** — the parse tree is already available, and it handles nesting
and formatting variations correctly.

### Slider → audio → code flow

```
User drags slider
  │
  ├──→ Write to param-overrides atom (immediate audio change)
  │      same mechanism as MIDI CC (Phase N1)
  │
  └──→ Find param position in parse tree
       │
       └──→ editor-view.dispatch({changes: {from, to, insert: newValue}})
            (code updates live as slider moves)
```

Both happen on every `input` event (not just `change`/release), so audio and code
track the slider in real time.

### Slider widget rendering

Each numeric param in the tracks section gets a slider:

```html
<div class="ctx-param-slider">
  <span class="ctx-param-key">amp</span>
  <input type="range" min="0" max="1" step="0.01" value="0.80"
         data-track="drums" data-param="amp" />
  <span class="ctx-param-val">0.80</span>
</div>
```

Param-specific ranges and steps:

| Param | Min | Max | Step | Curve |
|-------|-----|-----|------|-------|
| `amp` | 0.0 | 1.0 | 0.01 | linear |
| `pan` | -1.0 | 1.0 | 0.01 | linear |
| `attack` | 0.001 | 2.0 | 0.001 | exponential |
| `decay` | 0.01 | 5.0 | 0.01 | exponential |
| `release` | 0.01 | 5.0 | 0.01 | exponential |
| `rate` | 0.1 | 4.0 | 0.1 | linear |
| `begin` | 0.0 | 1.0 | 0.01 | linear |
| `end` | 0.0 | 1.0 | 0.01 | linear |

For exponential params (attack, decay, release), the slider position maps
exponentially so that the low end has finer control:

```javascript
// Slider position (0–1) → param value (exponential)
function expScale(position, min, max) {
  return min * Math.pow(max / min, position);
}

// Param value → slider position (inverse)
function expPosition(value, min, max) {
  return Math.log(value / min) / Math.log(max / min);
}
```

### Non-slider params

Some params are not numeric and don't get sliders:
- `:synth` — shown as a keyword, not editable via slider
- `:bank` — shown as a keyword
- `:loop` — boolean, could be a toggle checkbox (stretch goal)

These are displayed as static text, same as E2.

### Code update precision

When updating the code, preserve the user's formatting:
- Match the number of decimal places the user wrote (if they wrote `0.8`, update
  to `0.6`, not `0.60000000`)
- Round to a sensible precision based on the step size
- Don't reformat surrounding whitespace or parentheses

```javascript
function formatValue(value, originalStr) {
  // Match decimal places of original
  const match = originalStr.match(/\.(\d+)/);
  const decimals = match ? match[1].length : 0;
  return value.toFixed(Math.max(decimals, 2));
}
```

### Undo integration

CodeMirror 6 dispatch creates undo history entries. To avoid flooding undo with
every slider micro-movement, group rapid changes:

```javascript
// Use a transaction annotation to group slider drags
const sliderDrag = Annotation.define();

// On slider input (during drag):
view.dispatch({
  changes: { from, to, insert: newValue },
  annotations: [sliderDrag.of(true), Transaction.addToHistory.of(false)]
});

// On slider change (on release):
view.dispatch({
  changes: { from, to, insert: newValue },
  annotations: [Transaction.addToHistory.of(true)]
});
```

This way, only the final value enters undo history — the user can Ctrl+Z back
to the pre-drag value in one step.

### Interaction with code re-evaluation

When the user hits Alt+Enter:
1. Code is evaluated, patterns are rebuilt from the code text
2. `param-overrides` from the slider are no longer needed (the code already has
   the slider's value baked in)
3. Clear the override for that param after eval

Add a hook in the eval path:

```clojure
;; After successful eval, clear slider overrides (code is now in sync)
(reset! midi/param-overrides {})
```

This is safe because:
- If the user only moved sliders, eval picks up the updated code values
- If MIDI CC is also active, the CC listener will re-populate overrides on next knob move

---

## Implementation

### 1. `app/src/repulse/app.cljs` — slider rendering in dashboard

Extend `render-tracks-section` from Phase E2 to render `<input type="range">`
for each numeric param:

```clojure
(defn- render-param-slider [track-name param-key value]
  (let [range-info (get param-ranges param-key)
        {:keys [min max step]} range-info
        slider-val (if (:exponential range-info)
                     (exp-position value min max)
                     (/ (- value min) (- max min)))]
    (str "<div class='ctx-param-slider'>"
         "<span class='ctx-param-key'>" (name param-key) "</span>"
         "<input type='range' min='0' max='1' step='0.001'"
         " value='" slider-val "'"
         " data-track='" (name track-name) "'"
         " data-param='" (name param-key) "'"
         " />"
         "<span class='ctx-param-val'>" (.toFixed value 2) "</span>"
         "</div>")))
```

### 2. `app/src/repulse/app.cljs` — slider event handling

Attach event delegation on the panel container (not per-slider, since
innerHTML is rebuilt on re-render):

```clojure
(defn- init-slider-handlers! []
  (.addEventListener ctx-el "input"
    (fn [e]
      (when (= "range" (.. e -target -type))
        (let [track  (keyword (.. e -target -dataset -track))
              param  (keyword (.. e -target -dataset -param))
              pos    (js/parseFloat (.. e -target -value))
              range  (get param-ranges param)
              scaled (if (:exponential range)
                       (exp-scale pos (:min range) (:max range))
                       (+ (:min range) (* pos (- (:max range) (:min range)))))]
          ;; 1. Update audio immediately
          (swap! midi/param-overrides assoc-in [track param] scaled)
          ;; 2. Update code
          (update-code-param! track param scaled)
          ;; 3. Update value display
          (set! (.-textContent (.. e -target -nextElementSibling))
                (.toFixed scaled 2)))))))
```

### 3. `app/src/repulse/editor.cljs` or inline — code update via parse tree

```javascript
// In a JS module or via CLJS interop with CodeMirror

import { syntaxTree } from "@codemirror/language";

export function updateParamInCode(view, trackName, paramName, newValue) {
  const tree = syntaxTree(view.state);
  const doc = view.state.doc.toString();

  // Find the (play :trackName ...) block, then the (paramName N) within it
  let paramPos = null;
  let inTrack = trackName === null; // null = search globally

  tree.iterate({
    enter(node) {
      // Detect (play :trackName ...) scope
      if (!inTrack && node.type.name === "CallExpression") {
        const text = doc.slice(node.from, node.to);
        if (text.match(new RegExp(`^\\(play\\s+:${trackName}\\b`))) {
          inTrack = true;
        }
      }

      // Find (paramName <number>) within scope
      if (inTrack && node.type.name === "Number" && !paramPos) {
        // Check if parent call is the param function
        const parent = node.node.parent;
        if (parent) {
          const parentText = doc.slice(parent.from, parent.to);
          const re = new RegExp(`^\\(${paramName}\\s+`);
          if (re.test(parentText)) {
            const original = doc.slice(node.from, node.to);
            paramPos = { from: node.from, to: node.to, original };
          }
        }
      }
    }
  });

  if (paramPos) {
    const formatted = formatValue(newValue, paramPos.original);
    view.dispatch({
      changes: { from: paramPos.from, to: paramPos.to, insert: formatted },
      annotations: Transaction.addToHistory.of(false)
    });
  }
}

function formatValue(value, originalStr) {
  const match = originalStr.match(/\.(\d+)/);
  const decimals = match ? match[1].length : 2;
  return value.toFixed(Math.max(decimals, 2));
}
```

### 4. `app/src/repulse/app.cljs` — clear overrides on eval

In the Alt+Enter handler, after successful evaluation:

```clojure
;; Clear slider/MIDI overrides — code now has the baked-in values
(reset! midi/param-overrides {})
```

### 5. `app/public/css/main.css` — slider styling

```css
.ctx-param-slider {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 1px 0;
  height: 20px;
}

.ctx-param-slider .ctx-param-key {
  color: #5c6370;
  font-size: 10px;
  width: 48px;
  text-align: right;
}

.ctx-param-slider input[type="range"] {
  -webkit-appearance: none;
  appearance: none;
  flex: 1;
  height: 4px;
  background: #3e4451;
  border-radius: 2px;
  outline: none;
}

.ctx-param-slider input[type="range"]::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #56b6c2;
  cursor: pointer;
}

.ctx-param-slider input[type="range"]::-moz-range-thumb {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: #56b6c2;
  cursor: pointer;
  border: none;
}

.ctx-param-slider .ctx-param-val {
  color: #d19a66;
  font-size: 10px;
  width: 36px;
  text-align: right;
}

/* Pan slider: center marker */
.ctx-param-slider[data-param="pan"] input[type="range"] {
  background: linear-gradient(to right,
    #3e4451 0%, #3e4451 49%,
    #5c6370 49%, #5c6370 51%,
    #3e4451 51%, #3e4451 100%);
}
```

---

## Files to change

| File | Change |
|------|--------|
| `app/src/repulse/app.cljs` | Slider rendering in track params; event delegation for `input`/`change`; code update via parse tree; clear overrides on eval |
| `app/public/css/main.css` | Slider styling (thumb, track, layout) |

If the parse tree code update is done in JavaScript for cleaner CodeMirror interop:

| File | Change |
|------|--------|
| `app/src/repulse/lisp-lang/code-update.js` | **New** — `updateParamInCode()`, `findParamPosition()` |

No changes to `packages/core/`, `packages/lisp/`, or `packages/audio/`.
No new Lisp built-ins. No grammar changes.

---

## Dependencies

- **Phase E2** — the dashboard must show per-track params before they can become sliders
- **Phase N1** (soft) — uses the same `param-overrides` atom. If N1 isn't implemented yet, E2b creates the atom itself

Build order: E2 → E2b → N1 (N1 benefits from E2b's override atom, and E2b benefits from E2's param display)

---

## Definition of done

### Slider rendering

- [ ] Each numeric param in the tracks section has a slider
- [ ] Slider range matches the param type (0–1 for amp, -1–1 for pan, etc.)
- [ ] Exponential scaling for time params (attack, decay, release) — fine control at low end
- [ ] Current value displayed next to slider
- [ ] Non-numeric params (synth, bank) shown as static text, no slider
- [ ] Pan slider has a visible center marker

### Audio feedback

- [ ] Dragging a slider changes the sound immediately (no need to re-eval)
- [ ] Audio updates on every slider `input` event, not just on release
- [ ] Multiple sliders can be adjusted independently

### Code update

- [ ] Dragging a slider rewrites the corresponding number literal in the editor
- [ ] Code update finds the correct position via the Lezer parse tree
- [ ] Track-scoped: slider for `:drums amp` updates the number inside `(play :drums ...)`
- [ ] Preserves decimal precision of the original number
- [ ] Does not reformat surrounding code (whitespace, parens, etc.)
- [ ] Undo (Ctrl+Z) reverts to the pre-drag value in one step (micro-movements not in undo history)
- [ ] Final value on slider release is added to undo history

### Eval sync

- [ ] After Alt+Enter, param overrides are cleared (code is the source of truth)
- [ ] Re-evaluating with the slider's baked-in value produces the same sound
- [ ] MIDI CC overrides (Phase N1) are also cleared on eval, re-applied on next knob move

### Edge cases

- [ ] If the param is not found in the code (e.g. set via MIDI only), slider still controls audio but no code update
- [ ] If the user edits the code manually, next re-render picks up the new value
- [ ] Panel re-render does not reset slider position mid-drag (event delegation, not per-element listeners)
- [ ] Works with `->>` threading: `(->> pat (amp 0.8) (decay 0.3))` — finds the right number

### No regressions

- [ ] All existing core tests pass (`npm run test:core`)
- [ ] Basic playback: `(seq :bd :sd :bd :sd)`
- [ ] Effects: `(fx :reverb 0.3)`
- [ ] Track management: `(play :drums ...)`, `(mute! :drums)`
- [ ] Alt+Enter still evaluates correctly
- [ ] Undo/redo in editor still works for manual edits
- [ ] Context panel sections from E2 still render correctly
