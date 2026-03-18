import { completeFromList } from "@codemirror/autocomplete";

const BUILTINS = [
  // --- Pattern constructors ---
  { label: "seq",          type: "function", detail: "(seq val …) — sequence of values, one per cycle step" },
  { label: "stack",        type: "function", detail: "(stack pat …) — play patterns in parallel" },
  { label: "pure",         type: "function", detail: "(pure val) — a single constant value" },
  // --- Transformations ---
  { label: "fast",         type: "function", detail: "(fast factor pat) — speed up a pattern" },
  { label: "slow",         type: "function", detail: "(slow factor pat) — slow down a pattern" },
  { label: "rev",          type: "function", detail: "(rev pat) — reverse a pattern" },
  { label: "every",        type: "function", detail: "(every n f pat) — apply f every n cycles" },
  { label: "fmap",         type: "function", detail: "(fmap f pat) — map a function over pattern values" },
  // --- Music theory ---
  { label: "scale",        type: "function", detail: "(scale kw root pat) — map degree integers to Hz (e.g. (scale :minor :c4 (seq 0 2 4)))" },
  { label: "chord",        type: "function", detail: "(chord kw root) — stack chord tones as Hz (e.g. (chord :major7 :c4))" },
  { label: "transpose",    type: "function", detail: "(transpose n pat) — shift Hz values by n semitones" },
  // --- Per-event parameters ---
  { label: "->>",          type: "keyword",  detail: "(->> pat (amp 0.8) (attack 0.02)) — thread pattern through transformers (last arg)" },
  { label: "amp",          type: "function", detail: "(amp val pat) — amplitude 0.0–1.0; (amp val) returns transformer" },
  { label: "attack",       type: "function", detail: "(attack secs pat) — envelope attack time in seconds; (attack secs) returns transformer" },
  { label: "decay",        type: "function", detail: "(decay secs pat) — envelope decay time in seconds; (decay secs) returns transformer" },
  { label: "release",      type: "function", detail: "(release secs pat) — envelope release time in seconds; (release secs) returns transformer" },
  { label: "pan",          type: "function", detail: "(pan pos pat) — stereo pan -1.0 (left) to 1.0 (right); (pan pos) returns transformer" },
  // --- Pattern combinators ---
  { label: "euclidean",    type: "function", detail: "(euclidean k n val) — Björklund algorithm: k onsets in n steps; optional rotation (euclidean k n val rot)" },
  { label: "cat",          type: "function", detail: "(cat pat …) — play patterns one per cycle, then loop the sequence" },
  { label: "late",         type: "function", detail: "(late amount pat) — shift events forward by fraction of a cycle" },
  { label: "early",        type: "function", detail: "(early amount pat) — shift events backward by fraction of a cycle" },
  { label: "sometimes",    type: "function", detail: "(sometimes f pat) — apply transform on ~50% of cycles" },
  { label: "often",        type: "function", detail: "(often f pat) — apply transform on ~75% of cycles" },
  { label: "rarely",       type: "function", detail: "(rarely f pat) — apply transform on ~25% of cycles" },
  { label: "sometimes-by", type: "function", detail: "(sometimes-by prob f pat) — apply transform with probability 0.0–1.0" },
  { label: "degrade",      type: "function", detail: "(degrade pat) — randomly drop ~50% of events" },
  { label: "degrade-by",   type: "function", detail: "(degrade-by prob pat) — drop events with probability 0.0–1.0" },
  { label: "choose",       type: "function", detail: "(choose [vals]) — pick one value per cycle (deterministic)" },
  { label: "wchoose",      type: "function", detail: "(wchoose [[weight val] …]) — weighted random choice per cycle" },
  { label: "jux",          type: "function", detail: "(jux f pat) — original panned left + (f pat) panned right" },
  { label: "jux-by",       type: "function", detail: "(jux-by width f pat) — like jux with adjustable stereo width 0.0–1.0" },
  { label: "off",          type: "function", detail: "(off amount f pat) — layer original with time-shifted transformed copy" },
  { label: "comp",         type: "function", detail: "(comp f g …) — compose transformers right-to-left, e.g. (def pluck (comp (amp 0.8) (decay 0.15)))" },
  // --- Sound ---
  { label: "sound",        type: "function", detail: "(sound bank n) — select sample n from bank" },
  { label: "bpm",          type: "function", detail: "(bpm n) — set tempo in beats per minute" },
  { label: "stop",         type: "function", detail: "(stop) — stop all playback" },
  // --- Samples ---
  { label: "samples!",     type: "function", detail: "(samples! url) — load external sample bank" },
  { label: "sample-banks", type: "function", detail: "(sample-banks) — list all registered bank names" },
  // --- Effects ---
  { label: "fx",           type: "function", detail: "(fx :name param val …) — set effect parameters" },
  { label: "load-plugin",  type: "function", detail: "(load-plugin url) — load a REPuLse plugin from URL" },
  // --- Tracks (multi-pattern) ---
  { label: "play",        type: "function", detail: "(play :name pattern) — start or replace a named track (use in editor buffer)" },
  { label: "mute!",       type: "function", detail: "(mute! :name) — silence a track without removing it (use in command bar)" },
  { label: "unmute!",     type: "function", detail: "(unmute! :name) — re-enable a muted track (use in command bar)" },
  { label: "solo!",       type: "function", detail: "(solo! :name) — play only this track, mute all others (use in command bar)" },
  { label: "clear!",      type: "function", detail: "(clear! :name) — remove a track; (clear!) removes all tracks (use in command bar)" },
  { label: "tracks",      type: "function", detail: "(tracks) — list active track names" },
  { label: "upd",         type: "function", detail: "(upd) — hot-swap: re-evaluate editor buffer and update running tracks without stopping" },
  { label: "tap!",        type: "function", detail: "(tap!) — register a BPM tap; 4 taps sets tempo (or click tap button)" },
  { label: "midi-sync!",  type: "function", detail: "(midi-sync! true/false) — enable/disable MIDI clock sync" },
  // --- Onboarding ---
  { label: "demo",        type: "function", detail: "(demo :name) — load a starter template and play it; (demo) lists available demos" },
  { label: "tutorial",    type: "function", detail: "(tutorial n) — load tutorial chapter n into the editor (1–8); (tutorial) loads chapter 1" },
  // --- Arrangement ---
  { label: "arrange",      type: "function", detail: "(arrange [[pat cycles] …]) — sequence patterns by duration" },
  { label: "play-scenes",  type: "function", detail: "(play-scenes [pat …]) — play patterns as 1-cycle scenes" },
  // --- Special forms ---
  { label: "def",          type: "keyword",  detail: "(def name val) — bind a name in the global environment" },
  { label: "let",          type: "keyword",  detail: "(let [name val …] body) — local bindings" },
  { label: "fn",           type: "keyword",  detail: "(fn [params] body) — anonymous function" },
  { label: "lambda",       type: "keyword",  detail: "(lambda [params] body) — anonymous function (alias for fn)" },
  { label: "if",           type: "keyword",  detail: "(if cond then else) — conditional" },
  { label: "do",           type: "keyword",  detail: "(do expr …) — evaluate expressions, return last" },
  // --- Arithmetic ---
  { label: "+",            type: "function", detail: "(+ a b …) — addition" },
  { label: "-",            type: "function", detail: "(- a b …) — subtraction" },
  { label: "*",            type: "function", detail: "(* a b …) — multiplication" },
  { label: "/",            type: "function", detail: "(/ a b …) — division" },
  // --- Comparison ---
  { label: "=",            type: "function", detail: "(= a b …) — equality" },
  { label: "not=",         type: "function", detail: "(not= a b …) — inequality" },
  { label: "<",            type: "function", detail: "(< a b …) — less than" },
  { label: ">",            type: "function", detail: "(> a b …) — greater than" },
  { label: "<=",           type: "function", detail: "(<= a b …) — less than or equal" },
  { label: ">=",           type: "function", detail: "(>= a b …) — greater than or equal" },
  { label: "not",          type: "function", detail: "(not x) — logical negation" },
  // --- Map operations ---
  { label: "get",          type: "function", detail: "(get m k default?) — look up key in map" },
  { label: "assoc",        type: "function", detail: "(assoc m k v) — add or replace key in map" },
  { label: "merge",        type: "function", detail: "(merge m …) — merge maps" },
  { label: "keys",         type: "function", detail: "(keys m) — list of map keys" },
  { label: "vals",         type: "function", detail: "(vals m) — list of map values" },
];

export const builtinCompletions = completeFromList(BUILTINS);
