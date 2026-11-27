//! Phase VIZ1 — per-track meter slots. Test-only module.

use crate::alloc_count::count_allocs;
use crate::*;

const SR: f32 = 44100.0;
const FRAMES: usize = 128;

fn render_blocks(e: &mut AudioEngine, blocks: usize, start: usize) {
    for b in start..start + blocks {
        let t = (b * FRAMES) as f64 / SR as f64;
        e.process_block_buses_raw(FRAMES as u32, t);
    }
}

fn slot_rms(snap: &[f32], s: usize) -> f32 {
    snap[N_METERS * METER_LEN + N_METERS + s]
}

fn slot_peak(snap: &[f32], s: usize) -> f32 {
    snap[N_METERS * METER_LEN + s]
}

#[test]
fn voice_meters_only_its_slot() {
    let mut e = AudioEngine::new_for_test(SR);
    e.meter_next(3);
    e.trigger_v2("saw:220", 0.0, 0.8, 0.001, 1.0, 0.0);
    render_blocks(&mut e, 69, 0); // 0.2 s
    let snap = e.fill_meter_snapshot().to_vec();
    assert_eq!(snap.len(), METER_SNAPSHOT_LEN);
    assert!(
        slot_rms(&snap, 3) > 0.001,
        "slot 3 rms {}",
        slot_rms(&snap, 3)
    );
    assert!(slot_peak(&snap, 3) > 0.001);
    for s in (0..N_METERS).filter(|&s| s != 3) {
        assert_eq!(slot_rms(&snap, s), 0.0, "slot {s}");
        assert_eq!(slot_peak(&snap, s), 0.0, "slot {s}");
        assert!(snap[s * METER_LEN..(s + 1) * METER_LEN]
            .iter()
            .all(|&x| x == 0.0));
    }
}

#[test]
fn meter_slot_applies_to_one_trigger_of_any_kind() {
    // meter_next is consumed by the next trigger: the second voice is unmetered.
    let mut e = AudioEngine::new_for_test(SR);
    e.meter_next(5);
    e.trigger("440", 0.0);
    e.trigger("880", 0.0);
    e.meter_next(6);
    e.trigger_v3("sine:330", 0.0, &[0.5]);
    render_blocks(&mut e, 40, 0);
    let snap = e.fill_meter_snapshot().to_vec();
    assert!(slot_rms(&snap, 5) > 0.001);
    assert!(slot_rms(&snap, 6) > 0.001);
    // slot 5 holds the 440 Hz voice only: it is quieter than the full mix
    let dry = e.process_block_raw(FRAMES as u32, (40 * FRAMES) as f64 / SR as f64);
    assert!(dry.iter().any(|&x| x != 0.0));
}

#[test]
fn voice_without_slot_meters_nowhere() {
    let mut e = AudioEngine::new_for_test(SR);
    e.trigger_v2("saw:220", 0.0, 0.8, 0.001, 1.0, 0.0);
    e.meter_next(99); // out of range = not metered
    e.trigger("440", 0.0);
    render_blocks(&mut e, 69, 0);
    let snap = e.fill_meter_snapshot().to_vec();
    for s in 0..N_METERS {
        assert_eq!(slot_rms(&snap, s), 0.0, "slot {s}");
    }
}

#[test]
fn ring_is_time_ordered() {
    // A voice starting late in the window: the newest samples (end of the
    // ring) carry the sound, the oldest (start) are still silent.
    let mut e = AudioEngine::new_for_test(SR);
    render_blocks(&mut e, 30, 0);
    let t = (30 * FRAMES) as f64 / SR as f64;
    e.meter_next(0);
    e.trigger_v2("saw:220", t, 0.8, 0.001, 1.0, 0.0);
    render_blocks(&mut e, 4, 30); // 512 samples of sound
    let snap = e.fill_meter_snapshot().to_vec();
    let ring = &snap[..METER_LEN];
    assert!(ring[..METER_LEN - 600].iter().all(|&x| x == 0.0));
    assert!(ring[METER_LEN - 400..].iter().any(|&x| x != 0.0));
}

#[test]
fn stop_all_flattens_meters() {
    let mut e = AudioEngine::new_for_test(SR);
    e.meter_next(2);
    e.trigger_v2("saw:220", 0.0, 0.8, 0.001, 1.0, 0.0);
    render_blocks(&mut e, 20, 0);
    e.stop_all();
    let snap = e.fill_meter_snapshot().to_vec();
    assert_eq!(slot_rms(&snap, 2), 0.0);
    assert_eq!(slot_peak(&snap, 2), 0.0);
}

#[test]
fn metering_is_allocation_free_and_buffers_do_not_grow() {
    let mut e = AudioEngine::new_for_test(SR);
    let voices_cap = e.voices.capacity();
    let bus_cap = e.bus.capacity();
    let rings_cap = e.meter_rings.capacity();
    let snap_cap = e.meter_snap.capacity();
    let mut allocs = 0u64;
    for b in 0..1000usize {
        let t = (b * FRAMES) as f64 / SR as f64;
        if b % 10 == 0 {
            e.meter_next((b / 10 % N_METERS) as i32);
            e.trigger_v2("saw:220", t, 0.5, 0.001, 0.3, 0.0);
        }
        let (_, n) = count_allocs(|| {
            e.process_block_buses_raw(FRAMES as u32, t);
        });
        let (_, m) = count_allocs(|| {
            e.fill_meter_snapshot();
        });
        if b >= 10 {
            allocs += n + m;
        }
    }
    assert_eq!(allocs, 0, "allocations while metering");
    assert_eq!(e.voices.capacity(), voices_cap);
    assert_eq!(e.bus.capacity(), bus_cap);
    assert_eq!(e.meter_rings.capacity(), rings_cap);
    assert_eq!(e.meter_snap.capacity(), snap_cap);
}
