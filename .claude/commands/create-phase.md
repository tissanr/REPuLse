---
allowed-tools: Read, Write, Edit, Glob, Grep, Bash(git log *)
description: Create a new REPuLse phase — write the PROMPTS file, add the row to CLAUDE.md, and add the section to ROADMAP.md
---

## Idea

$ARGUMENTS

---

## Step 1 — Read context

Read these files before writing anything:

1. `ROADMAP.md` — understand all existing phases, the naming/numbering convention, and the style of planned entries
2. `CLAUDE.md` — phase lifecycle rules (especially **Rule 1**) and project constraints
3. `PROMPTS/phase-m-lisp-superpowers.md` — canonical style reference for PROMPTS files
4. Scan the source files most relevant to the idea (e.g. `eval.cljs` for Lisp features, `lib.rs` for audio, `fx.cljs` for effects) — enough to write accurate background and file-change sections

## Step 2 — Choose a phase ID

Look at all existing phase IDs in ROADMAP.md. Pick an ID that fits the naming pattern:

- **Single letters** (Q, R, S, …) — for broad, standalone feature areas
- **Named series** (e.g. `DST1`–`DST6`, `T1`–`T2`) — for a tight cluster of related incremental phases; start a new series if the idea has clear follow-on phases
- **Numbered suffixes** (e.g. `E2b`, `N1`) — for extensions or variants of an existing phase

State the chosen ID and one sentence explaining why it fits.

## Step 3 — Write `PROMPTS/PHASE-{ID}.md`

Follow the style of `PROMPTS/phase-m-lisp-superpowers.md` exactly. The file must contain:

### `## Goal`
3–6 sentences describing what the phase adds. Include a **before/after code example** in REPuLse-Lisp showing the concrete user-facing change.

### `## Background`
Cover the existing code the implementation will touch — file paths, function names, data structures. A future implementer should understand the landscape without reading the source themselves. Only cover files actually relevant to this phase.

### `## Implementation`
Concrete, step-by-step implementation notes. For each significant change:
- Name the exact file and function/section to modify
- Show code snippets where the change is non-obvious
- Note build steps required after the change (e.g. `npm run build:wasm`, `npm run gen:grammar`)

If the implementation is straightforward, keep this section brief — don't pad it.

### `## Files to change`
A markdown table with columns `File` and `Change`. Mark genuinely unknown rows with `?`. Include build steps as rows where applicable.

### `## Definition of done`
A checklist of 8–15 acceptance criteria written as `- [ ] …` items. Each item must be:
- Objectively verifiable (not "works correctly")
- Specific to this phase (not generic boilerplate)
- Include at least 3 REPuLse-Lisp expressions that should produce the described behaviour

### `## What NOT to do`
3–6 bullet points scoping the phase — explicitly name adjacent features that are out of scope and belong to follow-on phases.

## Step 4 — Register the phase (Rule 1 from CLAUDE.md)

**`CLAUDE.md` phase table** — add a new row in the right position (near related phases):
```
| {ID}  | Short description (≤60 chars)                                  | planned      |
```

**`ROADMAP.md`** — add a new section near related phases:
```markdown
## Phase {ID} — {Title} 📋 *planned*

One-sentence goal description.

**Key additions:**
- bullet points naming specific files, functions, Lisp built-ins
- be concrete — not "add audio support" but "`start_transition` method on `AudioEngine`"

See full spec: [PROMPTS/PHASE-{ID}.md](PROMPTS/PHASE-{ID}.md)

---
```

## Step 5 — Commit

```
docs: add Phase {ID} prompt — {short title}
```
