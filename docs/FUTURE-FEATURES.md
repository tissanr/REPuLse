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
| Multi-buffer editor | Multiple named code buffers switchable per track |

### Editor
| Feature | Description |
|---|---|
| Signature hints | Tooltip showing argument names and types inside a call form, triggered on open-paren |

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
| FLAC export | WASM FLAC encoder on top of the existing WAV export path |

---

## DAW Concepts (Long Horizon)

These features intentionally bring DAW-style workflows into REPuLse. They are not in
scope for the current live-coding instrument but make sense as REPuLse matures toward
a full production environment. They require the AI3+ agent layer to feel natural.

| Feature | Description |
|---|---|
| Stem export | Render each named track to a separate WAV file simultaneously. Requires per-track `OfflineAudioContext` rendering pipeline. |
| MIDI export | Serialise a pattern's events to a standard `.mid` file. Pure JS — no new dependencies. Useful for re-importing into a DAW for arrangement. |
| Timeline arranger view | Visual drag-and-drop bar grid showing the `arrange` / `play-scenes` structure above the editor. Clicking a bar jumps the eval cursor to that section. Read-only initially; editable in a follow-on phase. |
| Project branching / snapshots | Named session snapshots (not just the D2 single-slot restore). "Save as take A / take B", list, preview each, restore any. Stored in localStorage or Supabase. Complements AI4/AI5 undo. |
| Session comparison | Load two saved snapshots side by side: diff the editor text (unified diff), A/B toggle playback. Useful for comparing AI-generated variations against the original at session granularity rather than per-edit. |
| Loop-region export | Mark a bar range in the timeline view and render only that region to WAV/FLAC. |

---

## REPuLse as a POC: How Music Is Made

REPuLse's pattern-as-pure-function model makes it uniquely suited as a *proof of
concept platform* — a tool that doesn't just make music but explains how music works.
Because every pattern is inspectable Lisp code and every event is a data value with a
known time and source position, REPuLse can show the causal chain from code to sound
in ways a DAW cannot.

### Pattern archaeology

Decompose a complex pattern into the layers of transforms that produce it. A visual
"stack trace" for sound: given `(fast 2 (every 4 rev (euclidean 5 8 :bd)))`, the UI
shows each intermediate stage — what `euclidean 5 8 :bd` produces, what `every 4 rev`
does to it, what `fast 2` does to that — with each stage playable in isolation. Teaches
algorithmic composition by making composition history visible.

| Feature | Description |
|---|---|
| Pattern decomposition view | Step through the transform chain of any expression, hearing and seeing each stage |
| Intermediate event overlay | Highlight which events survive each transform (which were `rev`-ed, which were filtered) |
| "Why does this groove work?" tooltip | On hover over a complex expression, show event density, syncopation score, and Euclidean pattern name if recognised |

### Tension and energy curves

Plot the musical energy of a session over time as a real-time curve above the
arrangement. Energy can be derived from event density, pitch register, FX wetness,
harmonic dissonance, and track count. Composers talk about tension/release constantly
but rarely see it quantified — REPuLse can make it visible while the pattern plays.

| Feature | Description |
|---|---|
| Energy curve visual plugin | Real-time line graph: event density × pitch height × FX wet per cycle |
| Tension score | Numeric 0–1 derived from harmonic interval dissonance in active note patterns |
| Arrangement energy map | Static heatmap over the `arrange` timeline — useful for spotting energy plateaus before the drop |

### Harmonic trace

As note events fire, annotate them with their harmonic context: scale degree, chord
membership, interval from root. Turns an opaque `(scale :minor :c3 (seq 0 3 5 7))` into
a live Roman-numeral analysis. Bridges the gap between algorithmic pattern thinking and
traditional music theory.

| Feature | Description |
|---|---|
| Harmonic trace overlay | Per-event label in the punchcard / piano roll: scale degree (î ĩ iii̊ …), chord symbol |
| Key detection | Infer the current key from active note patterns; surfaced in the context panel |
| Voice-leading warnings | Soft highlight when consecutive events form parallel fifths or dissonant leaps |

### Morphing and interpolation

`(morph t pat-a pat-b)` — continuously interpolate between two patterns as `t` moves
from 0 to 1. At `t=0` you hear pattern A; at `t=1` you hear pattern B; in between,
events are probabilistically blended by proximity. A built-in that makes gradual
transitions first-class — not a DJ crossfader but a compositional operation.

| Feature | Description |
|---|---|
| `morph` built-in | `(morph 0.5 (seq :bd :_ :bd :_) (seq :bd :bd :sd :_))` — probabilistic blending by event proximity |
| Tween morph | `(morph (tween 0 1 8 :linear) pat-a pat-b)` — automated transition over N cycles using the T1 tween infrastructure |
| Morph visualisation | The punchcard timeline shows blended events as partially-opaque rectangles |

### Compositional constraint feedback

Soft, non-blocking feedback that watches the live session and surfaces musical
observations as annotations — not errors. "You have 8 simultaneous voices — the mix
may feel dense." "The bassline hasn't changed for 16 cycles." "This pattern has no
events on beat 3 — try `(off 1/2 identity pat)`." A composition assistant that speaks
in music theory, not code.

| Feature | Description |
|---|---|
| Constraint watcher | Background process watching `scheduler-state`; fires observations as dismissible toasts |
| Voice count warning | Triggers at > 6 simultaneous note events; suggests muting or thinning |
| Repetition detector | Flags tracks that have looped unchanged for > 8 cycles without variation |
| Silence detector | Flags if any active track produces 0 events per cycle (common mistake) |

### Session journal / eval history

Every Ctrl+Enter evaluation is a commit. REPuLse could surface this as a scrollable
timeline of the session — each entry is a snapshot of the buffer at that moment,
with playback of what it sounded like. Lets composers retrace how a piece evolved,
recover discarded ideas, and understand their own creative process. Distinct from
undo: the journal is append-only and never loses history.

| Feature | Description |
|---|---|
| Eval history timeline | Scrollable list of past evaluations; click to preview that snapshot's pattern |
| Snapshot diff | Side-by-side text diff between any two eval snapshots |
| Session replay | Auto-advance through snapshots at real-time intervals to replay how the piece developed |
| Export as story | Render the eval history as an annotated HTML page — the composition journal as a shareable artefact |

### Pedagogical annotation mode

A togglable overlay that explains *why* each expression sounds the way it does in
music-theory terms — not what the function does (hover docs cover that) but what the
musical result means. `(euclidean 5 8 :bd)` → "5 onsets spread across 8 steps: a
common clave-adjacent rhythm used in Afro-Cuban and West African music." Aimed at
learners who want to understand the musical reasoning behind the patterns they hear.

| Feature | Description |
|---|---|
| Music-theory annotation layer | Toggle on/off; annotates recognised patterns with genre/theory context |
| Scale / mode labels | `(scale :dorian :d3 …)` annotated with "D Dorian: minor but with a raised 6th; characteristic of modal jazz" |
| Rhythm pattern database | JSON lookup of euclidean/tidal patterns against known musical names (clave, son, bossa, tresillo …) |
| AI-powered annotation | When the annotation database has no match, the AI3 agent generates a music-theory explanation |
