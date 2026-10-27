# Phase SONG4 — Multi-Buffer Projects

> **Spec status:** outline — expand into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🤖 auto — buffer storage, `use` resolution order and persistence are unit-testable; tab switching and cross-buffer navigation are Playwright-testable.

**Milestone:** M2 — Full songs in code
**Depends on:** SONG1 (song form), SONG3 (navigation, extended here across buffers).

## Goal

Let a song grow beyond one file. A **project** has one **main buffer** — the song file
with the arrangement at the bottom — and any number of named buffers (drums, bass,
chords, …). The main buffer loads others explicitly at the top with `use`, so
"evaluate top to bottom, define before use" stays true across the whole project.
A single-file song is simply a project that only has a main buffer.

```lisp
;; Before: one editor buffer, everything in one file.

;; After (PROPOSAL)
;; buffer "drums"
(track :kick  (seq :bd :_ :bd :_))
(track :snare (seq :_ :sd :_ :sd))

;; buffer "main"
(use :drums :bass)          ; evaluates buffers "drums" then "bass" first
(song
  (section :verse 8 [:kick :snare :bass])
  (section :chorus 8 :all))
```

## Background

- Single editor: `app/src/repulse/ui/editor.cljs` (`make-editor`, `editor-view` atom).
- Evaluation: `app/src/repulse/eval_orchestrator.cljs` `evaluate!` evaluates one code
  string; resets global FX and `builtins/seen-tracks` per evaluation (duplicate track
  names are an error within one evaluation).
- Persistence: `app/src/repulse/session.cljs` stores a single `:editor` string in the
  `"repulse-session"` v2 blob (`build-session-snapshot`, `load-session`, `migrate-legacy!`);
  `specs/sanitize-session-v2` validates it. Share links: `share!` in `app/src/repulse/app.cljs`
  (`#v2:` URL hash encoding).
- Snippets: S1 local library and S3 community snippets (`app/src/repulse/snippets.cljs`) are
  single-code-string units.
- Command bar (`make-cmd-editor`) is separate and not persisted — unchanged.

## Scope

- Project model: ordered named buffers, one marked `main`; create / rename / delete / switch (tabs).
- `(use :name …)` evaluates the named buffers in order before continuing; cycles detected
  and reported as an eval error; a buffer used twice is evaluated once per evaluation.
- Duplicate track detection spans the whole project evaluation.
- Errors point to the right buffer and position (switch tab + diagnostic).
- Alt+Enter in any buffer evaluates the project from `main` (decide — see questions).
- Session schema v3 with buffers; migration from v2 (existing editor text → `main`).
- Share links / snippets: decide whether they carry full projects or only the main buffer.
- SONG3 outline + go-to-definition work across buffers.

## Open design questions

1. Alt+Enter semantics: always evaluate from `main`, or evaluate the current buffer only
   (fast iteration) with a separate "evaluate project" key?
2. Does `use` share one environment (simple) or give each buffer a namespace (`drums/kick`)?
3. Share-link size limits with multiple buffers — URL hash vs. Supabase-backed project share.
4. Import/export a project as a single file (e.g. `.repulse` EDN) for backup and version control?

## Definition of done

- [ ] Session v3 schema with buffers; a v2 session migrates to a project whose `main` buffer holds the old editor text (unit test).
- [ ] `(use :drums)` makes `drums`' defs and tracks available to `main`; evaluation order is deterministic (unit test with a fake buffer store).
- [ ] Circular `use` reports a clear eval error naming the cycle.
- [ ] A duplicate track name across two buffers is reported as an error pointing to the second definition's buffer.
- [ ] Creating, renaming, switching and deleting buffers works and persists across reload (Playwright).
- [ ] An error in a used buffer switches to that buffer and shows the diagnostic at the right position (Playwright).
- [ ] Go-to-definition jumps into another buffer (Playwright).
- [ ] A project with only `main` behaves exactly like today's single-buffer editor (existing tests pass).
- [ ] `docs/USAGE.md` documents projects, buffers and `use`; `use` follows CLAUDE.md Rule 3.

## What NOT to do

- No collaborative editing of buffers — that is O3 (deferred).
- No per-buffer audio routing or per-buffer BPM.
- No MIDI capture into buffers — SK3 uses this phase's API.
- Don't build a file-system/Git sync; local + existing share/snippet mechanisms only.
