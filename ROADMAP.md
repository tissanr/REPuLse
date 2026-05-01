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

## Phase D2 — Full Session Persistence ✅ *delivered*

Persist **all session state** to localStorage so a page reload restores exactly what
the user had — effects, bank prefix, sample sources, mute/solo state, MIDI mappings,
and BPM. Adds `(reset!)` to wipe everything back to defaults.

**Key additions:**
- Persist FX chain (names, params, bypass state), bank prefix, mute/solo sets, BPM
- Persist loaded external sample sources (`samples!` calls)
- `(reset!)` — stops playback, clears all localStorage, reloads default demo
- New localStorage keys with versioned schema; forward-compat handling for unknown keys

**Delivered:**
- New `app/src/repulse/session.cljs` module — `build-session-snapshot`, `save-session!`, `schedule-save!` (debounced 300ms), `load-session`, `migrate-legacy!`, `wipe!`
- Single `"repulse-session"` v2 JSON blob replaces separate `"repulse-editor"` / `"repulse-bpm"` keys
- Automatic migration: existing Phase D users silently upgraded on first load, legacy keys deleted
- Watchers on `audio/scheduler-state`, `fx/chain`, `samples/active-bank-prefix`, `samples/loaded-sources` trigger debounced saves
- Session restore at boot: BPM, bank prefix, editor text, FX chain params+bypass, muted tracks, external sample sources
- First-visit flow: random demo template loaded (from `:techno` / `:ambient` / `:house` / `:dnb` / `:minimal`), welcome message shown, not auto-played
- `(reset!)` built-in: stops playback, wipes all localStorage keys, reloads page to first-visit state
- `(share!)` upgraded to `#v2:` encoding with full session state; `#v1:` URLs remain backward-compatible
- `reset!` added to grammar (`npm run gen:grammar` run), completions, and hover docs

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

## Phase O1 — Embeddable Component ✅ *delivered*

Drop a live REPuLse editor into any static website with one `<script>` tag.

**Key additions:**
- **`<repulse-editor>` custom element** — Shadow DOM isolation; attributes: `code`, `snippet`, `autoplay`, `bpm`, `height`, `theme`
- **`snippet="id"` attribute** — pre-seeds editor from S1's `library.json` by snippet ID
- **Separate `embed.js` bundle** — shadow-cljs `:embed` build target outputting `app/public/embed.js`
- **`embed-test.html`** — minimal host page exercising all three attribute modes (inline code, snippet, no-autoplay)

**Delivered:**
- `app/src/repulse/embed.cljs` — custom element entry point; defines `<repulse-editor>` via `js*` ES6 class expression extending `HTMLElement`
- `app/src/repulse/embed_css.cljs` — Shadow DOM CSS string (editor styles, rainbow delimiters, active-event highlight, lint squiggle, hover tooltip)
- `app/public/embed-test.html` — three-instance test page demonstrating inline code + autoplay, snippet library, and no-autoplay modes
- `shadow-cljs.edn` `:embed` build target — produces `app/public/embed.js` (dev build ~647 KB gzipped; release build significantly smaller)
- All 134 core/lisp tests continue to pass

See full spec: [PROMPTS/PHASE-O1.md](PROMPTS/PHASE-O1.md)

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

## Phase P — Modular Routing: Busses & Control Rate ✅ *delivered*

Named audio and control-rate busses for inter-synth modulation — patch an LFO
into a filter cutoff, sidechain one synth from another, build modular-style rigs.

**Key additions:**
- `(bus :name)` / `(bus :name :control|:audio)` — creates a named bus backed by a `ConstantSourceNode` (control) or `GainNode` (audio); registry in `app/src/repulse/bus.cljs`
- `(out :bus-name signal)` / `(in :bus-name)` — write/read bus signals inside `defsynth` bodies; re-triggers replace the previous oscillator so connections don't accumulate
- `(kr rate signal)` — control-rate pass-through wrapper (informational; Web Audio is sample-rate throughout)
- `(env levels times curves?)` — general envelope descriptor with per-segment curve types: `:lin`, `:exp`, `:sin`, `:welch`, `:step`, or a numeric curvature value
- `(env-gen env-data signal)` — apply any `env` envelope to a UGen signal via `GainNode` automation; uses `setValueCurveAtTime` for non-linear shapes
- Pure envelope math (`packages/core/src/repulse/envelope.cljs`) with `lin-samples`, `sin-samples`, `welch-samples`, `exp-samples`, `custom-curve-samples`; tested in `envelope_test.cljs`
- Bus inspector section in the context panel — lists active bus names and types at a glance
- `(stop)` and `(clear!)` clean up all bus nodes and tracked synth writers

**Delivered:**
- `app/src/repulse/bus.cljs` — bus registry with per-synth writer tracking
- `app/src/repulse/synth.cljs` — `out-node`, `in-node`, `kr-node`, `env-gen-node`, `apply-env-automation!`
- `packages/core/src/repulse/envelope.cljs` — pure curve math (no Web Audio dependency)
- `packages/core/src/repulse/envelope_test.cljs` — 126 total tests passing, 0 failures
- Grammar + completions + hover docs for `bus`, `out`, `in`, `kr`, `env`, `env-gen`

See full spec: [PROMPTS/phase-p-modular-routing.md](PROMPTS/phase-p-modular-routing.md)

---

## Phase P2 — Modular Effect Routing 📋 *planned*

Advanced routing for the global and per-track effect chains: reorderable master
bus, parallel effect paths, and "Aux Sends" that route audio to named busses.

**Key additions:**
- `(fx-chain :name1 :name2 ...)` — reorder the master FX chain dynamically
- Parallel per-track FX via `(stack (fx :a) (fx :b))` syntax
- `(fx :send :bus-name amount)` — route track audio to a named audio bus
- Integration with the Phase P bus system for advanced "outboard" style processing

See full spec: [PROMPTS/PHASE-P2-MODULAR-FX.md](PROMPTS/PHASE-P2-MODULAR-FX.md)

---

## Phase DST1 — Soft Clipping Distortion ✅ *delivered*

Add `:distort` to the `(fx ...)` effect chain — a musical soft-clip waveshaper with
drive, tone, dry/wet, and three clipping algorithms (`:tanh`, `:sigmoid`, `:atan`).

**Key additions:**
- `(fx :distort :drive 8)` — soft clip with 1–100 drive range and gain compensation
- `:tone` — post-distortion lowpass cutoff (200–20000 Hz)
- `:mix` — dry/wet blend
- `:algo` — clipping curve: `:tanh` (default), `:sigmoid`, `:atan`

See full spec: [PROMPTS/PHASE-DST1.md](PROMPTS/PHASE-DST1.md)

---

## Phase DST2 — Asymmetric Soft Clipping ✅ *delivered*

Extends Phase DST1. Adds `:asym` parameter to `:distort` for even-harmonic "warm tube"
coloration, plus a DC blocker to remove the offset asymmetric clipping introduces.

**Key additions:**
- `:asym` (-1.0–1.0) — positive values produce harder clipping on the positive half-wave
- DC blocker (`IIRFilterNode`, ~5 Hz highpass) always in path to enable click-free live changes

**Delivered:**
- `app/public/plugins/distort.js` — `:asym` param, asymmetric `makeCurve`, DC blocker IIR filter, full `destroy()` cleanup
- `app/src/repulse/lisp-lang/hover.js` — hover docs for `:asym`
- `app/src/repulse/lisp-lang/completions.js` — `:asym` completion entry
- `app/src/repulse/lisp-lang/insert-categories.js` — `:asym` in insert categories
- `app/src/repulse/ui/context_panel.cljs` — `:asym` slider in session panel
- `docs/USAGE.md` — `:asym` documented in distortion effect table

See full spec: [PROMPTS/PHASE-DST2.md](PROMPTS/PHASE-DST2.md)

---

## Phase DST3 — Multi-Stage Amp Simulation ✅ *delivered*

New `(fx :amp-sim ...)` effect — cascaded tube preamp stages with inter-stage filters,
a 3-band tone stack with presets, and power supply sag simulation.

**Delivered:**
- `app/public/plugins/amp-sim.js` — multi-stage (1–4) cascaded waveshapers with asymmetric `STAGE_ASYM 0.2`
- 3-band tone stack (EQ) with 5 presets: `:neutral`, `:bright`, `:dark`, `:mid-scoop`, `:mid-hump`
- Power supply sag simulation via `DynamicsCompressorNode` approximation
- Inter-stage HPF/LPF filtering and DC blocker
- Interactive sliders in session dashboard for gain, stages, tone, sag, and mix
- Syntax highlighting and hover docs updated

**Key additions:**
- `:gain` (1–100), `:stages` (1–4), `:tone`, `:mix`
- `:tonestack` — `:neutral`, `:bright`, `:dark`, `:mid-scoop`, `:mid-hump` presets
- `:sag` (0–1) — transient compression / "spongy" feel

See full spec: [PROMPTS/PHASE-DST3.md](PROMPTS/PHASE-DST3.md)

---

## Phase DST4 — Oversampling Wrapper ✅ *delivered*

Adds `:oversample 1/2/4` to both `:distort` and `:amp-sim` using the native
`WaveShaperNode.oversample` property — zero-cost anti-aliasing at high drive.

**Delivered:**
- `:oversample` parameter added to `distort` and `amp-sim` plugins
- Real-time switching between `"none"`, `"2x"`, and `"4x"` oversampling
- Hover documentation and USAGE.md updated with new parameter
- Interactive sliders in session dashboard context panel

See full spec: [PROMPTS/PHASE-DST4.md](PROMPTS/PHASE-DST4.md)

---

## Phase DST5 — Waveshaper Lookup Table ✅ *delivered*

New `(fx :waveshape :curve C ...)` effect for arbitrary transfer-function distortion,
plus three Lisp built-in curve generators.

**Delivered:**
- `app/public/plugins/waveshape.js` — wrapper around Web Audio `WaveShaperNode`
- `:curve` parameter supporting `Float32Array`, Lisp vectors, or JS arrays
- `chebyshev` built-in generator for harmonic distortion (orders 1–8)
- `fold` built-in generator for triangle-wave wavefolding
- `bitcrush` built-in generator for quantization staircase curves (1–16 bits)
- `:drive` (pre-gain), `:tone` (post-LPF), and `:mix` controls
- Syntax highlighting and hover docs updated

**Key additions:**
- `(fx :waveshape :curve (chebyshev 3) :drive 2)`
- `(fx :waveshape :curve (fold) :drive 8)`
- `(fx :waveshape :curve (bitcrush 4))`
- `(fx :waveshape :curve [-1.0 -0.5 0 0.5 1.0])`

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

## Phase J2 — Contextual Insertion Buttons 📋 *planned*

Point-and-click code scaffolding: `+` buttons appear on hover over parentheses and
at empty line beginnings. Opening-paren `+` wraps the form (e.g., `fast`, `rev`,
`every`); closing-paren `+` chains after it via `->>` (e.g., `amp`, `pan`, effects);
empty-line `+` inserts a new top-level form.

**Key additions:**
- `app/src/repulse/lisp-lang/insert-helper.js` — ViewPlugin + WidgetType for `+` buttons and dropdown
- `app/src/repulse/lisp-lang/insert-categories.js` — categorised function lists (wrap, chain, top-level) with templates
- Opening `(` dropdown: `fast`, `slow`, `rev`, `every`, `jux`, `off`, `sometimes`, `degrade`, `stack`
- Closing `)` dropdown: `amp`, `pan`, `decay`, `attack`, `release`, and effects (`reverb`, `delay`, `filter`, …)
- Auto `->>` wrapping: extends existing `->>` chains or creates new ones
- Reuses `BUILTINS` from `completions.js` and `DOCS` from `hover.js`

See full spec: [PROMPTS/PHASE-J2.md](PROMPTS/PHASE-J2.md)

---

## Phase R0 — Correctness & Safety Fixes ✅ *delivered*

Fixed a small set of concrete correctness and security issues surfaced in code
review. This phase landed ahead of the larger refactor work and snippet-library
phases so those changes build on a safer baseline.

**Key additions:**
- `and` / `or` become short-circuiting special forms in `eval.cljs` (not eager functions)
- `coerce-bpm` helper clamps BPM to `[20, 400]`, rejects NaN/non-number — applied at `audio.cljs`, `session.cljs`, and `app.cljs` write sites
- `load-plugin` shows a confirmation dialog on first load per origin; consent remembered for the session
- Eval errors use a typed marker (`EvalError` record) instead of `{:error msg}` maps — so user data like `{:error "x"}` round-trips correctly
- `session_test.cljs` — first app-layer test, exercising BPM coercion on restore
- Remove unused `svelte` dependency from `app/package.json`
- `CLAUDE.md` corrections: remove "Svelte 5" claim, fix `npm run dev` description

See full spec: [PROMPTS/PHASE-R0.md](PROMPTS/PHASE-R0.md)

---

## Phase S1 — Local Snippet Library ✅ *delivered*

Browsable, auditionable snippet library inside the editor — curated starter set
shipped as static JSON. No backend required; runs on current Netlify deployment.
First step of the Snippet Library epic (S1–S4); validates the UX before building
the community backend.

**Delivered:**
- `app/public/snippets/library.json` — 24 curated snippets across 5 genres (rhythm, bassline, melody, chord-progression, fx-demo) with title, author, tags, BPM, description, and code
- `app/src/repulse/snippets.cljs` — snippet registry, async fetch, full-text + tag search/filter
- `app/src/repulse/ui/snippet_panel.cljs` — collapsible panel ("lib" button in header) with card grid, search input, and tag dropdown
- **Solo preview** (`▶ solo`): stops current session and plays snippet in isolation
- **Mix preview** (`⊕ mix`): adds snippet track alongside the running session
- **Insert** (`↓ insert`): appends snippet code to the editor and triggers `(upd)`; warns if track name conflicts
- `(snippet :id)` Lisp built-in for programmatic insertion; `(snippet)` lists available IDs
- `snippet` added to grammar, autocomplete, and hover docs

See full spec: [PROMPTS/PHASE-S1.md](PROMPTS/PHASE-S1.md)

---

## Phase R1 — App.cljs Modularization ✅ *delivered*

Pure refactor: split the 2035-line `app.cljs` into focused namespaces so the
upcoming Snippet Library backend (S2–S4) has a clean place to land. No new
features, no API changes — move-and-organize only.

**Delivered:**
- `content/demos.cljs` — demo template data + `demo` builtin factory (~200 lines)
- `content/tutorial.cljs` — tutorial chapters + `tutorial` builtin factory (~170 lines)
- `content/first_visit.cljs` — first-visit random demo loader (~30 lines)
- `ui/editor.cljs` — CodeMirror editor setup, highlighting infra, `make-editor`, `make-cmd-editor` (~140 lines)
- `ui/timeline.cljs` — SVG track timeline + RAF playhead loop (~55 lines)
- `ui/context_panel.cljs` — context panel DOM, slider config constants, `render-context-panel!`, `schedule-render!` (~290 lines)
- `plugin_loading.cljs` — plugin consent dialog (R0 code moved here) + `load-plugin`/`unload-plugin` factories (~85 lines)
- `env/builtins.cljs` — `ensure-env!` with all 40+ Lisp built-ins + owned atoms (`env-atom`, `builtin-names`, `seen-tracks`) (~370 lines)
- `eval_orchestrator.cljs` — `evaluate!`, `set-diagnostics!`, all slider code-patching fns (~160 lines)
- `app.cljs` trimmed from 2035 → **368 lines** (orchestrator: DOM helpers, session, bootstrap wiring)
- `docs/ARCHITECTURE.md` updated with module map and dependency rules
- `shadow-cljs compile app` — 0 warnings, clean build
- All 134 core+lisp tests still passing

See full spec: [PROMPTS/PHASE-R1.md](PROMPTS/PHASE-R1.md)

---

## Phase S2 — Backend & Authentication ✅ *delivered*

Migrated from static Netlify to Vercel + Supabase. Adds user accounts (GitHub OAuth),
snippet CRUD API, and database schema for community snippets. Pure infrastructure —
community browsing UI ships in S3.

**Delivered:**
- `vercel.json` — Vercel build config (WASM + shadow-cljs release, API functions, SPA rewrites)
- `supabase/schema.sql` — `profiles`, `snippets`, `stars` tables with RLS policies and star-count trigger
- `supabase/seed.sql` — all 24 S1 curated snippets seeded as system content
- `api/env.ts` — serves public Supabase credentials to the browser SPA
- `api/snippets.ts` — `GET` list (public) + `POST` create (authenticated)
- `api/snippets/[id]/star.ts` — `POST` toggle star for authenticated users
- `app/src/repulse/auth.cljs` — `auth-atom`, `init-auth!`, `login!`, `logout!` via `@supabase/supabase-js`
- `app/src/repulse/api.cljs` — fetch wrapper with JWT `Authorization` header
- `app/src/repulse/ui/auth_button.cljs` — login/avatar button in the app header
- `snippets.cljs` updated: loads from API when authenticated, falls back to static JSON for anonymous users
- `docs/DEPLOYMENT.md` — complete Vercel + Supabase setup guide
- Anonymous users: S1 static library unchanged; session URL sharing unaffected

See full spec: [PROMPTS/PHASE-S2.md](PROMPTS/PHASE-S2.md)

---

## Phase S3 — Community Snippets ✅ *delivered*

User-submitted snippets with ranking, usage tracking, and community browsing.
Depends on S2.

**Key additions:**
- **Share as snippet** button in snippet panel toolbar (logged-in only) — opens submit modal with title, description, tags, BPM pre-filled from current session; submits via `POST /api/snippets`
- **1-5 rating control** on each card with optimistic UI update; reverts on API error; disabled for anonymous users
- **Usage counter** incremented silently via `POST /api/snippets/:id/use` when Insert is clicked; uses `increment_snippet_usage` stored function for atomicity
- **Sort dropdown**: top rated, newest, most used, trending (server-side; trending uses time-decay formula in TypeScript)
- **Author filter**: debounced input → server-side filter by profile `display_name`; free-text search remains client-side
- **Report button** on each card → prompt for reason → `POST /api/snippets/:id/report` → row in `reports` table for manual review
- **`reports` table** added to Supabase schema with RLS (insert by auth user only)
- **Toast notifications** for successful snippet submission; error messages shown inline in modal
- Anonymous users: browse, preview, insert (all unchanged); authenticated users gain submit, star, report

**Delivered:**
- `api/snippets.ts` — extended GET with `sort`, `author` query params
- `api/snippets/[id]/use.ts` — new endpoint for usage tracking
- `api/snippets/[id]/report.ts` — new endpoint for moderation reports
- `supabase/schema.sql` — `reports` table + `increment_snippet_usage` function
- `app/src/repulse/api.cljs` — `track-usage!`, `report-snippet!`, updated `fetch-snippets`
- `app/src/repulse/snippets.cljs` — `sort-order`, `author-filter`, `ratings` atoms; `reload!` function
- `app/src/repulse/ui/snippet_submit_modal.cljs` — new submit modal with validation, Escape/click-outside close
- `app/src/repulse/ui/snippet_panel.cljs` — sort dropdown, author filter, rating/report buttons, share button

See full spec: [PROMPTS/PHASE-S3.md](PROMPTS/PHASE-S3.md)

---

## Phase S4 — Snippet Audio Preview ✅ *delivered*

Production-quality audition for community snippets: sandboxed eval, state
isolation, visual playing indicators, and per-card mini waveforms. Upgrades
S1's minimal preview.

**Key additions:**
- `app/src/repulse/snippets/preview.cljs` — isolated preview engine
- `app/src/repulse/snippets/sandbox.cljs` — env snapshot/restore so previews don't mutate user state
- 500ms execution time limit for runaway snippets
- Playing indicator (animated) on the active snippet card
- Mini waveform canvas per card, client-rendered via AnalyserNode during preview
- Syntax errors show as tooltip on the card, never crash the app
- Solo + mix preview both routed through the sandbox
- Owner-only snippet delete endpoint and card action for submitted snippets

See full spec: [PROMPTS/PHASE-S4.md](PROMPTS/PHASE-S4.md)

---

## Phase R2 — Builtin Environment Decomposition 📋 *planned*

Pure refactor of the two builtin registration monoliths: split pure language
builtins out of `packages/lisp/src/repulse/lisp/eval.cljs`, and split app/audio/UI
builtins out of `app/src/repulse/env/builtins.cljs`. No behaviour change, no new
built-ins, no public API change.

**Key additions:**
- Track A: `packages/lisp/src/repulse/lisp/builtins/*` — pattern, math, music,
  params, collection, types, synth, and arrangement builtin maps
- Track B: `app/src/repulse/env/builtins/*` — track, fx, samples, MIDI, content,
  export, session, routing, and plugin builtin factories
- `eval.cljs` keeps evaluator helpers and special forms only
- `env/builtins.cljs` keeps env ownership, callback wiring, `ensure-env!`, and
  public facade exports
- Before/after builtin key-set parity checks for both tracks

See full spec: [PROMPTS/PHASE-R2.md](PROMPTS/PHASE-R2.md)

---

## Phase CI1 — CI Pipeline ✅ *delivered*

GitHub Actions CI pipeline that runs on every PR and push to `main`: test suite,
CLJS lint, Rust checks, grammar-drift guard, and release build smoke test. No
feature changes — pure infrastructure. CD is unchanged (Vercel continues to
handle preview + production via git integration).

**Delivered:**
- `.github/workflows/ci.yml` — two jobs: `test` (`npm test` with Maven cache) and `release-build` (`npm run build:wasm` + `npx shadow-cljs release app` with Cargo + Maven cache)
- `.github/workflows/lint.yml` — three jobs: `lint` (clj-kondo across `packages/` and `app/src/`), `cargo-lint` (`cargo test` + `cargo clippy -D warnings` + `cargo fmt --check`), and `grammar` (drift check via `npm run gen:grammar` + `git diff --exit-code`)
- `.clj-kondo/config.edn` — silences CLJS false-positives (`deftype`, `defrecord`, `cljs.test` macros, `js/` interop) while keeping all real error classes active
- Status badges (`CI` and `Lint`) added to `README.md` header
- `docs/CONTRIBUTING.md` — pipeline overview table and local reproduction commands for all five checks

See full spec: [PROMPTS/PHASE-CI1.md](PROMPTS/PHASE-CI1.md)

---

## Phase HRD1 — Hardening ✅ *delivered*

AST-aware editor patching, remote fetch validation, and reproducible Rust builds.
No new features or language changes — pure correctness and infrastructure hardening.

**Delivered:**
- `app/src/repulse/lisp_patcher.cljs` (new): minimal Lisp tokenizer that skips
  comments and string literals, with four public paren-aware scanners
  (`find-param-num`, `find-fx-named-param-num`, `find-fx-pos-param-num`,
  `find-fx-form-close`). Replaces three regex-based patching functions in
  `eval_orchestrator.cljs` that silently misfired on nested forms, comments, and
  duplicate parameter names across tracks.
- `eval_orchestrator.cljs`: rewritten `patch-param-in-editor!`,
  `patch-fx-param-in-editor!`, and `patch-per-track-fx-param-in-editor!` to use
  the new scanner; extracted shared `fmt-num` and `dispatch-replace!` helpers.
- `samples.cljs`: added `fetch-ok!` helper that rejects with a descriptive error
  on non-2xx responses; applied to all four fetch chains (JSON manifest, Lisp
  manifest, GitHub tree API, audio buffer fetch).
- `rust-toolchain.toml` (new): pins `channel = "stable"` with
  `targets = ["wasm32-unknown-unknown"]` and `components = ["clippy", "rustfmt"]`,
  ensuring reproducible Rust builds and correct CI lint toolchain setup.
- `netlify.toml`: removed `rustup default stable && curl | sh` installer from the
  build command; deploys now run only project build steps.

See full spec: [PROMPTS/PHASE-HRD1.md](PROMPTS/PHASE-HRD1.md)

---

## Phase HRD2 — Security Hardening ✅ *delivered*

Security audit follow-up covering five vulnerability classes identified before
public launch of the community snippet library.

**Delivered:**
- `context_panel.cljs` — added `escape-html` and applied it to every user-controlled
  string site in all eight section renderers and three slider renderers, closing XSS
  via track names, binding names, FX names, bus names, source IDs, and param values
- `supabase/schema.sql` — enabled RLS on `profiles` table (was missing while `snippets`
  and `stars` already had it); added select-for-anyone and update-own-row policies
- `api/snippets.ts` — replaced `Access-Control-Allow-Origin: *` with a `setCors`
  helper that reflects origin only for `ALLOWED_ORIGINS` env var entries, `localhost:*`,
  and `*.vercel.app` previews; added field length limits (title 120, description 500,
  code 32 k, tags 40 chars × 20 items) and BPM range validation (1–999)
- `api/snippets/[id]/star.ts` — same CORS fix
- `vercel.json` — added `Content-Security-Policy` (blocks inline scripts; `'unsafe-eval'`
  required for shadow-cljs `new Function()` dispatch; `https:` in `script-src` for
  the plugin system), `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`,
  `Referrer-Policy: strict-origin-when-cross-origin`, and `frame-src` for Vercel toolbar

See full spec: [PROMPTS/PHASE-HRD2.md](PROMPTS/PHASE-HRD2.md)

---

## Phase HRD3 — Interface Specs 📋 *planned*

Harden REPuLse's runtime boundaries with explicit `cljs.spec` contracts, starting
with the public plugin API and extending to the data maps that cross persistence,
network, audio-routing, MIDI, and pattern-engine boundaries.

**Key additions:**
- Plugin interface specs for identity, visual/effect methods, host API, optional
  method normalization, and `createNodes` audio-node return contracts
- Core pattern specs for rational time, spans, events, and tagged Pattern maps
- Boundary specs for session snapshots, FX chain entries, sample manifests/loaded
  sources, event payload parameter maps, tween descriptors, and MIDI mappings
- Tests that invalid plugins, malformed sessions, bad effect nodes, and invalid
  core data fail early with clear diagnostics

See full spec: [PROMPTS/PHASE-HRD3.md](PROMPTS/PHASE-HRD3.md)

---

## Phase DOC1 — User Documentation Overhaul 📋 *planned*

Split the current monolithic user documentation into a friendly manual that works
for beginners, performers, and reference lookups. No app or language behaviour
changes — documentation-only.

**Key additions:**
- New 11-file user documentation structure: index, getting started, tutorial,
  cookbook, language, patterns, sound, effects, performance, reference,
  troubleshooting
- In-app help design: searchable help drawer, `(help ...)` commands, richer hover
  docs, contextual error help, recipe browser, and stable docs anchors
- Copyable, manually tested examples across drums, bass, melody, arrangement,
  effects, samples, MIDI, and live performance workflows
- Complete built-in reference with signatures and examples, cross-checked against
  evaluator built-ins, app built-ins, completions, hover docs, and grammar names
- `README.md` and legacy `docs/USAGE.md` updated so new users land on the right
  reading path instead of a single oversized manual

See full spec: [PROMPTS/PHASE-DOC1.md](PROMPTS/PHASE-DOC1.md)

---

## Phase HRD1 — Hardening ✅ *delivered*

AST-aware editor patching, remote fetch validation, and reproducible Rust builds.
No new features or language changes — pure correctness and infrastructure hardening.

**Delivered:**
- `app/src/repulse/lisp_patcher.cljs` (new): minimal Lisp tokenizer that skips
  comments and string literals, with four public paren-aware scanners
  (`find-param-num`, `find-fx-named-param-num`, `find-fx-pos-param-num`,
  `find-fx-form-close`). Replaces three regex-based patching functions in
  `eval_orchestrator.cljs` that silently misfired on nested forms, comments, and
  duplicate parameter names across tracks.
- `eval_orchestrator.cljs`: rewritten `patch-param-in-editor!`,
  `patch-fx-param-in-editor!`, and `patch-per-track-fx-param-in-editor!` to use
  the new scanner; extracted shared `fmt-num` and `dispatch-replace!` helpers.
- `samples.cljs`: added `fetch-ok!` helper that rejects with a descriptive error
  on non-2xx responses; applied to all four fetch chains (JSON manifest, Lisp
  manifest, GitHub tree API, audio buffer fetch).
- `rust-toolchain.toml` (new): pins `channel = "stable"` with
  `targets = ["wasm32-unknown-unknown"]` and `components = ["clippy", "rustfmt"]`,
  ensuring reproducible Rust builds and correct CI lint toolchain setup.
- `netlify.toml`: deleted (Netlify no longer used).
- `plugin_loading.cljs`: replaced `js/Function.` eval workaround with
  `js/window.__import__`; `index.html` exposes the global from a plain `<script>`
  tag so Closure never processes the `import()` syntax.
- `vercel.json`: removed `'unsafe-eval'` from CSP; cleaned build command to use
  `cargo install --locked wasm-pack@0.14.0` instead of curl-pipe-sh.

See full spec: [PROMPTS/PHASE-HRD1.md](PROMPTS/PHASE-HRD1.md)

---

## Phase R3 — JavaScript Purification 📋 *planned*

Move maintainable hand-written app logic from JavaScript into ClojureScript
without disturbing generated files, worklet boundaries, or the public plugin
authoring surface.

**Key additions:**
- Port CodeMirror language-support helpers from `app/src/repulse/lisp-lang/*.js`
  to CLJS equivalents
- Keep generated Lezer parser output, wasm-pack glue, externs, AudioWorklets,
  and public plugin examples as intentional JavaScript boundaries
- Update editor/app imports to consume CLJS language support directly
- Document the remaining JS boundary rationale in `docs/ARCHITECTURE.md`

See full spec: [PROMPTS/PHASE-R3.md](PROMPTS/PHASE-R3.md)

---

## Phase PLUG1 — Drop-In Plugin Packages 📋 *planned*

Let users install trusted third-party plugins by dragging local `.js` files or
`.repulse-plugin.zip` packages onto the app.

**Key additions:**
- Plugin package manifest format with `id`, `name`, `version`, `apiVersion`,
  `type`, `entry`, permissions, and assets
- Drag/drop install flow with explicit trust dialog before executing code
- IndexedDB persistence plus enable, disable, reload, and remove controls
- Host APIs for packaged assets and AudioWorklet modules while preserving the
  existing effect/visual plugin protocols

See full spec: [PROMPTS/PHASE-PLUG1.md](PROMPTS/PHASE-PLUG1.md)


---

## Phase AI1 — AI-Ready Knowledge Base 📋 *planned*

Machine-readable documentation of REPuLse's language, vocabulary, and live session state —
the foundation any AI assistant needs to write correct REPuLse-Lisp. Ships standalone;
serves both the future in-app co-pilot (AI2+) and external agents using this repo today.

**Key additions:**
- `docs/ai/builtins.json` — one entry per built-in with `category`, `signature`,
  `returns`, `side-effects`, `examples`, and `see-also`; generated by `scripts/gen_ai_docs.mjs`
  from `completions.js` + `builtin_meta.edn` + the Lezer grammar
- `docs/ai/concepts.json` — hand-authored: pattern model, rational time, transformer vs.
  pattern, `->>` semantics, mini-notation EBNF, all value types
- `docs/ai/session-schema.json` — JSON Schema for the AI session snapshot (track names,
  BPM, FX names, bank, sources — no editor code)
- `docs/ai/cookbook.json` — structured cookbook recipes tagged by musical goal
- `app/src/repulse/content/builtin_meta.edn` — enriched metadata companion to `completions.js`
- `(help-export)` Lisp built-in — emits the current session as the AI session snapshot;
  omits editor code by default
- `npm run gen:ai-docs` + `check:ai-docs` scripts; CI `ai-docs` drift-check job
- `CLAUDE.md` Rule 4 — AI docs maintenance step alongside `gen:grammar`

See full spec: [PROMPTS/PHASE-AI1.md](PROMPTS/PHASE-AI1.md)

---

## Phase AI2 — Assistant Panel & Providers 📋 *planned*

In-app AI chat panel with bring-your-own-key support for OpenAI, Anthropic, Google, and
Groq — streaming responses, message persistence, and session context injection. Opt-in
feature gate; no tool calling yet.

**Key additions:**
- `app/src/repulse/ai/settings.cljs` — localStorage-backed atoms: `enabled?`, `provider`,
  `api-key`, `model-override`, `include-code?`; settings modal accessible from the header
- `app/src/repulse/ai/client.cljs` — provider abstraction with `stream!` fn; `parse-delta`
  handles SSE format differences across OpenAI / Anthropic / Google / Groq
- `app/src/repulse/ai/system_prompt.cljs` — assembles context from `/docs/ai/builtins.json`
  summary + current `(help-export)` snapshot (track names, BPM, FX — no editor code)
- `app/src/repulse/ui/assistant_panel.cljs` — collapsible right-side panel; streaming
  render; code-block "↓ insert" and "↺ replace selection" buttons; cost/token indicator
- `(ai)` / `(ai "prompt")` Lisp built-ins — open panel or send a one-shot prompt
- `repulse:ai:*` localStorage namespace — keys, history (last 50 turns), feature flag

See full spec: [PROMPTS/PHASE-AI2.md](PROMPTS/PHASE-AI2.md)

---

## Phase AI3 — Tool-Using Agent 📋 *planned*

Promote the assistant from chat to agent: it can read the editor buffer, query session
state, browse snippets, propose unified-diff edits, and silently preview evaluated code
— all with explicit user confirmation before any change is applied.

**Key additions:**
- `app/src/repulse/ai/tools.cljs` — typed tool registry: `read_buffer`, `propose_edit`
  (unified diff), `eval_preview` (silent off-graph audio context), `query_session`,
  `query_track`, `find_snippet`, `insert_snippet`, `set_bpm_proposal`
- `app/src/repulse/ai/agent_loop.cljs` — bounded agent loop (max N tool calls per turn)
  with cancel button; function-calling adapters for OpenAI and Anthropic tool-use schemas
- Edit-proposal diff overlay in the editor with "Apply / Reject" buttons; rejections fed
  back as model feedback
- `eval_preview` runs against an off-graph silent gain node — assistant "listens" without
  touching the user's session; returns scheduled-event count + duration-bars summary
- Snippet integration: `find_snippet` searches community library, `insert_snippet` uses
  the same path as manual insert with usage tracking

See full spec: [PROMPTS/PHASE-AI3.md](PROMPTS/PHASE-AI3.md)

---

## Phase AI4 — Assistant Safety & Limits 📋 *planned*

The trust and economics layer: hard token + tool-call budgets, prompt-injection guards
for untrusted content the assistant reads, auto-apply toggle with full undo, and an
optional Supabase server-relay for users who want encrypted key storage.

**Key additions:**
- Hard token + tool-call budget per session; soft warning at 50%, hard stop at 100%
  with one-click raise; budget persisted per provider
- Prompt-injection guards — snippet text, sample manifest text, and any external content
  the assistant reads wrapped in `<untrusted>` tags in the system prompt
- Auto-apply toggle (default off) — when on, every `propose_edit` lands immediately and
  records to an undo stack with a "revert assistant turn" button
- Per-provider rate limiting + retry-with-backoff; provider errors surfaced inline
- Optional server-relay — keys stored in Supabase encrypted user settings (S2 backend)
  instead of localStorage; toggled in settings modal
- Activity log panel — last 50 tool calls with payloads, exportable as JSON for debugging

See full spec: [PROMPTS/PHASE-AI4.md](PROMPTS/PHASE-AI4.md)

---

## Future ideas (unscheduled)

See [docs/FUTURE-FEATURES.md](docs/FUTURE-FEATURES.md) for the full prioritised feature
backlog — tiered by impact and implementation cost.
