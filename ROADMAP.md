# REPuLse — Roadmap

## Phase 1 — First Sound ✅ *delivered*

Browser REPL with ClojureScript pattern engine and Web Audio synthesis.

**Delivered:**
- Monorepo: `packages/core`, `packages/lisp`, `app/`
- REPuLse-Lisp: reader + evaluator with typo hints
- Pattern algebra: `seq`, `stack`, `pure`, `fast`, `slow`, `rev`, `every`, `fmap`
- Web Audio lookahead scheduler (Chris Wilson clock)
- Synthesized voices: kick, snare, hi-hat via oscillators/noise
- CodeMirror 6 editor, Ctrl+Enter evaluation
- ▶ play / ■ stop button
- Strudel CDN sample library (Dirt-Samples + Tidal Drum Machines)
- `(sound :bank n)` for indexed sample access, `(bpm N)` for tempo, `:_` rest
- Core unit tests (6 tests, 18 assertions)
- Safari compatibility: `webkitAudioContext` fallback, unconditional `.resume()`, improved first-play timing

---

## Phase 2 — Rust/WASM Synthesis ✅ *delivered*

Replace JS oscillator synthesis with a Rust/WASM module for better sound quality.
Sample loading from the Strudel CDN is unchanged.

**What changes:**
- `packages/audio/` — Rust crate compiles to WASM via `wasm-pack`
- `AudioEngine.trigger(value, time)` — WASM API called from ClojureScript
- Fallback chain: sample bank → WASM synth → JS synth
- Console shows `[REPuLse] audio backend: wasm` when active

**Synthesis improvements over Phase 1:**
- Kick: sine sweep 150 → 40 Hz, LCG noise-free envelope
- Snare: bandpass noise body + 180 Hz sine crack, tuned separately
- Hi-hat: closed (45 ms) and open (350 ms) as distinct voices
- All noise: LCG pre-generated 2s buffer in Rust (deterministic, no Math.random)

### Build requirements

```bash
# Install Rust (if not already)
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
source ~/.cargo/env
rustup target add wasm32-unknown-unknown

# Install wasm-pack
cargo install wasm-pack

# Build WASM and start dev server
npm run build:wasm
npx shadow-cljs watch app
```

Or in one step: `npm run dev:full`

**Definition of done:**
- [x] `npm run build:wasm` completes without errors
- [x] Browser console shows `[REPuLse] audio backend: wasm`
- [x] `:bd :sd :hh :oh` sound noticeably better than Phase 1
- [x] `(stop)` stops sound immediately
- [x] Fallback verified (JS synthesis when WASM unavailable — confirmed Firefox, Chrome, Safari)

See full spec: [PROMPTS/phase-2-rust-wasm.md](PROMPTS/phase-2-rust-wasm.md)

---

## Phase 3 — AudioWorklet ✅ *delivered*

Move the WASM module into an `AudioWorkletProcessor` so synthesis runs on the
dedicated audio thread — eliminating main-thread jank and GC pauses.

**What changed:**
- `packages/audio/` — Rust crate rewritten for PCM synthesis; `web-sys` removed entirely
- `AudioEngine` now accepts `sample_rate: f32` (not an `AudioContext`), generates raw `Float32Array` samples
- `app/public/worklet.js` — `AudioWorkletProcessor` that loads WASM via dynamic `import()` on the audio thread
- `MessagePort` channel: main thread → Worklet for `trigger`, `stop`, and `init` messages
- `app/src/repulse/audio.cljs` — `init-worklet!` replaces `init-wasm!`; `wasm-engine` atom replaced by `worklet-node` + `worklet-ready?`
- `app/public/index.html` — removed `<script type="module">` WASM bootstrap block
- Two-tier fallback: Worklet+WASM → JS synthesis (main-thread WASM tier removed)
- Console shows `[REPuLse] audio backend: audioworklet+wasm` when active

**Synthesis improvements over Phase 2:**
- All DSP runs on the dedicated audio render thread — zero main-thread audio work
- `process_block(n_samples, current_time)` called directly from `AudioWorkletProcessor.process()`
- Pending events are time-stamped and activated sample-accurately within each block
- Voice lifecycle managed in Rust; `voices.retain(|v| !v.is_silent())` keeps the voice list lean

**Definition of done:**
- [x] `npm run build:wasm` completes without errors (no `web-sys` dependency)
- [x] `app/public/worklet.js` registered as `repulse-processor`
- [x] Browser console shows `[REPuLse] audio backend: audioworklet+wasm`
- [x] `:bd :sd :hh :oh` play via PCM synthesis on the audio thread
- [x] `(stop)` clears all voices and pending events in the Worklet
- [x] JS synthesis fallback active when AudioWorklet is unavailable

See full spec: [PROMPTS/phase-3-audioworklet.md](PROMPTS/phase-3-audioworklet.md)

---

## Phase 5 — Active Code Highlighting ✅ *delivered*

As a pattern plays, the editor highlights the exact tokens in the source code that are
generating the current sound — like Strudel.cc.

**How it works:**
- Reader extended to attach `{:from N :to N}` source ranges to every parsed form
- Evaluator propagates source ranges from literals into pattern events as `:source`
- Scheduler fires a `setTimeout` for each event, timed to the event's audio time
- CodeMirror applies a 120 ms CSS flash (`active-event` class) on the source range

**Example:** for `(seq :bd :sd :hh :sd)`, `:bd` flashes on beat 1, `:sd` on beats 2 and 4,
`:hh` on beat 3. Works with `stack`, `fast`, `every`, `def`, and numeric frequencies.

See full spec: [PROMPTS/phase-5-active-highlighting.md](PROMPTS/phase-5-active-highlighting.md)

---

## Phase 6a — Plugin System + Visual Plugins ✅ *delivered*

A lightweight plugin API and the first plugin type: visual plugins that receive the audio
stream and draw to a canvas — like Strudel's oscilloscope and spectrum views.

**Key additions:**
- Plugin registry (`plugins.cljs`) — register, list, unregister plugins by name
- Host API object passed to every plugin on `init` (audioCtx, analyser, registerLisp)
- Permanent `AnalyserNode` tap on the master bus (no audible change)
- Plugin panel DOM (collapsible, below the editor)
- Built-in **oscilloscope** plugin (`app/public/plugins/oscilloscope.js`)
- `(load-plugin url)` Lisp built-in — dynamically imports a third-party plugin

See full spec: [PROMPTS/phase-6a-plugins-visual.md](PROMPTS/phase-6a-plugins-visual.md)

---

## Phase 6b — Effect Plugins ✅ *delivered*

The second plugin type: Web Audio nodes inserted into the master signal chain, addressable
from the Lisp REPL via `(fx :name ...)`.

**Key additions:**
- Effect plugin interface — `createNodes`, `setParam`, `bypass`, `destroy`
- Graph manager (`fx.cljs`) — inserts/rewires effect nodes cleanly, with dry/wet bypass
- Five built-in effects: **reverb**, **delay**, **filter**, **compressor**, **dattorro-reverb**
- `(fx :reverb 0.4)` / `(fx :delay :wet 0.4 :time 0.25)` / `(fx :off :reverb)` Lisp built-ins
- Compressor reimplemented in ClojureScript (`app/src/repulse/plugins/compressor.cljs`)
- Dattorro plate reverb runs in its own `AudioWorkletProcessor` (high-quality, audio-thread)

See full spec: [PROMPTS/phase-6b-plugins-effects.md](PROMPTS/phase-6b-plugins-effects.md)

---

## Phase 8 — Song Arrangement Language ✅ *delivered*

A three-layer abstraction for composing full songs: motifs → sections → arrangement.
Keeps live coding readable as pieces grow beyond a handful of patterns.

**The three layers:**
```lisp
; Layer 1: motifs
(def kick  (seq :bd :_ :bd :_))
(def snare (seq :_ :sd :_ :sd))

; Layer 2: sections
(def verse  (stack kick snare))
(def chorus (stack kick snare (fast 2 (seq :hh :oh))))

; Layer 3: arrangement
(arrange [[intro 4] [verse 8] [chorus 8] [verse 8] [chorus 8]])
```

**Key additions:**
- `arrange* ` in `core.cljs` — time-shifting combinator; sections play in order, arrangement loops
- `(arrange [[pattern cycles] ...])` Lisp built-in — full arrangement with per-section cycle counts
- `(play-scenes [pat pat pat ...])` — each section plays for 1 cycle (Ableton scene-chain style)
- Map literals `{:key val}` in the reader + `get`, `assoc`, `merge` built-ins
- Parametric section factories: `(def make-verse (fn [opts] (if (get opts :dense false) ...)))`

See full spec: [PROMPTS/phase-8-song-arrangement.md](PROMPTS/phase-8-song-arrangement.md)

---

## Phase 10 — Syntax Highlighting ✅ *delivered*

A CodeMirror 6 language extension for REPuLse-Lisp — bracket matching and colour-coded
syntax using a hand-written Lezer grammar.

**Key additions:**
- Lezer grammar (`repulse-lisp.grammar`) — compiled once, parser committed to repo
- Highlight spec mapping Lezer node types to `@lezer/highlight` tags that integrate
  with the existing oneDark theme
- Keywords (`:bd`, `:sd`, …) in orange; built-ins (`seq`, `stack`, …) in purple;
  numbers gold; strings green; comments grey
- `(bracketMatching)` — clicking a delimiter highlights its pair
- `gen:grammar` npm script to regenerate the parser after grammar edits

See full spec: [PROMPTS/phase-10-syntax-highlighting.md](PROMPTS/phase-10-syntax-highlighting.md)

---

## Phase 9 — External Sample Repository Import ✅ *delivered*

Load sample banks at runtime from any public GitHub repository or a manifest file,
directly from the REPL. Two manifest formats are supported.

**Key additions:**
- `(samples! "https://…/samples.edn")` — REPuLse Lisp manifest (native format, parsed by the existing reader)
- `(samples! "https://…/samples.json")` — Strudel-compatible JSON manifest (ecosystem compat)
- `(samples! "github:owner/repo")` — auto-discovers audio files via the GitHub public tree API, groups by folder name, registers as banks
- `(samples! "github:owner/repo/branch")` — target a specific branch
- `(sample-banks)` — list all currently registered bank names
- After loading, new banks usable immediately: `(seq :my-kick :my-snare)`

**REPuLse Lisp manifest format (`.edn`):**
```clojure
{:_base "https://raw.githubusercontent.com/user/repo/main/samples/"
 :kick  ["kick1.wav" "kick2.wav"]
 :snare ["snare1.wav" "snare2.wav"]}
```
Parsed by the existing `repulse.lisp.reader` — no new dependencies. Keywords become bank names.

**How GitHub discovery works:**
Queries `api.github.com/repos/{owner}/{repo}/git/trees/{branch}?recursive=1`,
filters for audio extensions (`.wav`, `.mp3`, `.ogg`, `.flac`, `.aiff`),
groups files by immediate parent folder name, and fetches samples from
`raw.githubusercontent.com`. Both endpoints support CORS from the browser.

See full spec: [PROMPTS/phase-9-external-sample-repos.md](PROMPTS/phase-9-external-sample-repos.md)

---

## Phase C — Code Completion ✅ *delivered*

Autocompletion for the editor: all built-in names with docstrings, plus live tracking
of user-defined `def` bindings as the code changes.

**Key additions:**
- `completions.js` — static list of all built-ins with `detail` strings shown in the popup
- `defs-completion.js` — CM6 `CompletionSource` that walks the Lezer syntax tree to find `(def name …)` bindings and adds them dynamically
- `@codemirror/autocomplete` wired into `lispLanguage` in `index.js` — no `app.cljs` changes

See full spec: [PROMPTS/phase-c-code-completion.md](PROMPTS/phase-c-code-completion.md)

---

## Phase A — More Effects ✅ *delivered*

Additional effect plugins: chorus, phaser, tremolo, overdrive, and bitcrusher — all
following the same dry/wet plugin interface, addressable via `(fx :name ...)`.

**Key additions:**
- **Chorus** — two LFO-modulated delay lines, 1% rate offset for stereo detuning
- **Phaser** — 4 all-pass stages + LFO sweep + feedback delay node
- **Tremolo** — amplitude LFO with DC bias so gain never inverts the signal
- **Overdrive** — WaveShaperNode soft-clip with pre-gain boost and tone lowpass
- **Bitcrusher** — AudioWorklet processor with per-sample bit and rate reduction

See full spec: [PROMPTS/phase-a-more-effects.md](PROMPTS/phase-a-more-effects.md)

---

## Phase D — Editor Persistence ✅ *delivered*

Editor content survives page reloads via `localStorage`.

**Key additions:**
- `save-listener` CodeMirror extension — writes to `localStorage` on every document change
- `load-editor-content` — restores on startup, falls back to the default pattern
- `try/catch` around both operations for private-mode Safari compatibility

See full spec: [PROMPTS/phase-d-editor-persistence.md](PROMPTS/phase-d-editor-persistence.md)

---

## Phase D2 — Full Session Persistence 📋 *planned*

Persist **all session state** to localStorage so a page reload restores exactly what
the user had — effects, bank prefix, sample sources, mute/solo state, MIDI mappings,
and BPM. Adds `(reset!)` to wipe everything back to defaults.

**Key additions:**
- Persist FX chain (names, params, bypass state), bank prefix, mute/solo sets, BPM
- Persist loaded external sample sources (`samples!` calls)
- `(reset!)` — stops playback, clears all localStorage, reloads default demo
- New localStorage keys with versioned schema; forward-compat handling for unknown keys

See full spec: [PROMPTS/phase-d2-session-persistence.md](PROMPTS/phase-d2-session-persistence.md)

---

## Phase E — Session Context Panel ✅ *delivered*

A live sidebar to the right of the editor showing the current session state at a glance.

**Key additions:**
- **Status bar** — BPM (updates live when `(bpm N)` is called) + playing/stopped indicator
- **Bindings** — user `def`s with inferred type (`pattern`, `fn`, `number`, …); built-ins filtered out
- **Effects** — active effect chain with first key param value, or `off` indicator when bypassed
- `add-watch` on `env-atom`, `fx/chain`, and `scheduler-state` for zero-polling reactivity
- `:bypassed?` field added to `fx/chain` entries so bypass state is observable

See full spec: [PROMPTS/phase-e-context-panel.md](PROMPTS/phase-e-context-panel.md)

---

## Phase E2 — Live Session Dashboard ✅ *delivered*

Upgraded the context panel from a basic status display into a full live mirror of the session.

**Key additions:**
- **Tracks section** — every active track with state icon (`▶`/`■`/`★`), name, and inline params extracted from cycle 0 of its pattern (`amp`, `pan`, `decay`, `attack`, `release`, `synth`, `bank`, `rate`, `begin`, `end`); muted/solo states indicated; hidden when no tracks active
- **Conditional FX section** — only shown when at least one effect has been explicitly activated via `(fx :name value)`; `:active?` flag added to `fx/chain` entries, set to `true` by `set-param!`; bypassed active effects still shown with `off` indicator
- **MIDI section** — UI slot ready; appears automatically when `midi/cc-mappings` is non-empty (Phase N1)
- **Sources section** — tracks `(samples! "github:…")` loads via new `loaded-sources` atom in `samples.cljs`; `(freesound! …)` appends Freesound entries; hidden when none loaded
- **Bindings section** — hidden when empty (no more `—` placeholder)
- **Status bar** — adds `[wasm]`/`[js]` audio backend tag
- **rAF throttling** — `schedule-render!` debounces via `requestAnimationFrame`; all watchers call this instead of rendering directly
- **New watchers** — `samples/loaded-sources` and `midi/cc-mappings` now trigger panel updates

See full spec: [PROMPTS/phase-e2-live-session-dashboard.md](PROMPTS/phase-e2-live-session-dashboard.md)

---

## Phase E2b — Parameter Sliders 📋 *planned*

Make numeric params in the session dashboard **interactive sliders** that update
the editor code live and change the audio immediately, without re-evaluation.

**Key additions:**
- Per-param `<input type="range">` sliders with param-appropriate min/max/step
- Exponential scaling for time params (attack, decay, release)
- Slider drag → writes to `param-overrides` atom (instant audio) + rewrites number
  literal in editor via Lezer parse tree traversal
- Undo-friendly: micro-movements not in undo history; final value on release is
- Alt+Enter clears overrides — code is now source of truth
- Depends on Phase E2 (per-track param display) and Phase N1 (`param-overrides` atom)

See full spec: [PROMPTS/phase-e2b-param-sliders.md](PROMPTS/phase-e2b-param-sliders.md)

---


## Phase F — Drum Machine Bank Prefix ✅ *delivered*

A `(bank :MachineName)` built-in that sets a global drum machine prefix for the session.
After `(bank :AkaiLinn)`, a bare `:bd` resolves to the `AkaiLinn_bd` sample bank
automatically — no need to spell out the full name every time.

```lisp
(do
  (bank :AkaiLinn)
  (seq :bd :sd :_ :sd))
```

**Key additions:**
- `active-bank-prefix` atom in `samples.cljs` — nil or a prefix string like `"AkaiLinn"`
- `set-bank-prefix!` — sets or clears the prefix; logs to console
- `resolve-keyword` — tries `<prefix>_<kw>` first; falls through silently if bank absent
- `play-event` in `audio.cljs` updated to call `resolve-keyword` before sample lookup
- `(bank :Name)` / `(bank nil)` Lisp built-in registered in `ensure-env!`
- Context panel status bar shows the active bank name (in purple); hidden when cleared
- `add-watch` on `active-bank-prefix` so the panel updates reactively

See full spec: [PROMPTS/phase-f-bank-prefix.md](PROMPTS/phase-f-bank-prefix.md)

---

## Phase G — Music Theory ✅ *delivered*

A music theory layer: note keywords, scales, chords, and semitone transposition —
making melodic patterns as natural to write as rhythmic ones.

**Key additions:**
- `packages/core/src/repulse/theory.cljs` — new namespace with note parsing, scale/chord/transpose
- Note keywords `:c4`, `:eb3`, `:fs5` etc. resolve directly to Hz in the audio dispatcher
- `(scale kw root pat)` — maps degree integers to Hz using named scales (major, minor, dorian, etc.)
- `(chord kw root)` — stacks chord tones as a simultaneous pattern of Hz values
- `(transpose n pat)` — shifts all Hz values by n semitones; drum keywords pass through unchanged
- 10 supported scales, 13 chord types; equal-temperament with A4 = 440 Hz
- Unit tests in `packages/core/test/repulse/theory_test.cljs`
- Syntax highlighting and code completion for all three functions

See full spec: [PROMPTS/phase-g-music-theory.md](PROMPTS/phase-g-music-theory.md)

---

## Phase H — Per-Event Parameters ✅ *delivered*

Amplitude, envelope shape, and stereo position attached directly to pattern events as
first-class parameters that compose freely with all combinators.

**Key additions:**
- `combine` in `packages/core/src/repulse/core.cljs` — applicative liftA2 over patterns
- `packages/core/src/repulse/params.cljs` — `amp`, `attack`, `decay`, `release`, `pan`
- Each parameter function is curried: `(amp 0.8 pat)` applies directly; `(amp 0.8)` returns a transformer
- `->>` thread-last special form in the evaluator — chains transformers, pattern as last arg
- `comp` built-in — compose transformers right-to-left for reusable voice presets
- Map-value routing in `play-event` — `{:note :c4 :amp 0.8}` dispatched to WASM with full params
- WASM `trigger_v2` API extended with amp, attack, decay, pan
- `StereoPannerNode` in the JS synthesis fallback for pan support
- Unit tests in `packages/core/test/repulse/params_test.cljs`

See full spec: [PROMPTS/phase-h-per-event-params.md](PROMPTS/phase-h-per-event-params.md)

---

## Phase 4 — Live Performance Features ✅ *delivered*

Multiple named pattern tracks, a bash-style command bar for imperative commands,
tap BPM, MIDI clock sync, a visual track timeline, and session URL sharing.

**Key additions:**
- `scheduler-state` extended: `:tracks {kw → Pattern}` + `:muted #{kw}` replaces single `:pattern`
- `(play :name pattern)` — starts or replaces a named track; `ensure-running!` starts the interval only once
- `(mute! :name)`, `(unmute! :name)`, `(solo! :name)` — track mute controls; `!` marks side effects (Clojure convention)
- `(clear! :name)` / `(clear!)` — remove one or all tracks; `(tracks)` — list active track names
- `(upd)` — hot-swap: re-evaluates the editor buffer and updates running tracks without stopping; for raw patterns replaces the anonymous `:_` track; for `(play ...)` calls updates named tracks in-place
- **Command bar** — separate single-line CodeMirror editor below the main buffer for imperative one-shot commands; Enter evaluates + clears, Escape clears, Cmd+A selects all; not saved to localStorage; includes syntax highlighting + completions
- **Tap BPM** — rolling window of last 8 taps within 4 seconds; `(tap!)` Lisp built-in or tap button
- **MIDI clock sync** — `(midi-sync! true/false)` enables Web MIDI API 24ppqn clock handler
- **Session URLs** — `#v1:<base64-JSON>` URL hash encoding `{v, bpm, editor}`; share button copies URL
- **Track timeline** — SVG per-track rows with proportional event bars and RAF playhead sweep
- BPM auto-saved to `localStorage`; restored on reload

See full spec: [PROMPTS/phase-4-live-features.md](PROMPTS/phase-4-live-features.md)

---

## Phase I — Pattern Combinators ✅ *delivered*

Eight new pure pattern functions that complete REPuLse's expressive vocabulary.
Highest-value additions: cheap to implement (pure CLJS, no DOM/audio), immediate
impact on what you can write in one line.

**Key additions:**
- `(euclidean k n pat)` — Björklund algorithm; `(euclidean 5 8 :bd)` distributes 5 hits across 8 steps
- `(cat pat-a pat-b ...)` — multi-pattern concatenation; each plays for one full cycle in sequence
- `(late n pat)` / `(early n pat)` — rational-arithmetic time shift within a cycle
- `(sometimes f pat)` / `(often f pat)` / `(rarely f pat)` — cycle-hashed stochastic transforms; deterministic per cycle
- `(degrade pat)` / `(degrade-by p pat)` — per-event stochastic dropout
- `(choose [v1 v2 ...])` / `(wchoose [[v1 w1] ...])` — cycle-deterministic random selection
- `(jux f pat)` — juxtapose: apply `f` to the right channel while left plays unchanged; needs `pan`
- `(off n f pat)` — offset copy: `(stack pat (late n (f pat)))`
- All functions in `packages/core/src/repulse/core.cljs`; `jux` in `params.cljs`

See full spec: [PROMPTS/phase-i-pattern-combinators.md](PROMPTS/phase-i-pattern-combinators.md)

---

## Phase J — Onboarding & Discoverability ✅ *delivered*

Lower the activation energy for new users — the "first 60 seconds" experience.
No new language features; pure UX and documentation.

**Key additions:**
- **Demo patterns** — curated starter examples selectable from a dropdown or `(demo :name)` Lisp call
- **Hover docs** — CodeMirror hover extension shows docstring + example for any built-in under the cursor
- **Signature hints** — parameter hints appear as you type after an opening paren
- **Error messages** — contextual hints in the output footer for the 10 most common mistakes
- **Interactive tutorial** — `(tutorial)` loads a step-by-step guided session into the editor

See full spec: [PROMPTS/phase-j-onboarding.md](PROMPTS/phase-j-onboarding.md)

**Backlog:**
- [ ] Expand tutorials beyond the current 8 chapters to cover **all** built-in functions and effects — euclidean, cat, late/early, sometimes/often/rarely, degrade, choose/wchoose, jux/off, arrange/play-scenes, fx (reverb, delay, filter, compressor, chorus, phaser, tremolo, overdrive, bitcrusher, dattorro), samples!/bank/sound, and MIDI sync
- [ ] Add more demo templates covering additional genres and showcasing uncovered features

---

## Phase K — Mini-Notation & Sharing ✅ *delivered*

Tidal/Strudel-compatible mini-notation as opt-in Lisp sugar, plus Gist import and WAV export.

**Key additions:**
- `(~ "bd sd [hh hh] bd")` — mini-notation parser in `packages/lisp/src/repulse/lisp/mini.cljs`
- Supported syntax: sequences `bd sd`, sub-groups `[bd sd]`, alternation `<bd sd>`, repetition `hh*4`, rest `~`/`_`, sample index `bd:2`, weight/elongation `bd@3`, probability `bd?`
- `(alt pat-a pat-b ...)` — cycle-based alternation as a first-class Lisp function
- `(load-gist url)` — fetch a GitHub Gist and load into the editor
- `(export n)` — render n cycles of the current pattern to a downloadable WAV file via `OfflineAudioContext`
- `mini.cljs` has no audio or DOM dependency — pure pattern algebra

**Delivered:**
- `packages/lisp/src/repulse/lisp/mini.cljs` — tokeniser → recursive-descent parser → compiler to patterns
- `seq-of-pats` and `weighted-seq*` for pattern sequences (not `core/seq*` which treats args as plain values)
- `alt*` for cycle-based alternation, `degrade` for 50% probability
- `float->rat` helper (local, denominator 100000) since `core/float->rat` doesn't exist
- `~` and `alt` registered in `eval.cljs` `make-env`
- `load-gist` and `export` registered in `app.cljs` `ensure-env!`
- Grammar: `~`, `alt`, `load-gist`, `export` added to `BuiltinName`; `~` added to `identStart`/`identChar`
- 20 new mini-notation unit tests, all passing (96 total, 0 failures)
- `packages/lisp/src/repulse/lisp/mini_test.cljs` co-located with source (matching project convention)

See full spec: [PROMPTS/phase-k-mini-notation.md](PROMPTS/phase-k-mini-notation.md)

---

## Phase L — Per-Track Audio Routing & Sample Control ✅ *delivered*

Independent effect chains per track, per-event sample playback parameters, pattern-aware
sidechain, and four new synth voices.

**Key additions:**
- Per-track `GainNode` routing: each `(play :name pat)` gets its own node before `masterGain`
- `(track-fx :name :effect param ...)` — apply/remove/adjust effects on a single track
- New param functions: `(rate 1.5 pat)`, `(begin 0.2 pat)`, `(end 0.8 pat)`, `(loop-sample true pat)`
- **Pattern-aware sidechain** (`sidechain.js` plugin) — ducks master bus on `:bd` events via pre-scheduled gain automation
- Four new WASM voice types: `saw`, `square`, `noise`, `fm` (index + ratio)
- Dependency architecture: `fx.cljs` owns `notify-fx-event!`; scheduler calls it via an `:on-fx-event` callback — keeps `audio ↛ fx` acyclic

See full spec: [PROMPTS/phase-l-per-track-audio.md](PROMPTS/phase-l-per-track-audio.md)

---

## Phase M — Lisp Superpowers ✅ *delivered*

Four language-level additions that make REPuLse-Lisp a genuine instrument, not just a
shell around pattern combinators.

**Key additions:**
- `(defsynth name [params] body)` — user-defined instruments from Web Audio node graphs; UGen vocabulary: `sin`, `saw`, `square`, `tri`, `noise`, `lpf`, `hpf`, `bpf`, `mix`, `env-perc`, `env-asr`
- `(synth :name pat)` — apply a user-defined synth to any pattern
- `(defmacro name [params] body)` — compile-time macro expansion with backtick/unquote/splice-unquote
- `(loop [bindings] body)` / `(recur ...)` — trampoline-style tail-call optimised iteration
- `(defn name [params] body)` — named function sugar (`def` + `fn`)
- Rational number literals: `1/4` → `[1 4]`; `120bpm` → `(bpm 120)`
- Collection helpers: `conj`, `count`, `nth`, `first`, `rest`, `concat`, `map`, `range`, `apply`

See full spec: [PROMPTS/phase-m-lisp-superpowers.md](PROMPTS/phase-m-lisp-superpowers.md)

---

## Phase N — MIDI & External I/O ✅ *delivered*

Connect REPuLse to the outside world: hardware controllers, DAWs, sample libraries.

**Key additions:**
- `(midi-map :cc N :target)` — map MIDI CC messages from any controller to `:filter`, `:amp`, or `:bpm`
- `(midi-out ch pat)` — route pattern events as MIDI Note On/Off on a channel (1–16)
- `(midi-clock-out! true/false)` — broadcast 24ppqn MIDI clock + Start/Stop at current BPM
- `(midi-export :track N)` — export N cycles as a `.mid` file (pure binary, no external library)
- `(freesound-key! "key")` + `(freesound! "query")` — search freesound.org and load samples directly
- Chrome/Edge only (Web MIDI API); other browsers return a clear error
- Extends `app/src/repulse/midi.cljs` (new namespace)

See full spec: [PROMPTS/phase-n-midi-io.md](PROMPTS/phase-n-midi-io.md)

---

## Phase N1 — MIDI CC → Parameter Mapping 📋 *planned*

Bind any MIDI controller knob or fader to any numeric parameter in REPuLse for
hands-on live performance control.

**Key additions:**
- `(midi-map cc target)` — bind CC number to `:filter`, `:amp`, `:bpm`, `:reverb`, or
  any per-event param (optionally scoped to a track with `:track :name`)
- `(midi-unmap cc)` / `(midi-maps)` — remove or inspect mappings
- `(midi-learn target)` — learn mode: move a knob, it maps to the target automatically
- `param-overrides` atom applies CC values at event dispatch without re-evaluation
- Mappings persisted in localStorage (via Phase D2); shown in E2 dashboard MIDI section
- Global targets: `:bpm` (scaled 60–240), `:amp`, FX param names
- Track-scoped targets: any per-event param on a named track

See full spec: [PROMPTS/phase-n1-midi-cc-mapping.md](PROMPTS/phase-n1-midi-cc-mapping.md)

---

## Phase O — Platform & Deployment 📋 *planned*

Expand where REPuLse runs and who can use it.

**Key additions:**
- **PWA / offline** — service worker (cache-first), web app manifest, install prompt; `(download-bank! :Name)` caches samples for offline use
- **Embeddable component** — `<repulse-editor code='...' autoplay>` custom element via Shadow DOM; separate `repulse-embed.js` bundle
- **Collaborative sessions** — `(collab-start!)` / `(collab-join! "code")` via Yjs + WebRTC; peer-to-peer, no server required; synced code = synced audio
- **Mobile layout** — CSS media queries for ≤768px; ≥44px touch targets; command bar font-size prevents iOS auto-zoom
- New dependencies: `yjs`, `y-webrtc`, `y-codemirror.next` (collab only)

See full spec: [PROMPTS/phase-o-platform.md](PROMPTS/phase-o-platform.md)

---

## Phase B — Richer Visuals ✅ *delivered*

Two new visual plugin types: a high-quality spectrum analyser and a p5.js canvas
plugin adapter for generative graphics driven by audio data.

**Delivered:**
- **`spectrum.js`** — GPU-accelerated frequency spectrum via audiomotion-analyzer@4.5.4;
  auto-loads at startup; 1/12-octave band display with prism gradient and peak indicators
- **`p5-base.js`** — shared p5.js loader (esm.sh, pinned to v1.11.11) and `makeP5Plugin(name, version, sketchFn)`
  factory; sketch receives `(p, analyser, audioCtx)` and sets up `p.setup` / `p.draw`
- **`p5-waveform.js`** — built-in example p5 sketch (time-domain waveform, HSB colours)
- **`(unload-plugin "name")`** — removes a plugin and its DOM element; hides the panel
  when no visual plugins remain; returns `{:error …}` for unknown names
- Plugin panel layout updated to `flex-direction: column`, `max-height: 40vh`
- Grammar and completions updated with `unload-plugin`

**Usage:**
```lisp
(load-plugin "/plugins/oscilloscope.js")  ; add oscilloscope alongside spectrum
(load-plugin "/plugins/p5-waveform.js")   ; add p5 waveform sketch
(unload-plugin "spectrum")                ; remove spectrum and its canvas
```

See full spec: [PROMPTS/phase-b-richer-visuals.md](PROMPTS/phase-b-richer-visuals.md)

---

## Phase T1 — Parameter Transitions ✅ *delivered*

Smooth parameter changes over musical time via a `tween` built-in. A single message
is sent to the WASM audio engine, which handles per-sample interpolation — no polling
from ClojureScript.

**Key additions:**
- `(tween curve start end bars)` — returns a tween descriptor stored inside event maps; curve is `:linear`, `:exp`, or `:sine`
- `CurveType` enum + `Transition` struct in `packages/audio/src/lib.rs` — zero-allocation per-sample interpolation
- `start_transition` / `clear_transitions` on `AudioEngine` — replaces any running transition for `"amp"` or `"pan"`
- `{type: "transition"}` worklet message — WASM receives the full ramp spec at note-on time
- `arm-transitions!` in `app/src/repulse/audio.cljs` — detects tween descriptors in the first cycle and dispatches transition messages; re-evaluation restarts the transition
- Rust unit tests: linear quartiles, exp midpoint, sine symmetry, clamp-at-end, zero-duration

**Delivered:**
- `tween` built-in in the Lisp evaluator: validates curve type and duration, returns a plain data map stored as a parameter value
- `Transition` struct with per-sample `tick()` interpolation and three curve shapes (linear, quadratic/exp, sine S-curve)
- `AudioEngine::start_transition` / `clear_transitions` wasm_bindgen methods; `stop_all` clears transitions
- Global amp + pan transitions applied in `process_block` after voice mix; holds end value indefinitely on completion
- `arm-transitions!` function queries the first cycle of each pattern, detects tween descriptors, and sends a single `transition` message to the worklet on (re-)evaluation
- `schedule-cycle!` replaces tween descriptors with neutral values (`1.0` for amp, `0.0` for pan) so the WASM ramp applies correctly
- Grammar, completions, and hover docs updated; `parser.js` regenerated

See full spec: [PROMPTS/PHASE-T1.md](PROMPTS/PHASE-T1.md)

---

## Phase P — Modular Routing: Busses & Control Rate 📋 *planned*

Named audio and control-rate busses for inter-synth modulation — patch an LFO
into a filter cutoff, sidechain one synth from another, build modular-style rigs.

**Key additions:**
- `(bus :name)` / `(bus :name :control)` — create named audio or control-rate busses in `app/src/repulse/bus.cljs`
- `(out :bus-name signal)` — write a synth's output to a named bus (inside `defsynth`)
- `(in :bus-name)` — read from a bus as a UGen source (inside `defsynth`)
- `(kr freq signal)` — control-rate wrapper; downsamples to per-block updates instead of per-sample
- `(env [levels] [times] [curves])` — general envelope constructor with per-segment curve types (`:lin`, `:exp`, `:sin`, `:welch`, `:step`, or a numeric curvature)
- `(env-gen envelope gate)` — apply any envelope to a signal, with gate-based sustain/release
- Bus inspector in the context panel — shows active busses and their current values
- Unit tests for envelope math in `packages/core/test/repulse/envelope_test.cljs`

See full spec: [PROMPTS/phase-p-modular-routing.md](PROMPTS/phase-p-modular-routing.md)

---

## Phase DST1 — Soft Clipping Distortion 📋 *planned*

Add `:distort` to the `(fx ...)` effect chain — a musical soft-clip waveshaper with
drive, tone, dry/wet, and three clipping algorithms (`:tanh`, `:sigmoid`, `:atan`).

**Key additions:**
- `(fx :distort :drive 8)` — soft clip with 1–100 drive range and gain compensation
- `:tone` — post-distortion lowpass cutoff (200–20000 Hz)
- `:mix` — dry/wet blend
- `:algo` — clipping curve: `:tanh` (default), `:sigmoid`, `:atan`

See full spec: [PROMPTS/PHASE-DST1.md](PROMPTS/PHASE-DST1.md)

---

## Phase DST2 — Asymmetric Soft Clipping 📋 *planned*

Extends Phase DST1. Adds `:asym` parameter to `:distort` for even-harmonic "warm tube"
coloration, plus a DC blocker to remove the offset asymmetric clipping introduces.

**Key additions:**
- `:asym` (-1.0–1.0) — positive values produce harder clipping on the positive half-wave
- DC blocker (`IIRFilterNode`, ~5 Hz highpass) always in path to enable click-free live changes

See full spec: [PROMPTS/PHASE-DST2.md](PROMPTS/PHASE-DST2.md)

---

## Phase DST3 — Multi-Stage Amp Simulation 📋 *planned*

New `(fx :amp-sim ...)` effect — cascaded tube preamp stages with inter-stage filters,
a 3-band tone stack with presets, and power supply sag simulation.

**Key additions:**
- `:gain` (1–100), `:stages` (1–4), `:tone`, `:mix`
- `:tonestack` — `:neutral`, `:bright`, `:dark`, `:mid-scoop`, `:mid-hump` presets
- `:sag` (0–1) — transient compression / "spongy" feel

See full spec: [PROMPTS/PHASE-DST3.md](PROMPTS/PHASE-DST3.md)

---

## Phase DST4 — Oversampling Wrapper 📋 *planned*

Adds `:oversample 1/2/4` to both `:distort` and `:amp-sim` using the native
`WaveShaperNode.oversample` property — zero-cost anti-aliasing at high drive.

See full spec: [PROMPTS/PHASE-DST4.md](PROMPTS/PHASE-DST4.md)

---

## Phase DST5 — Waveshaper Lookup Table 📋 *planned*

New `(fx :waveshape :curve C ...)` effect for arbitrary transfer-function distortion,
plus three Lisp built-in curve generators.

**Key additions:**
- `(fx :waveshape :curve (chebyshev 3))` — specific harmonic distortion
- `(fx :waveshape :curve (fold))` — wavefolding
- `(fx :waveshape :curve (bitcrush 4))` — bit-reduction staircase
- Custom float-array curves via CLJS vectors

See full spec: [PROMPTS/PHASE-DST5.md](PROMPTS/PHASE-DST5.md)

---

## Phase DST6 — Cabinet Simulation 📋 *planned*

New `(fx :cab :ir :4x12)` effect — convolution-based speaker cabinet simulation using
Web Audio `ConvolverNode` with procedurally generated impulse responses.

**Key additions:**
- `:ir` — `:1x12`, `:2x12`, `:4x12` (synthetic cabinet IRs), `:di` (bypass)
- IRs generated via `OfflineAudioContext` filtered noise — no external files
- Pairs naturally with `(fx :amp-sim ...)` for a full amp chain

See full spec: [PROMPTS/PHASE-DST6.md](PROMPTS/PHASE-DST6.md)

---

## Future ideas (unscheduled)

See [docs/FUTURE-FEATURES.md](docs/FUTURE-FEATURES.md) for the full prioritised feature
backlog — tiered by impact and implementation cost.
