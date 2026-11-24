(ns repulse.kits
  "KIT1: pure model of layered sample banks (velocity layers + round-robin takes).
   No DOM, no Web Audio. samples.cljs loads manifests with it and plays through it;
   formulas are specified in PROMPTS/PHASE-KIT1.md."
  (:require [repulse.core :as core]
            [clojure.string :as str]))

(def manifest-format "repulse-kit/1")

(def ^:private max-gain 4.0)

(defn- num? [x] (and (number? x) (not (js/isNaN x)) (js/isFinite x)))

(defn parse-entry
  "A manifest entry value (already js->clj'd) → {:layers [{:v :gain :urls}] :choke s|nil}
   with every file prefixed by base, or {:error reason} when malformed. Returns nil
   when the value is not a layered entry at all (array values stay with the flat parser)."
  [v base]
  (when (and (map? v) (contains? v "layers"))
    (let [layers (get v "layers")
          choke  (get v "choke")
          base   (or base "")]
      (cond
        (not (and (vector? layers) (seq layers)))
        {:error "layers must be a non-empty array"}

        (not (or (nil? choke) (string? choke)))
        {:error "choke must be a string"}

        :else
        (let [parsed
              (mapv (fn [l]
                      (let [vv    (when (map? l) (get l "v"))
                            gain  (if (and (map? l) (contains? l "gain")) (get l "gain") 1)
                            files (when (map? l) (get l "files"))]
                        (cond
                          (not (and (num? vv) (< 0 vv) (<= vv 1)))
                          {:error "layer v must be a number in (0, 1]"}
                          (not (and (num? gain) (< 0 gain) (<= gain max-gain)))
                          {:error "layer gain must be a number in (0, 4]"}
                          (not (and (vector? files) (seq files) (every? string? files)))
                          {:error "layer files must be a non-empty array of strings"}
                          :else
                          {:v (double vv) :gain (double gain)
                           :urls (mapv #(str base %) files)})))
                    layers)]
          (if-let [e (some :error parsed)]
            {:error e}
            (let [vs (map :v parsed)]
              (cond
                (not (apply < vs))        {:error "layer v values must be strictly ascending"}
                (not= 1.0 (last vs))      {:error "the last layer must have v = 1"}
                :else {:layers parsed
                       :choke  (when (and choke (not (str/blank? choke))) choke)}))))))))

(defn clamp-velocity
  "Event velocity clamped to 0–1; anything non-numeric is 1.0 (top layer)."
  [vel]
  (if (num? vel) (max 0.0 (min 1.0 (double vel))) 1.0))

(defn pick-layer
  "[index layer] of the first layer whose :v ≥ vel (the last layer if none)."
  [layers vel]
  (let [vel (clamp-velocity vel)
        i   (or (first (keep-indexed (fn [i l] (when (<= vel (:v l)) i)) layers))
                (dec (count layers)))]
    [i (nth layers i)]))

(defn layer-scale
  "In-layer level scale s for velocity vel inside layer i (see Formulas)."
  [layers i vel]
  (let [vel (clamp-velocity vel)
        hi  (:v (nth layers i))]
    (if (zero? i)
      (/ vel hi)
      (let [lo (:v (nth layers (dec i)))]
        (+ 0.6 (* 0.4 (/ (- vel lo) (- hi lo))))))))

(defn- name-salt [bank]
  (mod (reduce (fn [h c] (bit-or 0 (+ (* 31 h) (.charCodeAt c 0)))) 7 (name bank)) 997))

(defn pick-take
  "Round-robin: index of the take to play among k, seeded by the event onset
   (a rational [n d]; nil counts as 0) and bank. Never equals last (the take this
   bank+layer played before) when k ≥ 2; last nil means no previous take."
  [k last onset bank]
  (if (<= k 1)
    0
    (let [t (core/rat+ (core/->rat (or onset 0)) [(name-salt bank) 1])
          x (core/time->rand t)]
      (if (nil? last)
        (min (dec k) (int (Math/floor (* x k))))
        (let [r (min (- k 2) (int (Math/floor (* x (dec k)))))]
          (if (< r last) r (inc r)))))))

(defn flatten-urls
  "All files of a layered entry, low → high layer, take order within a layer."
  [layers]
  (into [] (mapcat :urls) layers))

(defn level
  "Linear level amp × gain × layer-gain × in-layer scale for a layer pick."
  [layers i vel amp-gain]
  (* amp-gain (:gain (nth layers i)) (layer-scale layers i vel)))

(defn kit-prefix
  "The kit part of a layered bank name: \"JazzKit_bd\" → \"JazzKit\"."
  [bank]
  (first (str/split (name bank) #"_" 2)))

(defn choke-group
  "TL6 cut group of a layered entry, or nil. A vector, so it can never collide
   with a user's numeric (cut n)."
  [bank entry]
  (when-let [c (:choke entry)] [:kit (kit-prefix bank) c]))
