(ns repulse.plugins
  (:require [clojure.string :as str]))

;; Map of plugin-name → {:plugin js-obj :type keyword}
(defonce registry (atom {}))

;; ─── Validation ──────────────────────────────────────────────────────────────

(def ^:private visual-methods ["init" "mount" "unmount" "destroy"])
(def ^:private effect-methods ["init" "createNodes" "setParam" "bypass" "getParams" "destroy"])

(defn- validate! [^js plugin]
  (let [ptype    (.-type plugin)
        pname    (or (.-name plugin) "<unnamed>")
        required (case ptype
                   "visual" visual-methods
                   "effect" effect-methods
                   (throw (js/Error. (str "[REPuLse] Unknown plugin type: '" ptype
                                         "' — expected \"visual\" or \"effect\""))))
        missing  (filterv #(not (fn? (aget plugin %))) required)]
    (when (seq missing)
      (throw (js/Error. (str "[REPuLse] Plugin \"" pname
                             "\" is missing required method(s): "
                             (str/join ", " missing)))))))

;; ─── Registry ────────────────────────────────────────────────────────────────

(defn register! [^js plugin ^js host]
  (validate! plugin)
  (let [n (.-name plugin)]
    ;; Destroy existing registration before replacing
    (when-let [{old :plugin} (get @registry n)]
      (.destroy ^js old))
    (swap! registry assoc n {:plugin plugin :type (keyword (.-type plugin))})
    (.init plugin host)))

(defn unregister! [plugin-name]
  (when-let [{old :plugin} (get @registry plugin-name)]
    (.destroy ^js old)
    (swap! registry dissoc plugin-name)))

(defn visual-plugins []
  (filter #(= :visual (:type %)) (vals @registry)))
