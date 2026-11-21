(ns repulse.lisp.util
  "Shared helpers for the Lisp evaluator and builtin sub-namespaces.
   Extracted from eval.cljs so builtins can require them without creating
   circular dependencies."
  (:require [repulse.lisp.reader :as reader]
            [repulse.lisp.mini :as mini]))

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

(defn levenshtein [a b]
  (let [m (count a) n (count b)
        row (atom (vec (range (inc n))))]
    (dotimes [i m]
      (let [prev @row
            cur  (atom [(inc i)])]
        (dotimes [j n]
          (swap! cur conj
                 (if (= (nth a i) (nth b j))
                   (nth prev j)
                   (inc (min (nth prev (inc j))
                             (last @cur)
                             (nth prev j))))))
        (reset! row @cur)))
    (last @row)))

(defn typo-hint [name known]
  (when (seq known)
    (let [best (apply min-key #(levenshtein name (str %)) known)]
      (when (<= (levenshtein name (str best)) 3)
        best))))

(defn pat-arg
  "Unwrap a value that sits in a patternable argument position.
   Strings are parsed as mini-notation (with editor source offsets, so the
   tokens still highlight); everything else — numbers, [n d] rationals,
   patterns — is passed through unwrapped, never coerced to a float."
  [x]
  (let [v (unwrap x)]
    (if (string? v)
      (let [src (source-of x)]
        (mini/parse v (when src (inc (:from src)))))
      v)))
