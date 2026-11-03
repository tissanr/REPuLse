(ns repulse.lisp.builtins.structure
  "TL3 time & structure combinators: struct, mask, ply, chunk, iter, swing,
   polymeter, timecat, … — and TL3b windows (zoom, compress, focus, linger, …),
   switches (sew, stitch, bypass, beat) and binary rhythms — thin Lisp wrappers
   over repulse.core."
  (:require [repulse.core :as core]
            [repulse.params :as params]
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

(defn- xf2
  "Wrap a Lisp (fn [pat i]) so SourcedVals never reach core."
  [f]
  (let [f (u/unwrap f)] (fn [p i] (u/unwrap (f p i)))))

(defn- pred
  "Wrap a Lisp predicate (fn [v]) for core filters."
  [f]
  (let [f (u/unwrap f)] (fn [v] (u/unwrap (f v)))))

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
   "revv"  (p1 core/revv)
   "press" (p1 core/press)
   "fast-gap"      (n+p core/fast-gap)
   "linger"        (n+p core/linger)
   "trunc"         (n+p core/trunc)
   "repeat-cycles" (n+p core/repeat-cycles)
   "press-by"      (n+p core/press-by)
   "bypass"        (n+p core/bypass)
   "hurry"         (n+p params/hurry)
   "compress" (a+b+p core/compress)
   "zoom"     (a+b+p core/zoom)
   "focus"    (a+b+p core/focus)
   "ribbon"   (a+b+p core/ribbon)
   "beat"     (a+b+p core/beat)
   "bite"     (a+b+p core/bite)
   "chunk-into"      (n+f+p core/chunk-into)
   "chunk-back-into" (n+f+p core/chunk-back-into)
   "ply-for-each"
   (fn
     ([n f]   (let [n (u/pat-arg n) f (xf2 f)]
                (fn [p] (core/ply-for-each n f (u/unwrap p)))))
     ([n f p] (core/ply-for-each (u/pat-arg n) (xf2 f) (u/unwrap p))))
   "euclidish"
   (fn
     ([k n g]   (let [k (u/pat-arg k) n (u/pat-arg n) g (u/pat-arg g)]
                  (fn [p] (core/euclidish k n g (u/unwrap p)))))
     ([k n g p] (core/euclidish (u/pat-arg k) (u/pat-arg n) (u/pat-arg g) (u/unwrap p))))
   "filter-events" (fn
                     ([f]   (let [f (pred f)] (fn [p] (core/filter-events f (u/unwrap p)))))
                     ([f p] (core/filter-events (pred f) (u/unwrap p))))
   "filter-when"   (fn
                     ([f]   (let [f (pred f)] (fn [p] (core/filter-when f (u/unwrap p)))))
                     ([f p] (core/filter-when (pred f) (u/unwrap p))))
   "sew"    (fn [b x y] (core/sew (u/pat-arg b) (u/unwrap x) (u/unwrap y)))
   "stitch" (fn [b x y] (core/stitch (u/pat-arg b) (u/unwrap x) (u/unwrap y)))
   "binary"   (fn [n] (core/binary (u/pat-arg n)))
   "binary-n" (fn
                ([n]      (core/binary-n (u/pat-arg n)))
                ([n bits] (core/binary-n (u/pat-arg n) (u/pat-arg bits))))
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
