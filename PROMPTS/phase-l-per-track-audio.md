# Phase L — Per-Track Audio Routing & Sample Control

## Goal

Four additions that transform REPuLse from a single-bus instrument into a proper
mix environment with independent track processing, richer sample playback, pattern-aware
dynamics, and more synth voices.

```lisp
;; Before — all effects are global, all tracks share one bus:
(play :kick (seq :bd :_ :bd :_))
(play :lead (scale :minor :c4 (seq 0 2 4 7)))
(fx :reverb 0.4)    ; washes over everything

;; After — per-track effect chains (inline ->> syntax):
(play :lead (->> (scale :minor :c4 (seq 0 2 4 7))
                 (fx :reverb 0.4)))           ; reverb on lead only
(play :lead (->> (scale :minor :c4 (seq 0 2 4 7))
                 (fx :reverb 0.4)
                 (fx :delay :wet 0.3 :time 0.25)))
(play :kick (->> (seq :bd :_ :bd :_)
                 (fx :filter 800)))           ; lowpass on kick only
(play :kick (seq :bd :_ :bd :_))              ; re-evaluate without fx to remove

;; After — sample playback control:
(->> (sound :tabla 0) (rate 1.5))          ; 50% higher pitch
(->> (sound :tabla 0) (begin 0.2) (end 0.8))  ; middle 60% of sample
(->> (sound :tabla 0) (loop-sample true))  ; loop the sample

;; After — pattern-aware sidechain:
(fx :sidechain :trigger :bd :amount 0.8 :release 0.1)

;; After — new synth voices (per-note form):
(saw :c4)                   ; sawtooth oscillator
(square :c4 :pw 0.25)       ; pulse wave, 25% duty cycle
(noise)                     ; white noise burst
(fm :c4 :index 3 :ratio 2) ; FM synthesis pair

;; After — synth transformer (apply voice to whole note pattern):
(->> (seq :c4 :eb4 :g4) (synth :saw) (amp 0.6) (decay 0.5))
(->> (seq :c3 :eb3 :g3) (synth :square :pw 0.25) (amp 0.4))
(->> (seq :c4 :eb4 :g4) (synth :fm :index 4 :ratio 2) (decay 1.2))
```

---

## Background

### Current signal chain

```
WorkletNode ──► masterGain ──► [global FX chain] ──► analyser ──► destination
```

All tracks mix directly into `masterGain`. Effects from `fx.cljs` are inserted between
`masterGain` and `analyser` as a flat chain. There is no way to route different tracks
through different effects.

### Current scheduler state

```clojure
{:playing?    true
 :tracks      {:kick pat :lead pat}   ; keyword → Pattern
 :muted       #{}
 :cycle       42
 :cycle-dur   2.0
 :lookahead   0.2
 :interval-id 17
 :on-beat     fn
 :on-event    fn}
```

`play-event` has no knowledge of which track an event came from. It receives only
`(ac, t, value)`.

### Current params

`packages/core/src/repulse/params.cljs` has: `amp`, `attack`, `decay`, `release`, `pan`.
These use `apply-param` which merges a key into the event value map via `combine`.

### Current WASM voices

`Voice` enum in `packages/audio/src/lib.rs`: `Kick`, `Snare`, `Hihat`, `Tone` (sine).
`activate` dispatches on string value: `"bd"`, `"sd"`, `"hh"`, `"oh"`, or numeric Hz.

### Current sample playback

`samples/play!` creates an `AudioBufferSourceNode`, connects it through a gain and
panner to `ac.destination` (not to `masterGain`). It does not support playback rate,
loop, or start/end offsets.

---

## Design

### 1. Per-track GainNodes

Each track gets its own `GainNode` when `play-track!` is called. The `scheduler-state`
atom gains per-track audio routing info:

```clojure
:track-nodes {:kick {:gain-node <GainNode>
                     :fx-chain [{:name "filter" :plugin <js> :input <node> :output <node>}]}
              :lead {:gain-node <GainNode>
                     :fx-chain [{:name "reverb" :plugin <js> :input <node> :output <node>}
                                {:name "delay"  :plugin <js> :input <node> :output <node>}]}}
```

Signal flow after this phase:

```
                    ┌─► kick GainNode ──► [kick FX] ──┐
WorkletNode ──┐     │                                  │
              ├─────┤                                  ├──► masterGain ──► [global FX] ──► analyser ──► dest
sources ──────┘     │                                  │
                    └─► lead GainNode ──► [lead FX] ──┘
```

`play-event` gains a fourth argument: the track name. It routes the output of each
sound source to the track's GainNode rather than `masterGain` directly.

### 2. Per-track effects reuse the plugin infrastructure

Per-track effects use the exact same plugin interface as global effects (`createNodes`,
`setParam`, `bypass`, `destroy`). The only difference is where the chain is stored and
what nodes the chain connects between.

Per-track effects are applied inline via `->>` in the `play` call. The `fx` function
detects when a pattern is passed as its last argument and annotates it with `:track-fx`
metadata instead of applying global chain changes. When `play` evaluates, it reads the
metadata and wires up the track's effect chain automatically:

```lisp
(play :bass (->> (seq :c2 :_ :eb2 :_)
                 (fx :filter 600)
                 (fx :overdrive 0.6)))
```

### 3. Sample params as event map keys

New param functions (`rate`, `begin`, `end`, `loop-sample`) follow the same pattern as
`amp`, `attack`, `decay`, `pan` — they use `apply-param` to merge keys into the event
value map. The keys are:

- `:rate` — playback rate multiplier (1.0 = normal, 2.0 = double speed / octave up)
- `:begin` — start position as fraction of buffer duration (0.0–1.0)
- `:end` — end position as fraction of buffer duration (0.0–1.0)
- `:loop` — boolean, loop the sample

These are read in `samples/play!` and applied to the `AudioBufferSourceNode`.

### 4. Sidechain as gain automation, not a compressor

REPuLse knows event times before they sound. The sidechain plugin schedules gain
automation directly from pattern event timing — no audio-level analysis needed.
This is implemented as an effect plugin that receives event notifications from the
scheduler via a JS callback interface.

### 5. New voice types in WASM

Four new `Voice` variants: `Saw`, `Square`, `Noise`, `FM`. These are activated via
new string values in `activate`: `"saw:<freq>"`, `"square:<freq>"`, `"noise"`,
`"fm:<freq>:<index>:<ratio>"`. The JS fallback synth adds matching implementations.

---

## Implementation

### 1. `app/src/repulse/audio.cljs` — per-track routing

#### 1a. New atoms and helpers for track nodes

Add after the existing `analyser-node` defonce:

```clojure
;; Per-track audio routing: keyword → {:gain-node GainNode :fx-chain [...]}
(defonce track-nodes (atom {}))

(defn- ensure-track-node!
  "Create a GainNode for a track if it doesn't exist, connect to masterGain."
  [ac track-name]
  (when-not (get @track-nodes track-name)
    (let [gain (doto (.createGain ac)
                 (-> .-gain (.setValueAtTime 1.0 (.-currentTime ac)))
                 (.connect @master-gain))]
      (swap! track-nodes assoc track-name {:gain-node gain :fx-chain []}))))

(defn- track-output-node
  "Returns the node that sources in this track should connect to.
   If the track has a per-track FX chain, returns the input of the first effect.
   Otherwise returns the track's GainNode."
  [track-name]
  (let [tn (get @track-nodes track-name)]
    (if-let [first-fx (first (:fx-chain tn))]
      (:input first-fx)
      (:gain-node tn))))

(defn- output-for-track
  "Returns the AudioNode that a source in the given track should connect to.
   Falls back to masterGain if track routing is not set up."
  [ac track-name]
  (if-let [tn (get @track-nodes track-name)]
    (if (seq (:fx-chain tn))
      (:input (first (:fx-chain tn)))
      (:gain-node tn))
    (output-node ac)))
```

#### 1b. Modify `play-event` to accept a track name

Change the signature from `(defn play-event [ac t value])` to:

```clojure
(defn play-event [ac t value track-name]
```

In every branch that connects a source to `(output-node ac)`, change the connection
target to `(output-for-track ac track-name)`. This affects:

- `make-kick`, `make-snare`, `make-hihat`, `make-sine` — each accepts an extra
  `destination` parameter (the node to connect to instead of `(output-node ac)`)
- `samples/play!` — needs a new arity that accepts a destination node

Update `output-node` references in JS fallback synth functions:

```clojure
(defn- make-kick [ac t amp pan dest]
  ;; ... same as before but:
  (.connect panner dest))    ; was (.connect panner (output-node ac))

(defn- make-snare [ac t amp pan dest]
  (.connect panner dest))

(defn- make-hihat [ac t amp pan dest]
  (.connect panner dest))

(defn- make-sine [ac t freq dur amp attack pan dest]
  (.connect panner dest))
```

Default `dest` to `(output-node ac)` when called from `play-event` without a track:

```clojure
(defn play-event
  ([ac t value] (play-event ac t value nil))
  ([ac t value track-name]
   (let [dest (output-for-track ac track-name)]
     ;; existing dispatch, passing dest to all synth/sample functions
     ...)))
```

#### 1c. Modify `schedule-cycle!` to pass track name

```clojure
(defn schedule-cycle! [ac state cycle]
  (let [{:keys [tracks muted cycle-dur on-beat on-event]} state
        sp {:start [cycle 1] :end [(inc cycle) 1]}]
    (doseq [[track-name pattern] tracks]
      (when (and pattern (not (contains? muted track-name)))
        (let [evs (core/query pattern sp)]
          (doseq [ev evs]
            (let [part-start        (core/rat->float (:start (:part ev)))
                  cycle-audio-start (* cycle cycle-dur)
                  event-offset      (* (- part-start cycle) cycle-dur)
                  t                 (+ cycle-audio-start event-offset)]
              (when (> t (.-currentTime ac))
                (play-event ac t (:value ev) track-name)    ; <-- pass track-name
                ;; ... on-event / on-beat callbacks unchanged
                ))))))))
```

#### 1d. Modify `play-track!` to create track node

```clojure
(defn play-track!
  [track-name pattern on-beat-fn on-event-fn]
  (let [ac (get-ctx)]
    (.resume ac)
    (ensure-track-node! ac track-name)
    (swap! scheduler-state update :tracks assoc track-name pattern)
    (ensure-running! ac on-beat-fn on-event-fn)))
```

#### 1e. Modify `stop!` and `clear-track!` to clean up track nodes

```clojure
(defn stop! []
  (when-let [node @worklet-node]
    (.. node -port (postMessage #js {:type "stop"})))
  (when-let [id (:interval-id @scheduler-state)]
    (js/clearInterval id))
  ;; Disconnect and clear all per-track nodes
  (doseq [[_ {:keys [gain-node fx-chain]}] @track-nodes]
    (.disconnect gain-node)
    (doseq [{:keys [plugin]} fx-chain]
      (try (.destroy ^js plugin) (catch :default _))))
  (reset! track-nodes {})
  (swap! scheduler-state assoc
         :playing?     false
         :interval-id  nil
         :tracks       {}
         :muted        #{}))

(defn clear-track! [track-name]
  ;; Disconnect and remove track node
  (when-let [tn (get @track-nodes track-name)]
    (.disconnect (:gain-node tn))
    (doseq [{:keys [plugin]} (:fx-chain tn)]
      (try (.destroy ^js plugin) (catch :default _)))
    (swap! track-nodes dissoc track-name))
  (swap! scheduler-state (fn [s]
    (-> s
        (update :tracks dissoc track-name)
        (update :muted disj track-name))))
  (when (empty? (:tracks @scheduler-state))
    (stop!)))
```

---

### 2. `app/src/repulse/fx.cljs` — per-track effect chains

Add per-track FX management functions. These reuse the same plugin loading mechanism
as global effects.

```clojure
(defn- rewire-track!
  "Reconnect a track's FX chain: trackGain → fx1 → fx2 → ... → masterGain."
  [track-name]
  (let [tn      (get @audio/track-nodes track-name)
        gain    (:gain-node tn)
        effects (:fx-chain tn)
        master  @audio/master-gain]
    (.disconnect gain)
    (doseq [{:keys [output]} effects]
      (.disconnect output))
    (if (empty? effects)
      (.connect gain master)
      (do
        (.connect gain (:input (first effects)))
        (doseq [[a b] (partition 2 1 effects)]
          (.connect (:output a) (:input b)))
        (.connect (:output (last effects)) master)))))

(defn add-track-effect!
  "Instantiate an effect plugin on a specific track."
  [track-name effect-name]
  (let [ac     (audio/get-ctx)
        ;; Find plugin constructor from the global registry
        global (some #(when (= effect-name (:name %)) %) @chain)]
    (when global
      ;; Clone the plugin by re-importing it (effects are ES modules)
      ;; For now, use the plugin registry to get a fresh instance
      (let [^js plugin (:plugin global)
            ^js fresh-plugin (js/Object.create (js/Object.getPrototypeOf plugin))
            _     (js/Object.assign fresh-plugin plugin)
            nodes (.createNodes fresh-plugin ac)]
        (swap! audio/track-nodes update-in [track-name :fx-chain]
               conj {:name      effect-name
                     :plugin    fresh-plugin
                     :input     (.-inputNode nodes)
                     :output    (.-outputNode nodes)
                     :bypassed? false})
        (rewire-track! track-name)))))

(defn remove-track-effect!
  "Remove a specific effect from a track's chain."
  [track-name effect-name]
  (when-let [tn (get @audio/track-nodes track-name)]
    (when-let [entry (some #(when (= effect-name (:name %)) %) (:fx-chain tn))]
      (.destroy ^js (:plugin entry))
      (swap! audio/track-nodes update-in [track-name :fx-chain]
             (fn [c] (filterv #(not= effect-name (:name %)) c)))
      (rewire-track! track-name))))

(defn clear-track-effects!
  "Remove all effects from a track."
  [track-name]
  (when-let [tn (get @audio/track-nodes track-name)]
    (doseq [{:keys [plugin]} (:fx-chain tn)]
      (.destroy ^js plugin))
    (swap! audio/track-nodes assoc-in [track-name :fx-chain] [])
    (rewire-track! track-name)))

(defn set-track-param!
  "Set a parameter on a specific effect in a track's chain."
  [track-name effect-name param-name value]
  (when-let [tn (get @audio/track-nodes track-name)]
    (when-let [entry (some #(when (= effect-name (:name %)) %) (:fx-chain tn))]
      (.setParam ^js (:plugin entry) param-name value))))

(defn bypass-track-effect!
  "Bypass or un-bypass an effect on a specific track."
  [track-name effect-name enabled]
  (when-let [tn (get @audio/track-nodes track-name)]
    (when-let [entry (some #(when (= effect-name (:name %)) %) (:fx-chain tn))]
      (.bypass ^js (:plugin entry) enabled)
      (swap! audio/track-nodes update-in [track-name :fx-chain]
             (fn [c] (mapv #(if (= effect-name (:name %))
                              (assoc % :bypassed? enabled) %) c))))))
```

---

### 3. `packages/core/src/repulse/params.cljs` — new sample parameters

Add after the existing `pan` function:

```clojure
(defn rate
  "Playback rate multiplier. 1.0 = normal, 2.0 = double speed (octave up),
   0.5 = half speed (octave down).
   (rate 1.5 pat)       — apply directly
   (rate 1.5)           — return transformer"
  ([r]     (fn [pat] (rate r pat)))
  ([r pat] (apply-param :rate r pat)))

(defn begin
  "Sample start position as a fraction of buffer duration (0.0–1.0).
   (begin 0.25 pat)     — start playback at 25% into the sample
   (begin 0.25)         — return transformer"
  ([t]     (fn [pat] (begin t pat)))
  ([t pat] (apply-param :begin t pat)))

(defn end*
  "Sample end position as a fraction of buffer duration (0.0–1.0).
   (end 0.75 pat)       — stop playback at 75% into the sample
   (end 0.75)           — return transformer
   Named end* internally to avoid conflict with cljs.core/end."
  ([t]     (fn [pat] (end* t pat)))
  ([t pat] (apply-param :end t pat)))

(defn loop-sample
  "Enable sample looping.
   (loop-sample true pat)  — loop the sample
   (loop-sample true)      — return transformer"
  ([on?]     (fn [pat] (loop-sample on? pat)))
  ([on? pat] (apply-param :loop on? pat)))
```

---

### 4. `packages/core/test/repulse/params_test.cljs` — new tests

Add tests for the new sample parameter functions:

```clojure
(deftest rate-scalar
  (let [evs (core/query (params/rate 1.5 (core/pure :bd)) one-cycle)]
    (is (= {:note :bd :rate 1.5} (:value (first evs))))))

(deftest rate-one-arg
  (let [speed-up (params/rate 2.0)
        evs      (core/query (speed-up (core/pure :bd)) one-cycle)]
    (is (= {:note :bd :rate 2.0} (:value (first evs))))))

(deftest begin-end-chain
  (let [evs (core/query
              (params/end* 0.8 (params/begin 0.2 (core/pure :tabla)))
              one-cycle)]
    (is (= {:note :tabla :begin 0.2 :end 0.8} (:value (first evs))))))

(deftest loop-sample-test
  (let [evs (core/query (params/loop-sample true (core/pure :bd)) one-cycle)]
    (is (= {:note :bd :loop true} (:value (first evs))))))
```

---

### 5. `app/src/repulse/samples.cljs` — sample playback control

Update `play!` to read `:rate`, `:begin`, `:end`, `:loop` from event params:

```clojure
(defn play!
  "Schedule playback of the nth sample from bank at audio time t.
   params is an optional map with keys:
     :amp   — 0.0–1.0 (default 1.0)
     :pan   — -1.0–1.0 (default 0.0)
     :rate  — playback rate (default 1.0)
     :begin — start offset as fraction 0.0–1.0 (default 0.0)
     :end   — end offset as fraction 0.0–1.0 (default 1.0)
     :loop  — boolean (default false)"
  ([ac t bank n] (play! ac t bank n 1.0 0.0))
  ([ac t bank n amp] (play! ac t bank n amp 0.0))
  ([ac t bank n amp pan] (play! ac t bank n amp pan {}))
  ([ac t bank n amp pan extra-params]
   (when-let [url (get-url bank n)]
     (-> (get-buffer! url ac)
         (.then (fn [buf]
                  (let [src    (.createBufferSource ac)
                        gain   (.createGain ac)
                        panner (.createStereoPanner ac)
                        t'     (max t (.-currentTime ac))
                        rate   (or (:rate extra-params) 1.0)
                        begin  (or (:begin extra-params) 0.0)
                        end-f  (or (:end extra-params) 1.0)
                        loop?  (or (:loop extra-params) false)
                        buf-dur (.-duration buf)
                        offset  (* begin buf-dur)
                        dur     (* (- end-f begin) buf-dur)]
                    (set! (.-buffer src) buf)
                    (set! (.. src -playbackRate -value) (float rate))
                    (when loop?
                      (set! (.-loop src) true)
                      (set! (.-loopStart src) offset)
                      (set! (.-loopEnd src) (* end-f buf-dur)))
                    (.setValueAtTime (.-gain gain) (float amp) t')
                    (.setValueAtTime (.-pan panner) (float pan) t')
                    (.connect src gain)
                    (.connect gain panner)
                    (.connect panner (.-destination ac))
                    (if loop?
                      (.start src t' offset)
                      (.start src t' offset dur)))))
         (.catch (fn [e]
                   (js/console.debug "[REPuLse] sample play failed:" (name bank) e)))))))
```

Update the sample-playing branches of `play-event` in `audio.cljs` to pass the
extra params through. When the event value is a map, extract sample control keys:

```clojure
;; In the (map? value) branch, after extracting amp-v, attack-v, etc:
(let [sample-params {:rate  (:rate value)
                     :begin (:begin value)
                     :end   (:end value)
                     :loop  (:loop value)}]
  ;; Pass sample-params to samples/play! in the sample bank branch:
  (samples/play! ac t resolved 0 amp-v pan-v sample-params))
```

Also update `samples/play!` to accept a destination node parameter, so per-track
routing works for samples too:

```clojure
([ac t bank n amp pan extra-params dest]
 ;; ... same as above but:
 (.connect panner dest))    ; instead of (.connect panner (.-destination ac))
```

---

### 6. `app/public/plugins/sidechain.js` — new effect plugin

A pattern-aware sidechain compressor that ducks gain when specified events fire:

```javascript
export default {
  type: "effect",
  name: "sidechain",
  version: "1.0.0",

  // State
  _trigger: "bd",
  _amount: 0.8,
  _release: 0.1,
  _gain: null,
  _ctx: null,

  init(host) {},

  createNodes(ctx) {
    this._ctx = ctx;
    this._gain = ctx.createGain();
    this._gain.gain.value = 1.0;
    return { inputNode: this._gain, outputNode: this._gain };
  },

  setParam(name, value) {
    switch (name) {
      case "trigger": this._trigger = String(value); break;
      case "amount":  this._amount = Math.max(0, Math.min(1, Number(value))); break;
      case "release": this._release = Math.max(0.01, Number(value)); break;
      case "value":   this._trigger = String(value); break;
    }
  },

  /**
   * Called by the scheduler when any event fires.
   * eventName: the string name of the sound (e.g. "bd", "sd")
   * time: the AudioContext time the event is scheduled for
   */
  onEvent(eventName, time) {
    if (eventName === this._trigger && this._gain && this._ctx) {
      const gain = this._gain.gain;
      const target = 1.0 - this._amount;
      // Duck: instantly drop gain, then ramp back up
      gain.cancelScheduledValues(time);
      gain.setValueAtTime(target, time);
      gain.linearRampToValueAtTime(1.0, time + this._release);
    }
  },

  bypass(on) {
    if (this._gain) {
      this._gain.gain.cancelScheduledValues(this._ctx.currentTime);
      this._gain.gain.value = 1.0;
    }
  },

  getParams() {
    return {
      trigger: this._trigger,
      amount:  this._amount,
      release: this._release
    };
  },

  destroy() {
    if (this._gain) this._gain.disconnect();
  }
};
```

#### 6a. Event notification hook in the scheduler

In `schedule-cycle!` in `audio.cljs`, after calling `play-event`, notify all effect
plugins that implement `onEvent`:

```clojure
;; After (play-event ac t (:value ev) track-name):
(when-let [on-fx (:on-fx-event @scheduler-state)]
  (on-fx (:value ev) t))
```

Add the notification helper **in `fx.cljs`** (not in `audio.cljs` — `audio` cannot
require `fx` without creating a circular dependency, since `fx` already requires `audio`):

In `app/src/repulse/fx.cljs`, add:

```clojure
(defn- event-name
  "Extract the sound name from an event value for sidechain matching."
  [value]
  (cond
    (keyword? value) (name value)
    (and (map? value) (:note value)) (name (:note value))
    :else nil))

(defn notify-fx-event!
  "Notify all effect plugins (global and per-track) of a fired event.
   Called from the scheduler in audio.cljs — audio passes the event,
   fx knows about the chain. This keeps the dependency one-way: fx → audio."
  [value t]
  (when-let [evt-name (event-name value)]
    ;; Global effects
    (doseq [{:keys [plugin]} @chain]
      (when (.-onEvent ^js plugin)
        (.onEvent ^js plugin evt-name t)))
    ;; Per-track effects
    (doseq [[_ {:keys [fx-chain]}] @audio/track-nodes]
      (doseq [{:keys [plugin]} fx-chain]
        (when (.-onEvent ^js plugin)
          (.onEvent ^js plugin evt-name t))))))
```

Then in `audio.cljs`, the scheduler calls `fx/notify-fx-event!`. This requires
`audio.cljs` to add `[repulse.fx :as fx]` to its namespace requires.

**Wait — that would also be circular** (`audio → fx → audio`).

**Solution:** Use `app.cljs` as the mediator. The scheduler in `audio.cljs` accepts
an optional `:on-fx-event` callback when starting playback. `app.cljs` passes
`fx/notify-fx-event!` as this callback. Neither `audio.cljs` nor `fx.cljs` requires
the other's namespace directly.

In `audio.cljs`, `schedule-cycle!`:
```clojure
;; After (play-event ac t (:value ev) track-name):
(when-let [on-fx (:on-fx-event @scheduler-state)]
  (on-fx (:value ev) t))
```

In `app.cljs`, when starting playback:
```clojure
(swap! audio/scheduler-state assoc :on-fx-event fx/notify-fx-event!)
```

This keeps the dependency graph acyclic:
```
app.cljs → audio.cljs
app.cljs → fx.cljs
fx.cljs  → audio.cljs   (one-way, reads track-nodes/master-gain)
audio.cljs → (nothing in fx)
```

---

### 7. `packages/audio/src/lib.rs` — new voice types

#### 7a. Add `Saw`, `Square`, `Noise`, `FM` variants to Voice enum

```rust
enum Voice {
    Kick { /* ... unchanged ... */ },
    Snare { /* ... unchanged ... */ },
    Hihat { /* ... unchanged ... */ },
    Tone { /* ... unchanged ... */ },
    Saw {
        phase: f64,
        freq: f64,
        amp: f32,
        gain: f32,
        gain_decay: f32,
        attack_inc: f32,
        in_attack: bool,
    },
    Square {
        phase: f64,
        freq: f64,
        amp: f32,
        gain: f32,
        gain_decay: f32,
        attack_inc: f32,
        in_attack: bool,
        pulse_width: f32,   // 0.0–1.0, default 0.5
    },
    Noise {
        state: u32,
        gain: f32,
        gain_decay: f32,
    },
    FM {
        carrier_phase: f64,
        mod_phase: f64,
        carrier_freq: f64,
        mod_freq: f64,
        index: f32,          // modulation index
        amp: f32,
        gain: f32,
        gain_decay: f32,
        attack_inc: f32,
        in_attack: bool,
    },
}
```

#### 7b. Implement `tick` for new voices

Add new match arms in `Voice::tick`:

```rust
Voice::Saw { phase, freq, amp, gain, gain_decay, attack_inc, in_attack } => {
    if *in_attack {
        *gain += *attack_inc;
        if *gain >= *amp { *gain = *amp; *in_attack = false; }
    } else {
        *gain *= *gain_decay;
    }
    // Sawtooth: ramp from -1 to +1 over one cycle
    let s = (2.0 * (*phase % 1.0) - 1.0) as f32;
    *phase += *freq / sr as f64;
    s * *gain
}

Voice::Square { phase, freq, amp, gain, gain_decay, attack_inc, in_attack, pulse_width } => {
    if *in_attack {
        *gain += *attack_inc;
        if *gain >= *amp { *gain = *amp; *in_attack = false; }
    } else {
        *gain *= *gain_decay;
    }
    let p = *phase % 1.0;
    let s: f32 = if p < *pulse_width as f64 { 1.0 } else { -1.0 };
    *phase += *freq / sr as f64;
    s * *gain
}

Voice::Noise { state, gain, gain_decay } => {
    let s = lcg_next(state);
    let out = s * *gain;
    *gain *= *gain_decay;
    out
}

Voice::FM { carrier_phase, mod_phase, carrier_freq, mod_freq, index, amp, gain, gain_decay, attack_inc, in_attack } => {
    if *in_attack {
        *gain += *attack_inc;
        if *gain >= *amp { *gain = *amp; *in_attack = false; }
    } else {
        *gain *= *gain_decay;
    }
    // FM synthesis: carrier_out = sin(carrier_phase + index * sin(mod_phase))
    let mod_signal = (*mod_phase * TAU).sin() as f32;
    let carrier_signal = (*carrier_phase * TAU + (*index * mod_signal) as f64).sin() as f32;
    *carrier_phase += *carrier_freq / sr as f64;
    *mod_phase += *mod_freq / sr as f64;
    carrier_signal * *gain
}
```

#### 7c. Implement `is_silent` for new voices

```rust
fn is_silent(&self) -> bool {
    match self {
        Voice::Tone { gain, in_attack, .. }
        | Voice::Saw { gain, in_attack, .. }
        | Voice::Square { gain, in_attack, .. }
        | Voice::FM { gain, in_attack, .. } => !*in_attack && *gain < 1e-5,
        Voice::Kick { gain, .. } | Voice::Snare { gain, .. }
        | Voice::Hihat { gain, .. } | Voice::Noise { gain, .. } => *gain < 1e-5,
    }
}
```

#### 7d. Dispatch in `activate`

Add new match arms in `AudioEngine::activate`:

```rust
fn activate(&mut self, p: Pending) {
    let sr = self.sample_rate;
    let seed = self.next_seed();
    let amp = p.amp.clamp(0.0, 1.0);
    let value = p.value.trim_start_matches(':');
    let voice = if value.starts_with("saw:") {
        let freq = value[4..].parse::<f64>().unwrap_or(440.0);
        let peak = amp * 0.5;
        let attack_samples = (p.attack * sr).max(1.0);
        Voice::Saw {
            phase: 0.0, freq, amp: peak,
            gain: 0.0, gain_decay: decay_rate(p.decay, sr),
            attack_inc: peak / attack_samples, in_attack: true,
        }
    } else if value.starts_with("square:") {
        let parts: Vec<&str> = value[7..].splitn(2, ':').collect();
        let freq = parts[0].parse::<f64>().unwrap_or(440.0);
        let pw = if parts.len() > 1 { parts[1].parse::<f32>().unwrap_or(0.5) } else { 0.5 };
        let peak = amp * 0.5;
        let attack_samples = (p.attack * sr).max(1.0);
        Voice::Square {
            phase: 0.0, freq, amp: peak,
            gain: 0.0, gain_decay: decay_rate(p.decay, sr),
            attack_inc: peak / attack_samples, in_attack: true,
            pulse_width: pw.clamp(0.01, 0.99),
        }
    } else if value == "noise" {
        Voice::Noise {
            state: seed,
            gain: amp * 0.3,
            gain_decay: decay_rate(p.decay, sr),
        }
    } else if value.starts_with("fm:") {
        // Format: "fm:<carrier_freq>:<index>:<ratio>"
        let parts: Vec<&str> = value[3..].splitn(3, ':').collect();
        let carrier_freq = parts[0].parse::<f64>().unwrap_or(440.0);
        let index = if parts.len() > 1 { parts[1].parse::<f32>().unwrap_or(1.0) } else { 1.0 };
        let ratio = if parts.len() > 2 { parts[2].parse::<f64>().unwrap_or(2.0) } else { 2.0 };
        let peak = amp * 0.5;
        let attack_samples = (p.attack * sr).max(1.0);
        Voice::FM {
            carrier_phase: 0.0, mod_phase: 0.0,
            carrier_freq, mod_freq: carrier_freq * ratio,
            index, amp: peak,
            gain: 0.0, gain_decay: decay_rate(p.decay, sr),
            attack_inc: peak / attack_samples, in_attack: true,
        }
    } else {
        // ... existing match on "bd", "sd", "hh", "oh", Hz parse ...
        match value {
            "bd" => { /* ... unchanged ... */ }
            "sd" => { /* ... unchanged ... */ }
            "hh" => { /* ... unchanged ... */ }
            "oh" => { /* ... unchanged ... */ }
            other => {
                let freq = other.parse::<f64>().unwrap_or(440.0);
                let peak = amp * 0.5;
                let attack_samples = (p.attack * sr).max(1.0);
                Voice::Tone {
                    phase: 0.0, freq, amp: peak, gain: 0.0,
                    gain_decay: decay_rate(p.decay, sr),
                    attack_inc: peak / attack_samples, in_attack: true,
                }
            }
        }
    };
    self.voices.push(ActiveVoice { voice, pan: p.pan.clamp(-1.0, 1.0) });
}
```

---

### 8. `app/src/repulse/audio.cljs` — JS fallback synth for new voices

Add three new JS fallback functions:

```clojure
(defn- make-saw [ac t freq dur amp attack pan dest]
  (let [osc    (.createOscillator ac)
        gain   (.createGain ac)
        panner (.createStereoPanner ac)
        peak   (* 0.5 (float amp))
        atk    (max 0.001 (float attack))
        stop-t (+ t atk (float dur))]
    (set! (.-type osc) "sawtooth")
    (.setValueAtTime (.-frequency osc) freq t)
    (.setValueAtTime (.-gain gain) 0.0001 t)
    (.linearRampToValueAtTime (.-gain gain) peak (+ t atk))
    (.exponentialRampToValueAtTime (.-gain gain) 0.0001 stop-t)
    (.setValueAtTime (.-pan panner) (float pan) t)
    (.connect osc gain)
    (.connect gain panner)
    (.connect panner dest)
    (.start osc t)
    (.stop osc stop-t)))

(defn- make-pulse-wave
  "Build a PeriodicWave for a pulse wave with duty cycle pw (0.0–1.0).
   Fourier coefficients: real[n] = 2·sin(2πnD)/(πn), imag[n] = 2·(1−cos(2πnD))/(πn).
   For pw=0.5 the caller uses the native 'square' OscillatorNode type instead."
  [ac pw]
  (let [n 64 real (js/Float32Array. n) imag (js/Float32Array. n) d (double pw)]
    (aset real 0 0.0) (aset imag 0 0.0)
    (dotimes [i (dec n)]
      (let [k (inc i) pk (* js/Math.PI k)]
        (aset real k (/ (* 2.0 (js/Math.sin (* 2.0 js/Math.PI k d))) pk))
        (aset imag k (/ (* 2.0 (- 1.0 (js/Math.cos (* 2.0 js/Math.PI k d)))) pk))))
    (.createPeriodicWave ac real imag #js {:disableNormalization false})))

(defn- make-square [ac t freq dur amp attack pan dest pw]
  ;; pw=0.5 → native "square" type; any other duty cycle → PeriodicWave (Fourier).
  ;; This makes :pw work in the JS fallback (Safari/Chrome WASM fallback).
  (let [osc    (.createOscillator ac)
        gain   (.createGain ac)
        panner (.createStereoPanner ac)
        peak   (* 0.5 (float amp))
        atk    (max 0.001 (float attack))
        stop-t (+ t atk (float dur))]
    (if (== (double pw) 0.5)
      (set! (.-type osc) "square")
      (.setPeriodicWave osc (make-pulse-wave ac pw)))
    (.setValueAtTime (.-frequency osc) freq t)
    (.setValueAtTime (.-gain gain) 0.0001 t)
    (.linearRampToValueAtTime (.-gain gain) peak (+ t atk))
    (.exponentialRampToValueAtTime (.-gain gain) 0.0001 stop-t)
    (.setValueAtTime (.-pan panner) (float pan) t)
    (.connect osc gain)
    (.connect gain panner)
    (.connect panner dest)
    (.start osc t)
    (.stop osc stop-t)))

(defn- make-noise [ac t dur amp pan dest]
  (let [buf-size (* (.-sampleRate ac) (int (Math/ceil dur)))
        buf  (.createBuffer ac 1 buf-size (.-sampleRate ac))
        data (.getChannelData buf 0)
        _    (dotimes [i buf-size]
               (aset data i (- (* 2 (Math/random)) 1)))
        src  (.createBufferSource ac)
        gain (.createGain ac)
        panner (.createStereoPanner ac)
        pk   (* 0.3 (float amp))]
    (set! (.-buffer src) buf)
    (.setValueAtTime (.-gain gain) pk t)
    (.exponentialRampToValueAtTime (.-gain gain) 0.001 (+ t dur))
    (.setValueAtTime (.-pan panner) (float pan) t)
    (.connect src gain)
    (.connect gain panner)
    (.connect panner dest)
    (.start src t)
    (.stop src (+ t dur))))
```

> FM synthesis in JS fallback is complex (requires two connected OscillatorNodes with
> gain-controlled modulation). For the JS fallback, fall through to a sine tone. The
> WASM engine handles FM properly.

---

### 9. `packages/lisp/src/repulse/lisp/eval.cljs` — new bindings

Add to `make-env`:

```clojure
;; Sample playback control
"rate"        (fn
                ([v]   (params/rate (unwrap v)))
                ([v p] (params/rate (unwrap v) (unwrap p))))
"begin"       (fn
                ([v]   (params/begin (unwrap v)))
                ([v p] (params/begin (unwrap v) (unwrap p))))
"end"         (fn
                ([v]   (params/end* (unwrap v)))
                ([v p] (params/end* (unwrap v) (unwrap p))))
"loop-sample" (fn
                ([v]   (params/loop-sample (unwrap v)))
                ([v p] (params/loop-sample (unwrap v) (unwrap p))))
```

Update `"fx"` in `app.cljs` `ensure-env!` to be context-aware — it detects whether a
pattern is the last argument (per-track mode via `->>`), and if so annotates the pattern
with `:track-fx` metadata instead of mutating the global chain. The `"play"` binding reads
`:track-fx` metadata and wires the chain via `fx/clear-track-effects!` + `fx/add-track-effect!`
+ `fx/set-track-param!`.

Add new synth voice constructors — `saw`, `square`, `noise`, `fm` — to `make-env`
in `eval.cljs`:

```clojure
"saw"    (fn [note]
           (let [n (unwrap note)]
             (if (theory/note-keyword? n)
               {:note n :synth :saw}
               {:note n :synth :saw})))
"square" (fn [note & opts]
           (let [n    (unwrap note)
                 opts (mapv unwrap opts)
                 pw   (or (second (drop-while #(not= :pw %) opts)) 0.5)]
             {:note n :synth :square :pw pw}))
"noise"  (fn [] {:synth :noise})
"fm"     (fn [note & opts]
           (let [n     (unwrap note)
                 opts' (apply hash-map (mapv unwrap opts))
                 idx   (get opts' :index 1.0)
                 ratio (get opts' :ratio 2.0)]
             {:note n :synth :fm :index idx :ratio ratio}))
```

---

### 10. `app/src/repulse/audio.cljs` — dispatch new voice types

In `play-event`, add handling for the `:synth` key in map values. Extend the
`(and (map? value) (:note value))` branch:

```clojure
;; After extracting note, amp-v, attack-v, decay-v, pan-v:
(let [synth-type (:synth value)]
  (case synth-type
    :saw
    (let [hz (if (keyword? note) (theory/note->hz note) note)]
      (or (worklet-trigger-v2! (str "saw:" hz) t amp-v attack-v decay-v pan-v)
          (make-saw ac t hz decay-v amp-v attack-v pan-v dest)))

    :square
    (let [hz (if (keyword? note) (theory/note->hz note) note)
          pw (or (:pw value) 0.5)]
      (or (worklet-trigger-v2! (str "square:" hz ":" pw) t amp-v attack-v decay-v pan-v)
          (make-square ac t hz decay-v amp-v attack-v pan-v pw dest)))

    :noise
    (or (worklet-trigger-v2! "noise" t amp-v attack-v decay-v pan-v)
        (make-noise ac t decay-v amp-v pan-v dest))

    :fm
    (let [hz    (if (keyword? note) (theory/note->hz note) note)
          index (or (:index value) 1.0)
          ratio (or (:ratio value) 2.0)]
      (or (worklet-trigger-v2! (str "fm:" hz ":" index ":" ratio)
                                t amp-v attack-v decay-v pan-v)
          ;; JS fallback: use sine (FM requires custom setup)
          (make-sine ac t hz decay-v amp-v attack-v pan-v dest)))

    ;; nil / default — existing dispatch (note keyword, Hz, etc.)
    (cond
      ;; ... existing note dispatch unchanged ...
    )))
```

---

### 11. `app/src/repulse/lisp-lang/repulse-lisp.grammar` — add to BuiltinName

Add to the BuiltinName token list:

```
"rate" | "begin" | "end" | "loop-sample" |
"saw" | "square" | "noise" | "fm" |
```

After editing, run `npm run gen:grammar`.

---

### 12. `app/src/repulse/lisp-lang/completions.js` — add entries

```javascript
// --- Sample playback control ---
{ label: "rate",        type: "function", detail: "(rate n pat) — playback rate (1.0 = normal, 2.0 = octave up); (rate n) returns transformer" },
{ label: "begin",       type: "function", detail: "(begin frac pat) — sample start position 0.0–1.0; (begin frac) returns transformer" },
{ label: "end",         type: "function", detail: "(end frac pat) — sample end position 0.0–1.0; (end frac) returns transformer" },
{ label: "loop-sample", type: "function", detail: "(loop-sample true pat) — loop sample playback; (loop-sample bool) returns transformer" },
// --- Synth voices ---
{ label: "synth",       type: "function", detail: "(->> (seq :c4 :eb4) (synth :saw)) — apply voice to note pattern; opts: :pw :index :ratio" },
{ label: "saw",         type: "function", detail: "(saw :c4) — sawtooth oscillator" },
{ label: "square",      type: "function", detail: "(square :c4) — square wave; (square :c4 :pw 0.25) — pulse width" },
{ label: "noise",       type: "function", detail: "(noise) — white noise burst" },
{ label: "fm",          type: "function", detail: "(fm :c4 :index 3 :ratio 2) — FM synthesis pair" },
```

---

## Files to change

| File | Change |
|---|---|
| `app/src/repulse/audio.cljs` | Per-track GainNode routing; `play-event` gains track-name param + dest routing; `ensure-track-node!`; new JS fallback synths (`make-saw`, `make-square`, `make-noise`); `:on-fx-event` callback slot in scheduler-state; track cleanup in `stop!`/`clear-track!` |
| `app/src/repulse/fx.cljs` | `rewire-track!`, `add-track-effect!`, `remove-track-effect!`, `clear-track-effects!`, `set-track-param!`, `bypass-track-effect!`, `notify-fx-event!` (event notification for sidechain) |
| `app/src/repulse/app.cljs` | Wire `fx/notify-fx-event!` into scheduler via `:on-fx-event` callback; context-aware `fx` binding (per-track via `->>` or global); updated `play` binding reads `:track-fx` metadata; auto-load sidechain plugin; `saw`/`square`/`noise`/`fm` bindings |
| `app/src/repulse/samples.cljs` | `play!` gains `:rate`, `:begin`, `:end`, `:loop` support + destination node param |
| `packages/core/src/repulse/params.cljs` | `rate`, `begin`, `end*`, `loop-sample` |
| `packages/core/test/repulse/params_test.cljs` | Tests for new param functions |
| `packages/lisp/src/repulse/lisp/eval.cljs` | `rate`, `begin`, `end`, `loop-sample`, `saw`, `square`, `noise`, `fm`, `synth` bindings in `make-env` |
| `packages/audio/src/lib.rs` | `Voice::Saw`, `Voice::Square`, `Voice::Noise`, `Voice::FM` variants; `tick`/`is_silent` match arms; `activate` dispatch |
| `app/public/plugins/sidechain.js` | **New** — pattern-aware sidechain duck plugin |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add 9 tokens to BuiltinName |
| `app/src/repulse/lisp-lang/completions.js` | Add 9 completion entries |
| `docs/USAGE.md` | New sections: "Per-track effects", "Sample control", "Synth voices" |
| `CLAUDE.md` | Mark Phase L as delivered when done |

---

## Definition of done

### Per-track routing

- [ ] `(play :kick (seq :bd :_ :bd :_))` creates a per-track GainNode for `:kick`
- [ ] `(play :kick (->> (seq :bd :_ :bd :_) (fx :reverb 0.4)))` adds reverb only to the kick track
- [ ] `(play :lead (->> (scale :minor :c4 (seq 0 2 4 7)) (fx :delay :wet 0.3 :time 0.25)))` adds delay only to the lead track
- [ ] Kick and lead have independent, non-interfering effect chains
- [ ] Re-evaluating `(play :kick (seq :bd :_ :bd :_))` without `(fx ...)` removes the effect chain
- [ ] Global `(fx :reverb 0.4)` still works and affects the master bus
- [ ] `(stop)` cleans up all per-track nodes without console errors
- [ ] `(clear! :kick)` disconnects the kick track's GainNode and effects

### Sample playback control

- [ ] `(->> (sound :tabla 0) (rate 2.0))` plays the sample at double speed
- [ ] `(->> (sound :tabla 0) (rate 0.5))` plays the sample at half speed (lower pitch)
- [ ] `(->> (sound :tabla 0) (begin 0.5))` starts playback from the middle of the sample
- [ ] `(->> (sound :tabla 0) (begin 0.2) (end 0.8))` plays only the middle 60%
- [ ] `(->> (sound :tabla 0) (loop-sample true))` loops the sample
- [ ] `(rate 1.5)` returns a transformer; `((rate 1.5) (sound :tabla 0))` works
- [ ] Sample params chain with existing params: `(->> (sound :tabla 0) (rate 1.5) (amp 0.7))`
- [ ] Unit tests for `rate`, `begin`, `end*`, `loop-sample` pass (`npm run test:core`)

### Sidechain

- [ ] `(fx :sidechain :trigger :bd :amount 0.8 :release 0.1)` is loaded and active
- [ ] When `:bd` fires, the master bus gain visibly ducks and recovers
- [ ] Changing `:amount` to 0.0 disables the ducking effect
- [ ] Non-matching events (`:sd`, `:hh`) do not trigger the duck
- [ ] `(fx :off :sidechain)` bypasses the effect cleanly

### New synth voices

- [ ] `(saw :c4)` produces a sawtooth tone at middle C
- [ ] `(square :c4)` produces a square wave at middle C
- [ ] `(square :c4 :pw 0.25)` produces a pulse wave with 25% duty cycle (WASM + JS fallback via PeriodicWave)
- [ ] `(noise)` produces a burst of white noise
- [ ] `(fm :c4 :index 3 :ratio 2)` produces an FM tone
- [ ] All new voices work with `amp`, `attack`, `decay`, `pan`: `(->> (saw :c4) (amp 0.6) (decay 0.3))`
- [ ] All new voices work in both WASM and JS fallback (except FM in JS falls back to sine)
- [ ] `synth` transformer: `(->> (seq :c4 :eb4 :g4) (synth :saw))` applies sawtooth to a note sequence
- [ ] `synth` with opts: `(->> (seq :c3 :eb3) (synth :square :pw 0.25))` works
- [ ] `synth` composes with all param transformers: `(amp ...)`, `(decay ...)`, `(pan ...)`
- [ ] Raw `(seq :bd :sd)` without new features still works unchanged

### Composition

- [ ] Per-track effects + sample control + sidechain work together in a full arrangement
- [ ] `(play :drums ...)` and `(play :bass ...)` with independent per-track effects via `->>`
- [ ] All existing core tests still pass (`npm run test:core`)

### UI

- [ ] `rate`, `begin`, `end`, `loop-sample`, `saw`, `square`, `noise`, `fm` appear in autocomplete
- [ ] All eight tokens receive syntax highlighting as built-in names
- [ ] Context panel shows per-track effects when active
