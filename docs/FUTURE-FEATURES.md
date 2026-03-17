# REPuLse — Future Features

Unscheduled ideas beyond the current roadmap. These are not planned for any specific phase —
they are collected here to capture intent without cluttering the active roadmap.

---

## Visual plugins

| Idea | Description |
|---|---|
| Spectrum analyser | FFT frequency bars from `AnalyserNode.getByteFrequencyData()` |
| Punchcard timeline | Per-track step grid: 16 cells per bar at 16th-note resolution, ● = event ○ = silence. Reads scheduler state (not AnalyserNode). Playhead sweeps via `requestAnimationFrame`. |
| Cycle clock | Rotating circle indicating position within the current cycle — a visual metronome |
| Piano roll | 2D grid: time on X, pitch/keyword on Y, events rendered as rectangles |
| Event log | Scrolling list of events as they fire — useful for debugging complex patterns |
| Lissajous scope | X/Y plot of left vs. right channel (stereo) |
| VU meters | Per-track level meters using `getFloatTimeDomainData` RMS |

---

## Synthesis

| Idea | Description |
|---|---|
| More voice types | Additional WASM oscillator voices: sawtooth, pulse/square, band-limited variants, white/pink noise, FM pair (carrier + modulator with index parameter) |
| Granular synthesis | Split an audio buffer into short overlapping grains; control pitch, density, spread, and position — AudioWorklet-based |
| Custom synth definition | `(defsynth name params body)` — let users define named synthesis graphs in the Lisp layer, similar to Overtone's `defsynth` / `definstrument` |
| Per-voice effects routing | Independent effect chains per track (before the master bus), addressable via `(track-fx :name :reverb 0.4)` — currently all effects are global |
| Sample playback parameters | Per-event control over sample pitch-shift, playback rate, start/end points, and loop mode — currently `(sound :bank n)` uses fixed playback |

---

## Effect plugins

| Idea | Description |
|---|---|
| Ring modulator | Multiply signal by a carrier sine — metallic, robotic textures |
| Parametric EQ | Multi-band `BiquadFilterNode` chain with frequency/gain/Q per band |
| Wavefolder | Soft-clip / fold wave shaping — aggressive harmonic saturation |
| Sidechain compressor | Duck the master volume on kick events (pumping effect) |
| Granular processor | Chop incoming audio into grains and scatter them — AudioWorklet-based |

---

## Integration plugins

| Idea | Type | Description |
|---|---|---|
| MIDI input | `midi` | Receive Note On/Off and CC messages from a controller or keyboard, map to pattern triggers or parameter modulation |
| MIDI clock out | `midi` | Send MIDI clock messages at the current BPM to drive external hardware |
| MIDI note out | `midi` | Route pattern events to hardware synths via MIDI Note On/Off |
| Ableton Link | `sync` | Tempo sync with other Link-enabled apps via a local WebSocket bridge daemon |
| OSC output | `osc` | Forward pattern events to OSC (via a WebSocket→UDP bridge, e.g. to SuperCollider or Ableton) |
| OSC input | `osc` | Receive OSC messages to modulate patterns or parameters in real time |
| Freesound API | `samples` | Search and load samples from freesound.org via `(freesound! "query")` — similar to Overtone's built-in Freesound helpers |
| CV/Gate | `io` | Send gate/pitch voltages via WebSerial or WebUSB (for modular synths) |

---

## Export / sharing

| Idea | Description |
|---|---|
| Export via OfflineAudioContext | Render a fixed number of cycles faster-than-real-time to a WAV blob |
| FLAC export | Higher quality offline export using a WASM FLAC encoder |
| Community library | Browse and import shared patterns from a hosted repository |
| Gist import | Load a pattern from a GitHub Gist URL with `(load-gist "https://gist.github.com/...")` |

---

## Editor features

| Idea | Description |
|---|---|
| Env-aware completions | Drive the completion list directly from the live runtime `env-atom` — plugin-registered built-ins and dynamically added names appear automatically without re-scanning the source text. Requires a small JS/CLJS bridge to expose `env-atom` keys to the CM6 `CompletionSource`. This is the natural upgrade from Phase C's static + def-tracking approach. |
| Signature / parameter hints | Pop-up showing argument names when the cursor is inside a call, e.g. `(fast [factor] [pattern])`. Requires hardcoded signatures per built-in and a custom `ViewPlugin` or tooltip. |

---

## Language features

| Idea | Description |
|---|---|
| Tail-call optimisation | Enable deep recursion without stack overflow — needed for generative patterns |
| Pattern macros | `defmacro`-style compile-time transforms — e.g. `(euclidean 5 8 :bd)` |
| Number notation | Musical shorthand — `1/4` for quarter notes, `120bpm` as a literal |
| Multi-buffer editor | Multiple named code buffers, each associated with a pattern slot |

---

## Collaboration / platform

| Idea | Description |
|---|---|
| Collaborative session | WebRTC peer-to-peer session sharing — real-time co-editing and sync |
| Mobile layout | Touch-optimised editor, large buttons, virtual keyboard for `:bd`/`:sd` etc. |
| Embedded mode | `<repulse-editor>` custom element for embedding in blog posts or docs |
| PWA / offline | Service worker + manifest so REPuLse works offline as an installed app |
| Hardware controller | Map MIDI CC / OSC parameters to `(bpm)`, `(fx :filter ...)` in real time |
