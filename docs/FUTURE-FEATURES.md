# REPuLse — Future Features

Unscheduled ideas beyond the current roadmap. These are not planned for any specific phase —
they are collected here to capture intent without cluttering the active roadmap.

---

## Visual plugins

| Idea | Description |
|---|---|
| Spectrum analyser | FFT frequency bars from `AnalyserNode.getByteFrequencyData()` |
| Cycle clock | Rotating circle indicating position within the current cycle — a visual metronome |
| Piano roll | 2D grid: time on X, pitch/keyword on Y, events rendered as rectangles |
| Event log | Scrolling list of events as they fire — useful for debugging complex patterns |
| Lissajous scope | X/Y plot of left vs. right channel (stereo) |
| VU meters | Per-slot level meters using `getFloatTimeDomainData` RMS |

---

## Effect plugins

| Idea | Description |
|---|---|
| Chorus / Flanger | Modulated delay lines for classic ensemble effects |
| Bitcrusher | Sample rate and bit depth reduction via `WaveShaperNode` or custom Worklet |
| Ring modulator | Multiply signal by a carrier sine — metallic, robotic textures |
| Parametric EQ | Multi-band `BiquadFilterNode` chain with frequency/gain/Q per band |
| Wavefolder | Soft-clip / fold wave shaping — aggressive harmonic saturation |
| Sidechain compressor | Duck the master volume on kick events (pumping effect) |
| Granular processor | Chop incoming audio into grains and scatter them — AudioWorklet-based |

---

## Integration plugins

| Idea | Type | Description |
|---|---|---|
| MIDI input | `midi` | Receive Note On messages from a controller, map to pattern triggers |
| MIDI clock in | `midi` | Sync REPuLse BPM to an incoming MIDI clock signal |
| MIDI clock out | `midi` | Send MIDI clock messages at the current BPM |
| Ableton Link | `sync` | Tempo sync with other Link-enabled apps via a local WebSocket bridge daemon |
| OSC output | `osc` | Forward pattern events to OSC (via a WebSocket→UDP bridge, e.g. to SuperCollider) |
| CV/Gate | `io` | Send gate/pitch voltages via WebSerial or WebUSB (for modular synths) |

---

## Export / sharing

| Idea | Description |
|---|---|
| Export via OfflineAudioContext | Render a fixed number of cycles faster-than-real-time to a WAV blob |
| FLAC export | Higher quality offline export using a WASM FLAC encoder |
| Session URL | Encode current editor content + BPM as a Base64 URL hash for sharing |
| Community library | Browse and import shared patterns from a hosted repository |
| Gist import | Load a pattern from a GitHub Gist URL with `(load-gist "https://gist.github.com/...")` |

---

## Language features

| Idea | Description |
|---|---|
| Syntax highlighting | CodeMirror language extension for REPuLse-Lisp (bracket matching, keyword colouring) |
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
