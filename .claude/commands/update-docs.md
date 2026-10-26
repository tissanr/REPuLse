---
allowed-tools: Read, Edit, Write, Glob, Grep, Bash(git *), Bash(gh *), Bash(npm run gen:*)
description: Update README, ROADMAP, and docs/ to match current codebase state
---

## Context

- Recent commits since last docs update: !`git log --oneline -20`
- Files changed on this branch: !`git diff origin/main...HEAD --name-only`

## Your task

Bring all documentation up to date with the current state of the codebase. Read the source code to verify what actually exists — do not guess or rely solely on commit messages.

**Where built-ins are defined** (the authoritative list):
- Language built-ins: `packages/lisp/src/repulse/lisp/builtins/*.cljs` and the special forms in `packages/lisp/src/repulse/lisp/eval.cljs`
- App built-ins (tracks, fx, samples, MIDI, …): `app/src/repulse/env/builtins/*.cljs`
- Generated summary: `docs/ai/builtins.json` (from `app/src/repulse/content/builtin_meta.edn` — if you change the `.edn`, run `npm run gen:ai-docs`)
- Editor keybindings: `app/src/repulse/ui/editor.cljs`

### 1. README.md

The README is deliberately short: pitch, quick start, documentation index. Reference tables live in USAGE.md, development content in CONTRIBUTING.md — do not add them back to the README.

- **Quick start** — update if setup commands or requirements changed (compare with `package.json` scripts and the CI workflows).
- **What REPuLse is** — update only if a major capability was added.
- **Documentation table** — add new docs files.

### 2. ROADMAP.md

- **Phase status** — mark phases as delivered (✅) if they were completed. Add a "Delivered:" bullet list summarizing what was built, matching the style of existing phases.
- **New phases** — if new phase prompts were added to `PROMPTS/`, add a section for them in the roadmap with status "planned" or "in progress".
- Do NOT change the structure or style of existing delivered phases.

### 3. CLAUDE.md

- **Repository structure** — update the directory overview if new significant dirs were added.
- CLAUDE.md has no phase status table — phase status lives only in ROADMAP.md.
- Only touch sections that are factually outdated. Do not rephrase or reformat things that are already correct.

### 4. docs/USAGE.md

- Add sections for any new user-facing features (new built-ins, new workflows, new commands).
- Update existing sections if behavior changed.
- Every built-in in `docs/ai/builtins.json` should appear in USAGE.md with an example.
- Keep the Table of Contents in sync with the `##` headings.

### 4b. docs/CONTRIBUTING.md

- Update the repository layout, the "Adding a built-in" steps and the CI table if they changed (compare with `.github/workflows/*.yml`).

### 5. docs/ARCHITECTURE.md

- Update if the architecture changed (new modules, new data flow, new state atoms).
- Do not update for minor implementation changes.

### 6. docs/PLUGINS.md

- Update if the plugin API or available plugins changed.

### 7. docs/FUTURE-FEATURES.md

- Remove features that have been implemented.
- Add any newly planned features from phase prompts.

### Guidelines

- Read each file before editing — do not blindly append.
- Keep the existing style and tone of each document.
- Be precise: check the actual source files to confirm what's implemented.
- Do not add speculative content about unimplemented features.
- If a doc is already up to date, skip it — don't make no-op edits.

### Finish

`main` is protected. Commit the doc changes on a branch (`docs/<short-slug>` off `origin/main` if you are on `main`) and open a PR against `main` following `.claude/commands/commit-push-pr.md`. Return the PR URL.
