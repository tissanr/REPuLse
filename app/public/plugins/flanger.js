// Flanger (Phase FX1, D9): a short modulated delay with feedback, in a worklet.
// Dry stays 1; `wet` adds the delayed signal (as in chorus).
import { makeWorkletEffect } from "./lib/worklet-effect.js";

const make = makeWorkletEffect({
  name: "flanger",
  module: "/worklets/flanger-processor.js",
  processor: "flanger",
  mode: "wet",
  wetParam: "wet",
  params: {
    value: "wet", wet: "number", rate: "number", depth: "number",
    delay: "number", feedback: "number",
  },
  defs: {
    rate:     [0.25, 0.01, 10],
    depth:    [0.7, 0, 1],
    delay:    [0.002, 0.0005, 0.010],
    feedback: [0, -0.95, 0.95],
  },
});

export default make();
