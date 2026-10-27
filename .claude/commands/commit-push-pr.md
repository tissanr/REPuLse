---
allowed-tools: Bash(git *), Bash(gh *), Read, Glob, Grep
description: Commit all changes, push, and open a PR against dev
---

## Context

- Current git status: !`git status`
- Current git diff (staged and unstaged): !`git diff HEAD`
- Current branch: !`git branch --show-current`
- Recent commits on this branch: !`git log --oneline -10`
- Diff from dev: !`git diff origin/dev...HEAD --stat`

## Your task

Based on the changes above, do all of the following:

1. **Stage and commit** all relevant changes with a clear commit message. Follow conventional commit style (e.g. `feat:`, `fix:`, `refactor:`, `docs:`). Do not commit files containing secrets (.env, credentials, etc).

2. **Push** the branch to origin (with `-u` if not yet tracking).

3. **Create a PR** against `dev` using `gh pr create --base dev` (only `dev` → `main` release PRs target `main`). The PR body MUST follow this exact structure:

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

4. Return the PR URL when done.

Do all of the above in a single response. Do not ask for confirmation.
