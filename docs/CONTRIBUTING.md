# Contributing to REPuLse

This guide covers how work on REPuLse is organised, how to set up a development environment,
the most common kinds of change, and what CI checks. For how the layers work internally, read
[ARCHITECTURE.md](ARCHITECTURE.md). The binding rules (coding conventions, phase workflow)
are in [CLAUDE.md](../CLAUDE.md).

---

## How work is organised

REPuLse is developed in **phases**. A phase is one self-contained feature or change with a
written spec.

- [ROADMAP.md](../ROADMAP.md) lists every phase and its status (`📋 *planned*` or
  `✅ *delivered*`). It is the single source of truth — there is no other status table.
- [PROMPTS/](../PROMPTS/) holds one spec per phase (`PROMPTS/PHASE-<ID>.md`): background,
  files to change, and a definition-of-done checklist.

`main` is protected: every change, phase or not, goes through a pull request.

### Workflow

| Step | What to do |
|---|---|
| 1. Propose | Open an [issue](https://github.com/tissanr/REPuLse/issues) describing the idea, or comment on an existing one to claim a planned phase. |
| 2. Spec | For a new phase, write `PROMPTS/PHASE-<ID>.md` and add a `## Phase <ID> — Title 📋 *planned*` section to ROADMAP.md (2–4 bullets and a link to the spec). Commit both together. |
| 3. Branch | Create a branch off the latest `main`: `feat/phase-<id>` for a phase, `docs/phase-<id>` for a spec, `fix/<slug>` or `docs/<slug>` otherwise. |
| 4. Implement | Follow the spec's "Files to change" table and the conventions in CLAUDE.md. |
| 5. Finish | Meet the [definition of done](#definition-of-done) below. |
| 6. Pull request | Open a PR against `main` (format below). CI must be green before merging. |

Small fixes that are not part of a phase (typos, bug fixes, dependency updates) skip steps 2
and 5 — branch, fix, open a PR.

### Definition of done

A phase is delivered only when **all** of these hold:

1. **Verified.** `npm run test:all` passes. For docs-only changes `npm test` is enough — say
   why in the PR.
2. **Documented.** [USAGE.md](USAGE.md) lists every new built-in, effect or parameter with at
   least one example. README.md is updated if user-facing syntax or the quick start changed.
3. **Marked delivered.** In ROADMAP.md, `📋 *planned*` becomes `✅ *delivered*`, followed by a
   **Delivered:** list matching the style of the existing phases.
4. Docs are committed together with (or immediately after) the implementation.

### Commits and pull requests

Commit messages use conventional-commit prefixes: `feat:`, `fix:`, `refactor:`, `docs:`, … A
phase implementation is committed as `feat: implement Phase <ID> — <short title>`.

The PR description has two parts:

```markdown
## Summary
1–3 bullets: what changed and why.

## Test plan

### Functional tests
- [ ] Checks for the new behaviour, with concrete REPuLse-Lisp expressions

### Regression tests
- [ ] `npm test` passes
- [ ] `(seq :bd :sd :bd :sd)` plays; `(stop)` stops cleanly
- [ ] Alt+Enter, the command bar and the context panel still work
- [ ] No console errors on page load
```

### Claude Code commands

The repository ships [Claude Code](https://claude.com/claude-code) slash commands in
`.claude/commands/` that automate the workflow above:

| Command | Does |
|---|---|
| `/evaluate-idea <idea>` | Assesses an idea and suggests where it fits in the roadmap |
| `/create-phase <idea>` | Writes the PROMPTS spec and the planned ROADMAP section (step 2) |
| `/implement-phase <ID>` | Implements a phase, updates the docs and marks it delivered (steps 4–5) |
| `/commit-push-pr` | Commits, pushes and opens a PR in the format above (step 6) |
| `/update-docs` | Brings README, ROADMAP and `docs/` in line with the code |
| `/create-release` | Drafts release notes from delivered phases and bumps the version |

---

## Development setup

**Requirements:** Node.js 20+, Java 21+ (shadow-cljs 3 needs it), Rust +
[wasm-pack](https://rustwasm.github.io/wasm-pack/) (for the WASM synthesis engine).

```bash
git clone https://github.com/tissanr/REPuLse.git
cd REPuLse
npm install
npm run build:wasm   # build the Rust/WASM engine; repeat after changing packages/audio
npm run dev          # shadow-cljs watch with hot reload on http://localhost:3000
```

`npm run dev:full` runs both steps in one go. Without the WASM build the app falls back to a
JavaScript synthesis engine, which is enough for most work outside `packages/audio`.

Each git worktree needs its own `npm install` and `npm run build:wasm`.

### Backend features (optional)

Patterns, audio, the editor and plugins need no backend. Sign-in, the community snippet
library and the AI assistant go through the serverless functions in `api/`, so they need the
Vercel dev server:

```bash
vercel link                    # once: connect the checkout to the Vercel project
vercel env pull .env.local     # fetch the Supabase variables (file is gitignored)
vercel dev                     # app + /api functions
```

Without access to the Vercel project, copy `.env.local.example` to `.env.local` and fill in your own
Supabase project's values — see [DEPLOYMENT.md](DEPLOYMENT.md) for setting one up.

---

## Repository layout

```
packages/
  core/      Pattern algebra — pure CLJS, no DOM, no audio
             core.cljs (patterns), theory.cljs (scales, chords), params.cljs, envelope.cljs
  lisp/      REPuLse-Lisp reader and evaluator
             builtins/ — language built-ins grouped by topic (pattern, music, params, …)
  audio/     Rust crate → WASM synthesis engine (runs in an AudioWorklet)
app/
  src/repulse/
    app.cljs                Bootstrap: wires all modules together
    eval_orchestrator.cljs  Evaluates the editor buffer and routes results
    audio.cljs              Lookahead scheduler + AudioWorklet bridge
    env/builtins/           App-layer built-ins (tracks, fx, samples, MIDI, …)
    ui/                     Editor, context panel, timeline, snippet panel, AI panel
    ai/                     AI assistant (agent loop, tools, providers)
    lisp-lang/              CodeMirror language support (grammar, completions, hover)
    content/                Demos, tutorial, built-in metadata (builtin_meta.edn)
  public/
    plugins/                Built-in effect and visual plugins (JS)
api/         Vercel serverless functions (auth, snippets, AI proxy)
e2e/         Playwright offline-audio tests
scripts/     Test runner, AI-docs generator, Vercel build script
PROMPTS/     Phase specs, one per phase
docs/        User and contributor documentation
```

ARCHITECTURE.md has the detailed module map and dependency rules.

---

## Common changes

### Adding a built-in function

1. **Implement it** in the right layer:
   - Pure pattern or music functions (no DOM, no audio): in the matching namespace in
     `packages/core/src/repulse/` (`core.cljs`, `theory.cljs`, `params.cljs`, …), then expose
     it in the matching map in `packages/lisp/src/repulse/lisp/builtins/`.
   - Functions with side effects (audio, DOM, network): in the matching file in
     `app/src/repulse/env/builtins/` (`tracks.cljs`, `fx.cljs`, `samples.cljs`, …).
2. **Test it.** Every function in `packages/core` needs a `cljs.test` unit test in the
   namespace's `*_test.cljs` file (e.g. `core_test.cljs`, `theory_test.cljs`). A new test
   namespace must also be added to `packages/core/src/repulse/test_runner.cljs`.
3. **Make the editor and the AI assistant know it:**
   1. Add the name to `BuiltinName` in `app/src/repulse/lisp-lang/repulse-lisp.grammar`
   2. Run `npm run gen:grammar` — regenerates the committed `parser.js` and `parser.terms.js`
   3. Add a `{ label, type, detail }` entry to `app/src/repulse/lisp-lang/completions.js`
   4. Optionally add a richer tooltip to `app/src/repulse/lisp-lang/hover.js`
   5. Add an entry to `app/src/repulse/content/builtin_meta.edn` (`category`, `returns`,
      `side-effects`, at least one example, `see-also`)
   6. Run `npm run gen:ai-docs` — regenerates `docs/ai/builtins.json`
4. **Document it** in [USAGE.md](USAGE.md) with at least one example.

Commit the grammar, `parser.js`, `parser.terms.js`, `completions.js`, `builtin_meta.edn` and
`builtins.json` together — CI fails on grammar or AI-docs drift.

### Adding a synthesized voice

Voices live in `packages/audio/src/lib.rs`. Read the "WASM synthesis — architectural
decisions" section of [CLAUDE.md](../CLAUDE.md) first: the render callback must stay
allocation-free. Rebuild with `npm run build:wasm` and test with `npm run test:rust`.

### Writing a plugin

See [PLUGINS.md](PLUGINS.md).

---

## Testing

```bash
npm test              # CLJS unit and integration tests (core, lisp, selected app namespaces)
npm run test:rust     # Rust AudioEngine tests
npm run test:e2e      # Playwright offline-audio browser tests
npm run test:all      # all three
```

Before the first `npm run test:e2e`, install the browser once:
`npx playwright install chromium`.

**Before pushing a ClojureScript change, also run `npx shadow-cljs compile app`.** The test
build only compiles namespaces reachable from `test_runner.cljs`; app-layer code such as
`env/builtins/` and most of `ui/` is compiled only by the app build, so bracket mismatches and unresolved or
unused requires there show up nowhere else.

The browser tests render audio with an `OfflineAudioContext` and a JavaScript backend; they
do not exercise the production AudioWorklet + WASM path. [TESTPLAN.md](TESTPLAN.md) has the
full coverage map.

---

## CI pipeline

Every pull request and every push to `main` runs these jobs:

| Job | Workflow | What it runs |
|---|---|---|
| Test (cljs.test) | `ci.yml` | `npm test` |
| Rust audio tests | `ci.yml` | `cargo test` in `packages/audio` |
| Browser audio tests | `ci.yml` | `npm run test:e2e` (Playwright, Chromium) |
| AI docs drift check | `ci.yml` | `npm run check:ai-docs` |
| Release build | `ci.yml` | `npm run build:wasm && npx shadow-cljs release app` |
| CLJS lint | `lint.yml` | `clj-kondo` on `packages/` and `app/src/` — fails on errors, reports warnings |
| TypeScript type-check | `lint.yml` | `npm run typecheck` (the `api/` functions) |
| Rust lint | `lint.yml` | `cargo test`, `cargo clippy -- -D warnings`, `cargo fmt --check` |
| Grammar drift check | `lint.yml` | `npm run gen:grammar`, then fails if `parser.js` or `parser.terms.js` changed |

To reproduce the Rust lint locally, run the three cargo commands inside `packages/audio/`.

Dependabot opens grouped update PRs for npm packages (weekly) and GitHub Actions (monthly).

---

## Deployment

Vercel deploys automatically: every branch gets a preview deployment, and `main` goes to
production. No manual steps are needed. [DEPLOYMENT.md](DEPLOYMENT.md) covers the one-time
project setup.
