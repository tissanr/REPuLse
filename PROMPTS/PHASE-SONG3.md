# Phase SONG3 — Editor Navigation: Outline Panel & Go-to-Definition

> **Spec status:** outline — expand into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🤖 auto — the outline extraction is a pure function over the source text and the UI behaviour (click → cursor moves) is verifiable with Playwright / the preview browser.

**Milestone:** M2 — Full songs in code
**Depends on:** SONG1 (song/section forms to list). Should be extended by SONG4 for cross-buffer jumps.

## Goal

Songs are written top to bottom with the arrangement at the bottom, so the file gets
long. Make it navigable: an **outline panel** listing every `def`, `track`, `section`
and the `song` block, where clicking an entry jumps to it; and **go-to-definition** —
clicking (or Cmd/Ctrl-clicking) a name inside the arrangement jumps to where it is defined.

```lisp
;; PROPOSAL — no new Lisp syntax; this is editor behaviour.
(def kick (seq :bd :_ :bd :_))       ; ← outline: def kick
(track :drums (stack kick snare))    ; ← outline: track :drums
...
(song
  (section :verse 8 [:drums :bass])  ; Cmd-click :drums → jumps to (track :drums …)
  ...)
```

```text
Outline
  def   kick          L1
  def   snare         L2
  track :drums        L4
  track :bass         L9
  song               L40
    section :intro   L41
    section :verse   L42
```

## Background

- Editor: `app/src/repulse/ui/editor.cljs` (`make-editor`, `editor-view` atom, decorations
  via `highlights-field`); CodeMirror 6.
- Lezer grammar: `app/src/repulse/lisp-lang/repulse-lisp.grammar` → generated `parser.js`;
  the syntax tree is available in CodeMirror and is the natural source for the outline.
- Reader: `packages/lisp/src/repulse/lisp/reader.cljs` attaches `{:source {:from :to}}`
  to forms (`SourcedVal` / metadata) — an alternative source of positions.
- `app/src/repulse/lisp_patcher.cljs` — a paren-aware tokenizer already used for slider
  patching; may be reusable for a lightweight scan.
- Hover docs: `app/src/repulse/lisp-lang/hover.js` — existing pattern for mapping a
  cursor position to a symbol.
- AI5's planned `def-ranges` (PROMPTS/PHASE-AI5.md) describes the same extraction —
  share one implementation if both land.

## Scope

- Pure outline extraction: list of `{:kind :name :from :to :children}` for top-level
  `def`, `defn`, `defsynth`, `track`, `song`, `section`.
- Outline panel UI (collapsible, like the existing context/snippet panels), updated on
  edit (debounced), highlighting the entry under the cursor.
- Click entry → move cursor + scroll into view.
- Go-to-definition for symbols (`kick`) and track keywords (`:drums`) via Cmd/Ctrl-click
  and a keyboard shortcut; "back" to return to the previous position.
- Works while code is invalid (partial parse) — outline degrades gracefully.

## Design decisions

Questions 1, 3 and 4 were decided by the project owner on 2026-10-29. Question 2 is
**still open**: it waits for a UI design (brief: [docs/design/SONG3-outline-panel-brief.md](../docs/design/SONG3-outline-panel-brief.md)).
Do not expand or implement this spec until question 2 has a **Decided:** line.

1. **Source of truth: the Lezer syntax tree.** The outline is extracted from the
   CodeMirror/Lezer tree already in the editor (`syntaxTree(state)`), not from the Lisp
   reader. Lezer keeps a partial tree on invalid code, which the "works while code is
   invalid" requirement needs. The extraction is a pure function over the tree/text that
   returns `{:kind :name :from :to :children}`, so it can be unit-tested.
   **Decided:** Lezer syntax tree.

2. **Panel placement:** left sidebar vs. a section in the right context panel vs. tabs vs.
   something else. Waiting for the UI design described in the brief linked above.
   **Open — owner decision pending (UI design).**

3. **Shortcuts.** Go-to-definition: **Cmd/Ctrl-click** on a name and **F12** with the
   cursor on a name. Back: **Shift+F12** returns to the position before the last jump
   (a small position stack). Alt-click is not used (Alt is the eval modifier). Alt+← is not
   used because CodeMirror's `defaultKeymap` already binds it (word-left on macOS,
   syntax-left elsewhere). Ctrl+- is not used because it is browser zoom-out on
   Windows/Linux.
   **Decided:** Cmd/Ctrl-click + F12 to go, Shift+F12 to go back.

4. **Current playing section.** Yes: while a song plays, the outline marks the `section`
   entry that is sounding, using SONG1's existing `repulse.song/section-at-bar` together
   with the bar from `repulse.ui.context-panel/song-bar`. It refreshes on the same tick as
   the context panel and is visually distinct from the "cursor inside" highlight.
   **Decided:** highlight the playing section.

## Definition of done

- [ ] Pure extraction function has unit tests: nested forms, comments, strings containing parens, unbalanced code.
- [ ] Outline lists all top-level `def`/`defn`/`defsynth`/`track` forms and `song` with child `section`s, in source order.
- [ ] Clicking an outline entry places the cursor at the form start and scrolls it into view (Playwright).
- [ ] Cmd/Ctrl-click on `:drums` inside a `section` jumps to `(track :drums …)` (Playwright).
- [ ] Cmd/Ctrl-click on a `def`'d symbol jumps to its `def`; unknown names do nothing and show no error.
- [ ] F12 on a name jumps like Cmd/Ctrl-click; Shift+F12 ("back") returns to the previous cursor position.
- [ ] While a song plays, the outline marks the currently sounding `section` entry.
- [ ] Outline updates within ~300 ms after typing and survives a syntax error elsewhere in the buffer.
- [ ] `docs/USAGE.md` documents the outline panel and shortcuts.

## What NOT to do

- No multi-buffer navigation — SONG4 extends this to jump across buffers.
- No rename/refactor tooling (rename symbol everywhere).
- No graphical timeline/arranger editing.
- Don't change Lisp evaluation semantics.
