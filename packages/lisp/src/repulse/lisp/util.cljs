(ns repulse.lisp.util
  "Shared helpers for the Lisp evaluator and builtin sub-namespaces.
   Extracted from eval.cljs so builtins can require them without creating
   circular dependencies."
  (:require [repulse.lisp.reader :as reader]))

(defn sourced? [x] (instance? reader/SourcedVal x))

(defn unwrap
  "Strip a SourcedVal wrapper, returning the plain value."
  [x]
  (if (sourced? x) (:v x) x))

(defn deep-unwrap
  "Recursively strip SourcedVal wrappers from collections."
  [x]
  (let [v (unwrap x)]
    (cond
      (vector? v) (mapv deep-unwrap v)
      (seq? v)    (map deep-unwrap v)
      (map? v)    (into {} (map (fn [[k val]]
                                   [(deep-unwrap k) (deep-unwrap val)])
                                 v))
      :else       v)))

(defn source-of
  "Return the source position map {:from N :to N} for a value, or nil."
  [x]
  (if (sourced? x)
    (:source x)
    (:source (meta x))))

(defn ->num
  "Coerce a value to a number. Rational pairs [n d] → (/ n d). SourcedVals unwrapped first."
  [x]
  (let [v (unwrap x)]
    (if (and (vector? v) (= 2 (count v)) (number? (first v)) (number? (second v)))
      (/ (first v) (second v))
      v)))
