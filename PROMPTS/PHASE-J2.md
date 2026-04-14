# Phase J2 — Contextual Insertion Buttons

## Goal

Add a point-and-click code scaffolding UX for new users who don't yet know what
functions are available. Small `+` buttons appear on mouse-hover at three positions
in the editor, each with distinct semantics:

| Hover target | Semantics | What it shows |
|---|---|---|
| **Opening `(`** | "Wrap this form with…" | Combinators that take a pattern: `fast`, `slow`, `rev`, `every`, `jux`, `off`, `sometimes`, `degrade`, `stack` (to layer alongside) |
| **Closing `)`** | "Chain after this…" | Per-event params (`amp`, `pan`, `decay`, `attack`, `release`), effects (`reverb`, `delay`, `filter`, `chorus`…), and threading — auto-wraps with `->>` if needed |
| **Empty line / line start** | "Start something new" | Top-level forms: `seq`, `stack`, `def`, `bpm`, `fx`, `samples!`, `bank`, `arrange` |

Each `(` wraps its **own** form — clicking the inner `(` of `(stack (seq :bd :sd))`
wraps just `(seq :bd :sd)`, not the outer `stack`.

**Before:**

User must know what to type, or wait for autocomplete after typing the first character.

**After:**

```lisp
;; User has:
(seq :bd :sd :bd :sd)

;; Hovers over opening `(` → clicks + → picks "fast" →
(fast 2 (seq :bd :sd :bd :sd))

;; Hovers over closing `)` → clicks + → picks "reverb" →
(->> (seq :bd :sd :bd :sd) (fx :reverb 0.3))

;; Hovers over closing `)` → clicks + → picks "amp" →
(->> (seq :bd :sd :bd :sd) (amp 0.8))

;; If `->>` already wraps the form, extend it instead of double-wrapping:
;; Before:
(->> (seq :bd :sd :bd :sd) (amp 0.8))
;; Picks "pan" on closing `)` →
(->> (seq :bd :sd :bd :sd) (amp 0.8) (pan -0.5))
```

---

## Background

### Editor infrastructure

The CodeMirror 6 editor already has:

- **Lezer parser** (`app/src/repulse/lisp-lang/repulse-lisp.grammar`) — full syntax
  tree with `List`, `BuiltinName`, `Symbol`, `Keyword` nodes. `(` and `)` are
  anonymous tokens within `List` nodes.
- **ViewPlugin pattern** — `rainbow.js` iterates brackets via `syntaxTree().iterate()`;
  the same traversal can detect `(` / `)` positions.
- **Decoration.widget()** — available but not yet used. This is the right API for
  injecting small DOM elements (the `+` buttons) at specific document positions.
- **hoverTooltip** — `hover.js` already shows doc tooltips on hover; the `+` button
  is a different interaction (click, not hover) so they won't conflict.
- **BUILTINS array** (`completions.js`) — 179 entries with labels, types, and detail
  strings. Can be reused as the data source for dropdown items.
- **DOCS map** (`hover.js`) — signatures and examples for 70+ builtins.
- **domEventHandlers** — `EditorView.domEventHandlers()` facet for attaching
  `mousemove`/`mouseleave` listeners at the editor level.

### Key CodeMirror APIs

- `ViewPlugin.fromClass()` — lifecycle-managed plugin with `update()` method
- `Decoration.widget({ widget, side })` — inject a DOM element at a position
- `WidgetType` — base class for widget DOM creation/update/comparison
- `syntaxTree(state)` — access the Lezer parse tree
- `EditorView.decorations` — provide decoration set from a ViewPlugin
- `StateField` / `StateEffect` — if persistent state is needed for dropdown open/close

---

## Files to change

| File | Change |
|------|--------|
| `app/src/repulse/lisp-lang/insert-helper.js` | **New** — ViewPlugin, WidgetType subclasses, dropdown logic, context categorisation, insertion transforms |
| `app/src/repulse/lisp-lang/insert-categories.js` | **New** — categorised function lists (wrap, chain, top-level) with labels and templates |
| `app/src/repulse/lisp-lang/index.js` | Register `insertHelper` extension in `lispLanguage` extensions array |
| `app/src/repulse/lisp-lang/completions.js` | Export `BUILTINS` array (currently only the completion source is exported) |
| `app/public/css/main.css` | Styles for `.insert-plus-btn`, `.insert-dropdown`, `.insert-dropdown-item`, `.insert-category-header` |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | No change |

---

## Design details

### 1. Hover detection

A `ViewPlugin` listens to `mousemove` events on the editor DOM. On each event:

1. Map mouse coordinates to a document position via `view.posAtCoords()`.
2. Use `syntaxTree(state).resolveInner(pos)` to find the node under the cursor.
3. If the character at `pos` is `(` and it's the start of a `List` node → show
   **wrap** `+` button via `Decoration.widget()` placed just before the `(`.
4. If the character at `pos` is `)` and it's the end of a `List` node → show
   **chain** `+` button via `Decoration.widget()` placed just after the `)`.
5. If the line is empty or whitespace-only → show **new form** `+` button at column 0.

Debounce at ~50ms to avoid excessive tree lookups. Hide the button on `mouseleave`
or when the cursor moves away from the target.

### 2. The `+` button widget

A `WidgetType` subclass that renders a small, semi-transparent `+` icon. Specs:

- 16×16px, `opacity: 0.4` → `opacity: 0.8` on hover
- Positioned inline (not absolute) so it doesn't break CodeMirror's layout
- `pointer-events: auto` (CodeMirror widgets need this)
- `aria-label` for accessibility: "Insert function", "Wrap expression", "Chain after"
- Click handler opens the dropdown

### 3. Dropdown

A simple `<div>` positioned below/above the `+` button (using `getBoundingClientRect`).
Not a CodeMirror tooltip — a plain DOM overlay, since it needs click interaction.

Structure:
```html
<div class="insert-dropdown">
  <div class="insert-category-header">Transformations</div>
  <div class="insert-dropdown-item" data-fn="fast">
    <span class="insert-fn-name">fast</span>
    <span class="insert-fn-detail">(fast n pat) — speed up</span>
  </div>
  ...
</div>
```

- Closes on: Escape, click-outside, item selection, scroll
- Max height with overflow scroll if list is long
- Styled consistently with oneDark theme

### 4. Context categories

**Opening `(` — wrap functions** (things that take a pattern and return a pattern):

| Category | Functions |
|---|---|
| Speed | `fast`, `slow` |
| Structure | `rev`, `palindrome`, `cat` |
| Conditional | `every`, `sometimes`, `often`, `rarely`, `degrade` |
| Spatial | `jux`, `off` |
| Layering | `stack` (wraps as `(stack original (seq ...))` — adds a parallel layer) |
| Combinators | `euclidean` (rewrap), `late`, `early` |

**Closing `)` — chain functions** (things appended via `->>`):

| Category | Functions |
|---|---|
| Amplitude | `amp`, `attack`, `decay`, `release` |
| Spatial | `pan` |
| Effects | `reverb`, `delay`, `filter`, `chorus`, `phaser`, `tremolo`, `overdrive`, `bitcrusher`, `compressor`, `dattorro` |
| Transitions | `tween` |

**Empty line — top-level forms:**

| Category | Functions |
|---|---|
| Patterns | `seq`, `stack`, `pure`, `cat`, `euclidean` |
| Binding | `def`, `defn`, `defsynth`, `defmacro` |
| Control | `bpm`, `stop`, `clear!`, `reset!` |
| Audio | `fx`, `samples!`, `bank`, `sound` |
| Arrangement | `arrange`, `play-scenes` |

### 5. Insertion logic

**Wrap (opening `(`):**
1. Find the `List` node's `from` and `to` positions (including parens).
2. Wrap: `view.dispatch({ changes: [{ from, insert: "(fast 2 " }, { from: to, insert: ")" }] })`.
3. Place cursor after the inserted argument (e.g., after `2` in `(fast 2 ...)`).

**Chain (closing `)`):**
1. Find the `List` node that this `)` closes.
2. Walk up the tree: is this `List` already inside a `->>` form?
   - **Yes** → find the `->>` list's closing `)`, insert ` (amp 0.8)` before it.
   - **No** → wrap the whole form: `(->> original (amp 0.8))`.
3. For effects, insert `(fx :reverb 0.3)` instead of bare `(reverb 0.3)`.

**New form (empty line):**
1. Insert template at cursor: e.g., `(seq )` with cursor between the space and `)`.

### 6. Template strings

Each dropdown item has a template that defines what gets inserted:

```javascript
{ label: "fast",    template: "(fast 2 _)",    cursor: 6  }  // cursor on "2"
{ label: "amp",     template: "(amp 0.8)",      cursor: 5  }  // cursor on "0.8"
{ label: "reverb",  template: "(fx :reverb 0.3)", cursor: 14 }  // cursor on "0.3"
```

`_` in wrap templates marks where the original expression goes.

---

## Definition of done

- [ ] `+` button appears on mouse-hover over any opening `(` of a `List` node
- [ ] `+` button appears on mouse-hover over any closing `)` of a `List` node
- [ ] `+` button appears on empty lines or at the start of whitespace-only lines
- [ ] Opening-paren `+` opens a dropdown of wrap functions (pattern transformers, combinators)
- [ ] Closing-paren `+` opens a dropdown of chain functions (params, effects)
- [ ] Empty-line `+` opens a dropdown of top-level forms
- [ ] Selecting a wrap function wraps the clicked form correctly
- [ ] Selecting a chain function inserts via `->>` threading (or extends existing `->>`)
- [ ] Selecting a top-level form inserts a template on the empty line
- [ ] Cursor is placed at the most useful position after insertion (on the first argument)
- [ ] Dropdown closes on Escape, click-outside, or selection
- [ ] `+` buttons are visually subtle: low opacity, small, consistent with oneDark theme
- [ ] Feature does not interfere with typing, autocomplete, hover docs, or rainbow brackets
- [ ] No new npm dependencies — uses only CodeMirror core APIs
- [ ] Works in Chrome, Firefox, and Safari

---

## Out of scope

- Keyboard shortcut to trigger the dropdown (could be added later)
- Touch/mobile support (desktop is the target — Phase O would address mobile)
- Snippet-style tab-stop placeholders (just cursor positioning for now)
- Undo groups the insertion as a single undo step (CodeMirror does this by default)

---

## Open questions (resolved)

1. **Each `(` wraps its own form** — confirmed. Clicking the inner `(` of a nested
   expression wraps only that inner form.
2. **Closing-paren shows combinators too** — confirmed. `stack` appears in the
   wrap list so users can layer patterns alongside.
3. **Custom dropdown vs CodeMirror autocomplete** — custom dropdown chosen for
   better control over categorisation and visual design.
