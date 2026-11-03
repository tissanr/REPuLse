(ns repulse.ui.slider-model
  "Pure slider model (Phase WS1): slider/FX control configuration, value
   formatting, snapping, pointer-drag and keyboard stepping, plus the minimal
   text diff used to collapse a slider drag into one undo step.
   No DOM, no atoms.
   Exports: TRACK-PARAM-KEYS, SLIDER-PARAMS, FX-SLIDER-PARAMS, FX-SELECT-PARAMS,
            FX-PRIMARY-PARAM, fmt-pv, decimals, snap, value->t, t->value,
            drag-value, key-step, text-diff."
  (:require [clojure.string :as str]))

;;; Configuration

(def TRACK-PARAM-KEYS
  [:amp :pan :decay :attack :release :synth :bank :rate :begin :end])

;; Parameters that get interactive sliders, with their range config
(def SLIDER-PARAMS
  {:amp     {:min 0    :max 1   :step 0.01}
   :pan     {:min -1   :max 1   :step 0.01}
   :decay   {:min 0    :max 4   :step 0.01}
   :attack  {:min 0    :max 2   :step 0.001}
   :release {:min 0    :max 4   :step 0.01}
   :rate    {:min 0.1  :max 4   :step 0.01}
   :begin   {:min 0    :max 1   :step 0.01}
   :end     {:min 0    :max 1   :step 0.01}})

;; FX slider config: {effect-name {param-name {:min :max :step}}}
(def FX-SLIDER-PARAMS
  {"reverb"          {"wet"      {:min 0   :max 1    :step 0.01}}
   "dattorro-reverb" {"wet"      {:min 0   :max 1    :step 0.01}}
   "delay"           {"time"     {:min 0   :max 2    :step 0.01}
                      "feedback" {:min 0   :max 0.95 :step 0.01}
                      "wet"      {:min 0   :max 1    :step 0.01}}
   "filter"          {"freq"     {:min 20  :max 8000 :step 1}
                      "q"        {:min 0.1 :max 20   :step 0.1}}
   "chorus"          {"wet"      {:min 0   :max 1    :step 0.01}
                      "rate"     {:min 0.1 :max 10   :step 0.1}}
   "phaser"          {"wet"      {:min 0   :max 1    :step 0.01}
                      "rate"     {:min 0.1 :max 10   :step 0.1}}
   "tremolo"         {"depth"    {:min 0   :max 1    :step 0.01}
                      "rate"     {:min 0.1 :max 20   :step 0.1}}
   "overdrive"       {"drive"    {:min 0   :max 1    :step 0.01}}
   "distort"         {"drive"    {:min 1   :max 100   :step 0.1}
                      "tone"     {:min 200 :max 20000 :step 1}
                      "mix"      {:min 0   :max 1     :step 0.01}
                      "asym"     {:min -1  :max 1     :step 0.01}
                      "oversample" {:values [1 2 4]}}
   "amp-sim"         {"gain"     {:min 1   :max 100   :step 0.1}
                      "stages"   {:min 1   :max 4     :step 1}
                      "tone"     {:min 200 :max 20000 :step 1}
                      "sag"      {:min 0   :max 1     :step 0.01}
                      "mix"      {:min 0   :max 1     :step 0.01}
                      "oversample" {:values [1 2 4]}}
   "cab"             {"mix"      {:min 0   :max 1     :step 0.01}}
   "bitcrusher"      {"wet"       {:min 0    :max 1   :step 0.01}}
   "sidechain"       {"amount"    {:min 0    :max 1   :step 0.01}}
   "compressor"      {"wet"       {:min 0    :max 1   :step 0.01}
                      "threshold" {:min -60  :max 0   :step 0.5}
                      "ratio"     {:min 1    :max 20  :step 0.5}
                      "attack"    {:min 0    :max 1   :step 0.001}
                      "release"   {:min 0    :max 1   :step 0.01}
                      "knee"      {:min 0    :max 40  :step 0.5}}})

;; FX select config: {effect-name {param-name [option-name ...]}}
(def FX-SELECT-PARAMS
  {"amp-sim" {"tonestack" ["neutral" "bright" "dark" "mid-scoop" "mid-hump"]}
   "cab" {"ir" ["1x12" "2x12" "4x12" "di"]}})

;; Which getParams key corresponds to the positional (fx :name NUMBER) form
(def FX-PRIMARY-PARAM
  {"reverb"          "wet"
   "dattorro-reverb" "wet"
   "delay"           "time"
   "filter"          "freq"
   "chorus"          "wet"
   "phaser"          "wet"
   "tremolo"         "depth"
   "overdrive"       "drive"
   "distort"         "drive"
   "amp-sim"         "gain"
   "cab"             "mix"
   "bitcrusher"      "wet"
   "sidechain"       "amount"
   "compressor"      "wet"})

;;; Formatting

(defn fmt-pv
  "Display a parameter value: integers without decimals, other numbers with 2."
  [v]
  (if (number? v)
    (if (== v (Math/round v)) (str (int v)) (.toFixed v 2))
    (str v)))

;;; Snapping and mapping

(defn- clamp [lo hi x] (max lo (min hi x)))
(defn- clamp01 [x] (clamp 0 1 x))

(defn decimals
  "Digits after the decimal point of `step` (0.01 → 2, 1 → 0)."
  [step]
  (let [s (str step)
        i (str/index-of s ".")]
    (if i (- (count s) i 1) 0)))

(defn- nearest-index [values v]
  (first (apply min-key #(Math/abs (- (second %) v))
                (reverse (map-indexed vector values)))))

(defn snap
  "Snap `v` to the config: nearest :values entry, else clamp to [min max] and
   round to the step grid (without float noise)."
  [{:keys [values min max step]} v]
  (if (seq values)
    (nth values (nearest-index values v))
    (let [x (clamp min max v)
          x (+ min (* (Math/round (/ (- x min) step)) step))]
      (js/parseFloat (.toFixed x (decimals step))))))

(defn value->t
  "Position 0..1 of `v` on the slider."
  [{:keys [values min max]} v]
  (if (seq values)
    (let [n (count values)]
      (if (<= n 1) 0 (/ (nearest-index values v) (dec n))))
    (if (== max min) 0 (clamp01 (/ (- v min) (- max min))))))

(defn t->value
  "Snapped value at slider position t (0..1)."
  [{:keys [values min max] :as cfg} t]
  (let [t (clamp01 t)]
    (if (seq values)
      (nth values (Math/round (* t (dec (count values)))))
      (snap cfg (+ min (* t (- max min)))))))

(defn drag-value
  "Value after dragging `dx` px on a `width` px track from value v0;
   fine? (⇧) moves 10× slower."
  [cfg v0 dx width fine?]
  (t->value cfg (clamp01 (+ (value->t cfg v0)
                            (* (/ dx width) (if fine? 0.1 1))))))

(defn key-step
  "One keyboard step (dir ±1) from v; coarse? (⇧) = ten steps."
  [{:keys [values step] :as cfg} v dir coarse?]
  (let [n (if coarse? 10 1)]
    (if (seq values)
      (let [i (+ (nearest-index values v) (* dir n))]
        (nth values (clamp 0 (dec (count values)) i)))
      (snap cfg (+ v (* dir step n))))))

;;; Text diff

(defn text-diff
  "Minimal single replacement turning `old` into `new`:
   {:from :to :insert} replaces old[from,to) with insert; nil when equal."
  [old new]
  (when-not (= old new)
    (let [lo (count old)
          ln (count new)
          p  (loop [i 0]
               (if (and (< i lo) (< i ln) (= (.charAt old i) (.charAt new i)))
                 (recur (inc i))
                 i))
          maxs (- (min lo ln) p)
          s  (loop [j 0]
               (if (and (< j maxs)
                        (= (.charAt old (- lo j 1)) (.charAt new (- ln j 1))))
                 (recur (inc j))
                 j))]
      {:from p :to (- lo s) :insert (subs new p (- ln s))})))
