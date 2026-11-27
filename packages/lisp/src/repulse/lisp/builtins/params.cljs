(ns repulse.lisp.builtins.params
  "Per-event parameter builtins: amp, attack, decay, release, pan, rate, begin, end,
   loop-sample, comp, tween, env — and TL6 sample slicing: chop, striate, slice,
   splice, fit, loop-at, speed, unit, accelerate, legato, sustain, cut,
   loop-begin, loop-end — and TL7a per-event inserts: lpf/cutoff, resonance, hpf,
   hpq, bpf, bpq, shape, crush, gain, velocity — and TL7b sends: room, size, delay,
   delay-time, delay-feedback, orbit — and TL7c envelopes, pitch
   modulation, vowel, coarse, djf, postgain, nudge, delay-sync — and SYN6
   oscillator, drum-kit and instrument parameters."
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
   ;; Phase TL7b — reverb & delay sends per orbit
   "room"           (param params/room)
   "size"           (param params/size)
   "delay"          (param params/delay*)
   "delay-time"     (param params/delay-time)
   "delay-feedback" (param params/delay-feedback)
   "orbit"          (param params/orbit)
   ;; Phase TL7c — envelopes, pitch, vowel, coarse, djf, postgain, nudge, delay-sync
   "lp-env"       (param params/lp-env)
   "lp-attack"    (param params/lp-attack)
   "lp-decay"     (param params/lp-decay)
   "lp-sustain"   (param params/lp-sustain)
   "lp-release"   (param params/lp-release)
   "hp-env"       (param params/hp-env)
   "hp-attack"    (param params/hp-attack)
   "hp-decay"     (param params/hp-decay)
   "hp-sustain"   (param params/hp-sustain)
   "hp-release"   (param params/hp-release)
   "bp-env"       (param params/bp-env)
   "bp-attack"    (param params/bp-attack)
   "bp-decay"     (param params/bp-decay)
   "bp-sustain"   (param params/bp-sustain)
   "bp-release"   (param params/bp-release)
   "penv"         (param params/penv)
   "p-attack"     (param params/p-attack)
   "p-decay"      (param params/p-decay)
   "p-sustain"    (param params/p-sustain)
   "p-release"    (param params/p-release)
   "vib"          (param params/vib)
   "vib-mod"      (param params/vib-mod)
   "vowel"        (param params/vowel)
   "coarse"       (param params/coarse)
   "djf"          (param params/djf)
   "postgain"     (param params/postgain)
   "nudge"        (param params/nudge)
   "delay-sync"   (param params/delay-sync)
   ;; Phase SYN6
   "unison"       (param params/unison)
   "detune"       (param params/detune)
   "spread"       (param params/spread)
   "sub-osc"      (param params/sub-osc)
   "pw"           (param params/pw)
   "pw-rate"      (param params/pw-rate)
   "pw-depth"     (param params/pw-depth)
   "sync"         (param params/sync)
   "tune"         (param params/tune)
   "tone"         (param params/tone)
   "glide"        (param params/glide)
   "accent"       (param params/accent)
   "lp-rate"      (param params/lp-rate)
   "lp-depth"     (param params/lp-depth)
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
