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

#[derive(Clone, Copy)]
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

    /// Audio EQ Cookbook low-pass (Phase TL7a per-event `lpf`).
    fn lowpass(freq: f32, q: f32, sr: f32) -> Self {
        let w0 = 2.0 * std::f32::consts::PI * freq / sr;
        let alpha = w0.sin() / (2.0 * q);
        let cos_w0 = w0.cos();
        let a0 = 1.0 + alpha;
        let b1 = 1.0 - cos_w0;
        Biquad {
            b0: b1 / 2.0 / a0,
            b1: b1 / a0,
            b2: b1 / 2.0 / a0,
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

    fn peaking_eq(freq: f32, gain_db: f32, q: f32, sr: f32) -> Self {
        let a = 10.0_f32.powf(gain_db / 40.0);
        let w0 = 2.0 * std::f32::consts::PI * freq / sr;
        let alpha = w0.sin() / (2.0 * q);
        let cos_w0 = w0.cos();
        let a0 = 1.0 + alpha / a;
        Biquad {
            b0: (1.0 + alpha * a) / a0,
            b1: -2.0 * cos_w0 / a0,
            b2: (1.0 - alpha * a) / a0,
            a1: -2.0 * cos_w0 / a0,
            a2: (1.0 - alpha / a) / a0,
            x1: 0.0,
            x2: 0.0,
            y1: 0.0,
            y2: 0.0,
        }
    }

    fn highshelf(freq: f32, gain_db: f32, sr: f32) -> Self {
        let a = 10.0_f32.powf(gain_db / 40.0);
        let w0 = 2.0 * std::f32::consts::PI * freq / sr;
        let cos_w0 = w0.cos();
        let alpha = w0.sin() / 2.0 * ((a + 1.0 / a) * (1.0 / 0.9 - 1.0) + 2.0).sqrt();
        let a0 = (a + 1.0) - (a - 1.0) * cos_w0 + 2.0 * a.sqrt() * alpha;
        Biquad {
            b0: a * ((a + 1.0) + (a - 1.0) * cos_w0 + 2.0 * a.sqrt() * alpha) / a0,
            b1: -2.0 * a * ((a - 1.0) + (a + 1.0) * cos_w0) / a0,
            b2: a * ((a + 1.0) + (a - 1.0) * cos_w0 - 2.0 * a.sqrt() * alpha) / a0,
            a1: 2.0 * ((a - 1.0) - (a + 1.0) * cos_w0) / a0,
            a2: ((a + 1.0) - (a - 1.0) * cos_w0 - 2.0 * a.sqrt() * alpha) / a0,
            x1: 0.0,
            x2: 0.0,
            y1: 0.0,
            y2: 0.0,
        }
    }

    /// Take the coefficients of `from`, keep this filter's state (TL7c
    /// control-rate cutoff modulation).
    fn set_coeffs(&mut self, from: &Biquad) {
        self.b0 = from.b0;
        self.b1 = from.b1;
        self.b2 = from.b2;
        self.a1 = from.a1;
        self.a2 = from.a2;
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

fn fm_preset(name: &str) -> (f32, f64, f32, f32, f32, f32) {
    // Returns (index, ratio, attack_time, decay_time, sustain, release_time)
    // For zero-sustain voices (bell, marimba, epiano), decay_time carries the full ring-out;
    // release_time is minimal because the envelope is already at zero after decay.
    match name {
        "sax" => (5.0, 1.0, 0.020, 0.10, 0.70, 0.15),
        "trumpet" => (5.5, 1.0, 0.010, 0.05, 0.80, 0.10),
        "trumpet-muted" => (4.0, 1.0, 0.015, 0.08, 0.70, 0.12),
        "trombone" => (3.5, 1.0, 0.120, 0.08, 0.75, 0.20),
        "synth-brass" => (7.0, 1.414, 0.010, 0.04, 0.60, 0.08),
        "harmon-out" => (3.5, 1.0, 0.015, 0.06, 0.75, 0.15),
        "harmon-in" => (5.0, 1.0, 0.010, 0.05, 0.70, 0.12),
        "epiano" => (1.5, 14.0, 0.005, 0.80, 0.0, 0.01),
        "bell" => (5.0, 1.414, 0.001, 1.20, 0.0, 0.01),
        "marimba" => (2.0, 3.5, 0.001, 0.35, 0.0, 0.01),
        "flute" => (1.8, 1.0, 0.060, 0.05, 0.85, 0.10),
        _ => (3.0, 1.0, 0.010, 0.10, 0.70, 0.15),
    }
}

fn fm_body_filters(name: &str, sr: f32) -> Vec<Biquad> {
    // Per-preset formant chain applied post-FM to shape bore/bell resonances.
    // Empty vec = no shaping (epiano, bell, marimba, flute, synth-brass are fine as-is).
    match name {
        "sax" => vec![
            Biquad::peaking_eq(900.0, 6.0, 2.0, sr), // reed + body formant
            Biquad::peaking_eq(2200.0, 4.0, 2.5, sr), // bore resonance
            Biquad::highshelf(5000.0, -5.0, sr),     // soften top
        ],
        "trumpet" => vec![
            Biquad::peaking_eq(1200.0, 5.0, 1.5, sr), // bell throat
            Biquad::peaking_eq(2400.0, 6.0, 2.0, sr), // bell mouth resonance
            Biquad::peaking_eq(3500.0, 3.0, 2.5, sr), // high partial emphasis
        ],
        "trumpet-muted" => vec![
            Biquad::highpass(250.0, 0.7, sr),         // mute blocks lows
            Biquad::peaking_eq(1700.0, 7.0, 2.5, sr), // nasal mid peak
            Biquad::highshelf(4000.0, -8.0, sr),      // mute cuts highs
        ],
        "trombone" => vec![
            Biquad::peaking_eq(350.0, 4.0, 2.0, sr), // fundamental warmth
            Biquad::peaking_eq(1100.0, 3.0, 1.5, sr), // mid body
            Biquad::highshelf(3500.0, -5.0, sr),     // dark rolloff
        ],
        "harmon-out" => vec![
            Biquad::highpass(350.0, 0.7, sr), // cut lows (stem-out is open)
            Biquad::peaking_eq(1100.0, 10.0, 3.0, sr), // the harmon nasal peak
            Biquad::highshelf(2800.0, -12.0, sr), // heavy hi-cut
        ],
        "harmon-in" => vec![
            Biquad::highpass(400.0, 0.7, sr), // more lows cut (stem closed)
            Biquad::peaking_eq(850.0, 9.0, 3.5, sr), // lower/tighter peak than stem-out
            Biquad::highshelf(2500.0, -12.0, sr), // even heavier hi-cut
        ],
        _ => vec![],
    }
}

fn fm_noise_amp(name: &str) -> f32 {
    // Breath/air noise amplitude relative to the FM signal (pre-envelope).
    // 0.0 = no noise. Applied through a 3 kHz highpass in the tick path.
    match name {
        "sax" => 0.025,     // subtle reed hiss
        "trumpet" => 0.040, // air through bell
        "trumpet-muted" => 0.030,
        "trombone" => 0.010,
        "harmon-out" => 0.030,
        "harmon-in" => 0.040,
        _ => 0.0,
    }
}

fn ks_preset(name: &str) -> (f32, f32, f32, f32, f32, f32) {
    // Returns (feedback, brightness, pick_pos, vib_depth, vib_rate, excitation)
    // excitation scales the initial noise fill — lower = softer attack transient
    // T60 formula (at A440, buf_len≈100): -3*100 / (44100 * ln(feedback))
    match name {
        "western" | "guitar" => (0.997, 0.60, 0.12, 0.0, 0.0, 1.0), // ~2.3s, steel pick
        "nylon" => (0.996, 0.50, 0.16, 0.0, 0.0, 0.60),             // ~2.0s, soft fingertip
        "harp" => (0.998, 0.55, 0.25, 0.0, 0.0, 1.0),               // ~3.4s T60, warm
        "koto" => (0.994, 0.62, 0.10, 0.015, 5.5, 1.0),             // ~1.5s, subtle vib
        "pizz" => (0.975, 0.40, 0.42, 0.0, 0.0, 0.18),              // ~0.27s, gentle finger pluck
        "lute" => (0.996, 0.58, 0.16, 0.0, 0.0, 1.0),               // ~1.7s, warm
        "mandolin" => (0.992, 0.68, 0.08, 0.025, 6.5, 1.0),         // ~0.85s, bright
        _ => (0.997, 0.60, 0.12, 0.0, 0.0, 1.0),
    }
}

fn ks_body_filters(name: &str, sr: f32) -> Vec<Biquad> {
    // Per-instrument body resonance EQ chain applied post-KS.
    // Models the acoustic cavity + top-plate resonances that make each
    // instrument recognisably itself throughout the sustain, not just at attack.
    // SYN3 (bowed strings) uses the same Biquad primitives for its body chain.
    match name {
        "western" | "guitar" => vec![
            Biquad::peaking_eq(90.0, 5.0, 2.5, sr), // Helmholtz air resonance (dreadnought)
            Biquad::peaking_eq(200.0, 2.0, 1.5, sr), // top-plate main mode
            Biquad::highshelf(4500.0, -4.0, sr),    // steel-string HF rolloff
        ],
        "nylon" => vec![
            Biquad::peaking_eq(110.0, 4.0, 2.0, sr), // cedar/spruce top, higher air resonance
            Biquad::peaking_eq(230.0, 2.0, 1.5, sr), // top-plate mode
            Biquad::highshelf(3500.0, -5.0, sr),     // nylon rolls off treble sharply
        ],
        "harp" => vec![
            Biquad::peaking_eq(110.0, 4.0, 1.5, sr), // large open soundboard
            Biquad::peaking_eq(800.0, 1.0, 1.0, sr), // mild presence
            Biquad::highshelf(5000.0, -3.0, sr),     // silky rolloff
        ],
        "koto" => vec![
            Biquad::peaking_eq(220.0, 6.0, 2.5, sr), // characteristic thud/buzz
            Biquad::highshelf(3000.0, -5.0, sr),     // dark silk-string rolloff
        ],
        "pizz" => vec![
            Biquad::peaking_eq(270.0, 4.0, 3.5, sr), // violin air resonance (narrow)
            Biquad::peaking_eq(520.0, 3.0, 3.0, sr), // main wood mode
            Biquad::highshelf(4000.0, 2.0, sr),      // violin brightness
        ],
        "lute" => vec![
            Biquad::peaking_eq(120.0, 4.0, 2.0, sr), // rounded bowl resonance
            Biquad::highshelf(2500.0, -5.0, sr),     // gut-string warmth, dark top
        ],
        "mandolin" => vec![
            Biquad::peaking_eq(300.0, 4.0, 2.0, sr), // small archtop body
            Biquad::peaking_eq(2000.0, 3.0, 1.5, sr), // steel-string presence
        ],
        _ => vec![
            Biquad::peaking_eq(90.0, 5.0, 2.5, sr),
            Biquad::peaking_eq(200.0, 2.0, 1.5, sr),
            Biquad::highshelf(4500.0, -4.0, sr),
        ],
    }
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
        peak_gain: f32,
        gain: f32,
        attack_time: f32,
        decay_time: f32,
        sustain: f32,
        release_time: f32,
        env_phase: f32,
        body_filters: Vec<Biquad>,
        noise_amp: f32,
        noise_state: u32,
        noise_hpf: Biquad,
    },
    KarplusStrong {
        buf: Vec<f32>,
        buf_len: usize,
        write_pos: usize,
        lp_prev: f32,
        feedback: f32,
        brightness: f32,
        gain: f32,
        vib_phase: f32,
        vib_depth: f32,
        vib_rate: f32,
        body_filters: Vec<Biquad>,
    },
}

impl Voice {
    /// One sample. `pitch` multiplies the oscillator frequency of the tonal
    /// voices (Tone, Saw, Square, FM) — TL7c pitch envelope and vibrato; every
    /// other voice ignores it. 1.0 = unmodulated (bit-identical to before).
    fn tick(&mut self, sr: f32, pitch: f64) -> f32 {
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
                *phase += *freq * pitch / sr as f64;
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
                *phase += *freq * pitch / sr as f64;
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
                *phase += *freq * pitch / sr as f64;
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
                peak_gain,
                gain,
                attack_time,
                decay_time,
                sustain,
                release_time,
                env_phase,
                body_filters,
                noise_amp,
                noise_state,
                noise_hpf,
            } => {
                let dt = 1.0 / sr;
                let ep = *env_phase;
                *gain = if ep < *attack_time {
                    if *attack_time < 1e-6 {
                        *peak_gain
                    } else {
                        *peak_gain * (ep / *attack_time)
                    }
                } else if ep < *attack_time + *decay_time {
                    let t = (ep - *attack_time) / (*decay_time + 1e-6);
                    *peak_gain * (1.0 - t * (1.0 - *sustain))
                } else {
                    let t = (ep - *attack_time - *decay_time) / (*release_time + 0.01);
                    (*peak_gain * *sustain * (1.0 - t)).max(0.0)
                };
                *env_phase += dt;
                let mod_sig = (*mod_phase * TAU).sin() as f32;
                let carrier_raw = (*carrier_phase * TAU + (*index * mod_sig) as f64).sin() as f32;
                *carrier_phase += *carrier_freq * pitch / sr as f64;
                *mod_phase += *mod_freq * pitch / sr as f64;
                let tonal = body_filters.iter_mut().fold(carrier_raw, |s, f| f.tick(s));
                let noise_out = noise_hpf.tick(lcg_next(noise_state)) * *noise_amp;
                (tonal + noise_out) * *gain
            }
            Voice::KarplusStrong {
                buf,
                buf_len,
                write_pos,
                lp_prev,
                feedback,
                brightness,
                gain,
                vib_phase,
                vib_depth,
                vib_rate,
                body_filters,
            } => {
                let vib_offset = (*vib_depth * (*vib_phase).sin()) as isize;
                let read_pos = (*write_pos + 1 + *buf_len) % *buf_len;
                let vib_pos =
                    ((read_pos as isize + vib_offset).rem_euclid(*buf_len as isize)) as usize;
                let x = buf[vib_pos];
                let y = *brightness * x + (1.0 - *brightness) * *lp_prev;
                let y_fb = y * *feedback;
                buf[*write_pos] = y_fb;
                *write_pos = (*write_pos + 1) % *buf_len;
                *lp_prev = y;
                *vib_phase += 2.0 * std::f32::consts::PI * *vib_rate / sr;
                let out = body_filters.iter_mut().fold(y_fb, |s, f| f.tick(s));
                *gain = out.abs().max(*gain * 0.9999);
                out
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
            } => !*in_attack && *gain < 1e-5,
            Voice::FM {
                gain,
                env_phase,
                attack_time,
                ..
            } => *env_phase > *attack_time && *gain < 1e-4,
            Voice::Kick { gain, .. }
            | Voice::Snare { gain, .. }
            | Voice::Hihat { gain, .. }
            | Voice::Noise { gain, .. } => *gain < 1e-5,
            Voice::KarplusStrong { gain, .. } => *gain < 1e-4,
        }
    }
}

// ── Per-event inserts (Phase TL7a) ─────────────────────────────────────────
//
// Clamps, defaults and formulas mirror app/src/repulse/inserts.cljs, which
// drives the Web Audio node chain of the JS voice path:
//   shape:  k = 2a / (1 − a),  y = (1 + k)·x / (1 + k·|x|)
//   crush:  levels = 2^(bits − 1),  y = round(x·levels) / levels  (bits ≥ 16 → off)
// Inline fields only: no heap allocation, built at trigger time in `activate`.

/// Insert params of one event (trigger_v3 slots 4–11). Filter freq 0.0 = off.
#[derive(Clone, Copy, Debug, PartialEq)]
struct InsertParams {
    lpf: f32,
    resonance: f32,
    hpf: f32,
    hpq: f32,
    bpf: f32,
    bpq: f32,
    shape: f32,
    crush: f32,
}

impl InsertParams {
    const OFF: InsertParams = InsertParams {
        lpf: 0.0,
        resonance: 0.707,
        hpf: 0.0,
        hpq: 0.707,
        bpf: 0.0,
        bpq: 1.0,
        shape: 0.0,
        crush: 16.0,
    };
}

// ── Phase TL7c: envelopes, vowel, coarse, postgain ────────────────────────
// Formulas mirror app/src/repulse/inserts.cljs (env-value, effective-depth,
// vowel-table, djf is folded into lpf/hpf there). Envelopes are linear in
// the log domain: cutoff in octaves, pitch in semitones.

/// Control-rate period (samples) of envelope, vibrato and cutoff updates.
const MOD_PERIOD: u32 = 16;

/// One envelope: depth (octaves or semitones) and A D S R (seconds, S 0–1).
#[derive(Clone, Copy, Debug, PartialEq)]
struct EnvSpec {
    depth: f32,
    attack: f32,
    decay: f32,
    sustain: f32,
    release: f32,
}

impl EnvSpec {
    /// Clamped envelope from five trigger_v3 slots, None when depth is 0.
    fn from_slots(sl: [f32; 5], lim: f32) -> Option<EnvSpec> {
        let depth = finite_or(sl[0], 0.0).clamp(-lim, lim);
        if depth == 0.0 {
            return None;
        }
        Some(EnvSpec {
            depth,
            attack: finite_or(sl[1], 0.001).clamp(0.0, 10.0),
            decay: finite_or(sl[2], 0.2).clamp(0.0, 10.0),
            sustain: finite_or(sl[3], 0.0).clamp(0.0, 1.0),
            release: finite_or(sl[4], 0.1).clamp(0.0, 10.0),
        })
    }

    /// The envelope without release at t ≥ 0.
    fn peak(&self, t: f32) -> f32 {
        if t < self.attack {
            t / self.attack
        } else if t < self.attack + self.decay {
            1.0 + (self.sustain - 1.0) * (t - self.attack) / self.decay
        } else {
            self.sustain
        }
    }

    /// e(t) for gate seconds (≤ 0 = infinite).
    fn value(&self, gate: f32, t: f32) -> f32 {
        let g = if gate > 0.0 { gate } else { f32::INFINITY };
        if t < 0.0 {
            0.0
        } else if t < g {
            self.peak(t)
        } else if t < g + self.release {
            self.peak(g) * (1.0 - (t - g) / self.release)
        } else {
            0.0
        }
    }
}

/// TL7c params of one event (trigger_v3 slots 15–40).
#[derive(Clone, Copy, Debug, PartialEq)]
struct Tl7cParams {
    gate: f32,
    lp_env: Option<EnvSpec>,
    hp_env: Option<EnvSpec>,
    bp_env: Option<EnvSpec>,
    penv: Option<EnvSpec>,
    vib: f32,
    vib_mod: f32,
    vowel: usize, // 0 off, 1–5 = a e i o u
    coarse: u32,  // 1 = off
    postgain: f32,
}

impl Tl7cParams {
    const OFF: Tl7cParams = Tl7cParams {
        gate: 0.0,
        lp_env: None,
        hp_env: None,
        bp_env: None,
        penv: None,
        vib: 0.0,
        vib_mod: 0.5,
        vowel: 0,
        coarse: 1,
        postgain: 1.0,
    };

    fn from_slots(slot: impl Fn(usize, f32) -> f32) -> Tl7cParams {
        let env = |i: usize, lim: f32| {
            EnvSpec::from_slots(
                [
                    slot(i, 0.0),
                    slot(i + 1, 0.001),
                    slot(i + 2, 0.2),
                    slot(i + 3, 0.0),
                    slot(i + 4, 0.1),
                ],
                lim,
            )
        };
        Tl7cParams {
            gate: finite_or(slot(15, 0.0), 0.0).max(0.0),
            lp_env: env(16, 10.0),
            hp_env: env(21, 10.0),
            bp_env: env(26, 10.0),
            penv: env(31, 48.0),
            vib: finite_or(slot(36, 0.0), 0.0).clamp(0.0, 40.0),
            vib_mod: finite_or(slot(37, 0.5), 0.5).clamp(0.0, 12.0),
            vowel: finite_or(slot(38, 0.0), 0.0).round().clamp(0.0, 5.0) as usize,
            coarse: finite_or(slot(39, 1.0), 1.0).round().clamp(1.0, 64.0) as u32,
            postgain: finite_or(slot(40, 1.0), 1.0).clamp(0.0, 4.0),
        }
    }
}

/// Formants per vowel a e i o u: (freqs Hz, gains dB, bandwidths Hz).
/// Mirrors `vowel-table` in inserts.cljs (Csound bass formants; `i` from the
/// tenor table).
const VOWELS: [([f32; 5], [f32; 5], [f32; 5]); 5] = [
    (
        [600.0, 1040.0, 2250.0, 2450.0, 2750.0],
        [0.0, -7.0, -9.0, -9.0, -20.0],
        [60.0, 70.0, 110.0, 120.0, 130.0],
    ),
    (
        [400.0, 1620.0, 2400.0, 2800.0, 3100.0],
        [0.0, -12.0, -9.0, -12.0, -18.0],
        [40.0, 80.0, 100.0, 120.0, 120.0],
    ),
    // i: Csound tenor formants (the bass table's F2 at −30 dB sounds like "u").
    (
        [290.0, 1870.0, 2800.0, 3250.0, 3540.0],
        [0.0, -15.0, -18.0, -20.0, -30.0],
        [40.0, 90.0, 100.0, 120.0, 120.0],
    ),
    (
        [400.0, 750.0, 2400.0, 2600.0, 2900.0],
        [0.0, -11.0, -21.0, -20.0, -40.0],
        [40.0, 80.0, 100.0, 120.0, 120.0],
    ),
    (
        [350.0, 600.0, 2400.0, 2675.0, 2950.0],
        [0.0, -20.0, -32.0, -28.0, -36.0],
        [40.0, 80.0, 100.0, 120.0, 120.0],
    ),
];

/// Make-up gain after the formant bank (both paths).
const VOWEL_MAKEUP: f32 = 2.0;

#[derive(Clone, Copy)]
struct VowelBank {
    bands: [Biquad; 5],
    gains: [f32; 5],
}

impl VowelBank {
    fn new(index: usize, sr: f32) -> Option<VowelBank> {
        if index == 0 || index > 5 {
            return None;
        }
        let (freqs, dbs, bws) = VOWELS[index - 1];
        let nyq = sr * 0.49;
        let band = |i: usize| {
            let f = freqs[i].min(nyq);
            Biquad::bandpass(f, f / bws[i], sr)
        };
        Some(VowelBank {
            bands: [band(0), band(1), band(2), band(3), band(4)],
            gains: dbs.map(|d| 10.0_f32.powf(d / 20.0)),
        })
    }

    #[inline]
    fn tick(&mut self, x: f32) -> f32 {
        let mut y = 0.0;
        for i in 0..5 {
            y += self.gains[i] * self.bands[i].tick(x);
        }
        y * VOWEL_MAKEUP
    }
}

#[derive(Clone, Copy, Debug, PartialEq)]
enum FilterKind {
    Low,
    High,
    Band,
}

impl FilterKind {
    fn make(self, freq: f32, q: f32, sr: f32) -> Biquad {
        match self {
            FilterKind::Low => Biquad::lowpass(freq, q, sr),
            FilterKind::High => Biquad::highpass(freq, q, sr),
            FilterKind::Band => Biquad::bandpass(freq, q, sr),
        }
    }
}

/// A filter envelope bound to its stage: base cutoff, Q and effective depth.
#[derive(Clone, Copy)]
struct FilterEnv {
    env: EnvSpec,
    kind: FilterKind,
    base: f32,
    q: f32,
    depth: f32, // effective depth in octaves (peak clamped to 20 Hz … min(20 kHz, 0.49·sr))
}

struct Inserts {
    lpf: Option<Biquad>,
    hpf: Option<Biquad>,
    bpf: Option<Biquad>,
    /// TL7c: envelopes of lpf, hpf, bpf (index 0–2).
    envs: [Option<FilterEnv>; 3],
    vowel: Option<VowelBank>,
    coarse_n: u32, // 1 = bypass
    coarse_count: u32,
    coarse_hold: f32,
    crush_levels: f32, // 0.0 = bypass
    shape_k: f32,      // 0.0 = bypass
    postgain: f32,     // 1.0 = bypass
}

fn finite_or(x: f32, d: f32) -> f32 {
    if x.is_finite() {
        x
    } else {
        d
    }
}

impl Inserts {
    fn new(p: &InsertParams, t: &Tl7cParams, sr: f32) -> Self {
        // Cutoffs are also kept below Nyquist so a low sample rate stays stable.
        let nyq = sr * 0.49;
        // (freq, q) of a stage that is on, else None.
        let stage = |freq: f32, q: f32, dq: f32| {
            let freq = finite_or(freq, 0.0);
            if freq > 0.0 {
                Some((
                    freq.clamp(20.0, 20_000.0).min(nyq),
                    finite_or(q, dq).clamp(0.1, 30.0),
                ))
            } else {
                None
            }
        };
        let lp = stage(p.lpf, p.resonance, 0.707);
        let hp = stage(p.hpf, p.hpq, 0.707);
        let bp = stage(p.bpf, p.bpq, 1.0);
        let fenv = |st: Option<(f32, f32)>, env: Option<EnvSpec>, kind: FilterKind| match (st, env)
        {
            (Some((base, q)), Some(env)) => {
                let peak = (base * 2.0_f32.powf(env.depth)).clamp(20.0, 20_000.0_f32.min(nyq));
                Some(FilterEnv {
                    env,
                    kind,
                    base,
                    q,
                    depth: (peak / base).log2(),
                })
            }
            _ => None,
        };
        let bits = finite_or(p.crush, 16.0).clamp(1.0, 16.0);
        let a = finite_or(p.shape, 0.0).clamp(0.0, 0.99);
        Inserts {
            lpf: lp.map(|(f, q)| Biquad::lowpass(f, q, sr)),
            hpf: hp.map(|(f, q)| Biquad::highpass(f, q, sr)),
            bpf: bp.map(|(f, q)| Biquad::bandpass(f, q, sr)),
            envs: [
                fenv(lp, t.lp_env, FilterKind::Low),
                fenv(hp, t.hp_env, FilterKind::High),
                fenv(bp, t.bp_env, FilterKind::Band),
            ],
            vowel: VowelBank::new(t.vowel, sr),
            coarse_n: t.coarse.max(1),
            coarse_count: 0,
            coarse_hold: 0.0,
            crush_levels: if bits < 16.0 {
                2.0_f32.powf(bits - 1.0)
            } else {
                0.0
            },
            shape_k: if a > 0.0 { 2.0 * a / (1.0 - a) } else { 0.0 },
            postgain: t.postgain,
        }
    }

    fn has_envelope(&self) -> bool {
        self.envs.iter().any(|e| e.is_some())
    }

    /// Recompute the coefficients of the enveloped stages for t seconds after
    /// onset (control rate; filter state is kept).
    fn modulate(&mut self, t: f32, gate: f32, sr: f32) {
        let nyq = sr * 0.49;
        for i in 0..3 {
            if let Some(fe) = self.envs[i] {
                let f = (fe.base * 2.0_f32.powf(fe.depth * fe.env.value(gate, t)))
                    .clamp(20.0, 20_000.0_f32.min(nyq));
                let target = match i {
                    0 => self.lpf.as_mut(),
                    1 => self.hpf.as_mut(),
                    _ => self.bpf.as_mut(),
                };
                if let Some(bq) = target {
                    bq.set_coeffs(&fe.kind.make(f, fe.q, sr));
                }
            }
        }
    }

    /// lpf → hpf → bpf → vowel → coarse → crush → shape → postgain.
    /// All stages off returns `x` unchanged.
    #[inline]
    fn tick(&mut self, x: f32) -> f32 {
        let mut y = x;
        if let Some(f) = self.lpf.as_mut() {
            y = f.tick(y);
        }
        if let Some(f) = self.hpf.as_mut() {
            y = f.tick(y);
        }
        if let Some(f) = self.bpf.as_mut() {
            y = f.tick(y);
        }
        if let Some(v) = self.vowel.as_mut() {
            y = v.tick(y);
        }
        if self.coarse_n > 1 {
            if self.coarse_count == 0 {
                self.coarse_hold = y;
            }
            self.coarse_count = (self.coarse_count + 1) % self.coarse_n;
            y = self.coarse_hold;
        }
        if self.crush_levels > 0.0 {
            // floor(x + 0.5) = JS Math.round, as in the WaveShaper curve.
            let l = self.crush_levels;
            y = (y.clamp(-1.0, 1.0) * l + 0.5).floor() / l;
        }
        if self.shape_k > 0.0 {
            let k = self.shape_k;
            let c = y.clamp(-1.0, 1.0);
            y = (1.0 + k) * c / (1.0 + k * c.abs());
        }
        if self.postgain != 1.0 {
            y *= self.postgain;
        }
        y
    }
}

/// Per-voice modulation state (TL7c): pitch envelope, vibrato, and the
/// control-rate clock that also drives the filter envelopes.
#[derive(Clone, Copy)]
struct Mods {
    age: u32, // samples since activation
    gate: f32,
    penv: Option<EnvSpec>,
    vib: f32,
    vib_mod: f32,
    filter_env: bool,
    pitch: f64, // frequency multiplier, 1.0 = none
}

impl Mods {
    fn new(t: &Tl7cParams, filter_env: bool) -> Mods {
        Mods {
            age: 0,
            gate: t.gate,
            penv: t.penv,
            vib: t.vib,
            vib_mod: if t.vib > 0.0 { t.vib_mod } else { 0.0 },
            filter_env,
            pitch: 1.0,
        }
    }

    fn active(&self) -> bool {
        self.filter_env || self.penv.is_some() || self.vib > 0.0
    }

    /// Semitones of pitch modulation t seconds after onset.
    fn semitones(&self, t: f32) -> f32 {
        let env = self.penv.map_or(0.0, |e| e.depth * e.value(self.gate, t));
        let vib = if self.vib > 0.0 {
            self.vib_mod * (std::f32::consts::TAU * self.vib * t).sin()
        } else {
            0.0
        };
        env + vib
    }
}

// ── ActiveVoice — wraps a Voice with its stereo pan position ──────────────

struct ActiveVoice {
    voice: Voice,
    pan: f32, // -1.0 (left) … 0.0 (centre) … 1.0 (right)
    inserts: Inserts,
    sends: Sends,
    mods: Mods,
}

// ── Orbit sends (Phase TL7b) ──────────────────────────────────────────────
// The reverb and delay of each orbit are Web Audio nodes on the main thread
// (app/src/repulse/orbits.cljs). The engine only writes each voice's panned
// output, scaled by its send levels, into per-orbit send buses.

/// Number of orbits (0–3). Must match `n-orbits` in orbits.cljs.
pub const N_ORBITS: usize = 4;
/// Buses in the `process_block_buses` output: dry + room ×4 + delay ×4.
pub const N_BUSES: usize = 1 + 2 * N_ORBITS;
/// Render quantum the bus buffer is pre-allocated for.
const BUS_FRAMES: usize = 128;

/// Send levels of one event (trigger_v3 slots 12–14). Levels are clamped to
/// 0–1 and the orbit to 0–3 (rounded) in `Sends::new`.
#[derive(Clone, Copy, Debug, PartialEq)]
struct Sends {
    room: f32,
    delay: f32,
    orbit: usize,
}

impl Sends {
    const OFF: Sends = Sends {
        room: 0.0,
        delay: 0.0,
        orbit: 0,
    };

    fn new(room: f32, delay: f32, orbit: f32) -> Self {
        Sends {
            room: finite_or(room, 0.0).clamp(0.0, 1.0),
            delay: finite_or(delay, 0.0).clamp(0.0, 1.0),
            orbit: finite_or(orbit, 0.0)
                .round()
                .clamp(0.0, (N_ORBITS - 1) as f32) as usize,
        }
    }
}

// ── Pending event ──────────────────────────────────────────────────────────

struct Pending {
    time: f64,
    value: String,
    amp: f32,    // 0.0–2.0 (amp × gain × velocity), default 1.0
    attack: f32, // seconds, default 0.001
    decay: f32,  // seconds, default 1.5 for tones
    pan: f32,    // -1.0–1.0, default 0.0
    inserts: InsertParams,
    sends: Sends,
    tl7c: Tl7cParams,
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

/// Build a Pending from a trigger_v3 slot array (see `AudioEngine::trigger_v3`).
fn pending_v3(value: &str, time: f64, params: &[f32]) -> Pending {
    let slot = |i: usize, d: f32| params.get(i).copied().unwrap_or(d);
    let off = InsertParams::OFF;
    Pending {
        time,
        value: value.to_string(),
        amp: slot(0, 1.0),
        attack: slot(1, 0.001),
        decay: slot(2, 1.5),
        pan: slot(3, 0.0),
        inserts: InsertParams {
            lpf: slot(4, off.lpf),
            resonance: slot(5, off.resonance),
            hpf: slot(6, off.hpf),
            hpq: slot(7, off.hpq),
            bpf: slot(8, off.bpf),
            bpq: slot(9, off.bpq),
            shape: slot(10, off.shape),
            crush: slot(11, off.crush),
        },
        sends: Sends::new(slot(12, 0.0), slot(13, 0.0), slot(14, 0.0)),
        tl7c: Tl7cParams::from_slots(slot),
    }
}

#[wasm_bindgen]
pub struct AudioEngine {
    sample_rate: f32,
    voices: Vec<ActiveVoice>,
    pending: Vec<Pending>,
    noise_seed: u32,
    amp_transition: Option<Transition>,
    pan_transition: Option<Transition>,
    /// Phase TL7b: pre-allocated bus buffer for `process_block_buses_raw`,
    /// layout [bus][frame][channel] for the current block length n.
    bus: Vec<f32>,
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
            bus: vec![0.0; BUS_FRAMES * 2 * N_BUSES],
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
            inserts: InsertParams::OFF,
            sends: Sends::OFF,
            tl7c: Tl7cParams::OFF,
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
            inserts: InsertParams::OFF,
            sends: Sends::OFF,
            tl7c: Tl7cParams::OFF,
        });
    }

    /// Schedule a sound event with a parameter slot array (Phase TL7a).
    ///
    /// Slot layout (fixed; later phases append, never reorder):
    /// 0 amp · 1 attack · 2 decay · 3 pan · 4 lpf · 5 resonance · 6 hpf · 7 hpq
    /// 8 bpf · 9 bpq · 10 shape · 11 crush        (filter slots: 0.0 = off)
    /// 12 room · 13 delay · 14 orbit              (Phase TL7b sends)
    ///
    /// A missing trailing slot means its default.
    pub fn trigger_v3(&mut self, value: &str, time: f64, params: &[f32]) {
        self.pending.push(pending_v3(value, time, params));
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

    /// Generate one audio quantum into all buses (Phase TL7b). Returns
    /// `N_BUSES · n_samples · 2` floats, layout [bus][frame][channel]:
    /// bus 0 = dry (identical to `process_block`), 1..=4 = room sends of
    /// orbits 0–3, 5..=8 = delay sends of orbits 0–3.
    pub fn process_block_buses(&mut self, n_samples: u32, current_time: f64) -> Float32Array {
        let n = n_samples as usize;
        self.process_block_buses_raw(n_samples, current_time);
        let len = N_BUSES * n * 2;
        let arr = Float32Array::new_with_length(len as u32);
        arr.copy_from(&self.bus[..len]);
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
            bus: vec![0.0; BUS_FRAMES * 2 * N_BUSES],
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
            inserts: InsertParams::OFF,
            sends: Sends::OFF,
            tl7c: Tl7cParams::OFF,
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
            inserts: InsertParams::OFF,
            sends: Sends::OFF,
            tl7c: Tl7cParams::OFF,
        });
    }

    /// Pure-Rust stereo render — no JS types. Returns interleaved [L0, R0, L1, …].
    /// The dry signal only: send levels are ignored here.
    pub fn process_block_raw(&mut self, n_samples: u32, current_time: f64) -> Vec<f32> {
        let n = n_samples as usize;
        if n == 0 {
            return Vec::new();
        }
        let mut buf = vec![0.0f32; n * 2];
        self.render(n, current_time, &mut buf, false);
        buf
    }

    /// Render one block into the pre-allocated bus buffer (Phase TL7b) and
    /// return it, layout [bus][frame][channel]; see `process_block_buses`.
    /// Bus 0 is bit-identical to `process_block_raw`. The buffer only grows
    /// when n exceeds its capacity (never for 128-frame render quanta).
    pub fn process_block_buses_raw(&mut self, n_samples: u32, current_time: f64) -> &[f32] {
        let n = n_samples as usize;
        let len = N_BUSES * n * 2;
        if self.bus.len() < len {
            self.bus.resize(len, 0.0);
        }
        let mut bus = std::mem::take(&mut self.bus);
        bus[..len].fill(0.0);
        if n > 0 {
            self.render(n, current_time, &mut bus, true);
        }
        self.bus = bus;
        &self.bus[..len]
    }

    /// Shared render loop. `out` holds at least n·2 floats (dry, interleaved);
    /// with `sends`, it holds N_BUSES·n·2 zeroed floats and each voice is also
    /// added to the room/delay bus of its orbit. The engine's amp/pan
    /// transitions and the output clamp apply to the dry bus only.
    fn render(&mut self, n: usize, current_time: f64, out: &mut [f32], sends: bool) {
        let sr = self.sample_rate;
        let block_end = current_time + n as f64 / sr as f64;

        let pending = std::mem::take(&mut self.pending);
        let mut deferred = Vec::new();
        let mut scheduled: Vec<Vec<Pending>> = (0..n).map(|_| Vec::new()).collect();
        for p in pending {
            if p.time < block_end {
                let sample_idx = if p.time <= current_time {
                    0
                } else {
                    ((p.time - current_time) * sr as f64).floor() as usize
                };
                scheduled[sample_idx.min(n.saturating_sub(1))].push(p);
            } else {
                deferred.push(p);
            }
        }
        self.pending = deferred;

        let stride = n * 2;
        for i in 0..n {
            for p in std::mem::take(&mut scheduled[i]) {
                self.activate(p);
            }

            let mut l = 0.0f32;
            let mut r = 0.0f32;
            for av in self.voices.iter_mut() {
                // TL7c: envelopes and vibrato at control rate.
                let m = &mut av.mods;
                if m.active() && m.age % MOD_PERIOD == 0 {
                    let t = m.age as f32 / sr;
                    if m.filter_env {
                        av.inserts.modulate(t, m.gate, sr);
                    }
                    if m.penv.is_some() || m.vib > 0.0 {
                        m.pitch = 2.0_f64.powf(m.semitones(t) as f64 / 12.0);
                    }
                }
                m.age = m.age.wrapping_add(1);
                let s = av.inserts.tick(av.voice.tick(sr, m.pitch));
                let angle = (av.pan + 1.0) / 2.0 * std::f32::consts::FRAC_PI_2;
                let (vl, vr) = (s * angle.cos(), s * angle.sin());
                l += vl;
                r += vr;
                if sends {
                    let Sends { room, delay, orbit } = av.sends;
                    if room > 0.0 {
                        let k = (1 + orbit) * stride + i * 2;
                        out[k] += vl * room;
                        out[k + 1] += vr * room;
                    }
                    if delay > 0.0 {
                        let k = (1 + N_ORBITS + orbit) * stride + i * 2;
                        out[k] += vl * delay;
                        out[k + 1] += vr * delay;
                    }
                }
            }

            let amp_scale = if let Some(ref mut tr) = self.amp_transition {
                tr.tick()
            } else {
                1.0
            };

            if let Some(ref mut tr) = self.pan_transition {
                let pan_val = tr.tick().clamp(-1.0, 1.0);
                let bal_angle = (pan_val + 1.0) / 2.0 * std::f32::consts::FRAC_PI_2;
                out[i * 2] = ((l + r) * bal_angle.cos() * amp_scale).clamp(-1.0, 1.0);
                out[i * 2 + 1] = ((l + r) * bal_angle.sin() * amp_scale).clamp(-1.0, 1.0);
            } else {
                out[i * 2] = (l * amp_scale).clamp(-1.0, 1.0);
                out[i * 2 + 1] = (r * amp_scale).clamp(-1.0, 1.0);
            }
        }
        self.voices.retain(|av| !av.voice.is_silent());
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
        // 0–2: amp × gain may exceed 1 (TL7a); the output clamp at ±1 stays.
        let amp = p.amp.clamp(0.0, 2.0);
        let inserts = Inserts::new(&p.inserts, &p.tl7c, sr);
        let mods = Mods::new(&p.tl7c, inserts.has_envelope());
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
            let first_char = rest.chars().next().unwrap_or('0');
            if first_char.is_ascii_digit() {
                // Legacy format: "fm:<carrier_hz>:<index>:<ratio>"
                let parts: Vec<&str> = rest.splitn(3, ':').collect();
                let carrier_freq = parts[0].parse::<f64>().unwrap_or(440.0);
                let index: f32 = parts.get(1).and_then(|s| s.parse().ok()).unwrap_or(1.0);
                let ratio: f64 = parts.get(2).and_then(|s| s.parse().ok()).unwrap_or(2.0);
                let peak = amp * 0.5;
                Voice::FM {
                    carrier_phase: 0.0,
                    mod_phase: 0.0,
                    carrier_freq,
                    mod_freq: carrier_freq * ratio,
                    index,
                    peak_gain: peak,
                    gain: 0.0,
                    attack_time: p.attack,
                    decay_time: p.decay * 0.4,
                    sustain: 0.5,
                    release_time: p.decay * 0.6,
                    env_phase: 0.0,
                    body_filters: vec![],
                    noise_amp: 0.0,
                    noise_state: seed,
                    noise_hpf: Biquad::highpass(3000.0, 0.7, sr),
                }
            } else {
                // Preset format: "fm:<preset>:<freq>"
                let parts: Vec<&str> = rest.splitn(2, ':').collect();
                let preset = parts[0];
                let carrier_freq: f64 = parts.get(1).and_then(|s| s.parse().ok()).unwrap_or(440.0);
                let (index, ratio, attack_time, decay_time, sustain, release_time) =
                    fm_preset(preset);
                let peak = amp * 0.7;
                Voice::FM {
                    carrier_phase: 0.0,
                    mod_phase: 0.0,
                    carrier_freq,
                    mod_freq: carrier_freq * ratio,
                    index,
                    peak_gain: peak,
                    gain: 0.0,
                    attack_time,
                    decay_time,
                    sustain,
                    release_time,
                    env_phase: 0.0,
                    body_filters: fm_body_filters(preset, sr),
                    noise_amp: fm_noise_amp(preset),
                    noise_state: seed,
                    noise_hpf: Biquad::highpass(3000.0, 0.7, sr),
                }
            }
        } else if let Some(rest) = value.strip_prefix("ks:") {
            let parts: Vec<&str> = rest.splitn(2, ':').collect();
            let preset = parts.first().copied().unwrap_or("guitar");
            let freq: f32 = parts.get(1).and_then(|s| s.parse().ok()).unwrap_or(440.0);
            let (feedback, brightness, pick_pos, vib_depth, vib_rate, excitation) =
                ks_preset(preset);
            let buf_len = ((sr / freq).floor() as usize).clamp(2, 2205);
            let peak = amp * 0.5;
            let fill = peak * excitation;
            let mut buf = vec![0.0f32; 2205];
            for slot in buf.iter_mut().take(buf_len) {
                *slot = lcg_next(&mut self.noise_seed) * fill;
            }
            let comb = (buf_len as f32 * pick_pos).floor() as usize;
            if comb > 0 {
                for i in (0..buf_len).step_by(comb) {
                    buf[i] = 0.0;
                }
            }
            self.voices.push(ActiveVoice {
                voice: Voice::KarplusStrong {
                    buf,
                    buf_len,
                    write_pos: 0,
                    lp_prev: 0.0,
                    feedback,
                    brightness,
                    gain: fill,
                    vib_phase: 0.0,
                    vib_depth,
                    vib_rate,
                    body_filters: ks_body_filters(preset, sr),
                },
                pan: p.pan.clamp(-1.0, 1.0),
                inserts,
                sends: p.sends,
                mods,
            });
            return;
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
            inserts,
            sends: p.sends,
            mods,
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
    fn fm_presets_all_produce_output() {
        let presets = [
            "sax",
            "trumpet",
            "trumpet-muted",
            "trombone",
            "synth-brass",
            "harmon-out",
            "harmon-in",
            "epiano",
            "bell",
            "marimba",
            "flute",
        ];
        for preset in &presets {
            let mut eng = AudioEngine::new_for_test(44100.0);
            eng.trigger_raw(&format!("fm:{}:440", preset), 0.0);
            let buf = eng.process_block_raw(4410, 0.0);
            assert!(
                rms(&buf) > 0.001,
                "FM preset '{}' should produce audible output",
                preset
            );
        }
    }

    #[test]
    fn fm_bell_rings_for_one_second() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        eng.trigger_raw("fm:bell:440", 0.0);
        // Render 0.9s — bell has 1.2s ring, should still be audible
        let buf = eng.process_block_raw(39690, 0.0);
        let tail: Vec<f32> = buf[buf.len() - 4410..].to_vec();
        assert!(
            rms(&tail) > 1e-4,
            "bell should still be audible at 0.9s, got rms={}",
            rms(&tail)
        );
    }

    #[test]
    fn karplus_strong_guitar_produces_nonsilent_output() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        eng.trigger_raw("ks:guitar:440", 0.0);
        let buf = eng.process_block_raw(4410, 0.0);
        assert!(rms(&buf) > 0.001, "guitar KS should produce audible output");
    }

    #[test]
    fn karplus_strong_all_presets_produce_output() {
        for preset in &["guitar", "harp", "koto", "pizz", "lute", "mandolin"] {
            let mut eng = AudioEngine::new_for_test(44100.0);
            eng.trigger_raw(&format!("ks:{}:440", preset), 0.0);
            let buf = eng.process_block_raw(4410, 0.0);
            assert!(
                rms(&buf) > 0.001,
                "preset '{}' should produce audible output",
                preset
            );
        }
    }

    #[test]
    fn karplus_strong_decays_to_silence() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        eng.trigger_raw("ks:pizz:440", 0.0);
        // Render 5s — pizz has low feedback so should die out
        let buf = eng.process_block_raw(220500, 0.0);
        let tail: Vec<f32> = buf[buf.len() - 2000..].to_vec();
        assert!(rms(&tail) < 1e-3, "pizz KS should decay to silence");
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
    fn pending_event_starts_at_in_block_sample_offset() {
        let mut eng = AudioEngine::new_for_test(44100.0);
        let event_time = 0.001;
        let sample_idx = (event_time * 44100.0_f64).floor() as usize;
        eng.trigger_raw_v2("440", event_time, 1.0, 0.001, 1.5, 0.0);

        let buf = eng.process_block_raw(256, 0.0);
        let before: Vec<f32> = buf[..sample_idx * 2].to_vec();
        let after_start = ((sample_idx + 8) * 2).min(buf.len());
        let after_end = ((sample_idx + 80) * 2).min(buf.len());
        let after: Vec<f32> = buf[after_start..after_end].to_vec();

        assert!(
            rms(&before) < 1e-8,
            "samples before scheduled time should stay silent"
        );
        assert!(
            rms(&after) > 0.001,
            "samples after scheduled time should contain the event"
        );
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

#[cfg(test)]
mod tl7a_insert_tests {
    use super::*;

    const SR: f32 = 44100.0;

    /// Goertzel magnitude of `freq` in `buf`.
    fn goertzel(buf: &[f32], freq: f32, sr: f32) -> f32 {
        let w = 2.0 * std::f64::consts::PI * freq as f64 / sr as f64;
        let coeff = 2.0 * w.cos();
        let (mut s1, mut s2) = (0.0f64, 0.0f64);
        for &x in buf {
            let s0 = x as f64 + coeff * s1 - s2;
            s2 = s1;
            s1 = s0;
        }
        ((s1 * s1 + s2 * s2 - coeff * s1 * s2).max(0.0)).sqrt() as f32
    }

    fn db(ratio: f32) -> f32 {
        20.0 * ratio.max(1e-12).log10()
    }

    fn sine(freq: f32, amp: f32, secs: f32) -> Vec<f32> {
        let n = (secs * SR) as usize;
        (0..n)
            .map(|i| amp * (2.0 * std::f32::consts::PI * freq * i as f32 / SR).sin())
            .collect()
    }

    fn params(f: impl FnOnce(&mut InsertParams)) -> InsertParams {
        let mut p = InsertParams::OFF;
        f(&mut p);
        p
    }

    /// Run `input` through inserts built from `p`; drop the first 0.05 s (filter settling).
    fn through(p: InsertParams, input: &[f32]) -> Vec<f32> {
        let mut ins = Inserts::new(&p, &Tl7cParams::OFF, SR);
        let out: Vec<f32> = input.iter().map(|&x| ins.tick(x)).collect();
        out[(0.05 * SR) as usize..].to_vec()
    }

    /// Filter response at `freq` in dB (filtered vs unfiltered sine).
    fn response_db(p: InsertParams, freq: f32) -> f32 {
        let x = sine(freq, 0.5, 0.5);
        let dry = &x[(0.05 * SR) as usize..];
        db(goertzel(&through(p, &x), freq, SR) / goertzel(dry, freq, SR))
    }

    #[test]
    fn inserts_off_is_identity() {
        let mut ins = Inserts::new(&InsertParams::OFF, &Tl7cParams::OFF, SR);
        for &x in &[0.0f32, 0.25, -0.731, 1.5, -2.0] {
            assert_eq!(ins.tick(x).to_bits(), x.to_bits());
        }
        assert!(ins.lpf.is_none() && ins.hpf.is_none() && ins.bpf.is_none());
    }

    #[test]
    fn lpf_500_cuts_5k_and_passes_200() {
        let p = params(|p| p.lpf = 500.0);
        let at_5k = response_db(p, 5000.0);
        let at_200 = response_db(p, 200.0);
        assert!(at_5k <= -20.0, "5 kHz through lpf 500: {at_5k} dB");
        assert!(at_200.abs() <= 1.0, "200 Hz through lpf 500: {at_200} dB");
    }

    #[test]
    fn hpf_2000_cuts_200() {
        let p = params(|p| p.hpf = 2000.0);
        let at_200 = response_db(p, 200.0);
        assert!(at_200 <= -20.0, "200 Hz through hpf 2000: {at_200} dB");
    }

    #[test]
    fn bpf_1000_q5_rejects_100_and_8k() {
        let p = params(|p| {
            p.bpf = 1000.0;
            p.bpq = 5.0;
        });
        let centre = response_db(p, 1000.0);
        for f in [100.0, 8000.0] {
            let rel = centre - response_db(p, f);
            assert!(rel >= 15.0, "{f} Hz is only {rel} dB below 1 kHz");
        }
    }

    #[test]
    fn crush_4_has_at_most_16_values() {
        let mut e = AudioEngine::new_for_test(SR);
        e.trigger_v3(
            "440",
            0.0,
            &[
                1.0, 0.001, 1.5, 0.0, 0.0, 0.707, 0.0, 0.707, 0.0, 1.0, 0.0, 4.0,
            ],
        );
        let buf = e.process_block_raw((0.1 * SR) as u32, 0.0);
        let mut vals: Vec<u32> = buf.iter().step_by(2).map(|x| x.to_bits()).collect();
        vals.sort_unstable();
        vals.dedup();
        assert!(vals.len() <= 16, "{} distinct values", vals.len());
        assert!(vals.len() >= 3, "crush must still pass a waveform");
    }

    #[test]
    fn shape_08_raises_third_harmonic_20db() {
        let x = sine(440.0, 0.5, 0.3);
        let dry = through(InsertParams::OFF, &x);
        let wet = through(params(|p| p.shape = 0.8), &x);
        let rise = db(goertzel(&wet, 1320.0, SR) / goertzel(&dry, 1320.0, SR));
        assert!(rise >= 20.0, "3rd harmonic rose by {rise} dB");
    }

    #[test]
    fn amp_clamp_allows_two() {
        let render = |amp: f32| {
            let mut e = AudioEngine::new_for_test(SR);
            e.trigger_raw_v2("440", 0.0, amp, 0.001, 1.5, 0.0);
            let b = e.process_block_raw(4410, 0.0);
            (b.iter().map(|x| x * x).sum::<f32>() / b.len() as f32).sqrt()
        };
        let ratio = render(1.6) / render(0.8);
        assert!((ratio - 2.0).abs() < 0.01, "amp 1.6 / amp 0.8 = {ratio}");
    }

    /// Fingerprint of a fixed trigger_v2 render. GOLDEN was recorded on the engine
    /// before Phase TL7a: the no-insert path must stay bit-identical.
    fn v2_fingerprint(v3: bool) -> u64 {
        let mut e = AudioEngine::new_for_test(SR);
        let voices = [
            "saw:261.63",
            "square:220:0.3",
            "bd",
            "sd",
            "hh",
            "440",
            "fm:epiano:220",
            "ks:nylon:110",
            "noise",
        ];
        for (i, v) in voices.iter().enumerate() {
            let t = i as f64 * 0.05;
            if v3 {
                e.trigger_v3(v, t, &[0.8, 0.01, 0.3, 0.25]);
            } else {
                e.trigger_raw_v2(v, t, 0.8, 0.01, 0.3, 0.25);
            }
        }
        let mut h: u64 = 0;
        for b in 0..40 {
            for s in e.process_block_raw(128, b as f64 * 128.0 / SR as f64) {
                h = h
                    .wrapping_mul(1_099_511_628_211)
                    .wrapping_add(s.to_bits() as u64);
            }
        }
        h
    }

    const GOLDEN: u64 = 0xfd60_f3d5_f69d_e5e7;

    #[test]
    fn trigger_v2_render_is_bit_identical_to_pre_tl7a() {
        assert_eq!(v2_fingerprint(false), GOLDEN);
    }

    #[test]
    fn trigger_v3_without_inserts_matches_v2() {
        assert_eq!(v2_fingerprint(true), GOLDEN);
    }

    #[test]
    fn trigger_v3_missing_slots_default() {
        let p = pending_v3("bd", 1.0, &[0.5]);
        assert_eq!(p.amp, 0.5);
        assert_eq!(p.decay, 1.5);
        assert_eq!(p.inserts, InsertParams::OFF);
    }

    /// Relative levels (dB vs the 262 Hz fundamental) of a saw at C4 through
    /// lpf 1200, resonance 4 — the WASM half of the JS↔WASM parity check.
    /// e2e/audio-render.spec.ts asserts the offline JS render (make-saw → native
    /// BiquadFilterNode) against PARITY_DB within 2 dB.
    pub(crate) fn parity_levels() -> [f32; 2] {
        let mut e = AudioEngine::new_for_test(SR);
        e.trigger_v3(
            "saw:261.6255653005986",
            0.0,
            &[
                1.0, 0.001, 1.5, 0.0, 1200.0, 4.0, 0.0, 0.707, 0.0, 1.0, 0.0, 16.0,
            ],
        );
        let n = (0.5 * SR) as u32;
        let buf = e.process_block_raw(n, 0.0);
        let mono: Vec<f32> = buf.iter().step_by(2).copied().collect();
        let w = &mono[(0.05 * SR) as usize..];
        let f0 = goertzel(w, 261.625_57, SR);
        // 1200 Hz is not a harmonic of C4; measure the harmonics nearest it
        // (1308 Hz = 5th) and 4000 Hz (3924 Hz = 15th), as the JS side does.
        [
            db(goertzel(w, 261.625_57 * 5.0, SR) / f0),
            db(goertzel(w, 261.625_57 * 15.0, SR) / f0),
        ]
    }

    pub(crate) const PARITY_DB: [f32; 2] = [-4.69, -44.36];

    #[test]
    fn parity_reference_levels() {
        let l = parity_levels();
        println!("PARITY_DB = {:?}", l);
        for (got, want) in l.iter().zip(PARITY_DB.iter()) {
            assert!(
                (got - want).abs() <= 0.5,
                "parity level {got} dB, pinned {want} dB"
            );
        }
    }
}

// ── Phase TL7b: orbit send buses ───────────────────────────────────────────
#[cfg(test)]
mod send_tests {
    use super::*;

    const SR: f32 = 44100.0;

    /// trigger_v3 slots with defaults for 0–11 and the given sends in 12–14.
    fn slots(room: f32, delay: f32, orbit: f32) -> Vec<f32> {
        vec![
            1.0, 0.001, 1.5, 0.0, 0.0, 0.707, 0.0, 0.707, 0.0, 1.0, 0.0, 16.0, room, delay, orbit,
        ]
    }

    /// Render `blocks` 128-frame blocks; return the per-bus sum of |x|.
    fn bus_energy(e: &mut AudioEngine, blocks: usize) -> [f32; N_BUSES] {
        let mut acc = [0.0f32; N_BUSES];
        for b in 0..blocks {
            let t = (b * 128) as f64 / SR as f64;
            let buf = e.process_block_buses_raw(128, t);
            for (k, slot) in acc.iter_mut().enumerate() {
                *slot += buf[k * 256..(k + 1) * 256]
                    .iter()
                    .map(|x| x.abs())
                    .sum::<f32>();
            }
        }
        acc
    }

    #[test]
    fn room_on_orbit_2_writes_only_dry_and_room_bus_3() {
        let mut e = AudioEngine::new_for_test(SR);
        e.trigger_v3("440", 0.0, &slots(0.5, 0.0, 2.0));
        let acc = bus_energy(&mut e, 40);
        for (k, v) in acc.iter().enumerate() {
            if k == 0 || k == 3 {
                assert!(*v > 0.0, "bus {k} should carry signal");
            } else {
                assert_eq!(*v, 0.0, "bus {k} should be silent");
            }
        }
        // room 0.5 → the send is half the (unclamped) dry level
        let ratio = acc[3] / acc[0];
        assert!((ratio - 0.5).abs() < 1e-3, "room/dry ratio {ratio}");
    }

    #[test]
    fn delay_on_orbit_1_writes_delay_bus_6() {
        let mut e = AudioEngine::new_for_test(SR);
        e.trigger_v3("440", 0.0, &slots(0.0, 0.8, 1.0));
        let acc = bus_energy(&mut e, 20);
        for (k, v) in acc.iter().enumerate() {
            if k == 0 || k == 1 + N_ORBITS + 1 {
                assert!(*v > 0.0, "bus {k} should carry signal");
            } else {
                assert_eq!(*v, 0.0, "bus {k} should be silent");
            }
        }
    }

    #[test]
    fn room_0_leaves_send_buses_exactly_zero() {
        let mut e = AudioEngine::new_for_test(SR);
        e.trigger_v3("440", 0.0, &slots(0.0, 0.0, 0.0));
        e.trigger_v3("bd", 0.0, &slots(0.0, 0.0, 3.0));
        for b in 0..40 {
            let buf = e.process_block_buses_raw(128, (b * 128) as f64 / SR as f64);
            assert!(buf[256..].iter().all(|x| x.to_bits() == 0), "block {b}");
        }
    }

    #[test]
    fn sends_clamp_and_out_of_range_orbit_uses_3() {
        let s = Sends::new(1.7, -0.2, 7.0);
        assert_eq!(
            s,
            Sends {
                room: 1.0,
                delay: 0.0,
                orbit: 3
            }
        );
        assert_eq!(Sends::new(f32::NAN, 0.5, -2.0).orbit, 0);
        assert_eq!(Sends::new(0.0, 0.0, 1.4).orbit, 1);
        // missing trailing slots = no sends (TL7a messages stay valid)
        let p = pending_v3("440", 0.0, &[1.0, 0.001, 1.5, 0.0]);
        assert_eq!(p.sends, Sends::OFF);
    }

    /// Dry bus of process_block_buses_raw is bit-identical to process_block_raw,
    /// with and without sends on the voices.
    #[test]
    fn dry_bus_is_bit_identical_to_process_block_raw() {
        let mut a = AudioEngine::new_for_test(SR);
        let mut b = AudioEngine::new_for_test(SR);
        for e in [&mut a, &mut b] {
            e.trigger_v3("440", 0.0, &slots(0.6, 0.3, 1.0));
            e.trigger_v3("saw:110", 0.01, &slots(0.0, 0.9, 3.0));
            e.trigger_raw("sd", 0.02);
            e.trigger_v2("ks:guitar:196", 0.05, 0.8, 0.001, 1.5, -0.4);
        }
        for blk in 0..200 {
            let t = (blk * 128) as f64 / SR as f64;
            let raw = a.process_block_raw(128, t);
            let bus = b.process_block_buses_raw(128, t);
            for i in 0..256 {
                assert_eq!(raw[i].to_bits(), bus[i].to_bits(), "block {blk} sample {i}");
            }
        }
    }

    #[test]
    fn bus_buffer_is_not_reallocated_across_1000_blocks() {
        let mut e = AudioEngine::new_for_test(SR);
        let ptr = e.bus.as_ptr();
        let cap = e.bus.capacity();
        for blk in 0..1000 {
            if blk % 50 == 0 {
                e.trigger_v3("440", (blk * 128) as f64 / SR as f64, &slots(0.5, 0.5, 2.0));
            }
            let out = e.process_block_buses_raw(128, (blk * 128) as f64 / SR as f64);
            assert_eq!(out.as_ptr(), ptr, "block {blk}");
        }
        assert_eq!(e.bus.as_ptr(), ptr);
        assert_eq!(e.bus.capacity(), cap);
    }
}

// ── Phase TL7c: envelopes, pitch, vowel, coarse, postgain ────────────────
#[cfg(test)]
mod tl7c_tests {
    use super::*;

    const SR: f32 = 44100.0;
    const A2: f32 = 110.0;

    /// trigger_v3 slots 0–40, all at their defaults (TL7a/TL7b/TL7c off).
    fn base_slots() -> Vec<f32> {
        let mut v = vec![
            1.0, 0.001, 1.5, 0.0, // amp attack decay pan
            0.0, 0.707, 0.0, 0.707, 0.0, 1.0, 0.0, 16.0, // inserts
            0.0, 0.0, 0.0, // sends
            0.0, // gate
        ];
        for _ in 0..4 {
            v.extend_from_slice(&[0.0, 0.001, 0.2, 0.0, 0.1]); // lp hp bp pitch envs
        }
        v.extend_from_slice(&[0.0, 0.5, 0.0, 1.0, 1.0]); // vib vib-mod vowel coarse postgain
        assert_eq!(v.len(), 41);
        v
    }

    fn slots(f: impl FnOnce(&mut Vec<f32>)) -> Vec<f32> {
        let mut v = base_slots();
        f(&mut v);
        v
    }

    /// Mono render of one voice triggered at 0 with the given slots.
    fn render(value: &str, sl: &[f32], secs: f32) -> Vec<f32> {
        let mut e = AudioEngine::new_for_test(SR);
        e.trigger_v3(value, 0.0, sl);
        let buf = e.process_block_raw((secs * SR) as u32, 0.0);
        buf.iter().step_by(2).copied().collect()
    }

    fn goertzel(buf: &[f32], freq: f32) -> f32 {
        let w = 2.0 * std::f64::consts::PI * freq as f64 / SR as f64;
        let coeff = 2.0 * w.cos();
        let (mut s1, mut s2) = (0.0f64, 0.0f64);
        for &x in buf {
            let s0 = x as f64 + coeff * s1 - s2;
            s2 = s1;
            s1 = s0;
        }
        ((s1 * s1 + s2 * s2 - coeff * s1 * s2).max(0.0)).sqrt() as f32
    }

    fn db(ratio: f32) -> f32 {
        20.0 * ratio.max(1e-12).log10()
    }

    fn win(x: &[f32], from: f32, to: f32) -> &[f32] {
        &x[(from * SR) as usize..(to * SR) as usize]
    }

    /// Hann-windowed copy (keeps leakage from the strong low harmonics out of
    /// the 1–3 kHz bins).
    fn hann(x: &[f32]) -> Vec<f32> {
        let n = x.len() as f32;
        x.iter()
            .enumerate()
            .map(|(i, s)| s * 0.5 * (1.0 - (std::f32::consts::TAU * i as f32 / n).cos()))
            .collect()
    }

    /// Energy of the A2 harmonics between 1 and 3 kHz (k = 10…27), Hann window.
    fn high_band(x: &[f32]) -> f32 {
        let w = hann(x);
        (10..=27)
            .map(|k| goertzel(&w, A2 * k as f32).powi(2))
            .sum::<f32>()
            .sqrt()
    }

    fn rms(x: &[f32]) -> f32 {
        (x.iter().map(|s| s * s).sum::<f32>() / x.len() as f32).sqrt()
    }

    fn fingerprint(sl: &[f32]) -> u64 {
        let mut e = AudioEngine::new_for_test(SR);
        for (i, v) in [
            "saw:261.63",
            "square:220:0.3",
            "bd",
            "440",
            "fm:epiano:220",
            "ks:nylon:110",
        ]
        .iter()
        .enumerate()
        {
            e.trigger_v3(v, i as f64 * 0.05, sl);
        }
        let mut h: u64 = 0;
        for b in 0..40 {
            for s in e.process_block_raw(128, b as f64 * 128.0 / SR as f64) {
                h = h
                    .wrapping_mul(1_099_511_628_211)
                    .wrapping_add(s.to_bits() as u64);
            }
        }
        h
    }

    #[test]
    fn env_value_breakpoints() {
        let e = EnvSpec {
            depth: 1.0,
            attack: 0.1,
            decay: 0.2,
            sustain: 0.5,
            release: 0.4,
        };
        // Same table as inserts_test.cljs env-value-breakpoints.
        for (t, want) in [
            (0.0, 0.0),
            (0.05, 0.5),
            (0.1, 1.0),
            (0.2, 0.75),
            (0.3, 0.5),
            (1.0, 0.5),
            (1.2, 0.25),
            (1.4, 0.0),
            (2.0, 0.0),
        ] {
            assert!((e.value(1.0, t) - want).abs() < 1e-5, "t={t}");
        }
        assert!(
            (e.value(0.05, 0.05) - 0.5).abs() < 1e-5,
            "release from inside the attack"
        );
        assert!((e.value(0.05, 0.25) - 0.25).abs() < 1e-5);
        assert!(
            (e.value(0.0, 100.0) - 0.5).abs() < 1e-5,
            "gate 0 = infinite"
        );
    }

    #[test]
    fn slots_parse_and_clamp() {
        let t = Tl7cParams::from_slots(|i, d| {
            *slots(|v| {
                v[15] = 0.5;
                v[16] = 99.0;
                v[17] = -1.0;
                v[31] = 12.0;
                v[36] = 100.0;
                v[38] = 3.2;
                v[39] = 500.0;
                v[40] = 9.0;
            })
            .get(i)
            .unwrap_or(&d)
        });
        assert_eq!(t.gate, 0.5);
        let lp = t.lp_env.unwrap();
        assert_eq!((lp.depth, lp.attack), (10.0, 0.0));
        assert!(t.hp_env.is_none() && t.bp_env.is_none());
        assert_eq!(t.penv.unwrap().depth, 12.0);
        assert_eq!((t.vib, t.vowel, t.coarse, t.postgain), (40.0, 3, 64, 4.0));
        // Missing trailing slots → defaults (a 15-slot TL7b trigger).
        let short = [0.0f32; 15];
        assert_eq!(
            Tl7cParams::from_slots(|i, d| short.get(i).copied().unwrap_or(d)),
            Tl7cParams::OFF
        );
    }

    #[test]
    fn all_default_tl7c_slots_are_bit_identical() {
        let fifteen = &base_slots()[..15];
        assert_eq!(fingerprint(&base_slots()), fingerprint(fifteen));
        assert_eq!(fingerprint(&base_slots()), fingerprint(&base_slots()[..4]));
    }

    /// Filter-envelope ratio, early (5–25 ms) over late (300–320 ms) energy of
    /// the 1–3 kHz harmonics of a saw at A2 through lpf 300, lp-env 4,
    /// lp-decay 0.2 (decay 10 so the amp envelope barely moves) — the WASM half
    /// of the JS↔WASM check in e2e/audio-render.spec.ts.
    pub(crate) fn env_parity_db() -> f32 {
        let x = render(
            "saw:110",
            &slots(|v| {
                v[2] = 10.0;
                v[4] = 300.0;
                v[16] = 4.0;
                v[18] = 0.2;
            }),
            0.4,
        );
        db(high_band(win(&x, 0.005, 0.025)) / high_band(win(&x, 0.3, 0.32)))
    }

    pub(crate) const ENV_PARITY_DB: f32 = 30.36;

    #[test]
    fn filter_envelope_opens_and_closes() {
        let r = env_parity_db();
        println!("ENV_PARITY_DB = {r}");
        assert!(r >= 10.0, "early/late high band {r} dB");
    }

    #[test]
    fn filter_envelope_parity_pinned() {
        let r = env_parity_db();
        assert!(
            (r - ENV_PARITY_DB).abs() <= 0.5,
            "{r} dB, pinned {ENV_PARITY_DB} dB"
        );
    }

    #[test]
    fn filter_release_starts_at_the_gate() {
        let x = render(
            "saw:110",
            &slots(|v| {
                v[2] = 10.0;
                v[4] = 300.0;
                v[15] = 0.25; // gate
                v[16] = 4.0;
                v[19] = 1.0; // lp-sustain
                v[20] = 0.05; // lp-release
            }),
            0.5,
        );
        let held = high_band(win(&x, 0.1, 0.2));
        let released = high_band(win(&x, 0.35, 0.45));
        assert!(db(held / released) >= 10.0, "{} dB", db(held / released));
    }

    #[test]
    fn hp_and_bp_envelopes_move_their_filter() {
        // hpf 2000 with a negative envelope opens the low end at the start.
        let x = render(
            "saw:110",
            &slots(|v| {
                v[2] = 10.0;
                v[6] = 2000.0;
                v[21] = -4.0;
                v[23] = 0.2;
            }),
            0.4,
        );
        let early = goertzel(win(&x, 0.005, 0.025), 220.0);
        let late = goertzel(win(&x, 0.3, 0.32), 220.0);
        assert!(db(early / late) >= 10.0, "hp {} dB", db(early / late));
        let y = render(
            "saw:110",
            &slots(|v| {
                v[2] = 10.0;
                v[8] = 300.0;
                v[9] = 4.0;
                v[26] = 3.0;
                v[28] = 0.2;
            }),
            0.4,
        );
        let early = high_band(win(&y, 0.005, 0.025));
        let late = high_band(win(&y, 0.3, 0.32));
        assert!(db(early / late) >= 10.0, "bp {} dB", db(early / late));
    }

    #[test]
    fn pitch_envelope_falls_an_octave() {
        let x = render(
            "440",
            &slots(|v| {
                v[2] = 10.0;
                v[31] = 12.0;
                v[33] = 0.5;
            }),
            0.7,
        );
        let early = win(&x, 0.0, 0.02);
        let late = win(&x, 0.6, 0.7);
        assert!(db(goertzel(early, 880.0) / goertzel(early, 440.0)) >= 10.0);
        assert!(db(goertzel(late, 440.0) / goertzel(late, 880.0)) >= 10.0);
    }

    /// Instantaneous frequencies from successive interpolated upward zero crossings.
    fn crossing_freqs(x: &[f32]) -> Vec<f32> {
        let mut ts = vec![];
        for i in 1..x.len() {
            if x[i - 1] < 0.0 && x[i] >= 0.0 {
                ts.push((i - 1) as f32 + x[i - 1] / (x[i - 1] - x[i]));
            }
        }
        ts.windows(2).map(|w| SR / (w[1] - w[0])).collect()
    }

    #[test]
    fn vibrato_swings_a_semitone() {
        let x = render(
            "440",
            &slots(|v| {
                v[2] = 10.0;
                v[36] = 5.0;
                v[37] = 1.0;
            }),
            1.0,
        );
        let f = crossing_freqs(&x);
        let lo = f.iter().cloned().fold(f32::MAX, f32::min);
        let hi = f.iter().cloned().fold(f32::MIN, f32::max);
        assert!(lo < 425.0 && hi > 455.0, "range {lo}–{hi} Hz");
        assert!(
            lo > 400.0 && hi < 480.0,
            "no more than ~1 semitone: {lo}–{hi} Hz"
        );
    }

    #[test]
    fn pitch_mods_leave_drums_unchanged() {
        let plain = render("bd", &base_slots(), 0.3);
        let modded = render("bd", &slots(|v| v[36] = 6.0), 0.3);
        assert_eq!(plain, modded);
    }

    #[test]
    fn vowel_a_and_i_differ_at_their_formants() {
        let v = |idx: f32| {
            render(
                "saw:110",
                &slots(|s| {
                    s[2] = 10.0;
                    s[38] = idx;
                }),
                0.5,
            )
        };
        let (a, i) = (v(1.0), v(3.0));
        let (a, i) = (win(&a, 0.1, 0.5), win(&i, 0.1, 0.5));
        // a: F2 1040 Hz (nearest harmonic 1100); i: F1 250 Hz (nearest 220).
        let at = |x: &[f32], f: f32| goertzel(x, f);
        assert!(db(at(a, 1100.0) / at(i, 1100.0)) >= 6.0, "a over i at 1100");
        assert!(db(at(i, 220.0) / at(a, 220.0)) >= 6.0, "i over a at 220");
    }

    #[test]
    fn vowel_i_has_an_audible_second_formant() {
        // i reads as "ee" only with energy at its F2 (1870 Hz = 17th harmonic of
        // A2); the bass table's i (F2 −30 dB) was as dull as u there.
        let v = |idx: f32| {
            render(
                "saw:110",
                &slots(|s| {
                    s[2] = 10.0;
                    s[38] = idx;
                }),
                0.5,
            )
        };
        let (i, u) = (v(3.0), v(5.0));
        let (i, u) = (win(&i, 0.1, 0.5), win(&u, 0.1, 0.5));
        let d = db(goertzel(i, 1870.0) / goertzel(u, 1870.0));
        println!("i over u at 1870 Hz: {d} dB");
        assert!(d >= 12.0, "i over u at 1870 Hz: {d} dB");
    }

    #[test]
    fn coarse_8_aliases_a_1k_sine() {
        let input: Vec<f32> = (0..(0.5 * SR) as usize)
            .map(|i| 0.5 * (std::f32::consts::TAU * 1000.0 * i as f32 / SR).sin())
            .collect();
        let run = |coarse: u32| {
            let t = Tl7cParams {
                coarse,
                ..Tl7cParams::OFF
            };
            let mut ins = Inserts::new(&InsertParams::OFF, &t, SR);
            input.iter().map(|&x| ins.tick(x)).collect::<Vec<f32>>()
        };
        let image = SR / 8.0 - 1000.0;
        let (on, off) = (run(8), run(1));
        assert!(db(goertzel(&on, image) / goertzel(&off, image).max(1e-9)) >= 20.0);
        assert_eq!(off, input, "coarse 1 is bypass");
    }

    #[test]
    fn postgain_half_is_minus_6_db() {
        let plain = render("saw:110", &base_slots(), 0.3);
        let half = render("saw:110", &slots(|v| v[40] = 0.5), 0.3);
        let d = db(rms(&half) / rms(&plain));
        assert!((d + 6.02).abs() <= 0.1, "{d} dB");
    }
}
