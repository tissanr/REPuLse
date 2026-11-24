//! Phase QA2 — hardened, allocation-free engine. Test-only module.
//!
//! `golden_render_matches` compares the engine against `golden/qa2.bin`, a
//! render recorded on the engine *before* any QA2 change. Never re-record it:
//! a golden failure is a refactor bug, not a reason to update the snapshot.

use crate::alloc_count::count_allocs;
use crate::tl7c_tests::base_slots;
use crate::*;

const SR: f32 = 44100.0;
const BLOCKS: usize = 345;
const FRAMES: usize = 128;

/// The 28 voice kinds of the spec, plus every FM preset added since
/// (`trumpet-cup`) and the SYN6 voices (oscillators with unison, noise
/// colours, the synth kit incl. the hat choke), so the trigger-time refactor
/// is pinned for every voice the engine builds.
pub(crate) fn kinds() -> Vec<String> {
    let mut k: Vec<String> = [
        "440",
        "saw:261.63",
        "square:220:0.3",
        "noise",
        "bd",
        "sd",
        "hh",
        "oh",
        "fm:220:3.5:1.5",
    ]
    .iter()
    .map(|s| s.to_string())
    .collect();
    for p in FM_PRESETS {
        k.push(format!("fm:{p}:220"));
    }
    for p in KS_PRESETS {
        k.push(format!("ks:{p}:110"));
    }
    for s in [
        "drum:bd",
        "drum:oh",
        "drum:hh",
        "drum:cp",
        "saw:110:unison=3:spread=0.8",
        "tri:330",
        "sine:220:glide=0.1:glide-from=110",
        "noise:color=pink",
    ] {
        k.push(s.to_string());
    }
    k
}

/// trigger_v3 slots of event i (see the spec's fixture).
pub(crate) fn event_slots(i: usize) -> Vec<f32> {
    let mut s = base_slots();
    s[0] = 0.3;
    s[1] = 0.005;
    s[2] = 0.8;
    s[3] = [-0.6, 0.0, 0.6][i % 3];
    if i.is_multiple_of(3) {
        s[4] = 1500.0;
        s[5] = 2.0;
    }
    if i % 4 == 1 {
        s[12] = 0.4;
        s[14] = 1.0;
    }
    if i % 5 == 2 {
        s[13] = 0.5;
        s[14] = 2.0;
    }
    if i % 6 == 3 {
        s[38] = 2.0;
        s[39] = 3.0;
    }
    if i % 7 == 4 {
        s[31] = -12.0;
        s[36] = 5.0;
    }
    s
}

fn golden_engine() -> AudioEngine {
    let mut e = AudioEngine::new_for_test(SR);
    for (i, k) in kinds().iter().enumerate() {
        let t = i as f64 * 0.025;
        e.trigger_v3(k, t, &event_slots(i));
        if i == 4 {
            // three events at one sample (t = 0.1), then one in the same block
            e.trigger("sd", 0.1);
            e.trigger_v2("hh", 0.1, 0.5, 0.001, 0.2, -0.3);
            e.trigger_v2("oh", 0.1001, 0.4, 0.001, 0.5, 0.3);
        }
    }
    e
}

/// Dry bus of every block (interleaved), then the energy Σs² of each bus per
/// block (block-major).
fn golden_render() -> (Vec<f32>, Vec<f32>) {
    let mut e = golden_engine();
    let mut dry = Vec::with_capacity(BLOCKS * FRAMES * 2);
    let mut energy = Vec::with_capacity(BLOCKS * N_BUSES);
    for b in 0..BLOCKS {
        if b == 200 {
            e.start_transition("amp", 1.0, 0.6, 11025, "sine");
        }
        if b == 250 {
            e.start_transition("pan", 0.0, -0.5, 8000, "linear");
        }
        let t = (b * FRAMES) as f64 / SR as f64;
        let out = e.process_block_buses_raw(FRAMES as u32, t);
        let stride = FRAMES * 2;
        dry.extend_from_slice(&out[..stride]);
        for bus in 0..N_BUSES {
            let s: f64 = out[bus * stride..(bus + 1) * stride]
                .iter()
                .map(|&x| (x as f64) * (x as f64))
                .sum();
            energy.push(s as f32);
        }
    }
    (dry, energy)
}

const GOLDEN_PATH: &str = concat!(env!("CARGO_MANIFEST_DIR"), "/src/golden/qa2.bin");

/// Records the golden snapshot. Run once on the pre-QA2 engine only:
/// `cargo test -- --ignored record_qa2_golden`.
#[test]
#[ignore]
fn record_qa2_golden() {
    let (dry, energy) = golden_render();
    let bytes: Vec<u8> = dry
        .iter()
        .chain(energy.iter())
        .flat_map(|x| x.to_le_bytes())
        .collect();
    std::fs::create_dir_all(concat!(env!("CARGO_MANIFEST_DIR"), "/src/golden")).unwrap();
    std::fs::write(GOLDEN_PATH, bytes).unwrap();
}

#[test]
fn golden_render_matches() {
    let bytes: &[u8] = include_bytes!("golden/qa2.bin");
    let want: Vec<f32> = bytes
        .chunks_exact(4)
        .map(|c| f32::from_le_bytes([c[0], c[1], c[2], c[3]]))
        .collect();
    let n_dry = BLOCKS * FRAMES * 2;
    assert_eq!(want.len(), n_dry + BLOCKS * N_BUSES, "golden file size");
    let (dry, energy) = golden_render();
    let mut max_diff = 0.0f32;
    for (k, (&got, &exp)) in dry.iter().zip(&want[..n_dry]).enumerate() {
        let d = (got - exp).abs();
        max_diff = max_diff.max(d);
        assert!(
            d <= 1e-5,
            "dry block {} sample {}: got {got}, expected {exp}",
            k / (FRAMES * 2),
            k % (FRAMES * 2)
        );
    }
    for (k, (&got, &exp)) in energy.iter().zip(&want[n_dry..]).enumerate() {
        let (blk, bus) = (k / N_BUSES, k % N_BUSES);
        if exp == 0.0 {
            assert_eq!(
                got, 0.0,
                "bus {bus} block {blk}: expected silence, got {got}"
            );
        } else {
            let tol = 1e-4 * exp.max(1e-12);
            assert!(
                (got - exp).abs() <= tol,
                "bus {bus} block {blk}: energy {got}, expected {exp}"
            );
        }
    }
    eprintln!("golden_render_matches: max dry diff {max_diff:e}");
}

// ── Hardening tests ─────────────────────────────────────────────────────────

/// Render up to `secs` in 128-frame blocks (stops early once the engine is
/// idle: the rest would be exact silence). Returns the dry output.
fn render_secs(e: &mut AudioEngine, secs: f32) -> Vec<f32> {
    let blocks = (secs * SR / FRAMES as f32).ceil() as usize;
    let mut out = Vec::new();
    for b in 0..blocks {
        let t = (b * FRAMES) as f64 / SR as f64;
        out.extend_from_slice(e.process_block_into(FRAMES as u32, t));
        if e.voices.is_empty() && e.pending.is_empty() {
            break;
        }
    }
    out
}

fn rms(x: &[f32]) -> f32 {
    (x.iter().map(|s| s * s).sum::<f32>() / x.len().max(1) as f32).sqrt()
}

#[test]
fn nonfinite_slots_render_finite() {
    let kinds = [
        "440",
        "saw:220",
        "square:220:0.5",
        "fm:220:2:2",
        "fm:sax:220",
        "ks:guitar:110",
        "bd",
        "noise",
    ];
    for k in kinds {
        for slot in 0..4 {
            for bad in [f32::NAN, f32::INFINITY, f32::NEG_INFINITY, -1.0] {
                let mut e = AudioEngine::new_for_test(SR);
                let mut s = base_slots();
                s[slot] = bad;
                e.trigger_v3(k, 0.0, &s);
                // A valid 110 Hz guitar note at amp 1 rings for ~18 s
                // (feedback 0.997), so plucked strings get 30 s.
                let secs = if k.starts_with("ks:") { 30.0 } else { 10.0 };
                let out = render_secs(&mut e, secs);
                assert!(
                    out.iter().all(|x| x.is_finite()),
                    "{k} slot {slot} = {bad}: non-finite output"
                );
                assert!(
                    e.voices.is_empty(),
                    "{k} slot {slot} = {bad}: voice still sounding"
                );
                assert_eq!(e.nonfinite_kills(), 0, "{k} slot {slot} = {bad}");
            }
        }
    }
}

#[test]
fn invalid_frequencies_are_dropped() {
    let bad = [
        "NaN",
        "inf",
        "-5",
        "0",
        "saw:NaN",
        "saw:-5",
        "square:inf:0.5",
        "fm:0:1:2",
        "fm:sax:NaN",
        "fm:sax:-5",
        "ks:guitar:inf",
        "ks:guitar:0",
    ];
    let mut e = AudioEngine::new_for_test(SR);
    for v in bad {
        e.trigger(v, 0.0);
    }
    assert!(e.pending.is_empty());
    assert_eq!(e.invalid_drops(), 12);
    let out = render_secs(&mut e, 0.1);
    assert!(out.iter().all(|&x| x == 0.0));

    for v in ["fm:440:NaN:2", "square:220:NaN"] {
        let mut e = AudioEngine::new_for_test(SR);
        e.trigger(v, 0.0);
        let out = render_secs(&mut e, 0.5);
        assert!(out.iter().all(|x| x.is_finite()), "{v}");
        assert!(rms(&out) > 0.001, "{v} should play");
        assert_eq!(e.invalid_drops(), 0, "{v}");
    }
}

#[test]
fn fm_negative_decay_ends() {
    for v in ["fm:220:2:2", "fm:sax:220"] {
        for decay in [-0.0167, -1.0] {
            let mut e = AudioEngine::new_for_test(SR);
            e.trigger_v2(v, 0.0, 0.8, 0.001, decay, 0.0);
            let out = render_secs(&mut e, 2.0);
            assert!(out.iter().all(|x| x.is_finite()), "{v} decay {decay}");
            assert!(
                e.voices.is_empty(),
                "{v} decay {decay}: still sounding after 2 s"
            );
        }
    }
}

#[test]
fn nonfinite_time_is_dropped() {
    let mut e = AudioEngine::new_for_test(SR);
    e.trigger("bd", f64::NAN);
    e.trigger_v2("440", f64::INFINITY, 1.0, 0.001, 1.0, 0.0);
    assert!(e.pending.is_empty());
    assert_eq!(e.invalid_drops(), 2);
}

#[test]
fn nonfinite_voice_is_killed() {
    let mut e = AudioEngine::new_for_test(SR);
    let inserts = Inserts::new(&InsertParams::OFF, &Tl7cParams::OFF, SR);
    e.voices.push(ActiveVoice {
        voice: Voice::Tone {
            phase: 0.0,
            freq: 440.0,
            amp: 0.5,
            gain: f32::NAN,
            gain_decay: decay_rate(1.0, SR),
            attack_inc: 0.01,
            in_attack: false,
        },
        pan: 0.0,
        mods: Mods::new(&Tl7cParams::OFF, inserts.has_envelope()),
        inserts,
        inserts_b: None,
        sends: Sends::OFF,
        dead: false,
        meter: 3,
    });
    // a healthy voice keeps playing next to it
    e.trigger("440", 0.0);
    let out = e.process_block_buses_raw(FRAMES as u32, 0.0).to_vec();
    assert!(out.iter().all(|x| x.is_finite()));
    assert!(
        rms(&out[..FRAMES * 2]) > 0.001,
        "the valid voice still sounds"
    );
    assert_eq!(e.voices.len(), 1, "only the NaN voice is removed");
    assert!(matches!(e.voices[0].voice, Voice::Tone { gain, .. } if gain.is_finite()));
    assert_eq!(e.nonfinite_kills(), 1);

    e.start_transition("amp", f32::NAN, 1.0, 100, "linear");
    e.start_transition("pan", 0.0, f32::INFINITY, 100, "linear");
    assert!(e.amp_transition.is_none());
    assert!(e.pan_transition.is_none());
    assert_eq!(e.invalid_drops(), 2);
}

#[test]
fn voice_cap() {
    let mut e = AudioEngine::new_for_test(SR);
    for _ in 0..300 {
        e.trigger("ks:harp:110", 0.0);
    }
    e.process_block_into(FRAMES as u32, 0.0);
    assert_eq!(e.voices.len(), MAX_VOICES);
    assert_eq!(e.voices.len(), 256);
    assert_eq!(e.voice_cap_drops(), 44);
    assert!(e.voices.capacity() == MAX_VOICES, "voices never grows");
}

#[test]
fn pending_cap() {
    let mut e = AudioEngine::new_for_test(SR);
    for _ in 0..9000 {
        e.trigger("bd", 100.0);
    }
    assert_eq!(e.pending.len(), MAX_PENDING);
    assert_eq!(e.pending.len(), 8192);
    assert_eq!(e.queue_cap_drops(), 808);
    // stop_all keeps the counters
    e.stop_all();
    assert!(e.pending.is_empty());
    assert_eq!(e.queue_cap_drops(), 808);
}

#[test]
fn queue_order() {
    // out-of-order triggers activate in time order
    let mut e = AudioEngine::new_for_test(SR);
    e.trigger_v2("noise", 0.002, 0.5, 0.001, 1.5, 0.0);
    e.trigger_v2("noise", 0.001, 1.0, 0.001, 1.5, 0.0);
    let out = e.process_block_into(FRAMES as u32, 0.0).to_vec();
    let first = out.iter().position(|&x| x != 0.0).unwrap() / 2;
    assert_eq!(first, 44, "t = 0.001 s is frame 44 at 44.1 kHz");
    assert_eq!(e.voices.len(), 2);
    let gains: Vec<f32> = e
        .voices
        .iter()
        .map(|av| match &av.voice {
            Voice::Noise(n) => n.gain,
            _ => panic!("noise voice expected"),
        })
        .collect();
    assert!(
        gains[0] > gains[1],
        "the t = 0.001 voice (amp 1.0) comes first: {gains:?}"
    );

    // equal times activate in trigger order
    let mut e = AudioEngine::new_for_test(SR);
    e.trigger("bd", 0.001);
    e.trigger("sd", 0.001);
    e.trigger("hh", 0.001);
    e.process_block_into(FRAMES as u32, 0.0);
    assert_eq!(e.voices.len(), 3);
    assert!(matches!(e.voices[0].voice, Voice::Kick { .. }));
    assert!(matches!(e.voices[1].voice, Voice::Snare { .. }));
    assert!(matches!(e.voices[2].voice, Voice::Hihat { .. }));
}

#[test]
fn phase_wraps_on_long_notes() {
    let mut p = PHASE_WRAP - 0.25 + 0.5;
    wrap_cycle(&mut p);
    assert_eq!(p, 0.25);
    let mut q = 0.75;
    wrap_cycle(&mut q);
    assert_eq!(q, 0.75, "below the threshold nothing changes");

    // 10 s at 440 Hz = 4400 cycles: the phase must have wrapped (several times)
    let mut e = AudioEngine::new_for_test(SR);
    e.trigger_v2("440", 0.0, 0.8, 0.001, 600.0, 0.0);
    render_secs(&mut e, 10.0);
    match e.voices[0].voice {
        Voice::Tone { phase, .. } => {
            assert!((0.0..PHASE_WRAP).contains(&phase), "phase {phase}");
            // exact cycle count mod PHASE_WRAP, within accumulated rounding
            let n = (10.0 * SR / FRAMES as f32).ceil() as f64 * FRAMES as f64;
            let want = (n * 440.0 / SR as f64) % PHASE_WRAP;
            assert!((phase - want).abs() < 1e-6, "phase {phase}, want {want}");
        }
        _ => panic!("tone expected"),
    }

    let mut e = AudioEngine::new_for_test(SR);
    e.trigger("ks:koto:330", 0.0);
    render_secs(&mut e, 1.0);
    match e.voices[0].voice {
        Voice::KarplusStrong { vib_phase, .. } => {
            assert!(
                (0.0..std::f32::consts::TAU).contains(&vib_phase),
                "vib_phase {vib_phase}"
            );
        }
        _ => panic!("ks voice expected"),
    }
}

#[test]
fn ks_buffer_follows_sample_rate() {
    assert_eq!(ks_buf_cap(44100.0), 2205);
    assert_eq!(ks_buf_cap(48000.0), 2400);
    assert_eq!(ks_buf_cap(96000.0), 4800);
    let mut e = AudioEngine::new_for_test(96000.0);
    e.trigger("ks:guitar:20", 0.0);
    match &e.pending[0].voice.voice {
        Voice::KarplusStrong { buf, buf_len, .. } => {
            assert_eq!(*buf_len, 4800);
            assert_eq!(buf.len(), 4800);
        }
        _ => panic!("ks voice expected"),
    }
}

#[test]
fn render_is_allocation_free() {
    let kinds = kinds();
    let slots: Vec<Vec<f32>> = (0..kinds.len()).map(event_slots).collect();
    let mut a = AudioEngine::new_for_test(SR);
    let mut b = AudioEngine::new_for_test(SR);
    let mut total = 0u64;
    let mut max_voices = 0usize;
    for blk in 0..2050usize {
        let t = (blk * FRAMES) as f64 / SR as f64;
        if blk % 8 == 0 {
            for e in [&mut a, &mut b] {
                for (i, k) in kinds.iter().enumerate() {
                    // half now, half later in the block or in a later block
                    let dt = (i % 4) as f64 * 0.002;
                    e.trigger_v3(k, t + dt, &slots[i]);
                }
                if blk == 400 {
                    for _ in 0..300 {
                        e.trigger("ks:harp:110", t);
                    }
                }
            }
        }
        let (_, na) = count_allocs(|| {
            a.process_block_into(FRAMES as u32, t);
        });
        let (_, nb) = count_allocs(|| {
            b.process_block_buses_raw(FRAMES as u32, t);
        });
        max_voices = max_voices.max(a.voices.len());
        if blk >= 50 {
            total += na + nb;
        }
    }
    assert_eq!(max_voices, MAX_VOICES, "the voice cap was reached");
    assert!(a.voice_cap_drops() > 0);
    assert_eq!(total, 0, "allocations in the render path");
}

/// Size of the queued voices (reported, not asserted: it sets the memory of
/// the PENDING_RESERVE queue reserved at construction).
#[test]
fn scheduled_size_report() {
    let s = std::mem::size_of::<Scheduled>();
    eprintln!(
        "size_of Scheduled = {s} B, reserved queue = {} KiB",
        s * PENDING_RESERVE / 1024
    );
    assert!(s > 0);
}
