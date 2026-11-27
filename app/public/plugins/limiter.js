// Look-ahead peak limiter (Phase FX1, D2): no output sample exceeds `ceiling`.
// Delays the signal by 5 ms. Inactive until a parameter is set; on the master:
//   (master (fx :limiter -1))
import { makeWorkletEffect } from "./lib/worklet-effect.js";

const make = makeWorkletEffect({
  name: "limiter",
  module: "/worklets/limiter-processor.js",
  processor: "limiter",
  mode: "activate",
  params: { value: "ceiling", ceiling: "number", release: "number" },
  defs: { ceiling: [-1, -24, 0], release: [0.1, 0.01, 1] },
});

export default make();
