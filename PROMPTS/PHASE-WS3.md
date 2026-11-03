# Phase WS3 — Inline Sliders in Code: `(slider v min max)`

> **Spec status:** outline — expand into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🤖 auto — `slider` evaluation is a unit test, and the widget, drag write-back
> and ⌘K S wrapping are Playwright-testable.

**Milestone:** M2 — Full songs in code
**Depends on:** none hard. WS1 recommended (the inspector's `≋` toggle).

## Goal

Numbers you want to play with become draggable widgets right in the code, as in
*Outline Panel v2* (`design/outline-panel-v2/outline-v2.jsx`: `InlineSlider`,
`CodeLine`; frame 6 "Inline slider" in `Outline Panel v2.html`):

```lisp
;; before
(track :bass (->> (seq :e1 :g1) (synth :saw) (amp 0.7)))

;; after — (slider value min max) evaluates to value; the editor draws a fill bar
(track :bass (->> (seq :e1 :g1) (synth :saw) (amp (slider 0.7 0 1))))
```

- `(slider v min max)` evaluates to `v`: a plain number, so it works wherever a number works.
- The editor replaces the form with a 70×15 widget: 1 px border `#1e2a45`, cyan fill
  20 % (32 % on hover), and the value right-aligned in cyan. The cursor is `ew-resize`, and
  the tooltip shows the range.
- Dragging rewrites the first number in the code on every move (one undo step per
  drag) and hot-swaps without a full re-eval where possible (same mechanism as today's
  context-panel sliders). ⇧-drag = 10× finer, with a `×0.1` tag.
- When the text cursor enters the form, the widget turns back into text, so you can type.
- **⌘K S** (Ctrl-K S) on a number literal wraps it in `(slider …)`. The range comes from
  the enclosing param name: amp 0–1, pan −1–1, decay 0–2, freq 20–20000 (log), bpm 40–300;
  unknown names get 0–1. On an existing slider, ⌘K S unwraps it.
- WS1 inspector: params that are inline sliders get a cyan `≋` toggle (wrap/unwrap).

## Scope

- New built-in `slider` (Rule 3 checklist: grammar, `gen:grammar`, completions,
  builtin_meta, `gen:ai-docs`, hover docs). Validation: 3 numbers, min < max; v outside
  [min, max] is clamped with a warning.
- CodeMirror `ViewPlugin` + `WidgetType` replacing `(slider …)` ranges when the cursor
  isn't inside them. A log scale when min > 0 and max/min ≥ 100.
- Write-back through `lisp_patcher.cljs` (extend it to find the value literal inside a
  slider form) and the existing hot-swap path.

## Open design questions

1. Hot-swap vs re-eval on drag: **Recommendation:** reuse
   `eval-orchestrator/slider-patch-and-eval!` semantics (patch + debounced eval), which
   already gives smooth context-panel sliders. Measure before inventing a new path.
2. Log scale opt-in: **Recommendation:** automatic (min > 0 and max/min ≥ 100), plus an
   optional 4th arg `:log`/`:lin` to force it.
3. Number formatting: **Recommendation:** integers when max ≥ 100, otherwise 2 decimals
   (as in the design's `fmt`).

## Definition of done

- [ ] `(slider 0.7 0 1)` evaluates to 0.7. `(amp (slider 0.7 0 1) …)` gives the same
      events as `(amp 0.7 …)` (harness `evalToEvents`).
- [ ] Errors: wrong arity, non-numbers, min ≥ max → clear eval errors (exact texts in
      the protocol).
- [ ] Playwright: the widget renders. Dragging right raises the literal in the buffer. One
      undo restores it. ⇧-drag moves 10× less for the same mouse distance.
- [ ] Cursor inside the form → raw text, editable. Cursor leaves → widget again.
- [ ] ⌘K S on `0.25` inside `(decay 0.25)` → `(decay (slider 0.25 0 2))`. ⌘K S again unwraps.
- [ ] Grammar/completions/builtin_meta/ai-docs with no drift. USAGE.md documents `slider` and ⌘K S.
- [ ] `npm run test:all` + app compile pass. Test protocol in the PR.

## What NOT to do

- No MIDI-CC binding of sliders (N1 covers CC mapping).
- No sliders for non-numeric args.
- Don't remove the inspector/context sliders.
