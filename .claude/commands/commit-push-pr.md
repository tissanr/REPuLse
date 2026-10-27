---
allowed-tools: Bash(git *), Bash(gh *), Bash(npm run *), Bash(npx shadow-cljs compile *), Read, Glob, Grep
description: Commit all changes, push, and open a PR against dev
---

## Context

- Current git status: !`git status`
- Current git diff (staged and unstaged): !`git diff HEAD`
- Current branch: !`git branch --show-current`
- Recent commits on this branch: !`git log --oneline -10`
- Diff from dev: !`git diff origin/dev...HEAD --stat`
- Open PRs (match `headRefName` against the current branch): !`gh pr list --state open --json number,headRefName,baseRefName,url`

## Your task

Nothing is ever pushed to `dev` or `main` directly. Based on the changes above, do all of the following:

0. **Get off `dev` / `main`.** If the current branch is `dev` or `main`, create a branch first and commit there:
   `git switch -c <type>/<short-slug>` (e.g. `feat/phase-syn4`, `fix/solo-unmute`, `docs/usage-tracks`).

1. **Verify before committing** (CLAUDE.md, "Pre-push verification"). Run what applies to the changed files and stop with a report if anything fails — do not push a red branch:
   - Any ClojureScript change: `npm run test` **and** `npx shadow-cljs compile app`
   - Rust change (`packages/audio/`): `npm run test:rust`
   - Grammar change: run `npm run gen:grammar` and commit the regenerated `parser.js` / `parser.terms.js` with it
   - `builtin_meta.edn` change: run `npm run gen:ai-docs` and commit the regenerated `docs/ai/builtins.json` with it
   - Docs-only change: no checks needed

2. **Stage and commit** all relevant changes with a clear commit message. Follow conventional commit style (e.g. `feat:`, `fix:`, `refactor:`, `docs:`). Do not commit files containing secrets (.env, credentials, etc).

3. **Push** the branch to origin (with `-u` if not yet tracking).

4. **Create a PR** against `dev` using `gh pr create --base dev` (only `dev` → `main` release PRs target `main`) — unless the context shows an open PR for this branch already; then the push updates it and you only return its URL. The PR body MUST follow this exact structure:

```
## Summary
<1-3 bullet points describing what changed and why>

<any other sections: decisions, verification results, things to look at>

## Test protocol

Setup: `npm run dev:full`, open http://localhost:3000. Evaluate each snippet with
Alt+Enter (macOS Option+Enter); stop playback between tests.

### New features
- [ ] **1. <name>.** `<snippet>`
  Expected: <concrete: what you hear and see>

### Warnings and errors
- [ ] ...

### Regressions
- [ ] **n. Basic playback.** `(seq :bd :sd :bd :sd)`
  Expected: kick and snare alternate, 4 hits per cycle.
- [ ] **n. Stop.** `(stop)`
  Expected: playback stops cleanly, no console errors.
- [ ] **n. Transforms.** `(every 4 (fast 2) (seq :bd :sd :hh :oh))`
  Expected: every 4th cycle plays twice as fast.
- [ ] **n. Page load.** Reload the app.
  Expected: no console errors.

### Known issues (not blockers)
- <accepted limitations, or "none">

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

The `## Test protocol` section follows **Rule 5 in `CLAUDE.md`** and is always the last
section. Tailor the tests to the actual changes: every new built-in, parameter, message
and changed behaviour gets a test with a concrete expected outcome. Keep the baseline
regressions and add ones for the areas touched (audio code changed → sample playback;
lisp code changed → eval checks). For a change with no runtime behaviour (docs,
tooling), write `No runtime behaviour changed.` plus the commands that verify it.

5. Return the PR URL when done.

Do all of the above in a single response. Do not ask for confirmation.
