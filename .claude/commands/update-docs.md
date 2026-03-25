---
allowed-tools: Read, Edit, Write, Glob, Grep, Bash(git diff *), Bash(git log *)
description: Update README, ROADMAP, and docs/ to match current codebase state
---

## Context

- Recent commits since last docs update: !`git log --oneline -20`
- Files changed recently: !`git diff main...HEAD --name-only`

## Your task

Bring all documentation up to date with the current state of the codebase. Read the source code to verify what actually exists — do not guess or rely solely on commit messages.

### 1. README.md

- **Built-in pattern functions table** — add rows for any new Lisp built-ins. Remove any that were deleted. Check `app/src/repulse/app.cljs` (`ensure-env!`) and `packages/lisp/src/repulse/lisp/eval.cljs` (`make-env`) for the authoritative list.
- **Sound values table** — update if new voices, sample banks, or sound types were added.
- **Examples section** — add examples for significant new features if they'd help a new user.
- **Development setup / Repository structure** — update the file tree if new files or directories were added.
- **Editor keybindings** — check `app/src/repulse/app.cljs` for any new keybindings.

### 2. ROADMAP.md

- **Phase status** — mark phases as delivered (✅) if they were completed. Add a "Delivered:" bullet list summarizing what was built, matching the style of existing phases.
- **New phases** — if new phase prompts were added to `PROMPTS/`, add a section for them in the roadmap with status "planned" or "in progress".
- Do NOT change the structure or style of existing delivered phases.

### 3. CLAUDE.md

- **Phase status table** — update the status column to match reality. Check the codebase, not just git history.
- **Repository structure** — update the file tree if new significant files/dirs were added.
- Only touch sections that are factually outdated. Do not rephrase or reformat things that are already correct.

### 4. docs/USAGE.md

- Add sections for any new user-facing features (new built-ins, new workflows, new commands).
- Update existing sections if behavior changed.

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
