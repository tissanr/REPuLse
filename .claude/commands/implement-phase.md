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
**After any change:** run `npm run test:core` to catch regressions

### 3. Grammar/completions/hover docs checklist

If the phase adds new Lisp built-in names (see CLAUDE.md "Syntax highlighting + completions checklist"):
- [ ] Add name to `BuiltinName` in `repulse-lisp.grammar`
- [ ] Run `npm run gen:grammar`
- [ ] Add `{ label, type, detail }` entry in `completions.js`
- [ ] Add hover doc entry in the hover-docs map

### 4. Verify the Definition of Done

Work through the checklist at the bottom of the phase prompt. For browser-verifiable items, use `preview_start` and the preview tools.

### 5. Update `docs/USAGE.md` (Rule 2, step 3)

- Add every new built-in, effect, or parameter to the relevant reference tables
- Add at least one usage example per new feature
- Match the existing table/section style

Update `README.md` too if the phase adds user-facing syntax or changes the quick-start example.

### 6. Mark as delivered (Rule 2, steps 1–2)

**`CLAUDE.md` phase table** — change `planned` → `✓ delivered` for this phase.

**`ROADMAP.md`** — find the `## Phase $ARGUMENTS` section and:
- Change `📋 *planned*` → `✅ *delivered*` in the heading
- Add a **Delivered:** section with 2–6 bullet points summarising what was built (match the style of existing delivered phases)

### 7. Commit

Commit implementation + docs + CLAUDE.md + ROADMAP.md together. Message format:
```
feat: implement Phase $ARGUMENTS — <short title>
```
