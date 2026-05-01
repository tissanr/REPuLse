# Phase HRD1 — Hardening: AST Patching, Fetch Validation, Reproducible Builds

## Goal

Address the highest-risk technical debt items identified in a codebase review:
replace fragile regex-based editor patching with a paren-aware tokenizer, harden
all remote fetch paths against non-2xx responses, and make Rust builds
reproducible by pinning the toolchain.

No new user-visible features. No Lisp language changes. No audio changes.

---

## Background

The review flagged five areas of concern. This phase addresses items 1–3 and 5
(items 4 and the app/audio split are deferred to future refactor phases):

1. **Regex editor patching** — three functions in `eval_orchestrator.cljs` used
   ad-hoc regexes to find and replace numbers in live editor code. Regexes matched
   inside comments, strings, and nested forms, and broke silently when the code had
   duplicate parameter names across tracks.

2. **Unvalidated remote fetch** — `samples.cljs` called `.json()` / `.text()` /
   `.json()` on fetch responses without checking `response.ok` first. A 404 or
   rate-limit response would be parsed as if it were a valid manifest, producing
   silent failures or JS exceptions in the console.

3. **Non-reproducible Rust builds** — `netlify.toml` ran `rustup default stable &&
   curl | sh` at deploy time, meaning two builds of the same commit could use
   different Rust and wasm-pack versions. No `rust-toolchain.toml` existed.

---

## What was built

### `app/src/repulse/lisp_patcher.cljs` (new file)

A minimal Lisp tokenizer and paren-aware form scanner that replaces all three
regex-based patching functions. The tokenizer produces a flat vector of
`{:t :from :to}` tokens, skipping comments and string literals. Four public
functions:

| Function | Purpose |
|---|---|
| `find-param-num` | Find `(param-name NUMBER)` within a scoped region |
| `find-fx-named-param-num` | Find `:param-name NUMBER` inside `(fx :name ...)` |
| `find-fx-pos-param-num` | Find positional `NUMBER` in `(fx :name NUMBER ...)` |
| `find-fx-form-close` | Find the closing `)` offset of `(fx :name ...)` |

All functions take explicit `scope-start` / `scope-end` character offsets so the
caller controls which region of the document is searched.

### `app/src/repulse/eval_orchestrator.cljs` (updated)

- Added `[repulse.lisp-patcher :as patcher]` require
- Extracted `fmt-num` and `dispatch-replace!` helpers (deduplicates three copies
  of the same formatting and dispatch logic)
- `patch-param-in-editor!` — rewritten to call `patcher/find-param-num`
- `patch-fx-param-in-editor!` — rewritten to call `patcher/find-fx-named-param-num`,
  `patcher/find-fx-pos-param-num`, and `patcher/find-fx-form-close`
- `patch-per-track-fx-param-in-editor!` — rewritten to scope both searches to the
  `(track :name ...)` block using character offsets

Patch correctness is now immune to:
- Parameter names that appear in comments
- Numbers inside string literals
- Nested sub-forms whose first element matches the parameter name
- Duplicate parameter names in other tracks earlier in the document

### `app/src/repulse/samples.cljs` (updated)

Added a `fetch-ok!` helper that rejects with a descriptive error when
`response.ok` is false (includes status code and URL in the message). Applied to:
- `load-manifest!` — JSON manifests
- `load-lisp-manifest!` — `.edn` manifests
- `load-github!` — GitHub tree API
- `get-buffer!` — audio file fetches for sample playback

### `rust-toolchain.toml` (new file)

```toml
[toolchain]
channel = "stable"
targets = ["wasm32-unknown-unknown"]
components = ["clippy", "rustfmt"]
```

Pins the entire project to the Rust stable channel with the wasm target and lint
components declared. `dtolnay/rust-toolchain` in CI reads this file and installs
exactly the declared components — no separate `rustup component add` step needed.

### `netlify.toml` (updated)

Removed `rustup default stable && curl https://…/wasm-pack/installer/init.sh -sSf | sh`
from the build command. The command is now:

```toml
command = "npm run build:wasm && npx shadow-cljs release app"
```

wasm-pack is installed via `cargo install --locked wasm-pack@0.14.0` in CI (already
done in `.github/workflows/ci.yml`). Netlify deploys use the Netlify build image
which has Rust pre-installed and reads `rust-toolchain.toml`.

### CI fixes

- Updated `rust-toolchain.toml` to use `channel = "stable"` (wasm-pack 0.14.0
  requires rustc ≥ 1.88; the initial pin of 1.87.0 broke the release build job)
- `components = ["clippy", "rustfmt"]` ensures the lint job finds both tools
  installed without a separate `rustup component add` step

---

## Files changed

| File | Change |
|---|---|
| `app/src/repulse/lisp_patcher.cljs` | **New** — tokenizer + form-scanner |
| `app/src/repulse/eval_orchestrator.cljs` | Updated — use patcher, deduplicate helpers |
| `app/src/repulse/samples.cljs` | Updated — `fetch-ok!` applied to all fetch chains |
| `rust-toolchain.toml` | **New** — pin stable channel + components |
| `netlify.toml` | Updated — remove curl-install from build command |

---

## Definition of done

- [x] `shadow-cljs compile app` passes with no new warnings
- [x] Slider patching updates the correct number in the editor when multiple tracks
      share a parameter name and when parameter names appear in comments
- [x] FX sliders update named params, positional params, and insert missing params
      correctly in all three slider callback paths
- [x] Fetch failures (4xx, 5xx, network error) produce `console.warn` messages with
      status codes rather than JS exceptions from body-parse attempts
- [x] `cargo check --target wasm32-unknown-unknown` uses the toolchain from
      `rust-toolchain.toml`
- [x] All CI jobs (`test`, `release-build`, `cargo-lint`, `lint`, `grammar`) pass
      on the PR

---

## Out of scope

- No split of `audio.cljs` or `app.cljs` (deferred to a future R-phase)
- No browser integration tests (deferred — remains a gap)
- No changes to `packages/core`, `packages/lisp`, or Rust audio code
- No new user-facing built-ins or language features
