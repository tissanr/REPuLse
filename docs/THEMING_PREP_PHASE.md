# REPuLse Theming

This document covers the current design token layer, what was shipped in the UI redesign, and what needs to happen to support switchable themes.

---

## What was built (UI redesign, 2026-04-29)

The full UI was reskinned to a darker, crisper variant of the oneDark palette. Changes:

| Area | Before | After |
|------|--------|-------|
| Body background | `#1a1a2e` | `#131928` |
| Panel background | `#16213e` | `#161d30` / `#10172a` |
| Border | `#0f3460` | `#1e2a45` |
| Text | `#e0e0e0` | `#abb2bf` |
| Dim/muted text | `#888` | `#3a4868` (near-invisible) + new `--mid: #5a6a8a` |
| Header | `padding: 12px 20px` | `height: 40px`, flat |
| Buttons | `border-radius: 4px` | `border-radius: 0` (flat) |
| Playing dot | green | cyan `#56b6c2` with glow |
| Editor bg | `#16213e` (oneDark default) | `#0d1421` |
| Gutter bg | `#282c34` (oneDark) | `#0d1421` (overridden with `!important`) |
| Line numbers | `#7d8799` (oneDark) | `#2a3860` |
| Footer | padded flex-wrap | 28px status bar |
| Cmd-bar | `--bg` | `#070d1c` |
| Context panel | 190px wide | 195px |
| Slider thumbs | blue `#61afef` | cyan `#56b6c2` |
| Snippet panel | appended after main-area | inside `.left-col` (inline overlay) |

---

## Current CSS token layer

All tokens live in `:root` in `app/public/css/main.css`.

### Semantic tokens

```css
--bg:       #131928   /* page / body background */
--bg-ed:    #0d1421   /* code editor background */
--bg-pn:    #161d30   /* panel background (snippet panel, track panel) */
--bg-bt:    #090f1e   /* bottom area (footer/cmd bar intention; currently hardcoded, see below) */
--bd:       #1e2a45   /* standard border */
--bd-dk:    #0e1628   /* darker border (cmd-bar, footer separators) */
--text:     #abb2bf   /* primary readable text */
--dim:      #3a4868   /* near-invisible decorative elements */
--mid:      #5a6a8a   /* muted-but-readable text (button labels, output) */
```

### Accent / syntax colours

```css
--pink:     #e94560   /* primary accent — logo, errors, play state, > prompt */
--cyan:     #56b6c2   /* secondary accent — symbols, playing dot, lib active */
--yellow:   #e5c07b   /* BPM, keywords */
--purple:   #c678dd   /* brackets, arrows, macros */
--green:    #98c379   /* strings, ctx-playing */
--orange:   #d19a66   /* numbers */
```

### Legacy aliases (kept for backwards compat — do not use in new code)

```css
--bg2:      #10172a   /* old panel bg — maps to header/context panel bg */
--border:   #1e2a45   /* alias for --bd */
--accent:   #e94560   /* alias for --pink */
--red:      #f44336   /* only used in error states, consider aliasing to --pink */
--font-mono: 'JetBrains Mono', 'Fira Code', 'Cascadia Code', monospace
```

---

## Hardcoded values that still need to become tokens

These values are scattered through the CSS and ClojureScript and will need extracting before themes can be switched at runtime.

| Value | Location | Should become |
|-------|----------|---------------|
| `#10172a` | header bg, context panel bg | `--bg-header` or reuse `--bg2` |
| `#1a2440` | header border | `--bd-header` or `--bd` |
| `#070d1c` | footer bg, cmd-bar bg | `--bg-bt` (already defined, just not used) |
| `#2a3555` | playing dot inactive, auth avatar bg | `--bg-dot` |
| `#2a3860` | line number color | `--color-gutter` |
| `#0d1421` | editor bg (also `--bg-ed`) | already `--bg-ed` — gutter override still hardcoded |
| `#282c34` | oneDark editor bg (being overridden) | n/a — lives inside npm package |
| Various in `context-panel.cljs` | inline styles for track colours | consider CSS custom props per track |

---

## The CodeMirror / editor theming gap

The editor uses `oneDark` from `@codemirror/theme-one-dark` as a CodeMirror Extension. This extension:

1. Sets the editor and gutter background to `#282c34`
2. Sets all syntax token colours (keywords, strings, comments, etc.)
3. Is applied as part of the editor's extension array in `editor.cljs`

### What we override with CSS `!important`

```css
.editor-container .cm-editor       { background: var(--bg-ed) !important; }
.editor-container .cm-scroller     { background: var(--bg-ed) !important; }
.editor-container .cm-gutters      { background: var(--bg-ed) !important;
                                     border-right: 1px solid var(--bd-dk) !important;
                                     color: #2a3860 !important; }
.editor-container .cm-lineNumbers  { color: #2a3860 !important; }
.editor-container .cm-activeLine   { background: rgba(255,255,255,.025) !important; }
.editor-container .cm-activeLineGutter { ... !important; }
```

### What we do NOT override (still from oneDark)

- Syntax token colours (keywords, strings, comments, numbers, etc.) — these are set by the oneDark StyleSpec and currently match the new palette closely enough
- Selection background
- Cursor colour
- Autocomplete/tooltip popup styling

### To support full editor theming

Replace the `oneDark` import with a custom `EditorView.theme({})` + `syntaxHighlighting(customHighlightStyle)` pair, built from CSS variables. This allows the editor colours to change when the theme switches. Rough approach:

```clojure
;; In editor.cljs — replace `oneDark` with:
(def repulse-theme
  (EditorView.theme
    #js {".cm-editor"        #js {:background "var(--bg-ed)"}
         ".cm-gutters"       #js {:background "var(--bg-ed)"
                                  :color "var(--dim)"
                                  :border-right "1px solid var(--bd-dk)"}
         ".cm-activeLine"    #js {:background "rgba(255,255,255,.025)"}
         ".cm-selectionBackground" #js {:background "rgba(86,182,194,.2)"}
         ...}))

;; Separate highlight style (token colours):
(def repulse-highlight
  (syntaxHighlighting
    (HighlightStyle.define
      #js [#js {:tag tags.comment      :color "var(--mid)"}
           #js {:tag tags.keyword      :color "var(--yellow)"}
           #js {:tag tags.string       :color "var(--green)"}
           #js {:tag tags.number       :color "var(--orange)"}
           ...
           ])))
```

This is a medium-sized refactor in `editor.cljs`. The `!important` overrides in CSS can be removed once this is done.

---

## Rainbow delimiters

Currently defined as static CSS classes cycling through 6 levels:

```css
.rainbow-1 { color: #e06c75; }  /* red    */
.rainbow-2 { color: #e5c07b; }  /* yellow */
.rainbow-3 { color: #98c379; }  /* green  */
.rainbow-4 { color: #56b6c2; }  /* cyan   */
.rainbow-5 { color: #c678dd; }  /* purple */
.rainbow-6 { color: #61afef; }  /* blue   */
```

The design prototype uses uniform `#e94560` (pink) for all parens. Options:

- **Keep rainbow** (current) — expressive, helps depth perception
- **All pink** — matches the design prototype exactly; simplify to `--pink`
- **Theme-switchable** — expose as `--rainbow-1` … `--rainbow-6` CSS vars

For theming, updating these 6 variables is sufficient — no code changes needed.

---

## Active-event highlight

```css
.active-event {
  background-color: rgba(255, 200, 50, 0.35);
  transition: background-color 0.08s ease-out;
}
```

Hardcoded yellow flash. Could become `--color-active-event: rgba(255,200,50,.35)`.

---

## What a theme object looks like (proposed)

```js
// themes/dark-deep.js  (current default)
export const darkDeep = {
  '--bg':       '#131928',
  '--bg-ed':    '#0d1421',
  '--bg-pn':    '#161d30',
  '--bg-bt':    '#090f1e',
  '--bd':       '#1e2a45',
  '--bd-dk':    '#0e1628',
  '--pink':     '#e94560',
  '--cyan':     '#56b6c2',
  '--yellow':   '#e5c07b',
  '--purple':   '#c678dd',
  '--green':    '#98c379',
  '--orange':   '#d19a66',
  '--text':     '#abb2bf',
  '--dim':      '#3a4868',
  '--mid':      '#5a6a8a',
};
```

Applying a theme at runtime is a single `document.documentElement.style.setProperty(k, v)` call per token. The editor theme (CodeMirror) needs the separate Extension approach described above to respond to the same variables.

---

## Remaining hardcoded colours in ClojureScript

These are inline `style=` values in CLJS UI files — they will not respond to CSS variable changes until refactored.

| File | What | Value |
|------|------|-------|
| `app.cljs` | logo "u" span | `color:#56b6c2` |
| `app.cljs` | logo "L" span | `color:#e94560` |
| `context_panel.cljs` | per-track colour dots | various per-track hex |
| `ui/timeline.cljs` | SVG rect fills | `var(--accent)` via JS |

The logo colours are intentional brand marks and probably don't need to theme. The context panel track colours are user-defined (`:break`, `:hat`, `:sub` each get a cycle colour) — those cycle colours could be exposed as `--track-color-1` … `--track-color-N`.

---

## Priority order for theming work

1. **Extract remaining hardcoded values to CSS variables** — replace `#070d1c`, `#10172a`, `#2a3555`, `#2a3860` etc. with proper token names. One-time CSS-only change.
2. **Replace `oneDark` extension with a custom CodeMirror theme** — built from CSS variables. Removes all `!important` overrides. Medium refactor in `editor.cljs`.
3. **Add `--rainbow-1` … `--rainbow-6` CSS variables** — allows palettes to recolour delimiters. Tiny CSS change.
4. **Write theme objects** — at minimum `dark-deep` (current) and optionally `dark-warm`, `light`. JSON or JS files, applied via a theme-switcher function.
5. **Theme switcher UI** — a button or command to cycle/choose themes. Could live in the context panel or a settings modal.
