(ns repulse.lisp.builtins.collection
  "Collection and data-structure builtins: get, assoc, map, filter, reduce, etc."
  (:require [repulse.lisp.util :as u]))

(defn make-builtins []
  {"get"     (fn [m k & rest]
               (let [m' (u/unwrap m)
                     k' (u/unwrap k)]
                 (if (seq rest)
                   (get m' k' (u/unwrap (first rest)))
                   (get m' k'))))
   "assoc"   (fn [m k v] (assoc (u/unwrap m) (u/unwrap k) (u/unwrap v)))
   "merge"   (fn [& ms]  (apply merge (map u/unwrap ms)))
   "keys"    (fn [m]     (keys (u/unwrap m)))
   "vals"    (fn [m]     (vals (u/unwrap m)))
   "conj"    (fn [coll v] (conj (u/unwrap coll) (u/unwrap v)))
   "apply"   (fn [f & args]
               (let [last-arg  (mapv u/unwrap (u/unwrap (last args)))
                     init-args (map u/unwrap (butlast args))]
                 (apply f (concat init-args last-arg))))
   "list"    (fn [& vs] (apply list (map u/unwrap vs)))
   "count"   (fn [coll] (count (u/unwrap coll)))
   "nth"     (fn [coll i] (nth (u/unwrap coll) (u/unwrap i)))
   "first"   (fn [coll] (first (u/unwrap coll)))
   "rest"    (fn [coll] (rest (u/unwrap coll)))
   "empty?"  (fn [coll] (empty? (u/unwrap coll)))
   "cons"    (fn [x coll] (cons (u/unwrap x) (u/unwrap coll)))
   "concat"  (fn [& colls] (apply concat (map u/unwrap colls)))
   "vec"     (fn [coll] (vec (u/unwrap coll)))
   "map"     (fn [f coll] (map f (u/unwrap coll)))
   "filter"  (fn [f coll] (filter f (u/unwrap coll)))
   "reduce"  (fn
               ([f coll]      (reduce f (u/unwrap coll)))
               ([f init coll] (reduce f (u/unwrap init) (u/unwrap coll))))
   "range"   (fn
               ([n]        (range (u/unwrap n)))
               ([a b]      (range (u/unwrap a) (u/unwrap b)))
               ([a b step] (range (u/unwrap a) (u/unwrap b) (u/unwrap step))))
   "str"     (fn [& args] (apply str (map u/unwrap args)))
   "symbol"  (fn [s] (symbol (u/unwrap s)))
   "keyword" (fn [s] (keyword (u/unwrap s)))
   "name"    (fn [k] (cljs.core/name (u/unwrap k)))
   "identity" (fn [x] x)})
