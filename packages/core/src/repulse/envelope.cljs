(ns repulse.envelope)

;;; ── Pure envelope curve math ─────────────────────────────────────────────
;;
;; These functions compute interpolated sample sequences for each curve type.
;; They are pure (no Web Audio, no side effects) and fully testable.
;; `synth.cljs` uses them to fill Float32Arrays for setValueCurveAtTime.

(defn lin-samples
  "Linear interpolation from `from` to `to` over `n` samples (n >= 2)."
  [from to n]
  (let [n (max 2 n)]
    (mapv (fn [i] (+ (double from) (* (/ i (dec n)) (- (double to) (double from)))))
          (range n))))

(defn sin-samples
  "Sine (ease-in / ease-out) interpolation from `from` to `to` over `n` samples.
   Uses (1 - cos(π·t)) / 2 so it starts and ends smoothly."
  [from to n]
  (let [n (max 2 n)]
    (mapv (fn [i]
            (let [phase (* Math/PI (/ i (dec n)))
                  interp (/ (- 1 (Math/cos phase)) 2)]
              (+ (double from) (* interp (- (double to) (double from))))))
          (range n))))

(defn welch-samples
  "Welch (quarter-sine) interpolation from `from` to `to` over `n` samples.
   Uses sin(π/2·t) as the interp coefficient, giving a fast-at-start shape for both
   rising and falling segments."
  [from to n]
  (let [n (max 2 n)]
    (mapv (fn [i]
            (let [phase  (* (/ Math/PI 2) (/ i (dec n)))
                  interp (Math/sin phase)]
              (+ (double from) (* interp (- (double to) (double from))))))
          (range n))))

(defn exp-samples
  "Exponential interpolation from `from` to `to` over `n` samples.
   Values are clamped away from zero; falls back to linear when both endpoints
   have the same sign and one is zero."
  [from to n]
  (let [n     (max 2 n)
        from' (if (zero? from) 0.0001 (double from))
        to'   (if (zero? to)   0.0001 (double to))]
    ;; True exponential only makes sense when both values have the same sign.
    (if (neg? (* from' to'))
      ;; Sign crossing — linear fallback
      (lin-samples from to n)
      (mapv (fn [i]
              (let [t (/ i (dec n))]
                (* from' (Math/pow (/ to' from') t))))
            (range n)))))

(defn custom-curve-samples
  "Power-curve interpolation with numeric curvature `c`.
   c > 1 → concave-up (slow start), 0 < c < 1 → concave-down (fast start)."
  [from to n c]
  (let [n (max 2 n)
        c (max 0.01 (double c))]
    (mapv (fn [i]
            (let [t     (/ i (dec n))
                  y     (Math/pow t c)]
              (+ (double from) (* y (- (double to) (double from))))))
          (range n))))

(defn make-env
  "Construct an envelope descriptor from breakpoints.
   levels — N+1 amplitude values  (e.g. [0 1 0.3 0])
   times  — N segment durations   (e.g. [0.01 0.1 0.5])
   curves — N curve keywords or numbers; optional (default :lin for each segment)
             Supported: :lin :exp :sin :welch :step or a positive number.
   Returns {:levels [...] :times [...] :curves [...]}"
  ([levels times]
   (make-env levels times []))
  ([levels times curves]
   (let [n-segs (count times)
         filled (into (vec curves)
                      (repeat (max 0 (- n-segs (count curves))) :lin))]
     {:levels (vec levels)
      :times  (vec times)
      :curves filled})))

(defn total-duration
  "Sum of all segment times (seconds) in an envelope."
  [env]
  (reduce + 0.0 (:times env)))

(defn segment-samples
  "Compute `n` interpolated Float64 values for one envelope segment.
   from     — start level
   to       — end level
   curve    — :lin | :exp | :sin | :welch | :step | numeric curvature
   Returns a vector of doubles."
  [from to curve n]
  (case curve
    :lin   (lin-samples from to n)
    :exp   (exp-samples from to n)
    :sin   (sin-samples from to n)
    :welch (welch-samples from to n)
    :step  (into (vec (repeat (dec n) (double from))) [(double to)])
    ;; Numeric curvature
    (if (number? curve)
      (custom-curve-samples from to n curve)
      (lin-samples from to n))))
