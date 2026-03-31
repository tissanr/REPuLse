# Phase S — Synth Voices & Presets

## Goal

Eight new built-in synth voices and a library of named presets that are available from
the first keystroke, with zero boilerplate.

```lisp
;; Before — four built-in voices:
(->> (seq :c3 :eb3 :g3) (synth :saw) (decay 0.3))
(->> (seq :c3 :eb3 :g3) (synth :square :pw 0.3))
(->> (seq :c3 :eb3 :g3) (synth :fm :index 3))
(noise)

;; After — eight new voices:
(->> (seq :c3 :eb3 :g3) (synth :tri))
(->> (seq :c1 :eb1 :g1) (synth :sub))
(->> (seq :c3 :eb3 :g3) (synth :supersaw :voices 7 :detune 0.15))
(->> (seq :c3 :eb3 :g3) (synth :pwm :rate 0.5 :depth 0.3))
(->> (scale :minor :e3 (seq 0 2 4 7)) (synth :pluck :damp 0.6))
(->> (seq :c3 :e3 :g3) (synth :organ :drawbars [1.0 0.8 0.5 0.2 0.1 0.05]))
(->> (seq :a4 :c5 :e5) (synth :bell :ratio 3.5 :index 6))
(->> (slow 4 (chord :major :c4)) (synth :strings))

;; After — built-in presets (zero setup required):
(->> (scale :minor :c2 (seq 0 :_ 5 :_)) acid-bass)
(->> (scale :minor :c4 (seq 0 2 4 7)) pluck-melody)
(->> (slow 4 (chord :minor :a3)) pad)
(->> (seq :a5 :c5 :e5) bell-tone)
(->> (chord :major7 :c4) organ-tone)

;; stab:
(stab (seq :c4 :_ :eb4 :_))

;; list all preset names:
(presets)
;; => (:acid-bass :sub-bass :pluck-bass :reese :lead :soft-lead :fm-lead
;;     :pad :dark-pad :string-pad :pluck-melody :bell-tone :organ-tone
;;     :stab :noise-hit)
```

---

## Background

### Current voice architecture

Four built-in synth voices exist:

**In `packages/lisp/src/repulse/lisp/eval.cljs` — `make-env`:**
```clojure
"saw"    (fn [note]         {:note (unwrap note) :synth :saw})
"square" (fn [note & opts]  {:note n :synth :square :pw pw})
"noise"  (fn []             {:synth :noise})
"fm"     (fn [note & opts]  {:note n :synth :fm :index idx :ratio ratio})

"synth"  (fn [voice-arg & rest-args]
           ;; Generic dispatcher: (synth :saw pat), (synth :supersaw :detune 0.1 pat)
           ;; Returns a (pat → pat) transformer when called with no pattern.
           ;; Calls core/fmap to annotate each event: {:note v :synth voice :freq hz :opts...}
           ...)
```

**In `app/src/repulse/audio.cljs` — `play-event`:**
```clojure
;; Dispatches on {:note n :synth :keyword} maps:
(= synth :saw)    → worklet-trigger-v2!("saw:{hz}", ...) or make-saw
(= synth :square) → worklet-trigger-v2!("square:{hz}:{pw}", ...) or make-square
(= synth :fm)     → worklet-trigger-v2!("fm:{hz}:{idx}:{ratio}", ...) or make-sine
;; (:synth :noise) has its own top-level branch (no :note key)
```

**In `packages/audio/src/lib.rs` — WASM engine:**
Voices: `Kick`, `Snare`, `Hihat`, `Tone` (sine), `Saw`, `Square`, `Noise`, `FM`.
String dispatch: `"saw:{hz}"`, `"square:{hz}:{pw}"`, `"fm:{hz}:{idx}:{ratio}"`,
`"noise"`, bare number → `Tone`, `"bd"`, `"sd"`, `"hh"`, `"oh"`.

**User-defined synths** (`defsynth`) use the registry in `app/src/repulse/synth.cljs`
and are entirely separate from the built-in voice dispatch.

### Current preset mechanism

`comp` (Clojure's function composition) is already in `make-env`. Curried parameter
functions (`amp`, `decay`, `attack`, `synth`) return `(pat → pat)` transformers when
called with no pattern. So `(comp (synth :saw) (amp 0.7))` is valid today — it creates
a reusable transformer. There are no built-in named presets yet; users must define their
own with `def`.

### What is missing

- `:tri`, `:sub`, `:supersaw`, `:pwm`, `:pluck`, `:organ`, `:bell`, `:strings` voices
- Built-in presets in the initial env
- `(presets)` function
- Hover docs and autocomplete entries for all new names

---

## Design

### 1. Where each new voice lives

| Voice | WASM | JS fallback | Notes |
|---|---|---|---|
| `:tri` | ✓ New `Voice::Tri` variant | `make-tri` | Simple triangle oscillator |
| `:sub` | ✓ New `Voice::Sub` variant (freq÷2) | `make-sub` | Sine one octave below |
| `:bell` | ✓ Reuse FM (`"fm:{hz}:{idx}:{ratio}"`) | `make-bell` (sine) | FM with preset params |
| `:supersaw` | ✗ Fallback to single saw via worklet | `make-supersaw` | Multi-osc too complex for WASM |
| `:pwm` | ✗ Fallback to square via worklet | `make-pwm` | LFO modulation not in WASM |
| `:pluck` | ✗ Fallback to tone via worklet | `make-pluck` | Karplus-Strong, JS only |
| `:organ` | ✗ Fallback to tone via worklet | `make-organ` | Additive, JS only |
| `:strings` | ✗ Fallback to saw via worklet | `make-strings` | Supersaw variant, JS only |

For voices that lack a dedicated WASM implementation, when the worklet is ready,
send a simplified trigger (`"saw:hz"` for supersaw/strings, `"{hz}"` for pluck/organ,
`"square:hz:0.5"` for pwm) and use the full JS implementation as the worklet fallback.
The trigger logic ensures that if the worklet is ready it handles it; since it won't
recognise the new string formats it will do nothing. **Therefore: always call the JS
maker directly for JS-only voices, bypassing the worklet entirely.** Do not try to send
these voices to the worklet.

### 2. How new voices enter the system

Each new voice follows the same three-step pattern as the existing ones:

**Step 1 — Eval binding** (`eval.cljs make-env`):
```clojure
;; Shorthand convenience form: (supersaw :c3) → {:note :c3 :synth :supersaw}
"tri"      (fn [note] {:note (unwrap note) :synth :tri})
"sub"      (fn [note] {:note (unwrap note) :synth :sub})
"bell"     (fn [note & opts] ...)   ; same pattern as "fm"
;; Complex voices: only usable via (synth :voice pat) — no shorthand needed
;; (but add them to the synth dispatcher's known-voice list for validation)
```

**Step 2 — Audio dispatch** (`audio.cljs play-event`):
Add `(= synth :tri)`, `(= synth :sub)` etc. branches inside the
`(and (map? value) (:note value))` cond, before the generic `(keyword? note)` branch.

**Step 3 — WASM voice** (`lib.rs`, only for `:tri` and `:sub`):
Add `Voice::Tri` and `Voice::Sub` variants alongside the existing `Voice::Saw`.

### 3. Built-in presets

Presets are evaluated as Lisp strings during `ensure-env!` initialisation. After the
env atom is built, call `lisp/eval-string` on each preset definition and merge the
resulting `*defs*` bindings into the env. This reuses the exact same `def`, `comp`,
`synth`, `amp`, `decay`, `attack` vocabulary already in the env — no new mechanisms needed.

A separate atom `builtin-preset-names` in `app.cljs` holds a vector of keyword names
(`:acid-bass`, `:sub-bass` …) so `(presets)` can return them.

Preset bindings appear in the context panel under "Bindings" with type annotation `fn`
(they are composed functions). They are user-overridable: `(def pad ...)` replaces the
built-in by writing to `*defs*` in the normal way.

---

## Implementation

### 1. `packages/audio/src/lib.rs` — new WASM voices `:tri` and `:sub`

#### 1a. Add `Voice::Tri` and `Voice::Sub`

Both variants have the same fields as `Voice::Saw`. Copy the `Saw` variant definition
for each, renaming the enum arm:

```rust
Tri {
    phase:      f64,
    freq:       f64,
    amp:        f32,
    gain:       f32,
    gain_decay: f32,
    attack_inc: f32,
    in_attack:  bool,
},
Sub {   // sine at freq / 2
    phase:      f64,
    freq:       f64,
    amp:        f32,
    gain:       f32,
    gain_decay: f32,
    attack_inc: f32,
    in_attack:  bool,
},
```

#### 1b. `tick` for `Voice::Tri`

Triangle wave from phase (same envelope logic as `Voice::Saw`):

```rust
Voice::Tri { phase, freq, amp, gain, gain_decay, attack_inc, in_attack } => {
    // envelope
    if *in_attack {
        *gain += *attack_inc;
        if *gain >= *amp { *gain = *amp; *in_attack = false; }
    } else {
        *gain *= *gain_decay;
    }
    // triangle: |2 * (phase % 1) - 1| mapped to [-1, 1]
    let p = *phase % 1.0;
    let s = if p < 0.5 { 4.0 * p - 1.0 } else { 3.0 - 4.0 * p } as f32;
    *phase += freq / sample_rate as f64;
    s * *gain
}
```

#### 1c. `tick` for `Voice::Sub`

Identical to `Voice::Tone` but with `freq / 2.0` in the phase increment:

```rust
Voice::Sub { phase, freq, amp, gain, gain_decay, attack_inc, in_attack } => {
    // same envelope logic as Tone/Saw
    // ...
    let s = (2.0 * std::f64::consts::PI * *phase).sin() as f32;
    *phase += (freq / 2.0) / sample_rate as f64;   // ← one octave lower
    s * *gain
}
```

#### 1d. `is_silent` — add new arms

```rust
Voice::Tri { gain, in_attack, .. }
| Voice::Sub { gain, in_attack, .. } => !*in_attack && *gain < 1e-5,
```

#### 1e. Dispatch in `activate_v2`

```rust
} else if value.starts_with("tri:") {
    let hz: f64 = value[4..].parse().unwrap_or(440.0);
    Voice::Tri {
        phase: 0.0, freq: hz, amp: p.amp * 0.5,
        gain: 0.0, gain_decay: decay_rate(p.decay, sr),
        attack_inc: p.amp * 0.5 / (p.attack * sr).max(1.0),
        in_attack: true,
    }
} else if value.starts_with("sub:") {
    let hz: f64 = value[4..].parse().unwrap_or(110.0);
    Voice::Sub {
        phase: 0.0, freq: hz, amp: p.amp * 0.6,  // slightly louder for bass
        gain: 0.0, gain_decay: decay_rate(p.decay, sr),
        attack_inc: p.amp * 0.6 / (p.attack * sr).max(1.0),
        in_attack: true,
    }
}
```

After changing lib.rs, run `npm run build:wasm`.

---

### 2. `app/src/repulse/audio.cljs` — JS maker functions and dispatch

#### 2a. `make-tri`

Identical to `make-saw` except `(set! (.-type osc) "triangle")`. Add directly after
`make-saw`:

```clojure
(defn- make-tri [ac t freq dur amp attack pan dest]
  (let [osc    (.createOscillator ac)
        gain   (.createGain ac)
        panner (.createStereoPanner ac)
        peak   (* 0.5 (float amp))
        atk    (max 0.001 (float attack))
        stop-t (+ t atk (float dur))]
    (set! (.-type osc) "triangle")
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
```

#### 2b. `make-sub`

Same as `make-sine` but frequency is halved:

```clojure
(defn- make-sub [ac t freq dur amp attack pan dest]
  (make-sine ac t (/ freq 2.0) dur amp attack pan dest))
```

#### 2c. `make-supersaw`

Multiple detuned sawtooth oscillators mixed together with slight stereo spread:

```clojure
(defn- make-supersaw [ac t freq dur amp attack pan dest voices detune]
  (let [n      (max 3 (min 7 (int voices)))
        peak   (* (/ 0.5 n) (float amp))   ; normalise amplitude
        atk    (max 0.001 (float attack))
        stop-t (+ t atk (float dur))
        mixer  (.createGain ac)
        panner (.createStereoPanner ac)]
    (.setValueAtTime (.-gain mixer) 1.0 t)
    (.setValueAtTime (.-pan panner) (float pan) t)
    (.connect mixer panner)
    (.connect panner dest)
    (dotimes [i n]
      (let [offset (- (* detune (- i (/ (dec n) 2.0))))  ; spread around centre
            f      (* freq (js/Math.pow 2.0 (/ offset 12.0)))
            osc    (.createOscillator ac)
            g      (.createGain ac)]
        (set! (.-type osc) "sawtooth")
        (.setValueAtTime (.-frequency osc) f t)
        (.setValueAtTime (.-gain g) 0.0001 t)
        (.linearRampToValueAtTime (.-gain g) peak (+ t atk))
        (.exponentialRampToValueAtTime (.-gain g) 0.0001 stop-t)
        (.connect osc g)
        (.connect g mixer)
        (.start osc t)
        (.stop osc stop-t)))))
```

#### 2d. `make-pwm`

Two detuned sawtooth oscillators with opposite polarity — their difference creates a
variable-duty pulse. A slow LFO OscillatorNode modulates the frequency of one saw,
producing the characteristic slow filter-like sweep:

```clojure
(defn- make-pwm [ac t freq dur amp attack pan dest rate depth]
  (let [peak    (* 0.4 (float amp))
        atk     (max 0.001 (float attack))
        stop-t  (+ t atk (float dur))
        lfo-hz  (float rate)
        lfo-amt (* freq depth 0.5)   ; in Hz — depth fraction of freq / 2
        ;; Main saw
        osc-a   (.createOscillator ac)
        ;; Second saw, slightly detuned by LFO
        osc-b   (.createOscillator ac)
        ;; LFO
        lfo     (.createOscillator ac)
        lfo-g   (.createGain ac)
        ;; Output envelope
        gain    (.createGain ac)
        panner  (.createStereoPanner ac)]
    (set! (.-type osc-a) "sawtooth")
    (set! (.-type osc-b) "sawtooth")
    (set! (.-type lfo) "sine")
    (.setValueAtTime (.-frequency osc-a) freq t)
    (.setValueAtTime (.-frequency osc-b) freq t)
    (.setValueAtTime (.-frequency lfo) lfo-hz t)
    (.setValueAtTime (.-gain lfo-g) lfo-amt t)
    ;; Connect LFO → osc-b frequency modulation
    (.connect lfo lfo-g)
    (.connect lfo-g (.-frequency osc-b))
    ;; Invert osc-b (gain of -1) and add to osc-a
    (let [inv (.createGain ac)]
      (.setValueAtTime (.-gain inv) -1.0 t)
      (.connect osc-b inv)
      (.connect osc-a gain)
      (.connect inv gain))
    (.setValueAtTime (.-gain gain) 0.0001 t)
    (.linearRampToValueAtTime (.-gain gain) peak (+ t atk))
    (.exponentialRampToValueAtTime (.-gain gain) 0.0001 stop-t)
    (.setValueAtTime (.-pan panner) (float pan) t)
    (.connect gain panner)
    (.connect panner dest)
    (doto osc-a (.start t) (.stop stop-t))
    (doto osc-b (.start t) (.stop stop-t))
    (doto lfo   (.start t) (.stop stop-t))))
```

#### 2e. `make-pluck`

Karplus-Strong via a pre-rendered buffer. Generate the KS algorithm samples in JS,
write them to an `AudioBuffer`, and play them as a `AudioBufferSourceNode`. This avoids
the Web Audio feedback loop limitation:

```clojure
(defn- make-pluck [ac t freq dur amp damp pan dest]
  (let [sr        (.-sampleRate ac)
        n-samples (max 1 (int (* sr (float dur))))
        delay-len (max 2 (int (/ sr freq)))   ; samples per period = 1/freq
        buf       (.createBuffer ac 1 n-samples sr)
        data      (.getChannelData buf 0)
        ;; Karplus-Strong:
        ;; 1. Fill delay buffer with white noise
        ;; 2. For each sample: average with previous, apply damping
        ring      (js/Float32Array. delay-len)
        _         (dotimes [i delay-len]
                    (aset ring i (- (* 2.0 (Math/random)) 1.0)))
        damp-k    (float (- 1.0 (* 0.5 (max 0.0 (min 1.0 (float damp))))))]
    (loop [i 0]
      (when (< i n-samples)
        (let [j    (mod i delay-len)
              j1   (mod (inc j) delay-len)
              prev (aget ring j)
              next (aget ring j1)
              out  (* damp-k (+ prev next) 0.5)]
          (aset ring j1 out)
          (aset data i (* out (float amp))))
        (recur (inc i))))
    (let [src    (.createBufferSource ac)
          panner (.createStereoPanner ac)]
      (set! (.-buffer src) buf)
      (.setValueAtTime (.-pan panner) (float pan) t)
      (.connect src panner)
      (.connect panner dest)
      (.start src t)
      (.stop src (+ t (float dur))))))
```

#### 2f. `make-organ`

Sum of sine partials at harmonic frequencies with controllable drawbar levels:

```clojure
(defn- make-organ [ac t freq dur amp drawbars perc pan dest]
  (let [atk      0.003
        stop-t   (+ t atk (float dur))
        n-bars   (count drawbars)
        mixer    (.createGain ac)
        panner   (.createStereoPanner ac)]
    (.setValueAtTime (.-gain mixer) 1.0 t)
    (.setValueAtTime (.-pan panner) (float pan) t)
    (.connect mixer panner)
    (.connect panner dest)
    ;; Add harmonic partials
    (dotimes [i n-bars]
      (let [level (nth drawbars i)
            f     (* freq (inc i))   ; harmonics 1,2,3,4,5,6
            osc   (.createOscillator ac)
            g     (.createGain ac)]
        (set! (.-type osc) "sine")
        (.setValueAtTime (.-frequency osc) f t)
        (.setValueAtTime (.-gain g) 0.0001 t)
        (.linearRampToValueAtTime (.-gain g) (* (float amp) (float level) 0.15) (+ t atk))
        (.exponentialRampToValueAtTime (.-gain g) 0.0001 stop-t)
        (.connect osc g)
        (.connect g mixer)
        (.start osc t)
        (.stop osc stop-t)))
    ;; Percussive click: short burst of 4th harmonic
    (when (> perc 0.01)
      (let [click  (.createOscillator ac)
            cg     (.createGain ac)]
        (set! (.-type click) "sine")
        (.setValueAtTime (.-frequency click) (* freq 4) t)
        (.setValueAtTime (.-gain cg) (* (float amp) (float perc) 0.3) t)
        (.exponentialRampToValueAtTime (.-gain cg) 0.0001 (+ t 0.04))
        (.connect click cg)
        (.connect cg mixer)
        (.start click t)
        (.stop click (+ t 0.04))))))
```

#### 2g. `make-bell`

FM synthesis with bell-preset parameters. Reuse the pattern from `make-sine` for
envelope, but use two oscillators (carrier + modulator):

```clojure
(defn- make-bell [ac t freq dur amp ratio index pan dest]
  (let [carrier (.createOscillator ac)
        mod-osc (.createOscillator ac)
        mod-g   (.createGain ac)
        gain    (.createGain ac)
        panner  (.createStereoPanner ac)
        c-freq  (double freq)
        m-freq  (* c-freq (double ratio))
        m-depth (* c-freq (double index))
        peak    (* 0.4 (float amp))
        stop-t  (+ t (float dur))]
    (set! (.-type carrier) "sine")
    (set! (.-type mod-osc) "sine")
    (.setValueAtTime (.-frequency carrier) c-freq t)
    (.setValueAtTime (.-frequency mod-osc) m-freq t)
    (.setValueAtTime (.-gain mod-g) m-depth t)
    ;; FM modulator → carrier frequency
    (.connect mod-osc mod-g)
    (.connect mod-g (.-frequency carrier))
    ;; Exponential decay envelope on carrier output
    (.setValueAtTime (.-gain gain) peak t)
    (.exponentialRampToValueAtTime (.-gain gain) 0.0001 stop-t)
    (.setValueAtTime (.-pan panner) (float pan) t)
    (.connect carrier gain)
    (.connect gain panner)
    (.connect panner dest)
    (.start carrier t) (.stop carrier stop-t)
    (.start mod-osc t) (.stop mod-osc stop-t)))
```

#### 2h. `make-strings`

Thin wrapper around `make-supersaw` with a slow attack baked in:

```clojure
(defn- make-strings [ac t freq dur amp attack pan dest voices detune]
  ;; strings = supersaw with slow attack built into the voice
  ;; pattern-level (attack N) further modifies this
  (let [str-attack (max (float attack) 0.25)]   ; minimum 250 ms attack
    (make-supersaw ac t freq dur amp str-attack pan dest voices detune)))
```

#### 2i. Dispatch branches in `play-event`

Inside the `(and (map? value) (:note value))` cond, add new arms **after** the existing
`:saw`, `:square`, `:fm` arms and **before** the generic `(keyword? note)` branch:

```clojure
(= synth :tri)
(let [hz (if (theory/note-keyword? note) (theory/note->hz note) (double note))]
  (or (when-not offline?
        (worklet-trigger-v2! (str "tri:" hz) t amp-v attack-v decay-v pan-v dest))
      (make-tri ac t hz decay-v amp-v attack-v pan-v dest)))

(= synth :sub)
(let [hz (if (theory/note-keyword? note) (theory/note->hz note) (double note))]
  (or (when-not offline?
        (worklet-trigger-v2! (str "sub:" hz) t amp-v attack-v decay-v pan-v dest))
      (make-sub ac t hz decay-v amp-v attack-v pan-v dest)))

(= synth :supersaw)
(let [hz      (if (theory/note-keyword? note) (theory/note->hz note) (double note))
      voices' (double (or (:voices value) 5))
      detune' (double (or (:detune value) 0.1))]
  ;; JS only — no WASM equivalent; skip worklet
  (make-supersaw ac t hz decay-v amp-v attack-v pan-v dest voices' detune'))

(= synth :pwm)
(let [hz    (if (theory/note-keyword? note) (theory/note->hz note) (double note))
      rate  (double (or (:rate value) 0.5))
      depth (double (or (:depth value) 0.3))]
  (make-pwm ac t hz decay-v amp-v attack-v pan-v dest rate depth))

(= synth :pluck)
(let [hz   (if (theory/note-keyword? note) (theory/note->hz note) (double note))
      damp (double (or (:damp value) 0.5))]
  (make-pluck ac t hz decay-v amp-v damp pan-v dest))

(= synth :organ)
(let [hz       (if (theory/note-keyword? note) (theory/note->hz note) (double note))
      drawbars (or (:drawbars value) [1.0 0.5 0.3 0.1 0.05 0.02])
      perc     (double (or (:perc value) 0.3))]
  (make-organ ac t hz decay-v amp-v drawbars perc pan-v dest))

(= synth :bell)
(let [hz    (if (theory/note-keyword? note) (theory/note->hz note) (double note))
      ratio (double (or (:ratio value) 3.5))
      index (double (or (:index value) 6.0))
      dur   (double (or (:decay value) 2.0))]   ; bell has longer inherent decay
  ;; Try WASM FM with bell params, fall back to JS bell
  (or (when-not offline?
        (worklet-trigger-v2! (str "fm:" hz ":" index ":" ratio) t amp-v 0.001 dur pan-v dest))
      (make-bell ac t hz dur amp-v ratio index pan-v dest)))

(= synth :strings)
(let [hz      (if (theory/note-keyword? note) (theory/note->hz note) (double note))
      voices' (double (or (:voices value) 5))
      detune' (double (or (:detune value) 0.08))]
  (make-strings ac t hz decay-v amp-v attack-v pan-v dest voices' detune'))
```

---

### 3. `packages/lisp/src/repulse/lisp/eval.cljs` — new voice bindings

In `make-env`, in the "Built-in synth voices" section, add shorthand functions for
the simpler new voices (`:tri`, `:sub`) and a reference note for the `:bell` shorthand.
The complex voices (`:supersaw`, `:pwm`, `:pluck`, `:organ`, `:strings`) are only
accessible via `(synth :voice-kw pat)`, not through a bare shorthand — their parameter
sets are too rich for a single convenience function.

```clojure
;; After the existing "saw", "square", "noise", "fm" entries:

"tri"   (fn [note]
          (let [n (unwrap note)]
            {:note n :synth :tri}))

"sub"   (fn [note]
          (let [n (unwrap note)]
            {:note n :synth :sub}))

"bell"  (fn [note & opts]
          (let [n     (unwrap note)
                omap  (apply hash-map (map unwrap opts))
                ratio (get omap :ratio 3.5)
                index (get omap :index 6.0)]
            {:note n :synth :bell :ratio ratio :index index}))
```

---

### 4. `app/src/repulse/app.cljs` — built-in presets

#### 4a. Preset name registry

Add near the top of the `app.cljs` namespace (alongside other `defonce` atoms):

```clojure
(def ^:private builtin-preset-names
  [:acid-bass :sub-bass :pluck-bass :reese
   :lead :soft-lead :fm-lead
   :pad :dark-pad :string-pad
   :pluck-melody :bell-tone :organ-tone
   :stab :noise-hit])
```

#### 4b. Preset Lisp source

Define a private var with the preset definitions as a single Lisp string:

```clojure
(def ^:private preset-source
  "(def acid-bass    (comp (synth :saw)    (amp 0.7) (attack 0.005) (decay 0.15)))
   (def sub-bass     (comp (synth :sub)    (amp 0.8) (decay 0.4)))
   (def pluck-bass   (comp (synth :pluck :damp 0.8) (amp 0.7) (decay 0.2)))
   (def reese        (comp (synth :supersaw :voices 3 :detune 0.03) (amp 0.6) (decay 0.4)))
   (def lead         (comp (synth :saw)    (amp 0.6) (attack 0.01) (decay 0.3)))
   (def soft-lead    (comp (synth :tri)    (amp 0.5) (attack 0.05) (decay 0.5)))
   (def fm-lead      (comp (synth :fm :index 2 :ratio 1.5) (amp 0.5) (decay 0.4)))
   (def pad          (comp (synth :supersaw :detune 0.12 :voices 5) (amp 0.3) (attack 0.4) (decay 2.0)))
   (def dark-pad     (comp (synth :pwm :rate 0.3) (amp 0.3) (attack 0.5) (decay 2.5)))
   (def string-pad   (comp (synth :strings) (amp 0.3) (decay 2.0)))
   (def pluck-melody (comp (synth :pluck :damp 0.4) (amp 0.6) (decay 0.5)))
   (def bell-tone    (comp (synth :bell)   (amp 0.4) (decay 1.5)))
   (def organ-tone   (comp (synth :organ)  (amp 0.5) (decay 0.8)))
   (def stab         (comp (synth :supersaw :voices 7 :detune 0.2) (amp 0.8) (attack 0.001) (decay 0.08)))
   (def noise-hit    (comp (synth :noise)  (amp 0.5) (decay 0.05)))")
```

#### 4c. Inject presets in `ensure-env!`

In `ensure-env!`, after the `reset! env-atom` block, evaluate the preset source and
merge the resulting `*defs*` into the env atom:

```clojure
;; After (reset! env-atom ...) in ensure-env!:
(let [env @env-atom
      {:keys [result error]} (lisp/eval-string preset-source env)]
  (when error
    (js/console.warn "[REPuLse] preset init error:" error))
  ;; Presets write to *defs* atom inside env — no further action needed.
  ;; The context panel reads *defs* directly.
  nil)
```

Since `eval-string` with multiple top-level `def` forms mutates the `*defs*` atom
inside `env`, and `env-atom` already holds a reference to the same `env` map (with the
same `*defs*` atom), the presets automatically appear in the context panel.

#### 4d. `(presets)` function

Add to the `assoc` block inside `ensure-env!`:

```clojure
"presets"
(fn []
  (str "=> (" (cljs.core/str/join " " (map #(str ":" (name %)) builtin-preset-names)) ")"))
```

---

### 5. `app/src/repulse/lisp-lang/repulse-lisp.grammar`

Add to `BuiltinName`:

```
"tri" | "sub" | "bell" |
"supersaw" | "pwm" | "pluck" | "organ" | "strings" |
"presets" |
"acid-bass" | "sub-bass" | "pluck-bass" | "reese" |
"lead" | "soft-lead" | "fm-lead" |
"pad" | "dark-pad" | "string-pad" |
"pluck-melody" | "bell-tone" | "organ-tone" |
"stab" | "noise-hit" |
```

After editing the grammar, **run `npm run gen:grammar`** to regenerate `parser.js` and
`parser.terms.js`. Commit both files.

---

### 6. `app/src/repulse/lisp-lang/completions.js`

Add entries for new voices (in the existing "Synth voices" block):

```javascript
// --- New built-in voices ---
{ label: "tri",      type: "function", detail: "(tri :c4) — triangle wave oscillator; works with amp/attack/decay/pan" },
{ label: "sub",      type: "function", detail: "(sub :c2) — sub-bass sine one octave below played pitch" },
{ label: "bell",     type: "function", detail: "(bell :c5) — FM bell; opts: :ratio (default 3.5) :index (default 6.0)" },
{ label: "supersaw", type: "function", detail: "(synth :supersaw pat) — detuned saw stack; opts: :voices (5) :detune (0.1) :mix (0.5)" },
{ label: "pwm",      type: "function", detail: "(synth :pwm pat) — pulse-width modulation; opts: :rate (0.5) :depth (0.3) :pw (0.5)" },
{ label: "pluck",    type: "function", detail: "(synth :pluck pat) — Karplus-Strong plucked string; opts: :damp (0.5) :release (0.5)" },
{ label: "organ",    type: "function", detail: "(synth :organ pat) — additive Hammond organ; opts: :drawbars ([1.0 0.5 0.3 0.1 0.05 0.02]) :perc (0.3)" },
{ label: "strings",  type: "function", detail: "(synth :strings pat) — ensemble strings (slow attack supersaw); opts: :voices (5) :detune (0.08) :attack (0.3)" },
// --- Built-in presets ---
{ label: "presets",      type: "function", detail: "(presets) — list all built-in preset names" },
{ label: "acid-bass",    type: "function", detail: "acid-bass — preset: saw, amp 0.7, attack 0.005, decay 0.15" },
{ label: "sub-bass",     type: "function", detail: "sub-bass — preset: sub, amp 0.8, decay 0.4" },
{ label: "pluck-bass",   type: "function", detail: "pluck-bass — preset: pluck damp 0.8, amp 0.7, decay 0.2" },
{ label: "reese",        type: "function", detail: "reese — preset: supersaw 3 voices, detune 0.03, amp 0.6" },
{ label: "lead",         type: "function", detail: "lead — preset: saw, amp 0.6, attack 0.01, decay 0.3" },
{ label: "soft-lead",    type: "function", detail: "soft-lead — preset: tri, amp 0.5, attack 0.05, decay 0.5" },
{ label: "fm-lead",      type: "function", detail: "fm-lead — preset: fm index 2 ratio 1.5, amp 0.5, decay 0.4" },
{ label: "pad",          type: "function", detail: "pad — preset: supersaw 5 voices, amp 0.3, attack 0.4, decay 2.0" },
{ label: "dark-pad",     type: "function", detail: "dark-pad — preset: pwm rate 0.3, amp 0.3, attack 0.5, decay 2.5" },
{ label: "string-pad",   type: "function", detail: "string-pad — preset: strings, amp 0.3, decay 2.0" },
{ label: "pluck-melody", type: "function", detail: "pluck-melody — preset: pluck damp 0.4, amp 0.6, decay 0.5" },
{ label: "bell-tone",    type: "function", detail: "bell-tone — preset: bell, amp 0.4, decay 1.5" },
{ label: "organ-tone",   type: "function", detail: "organ-tone — preset: organ, amp 0.5, decay 0.8" },
{ label: "stab",         type: "function", detail: "stab — preset: supersaw 7 voices, amp 0.8, attack 0.001, decay 0.08" },
{ label: "noise-hit",    type: "function", detail: "noise-hit — preset: noise, amp 0.5, decay 0.05" },
```

---

### 7. `docs/USAGE.md` — documentation updates

#### 7a. Update the "Available voices" table in "Per-event parameters"

Find the existing voices table and extend it with the 8 new rows:

| Voice | Description | Options |
|---|---|---|
| `:tri` | Triangle wave oscillator | — |
| `:sub` | Sub-bass sine (one octave below played pitch) | — |
| `:supersaw` | Detuned sawtooth stack | `:voices` (3–7, default 5), `:detune` (default 0.1), `:mix` (default 0.5) |
| `:pwm` | Pulse-width modulation with LFO | `:rate` LFO Hz (default 0.5), `:depth` mod amount (default 0.3), `:pw` centre PW (default 0.5) |
| `:pluck` | Karplus-Strong plucked string | `:damp` (0–1, default 0.5), `:release` decay secs (default 0.5) |
| `:organ` | Additive Hammond-style organ | `:drawbars` (vector of 6 levels, default `[1.0 0.5 0.3 0.1 0.05 0.02]`), `:perc` click amount (default 0.3) |
| `:bell` | FM bell preset | `:ratio` (default 3.5), `:index` (default 6.0), `:decay` (default 2.0) |
| `:strings` | Ensemble strings (supersaw with slow attack) | `:voices` (default 5), `:detune` (default 0.08), `:attack` (default 0.3) |

#### 7b. New section "Built-in presets"

Add after the "Named voice presets" subsection (the one under Per-event parameters
that shows `def` + `comp` examples):

```markdown
### Built-in presets

REPuLse ships 15 ready-to-use presets. They are available immediately — no `def` needed.
Apply them like any other `(pat → pat)` transformer:

    (->> (scale :minor :c2 (seq 0 :_ 5 :_)) acid-bass)
    (->> (scale :minor :a4 (seq 0 2 4 7))   pluck-melody)
    (->> (slow 4 (chord :minor :a3))         pad)

    ; Use in stack
    (stack
      (->> (scale :minor :c2 (seq 0 :_ 5 :_)) acid-bass)
      (->> (scale :minor :c4 (seq 0 2 4 7))   lead)
      (->> (slow 2 (chord :minor :a3))         string-pad))

    ; Combine with other transforms
    (every 4 (fast 2) (->> (scale :minor :e4 (seq 0 :_ 2 :_)) stab))

| Preset | Voice | Character |
|---|---|---|
| `acid-bass` | `:saw` | Punchy acid bass (attack 5 ms, decay 150 ms) |
| `sub-bass` | `:sub` | Deep sub (decay 400 ms) |
| `pluck-bass` | `:pluck` | Heavily damped plucked bass |
| `reese` | `:supersaw` | Dark reese bass (3 voices, tight detune) |
| `lead` | `:saw` | Bright lead (10 ms attack, 300 ms decay) |
| `soft-lead` | `:tri` | Mellow lead (50 ms attack) |
| `fm-lead` | `:fm` | FM lead (index 2, ratio 1.5) |
| `pad` | `:supersaw` | Lush pad (400 ms attack, 2 s decay) |
| `dark-pad` | `:pwm` | Evolving dark pad (slow LFO) |
| `string-pad` | `:strings` | Soft ensemble strings |
| `pluck-melody` | `:pluck` | Guitar-like melodic pluck |
| `bell-tone` | `:bell` | Bright bell with long decay |
| `organ-tone` | `:organ` | Jazz organ tone |
| `stab` | `:supersaw` | Loud chord stab (1 ms attack, 80 ms decay) |
| `noise-hit` | `:noise` | Short noise burst |

All presets can be overridden with `def`:

    (def pad (comp (synth :strings :voices 7) (amp 0.4) (attack 0.6)))

List all preset names:

    (presets)
    ;; => (:acid-bass :sub-bass :pluck-bass :reese :lead :soft-lead :fm-lead
    ;;     :pad :dark-pad :string-pad :pluck-melody :bell-tone :organ-tone
    ;;     :stab :noise-hit)
```

#### 7c. Add examples to the "Examples" section

```markdown
### Preset-driven full track

```lisp
(bpm 130)

(track :kick  (seq :bd :_ :bd :_))
(track :snare (seq :_ :sd :_ :sd))
(track :hats  (fast 2 (seq :hh :_)))
(track :bass  (->> (scale :minor :c2 (seq 0 :_ 5 :_)) acid-bass))
(track :lead  (->> (scale :minor :c4 (seq 0 2 4 7))   lead))
(track :pad   (->> (slow 4 (chord :minor :a3))         pad))
```

### New voices showcase

```lisp
; Detuned supersaw chord
(->> (slow 2 (chord :minor7 :d4)) (synth :supersaw :voices 7 :detune 0.15) (amp 0.3) (attack 0.3))

; Karplus-Strong guitar melody
(->> (scale :minor :e4 (seq 0 2 4 7 4 2)) (synth :pluck :damp 0.4) (amp 0.7))

; Additive organ chords
(->> (slow 2 (chord :major7 :c4)) (synth :organ :drawbars [1.0 0.8 0.6 0.4 0.2 0.1]) (amp 0.4))

; Bell melody with long decay
(->> (scale :major :c5 (seq 0 :_ 2 :_ 4 :_)) (synth :bell :ratio 4.0 :index 8) (amp 0.4))

; PWM bass with slow modulation
(->> (scale :minor :c2 (seq 0 :_ :_ 5)) (synth :pwm :rate 0.2 :depth 0.4) (amp 0.6) (decay 0.5))
```
```

---

### 8. Tests

#### 8a. JS voice rendering tests (new file)
`packages/core/test/repulse/synth_voices_test.cljs`

These tests use the Lisp evaluator to verify that the new voices produce correctly
structured event maps. No Web Audio context is needed.

```clojure
(ns repulse.synth-voices-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.core :as core]))

(defn- make-test-env []
  (leval/make-env (fn [] nil) (fn [_] nil)))

(defn- eval-str [src env]
  (:result (lisp/eval-string src env)))

(defn- query-one [pat]
  (first (core/query pat {:start [0 1] :end [1 1]})))

(deftest tri-voice
  (let [env (make-test-env)
        pat (eval-str "(synth :tri (pure :c4))" env)
        ev  (query-one pat)]
    (is (= :tri (:synth (:value ev))))))

(deftest sub-voice
  (let [env (make-test-env)
        pat (eval-str "(synth :sub (pure :c2))" env)
        ev  (query-one pat)]
    (is (= :sub (:synth (:value ev))))))

(deftest supersaw-voice-with-params
  (let [env (make-test-env)
        pat (eval-str "(synth :supersaw :voices 7 :detune 0.2 (pure :c3))" env)
        ev  (query-one pat)]
    (is (= :supersaw (:synth (:value ev))))
    (is (= 7 (:voices (:value ev))))
    (is (= 0.2 (:detune (:value ev))))))

(deftest pluck-voice-with-damp
  (let [env (make-test-env)
        pat (eval-str "(synth :pluck :damp 0.7 (pure :e4))" env)
        ev  (query-one pat)]
    (is (= :pluck (:synth (:value ev))))
    (is (= 0.7 (:damp (:value ev))))))

(deftest bell-shorthand
  (let [env (make-test-env)
        pat (eval-str "(synth :bell (pure :a5))" env)
        ev  (query-one pat)]
    (is (= :bell (:synth (:value ev))))))

(deftest organ-defaults
  (let [env (make-test-env)
        pat (eval-str "(synth :organ (pure :c4))" env)
        ev  (query-one pat)]
    (is (= :organ (:synth (:value ev))))))

(deftest tri-shorthand-fn
  (let [env (make-test-env)
        v   (eval-str "(tri :c4)" env)]
    (is (= :tri (:synth v)))
    (is (= :c4  (:note v)))))

(deftest sub-shorthand-fn
  (let [env (make-test-env)
        v   (eval-str "(sub :c2)" env)]
    (is (= :sub (:synth v)))))
```

#### 8b. Preset structure tests (new file)
`packages/core/test/repulse/presets_test.cljs`

These tests verify preset structure without audio. They require the presets to have
been injected into the env, so they go in the app-level test suite (not the pure core
tests). Skip if the test runner doesn't have app-level env available — the primary
verification of presets is through the UI.

Instead, test that the Lisp expressions in preset-source evaluate cleanly in a
make-env context:

```clojure
(ns repulse.presets-test
  (:require [cljs.test :refer-macros [deftest is]]
            [repulse.lisp.core :as lisp]
            [repulse.lisp.eval :as leval]
            [repulse.core :as core]))

(defn- env-with-presets []
  (let [env (leval/make-env (fn [] nil) (fn [_] nil))
        preset-src "(def acid-bass (comp (synth :saw) (amp 0.7) (attack 0.005) (decay 0.15)))
                    (def pad (comp (synth :supersaw :detune 0.12 :voices 5) (amp 0.3) (attack 0.4) (decay 2.0)))
                    (def pluck-melody (comp (synth :pluck :damp 0.4) (amp 0.6) (decay 0.5)))"]
    (lisp/eval-string preset-src env)
    env))

(deftest acid-bass-is-a-function
  (let [env (env-with-presets)
        result (lisp/eval-string "acid-bass" env)]
    (is (fn? (:result result)))))

(deftest preset-applies-to-pattern
  (let [env (env-with-presets)
        pat (:result (lisp/eval-string "(->> (pure :c3) acid-bass)" env))
        ev  (first (core/query pat {:start [0 1] :end [1 1]}))]
    (is (some? pat))
    (is (= :saw (:synth (:value ev))))
    (is (= 0.7 (:amp (:value ev))))))

(deftest pad-preset-has-attack
  (let [env (env-with-presets)
        pat (:result (lisp/eval-string "(->> (pure :a4) pad)" env))
        ev  (first (core/query pat {:start [0 1] :end [1 1]}))]
    (is (= 0.4 (:attack (:value ev))))))
```

#### 8c. Add to test runner

In `packages/core/src/repulse/test_runner.cljs`, require the new test namespaces:

```clojure
(ns repulse.test-runner
  (:require ...
            [repulse.synth-voices-test]
            [repulse.presets-test]))

(defn main []
  (test/run-tests '... 'repulse.synth-voices-test 'repulse.presets-test))
```

---

## Files to change

| File | Change |
|---|---|
| `packages/audio/src/lib.rs` | Add `Voice::Tri` and `Voice::Sub` variants; tick/is_silent arms; dispatch in `activate_v2` for `"tri:hz"` and `"sub:hz"` |
| `app/src/repulse/audio.cljs` | Add `make-tri`, `make-sub`, `make-supersaw`, `make-pwm`, `make-pluck`, `make-organ`, `make-bell`, `make-strings`; add 8 dispatch branches in `play-event` |
| `packages/lisp/src/repulse/lisp/eval.cljs` | Add `"tri"`, `"sub"`, `"bell"` shorthand functions to `make-env` |
| `app/src/repulse/app.cljs` | Add `builtin-preset-names`, `preset-source`; inject presets after `ensure-env!` builds the env; add `"presets"` binding |
| `app/src/repulse/lisp-lang/repulse-lisp.grammar` | Add ~25 new tokens to `BuiltinName` |
| `app/src/repulse/lisp-lang/completions.js` | Add ~23 new completion entries (voices + presets + `presets` fn) |
| `packages/core/test/repulse/synth_voices_test.cljs` | **New** — evaluator-level tests for all 8 new voice keywords |
| `packages/core/test/repulse/presets_test.cljs` | **New** — tests for preset structure and application |
| `packages/core/src/repulse/test_runner.cljs` | Require new test namespaces |
| `docs/USAGE.md` | Extend voices table; add "Built-in presets" subsection; add examples |
| `CLAUDE.md` | Mark Phase S as ✓ delivered |

---

## Definition of done

### New voices — event map structure

- [ ] `(synth :tri (pure :c4))` produces events with `{:synth :tri :note :c4 :freq 261.63}`
- [ ] `(synth :sub (pure :c2))` produces events with `{:synth :sub ...}`
- [ ] `(synth :supersaw :voices 7 :detune 0.2 (pure :c3))` produces events with `:voices 7 :detune 0.2`
- [ ] `(synth :pwm :rate 1.0 :depth 0.4 (pure :a4))` produces events with `:rate 1.0 :depth 0.4`
- [ ] `(synth :pluck :damp 0.7 (pure :e4))` produces events with `:damp 0.7`
- [ ] `(synth :organ :drawbars [1.0 0.8 0.6] (pure :c4))` produces events with `:drawbars [1.0 0.8 0.6]`
- [ ] `(synth :bell :ratio 4.0 :index 8 (pure :a5))` produces events with `:ratio 4.0 :index 8`
- [ ] `(synth :strings :voices 7 (pure :d4))` produces events with `:voices 7`
- [ ] Shorthand `(tri :c4)` returns `{:note :c4 :synth :tri}`
- [ ] Shorthand `(sub :c2)` returns `{:note :c2 :synth :sub}`
- [ ] Shorthand `(bell :a5 :ratio 3.5)` returns `{:note :a5 :synth :bell :ratio 3.5}`

### New voices — audio output

- [ ] `(->> (pure :c4) (synth :tri) (decay 0.5))` produces a clearly triangle wave sound (brighter than sine, softer than saw)
- [ ] `(->> (pure :c1) (synth :sub))` produces a deep sub-bass tone one octave below `:c1`
- [ ] `(->> (pure :c3) (synth :supersaw :voices 7 :detune 0.15))` sounds wide and lush (multiple detuned saws)
- [ ] `(->> (pure :c3) (synth :pwm :rate 0.3 :depth 0.4))` sounds like a slowly sweeping filter/pulse
- [ ] `(->> (pure :e4) (synth :pluck :damp 0.4))` sounds like a plucked string with clear pitch and natural decay
- [ ] `(->> (pure :e4) (synth :pluck :damp 0.9))` sounds noticeably darker/shorter than `:damp 0.4`
- [ ] `(->> (pure :c4) (synth :organ))` produces an organ-like tone with multiple harmonics
- [ ] `(->> (pure :a5) (synth :bell :ratio 3.5 :index 6))` produces a metallic bell with long ringing tail
- [ ] `(->> (pure :d4) (synth :strings))` produces an ensemble-like string pad with slow attack
- [ ] WASM voices `:tri` and `:sub` work when the worklet is active (no JS fallback used)
- [ ] All JS-only voices fall back cleanly when the worklet is unavailable

### Presets

- [ ] All 15 preset names are available without any `def` in the editor buffer
- [ ] `(->> (scale :minor :c2 (seq 0 :_ 5 :_)) acid-bass)` plays a punchy acid bassline
- [ ] `(->> (slow 4 (chord :minor :a3)) pad)` plays a slow lush chord
- [ ] `(->> (scale :major :c5 (seq 0 2 4 7)) pluck-melody)` plays a plucked melody
- [ ] `(->> (seq :c4 :_ :eb4 :_) stab)` plays loud, very short chord stabs
- [ ] `(presets)` returns `(:acid-bass :sub-bass :pluck-bass :reese :lead :soft-lead :fm-lead :pad :dark-pad :string-pad :pluck-melody :bell-tone :organ-tone :stab :noise-hit)` (or prints it)
- [ ] User `(def pad ...)` overrides the built-in preset (user wins)
- [ ] Presets appear in the context panel under "Bindings"
- [ ] Hovering over `acid-bass` in the editor shows the tooltip from completions.js

### Composition

- [ ] Presets compose with all existing transforms: `(every 4 (fast 2) (->> melody lead))`
- [ ] Preset + extra params: `(->> melody acid-bass (amp 0.5))` — applies preset then lowers amp
- [ ] `(stack (->> kick (amp 0.9)) (->> melody lead) (->> (slow 4 (chord :minor :a3)) pad))` works
- [ ] `(->> (scale :minor :c4 (seq 0 2 4)) pluck-melody (fx :reverb 0.4))` works — preset + effect

### Tests

- [ ] All `synth-voices-test` tests pass
- [ ] All `presets-test` tests pass
- [ ] All existing `core-test`, `theory-test`, `params-test` still pass (`npm run test:core`)
- [ ] No console errors when loading the app fresh

### UI / editor

- [ ] All new voice keywords (`tri`, `sub`, `supersaw`, `pwm`, `pluck`, `organ`, `bell`, `strings`) have syntax highlighting
- [ ] All preset names have syntax highlighting
- [ ] All new names appear in autocomplete with correct detail strings
- [ ] Grammar regenerated (`npm run gen:grammar`) and `parser.js` committed
