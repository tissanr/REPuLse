(ns repulse.lisp.builtins.pattern
  "Pure pattern-algebra builtins: seq, stack, fast, slow, euclidean, etc."
  (:require [repulse.core :as core]
            [repulse.params :as params]
            [repulse.lisp.mini :as mini]
            [repulse.lisp.util :as u]))

(defn make-builtins []
  {"seq"    (fn [& vs]
              (let [srcs (mapv u/source-of vs)
                    vals (mapv u/unwrap vs)]
                (core/seq* vals srcs)))
   "stack"  (fn [& ps] (core/stack* (mapv u/unwrap ps)))
   "pure"   (fn [v] (core/pure (u/unwrap v) (u/source-of v)))
   "fast"   (fn
              ([f]   (fn [p] (core/fast (u/->num f) (u/unwrap p))))
              ([f p] (core/fast (u/->num f) (u/unwrap p))))
   "slow"   (fn
              ([f]   (fn [p] (core/slow (u/->num f) (u/unwrap p))))
              ([f p] (core/slow (u/->num f) (u/unwrap p))))
   "rev"    (fn [p] (core/rev (u/unwrap p)))
   "every"  (fn [n t p] (core/every (u/unwrap n) t (u/unwrap p)))
   "fmap"   (fn [f p] (core/fmap (fn [v] (u/unwrap (f v))) (u/unwrap p)))
   "euclidean" (fn
                 ([k n v]   (core/euclidean (u/unwrap k) (u/unwrap n) (u/unwrap v)))
                 ([k n v r] (core/euclidean (u/unwrap k) (u/unwrap n) (u/unwrap v) (u/unwrap r))))
   "cat"    (fn [& ps] (core/cat* (mapv u/unwrap ps)))
   "late"   (fn
              ([a]   (fn [p] (core/late (u/unwrap a) (u/unwrap p))))
              ([a p] (core/late (u/unwrap a) (u/unwrap p))))
   "early"  (fn
              ([a]   (fn [p] (core/early (u/unwrap a) (u/unwrap p))))
              ([a p] (core/early (u/unwrap a) (u/unwrap p))))
   "sometimes"    (fn [f p] (core/sometimes (u/unwrap f) (u/unwrap p)))
   "often"        (fn [f p] (core/often (u/unwrap f) (u/unwrap p)))
   "rarely"       (fn [f p] (core/rarely (u/unwrap f) (u/unwrap p)))
   "sometimes-by" (fn [prob f p] (core/sometimes-by (u/unwrap prob) (u/unwrap f) (u/unwrap p)))
   "degrade"      (fn [p] (core/degrade (u/unwrap p)))
   "degrade-by"   (fn
                    ([prob]   (fn [p] (core/degrade-by (u/unwrap prob) (u/unwrap p))))
                    ([prob p] (core/degrade-by (u/unwrap prob) (u/unwrap p))))
   "choose"  (fn [xs]
               (let [xs' (u/unwrap xs)]
                 (core/choose (mapv u/unwrap xs') (mapv u/source-of xs'))))
   "wchoose" (fn [pairs]
               (let [pairs' (u/unwrap pairs)
                     srcs   (mapv #(u/source-of (second %)) pairs')
                     vecs   (mapv (fn [[w v]] [(u/unwrap w) (u/unwrap v)]) pairs')]
                 (core/wchoose vecs srcs)))
   "jux"    (fn [f p] (params/jux (u/unwrap f) (u/unwrap p)))
   "jux-by" (fn [w f p] (params/jux-by (u/unwrap w) (u/unwrap f) (u/unwrap p)))
   "off"    (fn [a f p] (core/off (u/unwrap a) (u/unwrap f) (u/unwrap p)))
   "~"      (fn [s]
              (let [src         (u/source-of s)
                    base-offset (when src (inc (:from src)))]
                (mini/parse (u/unwrap s) base-offset)))
   "alt"    (fn [& pats] (mini/alt* (mapv u/unwrap pats)))})
