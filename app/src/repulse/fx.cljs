(ns repulse.fx
  (:require [repulse.audio :as audio]))

;; Ordered vector of {:name "reverb" :plugin js-obj :input node :output node}
(defonce chain (atom []))

(defn- rewire!
  "Reconnect the chain: masterGain → effect1 → effect2 → ... → analyser.
   Disconnects all existing outputs first, then rebuilds in order."
  []
  (let [gain    @audio/master-gain
        anl     @audio/analyser-node
        effects @chain]
    (.disconnect gain)
    (doseq [{:keys [output]} effects]
      (.disconnect output))
    (if (empty? effects)
      (.connect gain anl)
      (do
        (.connect gain (:input (first effects)))
        (doseq [[a b] (partition 2 1 effects)]
          (.connect (:output a) (:input b)))
        (.connect (:output (last effects)) anl)))))

(defn add-effect! [^js plugin]
  (let [ac    (audio/get-ctx)
        nodes (.createNodes plugin ac)]
    (swap! chain conj {:name      (.-name plugin)
                       :plugin    plugin
                       :input     (.-inputNode nodes)
                       :output    (.-outputNode nodes)
                       :bypassed? false})
    (rewire!)))

(defn remove-effect! [effect-name]
  (when-let [entry (some #(when (= effect-name (:name %)) %) @chain)]
    (.destroy ^js (:plugin entry))
    (swap! chain #(filterv (fn [e] (not= effect-name (:name e))) %))
    (rewire!)))

(defn set-param! [effect-name param-name value]
  (when-let [entry (some #(when (= effect-name (:name %)) %) @chain)]
    (.setParam ^js (:plugin entry) param-name value)))

(defn bypass! [effect-name enabled]
  (when-let [entry (some #(when (= effect-name (:name %)) %) @chain)]
    (.bypass ^js (:plugin entry) enabled)
    (swap! chain (fn [c] (mapv #(if (= effect-name (:name %)) (assoc % :bypassed? enabled) %) c)))))

;;; ── Per-track FX chains ───────────────────────────────────────────────────

(defn- rewire-track!
  "Reconnect a track's FX chain: trackGain → fx1 → fx2 → ... → masterGain."
  [track-name]
  (when-let [tn (get @audio/track-nodes track-name)]
    (let [gain    (:gain-node tn)
          effects (:fx-chain tn)
          master  @audio/master-gain]
      (.disconnect gain)
      (doseq [{:keys [output]} effects]
        (try (.disconnect output) (catch :default _)))
      (if (empty? effects)
        (.connect gain master)
        (do
          (.connect gain (:input (first effects)))
          (doseq [[a b] (partition 2 1 effects)]
            (.connect (:output a) (:input b)))
          (.connect (:output (last effects)) master))))))

(defn add-track-effect!
  "Instantiate an effect plugin on a specific track by re-using a registered plugin."
  [track-name effect-name]
  (when-let [entry (some #(when (= effect-name (:name %)) %) @chain)]
    (let [ac     (audio/get-ctx)
          ^js p  (:plugin entry)
          ;; Create a fresh instance by cloning the prototype-based plugin object
          ^js fresh (let [o (js/Object.create (.getPrototypeOf js/Object p))]
                      (js/Object.assign o p)
                      o)
          nodes  (.createNodes fresh ac)]
      (swap! audio/track-nodes update-in [track-name :fx-chain]
             conj {:name      effect-name
                   :plugin    fresh
                   :input     (.-inputNode nodes)
                   :output    (.-outputNode nodes)
                   :bypassed? false})
      (rewire-track! track-name))))

(defn remove-track-effect!
  "Remove a specific effect from a track's FX chain."
  [track-name effect-name]
  (when-let [tn (get @audio/track-nodes track-name)]
    (when-let [entry (some #(when (= effect-name (:name %)) %) (:fx-chain tn))]
      (try (.destroy ^js (:plugin entry)) (catch :default _))
      (swap! audio/track-nodes update-in [track-name :fx-chain]
             (fn [c] (filterv #(not= effect-name (:name %)) c)))
      (rewire-track! track-name))))

(defn clear-track-effects!
  "Remove all effects from a track's FX chain."
  [track-name]
  (when-let [tn (get @audio/track-nodes track-name)]
    (doseq [{:keys [plugin]} (:fx-chain tn)]
      (try (.destroy ^js plugin) (catch :default _)))
    (swap! audio/track-nodes assoc-in [track-name :fx-chain] [])
    (rewire-track! track-name)))

(defn set-track-param!
  "Set a parameter on a named effect in a track's chain."
  [track-name effect-name param-name value]
  (when-let [tn (get @audio/track-nodes track-name)]
    (when-let [entry (some #(when (= effect-name (:name %)) %) (:fx-chain tn))]
      (.setParam ^js (:plugin entry) param-name value))))

(defn bypass-track-effect!
  "Bypass or un-bypass an effect on a specific track."
  [track-name effect-name enabled]
  (when-let [tn (get @audio/track-nodes track-name)]
    (when-let [entry (some #(when (= effect-name (:name %)) %) (:fx-chain tn))]
      (try (.bypass ^js (:plugin entry) enabled) (catch :default _))
      (swap! audio/track-nodes update-in [track-name :fx-chain]
             (fn [c] (mapv #(if (= effect-name (:name %))
                               (assoc % :bypassed? enabled) %) c))))))

;;; ── Sidechain event notification ─────────────────────────────────────────

(defn- event-name
  "Extract the sound name from an event value for plugin matching."
  [value]
  (cond
    (keyword? value) (name value)
    (and (map? value) (:note value)) (name (:note value))
    (and (map? value) (:synth value)) (cljs.core/name (:synth value))
    :else nil))

(defn notify-fx-event!
  "Notify all plugins (global and per-track) that implement onEvent of a fired event.
   Called via :on-fx-event in the scheduler — fx → audio dependency is one-way."
  [value t]
  (when-let [evt (event-name value)]
    ;; Global effects
    (doseq [{:keys [plugin]} @chain]
      (when (.-onEvent ^js plugin)
        (try (.onEvent ^js plugin evt t) (catch :default _))))
    ;; Per-track effects
    (doseq [[_ {:keys [fx-chain]}] @audio/track-nodes]
      (doseq [{:keys [plugin]} fx-chain]
        (when (.-onEvent ^js plugin)
          (try (.onEvent ^js plugin evt t) (catch :default _)))))))
