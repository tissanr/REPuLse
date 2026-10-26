# Phase SK2 — Session Snapshots & Eval History

> **Spec status:** outline — expand into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🤖 auto — snapshot storage, history append/restore and diffing are pure or localStorage-backed and unit-testable; the history UI is Playwright-testable.

**Milestone:** M4 — Sketchpad
**Depends on:** SONG4 (snapshots must capture whole projects, not one buffer).

## Goal

Never lose an idea. Every evaluation is recorded in an append-only **eval history**
you can scroll back through, preview and restore; and you can save named **snapshots**
("take A", "take B") of the whole project to compare and return to.

```lisp
;; Before: one auto-saved session slot (D2); undo is per-editor only.

;; After (PROPOSAL)
(snapshot! "verse idea A")     ; save the whole project under a name
(snapshots)                    ; list saved snapshots
(restore! "verse idea A")      ; restore it (current state auto-saved first)
(history)                      ; open the eval-history panel
```

## Background

- D2 session persistence: `app/src/repulse/session.cljs` — a single
  `"repulse-session"` blob (`build-session-snapshot`, debounced `schedule-save!`,
  `load-session`, `wipe!`), validated by `specs/sanitize-session-v2`. SONG4 extends it to v3 with buffers.
- Evaluation entry point: `evaluate!` in `app/src/repulse/eval_orchestrator.cljs` —
  the natural hook to append a history entry (with success/error flag).
- AI4 undo: `app/src/repulse/ai/undo.cljs` and pure helpers in `app/src/repulse/ai/edit.cljs`
  (`push-snapshot`) — per-turn snapshots for AI edits; reuse ideas, don't merge stores.
- S2/S3 backend (Supabase) exists for snippets; cloud sync of snapshots is optional.
- Share links (`share!` in `app/src/repulse/app.cljs`) encode a session in the URL hash.

## Scope

- Eval history: append-only list of `{timestamp, project-state-hash, project-state, ok?}`;
  consecutive identical evaluations collapsed; bounded size with oldest-first eviction.
- History panel: list with time and a short diff summary; click to preview (load into a
  read-only view or audition), restore, or diff against current.
- Named snapshots of the full project (buffers, BPM, bank prefix, …); list, restore, delete, rename.
- Restoring always auto-snapshots the current state first.
- Storage in localStorage (or IndexedDB if size demands it); optional Supabase sync if logged in (?).

## Open design questions

1. Storage size: full copies vs. deltas; localStorage quota vs. IndexedDB.
2. Should previewing a history entry play it (audition, like AI5's planned audition) or only show code?
3. Cloud sync now or later?
4. Should snapshots be shareable as links (reuse `share!`)?

## Definition of done

- [ ] Each successful and failed evaluation appends one history entry; identical consecutive evals collapse (unit test).
- [ ] History is bounded (e.g. 500 entries) with oldest eviction (unit test).
- [ ] `(snapshot! "A")`, `(snapshots)`, `(restore! "A")` round-trip the full project state (unit test).
- [ ] `restore!` auto-snapshots the current state first; it is restorable (unit test).
- [ ] History panel lists entries and restores one by click (Playwright).
- [ ] Diff view shows added/removed lines between an entry and the current buffer (Playwright or unit test on the diff function).
- [ ] History and snapshots survive reload.
- [ ] New built-ins follow CLAUDE.md Rule 3; `docs/USAGE.md` documents history and snapshots.

## What NOT to do

- No A/B playback comparison UI beyond restore/preview (backlog "session comparison").
- No collaborative history (O3, deferred).
- Don't replace CodeMirror undo or the AI4 undo stack.
- No "export history as story" HTML (backlog).
