// hover.js — Hover documentation tooltips for REPuLse-Lisp built-ins
import { hoverTooltip } from "@codemirror/view";
import { syntaxTree } from "@codemirror/language";

/**
 * Documentation map: name → { signature, description, example }.
 * A superset of completions.js — same labels but richer content.
 */
const DOCS = {
  // --- Pattern constructors ---
  "seq": {
    signature: "(seq val ...)",
    description: "Create a sequence of values, each occupying one equal step per cycle.",
    example: "(seq :bd :sd :bd :sd)",
  },
  "stack": {
    signature: "(stack pat ...)",
    description: "Play multiple patterns simultaneously in parallel.",
    example: "(stack (seq :bd :_ :bd :_) (seq :_ :sd :_ :sd))",
  },
  "pure": {
    signature: "(pure val)",
    description: "A constant pattern that plays a single value for the entire cycle.",
    example: "(pure :bd)",
  },

  // --- Transformations ---
  "fast": {
    signature: "(fast factor pat)",
    description: "Speed up a pattern by the given factor. 2 = twice as fast.",
    example: "(fast 2 (seq :bd :sd))",
  },
  "slow": {
    signature: "(slow factor pat)",
    description: "Slow down a pattern by the given factor. 2 = half speed.",
    example: "(slow 2 (seq :bd :sd :hh :oh))",
  },
  "rev": {
    signature: "(rev pat)",
    description: "Reverse the order of events within each cycle.",
    example: "(rev (seq :bd :sd :hh :oh))",
  },
  "every": {
    signature: "(every n transform pat)",
    description: "Apply a transformation every nth cycle. Other cycles play unmodified.",
    example: "(every 4 (fast 2) (seq :bd :sd))",
  },
  "fmap": {
    signature: "(fmap f pat)",
    description: "Map a function over every value in the pattern.",
    example: "(fmap (fn [x] (* x 2)) (seq 1 2 3))",
  },

  // --- Pattern combinators ---
  "euclidean": {
    signature: "(euclidean k n val) or (euclidean k n val rot)",
    description: "Distribute k onsets evenly across n steps using the Björklund algorithm. Optional rot rotates the pattern.",
    example: "(euclidean 5 8 :bd)",
  },
  "cat": {
    signature: "(cat pat ...)",
    description: "Concatenate patterns: each plays for one full cycle in sequence, then loops.",
    example: "(cat (seq :bd :sd) (seq :hh :oh))",
  },
  "late": {
    signature: "(late amount pat)",
    description: "Shift events forward in time by a fraction of a cycle.",
    example: "(late 0.25 (seq :hh :hh :hh :hh))",
  },
  "early": {
    signature: "(early amount pat)",
    description: "Shift events backward in time by a fraction of a cycle.",
    example: "(early 0.125 (seq :sd :_ :sd :_))",
  },
  "sometimes": {
    signature: "(sometimes f pat)",
    description: "Apply transform f on approximately 50% of cycles (deterministic per cycle).",
    example: "(sometimes (fast 2) (seq :bd :sd))",
  },
  "often": {
    signature: "(often f pat)",
    description: "Apply transform f on approximately 75% of cycles.",
    example: "(often rev (seq :bd :sd :hh :oh))",
  },
  "rarely": {
    signature: "(rarely f pat)",
    description: "Apply transform f on approximately 25% of cycles.",
    example: "(rarely (fast 2) (seq :bd :sd))",
  },
  "sometimes-by": {
    signature: "(sometimes-by prob f pat)",
    description: "Apply transform f with probability prob (0.0–1.0) per cycle.",
    example: "(sometimes-by 0.3 rev (seq :bd :sd :hh :oh))",
  },
  "degrade": {
    signature: "(degrade pat)",
    description: "Randomly drop approximately 50% of events per cycle.",
    example: "(degrade (seq :hh :hh :hh :hh))",
  },
  "degrade-by": {
    signature: "(degrade-by prob pat)",
    description: "Drop events with the given probability (0.0 = keep all, 1.0 = drop all).",
    example: "(degrade-by 0.3 (seq :hh :hh :hh :hh))",
  },
  "choose": {
    signature: "(choose [val ...])",
    description: "Pick one value from the vector per cycle. Deterministic: same cycle always picks the same value.",
    example: "(choose [:bd :sd :hh :oh])",
  },
  "wchoose": {
    signature: "(wchoose [[val weight] ...])",
    description: "Weighted random choice: pick one value per cycle, biased by weights.",
    example: "(wchoose [[:bd 3] [:sd 1] [:hh 2]])",
  },
  "jux": {
    signature: "(jux f pat)",
    description: "Juxtapose: play pat panned left, and (f pat) panned right, simultaneously.",
    example: "(jux (fast 2) (seq :bd :sd :hh :oh))",
  },
  "jux-by": {
    signature: "(jux-by width f pat)",
    description: "Like jux with adjustable stereo width (0.0 = mono, 1.0 = hard pan).",
    example: "(jux-by 0.5 rev (seq :bd :sd))",
  },
  "off": {
    signature: "(off amount f pat)",
    description: "Layer the original with a time-shifted transformed copy: (stack pat (late amount (f pat))).",
    example: "(off 0.25 (fast 2) (seq :bd :sd))",
  },

  // --- Music theory ---
  "scale": {
    signature: "(scale scale-kw root pat)",
    description: "Map degree integers (1, 2, 3, ...) to Hz frequencies using a named scale. 1 = root, 2 = second tone, etc. Degrees outside the scale wrap into higher/lower octaves.",
    example: "(scale :minor :c4 (seq 1 3 5 8))",
  },
  "chord": {
    signature: "(chord chord-kw root)",
    description: "Stack the tones of a chord as simultaneous Hz values.",
    example: "(chord :minor7 :a3)",
  },
  "transpose": {
    signature: "(transpose semitones pat)",
    description: "Shift all Hz values in a pattern up or down by n semitones. Note keywords pass through. Non-note keywords (drums, rests) are unchanged.",
    example: "(transpose 7 (scale :major :c4 (seq 0 1 2 3)))",
  },

  // --- Per-event parameters ---
  "->>": {
    signature: "(->> pat (f args) ...)",
    description: "Thread-last: pass the pattern as the last argument of each successive form. The natural way to chain parameter transforms.",
    example: "(->> (seq :c4 :e4) (amp 0.7) (decay 0.5))",
  },
  "tween": {
    signature: "(tween curve start end bars)",
    description: "Interpolate a parameter from start to end over the given duration in bars. The audio engine handles per-sample interpolation — a single message is sent; no polling occurs. After the transition completes, the end value is held. Re-evaluating code restarts the transition.\n\ncurve: :linear — constant rate of change\n       :exp    — slow start, fast end (good for volume fades)\n       :sine   — S-curve: slow at both ends, fast in the middle",
    example: "(->> (seq :c4 :e4 :g4) (synth :saw) (amp (tween :linear 0.0 1.0 2)))",
  },
  "amp": {
    signature: "(amp val pat) or (amp val)",
    description: "Set event amplitude (0.0 = silent, 1.0 = full). One-arg form returns a transformer for use with ->> or comp.",
    example: "(amp 0.8 (seq :c4 :e4 :g4))",
  },
  "attack": {
    signature: "(attack secs pat) or (attack secs)",
    description: "Set envelope attack time in seconds. 0.001 = percussive, 0.3 = slow swell.",
    example: "(attack 0.1 (pure :c4))",
  },
  "decay": {
    signature: "(decay secs pat) or (decay secs)",
    description: "Set envelope decay time in seconds. 0.08 = short stab, 2.0 = long tone.",
    example: "(decay 0.5 (seq :c4 :e4))",
  },
  "release": {
    signature: "(release secs pat) or (release secs)",
    description: "Set envelope release time in seconds. Defaults to decay value when omitted.",
    example: "(release 0.3 (seq :c4 :e4))",
  },
  "pan": {
    signature: "(pan pos pat) or (pan pos)",
    description: "Set stereo panning. -1.0 = hard left, 0.0 = centre, 1.0 = hard right.",
    example: "(pan -0.5 (seq :c4 :e4))",
  },
  "comp": {
    signature: "(comp f g ...)",
    description: "Compose transformers right-to-left. Useful for building named presets.",
    example: "(def pluck (comp (amp 0.8) (attack 0.003) (decay 0.15)))",
  },

  // --- Sound & playback ---
  "bpm": {
    signature: "(bpm n)",
    description: "Set the tempo in beats per minute.",
    example: "(bpm 130)",
  },
  "stop": {
    signature: "(stop)",
    description: "Stop all playback immediately.",
    example: "(stop)",
  },
  "track": {
    signature: "(track :name pattern)",
    description: "Define or replace a named track. Each track runs independently.",
    example: "(track :kick (seq :bd :_ :bd :_))",
  },
  "play": {
    signature: "(play :name pattern)",
    description: "Renamed to track — use (track :name pattern) instead.",
    example: "(track :kick (seq :bd :_ :bd :_))",
  },
  "mute!": {
    signature: "(mute! :name)",
    description: "Silence a track without removing it. Use in the command bar.",
    example: "(mute! :kick)",
  },
  "unmute!": {
    signature: "(unmute! :name)",
    description: "Re-enable a muted track.",
    example: "(unmute! :kick)",
  },
  "solo!": {
    signature: "(solo! :name)",
    description: "Play only this track, muting all others.",
    example: "(solo! :bass)",
  },
  "clear!": {
    signature: "(clear! :name) or (clear!)",
    description: "Remove a track by name, or remove all tracks.",
    example: "(clear! :kick)",
  },
  "tracks": {
    signature: "(tracks)",
    description: "List all currently active track names.",
    example: "(tracks)",
  },
  "upd": {
    signature: "(upd)",
    description: "Hot-swap: re-evaluate the editor buffer and update running tracks without stopping playback.",
    example: "(upd)",
  },
  "tap!": {
    signature: "(tap!)",
    description: "Register a BPM tap. Four consecutive taps set the tempo.",
    example: "(tap!)",
  },
  "midi-sync!": {
    signature: "(midi-sync! true/false)",
    description: "Enable or disable MIDI clock synchronisation.",
    example: "(midi-sync! true)",
  },

  // --- MIDI output ---
  "midi-map": {
    signature: "(midi-map :cc N :target)",
    description: "Map MIDI CC number N from any connected controller to a named parameter. Supported targets: :filter (master lowpass cutoff), :amp (master gain), :bpm (tempo 60–240). Requires Chrome or Edge.",
    example: "(midi-map :cc 1 :filter)",
  },
  "midi-out": {
    signature: "(midi-out ch pat) or (midi-out ch)",
    description: "Route pattern events as MIDI Note On/Off messages on the given channel (1–16). Notes are derived from Hz values; amp maps to MIDI velocity. Combine with scale, chord, transpose. Requires Chrome or Edge.",
    example: "(->> (scale :minor :c4 (seq 0 2 4 7)) (midi-out 1))",
  },
  "midi-clock-out!": {
    signature: "(midi-clock-out! true/false)",
    description: "Start or stop broadcasting a 24ppqn MIDI clock at the current BPM. Sends MIDI Start (0xFA) when enabled, MIDI Stop (0xFC) when disabled. Locks external DAWs and hardware to REPuLse's tempo. Requires Chrome or Edge.",
    example: "(midi-clock-out! true)",
  },
  "midi-export": {
    signature: "(midi-export :track n)",
    description: "Export n cycles of the named track as a Standard MIDI File (.mid) and trigger a browser download. The file contains correct pitch, timing, duration, and tempo. Works in all browsers — no MIDI hardware required.",
    example: "(midi-export :bass 4)",
  },

  // --- Freesound ---
  "freesound-key!": {
    signature: '(freesound-key! "api-key")',
    description: "Set your Freesound API key. Required before using freesound!. Get a free key at freesound.org.",
    example: '(freesound-key! "abc123xyz")',
  },
  "freesound!": {
    signature: '(freesound! "query")',
    description: "Search Freesound.org and load up to 5 results as sample banks named :freesound-ID. Use them in patterns like any other keyword.",
    example: '(do (freesound! "kick 808") (seq :freesound-12345))',
  },

  // --- Samples ---
  "samples!": {
    signature: '(samples! "github:owner/repo")',
    description: "Load an external sample bank from a GitHub repository.",
    example: '(samples! "github:tidalcycles/Dirt-Samples")',
  },
  "sample-banks": {
    signature: "(sample-banks)",
    description: "List all registered sample bank names.",
    example: "(sample-banks)",
  },
  "bank": {
    signature: "(bank :prefix)",
    description: "Set a default bank prefix for all subsequent keyword lookups.",
    example: "(bank :AkaiLinn)",
  },
  "sound": {
    signature: "(sound bank n)",
    description: "Select sample number n from the named bank.",
    example: "(sound :808 0)",
  },

  // --- Effects ---
  "fx": {
    signature: "(fx :name param) or (->> pat (fx :name param))",
    description: "Global: sets a master-chain effect parameter. Per-track: use inside ->> to route a track through its own effect chain — re-evaluating play without (fx ...) removes the effect automatically. Use (fx :off :name) / (fx :on :name) to bypass/restore global effects.",
    example: "(->> (seq :bd :_ :bd :_) (fx :filter 1000))",
  },
  "distort": {
    signature: "(fx :distort [:drive N] [:tone Hz] [:mix N] [:asym N] [:algo kw] [:oversample 1|2|4])",
    description: "Soft-clipping waveshaper distortion. :drive sets pre-clip gain from 1.0–100.0 (default 4.0). :tone sets the post-clip lowpass from 200–20000 Hz (default 3000). :mix blends dry/wet from 0.0–1.0 (default 1.0). :asym sets half-wave asymmetry from -1.0–1.0 for even-harmonic warmth (default 0.0). :algo chooses :tanh, :sigmoid, or :atan clipping curves. :oversample reduces aliasing at high drive (default 1).",
    example: "(->> (seq :c2 :g2 :c3 :g2) (synth :saw) (fx :distort :drive 25 :oversample 4))",
  },
  "amp-sim": {
    signature: "(fx :amp-sim [:gain N] [:stages N] [:tone Hz] [:tonestack kw] [:sag N] [:mix N] [:oversample 1|2|4])",
    description: "Multi-stage tube preamp simulation. :gain sets total preamp gain from 1.0–100.0 (default 8.0). :stages sets number of gain stages from 1–4 (default 3). :tone sets post-amp lowpass Hz (default 4000). :tonestack chooses :neutral, :bright, :dark, :mid-scoop, or :mid-hump presets. :sag adds power supply sag / transient compression from 0.0–1.0 (default 0.0). :oversample reduces aliasing (default 1).",
    example: "(->> (seq :e2 :_ :e2 :g2) (synth :saw) (fx :amp-sim :gain 40 :stages 2 :oversample 2))",
  },
  "waveshape": {
    signature: "(fx :waveshape :curve C [:drive N] [:tone Hz] [:mix N])",
    description: "Arbitrary waveshaper distortion via a user-defined transfer function. :curve accepts a Float32Array, a vector of floats, or a generator function. :drive sets pre-shaper gain from 1.0–20.0 (default 1.0). :tone sets the post-shaper lowpass cutoff (default 20000).",
    example: "(fx :waveshape :curve (chebyshev 3) :drive 2)",
  },
  "chebyshev": {
    signature: "(chebyshev N)",
    description: "Generate a Chebyshev polynomial curve of order N (1–8) for use with (fx :waveshape). Order N adds primarily the Nth harmonic. Use as a :curve value.",
    example: "(fx :waveshape :curve (chebyshev 2) :drive 4)",
  },
  "fold": {
    signature: "(fold)",
    description: "Generate a wavefolder transfer function for use with (fx :waveshape). Folds the waveform back on itself, producing rich harmonics.",
    example: "(fx :waveshape :curve (fold) :drive 8)",
  },
  "bitcrush": {
    signature: "(bitcrush N)",
    description: "Generate a quantization staircase curve with 2^N steps for use with (fx :waveshape). N is bit depth (1–16). Low N = aggressive quantization = lo-fi grit.",
    example: "(fx :waveshape :curve (bitcrush 4))",
  },
  "load-plugin": {
    signature: '(load-plugin "url")',
    description: "Load a REPuLse plugin from a URL (visual or effect).",
    example: '(load-plugin "/plugins/reverb.js")',
  },

  // --- Arrangement ---
  "arrange": {
    signature: "(arrange [[pat cycles] ...])",
    description: "Sequence patterns by duration. Each [pattern cycles] pair plays for the given number of cycles.",
    example: "(arrange [[(seq :bd :sd) 4] [(seq :hh :oh) 2]])",
  },
  "play-scenes": {
    signature: "(play-scenes [pat ...])",
    description: "Play patterns as sequential 1-cycle scenes.",
    example: "(play-scenes [(seq :bd :sd) (seq :hh :oh)])",
  },

  // --- Special forms ---
  "def": {
    signature: "(def name value)",
    description: "Bind a name in the global environment. The name persists across evaluations.",
    example: "(def kick (seq :bd :_ :bd :_))",
  },
  "let": {
    signature: "(let [name val ...] body)",
    description: "Create local bindings. Names are only visible inside the let body.",
    example: "(let [x (seq :bd :sd)] (fast 2 x))",
  },
  "fn": {
    signature: "(fn [params ...] body)",
    description: "Create an anonymous function.",
    example: "(fn [p] (fast 2 p))",
  },
  "lambda": {
    signature: "(lambda [params ...] body)",
    description: "Create an anonymous function (alias for fn).",
    example: "(lambda [p] (fast 2 p))",
  },
  "if": {
    signature: "(if condition then else)",
    description: "Conditional expression. Evaluates then if condition is truthy, else otherwise.",
    example: "(if (> x 0) :bd :sd)",
  },
  "do": {
    signature: "(do expr ...)",
    description: "Evaluate expressions in sequence, return the value of the last one.",
    example: "(do (def x 1) (+ x 2))",
  },

  // --- Demo & Tutorial ---
  "demo": {
    signature: "(demo :name) or (demo)",
    description: "Load a starter template into the editor and play it. With no arguments, lists available demos.",
    example: "(demo :techno)",
  },
  "tutorial": {
    signature: "(tutorial) or (tutorial n)",
    description: "Load tutorial chapter n into the editor without auto-playing. Defaults to chapter 1. Press Alt+Enter to hear it.",
    example: "(tutorial 3)",
  },
  "reset!": {
    signature: "(reset!)",
    description: "Stop playback, clear all persisted state (editor, BPM, effects, bank, sample sources, mute/solo, MIDI mappings), and reload the page with a fresh demo. Use when you want a clean slate.",
    example: "(reset!)",
  },

  // --- General envelopes ---
  "env": {
    signature: "(env levels times) or (env levels times curves)",
    description: "Construct a general envelope descriptor from breakpoint levels and segment durations. Pass to env-gen inside a defsynth body. Supported curves: :lin :exp :sin :welch :step or a positive number.",
    example: "(env [0 1 0.3 0] [0.01 0.1 0.5] [:lin :exp :exp])",
  },
  "env-gen": {
    signature: "(env-gen env-data signal)",
    description: "Apply a general envelope (created with env) to a UGen signal. Returns the enveloped signal with duration equal to the total envelope time.",
    example: "(-> (sin freq) (env-gen (env [0 1 0] [0.01 0.5] [:lin :exp])))",
  },

  // --- Snippet library ---
  "snippet": {
    signature: "(snippet :id)",
    description: "Insert a snippet from the curated library by ID. The snippet code is appended to the editor and the session is hot-swapped with (upd). Call (snippet) with no args to list all available IDs.",
    example: "(snippet :four-on-the-floor)",
  },

  // --- Bus system ---
  "bus": {
    signature: "(bus :name) or (bus :name :control|:audio)",
    description: "Declare a named bus. :control (default) uses a ConstantSourceNode — ideal for LFOs and modulation signals. :audio uses a GainNode for mixing audio-rate signals.",
    example: "(bus :lfo :control)",
  },
  "out": {
    signature: "(out :bus-name signal)",
    description: "Write a UGen signal to a named bus. Use inside defsynth bodies. Re-triggers replace the previous connection for the same synth→bus pair.",
    example: "(out :lfo (sin 4))",
  },
  "in": {
    signature: "(in :bus-name)",
    description: "Read from a named bus as a UGen audio source. Returns silence and logs a warning if the bus does not exist.",
    example: "(lpf (* 2000 (in :lfo)) (saw freq))",
  },
  "kr": {
    signature: "(kr rate signal)",
    description: "Control-rate pass-through. In Web Audio, control-rate is not separately exposed — this is an informational wrapper that returns the signal unchanged.",
    example: "(kr 100 (in :lfo))",
  },
};

/**
 * Find a BuiltinName or Symbol node at the given position.
 * Returns { from, to, word } or null.
 */
function wordAt(state, pos) {
  const tree = syntaxTree(state);
  let node = tree.resolveInner(pos, 0);
  while (node) {
    if (node.name === "BuiltinName" || node.name === "Symbol") {
      const word = state.sliceDoc(node.from, node.to);
      return { from: node.from, to: node.to, word };
    }
    if (node.name === "List" || node.name === "Vector" || node.name === "Program") break;
    node = node.parent;
  }
  return null;
}

/**
 * CM6 hoverTooltip provider for REPuLse-Lisp built-ins.
 */
export const lispHoverTooltip = hoverTooltip((view, pos) => {
  const hit = wordAt(view.state, pos);
  if (!hit) return null;

  const doc = DOCS[hit.word];
  if (!doc) return null;

  return {
    pos: hit.from,
    end: hit.to,
    above: true,
    create() {
      const container = document.createElement("div");
      container.className = "repulse-hover-doc";

      const sig = document.createElement("div");
      sig.className = "repulse-hover-sig";
      sig.textContent = doc.signature;
      container.appendChild(sig);

      const desc = document.createElement("div");
      desc.className = "repulse-hover-desc";
      desc.textContent = doc.description;
      container.appendChild(desc);

      if (doc.example) {
        const ex = document.createElement("div");
        ex.className = "repulse-hover-example";
        ex.textContent = doc.example;
        container.appendChild(ex);
      }

      return { dom: container };
    },
  };
});
