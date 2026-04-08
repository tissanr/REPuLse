---
allowed-tools: Read, Glob, Grep
description: Evaluate a REPuLse feature idea and produce a structured assessment with roadmap placement suggestion and PROMPTS stub
---

## Idea to evaluate

$ARGUMENTS

---

## Context to read first

Before evaluating, read these files in order:

1. `ROADMAP.md` — delivered phases, planned phases, naming/numbering convention
2. `PROMPTS/phase-m-lisp-superpowers.md` — canonical style reference for PROMPTS files
3. `README.md` — current language surface, architecture, built-in vocabulary
4. `CLAUDE.md` — project-level constraints and conventions

Then scan whichever source files are relevant to the idea:

- `packages/core/src/repulse/core.cljs` — pattern algebra
- `packages/lisp/src/repulse/lisp/eval.cljs` — evaluator, `make-env`, special forms
- `packages/audio/src/lib.rs` — Rust/WASM synthesis engine
- `app/src/repulse/audio.cljs` — scheduler, `play-event` dispatch
- `app/src/repulse/fx.cljs` — effect chain manager

Only read files actually relevant to the idea.

---

## Output

### 1. Understanding

2–4 sentences: restate the idea in REPuLse's architectural vocabulary. What exactly would be added or changed?

### 2. Duplication check

Does any part of this already exist? Check delivered phases in ROADMAP.md, `make-env` in `eval.cljs`, `packages/core/`, `play-event` dispatch branches.

State clearly: **fully covered**, **partially covered** (describe what's missing), or **not covered**.

### 3. Fit assessment

| Dimension | Score (1–5) | Justification |
|---|---|---|
| **REPuLse alignment** | | Does it fit the live-coding instrument vision? |
| **Implementation cost** | | How much new code / new dependencies? (5 = very cheap) |
| **User impact** | | How much does it expand what a user can express? |

Overall verdict: **Ship it / Defer / Skip** with one sentence.

### 4. Placement suggestion

*(Skip if verdict is **Skip**)*

- **New phase** — letter/number fitting the existing sequence, with justification
- **Addition to existing planned phase** — name it and explain
- **Backlog entry** — if it belongs in `docs/FUTURE-FEATURES.md`

Is this a **blocker** for any planned phase, or **independent**?

### 5. Draft ROADMAP.md entry

*(Skip if verdict is **Skip**)*

Write the entry in the exact style of existing entries, status `📋 *planned*`. Include phase heading, one-sentence goal, **Key additions** bullets (specific: file names, function names, Lisp built-ins), and a `See full spec:` link placeholder.

### 6. Draft PROMPTS stub

*(Skip if verdict is **Skip**)*

First ~60 lines of the PROMPTS file following `phase-m-lisp-superpowers.md` style:

- `# Phase X — Title` heading
- `## Goal` with 3–6 line description and before/after code example
- `## Background` covering relevant existing code
- `## Files to change` table stub (mark unknowns with `?`)
- `## Definition of done` stub with 5–10 acceptance criteria

Stop after the stub — do not write the full implementation spec.

### 7. Open questions

Up to 5 decisions needed before implementation. Flag each as **blocking** or **deferrable**.

---

## Constraints

- Do not implement any code or modify any files
- Do not add speculative features beyond what the idea describes
- If the idea is too vague, list what clarifications are needed and stop
- If verdict is **Skip**, write sections 1–3 only
