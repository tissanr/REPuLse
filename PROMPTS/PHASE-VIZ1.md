# Phase VIZ1 — Honest Track Visuals & In-Code Visualizers

> **Spec status:** full
> **Automation:** 🤖👂 auto + human check. The event layout comes from a pure model with
> unit tests, the per-track meters from Rust tests, and the panel and inline widgets are
> checked with Playwright. Final acceptance needs a human **watch-and-listen check**
> (see *Definition of done*). The complaint behind this phase is perceptual ("it doesn't
> look like what I hear"), and no automated test can confirm that it now does.

**Milestone:** M2 — Full songs in code
**Depends on:** WS3 (inline editor widgets), WS5 (track forms are the source of truth),
TL7b (multi-bus worklet output), SONG1 (song mode); all delivered.

## Goal

The track panel under the editor doesn't show what you hear. Three of its six modes
(`wave`, `spec`, `loud`) never read the audio: they draw a fixed-frequency sine or bars,
shaped by a made-up envelope that starts at each event's onset. The animation runs on
wall-clock seconds times arbitrary factors, so it ignores the tempo. There's no playhead.
QA3 switched it to the integer cycle at `currentTime`, but that still ignores output
latency. `roll` places `:c3` on a row computed from the name's character codes. Found by
the user on 2026-11-24.

This phase does two things:

1. **Makes the panel honest.** Every mode shows either the **exact pattern events**,
   positioned by a playhead that follows the *heard* clock, or the **track's real
   audio**, from new per-track meters.
2. **Adds Strudel-style visualizers to the code.** Wrap a track's pattern in
   `pianoroll`, `punchcard`, `spiral`, `scope` or `spectrum`, and a live view of that
   track appears inline, under its form. This matches Strudel's `._pianoroll()`-style
   inline visuals (parity row in `docs/STRUDEL-PARITY.md`, *Visuals, I/O & JS-specific*).

```lisp
;; before: the bottom panel shows a decorative wave; nothing in the code
(track :lead (->> (seq :c3 :eb3 :g3 :bb3) (synth :saw)))

;; after: a piano roll scrolls under this form, with :c3 :eb3 :g3 :bb3 on their real
;; pitch rows and a playhead that crosses each note exactly when it sounds
(track :lead (pianoroll (->> (seq :c3 :eb3 :g3 :bb3) (synth :saw))))

;; after: a live oscilloscope of this track's own audio (not the master mix)
(track :bass (scope (->> (seq :c2 :_ :c2 :g1) (synth :saw))))

;; after: options map first, pattern last, so ->> threading still works
(track :drums (->> (seq :bd :sd :bd :sd) (punchcard {:cycles 2 :labels true})))
```

## Background

### Track panel — `app/src/repulse/ui/timeline.cljs` (`repulse.ui.timeline`)

- `modes`: `wave spec loud beat roll seg`; `viz-mode` atom (default `"wave"`).
- `active-track-data` reads `@audio/scheduler-state` (`:tracks :muted`) and the cycle
  from `audio/audible-cycle` (QA3 A2: the floor of `tempo/cycle-at-time state
  currentTime`, so it handles tempo changes but not output latency). It calls
  `cached-track-events` → `track-events`, which queries **one cycle**, `[cycle,
  cycle+1)`, using `:part` times. `events-cache` (QA3) queries each track at most once per
  cycle; `query-count` and `last-cycle` are test handles. Keep an equivalent cache and
  equivalent handles.
- `event-energy` / `energy-curve` / `energy-cache`: a synthetic envelope (`exp(-10·d/dur)`)
  per onset. `draw-wave!` multiplies it by `sin(t·56 + ph·2)`; `draw-spec!` draws it as
  bars; `draw-loud!` as a filled area. None of them read audio.
- `phase` advances by `ms × 0.001` in `start-playhead-raf!` while playing; the draw
  functions scroll by `ph × 0.12 / 0.16 / 0.1 / 0.18`. Tempo has no effect.
- `value-pitch` maps a number to `(mod n 12)` and a keyword to the sum of its char codes
  mod 12, so `:c3` and `:c4` share a row, `:e3` lands on a meaningless row, and `:bd`
  gets a pitch.
- `song-resting-tracks` dims tracks that don't play in the current song bar.
- `render-track-panel!` builds the toolbar (mode buttons, track mute buttons that call
  `js/window._repulseMuteToggle`) and a `1600×126` canvas `#track-viz-canvas`. It is
  re-rendered from `app.cljs` when tracks change.
- `timeline_test.cljs` covers `track-events`, `energy-curve`, `sample-energy`,
  `value-pitch` and `song-resting-tracks`.

### Heard clock — `app/src/repulse/audio_clock.cljs`, `audio.cljs`

- `audio-clock/latency-s`, `sane-output-ts?`, `audible-perf-ms snap t` map an
  AudioContext time to the `performance.now()` time when it is *heard* (output latency
  included). `audio/clock-snapshot ac` takes the snapshot.
- `schedule-visual! at-ms f` runs `f` on the animation frame nearest `at-ms`; the editor
  highlight and beat dot use it via `:on-event` / `:on-beat` in `schedule-event!`.
- `repulse.tempo/cycle-at-time state t` maps an AudioContext time to a (float) cycle
  position, tempo changes included; `audio/audible-cycle` floors it at `currentTime`.
  In song mode, the song bar is that cycle minus the song's `:origin`
  (`song/song-onsets`, `song/wrap-bar`).

### Pitch of an event — `audio.cljs` `schedule-event!` (MIDI-out branch)

The MIDI-out branch already computes the pitch an event really plays: `:freq` → Hz;
`:note` keyword → `theory/note->hz`; `:note` number → Hz; bare note keyword → Hz; **bare
number → Hz** (numbers are frequencies in REPuLse); else nil. Then `midi/hz->midi`.
Velocity is `(:amp value 1.0)`.

### Audio routing — `audio.cljs`

- `track-nodes`: `kw → {:gain-node :fx-chain}`, created by `ensure-track-node!`.
- `output-for-track ac track` returns the track's first FX input, else `master-gain`.
  **The track's `gain-node` is not on the voice path.**
- `play-event`: `dest = (output-for-track …)`. WASM voices are dispatched by
  `worklet-trigger!` / `worklet-trigger-v3!` **only when `dest` is the master gain**
  (decision E1). Otherwise JS voices are built and connected through
  `js-dest = (event-output ac t ins snd dest dur …)`. Sample playback is always JS.
- Consequence: synth voices of tracks without per-track FX are mixed **inside the
  single worklet** (`app/public/worklet.js`, processor `repulse-processor`) and never
  pass a per-track node. A per-track `AnalyserNode` alone can't see them.
- Master analyser: `analyser-node`, fed via `master-tap` → recorder → analyser. The
  `oscilloscope.js` / `spectrum.js` plugins (`app/public/plugins/`) read it.

### WASM engine — `packages/audio/src/lib.rs`

- `AudioEngine { voices: Vec<ActiveVoice>, pending, bus: Vec<f32>, … }`.
- `trigger_v3(value, time, params: &[f32])`: a fixed slot layout documented above the
  method (0–14 filters/sends, 15–40 TL7c, 41–43 SYN6; later phases append, never
  reorder). A missing trailing slot means its default.
- `process_block_buses(n, t)` / `process_block_buses_raw`: renders into the
  pre-allocated `bus` (`[bus][frame][channel]`, `N_BUSES = 9`). The render path is
  allocation-free (CLAUDE.md, *WASM synthesis*).

### Inline widgets — `app/src/repulse/ui/inline_slider.cljs` (WS3)

A CodeMirror `StateField` scans the Lezer tree for `(slider …)` forms and builds
`Decoration`s with a `WidgetType`. It isn't loaded in the embed. VIZ1's widgets follow
the same pattern but use **block** decorations (`Decoration.widget {block: true}`)
placed after the line where the enclosing top-level form ends.

### Track forms — `env/builtins/tracks.cljs`, `repulse.outline`

`(track :name pat [:mute] [:solo])` registers the pattern unchanged in
`scheduler-state :tracks`. `repulse.outline/extract` returns entries with kind
`:track`, name and `:from`/`:to`. A pattern is a map `{:query f ::core/pat true}`, so
extra keys survive `track` but are dropped by any combinator that builds a new pattern.

## Decisions

1. **Two sources, never synthesized.** Event views (`punchcard`, `pianoroll`, `spiral`)
   draw the exact queried events. Audio views (`scope`, `spectrum`, `level`) draw the
   track's measured audio. Nothing is generated from a made-up envelope.
2. **One clock: heard time.** Every view derives its position from the float
   `heard-cycle = tempo/cycle-at-time state (ctx.currentTime − latency-s)`. In song mode
   the song bar comes from the same value. Add `audio/heard-cycle-pos` next to
   `audible-cycle` and switch the panel's per-cycle cache to its floor. No `phase` atom,
   no wall-clock factors. When stopped, views freeze at the last heard position.
3. **Event views query a window, not one cycle.** For example, `pianoroll` defaults to
   4 cycles with the playhead at 0.5: `[heard − 2, heard + 2)`. Times come from
   `:whole` (fall back to `:part` for continuous events, drawn hatched). Queries are
   cached per `[track pattern-identity cycle-floor]` and recomputed only when the
   integer cycle or the pattern changes (pattern identity = `identical?`).
4. **Pitch mapping reuses the MIDI-out logic.** Extract it from `schedule-event!` into
   a pure `audio/event-hz value` (no behaviour change for MIDI). Pitched events go on
   their MIDI row. Unpitched events (`:bd`, samples, `nil` Hz) go into a separate
   *drum lane* above the pitch rows, one sub-row per distinct sound in order of first
   appearance. Velocity `(:amp v 1)` sets opacity (`0.25 + 0.75·amp`, clamped).
5. **Per-track audio comes from two taps, combined on the main thread.**
   - *JS voices* (samples, FX-routed tracks): each track gets a unity `tap` GainNode
     with an `AnalyserNode` (`fftSize 2048`) on the side. Only `js-dest` is redirected
     through the tap; **`dest` (and so WASM eligibility) is unchanged**.
   - *WASM voices*: the engine meters per **slot**. The main thread assigns each track a
     slot 0–15 (`meter-slots` atom; slot `-1` = unmetered, shown as "no meter: more
     than 16 tracks"). The slot travels in a new `trigger_v3` params slot appended to the
     layout: **slot 44** if nothing else has been appended by then, otherwise the next
     free one; document it in the layout comment. In the
     render loop each voice's dry output is also summed into a pre-allocated mono
     `slot_bus[16][128]`; per slot the engine keeps a 2048-sample ring and a running
     peak/RMS. No allocation in the render path.
   - The worklet sends snapshots back with **ping-pong transferable buffers**: the main
     thread posts `{type "meter-req" buf}` with a `Float32Array(16·2048 + 32)` at most
     30×/s; the processor fills it (rings + peak/RMS) and transfers it back. In the
     steady state nothing is allocated on the audio thread.
   - A track's scope is the sum of its JS tap's time-domain data and its WASM ring. The
     two sources aren't sample-aligned. That's acceptable: a track almost always plays
     one kind of voice. Document it as a known limit.
6. **In-code visualizers are pattern decorations.** `(pianoroll pat)` returns `pat`
   with `:viz [{:kind :pianoroll :opts {…}}]` added (stacked calls concatenate, at most
   3 per track). Events are unchanged, so playback, export and tests are unaffected.
   The UI reads `(:viz pattern)` from `scheduler-state :tracks`; the editor only uses the
   source to *place* the widget, under the enclosing top-level form.
7. **Viz must wrap the whole track pattern.** A combinator applied after a viz builds a
   new pattern and drops `:viz`. Viz built-ins record that they ran in an eval-scoped
   `viz-calls` atom (reset per top-level form by the orchestrator). When `track` gets a
   pattern without `:viz` but `viz-calls` is non-empty, it warns (exact text below).
8. **Panel modes become** `punch` (default), `roll`, `scope`, `spec`, `level`. `wave`,
   `loud`, `beat` and `seg` are removed; `seg`'s labels live on in `punch`
   (`:labels`, drawn when they fit). The toolbar, mute buttons and song dimming stay.
9. **One renderer for panel and widgets.** `repulse.ui.viz-draw` draws any view into
   `(ctx x y w h track-data opts)`. The panel calls it per row, inline widgets per canvas.
   A single RAF loop (the existing `start-playhead-raf!`, renamed `start-viz-raf!`)
   draws every visible canvas. Widgets outside the viewport are skipped
   (`IntersectionObserver`).

### Options (exact)

| Built-in | Options (default) | Notes |
|---|---|---|
| `pianoroll` | `:cycles` (4, 1–16), `:playhead` (0.5, 0–1), `:labels` (false), `:fold` (true), `:height` (80, 32–240 px) | `:fold false` shows the full range from lowest to highest pitch of the window, `true` only used rows |
| `punchcard` | `:cycles` (1, 1–16), `:labels` (true), `:height` (48) | Static grid of whole cycles, playhead sweeps it; same pitch/drum lanes as `pianoroll` |
| `spiral` | `:cycles` (2, 1–8), `:height` (120) | One turn per cycle, events as arcs, playhead as a rotating radius |
| `scope` | `:scale` (1, 0.1–10), `:trigger` (true), `:height` (64) | Time-domain; `:trigger` aligns on a rising zero crossing |
| `spectrum` | `:log` (true), `:height` (64) | Per-track FFT (2048), 20 Hz–20 kHz, dB −90…−10 |

### Error and warning texts (exact)

- Viz dropped: `pianoroll has no effect here — it must wrap the whole track pattern, e.g. (track :lead (pianoroll …))` (name substituted).
- Not a pattern: `pianoroll expects a pattern as its last argument, got 3` (printed value).
- Unknown option: `pianoroll: unknown option :cycle — did you mean :cycles?` (fuzzy match
  over that built-in's option keys; without a close match: `… known options: :cycles :playhead :labels :fold :height`).
- Out-of-range option: `pianoroll: :cycles must be between 1 and 16, got 40`.
- More than 3 vizzes on one track (warning, extras ignored): `track :lead shows at most 3 visualizers — ignoring spiral`.

## Implementation

### 1. Pure model — `app/src/repulse/ui/viz_model.cljs` (new, `repulse.ui.viz-model`)

No DOM, no audio. Unit-tested in `viz_model_test.cljs`:

- `heard-cycle cycle-at-fn ctx-time latency` → float (`cycle-at-fn` is
  `#(tempo/cycle-at-time state %)` in the app, a stub in tests).
- `window [heard opts]` → `[from to]` cycle floats from `:cycles` / `:playhead`.
- `layout-events [events]` → events with `:start :end` (floats, from `:whole`, `:part`
  fallback with `:continuous? true`), `:midi` (from `audio/event-hz` + `hz->midi`, nil
  if unpitched), `:lane` (drum sound name for unpitched), `:alpha`, `:label`.
- `rows [laid-out opts]` → `{:pitch-rows [midi…] :drum-lanes [name…]}` honouring `:fold`.
- `validate-opts kind opts` → `{:ok opts'}` or `{:error msg}` using the exact texts above.
- `scope-trigger-index samples` → index of the first rising zero crossing (or 0).

Move `song-resting-tracks` here from `timeline.cljs` (keep its test).

### 2. Shared pitch — `audio.cljs`

Extract `(defn event-hz [value] …)` from the MIDI branch of `schedule-event!` and call
it there. Pure. Add a test next to the existing audio-clock tests.

### 3. Per-track taps (JS voices) — `audio.cljs`, `fx.cljs`

- `ensure-track-node!` also creates `:tap` (GainNode, gain 1) and `:analyser`
  (`fftSize 2048`, `smoothingTimeConstant 0`); `tap → analyser` (dead end) and
  `tap → (first-fx input | master-gain)`.
- In `play-event`, `js-dest` wraps `(track-tap track-name dest)` instead of `dest`.
  `dest` stays for the `worklet-trigger!` eligibility check (`(= dest master)`).
- `fx.cljs`: in `rewire-track!` (used by `add-track-effect!`,
  `clear-track-effects!`, `apply-track-effects!`), reconnect `tap`'s forward output to the new first input.
- Cleanup paths (`stop!`, `clear-track!`) disconnect `tap` and `analyser`.
- Offline contexts: no taps (export and the TEST1 harness are unchanged).

### 4. Per-track meters (WASM voices) — `packages/audio/src/lib.rs`, `app/public/worklet.js`

- `ActiveVoice` gets `meter_slot: i8` from the new params slot (decision 5; `-1` when
  missing).
- `AudioEngine` gets pre-allocated `slot_bus: [[f32; 128]; 16]`,
  `rings: [[f32; 2048]; 16]`, `ring_pos: [usize; 16]`, `peak: [f32; 16]`,
  `rms_acc: [f32; 16]`. In `process_block_buses_raw`, add each voice's mono dry sample
  to `slot_bus[slot][i]`; after the block, copy into the rings and update peak/RMS
  (peak decays 20 dB/s).
- `pub fn meter_snapshot(&self, out: &mut [f32])` writes 16 rings (in time order) then
  16 peaks and 16 RMS values. Allocation-free.
- Rust tests (`npm run test:rust`): (a) a voice on slot 3 produces RMS > 0.001 in slot 3
  and exactly 0 in every other slot; (b) a voice without the meter slot param meters
  nowhere; (c)
  rendering 1000 blocks with metering leaves `voices`/`bus` capacities unchanged (no
  growth).
- `worklet.js`: on `{type:"meter-req", buf}`, call `meter_snapshot` into `buf` (via the
  wasm memory view) and `port.postMessage({type:"meter", buf}, [buf.buffer])`.
- `audio.cljs`: `meter-slots` atom (assign on `set-track!`, free on remove); pass the
  meter slot param in `worklet-trigger-v3!`; `request-meters!` (≤30 Hz, only while a scope/spectrum/
  level view is visible) and `track-audio track-name` →
  `{:samples Float32Array(2048) :peak :rms}` merged from tap + ring.

`npm run build:wasm` after the Rust change.

### 5. Renderer — `app/src/repulse/ui/viz_draw.cljs` (new)

`draw! kind ctx rect track-data opts`, with one private draw fn per kind: `punchcard`,
`pianoroll`, `spiral`, `scope`, `spectrum`, `level` (peak bar + 4-second RMS history).
Colours from the existing `track-colors`; muted/resting tracks keep the `#3a4868` dim
style. The playhead is a 1 px vertical line (pianoroll/punchcard) at the heard
position. Spectrum runs a radix-2 FFT (2048, Hann) over `:samples`; write the FFT in
plain CLJS in the app layer (no library).

### 6. Panel — `app/src/repulse/ui/timeline.cljs`

Replace `modes`, delete `event-energy`, `energy-curve`, `sample-energy`,
`energy-cache`, `value-pitch`, `phase` and the `draw-*` fns (and their tests). Rows call
`viz-draw/draw!`. Event views take their window from `viz-model` with panel defaults
(`punch` 1 cycle, `roll` 4 cycles). Default mode `punch`.

### 7. Built-ins — `app/src/repulse/env/builtins/viz.cljs` (new)

`pianoroll punchcard spiral scope spectrum`: `([pat])` and `([opts pat])`. Validate with
`viz-model/validate-opts`, throw `ex-info` with `:type :eval-error` and source data like
`tracks.cljs`'s `src-data`. Record in `viz-calls`, return
`(update pat :viz (fnil conj []) {:kind … :opts …})`. Register in `env/builtins.cljs`.
In `tracks.cljs` `track`: after receiving the pattern, emit the *viz dropped* warning
(decision 7) and truncate `:viz` to 3 with the *at most 3* warning. Warnings use the
existing eval-warning channel.

### 8. Inline widgets — `app/src/repulse/ui/inline_viz.cljs` (new)

`StateField` like `inline_slider.cljs`: for each top-level form that contains a call
to one of the five viz names, add one block widget after the form's last line. The
widget holds a `<canvas>` (height = sum of the `:height` opts, from the evaluated
`:viz`, defaulting to the table until first eval) and resolves its track by name: the
form's `(track :name …)` name, or `:_` for an anonymous top-level form. Placeholder
text when the track isn't running: `not playing — evaluate to start`. Register the
canvas with the RAF loop; unregister on destroy. Main app only (not `embed.cljs`). Add
it to the editor extensions in `ui/editor.cljs`.

### 9. Built-in surface (Rule 3) and docs

- Grammar `BuiltinName` += the five names → `npm run gen:grammar`.
- `completions.js` entries; `builtin_meta.edn` entries (category `visual`, `returns`
  `pattern`, `side-effects` "shows an inline visual under the track's form",
  examples from this spec, `see-also` each other) → `npm run gen:ai-docs`.
- `docs/USAGE.md`: new *Visualizers* section (the five built-ins, options table, the
  "wrap the whole pattern" rule, the per-track scope limit from decision 5), and the
  panel modes. `docs/STRUDEL-PARITY.md`: set the `pianoroll / punchcard / spiral / scope
  / spectrum / pitchwheel` row to ✅ for all but `pitchwheel`, and update the summary
  counts. `docs/ARCHITECTURE.md`: per-track meter path (taps + WASM slots).

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/ui/viz_model.cljs` (+ `_test`) | New pure model: heard cycle, window, layout, rows, option validation, scope trigger |
| `app/src/repulse/ui/viz_draw.cljs` | New shared renderer for all six views |
| `app/src/repulse/ui/timeline.cljs` (+ `_test`) | New modes, fake energy code removed, uses model + renderer |
| `app/src/repulse/ui/inline_viz.cljs` | New CodeMirror block widgets |
| `app/src/repulse/ui/editor.cljs` | Register `inline-viz` extension |
| `app/src/repulse/env/builtins/viz.cljs` | New: five viz built-ins, `viz-calls` |
| `app/src/repulse/env/builtins.cljs` | Register viz built-ins |
| `app/src/repulse/env/builtins/tracks.cljs` | Dropped-viz and max-3 warnings |
| `app/src/repulse/eval_orchestrator.cljs` | Reset `viz-calls` per top-level form |
| `app/src/repulse/audio.cljs` | `event-hz`, taps, `meter-slots`, meter slot param, `request-meters!`, `track-audio`, cleanup |
| `app/src/repulse/fx.cljs` | Reconnect taps when track FX chains change |
| `packages/audio/src/lib.rs` | Meter slots, rings, `meter_snapshot`, tests |
| `app/public/worklet.js` | `meter-req` / `meter` ping-pong |
| `app/public/css/main.css` | Panel modes, `.cm-inline-viz` block widget |
| `app/src/repulse/app.cljs` | Start `start-viz-raf!` (rename) |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar`, `parser.js` | Five names; `npm run gen:grammar` |
| `app/src/repulse/lisp-lang/completions.js` | Five entries |
| `app/src/repulse/content/builtin_meta.edn`, `docs/ai/builtins.json` | Five entries; `npm run gen:ai-docs` |
| `e2e/` | New Playwright spec for panel + widgets (see DoD) |
| `docs/USAGE.md`, `docs/STRUDEL-PARITY.md`, `docs/ARCHITECTURE.md` | As in step 9 |
| — | `npm run build:wasm` after `lib.rs` |

## Definition of done

### Reference test vectors (viz-model unit tests)

- `(seq :c3 :eb3 :g3 :bb3)` laid out → `:midi` 48 51 55 58, `:start` 0 1/4 1/2 3/4.
- `(seq :bd :sd :bd :sd)` → all `:midi nil`, drum lanes `["bd" "sd"]`.
- `(seq 440 220)` → `:midi` 69 57 (bare numbers are Hz).
- `(->> (seq :c4 :e4) (amp 0.2))` → `:alpha` 0.4 for both.
- `heard-cycle #(/ % 2) 10.05 0.05` → 5.0 (constant tempo, 2 s per cycle).
- `window 5.0 {:cycles 4 :playhead 0.5}` → `[3.0 7.0]`.

### Checklist

- [ ] `npm run test:all` passes; viz-model tests cover every vector above.
- [ ] Rust: slot isolation, no-slot and no-growth tests pass.
- [ ] `timeline.cljs` contains no `Math.sin`, `energy-curve` or wall-clock `phase`; the
      panel modes are exactly `punch roll scope spec level`.
- [ ] `(track :lead (pianoroll (seq :c3 :eb3 :g3 :bb3)))` shows an inline canvas under
      the form; `window.__REPULSE_TEST__`/Playwright reads 4 note rects on rows for MIDI
      48, 51, 55, 58.
- [ ] `(track :d (punchcard {:labels true} (seq :bd :sd :bd :sd)))` shows 4 cells in 2
      drum lanes labelled `bd` and `sd`.
- [ ] `(track :b (scope (->> (seq :c2 :g1) (synth :saw))))` (WASM voice) and
      `(track :k (scope (seq :bd :bd)))` (JS sample voice) both show a non-flat trace
      while playing (Playwright: `track-audio` RMS > 0.001), and a muted track's trace
      is flat.
- [ ] Two tracks with `scope` show different traces: the per-track RMS of a silent
      track (`(track :s (scope (seq :_)))`) stays 0 while another track plays.
- [ ] At 60 BPM vs 180 BPM, the pianoroll playhead crosses one cycle in 4 s vs 1.33 s
      (±5%), measured from canvas snapshots.
- [ ] In song mode, a resting track is drawn dimmed, and the playhead follows the song
      bar.
- [ ] `(track :x (fast 2 (pianoroll (seq :c3))))` evaluates and warns with the exact
      *viz dropped* text; no widget content appears (placeholder shown).
- [ ] `(pianoroll {:cycle 2} (seq :c3))` fails with `pianoroll: unknown option :cycle —
      did you mean :cycles?`; `(pianoroll 3)` fails with the *not a pattern* text.
- [ ] Offline export (`(export …)`) and TEST1 `evalToEvents` produce identical events
      with and without a viz wrapper.
- [ ] Grammar, completions, `builtin_meta.edn`, `builtins.json` committed together;
      CI drift checks pass. `npx shadow-cljs compile app` is clean.
- [ ] Docs updated as in step 9.
- [ ] **Watch-and-listen check (human, 🤖👂):** with headphones, run the PR's test
      protocol snippets for `pianoroll`, `punchcard` and `scope`. Every note lights or
      crosses the playhead when it is heard, with no visible lead or lag. Each scope
      moves only with its own track. Not delivered until a human ticks this.

## What NOT to do

- No full-screen background visuals (Strudel's non-underscore `.pianoroll()`), no
  `pitchwheel`, `wordfall` or `color` / `markcss`. These are follow-on phases.
- No inline widgets in the embed (`embed.cljs`) and no widgets for non-track forms
  (`def`, `defn`, `song`).
- Don't route WASM voices through per-track Web Audio nodes or give the worklet one
  output per track. Metering stays inside the engine (decision 5).
- Don't change the master `analyser-node` or the `oscilloscope` / `spectrum` plugins.
- No spectral or "sounds right" assertions in tests beyond RMS and the slot isolation
  above (CLAUDE.md, *FM preset tests*); perceptual sync is the human check's job.
- Don't add `:viz` support to core combinators; preserving it through transforms is
  explicitly out of scope (decision 7 warns instead).
