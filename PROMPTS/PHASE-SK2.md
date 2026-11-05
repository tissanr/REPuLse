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
   **Decided (2026-11-05): full copies, content-addressed, in IndexedDB.** Each distinct
   project state (the sanitized v3 map from `session/build-session-snapshot`) is stored
   once under its content hash; history entries and named snapshots reference blobs by
   hash, so repeated identical states cost almost nothing. No deltas — a corrupt delta
   breaks every later entry. Storage is IndexedDB via a small hand-written wrapper (no
   external library), separate from the D2 `"repulse-session"` localStorage slot, which
   stays unchanged. History is bounded by count (500) **and** total size (~20 MB),
   evicting oldest entries first and garbage-collecting unreferenced blobs; named
   snapshots are never evicted automatically. All history/snapshot logic (append,
   collapse, evict, GC, restore) is pure over a storage-adapter protocol: unit tests use
   an in-memory adapter, the app uses the IndexedDB adapter.
2. Should previewing a history entry play it (audition, like AI5's planned audition) or only show code?
   **Decided (2026-11-05): code only.** Preview shows the entry's code read-only with a
   line diff against the current buffer, plus Restore. No audio audition — extending the
   S4 snippet sandbox to whole multi-buffer projects is out of scope; audition belongs to AI5.
3. Cloud sync now or later?
   **Decided (2026-11-05): later.** Local-only in SK2. Supabase sync needs a table, RLS,
   a migration and auth flow (human-in-loop) and is a separate follow-up phase.
4. Should snapshots be shareable as links (reuse `share!`)?
   **Decided (2026-11-05): yes, minimal.** A "Copy link" action per named snapshot calls
   the existing `session/share-link` with the snapshot's stored project state (it already
   takes the snapshot as an argument). If the link is too long or invalid, surface
   `share-link`'s existing `:error` result; no new backend or URL format.

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
