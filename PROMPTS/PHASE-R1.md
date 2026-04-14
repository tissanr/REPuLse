# Phase R1 — App.cljs Modularization

## Goal

Split `app/src/repulse/app.cljs` (1997 lines, 16 sections, 53 functions, 11
defonces) into focused namespaces so that future features can be added without
making a god-file worse. This is a **pure refactor** — no behaviour changes, no
API changes, no new features.

**Motivation:** the upcoming Snippet Library epic (S1–S4) will add a snippet
browser panel, async network calls, auth state, and API clients. Dropping all of
that into the existing `app.cljs` would turn an already oversized file into an
unmaintainable one. This phase creates the module boundaries those features need.

**Prerequisite:** Phase R0 (correctness & safety fixes) must be delivered first.
R0 modifies `eval.cljs` and touches BPM/plugin code paths in `app.cljs`. Running
R1 on top of an unmodified R0 baseline would entangle the two efforts and make
regressions ambiguous.

**Before:** everything lives in `app.cljs`:

```
app/src/repulse/app.cljs  (1997 lines)
├── DOM helpers
├── Active code highlighting
├── Track timeline rendering
├── Playhead RAF loop
├── Session persistence wiring
├── Forward declarations
├── Environment
├── Plugin support
├── Demo templates (270 lines of data)
├── Tutorial chapters (160 lines of data)
├── First-visit flow (500 lines of UI)
├── Evaluation
├── Context panel
├── Code patching for live slider updates
├── CodeMirror editor setup
└── App bootstrap
```

**After:** `app.cljs` is a thin orchestrator (~600 lines), with content, UI,
env assembly, and plugin loading extracted into focused namespaces:

```
app/src/repulse/app.cljs                      (~600 lines — orchestrator + bootstrap)
app/src/repulse/content/demos.cljs             (~270 lines — demo template data)
app/src/repulse/content/tutorial.cljs          (~160 lines — tutorial chapters)
app/src/repulse/content/first_visit.cljs       (~500 lines — first-visit flow)
app/src/repulse/ui/editor.cljs                 (~200 lines — CodeMirror setup + highlights)
app/src/repulse/ui/timeline.cljs               (~60 lines — track timeline + playhead)
app/src/repulse/ui/context_panel.cljs          (~150 lines — context panel rendering)
app/src/repulse/env/builtins.cljs              (~120 lines — app-layer Lisp env assembly)
app/src/repulse/plugin_loading.cljs            (~80 lines — load-plugin + origin consent)
app/src/repulse/eval_orchestrator.cljs         (~100 lines — evaluate/upd glue)
app/src/repulse/session_test.cljs              (already delivered in R0)
docs/ARCHITECTURE.md                           (updated — module boundaries)
```

> **Note on `env/builtins` and `plugin_loading`:** these two extractions were
> suggested during code review as separate targets beyond the original R1 scope.
> `env/builtins.cljs` takes over `app.cljs`'s `ensure-env!` code that assembles
> the Lisp environment from all the app-layer modules (audio, fx, session, etc.).
> `plugin_loading.cljs` is the natural home for the `load-plugin` confirmation
> dialog introduced in R0 — extracting it keeps that security-sensitive code in
> one reviewable file.

---

## Background

### Why `app.cljs` grew this way

REPuLse has shipped 30+ phases. Each phase added "just a little" to `app.cljs`:
demo templates in Phase J, tutorial chapters in Phase J, first-visit flow in
Phase J, context panel in Phase E, code patching in Phase E2b, track timeline in
Phase 4, session persistence in Phase D. Individually each addition was fine.
Cumulatively, `app.cljs` is now the main obstacle to safe refactoring.

### Why now

The Snippet Library epic (S1–S4) is the largest feature set ever planned for
REPuLse. S2 introduces user accounts and network code — fundamentally new
concerns that don't belong in an already-crowded orchestration file. R1 makes
the S-epic tractable by creating the module boundaries **before** they get
filled with async/auth code.

### Current test coverage

- `packages/core`: 5 test files, well-covered
- `packages/lisp`: `eval_test.cljs`, `mini_test.cljs`
- `app/src/repulse/`: **zero tests**

This is consistent with the CLAUDE.md convention ("Tests for core"), but it
means the refactor has no automated safety net. Manual smoke testing is
required at the end of R1.

---

## Files to change

| File | Change |
|------|--------|
| `app/src/repulse/app.cljs` | Remove extracted sections; keep bootstrap, top-level orchestration, init entry point |
| `app/src/repulse/content/demos.cljs` | **New** — move demo template data + `demo` built-in |
| `app/src/repulse/content/tutorial.cljs` | **New** — move tutorial chapters + `tutorial` built-in |
| `app/src/repulse/content/first_visit.cljs` | **New** — move first-visit detection, welcome flow, random demo loader |
| `app/src/repulse/ui/editor.cljs` | **New** — `make-editor`, highlight StateField/StateEffect, `highlight-range!`, `rebuild-decorations!` |
| `app/src/repulse/ui/timeline.cljs` | **New** — SVG timeline rendering + RAF playhead loop |
| `app/src/repulse/ui/context_panel.cljs` | **New** — context panel DOM building + refresh |
| `app/src/repulse/env/builtins.cljs` | **New** — `ensure-env!`, registration of app-layer built-ins into the Lisp env |
| `app/src/repulse/plugin_loading.cljs` | **New** — `load-plugin` + per-origin consent (R0 code moves here) |
| `app/src/repulse/eval_orchestrator.cljs` | **New** — `evaluate!`, `(upd)`, code-patching for slider updates |
| `shadow-cljs.edn` | Verify all new namespaces compile; no config change expected |
| `docs/ARCHITECTURE.md` | Update/add section documenting module boundaries and dependency direction |

**Not in R1** (moved to R0 prerequisites):
- `session_test.cljs` — delivered in R0 alongside the BPM coerce fix
- `load-plugin` consent logic — implemented in R0; R1 only *moves* it into `plugin_loading.cljs`

---

## Module boundaries & dependency rules

```
┌──────────────────────────────────────────────────┐
│  app.cljs  (bootstrap, orchestration)            │
├──────────────────────────────────────────────────┤
│  ui/editor  ui/timeline  ui/context_panel        │
│  eval_orchestrator  env/builtins  plugin_loading │
│  content/demos  content/tutorial  content/…      │
├──────────────────────────────────────────────────┤
│  audio  fx  session  synth  samples  bus  midi   │
│  plugins  lisp-lang/*  (existing — unchanged)    │
├──────────────────────────────────────────────────┤
│  packages/core  packages/lisp  (unchanged)       │
└──────────────────────────────────────────────────┘
```

**Rules:**
- `content/*` and `ui/*` may depend on layers below, not each other laterally
  (unless strictly necessary — e.g., `eval_orchestrator` may call `ui/editor`)
- `app.cljs` may depend on everything above the audio layer
- No circular dependencies — verified by shadow-cljs compile
- Public API of each new namespace documented at the top as a docstring

---

## Step-by-step plan

1. **Create `content/demos.cljs`** — move demo template data and `demo` built-in.
   Re-export registration via a `register!` function called from `app.cljs`.
2. **Create `content/tutorial.cljs`** — same pattern, move tutorial chapters.
3. **Create `content/first_visit.cljs`** — move the first-visit flow.
4. **Create `ui/editor.cljs`** — move CodeMirror setup, highlight infrastructure,
   and `make-editor`. Keep the DOM IDs stable.
5. **Create `ui/timeline.cljs`** — move SVG rendering and RAF loop.
6. **Create `ui/context_panel.cljs`** — move context panel DOM building.
7. **Create `plugin_loading.cljs`** — move `load-plugin` + R0's confirmation dialog.
8. **Create `env/builtins.cljs`** — move `ensure-env!` and the app-layer built-in
   registrations. This should be the last module created because it depends on
   the functions extracted in steps 1–7.
9. **Create `eval_orchestrator.cljs`** — move `evaluate!`, `(upd)`, code-patching.
10. **Trim `app.cljs`** — leave only bootstrap, top-level orchestration, and the
    `init!` entry point.
11. **Update `docs/ARCHITECTURE.md`** — document module responsibilities and
    dependency direction.
12. **Manual smoke test** (see Definition of done).

---

## Definition of done

- [ ] `app.cljs` is ≤600 lines (from 1997)
- [ ] Every new namespace has a docstring at the top describing its responsibility
- [ ] `shadow-cljs compile app` succeeds with no warnings beyond the existing baseline
- [ ] `npm run test` — core + lisp + R0 tests still pass (unchanged)
- [ ] No circular dependencies between new namespaces
- [ ] No API changes — all Lisp built-ins still work identically
- [ ] `load-plugin` consent behaviour from R0 is preserved byte-for-byte in `plugin_loading.cljs`
- [ ] `ensure-env!` moved cleanly; the Lisp env built at startup contains exactly the same keys as before
- [ ] Bundle size change is within ±5% of current release build
- [ ] Hot reload (`npm run dev`) still works after edits to extracted files

**Manual smoke tests** (all must pass after the refactor):

- [ ] App boots, editor visible, no console errors
- [ ] `(seq :bd :sd :bd :sd)` — basic playback works
- [ ] `(stop)` — stops cleanly
- [ ] `(every 4 (fast 2) (seq :bd :sd :hh :oh))` — pattern transforms work
- [ ] `(fx :reverb 0.3)` — effects load and apply
- [ ] `(demo :techno)` — demo template loads into editor
- [ ] `(tutorial)` — tutorial loads
- [ ] First-visit flow triggers on fresh localStorage
- [ ] Active code highlighting flashes on event fire
- [ ] Track timeline renders and playhead sweeps
- [ ] Context panel shows BPM, bindings, effects
- [ ] Session URL sharing (`#v2:...`) still works — share + reload
- [ ] `(reset!)` clears state and reloads
- [ ] Slider updates patch code correctly (Phase E2b)
- [ ] Alt+Enter evaluates editor buffer
- [ ] Command bar accepts expressions
- [ ] No new console errors or warnings

---

## Out of scope

- No changes to `packages/core`, `packages/lisp`, `packages/audio` (Rust), or `app/public/worklet.js`
- No CSS refactor
- No new tests in R1 (R0 already added `session_test.cljs`)
- No CI/lint/typecheck gate setup (separate concern)
- No changes to `audio.cljs`, `fx.cljs`, `samples.cljs`, `synth.cljs`, `bus.cljs`, `midi.cljs`, `plugins.cljs`, `session.cljs`
- No decomposition of the `eval.cljs` builtin map (that's R2)
- No rewrites — this is a move-and-organize phase, not a redesign
- No behavioural changes, no API changes, no new features

---

## Risks

1. **No automated safety net for extracted code.** Mitigation: thorough manual
   smoke test checklist; keep commits small (one extraction per commit) so
   regressions are bisectable.
2. **Forward-declaration hell.** Several functions in `app.cljs` call each
   other across sections. Mitigation: map out the call graph before starting,
   extract leaf modules first (content/*), orchestration last.
3. **Shadow-cljs hot reload surprises.** Moving `defonce` atoms between
   namespaces can lose state across reloads during dev. Mitigation: do the
   refactor in one sitting where possible; accept one clean reload at the end.
