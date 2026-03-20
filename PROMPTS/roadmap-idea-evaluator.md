# REPuLse — Roadmap Idea Evaluator

## Your role

You are a REPuLse contributor helping to evaluate a new feature idea.
Your job is to read the idea, understand the existing codebase and roadmap, then produce
a structured assessment and — if the idea is worthwhile — a concrete roadmap placement
suggestion including a draft ROADMAP.md entry and PROMPTS/ stub.

You are **not** implementing anything. This is a planning and triage task only.

---

## Context to read first

Before evaluating the idea, read these files in order:

1. `ROADMAP.md` — understand what has been delivered, what is planned, and the naming/numbering convention for phases
2. `PROMPTS/phase-m-lisp-superpowers.md` — use this as the canonical style reference for PROMPTS files
3. `README.md` — understand the current language surface, architecture, and built-in vocabulary
4. `CLAUDE.md` — any project-level constraints or conventions

Also scan these source files for relevant context:

- `packages/core/src/repulse/core.cljs` — pattern algebra
- `packages/lisp/src/repulse/lisp/eval.cljs` — evaluator, `make-env`, special forms
- `packages/audio/src/lib.rs` — Rust/WASM synthesis engine
- `app/src/repulse/audio.cljs` — scheduler, `play-event` dispatch, lookahead clock
- `app/src/repulse/fx.cljs` — effect chain manager
- `app/src/repulse/synth.cljs` — UGen vocabulary (if it exists)

Only read files that are actually relevant to the idea being evaluated.

---

## The idea to evaluate

```
[PASTE THE IDEA HERE — a sentence, a paragraph, a rough sketch, or a conversation excerpt]
```

---

## Evaluation output format

Produce the following sections in order.

### 1. Understanding

In 2–4 sentences: restate the idea in your own words, translated into REPuLse's architectural vocabulary. What exactly would be added or changed?

### 2. Duplication check

Does any part of this already exist in REPuLse?

- Check delivered phases in ROADMAP.md
- Check `make-env` in `eval.cljs` for existing built-ins
- Check `packages/core/src/repulse/core.cljs` for existing combinators
- Check `app/src/repulse/audio.cljs` `play-event` for existing dispatch branches
- Check `app/src/repulse/synth.cljs` for existing UGens (if the file exists)

State clearly: **fully covered**, **partially covered** (describe what's missing), or **not covered**.

### 3. Fit assessment

Rate the idea on three dimensions, each on a 1–5 scale with one sentence of justification:

| Dimension | Score (1–5) | Justification |
|---|---|---|
| **REPuLse alignment** | | Does it fit the live-coding instrument vision? |
| **Implementation cost** | | How much new code / new dependencies? (5 = very cheap) |
| **User impact** | | How much does it expand what a user can express? |

Overall verdict: **Ship it / Defer / Skip** with one sentence explaining why.

### 4. Placement suggestion

If verdict is **Ship it** or **Defer**, suggest where it fits in the roadmap:

- **New phase** — give it a phase letter/number that fits the existing sequence (phases after M use letters: N, O, P, Q, … or numbers for major additions). Justify why it warrants its own phase.
- **Addition to existing planned phase** — name the phase and explain what to add.
- **Backlog entry** — if it should go to `docs/FUTURE-FEATURES.md` instead of a named phase.

State clearly: is this a **blocker** for any planned phase, or is it **independent**?

### 5. Draft ROADMAP.md entry

If the idea warrants a new phase, write the ROADMAP.md entry in the exact style of the
existing entries. Use the status `📋 *planned*`.

The entry must include:
- Phase heading with letter/number and title
- One-sentence goal description
- **Key additions** bullet list (be specific — name files, function names, Lisp built-ins)
- A `See full spec:` link placeholder: `[PROMPTS/phase-X-slug.md](PROMPTS/phase-X-slug.md)`

Match the existing heading and bullet style precisely. Do not add sections that don't
appear in other entries (no "Motivation", no "Risks", no "Timeline").

### 6. Draft PROMPTS stub

Write the first ~60 lines of the PROMPTS file for this phase, following the style of
`PROMPTS/phase-m-lisp-superpowers.md`. Include:

- `# Phase X — Title: Subtitle` heading
- `## Goal` section with a 3–6 line description and a before/after code example
- `## Background` section covering the relevant existing code the implementation will touch
- A `## Files to change` table stub (can be incomplete — mark unknown rows with `?`)
- A `## Definition of done` section stub with the first 5–10 acceptance criteria

Stop after the stub. Do not write the full implementation spec — that is a separate task.

### 7. Open questions

List up to 5 decisions that need to be made before implementation can begin. Frame each
as a question, not a statement. Flag any that are **blocking** (must be resolved before
starting) vs. **deferrable** (can be decided during implementation).

---

## Output constraints

- Do not implement any code
- Do not modify any files
- Do not add speculative features beyond what the idea describes
- If the idea is too vague to evaluate, list exactly what clarifications are needed before you can proceed — then stop
- If verdict is **Skip**, write sections 1–3 only and explain why sections 4–7 are omitted
