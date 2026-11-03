(ns repulse.lisp.builtins.random
  "TL4 randomness & conditionals: the degrade family, sometimes family,
   some-cycles, choose family, randcat, shuffle/scramble, with-seed,
   when-pat, whenmod, apply-n — thin Lisp wrappers over repulse.core.
   brand and berlin are bare signal values registered in builtins.signals."
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

(defn- p1 [f] (fn [p] (f (u/unwrap p))))

(defn- n+p
  "(f n pat) with curried (f n); n patternable."
  [f]
  (fn
    ([n]   (let [n (u/pat-arg n)] (fn [p] (f n (u/unwrap p)))))
    ([n p] (f (u/pat-arg n) (u/unwrap p)))))

(defn- f+p
  "(f fn pat) with curried (f fn)."
  [f]
  (fn
    ([g]   (let [g (xf g)] (fn [p] (f g (u/unwrap p)))))
    ([g p] (f (xf g) (u/unwrap p)))))

(defn- n+f+p
  "(f n fn pat) with curried (f n fn); n patternable."
  [f]
  (fn
    ([n g]   (let [n (u/pat-arg n) g (xf g)] (fn [p] (f n g (u/unwrap p)))))
    ([n g p] (f (u/pat-arg n) (xf g) (u/unwrap p)))))

(defn- when-on
  "A Lisp fn of the cycle number (Tidal when), or a patternable boolean."
  [on]
  (let [v (u/unwrap on)]
    (if (fn? v)
      (fn [c] (u/unwrap (v c)))
      (u/pat-arg on))))

(defn- pairs-of
  "[[w x] …] → [[w value] …] with sources of the values."
  [pairs]
  (let [pairs' (u/unwrap pairs)]
    [(mapv (fn [[w v]] [(u/unwrap w) (u/unwrap v)]) pairs')
     (mapv #(u/source-of (second %)) pairs')]))

(defn make-builtins []
  {"degrade"   (p1 core/degrade)
   "undegrade" (p1 core/undegrade)
   "degrade-by"   (n+p core/degrade-by)
   "undegrade-by" (n+p core/undegrade-by)
   "with-seed"    (n+p core/with-seed)
   "shuffle"      (n+p core/shuffle)
   "scramble"     (n+p core/scramble)
   "degrade-by-with"
   (fn
     ([r x]   (let [r (u/pat-arg r) x (u/pat-arg x)]
                (fn [p] (core/degrade-by-with r x (u/unwrap p)))))
     ([r x p] (core/degrade-by-with (u/pat-arg r) (u/pat-arg x) (u/unwrap p))))

   "sometimes"     (f+p core/sometimes)
   "often"         (f+p core/often)
   "rarely"        (f+p core/rarely)
   "almost-never"  (f+p core/almost-never)
   "almost-always" (f+p core/almost-always)
   "always"        (f+p core/always)
   "never"         (f+p core/never)
   "some-cycles"   (f+p core/some-cycles)
   "sometimes-by"    (n+f+p core/sometimes-by)
   "some-cycles-by"  (n+f+p core/some-cycles-by)
   "apply-n"         (n+f+p core/apply-n)

   "choose"        (fn [xs]
                     (let [xs' (u/unwrap xs)]
                       (core/choose (mapv u/unwrap xs') (mapv u/source-of xs'))))
   "choose-cycles" (fn [xs]
                     (let [xs' (u/unwrap xs)]
                       (core/choose-cycles (mapv u/unwrap xs') (mapv u/source-of xs'))))
   "choose-in"     (fn [xs] (core/choose-in (mapv step->pat (u/unwrap xs))))
   "choose-with"    (fn [sig xs] (core/choose-with (u/pat-arg sig) (mapv step->pat (u/unwrap xs))))
   "choose-in-with" (fn [sig xs] (core/choose-in-with (u/pat-arg sig) (mapv step->pat (u/unwrap xs))))
   "wchoose"        (fn [pairs] (let [[ps srcs] (pairs-of pairs)] (core/wchoose ps srcs)))
   "wchoose-cycles" (fn [pairs] (let [[ps srcs] (pairs-of pairs)] (core/wchoose-cycles ps srcs)))
   "randcat"        (fn [& ps] (core/randcat (mapv step->pat ps)))
   "wrandcat"       (fn [& pairs]
                      (core/wrandcat (mapv (fn [pr]
                                             (let [[w p] (u/unwrap pr)]
                                               [(u/unwrap w) (step->pat p)]))
                                           pairs)))

   "brand-by" (fn [p] (core/brand-by (u/pat-arg p)))
   "rand-l"   (fn [n] (core/rand-l (u/pat-arg n)))
   "randrun"  (fn [n] (core/randrun (u/pat-arg n)))

   "when-pat" (fn
                ([on g]   (let [on (when-on on) g (xf g)]
                            (fn [p] (core/when-pat on g (u/unwrap p)))))
                ([on g p] (core/when-pat (when-on on) (xf g) (u/unwrap p))))
   "whenmod"  (fn
                ([a b g]   (let [a (u/pat-arg a) b (u/pat-arg b) g (xf g)]
                             (fn [p] (core/whenmod a b g (u/unwrap p)))))
                ([a b g p] (core/whenmod (u/pat-arg a) (u/pat-arg b) (xf g) (u/unwrap p))))})
