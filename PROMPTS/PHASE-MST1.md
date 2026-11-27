# Phase MST1 — Master Output Stage & Loudness

> **Spec status:** full, with three owner decisions open ([Owner decisions](#owner-decisions)).
> Each has a recommendation; the implementer follows the recommendation unless the owner
> answered differently before the build starts.
>
> **Automation:** 🤖👂 auto + human check. Loudness, peaks and true peaks are measured in
> offline renders and live captures; "does it sound as loud as a streaming track without
> pumping or dulling the mix" needs a human listen ([Listen check](#listen-check)).

**Milestone:** 1 — Tidal-Lisp core (sound side track, next to FX1 and KIT1)
**Depends on:** FX1 (the look-ahead limiter worklet `app/public/worklets/limiter-processor.js`
and the ±8 engine output bound).

## Goal

REPuLse plays far quieter than streaming audio. Measured on `dev` (2026-11-24, live and
offline export, same results within 0.3 dB): a drum loop, a saw bass and a three-track
mix sit at **−20 to −25 dB RMS** with peaks at −3 to −6 dBFS. YouTube and Spotify normalise
to about **−14 LUFS**, so REPuLse is 6–11 dB quieter than what users hear right before
they open it. This phase adds a fixed **output stage** at the end of the master chain:
make-up gain followed by an always-on true-peak limiter at −1 dBTP, so a typical patch
lands near −14 LUFS without clipping. It adds a listening **volume** that never touches
exports, a **loudness meter** (LUFS short-term/integrated + true peak) in the status head
and in the export message, and it re-tunes the default master compressor against
measurements.

Before (today):

```lisp
(bpm 120)
(track :d (seq :bd :sd :bd :sd))
(track :b (->> (seq :c2 :_ :eb2 :_) (synth :saw)))
;; plays at about −21 dB RMS; to get louder you raise every amp and add
;; (master (fx :limiter -1)) yourself, and nothing tells you how loud it is
```

After:

```lisp
(bpm 120)
(track :d (seq :bd :sd :bd :sd))
(track :b (->> (seq :c2 :_ :eb2 :_) (synth :saw)))
;; plays at about −14 LUFS, never above −1 dBTP; the status head shows
;; "−14.2 LUFS · TP −1.0"

(master :gain 3 (fx :reverb 0.2))   ; mix 3 dB hotter into the same ceiling
(master :gain -6)                   ; quieter mix, more dynamics
(volume -12)                        ; listen quieter, exports unchanged
(export 4)                          ; → "exported all tracks — 4 cycles (offline) · −14.1 LUFS · TP −1.0 dBTP"
```

## Owner decisions

These change how every existing session sounds, so the owner decides them. FX1's Q1
("limiter opt-in") is about the `(fx :limiter …)` plugin and stays as it is; the output
stage limiter below is a separate, fixed safety stage.

| Question | Recommendation | Why |
|---|---|---|
| **Q1** — Always-on output limiter with default make-up gain? | **Yes.** Default make-up gain is the value that brings the reference mix (R1, below) to −14 LUFS ±1, expected around +7 to +10 dB; ceiling −1 dBTP. | Without a default nothing changes for users; without the limiter the gain clips. |
| **Q2** — Does `(export …)` include make-up gain and limiter? | **Yes for the mixdown, no for stems** (`master? false` stays unity). | The mixdown should sound like what you heard; stems go into a DAW that does its own mastering. |
| **Q3** — Is `(volume …)` saved in the session or only a per-browser setting? | **Per browser** (localStorage, like the inspector width), also settable from code. | Monitor level belongs to the listener's room, not to the piece. |

## Background

### Master signal path today (`app/src/repulse/audio.cljs`)

`build-master-chain!` (around line 69) builds:

```
sources → master-gain → [global FX chain…] → master-tap → [recorder] → analyser → destination
```

- `master-gain` (`defonce master-gain`) is the entry point. Voices, the WASM worklet
  (`(.connect node @master-gain 0)`, line ~96), orbit busses and per-track chains
  (`fx/rewire-track!`) all end here. `midi-map … :amp` writes its gain directly
  (`env/builtins/midi.cljs:63`), and the offline render copies its value
  (`offline_render.cljs` `build-graph!`, ~line 177).
- `fx/rewire!` (`app/src/repulse/fx.cljs`) reconnects `master-gain → effect1 → … → master-tap`
  whenever the global chain changes. `fx/default-order` keeps `limiter` last.
- `master-tap` is a permanent pass-through GainNode. The recorder worklet (SONG1 real-time
  export) is inserted between `master-tap` and `analyser-node` (`audio.cljs` ~line 145).
- `analyser-node` (fftSize 2048) feeds the visual plugins via the plugin host
  (`app.cljs` ~line 127 `:analyser`).

### Engine level

`packages/audio/src/lib.rs` `clamp_out` bounds the dry output at ±8 (`OUT_BOUND`, FX1 Q2);
overs above ±1 reach the browser output and are hard-clipped there, or by the WAV writer on
export. Voice levels are conservative so several tracks can sum without clipping.

### Default master compressor (`app/src/repulse/plugins/compressor.cljs`)

Loaded by default (`app.cljs` ~line 524): a `DynamicsCompressorNode` at threshold −24 dB,
knee 10, ratio 4:1, attack 3 ms, release 250 ms, fully wet. There is no explicit make-up
parameter; Web Audio applies its own automatic make-up gain inside the node. Nobody has
measured what this stage does to loudness and crest factor. `(master (fx :off :compressor))`
switches it off (used by the FX1 Q2 test).

### Limiter (`app/public/plugins/limiter.js`, `worklets/limiter-processor.js`)

FX1 look-ahead peak limiter: 5 ms delay, `ceiling` (default −1, −24…0 dB), `release`
(0.1 s). It limits sample peaks, not true (inter-sample) peaks.

### `(master …)` (`app/src/repulse/env/builtins/fx.cljs`)

`run-master!` evaluates the `(fx …)` children once per evaluation (one master per project)
and returns `"=> master: <n> fx"`. It takes no options today.

### Offline export (`app/src/repulse/offline_render.cljs`)

`build-graph!` builds master gain + global chain + per-track chains in an
`OfflineAudioContext`; `master? false` (SK1 stems) uses unity gain and no global chain.
The export message is `exported all tracks — <n> cycles (offline)`.

### Tests that pin today's levels

- `e2e/fx-effects.spec.ts` — `Q2` (hot synth mix through `(fx :limiter -1)`), `X1–X4`,
  `REVERB_CLASSIC_RMS_DB = -69.5` (bench renders, not the app master, so unaffected).
- `e2e/helpers/app-export.ts` `exportBuffer` (full app export), `e2e/helpers/audio-analysis.ts`
  (`rms`, `peakDb`, `nullDb`, …).
- Export-parity and stem specs under `e2e/` compare exports with each other; any spec that
  compares an absolute export level with a constant must be found and re-baselined.

## Implementation

Build in the order **A (measurement) → B (output stage) → C (controls) → D (meter UI) →
E (compressor re-tune) → F (docs & tests)**. One phase, one PR; parts may be separate
commits.

### A — Loudness measurement (shared code, tested first)

1. New `app/src/repulse/loudness.cljs`: pure functions over `Float32Array` channels
   implementing ITU-R BS.1770-4 / EBU R128:
   - K-weighting (the two BS.1770 biquads, coefficients computed for any sample rate),
   - momentary (400 ms) and short-term (3 s) loudness,
   - integrated loudness with the −70 LUFS absolute and −10 LU relative gates,
   - true peak by 4× oversampling (BS.1770 Annex 2 polyphase FIR).
   No DOM, no audio nodes, so it runs in `cljs.test`.
2. `app/src/repulse/loudness_test.cljs` against EBU Tech 3341 reference signals generated
   in the test: a stereo 1 kHz sine at −23 dBFS → −23.0 LUFS ±0.1; the same at −33 dBFS →
   −33.0 ±0.1; a 997 Hz sine at 0 dBFS sampled so peaks fall between samples → true peak
   ≥ sample peak and within 0.5 dB of +0 dBTP; silence → `-Infinity` (gated out).
3. Port the same functions to `e2e/helpers/audio-analysis.ts` (`lufsIntegrated`,
   `truePeakDb`) so Playwright specs measure exports with the same algorithm.

### B — Output stage

4. `audio.cljs`: add `output-stage` between `master-tap` and the recorder:

   ```
   … → master-tap → out-gain (make-up) → out-limiter (true-peak, −1 dBTP)
       → [recorder] → analyser → monitor-gain (volume) → destination
   ```

   - `out-gain`: GainNode, default `DEFAULT-MAKEUP-DB` (Q1; set from measurement R1).
   - `out-limiter`: a new processor `app/public/worklets/output-limiter-processor.js`
     based on the FX1 limiter but measuring the **4× oversampled** peak in its detector
     (look-ahead 5 ms, release 0.1 s, ceiling −1 dBTP). Keep `process()` allocation-free
     like the FX1 worklets. Until the worklet has loaded, connect `out-gain` straight
     through with gain forced to 1 (no make-up without a limiter, so nothing clips).
   - `monitor-gain`: GainNode after the analyser, so the meter and visuals show the mix,
     not the listening volume, and the recorder/export never see it.
5. `offline_render.cljs` `build-graph!`: with `master? true`, wire the global chain into
   an offline `out-gain → out-limiter` (same worklet module, added to the offline context)
   before `.-destination`. With `master? false` (stems), no output stage (Q2). No monitor
   gain offline.
6. Keep `master-gain` as the mix input: `midi-map … :amp`, sidechain ducking and the
   offline copy of its value keep working unchanged.

### C — Controls in code

7. `(master :gain db …)`: `run-master!` accepts leading keyword options before the
   `(fx …)` children. `:gain` sets `out-gain` relative to the default make-up
   (`:gain 0` = default, `:gain -6` = 6 dB quieter into the limiter), range −24…+12.
   A re-evaluation without `:gain` resets it to 0. Return value becomes
   `"=> master: <n> fx · gain +3 dB"` when `:gain` ≠ 0. Unknown keywords are an eval
   warning listing `:gain`.
8. `(volume db)`: sets `monitor-gain`, range −60…0 (0 = unity), persisted per browser
   (Q3). `(volume)` returns the current value as `"volume: -12 dB"`. Not part of exports,
   not reconciled away by WS5 (it is a listener setting, like `bpm` is not).
9. Register `volume` per CLAUDE.md Rule 3 (grammar, `npm run gen:grammar`,
   `completions.js`, `builtin_meta.edn`, `npm run gen:ai-docs`). `master` already exists;
   update its `builtin_meta.edn` entry and hover doc for `:gain`.

### D — Meter UI

10. A `loudness-meter` worklet tap on the analyser branch (or `analyser-node` reads at
    ~10 Hz, whichever keeps the main thread under 1 ms per update) computes short-term
    LUFS and true peak with the part-A algorithm.
11. `ui/status_head.cljs`: show `−14.2 LUFS · TP −1.0` while playing; true peak turns
    the warning colour at > −1.0 dBTP held for 1 s; `—` when stopped. Integrated LUFS
    since the last evaluation is in the tooltip.
12. Export message: append ` · <integrated> LUFS · TP <true peak> dBTP` to
    `exported all tracks — <n> cycles (offline)` (one decimal place). Stems keep the old text.

### E — Default compressor re-tune

13. Measure the reference set (R1–R4 below) with the compressor on and off, before and
    after the output stage. Record integrated LUFS, true peak and PLR (peak-to-loudness
    ratio). If the default compressor lowers PLR by more than 3 LU on R1 or makes R2
    (sparse drums) pump audibly in the listen check, retune it (suggested start: threshold
    −18, ratio 2:1, release 0.15) or default it off; write the chosen values and the
    measurements into the ROADMAP delivered entry.

### F — Docs and test baselines

14. Re-baseline every e2e spec that compares an absolute app-export level with a constant
    (search `e2e/` for `peakDb(`, `rms(` against numbers on `exportBuffer` results). The FX1
    `Q2` test keeps its intent: with `(fx :limiter -1)` the export peak stays ≤ −1 dBFS.
15. `docs/USAGE.md`: new "Loudness & monitoring" section (output stage diagram, `(master
    :gain …)`, `(volume …)`, the meter, what −14 LUFS means, stems are unmastered); update
    the "Mixing" section, which says overs are clipped by the audio output.

### Reference set (used by A–F)

| ID | Code (all at `(bpm 120)`, 4 cycles) |
|---|---|
| R1 | `(track :d (seq :bd :sd :bd :sd))` `(track :h (fast 2 (seq :hh :oh)))` `(track :b (->> (seq :c2 :_ :eb2 :_) (synth :saw)))` |
| R2 | `(track :d (seq :bd :_ :_ :sd))` |
| R3 | `(track :s (->> (seq :c4 :e4 :g4 :c5) (synth :sine)))` |
| R4 | the FX1 Q2 hot mix: 16 tracks of `(->> (seq :c3 :c3) (synth :sine) (amp 1))` |

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/loudness.cljs` | new — BS.1770 K-weighting, momentary/short-term/integrated LUFS, true peak |
| `app/src/repulse/loudness_test.cljs` | new — EBU Tech 3341 reference signals |
| `app/public/worklets/output-limiter-processor.js` | new — true-peak look-ahead limiter |
| `app/src/repulse/audio.cljs` | `build-master-chain!`: `out-gain`, `out-limiter`, `monitor-gain`; worklet load + pass-through fallback; recorder insertion point moves after the output stage |
| `app/src/repulse/offline_render.cljs` | `build-graph!`: output stage for mixdowns, none for stems; integrated LUFS/true peak for the export message |
| `app/src/repulse/env/builtins/fx.cljs` | `run-master!`: `:gain` option, return text, warning for unknown options |
| `app/src/repulse/env/builtins/*.cljs` | `volume` built-in (? which namespace — next to `bpm`) |
| `app/src/repulse/plugins/compressor.cljs` | defaults per part E (may stay unchanged) |
| `app/src/repulse/ui/status_head.cljs` (+ `status_model.cljs`) | LUFS / true-peak readout |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` → `npm run gen:grammar` | `volume` |
| `app/src/repulse/lisp-lang/completions.js` | `volume`; `master` detail mentions `:gain` |
| `app/src/repulse/content/builtin_meta.edn` → `npm run gen:ai-docs` | `volume`, `master :gain` |
| `e2e/helpers/audio-analysis.ts` | `lufsIntegrated`, `truePeakDb` |
| `e2e/loudness.spec.ts` | new — reference set, export message, `(master :gain …)`, `(volume …)` |
| `e2e/*.spec.ts` | re-baseline absolute export levels (?) |
| `docs/USAGE.md`, `README.md` (?) | "Loudness & monitoring"; Mixing section |
| `ROADMAP.md` | delivered entry with the measured table |

## Definition of done

- [ ] `loudness_test.cljs`: a stereo 1 kHz sine at −23 dBFS measures −23.0 ±0.1 LUFS integrated; at −33 dBFS −33.0 ±0.1; silence is gated out; inter-sample test signal gives true peak ≥ sample peak.
- [ ] R1 exported with defaults measures −14 ±1 LUFS integrated and ≤ −1.0 dBTP true peak (`e2e/loudness.spec.ts`, using `truePeakDb`).
- [ ] R4 (16 hot sines) exported with defaults: true peak ≤ −1.0 dBTP, no run of more than 3 equal samples above 0.85 (no hard clipping).
- [ ] R3 (single sine arpeggio) exported with defaults is louder than today by at least 6 dB integrated and its THD at 261.6 Hz stays below 1 % (the limiter does not distort a signal it barely touches).
- [ ] `(master :gain -6)` on R1 lowers integrated loudness by 5–7 LU; `(master :gain 3)` raises it while true peak stays ≤ −1.0 dBTP; re-evaluating without `:gain` returns to the default level.
- [ ] `(master :gain 3 (fx :reverb 0.2))` returns `=> master: 1 fx · gain +3 dB`; `(master :loud 3)` shows an eval warning that lists `:gain`.
- [ ] `(volume -12)` lowers the live destination level by 12 ±0.2 dB (captured with a destination tap as in the measurement spec) while an export made right after measures the same LUFS as with `(volume 0)`; `(volume)` returns `volume: -12 dB`; the value survives a page reload.
- [ ] Live playback of R1 measures the same integrated loudness as its export within 1 LU (destination tap).
- [ ] Stem export (`master? false`) of R1 is bit-identical to today's stem export (no output stage on stems).
- [ ] The status head shows short-term LUFS and true peak while playing (`−14.x LUFS · TP −1.x`) and `—` when stopped; it updates at least 4 times per second.
- [ ] The export message ends with ` · <x.x> LUFS · TP <x.x> dBTP` for mixdowns; stem exports keep the old text.
- [ ] Before the output-limiter worklet has loaded, the output stage runs at unity gain (no make-up), checked by a test that delays the worklet module.
- [ ] The default compressor decision (part E) is recorded with the measured LUFS/PLR table for R1–R4, compressor on vs off.
- [ ] `volume` passes Rule 3 (grammar, `parser.js`, `completions.js`, `builtin_meta.edn`, `builtins.json` committed together); `docs/USAGE.md` has the "Loudness & monitoring" section with examples for `(master :gain …)` and `(volume …)`.
- [ ] `npm run test:all` passes; the listen check below is ticked in the PR.

### Listen check

Headphones, then speakers, a YouTube music video open in another tab for reference.

- L1 R1 at default: about as loud as the YouTube reference; no pumping on the kick.
- L2 R2 (sparse kick/snare): transients still snap, no breathing between hits.
- L3 R3: a clean sine arpeggio, no added grit compared with `(master :gain -12)`.
- L4 R4: loud, no crackle, no audible distortion beyond the summed sines' own beating.
- L5 Drag `(master :gain …)` through an inline slider from −12 to +6: smooth, no clicks.
- L6 `(volume -20)`: quieter in the room, meter reading unchanged.

## What NOT to do

- No automatic gain control or loudness normalisation that moves the gain over time
  (AGC pumps and makes live coding unpredictable); the make-up gain is fixed.
- No per-track loudness meters, no LUFS history graph, no spectrum or K-meter views —
  a later visual phase (VIZ1 owns track visuals).
- Don't change voice or sample levels in the Rust engine or in `samples.cljs`; the output
  stage fixes loudness at one place.
- Don't change the `(fx :limiter …)` plugin or its opt-in rule (FX1 Q1); the output limiter
  is a separate fixed stage.
- No mastering chain (multiband compression, exciter, stereo widening) by default.
- No per-platform loudness targets (−16 Apple, −23 broadcast) as presets; `:gain` covers it.
