# Phase QA7 — CI, Tooling & Code Structure

> **Spec status:** outline. Expand it into a full spec (Background with exact code paths, step-by-step Implementation, Files to change) before implementing.
> **Automation:** 🧑 human-in-loop. This phase changes the CI gates that the unattended pipeline relies on to auto-merge, plus the Vercel build. A pipeline must not rewrite its own gate unattended. It also splits large files, which conflicts with any phase running in parallel, so a human picks the moment.

**Milestone:** none (quality track)
**Depends on:** QA1–QA4 delivered (they touch the files that get split, and QA4 adds the strict lint gate).

## Goal

Make CI faster, cheaper and stricter. Pin the toolchain. Split the files that have grown
too large to review. Trim the agent-facing docs so they stay accurate and cheap to load.

## Background

- **Node and toolchain:**
  - Workflows (`.github/workflows/ci.yml`, `lint.yml`) use Node 20, which is past end
    of life.
  - No `.nvmrc` and no `engines` field.
  - The Vercel default is Node 24.
- **Duplicated CI work:**
  - `cargo test` runs in both `ci.yml` (cargo-test) and `lint.yml` (cargo-lint).
  - `cargo install --locked wasm-pack` compiles from source in two CI jobs and again in
    `scripts/vercel-build.sh`.
  - Java/Node/m2/Cargo setup is copy-pasted into every job.
- **Triggers and coverage:**
  - `push` triggers only on `main`, so pushes to `dev` (merge results) are not tested.
    PR checks run before the merge, not on the merged state.
  - No coverage reporting for CLJS (shadow-cljs + c8 over `out/test.js`) or Rust
    (`cargo llvm-cov`).
- **Large files:**
  - `packages/audio/src/lib.rs` ≈ 2,100 lines
  - `packages/core/src/repulse/core.cljs` ≈ 1,750
  - `app/src/repulse/audio.cljs` ≈ 1,450 (JS voices, scheduler, capture, tap, MIDI sync)
  - `app/src/repulse/ui/assistant_panel.cljs` ≈ 800, with a pure Markdown renderer inside
  - Long functions: `eval-form` (~265 lines) and `evaluate*` (~170 lines) in
    `eval_orchestrator.cljs`.
- **Docs:**
  - `ROADMAP.md` is ~2,800 lines, mostly delivered phases, and is read on every pipeline
    run.
  - `AGENTS.md` and `GEMINI.md` are stale partial copies of `CLAUDE.md`.

## Scope

1. **Node:** Node 22 or 24 everywhere (CI, `.nvmrc`, `engines`), matching the Vercel
   project setting.
2. **Shared setup:** a composite action `.github/actions/setup` (Java, Node, npm ci, m2
   and Cargo caches, prebuilt `wasm-pack` binary via `jetli/wasm-pack-action` or the
   release tarball).
   - Use the prebuilt binary in `vercel-build.sh` too.
   - Remove the duplicate `cargo test`.
3. **Triggers:** run CI on `push` to `dev` as well.
4. **Coverage:** CLJS (c8) and Rust (llvm-cov) as job summaries. Report only, no threshold
   at first.
5. **Split files** (pure moves, no behaviour change, tests unchanged):
   - `lib.rs` → `dsp/biquad.rs`, `voices/{fm,ks,…}.rs`, `inserts.rs`, `sends.rs`,
     `engine.rs`.
   - `core.cljs` → by combinator family, keeping `repulse.core` as the public facade.
   - `audio.cljs` → scheduler, JS voices, capture, tempo.
   - Assistant Markdown renderer → its own namespace with tests.
   - Split `eval-form` and `evaluate*` into named steps.
6. **Docs:**
   - Move delivered phase sections from `ROADMAP.md` to `docs/ROADMAP-ARCHIVE.md`. Keep
     the table, the box and planned phases.
   - Make `AGENTS.md` and `GEMINI.md` one-line pointers to `CLAUDE.md`.
   - Check that `.claude/commands/auto-phase.md` still finds everything it reads.

## Open design questions

- **Q1. Node version.** 22 LTS or 24 LTS. *Recommendation:* 24. It's the Vercel default,
  and AI SDK and tooling already require ≥ 22.
- **Q2. ROADMAP archive.** Moving delivered sections changes links in PRs and PROMPTS.
  *Recommendation:* archive, and leave a one-line stub with a link per phase in the
  implementation table.

## Definition of done

- [ ] All workflows use the composite setup action and one Node version. `.nvmrc` and
      `engines` match.
- [ ] No job compiles `wasm-pack` from source. Total CI wall time on a docs-only PR drops
      measurably (record before and after in the PR).
- [ ] CI runs on pushes to `dev`.
- [ ] Coverage summaries appear on PRs for CLJS and Rust.
- [ ] `lib.rs`, `core.cljs` and `audio.cljs` are each under 800 lines. All tests pass
      unchanged, and golden renders (QA2) are identical.
- [ ] `ROADMAP.md` is under 600 lines, and the pipeline's `--dry-run` still selects the
      correct next phase.
- [ ] `AGENTS.md` and `GEMINI.md` point to `CLAUDE.md`.

## What NOT to do

- Don't change behaviour while splitting files. Moves and renames only.
- Don't add coverage thresholds that fail CI in this phase.
- Don't change the auto-phase pipeline's selection rules or usage gate.
