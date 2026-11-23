(ns repulse.lisp.builtins.common
  "Helpers shared by the Lisp builtin namespaces (pattern, structure, random,
   pick, music). Pure; they only adapt Lisp values for repulse.core."
  (:require [repulse.core :as core]
            [repulse.lisp.util :as u]))

(defn xf
  "Wrap a Lisp function argument so SourcedVals never reach core."
  [f]
  (let [f (u/unwrap f)]
    (fn [p] (u/unwrap (f p)))))

(defn step->pat [x]
  (let [v (u/unwrap x)]
    (if (core/pattern? v) v (core/pure v (u/source-of x)))))

(defn n+p
  "Builtin (f n pat) with curried form (f n); n patternable."
  [f]
  (fn
    ([n]   (let [n (u/pat-arg n)] (fn [p] (f n (u/unwrap p)))))
    ([n p] (f (u/pat-arg n) (u/unwrap p)))))

(defn n+f+p
  "Builtin (f n fn pat) with curried form (f n fn)."
  [f]
  (fn
    ([n g]   (let [n (u/pat-arg n) g (xf g)] (fn [p] (f n g (u/unwrap p)))))
    ([n g p] (f (u/pat-arg n) (xf g) (u/unwrap p)))))

(defn numeric-token? [tok]
  (boolean (re-matches #"-?\d+(\.\d+)?(/\d+)?" tok)))
