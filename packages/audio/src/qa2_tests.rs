//! Phase QA2 — hardened, allocation-free engine. Test-only module.
//!
//! `golden_render_matches` compares the engine against `golden/qa2.bin`, a
//! render recorded on the engine *before* any QA2 change. Never re-record it:
//! a golden failure is a refactor bug, not a reason to update the snapshot.

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
