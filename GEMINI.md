# REPuLse — Gemini Agent Guide

> The primary project reference is **[CLAUDE.md](CLAUDE.md)** — read it before this file.
> It contains architecture, coding conventions, build commands and the phase workflow
> rules. Phase status lives in [ROADMAP.md](ROADMAP.md).

---

## Quick command reference

| Command | Purpose |
|---------|---------|
| `npm run test` | Run all CLJS unit tests (core + lisp + app) |
| `npm run build:wasm` | Rebuild the Rust audio engine → WASM |
| `npm run dev:full` | Build WASM + start shadow-cljs watch + dev server |
| `npm run dev` | Start dev server only (WASM already built) |
| `npm run gen:grammar` | Regenerate Lisp parser after grammar changes |
