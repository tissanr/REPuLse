# REPuLse Test Plan

This plan describes the automated coverage expected after Phase TEST1.

## Standard Commands

```bash
npm test              # CLJS unit/integration tests
npm run test:rust     # Rust AudioEngine tests
npm run test:e2e      # Browser offline audio tests
npm run test:all      # Full local verification suite
./scripts/test-all.sh # Same full suite
```

Run `npm run test:all` after every code phase before marking the phase delivered.
For docs-only or prompt-only changes, `npm test` is the minimum acceptable check.

## Coverage Map

| Area | Coverage | Command |
|---|---|---|
| Pattern algebra | `seq`, `stack`, `pure`, `fast`, `slow`, `rev`, `every`, `arrange`, timing spans | `npm test` |
| Music theory | note parsing, Hz conversion, scales, chords, transpose | `npm test` |
| Parameter transforms | `amp`, `attack`, `decay`, `release`, `pan`, threading, param maps | `npm test` |
| Envelopes | envelope curve math and interpolation | `npm test` |
| Lisp evaluator | special forms, errors, bindings, macros, typo handling | `npm test` |
| Mini-notation | tokenization, parsing, subdivisions, rests, repeats, alternation | `npm test` |
| Eval-to-events integration | Lisp source -> Pattern -> queried Events | `npm test` |
| Session persistence | restore, corrupt BPM sanitization, local state shape | `npm test` |
| FX contract | plugin chain data behavior and app-level FX helpers | `npm test` |
| Rust audio engine | voices, scheduling, amp, pan, decay, pending events, finite PCM | `npm run test:rust` |
| Browser offline audio | OfflineAudioContext render, onset count, RMS, pan, silence, finite PCM | `npm run test:e2e` |
| Release build | WASM build and shadow-cljs release build | CI `release-build` |

## Browser Audio Boundary

TEST1 browser tests use `OfflineAudioContext` and report the `"offline-js"`
backend. They verify the offline render/export-style path, not live production
AudioWorklet + WASM capture.

Production browser audio capture is TEST2 scope:

```text
AudioContext -> AudioWorkletNode -> Rust/WASM AudioEngine -> captured PCM
```

## What Is Not Fully Covered Yet

- Live browser AudioWorklet + WASM PCM capture.
- Full effect-plugin audio output through the production graph.
- Randomized pattern determinism for `choose`, `sometimes`, `degrade`, and related
  functions.
- External network flows such as Freesound and remote sample repository loading.
- Full editor UI interaction beyond automated compile/test coverage.
- MIDI hardware input/output behavior.

## Adding Coverage For New Phases

When adding a feature, update the closest layer:

- Pure pattern or parameter behavior: add CLJS tests under `packages/core` or
  `packages/lisp`.
- Lisp syntax or evaluator behavior: add evaluator/integration tests.
- Rust synthesis behavior: add `AudioEngine` tests.
- Browser rendering behavior: add Playwright tests through the TEST1 harness if
  the offline path is sufficient.
- Production live audio behavior: use or extend the future TEST2 harness.

If a phase adds user-facing syntax, also update README/USAGE docs and keep the
syntax highlighting/completion/AI-docs workflows in sync.
