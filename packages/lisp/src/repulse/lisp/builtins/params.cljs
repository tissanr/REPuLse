(ns repulse.lisp.builtins.params
  "Per-event parameter builtins: amp, attack, decay, release, pan, rate, begin, end,
   loop-sample, comp, tween, env."
  (:require [repulse.params :as params]
            [repulse.lisp.util :as u]))

(defn make-builtins []
  {"amp"     (fn
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
