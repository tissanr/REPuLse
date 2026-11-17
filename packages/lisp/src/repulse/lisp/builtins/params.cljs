(ns repulse.lisp.builtins.params
  "Per-event parameter builtins: amp, attack, decay, release, pan, rate, begin, end,
   loop-sample, comp, tween, env — and TL6 sample slicing: chop, striate, slice,
   splice, fit, loop-at, speed, unit, accelerate, legato, sustain, cut,
   loop-begin, loop-end — and TL7a per-event inserts: lpf/cutoff, resonance, hpf,
   hpq, bpf, bpq, shape, crush, gain, velocity."
  (:require [repulse.params :as params]
            [repulse.lisp.util :as u]))

(defn- param
  "Builtin (f v pat) with curried form (f v); v patternable (mini-notation ok)."
  [f]
  (fn
    ([v]   (f (u/pat-arg v)))
    ([v p] (f (u/pat-arg v) (u/unwrap p)))))

(defn- n+i+p
  "Builtin (f n ipat pat) with curried form (f n ipat)."
  [f]
  (fn
    ([n i]   (f (u/pat-arg n) (u/pat-arg i)))
    ([n i p] (f (u/pat-arg n) (u/pat-arg i) (u/unwrap p)))))

(defn make-builtins []
  {"chop"       (param params/chop)
   "striate"    (param params/striate)
   "slice"      (n+i+p params/slice)
   "splice"     (n+i+p params/splice)
   "fit"        (fn [p] (params/fit (u/unwrap p)))
   "loop-at"    (param params/loop-at)
   "speed"      (param params/speed)
   "unit"       (param params/unit)
   "accelerate" (param params/accelerate)
   "legato"     (param params/legato)
   "sustain"    (param params/sustain)
   "cut"        (param params/cut)
   "loop-begin" (param params/loop-begin)
   "loop-end"   (param params/loop-end)
   ;; Phase TL7a — per-event inserts. Inside a defsynth body the UGen env is
   ;; merged last, so lpf/hpf/bpf/gain keep their UGen meaning there.
   "lpf"        (param params/lpf)
   "cutoff"     (param params/lpf)
   "resonance"  (param params/resonance)
   "hpf"        (param params/hpf)
   "hpq"        (param params/hpq)
   "bpf"        (param params/bpf)
   "bpq"        (param params/bpq)
   "shape"      (param params/shape)
   "crush"      (param params/crush)
   "gain"       (param params/gain)
   "velocity"   (param params/velocity)
"amp"     (fn
               ([v]   (params/amp (u/unwrap v)))
               ([v p] (params/amp (u/unwrap v) (u/unwrap p))))
   "attack"  (fn
               ([v]   (params/attack (u/unwrap v)))
               ([v p] (params/attack (u/unwrap v) (u/unwrap p))))
   "decay"   (fn
               ([v]   (params/decay (u/unwrap v)))
               ([v p] (params/decay (u/unwrap v) (u/unwrap p))))
   "release" (fn
               ([v]   (params/release (u/unwrap v)))
               ([v p] (params/release (u/unwrap v) (u/unwrap p))))
   "pan"     (fn
               ([v]   (params/pan (u/unwrap v)))
               ([v p] (params/pan (u/unwrap v) (u/unwrap p))))
   "rate"    (fn
               ([v]   (params/rate (u/unwrap v)))
               ([v p] (params/rate (u/unwrap v) (u/unwrap p))))
   "begin"   (fn
               ([v]   (params/begin (u/unwrap v)))
               ([v p] (params/begin (u/unwrap v) (u/unwrap p))))
   "end"     (fn
               ([v]   (params/end* (u/unwrap v)))
               ([v p] (params/end* (u/unwrap v) (u/unwrap p))))
   "loop-sample" (fn
                   ([v]   (params/loop-sample (u/unwrap v)))
                   ([v p] (params/loop-sample (u/unwrap v) (u/unwrap p))))
   "comp"    (fn [& fs] (apply comp fs))
   "tween"   (fn [curve-arg start-arg end-arg dur-arg]
               (let [curve (u/unwrap curve-arg)
                     start (u/->num start-arg)
                     end   (u/->num end-arg)
                     dur   (u/->num dur-arg)]
                 (when-not (#{:linear :exp :sine} curve)
                   (throw (js/Error.
                            (str "Unknown curve type " curve
                                 ". Available: :linear, :exp, :sine"))))
                 (when-not (pos? dur)
                   (throw (js/Error. "Transition duration must be > 0")))
                 {:type :tween :curve curve :start start :end end :duration-bars dur}))
   "env"     (fn [& args]
               (let [args'  (mapv u/unwrap args)
                     ;; Unwrap SourcedVal records inside each vector element
                     levels (mapv u/unwrap (nth args' 0 []))
                     times  (mapv u/unwrap (nth args' 1 []))
                     curves (mapv u/unwrap (nth args' 2 []))]
                 (when (not= (count times) (dec (count levels)))
                   (throw (js/Error.
                            (str "env: times must have exactly (count levels - 1) elements. "
                                 "Got " (count levels) " levels and " (count times) " times."))))
                 {:type   :envelope
                  :levels levels
                  :times  times
                  :curves (into curves
                                (repeat (max 0 (- (count times) (count curves))) :lin))}))})
