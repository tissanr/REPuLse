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
   ;; Time arguments are passed through raw (never via u/->num) so a
   ;; rational literal 1/3 stays the exact [1 3]. Strings in patternable
   ;; positions are mini-notation: (fast "<1 2>" pat).
   "fast"   (fn
              ([f]   (let [f (u/pat-arg f)] (fn [p] (core/fast f (u/unwrap p)))))
              ([f p] (core/fast (u/pat-arg f) (u/unwrap p))))
   "slow"   (fn
              ([f]   (let [f (u/pat-arg f)] (fn [p] (core/slow f (u/unwrap p)))))
              ([f p] (core/slow (u/pat-arg f) (u/unwrap p))))
   "rev"    (fn [p] (core/rev (u/unwrap p)))
   "every"  (fn [n t p] (core/every (u/pat-arg n) t (u/unwrap p)))
   "fmap"   (fn [f p] (core/fmap (fn [v] (u/unwrap (f v))) (u/unwrap p)))
   "euclidean" (fn
                 ([k n v]   (core/euclidean (u/pat-arg k) (u/pat-arg n) (u/unwrap v)))
                 ([k n v r] (core/euclidean (u/pat-arg k) (u/pat-arg n) (u/unwrap v) (u/pat-arg r))))
   "cat"    (fn [& ps] (core/cat* (mapv u/unwrap ps)))
   "late"   (fn
              ([a]   (let [a (u/pat-arg a)] (fn [p] (core/late a (u/unwrap p)))))
              ([a p] (core/late (u/pat-arg a) (u/unwrap p))))
   "early"  (fn
              ([a]   (let [a (u/pat-arg a)] (fn [p] (core/early a (u/unwrap p)))))
              ([a p] (core/early (u/pat-arg a) (u/unwrap p))))
   "jux"    (fn [f p] (params/jux (u/unwrap f) (u/unwrap p)))
   "jux-by" (fn [w f p] (params/jux-by (u/pat-arg w) (u/unwrap f) (u/unwrap p)))
   "off"    (fn [a f p] (core/off (u/pat-arg a) (u/unwrap f) (u/unwrap p)))
   "~"      (fn [s]
              (let [src         (u/source-of s)
                    base-offset (when src (inc (:from src)))]
                (mini/parse (u/unwrap s) base-offset)))
   "alt"    (fn [& pats] (mini/alt* (mapv u/unwrap pats)))})
