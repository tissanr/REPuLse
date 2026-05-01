# Phase R2 — Builtin Environment Decomposition

## Goal

Decompose REPuLse's two remaining builtin registration monoliths into focused,
domain-grouped namespaces:

- **Track A:** pure language builtins currently registered by
  `packages/lisp/src/repulse/lisp/eval.cljs`.
- **Track B:** app/audio/UI builtins currently registered by
  `app/src/repulse/env/builtins.cljs`.

This is a pure refactor. It must not change Lisp behavior, add or remove builtin
names, change public APIs, or alter app behavior.

**Current state:**

```
packages/lisp/src/repulse/lisp/eval.cljs   (~573 lines)
├── evaluator helpers
├── special forms
├── eval-form dispatch
└── make-env                               (starts around line 332)
    └── pure language builtin map

app/src/repulse/env/builtins.cljs          (~505 lines)
├── env-atom / builtin-names / seen-tracks ownership
├── app callback wiring
├── ensure-env!
│   └── app/audio/UI builtin registrations
└── builtin key snapshot
```

**Target state:**

```
packages/lisp/src/repulse/lisp/eval.cljs
└── evaluator only: helpers, special forms, eval-form, thin make-env assembly

packages/lisp/src/repulse/lisp/builtins/
├── pattern.cljs       pure Pattern constructors/transforms
├── math.cljs          arithmetic and comparison
├── music.cljs         scale/chord/transpose helpers
├── params.cljs        per-event params and transitions
├── collection.cljs    maps, lists, sequences, reducers
├── types.cljs         predicates and coercion helpers
├── synth.cljs         pure synth event constructors
└── arrangement.cljs   arrange/play-scenes

app/src/repulse/env/builtins.cljs
└── app env ownership and thin assembly only

app/src/repulse/env/builtins/
├── tracks.cljs        track transport and multi-track commands
├── fx.cljs            global and per-track fx builtin
├── samples.cljs       samples, sample banks, Freesound
├── midi.cljs          MIDI mapping, output, clock, export
├── content.cljs       snippet/demo/tutorial/gist builtins
├── export.cljs        WAV export
├── session.cljs       share/reset/session side effects
├── routing.cljs       bus/routing app builtins
└── plugins.cljs       load/unload plugin builtins
```

---

## Background

R2 originally targeted only `eval.cljs`. That is still needed, but later phases
moved most app-facing builtins into `app/src/repulse/env/builtins.cljs`, which is
now a second large registration table. R2 should therefore document and execute
the split as a two-track refactor instead of pretending the Lisp evaluator is the
only builtin environment.

R0, R1, S1, S2, S3, and S4 are already delivered or otherwise independent of
this prompt. No prerequisite phase work is required before starting R2.

Track A should be implemented first because `packages/lisp` has the stronger
unit-test safety net (`eval_test.cljs`, `mini_test.cljs`). Track B should follow
without changing the public surface of `repulse.env.builtins`; existing imports
from `app.cljs`, `eval_orchestrator.cljs`, snippet preview, embed, and context
panel must continue to work.

---

## Track A — Pure Lisp Builtins

### Files to change

| File | Change |
|------|--------|
| `packages/lisp/src/repulse/lisp/eval.cljs` | Keep evaluator and special forms; require and merge pure builtin namespaces |
| `packages/lisp/src/repulse/lisp/builtins/pattern.cljs` | **New** — Pattern constructors and transforms |
| `packages/lisp/src/repulse/lisp/builtins/math.cljs` | **New** — arithmetic and comparison |
| `packages/lisp/src/repulse/lisp/builtins/music.cljs` | **New** — music theory helpers |
| `packages/lisp/src/repulse/lisp/builtins/params.cljs` | **New** — event params, `comp`, `tween`, `env` |
| `packages/lisp/src/repulse/lisp/builtins/collection.cljs` | **New** — map/list/sequence helpers |
| `packages/lisp/src/repulse/lisp/builtins/types.cljs` | **New** — predicates and coercion helpers |
| `packages/lisp/src/repulse/lisp/builtins/synth.cljs` | **New** — pure synth event constructors and `synth` transformer |
| `packages/lisp/src/repulse/lisp/builtins/arrangement.cljs` | **New** — `arrange`, `play-scenes` |

Each namespace exports a `builtins` map. `eval.cljs/make-env` creates owned atoms
(`:*defs*`, `:*macros*`, `:*synths*`), merges the imported builtin maps, and adds
`"bpm"`, `"stop"`, plus the metadata atoms.

### Grouping rules

- **`pattern`** — `seq`, `stack`, `pure`, `fast`, `slow`, `rev`, `every`, `fmap`,
  `euclidean`, `cat`, `late`, `early`, `sometimes`, `often`, `rarely`,
  `sometimes-by`, `degrade`, `degrade-by`, `choose`, `wchoose`, `jux`, `jux-by`,
  `off`, `~`, `alt`
- **`math`** — `+`, `-`, `*`, `/`, `=`, `not=`, `<`, `>`, `<=`, `>=`, `not`,
  `mod`, `quot`, `abs`, `max`, `min`
- **`music`** — `scale`, `chord`, `transpose`
- **`params`** — `amp`, `attack`, `decay`, `release`, `pan`, `rate`, `begin`,
  `end`, `loop-sample`, `comp`, `tween`, `env`
- **`collection`** — `get`, `assoc`, `merge`, `keys`, `vals`, `conj`, `apply`,
  `list`, `count`, `nth`, `first`, `rest`, `empty?`, `cons`, `concat`, `vec`,
  `map`, `filter`, `reduce`, `range`, `str`, `symbol`, `keyword`, `name`,
  `identity`
- **`types`** — `number?`, `string?`, `keyword?`, `map?`, `seq?`, `vector?`,
  `nil?`
- **`synth`** — `sound`, `saw`, `square`, `noise`, `fm`, `synth`
- **`arrangement`** — `arrange`, `play-scenes`

### Special forms stay in `eval.cljs`

Do not move evaluator special forms into builtin namespaces:

`def`, `defn`, `let`, `fn`, `lambda`, `if`, `and`, `or`, `quote`, `do`, `->>`,
`->`, `quasiquote`, `defmacro`, `defsynth`, `loop`, `recur`

These forms control evaluation order, macro expansion, lexical binding, source
locations, or owned evaluator state. Treating them as ordinary builtin functions
would change semantics.

---

## Track B — App Builtins

### Files to change

| File | Change |
|------|--------|
| `app/src/repulse/env/builtins.cljs` | Keep public facade, owned atoms, callback wiring, `ensure-env!`, `init!`, and env assembly |
| `app/src/repulse/env/builtins/tracks.cljs` | **New** — `track`, `play` error, mute/solo/clear/tracks/upd/tap |
| `app/src/repulse/env/builtins/fx.cljs` | **New** — context-aware `fx` builtin |
| `app/src/repulse/env/builtins/samples.cljs` | **New** — `samples!`, `sample-banks`, `bank`, Freesound builtins |
| `app/src/repulse/env/builtins/midi.cljs` | **New** — `midi-sync!`, `midi-map`, `midi-out`, `midi-clock-out!`, `midi-export` |
| `app/src/repulse/env/builtins/content.cljs` | **New** — `snippet`, `demo`, `tutorial`, `load-gist` |
| `app/src/repulse/env/builtins/export.cljs` | **New** — `export` WAV rendering |
| `app/src/repulse/env/builtins/session.cljs` | **New** — `share!`, `reset!` |
| `app/src/repulse/env/builtins/routing.cljs` | **New** — `bus` app builtin |
| `app/src/repulse/env/builtins/plugins.cljs` | **New** — `load-plugin`, `unload-plugin` |

Each app builtin namespace exports one map or factory function. Use factory
functions whenever the builtin needs callbacks, editor refs, `evaluate-ref`, or
other runtime dependencies. `app/src/repulse/env/builtins.cljs` remains the only
namespace that owns:

- `env-atom`
- `builtin-names`
- `seen-tracks`
- `evaluate-ref`
- app callback storage
- `init!`
- `ensure-env!`
- final builtin key snapshot
- `:*register-synth-fn*` injection

Preserve the current `repulse.env.builtins` public API so existing requires do
not change.

---

## Key-set verification

R2 must include an explicit before/after key-set check for both tracks.

For Track A:

- Capture the keys produced by the pre-refactor `leval/make-env` in a scratch
  branch or temporary REPL/static check.
- Capture the keys produced by the refactored `leval/make-env`.
- The string builtin key sets must match exactly.
- Metadata keys `:*defs*`, `:*macros*`, and `:*synths*` must remain present and
  intentionally owned by `make-env`.

For Track B:

- Capture the keys in `@repulse.env.builtins/env-atom` after `ensure-env!` before
  and after the refactor.
- The app builtin string key sets must match exactly.
- No string builtin may be missing or duplicated.
- Metadata key `:*register-synth-fn*` must remain intentionally injected by the
  app env assembler.

If a full automated key-parity test is practical, add it. If not, document the
exact static or REPL check used in the PR notes.

---

## Definition of done

- [ ] Track A is complete: `eval.cljs` no longer contains the pure builtin map.
- [ ] Track B is complete: `env/builtins.cljs` is an assembler/facade, not a large
      app builtin implementation file.
- [ ] Every existing Lisp builtin exists in exactly one new domain namespace or is
      intentionally retained as evaluator/app-owned metadata.
- [ ] No builtin behavior changes.
- [ ] No new builtin names.
- [ ] No public `repulse.env.builtins` API changes.
- [ ] No circular dependencies between builtin namespaces.
- [ ] Existing imports from app, embed, snippets, context panel, and eval
      orchestrator still compile unchanged.
- [ ] `npm run test` passes before and after the refactor.
- [ ] Existing `eval_test.cljs`, `mini_test.cljs`, app session tests, and snippet
      sandbox behavior pass unchanged.
- [ ] Key-set parity for Track A and Track B is verified.
- [ ] `docs/ARCHITECTURE.md` documents both builtin namespace layouts.

---

## Manual smoke test target

After implementation, run `npm run dev` and manually evaluate representative code
covering:

- pure pattern/math/music/params/collection/type builtins
- named track playback and transport commands
- global and per-track `fx`
- samples/bank usage
- snippet/demo/tutorial/gist builtins
- WAV export
- MIDI export/output shape where available
- bus/env/defsynth usage

---

## Out of scope

- No changes to builtin behavior
- No new builtins
- No changes to reader syntax
- No changes to CodeMirror grammar, completions, or hover docs unless a compile
  error exposes a missed import during the refactor
- No audio engine or Rust changes
- No user documentation rewrite beyond documenting the internal builtin layout in
  `docs/ARCHITECTURE.md`
