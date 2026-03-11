use js_sys::Float32Array;
use wasm_bindgen::prelude::*;
use web_sys::{
    AudioBuffer, AudioContext, BiquadFilterType, OscillatorType,
};

// ─── Console helper ────────────────────────────────────────────────────────────

#[wasm_bindgen]
extern "C" {
    #[wasm_bindgen(js_namespace = console)]
    fn log(s: &str);
    #[wasm_bindgen(js_namespace = console)]
    fn warn(s: &str);
}

// ─── Noise buffer ───────────────────────────────────────────────────────────────

/// Generate white noise using a simple LCG — no JS Math.random().
fn generate_noise(ctx: &AudioContext, duration_secs: f32) -> Result<AudioBuffer, JsValue> {
    let sample_rate = ctx.sample_rate();
    let length = (sample_rate * duration_secs) as u32;
    let buffer = ctx.create_buffer(1, length, sample_rate)?;

    let channel = buffer.get_channel_data(0)?; // Float32Array view

    let mut state: u32 = 0xDEAD_BEEF;
    for i in 0..length {
        state = state.wrapping_mul(1_664_525).wrapping_add(1_013_904_223);
        let sample = (state as f32 / u32::MAX as f32).mul_add(2.0, -1.0);
        channel.set_index(i, sample);
    }

    Ok(buffer)
}

// ─── AudioEngine ────────────────────────────────────────────────────────────────

#[wasm_bindgen]
pub struct AudioEngine {
    ctx: AudioContext,
    noise_buf: AudioBuffer,
}

#[wasm_bindgen]
impl AudioEngine {
    #[wasm_bindgen(constructor)]
    pub fn new(ctx: AudioContext) -> AudioEngine {
        let noise_buf = generate_noise(&ctx, 2.0).expect("noise buffer creation failed");
        log("[REPuLse WASM] audio engine ready");
        AudioEngine { ctx, noise_buf }
    }

    /// Schedule a sound event.
    /// `value`: ":bd", ":sd", ":hh", ":oh", or a frequency in Hz as a string.
    /// `time`: AudioContext.currentTime to schedule at.
    pub fn trigger(&self, value: &str, time: f64) {
        let v = value.trim_start_matches(':');
        let result = match v {
            "bd" => self.play_kick(time),
            "sd" => self.play_snare(time),
            "hh" => self.play_hihat(time, false),
            "oh" => self.play_hihat(time, true),
            other => {
                let freq = other.parse::<f64>().unwrap_or(440.0);
                self.play_tone(time, freq)
            }
        };
        if let Err(e) = result {
            warn(&format!("[REPuLse WASM] trigger error for '{}': {:?}", value, e));
        }
    }

    pub fn stop_all(&self) {
        // Envelopes are all ≤500ms; scheduled nodes stop themselves.
        // Full stop-tracking would be added in Phase 3 (AudioWorklet).
    }
}

// ─── Private synthesis impl ─────────────────────────────────────────────────────

impl AudioEngine {
    /// Kick drum: sine sweep 150 → 40 Hz with gain decay.
    fn play_kick(&self, t: f64) -> Result<(), JsValue> {
        let ctx = &self.ctx;
        let osc = ctx.create_oscillator()?;
        let gain = ctx.create_gain()?;

        osc.set_type(OscillatorType::Sine);
        osc.frequency().set_value_at_time(150.0, t)?;
        osc.frequency().exponential_ramp_to_value_at_time(40.0, t + 0.06)?;

        gain.gain().set_value_at_time(1.0, t)?;
        gain.gain().exponential_ramp_to_value_at_time(0.001, t + 0.4)?;

        osc.connect_with_audio_node(&gain)?;
        gain.connect_with_audio_node(&ctx.destination())?;
        osc.start_with_when(t)?;
        osc.stop_with_when(t + 0.4)?;

        Ok(())
    }

    /// Snare: bandpass noise (body) + sine tone (crack).
    fn play_snare(&self, t: f64) -> Result<(), JsValue> {
        let ctx = &self.ctx;

        // — noise body —
        let src = ctx.create_buffer_source()?;
        src.set_buffer(Some(&self.noise_buf));
        src.set_loop(true);

        let bpf = ctx.create_biquad_filter()?;
        bpf.set_type(BiquadFilterType::Bandpass);
        bpf.frequency().set_value(200.0);
        bpf.q().set_value(0.7);

        let gain = ctx.create_gain()?;
        gain.gain().set_value_at_time(0.9, t)?;
        gain.gain().exponential_ramp_to_value_at_time(0.001, t + 0.2)?;

        src.connect_with_audio_node(&bpf)?;
        bpf.connect_with_audio_node(&gain)?;
        gain.connect_with_audio_node(&ctx.destination())?;
        src.start_with_when(t)?;
        src.stop_with_when(t + 0.2)?;

        // — sine crack (180 Hz body tone) —
        let tone = ctx.create_oscillator()?;
        tone.set_type(OscillatorType::Sine);
        tone.frequency().set_value(180.0);

        let tgain = ctx.create_gain()?;
        tgain.gain().set_value_at_time(0.35, t)?;
        tgain.gain().exponential_ramp_to_value_at_time(0.001, t + 0.1)?;

        tone.connect_with_audio_node(&tgain)?;
        tgain.connect_with_audio_node(&ctx.destination())?;
        tone.start_with_when(t)?;
        tone.stop_with_when(t + 0.1)?;

        Ok(())
    }

    /// Hi-hat (closed or open): highpass noise, short or long decay.
    fn play_hihat(&self, t: f64, open: bool) -> Result<(), JsValue> {
        let ctx = &self.ctx;
        let decay: f64 = if open { 0.35 } else { 0.045 };
        let vol: f32 = if open { 0.35 } else { 0.5 };

        let src = ctx.create_buffer_source()?;
        src.set_buffer(Some(&self.noise_buf));
        src.set_loop(true);

        let hpf = ctx.create_biquad_filter()?;
        hpf.set_type(BiquadFilterType::Highpass);
        hpf.frequency().set_value(8_000.0);

        let gain = ctx.create_gain()?;
        gain.gain().set_value_at_time(vol, t)?;
        gain.gain().exponential_ramp_to_value_at_time(0.001, t + decay)?;

        src.connect_with_audio_node(&hpf)?;
        hpf.connect_with_audio_node(&gain)?;
        gain.connect_with_audio_node(&ctx.destination())?;
        src.start_with_when(t)?;
        src.stop_with_when(t + decay)?;

        Ok(())
    }

    /// Sine tone at the given frequency (Hz).
    fn play_tone(&self, t: f64, freq: f64) -> Result<(), JsValue> {
        let ctx = &self.ctx;

        let osc = ctx.create_oscillator()?;
        let gain = ctx.create_gain()?;

        osc.set_type(OscillatorType::Sine);
        osc.frequency().set_value(freq as f32);

        gain.gain().set_value_at_time(0.5, t)?;
        gain.gain().exponential_ramp_to_value_at_time(0.001, t + 0.3)?;

        osc.connect_with_audio_node(&gain)?;
        gain.connect_with_audio_node(&ctx.destination())?;
        osc.start_with_when(t)?;
        osc.stop_with_when(t + 0.3)?;

        Ok(())
    }
}
