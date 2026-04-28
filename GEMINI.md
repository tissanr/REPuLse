# REPuLse: Gemini CLI Context

REPuLse is a browser-based live coding instrument where music is written in a minimal, purpose-built Lisp. It uses a pattern algebra engine (inspired by TidalCycles/Strudel) implemented in ClojureScript and a synthesis engine in Rust/WASM.

## Project Overview

- **Core Mission:** A REPL-driven workflow for music creation where patterns are pure functions of time.
- **Key Technologies:**
  - **ClojureScript:** Used for the pattern algebra (`packages/core`), Lisp interpreter (`packages/lisp`), and frontend UI/orchestration (`app/`).
  - **Rust (WASM):** Synthesis engine (`packages/audio`) compiled to WASM via `wasm-pack`.
  - **Web Audio API:** Scheduling and audio output.
  - **CodeMirror 6 / Lezer:** For the editor and syntax highlighting.
  - **shadow-cljs:** Build tool for ClojureScript.

## Architecture

The project is structured into three strictly separated layers:

1.  **Lisp Layer (`packages/lisp`):** Reader and Evaluator for REPuLse-Lisp. Returns pattern descriptions.
2.  **Pattern Engine (`packages/core`):** Pure pattern algebra. Functions that map a `TimeSpan` to a list of `Event`s using **rational arithmetic**.
3.  **Audio Layer (`packages/audio` & `app/audio.cljs`):** Rust synthesis engine and JS scheduler that pulls events from patterns and triggers sound.

## Building and Running

### Prerequisites
- Node.js 18+
- Java 11+ (for shadow-cljs)
- Rust + `wasm-pack` (for WASM synthesis)

### Key Commands

- **Initial Setup:**
  ```bash
  npm install
  npm run build:wasm
  ```
- **Development:**
  - `npm run dev`: Start shadow-cljs watch for the app (no WASM build).
  - `npm run dev:full`: Build WASM and then start shadow-cljs watch.
- **Testing:**
  - `npm run test`: Run unit tests for `packages/core` using Node.js.
- **Grammar Generation:**
  - `npm run gen:grammar`: Regenerate the Lezer parser after editing `.grammar` files.

## Development Conventions

- **Pure Functions:** Maintain purity in `core` and `lisp` packages. Side effects (audio, DOM) are restricted to the `app/` layer.
- **Rational Time:** ALWAYS use rational numbers (e.g., `1/4`, `3N`) for time in the pattern engine. Never use floats for timing.
- **Dependency Isolation:**
  - `packages/core` and `packages/lisp` must NOT have external dependencies (only `cljs.core` and `cljs.test`).
  - `packages/audio` should only use `web-sys` for Web Audio bindings.
- **Error Handling:** Return `{:error "message"}` maps from the Lisp evaluator instead of throwing exceptions.
- **Syntax Highlighting:** When adding new built-ins, update the Lezer grammar AND the completions in `app/src/repulse/lisp-lang/`.
- **Documentation:** Follow the "Phase Lifecycle Rules" in `CLAUDE.md`. Update `README.md`, `ROADMAP.md`, and `docs/USAGE.md` as features are delivered.

## Directory Structure

- `packages/core/`: Pattern algebra engine.
- `packages/lisp/`: REPuLse-Lisp interpreter.
- `packages/audio/`: Rust synthesis engine.
- `app/`: Frontend application, UI, and audio scheduling.
- `PROMPTS/`: Detailed specifications for project phases.
- `docs/`: Comprehensive documentation (Architecture, Usage, Plugins).
