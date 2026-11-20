(ns repulse.inserts
  "Phase TL7a — the shared per-event insert spec. Both voice paths read it: the
   Web Audio node chain in repulse.audio and the trigger_v3 slots sent to the
   WASM worklet. Clamps, defaults and the shape/crush formulas live here only
   (lib.rs `Inserts` mirrors the formulas). Phase TL7c adds filter envelopes,
   pitch modulation, vowel, coarse, djf and postgain.

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

(def env-defaults
  "Envelope defaults (TL7c): times in seconds, sustain as a fraction of depth."
  {:attack 0.001 :decay 0.2 :sustain 0.0 :release 0.1})

(defn env-params
  "Clamped envelope of event map v whose depth is at key depth-k and whose
   times are at prefix-attack … prefix-release, or nil when the depth is 0 or
   absent. lim — depth clamp (10 octaves, 48 semitones).
   → {:depth :attack :decay :sustain :release}"
  [v depth-k prefix lim]
  (let [depth (param v depth-k (- lim) lim 0.0)
        k     (fn [s] (keyword (str prefix "-" s)))]
    (when-not (zero? depth)
      {:depth   depth
       :attack  (param v (k "attack") 0.0 10.0 (:attack env-defaults))
       :decay   (param v (k "decay") 0.0 10.0 (:decay env-defaults))
       :sustain (param v (k "sustain") 0.0 1.0 (:sustain env-defaults))
       :release (param v (k "release") 0.0 10.0 (:release env-defaults))})))

(def vowel-table
  "Formants (Csound bass table; i from the tenor table, whose F2 at −15 dB
   makes it read as \"ee\" — the bass i's F2 at −30 dB sounds like \"u\"):
   per vowel [freqs-hz gains-db bandwidths-hz]. lib.rs VOWELS mirrors it."
  {:a [[600 1040 2250 2450 2750] [0 -7 -9 -9 -20]   [60 70 110 120 130]]
   :e [[400 1620 2400 2800 3100] [0 -12 -9 -12 -18] [40 80 100 120 120]]
   :i [[290 1870 2800 3250 3540] [0 -15 -18 -20 -30] [40 90 100 120 120]] ; tenor
   :o [[400 750 2400 2600 2900]  [0 -11 -21 -20 -40] [40 80 100 120 120]]
   :u [[350 600 2400 2675 2950]  [0 -20 -32 -28 -36] [40 80 100 120 120]]})

(def vowel-order [:a :e :i :o :u])

(def vowel-makeup
  "Make-up gain after the formant bank (both paths)."
  2.0)

(defn vowel-key
  "Vowel keyword for :a/\"a\"/… or nil."
  [x]
  (let [k (cond (keyword? x) x (string? x) (keyword x))]
    (when (contains? vowel-table k) k)))

(defn vowel-index
  "trigger_v3 slot value for a vowel: 1–5 for a e i o u, 0 for none."
  [x]
  (if-let [k (vowel-key x)] (inc (.indexOf vowel-order k)) 0))

(defn djf-filters
  "The lpf/hpf a djf value d sets: d < 0.5 → {:lpf 20·1000^(2d)},
   d > 0.5 → {:hpf 20·1000^(2d − 1)}, 0.5 → {}."
  [d]
  (let [d (clamp 0.0 1.0 d)]
    (cond (< d 0.5) {:lpf (* 20 (Math/pow 1000 (* 2 d)))}
          (> d 0.5) {:hpf (* 20 (Math/pow 1000 (- (* 2 d) 1)))}
          :else     {})))

(defn insert-params
  "Extract clamped insert params from an event value map, or nil when the event
   has none (the common case: no extra nodes, no extra WASM work). A Q or an
   envelope without its filter, shape 0, crush ≥ 16, coarse 1 and postgain 1
   count as absent. djf (TL7c) sets lpf/hpf unless the event has its own.
   → {:lpf hz-or-nil :resonance q :hpf hz-or-nil :hpq q :bpf hz-or-nil :bpq q
      :shape 0–0.99 :crush 1–16
      :lp-env env-or-nil :hp-env … :bp-env … (env-params maps, TL7c)
      :vowel kw-or-nil :coarse 2–64-or-nil :postgain 0–4}"
  [v]
  (when (map? v)
    (let [dj       (when-some [d (->num (get v :djf))] (djf-filters d))
          lpf      (or (param v :lpf freq-lo freq-hi nil) (:lpf dj))
          hpf      (or (param v :hpf freq-lo freq-hi nil) (:hpf dj))
          bpf      (param v :bpf freq-lo freq-hi nil)
          shape    (param v :shape 0.0 0.99 0.0)
          crush    (param v :crush 1.0 16.0 16.0)
          vowel    (vowel-key (get v :vowel))
          coarse   (let [c (Math/round (param v :coarse 1.0 64.0 1.0))]
                     (when (> c 1) c))
          postgain (param v :postgain 0.0 4.0 1.0)
          lp-env   (when lpf (env-params v :lp-env "lp" 10.0))
          hp-env   (when hpf (env-params v :hp-env "hp" 10.0))
          bp-env   (when bpf (env-params v :bp-env "bp" 10.0))]
      (when (or lpf hpf bpf (pos? shape) (< crush 16) vowel coarse (not= postgain 1.0))
        {:lpf       lpf
         :resonance (param v :resonance q-lo q-hi (:lpf default-q))
         :hpf       hpf
         :hpq       (param v :hpq q-lo q-hi (:hpf default-q))
         :bpf       bpf
         :bpq       (param v :bpq q-lo q-hi (:bpf default-q))
         :shape     shape
         :crush     crush
         :lp-env    lp-env
         :hp-env    hp-env
         :bp-env    bp-env
         :vowel     vowel
         :coarse    coarse
         :postgain  postgain}))))

(defn pitch-params
  "Pitch modulation of an event map (TL7c), or nil when it has none.
   → {:penv env-or-nil :vib hz :vib-mod semitones}"
  [v]
  (when (map? v)
    (let [penv (env-params v :penv "p" 48.0)
          vib  (param v :vib 0.0 40.0 0.0)]
      (when (or penv (pos? vib))
        {:penv    penv
         :vib     vib
         :vib-mod (param v :vib-mod 0.0 12.0 0.5)}))))

(defn nudge
  "Event time offset in seconds, clamped to −1…1 (0 when absent)."
  [v]
  (if (map? v) (param v :nudge -1.0 1.0 0.0) 0.0))

;;; ── Envelopes (TL7c) ─────────────────────────────────────────────────
;; e(t), linear in the log domain: the cutoff moves in octaves, the pitch in
;; semitones. lib.rs EnvSpec::value implements the same formula.

(defn- peak-value
  "The envelope without release at t ≥ 0."
  [{:keys [attack decay sustain]} t]
  (cond
    (< t attack)            (/ t attack)
    (< t (+ attack decay))  (+ 1 (* (- sustain 1) (/ (- t attack) decay)))
    :else                   sustain))

(defn- gate-secs
  "Gate length as a number: ##Inf when nil or ≤ 0 (no release)."
  [gate]
  (if (and (number? gate) (pos? gate)) gate ##Inf))

(defn env-value
  "Envelope level 0–1 at t seconds after onset; gate (event length in seconds)
   nil or ≤ 0 = infinite (no release)."
  [{:keys [release] :as env} gate t]
  (let [^number g (gate-secs gate)]
    (cond
      (neg? t)               0.0
      (< t g)                (peak-value env t)
      (< t (+ g release))    (* (peak-value env g) (- 1 (/ (- t g) release)))
      :else                  0.0)))

(defn env-points
  "The schedule of an envelope as [[t e kind] …] relative to onset, kind :set
   (jump) or :ramp (straight line in the log domain from the previous point).
   Both the cutoff (exponentialRamp) and detune (linearRamp) automation use it."
  [{:keys [attack decay sustain release] :as env} gate]
  (let [^number g (gate-secs gate)
        ad  (+ attack decay)
        pts (cond-> (if (pos? attack) [[0 0.0 :set] [attack 1.0 :ramp]] [[0 1.0 :set]])
              (pos? decay)       (conj [ad sustain :ramp])
              (not (pos? decay)) (conj [attack sustain :set]))]
    (if (= g ##Inf)
      pts
      (let [kept (filterv (fn [[^number t]] (<= t g)) pts)
            ^number last-t (first (peek kept))
            kept (if (< last-t g) (conj kept [g (peak-value env g) :ramp]) kept)]
        (conj kept (if (pos? release) [(+ g release) 0.0 :ramp] [g 0.0 :set]))))))

(defn effective-depth
  "Envelope depth in octaves after clamping the peak cutoff base·2^depth to
   20 Hz … min(20 kHz, 0.49·sr), so both paths bend the same curve."
  [base depth sr]
  (let [hi   (min freq-hi (* 0.49 sr))
        peak (clamp freq-lo hi (* base (Math/pow 2 depth)))]
    (/ (Math/log (/ peak base)) (Math/log 2))))

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

(def off
  "Insert params with every stage off — the trigger_v3 slots 4–11 an event
   without inserts sends when it needs the longer layout (TL7b sends)."
  {:lpf nil :resonance (:lpf default-q) :hpf nil :hpq (:hpf default-q)
   :bpf nil :bpq (:bpf default-q) :shape 0.0 :crush 16.0})

(defn wasm-slots
  "trigger_v3 params (slots 4–11) for an insert map from insert-params:
   lpf resonance hpf hpq bpf bpq shape crush, filter slots 0 = off."
  [ins]
  [(or (:lpf ins) 0) (:resonance ins) (or (:hpf ins) 0) (:hpq ins)
   (or (:bpf ins) 0) (:bpq ins) (:shape ins) (:crush ins)])

(def env-off
  "trigger_v3 slots of one envelope that is off (depth 0, default times)."
  [0 (:attack env-defaults) (:decay env-defaults) (:sustain env-defaults)
   (:release env-defaults)])

(defn- env-slots [env]
  (if env
    [(:depth env) (:attack env) (:decay env) (:sustain env) (:release env)]
    env-off))

(defn tl7c?
  "True when insert map ins or pitch map pm carries a TL7c key, i.e. the
   event needs trigger_v3 slots 15–40."
  [ins pm]
  (boolean (or pm (:lp-env ins) (:hp-env ins) (:bp-env ins) (:vowel ins)
               (:coarse ins) (and ins (not= 1.0 (:postgain ins))))))

(defn wasm-slots-tl7c
  "trigger_v3 slots 15–40: gate · lp/hp/bp envelopes · pitch envelope · vib
   vib-mod · vowel · coarse · postgain. gate nil = 0 (infinite)."
  [ins pm gate]
  (-> [(if (and gate (pos? gate)) gate 0)]
      (into (env-slots (:lp-env ins)))
      (into (env-slots (:hp-env ins)))
      (into (env-slots (:bp-env ins)))
      (into (env-slots (:penv pm)))
      (into [(:vib pm 0) (:vib-mod pm 0.5)
             (vowel-index (:vowel ins))
             (or (:coarse ins) 1)
             (:postgain ins 1.0)])))
