(ns repulse.lisp.builtins.structure
  "TL3 time & structure combinators: struct, mask, ply, chunk, iter, swing,
   polymeter, timecat, … — thin Lisp wrappers over repulse.core."
  (:require [repulse.core :as core]
            [repulse.lisp.util :as u]))

(defn- xf
  "Wrap a Lisp function argument so SourcedVals never reach core."
  [f]
  (let [f (u/unwrap f)]
    (fn [p] (u/unwrap (f p)))))

(defn- step->pat [x]
  (let [v (u/unwrap x)]
    (if (core/pattern? v) v (core/pure v (u/source-of x)))))

(defn- p1
  "Builtin (f pat), no numeric arg."
  [f]
  (fn [p] (f (u/pat-arg p))))

(defn- n+p
  "Builtin (f n pat) with curried form (f n); n patternable."
  [f]
  (fn
    ([n]   (let [n (u/pat-arg n)] (fn [p] (f n (u/unwrap p)))))
    ([n p] (f (u/pat-arg n) (u/unwrap p)))))

(defn- n+f+p
  "Builtin (f n fn pat) with curried form (f n fn)."
  [f]
  (fn
    ([n g]   (let [n (u/pat-arg n) g (xf g)] (fn [p] (f n g (u/unwrap p)))))
    ([n g p] (f (u/pat-arg n) (xf g) (u/unwrap p)))))

(defn- a+b+p
  "Builtin (f a b pat) with curried form (f a b); a and b patternable."
  [f]
  (fn
    ([a b]   (let [a (u/pat-arg a) b (u/pat-arg b)] (fn [p] (f a b (u/unwrap p)))))
    ([a b p] (f (u/pat-arg a) (u/pat-arg b) (u/unwrap p)))))

(defn make-builtins []
  {"struct"  (n+p core/struct)
   "mask"    (n+p core/mask)
   "invert"  (p1 core/invert)
   "brak"    (p1 core/brak)
   "palindrome" (p1 core/palindrome)
   "ply"     (n+p core/ply)
   "swing"   (n+p core/swing)
   "iter"    (n+p core/iter)
   "iter-back" (n+p core/iter-back)
   "rot"     (n+p core/rot)
   "ply-with"   (n+f+p core/ply-with)
   "inside"     (n+f+p core/inside)
   "outside"    (n+f+p core/outside)
   "chunk"      (n+f+p core/chunk)
   "chunk-back" (n+f+p core/chunk-back)
   "fast-chunk" (n+f+p core/fast-chunk)
   "first-of"   (n+f+p core/first-of)
   "last-of"    (n+f+p core/last-of)
   "within"  (fn
               ([s e f]   (let [s (u/pat-arg s) e (u/pat-arg e) f (xf f)]
                            (fn [p] (core/within s e f (u/unwrap p)))))
               ([s e f p] (core/within (u/pat-arg s) (u/pat-arg e) (xf f) (u/unwrap p))))
   "swing-by"     (a+b+p core/swing-by)
   "euclid-legato" (a+b+p core/euclid-legato)
   "euclid-legato-rot"
   (fn
     ([k n r]   (let [k (u/pat-arg k) n (u/pat-arg n) r (u/pat-arg r)]
                  (fn [p] (core/euclid-legato-rot k n r (u/unwrap p)))))
     ([k n r p] (core/euclid-legato-rot (u/pat-arg k) (u/pat-arg n) (u/pat-arg r) (u/unwrap p))))
   "timecat" (fn [& pairs]
               (core/timecat (mapv (fn [pair]
                                     (let [[w x] (u/unwrap pair)]
                                       [(u/unwrap w) (step->pat x)]))
                                   pairs)))
   "polymeter" (fn [& args]
                 (let [a0           (u/unwrap (first args))
                       has-steps?   (and (seq args) (not (vector? a0)))
                       steps        (when has-steps? (u/pat-arg (first args)))
                       layers       (if has-steps? (rest args) args)]
                   (core/polymeter steps
                                   (mapv (fn [layer] (mapv step->pat (u/unwrap layer)))
                                         layers))))})
