# REPuLse — Effect Plugins (Phase 6b)

## Context

REPuLse has a plugin system (Phase 6a) with a registry, host API, AnalyserNode tap, and
visual plugin support. This phase adds the second plugin type: **effect plugins** — Web Audio
nodes inserted into the master signal chain that process the audio in real time, addressable
from the Lisp REPL via `(fx :name ...)`.

Current master bus after Phase 6a:
```
WorkletNode ──► masterGain ──► analyser ──► destination
```

---

## Goal for this session

By the end of this session:

1. An **effect plugin interface** is defined (extends the base plugin interface)
2. A **graph manager** handles inserting/bypassing/removing effect nodes cleanly
3. Four **built-in effects** ship with the app: reverb, delay, filter, compressor
4. `(fx :reverb 0.5)` sets the wet mix; `(fx :delay 0.25 0.5)` sets time and feedback
5. `(fx :off :reverb)` bypasses an effect without removing it from the chain
6. Effect state persists across re-evaluations — `(fx ...)` is idempotent for unchanged params
7. All existing patterns play correctly; effects are global on the master bus

---

## Audio graph after this phase

Effects are inserted between `masterGain` and `analyser`:

```
WorkletNode ──► masterGain ──► [reverb] ──► [delay] ──► [filter] ──► [compressor] ──► analyser ──► destination
```

Each effect slot has a **dry/wet structure** for smooth bypass:

```
         ┌──── dry gain ────────────────────────────────┐
input ───┤                                              ├──► output
         └──► wet gain ──► [effect node(s)] ──► wet in ┘
```

This avoids clicks when toggling bypass and allows smooth crossfades.

---

## Effect plugin interface

Effect plugins extend the base plugin system established in Phase 6a. Two authoring styles
are supported — both pass the same `validate!` check in `plugins.cljs`:

**Class style** (preferred for new plugins — import from `plugin-base.js`):

```javascript
import { EffectPlugin } from '/plugin-base.js';

export default class Reverb extends EffectPlugin {
  constructor() { super({ name: "reverb", version: "1.0.0" }); }

  createNodes(ctx) {
    // Build Web Audio sub-graph synchronously.
    // Must return { inputNode: AudioNode, outputNode: AudioNode }.
    return { inputNode: this._input, outputNode: this._out };
  },

  setParam(name, value) { /* update named parameter */ },
  bypass(on)            { /* true → transparent; false → restore wet */ },
  getParams()           { return { wet: this._wet?.gain.value }; },
  destroy()             { this._input?.disconnect(); this._out?.disconnect(); }
}
```

**Plain object style** (used by all built-ins in this phase — zero import dependency):

```javascript
export default {
  type: "effect", name: "reverb", version: "1.0.0",
  init(host)           {},
  createNodes(ctx)     { /* ... */ return { inputNode, outputNode }; },
  setParam(name, value){ /* ... */ },
  bypass(on)           { /* ... */ },
  getParams()          { return { wet: … }; },
  destroy()            { /* disconnect nodes */ },
};
```

The full protocol table (required vs. optional methods, defaults) is in `docs/PLUGINS.md`.

**Required methods** (must be present or `validate!` throws at registration time):
`init`, `createNodes`, `setParam`, `bypass`, `getParams`, `destroy`

**`createNodes` contract:**
- Called synchronously after `register!` → `add-effect!`
- Must return `{ inputNode: AudioNode, outputNode: AudioNode }` immediately
- The graph manager wires `prev.outputNode → inputNode` and `outputNode → next.inputNode`

---

## Graph manager

**New file: `app/src/repulse/fx.cljs`**

Maintains an ordered list of active effects and rewires the chain when effects are added,
removed, or bypassed.

```clojure
(ns repulse.fx
  (:require [repulse.audio :as audio]))

;; Ordered vector of {:name "reverb" :plugin js-obj :input node :output node}
(defonce chain (atom []))

(defn- rewire! []
  "Reconnect the chain: masterGain → effect1 → effect2 → ... → analyser"
  (let [ac      (audio/get-ctx)
        gain    @audio/master-gain
        anl     @audio/analyser-node
        effects @chain]
    ;; Disconnect everything first
    (.disconnect gain)
    (doseq [{:keys [output]} effects]
      (.disconnect output))
    ;; Reconnect in order
    (if (empty? effects)
      (.connect gain anl)
      (do
        (.connect gain (:input (first effects)))
        (doseq [[a b] (partition 2 1 effects)]
          (.connect (:output a) (:input b)))
        (.connect (:output (last effects)) anl)))))

(defn add-effect! [plugin]
  (let [ac    (audio/get-ctx)
        nodes (.createNodes plugin ac)]
    (swap! chain conj {:name   (.-name plugin)
                       :plugin plugin
                       :input  (.-inputNode nodes)
                       :output (.-outputNode nodes)})
    (rewire!)))

(defn remove-effect! [name]
  (when-let [entry (some #(when (= name (:name %)) %) @chain)]
    (.destroy (:plugin entry))
    (swap! chain #(filterv (fn [e] (not= name (:name e))) %))
    (rewire!)))

(defn set-param! [name & params]
  (when-let [entry (some #(when (= name (:name %)) %) @chain)]
    (.setParam (:plugin entry) (first params) (second params))))

(defn bypass! [name enabled]
  (when-let [entry (some #(when (= name (:name %)) %) @chain)]
    (.bypass (:plugin entry) enabled)))
```

---

## `(fx ...)` Lisp built-in

Add to `make-env` in `eval.cljs`:

```clojure
;; (fx :reverb 0.5)           → set reverb wet to 0.5
;; (fx :reverb :wet 0.5 :room 0.8) → set multiple params
;; (fx :off :reverb)          → bypass reverb
;; (fx :remove :reverb)       → remove reverb from chain

"fx" (fn [& args]
       (let [first-arg (first args)]
         (cond
           (= first-arg :off)    (fx/bypass! (name (second args)) true)
           (= first-arg :on)     (fx/bypass! (name (second args)) false)
           (= first-arg :remove) (fx/remove-effect! (name (second args)))
           :else
           ;; (fx :name v) — shorthand for the first/main param
           ;; (fx :name :param v :param v ...) — named params
           (let [effect-name (name first-arg)
                 rest-args   (rest args)]
             (if (keyword? (first rest-args))
               ;; Named params: (:wet 0.5 :room 0.8 ...)
               (doseq [[k v] (partition 2 rest-args)]
                 (fx/set-param! effect-name (name k) v))
               ;; Positional: single value → first param
               (fx/set-param! effect-name "value" (first rest-args))))))
       nil)
```

---

## Built-in effects

Ship four effects as files in `app/public/plugins/`. They are auto-loaded at startup.

### `reverb.js`

Uses a `ConvolverNode` with a procedurally generated impulse response (no IR file needed):

```javascript
function makeImpulseResponse(audioCtx, duration, decay) {
  const sr     = audioCtx.sampleRate;
  const length = sr * duration;
  const buf    = audioCtx.createBuffer(2, length, sr);
  for (let ch = 0; ch < 2; ch++) {
    const d = buf.getChannelData(ch);
    for (let i = 0; i < length; i++)
      d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / length, decay);
  }
  return buf;
}

export default {
  type: "effect", name: "reverb", version: "1.0.0",
  createNodes(ctx) {
    this._dry  = ctx.createGain();  // dry path
    this._wet  = ctx.createGain();  // wet path
    this._conv = ctx.createConvolver();
    this._out  = ctx.createGain();

    this._dry.gain.value  = 0.8;
    this._wet.gain.value  = 0.0;  // off by default
    this._conv.buffer     = makeImpulseResponse(ctx, 2.5, 3.0);

    // Routing: input → dry → out; input → conv → wet → out
    this._input = ctx.createGain();
    this._input.connect(this._dry);
    this._input.connect(this._conv);
    this._conv.connect(this._wet);
    this._dry.connect(this._out);
    this._wet.connect(this._out);

    return { inputNode: this._input, outputNode: this._out };
  },
  setParam(name, value) {
    if (name === "wet"   || name === "value") this._wet.gain.value  = Math.min(1, Math.max(0, value));
    if (name === "dry")                       this._dry.gain.value  = Math.min(1, Math.max(0, value));
  },
  bypass(on) {
    this._wet.gain.value = on ? 0 : (this._savedWet ?? 0.4);
    if (!on) this._savedWet = this._wet.gain.value;
  },
  getParams() { return { wet: this._wet.gain.value, dry: this._dry.gain.value }; },
  destroy()   { this._input.disconnect(); this._out.disconnect(); }
};
```

**Lisp usage:**
```lisp
(fx :reverb 0.4)           ; set wet mix to 0.4
(fx :reverb :wet 0.6 :dry 0.7)
(fx :off :reverb)
```

---

### `delay.js`

```javascript
export default {
  type: "effect", name: "delay", version: "1.0.0",
  createNodes(ctx) {
    this._input    = ctx.createGain();
    this._delay    = ctx.createDelay(2.0);
    this._feedback = ctx.createGain();
    this._wet      = ctx.createGain();
    this._dry      = ctx.createGain();
    this._out      = ctx.createGain();

    this._delay.delayTime.value  = 0.375;  // 3/8 at 120 BPM
    this._feedback.gain.value    = 0.35;
    this._wet.gain.value         = 0.0;
    this._dry.gain.value         = 1.0;

    // Routing
    this._input.connect(this._dry);
    this._input.connect(this._delay);
    this._delay.connect(this._feedback);
    this._feedback.connect(this._delay);  // feedback loop
    this._delay.connect(this._wet);
    this._dry.connect(this._out);
    this._wet.connect(this._out);

    return { inputNode: this._input, outputNode: this._out };
  },
  setParam(name, value) {
    if (name === "time"     || name === "value") this._delay.delayTime.value = value;
    if (name === "feedback")                     this._feedback.gain.value   = Math.min(0.95, value);
    if (name === "wet")                          this._wet.gain.value        = value;
  },
  bypass(on)  { this._wet.gain.value = on ? 0 : 0.4; },
  getParams() { return { time: this._delay.delayTime.value, feedback: this._feedback.gain.value, wet: this._wet.gain.value }; },
  destroy()   { this._input.disconnect(); this._out.disconnect(); }
};
```

**Lisp usage:**
```lisp
(fx :delay 0.375)                    ; delay time in seconds
(fx :delay :time 0.25 :feedback 0.5 :wet 0.3)
```

---

### `filter.js`

```javascript
export default {
  type: "effect", name: "filter", version: "1.0.0",
  createNodes(ctx) {
    this._filter = ctx.createBiquadFilter();
    this._filter.type            = "lowpass";
    this._filter.frequency.value = 20000;  // fully open by default
    this._filter.Q.value         = 1.0;
    return { inputNode: this._filter, outputNode: this._filter };
  },
  setParam(name, value) {
    if (name === "freq"  || name === "value") this._filter.frequency.value = value;
    if (name === "q")                         this._filter.Q.value         = value;
    if (name === "type")                      this._filter.type            = value;
  },
  bypass(on)  { this._filter.frequency.value = on ? 20000 : (this._savedFreq ?? 2000); },
  getParams() { return { freq: this._filter.frequency.value, q: this._filter.Q.value, type: this._filter.type }; },
  destroy()   { this._filter.disconnect(); }
};
```

**Lisp usage:**
```lisp
(fx :filter 800)                       ; lowpass cutoff at 800 Hz
(fx :filter :freq 1200 :q 4)          ; resonant lowpass
(fx :filter :type "highpass" :freq 400)
```

---

### `compressor.js`

```javascript
export default {
  type: "effect", name: "compressor", version: "1.0.0",
  createNodes(ctx) {
    this._comp = ctx.createDynamicsCompressor();
    this._comp.threshold.value = -24;
    this._comp.knee.value      = 10;
    this._comp.ratio.value     = 4;
    this._comp.attack.value    = 0.003;
    this._comp.release.value   = 0.25;
    return { inputNode: this._comp, outputNode: this._comp };
  },
  setParam(name, value) {
    const p = { threshold: "threshold", ratio: "ratio",
                attack: "attack", release: "release", knee: "knee" };
    if (p[name]) this._comp[p[name]].value = value;
  },
  bypass(on)  { /* DynamicsCompressor has no built-in bypass — insert dry/wet wrapper if needed */ },
  getParams() { return { threshold: this._comp.threshold.value, ratio: this._comp.ratio.value }; },
  destroy()   { this._comp.disconnect(); }
};
```

**Lisp usage:**
```lisp
(fx :compressor :threshold -18 :ratio 6)
```

---

## Auto-loading built-in effects at startup

In `app.cljs` `init`, load and register effect plugins:

```clojure
(doseq [url ["/plugins/reverb.js"
             "/plugins/delay.js"
             "/plugins/filter.js"
             "/plugins/compressor.js"]]
  (-> (js/import url)
      (.then (fn [m]
               (let [plugin (.-default m)]
                 (plugins/register! plugin (make-host))
                 (fx/add-effect! plugin))))))
```

Effects are loaded and wired into the chain silently — wet mix starts at 0 for
reverb and delay, so there is no audible change until `(fx ...)` is called.

---

## Repository structure changes

```
app/
├── public/
│   └── plugins/
│       ├── oscilloscope.js      (Phase 6a)
│       ├── reverb.js            NEW
│       ├── delay.js             NEW
│       ├── filter.js            NEW
│       └── compressor.js        NEW
└── src/repulse/
    ├── fx.cljs                  NEW — graph manager
    ├── audio.cljs               updated — rewire worklet to masterGain (Phase 6a)
    ├── app.cljs                 updated — fx auto-load, (fx) built-in wired
    └── plugins.cljs             (Phase 6a)
```

---

## Definition of Done

- [ ] `(fx :reverb 0.4)` — adds perceptible reverb to all sounds
- [ ] `(fx :delay 0.375 :feedback 0.4)` — delay with feedback audible
- [ ] `(fx :filter 600)` — low frequencies pass, highs cut
- [ ] `(fx :off :reverb)` — bypasses reverb with no click
- [ ] `(fx :on :reverb)` — re-enables reverb
- [ ] `(stop)` still stops playback; effects remain in chain
- [ ] Evaluating a new pattern does not reset effect state
- [ ] Browser console shows no audio errors; graph rewires cleanly
- [ ] All core unit tests still pass

---

## What NOT to do in this phase

- No per-pattern effect routing — effects are global on the master bus only
- No effect UI controls (sliders, knobs) — `(fx ...)` is the only interface
- No MIDI, OSC, or recorder plugins
- No changes to the pattern engine, Lisp language, or WASM synthesis
- No `SharedArrayBuffer` or worklet-internal effects
