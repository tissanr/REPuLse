# REPuLse — Future Features

Unplanned feature backlog. See `ROADMAP.md` for planned phases.

---

## Backlog — nice-to-haves

Features that are valuable but not blocking. Pick from here when a tier is done and
there's energy for side quests.

### Additional combinators
| Function | Description |
|---|---|
| `struct` | `(struct (seq true true false true) (pure :bd))` — boolean pattern as rhythmic mask |
| `ply` | `(ply 3 pat)` — subdivide each event into n copies |
| `chunk` | `(chunk 4 rev pat)` — apply transform to rotating quarter of pattern |
| `range` / `segment` | Map 0–1 to numeric range; sample continuous pattern at n points |
| Polymeter | `(polymeter 4 (seq :a :b :c))` — fit n events into m beats |

### Connectivity
| Feature | Description |
|---|---|
| OSC output | `(osc-out "localhost:57120" "/trigger" pat)` via WebSocket→UDP bridge (~30-line Node relay) |
| OSC input | Receive OSC to modulate parameters. Same bridge, reverse direction. |
| Ableton Link | Tempo sync via WebSocket bridge wrapping Link C++ SDK. Breaks zero-install; optional. |
| CV/Gate | WebSerial/WebUSB for modular synths. Chrome only. Niche. |

### Synthesis
| Feature | Description |
|---|---|
| SC-style UGen aliases | Alias existing UGen names to SuperCollider conventions: `sin` → `sin-osc`, `saw` → `saw-osc`, `square` → `pulse`, `tri` → `tri-osc`. Both names coexist — no breaking changes. Trivial: one `assoc` per alias in `make-build-fn`. Valuable for users coming from SC/Overtone. |
| Granular synthesis | AudioWorklet-based grain engine: pitch, density, spread, position |
| Multi-buffer editor | Multiple named code buffers per track |

### Editor
| Feature | Description |
|---|---|
| Env-aware completions | Drive completion list from live `env-atom` instead of static list |
| Signature hints | Tooltip showing argument names inside a call |

### Timing
| Feature | Description |
|---|---|
| Latency monitor | `(latency)` — scheduling stats in context panel (green/yellow/red) |
| Jitter histogram | Visual plugin: per-event timing deviation over last N cycles |

### Visual plugins
| Feature | Description |
|---|---|
| Punchcard timeline | Per-track step grid (16th-note resolution) |
| Cycle clock | Rotating circle — visual metronome |
| Piano roll | 2D grid: time × pitch, events as rectangles |
| Event log | Scrolling list of events as they fire |
| Lissajous scope | X/Y stereo plot |
| VU meters | Per-track RMS level meters |

### Effect plugins
| Feature | Description |
|---|---|
| Ring modulator | Signal × carrier sine — metallic textures |
| Parametric EQ | Multi-band BiquadFilter chain |
| Wavefolder | Soft-clip / fold wave shaping |
| Granular processor | Chop + scatter incoming audio — AudioWorklet |

### Export
| Feature | Description |
|---|---|
| FLAC export | WASM FLAC encoder on top of WAV export |
| Community library | Curated JSON index of shared pattern Gist URLs |
