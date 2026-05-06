(ns repulse.lisp.builtins.synth
  "Synth-voice builtins: saw, square, noise, fm, synth, sound, and waveshape curves."
  (:require [repulse.core :as core]
            [repulse.theory :as theory]
            [repulse.lisp.util :as u]))

(defn make-builtins []
  {"saw"    (fn [note]
              {:note (u/unwrap note) :synth :saw})
   "square" (fn [note & opts]
              (let [n     (u/unwrap note)
                    opts' (apply hash-map (mapv u/unwrap opts))
                    pw    (get opts' :pw 0.5)]
                {:note n :synth :square :pw pw}))
   "noise"  (fn [] {:synth :noise})
   "fm"     (fn [note & opts]
              (let [n     (u/unwrap note)
                    opts' (apply hash-map (mapv u/unwrap opts))
                    idx   (get opts' :index 1.0)
                    ratio (get opts' :ratio 2.0)]
                {:note n :synth :fm :index idx :ratio ratio}))
   "synth"  (fn [voice-arg & rest-args]
              (let [voice    (u/unwrap voice-arg)
                    args'    (mapv u/unwrap rest-args)
                    last-a   (last args')
                    has-pat? (and (seq args')
                                  (map? last-a)
                                  (fn? (:query last-a)))]
                ;; Detect common mistake: transformer (amp, pan, …) passed directly
                ;; as a synth argument instead of chained via ->>.
                (when (and (seq args') (fn? last-a) (not has-pat?))
                  (throw (js/Error. "amp, pan and other transformers must be chained with ->>, not passed as synth arguments.\nUse: (->> (synth :saw pattern) (amp 0.7))")))
                (let [opts-map (apply hash-map (if has-pat? (butlast args') args'))
                      apply-xf (fn [pat]
                                  (core/fmap
                                   (fn [v]
                                     (let [base (if (map? v) v {:note v})
                                           freq (or (:freq base)
                                                    (when (number? (:note base)) (:note base))
                                                    (when (keyword? (:note base))
                                                      (theory/note->hz (:note base))))]
                                       (merge base {:synth voice :freq freq} opts-map)))
                                   pat))]
                  (if has-pat?
                    (apply-xf last-a)
                    apply-xf))))
   "sound"  (fn [bank n] {:bank (u/unwrap bank) :n (or (u/unwrap n) 0)})
   "chebyshev"
   (fn [n-arg]
     (let [n (u/unwrap n-arg)
           N 512
           arr (js/Float32Array. N)]
       (when (or (< n 1) (> n 8))
         (throw (js/Error. (str "chebyshev: order must be 1-8, got " n))))
       (dotimes [i N]
         (let [x (- (* 2.0 (/ i (dec N))) 1.0)
               t (loop [t0 1.0 t1 x k 1]
                   (if (>= k n)
                     t1
                     (recur t1 (- (* 2.0 x t1) t0) (inc k))))]
           (aset arr i (js/Math.max -1.0 (js/Math.min 1.0 t)))))
       arr))
   "fold"
   (fn []
     (let [N 512
           arr (js/Float32Array. N)]
       (dotimes [i N]
         (let [x (- (* 2.0 (/ i (dec N))) 1.0)
               folded (let [a (js/Math.abs x)
                            t (mod a 1.0)
                            v (if (< t 0.5) (* t 2.0) (- 2.0 (* t 2.0)))]
                        (if (neg? x) (- v) v))]
           (aset arr i (js/Math.max -1.0 (js/Math.min 1.0 folded)))))
       arr))
   "bitcrush"
   (fn [bits-arg]
     (let [bits (u/unwrap bits-arg)
           N 512
           arr (js/Float32Array. N)
           steps (js/Math.pow 2 bits)]
       (when (or (< bits 1) (> bits 16))
         (throw (js/Error. (str "bitcrush: bits must be 1-16, got " bits))))
       (dotimes [i N]
         (let [x (- (* 2.0 (/ i (dec N))) 1.0)
               quantized (/ (js/Math.round (* x (/ steps 2.0))) (/ steps 2.0))]
           (aset arr i (js/Math.max -1.0 (js/Math.min 1.0 quantized)))))
       arr))})
