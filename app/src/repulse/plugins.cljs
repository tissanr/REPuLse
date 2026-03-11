(ns repulse.plugins)

;; Map of plugin-name → {:plugin js-obj :type keyword}
(defonce registry (atom {}))

(defn register! [plugin host]
  (let [n (.-name plugin)]
    ;; Destroy existing registration before replacing
    (when-let [{:keys [plugin]} (get @registry n)]
      (.destroy plugin))
    (swap! registry assoc n {:plugin plugin :type (keyword (.-type plugin))})
    (.init plugin host)))

(defn unregister! [plugin-name]
  (when-let [{:keys [plugin]} (get @registry plugin-name)]
    (.destroy plugin)
    (swap! registry dissoc plugin-name)))

(defn visual-plugins []
  (filter #(= :visual (:type %)) (vals @registry)))
