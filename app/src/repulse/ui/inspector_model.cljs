(ns repulse.ui.inspector-model
  "Pure inspector model (Phase WS1).  Input is plain data collected by the
   DOM layer (repulse.ui.inspector): event values, fx chains with their
   params, the declared track flags and the muted set.
   Exports: track-params, param-rows, fx-rows, track-flags, mute-action,
            solo-action, section-tracks."
  (:require [repulse.ui.slider-model :as sm]))

(defn track-params
  "First value per key of slider-model/TRACK-PARAM-KEYS over a seq of event
   values (non-map values are ignored)."
  [values]
  (reduce (fn [acc m]
            (reduce (fn [a k]
                      (if (and (contains? m k) (not (contains? a k)))
                        (assoc a k (get m k))
                        a))
                    acc sm/TRACK-PARAM-KEYS))
          {} (filter map? values)))

(defn- text-of [v]
  (if (keyword? v) (name v) (sm/fmt-pv v)))

(defn param-rows
  "{:sliders [[k v cfg] …] :text [[k \"str\"] …]} in TRACK-PARAM-KEYS order:
   numeric values with a SLIDER-PARAMS entry are sliders, the rest is text."
  [params]
  (reduce (fn [acc k]
            (if (contains? params k)
              (let [v   (get params k)
                    cfg (get sm/SLIDER-PARAMS k)]
                (if (and cfg (number? v))
                  (update acc :sliders conj [k v cfg])
                  (update acc :text conj [k (text-of v)])))
              acc))
          {:sliders [] :text []}
          sm/TRACK-PARAM-KEYS))

(defn fx-rows
  "chain = [{:name :bypassed? :params {pname v}}] in signal order →
   [{:name :pos :bypassed? :sliders [[pname v cfg] …] :selects [[pname v opts] …]}].
   Control order follows the effect's FX-SLIDER-PARAMS / FX-SELECT-PARAMS entry;
   params missing from :params are omitted; bypassed effects get no controls."
  [chain]
  (vec (map-indexed
         (fn [i {:keys [name bypassed? params]}]
           (let [on? (not bypassed?)]
             {:name      name
              :pos       (inc i)
              :bypassed? (boolean bypassed?)
              :sliders   (if on?
                           (vec (keep (fn [[pname cfg]]
                                        (let [v (get params pname)]
                                          (when (number? v) [pname v cfg])))
                                      (get sm/FX-SLIDER-PARAMS name)))
                           [])
              :selects   (if on?
                           (vec (keep (fn [[pname opts]]
                                        (when (contains? params pname)
                                          [pname (get params pname) opts]))
                                      (get sm/FX-SELECT-PARAMS name)))
                           [])}))
         chain)))

(defn track-flags
  "Phase WS5: M/S state from the code. declared — {kw {:mute? :solo?}} of the
   last reconciling evaluation; muted — the scheduler's muted set.
   → {:muted? (has :mute) :solo? (has :solo) :silenced? (silent only because
   another track is soloed)}."
  [declared muted name]
  (let [{:keys [mute? solo?]} (get declared name)]
    {:muted?    (boolean mute?)
     :solo?     (boolean solo?)
     :silenced? (boolean (and (not mute?) (contains? (or muted #{}) name)))}))

(defn mute-action
  "[:flag name :mute on?] — toggles the :mute flag on the track's form."
  [flags name]
  [:flag name :mute (not (:muted? flags))])

(defn solo-action
  "[:flag name :solo on?] — toggles the :solo flag (several tracks may be
   soloed at once)."
  [flags name]
  [:flag name :solo (not (:solo? flags))])

(defn section-tracks
  "Track names of an outline section; [\":all\"] → every track entry name in
   source order."
  [section track-entries]
  (if (= [":all"] (:tracks section))
    (mapv :name track-entries)
    (vec (:tracks section))))
