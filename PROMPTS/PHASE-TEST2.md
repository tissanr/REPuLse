# Phase TEST2 - Production Browser Audio Capture

## Goal

Build on TEST1 and close the remaining automated coverage gaps toward full
product-level test coverage for REPuLse.

TEST1 covers CLJS logic/integration tests, Rust `AudioEngine` DSP tests, and
browser offline rendering through the `"offline-js"` harness. TEST2's goal is to
complete the missing production-path coverage, especially true browser
AudioWorklet + WASM output:

```text
AudioContext -> AudioWorkletNode -> Rust/WASM AudioEngine -> captured PCM
```

By the end of TEST2, automated tests should cover the main functional product
surfaces: Lisp evaluation, pattern timing, Rust DSP, offline browser rendering,
live browser audio, production graph routing, representative FX output, and the
core user-visible workflows that can reasonably be tested without external
hardware or network services.

---

## Scope Stub

This is intentionally a stub, not a full implementation prompt yet.

TEST2 should investigate and implement:

- A Playwright harness that starts a real `AudioContext`, not an
  `OfflineAudioContext`.
- A reliable way to wait until `audio/worklet-ready?` is true.
- PCM capture from the production worklet output.
- Verification that the loaded backend is AudioWorklet + WASM, not JS fallback.
- Tests for live-path trigger messages, `trigger_v2`, amp, pan, decay, and voice
  cleanup.
- Targeted production-path FX tests for effects that are only meaningful through
  browser graph routing.
- Small, targeted PCM snapshots or golden fixtures where exact output stability
  is useful.
- Coverage for the major gaps documented in `docs/TESTPLAN.md` where automation
  is practical.
- Browser support decision: Chromium-only gate or cross-browser suite.

Possible capture approaches to evaluate:

- `MediaStreamAudioDestinationNode` plus `MediaRecorder` or WebCodecs decode.
- `AnalyserNode` for property-based checks where exact PCM is not needed.
- A test-only worklet tap that mirrors output samples to the main thread.
- A dedicated test processor that wraps or instruments the production processor
  without changing release behavior.

---

## Open Questions

- Which capture approach gives deterministic enough PCM for CI?
- Can the production `worklet.js` be instrumented without exposing test hooks in
  release builds?
- Are PCM snapshots stable across Chromium versions and CI machines?
- Which FX should be considered mandatory production-path tests?
- Should TEST2 run on every PR, or only on audio-related PRs if it becomes slow?
- Which remaining gaps from `docs/TESTPLAN.md` are intentionally out of scope
  because they require hardware, credentials, or unstable external services?

---

## Definition Of Done Draft

- [ ] Playwright proves the app is using AudioWorklet + WASM.
- [ ] Captured live-path PCM is finite and non-silent for a basic trigger.
- [ ] `trigger_v2` amp/pan/decay behavior is verified through the live path.
- [ ] At least one production-path FX chain is verified.
- [ ] Targeted PCM snapshots are added only where stable and valuable.
- [ ] `docs/TESTPLAN.md` is updated to show which remaining gaps TEST2 closed and
      which gaps are still intentionally manual or external.
- [ ] CI runs the suite according to the browser support decision.
