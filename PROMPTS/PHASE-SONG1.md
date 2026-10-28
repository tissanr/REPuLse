# Phase SONG1 — Sections, Song Form & Full-Song Render

> **Spec status:** full (expanded 2026-10-28 from the outline; design decisions 1–8 by
> the project owner, 2026-10-28)
>
> **Automation:** 🤖 auto — song structure is pure pattern-engine logic plus a real-time
> capture of the master output, both verifiable with unit tests, the TEST1 harness and a
> Playwright test against a real `AudioContext`.

**Milestone:** M2 — Full songs in code
**Depends on:** TL1 (patterned arguments) recommended but not required; builds on Phase 8 `arrange`.
**Unblocks:** SONG2 (`(automate …)` inside `section`), SONG3, EXP1 (uses the SONG1 capture
as its reference), SK1 (song length for stems).

## Goal

Turn REPuLse from "loops forever" into "plays a song start to finish". A song is a
buffer of track definitions (top to bottom, define before use) with **one arrangement
block at the bottom** that names sections, gives them lengths in bars, and says which
tracks play in each. A song has an end: playback stops (or optionally loops) after the
last section, rehearsal can start at any section, and the whole song can be captured to
WAV in one call. The WAV sounds exactly like playback because it is recorded from the
master output in real time.

```lisp
;; Before (Phase 8): sections are patterns, the arrangement loops forever
(def verse  (stack kick snare))
(def chorus (stack kick snare hats))
(arrange [[verse 8] [chorus 8]])

;; After
(track :kick  (seq :bd :_ :bd :_))
(track :snare (seq :_ :sd :_ :sd))
(track :hats  (fast 2 (seq :hh :_)))
(track :bass  (->> (scale :minor :c2 (seq 0 :_ 4 :_)) (synth :saw)))

(song
  (section :intro  4 [:kick :hats])
  (section :verse  8 [:kick :snare :bass])
  (section :chorus 8 :all)
  (section :outro  4 [:hats])
  :end :stop)            ; or :end :loop

;; in the command bar:
(play-from :chorus)      ; rehearse: jump to bar 12
(play-from 20)           ; or to a bar number (0-based, as shown in the context panel)
(export :song)           ; capture one pass to WAV, in real time (decision 4)
(export :song :tail 4)   ; with a 4 s release tail instead of the default 2 s
```

## Design decisions

All questions below were decided by the project owner on 2026-10-28. They are fixed for
this phase; the implementation follows them exactly.

1. **`song` is an ordinary built-in, not a special form.** `(song …)` evaluates its
   `section` arguments to plain data and returns a song value, which is registered with
   the scheduler (like `track` registers a track). It validates every referenced track
   name against the tracks registered *above it* in the buffer and reports unknown names
   with a fuzzy-match suggestion. This respects top-to-bottom evaluation: tracks first,
   song last. At most one `song` per buffer (a second one is an eval error).
   **Decided:** ordinary built-in returning a song value.

2. **`track` registers; activation happens after the whole buffer has evaluated.**
   Today `track` calls `audio/play-track!` during evaluation, so tracks would start
   before the song below them is known. Change: during evaluation, `track` only collects
   `name → pattern`; `eval_orchestrator` activates the collected tracks once evaluation
   has finished. If the buffer contains a `song`, the song decides which tracks sound in
   each bar. If it doesn't, all collected tracks play exactly as today, so live-coding
   behaviour without `song` is unchanged.
   **Decided:** register during eval, activate at end of eval; without `song`, unchanged.

3. **Sections hold track lists only (plus `:all`).** No per-section pattern overrides in
   SONG1; a variant is just another track (`(track :bass-chorus …)`). The `section` form
   must accept further trailing arguments without breaking, because SONG2 puts
   `(automate …)` inside `section`. Unknown trailing arguments are an eval error in SONG1.
   **Decided:** track lists and `:all`; `section` stays extensible for SONG2.

4. **The export must sound exactly like playback.** The current offline path can't do
   that (sine fallbacks, no FX, see Background). SONG1 therefore exports the song by
   **real-time capture**: a small recorder `AudioWorkletProcessor` tapped after the master
   FX chain (between the last global effect and the analyser) records PCM while the song
   plays once from bar 0. Capture is trimmed by audio-clock frame positions, from the
   song's scheduled start time to its end plus a release tail (default 2 s, so reverb
   and decays aren't cut), and written as 16-bit stereo WAV with the existing encoder.
   Export takes as long as the song; the context panel shows progress and the
   export can be cancelled with `(stop)`. A fast offline render with the same sound
   (WASM worklet + FX chains in an `OfflineAudioContext`) is **Phase EXP1**, scheduled
   before SK1, which needs it for stems.
   **Decided:** real-time capture in SONG1; offline parity in EXP1.

5. **1 cycle = 1 bar.** Section lengths are in bars, which equal cycles. No time
   signatures in SONG1; that would be its own later phase.
   **Decided:** keep 1 cycle = 1 bar.

### Additional decisions

6. **Re-evaluating during playback keeps the song position.** The new song form takes
   over at the current bar. If the song has become shorter than the current position,
   playback continues at the start of the last section. No jump back to bar 0.
7. **Rehearsal start is a separate built-in: `(play-from :chorus)`.** It starts (or
   relocates) playback at the first bar of the named section; unknown section names get
   a fuzzy-match suggestion. `play-from` also accepts a bar number (`(play-from 17)`).
8. **Mute always wins.** A muted track stays silent even in a section that lists it;
   `solo` likewise keeps its current meaning on top of the section selection.

## Implementation choices

Choices made in this spec for everything the decisions leave open:

- **Built-ins:** `(section name bars tracks & more)` — `name` keyword, `bars` integer ≥ 1,
  `tracks` a vector of track keywords or `:all`; any `more` is an eval error in SONG1
  (SONG2 adds `(automate …)` values there). `(song section… [:end :stop|:loop])` — sections
  first, then keyword options; only `:end` exists. `(play-from :section-name)` /
  `(play-from bar)`. `(export :song)` / `(export :song :tail secs)`.
- **Bar numbers are 0-based**, like cycles: intro bars 0–3, the panel shows `bar 12/24`,
  and `(play-from 12)` jumps to the bar the panel calls 12.
- **Pure song logic lives in a new core namespace** `repulse.song`
  (`packages/core/src/repulse/song.cljs`): `make-section`, `make-song`, `song?`,
  `song-length`, `section-start-bar`, `section-at-bar`, `active-tracks-at-bar`,
  `wrap-bar`, `song-duration-secs`, `position-after-reeval`, `song-status`,
  `song-onsets`. The scheduler and the TEST1 harness both call `song-onsets`, so the
  harness tests exactly what plays.
- **Song value** (plain data, `:type :song`): sections carry their resolved `:start`/`:end`
  bars, `:all` is resolved to the tracks registered above the song (buffer order), and
  `:tracks` lists those known tracks. Data shape in step 1.
- **Song time:** in song mode every track pattern is queried in *song time*: bar `b` of
  the song queries cycle `b` of the pattern (`:loop` queries `b mod total`). `every`,
  `cat`, `<a b>` therefore align to the song start, and every loop pass and the export
  sound the same. Without a song, tracks keep querying absolute scheduler cycles.
- **Scheduler:** `audio/scheduler-state` gains `:song` (`{:song … :origin int|nil
  :start-bar int}`; bar = scheduler cycle − origin), `:capture` and `:on-song-end`.
  A fresh song start resolves `origin` lazily on the first tick so that bar 0 begins at
  the next whole bar boundary (≤ 1 bar latency, bar 0 is never cut). At `:end :stop` the
  scheduler schedules nothing past the last bar and calls `stop!` 2 s after the song
  end (so tails ring out), then `:on-song-end`.
- **Collect/activate:** a new app namespace `repulse.song-transport`
  (`app/src/repulse/song_transport.cljs`) owns the per-evaluation pending atoms
  (`pending-tracks`, `pending-song`, `pending-play-from`, `pending-export`), the last
  activated song buffer (`song-buffer`, used by `play-from`/`export` after `(stop)`), and
  `activate-buffer!`, which `eval_orchestrator/evaluate!` calls after a successful
  evaluation. It depends only on `audio`, `fx`, `repulse.song` and `wav`, so the TEST1
  harness can use it.
- **Recorder:** `app/public/worklets/recorder-processor.js` registers
  `"repulse-recorder"`. `audio/get-ctx` loads it with `addModule` next to `worklet.js`.
  A new permanent pass-through `master-tap` GainNode is the end of the master FX chain;
  the recorder node sits in-line between `master-tap` and the analyser (pass-through,
  1 input, 1 stereo output), recording only while armed.
- **WAV encoder** is extracted unchanged from `export.cljs` into `app/src/repulse/wav.cljs`
  (`encode-wav-16`, `download-wav!`) and shared by `(export n)`/`(export :track)` and
  `(export :song)`. The song WAV uses the `AudioContext`'s sample rate (not 44100).
- **Context panel:** status row shows `▶ chorus · bar 13/24` (`song-status`), plus
  `● REC 12.0 / 50.0 s` during an export. Tracks not active in the current section are
  listed with the status `(not in :chorus)`. The timeline draws them like muted tracks.
- **Errors** follow existing conventions: `song`, `section`, `play-from` throw `ex-info`
  (eval error + red squiggle at the offending argument); `export` keeps returning
  `"Error: …"` strings as it does today. Exact texts in step 6.
- **A `track` after the `song` form is an eval error** (`define tracks above the song`),
  enforcing decision 1's top-to-bottom order.

## Background

### Pattern engine — `packages/core/src/repulse/core.cljs`

- Rationals are `[n d]` vectors: `rat`, `rat+`, `rat-`, `rat<`, `rat->float`, `int->rat`.
  Spans: `span`, `cycle-span` (`[n, n+1)`), `split-cycles` (public; splits a span at cycle
  boundaries). Patterns: `pattern` (map with `:query`), `pattern?`, `query`, `pure`,
  `silence`, `query-onsets` (only events whose `:part` starts at the `:whole` start).
  `per-cycle` and `map-spans` are **private** — the new `repulse.song` namespace must use
  `split-cycles` and update `:whole`/`:part` itself.
- `arrange*` (≈ line 418) time-shifts a `[[pattern cycles] …]` plan; sections play in
  order and the arrangement **loops** (`(mod g-cycle total)`). There is no notion of an
  end, of section names, or of which *tracks* are active. Tests: `arrange-test`,
  `arrange-multi-cycle-span` in `packages/core/src/repulse/core_test.cljs`;
  `arrange-creates-pattern` in `packages/lisp/src/repulse/lisp/integration_test.cljs`.
- Other pure core namespaces with their own test files: `theory.cljs`, `params.cljs`,
  `envelope.cljs` (each `*_test.cljs` is required and listed in
  `packages/core/src/repulse/test_runner.cljs`, which also runs app tests such as
  `repulse.fx-test`, `repulse.session-test`, `repulse.ui.timeline-test`).

### Lisp — `packages/lisp/src/repulse/lisp/`

- `builtins/arrangement.cljs` — `make-builtins` returns `"arrange"` and `"play-scenes"`,
  both calling `core/arrange*` with `u/unwrap`ped args. Merged into the env by
  `eval.cljs` `make-env`.
- `util.cljs` — `u/unwrap` (strip a SourcedVal), `u/deep-unwrap`, `u/source-of`
  (`{:from :to}` of a literal argument).
- `eval.cljs` — `levenshtein`, `typo-hint [name known]` (best match within distance 3,
  `known` compared via `str`). Used for "did you mean" hints.
- `core.cljs` — `eval-string` turns a thrown `ex-info` whose data has `:from`/`:to` into
  an `EvalError` with that source range (red squiggle in the editor).

### App — tracks, evaluation, environment

- `app/src/repulse/env/builtins/tracks.cljs` — `"track"` unwraps name and pattern,
  throws a duplicate-name `ex-info` when `@seen-tracks` already contains the name, then
  **immediately** calls `audio/play-track!`, `fx/apply-track-effects!` and
  `set-playing! true`, returning `"=> track :kick playing"`. Also `mute!`, `unmute!`,
  `solo!`, `clear!` (0/1-arity), `tracks`, `upd` (re-evaluates the editor buffer through
  `lisp/eval-string`, nested inside the outer `evaluate!`), `tap!`.
- `app/src/repulse/env/builtins.cljs` — owns `env-atom`, `builtin-names`, `seen-tracks`
  (reset per evaluation, used for duplicate detection and the post-eval sound reports),
  `evaluate-ref`; `ensure-env!` merges `leval/make-env` with every
  `env/builtins/*.cljs` `make-builtins` (ctx: `:on-beat :set-playing! :set-output!
  :make-stop-fn :share! :env-atom :evaluate-ref :seen-tracks`).
- `app/src/repulse/eval_orchestrator.cljs` — `evaluate!`: bumps `eval-generation`,
  `fx/reset-global-effects!`, `(reset! builtins/seen-tracks #{})`, `lisp/eval-string`;
  on error sets diagnostics; on success a `cond` on the last value: pattern → `audio/stop!`
  + play on the anonymous `:_` track; note/bank/synth map → same with `core/pure`; `nil`
  → nothing; string → output; else `pr-str`. Afterwards: legacy `:_` hot-swap, restoring
  `pending-mutes`, `report-structureless-signals!`, `report-unknown-sounds!` (both read
  `(:tracks @audio/scheduler-state)` for the names in `seen-tracks`).
  `evaluate-in-context!` (AI `eval_preview`, "no audio") evaluates with the same env — so
  today a `track` in an AI preview really starts playback.
- `evaluate!` is also used by the command bar (`editor/make-cmd-editor` in `app.cljs`),
  the tap button (`(tap!)`), `(upd)`, demos/gists via `builtins/evaluate-ref`, and the
  embeddable component (`app/src/repulse/embed.cljs`).
- `app/src/repulse/app.cljs` `make-stop-fn` → `snippet-preview/stop!`, `audio/stop!`,
  `editor/clear-highlights!`, `set-playing! false`, output `"stopped"`. Watches on
  `audio/scheduler-state` call `ctx-panel/schedule-render!` and
  `timeline/render-track-panel!` (so the panel repaints on every bar, because `tick!`
  increments `:cycle`).
- `app/src/repulse/snippets/sandbox.cljs` `preview-env` overrides `"track"` (plays on a
  reserved preview track at once) and stubs `stop`, `clear!`, `mute!`, … for snippet
  previews.

### App — audio — `app/src/repulse/audio.cljs`

- Master chain (`build-master-chain!`): `master-gain → analyser-node → destination`.
  `fx/rewire!` (`app/src/repulse/fx.cljs`) rebuilds `master-gain → effect1 → … → analyser`
  on every `add-effect!`/`remove-effect!`; it references `@audio/analyser-node` directly.
  Other analyser users (`app.cljs` `make-host`, `snippets/preview.cljs`) only read from it.
- `init-worklet!` loads `worklet.js` with `(.addModule (.-audioWorklet ac) (public-url "worklet.js"))`,
  creates `AudioWorkletNode "repulse-processor"` (2 output channels) connected to
  `master-gain`, then posts the WASM bytes; `worklet-ready?` turns true on `"ready"`.
  Existing extra processors live in `app/public/worklets/` (`bitcrusher-processor.js`,
  `dattorro-reverb-processor.js`).
- `output-for-track` routes a live voice into its per-track node (`track-nodes`, per-track
  FX chain) or `master-gain`; for an `OfflineAudioContext` it routes straight to the
  destination — **no per-track and no global FX offline**.
- `play-event` (`offline?` branch): offline, every WASM voice is skipped — FM presets
  (`:sax :trumpet … :epiano :bell :marimba :flute`), plucked strings (`:guitar :harp …`),
  `:fm` and plain note keywords/numbers render as `make-sine`; `:saw`/`:square` fall back
  to native oscillators. Live, `worklet-trigger-v2!` is used when the worklet is ready
  and `dest` is `master-gain`.
- `scheduler-state` atom: `:playing? :tracks :muted :cycle :cycle-dur (2.0 = 120 BPM,
  1 cycle = 1 bar = 4 beats) :lookahead 0.2 :interval-id :on-beat :on-event :on-fx-event
  :tween-state`. `set-bpm!`/`get-bpm` (`cycle-dur = 240 / bpm`, clamped 20–640).
- `schedule-cycle! [ac state cycle]` queries `core/query-onsets` of every non-muted track
  over `[cycle, cycle+1)` and, per onset with audio time `t = cycle·cd + (whole-start −
  cycle)·cd` later than `currentTime`: tween interpolation, `play-event`, MIDI out,
  `on-fx-event`, editor highlight (`on-event`, via `schedule-visual!`), `on-beat`.
- `tick!` (every 25 ms) schedules `:cycle` once it starts within `lookahead`, then
  increments `:cycle` — so `:cycle` is always the **next unscheduled bar**.
  `ensure-running!` starts at `floor(currentTime / cycle-dur)`, i.e. the absolute bar
  grid of the `AudioContext` clock; the first, partly elapsed bar loses its past events.
- `play-track!` (node + tween arming + `:tracks` assoc, then `.resume` →
  `ensure-running!`), `mute-track!`, `unmute-track!`, `solo-track!` (mutes every other
  track in `:tracks`), `clear-track!` (stops all when the last track goes), `stop!`
  (worklet `"stop"`, clear interval, visuals, per-track nodes, buses; resets `:tracks`,
  `:muted`, `:tween-state`).

### App — export — `app/src/repulse/env/builtins/export.cljs`

`(export)`, `(export n)`, `(export :track)` render `n` cycles (default 4) of the current
`:tracks` through `audio/play-event` into a 44.1 kHz `OfflineAudioContext` and download a
16-bit stereo WAV (RIFF header + interleaved samples written inline in the `.then`,
filename `repulse-<all|track>-<n>cycles.wav`). It calls `play-event` without a track name,
so it never gets per-track FX; offline it also gets the sine fallbacks above. That is
why SONG1 captures in real time (decision 4); EXP1 fixes the offline path. The `export`
builtin returns `"Error: …"` strings rather than throwing.

### App — UI

- `app/src/repulse/ui/context_panel.cljs` — `render-status-section` (BPM, backend
  `[wasm]`/`[js]`, bank prefix, playing/stopped), `render-tracks-section` (per-track rows
  with muted/solo status), `render-context-panel!`, `schedule-render!` (rAF-debounced).
- `app/src/repulse/ui/timeline.cljs` — `active-track-data` builds per-track rows from
  `:tracks`/`:muted`/`:cycle` via `track-events`; no song view.

### Editor, AI docs (CLAUDE.md Rule 3)

- `app/src/repulse/lisp-lang/repulse-lisp.grammar` `BuiltinName` (contains `"arrange"`,
  `"play-scenes"`, `"export"`; not `song`, `section`, `play-from` — checked with grep);
  `npm run gen:grammar` regenerates `parser.js`.
- `app/src/repulse/lisp-lang/completions.js` (`{ label, type, detail }`; `export` entry
  at ≈ line 148), `app/src/repulse/lisp-lang/hover.js` (`DOCS` map: `signature`,
  `description`, `example`; `arrange` at ≈ line 1004; no `export` entry yet).
- `app/src/repulse/content/builtin_meta.edn` (`:arrange` category `"source"`, `:track`
  category `"audio"`, `:export` category `"io"`); `npm run gen:ai-docs` regenerates
  `docs/ai/builtins.json` and fails when a completion label has no meta entry.

### Tests — TEST1 harness

- `shadow-cljs.edn` `:test-harness` build (`repulse.test-api/init!`, output
  `app/public/test-js`), page `app/public/test-harness.html`.
- `app/src/repulse/test_api.cljs` sets `window.__REPULSE_TEST__` = `{evalToEvents,
  renderOffline, reset, backend}`; its env is `leval/make-env` only (no app builtins).
  `reset-test-state!` resets `audio/scheduler-state` to a literal map.
- `e2e/audio-render.spec.ts` (+ `e2e/helpers/audio-analysis.ts`: `rms`, `rmsStereo`,
  `mixMono`, `countOnsets`, `tailRms`, `assertFinitePcm`). `playwright.config.ts` serves
  `app/public` with `scripts/serve-static.mjs` (serves `.wasm` as `application/wasm`) and
  launches Chromium with `--autoplay-policy=no-user-gesture-required`.
- **CI** (`.github/workflows/ci.yml`) job `browser-audio` runs `npm run test:e2e`
  **without** building WASM (`app/public/repulse_audio_bg.wasm` and `repulse_audio.js` are
  gitignored build output). Only `release-build` installs Rust + wasm-pack and runs
  `npm run build:wasm`.

## Implementation

Work in this order; each step ends with `npm run test` green.

### 0. Verify tooling (CLAUDE.md Rule 4)

`npm run gen:grammar && npm run gen:ai-docs && npm run test` on the unmodified branch.
For the capture tests later: `npm run build:wasm` once (the e2e capture test needs the
real WASM worklet).

### 1. Pure song logic — `packages/core/src/repulse/song.cljs` (new)

```clojure
(ns repulse.song
  "Song form: named sections with bar lengths and per-section track lists.
   Pure — no audio, no DOM, no Lisp. 1 bar = 1 cycle; bars are 0-based integers."
  (:require [repulse.core :as core]))
```

**Data shapes.**

```clojure
;; section (from make-section / the `section` builtin)
{:type :section :name :verse :bars 8 :tracks [:kick :snare :bass] :source {:from 120 :to 141}}
{:type :section :name :chorus :bars 8 :tracks :all}

;; song (from make-song)
{:type     :song
 :end      :stop                         ; or :loop
 :total    24                            ; bars
 :tracks   [:kick :snare :hats :bass]    ; tracks registered above the song, buffer order
 :sections [{:type :section :name :intro  :bars 4 :tracks [:kick :hats]             :start 0  :end 4}
            {:type :section :name :verse  :bars 8 :tracks [:kick :snare :bass]      :start 4  :end 12}
            {:type :section :name :chorus :bars 8 :tracks [:kick :snare :hats :bass] :all? true :start 12 :end 20}
            {:type :section :name :outro  :bars 4 :tracks [:hats]                   :start 20 :end 24}]}
```

Errors are `(ex-info msg {:type :song-error …})`; the app attaches source ranges.

| Function | Signature | Semantics |
|---|---|---|
| `make-section` | `[name bars tracks]` | Validates: `name` keyword; `bars` integer ≥ 1 (a rational `[n d]`, float or 0 is an error); `tracks` = `:all` or a vector of keywords (duplicates removed, order kept). Returns the section map (without `:start`/`:end`). |
| `song?` / `section?` | `[x]` | `(= :song (:type x))` / `(= :section (:type x))` on maps. |
| `make-song` | `[sections opts known-tracks hint-fn]` | `sections` non-empty vector of section maps; `opts` `{:end :stop\|:loop}` (default `:stop`); `known-tracks` vector of keywords (buffer order); `hint-fn` `(fn [name-str known-strs] → str or nil)`. Rejects duplicate section names and unknown track names (message uses `hint-fn`; `ex-data` carries `:section`, `:track`). Resolves `:all` → `known-tracks` (`:all? true`), adds `:start`/`:end`, `:total`, `:tracks`. |
| `song-length` | `[song]` | `(:total song)` — total bars. |
| `section-start-bar` | `[song section-name]` | `:start` of that section, or `nil`. |
| `section-at-bar` | `[song bar]` | Section map with `start ≤ bar < end`, or `nil` (bar < 0 or ≥ total). No wrapping. |
| `active-tracks-at-bar` | `[song bar]` | Set of track keywords of `(section-at-bar song bar)`; `#{}` outside the song. |
| `wrap-bar` | `[song bar]` | Song bar to query for scheduler bar `bar` (relative to origin): `nil` for `bar < 0`; `:stop` → `bar` if `< total` else `nil`; `:loop` → `(mod bar total)`. |
| `song-duration-secs` | `[song bpm]` | `(* (:total song) (/ 240 bpm))` — 4 beats per bar; caller passes an already clamped BPM. |
| `position-after-reeval` | `[song bar]` | Decision 6. `bar < 0` → `bar` (pre-roll, unchanged); `bar < total` → `bar`; else `:start` of the last section. |
| `song-status` | `[song bar]` | For display: `{:bar b :total t :section :chorus :section-bar i :section-bars n}` using `(wrap-bar song bar)`; `nil` when that is `nil`. |
| `song-onsets` | `[song tracks muted bar]` | The onsets to schedule for scheduler bar `bar`. `tracks` map keyword → Pattern, `muted` set. `q = (wrap-bar song bar)`; `nil` → `[]`. Otherwise for each track in `(:tracks song)` order that is in `(active-tracks-at-bar song q)`, **not in `muted`** (decision 8) and present in `tracks`: `(core/query-onsets pat (core/cycle-span q))`, each event's `:whole` and `:part` shifted by `(- bar q)` bars (keep `:source` and all other keys). Returns a vector of `{:track kw :event ev}`. Rest values (`:_`) are kept, exactly as the scheduler gets them today. |

### 2. `section` built-in — `packages/lisp/src/repulse/lisp/builtins/arrangement.cljs`

Add `[repulse.song :as song]` to the requires and to `make-builtins`:

```clojure
"section" (fn [name-arg bars-arg tracks-arg & more]
            (let [nm (u/unwrap name-arg)]
              (when (seq more)
                (let [extra (first more) src (u/source-of extra)]
                  (throw (ex-info (str "section " (pr-str nm) ": unexpected argument "
                                       (pr-str (u/deep-unwrap extra))
                                       " — sections take (section name bars tracks)")
                                  (cond-> {:type :eval-error} src (merge src))))))
              (-> (song/make-section nm (u/unwrap bars-arg) (u/deep-unwrap tracks-arg))
                  (assoc :source (u/source-of tracks-arg)))))
```

Keep the `& more` arity: SONG2 dispatches `(automate …)` values there instead of
throwing. Re-throw `make-section` errors with the source of the offending argument.
`arrange` and `play-scenes` are not touched.

### 3. Collect/activate — `app/src/repulse/song_transport.cljs` (new)

Requires `repulse.audio`, `repulse.fx`, `repulse.song`, `repulse.wav`,
`repulse.lisp.eval` (for `typo-hint`). Owns:

```clojure
(defonce pending-tracks    (atom []))   ; [[kw pattern] …] in buffer order
(defonce pending-song      (atom nil))
(defonce pending-play-from (atom nil))  ; integer bar
(defonce pending-export    (atom nil))  ; {:tail secs :set-output! f}
(defonce song-buffer       (atom nil))  ; last activated {:tracks [[kw pat] …] :song song}
```

- `reset-pending!` — empties the four pending atoms.
- `collect-track! [name' pat' src]` — the duplicate check moved from `tracks.cljs`
  (same message), plus: if `@pending-song` is set, throw `"track :x is defined after
  (song …) — define tracks above the song"`. Appends to `pending-tracks`.
- `register-song! [sections opts src]` — throws `"Only one (song …) per buffer"` if
  `@pending-song`; builds `(song/make-song sections opts (mapv first @pending-tracks)
  hint-fn)` with `hint-fn` wrapping `leval/typo-hint` on name strings; on a
  `:song-error` re-throws with the failing section's `:source` (or `src`).
  Stores into `pending-song`, returns the song value.
- `activate-buffer! [{:keys [on-beat on-event set-playing!]}]` — called after every
  successful evaluation:
  1. If `pending-tracks` is non-empty: for each `[nm pat]` in order, `(audio/play-track!
     nm pat on-beat on-event)` and `(fx/apply-track-effects! nm (:track-fx pat))` — before
     that, `(audio/set-song! @pending-song …)` (see step 4) so the first tick sees it.
     If `pending-song` is `nil`, call `(audio/clear-song!)`: tracks play exactly as today
     (decision 2). `(set-playing! true)`. Set `song-buffer` to `{:tracks … :song …}`
     when there is a song, else `nil`.
  2. If `pending-tracks` is empty and there is no `pending-song` (e.g. a command-bar eval
     like `(bpm 140)` or `(play-from :chorus)`): leave tracks and song untouched.
  3. Apply `pending-play-from` (step 5), then `pending-export` (step 7).
- `replay-song-buffer! [start-bar opts]` — `(audio/stop!)` if needed, then re-activates
  `@song-buffer` from `start-bar` (used by `play-from` when stopped and by export).

### 4. Scheduler song mode — `app/src/repulse/audio.cljs`

Add `[repulse.song :as song]`. New `scheduler-state` keys (also in
`test_api.cljs` `reset-test-state!`): `:song nil`, `:capture nil`, `:on-song-end nil`.

```clojure
;; :song {:song <song value> :origin int-or-nil :start-bar int :end-handled? bool}
(def song-release-secs 2.0)
```

- `set-song! [song-value {:keys [start-bar]}]`:
  - not playing, or no song active → `{:song s :origin nil :start-bar (or start-bar 0)}`
    (fresh start; origin resolved on the first tick);
  - playing with a song → keep position: `cur = (:cycle state) − origin` (the next
    unscheduled bar), wrapped with the *old* song if it was `:loop`;
    `pos = (or start-bar (song/position-after-reeval new-song cur))`;
    `origin = (:cycle state) − pos`. The new form takes over at the next bar boundary.
  - playing without a song (tracks were looping) → `origin = (:cycle state) − (or start-bar 0)`.
- `clear-song! []` — `(swap! scheduler-state assoc :song nil)`.
- `relocate-song! [bar]` — playing with a song: `origin = (:cycle state) − bar`.
- `tick!` — before the schedule check, resolve a `nil` origin:
  `first = (if (< (* cycle cd) now) (inc cycle) cycle)`, `origin = first − start-bar`.
  Then the end check for a resolved `:stop` song: `end-t = (origin + total)·cd`; when
  `now ≥ end-t + song-release-secs` and no capture is active, call `(stop!)` and then
  `(:on-song-end state)` (captured before `stop!`). With an active capture the capture's
  completion handler stops instead (step 7).
- `schedule-cycle!` — factor the per-event body (the `(when (> t currentTime) …)` block:
  tweens, `play-event`, MIDI out, `on-fx-event`, `on-event`, `on-beat`) into
  `(schedule-event! ac state snap track-name ev t dur)`; the caller computes `t` and `dur`.
  - Without `:song`: unchanged — every non-muted track, `t = cycle·cd + (whole-start − cycle)·cd`.
  - With `:song` and a resolved origin: `bar = cycle − origin`;
    `(doseq [{:keys [track event]} (song/song-onsets s tracks muted bar)] …)`.
    `song-onsets` already places events at song bar `bar` (unwrapped), so
    `t = (origin + whole-start)·cd` and `dur = (whole-end − whole-start)·cd`.
    `song-onsets` returns `[]` for `bar < 0` (pre-roll) and past the end of a `:stop` song.
- `stop!` — also `assoc :song nil`, and cancel an active capture (step 7).
- Mute/solo need no change: `song-onsets` receives `(:muted state)` (decision 8).

### 5. `song` and `play-from` built-ins — `app/src/repulse/env/builtins/song.cljs` (new)

`make-builtins [ctx]`, merged in `env/builtins.cljs` `ensure-env!`:

- `"song" (fn [& args] …)` — split args: leading values that are `song/section?`
  (after `leval/unwrap`), then keyword/value option pairs. Errors: no sections →
  `"song needs at least one (section …)"`; a non-section before the options →
  `"song: expected (section …) or an option, got …"`; unknown option →
  `"song: unknown option :foo — expected :end"`; bad `:end` →
  `"song: :end must be :stop or :loop, got :fade"`. Calls
  `song-transport/register-song!`; returns the song value.
- `"play-from" (fn [target] …)` — song = `(or @pending-song (:song @song-buffer))`; none →
  `"play-from: no song — evaluate a buffer that ends with (song …) first"`. Keyword →
  `section-start-bar`, unknown → `"Unknown section :chrous — did you mean :chorus?"`
  (or `"Unknown section :x — sections: :intro :verse :chorus :outro"` without a hint).
  Integer → must be `0 ≤ n < total`, else `"play-from: bar 30 is outside the song (0–23)"`;
  non-integer → `"play-from: expected a section name or a bar number"`. Stores
  `pending-play-from`; returns `"=> playing from :chorus (bar 12)"` / `"=> playing from bar 17"`.
- Applying `pending-play-from` in `activate-buffer!`: a song activated in this same
  evaluation → passed as `:start-bar` to `audio/set-song!`; otherwise playing with a song
  → `audio/relocate-song!`; otherwise (stopped) → `replay-song-buffer!` from that bar.

`env/builtins/tracks.cljs` `"track"`: keep the unwrap/`seen-tracks` logic, replace the
immediate `play-track!`/`apply-track-effects!`/`set-playing!` with
`(song-transport/collect-track! name' pat' src)`; keep the return strings unchanged
(`"=> track :kick playing"`, `"Error: second argument to track must be a pattern"`).
`"clear!"` (0-arity) also resets `song-transport/song-buffer`.

### 6. Orchestrator — `app/src/repulse/eval_orchestrator.cljs`

- `evaluate!`: next to `(reset! builtins/seen-tracks #{})` call
  `(song-transport/reset-pending!)`. On success, call
  `(song-transport/activate-buffer! {:on-beat on-beat :on-event editor/highlight-range!
  :set-playing! set-playing!})` **before** the `cond` on the last value, so a buffer that
  ends in a bare pattern still stops the tracks and plays `:_` exactly as today. On error
  nothing is activated (the buffer applies all-or-nothing).
- Add a `cond` branch `(song/song? val)` → `set-output!`
  `"song: 4 sections · 24 bars · 48.0 s at 120 BPM · :end :stop"` (`:success`).
- Set `:on-song-end` in `scheduler-state` (in `init!`) to a fn that calls
  `editor/clear-highlights!`, `(set-playing! false)` and `set-output! "song finished" :idle`.
- `evaluate-in-context!`: `reset-pending!` before and after the silent eval and never
  activate — the AI preview stays silent even with `track` in the code.
- `upd` needs no change: it runs inside an outer `evaluate!`, whose activation picks up the
  tracks and song collected by the nested evaluation.
- `snippets/sandbox.cljs` `preview-env`: add stubs `"song" (fn [& _] "preview: song form
  ignored — tracks loop")`, `"play-from" (fn [& _] nil)`, so snippet previews behave as before.

### 7. Recorder and `(export :song)`

**`app/public/worklets/recorder-processor.js`** (new, plain JS, no imports):

```js
class RecorderProcessor extends AudioWorkletProcessor {
  constructor() {
    super();
    this.armed = false;
    this.port.onmessage = (e) => {
      const d = e.data;
      if (d.type === 'start') { /* startFrame, endFrame, chunkFrames; allocate chunk buffers */ this.armed = true; }
      else if (d.type === 'cancel') { this.armed = false; this.port.postMessage({ type: 'cancelled' }); }
    };
  }
  process(inputs, outputs) {
    const inp = inputs[0], out = outputs[0];
    for (let c = 0; c < out.length; c++) {          // pass-through
      const src = inp[c] || inp[0];
      if (src) out[c].set(src); else out[c].fill(0);
    }
    if (this.armed) {
      // frame of sample i is currentFrame + i; copy frames in [startFrame, endFrame)
      // into the chunk (missing input channels → 0); post {type:'chunk', offset, left, right}
      // with transferred Float32Arrays every chunkFrames; at endFrame post the final chunk
      // and {type:'done', frames, late} (late = first processed frame > startFrame; the
      // gap is zero-filled) and disarm.
    }
    return true;
  }
}
registerProcessor('repulse-recorder', RecorderProcessor);
```

**`audio.cljs`:**

- `(defonce master-tap (atom nil))` — a GainNode (gain 1) created in
  `build-master-chain!`: `master-gain → master-tap → analyser → destination`.
- `fx.cljs` `rewire!`: connect the chain end (or `master-gain` when empty) to
  `@audio/master-tap` instead of `@audio/analyser-node`.
- `(defonce recorder-node (atom nil))`, `init-recorder!` called from `get-ctx` after
  `init-worklet!`: `addModule (public-url "worklets/recorder-processor.js")`, then
  `(js/AudioWorkletNode. ac "repulse-recorder" #js {:numberOfInputs 1 :numberOfOutputs 1
  :outputChannelCount #js [2] :channelCount 2 :channelCountMode "explicit"})`,
  `(.disconnect tap)`, `tap → recorder → analyser`. On failure keep `tap → analyser` and
  leave `recorder-node` nil (console warning).
- `capture! [{:keys [tail on-progress]}]` → Promise. Requires a `:song` in
  `scheduler-state` with `origin nil` (fresh start). Stores `:capture {:tail t :status
  :waiting}`; when `tick!` resolves the origin it computes `t0 = origin·cd`,
  `t1 = t0 + (song-duration-secs song bpm) + tail`, posts `{type:"start", startFrame
  (Math/round (* t0 sr)), endFrame (Math/round (* t1 sr)), chunkFrames sr}` and sets
  `:capture {:start-time t0 :end-time t1 …}` (the panel reads it). Chunks are copied into
  two preallocated `Float32Array`s of `endFrame − startFrame` frames. On `"done"` resolve
  `{:left :right :sample-rate :frames :late}`, clear `:capture`, then `(stop!)` and
  `:on-song-end`. `stop!` with an active capture posts `"cancel"` and rejects with
  `(ex-info "export cancelled" {:cancelled true})`.
- The song end-check in `tick!` uses `max(song-release-secs, tail)` while capturing and
  leaves the stop to the capture's `"done"` handler.

**`app/src/repulse/wav.cljs`** (new): move the RIFF/PCM code from `export.cljs` verbatim
into `(encode-wav-16 [left right sample-rate] → js/ArrayBuffer)` and the blob/anchor code
into `(download-wav! [array-buffer filename])`. `export.cljs` calls both; its output for
`(export n)` / `(export :track)` must stay byte-identical (same 44100 Hz, same filenames).

**`export.cljs`:** before the existing logic, `(= arg :song)` → song branch (a track named
`:song` can therefore no longer be exported by name; documented). Parse `:tail secs`
(number 0–30, default 2; else `"Error: :tail must be a number of seconds between 0 and 30"`).
Errors (strings, like today): no song (`(or @pending-song (:song @song-buffer))` is nil) →
`"Error: no song — evaluate a buffer that ends with (song …) first"`; recorder missing →
`"Error: (export :song) needs AudioWorklet support for real-time capture"`; capture
running → `"Error: an export is already running — (stop) cancels it"`. Otherwise store
`pending-export {:tail t :set-output! (:set-output! ctx)}` and return
`"exporting song — 24 bars, 50.0 s in real time… (stop) cancels"`.

**`song-transport/start-export!`** (called by `activate-buffer!` for `pending-export`):
`(replay-song-buffer! 0 {:end :stop})` — a `:loop` song is captured as one pass by
activating a copy with `:end :stop` — then `(audio/capture! {:tail t})`; on resolve
`(wav/encode-wav-16 left right sr)` → `(wav/download-wav! buf (str "repulse-song-" total
"bars.wav"))` and `set-output! "exported song — 24 bars, 50.0 s"`; on cancel
`set-output! "export cancelled"`. `capture-song!` (same flow, resolving with the PCM
instead of downloading) is exported for the TEST1 harness.

### 8. Context panel and timeline

- `context_panel.cljs` `render-status-section`: when `(:song @audio/scheduler-state)` has
  a resolved origin, render `<span class="ctx-song">▶ chorus · bar 13/24</span>` from
  `(song/song-status s (- (:cycle state) 1 origin))` (the bar most recently scheduled ≈
  the one sounding). During pre-roll show `▶ song · starting`. With `:capture` set, add
  `<span class="ctx-rec">● REC 12.0 / 50.0 s</span>` from `currentTime − start-time`.
  CSS for `.ctx-song`/`.ctx-rec` in `app/public/css/` next to `.ctx-bpm`.
- `render-tracks-section`: in song mode, tracks not in `active-tracks-at-bar` get
  `(not in :chorus)` as status (muted/solo status takes precedence).
- `timeline.cljs` `active-track-data`: pass `muted? = (or muted (not active in section))`
  in song mode.

### 9. TEST1 harness — `app/src/repulse/test_api.cljs`

Add to `window.__REPULSE_TEST__`:

- `songEvents(code, fromBar, toBar, opts)` — evaluates `code` in `leval/make-env` plus
  harness `"track"` (`song-transport/collect-track!`), the app `"song"` and `"play-from"`
  builtins; returns the `song-onsets` of bars `fromBar … toBar−1` with `muted` from
  `opts.muted` (array of `":kick"` strings) as JS objects `{track, value, wholeStart,
  wholeEnd}` (same `safe-value` as `evalToEvents`), **dropping `:_` rests**, sorted by
  `wholeStart` then `track`. Pure — no audio.
- `audioReady()` → Promise resolving `"audioworklet+wasm"` once `audio/worklet-ready?` and
  the recorder are up (or `"js"` after 10 s).
- `captureSong(code, opts)` → Promise `{sampleRate, length, left, right, backend,
  expectedFrames, late}`: evaluates like `songEvents`, `activate-buffer!`s with a real
  `AudioContext`, runs `song-transport/capture-song!` with `opts.tail`. Its env is built
  with `(leval/make-env stop-fn audio/set-bpm!)` so `(bpm 480)` takes effect (the existing
  `make-test-env` passes a no-op BPM fn and stays as is for `evalToEvents`). `expectedFrames
  = round((song-duration-secs + tail) · sampleRate)`.
- `reset-test-state!` gains the new `scheduler-state` keys and `song-transport/reset-pending!`.

### 10. Tests

- `packages/core/src/repulse/song_test.cljs` (new, add to `test_runner.cljs` require + `run-tests`): DoD vectors.
- `packages/lisp/src/repulse/lisp/integration_test.cljs`: `section` values and errors;
  existing `arrange-creates-pattern` unchanged.
- `app/src/repulse/song_transport_test.cljs` (new, add to the runner): env =
  `leval/make-env` + test `"track"` → `collect-track!` + `env/builtins/song` builtins;
  error texts for unknown tracks, second song, track after song, `play-from` errors.
- `app/src/repulse/wav_test.cljs` (new, add to the runner): `encode-wav-16` header bytes and
  one sample.
- `e2e/song.spec.ts` (new): `songEvents` vectors; capture frame count and harmonics.
  `e2e/helpers/audio-analysis.ts`: add `goertzel(buffer, sampleRate, hz)` (magnitude of
  one frequency bin).
- `.github/workflows/ci.yml` job `browser-audio`: add the Rust toolchain, wasm-pack
  (`cargo install --locked wasm-pack@0.14.0`), Cargo cache and `npm run build:wasm` steps
  (copied from `release-build`) before `npm run test:e2e`, so the capture test runs
  against the real WASM worklet. Headless Chromium uses a fake audio sink, so a real
  `AudioContext` advances; if it does not in CI, stop and report it — do **not** fall back
  to an `OfflineAudioContext` for these tests.

### 11. Built-ins, docs (CLAUDE.md Rules 2 and 3)

- Grammar `BuiltinName`: `"song" | "section" | "play-from"` → `npm run gen:grammar`.
- `completions.js`: `song`, `section`, `play-from`; update `export` detail
  (`… (export :song) — capture the whole song in real time`).
- `hover.js`: entries for the three names, plus a new `export` entry (none exists today) covering `(export n)`, `(export :track)`, `(export :song)`.
- `builtin_meta.edn`: `:song` (category `"source"`, returns `"Song"`, side-effects
  `["audio"]`), `:section` (`"source"`, `"Section"`, `[]`), `:play-from` (`"audio"`,
  `"nil"`, `["audio"]`), each with ≥ 1 example and `see-also` (`song`/`section`/`track`/
  `play-from`/`export`/`arrange`); `:export` gains `"(export :song)"`. Then
  `npm run gen:ai-docs`.
- `docs/USAGE.md`: new section **"Writing a song"** after "Song arrangement" — the full
  example from the Goal, section/`:all`/`:end`, bar numbering, `play-from`, re-evaluating
  mid-song, mute/solo, `(export :song)` (real time, `:tail`, `(stop)` cancels, `:song` is
  reserved in `export`), how it differs from `arrange`; reference-table rows for `song`,
  `section`, `play-from`, and the `export` row update. Note in "Named tracks" that tracks
  start once the whole buffer has evaluated.
- `README.md`: add `song`/`section`/`play-from` to the built-in table and a 6-line song
  example.
- `ROADMAP.md`: SONG1 delivered + **Delivered:** notes; Implementation-order row.

### 12. PR test protocol (CLAUDE.md Rule 5)

The PR ends with a `## Test protocol`. It must contain at least: the Goal song (hear intro
kick+hats 4 bars → verse with snare and saw bass → chorus all four → outro hats only →
stop, output `song finished`); `:end :loop` (bar 24 sounds like bar 0); `(play-from
:chorus)` and `(play-from 20)` from the command bar while playing and after `(stop)`;
re-evaluating mid-chorus with the outro deleted (keeps playing from the same bar);
shortening the song below the current bar (jumps to the last section's start); `(mute!
:kick)` during the chorus; `(export :song)` with a downloaded WAV that contains the FX
(e.g. `(fx :reverb 0.5)`) and an FM voice, and `(stop)` during an export (`export
cancelled`); every error text from steps 2, 5 and 7; regressions: a buffer of tracks
without `song`, `(arrange …)`, `(export 4)`, `(export :kick)`, snippet preview, and the
baseline items from `.claude/commands/commit-push-pr.md`. Values come from the harness
(`songEvents`), not from memory.

## Files to change

| File | Change |
|---|---|
| `packages/core/src/repulse/song.cljs` (new) | Song data model and pure functions (step 1) |
| `packages/core/src/repulse/song_test.cljs` (new) | DoD vectors |
| `packages/core/src/repulse/test_runner.cljs` | Require + run `repulse.song-test`, `repulse.song-transport-test`, `repulse.wav-test` |
| `packages/lisp/src/repulse/lisp/builtins/arrangement.cljs` | `section` built-in |
| `packages/lisp/src/repulse/lisp/integration_test.cljs` | `section` tests |
| `app/src/repulse/song_transport.cljs` (new) | Pending atoms, `collect-track!`, `register-song!`, `activate-buffer!`, `replay-song-buffer!`, export flow |
| `app/src/repulse/song_transport_test.cljs` (new) | Error texts, collect/validate behaviour |
| `app/src/repulse/env/builtins/song.cljs` (new) | `song`, `play-from` built-ins |
| `app/src/repulse/env/builtins.cljs` | Merge `b-song/make-builtins` |
| `app/src/repulse/env/builtins/tracks.cljs` | `track` collects instead of playing; `clear!` resets `song-buffer` |
| `app/src/repulse/env/builtins/export.cljs` | `(export :song [:tail s])`; use `repulse.wav` |
| `app/src/repulse/wav.cljs` (new) + `wav_test.cljs` (new) | Extracted WAV encoder + download |
| `app/src/repulse/eval_orchestrator.cljs` | Reset pending, `activate-buffer!`, song output branch, `:on-song-end`, silent `evaluate-in-context!` |
| `app/src/repulse/audio.cljs` | `:song`/`:capture`/`:on-song-end` state, `set-song!`, `clear-song!`, `relocate-song!`, song branch in `schedule-cycle!`/`tick!`, `schedule-event!`, `master-tap`, `init-recorder!`, `capture!`, `stop!` changes |
| `app/src/repulse/fx.cljs` | `rewire!` ends at `master-tap` |
| `app/public/worklets/recorder-processor.js` (new) | `"repulse-recorder"` pass-through recorder |
| `app/src/repulse/snippets/sandbox.cljs` | `song`/`play-from` stubs in `preview-env` |
| `app/src/repulse/ui/context_panel.cljs`, `app/public/css/*` | Song status, REC progress, `(not in :x)` track status |
| `app/src/repulse/ui/timeline.cljs` | Inactive-in-section tracks drawn as muted |
| `app/src/repulse/test_api.cljs` | `songEvents`, `audioReady`, `captureSong`, reset keys |
| `e2e/song.spec.ts` (new), `e2e/helpers/audio-analysis.ts` | Song tests, `goertzel` |
| `.github/workflows/ci.yml` | WASM build in `browser-audio` |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` + `parser.js` (+ `parser.terms.js`) | 3 names (`npm run gen:grammar`) |
| `app/src/repulse/lisp-lang/completions.js`, `hover.js` | New entries, `export` updated |
| `app/src/repulse/content/builtin_meta.edn` + `docs/ai/builtins.json` | New entries, `:export` updated (`npm run gen:ai-docs`) |
| `docs/USAGE.md`, `README.md`, `ROADMAP.md` | Step 11 |

## Definition of done

Reference song **S** (used below), tracks registered in this order:

```lisp
(track :kick  (seq :bd :_ :bd :_))
(track :snare (seq :_ :sd :_ :sd))
(track :hats  (fast 2 (seq :hh :_)))
(track :bass  (synth :saw (seq :c2 :_ :g2 :_)))
(song (section :intro  4 [:kick :hats])
      (section :verse  8 [:kick :snare :bass])
      (section :chorus 8 :all)
      (section :outro  4 [:hats]))
```

`S'` = S with only intro (4) and verse (8), 12 bars. Mini song **M**:

```lisp
(track :kick (seq :bd :_ :bd :_))
(track :hats (fast 2 (seq :hh :_)))
(song (section :a 1 [:kick :hats]) (section :b 1 [:hats]))
```

**Pure core (`repulse.song`, `cljs.test`)**
- [ ] Song length and timing: `(song-length S)` → `24`; `(section-start-bar S :intro)` → `0`,
      `:verse` → `4`, `:chorus` → `12`, `:outro` → `20`, `:bridge` → `nil`;
      `(song-duration-secs S 120)` → `48`; `(song-duration-secs S 90)` → `64`;
      `(song-duration-secs M 480)` → `1`.
- [ ] Section/track activation for any bar: `(:name (section-at-bar S b))` for b = 0, 3, 4,
      12, 20, 23 → `:intro :intro :verse :chorus :outro :outro`; b = 24 and −1 → `nil`.
      `(active-tracks-at-bar S 3)` → `#{:kick :hats}`; bar 13 → `#{:kick :snare :hats :bass}`
      (`:all` = every track above the song); bar 21 → `#{:hats}`; bar 24 → `#{}`.
      `(:tracks (nth (:sections S) 2))` → `[:kick :snare :hats :bass]`.
- [ ] `wrap-bar`: S (`:stop`) 23 → `23`, 24 → `nil`, −1 → `nil`; S with `:end :loop`
      24 → `0`, 50 → `2`, −1 → `nil`.
- [ ] Re-evaluation position (decision 6): `(position-after-reeval S 13)` → `13`;
      `(position-after-reeval S' 13)` → `4`; `(position-after-reeval S' 12)` → `4`;
      `(position-after-reeval S' 11)` → `11`; `(position-after-reeval S 30)` → `20`;
      `(position-after-reeval S -1)` → `-1`.
- [ ] `(song-status S 13)` → `{:bar 13 :total 24 :section :chorus :section-bar 1 :section-bars 8}`.
- [ ] Validation: unknown track `:snar` in a section with known tracks above →
      `ex-info` whose message is `Unknown track :snar in section :verse — did you mean :snare?`;
      duplicate section name → `Duplicate section name :verse — section names must be unique in a song`;
      `(make-section :intro [1 2] [:kick])` and `(make-section :intro 0 [:kick])` throw
      `section :intro: length must be a whole number of bars ≥ 1, got …`.
- [ ] Event level (`song-onsets`, rationals): `(song-onsets M tracks #{} 1)` →
      `:hats` events `hh [1,5/4)`, `_ [5/4,3/2)`, `hh [3/2,7/4)`, `_ [7/4,2)` and no `:kick`;
      `(song-onsets M tracks #{} 2)` → `[]`; with `:end :loop`, bar 2 → `:kick` `bd [2,9/4)`,
      `_ [9/4,5/2)`, `bd [5/2,11/4)`, `_ [11/4,3)` plus the four `:hats` events of bar 0
      shifted by 2; `(song-onsets M tracks #{:kick} 0)` → only `:hats` events (mute wins).
- [ ] Event level for the outro: `(song-onsets S tracks #{} b)` for b = 20…23 contains only
      `:hats` events (no kick, snare or bass during the outro); events of `:source`-carrying
      patterns keep their `:source`. (Core tests build S's tracks with `core/seq*`,
      `core/fast` and plain values; the track names and section lists are what matter.)

**Lisp and app behaviour**
- [ ] `(section :intro 4 [:kick :hats])` evaluates to `{:type :section :name :intro :bars 4 :tracks [:kick :hats] …}`;
      `(section :intro 4 [:kick] 7)` → eval error `section :intro: unexpected argument 7 — sections take (section name bars tracks)`;
      a second `song` → `Only one (song …) per buffer`; a `track` below the song →
      `track :x is defined after (song …) — define tracks above the song`;
      `(play-from :chrous)` after S → `Unknown section :chrous — did you mean :chorus?`;
      `(play-from 30)` → `play-from: bar 30 is outside the song (0–23)` (`song_transport_test.cljs`).
- [ ] A buffer with tracks + a song block plays sections in order and stops after the last
      bar (`:end :stop`): harness `songEvents(M, 0, 3)` returns exactly 6 events —
      `{:hats :hh 0–0.25}`, `{:kick :bd 0–0.25}`, `{:hats :hh 0.5–0.75}`, `{:kick :bd 0.5–0.75}`,
      `{:hats :hh 1–1.25}`, `{:hats :hh 1.5–1.75}` (track, value, wholeStart–wholeEnd) — and
      in the app the scheduler calls `stop!` + `:on-song-end` after bar 23 + 2 s
      (output `song finished`).
- [ ] `:end :loop` loops the whole song: `songEvents(M with :end :loop, 0, 3)` returns 10 events,
      bar 2 equal to bar 0 shifted by 2. `arrange`/`play-scenes` behaviour is unchanged:
      `arrange-test`, `arrange-multi-cycle-span`, `arrange-creates-pattern` pass unmodified.
- [ ] Event-level outro test: `songEvents(S, 20, 24)` contains no event with track `:kick`
      (only `:hats`, 8 `:hh` onsets).
- [ ] A muted track stays silent in a section that lists it:
      `songEvents(M, 0, 1, {muted: [":kick"]})` returns only the 2 `:hats` events; in the
      scheduler `song-onsets` receives `(:muted state)`, so `solo!` works on top of sections.
- [ ] Starting from a named section begins playback at that section's first bar:
      `(play-from :chorus)` sets the next scheduled bar to 12 when playing, and starts at
      bar 12 after `(stop)` (re-activating the last song buffer); `(play-from 17)` → bar 17.
- [ ] Re-evaluating mid-song keeps the bar position (`audio/set-song!` uses
      `position-after-reeval`, unit-tested above); a song shortened below the current bar
      continues at the last section's first bar.
- [ ] The context panel shows the current section name and bar position
      (`▶ chorus · bar 13/24`), `● REC x / y s` during an export, and `(not in :outro)` on
      tracks that rest in the current section.
- [ ] A buffer without `song` behaves exactly as before: all tracks are activated (in
      buffer order) after evaluation, existing track/fx/session tests pass unchanged, the
      legacy bare-pattern buffer still replaces all tracks with `:_`, and snippet preview
      ignores `song`.

**Real-time export (Playwright, real `AudioContext`, `e2e/song.spec.ts`)**

Capture song **C**:

```lisp
(bpm 480)
(track :lead (synth :saw (seq :a3 :a3)))
(song (section :a 2 [:lead]))
```

- [ ] `audioReady()` resolves `"audioworklet+wasm"` in CI (WASM built in the
      `browser-audio` job).
- [ ] `(export :song)` captures one pass in real time and writes a WAV whose frame count
      equals song duration + tail (± 1 render quantum of 128 frames): `captureSong(C,
      {tail: 0.5})` (2 bars at 480 BPM = 1.0 s, + 0.5 s) → `|length − round(1.5 ·
      sampleRate)| ≤ 128`, `length === expectedFrames`, `late === false`, non-silent
      (`rms > 0.001`), all samples finite.
- [ ] The captured WAV contains WASM-voice output, not the JS sine fallback: in the same
      capture the `goertzel` magnitude at 440 Hz and at 660 Hz is each ≥ 0.1 × the
      magnitude at 220 Hz (a sine would give ≈ 0), and the capture reports backend
      `"audioworklet+wasm"` (the JS `:saw` fallback is a native sawtooth too, so the
      backend assertion is what rules it out).
- [ ] `(export n)` and `(export :track)` produce byte-identical WAVs to before the
      `repulse.wav` extraction (`wav_test.cljs` checks the 44-byte header and sample
      encoding; the existing offline e2e tests pass unchanged).

**Registration, docs, verification**
- [ ] New built-ins follow CLAUDE.md Rule 3: `song`, `section`, `play-from` in `BuiltinName`,
      `completions.js`, `hover.js`, `builtin_meta.edn`; `export` entries mention `:song`;
      `npm run gen:grammar` and `npm run gen:ai-docs` produce no diff after the commit.
- [ ] `docs/USAGE.md` gains a "Writing a song" section with a complete example (the Goal
      song), reference rows for the three built-ins and the `(export :song)` row;
      README updated; ROADMAP SONG1 marked delivered.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5), covering the items
      listed in step 12.

## What NOT to do

- No automation or transitions (filter sweeps, risers, fills) — that is SONG2. Keep
  `section`'s `& more` arity so SONG2 can add `(automate …)` without breaking it.
- No outline panel or click-to-definition — that is SONG3.
- No multiple buffers / `use` — that is SONG4.
- No stem export or MIDI song export — that is SK1.
- No offline (faster-than-real-time) render with WASM voices and FX — that is EXP1.
- Don't change the existing `(export n)` / `(export :track)` offline path (extracting the
  encoder into `repulse.wav` must not change its output).
- Don't remove or change the semantics of `arrange` / `play-scenes`.
- No time signatures or bar lengths other than 1 cycle (decision 5); no per-section
  pattern overrides (decision 3).
