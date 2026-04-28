# Gemini Project Guide - REPuLse

## Tech Stack
- **Language:** ClojureScript (Patterns/Lisp), Rust (Audio synthesis)
- **WASM:** `wasm-pack` for Rust -> WASM
- **Build Tool:** `shadow-cljs`
- **Editor:** CodeMirror 6 with custom Lezer grammar

## Critical Workflow Rules
- **Branch Protection:** `main` is protected. All changes must be submitted via a Pull Request (PR). Do not commit directly to `main`.
- **Rational Time:** Always use `clojure.core` rationals or `[n d]` vectors in `core`. NEVER use floats for timing.
- **Pure Core:** `packages/core` and `packages/lisp` must remain pure. No side effects, no DOM, no audio interaction.
- **Errors as Data:** Lisp evaluator returns `{:error "message"}` instead of throwing.
- **Documentation:** Updating `ROADMAP.md`, `README.md`, and `docs/USAGE.md` is MANDATORY for every delivered phase.
- **Grammar Updates:** After changing `repulse-lisp.grammar`, run `npm run gen:grammar` and commit the generated `parser.js`.

## Common Commands
- `npm run test`: Run all CLJS unit tests.
- `npm run build:wasm`: Rebuild the Rust audio engine.
- `npm run dev:full`: Clean build and start dev server.
- `npm run gen:grammar`: Regenerate the Lisp parser after grammar changes.

## Testing Strategy
- Unit tests for all pattern combinators in `packages/core/src/repulse/core_test.cljs`.
- Evaluator tests in `packages/lisp/src/repulse/lisp/eval_test.cljs`.
- Session persistence tests in `app/src/repulse/session_test.cljs`.
