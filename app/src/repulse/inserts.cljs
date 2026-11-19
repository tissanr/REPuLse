(ns repulse.inserts
  "Phase TL7a — the shared per-event insert spec. Both voice paths read it: the
   Web Audio node chain in repulse.audio and the trigger_v3 slots sent to the
   WASM worklet. Clamps, defaults and the shape/crush formulas live here only
   (lib.rs `Inserts` mirrors the formulas).

     shape:  a = min(amount, 0.99);  k = 2a / (1 − a)
             y = (1 + k) · x / (1 + k · |x|)
     crush:  levels = 2^(bits − 1)
             y = round(x · levels) / levels          (bits ≥ 16 → bypass)")

(defn- ->num
  "A finite JS number from a number or an [n d] rational, else nil."
  [x]
  (let [n (cond
            (number? x) x
            (and (vector? x) (= 2 (count x)) (number? (first x)) (number? (second x))
                 (not (zero? (second x))))
            (/ (first x) (second x)))]
    (when (and n (js/isFinite n)) (double n))))

(defn- clamp [lo hi x] (min hi (max lo x)))

(defn- param
  "Clamped number at key k of map v, or default d when absent or not a number."
  [v k lo hi d]
  (if-some [n (->num (get v k))] (clamp lo hi n) d))

(def ^:private freq-lo 20.0)
(def ^:private freq-hi 20000.0)
(def ^:private q-lo 0.1)
(def ^:private q-hi 30.0)

(def default-q
  "Default Q per filter stage."
  {:lpf 0.707 :hpf 0.707 :bpf 1.0})

(defn insert-params
  "Extract clamped insert params from an event value map, or nil when the event
   has none (the common case: no extra nodes, no extra WASM work). A Q without
   its filter, shape 0 and crush ≥ 16 count as absent.
   → {:lpf hz-or-nil :resonance q :hpf hz-or-nil :hpq q :bpf hz-or-nil :bpq q
      :shape 0–0.99 :crush 1–16}"
  [v]
  (when (map? v)
    (let [lpf   (param v :lpf freq-lo freq-hi nil)
          hpf   (param v :hpf freq-lo freq-hi nil)
          bpf   (param v :bpf freq-lo freq-hi nil)
          shape (param v :shape 0.0 0.99 0.0)
          crush (param v :crush 1.0 16.0 16.0)]
      (when (or lpf hpf bpf (pos? shape) (< crush 16))
        {:lpf       lpf
         :resonance (param v :resonance q-lo q-hi (:lpf default-q))
         :hpf       hpf
         :hpq       (param v :hpq q-lo q-hi (:hpf default-q))
         :bpf       bpf
         :bpq       (param v :bpq q-lo q-hi (:bpf default-q))
         :shape     shape
         :crush     crush}))))

(defn amp-factor
  "Linear factor gain × velocity that multiplies a voice's amp. gain is clamped
   to 0–2 and velocity to 0–1; both default to 1."
  [v]
  (if (map? v)
    (* (param v :gain 0.0 2.0 1.0) (param v :velocity 0.0 1.0 1.0))
    1.0))

(defn shape-k
  "Waveshaper coefficient k = 2a / (1 − a) for amount a (clamped to 0–0.99)."
  [amount]
  (let [a (clamp 0.0 0.99 amount)] (/ (* 2 a) (- 1 a))))

(defn crush-levels
  "Quantiser levels 2^(bits − 1), or nil when bits ≥ 16 (bypass)."
  [bits]
  (when (< bits 16) (Math/pow 2 (dec (clamp 1.0 16.0 bits)))))

(defn- curve
  "Float32Array of n points of f over x ∈ [−1, 1]."
  [f n]
  (let [arr (js/Float32Array. n)
        d   (dec n)]
    (dotimes [i n]
      (aset arr i (f (- (/ (* 2.0 i) d) 1.0))))
    arr))

(defn shape-curve
  "WaveShaperNode curve (n points) for the soft shaper with amount."
  [amount n]
  (let [k (shape-k amount)]
    (curve (fn [x] (/ (* (+ 1 k) x) (+ 1 (* k (Math/abs x))))) n)))

(defn crush-curve
  "WaveShaperNode staircase curve (n points) for a bits-deep quantiser.
   Math.round rounds halves up; lib.rs uses floor(x + 0.5) to match."
  [bits n]
  (if-let [levels (crush-levels bits)]
    (curve (fn [x] (/ (Math/round (* x levels)) levels)) n)
    (curve identity n)))

(defn q->webaudio
  "BiquadFilterNode Q value for a linear Q: lowpass/highpass take Q in dB
   (20·log10 Q), bandpass takes the linear Q."
  [type q]
  (case type
    ("lowpass" "highpass") (* 20 (Math/log10 q))
    q))

(defn wasm-slots
  "trigger_v3 params (slots 4–11) for an insert map from insert-params:
   lpf resonance hpf hpq bpf bpq shape crush, filter slots 0 = off."
  [ins]
  [(or (:lpf ins) 0) (:resonance ins) (or (:hpf ins) 0) (:hpq ins)
   (or (:bpf ins) 0) (:bpq ins) (:shape ins) (:crush ins)])
