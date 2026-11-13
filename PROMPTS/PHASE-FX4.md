# Phase FX4 — Reverb, Delay & Tape Colour

> **Spec status:** outline — expand into a full spec before implementing.
>
> **Automation:** 🤖👂 auto + human check — decay times, echo spacing and channel order
> are measurable in offline renders; whether a room sounds like a room and tape sounds
> like tape needs a human listen.

**Milestone:** 1 — Tidal-Lisp core (sound side track)
**Depends on:** none. Track-level plugins. Coordinates with TL7b (per-event sends reuse
the same reverb and delay building blocks per orbit).

## Goal

Make the two most used effects controllable and add the colour of tape and vinyl.
`(fx :reverb …)` has exactly one control, the wet level: there is no way to choose a
small room or a long hall. `(fx :delay …)` takes its time in seconds only and repeats in
mono. This phase gives the reverb a type, size, decay and damping, gives the delay
tempo sync, ping-pong, multi-tap and a tape mode, and adds `tape` and `vinyl` effects.

```lisp
;; Before
(fx :reverb 0.4)
(fx :delay :wet 0.4 :time 0.25 :feedback 0.5)

;; After (proposed)
(fx :reverb :type :hall :decay 3.5 :damp 0.4 :predelay 0.02 :wet 0.3)
(track :snare (->> pat (fx :reverb :type :gated :decay 0.4 :wet 0.6)))
(fx :delay :sync 3/16 :mode :ping-pong :feedback 0.5 :wet 0.35)
(track :lead (->> pat (fx :delay :mode :tape :sync 1/4 :wow 0.4 :feedback 0.6)))
(master (fx :tape :drive 0.4 :wow 0.2 :hiss 0.05))
(track :keys (->> pat (fx :vinyl :crackle 0.3 :age 0.5)))
```

## Background

- `app/public/plugins/reverb.js`: `ConvolverNode` with one procedurally generated
  impulse response; `setParam` knows `wet` and `dry` only. USAGE.md shows
  `(fx :reverb :wet 0.4 :room 0.8)`, but `room` is ignored.
- `app/public/plugins/dattorro-reverb.js` + `worklets/dattorro-reverb-processor.js`:
  plate reverb with `decay`, `damping`, `predelay`.
- `app/public/plugins/delay.js`: one `DelayNode` with a feedback `GainNode`; parameters
  `time` (seconds), `feedback` (max 0.95), `wet`.
- TL7b plans one convolver reverb and one feedback delay per orbit
  (`app/src/repulse/orbits.cljs`) for per-event sends; TL7c plans `delay-sync`.
- Tempo: effect plugins receive a host object in `init(host)`; whether it exposes the
  cycle length is to be checked (same question as FX1).
- Lo-fi today: `bitcrusher` (bits, sample rate) and the distortion family. No tape or
  vinyl character.
- Plugin protocol, auto-load list, slider ranges and docs: as described in PHASE-FX1.

## Scope

- `reverb`: `type` (`:room`, `:hall`, `:plate`, `:spring`, `:gated`, `:reverse`),
  `decay` (seconds), `size`, `damp`, `predelay`. Each type is an impulse-response
  generator, so the effect stays one `ConvolverNode`; the response is regenerated when a
  parameter changes.
- `delay`: `sync` (cycles, e.g. `3/16`), `mode` (`:mono`, `:stereo`, `:ping-pong`,
  `:tape`), `taps` (list of tap positions), `lowcut`/`highcut` in the feedback path;
  tape mode adds `wow`, `flutter` and saturation in the loop.
- `tape`: saturation, wow and flutter (slow and fast pitch drift), high-frequency
  roll-off, `hiss`.
- `vinyl`: crackle and pops, rumble, band limiting, `age`.
- `exciter`: high-passed copy → saturation → mixed back; `freq`, `amount`.
- Fix the USAGE.md `:room` example.

## Open design questions

- Regenerating an impulse response on every parameter change clicks or costs CPU while a
  slider moves. Crossfade between two convolvers, or debounce and swap?
- Spring reverb: a chirped (dispersive) impulse response is cheap and convincing enough?
  Decide by listening.
- Does the reverb work shared with TL7b belong in one module both use (IR generators),
  so orbit reverbs get `size` from the same code?
- `sync` needs the tempo. If the host cannot provide it, the `fx` built-in can convert
  cycles to seconds before `setParam` and re-send on BPM change.
- Noise sources (`hiss`, `crackle`) must be deterministic for offline export: generate a
  seeded noise buffer once and loop it.
- Does ping-pong belong on `delay` as a mode (proposed) or as its own plugin?

## Definition of done

- [ ] `reverb :type :hall :decay 3` fed one click: RT60 from the energy decay curve (Schroeder integration) is 3 s ± 20 %; `:decay 0.5` gives 0.5 s ± 20 %.
- [ ] `reverb :predelay 0.05`: the first wet energy arrives 50 ± 5 ms after the click.
- [ ] `reverb :type :gated :decay 0.3`: the tail level 100 ms after the gate time is ≥ 40 dB below its level just before it.
- [ ] `reverb :damp 0.9` vs `:damp 0.1`: energy above 4 kHz in the tail differs by ≥ 6 dB.
- [ ] `(fx :reverb 0.4)` with no other parameters sounds as before (RMS of a reference render within ±1 dB).
- [ ] `delay :sync 3/16` at 120 BPM: echoes are 375 ± 2 ms apart; after `(bpm 90)` they are 500 ± 2 ms apart.
- [ ] `delay :mode :ping-pong` fed one click: successive echoes alternate between left-only and right-only (channel ratio ≥ 20 dB each).
- [ ] `delay :taps [1/8 3/8]`: exactly two first-generation echoes at those positions.
- [ ] `tape :wow 1` on a 1 kHz sine: the measured pitch deviates by at least ±5 cents at a rate below 2 Hz; `:wow 0 :flutter 0 :drive 0 :hiss 0` is transparent (null test ≤ −60 dBFS).
- [ ] `vinyl :crackle 1` on silence produces impulses; two offline renders are bit-identical.
- [ ] Every effect renders in `(export …)` offline and works in a per-track chain.
- [ ] USAGE.md updated: reverb and delay parameter tables, new sections for `tape`, `vinyl`, `exciter`; the `:room` example corrected.
- [ ] Human listen check recorded in the PR: each reverb type on a snare, tape delay on a lead.
- [ ] `npm run test:all` and `npx shadow-cljs compile app` pass.
- [ ] The PR description ends with a test protocol (CLAUDE.md Rule 5).

## What NOT to do

- No per-event sends (`room`, `delay`, `orbit`): **TL7b**.
- No shimmer reverb or reverse-audio effects that need pitch shifting or buffering: **FX5**.
- No user impulse-response loading (parity row `ir` stays ❓); `cab` keeps its own IRs.
- No named tape-machine or record-player emulations.
- Don't change `dattorro-reverb`.
