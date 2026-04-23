# Phase CI1 — CI Pipeline

## Goal

Add a GitHub Actions CI pipeline that runs on every pull request and push to `main`.
Catches regressions before merge, enforces code quality across the CLJS + Rust stack,
and guards a documented pitfall (`npm run gen:grammar` drift). Deployment (CD) is
unchanged — Vercel continues to handle preview and production via git integration.

```
# Before — no CI. Tests only run when a contributor remembers to.
# After  — every PR and push to main shows:
#
#   ✓ test          (all cljs.test suites passing)
#   ✓ rust          (cargo test + clippy -D warnings + fmt --check)
#   ✓ grammar       (parser.js matches repulse-lisp.grammar)
#   ✓ release-build (shadow-cljs release compiles clean)
#   ✓ lint          (clj-kondo zero errors)
```

---

## Background

### Current state

The repo has no `.github/workflows/` directory. There is no automated CI of any kind.

**Test suite** — 134 tests across `packages/core` and `packages/lisp`, invoked locally
with `npm run test` (`npx shadow-cljs compile test && node out/test.js`). The test
runner does **not** require the WASM build; it runs pure CLJS against the pattern
algebra and Lisp evaluator.

**Rust crate** — `packages/audio/` compiles to WASM via `wasm-pack`. `Cargo.lock` is
committed. `cargo test` runs unit tests in `packages/audio/src/lib.rs` locally but
is never automated.

**Deployment (CD)** — `vercel.json` is committed and Vercel's git integration handles
it: preview deploy on every PR, production on `main`. This must remain unchanged.

**Grammar footgun** — `CLAUDE.md` documents that editing
`app/src/repulse/lisp-lang/repulse-lisp.grammar` without running `npm run gen:grammar`
has **no visible effect at runtime** — the old committed `parser.js` wins silently.
CI must catch this by regenerating the parser and diffing the output against the
committed files (`parser.js` and `parser.terms.js`).

### What CI does not need to do

- Build WASM for the `test` or `lint` jobs — the CLJS test suite is fully independent
  of the Rust crate.
- Replace Vercel — deployments continue as-is.
- Gate on code coverage metrics — not configured and out of scope for this phase.

---

## Design

### Two workflow files

Split by purpose so that cheap checks do not wait behind expensive ones:

| File | Jobs | WASM needed? | Typical warm time |
|---|---|---|---|
| `.github/workflows/ci.yml` | `test`, `release-build` | `release-build` only | ~3 min |
| `.github/workflows/lint.yml` | `lint`, `cargo-lint`, `grammar` | No | ~1 min |

Both trigger on `push` to `main` and `pull_request` (all branches).

### Caching strategy

- **npm**: use `actions/setup-node` built-in `cache: 'npm'` — keys on `package-lock.json`.
- **Cargo**: `actions/cache` on `~/.cargo/registry`, `~/.cargo/git`, `packages/audio/target`
  — keyed on `packages/audio/Cargo.lock`.
- **shadow-cljs**: `actions/cache` on `~/.m2` (Maven artifacts downloaded by shadow-cljs)
  — keyed on `shadow-cljs.edn`.

### WASM install in CI

`cargo install wasm-pack` is slow cold (~2 min) but the `~/.cargo` cache covers it after
the first run. Use `dtolnay/rust-toolchain@stable` to pin to stable Rust; it reads
from the cache on subsequent runs.

### clj-kondo placement

Standard location: `.clj-kondo/config.edn` at the repo root (not inside `.github/`).
Use the official `clj-kondo/setup-clj-kondo@v1` action to install the standalone
binary (no JVM required).

### Grammar drift detection

After running `npm run gen:grammar`, use `git diff --exit-code` on the two output files.
A non-empty diff means someone edited the grammar without regenerating — the job fails
with a clear diff in the log.

---

## Implementation

### 1. `.github/workflows/ci.yml`

```yaml
name: CI

on:
  push:
    branches: [main]
  pull_request:

jobs:
  test:
    name: Test (cljs.test)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'

      - name: Cache shadow-cljs deps
        uses: actions/cache@v4
        with:
          path: ~/.m2
          key: m2-${{ hashFiles('shadow-cljs.edn') }}
          restore-keys: m2-

      - run: npm ci
      - run: npm test

  release-build:
    name: Release build (shadow-cljs release app)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'

      - name: Cache shadow-cljs deps
        uses: actions/cache@v4
        with:
          path: ~/.m2
          key: m2-${{ hashFiles('shadow-cljs.edn') }}
          restore-keys: m2-

      - name: Cache Cargo
        uses: actions/cache@v4
        with:
          path: |
            ~/.cargo/registry
            ~/.cargo/git
            packages/audio/target
          key: cargo-${{ hashFiles('packages/audio/Cargo.lock') }}
          restore-keys: cargo-

      - uses: dtolnay/rust-toolchain@stable

      - name: Install wasm-pack
        run: cargo install --locked wasm-pack@0.14.0

      - run: npm ci
      - run: npm run build:wasm
      - run: npx shadow-cljs release app
```

### 2. `.github/workflows/lint.yml`

```yaml
name: Lint

on:
  push:
    branches: [main]
  pull_request:

jobs:
  lint:
    name: CLJS lint (clj-kondo)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: clj-kondo/setup-clj-kondo@v1
        with:
          version: 'v2024.11.14'

      - run: clj-kondo --parallel --lint packages/ app/src/

  cargo-lint:
    name: Rust lint (clippy + fmt)
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: dtolnay/rust-toolchain@stable
        with:
          components: clippy, rustfmt

      - name: Cache Cargo
        uses: actions/cache@v4
        with:
          path: |
            ~/.cargo/registry
            ~/.cargo/git
            packages/audio/target
          key: cargo-lint-${{ hashFiles('packages/audio/Cargo.lock') }}
          restore-keys: cargo-lint-

      - name: cargo test
        working-directory: packages/audio
        run: cargo test

      - name: cargo clippy
        working-directory: packages/audio
        run: cargo clippy -- -D warnings

      - name: cargo fmt
        working-directory: packages/audio
        run: cargo fmt --check

  grammar:
    name: Grammar drift check
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-node@v4
        with:
          node-version: '20'
          cache: 'npm'

      - run: npm ci

      - name: Regenerate parser
        run: npm run gen:grammar

      - name: Fail if parser.js or parser.terms.js drifted
        run: |
          git diff --exit-code \
            app/src/repulse/lisp-lang/parser.js \
            app/src/repulse/lisp-lang/parser.terms.js
```

### 3. `.clj-kondo/config.edn`

Minimal config that silences unavoidable CLJS false-positives while keeping all
real error classes active.

```edn
{:lint-as
 {;; ClojureScript type/record forms
  cljs.core/deftype   clojure.core/deftype
  cljs.core/defrecord clojure.core/defrecord
  ;; cljs.test macros
  cljs.test/deftest   clojure.test/deftest
  cljs.test/is        clojure.test/is
  cljs.test/testing   clojure.test/testing
  cljs.test/use-fixtures clojure.test/use-fixtures}

 :config-in-ns
 {;; shadow-cljs test runner namespace — allow js* and other interop
  repulse.test-runner {:linters {:unresolved-symbol {:level :off}}}}

 :linters
 {;; js/... interop calls are intentional throughout app/
  :unresolved-namespace {:level :warning}
  ;; Unused private vars are common in CLJS protocol implementations
  :unused-private-var {:level :off}}}
```

### 4. `README.md` — add CI badges

Add immediately after the first `#` heading line at the top of the file:

```markdown
[![CI](https://github.com/tissanr/REPuLse/actions/workflows/ci.yml/badge.svg)](https://github.com/tissanr/REPuLse/actions/workflows/ci.yml)
[![Lint](https://github.com/tissanr/REPuLse/actions/workflows/lint.yml/badge.svg)](https://github.com/tissanr/REPuLse/actions/workflows/lint.yml)
```

### 5. `docs/CONTRIBUTING.md` — new file

```markdown
# Contributing to REPuLse

## CI pipeline

Every pull request and push to `main` runs five automated checks:

| Check | Workflow | Command |
|---|---|---|
| CLJS tests | `ci.yml` / `test` | `npm test` |
| Release build | `ci.yml` / `release-build` | `npm run build:wasm && npx shadow-cljs release app` |
| CLJS lint | `lint.yml` / `lint` | `clj-kondo --parallel --lint packages/ app/src/` |
| Rust lint | `lint.yml` / `cargo-lint` | `cargo test && cargo clippy -- -D warnings && cargo fmt --check` |
| Grammar drift | `lint.yml` / `grammar` | `npm run gen:grammar && git diff --exit-code parser.js parser.terms.js` |

## Running checks locally

```bash
# CLJS tests
npm test

# Release build (requires Rust + wasm-pack)
npm run build:wasm
npx shadow-cljs release app

# Rust checks (run inside packages/audio/)
cargo test
cargo clippy -- -D warnings
cargo fmt --check

# Grammar drift — must be clean before committing grammar edits
npm run gen:grammar
git diff app/src/repulse/lisp-lang/parser.js app/src/repulse/lisp-lang/parser.terms.js
```

## Grammar editing rule

After editing `app/src/repulse/lisp-lang/repulse-lisp.grammar`, always run:

```bash
npm run gen:grammar
```

Then commit **both** the `.grammar` file and the regenerated `parser.js` /
`parser.terms.js`. The CI `grammar` job will fail on any PR where this was skipped.

## Deployment

Deployments are handled automatically by Vercel:
- Push to any branch → preview deployment
- Push to `main` → production deployment

No manual deploy steps are needed.
```
```

---

## Files to change

| File | Change |
|---|---|
| `.github/workflows/ci.yml` | **New** — `test` + `release-build` jobs; npm + Maven + Cargo caching |
| `.github/workflows/lint.yml` | **New** — `lint` (clj-kondo) + `cargo-lint` (clippy/fmt/test) + `grammar` (drift check) |
| `.clj-kondo/config.edn` | **New** — silence CLJS false-positives; keep all real error classes |
| `README.md` | Add two badge links after the `#` heading |
| `docs/CONTRIBUTING.md` | **New** — pipeline overview + local reproduction commands |
| `CLAUDE.md` | Mark Phase CI1 `✓ delivered` in the phase table |
| `AGENTS.md` | Mark Phase CI1 `✓ delivered` in the phase table |
| `ROADMAP.md` | Flip heading to `✅ *delivered*`; add **Delivered:** section |

---

## Definition of done

- [ ] `.github/workflows/ci.yml` and `.github/workflows/lint.yml` present and syntactically valid
- [ ] `test` job: `npm test` exits 0 with all existing tests green
- [ ] `release-build` job: `npm run build:wasm && npx shadow-cljs release app` exits 0
- [ ] `lint` job: `clj-kondo --parallel --lint packages/ app/src/` exits 0 (zero errors)
- [ ] `cargo-lint` job: `cargo test`, `cargo clippy -- -D warnings`, `cargo fmt --check` all exit 0
- [ ] `grammar` job: `npm run gen:grammar` + `git diff --exit-code` exits 0 (no drift)
- [ ] All five jobs appear as required status checks on a test PR
- [ ] Caching verified: second run of each workflow completes in under 3 minutes
- [ ] README displays both badges (green) after merging to `main`
- [ ] `docs/CONTRIBUTING.md` exists and covers all five local reproduction commands
- [ ] Vercel preview and production deployments continue working unchanged
- [ ] Introducing a deliberate test failure causes `test` job to fail and block merge
- [ ] Editing the grammar without regenerating causes `grammar` job to fail and block merge
