(ns repulse.ui.viz-model
  "Phase VIZ1 — pure model behind the track panel and the in-code visualizers.
   No DOM, no audio: the heard-clock arithmetic, the event window, event
   layout (pitch rows, drum lanes, opacity, labels), option validation and the
   scope trigger. Drawing lives in repulse.ui.viz-draw.
   Exports: viz-kinds, opt-specs, heard-cycle, window, grid-window, query-window,
            layout-events, rows, validate-opts, default-opts, scope-trigger-index,
            song-resting-tracks."
  (:require [clojure.string :as str]
            [repulse.core :as core]
            [repulse.song :as song]
            [repulse.voices :as voices]
            [repulse.midi :as midi]
            [repulse.edit-distance :as ed]))

;;; ── Visualizer kinds and their options (spec: "Options (exact)") ─────────

(def viz-kinds
  "The in-code visualizer built-ins, in display order."
  [:pianoroll :punchcard :spiral :scope :spectrum])

(def event-kinds #{:pianoroll :punchcard :spiral})
(def audio-kinds #{:scope :spectrum :level})

(def opt-specs
  "kind → [[option default type min max] …] in documentation order."
  {:pianoroll [[:cycles 4 :num 1 16] [:playhead 0.5 :num 0 1] [:labels false :bool]
               [:fold true :bool] [:height 80 :num 32 240]]
   :punchcard [[:cycles 1 :num 1 16] [:labels true :bool] [:height 48 :num 32 240]]
   :spiral    [[:cycles 2 :num 1 8] [:height 120 :num 32 240]]
   :scope     [[:scale 1 :num 0.1 10] [:trigger true :bool] [:height 64 :num 32 240]]
   :spectrum  [[:log true :bool] [:height 64 :num 32 240]]
   ;; panel-only view (no built-in)
   :level     [[:height 48 :num 32 240]]})

(defn default-opts [kind]
  (into {} (map (fn [[k d]] [k d])) (get opt-specs kind)))

(defn- num-val
  "A number from a Lisp value: numbers as-is, rationals [n d] → float."
  [v]
  (when-not (boolean? v) (core/as-number v)))

(defn- show [v]
  (cond (string? v) (pr-str v)
        (keyword? v) (str v)
        (nil? v) "nil"
        :else (str (or (num-val v) v))))

(defn validate-opts
  "kind — a viz kind keyword; opts — a map (or nil). → {:ok opts'} with the
   defaults filled in and numbers as floats, or {:error msg} with the exact
   texts of the spec."
  [kind opts]
  (let [nm    (name kind)
        specs (get opt-specs kind)
        known (mapv first specs)
        by-k  (into {} (map (juxt first identity)) specs)]
    (cond
      (and (some? opts) (not (map? opts)))
      {:error (str nm " expects an options map, got " (show opts))}

      :else
      (or (some (fn [[k v]]
                  (if-let [[_ _ typ lo hi] (get by-k k)]
                    (case typ
                      :bool (when-not (boolean? v)
                              {:error (str nm ": " k " must be true or false, got " (show v))})
                      :num  (let [n (num-val v)]
                              (cond
                                (nil? n)
                                {:error (str nm ": " k " must be a number, got " (show v))}
                                (not (<= lo n hi))
                                {:error (str nm ": " k " must be between " lo " and " hi ", got " (show v))})))
                    (let [kn   (if (keyword? k) (name k) (str k))
                          hint (ed/typo-hint kn (map name known))]
                      {:error (if hint
                                (str nm ": unknown option " (show k) " — did you mean :" hint "?")
                                (str nm ": unknown option " (show k) " — known options: "
                                     (str/join " " known)))})))
                opts)
          {:ok (merge (default-opts kind)
                      (into {} (map (fn [[k v]]
                                      [k (if (boolean? v) v (num-val v))]))
                            opts))}))))

;;; ── Heard clock and windows ────────────────────────────────────────────

(defn heard-cycle
  "Float cycle position the listener hears at AudioContext time ctx-time with
   output latency `latency` seconds. cycle-at-fn maps a context time to a
   cycle position (tempo/cycle-at-time in the app)."
  [cycle-at-fn ctx-time latency]
  (cycle-at-fn (- ctx-time latency)))

(defn window
  "[from to] cycle floats of a scrolling view: :cycles wide, the heard
   position at fraction :playhead of the width."
  [heard {:keys [cycles playhead] :or {cycles 4 playhead 0.5}}]
  [(- heard (* cycles playhead)) (+ heard (* cycles (- 1 playhead)))])

(defn grid-window
  "[from to] of a static view of whole cycles (punchcard, spiral): the block
   of `cycles` cycles that contains the heard position."
  [heard cycles]
  (let [c (max 1 (Math/round cycles))
        from (* c (Math/floor (/ heard c)))]
    [from (+ from c)]))

;;; ── Events ────────────────────────────────────────────────────────────

(defn query-window
  "Events of pattern overlapping the float span [from to): queried over the
   whole cycles that contain it, one event per whole (fragments of an event
   that crosses a cycle boundary are merged). Throws like core/query."
  [pattern from to]
  (let [c0 (Math/floor from)
        c1 (max (inc c0) (Math/ceil to))
        evs (core/query pattern {:start [c0 1] :end [c1 1]})]
    (->> evs
         (map (fn [ev] [(or (:whole ev) (:part ev)) ev]))
         (reduce (fn [[seen out] [w ev]]
                   (let [k [(:start w) (:end w) (:value ev) (nil? (:whole ev))]]
                     (if (contains? seen k) [seen out] [(conj seen k) (conj out ev)])))
                 [#{} []])
         second)))

(defn- rest-value? [v]
  (or (= v :_) (and (map? v) (= :_ (:note v)))))

(defn- sound-name
  "Name of an unpitched event's sound: :bd → \"bd\", {:bank :bd} → \"bd\"."
  [v]
  (let [x (cond (keyword? v) v
                (map? v) (or (:note v) (:bank v) (:synth v) (:sample v))
                :else v)]
    (cond (keyword? x) (name x)
          (nil? x) "?"
          :else (str x))))

(defn- label-of [v hz]
  (cond
    (keyword? v) (name v)
    (and (map? v) (keyword? (:note v))) (name (:note v))
    (and (map? v) (:bank v)) (str (name (:bank v)) (when (:n v) (str ":" (:n v))))
    hz (str (Math/round hz))
    (map? v) (sound-name v)
    :else (str v)))

(defn- clamp01 [x] (max 0 (min 1 x)))

(defn layout-events
  "Events → [{:start :end :continuous? :midi :lane :alpha :label :value}]:
   times as floats from :whole (from :part for continuous events, flagged
   :continuous?), :midi from the played pitch (nil when unpitched; then
   :lane is the sound name), :alpha = 0.25 + 0.75·amp clamped to 0–1.
   Rests are dropped."
  [events]
  (vec
   (keep (fn [ev]
           (let [v (:value ev)]
             (when-not (rest-value? v)
               (let [w   (:whole ev)
                     sp  (or w (:part ev))
                     hz  (voices/played-hz v)
                     hz  (when (and (number? hz) (pos? hz) (js/isFinite hz)) hz)
                     amp (let [a (when (map? v) (num-val (:amp v)))] (if (number? a) a 1))]
                 {:start       (core/rat->float (:start sp))
                  :end         (core/rat->float (:end sp))
                  :continuous? (nil? w)
                  :midi        (when hz (midi/hz->midi hz))
                  :lane        (when-not hz (sound-name v))
                  :alpha       (clamp01 (+ 0.25 (* 0.75 amp)))
                  :label       (label-of v hz)
                  :value       v}))))
         events)))

(defn rows
  "{:pitch-rows [midi …] (ascending) :drum-lanes [name …] (first appearance,
   by start time)} of laid-out events. :fold true keeps only used pitches;
   false fills the whole range lowest–highest."
  [laid-out {:keys [fold] :or {fold true}}]
  (let [midis (sort (distinct (keep :midi laid-out)))
        lanes (->> (sort-by :start laid-out)
                   (keep :lane)
                   distinct
                   vec)]
    {:pitch-rows (vec (if (or fold (empty? midis))
                        midis
                        (range (first midis) (inc (last midis)))))
     :drum-lanes lanes}))

;;; ── Scope ─────────────────────────────────────────────────────────────

(defn scope-trigger-index
  "Index of the first rising zero crossing of samples (a[i-1] < 0 ≤ a[i]),
   searched in the first half so a full trace remains; 0 when there is none."
  [samples]
  (let [n (.-length samples)
        lim (quot n 2)]
    (loop [i 1]
      (cond
        (>= i lim) 0
        (and (< (aget samples (dec i)) 0) (>= (aget samples i) 0)) i
        :else (recur (inc i))))))

;;; ── Song mode ─────────────────────────────────────────────────────────

(defn song-resting-tracks
  "In song mode: the set of tracks that do not play in the song bar of the
   scheduler cycle `audible` (default: the cycle before the next one to
   schedule) — drawn like muted tracks. nil without an active song."
  ([state] (song-resting-tracks state (dec (:cycle state))))
  ([{:keys [tracks] :as state} audible]
   (when-let [{s :song origin :origin} (:song state)]
     (let [q      (when origin (song/wrap-bar s (- audible origin)))
           active (if q (song/active-tracks-at-bar s q) #{})]
       (set (remove active (keys tracks)))))))
