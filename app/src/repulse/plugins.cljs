(ns repulse.plugins)

;; Map of plugin-name → {:plugin js-obj :type keyword}
(defonce registry (atom {}))

(defn register! [^js plugin ^js host]
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
