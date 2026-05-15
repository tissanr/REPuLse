(ns repulse.plugins
  (:require [clojure.string :as str]
            [repulse.specs :as specs]))

;; Map of plugin-name → {:plugin js-obj :type keyword}
(defonce registry (atom {}))

;; ─── Validation and normalization ────────────────────────────────────────────

(def ^:private visual-required ["mount" "unmount"])
(def ^:private effect-required ["createNodes" "setParam" "destroy"])

(defn- has-method? [^js plugin method]
  (fn? (aget plugin method)))

(defn- ensure-method! [^js plugin method f]
  (when-not (has-method? plugin method)
    (aset plugin method f))
  plugin)

(defn normalize-plugin!
  "Install documented no-op defaults for optional plugin protocol methods."
  [^js plugin]
  (case (.-type plugin)
    "visual"
    (do
      (ensure-method! plugin "init" (fn [_host] nil))
      (ensure-method! plugin "destroy" (fn [] (.unmount plugin)))
      plugin)

    "effect"
    (do
      (ensure-method! plugin "init" (fn [_host] nil))
      (ensure-method! plugin "bypass" (fn [_on?] nil))
      (ensure-method! plugin "getParams" (fn [] #js {}))
      plugin)

    plugin))

(defn validate-plugin! [^js plugin]
  (let [ptype    (.-type plugin)
        pname    (or (.-name plugin) "<unnamed>")
        version  (.-version plugin)
        _        (when-not (specs/non-empty-string? pname)
                   (throw (js/Error. "[REPuLse] Plugin name must be a non-empty string")))
        _        (when (and (some? version) (not (string? version)))
                   (throw (js/Error. (str "[REPuLse] Plugin \"" pname
                                          "\" has invalid version; expected string"))))
        required (case ptype
                   "visual" visual-required
                   "effect" effect-required
                   (throw (js/Error. (str "[REPuLse] Unknown plugin type: '" ptype
                                         "' — expected \"visual\" or \"effect\""))))
        missing  (filterv #(not (has-method? plugin %)) required)]
    (when (seq missing)
      (throw (js/Error. (str "[REPuLse] Plugin \"" pname
                             "\" is missing required method(s): "
                             (str/join ", " missing)))))))

;; ─── Registry ────────────────────────────────────────────────────────────────

(defn register! [^js plugin ^js host]
  (validate-plugin! plugin)
  (normalize-plugin! plugin)
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
