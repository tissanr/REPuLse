# Phase TEST2 - Production Browser Audio Capture

## Goal

Add true browser production-path audio verification for REPuLse:

```text
AudioContext -> AudioWorkletNode -> Rust/WASM AudioEngine -> captured PCM
```

TEST1 verifies CLJS eval, Rust engine DSP, and browser offline rendering. TEST2
closes the remaining gap: proving that the live browser Worklet/WASM path itself
loads, receives events, renders audio, and produces expected PCM.

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

---

## Definition Of Done Draft

- [ ] Playwright proves the app is using AudioWorklet + WASM.
- [ ] Captured live-path PCM is finite and non-silent for a basic trigger.
- [ ] `trigger_v2` amp/pan/decay behavior is verified through the live path.
- [ ] At least one production-path FX chain is verified.
- [ ] Targeted PCM snapshots are added only where stable and valuable.
- [ ] CI runs the suite according to the browser support decision.
