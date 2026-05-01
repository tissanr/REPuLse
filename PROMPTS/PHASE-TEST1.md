# Phase TEST1 — Automated End-to-End Testing

## Goal

Build a multi-layer automated test suite covering the full REPuLse pipeline from
Lisp code to audio output. This suite allows for automated verification that:
1. Lisp patterns evaluate to the correct event sequences.
2. The Rust Audio Engine processes these events correctly (DSP unit tests).
3. The integrated browser environment (WASM + Worklet) produces the expected audio PCM data.

Running `./scripts/test-all.sh` becomes the mandatory "final check" for any development session.

## Background

### Current State
- **CLJS Unit Tests:** ~1200 lines covering logic, but zero audio verification.
- **Rust Tests:** 7 basic transition tests; the `AudioEngine` itself is untested.
- **Manual Verification:** Developers must "listen" to verify sound, which is slow and prone to human error.
- **The `(export)` Primitive:** Already uses `OfflineAudioContext` to render audio to a file. We will repurpose this for testing.

## Improvements & Requirements

### 1. Browser-Based WASM Testing
We will NOT rely solely on the JS fallback synths. Playwright will run a headless Chromium instance that:
- Loads the actual WASM audio engine.
- Uses `OfflineAudioContext` (which supports `AudioWorklet` in modern Chromium).
- Assertions are made against the *real* production signal path.

### 2. PCM Snapshots
For deterministic patterns, we will implement "Snapshot Testing":
- Store a small binary or JSON representation of the expected PCM buffer.
- Compare the rendered output against the snapshot.
- This catches subtle DSP regressions (rounding errors, envelope math changes) that RMS/FFT might miss.

### 3. DSP Analysis Helpers
A dedicated utility suite for non-deterministic or complex signals:
- **RMS Power:** Verify volume levels and `(amp)` logic.
- **FFT Peaks:** Verify frequency content for filters and oscillators.
- **Onset Detection:** Verify that `(seq)` and `(fast)` produce the correct number of hits.
- **Stereo Balance:** Verify `(pan)` logic.
- **Silence/Leak Detection:** Ensure buffers return to zero after a sound ends (no DC offset or "zombie" voices).

### 4. Deterministic Randomness
The `test_api.cljs` will allow injecting a seed into the Lisp engine's PRNG. This makes patterns using `sometimes`, `choose`, etc., reproducible for testing.

### 5. Performance-Safe Rust Refactoring
We will extract the inner processing loop into `process_block_raw`. To ensure no performance regression in the WASM build, we will use `#[inline(always)]`.

## Files to Change

| File | Change |
|---|---|
| `PROMPTS/PHASE-TEST1.md` | **New**: This specification. |
| `packages/lisp/src/repulse/lisp/integration_test.cljs` | **New**: Eval-to-events integration tests. |
| `packages/audio/src/lib.rs` | Refactor `AudioEngine` to expose testable inner loops; add `mod engine_tests`. |
| `app/src/repulse/test_api.cljs` | **New**: `window.__REPULSE_TEST__` API for browser automation. |
| `app/public/test-harness.html` | **New**: Lightweight page to host the test API. |
| `e2e/audio-render.spec.ts` | **New**: Playwright suite for PCM verification. |
| `e2e/helpers/audio-analysis.ts` | **New**: DSP analysis utilities (RMS, FFT, Onset, etc.). |
| `scripts/test-all.sh` | **New**: Unified runner script. |
| `.github/workflows/ci.yml` | Add Playwright and Rust test jobs. |
| `package.json` | Add Playwright dependencies and test scripts. |

## Definition of Done

- [ ] `npm test` passes with new integration tests (Eval → Events).
- [ ] `cargo test` passes with ≥8 new AudioEngine tests (DSP logic).
- [ ] Playwright suite passes with ≥8 audio render tests covering:
    - Pure tones (frequency check).
    - Pattern onsets (sequencing check).
    - Amplitude/Panning (gain/stereo check).
    - Leak detection (silence check).
    - WASM engine verification (actual signal path).
- [ ] `./scripts/test-all.sh` exits 0 on success.
- [ ] CI successfully runs the full suite on every push.
- [ ] Documentation updated to reflect the new testing standard.
