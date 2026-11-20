(ns repulse.params
  (:refer-clojure :exclude [spread])
  (:require [repulse.core :as core]))

;;; ── Value map helpers ────────────────────────────────────────────────

(defn- to-map
  "Upgrade a raw event value to a map with :note key, or leave maps as-is."
  [v]
  (if (map? v) v {:note v}))

(defn- pat?
  "True if x is a REPuLse pattern (was created via core/pattern).
   Uses the explicit ::core/pat tag so plain event-value maps
   (which are also Clojure maps) are never misidentified."
  [x]
  (core/pattern? x))

(defn- apply-param
  "Merge parameter kw into each event of note-pat, sourcing values from
   param-val-or-pat. Scalar values are wrapped in (pure …) first.
   note-pat may also be a raw value (e.g. the map returned by (sound …));
   it is coerced to (pure v) so that (rate 2.0 (sound :tabla 0)) works."
  [kw param-val-or-pat note-pat]
  (let [param-pat (if (pat? param-val-or-pat)
                    param-val-or-pat
                    (core/pure param-val-or-pat))
        note-pat' (if (pat? note-pat)
                    note-pat
                    (core/pure note-pat))]
    ;; Structure from the note pattern (Tidal `#`, Strudel `.set`).
    (core/app-right (fn [pv nv] (assoc (to-map nv) kw pv))
                    param-pat
                    note-pat')))

;;; ── Parameter transformers ───────────────────────────────────────────

(defn amp
  "Scale event amplitude. 0.0 = silent, 1.0 = full.
   (amp 0.8 pat)          — apply directly
   (amp (seq 0.9 0.5) pat) — patterned accent
   (amp 0.8)               — return (pat → pat) transformer"
  ([v]     (fn [pat] (amp v pat)))
  ([v pat] (apply-param :amp v pat)))

(defn attack
  "Envelope attack time in seconds (time to reach peak amplitude).
   (attack 0.001 pat) — percussive / instant
   (attack 0.3 pat)   — slow swell
   (attack 0.05)      — return transformer"
  ([t]     (fn [pat] (attack t pat)))
  ([t pat] (apply-param :attack t pat)))

(defn decay
  "Envelope decay time in seconds (time to fade to silence after peak).
   (decay 0.08 pat) — punchy stab
   (decay 2.0 pat)  — long fade
   (decay 0.4)      — return transformer"
  ([t]     (fn [pat] (decay t pat)))
  ([t pat] (apply-param :decay t pat)))

(defn release
  "Envelope release time in seconds. When omitted, the decay value is used.
   (release 0.5 pat) — apply directly
   (release 0.5)     — return transformer"
  ([t]     (fn [pat] (release t pat)))
  ([t pat] (apply-param :release t pat)))

(defn pan
  "Stereo panning. -1.0 = hard left, 0.0 = centre, 1.0 = hard right.
   (pan 0.0 pat)              — centre
   (pan (seq -0.8 0.8) pat)   — alternating left/right
   (pan -0.5)                 — return transformer"
  ([p]     (fn [pat] (pan p pat)))
  ([p pat] (apply-param :pan p pat)))

(defn jux
  "Stack the original pattern panned left with (f pat) panned right.
   Creates stereo width by splitting original and transformed copies.
   (jux rev (seq :c4 :e4 :g4))  — original left, reversed right"
  [f pat]
  (core/stack* [(pan -1 pat) (pan 1 (f pat))]))

(defn rate
  "Playback rate multiplier. 1.0 = normal, 2.0 = double speed (octave up),
   0.5 = half speed (octave down).
   (rate 1.5 pat)  — apply directly
   (rate 1.5)      — return transformer"
  ([r]     (fn [pat] (rate r pat)))
  ([r pat] (apply-param :rate r pat)))

(defn begin
  "Sample start position as a fraction of buffer duration (0.0–1.0).
   (begin 0.25 pat)  — start at 25% into the sample
   (begin 0.25)      — return transformer"
  ([t]     (fn [pat] (begin t pat)))
  ([t pat] (apply-param :begin t pat)))

(defn end*
  "Sample end position as a fraction of buffer duration (0.0–1.0).
   Named end* to avoid conflict with cljs.core/end.
   (end* 0.75 pat)  — stop at 75% into the sample
   (end* 0.75)      — return transformer"
  ([t]     (fn [pat] (end* t pat)))
  ([t pat] (apply-param :end t pat)))

(defn loop-sample
  "Enable sample looping.
   (loop-sample true pat)  — loop the sample
   (loop-sample true)      — return transformer"
  ([on?]     (fn [pat] (loop-sample on? pat)))
  ([on? pat] (apply-param :loop on? pat)))

(defn jux-by
  "Like jux but with adjustable stereo width.
   width 0.0 = both copies centre (mono), 1.0 = full left/right.
   width may also be a pattern of widths.
   (jux-by 0.5 rev pat) — half stereo width"
  [width f pat]
  ((core/patternify
    (fn [w p] (core/stack* [(pan (- w) p) (pan w (f p))])))
   width pat))

(defn midi-out
  "Tag events for MIDI note output on the given channel (1–16).
   (midi-out 1 pat)  — send events as MIDI notes on channel 1
   (midi-out 1)      — return (pat → pat) transformer"
  ([ch]     (fn [pat] (midi-out ch pat)))
  ([ch pat] (apply-param :midi-ch ch pat)))

;;; ── Phase TL3b: hurry ────────────────────────────────────────────────

(defn- hurry* [r pat]
  (let [f (core/rat->float (core/->rat r))]
    (core/fmap (fn [v]
                 (if (or (nil? v) (= :_ v))
                   v
                   (let [m (to-map v)] (assoc m :rate (* (or (:rate m) 1) f)))))
               (core/fast r pat))))

(defn hurry
  "fast r, and multiply every event's :rate by r (missing :rate counts as 1).
   (hurry 2 pat) — double speed and playback rate
   (hurry 2)     — return transformer"
  ([r]     (fn [pat] (hurry r pat)))
  ([r pat] ((core/patternify hurry*) r pat)))

;;; ── Phase TL5: amp-scaling layering ──────────────────────────────────

(defn- amp*
  "Multiply the :amp of an event value by f (default amp 1). Rests stay rests."
  [f v]
  (if (= v :_) v (update (to-map v) :amp #(* (or % 1.0) f))))

(defn- echo* [times time feedback pat]
  (let [n  (int (core/rat->float (core/->rat times)))
        fb (core/rat->float (core/->rat feedback))
        t  (core/->rat time)]
    (core/stack* (mapv (fn [i]
                         (core/fmap #(amp* (Math/pow fb i) %)
                                    (core/late (core/rat* t [i 1]) pat)))
                       (range (max 0 n))))))

(defn echo
  "Strudel echo: times layers, each `time` later, amp × feedback^i."
  [times time feedback pat]
  ((core/patternify3 echo*) times time feedback pat))

(defn stut
  "Strudel stut: echo with Tidal argument order (times feedback time)."
  [times feedback time pat]
  (echo times time feedback pat))

(defn jux-flip-by
  "jux-by whose width alternates between w and −w every cycle."
  [w f pat]
  (let [->f (fn [x] (let [x (if (vector? x) (core/rat->float (core/->rat x)) x)] (double x)))
        w'  (if (core/pattern? w) (core/fmap ->f w) (core/pure (->f w)))]
    (jux-by (core/cat* [w' (core/fmap - w')]) f pat)))

(defn jux-flip [f pat] (jux-flip-by 1 f pat))

(defn- fade-gain [p] (if (< p 0.5) 1 (* 2 (- 1 p))))

(defn xfade
  "Crossfade two patterns: a gets fade-gain(pos), b gets fade-gain(1 − pos)."
  [a pos b]
  (let [pos  (if (core/pattern? pos) pos (core/pure pos))
        ->p  #(if (core/pattern? %) % (core/pure %))
        side (fn [pat g]
               (core/app-left
                (fn [v p] (amp* (fade-gain (g (core/rat->float (core/->rat p)))) v))
                (->p pat) pos))]
    (core/stack* [(side a identity) (side b #(- 1 %))])))

;;; ── Phase TL4b: fix ──────────────────────────────────────────────────

(defn- num-of [x]
  (cond (number? x) x
        (and (vector? x) (= 2 (count x)) (every? number? x) (not (zero? (second x))))
        (/ (first x) (second x))
        :else nil))

(defn- rest-val? [v] (or (nil? v) (= v :_) (and (map? v) (= :_ (:note v)))))

(defn- control-view
  "K9: the control map fix matches against, or nil (never matches)."
  [v]
  (when-not (rest-val? v)
    (let [m (cond (map? v) v
                  (or (keyword? v) (number? v) (num-of v)) (to-map v)
                  :else nil)]
      (when m
        (if (contains? m :s)
          m
          (let [n (:note m)
                ;; Any keyword :note counts — no note-name guessing, so sample
                ;; names that look like pitches (:cb cowbell = C-flat) still match.
                s (or (:bank m) (when (keyword? n) n))]
            (cond-> m s (assoc :s s))))))))

(defn- val-match? [cv ev]
  (let [a (num-of cv) b (num-of ev)]
    (if (and a b) (== a b) (= cv ev))))

(defn controls-match?
  "K10: every [k cv] of cond matches the event value's control view."
  [cond v]
  (if-let [m (control-view v)]
    (every? (fn [[k cv]]
              (let [ev (get m k)]
                (and (some? ev)
                     (if (vector? cv)
                       (boolean (some #(val-match? % ev) cv))
                       (val-match? cv ev)))))
            cond)
    false))

(defn fix
  "Tidal fix: apply f to the events whose controls match cond, keep the rest."
  [f cond pat]
  (core/stack* [(f (core/filter-events #(controls-match? cond %) pat))
                (core/filter-events #(not (controls-match? cond %)) pat)]))

;;; ── Phase TL6: sample slicing & playback control ─────────────────────

(defn speed
  "Alias of rate (Tidal/Strudel name). Writes :rate; negative = reverse.
   (speed -1 pat) — play backwards
   (speed 2)      — return transformer"
  ([r]     (fn [pat] (speed r pat)))
  ([r pat] (apply-param :rate r pat)))

(defn- ->unit [u]
  (let [u (if (string? u) (keyword u) u)]
    (if (#{:c :r} u)
      u
      (throw (ex-info (str "unit expects :c (cycles) or :r (rate), got " (pr-str u))
                      {:type :bad-unit})))))

(defn unit
  "Speed unit: :r (default, plain rate) or :c (rate × sample length / cycle length,
   so speed 1 plays the whole sample in one cycle).
   (unit :c pat)"
  ([u]     (fn [pat] (unit u pat)))
  ([u pat] (apply-param :unit (if (pat? u) (core/fmap ->unit u) (->unit u)) pat)))

(defn accelerate
  "Glide the playback rate from rate to rate + v over the sample's playing time.
   (accelerate -1 pat)"
  ([v]     (fn [pat] (accelerate v pat)))
  ([v pat] (apply-param :accelerate v pat)))

(defn legato
  "Sample length relative to the event: 1 = stop at the event end, 0.5 = half.
   (legato 0.5 pat)"
  ([v]     (fn [pat] (legato v pat)))
  ([v pat] (apply-param :legato v pat)))

(defn sustain
  "Maximum sample length in seconds.
   (sustain 0.1 pat)"
  ([v]     (fn [pat] (sustain v pat)))
  ([v pat] (apply-param :sustain v pat)))

(defn cut
  "Choke group: a new sample in group g stops the samples of g still playing.
   (cut 1 pat)"
  ([g]     (fn [pat] (cut g pat)))
  ([g pat] (apply-param :cut g pat)))

(defn loop-begin
  "Loop region start as a fraction of the sample (with loop-sample).
   (loop-begin 0.25 pat)"
  ([v]     (fn [pat] (loop-begin v pat)))
  ([v pat] (apply-param :loop-begin v pat)))

(defn loop-end
  "Loop region end as a fraction of the sample (with loop-sample).
   (loop-end 0.5 pat)"
  ([v]     (fn [pat] (loop-end v pat)))
  ([v pat] (apply-param :loop-end v pat)))

(defn- ->pat [x] (if (pat? x) x (core/pure x)))

(defn- ->num [x] (if (vector? x) (core/rat->float (core/->rat x)) x))

(defn- range-of
  "[begin end] of an event value map (defaults 0, 1) as doubles."
  [m]
  [(double (or (->num (:begin m)) 0)) (double (or (->num (:end m)) 1))])

(defn- sub-range
  "Value v with its begin–end range narrowed to piece i of n (S1: relative)."
  [v i n]
  (if (rest-val? v)
    v
    (let [m      (to-map v)
          [b e]  (range-of m)
          d      (- e b)]
      (assoc m :begin (+ b (* d (/ i n))) :end (+ b (* d (/ (inc i) n)))))))

(defn- ->count [n] (max 1 (int (core/rat->float (core/->rat n)))))

(defn- event-map
  "Pattern whose event values are (f event) — sees whole/part as well as value."
  [f pat]
  (core/pattern (fn [sp] (map #(assoc % :value (f %)) (core/query pat sp)))))

(defn- chop* [n pat]
  (let [k (->count n)]
    (core/squeeze-join
     (event-map (fn [ev]
                  (core/timecat (mapv (fn [i] [1 (core/pure (sub-range (:value ev) i k)
                                                            (:source ev))])
                                      (range k))))
                pat))))

(defn chop
  "Cut each event into n parts that play consecutive pieces of its sample.
   (chop 4 (sound :break 0)) — begin 0, 1/4, 1/2, 3/4"
  ([n]     (fn [pat] (chop n pat)))
  ([n pat] ((core/patternify chop*) n (->pat pat))))

(defn- striate* [n pat]
  (let [k (->count n)]
    (core/timecat (mapv (fn [i] [1 (core/fmap #(sub-range % i k) pat)]) (range k)))))

(defn striate
  "Play the pattern n times per cycle; repetition i plays piece i of each sample.
   (striate 4 (sound :break 0))"
  ([n]     (fn [pat] (striate n pat)))
  ([n pat] ((core/patternify striate*) n (->pat pat))))

(defn- slice-range [i n]
  (let [i (->num i) n (->num n)]
    (when (and (number? i) (number? n) (pos? n))
      (let [k (max 1 (int n))
            j (mod (int (Math/floor i)) k)]
        [(double (/ j k)) (double (/ (inc j) k))]))))

(defn slice
  "Cut the sample into n equal slices and play the slices chosen by ipat.
   Structure comes from ipat. (slice 8 (seq 0 3 2 7) (sound :break 0))"
  ([n ipat]     (fn [pat] (slice n ipat pat)))
  ([n ipat pat]
   (let [idx (core/app-left vector (->pat ipat) (->pat n))]
     (core/filter-events
      some?
      (core/app-left (fn [[i k] v]
                       (cond (rest-val? i) :_
                             (rest-val? v) v
                             :else (when-let [[b e] (slice-range i k)]
                                     (assoc (to-map v) :begin b :end e))))
                     idx (->pat pat))))))

(defn- whole-cycles [ev]
  (let [w (or (:whole ev) (:part ev))]
    (core/rat->float (core/rat- (:end w) (:start w)))))

(defn- fit-rate
  "Event value with :rate = frac / whole-cycles × (rate or 1) and :unit :c."
  [ev frac-fn]
  (let [v (:value ev)]
    (if (rest-val? v)
      v
      (let [m (to-map v)]
        (assoc m
               :rate (* (/ (frac-fn m) (whole-cycles ev)) (double (or (->num (:rate m)) 1)))
               :unit :c)))))

(defn splice
  "slice, then set each slice's speed so it fills its event (unit :c).
   (splice 4 (seq 0 1 2 3) (sound :break 0))"
  ([n ipat]     (fn [pat] (splice n ipat pat)))
  ([n ipat pat]
   (event-map #(fit-rate % (fn [m] (let [[b e] (range-of m)] (- e b))))
              (slice n ipat pat))))

(defn fit
  "Set each event's speed so its begin–end range fills the event (unit :c).
   (fit (seq (sound :break 0) :_))"
  [pat]
  (event-map #(fit-rate % (fn [m] (let [[b e] (range-of m)] (- e b))))
             (->pat pat)))

(defn- loop-at* [n pat]
  (let [f (/ 1 (core/rat->float (core/->rat n)))]
    (core/fmap (fn [v]
                 (if (rest-val? v)
                   v
                   (let [m (to-map v)]
                     (assoc m :rate (* (double (or (->num (:rate m)) 1)) f) :unit :c))))
               (core/slow n pat))))

(defn loop-at
  "Slow pat by n and fit its samples to n cycles (speed × 1/n, unit :c).
   (loop-at 2 (chop 16 (sound :break 0)))"
  ([n]     (fn [pat] (loop-at n pat)))
  ([n pat] ((core/patternify loop-at*) n (->pat pat))))

;;; ── Phase TL7a: per-event filters, shape, crush & gain ───────────────
;;; Core only writes the key. Clamping and defaults belong to the audio
;;; layer, so the event map shows what the user wrote.

(defn lpf
  "Per-event low-pass cutoff in Hz (20–20000). Patternable, accepts signals.
   (lpf 800 pat) · (lpf (range 400 6000 sine) pat) · (lpf 800) → transformer"
  ([f]     (fn [pat] (lpf f pat)))
  ([f pat] (apply-param :lpf f pat)))

(defn resonance
  "Q of the per-event low-pass (0.1–30, default 0.707). No effect without lpf.
   (resonance 4 pat) · (resonance 4) → transformer"
  ([q]     (fn [pat] (resonance q pat)))
  ([q pat] (apply-param :resonance q pat)))

(defn hpf
  "Per-event high-pass cutoff in Hz (20–20000).
   (hpf 300 pat) · (hpf 300) → transformer"
  ([f]     (fn [pat] (hpf f pat)))
  ([f pat] (apply-param :hpf f pat)))

(defn hpq
  "Q of the per-event high-pass (0.1–30, default 0.707). No effect without hpf.
   (hpq 4 pat) · (hpq 4) → transformer"
  ([q]     (fn [pat] (hpq q pat)))
  ([q pat] (apply-param :hpq q pat)))

(defn bpf
  "Per-event band-pass centre frequency in Hz (20–20000), 0 dB peak gain.
   (bpf 1000 pat) · (bpf 1000) → transformer"
  ([f]     (fn [pat] (bpf f pat)))
  ([f pat] (apply-param :bpf f pat)))

(defn bpq
  "Q of the per-event band-pass (0.1–30, default 1). No effect without bpf.
   (bpq 5 pat) · (bpq 5) → transformer"
  ([q]     (fn [pat] (bpq q pat)))
  ([q pat] (apply-param :bpq q pat)))

(defn shape
  "Per-event soft waveshaper amount (0–0.99, 0 = off).
   (shape 0.4 pat) · (shape 0.4) → transformer"
  ([a]     (fn [pat] (shape a pat)))
  ([a pat] (apply-param :shape a pat)))

(defn crush
  "Per-event bit-depth quantiser (1–16 bits, 16 = off).
   (crush 4 pat) · (crush 4) → transformer"
  ([b]     (fn [pat] (crush b pat)))
  ([b pat] (apply-param :crush b pat)))

(defn gain
  "Per-event linear gain (0–2, default 1); multiplies amp.
   (gain 0.5 pat) · (gain 0.5) → transformer"
  ([g]     (fn [pat] (gain g pat)))
  ([g pat] (apply-param :gain g pat)))

(defn velocity
  "Per-event velocity (0–1, default 1); multiplies amp.
   (velocity 0.6 pat) · (velocity 0.6) → transformer"
  ([v]     (fn [pat] (velocity v pat)))
  ([v pat] (apply-param :velocity v pat)))

;;; ── Phase TL7b: reverb & delay sends per orbit ───────────────────────
;;; Core only writes the key; the audio layer (repulse.orbits) clamps.

(defn room
  "Per-event reverb send level (0–1, default 0) into the event's orbit.
   (room 0.4 pat) · (room (seq 0 0.8) pat) · (room 0.4) → transformer"
  ([v]     (fn [pat] (room v pat)))
  ([v pat] (apply-param :room v pat)))

(defn size
  "Reverb size of the event's orbit (0–1, default 0.5; IR length 0.3 + 5.7·size s).
   Last event wins per orbit. (size 0.8 pat) · (size 0.8) → transformer"
  ([v]     (fn [pat] (size v pat)))
  ([v pat] (apply-param :size v pat)))

(defn delay*
  "Per-event delay send level (0–1, default 0) into the event's orbit.
   Registered as `delay` in REPuLse-Lisp (`delay` is a cljs.core macro).
   (delay* 0.4 pat) · (delay* 0.4) → transformer"
  ([v]     (fn [pat] (delay* v pat)))
  ([v pat] (apply-param :delay v pat)))

(defn delay-time
  "Delay time of the event's orbit in seconds (0.01–4, default 0.25).
   Last event wins per orbit. (delay-time 0.375 pat) · (delay-time 0.375) → transformer"
  ([v]     (fn [pat] (delay-time v pat)))
  ([v pat] (apply-param :delay-time v pat)))

(defn delay-feedback
  "Delay feedback of the event's orbit (0–0.95, default 0.5).
   Last event wins per orbit. (delay-feedback 0.6 pat) · (delay-feedback 0.6) → transformer"
  ([v]     (fn [pat] (delay-feedback v pat)))
  ([v pat] (apply-param :delay-feedback v pat)))

(defn orbit
  "Which orbit's reverb and delay the event sends into (integer 0–3, default 0).
   (orbit 1 pat) · (orbit 1) → transformer"
  ([v]     (fn [pat] (orbit v pat)))
  ([v pat] (apply-param :orbit v pat)))

;;; ── Phase TL7c: envelopes, pitch, vowel & the rest ───────────────────

(defn lp-env
  "Depth of the low-pass (lpf) envelope in octaves above the cutoff (−10…10, 0 = off).
   (lp-env 4 pat) · (lp-env 4) → transformer"
  ([v]     (fn [pat] (lp-env v pat)))
  ([v pat] (apply-param :lp-env v pat)))

(defn lp-attack
  "low-pass (lpf) envelope attack time in seconds (default 0.001).
   (lp-attack 0.01 pat)"
  ([v]     (fn [pat] (lp-attack v pat)))
  ([v pat] (apply-param :lp-attack v pat)))

(defn lp-decay
  "low-pass (lpf) envelope decay time in seconds (default 0.2).
   (lp-decay 0.2 pat)"
  ([v]     (fn [pat] (lp-decay v pat)))
  ([v pat] (apply-param :lp-decay v pat)))

(defn lp-sustain
  "low-pass (lpf) envelope sustain level, a fraction 0–1 of the depth (default 0).
   (lp-sustain 0.5 pat)"
  ([v]     (fn [pat] (lp-sustain v pat)))
  ([v pat] (apply-param :lp-sustain v pat)))

(defn lp-release
  "low-pass (lpf) envelope release time in seconds after the event ends (default 0.1).
   (lp-release 0.3 pat)"
  ([v]     (fn [pat] (lp-release v pat)))
  ([v pat] (apply-param :lp-release v pat)))

(defn hp-env
  "Depth of the high-pass (hpf) envelope in octaves above the cutoff (−10…10, 0 = off).
   (hp-env 4 pat) · (hp-env 4) → transformer"
  ([v]     (fn [pat] (hp-env v pat)))
  ([v pat] (apply-param :hp-env v pat)))

(defn hp-attack
  "high-pass (hpf) envelope attack time in seconds (default 0.001).
   (hp-attack 0.01 pat)"
  ([v]     (fn [pat] (hp-attack v pat)))
  ([v pat] (apply-param :hp-attack v pat)))

(defn hp-decay
  "high-pass (hpf) envelope decay time in seconds (default 0.2).
   (hp-decay 0.2 pat)"
  ([v]     (fn [pat] (hp-decay v pat)))
  ([v pat] (apply-param :hp-decay v pat)))

(defn hp-sustain
  "high-pass (hpf) envelope sustain level, a fraction 0–1 of the depth (default 0).
   (hp-sustain 0.5 pat)"
  ([v]     (fn [pat] (hp-sustain v pat)))
  ([v pat] (apply-param :hp-sustain v pat)))

(defn hp-release
  "high-pass (hpf) envelope release time in seconds after the event ends (default 0.1).
   (hp-release 0.3 pat)"
  ([v]     (fn [pat] (hp-release v pat)))
  ([v pat] (apply-param :hp-release v pat)))

(defn bp-env
  "Depth of the band-pass (bpf) envelope in octaves above the cutoff (−10…10, 0 = off).
   (bp-env 4 pat) · (bp-env 4) → transformer"
  ([v]     (fn [pat] (bp-env v pat)))
  ([v pat] (apply-param :bp-env v pat)))

(defn bp-attack
  "band-pass (bpf) envelope attack time in seconds (default 0.001).
   (bp-attack 0.01 pat)"
  ([v]     (fn [pat] (bp-attack v pat)))
  ([v pat] (apply-param :bp-attack v pat)))

(defn bp-decay
  "band-pass (bpf) envelope decay time in seconds (default 0.2).
   (bp-decay 0.2 pat)"
  ([v]     (fn [pat] (bp-decay v pat)))
  ([v pat] (apply-param :bp-decay v pat)))

(defn bp-sustain
  "band-pass (bpf) envelope sustain level, a fraction 0–1 of the depth (default 0).
   (bp-sustain 0.5 pat)"
  ([v]     (fn [pat] (bp-sustain v pat)))
  ([v pat] (apply-param :bp-sustain v pat)))

(defn bp-release
  "band-pass (bpf) envelope release time in seconds after the event ends (default 0.1).
   (bp-release 0.3 pat)"
  ([v]     (fn [pat] (bp-release v pat)))
  ([v pat] (apply-param :bp-release v pat)))

(defn penv
  "Depth of the pitch envelope in semitones (−48…48, 0 = off).
   (penv 12 pat) — start an octave up and fall back · (penv 12) → transformer"
  ([v]     (fn [pat] (penv v pat)))
  ([v pat] (apply-param :penv v pat)))

(defn p-attack
  "Pitch envelope attack time in seconds (default 0.001).
   (p-attack 0.1 pat)"
  ([v]     (fn [pat] (p-attack v pat)))
  ([v pat] (apply-param :p-attack v pat)))

(defn p-decay
  "Pitch envelope decay time in seconds (default 0.2).
   (p-decay 0.1 pat)"
  ([v]     (fn [pat] (p-decay v pat)))
  ([v pat] (apply-param :p-decay v pat)))

(defn p-sustain
  "Pitch envelope sustain level, a fraction 0–1 of the depth (default 0).
   (p-sustain 0.1 pat)"
  ([v]     (fn [pat] (p-sustain v pat)))
  ([v pat] (apply-param :p-sustain v pat)))

(defn p-release
  "Pitch envelope release time in seconds after the event ends (default 0.1).
   (p-release 0.1 pat)"
  ([v]     (fn [pat] (p-release v pat)))
  ([v pat] (apply-param :p-release v pat)))

(defn vib
  "Vibrato rate in Hz (0–40, 0 = off).
   (vib 6 pat) · (vib 6) → transformer"
  ([v]     (fn [pat] (vib v pat)))
  ([v pat] (apply-param :vib v pat)))

(defn vib-mod
  "Vibrato depth in semitones (0–12, default 0.5).
   (vib-mod 0.3 pat)"
  ([v]     (fn [pat] (vib-mod v pat)))
  ([v pat] (apply-param :vib-mod v pat)))

(defn vowel
  "Formant filter: :a :e :i :o or :u.
   (vowel :a pat) · (vowel (seq :a :o) pat)"
  ([v]     (fn [pat] (vowel v pat)))
  ([v pat] (apply-param :vowel v pat)))

(defn coarse
  "Sample-and-hold every n samples (1–64, 1 = off). WASM synth voices only.
   (coarse 8 pat)"
  ([v]     (fn [pat] (coarse v pat)))
  ([v pat] (apply-param :coarse v pat)))

(defn djf
  "DJ filter, one knob 0–1: below 0.5 low-pass, above 0.5 high-pass, 0.5 = off.
   (djf 0.2 pat) · (djf (slow 4 sine) pat)"
  ([v]     (fn [pat] (djf v pat)))
  ([v pat] (apply-param :djf v pat)))

(defn postgain
  "Linear gain after the insert chain (0–4, default 1).
   (postgain 0.5 pat)"
  ([v]     (fn [pat] (postgain v pat)))
  ([v pat] (apply-param :postgain v pat)))

(defn nudge
  "Move each event in time by seconds (−1…1; positive = later).
   (nudge 0.02 pat)"
  ([v]     (fn [pat] (nudge v pat)))
  ([v pat] (apply-param :nudge v pat)))

(defn delay-sync
  "Orbit delay time in cycles (1/64–16); overrides delay-time.
   (delay-sync 3/16 pat)"
  ([v]     (fn [pat] (delay-sync v pat)))
  ([v pat] (apply-param :delay-sync v pat)))

;;; ── Phase SYN6: oscillator, drum-kit and instrument parameters ──────
;;; Core does not clamp; the audio layer does (ranges in the docstrings).

(defn unison
  "Number of oscillators of a synth voice (1–7).
   (unison 3 pat) · (unison 3) → transformer"
  ([v]     (fn [pat] (unison v pat)))
  ([v pat] (apply-param :unison v pat)))

(defn detune
  "Total width of the unison stack in cents (0–200; 20 when unison > 1).
   (detune 12 pat)"
  ([v]     (fn [pat] (detune v pat)))
  ([v pat] (apply-param :detune v pat)))

(defn spread
  "Stereo width of the unison stack (0–1; supersaw 0.7).
   (spread 0.8 pat)"
  ([v]     (fn [pat] (spread v pat)))
  ([v pat] (apply-param :spread v pat)))

(defn sub-osc
  "Sine one octave below the note (0–1; 0.5 = 6 dB below the fundamental).
   (sub-osc 0.5 pat)"
  ([v]     (fn [pat] (sub-osc v pat)))
  ([v pat] (apply-param :sub-osc v pat)))

(defn pw
  "Pulse width of :square / :pulse (0.01–0.99, default 0.5).
   (pw 0.25 pat)"
  ([v]     (fn [pat] (pw v pat)))
  ([v pat] (apply-param :pw v pat)))

(defn pw-rate
  "Pulse-width LFO rate in Hz (0–20). WASM voices only.
   (pw-rate 1 pat)"
  ([v]     (fn [pat] (pw-rate v pat)))
  ([v pat] (apply-param :pw-rate v pat)))

(defn pw-depth
  "Pulse-width LFO depth (0–0.49). WASM voices only.
   (pw-depth 0.4 pat)"
  ([v]     (fn [pat] (pw-depth v pat)))
  ([v pat] (apply-param :pw-depth v pat)))

(defn sync
  "Hard-sync ratio slave/master (1–16) of :saw / :pulse. WASM voices only.
   (sync 4.5 pat)"
  ([v]     (fn [pat] (sync v pat)))
  ([v pat] (apply-param :sync v pat)))

(defn tune
  "Pitch of a synthesized drum in semitones (−48…48).
   (tune -5 pat)"
  ([v]     (fn [pat] (tune v pat)))
  ([v pat] (apply-param :tune v pat)))

(defn tone
  "Brightness / body–noise balance of a synthesized drum (0–1).
   (tone 0.8 pat)"
  ([v]     (fn [pat] (tone v pat)))
  ([v pat] (apply-param :tone v pat)))

(defn glide
  "Portamento time in seconds from the previous note of the track (0–5).
   (glide 0.06 pat)"
  ([v]     (fn [pat] (glide v pat)))
  ([v pat] (apply-param :glide v pat)))

(defn accent
  "Accent 0–1: louder, and with a filter envelope opens further and closes faster.
   (accent (seq 0 1 0 0) pat)"
  ([v]     (fn [pat] (accent v pat)))
  ([v pat] (apply-param :accent v pat)))

(defn lp-rate
  "Cutoff LFO rate of the low-pass in periods per cycle (0–64).
   (lp-rate 4 pat)"
  ([v]     (fn [pat] (lp-rate v pat)))
  ([v pat] (apply-param :lp-rate v pat)))

(defn lp-depth
  "Cutoff LFO depth of the low-pass in octaves (0–4).
   (lp-depth 2 pat)"
  ([v]     (fn [pat] (lp-depth v pat)))
  ([v pat] (apply-param :lp-depth v pat)))
