---
allowed-tools: Read, Edit, Write, Glob, Grep, Bash, TodoWrite
description: Implement a REPuLse phase from its PROMPTS/PHASE-*.md spec, update docs, and mark as delivered
---

## Arguments

Phase identifier: $ARGUMENTS (e.g. `T1`, `DST1`, `N1`, `D2`)

## Phase prompt

Find the phase spec file:
- Try `PROMPTS/PHASE-$ARGUMENTS.md` first (uppercase)
- Fall back to `PROMPTS/phase-*.md` matching the identifier

Read it fully before doing anything else.

## Branch

`main` is protected. Start every phase on a fresh branch from the latest `main`:

```bash
git fetch origin main
git switch -c feat/phase-$ARGUMENTS origin/main   # lowercase id, e.g. feat/phase-syn4
```

If the spec or its ROADMAP section is missing on `origin/main`, its spec PR has not been merged
yet — stop and say so instead of implementing against an unmerged spec.

## Your task

Implement the phase described in the prompt, then update all documentation and mark the phase as delivered. Follow **Rule 2** from `CLAUDE.md` exactly — a phase is not done until every sub-step is satisfied.

### 1. Read before writing

Read every file listed in the phase prompt's **"Files to change"** table. Understand existing patterns (how similar built-ins, effects, or params are implemented) before modifying anything.

### 2. Implement

Work through the "Files to change" table. Apply all changes described in the spec. Adhere to CLAUDE.md conventions:
- Pure functions by default; side effects only at edges
- Rational time for all time values in `packages/core/` — use the rational helpers in `core.cljs` (`rat`, `rat+`, `rat*`, …), never floats
- No external CLJS libs in `core` or `lisp`
- Errors: reader/evaluator internals may throw `ex-info`; `repulse.lisp.core/eval-string` turns them into a typed eval-error for the app layer — don't invent other error shapes

**After Rust changes:** run `npm run build:wasm`
**After grammar changes:** run `npm run gen:grammar` (overwrites committed `parser.js`)
**After any change:** run `npm run test` (plus `npx shadow-cljs compile app` for app-layer changes) to catch regressions

### 3. Grammar/completions/hover docs checklist

If the phase adds new Lisp built-in names, follow **Rule 3** in `CLAUDE.md`:
- [ ] Add name to `BuiltinName` in `repulse-lisp.grammar`
- [ ] Run `npm run gen:grammar`
- [ ] Add `{ label, type, detail }` entry in `completions.js`
- [ ] Optionally add a richer hover tooltip in `hover.js`
- [ ] Add an entry to `app/src/repulse/content/builtin_meta.edn`
- [ ] Run `npm run gen:ai-docs`
- [ ] Commit grammar, `parser.js`, `parser.terms.js`, `completions.js`, `builtin_meta.edn` and `docs/ai/builtins.json` together (CI fails on drift)

### 4. Verify the Definition of Done

Work through the checklist at the bottom of the phase prompt. For browser-verifiable items, use `preview_start` and the preview tools.

### 5. Update `docs/USAGE.md` (Rule 2, step 2)

- Add every new built-in, effect, or parameter to the relevant reference tables
- Add at least one usage example per new feature
- Match the existing table/section style

Update `README.md` too if the phase adds user-facing syntax or changes the quick-start example.

### 6. Mark as delivered (Rule 2, steps 1 and 3)

**`ROADMAP.md`** — find the `## Phase $ARGUMENTS` section and:
- Change `📋 *planned*` → `✅ *delivered*` in the heading
- Add a **Delivered:** section with 2–6 bullet points summarising what was built (match the style of existing delivered phases)

### 7. Commit and open a PR

Run `npm run test:all` and `npx shadow-cljs compile app` first (Rule 2, step 1; CLAUDE.md pre-push verification). If the full suite cannot run, state the exact blocker and run the strongest subset. Commit implementation + docs + ROADMAP.md together on the phase branch. Message format:
```
feat: implement Phase $ARGUMENTS — <short title>
```

Push the branch (`git push -u origin feat/phase-<id>`) and open a PR against `main` with `gh pr create`, using the Summary / Test plan structure from `.claude/commands/commit-push-pr.md`. The functional tests come from the spec's Definition of done. Return the PR URL — the phase is delivered when that PR is merged with green CI.
