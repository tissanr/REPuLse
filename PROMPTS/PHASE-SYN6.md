# Phase SYN6 — Synth Essentials: Oscillators, Drum Kit & Synth Instruments

> **Spec status:** full (expanded from the outline on 2026-11-14). This phase is large on
> purpose: it holds everything a song needs from the built-in synthesizer in one phase,
> built in four parts (A–D) that are each testable on their own.
>
> **Automation:** 🤖👂 auto + human check — aliasing, tuning, detune, envelopes and glide
> are measurable with FFT assertions in offline renders; whether the kit and the
> instruments sound good needs a human listen (see "Human listen check").

**Milestone:** 1 — Tidal-Lisp core (sound side track)
**Depends on:** parts A, B and D: none. Part C: TL7a (per-voice filter, `shape`) and
TL7c (filter envelope, vibrato). **Part C is blocked until both have landed.** TL7c is
still an outline; Part C names exactly what it needs from it (see "Part C — what it
needs from TL7a and TL7c"). Coordinates with KIT1 (recorded kits) and SYN4 (`tone`).

## Questions for the owner

The spec is complete without these answers: each has a default that the implementer
follows unless the owner says otherwise before implementation starts. They are listed
because they change existing behaviour or a name the outline used.

1. **`:synth_bd` naming (unconfirmed in the outline).** Default: a single synthesized
   hit inside a sampled kit is written `:synth_bd`, `:synth_cp`, … — the name
   `(bank :synth)` would produce through the existing prefix mechanism. Alternative:
   no single-hit syntax at all (only `(bank :synth)`).
2. **`sub` is already taken.** `sub` is the TL1 arithmetic function
   (`(sub [mode] amt pat)`, grammar line 43, `completions.js` line 101). The outline's
   `(sub 0.5)` for the sub-oscillator level cannot be registered. Default: the parameter
   is **`sub-osc`**. `ROADMAP.md` still says `sub` in the SYN6 bullets; the delivery
   commit corrects it.
3. **Two kicks for one name.** The outline forbids changing what bare `:bd :sd :hh :oh`
   play when no synth bank is selected. Default: the four legacy engine voices stay
   bit-identical as the no-sample fallback, and the new kit is reached through
   `(bank :synth)` / `:synth_*`. Consequence: without samples, `(seq :bd)` and
   `(bank :synth) (seq :bd)` sound different. Alternative: retire the legacy voices and
   let the fallback use the new kit (changes the fallback sound and the EXP1 e2e
   baselines in `e2e/export-parity.spec.ts`).
4. **`(synth :noise pat)` is fixed.** Today it never plays noise: a note event plays a
   sine at that pitch and a rest stays silent (the `:noise` branch of `play-event` is
   only reached by the map `(noise)` returns). USAGE.md's example
   `(->> (seq :_ :_ :_ :_) (synth :noise))` is therefore silent. Default: every non-rest
   event plays noise, rests stay rests, and the USAGE example is rewritten.
5. **Editor highlighting of common names.** Rule 3 registration puts `pad`, `lead`,
   `strings`, `reed`, `pluck`, `acid` into the grammar's `BuiltinName`, so a user symbol
   of the same name (`(def pad …)`) is highlighted as a built-in, as `bell`, `harp` and
   `flute` are today. Evaluation is unaffected. Default: follow Rule 3 unchanged.

Not a question, but the owner should know: on a track with its own `(fx …)` chain every
WASM voice plays through its JS fallback (EXP1 decision E1, USAGE.md "WASM synths on a
track with its own `(fx …)`"). That holds for the SYN6 voices too. The JS fallbacks
below are therefore real approximations, not placeholders, but hard sync, PWM movement,
the hat choke and the drum models are WASM-only. Routing the worklet through per-track
FX is its own follow-up phase and is not part of SYN6.

## Goal

Make the built-in synthesizer complete enough to write a whole song without samples:
clean oscillators, a full drum kit, and the standard synth instruments by name.

Today the WASM `:saw` and `:square` voices alias on high notes, there is no triangle,
unison or coloured noise, the synthesized drums are four fixed voices that can't be
selected once samples have loaded, and a bass or pad has to be built by hand.

```lisp
;; Before
(->> (seq :c7 :e7 :g7) (synth :saw))            ; harsh, inharmonic aliasing
(->> (seq :c4) (synth :tri))                    ; no such voice — plays a sine, no warning
(seq :bd :cp :bd :cp)                           ; :cp is a sample or nothing
(->> (seq :c4 :e4) (synth :trumpet-cup))        ; documented in USAGE.md, plays a sine

;; After
(->> (seq :c7 :e7 :g7) (synth :saw))            ; clean
(->> (chord :minor :a3) (synth :supersaw) (detune 18) (spread 0.8))
(->> (seq :a1 :a1 :c2 :e1) (synth :saw) (unison 3) (detune 12) (sub-osc 0.5))

(bank :synth)                                   ; bare drum names use the synth kit
(seq :bd :cp :bd [:cp :rim])
(->> (seq :bd :bd :bd :bd) (tune (seq 0 0 -5 0)) (decay 0.8))
(->> (fast 4 (seq :hh :hh :oh :hh)) (tone (range 0.2 0.9 sine)))

(->> (seq :c2 :c2 :eb2 :g1) (synth :acid) (glide (seq 0 0 0.06 0)))
(->> (chord :minor7 :d3) (synth :strings) (attack 0.4))
(->> (chord :major :f3) (synth :pad) (lpf 900))  ; preset values stay overridable

(->> (seq :c4) (synth :trii))
;; => Warning: unknown synth voice :trii — did you mean :tri?
```

## Decisions already made (2026-11-13)

- **Synth drums get their own bank: `(bank :synth)`.** Samples keep priority for bare
  names when no bank is selected; the synthesized voices stay the fallback while samples
  have not loaded. `(bank :synth)` makes bare drum names skip the sample lookup.
- **The oscillators are band-limited at the source** (PolyBLEP). A filter after the
  oscillator cannot remove aliasing (see Background), so this is not left to the user.
- **An unknown `(synth :name)` is a warning**, like an unknown sample name. Playback
  continues.
- **One phase.** SYN6 is not split into several phases. It may be delivered in two PRs
  (see "Build order").

## Design decisions (2026-11-14)

These answer the outline's open design questions. Each is based on the code as it is on
`dev`; the reason is given so a later reader can tell when it stops holding.

1. **Unison is one voice with stereo output, not N mono voices.** `Voice::tick` gets a
   stereo sibling `tick2(sr) -> (f32, f32)`; mono voices return `(s, s)`, so the engine
   keeps one code path and existing voices stay bit-identical. Expanding a unison note
   into N `ActiveVoice`s was rejected: TL7a puts a per-voice insert chain between `tick`
   and the pan, so N voices would run N filters, and `shape`/`crush` would distort each
   oscillator separately instead of the sum (a different sound). N voices also multiply
   the per-sample pan trigonometry in `process_block_raw` (lines 843–848). The cost of
   7 oscillators in one voice is measured, not guessed (benchmark in "Tests").
2. **Unison start phases are a fixed table, not random.** Phase of oscillator *i* is
   `frac(i · 0.618034)`. This needs no RNG state, keeps `tick` allocation-free, makes
   every note identical (random phases change the low-end level of a bass from note to
   note) and makes offline renders bit-identical without seeding rules. Whether a
   repeated supersaw chord sounds too static is a listen-check item; the fallback is
   start phases drawn from `next_seed()` in `activate`.
3. **Band-limiting changes `:saw` and `:square`, and that is accepted.** PolyBLEP alters
   the two samples around each edge, and the pulse loses its DC offset for widths
   ≠ 50 %. The PR states the measured RMS and spectrum difference (test A6).
4. **Voice parameters travel in the trigger string as named fields; `trigger_v3` slots
   stay reserved for the voice-independent chain.** `"saw:440:unison=3:detune=12"`,
   `"drum:bd:tune=-5:decay=0.8"`. Reasons: (a) parts A and B must not depend on TL7a;
   (b) TL7a's slot layout is append-only and TL7b and TL7c already append to it — a
   fourth phase appending slots makes the slot numbers depend on merge order;
   (c) the code already splits this way: the string says *which source and how it is
   built* (`"square:<hz>:<pw>"`, `"fm:<hz>:<index>:<ratio>"`), the numeric arguments say
   *how the voice is shaped afterwards* (amp, envelope, pan, later inserts and sends);
   (d) a named field can be absent, which a drum's `decay` needs ("use the model's own
   decay"), while a slot has only a default value. Fields are order-independent and
   optional, so every existing string keeps working.
5. **One `Voice::Drum` with a model enum and a preset table**, not one enum variant per
   drum. Eighteen voices reduce to four DSP models that share oscillators, noise and
   filters. A table `drum_preset(name)` matches `fm_preset`/`ks_preset`, keeps `tick`
   and `is_silent` readable, and gives Rust its one list of drum names. All state is
   inline (fixed arrays), so nothing allocates in `tick`.
6. **The new drum models are not ported to JavaScript.** The JS fallback maps each kit
   voice to the nearest existing JS voice with adjusted constants (table in Part B). The
   real models are tested in Rust, and the full path (Lisp → dispatch → WASM) is tested
   through the EXP1 offline export in `e2e/export-parity.spec.ts`, which renders the
   real engine. Reason: a second hand-written implementation of 18 voices is exactly
   the duplication TL7a's decision 1 rules out, and the TEST1 harness (`offline-js`)
   only needs the right class of sound and the right onsets.
7. **`:synth` as a bank name.** Reserved are the prefix `(bank :synth)` and the 18 names
   `synth_<drum>`. A sample bank that is itself called `synth` is not affected:
   `(seq :synth)` and `(sound :synth 2)` play it as before, because the prefix
   mechanism only ever looks at `synth_<name>`. If a manifest registers a bank named
   `synth_bd`, the synthesized voice wins for keyword addressing and the sample stays
   reachable with `(sound :synth_bd 0)` (a `(sound …)` map always uses samples, see
   `play-event` line 576). KIT1's `(kit …)` selects multisampled kits and is
   independent: `(bank :synth)` wins for the 18 drum names while it is active.
8. **`tone` is one shared 0–1 brightness parameter** (event key `:tone`). Each voice
   documents what it maps to. Deferred SYN4 uses the same key for the guitar tone
   control. Reason: it is the same idea (a tone knob), and one name per voice family
   would multiply built-ins. Absent means "the voice's default", not 0.5.
9. **Part C presets are a ClojureScript table expanded in `play-event`** (option (b) of
   the outline). A preset is a map of ordinary event parameters merged *under* the
   event's own keys, so explicit parameters win without extra code, the preset works on
   every path (WASM, JS fallback, offline export), and the engine needs no preset
   table. Rule that follows: **a preset may only use parameters a user could write by
   hand.** Presets whose parameters are engine-internal (FM index and ratio, body
   filters, string feedback) stay Rust tables (`fm_preset`, `ks_preset`); that is where
   `:trumpet-cup` goes.
10. **Glide takes its start pitch from the scheduler, per track, nearest note.** The
    scheduler keeps, per track, the pitches of the most recent onset group (all events
    with the same onset time) and of the group before it. A new note glides from the
    member of the previous group that is nearest in pitch. A single line glides from
    its last note, and a chord glides voice by voice into the next chord. The pattern
    engine stays pure. State is cleared by `(stop)` and `clear-track!`.
11. **`:wobble` uses a per-voice cutoff LFO, phase-locked to the cycle.** Two
    parameters, `lp-rate` (LFO periods per cycle) and `lp-depth` (octaves), extend the
    TL7c cutoff modulation. The generic `lfo` modulator (parity row ❓) stays undecided;
    nothing here blocks it. The scheduler computes the start phase from the onset time,
    so consecutive notes share one continuous wobble.
12. **If TL7a or TL7c is late:** parts A, B and D ship first in one PR; Part C follows
    in a second PR. `ROADMAP.md` keeps SYN6 at 📋 *planned* with a note
    "parts A, B, D delivered in #NNN" until Part C has landed and been listened to.

## Background

Line numbers refer to `dev` at the time of writing (2026-11-14).

### `packages/audio/src/lib.rs`

- `struct Biquad` (lines 14–112): Direct Form I, constructors `bandpass`, `highpass`,
  `peaking_eq`, `highshelf`; no `lowpass` (TL7a adds it).
- `lcg_next` (116): white noise in −1…1 from a `u32` state.
- Preset tables: `fm_preset` (121), `fm_body_filters` (141), `fm_noise_amp` (179),
  `ks_preset` (193), `ks_body_filters` (209). `decay_rate(dur, sr)` (257) returns the
  per-sample multiplier that reaches 1e-5 (−100 dB) after `dur` seconds. So "decay"
  everywhere in this spec is the time to −100 dB; −60 dB is reached after 0.6 × decay.
- `enum Voice` (264): `Kick` (265), `Snare` (273), `Hihat` (283), `Tone` (289), `Saw`
  (298), `Square` (307), `Noise` (317), `FM` (322), `KarplusStrong` (340).
  `Voice::tick(&mut self, sr) -> f32` (356); the naive oscillators are
  `2.0 * (phase % 1.0) - 1.0` (441) and `if p < pulse_width { 1 } else { -1 }` (465).
  `is_silent` (552).
- **Why filtering does not fix aliasing:** a naive saw at 3520 Hz has harmonics far
  above the Nyquist frequency (22 kHz). They fold back *down* into the audible range at
  frequencies that are not multiples of the note (the 12th harmonic, 42.2 kHz, lands at
  1.9 kHz). These tones sit between the wanted harmonics, so a low-pass that removes
  them also removes the sound.
- `struct ActiveVoice { voice, pan }` (580); `struct Pending { time, value, amp, attack,
  decay, pan }` (587); `AudioEngine` (660) with `noise_seed`; `trigger` (716),
  `trigger_v2` (728), `stop_all` (757); test helpers `new_for_test` (766),
  `trigger_raw` (778), `trigger_raw_v2` (790).
- `process_block_raw` (810): per sample it activates due events, then for every voice
  `av.voice.tick(sr)` → equal-power pan → sum (843–848), then the master transitions
  and a clamp to ±1.
- `activate` (878): parses the value string. `"saw:<hz>"` (884), `"square:<hz>[:<pw>]"`
  (897), `"noise"` (917), `"fm:…"` (923), `"ks:…"` (977), then `"bd" "sd" "hh" "oh"`
  (1014–1046) with constants fixed here, and finally any other string as a sine at that
  frequency, 440 Hz if it does not parse (1047). Oscillator peak is `amp × 0.5`. The
  drum voices ignore `attack` and `decay`.
- Tests: `mod engine_tests` (1151) with `rms`/`rms_stereo` helpers,
  `fm_presets_all_produce_output` (1235, its own list of names),
  `karplus_strong_all_presets_produce_output` (1284, another list).

### `app/src/repulse/audio.cljs`

- JS fallback voices on native nodes: `make-kick` (236), `make-snare` (253),
  `make-hihat` (277, arities with `dur gain-scale hpf-freq`), `make-sine` (305),
  `make-saw` (336), `make-pulse-wave` (355), `make-square` (374), `make-noise` (397),
  `js-synth` (449). Native `OscillatorNode`s are band-limited already.
- `worklet-trigger!` (428) and `worklet-trigger-v2!` (438) send `trigger` /
  `trigger_v2` messages, **only when `dest` is the master gain**. A track with an FX
  chain gets the JS fallback (decision E1 of EXP1).
- `play-event` (469). `offline?` is true only for an `OfflineAudioContext` without a
  registered graph (the TEST1 harness); the EXP1 export registers a graph and uses the
  worklet. Inside the parameter-map branch (498): `:saw` (509), `:square` with `:pw`
  (515), `:fm` (523), a literal set of FM preset names (533), a literal set of string
  preset names (542), then the note/sample dispatch (551–567), which resolves a
  non-note keyword with `samples/resolve-keyword` and plays the sample if
  `samples/has-bank?`, else the engine or `js-synth`. Any `:synth` value not listed
  falls through to that dispatch and plays a sine. Bare keywords: 589–602.
  `{:synth :noise}` without `:note`: 605.
- Scheduler: `scheduler-state` (740), `schedule-event!` (849, calls `play-event` with
  `{:dur :cycle-dur}`), `schedule-cycle!` (886, iterates `core/query-onsets` per track;
  the events of a cycle are **not** sorted by time), `stop!` (1104), `clear-track!`
  (1161). Nothing carries the previous note's pitch to the next event.

### Other files

- `app/src/repulse/samples.cljs`: `active-bank-prefix` (17), `set-bank-prefix!` (24),
  `has-bank?` (113), `resolve-keyword` (118: with prefix `X`, `:bd` becomes `:X_bd`
  only if that bank is registered). With `(bank :synth)` today nothing changes,
  because no bank `synth_bd` exists. `play!` (289) is the only place with choke groups
  (`cut`, TL6).
- `app/src/repulse/env/builtins/samples.cljs`: the `bank` built-in (18).
- `app/src/repulse/sound_check.cljs`: `builtin-drums` (16), `sound-keywords` (38),
  `suggest` (56), `unknown-sounds` (70), `warning-message` (82), `keyword-ranges` (90).
  It skips maps that have `:synth`. Called from `report-unknown-sounds!` in
  `app/src/repulse/eval_orchestrator.cljs` (118).
- Parameter functions: `packages/core/src/repulse/params.cljs` — `apply-param` (18),
  `amp` (37), `decay` (53), `pan` (68); registered in
  `packages/lisp/src/repulse/lisp/builtins/params.cljs` (`param` helper, line 9).
  `synth`, `noise`, `fm`: `packages/lisp/src/repulse/lisp/builtins/synth.cljs`;
  `(synth :square :pw 0.25 pat)` merges its options into every event map.
- Editor and AI registration: `app/src/repulse/lisp-lang/repulse-lisp.grammar`
  (`BuiltinName`, lines 30–74; voices on 61–64), `completions.js` (voices are
  `type: "keyword"` entries, e.g. `sax` on line 223), `hover.js`,
  `insert-categories.js`, `keywords-completion.js` (completion after `:`),
  `app/src/repulse/content/builtin_meta.edn` (`:synth` 805, `:noise` 988, `:sax` 1062,
  `:bank` 1358). `scripts/gen_ai_docs.mjs` writes `docs/ai/builtins.json` for names
  that are in the grammar **and** in `completions.js`.
- Tests: `app/src/repulse/test_api.cljs` — `window.__REPULSE_TEST__` with
  `evalToEvents`, `renderOffline` (backend `"offline-js"`), `registerTestSample`;
  `schedule-pattern!` (77) calls `play-event` directly. `e2e/audio-render.spec.ts`
  (TEST1) and `e2e/export-parity.spec.ts` (production app, `(export N)` through the
  WASM worklet, manifests blocked so the engine drums play). Helpers:
  `e2e/helpers/audio-analysis.ts` (`rms`, `goertzel`, `rmsPerWindow`, `toDb`, …).
  CLJS unit tests run on Node (`npm run test`), listed in
  `packages/core/src/repulse/test_runner.cljs`.
- `app/src/repulse/offline_render.cljs` schedules export events through `play-event`
  (294).
- Docs: `docs/USAGE.md` — "Built-in synthesis fallback" (1933), "`synth`" (2121) with
  the voice table (2145) and the FM preset table (2162; `:trumpet-cup` on 2172 is not
  implemented, `:synth-brass`, `:harmon-out`, `:harmon-in` are missing).
  `docs/STRUDEL-PARITY.md` has `detune`/`unison`/`spread` as ❓ (line 414) and a second
  ❓ row that reserves the name `spread` for Tidal's `spread` combinator (478).
  `docs/SYNTHESIS-COVERAGE.md` marks this phase's rows 🆕.
- `defsynth` (`app/src/repulse/synth.cljs`) has `sin saw square tri noise` UGens on
  native nodes; untouched here.
- Tidal Drum Machines sample names: `bd sd hh oh cp rim lt mt ht cb cr rd sh tb`. Using
  the same names lets one pattern switch between sampled and synthesized kits.

## Parameters and voices (overview)

New per-event parameter functions. All have the two-arity shape of `amp`
(`(f v)` → transformer, `(f v pat)`), accept patterns and signals, and are sampled once
per event at its onset. Core does not clamp; the audio layer does.

| Function | Event key | Range (clamped) | Default | Part | Meaning |
|---|---|---|---|---|---|
| `unison` | `:unison` | 1–7 (integer) | 1 | A | number of oscillators |
| `detune` | `:detune` | 0–200 cents | 20 when `unison` > 1, else 0 | A | total width of the unison stack |
| `spread` | `:spread` | 0–1 | 0 (supersaw: 0.7) | A | stereo width of the unison stack |
| `sub-osc` | `:sub-osc` | 0–1 | 0 | A | sine one octave below, relative to the note's fundamental |
| `pw` | `:pw` | 0.01–0.99 | 0.5 | A | pulse width (the `:pw` option of `synth` writes the same key) |
| `pw-rate` | `:pw-rate` | 0–20 Hz | 0 | A | pulse-width LFO rate, in Hz |
| `pw-depth` | `:pw-depth` | 0–0.49 | 0 | A | pulse-width LFO depth |
| `sync` | `:sync` | 1–16 | off | A | hard-sync ratio slave/master (`:saw`, `:pulse`) |
| `tune` | `:tune` | −48…48 semitones | 0 | B | pitch of a synthesized drum |
| `tone` | `:tone` | 0–1 | per voice | B | brightness / body–noise balance, per voice |
| `glide` | `:glide` | 0–5 s | 0 | C | portamento time from the previous note |
| `accent` | `:accent` | 0–1 | 0 | C | louder and brighter note (acid) |
| `lp-rate` | `:lp-rate` | 0–64 per cycle | 0 | C | cutoff LFO rate, periods per cycle |
| `lp-depth` | `:lp-depth` | 0–4 octaves | 0 | C | cutoff LFO depth |

`pw-rate` is in Hz because it is a timbre control like vibrato; `lp-rate` is in cycles
because a wobble is a rhythm.

`decay` (existing) is now honoured by the synthesized kit. Options of `synth` that are
not functions: `:color` (`:white :pink :brown :dust :lfsr`) and `:density` (dust
impulses per second, 1–2000, default 20) for `(synth :noise …)`.

New voices for `(synth …)`:

| Part | Voices |
|---|---|
| A | `:tri`, `:sine`, `:pulse` (alias of `:square`), `:supersaw` |
| C | `:acid` `:reese` `:sub-bass` `:fm-bass` `:wobble` `:lead` `:sync-lead` `:pluck` `:theremin` `:reed` `:pad` `:strings` `:sweep-pad` `:brass-pad` |
| D | `:trumpet-cup` |

Synthesized drum names (Part B, sound names, not `synth` voices):
`bd sd hh oh cp rim lt mt ht cb cr rd sh tb cl cg zap click`.

## Trigger string format

```
<voice>[:<positional>]…[:<key>=<value>]…
```

A field that contains `=` is a named field; the others are positional, in order. Named
fields may come in any order; an absent field means the default. Unknown keys are
ignored. The CLJS side writes a field only when the event has that key.

| Voice string | Positional | Named fields |
|---|---|---|
| `saw:` `tri:` `sine:` | frequency in Hz | `unison detune spread sub-osc sync glide-from glide` |
| `square:` | frequency, pulse width (kept for old callers) | the same plus `pw pw-rate pw-depth` |
| `noise` / `noise:` | — | `color` (name), `density`, `freq` (lfsr clock base) |
| `drum:<name>` | — | `tune tone decay` |
| `fm:…`, `ks:…`, `"bd" "sd" "hh" "oh"`, bare number | unchanged | — |

`:pulse` sends `square:`. `:supersaw` sends `saw:` with `unison=7` and its defaults.
`(synth :sine)` sends `sine:<hz>` (a `Voice::Osc`); a bare note keeps the bare-number
string and `Voice::Tone`, so patterns without `synth` render exactly as before.

Rust: one parser, used by every branch of `activate`:

```rust
/// Named field of a trigger string, e.g. arg("440:unison=3", "unison") == Some(3.0).
/// Walks rest.split(':') — no allocation.
fn arg(rest: &str, key: &str) -> Option<f32> { … }
fn arg_str<'a>(rest: &'a str, key: &str) -> Option<&'a str> { … }
/// n-th field without '='.
fn positional(rest: &str, n: usize) -> Option<&str> { … }
```

CLJS: one writer in the new namespace `repulse.voices` (see Part D):

```clojure
(defn voice-string
  "Pure. \"saw:440:unison=3:detune=12\" from a base, positional values and the
   event keys to pass on (only those present in value)."
  [base positionals value ks] …)
```

## Part A — Oscillator core

### A1. Band-limited waveforms

Free functions in `lib.rs`, so they can be tested without the engine. `t` is the phase
in [0, 1), `dt = freq / sr` the phase increment per sample.

```
poly_blep(t, dt):
    if t < dt:        x = t / dt;          return x + x − x·x − 1        // just after the edge
    if t > 1 − dt:    x = (t − 1) / dt;    return x·x + x + x + 1        // just before the edge
    return 0

saw(t, dt)       = 2t − 1 − poly_blep(t, dt)

pulse(t, dt, pw) = (t < pw ? 1 : −1)
                   + poly_blep(t, dt)                       // rising edge at t = 0
                   − poly_blep(frac(t + 1 − pw), dt)        // falling edge at t = pw
                   − (2·pw − 1)                             // remove the DC offset

poly_blamp(t, dt):
    if t < dt:        x = t / dt − 1;          return −x·x·x / 3
    if t > 1 − dt:    x = (t − 1) / dt + 1;    return  x·x·x / 3
    return 0

tri(t, dt):                                    // starts at 0, peak at t = 0.25
    y = 4t;  if y ≥ 3: y −= 4  else if y > 1: y = 2 − y
    return y + 4·dt·( poly_blamp(frac(t + 0.25), dt) − poly_blamp(frac(t + 0.75), dt) )

sine(t) = sin(2π·t)
```

- **Triangle method: PolyBLAMP on the naive triangle**, not an integrated square. It
  has no state, so it works unchanged under unison and glide and needs no leak
  constant or start value. The correction is positive at the trough and negative at
  the peak (it rounds the corners).
- The 2-point PolyBLEP multiplies the spectrum above Nyquist by sinc²(f / sr). That is
  the basis of the thresholds in test A1.
- `pw` is clamped to 0.01–0.99 as today. The DC term is new: the JS fallback
  (`make-pulse-wave`, `real[0] = 0`) already has none.

### A2. `Voice::Osc`

Replaces `Voice::Saw` and `Voice::Square`. All state inline:

```rust
#[derive(Clone, Copy, PartialEq)]
enum Wave { Saw, Pulse, Tri, Sine }

const MAX_UNISON: usize = 7;

struct OscVoice {
    wave: Wave,
    n: usize,                        // 1..=7
    phase: [f64; MAX_UNISON],
    ratio: [f64; MAX_UNISON],        // 2^(cents_i / 1200)
    wl: [f32; MAX_UNISON],           // stereo weights (see A3)
    wr: [f32; MAX_UNISON],
    norm: f32,                       // 1 / sqrt(n)
    freq: f64,                       // current frequency (moves while gliding)
    target: f64,
    glide_mul: f64,                  // per-sample multiplier, 1.0 when not gliding
    glide_left: u32,                 // samples of glide left
    pw: f32, pw_rate: f32, pw_depth: f32, lfo_phase: f32,
    sync: f64,                       // 0.0 = off, else slave/master ratio
    master: [f64; MAX_UNISON],       // master phases, used when sync > 0
    sync_sw: [f32; MAX_UNISON],      // slave phase at the last master wrap
    sub_phase: f64, sub_gain: f32,
    amp: f32, gain: f32, gain_decay: f32, attack_inc: f32, in_attack: bool,
}
// enum Voice { …, Osc(OscVoice), … }
```

Per sample: envelope exactly as `Voice::Saw` today (linear attack, then
`gain *= gain_decay`); if `glide_left > 0`: `freq *= glide_mul`, decrement, snap to
`target` at 0; for each oscillator `dt_i = freq · ratio[i] / sr`, compute the waveform,
advance `phase[i]` (wrap with `− floor`); sum into the two buses (A3); add the
sub-oscillator; multiply by `norm` and `gain`.

Peak amplitude stays `amp × 0.5` per oscillator as today, and the sum is scaled by
`1/√n`, so loudness is roughly independent of `unison`.

```rust
impl Voice {
    /// Stereo tick. Mono voices return the same sample twice, so the engine's
    /// `l += a * cos; r += b * sin` is today's arithmetic for them.
    fn tick2(&mut self, sr: f32) -> (f32, f32) {
        match self {
            Voice::Osc(o) => o.tick2(sr),
            _ => { let s = self.tick(sr); (s, s) }
        }
    }
}
```

`process_block_raw` calls `tick2` instead of `tick`. `is_silent` for `Voice::Osc` is the
rule of `Voice::Saw` today (`!in_attack && gain < 1e-5`).

### A3. Unison: detune, phase and pan layout

For oscillator `i` of `n` (`i = 0 … n−1`, sorted from lowest to highest pitch), with
`k = i − (n−1)/2` and `h = (n−1)/2`:

```
u_i      = k / h                                   // −1 … 1   (n = 1: u = 0)
cents_i  = (detune / 2) · sign(u_i) · |u_i|^1.5    // ±detune/2 at the edges, denser near the centre
phase_i  = frac(i · 0.618034)
q_i      = spread · |u_i| · sign(k) · (−1)^(ceil(|k|) + 1)   // pan −1 … 1, alternating outwards
wl_i     = √2 · cos((q_i + 1) · π/4)
wr_i     = √2 · sin((q_i + 1) · π/4)
```

Worked rows (detune 20, spread 1):

| n | cents | pan q |
|---|---|---|
| 2 | −10, +10 | −1, +1 |
| 3 | −10, 0, +10 | −1, 0, +1 |
| 7 | −10, −5.44, −1.92, 0, +1.92, +5.44, +10 | −1, +0.67, −0.33, 0, +0.33, −0.67, +1 |

- The exponent 1.5 makes the spacing uneven, so the oscillator pairs do not all beat at
  the same rate (equal spacing gives one loud, regular beat).
- Pans alternate left and right going outwards and mirror around the centre, so low and
  high pitches are not sorted from left to right.
- `wl² + wr² = 2` for every oscillator and `wl = wr = 1` at `q = 0`. So `spread 0`
  gives two identical buses equal to the mono sum, and the total power does not depend
  on `spread`.
- The buses are `A = Σ osc_i · wl_i` and `B = Σ osc_i · wr_i`. `tick2` returns
  `(A, B)`. The engine then applies the event pan as before:
  `l += A · cos(angle)`, `r += B · sin(angle)`. For mono voices `A == B`, which is
  today's arithmetic.
- `:supersaw` = `:saw` with defaults `unison 7`, `detune 20`, `spread 0.7`; explicit
  parameters override them.

### A4. Sub-oscillator

A sine at half the note frequency, added equally to both buses **after** the `1/√n`
scaling and under the same envelope. Its gain relative to one oscillator is
`sub-osc × c`, where `c` is the amplitude of the waveform's fundamental: saw 0.637 (2/π), pulse 1.273 (4/π, the 50 % value at any
width), triangle 0.811 (8/π²), sine 1. So `(sub-osc 0.5)` is always 6 dB below the
note's fundamental, whatever the waveform.

### A5. Pulse-width modulation

`pw(t) = clamp(pw + pw_depth · sin(2π · lfo_phase), 0.01, 0.99)`, `lfo_phase` starts at
0 for each voice and advances by `pw_rate / sr`. All unison oscillators share the LFO.
JS fallback: static `pw` (a `PeriodicWave` cannot move); documented.

### A6. Hard sync

`sync` > 0 on `:saw` or `:pulse`: `master[i]` runs at the note frequency, the audible
(slave) oscillator `phase[i]` at `sync ×` that. When the master wraps, the slave is
reset. The reset is a step, corrected with a PolyBLEP on the *master* phase, scaled by
the height of the step:

```
m, dtm  = master phase and increment;   s, dts = slave phase and increment
before the wrap (m > 1 − dtm):
    τ    = (1 − m) / dtm                      // samples until the wrap
    s_w  = frac(s + dts · τ)                  // slave phase at the wrap; store in sync_sw
after the wrap: slave phase = dts · (m_new / dtm), where m_new = m + dtm − 1

saw:    y = saw(s, dts)        − s_w · poly_blep(m, dtm)          // drop of 2·s_w
pulse:  y = pulse(s, dts, pw)  + (s_w ≥ pw ? 1 : 0) · poly_blep(m, dtm)   // rise of 2, or none
```

`sync` is ignored for `:tri` and `:sine` (a triangle reset needs both a step and a
slope correction; not worth it here) and in the JS fallback. With `sync`, the pitch you
hear is still the note; the ratio moves the bright formant.

### A7. Noise colours

`Voice::Noise` gains a colour and its filter state (inline). `w` is `lcg_next`.

| Colour | Algorithm | Output scale |
|---|---|---|
| `white` | `w` (unchanged) | 1 |
| `pink` | Paul Kellet's filter: `b0 = 0.99886·b0 + w·0.0555179`; `b1 = 0.99332·b1 + w·0.0750759`; `b2 = 0.96900·b2 + w·0.1538520`; `b3 = 0.86650·b3 + w·0.3104856`; `b4 = 0.55000·b4 + w·0.5329522`; `b5 = −0.7616·b5 − w·0.0168980`; `out = b0+b1+b2+b3+b4+b5+b6 + w·0.5362`; `b6 = w·0.115926` | 0.11 |
| `brown` | one-pole low-pass at 20 Hz: `a = exp(−2π·20/sr)`; `y = a·y + (1 − a)·w` | `√((1 + a)/(1 − a))` (same RMS as white; 26.5 at 44.1 kHz) |
| `dust` | each sample: with probability `density / sr` output `w` (a random-height impulse), else 0. Uses the same LCG for the decision and the height (two draws). | 1, and the voice gain is `amp` instead of `amp × 0.3` |
| `lfsr` | 15-bit shift register, `bit = (r ^ (r >> 1)) & 1; r = (r >> 1) | (bit << 14)`, clocked at `clock` Hz, output ±1 from bit 0, held between clocks. `clock = min(note Hz × 16, sr/2)`; 8000 Hz when the event has no pitch. Start value = the voice's seed with bit 0 forced to 1. | 1 |

The pink coefficients are tuned for 44.1 kHz; at 48 kHz the slope is the same and the
curve shifts by an eighth of an octave. A brown corner at 20 Hz (not the common 137 Hz
leaky integrator) keeps the slope at 6 dB per octave down to 100 Hz. LFSR noise is
meant to be gritty; it is not band-limited.

### A8. Dispatch and JS fallback (`audio.cljs`)

`play-event`, parameter-map branch: replace the `:saw` and `:square` cases by one case
for the oscillator voices, driven by `repulse.voices` (Part D):

```clojure
(voices/osc-voice? synth)                       ; :saw :square :pulse :tri :sine :supersaw
(let [hz  (note-hz note)
      v   (voices/with-defaults value)          ; fills supersaw defaults
      s   (voices/osc-string v hz)]             ; "saw:440:unison=7:detune=20:spread=0.7"
  (or (when-not offline?
        (worklet-trigger-v2! wt s t amp-v attack-v decay-v pan-v dest))
      (make-osc ac t hz decay-v amp-v attack-v pan-v dest v)))
```

JS fallback, native nodes only:

- `make-osc`: for each unison oscillator one `OscillatorNode` (`"sawtooth"`,
  `"square"` or the `make-pulse-wave` wave, `"triangle"`, `"sine"`) with its `detune`
  AudioParam set to `cents_i`, into a `StereoPannerNode` at `q_i`, all into one gain
  envelope (the code of `make-saw`), then the event panner. Gain per oscillator
  `1/√n`. The detune and pan layout come from one pure function
  `voices/unison-layout` (same formulas as A3), unit-tested against the worked rows.
- Sub-oscillator: one more sine `OscillatorNode` at `hz / 2`.
- Glide (Part C): `setValueAtTime(from, t)` then
  `exponentialRampToValueAtTime(hz, t + glide)` on each oscillator's frequency.
- Not available in JS: `sync`, `pw-rate`/`pw-depth`. They are ignored.
- `make-saw` and `make-square` become thin callers of `make-osc` or are removed.
- Noise: `(= synth :noise)` becomes a case of the parameter-map branch, **before** the
  note dispatch, so `(synth :noise pat)` plays noise for every non-rest event
  (question 4). `make-noise` takes the colour and fills its buffer with the formula
  from A7 (it already fills a buffer in a loop; `Math.random` stays). The existing
  branch for `{:synth :noise}` without `:note` (605) calls the same code. `(noise)`
  accepts the options too: `(noise :color :pink)`.

## Part B — Synthesized drum kit

### B1. Selection (`repulse.voices`, `samples.cljs`, `audio.cljs`)

One pure function decides how a non-note keyword plays:

```clojure
(defn drum-route
  "Pure. kw — a non-note keyword. prefix — active bank prefix (string or nil).
   has-bank? — (fn [kw]) → bool.
   → {:kind :synth-drum :name \"bd\"}     engine kit voice (\"drum:bd…\")
     {:kind :sample :bank kw}             play this sample bank
     {:kind :legacy-drum :name \"bd\"}    one of the four old engine voices
     {:kind :unknown}                     440 Hz sine, as today"
  [kw prefix has-bank?] …)
```

Rules, in this order:

1. The name is `synth_<d>` and `<d>` is one of the 18 drum names → `:synth-drum <d>`.
   Always, whatever bank is active, even if a sample bank of that name exists.
2. The active prefix is `"synth"` and the name is one of the 18 → `:synth-drum`.
3. Otherwise as today: `resolved = (samples/resolve-keyword kw)`; if
   `(has-bank? resolved)` → `:sample resolved`.
4. No sample: the name is `bd`, `sd`, `hh` or `oh` → `:legacy-drum` (strings `"bd"` …,
   unchanged sound, question 3). Another of the 18 names → `:synth-drum`. Anything
   else → `:unknown`.

`play-event` uses `drum-route` in both keyword paths (551–567 and 589–602).
`:synth-drum` sends `(voices/drum-string name value)`, e.g.
`"drum:bd:tune=-5:decay=0.8"`, with `worklet-trigger-v2!` (amp and pan as usual;
`attack` unused). `tune`, `tone` and `decay` only act on `:synth-drum` hits; samples
and legacy hits ignore them, as they ignore `decay` today.

`(sound :bd 2)` always plays a sample, also under `(bank :synth)`. `(bank nil)` clears
the synth kit like any other prefix. The `bank` built-in, the session save and the
status-head badge need no change: `"synth"` is just a prefix value.

`sound_check.cljs`: `builtin-drums` becomes the 18 names (from `repulse.voices`), and
`synth_<d>` names count as known, so neither warns.

### B2. `Voice::Drum`

```rust
#[derive(Clone, Copy)]
enum DrumModel { Membrane, Snare, Metal, NoiseBurst }

/// Constant description of one drum voice. `tone` and `tune` are applied in activate.
struct DrumPreset { model: DrumModel, /* model constants, see tables */ }

fn drum_preset(name: &str) -> Option<DrumPreset> { … }

const DRUM_VOICES: [&str; 18] = ["bd","sd","hh","oh","cp","rim","lt","mt","ht",
                                 "cb","cr","rd","sh","tb","cl","cg","zap","click"];

struct DrumVoice {
    model: DrumModel,
    phase: [f64; 6], freq: [f64; 6], n_osc: usize,   // sines or squares
    f_cur: f64, f_end: f64, f_mul: f64,              // pitch sweep (Membrane, Snare)
    noise_state: u32, noise_gain: f32, noise_decay: f32,
    f1: Biquad, f2: Biquad, f3: Biquad,              // model-specific filters
    click_gain: f32, click_decay: f32,
    burst_left: u8, burst_len: u32, burst_pos: u32,  // clap
    gain: f32, gain_decay: f32, attack_inc: f32, in_attack: bool,
    choke: u8,                                       // 0 = none, 1 = hat group
}
// enum Voice { …, Drum(DrumVoice), … }
```

`activate`, branch `"drum:<name>…"`: look up the preset; apply
`tune` (`mul = 2^(tune/12)` on every oscillator and sweep frequency, not on filter
frequencies), `tone` (per voice, tables below; absent = the default column) and `decay`
(replaces the preset's amplitude decay; noise and click decays keep their ratio to it);
build the filters here, never in `tick`. `is_silent`: every gain of the voice (body,
noise, click) is below 1e-5 and no clap burst is pending. An unknown name logs
`[REPuLse WASM] unknown drum voice: <name>` with `warn` and plays nothing.

The frequencies below are chosen for this kit. They are not taken from a particular
drum machine, and no voice is named after one.

**Membrane** — `out = sin(2π·∫f) · gain + click`, with
`f(t) = f_end · (1 + (ratio − 1) · e^(−t/τ))` and `click = w · click_gain`,
`click_gain` decaying to −100 dB in 4 ms.

| Voice | `f_end` Hz | `ratio` | τ ms | Decay s | Click | Level | `tone` (default) |
|---|---|---|---|---|---|---|---|
| `bd` | 48 | 4.0 | 18 | 0.5 | 0.25 | 1.0 | 0–1 → ratio 2.5–5.5 and click 0–0.5 (0.5) |
| `lt` | 82 | 1.6 | 30 | 0.55 | 0.10 | 0.8 | ratio 1.2–2.0 (0.5) |
| `mt` | 123 | 1.6 | 25 | 0.45 | 0.10 | 0.8 | ratio 1.2–2.0 (0.5) |
| `ht` | 185 | 1.6 | 20 | 0.35 | 0.10 | 0.8 | ratio 1.2–2.0 (0.5) |
| `cg` | 220 | 1.25 | 12 | 0.30 | 0.15 | 0.7 | click 0–0.3 (0.5) |
| `cl` | 2500 | 1.0 | — | 0.06 | 0 | 0.6 | `f_end` 1800–3200 Hz (0.5) |
| `zap` | 60 | 40 | 25 | 0.25 | 0 | 0.7 | τ 10–60 ms (0.3) |

**Snare** — two sines `f_a`, `f_b` (each with a pitch drop of ratio 1.3, τ 10 ms) plus
noise through a high-pass and a band-pass, each part with its own decay.
`out = body · (1 − 0.8·tone) + noise · (0.3 + 0.7·tone)`.

| Voice | `f_a` / `f_b` Hz | Body decay s | Noise filter | Noise decay s | Level | `tone` default |
|---|---|---|---|---|---|---|
| `sd` | 180 / 330 | 0.12 | high-pass 1200 Hz, Q 0.7 | 0.22 | 0.9 | 0.5 |
| `rim` | 480 / 1150 | 0.03 | high-pass 3000 Hz, Q 0.7 | 0.015 | 0.7 | 0.3 |

**Metal** — `n_osc` band-limited squares (`pulse(t, dt, 0.5)`) summed and divided by
`n_osc`, plus `noise_mix · w`, through a band-pass, then the same high-pass twice
(24 dB/oct), then the envelope. Six-oscillator set `M6` = 330 Hz ×
(1.000, 1.342, 1.541, 1.917, 2.262, 2.763) = 330, 443, 509, 633, 746, 912 Hz.

| Voice | Oscillators | Band-pass Hz / Q | High-pass Hz | Decay s | Noise mix | Level | Choke | `tone` (default) |
|---|---|---|---|---|---|---|---|---|
| `hh` | M6 | 9000 / 1.0 | 7000 | 0.05 | 0 | 0.6 | chokes group 1 | high-pass 5000–9000 Hz (0.5) |
| `oh` | M6 | 9000 / 1.0 | 7000 | 0.6 | 0 | 0.5 | group 1 | high-pass 5000–9000 Hz (0.5) |
| `cr` | M6 | 6000 / 0.6 | 3500 | 1.8 | 0.3 | 0.5 | — | high-pass 2500–5000 Hz (0.4) |
| `rd` | M6 × 1.23 | 7500 / 2.0 | 4500 | 1.4 | 0.1 | 0.4 | — | band-pass Q 1–3 (0.5) |
| `cb` | 555, 835 Hz | 700 / 3.0 | none | 0.35 | 0 | 0.6 | — | band-pass 500–1000 Hz (0.4) |
| `tb` | M6 | 8500 / 1.5 | 6000 | 0.18 | 0.5 | 0.5 | — | noise mix 0.2–0.8 (0.5) |

`tb` has a 4 ms linear attack (`in_attack`); the others start at full level.

**NoiseBurst** — noise through one filter and an envelope.

| Voice | Filter | Envelope | Level | `tone` (default) |
|---|---|---|---|---|
| `cp` | band-pass 1100 Hz, Q 1.4 | four bursts starting at 0, 11, 22 and 33 ms; each restarts the gain at 1 and decays to −100 dB in 30 ms; the fourth decays in 0.18 s (this is the `decay` value) | 0.9 | band-pass 700–1500 Hz (0.5) |
| `sh` | high-pass 6000 Hz, Q 0.7 | 12 ms linear attack, decay 0.08 s | 0.5 | high-pass 4000–8000 Hz (0.5) |
| `click` | band-pass 3000 Hz, Q 2 | decay 0.008 s | 0.8 | band-pass 1000–5000 Hz (0.5) |

### B3. Choke

In `activate`, when the new voice's preset says "chokes group 1" or is in group 1
(`hh` and `oh`): every active `Voice::Drum` with `choke == 1` gets
`gain_decay = decay_rate(0.015, sr)` (and the same for its noise decay). A closed hat
therefore stops a ringing open hat within about 15 ms without a click, and a new open
hat replaces the old one. The choke is global in the engine (the engine has no tracks):
hats on two tracks choke each other. A hat with `amp 0` still chokes. The JS fallback
has no choke.

### B4. JS fallback (TEST1 harness, FX tracks)

`js-synth` gains a table in `repulse.voices` that maps each kit voice to an existing JS
voice with constants. `make-kick` gets optional arguments `f-start f-end sweep dur`
(defaults 150, 40, 0.06, 0.4 keep today's kick); `tune` multiplies the frequencies and
`decay` replaces `dur`; `tone` is ignored.

| Kit voice | JS voice |
|---|---|
| `bd` | `make-kick` 192 → 48 Hz, sweep 0.05 s, 0.5 s |
| `lt` / `mt` / `ht` / `cg` | `make-kick` 131 → 82, 197 → 123, 296 → 185, 275 → 220 Hz; 0.55 / 0.45 / 0.35 / 0.30 s |
| `zap` | `make-kick` 2400 → 60 Hz, sweep 0.06 s, 0.25 s |
| `cl` | `make-sine` 2500 Hz, decay 0.06 s |
| `sd` / `rim` / `cp` / `click` | `make-snare` (existing constants; `cp` 0.18 s, `rim` and `click` 0.03 s via a new optional `dur`) |
| `hh` / `sh` / `tb` | `make-hihat` 0.05 / 0.08 / 0.18 s, gain 0.5, high-pass 7000 / 6000 / 6000 Hz |
| `oh` / `cr` / `rd` / `cb` | `make-hihat` 0.6 / 1.8 / 1.4 s, gain 0.4, high-pass 7000 / 3500 / 4500 Hz; `cb`: `make-square` 555 Hz, decay 0.35 s |

## Part C — Synth instruments (blocked until TL7a and TL7c have landed)

### Part C — what it needs from TL7a and TL7c

- **TL7a:** event keys `:lpf :resonance :hpf :bpf :bpq :shape`, the per-voice `Inserts`
  chain in Rust and the JS insert chain (`with-inserts`), `trigger_v3`.
- **TL7c (outline today):** a filter envelope on the low-pass with a depth, an attack
  and a decay (outline names `lp-env`, `lp-attack`, `lp-decay`); vibrato with a rate
  and a depth (outline names `vib`, `vib-mod`); per-sample cutoff updates in `Inserts`.
  The preset table below assumes `lp-env` is a depth in octaves above `lpf` and
  `vib-mod` is in semitones. If TL7c's full spec fixes other names or units, change the
  preset table so that the **peak cutoff** column and the vibrato depth in semitones
  stay as listed; nothing else in Part C changes.
- **Stereo voices and the insert chain.** A unison voice returns two buses. With TL7a
  in place, `ActiveVoice` carries a second `Inserts` for bus B and applies each chain
  to its bus before the pan (for mono voices only the first is used). If SYN6 parts
  A/B land **before** TL7a, the SYN6 PR adds one sentence to `PROMPTS/PHASE-TL7a.md`
  (section 4) saying so; if TL7a lands first, the Part A build step does it.

### C1. Preset expansion (`repulse.voices`)

```clojure
(def instrument-presets
  {:acid {:synth :saw :lpf 400 :resonance 12 :lp-env 3.5 …} …})

(defn expand-preset
  "Pure. For an event whose :synth names an instrument preset: the preset's
   parameters, overridden by every key the event has itself, with :synth
   replaced by the preset's base voice. Other events are returned unchanged."
  [value] …)
```

`play-event` calls `expand-preset` first in the parameter-map branch, then dispatches
on the result as usual. The event map the user sees (inspector, `evalToEvents`) is not
expanded.

| Voice | Base | Oscillator | Filter (TL7a) | Filter envelope / LFO (TL7c, C3) | Peak cutoff | Amp | Other |
|---|---|---|---|---|---|---|---|
| `:acid` | `:saw` | — | `lpf 400`, `resonance 12` | `lp-env 3.5`, `lp-attack 0.003`, `lp-decay 0.18` | 4.5 kHz | `attack 0.003`, `decay 0.35` | reacts to `accent` |
| `:reese` | `:saw` | `unison 2`, `detune 30`, `spread 0.3` | `lpf 500`, `resonance 1.5` | — | 500 Hz | `attack 0.01`, `decay 2.5` | — |
| `:sub-bass` | `:sine` | — | — | — | — | `attack 0.005`, `decay 0.8` | `shape 0.3` |
| `:fm-bass` | `:fm` | `:index 3`, `:ratio 1` | `lpf 300`, `resonance 1` | `lp-env 3`, `lp-attack 0.002`, `lp-decay 0.12` | 2.4 kHz | `attack 0.002`, `decay 0.5` | — |
| `:wobble` | `:saw` | `unison 2`, `detune 15` | `lpf 400`, `resonance 6` | `lp-rate 4`, `lp-depth 2` | 1.6 kHz | `attack 0.01`, `decay 2.0` | — |
| `:lead` | `:saw` | — | `lpf 2800`, `resonance 2` | — | 2.8 kHz | `attack 0.01`, `decay 0.8` | `vib 5.5`, `vib-mod 0.15` |
| `:sync-lead` | `:saw` | `sync 4.5` | `lpf 6000`, `resonance 1` | — | 6 kHz | `attack 0.005`, `decay 0.8` | — |
| `:pluck` | `:saw` | — | `lpf 500`, `resonance 1.2` | `lp-env 4`, `lp-attack 0.001`, `lp-decay 0.12` | 8 kHz | `attack 0.001`, `decay 0.4` | — |
| `:theremin` | `:sine` | — | — | — | — | `attack 0.06`, `decay 1.5` | `glide 0.08`, `vib 5`, `vib-mod 0.25` |
| `:reed` | `:pulse` | `pw 0.3`, `unison 2`, `detune 14`, `spread 0.2` | `bpf 1100`, `bpq 0.8` | — | — | `attack 0.03`, `decay 1.2` | — |
| `:pad` | `:saw` | `unison 3`, `detune 18`, `spread 0.6` | `lpf 1400`, `resonance 0.8` | — | 1.4 kHz | `attack 0.35`, `decay 4.0` | — |
| `:strings` | `:saw` | `unison 5`, `detune 22`, `spread 0.8` | `lpf 3200`, `hpf 180` | — | 3.2 kHz | `attack 0.3`, `decay 4.0` | — |
| `:sweep-pad` | `:saw` | `unison 7`, `detune 20`, `spread 0.9` | `lpf 300`, `resonance 3` | `lp-env 4`, `lp-attack 1.2`, `lp-decay 2.0` | 4.8 kHz | `attack 0.4`, `decay 6.0` | — |
| `:brass-pad` | `:saw` | `unison 3`, `detune 10`, `spread 0.4` | `lpf 500`, `resonance 1` | `lp-env 2.5`, `lp-attack 0.08`, `lp-decay 0.6` | 2.8 kHz | `attack 0.06`, `decay 3.0` | — |

- The envelopes use only `attack` and `decay`, which exist today. `decay` is the time
  to −100 dB, hence the long pad values.
- `:fm-bass` gets its "index decay" character from the filter envelope. A real FM index
  envelope is a TL7c decision ("FM per event") and is not added here.
- `:sub-bass` and `:theremin` need no filter; they still wait for Part C because of
  `shape` and `vib`.
- The values are starting points. The listen check may change any of them; a changed
  value is recorded in the PR. The tests below check structure (a filter sweep, a
  beat, a slow attack), not taste.

### C2. `accent`

Expanded in `expand-preset`, for any voice: with `a = accent` (0–1),
`amp × (1 + 0.5·a)`; and if the event (after preset expansion) has `lp-env`:
`lp-env + 1.5·a` and `lp-decay × (1 − 0.4·a)`. So an accented acid note is louder,
opens further and closes faster.

### C3. Cutoff LFO: `lp-rate`, `lp-depth`

`cutoff(t) = lpf · 2^(lp-depth · sin(2π · (φ0 + rate_hz · t)))`, clamped to
20–20000 Hz, with `rate_hz = lp-rate / cycle-dur` and
`φ0 = frac(lp-rate · onset-time / cycle-dur)`, both computed in `play-event` from its
`timing` argument (`:cycle-dur`) and `t`. Without `lpf` the two parameters do nothing.

- Rust: one more modulation source in TL7c's per-sample cutoff update of `Inserts`
  (the same place the filter envelope writes). Coefficients may be updated every
  16 samples.
- JS: an `OscillatorNode` at `rate_hz` → `GainNode` (`lp-depth · 1200`) → the low-pass
  `BiquadFilterNode`'s `detune` AudioParam (cents). The start phase is set with a
  `PeriodicWave` (`real[1] = sin(2π·φ0)`, `imag[1] = cos(2π·φ0)`). Native nodes only.
- Transport: three slots appended to `trigger_v3` after TL7c's: LFO rate in Hz, start
  phase, depth in octaves. This is the one place SYN6 touches the slot layout, and it
  does so after TL7c by construction.

### C4. Glide

Pure helper in `repulse.voices`:

```clojure
(defn assign-glide
  "Pure. state — {:t secs :cur [hz…] :prev [hz…]} or nil. events — the onsets of one
   track in one scheduling pass, as [{:t secs :hz hz-or-nil :value v} …].
   Sorts by :t. For every pitched event: if its time is later than (:t state) the
   current group becomes the previous one. An event with :glide > 0 and a non-empty
   previous group gets :glide-from = the member of that group nearest in pitch
   (smallest |log(hz / member)|).
   → [events' state']"
  [state events] …)
```

- `schedule-cycle!` collects a track's onsets for the cycle, runs `assign-glide` with
  the state from a private `glide-state` atom (`{track-name state}`) and passes the
  resulting values to `schedule-event!`. Both branches (song mode and free-running)
  use it. Events that are skipped because they are in the past still update the state.
- `stop!` and `clear-track!` clear the state (`(stop)` → no glide into the first note).
- `repulse.offline-render` and `test_api.cljs` `schedule-pattern!` call the same
  helper, so export and harness glide like playback.
- The oscillator string gets `glide-from=<hz>:glide=<secs>`. `activate` sets
  `freq = glide_from`, `glide_left = glide · sr`,
  `glide_mul = (target / glide_from)^(1 / glide_left)`: a straight line in pitch.
- Glide works on `Voice::Osc` voices and the presets built on them. FM, string and
  drum voices ignore it. Samples ignore it.
- `:theremin` has `glide 0.08` in its preset; `(glide 0)` turns it off.

## Part D — Housekeeping

### D1. One voice list per language

New pure namespace `app/src/repulse/voices.cljs` (no DOM, no audio):

```clojure
(def osc-voices        #{:saw :square :pulse :tri :sine :supersaw})
(def fm-presets        #{:sax :trumpet :trumpet-muted :trumpet-cup :trombone :synth-brass
                         :harmon-out :harmon-in :epiano :bell :marimba :flute})
(def string-presets    #{:western :nylon :guitar :harp :koto :pizz :lute :mandolin})
(def drum-voices       ["bd" "sd" "hh" "oh" "cp" "rim" "lt" "mt" "ht"
                        "cb" "cr" "rd" "sh" "tb" "cl" "cg" "zap" "click"])
(def instrument-presets {…})                      ; Part C
(def synth-voices      …)                         ; union of the above keys + :fm :noise
```

Plus the pure functions named in this spec: `osc-voice?`, `with-defaults`,
`voice-string`, `osc-string`, `drum-string`, `drum-route`, `unison-layout`,
`expand-preset`, `assign-glide`, `js-drum` (the B4 table).

Users of the list: the dispatch in `play-event` (the two literal sets on lines 533 and
542 go away), `sound_check.cljs`, the keyword completion (`providers.js` gets
`getVoiceNames` / `getDrumNames`, filled from this namespace at start-up, so `:su…`
offers `:supersaw` and `:synth_bd`), and the tests.

Rust: `const FM_PRESETS: [&str; 12]`, `const KS_PRESETS: [&str; 8]`,
`const DRUM_VOICES: [&str; 18]` next to the preset functions. The existing tests
`fm_presets_all_produce_output` and `karplus_strong_all_presets_produce_output` loop
over the constants instead of their own lists.

Drift guards (in `app/src/repulse/voices_test.cljs`, which runs on Node and may read
files with `(js/require "fs")`):

- the three Rust constants, read from `packages/audio/src/lib.rs` with a regular
  expression, equal the three CLJS collections;
- every member of `synth-voices` has a `label: "<name>"` entry in `completions.js` and
  an entry in `builtin_meta.edn`.

Adding a voice then means: the table in one language each, plus registration. No test
is edited.

### D2. Unknown voice warning (`sound_check.cljs`)

```clojure
(defn synth-voice-names [pat n] …)   ; distinct (:synth value) keywords over n cycles
(defn unknown-voices
  "Unknown (synth …) voices across patterns, each as {:name kw :suggestion kw-or-nil}.
   known-voice? — (fn [kw]) true for a built-in voice or a defsynth name."
  [patterns known-voice?] …)
(defn voice-warning-message [{:keys [name suggestion]}] …)
```

Messages:

- with a suggestion: `unknown synth voice :trii — did you mean :tri?`
- without: `unknown synth voice :xyz — plays a sine`

The suggestion uses `leval/typo-hint` over the names in `voices/synth-voices` with the
same plausibility rule as `suggest`. `report-unknown-sounds!` appends these messages to
the same warning line and marks the keyword in the editor with `keyword-ranges`.
`known-voice?` is `#(or (voices/synth-voices %) (synth/lookup-synth %))`. Playback is
unchanged: an unknown voice still plays a sine.

### D3. `:trumpet-cup`

Add to `fm_preset`: `"trumpet-cup" => (4.5, 1.0, 0.012, 0.06, 0.75, 0.11)`;
to `fm_body_filters`: `highpass(180, 0.7)`, `peaking_eq(1400, 4.0, 2.0)`,
`highshelf(3200, −6.0)`; to `fm_noise_amp`: `0.030`. That places it between the open
trumpet and the harmon mutes, as USAGE.md describes it. Per the CLAUDE.md "FM preset
tests" rule the automatic test is "non-silent" only; the character is a listen item.

### D4. Docs

- `docs/USAGE.md`: the voice table (2145) lists every voice in `synth-voices`; the FM
  table gains `:synth-brass`, `:harmon-out`, `:harmon-in` and keeps `:trumpet-cup`;
  new sections "Unison and oscillator parameters", "Synth drum kit" (replaces
  "Built-in synthesis fallback": the 18 voices, `(bank :synth)`, `:synth_bd`, `tune`,
  `tone`, `decay`, choke, the legacy fallback) and "Synth instruments" (one example
  per voice, the preset table, the override rule, `glide`, `accent`, `lp-rate`). The
  noise example on line 2140 is rewritten (question 4). The E1 note (1558) says which
  SYN6 features are WASM-only.
- `docs/STRUDEL-PARITY.md`: row 414 → ✅ SYN6 with the REPuLse names; row 478
  (`fastspread` / `spread`) notes that the name `spread` is taken by the stereo width.
- `docs/SYNTHESIS-COVERAGE.md`: the SYN6 rows → ✅ (after Part C for the instrument
  rows), summary counts updated.
- `README.md`: one line in the feature list if it names the voices.

## Rule 3 registration

Every name below goes into `BuiltinName` (then `npm run gen:grammar`),
`completions.js`, `builtin_meta.edn` (then `npm run gen:ai-docs`) and `hover.js`.
Parameter functions also get an entry in `insert-categories.js` next to `amp`/`pan`.

| Part | Parameter functions (`type: "function"`, category `"param"`) | Voices (`type: "keyword"`, category `"synth"`) |
|---|---|---|
| A | `unison` `detune` `spread` `sub-osc` `pw` `pw-rate` `pw-depth` `sync` | `pulse` `supersaw`; `tri` and `sine` exist already (signal / UGen): extend their detail text with the voice meaning |
| B | `tune` `tone` | — (drum names are sound names: keyword completion only) |
| C | `glide` `accent` `lp-rate` `lp-depth` | `acid` `reese` `sub-bass` `fm-bass` `wobble` `lead` `sync-lead` `pluck` `theremin` `reed` `pad` `strings` `sweep-pad` `brass-pad` |
| D | — | `trumpet-cup` |

Also update the existing entries `synth` (options `:color`, `:density`; see-also),
`noise`, `bank` (`(bank :synth)` example), `decay` (acts on synth drums) and `sub`
(see-also `sub-osc`). 14 functions and 17 new voice names in total.

## Files to change

| File | Change | Part |
|---|---|---|
| `packages/audio/src/lib.rs` | `poly_blep`, `poly_blamp`, waveform functions; `Voice::Osc` replaces `Saw`/`Square`; `tick2`; noise colours; `arg`/`arg_str`/`positional`; `Voice::Drum`, `drum_preset`, choke; `:trumpet-cup`; name constants; tests | A B D |
| `packages/audio/src/lib.rs` | second `Inserts` for stereo voices; cutoff LFO in `Inserts`; three `trigger_v3` slots | C |
| `app/public/worklet.js` | no change in A/B/D; the appended slots pass through TL7a's `trigger_v3` message | C |
| `app/src/repulse/voices.cljs` (new) | voice lists, string builders, `drum-route`, `unison-layout`, `expand-preset`, `assign-glide` | A B C D |
| `app/src/repulse/voices_test.cljs` (new) + `packages/core/src/repulse/test_runner.cljs` | unit tests and drift guards | A B C D |
| `app/src/repulse/audio.cljs` | `play-event` dispatch through `repulse.voices`; `make-osc`; coloured `make-noise`; `js-synth` kit table; glide state in the scheduler, cleared in `stop!` / `clear-track!` | A B C |
| `app/src/repulse/samples.cljs` | none expected (`drum-route` wraps `resolve-keyword`) | B |
| `app/src/repulse/sound_check.cljs` + `sound_check_test.cljs` | 18 drum names, `synth_*`, unknown-voice warning | B D |
| `app/src/repulse/eval_orchestrator.cljs` | `report-unknown-sounds!` adds the voice warnings | D |
| `app/src/repulse/offline_render.cljs`, `app/src/repulse/test_api.cljs` | call `assign-glide` when scheduling | C |
| `packages/core/src/repulse/params.cljs` + `params_test.cljs` | 14 parameter functions via `apply-param` | A B C |
| `packages/lisp/src/repulse/lisp/builtins/params.cljs` | register them with the `param` helper | A B C |
| `packages/lisp/src/repulse/lisp/builtins/synth.cljs` | `(noise :color …)` options | A |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` + `parser.js` | names; `npm run gen:grammar` | A B C D |
| `app/src/repulse/lisp-lang/completions.js`, `hover.js`, `insert-categories.js`, `keywords-completion.js`, `providers.js` | entries; voice and drum keyword completion | A B C D |
| `app/src/repulse/content/builtin_meta.edn` + `docs/ai/builtins.json` | entries; `npm run gen:ai-docs` | A B C D |
| `e2e/audio-render.spec.ts` | TEST1 checks (JS fallback) | A B C |
| `e2e/export-parity.spec.ts` | WASM path checks through `(export …)` | A B C |
| `docs/USAGE.md`, `docs/STRUDEL-PARITY.md`, `docs/SYNTHESIS-COVERAGE.md`, `README.md`, `ROADMAP.md` | see D4; delivered entry | all |
| `PROMPTS/PHASE-TL7a.md` | one sentence on stereo voices, only if SYN6 A lands first | A |
| build | `npm run build:wasm` after every `lib.rs` change | — |

## Build order

Before any code: CLAUDE.md Rule 4 (`npm run gen:grammar && npm run gen:ai-docs &&
npm run test` on the unmodified branch), and `npm run test:rust`.

**Part D first half (foundation)**

1. `repulse.voices` with the existing names only, `play-event` dispatching through it
   (no behaviour change), Rust name constants, the two Rust tests looping over them,
   drift guards. `npm run test:all` must pass unchanged.

**Part A**

2. `poly_blep`, `poly_blamp`, `saw`, `pulse`, `tri` as free functions with tests A1–A2.
3. `Voice::Osc` with `n = 1`, `arg` parser, `tick2`; remove `Voice::Saw`/`Square`.
   Run test A6 and record the numbers.
4. Unison, detune, spread, sub-oscillator (tests A3–A5, A8, A9).
5. PWM and hard sync (tests A10, A11).
6. Noise colours (test A7).
7. CLJS: parameter functions, `osc-string`, `make-osc`, noise dispatch fix, Rule 3 for
   the Part A names, e2e A-tests. `npm run build:wasm`.

**Part B**

8. `Voice::Drum`: Membrane, then Snare, Metal, NoiseBurst; `drum_preset`; tests B1–B6.
9. Choke (test B7).
10. CLJS: `drum-route`, `drum-string`, kit table for `js-synth`, `tune`/`tone`,
    `sound_check` names, Rule 3, e2e B-tests.

**Part D second half**

11. Unknown-voice warning, `:trumpet-cup`, USAGE voice tables. If TL7a and TL7c have
    not both landed: docs for A, B, D, listen check for A, B, D, and **PR 1** here.

**Part C — only when TL7a and TL7c are delivered on `dev`**

12. Second `Inserts` for stereo voices; cutoff LFO; slots.
13. `expand-preset`, the preset table, `accent`, `lp-rate`/`lp-depth`.
14. `assign-glide` in the scheduler, the export and the harness; glide in `Voice::Osc`
    and `make-osc`.
15. Tests C1–C8, Rule 3 for the Part C names, docs, listen check, **PR 2** (or the
    single PR if nothing was split). Only now: ROADMAP ✅ *delivered*.

## Tests

No test claims that something sounds like an instrument (CLAUDE.md "FM preset tests").
Every assertion below is a number. Rust tests add a small FFT (radix-2, in the test
module, no new crate) and a `goertzel` helper. Unless stated, 44.1 kHz.

### Rust — Part A

- **A1 Aliasing.** Generate 32768 samples of constant amplitude directly from the
  waveform functions at bin-centred frequency 2616 · 44100 / 32768 = 3520.7 Hz
  (rectangular window, no leakage), once with `saw` and once with the naive formula
  kept in the test. Sum the power of all bins that are not within ±1 bin of a
  harmonic. (a) Between 20 Hz and 3400 Hz (below the fundamental): the new oscillator
  is **≥ 30 dB** lower than the naive one. (b) Between 20 Hz and 10 kHz: **≥ 12 dB**
  lower. Same for `pulse` at widths 0.5 and 0.25.
  *Change from the outline, which asked for ≥ 30 dB below 10 kHz:* that number cannot
  be reached with a 2-point PolyBLEP. The alias of the 10th harmonic (35.2 kHz, folds
  to 8.9 kHz) is only reduced by sinc²(0.80) ≈ −22 dB, and it dominates the band; the
  estimate for the whole band is about 16 dB. Below the fundamental, where no harmonic
  masks the aliases, the reduction is 45 dB and more. If the measured values miss
  (a) or (b), stop and report; do not loosen the thresholds.
- **A2 Triangle.** `tri` at 440 Hz (bin-centred): 3rd harmonic 19 ± 2 dB below the
  fundamental; 2nd and 4th harmonic ≥ 40 dB below.
- **A3 Detune.** `"saw:440:unison=3:detune=20"`, decay 100 s, 4 s render: scanning
  430–450 Hz in 0.25 Hz steps with `goertzel` finds exactly three local maxima within
  12 dB of the strongest, at 437.5, 440.0 and 442.5 Hz (each ± 0.5 Hz, i.e. within
  ± 12 cents of the note).
- **A4 Spread.** `"saw:220:unison=7:detune=20:spread=1"`, 1 s: correlation of left and
  right < 0.9. With `spread=0`: left and right are sample-identical.
- **A5 Sub-oscillator.** `"saw:440:sub-osc=0.5"`: `goertzel` at 220 Hz is 6 ± 2 dB
  below 440 Hz.
- **A6 Change against the old oscillators.** RMS of 1 s of the new `saw` and `pulse`
  (0.5) against the naive formulas at 110, 440, 1760 and 3520 Hz. Assert
  |Δ| ≤ 0.5 dB at 110 and 440 Hz; print all eight values and put them in the PR.
- **A7 Noise colours.** 10 s of each colour, averaged FFT (Welch, 65536 points). Power
  spectral density (band power divided by bandwidth) in the octave bands centred on
  125, 250, …, 8000 Hz: pink falls 3 ± 1 dB per octave, brown 6 ± 1.5 dB, white is
  flat within ± 1 dB. Dust with `density=100` over 10 s has 800–1200 non-zero samples.
  LFSR with `freq=440` (clock 7040 Hz) over 1 s has between 2800 and 4200 sign changes
  (a random bit flips on half of the clocks).
- **A8 Determinism.** Two engines given the same four unison triggers render
  bit-identical buffers.
- **A9 Old strings.** `"saw:440"` and `"square:440:0.25"` still produce non-silent
  output; a bare `"440"` and the legacy `"bd"`, `"sd"`, `"hh"`, `"oh"` render
  bit-identically to a reference buffer captured before the phase (store the first
  256 samples of each as constants in the test).
- **A10 PWM.** `"square:220:pw=0.5:pw-rate=0.5:pw-depth=0.4"`, decay 100 s, 4 s render:
  the duty cycle measured in 50 ms windows (share of samples above the midpoint between
  the window's minimum and maximum) goes below 0.2 and above 0.8. With `pw-depth=0` it
  stays within 0.5 ± 0.05.
- **A11 Sync.** `"saw:220:sync=4.5"`: the strongest partial is the 4th or 5th harmonic
  (880 or 1100 Hz; the Fourier series of the synced saw gives 0.23 and 0.18 against
  0.06 for the 1st). Without `sync` it is the 1st.
- **A12 Benchmark** (`#[ignore]`, run with
  `cargo test --release -- --ignored bench_supersaw`): 16 simultaneous
  `unison=7` voices, 10 s. Print the real-time factor and assert ≥ 20× natively. Put
  the number in the PR. Below 20×: report before continuing with Part B.

### Rust — Part B

- **B1** Every name in `DRUM_VOICES`: RMS of the first 100 ms > 0.001, and at `amp 1`
  the peak of the rendered buffer is < 0.99 (the output clamp never engages).
- **B2** `"drum:bd:decay=2"` vs `"drum:bd:decay=2:tune=12"`: the strongest frequency in
  the window 200–700 ms (`goertzel` scan, 30–130 Hz in 0.5 Hz steps) is 48 Hz ± 5 % and
  96 Hz ± 5 %.
- **B3** `"drum:bd:decay=0.8"` vs `"drum:bd:decay=0.2"`: RMS of 300–400 ms differs by
  ≥ 20 dB (treat an RMS of 0 as −200 dB).
- **B4** `"drum:hh"`: ≥ 80 % of the energy above 5 kHz. `"drum:bd"`: ≥ 80 % below
  200 Hz.
- **B5** `"drum:cp"`: the amplitude envelope (RMS in 2 ms windows) of the first 40 ms
  has at least three peaks separated by dips of ≥ 6 dB.
- **B6** `"drum:lt:decay=2"` and `"drum:lt:decay=2:tune=7"`: tail fundamentals (same
  scan as B2, 60–160 Hz) 82 Hz and 123 Hz, each ± 5 %. `"drum:hh:tone=0.1"` has a lower share of energy above 8 kHz than
  `"drum:hh:tone=0.9"` (by ≥ 3 dB).
- **B7 Choke.** `"drum:oh"` at 0 s alone, and `"drum:oh"` at 0 s plus `"drum:hh"` with
  **amp 0** at 0.05 s: the RMS of 60–70 ms is ≥ 40 dB lower in the second render.
- **B8** `"drum:nope"` adds no voice.

### Rust — Part C and D

- **C-R1** Cutoff LFO: a `saw:110` voice with `lpf` 400 Hz and an LFO of 2 Hz, 2
  octaves: the level at 1100 Hz measured in 50 ms windows rises and falls twice per
  second with a swing ≥ 12 dB.
- **C-R2** Glide: `"saw:261.63:glide-from=130.81:glide=0.1"`: the pitch (zero-crossing
  rate or FFT peak) over 40–60 ms lies between 150 and 230 Hz; over 150–250 ms it is
  261.6 Hz ± 1 %.
- **D-R1** `"fm:trumpet-cup:440"`: RMS > 0.001 (via the `FM_PRESETS` loop).

### CLJS unit tests (`npm run test`)

- `params_test`: each of the 14 functions sets its key, takes a pattern and a signal
  (sampled at onset), and has the transformer arity.
- `voices_test`: `voice-string` and `osc-string` (field order, absent keys omitted,
  supersaw defaults, explicit `detune` overrides them); `drum-string`;
  `unison-layout` equals the worked rows of A3; `drum-route` — with prefix `"synth"`
  and a `bd` bank loaded → `:synth-drum`; without prefix and with the bank → `:sample`;
  without both → `:legacy-drum`; `:cp` without a bank → `:synth-drum`; `:synth_bd`
  with prefix `"RolandTR808"` → `:synth-drum`; `:tabla` with prefix `"synth"` and a
  `tabla` bank → `:sample`; `:foo` → `:unknown`.
  `expand-preset`: `{:synth :pad :lpf 900}` keeps 900, takes the rest from the table,
  ends with `:synth :saw`; `accent`. `assign-glide`: a line glides from the previous
  note; two chords pair by nearest pitch; events arriving unsorted give the same
  result; the first note after an empty state gets no `:glide-from`. The drift guards.
- `sound_check_test`: `(synth :trii)` → `unknown synth voice :trii — did you mean
  :tri?`; a `defsynth` name → no warning; `:cp` and `:synth_bd` → no unknown-sound
  warning.

### Playwright — TEST1 (`e2e/audio-render.spec.ts`, backend `offline-js`)

- `(->> (seq :c4 :c4 :c4 :c4) (synth :tri))`: four onsets; 3rd harmonic (785 Hz)
  15–23 dB below the fundamental (262 Hz).
- `(->> (seq :a3) (synth :supersaw))`: left/right correlation < 0.95;
  `(spread 0)` added: correlation > 0.999.
- `(->> (seq :c4 :_ :c4 :_) (synth :noise))`: two onsets, and the ratio
  Σ(x[n] − x[n−1])² / Σ x[n]² over the first 100 ms is > 1 (white noise ≈ 2; the
  262 Hz sine that plays today ≈ 0.001).
- `(seq :bd :cp :lt :hh)` (no samples in the harness): four onsets, non-silent.
- With `registerTestSample("bd", "tone")`: `(seq :bd)` renders the 440 Hz test tone;
  after `(bank :synth)` the same pattern has its `goertzel` peak below 200 Hz and
  ≥ 20 dB less energy at 440 Hz. `(seq :synth_bd)` without the bank form: the same.
- Part C: `(->> (seq :c3 :c4) (synth :saw) (glide 0.1))` — pitch 50 ms after the second
  onset between 131 and 262 Hz; with `(glide 0)` 262 Hz ± 1 %.

### Playwright — WASM path (`e2e/export-parity.spec.ts`, production app, `(export …)`)

- `(bpm 240) (track :a (->> (seq :a7) (synth :saw))) (export 1)`: in the first 0.4 s,
  `goertzel` at the alias of the 13th harmonic, `|13 · 3520 − sr|` for the WAV's sample
  rate (1660 Hz at 44.1 kHz, 2240 Hz at 48 kHz), is ≥ 40 dB below the level at
  3520 Hz. On `dev` before the phase it is about −22 dB (the naive saw's 13th harmonic
  is 1/13 of the fundamental), so the test also proves that the export uses the new
  oscillator.
- `(bank :synth)` then `(track :k (->> (seq :bd) (decay 2) (tune 12)))`, exported:
  strongest frequency of 200–700 ms at 96 Hz ± 5 %; without `tune` 48 Hz ± 5 %.
- Choke, `(bank :synth) (bpm 480)` (one cycle = 0.5 s), `(export 1)`:
  `(->> (seq :oh :hh) (decay (seq 3 0.05)))` against the reference
  `(->> (seq :oh :_) (decay 3))`. The RMS of 0.35–0.45 s is ≥ 40 dB lower with the
  closed hat than in the reference.
- Part C: `(->> (seq :c2) (synth :acid) (decay 2))`: the share of energy above 1 kHz in
  0–20 ms is ≥ 10 dB higher than in 300–320 ms. `(->> (seq :a2) (synth :reese)
  (decay 6))`: the RMS in 10 ms windows over 0.2–2.0 s, divided by a fitted
  exponential, has a modulation depth (max − min)/(max + min) ≥ 0.2.
  `(->> (seq :c4) (synth :strings))`: RMS at 10–30 ms < 25 % of RMS at 390–410 ms.
  `(->> (seq :a3) (synth :sync-lead))`: the strongest partial is above the 3rd
  harmonic. Every preset: non-silent, and its peak stays below 0.99 at `amp 1` (the
  output clamp never engages on a single note).

## Human listen check (🤖👂)

Recorded in the PR as a checklist with the listener's verdict. Compare against the
deployed build (or `dev` before the merge) where "old" is needed.

1. Old vs new saw on a high melody: `(->> (seq :c7 :e7 :g7 :c8) (synth :saw))`. The
   new one has no rough, pitch-less tones under the notes.
2. `(->> (seq :c2 :g2 :c3 :g3) (synth :saw))` and `(synth :square)`: no audible change
   against the old build in the bass range.
3. A supersaw chord, eight times in a row: wide, not thin, not identical-sounding to
   the point of irritation (decision 2). Then `(spread 0)`: mono.
4. `:reese`-style `(unison 2) (detune 30)` on a bass line: every note has the same
   weight (fixed phases).
5. Each noise colour for two seconds; `:dust` crackles, `:lfsr` at `:c3` and `:c6`.
6. A pattern with all 18 drum voices, then `(tune …)`, `(tone …)`, `(decay …)` on kick,
   snare and hats. Open hat cut by the closed hat.
7. Each Part C instrument in a short phrase; acid with `(glide …)` and `(accent …)`;
   wobble over two cycles staying in time; a pad with `(lpf 900)`.
8. `:trumpet-cup` between `:trumpet` and `:harmon-out`.

## Definition of done

**Part A**

- [ ] Tests A1–A12 pass; the A6 and A12 numbers are in the PR.
- [ ] `(->> (seq :a7) (synth :saw))` exports with the alias of the 13th harmonic ≥ 40 dB below the note (export-parity test).
- [ ] `(->> (chord :minor :a3) (synth :supersaw) (detune 18) (spread 0.8))` plays three notes, each a 7-oscillator stereo stack; `(spread 0)` gives identical channels.
- [ ] `(->> (seq :a1 :a1 :c2 :e1) (synth :saw) (unison 3) (detune 12) (sub-osc 0.5))` evaluates and plays; `(sub 0.5 …)` keeps its TL1 arithmetic meaning.
- [ ] `(synth :noise :color :pink)`, `:brown`, `:dust`, `:lfsr` render with the measured spectra (A7); `(->> (seq :c4 :_) (synth :noise))` plays noise on the note and nothing on the rest.
- [ ] `:tri`, `:sine`, `:pulse` and `:supersaw` work on the WASM path and the JS fallback.

**Part B**

- [ ] Tests B1–B8 pass.
- [ ] With `(bank :synth)` active and a `bd` sample bank loaded, `(seq :bd)` plays the engine voice, not the sample (`drum-route` unit test and the TEST1 render test). Without `(bank :synth)`, `(seq :bd)` behaves exactly as before, bit-identical on the legacy path (A9).
- [ ] `(seq :synth_bd)` plays the engine voice while another bank is active.
- [ ] `(tune (seq 0 7) (seq :lt :lt))` under `(bank :synth)` yields two different fundamentals; `tune`, `tone` and `decay` accept patterns and signals.
- [ ] A closed hat stops a ringing open hat (B7 and the export test).
- [ ] `(seq :cp)` without a `cp` sample plays the synthesized clap and shows no unknown-sound warning.

**Part C**

- [ ] The Part C tests pass (C-R1, C-R2, `expand-preset`, `assign-glide`, the TEST1 glide test, the export tests).
- [ ] `(->> (chord :major :f3) (synth :pad) (lpf 900))` uses 900 Hz, not the preset cutoff.
- [ ] `(->> (seq :c2 :c2 :eb2 :g1) (synth :acid) (glide (seq 0 0 0.06 0)))`: only the third note glides; glide state is per track and reset by `(stop)`.
- [ ] Every preset renders non-silent output and does not reach the output clamp at `amp 1`.

**Part D**

- [ ] `(->> (seq :c4) (synth :trii))` shows `unknown synth voice :trii — did you mean :tri?` in the output line; a `defsynth` name passed to `synth` produces no warning.
- [ ] `(->> (seq :c4) (synth :trumpet-cup))` triggers `"fm:trumpet-cup:…"`, not a sine.
- [ ] Voice names exist in exactly one list per language; the drift guards pass; adding a voice touches no test code.

**All parts**

- [ ] `Voice::tick` / `tick2` allocate nothing: `OscVoice` and `DrumVoice` contain only inline fields (review item).
- [ ] Parity rows `detune` / `unison` / `spread` updated in STRUDEL-PARITY.md; SYNTHESIS-COVERAGE.md rows for this phase set to ✅.
- [ ] Rule 3 registration for every new name; USAGE.md: voice table, "Unison and oscillator parameters", "Synth drum kit" and "Synth instruments" sections with one example per voice and parameter.
- [ ] Human listen check recorded in the PR (all eight items, or items 1–6 and 8 for the first PR).
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5).

## Test protocol for the PR (Rule 5)

The implementer ends the PR with this protocol. Before pasting, check every snippet
with `window.__REPULSE_TEST__.evalToEvents(code, cycles)` on `test-harness.html` and
correct note names or counts that differ; message texts come from the code. Drop the
Part C group from the first PR if the phase is split.

````markdown
## Test protocol

Setup: `npm run dev:full`, open http://localhost:3000, headphones on. Evaluate each
snippet with Alt+Enter (macOS Option+Enter); stop playback between tests.

### New features — oscillators
- [ ] **1. Clean saw.** `(->> (seq :c7 :e7 :g7) (synth :saw))`
  Expected: three very high notes per cycle (C7, E7, G7), bright but pure; no rough
  low tones under them.
- [ ] **2. Triangle.** `(->> (seq :c4 :e4 :g4) (synth :tri))`
  Expected: three soft, flute-like notes; no warning in the output line.
- [ ] **3. Supersaw.** `(->> (chord :minor :a3) (synth :supersaw) (detune 18) (spread 0.8))`
  Expected: one A-minor chord (A3 C4 E4) per cycle, wide in stereo and slowly beating.
- [ ] **4. Mono check.** `(->> (chord :minor :a3) (synth :supersaw) (spread 0))`
  Expected: the same chord in the centre; both ears identical.
- [ ] **5. Sub-oscillator.** `(->> (seq :a1 :a1 :c2 :e1) (synth :saw) (unison 3) (detune 12) (sub-osc 0.5))`
  Expected: four bass notes with a clear octave-lower sine under each.
- [ ] **6. PWM.** `(->> (seq :c3) (synth :pulse) (pw 0.5) (pw-rate 1) (pw-depth 0.4) (decay 4))`
  Expected: one long note whose colour sweeps from hollow to thin and back once per second.
- [ ] **7. Hard sync.** `(->> (seq :c3 :c3 :c3 :c3) (synth :saw) (sync (seq 2 3 4.5 6)))`
  Expected: four notes at the same pitch (C3), each brighter and more "torn" than the last.
- [ ] **8. Noise colours.** `(->> (seq :c4 :c4 :c4 :c4) (synth :noise :color :pink) (decay 0.3))`, then `:brown`, `:dust`, `:lfsr`
  Expected: four noise bursts per cycle; pink softer than white, brown a low rumble,
  dust single crackles, lfsr a buzzing, pitched "chip" noise.

### New features — drum kit
- [ ] **9. Synth kit.**
  ```lisp
  (bank :synth)
  (seq :bd :cp :bd [:cp :rim])
  ```
  Expected: output line `bank: synth`; kick, clap, kick, then clap and rimshot as a
  quick pair; all synthesized, also with samples loaded.
- [ ] **10. Whole kit.** `(slow 2 (seq :bd :sd :hh :oh :cp :rim :lt :mt :ht :cb :cr :rd :sh :tb :cl :cg :zap :click))`
  Expected: 18 different hits over two cycles; none silent, none a 440 Hz sine.
- [ ] **11. Tune and decay.** `(->> (seq :bd :bd :bd :bd) (tune (seq 0 0 -5 0)) (decay 0.8))`
  Expected: four long kicks; the third clearly lower.
- [ ] **12. Tone.** `(->> (fast 4 (seq :hh :hh :oh :hh)) (tone (range 0.2 0.9 sine)))`
  Expected: 16 hats per cycle getting brighter, then darker; each open hat is cut by
  the next closed hat.
- [ ] **13. Single hit in a sampled kit.** `(bank nil)` then `(seq :bd :synth_bd)`
  Expected: the sampled kick, then the synthesized kick.

### New features — instruments (Part C)
- [ ] **14. Acid with glide.** `(->> (seq :c2 :c2 :eb2 :g1) (synth :acid) (glide (seq 0 0 0.06 0)))`
  Expected: four squelchy bass notes; the third slides up from C2 to E♭2.
- [ ] **15. Accent.** `(->> (seq :c2 :c2 :c2 :c2) (synth :acid) (accent (seq 0 1 0 0)))`
  Expected: the second note louder and brighter than the others.
- [ ] **16. Override.** `(->> (chord :major :f3) (synth :pad) (lpf 900))`
  Expected: a slow, wide F-major pad, darker than without `(lpf 900)`.
- [ ] **17. Each instrument.** `(->> (seq :c3 :eb3 :g3 :bb3) (synth :reese))`, then `:sub-bass :fm-bass :wobble :lead :sync-lead :pluck :theremin :reed :strings :sweep-pad :brass-pad`
  Expected: twelve clearly different sounds; `:wobble` pulses four times per cycle;
  `:theremin` slides between notes; `:strings` and the pads fade in.

### Warnings and errors
- [ ] **18. Unknown voice.** `(->> (seq :c4) (synth :trii))`
  Expected: a sine plays; output line ends with
  `Warning: unknown synth voice :trii — did you mean :tri?`; `:trii` is underlined.
- [ ] **19. No suggestion.** `(->> (seq :c4) (synth :xylophone))`
  Expected: `Warning: unknown synth voice :xylophone — plays a sine`.

### Regressions
- [ ] **20. Saw and square in the bass.** `(->> (seq :c3 :e3 :g3) (synth :saw))`, then `(synth :square :pw 0.25)`
  Expected: buzzy 3-note arpeggios, as before.
- [ ] **21. Drums without the synth bank.** `(bank nil)` then `(seq :bd :sd :hh :oh)`
  Expected: the sampled kit as before (or, before samples have loaded, the old four
  synthesized voices).
- [ ] **22. `sub` arithmetic.** `(->> (sub 100 (seq 320 420 540)) (synth :saw))`
  Expected: three saw notes at 220, 320 and 440 Hz, as before the phase (TL1 `sub`
  subtracts; it is not the sub-oscillator).
- [ ] **23. FM and strings.** `(->> (seq :c4 :e4) (synth :epiano))`, `(->> (seq :a3) (synth :guitar))`, `(->> (seq :c4 :e4) (synth :trumpet-cup))`
  Expected: electric piano and plucked guitar as before; the cup-muted trumpet is a
  brass tone, not a sine.
- [ ] **24. Export.** `(track :a (->> (seq :c7 :e7) (synth :saw)))` then `(export 2)`
  Expected: `exported all tracks — 2 cycles (offline)`; the WAV sounds like playback.

### Editor
- [ ] **25. Completion.** Type `(synth :su`
  Expected: the list offers `:supersaw` and `:sub-bass`.
- [ ] **26. Hover.** Hover `detune`, `tune`, `glide`
  Expected: a signature and one example each.

### Known issues (not blockers)
- On a track with its own `(fx …)`, synth voices use the JS fallback: no hard sync, no
  PWM movement, no hat choke, simplified drums.
- `pad`, `lead`, `strings` used as your own names are highlighted as built-ins.
````

## What NOT to do

- No more acoustic instrument presets (clarinet, banjo, vibraphone, organ, choir) and no
  sound-effect generators: deferred **SYN7**.
- No recorded or multisampled kits, velocity layers or round-robin: **KIT1**.
- No circuit-level emulation of a specific drum machine or synthesizer, and no brand
  names as voice names.
- No new filter models (ladder, comb): deferred **FX2**. Presets use the TL7a filter.
- No effects baked into presets (chorus, reverb): those stay track-level `fx`.
- No wavetable oscillator (out of Milestone 1, see STRUDEL-PARITY.md).
- No preset browser, step sequencer or drum-pad UI.
- Don't touch the `defsynth` UGens; they already use band-limited native oscillators.
- Don't make the pattern engine stateful for glide; the state lives in the scheduler.
- Don't change what bare `:bd :sd :hh :oh` play when no synth bank is selected
  (question 3 may lift this).
- Don't port the drum models or hard sync to JavaScript, and don't route the worklet
  through per-track FX here (EXP1 decision E1 has its own follow-up phase).
- Don't add `trigger_v3` slots in parts A, B or D.
- Don't fix the per-block allocations in `process_block_raw` (`scheduled`, `buf`) in
  this phase; "allocation-free" here is about `tick`.
- No FM index envelope, no generic `lfo` modulator, no amplitude sustain: TL7c decides
  those.
