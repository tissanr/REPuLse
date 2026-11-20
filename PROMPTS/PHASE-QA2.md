# Phase QA2 — Hardened, Allocation-Free Audio Engine

> **Spec status:** full
> **Automation:** 🤖 auto — for valid input the output is proven sample-identical to before
> by a golden render recorded before any code change (tolerance 1e-5, see Decision 9), and
> invalid input is checked numerically (finite output, voices end, counters). No listen
> check is needed: the phase changes no sound.

**Milestone:** none — quality track
**Depends on:** none

## Goal

The WASM engine in `packages/audio/src/lib.rs` must survive any input value and must not
allocate inside the render callback. Today one NaN parameter can silence the whole mix
until `stop_all`, and every 128-frame block allocates several heap buffers on the audio
thread. CLAUDE.md ("WASM synthesis — architectural decisions") already requires that
voice allocation happens at trigger time, never in the render callback. The current code
breaks that rule.

After this phase: every numeric input is sanitised at trigger time, invalid events are
dropped and counted, a voice that produces a non-finite sample is removed, voice and queue
sizes are capped, and `render` performs zero heap allocations after warm-up. Valid input
renders exactly as before.

## Background

Verified against `packages/audio/src/lib.rs` (2839 lines) and `app/public/worklet.js`.

### NaN / inf paths

- `activate` (≈ line 1514): `p.amp.clamp(0.0, 2.0)` and `p.pan.clamp(-1.0, 1.0)` pass NaN
  through. `p.attack` and `p.decay` are used raw (`decay_rate`, `attack_inc`, FM times).
- Frequencies are parsed with `parse::<f64>()`/`parse::<f32>()` in the `saw:`, `square:`,
  `fm:` (legacy `fm:<hz>:<index>:<ratio>` and preset `fm:<preset>:<hz>`), `ks:` branches
  and the bare-number tone branch. Rust accepts `"NaN"`, `"inf"`, `"-5"`, `"0"`. Index,
  ratio and pulse width are parsed the same way.
- FM legacy branch: `release_time: p.decay * 0.6`; `Voice::tick` divides by
  `(*release_time + 0.01)`. A decay of ≈ −0.0167 gives a zero divisor; any negative
  decay gives a negative release.
- `render` (≈ line 1425): the output clamp `(x).clamp(-1.0, 1.0)` passes NaN. A NaN voice
  never satisfies `is_silent` (`NaN < 1e-5` is false), so it is never removed.
- A pending event with a NaN time fails `p.time < block_end`, so it is deferred on every
  block and carried forever. `start_transition` accepts NaN start/end, so
  `amp_scale` can become NaN.
- `finite_or(x, d)` (≈ line 873) already exists and is used by `Inserts::new`,
  `Sends::new`, `EnvSpec::from_slots` and `Tl7cParams::from_slots`. Slots 4–40 are
  therefore already safe. Slots 0–3 (amp, attack, decay, pan) and the value string are not.

### Allocations in the render path

- `render` allocates `deferred: Vec<Pending>` and `scheduled: Vec<Vec<Pending>>` of
  length n on every block, and moves the whole queue with `std::mem::take`.
- `activate` runs inside `render`. It allocates the KS delay buffer (`vec![0.0; 2205]`),
  `fm_body_filters`/`ks_body_filters` Vecs, the `splitn(..).collect()` Vecs, and may grow
  `self.voices`.
- `process_block_raw` allocates `vec![0.0; n * 2]` per block. `process_block` and
  `process_block_buses` create a new JS `Float32Array` per block.
- `process_block_buses_raw` already reuses `self.bus` (TL7b test
  `bus_buffer_is_not_reallocated_across_1000_blocks`).

### Callers

- `app/public/worklet.js` `process()` calls `engine.process_block_buses(n, currentTime)`
  when the node has more than one output (production path, TL7b) and
  `engine.process_block(n, currentTime)` otherwise. `_onMessage` calls `trigger`,
  `trigger_v2`, `trigger_v3`, `stop_all`, `start_transition`. `initSync(...)` returns the
  wasm exports object, which has `memory`.
- `app/src/repulse/audio.cljs` `worklet-trigger!` / `worklet-trigger-v3!` (≈ line 435) post
  the trigger messages. Value strings are built in the `:synth` dispatch (≈ line 735):
  `"saw:" hz`, `"square:" hz ":" pw`, `"fm:" hz ":" index ":" ratio`,
  `"fm:" preset ":" hz`, `"ks:" preset ":" hz`. No CLJS change is needed in this phase.

### Existing bit-exact tests

`tl7a_insert_tests::GOLDEN` (`v2_fingerprint`), `tl7c_tests::all_default_tl7c_slots_are_bit_identical`,
`filter_envelope_parity_pinned` and `send_tests::dry_bus_is_bit_identical_to_process_block_raw`
hash rendered output. They must keep passing unchanged.

## Decisions

1. **Sanitise at trigger time with `finite_or` + clamp**, using the existing trigger_v3
   slot defaults for non-finite values: amp `finite_or(x, 1.0).clamp(0.0, 2.0)`, attack
   `finite_or(x, 0.001).clamp(0.0, 60.0)`, decay `finite_or(x, 1.5).clamp(0.0, 600.0)`,
   pan `finite_or(x, 0.0).clamp(-1.0, 1.0)`, square pulse width
   `finite_or(pw, 0.5).clamp(0.01, 0.99)`, legacy FM index `finite_or(i, 1.0).clamp(0.0, 100.0)`,
   ratio `finite_or(r, 2.0).clamp(0.0, 100.0)`. Clamping decay at 0 fixes the FM divisor
   (`release_time + 0.01 ≥ 0.01`). These ranges leave every documented value unchanged.
2. **Frequency:** a missing or unparsable frequency keeps today's default 440 Hz (unchanged
   behaviour). A frequency that parses but is non-finite, ≤ 0 or > 1e6 **drops the event**.
3. **Time:** a non-finite event time drops the event. A finite time in the past still fires
   at sample 0 of the next block, as today.
4. **Transitions:** `start_transition` with a non-finite `start` or `end` is ignored (the
   current transition stays) and counted as an invalid drop. No `warn` call on that path
   (`warn`/`log` panic on non-wasm targets, so tests could not reach it).
5. **Safety net in the mix:** if a voice's sample (after its inserts) is not finite, the
   voice gets `dead = true`, contributes nothing, and is removed at the block-end `retain`.
   A non-finite final dry sample is written as 0.0. Valid input never takes these branches.
6. **Voices are built at trigger time.** `trigger`, `trigger_v2`, `trigger_v3` (and the test
   helpers `trigger_raw`, `trigger_raw_v2`) validate, build the complete `ActiveVoice`
   (KS buffer, body filters, inserts, mods, seed) and insert it into the pending queue.
   `render` only moves due voices into `voices`. The seed is drawn after validation, so
   dropped events consume no seed.
   *Consequence:* seeds are drawn in trigger order, not activation order. For events
   triggered in time order (the scheduler and every test do this) the result is
   bit-identical. Out-of-order triggers get a different noise realisation, which is
   statistically the same sound.
7. **Pending queue:** `pending: Vec<Scheduled>` with `struct Scheduled { time: f64, voice:
   ActiveVoice }`, kept sorted by **descending** time, so the next due event is `last()`
   and draining is `pop()` (no shifting, no allocation). Insert at
   `partition_point(|q| q.time > t)`, so equal times activate in trigger order. Reserve
   1024 at construction. Growth happens only at trigger time, never in `render`.
8. **Caps:** `const MAX_VOICES: usize = 256` and `const MAX_PENDING: usize = 8192`.
   `voices` is created with `Vec::with_capacity(MAX_VOICES)`. A trigger while
   `pending.len() == MAX_PENDING` is dropped before building the voice. A due voice while
   `voices.len() == MAX_VOICES` is dropped. No voice stealing. Counters (u32, saturating),
   each with a `#[wasm_bindgen]` getter of the same name: `invalid_drops`,
   `queue_cap_drops`, `voice_cap_drops`, `nonfinite_kills`. `stop_all` does not reset them.
9. **Phase wrap.** Cycle-unit f64 accumulators (`phase` of Kick, Snare, Tone, Saw, Square;
   FM `carrier_phase`, `mod_phase`) wrap with `if *p >= 1.0 { *p -= p.floor(); }` right
   after their increment. KS `vib_phase` (f32, radians) wraps with
   `if *vib_phase >= TAU { *vib_phase -= TAU; }`. All increments are ≥ 0 after Decisions 1–2.
   Measured while writing this spec (28 voice kinds, 5 s, 8 notes each): max abs difference
   to the unwrapped engine 1.03e-6 (FM `sax`), all other kinds ≤ 1.2e-7. **Tolerance: 1e-5
   absolute per sample (−100 dBFS).** The existing bit-exact hash tests above still pass with
   the wrap (also verified): if one fails, the cause is the refactor, not the wrap.
   FM `env_phase` and the TL7c `Mods` clock are not changed (both would change output).
10. **KS buffer from the sample rate:** `fn ks_buf_cap(sr: f32) -> usize {
    ((sr / 20.0).ceil() as usize).max(2) }`. The buffer is `vec![0.0; ks_buf_cap(sr)]` and
    `buf_len` clamps to `2..=ks_buf_cap(sr)`. At 44.1 kHz this is 2205, exactly as today.
    At 48/96 kHz notes down to 20 Hz now get their correct length instead of being clamped.
    The rest of the CLAUDE.md decision (max-size buffer per voice) stays.
11. **Output buffers.** New field `out: Vec<f32>` (`BUS_FRAMES * 2`), grown only when
    n > its length (never for 128-frame quanta), like `bus`. New methods:
    `fn process_block_into(&mut self, n, t) -> &[f32]` (no allocation);
    `#[wasm_bindgen] pub fn render_block(&mut self, n_samples: u32, current_time: f64) -> *const f32`
    and `pub fn render_block_buses(...) -> *const f32` return pointers into `out` / `bus`.
    `process_block_raw` stays (returns `self.process_block_into(..).to_vec()`, used by tests).
    `process_block` and `process_block_buses` stay as thin wrappers for compatibility, with a
    doc comment that they allocate and are not used by `worklet.js`.
12. **Worklet reads WASM memory directly.** `worklet.js` keeps `this.wasm = initSync(...)`
    and caches a `Float32Array` view `new Float32Array(this.wasm.memory.buffer, ptr, len)`.
    It rebuilds the view only when `view.buffer !== this.wasm.memory.buffer` (memory grew),
    or `ptr`/`len` changed. No per-block JS allocation.
13. **Module split only for tests.** Two new test-only files, declared from `lib.rs` with
    `#[cfg(test)] mod alloc_count;` and `#[cfg(test)] mod qa2_tests;` (child modules see the
    crate's private items via `super::`/`crate::`). No production code moves out of
    `lib.rs`. The full module split is QA7.
14. **Deallocation in render is allowed in this phase.** Removing a finished voice drops its
    KS buffer and body-filter Vec inside `render`. The allocator test counts `alloc`,
    `alloc_zeroed` and `realloc`, not `dealloc`. A voice pool is out of scope.

## Implementation

Work in this order. Each step ends with `cargo test --manifest-path packages/audio/Cargo.toml`
green.

### 0. Record the golden render FIRST (before any engine change)

Create `packages/audio/src/qa2_tests.rs` with only the fixture, the recorder and the golden
test. Make `tl7c_tests::base_slots` `pub(crate)` and reuse it.

Fixture `fn golden_engine() -> AudioEngine` at `SR = 44100.0` with `new_for_test`, using
only APIs that survive this phase (`trigger`, `trigger_v2`, `trigger_v3`,
`start_transition`, `process_block_buses_raw`):

- `KINDS` (28): `"440"`, `"saw:261.63"`, `"square:220:0.3"`, `"noise"`, `"bd"`, `"sd"`,
  `"hh"`, `"oh"`, `"fm:220:3.5:1.5"`, `"fm:<p>:220"` for every preset in `fm_preset`
  (`sax trumpet trumpet-muted trombone synth-brass harmon-out harmon-in epiano bell marimba
  flute`), `"ks:<p>:110"` for every preset in `ks_preset` (`western nylon guitar harp koto
  pizz lute mandolin`).
- For `i, k` in `KINDS`: `t = i as f64 * 0.025`; slots = `base_slots()` with amp 0.3,
  attack 0.005, decay 0.8, pan `[-0.6, 0.0, 0.6][i % 3]`; if `i % 3 == 0` lpf 1500 /
  resonance 2 (slots 4, 5); if `i % 4 == 1` room 0.4, orbit 1 (12, 14); if `i % 5 == 2`
  delay 0.5, orbit 2 (13, 14); if `i % 6 == 3` vowel 2, coarse 3 (38, 39); if `i % 7 == 4`
  pitch env depth −12 (slot 31), vib 5 (36). Then `trigger_v3(k, t, &slots)`.
- Interleaved in time order: `trigger("sd", 0.1)` and `trigger_v2("hh", 0.1, 0.5, 0.001,
  0.2, -0.3)` right after the `i = 4` event (three events at one sample), and
  `trigger_v2("oh", 0.1001, 0.4, 0.001, 0.5, 0.3)` (same block).
- Render 345 blocks of 128 frames, `current_time = b * 128 / SR`. Before block 200 call
  `start_transition("amp", 1.0, 0.6, 11025, "sine")`; before block 250
  `start_transition("pan", 0.0, -0.5, 8000, "linear")`.

Snapshot file `packages/audio/src/golden/qa2.bin` (little-endian f32): the dry bus
(bus 0) of every block, interleaved, 345·128·2 values, then the per-block energy
`Σ s²` of each of the 9 buses, 345·9 values (block-major). `#[test] #[ignore] fn
record_qa2_golden()` writes it to `concat!(env!("CARGO_MANIFEST_DIR"), "/src/golden/qa2.bin")`.
Run it once on the unmodified engine (`cargo test -- --ignored record_qa2_golden`), commit
the file, and never re-run the recorder after step 1.

`#[test] fn golden_render_matches()` loads it with `include_bytes!` and asserts: every dry
sample within 1e-5 (report the first failing block/sample/value pair), and every bus energy
within `1e-4 * max(expected, 1e-12)` relative, with expected 0.0 requiring exactly 0.0.
It must pass on the unmodified engine before step 1 starts.

### 1. Sanitising (Decisions 1–5)

- Replace `Pending`'s per-event fields with `struct EventParams { amp, attack, decay, pan,
  inserts, sends, tl7c }`. `pending_v3` becomes `params_v3(params: &[f32]) -> EventParams`;
  update its two callers in `tl7a_insert_tests` and `send_tests` (field names unchanged).
- `fn parse_freq(s: Option<&str>) -> Option<f64>`: `None`/unparsable → `Some(440.0)`;
  parsed and finite and `0.0 < f <= 1e6` → `Some(f)`; else `None` (drop).
- Apply Decision 1 clamps in one place (`EventParams::sanitised(self) -> Self`).
- Add `dead: bool` to `ActiveVoice`; in `render` check `s.is_finite()` after
  `av.inserts.tick(..)`; final dry samples use `if x.is_finite() { x.clamp(-1.0, 1.0) } else { 0.0 }`.
- `start_transition`: ignore non-finite start/end (Decision 4).

### 2. Build at trigger time, sorted queue, caps (Decisions 6–8, 10)

- `fn build_voice(&mut self, value: &str, p: &EventParams) -> Option<ActiveVoice>` = today's
  `activate` body without the `push`es, returning `None` for a dropped frequency. Keep every
  formula and the order of `next_seed()` / `lcg_next(&mut self.noise_seed)` calls as they are.
- `fn schedule(&mut self, value: &str, time: f64, p: EventParams)`: time check → queue cap →
  `build_voice` → sorted insert. All trigger entry points call it.
- `render`: remove `deferred`, `scheduled` and `mem::take`. Per sample `i`, pop while the
  last item's sample index (same formula as today: 0 if `time <= current_time`, else
  `floor((time - current_time) * sr)` clamped to `n - 1`; not due if `time >= block_end`)
  is `<= i`, and push into `voices` unless at `MAX_VOICES`.
- `new` and `new_for_test` share one private constructor that reserves `voices`,
  `pending` (1024), `out` and `bus`.

### 3. Output buffers and worklet (Decisions 11–12)

Add `out`, `process_block_into`, `render_block`, `render_block_buses`. In `worklet.js`:
store `this.wasm = initSync({ module: msg.wasmBytes })`; add `_view(ptr, len)` per Decision
12; `process()` calls `render_block_buses` / `render_block` and reads from the view. The
channel-splitting loops stay as they are.

### 4. Phase wrap (Decision 9)

Add the wraps. Run the golden test and the existing hash tests.

### 5. Tests — `packages/audio/src/alloc_count.rs` and `qa2_tests.rs`

`alloc_count.rs`: a `#[global_allocator]` wrapping `std::alloc::System` that increments a
`thread_local!` `Cell<u64>` (const-initialised) in `alloc`, `alloc_zeroed` and `realloc`
while a thread-local `Cell<bool>` is set; `pub(crate) fn count_allocs<R>(f: impl FnOnce() -> R)
-> (R, u64)`. Thread-local counting keeps parallel tests from interfering.

`qa2_tests.rs` (each a `#[test]`):

- `nonfinite_slots_render_finite`: for kinds `"440"`, `"saw:220"`, `"square:220:0.5"`,
  `"fm:220:2:2"`, `"fm:sax:220"`, `"ks:guitar:110"`, `"bd"`, `"noise"` and each of slots
  0–3 set to NaN, +inf, −inf, −1.0: render 10 s in 128-frame blocks; every sample finite,
  `voices` empty at the end, `nonfinite_kills == 0`.
- `invalid_frequencies_are_dropped`: `"NaN"`, `"inf"`, `"-5"`, `"0"`, `"saw:NaN"`,
  `"saw:-5"`, `"square:inf:0.5"`, `"fm:0:1:2"`, `"fm:sax:NaN"`, `"fm:sax:-5"`,
  `"ks:guitar:inf"`, `"ks:guitar:0"` → `pending` stays empty, `invalid_drops == 12`, output
  exactly 0.0. `"fm:440:NaN:2"` and `"square:220:NaN"` play (finite, non-silent).
- `fm_negative_decay_ends`: legacy `"fm:220:2:2"` and `"fm:sax:220"` with decay −0.0167 and
  −1.0: output finite, voice gone within 2 s.
- `nonfinite_time_is_dropped`: times NaN, +inf → not queued, `invalid_drops == 2`.
- `nonfinite_voice_is_killed`: push a `Voice::Tone` with `gain: f32::NAN, in_attack: false`
  directly into `voices`; one block → all samples finite, `voices` empty,
  `nonfinite_kills == 1`. Plus `start_transition("amp", NAN, 1.0, 100, "linear")` leaves
  `amp_transition` `None`.
- `voice_cap`: 300 × `"ks:harp:110"` at t = 0 → after one block `voices.len() == 256`,
  `voice_cap_drops == 44`.
- `pending_cap`: 9000 × `"bd"` at t = 100.0 → `pending.len() == 8192`,
  `queue_cap_drops == 808`.
- `queue_order`: trigger `"noise"` at t = 0.002, then `"noise"` at t = 0.001 → after one
  block the first non-zero dry sample is index 44 and `voices` holds the t = 0.001 voice
  first. Three events at one time (`"bd"`, `"sd"`, `"hh"`) activate in trigger order (check
  the `voices` variants).
- `phase_wraps_on_long_notes`: `"440"` with decay 600, render 10 s → the Tone `phase` is in
  `[0, 1)`; `"ks:koto:330"`, render 1 s → its `vib_phase` is in `[0, TAU)`.
- `ks_buffer_follows_sample_rate`: `ks_buf_cap(44100.0) == 2205`, `ks_buf_cap(96000.0) == 4800`;
  `"ks:guitar:20"` at 96 kHz has `buf_len == 4800`.
- `render_is_allocation_free`: warm up 50 blocks, then 2000 blocks; before every 8th block
  (outside the counted region) trigger all 28 `KINDS` with mixed inserts/sends/TL7c slots,
  and once 300 events at one time (hits the voice cap). Count around each
  `process_block_into(128, t)` and `process_block_buses_raw(128, t)`: total == 0.
- `golden_render_matches` from step 0.

## Files to change

| File | Change |
|---|---|
| `packages/audio/src/lib.rs` | sanitising, `EventParams`, `build_voice`/`schedule`, sorted queue, caps + counters, `dead` flag, phase wrap, `ks_buf_cap`, `out` buffer, `render_block*`, test module declarations |
| `packages/audio/src/alloc_count.rs` | new, test-only counting allocator |
| `packages/audio/src/qa2_tests.rs` | new, test-only: golden fixture/recorder and the tests above |
| `packages/audio/src/golden/qa2.bin` | new, golden snapshot (recorded in step 0) |
| `app/public/worklet.js` | keep `wasm` exports, cached memory view, `render_block*` |
| `CLAUDE.md` | KS bullet: buffer `ceil(sr/20)` samples, built at trigger time; caps 256/8192 |
| `ROADMAP.md` | mark QA2 delivered, *Where we stand* |

No grammar, completions, `builtin_meta.edn` or `docs/USAGE.md` changes (no user-facing
syntax). `docs/ARCHITECTURE.md`: update the audio paragraph if it mentions the pending
queue or `process_block`.

## Definition of done

- [ ] `golden/qa2.bin` was committed in a commit that contains no engine change, and
      `golden_render_matches` passed on that commit and passes at the end.
- [ ] The existing bit-exact tests (`trigger_v2_render_is_bit_identical_to_pre_tl7a`,
      `all_default_tl7c_slots_are_bit_identical`, `filter_envelope_parity_pinned`,
      `dry_bus_is_bit_identical_to_process_block_raw`) pass without changing their constants.
- [ ] All `qa2_tests` above pass; `render_is_allocation_free` reports 0 allocations.
- [ ] `render`, `process_block_into`, `process_block_buses_raw` contain no `Vec::new`,
      `vec!`, `collect`, `to_string`, `format!` or `push` onto a Vec without reserved capacity.
- [ ] `worklet.js` `process()` creates no typed array per block (only on memory growth).
- [ ] `npm run test:all` passes, including `e2e/export-parity.spec.ts` (real WASM worklet)
      and `e2e/audio-render.spec.ts` (offline-js regression), on a fresh `npm run build:wasm`.
- [ ] `npx shadow-cljs compile app` reports no warnings.
- [ ] CLAUDE.md and ROADMAP.md updated. The PR ends with a `## Test protocol` section:
      regressions (a drum pattern, `(synth :saw)`, an FM preset, a KS preset, `(room …)`
      sends, a TL7c filter envelope — each "sounds as before"). Invalid-input cases are
      covered by the Rust tests; the protocol says so instead of inventing a browser check.

## What NOT to do

- No voice stealing, limiter, headroom change or denormal flush (QA6). No sound design change.
- Don't change any formula, preset value or the order of RNG calls inside voice building.
- Don't re-run `record_qa2_golden` after step 0 or loosen its tolerances to make a test pass.
  A golden failure is a refactor bug.
- Don't change existing hash constants (`GOLDEN`, TL7c fingerprints).
- Don't split production code out of `lib.rs` (QA7). Don't add crates or change `crate-type`.
- Don't move FM `env_phase` to f64 or change the `Mods` clock.
- Don't change the CLJS message format or slot layout.
