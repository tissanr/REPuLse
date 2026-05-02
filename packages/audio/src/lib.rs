use js_sys::Float32Array;
use wasm_bindgen::prelude::*;

#[wasm_bindgen]
extern "C" {
    #[wasm_bindgen(js_namespace = console)]
    fn log(s: &str);
    #[wasm_bindgen(js_namespace = console)]
    fn warn(s: &str);
}

// ── Biquad filter (Direct Form I) ──────────────────────────────────────────

struct Biquad {
    b0: f32,
    b1: f32,
    b2: f32,
    a1: f32,
    a2: f32,
    x1: f32,
    x2: f32,
    y1: f32,
    y2: f32,
}

impl Biquad {
    fn bandpass(freq: f32, q: f32, sr: f32) -> Self {
        let w0 = 2.0 * std::f32::consts::PI * freq / sr;
        let alpha = w0.sin() / (2.0 * q);
        let cos_w0 = w0.cos();
        let a0 = 1.0 + alpha;
        Biquad {
            b0: alpha / a0,
            b1: 0.0,
            b2: -alpha / a0,
            a1: -2.0 * cos_w0 / a0,
            a2: (1.0 - alpha) / a0,
            x1: 0.0,
            x2: 0.0,
            y1: 0.0,
            y2: 0.0,
        }
    }

    fn highpass(freq: f32, q: f32, sr: f32) -> Self {
        let w0 = 2.0 * std::f32::consts::PI * freq / sr;
        let alpha = w0.sin() / (2.0 * q);
        let cos_w0 = w0.cos();
        let a0 = 1.0 + alpha;
        let b0 = (1.0 + cos_w0) / 2.0;
        Biquad {
            b0: b0 / a0,
            b1: -(1.0 + cos_w0) / a0,
            b2: b0 / a0,
            a1: -2.0 * cos_w0 / a0,
            a2: (1.0 - alpha) / a0,
            x1: 0.0,
            x2: 0.0,
            y1: 0.0,
            y2: 0.0,
        }
    }

    fn tick(&mut self, x: f32) -> f32 {
        let y = self.b0 * x + self.b1 * self.x1 + self.b2 * self.x2
            - self.a1 * self.y1
            - self.a2 * self.y2;
        self.x2 = self.x1;
        self.x1 = x;
        self.y2 = self.y1;
        self.y1 = y;
        y
    }
}

// ── LCG noise ──────────────────────────────────────────────────────────────

fn lcg_next(state: &mut u32) -> f32 {
    *state = state.wrapping_mul(1_664_525).wrapping_add(1_013_904_223);
    (*state as f32 / u32::MAX as f32).mul_add(2.0, -1.0)
}

// ── Decay rate: reach 1e-5 from 1.0 over `dur_secs` ──────────────────────

fn decay_rate(dur_secs: f32, sr: f32) -> f32 {
    let n = (dur_secs * sr).max(1.0);
    (1e-5_f32).powf(1.0 / n)
}

// ── Active voices ──────────────────────────────────────────────────────────

enum Voice {
    Kick {
        phase: f64,
        freq: f64,
        freq_decay: f64,
        freq_floor: f64,
        gain: f32,
        gain_decay: f32,
    },
    Snare {
        noise_state: u32,
        bpf: Biquad,
        gain: f32,
        gain_decay: f32,
        // Sine crack component
        phase: f64,
        tone_gain: f32,
        tone_gain_decay: f32,
    },
    Hihat {
        noise_state: u32,
        hpf: Biquad,
        gain: f32,
        gain_decay: f32,
    },
    Tone {
        phase: f64,
        freq: f64,
        amp: f32,
        gain: f32,
        gain_decay: f32,
        attack_inc: f32,
        in_attack: bool,
    },
    Saw {
        phase: f64,
        freq: f64,
        amp: f32,
        gain: f32,
        gain_decay: f32,
        attack_inc: f32,
        in_attack: bool,
    },
    Square {
        phase: f64,
        freq: f64,
        amp: f32,
        gain: f32,
        gain_decay: f32,
        attack_inc: f32,
        in_attack: bool,
        pulse_width: f32, // 0.0–1.0, default 0.5
    },
    Noise {
        state: u32,
        gain: f32,
        gain_decay: f32,
    },
    FM {
        carrier_phase: f64,
        mod_phase: f64,
        carrier_freq: f64,
        mod_freq: f64,
        index: f32,
        amp: f32,
        gain: f32,
        gain_decay: f32,
        attack_inc: f32,
        in_attack: bool,
    },
}

impl Voice {
    fn tick(&mut self, sr: f32) -> f32 {
        use std::f64::consts::TAU;
        match self {
            Voice::Kick {
                phase,
                freq,
                freq_decay,
                freq_floor,
                gain,
                gain_decay,
            } => {
                *freq = (*freq * *freq_decay).max(*freq_floor);
                let s = (*phase * TAU).sin() as f32;
                *phase += *freq / sr as f64;
                let out = s * *gain;
                *gain *= *gain_decay;
                out
            }
            Voice::Snare {
                noise_state,
                bpf,
                gain,
                gain_decay,
                phase,
                tone_gain,
                tone_gain_decay,
            } => {
                let body = bpf.tick(lcg_next(noise_state)) * *gain;
                *gain *= *gain_decay;
                let crack = (*phase * TAU).sin() as f32 * *tone_gain * 0.35;
                *phase += 180.0 / sr as f64;
                *tone_gain *= *tone_gain_decay;
                body + crack
            }
            Voice::Hihat {
                noise_state,
                hpf,
                gain,
                gain_decay,
            } => {
                let out = hpf.tick(lcg_next(noise_state)) * *gain;
                *gain *= *gain_decay;
                out
            }
            Voice::Tone {
                phase,
                freq,
                amp,
                gain,
                gain_decay,
                attack_inc,
                in_attack,
            } => {
                if *in_attack {
                    *gain += *attack_inc;
                    if *gain >= *amp {
                        *gain = *amp;
                        *in_attack = false;
                    }
                } else {
                    *gain *= *gain_decay;
                }
                let s = (*phase * TAU).sin() as f32;
                *phase += *freq / sr as f64;
                s * *gain
            }
            Voice::Saw {
                phase,
                freq,
                amp,
                gain,
                gain_decay,
                attack_inc,
                in_attack,
            } => {
                if *in_attack {
                    *gain += *attack_inc;
                    if *gain >= *amp {
                        *gain = *amp;
                        *in_attack = false;
                    }
                } else {
                    *gain *= *gain_decay;
                }
                // Sawtooth: ramp from -1 to +1 over one period
                let s = (2.0 * (*phase % 1.0) - 1.0) as f32;
                *phase += *freq / sr as f64;
                s * *gain
            }
            Voice::Square {
                phase,
                freq,
                amp,
                gain,
                gain_decay,
                attack_inc,
                in_attack,
                pulse_width,
            } => {
                if *in_attack {
                    *gain += *attack_inc;
                    if *gain >= *amp {
                        *gain = *amp;
                        *in_attack = false;
                    }
                } else {
                    *gain *= *gain_decay;
                }
                let p = *phase % 1.0;
                let s: f32 = if p < *pulse_width as f64 { 1.0 } else { -1.0 };
                *phase += *freq / sr as f64;
                s * *gain
            }
            Voice::Noise {
                state,
                gain,
                gain_decay,
            } => {
                let s = lcg_next(state);
                let out = s * *gain;
                *gain *= *gain_decay;
                out
            }
            Voice::FM {
                carrier_phase,
                mod_phase,
                carrier_freq,
                mod_freq,
                index,
                amp,
                gain,
                gain_decay,
                attack_inc,
                in_attack,
            } => {
                if *in_attack {
                    *gain += *attack_inc;
                    if *gain >= *amp {
                        *gain = *amp;
                        *in_attack = false;
                    }
                } else {
                    *gain *= *gain_decay;
                }
                let mod_sig = (*mod_phase * TAU).sin() as f32;
                let carrier_sig = (*carrier_phase * TAU + (*index * mod_sig) as f64).sin() as f32;
                *carrier_phase += *carrier_freq / sr as f64;
                *mod_phase += *mod_freq / sr as f64;
                carrier_sig * *gain
            }
        }
    }

    fn is_silent(&self) -> bool {
        match self {
            Voice::Tone {
                gain, in_attack, ..
            }
            | Voice::Saw {
                gain, in_attack, ..
            }
            | Voice::Square {
                gain, in_attack, ..
            }
            | Voice::FM {
                gain, in_attack, ..
            } => !*in_attack && *gain < 1e-5,
            Voice::Kick { gain, .. }
            | Voice::Snare { gain, .. }
            | Voice::Hihat { gain, .. }
            | Voice::Noise { gain, .. } => *gain < 1e-5,
        }
    }
}

// ── ActiveVoice — wraps a Voice with its stereo pan position ──────────────

struct ActiveVoice {
    voice: Voice,
    pan: f32, // -1.0 (left) … 0.0 (centre) … 1.0 (right)
}

// ── Pending event ──────────────────────────────────────────────────────────

struct Pending {
    time: f64,
    value: String,
    amp: f32,    // 0.0–1.0, default 1.0
    attack: f32, // seconds, default 0.001
    decay: f32,  // seconds, default 1.5 for tones
    pan: f32,    // -1.0–1.0, default 0.0
}

// ── Parameter transitions ──────────────────────────────────────────────────

#[derive(Clone, Copy, Debug, PartialEq)]
pub enum CurveType {
    Linear,
    Exp,
    Sine,
}

impl CurveType {
    fn from_str(s: &str) -> Self {
        match s {
            "exp" => CurveType::Exp,
            "sine" => CurveType::Sine,
            _ => CurveType::Linear,
        }
    }
}

/// One-shot parameter transition. Zero heap allocation — all state is inline.
#[derive(Clone, Copy, Debug)]
pub struct Transition {
    start_value: f32,
    end_value: f32,
    duration_samples: u64,
    elapsed_samples: u64,
    curve: CurveType,
}

impl Transition {
    fn new(start: f32, end: f32, duration_samples: u64, curve: CurveType) -> Self {
        Transition {
            start_value: start,
            end_value: end,
            duration_samples,
            elapsed_samples: 0,
            curve,
        }
    }

    /// Advance one sample, return interpolated value. Clamps at end — never resets.
    fn tick(&mut self) -> f32 {
        self.elapsed_samples = self.elapsed_samples.saturating_add(1);
        let t = if self.duration_samples == 0 {
            1.0_f32
        } else {
            (self.elapsed_samples as f32 / self.duration_samples as f32).min(1.0)
        };
        self.interpolate(t)
    }

    fn interpolate(&self, t: f32) -> f32 {
        let k = match self.curve {
            CurveType::Linear => t,
            CurveType::Exp => t * t,
            CurveType::Sine => 0.5 * (1.0 - f32::cos(std::f32::consts::PI * t)),
        };
        self.start_value + (self.end_value - self.start_value) * k
    }
}

// ── AudioEngine ────────────────────────────────────────────────────────────

#[wasm_bindgen]
pub struct AudioEngine {
    sample_rate: f32,
    voices: Vec<ActiveVoice>,
    pending: Vec<Pending>,
    noise_seed: u32,
    amp_transition: Option<Transition>,
    pan_transition: Option<Transition>,
}

#[wasm_bindgen]
impl AudioEngine {
    #[wasm_bindgen(constructor)]
    pub fn new(sample_rate: f32) -> AudioEngine {
        log(&format!(
            "[REPuLse WASM] PCM engine ready (sr={})",
            sample_rate
        ));
        AudioEngine {
            sample_rate,
            voices: Vec::new(),
            pending: Vec::new(),
            noise_seed: 0xDEAD_BEEF,
            amp_transition: None,
            pan_transition: None,
        }
    }

    /// Start a parameter transition. Replaces any existing transition for that param.
    /// param: "amp" or "pan"
    /// duration_samples: pre-computed on the JS side from bars * BPM * sample_rate
    pub fn start_transition(
        &mut self,
        param: &str,
        start: f32,
        end: f32,
        duration_samples: u64,
        curve: &str,
    ) {
        let tr = Transition::new(start, end, duration_samples, CurveType::from_str(curve));
        match param {
            "amp" => self.amp_transition = Some(tr),
            "pan" => self.pan_transition = Some(tr),
            _ => warn(&format!(
                "[REPuLse WASM] unknown transition param: {}",
                param
            )),
        }
    }

    /// Clear all active transitions (called by stop_all).
    pub fn clear_transitions(&mut self) {
        self.amp_transition = None;
        self.pan_transition = None;
    }

    /// Schedule a sound event at the given AudioContext time (uses default parameters).
    pub fn trigger(&mut self, value: &str, time: f64) {
        self.pending.push(Pending {
            time,
            value: value.to_string(),
            amp: 1.0,
            attack: 0.001,
            decay: 1.5,
            pan: 0.0,
        });
    }

    /// Schedule a sound event with explicit synthesis parameters.
    pub fn trigger_v2(
        &mut self,
        value: &str,
        time: f64,
        amp: f32,
        attack: f32,
        decay: f32,
        pan: f32,
    ) {
        self.pending.push(Pending {
            time,
            value: value.to_string(),
            amp,
            attack,
            decay,
            pan,
        });
    }

    /// Generate one audio quantum. Returns an interleaved stereo Float32Array of
    /// `n_samples * 2` values: [L0, R0, L1, R1, …].
    /// `current_time` = AudioWorkletGlobalScope.currentTime at the block start.
    pub fn process_block(&mut self, n_samples: u32, current_time: f64) -> Float32Array {
        let buf = self.process_block_raw(n_samples, current_time);
        let arr = Float32Array::new_with_length(n_samples * 2);
        arr.copy_from(&buf);
        arr
    }

    pub fn stop_all(&mut self) {
        self.voices.clear();
        self.pending.clear();
        self.clear_transitions();
    }
}

impl AudioEngine {
    #[cfg(test)]
    pub fn new_for_test(sample_rate: f32) -> AudioEngine {
        AudioEngine {
            sample_rate,
            voices: Vec::new(),
            pending: Vec::new(),
            noise_seed: 0xDEAD_BEEF,
            amp_transition: None,
            pan_transition: None,
        }
    }

    #[cfg(test)]
    pub fn trigger_raw(&mut self, value: &str, time: f64) {
        self.pending.push(Pending {
            time,
            value: value.to_string(),
            amp: 1.0,
            attack: 0.001,
            decay: 1.5,
            pan: 0.0,
        });
    }

    #[cfg(test)]
    pub fn trigger_raw_v2(
        &mut self,
        value: &str,
        time: f64,
        amp: f32,
        attack: f32,
        decay: f32,
        pan: f32,
    ) {
        self.pending.push(Pending {
            time,
            value: value.to_string(),
            amp,
            attack,
            decay,
            pan,
        });
    }

    /// Pure-Rust stereo render — no JS types. Returns interleaved [L0, R0, L1, …].
    pub fn process_block_raw(&mut self, n_samples: u32, current_time: f64) -> Vec<f32> {
        let sr = self.sample_rate;
        let n = n_samples as usize;
        let block_end = current_time + n as f64 / sr as f64;

        let pending = std::mem::take(&mut self.pending);
        let mut deferred = Vec::new();
        for p in pending {
            if p.time < block_end {
                self.activate(p);
            } else {
                deferred.push(p);
            }
        }
        self.pending = deferred;

        let mut buf = vec![0.0f32; n * 2];
        for i in 0..n {
            let mut l = 0.0f32;
            let mut r = 0.0f32;
            for av in self.voices.iter_mut() {
                let s = av.voice.tick(sr);
                let angle = (av.pan + 1.0) / 2.0 * std::f32::consts::FRAC_PI_2;
                l += s * angle.cos();
                r += s * angle.sin();
            }

            let amp_scale = if let Some(ref mut tr) = self.amp_transition {
                tr.tick()
            } else {
                1.0
            };

            if let Some(ref mut tr) = self.pan_transition {
                let pan_val = tr.tick().clamp(-1.0, 1.0);
                let bal_angle = (pan_val + 1.0) / 2.0 * std::f32::consts::FRAC_PI_2;
                buf[i * 2] = ((l + r) * bal_angle.cos() * amp_scale).clamp(-1.0, 1.0);
                buf[i * 2 + 1] = ((l + r) * bal_angle.sin() * amp_scale).clamp(-1.0, 1.0);
            } else {
                buf[i * 2] = (l * amp_scale).clamp(-1.0, 1.0);
                buf[i * 2 + 1] = (r * amp_scale).clamp(-1.0, 1.0);
            }
        }
        self.voices.retain(|av| !av.voice.is_silent());
        buf
    }

    fn next_seed(&mut self) -> u32 {
        self.noise_seed = self
            .noise_seed
            .wrapping_mul(1_664_525)
            .wrapping_add(1_013_904_223);
        self.noise_seed
    }

    fn activate(&mut self, p: Pending) {
        let sr = self.sample_rate;
        let seed = self.next_seed();
        let amp = p.amp.clamp(0.0, 1.0);
        let value = p.value.trim_start_matches(':');

        let voice = if let Some(rest) = value.strip_prefix("saw:") {
            let freq = rest.parse::<f64>().unwrap_or(440.0);
            let peak = amp * 0.5;
            let attack_samples = (p.attack * sr).max(1.0);
            Voice::Saw {
                phase: 0.0,
                freq,
                amp: peak,
                gain: 0.0,
                gain_decay: decay_rate(p.decay, sr),
                attack_inc: peak / attack_samples,
                in_attack: true,
            }
        } else if let Some(rest) = value.strip_prefix("square:") {
            let parts: Vec<&str> = rest.splitn(2, ':').collect();
            let freq = parts[0].parse::<f64>().unwrap_or(440.0);
            let pw = if parts.len() > 1 {
                parts[1].parse::<f32>().unwrap_or(0.5)
            } else {
                0.5
            };
            let peak = amp * 0.5;
            let attack_samples = (p.attack * sr).max(1.0);
            Voice::Square {
                phase: 0.0,
                freq,
                amp: peak,
                gain: 0.0,
                gain_decay: decay_rate(p.decay, sr),
                attack_inc: peak / attack_samples,
                in_attack: true,
                pulse_width: pw.clamp(0.01, 0.99),
            }
        } else if value == "noise" {
            Voice::Noise {
                state: seed,
                gain: amp * 0.3,
                gain_decay: decay_rate(p.decay, sr),
            }
        } else if let Some(rest) = value.strip_prefix("fm:") {
            // Format: "fm:<carrier_hz>:<index>:<ratio>"
            let parts: Vec<&str> = rest.splitn(3, ':').collect();
            let carrier_freq = parts[0].parse::<f64>().unwrap_or(440.0);
            let index = if parts.len() > 1 {
                parts[1].parse::<f32>().unwrap_or(1.0)
            } else {
                1.0
            };
            let ratio = if parts.len() > 2 {
                parts[2].parse::<f64>().unwrap_or(2.0)
            } else {
                2.0
            };
            let peak = amp * 0.5;
            let attack_samples = (p.attack * sr).max(1.0);
            Voice::FM {
                carrier_phase: 0.0,
                mod_phase: 0.0,
                carrier_freq,
                mod_freq: carrier_freq * ratio,
                index,
                amp: peak,
                gain: 0.0,
                gain_decay: decay_rate(p.decay, sr),
                attack_inc: peak / attack_samples,
                in_attack: true,
            }
        } else {
            match value {
                "bd" => {
                    let sweep_samples = 0.06 * sr as f64;
                    Voice::Kick {
                        phase: 0.0,
                        freq: 150.0,
                        freq_floor: 40.0,
                        freq_decay: (40.0_f64 / 150.0).powf(1.0 / sweep_samples),
                        gain: amp,
                        gain_decay: decay_rate(0.4, sr),
                    }
                }
                "sd" => Voice::Snare {
                    noise_state: seed,
                    bpf: Biquad::bandpass(200.0, 0.7, sr),
                    gain: 0.9 * amp,
                    gain_decay: decay_rate(0.2, sr),
                    phase: 0.0,
                    tone_gain: amp,
                    tone_gain_decay: decay_rate(0.1, sr),
                },
                "hh" => Voice::Hihat {
                    noise_state: seed,
                    hpf: Biquad::highpass(8000.0, 0.7, sr),
                    gain: 0.5 * amp,
                    gain_decay: decay_rate(0.045, sr),
                },
                "oh" => Voice::Hihat {
                    noise_state: seed,
                    hpf: Biquad::highpass(8000.0, 0.7, sr),
                    gain: 0.4 * amp,
                    gain_decay: decay_rate(1.0, sr),
                },
                other => {
                    let freq = other.parse::<f64>().unwrap_or(440.0);
                    let peak = amp * 0.5;
                    let attack_samples = (p.attack * sr).max(1.0);
                    Voice::Tone {
                        phase: 0.0,
                        freq,
                        amp: peak,
                        gain: 0.0,
                        gain_decay: decay_rate(p.decay, sr),
                        attack_inc: peak / attack_samples,
                        in_attack: true,
                    }
                }
            }
        };
        self.voices.push(ActiveVoice {
            voice,
            pan: p.pan.clamp(-1.0, 1.0),
        });
    }
}

#[cfg(test)]
mod transition_tests {
    use super::{CurveType, Transition};

    #[test]
    fn linear_values_at_quartiles() {
        let mut tr = Transition::new(0.0, 1.0, 100, CurveType::Linear);
        for _ in 0..25 {
            tr.tick();
        }
        assert!((tr.interpolate(0.25) - 0.25).abs() < 1e-5);
        for _ in 0..25 {
            tr.tick();
        }
        assert!((tr.interpolate(0.5) - 0.5).abs() < 1e-5);
        for _ in 0..25 {
            tr.tick();
        }
        assert!((tr.interpolate(0.75) - 0.75).abs() < 1e-5);
    }

    #[test]
    fn exp_midpoint_below_linear() {
        let tr = Transition::new(0.0, 1.0, 100, CurveType::Exp);
        assert!(
            tr.interpolate(0.5) < 0.5,
            "exp at t=0.5 should be < 0.5, got {}",
            tr.interpolate(0.5)
        );
    }

    #[test]
    fn sine_midpoint_at_half() {
        let tr = Transition::new(0.0, 1.0, 100, CurveType::Sine);
        assert!(
            (tr.interpolate(0.5) - 0.5).abs() < 1e-5,
            "sine at t=0.5 should be ≈0.5, got {}",
            tr.interpolate(0.5)
        );
    }

    #[test]
    fn clamps_at_end_value_no_overshoot() {
        let mut tr = Transition::new(0.0, 1.0, 100, CurveType::Linear);
        for _ in 0..200 {
            tr.tick();
        }
        assert!((tr.tick() - 1.0).abs() < 1e-6, "should hold at end value");
    }

    #[test]
    fn start_value_at_t_zero() {
        let tr = Transition::new(0.3, 0.9, 100, CurveType::Linear);
        assert!((tr.interpolate(0.0) - 0.3).abs() < 1e-6);
    }

    #[test]
    fn end_value_at_t_one() {
        let tr = Transition::new(0.3, 0.9, 100, CurveType::Sine);
        assert!((tr.interpolate(1.0) - 0.9).abs() < 1e-6);
    }

    #[test]
    fn zero_duration_clamps_to_end() {
        let mut tr = Transition::new(0.0, 1.0, 0, CurveType::Linear);
        assert!((tr.tick() - 1.0).abs() < 1e-6);
    }

    #[test]
    #[cfg(target_arch = "wasm32")]
    fn amp_transition_applied_in_engine() {
        use super::AudioEngine;
        let mut eng = AudioEngine::new(44100.0);
        eng.start_transition("amp", 0.0, 1.0, 44100, "linear");
        assert!(eng.amp_transition.is_some());
        eng.clear_transitions();
        assert!(eng.amp_transition.is_none());
    }
}

#[cfg(test)]
mod engine_tests {
    use super::AudioEngine;

    fn rms(buf: &[f32]) -> f32 {
        let sum: f32 = buf.iter().map(|s| s * s).sum();
        (sum / buf.len() as f32).sqrt()
    }

    fn rms_stereo(buf: &[f32]) -> (f32, f32) {
        let n = buf.len() / 2;
        let mut l_sum = 0.0f32;
        let mut r_sum = 0.0f32;
        for i in 0..n {
            l_sum += buf[i * 2] * buf[i * 2];
            r_sum += buf[i * 2 + 1] * buf[i * 2 + 1];
        }
        ((l_sum / n as f32).sqrt(), (r_sum / n as f32).sqrt())
    }

    #[test]
    fn kick_produces_nonsilent_output() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        eng.trigger_raw("bd", 0.0);
        let buf = eng.process_block_raw(4410, 0.0);
        assert!(rms(&buf) > 0.001, "kick should produce audible output");
    }

    #[test]
    fn snare_produces_nonsilent_output() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        eng.trigger_raw("sd", 0.0);
        let buf = eng.process_block_raw(4410, 0.0);
        assert!(rms(&buf) > 0.001, "snare should produce audible output");
    }

    #[test]
    fn hihat_produces_nonsilent_output() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        eng.trigger_raw("hh", 0.0);
        let buf = eng.process_block_raw(4410, 0.0);
        assert!(rms(&buf) > 0.001, "hihat should produce audible output");
    }

    #[test]
    fn tone_440_produces_nonsilent_output() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        eng.trigger_raw("440", 0.0);
        let buf = eng.process_block_raw(4410, 0.0);
        assert!(rms(&buf) > 0.001, "tone 440 should produce audible output");
    }

    #[test]
    fn saw_produces_nonsilent_output() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        eng.trigger_raw("saw:440", 0.0);
        let buf = eng.process_block_raw(4410, 0.0);
        assert!(rms(&buf) > 0.001, "saw should produce audible output");
    }

    #[test]
    fn square_produces_nonsilent_output() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        eng.trigger_raw("square:440:0.5", 0.0);
        let buf = eng.process_block_raw(4410, 0.0);
        assert!(rms(&buf) > 0.001, "square should produce audible output");
    }

    #[test]
    fn noise_produces_nonsilent_output() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        eng.trigger_raw("noise", 0.0);
        let buf = eng.process_block_raw(4410, 0.0);
        assert!(rms(&buf) > 0.001, "noise should produce audible output");
    }

    #[test]
    fn fm_produces_nonsilent_output() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        eng.trigger_raw("fm:440:2.0:3.0", 0.0);
        let buf = eng.process_block_raw(4410, 0.0);
        assert!(rms(&buf) > 0.001, "fm should produce audible output");
    }

    #[test]
    fn voice_decays_to_silence() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        eng.trigger_raw_v2("440", 0.0, 1.0, 0.001, 0.1, 0.0);
        // Render 0.5s — well past the 0.1s decay
        let buf = eng.process_block_raw(22050, 0.0);
        // Last 1000 samples should be near-silent
        let tail: Vec<f32> = buf[buf.len() - 2000..].to_vec();
        assert!(rms(&tail) < 0.001, "voice should decay to silence");
        assert!(eng.voices.is_empty(), "silent voices should be pruned");
    }

    #[test]
    fn pan_right_produces_right_heavy_stereo() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        eng.trigger_raw_v2("440", 0.0, 1.0, 0.001, 1.5, 1.0);
        let buf = eng.process_block_raw(4410, 0.0);
        let (l, r) = rms_stereo(&buf);
        assert!(
            r > l * 5.0,
            "pan=1.0 should be right-heavy, got L={:.4} R={:.4}",
            l,
            r
        );
    }

    #[test]
    fn pan_left_produces_left_heavy_stereo() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        eng.trigger_raw_v2("440", 0.0, 1.0, 0.001, 1.5, -1.0);
        let buf = eng.process_block_raw(4410, 0.0);
        let (l, r) = rms_stereo(&buf);
        assert!(
            l > r * 5.0,
            "pan=-1.0 should be left-heavy, got L={:.4} R={:.4}",
            l,
            r
        );
    }

    #[test]
    fn center_pan_produces_balanced_stereo() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        eng.trigger_raw_v2("440", 0.0, 1.0, 0.001, 1.5, 0.0);
        let buf = eng.process_block_raw(4410, 0.0);
        let (l, r) = rms_stereo(&buf);
        let ratio = if l > r { l / r } else { r / l };
        assert!(
            ratio < 1.1,
            "pan=0 should be balanced, got L={:.4} R={:.4}",
            l,
            r
        );
    }

    #[test]
    fn stop_all_clears_voices() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        eng.trigger_raw("bd", 0.0);
        eng.trigger_raw("sd", 0.0);
        eng.process_block_raw(128, 0.0);
        assert!(!eng.voices.is_empty());
        eng.stop_all();
        assert!(eng.voices.is_empty());
        assert!(eng.pending.is_empty());
    }

    #[test]
    fn amp_half_produces_lower_rms() {
        let mut eng1 = AudioEngine::new_for_test(44100.0);
        eng1.trigger_raw_v2("440", 0.0, 1.0, 0.001, 1.5, 0.0);
        let buf1 = eng1.process_block_raw(4410, 0.0);
        let rms1 = rms(&buf1);

        let mut eng2 = AudioEngine::new_for_test(44100.0);
        eng2.trigger_raw_v2("440", 0.0, 0.5, 0.001, 1.5, 0.0);
        let buf2 = eng2.process_block_raw(4410, 0.0);
        let rms2 = rms(&buf2);

        assert!(
            rms2 < rms1 * 0.7,
            "amp=0.5 should produce lower RMS than amp=1.0, got {:.4} vs {:.4}",
            rms2,
            rms1
        );
    }

    #[test]
    fn stereo_output_is_interleaved() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        eng.trigger_raw("bd", 0.0);
        let buf = eng.process_block_raw(128, 0.0);
        assert_eq!(
            buf.len(),
            256,
            "128 samples should produce 256 interleaved values"
        );
    }

    #[test]
    fn pending_event_activates_in_block() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        // Schedule at time 0.001 — should activate in a block starting at 0.0
        eng.trigger_raw("bd", 0.001);
        assert!(eng.voices.is_empty());
        assert_eq!(eng.pending.len(), 1);
        let _buf = eng.process_block_raw(128, 0.0);
        assert!(!eng.voices.is_empty(), "pending event should activate");
        assert!(eng.pending.is_empty());
    }

    #[test]
    fn pending_event_deferred_past_block() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        // Schedule at time 1.0 — should NOT activate in a short block at 0.0
        eng.trigger_raw("bd", 1.0);
        let _buf = eng.process_block_raw(128, 0.0);
        assert!(
            eng.voices.is_empty(),
            "event at t=1.0 should not activate in block at t=0.0"
        );
        assert_eq!(eng.pending.len(), 1);
    }
}
