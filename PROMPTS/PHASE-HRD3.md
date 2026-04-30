# Phase HRD3 — Interface Specs

## Goal

Harden REPuLse's runtime boundaries with explicit `cljs.spec` contracts, starting
with the public plugin API and then covering the data shapes most likely to cross
module, persistence, network, or user-code boundaries.

This phase is not a feature phase. It should make invalid data fail earlier, with
clearer error messages, while preserving existing user-facing behaviour for valid
programs and valid plugins.

---

## Background

REPuLse is increasingly driven by small data contracts:

- JS plugin objects loaded at runtime
- Pattern events flowing from the Lisp evaluator into the scheduler
- FX chain entries that wire Web Audio nodes
- LocalStorage and URL session snapshots
- External sample manifests and GitHub/Freesound source records
- MIDI mappings from browser devices into parameter updates

Today some of these contracts are implicit or hand-validated. Plugins are the most
obvious example: `app/src/repulse/plugins.cljs` validates methods manually, while
`docs/PLUGINS.md` documents several methods as optional. A spec pass should make
the real contract explicit in code and tests.

Use specs at boundaries and in tests. Do not add expensive spec checks inside the
tight audio scheduler loop in production.

---

## Scope

### 1. Plugin interface specs

Add specs and validation helpers for the public plugin surface:

| Contract | Shape |
|---|---|
| Plugin identity | `name` non-empty string, `type` one of `"visual"` / `"effect"`, optional `version` string |
| Visual plugin | required `mount`, `unmount`; optional `init`, `destroy` normalized to defaults |
| Effect plugin | required `createNodes`, `setParam`, `destroy`; optional `init`, `bypass`, `getParams`, `clone` — all normalized to safe defaults; absence of `clone` causes per-track instances to share state via `Object.create/assign`, which silently breaks CLJS closure-based plugins — those **must** implement `clone` |
| Host API | `audioCtx`, `analyser`, `masterGain`, `sampleRate`, `version`, `registerLisp` |
| Effect nodes | `createNodes(ctx)` returns a **JS object** `#js {:inputNode node :outputNode node}` — both properties must be connectable/disconnectable Web Audio nodes; validate with JS property access, not CLJS map destructuring |

Implementation expectations:

- Move plugin validation from ad hoc method lists toward named specs/predicates.
- Reconcile code and docs so optional methods are treated consistently.
- Validate `createNodes` return value before appending an effect to the chain.
- Preserve compatibility with class-style and plain-object ES module plugins.
- Return clear errors naming the plugin and the missing/invalid field.

### 2. Core pattern and event specs

Add low-level specs for the pure pattern engine:

- Rational time: a `cljs.core.Ratio` or integer — spec predicate `(s/or :ratio ratio? :int integer?)`; `Ratio` values carry an implicitly positive denominator and must never be constructed with zero denominator; do **not** spec as `[integer integer]` — that is not how CLJS rationals are represented at runtime
- Time span: `{:start rat :end rat}` with `start < end`
- Event: `{:value any? :whole span :part span}` plus optional `:source`
- Pattern: tagged map created by `repulse.core/pattern`

Use these specs in tests around constructors and boundary helpers. Avoid checking
every event in the scheduler hot path.

### 3. Event payload and parameter specs

Define specs for common event values consumed by the app/audio boundary:

- Keyword sample/note values
- `{:note ...}` note maps
- `{:bank kw :n number}` sample maps
- Synth maps with `:synth`, `:freq`, and voice-specific options
- Parameter keys: `:amp`, `:attack`, `:decay`, `:release`, `:pan`, `:rate`, `:begin`, `:end`, `:loop`
- Tween descriptors: `{:type :tween :curve ... :start ... :end ... :duration-bars ...}`
- MIDI note output marker: `:midi-ch` in the safe channel range

Use specs to protect constructors and external/state restoration paths. Keep audio
scheduling tolerant of old valid data where possible.

### 4. Session, sample, FX, and MIDI boundary specs

Add specs for mutable/external maps that are currently trusted after parsing:

| Area | Boundary |
|---|---|
| Session | `repulse.session/build-session-snapshot`, `load-session`, URL hash decode — validation must run **after** `migrate-legacy!` so legacy v1 sessions are upgraded to v2 shape first; the spec boundary is the v2 map, not the raw localStorage string |
| FX | global `fx/chain` entries and per-track `:fx-chain` entries |
| Samples | sample registry, manifest parse output, `loaded-sources` entries |
| MIDI | CC mappings, MIDI file export note events |

Invalid loaded state should be rejected or sanitized with a console warning rather
than partially applied.

### 5. AI tool-call interface specs

Add specs for the AI surface introduced by AI3 so the tool layer inherits the same
spec discipline as the rest of the app:

| Contract | Shape |
|---|---|
| Tool descriptor | `{:name string :description string :params map :side-effects #{:edit :eval :network :audio :none}}` |
| Edit proposal | `{:from non-neg-int :to non-neg-int :replacement string}` — `:to` must not exceed current document length; the resulting replacement must be reader-parseable |
| Eval preview result | `{:ok bool :event-count non-neg-int :duration-bars rat}` |
| Session snapshot for AI | matches shape validated by §4 session spec |
| Tool-call envelope | `{:tool keyword :args map :request-id string}` returned with `{:ok bool :result any :error (s/nilable string)}` |

Implementation expectations:

- Tool descriptors are defined as data maps and validated against a named spec before
  registration; invalid descriptors fail loudly at startup rather than silently at
  call time.
- `propose_edit` args are validated against current document length before the diff
  overlay is shown; out-of-bounds proposals return a typed error the model can read
  and retry.
- All tool input maps are validated before the executor function is called; failures
  return `{:ok false :error "..."}` rather than throwing to the agent loop.
- Tool result envelopes are validated before being sent back to the model context.

---

## Files to change

Expected files:

| File | Change |
|---|---|
| `app/src/repulse/specs.cljs` | New shared app-level specs for plugin, host, FX, session, samples, MIDI |
| `packages/core/src/repulse/specs.cljs` | New pure core specs for rationals, spans, events, patterns |
| `app/src/repulse/plugins.cljs` | Replace/extend validation with plugin specs and optional method normalization |
| `app/src/repulse/fx.cljs` | Validate effect node contracts and FX chain entries at mutation boundaries |
| `app/src/repulse/session.cljs` | Validate/sanitize loaded and built session snapshots |
| `app/src/repulse/samples.cljs` | Validate manifest parse results and loaded source records |
| `app/src/repulse/midi.cljs` | Validate CC mapping inputs and exported MIDI event maps |
| `packages/core/src/repulse/core_test.cljs` | Tests for core specs and invalid spans/events |
| `app/src/repulse/fx_test.cljs` | Tests for invalid effect plugin/node contracts |
| `app/src/repulse/session_test.cljs` | Tests for malformed persisted session rejection/sanitization |
| `docs/PLUGINS.md` | Update method requirement table to match runtime validation |
| `app/src/repulse/ai/tools.cljs` | Add spec validation for tool descriptors, call envelopes, and result shapes (§5) |
| `README.md` / `docs/ARCHITECTURE.md` | Briefly document the spec boundary strategy if useful |

The exact file split may change if a narrower namespace layout fits the codebase
better. Keep `packages/core` independent from app, DOM, audio, and JS plugin code.

---

## Design notes

### Specs vs predicates

Use `cljs.spec.alpha` **only for CLJS data**: session snapshots, FX chain entries,
events, time spans, MIDI maps, sample manifest records. `s/explain` produces useful
output for CLJS maps; it produces opaque failures for JS objects.

For **JS plugin objects** use explicit named predicate functions with hand-written
error construction:

```clojure
(defn has-method? [^js obj method]
  (fn? (aget obj method)))

(defn validate-effect-plugin! [^js plugin]
  (let [pname (or (.-name plugin) "<unnamed>")
        missing (filterv #(not (has-method? plugin %))
                         ["createNodes" "setParam" "destroy"])]
    (when (seq missing)
      (throw (js/Error. (str "[REPuLse] Plugin \"" pname
                             "\" missing: " (str/join ", " missing)))))))
```

Do not wrap `aget` checks inside `s/and` specs — `s/explain` on such specs gives
useless output and hides the JS interop boundary rather than clarifying it. Keep
plugin validation predicates named and tested independently.

### Optional plugin methods

Normalize optional plugin methods before registration:

```clojure
;; conceptual example
(ensure-method! plugin "init"    (fn [_host] nil))
(ensure-method! plugin "bypass"  (fn [_on?] nil))
(ensure-method! plugin "getParams" (fn [] #js {}))
```

This makes the documented protocol true at runtime and lets later code call methods
without repeatedly checking for nil.

### Boundary placement

Good places for validation:

- `plugins/register!`
- `fx/add-effect!` and `fx/add-track-effect!`
- `session/load-session` and URL session decode
- sample manifest parsing before `registry` mutation
- MIDI mapping creation
- tests and constructor-level assertions in `packages/core`

Avoid validation in:

- `audio/schedule-cycle!` inner loops
- per-event playback functions called many times per tick
- CodeMirror render/update loops

---

## Acceptance criteria

- Loading an invalid visual or effect plugin fails with a clear message naming the
  plugin and invalid/missing field.
- A plugin implementing only the documented required methods still loads successfully.
- `docs/PLUGINS.md` and runtime validation agree on required vs optional methods.
- Invalid `createNodes` results never enter the FX chain.
- Malformed persisted sessions are rejected or sanitized without throwing during app
  startup.
- Core specs cover rational time, spans, events, and patterns without introducing app
  dependencies into `packages/core`.
- Tests cover at least plugin validation, effect-node validation, session validation,
  and core data specs.
- AI tool descriptors are validated at registration time; an invalid descriptor (missing
  `:description` or unknown `:side-effects` keyword) throws a clear error at startup.
- `propose_edit` with `:to` beyond document length returns `{:ok false :error "..."}` and
  does not show the diff overlay.
- `npm run test` passes.

---

## Non-goals

- Do not redesign the plugin API beyond clarifying and enforcing the existing one.
- Do not add TypeScript or a JS schema validator dependency.
- Do not instrument every function with `s/fdef` by default.
- Do not add production spec checks inside the scheduler hot path.
- Do not change valid REPuLse-Lisp program semantics.
