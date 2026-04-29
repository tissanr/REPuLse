# REPuLse — Codex Agent Guide

> The primary project reference is **[CLAUDE.md](CLAUDE.md)** — read it before this file.
> It contains architecture, core concepts, coding conventions, phase lifecycle rules,
> syntax highlighting checklist, and the full phase status table.

---

## Codex-specific notes

### Dev server

Start the app with `npm run dev` (existing WASM build) or `npm run dev:full`
(first run, or after editing `packages/audio/src/lib.rs`).
The Shadow dev server serves `app/public` on port 3000.

Browser verification: open `http://localhost:3000` manually and check the DevTools
console. There are no automated preview tools available in this environment.

### Test baseline

```
npm run test   # → 132 tests, 421 assertions, 0 failures
```

Run this before and after changes to catch regressions.
