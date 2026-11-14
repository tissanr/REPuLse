# Phase FX1 — Mixing, Space & Modulation Effects

> **Spec status:** full. Three questions for the project owner are open (next section);
> each has a default the implementer follows until it is answered.
>
> **Automation:** 🤖👂 auto + human check — gains, ceilings, decay times, echo spacing,
> notch positions and channel balance are measured in offline renders; attack/release
> feel, room character and "does the mix sound better" need a human listen
> ([Listen check](#listen-check)). Follow the CLAUDE.md "FM preset tests" guidance:
> concrete level, time and frequency assertions only, no claims about how something sounds.

**Milestone:** 1 — Tidal-Lisp core (sound side track)
**Depends on:** none. All effects are track-level plugins. Coordinates with TL7b
(per-event sends use a reverb and a delay per orbit, see decision D8).

This phase is large on purpose: it holds every effect a finished song needs. It is built
in the order **0 (shared groundwork) → A (mixing) → B (space) → C (modulation & stereo)**;
each part is testable on its own and may be a separate commit, but it is one phase and
one PR.

## Questions for the project owner

These three change what existing sessions sound like, so they are not decided here.
Until one is answered the implementer takes the **default**, which keeps today's
behaviour.

**Q1 — Should a limiter sit on the master output by default?**
Today every effect is inactive until the code names it (`fx/reset-global-effects!` resets
all of them before each evaluation, so the buffer is the only source of truth). A
default-on limiter would be the one exception. It would only act on output above its
ceiling, so only sessions that clip today would sound different, but their exports would
change too, and everything would be delayed by the 5 ms look-ahead.
*Recommendation:* no. Ship it opt-in, `(master (fx :limiter -1))`, and show that form in
the USAGE "Mixing" example and the insert menu.
*Default:* off, like every other effect.
*If yes:* `limiter.js` `resetParams()` leaves the effect active with `ceiling -0.3`;
`(fx :off :limiter)` turns it off; USAGE and the ROADMAP entry say so; add an e2e test that
a `+6 dBFS` mix exports with no sample above −0.3 dBFS without any `(fx …)` in the code.

**Q2 — Should the Rust engine stop hard-clipping its output at ±1?**
`process_block_raw` clamps the summed synth voices to ±1 (`packages/audio/src/lib.rs`
≈ lines 859–863). This happens *before* the effect chain, and synth voices on tracks
without a track effect all take this path. If the clamp stays, a dense synth mix is
already distorted when it reaches the limiter, and the limiter cannot undo that; the
phase's main promise ("a dense mix no longer distorts harshly") then holds only for
samples and for tracks that have an effect.
*Recommendation:* yes, replace `.clamp(-1.0, 1.0)` with a safety bound `.clamp(-8.0, 8.0)`
(+18 dBFS; it still stops runaway values). It changes the sound only where the synth sum
exceeds ±1 today: with no limiter the overs are then clipped by the audio output instead
of by the engine (the same hard clip, but after master gain and effects); `(export …)`
still clamps at ±1 when writing the WAV (`app/src/repulse/wav.cljs` lines 40–41).
*Default:* keep the clamp; no Rust change in this phase.
*If yes:* build step A7.

**Q3 — What should the positional value of `(fx :delay 0.4)` set?**
USAGE.md ("`(fx :delay 0.5) ; delay wet = 0.5`") and the insert menu ("`(fx :delay mix)`")
treat it as the wet level.
The code sets the delay **time** (`delay.js` line 32, `FX-PRIMARY-PARAM` in
`slider_model.cljs`), and since `wet` starts at 0 the form is silent on its own.
*Recommendation:* wet, as documented. Anyone who wrote `(fx :delay 0.25 :wet 0.3)` in a
track chain to set the time would have to write `:time 0.25`.
*Default:* unchanged (time); USAGE.md and `insert-categories.js` are corrected to say
"time" and to show a form that makes sound (`(fx :delay :wet 0.35)`).
*If wet:* `delay.js` descriptor `value: "wet"`, `FX-PRIMARY-PARAM` `"delay" "wet"`.

## Goal

The effects a song needs to be mixed and placed in a room. REPuLse can compress and
duck, but it cannot equalise a track or stop the output from clipping: the engine clamps
at ±1, so a dense mix distorts harshly. The reverb has one control (the wet level) and
the delay takes its time in seconds only. The modulation family stops at chorus, phaser
and tremolo, and nothing works on the stereo image.

```lisp
;; Before
(fx :filter :freq 500 :type "highpass")
(fx :reverb 0.4)                                   ; no size, no decay
(fx :delay :wet 0.4 :time 0.25 :feedback 0.5)      ; seconds only

;; After
(track :bass  (->> (seq :c2 :_ :eb2 :_) (synth :saw)
                   (fx :eq :low 3 :low-freq 80 :mid -4 :mid-freq 400 :mid-q 1.5)))
(track :snare (->> (seq :_ :sd :_ :sd) (fx :gate :threshold -35 :release 0.08)))
(master
  (fx :eq :high 2 :high-freq 8000)
  (fx :limiter :ceiling -1))

(fx :reverb :type :hall :decay 3.5 :damp 0.4 :predelay 0.02 :wet 0.3)
(track :snare (->> (seq :_ :sd :_ :sd) (fx :reverb :type :gated :decay 0.4 :wet 0.6)))
(fx :delay :sync 3/16 :mode :ping-pong :feedback 0.5 :wet 0.35)

(fx :flanger :rate 0.25 :depth 0.7 :feedback 0.6 :wet 0.8)
(track :hats (->> (fast 8 (seq :hh)) (fx :auto-pan :rate 0.5)))
(fx :width 1.4)                       ; 0 = mono, 1 = unchanged, 2 = extra wide
```

## Decisions already made (2026-11-13)

- **The reverb size parameter is `size`, not `room`.** USAGE.md showed
  `(fx :reverb :wet 0.4 :room 0.8)`, but `reverb.js` ignores `room`; the example was
  corrected on 2026-11-13. `room` stays free for the per-event send level of TL7b (the
  Strudel meaning).
- **`(fx :reverb 0.4)` keeps its current sound.** The default reverb type is the
  generator `reverb.js` uses today (D6).
- **Few large phases.** FX1 is not split.

## Design decisions

These answer the outline's open questions. Each one names what the code or a measurement
showed. The measurements were taken on 2026-11-14 in headless Chromium 148 with an
`OfflineAudioContext` at 44.1 kHz; the numbers are in
[Measurements](#measurements-behind-the-decisions).

**D1 — Tempo reaches a plugin through a new optional method `setTempo(cycleDur, origin)`.**
The host object (`make-host`, `app/src/repulse/app.cljs` lines 117–124) has `audioCtx`,
`analyser`, `masterGain`, `sampleRate`, `version` and `registerLisp`: no tempo. A getter on
the host would not be enough anyway, because nothing would tell the plugin about a BPM
change, and per-track clones never get `init(host)` (`fx/instantiate-plugin` copies the
object). So `repulse.fx` calls `setTempo` on every instance that has it: right after
`createNodes`, and again from a watch on `audio/scheduler-state` whenever `:cycle-dur`
changes (every BPM path ends in `audio/set-bpm!`, `audio.cljs` ≈ line 794: `(bpm …)`,
tap tempo, MIDI clock, the status head, session restore). `cycleDur` is seconds per
cycle; `origin` is the context time at which cycle 0 starts: 0 in real time (the scheduler
places cycle *c* at `c × cycle-dur`, `schedule-cycle!` ≈ line 913), and
`offline-render/pre-roll-secs` (0.25 s) in an offline export. The plugin keeps `sync` in
cycles and derives seconds itself, so `getParams()` and the offline replay need no
special case. This is the one answer for `delay :sync` and `auto-pan :sync`.
On a BPM change the delay time glides to its new value over 50 ms (a short pitch bend in
the repeats, as on a tape delay), and a synced auto-pan restarts its LFO on the next
sweep boundary of the new grid.

**D2 — The limiter is an `AudioWorkletProcessor` with a 5 ms look-ahead.**
A `DynamicsCompressorNode` set up as a limiter (threshold −3 dB, knee 0, ratio 20,
attack 0) let a +6 dBFS sine through at −0.59 dBFS, 2.4 dB above the ceiling, and raised
a quiet signal by 1.7 dB with its built-in make-up gain. Both break the definition of
done. The worklet uses the standard design (required gain → minimum over the look-ahead
window → release → moving average, dry signal delayed by the look-ahead), which cannot
overshoot; a numeric prototype held a +6 dBFS signal with clicks at exactly −3.000 dBFS
and passed a −10 dBFS sine bit-identically, 221 samples late.

**D3 — The gate gets its own small worklet; no shared "dynamics" worklet.**
A gate needs a threshold with hysteresis, a hold timer and separate attack and release
times. Native nodes (`WaveShaperNode` |x| → low-pass → `AudioParam`) give one smoothing
time and no hold. Limiter and gate do not share a module file: every plugin calls
`addModule` itself, and a second `addModule` of one file in the same context runs
`registerProcessor` again for a name that exists, which throws. One file per processor
(as `bitcrusher` and `dattorro-reverb` do) avoids that.

**D4 — Unknown `fx` parameters produce an eval warning, driven by a plugin descriptor.**
Today `fx/set-param!` passes any name to `plugin.setParam`, and the plugins ignore names
they don't know: `(fx :reverb :room 0.8)` does nothing, silently. Values are passed as
the evaluator produced them: a keyword is a CLJS `Keyword` object (`cab.js` strips the
colon from `String(value)`), and a rational literal such as `3/16` is the vector `[3 16]`
(`reader.cljs` `read-number`), which no plugin can use. So a plugin may now declare
`params`, a static object ([Part 0](#part-0--shared-groundwork), 0.1); for such a
plugin the `fx` built-in checks names and enum values, converts rationals, keywords and
lists, and records warnings through the existing `builtins/record-eval-warning!` (they
appear as `… — Warning: …` in the output line and as editor diagnostics). Plugins without
`params` behave exactly as today. Only the plugins this phase creates or changes get a
descriptor.

**D5 — The reverb regenerates its impulse response debounced, caches it and crossfades
between two convolvers.** Setting `ConvolverNode.buffer` on a running convolver cuts the
tail at once. `fx/reset-global-effects!` calls `resetParams()` before every evaluation
and the parameters are set again right after, so an undebounced plugin would swap the
response twice per evaluation even when nothing changed. So: parameter changes mark the
response dirty; after 30 ms the plugin compares the wanted key with the loaded key and
does nothing when they are equal; otherwise it takes the response from a module-level
cache (or generates it), loads it into the idle convolver and crossfades over 50 ms.
`ready()` flushes the pending update, so an offline export never renders with a stale
response. `predelay` is a `DelayNode` in front of the convolver, not part of the
response, so changing it costs nothing and does not shift the convolver's level
normalisation.

**D6 — Seven reverb types; the default `:classic` is today's generator.**
`:classic` is the formula in `reverb.js` (white noise × `(1 − i/N)³`, 2.5 s), with a
seeded generator instead of `Math.random()` so offline renders are reproducible (as TL7b
requires for the orbit reverbs). `:room`, `:hall`, `:plate`, `:spring`, `:gated` and
`:reverse` are new generators. `ConvolverNode.normalize` stays `true` (the default), so
the classic response is scaled exactly as today; a per-type trim gain balances the
types.

**D7 — Whether the spring type is convincing is decided by listening.** The spec defines
a dispersive (chirped) response. If the listener rejects it in the listen check, `:spring`
is removed from the type list and the docs before delivery, and the ROADMAP entry notes
"spring reverb deferred to FX2 (waveguide model)". A weak imitation is not shipped.

**D8 — The generators live in one pure ES module, `app/public/plugins/lib/reverb-ir.js`;
TL7b does not import it.** Plugins are standalone ES modules served from `app/public`
(they may import a sibling module, as `plugin-base.js` shows), and the app build cannot
import from `app/public`. TL7b's orbit reverbs are app code and use only the classic
noise burst, which its spec already describes as a small CLJS function. The coordination
rule: both use the same PRNG (mulberry32) and the same classic formula; whichever phase
lands second copies the constants of the first. No shared code across the plugin
boundary.

**D9 — The flanger is a worklet.** Measured: a `DelayNode` in a feedback loop set to 1 ms
gives its first echo after 44 samples but the second after 216, i.e. the loop adds one
render quantum (128 samples, 2.9 ms) to the delay. Feedback below about 3 ms is therefore
impossible with native nodes, and a flanger's range is 0.5–10 ms. Feed-forward-only below
3 ms would change the effect's character at the most used settings. `vibrato` has no
feedback and stays native.

**D10 — `width` works per track and on the master; per track it only widens what is
already stereo.** A track with an effect chain is fed by JS voices only (the worklet can
only output to the master gain, `worklet-trigger-v2!` ≈ line 438), and every JS voice
ends in a `StereoPannerNode` (`make-kick` … `make-noise`, `samples/play!` line 314). So
the chain input is stereo, but a voice at `pan 0` is the same signal on both sides, and
the mid/side matrix has no side signal to scale. Such a track needs `(pan …)` on its
events, or `:haas`, which delays one side and works on mono material. On the master the
signal is stereo wherever tracks are panned.

**D11 — `auto-pan` is an equal-power balance, not an LFO on a `StereoPannerNode`.**
Measured: a `StereoPannerNode` fed a stereo signal with equal channels changes total
power by 3.0 dB between centre and hard left (−3.01 dB vs 0 dB). The definition of done
allows ±1.5 dB, and on the master a panner would also fold both channels to one side.
Two gain nodes (left, right) driven through `WaveShaperNode` sine/cosine curves keep the
power constant and leave each channel on its own side.

**D12 — The default chain order no longer depends on load timing.** The auto-load loop
(`app.cljs` lines 439–461) imports all plugins at once and calls `fx/add-effect!` in the
order the imports resolve, so the documented order is only the usual case. A limiter
must be last, always. `repulse.fx` gets a `default-order` vector; `add-effect!` sorts the
chain by it (stable; plugins not in the list, i.e. user-loaded ones, go after `cab` and
before `auto-pan`, `width`, `limiter`).

**D13 — Effect names are not added to the grammar.** Effects are selected with keywords
(`:eq`), which are `Keyword` tokens. A `BuiltinName` entry highlights the bare symbol,
and `eq`, `gate` and `width` are likely user variable names. `scripts/gen_ai_docs.mjs`
accepts completions that are not grammar names, so the new effects get `completions.js`,
`hover.js` and `builtin_meta.edn` entries (as `distort` and `cab` have) and no grammar
change. `npm run gen:grammar` is still run once to prove there is no drift.

**D14 — Small fixes in code this phase rewrites anyway.**
- In global mode, named parameters after a positional value are dropped today
  (`(fx :reverb 0.4 :dry 1)` sets only the wet level; the per-track form applies both).
  The shared argument parser applies both in both modes.
- `filter.js` `bypass(true)` sets the cutoff to 20 kHz, which silences a high-pass
  instead of bypassing it. Bypass becomes "type `peaking`, gain 0", which is an exact
  identity for every type.
- A second `addModule` of the same worklet file (after `(load-plugin …)` of a built-in)
  must not fail: the new processor files wrap `registerProcessor` in `try/catch`.

**Changes against the outline:** `flanger`'s notch test uses `wet 1` (with the chorus
convention "dry stays 1, `wet` adds the delayed signal", `wet 0.5` gives notches only
9.5 dB deep); `delay :mode` defaults to `:stereo`, which is what the code does today (the
outline called today's delay mono, but a `DelayNode` keeps a stereo input stereo);
`auto-pan` is a balance (D11); the reverb has a seventh type `:classic` (D6).

### Measurements behind the decisions

| Question | Setup | Result | Decision |
|---|---|---|---|
| Native limiter? | `DynamicsCompressorNode`, threshold −3, knee 0, ratio 20, attack 0; 220 Hz sine at +6 dBFS | output peak −0.59 dBFS; a −26 dBFS signal comes out +1.71 dB | worklet (D2) |
| Native feedback flanger? | click → `DelayNode` 1 ms with feedback 0.5 | echoes at samples 44 and 216 (period 172 = 44 + 128) | worklet (D9) |
| `StereoPannerNode` for auto-pan? | stereo input, both channels 0.5, pan 0 / −0.5 / −1 | total power −3.01 / −0.69 / 0 dB | balance (D11) |
| `WaveShaperNode` with a silent input | curve `[0.5 0.7 0.9]`, no input | outputs 0.7 (the curve's midpoint) | LFO shapers are safe before the LFO starts |
| `DelayNode` at 0 s outside a loop | sine through `delayTime 0` | sample-exact | `width :haas 0` is transparent |
| Flat EQ | noise through lowshelf, 2 × peaking, highshelf, all gains 0 | difference below −200 dB | no dry/wet path needed for `eq` |
| Limiter algorithm (numeric prototype) | +6 dBFS sine + noise + ±2.0 clicks, ceiling −3 | peak −3.000 dBFS; −10 dBFS sine passes bit-identically | algorithm as specified |
| Reverb generators (numeric prototype) | see [Reference test cases](#reference-test-cases) | hall RT60 2.71 s for `decay 3`, 0.45 s for `decay 0.5`; damping, gate, reverse and size margins | generator formulas as specified |

## Background

- **Effect plugins:** `app/public/plugins/*.js`, protocol in
  [docs/PLUGINS.md](../docs/PLUGINS.md): `createNodes(ctx)` → `{inputNode, outputNode}`,
  `setParam`, `bypass`, `getParams`, `destroy`, optional `init(host)`, `clone`, `ready`,
  `resetParams`, `onEvent`. The existing built-ins are plain objects that keep their nodes
  on `this`.
- **Chain** (`app/src/repulse/fx.cljs`): `chain` atom of `{:name :plugin :input :output
  :bypassed? :active?}`; `rewire!` connects master gain → effects → `master-tap`;
  `add-effect!` (line 53) appends; `set-param!` (line 66) marks the entry active, appends
  to `param-log` and calls `setParam`; `reset-global-effects!` (line 73) calls
  `resetParams()` on every plugin before an evaluation. Per track: `add-track-effect!`
  (line 123) makes a fresh instance with `instantiate-plugin` (line 109: `clone()` if the
  plugin has one, else `Object.create` + `Object.assign`), `apply-track-effects!`
  (line 150) rebuilds a track's chain from the pattern's `:track-fx` metadata on every
  evaluation. A track chain is input → effects in the order written → master gain.
- **`fx` built-in** (`app/src/repulse/env/builtins/fx.cljs`, lines 58–102): arguments go
  through `leval/deep-unwrap`. If the last argument is a pattern it is the per-track form
  and conj's `{:name :params}` onto `:track-fx`; otherwise it calls `fx/bypass!`,
  `fx/remove-effect!` or `fx/set-param!`. A positional value is sent as the parameter
  name `"value"`. `make-builtins` gets a ctx map built in `env/builtins.cljs` (≈ line 74).
  A second copy of the argument handling is `sandbox-fx` in
  `app/src/repulse/snippets/sandbox.cljs` (line 79).
- **Eval warnings:** `builtins/record-eval-warning!` (`env/builtins.cljs` line 44) collects
  `{:message :from :to}`; `eval_orchestrator.cljs` (`report-slider-warnings!`, ≈ line 185)
  prints `<output> — Warning: <messages joined by "; ">` and marks the ranges.
  `leval/source-of` gives the `{:from :to}` of a raw argument.
- **Auto-load** (`app/src/repulse/app.cljs`): the compressor (CLJS,
  `app/src/repulse/plugins/compressor.cljs`) is registered synchronously (lines 434–437);
  the URL list follows (lines 439–452). `make-host` is at lines 117–124.
- **Offline export** (`app/src/repulse/offline_render.cljs`, EXP1, delivered): the worklet
  and every used effect are rebuilt in an `OfflineAudioContext`. `build-instance!`
  (line 118) does `instantiate-plugin` → `init(offline host)` → `createNodes`;
  `replay-global-params!` (line 134) copies `getParams()` and then replays `param-log`;
  per-track instances get the `:track-fx` params; `await-ready!` awaits `ready()` (5 s);
  a used plugin without a real `getParams()` forces the real-time fallback. Events start
  `pre-roll-secs` (0.25 s) after context time 0. The job map carries `:cycle-dur`.
- **UI tables:** `app/src/repulse/ui/slider_model.cljs`: `FX-SLIDER-PARAMS` (lines 31–65),
  `FX-SELECT-PARAMS` (68–70), `FX-PRIMARY-PARAM` (73–87); the inspector builds its
  controls from them and from `getParams()` (`ui/inspector_model.cljs` `fx-rows`).
  `app/src/repulse/lisp-lang/insert-categories.js` "Effects" group (lines 80–98).
  `app/src/repulse/content/builtin_meta.edn`: `:fx` at line 1249, the effect entries
  (`:distort` …) from line 1264. `completions.js` lines 252–257, `hover.js` ≈ line 1037.
- **Worklet precedent:** `app/public/plugins/bitcrusher.js` calls `addModule` in
  `init(host)`, keeps the promise, wires the `AudioWorkletNode` in `createNodes` once it
  resolves and exposes that as `ready()`. Its processor
  (`app/public/worklets/bitcrusher-processor.js`) takes parameters over the message port.
- **Engine and master:** `packages/audio/src/lib.rs` `process_block_raw` (line 810) sums
  the voices and clamps each channel to ±1 (lines 859–863). `build-master-chain!`
  (`audio.cljs` line 64): master gain → tap → analyser → destination; the global chain
  sits between master gain and tap. The WAV writer clamps to ±1.
- **Existing effects this phase changes:**
  - `filter.js`: one `BiquadFilterNode`; `freq`, `q`, `type` (`"lowpass" "highpass"
    "bandpass" "notch"`). The node also has `"lowshelf" "highshelf" "peaking" "allpass"`
    and a `gain` parameter; neither is exposed. A keyword type (`:type :highpass`) is
    ignored today, because the node gets a `Keyword` object.
  - `reverb.js`: `ConvolverNode` with `makeImpulseResponse(ctx, 2.5, 3.0)`; `wet` (0 at
    load), `dry` (0.8).
  - `delay.js`: one `DelayNode` (max 2 s) with a feedback `GainNode`; `time`, `feedback`
    (max 0.95), `wet`.
- **Model for modulated delays:** `chorus.js` (`OscillatorNode` → `GainNode` →
  `delayTime`). It starts its LFOs from a `statechange` listener (`startWhenReady`); an
  offline context only becomes "running" inside `startRendering()`, so new plugins start
  their oscillators directly with `osc.start(0)`.
- **Tests:** `e2e/export-parity.spec.ts` drives the real app and reads the exported WAV
  (helpers `exportBuffer`, `mono`, `seg`); `e2e/helpers/audio-analysis.ts` has `rms`,
  `goertzel`, `countOnsets`, `rmsPerWindow`, `toDb`, `parseWav16`. CLJS unit tests for the
  chain are in `app/src/repulse/fx_test.cljs`, for the slider tables in
  `app/src/repulse/ui/slider_model_test.cljs`.
- STRUDEL-PARITY.md: `ring*` is ⛔ as a per-event SuperDirt control. A track-level plugin
  doesn't change that.

## Part 0 — Shared groundwork

Build this first; parts A–C rely on it.

### 0.1 Parameter descriptor (`params`)

An effect plugin may carry a static `params` object. Keys are parameter names; values say
what the parameter takes:

```js
params: {
  value: "wet",                    // optional: the positional value is this parameter
  wet:   "number",
  type:  ["classic", "room", "hall", "plate", "spring", "gated", "reverse"],  // enum
  taps:  "list",                   // list of numbers
},
```

`repulse.fx` gets two pure functions (unit-tested in `fx_test.cljs`):

```clojure
(defn parse-fx-args
  "(fx :name 0.4 :dry 1) → [[\"value\" 0.4] [\"dry\" 1]] — ordered pairs, positional
   first when present. Used by both fx built-ins (app and snippet sandbox)."
  [args] …)

(defn prepare-param
  "→ {:name n :value v :warning msg-or-nil :send? bool}. plugin without `params`:
   name and value unchanged, :send? true (today's behaviour)."
  [^js plugin effect-name pname v] …)
```

Rules of `prepare-param` for a plugin with a descriptor:

| Case | Value sent | Warning (exact text) |
|---|---|---|
| name `"value"`, descriptor has `value` | renamed to the parameter it names, then that parameter's rule | — |
| name `"value"`, no `value` key | not sent | `fx :eq takes no positional value — parameters: low, low-freq, …` |
| name not in the descriptor | not sent | `fx :reverb has no parameter :room — parameters: wet, dry, type, decay, size, damp, predelay` |
| `"number"` | `leval/->num` (so `3/16` → `0.1875`) | if the result is not a number: `fx :delay :time expects a number — got :fast`; not sent |
| enum | `(name v)` for a keyword, the string itself otherwise | if not in the list: `fx :reverb :type has no value :cathedral — values: classic, room, …`; not sent |
| `"list"` | JS array; each element through `->num`; a vector is always the list | — |
| no parameters at all (`(fx :limiter)`) | nothing | `fx :limiter needs a parameter — parameters: ceiling, release` |

The parameter list in a message is the descriptor's keys in order, without `value`.
`:taps 1/8` is the vector `[1 8]` and is therefore read as two taps; USAGE says "always
write taps as a vector: `:taps [1/8]`".

The `fx` built-in (both copies) uses `parse-fx-args`, looks the plugin up in `@fx/chain`,
runs every pair through `prepare-param`, records each warning with the source range of
the parameter keyword (`leval/source-of` on the raw argument; the effect keyword for the
"no parameter" and "needs a parameter" cases) and sends or stores only the pairs with
`:send?`. If the effect is not in the chain yet (startup race, unknown name) the pairs
pass through unchanged, as today. Add `:warn! record-eval-warning!` to the ctx map in
`env/builtins.cljs` and take it in `b-fx/make-builtins`.

### 0.2 `setTempo(cycleDur, origin)` (D1)

```clojure
;; repulse.fx
(defn- all-plugins [] …)                    ; global chain + every track chain
(defn notify-tempo! [cycle-dur]
  (doseq [^js p (all-plugins)]
    (when (fn? (.-setTempo p)) (.setTempo p cycle-dur 0))))
(defonce ^:private tempo-watch
  (add-watch audio/scheduler-state ::fx-tempo
             (fn [_ _ o n] (when (not= (:cycle-dur o) (:cycle-dur n))
                             (notify-tempo! (:cycle-dur n))))))
```

`add-effect!` and `add-track-effect!` call `setTempo` (when present) right after
`createNodes`, with `(:cycle-dur @audio/scheduler-state)` and origin `0`.
`offline-render/build-instance!` takes the job's `:cycle-dur` and calls
`(.setTempo fresh cycle-dur pre-roll-secs)` after `createNodes`.

### 0.3 Chain order (D12)

```clojure
(def default-order
  ["gate" "eq" "compressor" "reverb" "delay" "filter" "dattorro-reverb"
   "chorus" "flanger" "vibrato" "phaser" "tremolo" "ring-mod"
   "overdrive" "bitcrusher" "sidechain" "distort" "amp-sim" "waveshape" "cab"
   ;; user-loaded plugins sort here
   "auto-pan" "width" "limiter"])

(defn chain-rank [effect-name] …)   ; index, or (index of "auto-pan") − 0.5 when unknown
```

`add-effect!` conj's and then `(swap! chain #(vec (sort-by (comp chain-rank :name) %)))`
before `rewire!`. The existing order is unchanged; the new effects are inserted where a
mix expects them (gate and EQ before the compressor, modulation next to chorus/phaser,
stereo tools and the limiter last).

### 0.4 Plugin conventions for the new effects

- Written as a factory so every instance is fresh:
  `function make() { return { type: "effect", name: "eq", …, clone() { return make(); } }; }
  export default make();`. A worklet plugin's `clone()` copies the `_ready` promise of the
  instance it was cloned from (same context); `init(host)` replaces it for an offline
  context.
- `params` descriptor, `getParams()` returning every parameter in a form `setParam`
  accepts (strings for enums, arrays for lists, `null` for an unset `sync`),
  `resetParams()` back to the defaults, `destroy()` stopping oscillators.
- Values are clamped to the range in the parameter table.
- **Activation.** `wet`/`mix` effects start at 0 as today. Effects without a mix control
  (`limiter`, `gate`, `vibrato`, `auto-pan`) are inactive after `createNodes()` and
  `resetParams()` (input → dry gain → output only) and become active with the first
  `setParam`. `eq`, `width` and the new filter types are identities at their defaults.
- `bypass(true)` gives a null against the input (≤ −60 dBFS); dry/wet switches ramp over
  10 ms.
- Worklet parameters are k-rate `AudioParam`s (`parameterDescriptors`), not port
  messages, so they are set synchronously and cannot race an offline render. Until the
  node exists the plugin keeps the values in fields and applies them when the worklet is
  wired (the `_pending` idea of `bitcrusher.js`).
- Oscillators start with `osc.start(0)` in `createNodes`.
- Smoothed parameter changes (`setTargetAtTime`, ramps) are for a running context. While
  `ctx.currentTime` is 0 (a context that has not started, i.e. every offline render) a
  plugin sets `.value` directly, so a render never begins inside a glide.

### 0.5 Test bench (`e2e/helpers/fx-bench.ts`, new)

Most reference tests need a known input (a sine at a level, one click, white noise)
through one plugin. The bench does that without the app:

```ts
export type BenchInput =
  | { kind: 'sine'; hz: number; db: number; hzRight?: number }
  | { kind: 'click' }                       // one sample of 1.0 at t = 0, both channels
  | { kind: 'noise'; db: number; seed?: number }   // uniform white noise, peak level
  | { kind: 'samples'; left: number[]; right: number[] };

export async function renderFx(page: Page, o: {
  plugin: string;                           // '/plugins/eq.js'
  params?: [string, unknown][];             // already in setParam form
  input: BenchInput; seconds: number; sampleRate?: number;   // default 44100
  tempo?: { cycleDur: number; origin: number };
  bypass?: boolean;
}): Promise<{ left: number[]; right: number[]; sampleRate: number }>;
```

It opens `/test-harness.html`, and inside `page.evaluate`: `import(o.plugin)`, a fresh
instance (`clone()`), an `OfflineAudioContext(2, …)`, `init({audioCtx, sampleRate, …})`,
`createNodes`, `setTempo`, every `setParam`, `bypass`, `await ready?.()`, an
`AudioBufferSourceNode` → `inputNode`, `outputNode` → destination, `startRendering()`.
This is the same sequence the offline export runs. Add to
`e2e/helpers/audio-analysis.ts`: `peakDb`, `bandEnergyDb(x, sr, f0, f1)` (FFT),
`schroederRt60(x, sr)` (backward integration, T30 from −5 to −35 dB, × 2), `nullDb(a, b)`
(peak of the difference), `instFreq(x, sr)` (zero crossings).

The new spec file is `e2e/fx-effects.spec.ts`. Tests that need the `fx` built-in, the
chain or the export use the helpers of `e2e/export-parity.spec.ts` (move `load`,
`exportBuffer`, `mono`, `seg` into `e2e/helpers/app.ts` and import them in both specs).

## Part A — Mixing

### `eq` (new: `app/public/plugins/eq.js`, native nodes)

| Parameter | Range | Default | Meaning |
|---|---|---|---|
| `low` | −24…24 dB | 0 | low-shelf gain |
| `low-freq` | 20–1000 Hz | 100 | shelf corner |
| `mid` | −24…24 dB | 0 | first peaking band |
| `mid-freq` | 100–8000 Hz | 1000 | |
| `mid-q` | 0.1–10 | 1 | |
| `mid2` | −24…24 dB | 0 | second peaking band |
| `mid2-freq` | 200–16000 Hz | 3000 | |
| `mid2-q` | 0.1–10 | 1 | |
| `high` | −24…24 dB | 0 | high-shelf gain |
| `high-freq` | 1000–20000 Hz | 8000 | shelf corner |

No positional value. Graph: `input (GainNode) → lowshelf → peaking → peaking → highshelf →
out (GainNode)`. Parameters use `setTargetAtTime(v, now, 0.01)`. `bypass(true)` saves the
four gains and sets them to 0. A shelf reaches half its gain at its corner frequency and
the full gain about two octaves beyond it (see the reference numbers).

### `limiter` (new: `app/public/plugins/limiter.js` + `app/public/worklets/limiter-processor.js`)

| Parameter | Range | Default | Meaning |
|---|---|---|---|
| `ceiling` (positional) | −24…0 dBFS | −1 | no output sample exceeds this level |
| `release` | 0.01–1 s | 0.1 | time to recover after a peak |

Graph: `input ─┬─ dry ─┬─ out` and `input → AudioWorkletNode("limiter") → wet ─┘`; inactive
`dry 1 / wet 0`, active `dry 0 / wet 1`. Node options: `channelCount 2`,
`channelCountMode "explicit"`, `outputChannelCount [2]`.

Processor, per frame, both channels linked (`L = round(0.005 · sampleRate)`):

```
p      = max(|l|, |r|)
req    = p > c ? c / p : 1                      c = 10^(ceiling/20)
m      = min of req over the last L + 1 frames
env    = min(m, a · env + (1 − a))              a = exp(−1 / (release · sampleRate))
g      = mean of env over the last L frames     (running sum over a ring buffer)
out    = in[n − L] · g                          (ring buffer of L frames per channel)
```

All buffers are allocated in the constructor; `process` allocates nothing. The window
minimum may be a plain scan (L + 1 ≈ 221 comparisons per frame) or a monotonic deque.
The limiter delays its signal by 5 ms. On the master that is inaudible; on a single track
it moves that track 5 ms late against the others (USAGE says so). The ceiling applies to
sample values, not to inter-sample peaks.

### `gate` (new: `app/public/plugins/gate.js` + `app/public/worklets/gate-processor.js`)

| Parameter | Range | Default | Meaning |
|---|---|---|---|
| `threshold` (positional) | −80…0 dBFS | −40 | the gate opens above this level |
| `attack` | 0.0001–0.1 s | 0.001 | opening time |
| `hold` | 0–2 s | 0.05 | stays open this long after the level drops |
| `release` | 0.005–2 s | 0.1 | closing time |
| `range` | −80…0 dB | −80 | gain while closed (−80 ≈ silent; −12 makes it an expander) |

Same dry/wet graph and node options as the limiter, no look-ahead, no latency. Processor
per frame: `p = max(|l|, |r|)`; detector `d = max(p, d · k)` with `k = exp(−1/(0.010 ·
sampleRate))` (instant rise, 10 ms fall); the gate opens when `d` exceeds the threshold and
starts closing when `d` has been below `threshold − 3 dB` for `hold` seconds; the gain
moves towards 1 (open) or `10^(range/20)` (closed) with a one-pole whose time constant is
`attack` or `release`.

### `filter` (change: `app/public/plugins/filter.js`)

| Parameter | Range | Default | Meaning |
|---|---|---|---|
| `freq` (positional) | 20–20000 Hz | 20000 | cutoff / centre |
| `q` | 0.001–30 | 1 | resonance (unchanged) |
| `type` | `lowpass` `highpass` `bandpass` `notch` `lowshelf` `highshelf` `peaking` `allpass` | `lowpass` | keyword or string |
| `gain` (new) | −24…24 dB | 0 | for `lowshelf`, `highshelf`, `peaking` |

Add the `params` descriptor (so `:type :highpass` works and `3/2`-style values convert),
`gain` in `setParam`/`getParams`/`resetParams`, and the bypass fix of D14: `bypass(true)`
saves `type` and `gain` and sets `type "peaking"`, `gain 0`; `bypass(false)` restores
them. The single-node graph stays.

## Part B — Space

### `reverb` (change: `app/public/plugins/reverb.js`; new: `app/public/plugins/lib/reverb-ir.js`)

| Parameter | Range | Default | Meaning |
|---|---|---|---|
| `wet` (positional) | 0–1 | 0 | reverb level |
| `dry` | 0–1 | 0.8 | dry level (unchanged) |
| `type` | `classic` `room` `hall` `plate` `spring` `gated` `reverse` | `classic` | generator |
| `decay` | 0.1–10 s | per type | reverberation time (see the type table) |
| `size` | 0–1 | per type | room size: spacing of the first reflections and build-up time |
| `damp` | 0–1 | per type | how fast the highs die away (0 = not at all) |
| `predelay` | 0–0.25 s | 0 | gap before the reverb starts |

| Type | `decay` means | default `decay` | default `size` | default `damp` | trim (start value) |
|---|---|---|---|---|---|
| `classic` | length of the response | 2.5 | (unused) | 0 | 1.0 |
| `room` | RT60 | 0.6 | 0.3 | 0.5 | 1.9 |
| `hall` | RT60 | 3.0 | 0.8 | 0.35 | 0.8 |
| `plate` | RT60 | 1.8 | (unused) | 0.2 | 1.1 |
| `spring` | RT60 | 2.0 | 0.5 (echo spacing) | 0 | 1.0 |
| `gated` | gate time | 0.3 | (unused) | 0.2 | 2.8 |
| `reverse` | swell length | 1.0 | (unused) | 0.2 | 1.6 |

`decay`, `size` and `damp` that the user has not set follow the type. The trims start at
the values that give every type the same click-response energy at its default decay; the
listen check adjusts them so the types sound equally loud on a snare, and the final
values go into the PR.

Graph:

```
input ─┬─ dry ───────────────────────────────────────────────┬─ out
       └─ pre (DelayNode, max 0.25 s) ─┬─ convA ─ xA ─┬─ trim ─ wet ─┘
                                       └─ convB ─ xB ─┘
```

One convolver carries the sound; the other is idle with `buffer = null`. Update flow
(D5): `setParam` of `type`/`decay`/`size`/`damp` stores the value and (re)starts a 30 ms
timer → `_apply()`: `key = irKey(…)`; equal to the loaded key → return; else
`makeIR` (through the cache) → `AudioBuffer` → idle convolver → `xA`/`xB` linear crossfade
over 50 ms, `trim.gain` to the type's trim → 80 ms later the old convolver's buffer is
set to `null`. `ready()` runs a pending `_apply()` at once and resolves. `createNodes`
loads the classic response synchronously, as today. `predelay` →
`pre.delayTime.setTargetAtTime(v, now, 0.01)`.

`reverb-ir.js` is pure (no `AudioContext`), so it can be tested by importing it:

```js
export const TYPES   = ["classic", "room", "hall", "plate", "spring", "gated", "reverse"];
export const PRESETS = { classic: { decay: 2.5, size: 0.5, damp: 0, trim: 1.0 }, … };
export function irKey(type, p, sampleRate)   // decay, size, damp rounded to 0.01
export function makeIR(type, p, sampleRate)  // → [Float32Array L, Float32Array R], cached (12 entries, LRU)
```

Generators (`sr` = sample rate, `t = i / sr`, `noise` = mulberry32-seeded uniform values
in −1…1; the seed is a constant per type and channel, so left and right differ and every
render is identical):

```
damped(x, damp):  two one-pole low-passes in series, cutoff gliding over the buffer:
                  fc(i) = min(0.45·sr, 18000 · 2^(−7 · damp · i/N));  a = 1 − exp(−2π·fc/sr)
                  damp = 0 → return x unchanged

classic:  N = decay·sr;          x = noise · (1 − i/N)³;              damped
room, hall, plate:
          N = 1.2·decay·sr;      e = exp(−6.908 · t / decay)
          rise  = (0.002 + 0.078·size) s  (plate: 0)   → e · (t / rise) while t < rise
          last 10 ms: linear fade to 0
          x = damped(noise · e)
          room, hall add 8 early reflections after damping:
            position (0.010 + 0.070·size) s × {0.19 0.31 0.43 0.52 0.66 0.74 0.87 1.0}
            (right channel × 1.07), level L · 0.8^k with alternating sign,
            L = 0.6 (room), 0.3 (hall)
gated:    N = (decay + 0.005)·sr; x = noise · (1 − 0.5 · t/decay) for t < decay,
          then a 5 ms linear fade to 0; damped
reverse:  N = (decay + 0.005)·sr; x = noise · 10^(−2 · (1 − t/decay)) for t < decay,
          then a 5 ms linear fade to 0; damped
spring:   N = 1.2·decay·sr; echoes k = 0, 1, … while k·Td < 1.2·decay,
          Td = (0.030 + 0.030·size) s (right channel × 1.07);
          echo k is an upward chirp of length D = (k + 1) · 0.012 s,
          f(τ) = 100 · 40^(τ/D) Hz  (phase = 2π · 100 · D/ln 40 · (40^(τ/D) − 1)),
          Hann window, amplitude 10^(−3 · k·Td / decay) / √D; damped
```

For hall-like types heavy damping shortens the measured broadband RT60 (2.71 s at the
default `damp 0.35` for `decay 3`, 2.3 s at `damp 1`); USAGE describes `decay` as the
reverberation time of the low end.

### `delay` (change: `app/public/plugins/delay.js`)

| Parameter | Range | Default | Meaning |
|---|---|---|---|
| `wet` | 0–1 | 0 | echo level |
| `time` (positional, see Q3) | 0.001–4 s | 0.375 | delay time in seconds |
| `sync` | > 0 cycles, e.g. `3/16` | unset | delay time in cycles; wins over `time` while set |
| `feedback` | 0–0.95 | 0.35 | repeat amount |
| `mode` | `stereo` `mono` `ping-pong` | `stereo` | see below |
| `taps` | list of up to 4 positions in cycles | unset | multi-tap; wins over `time` and `sync` |
| `lowcut` | 20–2000 Hz | 20 (off) | high-pass in the feedback path |
| `highcut` | 500–20000 Hz | 20000 (off) | low-pass in the feedback path |

`setTempo` stores `cycleDur`; tap times are `sync × cycleDur` or `taps[k] × cycleDur`,
clamped to 4 s (one `console.warn` per instance when clamped). The delay nodes are created
with `createDelay(4)`. Time changes use `linearRampToValueAtTime(t, now + 0.05)`.

- `stereo` (today's graph): `send` keeps both channels, each side echoes on its own side.
- `mono`: `send` has `channelCount 1`, `channelCountMode "explicit"`; echoes are centred.
- `ping-pong`: mono `send`; echoes alternate left, right, left …

Graph for `stereo` and `mono` (one tap unless `taps` is set):

```
input ─┬─ dry ─────────────────────────────────────────────┬─ out
       └─ send ─┬─ tap[0] (DelayNode) ─┬───────────── wet ─┘
                ├─ tap[1] … tap[n−1] ──┘
                └──────── fb (GainNode) ←─ [lowcut → highcut] ←─ tap[n−1]
```

All taps have level 1; the longest tap feeds back into `send`, so the whole tap pattern
repeats. With `taps` unset there is one tap at `time`/`sync`, which is exactly today's
graph. Graph for `ping-pong` (taps unset):

```
send → A (DelayNode T) → panner(−1) → wet
       A → g (GainNode = feedback) → B (DelayNode T) → panner(+1) → wet
       B → [lowcut → highcut] → fb (GainNode = feedback) → A
```

Echo *k* arrives at `k·T` with level `feedback^(k−1)`, odd echoes left, even echoes right.
With `taps` set, `ping-pong` pans the taps alternately instead (tap 0 left, tap 1 right …).
The two `BiquadFilterNode`s (`highpass`, `lowpass`, `Q.value = 0`, i.e. Butterworth) are
only inserted while `lowcut > 20` or `highcut < 20000`, so the default repeats are
unchanged. Changing `mode`, `taps` or inserting/removing the filters rebuilds the wet
side (`_rebuild()`); that may click and is documented.

## Part C — Modulation & stereo

### `flanger` (new: `app/public/plugins/flanger.js` + `app/public/worklets/flanger-processor.js`)

| Parameter | Range | Default | Meaning |
|---|---|---|---|
| `wet` (positional) | 0–1 | 0 | level of the delayed signal (dry stays 1, as in `chorus`) |
| `rate` | 0.01–10 Hz | 0.25 | sweep speed |
| `depth` | 0–1 | 0.7 | sweep range: the delay moves between `delay·(1 − depth)` and `delay·(1 + depth)` |
| `delay` | 0.0005–0.010 s | 0.002 | centre delay |
| `feedback` | −0.95…0.95 | 0 | resonance; negative values invert the comb |

Graph: `input ─┬─ dry(1) ─┬─ out`, `input → AudioWorkletNode("flanger") → wet ─┘`. The
processor is stereo, wet only: per channel a ring buffer of `ceil(0.025·sampleRate)`
frames; `d = delay · (1 + depth · sin(phase))` clamped to 2 frames … 20 ms; the output is
the linearly interpolated sample `d` seconds back; the value written is
`in + feedback · out`; `phase += 2π · rate / sampleRate`, the same for both channels.

### `vibrato` (new: `app/public/plugins/vibrato.js`, native nodes)

| Parameter | Range | Default | Meaning |
|---|---|---|---|
| `depth` (positional) | 0–100 cents | 20 | pitch swing up and down |
| `rate` | 0.5–20 Hz | 5 | speed |

Graph: `input ─┬─ dry ─┬─ out`, `input → DelayNode(0.05) → wet ─┘`, `OscillatorNode(sine,
rate) → GainNode(A) → delayTime`. The swing of a modulated delay is `2π · rate · A`, so
`A = min(0.02, (2^(depth/1200) − 1) / (2π · rate))` seconds and the base delay is
`A + 0.001`. Fully wet when active (`dry 0 / wet 1`), inactive `dry 1 / wet 0`. Changes
use `setTargetAtTime(…, 0.02)`.

### `auto-pan` (new: `app/public/plugins/auto-pan.js`, native nodes)

| Parameter | Range | Default | Meaning |
|---|---|---|---|
| `depth` (positional) | 0–1 | 1 | how far it moves (1 = fully left to fully right) |
| `rate` | 0.01–20 Hz | 1 | sweeps per second |
| `sync` | > 0 cycles | unset | length of one sweep in cycles; wins over `rate` while set |
| `shape` | `sine` `tri` `square` | `sine` | LFO shape |

```
input (channelCount 2, explicit) ─┬─ dry ─────────────────────────┬─ out
                                  └─ splitter ─┬─ gL ─┬─ merger ─ wet ─┘
                                               └─ gR ─┘
lfo (OscillatorNode) → smooth (lowpass 100 Hz) → depth (GainNode) ─┬─ shapeL → gL.gain
                                                                   └─ shapeR → gR.gain
```

`gL.gain.value` and `gR.gain.value` are 0, so the shapers set the gains. Curves (1025
points over −1…1): `shapeL(x) = √2 · cos((x + 1) · π/4)`, `shapeR(x) = √2 · sin((x + 1) ·
π/4)`: both 1 at the centre, √2 and 0 at the ends, constant total power. `tri` and
`square` map to the oscillator types `triangle` and `square`. With `sync`, the rate is
`1 / (sync · cycleDur)` and the LFO is phase-locked: the plugin replaces the oscillator
with one that starts at `origin + ceil((now + 0.01 − origin) / P) · P` (`P = sync ·
cycleDur`) and stops the old one at that time, so a sweep starts at the centre moving
right on a sweep boundary. The same happens on `setTempo`. Inactive `dry 1 / wet 0`.

### `ring-mod` (new: `app/public/plugins/ring-mod.js`, native nodes)

| Parameter | Range | Default | Meaning |
|---|---|---|---|
| `mix` (positional) | 0–1 | 0 | crossfade: dry `1 − mix`, modulated `mix` |
| `freq` | 0.1–5000 Hz | 440 | carrier frequency |
| `shape` | `sine` `tri` `square` `saw` | `sine` | carrier shape |

Graph: `input ─┬─ dry ─┬─ out`, `input → ring (GainNode, gain.value 0) → wet ─┘`,
`OscillatorNode(freq) → ring.gain`. The output of `ring` is input × carrier.

### `width` (new: `app/public/plugins/width.js`, native nodes)

| Parameter | Range | Default | Meaning |
|---|---|---|---|
| `width` (positional) | 0–2 | 1 | side level: 0 = mono, 1 = unchanged, 2 = side doubled |
| `haas` | 0–30 ms | 0 | delay on the right channel; widens mono material |

```
input (channelCount 2, explicit) → splitter
  L → a → mergeL        R → a → mergeR         a = (1 + width)/2   (same-side gain)
  L → b → mergeR        R → b → mergeL         b = (1 − width)/2   (cross gain)
mergeR → DelayNode(haas, max 0.03) → merger input 1;   mergeL → merger input 0 → out
```

`width 1`, `haas 0` is the identity, so there is no dry path; `bypass(true)` saves both
values and sets them to 1 and 0. Values above 1 raise the level of out-of-phase content
and reduce mono compatibility; USAGE says so and points to D10 for per-track use.

## Registration, UI and docs

### App tables

- `app/src/repulse/app.cljs`: add `/plugins/gate.js`, `eq.js`, `flanger.js`, `vibrato.js`,
  `ring-mod.js`, `auto-pan.js`, `width.js`, `limiter.js` to the URL list (the order in the
  list no longer matters, 0.3).
- `slider_model.cljs` `FX-SLIDER-PARAMS` (add / extend):

| Effect | Sliders `{min max step}` |
|---|---|
| `eq` | `low` `mid` `mid2` `high` −24…24 / 0.5 · `low-freq` 20…1000 / 1 · `mid-freq` 100…8000 / 1 · `mid2-freq` 200…16000 / 1 · `high-freq` 1000…20000 / 1 · `mid-q` `mid2-q` 0.1…10 / 0.1 |
| `limiter` | `ceiling` −24…0 / 0.1 · `release` 0.01…1 / 0.01 |
| `gate` | `threshold` −80…0 / 0.5 · `attack` 0…0.1 / 0.001 · `hold` 0…2 / 0.01 · `release` 0.01…2 / 0.01 · `range` −80…0 / 1 |
| `filter` | existing + `gain` −24…24 / 0.5 |
| `reverb` | existing `wet` + `decay` 0.1…10 / 0.1 · `size` `damp` 0…1 / 0.01 · `predelay` 0…0.25 / 0.005 |
| `delay` | existing (`time` max → 4) + `sync` 0…2 / 0.0625 · `lowcut` 20…2000 / 1 · `highcut` 500…20000 / 1 |
| `flanger` | `wet` `depth` 0…1 / 0.01 · `rate` 0.01…10 / 0.01 · `delay` 0.0005…0.01 / 0.0005 · `feedback` −0.95…0.95 / 0.01 |
| `vibrato` | `depth` 0…100 / 1 · `rate` 0.5…20 / 0.1 |
| `auto-pan` | `depth` 0…1 / 0.01 · `rate` 0.01…20 / 0.01 · `sync` 0…4 / 0.125 |
| `ring-mod` | `mix` 0…1 / 0.01 · `freq` 0.1…5000 / 0.1 |
| `width` | `width` 0…2 / 0.01 · `haas` 0…30 / 0.5 |

- `FX-SELECT-PARAMS`: `"filter" {"type" [the 8 types]}`, `"reverb" {"type" [the 7 types]}`,
  `"delay" {"mode" ["stereo" "mono" "ping-pong"]}`, `"auto-pan" {"shape" ["sine" "tri"
  "square"]}`, `"ring-mod" {"shape" ["sine" "tri" "square" "saw"]}`.
- `FX-PRIMARY-PARAM`: `"limiter" "ceiling"`, `"gate" "threshold"`, `"flanger" "wet"`,
  `"vibrato" "depth"`, `"auto-pan" "depth"`, `"ring-mod" "mix"`, `"width" "width"` (no
  entry for `eq`; `delay` per Q3).
- `insert-categories.js` "Effects": `(fx :eq :low ¦3 :high 2)`, `(fx :limiter ¦-1)`,
  `(fx :gate ¦-40)`, `(fx :flanger ¦0.6)`, `(fx :vibrato ¦20)`, `(fx :auto-pan ¦1)`,
  `(fx :ring-mod ¦0.5 :freq 220)`, `(fx :width ¦1.4)`, each with a one-line detail; fix
  the `delay` entry per Q3.
- `completions.js` (type `"keyword"`), `hover.js` (signature, description with ranges and
  defaults, example) and `builtin_meta.edn` (`:category "effect"`, `:returns "nil"`,
  `:side-effects ["audio"]`, ≥ 1 example, `:see-also`) for `eq`, `limiter`, `gate`,
  `flanger`, `vibrato`, `auto-pan`, `ring-mod`, `width`; extend the `:fx` examples with
  `(fx :reverb :type :hall :decay 3 :wet 0.3)` and `(master (fx :limiter -1))`. Run
  `npm run gen:ai-docs` and `npm run gen:grammar` (D13: no grammar change, no drift).
- `hover.js` `wordAt` (≈ line 1254) only accepts `BuiltinName` and `Symbol` nodes, so the
  hover texts of effects (also the existing `distort`, `amp-sim`, `waveshape`, `cab`)
  never show on `:distort`. Extend it: a `Keyword` node that is the first argument of an
  `(fx …)` list resolves to its name without the colon.

### Docs

- `docs/USAGE.md` "Effect plugins": the effect count (15 → 23); a note under `fx` that
  unknown parameters and values are reported as warnings; "Built-in effects": a section
  per new effect (parameter table + example), rewritten `reverb` (type table, `decay`
  meaning per type), `delay` (`sync`, `mode`, `taps`, cuts, the tempo-change note) and
  `filter` tables; a short **Mixing** example with `eq` on two tracks and `eq` + `limiter`
  in `(master …)`; the limiter's latency and the per-track `width` note; "Effect chain
  order" with the order of 0.3. Correct the stale defaults found on the way (the reverb
  table says wet `0.3`, the code starts at 0; `delay` per Q3).
- `docs/PLUGINS.md`: `params` descriptor and `setTempo(cycleDur, origin)` in the effect
  protocol table and a short section each; "start oscillators with `start(0)`".
- `README.md` line 54 (effects bullet): add one mixing example.
- `docs/FUTURE-FEATURES.md`: ring-modulator and parametric-EQ rows marked done.
  `docs/SYNTHESIS-COVERAGE.md`: the FX1 rows → ✅ (spring only if it passes D7).
- `ROADMAP.md`: delivered entry.

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/fx.cljs` | `parse-fx-args`, `prepare-param`, `default-order` / `chain-rank` + sort in `add-effect!`, `notify-tempo!` + watch, `setTempo` after `createNodes` |
| `app/src/repulse/fx_test.cljs` | Tests for the above (fake plugins) |
| `app/src/repulse/env/builtins/fx.cljs` | Use `parse-fx-args` / `prepare-param`, record warnings, positional + named in global mode |
| `app/src/repulse/env/builtins.cljs` | `:warn!` in the ctx map |
| `app/src/repulse/snippets/sandbox.cljs` | `sandbox-fx` uses the shared parser |
| `app/src/repulse/offline_render.cljs` | `build-instance!` takes `cycle-dur`, calls `setTempo` |
| `app/src/repulse/app.cljs` | 8 new plugin URLs |
| `app/public/plugins/eq.js`, `limiter.js`, `gate.js`, `flanger.js`, `vibrato.js`, `auto-pan.js`, `ring-mod.js`, `width.js` (new) | The eight effects |
| `app/public/worklets/limiter-processor.js`, `gate-processor.js`, `flanger-processor.js` (new) | The three processors |
| `app/public/plugins/lib/reverb-ir.js` (new) | Impulse-response generators, presets, cache |
| `app/public/plugins/reverb.js`, `delay.js`, `filter.js` | New parameters, descriptors, `setTempo`, `ready()`, bypass fix |
| `app/src/repulse/ui/slider_model.cljs` + `slider_model_test.cljs` | Slider, select and primary tables |
| `app/src/repulse/lisp-lang/insert-categories.js`, `completions.js`, `hover.js` | Entries; `wordAt` resolves the effect keyword of an `(fx …)` form |
| `app/src/repulse/content/builtin_meta.edn` + `docs/ai/builtins.json` | Entries; `npm run gen:ai-docs` |
| `e2e/helpers/fx-bench.ts`, `e2e/helpers/app.ts` (new), `e2e/helpers/audio-analysis.ts` | Bench, shared app helpers, analysis functions |
| `e2e/fx-effects.spec.ts` (new), `e2e/export-parity.spec.ts` | Reference tests; import the moved helpers |
| `packages/audio/src/lib.rs` | Only if Q2 = yes: safety bound + test; `npm run build:wasm` |
| `docs/USAGE.md`, `docs/PLUGINS.md`, `README.md`, `docs/FUTURE-FEATURES.md`, `docs/SYNTHESIS-COVERAGE.md`, `ROADMAP.md` | Docs, delivered entry |

## Build order

Run CLAUDE.md Rule 4 first (`npm run gen:grammar && npm run gen:ai-docs && npm run test`
on the unmodified branch).

**Part 0 — groundwork**
1. `fx.cljs`: `default-order`, `chain-rank`, sort in `add-effect!`; unit test (effects
   added in reverse order end up in the documented order; an unknown name lands between
   `cab` and `auto-pan`).
2. `parse-fx-args`, `prepare-param`, both `fx` built-ins, `:warn!`; unit tests for every
   row of the table in 0.1 and for positional + named in global mode.
3. `notify-tempo!`, the watch, `setTempo` calls in `add-effect!`, `add-track-effect!` and
   `build-instance!`; unit test with a fake plugin (called once on add, again when
   `:cycle-dur` changes, not when another key changes).
4. `e2e/helpers/fx-bench.ts`, the analysis helpers, `e2e/helpers/app.ts`; prove the bench
   on an existing effect (`filter.js` low-pass at 500 Hz: a 5 kHz sine drops ≥ 20 dB).
5. **Reference capture, before touching `reverb.js`:** render a click through the current
   reverb (`wet 0.4`, 3 s) five times with the bench and write the mean RMS of 0.05–2.5 s
   (mono mix, in dB) into `e2e/fx-effects.spec.ts` as `REVERB_CLASSIC_RMS_DB`.

**Part A — mixing**
1. `eq.js` + tests A1–A4.
2. `filter.js`: descriptor, `gain`, bypass fix + tests A5–A6.
3. `limiter-processor.js`, `limiter.js` + tests A7–A9.
4. `gate-processor.js`, `gate.js` + tests A10–A12.
5. App tables, URL list, completions/hover/meta for the three new effects; `gen:ai-docs`.
6. App-level tests X1–X4 for `eq`, `limiter`, `gate` (export, per track, bypass, inactive).
7. Only if Q2 = yes: `lib.rs` safety bound, a Rust test (four voices at amp 1 in phase:
   the block peak is above 1.0 and at most 8.0; a single voice at amp 0.5 renders
   bit-identically to before), `npm run build:wasm`, and an export test (a synth mix that
   peaks above 0 dBFS with `(master (fx :limiter -1))` has no sample above −1 dBFS and no
   flat-topped run of more than 3 equal samples at the ceiling).

**Part B — space**
1. `lib/reverb-ir.js` + pure tests B1–B2 (imported in the page).
2. `reverb.js`: graph, descriptor, debounce/cache/crossfade, `ready()`, trims + tests B3–B9.
3. `delay.js`: descriptor, `setTempo`, `sync`, modes, taps, cuts + tests B10–B15.
4. App tables for both; warning test B16.

**Part C — modulation & stereo**
1. `flanger-processor.js`, `flanger.js` + tests C1–C3.
2. `vibrato.js` + C4. 3. `auto-pan.js` + C5–C6. 4. `ring-mod.js` + C7. 5. `width.js` + C8–C10.
6. App tables, URL list, completions/hover/meta; `gen:ai-docs`; tests X1–X4 for the five.

**Finish:** docs, `npm run test:all`, `npx shadow-cljs compile app`, the listen check,
the PR with the test protocol.

## Reference test cases

Bench tests (`renderFx`, 44.1 kHz) unless marked *app*. "Level" is the RMS of the last
half of the render compared with the same render of the dry input, in dB. The expected
values come from the filter formulas and the prototypes; tolerances are the pass limits.

**Part A**

| # | Effect and parameters | Input | Pass |
|---|---|---|---|
| A1 | `eq` `low 6`, `low-freq 100` | sine 50 Hz / 5 kHz, −12 dBFS, 1 s | 50 Hz: +5.6 dB (5…7); 5 kHz: 0.0 dB (±0.5) |
| A2 | `eq` `mid -6`, `mid-freq 1000`, `mid-q 2` | sine 1 kHz / 250 Hz / 4 kHz | −6.0 dB (±1); −0.1 dB (±1); −0.1 dB (±1) |
| A3 | `eq` `high 6`, `high-freq 8000` | sine 16 kHz / 1 kHz | +5.9 dB (5…7); 0.0 dB (±0.5) |
| A4 | `eq` defaults | noise −12 dBFS, 1 s | `nullDb` ≤ −60 dBFS |
| A5 | `filter` `type "peaking"`, `freq 1000`, `gain 6` | sine 1 kHz / 100 Hz | +6.0 dB (±1); +0.1 dB (±0.5) |
| A6 | `filter` `type "highpass"`, `freq 2000`, then `bypass(true)` | sine 100 Hz | level 0 dB (±0.5); without bypass ≤ −40 dB |
| A7 | `limiter` `ceiling -3` | 220 Hz sine, 0.05 for 0.2 s, then 2.0 (+6 dBFS) for 1 s | no sample above −2.5 dBFS (0.750); peak of the last 0.5 s within −3.5…−3.0 dBFS |
| A8 | `limiter` `ceiling -3` | 440 Hz sine, −10 dBFS | level ±0.5 dB; the output equals the input delayed by `round(0.005·sr)` ± 1 samples |
| A9 | `limiter` `ceiling -3`, `release 0.5` vs `0.02` | +6 dBFS burst 0.2 s, then −20 dBFS sine | 100 ms after the burst the `0.02` render is ≥ 3 dB louder than the `0.5` render |
| A10 | `gate` `threshold -40` | noise at −50 dBFS, 1 s | level ≤ −30 dB |
| A11 | `gate` `threshold -40` | 440 Hz sine, −20 dBFS | level 0 dB (±1) |
| A12 | `gate` `threshold -40`, `range -12` | noise at −50 dBFS | level −12 dB (±1) |

**Part B**

| # | Effect and parameters | Input | Pass |
|---|---|---|---|
| B1 | `makeIR("classic", defaults)` (pure) | — | length `2.5·sr`; every `|x[i]| ≤ (1 − i/N)³`; two calls return identical arrays; L ≠ R |
| B2 | `makeIR` for all 7 types (pure) | — | finite values, 2 channels, lengths: `1.2·decay·sr` (room, hall, plate, spring), `(decay + 0.005)·sr` (gated, reverse) |
| B3 | `reverb` `type "hall"`, `decay 3`, `wet 1`, `dry 0` | click, 4.2 s | `schroederRt60` 2.4…3.6 s (prototype 2.71) |
| B4 | same, `decay 0.5` | click, 1 s | 0.4…0.6 s (prototype 0.45) |
| B5 | `reverb` `predelay 0.05`, `wet 1`, `dry 0` (classic) | click | first sample above 10⁻⁴ × peak at 50 ± 5 ms |
| B6 | `reverb` `type "gated"`, `decay 0.3`, `wet 1`, `dry 0` | click | RMS of 0.40–0.45 s is ≥ 40 dB below RMS of 0.25–0.30 s |
| B7 | `reverb` `type "hall"`, `decay 3`, `damp 0.1` vs `0.9` | click | `bandEnergyDb` 4–16 kHz over 1.0–2.0 s differs by ≥ 6 dB (prototype 9.7 dB) |
| B8 | `reverb` `type "hall"`, `size 0` vs `1` | click | the time by which half of the first 200 ms of energy has arrived is ≥ 15 ms later for `size 1` (prototype 79 → 113 ms); `type "reverse"`, `decay 1`: energy of 0.75–1.0 s is ≥ 15 dB above 0–0.25 s (prototype 27.8 dB) |
| B9 | `reverb` `wet 0.4` only | click, 3 s | RMS of 0.05–2.5 s within ±1 dB of `REVERB_CLASSIC_RMS_DB`; `ready()` resolves; with `type "hall"` set and reset to `"classic"` before `ready()` the result is the same |
| B10 | `delay` `sync 0.1875`, `wet 1`, `feedback 0.5`; tempo `cycleDur 2` | click | echoes 375 ± 2 ms apart, each quieter |
| B11 | same, tempo `cycleDur 2.6667` (90 BPM) | click | 500 ± 2 ms apart |
| B12 | `delay` `mode "ping-pong"`, `time 0.2`, `feedback 0.6`, `wet 1` | click | echoes at 0.2, 0.4, 0.6, 0.8 s; odd ones L/R ≥ +20 dB, even ones ≤ −20 dB |
| B13 | `delay` `taps [0.125, 0.375]`, `feedback 0`, `wet 1`; tempo `cycleDur 2` | click | `countOnsets` = 3 (click, 0.25 s, 0.75 s ± 2 ms) |
| B14 | `delay` `time 0.2`, `feedback 0.7`, `highcut 1000` vs default | noise burst 20 ms | energy above 4 kHz of echo 2 relative to echo 1 is ≥ 15 dB lower with `highcut` |
| B15 | `delay` `mode "mono"` vs `"stereo"`, `time 0.2`, `wet 1` | click on the left channel only | `mono`: echo equal on both sides (±1 dB); `stereo`: echo L/R ≥ +20 dB |
| B16 | *app:* `(fx :reverb :room 0.8)` | — | output ends with `Warning: fx :reverb has no parameter :room — parameters: wet, dry, type, decay, size, damp, predelay` |

**Part C**

| # | Effect and parameters | Input | Pass |
|---|---|---|---|
| C1 | `flanger` `depth 0`, `delay 0.002`, `wet 1`, `feedback 0` | noise −12 dBFS, 2 s | `bandEnergyDb` ±20 Hz around 250, 750 and 1250 Hz each ≥ 10 dB below the bands around 500 and 1000 Hz |
| C2 | `flanger` `feedback 0.95`, `depth 0.7`, `wet 1` | click, 3 s | finite; RMS of the last 0.5 s ≤ −60 dBFS |
| C3 | `flanger` `depth 0.7`, `rate 0.25`, `wet 1` | noise, 4 s | the 250 Hz band energy in 100 ms windows varies by ≥ 6 dB over the render |
| C4 | `vibrato` `rate 5`, `depth 50` | 440 Hz sine, 2 s | `instFreq` swings to +50 and −50 cents (each ±15 %: 42.5…57.5); one swing takes 200 ms ± 10 % |
| C5 | `auto-pan` `rate 1`, `depth 1` | 440 Hz sine on both channels, 3 s | 50 ms windows: at 0.25 + k s R/L ≥ 20 dB, at 0.75 + k s L/R ≥ 20 dB; total power per window within ±1.5 dB of its mean |
| C6 | `auto-pan` `sync 1`; tempo `cycleDur 2`, `origin 0.25` | same, 4.5 s | hard right at 0.75 s and 2.75 s, hard left at 1.75 s (±50 ms); centred before 0.25 s |
| C7 | `ring-mod` `freq 100`, `mix 1` | 440 Hz sine | `goertzel` 340 and 540 Hz within 1 dB of each other; 440 Hz ≥ 20 dB below them |
| C8 | `width` `0` | L 440 Hz, R 660 Hz sines | `nullDb(left, right)` ≤ −60 dBFS |
| C9 | `width` `1` (and defaults) | same | `nullDb(out, in)` ≤ −60 dBFS per channel |
| C10 | `width` `2`; `haas 15` | same; click on both channels | `L − R` is 6 dB (±0.5) above the input's, `L + R` within ±0.5 dB; the right click is 15 ms ± 1 sample late |

**All new effects (bench and app)**

| # | Test | Pass |
|---|---|---|
| X1 | each of the 8 new plugins, after `createNodes` only, and again with non-default parameters + `bypass(true)` | `nullDb(out, in)` ≤ −60 dBFS |
| X2 | *app:* `(track :t (seq :a4))` + `(master (fx :<name> …))` + `(export 1)` for each new effect and for `reverb :type :hall`, `delay :sync 1/8` | output line `exported all tracks — 1 cycles (offline)` |
| X3 | *app:* two tracks, one with `(fx :flanger 0.8)` (and the same for the other new effects) | stem export (or two exports) shows the other track unchanged: `nullDb` ≤ −60 dBFS |
| X4 | *app:* `(bpm 90)` + `(master (fx :delay :sync 3/16 :wet 0.5))` + one hat + `(export 1)` | echo lag 500 ± 2 ms (the method of "master delay echoes at the delay time") |

## Definition of done

**Part 0**
- [ ] The chain order equals `default-order` whatever order the plugins load in; a user plugin sits between `cab` and `auto-pan` (unit test).
- [ ] `prepare-param` passes the table in 0.1; plugins without `params` get the same calls as before (existing `fx_test.cljs` tests unchanged).
- [ ] `(fx :reverb 0.4 :dry 1)` at top level sets both parameters.
- [ ] `setTempo` reaches global, per-track and offline instances (unit test + X4).

**Part A**
- [ ] A1–A4: `(fx :eq :low 6 :low-freq 100)`, `(fx :eq :mid -6 :mid-freq 1000 :mid-q 2)`, `(fx :eq :high 6 :high-freq 8000)` and the flat default meet their limits.
- [ ] A5–A6: `(fx :filter :type "peaking" :freq 1000 :gain 6)` gives +6 ± 1 dB at 1 kHz; `(fx :filter :type :highpass :freq 2000)` works with a keyword; bypassing a high-pass no longer silences the signal.
- [ ] A7–A9: `(fx :limiter :ceiling -3)` on a signal peaking at +6 dBFS has no output sample above −2.5 dBFS; a −10 dBFS signal passes within ±0.5 dB, 5 ms late.
- [ ] A10–A12: `(fx :gate :threshold -40)` attenuates −50 dBFS noise by ≥ 30 dB and passes a −20 dBFS tone within ±1 dB.

**Part B**
- [ ] B1–B9: reverb types, `decay`, `size`, `damp`, `predelay` meet their limits; `(fx :reverb 0.4)` stays within ±1 dB of the reference captured before the change.
- [ ] B16: `(fx :reverb :room 0.8)` shows the exact warning naming `size`.
- [ ] B10–B15 and X4: `(fx :delay :sync 3/16 …)` gives 375 ms echoes at 120 BPM and 500 ms at 90 BPM; ping-pong alternates sides; `:taps [1/8 3/8]` gives exactly two echoes.
- [ ] A reverb parameter set to the value it already has does not swap the impulse response (unit or bench test: the loaded key is unchanged and no crossfade is scheduled).

**Part C**
- [ ] C1–C3 flanger, C4 vibrato, C5–C6 auto-pan, C7 ring-mod, C8–C10 width meet their limits.

**All parts**
- [ ] X1: each new effect is transparent after auto-load and when bypassed (≤ −60 dBFS).
- [ ] X2: `(export …)` with each effect active renders offline (no fall-back to real-time capture).
- [ ] X3: each effect works per track: `(track :a (->> (seq :a4) (fx :flanger 0.8)))` leaves other tracks dry.
- [ ] The three worklet processors allocate nothing in `process` (code review item) and their files tolerate being loaded twice.
- [ ] Q1–Q3 are implemented as answered, or with their defaults, and the PR states which.
- [ ] Inspector: every new effect shows its sliders and selects; `completions.js`, `hover.js`, `builtin_meta.edn` entries for the 8 names; `gen:grammar` and `gen:ai-docs` leave no drift.
- [ ] USAGE.md, PLUGINS.md, README.md, FUTURE-FEATURES.md and SYNTHESIS-COVERAGE.md updated as listed under Docs; ROADMAP delivered entry.
- [ ] Human listen check recorded in the PR (below); spring kept or removed per D7; final reverb trims noted.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5).

## Listen check

Done by a human on the PR branch, headphones on, results written into the PR:

1. **Limiter:** a dense mix (kick, bass, pad, hats, all at `amp 1`) with and without
   `(master (fx :limiter -1))`. With it, no crackle on the kick and no audible pumping at
   `release 0.1`. If the engine clamp is kept (Q2 default), give every synth track a
   track effect for this check (append `(fx :filter 20000)`), because the engine clips
   synth voices on plain tracks before the limiter sees them.
2. **Reverb types** on `(track :snare (->> (seq :_ :sd :_ :sd) (fx :reverb :type … :wet 0.5)))`,
   one after the other: all seven are equally loud at the same `wet` (adjust the trims if
   not); room is short and close, hall long and distant, plate bright and dense, gated
   stops abruptly, reverse swells. **Spring** must flutter ("boing") without a ringing
   pitch; otherwise remove it (D7).
3. **EQ:** `:low 9` on a bass and `:high -9` on hats are clearly audible and free of
   clicks while the slider moves.
4. **Gate** on a snare with a long reverb in front of it: the tail is cut; `hold` and
   `release` change how.
5. **Ping-pong delay** on a lead: echoes clearly left, right, left; `(bpm 90)` while it
   plays bends the repeats briefly and then they sit on the new grid.
6. **Flanger** sweep with `feedback 0.6`: the jet sound, no zipper noise; **vibrato**,
   **auto-pan** (smooth, no level jump), **ring-mod** (bell-like at `freq 220`),
   **width** `0 → 2` on a panned track.

## Test protocol for the PR

The implementer ends the PR description with this list (CLAUDE.md Rule 5), after checking
every snippet and replacing an expectation where the built effect behaves differently.
The messages are the exact texts from 0.1.

````markdown
## Test protocol

Setup: `npm run dev:full`, open http://localhost:3000, headphones on. Evaluate each
snippet with Alt+Enter (macOS Option+Enter); stop playback between tests.

### New features
- [ ] **1. EQ on a track.**
  ```lisp
  (bpm 120)
  (track :bass (->> (seq :c2 :_ :eb2 :_) (synth :saw) (fx :eq :low 9 :low-freq 120)))
  ```
  Expected: two bass notes per cycle, clearly fuller than with `:low 0`; the inspector shows the EQ sliders.
- [ ] **2. EQ cut.** Same, with `(fx :eq :mid -12 :mid-freq 800 :mid-q 2)`.
  Expected: the bass sounds hollow; no click when you drag the `mid` slider.
- [ ] **3. Limiter on the master.**
  ```lisp
  (track :kick (->> (seq :bd :bd :bd :bd) (amp 1)))
  (track :bass (->> (seq :c2 :c2 :c2 :c2) (synth :saw) (amp 1)))
  (track :pad  (->> (seq :c3) (synth :saw) (amp 1)))
  (master (fx :limiter -1))
  ```
  Expected: loud but no crackle on the kicks. Remove the `master` line: the kicks crackle.
  (If the engine clamp was kept, Q2: append `(fx :filter 20000)` to the `:bass` and `:pad`
  chains first; plain synth tracks are clipped by the engine before the limiter.)
- [ ] **4. Gate.** `(track :snare (->> (seq :_ :sd :_ :sd) (fx :reverb 0.6) (fx :gate :threshold -30 :release 0.05)))`
  Expected: two snares per cycle, the reverb tail is cut off shortly after each hit.
- [ ] **5. Filter types.** `(track :hats (->> (fast 8 (seq :hh)) (fx :filter :type :peaking :freq 3000 :gain 12 :q 2)))`
  Expected: 8 hats per cycle with a nasal emphasis; with `:gain 0` they sound unprocessed.
- [ ] **6. Reverb types.** `(track :snare (->> (seq :_ :sd :_ :sd) (fx :reverb :type :hall :decay 3 :wet 0.5)))`, then `:room`, `:plate`, `:spring`, `:gated :decay 0.3`, `:reverse :decay 0.8`.
  Expected: hall rings for about 3 s; room is short; gated stops abruptly after 0.3 s; reverse swells for 0.8 s after each hit and stops.
- [ ] **7. Predelay and damping.** `… (fx :reverb :type :hall :predelay 0.12 :damp 0.9 :wet 0.5)`
  Expected: the reverb starts audibly after the hit and is dull; with `:damp 0` it is bright.
- [ ] **8. Synced ping-pong delay.**
  ```lisp
  (bpm 120)
  (track :lead (->> (seq :c4 :_ :_ :_) (synth :saw)))
  (master (fx :delay :sync 3/16 :mode :ping-pong :feedback 0.5 :wet 0.5))
  ```
  Expected: one note per cycle, echoes every 3 sixteenths (375 ms), alternating left, right, left. After `(bpm 90)` the echoes are 500 ms apart.
- [ ] **9. Multi-tap.** Same track, `(master (fx :delay :taps [1/8 3/8] :feedback 0 :wet 0.6))`
  Expected: exactly two echoes per note, an eighth and three eighths of a cycle after it (250 ms and 750 ms at 120 BPM).
- [ ] **10. Flanger.** `(track :pad (->> (seq :c3 :g3) (synth :saw) (fx :flanger :rate 0.25 :depth 0.7 :feedback 0.6 :wet 0.8)))`
  Expected: a slow jet-like sweep, one sweep every 4 s.
- [ ] **11. Vibrato.** `(track :lead (->> (seq :a4) (synth :saw) (fx :vibrato :rate 5 :depth 50)))`
  Expected: the note wobbles in pitch five times per second.
- [ ] **12. Auto-pan.** `(track :hats (->> (fast 8 (seq :hh)) (fx :auto-pan :sync 1)))`
  Expected: 8 hats per cycle moving centre → right → centre → left once per cycle, smoothly, at even loudness.
- [ ] **13. Ring modulator.** `(track :lead (->> (seq :a4 :e4) (synth :saw) (fx :ring-mod :freq 220 :mix 1)))`
  Expected: metallic, bell-like tones; with `:mix 0` the plain saw.
- [ ] **14. Width.**
  ```lisp
  (track :l (->> (seq :hh :_ :hh :_) (pan -0.5)))
  (track :r (->> (seq :_ :hh :_ :hh) (pan 0.5)))
  (master (fx :width 0))
  ```
  then `1`, then `2`.
  Expected: 0 = all four hats in the centre; 1 = alternating half left / half right; 2 = almost fully left / right.
- [ ] **15. Export.** Add `(export 1)` to snippet 8.
  Expected: output `exported all tracks — 1 cycles (offline)`; the WAV has the same echoes.

### Warnings and errors
- [ ] **16. Unknown parameter.** `(fx :reverb :room 0.8)`
  Expected: output ends with `Warning: fx :reverb has no parameter :room — parameters: wet, dry, type, decay, size, damp, predelay`; `:room` is underlined.
- [ ] **17. Unknown value.** `(fx :reverb :type :cathedral :wet 0.4)`
  Expected: `Warning: fx :reverb :type has no value :cathedral — values: classic, room, hall, plate, spring, gated, reverse`; the reverb plays with the classic type.
- [ ] **18. No positional value.** `(fx :eq 3)`
  Expected: `Warning: fx :eq takes no positional value — parameters: low, low-freq, mid, mid-freq, mid-q, mid2, mid2-freq, mid2-q, high, high-freq`.

### Regressions
- [ ] **19. Classic reverb.** `(track :snare (->> (seq :_ :sd :_ :sd) (fx :reverb 0.4)))`
  Expected: the same reverb as on `dev` (about 2.5 s tail).
- [ ] **20. Delay in seconds.** `(track :hat (seq :hh :_ :_ :_))` + `(master (fx :delay :wet 0.5 :time 0.25 :feedback 0.4))`
  Expected: echoes 250 ms apart on both sides, as before.
- [ ] **21. Filter.** `(track :hats (->> (fast 8 (seq :hh)) (fx :filter 800)))`
  Expected: 8 dull hats per cycle, as before.
- [ ] **22. Filter bypass.**
  ```lisp
  (track :hats (fast 8 (seq :hh)))
  (fx :filter :freq 6000 :type "highpass")
  ```
  then add the line `(fx :off :filter)` below and evaluate again.
  Expected: thin hats first; with the `:off` line the full hats (before this phase a bypassed high-pass was close to silent).
- [ ] **23. Chain order.** `(master (fx :limiter -1) (fx :reverb 0.3))`
  Expected: the inspector lists reverb before limiter, whatever the order in the code.

### Editor
- [ ] **24.** Typing `limi` offers `limiter`; hovering `:limiter` in `(fx :limiter -1)` shows its signature and ranges.

### Known issues (not blockers)
- A limiter on a single track delays that track by 5 ms.
- Changing the delay `mode` or `taps` while it plays can click.
````

## What NOT to do

- No per-event effects or sends (`room`, `delay`, `orbit`, `phaser`, `vib` as pattern
  parameters): **TL7a–c**.
- No character or special effects (ladder filter, comb, wavefolder, tape, vinyl, rotary
  speaker, transient shaper, multiband compressor, pitch shifter, freeze, vocoder):
  deferred **FX2**.
- No audio-keyed sidechain between tracks, no reorderable or parallel chains: **P2**.
- No de-esser, no loudness (LUFS) metering, no true-peak limiting, no graphical EQ curve
  editor.
- No user impulse-response loading (parity row `ir` stays ❓); `cab` keeps its own IRs.
- Don't change `compressor`, `sidechain` or `dattorro-reverb`, and don't add `params`
  descriptors to plugins this phase does not otherwise touch.
- No warning for unknown effect names (`(fx :flangr 0.5)` stays silent): effects load
  asynchronously at startup, so the check would misfire. Follow-up.
- Don't fix the LFO start of `chorus`, `phaser` and `tremolo` in offline exports here
  (suspected: `startWhenReady` waits for a `statechange` that an offline context only
  fires once rendering has begun). Follow-up: verify with an export test first.
- No external DSP libraries; worklets only for `limiter`, `gate` and `flanger`, where the
  measurements above show that native nodes cannot do it.
