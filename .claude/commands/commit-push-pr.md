---
allowed-tools: Bash(git *), Bash(gh *), Bash(npm run *), Bash(npx shadow-cljs compile *), Read, Glob, Grep
description: Commit all changes, push, and open a PR against main
---

## Context

- Current git status: !`git status`
- Current git diff (staged and unstaged): !`git diff HEAD`
- Current branch: !`git branch --show-current`
- Recent commits on this branch: !`git log --oneline -10`
- Diff from main: !`git diff origin/main...HEAD --stat`
- Open PRs (match `headRefName` against the current branch): !`gh pr list --state open --json number,headRefName,url`

## Your task

`main` is protected — nothing is ever pushed to it directly. Based on the changes above, do all of the following:

0. **Get off `main`.** If the current branch is `main`, create a branch first and commit there:
   `git switch -c <type>/<short-slug>` (e.g. `feat/phase-syn4`, `fix/solo-unmute`, `docs/usage-tracks`).

1. **Verify before committing** (CLAUDE.md, "Pre-push verification"). Run what applies to the changed files and stop with a report if anything fails — do not push a red branch:
   - Any ClojureScript change: `npm run test` **and** `npx shadow-cljs compile app`
   - Rust change (`packages/audio/`): `npm run test:rust`
   - Grammar change: run `npm run gen:grammar` and commit the regenerated `parser.js` / `parser.terms.js` with it
   - `builtin_meta.edn` change: run `npm run gen:ai-docs` and commit the regenerated `docs/ai/builtins.json` with it
   - Docs-only change: no checks needed

2. **Stage and commit** all relevant changes with a clear commit message. Follow conventional commit style (e.g. `feat:`, `fix:`, `refactor:`, `docs:`). Do not commit files containing secrets (.env, credentials, etc).

3. **Push** the branch to origin (with `-u` if not yet tracking).

4. **Create a PR** against `main` using `gh pr create` — unless the context shows an open PR for this branch already; then the push updates it and you only return its URL. The PR body MUST follow this exact structure:

```
## Summary
<1-3 bullet points describing what changed and why>

## Test plan

### Functional tests
<Bulleted checklist of specific things to verify for the new/changed functionality.
 Include concrete REPuLse-Lisp expressions where applicable, e.g.:>
- [ ] `(seq :bd :sd :bd :sd)` produces alternating kick/snare
- [ ] Verify in browser at http://localhost:3000

### Regression tests
<Bulleted checklist of things that MUST still work after this change.
 Always include these baseline checks plus any area-specific ones:>
- [ ] `npm run test` — all CLJS tests pass
- [ ] `(seq :bd :sd :bd :sd)` — basic playback works
- [ ] `(stop)` — stops playback cleanly
- [ ] `(every 4 (fast 2) (seq :bd :sd :hh :oh))` — pattern transforms work
- [ ] `(fx :reverb 0.3)` — effects still load and apply
- [ ] Alt+Enter evaluates the editor buffer
- [ ] Command bar accepts and evaluates expressions
- [ ] Context panel shows BPM, bindings, and effects
- [ ] No console errors on page load

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

Tailor the functional tests to the actual changes. For the regression tests, keep the baseline list above and add any extras relevant to the areas touched (e.g. if audio code changed, add sample playback checks; if lisp code changed, add eval checks).

5. Return the PR URL when done.

Do all of the above in a single response. Do not ask for confirmation.
