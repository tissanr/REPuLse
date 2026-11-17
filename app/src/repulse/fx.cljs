(ns repulse.fx
  (:require [repulse.audio :as audio]
            [repulse.specs :as specs]))

;; Ordered vector of {:name "reverb" :plugin js-obj :input node :output node}
(defonce chain (atom []))

;; Phase EXP1 (decision E6): effect-name → [[param value] …], the set-param!
;; calls of the current evaluation in order. Replayed by the offline export after
;; getParams, because some plugins (bitcrusher, dattorro-reverb) only report
;; {wet}. Kept beside the chain entries (not in them) so chain entries keep
;; their documented shape.
(defonce param-log (atom {}))

(defn validate-effect-nodes!
  [effect-name nodes]
  (when-not (specs/effect-nodes? nodes)
    (throw (js/Error. (str "[REPuLse] Effect plugin \"" effect-name
                           "\" createNodes(ctx) must return "
                           "{inputNode, outputNode} with connect/disconnect methods"))))
  nodes)

(defn- make-effect-entry [effect-name plugin ^js nodes]
  (let [entry {:name      effect-name
               :plugin    plugin
               :input     (.-inputNode nodes)
               :output    (.-outputNode nodes)
               :bypassed? false
               :active?   false}]
    (when-not (specs/fx-entry? entry)
      (throw (js/Error. (str "[REPuLse] Invalid FX chain entry for \"" effect-name "\""))))
    entry))

(defn- rewire!
  "Reconnect the chain: masterGain → effect1 → effect2 → ... → master-tap.
   master-tap is the fixed end of the master FX chain (it feeds the recorder
   and the analyser). Disconnects all existing outputs first, then rebuilds."
  []
  (let [gain    @audio/master-gain
        anl     @audio/master-tap
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
        nodes (validate-effect-nodes! (.-name plugin) (.createNodes plugin ac))]
    (swap! chain conj (make-effect-entry (.-name plugin) plugin nodes))
    (rewire!)))

(defn remove-effect! [effect-name]
  (when-let [entry (some #(when (= effect-name (:name %)) %) @chain)]
    (.destroy ^js (:plugin entry))
    (swap! chain #(filterv (fn [e] (not= effect-name (:name e))) %))
    (swap! param-log dissoc effect-name)
    (rewire!)))

(defn set-param! [effect-name param-name value]
  (when (some #(= effect-name (:name %)) @chain)
    (swap! chain (fn [c] (mapv #(if (= effect-name (:name %)) (assoc % :active? true) %) c)))
    (swap! param-log update effect-name (fnil conj []) [param-name value])
    (when-let [entry (some #(when (= effect-name (:name %)) %) @chain)]
      (.setParam ^js (:plugin entry) param-name value))))

(defn reset-global-effects!
  "Return global effects to their plugin defaults before evaluating source code.
   This keeps the editor buffer as the source of truth instead of carrying
   parameter values from an earlier eval or hot-reload session."
  []
  (doseq [{:keys [plugin]} @chain]
    (when (and plugin (fn? (.-resetParams ^js plugin)))
      (.resetParams ^js plugin)))
  (reset! param-log {})
  (swap! chain (fn [c] (mapv #(assoc % :active? false :bypassed? false) c))))

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

(defn instantiate-plugin
  "Fresh instance of a registered effect plugin (EXP1 decision 2, shared by
   per-track FX and the offline export): clone() when present, else
   Object.create + Object.assign."
  [^js p]
  ;; Prefer a plugin-provided clone() factory (needed for CLJS plugins
  ;; whose state lives in a closure atom rather than on `this`).
  ;; Fall back to Object.create/assign for plain JS object plugins.
  (if (fn? (.-clone p))
    (.clone p)
    (let [o (js/Object.create (js/Object.getPrototypeOf p))]
      (js/Object.assign o p)
      o)))

(defn add-track-effect!
  "Instantiate an effect plugin on a specific track by re-using a registered plugin."
  [track-name effect-name]
  (when-let [entry (some #(when (= effect-name (:name %)) %) @chain)]
    (let [ac     (audio/get-ctx)
          ^js fresh (instantiate-plugin (:plugin entry))
          nodes  (validate-effect-nodes! effect-name (.createNodes fresh ac))]
      (swap! audio/track-nodes update-in [track-name :fx-chain]
             conj (dissoc (make-effect-entry effect-name fresh nodes) :active?))
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

(defn apply-track-effects!
  "Replace a track's FX chain from pattern metadata."
  [track-name track-fx]
  (clear-track-effects! track-name)
  (doseq [{:keys [name params]} track-fx]
    (if-not (specs/track-fx-entry? {:name name :params params})
      (js/console.warn "[REPuLse] Ignoring invalid track FX entry:" (pr-str {:name name :params params}))
      (do
        (add-track-effect! track-name name)
        (doseq [[k v] params]
          (set-track-param! track-name name k v))))))

;;; ── Sidechain event notification ─────────────────────────────────────────

(defn- event-name
  "Extract the sound name from an event value for plugin matching."
  [value]
  (cond
    (keyword? value) (name value)
    (and (map? value) (:note value)) (let [n (:note value)]
                                       (when (keyword? n) (name n)))
    (and (map? value) (:synth value)) (cljs.core/name (:synth value))
    :else nil))

(defn notify-plugins!
  "Call onEvent(name, t) on every plugin in plugins that implements it."
  [plugins value t]
  (when-let [evt (event-name value)]
    (doseq [^js plugin plugins]
      (when (and plugin (.-onEvent plugin))
        (try (.onEvent plugin evt t) (catch :default _))))))

(defn notify-fx-event!
  "Notify all plugins (global and per-track) that implement onEvent of a fired event.
   Called via :on-fx-event in the scheduler — fx → audio dependency is one-way."
  [value t]
  (notify-plugins! (concat (map :plugin @chain)
                           (for [[_ {:keys [fx-chain]}] @audio/track-nodes
                                 {:keys [plugin]} fx-chain]
                             plugin))
                   value t))
