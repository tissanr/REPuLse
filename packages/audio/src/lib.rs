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
    b0: f32, b1: f32, b2: f32,
    a1: f32, a2: f32,
    x1: f32, x2: f32,
    y1: f32, y2: f32,
}

impl Biquad {
    fn bandpass(freq: f32, q: f32, sr: f32) -> Self {
        let w0 = 2.0 * std::f32::consts::PI * freq / sr;
        let alpha = w0.sin() / (2.0 * q);
        let cos_w0 = w0.cos();
        let a0 = 1.0 + alpha;
        Biquad {
            b0: alpha / a0, b1: 0.0, b2: -alpha / a0,
            a1: -2.0 * cos_w0 / a0, a2: (1.0 - alpha) / a0,
            x1: 0.0, x2: 0.0, y1: 0.0, y2: 0.0,
        }
    }

    fn highpass(freq: f32, q: f32, sr: f32) -> Self {
        let w0 = 2.0 * std::f32::consts::PI * freq / sr;
        let alpha = w0.sin() / (2.0 * q);
        let cos_w0 = w0.cos();
        let a0 = 1.0 + alpha;
        let b0 = (1.0 + cos_w0) / 2.0;
        Biquad {
            b0: b0 / a0, b1: -(1.0 + cos_w0) / a0, b2: b0 / a0,
            a1: -2.0 * cos_w0 / a0, a2: (1.0 - alpha) / a0,
            x1: 0.0, x2: 0.0, y1: 0.0, y2: 0.0,
        }
    }

    fn tick(&mut self, x: f32) -> f32 {
        let y = self.b0 * x + self.b1 * self.x1 + self.b2 * self.x2
              - self.a1 * self.y1 - self.a2 * self.y2;
        self.x2 = self.x1; self.x1 = x;
        self.y2 = self.y1; self.y1 = y;
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
        amp: f32,       // peak amplitude
        gain: f32,      // current envelope value
        gain_decay: f32,
        attack_inc: f32, // per-sample gain increment during attack (0 = instant)
        in_attack: bool,
    },
}

impl Voice {
    fn tick(&mut self, sr: f32) -> f32 {
        use std::f64::consts::TAU;
        match self {
            Voice::Kick { phase, freq, freq_decay, freq_floor, gain, gain_decay } => {
                *freq = (*freq * *freq_decay).max(*freq_floor);
                let s = (*phase * TAU).sin() as f32;
                *phase += *freq / sr as f64;
                let out = s * *gain;
                *gain *= *gain_decay;
                out
            }
            Voice::Snare { noise_state, bpf, gain, gain_decay, phase, tone_gain, tone_gain_decay } => {
                let body = bpf.tick(lcg_next(noise_state)) * *gain;
                *gain *= *gain_decay;
                let crack = (*phase * TAU).sin() as f32 * *tone_gain * 0.35;
                *phase += 180.0 / sr as f64;
                *tone_gain *= *tone_gain_decay;
                body + crack
            }
            Voice::Hihat { noise_state, hpf, gain, gain_decay } => {
                let out = hpf.tick(lcg_next(noise_state)) * *gain;
                *gain *= *gain_decay;
                out
            }
            Voice::Tone { phase, freq, amp, gain, gain_decay, attack_inc, in_attack } => {
                // Envelope: ramp up during attack, then exponential decay
                if *in_attack {
                    *gain += *attack_inc;
                    if *gain >= *amp { *gain = *amp; *in_attack = false; }
                } else {
                    *gain *= *gain_decay;
                }
                let s = (*phase * TAU).sin() as f32;
                *phase += *freq / sr as f64;
                s * *gain
            }
        }
    }

    fn is_silent(&self) -> bool {
        match self {
            Voice::Tone { gain, in_attack, .. } => !*in_attack && *gain < 1e-5,
            Voice::Kick { gain, .. } | Voice::Snare { gain, .. }
            | Voice::Hihat { gain, .. } => *gain < 1e-5,
        }
    }
}

// ── ActiveVoice — wraps a Voice with its stereo pan position ──────────────

struct ActiveVoice {
    voice: Voice,
    pan:   f32,  // -1.0 (left) … 0.0 (centre) … 1.0 (right)
}

// ── Pending event ──────────────────────────────────────────────────────────

struct Pending {
    time:   f64,
    value:  String,
    amp:    f32,   // 0.0–1.0, default 1.0
    attack: f32,   // seconds, default 0.001
    decay:  f32,   // seconds, default 0.3
    pan:    f32,   // -1.0–1.0, default 0.0
}

// ── AudioEngine ────────────────────────────────────────────────────────────

#[wasm_bindgen]
pub struct AudioEngine {
    sample_rate: f32,
    voices: Vec<ActiveVoice>,
    pending: Vec<Pending>,
    noise_seed: u32,
}

#[wasm_bindgen]
impl AudioEngine {
    #[wasm_bindgen(constructor)]
    pub fn new(sample_rate: f32) -> AudioEngine {
        log(&format!("[REPuLse WASM] PCM engine ready (sr={})", sample_rate));
        AudioEngine { sample_rate, voices: Vec::new(), pending: Vec::new(), noise_seed: 0xDEAD_BEEF }
    }

    /// Schedule a sound event at the given AudioContext time (uses default parameters).
    pub fn trigger(&mut self, value: &str, time: f64) {
        self.pending.push(Pending {
            time, value: value.to_string(),
            amp: 1.0, attack: 0.001, decay: 0.3, pan: 0.0,
        });
    }

    /// Schedule a sound event with explicit synthesis parameters.
    pub fn trigger_v2(&mut self, value: &str, time: f64, amp: f32, attack: f32, decay: f32, pan: f32) {
        self.pending.push(Pending {
            time, value: value.to_string(),
            amp, attack, decay, pan,
        });
    }

    /// Generate one audio quantum. Returns an interleaved stereo Float32Array of
    /// `n_samples * 2` values: [L0, R0, L1, R1, …].
    /// `current_time` = AudioWorkletGlobalScope.currentTime at the block start.
    pub fn process_block(&mut self, n_samples: u32, current_time: f64) -> Float32Array {
        let sr = self.sample_rate;
        let n = n_samples as usize;
        let block_end = current_time + n as f64 / sr as f64;

        // Activate pending events whose scheduled time falls in this block
        let pending = std::mem::take(&mut self.pending);
        let mut deferred = Vec::new();
        for p in pending {
            if p.time < block_end { self.activate(p); }
            else { deferred.push(p); }
        }
        self.pending = deferred;

        // Generate stereo samples (interleaved L R L R …)
        let mut buf = vec![0.0f32; n * 2];
        for i in 0..n {
            let mut l = 0.0f32;
            let mut r = 0.0f32;
            for av in self.voices.iter_mut() {
                let s = av.voice.tick(sr);
                // Constant-power panning
                let angle = (av.pan + 1.0) / 2.0 * std::f32::consts::FRAC_PI_2;
                l += s * angle.cos();
                r += s * angle.sin();
            }
            buf[i * 2]     = l.clamp(-1.0, 1.0);
            buf[i * 2 + 1] = r.clamp(-1.0, 1.0);
        }
        self.voices.retain(|av| !av.voice.is_silent());

        let arr = Float32Array::new_with_length(n_samples * 2);
        arr.copy_from(&buf);
        arr
    }

    pub fn stop_all(&mut self) {
        self.voices.clear();
        self.pending.clear();
    }
}

impl AudioEngine {
    fn next_seed(&mut self) -> u32 {
        self.noise_seed = self.noise_seed
            .wrapping_mul(1_664_525)
            .wrapping_add(1_013_904_223);
        self.noise_seed
    }

    fn activate(&mut self, p: Pending) {
        let sr = self.sample_rate;
        let seed = self.next_seed();
        let amp = p.amp.clamp(0.0, 1.0);
        let voice = match p.value.trim_start_matches(':') {
            "bd" => {
                let sweep_samples = 0.06 * sr as f64;
                Voice::Kick {
                    phase: 0.0, freq: 150.0, freq_floor: 40.0,
                    freq_decay: (40.0_f64 / 150.0).powf(1.0 / sweep_samples),
                    gain: amp, gain_decay: decay_rate(0.4, sr),
                }
            }
            "sd" => Voice::Snare {
                noise_state: seed, bpf: Biquad::bandpass(200.0, 0.7, sr),
                gain: 0.9 * amp, gain_decay: decay_rate(0.2, sr),
                phase: 0.0, tone_gain: amp, tone_gain_decay: decay_rate(0.1, sr),
            },
            "hh" => Voice::Hihat {
                noise_state: seed, hpf: Biquad::highpass(8000.0, 0.7, sr),
                gain: 0.5 * amp, gain_decay: decay_rate(0.045, sr),
            },
            "oh" => Voice::Hihat {
                noise_state: seed, hpf: Biquad::highpass(8000.0, 0.7, sr),
                gain: 0.35 * amp, gain_decay: decay_rate(0.35, sr),
            },
            other => {
                let freq = other.parse::<f64>().unwrap_or(440.0);
                let peak = amp * 0.5;
                let attack_samples = (p.attack * sr).max(1.0);
                Voice::Tone {
                    phase: 0.0, freq,
                    amp: peak,
                    gain: 0.0,
                    gain_decay: decay_rate(p.decay, sr),
                    attack_inc: peak / attack_samples,
                    in_attack: true,
                }
            }
        };
        self.voices.push(ActiveVoice { voice, pan: p.pan.clamp(-1.0, 1.0) });
    }
}
