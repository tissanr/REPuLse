---
allowed-tools: Bash(git *), Bash(gh *), Read, Glob, Grep
description: Commit all changes, push, and open a PR against main
---

## Context

- Current git status: !`git status`
- Current git diff (staged and unstaged): !`git diff HEAD`
- Current branch: !`git branch --show-current`
- Recent commits on this branch: !`git log --oneline -10`
- Diff from main: !`git diff main...HEAD --stat`

## Your task

Based on the changes above, do all of the following:

1. **Stage and commit** all relevant changes with a clear commit message. Follow conventional commit style (e.g. `feat:`, `fix:`, `refactor:`, `docs:`). Do not commit files containing secrets (.env, credentials, etc).

2. **Push** the branch to origin (with `-u` if not yet tracking).

3. **Create a PR** against `main` using `gh pr create`. The PR body MUST follow this exact structure:

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
- [ ] `npm run test:core` — all core unit tests pass
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

4. Return the PR URL when done.

Do all of the above in a single response. Do not ask for confirmation.
