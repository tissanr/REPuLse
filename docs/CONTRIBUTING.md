# Contributing to REPuLse

This guide covers everything you need to work on the code: setup, repository layout, the
most common changes, and what CI checks. For how the layers work internally, read
[ARCHITECTURE.md](ARCHITECTURE.md). Coding conventions and the phase workflow are in
[CLAUDE.md](../CLAUDE.md).

---

## Development setup

**Requirements:** Node.js 20+, Java 21+ (for shadow-cljs), Rust + [wasm-pack](https://rustwasm.github.io/wasm-pack/)
(for the WASM synthesis engine).

```bash
git clone https://github.com/tissanr/REPuLse.git
cd REPuLse
npm install
npm run build:wasm   # build the Rust/WASM engine; repeat after changing packages/audio
npm run dev          # shadow-cljs watch with hot reload on http://localhost:3000
```

`npm run dev:full` runs both steps in one go. Without the WASM build the app falls back to a
JavaScript synthesis engine, which is enough for most work outside `packages/audio`.

Sign-in, the snippet library backend and the AI proxy need environment variables — see
[DEPLOYMENT.md](DEPLOYMENT.md#4-local-development). Everything else runs without them.

---

## Repository layout

```
packages/
  core/      Pattern algebra — pure CLJS, no DOM, no audio
  lisp/      REPuLse-Lisp reader, evaluator and language built-ins
  audio/     Rust crate → WASM synthesis engine (runs in an AudioWorklet)
app/
  src/repulse/
    app.cljs              Bootstrap: wires all modules together
    eval_orchestrator.cljs  Evaluates the editor buffer and routes results
    audio.cljs            Lookahead scheduler + AudioWorklet bridge
    env/builtins/         App-layer built-ins (tracks, fx, samples, MIDI, …)
    ui/                   Editor, context panel, timeline, snippet panel, AI panel
    ai/                   AI assistant (agent loop, tools, providers)
    lisp-lang/            CodeMirror language support (grammar, completions, hover)
    content/              Demos, tutorial, built-in metadata
  public/
    plugins/              Built-in effect and visual plugins (JS)
api/         Vercel serverless functions (auth, snippets, AI proxy)
e2e/         Playwright offline-audio tests
PROMPTS/     Phase specs, one per phase
docs/        User and contributor documentation
```

ARCHITECTURE.md has the detailed module map and dependency rules.

---

## Common changes

### Adding a built-in function

1. **Implement it** in the right layer:
   - Pure pattern functions (no DOM, no audio): in `packages/core/src/repulse/core.cljs`, then
     expose it in the matching map in `packages/lisp/src/repulse/lisp/builtins/`
     (`pattern.cljs`, `music.cljs`, `params.cljs`, …).
   - Functions with side effects (audio, DOM, network): in the matching file in
     `app/src/repulse/env/builtins/` (`tracks.cljs`, `fx.cljs`, `samples.cljs`, …).
2. **Test it.** Every function in `packages/core` needs a `cljs.test` unit test in
   `packages/core/src/repulse/core_test.cljs`.
3. **Make the editor and the AI assistant know it:**
   1. Add the name to `BuiltinName` in `app/src/repulse/lisp-lang/repulse-lisp.grammar`
   2. Run `npm run gen:grammar` — regenerates the committed `parser.js` and `parser.terms.js`
   3. Add a `{ label, type, detail }` entry to `app/src/repulse/lisp-lang/completions.js`
   4. Optionally add a richer tooltip to `app/src/repulse/lisp-lang/hover.js`
   5. Add an entry to `app/src/repulse/content/builtin_meta.edn` (`category`, `returns`,
      `side-effects`, at least one example, `see-also`)
   6. Run `npm run gen:ai-docs` — regenerates `docs/ai/builtins.json`
4. **Document it** in [USAGE.md](USAGE.md) with at least one example.

Commit the grammar, `parser.js`, `completions.js`, `builtin_meta.edn` and `builtins.json`
together — CI fails on grammar or AI-docs drift.

### Adding a synthesized voice

Voices live in `packages/audio/src/lib.rs`. Read the "WASM synthesis — architectural
decisions" section of [CLAUDE.md](../CLAUDE.md) first: the render callback must stay
allocation-free. Rebuild with `npm run build:wasm` and test with `npm run test:rust`.

### Writing a plugin

See [PLUGINS.md](PLUGINS.md).

---

## Testing

```bash
npm test              # CLJS unit and integration tests (core, lisp, app)
npm run test:rust     # Rust AudioEngine tests
npm run test:e2e      # Playwright offline-audio browser tests
npm run test:all      # all three
```

Before pushing a ClojureScript change, also run `npx shadow-cljs compile app`. The test build
does not compile app-layer namespaces, so syntax errors and unused requires there only show
up in the app build.

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
| CLJS lint | `lint.yml` | `clj-kondo` on `packages/` and `app/src/` |
| TypeScript type-check | `lint.yml` | `npm run typecheck` (the `api/` functions) |
| Rust lint | `lint.yml` | `cargo test`, `cargo clippy -- -D warnings`, `cargo fmt --check` |
| Grammar drift check | `lint.yml` | `npm run gen:grammar`, then fails if `parser.js` changed |

To reproduce the Rust lint locally, run the three cargo commands inside `packages/audio/`.

---

## Pull requests

`main` is protected; every change goes through a pull request. A phase counts as delivered
only when it is tested, documented in USAGE.md, and marked delivered in
[ROADMAP.md](../ROADMAP.md) — see "Phase workflow" in [CLAUDE.md](../CLAUDE.md).

## Deployment

Vercel deploys automatically: every branch gets a preview deployment, and `main` goes to
production. No manual steps are needed. [DEPLOYMENT.md](DEPLOYMENT.md) covers the one-time
setup.
