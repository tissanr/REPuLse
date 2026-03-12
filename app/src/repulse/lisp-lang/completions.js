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
