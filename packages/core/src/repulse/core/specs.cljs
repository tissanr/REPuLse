(ns repulse.core.specs
  (:require [cljs.spec.alpha :as s]
            [repulse.core :as core]))

(defn rational-vector?
  "True for REPuLse's normalized [numerator denominator] rational form."
  [x]
  (if (and (vector? x) (= 2 (count x)))
    (let [[n d] x]
      (and (integer? n)
           (integer? d)
           (pos? d)
           (= x (core/rat n d))))
    false))

(defn span?
  [x]
  (and (map? x)
       (rational-vector? (:start x))
       (rational-vector? (:end x))
       (core/rat< (:start x) (:end x))))

(defn event?
  [x]
  (and (map? x)
       (contains? x :value)
       (span? (:whole x))
       (span? (:part x))))

(s/def ::rat rational-vector?)
(s/def ::span span?)
(s/def ::event event?)
(s/def ::pattern core/pattern?)

(defn valid-rat? [x] (s/valid? ::rat x))
(defn valid-span? [x] (s/valid? ::span x))
(defn valid-event? [x] (s/valid? ::event x))
(defn valid-pattern? [x] (s/valid? ::pattern x))
