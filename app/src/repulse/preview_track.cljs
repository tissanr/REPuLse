(ns repulse.preview-track
  "Shared helpers for reserved snippet preview track names."
  (:require [clojure.string :as cstr]))

(def prefix "__preview__")

(defn preview-track-name [track-name]
  (keyword (str prefix "-" (name track-name))))

(defn preview-track? [track-name]
  (and (keyword? track-name)
       (let [n (name track-name)]
         (or (= prefix n)
             (cstr/starts-with? n (str prefix "-"))))))
