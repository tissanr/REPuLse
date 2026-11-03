# REPuLse

[![CI](https://github.com/tissanr/REPuLse/actions/workflows/ci.yml/badge.svg)](https://github.com/tissanr/REPuLse/actions/workflows/ci.yml)
[![Lint](https://github.com/tissanr/REPuLse/actions/workflows/lint.yml/badge.svg)](https://github.com/tissanr/REPuLse/actions/workflows/lint.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
![Node.js 20+](https://img.shields.io/badge/Node.js-20%2B-339933?logo=node.js&logoColor=white)
![ClojureScript](https://img.shields.io/badge/ClojureScript-pattern_engine-5881D8?logo=clojure&logoColor=white)
![Rust + WASM](https://img.shields.io/badge/Rust%20%2B%20WASM-audio_engine-DEA584?logo=rust&logoColor=white)
![Web Audio](https://img.shields.io/badge/Web%20Audio-live_synthesis-FF6F00)
![CodeMirror 6](https://img.shields.io/badge/CodeMirror-6-1F6FEB)
![Status](https://img.shields.io/badge/status-active_development-blue)

> A Lisp that beats.

A browser-based live coding instrument where music is written in a small, purpose-built Lisp.
Think [Strudel](https://strudel.cc), but the user-facing language is a minimal Lisp and the
pattern engine is implemented in ClojureScript.

The name: **REPL** + **pulse** (rhythm, heartbeat).

I love Clojure — its REPL-driven workflow, immutable data model, and the way it makes you think
about time and transformation. REPuLse is what happens when that mindset meets live music: a
sequencer you talk to like a Clojure REPL, where patterns are pure functions and the output is
sound.

```clojure
(bpm 130)

(track :kick  (seq :bd :_ :bd :_))
(track :snare (seq :_ :sd :_ :sd))
(track :hats  (fast 2 (seq :hh :_)))
(track :bass  (->> (scale :minor :c2 (seq 0 :_ 4 :_))
                   (synth :saw)
                   (amp 0.5) (decay 0.3)))
```

---

## What REPuLse is (and isn't)

REPuLse is a **pattern-based** live coding instrument. Patterns describe **what** sounds play
and **when** — not **how** synthesis works internally.

- **Sounds:** sample keywords (`:bd`, `:sd`), note keywords (`:c4`), frequencies (`440`), or
  synth voices (`(synth :saw)`, `(synth :fm :index 4)`, `(synth :guitar)`)
- **Rhythm and structure:** `seq`, `stack`, `fast`, `slow`, `every`, `euclidean`, `struct`,
  `ply`, `chunk`, and Tidal-style mini-notation: `(~ "bd [sd hh] bd sd")`
- **Patterns everywhere:** arguments can themselves be patterns (`(fast "<1 2 4>" pat)`),
  patterns combine arithmetically (`(add 7 pat)`), and continuous signals (`sine`, `rand`,
  `perlin`) modulate parameters (`(pan sine pat)`)
- **Expression:** `amp`, `attack`, `decay`, `pan`, … chained with `->>`
- **Effects:** `(fx :reverb 0.4)`, `(fx :delay :wet 0.3 :time 0.25)`, `(fx :cab :ir :4x12)`
- **Performance:** named tracks you can mute, solo and replace live; MIDI in/out; WAV and
  MIDI export
- **Songs:** `song` and `section` turn the tracks of a buffer into a song with an end;
  `(play-from :chorus)` rehearses from any section and `(export :song)` records the whole song
  to WAV in real time:

  ```clojure
  (song (section :intro  4 [:kick :hats])
        (section :verse  8 [:kick :snare :bass])
        (section :chorus 8 :all)
        (section :outro  4 [:hats])
        :end :stop)
  (play-from :chorus)   ; in the command bar
  ```

REPuLse is **not** a signal-flow language like SuperCollider, Overtone or Extempore. There is
no oscillator routing in pattern code. The one exception is `defsynth`, which defines reusable
instruments from oscillators, filters and envelopes.

---

## Quick start

**Requirements:** Node.js 20+, Java 21+ (for shadow-cljs), and optionally Rust + wasm-pack
for the WASM synthesis engine.

```bash
git clone https://github.com/tissanr/REPuLse.git
cd REPuLse
npm install
npm run build:wasm   # optional, first time only — builds the Rust/WASM synthesis engine
npm run dev          # dev server with hot reload
```

Open <http://localhost:3000>, type this into the editor and press **Alt+Enter**
(**Option+Enter** on macOS):

```clojure
(seq :bd :sd :bd :sd)
```

You hear a kick and a snare alternating in a loop. Evaluate `(stop)` to stop.

Next steps:

- `(tutorial)` — an eight-chapter guided tour, right in the editor
- `(demo :techno)` — load and play a complete track (`(demo)` lists all demos)

> Without `npm run build:wasm` the app still works: it falls back to a JavaScript synthesis
> engine, and the browser console shows `[REPuLse] audio backend: clojurescript synthesis`.

---

## Documentation

| Document | For | Contents |
|---|---|---|
| [docs/USAGE.md](docs/USAGE.md) | Musicians | Complete language and feature reference, with examples |
| [docs/PLUGINS.md](docs/PLUGINS.md) | Plugin authors | Writing visual and effect plugins |
| [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) | Contributors | Development setup, repository layout, adding built-ins, CI |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Contributors | How the layers fit together, audio graph, data flow |
| [docs/TESTPLAN.md](docs/TESTPLAN.md) | Contributors | Test coverage map |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | Maintainers | Deploying to Vercel + Supabase |
| [ROADMAP.md](ROADMAP.md) | Everyone | Delivered and planned phases |

---

## Contributing

Contributions are welcome:

- **Ideas:** open a [GitHub issue](https://github.com/tissanr/REPuLse/issues) describing the
  function or capability you would like. [ROADMAP.md](ROADMAP.md) lists what is already
  planned.
- **Bugs:** open an issue with the code you evaluated, what you expected to hear, and what
  happened instead.
- **Code:** the [PROMPTS/](PROMPTS/) folder contains specs for planned phases. Read
  [docs/CONTRIBUTING.md](docs/CONTRIBUTING.md) for setup and conventions, then open a pull
  request against `dev`.

---

## Browser support

Desktop Chrome, Firefox and Safari. Safari needs the `webkitAudioContext` fallback and an
explicit `.resume()` call — both are handled automatically.
