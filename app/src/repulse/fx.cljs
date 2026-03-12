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
    (swap! chain conj {:name   (.-name plugin)
                       :plugin plugin
                       :input  (.-inputNode nodes)
                       :output (.-outputNode nodes)})
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
    (.bypass ^js (:plugin entry) enabled)))
