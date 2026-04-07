# Phase DST3 — Multi-Stage Amp Simulation

## Goal

Add `(fx :amp-sim ...)` — a multi-stage gain structure that models cascaded tube
preamp stages, producing complex harmonic saturation from clean crunch to high-gain.

```lisp
;; Classic crunch:
(->> (seq :e2 :_ :e2 :g2)
     (synth :saw)
     (fx :amp-sim :gain 6 :stages 2 :tonestack :bright))

;; High-gain metal:
(->> (seq :c1 :_ :c1 :_ :db1 :_ :c1 :_)
     (synth :square)
     (fx :amp-sim :gain 80 :stages 4 :tonestack :mid-scoop :sag 0.3))

;; Bluesy warmth:
(->> (seq :a2 :c3 :e3 :a3)
     (synth :saw)
     (fx :amp-sim :gain 4 :stages 2 :tonestack :mid-hump :sag 0.5))

;; Stack after distort:
(->> (seq :c2 :eb2 :g2 :c3)
     (synth :saw)
     (fx :amp-sim)
     (fx :cab :ir :4x12))   ; pairs naturally with Phase DST6
```

---

## Background

### Depends on Phase DST2

The `:amp-sim` waveshaping stages reuse the same asymmetric soft-clip approach
established in DST2. Read `distort.js` before implementing `amp-sim.js`; the
`makeStageCurve` helper below is analogous to DST2's `makeCurve`.

### Why cascaded stages

A single soft-clip stage produces one set of harmonic ratios. Cascading N stages with
inter-stage filters produces progressively richer, more complex distortion — each stage
clips the already-clipped signal, emphasising higher-order harmonics and adding the
progressive high-frequency rolloff characteristic of real tube amplifiers (each tube
stage has limited bandwidth). This is the key quality difference between `:distort`
(one stage) and `:amp-sim` (N stages with inter-stage filtering).

### Power supply sag

In real tube amplifiers, the power supply voltage sags under heavy load — typically on
loud transients. Sag compresses transients and then lets them recover slowly, creating a
"spongy" feel. We model this with an envelope follower that temporarily reduces the
effective input gain when sag > 0.

---

## Implementation

### `app/public/plugins/amp-sim.js`

The plugin is implemented entirely in Web Audio JS. No WASM changes.

```javascript
// ── Curve helpers (adapted from distort.js DST2) ─────────────────────────

// Per-stage: mild fixed asymmetry (0.2) adds tube character without being too aggressive
const STAGE_ASYM = 0.2;

function makeStageCurve(stageGain) {
  const N = 512;
  const curve = new Float32Array(N);
  const comp = 1 / Math.sqrt(Math.max(1, stageGain));
  for (let i = 0; i < N; i++) {
    const x = (i * 2) / (N - 1) - 1;
    const k = x >= 0
      ? stageGain * (1 + STAGE_ASYM)
      : stageGain * (1 - STAGE_ASYM);
    curve[i] = Math.tanh(x * Math.max(0.01, k)) * comp;
  }
  return curve;
}

// ── Tone stack presets ────────────────────────────────────────────────────
// Each preset is { lowGainDb, midGainDb, highGainDb }
const TONESTACKS = {
  neutral:    { low:  0, mid:  0, high:  0 },
  bright:     { low: -2, mid:  0, high: +4 },
  dark:       { low: +2, mid:  0, high: -6 },
  "mid-scoop":{ low: +3, mid: -6, high: +3 },
  "mid-hump": { low: -2, mid: +4, high: -2 },
};

function dbToGain(db) { return Math.pow(10, db / 20); }

// ── Plugin ────────────────────────────────────────────────────────────────

export default {
  type: "effect", name: "amp-sim", version: "1.0.0",

  _gain: 8.0,
  _stages: 3,
  _tone: 4000,
  _tonestack: "neutral",
  _sag: 0.0,
  _mix: 1.0,

  createNodes(ctx) {
    this._ctx    = ctx;
    this._input  = ctx.createGain();
    this._out    = ctx.createGain();
    this._dryGain = ctx.createGain();
    this._wetGain = ctx.createGain();
    this._dryGain.gain.value = 0.0;   // full wet by default (mix=1)
    this._wetGain.gain.value = 1.0;

    // DC blocker (same as DST2 — always present)
    this._dcBlock = ctx.createIIRFilter([1, -1], [1, -0.9995]);

    // Final tone lowpass
    this._toneLP = ctx.createBiquadFilter();
    this._toneLP.type = "lowpass";
    this._toneLP.frequency.value = this._tone;
    this._toneLP.Q.value = 0.7;

    // Tone stack (3-band EQ: low shelf, parametric mid, high shelf)
    this._lowShelf  = ctx.createBiquadFilter();
    this._midPeak   = ctx.createBiquadFilter();
    this._highShelf = ctx.createBiquadFilter();
    this._lowShelf.type  = "lowshelf";
    this._lowShelf.frequency.value  = 250;
    this._midPeak.type   = "peaking";
    this._midPeak.frequency.value   = 1000;
    this._midPeak.Q.value           = 1.0;
    this._highShelf.type = "highshelf";
    this._highShelf.frequency.value = 4000;
    this._applyTonestack(this._tonestack);

    // Build the gain stage chain
    this._stageChain = [];
    this._buildStages(ctx);

    // Routing:
    //   input → dry → out
    //   input → stages → dcBlock → toneLP → lowShelf → midPeak → highShelf → wet → out
    this._input.connect(this._dryGain);
    this._dryGain.connect(this._out);

    const stageIn = this._stageChain[0].inputGain;
    const stageOut = this._stageChain[this._stageChain.length - 1].outputGain;
    this._input.connect(stageIn);
    stageOut.connect(this._dcBlock);
    this._dcBlock.connect(this._toneLP);
    this._toneLP.connect(this._lowShelf);
    this._lowShelf.connect(this._midPeak);
    this._midPeak.connect(this._highShelf);
    this._highShelf.connect(this._wetGain);
    this._wetGain.connect(this._out);

    return { inputNode: this._input, outputNode: this._out };
  },

  _buildStages(ctx) {
    // Tear down any existing stages
    for (const s of this._stageChain) {
      try { s.inputGain.disconnect(); } catch (_) {}
      try { s.shaper.disconnect();    } catch (_) {}
      try { s.hpFilter.disconnect();  } catch (_) {}
      try { s.lpFilter.disconnect();  } catch (_) {}
      try { s.outputGain.disconnect(); } catch (_) {}
    }
    this._stageChain = [];

    const N = Math.max(1, Math.min(4, this._stages));
    // Distribute total gain evenly in log space: per_stage = gain^(1/N)
    const perStageGain = Math.pow(this._gain, 1 / N);

    // Inter-stage LP cutoffs: 12kHz / 8kHz / 5kHz / 4kHz
    const stageLPFreqs = [12000, 8000, 5000, 4000];

    for (let i = 0; i < N; i++) {
      const inputGain  = ctx.createGain();
      inputGain.gain.value = perStageGain;

      const shaper = ctx.createWaveShaper();
      shaper.curve = makeStageCurve(perStageGain);
      shaper.oversample = "2x";

      // Inter-stage highpass at 80 Hz removes DC/sub buildup
      const hpFilter = ctx.createBiquadFilter();
      hpFilter.type = "highpass";
      hpFilter.frequency.value = 80;
      hpFilter.Q.value = 0.7;

      // Inter-stage lowpass: progressively darker per stage
      const lpFilter = ctx.createBiquadFilter();
      lpFilter.type = "lowpass";
      lpFilter.frequency.value = stageLPFreqs[i] ?? 4000;
      lpFilter.Q.value = 0.7;

      const outputGain = ctx.createGain();
      outputGain.gain.value = 1.0;

      // Stage routing: inputGain → shaper → hpFilter → lpFilter → outputGain
      inputGain.connect(shaper);
      shaper.connect(hpFilter);
      hpFilter.connect(lpFilter);
      lpFilter.connect(outputGain);

      this._stageChain.push({ inputGain, shaper, hpFilter, lpFilter, outputGain });

      // Chain stage to next
      if (i > 0) {
        this._stageChain[i - 1].outputGain.connect(inputGain);
      }
    }

    // Reconnect stage chain to the rest of the graph
    // (caller must reconnect _input → stageChain[0].inputGain and
    //  stageChain[last].outputGain → _dcBlock)
  },

  // NOTE: _buildStages tears down and rebuilds all stage nodes.
  // After calling it during a live param change, reconnect:
  //   this._input → this._stageChain[0].inputGain (via disconnect/reconnect)
  //   last stage outputGain → this._dcBlock
  // See _rebuildAndRewire() below.

  _rebuildAndRewire() {
    // Disconnect input from old stage chain
    try { this._input.disconnect(this._stageChain[0]?.inputGain); } catch (_) {}

    this._buildStages(this._ctx);

    const stageIn  = this._stageChain[0].inputGain;
    const stageOut = this._stageChain[this._stageChain.length - 1].outputGain;
    this._input.connect(stageIn);
    stageOut.connect(this._dcBlock);
  },

  _applyTonestack(name) {
    const ts = TONESTACKS[name] ?? TONESTACKS.neutral;
    const now = this._ctx?.currentTime ?? 0;
    this._lowShelf.gain.linearRampToValueAtTime(dbToGain(ts.low),  now + 0.02);
    this._midPeak.gain.linearRampToValueAtTime( dbToGain(ts.mid),  now + 0.02);
    this._highShelf.gain.linearRampToValueAtTime(dbToGain(ts.high), now + 0.02);
  },

  setParam(name, value) {
    const now = this._ctx?.currentTime ?? 0;

    if (name === "gain") {
      this._gain = Math.max(1.0, Math.min(100.0, value));
      this._rebuildAndRewire();
    }
    if (name === "stages") {
      this._stages = Math.max(1, Math.min(4, Math.round(value)));
      this._rebuildAndRewire();
    }
    if (name === "tone") {
      this._tone = Math.max(200, Math.min(20000, value));
      this._toneLP.frequency.linearRampToValueAtTime(this._tone, now + 0.02);
    }
    if (name === "tonestack") {
      const ts = (typeof value === "string" ? value : String(value)).replace(/^:/, "");
      if (!TONESTACKS[ts]) {
        console.warn(`[amp-sim] unknown tonestack "${ts}", using neutral`);
        this._tonestack = "neutral";
      } else {
        this._tonestack = ts;
      }
      this._applyTonestack(this._tonestack);
    }
    if (name === "sag") {
      this._sag = Math.max(0, Math.min(1.0, value));
      // Sag is applied per-audio-frame via _sagEnvelope (see note below)
    }
    if (name === "mix") {
      this._mix = Math.max(0, Math.min(1, value));
      this._dryGain.gain.linearRampToValueAtTime(1 - this._mix, now + 0.02);
      this._wetGain.gain.linearRampToValueAtTime(this._mix,     now + 0.02);
    }
  },

  bypass(on) {
    const now = this._ctx?.currentTime ?? 0;
    this._wetGain.gain.linearRampToValueAtTime(on ? 0 : this._mix,       now + 0.02);
    this._dryGain.gain.linearRampToValueAtTime(on ? 1 : (1 - this._mix), now + 0.02);
  },

  getParams() {
    return {
      gain: this._gain, stages: this._stages, tone: this._tone,
      tonestack: this._tonestack, sag: this._sag, mix: this._mix,
    };
  },

  destroy() {
    for (const s of this._stageChain) {
      try { s.inputGain.disconnect(); } catch (_) {}
    }
    try { this._input.disconnect(); } catch (_) {}
    try { this._out.disconnect();   } catch (_) {}
  },
};
```

### Sag implementation

Power supply sag requires a per-sample envelope follower — this is not directly possible
with Web Audio built-in nodes. Two implementation options:

**Option A (recommended — ScriptProcessor or AudioWorkletProcessor):**

Create a dedicated sag worklet at `app/public/worklets/sag-processor.js`. The worklet
holds state for attack/release envelope following and outputs a control signal. The amp-sim
plugin instantiates this worklet when `sag > 0` and feeds its output to the gain stage
`AudioParam`. This is the correct approach but requires an async worklet load.

Use the same async pattern as `bitcrusher.js` (from Phase A): add the effect with a dry
pass-through immediately in `createNodes`, then connect the sag worklet in `init()` after
`ctx.audioWorklet.addModule(...)` resolves.

**Option B (approximation — no worklet):**

Skip the sag worklet. Instead, implement sag as a `DynamicsCompressorNode` inserted
before the first stage. Configure it based on `_sag`:

```javascript
// Sag approximation via compressor
this._sagComp = ctx.createDynamicsCompressor();
this._sagComp.threshold.value = -12;
this._sagComp.knee.value = 6;
this._sagComp.ratio.value = 2 + this._sag * 10;  // sag=0 → 2:1, sag=1 → 12:1
this._sagComp.attack.value  = 0.010;  // 10ms
this._sagComp.release.value = 0.100;  // 100ms
```

Insert between `_input` and the first stage. When `sag == 0`, bypass the compressor
(set ratio to 1:1 or disconnect).

**Option B is acceptable for this phase.** Document that it is an approximation;
Option A (true envelope follower) can be added in a future phase.

### Register in `app/src/repulse/app.cljs`

Add `"/plugins/amp-sim.js"` to the auto-load list:

```clojure
(doseq [url [...
             "/plugins/distort.js"
             "/plugins/amp-sim.js"]]   ; ← add this
  ...)
```

### Grammar and completions

Add `"amp-sim"` to `BuiltinName` in the grammar. Run `npm run gen:grammar`.

Add to `completions.js`:

```javascript
{ label: "amp-sim", type: "keyword", detail: "effect — multi-stage amp simulation" },
```

### Hover docs

```
"amp-sim": `(fx :amp-sim [:gain N] [:stages N] [:tone Hz] [:tonestack kw] [:sag N] [:mix N])
Multi-stage tube preamp simulation.
  :gain       1–100    total preamp gain (default 8.0)
  :stages     1–4      number of gain stages (default 3)
  :tone       200–20000 post-amp lowpass Hz (default 4000)
  :tonestack  :neutral | :bright | :dark | :mid-scoop | :mid-hump  (default :neutral)
  :sag        0.0–1.0  power supply sag / transient compression (default 0.0)
  :mix        0.0–1.0  dry/wet blend (default 1.0)`,
```

### `docs/USAGE.md` update

Add `:amp-sim` to the effects table and examples section:

```lisp
;; Classic crunch
(->> (seq :e2 :_ :e2 :g2)
     (synth :saw)
     (fx :amp-sim :gain 6 :stages 2 :tonestack :bright))

;; High-gain
(->> (seq :c1 :_ :c1 :_ :db1 :_ :c1 :_)
     (synth :square)
     (fx :amp-sim :gain 80 :stages 4 :tonestack :mid-scoop :sag 0.3))

;; Warm blues
(->> (seq :a2 :c3 :e3 :a3)
     (synth :saw)
     (fx :amp-sim :gain 4 :stages 2 :tonestack :mid-hump :sag 0.5))
```

---

## Files to change

| File | Change |
|------|--------|
| `app/public/plugins/amp-sim.js` | **New** — `:amp-sim` effect plugin |
| `app/public/worklets/sag-processor.js` | **New** (Option A only) — sag envelope worklet |
| `app/src/repulse/app.cljs` | Add `"/plugins/amp-sim.js"` to auto-load list |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add `"amp-sim"` to `BuiltinName` |
| `app/src/repulse/lisp-lang/completions.js` | Add `:amp-sim` entry |
| hover docs map | Add docs for `:amp-sim` and its parameters |
| `docs/USAGE.md` | Add `:amp-sim` to effects table + examples |
| `CLAUDE.md` | Mark DST3 as ✓ delivered |

Run `npm run gen:grammar` after editing the grammar.

No changes to `packages/core/`, `packages/lisp/`, or `packages/audio/`.
No changes to `distort.js`, `fx.cljs`, or `eval.cljs`.

---

## Definition of done

- [ ] `(fx :amp-sim)` with no parameters produces crunchy blues-amp tone (audible, musical)
- [ ] `:stages 1` with low gain sounds similar to `(fx :distort)` with equivalent drive
- [ ] `:stages 4` with same `:gain` total produces more complex, richer harmonic content
      than `:stages 1` (audibly different character, not just louder)
- [ ] All five `:tonestack` presets (`:neutral`, `:bright`, `:dark`, `:mid-scoop`, `:mid-hump`)
      produce measurably different frequency responses
- [ ] `:sag 0` has no dynamic compression; `:sag 1.0` audibly squashes transients
- [ ] `:gain 100 :stages 4` produces no NaN, no digital overs, no runaway feedback
- [ ] `:mix 0` passes dry signal; `:mix 1` passes fully processed signal
- [ ] Changing `:gain`, `:stages`, `:tone`, `:mix` while playing produces no sustained clicks
- [ ] `(fx :off :amp-sim)` / `(fx :on :amp-sim)` bypass works
- [ ] Works in `->>` chains with all existing effects
- [ ] `:tonestack :unknown-name` → console warning, falls back to `:neutral`
- [ ] Grammar change committed with regenerated `parser.js`
- [ ] All DST1 and DST2 acceptance criteria still pass
- [ ] All existing core tests pass (`npm run test:core`)
