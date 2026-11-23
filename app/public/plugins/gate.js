// Noise gate (Phase FX1, D3): opens above `threshold`, holds, then closes to
// `range` (−80 ≈ silent, −12 makes it an expander). Inactive until a parameter
// is set; no latency.
import { makeWorkletEffect } from "./lib/worklet-effect.js";

const make = makeWorkletEffect({
  name: "gate",
  module: "/worklets/gate-processor.js",
  processor: "gate",
  mode: "activate",
  params: {
    value: "threshold", threshold: "number", attack: "number",
    hold: "number", release: "number", range: "number",
  },
  defs: {
    threshold: [-40, -80, 0],
    attack:    [0.001, 0.0001, 0.1],
    hold:      [0.05, 0, 2],
    release:   [0.1, 0.005, 2],
    range:     [-80, -80, 0],
  },
});

export default make();
