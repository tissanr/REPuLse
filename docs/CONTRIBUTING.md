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
