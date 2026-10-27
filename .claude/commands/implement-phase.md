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

## Your task

Implement the phase described in the prompt, then update all documentation and mark the phase as delivered. Follow **Rule 2** from `CLAUDE.md` exactly — a phase is not done until every sub-step is satisfied.

### 1. Read before writing

Read every file listed in the phase prompt's **"Files to change"** table. Understand existing patterns (how similar built-ins, effects, or params are implemented) before modifying anything.

### 2. Implement

Work through the "Files to change" table. Apply all changes described in the spec. Adhere to CLAUDE.md conventions:
- Pure functions by default; side effects only at edges
- Rational time (`1/4`, `3N`) for all time values in `packages/core/` — never floats
- No external CLJS libs in `core` or `lisp`
- Return `{:error "..."}` maps (not thrown exceptions) from the Lisp evaluator

**After Rust changes:** run `npm run build:wasm`
**After grammar changes:** run `npm run gen:grammar` (overwrites committed `parser.js`)
**After any change:** run `npm run test` (plus `npx shadow-cljs compile app` for app-layer changes) to catch regressions

### 3. Grammar/completions/hover docs checklist

If the phase adds new Lisp built-in names, follow **Rule 3** in `CLAUDE.md`:
- [ ] Add name to `BuiltinName` in `repulse-lisp.grammar`
- [ ] Run `npm run gen:grammar`
- [ ] Add `{ label, type, detail }` entry in `completions.js`
- [ ] Add an entry to `app/src/repulse/content/builtin_meta.edn`
- [ ] Run `npm run gen:ai-docs`

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

### 7. Write the test protocol (Rule 5)

Write the `## Test protocol` section for the PR as described in **Rule 5** of `CLAUDE.md`:
a GitHub task list with one checkbox per test, each with a pasteable snippet and its
concrete expected outcome. Check pattern snippets with the TEST1 harness and copy values
from its output; never invent them. It is not a file in the repo.

- If a PR for this branch already exists (`gh pr view`), append the section to its
  description with `gh pr edit --body-file`, as the last section before the 🤖 line.
- Otherwise put it at the end of the description when opening the PR (`/commit-push-pr`).

### 8. Commit

Run `npm run test:all` first (Rule 2, step 1). Commit implementation + docs + ROADMAP.md together. Message format:
```
feat: implement Phase $ARGUMENTS — <short title>
```
