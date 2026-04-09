(ns repulse.bus)

;;; ── Named audio / control-rate bus registry ──────────────────────────────
;;
;; Each bus is backed by a Web Audio node:
;;   :audio   → GainNode  (gain=1, transparent mixing point)
;;   :control → ConstantSourceNode  (offset param receives writer signals)
;;
;; Writers connect their output to the bus; readers get the bus node as a
;; UGen source.  Multiple writers can mix into the same bus (additive).
;;
;; Per-synth writer tracking: when the same synth definition re-fires (e.g.
;; on a new cycle) its previous bus connection is stopped and replaced, so
;; only one oscillator per synth→bus pair is active at a time.

(defonce bus-nodes
  ;; keyword → {:type :audio|:control :node AudioNode}
  (atom {}))

(defonce synth-bus-writers
  ;; {synth-keyword {bus-keyword AudioNode}} — for replace-on-retrigger
  (atom {}))

;;; ── Lifecycle ────────────────────────────────────────────────────────────

(defn create-bus!
  "Create (or recreate) a named bus.
   type is :control (default) or :audio."
  [ac name type]
  ;; Destroy any existing bus with this name first
  (when-let [existing (get @bus-nodes name)]
    (let [node (:node existing)]
      (try (when (.-stop node) (.stop node)) (catch :default _))
      (try (.disconnect node) (catch :default _))))
  (let [node (if (= type :audio)
               ;; Audio bus: transparent GainNode (gain=1)
               (let [g (.createGain ac)]
                 (.setValueAtTime (.-gain g) 1.0 (.-currentTime ac))
                 g)
               ;; Control bus: ConstantSourceNode — started immediately
               ;; Writers connect to its .offset AudioParam.
               (let [cs (.createConstantSource ac)]
                 (.setValueAtTime (.-offset cs) 0.0 (.-currentTime ac))
                 (.start cs)
                 cs))]
    (swap! bus-nodes assoc name {:type (or type :control) :node node})))

(defn get-bus-node
  "Return the AudioNode backing a bus, or nil if it does not exist."
  [name]
  (:node (get @bus-nodes name)))

(defn get-bus-type
  "Return :audio or :control for a named bus, or nil if it does not exist."
  [name]
  (:type (get @bus-nodes name)))

(defn connect-to-bus!
  "Connect `writer-node` to the named bus.
   For control buses, connects to the ConstantSourceNode's .offset AudioParam.
   For audio buses, connects to the GainNode input."
  [bus-name writer-node]
  (when-let [bus (get @bus-nodes bus-name)]
    (let [node (:node bus)]
      (if (= (:type bus) :control)
        (.connect writer-node (.-offset node))
        (.connect writer-node node)))))

(defn replace-synth-writer!
  "Start a new writer connection for `synth-name` → `bus-name`, stopping
   the previous instance of this synth on that bus (if any).
   This prevents oscillator accumulation when a synth fires once per cycle."
  [synth-name bus-name writer-node]
  ;; Stop and disconnect the previous instance of this synth on this bus
  (when-let [old (get-in @synth-bus-writers [synth-name bus-name])]
    (try (when (.-stop old) (.stop old 0)) (catch :default _))
    (try (.disconnect old) (catch :default _)))
  ;; Register the new writer
  (swap! synth-bus-writers assoc-in [synth-name bus-name] writer-node)
  ;; Connect it to the bus
  (connect-to-bus! bus-name writer-node))

(defn cleanup-all!
  "Stop and disconnect all bus nodes and all tracked synth writers.
   Called by audio/stop! and audio/clear-track! when all tracks are removed."
  []
  ;; Stop all tracked synth writers
  (doseq [[_ bus-map] @synth-bus-writers
          [_ node]    bus-map]
    (try (when (.-stop node) (.stop node 0)) (catch :default _))
    (try (.disconnect node) (catch :default _)))
  ;; Stop and disconnect bus nodes themselves
  (doseq [[_ bus] @bus-nodes]
    (let [node (:node bus)]
      (try (when (.-stop node) (.stop node)) (catch :default _))
      (try (.disconnect node) (catch :default _))))
  (reset! synth-bus-writers {})
  (reset! bus-nodes {}))

(defn active-buses
  "Return a map of bus-name → {:type :audio|:control} for the context panel."
  []
  (into {} (map (fn [[k v]] [k (select-keys v [:type])]) @bus-nodes)))
