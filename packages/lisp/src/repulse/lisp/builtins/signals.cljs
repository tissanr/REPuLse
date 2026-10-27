(ns repulse.lisp.builtins.signals
  "Continuous-signal builtins (Phase TL2): sine saw tri square rand perlin …,
   plus segment, range helpers, bipolar converters, run, signal, steady."
  (:require [repulse.core :as core]
            [repulse.lisp.util :as u]))

(defn make-builtins []
  {;; Signal values (bare symbols; calling them is an eval error with a hint)
   "sine" core/sine "cosine" core/cosine "saw" core/saw "isaw" core/isaw
   "tri" core/tri "itri" core/itri "square" core/square "isquare" core/isquare
   "sine2" core/sine2 "cosine2" core/cosine2 "saw2" core/saw2 "isaw2" core/isaw2
   "tri2" core/tri2 "itri2" core/itri2 "square2" core/square2 "isquare2" core/isquare2
   "rand" core/rand "rand2" core/rand2 "perlin" core/perlin "time" core/time
   "brand" core/brand "berlin" core/berlin

   ;; Constructors
   "signal" (fn [f]
              (core/signal (fn [t] (u/unwrap (f (core/rat->float t))))))
   "steady" (fn [v] (core/steady (u/unwrap v)))
   "irand"  (fn [n] (core/irand (u/pat-arg n)))
   "run"    (fn [n] (core/run (u/pat-arg n)))

   ;; Shaping
   "segment" (fn
               ([n]   (let [n (u/pat-arg n)] (fn [p] (core/segment n (u/unwrap p)))))
               ([n p] (core/segment (u/pat-arg n) (u/unwrap p))))
   "rangex"  (fn
               ([lo hi]
                (let [lo (u/pat-arg lo) hi (u/pat-arg hi)]
                  (fn [p] (core/rangex-pat lo hi (u/unwrap p)))))
               ([lo hi p] (core/rangex-pat (u/pat-arg lo) (u/pat-arg hi) (u/unwrap p))))
   "range2"  (fn
               ([lo hi]
                (let [lo (u/pat-arg lo) hi (u/pat-arg hi)]
                  (fn [p] (core/range2-pat lo hi (u/unwrap p)))))
               ([lo hi p] (core/range2-pat (u/pat-arg lo) (u/pat-arg hi) (u/unwrap p))))
   "to-bipolar"   (fn [p] (core/to-bipolar (u/unwrap p)))
   "from-bipolar" (fn [p] (core/from-bipolar (u/unwrap p)))})
