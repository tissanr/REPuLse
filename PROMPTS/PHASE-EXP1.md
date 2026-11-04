# Phase EXP1 — Export Parity: Offline Render with WASM Voices & FX

> **Spec status:** full (expanded 2026-11-04 from the outline; design decisions 1–3 by the
> project owner, 2026-10-28; decisions E1–E17 taken during expansion)
> **Automation:** 🤖 auto — parity is measurable: render the same pattern offline and through a real-time capture, then compare spectra/RMS in Playwright.

**Milestone:** M4 — Sketchpad
**Depends on:** SONG1 (song length, real-time capture as the reference). TL7 recommended (per-event effects).
**Unblocks:** SK1 (stems rendered through the offline path).

## Goal

Make the offline (faster-than-real-time) render sound exactly like playback. Today the
`OfflineAudioContext` path renders every WASM voice as a JS sine fallback and drops all
FX, so `(export 8)` sounds nothing like what you hear. EXP1 loads the WASM worklet and
rebuilds the per-track and global FX chains inside the offline context. SONG1's
`(export :song)` then switches from real-time capture to a fast offline render, and SK1
gets stems that sound right.

```lisp
;; Before: offline export — :epiano renders as a sine, the master reverb is missing
(track :keys (->> (seq :c4 :e4 :g4) (synth :epiano)))
(master (fx :reverb 0.4))
(export 8)                 ; sine tones, dry

;; After: same sound as playback, faster than real time
(export 8)                 ; FM e-piano, with reverb
(export :song)             ; full song, offline, same WAV as the real-time capture
(export :song :realtime)   ; SONG1's real-time capture, still available
```

"Exactly like playback" is literal: the offline graph mirrors the real-time graph,
including its routing rules (see decision E1 for the one surprising consequence).

## Scope

- Offline graph builder: master gain → global FX chain (cloned plugins, current params)
  → destination; per-track gain → track FX chain → master gain.
- WASM worklet in the offline context, initialised before rendering; voices routed per
  track exactly as in the real-time graph.
- Plugin worklet modules (`bitcrusher`, `dattorro-reverb`) loaded into the offline context.
- `export.cljs` passes track names; `(export n)`, `(export :track)` and `(export :song)`
  all use the new offline path.
- Fallback: if the worklet can't initialise offline, export falls back to real-time capture
  (SONG1) and says so in the output panel, never silently to sines.
- Tweens re-armed at export start (bar 0) in both export paths (decision 3).

## Design decisions

All questions below were decided by the project owner on 2026-10-28.

1. **`:realtime` stays as an explicit option.** Default `(export :song)` uses the new
   offline path; `(export :song :realtime)` keeps SONG1's real-time capture. That path
   exists anyway, is the reference for the parity test and is the fallback when the
   offline worklet can't start.
   **Decided:** offline by default, `:realtime` as opt-in.

2. **Plugins are instantiated offline exactly like `fx/add-track-effect!`.** Use
   `clone()` when the plugin has one (the CLJS `compressor` does), otherwise
   `Object.create`/`Object.assign`, as today. Then call `createNodes(offline-ctx)` and copy
   the current state with `getParams` → `setParam`. Every audio plugin in
   `app/public/plugins/` already has `getParams`; plugins without it are visualisers
   (`oscilloscope`, `spectrum`, `p5-*`), which are skipped offline because they don't
   change the sound. If an audio plugin still can't be rebuilt (e.g. a third-party plugin
   without `getParams`), export falls back to real-time capture and prints which plugin
   caused it.
   **Decided:** reuse `add-track-effect!` instantiation + param replay; skip visualisers;
   real-time fallback otherwise.

3. **Tweens are re-armed at export start in both export paths.** Today `:tween-state`
   stores `:start-time` as the audio time of the evaluation (`arm-transitions!` in
   `app/src/repulse/audio.cljs`), so a tween runs from "when you pressed eval", not from
   bar 0. For any export (offline and `:realtime`), every tween's start is set to the
   export's bar 0. This makes exports reproducible and lets offline and real-time output
   agree. EXP1 also changes SONG1's real-time capture accordingly. SONG2 automation is
   already defined in song time and needs no special handling.
   **Decided:** re-arm tweens at export start (bar 0), offline and real time.

## Decisions taken during spec expansion

Questions the outline left open, decided here in line with its intent ("sounds exactly
like playback", "don't change the real-time audio graph").

- **E1 — WASM voices on a track with a track FX chain mirror the real-time graph, i.e.
  they use the JS fallback.** In the real-time graph there is exactly one
  `repulse-processor` node, hard-wired to `master-gain` (`init-worklet!`), and
  `worklet-trigger!` / `worklet-trigger-v2!` only fire when `dest` *is* `@master-gain`.
  `output-for-track` returns the first FX input for a track with an FX chain, so in
  playback today `(->> pat (synth :epiano) (fx :reverb 0.4))` already plays a JS sine
  through the reverb. Rendering the FM voice there offline would make export differ from
  playback and break the parity test, and fixing playback would change the real-time
  graph (forbidden here). EXP1 therefore mirrors the rule exactly; the Goal example uses
  `(master (fx :reverb …))`, where WASM voices do pass through FX. Routing WASM voices
  through per-track FX in real time (one worklet node per FX track) is a follow-up phase,
  and the offline builder is written so that it follows automatically: it reuses the same
  "dest = master-gain?" decision and not a copy of it.
- **E2 — Offline graphs are registered per context.** `play-event` looks up a graph
  registered for its `OfflineAudioContext` (a `WeakMap`). An `OfflineAudioContext`
  *without* a registered graph keeps today's JS-only path unchanged, which is exactly what
  the TEST1 harness (`repulse.test-api/render-offline`, backend `"offline-js"`) uses. So
  the harness backend needs no change and is not touched.
- **E3 — The offline sample rate is the live `AudioContext` rate**, not a fixed 44 100 Hz.
  The real-time capture records at the device rate (`arm-capture!` uses
  `(.-sampleRate ac)`), so this is the only way the lengths can match exactly. All exports
  compute frames with one helper, `(Math/round (* secs sr))` (same formula as
  `arm-capture!`). The old `(int …)` truncation goes away.
- **E4 — 0.25 s silent pre-roll, trimmed from the result.** Several plugins glide to new
  parameter values (`filter.js` uses `setTargetAtTime` with τ = 10 ms; `cab.js` ramps over
  20 ms) and therefore start from their `createNodes` defaults in a fresh context. Events are
  scheduled at `t + pre-roll`, tweens start at `pre-roll`, and the rendered buffer is
  sliced from frame `round(0.25 × sr)`. In the real-time graph these glides settled long
  ago; with the pre-roll they have also settled offline.
- **E5 — Every export starts after the buffer has evaluated** (where `(export :song)`
  already starts today). `pending-export` in `song_transport.cljs` grows a `:kind`. That
  way global `(fx …)` / `(master …)` forms placed *below* `(export 4)` are included, and
  `(export 4)` from the command bar (which goes through `activate-buffer!` too) uses the
  active tracks.
- **E6 — Parameter replay = `getParams` → `setParam` (decision 2), then a parameter log.**
  `bitcrusher.js` and `dattorro-reverb.js` `getParams()` return only `{wet}`; their
  worklet parameters (`bits`, `rate`, `decay`, …) would be lost. Global chain entries
  therefore get a `:params` log (written by `fx/set-param!`, cleared by
  `fx/reset-global-effects!`), which is replayed after `getParams`. Track FX are rebuilt
  from the pattern's `:track-fx` metadata exactly like `fx/apply-track-effects!` does in
  real time (clone → `createNodes` → `setParam` per metadata param), so no log is needed
  for them.
- **E7 — Plugin worklet modules: the clone's `init` is called with an offline host
  before `createNodes`.** `bitcrusher.js` / `dattorro-reverb.js` start
  `audioWorklet.addModule("/worklets/…-processor.js")` in `init(host)` and keep the
  promise in `this._ready`. A clone made by `Object.assign` inherits the *real-time*
  promise, and `new AudioWorkletNode(offlineCtx, "bitcrusher")` would throw inside its
  `.then`. Calling `fresh.init(offlineHost)` re-targets `_ready` at the offline context.
  This is generic (third-party plugins that follow the same pattern work too) and changes
  no DSP. A new **optional** plugin method `ready()` (Promise, resolves when the nodes from
  the last `createNodes` are fully wired, rejects on failure) is added to `bitcrusher.js`,
  `dattorro-reverb.js` and `cab.js` (async IR generation) and awaited before rendering
  (5 s timeout). It is a protocol addition documented in `docs/PLUGINS.md`, not a DSP
  change.
- **E8 — "Can't be rebuilt offline"** means any of: `createNodes` throws or fails
  `validate-effect-nodes!`; `ready()` rejects or times out; or the plugin is *used*
  (global entry `:active?`, or named in some exported track's `:track-fx`) and its
  `getParams` was installed by `plugins/normalize-plugin!` (marked with a flag, see step 4).
  An unused third-party plugin without `getParams` sits at its defaults in real time too
  and is rebuilt at defaults. Visual plugins never reach `fx/chain` (`add-effect!` is only
  called for `type "effect"`), and an `(fx :oscilloscope)` inside `->>` is ignored by
  `add-track-effect!` (no chain entry). The offline builder applies the same rules plus an
  explicit `type == "visual"` skip as a guard.
- **E9 — Fallback for `(export n)` / `(export :track)`.** SONG1's capture needs a song, so
  the fallback builds a synthetic one-section song: `(song/make-song [(song/make-section
  :export n track-kws)] {:end :stop} track-kws nil)`. It captures it with tail 0 through
  a generalised `replay-song-buffer!` that takes the buffer explicitly (the user's
  `song-buffer` is not overwritten). Like `(export :song)` today, this restarts playback
  and stops it at the end. Events match the offline render: song bar b queries cycle b,
  and `(export n)` queries cycles 0…n−1.
- **E10 — Control/audio buses force the real-time fallback.** Bus nodes (`repulse.bus`)
  live in the real-time context; connecting them to offline nodes throws. If
  `(seq (bus/active-buses))`, the offline path is skipped with the reason
  `buses (bus …) are not rendered offline yet`.
- **E11 — Worklet protocol.** The offline worklet is created by a new
  `init-offline-worklet!`. The real-time `init-worklet!` is not refactored. Events are sent
  with the existing `"trigger"` / `"trigger_v2"` messages, then a new `{type: "sync", id}`
  message, answered with `{type: "synced", id}`. `startRendering` is only called after
  `"synced"`, so every event is queued in `AudioEngine.pending` before the first quantum.
  Messages on one port are delivered in order, so the
  rendering thread never races the queue. The offline path fetches `repulse_audio_bg.wasm`
  itself on every export (no shared in-memory copy; the transfer detaches the buffer
  anyway), with a 5 s `"ready"` timeout.
- **E12 — Exports ignore mute**, as `(export n)` does today and as the SONG1 capture does
  (its `replay-song-buffer!` → `audio/stop!` resets `:muted` to `#{}`).
- **E13 — Argument grammar.** `(export :song)`, `(export :song :tail s)`,
  `(export :song :realtime)`, `(export :song :tail s :realtime)` and
  `(export :song :realtime :tail s)` are valid. `:realtime` with anything else →
  `Error: :realtime only applies to (export :song)`. A track named `:realtime` can't be
  exported by name (like `:song`, already reserved).
- **E14 — One export at a time; offline renders are not cancellable.** A new
  `export-running?` atom guards both paths (`Error: an export is already running — (stop)
  cancels it` stays the message). `(stop)` still cancels a real-time capture. An offline
  render can't be aborted (`OfflineAudioContext` has no abort) and finishes in a fraction
  of the song length.
- **E15 — Output messages** (all via the `set-output!` already passed to
  `export/make-builtins`):
  - start (return value of the call): `rendering all tracks — 4 cycles offline…`,
    `rendering :kick — 4 cycles offline…`, `rendering song — 24 bars, 50.0 s offline…`
  - success: `exported all tracks — 4 cycles (offline)` / `exported song — 24 bars, 50.0 s
    (offline)` with status `:success`
  - fallback: `offline render unavailable (<reason>) — capturing in real time instead…`,
    status `:idle`, followed by the SONG1 capture messages
  - `(export :song :realtime)` keeps SONG1's messages unchanged.
- **E16 — E2E tests drive the production app, not the TEST1 harness.** The Playwright
  `webServer` already compiles both builds (`npx shadow-cljs compile test-harness app`).
  The new spec loads `/`, evaluates via `__REPULSE_APP__` and reads the WAV from the
  Playwright `download` event. That exercises the real `(export …)` built-in, the real
  plugins and the real worklet, and leaves the harness alone.
- **E17 — Not-yet-delivered neighbours.** SONG2 (automation) and TL7 (per-event effects)
  are not delivered at the time of writing. If either lands first, its values must reach
  the offline renderer through the same helpers the scheduler uses (`resolve-tweens` /
  `play-event` graph lookup), never through a copy of the scheduler code.

## Background

### Audio engine — `app/src/repulse/audio.cljs`

- **Master chain** (`build-master-chain!`): `master-gain → [global FX…] → master-tap →
  [recorder] → analyser → destination`. `master-gain` / `master-tap` / `analyser-node`
  are `defonce` atoms. `master-gain` may be changed at runtime (e.g. MIDI CC in
  `env/builtins/midi.cljs` sets `(.-gain @audio/master-gain)`).
- **WASM worklet** (`init-worklet!`): `audioWorklet.addModule(public-url "worklet.js")`,
  `new AudioWorkletNode(ac, "repulse-processor", {outputChannelCount: [2]})`, connected
  to `@master-gain`, stored in `worklet-node`. Then it fetches `repulse_audio_bg.wasm` on
  the main thread, posts `{type: "init", wasmBytes}` (transferred) and sets
  `worklet-ready?` on `{type: "ready"}`. `public-url` resolves paths against the
  `/js/` or `/test-js/` script location.
- **Recorder** (`init-recorder!`): `worklets/recorder-processor.js`, node
  `"repulse-recorder"` between `master-tap` and the analyser (SONG1).
- **Per-track routing**: `track-nodes` atom `kw → {:gain-node :fx-chain [...]}`.
  `ensure-track-node!` creates the gain (always 1.0). `output-for-track` returns the first
  FX input of the track's chain, else `@master-gain`. For **any** `OfflineAudioContext` it
  returns `(.-destination ac)`, which is why offline exports have no FX. The track
  `gain-node` gets no sources: sources connect to the first FX input directly
  (`fx/rewire-track!` connects `gain → fx… → master`).
- **`play-event [ac t value track-name]`**: `offline? (instance? js/OfflineAudioContext
  ac)`; every WASM dispatch is guarded with `(when-not offline? …)` or
  `(if offline? <js> …)`. JS fallbacks: `make-sine`, `make-saw`, `make-square`,
  `make-noise`, `js-synth` (kick/snare/hihat), `samples/play!`,
  `synth/play-synth!` (defsynth). FM presets (`:sax … :flute`) and KS presets
  (`:western … :mandolin`) become `make-sine` offline.
- **`worklet-trigger!` / `worklet-trigger-v2!`**: post `{type: "trigger", value, time}` /
  `{type: "trigger_v2", value, time, amp, attack, decay, pan}` to `@worklet-node`; return
  `true` only when `@worklet-ready?`, `@worklet-node` and `(= dest @master-gain)`.
- **Tweens**: `arm-transitions!` (called by `play-track!` and `start!`) queries cycle 0 of
  the pattern for `{:type :tween}` param values and stores
  `{param {:tween pv :start-time (.-currentTime ac)}}` under `[:tween-state track]`.
  `schedule-event!` interpolates each tweened param with `interp-tween [ts t cycle-dur]`
  before calling `play-event`. Note that the `"transition"` worklet message is not used by
  the CLJS side; tweens are applied per event.
- **Song mode / capture (SONG1)**: `schedule-cycle!` → `song/song-onsets`;
  `resolve-song-origin!` → `arm-capture!` computes `t0 = (origin + start-bar) × cycle-dur`,
  `frames = round((song-duration + tail) × sr)`, posts `{type: "start", startFrame,
  endFrame, chunkFrames}` to the recorder; `capture!` returns the promise;
  `cancel-capture!` is called from `stop!`. `stop!` also resets `:tween-state`, `:muted`,
  `:song`, `:capture` and destroys track FX.

### WASM engine — `app/public/worklet.js`, `packages/audio/src/lib.rs`

- `worklet.js` (`RepulseProcessor`) handles `init` (→ `ready` / `error`), `trigger`,
  `trigger_v2`, `stop`, `transition`; `process()` calls
  `engine.process_block(n, currentTime)` and de-interleaves into stereo.
- `AudioEngine::trigger_v2` pushes a `Pending` with an absolute time;
  `process_block_raw` activates every pending event whose time is before the block end
  (late events at sample 0, future ones deferred). So the whole export can be queued
  before rendering starts: no voice-pool limit, and `currentTime` starts at 0 in an
  `OfflineAudioContext`.
- Relevant presets: `fm_preset("epiano") = (index 1.5, ratio 14.0, attack 0.005, decay
  0.80, sustain 0.0, release 0.01)`; KS `buf_len = floor(sr / freq)`; `"hh"` = noise
  through an 8 kHz high-pass with `decay_rate(0.045)` (to −100 dB in 45 ms); note
  keywords without `:synth` → `Voice::Tone` (sine). Noise voices draw from the engine's
  LCG seed, which advances per event (deterministic offline, history-dependent in real
  time).

### FX — `app/src/repulse/fx.cljs`, `app/src/repulse/plugins.cljs`, `app/public/plugins/*.js`

- `fx/chain`: vector of `{:name :plugin :input :output :bypassed? :active?}`, one per
  registered effect plugin, in load order (`app.cljs` loads `compressor` first, then
  `reverb delay filter dattorro-reverb chorus phaser tremolo overdrive bitcrusher
  sidechain distort amp-sim waveshape cab`). `rewire!` connects
  `master-gain → e1 → … → master-tap`. Inactive effects stay in the chain at their
  defaults: e.g. `reverb` keeps `dry 0.8`, and `compressor` is always on (`wet 1`).
- `set-param!` marks the entry `:active?` and calls `.setParam`; `bypass!` calls
  `.bypass`; `reset-global-effects!` (called at the start of every `evaluate*` in
  `eval_orchestrator.cljs`) calls `resetParams` and clears `:active?` / `:bypassed?`.
- `add-track-effect! [track-name effect-name]`: looks up the *global* entry by name (no
  entry → silently nothing), makes `fresh` with `(.clone p)` if present, else
  `Object.create(proto)` + `Object.assign`, then `createNodes(ac)` +
  `validate-effect-nodes!`, appends to `[:fx-chain]`, `rewire-track!`.
  `apply-track-effects! [track-name track-fx]` clears and rebuilds from the
  `:track-fx` metadata `[{:name "reverb" :params {"value" 0.4}}]` produced by the `fx`
  built-in in `env/builtins/fx.cljs` (per-track mode).
- `notify-fx-event! [value t]` calls `onEvent(name, t)` on global and track plugins
  (`sidechain.js` ducks on it). Scheduler hook: `:on-fx-event`.
- `plugins/normalize-plugin!` installs a default `getParams` (`#js {}`) when a plugin
  lacks one. Visual plugins are registered but never added to `fx/chain`.
- Plugins with their own worklet: `bitcrusher.js` (`/worklets/bitcrusher-processor.js`,
  params `wet` via gain, `bits`/`rate` via port; `getParams → {wet}`) and
  `dattorro-reverb.js` (`/worklets/dattorro-reverb-processor.js`, `decay`, `damping`,
  `bandwidth`, `predelay` via port; `getParams → {wet}`). Both create the
  `AudioWorkletNode` inside `this._ready.then(…)` in `createNodes`. Params that arrive
  earlier are buffered in `this._pending` and flushed on wiring. `cab.js` generates IRs
  asynchronously (`generateIR` renders its own small `OfflineAudioContext`).
  `amp-sim.js`, `cab.js`, `distort.js`, `waveshape.js` and the CLJS compressor
  (`app/src/repulse/plugins/compressor.cljs`, `:clone make`) have `clone()`.
- `reverb.js` builds its IR with `Math.random()` per instance, so two instances never
  produce sample-identical tails.

### Export — `app/src/repulse/env/builtins/export.cljs`, `song_transport.cljs`, `wav.cljs`

- `(export n)` / `(export :track)`: during evaluation, merges
  `(:tracks @audio/scheduler-state)` with `@song-transport/pending-tracks`, creates
  `(js/OfflineAudioContext. 2 n-frames 44100)` with `n-frames (int (* sr duration))`,
  queries `core/query-onsets` per cycle and calls `(audio/play-event offline abs-t
  (:value ev))` **without the track name** and without tweens. It then calls
  `startRendering` → `wav/encode-wav-16` → `wav/download-wav!` (filename
  `repulse-<all|track>-<n>cycles.wav`). Completion prints nothing.
- `(export :song [:tail s])`: validates, then sets `song-transport/pending-export
  {:tail :set-output!}`. After evaluation `activate-buffer!` calls `start-export!` →
  `capture-song!` (`recorder-ready`, `replay-song-buffer! 0 {:end :stop}`,
  `audio/capture!`), then encodes `repulse-song-<bars>bars.wav` and prints
  `exported song — 24 bars, 50.0 s`.
- `replay-song-buffer! [start-bar opts]` reads `@song-buffer`, calls `audio/stop!`,
  `audio/set-song!` and `play-tracks!` (`audio/play-track!` + `fx/apply-track-effects!`).
- `repulse.song` (`packages/core/src/repulse/song.cljs`): `make-section`, `make-song`,
  `song-onsets [song tracks muted bar]`, `song-duration-secs [song bpm]`, `song-length`.

### Tests

- `playwright.config.ts`: `testDir ./e2e`, 60 s default timeout, Chromium with
  `--autoplay-policy=no-user-gesture-required`, web server
  `npx shadow-cljs compile test-harness app && node scripts/serve-static.mjs app/public
  3100`.
- TEST1 harness: `app/public/test-harness.html` loads `/test-js/main.js` (`:test-harness`
  build, `repulse.test-api/init!`). `window.__REPULSE_TEST__` has `evalToEvents`,
  `renderOffline` (plain `OfflineAudioContext`, `:backend "offline-js"`), `reset`,
  `backend → "offline-js"`, `songEvents`, `audioReady`, `captureSong` (real
  `AudioContext` + WASM + recorder). Used by `e2e/audio-render.spec.ts` and
  `e2e/song.spec.ts`.
- App test handles: `app.cljs` sets `window.__REPULSE_EDITOR__` (CodeMirror view) and
  `window.__REPULSE_APP__` (`cycleDur`, `muted`, `selection`, `trackParam`, `fxParam`);
  used by `e2e/master.spec.ts`, `e2e/workspace.spec.ts`, ….
- `e2e/helpers/audio-analysis.ts`: `rms`, `rmsStereo`, `mixMono`, `countOnsets`,
  `tailRms`, `assertFinitePcm`, `goertzel` (normalised single-bin magnitude).
- CLJS unit tests run via `packages/core/src/repulse/test_runner.cljs` (`fx-test`,
  `song-transport-test`, `wav-test`, … are listed there twice: `:require` and
  `run-tests`).

### Docs that mention the limitation

- `docs/USAGE.md` § "`export` — render to WAV" (table + the sentence "The offline
  `(export n)` / `(export :track)` path uses JS fallbacks for WASM voices and skips FX"),
  § "`(export :song)` — capture the whole song", and the `song` vs `arrange` table
  (`(export :song)` (real time, with FX)).
- `docs/PLUGINS.md` § "Effect plugin protocol" table.
- `app/src/repulse/content/builtin_meta.edn` `:export` examples;
  `app/src/repulse/lisp-lang/completions.js` `export` detail.

## Implementation

### 0. Verify tooling (CLAUDE.md Rule 4)

`npm run gen:grammar && npm run gen:ai-docs && npm run test` on the unmodified branch.
Also `npx playwright test e2e/song.spec.ts` once, to confirm that real-time capture works
in the local headless Chromium before you build tests on it.

### 1. Pure helpers — `app/src/repulse/audio.cljs`

Extract, without changing real-time behaviour:

```clojure
(defn export-frames
  "Frame count for secs at sample rate sr — shared by offline render and capture."
  [secs sr]
  (Math/round (* secs sr)))

(defn tween-state-for
  "Pure core of arm-transitions!: {param {:tween pv :start-time start-time}}."
  [pattern start-time] …)

(defn rearm-tweens
  "Set every :start-time in a tween-state map (track → param → ts) to t0."
  [tween-state t0]
  (update-vals tween-state (fn [m] (update-vals m #(assoc % :start-time t0)))))

(defn resolve-tweens
  "The reduce-kv now inline in schedule-event!: interpolate tweened params of raw."
  [raw tween-st t cycle-dur] …)
```

`arm-transitions!` becomes `(swap! … assoc track-name (tween-state-for pattern now))`.
`schedule-event!` calls `resolve-tweens`. `arm-capture!` uses `export-frames` for
`frames`.

### 2. Decision 3 in the real-time capture — `arm-capture!`

Right after computing `t0`, before posting `"start"` to the recorder:

```clojure
(swap! scheduler-state update :tween-state rearm-tweens t0)
```

`arm-capture!` runs inside `resolve-song-origin!`, which `tick!` calls before
`schedule-cycle!`, so no event of the captured song has been scheduled with the old
start time (pre-roll bars return no onsets).

### 3. Offline graph lookup in `play-event` — `app/src/repulse/audio.cljs`

```clojure
;; OfflineAudioContext → {:master-gain node :worklet-node node :track-dests {kw node}}
(defonce ^:private offline-graphs (js/WeakMap.))
(defn register-offline-graph! [ac graph] (.set offline-graphs ac graph))
(defn- offline-graph [ac]
  (when (instance? js/OfflineAudioContext ac) (.get offline-graphs ac)))
```

- `output-for-track`: if `(offline-graph ac)` → `(get-in g [:track-dests track-name]
  (:master-gain g))`; otherwise unchanged.
- `worklet-trigger!` / `worklet-trigger-v2!` take a target `{:node :ready? :master}`
  instead of reading the atoms. A helper `(worklet-target ac)` returns the graph's
  `{:node (:worklet-node g) :ready? true :master (:master-gain g)}` for a registered
  offline context, the real-time atoms otherwise. The `(= dest master)` rule is unchanged
  (decision E1).
- In `play-event`, replace `offline?` by `js-only?` =
  `(and (instance? js/OfflineAudioContext ac) (nil? (offline-graph ac)))`. A registered
  offline graph therefore takes exactly the real-time branches, and an unregistered one
  (TEST1 harness) the JS branches it takes today.
- `output-node` (default dest of the JS voices' short arities): return the graph's
  `:master-gain` for a registered offline context.

### 4. FX: shared instantiation, param log, plugin flag — `fx.cljs`, `plugins.cljs`

- Extract from `add-track-effect!`:

  ```clojure
  (defn instantiate-plugin
    "Fresh instance of a registered effect plugin (decision 2): clone() when present,
     else Object.create + Object.assign."
    [^js p] …)
  ```

  `add-track-effect!` uses it. Its behaviour is unchanged.
- `set-param!` also does `(assoc-in entry [:params param-name] value)` (insertion
  order kept: use a vector of `[k v]` or an `array-map` re-assoc). `reset-global-effects!`
  sets `:params {}`. `make-effect-entry` starts with `:params {}`. (`specs/fx-entry?` in
  `app/src/repulse/specs.cljs` is open — extra keys pass.)
- Extract `(defn notify-plugins! [plugins value t])` from `notify-fx-event!` (which then
  calls it for the global and track plugins).
- `plugins/normalize-plugin!`: when it installs the default `getParams`, also
  `(aset plugin "__repulseDefaultGetParams" true)`.

### 5. Plugin readiness — `bitcrusher.js`, `dattorro-reverb.js`, `cab.js`

No DSP changes. In `createNodes`, keep the wiring promise:

```js
this._wired = this._ready.then(() => { /* existing wiring + _pending flush */ });
this._wired.catch(e => console.warn("[REPuLse] bitcrusher worklet failed to load:", e));
```

and add `ready() { return this._wired ?? Promise.resolve(); }` (rejects when wiring
failed). In `cab.js`, `_loadIR` stores the `generateIR(…)` promise as `this._irPending`
(resolved immediately for cached IRs and `"di"`). `ready()` returns it.

### 6. Offline worklet — `audio.cljs` + `app/public/worklet.js`

`worklet.js`: add to `_onMessage`

```js
} else if (msg.type === 'sync') {
  this.port.postMessage({ type: 'synced', id: msg.id });
}
```

`audio.cljs`: `(defn init-offline-worklet! [offline dest] → Promise<node>)`.
It calls `addModule (public-url "worklet.js")` on `(.-audioWorklet offline)`, creates
`AudioWorkletNode "repulse-processor" {outputChannelCount [2]}`, connects it to `dest`
(the offline master gain) and fetches `(public-url "repulse_audio_bg.wasm")` (rejects on
`!resp.ok`). It posts `init` (transfer), resolves on `"ready"`, rejects on `"error"`, on
any fetch/addModule failure and after 5 s. Plus `(defn sync-worklet! [node] →
Promise)` (posts `sync` with a counter id, resolves on the matching `synced`).

### 7. Offline renderer — `app/src/repulse/offline_render.cljs` (new)

Requires `audio`, `fx`, `bus`, `core`, `song`. No DOM, no WAV.

```clojure
(def pre-roll-secs 0.25)

(defn fx-plan
  "Pure. entries — global chain entries; used — set of effect names in use.
   → {:build [entry…] :skip [name…] :fail {:plugin name :reason str} or nil}"
  [entries used] …)

(defn render!
  "job — {:kind :cycles|:song, :tracks {kw pattern}, :song s (for :song), :n n,
          :tail secs, :cycle-dur cd}
   → Promise<{:left Float32Array :right Float32Array :sample-rate sr :frames n}>;
   rejects with (ex-info reason {:fallback true}) when the offline path can't run."
  [job] …)
```

Steps inside `render!`:

1. **Preconditions** (reject `{:fallback true}` with a readable reason):
   `js/OfflineAudioContext` or its `.audioWorklet` missing → `"AudioWorklet unavailable
   offline"`; `(seq (bus/active-buses))` → E10; `fx-plan` `:fail` → `plugin "<name>"
   can't be rebuilt offline (<reason>)`.
2. `sr (.-sampleRate (audio/get-ctx))` (E3). `pre (export-frames pre-roll-secs sr)`.
   `frames` = `(export-frames (* n cd) sr)` for `:cycles`, `(export-frames (+
   (song/song-duration-secs s (audio/get-bpm)) tail) sr)` for `:song`.
   `offline (js/OfflineAudioContext. 2 (+ pre frames) sr)`.
3. **Master**: `master` GainNode with gain = current `(.. @audio/master-gain -gain
   -value)`. Then the global chain, in `@fx/chain` order, for each `:build` entry:
   `fresh (fx/instantiate-plugin plugin)` →
   `(.init fresh offline-host)` with `offline-host #js {:audioCtx offline :sampleRate sr
   :masterGain master :analyser nil :version "1.0.0" :registerLisp (fn [_ _])}` (E7) →
   `nodes (fx/validate-effect-nodes! name (.createNodes fresh offline))` →
   `getParams` → `setParam` for every non-nil value (decision 2) → replay `:params` log
   (E6) → `(.bypass fresh true)` when `:bypassed?`. Wire `master → in1 … outN →
   destination` (empty chain: `master → destination`).
4. **Tracks**: for each exported track with non-empty `(:track-fx pattern)`: a gain node
   and, per metadata entry whose name has a global entry (others skipped, like
   `add-track-effect!`), the same instantiation with params from the metadata
   (`setParam` per `[k v]`, as `apply-track-effects!` does). Wire `gain → fx… → master`.
   `:track-dests {kw (:input first-fx)}`.
5. **Worklet**: `(audio/init-offline-worklet! offline master)`.
6. **Readiness**: `Promise.all` of `(.ready fresh)` for every instance that has `ready`,
   raced against a 5 s timeout; failure → reject `{:fallback true}` naming the plugin.
7. `(audio/register-offline-graph! offline {:master-gain master :worklet-node node
   :track-dests …})`.
8. **Events** (exports ignore mute, E12), each at `t' = pre-roll-secs + t`:
   - `:cycles`: for `c` in `0…n−1`, `core/query-onsets pattern {:start [c 1] :end [(inc c)
     1]}`, `t = whole-start × cd` (whole-start absolute, as the scheduler computes).
   - `:song`: for `bar` in `0…(song-length s)−1`, `(song/song-onsets s tracks #{} bar)`,
     `t = whole-start × cd` (already shifted to `bar`).
   - per track `tween-st (audio/tween-state-for pattern pre-roll-secs)` (decision 3);
     `value (audio/resolve-tweens (:value ev) tween-st t' cd)`;
     `(audio/play-event offline t' value track-kw)`;
     `(fx/notify-plugins! all-offline-instances value t')`.
9. `(audio/sync-worklet! node)`, then `startRendering`. Resolve with
   `(.slice (.getChannelData buf 0) pre (+ pre frames))` (and channel 1).

### 8. Export dispatch — `song_transport.cljs`, `env/builtins/export.cljs`

- `pending-export` becomes `{:kind :cycles :n n :track kw-or-nil :set-output! f}` or
  `{:kind :song :tail s :realtime? bool :set-output! f}`.
- `replay-song-buffer!` → `(replay-buffer! buffer start-bar opts)` with an explicit
  `{:tracks :song}` argument. The old `replay-song-buffer!` passes `@song-buffer`.
  `capture-song!` likewise takes the buffer.
- `start-export!` dispatches:
  - `:song`, not `:realtime?` → `offline-render/render!` with `{:kind :song :tracks (into
    {} (:tracks @song-buffer)) :song s :tail tail :cycle-dur cd}`. The song plays `:end
    :stop`, i.e. one pass, matching the capture.
  - `:cycles` → tracks = `(:tracks @audio/scheduler-state)` (already activated), filtered
    to the one track when `:track`; render `{:kind :cycles …}`. Filename unchanged.
  - success → `wav/encode-wav-16` + `wav/download-wav!` + success message (E15).
  - rejection with `{:fallback true}` → fallback message, then the real-time capture:
    `:song` → `capture-song! @song-buffer`; `:cycles` → synthetic song (E9) captured with
    tail 0 → same filename as the offline result.
  - other rejections → `Error: export failed — <message>` (`:error`).
  - `export-running?` atom set for the whole duration (E14).
- `export.cljs`: parse per E13. All validation stays synchronous and returns the existing
  `Error: …` strings (`no active tracks to export`, `no track :x`, `no song — …`,
  `:tail must be …`). `(export :song :realtime)` keeps the AudioWorklet check
  (`(export :song) needs AudioWorklet support for real-time capture`). The call only sets
  `pending-export` and returns the start message. The ns docstring is updated.

### 9. Test handles — `app/src/repulse/app.cljs`

Add to `__REPULSE_APP__`:

```clojure
:evaluate     (fn [code] (eo/evaluate! code))   ; = command bar
:audioBackend (fn [] (if (and @audio/worklet-ready? @audio/recorder-node)
                       "audioworklet+wasm" "js"))
```

### 10. Tests

**CLJS unit** (`app/src/repulse/offline_render_test.cljs` new, plus additions to
`fx_test.cljs` and `song_transport_test.cljs`). Register the new ns in
`packages/core/src/repulse/test_runner.cljs` (both lists):

- `export-frames`: `(export-frames 8.0 44100) = 352800`, `(export-frames (* 4 (/ 240
  140)) 48000) = 329143` (the old `int` gave 329142).
- `tween-state-for` / `rearm-tweens` / `resolve-tweens`: a `(tween :linear 0 1 4)` amp
  at `cycle-dur 1.0` armed at 0.25 → value 0.0 at t=0.25, 0.5 at t=2.25, 1.0 at t ≥ 4.25.
- `fx-plan`: a `type "visual"` stub entry → `:skip`; a used entry whose plugin has
  `__repulseDefaultGetParams` → `:fail` naming it; the same plugin unused → `:build`.
- `set-param!` writes `:params`; `reset-global-effects!` clears it.
- Export argument grammar (E13) → `pending-export` maps and error strings.

**E2E** — new `e2e/export-parity.spec.ts` against `/` (E16). Helpers added to
`e2e/helpers/audio-analysis.ts`: `parseWav16(buf: Buffer) → {sampleRate, frames,
left, right}` (asserts RIFF/WAVE, PCM, 2 ch, 16 bit), `rmsPerWindow(mono, size)`,
`toDb(x) = 20·log10(max(x, 1e-9))`. Flow per test: `page.goto('/')`, wait for
`__REPULSE_APP__`, poll `audioBackend()` until `"audioworklet+wasm"` (15 s), set the editor
text, `Alt+Enter` (as `e2e/master.spec.ts` does), or call `__REPULSE_APP__.evaluate(code)`
for command-bar exports. Wrap the trigger in `page.waitForEvent('download')`, then read
`download.path()` and parse. Accept the plugin-consent `confirm` with
`page.on('dialog', d => d.accept())`. All songs are short (≤ 5 s real time) so the
real-time captures fit the 60 s test timeout. Set `test.setTimeout(60_000)` on capture
tests. The exact programs and thresholds are in the Definition of done.

The worklet-failure test installs, **after** `audioBackend()` reports
`"audioworklet+wasm"` (so the live worklet is already up),
`page.route('**/worklet.js', r => r.abort())` and
`page.route('**/repulse_audio_bg.wasm', r => r.abort())`. The WASM fetch is a main-thread
`fetch` and is always intercepted; whether Chromium routes worklet module fetches is
irrelevant to the outcome. Playwright disables the HTTP cache while routes are active.

### 11. Docs, AI docs (CLAUDE.md Rules 2 and 3)

- `docs/USAGE.md`: rewrite the `export` table (offline for `n`, `:track`, `:song`;
  `:realtime` row; sample rate = audio device rate; offline exports render faster than
  real time with WASM voices, per-track and master FX). Delete the "JS fallbacks / skips
  FX" sentence. Add a note on decision E1 (a track with its own `(fx …)` plays and
  exports its WASM synth voices through the JS fallback; put FX in `(master …)` to keep
  the WASM voice). Note the fallback notice, and that tweens start at the export's bar 0.
  Update `(export :song)` § and the `song` vs `arrange` table (`(export :song)` offline,
  `(export :song :realtime)` real time).
- `docs/PLUGINS.md`: protocol table rows for `ready()` (optional) and a short "Offline
  export" paragraph (`init(host)` is called again on a clone with an offline host;
  `getParams` is needed for offline export of a used plugin).
- `builtin_meta.edn` `:export`: add `"(export :song :realtime)"` to `:examples`. Run
  `npm run gen:ai-docs` and commit `docs/ai/builtins.json`.
- `completions.js` `export` detail: mention `:realtime`. No new built-in name, so no
  grammar change.
- `README.md` (~line 59): `(export :song)` now renders offline; mention `:realtime`.
- `ROADMAP.md` / `CLAUDE.md`: mark delivered (Rule 2).

### 12. PR test protocol (CLAUDE.md Rule 5)

Last section of the PR description, per Rule 5 in `CLAUDE.md`
(and `.claude/commands/implement-phase.md` step 7). At minimum: `:epiano` export
vs. playback, master reverb in an `(export 4)`, `(export :song)` vs.
`(export :song :realtime)` A/B listen, the fallback notice (DevTools → block
`repulse_audio_bg.wasm`), and a regression check that playback is unchanged.

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/audio.cljs` | `export-frames`, `tween-state-for`, `rearm-tweens`, `resolve-tweens`; `arm-capture!` re-arms tweens (decision 3) + uses `export-frames`; offline-graph `WeakMap`, `register-offline-graph!`, graph-aware `output-for-track` / `output-node` / worklet triggers; `play-event` `js-only?`; `init-offline-worklet!`, `sync-worklet!` |
| `app/public/worklet.js` | `sync` → `synced` message |
| `app/src/repulse/offline_render.cljs` | **new** — `pre-roll-secs`, `fx-plan`, `render!` |
| `app/src/repulse/fx.cljs` | `instantiate-plugin` (used by `add-track-effect!`), `:params` log in `set-param!` / `reset-global-effects!` / `make-effect-entry`, `notify-plugins!` |
| `app/src/repulse/plugins.cljs` | `__repulseDefaultGetParams` flag in `normalize-plugin!` |
| `app/public/plugins/bitcrusher.js` | `_wired` promise + `ready()` (no DSP change) |
| `app/public/plugins/dattorro-reverb.js` | `_wired` promise + `ready()` (no DSP change) |
| `app/public/plugins/cab.js` | `_irPending` + `ready()` (no DSP change) |
| `app/src/repulse/song_transport.cljs` | `pending-export` kinds, `replay-buffer!` / `capture-song!` with explicit buffer, synthetic-song fallback, offline dispatch in `start-export!`, `export-running?` |
| `app/src/repulse/env/builtins/export.cljs` | argument grammar (E13), deferred start, messages (E15); no rendering code left here |
| `app/src/repulse/app.cljs` | `__REPULSE_APP__.evaluate`, `.audioBackend` |
| `app/src/repulse/offline_render_test.cljs` | **new** — unit tests |
| `app/src/repulse/fx_test.cljs` | param log tests |
| `app/src/repulse/song_transport_test.cljs` | export grammar / pending-export tests |
| `packages/core/src/repulse/test_runner.cljs` | register `repulse.offline-render-test` |
| `e2e/export-parity.spec.ts` | **new** — Playwright tests (Definition of done) |
| `e2e/helpers/audio-analysis.ts` | `parseWav16`, `rmsPerWindow`, `toDb` |
| `docs/USAGE.md` | export section, `(export :song)` §, song vs arrange table |
| `docs/PLUGINS.md` | `ready()`, offline export notes |
| `app/src/repulse/content/builtin_meta.edn` | `:export` example `(export :song :realtime)` |
| `docs/ai/builtins.json` | regenerated by `npm run gen:ai-docs` |
| `app/src/repulse/lisp-lang/completions.js` | `export` detail mentions `:realtime` |
| `README.md` | line ~59 says `(export :song)` "records the whole song" — reword to "renders" (offline, `:realtime` to record) |
| `ROADMAP.md`, `CLAUDE.md` | mark EXP1 delivered (Rule 2) |

`app/src/repulse/test_api.cljs` and `app/public/test-harness.html` are **not** changed.

## Definition of done

Unless stated otherwise, tests run against the production app (`/`) in Playwright and
read the WAV from the download. `sr` = the WAV header's sample rate, which must equal
`AudioContext.sampleRate` of the page. `cd` = `240 / bpm` seconds per bar. "Sine
baseline" means the same analysis applied to `make-sine` output, which is what the old
path produced.

- [ ] **Length.** `(bpm 240) (track :a (seq :bd :_ :hh :_)) (export 4)` downloads
  `repulse-all-4cycles.wav` with exactly `round(4 × 1.0 × sr)` frames: **176 400** at
  44.1 kHz, **192 000** at 48 kHz. Unit test: `export-frames` gives **352 800** for 8 s at
  44.1 kHz and **329 143** for `(export 4)` at 140 BPM / 48 kHz.
- [ ] **FM sidebands.** `(bpm 240) (track :keys (->> (seq :c4) (synth :epiano)))
  (export 4)`, analysing the first 0.4 s with `goertzel`. Carrier c4 = **261.63 Hz**,
  modulator = 14 × 261.63 = **3662.76 Hz**, index 1.5 (theory: J₁(1.5)/J₀(1.5) ≈ 1.09).
  Each first-order sideband, **3401.13 Hz** and **3924.38 Hz**, must be ≥ **0.3 ×** the
  261.63 Hz magnitude. Each second-order sideband, **7063.89 Hz** and **7587.14 Hz**, must
  be ≥ **0.05 ×** it. The sine baseline is < 0.01 × at all four. The output panel shows
  `exported all tracks — 4 cycles (offline)`.
- [ ] **Plucked string is not a sine.** `(bpm 240) (track :gtr (->> (seq :a3) (synth
  :guitar))) (export 4)`: f0 = the spectral peak in 150–300 Hz of the first 0.5 s
  (expected ≈ 220 Hz; KS period `floor(sr/220)` = 200 samples at 44.1 kHz, 218 at
  48 kHz). The strongest harmonic among k = 2…5 (max `goertzel` in k·f0 ± 2 %) must be
  ≥ **0.1 ×** the f0 magnitude. Sine baseline: < 0.01 ×.
- [ ] **Track reverb tail.** `(bpm 240) (track :pad (->> (seq :c4 :_ :_ :_) (synth :saw)
  (decay 0.1) (fx :reverb 0.4))) (export 4)`. The last note starts at 3.0 s and its
  envelope ends by 3.101 s. RMS of [3.2 s, 3.9 s] must be ≥ **1e-3**. The same program
  without `(fx :reverb 0.4)` must give ≤ **1e-4** there.
- [ ] **Master delay echo.** `(bpm 120) (track :hat (seq :hh :_ :_ :_)) (master (fx
  :delay :wet 0.5 :time 0.25)) (export 1)`. The echo lag is `round(0.25 × sr)` =
  **11 025** samples at 44.1 kHz, **12 000** at 48 kHz. The cross-correlation of the
  first 50 ms against the signal, searched over lags 0.20–0.30 s, peaks within **±2
  samples** of that lag. The 10 ms-window RMS peak in [0.24, 0.30] s must be ≥ **0.25 ×**
  the peak in [0, 0.05] s. Without the `master` form it is ≤ **0.02 ×**.
- [ ] **Offline `(export :song)` vs `(export :song :realtime)`.** Song P:
  ```lisp
  (bpm 240)
  (track :kick (seq :bd :_ :bd :_))
  (track :hats (fast 2 (seq :hh :_)))
  (track :bass (->> (seq :c2 :_ :g2 :_) (synth :saw)))
  (track :keys (->> (seq :c4 :e4) (synth :epiano)))
  (track :snare (->> (seq :_ :sd) (fx :filter :freq 2000)))
  (song (section :a 2 [:kick :bass :snare]) (section :b 2 :all))
  (master (fx :delay :wet 0.3 :time 0.25))
  ```
  exported with `(export :song :tail 1)` and with `(export :song :tail 1 :realtime)`.
  Both WAVs have exactly `round(5.0 × sr)` frames (**220 500** / **240 000**) and the same
  sample rate. Mono RMS per 1 s window (4 bars + tail) agrees within **1 dB** for every
  window whose real-time RMS ≥ 1e-3. Windows below that are < 1e-3 in both. The song
  has no reverb on purpose (random IR per instance, see Background).
- [ ] **Plugin worklets offline.** `(bpm 240) (track :t (seq :a4)) (master (fx
  :bitcrusher :wet 1 :bits 3)) (export 1)`: the 1320 Hz (3rd-harmonic) magnitude of the
  first 0.3 s must be ≥ **0.05 ×** the 440 Hz magnitude. Without the `master` form it is
  ≤ **0.005 ×**. This also proves that `bits` survives the replay (E6), because
  `getParams` returns only `wet`. And `(bpm 240) (track :t (seq :hh :_ :_ :_)) (master (fx
  :dattorro-reverb 0.5)) (export 2)`: RMS of [0.3 s, 1.5 s] ≥ **1e-3**. Without the
  `master` form it is ≤ **1e-4**. Both print `(offline)`, not the fallback notice.
- [ ] **Fallback.** With `**/worklet.js` and `**/repulse_audio_bg.wasm` routes aborted
  after the live backend is up, `(bpm 480) (track :lead (->> (seq :a3 :a3) (synth :saw)))
  (song (section :a 2 [:lead])) (export :song :tail 0.5)` prints `offline render
  unavailable (…) — capturing in real time instead…` and still downloads a WAV of exactly
  `round(1.5 × sr)` frames. Its 440 Hz `goertzel` is ≥ 0.1 × the 220 Hz one (saw
  harmonics, as in `e2e/song.spec.ts`, not a sine). `(export 2)` under the same routes
  also falls back and downloads `round(2 × 0.5 × sr)` frames. Unit test: a used plugin
  without its own `getParams` makes `fx-plan` return `:fail` with its name, and the notice
  names it.
- [ ] **`:realtime` path.** `(export :song :realtime)` produces SONG1's capture: output
  `exporting song — … in real time… (stop) cancels`, then `exported song — 2 bars, 1.5 s`
  for the fallback song above with `:tail 0.5`, never `(offline)`. `(stop)` during it
  prints `export cancelled`. `(export 4 :realtime)` returns `Error: :realtime only applies
  to (export :song)`.
- [ ] **Visualisers.** After `(load-plugin "/plugins/oscilloscope.js")` (dialog accepted),
  `(bpm 240) (track :keys (->> (seq :c4 :e4) (synth :epiano) (fx :oscilloscope)))
  (export 2)` succeeds with `(offline)`. Its samples differ by at most **1 LSB** (1/32767)
  from the same export made before loading the plugin and without `(fx :oscilloscope)`.
  Unit test: a `type "visual"` entry is `:skip` in `fx-plan`.
- [ ] **Tween reproducibility.** Song T:
  `(bpm 480) (track :lead (->> (seq :c4 :e4 :g4 :c5) (synth :saw) (amp (tween :linear 0.0
  1.0 4)))) (song (section :a 4 [:lead]))` (4 bars = 2.0 s). Export A: buffer T followed
  by `(export :song :tail 0.5)`, evaluated with Alt+Enter. Export B: T evaluated,
  playback left running for ≥ 3 bars (1.5 s), then `__REPULSE_APP__.evaluate("(export
  :song :tail 0.5)")`. Mono RMS per bar (0.5 s windows) of A and B agree within **0.1 dB**
  for every bar. Bar 0 is the quietest and the RMS rises monotonically over bars 0–3.
  Repeat both with `:realtime`: same 0.1 dB bound. Unit test: `rearm-tweens` sets every
  start time to the given t0.
- [ ] **Real-time graph unchanged.** `e2e/song.spec.ts`, `e2e/master.spec.ts` and the rest
  of the existing suite pass unmodified. `audio/play-event` on a real-time context takes
  the same branches as before (the only real-time code change is `arm-capture!`
  re-arming tweens).
- [ ] **TEST1 untouched.** Existing TEST1 offline-harness tests still pass
  (`e2e/audio-render.spec.ts` unmodified; `__REPULSE_TEST__.backend()` still returns
  `"offline-js"`). `app/src/repulse/test_api.cljs` has no diff.
- [ ] **Docs.** `docs/USAGE.md` export section no longer lists the "no FX / sine fallback"
  limitation. It documents `:realtime`, the device sample rate, the fallback notice and
  decision E1. `docs/PLUGINS.md` documents `ready()`. `builtin_meta.edn` has the
  `:realtime` example and `npm run check:ai-docs` is clean.
- [ ] **Verification.** `npm run test:all` and `npx shadow-cljs compile app` pass
  (CLAUDE.md pre-push verification).
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5)

## What NOT to do

- No stems or MIDI export — that is SK1.
- No new FX plugins or changes to plugin DSP (the `ready()` additions are protocol only).
- Don't change the real-time audio graph. In particular, don't route WASM voices through
  per-track FX in real time (decision E1; separate follow-up phase).
- Don't change the TEST1 `"offline-js"` harness backend; it tests something else
  (`test_api.cljs`, `test-harness.html` stay as they are).
- Don't render buses offline (E10) or make offline renders cancellable (E14).
- Don't copy scheduler code into the renderer: tweens, routing and dispatch go through the
  shared helpers in `audio.cljs`, so SONG2/TL7 and a later real-time routing fix carry
  over.
